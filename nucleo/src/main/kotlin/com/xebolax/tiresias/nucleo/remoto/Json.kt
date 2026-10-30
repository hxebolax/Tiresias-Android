package com.xebolax.tiresias.nucleo.remoto

/** Un texto que no es JSON válido (o que anida más de lo permitido). */
class JsonInvalido(mensaje: String) : IllegalArgumentException(mensaje)

/**
 * Lector y escritor de JSON mínimo, sin bibliotecas, para los mensajes de la
 * asistencia remota y de NVDA Remote.
 *
 * Al leer, cada valor sale como:
 * - objeto → `Map<String, Any?>` (un `LinkedHashMap`, en el orden del texto; si una clave
 *   se repite, vale la última),
 * - lista → `List<Any?>`,
 * - texto → `String`,
 * - número entero que cabe en un `Long` → `Long`; cualquier otro número → `Double`,
 * - `true`/`false` → `Boolean`, `null` → `null`.
 *
 * Se siguen las reglas estrictas de la RFC 8259: nada de comas al final, comillas simples,
 * comentarios ni caracteres de control sin escapar dentro de los textos.
 */
object Json {
	/** Cuánto pueden anidarse listas y objetos: para que un mensaje hostil no agote la pila. */
	const val PROFUNDIDAD_MAXIMA = 64

	/**
	 * Lee un JSON: `Map<String, Any?>` (LinkedHashMap), `List<Any?>`, `String`, `Long`
	 * (enteros), `Double`, `Boolean` o null.
	 *
	 * @throws JsonInvalido si no es válido o anida más de [PROFUNDIDAD_MAXIMA] niveles.
	 */
	fun leer(texto: String): Any? {
		val lector = Lector(texto)
		lector.saltarEspacios()
		val valor = lector.valor(0)
		lector.saltarEspacios()
		if (lector.pos != texto.length) throw lector.error("sobra texto después del valor")
		return valor
	}

	/** Lee [texto] y devuelve el objeto, o null si no es válido o no es un objeto. No lanza. */
	fun objeto(texto: String): Map<String, Any?>? {
		val valor = try {
			leer(texto)
		} catch (e: JsonInvalido) {
			return null
		}
		return comoMapa(valor)
	}

	/**
	 * Escribe [valor] en JSON compacto (sin espacios). Admite `Map<*, *>` (las claves se
	 * escriben con `toString`), `List`/`Iterable`, `Array`, `String`, `Char`, `Number`,
	 * `Boolean`, `Enum` (su `name`) y null.
	 *
	 * @throws IllegalArgumentException con cualquier otro tipo (por ejemplo `ByteArray`) o con
	 *   un número no finito (NaN o infinito), que JSON no admite.
	 */
	fun escribir(valor: Any?): String = StringBuilder().also { escribir(valor, it, 0) }.toString()

	private fun escribir(valor: Any?, sb: StringBuilder, profundidad: Int) {
		require(profundidad <= PROFUNDIDAD_MAXIMA) { "Demasiados niveles para escribir en JSON" }
		when (valor) {
			null -> sb.append("null")
			is String -> escribirTexto(valor, sb)
			is Char -> escribirTexto(valor.toString(), sb)
			is Boolean -> sb.append(if (valor) "true" else "false")
			is Double -> {
				require(valor.isFinite()) { "JSON no admite el número $valor" }
				sb.append(valor.toString())
			}
			is Float -> {
				require(valor.isFinite()) { "JSON no admite el número $valor" }
				sb.append(valor.toString())
			}
			is Number -> sb.append(valor.toString())
			is Enum<*> -> escribirTexto(valor.name, sb)
			is Map<*, *> -> {
				sb.append('{')
				var primero = true
				for ((clave, v) in valor) {
					if (!primero) sb.append(',')
					primero = false
					escribirTexto(clave.toString(), sb)
					sb.append(':')
					escribir(v, sb, profundidad + 1)
				}
				sb.append('}')
			}
			is Array<*> -> escribirLista(valor.asIterable(), sb, profundidad)
			is Iterable<*> -> escribirLista(valor, sb, profundidad)
			else -> throw IllegalArgumentException("JSON no admite el tipo ${valor::class.java.name}")
		}
	}

