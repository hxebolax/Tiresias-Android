/*
 * Basado en PicoTts.kt de Shortcut Menu (https://github.com/aaron-gh/shortcut-menu),
 * de aaron-gh, con licencia Apache 2.0. Cambios para Rescate Accesible y Tiresias (mismo autor): voz española
 * (es-ES de AOSP) además de las inglesas, voces copiadas al almacenamiento protegido
 * (funciona antes de desbloquear), velocidad y tono, reinicio y comentarios en español.
 */
package com.xebolax.tiresias.voz

import android.content.Context
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import java.io.File
import java.util.Locale

/** Una de las voces de Pico incluidas en la app, en assets/pico: su análisis de texto y su señal. */
enum class VozPico(val codigo: String, val ta: String, val sg: String, val locale: Locale) {
	ESPANOL("es-ES", "es-ES_ta.bin", "es-ES_zl0_sg.bin", Locale("es", "ES")),
	INGLES_EEUU("en-US", "en-US_ta.bin", "en-US_lh0_sg.bin", Locale.US),
	INGLES_GB("en-GB", "en-GB_ta.bin", "en-GB_kh0_sg.bin", Locale.UK);

	companion object {
		/** Español para cualquier español; inglés de EE. UU. en EE. UU., británico en el resto; español si no. */
		fun para(locale: Locale): VozPico = when (locale.language) {
			"es" -> ESPANOL
			"en" -> if (locale.country == "US") INGLES_EEUU else INGLES_GB
			else -> ESPANOL
		}

		fun porCodigo(codigo: String): VozPico? = entries.firstOrNull { it.codigo.equals(codigo, ignoreCase = true) }
	}
}

/**
 * SVOX Pico, el motor de voz incluido en la app, abierto con una voz. Produce
 * muestras de 16 kHz, 16 bits, mono. Úsalo desde un solo hilo.
 *
 * No depende del motor de voz del sistema ni de Internet: es la voz de reserva
 * de Tiresias cuando también falla eSpeak.
 */
class PicoNativo private constructor(private var manejador: Long, val voz: VozPico) {

	/** Las muestras de [texto] (ya preparado con [Marcado]), o null si Pico falló. */
	fun sintetizar(texto: String): ShortArray? {
		if (manejador == 0L || simularFallo) return null
		return try {
			nativeSynthesize(manejador, texto)
		} catch (e: Throwable) {
			Registro.e(ETIQUETA, "Pico falló al sintetizar", e)
			null
		}
	}

	/** Deja el motor limpio para la siguiente frase. */
	fun reiniciar() {
		if (manejador != 0L) nativeReset(manejador)
	}

	fun cerrar() {
		if (manejador != 0L) {
			nativeClose(manejador)
			manejador = 0L
		}
	}

