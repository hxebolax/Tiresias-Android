package com.xebolax.tiresias

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.ui.ActividadAjustes
import com.xebolax.tiresias.ui.ActividadSonidos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que tiene que hacer lo mismo en todas partes (informe de la 1.0.1): las
 * acciones de un elemento con arriba y abajo y el doble toque que hace la
 * elegida, con el control predeterminado y con el de Acciones; el valor de un
 * deslizador dicho una sola vez; y dar la vuelta al principio y volver atrás.
 */
@RunWith(AndroidJUnit4::class)
class CoherenciaTest : PruebaTiresias() {

	/** Va con «siguiente» desde el principio hasta lo que contenga [texto]. */
	private fun irAQueContenga(texto: String, maximo: Int = 40): String? {
		if (hacer(Accion.PRIMERO).contains(texto)) return ultimo()
		repeat(maximo) {
			val d = hacer(Accion.SIGUIENTE, 350)
			if (d.contains(texto)) return d
		}
		return null
	}

	private fun sonidoActivarApagado() = "ACTIVAR" in Ajustes(app).sonidosApagados

	@Test
	fun arribaYAbajoEligenLasAccionesYElDobleToqueHaceLaElegida() {
		abrir(ActividadSonidos::class.java)
		assertNotNull(irA("Al activar"))
		val apagadoAntes = sonidoActivarApagado()
		// Con el control predeterminado, en una fila con acciones: las acciones, no el elemento siguiente
		assertEquals("Probar", hacer(Accion.SELECTOR_SIGUIENTE))
		val segunda = hacer(Accion.SELECTOR_SIGUIENTE)
		assertTrue("La segunda acción no es la de vibrar: «$segunda»", segunda.startsWith("No vibrar") || segunda.startsWith("Vibrar"))
		assertEquals("Probar", hacer(Accion.SELECTOR_ANTERIOR))
		// El doble toque prueba el sonido: la casilla no cambia
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1200)
		assertEquals("El doble toque cambió la casilla en vez de probar el sonido", apagadoAntes, sonidoActivarApagado())
		// Al pasar a otro elemento y volver, la elección se olvida: el doble toque activa como siempre
		hacer(Accion.SIGUIENTE)
		assertTrue(hacer(Accion.ANTERIOR).startsWith("Al activar"))
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1200)
		assertEquals("El doble toque no cambió la casilla", !apagadoAntes, sonidoActivarApagado())
	}

	@Test
	fun elControlAccionesEligeConArribaYAbajo() {
		// Sin «arriba y abajo eligen las acciones», el predeterminado navega
		ajuste(Claves.ACCIONES_ARRIBA_ABAJO, false)
		abrir(ActividadSonidos::class.java)
		assertNotNull(irA("Al activar"))
		val siguiente = hacer(Accion.SELECTOR_SIGUIENTE)
		assertFalse("Sin el ajuste no debía elegir acciones: «$siguiente»", siguiente == "Probar")
		// Con el control Acciones, arriba y abajo dicen las acciones (arriba, la última; abajo después, la primera)
		assertNotNull(irA("Al activar"))
		s.lectorPruebas!!.control = ControlLectura.ACCIONES
		val ultima = hacer(Accion.SELECTOR_ANTERIOR)
		assertTrue("Arriba no dijo ninguna acción: «$ultima»", ultima.isNotBlank() && !ultima.startsWith("Al activar"))
		assertEquals("Probar", hacer(Accion.SELECTOR_SIGUIENTE))
		val apagadoAntes = sonidoActivarApagado()
		s.ejecutarPrueba(Accion.ACTIVAR)
		SystemClock.sleep(1200)
		assertEquals("Con Acciones, el doble toque cambió la casilla", apagadoAntes, sonidoActivarApagado())
	}

	@Test
	fun losEnlacesDeDentroDeUnTextoSeEligenYSePulsan() {
		// Como el aviso de Play Protect: dos enlaces en medio de una frase, que no son elementos aparte
		abrirMuestrario(".ActividadAviso")
		val frase = irA("Esta app puede ser peligrosa")
		assertNotNull("No se encontró la frase del aviso", frase)
		assertTrue("No dice que tiene enlaces: «$frase»", frase!!.contains("con 2 enlaces"))
		assertEquals("Más información, enlace", hacer(Accion.SELECTOR_SIGUIENTE))
		assertEquals("Instalar de todas formas, enlace", hacer(Accion.SELECTOR_SIGUIENTE))
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull("El doble toque no pulsó el enlace elegido", dichoQue(6000, m) { it.contains("Elegido: Instalar de todas formas") })
	}

	@Test
	fun elControlEnlacesVaDeEnlaceEnEnlaceDentroDelTexto() {
		abrirMuestrario(".ActividadAviso")
		assertNotNull(irA("Esta app puede ser peligrosa"))
		s.lectorPruebas!!.control = ControlLectura.ENLACES
		assertEquals("Más información, enlace", hacer(Accion.SELECTOR_SIGUIENTE))
		assertEquals("Instalar de todas formas, enlace", hacer(Accion.SELECTOR_SIGUIENTE))
		assertEquals("Más información, enlace", hacer(Accion.SELECTOR_ANTERIOR))
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull("El doble toque no pulsó el enlace dicho", dichoQue(6000, m) { it.contains("Elegido: Más información") })
	}

	@Test
	fun lasFaltasDeUnCampoSeDicenYSeCorrigen() {
		abrirMuestrario(".ActividadAviso")
		val campo = irA("Mensaje, Hola")
		assertNotNull("No se encontró el campo", campo)
		assertTrue("No dice la falta: «$campo»", campo!!.contains("1 falta de ortografía"))
		assertEquals("Cambiar «mundp» por «mundo»", hacer(Accion.SELECTOR_SIGUIENTE))
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull("No corrigió", dichoQue(5000, m) { it.startsWith("Corregido: mundo") })
		SystemClock.sleep(500)
		val texto = s.lectorPruebas!!.foco?.also { it.refresh() }?.text?.toString()
		assertEquals("Hola mundo", texto)
	}

	@Test
	fun laPantallaDeAjustesDeUnComplemento() {
		val codigo = InstrumentationRegistry.getInstrumentation().context.assets.open("codigos-de-verificacion/complemento.js").bufferedReader().use { it.readText() }
		val scripts = com.xebolax.tiresias.scripts.Scripts(null, app)
		scripts.instalar(codigo)
		scripts.restablecerAjustes("codigos-de-verificacion")
		abrir(com.xebolax.tiresias.ui.ActividadAjustesComplemento::class.java) { it.putExtra(com.xebolax.tiresias.ui.ActividadAjustesComplemento.EXTRA_ID, "codigos-de-verificacion") }
		// Un interruptor de una sola parada, con su explicación y su estado
		val copiar = irA("Copiar el código al portapapeles")
		assertNotNull(copiar)
		assertTrue("El interruptor no dice su estado: «$copiar»", Regex("interruptor, activado").containsMatchIn(copiar!!))
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		assertNotNull(dichoQue(4000, m) { it.endsWith("desactivado") })
		SystemClock.sleep(500)
		val info = scripts.instalados().first { it.id == "codigos-de-verificacion" }
		assertEquals(false, scripts.valorAjuste(info.id, info.ajustes.first { it.clave == "copiar" }))
		// Una lista: el botón dice el valor de ahora
		val vaciar = irA("Borrar el código del portapapeles")
		assertTrue("El botón no dice el valor: «$vaciar»", vaciar!!.startsWith("Borrar el código del portapapeles: A los 2 minutos"))
		scripts.restablecerAjustes(info.id)
	}

	/** Espera a que la velocidad de ahora sea [v] (el cambio de motor se hace en el hilo del lector). */
	private fun velocidadSera(v: Int): Int = esperar(3000) { com.xebolax.tiresias.base.Ajustes(app).velocidad.takeIf { it == v } } ?: com.xebolax.tiresias.base.Ajustes(app).velocidad

	@Test
	fun cadaMotorTieneSuVelocidad() {
		val a = com.xebolax.tiresias.base.Ajustes(app)
		val espeak = com.xebolax.tiresias.base.Ajustes.MOTOR_ESPEAK
		val pico = com.xebolax.tiresias.base.Ajustes.MOTOR_PICO
		prefs.edit().remove(Claves.VOZ_DE + espeak).remove(Claves.VOZ_DE + pico).commit()
		ajuste(Claves.MOTOR, espeak)
		ajuste(Claves.VELOCIDAD, 350)
		// Un motor nuevo no hereda la velocidad del otro: la primera vez, la normal
		ajuste(Claves.MOTOR, pico)
		assertEquals("La primera vez de un motor, velocidad normal", 100, velocidadSera(100))
		ajuste(Claves.VELOCIDAD, 150)
		// Cada uno vuelve con la suya
		ajuste(Claves.MOTOR, espeak)
		assertEquals(350, velocidadSera(350))
		ajuste(Claves.MOTOR, pico)
		assertEquals(150, velocidadSera(150))
		assertEquals(350, a.velocidadDe(espeak))
		// Al cambiar de voz con el gesto, se dice con qué velocidad queda
		val dicho = hacer(Accion.VOZ_SIGUIENTE, 1000)
		assertTrue("«$dicho»", dicho.startsWith("Voz: ") && dicho.contains("velocidad ${a.velocidad} por ciento"))
	}

	@Test
	fun elControlVozPasaSoloPorLasElegidasYCadaUnaConSuVozDelSistema() {
		val a = com.xebolax.tiresias.base.Ajustes(app)
		val espeak = com.xebolax.tiresias.base.Ajustes.MOTOR_ESPEAK
		val pico = com.xebolax.tiresias.base.Ajustes.MOTOR_PICO
		ajuste(Claves.MOTOR, espeak)
		ajuste(Claves.VOZ_SISTEMA, "voz-de-prueba")
		prefs.edit().putStringSet(Claves.VOCES_CONTROL, setOf(espeak, pico)).commit()
		val primero = hacer(Accion.VOZ_SIGUIENTE, 1000)
		assertTrue("«$primero»", primero.startsWith("Voz: ") && primero.contains("Pico"))
		assertEquals(pico, a.motor)
		// La voz del sistema elegida era de eSpeak, no de Pico
		assertEquals("", a.vozSistema)
		val segundo = hacer(Accion.VOZ_SIGUIENTE, 1000)
		assertTrue("«$segundo»", segundo.startsWith("Voz: ") && segundo.contains("eSpeak"))
		assertEquals(espeak, a.motor)
		assertEquals("voz-de-prueba", a.vozSistema)
	}

	@Test
	fun ajustesDiceLaVelocidadQueHayDeVerdad() {
		abrir(ActividadAjustes::class.java) { it.putExtra(ActividadAjustes.EXTRA_SECCION, "seccion_voz") }
		SystemClock.sleep(1200)
		// Como si se cambiara con un gesto con la pantalla abierta
		ajuste(Claves.VELOCIDAD, 170)
		SystemClock.sleep(1000)
		val dicho = irAQueContenga("Velocidad de la voz")
		assertTrue("El deslizador no dice la velocidad de ahora: «$dicho»", dicho!!.contains("170 por ciento"))
	}

	@Test
	fun elDeslizadorDiceSuValorUnaVez() {
		abrir(ActividadAjustes::class.java) { it.putExtra(ActividadAjustes.EXTRA_SECCION, "seccion_voz") }
		assertNotNull("No se encontró el tono", irAQueContenga("Tono"))
		SystemClock.sleep(1500)
		for (abajo in listOf(true, false)) {
			val m = marca()
			s.ejecutarPrueba(if (abajo) Accion.SELECTOR_SIGUIENTE else Accion.SELECTOR_ANTERIOR)
			SystemClock.sleep(2500)
			val valores = s.vozPruebas!!.dichoDesde(m).filter { it.contains("por ciento") }
			assertEquals("El valor no se dijo una sola vez: $valores", 1, valores.size)
		}
	}

	@Test
	fun laVueltaVaAlPrincipioDeVerdadYAtrasAlFinal() {
		abrirMuestrario()
		ajuste(Claves.DAR_VUELTA, true)
		val primero = hacer(Accion.PRIMERO).substringBefore(",")
		assertNotNull(irA("Mensaje 1,"))
		var m = marca()
		s.ejecutarPrueba(Accion.LISTA_FINAL)
		assertNotNull(dichoQue(15_000, m) { it.startsWith("Mensaje 40") })
		SystemClock.sleep(800)
		// Tras el último: al primero de la pantalla, con las listas otra vez arriba (no al primero que se ve)
		m = marca()
		s.ejecutarPrueba(Accion.SIGUIENTE)
		assertNotNull("No dio la vuelta al principio («$primero»)", dichoQue(10_000, m) { it.startsWith(primero) })
		SystemClock.sleep(800)
		// Hacia atrás desde ahí: al último de todo (no a la mitad de una lista)
		m = marca()
		s.ejecutarPrueba(Accion.ANTERIOR)
		assertNotNull("Hacia atrás desde el principio no dio la vuelta al final", dichoQue(10_000, m) { it.startsWith("Mensaje 40") })
	}
}
