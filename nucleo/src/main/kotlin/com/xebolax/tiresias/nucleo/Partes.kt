package com.xebolax.tiresias.nucleo

/**
 * La edición por partes (como el modo de edición granular de Jieshuo): el texto
 * partido en caracteres, palabras, frases, líneas o párrafos, o entero, para
 * leerlo como una lista y cambiar, borrar o insertar una parte sin tocar el
 * resto. Lo que hay entre las partes (los espacios, la puntuación suelta, los
 * saltos de línea) se conserva tal cual.
 */
enum class UnidadPartes { CARACTERES, PALABRAS, FRASES, LINEAS, PARRAFOS, TODO }

object Partes {
	/** Las partes de [texto] en [unidad], con su sitio en él. Sin texto, ninguna. */
	fun partir(texto: String, unidad: UnidadPartes): List<Segmento> {
		if (texto.isEmpty()) return emptyList()
		return when (unidad) {
			UnidadPartes.CARACTERES -> buildList {
				var i = 0
				while (i < texto.length) {
					val fin = i + Character.charCount(texto.codePointAt(i))
					add(Segmento(i, fin, texto.substring(i, fin)))
					i = fin
				}
			}
			UnidadPartes.PALABRAS -> Segmentos.palabras(texto)
			UnidadPartes.FRASES -> Segmentos.frases(texto)
			UnidadPartes.LINEAS -> Segmentos.lineas(texto)
			UnidadPartes.PARRAFOS -> Segmentos.parrafos(texto)
			UnidadPartes.TODO -> listOf(Segmento(0, texto.length, texto))
		}
	}

	/** [texto] con la parte [p] cambiada por [nuevo]. */
	fun cambiar(texto: String, p: Segmento, nuevo: String): String =
		texto.substring(0, p.inicio) + nuevo + texto.substring(p.fin)

	/**
	 * [texto] sin la parte [p]. Sin dejar dos separadores seguidos: al quitar una
	 * palabra o una frase se va también un espacio de los que la rodean, y al
	 * quitar una línea, su salto de línea.
	 */
	fun borrar(texto: String, p: Segmento, unidad: UnidadPartes): String {
		var a = p.inicio
		var b = p.fin
		when (unidad) {
			UnidadPartes.CARACTERES, UnidadPartes.TODO -> Unit
			UnidadPartes.LINEAS, UnidadPartes.PARRAFOS -> {
				// El salto de después (o, si es lo último, el de antes)
				if (b < texto.length && texto[b] == '\n') b++
				else if (a > 0 && texto[a - 1] == '\n') a--
				// Un párrafo se lleva también la línea en blanco que lo separa del siguiente
				if (unidad == UnidadPartes.PARRAFOS) while (b < texto.length && texto[b] == '\n') b++
			}
			UnidadPartes.PALABRAS, UnidadPartes.FRASES -> {
				val antesBlanco = a == 0 || texto[a - 1] == ' '
				if (b < texto.length && texto[b] == ' ' && antesBlanco) b++
				else if (a > 0 && texto[a - 1] == ' ' && (b == texto.length || texto[b] in ".,;:!?…\n")) a--
			}
		}
		return texto.substring(0, a) + texto.substring(b)
	}

	/** [texto] con [nuevo] puesto antes o [despues] de la parte [p], con el separador que corresponde a [unidad]. */
	fun insertar(texto: String, p: Segmento, nuevo: String, despues: Boolean, unidad: UnidadPartes): String {
		val sep = separador(unidad)
		return if (despues) texto.substring(0, p.fin) + sep + nuevo + texto.substring(p.fin)
		else texto.substring(0, p.inicio) + nuevo + sep + texto.substring(p.inicio)
	}

	/** Lo que separa dos partes de [unidad] al insertar una nueva. */
	fun separador(unidad: UnidadPartes): String = when (unidad) {
		UnidadPartes.CARACTERES, UnidadPartes.TODO -> ""
		UnidadPartes.PALABRAS, UnidadPartes.FRASES -> " "
		UnidadPartes.LINEAS -> "\n"
		UnidadPartes.PARRAFOS -> "\n\n"
	}

