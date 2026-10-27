package com.xebolax.tiresias.braille

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ChecksSdkIntAtLeast
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.braille.EntradaBraille
import com.xebolax.tiresias.nucleo.braille.OrdenLinea
import com.xebolax.tiresias.nucleo.braille.TablaBraille
import com.xebolax.tiresias.nucleo.braille.TablaInformatica
import com.xebolax.tiresias.nucleo.braille.TablaLiteraria
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo

/**
 * El braille de Tiresias: el teclado braille en pantalla ([TecladoBraille]) y
 * las líneas braille ([LineaBraille], Android 15 o posterior). En la línea se
 * ve lo que tiene el foco (lo mismo que se dice) y, en un campo de texto, su
 * texto con el cursor; desde su teclado se escribe y se maneja el móvil.
 *
 * Con el modo «primero braille» y la línea conectada, la voz se calla (o solo
 * dice los avisos) y todo se lee en la línea. Si la línea se desconecta, la voz
 * vuelve sola al momento: nadie se queda sin salida.
 */
class Braille(private val lector: Lector, private val alAccion: (Accion) -> Unit) {
	private val servicio = lector.servicio
	private val linea: LineaBraille? = if (Build.VERSION.SDK_INT >= 35) LineaBraille(lector, { lector.manejador.post { hacer(it) } }, { aplicarPrimero() }) else null

	/** Hace [f] con la línea braille, que solo existe en Android 15 o posterior (null antes o sin línea). */
	@ChecksSdkIntAtLeast(api = 35, lambda = 0)
	private inline fun <T> conLinea(f: (LineaBraille) -> T): T? = if (Build.VERSION.SDK_INT >= 35) linea?.let(f) else null

	private var tabla: TablaBraille = cargarTabla()
	private var entrada: EntradaBraille = tabla.entrada()

	/** El campo de texto que se ve en la línea (para seguir su cursor). */
	private var campo: AccessibilityNodeInfo? = null

	/** El identificador del teclado braille de Tiresias como método de entrada del sistema. */
	val idTeclado: String get() = "${servicio.packageName}/${TecladoBraille::class.java.name}"

	fun iniciar() {
		if (lector.ajustes.brailleLinea) conLinea { it.iniciar() }
	}

	fun parar() {
		conLinea { it.parar() }
	}

	// --- Primero braille -------------------------------------------------------------

	/** Si el usuario quiere el modo «primero braille» (se aplica solo con la línea conectada). */
	private var primeroBraille = lector.ajustes.braillePrimero != PRIMERO_NO

	/** Si ahora mismo la voz está callada por el braille. */
	val primeroActivo: Boolean get() = lector.voz.tiposPermitidos != null

	/** Calla la voz o la devuelve según el modo y si la línea está conectada. */
	fun aplicarPrimero() {
		val conectada = conLinea { it.conectada } == true
		lector.voz.tiposPermitidos = when {
			!primeroBraille || !conectada -> null
			lector.ajustes.braillePrimero == PRIMERO_SIN_VOZ -> emptySet()
			// Lo mínimo: los avisos de Tiresias y las notificaciones
			else -> setOf(Tipo.SISTEMA, Tipo.NOTIFICACION)
		}
	}

	/** La acción «Primero braille»: lo enciende o lo apaga (sin línea no se puede, para no quedarse sin voz). */
	fun alternarPrimero(): Boolean {
		if (conLinea { it.conectada } != true) {
			lector.decir(R.string.braille_primero_sin_linea)
			return false
		}
		primeroBraille = !primeroBraille
		// Se guarda (al apagarlo, «no»; al encenderlo sin nivel elegido, lo mínimo)
		val a = lector.ajustes
		if (!primeroBraille) a.braillePrimero = PRIMERO_NO else if (a.braillePrimero == PRIMERO_NO) a.braillePrimero = PRIMERO_MINIMA
		// Se dice (y se ve en la línea) antes de callar la voz
		lector.voz.tiposPermitidos = null
		lector.decir(if (primeroBraille) R.string.braille_primero_si else R.string.braille_primero_no)
		lector.manejador.postDelayed({ aplicarPrimero() }, 1500)
		return true
	}

