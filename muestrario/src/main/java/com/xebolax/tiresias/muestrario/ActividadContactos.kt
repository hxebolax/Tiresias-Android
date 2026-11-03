package com.xebolax.tiresias.muestrario

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Una lista de chats como la de WhatsApp (su estructura, copiada con Tiresias
 * en un OnePlus 6): la lista se mantiene pulsada y tiene nombre; cada fila se
 * pulsa (abre el chat) y lleva dentro un marco pulsable sin texto con la foto
 * del contacto, que también se pulsa (abre el perfil) y se describe como «Foto
 * de Ana», y luego el nombre y el último mensaje.
 */
class ActividadContactos : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Contactos"
		val l = RecyclerView(this).apply {
			layoutManager = LinearLayoutManager(this@ActividadContactos)
			adapter = Filas(NOMBRES)
			contentDescription = "Lista de chats"
			setOnLongClickListener { true }
		}
		setContentView(FrameLayout(this).apply { fitsSystemWindows = true; addView(l) })
	}

	private class Filas(val nombres: List<String>) : RecyclerView.Adapter<Filas.Hueco>() {
		class Hueco(val fila: LinearLayout, val foto: ImageView, val nombre: TextView, val mensaje: TextView) : RecyclerView.ViewHolder(fila)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Hueco {
			val c = parent.context
			val foto = ImageView(c).apply {
				setBackgroundColor(Color.GRAY)
				isClickable = true
				setOnClickListener { }
				layoutParams = LinearLayout.LayoutParams(140, 140)
			}
			val nombre = TextView(c).apply { textSize = 18f }
			val mensaje = TextView(c).apply { textSize = 14f }
			val marco = FrameLayout(c).apply {
				isClickable = true
				setOnClickListener { }
				addView(foto)
			}
			val textos = LinearLayout(c).apply {
				orientation = LinearLayout.VERTICAL
				setPadding(24, 0, 0, 0)
				addView(nombre)
				addView(mensaje)
			}
			val fila = LinearLayout(c).apply {
				orientation = LinearLayout.HORIZONTAL
				gravity = Gravity.CENTER_VERTICAL
				setPadding(24, 16, 24, 16)
				isClickable = true
				setOnClickListener { }
				layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180)
				addView(marco)
				addView(textos)
			}
			return Hueco(fila, foto, nombre, mensaje)
		}

		override fun getItemCount() = nombres.size

		override fun onBindViewHolder(h: Hueco, i: Int) {
			h.foto.contentDescription = "Foto de ${nombres[i]}"
			h.nombre.text = nombres[i]
			h.mensaje.text = "Hola, hoy a las 13.10"
		}
	}

	private companion object {
		val NOMBRES = listOf("Ana Pérez", "Benito Ruiz", "Carmen Díaz", "Daniel Gil", "Elena Sanz", "Fernando Mora", "Gloria Vega", "Hugo León")
	}
}
