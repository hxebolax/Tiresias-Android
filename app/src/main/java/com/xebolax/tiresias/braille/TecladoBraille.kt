package com.xebolax.tiresias.braille

import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.braille.BuscadorEmojis
import com.xebolax.tiresias.nucleo.braille.CategoriaEmoji
import com.xebolax.tiresias.nucleo.braille.Emojis
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo
import kotlin.math.abs

/**
 * Teclado braille en pantalla de Tiresias (método de entrada del sistema).
 *
 * Se sostiene el móvil con la pantalla hacia fuera, o sobre la mesa, y se
 * apoyan los dedos: los puntos 1, 2 y 3 a un lado y 4, 5 y 6 al otro. Al
 * levantar todos los dedos se escribe la combinación (signografía española de
 * la ONCE, grado 1: letras con tilde, ñ, ü, números con el signo de número y
 * mayúsculas con el signo de mayúscula). Los puntos se pueden calibrar tocando
 * uno a uno donde caen los dedos ([SuperficieBraille]).
 *
 * Los gestos los decide [GestosTeclado]: de fábrica, un dedo a la izquierda
 * borra y a la derecha escribe un espacio; con dos dedos hacia abajo, intro (o
 * la acción del campo: buscar, enviar); con dos dedos hacia arriba, cambia al
 * teclado anterior; un dedo hacia arriba abre el menú del teclado (emojis,
 * portapapeles, dictado, calibrar...). Los menús y listas del teclado se
 * recorren siempre igual: un dedo a los lados, el siguiente o el anterior; un
 * dedo arriba o abajo, el grupo; dos dedos hacia abajo, elegir; dos dedos hacia
 * arriba, salir; dos dedos a la derecha, favorito.
 *
 * La vista explica lo que pasa con anuncios de accesibilidad para que el
 * lector (Tiresias u otro) diga cada carácter escrito.
 *
 * Con «Puntos según cómo sujetas el móvil», el sensor de gravedad decide si
 * van en espejo: tumbado sobre la mesa (pantalla hacia ti), en espejo; de pie
 * con la pantalla hacia fuera, normales. Como el teclado braille de Gboard
 * (modos «sobre la mesa» y «pantalla hacia fuera»; Backtalk 12, 20 y 73).
 */
class TecladoBraille : InputMethodService(), SensorEventListener, OyenteSuperficie {
	/** La superficie dentro de la ventana del teclado: solo se usa sin Tiresias (con otro lector o sin ninguno). */
	private var vista: SuperficieBraille? = null
	/** El teclado puesto encima de todo por Tiresias ([ModoTecladoBraille]), mientras está a la vista. */
	private var encima: ModoTecladoBraille? = null
	/** La superficie que recibe los dedos ahora: la de encima, o la de la ventana del teclado. */
	private val activa: SuperficieBraille? get() = encima?.vista ?: vista
	private var mayuscula = false
	private var numero = false
	/** Lo que dice el sensor: en espejo (sobre la mesa) o no; null mientras no se sabe. */
	@Volatile private var espejoSensor: Boolean? = null
	private var sensorPuesto = false
	private val principal = Handler(Looper.getMainLooper())
	private var gestos: Map<String, AccionTeclado> = GestosTeclado.POR_DEFECTO
	/** Las acciones del lector puestas en gestos del teclado (para tenerlas a mano). */
	private var gestosLector: Map<String, com.xebolax.tiresias.nucleo.Accion> = emptyMap()
	/** El menú o la lista abierta en el teclado (emojis, portapapeles, sugerencias), o null escribiendo. */
	private var lista: ListaTeclado? = null
	/** Recorrer y corregir lo escrito (cursor, selección, menú Edición). */
	private val edicion = EdicionBraille(this)
	/** La búsqueda de emojis en marcha, o null. */
	private var busqueda: Busqueda? = null

	override fun onCreateInputView(): View = SuperficieBraille(this, this, textos()).also { v ->
		vista = v
		// Si alguna vez el teclado queda a la vista sin la superficie de encima (a esta ventana no le llegan los
		// dedos con la exploración táctil), un doble toque sobre él la vuelve a poner
		v.setOnClickListener { if (encima?.puesta != true) ponerEncima() }
	}

	/**
	 * Siempre a la vista, también con un teclado físico conectado: Android esconde los teclados en pantalla en
	 * cuanto hay uno, y el braille no salía (en el emulador, con su teclado de PC, ni aparecía). Como TalkBack.
	 */
	override fun onEvaluateInputViewShown(): Boolean {
		super.onEvaluateInputViewShown()
		return true
	}

	/** La ventana del teclado vuelve a verse sin empezar otro campo (al volver a la app): la superficie, otra vez encima. */
	override fun onWindowShown() {
		super.onWindowShown()
		if (isInputViewShown && encima?.puesta != true) ponerEncima()
	}

	/** Nunca a pantalla completa con el campo de texto de Android en horizontal: la superficie ya ocupa todo. */
	override fun onEvaluateFullscreenMode(): Boolean = false

	override fun onCreate() {
		super.onCreate()
		// Lo que copian las apps, para el historial del portapapeles (solo el teclado elegido puede leerlo desde Android 10)
		runCatching { getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(alCopiar) }
			.onFailure { Registro.w(ETIQUETA, "No se pudo vigilar el portapapeles", it) }
	}

