package com.xebolax.tiresias

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.nucleo.Accion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo de la 1.0.21: la pantalla sin título se lleva el foco (como en TalkBack), vigilar el chat de un directo dice solo
 * los mensajes nuevos (foro 120) y el eco del teclado se cambia con una acción (foro 125).
 */
@RunWith(AndroidJUnit4::class)
class Peticiones121Test : PruebaTiresias() {

	@Test
	fun unaPantallaSinTituloSeLlevaElFoco() {
		abrirMuestrario()
		val m = marca()
		app.startActivity(Intent().setClassName(MUESTRARIO, "$MUESTRARIO.ActividadSinTitulo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		val dicho = dichoQue(10_000, m) { it.startsWith("Primero sin título") }
		assertTrue("El foco no llega a la pantalla sin título: ${s.vozPruebas!!.dichoDesde(m)}", dicho != null)
		val foco = s.lectorPruebas!!.foco
		assertEquals("El foco no está en la pantalla nueva", "Primero sin título", foco?.text?.toString())
	}

	@Test
	fun vigilarElChatDeUnDirectoDiceSoloLoNuevo() {
		abrirMuestrario(".ActividadDirecto")
		hacer(Accion.PRIMERO)
		val puesto = hacer(Accion.VIGILAR)
		try {
			assertTrue("No vigila la lista: «$puesto»", puesto.startsWith("Vigilando: la lista"))
			val m = marca()
			val nuevo = dichoQue(10_000, m) { Regex("""^Mensaje del directo \d+$""").matches(it) }
			assertTrue("No dice los mensajes nuevos: ${s.vozPruebas!!.dichoDesde(m)}", nuevo != null)
			val numero = nuevo!!.substringAfterLast(' ').toInt()
			assertTrue("Dice un mensaje que ya estaba: «$nuevo»", numero > 5)
			assertTrue("Dice la lista entera como cambio: ${s.vozPruebas!!.dichoDesde(m)}", s.vozPruebas!!.dichoDesde(m).none { it.startsWith("Ha cambiado") })
		} finally {
			hacer(Accion.VIGILAR)
		}
	}

	@Test
	fun elEcoDelTecladoCambiaConUnaAccion() {
		ajuste("eco_teclado", "caracteres")
		val dicho = hacer(Accion.ECO_SIGUIENTE)
		assertEquals("Eco: Palabras", dicho)
		assertEquals("palabras", prefs.getString("eco_teclado", null))
		hacer(Accion.ECO_SIGUIENTE)
		hacer(Accion.ECO_SIGUIENTE)
		assertEquals("Eco: Nada", ultimo())
		assertEquals("Vuelve a empezar", "Eco: Caracteres", hacer(Accion.ECO_SIGUIENTE))
	}

	companion object {
		private const val MUESTRARIO = "com.xebolax.tiresias.muestrario"
	}
}
