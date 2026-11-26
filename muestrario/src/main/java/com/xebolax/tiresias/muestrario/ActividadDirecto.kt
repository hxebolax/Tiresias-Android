package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * El chat de un directo: una lista a la que llega un mensaje nuevo cada segundo y medio, abajo (como TikTok Live).
 * Al vigilarla, Tiresias tiene que decir solo los mensajes nuevos (Geralt_de_rivia, foro 120).
 */
class ActividadDirecto : AppCompatActivity() {
	private val mensajes = MutableList(5) { i -> "Mensaje del directo ${i + 1}" }
	private var lista: RecyclerView? = null
	private val llegar = object : Runnable {
		override fun run() {
			val l = lista ?: return
			mensajes.add("Mensaje del directo ${mensajes.size + 1}")
			l.adapter?.notifyItemInserted(mensajes.size - 1)
			if (mensajes.size < 40) l.postDelayed(this, 1500)
		}
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Directo"
		val l = RecyclerView(this).apply {
			layoutManager = LinearLayoutManager(this@ActividadDirecto).apply { stackFromEnd = true }
			adapter = Filas(mensajes)
		}
		lista = l
		setContentView(FrameLayout(this).apply { fitsSystemWindows = true; addView(l) })
	}

	override fun onResume() {
		super.onResume()
		lista?.postDelayed(llegar, 3000)
	}

	override fun onPause() {
		lista?.removeCallbacks(llegar)
		super.onPause()
	}

	private class Filas(val textos: List<String>) : RecyclerView.Adapter<Filas.Hueco>() {
		class Hueco(val vista: TextView) : RecyclerView.ViewHolder(vista)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Hueco(TextView(parent.context).apply {
			layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
			textSize = 18f
		})

		override fun onBindViewHolder(holder: Hueco, position: Int) {
			holder.vista.text = textos[position]
		}

		override fun getItemCount() = textos.size
	}
}
