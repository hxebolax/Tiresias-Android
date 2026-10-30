package com.xebolax.tiresias.ui

import android.content.Intent
import android.os.Bundle
import com.xebolax.tiresias.R

/**
 * Experimentos: lo que ya funciona pero sigue en pruebas (la ayuda remota entre
 * dos móviles y el control de un PC con NVDA). Se dice claro que puede fallar o
 * cambiar, y desde aquí se entra a cada uno. El apartado solo sale en el menú
 * lateral si se ha elegido en Ajustes («Enseñar los experimentos en el menú»);
 * de fábrica no se enseña.
 */
class ActividadExperimentos : ActividadBase() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val p = pantalla(getString(R.string.ui_experimentos))
		p.parrafo(getString(R.string.exp_aviso))
		p.encabezado(getString(R.string.accion_ayuda_remota))
		p.parrafo(getString(R.string.exp_ayuda_remota_res))
		p.boton(getString(R.string.accion_ayuda_remota)) { startActivity(Intent(this, ActividadAyudaRemota::class.java)) }
		p.encabezado(getString(R.string.accion_control_pc))
		p.parrafo(getString(R.string.exp_control_pc_res))
		p.boton(getString(R.string.accion_control_pc)) { startActivity(Intent(this, ActividadControlPc::class.java)) }
	}
}
