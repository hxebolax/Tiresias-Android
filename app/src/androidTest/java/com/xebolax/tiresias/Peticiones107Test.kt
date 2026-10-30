package com.xebolax.tiresias

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.braille.AccionTeclado
import com.xebolax.tiresias.braille.CalibracionBraille
import com.xebolax.tiresias.braille.DireccionBraille
import com.xebolax.tiresias.braille.GestoBraille
import com.xebolax.tiresias.braille.GestosTeclado
import com.xebolax.tiresias.braille.Menciones
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.remoto.ClaveIncorrecta
import com.xebolax.tiresias.nucleo.remoto.CodigoCorto
import com.xebolax.tiresias.nucleo.remoto.Emparejar
import com.xebolax.tiresias.nucleo.remoto.Enlace
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import com.xebolax.tiresias.nucleo.remoto.MensajeAyuda
import com.xebolax.tiresias.nucleo.remoto.Pareja
import com.xebolax.tiresias.nucleo.remoto.Socks5
import com.xebolax.tiresias.remoto.Asistencia
import com.xebolax.tiresias.remoto.Confianza
import com.xebolax.tiresias.remoto.Tor
import com.xebolax.tiresias.voz.VozEnLlamadas
import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lo de la 1.0.7: la ayuda remota directa entre dos móviles (aquí el otro
 * móvil es la prueba, que se conecta por la red de verdad del emulador), los
 * gestos y la calibración del teclado braille, las superficies braille y la
 * voz en llamadas.
 */
@RunWith(AndroidJUnit4::class)
class Peticiones107Test : PruebaTiresias() {

	@After
	fun terminarAyuda() {
		Asistencia.terminarPorUsuario()
		esperar(3000) { if (Asistencia.ayudado == null) true else null }
		s.lectorPruebas?.superficie?.cerrar()
		for (p in Confianza.personas(s)) Confianza.quitarPersona(s, p.id)
	}

	/** Una persona de confianza que entra sin que se le pregunte a nadie (solo se avisa y se esperan unos segundos). */
	private fun confiar(): Pareja {
		val p = Emparejar.nueva("Héctor", System.currentTimeMillis() / 1000)
		Confianza.anadirPersona(s, p)
		ajuste(Claves.AYUDA_PREGUNTAR, false)
		return p
	}

	private fun saludar(e: Enlace, p: Pareja?) {
		e.enviarTexto(MensajeAyuda.Hola("Héctor", "prueba", "es", true, listOfNotNull(p?.let { Emparejar.prueba(it, e.comprobacion) })).json())
	}

	private fun esperarInvitacion(tor: Boolean = false): Invitacion {
		ajuste(Claves.AYUDA_ROUTER, false)
		ajuste(Claves.AYUDA_TOR, tor)
		ajuste(Claves.AYUDA_NOMBRE, "Mamá")
		val fin = CountDownLatch(1)
		var inv: Invitacion? = null
		Asistencia.empezarAyudado(s) { i ->
			inv = i
			fin.countDown()
		}
		assertTrue("No se preparó la invitación", fin.await(if (tor) 60 else 15, TimeUnit.SECONDS))
		return inv ?: throw AssertionError("Sin invitación: ¿no hay red en el emulador?")
	}

	private fun conectar(inv: Invitacion, secreto: ByteArray): Pair<Socket, Enlace> {
		val c = inv.candidatos.first()
		val sock = Socket()
		sock.connect(InetSocketAddress(c.direccion(), c.puerto), 5000)
		sock.soTimeout = 20_000
		return sock to Enlace.cliente(sock.getInputStream(), sock.getOutputStream(), secreto)
	}

	/** Lee mensajes hasta uno que cumpla [condicion] (como mucho 20). */
	private fun recibirHasta(e: Enlace, condicion: (MensajeAyuda) -> Boolean): MensajeAyuda {
		repeat(40) {
			val m = MensajeAyuda.leer(e.recibirTexto()) ?: return@repeat
			if (condicion(m)) return m
		}
		throw AssertionError("No llegó el mensaje esperado")
	}

