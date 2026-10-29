package com.xebolax.tiresias.lectura

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Describir
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Compositor
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.nucleo.Elemento
import com.xebolax.tiresias.nucleo.FiltroRepetidos
import com.xebolax.tiresias.nucleo.Verbosidad
import com.xebolax.tiresias.perfiles.Etiquetas
import com.xebolax.tiresias.textos.PlantillasAndroid
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Sonidos
import com.xebolax.tiresias.voz.Tipo
import com.xebolax.tiresias.voz.Vibracion
import com.xebolax.tiresias.voz.Voz

/**
 * El lector: recibe los eventos de accesibilidad y los gestos, decide qué decir
 * y mueve el foco.
 *
 * **Hilos.** Los eventos llegan al hilo principal; ahí solo se copian y se
 * mandan al hilo del lector (prioridad alta), donde se hace todo lo demás: las
 * consultas a los nodos son llamadas a otra app y no pueden bloquear el hilo
 * principal (la causa de la mayor parte de la latencia de TalkBack). Todo el
 * estado del lector se toca solo desde su hilo.
 *
 * **Velocidad.** Al mover el foco Tiresias habla enseguida, sin esperar al
 * evento de la app; el evento que llega después se ignora. La frase del
 * elemento siguiente y del anterior se prepara de antemano y el orden de
 * lectura de la ventana se guarda hasta que algo cambia.
 */
