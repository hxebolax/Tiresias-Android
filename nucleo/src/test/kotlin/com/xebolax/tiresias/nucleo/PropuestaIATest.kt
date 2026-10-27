package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropuestaIATest {
	@Test
	fun tocarUnElementoDeLaLista() {
		val p = PropuestaIA.leer("Claro. ```json\n{\"accion\": \"tocar\", \"elemento\": 3, \"explicacion\": \"Pulsar el botón «Enviar»\"}\n```")!!
		assertEquals(PropuestaIA.Tipo.TOCAR, p.accion)
		assertEquals(3, p.elemento)
		assertEquals("Pulsar el botón «Enviar»", p.explicacion)
		assertTrue(p.valida)
	}

	@Test
	fun tocarUnPuntoDeUnJuego() {
		val p = PropuestaIA.leer("{\"accion\":\"tocar\",\"elemento\":null,\"x\":512,\"y\":880.4,\"explicacion\":\"El botón \\\"Jugar\\\" está abajo\"}")!!
		assertNull(p.elemento)
		assertEquals(512, p.x)
		assertEquals(880, p.y)
		assertEquals("El botón \"Jugar\" está abajo", p.explicacion)
		assertTrue(p.valida)
	}

	@Test
	fun escribirYDesplazar() {
		val e = PropuestaIA.leer("{\"accion\":\"escribir\",\"elemento\":1,\"texto\":\"Hola\\nqué tal\",\"explicacion\":\"Escribir el mensaje\"}")!!
		assertEquals("Hola\nqué tal", e.texto)
		val d = PropuestaIA.leer("{\"accion\":\"desplazar\",\"direccion\":\"abajo\",\"explicacion\":\"Ver más\"}")!!
		assertEquals(PropuestaIA.Direccion.ABAJO, d.direccion)
		// Escribir sin texto no se puede hacer
		assertFalse(PropuestaIA.leer("{\"accion\":\"escribir\",\"elemento\":1}")!!.valida)
	}

	@Test
	fun respuestasQueNoSirven() {
		assertNull(PropuestaIA.leer("No sé qué hacer"))
		assertNull(PropuestaIA.leer("{\"accion\":\"borrar_todo\"}"))
		assertEquals(PropuestaIA.Tipo.NINGUNA, PropuestaIA.leer("{\"accion\":\"ninguna\",\"explicacion\":\"No hay ningún botón de enviar\"}")!!.accion)
	}
}
