package com.xebolax.tiresias.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.xebolax.tiresias.R
import com.xebolax.tiresias.actualizaciones.GestorActualizaciones
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Tipo
import com.xebolax.tiresias.voz.Voz

/**
 * Pantalla principal: si Tiresias está activo y qué falta para que funcione
 * bien (cada cosa es un botón que la arregla), y lo que se usa a menudo. Lo
 * demás está en el menú lateral («Abrir menú», arriba a la izquierda). La
 * primera vez lleva al asistente.
 */
class ActividadPrincipal : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var actualizaciones: GestorActualizaciones
	private var vozPrueba: Voz? = null

	private val pedirNotificaciones = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { construir() }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		actualizaciones = GestorActualizaciones(this)
		p = pantalla(getString(R.string.app_name), destinoActual = R.id.lateral_inicio) { abrirDestino(this, it) }
		if (!Ajustes(this).asistenteHecho && estado == null) startActivity(Intent(this, ActividadAsistente::class.java))
		actualizaciones.alIniciar()
	}

	override fun onResume() {
		super.onResume()
		construir()
	}

	override fun onDestroy() {
		vozPrueba?.cerrar()
		super.onDestroy()
	}

	private fun construir() {
		p.vaciar()
		val activo = tiresiasActivo()
		p.encabezado(getString(R.string.ui_estado))
		p.estadoVivo(getString(if (activo) R.string.ui_tiresias_activo else R.string.ui_tiresias_inactivo))
		p.estado(getString(if (activo) R.string.ui_servicio_bien else R.string.ui_servicio_mal), activo) { abrirAccesibilidad() }
		// Estaba encendido y ya no lo está, sin que se apagara a mano: lo ha cerrado el sistema (ahorro de batería)
		if (!activo && com.xebolax.tiresias.base.Ajustes(this).p.getBoolean(com.xebolax.tiresias.base.Claves.SERVICIO_ENCENDIDO, false)) {
			p.estado(getString(R.string.ui_servicio_cerrado, Build.MANUFACTURER), false) { startActivity(Intent(this, ActividadAsistente::class.java)) }
		}
		val bateria = sinOptimizar()
		p.estado(getString(if (bateria) R.string.ui_bateria_bien else R.string.ui_bateria_mal), bateria) { pedirBateria() }
		if (Build.VERSION.SDK_INT >= 33 && !activo) p.parrafo(getString(R.string.ui_ajustes_restringidos))
		// El permiso de notificaciones: para el aviso de «Tiresias en pausa» con «Reanudar» (Backtalk 54)
		if (Build.VERSION.SDK_INT >= 33) {
			val avisos = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
			p.estado(getString(if (avisos) R.string.ui_notificaciones_bien else R.string.ui_notificaciones_mal), avisos) {
				if (!avisos) pedirNotificaciones.launch(android.Manifest.permission.POST_NOTIFICATIONS)
			}
		}

		p.encabezado(getString(R.string.ui_uso))
		p.boton(getString(R.string.ui_probar_voz)) { probarVoz() }
		p.boton(getString(R.string.ui_tutorial)) { startActivity(Intent(this, ActividadTutorial::class.java)) }
		p.boton(getString(R.string.ui_asistente)) { startActivity(Intent(this, ActividadAsistente::class.java)) }
		p.botonSecundario(getString(R.string.ui_ajustes_voz_sistema)) { abrirAjustesTts() }
		p.parrafo(getString(R.string.ui_mas_en_menu))
	}

	private fun tiresiasActivo(): Boolean {
		if (ServicioTiresias.instancia != null) return true
		val am = getSystemService(AccessibilityManager::class.java) ?: return false
		return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
			.any { it.resolveInfo.serviceInfo.packageName == packageName }
	}

	private fun sinOptimizar(): Boolean = getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

	private fun abrirAccesibilidad() {
		runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
	}

	private fun pedirBateria() {
		if (sinOptimizar()) return
		val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
		runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
	}

	private fun abrirAjustesTts() {
		runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
	}

	/** Prueba la voz: con el servicio si está activo; si no, con una voz propia de esta pantalla. */
	private fun probarVoz() {
		val texto = getString(R.string.ui_frase_prueba)
		val s = ServicioTiresias.instancia
		if (s != null) {
			s.probarVoz(texto)
			return
		}
		val v = vozPrueba ?: Voz(this).also { vozPrueba = it }
		v.decir(Enunciado(texto, tipo = Tipo.SISTEMA))
	}
}
