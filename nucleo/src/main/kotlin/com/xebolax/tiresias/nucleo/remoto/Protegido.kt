package com.xebolax.tiresias.nucleo.remoto

import java.util.Locale

/**
 * Lo que no pasa por la ayuda remota aunque quien ayuda sea de confianza: las apps de banca y
 * pagos, los códigos de un solo uso y los enlaces que instalan algo. Es la defensa contra el
 * guion de las estafas de falso soporte técnico (INCIBE): con un móvil enlazado, lo primero
 * que buscan es entrar en el banco y leer el código que llega por SMS.
 */
object Protegido {
	/** Fragmentos que basta con que aparezcan dentro del paquete o del nombre. */
	private val FRAGMENTOS = listOf(
		"bank", "banco", "banca", "bbva", "caixa", "santander", "sabadell", "kutxa", "unicaja", "abanca", "ibercaja", "cajamar",
		"cajasur", "cajarural", "ruralvia", "liberbank", "myinvestor", "pibank", "triodos", "revolut", "bizum", "paypal", "wallet",
		"cartera", "binance", "coinbase", "bitpanda", "kraken", "traderepublic", "degiro", "ingdirect",
	)

	/** Palabras que tienen que ir solas, porque dentro de otra no significan nada («ing», «pay»). */
	private val PALABRAS = setOf("ing", "evo", "pay", "spay", "n26", "wise", "imagin", "openbank", "bnext", "verse", "twint")

	private val CLAVES = Regex(
		"c[oó]digo|clave|contrase[nñ]a|verificaci[oó]n|\\bpin\\b|\\botp\\b|bizum|token|\\bcode\\b|passcode|password|verification",
		RegexOption.IGNORE_CASE,
	)
	private val CIFRAS = Regex("(?<![\\p{L}\\d])\\d(?:[ -]?\\d){3,7}(?![\\p{L}\\d])")
	private val SOLO_CIFRAS = Regex("^\\s*\\d(?:[ -]?\\d){3,7}\\s*$")

	/** Si la app es de banca o de pagos, por su paquete o por su [nombre] visible. */
	fun esAppProtegida(paquete: String, nombre: String? = null): Boolean {
		val texto = (paquete + " " + nombre.orEmpty()).lowercase(Locale.ROOT)
		if (FRAGMENTOS.any { it in texto }) return true
		return texto.split(Regex("[^\\p{L}\\p{N}]+")).any { it in PALABRAS }
	}

	/**
	 * [texto] con los códigos cambiados por [protegido]: las cifras de 4 a 8 dígitos si cerca
	 * se habla de un código o una clave, o si el texto es solo el número. Tapa de más antes que
	 * de menos: la persona ayudada lo sigue oyendo entero en su móvil.
	 */
	fun taparCodigos(texto: String, protegido: String): String = when {
		SOLO_CIFRAS.matches(texto) -> protegido
		CLAVES.containsMatchIn(texto) -> CIFRAS.replace(texto) { protegido }
		else -> texto
	}

	/** Si quien ayuda puede abrir este enlace: web y tienda sí; un .apk o cualquier otro esquema, no. */
	fun enlaceSeguro(enlace: String): Boolean {
		val e = enlace.trim().lowercase(Locale.ROOT)
		val esquema = e.substringBefore(':', "")
		if (esquema !in setOf("http", "https", "market")) return false
		return !e.substringBefore('?').substringBefore('#').endsWith(".apk")
	}
}
