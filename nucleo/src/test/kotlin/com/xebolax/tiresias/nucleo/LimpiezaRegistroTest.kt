package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que nunca puede llegar al registro que se comparte. Las claves de estas
 * pruebas son inventadas, con el mismo formato que las de verdad, y se montan
 * a trozos al ejecutarlas ([falsa]): escritas enteras, el buscador de secretos
 * de GitHub las toma por claves de verdad y avisa (le pasó a esta prueba).
 */
class LimpiezaRegistroTest {
	/** Una clave inventada: el principio de su formato y un relleno que dice FAKE. */
	private fun falsa(principio: String, relleno: String = "FAKEfake0123456789abcdefghijklmnop") = principio + relleno

	private val clave = falsa("AQ" + ".", "Ab8Zz9FAKEfakeFAKE0123456789abcdefGHIJklmnoPQRS")
	private val chat = "Enviado a las 12:18, No visto\nVisto 3 veces"

	/** El caso real (07-11-2026): la clave pegada con un trozo del chat, en el mensaje de la excepción. */
	@Test
	fun laClaveYElChatNoLleganAlEscribir() {
		val e = IllegalArgumentException("Unexpected char 0x0a at 53 in header value: $clave\n$chat")
		val traza = LimpiezaRegistro.traza(e)
		assertFalse(traza, clave.drop(3).take(12) in traza)
		assertFalse(traza, "Enviado" in traza || "Visto" in traza || "12:18" in traza)
		assertTrue(traza, traza.startsWith("java.lang.IllegalArgumentException: Unexpected char 0x0a at 53 in header value: [oculto] […]"))
		// Las líneas de código siguen (para saber dónde falló)
		assertTrue(traza, "\tat com.xebolax.tiresias.nucleo.LimpiezaRegistroTest" in traza)
	}

	/** Un registro antiguo, escrito antes de esto, se limpia al compartirlo. */
	@Test
	fun unRegistroAntiguoSeLimpiaAlCompartir() {
		val antiguo = """
			2026-10-29 20:47:46.118 E Vision: La IA falló
			java.lang.IllegalArgumentException: Unexpected char 0x0a at 53 in header value: $clave
			Enviado a las 12:18, No visto
			Visto 3 veces
				at com.android.okhttp.Headers${'$'}Builder.checkNameAndValue(Headers.java:317)
				at com.xebolax.tiresias.vision.Vision${'$'}${'$'}ExternalSyntheticLambda17.run(D8${'$'}${'$'}SyntheticClass:0)
				at java.lang.reflect.Method.invoke(Native Method)
			Caused by: java.io.IOException: token=abc123def456ghi789
				... 27 more

			2026-10-29 21:09:43.917 W AsistenteIA: El modelo gemini-2.5-flash de Gemini no contesta (503): se prueba otro
		""".trimIndent()
		val limpio = LimpiezaRegistro.compartible(antiguo)
		assertFalse(limpio, "Enviado" in limpio || "Visto 3" in limpio || "12:18" in limpio)
		assertFalse(limpio, clave.drop(3).take(12) in limpio)
		assertFalse(limpio, "abc123def456" in limpio)
		assertTrue(limpio, "E Vision: La IA falló" in limpio)
		assertTrue(limpio, "at com.android.okhttp.Headers\$Builder.checkNameAndValue(Headers.java:317)" in limpio)
		assertTrue(limpio, "SyntheticClass:0)" in limpio)
		assertTrue(limpio, "(Native Method)" in limpio)
		assertTrue(limpio, "... 27 more" in limpio)
		assertTrue(limpio, "El modelo gemini-2.5-flash de Gemini no contesta (503)" in limpio)
	}

