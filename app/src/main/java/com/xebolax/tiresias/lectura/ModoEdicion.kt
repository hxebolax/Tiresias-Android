package com.xebolax.tiresias.lectura

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.Granularidad
import com.xebolax.tiresias.nucleo.OrdenEdicion
import com.xebolax.tiresias.nucleo.Segmento
import com.xebolax.tiresias.nucleo.Segmentos
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo

/**
 * El modo edición, como el modo carácter a carácter de Jieshuo: en un campo (o
 * en cualquier texto), los gestos de un dedo recorren el texto por caracteres,
 * palabras, frases y párrafos a la vez, sin cambiar el control de lectura, y el
 * mismo movimiento selecciona cuando la selección está empezada. Los gestos son
 * los de [OrdenEdicion.GESTOS]; los demás hacen lo de siempre.
 *
 * En un campo se mueve el cursor de verdad (`ACTION_SET_SELECTION`), y lo
 * seleccionado se queda seleccionado al salir, para copiarlo, cortarlo o
 * escribir encima. En un texto que no se edita (un mensaje), el cursor es de
 * Tiresias y lo seleccionado se copia con «Copiar».
 *
 * Se sale con el mismo gesto, con la acción o al irse a otra app. Nunca se
 * guarda nada del texto: solo dónde está el cursor.
 */
class ModoEdicion(private val lector: Lector) {
	@Volatile var activo = false
		private set
	private var nodo: AccessibilityNodeInfo? = null
	/** Dónde está el cursor (en un campo, el extremo que se mueve de la selección). */
	private var cursor = 0
	/** Dónde empezó la selección, o -1 si no se está seleccionando. */
	private var ancla = -1
	/** Cuándo movió Tiresias el cursor: el aviso de selección que manda la app entonces no se repite. */
	@Volatile private var movidoEn = 0L

	/** La app del texto: al irse a otra, el modo se acaba. */
	val paquete: String? get() = nodo?.packageName?.toString()

	/** Entra o sale del modo. */
	fun alternar(): Boolean = if (activo) salir(decirlo = true) else entrar()

	/** Entra en el modo con lo enfocado (o con el campo en el que se escribe, si el foco no es un texto). */
	fun entrar(): Boolean {
		val n = objetivo()
		if (n == null) {
			lector.sonidos.tocar(Sonido.ERROR)
			lector.decir(R.string.modo_edicion_sin_texto)
			return false
		}
		nodo = n
		activo = true
		ancla = -1
		val texto = texto(n)
		cursor = if (n.isEditable) n.textSelectionEnd.takeIf { it in 0..texto.length } ?: texto.length else 0
		lector.sonidos.tocar(Sonido.EDICION)
		val donde = if (texto.isEmpty()) lector.textos.getString(R.string.modo_edicion_vacio) else lineaYPosicion(texto)
		lector.decir(lector.textos.getString(R.string.modo_edicion_entrar, donde))
		if (lector.ajustes.verbosidad.decirPistas) lector.decir(R.string.modo_edicion_pista, modo = Modo.ENCOLAR)
		return true
	}

	/** Sale del modo. Lo seleccionado en un campo se queda seleccionado. */
	fun salir(decirlo: Boolean): Boolean {
		if (!activo) return false
		val n = nodo
		if (n != null && !n.isEditable && ancla >= 0) guardarTrozo(n)
		activo = false
		nodo = null
		ancla = -1
		if (decirlo) {
			lector.sonidos.tocar(Sonido.EDICION)
			lector.decir(R.string.modo_edicion_salir)
		}
		return true
	}

	/** Si [g] es un gesto del modo: lo que haga se hace en el hilo del lector. */
	fun esDelModo(g: Gesto): Boolean = activo && g in OrdenEdicion.GESTOS

	/** Si el aviso de selección de [n] lo ha provocado el modo al mover el cursor (y ya se ha dicho). */
	fun esPropio(n: AccessibilityNodeInfo): Boolean = activo && n == nodo && SystemClock.uptimeMillis() - movidoEn < AVISO_PROPIO_MS

