package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La voz de un motor del sistema elegida para cada idioma, y lo guardado por versiones anteriores. */
class VocesPorIdiomaTest {

	@Test
	fun unaVozPorIdioma() {
		var g = VocesPorIdioma.poner("", "es", "es-es-x-eea-local")
		g = VocesPorIdioma.poner(g, "EN", "en-gb-x-rjs-local")
		assertEquals("es=es-es-x-eea-local|en=en-gb-x-rjs-local", g)
		assertEquals("en-gb-x-rjs-local", VocesPorIdioma.para(g, "en") { false })
		assertEquals("es-es-x-eea-local", VocesPorIdioma.para(g, "es") { false })
		assertNull(VocesPorIdioma.para(g, "fr") { true })
		// Quitarla: vuelve la del motor para ese idioma, y la otra se queda
		g = VocesPorIdioma.poner(g, "es", null)
		assertEquals("en=en-gb-x-rjs-local", g)
	}

	@Test
	fun loGuardadoAntesSinIdioma() {
		// Solo el nombre: vale para el idioma de esa voz (lo dice el motor)
		assertEquals("Jorge", VocesPorIdioma.para("Jorge", "es") { it == "Jorge" })
		assertNull(VocesPorIdioma.para("Jorge", "en") { false })
		// Al elegir otra, lo antiguo se guarda con su idioma si el nombre lo dice
		assertEquals("es=es-es-x-eea-local|en=en-us-x-sfg-local", VocesPorIdioma.poner("es-es-x-eea-local", "en", "en-us-x-sfg-local"))
		// Y si no lo dice, se deja
		assertEquals("en=en-us-x-sfg-local", VocesPorIdioma.poner("Jorge", "en", "en-us-x-sfg-local"))
	}

	@Test
	fun loMalEscritoNoRompeNada() {
		assertEquals(emptyMap<String, String>(), VocesPorIdioma.leer("  "))
		assertEquals(mapOf("es" to "a"), VocesPorIdioma.leer("es=a|=b|fr=|x"))
		assertEquals("es", VocesPorIdioma.idiomaDeNombre("es_ES-voz"))
		assertNull(VocesPorIdioma.idiomaDeNombre("Jorge"))
	}
}
