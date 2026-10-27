package com.xebolax.tiresias.accesibilidad

import android.widget.ScrollView
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityViewCommand
import androidx.recyclerview.widget.RecyclerView
import com.xebolax.tiresias.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/*
 * Ayudantes de accesibilidad para Views (Material Components).
 *
 * Cadenas que usa: res/values/strings_accesibilidad.xml (cancelar, aceptar,
 * volver, deshacer, deshecho, copiado y los plurales horas, minutos y
 * segundos). Identificador que usa: res/values/ids_accesibilidad.xml
 * (acciones_accesibles).
 */

/** Una acción con nombre (menús, acciones de TalkBack): el texto que se oye y lo que hace. */
typealias Accion = Pair<String, () -> Unit>

/** ¿Está activo TalkBack u otro lector que explora por tacto? Úsalo solo para tiempos y autoocultado, nunca para cambiar la interfaz entera. */
fun Context.lectorActivo(): Boolean {
	val gestor = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
	return gestor.isEnabled && gestor.isTouchExplorationEnabled
}

fun dp(contexto: Context, valor: Int): Int = (valor * contexto.resources.displayMetrics.density).toInt()

/** Marca una vista como encabezado para la navegación por encabezados de TalkBack. Sin roleDescription: TalkBack lo diría dos veces. */
fun View.encabezado(): View {
	ViewCompat.setAccessibilityHeading(this, true)
	return this
}

/**
 * Lee un texto con el lector de pantalla; si no hay lector activo, lo muestra brevemente.
 * Con 'mostrar' se ve siempre; entonces TalkBack ya lee el aviso en pantalla y no se anuncia otra vez.
 *
 * Solo para confirmaciones pasajeras («Copiado», «Cancelado»). Lo importante no
 * va nunca solo en un aviso: va en la pantalla (región viva, error del campo,
 * Snackbar con acción). announceForAccessibility está obsoleto desde API 36:
 * si el proyecto apunta a esa versión, prefiere una región viva o un cambio de
 * stateDescription y deja esta función como último recurso.
 */
fun Activity.avisar(texto: String, mostrar: Boolean = !lectorActivo()) {
	if (mostrar) {
		Toast.makeText(this, texto, if (texto.length > 60) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
		if (lectorActivo()) return
	}
	@Suppress("DEPRECATION")
	window.decorView.announceForAccessibility(texto)
}

/** Configura la barra superior con título y el botón Arriba con nombre. El título de la Activity es lo que TalkBack anuncia al entrar. */
fun AppCompatActivity.configurarBarra(barra: MaterialToolbar, titulo: String, conArriba: Boolean = true) {
	setSupportActionBar(barra)
	supportActionBar?.title = titulo
	if (conArriba) {
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		barra.setNavigationContentDescription(R.string.volver)
		// Arriba hace lo mismo que Atrás dentro de la app (y respeta los OnBackPressedCallback activos)
		barra.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
	}
	title = titulo
}

// --- Acciones de TalkBack -----------------------------------------------------------

/**
 * Pone acciones personalizadas en el menú de acciones de TalkBack de una
 * vista, sustituyendo las anteriores. Imprescindible en filas de RecyclerView:
 * sin quitar las del bind anterior, se acumulan al reciclar la fila.
 * Para respetar lo que el usuario elige en cada zona, usa la versión con
 * ZonaAcciones (AccionesTalkBack.kt).
 */
fun View.accionesAccesibles(acciones: List<Accion>) {
	@Suppress("UNCHECKED_CAST")
	(getTag(R.id.acciones_accesibles) as? List<Int>)?.forEach { ViewCompat.removeAccessibilityAction(this, it) }
	val ids = acciones.map { (etiqueta, accion) ->
		ViewCompat.addAccessibilityAction(this, etiqueta, AccessibilityViewCommand { _, _ -> accion(); true })
	}
	setTag(R.id.acciones_accesibles, ids)
}

/** Cambia el nombre que TalkBack dice para el doble toque («Toca dos veces para <texto>»). El clic sigue haciendo lo mismo. */
fun View.etiquetaPulsar(texto: String) {
	ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK, texto, null)
}

/**
 * Pulsación larga con nombre: TalkBack dice «Toca dos veces y mantén pulsado
 * para <texto>» y la ofrece también en su menú de acciones.
 */
fun View.accionAlMantener(texto: String, accion: () -> Unit) {
	setOnLongClickListener {
		accion()
		true
	}
	ViewCompat.replaceAccessibilityAction(this, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK, texto) { _, _ ->
		accion()
		true
	}
}

