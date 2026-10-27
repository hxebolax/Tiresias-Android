package com.xebolax.tiresias.muestrario

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/** Una página web con encabezados, enlaces, una tabla y un formulario (navegación web de Tiresias). */
class ActividadWeb : AppCompatActivity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Página web de prueba"
		val web = WebView(this)
		web.loadDataWithBaseURL(null, PAGINA, "text/html", "utf-8", null)
		setContentView(web)
	}

	companion object {
		private val PAGINA = """
			<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Prueba</title></head><body>
			<h1>Portada</h1>
			<p>Texto de bienvenida.</p>
			<nav><a href="#uno">Primer enlace</a> <a href="#dos">Segundo enlace</a></nav>
			<h2>Noticias</h2>
			<p>Una noticia.</p>
			<h2>Precios</h2>
			<table><tr><th>Producto</th><th>Precio</th></tr><tr><td>Pan</td><td>1 euro</td></tr></table>
			<h3>Contacto</h3>
			<form><label for="n">Nombre</label><input id="n" required><button type="button">Enviar</button></form>
			</body></html>
		""".trimIndent()
	}
}
