package com.xebolax.tiresias.servicio

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ui.ActividadPrincipal

/**
 * Que Android no cierre el lector al limpiar Recientes: el propio servicio de
 * accesibilidad pasa a primer plano, con una notificación discreta.
 *
 * Al quitar una tarea de Recientes, Android marca para matar los procesos de esa
 * app («remove task», `ActivityTaskSupervisor.cleanUpRemovedTask`) salvo los que
 * tienen un servicio en primer plano; un servicio de accesibilidad no cuenta como
 * tal. En el emulador el proceso queda marcado y sobrevive, pero en móviles con
 * capa propia (Infinix con Android 16, informe de un usuario) el lector se para.
 * Una detención forzosa no se evita así: esa quita además el servicio de
 * Accesibilidad, y por eso el asistente explica el candado de Recientes de cada marca.
 *
 * También da red con el móvil en reposo durante la ayuda remota. Sin el permiso de
 * notificaciones sigue en primer plano, solo que sin aviso visible. Se quita en
 * Ajustes, Notificaciones.
 */
object Guarda {
	private const val ETIQUETA = "Guarda"
	private const val CANAL = "guarda"
	private const val ID = 7320

	private var oyente: SharedPreferences.OnSharedPreferenceChangeListener? = null
	/** Con una ayuda remota en marcha hace falta aunque se haya quitado: da red con el móvil en reposo. */
	@Volatile private var enAyuda = false

	/** Empieza a vigilar el ajuste y lo aplica. Se llama al conectarse el servicio. */
	fun iniciar(s: AccessibilityService) {
		val a = Ajustes(s)
		aplicar(s, a.guardaActiva || enAyuda)
		val o = SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
			if (clave == Claves.GUARDA_ACTIVA) aplicar(s, a.guardaActiva || enAyuda)
		}
		oyente = o
		a.p.registerOnSharedPreferenceChangeListener(o)
	}

	/** Una ayuda remota empieza o termina. */
	fun enAyuda(s: AccessibilityService, si: Boolean) {
		enAyuda = si
		aplicar(s, si || Ajustes(s).guardaActiva)
	}

	/** Deja de vigilar el ajuste y quita el primer plano. Se llama al cerrarse el servicio. */
	fun terminar(s: AccessibilityService) {
		oyente?.let { Ajustes(s).p.unregisterOnSharedPreferenceChangeListener(it) }
		oyente = null
		aplicar(s, false)
	}

	private fun aplicar(s: AccessibilityService, si: Boolean) {
		try {
			if (si) poner(s) else s.stopForeground(Service.STOP_FOREGROUND_REMOVE)
		} catch (e: Exception) {
			// Android puede negarlo en algún móvil: el lector sigue funcionando igual que antes
			Registro.w(ETIQUETA, "No se pudo cambiar el primer plano del lector", e)
		}
	}

	private fun poner(s: AccessibilityService) {
		val t = Idioma.envolver(s)
		if (Build.VERSION.SDK_INT >= 26) {
			s.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
				NotificationChannel(CANAL, t.getString(R.string.guarda_canal), NotificationManager.IMPORTANCE_MIN).apply {
					setSound(null, null)
					enableVibration(false)
					setShowBadge(false)
				},
			)
		}
		val abrir = PendingIntent.getActivity(s, 7, Intent(s, ActividadPrincipal::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
		val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(s, CANAL) else @Suppress("DEPRECATION") Notification.Builder(s)
		val n = b.setSmallIcon(R.drawable.ic_toque_directo)
			.setContentTitle(t.getString(R.string.guarda_titulo))
			.setContentText(t.getString(R.string.guarda_texto))
			.setOngoing(true)
			.setCategory(Notification.CATEGORY_SERVICE)
			.setContentIntent(abrir)
			.build()
		if (Build.VERSION.SDK_INT >= 34) s.startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
		else s.startForeground(ID, n)
	}
}
