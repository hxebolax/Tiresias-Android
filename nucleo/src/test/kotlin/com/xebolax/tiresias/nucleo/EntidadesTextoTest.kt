package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que se ofrece del texto en el menú de acciones: direcciones, correos, teléfonos y códigos, y lo que no lo es. */
class EntidadesTextoTest {
	private fun codigos(texto: String, clasificado: List<TrozoReconocido> = emptyList()) =
		EntidadesTexto.encontrar(texto, emptyList(), clasificado).filterIsInstance<EntidadTexto.Codigo>().map { it.visto }

	@Test
	fun losCodigosDeVerificacion() {
		assertEquals(listOf("482916"), codigos("Tu código de WhatsApp es 482916. No lo compartas."))
		assertEquals(listOf("482916"), codigos("Código:482916"))
		assertEquals(listOf("482916"), codigos("G-482916 es tu código de Google"))
		// El clasificador de Android lo toma por un teléfono: se ofrece también como código
		val t = "Tu código es 482916"
		val e = EntidadesTexto.encontrar(t, emptyList(), listOf(TrozoReconocido(13, 19, telefono = true)))
		assertTrue(e[0] is EntidadTexto.Codigo && e[1] is EntidadTexto.Telefono)
	}

	@Test
	fun loQueNoEsUnCodigo() {
		// Una hora, un año de copyright, una cantidad, parte de una palabra o de un enlace
		assertTrue(codigos("Quedamos a las 10:4500 ").isEmpty())
		assertTrue(codigos("© 2026 Empresa").isEmpty())
		assertTrue(codigos("Son 1500 € o $ 2500").isEmpty())
		assertTrue(codigos("usuario_12345 y ref12345").isEmpty())
		assertTrue(codigos("Pedido 1234.5 kg").isEmpty())
		// Lo que el clasificador reconoce como fecha o teléfono no es un código
		val texto = "Llama al 912345678 el 2026"
		val cl = listOf(TrozoReconocido(9, 18, telefono = true), TrozoReconocido(22, 26))
		assertTrue(codigos(texto, cl).isEmpty())
		// Sin clasificador no se ofrecen ni códigos ni teléfonos (nada separa un código de una fecha)
		assertTrue(EntidadesTexto.encontrar("Código 482916", emptyList(), null).isEmpty())
	}

	@Test
	fun losTelefonosYLosEnlaces() {
		val texto = "Escribe a ana@correo.es o mira ejemplo.com, o llama al +34 (0)912 345 678"
		val enlaces = listOf(Triple(10, 23, "mailto:ana@correo.es"), Triple(31, 42, "http://ejemplo.com"))
		val i = texto.indexOf("+34")
		val e = EntidadesTexto.encontrar(texto, enlaces, listOf(TrozoReconocido(i, texto.length, telefono = true)))
		assertEquals("ana@correo.es", (e[0] as EntidadTexto.Correo).direccion)
		assertEquals("http://ejemplo.com", (e[1] as EntidadTexto.Web).url)
		assertEquals("+34912345678", (e[2] as EntidadTexto.Telefono).numero)
		// Lo que el texto ya enlaza lo ofrece el menú como enlace: no se repite
		assertTrue(EntidadesTexto.encontrar(texto, enlaces, null, yaEnlazados = setOf("http://ejemplo.com")).none { it is EntidadTexto.Web })
	}

	@Test
	fun cuandoMerecePenaMirar() {
		assertTrue(EntidadesTexto.puedeTener("código 4829"))
		assertTrue(EntidadesTexto.puedeTener("ana@x"))
		assertTrue(EntidadesTexto.puedeTener("mira ejemplo.com"))
		assertFalse(EntidadesTexto.puedeTener("Hola, ¿qué tal?"))
		assertFalse(EntidadesTexto.puedeTener(null))
	}
}
