package com.xebolax.tiresias.voz

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.recursos.Recursos
import com.xebolax.tiresias.recursos.VocesNeuronales
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Locale

/**
 * Una voz neuronal Piper (VITS en ONNX) descargada, con ONNX Runtime (MIT).
 *
 * Como en Piper: el texto se pasa a fonemas IPA con eSpeak NG (el mismo que
 * usaron para entrenar las voces), los fonemas se convierten en números con el
 * `phoneme_id_map` de la voz (inicio «^», relleno «_» entre fonemas y fin «$»)
 * y el modelo da el audio. Las frases se sintetizan de una en una para que la
 * primera suene enseguida.
 *
 * La puntuación de final de frase (. , ? ! ; :) se añade como fonema, porque
 * marca la entonación y las pausas de la voz.
 */
class MotorPiper(private val contexto: Context, private val voz: VocesNeuronales.Voz, private val pista: PistaAudio) : Motor {
	override val id = PREFIJO + voz.id
	override val nombre = voz.nombre

	private val entorno = com.xebolax.tiresias.base.Onnx.entorno()
	private var sesion: OrtSession? = null
	private var mapa: Map<Int, IntArray> = emptyMap()
	private var vozEspeak = "es"
	private var frecuencia = 22050
	private var ruido = 0.667f
	private var duracion = 1f
	private var ruidoW = 0.8f
	private var conHablante = false
	@Volatile private var cortada = false

	@Synchronized
	private fun abrir(): Boolean {
		if (sesion != null) return true
		val d = Recursos.carpeta(contexto, voz.recurso)
		val modelo = File(d, "voz.onnx")
		val config = File(d, "voz.onnx.json")
		if (!modelo.isFile || !config.isFile) return false
		return try {
			val j = JSONObject(config.readText())
			vozEspeak = j.optJSONObject("espeak")?.optString("voice")?.takeIf { it.isNotBlank() } ?: voz.locale.language
			frecuencia = j.optJSONObject("audio")?.optInt("sample_rate", 22050) ?: 22050
			j.optJSONObject("inference")?.let {
				ruido = it.optDouble("noise_scale", 0.667).toFloat()
				duracion = it.optDouble("length_scale", 1.0).toFloat()
				ruidoW = it.optDouble("noise_w", 0.8).toFloat()
			}
			val ids = j.getJSONObject("phoneme_id_map")
			val m = HashMap<Int, IntArray>()
			for (k in ids.keys()) {
				if (k.isEmpty()) continue
				val arr = ids.getJSONArray(k)
				m[k.codePointAt(0)] = IntArray(arr.length()) { arr.getInt(it) }
			}
			mapa = m
			val opciones = OrtSession.SessionOptions().apply {
				setIntraOpNumThreads(2)
				setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
			}
			val s = entorno.createSession(modelo.path, opciones)
			conHablante = "sid" in s.inputNames
			sesion = s
			true
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo abrir la voz ${voz.id}", e)
			false
		}
	}

	/**
	 * Abre el modelo y lo calienta con una frase corta en segundo plano, al
	 * elegir la voz: así la primera frase de verdad no espera a que se cargue
	 * (más de un segundo la primera vez; luego, unos 150 ms).
	 */
	fun precargar() {
		Thread({
			if (!abrir()) return@Thread
			runCatching { identificadores("hola.")?.let { sintetizar(it, 100) } }
		}, "tiresias-piper").apply { priority = Thread.MIN_PRIORITY }.start()
	}

	override fun hablar(texto: String, idioma: Locale, velocidad: Int, tono: Int, sigue: () -> Boolean, alEmpezar: () -> Unit): Resultado {
		if (!abrir()) return Resultado.FALLO
		cortada = false
		var empezado = false
		for (frase in Marcado.frases(Marcado.limpiar(texto), MotorEspeak.MAXIMO_FRASE)) {
			if (!sigue() || cortada) return Resultado.CORTADO
			val ids = identificadores(frase) ?: return Resultado.FALLO
			if (ids.size <= 3) continue
			val muestras = sintetizar(ids, velocidad) ?: return Resultado.FALLO
			if (!empezado) {
				empezado = true
				alEmpezar()
			}
			if (!pista.escribir(muestras, frecuencia, sigue)) return Resultado.FALLO
		}
		if (!sigue() || cortada) return Resultado.CORTADO
		return if (pista.esperarFin(sigue)) Resultado.HABLADO else Resultado.CORTADO
	}

