package com.xebolax.tiresias.remoto

import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.CopiaSeguridad
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.remoto.Candidato
import com.xebolax.tiresias.nucleo.remoto.ClaveIncorrecta
import com.xebolax.tiresias.nucleo.remoto.CodigoCorto
import com.xebolax.tiresias.nucleo.remoto.ElementoRemoto
import com.xebolax.tiresias.nucleo.remoto.Emparejar
import com.xebolax.tiresias.nucleo.remoto.Enlace
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import com.xebolax.tiresias.nucleo.remoto.Mapeo
import com.xebolax.tiresias.nucleo.remoto.MensajeAyuda
import com.xebolax.tiresias.nucleo.remoto.Onion
import com.xebolax.tiresias.nucleo.remoto.Pareja
import com.xebolax.tiresias.nucleo.remoto.Protegido
import com.xebolax.tiresias.nucleo.remoto.Puertos
import com.xebolax.tiresias.servicio.Guarda
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo
import com.xebolax.tiresias.voz.VozEnLlamadas
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Este móvil, cuando lo ayudan desde otro ([Asistencia]). Escucha en un puerto
 * TCP propio (lo pide abierto al router si se puede y, con [Tor], lo publica como
 * servicio onion para llegar por datos móviles), espera al ayudante con el secreto
 * de la invitación o con el código corto de la misma wifi, y, enlazados, le manda
 * lo que dice Tiresias y sus sonidos y hace lo que le pide.
 *
 * Límites: la invitación caduca a los 10 minutos si nadie entra; la ayuda dura
 * como mucho lo elegido (60 minutos de fábrica); se termina sola a los 15
 * minutos sin que el ayudante haga nada o a los 2 minutos de cortarse la
 * conexión si no vuelve. Tras 5 intentos con una clave equivocada se deja de
 * esperar. Con el móvil bloqueado no se hace nada: el PIN lo pone la persona.
 *
 * Solo entra quien la persona acepta ([Confianza]). En una app de banca o pagos la
 * ayuda se queda en pausa: ni voz ni órdenes. Los códigos no salen del móvil.
 */
class SesionAyudado(private val servicio: ServicioTiresias, private val lector: Lector) {
	private val ajustes = lector.ajustes
	private val t get() = lector.textos
	private val principal = Handler(Looper.getMainLooper())

	lateinit var invitacion: Invitacion
		private set
	/** Las ocho cifras para dictar por teléfono si los dos están en la misma wifi. */
	val codigoCorto: String = CodigoCorto.nuevo()

	@Volatile var activa = true
		private set
	/** Si ya ha entrado el ayudante (aunque la conexión se corte un momento). */
	@Volatile var conectada = false
		private set
	@Volatile var nombreAyudante: String = ""
		private set
	/** Si el router ha abierto un puerto o hay Tor: se puede entrar desde fuera de casa. */
	@Volatile var desdeFuera = false
		private set
	private var conTor = false
	/** La persona de confianza que está dentro, si lo es. */
	@Volatile private var persona: Pareja? = null
	/** En una app de banca o pagos: no se manda nada y no se obedece nada. */
	@Volatile private var protegida = false
	/** Mientras se le pregunta algo a la persona, lo que venga de fuera no puede contestar por ella. */
	@Volatile private var preguntando = false
	private var empezadaEn = 0L
	private var copiaAntes: String? = null
	private var pasosSeguimiento = 0

	private var servidor: ServerSocket? = null
	private var mapeo: Mapeo? = null
	private var quitarAnuncio: () -> Unit = {}
	@Volatile private var enlace: Enlace? = null
	@Volatile private var socket: Socket? = null
	@Volatile private var fallos = 0
	@Volatile private var ultimoMensaje = 0L
	@Volatile private var cortadaDesde = 0L
	private var vistaEncendida: View? = null
	/** Los elementos que se mandaron la última vez, para usarlos por su número. */
	private var ultimosNodos: List<AccessibilityNodeInfo> = emptyList()

	/** Los mensajes salen en orden por su propio hilo: la voz no espera a la red. Si la red se atasca, se tiran los más nuevos. */
	private val envios = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(400), { r -> Thread(r, "tiresias-ayuda-envios").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())

	/** Abre el puerto, apunta las direcciones y da la invitación; null si no hay red. Bloquea unos segundos (el router). */
	fun empezar(): Invitacion? {
		val s = abrirServidor() ?: return null
		servidor = s
		val puerto = s.localPort
		val candidatos = ArrayList(Red.candidatos(puerto))
		if (ajustes.ayudaRouter) {
			Red.wifi(servicio)?.let { (ip, router) ->
				val segundos = (ajustes.ayudaMinutos + MINUTOS_INVITACION + 5) * 60
				val m = Red.conMultidifusion(servicio) { Puertos.abrir(ip, puerto, router, puerto, segundos, "Tiresias") }
				if (m != null) {
					mapeo = m
					desdeFuera = true
					candidatos += Candidato(m.ipPublica.address, m.puertoPublico)
				}
			}
		}
		val onion = if (ajustes.ayudaTor && Red.hayInternet(servicio)) publicarOnion(puerto) else null
		if (candidatos.isEmpty() && onion == null) {
			cerrarTodo()
			lector.decir(R.string.ayuda_sin_red)
			return null
		}
		invitacion = Invitacion.nueva(nombrePropio(), candidatos, MINUTOS_INVITACION, System.currentTimeMillis() / 1000, onion)
		quitarAnuncio = Red.anunciar(servicio, puerto, nombrePropio())
		Thread({ aceptar(s) }, "tiresias-ayuda-escucha").start()
		lector.manejador.postDelayed(caducar, MINUTOS_INVITACION * 60_000L)
		Asistencia.notificarAyudado(servicio, t.getString(R.string.ayuda_esperando))
		Registro.i(ETIQUETA, "Esperando ayuda: ${candidatos.size} direcciones, desde fuera: $desdeFuera")
		return invitacion
	}

