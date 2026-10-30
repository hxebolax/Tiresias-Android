package com.xebolax.tiresias.ui

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import com.google.android.material.checkbox.MaterialCheckBox
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.accionesAccesibles
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.lectorActivo
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.nucleo.Listas
import com.xebolax.tiresias.servicio.PuenteEscritorio
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Elegir y ordenar una lista del lector: el menú de Tiresias o los controles de
 * lectura. Cada opción es una casilla: marcada, está en la lista (en su orden);
 * desmarcada, no. Las marcadas tienen en el menú de acciones «Subir», «Bajar»,
 * «Al principio» y «Al final»; al moverlas, el foco se queda en ellas.
 */
class ActividadOrdenar : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes
	private val lista by lazy { intent.getStringExtra(EXTRA_LISTA) ?: LISTA_MENU }
	private val esMenu get() = lista == LISTA_MENU

	/** Los nombres internos (Accion o ControlLectura) en el orden elegido. */
	private var elegidas = mutableListOf<String>()
	private val todas: List<String> by lazy {
		when (lista) {
			LISTA_MENU, LISTA_CIRCULAR -> Accion.entries.filter { it !in Listas.FUERA_DEL_MENU && it != Accion.MENU_CIRCULAR && it !in PuenteEscritorio.ACCIONES_OCULTAS }.map { it.name }
			LISTA_PARTES -> com.xebolax.tiresias.nucleo.Parte.entries.map { it.name }
			LISTA_ESTADO -> com.xebolax.tiresias.nucleo.ParteEstado.entries.map { it.name }
			else -> ControlLectura.entries.map { it.name }
		}
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(titulo()))
		elegidas = when (lista) {
			LISTA_MENU -> ajustes.menuTiresias.map { it.name }
			LISTA_CIRCULAR -> ajustes.menuCircular.map { it.name }
			LISTA_PARTES -> ajustes.ordenPartes.map { it.name }
			LISTA_ESTADO -> ajustes.partesEstado.map { it.name }
			else -> ajustes.controlesLectura.map { it.name }
		}.toMutableList()
		construir(null)
	}

	private fun titulo(): Int = when (lista) {
		LISTA_MENU -> R.string.ordenar_menu_titulo
		LISTA_CIRCULAR -> R.string.ordenar_circular_titulo
		LISTA_PARTES -> R.string.ordenar_partes_titulo
		LISTA_ESTADO -> R.string.ordenar_estado_titulo
		else -> R.string.ordenar_controles_titulo
	}

	private fun nombre(clave: String): String {
		val prefijo = when (lista) { LISTA_MENU, LISTA_CIRCULAR -> "accion_"; LISTA_PARTES -> "parte_"; LISTA_ESTADO -> "estado_parte_"; else -> "control_" }
		val id = resources.getIdentifier(prefijo + clave.lowercase(), "string", packageName)
		return if (id != 0) getString(id) else clave.lowercase().replace('_', ' ')
	}

	private fun guardar() {
		when (lista) {
			LISTA_MENU -> ajustes.menuTiresias = elegidas.map { Accion.valueOf(it) }
			LISTA_CIRCULAR -> ajustes.menuCircular = elegidas.map { Accion.valueOf(it) }
			LISTA_ESTADO -> ajustes.partesEstado = elegidas.map { com.xebolax.tiresias.nucleo.ParteEstado.valueOf(it) }
			LISTA_PARTES -> {
				ajustes.ordenPartes = elegidas.map { com.xebolax.tiresias.nucleo.Parte.valueOf(it) }
				// Elegir las partes es el orden libre
				ajustes.p.edit().putString("orden", "libre").apply()
			}
			else -> ajustes.controlesLectura = elegidas.map { ControlLectura.valueOf(it) }
		}
		ServicioTiresias.instancia?.recargarAjustes()
	}

	/** Vuelve a hacer la lista; [enfocar] es la opción que se acaba de mover o marcar (el foco sigue en ella). */
	private fun construir(enfocar: String?) {
		p.vaciar()
		p.parrafo(getString(when (lista) {
			LISTA_MENU -> R.string.ordenar_menu_explicacion
			LISTA_CIRCULAR -> R.string.ordenar_circular_explicacion
			LISTA_PARTES -> R.string.ordenar_partes_explicacion
			LISTA_ESTADO -> R.string.ordenar_estado_explicacion
			else -> R.string.ordenar_controles_explicacion
		}))
		var destino: View? = null
		val fuera = todas.filter { it !in elegidas }.sortedBy { nombre(it) }
		for ((i, clave) in elegidas.withIndex()) destino = fila(clave, true, i, elegidas.size).takeIf { clave == enfocar } ?: destino
		if (fuera.isNotEmpty()) {
			p.encabezado(getString(R.string.ordenar_fuera))
			for (clave in fuera) destino = fila(clave, false, -1, 0).takeIf { clave == enfocar } ?: destino
		}
		p.separador()
		p.botonSecundario(getString(R.string.ordenar_restablecer)) {
			confirmar(getString(R.string.ordenar_restablecer_pregunta), getString(R.string.ordenar_restablecer)) {
				elegidas = when (lista) {
					LISTA_MENU -> Listas.MENU_TIRESIAS.map { it.name }
					LISTA_CIRCULAR -> Listas.MENU_CIRCULAR.map { it.name }
					LISTA_ESTADO -> Listas.ESTADO.map { it.name }
					LISTA_PARTES -> com.xebolax.tiresias.nucleo.Verbosidad.ORDEN_NORMAL.map { it.name }
					else -> ControlLectura.POR_DEFECTO.map { it.name }
				}.toMutableList()
				guardar()
				avisar(getString(R.string.ordenar_restablecido))
				construir(null)
			}
		}
		// El foco se queda en la opción que se acaba de cambiar (con un lector activo)
		destino?.let { v -> if (lectorActivo()) v.post { v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) } }
	}

	private fun fila(clave: String, dentro: Boolean, posicion: Int, total: Int): View {
		val caja = MaterialCheckBox(this).apply {
			text = if (dentro) getString(R.string.ordenar_fila_dentro, nombre(clave), posicion + 1, total) else nombre(clave)
			isChecked = dentro
			minHeight = dp(this@ActividadOrdenar, 48)
			setOnCheckedChangeListener { _, marcada ->
				if (marcada) elegidas.add(clave) else {
					// Los controles de lectura nunca se quedan vacíos, y lo que se dice siempre lleva el nombre
					if ((lista == LISTA_CONTROLES && elegidas.size == 1) || (lista == LISTA_PARTES && clave == "NOMBRE")) {
						isChecked = true
						avisar(getString(R.string.ordenar_minimo))
						return@setOnCheckedChangeListener
					}
					elegidas.remove(clave)
				}
				guardar()
				post { construir(clave) }
			}
		}
		if (dentro) {
			val acciones = buildList {
				if (posicion > 0) {
					add(getString(R.string.ordenar_subir) to { mover(clave, posicion - 1) })
					add(getString(R.string.ordenar_principio) to { mover(clave, 0) })
				}
				if (posicion < total - 1) {
					add(getString(R.string.ordenar_bajar) to { mover(clave, posicion + 1) })
					add(getString(R.string.ordenar_final) to { mover(clave, total - 1) })
				}
			}
			caja.accionesAccesibles(acciones)
			// Sin lector: mantener pulsado para subir o bajar
			caja.setOnLongClickListener {
				elegir(nombre(clave), acciones)
				true
			}
		}
		p.contenedor.addView(caja, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
		return caja
	}

	private fun mover(clave: String, a: Int) {
		elegidas.remove(clave)
		elegidas.add(a.coerceIn(0, elegidas.size), clave)
		guardar()
		avisar(getString(R.string.ordenar_movido, nombre(clave), a + 1))
		construir(clave)
	}

	companion object {
		const val EXTRA_LISTA = "lista"
		const val LISTA_MENU = "menu"
		const val LISTA_CONTROLES = "controles"
		const val LISTA_PARTES = "partes"
		const val LISTA_CIRCULAR = "circular"
		const val LISTA_ESTADO = "estado"
	}
}
