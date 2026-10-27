package com.xebolax.tiresias.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.recursos.Recursos
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reconocimiento de texto sin Internet ni Play Services: PP-OCRv5 de
 * PaddlePaddle (detección y alfabeto latino, Apache 2.0) con ONNX Runtime (MIT).
 *
 * 1. **Detección**: la imagen se reduce a 960 píxeles de lado como mucho y el
 *    modelo da un mapa de probabilidad de texto; se separa en manchas
 *    conectadas y cada una, ampliada un poco, es una línea.
 * 2. **Reconocimiento**: cada línea se pone a 48 píxeles de alto y el modelo da,
 *    por columna, la probabilidad de cada carácter (decodificación CTC).
 *
 * Devuelve las líneas con su rectángulo en la pantalla, para leerlas como
 * texto o tocarlas (cursor de revisión por OCR en apps inaccesibles).
 */
class Ocr(private val contexto: Context) {
	data class Linea(val texto: String, val rect: Rect, val confianza: Float)

	private val entorno = com.xebolax.tiresias.base.Onnx.entorno()
	private var deteccion: OrtSession? = null
	private var reconocimiento: OrtSession? = null
	private var diccionario: List<String> = emptyList()

	val disponible: Boolean get() = Recursos.instalado(contexto, Recursos.OCR)

	@Synchronized
	private fun abrir() {
		if (deteccion != null) return
		val d = Recursos.carpeta(contexto, Recursos.OCR)
		val opciones = OrtSession.SessionOptions().apply {
			setIntraOpNumThreads(4)
			setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
		}
		deteccion = entorno.createSession(File(d, "det.onnx").path, opciones)
		reconocimiento = entorno.createSession(File(d, "rec.onnx").path, opciones)
		diccionario = leerDiccionario(File(d, "rec.yml"))
	}

	/** Libera los modelos (unos 50 MB de memoria) si no se van a usar en un rato. */
	@Synchronized
	fun cerrar() {
		runCatching { deteccion?.close() }
		runCatching { reconocimiento?.close() }
		deteccion = null
		reconocimiento = null
	}

	/** Las líneas de texto de [imagen], de arriba abajo y de izquierda a derecha. [desplazamiento]: dónde está la imagen en la pantalla. */
	@Synchronized
	fun reconocer(imagen: Bitmap, desplazamiento: Rect? = null): List<Linea> {
		abrir()
		val cajas = detectar(imagen)
		val lineas = ArrayList<Linea>()
		for (caja in cajas) {
			val recorte = runCatching { Bitmap.createBitmap(imagen, caja.left, caja.top, caja.width(), caja.height()) }.getOrNull() ?: continue
			val (texto, confianza) = leerLinea(recorte)
			if (recorte !== imagen) recorte.recycle()
			if (texto.isBlank() || confianza < CONFIANZA_MINIMA) continue
			val r = Rect(caja)
			desplazamiento?.let { r.offset(it.left, it.top) }
			lineas += Linea(texto.trim(), r, confianza)
		}
		return ordenar(lineas)
	}

	/** El texto de [imagen] por párrafos (las líneas cercanas se unen). */
	fun texto(imagen: Bitmap): String = reconocer(imagen).joinToString("\n") { it.texto }

	// --- Detección ------------------------------------------------------------------------