	/** El ayudante entra con la invitación, oye lo que dice el móvil, pide sus elementos y lo maneja; lo prohibido no se hace. */
	@Test
	fun laAyudaRemotaSeEnlazaDirectamenteYManejaElMovil() {
		val persona = confiar()
		val inv = esperarInvitacion()
		assertEquals("Mamá", inv.nombre)
		assertTrue("Sin direcciones en la invitación", inv.candidatos.isNotEmpty())
		// El código del mensaje se lee entero dentro del texto que se manda
		val leida = Invitacion.leer(com.xebolax.tiresias.remoto.PeticionAyuda.texto(app, inv))
		assertNotNull("El mensaje no lleva una invitación válida", leida)
		val (sock, e) = conectar(inv, inv.secreto)
		try {
			saludar(e, persona)
			val hola = recibirHasta(e) { it is MensajeAyuda.Hola } as MensajeAyuda.Hola
			assertEquals("Mamá", hola.nombre)
			assertFalse(hola.ayudante)
			// Quien ayuda ve cómo está el móvil
			val ficha = recibirHasta(e) { it is MensajeAyuda.Ficha } as MensajeAyuda.Ficha
			assertTrue("La ficha viene vacía", ficha.lineas.isNotEmpty())
			// La persona ayudada oye quién la ayuda, y lo que dice su móvil llega al ayudante
			val aviso = recibirHasta(e) { it is MensajeAyuda.Voz && it.texto.contains("Héctor") }
			assertNotNull(aviso)
			assertTrue("El móvil no dice que lo ayudan", dichoQue(4000) { it.contains("Héctor te está ayudando") } != null)
			assertEquals(true, Asistencia.ayudado?.conectada)
			// Quien ayuda ve lo que le pide decir, dicho allí
			e.enviarTexto(MensajeAyuda.Decir("Hola, mamá").json())
			assertTrue("No dijo lo que manda quien ayuda", dichoQue(4000) { it.contains("te dice: Hola, mamá") } != null)
			// La lista de elementos de su pantalla
			e.enviarTexto(MensajeAyuda.PedirElementos(7).json())
			val elementos = recibirHasta(e) { it is MensajeAyuda.Elementos } as MensajeAyuda.Elementos
			assertEquals(7L, elementos.peticion)
			// Una acción de Tiresias: se hace y se oye en los dos
			e.enviarTexto(MensajeAyuda.HacerAccion(Accion.ESTADO_SISTEMA.name).json())
			assertNotNull(recibirHasta(e) { it is MensajeAyuda.Voz })
			// Lo que dejaría a la persona sin lector no se hace
			e.enviarTexto(MensajeAyuda.HacerAccion(Accion.SUSPENDER.name).json())
			val no = recibirHasta(e) { it is MensajeAyuda.Respuesta } as MensajeAyuda.Respuesta
			assertFalse("Se dejó suspender Tiresias desde fuera", no.ok)
			// Terminar desde el ayudante
			e.enviarTexto(MensajeAyuda.Fin("usuario").json())
			assertNotNull("La ayuda no terminó", esperar(5000) { if (Asistencia.ayudado == null) true else null })
		} finally {
			runCatching { sock.close() }
		}
	}

