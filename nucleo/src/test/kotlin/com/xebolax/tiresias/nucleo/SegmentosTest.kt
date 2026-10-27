package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentosTest {
	private val texto = "Hola, Sr. García. Cuesta 3,5 euros.\nSegunda línea\n\nOtro párrafo"

	@Test
	fun palabras() {
		val p = Segmentos.palabras(texto).map { it.texto }
		assertEquals(listOf("Hola", "Sr", "García", "Cuesta", "3,5", "euros", "Segunda", "línea", "Otro", "párrafo"), p)
		assertEquals("Hola", Segmentos.siguiente(texto, 0, Granularidad.PALABRA)?.texto)
		assertEquals("Sr", Segmentos.siguiente(texto, 4, Granularidad.PALABRA)?.texto)
		assertEquals("Hola", Segmentos.anterior(texto, 5, Granularidad.PALABRA)?.texto)
	}

	@Test
	fun caracteresConEmoji() {
		val t = "a😀b"
		assertEquals("😀", Segmentos.siguiente(t, 1, Granularidad.CARACTER)?.texto)
		assertEquals("😀", Segmentos.anterior(t, 3, Granularidad.CARACTER)?.texto)
		assertNull(Segmentos.siguiente(t, t.length, Granularidad.CARACTER))
	}

	@Test
	fun lineasYParrafos() {
		assertEquals(4, Segmentos.lineas(texto).size)
		assertEquals(listOf("Hola, Sr. García. Cuesta 3,5 euros.\nSegunda línea", "Otro párrafo"), Segmentos.parrafos(texto).map { it.texto })
	}

	@Test
	fun frasesRespetanAbreviaturas() {
		assertEquals(listOf("Hola, Sr. García.", "Cuesta 3,5 euros.", "Segunda línea", "Otro párrafo"), Segmentos.frases(texto).map { it.texto })
	}

	@Test
	fun cambiosDeEdicion() {
		assertEquals(CambioTexto.Escrito("a"), Edicion.cambio("hol", "hola"))
		assertEquals(CambioTexto.Borrado("a"), Edicion.cambio("hola", "hol"))
		assertEquals(CambioTexto.Sustituido("ola", "ello"), Edicion.cambio("hola", "hello"))
		assertEquals(CambioTexto.SinCambios, Edicion.cambio("x", "x"))
		// Escribir en medio
		assertEquals(CambioTexto.Escrito("XY"), Edicion.cambio("abcd", "abXYcd"))
		assertEquals("hola", Edicion.palabraTerminada("hola ", 5))
		assertNull(Edicion.palabraTerminada("hol", 3))
		// El apóstrofo no termina la palabra; el espacio de después, sí, y se dice entera
		assertNull(Edicion.palabraTerminada("don'", 4))
		assertEquals("don't", Edicion.palabraTerminada("don't ", 6))
		assertEquals("l’eau", Edicion.palabraTerminada("l’eau ", 6))
		assertNull(Edicion.palabraTerminada("bien-", 5))
		assertEquals("bien-estar", Edicion.palabraTerminada("bien-estar.", 11))
	}

	@Test
	fun filtroRepetidos() {
		val f = FiltroRepetidos(ventanaMs = 700, maximoPorSegundo = 3)
		assertTrue(f.pasa("a", "Hola", 0))
		assertFalse(f.pasa("a", "Hola", 100))
		assertTrue(f.pasa("b", "Hola", 150))
		assertTrue(f.pasa("a", "Hola", 800))
		// Tormenta: no más de 3 por segundo
		assertFalse(f.pasa("c", "1", 900))
		assertTrue(f.pasa("c", "2", 1900))
	}

	@Test
	fun versiones() {
		assertTrue(Versiones.comparar("1.2.10", "1.2.9") > 0)
		assertEquals(0, Versiones.comparar("1.0", "1.0.0"))
	}

	@Test
	fun mapaGestos() {
		val m = MapaGestos.leer("ARRIBA=SIGUIENTE;TRIPLE_TOQUE_MANTENIDO_4=NINGUNA;XX=YY")
		assertEquals(Accion.SIGUIENTE, m[Gesto.ARRIBA])
		// La salida de emergencia no se puede quitar
		assertEquals(Accion.MODO_SEGURO, m[Gesto.TRIPLE_TOQUE_MANTENIDO_4])
		assertEquals("ARRIBA=SIGUIENTE", m.escribir())
	}

	@Test
	fun combinaciones() {
		val c = Combinacion(Tecla.H, Mod.TIRESIAS or Mod.CTRL)
		assertEquals(c, Combinacion.leer(c.escribir()))
		assertEquals(ControlLectura.ENCABEZADOS, MapaTeclas.letraNavegacion(Tecla.H))
		assertEquals(2, MapaTeclas.nivelEncabezado(Tecla.UNO + 1))
	}
}
