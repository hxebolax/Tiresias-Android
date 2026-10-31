package com.xebolax.tiresias.teclado

import android.os.SystemClock
import android.view.KeyEvent
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.Combinacion
import com.xebolax.tiresias.nucleo.MapaTeclas
import com.xebolax.tiresias.nucleo.Mod

/**
 * El teclado físico (y las teclas de volumen).
 *
 * [alPulsar] se llama en el hilo principal desde `onKeyEvent` y tiene que
 * contestar enseguida si Tiresias se queda la tecla; lo que haya que hacer se
 * manda al hilo del lector.
 *
 * - **Tecla Tiresias**: Alt, Meta (Buscar o Windows), Bloq Mayús o Insertar,
 *   según el estilo (TalkBack o Tiresias para Windows).
 * - **Navegación por letras** en la web (H encabezado, K enlace, 1 a 6 nivel de
 *   encabezado...), con Mayúsculas hacia atrás, si no se está escribiendo en un campo.
 * - **Cualquier tecla corta la voz** (como TalkBack), salvo los modificadores y el volumen.
 * - **Las dos teclas de volumen** mantenidas: suspender o reanudar Tiresias, y
 *   quitar el toque directo. Es la salida que siempre funciona.
 */
class Teclado(private val lector: Lector, private val alAccion: (Accion) -> Unit) {
	private var tiresiasPulsada = false
	private var usadaConOtra = false
	private var volumenArriba = 0L
	private var volumenAbajo = 0L
	private var ambasDisparada = false
	private val pulsadas = HashSet<Int>()

	/** Si la tecla [codigo] corta la voz, según el ajuste (siempre, solo escribiendo en un campo o nunca). */
	private fun cortaLaVoz(codigo: Int): Boolean {
		val a = lector.ajustes
		if ((codigo == KeyEvent.KEYCODE_ENTER || codigo == KeyEvent.KEYCODE_NUMPAD_ENTER) && !a.introCorta) return false
		return when (a.teclasCortan) {
			Ajustes.TECLAS_NUNCA -> false
			Ajustes.TECLAS_CAMPOS -> lector.foco?.isEditable == true
			else -> true
		}
	}

