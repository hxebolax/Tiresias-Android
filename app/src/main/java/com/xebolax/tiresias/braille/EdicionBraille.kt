package com.xebolax.tiresias.braille

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Registro

/**
 * Explorar y corregir lo escrito sin salir del teclado braille, como el modo
 * edición de Tiresias: el cursor se mueve por caracteres, palabras o líneas
 * (tres dedos a los lados, de fábrica) diciendo lo que pasa; tres dedos hacia
 * abajo cambian la unidad; y desde el menú Edición se selecciona (moviendo el
 * cursor), se selecciona todo, se copia, se corta, se pega, se deshace y se
 * rehace, se va al principio o al final y se lee lo seleccionado.
 *
 * Todo pasa por la conexión con el campo de texto del teclado ([InputConnection]):
 * vale en cualquier app y no hace falta salir del teclado para nada.
 */
class EdicionBraille(private val teclado: TecladoBraille) {
	enum class Unidad(val nombre: Int) {
		CARACTER(R.string.braille_unidad_caracter),
		PALABRA(R.string.braille_unidad_palabra),
		LINEA(R.string.braille_unidad_linea),
	}

	var unidad = Unidad.CARACTER
		private set

	/** Mientras se selecciona: dónde empezó la selección (en el texto del campo). */
	private var ancla: Int? = null

	val seleccionando: Boolean get() = ancla != null

	/** Al empezar otro campo, se empieza de cero. */
	fun reiniciar() {
		ancla = null
		unidad = Unidad.CARACTER
	}

	private val t get() = teclado.textos()

	/** El texto del campo, dónde empieza (si el campo solo da un trozo) y la selección. */
	private class Campo(val texto: String, val base: Int, val inicio: Int, val fin: Int)

