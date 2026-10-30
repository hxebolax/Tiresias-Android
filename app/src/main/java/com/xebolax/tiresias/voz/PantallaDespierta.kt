package com.xebolax.tiresias.voz

import android.content.Context
import android.os.PowerManager
import com.xebolax.tiresias.base.Registro

/**
 * La pantalla encendida mientras Tiresias habla (si se elige en Ajustes, Voz):
 * al leer un mensaje largo o de seguido, Android apagaría la pantalla a su hora
 * y, en muchos móviles, con ella se corta o se calla el lector. Se usa un
 * bloqueo de pantalla (puede atenuarse, pero no se apaga) solo mientras hay
 * algo que decir, con un tope por si acaso; al soltarlo, la pantalla vuelve a
 * contar su tiempo normal desde ese momento. Si la pantalla ya estaba apagada
 * (un aviso de noche), no la enciende.
 */
class PantallaDespierta(private val c: Context) {
	private var bloqueo: PowerManager.WakeLock? = null

	@Suppress("DEPRECATION")
	@Synchronized
	fun mantener() {
		val b = bloqueo ?: runCatching {
			c.getSystemService(PowerManager::class.java)?.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "Tiresias:habla",
			)?.apply { setReferenceCounted(false) }
		}.onFailure { Registro.w(ETIQUETA, "No se pudo preparar el bloqueo de pantalla", it) }.getOrNull()?.also { bloqueo = it } ?: return
		if (!b.isHeld) runCatching { b.acquire(MAXIMO_MS) }.onFailure { Registro.w(ETIQUETA, "No se pudo mantener la pantalla encendida", it) }
	}

	@Synchronized
	fun soltar() {
		val b = bloqueo ?: return
		if (b.isHeld) runCatching { b.release() }.onFailure { Registro.w(ETIQUETA, "No se pudo soltar el bloqueo de pantalla", it) }
	}

	private companion object {
		const val ETIQUETA = "PantallaDespierta"
		/** Lo que como mucho se mantiene la pantalla por una sola frase (una lectura de seguido la renueva frase a frase). */
		const val MAXIMO_MS = 10 * 60_000L
	}
}
