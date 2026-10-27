package com.xebolax.tiresias

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.tiresias.api.ServicioApi
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Las funciones de la 1.1: la API para otras apps, el filtro de voz de
 * «primero braille» y que la descripción continua y el asistente que actúa no
 * hacen nada sin una IA configurada (el emulador de pruebas no tiene ninguna).
 * Necesita Tiresias activo (lo activa la prueba en el emulador, como LectorTest).
 */
@RunWith(AndroidJUnit4::class)
class FuncionesTest {
	private val inst = InstrumentationRegistry.getInstrumentation()
	private lateinit var s: ServicioTiresias

	@Before
	fun preparar() {
		s = ServicioTiresias.instancia ?: conectar()
	}

	private fun conectar(): ServicioTiresias {
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		val servicio = inst.targetContext.packageName + "/com.xebolax.tiresias.servicio.ServicioTiresias"
		fun shell(orden: String) = ua.executeShellCommand(orden).close()
		shell("settings put secure enabled_accessibility_services null")
		SystemClock.sleep(800)
		shell("settings put secure enabled_accessibility_services $servicio")
		shell("settings put secure accessibility_enabled 1")
		val fin = SystemClock.uptimeMillis() + 20_000
		while (SystemClock.uptimeMillis() < fin) {
			ServicioTiresias.instancia?.let { return it }
			SystemClock.sleep(100)
		}
		error("Tiresias no está activo en el emulador")
	}

	private fun dichoTras(ms: Long = 3000, condicion: (String) -> Boolean): String? {
		val fin = SystemClock.uptimeMillis() + ms
		while (SystemClock.uptimeMillis() < fin) {
			s.vozPruebas?.historial()?.firstOrNull { condicion(it) }?.let { return it }
			SystemClock.sleep(50)
		}
		return null
	}

	@Test
	fun elTecladoBrailleSabeComoSeSujetaElMovil() {
		val g = android.hardware.SensorManager.GRAVITY_EARTH
		val f = com.xebolax.tiresias.braille.TecladoBraille.Companion
		// Tumbado sobre la mesa: en espejo; de pie: normales
		org.junit.Assert.assertEquals(true, f.espejoPorGravedad(9.6f, g, null))
		org.junit.Assert.assertEquals(false, f.espejoPorGravedad(1.0f, g, null))
		// A medio camino no cambia (ni se decide si aún no se sabía)
		org.junit.Assert.assertEquals(true, f.espejoPorGravedad(5.5f, g, true))
		org.junit.Assert.assertEquals(false, f.espejoPorGravedad(5.5f, g, false))
		org.junit.Assert.assertNull(f.espejoPorGravedad(5.5f, g, null))
	}

	@Test
	fun laApiHablaYContesta() {
		val c = inst.targetContext
		val conectado = CountDownLatch(1)
		var mensajero: Messenger? = null
		val conexion = object : ServiceConnection {
			override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
				mensajero = Messenger(b)
				conectado.countDown()
			}
			override fun onServiceDisconnected(n: ComponentName?) = Unit
		}
		val intent = Intent(ServicioApi.ACCION).setPackage(c.packageName)
		assertTrue(c.bindService(intent, conexion, Context.BIND_AUTO_CREATE))
		try {
			assertTrue(conectado.await(5, TimeUnit.SECONDS))
			val hilo = HandlerThread("respuestas").apply { start() }
			val contestado = CountDownLatch(1)
			var resultado = -1
			val receptor = Messenger(Handler(hilo.looper) { m ->
				resultado = m.data.getInt(ServicioApi.RESULTADO)
				contestado.countDown()
				true
			})
			val frase = "Frase de prueba de la API ${SystemClock.uptimeMillis()}"
			mensajero!!.send(Message.obtain(null, ServicioApi.HABLAR).apply {
				data = Bundle().apply { putString(ServicioApi.TEXTO, frase); putBoolean(ServicioApi.INTERRUMPIR, true) }
				replyTo = receptor
			})
			assertTrue(contestado.await(5, TimeUnit.SECONDS))
			// La propia app de Tiresias no necesita permiso
			assertEquals(ServicioApi.HECHO, resultado)
			assertNotNull("La frase de la API no sonó", dichoTras { it == frase })
			hilo.quitSafely()
		} finally {
			c.unbindService(conexion)
		}
	}

	@Test
	fun primeroBrailleCallaLoQueNoEsta() {
		val voz = s.vozPruebas!!
		try {
			voz.tiposPermitidos = setOf(Tipo.SISTEMA)
			val navegacion = "Esto no debe sonar ${SystemClock.uptimeMillis()}"
			voz.decir(Enunciado(navegacion, tipo = Tipo.NAVEGACION))
			val sistema = "Esto sí ${SystemClock.uptimeMillis()}"
			voz.decir(Enunciado(sistema, tipo = Tipo.SISTEMA))
			assertNotNull(dichoTras { it == sistema })
			assertTrue("Sonó lo que el filtro calla", voz.historial().none { it == navegacion })
		} finally {
			voz.tiposPermitidos = null
		}
	}

	@Test
	fun primeroBrailleSinLineaNoCallaLaVoz() {
		s.ejecutarPrueba(Accion.PRIMERO_BRAILLE)
		assertNotNull(dichoTras { it.contains("necesita una línea braille") || it.contains("needs a connected braille display") })
		assertEquals(null, s.vozPruebas!!.tiposPermitidos)
	}

	@Test
	fun sinIaNoSeDescribeNiSeActua() {
		for (a in listOf(Accion.DESCRIPCION_CONTINUA, Accion.ACTUAR_IA)) {
			val antes = SystemClock.uptimeMillis()
			s.ejecutarPrueba(a)
			// Sin IA configurada lo dice; en Android 10 o anterior, que hace falta Android 11
			val dicho = dichoTras { it.contains("inteligencia artificial", true) || it.contains("artificial intelligence", true) || it.contains("Android 11") }
			assertNotNull("$a no dijo nada (desde $antes)", dicho)
		}
	}
}
