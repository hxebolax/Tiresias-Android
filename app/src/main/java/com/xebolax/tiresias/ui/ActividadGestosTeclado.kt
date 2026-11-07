package com.xebolax.tiresias.ui

import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.braille.AccionTeclado
import com.xebolax.tiresias.braille.GestoBraille
import com.xebolax.tiresias.braille.GestosTeclado
import com.xebolax.tiresias.nucleo.Accion

/**
 * Los gestos del teclado braille en pantalla, como la pantalla de Gestos: cada
 * fila es una sola parada («Un dedo hacia la izquierda: Borrar») y al pulsarla
 * se elige otra acción. Primero los de uno, dos y tres dedos; luego los de un
 * punto (Can Talk), que de fábrica hacen lo del gesto de un dedo.
 */
class ActividadGestosTeclado : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		protegerConCodigo()
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.gestos_braille_titulo))
		construir()
	}

	private val mapa: Map<String, AccionTeclado> get() = GestosTeclado.leer(ajustes.gestosTecladoBraille)
	private val mapaLector: Map<String, Accion> get() = GestosTeclado.leerLector(ajustes.gestosTecladoBraille)

	/** El nombre de una acción del lector, con «Lector» delante para no confundirla con las del teclado. */
	private fun nombreLector(a: Accion): String {
		val id = resources.getIdentifier("accion_" + a.name.lowercase(), "string", packageName)
		return getString(R.string.gestos_braille_del_lector, if (id != 0) getString(id) else a.name.lowercase().replace('_', ' '))
	}

	private fun nombreDe(g: GestoBraille): String = mapaLector[g.clave]?.let { nombreLector(it) } ?: (mapa[g.clave] ?: AccionTeclado.NINGUNA).nombre(this)

	private fun fila(g: GestoBraille): String = getString(R.string.gestos_braille_fila, GestosTeclado.nombre(this, g), nombreDe(g))

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.gestos_braille_explicacion))
		p.encabezado(getString(R.string.gestos_braille_cabecera_dedos))
		for (g in GestosTeclado.TODOS.filter { it.punto == 0 }) p.botonSecundario(fila(g)) { cambiar(g) }
		p.encabezado(getString(R.string.gestos_braille_cabecera_puntos))
		for (g in GestosTeclado.TODOS.filter { it.punto > 0 }) p.botonSecundario(fila(g)) { cambiar(g) }
		p.separador()
		p.boton(getString(R.string.gestos_braille_restablecer)) {
			confirmar(getString(R.string.gestos_braille_restablecer_pregunta), getString(R.string.gestos_braille_restablecer)) {
				ajustes.gestosTecladoBraille = ""
				avisar(getString(R.string.gestos_braille_restablecidos))
				construir()
			}
		}
	}

	private fun cambiar(g: GestoBraille) {
		// En un gesto de un punto, «Nada» es hacer lo del gesto de un dedo; en los demás, nada de verdad. Detrás
		// de las del teclado, las del lector que valen con el teclado puesto (para tenerlas a mano)
		val propias = AccionTeclado.entries.toList()
		val delLector = com.xebolax.tiresias.braille.MenusTeclado.LECTOR_PERMITIDAS
		val nombres = propias.map { it.nombre(this) } + delLector.map { nombreLector(it) }
		val actual = mapaLector[g.clave]?.let { propias.size + delLector.indexOf(it) } ?: propias.indexOf(mapa[g.clave] ?: AccionTeclado.NINGUNA)
		elegirUno(GestosTeclado.nombre(this, g), nombres, actual) { i ->
			val lector = mapaLector.toMutableMap()
			val teclado = mapa.toMutableMap()
			if (i < propias.size) {
				lector.remove(g.clave)
				teclado[g.clave] = propias[i]
			} else lector[g.clave] = delLector[i - propias.size]
			ajustes.gestosTecladoBraille = GestosTeclado.escribir(teclado, lector)
			avisar(getString(R.string.gestos_braille_cambiado, GestosTeclado.nombre(this, g), nombres[i]))
			construir()
		}
	}
}