/**
 * Hace que TalkBack anuncie la vista como botón (vistas propias pulsables que
 * no heredan de Button). Ojo: sustituye el AccessibilityDelegate que tuviera.
 */
fun View.comoBoton() {
	ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
		override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
			super.onInitializeAccessibilityNodeInfo(host, info)
			info.className = Button::class.java.name
		}
	})
}

/**
 * Deja el contenedor desplazable como lo necesita TalkBack para recorrer la
 * pantalla entera deslizando a derecha e izquierda: enfocable (como viene de
 * serie). Si no lo es, TalkBack no lo desplaza al pasar del último elemento
 * visible y vuelve al principio, y solo se llega al resto desplazando con dos
 * dedos. No es una parada propia: TalkBack se para en lo de dentro. Sin el
 * resaltado gris del foco de teclado, que taparía la pantalla entera.
 */
fun ScrollView.recorribleConLector(): ScrollView = apply {
	isFocusable = true
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
}

/**
 * La lista no es un elemento para TalkBack (sus filas sí): vacía, TalkBack se
 * pararía en ella y solo diría «Lista». El teclado sigue llegando a las filas.
 */
fun RecyclerView.sinFocoPropio() {
	isFocusable = false
	isFocusableInTouchMode = false
}

/** Copia al portapapeles. Android 13 y posteriores ya avisan al copiar; antes, se avisa aquí. */
fun Context.copiar(texto: String, etiqueta: String = getString(R.string.app_name)) {
	val portapapeles = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
	portapapeles.setPrimaryClip(ClipData.newPlainText(etiqueta, texto))
	if (Build.VERSION.SDK_INT < 33 && this is Activity) avisar(getString(R.string.copiado))
}

// --- Campos de texto ------------------------------------------------------------------

/**
 * Campo de texto con etiqueta flotante, para formularios creados en código.
 * El nombre accesible sale del hint del TextInputLayout: nunca contentDescription
 * en el EditText. Errores con contenedor.error = "...", diciendo cómo corregir.
 */
fun Context.campoTexto(pista: String, inicial: String = ""): Pair<TextInputLayout, EditText> {
	// 56 dp de alto, como el campo de texto de Material 3 (nunca menos de 48 para tocarlo bien).
	// Ojo: isSingleLine o setLines quitan esta altura mínima; quien los use debe volver a ponerla
	val campo = TextInputEditText(this).apply {
		setText(inicial)
		minHeight = dp(this@campoTexto, 56)
	}
	val contenedor = TextInputLayout(this).apply {
		hint = pista
		addView(campo)
	}
	return contenedor to campo
}

// --- Diálogos (MaterialAlertDialogBuilder aísla el foco de TalkBack) ----------------

/** Lista de opciones; al elegir una se ejecuta su acción. Es también el menú de «mantener pulsado»: con TODAS las acciones. */
fun Context.elegir(titulo: String, opciones: List<Accion>) {
	MaterialAlertDialogBuilder(this)
		.setTitle(titulo)
		.setItems(opciones.map { it.first }.toTypedArray()) { _, i -> opciones[i].second() }
		.setNegativeButton(R.string.cancelar, null)
		.show()
}

/** Elegir un valor de una lista con el actual marcado (TalkBack dice «marcado» solo en él). */
fun Context.elegirUno(titulo: String, nombres: List<String>, actual: Int, alElegir: (Int) -> Unit) {
	MaterialAlertDialogBuilder(this)
		.setTitle(titulo)
		.setSingleChoiceItems(nombres.toTypedArray(), actual) { d, i ->
			d.dismiss()
			alElegir(i)
		}
		.setNegativeButton(R.string.cancelar, null)
		.show()
}

/** Elegir varias opciones de una lista (casillas) y Aceptar; Cancelar no cambia nada. */
fun Context.elegirVarios(titulo: String, nombres: List<String>, marcadas: BooleanArray, alAceptar: (BooleanArray) -> Unit) {
	val estado = marcadas.copyOf()
	MaterialAlertDialogBuilder(this)
		.setTitle(titulo)
		.setMultiChoiceItems(nombres.toTypedArray(), estado) { _, i, si -> estado[i] = si }
		.setPositiveButton(R.string.aceptar) { _, _ -> alAceptar(estado) }
		.setNegativeButton(R.string.cancelar, null)
		.show()
}

