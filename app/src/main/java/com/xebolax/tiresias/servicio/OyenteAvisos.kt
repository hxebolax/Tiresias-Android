package com.xebolax.tiresias.servicio

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xebolax.tiresias.base.Registro

/**
 * Oyente de notificaciones (con el permiso de acceso a las notificaciones que
 * da el usuario): lee también las que Android no manda al lector de pantalla,
 * como SandFly (NotificationListService).
 *
 * - **Con el móvil bloqueado**, la notificación entera: el evento de
 *   accesibilidad solo trae la versión pública, que muchas apps no rellenan.
 * - Las que retiene **No molestar** y las que **se actualizan sin avisar**
 *   (otro mensaje en el mismo chat): si al poco no ha llegado su evento.
 *
 * No se guarda nada en disco: pasan a [com.xebolax.tiresias.lectura.Avisos],
 * que las lee y las deja en el historial en memoria.
 */
class OyenteAvisos : NotificationListenerService() {
	/** Lo último leído de cada notificación (título y texto), para no repetir lo que no cambia. */
	private val ultimos = object : LinkedHashMap<String, String>(64, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		conectado = true
		// Lo que ya está en la persiana al conectar no es nuevo
		runCatching { activeNotifications }.getOrNull()?.forEach { ultimos[it.key] = firma(it.notification) }
		Registro.i(ETIQUETA, "Oyente de notificaciones conectado")
	}

	override fun onListenerDisconnected() {
		conectado = false
		super.onListenerDisconnected()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification?, ranking: RankingMap?) {
		sbn ?: return
		val lector = ServicioTiresias.instancia?.lectorPruebas ?: return
		try {
			if (sbn.packageName == packageName) return
			val n = sbn.notification ?: return
			if (!seLeeria(n)) return
			// Importancia del canal: las silenciosas del todo (mínima) no
			val r = Ranking()
			if (ranking?.getRanking(sbn.key, r) == true && r.importance < NotificationManager.IMPORTANCE_LOW) return
			val f = firma(n)
			if (f.isBlank() || ultimos[sbn.key] == f) return
			ultimos[sbn.key] = f
			lector.manejador.post { lector.avisos.alNotificarDesdeOyente(sbn.packageName, n) }
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "Fallo con una notificación del oyente", e)
		}
	}

	/** Las que no se leen nunca solas: llamadas (las dice el sistema), progreso, en curso, de servicio y resúmenes de grupo. */
	private fun seLeeria(n: Notification): Boolean {
		if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_PROGRESS) return false
		if ((n.extras?.getInt(Notification.EXTRA_PROGRESS_MAX) ?: 0) > 0) return false
		val fuera = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY
		return n.flags and fuera == 0
	}

	private fun firma(n: Notification?): String {
		val e = n?.extras ?: return ""
		val titulo = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
		val texto = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
		return "$titulo|$texto".trim('|')
	}

	companion object {
		private const val ETIQUETA = "OyenteAvisos"

		/** Si Android tiene el oyente conectado ahora (con el acceso dado). */
		@Volatile var conectado = false
			private set

		/** Si el usuario ha dado a Tiresias el acceso a las notificaciones. */
		fun tieneAcceso(c: Context): Boolean {
			val nombre = ComponentName(c, OyenteAvisos::class.java).flattenToString()
			return Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")?.split(':')?.contains(nombre) == true
		}
	}
}
