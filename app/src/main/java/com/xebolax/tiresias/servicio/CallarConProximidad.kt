package com.xebolax.tiresias.servicio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro

/**
 * Calla la voz al tapar el sensor de proximidad (pasar la mano por encima de la
 * parte de arriba de la pantalla), como TalkBack.
 *
 * - El sensor solo se mira mientras la voz habla y la pantalla está encendida,
 *   para no gastar batería, y se suelta al poco de callarse.
 * - Solo cuenta pasar de destapado a tapado: si el móvil ya estaba tapado al
 *   empezar a hablar (boca abajo, en un bolsillo), no se calla cada frase.
 * - En una llamada no hace nada: el móvil en la oreja también tapa el sensor.
 */
class CallarConProximidad(private val c: Context, private val ajustes: Ajustes, private val callar: () -> Unit) {
	private val principal = Handler(Looper.getMainLooper())
	private val sensores = c.getSystemService(SensorManager::class.java)
	private val sensor: Sensor? = runCatching { sensores?.getDefaultSensor(Sensor.TYPE_PROXIMITY) }.getOrNull()
	private val lejos = sensor?.let { minOf(it.maximumRange, CERCA_CM) } ?: 0f
	private var escuchando = false
	/** Lo último que dijo el sensor: null hasta la primera lectura tras registrarlo. */
	private var tapado: Boolean? = null
	/** Tras [cerrar] ya no se vuelve a mirar el sensor (por si quedaba un aviso de la voz por atender). */
	private var cerrado = false

	private val alLeer = object : SensorEventListener {
		override fun onSensorChanged(e: SensorEvent) {
			val ahora = e.values.firstOrNull()?.let { it < lejos } ?: return
			val antes = tapado
			tapado = ahora
			if (antes == false && ahora && !enLlamada()) {
				Registro.i(ETIQUETA, "Sensor de proximidad tapado: la voz se calla")
				callar()
			}
		}

		override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
	}

	private val soltar = Runnable { dejarDeEscuchar() }

	/** La voz empieza a hablar o lleva un rato callada (desde cualquier hilo). */
	fun alCambiarHablando(hablando: Boolean) {
		principal.post {
			if (hablando) {
				principal.removeCallbacks(soltar)
				if (ajustes.callarProximidad && pantallaEncendida()) escuchar()
			} else {
				principal.removeCallbacks(soltar)
				principal.postDelayed(soltar, SOLTAR_MS)
			}
		}
	}

	private fun escuchar() {
		val s = sensor ?: return
		if (escuchando || cerrado) return
		escuchando = true
		tapado = null
		// El sensor dice su estado en cuanto se registra (esa primera lectura no calla)
		runCatching { sensores?.registerListener(alLeer, s, SensorManager.SENSOR_DELAY_UI, principal) }
			.onFailure {
				escuchando = false
				Registro.w(ETIQUETA, "No se pudo mirar el sensor de proximidad", it)
			}
	}

	private fun dejarDeEscuchar() {
		if (!escuchando) return
		escuchando = false
		runCatching { sensores?.unregisterListener(alLeer) }.onFailure { Registro.w(ETIQUETA, "No se pudo soltar el sensor de proximidad", it) }
	}

	/** En el hilo principal, al apagarse el servicio. */
	fun cerrar() {
		cerrado = true
		principal.removeCallbacks(soltar)
		dejarDeEscuchar()
	}

	private fun pantallaEncendida(): Boolean = runCatching { c.getSystemService(PowerManager::class.java)?.isInteractive }.getOrNull() ?: true

	private fun enLlamada(): Boolean = runCatching {
		val modo = c.getSystemService(AudioManager::class.java)?.mode
		modo == AudioManager.MODE_IN_CALL || modo == AudioManager.MODE_IN_COMMUNICATION || modo == AudioManager.MODE_RINGTONE
	}.getOrDefault(false)

	companion object {
		private const val ETIQUETA = "CallarProximidad"
		/** Más cerca que esto, en centímetros, es «tapado». */
		private const val CERCA_CM = 5f
		/** Lo que se sigue mirando el sensor tras callarse la voz (lo que se dice a trozos no lo registra cada vez). */
		private const val SOLTAR_MS = 3000L
	}
}