	private fun escribirLista(valores: Iterable<*>, sb: StringBuilder, profundidad: Int) {
		sb.append('[')
		var primero = true
		for (v in valores) {
			if (!primero) sb.append(',')
			primero = false
			escribir(v, sb, profundidad + 1)
		}
		sb.append(']')
	}

	private const val HEX = "0123456789abcdef"

	private fun escribirTexto(s: String, sb: StringBuilder) {
		sb.append('"')
		for (c in s) {
			when (c) {
				'"' -> sb.append("\\\"")
				'\\' -> sb.append("\\\\")
				'\n' -> sb.append("\\n")
				'\r' -> sb.append("\\r")
				'\t' -> sb.append("\\t")
				'\b' -> sb.append("\\b")
				'\u000C' -> sb.append("\\f")
				else -> if (c < ' ' || c == ' ' || c == ' ') {
					val n = c.code
					sb.append("\\u")
					sb.append(HEX[(n shr 12) and 0xF]).append(HEX[(n shr 8) and 0xF])
					sb.append(HEX[(n shr 4) and 0xF]).append(HEX[n and 0xF])
				} else {
					sb.append(c)
				}
			}
		}
		sb.append('"')
	}

	/** [valor] como mapa de claves de texto, o null si no lo es. */
	internal fun comoMapa(valor: Any?): Map<String, Any?>? {
		if (valor !is Map<*, *>) return null
		if (valor.keys.any { it !is String }) return null
		@Suppress("UNCHECKED_CAST")
		return valor as Map<String, Any?>
	}

	/** Recorre el texto una vez, de izquierda a derecha. */
	private class Lector(val s: String) {
		var pos = 0

		fun error(motivo: String) = JsonInvalido("JSON no válido en la posición $pos: $motivo")

		fun saltarEspacios() {
			while (pos < s.length) {
				val c = s[pos]
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++ else return
			}
		}

		fun valor(profundidad: Int): Any? {
			if (pos >= s.length) throw error("falta un valor")
			return when (val c = s[pos]) {
				'{' -> objeto(profundidad + 1)
				'[' -> lista(profundidad + 1)
				'"' -> texto()
				't' -> palabra("true", true)
				'f' -> palabra("false", false)
				'n' -> palabra("null", null)
				else -> if (c == '-' || c in '0'..'9') numero() else throw error("carácter inesperado «$c»")
			}
		}

		private fun palabra(p: String, valor: Any?): Any? {
			if (!s.startsWith(p, pos)) throw error("se esperaba $p")
			pos += p.length
			return valor
		}

		private fun objeto(profundidad: Int): Map<String, Any?> {
			if (profundidad > PROFUNDIDAD_MAXIMA) throw error("demasiados niveles")
			pos++
			val mapa = LinkedHashMap<String, Any?>()
			saltarEspacios()
			if (pos < s.length && s[pos] == '}') {
				pos++
				return mapa
			}
			while (true) {
				saltarEspacios()
				if (pos >= s.length || s[pos] != '"') throw error("se esperaba una clave entre comillas")
				val clave = texto()
				saltarEspacios()
				if (pos >= s.length || s[pos] != ':') throw error("se esperaban dos puntos")
				pos++
				saltarEspacios()
				mapa[clave] = valor(profundidad)
				saltarEspacios()
				if (pos >= s.length) throw error("objeto sin cerrar")
				when (s[pos]) {
					',' -> pos++
					'}' -> {
						pos++
						return mapa
					}
					else -> throw error("se esperaba una coma o una llave de cierre")
				}
			}
		}

		private fun lista(profundidad: Int): List<Any?> {
			if (profundidad > PROFUNDIDAD_MAXIMA) throw error("demasiados niveles")
			pos++
			val lista = ArrayList<Any?>()
			saltarEspacios()
			if (pos < s.length && s[pos] == ']') {
				pos++
				return lista
			}
			while (true) {
				saltarEspacios()
				lista += valor(profundidad)
				saltarEspacios()
				if (pos >= s.length) throw error("lista sin cerrar")
				when (s[pos]) {
					',' -> pos++
					']' -> {
						pos++
						return lista
					}
					else -> throw error("se esperaba una coma o un corchete de cierre")
				}
			}
		}

