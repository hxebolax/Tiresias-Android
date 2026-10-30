package com.xebolax.tiresias.braille

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import kotlin.math.abs
import kotlin.math.hypot

/** Hacia dónde va un deslizamiento sobre la superficie braille (ya girado si los puntos van en espejo). */
enum class DireccionBraille { IZQUIERDA, DERECHA, ARRIBA, ABAJO }

/**
 * Un deslizamiento sobre la superficie braille: [dedos] (1 a 3), [direccion] y,
 * con un dedo, el [punto] (1 a 6) en el que empezó, para los gestos de un punto
 * (Can Talk): «desde el punto 3 hacia la izquierda» es otro gesto que «un dedo
 * hacia la izquierda».
 */
data class GestoBraille(val dedos: Int, val direccion: DireccionBraille, val punto: Int = 0) {
	/** La clave de los ajustes: «1_IZQUIERDA» o, con punto, «p3_IZQUIERDA». */
	val clave: String get() = if (punto > 0) "p${punto}_${direccion.name}" else "${dedos}_${direccion.name}"

	/** El mismo gesto sin el punto: el que vale si el del punto no tiene nada. */
	val generico: GestoBraille get() = copy(punto = 0)

	companion object {
		fun deClave(clave: String): GestoBraille? {
			val (izq, dir) = clave.split('_', limit = 2).takeIf { it.size == 2 } ?: return null
			val direccion = DireccionBraille.entries.firstOrNull { it.name == dir } ?: return null
			return when {
				izq.startsWith("p") -> izq.drop(1).toIntOrNull()?.takeIf { it in 1..6 }?.let { GestoBraille(1, direccion, it) }
				else -> izq.toIntOrNull()?.takeIf { it in 1..3 }?.let { GestoBraille(it, direccion) }
			}
		}
	}
}

/** Lo que hace quien usa una [SuperficieBraille]: el teclado, la navegación web braille, la búsqueda o el control de un PC. */
interface OyenteSuperficie {
	/** Una combinación de puntos al levantar todos los dedos (bits 0 a 5 = puntos 1 a 6). */
	fun alPuntos(puntos: Int)
	fun alGesto(g: GestoBraille)
	/** Un dedo se ha apoyado: para el clic o el sonido. */
	fun alApoyar() = Unit
	/** Algo que decir (la calibración). */
	fun anunciar(texto: String)
	/** Si los puntos van en espejo ahora. */
	fun espejo(): Boolean
}

/**
 * Las posiciones de los seis puntos que ha tocado el usuario (calibración punto
 * a punto, Can Talk), en proporción del ancho y del alto de la superficie: una
 * para los puntos normales y otra para los puntos en espejo, porque la mano se
 * pone distinta. Es configuración, no texto de la pantalla.
 */
