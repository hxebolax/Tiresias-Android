package com.xebolax.tiresias.escucha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.recursos.Recursos
import com.xebolax.tiresias.voz.Sonido
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.text.Normalizer
import java.util.Locale

/**
 * Escuchar al usuario: órdenes por voz y dictado.
 *
 * - **Sin Internet ni Play Services**: Vosk con su modelo pequeño de español
 *   (39 MB, se descarga en Ajustes > Recursos).
 * - Si el modelo no está, el reconocedor del sistema (el de Google u otro), si el
 *   móvil tiene alguno.
 *
 * Al empezar suena un aviso y Tiresias se calla; al terminar, otro aviso. Hace
 * falta el permiso del micrófono, que se pide en la pantalla de Ajustes (un
 * servicio de accesibilidad no puede pedir permisos).
 */
class Escucha(private val lector: Lector, private val alAccion: (Accion) -> Unit, private val alBuscar: (String) -> Unit) {
	private val servicio = lector.servicio
	private val principal = Handler(Looper.getMainLooper())
	private val t get() = lector.textos
	private var modelo: Model? = null
	private var vosk: SpeechService? = null
	private var sistema: SpeechRecognizer? = null
	@Volatile private var escuchando = false

	private enum class Para { ORDENES, DICTADO, TECLADO }

	/** A quién se da lo dictado desde el teclado braille. */
	@Volatile private var alDictadoTeclado: ((String) -> Unit)? = null

	fun ordenes(): Boolean = empezar(Para.ORDENES)

	/**
	 * Dictado para el teclado braille (Can Talk): lo reconocido se da a
	 * [alTexto] (en el hilo del lector), que lo escribe con el teclado. Si ya se
	 * está escuchando, para. Devuelve si se ha empezado a escuchar.
	 */
	fun dictarEn(alTexto: (String) -> Unit): Boolean {
		if (escuchando) {
			parar()
			return false
		}
		alDictadoTeclado = alTexto
		return empezar(Para.TECLADO)
	}

	fun dictar(): Boolean {
		if (lector.foco?.isEditable != true) {
			lector.decir(R.string.escucha_sin_campo)
			return false
		}
		return empezar(Para.DICTADO)
	}

	private fun empezar(para: Para): Boolean {
		if (escuchando) {
			parar()
			return true
		}
		if (servicio.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			lector.decir(R.string.escucha_sin_permiso)
			return false
		}
		lector.continua.parar()
		lector.voz.callar()
		val campo = lector.foco
		return when {
			Recursos.instalado(servicio, Recursos.VOSK_ES) -> escucharVosk(para, campo)
			SpeechRecognizer.isRecognitionAvailable(servicio) -> {
				principal.post { escucharSistema(para, campo) }
				true
			}
			else -> {
				lector.decir(R.string.escucha_sin_motor)
				false
			}
		}
	}

