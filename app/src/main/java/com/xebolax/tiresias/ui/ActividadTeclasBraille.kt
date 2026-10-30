package com.xebolax.tiresias.ui

import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.braille.Puntos
import com.xebolax.tiresias.nucleo.braille.TecladoLinea
import com.xebolax.tiresias.servicio.PuenteEscritorio
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Las combinaciones de la línea braille (espacio y puntos), como la pantalla de
 * Gestos: cada fila es una sola parada, «Espacio y puntos 1 2: atrás», y al
 * pulsarla se cambia la acción o se quita. Se pueden añadir combinaciones
 * nuevas escribiendo sus puntos y volver a las de fábrica.
 */
class ActividadTeclasBraille : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.acordes_titulo))
		construir()
	}

	private val mapa: Map<Int, Accion> get() = TecladoLinea.leerAcordes(ajustes.acordesBraille)

	private fun guardar(nuevo: Map<Int, Accion>) {
		ajustes.acordesBraille = TecladoLinea.escribirAcordes(nuevo)
		ServicioTiresias.instancia?.recargarAjustes()
	}

	private fun fila(celda: Int, a: Accion): String =
		getString(R.string.acordes_fila, Puntos.describir(celda), ActividadGestos.nombreAccion(this, a))

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.acordes_explicacion))
		for ((celda, a) in mapa.entries.sortedBy { Puntos.nombre(it.key).length * 100 + it.key }) {
			p.botonSecundario(fila(celda, a)) { cambiar(celda) }
		}
		p.separador()
		p.boton(getString(R.string.acordes_anadir)) { anadir() }
		p.botonSecundario(getString(R.string.acordes_restablecer)) {
			confirmar(getString(R.string.acordes_restablecer_pregunta), getString(R.string.acordes_restablecer)) {
				guardar(TecladoLinea.ACORDES)
				avisar(getString(R.string.acordes_restablecidas))
				construir()
			}
		}
	}

	private fun cambiar(celda: Int) {
		val actual = mapa[celda] ?: return
		elegir(fila(celda, actual), listOf(
			getString(R.string.acordes_cambiar) to { elegirAccion(celda) },
			getString(R.string.acordes_quitar) to {
				guardar(mapa - celda)
				avisar(getString(R.string.acordes_quitada, Puntos.describir(celda)))
				construir()
			},
		))
	}

	private fun elegirAccion(celda: Int) {
		val acciones = Accion.entries.filter { it != Accion.MODO_SEGURO && it != Accion.NINGUNA && it !in PuenteEscritorio.ACCIONES_OCULTAS }
		val actual = acciones.indexOf(mapa[celda]).coerceAtLeast(-1)
		elegirUno(getString(R.string.acordes_elegir, Puntos.describir(celda)), acciones.map { ActividadGestos.nombreAccion(this, it) }, actual) { i ->
			guardar(mapa + (celda to acciones[i]))
			construir()
		}
	}

	/** Una combinación nueva: sus puntos escritos («1 2 5» o «125») y luego su acción. */
	private fun anadir() {
		pedirTexto(getString(R.string.acordes_anadir), getString(R.string.acordes_puntos_pista), numerico = true) { texto ->
			val cifras = texto.filter { !it.isWhitespace() }
			val celda = Puntos.de(cifras)
			when {
				cifras.isEmpty() || cifras.any { it !in '1'..'8' } || celda == 0 -> avisar(getString(R.string.acordes_puntos_mal))
				// Espacio y 7, y espacio y 8, son borrar e intro: no se pueden cambiar
				celda == Puntos.P7 || celda == Puntos.P8 -> avisar(getString(R.string.acordes_reservada))
				else -> elegirAccion(celda)
			}
		}
	}
}
