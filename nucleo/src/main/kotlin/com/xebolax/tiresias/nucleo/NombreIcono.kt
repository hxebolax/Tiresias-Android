package com.xebolax.tiresias.nucleo

/**
 * El nombre propuesto para un botón o una imagen sin etiqueta, a partir de lo
 * que lee el OCR o de lo que responde la IA: corto (como mucho cuatro palabras),
 * sin comillas ni punto final, y nada si la respuesta no dice nada útil.
 */
object NombreIcono {
	private const val MAXIMO = 40
	private const val PALABRAS = 4

	/** Respuestas que quieren decir «no lo sé». */
	private val VACIAS = Regex(
		"^(no (lo )?s[eé]|desconocid[oa]|ninguno|nada|n/?a|unknown|none|i don.?t know|no (hay|se ve)( texto)?)\\b",
		RegexOption.IGNORE_CASE,
	)

	private val PREFIJO = Regex("^(nombre|name|bot[oó]n|button|icono|icon|etiqueta|label)\\s*[:：-]\\s*", RegexOption.IGNORE_CASE)

	/** Lo que se puede decir de la respuesta de la IA, o null. */
	fun deIa(respuesta: String): String? {
		var r = respuesta.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
		// «Nombre: Enviar», «Botón: atrás», «**Buscar**»
		r = r.replace(PREFIJO, "")
		// Comillas y puntuación del final, en el orden que vengan («buscar». o "Enviar.")
		var antes: String
		do {
			antes = r
			r = r.trim('*', '"', '\'', '«', '»', '“', '”', '`', ' ').trimEnd('.', '!', ';', ':', ',')
		} while (r != antes)
		if (r.isEmpty() || VACIAS.containsMatchIn(r)) return null
		val palabras = r.split(Regex("\\s+"))
		if (palabras.size > PALABRAS) return null
		return r.take(MAXIMO).replaceFirstChar { it.uppercaseChar() }
	}

	/** Lo que se puede decir del texto que lee el OCR en el icono, o null. */
	fun deOcr(lineas: List<Pair<String, Float>>, confianzaMinima: Float = 0.7f): String? {
		val texto = lineas.filter { it.second >= confianzaMinima }.joinToString(" ") { it.first.trim() }.trim()
		if (texto.length !in 1..MAXIMO) return null
		// Un solo carácter suelto que no es una letra o cifra suele ser un dibujo mal leído
		if (texto.length == 1 && !texto[0].isLetterOrDigit()) return null
		return texto
	}
}
