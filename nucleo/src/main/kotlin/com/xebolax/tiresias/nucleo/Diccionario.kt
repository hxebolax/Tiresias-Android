package com.xebolax.tiresias.nucleo

/** Cómo se busca el patrón de una entrada del diccionario. */
enum class TipoEntrada { TEXTO, PALABRA, EXPRESION }

/**
 * Una entrada de diccionario de pronunciación: cambia [patron] por [reemplazo]
 * antes de mandar el texto a la voz. Como los diccionarios de NVDA y del Tiresias
 * de Windows (se pueden importar de ellos).
 */
data class EntradaDiccionario(
	val patron: String,
	val reemplazo: String,
	val tipo: TipoEntrada = TipoEntrada.PALABRA,
	val distinguirMayusculas: Boolean = false,
	val comentario: String = "",
) {
	/** La expresión regular de la entrada, o null si el patrón no es válido (expresión mal escrita). */
	val regex: Regex? by lazy {
		runCatching {
			val opciones = if (distinguirMayusculas) emptySet() else setOf(RegexOption.IGNORE_CASE)
			when (tipo) {
				TipoEntrada.TEXTO -> Regex(Regex.escape(patron), opciones)
				TipoEntrada.PALABRA -> Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(patron) + "(?![\\p{L}\\p{N}_])", opciones)
				TipoEntrada.EXPRESION -> Regex(patron, opciones)
			}
		}.getOrNull()
	}
}

/**
 * Un diccionario: una lista ordenada de entradas que se aplican una tras otra.
 * Tiresias aplica, en este orden, el de la app que está delante, el de la voz
 * y el general.
 */
class Diccionario(val entradas: List<EntradaDiccionario>) {

	fun aplicar(texto: String): String {
		var r = texto
		for (e in entradas) {
			val regex = e.regex ?: continue
			r = try {
				// En una expresión, el reemplazo admite $1; en texto y palabra, es literal
				if (e.tipo == TipoEntrada.EXPRESION) regex.replace(r, e.reemplazo) else regex.replace(r, Regex.escapeReplacement(e.reemplazo))
			} catch (ex: Exception) {
				// Un reemplazo mal escrito ($5 sin grupo): se deja el texto como estaba
				r
			}
		}
		return r
	}

	/** El diccionario en el formato TSV de Tiresias (el mismo que lee [leer]). */
	fun escribir(): String = buildString {
		appendLine("# patrón\treemplazo\ttipo (texto, palabra, expresion)\tmayúsculas (si o no)\tcomentario")
		for (e in entradas) {
			append(escapar(e.patron)).append('\t').append(escapar(e.reemplazo)).append('\t')
			append(e.tipo.name.lowercase()).append('\t').append(if (e.distinguirMayusculas) "si" else "no").append('\t')
			appendLine(escapar(e.comentario))
		}
	}

	companion object {
		val VACIO = Diccionario(emptyList())

		/**
		 * Lee un diccionario en formato TSV de Tiresias. También entiende el
		 * formato `.dic` de NVDA (patrón, reemplazo, distinguir mayúsculas 0/1,
		 * tipo 0 cualquier sitio, 1 palabra completa, 2 expresión regular).
		 */
		fun leer(texto: String): Diccionario {
			val lista = mutableListOf<EntradaDiccionario>()
			for (linea in texto.lineSequence()) {
				if (linea.isBlank() || linea.startsWith("#")) continue
				val c = linea.split('\t')
				if (c.size < 2 || c[0].isEmpty()) continue
				val tercero = c.getOrNull(2)?.trim().orEmpty()
				val entrada = if (tercero == "0" || tercero == "1") {
					// NVDA: patrón, reemplazo, mayúsculas (0/1), tipo (0, 1, 2), comentario al final si lo hay
					val tipo = when (c.getOrNull(3)?.trim()) {
						"1" -> TipoEntrada.PALABRA
						"2" -> TipoEntrada.EXPRESION
						else -> TipoEntrada.TEXTO
					}
					EntradaDiccionario(desescapar(c[0]), desescapar(c[1]), tipo, tercero == "1", c.getOrNull(4).orEmpty())
				} else {
					val tipo = when (tercero.lowercase()) {
						"texto" -> TipoEntrada.TEXTO
						"expresion", "expresión" -> TipoEntrada.EXPRESION
						else -> TipoEntrada.PALABRA
					}
					EntradaDiccionario(desescapar(c[0]), desescapar(c[1]), tipo, c.getOrNull(3)?.trim()?.lowercase() == "si", desescapar(c.getOrNull(4).orEmpty()))
				}
				lista += entrada
			}
			return Diccionario(lista)
		}

		private fun escapar(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
		private fun desescapar(s: String): String {
			val sb = StringBuilder()
			var i = 0
			while (i < s.length) {
				val c = s[i]
				if (c == '\\' && i + 1 < s.length) {
					when (s[i + 1]) {
						't' -> sb.append('\t')
						'n' -> sb.append('\n')
						'\\' -> sb.append('\\')
						else -> sb.append(c).append(s[i + 1])
					}
					i += 2
				} else {
					sb.append(c)
					i++
				}
			}
			return sb.toString()
		}
	}
}