	private fun escucharVosk(para: Para, campo: AccessibilityNodeInfo?): Boolean {
		try {
			val m = modelo ?: Model(Recursos.carpeta(servicio, Recursos.VOSK_ES).path).also { modelo = it }
			val r = Recognizer(m, 16000f)
			val s = SpeechService(r, 16000f)
			vosk = s
			escuchando = true
			lector.sonidos.tocar(Sonido.ENCENDIDO)
			s.startListening(object : org.vosk.android.RecognitionListener {
				override fun onPartialResult(hypothesis: String?) = Unit
				override fun onResult(hypothesis: String?) {
					val texto = runCatching { JSONObject(hypothesis.orEmpty()).optString("text") }.getOrDefault("")
					if (texto.isBlank()) return
					parar()
					lector.manejador.post { atender(para, texto, campo) }
				}
				override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)
				override fun onError(exception: Exception?) {
					Registro.w(ETIQUETA, "Vosk falló", exception)
					parar()
					lector.manejador.post { lector.decir(R.string.escucha_error) }
				}
				override fun onTimeout() {
					parar()
				}
			}, TIEMPO_MAXIMO_MS)
			return true
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo empezar a escuchar con Vosk", e)
			escuchando = false
			lector.decir(R.string.escucha_error)
			return false
		}
	}

	private fun escucharSistema(para: Para, campo: AccessibilityNodeInfo?) {
		try {
			val r = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(servicio)) {
				SpeechRecognizer.createOnDeviceSpeechRecognizer(servicio)
			} else SpeechRecognizer.createSpeechRecognizer(servicio)
			sistema = r
			escuchando = true
			r.setRecognitionListener(object : RecognitionListener {
				override fun onReadyForSpeech(params: Bundle?) = lector.sonidos.tocar(Sonido.ENCENDIDO)
				override fun onBeginningOfSpeech() = Unit
				override fun onRmsChanged(rmsdB: Float) = Unit
				override fun onBufferReceived(buffer: ByteArray?) = Unit
				override fun onEndOfSpeech() = Unit
				override fun onError(error: Int) {
					parar()
					lector.manejador.post { lector.decir(R.string.escucha_no_entendido) }
				}
				override fun onResults(results: Bundle?) {
					val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
					parar()
					if (texto.isNotBlank()) lector.manejador.post { atender(para, texto, campo) }
				}
				override fun onPartialResults(partialResults: Bundle?) = Unit
				override fun onEvent(eventType: Int, params: Bundle?) = Unit
			})
			val idioma = Idioma.efectivo(servicio).toLanguageTag()
			r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
				.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
				.putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
				.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
				.apply { if (para != Para.ORDENES && lector.ajustes.dictadoSinCensura) sinCensura(this) })
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo usar el reconocedor del sistema", e)
			parar()
			lector.manejador.post { lector.decir(R.string.escucha_error) }
		}
	}

	fun parar() {
		if (!escuchando) return
		escuchando = false
		runCatching { vosk?.stop(); vosk?.shutdown() }
		vosk = null
		principal.post {
			runCatching { sistema?.destroy() }
			sistema = null
		}
		lector.sonidos.tocar(Sonido.APAGADO)
	}

	/**
	 * Pide al reconocedor que no tape las palabras malsonantes (Can Talk). Son
	 * extras que solo entiende el reconocedor de Google; los demás los ignoran.
	 * Vosk no tapa nada.
	 */
	private fun sinCensura(i: Intent) {
		i.putExtra("android.speech.extra.MASK_OFFENSIVE_WORDS", false)
		i.putExtra("com.google.recognition.extra.MASK_OFFENSIVE_WORDS", false)
		i.putExtra("android.speech.extra.PROFANITY_FILTER", false)
		i.putExtra("profanity_filter", false)
		i.putExtra("maskOffensiveWords", false)
	}

	private fun atender(para: Para, texto: String, campo: AccessibilityNodeInfo?) {
		if (para == Para.TECLADO) {
			alDictadoTeclado?.invoke(texto)
			alDictadoTeclado = null
			return
		}
		if (para == Para.DICTADO) {
			escribir(campo, texto)
			return
		}
		val orden = normalizar(texto)
		// «Abrir <app>»: busca la app por su nombre
		Regex("^(abre|abrir|abreme|lanza|open)\\s+(.+)$").find(orden)?.let { m ->
			abrirApp(m.groupValues[2])
			return
		}
		Regex("^(busca|buscar|search)\\s+(.+)$").find(orden)?.let { m ->
			lector.decir(t.getString(R.string.escucha_buscando, m.groupValues[2]))
			alBuscar(m.groupValues[2])
			return
		}
		val accion = ORDENES.entries.firstOrNull { (frases, a) -> a !in com.xebolax.tiresias.servicio.PuenteEscritorio.ACCIONES_OCULTAS && frases.any { orden == it || orden.startsWith("$it ") } }?.value
		if (accion != null) alAccion(accion) else lector.decir(t.getString(R.string.escucha_no_se, texto))
	}

	private fun escribir(campo: AccessibilityNodeInfo?, texto: String) {
		val c = campo ?: lector.foco ?: return
		runCatching { c.refresh() }
		val actual = c.text?.toString().orEmpty().takeIf { c.textSelectionStart >= 0 } ?: ""
		val cursor = c.textSelectionEnd.coerceIn(0, actual.length)
		val separador = if (cursor > 0 && !actual[cursor - 1].isWhitespace()) " " else ""
		val nuevo = actual.substring(0, cursor) + separador + texto + actual.substring(cursor)
		val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nuevo) }
		if (c.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
			val fin = cursor + separador.length + texto.length
			c.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, fin)
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, fin)
			})
			lector.decir(texto)
		} else lector.decir(R.string.escucha_no_escrito)
	}

	/** Abre la app que más se parezca a [nombre]; devuelve si la ha encontrado (también para la asistencia remota). */
	fun abrirAppPorNombre(nombre: String): Boolean {
		abrirApp(nombre)
		return encontrada
	}

	/** Si la última búsqueda de [abrirApp] encontró una app. */
	private var encontrada = false

	private fun abrirApp(nombre: String) {
		encontrada = false
		val pm = servicio.packageManager
		val elegida = com.xebolax.tiresias.base.Lanzables.buscar(servicio, nombre)
		if (elegida == null) {
			lector.decir(t.getString(R.string.escucha_app_no, nombre))
			return
		}
		encontrada = true
		val intent = pm.getLaunchIntentForPackage(elegida.paquete)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
		runCatching { servicio.startActivity(intent) }.onFailure { Registro.w(ETIQUETA, "No se pudo abrir la app", it) }
	}

	private fun normalizar(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

	fun cerrar() {
		parar()
		runCatching { modelo?.close() }
		modelo = null
	}

	companion object {
		private const val ETIQUETA = "Escucha"
		private const val TIEMPO_MAXIMO_MS = 8000

		/** Frases (sin acentos ni mayúsculas) de cada orden; en español y en inglés. */
		private val ORDENES: Map<List<String>, Accion> = mapOf(
			listOf("inicio", "pantalla de inicio", "home") to Accion.INICIO,
			listOf("escritorio del sistema", "inicio del sistema", "system home") to Accion.ESCRITORIO_SISTEMA,
			listOf("atras", "volver", "back") to Accion.ATRAS,
			listOf("notificaciones", "notifications") to Accion.NOTIFICACIONES,
			listOf("recientes", "aplicaciones recientes", "recent apps") to Accion.RECIENTES,
			listOf("ajustes rapidos", "quick settings") to Accion.AJUSTES_RAPIDOS,
			listOf("leer pantalla", "leer todo", "lee la pantalla", "read screen") to Accion.LEER_DESDE_ARRIBA,
			listOf("leer desde aqui", "sigue leyendo", "read from here") to Accion.LEER_DESDE_AQUI,
			listOf("hora", "que hora es", "bateria", "estado", "time", "battery") to Accion.ESTADO_SISTEMA,
			listOf("mas rapido", "mas deprisa", "faster") to Accion.VELOCIDAD_MAS,
			listOf("mas lento", "mas despacio", "slower") to Accion.VELOCIDAD_MENOS,
			listOf("describir pantalla", "describe la pantalla", "que hay en pantalla", "describe screen") to Accion.PANTALLA_IA,
			listOf("describir", "describe", "que es esto") to Accion.DESCRIBIR_IA,
			listOf("texto de la pantalla", "reconocer texto", "ocr") to Accion.OCR_PANTALLA,
			listOf("cortina", "pantalla negra", "curtain") to Accion.CORTINA,
			listOf("repetir", "repite", "repeat") to Accion.REPETIR,
			listOf("copiar", "copy") to Accion.COPIAR,
			listOf("pegar", "paste") to Accion.PEGAR,
			listOf("lista de elementos", "elementos") to Accion.LISTA_ELEMENTOS,
			listOf("historial de notificaciones", "notificaciones recientes") to Accion.HISTORIAL_NOTIFICACIONES,
			listOf("ajustes de tiresias", "configuracion de tiresias", "tiresias settings") to Accion.AJUSTES_TIRESIAS,
			listOf("bloquear", "bloquear pantalla", "lock") to Accion.BLOQUEAR,
			listOf("captura", "captura de pantalla", "screenshot") to Accion.CAPTURA,
			listOf("silencio", "callate", "voz", "mute") to Accion.ALTERNAR_VOZ,
			listOf("dictar", "dictado", "dictate") to Accion.DICTADO,
		)
	}
}
