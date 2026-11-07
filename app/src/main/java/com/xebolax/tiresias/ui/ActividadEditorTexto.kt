package com.xebolax.tiresias.ui

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.accionesAccesibles
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.configurarBarra
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.copiar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.EdicionPartes
import com.xebolax.tiresias.nucleo.Partes
import com.xebolax.tiresias.nucleo.Segmentos
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * El editor de texto, como el editor de Jieshuo: el texto de un campo en una
 * pantalla entera, en un campo grande de varias líneas, para escribir con el
 * teclado y recorrerlo con el modo edición de Tiresias (que se pone solo al
 * abrir). Arriba, «Guardar» y «Menú del editor» con todo lo de editar:
 * deshacer y rehacer (propios, de muchos pasos), seleccionar, copiar, cortar,
 * pegar, buscar y reemplazar, ir a una línea, dónde está el cursor, contar,
 * la fecha y la hora, y la edición por partes. Lo mismo está en las acciones
 * del campo (menú de acciones de Tiresias).
 *
 * Al guardar, el texto vuelve al campo de la app (con [EdicionPartes], solo en
 * memoria: nunca se escribe en ningún archivo). Si lo enfocado no se editaba
 * (un mensaje), se trabaja con una copia que se puede copiar.
 */
class ActividadEditorTexto : ActividadBase() {
	private lateinit var t: EdicionPartes.Trabajo
	private lateinit var campo: EditText
	/** Lo de antes de cada cambio, para deshacer, con dónde estaba el cursor. */
	private val deshacer = ArrayDeque<Pair<String, Int>>()
	private val rehacer = ArrayDeque<Pair<String, Int>>()
	/** Mientras se pone el texto desde el editor (deshacer, reemplazar): no cuenta como un cambio nuevo. */
	private var poniendo = false
	/** Cuándo se apuntó el último paso de deshacer: lo escrito de seguido es un solo paso. */
	private var ultimoPaso = 0L
	private var buscado = ""
	/** El texto ha pasado a la edición por partes: no se olvida al cerrar esta pantalla. */
	private var pasado = false

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val trabajo = EdicionPartes.trabajo
		if (trabajo == null) {
			finish()
			return
		}
		t = trabajo
		construir(estado)
		onBackPressedDispatcher.addCallback(this) { salir() }
	}

	override fun onDestroy() {
		// El texto se olvida al cerrar (no al girar la pantalla)
		if (isFinishing && !pasado && ::t.isInitialized && EdicionPartes.trabajo === t) EdicionPartes.trabajo = null
		super.onDestroy()
	}

	override fun onResume() {
		super.onResume()
		// Con Tiresias activo, el modo edición se pone solo en el campo, como en Jieshuo: los gestos recorren el
		// texto por caracteres, palabras, líneas y párrafos y seleccionan. Se sale con su gesto, como siempre
		campo.postDelayed({ if (!isFinishing) entrarEnModoEdicion() }, ESPERA_MODO_MS)
	}

	private fun entrarEnModoEdicion() {
		val l = ServicioTiresias.instancia?.lectorActual ?: return
		l.manejador.post {
			if (l.modoEdicion.activo) return@post
			val n = runCatching { l.raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull() ?: return@post
			if (n.packageName?.toString() != packageName || !n.isEditable) return@post
			l.enfocar(n, null)
			l.modoEdicion.entrar()
		}
	}

	private fun construir(estado: Bundle?) {
		enableEdgeToEdge()
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		val barra = MaterialToolbar(this)
		raiz.addView(barra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
		val m = dp(this, 16)
		// Arriba, los dos botones: guardar (o copiar, si no hay campo al que volver) y el menú con todo lo demás
		val botones = LinearLayout(this).apply {
			orientation = LinearLayout.HORIZONTAL
			setPadding(m, 0, m, 0)
		}
		fun boton(texto: String, accion: () -> Unit) = MaterialButton(this).apply {
			text = texto
			minHeight = dp(this@ActividadEditorTexto, 56)
			setOnClickListener { accion() }
			botones.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(this@ActividadEditorTexto, 8) })
		}
		if (t.editable) boton(getString(R.string.editor_guardar)) { guardar() } else boton(getString(R.string.editor_copiar_todo)) { copiarTodo() }
		boton(getString(R.string.editor_menu)) { menu() }
		raiz.addView(botones)
		campo = TextInputEditText(this).apply {
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
			isSingleLine = false
			gravity = Gravity.TOP or Gravity.START
			minLines = 6
			setText(estado?.getString(TEXTO) ?: t.texto)
			val cursor = estado?.getInt(CURSOR, -1)?.takeIf { it in 0..(text?.length ?: 0) } ?: (text?.length ?: 0)
			setSelection(cursor)
		}
		val contenedor = TextInputLayout(this).apply {
			hint = getString(if (t.editable) R.string.editor_campo else R.string.editor_campo_copia)
			addView(campo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		}
		raiz.addView(contenedor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(m, m / 2, m, m) })
		setContentView(raiz)
		configurarBarra(barra, getString(R.string.editor_titulo))
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
			v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
			WindowInsetsCompat.CONSUMED
		}
		campo.addTextChangedListener(object : TextWatcher {
			private var antes = ""
			private var cursorAntes = 0
			override fun beforeTextChanged(s: CharSequence?, inicio: Int, cuantos: Int, despues: Int) {
				antes = s?.toString().orEmpty()
				cursorAntes = campo.selectionEnd.coerceAtLeast(0)
			}
			override fun onTextChanged(s: CharSequence?, inicio: Int, antes: Int, cuantos: Int) = Unit
			override fun afterTextChanged(s: Editable?) {
				if (poniendo) return
				val ahora = android.os.SystemClock.uptimeMillis()
				// Lo escrito de seguido (sin parar más de un segundo) es un solo paso de deshacer
				if (deshacer.isEmpty() || ahora - ultimoPaso > PASO_MS) {
					deshacer.addLast(antes to cursorAntes)
					while (deshacer.size > MAXIMO_DESHACER) deshacer.removeFirst()
				}
				ultimoPaso = ahora
				rehacer.clear()
				t.texto = s?.toString().orEmpty()
			}
		})
		// Lo mismo que el menú, en las acciones del campo (Tiresias las ofrece en su menú de acciones); «Deshacer» y
		// «Rehacer» llevan el nombre que buscan los gestos de Tiresias para deshacer y rehacer
		campo.accionesAccesibles(listOf(
			getString(R.string.acc_deshacer) to { deshacerUno() },
			getString(R.string.acc_rehacer) to { rehacerUno() },
			getString(R.string.editor_donde) to { donde() },
			getString(R.string.editor_menu) to { menu() },
		) + if (t.editable) listOf(getString(R.string.editor_guardar) to { guardar() }) else emptyList())
		campo.requestFocus()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		if (::campo.isInitialized) {
			outState.putString(TEXTO, campo.text.toString())
			outState.putInt(CURSOR, campo.selectionEnd)
		}
	}

	private val texto: String get() = campo.text?.toString().orEmpty()
	private val selInicio: Int get() = minOf(campo.selectionStart, campo.selectionEnd).coerceAtLeast(0)
	private val selFin: Int get() = maxOf(campo.selectionStart, campo.selectionEnd).coerceAtLeast(0)

	/** El menú del editor: todo lo que se puede hacer con el texto. */
	private fun menu() {
		val hay = selFin > selInicio
		val opciones = mutableListOf<Pair<String, () -> Unit>>()
		if (t.editable) opciones += getString(R.string.editor_guardar) to { guardar() }
		if (deshacer.isNotEmpty()) opciones += getString(R.string.acc_deshacer) to { deshacerUno() }
		if (rehacer.isNotEmpty()) opciones += getString(R.string.acc_rehacer) to { rehacerUno() }
		opciones += getString(R.string.editor_donde) to { donde() }
		opciones += getString(R.string.editor_seleccionar_todo) to { seleccionarTodo() }
		opciones += getString(R.string.editor_seleccionar_linea) to { seleccionarLinea() }
		if (hay) {
			opciones += getString(R.string.editor_copiar) to { copiarSeleccion(cortar = false) }
			opciones += getString(R.string.editor_cortar) to { copiarSeleccion(cortar = true) }
			opciones += getString(R.string.editor_borrar_seleccion) to { reemplazarSeleccion("", getString(R.string.editor_borrado)) }
		}
		opciones += getString(R.string.editor_pegar) to { pegar() }
		opciones += getString(R.string.editor_buscar) to { buscar() }
		if (buscado.isNotEmpty()) opciones += getString(R.string.editor_buscar_siguiente) to { buscarSiguiente(buscado) }
		opciones += getString(R.string.editor_reemplazar) to { reemplazar() }
		opciones += getString(R.string.editor_ir_a_linea) to { irALinea() }
		opciones += getString(R.string.editor_principio) to { moverCursor(0, R.string.edicion_principio) }
		opciones += getString(R.string.editor_final) to { moverCursor(texto.length, R.string.edicion_final) }
		opciones += getString(R.string.editor_contar) to { contar() }
		opciones += getString(R.string.editor_fecha_hora) to { insertar(fechaHora()) }
		opciones += getString(R.string.editor_borrar_linea) to { borrarLinea() }
		opciones += getString(R.string.editor_copiar_todo) to { copiarTodo() }
		opciones += getString(R.string.editor_por_partes) to { porPartes() }
		opciones += getString(R.string.editor_salir) to { salir() }
		elegir(getString(R.string.editor_menu), opciones)
	}

	/** Pone [nuevo] en el campo con el cursor en [cursor], como un paso que se puede deshacer. */
	private fun poner(nuevo: String, cursor: Int, apuntar: Boolean = true) {
		if (apuntar) {
			deshacer.addLast(texto to selFin)
			while (deshacer.size > MAXIMO_DESHACER) deshacer.removeFirst()
			rehacer.clear()
			ultimoPaso = 0L
		}
		poniendo = true
		campo.setText(nuevo)
		campo.setSelection(cursor.coerceIn(0, nuevo.length))
		poniendo = false
		t.texto = nuevo
	}

	private fun deshacerUno() {
		val (anterior, cursor) = deshacer.removeLastOrNull() ?: run {
			avisar(getString(R.string.editor_nada_deshacer))
			return
		}
		rehacer.addLast(texto to selFin)
		poner(anterior, cursor, apuntar = false)
		avisar(getString(R.string.partes_deshecho))
	}

	private fun rehacerUno() {
		val (siguiente, cursor) = rehacer.removeLastOrNull() ?: run {
			avisar(getString(R.string.editor_nada_rehacer))
			return
		}
		deshacer.addLast(texto to selFin)
		poner(siguiente, cursor, apuntar = false)
		avisar(getString(R.string.partes_rehecho))
	}

	/** Línea, columna y carácter del cursor, de cuántos. */
	private fun donde() {
		val s = texto
		val c = selFin.coerceIn(0, s.length)
		val lineas = Segmentos.lineas(s)
		val i = lineas.indexOfLast { it.inicio <= c }.coerceAtLeast(0)
		val columna = c - (lineas.getOrNull(i)?.inicio ?: 0) + 1
		val sel = if (selFin > selInicio) resources.getQuantityString(R.plurals.editor_seleccionados_plural, selFin - selInicio, selFin - selInicio) + ". " else ""
		avisar(sel + getString(R.string.editor_donde_res, i + 1, lineas.size.coerceAtLeast(1), columna, c, s.length))
	}

	private fun contar() {
		val s = texto
		val palabras = Regex("[\\p{L}\\p{N}]+").findAll(s).count()
		val parrafos = Segmentos.parrafos(s).size
		mensaje(getString(R.string.editor_contar), getString(R.string.editor_contar_res, s.length, palabras, Segmentos.lineas(s).size, parrafos))
	}

	private fun seleccionarTodo() {
		campo.setSelection(0, texto.length)
		avisar(getString(R.string.editor_todo_seleccionado))
	}

	private fun seleccionarLinea() {
		val s = texto
		val linea = Segmentos.lineas(s).lastOrNull { it.inicio <= selFin } ?: return
		campo.setSelection(linea.inicio, linea.fin)
		avisar(getString(R.string.editor_linea_seleccionada, linea.texto.ifBlank { getString(R.string.partes_vacia) }))
	}

	private fun copiarSeleccion(cortar: Boolean) {
		val a = selInicio
		val b = selFin
		if (b <= a) return
		copiar(texto.substring(a, b))
		if (cortar) reemplazarSeleccion("", getString(R.string.editor_cortado)) else avisar(getString(R.string.editor_copiado))
	}

	private fun reemplazarSeleccion(nuevo: String, aviso: String? = null) {
		val s = texto
		val a = selInicio.coerceIn(0, s.length)
		val b = selFin.coerceIn(a, s.length)
		poner(s.substring(0, a) + nuevo + s.substring(b), a + nuevo.length)
		aviso?.let { avisar(it) }
	}

	private fun insertar(nuevo: String) = reemplazarSeleccion(nuevo, getString(R.string.editor_insertado, nuevo))

	private fun pegar() {
		val portapapeles = getSystemService(android.content.ClipboardManager::class.java)
		val pegado = runCatching { portapapeles?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()
		if (pegado.isNullOrEmpty()) {
			avisar(getString(R.string.editor_nada_pegar))
			return
		}
		reemplazarSeleccion(pegado, getString(R.string.editor_pegado))
	}

	private fun moverCursor(donde: Int, aviso: Int) {
		campo.setSelection(donde.coerceIn(0, texto.length))
		avisar(getString(aviso))
	}

	private fun buscar() {
		pedirTexto(getString(R.string.editor_buscar), getString(R.string.partes_buscar_pista), buscado) { b ->
			if (b.isEmpty()) return@pedirTexto
			buscado = b
			buscarSiguiente(b)
		}
	}

	/** Lo siguiente a partir del cursor (sin distinguir mayúsculas); al final, da la vuelta. Lo encontrado queda seleccionado. */
	private fun buscarSiguiente(b: String) {
		val s = texto
		val i = s.indexOf(b, selFin, ignoreCase = true).takeIf { it >= 0 } ?: s.indexOf(b, 0, ignoreCase = true)
		if (i < 0) {
			avisar(getString(R.string.partes_no_esta, b))
			return
		}
		campo.setSelection(i, i + b.length)
		val linea = Segmentos.lineas(s).lastOrNull { it.inicio <= i }
		avisar(linea?.texto?.takeIf { it.isNotBlank() } ?: b)
	}

	private fun reemplazar() {
		pedirTexto(getString(R.string.editor_reemplazar), getString(R.string.partes_reemplazar_buscar), buscado) { b ->
			if (b.isEmpty()) return@pedirTexto
			buscado = b
			pedirTexto(getString(R.string.partes_reemplazar_por_titulo, b), getString(R.string.partes_reemplazar_por)) { nuevo ->
				elegir(getString(R.string.editor_reemplazar), listOf(
					getString(R.string.partes_sin_distinguir) to { hacerReemplazo(b, nuevo, false) },
					getString(R.string.partes_distinguiendo) to { hacerReemplazo(b, nuevo, true) },
				))
			}
		}
	}

	private fun hacerReemplazo(b: String, nuevo: String, distinguir: Boolean) {
		val (resultado, veces) = Partes.reemplazar(texto, b, nuevo, distinguir)
		if (veces == 0) {
			avisar(getString(R.string.partes_no_esta, b))
			return
		}
		poner(resultado, selFin.coerceAtMost(resultado.length))
		avisar(resources.getQuantityString(R.plurals.partes_reemplazadas, veces, veces))
	}

	private fun irALinea() {
		val total = Segmentos.lineas(texto).size.coerceAtLeast(1)
		pedirTexto(getString(R.string.editor_ir_a_linea), getString(R.string.editor_ir_a_linea_pista, total), numerico = true) { n ->
			val k = n.toIntOrNull() ?: return@pedirTexto
			val lineas = Segmentos.lineas(texto)
			val linea = lineas.getOrNull((k - 1).coerceIn(0, (lineas.size - 1).coerceAtLeast(0)))
			if (linea == null) {
				campo.setSelection(0)
				return@pedirTexto
			}
			campo.setSelection(linea.inicio)
			avisar(getString(R.string.editor_en_linea, lineas.indexOf(linea) + 1, linea.texto.ifBlank { getString(R.string.partes_vacia) }))
		}
	}

	private fun borrarLinea() {
		val s = texto
		val linea = Segmentos.lineas(s).lastOrNull { it.inicio <= selFin } ?: return
		// Con su salto de línea, si lo tiene (si no, el de antes): no quedan dos saltos seguidos
		val fin = if (linea.fin < s.length && s[linea.fin] == '\n') linea.fin + 1 else linea.fin
		val inicio = if (fin == linea.fin && linea.inicio > 0 && s[linea.inicio - 1] == '\n') linea.inicio - 1 else linea.inicio
		poner(s.substring(0, inicio) + s.substring(fin), inicio)
		avisar(getString(R.string.editor_linea_borrada))
	}

	private fun fechaHora(): String {
		val formato = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.LONG, java.text.DateFormat.SHORT, com.xebolax.tiresias.base.Idioma.efectivo(this))
		return formato.format(java.util.Date())
	}

	private fun copiarTodo() {
		copiar(texto)
		avisar(getString(R.string.editor_todo_copiado))
	}

	/** Pasa el texto de ahora a la edición por partes (lo que se guarde allí vuelve al mismo campo). */
	private fun porPartes() {
		t.texto = texto
		runCatching { startActivity(android.content.Intent(this, ActividadEdicionPartes::class.java)) }
			.onSuccess {
				pasado = true
				finish()
			}
			.onFailure { Registro.w(ETIQUETA, "No se pudo abrir la edición por partes", it) }
	}

	/** Devuelve el texto al campo. Si la app lo ha cambiado mientras tanto, se pregunta antes de taparlo. */
	private fun guardar() {
		val final = if (t.unaLinea) texto.replace('\n', ' ') else texto
		if (t.maximo in 1 until final.length) {
			mensaje(getString(R.string.editor_titulo), getString(R.string.partes_demasiado_largo, final.length, t.maximo))
			return
		}
		val n = t.nodo
		val ahora = n?.let { nodo ->
			runCatching { nodo.refresh() }
			if (android.os.Build.VERSION.SDK_INT >= 26 && nodo.isShowingHintText) "" else nodo.text?.toString()
		}
		if (n == null || ahora == null) {
			copiar(final)
			mensaje(getString(R.string.editor_titulo), getString(R.string.partes_campo_perdido)) { terminar() }
			return
		}
		if (ahora != t.original) {
			confirmar(getString(R.string.partes_campo_cambiado), getString(R.string.partes_sustituir)) { t.guardar(final); terminar() }
			return
		}
		t.guardar(final)
		terminar()
	}

	/** Salir: con cambios sin guardar, se pregunta qué hacer con ellos (Atrás también). */
	private fun salir() {
		if (!t.editable || texto == t.original) {
			terminar()
			return
		}
		elegir(getString(R.string.partes_sin_guardar), listOf(
			getString(R.string.editor_guardar) to { guardar() },
			getString(R.string.partes_copiar_y_salir) to {
				copiar(texto)
				terminar()
			},
			getString(R.string.partes_salir_sin_guardar) to { terminar() },
			getString(R.string.partes_seguir) to { },
		))
	}

	private fun terminar() {
		// El modo edición era de este campo: al irse, se acaba
		ServicioTiresias.instancia?.lectorActual?.let { l -> l.manejador.post { if (l.modoEdicion.activo) l.modoEdicion.salir(decirlo = false) } }
		EdicionPartes.trabajo = null
		finish()
	}

	private companion object {
		const val ETIQUETA = "ActividadEditorTexto"
		const val TEXTO = "texto"
		const val CURSOR = "cursor"
		const val MAXIMO_DESHACER = 200
		/** Lo escrito sin parar más de esto es un solo paso de deshacer. */
		const val PASO_MS = 1_000L
		/** Lo que se espera a que Tiresias diga la pantalla antes de poner el modo edición. */
		const val ESPERA_MODO_MS = 900L
	}
}
