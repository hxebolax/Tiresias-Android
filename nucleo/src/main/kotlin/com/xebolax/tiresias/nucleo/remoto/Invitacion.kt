package com.xebolax.tiresias.nucleo.remoto

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Una dirección donde escucha el ayudado. [ip] son 4 bytes (IPv4) o 16 (IPv6), en el orden
 * de red; [puerto] va de 1 a 65535.
 */
data class Candidato(val ip: ByteArray, val puerto: Int) {
	init {
		require(ip.size == 4 || ip.size == 16) { "Una IP tiene 4 o 16 bytes, no ${ip.size}" }
		require(puerto in 1..65535) { "Puerto fuera de rango: $puerto" }
	}

	val esIpv6: Boolean get() = ip.size == 16

	/** La dirección para conectar. Una IPv6 con forma de IPv4 (`::ffff:a.b.c.d`) sale como IPv4. */
	fun direccion(): InetAddress = InetAddress.getByAddress(ip)

	override fun equals(other: Any?): Boolean = other is Candidato && other.puerto == puerto && other.ip.contentEquals(ip)

	override fun hashCode(): Int = 31 * ip.contentHashCode() + puerto

	override fun toString(): String = if (esIpv6) "[${direccion().hostAddress}]:$puerto" else "${direccion().hostAddress}:$puerto"

	companion object {
		/** Un candidato a partir de una dirección ya hecha. */
		fun de(direccion: InetAddress, puerto: Int) = Candidato(direccion.address, puerto)
	}
}

/**
 * El servicio onion de Tor del ayudado (de datos móviles a datos móviles, sin abrir puertos).
 * [direccion] son las 56 letras de la v3, sin «.onion», en minúsculas.
 */
data class Onion(val direccion: String, val puerto: Int) {
	init {
		require(direccion.length == 56 && direccion.all { it in 'a'..'z' || it in '2'..'7' }) { "Dirección onion no válida" }
		require(puerto in 1..65535) { "Puerto fuera de rango: $puerto" }
	}

	/** El nombre para conectar por Tor. */
	val host: String get() = "$direccion.onion"
}

/**
 * Lo que el ayudado manda al ayudante (por WhatsApp, SMS…) para que se conecte a su móvil.
 *
 * @property secreto 16 bytes aleatorios: la sal de la clave del canal ([Enlace]).
 * @property caduca cuándo deja de valer, en segundos Unix. El código lo guarda en minutos, así
 *   que al leerlo sale redondeado al minuto (por eso [nueva] ya lo redondea hacia arriba).
 * @property nombre quién pide ayuda, para que el ayudante sepa a quién se conecta.
 * @property candidatos las direcciones donde escucha.
 * @property onion dónde espera por Tor, si lo tiene encendido.
 */
data class Invitacion(val secreto: ByteArray, val caduca: Long, val nombre: String, val candidatos: List<Candidato>, val onion: Onion? = null) {
	init {
		require(secreto.size == LARGO_SECRETO) { "El secreto tiene $LARGO_SECRETO bytes, no ${secreto.size}" }
		require(candidatos.size <= MAXIMO_CANDIDATOS) { "Demasiados candidatos: ${candidatos.size}" }
	}

	/**
	 * El código para el mensaje: «TIR1» seguido del Base32 (RFC 4648, mayúsculas, sin relleno)
	 * de estos bytes:
	 *
	 * | bytes | contenido |
	 * |---|---|
	 * | 1 | versión del formato (1, o 2 si lleva onion) |
	 * | 16 | secreto |
	 * | 4 | caducidad en minutos Unix, sin signo, big-endian |
	 * | 1 + n | longitud y nombre en UTF-8, recortado a 40 bytes sin partir caracteres |
	 * | 1 | número de candidatos |
	 * | 1 + 4/16 + 2 | cada candidato: tipo (4 o 6), IP y puerto big-endian |
	 * | 35 + 2 | solo en la versión 2: el onion (los bytes de sus 56 letras) y su puerto |
	 * | 2 | CRC-16/CCITT-FALSE de todo lo anterior |
	 */
	fun codigo(): String {
		val b = ByteArrayOutputStream()
		b.write(if (onion == null) VERSION else VERSION_ONION)
		b.write(secreto)
		val minutos = Math.floorDiv(caduca, 60L).coerceIn(0L, 0xFFFFFFFFL)
		b.write(ByteBuffer.allocate(4).putInt(minutos.toInt()).array())
		val n = recortar(nombre, MAXIMO_NOMBRE).toByteArray(Charsets.UTF_8)
		b.write(n.size)
		b.write(n)
		b.write(candidatos.size)
		for (c in candidatos) {
			b.write(if (c.esIpv6) 6 else 4)
			b.write(c.ip)
			b.write(c.puerto shr 8)
			b.write(c.puerto and 0xFF)
		}
		onion?.let {
			b.write(Base32.decodificar(it.direccion.uppercase()))
			b.write(it.puerto shr 8)
			b.write(it.puerto and 0xFF)
		}
		val crc = Crc16.calcular(b.toByteArray())
		b.write(crc shr 8)
		b.write(crc and 0xFF)
		return PREFIJO + Base32.codificar(b.toByteArray())
	}

