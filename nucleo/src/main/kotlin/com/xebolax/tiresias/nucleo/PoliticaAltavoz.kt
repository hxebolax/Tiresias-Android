package com.xebolax.tiresias.nucleo

/**
 * Decide por dónde va el audio de una llamada al apartar el móvil de la oreja
 * y al volver a acercarlo, como VoiceOver en el iPhone (Backtalk 11, 18 y 196).
 *
 * Solo actúa cuando el móvil se mueve, y solo pasa la llamada entre el
 * auricular y el altavoz: no toca el Bluetooth ni los auriculares con cable, y
 * solo apaga el altavoz si lo encendió ella. Así, lo que el usuario elija en la
 * app de teléfono se respeta hasta que el móvil vuelva a moverse.
 *
 * Basado en SpeakerphonePolicy de Backtalk (Copyright 2026 Backtalk
 * contributors, Apache License 2.0), con cambios.
 */
class PoliticaAltavoz {
	/** Por dónde va el audio de la llamada. */
	enum class Salida { AURICULAR, ALTAVOZ, OTRA }

	/** Si el móvil estaba en la oreja en la última lectura (null antes de la primera). */
	private var ultimaEnOreja: Boolean? = null

	/** Si el altavoz está puesto porque lo puso esta política. */
	private var puestoPorMi = false

	/**
	 * Una lectura del sensor de proximidad durante una llamada. Devuelve la
	 * salida a la que hay que pasar o null para dejarla. La primera lectura
	 * cuenta como movimiento: una llamada que empieza con el móvil lejos de la
	 * oreja va al altavoz.
	 */
	fun alLeer(enOreja: Boolean, salida: Salida): Salida? {
		val movido = enOreja != ultimaEnOreja
		ultimaEnOreja = enOreja
		if (!movido) return null
		return when {
			!enOreja && salida == Salida.AURICULAR -> {
				puestoPorMi = true
				Salida.ALTAVOZ
			}
			enOreja && salida == Salida.ALTAVOZ && puestoPorMi -> {
				puestoPorMi = false
				Salida.AURICULAR
			}
			else -> null
		}
	}

	/** La llamada ha pasado a [salida] (por esta política, por el usuario o por otra app). */
	fun alCambiarSalida(salida: Salida) {
		if (salida != Salida.ALTAVOZ) puestoPorMi = false
	}

	/** Lo olvida todo, cuando ya no hay llamada. */
	fun reiniciar() {
		ultimaEnOreja = null
		puestoPorMi = false
	}
}
