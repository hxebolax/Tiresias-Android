package com.xebolax.tiresias.ventanas

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.xebolax.tiresias.base.Registro
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Menú circular (Backtalk 36): se pone un dedo en cualquier sitio, se desliza
 * hacia una de las ocho opciones (arriba, arriba a la derecha, derecha... en
 * el sentido de las agujas del reloj), Tiresias la dice al llegar a ella y al
 * levantar el dedo se hace. Levantarlo en el centro cierra el menú sin hacer
 * nada.
 *
 * Mientras está abierto, los toques llegan directos al menú (la región de paso
 * de la exploración táctil, Android 11 o posterior): lo pone y lo quita
 * [alPaso]. La opción se hace después de cerrar el menú, para que nada de él
 * quede encima de lo que la opción vaya a tocar (el fallo de Backtalk 283).
 */
class MenuCircular(
	private val servicio: AccessibilityService,
	/** Dice el nombre de una opción (o el aviso del menú). */
	private val decir: (String) -> Unit,
	/** Suena al pasar a otra opción. */
	private val alPasar: () -> Unit,
	/** Pone (true) o quita (false) el paso directo de los toques al menú. */
	private val alPaso: (Boolean) -> Unit,
) {
	private val principal = Handler(Looper.getMainLooper())
	private var vista: Vista? = null

	val abierto: Boolean get() = vista != null

	/** Abre el menú con [opciones] (las ocho primeras) y [aviso]; [alElegir] recibe la elegida. */
	fun abrir(opciones: List<String>, aviso: String, alElegir: (Int) -> Unit) = principal.post {
		quitar()
		val wm = servicio.getSystemService(WindowManager::class.java) ?: return@post
		val v = Vista(opciones.take(OPCIONES), alElegir)
		val params = WindowManager.LayoutParams(
			WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
			PixelFormat.TRANSLUCENT,
		)
		try {
			// Primero el paso directo de los toques al menú: si el dedo baja enseguida, ya llega a él
			alPaso(true)
			wm.addView(v, params)
			vista = v
			decir(aviso)
			principal.postDelayed(cierreSinUso, ESPERA_MS)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo abrir el menú circular", e)
		}
	}

	fun cerrar() = principal.post { quitar() }

	private val cierreSinUso = Runnable { quitar() }

	private fun quitar() {
		principal.removeCallbacks(cierreSinUso)
		val v = vista ?: return
		vista = null
		runCatching { servicio.getSystemService(WindowManager::class.java)?.removeView(v) }
			.onFailure { Registro.w(ETIQUETA, "No se pudo quitar el menú circular", it) }
		alPaso(false)
	}

	@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
	private inner class Vista(private val opciones: List<String>, private val alElegir: (Int) -> Unit) : View(servicio) {
		private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.WHITE
			textAlign = Paint.Align.CENTER
			textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
		}
		private val elegido = Paint(texto).apply { color = Color.YELLOW; isFakeBoldText = true }
		private val aro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.WHITE
			style = Paint.Style.STROKE
			strokeWidth = 3f * resources.displayMetrics.density
		}
		private var cx = -1f
		private var cy = -1f
		private var actual = -1

		init {
			setBackgroundColor(Color.argb(200, 0, 0, 0))
			importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
		}

		override fun onTouchEvent(e: MotionEvent): Boolean {
			when (e.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					principal.removeCallbacks(cierreSinUso)
					cx = e.x
					cy = e.y
					actual = -1
					invalidate()
				}
				MotionEvent.ACTION_MOVE -> {
					val i = opcionEn(e.x, e.y)
					if (i != actual) {
						actual = i
						if (i >= 0) {
							alPasar()
							decir(opciones[i])
						}
						invalidate()
					}
				}
				MotionEvent.ACTION_UP -> {
					val i = opcionEn(e.x, e.y)
					quitar()
					if (i >= 0) principal.postDelayed({ alElegir(i) }, RETARDO_ELEGIR_MS)
				}
				MotionEvent.ACTION_CANCEL -> quitar()
			}
			return true
		}

		/** La opción hacia la que apunta el dedo, o -1 si sigue en el centro. */
		private fun opcionEn(x: Float, y: Float): Int {
			val radio = RADIO_DP * resources.displayMetrics.density
			val dx = x - cx
			val dy = y - cy
			if (cx < 0 || hypot(dx, dy) < radio) return -1
			return indiceDeSector(dx, dy, opciones.size)
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			if (cx < 0) return
			val distancia = 120f * resources.displayMetrics.density
			canvas.drawCircle(cx, cy, RADIO_DP * resources.displayMetrics.density, aro)
			for ((i, nombre) in opciones.withIndex()) {
				// La opción 0 arriba y las demás en el sentido de las agujas del reloj
				val angulo = -PI / 2 + i * 2 * PI / OPCIONES
				val x = cx + (distancia * cos(angulo)).toFloat()
				val y = cy + (distancia * sin(angulo)).toFloat()
				canvas.drawText(nombre, x, y, if (i == actual) elegido else texto)
			}
		}
	}

	companion object {
		private const val ETIQUETA = "MenuCircular"
		const val OPCIONES = 8
		private const val RADIO_DP = 48f
		/** Sin tocar en este tiempo, el menú se cierra solo. */
		private const val ESPERA_MS = 15_000L
		private const val RETARDO_ELEGIR_MS = 250L

		/**
		 * El sector (de [total], como mucho ocho) hacia el que apunta el
		 * desplazamiento ([dx], [dy], con la y hacia abajo): 0 arriba y luego en
		 * el sentido de las agujas del reloj; -1 si ese sector no tiene opción.
		 */
		fun indiceDeSector(dx: Float, dy: Float, total: Int): Int {
			// Ángulo desde arriba, en el sentido de las agujas del reloj, de 0 a 2π
			var angulo = atan2(dx.toDouble(), -dy.toDouble())
			if (angulo < 0) angulo += 2 * PI
			val sector = ((angulo + PI / OPCIONES) / (2 * PI / OPCIONES)).toInt() % OPCIONES
			return if (sector < total) sector else -1
		}
	}
}