	/** Con la red Tor de verdad: el ayudante entra por el onion, como dos móviles con datos móviles y sin puertos abiertos. */
	@Test
	fun porTorSeEntraSinVerseDirectamente() {
		val inicio = android.os.SystemClock.uptimeMillis()
		val persona = confiar()
		val inv = esperarInvitacion(tor = true)
		val onion = inv.onion ?: throw AssertionError("La invitación no lleva dirección de Tor")
		Log.i("Peticiones107", "Invitación con onion en ${android.os.SystemClock.uptimeMillis() - inicio} ms")
		assertEquals(inv, Invitacion.leer(inv.codigo()))
		assertTrue(Tor.encender(s))
		var sock: Socket? = null
		val hasta = android.os.SystemClock.uptimeMillis() + 300_000
		var intentos = 0
		while (sock == null && android.os.SystemClock.uptimeMillis() < hasta) {
			intentos++
			val socks = Tor.puertoSocks()
			if (socks != null) sock = runCatching { Socks5.conectar(socks, onion.host, onion.puerto, 60_000) }.onFailure { Log.i("Peticiones107", "Intento $intentos: ${it.message}") }.getOrNull()
			if (sock == null) Thread.sleep(3000)
		}
		val conexion = sock ?: throw AssertionError("Tor no ha llegado al servicio onion en cinco minutos")
		Log.i("Peticiones107", "Conectado por Tor en ${android.os.SystemClock.uptimeMillis() - inicio} ms, $intentos intentos")
		try {
			conexion.soTimeout = 60_000
			val e = Enlace.cliente(conexion.getInputStream(), conexion.getOutputStream(), inv.secreto)
			saludar(e, persona)
			assertTrue(recibirHasta(e) { it is MensajeAyuda.Hola } is MensajeAyuda.Hola)
			val vueltas = ArrayList<Long>()
			repeat(10) {
				val t = android.os.SystemClock.uptimeMillis()
				val peticion = 900L + it
				e.enviarTexto(MensajeAyuda.PedirApps(peticion).json())
				recibirHasta(e) { m -> m is MensajeAyuda.Apps && m.peticion == peticion }
				vueltas += android.os.SystemClock.uptimeMillis() - t
			}
			Log.i("Peticiones107", "Ida y vuelta por Tor, ms: ${vueltas.sorted()}")
		} finally {
			runCatching { conexion.close() }
			Tor.soltar(s)
		}
	}

	/** Con alguien en la lista, una invitación no basta: es lo que para al falso soporte técnico. */
	@Test
	fun conLaInvitacionNoEntraQuienNoEstaEnLaLista() {
		confiar()
		val inv = esperarInvitacion()
		val (sock, e) = conectar(inv, inv.secreto)
		try {
			saludar(e, null)
			val fin = recibirHasta(e) { it is MensajeAyuda.Fin } as MensajeAyuda.Fin
			assertEquals(com.xebolax.tiresias.remoto.SesionAyudado.FIN_RECHAZADA, fin.motivo)
			assertTrue("No avisó a la persona", dichoQue(4000) { it.contains("no está en tu lista") } != null)
			assertFalse("Entró sin estar en la lista", Asistencia.ayudado?.conectada == true)
		} finally {
			runCatching { sock.close() }
		}
	}

	/** En una app de banca, ni se oye ni se obedece; los códigos no salen nunca; los enlaces que instalan algo, tampoco. */
	@Test
	fun laBancaYLosCodigosNoPasanPorLaAyuda() {
		val persona = confiar()
		val inv = esperarInvitacion()
		val (sock, e) = conectar(inv, inv.secreto)
		try {
			saludar(e, persona)
			recibirHasta(e) { it is MensajeAyuda.Hola }
			recibirHasta(e) { it is MensajeAyuda.Ficha }
			val ayudado = Asistencia.ayudado ?: throw AssertionError("Sin sesión")
			// Los códigos llegan tapados
			s.lectorPruebas?.decir("Tu código de WhatsApp es 123 456")
			val voz = recibirHasta(e) { it is MensajeAyuda.Voz && it.texto.contains("WhatsApp") } as MensajeAyuda.Voz
			assertFalse("El código ha salido del móvil: ${voz.texto}", voz.texto.contains("123"))
			// Un enlace a un APK no se abre
			e.enviarTexto(MensajeAyuda.AbrirEnlace(5, "https://malo.example/banco.apk").json())
			assertFalse((recibirHasta(e) { it is MensajeAyuda.Respuesta && it.peticion == 5L } as MensajeAyuda.Respuesta).ok)
			// Abre el banco: la ayuda se queda en pausa
			s.lectorPruebas?.manejador?.post { ayudado.alPaquete("es.bancosantander.apps", null) }
			val pausa = recibirHasta(e) { it is MensajeAyuda.Estado && it.clave == com.xebolax.tiresias.remoto.SesionAyudado.ESTADO_PROTEGIDA } as MensajeAyuda.Estado
			assertTrue(pausa.texto.orEmpty().contains("banco"))
			s.lectorPruebas?.decir("Saldo mil euros")
			e.enviarTexto(MensajeAyuda.PedirElementos(8).json())
			val no = recibirHasta(e) { it is MensajeAyuda.Respuesta } as MensajeAyuda.Respuesta
			assertFalse("Obedeció dentro del banco", no.ok)
			// Sale del banco: sigue, y lo que dijo dentro no ha llegado
			s.lectorPruebas?.manejador?.post { ayudado.alPaquete("com.android.settings", "Ajustes") }
			val fin = recibirHasta(e) { (it is MensajeAyuda.Voz && it.texto.contains("Saldo")) || (it is MensajeAyuda.Estado && it.clave == com.xebolax.tiresias.remoto.SesionAyudado.ESTADO_PROTEGIDA) }
			assertTrue("Lo dicho dentro del banco ha salido", fin is MensajeAyuda.Estado)
		} finally {
			runCatching { sock.close() }
		}
	}

