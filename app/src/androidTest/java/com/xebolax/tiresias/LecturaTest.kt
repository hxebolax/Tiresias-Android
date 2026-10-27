package com.xebolax.tiresias

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Listas
import com.xebolax.tiresias.nucleo.Parte
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lo que se dice y cómo, con cada ajuste de lectura: la verbosidad, decir o no
 * el tipo, la posición y las pistas, el orden libre de las partes, la
 * puntuación, los números, el diccionario, las etiquetas, las reglas de los
 * complementos y los encabezados de las tablas.
 */
@RunWith(AndroidJUnit4::class)
class LecturaTest : PruebaTiresias() {

	/** Lo que se dice del botón Guardar y de la casilla del muestrario con los ajustes de ahora. */
	private fun guardar(inicio: String = "Guardar"): String {
		abrirMuestrario()
		return irA(inicio) ?: ""
	}

	@Test
	fun verbosidadYPartes() {
		assertEquals("Guardar, botón", guardar())
		// Personalizada, sin el tipo
		ajuste("nivel_verbosidad", "PERSONALIZADA")
		ajuste("decir_rol", false)
		assertEquals("Guardar", guardar())
		ajuste("decir_rol", true)
		// Orden libre: el tipo antes del nombre
		ajuste("orden", "libre")
		ajuste("orden_partes", Listas.escribir(listOf(Parte.ROL, Parte.NOMBRE, Parte.ESTADO)))
		assertEquals("botón, Guardar", guardar("botón, Guardar"))
		// La posición, quitada del orden libre, no se dice en la lista
		abrirMuestrario()
		val fila = irA("Mensaje 1")
		assertEquals("Mensaje 1", fila)
	}

	@Test
	fun verbosidadBajaQuitaLaPosicion() {
		abrirMuestrario()
		assertTrue(irA("Mensaje 1")!!.contains("1 de 40"))
		ajuste("nivel_verbosidad", "BAJA")
		abrirMuestrario()
		assertTrue("Con verbosidad baja no debe decir la posición", !(irA("Mensaje 1") ?: "").contains("de 40"))
	}

	private fun dice(texto: String): String {
		val m = marca()
		s.vozPruebas!!.decir(Enunciado(texto, tipo = Tipo.NAVEGACION))
		return dichoQue(3000, m) { true } ?: ""
	}

	@Test
	fun puntuacionNumerosYMayusculas() {
		ajuste("puntuacion", "NINGUNA")
		val ninguna = dice("Hola, ¿qué tal? (bien)")
		ajuste("puntuacion", "TODAS")
		val todas = dice("Hola, ¿qué tal? (bien)")
		assertTrue("Con toda la puntuación debe decir más: «$ninguna» / «$todas»", todas.length > ninguna.length && "coma" in todas)
		ajuste("numeros", "DIGITOS")
		assertTrue(dice("Tengo 1234 euros").contains("1 2 3 4"))
		ajuste("numeros", "CIFRAS")
		assertTrue(dice("Tengo 1234 euros").contains("1234"))
	}

	@Test
	fun losEmojisEnSusTresModos() {
		val cara = "Hola \uD83D\uDE00"
		ajuste("modo_emojis", "QUITAR")
		assertEquals("Hola", dice(cara))
		ajuste("modo_emojis", "MOTOR")
		assertEquals(cara, dice(cara))
		ajuste("modo_emojis", "NOMBRE")
		val nombre = dice(cara)
		assertTrue("Por su nombre debe decir el nombre del emoji: «$nombre»", nombre.startsWith("Hola ") && nombre.length > 6 && "\uD83D" !in nombre)
	}

	/** Un nodo suelto con [texto] en [idioma], dicho como si se enfocara; devuelve el idioma pedido a la voz. */
	private fun idiomaAlDecir(texto: String, idioma: java.util.Locale): java.util.Locale? {
		// Los nodos sueltos son «iguales» para la caché del lector: se vacía antes de cada uno
		s.recargarAjustes()
		SystemClock.sleep(300)
		val n = android.view.accessibility.AccessibilityNodeInfo()
		n.text = android.text.SpannableString(texto).apply { setSpan(android.text.style.LocaleSpan(idioma), 0, length, 0) }
		val m = marca()
		s.lectorPruebas!!.manejador.post { s.lectorPruebas!!.decirElemento(n) }
		assertNotNull(dichoQue(3000, m) { it.startsWith(texto) })
		return s.vozPruebas!!.idiomaPruebas
	}