	/**
	 * Hace lo que toca a [g] en el modo, desde el hilo del lector. Devuelve false si el modo ya no vale (el texto
	 * ha desaparecido): entonces se sale y el gesto hace lo de siempre.
	 */
	fun gesto(g: Gesto, ejecutar: (Accion) -> Boolean): Boolean {
		val orden = OrdenEdicion.GESTOS[g] ?: return false
		val n = nodo ?: return false
		if (!Nodos.vivo(n)) {
			salir(decirlo = true)
			return false
		}
		when (orden) {
			OrdenEdicion.CARACTER_SIGUIENTE -> moverPor(n, Granularidad.CARACTER, true)
			OrdenEdicion.CARACTER_ANTERIOR -> moverPor(n, Granularidad.CARACTER, false)
			OrdenEdicion.PALABRA_SIGUIENTE -> moverPor(n, Granularidad.PALABRA, true)
			OrdenEdicion.PALABRA_ANTERIOR -> moverPor(n, Granularidad.PALABRA, false)
			OrdenEdicion.FRASE_SIGUIENTE -> moverPor(n, Granularidad.FRASE, true)
			OrdenEdicion.FRASE_ANTERIOR -> moverPor(n, Granularidad.FRASE, false)
			OrdenEdicion.LINEA_SIGUIENTE -> moverPorBloques(n, lineas = true, adelante = true)
			OrdenEdicion.LINEA_ANTERIOR -> moverPorBloques(n, lineas = true, adelante = false)
			OrdenEdicion.PARRAFO_SIGUIENTE -> moverPorBloques(n, lineas = false, adelante = true)
			OrdenEdicion.PARRAFO_ANTERIOR -> moverPorBloques(n, lineas = false, adelante = false)
			OrdenEdicion.PRINCIPIO_LINEA -> extremoDeLinea(n, final = false)
			OrdenEdicion.FINAL_LINEA -> extremoDeLinea(n, final = true)
			OrdenEdicion.PRINCIPIO -> extremo(n, final = false)
			OrdenEdicion.FINAL -> extremo(n, final = true)
			OrdenEdicion.SELECCIONAR -> alternarSeleccion(n)
			OrdenEdicion.LEER -> leer(n)
			OrdenEdicion.MENU_EDICION -> ejecutar(Accion.MENU_EDICION)
			OrdenEdicion.DESHACER -> ejecutar(Accion.DESHACER)
			OrdenEdicion.REHACER -> ejecutar(Accion.REHACER)
			OrdenEdicion.SALIR -> salir(decirlo = true)
		}
		return true
	}

	// --- Movimientos ------------------------------------------------------------------------------

	private fun moverPor(n: AccessibilityNodeInfo, g: Granularidad, adelante: Boolean) {
		val texto = sincronizar(n) ?: return
		val s = if (adelante) Segmentos.siguiente(texto, cursor, g) else Segmentos.anterior(texto, cursor, g)
		if (s == null) {
			borde(adelante)
			return
		}
		ir(n, texto, if (adelante) s.fin else s.inicio, s, g)
	}

	/** Por líneas o párrafos: el cursor va al principio del siguiente (o del anterior) y se dice entero. */
	private fun moverPorBloques(n: AccessibilityNodeInfo, lineas: Boolean, adelante: Boolean) {
		val texto = sincronizar(n) ?: return
		val bloques = if (lineas) Segmentos.lineas(texto) else Segmentos.parrafos(texto)
		val actual = bloques.indexOfLast { it.inicio <= cursor }
		val destino = if (adelante) (if (actual < 0) bloques.firstOrNull() else bloques.getOrNull(actual + 1)) else bloques.getOrNull(actual - 1)
		if (destino == null) {
			borde(adelante)
			return
		}
		ir(n, texto, destino.inicio, destino, if (lineas) Granularidad.LINEA else Granularidad.PARRAFO)
	}

