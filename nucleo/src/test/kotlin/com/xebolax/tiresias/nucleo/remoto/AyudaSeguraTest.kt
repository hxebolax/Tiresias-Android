package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Lo de la 1.0.7 que hace la ayuda remota segura y posible por datos móviles: Tor, parejas y protecciones. */
class AyudaSeguraTest {
	private val ahora = 1_790_000_000L
	private val onion = Onion("a".repeat(52) + "2345", 41007)

	@Test
	fun `una invitacion con onion va y vuelve y lleva la version 2`() {
		val i = Invitacion.nueva("Mamá", emptyList(), 10, ahora, onion)
		val leida = Invitacion.leer("Hola, ${i.codigo()} gracias")
		assertEquals(i, leida)
		assertEquals(onion, leida?.onion)
		assertEquals("a".repeat(52) + "2345.onion", leida?.onion?.host)
	}

	@Test
	fun `sin onion el codigo sigue siendo de la version 1`() {
		val i = Invitacion.nueva("Mamá", listOf(Candidato.de(InetAddress.getByName("192.168.1.3"), 41000)), 10, ahora)
		val leida = Invitacion.leer(i.codigo())
		assertEquals(i, leida)
		assertNull(leida?.onion)
	}

	@Test
	fun `una direccion onion mal formada no se acepta`() {
		try {
			Onion("corta", 1)
			fail("Debería lanzar")
		} catch (e: IllegalArgumentException) {
			assertNotNull(e.message)
		}
	}

	@Test
	fun `socks5 pide el nombre del onion y deja el socket unido`() {
		ServerSocket(0).use { proxy ->
			val pedido = StringBuilder()
			val hilo = thread {
				proxy.accept().use { s ->
					val e = DataInputStream(s.getInputStream())
					val saludo = ByteArray(3).also { e.readFully(it) }
					assertEquals(5, saludo[0].toInt())
					s.getOutputStream().write(byteArrayOf(5, 0))
					val cabecera = ByteArray(5).also { e.readFully(it) }
					assertEquals(3, cabecera[3].toInt())
					val nombre = ByteArray(cabecera[4].toInt() and 0xFF).also { e.readFully(it) }
					val puerto = e.readUnsignedShort()
					pedido.append(String(nombre, Charsets.US_ASCII)).append(':').append(puerto)
					s.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
					s.getOutputStream().write(42)
					s.getOutputStream().flush()
					e.read()
				}
			}
			Socks5.conectar(proxy.localPort, onion.host, onion.puerto, 3000).use { s ->
				assertEquals(42, s.getInputStream().read())
				s.getOutputStream().write(1)
			}
			hilo.join(3000)
			assertEquals("${onion.host}:${onion.puerto}", pedido.toString())
		}
	}

