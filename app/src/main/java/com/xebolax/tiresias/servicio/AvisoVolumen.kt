package com.xebolax.tiresias.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro

/**
 * Dice el volumen nuevo al cambiarlo con las teclas («Volumen multimedia, 40
 * por ciento»), como TalkBack y SandFly (VolumeMonitor), pero sin sus dos
 * segundos de espera: se dice un momento después de la última pulsación.
 *
 * Android avisa con la difusión `VOLUME_CHANGED_ACTION` (oculta, pero se
 * entrega a quien la registra). Lo que cambia Tiresias (el volumen de la voz,
 * que ya dice él) no se repite: ver [propio].
 */
class AvisoVolumen(private val c: Context, private val ajustes: Ajustes, private val decir: (nombre: Int, porCiento: Int) -> Unit) {
	private val principal = Handler(Looper.getMainLooper())
	private val audio = c.getSystemService(AudioManager::class.java)
	private var pendiente: Runnable? = null
	private var registrado = false

	private val receptor = object : BroadcastReceiver() {
		override fun onReceive(contexto: Context?, intent: Intent?) {
			intent ?: return
			if (!ajustes.decirVolumen) return
			val flujo = intent.getIntExtra(EXTRA_FLUJO, -1)
			val valor = intent.getIntExtra(EXTRA_VALOR, -1)
			val antes = intent.getIntExtra(EXTRA_ANTES, -1)
			if (flujo < 0 || valor < 0 || valor == antes) return
			if (SystemClock.uptimeMillis() < propioHasta) return
			pendiente?.let { principal.removeCallbacks(it) }
			val r = Runnable {
				pendiente = null
				val maximo = runCatching { audio?.getStreamMaxVolume(flujo) }.getOrNull()?.coerceAtLeast(1) ?: return@Runnable
				val actual = runCatching { audio?.getStreamVolume(flujo) }.getOrNull() ?: valor
				decir(nombre(flujo), actual * 100 / maximo)
			}
			pendiente = r
			principal.postDelayed(r, ESPERA_MS)
		}
	}

	fun iniciar() {
		if (registrado) return
		try {
			val filtro = IntentFilter(ACCION)
			if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED)
			else @Suppress("UnspecifiedRegisterReceiverFlag") c.registerReceiver(receptor, filtro)
			registrado = true
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo escuchar los cambios de volumen", e)
		}
	}

	fun cerrar() {
		pendiente?.let { principal.removeCallbacks(it) }
		if (!registrado) return
		runCatching { c.unregisterReceiver(receptor) }.onFailure { Registro.w(ETIQUETA, "No se pudo dejar de escuchar el volumen", it) }
		registrado = false
	}

	/** La cadena del nombre del volumen [flujo] (la dice el servicio en el idioma de Tiresias). */
	private fun nombre(flujo: Int): Int =
		when (flujo) {
			AudioManager.STREAM_MUSIC -> R.string.volumen_multimedia
			AudioManager.STREAM_RING -> R.string.volumen_tono
			AudioManager.STREAM_NOTIFICATION -> R.string.volumen_notificaciones
			AudioManager.STREAM_ALARM -> R.string.volumen_alarma
			AudioManager.STREAM_VOICE_CALL -> R.string.volumen_llamada
			AudioManager.STREAM_SYSTEM -> R.string.volumen_sistema
			else -> if (Build.VERSION.SDK_INT >= 26 && flujo == AudioManager.STREAM_ACCESSIBILITY) R.string.volumen_accesibilidad else R.string.volumen_otro
		}

	companion object {
		private const val ETIQUETA = "AvisoVolumen"
		private const val ACCION = "android.media.VOLUME_CHANGED_ACTION"
		private const val EXTRA_FLUJO = "android.media.EXTRA_VOLUME_STREAM_TYPE"
		private const val EXTRA_VALOR = "android.media.EXTRA_VOLUME_STREAM_VALUE"
		private const val EXTRA_ANTES = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
		/** Lo que se espera tras la última pulsación para decir el volumen (TalkBack espera 2 s: demasiado). */
		private const val ESPERA_MS = 350L

		/** Hasta cuándo un cambio de volumen es de Tiresias (que ya lo dice): no se repite. */
		@Volatile var propioHasta = 0L

		/** Tiresias va a cambiar un volumen y lo dirá él. */
		fun propio() {
			propioHasta = SystemClock.uptimeMillis() + 1000
		}
	}
}
