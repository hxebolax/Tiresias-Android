package com.xebolax.tiresias.nucleo.braille

/**
 * Una celda braille es un byte: el bit 0 es el punto 1 y el bit 7 el punto 8
 * (el mismo orden que usan las líneas braille HID y liblouis).
 */
object Puntos {
	const val P1 = 0x01
	const val P2 = 0x02
	const val P3 = 0x04
	const val P4 = 0x08
	const val P5 = 0x10
	const val P6 = 0x20
	const val P7 = 0x40
	const val P8 = 0x80

	/** Los puntos 7 y 8 juntos: marcan el cursor en la línea. */
	const val CURSOR = P7 or P8

	/** «1246» → la celda con los puntos 1, 2, 4 y 6. «0» es la celda vacía. */
	fun de(texto: String): Int = texto.fold(0) { acc, c ->
		val d = c - '0'
		if (d in 1..8) acc or (1 shl (d - 1)) else acc
	}

	fun de(vararg puntos: Int): Int = puntos.fold(0) { acc, d -> acc or (1 shl (d - 1)) }

	/** La celda por sus puntos, seguidos: «124» (para guardarla). */
	fun nombre(celda: Int): String = (0 until 8).filter { celda and (1 shl it) != 0 }.joinToString("") { (it + 1).toString() }

	/** La celda como se dice: «puntos 1 2 4». */
	fun describir(celda: Int): String = (0 until 8).filter { celda and (1 shl it) != 0 }.joinToString(" ") { (it + 1).toString() }
}

/**
 * Un texto pasado a braille. [celdaDe] dice en qué celda empieza cada carácter
 * del texto (tiene un elemento más: la celda tras el final) y [caracterDe] de
 * qué carácter sale cada celda; sirven para poner el cursor y para que las
 * teclas de encaminamiento lleven al carácter que se toca.
 */
class Traduccion(val celdas: ByteArray, val celdaDe: IntArray, val caracterDe: IntArray) {
	val tamano: Int get() = celdas.size
}

/** Una tabla braille: texto a celdas (lo que se ve en la línea) y celdas a texto (lo que se escribe en ella). */
interface TablaBraille {
	val id: String

	fun traducir(texto: String): Traduccion

	/** Para escribir desde el teclado de la línea, con lo que haga falta recordar entre celdas. */
	fun entrada(): EntradaBraille
}

/** Lo que se escribe celda a celda: cada celda devuelve el texto que añade (vacío si solo cambia el modo). */
fun interface EntradaBraille {
	fun introducir(celda: Int): String?
}

/**
 * Braille informático (un carácter, una celda de 8 puntos), leído de una tabla
 * de liblouis (`.utb`/`.uti`/`.ctb`). Solo hace falta lo que define caracteres
 * sueltos: `space`, `punctuation`, `digit`, `letter`, `lowercase`, `uppercase`,
 * `uplow`, `sign`, `math`, `litdigit` y las inclusiones con `include`. Como en
 * liblouis, la primera definición de un carácter es la que vale.
 */
class TablaInformatica(override val id: String, lineas: Sequence<String>, incluir: (String) -> Sequence<String>) : TablaBraille {
	private val aCelda = HashMap<Int, Int>()
	private val aCaracter = HashMap<Int, String>()

	init {
		leer(lineas, incluir, 0)
	}

	private fun leer(lineas: Sequence<String>, incluir: (String) -> Sequence<String>, profundidad: Int) {
		for (bruta in lineas) {
			val partes = bruta.trim().split(Regex("\\s+"))
			if (partes.isEmpty() || partes[0].isEmpty() || partes[0].startsWith("#")) continue
			var i = 0
			// Los prefijos que no cambian lo que define el carácter
			var soloIda = false
			while (i < partes.size && partes[i] in PREFIJOS) {
				if (partes[i] == "noback") soloIda = true
				i++
			}
			val orden = partes.getOrNull(i) ?: continue
			if (orden == "include") {
				if (profundidad < 8) partes.getOrNull(i + 1)?.let { leer(incluir(it), incluir, profundidad + 1) }
				continue
			}
			if (orden !in ORDENES) continue
			val caracteres = desescapar(partes.getOrNull(i + 1) ?: continue)
			val puntos = partes.getOrNull(i + 2) ?: continue
			if (orden == "uplow") {
				// «uplow Aa 17,1»: la mayúscula y la minúscula con sus puntos
				val celdas = puntos.split(',')
				if (caracteres.length >= 2 && celdas.size >= 2) {
					definir(caracteres.codePointAt(0), Puntos.de(celdas[0]), soloIda)
					definir(caracteres.codePointAt(caracteres.offsetByCodePoints(0, 1)), Puntos.de(celdas[1]), soloIda)
				}
				continue
			}
			// Solo los que son un carácter y una celda (lo informático)
			if (caracteres.codePointCount(0, caracteres.length) != 1 || puntos.contains('-')) continue
			definir(caracteres.codePointAt(0), if (puntos == "0") 0 else Puntos.de(puntos), soloIda)
		}
	}

