package com.xebolax.tiresias.arbol

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.xebolax.tiresias.nucleo.Elemento
import com.xebolax.tiresias.nucleo.Expansion
import com.xebolax.tiresias.nucleo.Marcado
import com.xebolax.tiresias.nucleo.Posicion
import com.xebolax.tiresias.nucleo.Rango
import com.xebolax.tiresias.nucleo.Rol

/**
 * Convierte un nodo de accesibilidad en el [Elemento] que dice Tiresias.
 *
 * Usa las propiedades nuevas de cada versión cuando existen (triestado,
 * expandido, obligatorio y descripción complementaria de Android 16;
 * descripción de estado de Android 11; encabezados de Android 9...) y deduce
 * lo mismo en las anteriores.
 *
 * [etiqueta] da la etiqueta que el usuario puso a un elemento (por paquete e
 * identificador), si la hay.
 */
class Describir(private val etiqueta: (paquete: String, id: String) -> String? = { _, _ -> null }) {

	fun elemento(n: AccessibilityNodeInfo): Elemento {
		// Una fila pulsable con su interruptor o casilla dentro (los ajustes de Android): se dice como ese control
		val interior = if (!n.isCheckable && (n.isClickable || n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })) Nodos.marcableInterior(n) else null
		val rol = interior?.let { Nodos.rol(it) } ?: Nodos.rol(n)
		val editable = n.isEditable || rol == Rol.CAMPO_TEXTO
		val id = n.viewIdResourceName
		val paquete = n.packageName?.toString().orEmpty()
		val acciones = n.actionList
		val descripcion = n.contentDescription?.toString()?.trim().orEmpty()
		// Un campo vacío que enseña su pista da la pista como texto (Android 8+ lo marca): no es lo escrito
		val texto = if (editable && Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) "" else n.text?.toString().orEmpty()

		val nombre = when {
			editable -> etiquetaCampo(n, descripcion)
			descripcion.isNotEmpty() -> descripcion
			texto.isNotBlank() -> texto.trim()
			else -> Nodos.textoHijos(n)
		}

