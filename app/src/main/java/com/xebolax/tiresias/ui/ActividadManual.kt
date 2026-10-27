package com.xebolax.tiresias.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.widget.LinearLayout
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma

/**
 * El manual de uso, en HTML accesible (encabezados, listas, enlaces) dentro de
 * la app: `assets/manual-es.html` y `assets/manual-en.html`. Se navega por
 * encabezados con el selector como cualquier página web.
 */
class ActividadManual : ActividadBase() {
	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val p = pantalla(getString(R.string.ui_manual))
		val web = WebView(this).apply {
			settings.javaScriptEnabled = false
			settings.allowFileAccess = false
		}
		val idioma = Idioma.efectivo(this).language
		val archivo = if (runCatching { assets.open("manual-$idioma.html").close() }.isSuccess) "manual-$idioma.html" else "manual-es.html"
		// Las tablas de gestos, teclas y braille, con las asignaciones que tiene el usuario
		val html = assets.open(archivo).bufferedReader().use { it.readText() }
		web.loadDataWithBaseURL("file:///android_asset/", ReferenciaManual.completar(this, html), "text/html", "utf-8", null)
		p.contenedor.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
	}
}
