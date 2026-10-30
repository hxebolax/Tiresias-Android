package com.xebolax.tiresias.remoto

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.remoto.Candidato
import com.xebolax.tiresias.nucleo.remoto.CodigoCorto
import com.xebolax.tiresias.nucleo.remoto.Conectar
import com.xebolax.tiresias.nucleo.remoto.Emparejar
import com.xebolax.tiresias.nucleo.remoto.Enlace
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import com.xebolax.tiresias.nucleo.remoto.MensajeAyuda
import com.xebolax.tiresias.nucleo.remoto.Socks5
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo
import java.io.IOException
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Este móvil, cuando ayuda a otro ([Asistencia]): se enlaza directamente con
 * él (las direcciones de la invitación, o las que encuentra en la wifi con el
 * código corto), dice lo que dice el otro móvil con un tono algo distinto y le
 * manda lo que se pide en la pantalla de ayudar o, en «manejar con mis
 * gestos», los gestos de Tiresias. Si no llega directamente y la invitación
 * trae un servicio onion, entra por [Tor]. Si la red se corta, vuelve a probar
 * durante dos minutos.
 */
class SesionAyudante(private val c: Context, private val invitacion: Invitacion?, private val codigoCorto: String?) {
	enum class Estado { CONECTANDO, CONECTADA, CORTADA, TERMINADA }

	@Volatile var estado = Estado.CONECTANDO
		private set
	@Volatile var nombreOtro: String = invitacion?.nombre.orEmpty()
		private set
	/** Las cuatro cifras que dicen los dos móviles (para quien quiera comprobar que no hay nadie en medio). */
	@Volatile var comprobacion: String = ""
		private set
	/** Por qué terminó (una de las FIN_ de aquí o de [SesionAyudado]). */
	@Volatile var motivoFin: String? = null
		private set
	/** Si está probando por Tor: tarda más y hay que decirlo. */
	@Volatile var porTor = false
		private set
	/** La ficha del otro móvil (batería, red, volumen...), si ya ha llegado. */
	@Volatile var ficha: List<String> = emptyList()
		private set
	/** Lo último que ha dicho el otro móvil, para quien ayuda mirando la pantalla. */
	private val ultimas = ArrayDeque<String>()
	/** Si se calla la voz que llega del otro móvil. */
	@Volatile var silenciada = false

	val conectada: Boolean get() = estado == Estado.CONECTADA

	/** Lo que llega, para la pantalla de ayudar (en el hilo de la red). Sin [alVoz], dice la voz Tiresias si está activo. */
	@Volatile var alVoz: ((MensajeAyuda.Voz) -> Unit)? = null
	@Volatile var alCallar: (() -> Unit)? = null
	@Volatile var alElementos: ((MensajeAyuda.Elementos) -> Unit)? = null
	@Volatile var alApps: ((MensajeAyuda.Apps) -> Unit)? = null
	@Volatile var alRespuesta: ((MensajeAyuda.Respuesta) -> Unit)? = null
	@Volatile var alEstadoOtro: ((MensajeAyuda.Estado) -> Unit)? = null
	/** Ha cambiado lo último dicho: se pasan las líneas (la más nueva, la última). */
	@Volatile var alTexto: ((List<String>) -> Unit)? = null

	@Volatile private var enlace: Enlace? = null
	@Volatile private var socket: Socket? = null
	@Volatile private var terminada = false
	private var torEncendido = false
	private val peticiones = AtomicLong()
	private val envios = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(200), { r -> Thread(r, "tiresias-ayudante-envios").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())

	fun nuevaPeticion(): Long = peticiones.incrementAndGet()

	fun conectar() {
		Thread({ bucle() }, "tiresias-ayudante").start()
	}

	private fun bucle() {
		val inicio = SystemClock.uptimeMillis()
		var cortadaDesde = 0L
		while (!terminada) {
			val estuvo = try {
				intentar()
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "La conexión con el otro móvil falló", e)
				false
			}
			if (terminada) break
			val ahora = SystemClock.uptimeMillis()
			if (estuvo || estado == Estado.CONECTADA) {
				estado = Estado.CORTADA
				cortadaDesde = ahora
				Asistencia.avisarCambio()
			}
			when {
				estado == Estado.CONECTANDO && ahora - inicio > (if (invitacion?.onion != null) PRIMERA_TOR_MS else PRIMERA_MS) -> return terminar(FIN_NO_LLEGA)
				estado == Estado.CORTADA && ahora - cortadaDesde > VOLVER_MS -> return terminar(FIN_CORTADA)
			}
			try {
				Thread.sleep(ESPERA_REINTENTO_MS)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
		}
	}

