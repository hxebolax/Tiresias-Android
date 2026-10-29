package com.xebolax.tiresias.voz

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import com.xebolax.tiresias.base.Registro

/**
 * La salida de las voces incluidas: una única pista de audio en modo de
 * transmisión y baja latencia que se queda abierta mientras se habla.
 *
 * Abrir una `AudioTrack` por frase cuesta entre 60 y 70 ms; con la pista
 * abierta, el primer sonido sale en unos 20 ms (medido por Backtalk en
 * `LowLatencyAudio.kt`). Tras unos segundos callada se cierra: una pista abierta
 * en silencio impide a algunas apps (asistentes de voz) oír el micrófono
 * (Backtalk 74, 116 y 264) y gasta batería.
 *
 * Solo se usa desde el hilo de la voz, salvo [cortar], que puede llamarse desde
 * cualquier hilo.
 */
class PistaAudio(atributosIniciales: AudioAttributes) {
	/** Los atributos de la voz; al cambiarlos (otro volumen), la pista se rehace en la siguiente frase. */
	@Volatile var atributos: AudioAttributes = atributosIniciales
	private var atributosDePista: AudioAttributes? = null
	private var pista: AudioTrack? = null
	private var frecuencia = 0
	@Volatile private var cortada = false
	@Volatile private var ultimoUso = 0L
	private var volumen = 1f
	/** El dispositivo elegido con el que se abrió la pista (null: el del sistema). */
	private var dispositivo: Int? = null

	/** Volumen de la pista (0 a 1), relativo al volumen de accesibilidad del sistema. */
	fun ponerVolumen(v: Float) {
		volumen = v.coerceIn(0f, 1f)
		runCatching { pista?.setVolume(volumen) }
	}

	/**
	 * Escribe [muestras] (16 bits, mono, a [hz]) y vuelve cuando se han
	 * entregado a la pista. [sigue] se consulta entre bloques: si devuelve
	 * false, se deja de escribir. Devuelve false si la pista falló.
	 */
	fun escribir(muestras: ShortArray, hz: Int, sigue: () -> Boolean): Boolean {
		if (muestras.isEmpty()) return true
		val p = abrir(hz) ?: return false
		cortada = false
		try {
			if (p.playState != AudioTrack.PLAYSTATE_PLAYING) p.play()
			var desde = 0
			while (desde < muestras.size) {
				if (cortada || !sigue()) return true
				val n = minOf(BLOQUE, muestras.size - desde)
				val escritas = p.write(muestras, desde, n)
				if (escritas < 0) {
					Registro.w(ETIQUETA, "La pista rechazó las muestras (código $escritas): se vuelve a abrir")
					cerrar()
					return false
				}
				desde += escritas
			}
			ultimoUso = SystemClock.uptimeMillis()
			return true
		} catch (e: IllegalStateException) {
			Registro.e(ETIQUETA, "La pista de audio falló", e)
			cerrar()
			return false
		}
	}

	/**
	 * Espera a que suene lo ya escrito (para saber cuándo acaba una frase) o a
	 * que se corte. Devuelve true si terminó de sonar.
	 */
	fun esperarFin(sigue: () -> Boolean): Boolean {
		val p = pista ?: return true
		// Como mucho 15 s (una frase larga); la cabeza de reproducción quieta indica que ya sonó todo
		val limite = SystemClock.uptimeMillis() + 15_000
		var anterior = -1
		var quieta = 0
		while (!cortada && sigue() && SystemClock.uptimeMillis() < limite) {
			val pos = runCatching { p.playbackHeadPosition }.getOrDefault(0)
			if (pos == anterior) {
				// La cabeza no avanza: el búfer está vacío (ya sonó todo)
				if (++quieta >= 3) return true
			} else quieta = 0
			anterior = pos
			SystemClock.sleep(8)
		}
		return !cortada
	}

	/** Corta en seco lo que suena (desde cualquier hilo). */
	fun cortar() {
		cortada = true
		val p = pista ?: return
		runCatching {
			p.pause()
			p.flush()
		}
	}

	/** Cierra la pista si lleva [ms] callada. Lo llama la voz de vez en cuando. */
	fun cerrarSiCallada(ms: Long) {
		if (pista != null && SystemClock.uptimeMillis() - ultimoUso > ms) cerrar()
	}

