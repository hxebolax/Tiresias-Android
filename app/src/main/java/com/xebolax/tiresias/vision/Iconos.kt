package com.xebolax.tiresias.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ia.AsistenteIA
import com.xebolax.tiresias.ia.ModeloLocal
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.NombreIcono
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Tipo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Nombres para los botones y las imágenes sin etiqueta, en el propio móvil y
 * sin Internet: al enfocar uno, Tiresias lo recorta de una captura y lo lee con
 * el OCR (muchos iconos llevan texto) o, si el usuario lo ha elegido y tiene el
 * modelo descargado, se lo pregunta a la IA del móvil. Después dice
 * «posiblemente: Enviar». Nunca se manda nada fuera del móvil.
 *
 * Lo reconocido se guarda en memoria por la huella de la imagen y por el
 * elemento, así que el mismo icono no se vuelve a reconocer. La acción
 * «Etiquetar el elemento» propone lo reconocido para guardarlo como etiqueta.
 */
class Iconos(private val lector: Lector, private val vision: Vision) {
	private val servicio = lector.servicio
	private val trabajo = Executors.newSingleThreadExecutor { Thread(it, "tiresias-iconos").apply { priority = Thread.MIN_PRIORITY } }
	@Volatile private var ocupado = false
	private var pendiente: Runnable? = null

	/** Huella de la imagen (con la app) → nombre ("" si no se pudo reconocer). */
	private val porHuella = recientes<String>()
	/** Elemento (app, identificador, clase, tamaño) → nombre, para no capturar otra vez. */
	private val porElemento = recientes<String>()

	private fun <T> recientes() = object : LinkedHashMap<String, T>(64, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = size > 300
	}

	private fun modo(): String = lector.ajustes.iconos

	/** Lo reconocido para [n], si ya se sabe (para proponerlo como etiqueta). */
	@Synchronized
	fun sugerencia(n: AccessibilityNodeInfo): String? = porElemento[clave(n)]?.takeIf { it.isNotEmpty() }

	/** Se acaba de decir [n], que no tiene nombre: se intenta reconocer cuando el foco se queda en él. */
	fun alEnfocarSinNombre(n: AccessibilityNodeInfo) {
		if (modo() == NO || Build.VERSION.SDK_INT < 30 || n.isPassword) return
		val ya = synchronized(this) { porElemento[clave(n)] }
		if (ya != null) {
			if (ya.isNotEmpty()) decir(n, ya)
			return
		}
		pendiente?.let { lector.manejador.removeCallbacks(it) }
		// Se espera a que el foco se quede: al deslizar seguido no se captura nada
		val r = Runnable { if (lector.foco == n && !ocupado) reconocer(n) }
		pendiente = r
		lector.manejador.postDelayed(r, ESPERA_MS)
	}

	private fun reconocer(n: AccessibilityNodeInfo) {
		val zona = Nodos.limites(n)
		val pantalla = servicio.resources.displayMetrics
		// Lo que ocupa media pantalla no es un icono
		if (zona.width() < 8 || zona.height() < 8 || zona.width() * zona.height() > pantalla.widthPixels * pantalla.heightPixels / 3) return
		val claveElemento = clave(n)
		val paquete = n.packageName?.toString().orEmpty()
		ocupado = true
		trabajo.execute {
			try {
				val captura = vision.capturar() ?: return@execute
				val icono = recortar(captura, zona)
				captura.recycle()
				icono ?: return@execute
				val huella = "$paquete:${huella(icono)}"
				var nombre = synchronized(this) { porHuella[huella] }
				if (nombre == null) {
					nombre = conOcr(icono) ?: (if (modo() == CON_IA) conIa(icono) else null) ?: ""
					synchronized(this) { porHuella[huella] = nombre }
				}
				icono.recycle()
				synchronized(this) { porElemento[claveElemento] = nombre }
				if (nombre.isNotEmpty()) lector.manejador.post { if (lector.foco == n) decir(n, nombre) }
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo reconocer el icono", e)
			} finally {
				ocupado = false
			}
		}
	}

	private fun decir(n: AccessibilityNodeInfo, nombre: String) {
		if (lector.foco != n) return
		lector.voz.decir(com.xebolax.tiresias.voz.Enunciado(lector.textos.getString(R.string.iconos_posible, nombre), Modo.ENCOLAR, Tipo.NAVEGACION))
	}

	private fun conOcr(icono: Bitmap): String? {
		if (!vision.ocr.disponible) return null
		// El OCR lee mejor el texto pequeño de un icono si se agranda
		val escala = if (icono.height < 64) 64f / icono.height else 1f
		val img = if (escala > 1f) Bitmap.createScaledBitmap(icono, (icono.width * escala).toInt(), 64, true) else icono
		val lineas = vision.ocr.reconocer(img).map { it.texto to it.confianza }
		if (img !== icono) img.recycle()
		return NombreIcono.deOcr(lineas)
	}

	private fun conIa(icono: Bitmap): String? {
		if (ModeloLocal.estado(servicio) != ModeloLocal.Estado.DESCARGADO) return null
		val t = lector.textos
		val f = File(servicio.cacheDir, "vision/icono.jpg").apply { parentFile?.mkdirs() }
		// Los modelos ven mejor una imagen de al menos 224 píxeles
		val escala = maxOf(1f, 224f / maxOf(icono.width, icono.height))
		val img = if (escala > 1f) Bitmap.createScaledBitmap(icono, (icono.width * escala).toInt(), (icono.height * escala).toInt(), true) else icono
		FileOutputStream(f).use { img.compress(Bitmap.CompressFormat.JPEG, 90, it) }
		if (img !== icono) img.recycle()
		return try {
			val r = ModeloLocal.preguntar(servicio, t.getString(R.string.iconos_instrucciones), listOf(AsistenteIA.Mensaje(true, t.getString(R.string.iconos_pregunta))), f)
			NombreIcono.deIa(r)
		} catch (e: AsistenteIA.ErrorIA) {
			Registro.w(ETIQUETA, "La IA del móvil no pudo nombrar el icono: ${e.message}")
			null
		} finally {
			f.delete()
		}
	}

	private fun recortar(b: Bitmap, r: Rect): Bitmap? {
		val c = Rect(r)
		if (!c.intersect(0, 0, b.width, b.height) || c.width() < 4 || c.height() < 4) return null
		return Bitmap.createBitmap(b, c.left, c.top, c.width(), c.height())
	}

	/** Huella de diferencia (dHash) de 64 bits: el mismo dibujo da la misma huella aunque cambie un poco. */
	private fun huella(b: Bitmap): Long {
		val p = Bitmap.createScaledBitmap(b, 9, 8, true)
		var h = 0L
		for (y in 0 until 8) for (x in 0 until 8) {
			if (gris(p.getPixel(x, y)) > gris(p.getPixel(x + 1, y))) h = h or (1L shl (y * 8 + x))
		}
		p.recycle()
		return h
	}

	private fun gris(c: Int) = Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114

	private fun clave(n: AccessibilityNodeInfo): String {
		val r = Nodos.limites(n)
		return "${n.packageName}|${n.viewIdResourceName}|${n.className}|${r.width()}x${r.height()}|${if (n.viewIdResourceName == null) "${r.left},${r.top}" else ""}"
	}

	fun cerrar() = trabajo.shutdownNow()

	companion object {
		private const val ETIQUETA = "Iconos"
		private const val ESPERA_MS = 600L
		const val NO = "no"
		const val CON_OCR = "ocr"
		const val CON_IA = "ia"
	}
}
