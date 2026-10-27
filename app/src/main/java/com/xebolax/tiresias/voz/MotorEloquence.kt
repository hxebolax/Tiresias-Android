package com.xebolax.tiresias.voz

import android.content.Context
import android.os.Build
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import org.evvdroid.Dictionaries
import org.evvdroid.Eci
import org.evvdroid.EvvEngine
import org.evvdroid.EvvLibrary
import org.evvdroid.Languages
import org.evvdroid.Pauses
import org.evvdroid.Prosody
import org.evvdroid.TextFixes
import org.evvdroid.TextPieces
import java.io.File
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * La biblioteca de Eloquence (openevv, a través de evvdroid). Viaja comprimida
 * dentro del APK de cada procesador (`lib/<procesador>/libevvjni_gz.so`, unos
 * 16 MB en vez de 50) y se descomprime la primera vez al almacenamiento
 * protegido de Tiresias, que se puede leer antes del primer desbloqueo; se
 * vuelve a descomprimir si el APK trae otra.
 */
object BibliotecaEloquence {
	private const val ETIQUETA = "Eloquence"
	private const val COMPRIMIDA = "libevvjni_gz.so"

	@Volatile private var lista: Boolean? = null

	/** La entrada del APK con la biblioteca para este móvil, si la hay (no la hay en x86 de 32 bits). */
	private fun entrada(c: Context): Pair<ZipFile, java.util.zip.ZipEntry>? {
		val apks = listOfNotNull(c.applicationInfo.sourceDir) + c.applicationInfo.splitSourceDirs.orEmpty()
		for (apk in apks) {
			val zip = runCatching { ZipFile(apk) }.getOrNull() ?: continue
			for (abi in Build.SUPPORTED_ABIS) {
				zip.getEntry("lib/$abi/$COMPRIMIDA")?.let { return zip to it }
			}
			zip.close()
		}
		return null
	}

	/** Si este APK trae Eloquence para este móvil (sin descomprimir nada). */
	fun disponible(c: Context): Boolean = entrada(c)?.let { (zip, _) -> zip.close(); true } ?: false

	/** Descomprime la biblioteca si hace falta y le dice al motor dónde está. False si no hay para este móvil. */
	@Synchronized
	fun preparar(c: Context): Boolean {
		lista?.let { return it }
		val (zip, e) = entrada(c) ?: run {
			lista = false
			return false
		}
		val carpeta = File(Almacen.protegido(c).filesDir, "eloquence").apply { mkdirs() }
		val destino = File(carpeta, "libevvjni.so")
		val marca = File(carpeta, "version")
		val esta = "${e.crc}-${e.size}"
		try {
			zip.use { z ->
				if (!destino.isFile || runCatching { marca.readText() }.getOrNull() != esta) {
					val parcial = File(carpeta, "libevvjni.so.parte")
					z.getInputStream(e).use { entrada -> GZIPInputStream(entrada, 64 * 1024).use { g -> parcial.outputStream().use { g.copyTo(it, 64 * 1024) } } }
					destino.setWritable(true)
					destino.delete()
					if (!parcial.renameTo(destino)) throw java.io.IOException("No se pudo guardar la biblioteca de Eloquence")
					marca.writeText(esta)
				}
			}
			// Solo de lectura: Android no dejará cargar una biblioteca que se pueda modificar
			destino.setReadOnly()
			EvvLibrary.path = destino.absolutePath
			lista = org.evvdroid.EvvNative.loaded
			if (lista != true) Registro.e(ETIQUETA, "La biblioteca de Eloquence no se cargó", org.evvdroid.EvvNative.loadError)
		} catch (ex: java.io.IOException) {
			Registro.e(ETIQUETA, "No se pudo descomprimir Eloquence", ex)
			lista = false
		}
		return lista == true
	}
}