	/** Enciende Tor y publica el puerto como servicio onion. La dirección sale al momento; anunciarse tarda más. */
	private fun publicarOnion(puerto: Int): Onion? {
		if (!Tor.encender(servicio)) return null
		conTor = true
		val direccion = Tor.publicar(puerto, PUERTO_ONION)
		if (direccion == null) {
			Registro.w(ETIQUETA, "Tor no ha dado dirección")
			return null
		}
		desdeFuera = true
		return Onion(direccion, PUERTO_ONION)
	}

	/** El nombre con el que se presenta este móvil: el elegido o el del aparato. */
	fun nombrePropio(): String = ajustes.ayudaNombre.ifBlank {
		runCatching { Settings.Global.getString(servicio.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.ifBlank { null } ?: android.os.Build.MODEL
	}

	private fun abrirServidor(): ServerSocket? {
		repeat(8) {
			val puerto = PUERTO_MINIMO + Random.nextInt(PUERTO_MAXIMO - PUERTO_MINIMO)
			try {
				return ServerSocket().apply {
					reuseAddress = true
					bind(InetSocketAddress(puerto), 4)
				}
			} catch (e: IOException) {
				Registro.w(ETIQUETA, "El puerto $puerto no está libre", e)
			}
		}
		lector.decir(R.string.ayuda_sin_puerto)
		return null
	}

	private val caducar = Runnable { if (!conectada) terminar(FIN_CADUCADA) }

	private fun aceptar(s: ServerSocket) {
		while (activa) {
			val sock = try {
				s.accept()
			} catch (e: IOException) {
				if (activa) Registro.w(ETIQUETA, "Se dejó de esperar al ayudante", e)
				break
			}
			Thread({ atender(sock) }, "tiresias-ayuda-entrada").start()
		}
	}

	private fun atender(sock: Socket) {
		try {
			sock.tcpNoDelay = true
			sock.soTimeout = ESPERA_SALUDO_MS
			val e = try {
				Enlace.servidor(sock.getInputStream(), sock.getOutputStream(), listOf(invitacion.secreto, CodigoCorto.secreto(codigoCorto)))
			} catch (x: ClaveIncorrecta) {
				fallos++
				Registro.w(ETIQUETA, "Alguien ha probado una clave equivocada ($fallos)")
				cerrar(sock)
				if (fallos >= MAXIMO_FALLOS) lector.manejador.post { terminar(FIN_FALLOS) }
				return
			}
			val hola = MensajeAyuda.leer(e.recibirTexto()) as? MensajeAyuda.Hola
			if (hola == null || !hola.ayudante || !activa) {
				cerrar(sock)
				return
			}
			val conocida = Emparejar.comprobar(Confianza.personas(servicio), hola.pruebas, e.comprobacion)
			// Si vuelve quien estaba: con una persona de confianza dentro, nadie más puede ocupar su sitio
			val vuelve = conectada && (persona == null || persona?.id == conocida?.id)
			if (conectada && !vuelve) {
				cerrar(sock)
				return
			}
			if (!vuelve && !dejarEntrar(hola.nombre, conocida)) {
				runCatching { e.enviarTexto(MensajeAyuda.Fin(FIN_RECHAZADA).json()) }
				cerrar(sock)
				return
			}
			if (!vuelve) persona = conocida
			e.enviarTexto(MensajeAyuda.Hola(nombrePropio(), BuildConfig.VERSION_NAME, Idioma.efectivo(servicio).toLanguageTag(), false).json())
			// Si el ayudante vuelve tras cortarse la red, esta conexión sustituye a la vieja
			socket?.let { viejo -> if (viejo !== sock) cerrar(viejo) }
			socket = sock
			enlace = e
			sock.soTimeout = ESPERA_LECTURA_MS
			ultimoMensaje = SystemClock.uptimeMillis()
			cortadaDesde = 0L
			lector.manejador.post { if (vuelve) alVolver() else alConectar(conocida?.nombre ?: hola.nombre) }
			leer(sock, e)
		} catch (x: IOException) {
			Registro.w(ETIQUETA, "Se cortó la conexión con el ayudante", x)
			cerrar(sock)
		} catch (x: Exception) {
			Registro.e(ETIQUETA, "Fallo con el ayudante", x)
			cerrar(sock)
		} finally {
			if (socket === sock && activa) {
				enlace = null
				cortadaDesde = SystemClock.uptimeMillis()
				lector.manejador.post { if (activa && conectada) lector.decir(t.getString(R.string.ayuda_cortada, nombreAyudante), Modo.ENCOLAR, Tipo.SISTEMA) }
				Asistencia.avisarCambio()
			}
		}
	}

	private fun leer(sock: Socket, e: Enlace) {
		while (activa && socket === sock) {
			val m = MensajeAyuda.leer(e.recibirTexto()) ?: continue
			ultimoMensaje = SystemClock.uptimeMillis()
			lector.manejador.post { if (activa) atenderMensaje(m) }
		}
	}

	/**
	 * Si puede entrar quien llama. Una persona de confianza entra preguntando (o, si se
	 * ha quitado, con unos segundos para decir que no). A las demás se les pregunta
	 * siempre, nunca durante una llamada: es el momento en el que un estafador dicta.
	 */
	private fun dejarEntrar(nombre: String, conocida: Pareja?): Boolean {
		if (conocida != null) {
			if (ajustes.ayudaPreguntar) return preguntar(t.getString(R.string.ayuda_pregunta, conocida.nombre))
			lector.manejador.post { lector.decir(t.getString(R.string.ayuda_va_a_entrar, conocida.nombre), Modo.INTERRUMPIR, Tipo.SISTEMA) }
			SystemClock.sleep(ESPERA_ENTRAR_MS)
			return activa
		}
		if (VozEnLlamadas.enLlamada(servicio)) {
			lector.manejador.post { lector.decir(t.getString(R.string.ayuda_en_llamada), Modo.INTERRUMPIR, Tipo.SISTEMA) }
			return false
		}
		if (!Confianza.puedeEntrarNuevo(servicio)) {
			lector.manejador.post { lector.decir(t.getString(R.string.ayuda_no_en_lista), Modo.INTERRUMPIR, Tipo.SISTEMA) }
			return false
		}
		return preguntar(t.getString(R.string.ayuda_pregunta_nueva, nombre))
	}

	/** Pregunta [titulo] a la persona con Sí y No. Espera su respuesta un minuto; lo que venga de fuera no vale. */
	private fun preguntar(titulo: String, si: String = t.getString(R.string.ayuda_pregunta_si), no: String = t.getString(R.string.ayuda_pregunta_no)): Boolean {
		val ventanas = servicio.ventanasActuales ?: return false
		val fin = CountDownLatch(1)
		var respuesta = false
		preguntando = true
		lector.manejador.post {
			lector.decir(titulo, Modo.INTERRUMPIR, Tipo.SISTEMA)
			ventanas.lista(titulo, listOf(si, no), alCancelar = { fin.countDown() }) { i ->
				respuesta = i == 0
				fin.countDown()
			}
		}
		fin.await(60, TimeUnit.SECONDS)
		preguntando = false
		return respuesta
	}

	/** Un rato después de entrar alguien nuevo, se ofrece guardarlo como persona de confianza. */
	private val ofrecerPareja = Runnable {
		val nombre = nombreAyudante
		if (!activa || persona != null || VozEnLlamadas.enLlamada(servicio)) return@Runnable
		Thread({
			val si = preguntar(t.getString(R.string.ayuda_confianza_pregunta, nombre), t.getString(R.string.ayuda_confianza_si), t.getString(R.string.ayuda_confianza_no))
			if (si && activa) {
				val p = Emparejar.nueva(nombre, System.currentTimeMillis() / 1000)
				Confianza.anadirPersona(servicio, p)
				Confianza.cancelarNuevo(servicio)
				persona = p
				mandar(MensajeAyuda.Pareja(p.id, Emparejar.secretoTexto(p), nombrePropio()))
				lector.manejador.post { lector.decir(t.getString(R.string.ayuda_confianza_hecho, nombre), Modo.ENCOLAR, Tipo.SISTEMA) }
			}
		}, "tiresias-ayuda-confianza").start()
	}

	// --- Enlazados --------------------------------------------------------------------------------

	private fun alConectar(nombre: String) {
		conectada = true
		nombreAyudante = nombre.take(40).ifBlank { t.getString(R.string.ayuda_alguien) }
		lector.manejador.removeCallbacks(caducar)
		// Lo que dice y suena Tiresias va también al ayudante (las contraseñas, no)
		lector.voz.escuchaRemota = { e, sensible ->
			val protegido = t.getString(R.string.ayuda_dato_protegido)
			val texto = if (sensible) protegido else Protegido.taparCodigos(e.texto, protegido)
			if (!protegida) mandar(MensajeAyuda.Voz(texto, e.modo == Modo.INTERRUMPIR, e.deletrear, e.tipo.name, e.idioma?.toLanguageTag()))
		}
		lector.voz.alCallarRemoto = { if (!protegida) mandar(MensajeAyuda.Callar) }
		lector.sonidos.escuchaSonido = { s -> if (!protegida) mandar(MensajeAyuda.Sonido(s.name)) }
		empezadaEn = SystemClock.elapsedRealtime()
		Guarda.enAyuda(servicio, true)
		alCambiarApp()
		mandar(MensajeAyuda.Ficha(ficha()))
		// Para poder deshacer al final lo que se cambie en Tiresias durante la ayuda
		Thread({ copiaAntes = runCatching { CopiaSeguridad.exportar(servicio) }.onFailure { Registro.w(ETIQUETA, "Sin copia de los ajustes", it) }.getOrNull() }, "tiresias-ayuda-copia").start()
		mantenerEncendida(true)
		lector.decir(t.getString(R.string.ayuda_empieza, nombreAyudante), Modo.INTERRUMPIR, Tipo.SISTEMA)
		Asistencia.notificarAyudado(servicio, t.getString(R.string.ayuda_notificacion_texto, nombreAyudante))
		if (lector.bloqueado()) mandar(MensajeAyuda.Estado(ESTADO_BLOQUEADO, t.getString(R.string.ayuda_bloqueado)))
		val aviso = ajustes.ayudaAviso
		if (aviso > 0) lector.manejador.postDelayed(recordar, aviso * 60_000L)
		val minutos = ajustes.ayudaMinutos
		lector.manejador.postDelayed(casiFin, (minutos - 2).coerceAtLeast(1) * 60_000L)
		lector.manejador.postDelayed(finDelTiempo, minutos * 60_000L)
		lector.manejador.postDelayed(vigilar, VIGILAR_MS)
		if (persona == null) lector.manejador.postDelayed(ofrecerPareja, OFRECER_PAREJA_MS)
		Registro.i(ETIQUETA, "Ayuda empezada")
		Asistencia.avisarCambio()
	}

	private fun alVolver() {
		lector.decir(t.getString(R.string.ayuda_vuelve, nombreAyudante), Modo.ENCOLAR, Tipo.SISTEMA)
		Asistencia.avisarCambio()
	}

	private val recordar: Runnable = object : Runnable {
		override fun run() {
			if (!activa) return
			lector.vibracion.vibrar(Sonido.NOTIFICACION)
			lector.decir(t.getString(R.string.ayuda_recordatorio, nombreAyudante), Modo.ENCOLAR, Tipo.SISTEMA)
			lector.manejador.postDelayed(this, ajustes.ayudaAviso.coerceAtLeast(1) * 60_000L)
		}
	}

	private val casiFin = Runnable { if (activa) lector.decir(R.string.ayuda_casi_fin, modo = Modo.ENCOLAR) }

	/** La petición ya ha salido: que la persona no se quede esperando en silencio si nadie entra. */
	fun seguirPeticion() {
		pasosSeguimiento = 0
		lector.manejador.postDelayed(seguimiento, SEGUIR_MS)
	}

	private val seguimiento: Runnable = object : Runnable {
		override fun run() {
			if (!activa || conectada) return
			val quien = ajustes.seniorNombreAyuda.ifBlank { t.getString(R.string.ayuda_tu_contacto) }
			pasosSeguimiento++
			when (pasosSeguimiento) {
				1 -> lector.decir(t.getString(R.string.ayuda_aun_no, quien), Modo.ENCOLAR, Tipo.SISTEMA)
				2 -> pedirASegundo()
				else -> return preguntarSiLlamo(quien)
			}
			lector.manejador.postDelayed(this, SEGUIR_MS)
		}
	}

	private fun pedirASegundo() {
		val numero = ajustes.ayudaContacto2
		if (numero.isBlank()) return
		val nombre = ajustes.ayudaNombre2.ifBlank { numero }
		lector.decir(t.getString(R.string.ayuda_pido_a_otro, nombre), Modo.ENCOLAR, Tipo.SISTEMA)
		PeticionAyuda.enviarA(servicio, lector, invitacion, if (ajustes.ayudaMetodo == PeticionAyuda.COMPARTIR) PeticionAyuda.SMS else ajustes.ayudaMetodo, numero, nombre)
	}

	/** Nadie ha entrado: se ofrece llamar por teléfono, seguir esperando o dejarlo. */
	private fun preguntarSiLlamo(quien: String) {
		val ventanas = servicio.ventanasActuales ?: return
		val titulo = t.getString(R.string.ayuda_nadie_entra, quien)
		val numero = ajustes.seniorContactoAyuda
		val opciones = listOfNotNull(if (numero.isNotBlank()) t.getString(R.string.ayuda_llamar_a, quien) else null, t.getString(R.string.ayuda_seguir), t.getString(R.string.ayuda_dejarlo))
		lector.decir(titulo, Modo.ENCOLAR, Tipo.SISTEMA)
		ventanas.lista(titulo, opciones) { i ->
			when (opciones[i]) {
				t.getString(R.string.ayuda_seguir) -> Unit
				t.getString(R.string.ayuda_dejarlo) -> terminar(FIN_USUARIO)
				else -> servicio.llamarAyuda(numero)
			}
		}
	}

	/** Ha cambiado la app de delante: en una de banca o pagos, la ayuda se queda en pausa. Se mira la ventana activa, no el teclado. */
	fun alCambiarApp() {
		if (!conectada) return
		val paquete = runCatching { servicio.rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
		val nombre = runCatching { servicio.packageManager.run { getApplicationLabel(getApplicationInfo(paquete, 0)).toString() } }.getOrNull()
		alPaquete(paquete, nombre)
	}

	/** Pone o quita la pausa según la app de delante. */
	internal fun alPaquete(paquete: String, nombre: String?) {
		val es = Protegido.esAppProtegida(paquete, nombre)
		if (es == protegida) return
		// El aviso sale antes de cerrar el grifo, y al volver, después de abrirlo
		if (es) {
			mandar(MensajeAyuda.Estado(ESTADO_PROTEGIDA, t.getString(R.string.ayuda_app_protegida_ayudante)))
			protegida = true
			lector.decir(t.getString(R.string.ayuda_app_protegida), Modo.ENCOLAR, Tipo.SISTEMA)
		} else {
			protegida = false
			mandar(MensajeAyuda.Estado(ESTADO_PROTEGIDA, t.getString(R.string.ayuda_app_protegida_fin)))
		}
		Registro.i(ETIQUETA, "Ayuda en pausa por app de banca: $es")
	}

	/** Solo estado técnico del móvil, sin nada de la pantalla. */
	private fun ficha(): List<String> {
		val l = ArrayList<String>()
		runCatching {
			l += t.getString(R.string.ayuda_ficha_movil, android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.VERSION.RELEASE, BuildConfig.VERSION_NAME)
			val bateria = servicio.getSystemService(BatteryManager::class.java)
			val nivel = bateria?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
			if (nivel in 0..100) l += t.getString(if (bateria?.isCharging == true) R.string.estado_bateria_cargando else R.string.estado_bateria, nivel)
			val am = servicio.getSystemService(AudioManager::class.java)
			if (am != null) {
				val maximo = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
				l += t.getString(R.string.ayuda_ficha_volumen, am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maximo, am.getStreamVolume(AudioManager.STREAM_RING))
			}
			l += t.getString(if (Red.wifi(servicio) != null) R.string.ayuda_ficha_wifi else R.string.ayuda_ficha_datos)
			if (Settings.Global.getInt(servicio.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1) l += t.getString(R.string.ayuda_ficha_avion)
			val libre = Environment.getDataDirectory().usableSpace / (1024L * 1024L * 1024L)
			l += t.getString(R.string.ayuda_ficha_espacio, libre)
			val nm = servicio.getSystemService(android.app.NotificationManager::class.java)
			if (nm != null && nm.currentInterruptionFilter > android.app.NotificationManager.INTERRUPTION_FILTER_ALL) l += t.getString(R.string.ayuda_ficha_no_molestar)
		}.onFailure { Registro.w(ETIQUETA, "Ficha del móvil incompleta", it) }
		return l
	}
	private val finDelTiempo = Runnable { terminar(FIN_TIEMPO) }

	/** Cada 20 s: un ping al ayudante; sin noticias suyas en 15 minutos, o 2 minutos cortada, se termina. */
	private val vigilar: Runnable = object : Runnable {
		override fun run() {
			if (!activa) return
			val ahora = SystemClock.uptimeMillis()
			when {
				cortadaDesde > 0 && ahora - cortadaDesde > VOLVER_MS -> return terminar(FIN_CORTADA)
				ahora - ultimoMensaje > SIN_USO_MS -> return terminar(FIN_SIN_USO)
			}
			mandar(MensajeAyuda.Ping)
			lector.manejador.postDelayed(this, VIGILAR_MS)
		}
	}

	/** Manda [m] al ayudante sin esperar (si está enlazado). */
	fun mandar(m: MensajeAyuda) {
		val e = enlace ?: return
		val texto = m.json()
		runCatching {
			envios.execute {
				try {
					e.enviarTexto(texto)
				} catch (x: IOException) {
					Registro.w(ETIQUETA, "No se pudo mandar al ayudante", x)
				}
			}
		}.onFailure { Registro.w(ETIQUETA, "La cola de envíos está cerrada", it) }
	}

	fun alPantalla(encendida: Boolean) {
		if (!conectada) return
		mandar(MensajeAyuda.Estado(if (encendida) ESTADO_ENCENDIDA else ESTADO_APAGADA, t.getString(if (encendida) R.string.ayuda_pantalla_encendida else R.string.ayuda_pantalla_apagada)))
	}

	/**
	 * Mientras ayudan, la pantalla no se apaga sola: las acciones de Tiresias no
	 * cuentan como si la persona tocara, y si se apagara se bloquearía. Una
	 * ventana de un píxel que no se toca la mantiene encendida.
	 */
	private fun mantenerEncendida(poner: Boolean) = principal.post {
		val wm = servicio.getSystemService(WindowManager::class.java) ?: return@post
		if (poner && vistaEncendida == null) {
			val v = View(servicio)
			val params = WindowManager.LayoutParams(
				1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				PixelFormat.TRANSPARENT,
			)
			v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
			runCatching { wm.addView(v, params); vistaEncendida = v }.onFailure { Registro.w(ETIQUETA, "No se pudo mantener la pantalla encendida", it) }
		} else if (!poner) {
			vistaEncendida?.let { v -> runCatching { wm.removeView(v) }.onFailure { Registro.w(ETIQUETA, "No se pudo quitar la ventana de la pantalla encendida", it) } }
			vistaEncendida = null
		}
	}

	// --- Lo que pide el ayudante (en el hilo del lector) ------------------------------------------

	private fun atenderMensaje(m: MensajeAyuda) {
		when (m) {
			is MensajeAyuda.Ping, is MensajeAyuda.Hola -> Unit
			is MensajeAyuda.Fin -> terminar(FIN_AYUDANTE)
			is MensajeAyuda.Decir -> if (!preguntando) {
				lector.sonidos.tocar(Sonido.NOTIFICACION)
				lector.decir(t.getString(R.string.ayuda_te_dice, nombreAyudante, m.texto.take(MAXIMO_DECIR)), Modo.ENCOLAR, Tipo.SISTEMA)
			}
			is MensajeAyuda.Llamame -> {
				val numero = ajustes.seniorContactoAyuda
				if (numero.isBlank()) responder(0, false, t.getString(R.string.ayuda_sin_contacto))
				else servicio.llamarAyuda(numero)
			}
			else -> {
				if (preguntando) return
				// En una app de banca solo se puede salir de ella
				if (protegida && !(m is MensajeAyuda.HacerAccion && m.nombre == Accion.INICIO.name)) {
					responder(0, false, t.getString(R.string.ayuda_app_protegida_ayudante))
					return
				}
				// Con el móvil bloqueado no se hace nada: el PIN lo pone la persona
				if (lector.bloqueado()) {
					mandar(MensajeAyuda.Estado(ESTADO_BLOQUEADO, t.getString(R.string.ayuda_bloqueado)))
					return
				}
				when (m) {
					is MensajeAyuda.HacerAccion -> hacerAccion(m.nombre)
					is MensajeAyuda.Escribir -> escribir(m.texto)
					is MensajeAyuda.PedirElementos -> elementos(m.peticion)
					is MensajeAyuda.UsarElemento -> usarElemento(m)
					is MensajeAyuda.PedirApps -> apps(m.peticion)
					is MensajeAyuda.AbrirApp -> abrirApp(m)
					is MensajeAyuda.AbrirEnlace -> abrirEnlace(m)
					else -> Unit
				}
			}
		}
	}

	private fun responder(peticion: Long, ok: Boolean, texto: String? = null) = mandar(MensajeAyuda.Respuesta(peticion, ok, texto))

	private fun hacerAccion(nombre: String) {
		val a = Accion.entries.firstOrNull { it.name == nombre } ?: return
		if (a in PROHIBIDAS) {
			responder(0, false, t.getString(R.string.ayuda_no_permitida))
			return
		}
		servicio.hacerYa(a)
	}

	/** Escribe [texto] en el campo enfocado, en el cursor. */
	private fun escribir(texto: String) {
		val raiz = lector.raiz()
		val campo = runCatching { raiz?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()?.takeIf { it.isEditable }
			?: lector.foco?.takeIf { it.isEditable }
		if (campo == null) {
			responder(0, false, t.getString(R.string.ayuda_sin_campo))
			return
		}
		runCatching { campo.refresh() }
		val actual = campo.text?.toString().orEmpty().takeIf { campo.textSelectionStart >= 0 && (android.os.Build.VERSION.SDK_INT < 26 || !campo.isShowingHintText) } ?: ""
		val cursor = campo.textSelectionEnd.coerceIn(0, actual.length)
		val nuevo = actual.substring(0, cursor) + texto + actual.substring(cursor)
		val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nuevo) }
		val hecho = campo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
		if (hecho) {
			val fin = cursor + texto.length
			campo.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, fin)
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, fin)
			})
		}
		responder(0, hecho, t.getString(if (hecho) R.string.ayuda_escrito else R.string.ayuda_no_escrito))
	}

	/** Los elementos de la pantalla, en el orden de lectura, como los diría Tiresias (sin pistas). */
	private fun elementos(peticion: Long) {
		val raiz = lector.raiz()
		val nodos = if (raiz == null) emptyList() else lector.navegador.orden(raiz).take(MAXIMO_ELEMENTOS)
		ultimosNodos = nodos
		val v = ajustes.verbosidad.copy(decirPistas = false)
		val lista = nodos.mapIndexed { i, n ->
			val protegido = t.getString(R.string.ayuda_dato_protegido)
			ElementoRemoto(i, if (Nodos.sensible(n)) protegido else Protegido.taparCodigos(lector.compositor.describir(lector.elemento(n), v), protegido))
		}
		mandar(MensajeAyuda.Elementos(peticion, lista))
	}

	private fun usarElemento(m: MensajeAyuda.UsarElemento) {
		val n = ultimosNodos.getOrNull(m.indice)
		if (n == null || !runCatching { n.refresh() }.getOrDefault(false)) {
			responder(m.peticion, false, t.getString(R.string.ayuda_elemento_ya_no))
			return
		}
		when (m.como) {
			COMO_ENFOCAR -> lector.enfocar(n)
			COMO_MANTENER -> {
				lector.enfocar(n, null)
				servicio.hacerYa(Accion.MANTENER)
			}
			else -> {
				lector.enfocar(n, null)
				servicio.hacerYa(Accion.ACTIVAR)
			}
		}
		responder(m.peticion, true)
	}

	private fun lanzables(): List<Pair<String, String>> {
		return com.xebolax.tiresias.base.Lanzables.todas(servicio).map { it.nombre to it.paquete }
	}

	private fun apps(peticion: Long) = mandar(MensajeAyuda.Apps(peticion, lanzables().map { it.first }))

	private fun abrirApp(m: MensajeAyuda.AbrirApp) {
		val buscado = normalizar(m.nombre)
		val elegida = lanzables().minByOrNull { (nombre, _) ->
			val n = normalizar(nombre)
			when {
				n == buscado -> 0
				n.startsWith(buscado) -> 1
				n.contains(buscado) -> 2
				else -> 99
			}
		}?.takeIf { normalizar(it.first).contains(buscado) }
		if (elegida == null) {
			responder(m.peticion, false, t.getString(R.string.escucha_app_no, m.nombre))
			return
		}
		if (Protegido.esAppProtegida(elegida.second, elegida.first)) {
			responder(m.peticion, false, t.getString(R.string.ayuda_app_protegida_ayudante))
			return
		}
		val intent = if (m.informacion) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", elegida.second, null))
		else servicio.packageManager.getLaunchIntentForPackage(elegida.second)
		val ok = intent != null && abrirIntent(intent)
		responder(m.peticion, ok, if (ok) elegida.first else t.getString(R.string.ayuda_no_abierto))
	}

	/** Un enlace, la tienda o una pantalla de Ajustes: «tiresias:ajustes-sistema» y «tiresias:ajustes-tiresias» son las de siempre. */
	private fun abrirEnlace(m: MensajeAyuda.AbrirEnlace) {
		val intent = when (m.enlace) {
			ENLACE_AJUSTES_SISTEMA -> Intent(Settings.ACTION_SETTINGS)
			ENLACE_AJUSTES_TIRESIAS -> Intent(servicio, com.xebolax.tiresias.ui.ActividadPrincipal::class.java)
			ENLACE_ACCESIBILIDAD -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
			ENLACE_WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
			ENLACE_ESCRITORIO_SISTEMA -> {
				val ok = com.xebolax.tiresias.servicio.PuenteEscritorio.salirAlSistema(servicio)
				responder(m.peticion, ok, if (ok) null else t.getString(R.string.ayuda_no_abierto))
				return
			}
			else -> if (Protegido.enlaceSeguro(m.enlace)) runCatching { Intent(Intent.ACTION_VIEW, Uri.parse(m.enlace.trim())) }.getOrNull() else null
		}
		val ok = intent != null && abrirIntent(intent)
		responder(m.peticion, ok, if (ok) null else t.getString(R.string.ayuda_no_abierto))
	}

	private fun abrirIntent(i: Intent): Boolean = try {
		servicio.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		true
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo abrir lo que pidió el ayudante", e)
		false
	}

	private fun normalizar(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

	// --- Terminar ---------------------------------------------------------------------------------

	fun terminar(motivo: String) {
		if (!activa) return
		activa = false
		val e = enlace
		Thread({
			runCatching { e?.enviarTexto(MensajeAyuda.Fin(motivo).json()) }
			cerrarTodo()
		}, "tiresias-ayuda-fin").start()
		if (lector.voz.escuchaRemota != null) lector.voz.escuchaRemota = null
		lector.voz.alCallarRemoto = null
		lector.sonidos.escuchaSonido = null
		mantenerEncendida(false)
		for (r in listOf(caducar, recordar, casiFin, finDelTiempo, vigilar, seguimiento, ofrecerPareja)) lector.manejador.removeCallbacks(r)
		Asistencia.notificarAyudado(servicio, null)
		Guarda.enAyuda(servicio, false)
		if (conectada) alAcabar()
		val aviso = when {
			!conectada -> t.getString(R.string.ayuda_ya_no_se_espera)
			motivo == FIN_TIEMPO -> t.getString(R.string.ayuda_fin_tiempo, nombreAyudante)
			motivo == FIN_AYUDANTE -> t.getString(R.string.ayuda_fin_ayudante, nombreAyudante)
			else -> t.getString(R.string.ayuda_fin, nombreAyudante)
		}
		lector.decir(aviso, Modo.ENCOLAR, Tipo.SISTEMA)
		Registro.i(ETIQUETA, "Ayuda terminada: $motivo")
		Asistencia.alTerminarAyudado(this)
	}

	/** Apunta la ayuda y, si durante ella cambiaron los ajustes de Tiresias, pregunta si se dejan así. */
	private fun alAcabar() {
		val minutos = ((SystemClock.elapsedRealtime() - empezadaEn) / 60_000L).toInt().coerceAtLeast(1)
		val fecha = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date())
		val quien = nombreAyudante
		val antes = copiaAntes
		Thread({
			Confianza.apuntar(servicio, t.getString(R.string.ayuda_historial_linea, fecha, quien, minutos))
			val ahora = antes?.let { runCatching { CopiaSeguridad.exportar(servicio) }.onFailure { e -> Registro.w(ETIQUETA, "Sin copia de los ajustes", e) }.getOrNull() }
			val cambios = if (antes != null && ahora != null) CopiaSeguridad.cambios(antes, ahora) else 0
			if (antes != null && cambios > 0) lector.manejador.post { preguntarDeshacer(antes, cambios) }
		}, "tiresias-ayuda-acabar").start()
	}

	private fun preguntarDeshacer(antes: String, cambios: Int) {
		val ventanas = servicio.ventanasActuales ?: return
		val titulo = lector.textos.resources.getQuantityString(R.plurals.ayuda_deshacer, cambios, cambios)
		lector.decir(titulo, Modo.ENCOLAR, Tipo.SISTEMA)
		// Cerrar sin elegir deja lo hecho: lo que importa es no deshacer un arreglo sin querer
		ventanas.lista(titulo, listOf(t.getString(R.string.ayuda_deshacer_no), t.getString(R.string.ayuda_deshacer_si))) { i ->
			if (i == 1) Thread({
				val ok = runCatching { CopiaSeguridad.importar(servicio, antes) }.onFailure { Registro.w(ETIQUETA, "No se pudo deshacer", it) }.getOrDefault(false)
				lector.manejador.post { lector.decir(if (ok) R.string.ayuda_deshecho else R.string.ayuda_no_deshecho) }
			}, "tiresias-ayuda-deshacer").start()
		}
	}

	private fun cerrarTodo() {
		socket?.let { cerrar(it) }
		socket = null
		enlace = null
		runCatching { servidor?.close() }.onFailure { Registro.w(ETIQUETA, "No se pudo cerrar el puerto", it) }
		servidor = null
		mapeo?.let { m -> runCatching { m.cerrar() }.onFailure { Registro.w(ETIQUETA, "No se pudo cerrar el puerto del router", it) } }
		mapeo = null
		runCatching { quitarAnuncio() }
		if (conTor) Tor.soltar(servicio)
		conTor = false
		envios.shutdown()
	}

	private fun cerrar(s: Socket) {
		// Primero se cierra la salida (así el otro lee el final), luego todo
		runCatching { if (!s.isClosed) s.shutdownOutput() }
		runCatching { s.close() }.onFailure { Registro.w(ETIQUETA, "No se pudo cerrar la conexión", it) }
	}

	companion object {
		private const val ETIQUETA = "SesionAyudado"
		const val MINUTOS_INVITACION = 10
		private const val PUERTO_MINIMO = 41000
		private const val PUERTO_MAXIMO = 59000
		private const val PUERTO_ONION = 41007
		private const val ESPERA_SALUDO_MS = 15_000
		private const val ESPERA_LECTURA_MS = 90_000
		private const val MAXIMO_FALLOS = 5
		private const val VIGILAR_MS = 20_000L
		private const val VOLVER_MS = 2 * 60_000L
		private const val SIN_USO_MS = 15 * 60_000L
		private const val MAXIMO_ELEMENTOS = 300
		private const val MAXIMO_DECIR = 500
		private const val ESPERA_ENTRAR_MS = 8000L
		private const val OFRECER_PAREJA_MS = 10_000L
		private const val SEGUIR_MS = 100_000L

		const val FIN_USUARIO = "usuario"
		const val FIN_CADUCADA = "caducada"
		const val FIN_TIEMPO = "tiempo"
		const val FIN_SIN_USO = "sin_uso"
		const val FIN_CORTADA = "cortada"
		const val FIN_FALLOS = "fallos"
		const val FIN_AYUDANTE = "ayudante"
		const val FIN_RECHAZADA = "rechazada"

		const val ESTADO_BLOQUEADO = "bloqueado"
		const val ESTADO_ENCENDIDA = "pantalla_encendida"
		const val ESTADO_APAGADA = "pantalla_apagada"
		const val ESTADO_PROTEGIDA = "protegida"

		const val COMO_ACTIVAR = "activar"
		const val COMO_MANTENER = "mantener"
		const val COMO_ENFOCAR = "enfocar"

		const val ENLACE_AJUSTES_SISTEMA = "tiresias:ajustes-sistema"
		const val ENLACE_AJUSTES_TIRESIAS = "tiresias:ajustes-tiresias"
		const val ENLACE_ACCESIBILIDAD = "tiresias:accesibilidad"
		const val ENLACE_WIFI = "tiresias:wifi"
		const val ENLACE_ESCRITORIO_SISTEMA = "tiresias:escritorio-sistema"

		/**
		 * Lo que el ayudante no puede hacer aunque sea de confianza: lo que dejaría a
		 * la persona sin lector o sin la ayuda (suspender, el modo seguro, apagar,
		 * bloquear), lo que llama o escucha por su micrófono, y lo de la propia ayuda.
		 */
		val PROHIBIDAS = setOf(
			Accion.SUSPENDER, Accion.MODO_SEGURO, Accion.ENCENDIDO, Accion.BLOQUEAR, Accion.SOS,
			Accion.DICTADO, Accion.ORDENES_VOZ, Accion.PEDIR_AYUDA, Accion.AYUDA_REMOTA, Accion.AYUDA_TERMINAR,
			Accion.AYUDA_CONTROLAR, Accion.CONTROL_PC, Accion.ALTERNAR_VOZ, Accion.VOZ_A_DEMANDA,
		)
	}
}
