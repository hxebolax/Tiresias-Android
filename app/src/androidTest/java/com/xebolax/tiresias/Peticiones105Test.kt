package com.xebolax.tiresias

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.nucleo.Accion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo de la 1.0.5, sacado del informe «Carencias y fallos de lectores Android»:
 * el volumen de la voz al momento, la posición del cursor, poner un valor, el
 * formato del texto, las marcas, elegir y copiar texto, el nombre propio de una
 * pantalla, la voz a demanda, la voz de las notificaciones y los teclados que
 * hablan solos.
 */
@RunWith(AndroidJUnit4::class)
class Peticiones105Test : PruebaTiresias() {

	private fun nodo(texto: String): AccessibilityNodeInfo? = s.lectorPruebas!!.raiz()?.findAccessibilityNodeInfosByText(texto)?.firstOrNull()

	/** El campo de la ventana de Tiresias que pide un texto (con el teclado abierto). */
	private fun campoDeVentana(): AccessibilityNodeInfo? = esperar(4000) {
		s.windows.mapNotNull { it.root }.mapNotNull { r -> r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.firstOrNull { it.isEditable && it.packageName == app.packageName }
	}

	@Test
	fun elVolumenDeLaVozSeCambiaAlMomento() {
		abrirMuestrario()
		val antes = Ajustes(app).vozMultimedia
		var m = marca()
		s.ejecutarPrueba(Accion.CANAL_VOZ)
		assertNotNull(dichoQue(4000, m) { it.startsWith(if (antes) "Voz por el volumen de accesibilidad" else "Voz por el volumen multimedia") })
		assertEquals(!antes, com.xebolax.tiresias.voz.PistaAudio.multimedia)
		// Y sigue hablando con el otro volumen
		m = marca()
		s.ejecutarPrueba(Accion.CANAL_VOZ)
		assertNotNull(dichoQue(4000, m) { it.startsWith("Voz por el volumen") })
		assertEquals(antes, com.xebolax.tiresias.voz.PistaAudio.multimedia)
	}

	@Test
	fun laPosicionDelCursor() {
		abrirMuestrario()
		val campo = esperar(5000) { s.lectorPruebas!!.raiz()?.let { r -> buscarCampo(r, "Correo") } }!!
		campo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
		campo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "hola mundo") })
		campo.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 4)
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 4)
		})
		SystemClock.sleep(600)
		val dicho = hacer(Accion.POSICION_CURSOR)
		assertEquals("Carácter 5 de 11", dicho)
	}

	private fun buscarCampo(r: AccessibilityNodeInfo, pista: String): AccessibilityNodeInfo? {
		val cola = ArrayDeque<AccessibilityNodeInfo>()
		cola.add(r)
		while (cola.isNotEmpty()) {
			val n = cola.removeFirst()
			if (n.isEditable && !n.isPassword && (n.hintText?.toString() == pista || n.text?.toString() == pista)) return n
			for (i in 0 until n.childCount) n.getChild(i)?.let { cola.add(it) }
		}
		return null
	}

	@Test
	fun ponerUnValorExactoEnUnDeslizador() {
		abrirMuestrario()
		val l = s.lectorPruebas!!
		// El deslizador «Volumen» (no su etiqueta, que va justo antes y se llama igual)
		val barra = esperar(5000) { l.raiz()?.findAccessibilityNodeInfosByText("Volumen")?.firstOrNull { it.rangeInfo != null } }
		assertNotNull("No está el deslizador", barra)
		l.manejador.post { l.enfocar(barra!!) }
		SystemClock.sleep(800)
		s.ejecutarPrueba(Accion.PONER_VALOR)
		val campo = campoDeVentana()
		assertNotNull("No se abrió la ventana del valor", campo)
		campo!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "73") })
		val m = marca()
		if (Build.VERSION.SDK_INT >= 30) campo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
		assertNotNull("No dijo el valor puesto", dichoQue(5000, m) { it == "Puesto a 73" })
		SystemClock.sleep(500)
		barra!!.refresh()
		assertEquals(73f, barra.rangeInfo!!.current, 0.5f)
	}

	@Test
	fun elFormatoDelTexto() {
		val texto = SpannableString("Hola mundo, esto es importante")
		texto.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		texto.setSpan(UnderlineSpan(), 20, 30, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		assertEquals(listOf("Negrita: Hola", "Subrayado: importante"), s.formatoPruebas(texto))
		assertTrue(s.formatoPruebas(SpannableString("Sin formato")).isEmpty())
	}

	@Test
	fun unaMarcaDePosicionLlevaAlSitio() {
		abrirMuestrario()
		assertNotNull(irA("Guardar"))
		var m = marca()
		s.ejecutarPrueba(Accion.PONER_MARCA)
		assertNotNull(dichoQue(4000, m) { it.startsWith("Marca puesta en Guardar") })
		hacer(Accion.PRIMERO)
		m = marca()
		s.ejecutarPrueba(Accion.IR_A_MARCA)
		assertNotNull("No volvió a la marca", dichoQue(4000, m) { it.startsWith("Guardar") })
	}

	@Test
	fun elegirYCopiarTextoQueNoEsUnCampo() {
		abrirMuestrario()
		assertNotNull(irA("Mensaje 1,"))
		s.ejecutarPrueba(Accion.SELECCIONAR_TEXTO)
		val campo = campoDeVentana()
		assertNotNull("No se abrió la ventana para elegir", campo)
		assertTrue(campo!!.text.toString().startsWith("Mensaje 1"))
		// Se elige «Mensaje» y se copia
		campo.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 7)
		})
		SystemClock.sleep(400)
		val copiar = s.windows.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("Copiar lo elegido") }.firstOrNull()
		assertNotNull(copiar)
		val m = marca()
		assertTrue(copiar!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
		assertNotNull(dichoQue(4000, m) { it == "Copiados 7 caracteres" })
		assertEquals("Mensaje", s.lectorPruebas!!.edicion.copiado().first())
	}

	@Test
	fun unNombrePropioParaUnaPantalla() {
		ajuste(Claves.NOMBRES_VENTANA, "{\"com.xebolax.tiresias.muestrario/com.xebolax.tiresias.muestrario.ActividadMuestrario\": \"Mi muestrario\"}")
		val m = marca()
		abrirMuestrario()
		assertNotNull("No dijo el nombre propio", dichoQue(6000, m) { it.startsWith("Mi muestrario") })
	}

	@Test
	fun laVozADemandaSoloHablaCuandoSeLePide() {
		abrirMuestrario()
		hacer(Accion.PRIMERO)
		var m = marca()
		s.ejecutarPrueba(Accion.VOZ_A_DEMANDA)
		assertNotNull(dichoQue(4000, m) { it.startsWith("Voz a demanda") })
		try {
			SystemClock.sleep(3000)
			m = marca()
			s.ejecutarPrueba(Accion.SIGUIENTE)
			SystemClock.sleep(1500)
			assertTrue("Habló al moverse: ${s.vozPruebas!!.dichoDesde(m)}", s.vozPruebas!!.dichoDesde(m).isEmpty())
			m = marca()
			s.ejecutarPrueba(Accion.REPETIR)
			assertNotNull("No dijo nada al pedirlo", dichoQue(3000, m) { it.isNotBlank() })
		} finally {
			s.ejecutarPrueba(Accion.VOZ_A_DEMANDA)
			SystemClock.sleep(500)
		}
	}

	@Test
	fun lasNotificacionesConOtraVoz() {
		ajuste(Claves.MOTOR, Ajustes.MOTOR_ESPEAK)
		ajuste(Claves.MOTOR_AVISOS, Ajustes.MOTOR_PICO)
		if (Build.VERSION.SDK_INT >= 33) inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("pruebas", "Pruebas", NotificationManager.IMPORTANCE_HIGH))
		val voz = s.vozPruebas!!
		val antes = voz.ultimoInicioSonido
		val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(app, "pruebas") else @Suppress("DEPRECATION") Notification.Builder(app))
			.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Aviso con otra voz").setContentText("Hola").build()
		nm.notify(4901, n)
		try {
			assertNotNull(esperar(8000) { voz.ultimoInicioSonido.takeIf { it != antes && voz.ultimoMotor?.contains("Pico") == true } })
		} finally {
			nm.cancel(4901)
		}
	}

	@Test
	fun losTecladosQueHablanSolos() {
		val actual = android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
		assertNotNull(actual)
		val edicion = s.lectorPruebas!!.edicion
		prefs.edit().putStringSet(Claves.TECLADOS_QUE_HABLAN, setOf(actual!!)).commit()
		assertTrue(edicion.tecladoHablaSolo())
		prefs.edit().putStringSet(Claves.TECLADOS_QUE_HABLAN, emptySet()).commit()
		assertTrue(!edicion.tecladoHablaSolo())
	}

	@Test
	fun losDatosSensiblesNoSalen() {
		if (Build.VERSION.SDK_INT < 34) return
		val n = AccessibilityNodeInfo()
		assertTrue(!com.xebolax.tiresias.arbol.Nodos.sensible(n))
		n.setAccessibilityDataSensitive(true)
		assertTrue(com.xebolax.tiresias.arbol.Nodos.sensible(n))
	}

	/** La práctica del tutorial (ayuda de entrada): siempre se puede salir. */
	@Test
	fun deLaPracticaDelTutorialSeSale() {
		val l = s.lectorPruebas!!
		abrir(com.xebolax.tiresias.ui.ActividadTutorial::class.java)
		SystemClock.sleep(1500)
		// Con Atrás (abajo y a la izquierda)
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		SystemClock.sleep(500)
		assertTrue(l.ayudaEntrada)
		s.gestoPrueba(com.xebolax.tiresias.nucleo.Gesto.ABAJO_IZQUIERDA)
		assertNotNull("Atrás no terminó la práctica", esperar(3000) { true.takeIf { !l.ayudaEntrada } })
		// En las pantallas de Tiresias, el doble toque pulsa de verdad (el botón «Siguiente lección»)
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		SystemClock.sleep(500)
		assertNotNull(irA("Siguiente lección", 10))
		var m = marca()
		s.gestoPrueba(com.xebolax.tiresias.nucleo.Gesto.DOBLE_TOQUE)
		assertNotNull("El doble toque no pulsó en la práctica", dichoQue(5000, m) { it.startsWith("Lección 2 de") })
		assertTrue(l.ayudaEntrada)
		// Fuera de Tiresias, los gestos se dicen y no se hacen; y al salir del tutorial, la práctica se acaba
		abrirMuestrario()
		assertNotNull("Al salir del tutorial no terminó la práctica", esperar(4000) { true.takeIf { !l.ayudaEntrada } })
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		SystemClock.sleep(500)
		m = marca()
		s.gestoPrueba(com.xebolax.tiresias.nucleo.Gesto.DERECHA)
		assertNotNull(dichoQue(3000, m) { it.contains(":") })
		s.ejecutarPrueba(Accion.AYUDA_ENTRADA)
		SystemClock.sleep(500)
		assertTrue(!l.ayudaEntrada)
	}

	/** Recorre 20 mensajes deslizando; devuelve lo dicho y el paso más lento, en milisegundos. */
	private fun recorrerMensajes(): Pair<List<String>, Long> {
		abrirMuestrario()
		assertNotNull(irA("Mensaje 1,"))
		SystemClock.sleep(800)
		val dichos = ArrayList<String>()
		var peor = 0L
		repeat(20) {
			val antes = SystemClock.uptimeMillis()
			val voz = s.vozPruebas!!
			val previo = voz.historial().firstOrNull()
			s.ejecutarPrueba(Accion.SIGUIENTE)
			val nuevo = esperar(4000) { voz.historial().firstOrNull()?.takeIf { it != previo } }
			peor = maxOf(peor, SystemClock.uptimeMillis() - antes)
			dichos += nuevo.orEmpty()
			SystemClock.sleep(700)
		}
		return dichos to peor
	}

	@Test
	fun elDesplazamientoAnticipadoNoSeSaltaNadaYNoEspera() {
		ajuste(Claves.DESPLAZAR_LISTAS, Ajustes.DESPLAZAR_NORMAL)
		val (normal, peorNormal) = recorrerMensajes()
		ajuste(Claves.DESPLAZAR_LISTAS, Ajustes.DESPLAZAR_ANTICIPADO)
		val (anticipado, peorAnticipado) = recorrerMensajes()
		android.util.Log.i("Peticiones105Test", "Paso más lento: normal $peorNormal ms, anticipado $peorAnticipado ms")
		for ((nombre, dichos) in listOf("normal" to normal, "anticipado" to anticipado)) {
			val numeros = dichos.mapNotNull { Regex("^Mensaje (\\d+)").find(it)?.groupValues?.get(1)?.toInt() }
			assertEquals("En $nombre se saltó alguno: $dichos", (2..21).toList(), numeros)
		}
		assertTrue("El anticipado no es más rápido: $peorAnticipado ms frente a $peorNormal ms", peorAnticipado < peorNormal)
	}

	/**
	 * Cada ventana de Tiresias (las listas y el campo de buscar) se cierra de las
	 * tres formas: el gesto de Atrás, la tecla Atrás del sistema (en un campo de
	 * texto, como en todo Android, la primera oculta el teclado) y su botón
	 * Cancelar o Cerrar.
	 */
	@Test
	fun lasVentanasDeTiresiasSeCierranDeTodasLasFormas() {
		abrirMuestrario()
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		val problemas = ArrayList<String>()
		for (abrirla in listOf(Accion.MENU_TIRESIAS, Accion.LISTA_ELEMENTOS, Accion.HISTORIAL_HABLA, Accion.BUSCAR)) {
			for (forma in listOf("gesto Atrás", "tecla Atrás", "botón")) {
				hacer(abrirla, 1500)
				if (!s.ventanaAbiertaPruebas) {
					problemas += "$abrirla no abrió su ventana"
					continue
				}
				when (forma) {
					"gesto Atrás" -> s.gestoPrueba(com.xebolax.tiresias.nucleo.Gesto.ABAJO_IZQUIERDA)
					"tecla Atrás" -> {
						ua.executeShellCommand("input keyevent 4").close()
						if (abrirla == Accion.BUSCAR && esperar(1500) { true.takeIf { !s.ventanaAbiertaPruebas } } == null) {
							ua.executeShellCommand("input keyevent 4").close()
						}
					}
					else -> {
						val boton = s.windows.mapNotNull { it.root }.flatMap { r -> listOf("Cancelar", "Cerrar").flatMap { r.findAccessibilityNodeInfosByText(it) } }
							.firstOrNull { it.packageName == app.packageName && it.isClickable }
						if (boton == null) problemas += "$abrirla no tiene botón Cancelar ni Cerrar" else boton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
					}
				}
				if (esperar(4000) { true.takeIf { !s.ventanaAbiertaPruebas } } == null) {
					problemas += "$abrirla no se cerró con $forma"
					s.cerrarVentanaPruebas()
				}
				SystemClock.sleep(700)
			}
		}
		assertTrue(problemas.joinToString("; "), problemas.isEmpty())
	}

	@Test
	fun elDiccionarioConEnesYTildes() {
		val e = com.xebolax.tiresias.nucleo.EntradaDiccionario("\\bniño\\b", "chaval", com.xebolax.tiresias.nucleo.TipoEntrada.EXPRESION)
		assertEquals("el chaval come y la niña no", com.xebolax.tiresias.nucleo.Diccionario(listOf(e)).aplicar("el niño come y la niña no"))
		val p = com.xebolax.tiresias.nucleo.EntradaDiccionario("año", "periodo")
		assertEquals("un periodo y unos años", com.xebolax.tiresias.nucleo.Diccionario(listOf(p)).aplicar("un año y unos años"))
	}
}