	/** El modo seguro: la voz siempre vuelve. */
	fun quitarPrimero() {
		primeroBraille = false
		lector.voz.tiposPermitidos = null
	}

	fun recargarAjustes() {
		primeroBraille = lector.ajustes.braillePrimero != PRIMERO_NO
		aplicarPrimero()
		val antes = tabla.id
		tabla = cargarTabla()
		if (tabla.id != antes) entrada = tabla.entrada()
		if (!lector.ajustes.brailleLinea) conLinea { it.parar() }
		conLinea { it.aplicarAjustes() }
		lector.foco?.let { mostrarElemento(it) }
	}

	/** Busca y conecta una línea braille, diciendo lo que pasa (lo pide el usuario). */
	fun buscarLinea() {
		conLinea { it.buscar(true) } ?: lector.decir(R.string.braille_linea_no_disponible)
	}

	private fun cargarTabla(): TablaBraille {
		val activos = servicio.assets
		fun leer(nombre: String) = runCatching { activos.open("braille/$nombre").bufferedReader().readLines().asSequence() }.getOrElse { emptySequence() }
		val idioma = lector.textos.resources.configuration.locales[0].language
		val informatica = runCatching {
			// La elegida en Ajustes (Backtalk 186) o, si no, la del idioma de Tiresias
			val elegida = lector.ajustes.brailleTablaInformatica.takeIf { t -> TABLAS_INFORMATICAS.any { it.first == t } }
			val nombre = elegida ?: if (idioma == "es") "Es-Es-G0.utb" else "en-us-comp8-ext.utb"
			TablaInformatica(nombre, leer(nombre), ::leer)
		}.onFailure { Registro.w(ETIQUETA, "No se pudo leer la tabla braille", it) }.getOrNull()
		return when {
			lector.ajustes.brailleTabla == TABLA_LITERARIA || informatica == null -> TablaLiteraria(informatica)
			else -> informatica
		}
	}

	// --- Lo que se ve en la línea --------------------------------------------------------

	/** Pone en la línea el elemento enfocado: en un campo de texto, su texto con el cursor. */
	fun mostrarElemento(n: AccessibilityNodeInfo, dicho: String? = null) {
		// Con un documento abierto en la línea, el foco no lo tapa
		if (documento != null) return
		conLinea { l -> if (l.conectada) mostrarEn(l, n, dicho) }
	}

	// --- Documentos en la línea (archivos BRF, Backtalk 153) -------------------------------

	/** Las líneas del documento que se lee en la línea braille, y la que se ve. */
	private var documento: List<String>? = null
	private var lineaDocumento = 0

	/**
	 * Abre un archivo BRF (braille ASCII, de 6 puntos) en la línea: se lee línea
	 * a línea con las teclas de desplazar, sin que lo tape el foco. Atrás lo cierra.
	 * Devuelve false si no hay línea conectada.
	 */
	fun abrirBrf(texto: String): Boolean {
		if (conLinea { it.conectada } != true) return false
		val lineas = texto.replace("\u000C", "\n").lines().map { brfAUnicode(it) }.ifEmpty { listOf("") }
		documento = lineas
		lineaDocumento = 0
		lector.manejador.post {
			lector.decir(lector.textos.resources.getQuantityString(R.plurals.braille_documento_abierto, lineas.size, lineas.size))
			verLineaDocumento()
		}
		return true
	}

	private fun verLineaDocumento() {
		val d = documento ?: return
		conLinea { l -> if (l.conectada) l.mostrar(tabla, d.getOrElse(lineaDocumento) { "" }) }
	}

