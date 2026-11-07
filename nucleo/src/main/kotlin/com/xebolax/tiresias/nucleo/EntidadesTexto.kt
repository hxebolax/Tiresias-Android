package com.xebolax.tiresias.nucleo

/**
 * Lo que hay escrito en un texto y se puede usar aunque no sea un enlace (idea
 * de SandFly, `TextEntityMenu`): direcciones web y de correo, teléfonos y
 * códigos (el de un SMS de verificación). El menú de acciones los ofrece:
 * abrir, escribir, llamar o copiar.
 */
sealed class EntidadTexto(val visto: String) {
	class Web(visto: String, val url: String) : EntidadTexto(visto)
	class Correo(visto: String, val direccion: String) : EntidadTexto(visto)
	class Telefono(visto: String, val numero: String) : EntidadTexto(visto)
	class Codigo(visto: String) : EntidadTexto(visto)
}

/** Un trozo de [inicio] a [fin] (sin incluir) que ya es otra cosa: un enlace, o lo que reconoce el clasificador de Android. */
data class TrozoReconocido(val inicio: Int, val fin: Int, val telefono: Boolean = false)

object EntidadesTexto {
	const val MAXIMO = 20

	/** Si merece la pena mirar [texto]: cuatro cifras seguidas, una arroba, `://` o algo con forma de dominio. */
	fun puedeTener(texto: CharSequence?): Boolean = texto != null && texto.length >= 4 && PUEDE.containsMatchIn(texto)

	private val PUEDE = Regex("\\p{Nd}{4}|@|://|[a-zA-Z0-9]\\.[a-zA-Z]{2,}")
	private val CODIGO = Regex("(?<!\\p{Nd})\\p{Nd}{4,8}(?!\\p{Nd})")

	/**
	 * Lo que hay en [texto]. [enlaces] son las direcciones web y de correo encontradas (`mailto:` para el correo), con
	 * su posición; [clasificado] lo que reconoce el clasificador de texto de Android (teléfonos, fechas, horas,
	 * direcciones...) o null si no hay clasificador: entonces no se ofrecen teléfonos ni códigos, porque nada separa
	 * un código de una fecha. [yaEnlazados]: lo que el texto ya enlaza (lo ofrece el menú como enlace).
	 * [marcar] da el número para llamar a partir del que se ve.
	 */
	fun encontrar(
		texto: String,
		enlaces: List<Triple<Int, Int, String>>,
		clasificado: List<TrozoReconocido>?,
		yaEnlazados: Set<String> = emptySet(),
		marcar: (String) -> String = { normalizarTelefono(it) },
	): List<EntidadTexto> {
		if (texto.isBlank()) return emptyList()
		val encontradas = LinkedHashMap<String, EntidadTexto>()
		val ocupados = ArrayList<IntRange>()
		for ((inicio, fin, url) in enlaces.sortedBy { it.first }) {
			if (inicio < 0 || fin > texto.length || inicio >= fin) continue
			val visto = texto.substring(inicio, fin)
			ocupados += inicio until fin
			val e = when {
				url.startsWith("mailto:") -> EntidadTexto.Correo(visto, url.removePrefix("mailto:"))
				url in yaEnlazados || visto in yaEnlazados -> null
				else -> EntidadTexto.Web(visto, url)
			}
			if (e != null) encontradas.putIfAbsent(e.visto, e)
		}
		if (clasificado != null) {
			for (c in clasificado.sortedBy { it.inicio }) {
				if (c.inicio < 0 || c.fin > texto.length || c.inicio >= c.fin) continue
				ocupados += c.inicio until c.fin
				if (!c.telefono) continue
				val visto = texto.substring(c.inicio, c.fin)
				// Unas cifras sueltas, sin espacios ni prefijo, de 4 a 8 (482916): el clasificador las toma por un teléfono,
				// pero casi siempre son un código. Se ofrecen las dos cosas, el código primero
				if (visto.length in 4..8 && visto.all { it.isDigit() } && !unidoAntes(texto, c.inicio) && !unidoDespues(texto, c.fin)) {
					encontradas.putIfAbsent("código:$visto", EntidadTexto.Codigo(visto))
				}
				val numero = marcar(visto)
				if (numero.isNotEmpty()) encontradas.putIfAbsent(visto, EntidadTexto.Telefono(visto, numero))
			}
			for (m in CODIGO.findAll(texto)) {
				val r = m.range
				if (ocupados.any { r.first in it || r.last in it }) continue
				if (unidoAntes(texto, r.first) || unidoDespues(texto, r.last + 1)) continue
				encontradas.putIfAbsent(m.value, EntidadTexto.Codigo(m.value))
			}
		}
		return encontradas.values.take(MAXIMO)
	}

	/**
	 * Si las cifras que empiezan en [inicio] son parte de otra cosa: una palabra, un usuario, un enlace, una hora,
	 * una cantidad o un año de copyright. Un guion tras una sola letra (G-482916) las deja solas.
	 */
	private fun unidoAntes(t: String, inicio: Int): Boolean {
		val c = t.getOrNull(inicio - 1) ?: return false
		if (c.isLetterOrDigit() || c in "_@/\\#&=%+.,'–—" || moneda(c)) return true
		// Parte de una hora (10:45), pero no tras una palabra (Código:482916)
		if (c == ':') return t.getOrNull(inicio - 2)?.isDigit() == true
		if (c == '-') {
			val p = t.getOrNull(inicio - 2) ?: return false
			if (!p.isLetterOrDigit()) return false
			return p.isDigit() || t.getOrNull(inicio - 3)?.isLetterOrDigit() == true
		}
		val p = if (c.isWhitespace()) t.getOrNull(inicio - 2) else c
		return moneda(p) || p == '©'
	}

	/** Si las cifras que acaban antes de [fin] siguen en otra cosa. */
	private fun unidoDespues(t: String, fin: Int): Boolean {
		val c = t.getOrNull(fin) ?: return false
		if (c.isLetterOrDigit() || c in "_@/\\%" || moneda(c)) return true
		if (c in ".,:-–—'") return t.getOrNull(fin + 1)?.isLetterOrDigit() == true
		return c.isWhitespace() && moneda(t.getOrNull(fin + 1))
	}

	private fun moneda(c: Char?): Boolean = c != null && Character.getType(c) == Character.CURRENCY_SYMBOL.toInt()

	/** El número para marcar: solo cifras y el «+» del principio; «+44 (0)20» se marca «+4420». */
	fun normalizarTelefono(visto: String): String {
		val s = if (visto.trimStart().startsWith('+')) visto.replace("(0)", "") else visto
		val sb = StringBuilder()
		for (c in s) {
			if (c.isDigit()) sb.append(c) else if (c == '+' && sb.isEmpty()) sb.append(c)
		}
		return sb.toString()
	}
}
