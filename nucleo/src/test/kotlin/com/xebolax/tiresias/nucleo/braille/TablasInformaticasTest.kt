package com.xebolax.tiresias.nucleo.braille

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Las tablas informáticas de 8 puntos que se pueden elegir (Ajustes, Braille):
 * que se leen con sus inclusiones, que las letras y las cifras tienen celdas
 * distintas y que lo escrito en la línea vuelve a ser el mismo texto.
 */
class TablasInformaticasTest {
	private val carpeta = File("../app/src/main/assets/braille")

	private fun tabla(nombre: String) = TablaInformatica(nombre, File(carpeta, nombre).readLines().asSequence()) { incluida ->
		File(carpeta, incluida.substringAfterLast('/')).takeIf { it.isFile }?.readLines()?.asSequence() ?: emptySequence()
	}

	private val latinas = listOf(
		"cs-comp8.utb", "de-de-comp8.ctb", "en-us-comp8.ctb", "en-us-comp8-ext.utb", "fi-fi-8dot.ctb", "fr-bfu-comp8.utb",
		"hr-comp8.utb", "hu-hu-comp8.ctb", "it-it-comp8.utb", "nl-comp8.utb", "no-no-comp8.ctb", "pl-pl-comp8.ctb", "pt-pt-comp8.ctb",
		"Es-Es-G0.utb",
	)

	@Test
	fun lasLetrasYLasCifrasTienenCeldasDistintas() {
		for (nombre in latinas) {
			val t = tabla(nombre)
			val letras = ('a'..'z').map { t.celda(it.code) }
			assertTrue("$nombre: faltan letras", letras.all { it != null && it != 0 })
			assertEquals("$nombre: dos letras con la misma celda", 26, letras.toSet().size)
			val cifras = ('0'..'9').map { t.celda(it.code) }
			assertTrue("$nombre: faltan cifras", cifras.all { it != null })
			assertEquals("$nombre: dos cifras con la misma celda", 10, cifras.toSet().size)
		}
	}

	@Test
	fun loEscritoVuelveASerElMismoTexto() {
		for (nombre in latinas) {
			val t = tabla(nombre)
			val e = t.entrada()
			val texto = "hola 123"
			val vuelta = t.traducir(texto).celdas.joinToString("") { e.introducir(it.toInt() and 0xFF).orEmpty() }
			assertEquals("$nombre: ida y vuelta", texto, vuelta)
		}
	}

	@Test
	fun lasDeOtrosAlfabetosTambienSeLeen() {
		for ((nombre, letra) in listOf("ru-comp8.utb" to 'д', "he-IL-comp8.utb" to 'א', "ar-ar-comp8.utb" to 'ب')) {
			val t = tabla(nombre)
			val celda = t.celda(letra.code)
			assertTrue("$nombre: no tiene «$letra»", celda != null && celda != 0)
			assertNotEquals("$nombre: «$letra» es lo mismo que el espacio", t.celda(' '.code), celda)
		}
	}
}
