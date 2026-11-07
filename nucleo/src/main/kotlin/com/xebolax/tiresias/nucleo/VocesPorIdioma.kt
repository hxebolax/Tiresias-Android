package com.xebolax.tiresias.nucleo

/**
 * La voz elegida de un motor del sistema para cada idioma, como en @Voice: con
 * Google se puede elegir una voz de España para el español y otra británica
 * para el inglés, y Tiresias usa la de cada idioma al cambiar de idioma.
 *
 * Se guarda en una sola cadena, `es=es-es-x-eea-local|en=en-gb-x-rjs-local`
 * (idioma de dos o tres letras, en minúsculas). Lo guardado por versiones
 * anteriores es solo el nombre de una voz, sin idioma: vale para el idioma de
 * esa voz ([SIN_IDIOMA]).
 */
object VocesPorIdioma {
	/** La clave de lo guardado antes, que no decía el idioma: vale para el idioma de la propia voz. */
	const val SIN_IDIOMA = "*"

	fun leer(guardado: String): Map<String, String> {
		if (guardado.isBlank()) return emptyMap()
		if ('=' !in guardado) return mapOf(SIN_IDIOMA to guardado.trim())
		val mapa = LinkedHashMap<String, String>()
		for (parte in guardado.split('|')) {
			val i = parte.indexOf('=')
			if (i <= 0) continue
			val idioma = parte.substring(0, i).trim().lowercase()
			val voz = parte.substring(i + 1).trim()
			if (idioma.isNotEmpty() && voz.isNotEmpty()) mapa[idioma] = voz
		}
		return mapa
	}

	fun escribir(mapa: Map<String, String>): String =
		mapa.filter { (idioma, voz) -> idioma.isNotBlank() && voz.isNotBlank() && '|' !in voz && '=' !in voz }
			.entries.joinToString("|") { (idioma, voz) -> "${idioma.lowercase()}=$voz" }

	/** Pone [voz] para [idioma] (o la quita, con null: se usará la del motor). Lo de otros idiomas no cambia. */
	fun poner(guardado: String, idioma: String, voz: String?): String {
		val mapa = LinkedHashMap(leer(guardado))
		mapa.remove(SIN_IDIOMA)?.let { antigua ->
			// Lo de antes, sin idioma: se queda para el suyo si se sabe por el nombre («es-es-x-...»)
			idiomaDeNombre(antigua)?.let { if (it != idioma.lowercase()) mapa.putIfAbsent(it, antigua) }
		}
		if (voz.isNullOrBlank()) mapa.remove(idioma.lowercase()) else mapa[idioma.lowercase()] = voz
		return escribir(mapa)
	}

	/**
	 * El nombre de la voz para [idioma], o null para la del motor. Lo guardado sin idioma vale si [esDelIdioma]
	 * dice que esa voz es de ese idioma (lo sabe el motor, no el nombre).
	 */
	fun para(guardado: String, idioma: String, esDelIdioma: (String) -> Boolean): String? {
		val mapa = leer(guardado)
		mapa[idioma.lowercase()]?.let { return it }
		return mapa[SIN_IDIOMA]?.takeIf(esDelIdioma)
	}

	/** El idioma por el principio del nombre, si lo lleva («es-es-x-eea-local» → «es»); null si no. */
	fun idiomaDeNombre(nombre: String): String? =
		Regex("^([a-z]{2,3})[-_]").find(nombre.lowercase())?.groupValues?.get(1)
}