	companion object {
		const val FRECUENCIA = 16000
		private const val ETIQUETA = "Pico"

		/** Solo para las pruebas: Pico se comporta como si estuviera dañado. */
		@Volatile var simularFallo = false
		@Volatile private var cargada: Boolean? = null

		/** Si la biblioteca nativa de Pico se puede cargar en este móvil. */
		fun bibliotecaDisponible(): Boolean {
			cargada?.let { return it }
			val ok = try {
				System.loadLibrary("tiresiaspico")
				true
			} catch (e: Throwable) {
				Registro.e(ETIQUETA, "No se pudo cargar la biblioteca de Pico", e)
				false
			}
			cargada = ok
			return ok
		}

		/** Abre Pico con [voz], o devuelve null si no puede. */
		fun abrir(contexto: Context, voz: VozPico): PicoNativo? {
			if (!bibliotecaDisponible()) return null
			return try {
				val ta = archivoVoz(contexto, voz.ta)
				val sg = archivoVoz(contexto, voz.sg)
				val manejador = nativeOpen(ta.path, sg.path)
				if (manejador == 0L) {
					// Archivo de voz dañado: se borra para copiarlo de nuevo la próxima vez
					Registro.w(ETIQUETA, "Pico no pudo abrir la voz ${voz.codigo}; se volverá a copiar")
					ta.delete()
					sg.delete()
					null
				} else PicoNativo(manejador, voz)
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudo abrir Pico con ${voz.codigo}", e)
				null
			}
		}

		/**
		 * Pico lee sus voces de archivos, así que se copian de los assets la primera
		 * vez, al almacenamiento protegido. La copia solo ocupa su sitio cuando está
		 * entera, y se repite si el tamaño no coincide (archivo dañado).
		 */
		private fun archivoVoz(contexto: Context, nombre: String): File {
			val protegido = Almacen.protegido(contexto)
			val dir = File(protegido.noBackupFilesDir, "pico").apply { mkdirs() }
			val archivo = File(dir, nombre)
			val esperado = runCatching { protegido.assets.openFd("pico/$nombre").use { it.length } }.getOrDefault(-1L)
			if (!archivo.exists() || (esperado > 0 && archivo.length() != esperado)) {
				val parcial = File(dir, "$nombre.parcial")
				protegido.assets.open("pico/$nombre").use { entrada ->
					parcial.outputStream().use { entrada.copyTo(it) }
				}
				archivo.delete()
				if (!parcial.renameTo(archivo)) throw IllegalStateException("No se pudo guardar $nombre")
			}
			return archivo
		}

		@JvmStatic private external fun nativeOpen(taPath: String, sgPath: String): Long
		@JvmStatic private external fun nativeSynthesize(handle: Long, text: String): ShortArray?
		@JvmStatic private external fun nativeReset(handle: Long)
		@JvmStatic private external fun nativeClose(handle: Long)
	}
}

/**
 * Prepara el texto para Pico: le pone la velocidad y el tono con su marcado
 * propio y quita lo que Pico tomaría por marcado o no sabe leer.
 */
object Marcado {
	/** [velocidad] y [tono] en por ciento (100 = normal). Pico admite velocidad de 20 a 500 y tono de 50 a 200. */
	fun preparar(texto: String, velocidad: Int, tono: Int): String {
		val limpio = limpiar(texto)
		val v = velocidad.coerceIn(20, 500)
		val t = tono.coerceIn(50, 200)
		if (v == 100 && t == 100) return limpio
		// Mismo marcado que el motor Pico de AOSP (picottsengine.cpp)
		return "<speed level='$v'><pitch level='$t'>$limpio</pitch></speed>"
	}

	/** Quita los signos de marcado y los caracteres de control; los saltos de línea pasan a pausas. */
	fun limpiar(texto: String): String {
		val sb = StringBuilder(texto.length)
		for (c in texto) {
			when {
				c == '<' || c == '>' -> sb.append(' ')
				c == '\n' || c == '\r' -> sb.append(". ")
				c.isISOControl() -> sb.append(' ')
				else -> sb.append(c)
			}
		}
		return sb.toString().replace(Regex("\\s+"), " ").replace(Regex("(\\. ){2,}"), ". ").trim()
	}

	/**
	 * Parte un texto largo en frases de como mucho [maximo] caracteres, para que
	 * empiece a sonar enseguida y se pueda cortar entre frases.
	 */
	fun frases(texto: String, maximo: Int = 300): List<String> {
		val resultado = mutableListOf<String>()
		val partes = texto.split(Regex("(?<=[.!?;:])\\s+"))
		val actual = StringBuilder()
		for (parte in partes) {
			if (parte.isBlank()) continue
			if (actual.isNotEmpty() && actual.length + parte.length + 1 > maximo) {
				resultado += actual.toString()
				actual.clear()
			}
			if (parte.length > maximo) {
				// Frase sin puntos demasiado larga: por comas o espacios
				var resto = parte
				while (resto.length > maximo) {
					val corte = resto.lastIndexOf(',', maximo).takeIf { it > maximo / 2 }
						?: resto.lastIndexOf(' ', maximo).takeIf { it > 0 } ?: maximo
					resultado += resto.substring(0, corte + 1).trim()
					resto = resto.substring(corte + 1)
				}
				if (resto.isNotBlank()) actual.append(resto.trim())
			} else {
				if (actual.isNotEmpty()) actual.append(' ')
				actual.append(parte)
			}
		}
		if (actual.isNotBlank()) resultado += actual.toString()
		return resultado
	}
}