	/** Un intento: conectar, saludarse y leer hasta que se corte. Devuelve si llegó a enlazarse. */
	private fun intentar(): Boolean {
		val secreto = invitacion?.secreto ?: CodigoCorto.secreto(codigoCorto ?: return false)
		val candidatos: List<Candidato> = invitacion?.candidatos ?: Red.buscar(c, BUSCAR_WIFI_MS)
		// Con la invitación, el primero que conteste y, si nadie contesta, por Tor; con el código corto, uno a uno (puede haber varios esperando en la wifi)
		val probar = if (invitacion != null) listOfNotNull(Conectar.primero(candidatos, if (invitacion.onion != null) DIRECTO_MS else CONECTAR_MS) ?: conectarPorTor(invitacion)) else candidatos.mapNotNull { Conectar.primero(listOf(it), CONECTAR_MS) }
		if (probar.isEmpty()) return false
		for (sock in probar) {
			if (terminada) return false
			try {
				sock.tcpNoDelay = true
				sock.soTimeout = ESPERA_SALUDO_MS
				val e = Enlace.cliente(sock.getInputStream(), sock.getOutputStream(), secreto)
				val pruebas = Confianza.ayudados(c).map { Emparejar.prueba(it, e.comprobacion) }
				e.enviarTexto(MensajeAyuda.Hola(nombrePropio(), BuildConfig.VERSION_NAME, Idioma.efectivo(c).toLanguageTag(), true, pruebas).json())
				when (val respuesta = MensajeAyuda.leer(e.recibirTexto())) {
					is MensajeAyuda.Hola -> nombreOtro = respuesta.nombre.take(40).ifBlank { nombreOtro }
					is MensajeAyuda.Fin -> {
						cerrar(sock)
						terminar(respuesta.motivo)
						return false
					}
					else -> {
						cerrar(sock)
						continue
					}
				}
				comprobacion = e.comprobacion
				enlace = e
				socket = sock
				sock.soTimeout = ESPERA_LECTURA_MS
				estado = Estado.CONECTADA
				Asistencia.avisarCambio()
				Thread({ latir(sock) }, "tiresias-ayudante-ping").start()
				leer(sock, e)
				return true
			} catch (x: IOException) {
				Registro.w(ETIQUETA, "No se pudo enlazar con un candidato", x)
				cerrar(sock)
				if (socket === sock) return true
			}
		}
		return false
	}

	/** Entra por el servicio onion del otro móvil. Mientras este lo anuncia, Tor contesta que no llega: el bucle vuelve a probar. */
	private fun conectarPorTor(inv: Invitacion): Socket? {
		val onion = inv.onion ?: return null
		if (!Ajustes(c).ayudaTor) return null
		if (!porTor) {
			porTor = true
			Asistencia.avisarCambio()
		}
		if (!torEncendido) {
			if (!Tor.encender(c)) return null
			torEncendido = true
		}
		val socks = Tor.puertoSocks() ?: return null
		return try {
			Socks5.conectar(socks, onion.host, onion.puerto, TOR_MS)
		} catch (e: IOException) {
			Registro.w(ETIQUETA, "Tor aún no llega al otro móvil", e)
			null
		}
	}

	private fun leer(sock: Socket, e: Enlace) {
		try {
			while (!terminada && socket === sock) {
				val m = MensajeAyuda.leer(e.recibirTexto()) ?: continue
				atender(m)
			}
		} finally {
			if (socket === sock) {
				enlace = null
				socket = null
			}
			cerrar(sock)
		}
	}

