package com.xebolax.tiresias.voz

import android.content.Context
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.recursos.Recursos
import com.xebolax.tiresias.recursos.VocesNeuronales

/** Las voces que se pueden elegir: las de Tiresias, las neuronales descargadas y los motores del sistema. */
object VocesDisponibles {
	/** Cada voz con su nombre, en el orden en que se ofrecen: `(id, nombre)`. */
	fun disponibles(c: Context): List<Pair<String, String>> {
		val t = Idioma.envolver(c)
		val lista = ArrayList<Pair<String, String>>()
		val instalados = MotorSistema.instalados(c)
		// La que use el sistema: si se cambia el motor en los ajustes del móvil, Tiresias lo sigue
		val delSistema = MotorSistema.predeterminado(c)
		val ahora = delSistema?.let { p -> instalados.firstOrNull { it.first == p }?.second } ?: t.getString(R.string.aj_motor_espeak_corto)
		lista += Ajustes.MOTOR_DEL_SISTEMA to t.getString(R.string.aj_motor_del_sistema, ahora)
		lista += Ajustes.MOTOR_ESPEAK to t.getString(R.string.aj_motor_espeak)
		lista += Ajustes.MOTOR_PICO to t.getString(R.string.aj_motor_pico)
		if (BibliotecaEloquence.disponible(c)) lista += Ajustes.MOTOR_ELOQUENCE to t.getString(R.string.aj_motor_eloquence)
		for (v in VocesNeuronales.CATALOGO) {
			if (Recursos.instalado(c, v.recurso)) lista += (MotorPiper.PREFIJO + v.id) to t.getString(R.string.aj_motor_neuronal, v.nombre)
		}
		for ((paquete, nombre) in instalados) {
			lista += paquete to t.getString(if (paquete == delSistema) R.string.aj_motor_sistema_elegido else R.string.aj_motor_sistema, nombre)
		}
		return lista
	}

	/**
	 * Las voces por las que pasa el control «Voz» (y el gesto de cambiar de voz):
	 * las elegidas en Ajustes, Voz, o todas si no se ha elegido ninguna. La de
	 * ahora siempre está, para poder seguir desde ella.
	 */
	fun delControl(c: Context, ajustes: Ajustes): List<String> {
		val todas = disponibles(c).map { it.first }
		val elegidas = ajustes.vocesControl
		if (elegidas.isEmpty()) return todas
		return todas.filter { it in elegidas || it == ajustes.motor }
	}
}
