package com.xebolax.tiresias.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.campoTexto
import com.xebolax.tiresias.accesibilidad.lectorActivo
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.braille.AccionTeclado
import com.xebolax.tiresias.braille.CeldasBraille
import com.xebolax.tiresias.braille.ModoPracticaBraille
import com.xebolax.tiresias.braille.Objetivo
import com.xebolax.tiresias.braille.Practica
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * El tutorial del teclado braille en pantalla: de quien no ha escrito nunca en
 * braille a quien quiere usarlo con soltura. Una lección por pantalla (qué es,
 * cómo sujetar el móvil, calibrar, las letras por grupos, tildes, mayúsculas y
 * números, signos, gestos, el menú, dictar, recorrer y corregir lo escrito,
 * emojis, palabras, práctica libre, la cortina y usarlo de verdad), y casi
 * todas con su práctica: la misma superficie que el teclado,
 * encima de todo, que pide cada cosa, dice si está bien y, si no, qué se ha
 * escrito y qué tocaba. La última lección tiene un campo para probar el
 * teclado de verdad.
 */
class ActividadTutorialBraille : ActividadBase() {
	private lateinit var p: Pantalla
	private var leccion = 0

	private class Leccion(val titulo: Int, val texto: Int, val practica: (() -> Practica)? = null)

	private val lecciones = listOf(
		Leccion(R.string.tb_1_titulo, R.string.tb_1),
		Leccion(R.string.tb_2_titulo, R.string.tb_2) { Practica.Puntos },
		Leccion(R.string.tb_3_titulo, R.string.tb_3) { Practica.Calibrar },
		Leccion(R.string.tb_4_titulo, R.string.tb_4) { letras("abcde", "daecb") },
		Leccion(R.string.tb_5_titulo, R.string.tb_5) { letras("fghij", "jhfig") },
		Leccion(R.string.tb_6_titulo, R.string.tb_6) { letras("klmnopqrst", "ospkt") },
		Leccion(R.string.tb_7_titulo, R.string.tb_7) { letras("uvxyzñw", "zñuwy") },
		Leccion(R.string.tb_8_titulo, R.string.tb_8) { letras("áéíóúü", "óáüé") },
		Leccion(R.string.tb_9_titulo, R.string.tb_9) { escribir(listOf("B", "M", "1", "5", "0", "25"), conPista = true) },
		Leccion(R.string.tb_10_titulo, R.string.tb_10) { letras(".,;:?!-", "?.!,") },
		Leccion(R.string.tb_11_titulo, R.string.tb_11) {
			Practica.Gestos(listOf(
				AccionTeclado.ESPACIO, AccionTeclado.BORRAR, AccionTeclado.INTRO, AccionTeclado.BORRAR_PALABRA, AccionTeclado.MENU,
				AccionTeclado.DICTADO, AccionTeclado.CURSOR_ATRAS, AccionTeclado.CURSOR_ADELANTE, AccionTeclado.CAMBIAR_UNIDAD, AccionTeclado.MENU_LECTOR,
			))
		},
		Leccion(R.string.tb_12_titulo, R.string.tb_12),
		Leccion(R.string.tb_lector_titulo, R.string.tb_lector),
		Leccion(R.string.tb_dictar_titulo, R.string.tb_dictar),
		Leccion(R.string.tb_corregir_titulo, R.string.tb_corregir),
		Leccion(R.string.tb_emojis_titulo, R.string.tb_emojis) { Practica.Emojis },
		Leccion(R.string.tb_13_titulo, R.string.tb_13) { escribir(listOf("sol", "casa", "niño", "café", "Tiresias", "2026"), conPista = false) },
		Leccion(R.string.tb_14_titulo, R.string.tb_14) { Practica.Libre },
		Leccion(R.string.tb_cortina_titulo, R.string.tb_cortina),
		Leccion(R.string.tb_15_titulo, R.string.tb_15),
	)

