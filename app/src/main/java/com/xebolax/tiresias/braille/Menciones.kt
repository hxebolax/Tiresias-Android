package com.xebolax.tiresias.braille

import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.base.Registro

/**
 * Las sugerencias de mención de WhatsApp («@Ana» en un grupo) desde el teclado
 * braille, sin cerrarlo (Can Talk). WhatsApp pone debajo del campo `entry` una
 * lista `list` (dentro de `mention_attach`, que Android a veces quita del
 * árbol) con filas pulsables que llevan el nombre en `primary_name`. Los
 * identificadores pueden cambiar en otra versión de WhatsApp: si no se
 * encuentran, no hay sugerencias y no pasa nada. No se guarda ningún nombre.
 */
object Menciones {
	private const val ETIQUETA = "Menciones"
	private val APPS = setOf("com.whatsapp", "com.whatsapp.w4b")

	/** Si [antes] (el texto hasta el cursor) acaba en una mención a medias: «@» al principio o tras un espacio. */
	fun hayConsulta(antes: CharSequence?): Boolean = antes != null && Regex("(^|\\s)@[^\\s@]*$").containsMatchIn(antes)

	/** Una sugerencia: el nombre y la fila que se pulsa. */
	class Sugerencia(val nombre: String, val fila: AccessibilityNodeInfo)

	/** Las sugerencias visibles en la ventana de [raiz], si es de WhatsApp; vacía si no hay una sola lista de menciones. */
	fun buscar(raiz: AccessibilityNodeInfo?, paquete: String?): List<Sugerencia> {
		if (raiz == null || paquete !in APPS) return emptyList()
		return try {
			val base = raiz.findAccessibilityNodeInfosByViewId("$paquete:id/mention_attach").firstOrNull() ?: raiz
			val listas = base.findAccessibilityNodeInfosByViewId("$paquete:id/list").filter { l ->
				(0 until l.childCount).any { i -> l.getChild(i)?.findAccessibilityNodeInfosByViewId("$paquete:id/primary_name")?.isNotEmpty() == true }
			}
			// Sin el contenedor, solo si hay una lista así: con dos no se sabe cuál es la de menciones
			if (listas.size != 1) return emptyList()
			val lista = listas[0]
			(0 until lista.childCount).mapNotNull { i ->
				val fila = lista.getChild(i) ?: return@mapNotNull null
				val nombre = fila.findAccessibilityNodeInfosByViewId("$paquete:id/primary_name").firstOrNull()?.text?.toString()?.trim()
				if (nombre.isNullOrEmpty() || !fila.isEnabled) null else Sugerencia(nombre, pulsable(fila))
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudieron leer las sugerencias", e)
			emptyList()
		}
	}

	/** Pulsa la sugerencia [nombre], comprobando antes que sigue en la lista. */
	fun elegir(raiz: AccessibilityNodeInfo?, paquete: String?, nombre: String): Boolean {
		val s = buscar(raiz, paquete).firstOrNull { it.nombre == nombre } ?: return false
		return runCatching { s.fila.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrElse {
			Registro.w(ETIQUETA, "No se pudo pulsar la sugerencia", it)
			false
		}
	}

	private fun pulsable(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
		var actual: AccessibilityNodeInfo? = n
		var vueltas = 0
		while (actual != null && vueltas < 4) {
			if (actual.isClickable) return actual
			actual = actual.parent
			vueltas++
		}
		return n
	}
}
