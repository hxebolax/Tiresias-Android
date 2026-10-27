package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoTest {
	private val tabla = TablaSimbolos.leer(
		"""
		# prueba
		.	punto	mayoria
		,	coma	mayoria
		?	cierra interrogación	mayoria	mantener
		@	arroba	algunas
		=	igual	todas
		...	puntos suspensivos	algunas
		#	almohadilla	algunas
		😀	cara sonriente	algunas
		""".trimIndent()
	)
	private val n = Normalizador(tabla, TextosPrueba, letras = mapOf("y" to "i griega"))

	@Test
	fun losEmojisPorSuNombreAlMotorOQuitados() {
		val texto = "Hola \uD83D\uDE00 @"
		assertEquals("Hola cara sonriente arroba", n.normalizar(texto, AjustesTexto(emojis = ModoEmojis.NOMBRE)))
		// Para el motor: el emoji se queda tal cual, pero lo demás se normaliza
		assertEquals("Hola \uD83D\uDE00 arroba", n.normalizar(texto, AjustesTexto(emojis = ModoEmojis.MOTOR)))
		assertEquals("Hola arroba", n.normalizar(texto, AjustesTexto(emojis = ModoEmojis.QUITAR)))
	}

	@Test
	fun puntuacionPorNiveles() {
		assertEquals("Hola. ¿Qué tal?", n.normalizar("Hola. ¿Qué tal?", AjustesTexto(puntuacion = NivelPuntuacion.NINGUNA)))
		assertEquals("correo arroba sitio.es", n.normalizar("correo@sitio.es", AjustesTexto(puntuacion = NivelPuntuacion.ALGUNAS)))
		assertEquals("correo arroba sitio punto es", n.normalizar("correo@sitio.es", AjustesTexto(puntuacion = NivelPuntuacion.MAYORIA)))
	}

	@Test
	fun losDecimalesNoSonPuntuacion() {
		assertEquals("Cuesta 3,50 euros punto", n.normalizar("Cuesta 3,50 euros.", AjustesTexto(puntuacion = NivelPuntuacion.MAYORIA)))
	}

	@Test
	fun simbolosLargosGananALosCortos() {
		assertEquals("Espera puntos suspensivos", n.normalizar("Espera...", AjustesTexto(puntuacion = NivelPuntuacion.ALGUNAS)))
	}

	@Test
	fun mantenerElSignoParaLaEntonacion() {
		assertEquals("Vienes cierra interrogación?", n.normalizar("Vienes?", AjustesTexto(puntuacion = NivelPuntuacion.MAYORIA)))
	}

	@Test
	fun repetidos() {
		assertEquals("igual repetido 6 veces", n.normalizar("======", AjustesTexto(puntuacion = NivelPuntuacion.NINGUNA)))
	}

	@Test
	fun digitos() {
		assertEquals("Código 1 2 3 4", n.normalizar("Código 1234", AjustesTexto(numeros = ModoNumeros.DIGITOS)))
	}

	@Test
	fun caracteres() {
		val a = AjustesTexto(mayusculas = ModoMayusculas.PALABRA)
		assertEquals("mayúscula a", n.caracter("A", a).texto)
		assertTrue(n.caracter("A", a).mayuscula)
		assertEquals("i griega", n.caracter("y", a).texto)
		assertEquals("arroba", n.caracter("@", a).texto)
		assertEquals("espacio", n.caracter(" ", a).texto)
	}

	@Test
	fun diccionarioDePalabraYExpresion() {
		val d = Diccionario.leer("WhatsApp\tguasap\tpalabra\tno\t\n(\\d+)km\t$1 kilómetros\texpresion\tno\t")
		assertEquals("Abre guasap, son 5 kilómetros", d.aplicar("Abre WhatsApp, son 5km"))
		// Palabra completa: no cambia dentro de otra palabra
		assertEquals("WhatsAppWeb", d.aplicar("WhatsAppWeb"))
	}

	@Test
	fun diccionarioDeNvda() {
		val d = Diccionario.leer("Sr.\tseñor\t1\t0\n")
		assertEquals("señor García", d.aplicar("Sr. García"))
	}

	@Test
	fun diccionarioIdaYVuelta() {
		val d = Diccionario(listOf(EntradaDiccionario("a\tb", "c", TipoEntrada.TEXTO)))
		assertEquals(d.entradas, Diccionario.leer(d.escribir()).entradas)
	}

	@Test
	fun expresionMalEscritaNoRompe() {
		val d = Diccionario.leer("([\tx\texpresion\tno\t\n")
		assertEquals("hola", d.aplicar("hola"))
	}
}
