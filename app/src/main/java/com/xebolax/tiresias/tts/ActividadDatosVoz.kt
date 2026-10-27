package com.xebolax.tiresias.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.xebolax.tiresias.ui.ActividadAjustes

/**
 * Responde a las preguntas del sistema sobre los datos de voz de «Voces de
 * Tiresias», sin pantalla: las voces vienen dentro de la app, así que siempre
 * están. Portado de Rescate Accesible (mismo autor).
 */
class ActividadDatosVoz : Activity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		when (intent?.action) {
			TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
				startActivity(Intent(this, ActividadAjustes::class.java))
				setResult(RESULT_OK)
			}
			else -> {
				val disponibles = ArrayList(ServicioTtsTiresias.VOCES.map { v ->
					"${v.locale.isO3Language}-${runCatching { v.locale.isO3Country }.getOrDefault("")}"
				}.distinct())
				val resultado = Intent()
					.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, disponibles)
					.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
				setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultado)
			}
		}
		finish()
	}
}
