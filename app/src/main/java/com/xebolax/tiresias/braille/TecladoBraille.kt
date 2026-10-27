package com.xebolax.tiresias.braille

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import kotlin.math.abs

/**
 * Teclado braille en pantalla de Tiresias (método de entrada del sistema).
 *
 * Se sostiene el móvil con la pantalla hacia fuera, o sobre la mesa, y se
 * apoyan los dedos: los puntos 1, 2 y 3 a un lado y 4, 5 y 6 al otro. Al
 * levantar todos los dedos se escribe la combinación (signografía española de
 * la ONCE, grado 1: letras con tilde, ñ, ü, números con el signo de número y
 * mayúsculas con el signo de mayúscula).
 *
 * Gestos con un dedo: deslizar a la izquierda borra, a la derecha escribe un
 * espacio; con dos dedos hacia abajo, intro (o la acción del campo: buscar,
 * enviar); con dos dedos hacia arriba, cambia al teclado anterior.
 *
 * La vista explica lo que pasa con anuncios de accesibilidad para que el
 * lector (Tiresias u otro) diga cada carácter escrito.
 *
 * Con «Puntos según cómo sujetas el móvil», el sensor de gravedad decide si
 * van en espejo: tumbado sobre la mesa (pantalla hacia ti), en espejo; de pie
 * con la pantalla hacia fuera, normales. Como el teclado braille de Gboard
 * (modos «sobre la mesa» y «pantalla hacia fuera»; Backtalk 12, 20 y 73).
 */
class TecladoBraille : InputMethodService(), SensorEventListener {
	private var vista: VistaBraille? = null
	private var mayuscula = false
	private var numero = false
	/** Lo que dice el sensor: en espejo (sobre la mesa) o no; null mientras no se sabe. */
	@Volatile private var espejoSensor: Boolean? = null
	private var sensorPuesto = false

	override fun onCreateInputView(): View = VistaBraille(this).also { vista = it }