	private fun definir(cp: Int, celda: Int, soloIda: Boolean) {
		if (cp !in aCelda) aCelda[cp] = celda
		if (!soloIda && celda !in aCaracter && cp != '\t'.code && cp != 0x1B && cp != '\n'.code && cp != '\r'.code && cp != 0xA0) {
			aCaracter[celda] = String(Character.toChars(cp))
		}
	}

	fun celda(cp: Int): Int? = aCelda[cp]

	override fun traducir(texto: String): Traduccion {
		val celdas = ArrayList<Byte>(texto.length)
		val celdaDe = IntArray(texto.length + 1)
		val caracterDe = ArrayList<Int>(texto.length)
		var i = 0
		while (i < texto.length) {
			val cp = texto.codePointAt(i)
			val n = Character.charCount(cp)
			for (k in 0 until n) celdaDe[i + k] = celdas.size
			celdas += (aCelda[cp] ?: aCelda[Character.toLowerCase(cp)]?.or(Puntos.P7) ?: DESCONOCIDO).toByte()
			caracterDe += i
			i += n
		}
		celdaDe[texto.length] = celdas.size
		return Traduccion(celdas.toByteArray(), celdaDe, caracterDe.toIntArray())
	}

	override fun entrada() = EntradaBraille { celda -> if (celda == 0) " " else aCaracter[celda] }

	companion object {
		/** Lo que no está en la tabla: los 8 puntos (se nota que ahí hay algo). */
		const val DESCONOCIDO = 0xFF
		private val PREFIJOS = setOf("noback", "nofor", "nocross")
		private val ORDENES = setOf("space", "punctuation", "digit", "letter", "lowercase", "uppercase", "uplow", "sign", "math", "litdigit", "display")

		/** «\x00e1» → «á», «\s» → espacio, «\t» → tabulador. */
		fun desescapar(s: String): String {
			val sb = StringBuilder()
			var i = 0
			while (i < s.length) {
				val c = s[i]
				if (c == '\\' && i + 1 < s.length) {
					when (s[i + 1]) {
						's' -> { sb.append(' '); i += 2; continue }
						't' -> { sb.append('\t'); i += 2; continue }
						'n' -> { sb.append('\n'); i += 2; continue }
						'r' -> { sb.append('\r'); i += 2; continue }
						'e' -> { sb.append('\u001B'); i += 2; continue }
						'\\' -> { sb.append('\\'); i += 2; continue }
						'x' -> {
							var j = i + 2
							while (j < s.length && j < i + 6 && s[j].isLetterOrDigit() && s[j].lowercaseChar() in "0123456789abcdef") j++
							s.substring(i + 2, j).toIntOrNull(16)?.let { sb.appendCodePoint(it) }
							i = j
							continue
						}
					}
				}
				sb.append(c)
				i++
			}
			return sb.toString()
		}
	}
}

/**
 * Braille literario español de 6 puntos (grado 1, sin contracciones, como lo
 * enseña la ONCE): signo de mayúscula (46) delante de cada mayúscula, signo de
 * número (3456) delante de las cifras y punto 5 si tras un número viene una
 * letra de la «a» a la «j», que se confundiría con una cifra. Lo que no tiene
 * signo literario se escribe como en el braille informático ([reserva]).
 */
class TablaLiteraria(private val reserva: TablaInformatica?) : TablaBraille {
	override val id = "es-literario"

