package com.xebolax.tiresias

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.nucleo.Accion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que contaron los usuarios de la 1.0.9: el micrófono de Telegram, que
 * Tiresias no tomaba por una parada (y el doble toque mantenido abría el menú
 * de enviar), y las notificaciones con el móvil en silencio y la pantalla
 * apagada.
 */
@RunWith(AndroidJUnit4::class)
class Peticiones110Test : PruebaTiresias() {
	private val muestrario = "com.xebolax.tiresias.muestrario"

	private fun abrir(clase: String) {
		val m = marca()
		app.startActivity(Intent().setClassName(muestrario, "$muestrario.$clase").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
		esperar(10_000) { s.lectorPruebas?.raiz()?.takeIf { it.packageName == muestrario && it.childCount > 0 } }
		esperarCalma(m)
	}

	/**
	 * En la barra de Telegram, el micrófono (un marco enfocable con nombre dentro del contenedor de enviar, que se
	 * mantiene pulsado) es su propia parada y un botón de mantener pulsado; el contenedor no es una parada.
	 */
	@Test
	fun elMicrofonoDeTelegramEsSuPropiaParada() {
		abrir("ActividadTelegram")
		val l = s.lectorPruebas!!
		val dichos = arrayListOf(hacer(Accion.PRIMERO, 300))
		var micro: android.view.accessibility.AccessibilityNodeInfo? = null
		repeat(6) {
			val d = hacer(Accion.SIGUIENTE, 300)
			dichos += d
			if (d.startsWith("Grabar mensaje de voz")) micro = l.foco
		}
		val todo = dichos.joinToString(" | ")
		val m = micro
		assertNotNull("El micrófono no es una parada: $todo", m)
		assertFalse("La parada del micrófono es el contenedor que se mantiene: $todo", m!!.isLongClickable)
		assertTrue("El micrófono no se toma por un botón de mantener pulsado", Nodos.esDeMantenerPulsado(m))
		assertEquals("El micrófono sale más de una vez: $todo", 1, dichos.count { it.startsWith("Grabar mensaje de voz") })
		assertFalse("Hay una parada sin nada (el contenedor): $todo", dichos.any { it.isBlank() || it.startsWith("Sin nombre") })
	}

	/** «Respetar el modo silencio» viene puesto, y con la pantalla en uso no calla nada (solo con ella apagada o bloqueada). */
	@Test
	fun elModoSilencioNoCallaConLaPantallaEnUso() {
		assertTrue("«Respetar el modo silencio» no viene puesto", Ajustes(app).callarEnSilencio)
		assertEquals(null, s.lectorPruebas!!.motivoCallarSinPedir())
	}
}