	/** Lo que hace una orden con un documento abierto; false si no es suya (sigue lo normal). */
	private fun ordenEnDocumento(o: OrdenLinea): Boolean {
		val d = documento ?: return false
		when {
			o is OrdenLinea.Desplazar -> {
				if (conLinea { it.desplazar(o.adelante) } == true) return true
				val siguiente = lineaDocumento + if (o.adelante) 1 else -1
				if (siguiente !in d.indices) {
					lector.sonidos.tocar(Sonido.BORDE)
					return true
				}
				lineaDocumento = siguiente
				verLineaDocumento()
			}
			o is OrdenLinea.Hacer && o.accion == Accion.ATRAS -> cerrarDocumento()
			o is OrdenLinea.Hacer && (o.accion == Accion.SIGUIENTE || o.accion == Accion.ANTERIOR) -> {
				lineaDocumento = (lineaDocumento + if (o.accion == Accion.SIGUIENTE) 1 else -1).coerceIn(0, d.lastIndex)
				verLineaDocumento()
			}
			o is OrdenLinea.Hacer && o.accion == Accion.PRIMERO -> { lineaDocumento = 0; verLineaDocumento() }
			o is OrdenLinea.Hacer && o.accion == Accion.ULTIMO -> { lineaDocumento = d.lastIndex; verLineaDocumento() }
			else -> return false
		}
		return true
	}

	fun cerrarDocumento() {
		if (documento == null) return
		documento = null
		lector.decir(R.string.braille_documento_cerrado)
		lector.foco?.let { mostrarElemento(it) }
	}

	// --- Línea braille de otra app (API, Backtalk 179) --------------------------------------

	/** Otra app hace de línea braille de [celdas] celdas; [enviar] le manda lo que hay que ver. */
	fun conectarVirtual(nombre: String, celdas: Int, enviar: (ByteArray) -> Unit): Boolean = conLinea { it.conectarVirtual(nombre, celdas, enviar) } ?: false

	fun desconectarVirtual() {
		conLinea { it.desconectarVirtual() }
	}

	/** Una tecla de la línea de otra app, ya convertida en orden. */
	fun ordenVirtual(o: OrdenLinea) {
		lector.manejador.post { hacer(o) }
	}

	@androidx.annotation.RequiresApi(35)
	private fun mostrarEn(l: LineaBraille, n: AccessibilityNodeInfo, dicho: String?) {
		if (n.isEditable) {
			campo = n
			l.mostrar(tabla, textoDe(n), n.textSelectionEnd.coerceAtLeast(0))
		} else {
			campo = null
			l.mostrar(tabla, dicho ?: lector.textoElemento(n))
		}
	}

	/** El texto o el cursor del campo que se ve han cambiado. */
	fun alCambiarEdicion(n: AccessibilityNodeInfo?) {
		val c = campo ?: return
		if (n != null && n != c) return
		conLinea { l ->
			if (l.conectada) {
				runCatching { c.refresh() }
				l.mostrar(tabla, textoDe(c), c.textSelectionEnd.coerceAtLeast(0))
			}
		}
	}

	/** Un aviso (notificación, anuncio, título de ventana) se ve unos segundos. */
	fun mensaje(texto: String) {
		if (texto.isBlank()) return
		lector.manejador.post { conLinea { l -> if (l.conectada) l.mensaje(tabla, texto, lector.ajustes.brailleMensajes) } }
	}

