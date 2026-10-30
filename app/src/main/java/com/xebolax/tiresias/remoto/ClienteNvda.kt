package com.xebolax.tiresias.remoto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.remoto.MensajeNvda
import com.xebolax.tiresias.nucleo.remoto.Nvda
import com.xebolax.tiresias.nucleo.remoto.ParteVoz
import com.xebolax.tiresias.nucleo.remoto.TeclaVk
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Tipo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * El móvil maneja un PC con NVDA o con Tiresias para Windows (Can Talk): un
 * cliente del acceso remoto de NVDA (protocolo 2, JSON por líneas sobre TLS)
 * que entra en el canal como «master» (el que controla). Lo que dice el PC se
 * oye con la voz de Tiresias; las teclas y el braille van al PC.
 *
 * El certificado del servidor se comprueba como en un navegador; si no se
 * puede (un servidor propio, la conexión directa a un PC), se enseña su huella
 * y el usuario decide fiarse una vez o siempre (se guarda la huella, no la
 * clave). En el registro solo van las etapas y los errores: nunca teclas, voz
 * ni claves.
 */
class ClienteNvda(private val c: Context) {
	enum class Estado { CONECTANDO, CONECTADO, SIN_PC, TERMINADO }

	/** El certificado no se pudo comprobar: su huella SHA-256, para preguntar. */
	class CertificadoDesconocido(val huella: String) : IOException("Certificado desconocido")

	@Volatile var estado = Estado.CONECTANDO
		private set
	@Volatile var error: String? = null
		private set
	/** Con la voz del PC callada en el móvil. */
	@Volatile var silenciado = false
	/** Quien quiere saber que algo ha cambiado (la pantalla). */
	@Volatile var alCambiar: (() -> Unit)? = null
	/** Al pedir fiarse de un certificado (la pantalla pregunta y vuelve a conectar). */
	@Volatile var alCertificado: ((String) -> Unit)? = null

	@Volatile private var socket: SSLSocket? = null
	@Volatile private var terminado = false
	private val cola = ArrayBlockingQueue<String>(64)
	/** Las teclas que se han mandado pulsadas y no sueltas: se sueltan al cerrar. */
	private val pulsadas = java.util.Collections.synchronizedSet(LinkedHashSet<TeclaVk>())

	fun conectar(host: String, puerto: Int, clave: String, huellaAceptada: String?) {
		Thread({ bucle(host, puerto, clave, huellaAceptada) }, "tiresias-nvda").start()
	}

	private fun cambiar(e: Estado, motivo: String? = null) {
		estado = e
		if (motivo != null) error = motivo
		alCambiar?.invoke()
	}

	private fun bucle(host: String, puerto: Int, clave: String, huellaAceptada: String?) {
		try {
			val s = abrir(host, puerto, huellaAceptada)
			socket = s
			s.soTimeout = 15_000
			Thread({ escribir(s) }, "tiresias-nvda-envios").start()
			mandar(Nvda.versionProtocolo())
			mandar(Nvda.unirse(clave, controla = true))
			val entrada = s.inputStream
			while (!terminado) {
				val linea = leerLinea(entrada) ?: break
				Nvda.leer(linea)?.let { atender(it, s) }
			}
		} catch (e: CertificadoDesconocido) {
			Registro.w(ETIQUETA, "Certificado sin comprobar")
			cambiar(Estado.TERMINADO, c.getString(R.string.nvda_certificado))
			alCertificado?.invoke(e.huella)
		} catch (e: IOException) {
			if (!terminado) {
				Registro.w(ETIQUETA, "Se cortó la conexión con el PC", e)
				cambiar(Estado.TERMINADO, c.getString(R.string.nvda_cortada))
			}
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Fallo con el PC", e)
			cambiar(Estado.TERMINADO, c.getString(R.string.nvda_cortada))
		} finally {
			terminar()
		}
	}