	fun cerrar() {
		val p = pista ?: return
		pista = null
		frecuencia = 0
		runCatching {
			p.pause()
			p.flush()
			p.release()
		}
	}

	private fun abrir(hz: Int): AudioTrack? {
		val elegido = dispositivoElegido()
		pista?.let { if (frecuencia == hz && it.state == AudioTrack.STATE_INITIALIZED && dispositivo == elegido?.id && atributosDePista === atributos) return it else cerrar() }
		return try {
			val minimo = AudioTrack.getMinBufferSize(hz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
			val tamano = maxOf(minimo, hz / 10 * 2)
			val b = AudioTrack.Builder()
				.setAudioAttributes(atributos)
				.setAudioFormat(
					AudioFormat.Builder()
						.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
						.setSampleRate(hz)
						.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
						.build()
				)
				.setTransferMode(AudioTrack.MODE_STREAM)
				.setBufferSizeInBytes(tamano)
			if (Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
			val nueva = b.build()
			if (nueva.state != AudioTrack.STATE_INITIALIZED) {
				nueva.release()
				Registro.w(ETIQUETA, "No se pudo iniciar la pista de $hz Hz")
				return null
			}
			nueva.setVolume(volumen)
			if (elegido != null && !nueva.setPreferredDevice(elegido)) Registro.w(ETIQUETA, "La pista no aceptó la salida elegida: suena por la del sistema")
			dispositivo = elegido?.id
			atributosDePista = atributos
			pista = nueva
			frecuencia = hz
			nueva
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo crear la pista de audio", e)
			null
		}
	}

	companion object {
		private const val ETIQUETA = "PistaAudio"
		/** Muestras por escritura: unos 23 ms a 22 050 Hz, para cortar enseguida. */
		private const val BLOQUE = 512

		/** La voz por el volumen multimedia (Ajustes, Voz); lo pone el servicio al arrancar. */
		@Volatile var multimedia = false

		const val SALIDA_SISTEMA = "sistema"
		const val SALIDA_ALTAVOZ = "altavoz"
		const val SALIDA_AURICULARES = "auriculares"
		/** El auricular de las llamadas: para oír la voz con el móvil en la oreja, en público y sin auriculares (SandFly). */
		const val SALIDA_AURICULAR_LLAMADA = "auricular_llamada"

		/** Por dónde suenan las voces incluidas (Ajustes, Voz); lo pone la voz al leer los ajustes. */
		@Volatile var salida = SALIDA_SISTEMA

		/** Para buscar el dispositivo de salida; lo pone la voz al crearse. */
		@Volatile var gestor: AudioManager? = null

		/** Los tipos de auriculares, por preferencia: con cable, USB y Bluetooth. */
		private val AURICULARES: List<Int> = buildList {
			add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
			add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
			if (Build.VERSION.SDK_INT >= 26) add(AudioDeviceInfo.TYPE_USB_HEADSET)
			if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
			add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
		}

		/**
		 * El dispositivo elegido, si está conectado; null para dejarlo al sistema.
		 * Durante una llamada no se toca nunca: la llamada se iría al reloj o el
		 * asistente dejaría de oír (Backtalk 154, 174 y 237).
		 */
		fun dispositivoElegido(): AudioDeviceInfo? {
			val s = salida
			if (s == SALIDA_SISTEMA) return null
			val am = gestor ?: return null
			if (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION) return null
			val salidas = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull() ?: return null
			val tipos = when (s) {
				SALIDA_ALTAVOZ -> listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
				SALIDA_AURICULAR_LLAMADA -> listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
				else -> AURICULARES
			}
			return tipos.firstNotNullOfOrNull { t -> salidas.firstOrNull { it.type == t } }
		}

		/** Los atributos de la voz: uso de accesibilidad (su propio volumen y prioridad) y voz. */
		fun atributosVoz(): AudioAttributes = AudioAttributes.Builder()
			.setUsage(if (Build.VERSION.SDK_INT >= 26 && !multimedia) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY else AudioAttributes.USAGE_MEDIA)
			.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
			.build()

		/** El flujo de volumen que corresponde a la voz. */
		val flujo: Int get() = if (Build.VERSION.SDK_INT >= 26 && !multimedia) AudioManager.STREAM_ACCESSIBILITY else AudioManager.STREAM_MUSIC
	}
}
