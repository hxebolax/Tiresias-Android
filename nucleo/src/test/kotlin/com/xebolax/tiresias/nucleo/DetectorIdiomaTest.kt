package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorIdiomaTest {
	@Test
	fun distingueEspanolEIngles() {
		assertEquals("es", DetectorIdioma.detectar("Hola, te escribo para decirte que mañana no podré ir a la reunión de las diez."))
		assertEquals("en", DetectorIdioma.detectar("Hi, I am writing to tell you that I will not be able to come to the meeting tomorrow."))
		assertEquals("en", DetectorIdioma.detectar("The quick brown fox jumps over the lazy dog and then it goes back to the forest."))
		assertEquals("es", DetectorIdioma.detectar("El perro corre por el parque y luego vuelve a casa con su dueño."))
	}

	@Test
	fun noDecideConPocoONadaClaro() {
		assertNull(DetectorIdioma.detectar("Aceptar"))
		assertNull(DetectorIdioma.detectar("WhatsApp, 3 mensajes nuevos"))
		assertNull(DetectorIdioma.detectar("12:45 · 23 °C · 87 %"))
		// Nombres y marcas sin palabras de ningún idioma
		assertNull(DetectorIdioma.detectar("Samsung Galaxy S25 Ultra Titanium Black 512 GB"))
	}
}
