package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * «Reenviar a» de WhatsApp, con su estructura (copiada con Tiresias en un
 * OnePlus 6): un ListView que se mantiene pulsado y tiene nombre, con
 * encabezados («Chats frecuentes», «Chats recientes») y contactos que son
 * casillas de opción que no se pulsan por sí mismas (lo hace la lista).
 */
class ActividadReenviar : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Reenviar a"
		val lista = ListView(this).apply {
			adapter = Filas()
			contentDescription = "Contactos"
			setOnItemClickListener { _, _, _, _ -> }
			setOnItemLongClickListener { _, _, _, _ -> true }
		}
		setContentView(FrameLayout(this).apply { fitsSystemWindows = true; addView(lista) })
	}

	/** Un contacto: una casilla de opción con su descripción, sin pulsarse por sí misma. */
	private class Contacto(c: android.content.Context) : LinearLayout(c) {
		override fun getAccessibilityClassName(): CharSequence = "android.widget.RadioButton"

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isCheckable = true
			info.isChecked = false
		}
	}

	private class Filas : BaseAdapter() {
		override fun getCount() = FILAS.size
		override fun getItem(i: Int) = FILAS[i]
		override fun getItemId(i: Int) = i.toLong()
		override fun getViewTypeCount() = 2
		override fun getItemViewType(i: Int) = if (FILAS[i].startsWith("#")) 0 else 1
		override fun isEnabled(i: Int) = !FILAS[i].startsWith("#")

		override fun getView(i: Int, reusada: View?, padre: ViewGroup): View {
			val c = padre.context
			val fila = FILAS[i]
			if (fila.startsWith("#")) {
				val texto = TextView(c).apply {
					text = fila.removePrefix("#")
					textSize = 14f
					if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
				}
				return FrameLayout(c).apply {
					if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
					setPadding(32, 24, 32, 8)
					addView(texto)
				}
			}
			return Contacto(c).apply {
				contentDescription = fila
				minimumHeight = 160
				// (como en WhatsApp, el nombre solo en la descripción: se dibuja, pero no es otro elemento)
				addView(TextView(c).apply { text = fila; textSize = 18f; setPadding(32, 40, 32, 40); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
			}
		}
	}

	private companion object {
		val FILAS = listOf("#Chats frecuentes", "Ana Pérez", "Benito Ruiz", "Carmen Díaz", "#Chats recientes", "Daniel Gil", "Elena Sanz", "Fernando Mora")
	}
}
