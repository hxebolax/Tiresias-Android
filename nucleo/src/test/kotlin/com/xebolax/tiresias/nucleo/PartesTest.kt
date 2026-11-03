package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La edición por partes: partir, cambiar, borrar, insertar y reemplazar sin estropear lo de alrededor. */
class PartesTest {
	private val texto = "Hola, Ana. ¿Vienes mañana?\nYo sí.\n\nOtro párrafo aquí."

	@Test
	fun partirEnCadaUnidad() {
		assertEquals(listOf("Hola", "Ana", "Vienes", "mañana", "Yo", "sí", "Otro", "párrafo", "aquí"), Partes.partir(texto, UnidadPartes.PALABRAS).map { it.texto })
		assertEquals(listOf("Hola, Ana.", "¿Vienes mañana?", "Yo sí.", "Otro párrafo aquí."), Partes.partir(texto, UnidadPartes.FRASES).map { it.texto })
		assertEquals(listOf("Hola, Ana. ¿Vienes mañana?", "Yo sí.", "", "Otro párrafo aquí."), Partes.partir(texto, UnidadPartes.LINEAS).map { it.texto })
		assertEquals(listOf("Hola, Ana. ¿Vienes mañana?\nYo sí.", "Otro párrafo aquí."), Partes.partir(texto, UnidadPartes.PARRAFOS).map { it.texto })
		assertEquals(listOf(texto), Partes.partir(texto, UnidadPartes.TODO).map { it.texto })
		// Un emoji es un solo carácter
		assertEquals(listOf("a", "😀", "b"), Partes.partir("a😀b", UnidadPartes.CARACTERES).map { it.texto })
		assertTrue(Partes.partir("", UnidadPartes.PALABRAS).isEmpty())
	}

	@Test
	fun cambiarUnaParteConservaLoDeAlrededor() {
		val ana = Partes.partir(texto, UnidadPartes.PALABRAS)[1]
		assertEquals("Hola, Juan. ¿Vienes mañana?\nYo sí.\n\nOtro párrafo aquí.", Partes.cambiar(texto, ana, "Juan"))
	}

	@Test
	fun borrarNoDejaDosEspacios() {
		val t = "uno dos tres"
		val palabras = Partes.partir(t, UnidadPartes.PALABRAS)
		assertEquals("dos tres", Partes.borrar(t, palabras[0], UnidadPartes.PALABRAS))
		assertEquals("uno tres", Partes.borrar(t, palabras[1], UnidadPartes.PALABRAS))
		assertEquals("uno dos", Partes.borrar(t, palabras[2], UnidadPartes.PALABRAS))
		val lineas = Partes.partir("a\nb\nc", UnidadPartes.LINEAS)
		assertEquals("a\nc", Partes.borrar("a\nb\nc", lineas[1], UnidadPartes.LINEAS))
		assertEquals("a\nb", Partes.borrar("a\nb\nc", lineas[2], UnidadPartes.LINEAS))
		val parrafos = Partes.partir(texto, UnidadPartes.PARRAFOS)
		assertEquals("Otro párrafo aquí.", Partes.borrar(texto, parrafos[0], UnidadPartes.PARRAFOS))
		assertEquals("uno tres", Partes.borrarVarias("uno dos tres cuatro", Partes.partir("uno dos tres cuatro", UnidadPartes.PALABRAS).filterIndexed { i, _ -> i == 1 || i == 3 }, UnidadPartes.PALABRAS))
	}

	@Test
	fun insertarConSuSeparador() {
		val t = "uno tres"
		val p = Partes.partir(t, UnidadPartes.PALABRAS)
		assertEquals("uno dos tres", Partes.insertar(t, p[0], "dos", despues = true, unidad = UnidadPartes.PALABRAS))
		assertEquals("cero uno tres", Partes.insertar(t, p[0], "cero", despues = false, unidad = UnidadPartes.PALABRAS))
		val l = Partes.partir("a\nc", UnidadPartes.LINEAS)
		assertEquals("a\nb\nc", Partes.insertar("a\nc", l[0], "b", despues = true, unidad = UnidadPartes.LINEAS))
	}

	@Test
	fun reemplazarDiceCuantos() {
		assertEquals("uno 2 tres 2" to 2, Partes.reemplazar("uno dos tres dos", "dos", "2", distinguirMayusculas = true))
		assertEquals("uno 2 tres 2" to 2, Partes.reemplazar("uno Dos tres DOS", "dos", "2", distinguirMayusculas = false))
		assertEquals("uno Dos" to 0, Partes.reemplazar("uno Dos", "dos", "2", distinguirMayusculas = true))
		assertEquals("abc" to 0, Partes.reemplazar("abc", "", "x", distinguirMayusculas = false))
	}

	@Test
	fun losGestosDelModoEdicionNoTocanLaSalidaDeEmergencia() {
		assertFalse(OrdenEdicion.GESTOS.keys.any { it in MapaGestos.EMERGENCIA })
		assertEquals(OrdenEdicion.SALIR, OrdenEdicion.GESTOS[Gesto.IZQUIERDA_ABAJO])
		// Se entra con el mismo gesto con el que se sale (el de Jieshuo), y buscar sigue teniendo el suyo
		assertEquals(Accion.MODO_EDICION, MapaGestos.POR_DEFECTO[Gesto.IZQUIERDA_ABAJO])
		assertEquals(Accion.BUSCAR, MapaGestos.POR_DEFECTO[Gesto.TOQUE_MANTENIDO_3])
		// Ni el doble toque ni Atrás: siguen haciendo lo de siempre
		assertFalse(Gesto.DOBLE_TOQUE in OrdenEdicion.GESTOS)
		assertFalse(Gesto.ABAJO_IZQUIERDA in OrdenEdicion.GESTOS)
	}
}
