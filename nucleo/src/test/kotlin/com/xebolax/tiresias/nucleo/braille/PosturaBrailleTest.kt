package com.xebolax.tiresias.nucleo.braille

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosturaBrailleTest {
	// Una pantalla en vertical de 1080 × 2400
	private val ancho = 1080f
	private val alto = 2400f

	/**
	 * Pantalla hacia fuera, móvil en horizontal con su parte de arriba en la mano izquierda: la mitad de arriba
	 * de la pantalla (en vertical) es la mano izquierda, y su borde izquierdo queda arriba.
	 */
	@Test
	fun conLaPantallaHaciaFueraLaManoIzquierdaEsElUnoDosTres() {
		assertEquals(1, PosturaBraille.punto(100f, 300f, ancho, alto, false))
		assertEquals(2, PosturaBraille.punto(540f, 300f, ancho, alto, false))
		assertEquals(3, PosturaBraille.punto(980f, 300f, ancho, alto, false))
		assertEquals(4, PosturaBraille.punto(100f, 2100f, ancho, alto, false))
		assertEquals(5, PosturaBraille.punto(540f, 2100f, ancho, alto, false))
		assertEquals(6, PosturaBraille.punto(980f, 2100f, ancho, alto, false))
	}

	/** Sobre la mesa, los seis en fila de izquierda a derecha: 3, 2, 1, 4, 5, 6 (a lo largo del móvil). */
	@Test
	fun sobreLaMesaVanEnFilaComoUnaPerkins() {
		val fila = (0 until 6).map { PosturaBraille.punto(540f, alto / 12f + it * alto / 6f, ancho, alto, true) }
		assertEquals(listOf(3, 2, 1, 4, 5, 6), fila)
	}

	/** En vertical, mover el dedo hacia abajo de la pantalla es, en la mano, moverlo hacia la derecha. */
	@Test
	fun losDeslizamientosSonLosDeLaPersona() {
		assertEquals(Sentido.DERECHA, PosturaBraille.sentido(0f, 400f, ancho, alto, false, 100f))
		assertEquals(Sentido.IZQUIERDA, PosturaBraille.sentido(0f, -400f, ancho, alto, false, 100f))
		// Pantalla hacia fuera: el borde izquierdo de la pantalla queda arriba
		assertEquals(Sentido.ARRIBA, PosturaBraille.sentido(-400f, 0f, ancho, alto, false, 100f))
		assertEquals(Sentido.ABAJO, PosturaBraille.sentido(400f, 0f, ancho, alto, false, 100f))
		// Sobre la mesa, al revés: el borde izquierdo queda hacia la persona
		assertEquals(Sentido.ABAJO, PosturaBraille.sentido(-400f, 0f, ancho, alto, true, 100f))
		assertEquals(Sentido.DERECHA, PosturaBraille.sentido(0f, 400f, ancho, alto, true, 100f))
	}

	/** Ya en horizontal: con la pantalla hacia fuera la persona la ve por detrás (izquierda y derecha cambian). */
	@Test
	fun enHorizontalConLaPantallaHaciaFueraSeVePorDetras() {
		assertEquals(Sentido.IZQUIERDA, PosturaBraille.sentido(400f, 0f, alto, ancho, false, 100f))
		assertEquals(Sentido.DERECHA, PosturaBraille.sentido(400f, 0f, alto, ancho, true, 100f))
		assertEquals(Sentido.ARRIBA, PosturaBraille.sentido(0f, -400f, alto, ancho, false, 100f))
		// La mitad derecha de la pantalla es la mano izquierda de la persona
		assertEquals(1, PosturaBraille.punto(2300f, 100f, alto, ancho, false))
		assertEquals(4, PosturaBraille.punto(100f, 100f, alto, ancho, false))
	}

	/** Lo que no llega al umbral o va en diagonal no es un gesto (es un punto). */
	@Test
	fun loCortoOEnDiagonalNoEsGesto() {
		assertNull(PosturaBraille.sentido(0f, 50f, ancho, alto, false, 100f))
		assertNull(PosturaBraille.sentido(300f, 300f, ancho, alto, false, 100f))
	}
}
