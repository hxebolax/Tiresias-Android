package com.xebolax.tiresias.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.voz.EspeakNativo
import com.xebolax.tiresias.voz.Marcado
import com.xebolax.tiresias.voz.PicoNativo
import com.xebolax.tiresias.voz.VozPico
import java.util.Locale

/**
 * «Voces de Tiresias» como motor de voz del sistema: eSpeak NG (español de
 * España y de Latinoamérica, inglés, francés, alemán, italiano y portugués) y
 * Pico (español e inglés), sin Internet.
 *
 * Sin Google Play Services muchos móviles no tienen una voz en español: así
 * cualquier app (y otros lectores) la tienen. Basado en el motor propio de
 * Rescate Accesible (mismo autor), con eSpeak NG añadido.
 *
 * Android llama a la síntesis siempre desde el mismo hilo; [onStop] llega desde
 * otro y solo levanta una marca.
 */
class ServicioTtsTiresias : TextToSpeechService() {
	private var pico: PicoNativo? = null
	@Volatile private var parar = false
	@Volatile private var cargada: VozTiresias = VOCES.first()

	/** Una voz de este motor: eSpeak o Pico en un idioma. */
	data class VozTiresias(val nombre: String, val locale: Locale, val motor: String)

	override fun onDestroy() {
		super.onDestroy()
		pico?.cerrar()
		pico = null
	}

	override fun onIsLanguageAvailable(idioma: String?, pais: String?, variante: String?): Int = disponibilidad(idioma, pais)

	override fun onGetLanguage(): Array<String> = arrayOf(cargada.locale.isO3Language, runCatching { cargada.locale.isO3Country }.getOrDefault(""), "")

	override fun onLoadLanguage(idioma: String?, pais: String?, variante: String?): Int {
		val r = disponibilidad(idioma, pais)
		if (r >= TextToSpeech.LANG_AVAILABLE) cargada = elegir(idioma, pais)
		return r
	}

	override fun onStop() {
		parar = true
		EspeakNativo.cancelar()
	}

	override fun onGetVoices(): List<Voice> = VOCES.map { Voice(it.nombre, it.locale, Voice.QUALITY_NORMAL, Voice.LATENCY_VERY_LOW, false, emptySet()) }

