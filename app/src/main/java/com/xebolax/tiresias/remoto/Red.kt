package com.xebolax.tiresias.remoto

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.remoto.Candidato
import com.xebolax.tiresias.nucleo.remoto.Direcciones
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lo de la red que necesita la asistencia remota, sin ningún servidor: las
 * direcciones propias que se ponen en la invitación, la puerta de enlace (el
 * router) para pedirle un puerto, y el anuncio y la búsqueda en la misma wifi
 * (NSD, para el código corto).
 */
object Red {
	private const val ETIQUETA = "Red"
	/** El tipo de servicio que se anuncia en la wifi. */
	const val SERVICIO_NSD = "_tiresias-ayuda._tcp."

	/** Las direcciones de este móvil donde puede entrar alguien: las de la red local (IPv4) y las IPv6 globales. */
	fun direcciones(): List<java.net.InetAddress> = try {
		NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
			.filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
			.flatMap { i -> i.inetAddresses.toList() }
			.filter { ip ->
				when (ip) {
					is Inet4Address -> Direcciones.esPrivada(ip) && !ip.isLinkLocalAddress
					is Inet6Address -> Direcciones.esIpv6Global(ip)
					else -> false
				}
			}
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudieron leer las direcciones del móvil", e)
		emptyList()
	}

	/** Los candidatos de [puerto]: primero la wifi (IPv4), luego como mucho dos IPv6 globales. */
	fun candidatos(puerto: Int): List<Candidato> {
		val ips = direcciones()
		val v4 = ips.filterIsInstance<Inet4Address>().take(2)
		val v6 = ips.filterIsInstance<Inet6Address>().take(2)
		return (v4 + v6).map { Candidato(it.address, puerto) }
	}

	/** Si el móvil tiene ahora alguna red (wifi o datos). */
	fun hayInternet(c: Context): Boolean = try {
		val cm = c.getSystemService(ConnectivityManager::class.java)
		cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo saber si hay red", e)
		false
	}

	/** La IPv4 de la wifi y la de su router, si este móvil está en una wifi. */
	fun wifi(c: Context): Pair<Inet4Address, Inet4Address?>? = try {
		val cm = c.getSystemService(ConnectivityManager::class.java)
		val red = cm?.activeNetwork
		val capacidades = red?.let { cm.getNetworkCapabilities(it) }
		if (capacidades?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true && capacidades?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true) null
		else {
			val enlace = cm.getLinkProperties(red)
			val propia = enlace?.linkAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { Direcciones.esPrivada(it) }
			val router = enlace?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway as? Inet4Address
			propia?.let { it to router }
		}
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo saber la red wifi", e)
		null
	}

	/** Hace [accion] con el permiso de multidifusión de la wifi puesto (UPnP lo necesita para buscar el router). */
	fun <T> conMultidifusion(c: Context, accion: () -> T): T {
		val cerrojo = runCatching { c.applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock("tiresias-ayuda")?.apply { setReferenceCounted(false); acquire() } }
			.onFailure { Registro.w(ETIQUETA, "Sin el cerrojo de multidifusión", it) }.getOrNull()
		try {
			return accion()
		} finally {
			runCatching { cerrojo?.release() }
		}
	}

	/** Anuncia en la wifi que este móvil espera ayuda en [puerto] (para el código corto). Devuelve cómo quitarlo. */
	fun anunciar(c: Context, puerto: Int, nombre: String): () -> Unit {
		val nsd = c.getSystemService(NsdManager::class.java) ?: return {}
		val info = NsdServiceInfo().apply {
			serviceName = "Tiresias " + nombre.take(30)
			serviceType = SERVICIO_NSD
			port = puerto
		}
		val oyente = object : NsdManager.RegistrationListener {
			override fun onServiceRegistered(info: NsdServiceInfo) = Unit
			override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Registro.w(ETIQUETA, "No se pudo anunciar en la wifi ($error)")
			override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
			override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Registro.w(ETIQUETA, "No se pudo quitar el anuncio ($error)")
		}
		val quitar: () -> Unit = { runCatching { nsd.unregisterService(oyente) }.onFailure { Registro.w(ETIQUETA, "No se pudo quitar el anuncio", it) } }
		val nada: () -> Unit = {}
		return try {
			nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, oyente)
			quitar
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo anunciar en la wifi", e)
			nada
		}
	}

	/**
	 * Busca en la wifi los móviles que esperan ayuda, durante [esperaMs] como
	 * mucho, y devuelve sus direcciones (para probar el código corto en todos).
	 */
	@Suppress("DEPRECATION")
	fun buscar(c: Context, esperaMs: Long): List<Candidato> {
		val nsd = c.getSystemService(NsdManager::class.java) ?: return emptyList()
		val encontrados = java.util.Collections.synchronizedList(ArrayList<Candidato>())
		val pendientes = java.util.concurrent.atomic.AtomicInteger(0)
		val fin = CountDownLatch(1)
		val oyente = object : NsdManager.DiscoveryListener {
			override fun onDiscoveryStarted(tipo: String) = Unit
			override fun onDiscoveryStopped(tipo: String) = Unit
			override fun onStartDiscoveryFailed(tipo: String, error: Int) {
				Registro.w(ETIQUETA, "No se pudo buscar en la wifi ($error)")
				fin.countDown()
			}
			override fun onStopDiscoveryFailed(tipo: String, error: Int) = Unit
			override fun onServiceLost(info: NsdServiceInfo) = Unit
			override fun onServiceFound(info: NsdServiceInfo) {
				pendientes.incrementAndGet()
				runCatching {
					nsd.resolveService(info, object : NsdManager.ResolveListener {
						override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
							pendientes.decrementAndGet()
						}
						override fun onServiceResolved(info: NsdServiceInfo) {
							val ip = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull() else info.host
							if (ip != null) encontrados += Candidato(ip.address, info.port)
							pendientes.decrementAndGet()
						}
					})
				}.onFailure {
					pendientes.decrementAndGet()
					Registro.w(ETIQUETA, "No se pudo resolver un móvil de la wifi", it)
				}
			}
		}
		try {
			nsd.discoverServices(SERVICIO_NSD, NsdManager.PROTOCOL_DNS_SD, oyente)
			fin.await(esperaMs, TimeUnit.MILLISECONDS)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo buscar en la wifi", e)
		} finally {
			runCatching { nsd.stopServiceDiscovery(oyente) }
		}
		return encontrados.toList().distinct()
	}
}
