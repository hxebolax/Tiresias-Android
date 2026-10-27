package com.xebolax.tiresias.recursos

import com.xebolax.tiresias.R
import java.util.Locale

/**
 * Las voces neuronales Piper (rhasspy/piper-voices) que se pueden descargar.
 * Suenan mucho más naturales que eSpeak, a cambio de unos 20 a 115 MB por voz
 * y algo más de proceso. Funcionan sin Internet una vez bajadas.
 *
 * La licencia de cada una es la de su grabación, y se enseña antes de bajarla.
 * Varias están afinadas a partir de la voz inglesa «lessac», cuyo corpus solo
 * se permite para investigación: se marcan con [derivadaLessac] para que el
 * usuario lo sepa. Tiresias no las incluye: las baja el usuario si quiere.
 */
object VocesNeuronales {
	data class Voz(
		val id: String,
		val locale: Locale,
		val nombre: String,
		val calidad: Int,
		val licencia: String,
		val derivadaLessac: Boolean,
		val ruta: String,
		val tamano: Long,
		val sha256: String,
		val tamanoJson: Long,
	) {
		val recurso: Recursos.Recurso
			get() = Recursos.Recurso(
				"piper_$id", R.string.recurso_voz, R.string.recurso_voz_desc,
				listOf(
					Recursos.Archivo("$BASE/$ruta.onnx", "voz.onnx", tamano, sha256),
					Recursos.Archivo("$BASE/$ruta.onnx.json", "voz.onnx.json", 0, ""),
				),
				licencia,
			)
	}

	private const val BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main"

	val CATALOGO = listOf(
		Voz("es_ES-carlfm-x_low", Locale("es", "ES"), "Carlos (España)", R.string.calidad_baja, "Dominio público", false,
			"es/es_ES/carlfm/x_low/es_ES-carlfm-x_low", 28_130_791, "d69677323a907cd4963f42b29c20a98b5d6bfa7f3e64df339915e4650c00d125", 4159),
		Voz("es_ES-davefx-medium", Locale("es", "ES"), "Dave (España)", R.string.calidad_media, "CC0", true,
			"es/es_ES/davefx/medium/es_ES-davefx-medium", 63_201_294, "6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917", 4817),
		Voz("es_ES-sharvard-medium", Locale("es", "ES"), "Sharvard (España, dos voces)", R.string.calidad_media, "CC BY 3.0", true,
			"es/es_ES/sharvard/medium/es_ES-sharvard-medium", 76_733_615, "40febfb1679c69a4505ff311dc136e121e3419a13a290ef264fdf43ddedd0fb1", 4903),
		Voz("es_MX-claude-high", Locale("es", "MX"), "Claude (México)", R.string.calidad_alta, "Apache 2.0", false,
			"es/es_MX/claude/high/es_MX-claude-high", 63_122_309, "3ef40a71ea63852cd8ab7e6fa7d2ecdcfa67a0b47c9c48e3f10e02ee02083ea0", 4963),
		Voz("es_MX-ald-medium", Locale("es", "MX"), "Ald (México)", R.string.calidad_media, "Unlicense", true,
			"es/es_MX/ald/medium/es_MX-ald-medium", 63_201_294, "019b3803293c93e34a206dd2e53a3889209a514e786fd7144f7b70196c579b63", 4878),
		Voz("es_MX-ald-x_low", Locale("es", "MX"), "Ald ligera (México)", R.string.calidad_baja, "Unlicense", true,
			"es/es_MX/ald/x_low/es_MX-ald-x_low", 20_986_952, "d8aae54aee9eafb37fb85d6da676facafca31c2d84d11acdca1a5b6dd82b1df6", 4877),
		Voz("es_AR-daniela-high", Locale("es", "AR"), "Daniela (Argentina)", R.string.calidad_alta, "CC BY-SA 4.0", true,
			"es/es_AR/daniela/high/es_AR-daniela-high", 114_199_011, "7ceb1fc0dab349418c5b54a639ae9ee595212d7c9ea422220d8419163d5cc985", 7248),
		Voz("en_GB-alba-medium", Locale.UK, "Alba (Reino Unido)", R.string.calidad_media, "CC BY 4.0", true,
			"en/en_GB/alba/medium/en_GB-alba-medium", 63_201_294, "401369c4a81d09fdd86c32c5c864440811dbdcc66466cde2d64f7133a66ad03b", 4888),
		Voz("en_US-lessac-medium", Locale.US, "Lessac (Estados Unidos)", R.string.calidad_media, "Blizzard 2013 (investigación)", true,
			"en/en_US/lessac/medium/en_US-lessac-medium", 63_201_294, "5efe09e69902187827af646e1a6e9d269dee769f9877d17b16b1b46eeaaf019f", 4885),
	)

	fun porId(id: String): Voz? = CATALOGO.firstOrNull { it.id == id }
}
