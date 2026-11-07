package com.xebolax.tiresias.nucleo.braille

import java.text.Normalizer
import java.util.Locale

/**
 * El buscador de emojis del teclado braille: se escriben palabras del nombre
 * del emoji (sin tildes ni mayúsculas, que no hacen falta) y salen los que las
 * tienen. Los nombres son los de la base de Tiresias (`emojis.tsv` de cada
 * idioma, del CLDR de Unicode), como en el buscador de emojis del teclado braille
 * de SandFly.
 *
 * Cada palabra buscada tiene que ser el principio de alguna palabra del nombre
 * («cor roj» encuentra «corazón rojo»). Primero los nombres que empiezan por lo
 * buscado, luego los más cortos (los más generales) y, a igualdad, el orden de
 * la base.
 */
class BuscadorEmojis(entradas: List<Pair<String, String>>) {
	/** Un emoji y su nombre. */
	data class Emoji(val emoji: String, val nombre: String)

	private class Indexado(val emoji: Emoji, val palabras: List<String>, val orden: Int)

	private val todos: List<Indexado> = entradas
		.filter { (e, n) -> e.isNotBlank() && n.isNotBlank() && esEmoji(e) }
		.distinctBy { it.first }
		.mapIndexed { i, (e, n) -> Indexado(Emoji(e, n), palabras(n), i) }

	val cuantos: Int get() = todos.size

	/** Los emojis que tienen todas las palabras de [consulta] (como mucho [maximo]). Vacía: ninguno. */
	fun buscar(consulta: String, maximo: Int = MAXIMO): List<Emoji> {
		val buscadas = palabras(consulta)
		if (buscadas.isEmpty()) return emptyList()
		return todos.asSequence()
			.filter { i -> buscadas.all { b -> i.palabras.any { it.startsWith(b) } } }
			.sortedWith(compareBy<Indexado>({ if (it.palabras.firstOrNull()?.startsWith(buscadas[0]) == true) 0 else 1 }, { it.palabras.size }, { it.orden }))
			.take(maximo)
			.map { it.emoji }
			.toList()
	}

	companion object {
		/** Todos: la base entera son unos pocos miles y se recorre en nada. */
		const val MAXIMO = Int.MAX_VALUE

		/** Las palabras de un texto sin tildes ni mayúsculas («Corazón rojo» → corazon, rojo); la ñ se queda. */
		fun palabras(texto: String): List<String> = normalizar(texto).split(' ').filter { it.isNotEmpty() }

		fun normalizar(texto: String): String {
			val conEnes = texto.lowercase(Locale.ROOT).replace('ñ', '\u0001')
			val sinTildes = Normalizer.normalize(conEnes, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
			return sinTildes.replace('\u0001', 'ñ').replace(Regex("[^\\p{L}\\p{N}ñ]+"), " ").trim()
		}

		/** Si es un emoji de verdad (no una letra ni un signo de la tabla, que también trae alguno). */
		fun esEmoji(texto: String): Boolean {
			val cp = texto.codePointAt(0)
			// Los tonos de piel solos no se escriben: van pegados a otro emoji
			if (cp in 0x1F3FB..0x1F3FF) return false
			return cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2300..0x23FF || cp == 0x00A9 || cp == 0x00AE ||
				cp in 0x2190..0x21FF || cp in 0x3030..0x303D || cp in 0x3297..0x3299 || cp in 0x2122..0x2139
		}

		/** Lee las líneas de un `emojis.tsv` («emoji, tabulador, nombre, tabulador, nivel»). */
		fun leerTabla(texto: String): List<Pair<String, String>> = texto.lineSequence()
			.filter { it.isNotBlank() && !it.startsWith("#") }
			.mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1].trim() } }
			.toList()
	}
}
