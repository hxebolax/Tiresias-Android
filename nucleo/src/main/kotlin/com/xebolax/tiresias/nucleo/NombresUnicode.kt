package com.xebolax.tiresias.nucleo

/**
 * Del nombre Unicode en inglés de un carácter («yen sign», «inverted question
 * mark», «commercial at») al carácter. Gboard describe las teclas de símbolos
 * con ese nombre, en inglés sea cual sea el idioma del móvil (solo unas pocas
 * tienen un nombre propio: «period», «slash»), y el lector lo necesita para
 * decir la tecla con el nombre del símbolo en su idioma.
 */
object NombresUnicode {
	/** Los tramos de caracteres que se miran: puntuación y símbolos de los teclados, monedas, flechas, formas. */
	private val TRAMOS = listOf(
		0x21..0x7E, 0xA1..0x17F, 0x2010..0x2027, 0x2030..0x205E, 0x20A0..0x20C0,
		0x2100..0x214F, 0x2190..0x21FF, 0x2200..0x22FF, 0x2300..0x23FF, 0x2500..0x27BF, 0x3000..0x303F,
	)

	private val porNombre: Map<String, String> by lazy {
		val mapa = HashMap<String, String>(4096)
		for (tramo in TRAMOS) for (cp in tramo) {
			val nombre = runCatching { Character.getName(cp) }.getOrNull() ?: continue
			mapa.putIfAbsent(nombre.lowercase(), String(Character.toChars(cp)))
		}
		mapa
	}

	/** El carácter cuyo nombre Unicode es [nombre] (sin distinguir mayúsculas), o null. */
	fun caracter(nombre: String): String? {
		val limpio = nombre.trim().lowercase()
		if (limpio.length < 3 || limpio.codePointCount(0, limpio.length) == 1) return null
		return porNombre[limpio]
	}
}