object CalibracionBraille {
	fun leer(ajustes: Ajustes, espejo: Boolean): List<PointF>? {
		val texto = ajustes.calibracionBraille(espejo)
		if (texto.isBlank()) return null
		val puntos = texto.split(';').mapNotNull { par ->
			val (x, y) = par.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
			val px = x.toFloatOrNull() ?: return@mapNotNull null
			val py = y.toFloatOrNull() ?: return@mapNotNull null
			PointF(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
		}
		return puntos.takeIf { it.size == 6 }
	}

	fun guardar(ajustes: Ajustes, espejo: Boolean, puntos: List<PointF>) {
		ajustes.ponerCalibracionBraille(espejo, puntos.joinToString(";") { "%.4f,%.4f".format(java.util.Locale.ROOT, it.x, it.y) })
	}

	fun quitar(ajustes: Ajustes) {
		ajustes.ponerCalibracionBraille(false, "")
		ajustes.ponerCalibracionBraille(true, "")
	}

	fun hay(ajustes: Ajustes): Boolean = leer(ajustes, false) != null || leer(ajustes, true) != null
}

/**
 * La superficie donde se apoyan los dedos para escribir en braille: toda la
 * vista recibe los dedos. Sin calibrar, los puntos se reparten por posición (la
 * mitad de arriba son 1, 2 y 3 de izquierda a derecha y la de abajo 4, 5 y 6,
 * que es como quedan los dedos con el móvil en horizontal y la pantalla hacia
 * fuera); calibrada, cada dedo es el punto más cercano de los que tocó el
 * usuario. La usan el teclado braille (con el 60 % del alto de la pantalla) y
 * las superficies que se ponen encima de todo (la navegación web braille, la
 * búsqueda braille y el control de un PC), que ocupan la pantalla entera.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class SuperficieBraille(
	contexto: Context,
	private val oyente: OyenteSuperficie,
	private val textos: Context,
	/** La parte del alto de la pantalla; 0 ocupa lo que le den. */
	private val parteDelAlto: Float = 0.6f,
	/** Lo que se dibuja arriba a la izquierda (para quien ve un poco). */
	private val rotulo: String = "⠿ Tiresias",
) : View(contexto) {
	private val ajustes = Ajustes(contexto)
	private val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 48f }
	private var puntos = 0
	private var dedosMax = 0
	private val inicioX = HashMap<Int, Float>()
	private val inicioY = HashMap<Int, Float>()
	private var momento = 0L

	/** La calibración en curso: los puntos tocados hasta ahora, o null si no se calibra. */
	private var calibrando: MutableList<PointF>? = null
	private var alCalibrar: ((Boolean) -> Unit)? = null

	/** Lo calibrado de ahora (se lee una vez por toque, no por dedo). */
	private var centros: List<PointF>? = null

	init {
		setBackgroundColor(Color.BLACK)
		contentDescription = textos.getString(R.string.braille_superficie)
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (parteDelAlto <= 0f) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			return
		}
		val alto = (resources.displayMetrics.heightPixels * parteDelAlto).toInt()
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), alto)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawText(rotulo, 32f, 64f, pintura)
	}

	@Suppress("DEPRECATION")
	fun anunciarAccesible(texto: String) {
		if (texto.isNotEmpty()) announceForAccessibility(texto)
	}

	/**
	 * Empieza la calibración: se pide «Toca el punto 1», luego el 2... hasta el
	 * 6, cada uno con un dedo y donde caiga natural. Al terminar se guarda para
	 * la postura de ahora (normal o en espejo). Dos dedos hacia arriba la cancelan.
	 */
	fun calibrar(alTerminar: (Boolean) -> Unit) {
		calibrando = ArrayList()
		alCalibrar = alTerminar
		oyente.anunciar(textos.getString(R.string.braille_calibrar_empezar))
	}

	val estaCalibrando: Boolean get() = calibrando != null

	private fun cancelarCalibracion() {
		calibrando = null
		val fin = alCalibrar
		alCalibrar = null
		oyente.anunciar(textos.getString(R.string.braille_calibrar_cancelado))
		fin?.invoke(false)
	}

	private fun puntoCalibrado(x: Float, y: Float) {
		val lista = calibrando ?: return
		val nuevo = PointF(x / width.coerceAtLeast(1), y / height.coerceAtLeast(1))
		// Dos puntos casi en el mismo sitio: se pide otra vez
		if (lista.any { hypot((it.x - nuevo.x) * width, (it.y - nuevo.y) * height) < minOf(width, height) * DISTANCIA_MINIMA }) {
			oyente.anunciar(textos.getString(R.string.braille_calibrar_cerca, lista.size + 1))
			return
		}
		lista += nuevo
		performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
		if (lista.size < 6) {
			oyente.anunciar(textos.getString(R.string.braille_calibrar_siguiente, lista.size, lista.size + 1))
			return
		}
		CalibracionBraille.guardar(ajustes, oyente.espejo(), lista)
		calibrando = null
		val fin = alCalibrar
		alCalibrar = null
		oyente.anunciar(textos.getString(R.string.braille_calibrar_hecho))
		fin?.invoke(true)
	}

	override fun onTouchEvent(e: MotionEvent): Boolean {
		when (e.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
				if (e.actionMasked == MotionEvent.ACTION_DOWN) {
					puntos = 0
					dedosMax = 0
					momento = SystemClock.uptimeMillis()
					centros = if (calibrando == null) CalibracionBraille.leer(ajustes, oyente.espejo()) else null
				}
				val i = e.actionIndex
				val id = e.getPointerId(i)
				inicioX[id] = e.getX(i)
				inicioY[id] = e.getY(i)
				puntos = puntos or punto(e.getX(i), e.getY(i))
				oyente.alApoyar()
				dedosMax = maxOf(dedosMax, e.pointerCount)
			}
			MotionEvent.ACTION_UP -> {
				val id = e.getPointerId(e.actionIndex)
				val sx = inicioX[id] ?: e.x
				val sy = inicioY[id] ?: e.y
				val dx = e.x - sx
				val dy = e.y - sy
				val horizontal = abs(dx) > width * 0.25f && abs(dx) > abs(dy)
				val vertical = abs(dy) > height * 0.2f && abs(dy) > abs(dx)
				val gesto = when {
					horizontal -> {
						// Con los puntos en espejo, también giran izquierda y derecha (borrar y espacio)
						val izquierda = (dx < 0) != oyente.espejo()
						GestoBraille(dedosMax.coerceIn(1, 3), if (izquierda) DireccionBraille.IZQUIERDA else DireccionBraille.DERECHA)
					}
					vertical -> GestoBraille(dedosMax.coerceIn(1, 3), if (dy < 0) DireccionBraille.ARRIBA else DireccionBraille.ABAJO)
					else -> null
				}?.let { g -> if (g.dedos == 1) g.copy(punto = numeroDePunto(punto(sx, sy))) else g }
				val lista = calibrando
				when {
					lista != null && gesto != null && gesto.dedos == 2 && gesto.direccion == DireccionBraille.ARRIBA -> cancelarCalibracion()
					lista != null && gesto == null && dedosMax == 1 -> puntoCalibrado(sx, sy)
					lista != null -> oyente.anunciar(textos.getString(R.string.braille_calibrar_un_dedo, lista.size + 1))
					gesto != null -> oyente.alGesto(gesto)
					puntos != 0 -> oyente.alPuntos(puntos)
				}
				puntos = 0
				inicioX.clear()
				inicioY.clear()
			}
			MotionEvent.ACTION_CANCEL -> {
				puntos = 0
				inicioX.clear()
				inicioY.clear()
			}
		}
		return true
	}

	/**
	 * El punto (bit) de una posición. Calibrada: el centro más cercano de los
	 * que tocó el usuario. Sin calibrar: tres columnas por fila; fila de arriba
	 * 1, 2, 3 y de abajo 4, 5, 6 (en espejo, el punto 1 a la derecha).
	 */
	private fun punto(x: Float, y: Float): Int {
		centros?.let { c ->
			val nx = x / width.coerceAtLeast(1)
			val ny = y / height.coerceAtLeast(1)
			val i = c.indices.minByOrNull { hypot((c[it].x - nx) * width, (c[it].y - ny) * height) } ?: 0
			return 1 shl i
		}
		val leida = (x / (width / 3f)).toInt().coerceIn(0, 2)
		val columna = if (oyente.espejo()) 2 - leida else leida
		val abajo = y > height / 2f
		return 1 shl (columna + if (abajo) 3 else 0)
	}

	private fun numeroDePunto(bit: Int): Int = Integer.numberOfTrailingZeros(bit) + 1

	private companion object {
		/** Dos puntos calibrados tienen que estar al menos a esta parte del lado corto. */
		const val DISTANCIA_MINIMA = 0.08f
	}
}
