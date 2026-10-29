package com.xebolax.tiresias.lectura

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.arbol.Nodos

/**
 * Recuerda el elemento enfocado de cada pantalla para volver a él: al volver a
 * una pantalla (también al pulsar Atrás, petición de Backtalk 151) el foco
 * vuelve donde estaba, en vez de al principio.
 *
 * Se recuerda por paquete y clase de la ventana más el identificador del nodo
 * (o su texto y posición si no tiene), porque los ids de ventana cambian.
 *
 * Como Backtalk 311: si al volver aún no está (la app rehace la lista tarde), se
 * vuelve a buscar durante unos segundos, y se desplaza la lista hasta su fila;
 * una fila se reconoce también por su título, aunque cambie lo demás («3 sin
 * leer»). Si el usuario se mueve entretanto, se deja.
 */
class MemoriaVentanas {
	private data class Recuerdo(val id: String?, val texto: String, val titulo: String?, val fila: Int?, val top: Int, val left: Int)

	private val recuerdos = object : LinkedHashMap<String, Recuerdo>(32, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Recuerdo>?) = size > 40
	}

	/** Recuerda [n] con [nombre], lo que dice Tiresias de él (las filas de lista no tienen texto propio: está en sus hijos). */
	fun recordar(n: AccessibilityNodeInfo, nombre: String) {
		// Un campo de texto o una contraseña no se recuerdan (al volver, el foco no debe caer escribiendo)
		if (n.isEditable || n.isPassword) return
		val clave = clave(n) ?: return
		val r = Nodos.limites(n)
		recuerdos[clave] = Recuerdo(n.viewIdResourceName, nombre, tituloDeFila(n), filaDe(n), r.top, r.left)
	}

	/** Si se recuerda algo de la ventana [idVentana], le vuelve a poner el foco. */
	fun restaurar(lector: Lector, idVentana: Int) {
		// Si el foco ya está en un elemento vivo de esa ventana (el sistema lo puso), no se toca
		if (lector.foco?.let { it.windowId == idVentana && Nodos.vivo(it) } == true) return
		val raiz = lector.raiz() ?: return
		if (raiz.windowId != idVentana) return
		val clave = claveDe(raiz) ?: return
		val r = recuerdos[clave]
		if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasMemoria", "Ventana $clave: ${if (r == null) "sin recuerdo" else "recuerdo encontrado"}")
		r ?: return
		intentar(lector, idVentana, r, lector.momentoUsuario, SystemClock.uptimeMillis(), desplazado = false)
	}

	private fun intentar(lector: Lector, idVentana: Int, r: Recuerdo, usuario: Long, desde: Long, desplazado: Boolean) {
		// El usuario ya se ha movido (o ha tocado): manda él
		if (lector.momentoUsuario != usuario) return
		val raiz = lector.raiz() ?: return
		if (raiz.windowId != idVentana) return
		val elegido = buscar(lector, raiz, r)
		if (elegido != null) {
			if (elegido != lector.foco) lector.enfocar(elegido, null, com.xebolax.tiresias.voz.Modo.ENCOLAR)
			return
		}
		// No se ve: la lista va a su fila (una vez), y se vuelve a mirar al poco
		val movida = !desplazado && r.fila != null && desplazarAFila(raiz, r.fila)
		if (SystemClock.uptimeMillis() - desde < REINTENTAR_MS) {
			lector.manejador.postDelayed({ intentar(lector, idVentana, r, usuario, desde, desplazado || movida) }, CADA_MS)
		}
	}

	private fun buscar(lector: Lector, raiz: AccessibilityNodeInfo, r: Recuerdo): AccessibilityNodeInfo? {
		val lista = lector.navegador.orden(raiz)
		var candidatos = lista.filter { r.texto.isNotEmpty() && lector.elemento(it).nombre == r.texto }
		// Si lo que dice ha cambiado (una fila con «3 sin leer»), por el título de la fila
		if (candidatos.isEmpty() && !r.titulo.isNullOrEmpty()) candidatos = lista.filter { tituloDeFila(it) == r.titulo }
		// Si hay varios, el del mismo identificador y, si no, el más cercano a donde estaba
		return candidatos.firstOrNull { r.id != null && it.viewIdResourceName == r.id && candidatos.size == 1 }
			?: candidatos.minByOrNull { val o = Nodos.limites(it); Math.abs(o.top - r.top) + Math.abs(o.left - r.left) }
	}

	/** Desplaza la primera lista de [raiz] que sabe ir a una posición hasta la fila [fila]. */
	private fun desplazarAFila(raiz: AccessibilityNodeInfo, fila: Int): Boolean {
		val lista = Nodos.desplazables(raiz).firstOrNull { l ->
			l.collectionInfo != null && l.actionList.any { it.id == android.R.id.accessibilityActionScrollToPosition }
		} ?: return false
		val args = Bundle().apply {
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, fila)
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
		}
		return runCatching { lista.performAction(android.R.id.accessibilityActionScrollToPosition, args) }.getOrDefault(false)
	}

	/**
	 * El título de una fila o tarjeta: el único hijo (hasta dos niveles) que es encabezado o cuyo identificador
	 * acaba en «title». Lo demás de la fila puede cambiar sin que deje de ser ella.
	 */
	private fun tituloDeFila(n: AccessibilityNodeInfo): String? {
		val titulos = ArrayList<String>()
		fun mirar(x: AccessibilityNodeInfo, nivel: Int) {
			if (nivel > 2 || titulos.size > 1) return
			for (i in 0 until x.childCount) {
				val h = runCatching { x.getChild(i) }.getOrNull() ?: continue
				val id = h.viewIdResourceName?.substringAfter(":id/", "")?.lowercase().orEmpty()
				val texto = h.text?.toString()?.trim().orEmpty()
				if (texto.isNotEmpty() && (Nodos.esEncabezado(h) || id == "title" || id.endsWith("_title"))) titulos += texto
				else mirar(h, nivel + 1)
			}
		}
		mirar(n, 1)
		return titulos.singleOrNull()
	}

	/** La fila de [n] en su lista, si la app la da. */
	private fun filaDe(n: AccessibilityNodeInfo): Int? {
		var p: AccessibilityNodeInfo? = n
		repeat(3) {
			p?.collectionItemInfo?.let { return it.rowIndex }
			p = runCatching { p?.parent }.getOrNull() ?: return null
		}
		return null
	}

	/** La pantalla de [n]: app y título de su ventana (la misma ventana cambia de página dentro de una app, el título no). */
	private fun clave(n: AccessibilityNodeInfo): String? {
		val ventana = runCatching { n.window }.getOrNull() ?: return null
		return "${n.packageName}/${ventana.title}"
	}

	private fun claveDe(raiz: AccessibilityNodeInfo): String? = clave(raiz)

	companion object {
		/** Lo que se sigue buscando el elemento al volver a una pantalla, y cada cuánto. */
		private const val REINTENTAR_MS = 3000L
		private const val CADA_MS = 300L
	}
}
