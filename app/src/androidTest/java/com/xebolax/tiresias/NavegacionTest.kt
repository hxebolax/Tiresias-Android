package com.xebolax.tiresias

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.nucleo.Listas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La navegación con Tiresias: el menú de Tiresias elemento a elemento (sin
 * saltarse los que no se ven) y personalizado, los controles de lectura
 * elegidos, los deslizadores con arriba y abajo, dar la vuelta, ir al final de
 * la lista entera, la lista de elementos y la lectura continua con pausa.
 */
@RunWith(AndroidJUnit4::class)
class NavegacionTest : PruebaTiresias() {

	private fun nombreAccion(a: Accion): String {
		val id = app.resources.getIdentifier("accion_" + a.name.lowercase(), "string", app.packageName)
		return app.getString(id)
	}

	@Test
	fun elMenuDeTiresiasSeRecorreEnteroSinSaltos() {
		abrirMuestrario()
		hacer(Accion.MENU_TIRESIAS, 1200)
		assertTrue("No se abrió el menú", s.ventanaAbiertaPruebas)
		val esperado = Ajustes().menu().map { nombreAccion(it) }
		val dichos = ArrayList<String>()
		dichos += ultimo()
		for (i in 0 until esperado.size + 6) {
			val d = hacer(Accion.SIGUIENTE, 300)
			if (d.startsWith("Cancelar")) break
			if (d != dichos.last()) dichos += d
		}
		// Cada opción del menú se ha dicho, en orden, antes de llegar a Cancelar
		var j = 0
		for (d in dichos) if (j < esperado.size && d.startsWith(esperado[j])) j++
		assertEquals("Se saltó opciones. Dicho: $dichos", esperado.size, j)
	}

	private inner class Ajustes {
		fun menu() = com.xebolax.tiresias.base.Ajustes(app).menuTiresias
	}

	@Test
	fun elMenuPersonalizadoSoloTieneLoElegido() {
		abrirMuestrario()
		ajuste("menu_tiresias", Listas.escribir(listOf(Accion.ESTADO_SISTEMA, Accion.REPETIR)))
		hacer(Accion.MENU_TIRESIAS, 1200)
		val dichos = mutableListOf(ultimo())
		repeat(4) { dichos += hacer(Accion.SIGUIENTE, 300) }
		assertTrue("Falta Hora, fecha y batería: $dichos", dichos.any { it.startsWith(nombreAccion(Accion.ESTADO_SISTEMA)) })
		assertTrue("Falta Repetir lo último: $dichos", dichos.any { it.startsWith(nombreAccion(Accion.REPETIR)) })
		assertTrue("Sale una opción quitada: $dichos", dichos.none { it.startsWith(nombreAccion(Accion.LEER_DESDE_ARRIBA)) })
		// Y elegir una opción la hace: la hora, fecha y batería
		s.cerrarVentanaPruebas()
		SystemClock.sleep(500)
		hacer(Accion.MENU_TIRESIAS, 1500)
		// Al abrir otra vez el menú, el foco está en la primera opción (no en la última que se tocó)
		assertNotNull("El menú no empezó por su primera opción: «${ultimo()}»", dichoQue(3000) { it.startsWith(nombreAccion(Accion.ESTADO_SISTEMA)) })
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull("Activar la opción no hizo nada", dichoQue(5000, m) { it.contains("batería al") })
	}

	@Test
	fun losControlesDeLecturaSonLosElegidos() {
		abrirMuestrario()
		ajuste("controles_lectura", Listas.escribir(listOf(ControlLectura.PREDETERMINADO, ControlLectura.CARACTERES, ControlLectura.MOTOR)))
		assertEquals("caracteres", hacer(Accion.CONTROL_SIGUIENTE))
		assertEquals("voz", hacer(Accion.CONTROL_SIGUIENTE))
		assertEquals("predeterminado", hacer(Accion.CONTROL_SIGUIENTE))
		assertEquals("voz", hacer(Accion.CONTROL_ANTERIOR))
		// Con el control de voz, arriba y abajo cambian de motor
		assertTrue(hacer(Accion.SELECTOR_SIGUIENTE).startsWith("Voz:"))
		hacer(Accion.SELECTOR_ANTERIOR)
	}

	@Test
	fun arribaYAbajoCambianElDeslizador() {
		abrirMuestrario()
		assertNotNull(irA("Volumen, 40 por ciento"))
		hacer(Accion.SELECTOR_ANTERIOR, 900)
		assertNotNull("No subió el deslizador", dichoQue { it.startsWith("45 por ciento") || it.contains("45 por ciento") })
		hacer(Accion.SELECTOR_SIGUIENTE, 900)
		assertNotNull("No bajó el deslizador", dichoQue { it.contains("40 por ciento") })
		// Con el ajuste quitado, arriba y abajo navegan
		ajuste("deslizador_arriba_abajo", false)
		val d = hacer(Accion.SELECTOR_SIGUIENTE)
		assertTrue("Debía pasar al siguiente: «$d»", d.startsWith("Descarga"))
	}