	/** Con el código corto de la misma wifi también se entra; con una clave equivocada, no. */
	@Test
	fun conElCodigoCortoSeEntraYConOtraClaveNo() {
		val persona = confiar()
		val inv = esperarInvitacion()
		val corto = Asistencia.ayudado!!.codigoCorto
		assertEquals(8, corto.length)
		val otra = CodigoCorto.secreto("00000000".takeIf { it != corto } ?: "11111111")
		val (malo, _) = try {
			conectar(inv, otra)
		} catch (x: ClaveIncorrecta) {
			null to null
		} catch (x: java.io.IOException) {
			null to null
		}
		assertNull("Se entró con una clave equivocada", malo)
		val (sock, e) = conectar(inv, CodigoCorto.secreto(corto))
		try {
			saludar(e, persona)
			assertNotNull(recibirHasta(e) { it is MensajeAyuda.Hola })
			assertNotNull("No se enlazó con el código corto", esperar(4000) { if (Asistencia.ayudado?.conectada == true) true else null })
		} finally {
			runCatching { sock.close() }
		}
	}

	/** Las dos teclas de volumen terminan la ayuda antes de suspender Tiresias. */
	@Test
	fun lasDosTeclasDeVolumenTerminanLaAyuda() {
		esperarInvitacion()
		assertTrue(Asistencia.ayudado?.activa == true)
		s.ejecutarPrueba(Accion.SUSPENDER)
		assertNotNull("La ayuda no terminó", esperar(4000) { if (Asistencia.ayudado == null) true else null })
		// Y Tiresias sigue hablando: no se ha suspendido
		assertTrue(hacer(Accion.ESTADO_SISTEMA, 1500).isNotEmpty())
	}

	/** La navegación web braille se pone encima y las dos teclas de volumen la quitan sin suspender Tiresias. */
	@Test
	fun laSuperficieBrailleSePoneYSeQuita() {
		s.ejecutarPrueba(Accion.NAVEGACION_BRAILLE)
		val l = s.lectorPruebas!!
		assertNotNull("No se puso la superficie", esperar(3000) { if (l.superficie.abierta) true else null })
		assertTrue(l.gestos.superficie)
		s.ejecutarPrueba(Accion.SUSPENDER)
		assertNotNull("No se quitó la superficie", esperar(3000) { if (!l.superficie.abierta) true else null })
		assertNotNull(esperar(3000) { if (!l.gestos.superficie) true else null })
		assertTrue(hacer(Accion.ESTADO_SISTEMA, 1500).isNotEmpty())
	}

