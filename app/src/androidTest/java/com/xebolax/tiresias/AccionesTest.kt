package com.xebolax.tiresias

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.servicio.ServicioTiresias
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Todas las acciones de Tiresias, una a una, como si llegaran de un gesto: que
 * ninguna cierra ni cuelga el lector (después de cada una tiene que seguir
 * respondiendo), y todos los gestos del mapa por defecto.
 */
@RunWith(AndroidJUnit4::class)
class AccionesTest : PruebaTiresias() {
	/** Las que no se prueban aquí: bloquean el móvil, abren el menú de apagado o necesitan otra prueba. */
	private val fuera = setOf(Accion.NINGUNA, Accion.BLOQUEAR, Accion.ENCENDIDO, Accion.MODO_SEGURO, Accion.APAGAR_SCRIPTS)

	/** Tras cada acción: se cierra lo que haya abierto y se vuelve al muestrario; Tiresias tiene que contestar. */
	private fun despues(a: Accion, problemas: MutableList<String>) {
		s.cerrarVentanaPruebas()
		// Lo que cambian algunas acciones (idioma, puntuación, verbosidad, velocidad...) se deshace
		restaurarAjustes()
		// La cortinilla de notificaciones o de ajustes rápidos se cierra
		if (android.os.Build.VERSION.SDK_INT >= 31) s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
		SystemClock.sleep(400)
		if (s.lectorPruebas?.raiz()?.packageName != "com.xebolax.tiresias.muestrario") abrirMuestrario()
		val viva = ServicioTiresias.instancia
		if (viva == null || viva !== s) {
			problemas += "$a: el servicio se reinició o se cerró"
			s = viva ?: return
		}
		val m = marca()
		s.ejecutarPrueba(Accion.LEER_TITULO)
		if (dichoQue(4000, m) { it.isNotBlank() } == null) problemas += "$a: después, Tiresias no contesta"
	}

	@Test
	fun ningunaAccionRompeElLector() {
		abrirMuestrario()
		val problemas = ArrayList<String>()
		for (a in Accion.entries) {
			if (a in fuera) continue
			Log.i(ETIQUETA, "Acción $a")
			hacer(Accion.PRIMERO, 200)
			s.ejecutarPrueba(a)
			SystemClock.sleep(1500)
			// Las que cambian algo de forma duradera se deshacen
			when (a) {
				Accion.SUSPENDER, Accion.CORTINA, Accion.ALTERNAR_VOZ, Accion.TOQUE_DIRECTO, Accion.AYUDA_ENTRADA, Accion.PRIMERO_BRAILLE, Accion.DESCRIPCION_CONTINUA,
				Accion.CLIC_DIRECTO, Accion.MODO_SELECCION -> {
					s.ejecutarPrueba(a)
					SystemClock.sleep(800)
				}
				Accion.LEER_DESDE_ARRIBA, Accion.LEER_DESDE_AQUI -> s.ejecutarPrueba(Accion.PAUSAR_VOZ)
				else -> Unit
			}
			despues(a, problemas)
		}
		problemas.forEach { Log.w(ETIQUETA, "PROBLEMA $it") }
		assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
	}

	@Test
	fun losGestosHacenSuAccion() {
		abrirMuestrario()
		// Unos cuantos gestos del mapa por defecto, con su efecto
		hacer(Accion.PRIMERO)
		var m = marca()
		s.gestoPrueba(Gesto.DERECHA)
		assertNotNull("Derecha no pasó al siguiente", dichoQue(3000, m) { it.startsWith("Guardar") })
		m = marca()
		s.gestoPrueba(Gesto.IZQUIERDA)
		assertNotNull("Izquierda no volvió", dichoQue(3000, m) { it.startsWith("Controles") })
		m = marca()
		s.gestoPrueba(Gesto.TOQUE_4)
		assertNotNull("Cuatro dedos no dijo la hora", dichoQue(3000, m) { it.contains("batería al") })
		m = marca()
		s.gestoPrueba(Gesto.ARRIBA_DERECHA)
		assertNotNull("Arriba y derecha no abrió el menú", dichoQue(8000, m) { it.startsWith("Menú de Tiresias") })
		s.cerrarVentanaPruebas()
		// Que cada gesto del mapa tiene una acción que existe (ninguno se queda en nada por error)
		val sinAccion = MapaGestos.POR_DEFECTO.todas.filterValues { it == Accion.NINGUNA }.keys
		assertTrue("Gestos sin acción en el mapa de fábrica: $sinAccion", sinAccion.isEmpty())
	}
}
