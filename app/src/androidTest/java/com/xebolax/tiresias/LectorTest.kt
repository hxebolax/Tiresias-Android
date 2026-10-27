package com.xebolax.tiresias

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.servicio.ServicioTiresias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba del lector en el emulador, sobre la app de muestras (`muestrario`).
 *
 * Necesita Tiresias activado (lo hace `herramientas\emulador_pruebas.ps1`, solo
 * en el emulador) y el muestrario instalado. Corre en el proceso de Tiresias:
 * pide las acciones al servicio y lee lo que dijo en el historial de la voz,
 * que es exactamente lo que se mandó a sonar.
 *
 * Lo que comprueba una persona con el móvil (que se entiende, que el orden es
 * el lógico, que se oye bien) queda fuera: esto solo prueba que dice lo que debe.
 */
@RunWith(AndroidJUnit4::class)
class LectorTest {
	private lateinit var s: ServicioTiresias

	@Before
	fun preparar() {
		s = ServicioTiresias.instancia ?: conectar()
		// Las frases esperadas están en español (el emulador está en inglés y cada instalación empieza de cero)
		val c = InstrumentationRegistry.getInstrumentation().targetContext
		if (com.xebolax.tiresias.base.Idioma.elegido(c) != "es") {
			com.xebolax.tiresias.base.Idioma.cambiar(c, "es")
			s.recargarAjustes()
			SystemClock.sleep(500)
		}
		if (android.os.Build.VERSION.SDK_INT >= 31) s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
		abrirMuestrario()
	}

	/**
	 * La instrumentación reinicia el proceso de Tiresias y Android no vuelve a
	 * conectar el servicio solo: se pide la automatización sin suprimir los
	 * servicios de accesibilidad y se vuelve a activar Tiresias (en el emulador).
	 */
	private fun conectar(): ServicioTiresias {
		val inst = InstrumentationRegistry.getInstrumentation()
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		val servicio = inst.targetContext.packageName + "/com.xebolax.tiresias.servicio.ServicioTiresias"
		fun shell(orden: String) = ua.executeShellCommand(orden).close()
		shell("settings put secure enabled_accessibility_services null")
		SystemClock.sleep(800)
		shell("settings put secure enabled_accessibility_services $servicio")
		shell("settings put secure accessibility_enabled 1")
		return esperar(20_000) { ServicioTiresias.instancia } ?: error("Tiresias no está activo en el emulador")
	}

	private fun <T> esperar(ms: Long, condicion: () -> T?): T? {
		val fin = SystemClock.uptimeMillis() + ms
		while (SystemClock.uptimeMillis() < fin) {
			condicion()?.let { return it }
			SystemClock.sleep(50)
		}
		return null
	}

	private fun abrirMuestrario(actividad: String = ".ActividadMuestrario") {
		val c = InstrumentationRegistry.getInstrumentation().targetContext
		val i = Intent().setClassName("com.xebolax.tiresias.muestrario", "com.xebolax.tiresias.muestrario$actividad")
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
		val voz = s.vozPruebas!!
		val desde = voz.contadorPruebas
		c.startActivity(i)
		// Hasta que la ventana de delante sea la del muestrario (no la de arranque de Android, «Application icon»)
		esperar(10_000) { s.lectorPruebas?.raiz()?.takeIf { r -> r.packageName == "com.xebolax.tiresias.muestrario" && r.childCount > 0 && r.className?.contains("SplashScreen") != true } }
		// Que ya haya dicho el título de la ventana (y lo que venga detrás) y se calle: si no, llega tarde y
		// se confunde con lo que se prueba (la página web tarda en cargar)
		val titulo = if (actividad.contains("Web")) "Página web" else "Muestrario"
		esperar(8000) { voz.dichoDesde(desde).firstOrNull { it.startsWith(titulo) } }
		var ultimo = voz.contadorPruebas
		var quieto = SystemClock.uptimeMillis()
		val fin = SystemClock.uptimeMillis() + 8000
		while (SystemClock.uptimeMillis() < fin) {
			SystemClock.sleep(100)
			if (voz.contadorPruebas != ultimo) {
				ultimo = voz.contadorPruebas
				quieto = SystemClock.uptimeMillis()
			} else if (SystemClock.uptimeMillis() - quieto > 1500) break
		}
	}

	/** Hace [a] y devuelve lo nuevo que dijo Tiresias (sin las pistas de uso). */
	private fun hacer(a: Accion, espera: Long = 700): String {
		val voz = s.vozPruebas!!
		val marca = voz.contadorPruebas
		s.ejecutarPrueba(a)
		// Lo nuevo que diga (aunque sea lo mismo que antes); si en 3 s no dice nada, lo último
		val dicho = esperar(3000) { voz.dichoDesde(marca).firstOrNull() }
		SystemClock.sleep(minOf(espera, 150L))
		// La última frase nueva (nunca una de antes de la acción)
		return (voz.dichoDesde(marca).firstOrNull() ?: dicho ?: voz.historial().firstOrNull().orEmpty()).also { Log.i(ETIQUETA, "$a -> «$it»") }
	}