	/** La frase en identificadores de fonema de esta voz: ^ _ f1 _ f2 _ ... $ */
	private fun identificadores(frase: String): IntArray? {
		val lista = ArrayList<Int>()
		val relleno = mapa['_'.code]?.firstOrNull() ?: 0
		mapa['^'.code]?.let { lista.addAll(it.toList()); lista += relleno }
		for (m in CLAUSULA.findAll(frase)) {
			val cuerpo = m.groupValues[1].trim()
			val signos = m.groupValues[2]
			if (cuerpo.isNotEmpty()) {
				val fonemas = EspeakNativo.fonemas(contexto, cuerpo, vozEspeak) ?: return null
				var i = 0
				while (i < fonemas.length) {
					val cp = fonemas.codePointAt(i)
					mapa[cp]?.let { ids -> ids.forEach { lista += it; lista += relleno } }
					i += Character.charCount(cp)
				}
			}
			for (s in signos) mapa[s.code]?.let { ids -> ids.forEach { lista += it; lista += relleno } }
			mapa[' '.code]?.let { ids -> ids.forEach { lista += it; lista += relleno } }
		}
		mapa['$'.code]?.let { lista.addAll(it.toList()) }
		return lista.toIntArray()
	}

	private fun sintetizar(ids: IntArray, velocidad: Int): ShortArray? {
		val s = sesion ?: return null
		return try {
			val entrada = OnnxTensor.createTensor(entorno, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), longArrayOf(1, ids.size.toLong()))
			val largo = OnnxTensor.createTensor(entorno, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
			// La velocidad de Tiresias acorta o alarga la duración de los fonemas
			val escalas = OnnxTensor.createTensor(entorno, FloatBuffer.wrap(floatArrayOf(ruido, duracion * 100f / velocidad.coerceIn(30, 600), ruidoW)), longArrayOf(3))
			val entradas = HashMap<String, OnnxTensor>()
			entradas["input"] = entrada
			entradas["input_lengths"] = largo
			entradas["scales"] = escalas
			if (conHablante) entradas["sid"] = OnnxTensor.createTensor(entorno, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1))
			try {
				s.run(entradas).use { r ->
					val fb = (r[0] as OnnxTensor).floatBuffer
					val n = fb.remaining()
					val muestras = ShortArray(n)
					// Normaliza el volumen como Piper: el pico de la frase al máximo de 16 bits
					var maximo = 0f
					for (i in 0 until n) maximo = maxOf(maximo, Math.abs(fb.get(i)))
					val escala = 32767f / maxOf(0.01f, maximo)
					for (i in 0 until n) muestras[i] = (fb.get(i) * escala).toInt().coerceIn(-32768, 32767).toShort()
					muestras
				}
			} finally {
				entradas.values.forEach { it.close() }
			}
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "La voz ${voz.id} falló al sintetizar", e)
			null
		}
	}

	override fun cortar() {
		cortada = true
		pista.cortar()
	}

	override fun cerrar() {
		runCatching { sesion?.close() }
		sesion = null
	}

	companion object {
		private const val ETIQUETA = "Piper"
		const val PREFIJO = "tiresias:piper:"
		private val CLAUSULA = Regex("([^.,;:!?¡¿]*)([.,;:!?]*)")

		/** El motor Piper para el identificador de Ajustes, si la voz está descargada. */
		fun para(c: Context, id: String, pista: PistaAudio): MotorPiper? {
			if (!id.startsWith(PREFIJO)) return null
			val voz = VocesNeuronales.porId(id.removePrefix(PREFIJO)) ?: return null
			if (!Recursos.instalado(c, voz.recurso)) return null
			return MotorPiper(c, voz, pista)
		}
	}
}
