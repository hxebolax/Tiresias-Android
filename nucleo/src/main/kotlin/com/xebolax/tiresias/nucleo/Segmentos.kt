package com.xebolax.tiresias.nucleo

/** Unidades de movimiento por el texto. */
enum class Granularidad { CARACTER, PALABRA, LINEA, PARRAFO, FRASE, PAGINA }

/** Un trozo de texto: de [inicio] (incluido) a [fin] (excluido). */
data class Segmento(val inicio: Int, val fin: Int, val texto: String)

/**
 * Movimiento por un texto en las unidades de [Granularidad], para los textos
 * que Tiresias recorre él mismo (cursor de revisión, texto de OCR, nodos que no
 * admiten la granularidad). Los campos de edición usan las acciones de Android
 * cuando las admiten, y esto cuando no.
 *
 * TalkBack lleva sus propios iteradores de carácter, palabra y párrafo; aquí se
 * añaden línea y frase, y las palabras respetan los apóstrofos y los números
 * con decimales.
 */
object Segmentos {

	/** El segmento siguiente a [desde] (posición de cursor), o null si no hay más. */
	fun siguiente(texto: String, desde: Int, g: Granularidad): Segmento? {
		if (texto.isEmpty()) return null
		val p = desde.coerceIn(0, texto.length)
		return when (g) {
			Granularidad.CARACTER -> if (p < texto.length) {
				val fin = p + Character.charCount(texto.codePointAt(p))
				Segmento(p, fin, texto.substring(p, fin))
			} else null
			Granularidad.PALABRA -> palabras(texto).firstOrNull { it.inicio >= p }
			Granularidad.LINEA -> lineas(texto).firstOrNull { it.inicio >= p && (it.inicio > p || p == 0 || texto[p - 1] == '\n') }
				?: lineas(texto).firstOrNull { it.inicio > p }
			Granularidad.PARRAFO -> parrafos(texto).firstOrNull { it.inicio >= p }
			Granularidad.FRASE -> frases(texto).firstOrNull { it.inicio >= p }
			Granularidad.PAGINA -> null
		}
	}

	/** El segmento anterior a [desde] (posición de cursor), o null si no hay. */
	fun anterior(texto: String, desde: Int, g: Granularidad): Segmento? {
		if (texto.isEmpty()) return null
		val p = desde.coerceIn(0, texto.length)
		return when (g) {
			Granularidad.CARACTER -> if (p > 0) {
				val inicio = p - Character.charCount(texto.codePointBefore(p))
				Segmento(inicio, p, texto.substring(inicio, p))
			} else null
			Granularidad.PALABRA -> palabras(texto).lastOrNull { it.fin <= p }
			Granularidad.LINEA -> lineas(texto).lastOrNull { it.fin <= p && it.inicio < p }
			Granularidad.PARRAFO -> parrafos(texto).lastOrNull { it.fin <= p && it.inicio < p }
			Granularidad.FRASE -> frases(texto).lastOrNull { it.fin <= p && it.inicio < p }
			Granularidad.PAGINA -> null
		}
	}

	/** El segmento que contiene [posicion]. */
	fun en(texto: String, posicion: Int, g: Granularidad): Segmento? {
		val lista = when (g) {
			Granularidad.CARACTER -> return siguiente(texto, posicion, g)
			Granularidad.PALABRA -> palabras(texto)
			Granularidad.LINEA -> lineas(texto)
			Granularidad.PARRAFO -> parrafos(texto)
			Granularidad.FRASE -> frases(texto)
			Granularidad.PAGINA -> return null
		}
		return lista.firstOrNull { posicion in it.inicio until maxOf(it.fin, it.inicio + 1) } ?: lista.lastOrNull { it.inicio <= posicion }
	}

	/** Las palabras: letras y números, con apóstrofos y guiones internos y decimales («3,5», «l'Hospitalet»). */
	fun palabras(texto: String): List<Segmento> = PALABRA.findAll(texto).map { Segmento(it.range.first, it.range.last + 1, it.value) }.toList()

	/** Las líneas, sin el salto de línea. Una línea vacía cuenta. */
	fun lineas(texto: String): List<Segmento> {
		val r = mutableListOf<Segmento>()
		var inicio = 0
		for (i in texto.indices) {
			if (texto[i] == '\n') {
				r += Segmento(inicio, i, texto.substring(inicio, i))
				inicio = i + 1
			}
		}
		r += Segmento(inicio, texto.length, texto.substring(inicio))
		return r
	}

	/** Los párrafos: bloques separados por una o más líneas en blanco (o por un salto, si no hay líneas en blanco). */
	fun parrafos(texto: String): List<Segmento> {
		val separador = if (texto.contains(Regex("\\n\\s*\\n"))) Regex("\\n\\s*\\n") else Regex("\\n")
		val r = mutableListOf<Segmento>()
		var inicio = 0
		for (m in separador.findAll(texto)) {
			if (m.range.first > inicio) r += recortar(texto, inicio, m.range.first)
			inicio = m.range.last + 1
		}
		if (inicio < texto.length) r += recortar(texto, inicio, texto.length)
		return r.filter { it.texto.isNotBlank() }
	}

	/** Las frases: terminan en . ! ? … seguido de espacio o fin, o en un salto de línea. */
	fun frases(texto: String): List<Segmento> {
		val r = mutableListOf<Segmento>()
		var inicio = 0
		var i = 0
		while (i < texto.length) {
			val c = texto[i]
			val fin = c == '\n' || ((c == '.' || c == '!' || c == '?' || c == '…') && (i + 1 >= texto.length || texto[i + 1].isWhitespace()) && !esAbreviatura(texto, i))
			if (fin) {
				val s = recortar(texto, inicio, i + 1)
				if (s.texto.isNotBlank()) r += s
				inicio = i + 1
			}
			i++
		}
		if (inicio < texto.length) recortar(texto, inicio, texto.length).takeIf { it.texto.isNotBlank() }?.let { r += it }
		return r
	}

