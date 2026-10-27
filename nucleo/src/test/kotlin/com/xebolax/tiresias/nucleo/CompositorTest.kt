package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositorTest {
	private val c = Compositor(TextosPrueba)

	@Test
	fun losEncabezadosDeTablaAntesDespuesONunca() {
		val celda = Elemento(nombre = "12", posicion = Posicion(fila = 2, columna = 3, totalFilas = 5, totalColumnas = 4, encabezadoFila = "Lunes", encabezadoColumna = "Ventas"))
		assertEquals("Lunes, Ventas, fila 2, columna 3", c.posicion(celda))
		assertEquals("fila 2, columna 3, Lunes, Ventas", c.posicion(celda, Verbosidad(encabezadosDespues = true)))
		assertEquals("fila 2, columna 3", c.posicion(celda, Verbosidad(decirEncabezadosTabla = false)))
		assertEquals("Lunes, Ventas", c.posicion(celda, Verbosidad(decirFilaColumna = false)))
	}

	@Test
	fun lasPistasNombranLosGestosDelUsuario() {
		val conAcciones = Elemento(nombre = "Mensaje", tieneAcciones = true)
		assertEquals("Toca con tres dedos para ver sus acciones.", c.pista(conAcciones))
		val otro = Compositor(TextosPrueba, gestos = Compositor.GestosDePistas(menuAcciones = "Deslizar arriba y luego a la izquierda"))
		assertEquals("Deslizar arriba y luego a la izquierda para ver sus acciones.", otro.pista(conAcciones))
		// Con «activar al levantar el dedo»: nada de doble toque
		val levantar = Compositor(TextosPrueba, gestos = Compositor.GestosDePistas(alLevantar = true))
		assertEquals("Levanta el dedo para activar.", levantar.pista(Elemento(nombre = "Guardar", rol = Rol.BOTON, pulsable = true)))
	}

	@Test
	fun conAccionesArribaYAbajoLaPistaLoDice() {
		val arribaAbajo = Compositor(TextosPrueba, gestos = Compositor.GestosDePistas(accionesArribaAbajo = true))
		val elegir = "Desliza arriba o abajo para elegir una acción y toca dos veces para hacerla."
		// Solo con acciones: en vez del menú de acciones
		assertEquals(elegir, arribaAbajo.pista(Elemento(nombre = "Mensaje", tieneAcciones = true)))
		// Una casilla con acciones: cómo se cambia y cómo se eligen
		val casilla = Elemento(nombre = "Al activar", rol = Rol.CASILLA, marcado = Marcado.MARCADO, pulsable = true, tieneAcciones = true)
		assertEquals("Toca dos veces para cambiar. $elegir", arribaAbajo.pista(casilla))
		// Un deslizador: arriba y abajo cambian el valor
		val barra = Elemento(nombre = "Volumen", rol = Rol.DESLIZADOR, tieneAcciones = true)
		assertEquals(c.pista(barra), arribaAbajo.pista(barra))
	}

	@Test
	fun interruptorDiceActivadoYNoMarcado() {
		val e = Elemento(nombre = "Wifi", rol = Rol.INTERRUPTOR, marcado = Marcado.MARCADO, pulsable = true)
		assertEquals("Wifi, interruptor, activado", c.describir(e))
	}

	@Test
	fun casillaParcial() {
		val e = Elemento(nombre = "Todo", rol = Rol.CASILLA, marcado = Marcado.PARCIAL)
		assertEquals("Todo, casilla, parcialmente marcado", c.describir(e))
	}

	@Test
	fun ordenEstadoPrimero() {
		val e = Elemento(nombre = "Leído", rol = Rol.CASILLA, marcado = Marcado.NO_MARCADO)
		assertEquals("no marcado, Leído, casilla", c.describir(e, Verbosidad(orden = Verbosidad.ORDEN_ESTADO_PRIMERO)))
	}

	@Test
	fun estadoDeLaAppMandaSobreElCalculado() {
		val e = Elemento(nombre = "Modo", rol = Rol.INTERRUPTOR, marcado = Marcado.MARCADO, estado = "automático")
		assertEquals("Modo, interruptor, automático", c.describir(e))
	}

	@Test
	fun sinNombreDiceElIdentificador() {
		val e = Elemento(rol = Rol.BOTON_IMAGEN, pulsable = true, identificador = "com.app:id/boton_enviar")
		assertEquals("boton enviar, sin nombre, botón", c.describir(e))
		val sinId = Elemento(rol = Rol.BOTON_IMAGEN, pulsable = true, identificador = "com.app:id/view12")
		assertEquals("sin nombre, botón", c.describir(sinId))
	}

	@Test
	fun etiquetaPersonalMandaSobreElNombre() {
		val e = Elemento(nombre = "ic_menu", rol = Rol.BOTON, etiquetaPersonal = "Menú principal")
		assertEquals("Menú principal, botón", c.describir(e))
	}

	@Test
	fun deslizadorConPorCiento() {
		val e = Elemento(nombre = "Volumen", rol = Rol.DESLIZADOR, rango = Rango(0f, 100f, 40f))
		assertEquals("Volumen, 40 por ciento, deslizador", c.describir(e))
		val propio = Elemento(nombre = "Velocidad", rol = Rol.DESLIZADOR, rango = Rango(0f, 10f, 2f), estado = "1,5x")
		assertEquals("Velocidad, 1,5x, deslizador", c.describir(propio))
	}

	@Test
	fun posicionEnListaYTabla() {
		val fila = Elemento(nombre = "Correo", posicion = Posicion(fila = 3, totalFilas = 10))
		assertEquals("Correo, 3 de 10", c.describir(fila))
		val celda = Elemento(nombre = "12", posicion = Posicion(fila = 2, columna = 3, totalFilas = 5, totalColumnas = 4, encabezadoColumna = "Precio"))
		assertEquals("12, Precio, fila 2, columna 3", c.describir(celda))
	}

	@Test
	fun encabezadoConNivel() {
		val e = Elemento(nombre = "Noticias", encabezado = true, nivelEncabezado = 2)
		assertEquals("Noticias, encabezado de nivel 2", c.describir(e))
		// Chrome manda «heading 2» como descripción de rol: no se repite
		assertEquals("Noticias, encabezado de nivel 2", c.describir(e.copy(descripcionRol = "heading 2")))
	}

	@Test
	fun errorYObligatorio() {
		val e = Elemento(nombre = "Correo", rol = Rol.CAMPO_TEXTO, editable = true, obligatorio = true, error = "Escribe un correo válido")
		assertEquals("Correo, campo de texto, obligatorio, error: Escribe un correo válido", c.describir(e))
	}

	@Test
	fun noRepiteLoMismo() {
		val e = Elemento(nombre = "Seleccionado", seleccionado = true)
		assertEquals("Seleccionado", c.describir(e))
	}

	@Test
	fun pistas() {
		assertEquals("Toca dos veces para activar.", c.pista(Elemento(nombre = "Enviar", rol = Rol.BOTON, pulsable = true)))
		assertNull(c.pista(Elemento(nombre = "Enviar", rol = Rol.BOTON, pulsable = true), Verbosidad(decirPistas = false)))
		assertNull(c.pista(Elemento(nombre = "Enviar", rol = Rol.BOTON, pulsable = true, activo = false)))
	}

	@Test
	fun identificadoresLegibles() {
		assertEquals("btn send message", Compositor.legible("com.x:id/btnSendMessage"))
		assertNull(Compositor.legible("com.x:id/view3"))
		assertNull(Compositor.legible("12345"))
	}

	@Test
	fun coleccion() {
		assertEquals("lista con 12 elementos", c.entrarEnColeccion(Rol.LISTA, 12, 1))
		assertEquals("cuadrícula con 3 filas, 4 columnas", c.entrarEnColeccion(Rol.CUADRICULA, 3, 4))
	}
}