	private fun abrir(host: String, puerto: Int, huellaAceptada: String?): SSLSocket {
		val porDefecto = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
			.trustManagers.filterIsInstance<X509TrustManager>().first()
		val guardada = huellas()["$host:$puerto"]
		var comprobadoPorHuella = false
		val confianza = object : X509TrustManager {
			override fun checkClientTrusted(cadena: Array<out X509Certificate>?, tipo: String?) = porDefecto.checkClientTrusted(cadena, tipo)
			override fun getAcceptedIssuers(): Array<X509Certificate> = porDefecto.acceptedIssuers
			override fun checkServerTrusted(cadena: Array<out X509Certificate>, tipo: String) {
				try {
					porDefecto.checkServerTrusted(cadena, tipo)
				} catch (e: CertificateException) {
					val huella = huella(cadena[0])
					if (huella != guardada && huella != huellaAceptada) throw CertificateException(PREFIJO_HUELLA + huella, e)
					comprobadoPorHuella = true
				}
			}
		}
		val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(confianza), null) }
		val s = java.net.Socket()
		s.tcpNoDelay = true
		s.keepAlive = true
		s.connect(InetSocketAddress(host, puerto), 10_000)
		// Con el nombre del servidor: así va en el saludo de TLS (SNI) y se comprueba
		val tls = ctx.socketFactory.createSocket(s, host, puerto, true) as SSLSocket
		try {
			tls.startHandshake()
		} catch (e: IOException) {
			val huella = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.firstOrNull { it.startsWith(PREFIJO_HUELLA) }?.removePrefix(PREFIJO_HUELLA)
			runCatching { tls.close() }
			if (huella != null) throw CertificadoDesconocido(huella)
			throw e
		}
		// Con un certificado de verdad, también el nombre (como un navegador); con la huella fijada, ya vale
		if (!comprobadoPorHuella && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.session)) {
			runCatching { tls.close() }
			throw IOException("El certificado no es de $host")
		}
		return tls
	}

	private fun huella(cert: X509Certificate): String =
		MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

	private fun huellas(): Map<String, String> = Ajustes(c).nvdaHuellas.lines().mapNotNull { l ->
		val i = l.lastIndexOf('=')
		if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
	}.toMap()

	/** Se fía siempre de [huella] para [host]:[puerto]. */
	fun guardarHuella(host: String, puerto: Int, huella: String) {
		val todas = huellas().toMutableMap()
		todas["$host:$puerto"] = huella
		Ajustes(c).nvdaHuellas = todas.entries.joinToString("\n") { "${it.key}=${it.value}" }
	}

	/** Una línea del servidor (como mucho 256 KB), o null al cerrarse. */
	private fun leerLinea(entrada: InputStream): String? {
		val b = ByteArrayOutputStream()
		while (true) {
			val x = try {
				entrada.read()
			} catch (e: java.net.SocketTimeoutException) {
				// Sin nada que leer un rato: si ya está dentro, el servidor manda ping de vez en cuando
				if (estado != Estado.CONECTANDO && !terminado) continue else throw e
			}
			if (x < 0) return if (b.size() > 0) b.toString("UTF-8") else null
			if (x == '\n'.code) return b.toString("UTF-8")
			if (b.size() >= MAXIMO_LINEA) throw IOException("Línea demasiado larga")
			b.write(x)
		}
	}

	private fun atender(m: MensajeNvda, s: SSLSocket) {
		when (m) {
			is MensajeNvda.CanalUnido -> {
				s.soTimeout = 0
				val pcs = m.clientes.count { it.tipo == "slave" }
				when {
					pcs > 1 -> {
						cambiar(Estado.TERMINADO, c.getString(R.string.nvda_varios))
						terminar()
					}
					pcs == 1 -> cambiar(Estado.CONECTADO)
					else -> cambiar(Estado.SIN_PC)
				}
			}
			is MensajeNvda.ClienteEntra -> if (m.cliente.tipo == "slave") cambiar(Estado.CONECTADO)
			is MensajeNvda.ClienteSale -> if (m.cliente.tipo == "slave") cambiar(Estado.SIN_PC)
			is MensajeNvda.Hablar -> hablar(m.partes, m.prioridad)
			is MensajeNvda.Callar -> ServicioTiresias.instancia?.lectorActual?.voz?.callar()
			is MensajeNvda.Portapapeles -> {
				runCatching { c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("NVDA", m.texto)) }
				decir(c.getString(R.string.nvda_portapapeles))
			}
			is MensajeNvda.Aviso -> decir(m.texto)
			is MensajeNvda.Error -> {
				cambiar(Estado.TERMINADO, c.getString(R.string.nvda_error, m.texto))
				terminar()
			}
			is MensajeNvda.VersionDistinta -> {
				cambiar(Estado.TERMINADO, c.getString(R.string.nvda_version))
				terminar()
			}
			else -> Unit
		}
	}

	/** Lo que dice el PC, con la voz de Tiresias, en el idioma que diga NVDA. */
	private fun hablar(partes: List<ParteVoz>, prioridad: Int) {
		if (silenciado) return
		val voz = ServicioTiresias.instancia?.lectorActual?.voz ?: return
		var idioma: Locale? = null
		var deletrear = false
		val texto = StringBuilder()
		var primero = true
		fun soltar() {
			if (texto.isBlank()) return
			voz.decir(Enunciado(texto.toString(), if (primero && prioridad > 0) Modo.INTERRUMPIR else Modo.ENCOLAR, Tipo.SISTEMA, idioma = idioma, deletrear = deletrear))
			primero = false
			texto.clear()
		}
		for (p in partes) when (p) {
			is ParteVoz.Texto -> texto.append(p.texto).append(' ')
			is ParteVoz.Idioma -> {
				soltar()
				idioma = p.idioma?.replace('_', '-')?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
			}
			is ParteVoz.Deletrear -> {
				soltar()
				deletrear = p.si
			}
			is ParteVoz.Pausa -> texto.append(". ")
		}
		soltar()
	}

	private fun decir(texto: String) {
		ServicioTiresias.instancia?.lectorActual?.decir(texto, Modo.ENCOLAR, Tipo.SISTEMA)
	}

	// --- Lo que va al PC ----------------------------------------------------------------------------

	private fun mandar(linea: String): Boolean = cola.offer(if (linea.endsWith("\n")) linea else linea + "\n")

	private fun escribir(s: SSLSocket) {
		try {
			val salida = s.outputStream
			while (!terminado || cola.isNotEmpty()) {
				val linea = cola.poll(500, TimeUnit.MILLISECONDS) ?: continue
				salida.write(linea.toByteArray(Charsets.UTF_8))
				salida.flush()
			}
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
		} catch (e: IOException) {
			if (!terminado) Registro.w(ETIQUETA, "No se pudo mandar al PC", e)
		}
	}

	/** Pulsa y suelta una combinación (los modificadores primero). */
	fun teclas(combinacion: List<TeclaVk>) {
		if (estado != Estado.CONECTADO) return
		Nvda.combinacion(combinacion).forEach { mandar(it) }
	}

	/** Deja pulsada o suelta una tecla (Alt para Alt+Tab mantenido). */
	fun tecla(t: TeclaVk, pulsada: Boolean) {
		if (estado != Estado.CONECTADO) return
		if (pulsada) pulsadas += t else pulsadas -= t
		mandar(Nvda.tecla(t.vk, 0, t.extendida, pulsada))
	}

	fun puntos(puntos: Int, espacio: Boolean = false) {
		if (estado == Estado.CONECTADO) mandar(Nvda.puntosBraille(puntos, espacio))
	}

	fun ordenBraille(guion: String) {
		if (estado == Estado.CONECTADO) mandar(Nvda.ordenBraille(guion))
	}

	/** Manda [texto] al portapapeles del PC y lo pega con Control+V. */
	fun pegarTexto(texto: String) {
		if (estado != Estado.CONECTADO) return
		mandar(Nvda.portapapeles(texto.take(MAXIMO_TEXTO)))
		teclas(listOf(TeclaVk.CONTROL, TeclaVk.V))
	}

	fun ctrlAltSupr() {
		if (estado == Estado.CONECTADO) mandar(Nvda.ctrlAltSupr())
	}

	fun terminar() {
		if (terminado) return
		// Antes de cerrar, se sueltan las teclas que se quedaron pulsadas (si no, el PC tendría Alt pegado)
		synchronized(pulsadas) {
			for (t in pulsadas) mandar(Nvda.tecla(t.vk, 0, t.extendida, false))
			pulsadas.clear()
		}
		terminado = true
		val s = socket
		Thread({
			try {
				Thread.sleep(500)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
			}
			runCatching { s?.close() }
		}, "tiresias-nvda-cierre").start()
		if (estado != Estado.TERMINADO) cambiar(Estado.TERMINADO)
	}

	companion object {
		private const val ETIQUETA = "ClienteNvda"
		private const val MAXIMO_LINEA = 256 * 1024
		private const val MAXIMO_TEXTO = 8192
		private const val PREFIJO_HUELLA = "tiresias-huella:"
	}
}
