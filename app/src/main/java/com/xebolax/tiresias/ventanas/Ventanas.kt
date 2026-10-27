package com.xebolax.tiresias.ventanas

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro

/**
 * Ventanas de Tiresias encima de cualquier app (menú de Tiresias, acciones,
 * lista de elementos, búsqueda, etiquetas): diálogos de Material 3 en una
 * ventana de accesibilidad (`TYPE_ACCESSIBILITY_OVERLAY`), como hace TalkBack.
 * No cambian la app que está delante ni la pausan.
 *
 * Se manejan con Tiresias como cualquier diálogo (deslizar, doble toque,
 * Atrás los cierra). Al cerrarse se llama a [alCerrar], que devuelve el foco
 * al elemento de partida.
 */
class Ventanas(private val servicio: AccessibilityService) {
	private val principal = Handler(Looper.getMainLooper())
	private var abierta: AlertDialog? = null
	private var cortina: View? = null

	val hayAbierta: Boolean get() = abierta?.isShowing == true

	/**
	 * El contexto de las ventanas: el del propio servicio (es el que tiene permiso
	 * para poner ventanas de accesibilidad), con el tema y el idioma de Tiresias.
	 */
	private fun contexto(): Context = ContextThemeWrapper(servicio, R.style.Tema_Tiresias).apply {
		applyOverrideConfiguration(Idioma.envolver(servicio).resources.configuration)
	}

	private fun mostrar(d: AlertDialog, alCerrar: () -> Unit) {
		try {
			d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
			d.setOnDismissListener {
				if (abierta === d) abierta = null
				alCerrar()
			}
			abierta?.dismiss()
			abierta = d
			d.show()
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo abrir la ventana de Tiresias", e)
			alCerrar()
		}
	}

	/** Una lista de opciones; [alElegir] recibe la posición elegida; [alCancelar], si se cierra sin elegir. */
	fun lista(titulo: String, opciones: List<String>, alCancelar: () -> Unit = {}, alElegir: (Int) -> Unit) = principal.post {
		var elegida = -1
		val d = MaterialAlertDialogBuilder(contexto())
			.setTitle(titulo)
			.setItems(opciones.toTypedArray()) { _, i -> elegida = i }
			.setNegativeButton(R.string.cancelar, null)
			.create()
		mostrar(d) {
			if (elegida >= 0) alElegir(elegida) else alCancelar()
		}
	}

	/** Pide un texto (buscar, etiquetar). [alAceptar] recibe lo escrito. */
	fun pedirTexto(titulo: String, pista: String, inicial: String = "", alCancelar: () -> Unit = {}, alAceptar: (String) -> Unit) = principal.post {
		val c = contexto()
		val campo = TextInputEditText(c).apply {
			setText(inicial)
			inputType = InputType.TYPE_CLASS_TEXT
			setSingleLine()
			// La tecla Intro (o Buscar) del teclado acepta, como el botón Aceptar
			imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
			selectAll()
		}
		val caja = TextInputLayout(c).apply {
			hint = pista
			addView(campo)
		}
		val margen = (20 * c.resources.displayMetrics.density).toInt()
		val marco = FrameLayout(c).apply {
			setPadding(margen, margen / 2, margen, 0)
			addView(caja)
		}
		var aceptado: String? = null
		val d = MaterialAlertDialogBuilder(c)
			.setTitle(titulo)
			.setView(marco)
			.setPositiveButton(R.string.aceptar) { _, _ -> aceptado = campo.text?.toString().orEmpty() }
			.setNegativeButton(R.string.cancelar, null)
			.create()
		d.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
		d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
		// Arriba de la pantalla: Android no sube una ventana de accesibilidad al abrirse el teclado, y centrada
		// sus botones quedaban debajo del teclado (el doble toque en Aceptar o Cancelar no hacía nada)
		d.window?.let { w ->
			w.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
			w.attributes = w.attributes.apply { y = (24 * c.resources.displayMetrics.density).toInt() }
		}
		campo.setOnEditorActionListener { _, _, _ ->
			aceptado = campo.text?.toString().orEmpty()
			d.dismiss()
			true
		}
		mostrar(d) {
			val texto = aceptado
			if (texto != null) alAceptar(texto) else alCancelar()
		}
		campo.requestFocus()
	}

	/** Un texto largo para leer (resultado de la IA, del OCR, el historial). Se puede copiar. */
	fun mensaje(titulo: String, texto: String, alCerrar: () -> Unit = {}, alCopiar: ((String) -> Unit)? = null) = principal.post {
		val b = MaterialAlertDialogBuilder(contexto())
			.setTitle(titulo)
			.setMessage(texto)
			.setPositiveButton(R.string.cerrar, null)
		if (alCopiar != null) b.setNeutralButton(R.string.copiar) { _, _ -> alCopiar(texto) }
		mostrar(b.create(), alCerrar)
	}

	fun cerrar() = principal.post { abierta?.dismiss() }

	// --- Cortina ---------------------------------------------------------------------------

	val cortinaPuesta: Boolean get() = cortina != null

	/**
	 * Cortina de pantalla: negro encima de todo, para que no se vea lo que hay
	 * (privacidad) y para ahorrar batería en pantallas OLED. No recibe toques ni
	 * foco, así que no impide desbloquear ni usar nada; deja un píxel libre
	 * (algunos sistemas dejan de mandar eventos con una ventana a pantalla
	 * completa encima, arreglo de TalkBack). La quita el modo seguro siempre.
	 */
	fun ponerCortina(poner: Boolean) = principal.post {
		val wm = servicio.getSystemService(WindowManager::class.java) ?: return@post
		if (poner && cortina == null) {
			val v = View(servicio).apply {
				setBackgroundColor(Color.BLACK)
				importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
			}
			val metricas = servicio.resources.displayMetrics
			val params = WindowManager.LayoutParams(
				metricas.widthPixels, metricas.heightPixels * 2,
				WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
					WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				// Negra del todo pero no «opaca» para Android: una ventana opaca de pantalla completa deja tapada la de
				// debajo, y una app tapada no puede mantener la pantalla encendida (un vídeo, el GPS: Backtalk 136)
				PixelFormat.TRANSLUCENT,
			).apply {
				gravity = Gravity.TOP or Gravity.START
				x = 1
				y = -metricas.heightPixels / 2
			}
			try {
				wm.addView(v, params)
				cortina = v
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudo poner la cortina", e)
			}
		} else if (!poner) {
			cortina?.let { runCatching { wm.removeView(it) } }
			cortina = null
		}
	}

	companion object {
		private const val ETIQUETA = "Ventanas"
	}
}
