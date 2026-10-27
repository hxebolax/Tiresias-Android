package com.xebolax.tiresias.nucleo.braille

import com.xebolax.tiresias.nucleo.Accion
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrailleTest {
	/** Las tablas de liblouis que lleva la app. */
	private val carpeta = File("../app/src/main/assets/braille")

	private fun tabla(nombre: String) = TablaInformatica(nombre, File(carpeta, nombre).readLines().asSequence()) { File(carpeta, it).readLines().asSequence() }

	private fun celdas(vararg puntos: String) = puntos.map { Puntos.de(it).toByte() }.toByteArray()

	@Test
	fun informaticaEspanola() {
		val es = tabla("Es-Es-G0.utb")
		assertArrayEquals(celdas("1257", "135", "123", "1"), es.traducir("Hola").celdas)
		assertArrayEquals(celdas("12356", "124568", "0", "16", "34678"), es.traducir("áñ 10").celdas)
		// Lo que no está en la tabla: los 8 puntos
		assertEquals(0xFF.toByte(), es.traducir("中").celdas[0])
		// Y al revés, para escribir desde la línea
		val e = es.entrada()
		assertEquals("h", e.introducir(Puntos.de("125")))
		assertEquals("H", e.introducir(Puntos.de("1257")))
		assertEquals("ñ", e.introducir(Puntos.de("124568")))
		assertEquals("1", e.introducir(Puntos.de("16")))
	}

	@Test
	fun informaticaInglesa() {
		val en = tabla("en-us-comp8-ext.utb")
		assertArrayEquals(celdas("1257", "15", "123", "123", "135"), en.traducir("Hello").celdas)
		assertArrayEquals(celdas("2"), en.traducir("1").celdas)
	}

	@Test
	fun literariaEspanola() {
		val lit = TablaLiteraria(null)
		// Mayúscula con su signo; números con el signo de número
		assertArrayEquals(celdas("46", "125", "135", "123", "1", "0", "3456", "12", "15"), lit.traducir("Hola 25").celdas)
		// Una letra de la a a la j tras un número lleva el punto 5 delante
		assertArrayEquals(celdas("3456", "14", "5", "1"), lit.traducir("3a").celdas)
		// La coma decimal no corta el número
		assertArrayEquals(celdas("3456", "14", "2", "15"), lit.traducir("3,5").celdas)
		// Dónde cae cada carácter (para el cursor y las teclas de encaminamiento)
		val t = lit.traducir("Ab")
		assertEquals(0, t.celdaDe[0])
		assertEquals(2, t.celdaDe[1])
		assertEquals(3, t.celdaDe[2])
		assertEquals(0, t.caracterDe[1])
	}

	@Test
	fun literariaAlEscribir() {
		val e = TablaLiteraria(null).entrada()
		val escrito = listOf("46", "125", "135", "0", "3456", "12", "15", "0", "1").joinToString("") { e.introducir(Puntos.de(it)).orEmpty() }
		assertEquals("Ho 25 a", escrito)
	}

	/**
	 * Un descriptor como el del ejemplo de la norma HID Braille: 20 celdas de
	 * 8 puntos (salida), 20 teclas de encaminamiento, los 8 puntos y el espacio
	 * y las teclas de desplazar (entrada), todo en el informe 1.
	 */
	private val descriptor = bytes(
		0x05, 0x41, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x01,
		0x09, 0x02, 0xA1, 0x02, 0x09, 0x03, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x14, 0x91, 0x02, 0xC0,
		0x09, 0xFA, 0xA1, 0x02, 0x0A, 0x00, 0x01, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x14, 0x81, 0x02, 0xC0,
		0x95, 0x04, 0x81, 0x01,
		0x1A, 0x01, 0x02, 0x2A, 0x09, 0x02, 0x95, 0x09, 0x81, 0x02,
		0x0A, 0x1A, 0x02, 0x0A, 0x1B, 0x02, 0x95, 0x02, 0x81, 0x02,
		0x95, 0x05, 0x81, 0x01,
		0xC0,
	)

	private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

	@Test
	fun descriptorDeUnaLinea() {
		val linea = LineaHid.crear(descriptor)
		assertNotNull(linea)
		linea!!
		assertEquals(20, linea.celdas)
		assertTrue(linea.ochoPuntos)
		// Salida: el identificador y las 20 celdas
		val informe = linea.informe(celdas("1", "12"))
		assertEquals(21, informe.size)
		assertEquals(1, informe[0].toInt())
		assertEquals(Puntos.de("12").toByte(), informe[2])
		assertEquals(0, informe[20].toInt())
		// Entrada: encaminamiento 3, y luego espacio + punto 1
		val router = bytes(0x01, 0x08, 0x00, 0x00, 0x00, 0x00)
		assertEquals(setOf(TeclaLinea(UsosBraille.ENCAMINAMIENTO, 3)), linea.teclas(router))
		val acorde = bytes(0x01, 0x00, 0x00, 0x00, 0x01, 0x01)
		assertEquals(setOf(TeclaLinea(UsosBraille.PUNTO_1), TeclaLinea(UsosBraille.ESPACIO)), linea.teclas(acorde))
		// Desplazar a la derecha: el bit 10 del campo de teclas
		assertEquals(setOf(TeclaLinea(UsosBraille.DESPLAZAR_DERECHA)), linea.teclas(bytes(0x01, 0x00, 0x00, 0x00, 0x00, 0x04)))
	}

	@Test
	fun sinCeldasNoEsUnaLinea() {
		// Un ratón: página genérica, sin celdas braille
		val raton = bytes(0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01, 0x81, 0x02, 0xC0)
		assertNull(LineaHid.crear(raton))
	}

	@Test
	fun lasTeclasSeJuntanHastaSoltarlas() {
		val t = TecladoLinea()
		assertNull(t.alTeclas(setOf(TeclaLinea(UsosBraille.ESPACIO))))
		assertNull(t.alTeclas(setOf(TeclaLinea(UsosBraille.ESPACIO), TeclaLinea(UsosBraille.PUNTO_1 + 3))))
		assertEquals(OrdenLinea.Hacer(Accion.SIGUIENTE), t.alTeclas(emptySet()))
		// Puntos sin espacio: se escribe la celda
		t.alTeclas(setOf(TeclaLinea(UsosBraille.PUNTO_1), TeclaLinea(UsosBraille.PUNTO_1 + 1)))
		assertEquals(OrdenLinea.Escribir(Puntos.de("12")), t.alTeclas(emptySet()))
		// Encaminamiento
		t.alTeclas(setOf(TeclaLinea(UsosBraille.ENCAMINAMIENTO, 7)))
		assertEquals(OrdenLinea.Encaminar(7), t.alTeclas(emptySet()))
		assertEquals(OrdenLinea.Borrar, TecladoLinea.orden(setOf(TeclaLinea(UsosBraille.PUNTO_1 + 6))))
		assertEquals(OrdenLinea.Desplazar(true), TecladoLinea.orden(setOf(TeclaLinea(UsosBraille.DESPLAZAR_DERECHA))))
	}

	@Test
	fun laVentanaNoPartePalabras() {
		val lit = TablaLiteraria(null)
		val v = VistaLinea(8)
		v.poner(lit.traducir("uno dos tres cuatro"))
		// «uno dos » cabe; «tres» no se parte
		assertArrayEquals(lit.traducir("uno dos ").celdas, v.celdas())
		assertTrue(v.avanzar())
		assertArrayEquals(lit.traducir("tres").celdas + ByteArray(4), v.celdas())
		assertTrue(v.avanzar())
		assertArrayEquals(lit.traducir("cuatro").celdas + ByteArray(2), v.celdas())
		assertFalse(v.avanzar())
		assertTrue(v.retroceder())
		assertArrayEquals(lit.traducir("tres").celdas + ByteArray(4), v.celdas())
		// La tecla de encaminamiento 1 está sobre la «r» de «tres» (carácter 9)
		assertEquals(9, v.caracterEn(1))
	}

	@Test
	fun cursorConPuntosSieteYOcho() {
		val es = TablaLiteraria(null)
		val v = VistaLinea(10)
		v.poner(es.traducir("hola"), cursorTexto = 2)
		assertEquals(Puntos.de("123") or Puntos.CURSOR, v.celdas()[2].toInt() and 0xFF)
		// Un cursor más allá de la ventana la mueve
		v.poner(es.traducir("una frase bastante larga"), cursorTexto = 20)
		assertTrue(v.celdas().any { it.toInt() and Puntos.CURSOR == Puntos.CURSOR })
		assertTrue(v.retroceder())
	}

	@Test
	fun lasCombinacionesDeLaLineaSeCambian() {
		val mapa = TecladoLinea.leerAcordes("12=MENU_TIRESIAS;13=NINGUNA;2345=OCR_PANTALLA;9=ATRAS;1=INVENTADA")
		assertEquals(Accion.MENU_TIRESIAS, mapa[Puntos.de("12")])
		assertEquals(null, mapa[Puntos.de("13")])
		assertEquals(Accion.OCR_PANTALLA, mapa[Puntos.de("2345")])
		// Lo que no se entiende se ignora: el punto 1 sigue con lo de fábrica
		assertEquals(TecladoLinea.ACORDES[Puntos.de("1")], mapa[Puntos.de("1")])
		// Se guarda solo lo que cambia y se lee igual
		val texto = TecladoLinea.escribirAcordes(mapa)
		assertEquals(mapa, TecladoLinea.leerAcordes(texto))
		assertEquals("", TecladoLinea.escribirAcordes(TecladoLinea.ACORDES))
		// Con espacio, la combinación del usuario
		val teclas = setOf(TeclaLinea(UsosBraille.ESPACIO), TeclaLinea(UsosBraille.PUNTO_1), TeclaLinea(UsosBraille.PUNTO_1 + 1))
		assertEquals(OrdenLinea.Hacer(Accion.MENU_TIRESIAS), TecladoLinea.orden(teclas, mapa))
	}

	@Test
	fun elCursorSeMarcaComoSeElija() {
		val v = VistaLinea(10)
		v.poner(Traduccion(ByteArray(3) { 1 }, IntArray(4) { it }, IntArray(3) { it }), 1)
		assertEquals(1 or Puntos.CURSOR, v.celdas()[1].toInt() and 0xFF)
		v.puntosCursor = Puntos.P8
		assertEquals(1 or Puntos.P8, v.celdas()[1].toInt() and 0xFF)
		v.puntosCursor = 0
		assertEquals(1, v.celdas()[1].toInt() and 0xFF)
	}
}
