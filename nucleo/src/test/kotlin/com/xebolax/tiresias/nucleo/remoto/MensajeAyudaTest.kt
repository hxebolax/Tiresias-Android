package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MensajeAyudaTest {
	private val todos: List<MensajeAyuda> = listOf(
		MensajeAyuda.Hola("Ana", "1.0.7", "es", ayudante = false),
		MensajeAyuda.Hola("Pepe «el de la tienda» 😀", "1.0.7", "es-ES", ayudante = true),
		MensajeAyuda.Fin("usuario"),
		MensajeAyuda.Ping,
		MensajeAyuda.Voz("Botón \"Enviar\"\nseleccionado", interrumpir = true, deletrear = false, tipo = "foco", idioma = null),
		MensajeAyuda.Voz("a", interrumpir = false, deletrear = true, tipo = "eco", idioma = "en"),
		MensajeAyuda.Callar,
		MensajeAyuda.Sonido("click"),
		MensajeAyuda.Estado("bateria", "45 %"),
		MensajeAyuda.Estado("wifi", null),
		MensajeAyuda.Elementos(7, listOf(ElementoRemoto(0, "Atrás"), ElementoRemoto(1, "Ajustes, botón"))),
		MensajeAyuda.Elementos(8, emptyList()),
		MensajeAyuda.Apps(9, listOf("WhatsApp", "Teléfono", "Cámara")),
		MensajeAyuda.Respuesta(10, ok = true, texto = null),
		MensajeAyuda.Respuesta(11, ok = false, texto = "No encuentro la app"),
		MensajeAyuda.HacerAccion("inicio"),
		MensajeAyuda.Escribir("Hola, ¿qué tal?"),
		MensajeAyuda.PedirElementos(12),
		MensajeAyuda.UsarElemento(13, 4, MensajeAyuda.UsarElemento.ACTIVAR),
		MensajeAyuda.UsarElemento(14, 0, MensajeAyuda.UsarElemento.MANTENER),
		MensajeAyuda.PedirApps(15),
		MensajeAyuda.AbrirApp(16, "WhatsApp", informacion = false),
		MensajeAyuda.AbrirApp(17, "Ajustes", informacion = true),
		MensajeAyuda.AbrirEnlace(18, "https://xebolax.synology.me/Tiresias-Android"),
	)

	@Test
	fun idaYVueltaDeTodos() {
		for (m in todos) assertEquals(m.json(), m, MensajeAyuda.leer(m.json()))
		assertSame(MensajeAyuda.Ping, MensajeAyuda.leer(MensajeAyuda.Ping.json()))
		assertSame(MensajeAyuda.Callar, MensajeAyuda.leer("{\"t\":\"callar\"}"))
	}

	@Test
	fun formatoDelJson() {
		assertEquals("{\"t\":\"ping\"}", MensajeAyuda.Ping.json())
		assertEquals("{\"t\":\"fin\",\"motivo\":\"tiempo\"}", MensajeAyuda.Fin("tiempo").json())
		assertEquals(
			"{\"t\":\"voz\",\"texto\":\"Hola\",\"interrumpir\":true,\"deletrear\":false,\"tipo\":\"foco\"}",
			MensajeAyuda.Voz("Hola", true, false, "foco", null).json(),
		)
		assertEquals(
			"{\"t\":\"elementos\",\"peticion\":1,\"lista\":[{\"indice\":0,\"texto\":\"A\"}]}",
			MensajeAyuda.Elementos(1, listOf(ElementoRemoto(0, "A"))).json(),
		)
		assertEquals("{\"t\":\"accion\",\"nombre\":\"atras\"}", MensajeAyuda.HacerAccion("atras").json())
	}

	@Test
	fun camposDeMasSeIgnoran() {
		assertEquals(MensajeAyuda.Fin("x"), MensajeAyuda.leer("{\"t\":\"fin\",\"motivo\":\"x\",\"nuevo\":[1,2]}"))
	}

	@Test
	fun entradasRaras() {
		for (raro in listOf(
			"",
			"no es json",
			"[]",
			"{}",
			"{\"t\":1}",
			"{\"t\":\"desconocido\"}",
			"{\"t\":\"hola\",\"nombre\":\"A\",\"version\":\"1\",\"idioma\":\"es\"}",
			"{\"t\":\"hola\",\"nombre\":\"A\",\"version\":\"1\",\"idioma\":\"es\",\"ayudante\":\"si\"}",
			"{\"t\":\"fin\"}",
			"{\"t\":\"voz\",\"texto\":\"a\",\"interrumpir\":true,\"deletrear\":false}",
			"{\"t\":\"voz\",\"texto\":\"a\",\"interrumpir\":true,\"deletrear\":false,\"tipo\":\"x\",\"idioma\":5}",
			"{\"t\":\"estado\",\"texto\":\"x\"}",
			"{\"t\":\"estado\",\"clave\":\"k\",\"texto\":3}",
			"{\"t\":\"elementos\",\"peticion\":1}",
			"{\"t\":\"elementos\",\"peticion\":1,\"lista\":[{\"indice\":0}]}",
			"{\"t\":\"elementos\",\"peticion\":1,\"lista\":[5]}",
			"{\"t\":\"elementos\",\"lista\":[]}",
			"{\"t\":\"apps\",\"peticion\":1,\"nombres\":[\"a\",2]}",
			"{\"t\":\"respuesta\",\"peticion\":1}",
			"{\"t\":\"respuesta\",\"peticion\":1.5,\"ok\":true}",
			"{\"t\":\"usar_elemento\",\"peticion\":1,\"indice\":99999999999,\"como\":\"activar\"}",
			"{\"t\":\"usar_elemento\",\"peticion\":1,\"indice\":1}",
			"{\"t\":\"abrir_app\",\"peticion\":1,\"nombre\":\"x\"}",
			"{\"t\":\"abrir_enlace\",\"peticion\":1}",
			"{\"t\":\"pedir_apps\"}",
		)) {
			assertNull(raro, MensajeAyuda.leer(raro))
		}
	}

	@Test
	fun numerosComoDecimalSinDecimalesValen() {
		assertEquals(MensajeAyuda.PedirApps(3), MensajeAyuda.leer("{\"t\":\"pedir_apps\",\"peticion\":3.0}"))
	}
}
