package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class MapaGestosSencilloTest {
	@Test
	fun loDeCadaDiaConToquesDeVariosDedos() {
		val m = MapaGestos.SENCILLO
		assertEquals(Accion.ATRAS, m[Gesto.DOBLE_TOQUE_2])
		assertEquals(Accion.INICIO, m[Gesto.TRIPLE_TOQUE_2])
		assertEquals(Accion.MENU_TIRESIAS, m[Gesto.TOQUE_3])
		assertEquals(Accion.NOTIFICACIONES, m[Gesto.IZQUIERDA_3])
		// Lo básico de un dedo y los de ángulo siguen como siempre, y la salida de emergencia no se toca
		assertEquals(Accion.SIGUIENTE, m[Gesto.DERECHA])
		assertEquals(MapaGestos.POR_DEFECTO[Gesto.ABAJO_IZQUIERDA], m[Gesto.ABAJO_IZQUIERDA])
		assertEquals(Accion.MODO_SEGURO, m[Gesto.TRIPLE_TOQUE_MANTENIDO_4])
		// Se guarda y se lee igual
		assertEquals(m.todas, MapaGestos.leer(m.escribir()).todas)
	}
}
