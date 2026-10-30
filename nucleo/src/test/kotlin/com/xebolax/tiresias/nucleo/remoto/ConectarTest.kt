package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class ConectarTest {
	private val bucle = InetAddress.getByName("127.0.0.1")

	/** Un puerto en el que seguro que no escucha nadie (se abre y se cierra). */
	private fun puertoLibre(): Int = ServerSocket(0, 1, bucle).use { it.localPort }

	@Test
	fun conectaConElQueExisteYCierraLosDemas() {
		ServerSocket(0, 5, bucle).use { a ->
			ServerSocket(0, 5, bucle).use { b ->
				val candidatos = listOf(
					Candidato.de(bucle, puertoLibre()),
					Candidato.de(bucle, a.localPort),
					Candidato.de(bucle, b.localPort),
				)
				val s = Conectar.primero(candidatos, 3000)
				assertNotNull(s)
				s!!.use {
					assertTrue(it.isConnected)
					assertTrue(it.port == a.localPort || it.port == b.localPort)
				}
			}
		}
	}

	@Test
	fun ningunoDevuelveNull() {
		val inicio = System.nanoTime()
		assertNull(Conectar.primero(listOf(Candidato.de(bucle, puertoLibre()), Candidato.de(bucle, puertoLibre())), 2000))
		// Como mucho el tiempo pedido (Windows tarda unos 2 s en dar por rechazada una conexión local)
		assertTrue((System.nanoTime() - inicio) / 1_000_000L < 3500)
		assertNull(Conectar.primero(emptyList(), 1000))
	}

	@Test
	fun sinRespuestaSeRindeATiempo() {
		// 192.0.2.1 es de documentación: no contesta (o la red la rechaza enseguida)
		val inicio = System.nanoTime()
		assertNull(Conectar.primero(listOf(Candidato.de(InetAddress.getByName("192.0.2.1"), 9)), 800))
		assertTrue((System.nanoTime() - inicio) / 1_000_000L < 2500)
	}

	@Test
	fun ordenLocalPrimeroLuegoIpv6() {
		val publica = Candidato.de(InetAddress.getByName("83.45.120.7"), 1)
		val ipv6 = Candidato.de(InetAddress.getByName("2a02::1"), 1)
		val wifi = Candidato.de(InetAddress.getByName("192.168.1.2"), 1)
		assertEquals(listOf(wifi, ipv6, publica), Conectar.ordenar(listOf(publica, ipv6, wifi, publica)))
	}
}