	private fun campo(ic: InputConnection): Campo? {
		val et = try {
			ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAXIMO_TEXTO }, 0)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo leer el texto del campo", e)
			null
		} ?: return null
		val texto = et.text?.toString().orEmpty()
		val inicio = et.selectionStart.coerceIn(0, texto.length)
		val fin = et.selectionEnd.coerceIn(0, texto.length)
		return Campo(texto, et.startOffset.coerceAtLeast(0), minOf(inicio, fin), maxOf(inicio, fin))
	}

	fun cambiarUnidad() {
		unidad = Unidad.entries[(unidad.ordinal + 1) % Unidad.entries.size]
		teclado.anunciar(t.getString(unidad.nombre))
	}

	/** Mueve el cursor una unidad y dice lo que ha pasado por encima (o lo que se ha seleccionado). */
	fun mover(adelante: Boolean) {
		val ic = teclado.currentInputConnection ?: return
		val c = campo(ic)
		if (c == null) {
			teclado.anunciar(t.getString(R.string.braille_campo_vacio))
			return
		}
		val texto = c.texto
		val a = ancla
		// El cursor: con selección, su extremo que se mueve; sin ella, el lado hacia el que se va
		val pos = when {
			a != null -> if (c.inicio == a) c.fin else c.inicio
			adelante -> c.fin
			else -> c.inicio
		}
		if ((adelante && pos >= texto.length) || (!adelante && pos <= 0)) {
			teclado.anunciar(t.getString(if (adelante) R.string.braille_final else R.string.braille_principio))
			return
		}
		val nuevo: Int
		val pasado: String
		when (unidad) {
			Unidad.CARACTER -> {
				nuevo = if (adelante) pos + 1 else pos - 1
				pasado = texto.substring(minOf(pos, nuevo), maxOf(pos, nuevo))
			}
			Unidad.PALABRA -> {
				nuevo = if (adelante) finDePalabra(texto, pos) else inicioDePalabra(texto, pos)
				pasado = texto.substring(minOf(pos, nuevo), maxOf(pos, nuevo)).trim()
			}
			Unidad.LINEA -> {
				nuevo = if (adelante) {
					val salto = texto.indexOf('\n', pos)
					if (salto < 0) texto.length else salto + 1
				} else {
					if (pos - 2 < 0) 0 else texto.lastIndexOf('\n', pos - 2) + 1
				}
				pasado = texto.substring(minOf(pos, nuevo), maxOf(pos, nuevo)).trim('\n')
			}
		}
		if (a != null) {
			ic.setSelection(c.base + a, c.base + nuevo)
			teclado.anunciar(t.getString(R.string.braille_seleccionado, dicho(pasado)))
		} else {
			ic.setSelection(c.base + nuevo, c.base + nuevo)
			if (unidad == Unidad.CARACTER) teclado.decirCaracter(pasado) else teclado.anunciar(dicho(pasado))
		}
	}

	/** Lo que se dice de un trozo: en una contraseña, nada de su contenido; vacío o un espacio, por su nombre. */
	private fun dicho(trozo: String): String = when {
		teclado.ocultarContrasena() -> t.getString(R.string.edicion_punto)
		trozo.isBlank() -> teclado.nombreDe(trozo.take(1).ifEmpty { " " })
		else -> trozo
	}

	private fun finDePalabra(s: String, desde: Int): Int {
		var i = desde
		while (i < s.length && !s[i].isLetterOrDigit()) i++
		while (i < s.length && s[i].isLetterOrDigit()) i++
		return i
	}

	private fun inicioDePalabra(s: String, desde: Int): Int {
		var i = desde
		while (i > 0 && !s[i - 1].isLetterOrDigit()) i--
		while (i > 0 && s[i - 1].isLetterOrDigit()) i--
		return i
	}

	/** Las acciones del menú Edición (y las que se pongan en un gesto). */
	fun hacer(a: AccionTeclado) {
		val ic = teclado.currentInputConnection ?: return
		when (a) {
			AccionTeclado.SELECCIONAR -> alternarSeleccion(ic)
			AccionTeclado.SELECCIONAR_TODO -> {
				ic.performContextMenuAction(android.R.id.selectAll)
				ancla = null
				teclado.anunciar(t.getString(R.string.braille_todo_seleccionado))
			}
			AccionTeclado.COPIAR, AccionTeclado.CORTAR -> {
				val sel = ic.getSelectedText(0)?.toString().orEmpty()
				if (sel.isEmpty()) {
					teclado.anunciar(t.getString(R.string.braille_nada_seleccionado))
					return
				}
				val cortar = a == AccionTeclado.CORTAR
				ic.performContextMenuAction(if (cortar) android.R.id.cut else android.R.id.copy)
				ancla = null
				val corto = if (teclado.ocultarContrasena()) "" else resumen(sel)
				teclado.anunciar(t.getString(if (cortar) R.string.braille_cortado else R.string.braille_copiado, corto))
			}
			AccionTeclado.PEGAR -> {
				ic.performContextMenuAction(android.R.id.paste)
				ancla = null
				teclado.anunciar(t.getString(R.string.braille_pegado_portapapeles))
			}
			AccionTeclado.DESHACER -> {
				tecla(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
				teclado.anunciar(t.getString(R.string.braille_deshecho))
			}
			AccionTeclado.REHACER -> {
				tecla(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
				teclado.anunciar(t.getString(R.string.braille_rehecho))
			}
			AccionTeclado.IR_PRINCIPIO, AccionTeclado.IR_FINAL -> {
				val c = campo(ic) ?: return
				val final = a == AccionTeclado.IR_FINAL
				val destino = if (final) c.texto.length else 0
				val desde = ancla
				if (desde != null) ic.setSelection(c.base + desde, c.base + destino) else ic.setSelection(c.base + destino, c.base + destino)
				teclado.anunciar(t.getString(if (final) R.string.braille_final else R.string.braille_principio))
			}
			AccionTeclado.LEER_SELECCION -> {
				val sel = ic.getSelectedText(0)?.toString().orEmpty()
				teclado.anunciar(when {
					sel.isEmpty() -> t.getString(R.string.braille_nada_seleccionado)
					teclado.ocultarContrasena() -> t.getString(R.string.edicion_punto)
					else -> sel
				})
			}
			else -> Unit
		}
	}

	private fun alternarSeleccion(ic: InputConnection) {
		val c = campo(ic) ?: return
		val a = ancla
		if (a == null) {
			// Desde el cursor: lo que se mueva a partir de ahora queda seleccionado
			ancla = if (c.inicio != c.fin) c.inicio else c.fin
			teclado.anunciar(t.getString(R.string.braille_seleccion_empieza))
			return
		}
		ancla = null
		val n = c.fin - c.inicio
		teclado.anunciar(t.resources.getQuantityString(R.plurals.braille_seleccion_termina, n, n))
	}

	private fun tecla(ic: InputConnection, codigo: Int, meta: Int) {
		val ahora = SystemClock.uptimeMillis()
		ic.sendKeyEvent(KeyEvent(ahora, ahora, KeyEvent.ACTION_DOWN, codigo, 0, meta))
		ic.sendKeyEvent(KeyEvent(ahora, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, codigo, 0, meta))
	}

	private fun resumen(s: String): String = s.replace('\n', ' ').let { if (it.length > 80) it.take(80) + "…" else it }

	private companion object {
		const val ETIQUETA = "EdicionBraille"
		/** Lo que se pide del texto del campo como mucho (un mensaje largo cabe de sobra). */
		const val MAXIMO_TEXTO = 200_000
	}
}
