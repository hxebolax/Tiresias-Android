package com.xebolax.tiresias.lectura

import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.arbol.Nodos

/**
 * Recuerda el elemento enfocado de cada pantalla para volver a él: al volver a
 * una pantalla (también al pulsar Atrás, petición de Backtalk 151) el foco
 * vuelve donde estaba, en vez de al principio.
 *
 * Se recuerda por paquete y clase de la ventana más el identificador del nodo
 * (o su texto y posición si no tiene), porque los ids de ventana cambian.
 */
class MemoriaVentanas {
	private data class Recuerdo(val id: String?, val texto: String, val top: Int, val left: Int)

	private val recuerdos = object : LinkedHashMap<String, Recuerdo>(32, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Recuerdo>?) = size > 40
	}

	/** Recuerda [n] con [nombre], lo que dice Tiresias de él (las filas de lista no tienen texto propio: está en sus hijos). */
	fun recordar(n: AccessibilityNodeInfo, nombre: String) {
		val clave = clave(n) ?: return
		val r = Nodos.limites(n)
		recuerdos[clave] = Recuerdo(n.viewIdResourceName, nombre, r.top, r.left)
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
		val lista = lector.navegador.orden(raiz)
		val mismoNombre = lista.filter { r.texto.isNotEmpty() && lector.elemento(it).nombre == r.texto }
		// Si hay varios con el mismo nombre, el del mismo identificador y, si no, el más cercano a donde estaba
		val elegido = mismoNombre.firstOrNull { r.id != null && it.viewIdResourceName == r.id && mismoNombre.size == 1 }
			?: mismoNombre.minByOrNull { val o = Nodos.limites(it); Math.abs(o.top - r.top) + Math.abs(o.left - r.left) }
			?: return
		lector.enfocar(elegido, null, com.xebolax.tiresias.voz.Modo.ENCOLAR)
	}

	/** La pantalla de [n]: app y título de su ventana (la misma ventana cambia de página dentro de una app, el título no). */
	private fun clave(n: AccessibilityNodeInfo): String? {
		val ventana = runCatching { n.window }.getOrNull() ?: return null
		return "${n.packageName}/${ventana.title}"
	}

	private fun claveDe(raiz: AccessibilityNodeInfo): String? = clave(raiz)
}