	private fun controlPredeterminado() {
		s.lectorPruebas!!.control = ControlLectura.PREDETERMINADO
	}

	@Test
	fun leeCadaControlConSuRolYSuEstado() {
		controlPredeterminado()
		assertEquals("Controles, encabezado", hacer(Accion.PRIMERO))
		assertEquals("Guardar, botón", hacer(Accion.SIGUIENTE))
		// Sin nombre: el identificador de la vista en vez de «sin nombre»
		assertEquals("boton enviar, sin nombre, botón", hacer(Accion.SIGUIENTE))
		assertEquals("Recordarme, casilla, no marcado", hacer(Accion.SIGUIENTE))
		assertEquals("Wifi, interruptor, activado", hacer(Accion.SIGUIENTE))
		assertEquals("Rojo, opción, no marcado", hacer(Accion.SIGUIENTE))
		hacer(Accion.SIGUIENTE)
		assertEquals("Volumen", hacer(Accion.SIGUIENTE))
		assertEquals("Volumen, 40 por ciento, deslizador", hacer(Accion.SIGUIENTE))
		assertEquals("Descarga, 75 por ciento, barra de progreso", hacer(Accion.SIGUIENTE))
		assertTrue(hacer(Accion.SIGUIENTE).startsWith("País"))
		assertEquals("Correo, campo de texto", hacer(Accion.SIGUIENTE))
		assertEquals("Contraseña, campo de contraseña", hacer(Accion.SIGUIENTE))
	}

	@Test
	fun alMarcarUnaCasillaDiceComoQueda() {
		controlPredeterminado()
		hacer(Accion.PRIMERO)
		hacer(Accion.SIGUIENTE)
		hacer(Accion.SIGUIENTE)
		assertEquals("Recordarme, casilla, no marcado", hacer(Accion.SIGUIENTE))
		assertEquals("marcado", hacer(Accion.ACTIVAR, espera = 1500))
		assertEquals("no marcado", hacer(Accion.ACTIVAR, espera = 1500))
	}

	@Test
	fun laRegionVivaSeAnunciaSinMoverElFoco() {
		controlPredeterminado()
		hacer(Accion.PRIMERO)
		var dicho = ""
		for (i in 0 until 20) {
			dicho = hacer(Accion.SIGUIENTE)
			if (dicho.startsWith("Cambiar estado")) break
		}
		assertEquals("Cambiar estado, botón", dicho)
		assertEquals("Estado: guardado", hacer(Accion.ACTIVAR, espera = 2000))
	}

	@Test
	fun alFinalDeLoVisibleLaListaSeDesplazaSola() {
		controlPredeterminado()
		hacer(Accion.PRIMERO)
		var dicho = ""
		// Más allá de lo que cabe en la pantalla: Tiresias desplaza y sigue sin saltos
		for (i in 0 until 60) {
			dicho = hacer(Accion.SIGUIENTE)
			if (dicho.startsWith("Mensaje 15,")) break
		}
		assertEquals("Mensaje 15, 15 de 40", dicho)
		assertEquals("Mensaje 16, 16 de 40", hacer(Accion.SIGUIENTE))
	}

	@Test
	fun navegaPorEncabezadosEnLaWeb() {
		abrirMuestrario(".ActividadWeb")
		SystemClock.sleep(2000)
		val l = s.lectorPruebas!!
		controlPredeterminado()
		hacer(Accion.PRIMERO)
		l.control = ControlLectura.ENCABEZADOS
		val encabezados = (1..3).map { hacer(Accion.SELECTOR_SIGUIENTE, espera = 1200) }
		Log.i(ETIQUETA, "Encabezados web: $encabezados")
		// Con el nivel en español y sin la descripción de rol de Chrome («heading 2») repetida
		assertTrue("Debe llegar a Noticias: $encabezados", "Noticias, encabezado de nivel 2" in encabezados)
		assertTrue("Debe llegar a Precios: $encabezados", "Precios, encabezado de nivel 2" in encabezados)
		controlPredeterminado()
	}

	@Test
	fun latenciaDelGestoALaVoz() {
		controlPredeterminado()
		hacer(Accion.PRIMERO)
		val voz = s.vozPruebas!!
		val medidas = ArrayList<Long>()
		repeat(10) {
			val antes = voz.ultimoInicioSonido
			val t0 = SystemClock.uptimeMillis()
			s.ejecutarPrueba(Accion.SIGUIENTE)
			val inicio = esperar(2000) { voz.ultimoInicioSonido.takeIf { it != antes } }
			assertNotNull("La voz no empezó a sonar", inicio)
			medidas += inicio!! - t0
			SystemClock.sleep(500)
		}
		medidas.sort()
		val mediana = medidas[medidas.size / 2]
		Log.i(ETIQUETA, "Latencia del gesto al primer sonido: mediana $mediana ms, mínima ${medidas.first()} ms, máxima ${medidas.last()} ms")
		// Objetivo del plan: menos de 40 ms con la voz integrada. El emulador es más lento que un móvil: margen de 80 ms
		assertTrue("Latencia mediana $mediana ms", mediana < 80)
	}

	companion object {
		private const val ETIQUETA = "LectorTest"
	}
}