	override fun traducir(texto: String): Traduccion {
		val celdas = ArrayList<Byte>(texto.length + 8)
		val celdaDe = IntArray(texto.length + 1)
		val caracterDe = ArrayList<Int>(texto.length + 8)
		var enNumero = false
		var i = 0
		fun poner(celda: Int, desde: Int) {
			celdas += celda.toByte()
			caracterDe += desde
		}
		while (i < texto.length) {
			val c = texto[i]
			celdaDe[i] = celdas.size
			val cifra = CIFRAS.indexOf(c)
			when {
				cifra >= 0 -> {
					if (!enNumero) poner(SIGNO_NUMERO, i)
					enNumero = true
					poner(LETRAS.getValue("abcdefghij"[cifra]), i)
				}
				// La coma y el punto dentro de un número no lo cortan: 3,5 o 1.000
				enNumero && (c == ',' || c == '.') && texto.getOrNull(i + 1)?.isDigit() == true -> poner(LETRAS.getValue(c), i)
				else -> {
					val minuscula = c.lowercaseChar()
					val letra = LETRAS[minuscula]
					if (letra != null && c.isLetter()) {
						if (enNumero && minuscula in "abcdefghij") poner(Puntos.P5, i)
						if (c.isUpperCase()) poner(SIGNO_MAYUSCULA, i)
						poner(letra, i)
					} else if (letra != null) {
						poner(letra, i)
					} else {
						poner(reserva?.celda(c.code) ?: TablaInformatica.DESCONOCIDO, i)
					}
					enNumero = false
				}
			}
			i++
		}
		celdaDe[texto.length] = celdas.size
		return Traduccion(celdas.toByteArray(), celdaDe, caracterDe.toIntArray())
	}

	override fun entrada(): EntradaBraille {
		var numero = false
		var mayuscula = false
		return EntradaBraille { celda ->
			val seis = celda and 0x3F
			when {
				seis == 0 -> { numero = false; " " }
				seis == SIGNO_NUMERO -> { numero = true; "" }
				seis == SIGNO_MAYUSCULA -> { mayuscula = true; "" }
				numero && seis in NUMEROS -> NUMEROS[seis]
				else -> {
					numero = false
					val t = INVERSA[seis] ?: return@EntradaBraille null
					if (mayuscula) { mayuscula = false; t.uppercase() } else t
				}
			}
		}
	}

	companion object {
		const val SIGNO_NUMERO = 0x3C // 3456
		const val SIGNO_MAYUSCULA = 0x28 // 46
		private const val CIFRAS = "1234567890"
		private fun p(vararg n: Int) = Puntos.de(*n)

		/** Las letras y los signos de puntuación del braille español (ONCE, Comisión Braille Española). */
		val LETRAS: Map<Char, Int> = mapOf(
			'a' to p(1), 'b' to p(1, 2), 'c' to p(1, 4), 'd' to p(1, 4, 5), 'e' to p(1, 5), 'f' to p(1, 2, 4),
			'g' to p(1, 2, 4, 5), 'h' to p(1, 2, 5), 'i' to p(2, 4), 'j' to p(2, 4, 5), 'k' to p(1, 3), 'l' to p(1, 2, 3),
			'm' to p(1, 3, 4), 'n' to p(1, 3, 4, 5), 'ñ' to p(1, 2, 4, 5, 6), 'o' to p(1, 3, 5), 'p' to p(1, 2, 3, 4),
			'q' to p(1, 2, 3, 4, 5), 'r' to p(1, 2, 3, 5), 's' to p(2, 3, 4), 't' to p(2, 3, 4, 5), 'u' to p(1, 3, 6),
			'v' to p(1, 2, 3, 6), 'w' to p(2, 4, 5, 6), 'x' to p(1, 3, 4, 6), 'y' to p(1, 3, 4, 5, 6), 'z' to p(1, 3, 5, 6),
			'á' to p(1, 2, 3, 5, 6), 'é' to p(2, 3, 4, 6), 'í' to p(3, 4), 'ó' to p(3, 4, 6), 'ú' to p(2, 3, 4, 5, 6), 'ü' to p(1, 2, 5, 6),
			'.' to p(3), ',' to p(2), ';' to p(2, 3), ':' to p(2, 5), '¿' to p(2, 6), '?' to p(2, 6), '¡' to p(2, 3, 5), '!' to p(2, 3, 5),
			'-' to p(3, 6), '(' to p(1, 2, 6), ')' to p(3, 4, 5), '"' to p(2, 3, 6), '\'' to p(5), '=' to p(2, 3, 5, 6),
			'*' to p(3, 5), '/' to p(2, 5, 6), '_' to p(4, 5, 6), '+' to p(2, 3, 5), ' ' to 0, '\n' to 0,
		)

		private val NUMEROS: Map<Int, String> = (0 until 10).associate { LETRAS.getValue("abcdefghij"[it]) to CIFRAS[(it + 10) % 10].toString() } +
			mapOf(p(3) to ".", p(2) to ",")

		private val INVERSA: Map<Int, String> = LETRAS.entries.filter { it.key != '+' && it.key != '¿' && it.key != '¡' && it.key != '\n' }
			.associate { it.value to it.key.toString() }
	}
}
