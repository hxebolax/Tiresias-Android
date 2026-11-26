package com.xebolax.tiresias.lectura

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lo que está en el árbol de un ScrollView pero fuera de la pantalla, para que deslizar lo traiga a la vista sin pasarse.
 * Un ScrollView tiene todo su contenido en el árbol, así que lo siguiente ya existe antes de verse; las listas reutilizan
 * sus filas y no, y esas se dejan al desplazamiento por páginas de siempre.
 *
 * Es la idea de `OffscreenContent` de Backtalk y SandFly: se miran todos los nodos (no solo las paradas), y cuenta lo
 * que está fuera de la pantalla o cortado por el borde y dice o hace algo. Con las paradas se perdía lo que asoma unos
 * píxeles: el encabezado de una categoría cuyo texto aún no se ve no es parada (su texto lo lee él, y él no dice nada
 * mientras su texto no se ve), y deslizar se lo saltaba (los Ajustes de pantalla de Android 17).
 */
internal object FueraDeLaVista {

	/** Si el árbol es más grande, no se mira: recorrerlo en cada borde costaría más que el desplazamiento que ahorra. */
	private const val MAXIMO_NODOS = 1500

	/** Lo siguiente que no se ve y lo que queda a una pantalla de ahí. */
	data class Delante(val siguiente: AccessibilityNodeInfo, val finDePagina: AccessibilityNodeInfo)

	/** El subárbol en preorden, con dónde acaba el de cada nodo y si está escondido dentro de algo. */
	private class Recogido(val nodos: List<AccessibilityNodeInfo>, val fin: IntArray, val oculto: BooleanArray)

	/**
	 * Lo primero tras [pivote] en [contenedor] (en el orden del árbol, sin lo de dentro del pivote, que ya lo dice él)
	 * que está fuera de la pantalla o cortado y dice o hace algo, y lo que queda a una pantalla de ahí (por cuántas cosas
	 * así se ven ahora): enseñar una pantalla de golpe hace que los siguientes deslizamientos no se queden en el borde.
	 * Null si no hay nada o el árbol es demasiado grande.
	 */
	fun delante(contenedor: AccessibilityNodeInfo, pivote: AccessibilityNodeInfo, adelante: Boolean): Delante? {
		val r = recoger(contenedor) ?: return null
		val p = r.nodos.indexOf(pivote)
		if (p < 0) return null
		val enPantalla = r.nodos.indices.count { !r.oculto[it] && r.nodos[it].isVisibleToUser && diceOHace(r.nodos[it]) }
		val queridos = maxOf(1, enPantalla)
		val hallados = ArrayList<AccessibilityNodeInfo>()
		if (adelante) {
			var i = r.fin[p]
			while (i < r.nodos.size && hallados.size < queridos) {
				if (!r.oculto[i] && candidato(r.nodos[i])) hallados += r.nodos[i]
				i++
			}
		} else {
			var i = p - 1
			while (i >= 0 && hallados.size < queridos) {
				// (los antepasados del pivote van antes en el orden del árbol, pero están en la pantalla, alrededor)
				if (r.fin[i] <= p && !r.oculto[i] && candidato(r.nodos[i])) hallados += r.nodos[i]
				i--
			}
		}
		if (hallados.isEmpty()) return null
		return Delante(hallados.first(), hallados.last())
	}

