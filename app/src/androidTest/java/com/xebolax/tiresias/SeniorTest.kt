package com.xebolax.tiresias

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.ModoSenior
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Listas
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.nucleo.NivelVerbosidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El modo senior (docs/modo_senior.md): se pone y se quita dejando todo como
 * estaba, Inicio seguro, «qué puedo hacer», solo las llamadas y los mensajes, y
 * la llamada de ayuda con su cuenta atrás que se cancela.
 */
@RunWith(AndroidJUnit4::class)
class SeniorTest : PruebaTiresias() {

	private fun notificar(titulo: String, categoria: String?): Int {
		if (Build.VERSION.SDK_INT >= 33) inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("pruebas", "Pruebas", NotificationManager.IMPORTANCE_HIGH))
		val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(app, "pruebas") else @Suppress("DEPRECATION") Notification.Builder(app))
			.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(titulo).setContentText("Texto").setCategory(categoria).build()
		val id = (SystemClock.uptimeMillis() % 10000).toInt()
		nm.notify(id, n)
		return id
	}

	@Test
	fun ponerloYQuitarloDejaLosAjustesComoEstaban() {
		ajuste(Claves.DAR_VUELTA, false)
		ajuste(Claves.NIVEL_VERBOSIDAD, NivelVerbosidad.ALTA.name)
		val gestosAntes = prefs.getString(Claves.GESTOS, null)
		ModoSenior.poner(app)
		SystemClock.sleep(300)
		val a = Ajustes(app)
		assertTrue(a.modoSenior)
		assertTrue(a.darVuelta)
		assertEquals(NivelVerbosidad.BAJA, a.nivelVerbosidad)
		assertEquals(MapaGestos.SENIOR.escribir(), prefs.getString(Claves.GESTOS, null))
		assertEquals(Listas.MENU_SENIOR, a.menuTiresias)
		ModoSenior.quitar(app)
		SystemClock.sleep(300)
		assertFalse(a.modoSenior)
		assertFalse(a.darVuelta)
		assertEquals(NivelVerbosidad.ALTA, a.nivelVerbosidad)
		assertEquals(gestosAntes, prefs.getString(Claves.GESTOS, null))
		assertFalse(prefs.contains(Claves.SENIOR_ANTES))
	}

	@Test
	fun inicioSeguroVaAInicioYDiceLaHora() {
		abrirMuestrario()
		val m = marca()
		s.ejecutarPrueba(Accion.INICIO_SEGURO)
		assertNotNull("No dijo Inicio y la hora", dichoQue(6000, m) { it.startsWith("Inicio. Son las") })
		assertNotNull("No se fue a la pantalla de inicio", esperar(5000) { s.lectorPruebas?.raiz()?.takeIf { it.packageName != app.packageName } })
	}

	@Test
	fun quePuedoHacerDiceComoSeUsa() {
		abrirMuestrario()
		assertNotNull(irA("Guardar"))
		val dicho = hacer(Accion.QUE_PUEDO_HACER)
		assertTrue("«$dicho»", dicho.startsWith("Estás en Guardar") && dicho.contains("Para abrirlo, toca dos veces"))
	}

	@Test
	fun soloSeLeenLasLlamadasYLosMensajes() {
		ajuste(Claves.MODO_SENIOR, true)
		ajuste(Claves.SENIOR_SOLO_MENSAJES, true)
		val nm = app.getSystemService(NotificationManager::class.java)
		val m = marca()
		val otra = notificar("Oferta del día", null)
		SystemClock.sleep(4000)
		assertTrue("Se leyó un aviso que no es un mensaje", s.vozPruebas!!.dichoDesde(m).none { it.contains("Oferta del día") })
		val mensaje = notificar("Mensaje de Ana", Notification.CATEGORY_MESSAGE)
		assertNotNull("No se leyó el mensaje", dichoQue(8000, m) { it.contains("Mensaje de Ana") })
		nm.cancel(otra)
		nm.cancel(mensaje)
	}

	@Test
	fun laLlamadaDeAyudaCuentaYSeCancela() {
		abrirMuestrario()
		// Sin contacto, lo dice
		var m = marca()
		s.ejecutarPrueba(Accion.SOS)
		assertNotNull(dichoQue(4000, m) { it.startsWith("No hay contacto de ayuda") })
		ajuste(Claves.SENIOR_CONTACTO, "600000000")
		ajuste(Claves.SENIOR_NOMBRE, "Juan")
		// Pedirla otra vez la cancela
		m = marca()
		s.ejecutarPrueba(Accion.SOS)
		assertNotNull("No avisó de la llamada", dichoQue(4000, m) { it.startsWith("Voy a llamar a Juan") })
		assertNotNull("No empezó la cuenta atrás", dichoQue(8000, m) { it == "5" })
		s.ejecutarPrueba(Accion.SOS)
		assertNotNull("No se canceló", dichoQue(4000, m) { it == "Llamada de ayuda cancelada" })
		// Cualquier otra acción también la cancela (y no se hace)
		m = marca()
		s.ejecutarPrueba(Accion.SOS)
		assertNotNull(dichoQue(4000, m) { it.startsWith("Voy a llamar a Juan") })
		s.ejecutarPrueba(Accion.SIGUIENTE)
		assertNotNull("Otra acción no la canceló", dichoQue(4000, m) { it == "Llamada de ayuda cancelada" })
		SystemClock.sleep(9000)
		assertTrue("Llamó aunque se canceló", s.vozPruebas!!.dichoDesde(m).none { it.startsWith("Llamando") || it == "1" })
	}
}