	/** Si ya no vale en el instante [ahora] (segundos Unix). */
	fun caducada(ahora: Long): Boolean = ahora >= caduca

	override fun equals(other: Any?): Boolean = other is Invitacion && other.caduca == caduca && other.nombre == nombre &&
		other.candidatos == candidatos && other.onion == onion && other.secreto.contentEquals(secreto)

	override fun hashCode(): Int = ((secreto.contentHashCode() * 31 + caduca.hashCode()) * 31 + nombre.hashCode()) * 31 + candidatos.hashCode()

	/** Sin el secreto, para que no acabe en un registro por descuido. */
	override fun toString(): String = "Invitacion(caduca=$caduca, nombre=$nombre, candidatos=$candidatos, tor=${onion != null})"

	companion object {
		const val PREFIJO = "TIR1"
		const val VERSION = 1
		const val VERSION_ONION = 2
		private const val LARGO_ONION = 35
		const val LARGO_SECRETO = 16
		const val MAXIMO_NOMBRE = 40
		const val MAXIMO_CANDIDATOS = 255

		private val aleatorio = SecureRandom()

		/**
		 * Busca un código TIR1… dentro de un texto cualquiera (un mensaje de WhatsApp entero),
		 * sin distinguir mayúsculas e ignorando espacios, saltos de línea y guiones dentro del
		 * código, y lo valida (versión, longitudes, CRC). null si no hay uno válido.
		 *
		 * El código sabe cuánto mide (lleva las longitudes dentro), así que lo que venga
		 * pegado detrás («…ABC gracias») no estorba.
		 */
		fun leer(texto: String): Invitacion? {
			val mayusculas = texto.uppercase()
			var desde = 0
			while (true) {
				val i = mayusculas.indexOf(PREFIJO, desde)
				if (i < 0) return null
				val inicio = i + PREFIJO.length
				val letras = StringBuilder()
				var j = inicio
				while (j < mayusculas.length) {
					val c = mayusculas[j]
					if (Base32.valor(c) >= 0) {
						letras.append(c)
					} else if (!(c == '-' || c == ' ' || c == ' ' || c == '\n' || c == '\r' || c == '\t')) {
						break
					}
					j++
				}
				descifrar(Base32.decodificar(letras))?.let { return it }
				desde = i + 1
			}
		}

		/**
		 * Una nueva con secreto aleatorio ([SecureRandom]), que caduca a los [minutos] de
		 * [ahora] (segundos Unix), redondeado hacia arriba al minuto. El nombre se guarda ya
		 * recortado, igual que irá en el código.
		 */
		fun nueva(nombre: String, candidatos: List<Candidato>, minutos: Int, ahora: Long, onion: Onion? = null): Invitacion {
			val secreto = ByteArray(LARGO_SECRETO).also { aleatorio.nextBytes(it) }
			val fin = ahora + minutos.toLong() * 60L
			val caduca = Math.floorDiv(fin + 59L, 60L) * 60L
			return Invitacion(secreto, caduca, recortar(nombre, MAXIMO_NOMBRE), candidatos, onion)
		}

		/** Los bytes ya decodificados (puede haber de más al final) → la invitación, o null. */
		private fun descifrar(b: ByteArray): Invitacion? {
			var p = 0
			fun hay(n: Int) = p + n <= b.size
			if (!hay(1 + LARGO_SECRETO + 4 + 1)) return null
			val version = b[p++].toInt()
			if (version != VERSION && version != VERSION_ONION) return null
			val secreto = b.copyOfRange(p, p + LARGO_SECRETO)
			p += LARGO_SECRETO
			val minutos = ByteBuffer.wrap(b, p, 4).int.toLong() and 0xFFFFFFFFL
			p += 4
			val largoNombre = b[p++].toInt() and 0xFF
			if (largoNombre > MAXIMO_NOMBRE || !hay(largoNombre + 1)) return null
			val nombre = utf8(b.copyOfRange(p, p + largoNombre)) ?: return null
			p += largoNombre
			val cuantos = b[p++].toInt() and 0xFF
			val candidatos = ArrayList<Candidato>(cuantos)
			repeat(cuantos) {
				if (!hay(1)) return null
				val largo = when (b[p++].toInt()) {
					4 -> 4
					6 -> 16
					else -> return null
				}
				if (!hay(largo + 2)) return null
				val ip = b.copyOfRange(p, p + largo)
				p += largo
				val puerto = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
				p += 2
				if (puerto == 0) return null
				candidatos += Candidato(ip, puerto)
			}
			var onion: Onion? = null
			if (version == VERSION_ONION) {
				if (!hay(LARGO_ONION + 2)) return null
				val direccion = Base32.codificar(b.copyOfRange(p, p + LARGO_ONION)).lowercase()
				p += LARGO_ONION
				val puerto = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
				p += 2
				if (puerto == 0) return null
				onion = Onion(direccion, puerto)
			}
			if (!hay(2)) return null
			val crc = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
			if (crc != Crc16.calcular(b, 0, p)) return null
			return Invitacion(secreto, minutos * 60L, nombre, candidatos, onion)
		}

		/** UTF-8 estricto: null si los bytes no son UTF-8 válido. */
		private fun utf8(b: ByteArray): String? = try {
			Charsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(b))
				.toString()
		} catch (e: CharacterCodingException) {
			null
		}

