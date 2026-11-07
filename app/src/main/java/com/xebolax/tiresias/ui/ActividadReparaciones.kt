package com.xebolax.tiresias.ui

import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.arbol.Reparador
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Lo que la reparación automática ha arreglado en cada app ([Reparador]): una
 * fila por reparación, con la pantalla, qué se hizo y el elemento; al tocarla,
 * se deshace (y no se vuelve a hacer sola). Debajo de cada app, deshacer todo
 * lo suyo; al final, deshacer todo.
 */
class ActividadReparaciones : ActividadBase() {
	private lateinit var p: Pantalla
	/** El del lector si está en marcha (así lo deshecho vale al momento); si no, uno propio con el mismo archivo. */
	private val reparador: Reparador by lazy { ServicioTiresias.instancia?.lectorActual?.reparador ?: Reparador(this) }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.reparaciones_titulo))
		construir()
	}

	private fun nombreApp(paquete: String): String =
		runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrDefault(paquete)

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.reparaciones_explica))
		val todas = reparador.todas()
		if (todas.isEmpty()) {
			p.parrafo(getString(R.string.reparaciones_vacio))
			return
		}
		for ((paquete, deLaApp) in todas.groupBy { it.paquete }.toSortedMap(compareBy { nombreApp(it) })) {
			val app = nombreApp(paquete)
			p.encabezado(app)
			for (g in deLaApp) {
				val pantalla = g.actividad.substringAfterLast('.').ifEmpty { getString(R.string.reparaciones_toda_app) }
				p.botonSecundario(getString(R.string.reparaciones_fila, pantalla, getString(Reparador.nombre(g.tipo)), g.id)) {
					confirmar(getString(R.string.reparaciones_deshacer) + "?", getString(R.string.reparaciones_deshacer)) {
						reparador.deshacer(g)
						avisoAlLector()
						construir()
						avisar(getString(R.string.reparaciones_deshecha))
					}
				}
			}
			p.botonSecundario(getString(R.string.reparaciones_deshacer_app, app)) {
				reparador.borrar(paquete)
				avisoAlLector()
				construir()
				avisar(getString(R.string.reparaciones_deshechas))
			}
		}
		p.separador()
		p.boton(getString(R.string.reparaciones_deshacer_todo)) {
			confirmar(getString(R.string.reparaciones_deshacer_todo_pregunta), getString(R.string.reparaciones_deshacer_todo)) {
				reparador.borrar()
				avisoAlLector()
				construir()
				avisar(getString(R.string.reparaciones_deshechas))
			}
		}
	}

	/** El lector rehace el orden de lectura con las reglas que quedan. */
	private fun avisoAlLector() {
		val l = ServicioTiresias.instancia?.lectorActual ?: return
		if (l.reparador !== reparador) l.reparador.olvidar()
		ServicioTiresias.instancia?.recargarAjustes()
		l.manejador.post { l.navegador.invalidarTodo() }
	}
}
