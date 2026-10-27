package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapaGestosSeniorTest {
	@Test
	fun pocosGestosYLasTeclasDeVolumen() {
		val m = MapaGestos.SENIOR
		assertEquals(Accion.SIGUIENTE, m[Gesto.DERECHA])
		assertEquals(Accion.ANTERIOR, m[Gesto.IZQUIERDA])
		assertEquals(Accion.ACTIVAR, m[Gesto.DOBLE_TOQUE])
		assertEquals(Accion.INICIO_SEGURO, m[Gesto.VOLUMEN_AMBAS])
		assertEquals(Accion.SOS, m[Gesto.VOLUMEN_ARRIBA_TRIPLE])
		// Los gestos en ángulo y los de tres y cuatro dedos no hacen nada (sin sustos)
		for (g in listOf(Gesto.ARRIBA_IZQUIERDA, Gesto.ABAJO_DERECHA, Gesto.ARRIBA_ABAJO, Gesto.TOQUE_3, Gesto.ARRIBA_4)) assertEquals(g.name, Accion.NINGUNA, m[g])
		// La salida de emergencia sigue
		assertEquals(Accion.MODO_SEGURO, m[Gesto.TRIPLE_TOQUE_MANTENIDO_4])
		// Se guarda y se lee igual (gesto a gesto: los que no hacen nada pueden no guardarse)
		val leido = MapaGestos.leer(m.escribir())
		for (g in Gesto.entries) assertEquals(g.name, m[g], leido[g])
		assertTrue(Accion.SOS in Listas.MENU_SENIOR && Accion.INICIO_SEGURO in Listas.MENU_SENIOR)
	}
}