		/** [texto] recortado a [maximo] bytes de UTF-8 sin partir ningún carácter (ni un par sustituto). */
		internal fun recortar(texto: String, maximo: Int): String {
			var bytes = 0
			var i = 0
			while (i < texto.length) {
				val cp = texto.codePointAt(i)
				val largo = when {
					cp < 0x80 -> 1
					cp < 0x800 -> 2
					cp < 0x10000 -> 3
					else -> 4
				}
				if (bytes + largo > maximo) break
				bytes += largo
				i += Character.charCount(cp)
			}
			return texto.substring(0, i)
		}
	}
}

/** Código corto de 8 cifras para la misma wifi: se dicta por teléfono. */
object CodigoCorto {
	private val aleatorio = SecureRandom()

	/** 8 cifras al azar ([SecureRandom]), con ceros a la izquierda si tocan. */
	fun nuevo(): String = "%08d".format(aleatorio.nextInt(100_000_000))

	/** Quita espacios y guiones; devuelve las 8 cifras o null si no son exactamente 8 cifras. */
	fun normalizar(texto: String): String? {
		val limpio = texto.filterNot { it == ' ' || it == '-' || it == ' ' || it == '\t' }
		if (limpio.length != 8 || limpio.any { it !in '0'..'9' }) return null
		return limpio
	}

	/**
	 * El secreto que sale del código: los 16 primeros bytes del SHA-256 de
	 * `"tiresias-ayuda-codigo:"` seguido de las cifras.
	 *
	 * @throws IllegalArgumentException si [codigo] no son 8 cifras (con espacios o guiones).
	 */
	fun secreto(codigo: String): ByteArray {
		val cifras = requireNotNull(normalizar(codigo)) { "El código corto tiene 8 cifras" }
		val resumen = MessageDigest.getInstance("SHA-256").digest(("tiresias-ayuda-codigo:$cifras").toByteArray(Charsets.UTF_8))
		return resumen.copyOf(Invitacion.LARGO_SECRETO)
	}

	/** «1234 5678», para leerlo o dictarlo. Si no es un código válido, el texto tal cual. */
	fun agrupado(codigo: String): String {
		val cifras = normalizar(codigo) ?: return codigo
		return cifras.substring(0, 4) + " " + cifras.substring(4)
	}
}

/** Clasificación de direcciones IP para elegir qué candidatos poner en la invitación. */
object Direcciones {
	private fun u(b: Byte) = b.toInt() and 0xFF

