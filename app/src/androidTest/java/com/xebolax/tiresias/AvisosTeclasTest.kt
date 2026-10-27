package com.xebolax.tiresias

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.nucleo.Accion
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las notificaciones (se leen, las de una app silenciada no, y desde el
 * historial se abren y se copian), el teclado físico, las teclas de volumen
 * (mantener, tres veces, en los deslizadores) y el aviso de «sigue pensando».
 */
@RunWith(AndroidJUnit4::class)
class AvisosTeclasTest : PruebaTiresias() {

	private fun notificar(titulo: String, texto: String): Int {
		if (Build.VERSION.SDK_INT >= 33) inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("pruebas", "Pruebas", NotificationManager.IMPORTANCE_HIGH))
		val abrir = PendingIntent.getActivity(app, 0, Intent(app, com.xebolax.tiresias.ui.ActividadAcerca::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
		val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(app, "pruebas") else @Suppress("DEPRECATION") Notification.Builder(app))
			.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(titulo).setContentText(texto).setContentIntent(abrir).build()
		val id = (SystemClock.uptimeMillis() % 10000).toInt()
		nm.notify(id, n)
		return id
	}

	@Test
	fun lasNotificacionesSeLeenYSeUsanDesdeElHistorial() {
		abrirMuestrario()
		val m = marca()
		val id = notificar("Prueba de aviso", "Texto del aviso")
		assertNotNull("No se leyó la notificación", dichoQue(8000, m) { it.contains("Prueba de aviso") })
		// Desde el historial: la notificación y sus acciones (abrir, copiar)
		hacer(Accion.HISTORIAL_NOTIFICACIONES, 1500)
		assertTrue(s.ventanaAbiertaPruebas)
		// La entrada de esta notificación (no otra que haya en el historial)
		hacer(Accion.PRIMERO)
		var entrada = ultimo()
		var pasos = 0
		while (!entrada.contains("Prueba de aviso") && pasos++ < 15) entrada = hacer(Accion.SIGUIENTE, 350)
		assertTrue("La notificación no está en el historial: «$entrada»", entrada.contains("Prueba de aviso"))
		val m2 = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		// La lista de sus acciones, con la notificación por título
		assertNotNull("No se abrió la lista de acciones de la notificación", dichoQue(8000, m2) { it.startsWith("Tiresias: Prueba de aviso") })
		esperarCalma(m2)
		assertNotNull("No salieron las acciones de la notificación", irA("Abrir la notificación", 10))
		s.ejecutarPrueba(Accion.ACTIVAR)
		// Abrirla abre su pantalla (Acerca de)
		assertNotNull("No se abrió la notificación", esperar(6000) { s.lectorPruebas?.raiz()?.takeIf { it.packageName == app.packageName } })
		app.getSystemService(NotificationManager::class.java).cancel(id)
	}

	@Test
	fun lasDeUnaAppSilenciadaNoSeLeen() {
		ajuste("apps_silenciadas", setOf(app.packageName))
		val m = marca()
		val id = notificar("Esta no se lee", "Nada")
		SystemClock.sleep(5000)
		assertTrue("Se leyó una notificación de una app silenciada", s.vozPruebas!!.dichoDesde(m).none { it.contains("Esta no se lee") })
		app.getSystemService(NotificationManager::class.java).cancel(id)
	}

	private fun tecla(codigo: Int, abajo: Boolean, meta: Int = 0, repeticion: Int = 0): Boolean {
		val t = SystemClock.uptimeMillis()
		return s.teclaPrueba(KeyEvent(t, t, if (abajo) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, codigo, repeticion, meta))
	}

