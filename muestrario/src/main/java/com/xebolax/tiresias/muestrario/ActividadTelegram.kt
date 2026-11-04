package com.xebolax.tiresias.muestrario

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * La barra de escribir de un chat de Telegram (su estructura, copiada con
 * Tiresias por un usuario en un Samsung con Android 10): emoji, el campo,
 * adjuntar y un contenedor que se mantiene pulsado (su pulsación larga abre
 * «enviar sin sonido» y «programar») y que tapa en parte a adjuntar. Dentro,
 * el micrófono: un marco que no se pulsa ni se mantiene, solo enfocable y con
 * nombre, que graba mientras el dedo está puesto.
 */
class ActividadTelegram : AppCompatActivity() {
	@SuppressLint("ClickableViewAccessibility")
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Telegram"
		val aviso = TextView(this).apply {
			textSize = 18f
			text = "Sin grabar"
		}
		fun icono(nombre: String, izquierda: Int) = ImageView(this).apply {
			setBackgroundColor(Color.LTGRAY)
			contentDescription = nombre
			isClickable = true
			setOnClickListener { aviso.text = "Pulsado $nombre" }
			layoutParams = FrameLayout.LayoutParams(ANCHO_ICONO, ALTO).apply { leftMargin = izquierda }
		}
		val campo = EditText(this).apply {
			hint = "Mensaje"
			layoutParams = FrameLayout.LayoutParams(660, ALTO).apply { leftMargin = 132 }
		}
		val micro = FrameLayout(this).apply {
			setBackgroundColor(Color.DKGRAY)
			isFocusable = true
			contentDescription = "Grabar mensaje de voz"
			setOnTouchListener { _, e ->
				when (e.actionMasked) {
					MotionEvent.ACTION_DOWN -> aviso.text = "Grabando"
					MotionEvent.ACTION_UP -> aviso.text = "Nota de voz enviada"
				}
				true
			}
			layoutParams = FrameLayout.LayoutParams(ANCHO_ICONO, ALTO).apply { leftMargin = 147 }
		}
		val enviar = FrameLayout(this).apply {
			isLongClickable = true
			setOnLongClickListener {
				aviso.text = "Menú de enviar: sin sonido, programar"
				true
			}
			layoutParams = FrameLayout.LayoutParams(263, ALTO).apply { leftMargin = 779 }
			addView(micro)
		}
		val barra = FrameLayout(this).apply {
			isFocusable = true
			layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ALTO, Gravity.BOTTOM)
			addView(icono("Emoji", 8))
			addView(campo)
			addView(icono("Adjuntar", 810))
			addView(enviar)
		}
		setContentView(FrameLayout(this).apply {
			fitsSystemWindows = true
			addView(aviso)
			addView(barra)
		})
	}

	private companion object {
		const val ANCHO_ICONO = 116
		const val ALTO = 116
	}
}
