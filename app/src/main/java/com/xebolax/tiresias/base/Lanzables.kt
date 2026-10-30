package com.xebolax.tiresias.base

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.xebolax.tiresias.nucleo.escritorio.Buscar

/** Una app que se puede abrir. */
data class Lanzable(val nombre: String, val paquete: String)

/**
 * Las apps del lanzador, por su nombre. Pedir sus nombres a Android es lento, y esta lista
 * hacía falta en seis sitios, así que se guarda unos segundos. Bloquea la primera vez.
 */
object Lanzables {
	private const val ETIQUETA = "Lanzables"
	private const val VALE_MS = 10_000L
	private var lista: List<Lanzable> = emptyList()
	private var cuando = 0L

	@Synchronized
	fun todas(c: Context): List<Lanzable> {
		val ahora = SystemClock.elapsedRealtime()
		if (lista.isNotEmpty() && ahora - cuando < VALE_MS) return lista
		val pm = c.packageManager
		lista = try {
			pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
				.map { Lanzable(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
				.distinctBy { it.paquete }
				.sortedBy { Buscar.normalizar(it.nombre) }
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo leer la lista de apps", e)
			emptyList()
		}
		cuando = ahora
		return lista
	}

	/** La más parecida a [nombre], o null si ninguna lo contiene. */
	fun buscar(c: Context, nombre: String): Lanzable? {
		val q = Buscar.normalizar(nombre)
		return todas(c).maxByOrNull { Buscar.parecido(Buscar.normalizar(it.nombre), q) }?.takeIf { Buscar.normalizar(it.nombre).contains(q) }
	}
}
