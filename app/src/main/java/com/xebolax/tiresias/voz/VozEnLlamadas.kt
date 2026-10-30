package com.xebolax.tiresias.voz

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import kotlin.math.pow

/**
 * La voz y los sonidos durante una llamada (Can Talk). En muchos móviles el
 * volumen del lector va pegado al de la llamada: al subir la llamada, la voz
 * atruena, y al bajarla desaparece. Con «Voz en llamadas» puesto, durante una
 * llamada (de teléfono o por Internet) la voz suena a un porcentaje propio del
 * volumen normal, con una curva cuadrática (100 % es el volumen de siempre; 50
 * %, la cuarta parte), y con un dedo en la pantalla las teclas de volumen
 * cambian ese porcentaje de 5 en 5 en vez del volumen del sistema.
 *
 * Con «Compensar» (experimental, Android 9 o posterior), la primera frase de
 * la llamada apunta los decibelios del volumen de la llamada en esa salida y,
 * si luego se sube o se baja, la voz se corrige al revés para que se oiga igual.
 *
 * Solo cambia la ganancia de lo que suena (la pista de las voces incluidas,
 * el volumen de cada frase de la voz del sistema y los sonidos): ningún volumen
 * del sistema, ni el modo de audio, ni la salida.
 */
object VozEnLlamadas {
	private const val ETIQUETA = "VozEnLlamadas"
	private const val MINIMO = 0.05f

	/** Lo que se multiplica ahora al volumen de la voz; lo lee la voz del sistema al hablar. */
	@Volatile var ganancia = 1f
		private set

	/** La salida y los decibelios de referencia de la llamada de ahora. */
	private var rutaReferencia: String? = null
	private var dbReferencia = 0f

	fun enLlamada(c: Context): Boolean {
		val modo = c.getSystemService(AudioManager::class.java)?.mode ?: return false
		return modo == AudioManager.MODE_IN_CALL || modo == AudioManager.MODE_IN_COMMUNICATION
	}

	/** Calcula la ganancia de la frase que va a sonar (en el hilo de la voz). */
	fun calcular(c: Context, ajustes: Ajustes): Float {
		val g = try {
			calcularYa(c, ajustes)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo calcular el volumen de la voz en la llamada", e)
			1f
		}
		ganancia = g
		return g
	}

	private fun calcularYa(c: Context, ajustes: Ajustes): Float {
		val am = c.getSystemService(AudioManager::class.java) ?: return 1f
		val modo = am.mode
		if (!ajustes.vozLlamadas || (modo != AudioManager.MODE_IN_CALL && modo != AudioManager.MODE_IN_COMMUNICATION)) {
			rutaReferencia = null
			return 1f
		}
		val parte = (ajustes.volumenLlamadas / 100f).coerceIn(MINIMO, 1f)
		var g = parte * parte
		if (ajustes.compensarLlamadas && Build.VERSION.SDK_INT >= 28) {
			val tipo = tipoDeSalida(am)
			val indice = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
			val db = runCatching { am.getStreamVolumeDb(AudioManager.STREAM_VOICE_CALL, indice, tipo) }.getOrNull()
			if (db != null && db.isFinite()) {
				val ruta = "$modo:$tipo"
				if (ruta != rutaReferencia) {
					rutaReferencia = ruta
					dbReferencia = db
				}
				g *= 10f.pow((dbReferencia - db) / 20f)
			}
		}
		return g.coerceIn(0f, 1f)
	}

	/** Por dónde va la llamada: el auricular, el altavoz, el Bluetooth o los auriculares. */
	private fun tipoDeSalida(am: AudioManager): Int {
		if (Build.VERSION.SDK_INT >= 31) am.communicationDevice?.let { return it.type }
		@Suppress("DEPRECATION")
		return when {
			am.isBluetoothScoOn -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
			am.isSpeakerphoneOn -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
			am.isWiredHeadsetOn -> AudioDeviceInfo.TYPE_WIRED_HEADSET
			else -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
		}
	}

	/** Las teclas con un dedo en la pantalla durante una llamada: sube o baja el porcentaje de 5 en 5; devuelve el nuevo. */
	fun cambiar(ajustes: Ajustes, arriba: Boolean): Int {
		val nuevo = ((ajustes.volumenLlamadas / 5) * 5 + if (arriba) 5 else -5).coerceIn(5, 100)
		ajustes.volumenLlamadas = nuevo
		return nuevo
	}
}
