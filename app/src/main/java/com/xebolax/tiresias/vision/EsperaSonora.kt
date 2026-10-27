package com.xebolax.tiresias.vision

import android.os.SystemClock
import com.xebolax.tiresias.R
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido

/**
 * El sonido de «estoy trabajando» mientras la IA piensa o se reconoce el texto,
 * como el de TalkBack con Gemini (`ProgressTonePlayer`: el sonido de carga cada
 * 3 segundos hasta la respuesta). En un móvil lento puede tardar bastante: si
 * pasan [AVISO_MS] sin respuesta, se dice una vez que sigue pensando. Como mucho
 * suena [MAXIMO_MS]; luego se calla aunque no haya respuesta.
 */
class EsperaSonora(private val lector: Lector) {
	@Volatile private var tarea: Runnable? = null

	val activa: Boolean get() = tarea != null

	fun empezar() {
		parar()
		val a = lector.ajustes
		if (!a.sonidoEspera && !a.avisoEspera) return
		val inicio = SystemClock.uptimeMillis()
		var avisado = false
		val r = object : Runnable {
			override fun run() {
				if (tarea !== this) return
				val pasado = SystemClock.uptimeMillis() - inicio
				if (pasado > MAXIMO_MS) {
					parar()
					return
				}
				if (a.sonidoEspera) lector.sonidos.tocar(Sonido.CARGANDO)
				if (!avisado && a.avisoEspera && pasado >= AVISO_MS) {
					avisado = true
					lector.decir(R.string.ia_sigue_pensando, modo = Modo.ENCOLAR)
				}
				lector.manejador.postDelayed(this, CICLO_MS)
			}
		}
		tarea = r
		lector.manejador.post(r)
	}

	fun parar() {
		tarea?.let { lector.manejador.removeCallbacks(it) }
		tarea = null
	}

	companion object {
		/** Cada cuánto suena (el mismo ciclo que TalkBack). */
		const val CICLO_MS = 3000L
		const val AVISO_MS = 20_000L
		const val MAXIMO_MS = 180_000L
	}
}