	override fun onIsValidVoiceName(nombre: String?): Int = if (VOCES.any { it.nombre == nombre }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

	override fun onLoadVoice(nombre: String?): Int {
		cargada = VOCES.firstOrNull { it.nombre == nombre } ?: return TextToSpeech.ERROR
		return TextToSpeech.SUCCESS
	}

	override fun onGetDefaultVoiceNameFor(idioma: String?, pais: String?, variante: String?): String? =
		if (disponibilidad(idioma, pais) >= TextToSpeech.LANG_AVAILABLE) elegir(idioma, pais).nombre else null

	override fun onSynthesizeText(peticion: SynthesisRequest, respuesta: SynthesisCallback) {
		parar = false
		val voz = peticion.voiceName?.let { n -> VOCES.firstOrNull { it.nombre == n } }
			?: if (disponibilidad(peticion.language, peticion.country) >= TextToSpeech.LANG_AVAILABLE) elegir(peticion.language, peticion.country)
			// Un idioma que no hay: mejor en el idioma de Tiresias que callar
			else elegir(Idioma.efectivo(this).language, Idioma.efectivo(this).country)
		val texto = peticion.charSequenceText?.toString() ?: @Suppress("DEPRECATION") peticion.text.orEmpty()
		try {
			if (voz.motor == "pico") pico(voz, texto, peticion, respuesta) else espeak(voz, texto, peticion, respuesta)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Fallo al sintetizar como motor del sistema", e)
			respuesta.error(TextToSpeech.ERROR_SYNTHESIS)
		}
	}

	private fun espeak(voz: VozTiresias, texto: String, peticion: SynthesisRequest, r: SynthesisCallback) {
		if (!EspeakNativo.iniciar(this)) {
			r.error(TextToSpeech.ERROR_SYNTHESIS)
			return
		}
		if (r.start(EspeakNativo.frecuenciaMuestras, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
		for (frase in Marcado.frases(Marcado.limpiar(texto))) {
			if (parar) break
			val m = EspeakNativo.sintetizar(this, frase, voz.locale, peticion.speechRate, peticion.pitch)
			if (m == null) {
				r.error(TextToSpeech.ERROR_SYNTHESIS)
				return
			}
			if (!entregar(m, r)) break
		}
		r.done()
	}

	private fun pico(voz: VozTiresias, texto: String, peticion: SynthesisRequest, r: SynthesisCallback) {
		val vozPico = VozPico.para(voz.locale)
		val motor = pico?.takeIf { it.voz == vozPico } ?: run {
			pico?.cerrar()
			PicoNativo.abrir(this, vozPico).also { pico = it }
		}
		if (motor == null) {
			r.error(TextToSpeech.ERROR_SYNTHESIS)
			return
		}
		if (r.start(PicoNativo.FRECUENCIA, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
		for (frase in Marcado.frases(Marcado.limpiar(texto))) {
			if (parar) break
			val m = motor.sintetizar(Marcado.preparar(frase, peticion.speechRate, peticion.pitch))
			if (m == null) {
				motor.reiniciar()
				r.error(TextToSpeech.ERROR_SYNTHESIS)
				return
			}
			if (!entregar(m, r)) break
		}
		if (parar) motor.reiniciar()
		r.done()
	}

	/** Entrega las muestras al sistema en trozos del tamaño que admite. false si hay que parar. */
	private fun entregar(muestras: ShortArray, r: SynthesisCallback): Boolean {
		val bytes = ByteArray(muestras.size * 2)
		for (i in muestras.indices) {
			val m = muestras[i].toInt()
			bytes[2 * i] = (m and 0xFF).toByte()
			bytes[2 * i + 1] = (m shr 8 and 0xFF).toByte()
		}
		val maximo = r.maxBufferSize.coerceAtLeast(1024)
		var desde = 0
		while (desde < bytes.size) {
			if (parar) return false
			val n = minOf(maximo, bytes.size - desde)
			if (r.audioAvailable(bytes, desde, n) != TextToSpeech.SUCCESS) return false
			desde += n
		}
		return true
	}

	companion object {
		private const val ETIQUETA = "ServicioTts"

		val VOCES = listOf(
			VozTiresias("es-ES-espeak", Locale("es", "ES"), "espeak"),
			VozTiresias("es-419-espeak", Locale("es", "419"), "espeak"),
			VozTiresias("es-MX-espeak", Locale("es", "MX"), "espeak"),
			VozTiresias("en-US-espeak", Locale.US, "espeak"),
			VozTiresias("en-GB-espeak", Locale.UK, "espeak"),
			VozTiresias("fr-FR-espeak", Locale.FRANCE, "espeak"),
			VozTiresias("de-DE-espeak", Locale.GERMANY, "espeak"),
			VozTiresias("it-IT-espeak", Locale.ITALY, "espeak"),
			VozTiresias("pt-PT-espeak", Locale("pt", "PT"), "espeak"),
			VozTiresias("pt-BR-espeak", Locale("pt", "BR"), "espeak"),
			VozTiresias("es-ES-pico", Locale("es", "ES"), "pico"),
			VozTiresias("en-US-pico", Locale.US, "pico"),
			VozTiresias("en-GB-pico", Locale.UK, "pico"),
		)

		private fun corto(i: String?): String = when (i?.lowercase(Locale.ROOT)) {
			"spa" -> "es"
			"eng" -> "en"
			"fra", "fre" -> "fr"
			"deu", "ger" -> "de"
			"ita" -> "it"
			"por" -> "pt"
			else -> i?.lowercase(Locale.ROOT).orEmpty()
		}

		private fun pais(p: String?): String = when (p?.uppercase(Locale.ROOT)) {
			"ESP" -> "ES"; "USA" -> "US"; "GBR" -> "GB"; "MEX" -> "MX"; "FRA" -> "FR"; "DEU" -> "DE"; "ITA" -> "IT"; "PRT" -> "PT"; "BRA" -> "BR"
			else -> p?.uppercase(Locale.ROOT).orEmpty()
		}

		fun disponibilidad(idioma: String?, p: String?): Int {
			val i = corto(idioma)
			val c = pais(p)
			val delIdioma = VOCES.filter { it.locale.language == i }
			if (delIdioma.isEmpty()) return TextToSpeech.LANG_NOT_SUPPORTED
			return if (delIdioma.any { it.locale.country == c }) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
		}

		fun elegir(idioma: String?, p: String?): VozTiresias {
			val i = corto(idioma)
			val c = pais(p)
			val delIdioma = VOCES.filter { it.locale.language == i && it.motor == "espeak" }
			// Español de América (cualquier país que no sea España): la variante latinoamericana
			if (i == "es" && c.isNotEmpty() && c != "ES") return delIdioma.firstOrNull { it.locale.country == c } ?: VOCES[1]
			return delIdioma.firstOrNull { it.locale.country == c } ?: delIdioma.firstOrNull() ?: VOCES.first()
		}
	}
}
