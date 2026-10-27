package com.xebolax.tiresias.base

import android.content.Context

/**
 * Almacenamiento protegido del dispositivo.
 *
 * Tiresias tiene que hablar también justo después de reiniciar, antes de
 * desbloquear el móvil por primera vez (arranque directo), que es cuando más
 * falta hace un lector (para el PIN). En ese momento solo se puede leer el
 * almacenamiento «protegido del dispositivo», así que los ajustes, las voces
 * de Pico y los datos de eSpeak copiados viven ahí desde el principio.
 */
object Almacen {
	fun protegido(contexto: Context): Context {
		val app = contexto.applicationContext ?: contexto
		return app.createDeviceProtectedStorageContext()
	}
}