	private fun esAbreviatura(texto: String, i: Int): Boolean {
		if (texto[i] != '.') return false
		val palabra = texto.substring(0, i).takeLastWhile { it.isLetter() }
		return palabra.lowercase() in ABREVIATURAS || (palabra.length == 1 && palabra[0].isUpperCase())
	}

	private fun recortar(texto: String, inicio: Int, fin: Int): Segmento {
		var a = inicio
		var b = fin
		while (a < b && texto[a].isWhitespace()) a++
		while (b > a && texto[b - 1].isWhitespace()) b--
		return Segmento(a, b, texto.substring(a, b))
	}

	private val PALABRA = Regex("[\\p{L}\\p{N}]+(?:[’'\\-.,][\\p{L}\\p{N}]+)*")
	private val ABREVIATURAS = setOf("sr", "sra", "srta", "dr", "dra", "ud", "uds", "etc", "pág", "pag", "núm", "num", "tel", "av", "c", "vs", "p", "ej", "aprox", "mr", "mrs", "ms", "st")
}

/** Qué cambió en un campo de texto entre dos momentos: para decir lo escrito o lo borrado. */
sealed interface CambioTexto {
	data class Escrito(val texto: String) : CambioTexto
	data class Borrado(val texto: String) : CambioTexto
	data class Sustituido(val antes: String, val despues: String) : CambioTexto
	data object SinCambios : CambioTexto
}

object Edicion {
	/**
	 * Compara [antes] y [despues] y dice qué se escribió o se borró, buscando el
	 * prefijo y el sufijo comunes. Así funciona aunque la app no mande los
	 * índices del cambio (muchos teclados y apps los mandan mal).
	 */
	fun cambio(antes: String, despues: String): CambioTexto {
		if (antes == despues) return CambioTexto.SinCambios
		var prefijo = 0
		val minimo = minOf(antes.length, despues.length)
		while (prefijo < minimo && antes[prefijo] == despues[prefijo]) prefijo++
		var sufijo = 0
		while (sufijo < minimo - prefijo && antes[antes.length - 1 - sufijo] == despues[despues.length - 1 - sufijo]) sufijo++
		val quitado = antes.substring(prefijo, antes.length - sufijo)
		val puesto = despues.substring(prefijo, despues.length - sufijo)
		return when {
			quitado.isEmpty() -> CambioTexto.Escrito(puesto)
			puesto.isEmpty() -> CambioTexto.Borrado(quitado)
			else -> CambioTexto.Sustituido(quitado, puesto)
		}
	}

	/** La palabra que se acaba de terminar al escribir [despues] con el cursor en [cursor], para el eco por palabras. */
	fun palabraTerminada(despues: String, cursor: Int): String? {
		if (cursor <= 0 || cursor > despues.length) return null
		val ultimo = despues[cursor - 1]
		// El apóstrofo y el guion no terminan la palabra: «don't» y «bien-estar» se dicen enteras (Backtalk 27)
		if (ultimo.isLetterOrDigit() || ultimo in DENTRO_DE_PALABRA) return null
		val antes = despues.substring(0, cursor - 1)
		val palabra = antes.takeLastWhile { it.isLetterOrDigit() || it in DENTRO_DE_PALABRA }.trimStart { it in DENTRO_DE_PALABRA }
		return palabra.takeIf { it.isNotEmpty() }
	}

	/** Lo que puede ir dentro de una palabra sin terminarla. */
	private val DENTRO_DE_PALABRA = setOf('\'', '’', '-')
}

/**
 * Filtro de anuncios repetidos: el mismo texto del mismo origen dentro de
 * [ventanaMs] no se vuelve a decir. Lo necesitan las apps que mandan el mismo
 * evento sin parar (el ecualizador de Samsung lo manda cada 10 ms: Backtalk 183)
 * y los dobles eventos de foco. Además limita las tormentas a [maximoPorSegundo].
 */
class FiltroRepetidos(private val ventanaMs: Long = 700, private val maximoPorSegundo: Int = 8) {
	private val vistos = LinkedHashMap<String, Long>()
	private var inicioSegundo = 0L
	private var enSegundo = 0

	/** true si hay que decirlo; false si es repetido o una tormenta. [ahora] en milisegundos monotónicos. */
	@Synchronized
	fun pasa(origen: String, texto: String, ahora: Long): Boolean {
		val clave = "$origen\u0000$texto"
		val ultimo = vistos[clave]
		if (ultimo != null && ahora - ultimo < ventanaMs) return false
		if (ahora - inicioSegundo >= 1000) {
			inicioSegundo = ahora
			enSegundo = 0
		}
		if (++enSegundo > maximoPorSegundo) return false
		vistos[clave] = ahora
		if (vistos.size > 64) vistos.remove(vistos.keys.first())
		return true
	}

	@Synchronized
	fun olvidar() {
		vistos.clear()
		enSegundo = 0
	}
}

/** Comparación de versiones «1.2.10» > «1.2.9», número a número. */
object Versiones {
	fun comparar(a: String, b: String): Int {
		val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
		val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
		for (i in 0 until maxOf(x.size, y.size)) {
			val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
			if (c != 0) return c
		}
		return 0
	}
}
