package com.xebolax.tiresias.nucleo

/**
 * Un nodo de la pantalla tal como lo necesita la reparación automática: cómo
 * es, nunca lo que dice. De sus textos solo hay una [huella] (un número que
 * es igual si dos elementos dicen lo mismo) para encontrar repetidos; no se
 * guarda ni se puede recuperar el texto.
 */
class NodoReparar(
	val clase: String,
	/** El identificador corto (lo de después de `:id/`), o null si no tiene. */
	val id: String?,
	/** Si Tiresias se para en él al deslizar. */
	val parada: Boolean,
	/** Si se pulsa o se mantiene pulsado. */
	val accionable: Boolean,
	/** Si tiene texto o descripción propios. */
	val conTexto: Boolean,
	/** La huella de lo que Tiresias dice de él (0 si no es una parada o no dice nada). */
	val huella: Int = 0,
	val encabezado: Boolean = false,
	val marcable: Boolean = false,
	val editable: Boolean = false,
	/** Una lista: se desplaza o dice cuántas filas tiene. */
	val lista: Boolean = false,
	val hijos: List<NodoReparar> = emptyList(),
)

enum class TipoReparacion {
	/** Las filas de una lista se leen a trozos (el título en una parada, la fecha en otra): cada fila, una parada. */
	AGRUPAR_FILA,
	/** Una parada que no dice nada ni se pulsa (un adorno): se quita. */
	OCULTAR_VACIO,
	/** Una parada que repite lo que ya dice la de al lado o la que la contiene: se quita. */
	OCULTAR_REPETIDO,
	/** Los títulos de las secciones de una lista, que la app no marca como encabezados: se marcan. */
	ENCABEZADO,
	/**
	 * Lo mismo que [AGRUPAR_FILA] cuando la fila no existe para Android (un contenedor que no se pulsa ni dice nada
	 * propio se quita del árbol de accesibilidad y sus textos quedan sueltos y seguidos: título, fecha, título,
	 * fecha...): cada trozo se une al de antes, y la fila se lee de una vez.
	 */
	UNIR_CON_ANTERIOR,
}

/** Una reparación: qué se hace con los elementos de identificador [id], y cuántos se encontraron. */
data class Reparacion(val tipo: TipoReparacion, val id: String, val veces: Int)

/**
 * La reparación automática de pantallas mal hechas: busca en la estructura de
 * una pantalla los fallos más comunes de las apps que no se han probado con
 * un lector, y propone una regla para cada uno (por el identificador de la
 * vista, como las reglas de los complementos). Es conservadora: solo propone
 * lo que se repite o lo que es seguro que no dice nada, y nunca toca un
 * control que se pulsa, un campo ni una casilla.
 */
object Reparaciones {
	/** Las filas con el mismo identificador que hacen falta para dar una lista por mal leída. */
	const val FILAS_MINIMAS = 3

	private val NO_OCULTAR = listOf("Progress", "SeekBar", "Slider", "Rating", "Switch", "CheckBox", "RadioButton", "EditText", "Button", "WebView")

	fun analizar(raiz: NodoReparar): List<Reparacion> {
		val propuestas = LinkedHashMap<Pair<TipoReparacion, String>, Int>()
		fun proponer(t: TipoReparacion, id: String) {
			val k = t to id
			propuestas[k] = (propuestas[k] ?: 0) + 1
		}
		recorrer(raiz, null, ::proponer)
		// Una reparación por identificador: si uno sale para agrupar y para otra cosa, manda agrupar
		val agrupados = propuestas.keys.filter { it.first == TipoReparacion.AGRUPAR_FILA }.map { it.second }.toSet()
		return propuestas.filter { (k, _) -> k.first == TipoReparacion.AGRUPAR_FILA || k.second !in agrupados }
			.map { (k, veces) -> Reparacion(k.first, k.second, veces) }
	}

