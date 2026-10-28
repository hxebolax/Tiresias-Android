package com.xebolax.tiresias.base

import android.content.Context
import java.io.File

/**
 * Deja Tiresias como recién instalado sin desactivar su servicio de
 * accesibilidad: borrar los datos de la app desde Android (`pm clear`) también
 * quita el lector de los servicios activados, y quien solo usa Tiresias se
 * quedaría sin voz. Se borran los ajustes, los gestos, los complementos, las
 * etiquetas, los perfiles, los diccionarios y lo guardado por los complementos;
 * se quedan las voces y los modelos descargados (cientos de megas).
 */
object Restablecer {
	fun todo(c: Context) {
		val app = c.applicationContext
		// Las preferencias de los dos almacenamientos, vaciadas con su objeto (así no queda nada en memoria)
		for (contexto in listOf(app, Almacen.protegido(app))) {
			val dir = File(contexto.dataDir, "shared_prefs")
			for (f in dir.listFiles().orEmpty()) {
				if (!f.name.endsWith(".xml")) continue
				contexto.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
			}
			for (nombre in listOf("complementos", "diccionarios", "etiquetas", "perfiles", "personas")) {
				File(contexto.filesDir, nombre).takeIf { it.exists() }?.deleteRecursively()
			}
		}
		Registro.i("Restablecer", "Tiresias restablecido como recién instalado")
	}
}
