package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Una pantalla sin título (como las de Compose, Flutter o las vistas propias): su aviso de ventana llega sin texto.
 * Tiresias tiene que poner el foco en ella igual (1.0.21).
 */
class ActividadSinTitulo : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = ""
		setContentView(LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			fitsSystemWindows = true
			addView(TextView(this@ActividadSinTitulo).apply { text = "Primero sin título" })
			addView(Button(this@ActividadSinTitulo).apply { text = "Botón sin título" })
		})
	}
}
