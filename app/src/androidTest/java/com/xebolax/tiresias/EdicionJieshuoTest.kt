package com.xebolax.tiresias

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.lectura.EdicionPartes
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El modo edición y la edición por partes, como en Jieshuo, en un campo de
 * varias líneas del muestrario («Hola, Ana. ¿Vienes mañana?» y «Yo sí.»).
 */
@RunWith(AndroidJUnit4::class)
class EdicionJieshuoTest : PruebaTiresias() {
	private val muestrario = "com.xebolax.tiresias.muestrario"
	private val texto = "Hola, Ana. ¿Vienes mañana?\nYo sí."

	/** Abre el editor y pone el foco en su campo. */
	private fun campo(): AccessibilityNodeInfo {
		val m = marca()
		app.startActivity(Intent().setClassName(muestrario, "$muestrario.ActividadEditor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
		esperar(10_000) { s.lectorPruebas?.raiz()?.takeIf { it.packageName == muestrario && it.childCount > 0 } }
		esperarCalma(m)
		val l = s.lectorPruebas!!
		val n = esperar(5000) { l.raiz()?.findAccessibilityNodeInfosByText("Vienes")?.firstOrNull { it.isEditable } }
		assertNotNull("No está el campo", n)
		l.manejador.post { l.enfocar(n!!) }
		SystemClock.sleep(800)
		return n!!
	}

	private fun gesto(g: Gesto, espera: Long = 700): String {
		val m = marca()
		s.gestoPrueba(g)
		dichoQue(3000, m) { true }
		SystemClock.sleep(espera)
		return s.vozPruebas!!.dichoDesde(m).reversed().joinToString(" | ")
	}

	@Test
	fun elModoEdicionRecorreYSelecciona() {
		val n = campo()
		assertTrue("No entró en el modo: ${hacer(Accion.MODO_EDICION)}", s.lectorPruebas!!.modoEdicion.activo)
		assertTrue(gesto(Gesto.ARRIBA_ABAJO).contains("Principio"))
		// Arriba y abajo, letras
		val letra = gesto(Gesto.ABAJO)
		assertTrue("Abajo no dijo la letra: $letra", letra.startsWith("H") || letra.contains("h", ignoreCase = true))
		n.refresh()
		assertEquals(1, n.textSelectionEnd)
		// Derecha, la frase siguiente; ida y vuelta, la palabra siguiente
		assertTrue(gesto(Gesto.DERECHA).contains("Vienes mañana"))
		assertTrue(gesto(Gesto.ABAJO_2).contains("Yo sí"))
		// Seleccionar con los mismos movimientos: solo se dice lo que cambia
		gesto(Gesto.ARRIBA_ABAJO)
		assertTrue(gesto(Gesto.ARRIBA_DERECHA).contains("Selección empezada"))
		val seleccionado = gesto(Gesto.DERECHA_IZQUIERDA)
		assertTrue("No dijo lo seleccionado: $seleccionado", seleccionado.contains("Seleccionado: Hola"))
		n.refresh()
		assertEquals(0, minOf(n.textSelectionStart, n.textSelectionEnd))
		assertEquals(4, maxOf(n.textSelectionStart, n.textSelectionEnd))
		assertTrue(gesto(Gesto.ARRIBA_DERECHA).contains("4 caracteres"))
		// Salir: lo seleccionado se queda seleccionado, y los gestos vuelven a ser los de siempre
		assertTrue(gesto(Gesto.IZQUIERDA_ABAJO).contains("Fin del modo edición"))
		assertTrue(!s.lectorPruebas!!.modoEdicion.activo)
		n.refresh()
		assertEquals(4, maxOf(n.textSelectionStart, n.textSelectionEnd))
		// Y el texto, intacto
		assertEquals(texto, n.text.toString())
	}

	@Test
	fun laEdicionPorPartesDevuelveElTextoAlCampo() {
		val n = campo()
		val m = marca()
		s.ejecutarPrueba(Accion.EDICION_POR_PARTES)
		assertNotNull("No se abrió la edición por partes", dichoQue(8000, m) { it.startsWith("Edición por partes") })
		val t = EdicionPartes.trabajo
		assertNotNull(t)
		assertEquals(texto, t!!.original)
		assertTrue(t.editable)
		// Lo que hace «Guardar en el campo»: devolverlo y cerrar la pantalla (sin cambios en ella, Atrás la cierra sin preguntar)
		val m2 = marca()
		t.guardar("Texto cambiado por partes")
		s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
		assertNotNull("No dijo que lo había guardado", dichoQue(8000, m2) { it.startsWith("Texto guardado") })
		n.refresh()
		assertEquals("Texto cambiado por partes", n.text.toString())
	}

	private fun tecla(accion: Int, codigo: Int): Boolean {
		var r = false
		inst.runOnMainSync { r = s.teclaPrueba(android.view.KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), accion, codigo, 0)) }
		return r
	}

	/** Con «Teclas de volumen en los campos de texto», volumen abajo lleva el cursor un carácter adelante y lo dice. */
	@Test
	fun lasTeclasDeVolumenMuevenElCursor() {
		val n = campo()
		ajuste("volumen_cursor", true)
		val l = s.lectorPruebas!!
		// El cursor al principio
		l.manejador.post { l.edicion.cursorA(true) }
		SystemClock.sleep(800)
		val m = marca()
		assertTrue("Tiresias no se quedó la tecla", tecla(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
		tecla(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
		assertNotNull("No dijo la letra", dichoQue(3000, m) { it.contains("hache", ignoreCase = true) || it == "H" })
		n.refresh()
		assertEquals(1, n.textSelectionEnd)
		tecla(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)
		tecla(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_UP)
		SystemClock.sleep(800)
		n.refresh()
		assertEquals(0, n.textSelectionEnd)
		// Con el selector en palabras, por palabras
		l.control = com.xebolax.tiresias.nucleo.ControlLectura.PALABRAS
		val m2 = marca()
		tecla(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
		tecla(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
		assertNotNull("No dijo la palabra", dichoQue(3000, m2) { it.startsWith("Hola") })
		n.refresh()
		assertEquals(4, n.textSelectionEnd)
		// Una lectura continua que se quedó marcada sin hablar (la cortó otra cosa) no les quita lo suyo a las teclas
		l.manejador.post { l.continua.desdeAqui() }
		SystemClock.sleep(300)
		s.vozPruebas!!.callar()
		SystemClock.sleep(2500)
		l.control = com.xebolax.tiresias.nucleo.ControlLectura.CARACTERES
		l.manejador.post { l.enfocar(n) }
		SystemClock.sleep(800)
		assertTrue("Las teclas no son del campo tras cortar la lectura", tecla(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
		tecla(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
		SystemClock.sleep(800)
		assertTrue("La lectura continua se quedó marcada", !l.continua.leyendo)
	}
}
