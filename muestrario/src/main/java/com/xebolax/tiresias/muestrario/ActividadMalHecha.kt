package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Una pantalla mal hecha a propósito, para la reparación automática de
 * Tiresias, hecha a mano como tantas: un `ScrollView` con un `LinearLayout`
 * dentro. Los títulos de sección son textos sin marcar como encabezados
 * (entre opciones que se pulsan), y las filas de abajo no se pulsan y llevan
 * el título y la fecha en dos textos sueltos, así que se leen a trozos.
 */
class ActividadMalHecha : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Mal hecha"
		val dentro = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		fun seccion(t: String) = dentro.addView(TextView(this).apply { id = R.id.mal_seccion; text = t; textSize = 14f; setPadding(32, 40, 32, 8) })
		fun opcion(t: String) = dentro.addView(TextView(this).apply { id = R.id.mal_opcion; text = t; textSize = 18f; setPadding(32, 32, 32, 32); isClickable = true; setOnClickListener { } })
		fun fila(t: String, fecha: String) = dentro.addView(LinearLayout(this).apply {
			id = R.id.mal_fila
			orientation = LinearLayout.VERTICAL
			setPadding(32, 24, 32, 24)
			addView(TextView(this@ActividadMalHecha).apply { id = R.id.mal_titulo; text = t; textSize = 18f })
			addView(TextView(this@ActividadMalHecha).apply { id = R.id.mal_fecha; text = fecha; textSize = 14f })
		})
		seccion("General")
		listOf("Idioma", "Tema", "Avisos").forEach(::opcion)
		seccion("Cuenta")
		listOf("Perfil", "Privacidad", "Salir").forEach(::opcion)
		listOf("octubre", "septiembre", "agosto", "julio", "junio").forEachIndexed { i, mes -> fila("Factura de $mes", "Pagada el día ${i + 1}") }
		setContentView(ScrollView(this).apply { id = R.id.mal_lista; fitsSystemWindows = true; addView(dentro) })
	}
}