	override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
		super.onStartInputView(info, restarting)
		mayuscula = false
		numero = false
		vista?.anunciar(textos().getString(R.string.braille_teclado_listo))
		ponerSensor(Ajustes(this).brailleSensor)
	}

	override fun onFinishInputView(finishingInput: Boolean) {
		ponerSensor(false)
		super.onFinishInputView(finishingInput)
	}

	override fun onDestroy() {
		ponerSensor(false)
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
			vista?.anunciar(textos().getString(R.string.braille_sin_sensor))
			return
		}
		bloqueado = !bloqueado
		vista?.anunciar(textos().getString(if (bloqueado) R.string.braille_orientacion_bloqueada else R.string.braille_orientacion_libre))
	}

	override fun onSensorChanged(e: SensorEvent) {
		if (e.values.size < 3 || bloqueado) return
		val anterior = espejoSensor
		val ahora = espejoPorGravedad(e.values[2], SensorManager.GRAVITY_EARTH, anterior) ?: return
		if (ahora == anterior) return
		espejoSensor = ahora
		// Al cambiar de postura (no la primera vez, que ya la dice el aviso de listo), se dice cómo quedan los puntos
		if (anterior != null) vista?.anunciar(textos().getString(if (ahora) R.string.braille_sobre_mesa else R.string.braille_pantalla_fuera))
	}

	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

	/** Si los puntos van en espejo ahora: lo que diga el sensor, si está puesto y lo sabe; si no, el ajuste. */
	fun espejo(): Boolean = espejoSensor?.takeIf { sensorPuesto } ?: Ajustes(this).brailleEspejo

	private fun textos(): Context = Idioma.envolver(this)

	/** Llega una combinación de puntos (bits 0 a 5 = puntos 1 a 6). */
	fun alPuntos(puntos: Int) {
		val ic = currentInputConnection ?: return
		when (puntos) {
			SIGNO_NUMERO -> {
				numero = true
				vista?.anunciar(textos().getString(R.string.braille_numero))
				return
			}
			SIGNO_MAYUSCULA -> {
				mayuscula = true
				vista?.anunciar(textos().getString(R.string.braille_mayuscula))
				return
			}
		}
		val c: String? = if (numero) NUMEROS[puntos] ?: run { numero = false; LETRAS[puntos] } else LETRAS[puntos]
		if (c == null) {
			vista?.anunciar(textos().getString(R.string.braille_desconocido, describir(puntos)))
			return
		}
		val escrito = if (mayuscula) c.uppercase() else c
		mayuscula = false
		ic.commitText(escrito, 1)
		palabra.append(escrito)
		// El eco: la letra (lo normal), nada, o la palabra al terminarla con un espacio
		if (Ajustes(this).brailleEco == ECO_LETRAS) vista?.anunciar(escrito)
	}

	fun borrar() {
		val ic = currentInputConnection ?: return
		val antes = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
		ic.deleteSurroundingText(1, 0)
		vista?.anunciar(textos().getString(R.string.edicion_borrado, antes.ifEmpty { "" }))
	}

	/** Lo escrito desde el último espacio (para el eco de palabras). */
	private val palabra = StringBuilder()

	fun espacio() {
		numero = false
		currentInputConnection?.commitText(" ", 1)
		val eco = Ajustes(this).brailleEco
		when {
			eco == ECO_PALABRAS && palabra.isNotEmpty() -> vista?.anunciar(palabra.toString())
			eco != ECO_NADA -> vista?.anunciar(textos().getString(R.string.voz_espacio))
		}
		palabra.clear()
	}

	fun intro() {
		val ic = currentInputConnection ?: return
		val accion = (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
		if (accion != EditorInfo.IME_ACTION_NONE && accion != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(accion)
		else ic.commitText("\n", 1)
	}

	fun cambiarTeclado() {
		val imm = getSystemService(InputMethodManager::class.java)
		// Volver al teclado anterior solo existe desde Android 9; antes, la lista de teclados
		val cambiado = Build.VERSION.SDK_INT >= 28 && switchToPreviousInputMethod()
		if (!cambiado) imm?.showInputMethodPicker()
	}

	private fun describir(p: Int): String = (0 until 6).filter { p and (1 shl it) != 0 }.joinToString(" ") { (it + 1).toString() }

	/**
	 * La superficie del teclado: toda la vista recibe los dedos. Los puntos se
	 * asignan por posición: la mitad de arriba son 1, 2 y 3 (de izquierda a
	 * derecha) y la de abajo 4, 5 y 6, que es como quedan los dedos con el
	 * móvil en horizontal y la pantalla hacia fuera.
	 */
	@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
	class VistaBraille(private val ime: TecladoBraille) : View(ime) {
		private val pintura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 48f }
		private var puntos = 0
		private var dedosMax = 0
		private val inicioX = HashMap<Int, Float>()
		private val inicioY = HashMap<Int, Float>()
		private var momento = 0L

		init {
			setBackgroundColor(Color.BLACK)
			contentDescription = ime.textos().getString(R.string.braille_superficie)
			importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val alto = (resources.displayMetrics.heightPixels * 0.6f).toInt()
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), alto)
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			canvas.drawText("⠿ Tiresias", 32f, 64f, pintura)
		}

		fun anunciar(texto: String) {
			if (texto.isNotEmpty()) @Suppress("DEPRECATION") announceForAccessibility(texto)
		}

		override fun onTouchEvent(e: MotionEvent): Boolean {
			when (e.actionMasked) {
				MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
					if (e.actionMasked == MotionEvent.ACTION_DOWN) {
						puntos = 0
						dedosMax = 0
						momento = SystemClock.uptimeMillis()
					}
					val i = e.actionIndex
					val id = e.getPointerId(i)
					inicioX[id] = e.getX(i)
					inicioY[id] = e.getY(i)
					puntos = puntos or punto(e.getX(i), e.getY(i))
					val a = Ajustes(ime)
					if (a.brailleVibrarPuntos) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
					if (a.brailleSonarPuntos) ime.getSystemService(android.media.AudioManager::class.java)?.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
					dedosMax = maxOf(dedosMax, e.pointerCount)
				}
				MotionEvent.ACTION_UP -> {
					val id = e.getPointerId(e.actionIndex)
					val dx = e.x - (inicioX[id] ?: e.x)
					val dy = e.y - (inicioY[id] ?: e.y)
					val umbral = width * 0.25f
					// Con los puntos en espejo, también giran borrar y espacio
					val haciaIzquierda = (dx < 0) != ime.espejo()
					when {
						dedosMax == 1 && abs(dx) > umbral && abs(dx) > abs(dy) -> if (haciaIzquierda) ime.borrar() else ime.espacio()
						dedosMax >= 3 && abs(dy) > height * 0.2f && abs(dy) > abs(dx) && dy < 0 -> ime.alternarBloqueo()
						dedosMax == 2 && abs(dy) > height * 0.2f && abs(dy) > abs(dx) -> if (dy > 0) ime.intro() else ime.cambiarTeclado()
						puntos != 0 -> ime.alPuntos(puntos)
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

		/** El punto (bit) de una posición: tres columnas por fila; fila de arriba 1, 2, 3 y de abajo 4, 5, 6. */
		private fun punto(x: Float, y: Float): Int {
			val leida = (x / (width / 3f)).toInt().coerceIn(0, 2)
			// En espejo (la pantalla mirando al otro lado): el punto 1 a la derecha
			val columna = if (ime.espejo()) 2 - leida else leida
			val abajo = y > height / 2f
			return 1 shl (columna + if (abajo) 3 else 0)
		}
	}

	companion object {
		const val ECO_LETRAS = "letras"
		const val ECO_PALABRAS = "palabras"
		const val ECO_NADA = "nada"

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

		private val NUMEROS: Map<Int, String> = mapOf(
			p(1) to "1", p(1, 2) to "2", p(1, 4) to "3", p(1, 4, 5) to "4", p(1, 5) to "5",
			p(1, 2, 4) to "6", p(1, 2, 4, 5) to "7", p(1, 2, 5) to "8", p(2, 4) to "9", p(2, 4, 5) to "0",
			p(3) to ".", p(2) to ",",
		)
	}
}
