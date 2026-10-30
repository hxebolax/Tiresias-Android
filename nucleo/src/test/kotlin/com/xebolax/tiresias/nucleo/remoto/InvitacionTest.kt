package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class InvitacionTest {
	private val ahora = 1_790_000_000L

	private fun ip(texto: String) = InetAddress.getByName(texto)

	private val wifi = Candidato.de(ip("192.168.1.34"), 41234)
	private val ipv6 = Candidato.de(ip("2a02:9130:84a1:2b00:1c3e:5ff:fe12:3456"), 41234)
	private val publica = Candidato.de(ip("83.45.120.7"), 51000)
	private val localV6 = Candidato.de(ip("fe80::1"), 41234)

	private fun idaYVuelta(i: Invitacion): Invitacion {
		val codigo = i.codigo()
		assertTrue(codigo.startsWith("TIR1"))
		assertTrue("Solo Base32 en mayúsculas: $codigo", codigo.substring(4).all { it in 'A'..'Z' || it in '2'..'7' })
		val leida = Invitacion.leer(codigo)
		assertEquals(i, leida)
		return leida!!
	}

	@Test
	fun idaYVueltaConCeroUnoYCuatroCandidatos() {
		idaYVuelta(Invitacion.nueva("Ana", emptyList(), 30, ahora))
		idaYVuelta(Invitacion.nueva("Ana", listOf(wifi), 30, ahora))
		val leida = idaYVuelta(Invitacion.nueva("Ana", listOf(wifi, ipv6, publica, localV6), 30, ahora))
		assertEquals(4, leida.candidatos.size)
		assertFalse(leida.candidatos[0].esIpv6)
		assertTrue(leida.candidatos[1].esIpv6)
		assertEquals(ip("2a02:9130:84a1:2b00:1c3e:5ff:fe12:3456"), leida.candidatos[1].direccion())
		assertEquals(51000, leida.candidatos[2].puerto)
	}

	@Test
	fun elSecretoEsAleatorioYDe16Bytes() {
		val a = Invitacion.nueva("Ana", listOf(wifi), 30, ahora)
		val b = Invitacion.nueva("Ana", listOf(wifi), 30, ahora)
		assertEquals(16, a.secreto.size)
		assertFalse(a.secreto.contentEquals(b.secreto))
		assertNotEquals(a, b)
		assertNotEquals(a.codigo(), b.codigo())
		assertArrayEquals(a.secreto, Invitacion.leer(a.codigo())!!.secreto)
	}

	@Test
	fun dentroDeUnMensajeLargo() {
		val i = Invitacion.nueva("Pepe", listOf(wifi, ipv6), 60, ahora)
		val codigo = i.codigo()
		val mensaje = "Hola, soy Pepe. ¿Me ayudas con el móvil? Abre Tiresias, ve a Ayuda remota y pega esto:\n\n" +
			codigo + "\n\nGracias 😀 TIR1 no es esto."
		assertEquals(i, Invitacion.leer(mensaje))
		// Con palabras pegadas detrás
		assertEquals(i, Invitacion.leer("código: $codigo gracias majo"))
		// En minúsculas y partido con espacios y guiones
		val partido = codigo.lowercase().chunked(5).joinToString(" - ")
		assertEquals(i, Invitacion.leer("Te lo mando partido: $partido."))
		// Con un TIR1 falso antes
		assertEquals(i, Invitacion.leer("TIR1ABCDEF y el bueno: $codigo"))
	}

	@Test
	fun crcRotoDaNull() {
		val codigo = Invitacion(ByteArray(16) { it.toByte() }, ahora, "Ana", listOf(wifi)).codigo()
		// Cambiar un carácter del secreto: el CRC-16 lo detecta siempre (ráfaga de menos de 16 bits)
		val p = 20
		val cambiado = codigo.substring(0, p) + (if (codigo[p] == 'A') 'B' else 'A') + codigo.substring(p + 1)
		assertNull(Invitacion.leer(cambiado))
		// Cortado
		assertNull(Invitacion.leer(codigo.dropLast(4)))
		assertNull(Invitacion.leer("TIR1"))
		assertNull(Invitacion.leer("Nada por aquí"))
		assertNull(Invitacion.leer(""))
	}

	@Test
	fun versionDistintaDaNull() {
		val codigo = Invitacion.nueva("Ana", listOf(wifi), 30, ahora).codigo()
		val bytes = Base32.decodificar(codigo.substring(4))
		bytes[0] = 2
		// Se rehace el CRC para que solo falle la versión
		val sinCrc = bytes.copyOf(bytes.size - 2)
		val crc = Crc16.calcular(sinCrc)
		val rehecho = sinCrc + byteArrayOf((crc shr 8).toByte(), crc.toByte())
		assertNull(Invitacion.leer("TIR1" + Base32.codificar(rehecho)))
	}

	@Test
	fun caducidad() {
		val i = Invitacion.nueva("Ana", listOf(wifi), 30, ahora)
		assertEquals(0L, i.caduca % 60)
		assertTrue(i.caduca >= ahora + 30 * 60)
		assertTrue(i.caduca < ahora + 30 * 60 + 60)
		assertFalse(i.caducada(ahora))
		assertFalse(i.caducada(ahora + 29 * 60))
		assertTrue(i.caducada(ahora + 31 * 60))
		assertTrue(i.caducada(i.caduca))
		assertEquals(i.caduca, Invitacion.leer(i.codigo())!!.caduca)
	}

	@Test
	fun nombreConTildesYEmojisRecortadoSinPartir() {
		// 39 bytes de «á» (2 bytes cada una: 19 = 38 bytes) y luego un emoji de 4 que no cabe
		val largo = "á".repeat(19) + "😀" + "más"
		val i = Invitacion.nueva(largo, listOf(wifi), 30, ahora)
		assertEquals("á".repeat(19), i.nombre)
		assertEquals(38, i.nombre.toByteArray(Charsets.UTF_8).size)
		idaYVuelta(i)
		// 37 bytes y un emoji más que ya no cabe
		val justo = "José María 😀😀😀😀😀😀👍"
		assertTrue(justo.toByteArray(Charsets.UTF_8).size > 40)
		val recortado = Invitacion.recortar(justo, 40)
		assertTrue(recortado.toByteArray(Charsets.UTF_8).size <= 40)
		assertTrue(justo.startsWith(recortado))
		// No deja medio par sustituto
		assertFalse(Character.isHighSurrogate(recortado.last()))
		assertEquals("José María 😀😀😀😀😀😀", recortado)
		assertEquals("ñ", Invitacion.recortar("ñ", 40))
		assertEquals("", Invitacion.recortar("😀", 3))
	}

	@Test
	fun candidatosPorContenido() {
		assertEquals(Candidato(byteArrayOf(10, 0, 0, 1), 80), Candidato(byteArrayOf(10, 0, 0, 1), 80))
		assertEquals(Candidato(byteArrayOf(10, 0, 0, 1), 80).hashCode(), Candidato(byteArrayOf(10, 0, 0, 1), 80).hashCode())
		assertNotEquals(Candidato(byteArrayOf(10, 0, 0, 1), 80), Candidato(byteArrayOf(10, 0, 0, 2), 80))
		assertEquals("192.168.1.34:41234", wifi.toString())
	}

	@Test(expected = IllegalArgumentException::class)
	fun ipDeLongitudRara() {
		Candidato(byteArrayOf(1, 2, 3), 80)
	}

	@Test
	fun codigoCorto() {
		val c = CodigoCorto.nuevo()
		assertEquals(8, c.length)
		assertTrue(c.all { it in '0'..'9' })
		assertEquals("12345678", CodigoCorto.normalizar(" 1234-5678 "))
		assertEquals("12345678", CodigoCorto.normalizar("1234 5678"))
		assertNull(CodigoCorto.normalizar("1234567"))
		assertNull(CodigoCorto.normalizar("123456789"))
		assertNull(CodigoCorto.normalizar("1234567a"))
		assertEquals("1234 5678", CodigoCorto.agrupado("12345678"))
		assertEquals("0012 3400", CodigoCorto.agrupado("0012-3400"))
		val s = CodigoCorto.secreto("1234 5678")
		assertEquals(16, s.size)
		assertArrayEquals(s, CodigoCorto.secreto("12345678"))
		assertFalse(s.contentEquals(CodigoCorto.secreto("12345679")))
		// SHA-256("tiresias-ayuda-codigo:12345678"), primeros 16 bytes
		val esperado = java.security.MessageDigest.getInstance("SHA-256").digest("tiresias-ayuda-codigo:12345678".toByteArray()).copyOf(16)
		assertArrayEquals(esperado, s)
	}

	@Test
	fun clasificacionDeDirecciones() {
		for (p in listOf("10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.10", "169.254.3.4", "fe80::1", "fd12:3456::1", "fc00::1")) {
			assertTrue(p, Direcciones.esPrivada(ip(p)))
		}
		for (p in listOf("172.32.0.1", "8.8.8.8", "100.64.0.1", "2a02::1", "11.0.0.1")) {
			assertFalse(p, Direcciones.esPrivada(ip(p)))
		}
		assertTrue(Direcciones.esCompartida(ip("100.64.0.1")))
		assertTrue(Direcciones.esCompartida(ip("100.127.255.254")))
		assertFalse(Direcciones.esCompartida(ip("100.128.0.1")))
		assertFalse(Direcciones.esCompartida(ip("100.63.255.255")))
		assertTrue(Direcciones.esIpv6Global(ip("2a02:9130:84a1:2b00::1")))
		assertTrue(Direcciones.esIpv6Global(ip("2001:4860::8888")))
		assertFalse(Direcciones.esIpv6Global(ip("2001:db8::1")))
		assertFalse(Direcciones.esIpv6Global(ip("fe80::1")))
		assertFalse(Direcciones.esIpv6Global(ip("::1")))
		assertFalse(Direcciones.esIpv6Global(ip("8.8.8.8")))
		assertTrue(Direcciones.esPublicaIpv4(ip("83.45.120.7")))
		assertTrue(Direcciones.esPublicaIpv4(ip("8.8.8.8")))
		for (p in listOf("192.168.1.1", "100.64.1.1", "127.0.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "192.0.2.1", "198.51.100.3", "203.0.113.9", "198.18.0.1")) {
			assertFalse(p, Direcciones.esPublicaIpv4(ip(p)))
		}
		assertFalse(Direcciones.esPublicaIpv4(ip("2a02::1")))
	}

	@Test
	fun base32SegunLaRfc() {
		// Vectores de la RFC 4648 sin relleno
		assertEquals("", Base32.codificar(ByteArray(0)))
		assertEquals("MY", Base32.codificar("f".toByteArray()))
		assertEquals("MZXQ", Base32.codificar("fo".toByteArray()))
		assertEquals("MZXW6", Base32.codificar("foo".toByteArray()))
		assertEquals("MZXW6YQ", Base32.codificar("foob".toByteArray()))
		assertEquals("MZXW6YTB", Base32.codificar("fooba".toByteArray()))
		assertEquals("MZXW6YTBOI", Base32.codificar("foobar".toByteArray()))
		assertEquals("foobar", String(Base32.decodificar("MZXW6YTBOI")))
	}

	@Test
	fun crc16CcittFalse() {
		// Valor de comprobación del catálogo de CRC: "123456789" → 0x29B1
		assertEquals(0x29B1, Crc16.calcular("123456789".toByteArray()))
	}

	@Test
	fun toStringSinSecreto() {
		val i = Invitacion.nueva("Ana", listOf(wifi), 30, ahora)
		assertNotNull(i.toString())
		assertFalse(i.toString().contains("secreto"))
	}
}
