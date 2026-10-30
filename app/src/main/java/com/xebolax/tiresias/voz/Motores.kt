package com.xebolax.tiresias.voz

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Cómo acabó una frase. */
enum class Resultado { HABLADO, CORTADO, FALLO }

/**
 * Un motor de voz. [hablar] se llama siempre desde el hilo de la voz y bloquea
 * hasta que la frase termina, se corta o falla; [cortar] llega desde cualquier
 * hilo y hace que [hablar] vuelva enseguida.
 */
interface Motor {
	val id: String
	val nombre: String
	fun hablar(texto: String, idioma: Locale, velocidad: Int, tono: Int, sigue: () -> Boolean, alEmpezar: () -> Unit): Resultado
	fun cortar()
	fun cerrar()
}

/**
 * eSpeak NG, dentro de Tiresias: la voz de menor latencia (sintetiza una frase
 * corta en pocos milisegundos) y la que se usa si el motor del sistema falla.
 */
class MotorEspeak(private val contexto: Context, private val pista: PistaAudio) : Motor {
	override val id = Ajustes.MOTOR_ESPEAK
	override val nombre = "eSpeak NG"

	override fun hablar(texto: String, idioma: Locale, velocidad: Int, tono: Int, sigue: () -> Boolean, alEmpezar: () -> Unit): Resultado {
		var empezado = false
		for (frase in Marcado.frases(Marcado.limpiar(texto), MAXIMO_FRASE)) {
			if (!sigue()) return Resultado.CORTADO
			val muestras = EspeakNativo.sintetizar(contexto, frase, idioma, velocidad, tono) ?: return Resultado.FALLO
			if (!empezado) {
				empezado = true
				alEmpezar()
			}
			if (!pista.escribir(muestras, EspeakNativo.frecuenciaMuestras, sigue)) return Resultado.FALLO
		}
		if (!sigue()) return Resultado.CORTADO
		return if (pista.esperarFin(sigue)) Resultado.HABLADO else Resultado.CORTADO
	}

	override fun cortar() {
		EspeakNativo.cancelar()
		pista.cortar()
	}

	override fun cerrar() = Unit

	companion object {
		/** Frases cortas: la primera suena antes y se corta mejor. */
		const val MAXIMO_FRASE = 200
	}
}

/**
 * SVOX Pico, dentro de Tiresias: la segunda voz incluida (español, inglés de
 * EE. UU. y británico), independiente de eSpeak.
 */
class MotorPico(private val contexto: Context, private val pista: PistaAudio) : Motor {
	override val id = Ajustes.MOTOR_PICO
	override val nombre = "SVOX Pico"
	private var pico: PicoNativo? = null

	override fun hablar(texto: String, idioma: Locale, velocidad: Int, tono: Int, sigue: () -> Boolean, alEmpezar: () -> Unit): Resultado {
		val voz = VozPico.para(idioma)
		val motor = pico?.takeIf { it.voz == voz } ?: run {
			pico?.cerrar()
			PicoNativo.abrir(contexto, voz).also { pico = it }
		} ?: return Resultado.FALLO
		var empezado = false
		for (frase in Marcado.frases(Marcado.limpiar(texto), MotorEspeak.MAXIMO_FRASE)) {
			if (!sigue()) return Resultado.CORTADO
			val muestras = motor.sintetizar(Marcado.preparar(frase, velocidad, tono))
			if (muestras == null) {
				motor.reiniciar()
				return Resultado.FALLO
			}
			if (!empezado) {
				empezado = true
				alEmpezar()
			}
			if (!pista.escribir(muestras, PicoNativo.FRECUENCIA, sigue)) return Resultado.FALLO
		}
		if (!sigue()) {
			motor.reiniciar()
			return Resultado.CORTADO
		}
		return if (pista.esperarFin(sigue)) Resultado.HABLADO else Resultado.CORTADO
	}

	override fun cortar() = pista.cortar()

	override fun cerrar() {
		pico?.cerrar()
		pico = null
	}
}

/**
 * Un motor de voz del sistema (Google, Samsung, RHVoice, Vocalizer...) con
 * `TextToSpeech`.
 *
 * Se da por roto si no arranca, si da error o si una frase no empieza a sonar
 * en [ESPERA_INICIO_MS]: entonces [hablar] devuelve FALLO y la voz pasa a la
 * voz incluida (como `FailoverTextToSpeech` de TalkBack, pero sin quedarse
 * mudo mientras el motor se reinicia).
 */
class MotorSistema(private val contexto: Context, override val id: String, private val atributos: android.media.AudioAttributes) : Motor {
	// El nombre que ve el usuario («Speech Services by Google»), no el del paquete
	override var nombre: String = runCatching { instalados(contexto).firstOrNull { it.first == id }?.second }.getOrNull() ?: id
		private set

	private var tts: TextToSpeech? = null
	@Volatile private var listo = false
	@Volatile private var fallido = false
	private val contador = AtomicInteger()
	@Volatile private var pendiente: Pendiente? = null
	private var idiomaActual: Locale? = null
	private var vozElegida = ""

	private class Pendiente(val id: String) {
		val empezado = CountDownLatch(1)
		val terminado = CountDownLatch(1)
		@Volatile var resultado = Resultado.CORTADO
	}