		private fun texto(): String {
			pos++
			val sb = StringBuilder()
			while (true) {
				if (pos >= s.length) throw error("texto sin cerrar")
				val c = s[pos++]
				when {
					c == '"' -> return sb.toString()
					c == '\\' -> {
						if (pos >= s.length) throw error("escape sin terminar")
						when (val e = s[pos++]) {
							'"' -> sb.append('"')
							'\\' -> sb.append('\\')
							'/' -> sb.append('/')
							'b' -> sb.append('\b')
							'f' -> sb.append('\u000C')
							'n' -> sb.append('\n')
							'r' -> sb.append('\r')
							't' -> sb.append('\t')
							// Los pares sustitutos (😀) salen solos: cada mitad es un Char
							'u' -> sb.append(hex4())
							else -> throw error("escape desconocido «\\$e»")
						}
					}
					c < ' ' -> throw error("carácter de control sin escapar")
					else -> sb.append(c)
				}
			}
		}

		private fun hex4(): Char {
			if (pos + 4 > s.length) throw error("escape \\u incompleto")
			var n = 0
			repeat(4) {
				val d = Character.digit(s[pos++], 16)
				if (d < 0) throw error("escape \\u con un dígito que no es hexadecimal")
				n = (n shl 4) or d
			}
			return n.toChar()
		}

		private fun numero(): Any {
			val inicio = pos
			var entero = true
			if (s[pos] == '-') pos++
			if (pos >= s.length) throw error("número incompleto")
			if (s[pos] == '0') {
				pos++
			} else if (s[pos] in '1'..'9') {
				while (pos < s.length && s[pos] in '0'..'9') pos++
			} else {
				throw error("número mal formado")
			}
			if (pos < s.length && s[pos] == '.') {
				entero = false
				pos++
				if (pos >= s.length || s[pos] !in '0'..'9') throw error("faltan decimales")
				while (pos < s.length && s[pos] in '0'..'9') pos++
			}
			if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
				entero = false
				pos++
				if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
				if (pos >= s.length || s[pos] !in '0'..'9') throw error("exponente sin cifras")
				while (pos < s.length && s[pos] in '0'..'9') pos++
			}
			val trozo = s.substring(inicio, pos)
			if (entero) trozo.toLongOrNull()?.let { return it }
			return trozo.toDouble()
		}
	}
}

/** El texto de [clave], o null si falta o no es un texto. */
fun Map<String, Any?>.texto(clave: String): String? = this[clave] as? String

/** El entero de [clave] (acepta `Long`, `Int` y `Double` sin decimales), o null. */
fun Map<String, Any?>.entero(clave: String): Long? = when (val v = this[clave]) {
	is Long -> v
	is Int -> v.toLong()
	is Short -> v.toLong()
	is Byte -> v.toLong()
	is Double -> enteroExacto(v)
	is Float -> enteroExacto(v.toDouble())
	else -> null
}

/** [v] como `Long` si no tiene decimales y cabe; si no, null. */
private fun enteroExacto(v: Double): Long? =
	if (v.isFinite() && v == Math.floor(v) && v >= -9.223372036854776E18 && v < 9.223372036854776E18) v.toLong() else null

/** El número de [clave] como `Double`, o null si falta o no es un número. */
fun Map<String, Any?>.decimal(clave: String): Double? = (this[clave] as? Number)?.toDouble()

/** El valor lógico de [clave], o null si falta o no es `true`/`false`. */
fun Map<String, Any?>.logico(clave: String): Boolean? = this[clave] as? Boolean

/** La lista de [clave], o null si falta o no es una lista. */
fun Map<String, Any?>.lista(clave: String): List<Any?>? = this[clave] as? List<Any?>

/** El objeto de [clave], o null si falta o no es un objeto con claves de texto. */
fun Map<String, Any?>.mapa(clave: String): Map<String, Any?>? = Json.comoMapa(this[clave])
