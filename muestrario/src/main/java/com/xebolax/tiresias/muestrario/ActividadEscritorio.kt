package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

/**
 * Un escritorio como los de Launcher3 antiguo y sus derivados (EMUI): la página y
 * el dock se pueden pulsar y mantener pulsados, sin texto propio, y cada icono es
 * un TextView que también se pulsa. Con el extra `reloj`, la página lleva además
 * un texto suelto, como un widget del reloj.
 */
class ActividadEscritorio : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Inicio"
		val abierta = TextView(this).apply {
			text = "Ninguna abierta"
			ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
		}
		fun icono(nombre: String) = TextView(this).apply {
			text = nombre
			setPadding(24, 48, 24, 48)
			isClickable = true
			isLongClickable = true
			isFocusable = true
			setOnClickListener { abierta.text = "Abierta $nombre" }
			setOnLongClickListener { true }
		}
		fun deEscritorio(v: View) {
			v.isClickable = true
			v.isLongClickable = true
			v.setOnClickListener { }
			v.setOnLongClickListener { true }
			// Los márgenes entre los iconos: se pueden tocar sin dar en ninguno
			v.setPadding(48, 48, 48, 48)
		}
		val pagina = GridLayout(this).apply {
			columnCount = 2
			deEscritorio(this)
			if (intent.getBooleanExtra("reloj", false)) addView(TextView(context).apply { text = "10:30" })
			for (n in listOf("Telegram", "Correo", "Fotos", "Mapas")) addView(icono(n))
		}
		val dock = LinearLayout(this).apply {
			deEscritorio(this)
			for (n in listOf("Teléfono", "Mensajes", "Navegador")) addView(icono(n))
		}
		val raiz = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			fitsSystemWindows = true
			addView(pagina, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
			addView(abierta)
			addView(dock)
		}
		setContentView(raiz)
	}
}
