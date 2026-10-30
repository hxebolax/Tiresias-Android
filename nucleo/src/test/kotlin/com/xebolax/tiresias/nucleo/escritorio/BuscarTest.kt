package com.xebolax.tiresias.nucleo.escritorio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuscarTest {
	@Test
	fun normalizarQuitaAcentosYMayusculas() {
		assertEquals("musica", Buscar.normalizar("Música"))
		assertEquals("pinguino", Buscar.normalizar("PINGÜINO"))
		assertEquals("ano", Buscar.normalizar("Año"))
		assertEquals("telegram", Buscar.normalizar("  Telegram  "))
		assertEquals("", Buscar.normalizar("   "))
	}

	@Test
	fun parecidoOrdenaDeMasAMenos() {
		val q = "tele"
		assertEquals(100, Buscar.parecido("tele", q))
		assertEquals(80, Buscar.parecido("telegram", q))
		assertEquals(60, Buscar.parecido("mi telefono", q))
		assertEquals(40, Buscar.parecido("hotelero", q))
		assertEquals(0, Buscar.parecido("whatsapp", q))
	}

	@Test
	fun parecidoSeparaPalabrasPorSignos() {
		assertEquals(60, Buscar.parecido("uno-dos", "dos"))
		assertEquals(60, Buscar.parecido("uno_dos", "dos"))
		assertEquals(60, Buscar.parecido("uno.dos", "dos"))
	}

	@Test
	fun parecidoEntiendeLasIniciales() {
		assertEquals(30, Buscar.parecido("whatsapp business", "wb"))
		assertEquals(0, Buscar.parecido("telegram", "tg"))
		// Con una letra no bastan las iniciales: saldría medio móvil
		assertEquals(0, Buscar.parecido("uno dos", "x"))
	}

	@Test
	fun unaConsultaVaciaNoSeParece() {
		assertEquals(0, Buscar.parecido("telegram", ""))
	}

	@Test
	fun filtrarVaDelMasParecidoAlMenosYPorNombre() {
		val apps = listOf("Hostelero", "Telegram", "Tele", "Mi tele", "Telecinco", "WhatsApp")
		assertEquals(listOf("Tele", "Telecinco", "Telegram", "Mi tele", "Hostelero"), Buscar.filtrar(apps, "tele") { it })
	}

	@Test
	fun filtrarNoDistingueAcentos() {
		assertEquals(listOf("Música"), Buscar.filtrar(listOf("Música", "Mapas"), "MUSICA") { it })
		assertEquals(listOf("Musica"), Buscar.filtrar(listOf("Musica", "Mapas"), "música") { it })
	}

	@Test
	fun filtrarSinConsultaLoDevuelveTodoEnSuOrden() {
		val apps = listOf("Zeta", "Alfa")
		assertEquals(apps, Buscar.filtrar(apps, "   ") { it })
	}

	@Test
	fun filtrarSinCoincidenciasNoDaNada() {
		assertTrue(Buscar.filtrar(listOf("Alfa", "Beta"), "gamma") { it }.isEmpty())
	}
}
