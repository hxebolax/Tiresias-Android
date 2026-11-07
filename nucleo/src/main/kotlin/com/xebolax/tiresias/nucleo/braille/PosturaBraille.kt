package com.xebolax.tiresias.nucleo.braille

import kotlin.math.abs

/** Hacia dónde mueve el dedo la persona, tal como lo nota ella (no como lo ve la pantalla). */
enum class Sentido { IZQUIERDA, DERECHA, ARRIBA, ABAJO }

/**
 * La geometría de la superficie braille según cómo se sujeta el móvil, como el
 * teclado braille de TalkBack: los puntos y los deslizamientos se piensan con
 * el móvil en horizontal, visto por la persona que escribe.
 *
 * - **Pantalla hacia fuera** (de fábrica): el móvil en horizontal, con la
 *   pantalla mirando hacia fuera y la parte de arriba del móvil (la del
 *   auricular y la cámara) en la mano izquierda. Los dedos de la mano
 *   izquierda son los puntos 1, 2 y 3 (índice, corazón y anular, de arriba
 *   abajo) y los de la derecha, 4, 5 y 6.
 * - **Sobre la mesa** (en espejo): el móvil tumbado en horizontal, con la
 *   pantalla hacia arriba y su parte de arriba a la izquierda. Los seis dedos
 *   en fila, como en una máquina Perkins: de izquierda a derecha 3, 2, 1, 4, 5 y 6.
 *
 * Si la pantalla está en vertical (lo normal: el giro se bloquea mientras está
 * la superficie), las coordenadas se pasan a las de la persona: lo que para la
 * pantalla es «hacia abajo» es, con el móvil así cogido, «hacia la derecha».
 * Así «un dedo a la derecha» es mover el dedo hacia la derecha de verdad (antes
 * había que moverlo a lo largo del móvil, que en la mano iba de arriba abajo).
 */
object PosturaBraille {
	/** Un punto en las coordenadas de la persona: [x] hacia su derecha, [y] hacia abajo, con su ancho y alto. */
	data class Plano(val x: Float, val y: Float, val ancho: Float, val alto: Float)

	/**
	 * Pasa un punto de la pantalla ([x], [y] en una superficie de [ancho] × [alto]) a las coordenadas de la
	 * persona. [espejo]: sobre la mesa (la pantalla mirando a la persona); si no, pantalla hacia fuera.
	 */
	fun aPersona(x: Float, y: Float, ancho: Float, alto: Float, espejo: Boolean): Plano {
		if (alto > ancho) {
			// En vertical, con la parte de arriba del móvil a la izquierda de la persona: lo que para la
			// pantalla baja, para ella va a la derecha. Con la pantalla hacia fuera, el borde izquierdo de la
			// pantalla queda arriba; sobre la mesa, abajo (hacia la persona)
			return if (espejo) Plano(y, ancho - x, alto, ancho) else Plano(y, x, alto, ancho)
		}
		// Ya en horizontal: sobre la mesa se ve tal cual; con la pantalla hacia fuera, la persona la ve por detrás
		return if (espejo) Plano(x, y, ancho, alto) else Plano(ancho - x, y, ancho, alto)
	}

	/** El punto braille (1 a 6) de una posición, sin calibrar. */
	fun punto(x: Float, y: Float, ancho: Float, alto: Float, espejo: Boolean): Int {
		val p = aPersona(x, y, ancho, alto, espejo)
		if (espejo) {
			// En fila: 3, 2, 1 con la mano izquierda y 4, 5, 6 con la derecha
			val sexto = (p.x / (p.ancho / 6f)).toInt().coerceIn(0, 5)
			return FILA_MESA[sexto]
		}
		val derecha = p.x >= p.ancho / 2f
		val fila = (p.y / (p.alto / 3f)).toInt().coerceIn(0, 2)
		return (if (derecha) 4 else 1) + fila
	}

	/**
	 * El sentido de un deslizamiento de ([dx], [dy]) en la pantalla, o null si no llega a [umbral] o no está
	 * claro (hay que moverse al menos vez y media más en un sentido que en el otro).
	 */
	fun sentido(dx: Float, dy: Float, ancho: Float, alto: Float, espejo: Boolean, umbral: Float): Sentido? {
		// El desplazamiento visto por la persona: la diferencia entre dos puntos pasados a sus coordenadas
		val origen = aPersona(0f, 0f, ancho, alto, espejo)
		val fin = aPersona(dx, dy, ancho, alto, espejo)
		val px = fin.x - origen.x
		val py = fin.y - origen.y
		return when {
			abs(px) > umbral && abs(px) > abs(py) * 1.5f -> if (px < 0) Sentido.IZQUIERDA else Sentido.DERECHA
			abs(py) > umbral && abs(py) > abs(px) * 1.5f -> if (py < 0) Sentido.ARRIBA else Sentido.ABAJO
			else -> null
		}
	}

	private val FILA_MESA = intArrayOf(3, 2, 1, 4, 5, 6)
}
