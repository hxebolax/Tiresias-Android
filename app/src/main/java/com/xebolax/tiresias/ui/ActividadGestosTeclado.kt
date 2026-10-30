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

	private fun fila(g: GestoBraille, a: AccionTeclado): String = getString(R.string.gestos_braille_fila, GestosTeclado.nombre(this, g), a.nombre(this))

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.gestos_braille_explicacion))
		val m = mapa
		p.encabezado(getString(R.string.gestos_braille_cabecera_dedos))
		for (g in GestosTeclado.TODOS.filter { it.punto == 0 }) p.botonSecundario(fila(g, m[g.clave] ?: AccionTeclado.NINGUNA)) { cambiar(g) }
		p.encabezado(getString(R.string.gestos_braille_cabecera_puntos))
		for (g in GestosTeclado.TODOS.filter { it.punto > 0 }) p.botonSecundario(fila(g, m[g.clave] ?: AccionTeclado.NINGUNA)) { cambiar(g) }
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
		// En un gesto de un punto, «Nada» es hacer lo del gesto de un dedo; en los demás, nada de verdad
		val acciones = AccionTeclado.entries.toList()
		val actual = acciones.indexOf(mapa[g.clave] ?: AccionTeclado.NINGUNA)
		elegirUno(GestosTeclado.nombre(this, g), acciones.map { it.nombre(this) }, actual) { i ->
			val nuevo = mapa + (g.clave to acciones[i])
			ajustes.gestosTecladoBraille = GestosTeclado.escribir(nuevo)
			avisar(getString(R.string.gestos_braille_cambiado, GestosTeclado.nombre(this, g), acciones[i].nombre(this)))
			construir()
		}
	}
}
