package com.xebolax.tiresias.servicio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro

/**
 * La notificación de «Tiresias en pausa» (Backtalk 54 y 127): mientras está
 * suspendido, una notificación fija con «Reanudar», para quien ayuda al
 * usuario o por si no recuerda cómo volver. No va en ningún grupo, para que
 * no quede escondida entre otras. Sin el permiso de notificaciones (Android 13
 * o posterior), no sale y no pasa nada más.
 */
object AvisoPausa {
	private const val CANAL = "pausa"
	private const val ID = 7301
	const val REANUDAR = "com.xebolax.tiresias.REANUDAR"

	fun mostrar(c: Context) {
		if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
		val nm = c.getSystemService(NotificationManager::class.java) ?: return
		val t = Idioma.envolver(c)
		try {
			if (Build.VERSION.SDK_INT >= 26) {
				nm.createNotificationChannel(NotificationChannel(CANAL, t.getString(R.string.pausa_canal), NotificationManager.IMPORTANCE_DEFAULT).apply {
					setSound(null, null)
					enableVibration(false)
				})
			}
			val reanudar = PendingIntent.getBroadcast(c, 0, Intent(REANUDAR).setPackage(c.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CANAL) else @Suppress("DEPRECATION") Notification.Builder(c)
			val n = b.setSmallIcon(R.drawable.ic_toque_directo)
				.setContentTitle(t.getString(R.string.pausa_titulo))
				.setContentText(t.getString(R.string.pausa_texto))
				.setOngoing(true)
				.setCategory(Notification.CATEGORY_STATUS)
				.setContentIntent(reanudar)
				.addAction(Notification.Action.Builder(null, t.getString(R.string.pausa_reanudar), reanudar).build())
				.build()
			nm.notify(ID, n)
		} catch (e: Exception) {
			Registro.w("AvisoPausa", "No se pudo poner la notificación de pausa", e)
		}
	}

	fun quitar(c: Context) {
		runCatching { c.getSystemService(NotificationManager::class.java)?.cancel(ID) }
	}
}
