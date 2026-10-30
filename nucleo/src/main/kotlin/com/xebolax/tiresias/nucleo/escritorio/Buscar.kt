package com.xebolax.tiresias.nucleo.escritorio

import java.text.Normalizer
import java.util.Locale

/** Buscar sin acentos ni mayúsculas, ordenando por parecido. */
object Buscar {
	private val MARCAS = Regex("\\p{M}+")

	/** [texto] en minúsculas y sin acentos («Música» y «musica» son lo mismo). */
	fun normalizar(texto: String): String =
		Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARCAS, "").trim()

	/**
	 * Lo parecido que es [nombre] a [consulta], ya normalizadas las dos; 0 es que no vale.
	 * Igual, luego empieza por, luego empieza una palabra, luego contiene, y las iniciales
	 * («ws» de «WhatsApp Business»). Nada de parecido difuso: dar resultados que no se
	 * entienden es peor que no darlos cuando se oyen en vez de verse.
	 */
	fun parecido(nombre: String, consulta: String): Int {
		if (consulta.isEmpty()) return 0
		if (nombre == consulta) return 100
		if (nombre.startsWith(consulta)) return 80
		val palabras = nombre.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
		if (palabras.any { it.startsWith(consulta) }) return 60
		if (nombre.contains(consulta)) return 40
		if (consulta.length >= 2 && palabras.size >= 2 && palabras.joinToString("") { it.take(1) }.startsWith(consulta)) return 30
		return 0
	}

	/** Los [elementos] que valen para [consulta], del más parecido al menos y, a igualdad, por nombre. */
	fun <T> filtrar(elementos: List<T>, consulta: String, nombre: (T) -> String): List<T> {
		val q = normalizar(consulta)
		if (q.isEmpty()) return elementos
		return elementos.map { it to normalizar(nombre(it)) }
			.map { (e, n) -> Triple(e, n, parecido(n, q)) }
			.filter { it.third > 0 }
			.sortedWith(compareBy({ -it.third }, { it.second }))
			.map { it.first }
	}
}
