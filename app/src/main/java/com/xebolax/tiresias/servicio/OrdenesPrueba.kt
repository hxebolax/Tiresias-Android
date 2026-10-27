package com.xebolax.tiresias.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto

/**
 * Órdenes de prueba, **solo en la variante de pruebas** (`BuildConfig.PRUEBAS`):
 * simulan gestos y acciones de Tiresias desde `adb`, porque `adb shell input`
 * no pasa por el filtro de accesibilidad y no sirve para probar un lector.
 *
 * ```
 * adb shell am broadcast -a com.xebolax.tiresias.pruebas.ORDEN -p com.xebolax.tiresias.pruebas --es gesto DERECHA
 * adb shell am broadcast -a com.xebolax.tiresias.pruebas.ORDEN -p com.xebolax.tiresias.pruebas --es accion LEER_DESDE_ARRIBA
 * ```
 *
 * Lo que dice Tiresias queda en el registro con la etiqueta `TiresiasHabla`
 * (también solo en esta variante). En la versión de publicación esto no existe.
 */
class OrdenesPrueba(private val alGesto: (Gesto) -> Unit, private val alAccion: (Accion) -> Unit) : BroadcastReceiver() {

	override fun onReceive(c: Context?, i: Intent?) {
		if (!BuildConfig.PRUEBAS) return
		i ?: return
		i.getStringExtra("gesto")?.let { g ->
			runCatching { Gesto.valueOf(g.uppercase()) }.onSuccess(alGesto).onFailure { Registro.w(ETIQUETA, "Gesto desconocido: $g") }
		}
		// Cambiar un ajuste de texto: --es clave motor --es valor tiresias:piper:es_ES-carlfm-x_low
		val clave = i.getStringExtra("clave")
		val valor = i.getStringExtra("valor")
		if (c != null && clave != null && valor != null) {
			com.xebolax.tiresias.base.Ajustes(c).p.edit().putString(clave, valor).apply()
			ServicioTiresias.instancia?.recargarAjustes()
		}
		i.getStringExtra("accion")?.let { a ->
			runCatching { Accion.valueOf(a.uppercase()) }.onSuccess(alAccion).onFailure { Registro.w(ETIQUETA, "Acción desconocida: $a") }
		}
	}

	fun registrar(c: Context) {
		if (!BuildConfig.PRUEBAS) return
		val filtro = IntentFilter(ACCION)
		// Exportado a propósito: lo manda adb en el emulador (y solo existe en la variante de pruebas)
		androidx.core.content.ContextCompat.registerReceiver(c, this, filtro, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
	}

	companion object {
		private const val ETIQUETA = "OrdenesPrueba"
		const val ACCION = "com.xebolax.tiresias.pruebas.ORDEN"
	}
}