	/** En el modo senior, con el teléfono sonando: subir volumen descuelga y bajarlo cuelga, sin buscar nada en la pantalla. */
	private fun teclaDeLlamada(e: KeyEvent): Boolean {
		val a = lector.ajustes
		if (!a.modoSenior || !a.seniorTeclasLlamada) return false
		val modo = lector.servicio.getSystemService(android.media.AudioManager::class.java)?.mode
		if (modo != android.media.AudioManager.MODE_RINGTONE) return false
		if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
			alAccion(if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP) Accion.LLAMADA_CONTESTAR else Accion.LLAMADA_RECHAZAR)
		}
		return true
	}

	fun alPulsar(e: KeyEvent): Boolean {
		val codigo = e.keyCode
		if (codigo != KeyEvent.KEYCODE_VOLUME_UP && codigo != KeyEvent.KEYCODE_VOLUME_DOWN) lector.momentoTecla = SystemClock.uptimeMillis()
		lector.momentoUsuario = SystemClock.uptimeMillis()
		if (codigo == KeyEvent.KEYCODE_VOLUME_UP || codigo == KeyEvent.KEYCODE_VOLUME_DOWN) {
			if (teclaDeLlamada(e)) return true
			return volumen(e)
		}
		val esTiresias = esTeclaTiresias(codigo)
		if (e.action == KeyEvent.ACTION_DOWN) {
			if (esTiresias) {
				if (e.repeatCount == 0) {
					tiresiasPulsada = true
					usadaConOtra = false
				}
				// Alt y Meta se dejan pasar solos; Bloq Mayús e Insertar se los queda Tiresias
				return !esAltOMeta(codigo)
			}
			// Control solo corta siempre la voz (como en los demás lectores); las demás teclas, según el ajuste
			if (esControl(codigo) || (!esModificador(codigo) && cortaLaVoz(codigo))) lector.voz.callar()
			val combinacion = combinacion(e)
			val accion = lector.ajustes.mapaTeclas[combinacion]
			// La combinación lleva la tecla Tiresias solo si está pulsada: las del mapa por defecto no roban teclas a las apps
			if (accion != null) {
				usadaConOtra = true
				pulsadas += codigo
				if (e.repeatCount == 0) despachar(accion)
				return true
			}
			// Navegación por letras en la web (modo exploración)
			if (!tiresiasPulsada && e.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON) == 0 && navegaLetras()) {
				val nivel = MapaTeclas.nivelEncabezado(codigo)
				val control = MapaTeclas.letraNavegacion(codigo)
				if (nivel > 0 || control != null) {
					pulsadas += codigo
					val atras = e.isShiftPressed
					lector.manejador.post {
						if (nivel > 0) lector.navegador.porTipo(com.xebolax.tiresias.nucleo.ControlLectura.ENCABEZADOS, !atras, nivel)
						else lector.navegador.porTipo(control!!, !atras)
					}
					return true
				}
			}
			return false
		}
		// Al soltar
		if (esTiresias) {
			tiresiasPulsada = false
			return !esAltOMeta(codigo)
		}
		return pulsadas.remove(codigo)
	}

	private fun despachar(accion: Accion) {
		if (lector.ayudaEntrada && accion != Accion.AYUDA_ENTRADA) {
			// Con el teclado también se sale con Atrás, como con el gesto
			if (accion == Accion.ATRAS) alAccion(Accion.AYUDA_ENTRADA)
			else lector.manejador.post { lector.decir(lector.textos.getString(R.string.ayuda_entrada_accion, nombre(accion))) }
			return
		}
		alAccion(accion)
	}

	private fun nombre(a: Accion): String {
		val t = lector.textos
		val id = t.resources.getIdentifier("accion_" + a.name.lowercase(), "string", t.packageName)
		return if (id != 0) t.getString(id) else a.name.lowercase()
	}

	private fun navegaLetras(): Boolean {
		val f = lector.foco ?: return false
		if (f.isEditable) return false
		// Un árbol, un desplegable, una lista o un deslizador de la web usan las letras y las flechas: son suyas
		val rolWeb = runCatching { f.extras?.getCharSequence("AccessibilityNodeInfo.chromeRole")?.toString() }.getOrNull()
		if (rolWeb in CONTROLES_CON_TECLAS) return false
		return lector.navegador.enWeb(f)
	}

	private fun combinacion(e: KeyEvent): Combinacion {
		var m = 0
		if (tiresiasPulsada) m = m or Mod.TIRESIAS
		val estilo = lector.ajustes.teclaTiresias
		if (e.isCtrlPressed) m = m or Mod.CTRL
		if (e.isShiftPressed) m = m or Mod.MAYUS
		// Si Alt o Meta son la tecla Tiresias, no cuentan además como modificador
		if (e.isAltPressed && estilo != "alt") m = m or Mod.ALT
		if (e.isMetaPressed && estilo != "meta") m = m or Mod.META
		return Combinacion(e.keyCode, m)
	}

	private fun esTeclaTiresias(codigo: Int): Boolean = when (lector.ajustes.teclaTiresias) {
		"alt" -> codigo == KeyEvent.KEYCODE_ALT_LEFT || codigo == KeyEvent.KEYCODE_ALT_RIGHT
		"meta" -> codigo == KeyEvent.KEYCODE_META_LEFT || codigo == KeyEvent.KEYCODE_META_RIGHT
		"bloqmayus" -> codigo == KeyEvent.KEYCODE_CAPS_LOCK
		else -> codigo == KeyEvent.KEYCODE_INSERT
	}

	private fun esAltOMeta(codigo: Int) = codigo == KeyEvent.KEYCODE_ALT_LEFT || codigo == KeyEvent.KEYCODE_ALT_RIGHT ||
		codigo == KeyEvent.KEYCODE_META_LEFT || codigo == KeyEvent.KEYCODE_META_RIGHT

	private val CONTROLES_CON_TECLAS = setOf(
		"tree", "treeItem", "treeitem", "comboBoxGrouping", "comboBoxMenuButton", "combobox", "listBox", "listbox", "listBoxOption", "option",
		"menu", "menuItem", "menuitem", "menuBar", "slider", "spinButton", "spinbutton", "tabList", "tablist", "grid", "radioGroup", "textField",
	)

	private fun esControl(codigo: Int) = codigo == KeyEvent.KEYCODE_CTRL_LEFT || codigo == KeyEvent.KEYCODE_CTRL_RIGHT

	private fun esModificador(codigo: Int) = KeyEvent.isModifierKey(codigo) || codigo == KeyEvent.KEYCODE_CAPS_LOCK || codigo == KeyEvent.KEYCODE_INSERT

	/** La acción que tiene asignada [g] (gestos de las teclas de volumen), con el perfil de la app de delante. */
	private fun accionDe(g: Gesto): Accion =
		lector.perfiles.mapaGestos(lector.ajustes.mapaGestos, lector.foco?.packageName?.toString())[g]

	private var volumenLarga = false
	/** La tecla de volumen pulsada ahora mueve el cursor del campo de texto, y ese campo. */
	private var volumenEnCursor = false
	private var campoCursor: android.view.accessibility.AccessibilityNodeInfo? = null

	/**
	 * El campo de texto en el que se escribe: el del foco de Tiresias o, si el foco está en otra cosa (la tecla
	 * del teclado en pantalla que se acaba de explorar), el que tiene el foco de escritura.
	 */
	private fun campoEditando(): android.view.accessibility.AccessibilityNodeInfo? =
		lector.foco?.takeIf { it.isEditable }
			?: runCatching { lector.servicio.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()?.takeIf { it.isEditable }
	/** La tecla de volumen pulsada ahora cambia el volumen de la voz (había un dedo en la pantalla al pulsarla). */
	private var volumenConDedo = false

	/**
	 * Con un dedo en la pantalla (o leyendo de seguido), la tecla cambia el volumen de la voz de Tiresias, como
	 * en TalkBack; también es de Tiresias el soltar de esa tecla, aunque el dedo ya se haya levantado.
	 */
	private fun volumenDeLaVoz(e: KeyEvent, arriba: Boolean): Boolean {
		volumenConDedo = true
		// En una llamada, con «Voz en llamadas», el volumen propio de la voz en las llamadas (Can Talk)
		if (lector.ajustes.vozLlamadas && com.xebolax.tiresias.voz.VozEnLlamadas.enLlamada(lector.servicio)) {
			if (e.repeatCount % 3 == 0) lector.manejador.post {
				val nuevo = com.xebolax.tiresias.voz.VozEnLlamadas.cambiar(lector.ajustes, arriba)
				lector.decir(lector.textos.getString(R.string.voz_llamadas_volumen, nuevo))
			}
			return true
		}
		if (e.repeatCount % 3 == 0) alAccion(if (arriba) Accion.VOLUMEN_MAS else Accion.VOLUMEN_MENOS)
		return true
	}

	private fun pantallaEncendida(): Boolean =
		runCatching { lector.servicio.getSystemService(android.os.PowerManager::class.java)?.isInteractive }.getOrNull() ?: true
	private val toquesVolumen = ArrayList<Long>()
	private var volumenPendiente: Runnable? = null

	/**
	 * Las teclas de volumen. Las dos a la vez, mantenidas [MANTENER_AMBAS_MS],
	 * hacen lo que tengan asignado (de fábrica, suspender o reanudar Tiresias;
	 * también quitan el toque directo): funciona siempre, también suspendido.
	 *
	 * Una tecla mantenida o pulsada tres veces hace lo asignado en Gestos; si no
	 * hay nada asignado, las teclas pasan al sistema y cambian el volumen como
	 * siempre. Con «Teclas de volumen en los deslizadores», con un deslizador
	 * enfocado suben y bajan su valor.
	 */
	private fun volumen(e: KeyEvent): Boolean {
		val ahora = SystemClock.uptimeMillis()
		val arriba = e.keyCode == KeyEvent.KEYCODE_VOLUME_UP
		if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
			if (arriba) volumenArriba = ahora else volumenAbajo = ahora
			if (volumenArriba > 0 && volumenAbajo > 0) {
				ambasDisparada = false
				// Las dos a la vez (casi a la vez) mientras se lee una notificación: se calla (SandFly)
				if (lector.voz.diciendoAviso && Math.abs(volumenArriba - volumenAbajo) <= AMBAS_AVISO_MS) lector.voz.callar()
				lector.manejador.postDelayed({
					if (volumenArriba > 0 && volumenAbajo > 0 && !ambasDisparada) {
						ambasDisparada = true
						// Las dos teclas de volumen también terminan la práctica de los gestos
						if (lector.ayudaEntrada) alAccion(Accion.AYUDA_ENTRADA)
						else if (lector.gestos.toqueDirecto) {
							val eraAyuda = lector.gestos.ayuda
							lector.gestos.restablecer()
							lector.decir(if (eraAyuda) R.string.ayuda_otra_no else R.string.gestos_toque_directo_no)
						} else accionDe(Gesto.VOLUMEN_AMBAS).takeIf { it != Accion.NINGUNA }?.let { alAccion(it) }
					}
				}, MANTENER_AMBAS_MS)
			}
		} else if (e.action == KeyEvent.ACTION_UP) {
			if (arriba) volumenArriba = 0 else volumenAbajo = 0
		}
		// Las dos a la vez: no se toca nada más
		if (volumenArriba > 0 && volumenAbajo > 0) return false
		// Con un dedo en la pantalla, el volumen de la voz de Tiresias (como TalkBack); el soltar de esa
		// tecla también es de Tiresias, aunque el dedo ya se haya levantado
		if (e.action == KeyEvent.ACTION_DOWN && lector.dedoEnPantalla) return volumenDeLaVoz(e, arriba)
		if (volumenConDedo) {
			if (e.action == KeyEvent.ACTION_UP) volumenConDedo = false
			return true
		}
		// En un campo de texto, si se ha elegido, las teclas mueven el cursor: arriba, un carácter atrás; abajo,
		// uno adelante (SandFly). Lo que se decide al pulsar vale hasta soltar
		if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && lector.ajustes.volumenCursor && !lector.continua.activa && pantallaEncendida()) {
			campoCursor = campoEditando()
			volumenEnCursor = campoCursor != null
		}
		if (volumenEnCursor) {
			if (e.action == KeyEvent.ACTION_DOWN) {
				val f = campoCursor
				if (f != null) {
					val args = android.os.Bundle().apply {
						putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, android.view.accessibility.AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER)
					}
					val accion = if (arriba) android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
					else android.view.accessibility.AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
					lector.manejador.post { runCatching { f.performAction(accion, args) } }
				}
			} else if (e.action == KeyEvent.ACTION_UP) volumenEnCursor = false
			return true
		}
		// Un deslizador enfocado: las teclas cambian su valor (con la pantalla encendida: apagada, el volumen de siempre)
		if (lector.ajustes.volumenDeslizadores && lector.foco?.rangeInfo != null && pantallaEncendida()) {
			if (e.action == KeyEvent.ACTION_DOWN) alAccion(if (arriba) Accion.VALOR_MAS else Accion.VALOR_MENOS)
			return true
		}
		val larga = accionDe(if (arriba) Gesto.VOLUMEN_ARRIBA_MANTENIDO else Gesto.VOLUMEN_ABAJO_MANTENIDO)
		val triple = accionDe(if (arriba) Gesto.VOLUMEN_ARRIBA_TRIPLE else Gesto.VOLUMEN_ABAJO_TRIPLE)
		if (larga == Accion.NINGUNA && triple == Accion.NINGUNA) {
			// Leyendo de seguido, el volumen de la voz sin cortar la lectura (como TalkBack); si no, el del sistema
			return if (e.action == KeyEvent.ACTION_DOWN && lector.continua.activa) volumenDeLaVoz(e, arriba) else false
		}
		if (e.action == KeyEvent.ACTION_DOWN) {
			if (e.repeatCount == 0) {
				volumenLarga = false
				if (larga != Accion.NINGUNA) lector.manejador.postDelayed({
					val pulsada = if (arriba) volumenArriba else volumenAbajo
					if (pulsada == ahora) {
						volumenLarga = true
						alAccion(larga)
					}
				}, MANTENER_UNA_MS)
			}
			return true
		}
		if (e.action != KeyEvent.ACTION_UP || volumenLarga) return true
		// Una pulsación corta: si hay triple asignada, se espera a ver si vienen más
		if (triple == Accion.NINGUNA) {
			cambiarVolumen(arriba, 1)
			return true
		}
		toquesVolumen.removeAll { ahora - it > TRIPLE_MS }
		toquesVolumen += ahora
		volumenPendiente?.let { lector.manejador.removeCallbacks(it) }
		if (toquesVolumen.size >= 3) {
			toquesVolumen.clear()
			alAccion(triple)
			return true
		}
		val veces = toquesVolumen.size
		val r = Runnable {
			toquesVolumen.clear()
			cambiarVolumen(arriba, veces)
		}
		volumenPendiente = r
		lector.manejador.postDelayed(r, TRIPLE_MS)
		return true
	}

	/** El volumen como lo cambiaría el sistema (el flujo que suena ahora, con su barra en pantalla). */
	private fun cambiarVolumen(arriba: Boolean, veces: Int) {
		val audio = lector.servicio.getSystemService(android.media.AudioManager::class.java) ?: return
		repeat(veces) {
			runCatching {
				audio.adjustSuggestedStreamVolume(
					if (arriba) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
					android.media.AudioManager.USE_DEFAULT_STREAM_TYPE,
					android.media.AudioManager.FLAG_SHOW_UI,
				)
			}
		}
	}


	companion object {
		const val MANTENER_AMBAS_MS = 1500L
		const val MANTENER_UNA_MS = 700L
		const val TRIPLE_MS = 500L
		/** Lo que puede haber entre las dos teclas de volumen para callar una notificación. */
		const val AMBAS_AVISO_MS = 400L
	}
}
