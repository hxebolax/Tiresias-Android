package com.xebolax.tiresias.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes

/**
 * Asistente de primer arranque: lo que hay que hacer una vez para que Tiresias
 * funcione bien y no lo cierre el sistema. Un paso por pantalla («Paso 2 de 6»),
 * cada uno con el botón que lo hace y la explicación de para qué sirve.
 *
 * Responde a dos peticiones de Backtalk (193 y 272): los fabricantes que matan
 * el lector necesitan que se quite la optimización de batería y se permita el
 * inicio automático, y en Android 13 o posterior un APK instalado fuera de una
 * tienda necesita «Permitir ajustes restringidos».
 */
class ActividadAsistente : ActividadBase() {
	private lateinit var p: Pantalla
	private var paso = 0

	private data class Paso(val titulo: Int, val texto: Int, val boton: Int?, val accion: (() -> Unit)?)

	private val pasos by lazy {
		listOf(
			Paso(R.string.asis_1_titulo, R.string.asis_1, null, null),
			Paso(R.string.asis_2_titulo, if (Build.VERSION.SDK_INT >= 33) R.string.asis_2 else R.string.asis_2_antiguo, R.string.asis_2_boton) { abrirInfoApp() },
			Paso(R.string.asis_3_titulo, R.string.asis_3, R.string.asis_3_boton) { abrir(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
			Paso(R.string.asis_4_titulo, R.string.asis_4, R.string.asis_4_boton) { bateria() },
			Paso(R.string.asis_5_titulo, R.string.asis_5, R.string.asis_5_boton) { abrirInfoApp() },
			Paso(R.string.asis_6_titulo, R.string.asis_6, R.string.asis_6_boton) { startActivity(Intent(this, ActividadTutorial::class.java)) },
		)
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		paso = estado?.getInt("paso") ?: 0
		p = pantalla(getString(R.string.ui_asistente))
		construir()
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putInt("paso", paso)
	}

	private fun construir() {
		p.vaciar()
		val actual = pasos[paso]
		p.encabezado(getString(R.string.asis_paso, paso + 1, pasos.size, getString(actual.titulo)))
		p.parrafo(getString(actual.texto, Build.MANUFACTURER))
		if (actual.boton != null && actual.accion != null) p.boton(getString(actual.boton)) { actual.accion.invoke() }
		if (paso < pasos.lastIndex) p.boton(getString(R.string.asis_siguiente)) { paso++; construir() }
		else p.boton(getString(R.string.asis_terminar)) {
			Ajustes(this).asistenteHecho = true
			finish()
		}
		if (paso > 0) p.botonSecundario(getString(R.string.asis_anterior)) { paso--; construir() }
	}

	private fun abrir(i: Intent) {
		runCatching { startActivity(i) }
	}

	private fun abrirInfoApp() = abrir(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

	private fun bateria() {
		val pm = getSystemService(android.os.PowerManager::class.java)
		if (pm?.isIgnoringBatteryOptimizations(packageName) == true) {
			abrir(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
			return
		}
		runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
			.onFailure { abrir(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
	}
}