/**
 * Eloquence dentro de Tiresias, como eSpeak y Pico: diez idiomas (inglés de
 * EE. UU. y británico, español de España y de México, francés de Francia y de
 * Canadá, alemán, italiano, polaco y japonés) y sus ocho voces, con todo lo que
 * el motor deja cambiar (Ajustes, Voz, «Ajustes de Eloquence»).
 *
 * Como en evvdroid: el texto va en trozos del tamaño de una frase (el motor no
 * puede abandonar una frase, así que cortar espera como mucho a un trozo), la
 * velocidad y el tono van delante de cada trozo como anotaciones (el motor no
 * acepta cambios mientras habla) y las pausas se acortan como en el driver de
 * NVDA. Lo que dice Tiresias ya llega con la puntuación, los números y los
 * emojis resueltos: aquí no se vuelven a tocar.
 */
class MotorEloquence(private val contexto: Context, private val pista: PistaAudio) : Motor {
	override val id = Ajustes.MOTOR_ELOQUENCE
	override val nombre = "Eloquence"
	private val ajustes = Ajustes(contexto)
	private var motor: EvvEngine? = null
	/** Lo puesto en el motor, para no volver a ponerlo en cada frase. */
	private var puesto = ""
	/** Los diccionarios cargados (ruta y fecha de cada uno). */
	private var diccionarios = ""
	@Volatile private var cortado = false

	/** La lengua del motor para [idioma], con la variante elegida en Ajustes (España o México, EE. UU. o Reino Unido...). */
	private fun lengua(idioma: Locale): Int? {
		val pais = when (idioma.language) {
			"es" -> ajustes.eloquenceEspanol.takeIf { it != "auto" }
			"en" -> ajustes.eloquenceIngles.takeIf { it != "auto" }
			"fr" -> ajustes.eloquenceFrances.takeIf { it != "auto" }
			else -> null
		} ?: idioma.country.takeIf { it.isNotEmpty() } ?: if (idioma.language == "es") "ES" else ""
		// Si el idioma pedido no está en Eloquence (portugués, ruso...), habla en inglés: mejor que callar
		return Languages.match(idioma.language, pais)?.first ?: Languages.match("en", "")?.first
	}

	private fun abrir(lengua: Int): EvvEngine? {
		motor?.takeIf { it.language == lengua }?.let { return it }
		motor?.close()
		motor = null
		puesto = ""
		diccionarios = ""
		if (!BibliotecaEloquence.preparar(contexto)) return null
		return EvvEngine.open(lengua).also { motor = it }
	}

	/** Pone en el motor la voz, sus parámetros, la frecuencia, las abreviaturas y los diccionarios, si han cambiado. */
	private fun aplicar(m: EvvEngine) {
		val voz = ajustes.eloquenceVoz
		val forma = (0 until Eci.NUM_VOICE_PARAMS).mapNotNull { p -> ajustes.eloquenceParametro(voz, p).takeIf { it >= 0 }?.let { p to it } }.toMap() +
			// La velocidad de la voz con la de Tiresias al 100 %: la de Eloquence en Ajustes (como en NVDA)
			(Eci.VOICE_SPEED to velocidadMotor(ajustes.eloquenceVelocidad, ajustes.eloquenceAumento))
		val clave = "$voz|$forma|${ajustes.eloquenceMuestreo}|${ajustes.eloquenceAbreviaturas}"
		if (clave != puesto) {
			m.setSampleRate(ajustes.eloquenceMuestreo)
			m.setAbbreviations(ajustes.eloquenceAbreviaturas)
			if (m.applyVoice(voz, forma)) puesto = clave
		}
		val dic = Eci.DICT_VOLUMES.mapNotNull { v -> ajustes.eloquenceDiccionario(v)?.let { File(it) }?.takeIf { it.isFile }?.let { v to it } }
		val claveDic = dic.joinToString { (v, f) -> "$v:${f.path}:${f.lastModified()}" }
		if (claveDic != diccionarios) {
			m.forgetDictionaries()
			for ((v, f) in dic) runCatching { Dictionaries.load(m, v, f) }.onFailure { Registro.w(ETIQUETA, "No se pudo cargar el diccionario $v de Eloquence", it) }
			diccionarios = claveDic
		}
	}