	override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
		super.onStartInputView(info, restarting)
		mayuscula = false
		numero = false
		lista = null
		busqueda = null
		if (!restarting) edicion.reiniciar()
		consultaAvisada = false
		gestos = GestosTeclado.leer(Ajustes(this).gestosTecladoBraille)
		gestosLector = GestosTeclado.leerLector(Ajustes(this).gestosTecladoBraille)
		ponerSensor(Ajustes(this).brailleSensor)
		// Con Tiresias, la superficie va encima de todo y el aviso lo dice al ponerse; sin él, en la ventana del teclado
		if (!ponerEncima()) anunciar(avisoListo())
	}

	/** Lo que se dice al aparecer el teclado: qué es, dónde están los puntos y cómo se sale. */
	fun avisoListo(): String = textos().getString(R.string.braille_teclado_listo)

	/**
	 * Pone la superficie encima de todo con Tiresias activo (si ya está puesta, la deja). Devuelve si está o
	 * se va a poner: si no, el teclado sigue en su ventana como antes.
	 */
	private fun ponerEncima(): Boolean {
		val lector = ServicioTiresias.instancia?.lectorActual ?: return false
		encima?.let { if (it.puesta) return true }
		val m = ModoTecladoBraille(lector, this)
		encima = m
		lector.superficie.abrir(m)
		return true
	}

	/** Quita la superficie de encima (el teclado se esconde, o sale la lista de teclados del sistema). */
	private fun quitarEncima() {
		val m = encima ?: return
		encima = null
		m.quitadaPorElTeclado = true
		m.cerrar()
	}

	/**
	 * La superficie de encima se ha quitado. Si no ha sido el teclado, se esconde: sin la superficie, a la
	 * ventana del teclado no le llegan los dedos. Si ni siquiera se pudo poner, el teclado sigue en su ventana.
	 */
	fun alQuitarSuperficie(m: ModoTecladoBraille, llegoAPonerse: Boolean) {
		if (encima === m) encima = null
		if (m.quitadaPorElTeclado) return
		if (!llegoAPonerse) {
			vista?.anunciarAccesible(avisoListo())
			return
		}
		if (isInputViewShown) requestHideSelf(0)
	}

	override fun onFinishInputView(finishingInput: Boolean) {
		quitarEncima()
		ponerSensor(false)
		lista = null
		busqueda = null
		if (dictando) ServicioTiresias.instancia?.lectorActual?.escucha?.parar()
		dictando = false
		super.onFinishInputView(finishingInput)
	}

	override fun onDestroy() {
		quitarEncima()
		ponerSensor(false)
		runCatching { getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(alCopiar) }
			.onFailure { Registro.w(ETIQUETA, "No se pudo soltar el portapapeles", it) }
		super.onDestroy()
	}

	private fun ponerSensor(poner: Boolean) {
		val sm = getSystemService(SensorManager::class.java) ?: return
		if (poner && !sensorPuesto) {
			val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
			espejoSensor = null
			sensorPuesto = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
		} else if (!poner && sensorPuesto) {
			sm.unregisterListener(this)
			sensorPuesto = false
			espejoSensor = null
		}
	}

	/** La orientación bloqueada: el sensor ya no cambia los puntos (tres dedos hacia arriba). */
	private var bloqueado = false

	/** Bloquea o desbloquea la orientación que ha detectado el sensor. */
	fun alternarBloqueo() {
		if (!sensorPuesto) {
			anunciar(textos().getString(R.string.braille_sin_sensor))
			return
		}
		bloqueado = !bloqueado
		anunciar(textos().getString(if (bloqueado) R.string.braille_orientacion_bloqueada else R.string.braille_orientacion_libre))
	}

	override fun onSensorChanged(e: SensorEvent) {
		if (e.values.size < 3 || bloqueado) return
		val anterior = espejoSensor
		val ahora = espejoPorGravedad(e.values[2], SensorManager.GRAVITY_EARTH, anterior) ?: return
		if (ahora == anterior) return
		espejoSensor = ahora
		// Al cambiar de postura (no la primera vez, que ya la dice el aviso de listo), se dice cómo quedan los puntos
		if (anterior != null) anunciar(textos().getString(if (ahora) R.string.braille_sobre_mesa else R.string.braille_pantalla_fuera))
	}

	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

	/** Si los puntos van en espejo ahora: lo que diga el sensor, si está puesto y lo sabe; si no, el ajuste. */
	override fun espejo(): Boolean = espejoSensor?.takeIf { sensorPuesto } ?: Ajustes(this).brailleEspejo

	internal fun textos(): Context = Idioma.envolver(this)

	/**
	 * Dice [texto] cortando lo anterior. Con Tiresias, por su voz directamente, como el teclado braille de
	 * TalkBack: los anuncios de accesibilidad se ponían a la cola y el filtro de repetidos se comía la segunda
	 * de dos letras iguales («ll», «rr»). Sin Tiresias, con un anuncio para el lector que haya.
	 */
	override fun anunciar(texto: String) {
		if (texto.isEmpty() || callarVuelta) return
		val lector = ServicioTiresias.instancia?.lectorActual
		if (lector != null) lector.manejador.post { lector.decir(texto, Modo.INTERRUMPIR, Tipo.ESCRITURA) }
		else activa?.anunciarAccesible(texto)
	}

	/** El eco de lo escrito: cada carácter por su nombre («punto», «coma»), y en una contraseña, solo «punto». */
	private fun decirEscrito(escrito: String) {
		val lector = ServicioTiresias.instancia?.lectorActual
		if (lector == null) {
			anunciar(escrito)
			return
		}
		if (esContrasena() && !mostrarContrasenas()) {
			anunciar(textos().getString(R.string.edicion_punto))
			return
		}
		lector.manejador.post { lector.voz.decir(Enunciado(escrito, Modo.INTERRUMPIR, Tipo.ESCRITURA, deletrear = escrito.length == 1)) }
	}

	/** Un carácter por su nombre («a», «coma», «espacio»); en una contraseña sin mostrar, «punto». */
	internal fun decirCaracter(c: String) {
		if (c == " " || c == "\n") {
			anunciar(nombreDe(c))
			return
		}
		decirEscrito(c)
	}

	/** Si en este campo no se dice lo escrito (una contraseña y Android no las muestra). */
	internal fun ocultarContrasena(): Boolean = esContrasena() && !mostrarContrasenas()

	private fun esContrasena(): Boolean {
		val tipo = currentInputEditorInfo?.inputType ?: return false
		val variacion = tipo and InputType.TYPE_MASK_VARIATION
		return when (tipo and InputType.TYPE_MASK_CLASS) {
			InputType.TYPE_CLASS_TEXT -> variacion == InputType.TYPE_TEXT_VARIATION_PASSWORD || variacion == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
				variacion == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
			InputType.TYPE_CLASS_NUMBER -> variacion == InputType.TYPE_NUMBER_VARIATION_PASSWORD
			else -> false
		}
	}

	private fun mostrarContrasenas(): Boolean = try {
		android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.TEXT_SHOW_PASSWORD, 1) == 1
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo mirar si se dicen las contraseñas", e)
		false
	}

	// --- Lo que llega de la superficie ----------------------------------------------------------

	override fun alApoyar() {
		val a = Ajustes(this)
		if (a.brailleVibrarPuntos) activa?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
		if (a.brailleSonarPuntos) sonar(Sonido.TECLA)
	}

	override fun alGesto(g: GestoBraille) {
		busqueda?.let { b ->
			gestoEnBusqueda(b, g)
			return
		}
		lista?.let { l ->
			gestoEnLista(l, g)
			return
		}
		GestosTeclado.accionLector(gestosLector, g)?.let {
			hacerDelLector(it)
			return
		}
		hacer(GestosTeclado.accion(gestos, g))
	}

	/** Llega una combinación de puntos (bits 0 a 5 = puntos 1 a 6). */
	override fun alPuntos(puntos: Int) {
		// Buscando un emoji, las letras son de la búsqueda
		busqueda?.let { b ->
			letraEnBusqueda(b, puntos)
			return
		}
		// Escribir sale de la lista abierta (las sugerencias se rehacen con lo nuevo)
		if (lista != null) {
			lista = null
			anunciar(textos().getString(R.string.braille_lista_cerrada))
		}
		val ic = currentInputConnection ?: return
		when (puntos) {
			SIGNO_NUMERO -> {
				numero = true
				anunciar(textos().getString(R.string.braille_numero))
				return
			}
			SIGNO_MAYUSCULA -> {
				mayuscula = true
				anunciar(textos().getString(R.string.braille_mayuscula))
				return
			}
		}
		val c: String? = if (numero) NUMEROS[puntos] ?: run { numero = false; LETRAS[puntos] } else LETRAS[puntos]
		if (c == null) {
			anunciar(textos().getString(R.string.braille_desconocido, describir(puntos)))
			return
		}
		val escrito = if (mayuscula || (c[0].isLetter() && mayusculaAutomatica())) c.uppercase() else c
		mayuscula = false
		ic.commitText(escrito, 1)
		palabra.append(escrito)
		// El eco: la letra (lo normal), nada, o la palabra al terminarla con un espacio
		if (Ajustes(this).brailleEco == ECO_LETRAS) decirEscrito(escrito)
		mirarMenciones()
	}

	/**
	 * Mayúscula automática (Can Talk): la que pide el campo en el cursor, al
	 * empezar o tras un punto, si el campo la quiere (los de frases sí; los de
	 * correo o contraseña no). Apagada de fábrica.
	 */
	private fun mayusculaAutomatica(): Boolean {
		if (!Ajustes(this).brailleMayusculaAuto) return false
		val tipo = currentInputEditorInfo?.inputType ?: return false
		if (tipo and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
		return runCatching { (currentInputConnection?.getCursorCapsMode(tipo) ?: 0) != 0 }.getOrDefault(false)
	}

	// --- Acciones del teclado -------------------------------------------------------------------

	private fun hacer(a: AccionTeclado) {
		when (a) {
			AccionTeclado.NINGUNA -> Unit
			AccionTeclado.BORRAR -> borrar()
			AccionTeclado.BORRAR_PALABRA -> borrarPalabra()
			AccionTeclado.ESPACIO -> espacio()
			AccionTeclado.INTRO -> intro()
			AccionTeclado.CAMBIAR_TECLADO -> cambiarTeclado()
			AccionTeclado.BLOQUEAR_ORIENTACION -> alternarBloqueo()
			AccionTeclado.MENU -> abrirMenu()
			AccionTeclado.DICTADO -> dictar()
			AccionTeclado.EMOJIS -> abrirEmojis()
			AccionTeclado.PORTAPAPELES -> abrirPortapapeles()
			AccionTeclado.SUGERENCIAS -> abrirSugerencias()
			AccionTeclado.LEER_CAMPO -> leerCampo()
			AccionTeclado.CALIBRAR -> activa?.calibrar { } ?: Unit
			AccionTeclado.QUITAR_CALIBRACION -> {
				CalibracionBraille.quitar(Ajustes(this))
				anunciar(textos().getString(R.string.braille_calibracion_quitada))
			}
			AccionTeclado.BUSCAR_EMOJI -> abrirBusqueda()
			AccionTeclado.MENU_LECTOR -> abrirMenuLector()
			AccionTeclado.EDICION -> abrirEdicion()
			AccionTeclado.CURSOR_ATRAS -> edicion.mover(false)
			AccionTeclado.CURSOR_ADELANTE -> edicion.mover(true)
			AccionTeclado.CAMBIAR_UNIDAD -> edicion.cambiarUnidad()
			AccionTeclado.SELECCIONAR, AccionTeclado.SELECCIONAR_TODO, AccionTeclado.COPIAR, AccionTeclado.CORTAR, AccionTeclado.PEGAR,
			AccionTeclado.DESHACER, AccionTeclado.REHACER, AccionTeclado.IR_PRINCIPIO, AccionTeclado.IR_FINAL, AccionTeclado.LEER_SELECCION -> edicion.hacer(a)
		}
	}

	fun borrar() {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
		if (antes.isEmpty()) {
			anunciar(textos().getString(R.string.braille_nada_que_borrar))
			return
		}
		ic.deleteSurroundingText(1, 0)
		if (palabra.isNotEmpty()) palabra.setLength(palabra.length - 1)
		sonarTecla(Sonido.TECLA_BORRAR)
		val dicho = if (esContrasena() && !mostrarContrasenas()) textos().getString(R.string.edicion_punto) else nombreDe(antes)
		anunciar(textos().getString(R.string.edicion_borrado, dicho))
	}

	/** Borra la palabra de antes del cursor (y los espacios que la siguen) y la dice. */
	private fun borrarPalabra() {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
		if (antes.isEmpty()) {
			anunciar(textos().getString(R.string.braille_nada_que_borrar))
			return
		}
		val sinEspacios = antes.trimEnd()
		val inicio = sinEspacios.indexOfLast { it.isWhitespace() } + 1
		val borrada = sinEspacios.substring(inicio)
		ic.deleteSurroundingText(antes.length - inicio, 0)
		palabra.clear()
		sonarTecla(Sonido.TECLA_BORRAR)
		anunciar(textos().getString(R.string.edicion_borrado, borrada))
	}

	/** Lo escrito desde el último espacio (para el eco de palabras). */
	private val palabra = StringBuilder()

	fun espacio() {
		numero = false
		currentInputConnection?.commitText(" ", 1)
		sonarTecla(Sonido.TECLA_ESPACIO)
		val eco = Ajustes(this).brailleEco
		when {
			eco == ECO_PALABRAS && palabra.isNotEmpty() -> anunciar(palabra.toString())
			// Un número recién escrito se dice entero («dos mil veintiséis», no «2 0 2 6»)
			eco == ECO_LETRAS && esNumero(palabra) && !ocultarContrasena() -> anunciar(palabra.toString())
			eco != ECO_NADA -> anunciar(textos().getString(R.string.voz_espacio))
		}
		palabra.clear()
	}

	fun intro() {
		val ic = currentInputConnection ?: return
		sonarTecla(Sonido.TECLA_INTRO)
		val accion = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
		if (accion != EditorInfo.IME_ACTION_NONE && accion != EditorInfo.IME_ACTION_UNSPECIFIED) {
			ic.performEditorAction(accion)
			return
		}
		// En una app de mensajes, su campo no tiene acción del editor: Intro pulsa su botón Enviar (SandFly)
		if (Ajustes(this).brailleIntroEnvia && enviarConBoton()) return
		ic.commitText("\n", 1)
	}

	/** Pulsa el botón Enviar junto al campo, si es de una app de mensajes y se encuentra. */
	private fun enviarConBoton(): Boolean {
		val paquete = currentInputEditorInfo?.packageName ?: return false
		if (paquete !in APPS_MENSAJES) return false
		val servicio = ServicioTiresias.instancia ?: return false
		return try {
			val raiz = servicio.rootInActiveWindow ?: return false
			val campo = raiz.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
			val boton = BuscarEnviar.buscar(raiz, campo) ?: return false
			boton.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo pulsar Enviar", e)
			false
		}
	}

	fun cambiarTeclado() {
		val imm = getSystemService(InputMethodManager::class.java)
		// Volver al teclado anterior solo existe desde Android 9; antes, la lista de teclados
		val cambiado = Build.VERSION.SDK_INT >= 28 && switchToPreviousInputMethod()
		if (!cambiado) {
			// La lista de teclados del sistema quedaría debajo de la superficie, sin poder tocarla; y el teclado se
			// esconde, para que al cerrar la lista no quede a la vista sin superficie (sin que le lleguen los dedos)
			quitarEncima()
			requestHideSelf(0)
			imm?.showInputMethodPicker()
		}
	}

	/** Un carácter dicho por su nombre si es un espacio o un salto de línea (para «borrado espacio»). */
	internal fun nombreDe(c: String): String = when (c) {
		" " -> textos().getString(R.string.voz_espacio)
		"\n" -> textos().getString(R.string.braille_accion_intro)
		else -> c
	}

	/** Dice todo lo escrito en el campo (como mucho unos miles de caracteres). */
	private fun leerCampo() {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(MAXIMO_LEER, 0)?.toString().orEmpty()
		val despues = ic.getTextAfterCursor(MAXIMO_LEER, 0)?.toString().orEmpty()
		val todo = antes + despues
		anunciar(if (todo.isBlank()) textos().getString(R.string.braille_campo_vacio) else todo)
	}

	private fun esNumero(s: CharSequence): Boolean = s.isNotEmpty() && s.any { it.isDigit() } && s.all { it.isDigit() || it == '.' || it == ',' }

	private fun describir(p: Int): String = (0 until 6).filter { p and (1 shl it) != 0 }.joinToString(" ") { (it + 1).toString() }

	// --- Sonidos ----------------------------------------------------------------------------------

	/** El sonido de espacio, borrar o Intro, solo con «Sonidos al escribir» puesto. */
	private fun sonarTecla(s: Sonido) {
		if (Ajustes(this).brailleSonarTeclas) sonar(s)
	}

	/**
	 * Suena un sonido del teclado con su volumen: los de Android (los de
	 * siempre; no suenan si el sistema tiene los sonidos al tocar quitados) o los
	 * del tema de Tiresias, por el canal y la salida del lector (Can Talk).
	 */
	private fun sonar(s: Sonido) {
		val a = Ajustes(this)
		val volumen = a.brailleVolumenSonidos / 100f
		if (a.brailleEstiloSonidos == SONIDOS_TIRESIAS) {
			val sonidos = ServicioTiresias.instancia?.lectorActual?.sonidos
			if (sonidos != null) {
				sonidos.tocar(s, volumen = volumen)
				return
			}
		}
		val efecto = when (s) {
			Sonido.TECLA_ESPACIO -> AudioManager.FX_KEYPRESS_SPACEBAR
			Sonido.TECLA_BORRAR -> AudioManager.FX_KEYPRESS_DELETE
			Sonido.TECLA_INTRO -> AudioManager.FX_KEYPRESS_RETURN
			else -> AudioManager.FX_KEYPRESS_STANDARD
		}
		getSystemService(AudioManager::class.java)?.playSoundEffect(efecto, volumen)
	}

	// --- Dictado ----------------------------------------------------------------------------------

	private var dictando = false

	/**
	 * Dictado por voz desde el teclado (Can Talk), con el reconocedor de
	 * Tiresias (Vosk sin Internet o el del sistema). Lo dictado se escribe en el
	 * cursor y se dice. Otra vez el mismo gesto lo para.
	 */
	private fun dictar() {
		val escucha = ServicioTiresias.instancia?.lectorActual?.escucha
		if (escucha == null) {
			anunciar(textos().getString(R.string.braille_dictado_sin_tiresias))
			return
		}
		dictando = escucha.dictarEn { texto ->
			principal.post {
				dictando = false
				escribirDictado(texto)
			}
		}
	}

	private fun escribirDictado(texto: String) {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
		val separador = if (antes.isNotEmpty() && !antes[0].isWhitespace()) " " else ""
		val conMayuscula = if (mayusculaAutomatica()) texto.replaceFirstChar { it.titlecase() } else texto
		ic.commitText(separador + conMayuscula, 1)
		palabra.clear()
		anunciar(conMayuscula)
	}

	// --- Portapapeles ---------------------------------------------------------------------------

	private val alCopiar = ClipboardManager.OnPrimaryClipChangedListener {
		try {
			val cm = getSystemService(ClipboardManager::class.java) ?: return@OnPrimaryClipChangedListener
			val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
			val extras = clip.description?.extras
			val sensible = extras?.getBoolean(if (Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE") == true
			if (sensible || clip.itemCount == 0) return@OnPrimaryClipChangedListener
			val texto = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
			if (texto.isNotBlank()) ServicioTiresias.instancia?.lectorActual?.edicion?.apuntarCopiado(texto)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo apuntar lo copiado", e)
		}
	}

	// --- Listas del teclado ---------------------------------------------------------------------

	/** Un grupo de la lista: su nombre y sus elementos (lo que se dice y lo que se usa). */
	private class Grupo(val nombre: String, val elementos: List<Pair<String, String>>)

	private class ListaTeclado(
		val grupos: List<Grupo>,
		/** Elegir: devuelve si la lista sigue abierta (los emojis, para escribir varios). */
		val alElegir: (String) -> Boolean,
		/** Favorito: lo que se dice al ponerlo o quitarlo; null si esta lista no tiene favoritos. */
		val alFavorito: ((String) -> String)? = null,
		/** Guardar en una de las categorías propias (tres dedos a la derecha); null si no se puede. */
		val alCategoria: ((String) -> Unit)? = null,
		/** Al salir con dos dedos hacia arriba (volver a lo de antes); null: se cierra sin más. */
		val alSalir: (() -> Unit)? = null,
	) {
		var g = 0
		var i = -1
	}

	private fun abrirLista(
		titulo: String, grupos: List<Grupo>, alElegir: (String) -> Boolean, alFavorito: ((String) -> String)? = null,
		alCategoria: ((String) -> Unit)? = null, alSalir: (() -> Unit)? = null,
	) {
		val llenos = grupos.filter { it.elementos.isNotEmpty() }
		if (llenos.isEmpty()) {
			anunciar(textos().getString(R.string.braille_lista_vacia, titulo))
			return
		}
		lista = ListaTeclado(llenos, alElegir, alFavorito, alCategoria, alSalir)
		val primero = llenos[0]
		val grupo = if (llenos.size > 1) ". " + textos().getString(R.string.braille_lista_grupo, primero.nombre, primero.elementos.size) else ""
		anunciar(textos().getString(R.string.braille_lista_abierta, titulo) + grupo + ". " + textos().getString(R.string.braille_lista_ayuda))
	}

	private fun gestoEnLista(l: ListaTeclado, g: GestoBraille) {
		val t = textos()
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.DERECHA, 1 to DireccionBraille.IZQUIERDA -> {
				val elementos = l.grupos[l.g].elementos
				val paso = if (g.direccion == DireccionBraille.DERECHA) 1 else -1
				l.i = when {
					l.i < 0 -> if (paso > 0) 0 else elementos.size - 1
					else -> (l.i + paso + elementos.size) % elementos.size
				}
				anunciar(t.getString(R.string.braille_lista_elemento, elementos[l.i].first, l.i + 1, elementos.size))
			}
			1 to DireccionBraille.ABAJO, 1 to DireccionBraille.ARRIBA -> {
				if (l.grupos.size < 2) {
					anunciar(t.getString(R.string.braille_lista_un_grupo))
					return
				}
				val paso = if (g.direccion == DireccionBraille.ABAJO) 1 else -1
				l.g = (l.g + paso + l.grupos.size) % l.grupos.size
				l.i = -1
				val grupo = l.grupos[l.g]
				anunciar(t.getString(R.string.braille_lista_grupo, grupo.nombre, grupo.elementos.size))
			}
			2 to DireccionBraille.ABAJO -> {
				val elegido = l.grupos[l.g].elementos.getOrNull(l.i)
				if (elegido == null) {
					anunciar(t.getString(R.string.braille_lista_elige_antes))
					return
				}
				if (!l.alElegir(elegido.second) && lista === l) lista = null
			}
			2 to DireccionBraille.ARRIBA -> {
				lista = null
				val salir = l.alSalir
				if (salir != null) salir() else anunciar(t.getString(R.string.braille_lista_cerrada))
			}
			3 to DireccionBraille.DERECHA -> {
				val categoria = l.alCategoria
				val elegido = l.grupos[l.g].elementos.getOrNull(l.i)
				when {
					categoria == null -> anunciar(t.getString(R.string.braille_lista_ayuda))
					elegido == null -> anunciar(t.getString(R.string.braille_lista_elige_antes))
					else -> categoria(elegido.second)
				}
			}
			2 to DireccionBraille.DERECHA -> {
				val favorito = l.alFavorito
				val elegido = l.grupos[l.g].elementos.getOrNull(l.i)
				when {
					favorito == null -> anunciar(t.getString(R.string.braille_lista_sin_favoritos))
					elegido == null -> anunciar(t.getString(R.string.braille_lista_elige_antes))
					else -> anunciar(favorito(elegido.second))
				}
			}
			else -> anunciar(t.getString(R.string.braille_lista_ayuda))
		}
	}

	private fun abrirMenu() {
		val t = textos()
		// Las elegidas en Ajustes, Braille, sin lo que ahora no viene a cuento
		val calibrado = CalibracionBraille.hay(Ajustes(this@TecladoBraille))
		val conSugerencias = sugerencias().isNotEmpty()
		val opciones = Ajustes(this).menuTecladoBraille.filter { a ->
			when (a) {
				AccionTeclado.SUGERENCIAS -> conSugerencias
				AccionTeclado.QUITAR_CALIBRACION -> calibrado
				else -> true
			}
		}
		abrirLista(t.getString(R.string.braille_accion_menu), listOf(Grupo("", opciones.map { it.nombre(t) to it.name })), { nombre ->
			lista = null
			AccionTeclado.entries.firstOrNull { it.name == nombre }?.let { hacer(it) }
			// La acción puede abrir otra lista (emojis): no se cierra aquí
			lista != null
		})
	}

	private fun abrirEmojis() {
		val t = textos()
		val a = Ajustes(this)
		val favoritos = a.emojisFavoritos
		val grupos = buildList {
			if (favoritos.isNotEmpty()) add(Grupo(t.getString(R.string.braille_favoritos), favoritos.map { nombreEmoji(it) to it }))
			for (c in EmojisPropios.categorias(a)) add(Grupo(c.nombre, c.emojis.map { nombreEmoji(it) to it }))
			for (c in CategoriaEmoji.entries) add(Grupo(nombreCategoria(t, c), Emojis.de(c).map { nombreEmoji(it) to it }))
		}
		abrirLista(t.getString(R.string.braille_accion_emojis), grupos, { emoji ->
			escribirEmoji(emoji)
			true
		}, { emoji -> alternarFavorito(emoji) }, { emoji ->
			val anterior = lista
			abrirCategoriasPara(emoji) {
				lista = anterior
				anunciar(t.getString(R.string.braille_vuelta_emojis))
			}
		})
	}

	/**
	 * Las categorías propias para guardar [emoji] (o quitarlo, si ya está en una): al elegir o al salir se vuelve
	 * a lo de antes con [volver] (la lista de emojis o la búsqueda).
	 */
	private fun abrirCategoriasPara(emoji: String, volver: () -> Unit) {
		val t = textos()
		val categorias = EmojisPropios.categorias(Ajustes(this))
		if (categorias.isEmpty()) {
			anunciar(t.getString(R.string.braille_sin_categorias))
			return
		}
		val elementos = categorias.map { c -> (if (emoji in c.emojis) t.getString(R.string.braille_categoria_ya_esta, c.nombre) else c.nombre) to c.nombre }
		abrirLista(t.getString(R.string.braille_categoria_titulo, nombreEmoji(emoji)), listOf(Grupo("", elementos)), { categoria ->
			val puesto = EmojisPropios.alternar(Ajustes(this), categoria, emoji)
			anunciar(t.getString(if (puesto) R.string.braille_categoria_puesto else R.string.braille_categoria_quitado, categoria))
			// Lo de antes, sin decirlo: lo dicho es lo que ha pasado
			lista = null
			principal.postDelayed({ volverSinDecir(volver) }, 0)
			true
		}, alSalir = volver)
	}

	/** Vuelve a lo de antes (la lista de emojis o la búsqueda) sin que hable: ya se ha dicho lo que ha pasado. */
	private var callarVuelta = false

	private fun volverSinDecir(volver: () -> Unit) {
		callarVuelta = true
		try {
			volver()
		} finally {
			callarVuelta = false
		}
	}

	/** Escribe [emoji] en el cursor y lo dice por su nombre. */
	private fun escribirEmoji(emoji: String) {
		currentInputConnection?.commitText(emoji, 1)
		anunciar(textos().getString(R.string.braille_escrito, nombreEmoji(emoji)))
	}

	/** Pone o quita [emoji] de los favoritos (los primeros al explorar) y dice lo que ha pasado. */
	private fun alternarFavorito(emoji: String): String {
		val puesto = EmojisPropios.alternarFavorito(Ajustes(this), emoji)
		return textos().getString(if (puesto) R.string.braille_favorito_puesto else R.string.braille_favorito_quitado)
	}

	// --- Base de emojis y búsqueda ------------------------------------------------------------

	/** La base de emojis del idioma de Tiresias (`emojis.tsv`). */
	private fun base(): BuscadorEmojis = BaseEmojis.buscador(this)

	/** El nombre de un emoji en la base («cara sonriendo»); si no está, el propio emoji. */
	private fun nombreEmoji(emoji: String): String = BaseEmojis.nombre(this, emoji)

	/** La búsqueda: lo escrito (palabras separadas por espacios) y lo encontrado. */
	private class Busqueda(val consulta: StringBuilder = StringBuilder(), var resultados: List<BuscadorEmojis.Emoji> = emptyList(), var i: Int = 0)

	private fun abrirBusqueda() {
		lista = null
		base()
		busqueda = Busqueda()
		anunciar(textos().getString(R.string.braille_busqueda_abierta))
	}

	private fun letraEnBusqueda(b: Busqueda, puntos: Int) {
		val t = textos()
		val c = LETRAS[puntos]?.takeIf { it[0].isLetter() } ?: NUMEROS[puntos]?.takeIf { numero && it[0].isDigit() }
		if (puntos == SIGNO_NUMERO) {
			numero = true
			anunciar(t.getString(R.string.braille_numero))
			return
		}
		if (c == null) {
			anunciar(t.getString(R.string.braille_desconocido, describir(puntos)))
			return
		}
		val prueba = b.consulta.toString() + c
		val encontrados = base().buscar(prueba)
		if (encontrados.isEmpty()) {
			// La letra que deja sin ningún emoji no se queda (como en SandFly): se sigue con lo de antes
			anunciar(t.getString(R.string.braille_busqueda_sin, prueba))
			return
		}
		b.consulta.append(c)
		b.resultados = encontrados
		b.i = 0
		anunciar(c + ". " + t.resources.getQuantityString(R.plurals.braille_busqueda_hay, encontrados.size, encontrados.size, encontrados[0].nombre))
	}

	private fun gestoEnBusqueda(b: Busqueda, g: GestoBraille) {
		val t = textos()
		val actual = b.resultados.getOrNull(b.i)
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.DERECHA, 1 to DireccionBraille.IZQUIERDA -> {
				if (b.resultados.isEmpty()) {
					anunciar(t.getString(R.string.braille_busqueda_escribe))
					return
				}
				val paso = if (g.direccion == DireccionBraille.DERECHA) 1 else -1
				b.i = (b.i + paso + b.resultados.size) % b.resultados.size
				anunciar(t.getString(R.string.braille_lista_elemento, b.resultados[b.i].nombre, b.i + 1, b.resultados.size))
			}
			1 to DireccionBraille.ABAJO -> {
				// Otra palabra del nombre
				if (b.consulta.isNotEmpty() && b.consulta.last() != ' ') b.consulta.append(' ')
				numero = false
				anunciar(t.getString(R.string.voz_espacio))
			}
			1 to DireccionBraille.ARRIBA -> anunciar(
				if (b.consulta.isBlank()) t.getString(R.string.braille_busqueda_escribe)
				else t.resources.getQuantityString(R.plurals.braille_busqueda_estado, b.resultados.size, b.consulta.toString().trim(), b.resultados.size)
			)
			2 to DireccionBraille.ABAJO -> if (actual == null) anunciar(t.getString(R.string.braille_busqueda_escribe)) else escribirEmoji(actual.emoji)
			2 to DireccionBraille.DERECHA -> if (actual == null) anunciar(t.getString(R.string.braille_busqueda_escribe)) else anunciar(alternarFavorito(actual.emoji))
			2 to DireccionBraille.IZQUIERDA -> {
				if (b.consulta.isEmpty()) {
					anunciar(t.getString(R.string.braille_nada_que_borrar))
					return
				}
				val quitado = b.consulta.last().toString()
				b.consulta.setLength(b.consulta.length - 1)
				b.resultados = base().buscar(b.consulta.toString())
				b.i = 0
				val borrado = t.getString(R.string.edicion_borrado, nombreDe(quitado))
				anunciar(if (b.resultados.isEmpty()) borrado else borrado + ". " + t.resources.getQuantityString(R.plurals.braille_busqueda_hay, b.resultados.size, b.resultados.size, b.resultados[0].nombre))
			}
			2 to DireccionBraille.ARRIBA -> {
				busqueda = null
				anunciar(t.getString(R.string.braille_busqueda_cerrada))
			}
			3 to DireccionBraille.DERECHA -> {
				if (actual == null) {
					anunciar(t.getString(R.string.braille_busqueda_escribe))
					return
				}
				// Mientras se elige la categoría, la búsqueda espera (las letras no van a ella)
				busqueda = null
				abrirCategoriasPara(actual.emoji) {
					busqueda = b
					anunciar(t.getString(R.string.braille_vuelta_busqueda))
				}
				if (lista == null) busqueda = b
			}
			else -> anunciar(t.getString(R.string.braille_busqueda_ayuda))
		}
	}

	// --- Menú del lector -------------------------------------------------------------------------

	/** El menú del lector (tres dedos hacia arriba): las acciones de Tiresias elegidas en Ajustes, Braille. */
	private fun abrirMenuLector() {
		val t = textos()
		if (ServicioTiresias.instancia?.lectorActual == null) {
			anunciar(t.getString(R.string.braille_lector_sin_tiresias))
			return
		}
		val acciones = Ajustes(this).menuLectorTeclado
		abrirLista(t.getString(R.string.braille_accion_menu_lector), listOf(Grupo("", acciones.map { nombreAccionLector(it) to it.name })), { elegida ->
			lista = null
			com.xebolax.tiresias.nucleo.Accion.entries.firstOrNull { it.name == elegida }?.let { hacerDelLector(it) }
			false
		})
	}

	/** Hace una acción del lector (del menú del lector o de un gesto del teclado); lo que dice, lo dice Tiresias. */
	private fun hacerDelLector(a: com.xebolax.tiresias.nucleo.Accion) {
		val s = ServicioTiresias.instancia
		if (s?.lectorActual == null) {
			anunciar(textos().getString(R.string.braille_lector_sin_tiresias))
			return
		}
		s.ejecutarDesdeTeclado(a)
	}

	private fun nombreAccionLector(a: com.xebolax.tiresias.nucleo.Accion): String {
		val t = textos()
		val id = t.resources.getIdentifier("accion_" + a.name.lowercase(), "string", packageName)
		return if (id != 0) t.getString(id) else a.name.lowercase().replace('_', ' ')
	}

	// --- Menú Edición ---------------------------------------------------------------------------

	private fun abrirEdicion() {
		val t = textos()
		val acciones = listOf(
			AccionTeclado.SELECCIONAR, AccionTeclado.SELECCIONAR_TODO, AccionTeclado.COPIAR, AccionTeclado.CORTAR, AccionTeclado.PEGAR,
			AccionTeclado.DESHACER, AccionTeclado.REHACER, AccionTeclado.IR_PRINCIPIO, AccionTeclado.IR_FINAL, AccionTeclado.LEER_SELECCION,
			AccionTeclado.CAMBIAR_UNIDAD,
		)
		// «Terminar de seleccionar» si ya se está seleccionando (nunca ofrecer lo que ya está hecho)
		fun nombre(a: AccionTeclado) = if (a == AccionTeclado.SELECCIONAR) t.getString(if (edicion.seleccionando) R.string.braille_seleccion_terminar else R.string.braille_seleccion_empezar) else a.nombre(t)
		abrirLista(t.getString(R.string.braille_accion_edicion), listOf(Grupo("", acciones.map { nombre(it) to it.name })), { elegida ->
			lista = null
			AccionTeclado.entries.firstOrNull { it.name == elegida }?.let { hacer(it) }
			false
		})
	}

	private fun abrirPortapapeles() {
		val t = textos()
		val edicion = ServicioTiresias.instancia?.lectorActual?.edicion
		if (edicion == null) {
			anunciar(t.getString(R.string.braille_portapapeles_sin_tiresias))
			return
		}
		fun corto(s: String) = s.replace('\n', ' ').let { if (it.length > 90) it.take(90) + "…" else it }
		val grupos = listOf(
			Grupo(t.getString(R.string.braille_portapapeles_general), edicion.copiado().map { corto(it) to it }),
			Grupo(t.getString(R.string.braille_favoritos), edicion.copiadoFavorito().map { corto(it) to it }),
		)
		abrirLista(t.getString(R.string.braille_accion_portapapeles), grupos, { texto ->
			currentInputConnection?.commitText(texto, 1)
			anunciar(t.getString(R.string.braille_pegado, corto(texto)))
			false
		}, alFavorito = { texto ->
			val puesto = edicion.alternarFavorito(texto)
			t.getString(if (puesto) R.string.braille_favorito_puesto else R.string.braille_favorito_quitado)
		})
	}

	private fun paqueteCampo(): String? = currentInputEditorInfo?.packageName

	private fun sugerencias(): List<Menciones.Sugerencia> {
		val antes = runCatching { currentInputConnection?.getTextBeforeCursor(60, 0) }.getOrNull()
		if (!Menciones.hayConsulta(antes)) return emptyList()
		val raiz = runCatching { ServicioTiresias.instancia?.rootInActiveWindow }.getOrNull()
		return Menciones.buscar(raiz, paqueteCampo())
	}

	private fun abrirSugerencias() {
		val t = textos()
		val lista = sugerencias()
		if (lista.isEmpty()) {
			anunciar(t.getString(R.string.braille_sin_sugerencias))
			return
		}
		abrirLista(t.getString(R.string.braille_accion_sugerencias), listOf(Grupo("", lista.map { it.nombre to it.nombre })), { nombre ->
			val raiz = runCatching { ServicioTiresias.instancia?.rootInActiveWindow }.getOrNull()
			if (Menciones.elegir(raiz, paqueteCampo(), nombre)) anunciar(t.getString(R.string.braille_sugerencia_elegida, nombre))
			else anunciar(t.getString(R.string.braille_sugerencia_no))
			false
		})
	}

	/** Si se ha avisado ya de las sugerencias de esta mención. */
	private var consultaAvisada = false

	/** Al escribir «@» en WhatsApp, avisa (una vez por mención) de que hay sugerencias, cuando la app las pone. */
	private fun mirarMenciones() {
		val paquete = paqueteCampo() ?: return
		if (paquete != "com.whatsapp" && paquete != "com.whatsapp.w4b") return
		val antes = runCatching { currentInputConnection?.getTextBeforeCursor(60, 0) }.getOrNull()
		if (!Menciones.hayConsulta(antes)) {
			consultaAvisada = false
			return
		}
		if (consultaAvisada) return
		principal.postDelayed({
			if (consultaAvisada || lista != null) return@postDelayed
			val n = sugerencias().size
			if (n > 0) {
				consultaAvisada = true
				anunciar(textos().resources.getQuantityString(R.plurals.braille_hay_sugerencias, n, n))
			}
		}, ESPERA_SUGERENCIAS_MS)
	}

	companion object {
		private const val ETIQUETA = "TecladoBraille"
		const val ECO_LETRAS = "letras"
		const val ECO_PALABRAS = "palabras"
		const val ECO_NADA = "nada"
		const val SONIDOS_ANDROID = "android"
		const val SONIDOS_TIRESIAS = "tiresias"
		private const val MAXIMO_LEER = 4000
		private const val MAXIMO_FAVORITOS = 60
		private const val ESPERA_SUGERENCIAS_MS = 450L

		fun nombreCategoria(c: Context, cat: CategoriaEmoji): String = c.getString(when (cat) {
			CategoriaEmoji.CARAS -> R.string.emoji_caras
			CategoriaEmoji.GESTOS -> R.string.emoji_gestos
			CategoriaEmoji.PERSONAS -> R.string.emoji_personas
			CategoriaEmoji.CORAZONES -> R.string.emoji_corazones
			CategoriaEmoji.ANIMALES -> R.string.emoji_animales
			CategoriaEmoji.COMIDA -> R.string.emoji_comida
			CategoriaEmoji.ACTIVIDADES -> R.string.emoji_actividades
			CategoriaEmoji.VIAJES -> R.string.emoji_viajes
			CategoriaEmoji.OBJETOS -> R.string.emoji_objetos
			CategoriaEmoji.SIMBOLOS -> R.string.emoji_simbolos
			CategoriaEmoji.BANDERAS -> R.string.emoji_banderas
		})

		/**
		 * Si los puntos van en espejo según la gravedad en el eje de la pantalla
		 * ([z], de [g]): tumbado (más del 70 %), sí; de pie (menos del 40 %), no; en
		 * medio, lo que había ([anterior]), para no cambiar a cada temblor.
		 */
		fun espejoPorGravedad(z: Float, g: Float, anterior: Boolean?): Boolean? {
			val parte = abs(z) / g
			return when {
				parte > 0.7f -> true
				parte < 0.4f -> false
				else -> anterior
			}
		}
		private fun p(vararg n: Int): Int = n.fold(0) { acc, d -> acc or (1 shl (d - 1)) }

		private val SIGNO_NUMERO = p(3, 4, 5, 6)
		private val SIGNO_MAYUSCULA = p(4, 6)

		/** Signografía española (ONCE), grado 1. */
		private val LETRAS: Map<Int, String> = mapOf(
			p(1) to "a", p(1, 2) to "b", p(1, 4) to "c", p(1, 4, 5) to "d", p(1, 5) to "e", p(1, 2, 4) to "f",
			p(1, 2, 4, 5) to "g", p(1, 2, 5) to "h", p(2, 4) to "i", p(2, 4, 5) to "j", p(1, 3) to "k", p(1, 2, 3) to "l",
			p(1, 3, 4) to "m", p(1, 3, 4, 5) to "n", p(1, 2, 4, 5, 6) to "ñ", p(1, 3, 5) to "o", p(1, 2, 3, 4) to "p",
			p(1, 2, 3, 4, 5) to "q", p(1, 2, 3, 5) to "r", p(2, 3, 4) to "s", p(2, 3, 4, 5) to "t", p(1, 3, 6) to "u",
			p(1, 2, 3, 6) to "v", p(2, 4, 5, 6) to "w", p(1, 3, 4, 6) to "x", p(1, 3, 4, 5, 6) to "y", p(1, 3, 5, 6) to "z",
			p(1, 2, 3, 5, 6) to "á", p(2, 3, 4, 6) to "é", p(3, 4) to "í", p(3, 4, 6) to "ó", p(2, 3, 4, 5, 6) to "ú", p(1, 2, 5, 6) to "ü",
			p(3) to ".", p(2) to ",", p(2, 3) to ";", p(2, 5) to ":", p(2, 6) to "?", p(2, 3, 5) to "!", p(3, 6) to "-",
			p(1, 2, 6) to "(", p(3, 4, 5) to ")", p(2, 3, 6) to "\"", p(5) to "'", p(2, 3, 5, 6) to "=", p(3, 5) to "*",
			p(2, 5, 6) to "/", p(4, 5, 6) to "_",
		)

		/** Las letras braille (sin números) para quien escribe con una superficie braille: la navegación web y la búsqueda. */
		fun letra(puntos: Int): String? = LETRAS[puntos]

		/** Las apps de mensajes en las que Intro pulsa Enviar. */
		private val APPS_MENSAJES = setOf(
			"com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.google.android.apps.messaging",
			"com.samsung.android.messaging", "com.facebook.orca", "com.instagram.android", "com.discord", "org.telegram.plus",
		)

		private val NUMEROS: Map<Int, String> = mapOf(
			p(1) to "1", p(1, 2) to "2", p(1, 4) to "3", p(1, 4, 5) to "4", p(1, 5) to "5",
			p(1, 2, 4) to "6", p(1, 2, 4, 5) to "7", p(1, 2, 5) to "8", p(2, 4) to "9", p(2, 4, 5) to "0",
			p(3) to ".", p(2) to ",",
		)

		/** Los números braille (con el signo de número delante). */
		fun numero(puntos: Int): String? = NUMEROS[puntos]

		/** La celda de una letra en minúscula o un signo (el tutorial dice cómo se escribe), o null si no la hay. */
		fun celdaDe(c: String): Int? = LETRAS.entries.firstOrNull { it.value == c }?.key

		/** La celda de una cifra (detrás del signo de número). */
		fun celdaDeCifra(c: String): Int? = NUMEROS.entries.firstOrNull { it.value == c }?.key
		val SIGNO_DE_NUMERO: Int get() = SIGNO_NUMERO
		val SIGNO_DE_MAYUSCULA: Int get() = SIGNO_MAYUSCULA
	}
}
