package com.xebolax.tiresias.ui

import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Tutorial de Tiresias: lecciones cortas, una por pantalla, y una zona de
 * práctica (la ayuda de entrada: los gestos se dicen en vez de hacerse).
 * Inspirado en la app de práctica de gestos de appt.
 */
class ActividadTutorial : ActividadBase() {
	private lateinit var p: Pantalla
	private var leccion = 0

	private val lecciones = listOf(
		R.string.tut_1_titulo to R.string.tut_1,
		R.string.tut_2_titulo to R.string.tut_2,
		R.string.tut_3_titulo to R.string.tut_3,
		R.string.tut_4_titulo to R.string.tut_4,
		R.string.tut_5_titulo to R.string.tut_5,
		R.string.tut_6_titulo to R.string.tut_6,
		R.string.tut_7_titulo to R.string.tut_7,
		R.string.tut_8_titulo to R.string.tut_8,
	)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		leccion = estado?.getInt("leccion") ?: 0
		p = pantalla(getString(R.string.ui_tutorial))
		construir()
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("leccion", leccion)
	}

	private fun construir() {
		p.vaciar()
		val (titulo, texto) = lecciones[leccion]
		p.encabezado(getString(R.string.tut_paso, leccion + 1, lecciones.size, getString(titulo)))
		p.parrafo(getString(texto))
		if (leccion == 1) {
			// La lista de gestos asignados, para repasar
			val mapa = Ajustes(this).mapaGestos
			for (g in listOf(Gesto.DERECHA, Gesto.IZQUIERDA, Gesto.ARRIBA, Gesto.ABAJO, Gesto.DOBLE_TOQUE, Gesto.DOBLE_TOQUE_MANTENIDO, Gesto.ABAJO_IZQUIERDA, Gesto.ARRIBA_IZQUIERDA, Gesto.TOQUE_3, Gesto.TRIPLE_TOQUE_MANTENIDO_4)) {
				p.parrafo(getString(R.string.tut_gesto, ActividadGestos.nombreGesto(this, g), ActividadGestos.nombreAccion(this, mapa[g])))
			}
		}
		if (leccion == lecciones.lastIndex) {
			p.boton(getString(R.string.tut_practicar)) { practicar() }
		}
		if (leccion < lecciones.lastIndex) p.boton(getString(R.string.tut_siguiente)) { leccion++; construir() }
		if (leccion > 0) p.botonSecundario(getString(R.string.tut_anterior)) { leccion--; construir() }
	}

	/** La zona de práctica: la ayuda de entrada de Tiresias dice cada gesto sin hacerlo. Se sale con el mismo botón o con Tiresias+1. */
	private fun practicar() {
		val s = ServicioTiresias.instancia
		if (s == null) {
			p.parrafo(getString(R.string.tut_sin_servicio))
			return
		}
		s.alternarAyudaEntrada()
	}
}
