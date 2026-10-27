package com.xebolax.tiresias

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.nucleo.Accion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que se pidió en la investigación de octubre de 2026 (adjuntos,
 * investigacion_peticiones_lectores_2026-10.md): «Leer más» que se abre solo, lo
 * copiado por Tiresias, copiar la lista entera, la ayuda de otra persona y la
 * pantalla virtual.
 */
@RunWith(AndroidJUnit4::class)
class PeticionesTest : PruebaTiresias() {

	private fun nodo(texto: String): AccessibilityNodeInfo? {
		val raiz = s.lectorPruebas!!.raiz() ?: return null
		return raiz.findAccessibilityNodeInfosByText(texto).firstOrNull()
	}

	@Test
	fun unLeerMasSeAbreSoloYSeLeeElResto() {
		abrirMuestrario(".ActividadAviso")
		val m = marca()
		assertNotNull(irA("Mensaje largo"))
		assertNotNull("No se leyó el resto del mensaje", dichoQue(6000, m) { it.contains("aquí sigue el final del mensaje") })
	}

	@Test
	fun loCopiadoPorTiresiasYCopiarAnadiendo() {
		abrirMuestrario()
		val primero = hacer(Accion.PRIMERO)
		s.ejecutarPrueba(Accion.COPIAR_ULTIMO)
		SystemClock.sleep(600)
		val edicion = s.lectorPruebas!!.edicion
		assertEquals(primero, edicion.copiado().first())
		val segundo = hacer(Accion.SIGUIENTE)
		s.ejecutarPrueba(Accion.COPIAR_ANADIENDO)
		SystemClock.sleep(600)
		assertEquals("$primero\n$segundo", edicion.copiado().first())
		// La lista de lo copiado se abre (y se cierra sin elegir)
		hacer(Accion.HISTORIAL_PORTAPAPELES, 1200)
		assertTrue("No se abrió lo copiado", s.ventanaAbiertaPruebas)
		s.cerrarVentanaPruebas()
	}

	@Test
	fun copiarLaListaEntera() {
		abrirMuestrario()
		assertNotNull(irA("Mensaje 1,"))
		val m = marca()
		s.ejecutarPrueba(Accion.COPIAR_LISTA)
		assertNotNull("No terminó de copiar la lista", dichoQue(30_000, m) { it.contains("elementos copiados") })
		val copiado = s.lectorPruebas!!.edicion.copiado().first().lines()
		assertEquals("La lista no se copió entera: ${copiado.take(3)}…${copiado.takeLast(2)}", 40, copiado.size)
		assertEquals("Mensaje 1", copiado.first())
		assertEquals("Mensaje 40", copiado.last())
	}

	@Test
	fun laAyudaDeOtraPersonaDiceLoQueSePulsa() {
		abrirMuestrario()
		s.ejecutarPrueba(Accion.AYUDA_OTRA_PERSONA)
		SystemClock.sleep(800)
		val gestos = s.lectorPruebas!!.gestos
		try {
			assertTrue(gestos.ayuda)
			val m = marca()
			assertTrue(nodo("Guardar")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
			assertNotNull("No dijo lo que se pulsó", dichoQue(4000, m) { it.startsWith("Guardar") })
		} finally {
			s.ejecutarPrueba(Accion.AYUDA_OTRA_PERSONA)
			SystemClock.sleep(600)
		}
		assertFalse(gestos.ayuda)
	}

	@Test
	fun laPantallaVirtualSeRecorreYPulsaEnSuSitio() {
		abrirMuestrario()
		val guardar = Nodos.limites(nodo("Guardar")!!)
		val casilla = Nodos.limites(nodo("Recordarme")!!)
		val l = s.lectorPruebas!!
		l.manejador.post { l.entrarVirtual(listOf("Recordarme" to Rect(casilla), "Guardar" to Rect(guardar))) }
		SystemClock.sleep(1500)
		// De arriba abajo, no en el orden en que llegan
		assertEquals("Guardar, 1 de 2", hacer(Accion.SIGUIENTE))
		assertEquals("Recordarme, 2 de 2", hacer(Accion.SIGUIENTE))
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull("El toque no llegó a la casilla", dichoQue(6000, m) { it.contains("marcado") })
		// (se deja como estaba)
		nodo("Recordarme")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
		assertTrue("La pantalla virtual sigue puesta", l.virtual == null)
	}

	@Test
	fun conLaCortinaSeSigueNavegandoYDesplazando() {
		abrirMuestrario()
		hacer(Accion.CORTINA, 1000)
		try {
			val primero = hacer(Accion.PRIMERO)
			assertTrue("Con la cortina no se lee: «$primero»", primero.isNotBlank())
			val siguiente = hacer(Accion.SIGUIENTE)
			assertTrue("Con la cortina no se pasa al siguiente", siguiente.isNotBlank() && siguiente != primero)
			// La lista sigue desplazándose
			assertNotNull(irA("Mensaje 1,"))
			val m = marca()
			s.ejecutarPrueba(Accion.LISTA_FINAL)
			assertNotNull("Con la cortina no se desplaza la lista", dichoQue(15_000, m) { it.startsWith("Mensaje 40") })
		} finally {
			s.ejecutarPrueba(Accion.CORTINA)
			SystemClock.sleep(800)
		}
	}
}
