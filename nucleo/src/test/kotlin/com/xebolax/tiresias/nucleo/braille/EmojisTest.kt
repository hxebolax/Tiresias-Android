package com.xebolax.tiresias.nucleo.braille

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojisTest {
	@Test
	fun cadaCategoriaTieneEntre40Y120SinVacios() {
		for (c in CategoriaEmoji.entries) {
			val lista = Emojis.de(c)
			assertTrue("$c tiene ${lista.size}", lista.size in 40..120)
			for (e in lista) {
				assertFalse("Vacío en $c", e.isBlank())
				assertFalse("Espacio dentro de «$e» en $c", e.any { it.isWhitespace() })
			}
		}
	}

	@Test
	fun ningunoSeRepiteNiDentroNiEntreCategorias() {
		val vistos = HashMap<String, CategoriaEmoji>()
		for (c in CategoriaEmoji.entries) {
			for (e in Emojis.de(c)) {
				val antes = vistos.put(e, c)
				assertEquals("«$e» repetido en $antes y $c", null, antes)
			}
		}
	}

	@Test
	fun sinTonosDePiel() {
		for (c in CategoriaEmoji.entries) for (e in Emojis.de(c)) {
			val tonos = e.codePoints().filter { it in 0x1F3FB..0x1F3FF }.count()
			assertEquals("«$e» lleva tono de piel", 0L, tonos)
		}
	}

	@Test
	fun lasDeTextoLlevanElSelectorDeEmoji() {
		assertTrue("❤️" in Emojis.de(CategoriaEmoji.CORAZONES))
		assertTrue("☺️" in Emojis.de(CategoriaEmoji.CARAS))
		assertTrue("✈️" in Emojis.de(CategoriaEmoji.VIAJES))
		assertTrue("1️⃣" in Emojis.de(CategoriaEmoji.SIMBOLOS))
	}

	@Test
	fun banderasDeLosPaisesPedidos() {
		val banderas = Emojis.de(CategoriaEmoji.BANDERAS)
		fun bandera(codigo: String) = codigo.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
		for (p in listOf("ES", "MX", "AR", "CO", "PE", "VE", "CL", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR", "GQ", "EU", "US", "GB", "FR", "DE", "IT", "PT", "BR")) {
			assertTrue("Falta la bandera $p", bandera(p) in banderas)
		}
	}
}