class Lector(val servicio: AccessibilityService, val voz: Voz, val sonidos: Sonidos, val vibracion: Vibracion) {
	private val hilo = HandlerThread("tiresias-lector", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
	val manejador = Handler(hilo.looper)
	val ajustes = Ajustes(servicio)
	val etiquetas = Etiquetas(servicio)
	// La etiqueta del usuario; si no hay, la que ponga una regla de un complemento
	val describir = Describir { paquete, id -> etiquetas.de(paquete, id) ?: if (scriptsListos) scripts.etiquetaRegla(paquete, id) else null }
	var compositor = nuevoCompositor()
		private set
	private var verbosidad: Verbosidad = ajustes.verbosidad
	val textos: Context get() = Idioma.envolver(servicio)

	val navegador = Navegador(this)
	val edicion = EdicionTexto(this)
	val continua = LecturaContinua(this)
	val memoria = MemoriaVentanas()
	val gestos = com.xebolax.tiresias.gestos.GestosTiresias(this)
	val perfiles = com.xebolax.tiresias.perfiles.Perfiles(servicio, this)

	// Módulos que necesitan el servicio entero (capturas, ventanas, teclado braille): los pone el servicio al conectarse
	lateinit var vision: com.xebolax.tiresias.vision.Vision
	lateinit var escucha: com.xebolax.tiresias.escucha.Escucha
	lateinit var braille: com.xebolax.tiresias.braille.Braille
	lateinit var scripts: com.xebolax.tiresias.scripts.Scripts
	val scriptsListos: Boolean get() = this::scripts.isInitialized
	val brailleListo: Boolean get() = this::braille.isInitialized
	val visionListo: Boolean get() = this::vision.isInitialized

	private val filtro = FiltroRepetidos(ventanaMs = 600, maximoPorSegundo = 10)
	private val filtroVivas = FiltroRepetidos(ventanaMs = 1500, maximoPorSegundo = 4)

	/** El nodo con el foco de accesibilidad (el que se lee y se activa). */
	@Volatile var foco: AccessibilityNodeInfo? = null
		private set

	/**
	 * La pantalla virtual: el texto reconocido en la pantalla, cada línea con su
	 * sitio, para recorrerla deslizando o con el dedo y pulsar en su sitio (lo
	 * que valoran de Prudence y Jieshuo en las apps que no se pueden leer).
	 */
	class PantallaVirtual(val elementos: List<Pair<String, android.graphics.Rect>>, val ventana: Int) {
		@Volatile var actual = -1
	}

	@Volatile var virtual: PantallaVirtual? = null
		private set

	/** Pone la pantalla virtual con [lineas] (texto y sitio), en el orden de lectura: de arriba abajo y de izquierda a derecha. */
	fun entrarVirtual(lineas: List<Pair<String, android.graphics.Rect>>) {
		val ordenadas = lineas.filter { it.first.isNotBlank() }.sortedWith(compareBy({ it.second.top / 24 }, { it.second.left }))
		val ventana = runCatching { servicio.windows.firstOrNull { it.isActive }?.id }.getOrNull() ?: -1
		virtual = PantallaVirtual(ordenadas, ventana)
		sonidos.tocar(Sonido.VENTANA)
		decir(textos.resources.getQuantityString(R.plurals.virtual_entrar, ordenadas.size, ordenadas.size))
	}

	/** Quita la pantalla virtual (diciéndolo, si [avisar]). */
	fun salirVirtual(avisar: Boolean) {
		if (virtual == null) return
		virtual = null
		if (avisar) decir(R.string.virtual_salir)
	}

	/** Pasa en la pantalla virtual: [paso] 1 o -1, 0 repite, MIN_VALUE y MAX_VALUE al principio y al final. */
	fun moverVirtual(paso: Int): Boolean {
		val v = virtual ?: return false
		if (v.elementos.isEmpty()) {
			sonidos.tocar(Sonido.BORDE)
			return false
		}
		val destino = when (paso) {
			Int.MIN_VALUE -> 0
			Int.MAX_VALUE -> v.elementos.lastIndex
			else -> if (v.actual < 0) (if (paso >= 0) 0 else v.elementos.lastIndex) else v.actual + paso
		}
		if (destino !in v.elementos.indices) {
			sonidos.tocar(Sonido.BORDE)
			return false
		}
		decirVirtual(v, destino, Sonido.FOCO)
		return true
	}

	private fun decirVirtual(v: PantallaVirtual, i: Int, sonido: Sonido) {
		v.actual = i
		val (texto, _) = v.elementos[i]
		sonidos.tocar(sonido)
		voz.decir(Enunciado(textos.getString(R.string.virtual_posicion, texto, i + 1, v.elementos.size), Modo.INTERRUMPIR, Tipo.NAVEGACION))
		if (brailleListo) braille.mensaje(texto)
	}

	/** El dedo está en ([x], [y]) con la pantalla virtual puesta: se dice lo que hay debajo (o lo más cercano, a 48 dp). */
	fun virtualEnPunto(x: Float, y: Float) {
		val v = virtual ?: return
		val margen = 48 * servicio.resources.displayMetrics.density
		val i = v.elementos.indices.firstOrNull { v.elementos[it].second.contains(x.toInt(), y.toInt()) }
			?: v.elementos.indices.minByOrNull { k ->
				val r = v.elementos[k].second
				val dx = maxOf(r.left - x, 0f, x - r.right)
				val dy = maxOf(r.top - y, 0f, y - r.bottom)
				dx * dx + dy * dy
			}?.takeIf { k ->
				val r = v.elementos[k].second
				maxOf(r.left - x, 0f, x - r.right) < margen && maxOf(r.top - y, 0f, y - r.bottom) < margen
			}
			?: return
		if (i != v.actual) decirVirtual(v, i, Sonido.TOQUE)
	}

	/** Cuántas veces ha cambiado el foco: lo que se elige para un elemento (una acción) vale hasta que cambie. */
	@Volatile var cambiosFoco = 0
		private set

	/** El nodo que Tiresias acaba de enfocar y decir: su evento de foco, cuando llegue, se ignora. */
	private var dichoAlEnfocar: AccessibilityNodeInfo? = null
	private var momentoDicho = 0L
	/** Los últimos elementos enfocados por Tiresias, con su momento: el aviso de Android llega después. */
	private val enfocadosPropios = ArrayDeque<Pair<AccessibilityNodeInfo, Long>>()
	private var momentoVentana = 0L
	private var tituloVentana = ""
	/** La ventana de la que es [tituloVentana]. */
	private var idVentanaTitulo = -1
	/** Si hay un teclado en pantalla y desde cuándo no lo hay (un teclado que se rehace al momento no se anuncia otra vez). */
	private var hayTeclado = false
	private var sinTecladoDesde = 0L
	private var ultimoSonidoDesplazar = 0L

	/** Elementos ya descritos, para hablar sin volver a preguntar a la app. Se vacía con cada cambio. */
	private val descritos = HashMap<AccessibilityNodeInfo, Elemento>()

	/** El control de lectura del selector (deslizar arriba y abajo). */
	@Volatile var control: ControlLectura = ControlLectura.PREDETERMINADO

	/** Ayuda de entrada: los gestos y las teclas se dicen en vez de hacerse. */
	@Volatile var ayudaEntrada = false

	fun recargarAjustes() = manejador.post {
		// Otro volumen para la voz (Ajustes, Voz, o la acción): se aplica al momento
		if (com.xebolax.tiresias.voz.PistaAudio.multimedia != ajustes.vozMultimedia) cambiarCanal(ajustes.vozMultimedia)
		// Las etiquetas y los perfiles pueden haber cambiado en sus pantallas (son otra copia en memoria)
		etiquetas.olvidar()
		perfiles.olvidar()
		verbosidad = perfiles.verbosidad(ajustes.verbosidad)
		compositor = nuevoCompositor()
		voz.invalidarTextos()
		voz.elegirMotor()
		voz.velocidadPerfil = perfiles.velocidad(ajustes.velocidad).takeIf { it != ajustes.velocidad }
		voz.puntuacionPerfil = perfiles.puntuacion()
		descritos.clear()
		if (brailleListo) braille.recargarAjustes()
	}

	/** La voz y los sonidos por el volumen multimedia ([multimedia]) o por el de accesibilidad. */
	fun cambiarCanal(multimedia: Boolean) {
		com.xebolax.tiresias.voz.PistaAudio.multimedia = multimedia
		voz.aplicarCanal()
		Thread({ sonidos.cambiarCanal() }, "tiresias-sonidos").start()
	}

	fun cerrar() {
		continua.parar()
		edicion.cerrar()
		hilo.quitSafely()
	}

	// --- Eventos ------------------------------------------------------------------------

	/** Desde el hilo principal: copia el evento (Android lo recicla al volver) y lo pasa al hilo del lector. */
	/** Cuántas veces se ha empezado a tocar la pantalla: lo que va por pasos se para si cambia. */
	@Volatile var toques = 0
		private set

	/** Hasta cuándo se calla lo que SystemUI dice solo al encender con el móvil bloqueado, y desde cuándo. */
	@Volatile private var silencioSistemaHasta = 0L
	@Volatile private var silencioSistemaDesde = 0L

	/** Al encender bloqueado: unos segundos sin el foco, las ventanas ni los anuncios que SystemUI pone solo. */
	fun callarSistemaUnMomento() {
		val ahora = SystemClock.uptimeMillis()
		silencioSistemaDesde = ahora
		silencioSistemaHasta = ahora + SILENCIO_ENCENDER_MS
	}

	/**
	 * Si lo que llega de [paquete] se calla ahora: es de SystemUI, sigue bloqueado, no ha pasado el plazo y el
	 * usuario no ha tocado nada desde que se encendió la pantalla.
	 */
	private fun calladoAlEncender(paquete: CharSequence?): Boolean {
		val ahora = SystemClock.uptimeMillis()
		if (ahora > silencioSistemaHasta) return false
		if (momentoUsuario > silencioSistemaDesde || !bloqueado()) {
			silencioSistemaHasta = 0L
			return false
		}
		return paquete?.toString() in paquetesDelSistema
	}

	/** SystemUI y la pantalla de bloqueo, como los nombra Android (sin nombres de fabricante). */
	private val paquetesDelSistema: Set<String> by lazy {
		val r = servicio.resources
		val desdeConfig = listOf("config_systemUi", "config_systemUIServiceComponent", "config_keyguardComponent").mapNotNull { nombre ->
			runCatching {
				val id = r.getIdentifier(nombre, "string", "android")
				if (id != 0) r.getString(id).substringBefore('/').takeIf { it.isNotBlank() } else null
			}.getOrNull()
		}
		(desdeConfig + PAQUETE_SISTEMA).toSet()
	}

	/** Desde cuándo hay un dedo del usuario en la pantalla (0: ninguno). */
	@Volatile private var dedoDesde = 0L

	/**
	 * Si el usuario tiene ahora un dedo en la pantalla (las teclas de volumen cambian entonces el de la voz,
	 * como en TalkBack). No más de medio minuto: si Android no avisa de que se levantó (el dedo «pegado» de
	 * algunos móviles), las teclas no se quedan cambiando la voz para siempre.
	 */
	val dedoEnPantalla: Boolean get() = dedoDesde > 0 && SystemClock.uptimeMillis() - dedoDesde < DEDO_MAXIMO_MS

	fun alEvento(e: AccessibilityEvent) {
		// Los toques de un gesto que ha mandado Tiresias (la lista que se adelanta sola) no son del usuario:
		// ni cortan lo que se está diciendo ni paran lo que va por pasos
		val toqueUsuario = e.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START && !esGestoPropio(e)
		if (toqueUsuario) {
			toques++
			dedoDesde = SystemClock.uptimeMillis()
		} else if (e.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) dedoDesde = 0L
		val copia = copiar(e) ?: return
		// Tocar la pantalla corta la voz al momento, sin esperar al hilo del lector
		if (toqueUsuario && ajustes.cortarAlTocar) {
			continua.parar()
			voz.callar()
		}
		manejador.post {
			try {
				procesar(copia)
			} catch (ex: Exception) {
				Registro.e(ETIQUETA, "Fallo al procesar un evento (tipo ${copia.eventType})", ex)
			}
		}
	}

	private fun copiar(e: AccessibilityEvent): AccessibilityEvent? = try {
		if (Build.VERSION.SDK_INT >= 30) AccessibilityEvent(e) else @Suppress("DEPRECATION") AccessibilityEvent.obtain(e)
	} catch (ex: Exception) {
		Registro.w(ETIQUETA, "No se pudo copiar el evento", ex)
		null
	}

	private fun procesar(e: AccessibilityEvent) {
		when (e.eventType) {
			AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> alExplorar(e)
			AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
				toquePropio = esGestoPropio(e)
				if (toquePropio) return
				momentoUsuario = SystemClock.uptimeMillis()
				teclaExplorada = null
				ultimoExplorado = null
				cancelarToqueActiva()
				focoAlTocar = foco
				inicioToque = SystemClock.uptimeMillis()
				tocoOtro = false
				exploradoEnEsteToque = false
				gestoEnEsteToque = false
			}
			AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
				// Los toques de un gesto que ha mandado Tiresias (arrastrar una lista, pulsar) no son del dedo del
				// usuario: ni cuentan como un dedo atascado ni activan nada con «un toque activa». Se decide al
				// empezar el toque: el final puede llegar tarde (con el móvil ocupado) y ya no se distinguiría
				if (!toquePropio) {
					vigilarToqueAtascado()
					alLevantarDedo()
				}
			}
			AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> gestoEnEsteToque = true
			AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> alEnfocar(e)
			AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> if (e.source == foco) perderFoco()
			AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> alCambiarVentana(e)
			AccessibilityEvent.TYPE_WINDOWS_CHANGED -> alCambiarVentanas(e)
			AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> alCambiarContenido(e)
			AccessibilityEvent.TYPE_VIEW_SCROLLED -> alDesplazar(e)
			AccessibilityEvent.TYPE_VIEW_CLICKED -> alPulsar(e)
			AccessibilityEvent.TYPE_VIEW_SELECTED -> alSeleccionar(e)
			AccessibilityEvent.TYPE_VIEW_FOCUSED -> alEnfocarEntrada(e)
			AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
				edicion.alCambiarTexto(e)
				if (brailleListo) braille.alCambiarEdicion(e.source)
			}
			AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
				edicion.alCambiarSeleccion(e)
				if (brailleListo) braille.alCambiarEdicion(e.source)
			}
			AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> edicion.alRecorrer(e)
			AccessibilityEvent.TYPE_ANNOUNCEMENT -> alAnunciar(e)
			AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> alNotificar(e)
			AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> Unit
		}
	}

	private var exploracionPendiente: Runnable? = null

	/**
	 * El dedo pasa por un elemento: se le pone el foco y se dice al momento. Con
	 * una espera en Ajustes (para quien arrastra rápido y no quiere oír cada
	 * elemento por el que pasa), solo se dice si el dedo se queda en él.
	 */
	/** La tecla del teclado en pantalla sobre la que está el dedo (para escribir al levantarlo). */
	private var teclaExplorada: AccessibilityNodeInfo? = null

	/**
	 * Con «Escribir al levantar el dedo», la tecla del teclado en pantalla sobre
	 * la que se levanta el dedo se escribe, sin doble toque (como en TalkBack).
	 */
	private fun alLevantarDedo() {
		val tecla = teclaExplorada
		val explorado = ultimoExplorado
		val focoTocado = focoAlTocar
		focoAlTocar = null
		// Modo senior, «un toque activa»: un toque corto sobre lo que ya se había dicho (sin pasar por nada más)
		if (ajustes.modoSenior && ajustes.seniorToqueActiva && focoTocado != null && !tocoOtro &&
			SystemClock.uptimeMillis() - inicioToque < TOQUE_CORTO_MS && Nodos.vivo(focoTocado)) {
			// Se espera un poco: si era el primer toque de un doble toque, el doble toque manda (y lo cancela)
			val r = Runnable {
				toqueActivaPendiente = null
				sonidos.tocar(Sonido.ACTIVAR)
				pulsar(focoTocado)
			}
			toqueActivaPendiente = r
			manejador.postDelayed(r, ESPERA_DOBLE_TOQUE_MS)
		}
		teclaExplorada = null
		ultimoExplorado = null
		if (tecla != null && ajustes.escribirAlLevantar && Nodos.vivo(tecla)) {
			sonidos.tocar(Sonido.TECLA)
			pulsar(tecla)
			return
		}
		// Activar al levantar el dedo: en los botones de la barra de navegación o en toda la pantalla
		val modo = ajustes.activarAlLevantar
		if (explorado == null || !Nodos.vivo(explorado)) return
		// Un elemento con la regla «levantar» se activa así aunque el ajuste diga que no
		if (scriptsListos && scripts.regla(explorado)?.levantar == true) {
			sonidos.tocar(Sonido.ACTIVAR)
			pulsar(explorado)
			return
		}
		if (modo == Ajustes.LEVANTAR_NO) return
		val barra = esBotonDeLaBarra(explorado)
		if (modo == Ajustes.LEVANTAR_BARRA && !barra) return
		if (!barra && !Nodos.accionable(explorado)) return
		sonidos.tocar(Sonido.ACTIVAR)
		pulsar(explorado)
	}

	/** El foco al empezar a tocar, cuándo, y si el dedo ha pasado por otro elemento (para «un toque activa»). */
	private var focoAlTocar: AccessibilityNodeInfo? = null
	private var inicioToque = 0L
	private var tocoOtro = false
	private var toqueActivaPendiente: Runnable? = null

	/** Un gesto (el doble toque) o un toque nuevo: la activación de «un toque activa» que esperaba ya no vale. */
	fun cancelarToqueActiva() {
		toqueActivaPendiente?.let { manejador.removeCallbacks(it) }
		toqueActivaPendiente = null
	}

	// --- Vigilar un elemento -----------------------------------------------------------------

	/** El elemento vigilado, lo último que se dijo de él, su app y cuándo se avisó por última vez. */
	@Volatile var vigilado: AccessibilityNodeInfo? = null
		private set
	private var textoVigilado = ""
	private var appVigilada: String? = null
	private var avisoVigilado = 0L
	private var vecesFuera = 0
	private val revisarVigilado = object : Runnable {
		override fun run() {
			revisarVigilancia()
			if (vigilado != null) manejador.postDelayed(this, VIGILAR_CADA_MS)
		}
	}

	/**
	 * Vigila [n] (el coche que se acerca, una subida, un marcador, el turno de
	 * una cola) y dice lo nuevo cada vez que cambia, con un sonido, aunque el
	 * foco esté en otra parte; o deja de vigilar si ya se vigilaba algo.
	 */
	fun alternarVigilar(n: AccessibilityNodeInfo?): Boolean {
		if (vigilado != null) {
			dejarDeVigilar(R.string.vigilar_quitado)
			return true
		}
		if (n == null) {
			decir(R.string.vigilar_nada)
			return false
		}
		vigilado = n
		textoVigilado = textoFresco(n)
		appVigilada = n.packageName?.toString()
		avisoVigilado = 0L
		vecesFuera = 0
		decir(textos.getString(R.string.vigilar_puesto, textoVigilado))
		manejador.removeCallbacks(revisarVigilado)
		manejador.postDelayed(revisarVigilado, VIGILAR_CADA_MS)
		return true
	}

	fun dejarDeVigilar(motivo: Int?) {
		manejador.removeCallbacks(revisarVigilado)
		if (vigilado == null) return
		vigilado = null
		motivo?.let { decir(it) }
	}

	/** Lo que se dice del elemento, leído ahora (sin la copia de lo descrito, que sería la de antes del cambio). */
	private fun textoFresco(n: AccessibilityNodeInfo): String = compositor.describir(conRegla(n, describir.elemento(n)), verbosidad)

	private fun revisarVigilancia() {
		var n = vigilado ?: return
		// Fuera de su app no se ve: se espera un rato (la persiana, un aviso) y luego se deja
		if (raiz()?.packageName?.toString() != appVigilada) {
			if (++vecesFuera * VIGILAR_CADA_MS >= VIGILAR_FUERA_MS) dejarDeVigilar(R.string.vigilar_fuera)
			return
		}
		vecesFuera = 0
		if (!Nodos.vivo(n)) {
			// La app lo ha vuelto a crear: el mismo identificador en la pantalla de ahora
			val id = n.viewIdResourceName
			val otro = id?.let { raiz()?.findAccessibilityNodeInfosByViewId(it)?.firstOrNull() }
			if (otro == null) {
				dejarDeVigilar(R.string.vigilar_desaparecido)
				return
			}
			n = otro
			vigilado = otro
		}
		val nuevo = textoFresco(n)
		val ahora = SystemClock.uptimeMillis()
		// Lo de antes solo se cambia al avisar: si cambia muy seguido, en el siguiente aviso va lo último
		if (nuevo.isBlank() || nuevo == textoVigilado || ahora - avisoVigilado < VIGILAR_ENTRE_AVISOS_MS) return
		textoVigilado = nuevo
		avisoVigilado = ahora
		sonidos.tocar(Sonido.NOTIFICACION)
		voz.decir(Enunciado(textos.getString(R.string.vigilar_cambio, nuevo), Modo.ENCOLAR, Tipo.NOTIFICACION))
		if (brailleListo) braille.mensaje(nuevo)
	}

	/**
	 * Lo que se espera al abrirse una ventana antes de poner el foco: con las
	 * animaciones del sistema, más (la ventana aún se está moviendo y su árbol
	 * cambia); sin ellas, menos (como el intérprete de ventanas de TalkBack).
	 */
	private fun esperaVentana(): Long {
		val escala = runCatching { android.provider.Settings.Global.getFloat(servicio.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
		return if (escala <= 0f) 200L else (350L * escala.coerceAtMost(2f)).toLong().coerceAtLeast(350L)
	}

	/**
	 * Si [clase] es una pantalla (no un diálogo ni un panel): «Activity» en inglés
	 * o «Actividad», como llaman a las suyas las apps en español (Tiresias también).
	 */
	private fun esPantalla(clase: String): Boolean = clase.contains("Activity") || clase.contains("Actividad")

	/** El nombre que el usuario ha puesto a la pantalla [clase] de [paquete] (Jieshuo 42), o null. */
	fun nombrePropio(paquete: String?, clase: String?): String? {
		if (paquete == null || clase == null || !esPantalla(clase)) return null
		return runCatching { org.json.JSONObject(ajustes.p.getString(com.xebolax.tiresias.base.Claves.NOMBRES_VENTANA, "{}") ?: "{}").optString("$paquete/$clase") }
			.getOrNull()?.takeIf { it.isNotBlank() }
	}

	/**
	 * La app ha rehecho el elemento que tenía el foco (pasa al refrescar listas y
	 * en algunas apps al mínimo cambio): el más parecido de la misma ventana, por
	 * su identificador, su texto y lo cerca que está, para no perder el sitio.
	 */
	fun recuperarFoco(viejo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		val raiz = runCatching { servicio.windows.firstOrNull { it.id == viejo.windowId }?.root }.getOrNull() ?: return null
		val id = viejo.viewIdResourceName
		val nombre = (viejo.text ?: viejo.contentDescription)?.toString()?.takeIf { it.isNotBlank() }
		val antes = Nodos.limites(viejo)
		val candidatos = buildList {
			if (id != null) addAll(runCatching { raiz.findAccessibilityNodeInfosByViewId(id) }.getOrDefault(emptyList()))
			if (isEmpty() && nombre != null) addAll(runCatching { raiz.findAccessibilityNodeInfosByText(nombre) }.getOrDefault(emptyList()))
		}.filter { it.isVisibleToUser && (nombre == null || (it.text ?: it.contentDescription)?.toString() == nombre) }
		val mejor = candidatos.minByOrNull { n -> val r = Nodos.limites(n); kotlin.math.abs(r.centerX() - antes.centerX()) + kotlin.math.abs(r.centerY() - antes.centerY()) } ?: return null
		val destino = Nodos.enfocable(mejor) ?: mejor
		cambiarFoco(destino)
		return destino
	}

	/** El título del contenedor de [n] (Android 14), el más cercano hacia arriba, o null. */
	private var ultimoContenedor: String? = null

	private fun tituloContenedor(n: AccessibilityNodeInfo): String? {
		if (Build.VERSION.SDK_INT < 34) return null
		var x: AccessibilityNodeInfo? = n
		var pasos = 0
		while (x != null && pasos++ < 10) {
			runCatching { x.containerTitle?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
			x = runCatching { x.parent }.getOrNull()
		}
		return null
	}

	/** El último momento en que dijo algo cada elemento que cambia (para el plazo mínimo que pide la app, Android 14). */
	private val ultimoCambio = HashMap<Int, Long>()

	/**
	 * Si [n] pide un plazo mínimo entre cambios (`minDurationBetweenContentChanges`,
	 * Android 14: un temporizador, un ecualizador) y aún no ha pasado, false.
	 */
	private fun pasaPlazo(n: AccessibilityNodeInfo, ahora: Long): Boolean {
		if (Build.VERSION.SDK_INT < 34) return true
		val minimo = runCatching { n.minDurationBetweenContentChanges?.toMillis() }.getOrNull() ?: 0L
		if (minimo <= 0L) return true
		val clave = n.hashCode()
		val antes = ultimoCambio[clave]
		if (antes != null && ahora - antes < minimo) return false
		if (ultimoCambio.size > 200) ultimoCambio.clear()
		ultimoCambio[clave] = ahora
		return true
	}

	// --- El toque atascado ---------------------------------------------------------------

	/** Si en el toque de ahora ha habido exploración o un gesto. */
	private var exploradoEnEsteToque = false
	private var gestoEnEsteToque = false
	/** Los toques largos seguidos sin exploración ni gesto, y cuándo empezó la racha. */
	private var toquesSinExplorar = 0
	private var inicioRacha = 0L

	/** Lo que hace el servicio cuando la exploración táctil se ha atascado (reiniciarla). */
	var alToqueAtascado: (() -> Unit)? = null

	/**
	 * Samsung One UI 8.5 deja a veces un dedo «pegado»: Android sigue dando los
	 * toques al lector, pero ya no exploran (no llega ningún «encima de»). Tres
	 * toques largos seguidos así en medio minuto: se reinicia la exploración solo,
	 * como la acción «Reiniciar la exploración táctil».
	 */
	private fun vigilarToqueAtascado() {
		val ahora = SystemClock.uptimeMillis()
		if (exploradoEnEsteToque || gestoEnEsteToque || ahora - inicioToque < TOQUE_LARGO_MS) return
		if (ahora - inicioRacha > RACHA_ATASCO_MS) {
			toquesSinExplorar = 0
			inicioRacha = ahora
		}
		if (++toquesSinExplorar >= TOQUES_ATASCO) {
			toquesSinExplorar = 0
			Registro.w(ETIQUETA, "La exploración táctil parece atascada: se reinicia sola")
			alToqueAtascado?.invoke()
		}
	}

	/** Hasta cuándo un toque que empieza es de un gesto que ha mandado Tiresias. */
	@Volatile
	private var gestoPropioHasta = 0L
	/** Si el toque de ahora (desde que empezó hasta que acabe) es de un gesto de Tiresias (hilo del lector). */
	private var toquePropio = false

	/** Si [e], el principio de un toque, es de un gesto de Tiresias (por cuándo lo vio Android, no por cuándo llega aquí). */
	private fun esGestoPropio(e: AccessibilityEvent): Boolean = (e.eventTime.takeIf { it > 0 } ?: SystemClock.uptimeMillis()) <= gestoPropioHasta

	/**
	 * Manda un gesto de Tiresias a la pantalla (todos pasan por aquí) y apunta
	 * cuánto dura, para no tomar sus toques por los del usuario.
	 */
	fun mandarGesto(gesto: android.accessibilityservice.GestureDescription, alAcabar: (() -> Unit)? = null): Boolean {
		// Su toque empieza con el primer trazo: un toque que empiece después ya es del usuario
		var empieza = Long.MAX_VALUE
		for (i in 0 until gesto.strokeCount) empieza = minOf(empieza, gesto.getStroke(i).startTime)
		gestoPropioHasta = SystemClock.uptimeMillis() + empieza.coerceIn(0L, 10_000L) + MARGEN_GESTO_PROPIO_MS
		// Un gesto de un dedo (un toque, una pulsación larga, un deslizamiento) pasa directo a la app por una zona
		// de paso de toques donde empieza: si no, la exploración táctil lo tomaría por un dedo que explora y la app
		// no recibiría nada (los de dos dedos, como el arrastre de una lista, sí le llegan)
		val inicio = if (Build.VERSION.SDK_INT >= 30 && gesto.strokeCount == 1) inicioDe(gesto.getStroke(0)) else null
		val devolver: () -> Unit = inicio?.let { p ->
			gestos.pasoParaToque(android.graphics.Rect(p.x.toInt() - PASO_TOQUE_PX, p.y.toInt() - PASO_TOQUE_PX, p.x.toInt() + PASO_TOQUE_PX, p.y.toInt() + PASO_TOQUE_PX))
		} ?: {}
		// [alAcabar], en el hilo del lector cuando Android ha hecho el gesto (para seguirlo: los dedos que se quedan quietos)
		val aviso = if (alAcabar != null || inicio != null) object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
			override fun onCompleted(descripcion: android.accessibilityservice.GestureDescription?) {
				devolver()
				alAcabar?.invoke()
			}

			override fun onCancelled(descripcion: android.accessibilityservice.GestureDescription?) = devolver()
		} else null
		val mandar = {
			runCatching { servicio.dispatchGesture(gesto, aviso, aviso?.let { manejador }) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo mandar el gesto", it) }
				.getOrDefault(false)
				.also { if (!it) devolver() }
		}
		if (inicio == null) return mandar()
		// Android pone la zona de paso un momento después de pedirla: el gesto va detrás
		gestoPropioHasta += ESPERA_PASO_TOQUE_MS
		manejador.postDelayed({ mandar() }, ESPERA_PASO_TOQUE_MS)
		return true
	}

	/** Dónde empieza [trazo]. */
	private fun inicioDe(trazo: android.accessibilityservice.GestureDescription.StrokeDescription): android.graphics.PointF? {
		val medida = android.graphics.PathMeasure(trazo.path, false)
		val punto = FloatArray(2)
		return if (medida.getPosTan(0f, punto, null)) android.graphics.PointF(punto[0], punto[1])
		else android.graphics.RectF().also { trazo.path.computeBounds(it, true) }.let { android.graphics.PointF(it.left, it.top) }
	}

	/** Un clic de accesibilidad en [n] o, si la app no lo atiende, un toque de verdad en su centro. */
	private fun pulsar(n: AccessibilityNodeInfo) {
		soltarProteccionFoco()
		if (runCatching { n.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)) return
		val r = Nodos.visibleEnVentana(n)
		if (r.isEmpty) return
		val camino = android.graphics.Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
		mandarGesto(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(camino, 0, 40)).build())
	}

	/** Atrás, Inicio, Recientes... de la barra de navegación del sistema (por su identificador, como Backtalk). */
	private fun esBotonDeLaBarra(n: AccessibilityNodeInfo): Boolean {
		if (n.packageName?.toString() != "com.android.systemui") return false
		val id = n.viewIdResourceName?.substringAfter(":id/", "") ?: return false
		return id in BOTONES_BARRA
	}

	private fun alExplorar(e: AccessibilityEvent) {
		exploradoEnEsteToque = true
		toquesSinExplorar = 0
		val origen = e.source ?: return
		if (Nodos.enfocable(origen)?.let { it != focoAlTocar } != false) tocoOtro = true
		virtual?.let { v ->
			// Android no da el punto exacto del dedo a un lector sin quitarle los toques: por el centro de lo que
			// hay debajo, si es pequeño (en una vista que ocupa toda la pantalla, se recorre deslizando)
			val r = Nodos.limites(origen)
			val m = servicio.resources.displayMetrics
			if (r.width() * r.height() < m.widthPixels * m.heightPixels / 4) virtualEnPunto(r.exactCenterX(), r.exactCenterY())
			return
		}
		val destino = Nodos.enfocable(origen) ?: return
		// El fondo de la pantalla de bloqueo (detrás del reloj y del teclado del PIN), al tocar entre las teclas:
		// no se dice «Pantalla de bloqueo» cada vez, el foco se queda en lo último tocado (SandFly, 657ca24b5)
		if (esFondoDelBloqueo(destino) || esPaginaDelEscritorio(destino)) return
		ultimoExplorado = destino
		// Las teclas del teclado en pantalla: se apunta la última que se toca
		teclaExplorada = if (runCatching { destino.window?.type }.getOrNull() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) destino else null
		if (destino == foco) return
		exploracionPendiente?.let { manejador.removeCallbacks(it) }
		val espera = ajustes.retardoExploracion
		if (espera <= 0) {
			enfocar(destino, Sonido.TOQUE)
			return
		}
		val r = Runnable { if (destino != foco) enfocar(destino, Sonido.TOQUE) }
		exploracionPendiente = r
		manejador.postDelayed(r, espera)
	}

	/**
	 * Una vista de SystemUI sin hijos que no se pulsa y ocupa un tercio o más de su ventana, con el móvil
	 * bloqueado: el fondo de la pantalla de bloqueo, que Android nombra «Pantalla de bloqueo».
	 */
	private fun esFondoDelBloqueo(n: AccessibilityNodeInfo): Boolean {
		if (n.packageName?.toString() != PAQUETE_SISTEMA || n.childCount > 0 || n.isClickable || n.isLongClickable) return false
		if (!bloqueado()) return false
		val r = Nodos.limites(n)
		val v = android.graphics.Rect().also { runCatching { n.window?.getBoundsInScreen(it) } }
		if (v.isEmpty) return false
		return r.width().toLong() * r.height() * 3 >= v.width().toLong() * v.height()
	}

	/**
	 * La página del escritorio («Pantalla de inicio 1 de 2»), al tocar entre los iconos: no se pulsa y tiene
	 * iconos dentro o cubre un tercio de la ventana o más. No se dice cada vez (SandFly, HomeScreenPages).
	 */
	private fun esPaginaDelEscritorio(n: AccessibilityNodeInfo): Boolean {
		if (n.packageName?.toString() != escritorio() || Nodos.accionable(n)) return false
		var conIconos = false
		for (i in 0 until minOf(n.childCount, 40)) {
			val h = runCatching { n.getChild(i) }.getOrNull() ?: continue
			if (Nodos.accionable(h) || h.isFocusable) {
				conIconos = true
				break
			}
		}
		if (conIconos) return true
		val r = Nodos.limites(n)
		val v = android.graphics.Rect().also { runCatching { n.window?.getBoundsInScreen(it) } }
		return !v.isEmpty && r.width().toLong() * r.height() * 3 >= v.width().toLong() * v.height()
	}

	/** El paquete del escritorio de ahora (se vuelve a mirar cada minuto: se puede cambiar de escritorio). */
	private var paqueteEscritorio: String? = null
	private var escritorioMirado = 0L

	private fun escritorio(): String? {
		val ahora = SystemClock.uptimeMillis()
		if (ahora - escritorioMirado > 60_000L) {
			escritorioMirado = ahora
			paqueteEscritorio = runCatching {
				val inicio = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
				servicio.packageManager.resolveActivity(inicio, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
			}.getOrNull()
		}
		return paqueteEscritorio
	}

	/** El foco de accesibilidad ha cambiado (por Tiresias, por la app o por el sistema). */
	private fun alEnfocar(e: AccessibilityEvent) {
		val n = e.source ?: return
		if (calladoAlEncender(e.packageName)) {
			cambiarFoco(n)
			return
		}
		// La app avisa del foco del elemento que esperaba a rellenarse: ya se puede decir
		porRellenar?.takeIf { it.nodo == n }?.let {
			decirRellena(it)
			return
		}
		val ahora = SystemClock.uptimeMillis()
		val yaDicho = n == dichoAlEnfocar && ahora - momentoDicho < 2000
		// El aviso tardío de un elemento que Tiresias enfocó antes del actual (deslizando deprisa):
		// ni se vuelve a él ni se repite
		if (!yaDicho && enfocadosPropios.any { it.first == n && ahora - it.second < 2000 }) return
		// Protección del foco (VoiceOver): justo después de que el usuario se mueva, la app no se lleva el foco
		// a otro elemento de la misma ventana (un carrusel que avanza solo, una lista que se rehace); se queda donde iba
		val actual = foco
		if (!yaDicho && actual != null && n != actual && ahora - momentoMovido < PROTEGER_FOCO_MS && n.windowId == actual.windowId &&
			Nodos.vivo(actual) && actual.isVisibleToUser) {
			if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d(ETIQUETA, "La app quiso llevarse el foco justo después de moverse: se deja donde estaba")
			runCatching { actual.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) }
			return
		}
		// El teclado del PIN de la pantalla de bloqueo: SystemUI pone el foco en su contenedor (sin texto propio,
		// no enfocable) justo después del campo del PIN, y leerlo diría toda la numeración («1, 2, 3...»).
		// El foco se queda donde estaba
		if (n.packageName?.toString() == PAQUETE_SISTEMA && n.childCount > 0 && !n.isFocusable && !Nodos.tieneTexto(n) && bloqueado()) {
			if (actual != null && actual != n && Nodos.vivo(actual)) runCatching { actual.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) }
			return
		}
		cambiarFoco(n)
		if (yaDicho) return
		// Justo después de anunciar una ventana, el foco inicial va detrás del título (no lo corta)
		val modo = if (ahora - momentoVentana < 600) Modo.ENCOLAR else Modo.INTERRUMPIR
		decirElemento(n, modo)
	}

	/** Pone el foco de accesibilidad en [n] y lo dice enseguida (sin esperar a la app). */
	fun enfocar(n: AccessibilityNodeInfo, sonido: Sonido? = Sonido.FOCO, modo: Modo = Modo.INTERRUMPIR) {
		momentoMovido = SystemClock.uptimeMillis()
		cambiarFoco(n)
		// Una fila que la app rellena al recibir el foco (Telegram, Nekogram: aún sin texto, sin descripción y sin
		// hijos): se dice cuando la app avisa de su foco, ya rellena; si no avisa, al poco con lo que tenga (Backtalk 240)
		val vacia = vaciaDeMomento(n)
		if (vacia) {
			val r = Rellenar(n, modo, sonido)
			porRellenar = r
			manejador.postDelayed({ if (porRellenar === r) decirRellena(r) }, ESPERA_RELLENO_MS)
		} else {
			porRellenar = null
			apuntarPropio(n)
			decirElemento(n, modo, sonido)
			if (ajustes.leerMasSolo) abrirLeerMas(n)
		}
		val puesto = runCatching { n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) }.getOrDefault(false)
		if (!puesto && com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d(ETIQUETA, "La app no aceptó el foco de accesibilidad; se sigue con el foco propio")
		// Sin el foco de accesibilidad en él, Android pulsaría otra cosa: el doble toque sigue siendo de Tiresias
		if (!puesto) mantenerDeVerdad(false)
		// La frase de los vecinos, preparada para el siguiente deslizamiento
		manejador.post { navegador.prepararVecinos(n) }
	}

	/** Un elemento enfocado por Tiresias que se dirá cuando la app lo rellene (o al poco, si no avisa). */
	private class Rellenar(val nodo: AccessibilityNodeInfo, val modo: Modo, val sonido: Sonido?)
	private var porRellenar: Rellenar? = null

	/** Sin nada que decir todavía: ni texto, ni descripción, ni estado, ni hijos (ni es un campo). */
	private fun vaciaDeMomento(n: AccessibilityNodeInfo): Boolean =
		n.childCount == 0 && !n.isEditable && n.text.isNullOrEmpty() && n.contentDescription.isNullOrEmpty() &&
			(Build.VERSION.SDK_INT < 30 || n.stateDescription.isNullOrEmpty()) && (Build.VERSION.SDK_INT < 26 || n.hintText.isNullOrEmpty())

	private fun decirRellena(r: Rellenar) {
		porRellenar = null
		val n = r.nodo
		if (foco != n) return
		runCatching { n.refresh() }
		apuntarPropio(n)
		decirElemento(n, r.modo, r.sonido)
	}

	/** El último «Leer más» pulsado solo, para no pulsar dos veces el mismo mensaje. */
	private var leerMasPulsado: AccessibilityNodeInfo? = null
	private val vistaVacia by lazy { android.view.View(servicio) }

	/**
	 * Si [n] acaba en un enlace «Leer más» (los mensajes largos de WhatsApp o
	 * Telegram, android_accesible, julio de 2026), se pulsa solo y, cuando la app
	 * enseña el mensaje entero, se dice lo que faltaba, a la cola.
	 */
	private fun abrirLeerMas(n: AccessibilityNodeInfo) {
		if (n == leerMasPulsado) return
		val enlaces = Nodos.enlacesEnTexto(n)
		val (texto, enlace) = enlaces.lastOrNull()?.takeIf { LEER_MAS.matches(it.first.trim()) } ?: return
		val antes = n.text?.toString().orEmpty().replace(texto, "").trim()
		leerMasPulsado = n
		try {
			enlace.onClick(vistaVacia)
		} catch (e: RuntimeException) {
			Registro.w(ETIQUETA, "No se pudo pulsar «Leer más»", e)
			return
		}
		manejador.postDelayed({
			if (foco != n || !Nodos.vivo(n)) return@postDelayed
			val despues = n.text?.toString().orEmpty().trim()
			val resto = if (antes.isNotEmpty() && despues.startsWith(antes.trimEnd('.', '…', ' '))) despues.substring(antes.trimEnd('.', '…', ' ').length).trim() else despues
			if (resto.isNotEmpty() && resto != antes) voz.decir(Enunciado(resto, Modo.ENCOLAR, Tipo.NAVEGACION, vigente = { foco == n }))
		}, 450)
	}

	/** Pone el foco en [n] sin decirlo (lo dice quien llama: la lectura continua). */
	fun enfocarSinHablar(n: AccessibilityNodeInfo) {
		cambiarFoco(n)
		apuntarPropio(n)
		runCatching { n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) }
	}

	private fun apuntarPropio(n: AccessibilityNodeInfo) {
		dichoAlEnfocar = n
		momentoDicho = SystemClock.uptimeMillis()
		enfocadosPropios.addFirst(n to momentoDicho)
		while (enfocadosPropios.size > 8) enfocadosPropios.removeLast()
	}

	/** Lo que se dice de [n] con la verbosidad actual (sin decirlo). */
	fun textoElemento(n: AccessibilityNodeInfo): String = compositor.describir(elemento(n), verbosidad)

	/** El nombre de un carácter suelto («a», «mayúscula b», «arroba»). */
	fun edicionCaracter(c: String): String = voz.nombreCaracter(c)

	/** Dice [n] con la verbosidad actual y, tras una pausa, su pista de uso. */
	fun decirElemento(n: AccessibilityNodeInfo, modo: Modo = Modo.INTERRUMPIR, sonido: Sonido? = null) {
		val elemento = elemento(n)
		val normal = compositor.describir(elemento, verbosidad)
		// Un complemento de esta app puede cambiar lo que se dice (arreglar etiquetas, quitar ruido)
		val texto = if (scriptsListos) scripts.alEnfocar(n.packageName?.toString().orEmpty(), elemento, normal) ?: normal else normal
		if (sonido != null) {
			val limites = Nodos.limites(n)
			val ancho = servicio.resources.displayMetrics.widthPixels.coerceAtLeast(1)
			sonidos.tocar(if (elemento.tieneAcciones && verbosidad.decirAcciones) Sonido.ACCIONES else sonido, limites.exactCenterX() / ancho, tonoDe(elemento, limites))
		}
		// Las teclas de un teclado que habla solo: ya las dice él
		if (edicion.tecladoHablaSolo() && runCatching { n.window?.type }.getOrNull() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
			if (brailleListo) braille.mostrarElemento(n, texto)
			return
		}
		// Al entrar en un contenedor con título (Android 14, containerTitle), se dice antes su título
		val contenedor = tituloContenedor(n)
		val dicho = if (contenedor != null && contenedor != ultimoContenedor) "$contenedor, $texto" else texto
		ultimoContenedor = contenedor
		voz.decir(Enunciado(dicho, modo = modo, tipo = Tipo.NAVEGACION, idioma = idiomaDe(n), vigente = if (modo == Modo.ENCOLAR) ({ foco == n }) else null))
		if (brailleListo) braille.mostrarElemento(n, texto)
		// Sin nombre: Tiresias intenta reconocer el icono (en el móvil) y lo dice después
		if (visionListo && compositor.sinNombre(elemento)) vision.iconos.alEnfocarSinNombre(n)
		programarPista(n, elemento)
		// Después de hablar: recordar dónde estaba en esta pantalla (cuesta una consulta a la app)
		manejador.post { memoria.recordar(n, elemento.nombre) }
	}

	/**
	 * El idioma que la app indica para el texto de [n] (un LocaleSpan que cubre al
	 * menos la mitad del texto), si es otro que el de Tiresias. Un nombre propio en
	 * otro idioma dentro de una frase no cambia la voz.
	 */
	fun idiomaDe(n: AccessibilityNodeInfo): java.util.Locale? {
		if (!ajustes.cambioIdioma) return null
		val texto = (n.text as? android.text.Spanned) ?: (n.contentDescription as? android.text.Spanned) ?: return idiomaDetectado(n)
		if (texto.isEmpty()) return null
		val spans = texto.getSpans(0, texto.length, android.text.style.LocaleSpan::class.java)
		val span = spans.maxByOrNull { texto.getSpanEnd(it) - texto.getSpanStart(it) } ?: return idiomaDetectado(n)
		if ((texto.getSpanEnd(span) - texto.getSpanStart(span)) * 2 < texto.length) return null
		val indicado = span.locale ?: return null
		val propio = Idioma.efectivo(servicio)
		val dialecto = ajustes.cambioDialecto
		if (indicado.language == propio.language && (!dialecto || indicado.country.isEmpty() || indicado.country == propio.country)) return null
		return if (dialecto) indicado else java.util.Locale(indicado.language)
	}

	/**
	 * El idioma de un texto largo que la app no marca (español o inglés), si es
	 * otro que el de Tiresias y se ha dejado «Detectar el idioma» (Ajustes, Voz).
	 */
	private fun idiomaDetectado(n: AccessibilityNodeInfo): java.util.Locale? {
		if (!ajustes.detectarIdioma) return null
		val texto = (n.text ?: n.contentDescription)?.toString().orEmpty()
		if (texto.length < 30) return null
		val detectado = com.xebolax.tiresias.nucleo.DetectorIdioma.detectar(texto) ?: return null
		return if (detectado == Idioma.efectivo(servicio).language) null else java.util.Locale(detectado)
	}

	/**
	 * El tono del sonido del foco: según el tipo de control y la altura en la
	 * pantalla, si se han elegido (Backtalk 22, como Unspoken de NVDA).
	 */
	private fun tonoDe(e: Elemento, limites: android.graphics.Rect): Float {
		var tono = 1f
		if (ajustes.sonidoTipo) tono *= when {
			e.encabezado -> 1.35f
			e.editable -> 0.8f
			e.rol == com.xebolax.tiresias.nucleo.Rol.ENLACE -> 1.2f
			e.marcado != com.xebolax.tiresias.nucleo.Marcado.NO_MARCABLE -> 1.1f
			e.rol == com.xebolax.tiresias.nucleo.Rol.IMAGEN -> 0.9f
			e.pulsable -> 1f
			else -> 0.7f
		}
		if (ajustes.sonidoVertical) {
			val alto = servicio.resources.displayMetrics.heightPixels.coerceAtLeast(1)
			tono *= 1.3f - 0.5f * (limites.exactCenterY() / alto).coerceIn(0f, 1f)
		}
		return tono
	}

	/** [e] con lo que cambian las reglas de los complementos (tipo, estado, pista, encabezado). */
	private fun conRegla(n: AccessibilityNodeInfo, e: Elemento): Elemento {
		if (!scriptsListos) return e
		val r = scripts.regla(n) ?: return e
		return e.copy(
			descripcionRol = r.rol ?: e.descripcionRol,
			estado = r.estado ?: e.estado,
			pista = r.pista ?: e.pista,
			encabezado = r.encabezado ?: e.encabezado,
		)
	}

	/** La clase de la pantalla de delante (la última ventana de una actividad), para las reglas por actividad. */
	@Volatile var actividadDelante: String? = null
		private set

	/** El título de la ventana de delante, para las reglas por ventana. */
	fun tituloVentanaActual(): String = tituloVentana

	/** El compositor en el idioma de Tiresias, con su tabla de identificadores. */
	private fun nuevoCompositor(): Compositor {
		val idioma = Idioma.efectivo(servicio)
		// Las pistas nombran el gesto que el usuario tiene para el menú de acciones (o el menú, si no tiene)
		val gestoAcciones = ajustes.mapaGestos.gestosDe(com.xebolax.tiresias.nucleo.Accion.MENU_ACCIONES).firstOrNull()?.let { g ->
			val id = textos.resources.getIdentifier("gesto_" + g.name.lowercase(), "string", servicio.packageName)
			if (id != 0) textos.getString(id) else null
		}
		val gestos = Compositor.GestosDePistas(
			menuAcciones = gestoAcciones,
			alLevantar = ajustes.activarAlLevantar == Ajustes.LEVANTAR_PANTALLA,
			accionesArribaAbajo = ajustes.accionesArribaAbajo && ajustes.mapaGestos[com.xebolax.tiresias.nucleo.Gesto.ARRIBA] == com.xebolax.tiresias.nucleo.Accion.SELECTOR_ANTERIOR,
		)
		return Compositor(PlantillasAndroid(servicio), idioma, com.xebolax.tiresias.textos.DatosIdioma.de(servicio, idioma).identificadores, gestos)
	}

	/** El [Elemento] de [n], de la caché si está. */
	fun elemento(n: AccessibilityNodeInfo): Elemento = descritos[n] ?: conRegla(n, describir.elemento(n)).also {
		if (descritos.size > 200) descritos.clear()
		descritos[n] = it
	}

	/** Prepara (sin decirlo) el elemento de [n]. */
	fun preparar(n: AccessibilityNodeInfo) {
		if (n !in descritos) runCatching { elemento(n) }
	}

	private var pistaPendiente: Runnable? = null

	private fun programarPista(n: AccessibilityNodeInfo, elemento: Elemento) {
		pistaPendiente?.let { manejador.removeCallbacks(it) }
		val pista = compositor.pista(elemento, verbosidad) ?: return
		val r = Runnable {
			if (foco == n && !continua.activa) voz.decir(Enunciado(pista, modo = Modo.ENCOLAR, tipo = Tipo.PISTA))
		}
		pistaPendiente = r
		manejador.postDelayed(r, ajustes.esperaPistas)
	}

	/** El usuario ya está haciendo algo con el elemento (eligiendo una acción, cambiando un valor): la pista sobra. */
	fun cancelarPista() {
		pistaPendiente?.let { manejador.removeCallbacks(it) }
		pistaPendiente = null
	}

	/**
	 * Una ventana del sistema que aparece sin que el usuario haya hecho nada
	 * (la persiana, un aviso del sistema): puede ser una notificación emergente
	 * (heads-up), que Android pone en la ventana de la persiana. Se decide un
	 * momento después, cuando ya ha llegado su notificación.
	 */
	private fun sinQueLoPidaElUsuario(e: AccessibilityEvent): Boolean {
		if (e.packageName != PAQUETE_SISTEMA) return false
		val ahora = SystemClock.uptimeMillis()
		// Si el usuario abrió la persiana (o los ajustes rápidos), es lo que pidió: se anuncia
		if (ahora - momentoPersiana < 3000) return false
		// Sin que haga nada, o con una notificación recién llegada (la emergente puede salir mientras hace otra cosa)
		return ahora - momentoUsuario > 2000 || llegoNotificacion(System.currentTimeMillis() - 6000)
	}

	/** Cuándo abrió el usuario la persiana o los ajustes rápidos con Tiresias. */
	@Volatile var momentoPersiana = 0L

	/**
	 * Si acaba de llegar una notificación (desde [desde], en la hora del reloj):
	 * entonces la ventana era la emergente, que no se anuncia ni se lleva el foco.
	 * La notificación ya se dice (o se calla, si su app está silenciada) por su
	 * propio aviso, como en TalkBack.
	 */
	private fun llegoNotificacion(desde: Long): Boolean = avisos.historial().firstOrNull()?.let { it.momento >= desde } == true

	/** Cuándo hizo algo el usuario por última vez (un toque, un gesto, una tecla, una acción). */
	@Volatile var momentoUsuario = 0L

	private fun alCambiarVentana(e: AccessibilityEvent, comprobada: Boolean = false) {
		// El teclado en pantalla cambia de ventana con cada tecla, con las mayúsculas o los símbolos (Gboard, SwiftKey,
		// Samsung): no es una pantalla nueva. Su aparición ya se dice una vez (alCambiarVentanas); lo de dentro, al tocarlo.
		if (esDelTeclado(e)) {
			olvidarCaches()
			return
		}
		virtual?.let { v -> if (e.windowId != v.ventana && e.packageName != servicio.packageName) salirVirtual(true) }
		if (calladoAlEncender(e.packageName)) {
			olvidarCaches()
			return
		}
		if (!comprobada && sinQueLoPidaElUsuario(e)) {
			val copia = copiar(e) ?: return
			val desde = System.currentTimeMillis() - 6000
			manejador.postDelayed({ if (!llegoNotificacion(desde)) alCambiarVentana(copia, comprobada = true) }, ESPERA_EMERGENTE_MS)
			return
		}
		olvidarCaches()
		// La raíz de un menú o diálogo de Tiresias, por si la lista de ventanas aún no lo tiene
		if (e.packageName == servicio.packageName) e.source?.let { raizVentanaPropia = subirARaiz(it) }
		// La pantalla de delante (no un diálogo ni un panel: esos no cambian de actividad)
		e.className?.toString()?.takeIf { esPantalla(it) }?.let { actividadDelante = it }
		val titulo = nombrePropio(e.packageName?.toString(), e.className?.toString()) ?: tituloDe(e)
		val ahora = SystemClock.uptimeMillis()
		if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasVentana", "ventana ${e.windowId} ${e.packageName} ${e.className} tipos=${e.contentChangeTypes} «$titulo»")
		// Los cambios de panel (Android 9+) avisan con su propio tipo de cambio
		val tipos = e.contentChangeTypes
		// Se cerró un panel o un menú lateral en la misma ventana: el foco vuelve a donde estaba antes de abrirlo
		manejador.postDelayed({ volverAlFocoAnterior(e.windowId) }, 450)
		if (tipos and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED != 0) return
		// Los complementos que quieren saber de las pantallas nuevas (tiresias.alVentana)
		if (scriptsListos && e.packageName != null && e.packageName != servicio.packageName) scripts.alVentana(e.packageName.toString(), actividadDelante.orEmpty(), titulo)
		// El mismo título otra vez (o con el texto de un botón detrás: «Título» y luego «Título Cancelar»), no se repite
		val repetido = tituloVentana.isNotEmpty() && (titulo == tituloVentana ||
			(e.windowId == idVentanaTitulo && (titulo.startsWith(tituloVentana) || tituloVentana.startsWith(titulo))))
		if (titulo.isBlank() || (repetido && ahora - momentoVentana < 1500)) return
		if (!filtro.pasa("ventana", titulo, ahora)) return
		tituloVentana = titulo
		idVentanaTitulo = e.windowId
		momentoVentana = ahora
		sonidos.tocar(Sonido.VENTANA)
		voz.decir(Enunciado(titulo, modo = Modo.INTERRUMPIR, tipo = Tipo.NAVEGACION))
		if (brailleListo) braille.mensaje(titulo)
		// El foco de otra ventana ya no vale: el siguiente deslizamiento empieza en la nueva
		if (foco?.let { it.windowId != e.windowId || !Nodos.vivo(it) } == true) perderFoco()
		val dialogo = esDialogo(e)
		cuandoEstable(e.windowId, ahora + esperaVentana()) {
			// Volver a donde estaba en esta ventana (también al pulsar Atrás: Backtalk 151)
			// (en los menús y diálogos de Tiresias, siempre su primera opción: no la última que se tocó)
			if (ajustes.recordarFoco && !(dialogo && e.packageName == servicio.packageName)) memoria.restaurar(this, e.windowId)
			// La app pide dónde empezar (Android 14, requestInitialAccessibilityFocus): ahí va el foco
			if (foco?.windowId != e.windowId) focoInicialPedido(e.windowId)?.let {
				enfocar(it, null, if (ajustes.focoSinEsperarTitulo) Modo.INTERRUMPIR else Modo.ENCOLAR)
				return@cuandoEstable
			}
			// En un diálogo (también los de Tiresias), el foco entra en él: su primer elemento, tras el título
			// (el título ya se ha dicho: el foco va a lo primero que no es el título)
			// (la ventana del diálogo, no la activa: los menús de Tiresias van encima de la app sin ser la activa)
			val raizDialogo = runCatching { servicio.windows.firstOrNull { it.id == e.windowId }?.root }.getOrNull() ?: raiz()?.takeIf { it.windowId == e.windowId }
			// (si el foco sigue en otra ventana, también: se deslizó antes de que acabara de abrirse)
			if ((dialogo || ajustes.focoVentanasNuevas) && foco?.windowId != e.windowId) raizDialogo?.let { r ->
				val orden = navegador.orden(r)
				val primero = orden.firstOrNull { n ->
					val nombre = describir.elemento(n).nombre.trim()
					// (el título de la ventana a veces trae sus textos detrás: solo se salta lo que lo empieza)
					nombre.isNotEmpty() && !titulo.startsWith(nombre, ignoreCase = true)
				} ?: orden.firstOrNull()
				// Con «sin esperar al título», el primer elemento corta el título (el sonido de ventana ya avisa)
				primero?.let { enfocar(it, null, if (ajustes.focoSinEsperarTitulo) Modo.INTERRUMPIR else Modo.ENCOLAR) }
			}
		}
	}

	/** Cuándo cambió por última vez el contenido de cada ventana, para saber si una ventana nueva ya está quieta. */
	private val cambiosPorVentana = HashMap<Int, Long>()

	/**
	 * Hace [accion] en [desde] (la espera de la ventana) o, si la ventana [ventana]
	 * sigue cambiando (se está construyendo), cuando lleve un momento quieta, sin
	 * esperar nunca más de [ESTABLE_MAXIMO_MS] de más: así el foco no se pone en lo
	 * que aún no está ni en lo que la app va a quitar.
	 */
	private fun cuandoEstable(ventana: Int, desde: Long, accion: () -> Unit) {
		val limite = desde + ESTABLE_MAXIMO_MS
		fun probar() {
			val ahora = SystemClock.uptimeMillis()
			if (ahora - (cambiosPorVentana[ventana] ?: 0L) < ESTABLE_QUIETA_MS && ahora < limite) {
				manejador.postDelayed({ probar() }, ESTABLE_QUIETA_MS)
				return
			}
			accion()
		}
		manejador.postAtTime({ probar() }, desde)
	}

	/** El elemento de la ventana [ventana] que la app ha pedido que tenga el foco al abrirla (Android 14), o null. */
	private fun focoInicialPedido(ventana: Int): AccessibilityNodeInfo? {
		if (Build.VERSION.SDK_INT < 34) return null
		val r = runCatching { servicio.windows.firstOrNull { it.id == ventana }?.root }.getOrNull() ?: return null
		val cola = ArrayDeque<AccessibilityNodeInfo>()
		cola.add(r)
		var vistos = 0
		while (cola.isNotEmpty() && vistos++ < 400) {
			val n = cola.removeFirst()
			if (runCatching { n.hasRequestInitialAccessibilityFocus() }.getOrDefault(false) && n.isVisibleToUser) return Nodos.enfocable(n) ?: n
			for (i in 0 until n.childCount) runCatching { n.getChild(i) }.getOrNull()?.let { cola.add(it) }
		}
		return null
	}

	/** Cuándo puso Tiresias el foco en un elemento (al deslizar, al tocar, al buscar): lo protege un momento. */
	private var momentoMovido = 0L

	/** Tras activar algo, la app puede mover el foco al momento (a un diálogo, al resultado). */
	fun soltarProteccionFoco() {
		momentoMovido = 0L
	}

	/** Los últimos elementos que tuvieron el foco, para volver a ellos al cerrarse un panel o un diálogo. */
	private val focosAnteriores = ArrayDeque<AccessibilityNodeInfo>()

	private fun cambiarFoco(n: AccessibilityNodeInfo) {
		val anterior = foco
		foco = n
		// Una contraseña o un dato sensible: lo que se diga de él no va al registro de la variante de pruebas
		voz.focoSensible = Nodos.sensible(n)
		if (anterior != n) cambiosFoco++
		if (anterior != null && anterior != n) apuntarAnterior(anterior)
		mantenerDeVerdad(n.packageName?.toString() != servicio.packageName && Nodos.esDeMantenerPulsado(n))
	}

	/** El foco se ha ido (lo que lo tenía se ocultó o se cerró): se apunta por si vuelve a verse. */
	private fun perderFoco() {
		foco?.let { apuntarAnterior(it) }
		foco = null
		voz.focoSensible = false
		cambiosFoco++
		mantenerDeVerdad(false)
	}

	/**
	 * Lo que hace el servicio cuando el foco llega a un botón de mantener pulsado (true) o sale de él:
	 * el doble toque lo hace entonces Android, que con el doble toque mantenido deja el botón pulsado
	 * de verdad mientras no se levanta el dedo (como con TalkBack). Desde el hilo del lector.
	 */
	var alMantenerDeVerdad: ((Boolean) -> Unit)? = null
	private var enBotonDeMantener = false

	private fun mantenerDeVerdad(si: Boolean) {
		if (si == enBotonDeMantener) return
		enBotonDeMantener = si
		alMantenerDeVerdad?.invoke(si)
	}

	private fun apuntarAnterior(n: AccessibilityNodeInfo) {
		focosAnteriores.remove(n)
		focosAnteriores.addLast(n)
		while (focosAnteriores.size > 8) focosAnteriores.removeFirst()
	}

	/**
	 * Tras cerrarse un panel o un diálogo: si el foco se ha perdido (estaba en lo
	 * que se cerró), vuelve al elemento desde el que se abrió y lo dice. Es lo que
	 * hace TalkBack y en lo que confían las apps (el menú lateral, los diálogos).
	 */
	private fun volverAlFocoAnterior(ventana: Int? = null) {
		val actual = foco
		if (actual != null && Nodos.vivo(actual) && actual.isVisibleToUser) return
		// Solo en la ventana que queda delante (en otra pantalla no tiene sentido volver)
		val delante = ventana ?: runCatching { servicio.windows.firstOrNull { it.isActive }?.id }.getOrNull() ?: return
		while (focosAnteriores.isNotEmpty()) {
			val previo = focosAnteriores.removeLast()
			if (previo.windowId == delante && Nodos.vivo(previo) && previo.isVisibleToUser) {
				enfocar(previo, null, Modo.ENCOLAR)
				return
			}
		}
	}

	/** Si el evento es de la ventana del teclado en pantalla (o de su app, si la lista de ventanas aún no lo tiene). */
	private fun esDelTeclado(e: AccessibilityEvent): Boolean {
		val paquete = e.packageName?.toString() ?: return false
		if (paquete == servicio.packageName) return false
		val ventanas = runCatching { servicio.windows }.getOrNull().orEmpty()
		ventanas.firstOrNull { it.id == e.windowId }?.let { return it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
		// (los ajustes del teclado son una pantalla de su app: solo cuenta mientras el teclado está a la vista)
		if (ventanas.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) return false
		val teclado = runCatching { android.provider.Settings.Secure.getString(servicio.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()?.substringBefore('/')
		return paquete == teclado
	}

	/** Si la ventana del evento es un diálogo o una ventana de Tiresias encima de la app. */
	private fun esDialogo(e: AccessibilityEvent): Boolean {
		if (e.className?.toString()?.contains("Dialog") == true) return true
		if (e.packageName == servicio.packageName) return true
		val v = runCatching { servicio.windows.firstOrNull { it.id == e.windowId } }.getOrNull() ?: return false
		return v.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || v.type == AccessibilityWindowInfo.TYPE_SYSTEM
	}

	private fun tituloDe(e: AccessibilityEvent): String {
		// En las ventanas del sistema (la persiana), solo el título: el resto es su contenido, que se lee al navegar
		val texto = (if (e.packageName == PAQUETE_SISTEMA) e.text.firstOrNull()?.toString().orEmpty() else e.text.joinToString(" ") { it.toString() }).trim()
		val clase = e.className?.toString().orEmpty()
		// La pantalla de bloqueo se anuncia como tal, no como «systemui» (Backtalk 208)
		if (e.packageName == "com.android.systemui" && (texto.isBlank() || texto.equals("systemui", true))) {
			return if (servicio.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) textos.getString(R.string.lect_pantalla_bloqueo) else ""
		}
		if (texto.isNotBlank()) return texto
		if (Build.VERSION.SDK_INT >= 28) e.source?.paneTitle?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
		return e.contentDescription?.toString()?.takeIf { it.isNotBlank() } ?: if (clase.contains("Dialog")) textos.getString(R.string.lect_dialogo) else ""
	}

	private fun alCambiarVentanas(e: AccessibilityEvent) {
		olvidarCaches()
		// Una ventana que se cierra (un diálogo de la app): el foco vuelve a la de debajo
		if (Build.VERSION.SDK_INT >= 28 && e.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0) {
			manejador.postDelayed({ volverAlFocoAnterior() }, 300)
		}
		// (si Android no da la lista de ventanas, no se sabe nada del teclado: no se cambia lo que había)
		val ventanas = runCatching { servicio.windows }.getOrNull() ?: return
		val teclado = ventanas.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
		// El teclado en pantalla aparece: se dice una vez. No cuando pasa a ser la ventana activa (al explorar sus teclas
		// o al escribir cada letra), ni cuando el teclado se rehace al momento (al cambiar de idioma o de teclas)
		val ahora = SystemClock.uptimeMillis()
		if (teclado != null && !hayTeclado && ahora - sinTecladoDesde > TECLADO_REHECHO_MS) {
			voz.decir(Enunciado(textos.getString(R.string.lect_teclado), Modo.ENCOLAR, Tipo.ANUNCIO))
		}
		if (teclado == null && hayTeclado) sinTecladoDesde = ahora
		hayTeclado = teclado != null
		// Con «Toque directo en el teclado», los toques en el teclado en pantalla pasan directos a él
		if (ajustes.toqueDirectoTeclado) {
			val zona = teclado?.let { android.graphics.Rect().also { r -> it.getBoundsInScreen(r) } }
			gestos.zonaDirecta(zona)
		}
	}

	// CONTENT_CHANGE_TYPE_STATE_DESCRIPTION (Android 11) es una constante: antes nunca llega ese tipo, sin fallo
	@android.annotation.SuppressLint("InlinedApi")
	private fun alCambiarContenido(e: AccessibilityEvent) {
		navegador.invalidar(e.windowId)
		cambiosPorVentana[e.windowId] = SystemClock.uptimeMillis()
		if (cambiosPorVentana.size > 32) cambiosPorVentana.clear()
		descritos.clear()
		val n = e.source ?: return
		// Una regla de un complemento puede silenciar los cambios de este elemento (o de lo que hay dentro de otro)
		if (scriptsListos && scripts.silenciado(n)) return
		val tipos = e.contentChangeTypes
		// Región viva: se dice lo nuevo (educada, a la cola; urgente, cortando), sin repetir
		val viva = runCatching { n.liveRegion }.getOrDefault(0)
		if (viva != android.view.View.ACCESSIBILITY_LIVE_REGION_NONE && calladoAlEncender(e.packageName)) return
		if (viva != android.view.View.ACCESSIBILITY_LIVE_REGION_NONE && (tipos and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT != 0 || tipos == 0 || tipos and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE != 0)) {
			// El plazo mínimo entre cambios que pide la app (un cronómetro no habla cada décima)
			if (!pasaPlazo(n, SystemClock.uptimeMillis())) return
			// Una región viva educada de una ventana que no está delante no corta lo que se lee (NVDA 2019.2)
			val delante = raiz()?.windowId
			if (viva == android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE && delante != null && e.windowId != -1 && e.windowId != delante &&
				e.packageName != servicio.packageName) return
			val texto = compositor.describir(describir.elemento(n), verbosidad.copy(decirRol = false, decirPosicion = false))
			if (texto.isNotBlank() && filtroVivas.pasa("viva:${e.packageName}", texto, SystemClock.uptimeMillis())) {
				val urgente = viva == android.view.View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
				voz.decir(Enunciado(texto, if (urgente) Modo.INTERRUMPIR else Modo.ENCOLAR, Tipo.ANUNCIO))
			}
			return
		}
		// El elemento enfocado cambió de estado o de valor (un deslizador, una casilla pulsada con el teclado)
		val estado = AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION or (if (Build.VERSION.SDK_INT >= 36) AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED or AccessibilityEvent.CONTENT_CHANGE_TYPE_EXPANDED else 0)
		if (n == foco && tipos and estado != 0) {
			decirCambioEstado(n)
		}
		if (Build.VERSION.SDK_INT >= 34 && tipos and AccessibilityEvent.CONTENT_CHANGE_TYPE_ERROR != 0) {
			runCatching { n.refresh() }
			n.error?.toString()?.takeIf { it.isNotBlank() && filtro.pasa("error", it, SystemClock.uptimeMillis()) }?.let {
				sonidos.tocar(Sonido.ERROR)
				voz.decir(Enunciado(textos.getString(R.string.error_campo, it), Modo.ENCOLAR, Tipo.ANUNCIO))
			}
		}
		if (Build.VERSION.SDK_INT >= 28 && tipos and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE != 0) {
			n.paneTitle?.toString()?.takeIf { it.isNotBlank() && filtro.pasa("panel", it, SystemClock.uptimeMillis()) }?.let {
				voz.decir(Enunciado(it, Modo.ENCOLAR, Tipo.ANUNCIO))
			}
		}
	}

	/** Dice solo el estado o el valor nuevo del elemento enfocado. */
	private fun decirCambioEstado(n: AccessibilityNodeInfo) {
		runCatching { n.refresh() }
		val el = describir.elemento(n)
		val partes = compositor.estados(el) + listOfNotNull(compositor.valor(el, verbosidad))
		val texto = partes.joinToString(", ")
		if (texto.isBlank() || !filtro.pasa("estado", texto, SystemClock.uptimeMillis())) return
		// Si la app acaba de anunciar lo mismo (un deslizador que dice su valor), no se repite cortándolo
		if (yaDichoComoValor(texto)) return
		apuntarValor(texto)
		voz.decir(Enunciado(texto, Modo.INTERRUMPIR, Tipo.NAVEGACION))
	}

	/** Lo último dicho como valor o como anuncio, con su momento: el mismo valor llega por los dos sitios. */
	private var ultimoValor = ""
	private var momentoValor = 0L

	private fun normalizarValor(t: String): String = t.lowercase().filter { it.isLetterOrDigit() }

	private fun apuntarValor(t: String) {
		ultimoValor = normalizarValor(t)
		momentoValor = SystemClock.uptimeMillis()
	}

	private fun yaDichoComoValor(t: String): Boolean =
		SystemClock.uptimeMillis() - momentoValor < 1500 && ultimoValor.isNotEmpty() && normalizarValor(t) == ultimoValor

	private fun alDesplazar(e: AccessibilityEvent) {
		navegador.invalidar(e.windowId)
		descritos.clear()
		// Android guarda en caché los nodos (API 33): tras desplazar, los de esta lista ya no valen (TalkBack hace lo mismo)
		if (Build.VERSION.SDK_INT >= 33) e.source?.let { runCatching { servicio.clearCachedSubtree(it) } }
		val ahora = SystemClock.uptimeMillis()
		// Un tic por cada elemento que pasa (Backtalk 41), si se ha elegido
		if (ajustes.ticDesplazar && e.fromIndex >= 0) {
			val clave = e.windowId * 31 + (e.className?.hashCode() ?: 0)
			val antes = primeroVisible.put(clave, e.fromIndex)
			if (primeroVisible.size > 20) primeroVisible.clear()
			val pasos = antes?.let { kotlin.math.abs(e.fromIndex - it).coerceAtMost(8) } ?: 0
			if (pasos > 0) {
				repeat(pasos) { i -> manejador.postDelayed({ sonidos.tocar(Sonido.TOQUE, tono = 1.3f) }, i * 35L) }
				navegador.alDesplazar(e)
				return
			}
		}
		// Sin el sonido si se ha elegido así, o si es la lista que Tiresias adelanta sola (desplazamiento anticipado)
		if (ahora - ultimoSonidoDesplazar > 150 && ajustes.desplazarListas == Ajustes.DESPLAZAR_NORMAL && ahora > navegador.silencioDesplazarHasta) {
			ultimoSonidoDesplazar = ahora
			sonidos.tocar(Sonido.DESPLAZAR)
		}
		navegador.alDesplazar(e)
	}

	private fun alPulsar(e: AccessibilityEvent) {
		val n = e.source ?: return
		// Con la ayuda de otra persona, lo que pulsa (sin mover el foco de Tiresias, que no se usa)
		if (gestos.ayuda && !n.isCheckable && Nodos.marcableInterior(n) == null) {
			decirElemento(n, Modo.INTERRUMPIR, Sonido.ACTIVAR)
			return
		}
		// Casillas e interruptores: cómo han quedado (la app tarda un poco en cambiar el estado)
		// (también la fila de un ajuste con su interruptor dentro)
		if (n.isCheckable || Nodos.marcableInterior(n) != null) manejador.postDelayed({ decirCambioEstado(n) }, 150)
	}

	private fun alSeleccionar(e: AccessibilityEvent) {
		val n = e.source ?: return
		// Valor nuevo de una barra o un desplegable enfocado; los «seleccionado» falsos tras un foco no
		val ahora = SystemClock.uptimeMillis()
		if (n != foco || ahora - momentoDicho <= 300 || !(n.rangeInfo != null || Nodos.rol(n) == com.xebolax.tiresias.nucleo.Rol.DESPLEGABLE)) return
		// Los ecualizadores mandan «seleccionado» cada 10 ms: como mucho uno por segundo (Backtalk) y el plazo de la app
		if (ahora - ultimaSeleccion < 1000 || !pasaPlazo(n, ahora)) return
		ultimaSeleccion = ahora
		decirCambioEstado(n)
	}

	private var ultimaSeleccion = 0L

	/** Foco de entrada (teclado físico, Tab): se lee si no hay foco de accesibilidad que lo diga. */
	private fun alEnfocarEntrada(e: AccessibilityEvent) {
		val n = e.source ?: return
		if (n == foco) return
		val ahora = SystemClock.uptimeMillis()
		if (ahora - momentoDicho < 400) return
		// Si el foco de Tiresias sigue en algo que se ve de la misma ventana y no se ha pulsado ninguna tecla,
		// una app que mueve su foco de entrada por su cuenta (el lanzador en Recientes) no se lo lleva (Backtalk 206)
		val actual = foco
		if (actual != null && actual.windowId == n.windowId && ahora - momentoTecla > 1500 && Nodos.vivo(actual) && actual.isVisibleToUser) return
		val destino = Nodos.enfocable(n) ?: n
		// Un contenedor con el foco de entrada: sin modo táctil, Android se lo da a la lista o a la vista que se
		// desplaza al abrir una ventana; ahí no se lee entero (la ventana dice su título y, si toca, va a lo primero).
		// Si llega por una tecla (Tab hasta una lista), a su primer elemento
		if (esContenedor(destino)) {
			if (ahora - momentoTecla > 1500) return
			val raizVentana = runCatching { destino.window?.root }.getOrNull() ?: raiz() ?: return
			val primero = navegador.orden(raizVentana).firstOrNull { it != destino && Nodos.dentroDe(it, destino) } ?: return
			enfocar(primero, Sonido.FOCO)
			return
		}
		enfocar(destino, Sonido.FOCO)
	}

	/** Una lista, una cuadrícula o una vista que se desplaza, con cosas dentro. */
	private fun esContenedor(n: AccessibilityNodeInfo): Boolean = n.childCount > 0 &&
		(n.isScrollable || n.collectionInfo != null || n.className?.toString()?.substringAfterLast('.') in CONTENEDORES)

	/** Si el móvil está bloqueado (la pantalla de bloqueo delante). */
	fun bloqueado(): Boolean = servicio.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

	private fun alAnunciar(e: AccessibilityEvent) {
		if (ajustes.callarBloqueo && bloqueado()) return
		if (calladoAlEncender(e.packageName)) return
		if (scriptsListos) e.source?.let { if (scripts.silenciado(it)) return }
		val texto = e.text.joinToString(" ").trim()
		if (texto.isBlank() || !filtro.pasa("anuncio:${e.packageName}", texto, SystemClock.uptimeMillis())) return
		// El valor nuevo de un deslizador que Tiresias ya ha dicho (o que va a decir: se apunta)
		if (yaDichoComoValor(texto)) return
		apuntarValor(texto)
		voz.decir(Enunciado(texto, Modo.ENCOLAR, Tipo.ANUNCIO))
		if (brailleListo) braille.mensaje(texto)
	}

	private fun alNotificar(e: AccessibilityEvent) {
		val datos = e.parcelableData
		val paquete = e.packageName?.toString().orEmpty()
		if (datos is Notification) {
			avisos.alNotificar(paquete, datos, e)
			return
		}
		// Un Toast
		val texto = e.text.joinToString(" ").trim()
		if (texto.isNotBlank() && filtro.pasa("toast:$paquete", texto, SystemClock.uptimeMillis())) voz.decir(Enunciado(texto, Modo.ENCOLAR, Tipo.ANUNCIO))
	}

	val avisos = Avisos(this)

	/** Si el modo No molestar está activo (para no leer notificaciones solas). */
	fun noMolestar(): Boolean = runCatching {
		servicio.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
	}.getOrDefault(false)

	fun olvidarCaches() {
		descritos.clear()
		navegador.invalidarTodo()
	}

	/** Si hay un menú o un diálogo de Tiresias abierto (lo pone el servicio). */
	var hayVentanaPropia: () -> Boolean = { false }

	/** La raíz del menú o el diálogo de Tiresias (no la cortina, que no tiene nada dentro). */
	private fun raizPropia(): AccessibilityNodeInfo? = runCatching {
		servicio.windows.asSequence()
			.filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
			.mapNotNull { it.root }
			.firstOrNull { it.packageName == servicio.packageName && it.childCount > 0 }
	}.getOrNull()
		// La lista de ventanas de Android llega un instante tarde: mientras, la raíz que se vio al abrirla
		?: raizVentanaPropia?.takeIf { Nodos.vivo(it) }

	/** La raíz del último menú o diálogo de Tiresias, tal como llegó en su aviso de ventana nueva. */
	@Volatile private var raizVentanaPropia: AccessibilityNodeInfo? = null

	private fun subirARaiz(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
		var actual = n
		repeat(30) { actual = runCatching { actual.parent }.getOrNull() ?: return actual }
		return actual
	}

	/** La raíz de la ventana en la que está el foco (o la activa). */
	fun raiz(): AccessibilityNodeInfo? {
		// Con un menú o un diálogo de Tiresias abierto, se navega en él aunque el foco siga en la app
		// de debajo (va encima sin ser la ventana activa, y en un móvil lento tarda en abrirse)
		if (hayVentanaPropia()) raizPropia()?.let { r -> if (foco?.windowId != r.windowId) return r }
		foco?.let { f -> runCatching { f.window?.root }.getOrNull()?.let { return it } }
		return runCatching { servicio.rootInActiveWindow }.getOrNull()
	}

	/** Dice un texto del propio Tiresias (avisos, valores de ajustes). */
	fun decir(texto: String, modo: Modo = Modo.INTERRUMPIR, tipo: Tipo = Tipo.SISTEMA) {
		voz.decir(Enunciado(texto, modo, tipo))
		// Las respuestas de Tiresias también en la línea braille (con «primero braille», solo ahí)
		if (brailleListo && tipo == Tipo.SISTEMA) braille.mensaje(texto)
	}

	fun decir(id: Int, vararg args: Any, modo: Modo = Modo.INTERRUMPIR) = decir(textos.getString(id, *args), modo)

	/** Título de la ventana activa (para «leer el título»). */
	fun tituloActivo(): String {
		val activa = runCatching { servicio.windows.firstOrNull { it.isActive } }.getOrNull()
		return activa?.title?.toString()?.takeIf { it.isNotBlank() } ?: tituloVentana
	}

	/** Cuándo se pulsó la última tecla (para no seguir el foco de entrada que mueve una app sola). */
	@Volatile var momentoTecla = 0L

	/** El primer elemento visible de cada lista que se desplaza (para el tic por elemento). */
	private val primeroVisible = HashMap<Int, Int>()

	/** El último elemento al que se llegó explorando con el dedo (para activar al levantarlo). */
	private var ultimoExplorado: AccessibilityNodeInfo? = null

	companion object {
		/** Cada cuánto se mira el elemento vigilado, lo mínimo entre dos avisos y lo que se espera fuera de su app. */
		private const val VIGILAR_CADA_MS = 1500L
		private const val VIGILAR_ENTRE_AVISOS_MS = 2000L
		private const val VIGILAR_FUERA_MS = 30_000L

		/** Un toque así de largo debería haber explorado algo; tres seguidos sin nada en este tiempo, atasco. */
		private const val TOQUE_LARGO_MS = 450L
		/** Lo que se espera a que la app rellene un elemento enfocado vacío antes de decirlo con lo que tenga. */
		private const val ESPERA_RELLENO_MS = 300L
		/** Lo que dura el silencio de SystemUI al encender con el móvil bloqueado (Backtalk 312). */
		private const val SILENCIO_ENCENDER_MS = 3000L
		/** Lo que como mucho se cuenta un dedo en la pantalla para las teclas de volumen. */
		private const val DEDO_MAXIMO_MS = 30_000L
		/** La mitad del lado de la zona de paso que se abre para un toque de Tiresias, y lo que se espera a que Android la ponga. */
		private const val PASO_TOQUE_PX = 24
		private const val ESPERA_PASO_TOQUE_MS = 60L
		/** Lo que puede tardar Android en empezar el toque de un gesto propio. */
		private const val MARGEN_GESTO_PROPIO_MS = 500L
		/** Lo que puede tardar un teclado en volver a salir para no anunciarlo otra vez. */
		private const val TECLADO_REHECHO_MS = 600L
		/** Lo que dura la protección del foco tras moverse el usuario. */
		private const val PROTEGER_FOCO_MS = 600L
		/** Lo que tiene que estar quieta una ventana nueva para poner el foco en ella, y lo que se espera de más como mucho. */
		private const val ESTABLE_QUIETA_MS = 120L
		private const val ESTABLE_MAXIMO_MS = 400L
		private const val TOQUES_ATASCO = 3
		private const val RACHA_ATASCO_MS = 30_000L

		/** Un toque que dura menos que esto es un toque (no explorar con el dedo). */
		private const val TOQUE_CORTO_MS = 350L
		/** Lo que se espera tras un toque por si llega el segundo de un doble toque. */
		private const val ESPERA_DOBLE_TOQUE_MS = 400L

		/** Los enlaces que abren el resto de un mensaje («Leer más», «Ver más», «Read more»...). */
		private val LEER_MAS = Regex("(?i)(leer|ver|mostrar|read|see|show|lire|mehr|leggi) ?(más|mas|more|la suite|anzeigen|altro)\\.*…?")

		/** Las vistas que solo contienen y desplazan (sin modo táctil reciben el foco de entrada al abrir una ventana). */
		private val CONTENEDORES = setOf("ScrollView", "NestedScrollView", "HorizontalScrollView", "ListView", "GridView", "RecyclerView", "ViewPager", "ViewPager2")
		private const val PAQUETE_SISTEMA = "com.android.systemui"
		/** Lo que se espera a su notificación antes de anunciar una ventana del sistema que nadie ha pedido. */
		private const val ESPERA_EMERGENTE_MS = 500L
		private val BOTONES_BARRA = setOf("back", "home", "recent_apps", "menu", "ime_switcher", "accessibility_button")
		private const val ETIQUETA = "Lector"
	}
}