	private fun recorrer(n: NodoReparar, paradaDeFuera: NodoReparar?, proponer: (TipoReparacion, String) -> Unit) {
		// Una lista o cualquier contenedor con varias filas (una pantalla hecha a mano que cabe entera no se desplaza)
		if ((n.lista && n.hijos.size > 1) || n.hijos.size >= FILAS_MINIMAS) mirarLista(n, proponer)
		val id = n.id
		if (n.parada && id != null && !n.accionable && !n.marcable && !n.editable) {
			// Nada que decir ni nada dentro que lo diga: un adorno que Tiresias lee como «sin etiqueta»
			if (!algoQueDecir(n) && NO_OCULTAR.none { n.clase.contains(it) }) proponer(TipoReparacion.OCULTAR_VACIO, id)
			// Lo mismo que dice la parada que lo contiene
			else if (n.huella != 0 && paradaDeFuera != null && paradaDeFuera.huella == n.huella) proponer(TipoReparacion.OCULTAR_REPETIDO, id)
		}
		// Dos paradas seguidas que dicen lo mismo (la foto «Ana» y el texto «Ana»): sobra la que no se pulsa
		val paradas = n.hijos.filter { it.parada }
		for (i in 1 until paradas.size) {
			val a = paradas[i - 1]
			val b = paradas[i]
			if (a.huella == 0 || a.huella != b.huella) continue
			val sobra = when {
				!b.accionable && b.id != null && !b.marcable && !b.editable -> b
				!a.accionable && a.id != null && !a.marcable && !a.editable -> a
				else -> null
			}
			sobra?.id?.let { proponer(TipoReparacion.OCULTAR_REPETIDO, it) }
		}
		val fuera = if (n.parada) n else paradaDeFuera
		for (h in n.hijos) recorrer(h, fuera, proponer)
	}

	/** Las filas de [lista]: si se leen a trozos, agrupar; y los títulos de sus secciones, encabezados. */
	private fun mirarLista(lista: NodoReparar, proponer: (TipoReparacion, String) -> Unit) {
		val filas = filasDe(lista)
		val porId = filas.filter { it.id != null }.groupBy { it.id!! }
		for ((id, filas) in porId) {
			if (filas.size < FILAS_MINIMAS) continue
			// Filas que no son una parada, ni se pulsan, y cuyo contenido se lee en dos o más paradas que no son controles
			val partidas = filas.count { f ->
				if (f.parada || f.accionable) return@count false
				val dentro = paradasDentro(f)
				dentro.size >= 2 && dentro.none { it.accionable || it.marcable || it.editable }
			}
			if (partidas >= FILAS_MINIMAS && partidas * 2 >= filas.size) proponer(TipoReparacion.AGRUPAR_FILA, id)
		}
		// Filas que Android ha quitado del árbol: sus trozos quedan sueltos y seguidos (título, fecha, título, fecha...).
		// Un identificador que siempre va justo detrás de otro, al menos tres veces, se une a él
		val unidos = HashSet<String>()
		fun suelto(n: NodoReparar) = n.id != null && n.parada && !n.accionable && !n.marcable && !n.editable && n.conTexto && paradasDentro(n).isEmpty()
		val detras = HashMap<String, HashSet<String?>>()
		for (i in filas.indices) {
			val b = filas[i]
			if (b.id == null) continue
			val a = filas.getOrNull(i - 1)
			detras.getOrPut(b.id) { HashSet() } += if (a != null && suelto(a) && suelto(b) && a.id != b.id) a.id else null
		}
		for ((id, antes) in detras) {
			val delante = antes.singleOrNull() ?: continue
			if ((porId[id]?.size ?: 0) >= FILAS_MINIMAS) {
				proponer(TipoReparacion.UNIR_CON_ANTERIOR, id)
				unidos += id
				unidos += delante
			}
		}
		// Títulos de sección: dos o más paradas iguales en forma que no se pulsan y dicen algo, cada una seguida de una
		// fila que sí se pulsa
		val pulsables = filas.count { it.accionable }
		if (pulsables < 2) return
		for ((id, iguales) in porId) {
			if (id in unidos || iguales.size < 2 || iguales.size * 2 > filas.size) continue
			val titulos = iguales.all { it.parada && !it.accionable && !it.encabezado && it.conTexto && !it.marcable && !it.editable && paradasDentro(it).isEmpty() }
			val antesDeOpciones = iguales.all { t -> filas.getOrNull(filas.indexOf(t) + 1)?.accionable == true }
			if (titulos && antesDeOpciones) proponer(TipoReparacion.ENCABEZADO, id)
		}
	}

	/**
	 * Las filas de [lista]: sus hijos o, si solo tiene uno que las envuelve (un `ScrollView` con un `LinearLayout`
	 * dentro, lo más común en las pantallas hechas a mano), los de ese, hasta tres niveles.
	 */
	private fun filasDe(lista: NodoReparar): List<NodoReparar> {
		var n = lista
		repeat(3) {
			val unico = n.hijos.singleOrNull() ?: return n.hijos
			if (unico.parada || unico.accionable || unico.lista) return n.hijos
			n = unico
		}
		return n.hijos
	}

	private fun paradasDentro(n: NodoReparar): List<NodoReparar> = n.hijos.flatMap { h -> (if (h.parada) listOf(h) else emptyList()) + paradasDentro(h) }

	private fun algoQueDecir(n: NodoReparar): Boolean = n.conTexto || n.hijos.any { algoQueDecir(it) }
}
