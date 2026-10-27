package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NombreIconoTest {
	@Test
	fun respuestasDeLaIa() {
		assertEquals("Enviar", NombreIcono.deIa("Enviar"))
		assertEquals("Buscar", NombreIcono.deIa("«buscar».\n"))
		assertEquals("Atrás", NombreIcono.deIa("Botón: atrás"))
		assertEquals("Añadir contacto", NombreIcono.deIa("**Añadir contacto**"))
		assertNull(NombreIcono.deIa("No lo sé"))
		assertNull(NombreIcono.deIa("Desconocido"))
		assertNull(NombreIcono.deIa(""))
		// Una frase entera no es un nombre de botón
		assertNull(NombreIcono.deIa("Parece un botón que sirve para compartir la foto con tus contactos"))
	}

	@Test
	fun textoDelOcr() {
		assertEquals("OK", NombreIcono.deOcr(listOf("OK" to 0.95f)))
		assertNull(NombreIcono.deOcr(listOf("Ok" to 0.3f)))
		assertNull(NombreIcono.deOcr(listOf("·" to 0.9f)))
		assertEquals("Ver todo", NombreIcono.deOcr(listOf("Ver" to 0.9f, "todo" to 0.8f)))
	}
}
