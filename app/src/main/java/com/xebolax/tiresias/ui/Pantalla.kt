package com.xebolax.tiresias.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.configurarBarra
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.accesibilidad.ponerMenuLateral
import com.xebolax.tiresias.accesibilidad.recorribleConLector
import com.xebolax.tiresias.base.Idioma

/**
 * Base de las pantallas de la app: idioma propio (independiente del sistema),
 * borde a borde con los márgenes del sistema aplicados y barra superior con
 * título, que es lo que TalkBack anuncia al entrar.
 */
abstract class ActividadBase : AppCompatActivity() {
	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(Idioma.envolver(base))
	}

	/**
	 * En el modo senior con código (ajustes protegidos), lo pide antes de dejar
	 * tocar nada: si se cancela o no es el bueno, la pantalla se cierra. Así nadie
	 * cambia los ajustes de una persona mayor sin querer.
	 */
	protected fun protegerConCodigo() {
		// Ya se dio bien en esta pantalla (se ha vuelto a crear al girarla o al cambiar de idioma): no se pide otra vez
		if (codigoAceptado || !com.xebolax.tiresias.base.ModoSenior.protegido(this)) return
		pedirTexto(getString(R.string.senior_codigo_pedir), getString(R.string.senior_codigo_pedir_pista), numerico = true, alCancelar = { finish() }) { codigo ->
			if (!com.xebolax.tiresias.base.ModoSenior.comprobar(this, codigo)) {
				avisar(getString(R.string.senior_codigo_mal))
				finish()
			} else codigoAceptado = true
		}
	}

	/** Si el código del modo senior ya se dio bien en esta pantalla (se guarda al rehacerla). */
	private var codigoAceptado = false

	override fun onCreate(savedInstanceState: android.os.Bundle?) {
		codigoAceptado = savedInstanceState?.getBoolean(CODIGO_ACEPTADO) == true
		super.onCreate(savedInstanceState)
	}

	override fun onSaveInstanceState(outState: android.os.Bundle) {
		super.onSaveInstanceState(outState)
		outState.putBoolean(CODIGO_ACEPTADO, codigoAceptado)
	}

	/**
	 * Crea la pantalla con barra y contenido desplazable; devuelve el contenedor
	 * donde se añaden las cosas. Con [alElegirEnMenu], la barra lleva «Abrir
	 * menú» con el menú lateral, en el que [destinoActual] es la opción actual.
	 */
	protected fun pantalla(titulo: String, conArriba: Boolean = true, destinoActual: Int = 0, alElegirEnMenu: ((Int) -> Unit)? = null): Pantalla {
		enableEdgeToEdge()
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		val barra = MaterialToolbar(this)
		raiz.addView(barra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
		val contenido = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			val m = dp(this@ActividadBase, 16)
			setPadding(m, m / 2, m, m)
		}
		val desplazable = ScrollView(this).recorribleConLector().apply {
			isFillViewport = true
			addView(contenido)
		}
		raiz.addView(desplazable, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
		if (alElegirEnMenu != null) {
			// La barra se configura antes del menú lateral: si no, «Abrir menú» no abriría el cajón
			configurarBarra(barra, titulo, conArriba = false)
			setContentView(ponerMenuLateral(raiz, barra, R.menu.menu_lateral, destinoActual, R.drawable.ic_menu, alElegirEnMenu))
		} else {
			setContentView(raiz)
			configurarBarra(barra, titulo, conArriba)
		}
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
			v.updatePadding(left = barras.left, top = barras.top, right = barras.right, bottom = barras.bottom)
			WindowInsetsCompat.CONSUMED
		}
		return Pantalla(this, contenido)
	}

	private companion object {
		const val CODIGO_ACEPTADO = "codigo_senior_aceptado"
	}
}

/** Construye una pantalla accesible en código: encabezados, párrafos, botones de 48 dp como mínimo y tarjetas de estado. */
class Pantalla(private val contexto: Context, val contenedor: LinearLayout) {

	fun encabezado(texto: String): TextView = TextView(contexto).apply {
		text = texto
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
		ViewCompat.setAccessibilityHeading(this, true)
		setPadding(0, dp(contexto, 20), 0, dp(contexto, 8))
		contenedor.addView(this)
	}

	fun parrafo(texto: CharSequence): TextView = TextView(contexto).apply {
		text = texto
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		setPadding(0, dp(contexto, 4), 0, dp(contexto, 4))
		contenedor.addView(this)
	}

	/** Un texto que cambia (estado): región viva educada, se anuncia sin mover el foco. */
	fun estadoVivo(texto: CharSequence = ""): TextView = parrafo(texto).apply {
		ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
	}

	fun boton(texto: String, accion: () -> Unit): MaterialButton = MaterialButton(contexto).apply {
		text = texto
		minHeight = dp(contexto, 56)
		setOnClickListener { accion() }
		contenedor.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(contexto, 8) })
	}

	fun botonSecundario(texto: String, accion: () -> Unit): MaterialButton = MaterialButton(contexto, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
		text = texto
		minHeight = dp(contexto, 56)
		setOnClickListener { accion() }
		contenedor.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(contexto, 8) })
	}

	/**
	 * Una fila de estado de una sola parada: el estado va delante en el texto
	 * («Listo: …», «Falta: …»), no solo con color. Si tiene [accion], la fila
	 * entera es el botón que lo arregla.
	 */
	fun estado(texto: String, bien: Boolean?, accion: (() -> Unit)? = null): MaterialCardView {
		val prefijo = contexto.getString(when (bien) {
			true -> R.string.ui_listo
			false -> R.string.ui_falta
			null -> R.string.ui_opcional
		})
		val color = contexto.getColor(when (bien) {
			true -> R.color.estado_bien
			false -> R.color.estado_mal
			null -> R.color.estado_aviso
		})
		val tarjeta = MaterialCardView(contexto).apply {
			strokeColor = color
			strokeWidth = dp(contexto, 2)
			minimumHeight = dp(contexto, 56)
			if (accion != null) {
				isClickable = true
				isFocusable = true
				setOnClickListener { accion() }
			}
		}
		val linea = TextView(contexto).apply {
			text = "$prefijo $texto"
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
			val m = dp(contexto, 12)
			setPadding(m, m, m, m)
			importantForAccessibility = if (accion != null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
		}
		if (accion != null) tarjeta.contentDescription = linea.text
		tarjeta.addView(linea)
		contenedor.addView(tarjeta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(contexto, 8) })
		return tarjeta
	}

	fun separador() {
		contenedor.addView(View(contexto), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(contexto, 16)))
	}

	fun vaciar() = contenedor.removeAllViews()

	fun tamanoTexto(v: TextView, sp: Float) = v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
}
