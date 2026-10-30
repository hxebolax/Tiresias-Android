package com.xebolax.tiresias.lectura

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.nucleo.CambioTexto
import com.xebolax.tiresias.nucleo.Edicion
import com.xebolax.tiresias.nucleo.Granularidad
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo

/**
 * Lo que dice Tiresias al escribir y al moverse por un campo de texto.
 *
 * - **Eco**: por caracteres, por palabras, los dos o nada (Ajustes). Lo borrado
 *   se dice («borrado a»); la sustitución de una palabra (autocorrección) dice
 *   la palabra nueva.
 * - **Contraseñas**: el carácter solo si el sistema tiene activado «mostrar
 *   contraseñas»; si no, «punto».
 * - **Cursor**: al moverlo con el teclado o con el gesto, dice lo que se ha
 *   saltado; con selección, lo seleccionado.
 *
 * Calcula el cambio comparando el texto anterior con el nuevo, porque muchos
 * teclados y apps mandan índices equivocados.
 */
/** Letras fonéticas al moverse por caracteres (Ajustes, Lectura). */
const val FONETICAS_NO = "no"
const val FONETICAS_DESPUES = "despues"
const val FONETICAS_EN_VEZ = "en_vez"
/** La letra y, si no se sigue en un segundo, su palabra (NVDA 2022.3). */
const val FONETICAS_PAUSA = "pausa"

class EdicionTexto(private val lector: Lector) {
	private var ultimoCambio = 0L
	private var textoAnterior = ""
	private var nodoAnterior: AccessibilityNodeInfo? = null
	private var seleccionAnterior = -1

	fun alCambiarTexto(e: AccessibilityEvent) {
		val nodo = e.source
		val antes = if (nodo != null && nodo == nodoAnterior) textoAnterior else e.beforeText?.toString().orEmpty()
		val despues = e.text.joinToString("") { it.toString() }
		nodoAnterior = nodo
		textoAnterior = despues
		ultimoCambio = SystemClock.uptimeMillis()
		val contrasena = e.isPassword
		// Un teclado que habla solo (un teclado braille con voz): su eco basta, Tiresias no lo repite (Backtalk 310)
		if (tecladoHablaSolo()) return
		val eco = lector.perfiles.ecoTeclado() ?: lector.ajustes.ecoTeclado
		when (val c = Edicion.cambio(antes, despues)) {
			is CambioTexto.Escrito -> {
				if (eco == "nada") return
				val t = c.texto
				lector.sonidos.tocar(Sonido.TECLA)
				if (contrasena && !mostrarContrasenas()) {
					if (eco != "palabras") lector.decir(lector.textos.getString(R.string.edicion_punto), Modo.INTERRUMPIR, Tipo.ESCRITURA)
					return
				}
				// Lo pegado o lo dictado (varios caracteres) se dice entero
				if (t.length > 1) {
					lector.decir(t, Modo.INTERRUMPIR, Tipo.ESCRITURA)
					return
				}
				val palabra = Edicion.palabraTerminada(despues, (e.fromIndex + e.addedCount).coerceIn(0, despues.length)).takeIf { eco == "palabras" || eco == "ambos" }
				when {
					eco == "palabras" && palabra != null -> lector.decir(palabra, Modo.INTERRUMPIR, Tipo.ESCRITURA)
					eco == "palabras" -> Unit
					eco == "ambos" && palabra != null -> lector.voz.decir(Enunciado(t + " " + palabra, Modo.INTERRUMPIR, Tipo.ESCRITURA, deletrear = false))
					else -> lector.voz.decir(Enunciado(t, Modo.INTERRUMPIR, Tipo.ESCRITURA, deletrear = true, tono = tonoMayuscula(t)))
				}
			}
			is CambioTexto.Borrado -> {
				lector.sonidos.tocar(Sonido.TECLA)
				val borrado = if (contrasena && !mostrarContrasenas()) lector.textos.getString(R.string.edicion_punto) else c.texto
				val dicho = if (borrado.length == 1) lector.edicionCaracter(borrado) else borrado
				lector.decir(lector.textos.getString(R.string.edicion_borrado, dicho), Modo.INTERRUMPIR, Tipo.ESCRITURA)
			}
			// Una autocorrección o una palabra sugerida: como lo escrito, nada con el eco apagado
			is CambioTexto.Sustituido -> if (!contrasena && eco != "nada") lector.decir(c.despues, Modo.INTERRUMPIR, Tipo.ESCRITURA)
			CambioTexto.SinCambios -> Unit
		}
	}