	/** [texto] sin las [partes] dadas (de atrás adelante, para que no se muevan las que quedan). */
	fun borrarVarias(texto: String, partes: List<Segmento>, unidad: UnidadPartes): String =
		partes.sortedByDescending { it.inicio }.fold(texto) { t, p -> borrar(t, p, unidad) }

	/** Lo de [partes] junto, en su orden, con el separador de [unidad] (para copiarlo). */
	fun juntar(partes: List<Segmento>, unidad: UnidadPartes): String =
		partes.sortedBy { it.inicio }.joinToString(if (unidad == UnidadPartes.CARACTERES) "" else if (unidad == UnidadPartes.PALABRAS || unidad == UnidadPartes.FRASES) " " else "\n") { it.texto }

	/** Cambia todos los [buscado] de [texto] por [nuevo]: el texto nuevo y cuántos se han cambiado. */
	fun reemplazar(texto: String, buscado: String, nuevo: String, distinguirMayusculas: Boolean): Pair<String, Int> {
		if (buscado.isEmpty()) return texto to 0
		var veces = 0
		val sb = StringBuilder()
		var i = 0
		while (true) {
			val j = texto.indexOf(buscado, i, ignoreCase = !distinguirMayusculas)
			if (j < 0) break
			sb.append(texto, i, j).append(nuevo)
			i = j + buscado.length
			veces++
		}
		sb.append(texto, i, texto.length)
		return sb.toString() to veces
	}
}

/**
 * Lo que hace cada gesto en el modo edición (como el modo carácter a carácter
 * de Jieshuo, con los mismos gestos de un dedo): moverse por el texto en todas
 * las unidades a la vez sin cambiar el control de lectura, y seleccionar con
 * esos mismos movimientos. Los gestos que no están aquí hacen lo de siempre.
 */
enum class OrdenEdicion {
	CARACTER_SIGUIENTE, CARACTER_ANTERIOR, PALABRA_SIGUIENTE, PALABRA_ANTERIOR, FRASE_SIGUIENTE, FRASE_ANTERIOR,
	LINEA_SIGUIENTE, LINEA_ANTERIOR, PARRAFO_SIGUIENTE, PARRAFO_ANTERIOR, PRINCIPIO_LINEA, FINAL_LINEA, PRINCIPIO, FINAL,
	SELECCIONAR, LEER, MENU_EDICION, DESHACER, REHACER, SALIR;

	companion object {
		/** El mapa del modo edición. No lo cambia ningún perfil ni ajuste: es el mismo siempre, para aprenderlo una vez. */
		val GESTOS: Map<Gesto, OrdenEdicion> = mapOf(
			Gesto.ABAJO to CARACTER_SIGUIENTE,
			Gesto.ARRIBA to CARACTER_ANTERIOR,
			Gesto.DERECHA_IZQUIERDA to PALABRA_SIGUIENTE,
			Gesto.IZQUIERDA_DERECHA to PALABRA_ANTERIOR,
			Gesto.DERECHA to FRASE_SIGUIENTE,
			Gesto.IZQUIERDA to FRASE_ANTERIOR,
			Gesto.ABAJO_2 to LINEA_SIGUIENTE,
			Gesto.ARRIBA_2 to LINEA_ANTERIOR,
			Gesto.DERECHA_ABAJO to PARRAFO_SIGUIENTE,
			Gesto.IZQUIERDA_ARRIBA to PARRAFO_ANTERIOR,
			Gesto.IZQUIERDA_2 to PRINCIPIO_LINEA,
			Gesto.DERECHA_2 to FINAL_LINEA,
			Gesto.ARRIBA_ABAJO to PRINCIPIO,
			Gesto.ABAJO_ARRIBA to FINAL,
			Gesto.ARRIBA_DERECHA to SELECCIONAR,
			Gesto.TOQUE_MANTENIDO_2 to LEER,
			Gesto.ARRIBA_IZQUIERDA to MENU_EDICION,
			Gesto.IZQUIERDA_3 to DESHACER,
			Gesto.DERECHA_3 to REHACER,
			Gesto.IZQUIERDA_ABAJO to SALIR,
		)
	}
}
