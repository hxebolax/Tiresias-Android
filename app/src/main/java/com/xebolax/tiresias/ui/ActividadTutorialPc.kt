package com.xebolax.tiresias.ui

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.lectorActivo
import com.xebolax.tiresias.remoto.GestosPc
import com.xebolax.tiresias.remoto.SuperficieControlPc
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * El tutorial del control de un PC desde el móvil: lecciones cortas, una por
 * pantalla, que llevan de cero (qué hace falta en el PC y en el móvil) a
 * manejar Windows entero con la pantalla de control: moverse, leer, las
 * ventanas, escribir, el menú de órdenes, la web, los objetos y el cursor de
 * revisión, y qué hacer cuando algo falla. Al final, una práctica: la pantalla
 * de control sin PC, en la que cada gesto se dice y no se manda.
 */
class ActividadTutorialPc : ActividadBase() {
	private lateinit var p: Pantalla
	private var leccion = 0

	private val lecciones = listOf(
		R.string.pct_1_titulo to R.string.pct_1,
		R.string.pct_2_titulo to R.string.pct_2,
		R.string.pct_3_titulo to R.string.pct_3,
		R.string.pct_4_titulo to R.string.pct_4,
		R.string.pct_5_titulo to R.string.pct_5,
		R.string.pct_6_titulo to R.string.pct_6,
		R.string.pct_7_titulo to R.string.pct_7,
		R.string.pct_8_titulo to R.string.pct_8,
		R.string.pct_9_titulo to R.string.pct_9,
		R.string.pct_10_titulo to R.string.pct_10,
		R.string.pct_11_titulo to R.string.pct_11,
		R.string.pct_12_titulo to R.string.pct_12,
		R.string.pct_13_titulo to R.string.pct_13,
		R.string.pct_14_titulo to R.string.pct_14,
	)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		leccion = (estado?.getInt("leccion") ?: intent.getIntExtra("leccion", 0)).coerceIn(0, lecciones.lastIndex)
		p = pantalla(getString(R.string.pc_tutorial))
		construir()
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("leccion", leccion)
	}

	/** Rehace la pantalla con la lección actual; al cambiar de lección, el foco va al encabezado para oírla. */
	private fun construir(alEncabezado: Boolean = false) {
		p.vaciar()
		val (titulo, texto) = lecciones[leccion]
		val encabezado = p.encabezado(getString(R.string.tut_paso, leccion + 1, lecciones.size, getString(titulo)))
		p.parrafo(getString(texto))
		when (leccion) {
			// La tabla de gestos entera, para repasarla
			5 -> for ((g, o) in GestosPc.TABLA) p.parrafo(getString(R.string.pc_gesto_y_orden, GestosPc.nombreGesto(this, g), getString(o.nombre)))
			lecciones.lastIndex -> {
				p.boton(getString(R.string.pc_practicar)) { practicar() }
				p.botonSecundario(getString(R.string.pc_abrir_control)) { startActivity(android.content.Intent(this, ActividadControlPc::class.java)) }
			}
		}
		if (leccion < lecciones.lastIndex) p.boton(getString(R.string.tut_siguiente)) { leccion++; construir(true) }
		if (leccion > 0) p.botonSecundario(getString(R.string.tut_anterior)) { leccion--; construir(true) }
		if (alEncabezado && lectorActivo()) encabezado.post { encabezado.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
	}

	/** La pantalla de control sin PC: cada gesto se dice y no se manda. Se quita con cuatro dedos, dos toques, o con las dos teclas de volumen. */
	private fun practicar() {
		val lector = ServicioTiresias.instancia?.lectorActual
		if (lector == null) {
			avisar(getString(R.string.nvda_sin_tiresias))
			return
		}
		lector.superficie.abrir(SuperficieControlPc(lector, null, practica = true))
	}

	companion object {
		/** La lección de la tabla de gestos (para abrirla desde la pantalla del PC). */
		const val LECCION_GESTOS = 5
	}
}
