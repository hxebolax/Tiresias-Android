package com.xebolax.tiresias.muestrario

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Una lista de chats como la de Telegram: cada fila es una sola vista dibujada a
 * mano, sin hijos, que no tiene descripción hasta que recibe el foco (la pone al
 * rellenar su evento). La mitad acaba en «Mensaje de voz», como un chat cuyo
 * último mensaje es una nota de voz. Con el extra `rafaga`, cada fila que se ve
 * avisa de cambios sin parar, como una lista con avatares animados.
 */
class ActividadChats : AppCompatActivity() {
	private var lista: RecyclerView? = null
	private var rafaga = false

	private val avisar = object : Runnable {
		override fun run() {
			val l = lista ?: return
			for (i in 0 until l.childCount) l.getChildAt(i).sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
			l.postDelayed(this, 16)
		}
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Chats"
		rafaga = intent.getBooleanExtra("rafaga", false)
		val l = RecyclerView(this).apply {
			layoutManager = LinearLayoutManager(this@ActividadChats)
			adapter = Filas(List(80) { i -> "Chat ${i + 1}, ${if (i % 2 == 0) "Mensaje de voz" else "Hola"}" })
		}
		lista = l
		setContentView(FrameLayout(this).apply { fitsSystemWindows = true; addView(l) })
	}

	override fun onResume() {
		super.onResume()
		if (rafaga) lista?.post(avisar)
	}

	override fun onPause() {
		lista?.removeCallbacks(avisar)
		super.onPause()
	}

	private class Fila(c: Context) : View(c) {
		var resumen = ""
		private val pincel = Paint().apply { textSize = 40f }

		override fun onDraw(c: Canvas) = c.drawText(resumen, 24f, 60f, pincel)

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.addAction(AccessibilityNodeInfo.ACTION_CLICK)
			info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
		}

		override fun onPopulateAccessibilityEvent(e: AccessibilityEvent) {
			super.onPopulateAccessibilityEvent(e)
			contentDescription = resumen
		}
	}

	private class Filas(val textos: List<String>) : RecyclerView.Adapter<Filas.Hueco>() {
		class Hueco(val vista: Fila) : RecyclerView.ViewHolder(vista)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Hueco(Fila(parent.context).apply {
			layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160)
			isClickable = true
			isFocusable = true
			setOnClickListener { }
		})

		override fun onBindViewHolder(holder: Hueco, position: Int) {
			holder.vista.resumen = textos[position]
			holder.vista.contentDescription = null
		}

		override fun getItemCount() = textos.size
	}
}
