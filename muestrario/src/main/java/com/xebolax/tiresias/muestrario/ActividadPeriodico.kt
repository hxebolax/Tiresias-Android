package com.xebolax.tiresias.muestrario

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Un artículo de periódico como se ve en Chrome: arriba una barra con «Inicio» y
 * «Conexión segura», y debajo la página, con anuncios incrustados que se rehacen
 * solos cada poco (uno cambia su contenido y otro es un marco que se vuelve a
 * cargar). Con eso, el foco de Tiresias se iba a la barra o al principio.
 */
class ActividadPeriodico : AppCompatActivity() {
	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Periódico"
		val barra = LinearLayout(this).apply {
			orientation = LinearLayout.HORIZONTAL
			addView(Button(this@ActividadPeriodico).apply { text = "Inicio" })
			addView(Button(this@ActividadPeriodico).apply { text = "Conexión segura" })
		}
		val web = WebView(this).apply {
			settings.javaScriptEnabled = true
			loadDataWithBaseURL(null, PAGINA, "text/html", "utf-8", null)
		}
		setContentView(LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			fitsSystemWindows = true
			addView(barra)
			addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
		})
	}

	private companion object {
		val PAGINA = """
			<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Periódico</title>
			<style>p { margin: 0 0 24px 0; font-size: 18px; }</style></head><body>
			<h1>Titular del día</h1>
			<p>Párrafo uno.</p>
			<p>Párrafo dos.</p>
			<div id="anuncio"><p>Anuncio 0</p></div>
			<p>Párrafo tres.</p>
			<iframe id="marco" style="width:100%;height:80px;border:0" srcdoc="<p>Publicidad 0</p>"></iframe>
			<p>Párrafo cuatro.</p>
			<p>Párrafo cinco.</p>
			<p>Párrafo seis.</p>
			<p>Párrafo siete.</p>
			<p>Párrafo ocho.</p>
			<script>
				var n = 0;
				setInterval(function () {
					n++;
					document.getElementById('anuncio').innerHTML = '<p>Anuncio ' + n + '</p>';
					document.getElementById('marco').srcdoc = '<p>Publicidad ' + n + '</p>';
				}, 700);
			</script>
			</body></html>
		""".trimIndent()
	}
}
