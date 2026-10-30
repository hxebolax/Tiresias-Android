package com.xebolax.tiresias.nucleo.remoto

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** El otro lado no tiene el mismo secreto: la invitación o el código corto no son los suyos. */
class ClaveIncorrecta : IOException("El otro lado no tiene la misma clave")

/**
 * Canal cifrado de extremo a extremo sobre un flujo cualquiera (normalmente un `Socket`).
 * No abre ni cierra el flujo: eso lo hace quien lo crea.
 *
 * ## Protocolo, versión 1
 *
 * 1. **Saludo.** Cada lado manda, sin esperar al otro:
 *    `"TIRA"` (4 bytes ASCII) · versión (1 byte = 1) · longitud de la clave (2 bytes,
 *    big-endian, de 1 a 200) · clave pública EC P-256 efímera en X.509
 *    (`PublicKey.encoded`), nueva en cada conexión · 16 bytes aleatorios.
 * 2. **Claves.** Con el secreto ECDH de las dos claves efímeras:
 *    `HKDF-SHA256(ikm = secreto ECDH, salt = secreto compartido de 16 bytes,
 *    info = "tiresias-ayuda-1" ‖ saludo del cliente ‖ saludo del servidor)` → 64 bytes:
 *    los 32 primeros cifran del cliente al servidor y los 32 siguientes del servidor al
 *    cliente. Los saludos entran enteros, tal como viajaron.
 * 3. **Cifras de comprobación.** `HKDF-SHA256` igual, pero con
 *    `info = "tiresias-comprobar" ‖ saludos` → 2 bytes → número big-endian % 10000, con
 *    ceros a la izquierda. Los dos lados ven las mismas 4 cifras; si alguien se mete en
 *    medio, no coinciden.
 * 4. **Tramas.** 4 bytes big-endian de longitud y el texto cifrado con AES-256-GCM
 *    (etiqueta de 128 bits, sin datos adicionales). El nonce, de 12 bytes, es 4 ceros y el
 *    contador de 8 bytes de ese sentido, que empieza en 0 y sube con cada trama: una trama
 *    repetida, quitada o cambiada de orden no se descifra.
 * 5. **Confirmación.** La primera trama del cliente (contador 0) es
 *    `"tiresias-ayuda-cliente"`. El servidor, que puede aceptar varios secretos (el de la
 *    invitación y el del código corto), prueba con las claves de cada uno: el que la
 *    descifra es el bueno ([secretoUsado]). Si ninguno sirve, manda una trama vacía
 *    (longitud 0, sin cifrar), que el cliente entiende como «clave incorrecta», y los dos
 *    lanzan [ClaveIncorrecta]. Si sirve, contesta con `"tiresias-ayuda-servidor"`, que el
 *    cliente comprueba igual. Después ya van los datos, con contador 1 en adelante.
 *
 * [enviar] y [recibir] pueden llamarse a la vez desde hilos distintos (uno escribe y otro
 * lee); cada uno está sincronizado por su lado.
 */