	private fun detectar(imagen: Bitmap): List<Rect> {
		val sesion = deteccion ?: return emptyList()
		val escala = min(1f, LADO_DETECCION.toFloat() / max(imagen.width, imagen.height))
		val ancho = max(32, ((imagen.width * escala) / 32f).roundToInt() * 32)
		val alto = max(32, ((imagen.height * escala) / 32f).roundToInt() * 32)
		val reducida = Bitmap.createScaledBitmap(imagen, ancho, alto, true)
		val pixeles = IntArray(ancho * alto)
		reducida.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
		if (reducida !== imagen) reducida.recycle()
		val datos = FloatBuffer.allocate(3 * ancho * alto)
		val plano = ancho * alto
		// PaddleOCR trabaja en BGR con la media y la desviación de ImageNet en ese orden
		val media = floatArrayOf(0.485f, 0.456f, 0.406f)
		val desv = floatArrayOf(0.229f, 0.224f, 0.225f)
		for (i in 0 until plano) {
			val p = pixeles[i]
			val b = (p and 0xFF) / 255f
			val g = (p shr 8 and 0xFF) / 255f
			val r = (p shr 16 and 0xFF) / 255f
			datos.put(i, (b - media[0]) / desv[0])
			datos.put(plano + i, (g - media[1]) / desv[1])
			datos.put(2 * plano + i, (r - media[2]) / desv[2])
		}
		val mapa: FloatArray = OnnxTensor.createTensor(entorno, datos, longArrayOf(1, 3, alto.toLong(), ancho.toLong())).use { entrada ->
			sesion.run(mapOf(sesion.inputNames.first() to entrada)).use { salida ->
				val t = salida[0] as OnnxTensor
				val fb = t.floatBuffer
				FloatArray(fb.remaining()).also { fb.get(it) }
			}
		}
		val cajas = manchas(mapa, ancho, alto)
		val fx = imagen.width.toFloat() / ancho
		val fy = imagen.height.toFloat() / alto
		return cajas.map { c ->
			Rect(
				(c.left * fx).toInt().coerceIn(0, imagen.width - 1),
				(c.top * fy).toInt().coerceIn(0, imagen.height - 1),
				ceil(c.right * fx).toInt().coerceIn(1, imagen.width),
				ceil(c.bottom * fy).toInt().coerceIn(1, imagen.height),
			)
		}.filter { it.width() >= 4 && it.height() >= 4 }
	}

	/** Las manchas conectadas del mapa de probabilidad, ampliadas como hace DBPostProcess (unclip 1,5). */
	private fun manchas(mapa: FloatArray, ancho: Int, alto: Int): List<Rect> {
		val visto = BooleanArray(mapa.size)
		val cola = IntArray(mapa.size)
		val r = ArrayList<Rect>()
		for (inicio in mapa.indices) {
			if (visto[inicio] || mapa[inicio] < UMBRAL) continue
			var cabeza = 0
			var fin = 0
			cola[fin++] = inicio
			visto[inicio] = true
			var izq = ancho
			var der = 0
			var arr = alto
			var aba = 0
			var suma = 0f
			while (cabeza < fin) {
				val k = cola[cabeza++]
				val x = k % ancho
				val y = k / ancho
				suma += mapa[k]
				if (x < izq) izq = x
				if (x > der) der = x
				if (y < arr) arr = y
				if (y > aba) aba = y
				// Cuatro vecinos
				if (x > 0) empujar(k - 1, mapa, visto, cola, fin).also { fin = it }
				if (x < ancho - 1) empujar(k + 1, mapa, visto, cola, fin).also { fin = it }
				if (y > 0) empujar(k - ancho, mapa, visto, cola, fin).also { fin = it }
				if (y < alto - 1) empujar(k + ancho, mapa, visto, cola, fin).also { fin = it }
			}
			val puntuacion = suma / fin
			val w = der - izq + 1
			val h = aba - arr + 1
			if (puntuacion < UMBRAL_CAJA || w < 3 || h < 3) continue
			val d = (w * h * UNCLIP) / (2f * (w + h))
			r += Rect((izq - d).toInt().coerceAtLeast(0), (arr - d).toInt().coerceAtLeast(0), (der + d).toInt().coerceAtMost(ancho), (aba + d).toInt().coerceAtMost(alto))
			if (r.size >= MAXIMO_CAJAS) break
		}
		return r
	}

	private fun empujar(k: Int, mapa: FloatArray, visto: BooleanArray, cola: IntArray, fin: Int): Int {
		if (visto[k] || mapa[k] < UMBRAL) return fin
		visto[k] = true
		cola[fin] = k
		return fin + 1
	}

	// --- Reconocimiento ---------------------------------------------------------------------