	@Test
	fun `socks5 lanza si el proxy no llega al destino`() {
		ServerSocket(0).use { proxy ->
			val hilo = thread {
				proxy.accept().use { s ->
					val e = DataInputStream(s.getInputStream())
					e.readFully(ByteArray(3))
					s.getOutputStream().write(byteArrayOf(5, 0))
					e.readFully(ByteArray(5 + onion.host.length + 2))
					s.getOutputStream().write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0))
					s.getOutputStream().flush()
					// Cerrar ya cortaría la conexión antes de que el otro lado lea el código
					e.read()
				}
			}
			try {
				Socks5.conectar(proxy.localPort, onion.host, onion.puerto, 3000).close()
				fail("Debería lanzar")
			} catch (e: IOException) {
				assertTrue(e.toString(), e.message.orEmpty().contains("código 4"))
			}
			hilo.join(3000)
		}
	}

	@Test
	fun `solo la pareja con su secreto pasa la prueba`() {
		val marta = Emparejar.nueva("Marta", ahora)
		val otra = Emparejar.nueva("Otra", ahora)
		val prueba = Emparejar.prueba(marta, "1234")
		assertEquals(marta, Emparejar.comprobar(listOf(otra, marta), listOf(prueba), "1234"))
		assertNull("Otra comprobación del canal", Emparejar.comprobar(listOf(marta), listOf(prueba), "9999"))
		assertNull("Una pareja que no está en la lista", Emparejar.comprobar(listOf(otra), listOf(prueba), "1234"))
		assertNull("Un secreto distinto con la misma id", Emparejar.comprobar(listOf(marta), listOf(Emparejar.prueba(otra.copy(id = marta.id), "1234")), "1234"))
		assertNull("Basura", Emparejar.comprobar(listOf(marta), listOf("sin dos puntos", ":"), "1234"))
	}

	@Test
	fun `la oferta se reconoce dentro de un mensaje y caduca`() {
		val marta = Emparejar.nueva("Marta", ahora)
		val oferta = Emparejar.oferta(marta, ahora)
		assertEquals(marta, Emparejar.leerOferta("Mamá, te ayudo: $oferta.", listOf(marta), ahora + 60))
		assertNull("Caducada", Emparejar.leerOferta(oferta, listOf(marta), ahora + Emparejar.MINUTOS_OFERTA * 60L + 60))
		assertNull("De otra pareja", Emparejar.leerOferta(oferta, listOf(Emparejar.nueva("Otra", ahora)), ahora))
		// La última letra lleva bits de relleno que no cuentan: se toca la anterior, que es todo datos
		repeat(200) {
			val p = Emparejar.nueva("Marta", ahora)
			val o = Emparejar.oferta(p, ahora)
			val tocada = o.replaceRange(o.length - 2, o.length - 1, if (o[o.length - 2] == 'A') "B" else "A")
			assertNull("Tocada", Emparejar.leerOferta(tocada, listOf(p), ahora))
		}
	}

	@Test
	fun `las parejas se guardan y se leen`() {
		val lista = listOf(Emparejar.nueva("Marta", ahora), Emparejar.nueva("Juan \"el\" hijo", ahora + 5))
		assertEquals(lista, Emparejar.deJson(Emparejar.aJson(lista)))
		assertTrue(Emparejar.deJson("no es json").isEmpty())
		val m = lista[0]
		assertEquals(m.copy(nombre = "Mi hija"), Emparejar.deMensaje(m.id, Emparejar.secretoTexto(m), "Mi hija", m.desde))
		assertNull(Emparejar.deMensaje(m.id, "zz", "Mi hija", m.desde))
	}

	@Test
	fun `las apps de banca se reconocen y las normales no`() {
		for (p in listOf("es.bancosantander.apps", "com.bbva.bbvacontigo", "es.caixabank.caixabanknow", "com.bankinter.launcher", "com.revolut.revolut", "com.paypal.android.p2pmobile", "com.google.android.apps.walletnfcrel", "com.samsung.android.spay", "www.ingdirect.nativeframe")) {
			assertTrue(p, Protegido.esAppProtegida(p))
		}
		assertTrue(Protegido.esAppProtegida("com.ejemplo.app", "ING España"))
		assertTrue(Protegido.esAppProtegida("com.ejemplo.app", "Mi Banco"))
		for (p in listOf("com.whatsapp", "com.android.settings", "com.android.vending", "com.google.android.youtube", "com.android.chrome", "com.spotify.music", "com.xebolax.tiresias")) {
			assertFalse(p, Protegido.esAppProtegida(p))
		}
		assertFalse(Protegido.esAppProtegida("com.ejemplo.app", "Evolución de la salud"))
		assertFalse(Protegido.esAppProtegida("com.ejemplo.app", "Reading list"))
	}

	@Test
	fun `los codigos se tapan y lo demas no`() {
		val p = "dato protegido"
		assertEquals("Tu código de WhatsApp es dato protegido", Protegido.taparCodigos("Tu código de WhatsApp es 123 456", p))
		assertEquals("Código Bizum: dato protegido.", Protegido.taparCodigos("Código Bizum: 48213.", p))
		assertEquals("Your verification code is dato protegido", Protegido.taparCodigos("Your verification code is 847291", p))
		assertEquals(p, Protegido.taparCodigos("847291", p))
		assertEquals("Son las 12:30 del 2026", Protegido.taparCodigos("Son las 12:30 del 2026", p))
		assertEquals("Batería al 87 por ciento", Protegido.taparCodigos("Batería al 87 por ciento", p))
		assertEquals("Llama al 600 123 456", Protegido.taparCodigos("Llama al 600 123 456", p))
	}

	@Test
	fun `solo se abren enlaces web y de la tienda`() {
		assertTrue(Protegido.enlaceSeguro("https://www.ejemplo.es/ayuda"))
		assertTrue(Protegido.enlaceSeguro("market://details?id=com.whatsapp"))
		assertTrue(Protegido.enlaceSeguro(" HTTP://ejemplo.es "))
		assertFalse(Protegido.enlaceSeguro("https://malo.ru/banco.APK"))
		assertFalse(Protegido.enlaceSeguro("https://malo.ru/banco.apk?x=1#y"))
		assertFalse(Protegido.enlaceSeguro("intent://x#Intent;end"))
		assertFalse(Protegido.enlaceSeguro("file:///sdcard/a.apk"))
		assertFalse(Protegido.enlaceSeguro("tel:900123456"))
		assertFalse(Protegido.enlaceSeguro("sin esquema"))
	}

	@Test
	fun `los mensajes nuevos van y vuelven`() {
		for (m in listOf(
			MensajeAyuda.Hola("Marta", "1.0.7", "es", true, listOf("ab:cd")),
			MensajeAyuda.Hola("Mamá", "1.0.7", "es", false),
			MensajeAyuda.Ficha(listOf("Batería al 30 por ciento", "Wifi")),
			MensajeAyuda.Pareja("ab", "cd", "Mamá"),
			MensajeAyuda.Decir("Hola, mamá"),
			MensajeAyuda.Llamame,
		)) assertEquals(m, MensajeAyuda.leer(m.json()))
		assertFalse("Sin pruebas no se mandan", MensajeAyuda.Hola("a", "b", "c", true).json().contains("pruebas"))
		assertNotEquals(null, MensajeAyuda.leer("""{"t":"hola","nombre":"a","version":"b","idioma":"c","ayudante":true}"""))
		assertNull(MensajeAyuda.leer("""{"t":"hola","nombre":"a","version":"b","idioma":"c","ayudante":true,"pruebas":[1]}"""))
	}
}