	private fun extremoDeLinea(n: AccessibilityNodeInfo, final: Boolean) {
		val texto = sincronizar(n) ?: return
		val linea = Segmentos.lineas(texto).lastOrNull { it.inicio <= cursor } ?: return
		ir(n, texto, if (final) linea.fin else linea.inicio, null, null)
		if (ancla < 0) lector.decir(if (final) R.string.modo_edicion_final_linea else R.string.modo_edicion_principio_linea)
	}

	private fun extremo(n: AccessibilityNodeInfo, final: Boolean) {
		val texto = sincronizar(n) ?: return
		ir(n, texto, if (final) texto.length else 0, null, null)
		if (ancla < 0) lector.decir(if (final) R.string.edicion_final else R.string.edicion_principio)
	}

	/**
	 * Lleva el cursor a [nuevo] y dice lo recorrido: el trozo [s] (con fonética en los caracteres) o, si se está
	 * seleccionando, solo lo que se añade a la selección o se quita de ella.
	 */
	private fun ir(n: AccessibilityNodeInfo, texto: String, nuevo: Int, s: Segmento?, g: Granularidad?) {
		val antes = cursor
		if (!ponerCursor(n, nuevo)) {
			lector.sonidos.tocar(Sonido.ERROR)
			lector.decir(R.string.modo_edicion_no_mueve)
			return
		}
		if (ancla >= 0) {
			val a = minOf(antes, nuevo)
			val b = maxOf(antes, nuevo)
			if (a == b) return
			val trozo = decible(texto.substring(a, b))
			val crece = Math.abs(nuevo - ancla) > Math.abs(antes - ancla)
			lector.decir(lector.textos.getString(if (crece) R.string.modo_edicion_seleccionado else R.string.modo_edicion_deseleccionado, trozo), Modo.INTERRUMPIR, Tipo.NAVEGACION)
			return
		}
		if (s == null || g == null) return
		if (s.texto.isBlank()) lector.decir(R.string.modo_edicion_linea_vacia)
		else lector.edicion.decirSegmento(s.texto, g)
	}

