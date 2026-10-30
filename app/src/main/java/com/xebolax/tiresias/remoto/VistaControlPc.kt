package com.xebolax.tiresias.remoto

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.xebolax.tiresias.braille.DireccionBraille
import kotlin.math.abs
import kotlin.math.hypot

/** Lo que se puede hacer con los dedos en la pantalla de control de un PC. */
enum class TipoGestoPc { TOQUE, DOBLE_TOQUE, TRIPLE_TOQUE, MANTENER, DESLIZAR }

/**
 * Un gesto en la pantalla de control: cuántos [dedos] (1 a 4), qué [tipo] y,
 * al deslizar, hacia dónde.
 */
data class GestoPc(val dedos: Int, val tipo: TipoGestoPc, val direccion: DireccionBraille? = null) {
	/** La clave con la que se nombra en las pruebas y en los ajustes: «2_DESLIZAR_DERECHA», «1_DOBLE_TOQUE». */
	val clave: String get() = if (tipo == TipoGestoPc.DESLIZAR) "${dedos}_DESLIZAR_${direccion?.name}" else "${dedos}_${tipo.name}"

	companion object {
		fun deClave(clave: String): GestoPc? {
			val partes = clave.uppercase().split('_')
			val dedos = partes.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..4 } ?: return null
			if (partes.getOrNull(1) == "DESLIZAR") {
				val d = DireccionBraille.entries.firstOrNull { it.name == partes.getOrNull(2) } ?: return null
				return GestoPc(dedos, TipoGestoPc.DESLIZAR, d)
			}
			val tipo = TipoGestoPc.entries.firstOrNull { it.name == partes.drop(1).joinToString("_") } ?: return null
			return if (tipo == TipoGestoPc.DESLIZAR) null else GestoPc(dedos, tipo)
		}
	}
}

/**
 * La vista a pantalla completa de la pantalla de control de un PC: recibe los
 * toques tal cual (sin la exploración táctil) y los convierte en gestos de uno
 * a cuatro dedos: toque, doble y triple toque, mantener y deslizar en las cuatro
 * direcciones. Para quien ve un poco, dibuja arriba el rótulo y en el centro la
 * última orden mandada.
 */
