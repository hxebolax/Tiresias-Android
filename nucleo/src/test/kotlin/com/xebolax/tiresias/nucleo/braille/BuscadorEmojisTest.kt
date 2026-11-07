package com.xebolax.tiresias.nucleo.braille

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuscadorEmojisTest {
	/** La base de emojis en español que lleva la app. */
	private val buscador = BuscadorEmojis(BuscadorEmojis.leerTabla(File("../app/src/main/assets/idiomas/es/emojis.tsv").readText()))

	@Test
	fun laBaseSeLeeEntera() {
		assertTrue("Solo ${buscador.cuantos} emojis", buscador.cuantos > 1500)
	}

	@Test
	fun sinTildesNiMayusculasYConVariasPalabras() {
		val corazon = buscador.buscar("corazon roj")
		assertTrue(corazon.isNotEmpty())
		assertTrue(corazon.all { BuscadorEmojis.normalizar(it.nombre).contains("corazon") && BuscadorEmojis.normalizar(it.nombre).contains("roj") })
		assertEquals(buscador.buscar("corazón ROJ"), corazon)
	}

	@Test
	fun primeroLosQueEmpiezanPorLoBuscadoYLosMasCortos() {
		val caras = buscador.buscar("cara")
		assertTrue(caras.size > 20)
		assertTrue(BuscadorEmojis.palabras(caras[0].nombre)[0].startsWith("cara"))
		assertEquals("😀", buscador.buscar("cara sonriendo")[0].emoji)
	}

	@Test
	fun laEneNoEsUnaN() {
		assertEquals(listOf("niño"), BuscadorEmojis.palabras("Niño"))
		assertFalse(BuscadorEmojis.palabras("nino").contains("niño"))
	}

	@Test
	fun nadaQueBuscarNoDaNada() {
		assertTrue(buscador.buscar("").isEmpty())
		assertTrue(buscador.buscar("zzqx").isEmpty())
		// Los tonos de piel solos no salen
		assertTrue(buscador.buscar("tono de piel").none { it.emoji.codePointAt(0) in 0x1F3FB..0x1F3FF && it.emoji.length <= 2 })
	}
}