	/** Los gestos del teclado braille: los de un punto, si no tienen nada, hacen lo del gesto de un dedo. */
	@Test
	fun losGestosDeUnPuntoCaenEnLosDeUnDedo() {
		val fabrica = GestosTeclado.leer("")
		assertEquals(AccionTeclado.BORRAR, GestosTeclado.accion(fabrica, GestoBraille(1, DireccionBraille.IZQUIERDA, 3)))
		assertEquals(AccionTeclado.ESPACIO, GestosTeclado.accion(fabrica, GestoBraille(1, DireccionBraille.DERECHA, 5)))
		assertEquals(AccionTeclado.INTRO, GestosTeclado.accion(fabrica, GestoBraille(2, DireccionBraille.ABAJO)))
		val propio = GestosTeclado.leer("p3_IZQUIERDA=BORRAR_PALABRA;1_ARRIBA=DICTADO;nada=OTRA")
		assertEquals(AccionTeclado.BORRAR_PALABRA, GestosTeclado.accion(propio, GestoBraille(1, DireccionBraille.IZQUIERDA, 3)))
		assertEquals(AccionTeclado.BORRAR, GestosTeclado.accion(propio, GestoBraille(1, DireccionBraille.IZQUIERDA, 2)))
		assertEquals(AccionTeclado.DICTADO, GestosTeclado.accion(propio, GestoBraille(1, DireccionBraille.ARRIBA, 1)))
		// Solo se guarda lo que cambia
		assertEquals(propio, GestosTeclado.leer(GestosTeclado.escribir(propio)))
		assertEquals("", GestosTeclado.escribir(fabrica))
		assertEquals(36, GestosTeclado.TODOS.size)
		assertEquals("Desde el punto 3 hacia la izquierda", GestosTeclado.nombre(s.lectorPruebas!!.textos, GestoBraille(1, DireccionBraille.IZQUIERDA, 3)))
	}

	/** La calibración se guarda aparte para los puntos normales y en espejo, y se quita. */
	@Test
	fun laCalibracionSeGuardaYSeQuita() {
		val a = Ajustes(app)
		val puntos = listOf(PointF(0.1f, 0.2f), PointF(0.3f, 0.2f), PointF(0.5f, 0.25f), PointF(0.1f, 0.8f), PointF(0.3f, 0.8f), PointF(0.5f, 0.75f))
		CalibracionBraille.guardar(a, false, puntos)
		val leidos = CalibracionBraille.leer(a, false)!!
		assertEquals(6, leidos.size)
		assertEquals(0.5f, leidos[2].x, 0.001f)
		assertNull(CalibracionBraille.leer(a, true))
		assertTrue(CalibracionBraille.hay(a))
		CalibracionBraille.quitar(a)
		assertFalse(CalibracionBraille.hay(a))
	}

	/** Fuera de una llamada, la voz en llamadas no cambia nada aunque esté puesta. */
	@Test
	fun fueraDeUnaLlamadaLaVozNoCambia() {
		ajuste(Claves.VOZ_LLAMADAS, true)
		ajuste(Claves.VOLUMEN_LLAMADAS, 30)
		assertEquals(1f, VozEnLlamadas.calcular(app, Ajustes(app)), 0.0001f)
		assertEquals(35, VozEnLlamadas.cambiar(Ajustes(app), true))
	}

	/** Las sugerencias de mención solo con una «@» al principio o tras un espacio. */
	@Test
	fun lasMencionesSeReconocen() {
		assertTrue(Menciones.hayConsulta("@"))
		assertTrue(Menciones.hayConsulta("hola @An"))
		assertFalse(Menciones.hayConsulta("correo@dominio"))
		assertFalse(Menciones.hayConsulta("hola @Ana "))
	}

	/** Las acciones nuevas tienen nombre y la de pedir ayuda está en los menús. */
	@Test
	fun lasAccionesNuevasTienenNombreYEstanEnLosMenus() {
		for (a in listOf(Accion.NAVEGACION_BRAILLE, Accion.BUSQUEDA_BRAILLE, Accion.TRADUCIR_ULTIMO, Accion.CONTROL_PC, Accion.PEDIR_AYUDA, Accion.AYUDA_REMOTA, Accion.AYUDA_TERMINAR, Accion.AYUDA_CONTROLAR)) {
			assertTrue("Sin nombre: $a", app.resources.getIdentifier("accion_" + a.name.lowercase(), "string", app.packageName) != 0)
		}
		assertTrue(Accion.PEDIR_AYUDA in com.xebolax.tiresias.nucleo.Listas.MENU_TIRESIAS)
		assertTrue(Accion.PEDIR_AYUDA in com.xebolax.tiresias.nucleo.Listas.MENU_SENIOR)
	}
}
