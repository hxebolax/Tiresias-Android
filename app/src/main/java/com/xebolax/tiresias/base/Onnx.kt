package com.xebolax.tiresias.base

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException

/**
 * El entorno de ONNX Runtime (el OCR y las voces neuronales), siempre sin
 * telemetría. ONNX Runtime para Android trae telemetría de Microsoft (un
 * identificador del dispositivo, el modelo y lo que se usa, por Internet): su
 * arranque automático se quita en el manifiesto (`TelemetryInitializer`, con
 * `tools:node="remove"`) y aquí se apaga en el entorno. Tiresias no envía nada.
 */
object Onnx {
	@Volatile private var apagada = false

	fun entorno(): OrtEnvironment {
		val e = OrtEnvironment.getEnvironment()
		if (!apagada) {
			try {
				e.setTelemetry(false)
				apagada = true
			} catch (ex: OrtException) {
				Registro.w(ETIQUETA, "No se pudo apagar la telemetría de ONNX Runtime", ex)
			}
		}
		return e
	}

	private const val ETIQUETA = "Onnx"
}
