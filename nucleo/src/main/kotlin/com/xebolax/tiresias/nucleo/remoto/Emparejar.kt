package com.xebolax.tiresias.nucleo.remoto

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Una persona de confianza en la ayuda remota: los dos móviles guardan la misma [id] y el
 * mismo [secreto] desde que se emparejaron. [nombre] es cómo llama este móvil al otro y
 * [desde], cuándo se emparejaron (segundos Unix).
 */
data class Pareja(val id: String, val nombre: String, val secreto: ByteArray, val desde: Long) {
	override fun equals(other: Any?): Boolean = other is Pareja && other.id == id && other.nombre == nombre &&
		other.desde == desde && other.secreto.contentEquals(secreto)

	override fun hashCode(): Int = id.hashCode()

	/** Sin el secreto, para que no acabe en un registro por descuido. */
	override fun toString(): String = "Pareja(id=$id, nombre=$nombre, desde=$desde)"
}

/**
 * El emparejamiento de la ayuda remota, para que una invitación robada o dictada por un
 * estafador no sirva de nada: quien entra demuestra que tiene el secreto de una pareja, sin
 * mandarlo, y solo dentro del canal ya cifrado ([Enlace]). También la oferta: un texto corto
 * con el que quien ayuda se ofrece, por WhatsApp o SMS, y que Tiresias reconoce en el otro móvil.
 */
object Emparejar {
	const val PREFIJO_OFERTA = "TIRA"
	const val MINUTOS_OFERTA = 15
	private const val LARGO_ID = 16
	private const val LARGO_SECRETO = 32
	private const val LARGO_PRUEBA = 16
	private const val LARGO_FIRMA = 8
	private val aleatorio = SecureRandom()

	/** Una pareja nueva, con su identificador y su secreto al azar. */
	fun nueva(nombre: String, ahora: Long): Pareja {
		val id = ByteArray(LARGO_ID).also { aleatorio.nextBytes(it) }
		val secreto = ByteArray(LARGO_SECRETO).also { aleatorio.nextBytes(it) }
		return Pareja(Hex.codificar(id), nombre, secreto, ahora)
	}

	/** La prueba de que se tiene el secreto de [p], ligada a [nonce] (la comprobación del canal): «id:firma». */
	fun prueba(p: Pareja, nonce: String): String =
		p.id + ":" + Hex.codificar(firmar(p.secreto, "prueba", nonce.toByteArray(Charsets.UTF_8)).copyOf(LARGO_PRUEBA))

	/** La pareja de [lista] que demuestra alguna de las [pruebas] con [nonce], o null. */
	fun comprobar(lista: List<Pareja>, pruebas: List<String>, nonce: String): Pareja? {
		for (texto in pruebas) {
			val id = texto.substringBefore(':', "")
			val p = lista.firstOrNull { it.id == id } ?: continue
			if (MessageDigest.isEqual(prueba(p, nonce).toByteArray(Charsets.UTF_8), texto.toByteArray(Charsets.UTF_8))) return p
		}
		return null
	}

	/** El texto con el que quien ayuda se ofrece a [p]: vale [MINUTOS_OFERTA] minutos desde [ahora]. */
	fun oferta(p: Pareja, ahora: Long): String {
		val cuerpo = cuerpoOferta(p, Math.floorDiv(ahora, 60L) + MINUTOS_OFERTA)
		return PREFIJO_OFERTA + Base32.codificar(cuerpo + firmar(p.secreto, "oferta", cuerpo).copyOf(LARGO_FIRMA))
	}