	private fun latir(sock: Socket) {
		while (!terminada && socket === sock) {
			try {
				Thread.sleep(LATIDO_MS)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
			enviar(MensajeAyuda.Ping)
		}
	}

	private fun atender(m: MensajeAyuda) {
		when (m) {
			is MensajeAyuda.Voz -> hablar(m)
			is MensajeAyuda.Callar -> {
				alCallar?.invoke() ?: ServicioTiresias.instancia?.lectorActual?.voz?.callar()
			}
			is MensajeAyuda.Sonido -> if (!silenciada) Sonido.entries.firstOrNull { it.name == m.nombre }?.let { s ->
				ServicioTiresias.instancia?.lectorActual?.sonidos?.tocar(s)
			}
			is MensajeAyuda.Estado -> {
				alEstadoOtro?.invoke(m)
				m.texto?.let { hablar(MensajeAyuda.Voz(it, false, false, Tipo.SISTEMA.name, null)) }
			}
			is MensajeAyuda.Ficha -> {
				ficha = m.lineas
				Asistencia.avisarCambio()
			}
			is MensajeAyuda.Pareja -> Emparejar.deMensaje(m.id, m.secreto, m.nombre, System.currentTimeMillis() / 1000)?.let { p ->
				Confianza.anadirAyudado(c, p)
				nombreOtro = p.nombre.take(40).ifBlank { nombreOtro }
				hablar(MensajeAyuda.Voz(Idioma.envolver(c).getString(R.string.ayudar_ahora_confianza, nombreOtro), false, false, Tipo.SISTEMA.name, null))
			}
			is MensajeAyuda.Elementos -> alElementos?.invoke(m)
			is MensajeAyuda.Apps -> alApps?.invoke(m)
			is MensajeAyuda.Respuesta -> {
				alRespuesta?.invoke(m)
				m.texto?.let { hablar(MensajeAyuda.Voz(it, false, false, Tipo.SISTEMA.name, null)) }
			}
			is MensajeAyuda.Fin -> terminar(FIN_OTRO)
			else -> Unit
		}
	}

	/** Dice lo que dice el otro móvil: con la voz de Tiresias (algo más grave) o con la de la pantalla de ayudar. */
	private fun hablar(v: MensajeAyuda.Voz) {
		val lineas = synchronized(ultimas) {
			ultimas.addLast(v.texto)
			while (ultimas.size > MAXIMO_ULTIMAS) ultimas.removeFirst()
			ultimas.toList()
		}
		alTexto?.invoke(lineas)
		if (silenciada) return
		alVoz?.let {
			it(v)
			return
		}
		val lector = ServicioTiresias.instancia?.lectorActual ?: return
		val idioma = v.idioma?.let { runCatching { Locale.forLanguageTag(it) }.getOrNull() }
		lector.voz.decir(Enunciado(v.texto, if (v.interrumpir) Modo.INTERRUMPIR else Modo.ENCOLAR, Tipo.SISTEMA, idioma = idioma, tono = TONO_REMOTO, deletrear = v.deletrear))
	}

	/** Manda [m] al otro móvil sin esperar; devuelve si está enlazado. */
	fun enviar(m: MensajeAyuda): Boolean {
		val e = enlace ?: return false
		val texto = m.json()
		runCatching {
			envios.execute {
				try {
					e.enviarTexto(texto)
				} catch (x: IOException) {
					Registro.w(ETIQUETA, "No se pudo mandar al otro móvil", x)
				}
			}
		}.onFailure { Registro.w(ETIQUETA, "La cola de envíos está cerrada", it) }
		return true
	}

	private fun nombrePropio(): String = Ajustes(c).ayudaNombre.ifBlank {
		runCatching { Settings.Global.getString(c.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.ifBlank { null } ?: android.os.Build.MODEL
	}

	fun terminar(motivo: String) {
		if (terminada) return
		terminada = true
		motivoFin = motivo
		val e = enlace
		val s = socket
		Thread({
			if (motivo == FIN_USUARIO || motivo == FIN_OTRA) runCatching { e?.enviarTexto(MensajeAyuda.Fin(motivo).json()) }
			s?.let { cerrar(it) }
			envios.shutdown()
		}, "tiresias-ayudante-fin").start()
		enlace = null
		socket = null
		estado = Estado.TERMINADA
		if (torEncendido) Tor.soltar(c)
		torEncendido = false
		Registro.i(ETIQUETA, "Ayuda terminada: $motivo")
		Asistencia.alTerminarAyudante(this)
	}

	private fun cerrar(s: Socket) {
		runCatching { s.close() }.onFailure { Registro.w(ETIQUETA, "No se pudo cerrar la conexión", it) }
	}

	companion object {
		private const val ETIQUETA = "SesionAyudante"
		private const val PRIMERA_MS = 40_000L
		/** Por Tor, la primera vez que se enciende en mucho tiempo puede tardar más de dos minutos. */
		private const val PRIMERA_TOR_MS = 5 * 60_000L
		private const val DIRECTO_MS = 5000
		private const val TOR_MS = 60_000
		private const val VOLVER_MS = 2 * 60_000L
		private const val ESPERA_REINTENTO_MS = 3000L
		private const val CONECTAR_MS = 8000
		private const val BUSCAR_WIFI_MS = 4000L
		private const val ESPERA_SALUDO_MS = 70_000
		private const val ESPERA_LECTURA_MS = 90_000
		private const val LATIDO_MS = 20_000L
		private const val MAXIMO_ULTIMAS = 6
		/** El tono de la voz que llega del otro móvil: algo más grave, para no confundirla con la propia. */
		const val TONO_REMOTO = 85

		const val FIN_USUARIO = "usuario"
		const val FIN_OTRA = "otra"
		const val FIN_OTRO = "otro"
		const val FIN_NO_LLEGA = "no_llega"
		const val FIN_CORTADA = "cortada"
	}
}
