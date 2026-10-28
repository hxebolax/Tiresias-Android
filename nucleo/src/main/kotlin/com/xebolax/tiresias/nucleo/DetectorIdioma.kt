package com.xebolax.tiresias.nucleo

/**
 * El idioma de un texto que la app no marca (sin LocaleSpan), entre español e
 * inglés: para que un correo o una web en inglés no se lea con la voz española.
 * Cuenta las palabras más frecuentes de cada idioma (sin las que comparten) y
 * los signos propios del español (ñ, tildes, ¿, ¡). Solo decide con textos de
 * seis palabras o más y cuando uno gana con claridad; si no, null.
 */
object DetectorIdioma {
	private val ESPANOL = setOf(
		"de", "la", "que", "el", "en", "y", "los", "del", "se", "las", "por", "un", "para", "con", "una", "su", "al", "lo", "como",
		"más", "pero", "sus", "le", "ya", "este", "sí", "porque", "esta", "entre", "cuando", "muy", "sin", "sobre", "también",
		"hasta", "hay", "donde", "quien", "desde", "todo", "nos", "durante", "todos", "uno", "les", "ni", "contra", "otros",
		"ese", "eso", "ante", "ellos", "esto", "antes", "algunos", "qué", "unos", "yo", "otro", "otras", "otra", "él", "tanto",
		"esa", "estos", "mucho", "nada", "muchos", "cual", "poco", "ella", "estar", "estas", "algo", "nosotros", "es", "son",
		"está", "fue", "ha", "tiene", "puede", "hace", "aquí", "ahora", "bien", "gracias", "hola", "mensaje", "día",
	)
	private val INGLES = setOf(
		"the", "be", "to", "of", "and", "in", "that", "have", "it", "for", "not", "on", "with", "as", "you", "do", "at", "this",
		"but", "his", "by", "from", "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would", "there",
		"their", "what", "so", "up", "out", "if", "about", "who", "get", "which", "go", "when", "make", "can", "like", "just",
		"him", "know", "take", "into", "your", "some", "could", "them", "see", "other", "than", "then", "now", "look", "only",
		"come", "its", "over", "think", "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
		"even", "new", "want", "because", "any", "these", "give", "day", "most", "us", "is", "are", "was", "were", "has", "had",
		"thanks", "hello", "please", "message",
	)
	/** Las que se escriben igual en los dos idiomas no dicen nada. */
	private val COMUNES = ESPANOL intersect INGLES
	private val SIGNOS_ESPANOL = Regex("[ñÑáéíóúÁÉÍÓÚ¿¡]")
	private val PALABRA = Regex("[\\p{L}']+")

	/** `"es"`, `"en"` o null si no está claro. */
	fun detectar(texto: String): String? {
		val palabras = PALABRA.findAll(texto.lowercase()).map { it.value }.toList()
		if (palabras.size < 6) return null
		var es = palabras.count { it in ESPANOL && it !in COMUNES }
		val en = palabras.count { it in INGLES && it !in COMUNES }
		es += minOf(3, SIGNOS_ESPANOL.findAll(texto).count())
		return when {
			es >= 3 && es >= 2 * en + 1 -> "es"
			en >= 3 && en >= 2 * es + 1 -> "en"
			else -> null
		}
	}
}
