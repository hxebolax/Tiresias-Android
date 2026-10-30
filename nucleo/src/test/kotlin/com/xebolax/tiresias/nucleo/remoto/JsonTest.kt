package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {
	private fun invalido(texto: String) {
		try {
			Json.leer(texto)
			fail("Debería fallar: $texto")
		} catch (e: JsonInvalido) {
			assertTrue(e.message!!.isNotEmpty())
		}
	}

	@Test
	fun leeLosTiposBasicos() {
		assertEquals(null, Json.leer("null"))
		assertEquals(true, Json.leer(" true "))
		assertEquals(false, Json.leer("false"))
		assertEquals(42L, Json.leer("42"))
		assertEquals(-7L, Json.leer("-7"))
		assertEquals(0L, Json.leer("0"))
		assertEquals(1.5, Json.leer("1.5"))
		assertEquals(1.0e10, Json.leer("1e10"))
		assertEquals(-2.5e-3, Json.leer("-2.5E-3"))
		assertEquals(1.2e+3, Json.leer("1.2e+3"))
		// Más grande que un Long: sale como Double
		assertEquals(1.0e20, Json.leer("100000000000000000000"))
		assertEquals("hola", Json.leer("\"hola\""))
		assertEquals(listOf(1L, "a", null, listOf<Any?>()), Json.leer("[1, \"a\", null, []]"))
	}

	@Test
	fun leeObjetosEnOrdenConEspacios() {
		val m = Json.leer(" {\n\t\"b\" : 1 ,\r\n \"a\":{\"x\":[true]} , \"c\":\"\"} ") as Map<*, *>
		assertEquals(listOf("b", "a", "c"), m.keys.toList())
		assertEquals(mapOf("x" to listOf(true)), m["a"])
		assertEquals(emptyMap<String, Any?>(), Json.leer("{}"))
	}

	@Test
	fun escapesAlLeer() {
		assertEquals("a\"b\\c/d\b\u000C\n\r\t", Json.leer("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\""))
		assertEquals("ñ", Json.leer("\"\\u00f1\""))
		assertEquals("ñ", Json.leer("\"\\u00F1\""))
		// Par sustituto: 😀 es U+1F600 = \uD83D\uDE00
		assertEquals("😀", Json.leer("\"\\ud83d\\ude00\""))
		assertEquals("ñ😀", Json.leer("\"ñ😀\""))
	}

	@Test
	fun escapesAlEscribir() {
		assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\b\\f\"", Json.escribir("a\"b\\c\n\r\t\b\u000C"))
		assertEquals("\"\\u0001\\u001f\"", Json.escribir("\u0001\u001F"))
		assertEquals("\"\\u2028\\u2029\"", Json.escribir("\u2028\u2029"))
		// Lo demás va tal cual, también lo que no es ASCII
		assertEquals("\"ñ😀/\"", Json.escribir("ñ😀/"))
	}

	@Test
	fun escribeTodosLosTipos() {
		assertEquals("null", Json.escribir(null))
		assertEquals("[1,2.5,true,\"x\",null]", Json.escribir(listOf(1, 2.5, true, "x", null)))
		assertEquals("[\"a\",\"b\"]", Json.escribir(arrayOf("a", "b")))
		assertEquals("{\"1\":\"uno\",\"k\":{\"z\":[]}}", Json.escribir(linkedMapOf(1 to "uno", "k" to mapOf("z" to emptyList<Any>()))))
		assertEquals("\"MASTER\"", Json.escribir(Prueba.MASTER))
		assertEquals("9007199254740993", Json.escribir(9007199254740993L))
	}

	private enum class Prueba { MASTER }

	@Test(expected = IllegalArgumentException::class)
	fun noEscribeBytes() {
		Json.escribir(byteArrayOf(1, 2))
	}

	@Test(expected = IllegalArgumentException::class)
	fun noEscribeNaN() {
		Json.escribir(Double.NaN)
	}

	@Test
	fun idaYVuelta() {
		val original = linkedMapOf<String, Any?>(
			"t" to "voz",
			"texto" to "Línea 1\nLínea 2 «comillas» \"dobles\" \\ y tab\t, ñ, 😀👍🏽, \u0000 y \u2028",
			"n" to 123456789012L,
			"d" to 0.1,
			"lista" to listOf(1L, listOf(2L, listOf(3L)), mapOf("a" to null)),
			"si" to true,
			"no" to null,
		)
		assertEquals(original, Json.leer(Json.escribir(original)))
	}

	@Test
	fun errores() {
		invalido("")
		invalido("   ")
		invalido("{")
		invalido("[1,]")
		invalido("{\"a\":1,}")
		invalido("{'a':1}")
		invalido("{a:1}")
		invalido("\"sin cerrar")
		invalido("\"control \u0001\"")
		invalido("\"\\x\"")
		invalido("\"\\u12\"")
		invalido("\"\\u12g4\"")
		invalido("01")
		invalido("1.")
		invalido(".5")
		invalido("1e")
		invalido("-")
		invalido("+1")
		invalido("tru")
		invalido("nul")
		invalido("[1 2]")
		invalido("{\"a\" 1}")
		invalido("1 2")
		invalido("[1]x")
	}

	@Test
	fun profundidadMaxima() {
		val bien = "[".repeat(Json.PROFUNDIDAD_MAXIMA) + "]".repeat(Json.PROFUNDIDAD_MAXIMA)
		Json.leer(bien)
		invalido("[".repeat(Json.PROFUNDIDAD_MAXIMA + 1) + "]".repeat(Json.PROFUNDIDAD_MAXIMA + 1))
		invalido("{\"a\":".repeat(200) + "1" + "}".repeat(200))
	}

	@Test
	fun objetoNoLanza() {
		assertEquals(mapOf("a" to 1L), Json.objeto("{\"a\":1}"))
		assertNull(Json.objeto("[1]"))
		assertNull(Json.objeto("\"x\""))
		assertNull(Json.objeto("{roto"))
		assertNull(Json.objeto(""))
	}

	@Test
	fun ayudasParaLeerCampos() {
		val m = Json.objeto("{\"s\":\"x\",\"n\":5,\"d\":2.0,\"f\":2.5,\"b\":true,\"l\":[1],\"m\":{\"k\":1},\"z\":null}")!!
		assertEquals("x", m.texto("s"))
		assertNull(m.texto("n"))
		assertEquals(5L, m.entero("n"))
		assertEquals(2L, m.entero("d"))
		assertNull(m.entero("f"))
		assertNull(m.entero("s"))
		assertEquals(2.5, m.decimal("f")!!, 0.0)
		assertEquals(5.0, m.decimal("n")!!, 0.0)
		assertEquals(true, m.logico("b"))
		assertNull(m.logico("s"))
		assertEquals(listOf(1L), m.lista("l"))
		assertNull(m.lista("m"))
		assertEquals(mapOf("k" to 1L), m.mapa("m"))
		assertNull(m.mapa("l"))
		assertNull(m.texto("z"))
		assertNull(m.texto("no-existe"))
	}
}
