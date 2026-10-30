package com.xebolax.tiresias.nucleo.remoto

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Conectar con el ayudado probando todas sus direcciones a la vez. */
object Conectar {
	/** Cuánto se espera entre un intento y el siguiente, como en «Happy Eyeballs» (RFC 8305). */
	const val ESCALON_MS = 250L

	/**
	 * Prueba todos los [candidatos] a la vez (escalonados [ESCALON_MS] ms: primero las
	 * direcciones de la red local, luego las IPv6 globales y al final las IPv4 públicas) y
	 * devuelve el primer `Socket` conectado; cierra los demás. null si ninguno conecta en
	 * [esperaMs]. Bloquea; cada intento va en su propio hilo.
	 *
	 * @throws InterruptedException si interrumpen el hilo que espera (antes cierra los intentos).
	 */
	fun primero(candidatos: List<Candidato>, esperaMs: Int): Socket? {
		if (candidatos.isEmpty() || esperaMs <= 0) return null
		val orden = ordenar(candidatos)
		val carrera = Carrera(orden.size)
		val hasta = System.nanoTime() + esperaMs.toLong() * 1_000_000L
		for ((i, c) in orden.withIndex()) {
			val hilo = Thread({ intentar(c, i * ESCALON_MS, hasta, carrera) }, "Tiresias-conectar-$i")
			hilo.isDaemon = true
			hilo.start()
		}
		try {
			carrera.fin.await(esperaMs.toLong(), TimeUnit.MILLISECONDS)
		} finally {
			carrera.terminar()
		}
		return carrera.ganador
	}

	/** Local primero, luego IPv6 global, luego lo demás; dentro de cada grupo, el orden recibido. */
	internal fun ordenar(candidatos: List<Candidato>): List<Candidato> = candidatos.distinct().sortedBy { c ->
		val ip = c.direccion()
		when {
			Direcciones.esPrivada(ip) || ip.isLoopbackAddress -> 0
			Direcciones.esIpv6Global(ip) -> 1
			c.esIpv6 -> 2
			else -> 3
		}
	}

	private fun intentar(c: Candidato, retrasoMs: Long, hasta: Long, carrera: Carrera) {
		try {
			// Espera su turno, salvo que otro gane antes o se acaben todos
			if (retrasoMs > 0 && carrera.fin.await(retrasoMs, TimeUnit.MILLISECONDS)) {
				carrera.fallo()
				return
			}
		} catch (e: InterruptedException) {
			carrera.fallo()
			return
		}
		val queda = ((hasta - System.nanoTime()) / 1_000_000L).toInt()
		if (queda <= 0) {
			carrera.fallo()
			return
		}
		val socket = Socket()
		if (!carrera.apuntar(socket)) {
			cerrar(socket)
			carrera.fallo()
			return
		}
		try {
			socket.tcpNoDelay = true
			socket.connect(InetSocketAddress(c.direccion(), c.puerto), queda)
		} catch (e: IOException) {
			cerrar(socket)
			carrera.fallo()
			return
		}
		if (!carrera.ganar(socket)) cerrar(socket)
	}

	/** El estado compartido de los intentos: quién ganó y qué sockets hay que cerrar. */
	private class Carrera(private var pendientes: Int) {
		val fin = CountDownLatch(1)
		private val abiertos = ArrayList<Socket>()
		private var terminada = false

		@Volatile
		var ganador: Socket? = null
			private set

		/** Apunta [s] para cerrarlo al terminar; false si ya ha terminado. */
		@Synchronized
		fun apuntar(s: Socket): Boolean {
			if (terminada) return false
			abiertos += s
			return true
		}

		/** [s] ha conectado; true si es el primero (se queda con él). */
		@Synchronized
		fun ganar(s: Socket): Boolean {
			abiertos -= s
			if (terminada || ganador != null) return false
			ganador = s
			fin.countDown()
			return true
		}

		/** Un intento menos; si no queda ninguno, se acaba la espera. */
		@Synchronized
		fun fallo() {
			pendientes--
			if (pendientes <= 0) fin.countDown()
		}

		/** Cierra los intentos que siguen en marcha: el que esté conectando se corta. */
		fun terminar() {
			val sobran: List<Socket>
			synchronized(this) {
				terminada = true
				sobran = abiertos.toList()
				abiertos.clear()
			}
			sobran.forEach { cerrar(it) }
		}
	}

	/** @return si se cerró sin error (si falla, el socket ya no sirve de todos modos). */
	private fun cerrar(s: Socket): Boolean = try {
		s.close()
		true
	} catch (e: IOException) {
		false
	}
}
