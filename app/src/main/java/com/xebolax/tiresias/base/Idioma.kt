package com.xebolax.tiresias.base

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Idioma de la app (español o inglés), independiente del idioma del sistema.
 *
 * Importa en un lector: si alguien cambió por error el idioma del móvil a uno
 * que el usuario no entiende, Tiresias sigue hablando en el suyo. En Android 13
 * o posterior se sincroniza con «Idioma de la app» de los ajustes del sistema.
 */
object Idioma {
	val DISPONIBLES = listOf("es", "en")

	/** El código elegido ("es", "en") o "" si sigue al sistema. */
	fun elegido(contexto: Context): String {
		if (Build.VERSION.SDK_INT >= 33) {
			val sistema = runCatching {
				contexto.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
			}.getOrNull()
			if (sistema != null && sistema in DISPONIBLES) return sistema
		}
		return Ajustes(contexto).idioma
	}

	/** El idioma efectivo: el elegido o, si no, el del sistema si la app lo tiene; si no, español. */
	fun efectivo(contexto: Context): Locale {
		val elegido = elegido(contexto)
		if (elegido.isNotEmpty()) return Locale(elegido, if (elegido == "es") "ES" else Locale.getDefault().country)
		val sistema = Locale.getDefault()
		return if (sistema.language in DISPONIBLES) sistema else Locale("es", "ES")
	}

	fun cambiar(contexto: Context, codigo: String) {
		Ajustes(contexto).idioma = codigo
		if (Build.VERSION.SDK_INT >= 33) {
			runCatching {
				contexto.getSystemService(LocaleManager::class.java)?.applicationLocales =
					if (codigo.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(codigo)
			}.onFailure { Registro.w("Idioma", "No se pudo fijar el idioma de la app en el sistema", it) }
		}
	}

	/** Un contexto con los recursos en el idioma de la app (para servicios y para attachBaseContext). */
	fun envolver(contexto: Context): Context {
		val elegido = elegido(contexto)
		if (elegido.isEmpty()) return contexto
		val locale = efectivo(contexto)
		val configuracion = Configuration(contexto.resources.configuration)
		configuracion.setLocale(locale)
		configuracion.setLocales(LocaleList(locale))
		return contexto.createConfigurationContext(configuracion)
	}
}
