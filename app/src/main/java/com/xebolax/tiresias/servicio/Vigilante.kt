package com.xebolax.tiresias.servicio

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.voz.Voz

/**
 * Que un cuelgue no deje a nadie sin lector: un hilo aparte pregunta cada poco al
 * hilo principal y al del lector si siguen ahí, y mira que la voz avance. Si alguno
 * lleva demasiado sin responder, apunta dónde estaba cada hilo en el registro técnico
 * (solo nombres de funciones, nada de lo que hay en la pantalla) y reinicia el
 * proceso: Android vuelve a conectar el servicio solo, en unos segundos.
 *
 * Antes, una app que se quedaba sin responder o un motor de voz colgado dejaban el
 * móvil con la exploración táctil puesta y sin nadie que la atendiera: los toques ni
 * llegaban a las apps ni hacían nada, y había que reiniciar a la fuerza.
 */
class Vigilante(private val contexto: Context, private val lector: Handler, private val voz: Voz) {
	private class Vigilado(val nombre: String, val manejador: Handler) {
		/** Desde cuándo espera su respuesta (0: ninguna pendiente). */
		@Volatile var preguntado = 0L
	}

	private val vigilados = listOf(Vigilado("principal", Handler(Looper.getMainLooper())), Vigilado("lector", lector))
	@Volatile private var parar = false
	private val hilo = Thread({ vigilar() }, "tiresias-vigilante").apply {
		isDaemon = true
		priority = Thread.MAX_PRIORITY
	}

	fun iniciar() = hilo.start()

	fun cerrar() {
		parar = true
		hilo.interrupt()
	}

	private fun vigilar() {
		try {
			// El arranque carga los complementos y las voces: en un móvil lento puede llevar un rato
			Thread.sleep(ESPERA_INICIAL_MS)
			var vuelta = SystemClock.uptimeMillis()
			while (!parar) {
				Thread.sleep(CADA_MS)
				val ahora = SystemClock.uptimeMillis()
				// Si este hilo tampoco ha corrido (el móvil dormido o parado), no es culpa de nadie
				if (ahora - vuelta > CADA_MS * 3) vigilados.forEach { it.preguntado = 0L }
				vuelta = ahora
				for (v in vigilados) {
					val desde = v.preguntado
					if (desde == 0L) {
						v.preguntado = ahora
						v.manejador.post { v.preguntado = 0L }
					} else if (ahora - desde > LIMITE_MS && !Debug.isDebuggerConnected()) {
						reiniciar("El hilo ${v.nombre} lleva ${ahora - desde} ms sin responder")
						return
					}
				}
				if (voz.atascada(LIMITE_MS) && !Debug.isDebuggerConnected()) {
					reiniciar("La voz lleva más de $LIMITE_MS ms sin avanzar")
					return
				}
			}
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
		}
	}

	private fun reiniciar(motivo: String) {
		val hilos = listOf(Looper.getMainLooper().thread, lector.looper.thread, voz.hiloVoz)
		val pilas = hilos.joinToString("\n") { h -> "${h.name}: ${h.state}\n" + h.stackTrace.take(PILA_MAXIMA).joinToString("\n") { "  $it" } }
		Registro.e(ETIQUETA, "$motivo: se reinicia Tiresias\n$pilas")
		Registro.vaciar(ESPERA_REGISTRO_MS)
		Ajustes(contexto).p.edit().putBoolean(Claves.REINICIO_VIGILANTE, true).commit()
		Process.killProcess(Process.myPid())
	}

	companion object {
		private const val ETIQUETA = "Vigilante"
		private const val ESPERA_INICIAL_MS = 30_000L
		private const val CADA_MS = 2000L
		/**
		 * Lo que puede tardar un hilo en responder: más que varias preguntas seguidas a
		 * una app ocupada (Android espera a cada una hasta 5 s), para no reiniciar en balde.
		 */
		private const val LIMITE_MS = 25_000L
		private const val PILA_MAXIMA = 30
		private const val ESPERA_REGISTRO_MS = 1000L
	}
}