	fun alCambiarSeleccion(e: AccessibilityEvent) {
		val nodo = e.source ?: return
		val texto = e.text.joinToString("") { it.toString() }
		val inicio = e.fromIndex
		val fin = e.toIndex
		val anterior = if (nodo == nodoAnterior) seleccionAnterior else -1
		nodoAnterior = nodo
		textoAnterior = texto
		seleccionAnterior = fin
		// El cursor se mueve solo al escribir: eso ya lo dijo el eco
		if (SystemClock.uptimeMillis() - ultimoCambio < 150) return
		if (inicio < 0 || fin < 0 || texto.isEmpty()) return
		if (inicio != fin) {
			val a = minOf(inicio, fin).coerceIn(0, texto.length)
			val b = maxOf(inicio, fin).coerceIn(0, texto.length)
			lector.decir(lector.textos.getString(R.string.edicion_seleccionado, texto.substring(a, b)), Modo.INTERRUMPIR, Tipo.NAVEGACION)
			return
		}
		if (anterior < 0 || anterior == fin) return
		val a = minOf(anterior, fin).coerceIn(0, texto.length)
		val b = maxOf(anterior, fin).coerceIn(0, texto.length)
		val saltado = texto.substring(a, b)
		when {
			fin == 0 || fin == texto.length -> {
				lector.sonidos.tocar(Sonido.EDICION)
				if (saltado.length == 1) lector.decir(lector.edicionCaracter(saltado), Modo.INTERRUMPIR, Tipo.NAVEGACION)
			}
			saltado.length == 1 -> lector.decir(lector.edicionCaracter(if (fin > anterior) texto.substring(fin - 1, fin) else texto.substring(fin, fin + 1)), Modo.INTERRUMPIR, Tipo.NAVEGACION)
			else -> lector.decir(saltado, Modo.INTERRUMPIR, Tipo.NAVEGACION)
		}
	}

	/** La app recorrió el texto por granularidad (lo pidió el navegador): se dice lo recorrido. */
	fun alRecorrer(e: AccessibilityEvent) {
		val texto = e.text.joinToString("") { it.toString() }
		val a = minOf(e.fromIndex, e.toIndex).coerceIn(0, texto.length)
		val b = maxOf(e.fromIndex, e.toIndex).coerceIn(0, texto.length)
		if (a >= b) return
		val g = when (e.movementGranularity) {
			AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> Granularidad.CARACTER
			AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD -> Granularidad.PALABRA
			AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE -> Granularidad.LINEA
			AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH -> Granularidad.PARRAFO
			else -> Granularidad.PAGINA
		}
		decirSegmento(texto.substring(a, b), g)
		e.source?.let { n -> if (modoSeleccion && !n.isEditable) apuntarTrozo(n, texto, a, b) }
	}

	// --- Copia por trozos de un texto que no se puede editar (Backtalk 188 y 204) -----------

	/** Al moverse por el texto, se selecciona (en un campo) o se marca un trozo para copiar (fuera de él). */
	var modoSeleccion = false
		private set

	private var trozoNodo: AccessibilityNodeInfo? = null
	private var trozoTexto = ""
	private var trozoDesde = -1
	private var trozoHasta = -1
	private val trozos = ArrayList<String>()

	/** Activa o desactiva el modo selección; al desactivarlo fuera de un campo, el trozo marcado se guarda. */
	fun alternarSeleccion() {
		modoSeleccion = !modoSeleccion
		if (modoSeleccion) {
			olvidarTrozo()
			lector.decir(R.string.edicion_modo_seleccion_si)
			return
		}
		val guardado = cerrarTrozo()
		if (guardado != null) lector.decir(lector.textos.getString(R.string.edicion_trozo_guardado, guardado))
		else lector.decir(R.string.edicion_modo_seleccion_no)
	}

	/** Lo recorrido de [texto] (de [a] a [b]) en [n] se suma al trozo que se está marcando. */
	fun apuntarTrozo(n: AccessibilityNodeInfo, texto: String, a: Int, b: Int) {
		if (trozoNodo != n || trozoTexto != texto) {
			// Otro elemento: lo marcado en el anterior se guarda y se empieza otro trozo
			cerrarTrozo()
			trozoNodo = n
			trozoTexto = texto
			trozoDesde = a
			trozoHasta = b
			return
		}
		trozoDesde = minOf(trozoDesde, a)
		trozoHasta = maxOf(trozoHasta, b)
	}