	@Test
	fun elTecladoFisicoNavega() {
		abrirMuestrario()
		hacer(Accion.PRIMERO)
		// Tiresias (Alt) + flecha derecha: siguiente elemento
		val m = marca()
		tecla(KeyEvent.KEYCODE_ALT_LEFT, true, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
		tecla(KeyEvent.KEYCODE_DPAD_RIGHT, true, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
		tecla(KeyEvent.KEYCODE_DPAD_RIGHT, false, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
		tecla(KeyEvent.KEYCODE_ALT_LEFT, false)
		assertNotNull("Tiresias + flecha derecha no pasó al siguiente", dichoQue(3000, m) { it.startsWith("Guardar") })
	}

	/** Si una tecla corta la voz que está hablando, con el ajuste de ahora. */
	private fun cortaLaTecla(codigo: Int): Boolean {
		val voz = s.vozPruebas!!
		voz.decir(com.xebolax.tiresias.voz.Enunciado("Una frase larga para tener tiempo de pulsar una tecla mientras se dice entera, sin prisa ninguna, de principio a fin."))
		esperar(2000) { voz.hablando.takeIf { it } }
		SystemClock.sleep(300)
		tecla(codigo, true)
		tecla(codigo, false)
		SystemClock.sleep(300)
		val cortada = !voz.hablando
		voz.callar()
		return cortada
	}

	@Test
	fun lasTeclasCortanLaVozSegunElAjuste() {
		abrirMuestrario()
		hacer(Accion.PRIMERO)
		// Que pase antes la pista del elemento (llega tras una pausa y se tomaría por la frase que hay que cortar)
		SystemClock.sleep(2500)
		assertTrue("Con «siempre», una letra debe cortar la voz", cortaLaTecla(KeyEvent.KEYCODE_A))
		ajuste("teclas_cortan", "nunca")
		assertTrue("Con «nunca», una letra no debe cortar la voz", !cortaLaTecla(KeyEvent.KEYCODE_A))
		assertTrue("Control corta la voz siempre", cortaLaTecla(KeyEvent.KEYCODE_CTRL_LEFT))
		// «Solo en campos»: el foco está en un encabezado, no en un campo de texto
		ajuste("teclas_cortan", "campos")
		assertTrue("Fuera de un campo, una letra no debe cortar la voz", !cortaLaTecla(KeyEvent.KEYCODE_A))
		ajuste("teclas_cortan", "siempre")
		ajuste("intro_corta", false)
		assertTrue("Sin «Intro corta», Intro no debe cortar la voz", !cortaLaTecla(KeyEvent.KEYCODE_ENTER))
	}

	@Test
	fun lasTeclasDeVolumenConAccionesAsignadas() {
		abrirMuestrario()
		ajuste("gestos", "VOLUMEN_ARRIBA_TRIPLE=ESTADO_SISTEMA;VOLUMEN_ABAJO_MANTENIDO=LEER_TITULO")
		var m = marca()
		repeat(3) {
			assertTrue("La tecla de volumen no se la quedó Tiresias", tecla(KeyEvent.KEYCODE_VOLUME_UP, true))
			tecla(KeyEvent.KEYCODE_VOLUME_UP, false)
			SystemClock.sleep(120)
		}
		assertNotNull("Tres veces volumen arriba no dijo la hora y la batería", dichoQue(4000, m) { it.contains("batería al") })
		m = marca()
		tecla(KeyEvent.KEYCODE_VOLUME_DOWN, true)
		SystemClock.sleep(1000)
		tecla(KeyEvent.KEYCODE_VOLUME_DOWN, false)
		assertNotNull("Mantener volumen abajo no leyó el título", dichoQue(4000, m) { it.startsWith("Muestrario") })
		// Sin nada asignado, las teclas de volumen son del sistema
		ajuste("gestos", "")
		assertTrue("Sin asignar, Tiresias no debe quedarse la tecla", !tecla(KeyEvent.KEYCODE_VOLUME_UP, true))
		tecla(KeyEvent.KEYCODE_VOLUME_UP, false)
	}

	@Test
	fun lasTeclasDeVolumenEnUnDeslizador() {
		abrirMuestrario()
		ajuste("volumen_deslizadores", true)
		assertNotNull(irA("Volumen, 40 por ciento"))
		val m = marca()
		assertTrue(tecla(KeyEvent.KEYCODE_VOLUME_UP, true))
		tecla(KeyEvent.KEYCODE_VOLUME_UP, false)
		assertNotNull("La tecla de volumen no subió el deslizador", dichoQue(3000, m) { it.contains("45 por ciento") })
	}

	@Test
	fun avisaSiLaIaTarda() {
		val m = marca()
		s.lectorPruebas!!.vision.espera.empezar()
		try {
			assertNotNull("No avisó de que sigue pensando", dichoQue(26_000, m) { it.startsWith("Sigue pensando") })
		} finally {
			s.lectorPruebas!!.vision.espera.parar()
		}
	}
}