	/**
	 * Privada o de enlace local: 10/8, 172.16/12, 192.168/16, 169.254/16, y en IPv6
	 * fe80::/10 y fc00::/7.
	 */
	fun esPrivada(ip: InetAddress): Boolean {
		val b = ip.address
		return when (ip) {
			is Inet4Address -> {
				val a = u(b[0])
				val c = u(b[1])
				a == 10 || (a == 172 && c in 16..31) || (a == 192 && c == 168) || (a == 169 && c == 254)
			}
			is Inet6Address -> {
				val a = u(b[0])
				(a == 0xFE && (u(b[1]) and 0xC0) == 0x80) || (a and 0xFE) == 0xFC
			}
			else -> false
		}
	}

	/** Espacio compartido de los operadores (CGNAT): 100.64.0.0/10. */
	fun esCompartida(ip: InetAddress): Boolean {
		if (ip !is Inet4Address) return false
		val b = ip.address
		return u(b[0]) == 100 && (u(b[1]) and 0xC0) == 64
	}

	/**
	 * IPv6 global de unidifusión (2000::/3), salvo la de documentación 2001:db8::/32. Las
	 * temporales (de privacidad) valen igual: lo que importa es que se pueda llegar a ella.
	 */
	fun esIpv6Global(ip: InetAddress): Boolean {
		if (ip !is Inet6Address) return false
		val b = ip.address
		if ((u(b[0]) and 0xE0) != 0x20) return false
		val documentacion = u(b[0]) == 0x20 && u(b[1]) == 0x01 && u(b[2]) == 0x0D && u(b[3]) == 0xB8
		return !documentacion
	}

	/**
	 * IPv4 pública: ni privada, ni compartida, ni de bucle local (127/8), ni 0/8, ni de
	 * multidifusión (224/4) o reservada (240/4), ni de documentación o de pruebas
	 * (192.0.0/24, 192.0.2/24, 198.18/15, 198.51.100/24, 203.0.113/24).
	 */
	fun esPublicaIpv4(ip: InetAddress): Boolean {
		if (ip !is Inet4Address) return false
		if (esPrivada(ip) || esCompartida(ip)) return false
		val b = ip.address
		val a = u(b[0])
		val c = u(b[1])
		val d = u(b[2])
		return when {
			a == 0 || a == 127 || a >= 224 -> false
			a == 192 && c == 0 && (d == 0 || d == 2) -> false
			a == 198 && (c == 18 || c == 19) -> false
			a == 198 && c == 51 && d == 100 -> false
			a == 203 && c == 0 && d == 113 -> false
			else -> true
		}
	}
}

/** Base32 de la RFC 4648, con el alfabeto en mayúsculas y sin relleno. */
internal object Base32 {
	private const val ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

	/** El valor de [c] (0-31) o -1 si no es del alfabeto. Solo mayúsculas. */
	fun valor(c: Char): Int = when (c) {
		in 'A'..'Z' -> c - 'A'
		in '2'..'7' -> c - '2' + 26
		else -> -1
	}

	fun codificar(datos: ByteArray): String {
		val sb = StringBuilder((datos.size * 8 + 4) / 5)
		var acumulado = 0
		var bits = 0
		for (byte in datos) {
			acumulado = (acumulado shl 8) or (byte.toInt() and 0xFF)
			bits += 8
			while (bits >= 5) {
				bits -= 5
				sb.append(ALFABETO[(acumulado shr bits) and 31])
			}
		}
		if (bits > 0) sb.append(ALFABETO[(acumulado shl (5 - bits)) and 31])
		return sb.toString()
	}

	/** Los bytes completos de [letras] (que deben ser todas del alfabeto); los bits sobrantes se tiran. */
	fun decodificar(letras: CharSequence): ByteArray {
		val salida = ByteArrayOutputStream(letras.length * 5 / 8)
		var acumulado = 0
		var bits = 0
		for (c in letras) {
			val v = valor(c)
			require(v >= 0) { "«$c» no es Base32" }
			acumulado = ((acumulado shl 5) or v) and 0xFFFF
			bits += 5
			if (bits >= 8) {
				bits -= 8
				salida.write((acumulado shr bits) and 0xFF)
			}
		}
		return salida.toByteArray()
	}
}

/** CRC-16/CCITT-FALSE: polinomio 0x1021, valor inicial 0xFFFF, sin reflejar ni XOR final. */
internal object Crc16 {
	fun calcular(datos: ByteArray, desde: Int = 0, hasta: Int = datos.size): Int {
		var crc = 0xFFFF
		for (i in desde until hasta) {
			crc = crc xor ((datos[i].toInt() and 0xFF) shl 8)
			repeat(8) {
				crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
				crc = crc and 0xFFFF
			}
		}
		return crc
	}
}
