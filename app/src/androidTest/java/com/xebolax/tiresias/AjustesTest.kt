package com.xebolax.tiresias

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.ui.ActividadAjustes
import com.xebolax.tiresias.ui.ActividadGestos
import com.xebolax.tiresias.ui.ActividadOrdenar
import com.xebolax.tiresias.ui.ActividadSonidos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La app de Tiresias manejada con Tiresias: cada sección de Ajustes recorrida
 * entera (sin «switch widget» ni elementos sin nombre), todos sus interruptores
 * activados y desactivados con el doble toque (se dice cómo quedan y se
 * guarda), las pantallas nuevas (ordenar, sonidos) y el botón Volver.
 */
@RunWith(AndroidJUnit4::class)
class AjustesTest : PruebaTiresias() {
	private val secciones = listOf(
		"seccion_voz", "seccion_lectura", "seccion_navegacion", "seccion_teclado", "seccion_braille",
		"seccion_notificaciones", "seccion_ia", "seccion_actualizaciones",
	)
	// «sin nombre» como parte de lo dicho (no dentro de un texto que lo explica: «… en vez de «sin nombre»»)
	private val mal = Regex("(?i)widget|\\bswitch\\b|\\bchecked\\b|\\bunchecked\\b|(^|, )sin nombre(,|$)|sin establecer")

	private fun abrirSeccion(clave: String) = abrir(ActividadAjustes::class.java) { it.putExtra(ActividadAjustes.EXTRA_SECCION, clave) }

	@Test
	fun cadaSeccionSeLeeBien() {
		val problemas = ArrayList<String>()
		for (seccion in secciones) {
			abrirSeccion(seccion)
			val dichos = recorrer(seccion)
			assertTrue("$seccion: casi no se ha leído nada: $dichos", dichos.size >= 3)
			// La primera parada es Volver (no el menú lateral)
			assertTrue("$seccion: no empieza por Volver: ${dichos.take(2)}", dichos.take(2).any { it.startsWith("Volver") })
			dichos.filter { mal.containsMatchIn(it) }.forEach { problemas += "$seccion: «$it»" }
			// Un interruptor de fila se dice entero: «Título, resumen, interruptor, activado»
			dichos.filter { "interruptor" in it }.forEach { d ->
				if (!d.contains(Regex("interruptor, (activado|desactivado)"))) problemas += "$seccion: interruptor sin estado: «$d»"
			}
		}
		assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
	}

	@Test
	fun todosLosInterruptoresCambianConElDobleToque() {
		val problemas = ArrayList<String>()
		var probados = 0
		for (seccion in secciones) {
			abrirSeccion(seccion)
			val dichos = recorrer(seccion)
			val interruptores = dichos.filter { Regex("interruptor, (activado|desactivado)").containsMatchIn(it) }
			for (d in interruptores) {
				val titulo = d.substringBefore(",")
				if (irA("$titulo,") == null) {
					problemas += "$seccion: no se volvió a encontrar «$titulo»"
					continue
				}
				val antes = if (ultimo().contains("interruptor, activado")) "activado" else "desactivado"
				val esperado = if (antes == "activado") "desactivado" else "activado"
				val m = marca()
				s.ejecutarPrueba(Accion.ACTIVAR)
				val dicho = dichoQue(3000, m) { it == esperado || it.endsWith(esperado) }
				if (dicho == null) problemas += "$seccion: «$titulo» no dijo «$esperado» al activarlo"
				// Y se deja como estaba
				val m2 = marca()
				s.ejecutarPrueba(Accion.ACTIVAR)
				if (dichoQue(3000, m2) { it == antes || it.endsWith(antes) } == null) problemas += "$seccion: «$titulo» no volvió a «$antes»"
				probados++
				Log.i(ETIQUETA, "$seccion: «$titulo» bien")
			}
		}
		Log.i(ETIQUETA, "Interruptores probados: $probados")
		assertTrue("Muy pocos interruptores probados: $probados", probados >= 20)
		assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
	}

	@Test
	fun unaListaDeAjustesSeEligeConElLector() {
		abrirSeccion("seccion_lectura")
		assertNotNull(irA("Puntuación"))
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1500)
		// El diálogo de la lista: se elige «todas» y vuelve al ajuste con el valor nuevo
		assertNotNull("No llegó a «todas»", irA("todas", 10))
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1500)
		assertEquals("TODAS", prefs.getString("puntuacion", null))
	}

	@Test
	fun lasPantallasSecundariasTienenVolver() {
		abrir(ActividadGestos::class.java)
		val primero = hacer(Accion.PRIMERO)
		assertTrue("Gestos no empieza por Volver: «$primero»", primero.startsWith("Volver"))
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(2500)
		// Volver lleva a la pantalla de antes (aquí no hay ninguna: se cierra)
		assertTrue("Volver no cerró Gestos", s.lectorPruebas?.raiz()?.packageName != app.packageName || !ultimo().startsWith("Gestos"))
	}

	@Test
	fun laPantallaDeOrdenarElMenu() {
		abrir(ActividadOrdenar::class.java) { it.putExtra(ActividadOrdenar.EXTRA_LISTA, ActividadOrdenar.LISTA_MENU) }
		val dichos = recorrer("ordenar")
		assertTrue("No se leen las opciones como casillas: ${dichos.take(6)}", dichos.any { Regex("Leer desde arriba, 1 de \\d+, casilla, marcado").containsMatchIn(it) })
		// Desmarcar la primera la quita del menú (la pantalla otra vez, desde arriba)
		abrir(ActividadOrdenar::class.java) { it.putExtra(ActividadOrdenar.EXTRA_LISTA, ActividadOrdenar.LISTA_MENU) }
		assertNotNull(irA("Leer desde arriba, 1 de"))
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1500)
		assertTrue(com.xebolax.tiresias.base.Ajustes(app).menuTiresias.none { it == Accion.LEER_DESDE_ARRIBA })
	}

	@Test
	fun laPantallaDeSonidos() {
		abrir(ActividadSonidos::class.java)
		val dichos = recorrer("sonidos")
		assertTrue("Faltan sonidos: ${dichos.size}", dichos.count { it.contains("casilla") } >= 15)
		assertNotNull(irA("Al activar"))
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1000)
		assertTrue("No se apagó el sonido de activar", "ACTIVAR" in com.xebolax.tiresias.base.Ajustes(app).sonidosApagados)
	}
}