	/** Las letras nuevas de una lección, con sus puntos, y después un repaso sin decirlos (salvo al fallar). */
	private fun letras(nuevas: String, repaso: String): Practica =
		Practica.Escribir(nuevas.map { objetivo(it.toString(), true) } + repaso.map { objetivo(it.toString(), false) })

	private fun escribir(textos: List<String>, conPista: Boolean): Practica = Practica.Escribir(textos.map { objetivo(it, conPista) })

	private fun objetivo(texto: String, pista: Boolean): Objetivo =
		Objetivo(texto, CeldasBraille.de(texto) ?: error("El tutorial pide algo que el teclado braille no escribe: $texto"), pista)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		leccion = (estado?.getInt("leccion") ?: intent.getIntExtra("leccion", 0)).coerceIn(0, lecciones.lastIndex)
		p = pantalla(getString(R.string.tb_titulo))
		construir()
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("leccion", leccion)
	}

	override fun onResume() {
		super.onResume()
		// Al volver de los ajustes del sistema (activar el teclado), la última lección dice cómo está
		if (leccion == lecciones.lastIndex) construir()
	}

	/** Rehace la pantalla con la lección actual; al cambiar de lección, el foco va al encabezado para oírla. */
	private fun construir(alEncabezado: Boolean = false) {
		p.vaciar()
		val l = lecciones[leccion]
		val encabezado = p.encabezado(getString(R.string.tut_paso, leccion + 1, lecciones.size, getString(l.titulo)))
		p.parrafo(getString(l.texto))
		l.practica?.let { crear -> p.boton(getString(R.string.tb_practicar)) { practicar(crear()) } }
		if (leccion == lecciones.lastIndex) ultimaLeccion()
		if (leccion < lecciones.lastIndex) p.boton(getString(R.string.tut_siguiente)) { leccion++; construir(true) }
		if (leccion > 0) p.botonSecundario(getString(R.string.tut_anterior)) { leccion--; construir(true) }
		if (alEncabezado && lectorActivo()) encabezado.post { encabezado.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
	}

	/** Activarlo, cambiar a él y un campo para probarlo de verdad. */
	private fun ultimaLeccion() {
		val lector = ServicioTiresias.instancia?.lectorActual
		val activado = lector?.braille?.tecladoActivado() ?: false
		p.estado(getString(if (activado) R.string.tb_estado_activado else R.string.tb_estado_sin_activar), activado) { abrirAjustesTeclados() }
		p.botonSecundario(getString(R.string.tb_activar)) { abrirAjustesTeclados() }
		val (contenedor, campo) = campoTexto(getString(R.string.tb_campo))
		p.contenedor.addView(contenedor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
		p.boton(getString(R.string.tb_cambiar)) {
			val l = ServicioTiresias.instancia?.lectorActual
			if (l == null) {
				avisar(getString(R.string.tb_sin_tiresias))
				return@boton
			}
			if (!l.braille.tecladoActivado()) {
				avisar(getString(R.string.braille_teclado_inactivo))
				return@boton
			}
			campo.requestFocus()
			// Android 11 o posterior: Tiresias cambia de teclado solo; antes, la lista de teclados del sistema
			if (!l.braille.abrirTeclado()) getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
			getSystemService(InputMethodManager::class.java)?.showSoftInput(campo, 0)
		}
		p.botonSecundario(getString(R.string.tb_gestos)) { startActivity(Intent(this, ActividadGestosTeclado::class.java)) }
	}

	private fun abrirAjustesTeclados() {
		try {
			startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudieron abrir los ajustes de teclados", e)
			avisar(getString(R.string.tb_sin_ajustes_teclados))
		}
	}

	private fun practicar(practica: Practica) {
		val lector = ServicioTiresias.instancia?.lectorActual
		if (lector == null) {
			avisar(getString(R.string.tb_sin_tiresias))
			return
		}
		lector.superficie.abrir(ModoPracticaBraille(lector, practica))
	}

	private companion object {
		const val ETIQUETA = "TutorialBraille"
	}
}