class Enlace private constructor(
	entrada: InputStream,
	private val salida: OutputStream,
	claveEnvio: ByteArray,
	claveRecibo: ByteArray,
	/** Las 4 cifras de comprobación: las mismas en los dos lados si nadie se ha metido en medio. */
	val comprobacion: String,
	/** El índice del secreto que valió (en el servidor); 0 en el cliente. */
	val secretoUsado: Int,
	private val maximo: Int,
	contadorEnvio: Long,
	contadorRecibo: Long,
) {
	private val entrada = if (entrada is DataInputStream) entrada else DataInputStream(entrada)
	private val envio = Sentido(claveEnvio, contadorEnvio)
	private val recibo = Sentido(claveRecibo, contadorRecibo)

	/** La clave y el contador de un sentido, con su propio cifrador. */
	private class Sentido(clave: ByteArray, var contador: Long) {
		val clave = SecretKeySpec(clave, "AES")
		val cifrador: Cipher = Cipher.getInstance(CIFRADO)
	}

	/**
	 * Cifra y manda [datos] en una trama. Seguro entre hilos.
	 *
	 * @throws IOException si [datos] pasa del máximo o el flujo falla.
	 */
	fun enviar(datos: ByteArray) {
		if (datos.size > maximo) throw IOException("Mensaje demasiado largo: ${datos.size} bytes (máximo $maximo)")
		synchronized(envio) {
			val cifrado = cifrar(envio.cifrador, envio.clave, envio.contador, datos)
			envio.contador++
			escribirTrama(salida, cifrado)
		}
	}

	/**
	 * Espera la siguiente trama y la devuelve descifrada.
	 *
	 * @throws EOFException si el flujo se cierra.
	 * @throws IOException si la trama es demasiado larga o no se descifra (manipulada).
	 */
	fun recibir(): ByteArray = synchronized(recibo) {
		val trama = leerTrama(entrada, maximo)
		if (trama.isEmpty()) throw IOException("Trama vacía en mitad de la conexión")
		val datos = try {
			descifrar(recibo.cifrador, recibo.clave, recibo.contador, trama)
		} catch (e: AEADBadTagException) {
			throw IOException("Trama manipulada o fuera de orden", e)
		}
		recibo.contador++
		datos
	}

	/** [enviar] de un texto en UTF-8. */
	fun enviarTexto(texto: String) = enviar(texto.toByteArray(Charsets.UTF_8))

	/** [recibir] de un texto en UTF-8. */
	fun recibirTexto(): String = String(recibir(), Charsets.UTF_8)

	companion object {
		const val MAXIMO_POR_DEFECTO = 4 shl 20

		private const val CIFRADO = "AES/GCM/NoPadding"
		private const val ETIQUETA = 16
		private const val VERSION = 1
		private const val MAXIMO_CLAVE = 200
		private const val LARGO_AZAR = 16
		private val MAGIA = "TIRA".toByteArray(Charsets.US_ASCII)
		private val INFO_CLAVES = "tiresias-ayuda-1".toByteArray(Charsets.US_ASCII)
		private val INFO_COMPROBAR = "tiresias-comprobar".toByteArray(Charsets.US_ASCII)
		private val HOLA_CLIENTE = "tiresias-ayuda-cliente".toByteArray(Charsets.US_ASCII)
		private val HOLA_SERVIDOR = "tiresias-ayuda-servidor".toByteArray(Charsets.US_ASCII)

		private val aleatorio = SecureRandom()

		/**
		 * Abre el canal como cliente (el ayudante, que se conecta) con el [secreto] de la
		 * invitación o del código corto. Bloquea hasta terminar la confirmación.
		 *
		 * @throws ClaveIncorrecta si el servidor no tiene el mismo secreto.
		 * @throws IOException si el otro lado no habla este protocolo o se corta.
		 */
		fun cliente(entrada: InputStream, salida: OutputStream, secreto: ByteArray, maximo: Int = MAXIMO_POR_DEFECTO): Enlace {
			comprobarSecreto(secreto)
			val datos = DataInputStream(entrada)
			val pares = parDeClaves()
			val propio = saludo(pares.public)
			salida.write(propio)
			salida.flush()
			val ajeno = leerSaludo(datos)
			val ecdh = acordar(pares, ajeno.clave)
			val claves = derivar(ecdh, secreto, INFO_CLAVES, propio, ajeno.bytes, 64)
			val haciaServidor = claves.copyOfRange(0, 32)
			val haciaCliente = claves.copyOfRange(32, 64)
			escribirTrama(salida, cifrar(Cipher.getInstance(CIFRADO), SecretKeySpec(haciaServidor, "AES"), 0, HOLA_CLIENTE))
			val respuesta = leerTrama(datos, maximo)
			if (respuesta.isEmpty()) throw ClaveIncorrecta()
			val texto = try {
				descifrar(Cipher.getInstance(CIFRADO), SecretKeySpec(haciaCliente, "AES"), 0, respuesta)
			} catch (e: AEADBadTagException) {
				throw ClaveIncorrecta().apply { initCause(e) }
			}
			if (!texto.contentEquals(HOLA_SERVIDOR)) throw ClaveIncorrecta()
			val cifras = comprobacion(ecdh, secreto, propio, ajeno.bytes)
			return Enlace(datos, salida, haciaServidor, haciaCliente, cifras, 0, maximo, 1, 1)
		}

		/**
		 * Abre el canal como servidor (el ayudado, que escucha) aceptando cualquiera de los
		 * [secretos] (por ejemplo, el de la invitación y el del código corto). Bloquea hasta
		 * terminar la confirmación.
		 *
		 * @throws ClaveIncorrecta si el cliente no usa ninguno de ellos (antes se lo dice).
		 * @throws IOException si el otro lado no habla este protocolo o se corta.
		 */
		fun servidor(entrada: InputStream, salida: OutputStream, secretos: List<ByteArray>, maximo: Int = MAXIMO_POR_DEFECTO): Enlace {
			require(secretos.isNotEmpty()) { "Hace falta al menos un secreto" }
			secretos.forEach { comprobarSecreto(it) }
			val datos = DataInputStream(entrada)
			val pares = parDeClaves()
			val propio = saludo(pares.public)
			salida.write(propio)
			salida.flush()
			val ajeno = leerSaludo(datos)
			val ecdh = acordar(pares, ajeno.clave)
			val primera = leerTrama(datos, maximo)
			if (primera.isEmpty()) throw IOException("El cliente no mandó la confirmación")
			val cifrador = Cipher.getInstance(CIFRADO)
			for ((i, secreto) in secretos.withIndex()) {
				val claves = derivar(ecdh, secreto, INFO_CLAVES, ajeno.bytes, propio, 64)
				val haciaServidor = claves.copyOfRange(0, 32)
				val haciaCliente = claves.copyOfRange(32, 64)
				val texto = try {
					descifrar(cifrador, SecretKeySpec(haciaServidor, "AES"), 0, primera)
				} catch (e: AEADBadTagException) {
					// No es este secreto: se prueba el siguiente
					null
				}
				if (texto != null && texto.contentEquals(HOLA_CLIENTE)) {
					escribirTrama(salida, cifrar(cifrador, SecretKeySpec(haciaCliente, "AES"), 0, HOLA_SERVIDOR))
					val cifras = comprobacion(ecdh, secreto, ajeno.bytes, propio)
					return Enlace(datos, salida, haciaCliente, haciaServidor, cifras, i, maximo, 1, 1)
				}
			}
			escribirTrama(salida, ByteArray(0))
			throw ClaveIncorrecta()
		}

		private fun comprobarSecreto(secreto: ByteArray) {
			require(secreto.size == Invitacion.LARGO_SECRETO) { "El secreto tiene ${Invitacion.LARGO_SECRETO} bytes, no ${secreto.size}" }
		}

		private fun parDeClaves(): KeyPair {
			val generador = KeyPairGenerator.getInstance("EC")
			generador.initialize(ECGenParameterSpec("secp256r1"), aleatorio)
			return generador.generateKeyPair()
		}

		private fun saludo(clave: PublicKey): ByteArray {
			val codificada = clave.encoded
			val azar = ByteArray(LARGO_AZAR).also { aleatorio.nextBytes(it) }
			val b = ByteArray(MAGIA.size + 1 + 2 + codificada.size + LARGO_AZAR)
			var p = 0
			MAGIA.copyInto(b, p)
			p += MAGIA.size
			b[p++] = VERSION.toByte()
			b[p++] = (codificada.size shr 8).toByte()
			b[p++] = codificada.size.toByte()
			codificada.copyInto(b, p)
			p += codificada.size
			azar.copyInto(b, p)
			return b
		}

		/** El saludo del otro lado: sus bytes tal cual llegaron y su clave pública. */
		private class Saludo(val bytes: ByteArray, val clave: ECPublicKey)

		private fun leerSaludo(entrada: DataInputStream): Saludo {
			val cabecera = ByteArray(MAGIA.size + 1 + 2)
			entrada.readFully(cabecera)
			if (!cabecera.copyOfRange(0, MAGIA.size).contentEquals(MAGIA)) throw IOException("El otro lado no habla el protocolo de Tiresias")
			val version = cabecera[MAGIA.size].toInt() and 0xFF
			if (version != VERSION) throw IOException("Versión del protocolo distinta: $version")
			val largo = ((cabecera[MAGIA.size + 1].toInt() and 0xFF) shl 8) or (cabecera[MAGIA.size + 2].toInt() and 0xFF)
			if (largo !in 1..MAXIMO_CLAVE) throw IOException("Clave pública de longitud no válida: $largo")
			val resto = ByteArray(largo + LARGO_AZAR)
			entrada.readFully(resto)
			val clave = try {
				KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(resto.copyOfRange(0, largo)))
			} catch (e: GeneralSecurityException) {
				throw IOException("Clave pública no válida", e)
			}
			if (clave !is ECPublicKey) throw IOException("La clave pública no es EC")
			return Saludo(cabecera + resto, clave)
		}

		private fun acordar(propio: KeyPair, ajena: ECPublicKey): ByteArray {
			val mia = propio.public as ECPublicKey
			if (ajena.params.order != mia.params.order || ajena.params.curve != mia.params.curve) {
				throw IOException("La clave pública no es de la curva P-256")
			}
			return try {
				val acuerdo = KeyAgreement.getInstance("ECDH")
				acuerdo.init(propio.private)
				acuerdo.doPhase(ajena, true)
				acuerdo.generateSecret()
			} catch (e: GeneralSecurityException) {
				throw IOException("No se pudo acordar la clave", e)
			}
		}

		private fun derivar(ecdh: ByteArray, sal: ByteArray, etiqueta: ByteArray, saludoCliente: ByteArray, saludoServidor: ByteArray, largo: Int): ByteArray =
			Hkdf.derivar(ecdh, sal, etiqueta + saludoCliente + saludoServidor, largo)

		private fun comprobacion(ecdh: ByteArray, sal: ByteArray, saludoCliente: ByteArray, saludoServidor: ByteArray): String {
			val b = derivar(ecdh, sal, INFO_COMPROBAR, saludoCliente, saludoServidor, 2)
			val n = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
			return "%04d".format(n % 10000)
		}

		private fun nonce(contador: Long): ByteArray {
			val n = ByteArray(12)
			for (i in 0 until 8) n[4 + i] = (contador ushr (56 - 8 * i)).toByte()
			return n
		}

		private fun cifrar(cifrador: Cipher, clave: SecretKeySpec, contador: Long, datos: ByteArray): ByteArray {
			cifrador.init(Cipher.ENCRYPT_MODE, clave, GCMParameterSpec(ETIQUETA * 8, nonce(contador)))
			return cifrador.doFinal(datos)
		}

		/** @throws AEADBadTagException si la trama no es de esta clave y este contador. */
		private fun descifrar(cifrador: Cipher, clave: SecretKeySpec, contador: Long, trama: ByteArray): ByteArray {
			if (trama.size < ETIQUETA) throw AEADBadTagException("Trama más corta que la etiqueta")
			cifrador.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(ETIQUETA * 8, nonce(contador)))
			return cifrador.doFinal(trama)
		}

		/** Longitud y datos de una sola vez, para que no se mezclen dos tramas. */
		private fun escribirTrama(salida: OutputStream, datos: ByteArray) {
			val b = ByteArray(4 + datos.size)
			b[0] = (datos.size shr 24).toByte()
			b[1] = (datos.size shr 16).toByte()
			b[2] = (datos.size shr 8).toByte()
			b[3] = datos.size.toByte()
			datos.copyInto(b, 4)
			salida.write(b)
			salida.flush()
		}

		/** Una trama entera (una vacía es el aviso de clave incorrecta del servidor). */
		private fun leerTrama(entrada: DataInputStream, maximo: Int): ByteArray {
			val largo = entrada.readInt()
			if (largo < 0 || largo.toLong() > maximo.toLong() + ETIQUETA) throw IOException("Trama demasiado larga: $largo bytes")
			val b = ByteArray(largo)
			entrada.readFully(b)
			return b
		}
	}
}

/** HKDF con HMAC-SHA256 (RFC 5869). */
internal object Hkdf {
	private const val ALGORITMO = "HmacSHA256"

	/** Extrae con [sal] (vacía = 32 ceros) y expande con [info] hasta [largo] bytes (máx. 8160). */
	fun derivar(ikm: ByteArray, sal: ByteArray, info: ByteArray, largo: Int): ByteArray {
		require(largo in 1..255 * 32) { "HKDF-SHA256 da como mucho ${255 * 32} bytes" }
		val mac = Mac.getInstance(ALGORITMO)
		mac.init(SecretKeySpec(if (sal.isEmpty()) ByteArray(32) else sal, ALGORITMO))
		val prk = mac.doFinal(ikm)
		mac.init(SecretKeySpec(prk, ALGORITMO))
		val salida = ByteArray(largo)
		var anterior = ByteArray(0)
		var hecho = 0
		var i = 1
		while (hecho < largo) {
			mac.update(anterior)
			mac.update(info)
			mac.update(i.toByte())
			anterior = mac.doFinal()
			val n = minOf(anterior.size, largo - hecho)
			anterior.copyInto(salida, hecho, 0, n)
			hecho += n
			i++
		}
		return salida
	}
}
