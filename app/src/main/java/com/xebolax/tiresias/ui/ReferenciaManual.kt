package com.xebolax.tiresias.ui

import android.content.Context
import android.text.TextUtils
import android.view.KeyEvent
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Combinacion
import com.xebolax.tiresias.nucleo.Mod
import com.xebolax.tiresias.nucleo.braille.Puntos
import com.xebolax.tiresias.nucleo.braille.TecladoLinea

/**
 * Las tablas de referencia del manual (gestos, teclas y órdenes de la línea
 * braille) salen de las asignaciones de verdad, las que tiene el usuario: el
 * manual nunca dice un gesto que no es. En el HTML del manual van donde están
 * los comentarios `<!-- TIRESIAS:GESTOS -->`, `<!-- TIRESIAS:TECLAS -->` y
 * `<!-- TIRESIAS:BRAILLE -->`.
 */
object ReferenciaManual {

	fun completar(c: Context, html: String): String {
		val t = Idioma.envolver(c)
		return html
			.replace("<!-- TIRESIAS:GESTOS -->", gestos(t))
			.replace("<!-- TIRESIAS:TECLAS -->", teclas(t))
			.replace("<!-- TIRESIAS:BRAILLE -->", braille(t))
	}

	private fun esc(s: String) = TextUtils.htmlEncode(s)

	private fun tabla(titulo: String, columna1: String, columna2: String, filas: List<Pair<String, String>>): String = buildString {
		append("<table><caption>").append(esc(titulo)).append("</caption>")
		append("<thead><tr><th scope=\"col\">").append(esc(columna1)).append("</th><th scope=\"col\">").append(esc(columna2)).append("</th></tr></thead><tbody>")
		for ((a, b) in filas) append("<tr><td>").append(esc(a)).append("</td><td>").append(esc(b)).append("</td></tr>")
		append("</tbody></table>")
	}

	private fun gestos(t: Context): String {
		val mapa = Ajustes(t).mapaGestos.todas
		val filas = mapa.entries.filter { it.value != Accion.NINGUNA }.sortedBy { it.key.ordinal }
			.map { ActividadGestos.nombreGesto(t, it.key) to ActividadGestos.nombreAccion(t, it.value) }
		return tabla(t.getString(R.string.man_tabla_gestos), t.getString(R.string.man_gesto), t.getString(R.string.man_accion), filas)
	}

	private fun teclas(t: Context): String {
		val mapa = Ajustes(t).mapaTeclas.todas
		val filas = mapa.entries.sortedWith(compareBy({ it.value.ordinal }, { it.key.modificadores }))
			.map { nombreCombinacion(t, it.key) to ActividadGestos.nombreAccion(t, it.value) }
		return tabla(t.getString(R.string.man_tabla_teclas), t.getString(R.string.man_teclas), t.getString(R.string.man_accion), filas)
	}

	private fun braille(t: Context): String {
		val acordes = TecladoLinea.ACORDES.entries.sortedBy { it.value.ordinal }.map {
			t.getString(R.string.man_espacio_puntos, Puntos.describir(it.key)) to ActividadGestos.nombreAccion(t, it.value)
		}
		val sueltas = listOf(
			t.getString(R.string.man_br_desplazar) to t.getString(R.string.man_br_desplazar_hace),
			t.getString(R.string.man_br_encaminar) to t.getString(R.string.man_br_encaminar_hace),
			t.getString(R.string.man_br_puntos) to t.getString(R.string.man_br_puntos_hace),
			t.getString(R.string.man_br_espacio) to t.getString(R.string.man_br_espacio_hace),
			t.getString(R.string.man_br_punto7) to t.getString(R.string.man_br_punto7_hace),
			t.getString(R.string.man_br_punto8) to t.getString(R.string.man_br_punto8_hace),
			t.getString(R.string.man_br_cruceta) to t.getString(R.string.man_br_cruceta_hace),
		)
		return tabla(t.getString(R.string.man_tabla_braille), t.getString(R.string.man_teclas), t.getString(R.string.man_accion), sueltas + acordes)
	}

	/** «Tiresias + Ctrl + H»: la tecla Tiresias con el nombre de la tecla elegida en los ajustes. */
	private fun nombreCombinacion(t: Context, c: Combinacion): String {
		val partes = ArrayList<String>()
		if (c.modificadores and Mod.TIRESIAS != 0) partes += t.getString(R.string.man_tecla_tiresias)
		if (c.modificadores and Mod.CTRL != 0) partes += "Ctrl"
		if (c.modificadores and Mod.ALT != 0) partes += "Alt"
		if (c.modificadores and Mod.MAYUS != 0) partes += t.getString(R.string.man_mayus)
		if (c.modificadores and Mod.META != 0) partes += "Meta"
		partes += nombreTecla(t, c.tecla)
		return partes.joinToString(" + ")
	}

	private fun nombreTecla(t: Context, codigo: Int): String = when (codigo) {
		KeyEvent.KEYCODE_DPAD_UP -> t.getString(R.string.man_tecla_arriba)
		KeyEvent.KEYCODE_DPAD_DOWN -> t.getString(R.string.man_tecla_abajo)
		KeyEvent.KEYCODE_DPAD_LEFT -> t.getString(R.string.man_tecla_izquierda)
		KeyEvent.KEYCODE_DPAD_RIGHT -> t.getString(R.string.man_tecla_derecha)
		KeyEvent.KEYCODE_SPACE -> t.getString(R.string.man_tecla_espacio)
		KeyEvent.KEYCODE_DEL -> t.getString(R.string.man_tecla_retroceso)
		KeyEvent.KEYCODE_ENTER -> t.getString(R.string.man_tecla_intro)
		KeyEvent.KEYCODE_ESCAPE -> t.getString(R.string.man_tecla_escape)
		KeyEvent.KEYCODE_SLASH -> "/"
		KeyEvent.KEYCODE_PERIOD -> "."
		KeyEvent.KEYCODE_COMMA -> ","
		else -> KeyEvent.keyCodeToString(codigo).removePrefix("KEYCODE_")
	}
}