	@Test
	fun cambiaDeIdiomaSegunElTexto() {
		// La app marca el texto en francés de Canadá (LocaleSpan): se dice con esa voz
		assertEquals(java.util.Locale.CANADA_FRENCH, idiomaAlDecir("Bonjour tout le monde", java.util.Locale.CANADA_FRENCH))
		// Sin la variante: solo el idioma
		ajuste("cambio_dialecto", false)
		assertEquals(java.util.Locale("fr"), idiomaAlDecir("Bonjour tout le monde", java.util.Locale.CANADA_FRENCH))
		// Español de México con la variante quitada: es el mismo idioma que el de Tiresias, no cambia
		assertNull(idiomaAlDecir("Hola a todos", java.util.Locale("es", "MX")))
		// Sin cambio de idioma: nunca
		ajuste("cambio_idioma", false)
		assertNull(idiomaAlDecir("Bonjour tout le monde", java.util.Locale.CANADA_FRENCH))
	}

	@Test
	fun copiaPorTrozosDeUnTextoQueNoSeEdita() {
		abrirMuestrario()
		assertEquals("Controles, encabezado", hacer(Accion.PRIMERO))
		s.lectorPruebas!!.control = com.xebolax.tiresias.nucleo.ControlLectura.CARACTERES
		hacer(Accion.MODO_SELECCION)
		// Tres caracteres marcados: «Con»
		repeat(3) { hacer(Accion.SELECTOR_SIGUIENTE, 300) }
		val m = marca()
		s.ejecutarPrueba(Accion.MODO_SELECCION)
		assertNotNull("No guardó el trozo «Con»", dichoQue(3000, m) { it.startsWith("Trozo guardado: Con.") })
		val m2 = marca()
		s.ejecutarPrueba(Accion.COPIAR)
		assertNotNull("No copió el trozo", dichoQue(3000, m2) { it == "Copiado 1 trozo" })
	}

	@Test
	fun elDiccionarioCambiaLaPronunciacion() {
		val f = File(Almacen.protegido(app).filesDir, "diccionario.tsv")
		val antes = f.takeIf { it.isFile }?.readText()
		try {
			f.writeText("Guardar\tGuardar ya\tpalabra\n")
			s.recargarAjustes()
			SystemClock.sleep(500)
			assertTrue(dice("Guardar").contains("Guardar ya"))
		} finally {
			if (antes != null) f.writeText(antes) else f.delete()
			s.recargarAjustes()
		}
	}

	@Test
	fun lasEtiquetasNombranLosBotones() {
		abrirMuestrario()
		assertTrue(irA("boton enviar")!!.contains("sin nombre"))
		val etiquetas = s.lectorPruebas!!.etiquetas
		try {
			etiquetas.poner("com.xebolax.tiresias.muestrario", "com.xebolax.tiresias.muestrario:id/boton_enviar", "Enviar mensaje")
			s.recargarAjustes()
			SystemClock.sleep(500)
			abrirMuestrario()
			assertEquals("Enviar mensaje, botón", irA("Enviar mensaje"))
		} finally {
			etiquetas.poner("com.xebolax.tiresias.muestrario", "com.xebolax.tiresias.muestrario:id/boton_enviar", null)
			s.recargarAjustes()
		}
	}

	@Test
	fun lasReglasDeLosComplementos() {
		val scripts = com.xebolax.tiresias.scripts.Scripts(null, app)
		val codigo = """
			export const manifest = { id: "prueba-reglas", nombre: "Reglas", version: "1.0", descripcion: "Prueba",
				apps: ["com.xebolax.tiresias.muestrario"], permisos: [],
				reglas: [ { id: "com.xebolax.tiresias.muestrario:id/boton_enviar", etiqueta: "Mandar" } ] };
		""".trimIndent()
		try {
			scripts.instalar(codigo)
			s.recargarComplementos()
			SystemClock.sleep(800)
			abrirMuestrario()
			assertEquals("Mandar, botón", irA("Mandar"))
		} finally {
			scripts.desinstalar("prueba-reglas")
			s.recargarComplementos()
		}
	}

	@Test
	fun losEncabezadosDeLasTablas() {
		abrirMuestrario(".ActividadWeb")
		SystemClock.sleep(2000)
		val conEncabezado = irA("1 euro") ?: irA("Pan") ?: ""
		ajuste("encabezados_tabla", false)
		abrirMuestrario(".ActividadWeb")
		SystemClock.sleep(2000)
		val sin = irA("1 euro") ?: irA("Pan") ?: ""
		assertTrue("Sin encabezados debe decir menos: «$conEncabezado» / «$sin»", sin.isNotBlank() && sin.length <= conEncabezado.length)
	}
}