	/** Guarda el trozo marcado (si lo hay) y lo devuelve. */
	private fun cerrarTrozo(): String? {
		val t = trozoTexto
		val desde = trozoDesde.coerceIn(0, t.length)
		val hasta = trozoHasta.coerceIn(0, t.length)
		olvidarTrozo()
		if (hasta <= desde) return null
		return t.substring(desde, hasta).trim().takeIf { it.isNotEmpty() }?.also { trozos += it }
	}

	private fun olvidarTrozo() {
		trozoNodo = null
		trozoTexto = ""
		trozoDesde = -1
		trozoHasta = -1
	}

	/** Cuenta las letras dichas: la palabra fonética de una se descarta si ya se ha ido a otra. */
	@Volatile private var turnoLetra = 0

	/** Si el teclado elegido es de los que hablan solos (Ajustes, Teclado). */
	fun tecladoHablaSolo(): Boolean {
		val elegidos = lector.ajustes.tecladosQueHablan
		if (elegidos.isEmpty()) return false
		val actual = runCatching { android.provider.Settings.Secure.getString(lector.servicio.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
			?.substringBefore('/') ?: return false
		return actual in elegidos
	}

	fun decirSegmento(s: String, g: Granularidad) {
		if (g != Granularidad.CARACTER) turnoLetra++
		if (g == Granularidad.CARACTER) {
			val modo = lector.ajustes.foneticas
			val palabra = if (modo != FONETICAS_NO) com.xebolax.tiresias.textos.DatosIdioma.de(lector.servicio, com.xebolax.tiresias.base.Idioma.efectivo(lector.servicio)).fonetico.de(s.firstOrNull() ?: ' ')?.takeIf { s.length == 1 && s[0].isLetter() } else null
			when {
				palabra != null && modo == FONETICAS_EN_VEZ -> lector.voz.decir(Enunciado(palabra, Modo.INTERRUMPIR, Tipo.NAVEGACION, tono = tonoMayuscula(s)))
				palabra != null && modo == FONETICAS_PAUSA -> {
					lector.voz.decir(Enunciado(s, Modo.INTERRUMPIR, Tipo.NAVEGACION, deletrear = true, tono = tonoMayuscula(s)))
					// Si en un segundo no se ha ido a otra letra, la palabra: «b»... «Barcelona»
					val turno = ++turnoLetra
					lector.manejador.postDelayed({ if (turno == turnoLetra) lector.voz.decir(Enunciado(palabra, Modo.ENCOLAR, Tipo.NAVEGACION, vigente = { turno == turnoLetra })) }, 1000)
				}
				else -> {
					lector.voz.decir(Enunciado(s, Modo.INTERRUMPIR, Tipo.NAVEGACION, deletrear = true, tono = tonoMayuscula(s)))
					// «b, Barcelona»: la palabra detrás, tras una pausa corta
					if (palabra != null) lector.voz.decir(Enunciado(palabra, Modo.ENCOLAR, Tipo.NAVEGACION))
				}
			}
		} else lector.decir(s.ifBlank { lector.textos.getString(R.string.edicion_linea_vacia) }, Modo.INTERRUMPIR, Tipo.NAVEGACION)
	}

	private fun tonoMayuscula(s: String): Int {
		val modo = lector.ajustes.ajustesTexto.mayusculas
		val tonos = modo == com.xebolax.tiresias.nucleo.ModoMayusculas.TONO || modo == com.xebolax.tiresias.nucleo.ModoMayusculas.PALABRA_Y_TONO
		if (s.isNotEmpty() && Character.isUpperCase(s.codePointAt(0))) {
			if (modo == com.xebolax.tiresias.nucleo.ModoMayusculas.SONIDO) lector.sonidos.tocar(Sonido.EDICION, tono = 1.5f)
			if (tonos) return 140
		}
		return 100
	}

	/** Si el sistema dice los caracteres de las contraseñas (Ajustes > Privacidad > Mostrar contraseñas). */
	private fun mostrarContrasenas(): Boolean = runCatching {
		android.provider.Settings.System.getInt(lector.servicio.contentResolver, android.provider.Settings.System.TEXT_SHOW_PASSWORD, 1) == 1
	}.getOrDefault(false)

	// --- Órdenes de edición -----------------------------------------------------------------

	private fun campo(): AccessibilityNodeInfo? = lector.foco?.takeIf { it.isEditable }

	fun copiar(): Boolean {
		val f = lector.foco ?: return false
		if (f.isEditable && f.textSelectionStart != f.textSelectionEnd && f.performAction(AccessibilityNodeInfo.ACTION_COPY)) {
			lector.decir(R.string.copiado)
			return true
		}
		// Trozos marcados en modo selección: se copian todos juntos, uno por línea
		cerrarTrozo()
		if (trozos.isNotEmpty()) {
			val n = trozos.size
			portapapeles(trozos.joinToString("\n"))
			trozos.clear()
			lector.decir(lector.textos.resources.getQuantityString(R.plurals.edicion_trozos_copiados, n, n))
			return true
		}
		// Fuera de un campo: copia el texto del elemento (petición frecuente: copiar un mensaje, un código)
		val texto = lector.elemento(f).let { if (it.editable) it.texto else it.nombre }
		if (texto.isBlank()) return false
		portapapeles(texto)
		lector.decir(lector.textos.getString(R.string.edicion_copiado_texto, texto))
		return true
	}

	/**
	 * Copia [texto]. Si es [sensible] (un código de seguridad), se marca para que
	 * el teclado y el aviso de Android no lo enseñen en su vista previa.
	 */
	fun portapapeles(texto: String, sensible: Boolean = false) {
		val cm = lector.servicio.getSystemService(android.content.ClipboardManager::class.java) ?: return
		val clip = android.content.ClipData.newPlainText("Tiresias", texto)
		if (sensible) clip.description.extras = android.os.PersistableBundle().apply {
			putBoolean(if (android.os.Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
		}
		vigilarPortapapeles(cm)
		// El aviso de este cambio (si llega) lo deja en 0: lo de después es de otro
		cambiosPortapapeles = -1
		cm.setPrimaryClip(clip)
		huellaCopiado = texto.hashCode()
		// Lo sensible (un código de seguridad) no se apunta
		if (!sensible && texto.isNotBlank()) synchronized(copiado) {
			copiado.remove(texto)
			copiado.addFirst(texto)
			while (copiado.size > MAXIMO_COPIADO) copiado.removeLast()
		}
	}

	/** Lo último que ha copiado Tiresias (lo más nuevo primero). Solo en memoria: se olvida al bloquear el móvil. */
	private val copiado = ArrayDeque<String>()

	fun copiado(): List<String> = synchronized(copiado) { copiado.toList() }

	fun olvidarCopiado(texto: String? = null) = synchronized(copiado) { if (texto == null) copiado.clear() else copiado.remove(texto) }

	/**
	 * Apunta lo que ha copiado una app (lo ve el teclado braille de Tiresias, el
	 * único que puede leer el portapapeles desde Android 10), como lo copiado por
	 * Tiresias: solo en memoria y se olvida al bloquear.
	 */
	fun apuntarCopiado(texto: String) {
		if (texto.isBlank() || texto.hashCode() == huellaCopiado) return
		synchronized(copiado) {
			copiado.remove(texto)
			copiado.addFirst(texto)
			while (copiado.size > MAXIMO_COPIADO) copiado.removeLast()
		}
	}

	/** Lo copiado que el usuario ha marcado como favorito (Can Talk): solo en memoria; no se olvida al bloquear, sí al reiniciar. */
	private val favoritos = ArrayDeque<String>()

	fun copiadoFavorito(): List<String> = synchronized(favoritos) { favoritos.toList() }

	/** Pone o quita [texto] de los favoritos; devuelve si queda puesto. */
	fun alternarFavorito(texto: String): Boolean = synchronized(favoritos) {
		if (favoritos.remove(texto)) false
		else {
			favoritos.addFirst(texto)
			while (favoritos.size > MAXIMO_FAVORITOS) favoritos.removeLast()
			true
		}
	}

	/** Añade [texto] (lo último dicho) a lo último que copió Tiresias, en otra línea, y lo copia junto. */
	fun copiarAnadiendo(texto: String) {
		val antes = synchronized(copiado) { copiado.firstOrNull() }
		val junto = if (antes.isNullOrBlank()) texto else antes + "\n" + texto
		synchronized(copiado) { antes?.let { copiado.remove(it) } }
		portapapeles(junto)
	}

	private var vigilado: android.content.ClipboardManager? = null
	private val alCambiarPortapapeles = android.content.ClipboardManager.OnPrimaryClipChangedListener { cambiosPortapapeles++ }

	private fun vigilarPortapapeles(cm: android.content.ClipboardManager) {
		if (vigilado != null) return
		vigilado = cm
		cm.addPrimaryClipChangedListener(alCambiarPortapapeles)
	}

	/** Al apagarse el lector: deja de vigilar el portapapeles (si no, cada arranque del servicio dejaría uno más). */
	fun cerrar() {
		vigilado?.let { cm -> runCatching { cm.removePrimaryClipChangedListener(alCambiarPortapapeles) }.onFailure { com.xebolax.tiresias.base.Registro.w("EdicionTexto", "No se pudo soltar el portapapeles", it) } }
		vigilado = null
	}

	/** La huella (no el texto) de lo último que copió Tiresias, para no vaciar lo que haya copiado otro. */
	private var huellaCopiado: Int? = null

	/**
	 * Vacía el portapapeles si aún tiene [texto], lo que copió Tiresias. Si
	 * Android no deja leerlo, se vacía solo si lo último que se copió en el móvil
	 * fue eso (Tiresias lo sabe porque lo copió él y no ha cambiado desde entonces).
	 */
	fun vaciarPortapapeles(texto: String): Boolean {
		val cm = lector.servicio.getSystemService(android.content.ClipboardManager::class.java) ?: return false
		if (huellaCopiado != texto.hashCode()) return false
		val actual = try {
			cm.primaryClip?.getItemAt(0)?.text?.toString()
		} catch (e: SecurityException) {
			com.xebolax.tiresias.base.Registro.w("Portapapeles", "Android no deja leer el portapapeles", e)
			null
		}
		if (actual != null && actual != texto) return false
		if (actual == null && cambiosPortapapeles > 0) return false
		if (android.os.Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
		huellaCopiado = null
		return true
	}

	/** Cuántas veces ha cambiado el portapapeles desde la última copia de Tiresias (si Android lo avisa). */
	@Volatile private var cambiosPortapapeles = 0

	fun cortar(): Boolean = campo()?.performAction(AccessibilityNodeInfo.ACTION_CUT)?.also { if (it) lector.decir(R.string.edicion_cortado) } ?: false

	fun pegar(): Boolean = campo()?.performAction(AccessibilityNodeInfo.ACTION_PASTE)?.also { if (it) lector.decir(R.string.edicion_pegado) } ?: false

	fun seleccionarTodo(): Boolean {
		val c = campo() ?: return false
		val largo = c.text?.length ?: 0
		return seleccion(c, 0, largo).also { if (it) lector.decir(R.string.edicion_todo_seleccionado) }
	}

	fun cursorA(inicio: Boolean): Boolean {
		val c = campo() ?: return false
		val p = if (inicio) 0 else c.text?.length ?: 0
		return seleccion(c, p, p).also { if (it) lector.decir(if (inicio) R.string.edicion_principio else R.string.edicion_final) }
	}

	/** Borra todo el texto del campo (Backtalk 318). Si solo tiene la pista (está vacío), no hay nada que borrar. */
	fun borrarTodo(): Boolean {
		val c = campo() ?: return false
		if (c.text.isNullOrEmpty() || (Build.VERSION.SDK_INT >= 26 && c.isShowingHintText)) {
			lector.decir(R.string.edicion_nada_que_borrar)
			return false
		}
		val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "") }
		val hecho = runCatching { c.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)
		lector.decir(if (hecho) R.string.edicion_texto_borrado else R.string.edicion_no_borrado)
		return hecho
	}

	private fun seleccion(c: AccessibilityNodeInfo, a: Int, b: Int): Boolean {
		val args = Bundle().apply {
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, a)
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, b)
		}
		return runCatching { c.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args) }.getOrDefault(false)
	}

	/** Leer lo que hay en el portapapeles. */
	companion object {
		/** Cuántas cosas copiadas se recuerdan. */
		private const val MAXIMO_COPIADO = 20
		private const val MAXIMO_FAVORITOS = 50
	}

	fun leerPortapapeles() {
		val cm = lector.servicio.getSystemService(android.content.ClipboardManager::class.java)
		val texto = runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(lector.servicio)?.toString() }.getOrNull()
		lector.decir(if (texto.isNullOrBlank()) lector.textos.getString(R.string.edicion_portapapeles_vacio) else texto)
	}
}
