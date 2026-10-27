package com.xebolax.tiresias.nucleo

/**
 * Los identificadores de vista dichos en el idioma de Tiresias.
 *
 * Cuando un control no tiene nombre, Tiresias dice el identificador que le dio
 * el programador (`com.whatsapp:id/contact_photo`), y esos están casi siempre
 * en inglés: sin esto, con Tiresias en español se oiría «contact photo». Aquí
 * se quitan las palabras que no dicen nada («view», «btn», «iv») y se traducen
 * las demás con la tabla del idioma, en el orden de ese idioma («foto de
 * contacto»). Las palabras que no están en la tabla (nombres de marca, palabras
 * que el programador escribió ya en español) se dicen tal cual.
 *
 * La tabla es `assets/idiomas/<código>/identificadores.tsv`:
 *
 * - `palabra<TAB>traducción<TAB>tipo`: el tipo es `n` (nombre), `a` (adjetivo o
 *   adverbio) o `v` (verbo, o una palabra que no cambia de sitio).
 * - Varias palabras en la primera columna son una frase, que manda sobre las
 *   palabras sueltas («video call» → «videollamada»).
 * - `palabra<TAB>-`: palabra de relleno, que se quita.
 * - La línea `@invertir` pide el orden del español: los nombres y adjetivos
 *   seguidos van al revés, y entre dos nombres se pone «de» («contact photo»
 *   → «foto de contacto», «dark mode» → «modo oscuro»).
 */
class Identificadores private constructor(
	private val palabras: Map<List<String>, Traduccion>,
	private val relleno: Set<String>,
	private val invertir: Boolean,
	private val union: String,
) {
	private class Traduccion(val texto: String, val tipo: Char)

	private val maximo = palabras.keys.maxOfOrNull { it.size } ?: 1

	/** [id] legible y traducido, o null si no dice nada (solo relleno o un número). */
	fun legible(id: String): String? {
		val todas = Compositor.legible(id)?.split(' ')?.filter { it.isNotBlank() } ?: return null
		// Cada trozo: su texto y su tipo (las palabras sin traducir son «v»: no se mueven).
		// Las frases se miran antes de quitar el relleno («search view» es «buscador»)
		val trozos = ArrayList<Traduccion>()
		var i = 0
		while (i < todas.size) {
			var hecha: Traduccion? = null
			var largo = minOf(maximo, todas.size - i)
			while (largo >= 1 && hecha == null) {
				hecha = palabras[todas.subList(i, i + largo)]
				if (hecha == null) largo--
			}
			if (hecha != null) {
				trozos += hecha
				i += largo
			} else {
				if (todas[i] !in relleno) trozos += Traduccion(todas[i], 'v')
				i++
			}
		}
		if (trozos.isEmpty()) return null
		if (!invertir) return trozos.joinToString(" ") { it.texto }
		val sb = StringBuilder()
		var j = 0
		while (j < trozos.size) {
			if (trozos[j].tipo != 'n' && trozos[j].tipo != 'a') {
				anadir(sb, trozos[j].texto, " ")
				j++
				continue
			}
			// Una serie de nombres y adjetivos: al revés, con «de» entre dos nombres
			var fin = j
			while (fin < trozos.size && (trozos[fin].tipo == 'n' || trozos[fin].tipo == 'a')) fin++
			val serie = trozos.subList(j, fin).reversed()
			for ((k, t) in serie.withIndex()) {
				val separador = if (k > 0 && serie[k - 1].tipo == 'n' && t.tipo == 'n') " $union " else " "
				anadir(sb, t.texto, if (k == 0) " " else separador)
			}
			j = fin
		}
		return sb.toString().trim().takeIf { it.isNotEmpty() }
	}

	private fun anadir(sb: StringBuilder, texto: String, separador: String) {
		if (sb.isNotEmpty()) sb.append(separador)
		sb.append(texto)
	}

	companion object {
		/** Sin tabla: el identificador tal cual, sin nada que quitar (como antes). */
		val VACIO = Identificadores(emptyMap(), emptySet(), false, "")

		/** Lee la tabla (ver el formato arriba). La línea `@union <palabra>` cambia el «de». */
		fun leer(tsv: String): Identificadores {
			val palabras = HashMap<List<String>, Traduccion>()
			val relleno = HashSet<String>()
			var invertir = false
			var union = "de"
			for (linea in tsv.lineSequence()) {
				val l = linea.trim()
				if (l.isEmpty() || l.startsWith("#")) continue
				if (l == "@invertir") {
					invertir = true
					continue
				}
				if (l.startsWith("@union ")) {
					union = l.removePrefix("@union ").trim()
					continue
				}
				val columnas = linea.split('\t')
				val clave = columnas[0].trim().lowercase().split(' ').filter { it.isNotBlank() }
				if (clave.isEmpty()) continue
				val traduccion = columnas.getOrNull(1)?.trim().orEmpty()
				if (traduccion == "-" || traduccion.isEmpty()) {
					if (clave.size == 1) relleno += clave[0]
					continue
				}
				val tipo = columnas.getOrNull(2)?.trim()?.firstOrNull()?.takeIf { it in "nav" } ?: 'v'
				palabras[clave] = Traduccion(traduccion, tipo)
			}
			return Identificadores(palabras, relleno, invertir, union)
		}
	}
}