	/** Arranca el motor si hace falta; espera como mucho [ESPERA_ARRANQUE_MS]. */
	private fun asegurar(): TextToSpeech? {
		tts?.let { if (listo) return it }
		if (fallido) return null
		val arrancado = CountDownLatch(1)
		var estado = TextToSpeech.ERROR
		val nuevo = try {
			TextToSpeech(contexto.applicationContext, { s ->
				estado = s
				arrancado.countDown()
			}, id)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo crear el motor $id", e)
			fallido = true
			return null
		}
		if (!arrancado.await(ESPERA_ARRANQUE_MS, TimeUnit.MILLISECONDS) || estado != TextToSpeech.SUCCESS) {
			Registro.w(ETIQUETA, "El motor $id no arrancó (estado $estado)")
			runCatching { nuevo.shutdown() }
			fallido = true
			return null
		}
		nuevo.setAudioAttributes(atributos)
		nuevo.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(utteranceId: String?) {
				pendiente?.takeIf { it.id == utteranceId }?.empezado?.countDown()
			}

			override fun onDone(utteranceId: String?) = terminar(utteranceId, Resultado.HABLADO)

			@Deprecated("Deprecated in Java")
			override fun onError(utteranceId: String?) = terminar(utteranceId, Resultado.FALLO)

			override fun onError(utteranceId: String?, errorCode: Int) = terminar(utteranceId, Resultado.FALLO)

			override fun onStop(utteranceId: String?, interrupted: Boolean) = terminar(utteranceId, Resultado.CORTADO)
		})
		nombre = runCatching { nuevo.engines.firstOrNull { it.name == id }?.label }.getOrNull() ?: id
		tts = nuevo
		listo = true
		return nuevo
	}

	private fun terminar(utterance: String?, r: Resultado) {
		val p = pendiente?.takeIf { it.id == utterance } ?: return
		p.resultado = r
		p.empezado.countDown()
		p.terminado.countDown()
	}

	/** Las voces del motor (para Ajustes). */
	fun voces(): List<Voice> = runCatching { asegurar()?.voices?.toList().orEmpty() }.getOrDefault(emptyList())

	fun elegirVoz(nombre: String) {
		vozElegida = nombre
	}

	override fun hablar(texto: String, idioma: Locale, velocidad: Int, tono: Int, sigue: () -> Boolean, alEmpezar: () -> Unit): Resultado {
		val t = asegurar() ?: return Resultado.FALLO
		try {
			if (idioma != idiomaActual) {
				val r = t.setLanguage(idioma)
				if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
					Registro.w(ETIQUETA, "El motor $id no tiene ${idioma.toLanguageTag()}")
					// Sin el idioma: mejor la voz incluida que una voz que no lo sabe leer
					return Resultado.FALLO
				}
				idiomaActual = idioma
				if (vozElegida.isNotEmpty()) t.voices?.firstOrNull { it.name == vozElegida && it.locale.language == idioma.language }?.let { t.voice = it }
			}
			t.setSpeechRate(velocidad / 100f)
			t.setPitch(tono / 100f)
			val p = Pendiente("t" + contador.incrementAndGet())
			pendiente = p
			val params = Bundle()
			// En una llamada, el volumen propio de la voz (Can Talk): solo cuando baja, para no cambiar nada fuera de ellas
			VozEnLlamadas.ganancia.takeIf { it < 1f }?.let { params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, it) }
			if (t.speak(texto, TextToSpeech.QUEUE_FLUSH, params, p.id) != TextToSpeech.SUCCESS) return Resultado.FALLO
			val inicio = SystemClock.uptimeMillis()
			while (!p.empezado.await(20, TimeUnit.MILLISECONDS)) {
				if (!sigue()) {
					t.stop()
					return Resultado.CORTADO
				}
				if (SystemClock.uptimeMillis() - inicio > ESPERA_INICIO_MS) {
					Registro.w(ETIQUETA, "El motor $id no empezó a hablar en $ESPERA_INICIO_MS ms")
					t.stop()
					return Resultado.FALLO
				}
			}
			if (p.resultado == Resultado.FALLO) return Resultado.FALLO
			alEmpezar()
			while (!p.terminado.await(20, TimeUnit.MILLISECONDS)) {
				if (!sigue()) {
					t.stop()
					return Resultado.CORTADO
				}
			}
			return p.resultado
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Fallo al hablar con el motor $id", e)
			return Resultado.FALLO
		}
	}

	override fun cortar() {
		runCatching { tts?.stop() }
	}

	/** Vuelve a intentar arrancar la próxima vez (tras un fallo). */
	fun reintentar() {
		cerrar()
		fallido = false
	}

	override fun cerrar() {
		runCatching { tts?.shutdown() }
		tts = null
		listo = false
		idiomaActual = null
	}

	companion object {
		private const val ETIQUETA = "MotorSistema"
		const val ESPERA_ARRANQUE_MS = 4000L
		const val ESPERA_INICIO_MS = 3000L

		/** Los motores de voz del sistema instalados (paquete y nombre), sin Tiresias. */
		fun instalados(contexto: Context): List<Pair<String, String>> {
			val pm = contexto.packageManager
			val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
			val lista = runCatching { pm.queryIntentServices(intent, 0) }.getOrDefault(emptyList())
			return lista.mapNotNull { r ->
				val paquete = r.serviceInfo?.packageName ?: return@mapNotNull null
				if (paquete == contexto.packageName) return@mapNotNull null
				paquete to (r.loadLabel(pm)?.toString() ?: paquete)
			}.distinctBy { it.first }
		}
	}
}