	private fun leerLinea(recorte: Bitmap): Pair<String, Float> {
		val sesion = reconocimiento ?: return "" to 0f
		val alto = ALTO_LINEA
		val ancho = (ceil(alto * recorte.width / recorte.height.toFloat()).toInt()).coerceIn(16, 3200)
		val linea = Bitmap.createScaledBitmap(recorte, ancho, alto, true)
		val pixeles = IntArray(ancho * alto)
		linea.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
		if (linea !== recorte) linea.recycle()
		val plano = ancho * alto
		val datos = FloatBuffer.allocate(3 * plano)
		for (i in 0 until plano) {
			val p = pixeles[i]
			datos.put(i, ((p and 0xFF) / 255f - 0.5f) / 0.5f)
			datos.put(plano + i, ((p shr 8 and 0xFF) / 255f - 0.5f) / 0.5f)
			datos.put(2 * plano + i, ((p shr 16 and 0xFF) / 255f - 0.5f) / 0.5f)
		}
		return OnnxTensor.createTensor(entorno, datos, longArrayOf(1, 3, alto.toLong(), ancho.toLong())).use { entrada ->
			sesion.run(mapOf(sesion.inputNames.first() to entrada)).use { salida ->
				val t = salida[0] as OnnxTensor
				val forma = t.info.shape
				val pasos = forma[1].toInt()
				val clases = forma[2].toInt()
				val fb = t.floatBuffer
				decodificar(fb, pasos, clases)
			}
		}
	}

	/** Decodificación CTC voraz: el carácter más probable de cada paso, sin repetidos ni huecos (índice 0). */
	private fun decodificar(fb: FloatBuffer, pasos: Int, clases: Int): Pair<String, Float> {
		val sb = StringBuilder()
		var anterior = -1
		var suma = 0f
		var n = 0
		for (t in 0 until pasos) {
			var mejor = 0
			var p = -1f
			for (c in 0 until clases) {
				val v = fb.get(t * clases + c)
				if (v > p) {
					p = v
					mejor = c
				}
			}
			if (mejor != 0 && mejor != anterior) {
				val i = mejor - 1
				sb.append(if (i < diccionario.size) diccionario[i] else " ")
				suma += p
				n++
			}
			anterior = mejor
		}
		return sb.toString() to (if (n > 0) suma / n else 0f)
	}

	/** Lee la lista `character_dict` de la configuración del modelo (YAML sencillo, una entrada por línea). */
	private fun leerDiccionario(yml: File): List<String> {
		val lista = ArrayList<String>()
		var dentro = false
		for (l in yml.readLines(Charsets.UTF_8)) {
			if (l.trim() == "character_dict:") {
				dentro = true
				continue
			}
			if (!dentro) continue
			val t = l.trimStart()
			if (!t.startsWith("- ")) {
				if (t.isNotEmpty() && !l.startsWith(" ")) break
				if (t.isNotEmpty()) break
				continue
			}
			var v = t.substring(2)
			if (v.length >= 2 && ((v.startsWith("'") && v.endsWith("'")) || (v.startsWith("\"") && v.endsWith("\"")))) {
				v = v.substring(1, v.length - 1).replace("''", "'")
				if (v == "\\\\") v = "\\"
			}
			lista += v
		}
		if (lista.isEmpty()) Registro.w(ETIQUETA, "El diccionario del OCR está vacío")
		return lista
	}

	/** De arriba abajo; en la misma altura (líneas que se solapan en vertical), de izquierda a derecha. */
	private fun ordenar(lineas: List<Linea>): List<Linea> = lineas.sortedWith { a, b ->
		val solape = min(a.rect.bottom, b.rect.bottom) - max(a.rect.top, b.rect.top)
		if (solape > min(a.rect.height(), b.rect.height()) / 2) a.rect.left - b.rect.left else a.rect.top - b.rect.top
	}

	companion object {
		private const val ETIQUETA = "Ocr"
		private const val LADO_DETECCION = 960
		private const val ALTO_LINEA = 48
		private const val UMBRAL = 0.3f
		private const val UMBRAL_CAJA = 0.6f
		private const val UNCLIP = 1.5f
		private const val MAXIMO_CAJAS = 400
		private const val CONFIANZA_MINIMA = 0.5f
	}
}
