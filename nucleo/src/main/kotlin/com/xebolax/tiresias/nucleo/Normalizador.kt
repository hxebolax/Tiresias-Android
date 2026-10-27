package com.xebolax.tiresias.nucleo

/** Cómo se leen los números. */
enum class ModoNumeros { CIFRAS, DIGITOS }

/** Cómo se avisa de una mayúscula al leer por caracteres. */
enum class ModoMayusculas { NADA, PALABRA, TONO, SONIDO, PALABRA_Y_TONO }

/** Qué se hace con los emojis (Backtalk 72). */
enum class ModoEmojis {
	/** Por su nombre, con la tabla de Tiresias («cara sonriente»). */
	NOMBRE,
	/** Se dejan al motor de voz, que los dice a su manera (o no los dice). */
	MOTOR,
	/** Se quitan. */
	QUITAR,
}

/** Ajustes de lectura del texto, por perfil. */
data class AjustesTexto(
	val puntuacion: NivelPuntuacion = NivelPuntuacion.ALGUNAS,
	val numeros: ModoNumeros = ModoNumeros.CIFRAS,
	val mayusculas: ModoMayusculas = ModoMayusculas.TONO,
	/** A partir de cuántos símbolos iguales seguidos se dice «igual repetido 12 veces». 0: nunca. */
	val repetidos: Int = 4,
	val emojis: ModoEmojis = ModoEmojis.NOMBRE,
)

/** Un carácter dicho suelto: el texto y si es mayúscula (para subir el tono o sonar). */
data class CaracterHablado(val texto: String, val mayuscula: Boolean)

/**
 * Prepara el texto que va a la voz: diccionarios, símbolos repetidos,
 * puntuación por niveles, emojis y números. No toca el sentido del texto:
 * solo cómo suena.
 */
class Normalizador(
	private val simbolos: TablaSimbolos,
	private val textos: Plantillas,
	/** Nombre de cada letra que necesita ayuda («y» → «i griega»), del idioma. */
	private val letras: Map<String, String> = emptyMap(),
	/** Palabra para la mayúscula («mayúscula %1$s»). */
	private val plantillaMayuscula: String = "mayúscula %1\$s",
	private val nombreEspacio: String = "espacio",
) {

	fun normalizar(texto: String, a: AjustesTexto, diccionario: Diccionario = Diccionario.VACIO): String {
		if (texto.isEmpty()) return texto
		var r = diccionario.aplicar(texto)
		if (a.repetidos > 0) r = repetidos(r, a.repetidos)
		if (a.emojis == ModoEmojis.QUITAR) r = quitarEmojis(r)
		// Con los emojis para el motor, la tabla de símbolos no los toca (lo demás, sí)
		r = if (a.emojis == ModoEmojis.MOTOR) fueraDeEmojis(r) { simbolos.aplicar(it, a.puntuacion) } else simbolos.aplicar(r, a.puntuacion)
		if (a.numeros == ModoNumeros.DIGITOS) r = digitos(r)
		// Sin dobles espacios ni espacio delante de un signo (lo dejan los nombres de símbolo: «40 por ciento ,»)
		return r.replace(Regex("[ \\t]{2,}"), " ").replace(Regex(" +([,.;:!?])"), "$1").trim()
	}

	/** Cómo se dice un carácter suelto (navegación por carácter, eco de escritura). */
	fun caracter(c: String, a: AjustesTexto): CaracterHablado {
		if (c.isEmpty()) return CaracterHablado("", false)
		if (c == " ") return CaracterHablado(nombreEspacio, false)
		simbolos.nombreCaracter(c)?.let { return CaracterHablado(it, false) }
		val cp = c.codePointAt(0)
		val esMayuscula = Character.isUpperCase(cp)
		val base = letras[c.lowercase()] ?: c.lowercase()
		val texto = if (esMayuscula && (a.mayusculas == ModoMayusculas.PALABRA || a.mayusculas == ModoMayusculas.PALABRA_Y_TONO)) plantillaMayuscula.format(base) else base
		return CaracterHablado(texto, esMayuscula)
	}

	/** «=====» → «igual repetido 5 veces», con el nombre del símbolo de la tabla. */
	private fun repetidos(texto: String, minimo: Int): String {
		val regex = Regex("([^\\p{L}\\p{N}\\s])\\1{${minimo - 1},}")
		return regex.replace(texto) { m ->
			val s = m.groupValues[1]
			val nombre = simbolos[s]?.nombre ?: s
			" " + textos.formatoPlural(FrasePlural.REPETIDO, m.value.length, nombre, m.value.length) + " "
		}
	}

	private fun digitos(texto: String): String = Regex("\\d{2,}").replace(texto) { m -> m.value.toCharArray().joinToString(" ") }

	/** Aplica [cambio] a los trozos de [texto] que no son emojis; los emojis quedan como estaban. */
	private fun fueraDeEmojis(texto: String, cambio: (String) -> String): String {
		val sb = StringBuilder(texto.length + 16)
		val trozo = StringBuilder()
		var enEmoji = false
		var i = 0
		while (i < texto.length) {
			val cp = texto.codePointAt(i)
			val emoji = esEmoji(cp)
			if (emoji != enEmoji && trozo.isNotEmpty()) {
				sb.append(if (enEmoji) trozo.toString() else cambio(trozo.toString()))
				trozo.setLength(0)
			}
			enEmoji = emoji
			trozo.appendCodePoint(cp)
			i += Character.charCount(cp)
		}
		if (trozo.isNotEmpty()) sb.append(if (enEmoji) trozo.toString() else cambio(trozo.toString()))
		return sb.toString()
	}

	private fun quitarEmojis(texto: String): String {
		val sb = StringBuilder(texto.length)
		var i = 0
		while (i < texto.length) {
			val cp = texto.codePointAt(i)
			if (!esEmoji(cp)) sb.appendCodePoint(cp)
			i += Character.charCount(cp)
		}
		return sb.toString()
	}

	companion object {
		fun esEmoji(cp: Int): Boolean = cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x1F000..0x1F2FF ||
			cp == 0xFE0F || cp == 0x200D || cp in 0x1F1E6..0x1F1FF
	}
}

/** Alfabeto fonético (deletreo «a de Antonio»): el de cada idioma viene de sus recursos. */
class Fonetico(private val palabras: Map<Char, String>) {
	fun de(c: Char): String? = palabras[c.lowercaseChar()]

	companion object {
		/** El alfabeto radiofónico internacional (OTAN), que entienden en todos los idiomas. */
		val OTAN = Fonetico(
			mapOf(
				'a' to "Alfa", 'b' to "Bravo", 'c' to "Charlie", 'd' to "Delta", 'e' to "Eco", 'f' to "Foxtrot", 'g' to "Golf",
				'h' to "Hotel", 'i' to "India", 'j' to "Juliett", 'k' to "Kilo", 'l' to "Lima", 'm' to "Mike", 'n' to "November",
				'ñ' to "Eñe", 'o' to "Oscar", 'p' to "Papa", 'q' to "Quebec", 'r' to "Romeo", 's' to "Sierra", 't' to "Tango",
				'u' to "Uniform", 'v' to "Victor", 'w' to "Whiskey", 'x' to "X-ray", 'y' to "Yankee", 'z' to "Zulu",
			)
		)

		/** Lee «letra<TAB>palabra» por línea. */
		fun leer(tsv: String): Fonetico = Fonetico(
			tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
				val c = l.split('\t')
				if (c.size >= 2 && c[0].isNotEmpty()) c[0][0].lowercaseChar() to c[1].trim() else null
			}.toMap()
		)
	}
}
