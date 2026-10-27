package com.xebolax.tiresias.voz

import android.content.Context
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import java.io.File
import java.util.Locale

/**
 * eSpeak NG 1.52.0 (GPL-3.0-or-later), la voz incluida principal de Tiresias (la de menor latencia).
 *
 * Es independiente de Pico (otro código y otros datos): si eSpeak falla, Pico
 * puede seguir hablando. Su API guarda el estado de forma global en el proceso,
 * así que todo pasa por este objeto, sincronizado.
 *
 * Los datos (fonemas y diccionarios de español, inglés, francés, alemán,
 * italiano y portugués) se copian de los assets al almacenamiento protegido la
 * primera vez, para que funcione también antes de desbloquear.
 */
object EspeakNativo {
	private const val ETIQUETA = "eSpeak"
	private const val VERSION_DATOS = "1.52.0-1"
	/** eSpeak: velocidad en palabras por minuto (80 a 450; 175 es lo normal) y tono (0 a 100; 50 normal). */
	private const val PARAM_VELOCIDAD = 1
	private const val PARAM_TONO = 3

	private var frecuencia = 0

	/** Solo para las pruebas: eSpeak se comporta como si estuviera dañado (se comprueba el cambio a Pico). */
	@Volatile var simularFallo = false
	private var vozActual = ""
	@Volatile private var cargada: Boolean? = null

	/** Si la biblioteca de eSpeak se puede cargar en este móvil. */
	fun disponible(): Boolean {
		cargada?.let { return it }
		val ok = try {
			System.loadLibrary("tiresiasespeak")
			true
		} catch (e: Throwable) {
			Registro.e(ETIQUETA, "No se pudo cargar la biblioteca de eSpeak", e)
			false
		}
		cargada = ok
		return ok
	}

	/** La frecuencia de las muestras (normalmente 22 050 Hz), o 0 si no arrancó. */
	val frecuenciaMuestras: Int get() = frecuencia

	@Synchronized
	fun iniciar(contexto: Context): Boolean {
		if (frecuencia > 0) return true
		if (!disponible()) return false
		return try {
			val raiz = copiarDatos(contexto)
			frecuencia = nativeIniciar(raiz.path)
			if (frecuencia <= 0) Registro.e(ETIQUETA, "eSpeak no arrancó (código $frecuencia)")
			frecuencia > 0
		} catch (e: Throwable) {
			Registro.e(ETIQUETA, "Fallo al iniciar eSpeak", e)
			frecuencia = 0
			false
		}
	}

	/** Las muestras de [texto] con la voz de [locale], o null si falló. */
	@Synchronized
	fun sintetizar(contexto: Context, texto: String, locale: Locale, velocidad: Int, tono: Int): ShortArray? {
		if (simularFallo || !iniciar(contexto)) return null
		return try {
			val voz = vozPara(locale)
			if (voz != vozActual) {
				if (!nativeVoz(voz) && !nativeVoz("es")) return null
				vozActual = voz
			}
			nativeParametro(PARAM_VELOCIDAD, (175 * velocidad / 100).coerceIn(80, 450))
			nativeParametro(PARAM_TONO, (50 * tono / 100).coerceIn(0, 100))
			nativeSintetizar(texto)
		} catch (e: Throwable) {
			Registro.e(ETIQUETA, "eSpeak falló al sintetizar", e)
			null
		}
	}

	/**
	 * Los fonemas IPA de [texto] con la voz de eSpeak [voz] («es», «en-us»...),
	 * para las voces neuronales Piper. Null si eSpeak no arranca.
	 */
	@Synchronized
	fun fonemas(contexto: Context, texto: String, voz: String): String? {
		if (!iniciar(contexto)) return null
		return try {
			if (voz != vozActual) {
				if (!nativeVoz(voz) && !nativeVoz("es")) return null
				vozActual = voz
			}
			nativeFonemas(texto)
		} catch (e: Throwable) {
			Registro.e(ETIQUETA, "eSpeak falló al sacar los fonemas", e)
			null
		}
	}

	/** Corta lo que esté sintetizando (desde cualquier hilo). */
	fun cancelar() {
		if (frecuencia > 0) runCatching { nativeCancelar() }
	}

	fun vozPara(locale: Locale): String = when (locale.language) {
		"es" -> if (locale.country.isEmpty() || locale.country == "ES") "es" else "es-419"
		"en" -> if (locale.country == "US") "en-us" else "en"
		"fr", "de", "it" -> locale.language
		"pt" -> if (locale.country == "BR") "pt-br" else "pt"
		else -> "es"
	}

	/** Copia espeak-ng-data de los assets si falta o es de otra versión. Devuelve la carpeta que la contiene. */
	private fun copiarDatos(contexto: Context): File {
		val protegido = Almacen.protegido(contexto)
		val raiz = File(protegido.noBackupFilesDir, "espeak")
		val marca = File(raiz, "version")
		if (marca.exists() && runCatching { marca.readText() }.getOrNull() == VERSION_DATOS) return raiz
		val datos = File(raiz, "espeak-ng-data")
		datos.deleteRecursively()
		copiar(protegido, "espeak-ng-data", datos)
		marca.writeText(VERSION_DATOS)
		return raiz
	}

	private fun copiar(contexto: Context, ruta: String, destino: File) {
		val hijos = contexto.assets.list(ruta).orEmpty()
		if (hijos.isEmpty()) {
			destino.parentFile?.mkdirs()
			contexto.assets.open(ruta).use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
			return
		}
		destino.mkdirs()
		for (hijo in hijos) copiar(contexto, "$ruta/$hijo", File(destino, hijo))
	}

	@JvmStatic private external fun nativeIniciar(ruta: String): Int
	@JvmStatic private external fun nativeVoz(nombre: String): Boolean
	@JvmStatic private external fun nativeParametro(parametro: Int, valor: Int): Boolean
	@JvmStatic private external fun nativeSintetizar(texto: String): ShortArray?
	@JvmStatic private external fun nativeFonemas(texto: String): String?
	@JvmStatic private external fun nativeCancelar()
	@JvmStatic private external fun nativeTerminar()
}

