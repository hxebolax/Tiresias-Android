package com.xebolax.tiresias.remoto

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import com.xebolax.tiresias.base.Registro
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.torproject.jni.TorService

/**
 * Tor para la ayuda remota: lo que hace posible que dos móviles se enlacen con
 * datos móviles en los dos lados, sin abrir puertos y sin un servidor propio. Los
 * dos llaman hacia fuera a la red Tor, así que el CGNAT de los operadores no estorba.
 *
 * Solo se enciende mientras se espera o se da una ayuda, y va en el proceso `:tor`
 * ([ServicioTor]): si falla, el lector sigue hablando. Por dentro, todo sigue
 * cifrado de extremo a extremo con [com.xebolax.tiresias.nucleo.remoto.Enlace].
 */
object Tor {
	private const val ETIQUETA = "Tor"
	private const val ESPERA_MS = 20_000L

	private var enlaces = 0
	private var hilo: HandlerThread? = null
	private var conexion: ServiceConnection? = null
	@Volatile private var mensajero: Messenger? = null

	/** Lo enciende (o se apunta a él si ya lo está). Bloquea hasta tener el servicio; false si no se ha podido. */
	@Synchronized
	fun encender(c: Context): Boolean {
		enlaces++
		if (mensajero != null) return true
		val listo = CountDownLatch(1)
		val app = c.applicationContext
		val con = object : ServiceConnection {
			override fun onServiceConnected(nombre: ComponentName?, servicio: IBinder?) {
				mensajero = Messenger(servicio)
				listo.countDown()
			}

			override fun onServiceDisconnected(nombre: ComponentName?) {
				Registro.w(ETIQUETA, "El proceso de Tor se ha cerrado")
				mensajero = null
			}
		}
		conexion = con
		val h = HandlerThread("tiresias-tor-respuestas").apply { start() }
		hilo = h
		val enlazado = runCatching { app.bindService(Intent(app, ServicioTor::class.java), con, Context.BIND_AUTO_CREATE) }
			.onFailure { Registro.w(ETIQUETA, "No se pudo enlazar con Tor", it) }.getOrDefault(false)
		if (!enlazado || !listo.await(ESPERA_MS, TimeUnit.MILLISECONDS)) {
			apagarYa(app)
			return false
		}
		return true
	}

	/** Deja de usarlo; al soltarlo el último, se apaga su proceso entero. */
	@Synchronized
	fun soltar(c: Context) {
		if (enlaces > 0) enlaces--
		if (enlaces == 0) apagarYa(c.applicationContext)
	}

	private fun apagarYa(app: Context) {
		enlaces = 0
		conexion?.let { con -> runCatching { app.unbindService(con) }.onFailure { Registro.w(ETIQUETA, "No se pudo soltar Tor", it) } }
		conexion = null
		mensajero = null
		hilo?.quitSafely()
		hilo = null
	}

	/** Publica un servicio onion que lleva a [puertoLocal]: da su dirección sin esperar a que se anuncie. Bloquea. */
	fun publicar(puertoLocal: Int, puertoOnion: Int): String? =
		pedir(ServicioTor.PUBLICAR, puertoLocal, puertoOnion)?.getString(ServicioTor.CLAVE_ONION)

	/** El puerto SOCKS de Tor, o null si aún no está. Bloquea. */
	fun puertoSocks(): Int? = pedir(ServicioTor.SOCKS, 0, 0)?.getInt(ServicioTor.CLAVE_SOCKS)?.takeIf { it > 0 }

	private fun pedir(que: Int, a: Int, b: Int): Bundle? {
		val m = mensajero ?: return null
		val h = hilo ?: return null
		val listo = CountDownLatch(1)
		var respuesta: Bundle? = null
		val pedido = Message.obtain(null, que, a, b).apply {
			replyTo = Messenger(Handler(h.looper) { r ->
				respuesta = r.data
				listo.countDown()
				true
			})
		}
		return try {
			m.send(pedido)
			listo.await(ESPERA_MS + 5_000, TimeUnit.MILLISECONDS)
			respuesta
		} catch (e: RemoteException) {
			Registro.w(ETIQUETA, "No se pudo hablar con Tor", e)
			null
		}
	}
}

/**
 * Tor en su proceso. Vive mientras haya alguien enlazado ([Tor]) y, al cerrarse,
 * termina el proceso para que no quede nada en marcha gastando batería.
 */
class ServicioTor : Service() {
	private val hilo = HandlerThread("tiresias-tor").apply { start() }
	@Volatile private var tor: TorService? = null

	private val conexion = object : ServiceConnection {
		override fun onServiceConnected(nombre: ComponentName?, servicio: IBinder?) {
			tor = (servicio as? TorService.LocalBinder)?.service
		}

		override fun onServiceDisconnected(nombre: ComponentName?) {
			tor = null
		}
	}

	private val mensajero = Messenger(Handler(hilo.looper) { m ->
		val datos = Bundle()
		try {
			when (m.what) {
				PUBLICAR -> publicar(m.arg1, m.arg2)?.let { datos.putString(CLAVE_ONION, it) }
				SOCKS -> tor?.takeIf { it.torControlConnection != null }?.socksPort?.let { datos.putInt(CLAVE_SOCKS, it) }
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Tor no ha podido hacer lo pedido", e)
		}
		runCatching { m.replyTo?.send(Message.obtain().apply { data = datos }) }.onFailure { Registro.w(ETIQUETA, "No se pudo responder", it) }
		true
	})

	override fun onCreate() {
		super.onCreate()
		bindService(Intent(this, TorService::class.java), conexion, Context.BIND_AUTO_CREATE)
	}

	override fun onBind(intent: Intent?): IBinder = mensajero.binder

	private fun publicar(puertoLocal: Int, puertoOnion: Int): String? {
		val hasta = SystemClock.uptimeMillis() + ESPERA_MS
		while (tor?.torControlConnection == null && SystemClock.uptimeMillis() < hasta) SystemClock.sleep(100)
		val control = tor?.torControlConnection ?: return null
		val r = control.addOnion("NEW:ED25519-V3", mapOf(puertoOnion to "127.0.0.1:$puertoLocal"))
		return r["onionAddress"]?.removeSuffix(".onion")
	}

	override fun onDestroy() {
		runCatching { unbindService(conexion) }.onFailure { Registro.w(ETIQUETA, "No se pudo soltar el servicio de Tor", it) }
		super.onDestroy()
		Process.killProcess(Process.myPid())
	}

	companion object {
		private const val ETIQUETA = "ServicioTor"
		private const val ESPERA_MS = 20_000L
		const val PUBLICAR = 1
		const val SOCKS = 2
		const val CLAVE_ONION = "onion"
		const val CLAVE_SOCKS = "socks"
	}
}
