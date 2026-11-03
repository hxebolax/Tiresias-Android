package com.xebolax.tiresias.lectura

import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.nucleo.UnidadPartes

/**
 * Lo que comparten el lector y la pantalla de edición por partes mientras está
 * abierta: el texto que se edita, solo en memoria, y cómo devolverlo al campo.
 * Se olvida al cerrar la pantalla. Va aquí y no en el Intent para que el texto
 * de la pantalla no salga del proceso de Tiresias.
 */
object EdicionPartes {
	class Trabajo(
		/** El campo o el texto de donde sale (null si ya no existe). */
		val nodo: AccessibilityNodeInfo?,
		/** El texto que tenía al abrir la pantalla (para saber si la app lo ha cambiado mientras). */
		val original: String,
		/** Si se puede devolver al campo; si no, la pantalla solo lee y copia. */
		val editable: Boolean,
		/** Lo que como mucho admite el campo (0 sin límite). */
		val maximo: Int,
		/** Un campo de una sola línea: los saltos de línea se guardan como espacios. */
		val unaLinea: Boolean,
		/** Escribe el texto en el campo (lo hace el lector cuando la app vuelve a estar delante). */
		val guardar: (String) -> Unit,
	) {
		/** El texto como va quedando, y lo de deshacer y rehacer: aquí para que no se pierdan al girar la pantalla. */
		var texto: String = original
		val deshacer = ArrayDeque<String>()
		val rehacer = ArrayDeque<String>()
		var unidad: UnidadPartes? = null
		val marcadas = HashSet<Int>()
	}

	@Volatile var trabajo: Trabajo? = null
}
