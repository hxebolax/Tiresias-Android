package com.xebolax.tiresias

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.ui.ActividadTutorial
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que contaron los usuarios de la 1.0.7: la práctica de los gestos de la que no
 * se podía salir, los cortes al recorrer los chats de Telegram y la pantalla de
 * inicio de Huawei que se leía como un solo elemento.
 */
@RunWith(AndroidJUnit4::class)
class Peticiones108Test : PruebaTiresias() {
	private fun abrir(clase: String, extras: Intent.() -> Unit = {}) {
		app.startActivity(Intent().setClassName("com.xebolax.tiresias.muestrario", "com.xebolax.tiresias.muestrario.$clase").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).apply(extras))
		SystemClock.sleep(1500)
	}

	private fun nodo(nombre: String) = AccessibilityNodeInfo().apply {
		isClickable = true
		contentDescription = nombre
	}

	/** El nombre entero del botón de grabar cuenta; una fila que habla de un mensaje de voz, no. */
	@Test
	fun unaFilaConMensajeDeVozNoEsElBotonDeGrabar() {
		for (nombre in listOf("Mensaje de voz", "Voice message", "Grabar mensaje de voz", "Record voice message", "Enviar nota de voz", "Mantén pulsado para grabar", "Hold to record", "Push to talk")) {
			assertTrue("«$nombre» es un botón de grabar", Nodos.esDeMantenerPulsado(nodo(nombre)))
		}
		for (nombre in listOf("Ana, Mensaje de voz", "Ana. Mensaje de voz. 10:30", "Reproducir mensaje de voz", "Voice message from Ana", "Ana: Nota de voz (0:12)")) {
			assertFalse("«$nombre» no es un botón de grabar", Nodos.esDeMantenerPulsado(nodo(nombre)))
		}
		// Una fila de una lista nunca lo es, aunque se llame igual
		val fila = nodo("Mensaje de voz").apply {
			collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo(0, 1, 0, 1, false)
		}
		assertFalse(Nodos.esDeMantenerPulsado(fila))
	}

	/** Recorrer una lista como la de Telegram, también por filas que ya tienen nombre, no toca la configuración del servicio. */
	@Test
	fun recorrerLosChatsNoRehaceLaExploracionTactil() {
		if (android.os.Build.VERSION.SDK_INT < 30) return
		abrir("ActividadChats")
		val maneja = android.accessibilityservice.AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP
		repeat(5) { hacer(Accion.SIGUIENTE, 200) }
		repeat(5) {
			hacer(Accion.ANTERIOR, 200)
			assertTrue("Una fila de un chat ha cambiado quién hace el doble toque", s.serviceInfo.flags and maneja != 0)
		}
	}

	/** Cada icono de un escritorio como el de EMUI es una parada: ni la página ni el dock se leen de una vez. */
	@Test
	fun elEscritorioPulsableSeRecorreIconoAIcono() {
		abrir("ActividadEscritorio")
		val dichos = arrayListOf(hacer(Accion.PRIMERO, 150))
		repeat(9) { dichos += hacer(Accion.SIGUIENTE, 150) }
		val todo = dichos.joinToString(" | ")
		for (app in listOf("Telegram", "Correo", "Fotos", "Mapas", "Teléfono", "Mensajes", "Navegador")) {
			assertTrue("«$app» no es una parada propia: $todo", dichos.any { it.startsWith(app) })
		}
		assertFalse("Se ha leído junto: $todo", dichos.any { "Telegram" in it && "Correo" in it })
		assertFalse("Se ha leído junto: $todo", dichos.any { "Teléfono" in it && "Mensajes" in it })
	}

	/** Y el icono se abre con el doble toque, que era lo que no se podía hacer. */
	@Test
	fun unIconoDelEscritorioSeAbreConElDobleToque() {
		abrir("ActividadEscritorio")
		hacer(Accion.PRIMERO, 150)
		repeat(9) {
			if (hacer(Accion.SIGUIENTE, 150).startsWith("Fotos")) {
				s.ejecutarPrueba(Accion.ACTIVAR)
				assertNotNull("No se abrió la app del icono", dichoQue { "Abierta Fotos" in it })
				return
			}
		}
		error("No se llegó al icono de Fotos")
	}

	/** Con un texto suelto en la página (un reloj), la página es una parada con ese texto y los iconos siguen siendo paradas. */
	@Test
	fun unaPaginaConUnTextoSueltoNoSeTragaLosIconos() {
		abrir("ActividadEscritorio") { putExtra("reloj", true) }
		val dichos = arrayListOf(hacer(Accion.PRIMERO, 150))
		repeat(10) { dichos += hacer(Accion.SIGUIENTE, 150) }
		val todo = dichos.joinToString(" | ")
		assertTrue("El reloj no se ha dicho: $todo", dichos.any { "10:30" in it })
		assertFalse("El reloj se ha dicho con los iconos: $todo", dichos.any { "10:30" in it && "Telegram" in it })
		assertTrue("Los iconos no son paradas: $todo", dichos.any { it.startsWith("Telegram") } && dichos.any { it.startsWith("Mapas") })
	}

	private fun abrirTutorial() {
		app.startActivity(Intent(app, ActividadTutorial::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
		SystemClock.sleep(1500)
	}

	/** El primer toque de un doble toque en la parte de abajo deja el foco en la barra del sistema: ahí también pulsa de verdad. */
	@Test
	fun enLaPracticaElDobleToqueNoSeQuedaEnLaBarraDelSistema() {
		abrirTutorial()
		val l = s.lectorPruebas!!
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		assertNotNull(esperar(2000) { true.takeIf { l.ayudaEntrada } })
		val barra = s.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }?.root
		assertNotNull("No hay ventana del sistema en el emulador", barra)
		val desde = marca()
		l.manejador.post { l.enfocar(barra!!, null) }
		assertNotNull(esperar(2000) { l.foco?.takeIf { l.esDelSistema(it.packageName?.toString()) } })
		s.gestoPrueba(Gesto.DOBLE_TOQUE)
		SystemClock.sleep(800)
		assertNull("Se dijo el gesto en vez de pulsar", s.vozPruebas!!.dichoDesde(desde).firstOrNull { it.startsWith("Doble toque:") })
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
	}

	/** Sin foco no hay nada que pulsar: el doble toque termina la práctica en vez de dejar a nadie dentro. */
	@Test
	fun enLaPracticaSinFocoElDobleToqueTerminaLaPractica() {
		abrirTutorial()
		val l = s.lectorPruebas!!
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		assertNotNull(esperar(2000) { true.takeIf { l.ayudaEntrada } })
		l.perderFocoPruebas()
		assertNotNull(esperar(2000) { true.takeIf { l.foco == null } })
		s.gestoPrueba(Gesto.DOBLE_TOQUE)
		// Pulsa lo que tenga el foco de Android, si queda algo; si no, termina: se comprueba que no se dice el gesto
		val desde = marca()
		SystemClock.sleep(800)
		assertNull(s.vozPruebas!!.dichoDesde(desde).firstOrNull { it.startsWith("Doble toque:") })
		if (l.ayudaEntrada) s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
	}

	/** El informe que se comparte no lleva direcciones de red, y lo demás se queda como está. */
	@Test
	fun elInformeNoLlevaDireccionesDeRed() {
		fun l(t: String) = com.xebolax.tiresias.base.Registro.limpiar(t)
		for (t in listOf(
			"java.net.ConnectException: failed to connect to casa.ejemplo.es/192.168.1.20 (port 6837)",
			"java.net.UnknownHostException: Unable to resolve host \"casa.ejemplo.es\": No address associated",
			"java.net.UnknownHostException: casa.ejemplo.es",
			"javax.net.ssl.SSLPeerUnverifiedException: Hostname casa.ejemplo.es not verified",
			"fallo con 2a02:9130:1:2:3:4:5:6",
			"fallo con abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuvwx.onion",
		)) {
			val limpio = l(t)
			assertFalse("Queda una dirección: $limpio", "casa.ejemplo" in limpio || "192.168" in limpio || "2a02" in limpio || ".onion" in limpio)
		}
		val normal = "2026-10-31 09:58:27.237 E Vigilante: El hilo lector lleva 26108 ms sin responder\n  at com.xebolax.tiresias.lectura.Lector.alEvento(Lector.kt:309)"
		assertTrue(l(normal) == normal)
	}

	/** Cada pocos gestos dichos se recuerda cómo salir. */
	@Test
	fun laPracticaRecuerdaComoSalir() {
		abrirTutorial()
		val l = s.lectorPruebas!!
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		assertNotNull(esperar(2000) { true.takeIf { l.ayudaEntrada } })
		val desde = marca()
		repeat(9) {
			s.gestoPrueba(Gesto.DERECHA)
			SystemClock.sleep(80)
		}
		assertNotNull("No se recordó cómo salir", dichoQue(3000, desde) { it.startsWith("Para salir de la práctica") })
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
	}

	/** Una voz que corta en mitad de una frase no se queda sin la siguiente. */
	@Test
	fun cortarSeguidoNoSeComeLaUltimaFrase() {
		val voz = s.vozPruebas!!
		val desde = marca()
		repeat(30) { i ->
			voz.decir("Frase número $i con un poco de texto para que empiece a sonar")
			SystemClock.sleep(15)
		}
		assertTrue("Lo último pedido no es la última frase", voz.dichoDesde(desde).first().contains("29"))
		assertNotNull("La última frase no llegó a sonar", esperar(3000) { voz.ultimoInicioSonido.takeIf { it > 0 && voz.hablando } })
		assertNull("La voz se ha colgado", esperar(1000) { true.takeIf { voz.atascada(20_000) } })
		voz.callar()
	}
}
