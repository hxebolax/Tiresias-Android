package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton

/**
 * Un aviso como el de Play Protect: una frase con dos enlaces dentro («Más
 * información» e «Instalar de todas formas») que no son elementos aparte, y un
 * botón Cancelar. Al pulsar un enlace, la línea de estado (región viva) dice cuál.
 */
class ActividadAviso : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Aviso de prueba"
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
		val resultado = TextView(this).apply {
			text = "Sin elegir"
			ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
		}
		fun enlace(texto: String) = object : ClickableSpan() {
			override fun onClick(vista: View) {
				resultado.text = "Elegido: $texto"
			}
		}
		val frase = SpannableStringBuilder("Esta app puede ser peligrosa. ")
		for ((i, t) in listOf("Más información", "Instalar de todas formas").withIndex()) {
			if (i > 0) frase.append("  ")
			val inicio = frase.length
			frase.append(t)
			frase.setSpan(enlace(t), inicio, frase.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		raiz.addView(TextView(this).apply {
			text = frase
			movementMethod = LinkMovementMethod.getInstance()
		})
		raiz.addView(MaterialButton(this).apply { text = "Cancelar"; setOnClickListener { finish() } })
		raiz.addView(resultado)
		// Un mensaje largo cortado con «Leer más», como en WhatsApp: al pulsarlo se ve entero
		val largo = TextView(this)
		val corto = SpannableStringBuilder("Mensaje largo: esto es el principio… ")
		val inicioMas = corto.length
		corto.append("Leer más")
		corto.setSpan(object : ClickableSpan() {
			override fun onClick(vista: View) {
				largo.text = "Mensaje largo: esto es el principio y aquí sigue el final del mensaje."
			}
		}, inicioMas, corto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		largo.text = corto
		largo.movementMethod = LinkMovementMethod.getInstance()
		raiz.addView(largo)
		// Un campo con una falta marcada como la marcaría el corrector del teclado
		val escrito = android.text.SpannableString("Hola mundp")
		escrito.setSpan(android.text.style.SuggestionSpan(this, arrayOf("mundo", "mundos"), android.text.style.SuggestionSpan.FLAG_MISSPELLED), 5, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		raiz.addView(android.widget.EditText(this).apply {
			hint = "Mensaje"
			setText(escrito, TextView.BufferType.EDITABLE)
		})
		setContentView(raiz)
	}
}
