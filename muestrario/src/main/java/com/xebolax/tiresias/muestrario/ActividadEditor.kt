package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Un campo de varias líneas con texto, para el modo edición y la edición por partes de Tiresias. */
class ActividadEditor : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Editor"
		val etiqueta = TextView(this).apply { text = "Mensaje" }
		val campo = EditText(this).apply {
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
			setText(TEXTO)
		}
		campo.id = android.view.View.generateViewId()
		etiqueta.labelFor = campo.id
		setContentView(LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			fitsSystemWindows = true
			setPadding(32, 32, 32, 32)
			addView(etiqueta)
			addView(campo)
		})
	}

	companion object {
		const val TEXTO = "Hola, Ana. ¿Vienes mañana?\nYo sí."
	}
}