	/** Mueve el cursor (y, si se selecciona, el extremo de la selección). En un texto que no se edita, solo el de Tiresias. */
	private fun ponerCursor(n: AccessibilityNodeInfo, nuevo: Int): Boolean {
		if (n.isEditable) {
			// Android solo admite la selección de menor a mayor (seleccionando hacia atrás, el extremo que se mueve es
			// el de Tiresias: [cursor]) y dice que no si ya estaba así: eso no es un fallo
			val ancho = if (ancla >= 0) ancla else nuevo
			val a = minOf(ancho, nuevo)
			val b = maxOf(ancho, nuevo)
			if (minOf(n.textSelectionStart, n.textSelectionEnd) != a || maxOf(n.textSelectionStart, n.textSelectionEnd) != b) {
				val args = Bundle().apply {
					putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, a)
					putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, b)
				}
				movidoEn = SystemClock.uptimeMillis()
				if (!runCatching { n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args) }.getOrDefault(false)) return false
			}
		}
		cursor = nuevo
		return true
	}

	private fun borde(adelante: Boolean) {
		lector.sonidos.tocar(Sonido.BORDE)
		lector.decir(if (adelante) R.string.edicion_final else R.string.edicion_principio)
	}

	// --- Selección y lectura ----------------------------------------------------------------------

	private fun alternarSeleccion(n: AccessibilityNodeInfo) {
		val texto = sincronizar(n) ?: return
		lector.sonidos.tocar(Sonido.SELECTOR)
		if (ancla < 0) {
			ancla = cursor
			lector.decir(R.string.modo_edicion_seleccion_empieza)
			return
		}
		val cuantos = Math.abs(cursor - ancla)
		if (!n.isEditable) guardarTrozo(n, texto)
		ancla = -1
		lector.decir(lector.textos.resources.getQuantityString(R.plurals.modo_edicion_seleccion_acaba, cuantos, cuantos))
	}

	/** Lo seleccionado en un texto que no se edita pasa a los trozos marcados, que se copian con «Copiar». */
	private fun guardarTrozo(n: AccessibilityNodeInfo, texto: String = texto(n)) {
		val a = minOf(ancla, cursor).coerceIn(0, texto.length)
		val b = maxOf(ancla, cursor).coerceIn(0, texto.length)
		if (b > a) lector.edicion.apuntarTrozo(n, texto, a, b)
	}

	/** Lee lo seleccionado o, si no se selecciona, la línea en la que está el cursor y dónde está. */
	private fun leer(n: AccessibilityNodeInfo) {
		val texto = sincronizar(n) ?: return
		if (ancla >= 0 && ancla != cursor) {
			val a = minOf(ancla, cursor)
			val b = maxOf(ancla, cursor)
			lector.decir(lector.textos.getString(R.string.modo_edicion_seleccionado, decible(texto.substring(a, b))))
			return
		}
		lector.decir(lineaYPosicion(texto))
	}

	private fun lineaYPosicion(texto: String): String {
		val lineas = Segmentos.lineas(texto)
		val i = lineas.indexOfLast { it.inicio <= cursor }.coerceAtLeast(0)
		val linea = lineas.getOrNull(i)?.texto?.takeIf { it.isNotBlank() } ?: lector.textos.getString(R.string.modo_edicion_linea_vacia)
		val posicion = lector.textos.getString(R.string.modo_edicion_posicion, cursor + 1, texto.length + 1, i + 1, lineas.size)
		return "$linea. $posicion"
	}

	/** Un trozo dicho como tal: un solo carácter, por su nombre («espacio», «coma»). */
	private fun decible(t: String): String = if (t.codePointCount(0, t.length) == 1) lector.edicionCaracter(t) else t

	// --- El texto ---------------------------------------------------------------------------------

	/**
	 * El texto de ahora (la app o quien escribe pueden haberlo cambiado): el cursor se ajusta a él y, en un campo
	 * sin seleccionar, se toma el de la app (se ha podido mover tecleando). Vacío: se dice y no hay nada que hacer.
	 */
	private fun sincronizar(n: AccessibilityNodeInfo): String? {
		runCatching { n.refresh() }
		val texto = texto(n)
		if (texto.isEmpty()) {
			lector.sonidos.tocar(Sonido.BORDE)
			lector.decir(R.string.modo_edicion_vacio)
			return null
		}
		if (n.isEditable && ancla < 0) n.textSelectionEnd.takeIf { it in 0..texto.length }?.let { cursor = it }
		cursor = cursor.coerceIn(0, texto.length)
		if (ancla > texto.length) ancla = texto.length
		return texto
	}

	/** El texto de [n]: en un campo, lo escrito (nunca la pista); fuera, lo que se dice de él. */
	private fun texto(n: AccessibilityNodeInfo): String {
		if (n.isEditable) {
			if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) return ""
			return n.text?.toString().orEmpty()
		}
		return lector.elemento(n).nombre
	}

	/** Lo enfocado si es un campo o tiene texto; si no, el campo en el que se está escribiendo. */
	private fun objetivo(): AccessibilityNodeInfo? {
		val f = lector.foco?.takeIf { Nodos.vivo(it) }
		if (f != null && f.isEditable) return f
		val entrada = runCatching { lector.raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()?.takeIf { it.isEditable }
		if (entrada != null && (f == null || !Nodos.tieneTexto(f))) return entrada
		return f?.takeIf { lector.elemento(it).nombre.isNotBlank() } ?: entrada
	}

	private companion object {
		/** Lo que puede tardar la app en avisar de la selección que Tiresias ha puesto. */
		const val AVISO_PROPIO_MS = 700L
	}
}