	/**
	 * De [paradas] (lo que se ve, en orden), la primera que va tras [pivote] en el orden del árbol de [contenedor] (o la
	 * última de antes, hacia atrás). Es buscar otra vez desde donde estaba el foco cuando ya se ha traído lo siguiente a la
	 * vista, como SandFly: el pivote puede haberse quedado fuera de la pantalla, y lo que asomaba ya es una parada.
	 */
	fun primeraTras(contenedor: AccessibilityNodeInfo, pivote: AccessibilityNodeInfo, paradas: List<AccessibilityNodeInfo>, adelante: Boolean): AccessibilityNodeInfo? {
		val r = recoger(contenedor) ?: return null
		val p = r.nodos.indexOf(pivote)
		if (p < 0) return null
		val sitio = HashMap<AccessibilityNodeInfo, Int>(r.nodos.size * 2)
		r.nodos.forEachIndexed { i, n -> sitio.putIfAbsent(n, i) }
		var mejor: AccessibilityNodeInfo? = null
		var mejorSitio = if (adelante) Int.MAX_VALUE else Int.MIN_VALUE
		for (n in paradas) {
			val k = sitio[n] ?: continue
			if (adelante && k >= r.fin[p] && k < mejorSitio) {
				mejor = n
				mejorSitio = k
			} else if (!adelante && k < p && r.fin[k] <= p && k > mejorSitio) {
				mejor = n
				mejorSitio = k
			}
		}
		return mejor
	}

	private fun recoger(raiz: AccessibilityNodeInfo): Recogido? {
		val nodos = ArrayList<AccessibilityNodeInfo>()
		val fin = ArrayList<Int>()
		val oculto = ArrayList<Boolean>()
		// Cada entrada es un nodo por visitar o el índice de uno visitado cuyo subárbol acaba ahí
		val pila = ArrayDeque<Any>()
		pila.addLast(Visita(raiz, false))
		while (pila.isNotEmpty()) {
			val arriba = pila.removeLast()
			if (arriba is Int) {
				fin[arriba] = nodos.size
				continue
			}
			val v = arriba as Visita
			if (nodos.size >= MAXIMO_NODOS) return null
			val i = nodos.size
			nodos += v.nodo
			fin += i + 1
			oculto += v.oculto
			pila.addLast(i)
			val hijosOcultos = v.oculto || (v.nodo != raiz && escondeHijos(v.nodo))
			for (k in v.nodo.childCount - 1 downTo 0) {
				val h = runCatching { v.nodo.getChild(k) }.getOrNull() ?: continue
				pila.addLast(Visita(h, hijosOcultos))
			}
		}
		return Recogido(nodos, fin.toIntArray(), oculto.toBooleanArray())
	}

	private class Visita(val nodo: AccessibilityNodeInfo, val oculto: Boolean)

	/**
	 * Lo de dentro puede estar fuera de la pantalla sin haberse desplazado: algo plegado (sin tamaño), un paginador o
	 * algo que se desplaza por su cuenta.
	 */
	private fun escondeHijos(n: AccessibilityNodeInfo): Boolean {
		val tam = tamano(n)
		if (tam.width() <= 0 || tam.height() <= 0 || n.isScrollable) return true
		return n.className?.toString()?.endsWith("ViewPager") == true || n.className?.toString()?.endsWith("ViewPager2") == true
	}

	private fun candidato(n: AccessibilityNodeInfo) = fueraOCortado(n) && diceOHace(n)

	/** Tiene tamaño y está fuera de la pantalla, o se ve solo un trozo (cortado por el borde: aún no se ha leído). */
	private fun fueraOCortado(n: AccessibilityNodeInfo): Boolean {
		val tam = tamano(n)
		if (tam.width() <= 0 || tam.height() <= 0) return false
		if (!n.isVisibleToUser) return true
		val visto = Rect().also { n.getBoundsInScreen(it) }
		return visto.width() < tam.width() || visto.height() < tam.height()
	}

	private fun diceOHace(n: AccessibilityNodeInfo) =
		!n.text.isNullOrEmpty() || !n.contentDescription.isNullOrEmpty() || n.isFocusable || n.isClickable ||
			(android.os.Build.VERSION.SDK_INT >= 28 && n.isScreenReaderFocusable)

	/** El tamaño entero (los límites en la pantalla se recortan a lo que se ve; los de dentro del padre, no). */
	@Suppress("DEPRECATION")
	private fun tamano(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInParent(it) }
}
