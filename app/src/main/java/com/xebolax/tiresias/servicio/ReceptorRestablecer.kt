package com.xebolax.tiresias.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xebolax.tiresias.base.Restablecer

/**
 * «Restablecer Tiresias» desde el PC con adb (para dejarlo como recién
 * instalado sin quitarlo de los servicios de accesibilidad). Solo lo puede
 * mandar quien tenga el permiso DUMP, es decir, adb: ninguna app normal.
 *
 * `adb shell am broadcast -a com.xebolax.tiresias.RESTABLECER -n com.xebolax.tiresias/.servicio.ReceptorRestablecer`
 */
class ReceptorRestablecer : BroadcastReceiver() {
	override fun onReceive(c: Context, i: Intent) {
		if (i.action != ACCION) return
		Restablecer.todo(c)
		ServicioTiresias.instancia?.alRestablecer()
	}

	companion object {
		const val ACCION = "com.xebolax.tiresias.RESTABLECER"
	}
}