/** Pide un texto. alAceptar recibe el texto sin espacios sobrantes. Intro en el teclado acepta (si no es multilínea). */
fun Context.pedirTexto(
	titulo: String,
	pista: String,
	inicial: String = "",
	numerico: Boolean = false,
	multilinea: Boolean = false,
	telefono: Boolean = false,
	alCancelar: (() -> Unit)? = null,
	alAceptar: (String) -> Unit,
) {
	val campo = TextInputEditText(this).apply {
		setText(inicial)
		setSelection(inicial.length)
		inputType = when {
			numerico -> InputType.TYPE_CLASS_NUMBER
			telefono -> InputType.TYPE_CLASS_PHONE
			multilinea -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
			else -> InputType.TYPE_CLASS_TEXT
		}
		if (multilinea) {
			minLines = 3
			maxLines = 8
		}
	}
	val contenedor = TextInputLayout(this).apply {
		hint = pista
		addView(campo)
	}
	// El contenedor recalcula la altura del campo: con 2 dp más de relleno arriba y abajo llega a 48 dp
	campo.post { campo.setPaddingRelative(campo.paddingStart, campo.paddingTop + dp(this, 2), campo.paddingEnd, campo.paddingBottom + dp(this, 2)) }
	val marco = FrameLayout(this).apply {
		val margen = dp(this@pedirTexto, 20)
		setPadding(margen, margen / 2, margen, 0)
		addView(contenedor)
	}
	val dialogo = MaterialAlertDialogBuilder(this)
		.setTitle(titulo)
		.setView(marco)
		.setPositiveButton(R.string.aceptar) { _, _ -> alAceptar(campo.text?.toString()?.trim().orEmpty()) }
		.setNegativeButton(R.string.cancelar) { _, _ -> alCancelar?.invoke() }
		.apply { if (alCancelar != null) setOnCancelListener { alCancelar() } }
		.create()
	if (!multilinea) campo.setOnEditorActionListener { _, _, _ ->
		alAceptar(campo.text?.toString()?.trim().orEmpty())
		dialogo.dismiss()
		true
	}
	dialogo.setOnShowListener { campo.requestFocus() }
	dialogo.show()
}

/** Pregunta de confirmación. textoSi dice la acción («Borrar», «Salir sin guardar»), no «Sí». */
fun Context.confirmar(mensaje: String, textoSi: String, alConfirmar: () -> Unit) {
	MaterialAlertDialogBuilder(this)
		.setMessage(mensaje)
		.setPositiveButton(textoSi) { _, _ -> alConfirmar() }
		.setNegativeButton(R.string.cancelar, null)
		.show()
}

/** Mensaje informativo con un botón Aceptar (el texto se puede recorrer con TalkBack y copiar). */
fun Context.mensaje(titulo: String, texto: String, alCerrar: (() -> Unit)? = null) {
	MaterialAlertDialogBuilder(this)
		.setTitle(titulo)
		.setMessage(texto)
		.setPositiveButton(R.string.aceptar, null)
		.apply { if (alCerrar != null) setOnDismissListener { alCerrar() } }
		.show()
}

/**
 * Aviso con «Deshacer» tras un cambio en bloque. Dura lo que pida el sistema
 * para quien necesita más tiempo (TalkBack, «Tiempo para actuar»). Nunca es la
 * única forma de deshacer algo importante.
 */
fun Activity.conDeshacer(texto: String, deshacer: () -> Unit) {
	val vista = findViewById<View>(android.R.id.content) ?: return
	val gestor = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
	val ms = if (Build.VERSION.SDK_INT >= 29)
		gestor.getRecommendedTimeoutMillis(10_000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS)
	else 10_000
	Snackbar.make(vista, texto, ms)
		.setAction(R.string.deshacer) {
			deshacer()
			avisar(getString(R.string.deshecho), mostrar = false)
		}
		.show()
}

// --- Tiempos dichos para TalkBack ----------------------------------------------------

/** «1 hora 2 minutos 5 segundos» para TalkBack (la vista puede mostrar «1:02:05»). */
fun Context.tiempoHablado(ms: Long, conSegundos: Boolean = true): String {
	val total = (ms / 1000).coerceAtLeast(0)
	val h = total / 3600
	val m = (total % 3600) / 60
	val s = total % 60
	val partes = mutableListOf<String>()
	if (h > 0) partes += resources.getQuantityString(R.plurals.horas, h.toInt(), h.toInt())
	if (m > 0) partes += resources.getQuantityString(R.plurals.minutos, m.toInt(), m.toInt())
	if ((s > 0 && conSegundos) || partes.isEmpty()) partes += resources.getQuantityString(R.plurals.segundos, s.toInt(), s.toInt())
	return partes.joinToString(" ")
}