	private fun textoDe(n: AccessibilityNodeInfo): String =
		if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) "" else n.text?.toString().orEmpty()

	// --- Lo que se pide desde la línea ----------------------------------------------------

	private fun hacer(o: OrdenLinea) {
		if (ordenEnDocumento(o)) return
		// Un complemento con permiso «braille» puede quedarse la orden (notas, calculadora... Backtalk 153)
		if (lector.scriptsListos) {
			val (tipo, valor) = when (o) {
				is OrdenLinea.Hacer -> "accion" to o.accion.name.lowercase()
				is OrdenLinea.Desplazar -> "desplazar" to if (o.adelante) "adelante" else "atras"
				is OrdenLinea.Encaminar -> "celda" to o.celda.toString()
				is OrdenLinea.Escribir -> "puntos" to o.celda.toString()
				OrdenLinea.Espacio -> "tecla" to "espacio"
				OrdenLinea.Borrar -> "tecla" to "borrar"
				OrdenLinea.Intro -> "tecla" to "intro"
				OrdenLinea.Desconocida -> "otra" to "desconocida"
			}
			if (lector.scripts.teclaBraille(tipo, valor)) return
		}
		when (o) {
			is OrdenLinea.Hacer -> alAccion(o.accion)
			is OrdenLinea.Desplazar -> {
				// Al final del texto, al elemento siguiente (y al principio, al anterior), como TalkBack
				if (conLinea { it.desplazar(o.adelante) } != true) alAccion(if (o.adelante) Accion.SIGUIENTE else Accion.ANTERIOR)
			}
			is OrdenLinea.Encaminar -> encaminar(o.celda)
			is OrdenLinea.Escribir -> {
				val texto = entrada.introducir(o.celda)
				when {
					texto == null -> lector.sonidos.tocar(Sonido.ERROR)
					texto.isNotEmpty() -> escribir(texto)
				}
			}
			OrdenLinea.Espacio -> {
				entrada.introducir(0)
				escribir(" ")
			}
			OrdenLinea.Borrar -> borrar()
			OrdenLinea.Intro -> intro()
			OrdenLinea.Desconocida -> lector.sonidos.tocar(Sonido.ERROR)
		}
	}

	/** Tecla de encaminamiento: en un campo de texto lleva el cursor a ese carácter; en otro elemento lo activa. */
	private fun encaminar(celda: Int) {
		val c = campo
		val posicion = conLinea { it.vista?.caracterEn(celda) } ?: -1
		if (c != null && posicion >= 0) {
			val args = Bundle().apply {
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, posicion)
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, posicion)
			}
			if (c.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)) {
				conLinea { it.moverCursor(posicion) }
				return
			}
		}
		alAccion(Accion.ACTIVAR)
	}

	/** El campo con el foco de entrada (donde se escribe). */
	private fun campoActivo(): AccessibilityNodeInfo? =
		campo?.takeIf { runCatching { it.refresh() }.getOrDefault(false) }
			?: lector.raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

	private fun escribir(texto: String) {
		val c = campoActivo() ?: run {
			lector.sonidos.tocar(Sonido.ERROR)
			return
		}
		val actual = textoDe(c)
		val inicio = c.textSelectionStart.coerceIn(0, actual.length).let { if (c.textSelectionStart < 0) actual.length else it }
		val fin = c.textSelectionEnd.coerceIn(inicio, actual.length).let { if (c.textSelectionEnd < 0) actual.length else it }
		cambiarTexto(c, actual.substring(0, inicio) + texto + actual.substring(fin), inicio + texto.length)
		lector.decir(lector.edicionCaracter(texto), tipo = com.xebolax.tiresias.voz.Tipo.ESCRITURA)
	}

	private fun borrar() {
		val c = campoActivo() ?: return
		val actual = textoDe(c)
		val inicio = c.textSelectionStart.let { if (it < 0) actual.length else it.coerceIn(0, actual.length) }
		val fin = c.textSelectionEnd.let { if (it < 0) actual.length else it.coerceIn(inicio, actual.length) }
		if (inicio == fin && inicio == 0) {
			lector.sonidos.tocar(Sonido.BORDE)
			return
		}
		val desde = if (inicio == fin) actual.offsetByCodePoints(inicio, -1) else inicio
		val borrado = actual.substring(desde, fin)
		cambiarTexto(c, actual.substring(0, desde) + actual.substring(fin), desde)
		lector.decir(lector.edicionCaracter(borrado), tipo = com.xebolax.tiresias.voz.Tipo.ESCRITURA)
	}

	private fun intro() {
		val c = campoActivo() ?: run { alAccion(Accion.ACTIVAR); return }
		if (Build.VERSION.SDK_INT >= 30 && c.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return
		// Sin acción de intro: un salto de línea en los campos de varias líneas
		if (c.isMultiLine) escribir("\n") else lector.sonidos.tocar(Sonido.ERROR)
	}

	private fun cambiarTexto(c: AccessibilityNodeInfo, texto: String, cursor: Int) {
		val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto) }
		if (!c.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
			lector.sonidos.tocar(Sonido.ERROR)
			return
		}
		val sel = Bundle().apply {
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
		}
		c.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
		campo = c
		conLinea { it.mostrar(tabla, texto, cursor) }
	}

	// --- El teclado braille en pantalla ---------------------------------------------------

	/** Si el usuario ha activado el teclado braille en los ajustes del sistema. */
	fun tecladoActivado(): Boolean = runCatching {
		Settings.Secure.getString(servicio.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty().split(':').any { it == idTeclado }
	}.getOrDefault(false)

	/**
	 * Cambia al teclado braille (Android 11 o posterior lo hace Tiresias solo,
	 * con el control del teclado del servicio de accesibilidad). Si no está
	 * activado, dice cómo activarlo.
	 */
	fun abrirTeclado(): Boolean {
		if (!tecladoActivado()) {
			lector.decir(R.string.braille_teclado_inactivo)
			return false
		}
		if (Build.VERSION.SDK_INT >= 30) {
			val ok = runCatching { servicio.softKeyboardController.switchToInputMethod(idTeclado) }.getOrElse {
				Registro.w(ETIQUETA, "No se pudo cambiar al teclado braille", it)
				false
			}
			if (ok) return true
		}
		lector.decir(R.string.braille_teclado_elegir)
		return false
	}

	companion object {
		private const val ETIQUETA = "Braille"
		const val TABLA_INFORMATICA = "informatico"
		const val TABLA_LITERARIA = "literario"

		/** Las tablas informáticas de 8 puntos que se pueden elegir (de liblouis) y el idioma de su nombre. */
		val TABLAS_INFORMATICAS: List<Pair<String, String>> = listOf(
			"Es-Es-G0.utb" to "es",
			"en-us-comp8-ext.utb" to "en",
			"en-us-comp8.ctb" to "en",
			"ar-ar-comp8.utb" to "ar",
			"cs-comp8.utb" to "cs",
			"de-de-comp8.ctb" to "de",
			"fi-fi-8dot.ctb" to "fi",
			"fr-bfu-comp8.utb" to "fr",
			"he-IL-comp8.utb" to "he",
			"hr-comp8.utb" to "hr",
			"hu-hu-comp8.ctb" to "hu",
			"it-it-comp8.utb" to "it",
			"nl-comp8.utb" to "nl",
			"no-no-comp8.ctb" to "nb",
			"pl-pl-comp8.ctb" to "pl",
			"pt-pt-comp8.ctb" to "pt",
			"ru-comp8.utb" to "ru",
		)

		/** El braille ASCII de los BRF (Braille ASCII de Norteamérica): cada carácter es una celda de 6 puntos, en este orden. */
		private const val BRAILLE_ASCII = " A1B'K2L@CIF/MSP\"E3H9O6R^DJG>NTQ,*5<-U8V.%[$+X!&;:4\\0Z7(_?W]#Y)="

		/** Una línea de un BRF en caracteres braille de Unicode (que la tabla pone tal cual, con braille-patterns). */
		fun brfAUnicode(linea: String): String = buildString(linea.length) {
			for (c in linea) {
				val i = BRAILLE_ASCII.indexOf(c.uppercaseChar())
				append(if (i >= 0) (0x2800 + i).toChar() else if (c == ' ') ' ' else c)
			}
		}
		const val PRIMERO_NO = "no"
		const val PRIMERO_MINIMA = "minima"
		const val PRIMERO_SIN_VOZ = "sin_voz"
	}
}
