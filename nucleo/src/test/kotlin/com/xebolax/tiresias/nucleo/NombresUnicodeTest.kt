package com.xebolax.tiresias.nucleo

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class NombresUnicodeTest {
	@Test
	fun losNombresDeGboardDanSuSimbolo() {
		assertEquals("¥", NombresUnicode.caracter("Yen sign"))
		assertEquals("¿", NombresUnicode.caracter("inverted question mark"))
		assertEquals("@", NombresUnicode.caracter("commercial at"))
		assertEquals("&", NombresUnicode.caracter("Ampersand"))
		assertEquals("_", NombresUnicode.caracter("low line"))
		assertEquals("€", NombresUnicode.caracter("euro sign"))
		assertEquals("«", NombresUnicode.caracter("left-pointing double angle quotation mark"))
		assertEquals("•", NombresUnicode.caracter("bullet"))
		assertEquals("→", NombresUnicode.caracter("rightwards arrow"))
		assertEquals("\\", NombresUnicode.caracter("reverse solidus"))
	}

	@Test
	fun loQueNoEsUnNombreUnicodeDaNull() {
		assertNull(NombresUnicode.caracter("u"))
		assertNull(NombresUnicode.caracter("Shift enabled"))
		assertNull(NombresUnicode.caracter(""))
	}
}
