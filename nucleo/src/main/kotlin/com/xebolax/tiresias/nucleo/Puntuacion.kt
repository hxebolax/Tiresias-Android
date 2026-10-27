package com.xebolax.tiresias.nucleo

/**
 * Niveles de puntuación, como en NVDA y en el Tiresias de Windows. Cada símbolo
 * tiene el nivel a partir del cual se dice: con [ALGUNAS] se dicen los de nivel
 * [ALGUNAS]; con [TODAS], todos. [CARACTER] solo se dice al leer carácter a
 * carácter.
 *
 * Es independiente del motor de voz (una de las críticas a TalkBack es que solo
 * funciona con la voz de Google).
 */
enum class NivelPuntuacion { NINGUNA, ALGUNAS, MAYORIA, TODAS, CARACTER }

/** Un símbolo y cómo se dice. [mantener]: si además del nombre se deja el signo (para la entonación). */
data class Simbolo(val simbolo: String, val nombre: String, val nivel: NivelPuntuacion, val mantener: Boolean = false)

/**
 * La tabla de símbolos de un idioma. Se carga de `assets/idiomas/<código>/simbolos.tsv`
 * (y se puede ampliar con la del usuario):
 *
 * ```
 * # símbolo<TAB>nombre<TAB>nivel[<TAB>mantener]
 * ¿	abre interrogación	mayoria
 * ?	cierra interrogación	mayoria	mantener
 * ```
 *
 * Los niveles se escriben `ninguna`, `algunas`, `mayoria`, `todas` o `caracter`.
 * `\t` se escribe `\\t` y el espacio `\\s` en la primera columna.
 */
class TablaSimbolos(simbolos: Collection<Simbolo>) {
	private val porSimbolo: Map<String, Simbolo> = simbolos.associateBy { it.simbolo }
	/** Los de más de un carácter primero, para que «...» gane a «.». */
	private val ordenados = simbolos.sortedByDescending { it.simbolo.length }
	private val maximo = simbolos.maxOfOrNull { it.simbolo.length } ?: 1

	operator fun get(simbolo: String): Simbolo? = porSimbolo[simbolo]

	val todos: Collection<Simbolo> get() = porSimbolo.values

	/** Una tabla con los símbolos de [otra] por encima de los de esta (los del usuario mandan). */
	fun mas(otra: TablaSimbolos): TablaSimbolos = TablaSimbolos((porSimbolo + otra.porSimbolo).values)

	/**
	 * Sustituye en [texto] los símbolos cuyo nivel entra en [nivel] por su nombre
	 * entre espacios. Las letras, los dígitos y los espacios no se tocan.
	 */
	fun aplicar(texto: String, nivel: NivelPuntuacion): String {
		if (nivel == NivelPuntuacion.NINGUNA && ordenados.none { it.nivel == NivelPuntuacion.NINGUNA }) return texto
		val sb = StringBuilder(texto.length + 16)
		var i = 0
		while (i < texto.length) {
			val c = texto[i]
			if (c.isLetterOrDigit() || c == ' ') {
				sb.append(c)
				i++
				continue
			}
			var encontrado: Simbolo? = null
			var largo = minOf(maximo, texto.length - i)
			while (largo >= 1) {
				val s = porSimbolo[texto.substring(i, i + largo)]
				if (s != null) {
					encontrado = s
					break
				}
				largo--
			}
			if (encontrado != null && dice(encontrado.nivel, nivel)) {
				// Puntos decimales y de miles entre cifras: no son puntuación
				if (esSeparadorNumerico(texto, i, encontrado)) {
					sb.append(c)
					i++
					continue
				}
				sb.append(' ').append(encontrado.nombre).append(' ')
				if (encontrado.mantener) sb.append(encontrado.simbolo)
				i += encontrado.simbolo.length
			} else if (encontrado != null) {
				sb.append(texto, i, i + encontrado.simbolo.length)
				i += encontrado.simbolo.length
			} else {
				sb.append(c)
				i++
			}
		}
		return sb.toString()
	}

	/** El nombre de un carácter suelto (lectura por carácter): siempre se dice, sea del nivel que sea. */
	fun nombreCaracter(c: String): String? = porSimbolo[c]?.nombre

	private fun esSeparadorNumerico(texto: String, i: Int, s: Simbolo): Boolean {
		if (s.simbolo != "." && s.simbolo != "," && s.simbolo != ":") return false
		val antes = i > 0 && texto[i - 1].isDigit()
		val despues = i + 1 < texto.length && texto[i + 1].isDigit()
		return antes && despues
	}

	companion object {
		fun dice(simbolo: NivelPuntuacion, actual: NivelPuntuacion): Boolean = when (actual) {
			NivelPuntuacion.CARACTER -> true
			else -> simbolo != NivelPuntuacion.CARACTER && simbolo.ordinal <= actual.ordinal
		}

		/** Lee una tabla en formato TSV (ver la documentación de la clase). Las líneas mal formadas se saltan. */
		fun leer(tsv: String): TablaSimbolos {
			val lista = mutableListOf<Simbolo>()
			for (linea in tsv.lineSequence()) {
				// «#» y un tabulador es el símbolo de la almohadilla, no un comentario
				if (linea.isBlank() || (linea.startsWith("#") && !linea.startsWith("#\t"))) continue
				val campos = linea.split('\t')
				if (campos.size < 3) continue
				val simbolo = campos[0].replace("\\t", "\t").replace("\\s", " ").replace("\\\\", "\\")
				if (simbolo.isEmpty()) continue
				val nivel = when (campos[2].trim().lowercase()) {
					"ninguna" -> NivelPuntuacion.NINGUNA
					"algunas" -> NivelPuntuacion.ALGUNAS
					"mayoria", "mayoría" -> NivelPuntuacion.MAYORIA
					"todas" -> NivelPuntuacion.TODAS
					"caracter", "carácter" -> NivelPuntuacion.CARACTER
					else -> continue
				}
				lista += Simbolo(simbolo, campos[1].trim(), nivel, campos.getOrNull(3)?.trim() == "mantener")
			}
			return TablaSimbolos(lista)
		}
	}
}
