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
import com.xebolax.tiresias.nucleo.braille.CategoriaEmoji
import com.xebolax.tiresias.nucleo.braille.Emojis
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Sonido
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
	private var vista: SuperficieBraille? = null
	private var mayuscula = false
	private var numero = false
	/** Lo que dice el sensor: en espejo (sobre la mesa) o no; null mientras no se sabe. */
	@Volatile private var espejoSensor: Boolean? = null
	private var sensorPuesto = false
	private val principal = Handler(Looper.getMainLooper())
	private var gestos: Map<String, AccionTeclado> = GestosTeclado.POR_DEFECTO
	/** El menú o la lista abierta en el teclado (emojis, portapapeles, sugerencias), o null escribiendo. */
	private var lista: ListaTeclado? = null

	override fun onCreateInputView(): View = SuperficieBraille(this, this, textos()).also { vista = it }

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
		consultaAvisada = false
		gestos = GestosTeclado.leer(Ajustes(this).gestosTecladoBraille)
		anunciar(textos().getString(R.string.braille_teclado_listo))
		ponerSensor(Ajustes(this).brailleSensor)
	}

	override fun onFinishInputView(finishingInput: Boolean) {
		ponerSensor(false)
		lista = null
		if (dictando) ServicioTiresias.instancia?.lectorActual?.escucha?.parar()
		dictando = false
		super.onFinishInputView(finishingInput)
	}

	override fun onDestroy() {
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

	private fun textos(): Context = Idioma.envolver(this)

	override fun anunciar(texto: String) {
		vista?.anunciarAccesible(texto)
	}

	// --- Lo que llega de la superficie ----------------------------------------------------------

	override fun alApoyar() {
		val a = Ajustes(this)
		if (a.brailleVibrarPuntos) vista?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
		if (a.brailleSonarPuntos) sonar(Sonido.TECLA)
	}

	override fun alGesto(g: GestoBraille) {
		lista?.let { l ->
			gestoEnLista(l, g)
			return
		}
		hacer(GestosTeclado.accion(gestos, g))
	}

	/** Llega una combinación de puntos (bits 0 a 5 = puntos 1 a 6). */
	override fun alPuntos(puntos: Int) {
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
		if (Ajustes(this).brailleEco == ECO_LETRAS) anunciar(escrito)
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
			AccionTeclado.CALIBRAR -> vista?.calibrar { } ?: Unit
			AccionTeclado.QUITAR_CALIBRACION -> {
				CalibracionBraille.quitar(Ajustes(this))
				anunciar(textos().getString(R.string.braille_calibracion_quitada))
			}
		}
	}

	fun borrar() {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
		ic.deleteSurroundingText(1, 0)
		if (palabra.isNotEmpty()) palabra.setLength(palabra.length - 1)
		sonarTecla(Sonido.TECLA_BORRAR)
		anunciar(textos().getString(R.string.edicion_borrado, antes.ifEmpty { "" }))
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
		if (!cambiado) imm?.showInputMethodPicker()
	}

	/** Dice todo lo escrito en el campo (como mucho unos miles de caracteres). */
	private fun leerCampo() {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(MAXIMO_LEER, 0)?.toString().orEmpty()
		val despues = ic.getTextAfterCursor(MAXIMO_LEER, 0)?.toString().orEmpty()
		val todo = antes + despues
		anunciar(if (todo.isBlank()) textos().getString(R.string.braille_campo_vacio) else todo)
	}

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
	) {
		var g = 0
		var i = -1
	}

	private fun abrirLista(titulo: String, grupos: List<Grupo>, alElegir: (String) -> Boolean, alFavorito: ((String) -> String)? = null) {
		val llenos = grupos.filter { it.elementos.isNotEmpty() }
		if (llenos.isEmpty()) {
			anunciar(textos().getString(R.string.braille_lista_vacia, titulo))
			return
		}
		lista = ListaTeclado(llenos, alElegir, alFavorito)
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
				anunciar(t.getString(R.string.braille_lista_cerrada))
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
		val opciones = buildList {
			add(AccionTeclado.EMOJIS)
			add(AccionTeclado.PORTAPAPELES)
			if (sugerencias().isNotEmpty()) add(AccionTeclado.SUGERENCIAS)
			add(AccionTeclado.DICTADO)
			add(AccionTeclado.LEER_CAMPO)
			add(AccionTeclado.BORRAR_PALABRA)
			add(AccionTeclado.CALIBRAR)
			if (CalibracionBraille.hay(Ajustes(this@TecladoBraille))) add(AccionTeclado.QUITAR_CALIBRACION)
			add(AccionTeclado.BLOQUEAR_ORIENTACION)
			add(AccionTeclado.CAMBIAR_TECLADO)
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
			if (favoritos.isNotEmpty()) add(Grupo(t.getString(R.string.braille_favoritos), favoritos.map { it to it }))
			for (c in CategoriaEmoji.entries) add(Grupo(nombreCategoria(t, c), Emojis.de(c).map { it to it }))
		}
		abrirLista(t.getString(R.string.braille_accion_emojis), grupos, { emoji ->
			currentInputConnection?.commitText(emoji, 1)
			anunciar(t.getString(R.string.braille_escrito, emoji))
			true
		}) { emoji ->
			val ahora = a.emojisFavoritos.toMutableList()
			val quitar = emoji in ahora
			if (quitar) ahora.remove(emoji) else ahora.add(0, emoji)
			a.emojisFavoritos = ahora.take(MAXIMO_FAVORITOS)
			t.getString(if (quitar) R.string.braille_favorito_quitado else R.string.braille_favorito_puesto)
		}
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
		}) { texto ->
			val puesto = edicion.alternarFavorito(texto)
			t.getString(if (puesto) R.string.braille_favorito_puesto else R.string.braille_favorito_quitado)
		}
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
		val SIGNO_DE_NUMERO: Int get() = SIGNO_NUMERO
		val SIGNO_DE_MAYUSCULA: Int get() = SIGNO_MAYUSCULA
	}
}
