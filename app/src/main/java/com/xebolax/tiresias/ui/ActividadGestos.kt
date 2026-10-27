package com.xebolax.tiresias.ui

import android.content.Context
import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Gestos de Tiresias: cada gesto con lo que hace, y elegir otra acción para
 * cualquiera. Los gestos de emergencia no se pueden cambiar (se dice en su fila).
 * Cada fila es una sola parada: «Deslizar a la derecha: siguiente elemento».
 */
class ActividadGestos : ActividadBase() {
	private lateinit var p: Pantalla

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		protegerConCodigo()
		p = pantalla(getString(R.string.ui_gestos))
		construir()
	}

	private fun construir() {
		p.vaciar()
		val ajustes = Ajustes(this)
		val mapa = ajustes.mapaGestos
		p.parrafo(getString(R.string.gestos_explicacion))
		var dedos = -1
		for (g in Gesto.entries) {
			if (g.dedos != dedos) {
				dedos = g.dedos
				p.encabezado(getString(when (dedos) {
					1 -> R.string.gestos_un_dedo
					2 -> R.string.gestos_dos_dedos
					3 -> R.string.gestos_tres_dedos
					4 -> R.string.gestos_cuatro_dedos
					else -> R.string.gestos_otros
				}))
			}
			val texto = getString(R.string.gestos_fila, nombreGesto(this, g), nombreAccion(this, mapa[g]))
			if (g in MapaGestos.EMERGENCIA) {
				p.parrafo(texto + ". " + getString(R.string.gestos_emergencia))
				continue
			}
			p.botonSecundario(texto) { elegir(g) }
		}
		p.separador()
		p.botonSecundario(getString(R.string.gestos_sencillos)) {
			confirmar(getString(R.string.gestos_sencillos_pregunta), getString(R.string.gestos_sencillos)) {
				ajustes.mapaGestos = MapaGestos.SENCILLO
				ServicioTiresias.instancia?.recargarAjustes()
				avisar(getString(R.string.gestos_sencillos_puestos))
				construir()
			}
		}
		p.boton(getString(R.string.gestos_restablecer)) {
			confirmar(getString(R.string.gestos_restablecer_pregunta), getString(R.string.gestos_restablecer)) {
				ajustes.mapaGestos = MapaGestos.POR_DEFECTO
				ServicioTiresias.instancia?.recargarAjustes()
				avisar(getString(R.string.gestos_restablecidos))
				construir()
			}
		}
	}

	private fun elegir(g: Gesto) {
		val ajustes = Ajustes(this)
		val acciones = Accion.entries.filter { it != Accion.MODO_SEGURO }
		val actual = acciones.indexOf(ajustes.mapaGestos[g]).coerceAtLeast(0)
		elegirUno(getString(R.string.gestos_elegir, nombreGesto(this, g)), acciones.map { nombreAccion(this, it) }, actual) { i ->
			ajustes.mapaGestos = ajustes.mapaGestos.mas(mapOf(g to acciones[i]))
			ServicioTiresias.instancia?.recargarAjustes()
			construir()
		}
	}

	companion object {
		fun nombreGesto(c: Context, g: Gesto): String {
			val t = Idioma.envolver(c)
			val id = t.resources.getIdentifier("gesto_" + g.name.lowercase(), "string", c.packageName)
			return if (id != 0) t.getString(id) else g.name.lowercase()
		}

		fun nombreAccion(c: Context, a: Accion): String {
			val t = Idioma.envolver(c)
			val id = t.resources.getIdentifier("accion_" + a.name.lowercase(), "string", c.packageName)
			return if (id != 0) t.getString(id) else a.name.lowercase()
		}
	}
}