	@Test
	fun darLaVueltaAlFinal() {
		abrirMuestrario()
		ajuste("dar_vuelta", true)
		assertNotNull(irA("Mensaje 1,"))
		val m = marca()
		s.ejecutarPrueba(Accion.LISTA_FINAL)
		assertNotNull(dichoQue(15_000, m) { it.startsWith("Mensaje 40") })
		SystemClock.sleep(800)
		// Tras el último de todos, da la vuelta: vuelve a lo primero que hay en la pantalla
		val d = hacer(Accion.SIGUIENTE, 1200)
		assertTrue("No dio la vuelta: «$d»", !d.startsWith("Mensaje 40") && d.isNotBlank())
	}

	@Test
	fun alFinalYAlPrincipioDeLaListaEntera() {
		abrirMuestrario()
		assertNotNull(irA("Mensaje 1,"))
		var m = marca()
		s.ejecutarPrueba(Accion.LISTA_FINAL)
		assertNotNull("No llegó al final de la lista", dichoQue(15_000, m) { it.startsWith("Mensaje 40") })
		m = marca()
		s.ejecutarPrueba(Accion.LISTA_PRINCIPIO)
		assertNotNull("No volvió al principio de la lista", dichoQue(15_000, m) { it.startsWith("Mensaje 1,") })
	}

	@Test
	fun laListaDeElementosLlevaAlElegido() {
		abrirMuestrario()
		hacer(Accion.LISTA_ELEMENTOS, 1200)
		assertTrue(s.ventanaAbiertaPruebas)
		// Primera lista: qué ver; el foco entra en la primera opción, «Todos los elementos»
		assertNotNull(dichoQue(3000) { it.startsWith("Todos los elementos") })
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1500)
		assertTrue("No salió la segunda lista", s.ventanaAbiertaPruebas)
		assertNotNull("La segunda lista no se anunció", dichoQue(3000, m) { it.startsWith("Todos los elementos") })
		val d = irA("Guardar", 25)
		assertNotNull("Guardar no está en la lista", d)
		val m2 = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull("El foco no fue a Guardar", dichoQue(4000, m2) { it.startsWith("Guardar, botón") })
	}

	@Test
	fun leerDesdeArribaYPausarReanuda() {
		abrirMuestrario()
		val antes = s.vozPruebas!!.historial().size
		s.ejecutarPrueba(Accion.LEER_DESDE_ARRIBA)
		esperar(15_000) { (s.vozPruebas!!.historial().size - antes).takeIf { it >= 5 } }
		assertTrue("La lectura continua no avanzó", s.vozPruebas!!.historial().size - antes >= 5)
		s.ejecutarPrueba(Accion.PAUSAR_VOZ)
		SystemClock.sleep(800)
		assertTrue(!s.vozPruebas!!.hablando)
		val pausado = s.vozPruebas!!.historial().size
		s.ejecutarPrueba(Accion.PAUSAR_VOZ)
		SystemClock.sleep(1500)
		assertTrue("Pausar otra vez no reanudó", s.vozPruebas!!.historial().size > pausado || s.vozPruebas!!.hablando)
		s.ejecutarPrueba(Accion.PAUSAR_VOZ)
	}

	@Test
	fun losControlesDeNavegacionEnLaWeb() {
		abrirMuestrario(".ActividadWeb")
		SystemClock.sleep(2000)
		val l = s.lectorPruebas!!
		hacer(Accion.PRIMERO)
		l.control = ControlLectura.ENLACES
		assertTrue(hacer(Accion.SELECTOR_SIGUIENTE, 900).startsWith("Primer enlace"))
		assertTrue(hacer(Accion.SELECTOR_SIGUIENTE, 900).startsWith("Segundo enlace"))
		l.control = ControlLectura.TABLAS
		hacer(Accion.PRIMERO)
		assertTrue("No encontró la tabla", hacer(Accion.SELECTOR_SIGUIENTE, 1200).isNotBlank())
		l.control = ControlLectura.CONTROLES
		hacer(Accion.PRIMERO)
		val controles = (1..4).map { hacer(Accion.SELECTOR_SIGUIENTE, 900) }
		assertTrue("No llegó al botón Enviar: $controles", controles.any { it.startsWith("Enviar") })
	}

	@Test
	fun casillasYCaracteresEnElMuestrario() {
		abrirMuestrario()
		val l = s.lectorPruebas!!
		hacer(Accion.PRIMERO)
		l.control = ControlLectura.CASILLAS
		assertTrue(hacer(Accion.SELECTOR_SIGUIENTE).startsWith("Recordarme"))
		// Caracteres: el nombre del elemento letra a letra
		l.control = ControlLectura.PREDETERMINADO
		assertNotNull(irA("Guardar, botón"))
		l.control = ControlLectura.CARACTERES
		assertEquals("G", hacer(Accion.SELECTOR_SIGUIENTE).take(1).uppercase())
		// Letras fonéticas después de la letra
		ajuste("foneticas", "despues")
		l.control = ControlLectura.CARACTERES
		hacer(Accion.SELECTOR_SIGUIENTE, 1200)
		assertNotNull("No dijo la palabra fonética", dichoQue { it == "Último" })
		// Y la voz anuncia la voz elegida, no la que sonaba antes
		ajuste("controles_lectura", "PREDETERMINADO,MOTOR")
		l.control = ControlLectura.MOTOR
		val a = hacer(Accion.SELECTOR_SIGUIENTE)
		val b = hacer(Accion.SELECTOR_ANTERIOR)
		assertTrue("Cambiar de voz adelante y atrás dijo lo mismo: «$a»", a != b)
	}
}
