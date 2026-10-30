package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class EnlaceTest {
	private val secretoA = ByteArray(16) { (it * 7 + 1).toByte() }
	private val secretoB = CodigoCorto.secreto("12345678")

	/** Un extremo de la conexión: lo que lee y lo que escribe. */
	private class Extremo(val entrada: InputStream, val salida: OutputStream)

	/**
	 * Dos tuberías en memoria, una por sentido: el servidor ejecuta [lado] en otro hilo y el
	 * cliente [cliente] en este. Al terminar, cada uno cierra lo que escribe (el otro lee el
	 * final del flujo). Devuelve el resultado del servidor.
	 *
	 * Se usan tuberías y no sockets de 127.0.0.1 porque en el Windows de desarrollo el bucle
	 * local da «Connection reset» al azar al cerrar, también fuera de estas pruebas.
	 */
	private fun <T> conectar(lado: (Extremo) -> T, cliente: (Extremo) -> Unit): Future<T> {
		val haciaServidor = PipedInputStream(1 shl 16)
		val desdeCliente = PipedOutputStream(haciaServidor)
		val haciaCliente = PipedInputStream(1 shl 16)
		val desdeServidor = PipedOutputStream(haciaCliente)
		val hilos = Executors.newSingleThreadExecutor()
		val futuro = hilos.submit(Callable {
			desdeServidor.use { lado(Extremo(haciaServidor, it)) }
		})
		hilos.shutdown()
		try {
			desdeCliente.use { cliente(Extremo(haciaCliente, it)) }
		} catch (e: Throwable) {
			// Si el servidor también falló, su error explica el del cliente
			try {
				futuro.get(10, TimeUnit.SECONDS)
			} catch (x: ExecutionException) {
				e.addSuppressed(x.cause!!)
			}
			throw e
		}
		return futuro
	}

	/** El resultado del servidor, o su excepción tal cual. */
	private fun <T> resultado(f: Future<T>): T = try {
		f.get(20, TimeUnit.SECONDS)
	} catch (e: ExecutionException) {
		throw e.cause!!
	}

	@Test(timeout = 30_000)
	fun conexionBuenaEnLosDosSentidos() {
		val grande = Random(1).nextBytes(1 shl 20)
		var cifrasCliente = ""
		val servidor = conectar({ s ->
			val e = Enlace.servidor(s.entrada, s.salida, listOf(secretoA))
			val recibidos = listOf(e.recibirTexto(), e.recibirTexto())
			val bytes = e.recibir()
			e.enviarTexto("Hola desde el ayudado ñ😀")
			e.enviar(bytes)
			e.enviar(ByteArray(0))
			Triple(e.comprobacion, recibidos, e.secretoUsado)
		}) { s ->
			val e = Enlace.cliente(s.entrada, s.salida, secretoA)
			cifrasCliente = e.comprobacion
			assertEquals(0, e.secretoUsado)
			e.enviarTexto("uno")
			e.enviarTexto("dos «ñ»")
			e.enviar(grande)
			assertEquals("Hola desde el ayudado ñ😀", e.recibirTexto())
			assertArrayEquals(grande, e.recibir())
			assertEquals(0, e.recibir().size)
		}
		val (cifrasServidor, recibidos, usado) = resultado(servidor)
		assertEquals(listOf("uno", "dos «ñ»"), recibidos)
		assertEquals(0, usado)
		assertEquals(4, cifrasCliente.length)
		assertTrue(cifrasCliente.all { it in '0'..'9' })
		assertEquals(cifrasServidor, cifrasCliente)
	}

	@Test(timeout = 30_000)
	fun secretoEquivocadoFallaEnLosDosLados() {
		val servidor = conectar({ s ->
			Enlace.servidor(s.entrada, s.salida, listOf(secretoA))
		}) { s ->
			try {
				Enlace.cliente(s.entrada, s.salida, secretoB)
				fail("El cliente debería fallar")
			} catch (e: ClaveIncorrecta) {
				assertTrue(e.message!!.isNotEmpty())
			}
		}
		try {
			resultado(servidor)
			fail("El servidor debería fallar")
		} catch (e: ClaveIncorrecta) {
			assertTrue(e.message!!.isNotEmpty())
		}
	}

	@Test(timeout = 30_000)
	fun servidorConDosSecretosYElClienteUsaElSegundo() {
		var cifrasCliente = ""
		val servidor = conectar({ s ->
			val e = Enlace.servidor(s.entrada, s.salida, listOf(secretoA, secretoB))
			val texto = e.recibirTexto()
			e.enviarTexto("visto: $texto")
			Triple(e.secretoUsado, e.comprobacion, texto)
		}) { s ->
			val e = Enlace.cliente(s.entrada, s.salida, secretoB)
			cifrasCliente = e.comprobacion
			e.enviarTexto("con el código corto")
			assertEquals("visto: con el código corto", e.recibirTexto())
		}
		val (usado, cifras, texto) = resultado(servidor)
		assertEquals(1, usado)
		assertEquals(cifrasCliente, cifras)
		assertEquals("con el código corto", texto)
	}

	@Test(timeout = 30_000)
	fun lasCifrasCambianEnCadaConexion() {
		val vistas = HashSet<String>()
		repeat(6) {
			val servidor = conectar({ s -> Enlace.servidor(s.entrada, s.salida, listOf(secretoA)).comprobacion }) { s ->
				Enlace.cliente(s.entrada, s.salida, secretoA)
			}
			vistas += resultado(servidor)
		}
		// Con claves efímeras, 6 conexiones con las mismas 4 cifras sería casualidad de 1 entre 10^20
		assertTrue(vistas.size > 1)
	}

	/** Deja pasar todo lo que escribe el servidor, pero cambia un bit del byte [posicion] (contado desde el principio). */
	private class Saboteador(private val dentro: OutputStream, private val posicion: Long) : OutputStream() {
		private var escritos = 0L

		override fun write(b: Int) {
			dentro.write(if (escritos++ == posicion) b xor 0x01 else b)
		}

		override fun write(b: ByteArray, off: Int, len: Int) {
			for (i in off until off + len) write(b[i].toInt())
		}

		override fun flush() = dentro.flush()
	}

	@Test(timeout = 30_000)
	fun tramaManipuladaDaExcepcion() {
		// El servidor manda: saludo (unos 114 bytes), confirmación (4 + 39) y luego una trama
		// de datos. Se estropea un byte de esa última, ya dentro del texto cifrado.
		var largoSaludo = 0
		val servidor = conectar({ s ->
			val salida = Contador(s.salida)
			val e = Enlace.servidor(s.entrada, salida, listOf(secretoA))
			largoSaludo = salida.total.toInt()
			e.enviarTexto("primero bien")
			e.recibirTexto()
		}) { s ->
			val e = Enlace.cliente(s.entrada, s.salida, secretoA)
			assertEquals("primero bien", e.recibirTexto())
			e.enviarTexto("sigue")
		}
		assertEquals("sigue", resultado(servidor))
		assertTrue(largoSaludo > 0)

		val roto = conectar({ s ->
			val e = Enlace.servidor(s.entrada, Saboteador(s.salida, largoSaludo + 4L + 3L), listOf(secretoA))
			e.enviarTexto("esto llega estropeado")
			// Espera a que el cliente cierre
			try {
				e.recibir()
			} catch (x: IOException) {
				"cerrado"
			}
		}) { s ->
			val e = Enlace.cliente(s.entrada, s.salida, secretoA)
			try {
				e.recibirTexto()
				fail("Una trama manipulada no debería descifrarse")
			} catch (x: IOException) {
				assertTrue(x !is EOFException)
			}
		}
		resultado(roto)
	}

	/** Cuenta los bytes que pasan. */
	private class Contador(private val dentro: OutputStream) : OutputStream() {
		@Volatile
		var total = 0L

		override fun write(b: Int) {
			total++
			dentro.write(b)
		}

		override fun write(b: ByteArray, off: Int, len: Int) {
			total += len
			dentro.write(b, off, len)
		}

		override fun flush() = dentro.flush()
	}

	@Test(timeout = 30_000)
	fun tramaRepetidaDaExcepcion() {
		// Un flujo grabado: se repite la misma trama dos veces y la segunda no vale (contador)
		val grabado = ByteArrayOutputStream()
		val servidor = conectar({ s ->
			val salida = Duplicador(s.salida)
			val e = Enlace.servidor(s.entrada, salida, listOf(secretoA))
			salida.duplicar = true
			e.enviarTexto("una vez")
			"ok"
		}) { s ->
			val e = Enlace.cliente(s.entrada, s.salida, secretoA)
			assertEquals("una vez", e.recibirTexto())
			try {
				e.recibirTexto()
				fail("La trama repetida no debería valer")
			} catch (x: IOException) {
				grabado.write(1)
			}
		}
		assertEquals("ok", resultado(servidor))
		assertEquals(1, grabado.size())
	}

	/** Cuando [duplicar] está activo, cada escritura se manda dos veces. */
	private class Duplicador(private val dentro: OutputStream) : OutputStream() {
		@Volatile
		var duplicar = false

		override fun write(b: Int) = dentro.write(b)

		override fun write(b: ByteArray, off: Int, len: Int) {
			dentro.write(b, off, len)
			if (duplicar) dentro.write(b, off, len)
		}

		override fun flush() = dentro.flush()
	}

	@Test(timeout = 30_000)
	fun longitudMaxima() {
		val servidor = conectar({ s ->
			val e = Enlace.servidor(s.entrada, s.salida, listOf(secretoA), maximo = 1000)
			try {
				e.enviar(ByteArray(1001))
				"no falló"
			} catch (x: IOException) {
				e.enviar(ByteArray(1000))
				"falló bien"
			}
		}) { s ->
			// El cliente admite menos: la trama de 1000 bytes es demasiado larga para él
			val e = Enlace.cliente(s.entrada, s.salida, secretoA, maximo = 100)
			try {
				e.recibir()
				fail("Debería rechazar una trama más larga que su máximo")
			} catch (x: IOException) {
				assertTrue(x !is EOFException)
			}
		}
		assertEquals("falló bien", resultado(servidor))
	}

	@Test(timeout = 30_000)
	fun alCerrarseElFlujoDaEof() {
		val servidor = conectar({ s ->
			Enlace.servidor(s.entrada, s.salida, listOf(secretoA))
			"cerrado"
		}) { s ->
			val e = Enlace.cliente(s.entrada, s.salida, secretoA)
			try {
				e.recibir()
				fail("Debería dar EOF")
			} catch (x: EOFException) {
				assertTrue(true)
			}
		}
		assertEquals("cerrado", resultado(servidor))
	}

	@Test(timeout = 30_000)
	fun otroProtocoloSeRechaza() {
		val entrada: InputStream = "GET / HTTP/1.1\r\nHost: x\r\n\r\n".byteInputStream()
		try {
			Enlace.cliente(entrada, ByteArrayOutputStream(), secretoA)
			fail("Debería rechazarlo")
		} catch (e: IOException) {
			assertTrue(e !is ClaveIncorrecta)
		}
	}

	@Test(timeout = 30_000)
	fun hkdfSegunLaRfc5869() {
		// Caso de prueba 1 de la RFC 5869
		fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
		val ikm = hex("0b".repeat(22))
		val sal = hex("000102030405060708090a0b0c")
		val info = hex("f0f1f2f3f4f5f6f7f8f9")
		val okm = Hkdf.derivar(ikm, sal, info, 42)
		assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
	}
}
