package com.xebolax.tiresias.nucleo.remoto

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Conectar por un proxy SOCKS5 local sin contraseña (RFC 1928): el de Tor, para llegar a una dirección .onion. */
object Socks5 {
	private const val VERSION = 5
	private const val SIN_AUTENTICACION = 0
	private const val CONNECT = 1
	private const val POR_NOMBRE = 3

	/**
	 * Abre una conexión a [host]:[puerto] por el proxy de `127.0.0.1:`[puertoProxy]. Devuelve
	 * el socket ya unido (sin plazo de lectura); si el proxy dice que no llega, lanza.
	 *
	 * @throws IOException si falla o tarda más de [esperaMs].
	 */
	fun conectar(puertoProxy: Int, host: String, puerto: Int, esperaMs: Int): Socket {
		val nombre = host.toByteArray(Charsets.US_ASCII)
		require(nombre.size in 1..255) { "El nombre de un host SOCKS tiene de 1 a 255 bytes" }
		val s = Socket()
		try {
			s.tcpNoDelay = true
			s.soTimeout = esperaMs
			s.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), puertoProxy), esperaMs)
			val salida = s.getOutputStream()
			val entrada = DataInputStream(s.getInputStream())
			salida.write(byteArrayOf(VERSION.toByte(), 1, SIN_AUTENTICACION.toByte()))
			salida.flush()
			val metodo = ByteArray(2)
			entrada.readFully(metodo)
			if (metodo[0].toInt() != VERSION || metodo[1].toInt() != SIN_AUTENTICACION) throw IOException("El proxy no acepta entrar sin contraseña")
			val pedido = ByteArray(7 + nombre.size)
			pedido[0] = VERSION.toByte()
			pedido[1] = CONNECT.toByte()
			pedido[3] = POR_NOMBRE.toByte()
			pedido[4] = nombre.size.toByte()
			nombre.copyInto(pedido, 5)
			pedido[5 + nombre.size] = (puerto shr 8).toByte()
			pedido[6 + nombre.size] = puerto.toByte()
			salida.write(pedido)
			salida.flush()
			val cabecera = ByteArray(4)
			entrada.readFully(cabecera)
			if (cabecera[0].toInt() != VERSION) throw IOException("Respuesta del proxy no válida")
			val estado = cabecera[1].toInt() and 0xFF
			if (estado != 0) throw IOException("El proxy no llega al destino (código $estado)")
			val largo = when (cabecera[3].toInt()) {
				1 -> 4
				4 -> 16
				POR_NOMBRE -> entrada.readUnsignedByte()
				else -> throw IOException("Tipo de dirección del proxy no válido")
			}
			entrada.readFully(ByteArray(largo + 2))
			s.soTimeout = 0
			return s
		} catch (e: EOFException) {
			cerrar(s)
			throw IOException("El proxy cerró la conexión", e)
		} catch (e: IOException) {
			cerrar(s)
			throw e
		}
	}

	private fun cerrar(s: Socket) {
		try {
			s.close()
		} catch (e: IOException) {
			// El socket ya no sirve; lo que importa es el error de conectar, que sigue su camino
			e.addSuppressed(IOException("No se pudo cerrar el socket del proxy"))
		}
	}
}