@SuppressLint("ViewConstructor")
class VistaControlPc(
	contexto: Context,
	private val alGesto: (GestoPc) -> Unit,
	private val alApoyar: () -> Unit,
) : View(contexto) {
	var rotulo: String = ""
		set(v) { field = v; postInvalidate() }
	var mensaje: String = ""
		set(v) { field = v; postInvalidate() }

	private val principal = Handler(Looper.getMainLooper())
	private val holgura = ViewConfiguration.get(contexto).scaledTouchSlop * 2f
	private val inicioX = HashMap<Int, Float>()
	private val inicioY = HashMap<Int, Float>()
	private var dedosMax = 0
	private var momento = 0L
	private var movido = false
	private var mantenido = false
	/** Los toques seguidos que llevamos (para el doble y el triple) y con cuántos dedos. */
	private var toques = 0
	private var toquesDedos = 0

	private val mantener = Runnable {
		if (movido || dedosMax == 0) return@Runnable
		mantenido = true
		toques = 0
		performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
		alGesto(GestoPc(dedosMax, TipoGestoPc.MANTENER))
	}
	private val finToques = Runnable { soltarToques() }

	private val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textSize = 22 * contexto.resources.displayMetrics.scaledDensity
	}
	private val pinturaMensaje = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textSize = 30 * contexto.resources.displayMetrics.scaledDensity
	}

	init {
		setBackgroundColor(Color.BLACK)
		isFocusable = false
		// Los toques no pasan por el lector: la pantalla no es un elemento que explorar
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
	}

	private fun soltarToques() {
		principal.removeCallbacks(finToques)
		if (toques == 0) return
		val tipo = when (toques) {
			1 -> TipoGestoPc.TOQUE
			2 -> TipoGestoPc.DOBLE_TOQUE
			else -> TipoGestoPc.TRIPLE_TOQUE
		}
		val dedos = toquesDedos
		toques = 0
		alGesto(GestoPc(dedos, tipo))
	}

	override fun onTouchEvent(e: MotionEvent): Boolean {
		when (e.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
				if (e.actionMasked == MotionEvent.ACTION_DOWN) {
					dedosMax = 0
					movido = false
					mantenido = false
					momento = SystemClock.uptimeMillis()
					inicioX.clear()
					inicioY.clear()
				}
				val i = e.actionIndex
				val id = e.getPointerId(i)
				inicioX[id] = e.getX(i)
				inicioY[id] = e.getY(i)
				dedosMax = maxOf(dedosMax, e.pointerCount)
				alApoyar()
				// Mantener se cuenta desde el último dedo que baja, sin moverse
				principal.removeCallbacks(mantener)
				principal.postDelayed(mantener, MANTENER_MS)
			}
			MotionEvent.ACTION_MOVE -> if (!movido) {
				for (i in 0 until e.pointerCount) {
					val id = e.getPointerId(i)
					val sx = inicioX[id] ?: continue
					val sy = inicioY[id] ?: continue
					if (hypot(e.getX(i) - sx, e.getY(i) - sy) > holgura) {
						movido = true
						principal.removeCallbacks(mantener)
						break
					}
				}
			}
			MotionEvent.ACTION_UP -> {
				principal.removeCallbacks(mantener)
				if (!mantenido) {
					val id = e.getPointerId(e.actionIndex)
					val dx = e.x - (inicioX[id] ?: e.x)
					val dy = e.y - (inicioY[id] ?: e.y)
					val umbral = (minOf(width, height) * 0.12f).coerceAtLeast(holgura * 3)
					val dedos = dedosMax.coerceIn(1, 4)
					when {
						abs(dx) > umbral && abs(dx) > abs(dy) -> {
							soltarToques()
							alGesto(GestoPc(dedos, TipoGestoPc.DESLIZAR, if (dx < 0) DireccionBraille.IZQUIERDA else DireccionBraille.DERECHA))
						}
						abs(dy) > umbral && abs(dy) > abs(dx) -> {
							soltarToques()
							alGesto(GestoPc(dedos, TipoGestoPc.DESLIZAR, if (dy < 0) DireccionBraille.ARRIBA else DireccionBraille.ABAJO))
						}
						!movido && SystemClock.uptimeMillis() - momento < TOQUE_MAXIMO_MS -> {
							// Un toque con otro número de dedos cierra la cuenta anterior
							if (toques > 0 && toquesDedos != dedos) soltarToques()
							toques++
							toquesDedos = dedos
							principal.removeCallbacks(finToques)
							if (toques >= 3) soltarToques() else principal.postDelayed(finToques, ENTRE_TOQUES_MS)
						}
					}
				}
				dedosMax = 0
				inicioX.clear()
				inicioY.clear()
			}
			MotionEvent.ACTION_CANCEL -> {
				principal.removeCallbacks(mantener)
				dedosMax = 0
				inicioX.clear()
				inicioY.clear()
			}
		}
		return true
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val margen = 16 * resources.displayMetrics.density
		canvas.drawText(rotulo, margen, margen + pintura.textSize, pintura)
		if (mensaje.isBlank()) return
		val ancho = (width - 2 * margen).toInt().coerceAtLeast(1)
		val capa = StaticLayout.Builder.obtain(mensaje, 0, mensaje.length, pinturaMensaje, ancho)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.build()
		canvas.save()
		canvas.translate(margen, (height - capa.height) / 2f)
		capa.draw(canvas)
		canvas.restore()
	}

	private companion object {
		/** Lo que hay que dejar el dedo quieto para «mantener». */
		const val MANTENER_MS = 600L
		/** Un toque dura menos que esto (si no, no es un toque). */
		const val TOQUE_MAXIMO_MS = 400L
		/** Lo que se espera a otro toque para el doble y el triple. */
		const val ENTRE_TOQUES_MS = 320L
	}
}