	/** Busca en [texto] una oferta válida y sin caducar de una pareja de [lista]; null si no la hay. */
	fun leerOferta(texto: String, lista: List<Pareja>, ahora: Long): Pareja? {
		val mayusculas = texto.uppercase()
		val i = mayusculas.indexOf(PREFIJO_OFERTA)
		if (i < 0) return null
		val letras = StringBuilder()
		var j = i + PREFIJO_OFERTA.length
		val largo = ((LARGO_ID + 4 + LARGO_FIRMA) * 8 + 4) / 5
		while (j < mayusculas.length && letras.length < largo) {
			val c = mayusculas[j++]
			if (Base32.valor(c) >= 0) letras.append(c) else if (c != ' ' && c != '-' && c != '\n') break
		}
		if (letras.length < largo) return null
		val b = Base32.decodificar(letras)
		if (b.size < LARGO_ID + 4 + LARGO_FIRMA) return null
		val id = Hex.codificar(b.copyOf(LARGO_ID))
		val p = lista.firstOrNull { it.id == id } ?: return null
		val hasta = ByteBuffer.wrap(b, LARGO_ID, 4).int.toLong() and 0xFFFFFFFFL
		val cuerpo = b.copyOf(LARGO_ID + 4)
		val firma = b.copyOfRange(LARGO_ID + 4, LARGO_ID + 4 + LARGO_FIRMA)
		if (!MessageDigest.isEqual(firma, firmar(p.secreto, "oferta", cuerpo).copyOf(LARGO_FIRMA))) return null
		return if (Math.floorDiv(ahora, 60L) < hasta) p else null
	}

	/** El secreto de [p] en texto, para mandarlo dentro del canal cifrado. */
	fun secretoTexto(p: Pareja): String = Hex.codificar(p.secreto)

	/** La pareja que llega en un mensaje, o null si el secreto no es válido. */
	fun deMensaje(id: String, secreto: String, nombre: String, ahora: Long): Pareja? {
		val s = Hex.decodificar(secreto)?.takeIf { it.size == LARGO_SECRETO } ?: return null
		return Pareja(id, nombre, s, ahora)
	}

	/** La lista en JSON, para guardarla cifrada. */
	fun aJson(lista: List<Pareja>): String = Json.escribir(linkedMapOf<String, Any?>("parejas" to lista.map {
		linkedMapOf<String, Any?>("id" to it.id, "nombre" to it.nombre, "secreto" to Hex.codificar(it.secreto), "desde" to it.desde)
	}))

	/** La lista de [json]; lo que esté mal se deja fuera. No lanza. */
	fun deJson(json: String): List<Pareja> {
		val m = Json.objeto(json) ?: return emptyList()
		return m.lista("parejas").orEmpty().mapNotNull { e ->
			val o = Json.comoMapa(e) ?: return@mapNotNull null
			val secreto = o.texto("secreto")?.let { Hex.decodificar(it) } ?: return@mapNotNull null
			Pareja(o.texto("id") ?: return@mapNotNull null, o.texto("nombre") ?: return@mapNotNull null, secreto, o.entero("desde") ?: return@mapNotNull null)
		}
	}

	private fun cuerpoOferta(p: Pareja, hastaMinuto: Long): ByteArray {
		val b = ByteArrayOutputStream()
		b.write(Hex.decodificar(p.id) ?: ByteArray(LARGO_ID))
		b.write(ByteBuffer.allocate(4).putInt(hastaMinuto.toInt()).array())
		return b.toByteArray()
	}

	private fun firmar(secreto: ByteArray, para: String, datos: ByteArray): ByteArray {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(secreto, "HmacSHA256"))
		mac.update(("tiresias-pareja-$para").toByteArray(Charsets.UTF_8))
		return mac.doFinal(datos)
	}
}

/** Hexadecimal en minúsculas. */
internal object Hex {
	private const val DIGITOS = "0123456789abcdef"

	fun codificar(b: ByteArray): String {
		val s = StringBuilder(b.size * 2)
		for (x in b) {
			val v = x.toInt() and 0xFF
			s.append(DIGITOS[v shr 4]).append(DIGITOS[v and 0xF])
		}
		return s.toString()
	}

	/** Los bytes de [texto], o null si no es hexadecimal. */
	fun decodificar(texto: String): ByteArray? {
		if (texto.length % 2 != 0) return null
		val b = ByteArray(texto.length / 2)
		for (i in b.indices) {
			val alto = Character.digit(texto[i * 2], 16)
			val bajo = Character.digit(texto[i * 2 + 1], 16)
			if (alto < 0 || bajo < 0) return null
			b[i] = (alto * 16 + bajo).toByte()
		}
		return b
	}
}
