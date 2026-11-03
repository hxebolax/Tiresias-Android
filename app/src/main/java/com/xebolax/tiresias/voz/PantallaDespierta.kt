package com.xebolax.tiresias.voz

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * La pantalla encendida mientras Tiresias habla (si se elige en Ajustes, Voz):
 * al leer un mensaje largo o de seguido, Android apagaría la pantalla a su hora
 * y, en muchos móviles, con ella se corta o se calla el lector. Se usa un
 * bloqueo de pantalla (puede atenuarse, pero no se apaga) solo mientras hay
 * algo que decir, con un tope por si acaso; al soltarlo, la pantalla vuelve a
 * contar su tiempo normal desde ese momento. Si la pantalla ya estaba apagada
 * (un aviso de noche), no la enciende.
 *
 * Algunos fabricantes no hacen caso del bloqueo de pantalla de una app (a un
 * usuario se le apagaba unas veces sí y otras no): mientras habla, además, una
 * ventana de accesibilidad de un píxel con «mantener la pantalla encendida», que
 * es lo que respetan todos (lo mismo que en la ayuda remota). La ventana se
 * quita un momento después de callarse, para no ponerla y quitarla en cada frase.
 */
class PantallaDespierta(private val c: Context) {
	private var bloqueo: PowerManager.WakeLock? = null
	private val principal = Handler(Looper.getMainLooper())
	/** La ventana que mantiene la pantalla encendida; solo se toca desde el hilo principal. */
	private var vista: View? = null
	private val quitarVentana = Runnable { ponerVentana(false) }

	@Suppress("DEPRECATION")
	@Synchronized
	fun mantener() {
		val b = bloqueo ?: runCatching {
			c.getSystemService(PowerManager::class.java)?.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "Tiresias:habla",
			)?.apply { setReferenceCounted(false) }
		}.onFailure { Registro.w(ETIQUETA, "No se pudo preparar el bloqueo de pantalla", it) }.getOrNull()?.also { bloqueo = it }
		if (b != null && !b.isHeld) runCatching { b.acquire(MAXIMO_MS) }.onFailure { Registro.w(ETIQUETA, "No se pudo mantener la pantalla encendida", it) }
		principal.removeCallbacks(quitarVentana)
		principal.post { ponerVentana(true) }
	}

	@Synchronized
	fun soltar() {
		principal.removeCallbacks(quitarVentana)
		principal.postDelayed(quitarVentana, QUITAR_VENTANA_MS)
		val b = bloqueo ?: return
		if (b.isHeld) runCatching { b.release() }.onFailure { Registro.w(ETIQUETA, "No se pudo soltar el bloqueo de pantalla", it) }
	}

	/** Pone o quita la ventana que mantiene la pantalla encendida (en el hilo principal). Apagada, no se pone: no la enciende, pero tampoco hace falta. */
	private fun ponerVentana(poner: Boolean) {
		val servicio = ServicioTiresias.instancia ?: return
		val wm = servicio.getSystemService(WindowManager::class.java) ?: return
		if (poner) {
			if (vista != null) return
			val encendida = runCatching { servicio.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: true
			if (!encendida) return
			val v = View(servicio)
			v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
			val params = WindowManager.LayoutParams(
				1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				PixelFormat.TRANSPARENT,
			)
			runCatching { wm.addView(v, params); vista = v }.onFailure { Registro.w(ETIQUETA, "No se pudo poner la ventana que mantiene la pantalla encendida", it) }
		} else {
			vista?.let { v -> runCatching { wm.removeView(v) }.onFailure { Registro.w(ETIQUETA, "No se pudo quitar la ventana que mantiene la pantalla encendida", it) } }
			vista = null
		}
	}

	private companion object {
		const val ETIQUETA = "PantallaDespierta"
		/** Lo que como mucho se mantiene la pantalla por una sola frase (una lectura de seguido la renueva frase a frase). */
		const val MAXIMO_MS = 10 * 60_000L
		/** Lo que sigue puesta la ventana tras callarse (entre frase y frase de una lectura no se quita). */
		const val QUITAR_VENTANA_MS = 3_000L
	}
}