	override fun hablar(texto: String, idioma: Locale, velocidad: Int, tono: Int, sigue: () -> Boolean, alEmpezar: () -> Unit): Resultado {
		cortado = false
		// La biblioteca, lo primero: cualquier pregunta al motor (sus idiomas) la carga, y sin la ruta puesta
		// se quedaría sin cargar para siempre (lo que hacía que sonara eSpeak en un móvil recién encendido)
		if (!BibliotecaEloquence.preparar(contexto)) return Resultado.FALLO
		val lengua = lengua(idioma) ?: return Resultado.FALLO
		val m = abrir(lengua) ?: return Resultado.FALLO
		aplicar(m)
		m.setRatePercent(velocidad)
		m.setPitchPercent(tono)
		val limpio = TextFixes.apply(Marcado.limpiar(texto))
		if (limpio.isBlank()) return Resultado.HABLADO
		val trozos = TextPieces.split(limpio)
		val delante = Prosody.prefix(ajustes.eloquencePrediccion)
		val pausas = ajustes.eloquencePausas
		val bufer = ByteArray(8192)
		var empezado = false
		for ((i, trozo) in trozos.withIndex()) {
			if (!sigue() || cortado) return Resultado.CORTADO
			if (!m.speak(delante + Pauses.apply(trozo, pausas, i == trozos.lastIndex))) {
				Registro.w(ETIQUETA, "Eloquence no aceptó un trozo de ${trozo.length} caracteres")
				return Resultado.FALLO
			}
			while (true) {
				val n = m.read(bufer)
				if (n <= 0) break
				if (!sigue() || cortado) {
					m.stop()
					return Resultado.CORTADO
				}
				if (!empezado) {
					empezado = true
					alEmpezar()
				}
				val muestras = ShortArray(n / 2) { k -> ((bufer[2 * k].toInt() and 0xFF) or (bufer[2 * k + 1].toInt() shl 8)).toShort() }
				if (!pista.escribir(muestras, m.sampleRateHz, sigue)) {
					m.stop()
					return if (sigue()) Resultado.FALLO else Resultado.CORTADO
				}
			}
		}
		if (!sigue() || cortado) return Resultado.CORTADO
		return if (pista.esperarFin(sigue)) Resultado.HABLADO else Resultado.CORTADO
	}

	override fun cortar() {
		cortado = true
		motor?.stop()
		pista.cortar()
	}

	override fun cerrar() {
		motor?.close()
		motor = null
		puesto = ""
		diccionarios = ""
	}

	companion object {
		private const val ETIQUETA = "Eloquence"

		/** Los nombres de las ocho voces, como siempre se han llamado. */
		val VOCES: List<String> get() = Eci.PRESET_NAMES.toList()

		/**
		 * La velocidad del motor para la velocidad [porcentaje] (0 a 100) de NVDA,
		 * con su «aumento de velocidad»: la misma cuenta que el driver IBMTTS de
		 * NVDA (de 40 a 156, por 1,6 con el aumento), para que suene igual que allí.
		 */
		fun velocidadMotor(porcentaje: Int, aumento: Boolean): Int {
			val base = Math.round(40 + porcentaje.coerceIn(0, 100) * (156 - 40) / 100.0).toInt()
			return (if (aumento) Math.round(base * 1.6).toInt() else base).coerceIn(0, Eci.SPEED_MAX)
		}

		/** Los parámetros de una voz que se pueden cambiar (la velocidad es la de Tiresias). */
		val PARAMETROS = listOf(
			Eci.VOICE_GENDER, Eci.VOICE_HEAD_SIZE, Eci.VOICE_PITCH_BASELINE, Eci.VOICE_PITCH_FLUCTUATION,
			Eci.VOICE_ROUGHNESS, Eci.VOICE_BREATHINESS, Eci.VOICE_VOLUME,
		)

		/** Las frecuencias de muestreo que se pueden elegir, en hercios. */
		val FRECUENCIAS = listOf(8000, 11025, 16000, 22050)

		/** Lo que tiene de fábrica la voz [voz] (sus parámetros), para enseñarlo en Ajustes. */
		fun formaDeFabrica(c: Context, voz: Int): Map<Int, Int> {
			if (!BibliotecaEloquence.preparar(c)) return emptyMap()
			val m = EvvEngine.available.firstOrNull()?.let { EvvEngine.open(it) } ?: return emptyMap()
			return try {
				m.presetShape(voz)
			} finally {
				m.close()
			}
		}
	}
}