	@Test
	fun lasClavesDeCadaServicio() {
		for (secreto in listOf(
			falsa("AI" + "za" + "Sy", "FAKE0123456789abcdefghijklmnopqrs"),
			clave,
			falsa("sk" + "-ant-api03-", "FAKEfake0123456789abcdefghij"),
			falsa("sk" + "-proj-", "FAKEfake0123456789abcdefghijklmn"),
			falsa("gh" + "p_"),
			falsa("ey" + "JhbGciOiJIUzI1NiJ9.", "ey" + "JzdWIiOiJGQUtFIn0.FAKEfakeFAKE"),
		)) {
			val l = LimpiezaRegistro.linea("fallo con $secreto al pedir")
			assertFalse("Se ve: $l", secreto.drop(4).take(10) in l)
			assertTrue(l, l.startsWith("fallo con [oculto]"))
		}
		assertEquals("Authorization: [oculto]", LimpiezaRegistro.linea("Authorization: Bearer FAKEfake0123456789"))
		assertEquals("Bearer [oculto] falló", LimpiezaRegistro.linea("Bearer FAKEfake0123456789 falló"))
		assertEquals("x-goog-api-key=[oculto]", LimpiezaRegistro.linea("x-goog-api-key=algo"))
		assertEquals("password: [oculto], usuario", LimpiezaRegistro.linea("password: hunter2, usuario"))
		// Un token largo sin formato conocido (letras y cifras mezcladas)
		assertEquals("con [oculto] dentro", LimpiezaRegistro.linea("con Q7xk2mP9vR4tY8wZ3nB6cF1dG5hJ0 dentro"))
	}

	@Test
	fun textoCorreosYUrls() {
		assertEquals("No se encontró [texto]", LimpiezaRegistro.linea("No se encontró \"Hola, ¿quedamos mañana?\""))
		assertEquals("Botón [texto] sin nombre", LimpiezaRegistro.linea("Botón «Enviar a Ana» sin nombre"))
		assertEquals("Escribe a [correo]", LimpiezaRegistro.linea("Escribe a ana.perez+1@correo.es"))
		assertEquals("GET https://servidor.es/api?[oculto] falló", LimpiezaRegistro.linea("GET https://servidor.es/api?key=FAKE&q=hola falló"))
		assertEquals("Llamar al [oculto]", LimpiezaRegistro.linea("Llamar al +34 612 345 678"))
	}

	@Test
	fun lasDireccionesDeRed() {
		for (t in listOf(
			"java.net.ConnectException: failed to connect to casa.ejemplo.es/192.168.1.20 (port 6837)",
			"java.net.UnknownHostException: Unable to resolve host \"casa.ejemplo.es\": No address associated",
			"java.net.UnknownHostException: casa.ejemplo.es",
			"javax.net.ssl.SSLPeerUnverifiedException: Hostname casa.ejemplo.es not verified",
			"fallo con 2a02:9130:1:2:3:4:5:6",
			"fallo con abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuvwx.onion",
		)) {
			val l = LimpiezaRegistro.linea(t)
			assertFalse("Queda una dirección: $l", "casa.ejemplo" in l || "192.168" in l || "2a02" in l || ".onion" in l)
		}
	}

	/** Lo nuestro de siempre no cambia: mensajes, números, nombres de clases y las pilas del vigilante. */
	@Test
	fun loNormalSeQuedaComoEsta() {
		for (t in listOf(
			"Android cerró Tiresias: motivo 13, importancia 100, proceso com.xebolax.tiresias, killedByAm=true",
			"Descarga cortada en 1048576 de 106843984 bytes",
			"Otro servicio explora por toque (com.google.android.marvin.talkback): el doble toque lo hace Android",
			"Fallo al procesar un evento (tipo 2048)",
			"Alguien ha probado una clave equivocada (3)",
			"Primer desbloqueo: se cargan los complementos y las voces descargadas",
		)) assertEquals(t, LimpiezaRegistro.mensaje(t))
		val vigilante = "El hilo lector no responde: se reinicia Tiresias\ntiresias-lector: TIMED_WAITING\n  java.lang.Thread.sleep(Native Method)\n  com.xebolax.tiresias.lectura.Lector.alEvento(Lector.kt:309)"
		assertEquals(vigilante, LimpiezaRegistro.mensaje(vigilante))
		// Un mensaje nuestro con más líneas de texto: solo la primera
		assertEquals("Falló algo", LimpiezaRegistro.mensaje("Falló algo\nHola, esto es un chat"))
	}

	@Test
	fun losMensajesLargosSeRecortan() {
		val largo = "x ".repeat(1000)
		assertTrue(LimpiezaRegistro.mensaje(largo).length <= LimpiezaRegistro.MAXIMO_MENSAJE + 1)
	}
}