		return Elemento(
			nombre = nombre,
			texto = if (editable) texto else "",
			rol = rol,
			descripcionRol = descripcionRol(n),
			estado = estadoPropio(interior ?: n),
			marcado = marcado(interior ?: n),
			seleccionado = n.isSelected,
			activo = n.isEnabled,
			expansion = expansion(n, acciones),
			obligatorio = Build.VERSION.SDK_INT >= 36 && n.isFieldRequired,
			error = if (n.isContentInvalid) n.error?.toString() else null,
			encabezado = Nodos.esEncabezado(n),
			nivelEncabezado = Nodos.nivelEncabezado(n),
			pista = pista(n, editable, texto),
			complementaria = complementaria(n),
			rango = rango(n),
			posicion = posicion(n),
			pulsable = n.isClickable || acciones.any { it.id == AccessibilityNodeInfo.ACTION_CLICK },
			mantenible = n.isLongClickable || acciones.any { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK },
			editable = editable,
			contrasena = n.isPassword,
			tieneAcciones = accionesPropias(acciones) > 0,
			enlaces = Nodos.enlacesEnTexto(n).size,
			faltas = Nodos.faltas(n).size,
			escribiendo = editable && n.isFocused,
			identificador = id,
			etiquetaPersonal = if (id != null) etiqueta(paquete, id) else null,
		)
	}

	/**
	 * La descripción de estado que da la app, salvo la que pone Android solo en
	 * las casillas, interruptores y opciones estándar («Checked», «On», «Not
	 * selected»...): esa viene en el idioma del sistema, que puede no ser el de
	 * Tiresias, y Tiresias ya dice el estado en el suyo.
	 */
	private fun estadoPropio(n: AccessibilityNodeInfo): String? {
		if (Build.VERSION.SDK_INT < 30) return null
		val e = n.stateDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
		if (n.isCheckable && e.lowercase() in ESTADOS_DEL_SISTEMA) return null
		return e
	}

	/** La etiqueta de un campo: la vista que lo etiqueta, su descripción o su pista. */
	private fun etiquetaCampo(n: AccessibilityNodeInfo, descripcion: String): String {
		val etiquetadoPor = runCatching { n.labeledBy }.getOrNull()
		val deEtiqueta = etiquetadoPor?.let { it.text?.toString() ?: it.contentDescription?.toString() }?.trim()
		if (!deEtiqueta.isNullOrEmpty()) return deEtiqueta
		if (descripcion.isNotEmpty()) return descripcion
		val pista = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString()?.trim() else null
		return pista.orEmpty()
	}

	private fun pista(n: AccessibilityNodeInfo, editable: Boolean, texto: String): String? {
		if (Build.VERSION.SDK_INT < 26) return null
		val hint = n.hintText?.toString()?.trim()
		// En un campo vacío, la pista es lo que hay que escribir; si ya hay texto, se dice como ayuda
		return if (editable && texto.isBlank()) hint else if (!editable) hint else null
	}

	private fun complementaria(n: AccessibilityNodeInfo): String? {
		if (Build.VERSION.SDK_INT >= 36) n.supplementalDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
		if (Build.VERSION.SDK_INT >= 28) n.tooltipText?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
		return null
	}

	private fun descripcionRol(n: AccessibilityNodeInfo): String? {
		val extras = runCatching { n.extras }.getOrNull() ?: return null
		// La de AndroidX (ViewCompat.setAccessibilityDelegate con roleDescription) y la de Chrome
		val propia = extras.getCharSequence("AccessibilityNodeInfo.roleDescription")?.toString()
		// Chrome pone a todo su nombre de rol («link», «button») en el idioma del móvil: si Tiresias
		// conoce el rol, lo dice él, en el idioma del usuario (las descripciones propias de la web se mantienen)
		if (extras.containsKey("AccessibilityNodeInfo.chromeRole") && Nodos.rol(n) !in setOf(com.xebolax.tiresias.nucleo.Rol.NINGUNO, com.xebolax.tiresias.nucleo.Rol.TEXTO)) {
			val chrome = extras.getCharSequence("AccessibilityNodeInfo.chromeRole")?.toString().orEmpty()
			if (chrome in ROLES_CHROME) return null
		}
		return propia?.takeIf { it.isNotBlank() }
	}

	private fun marcado(n: AccessibilityNodeInfo): Marcado {
		if (!n.isCheckable) return Marcado.NO_MARCABLE
		if (Build.VERSION.SDK_INT >= 36) {
			return when (n.checked) {
				AccessibilityNodeInfo.CHECKED_STATE_TRUE -> Marcado.MARCADO
				AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> Marcado.PARCIAL
				else -> Marcado.NO_MARCADO
			}
		}
		@Suppress("DEPRECATION")
		return if (n.isChecked) Marcado.MARCADO else Marcado.NO_MARCADO
	}

	private fun expansion(n: AccessibilityNodeInfo, acciones: List<AccessibilityAction>): Expansion {
		if (Build.VERSION.SDK_INT >= 36) {
			when (n.expandedState) {
				AccessibilityNodeInfo.EXPANDED_STATE_FULL -> return Expansion.EXPANDIDO
				AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED -> return Expansion.CONTRAIDO
				AccessibilityNodeInfo.EXPANDED_STATE_PARTIAL -> return Expansion.EXPANDIDO_PARCIAL
			}
		}
		val expandir = acciones.any { it.id == AccessibilityNodeInfo.ACTION_EXPAND }
		val contraer = acciones.any { it.id == AccessibilityNodeInfo.ACTION_COLLAPSE }
		return when {
			contraer && !expandir -> Expansion.EXPANDIDO
			expandir && !contraer -> Expansion.CONTRAIDO
			else -> Expansion.NINGUNA
		}
	}

	private fun rango(n: AccessibilityNodeInfo): Rango? {
		val r = n.rangeInfo ?: return null
		val clase = n.className?.toString().orEmpty()
		// Una barra de progreso sin valor (girando): «cargando»
		val indeterminado = r.max <= r.min && clase.endsWith("ProgressBar")
		return Rango(r.min, r.max, r.current, r.type, n.text?.toString()?.takeIf { it.isNotBlank() }, indeterminado)
	}

	private fun posicion(n: AccessibilityNodeInfo): Posicion? {
		val item = n.collectionItemInfo ?: return null
		val coleccion = coleccionDe(n)?.collectionInfo
		val filas = coleccion?.rowCount ?: 0
		val columnas = coleccion?.columnCount ?: 0
		// Listas de una columna: el índice es la fila; en una lista horizontal, la columna
		val (fila, columna) = if (columnas <= 1 && filas <= 1 && item.columnIndex > 0) item.columnIndex + 1 to 0 else item.rowIndex + 1 to item.columnIndex + 1
		val total = if (columnas <= 1 && filas <= 1) maxOf(filas, columnas) else filas
		val encabezadoFila = if (Build.VERSION.SDK_INT >= 33) item.rowTitle else null
		val encabezadoColumna = if (Build.VERSION.SDK_INT >= 33) item.columnTitle else null
		@Suppress("DEPRECATION")
		return Posicion(
			fila = fila,
			columna = if (columnas > 1) columna else 0,
			totalFilas = if (total > 0) total else 0,
			totalColumnas = columnas,
			encabezadoFila = encabezadoFila,
			encabezadoColumna = encabezadoColumna,
			esEncabezado = item.isHeading,
		)
	}

	/** La colección (lista, cuadrícula) que contiene a [n], hasta tres niveles arriba. */
	fun coleccionDe(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		var p = runCatching { n.parent }.getOrNull()
		var nivel = 0
		while (p != null && nivel < 4) {
			if (p.collectionInfo != null) return p
			p = runCatching { p!!.parent }.getOrNull()
			nivel++
		}
		return null
	}

	companion object {
		/** Los roles de Chrome que Tiresias dice con sus propias palabras. */
		val ROLES_CHROME = setOf("link", "button", "checkBox", "radioButton", "image", "list", "listItem", "table", "cell", "textField", "comboBoxGrouping", "comboBoxMenuButton", "slider", "switch", "tab", "tabList", "menuItem", "menu", "heading", "searchBox", "textFieldWithComboBox", "popUpButton", "toggleButton", "rowHeader", "columnHeader", "row", "navigation", "main", "banner", "contentInfo", "form", "region")
		/** Los estados que Android pone solo a los controles estándar (en los idiomas de Tiresias y los más comunes). */
		private val ESTADOS_DEL_SISTEMA = setOf(
			"checked", "not checked", "unchecked", "on", "off", "selected", "not selected", "partially checked",
			"marcado", "no marcado", "marcada", "no marcada", "activado", "desactivado", "activada", "desactivada", "sí", "no",
			"seleccionado", "no seleccionado", "seleccionada", "no seleccionada", "parcialmente marcado",
			"coché", "non coché", "activé", "désactivé", "aktiviert", "deaktiviert", "ativado", "desativado",
		)

		private val ESTANDAR = setOf(
			AccessibilityNodeInfo.ACTION_FOCUS, AccessibilityNodeInfo.ACTION_CLEAR_FOCUS, AccessibilityNodeInfo.ACTION_SELECT,
			AccessibilityNodeInfo.ACTION_CLEAR_SELECTION, AccessibilityNodeInfo.ACTION_CLICK, AccessibilityNodeInfo.ACTION_LONG_CLICK,
			AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
			AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
			AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT, AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT,
			AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_COPY,
			AccessibilityNodeInfo.ACTION_PASTE, AccessibilityNodeInfo.ACTION_CUT, AccessibilityNodeInfo.ACTION_SET_SELECTION,
			AccessibilityNodeInfo.ACTION_EXPAND, AccessibilityNodeInfo.ACTION_COLLAPSE, AccessibilityNodeInfo.ACTION_DISMISS,
			AccessibilityNodeInfo.ACTION_SET_TEXT,
		)

		/** Cuántas acciones propias de la app tiene (no las de navegación ni las básicas). */
		// Los identificadores de acción son constantes que se copian al compilar: en un Android sin ellas
		// simplemente no coinciden con ninguna acción, sin fallo
		@android.annotation.SuppressLint("InlinedApi")
		fun accionesPropias(acciones: List<AccessibilityAction>): Int = acciones.count { a ->
			a.id !in ESTANDAR && a.id != android.R.id.accessibilityActionShowOnScreen &&
				a.id != android.R.id.accessibilityActionScrollToPosition && a.id != android.R.id.accessibilityActionScrollUp &&
				a.id != android.R.id.accessibilityActionScrollDown && a.id != android.R.id.accessibilityActionScrollLeft &&
				a.id != android.R.id.accessibilityActionScrollRight && a.id != android.R.id.accessibilityActionSetProgress &&
				a.id != android.R.id.accessibilityActionContextClick && a.id != android.R.id.accessibilityActionPageUp &&
				a.id != android.R.id.accessibilityActionPageDown && a.id != android.R.id.accessibilityActionPageLeft &&
				a.id != android.R.id.accessibilityActionPageRight && a.label != null
		}
	}
}
