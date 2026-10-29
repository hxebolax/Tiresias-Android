package com.xebolax.tiresias

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.ui.ActividadAjustes
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.TemasSonido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo de la 1.0.6, de lo que vio el usuario: los temas de sonidos a mano en
 * Ajustes, el teclado en pantalla que se anunciaba con cada tecla y los
 * encabezados de los ajustes, que se callaban al deslizar.
 */
@RunWith(AndroidJUnit4::class)
class Peticiones106Test : PruebaTiresias() {

	private fun abrirSeccion(clave: String) = abrir(ActividadAjustes::class.java) { it.putExtra(ActividadAjustes.EXTRA_SECCION, clave) }

	/** «Suave» y «Madera» vienen dentro de Tiresias: se eligen en Ajustes, Voz, sin descargar nada. */
	@Test
	fun losTemasDeSonidosVienenDentroYSeEligenEnAjustes() {
		val incluidos = TemasSonido.incluidos(app)
		val todos = listOf("cristal", "espacio", "gotas", "madera", "marimba", "minimo", "retro", "suave")
		assertTrue("No vienen los temas: $incluidos", incluidos.containsAll(todos))
		assertEquals("Madera", TemasSonido.nombreDe(app, "madera"))
		assertEquals("Mínimo", TemasSonido.nombreDe(app, "minimo"))
		// Completos: cada tema tiene todos los sonidos de Tiresias
		for (t in todos) for (s in Sonido.entries) assertNotNull("$t no tiene $s", TemasSonido.archivoDe(app, t, s))
		// Un ZIP con el nombre de uno incluido no lo tapa
		val zip = java.io.ByteArrayOutputStream().also { o ->
			java.util.zip.ZipOutputStream(o).use { z -> z.putNextEntry(java.util.zip.ZipEntry("foco.wav")); z.write(ByteArray(64) { 1 }); z.closeEntry() }
		}.toByteArray()
		val importado = TemasSonido.importarZip(app, zip.inputStream(), "madera.zip")
		try {
			assertEquals("madera-propio", importado)
		} finally {
			TemasSonido.borrar(app, importado)
		}
		// En Ajustes, Voz, dentro de «Sonidos y vibración», deslizando
		abrirSeccion("seccion_voz")
		val dicho = irA("Tema de sonidos", 60)
		assertNotNull("No se llega a «Tema de sonidos» deslizando", dicho)
		assertTrue("No dice el tema de ahora: $dicho", dicho!!.contains("Los de Tiresias") || dicho.contains("Madera") || dicho.contains("Suave"))
		// Elegirlo cambia el ajuste y los sonidos
		ajuste(Claves.TEMA_SONIDOS, "madera")
		assertEquals("madera", Ajustes(app).temaSonidos)
	}

	/** Al deslizar por una sección de Ajustes, cada encabezado de categoría se dice (no se calla al llegar a él). */
	@Test
	fun losEncabezadosDeLosAjustesSeDicenAlDeslizar() {
		// Con la letra grande la lista se desplaza más a menudo (como en un móvil pequeño). En el emulador de
		// Android 15, cambiar el tamaño de la letra cuelga a veces el sistema entero (el vigilante lo reinicia):
		// ahí se prueba con la letra normal
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		val letraGrande = android.os.Build.VERSION.SDK_INT != 35
		if (letraGrande) ua.executeShellCommand("settings put system font_scale 1.3").close()
		val problemas = ArrayList<String>()
		try {
			SystemClock.sleep(1500)
			for (modo in listOf(Ajustes.DESPLAZAR_NORMAL, Ajustes.DESPLAZAR_SIN_SONIDO, Ajustes.DESPLAZAR_ANTICIPADO)) {
				ajuste(Claves.DESPLAZAR_LISTAS, modo)
				for ((seccion, encabezados) in listOf(
					"seccion_voz" to listOf("Sonidos y vibración"), "seccion_lectura" to listOf("Texto"), "seccion_ia" to listOf("En Internet con tu clave"),
				)) {
					abrirSeccion(seccion)
					val calladaAntes = s.vozPruebas!!.vecesCallada
					val dichos = recorrer("$modo $seccion", 80)
					for (e in encabezados) if (dichos.none { it.startsWith("$e, encabezado") }) problemas += "$modo, $seccion: no dijo «$e»: $dichos"
					// La lista que se adelanta sola (un arrastre de Tiresias) no corta lo que se dice ni parece un dedo atascado
					val cortes = s.vozPruebas!!.vecesCallada - calladaAntes
					if (cortes > 0) problemas += "$modo, $seccion: la voz se cortó sola $cortes veces"
					if (dichos.any { it.contains("exploración táctil se había atascado") }) problemas += "$modo, $seccion: creyó que la exploración táctil se había atascado"
					// Ninguna parada sin decir nada
					dichos.withIndex().filter { it.value.isBlank() }.forEach { problemas += "$modo, $seccion: la parada ${it.index + 1} no dijo nada" }
				}
			}
		} finally {
			if (letraGrande) ua.executeShellCommand("settings put system font_scale 1.0").close()
		}
		assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
	}

	/**
	 * «Teclado en pantalla» se dice una vez al salir el teclado, no cada vez que
	 * se va a una tecla y se vuelve al campo (como al escribir letra a letra).
	 */
	@Test
	fun elTecladoSeAnunciaUnaSolaVez() {
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		val antes = ua.executeShellCommand("settings get secure show_ime_with_hard_keyboard").let { p -> android.os.ParcelFileDescriptor.AutoCloseInputStream(p).bufferedReader().use { it.readText().trim() } }
		ua.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1").close()
		try {
			abrirMuestrario()
			// Tras cambiar el tamaño de la letra (la prueba de antes), el muestrario tarda en rehacerse
			val campo = esperar(10_000) {
				buscarEditable(s.lectorPruebas!!.raiz()) ?: buscarEditable(s.lectorPruebas!!.raiz(), visible = false)?.let {
					it.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
					null
				}
			}
			assertNotNull("No hay campo de texto en el muestrario; delante: ${s.lectorPruebas!!.raiz()?.packageName} ${s.windows.map { "${it.type}:${it.title}" }}", campo)
			val m = marca()
			campo!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
			val teclado = esperar(6000) { s.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
			assertNotNull("No salió el teclado en pantalla", teclado)
			SystemClock.sleep(1500)
			// Ir a una tecla del teclado y volver al campo, varias veces
			repeat(4) { i ->
				val tecla = esperar(3000) { s.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root?.let { teclaDe(it, i) } }
				tecla?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
				SystemClock.sleep(700)
				buscarEditable(s.lectorPruebas!!.raiz())?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
				SystemClock.sleep(700)
			}
			val dichos = s.vozPruebas!!.dichoDesde(m)
			val veces = dichos.count { it.startsWith("Teclado en pantalla") }
			assertTrue("«Teclado en pantalla» se dijo $veces veces: $dichos", veces <= 1)
		} finally {
			ua.executeShellCommand("settings put secure show_ime_with_hard_keyboard ${antes.ifEmpty { "0" }.takeIf { it != "null" } ?: "0"}").close()
			s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
		}
	}

	private fun buscarEditable(n: AccessibilityNodeInfo?, prof: Int = 0, visible: Boolean = true): AccessibilityNodeInfo? {
		if (n == null || prof > 30) return null
		if (n.isEditable && (n.isVisibleToUser || !visible)) return n
		for (i in 0 until n.childCount) buscarEditable(n.getChild(i), prof + 1, visible)?.let { return it }
		return null
	}

	/** La tecla número [i] con nombre de la ventana del teclado. */
	private fun teclaDe(raiz: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? {
		val teclas = ArrayList<AccessibilityNodeInfo>()
		fun recoger(n: AccessibilityNodeInfo?, prof: Int) {
			if (n == null || prof > 20 || teclas.size > 40) return
			if (n.childCount == 0 && (!n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank())) teclas += n
			for (k in 0 until n.childCount) recoger(n.getChild(k), prof + 1)
		}
		recoger(raiz, 0)
		return teclas.getOrNull(i % maxOf(1, teclas.size))
	}

	/** La mediana, en ms, de lo que tarda en sonar el siguiente elemento tras el gesto (10 veces). */
	private fun latencia(): Long {
		hacer(Accion.PRIMERO)
		val voz = s.vozPruebas!!
		val medidas = ArrayList<Long>()
		repeat(10) {
			val antes = voz.ultimoInicioSonido
			val t0 = SystemClock.uptimeMillis()
			s.ejecutarPrueba(Accion.SIGUIENTE)
			esperar(2000) { voz.ultimoInicioSonido.takeIf { it != antes } }?.let { medidas += it - t0 }
			SystemClock.sleep(500)
		}
		assertTrue("La voz no sonó: ${medidas.size} de 10", medidas.size >= 8)
		return medidas.sorted()[medidas.size / 2]
	}

	/**
	 * La latencia del gesto a la voz con cada ajuste que toca la cola de la voz
	 * (informe de carencias): ninguno debe frenarla.
	 */
	@Test
	fun laLatenciaConCadaAjusteQueTocaLaCola() {
		abrirMuestrario()
		val base = latencia()
		val resultados = linkedMapOf<String, Long>("de fábrica" to base)
		for ((clave, valor) in listOf<Pair<String, Any>>(
			Claves.TROCEAR_LARGOS to false, Claves.CAMBIO_IDIOMA to false, "detectar_idioma" to false,
			"sonido_tipo" to true, "sonido_vertical" to true, "sonido_posicional" to true,
			Claves.CALLAR_PROXIMIDAD to false, Claves.CALLAR_AL_GRABAR to false,
		)) {
			ajuste(clave, valor)
			resultados["$clave=$valor"] = latencia()
			restaurarAjustes()
		}
		android.util.Log.i("Peticiones106Test", "Latencia por ajuste: $resultados")
		val lentos = resultados.filterValues { it >= 120 }
		assertTrue("Ajustes que frenan la voz (mediana en ms): $lentos", lentos.isEmpty())
	}

	/** Todos los encabezados (con su texto) de la ventana de delante. */
	private fun encabezadosDe(n: AccessibilityNodeInfo?, prof: Int = 0, salida: MutableList<String> = ArrayList()): MutableList<String> {
		if (n == null || prof > 40) return salida
		if (com.xebolax.tiresias.arbol.Nodos.esEncabezado(n) && n.isVisibleToUser) {
			val texto = (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() } ?: com.xebolax.tiresias.arbol.Nodos.textoHijos(n)
			if (texto.isNotBlank()) salida += texto
		}
		for (i in 0 until n.childCount) encabezadosDe(n.getChild(i), prof + 1, salida)
		return salida
	}

	/**
	 * Los encabezados de la app Ajustes de Android (Accesibilidad, Pantalla,
	 * Sonido) se dicen al deslizar: ninguno se salta ni se queda callado.
	 */
	@Test
	fun losEncabezadosDeLosAjustesDelSistemaSeDicen() {
		val problemas = ArrayList<String>()
		for (accion in listOf(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, android.provider.Settings.ACTION_DISPLAY_SETTINGS, android.provider.Settings.ACTION_SOUND_SETTINGS)) {
			for (modo in listOf(Ajustes.DESPLAZAR_NORMAL, Ajustes.DESPLAZAR_ANTICIPADO)) {
				ajuste(Claves.DESPLAZAR_LISTAS, modo)
				val m = marca()
				app.startActivity(android.content.Intent(accion).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
				esperar(8000) { s.lectorPruebas?.raiz()?.takeIf { it.packageName?.contains("settings") == true && it.childCount > 0 } }
				esperarCalma(m)
				val vistos = LinkedHashSet<String>()
				val dichos = ArrayList<String>()
				dichos += hacer(Accion.PRIMERO)
				var iguales = 0
				for (i in 0 until 90) {
					vistos += encabezadosDe(s.lectorPruebas!!.raiz())
					val d = hacer(Accion.SIGUIENTE, 400)
					if (d == dichos.last()) {
						if (++iguales >= 2) break
						continue
					}
					iguales = 0
					dichos += d
				}
				android.util.Log.i("Peticiones106Test", "$accion $modo: encabezados ${vistos.toList()}; dicho: $dichos")
				// («&» se lee «y»)
				for (e in vistos) if (dichos.none { it.startsWith(e) || it.startsWith(e.replace("&", "y")) }) problemas += "$accion, $modo: no dijo el encabezado «$e»"
				dichos.withIndex().filter { it.value.isBlank() }.forEach { problemas += "$accion, $modo: la parada ${it.index + 1} no dijo nada" }
			}
		}
		s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
		assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
	}

	/** El ajuste de callar con el sensor de proximidad está, encendido de fábrica. */
	@Test
	fun callarConElSensorDeProximidadEstaEnLosAjustes() {
		assertTrue(Ajustes(app).callarProximidad)
		val hay = app.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) != null
		if (!hay) return
		abrirSeccion("seccion_voz")
		assertNotNull("No se llega al ajuste deslizando", irA("Callar al tapar el sensor de proximidad", 60))
		hacer(Accion.SIGUIENTE)
	}

	/**
	 * Grabar una nota de voz en WhatsApp: en un botón de mantener pulsado, el doble toque lo hace Android
	 * (que deja el botón pulsado mientras no se levanta el dedo); en los demás, Tiresias.
	 */
	@Test
	fun enUnBotonDeMantenerPulsadoElDobleToqueEsDeAndroid() {
		if (android.os.Build.VERSION.SDK_INT < 30) return
		fun nodo(id: String?, nombre: String?) = AccessibilityNodeInfo().apply {
			isClickable = true
			id?.let { viewIdResourceName = it }
			nombre?.let { contentDescription = it }
		}
		assertTrue(com.xebolax.tiresias.arbol.Nodos.esDeMantenerPulsado(nodo("com.whatsapp:id/voice_note_btn", "Mensaje de voz")))
		assertTrue(com.xebolax.tiresias.arbol.Nodos.esDeMantenerPulsado(nodo(null, "Grabar mensaje de voz")))
		assertTrue(com.xebolax.tiresias.arbol.Nodos.esDeMantenerPulsado(nodo(null, "Record voice message")))
		// El de WhatsApp de verdad (OnePlus 6, 29-10-2026): ni pulsable ni de pulsación larga, solo con la acción
		assertTrue(com.xebolax.tiresias.arbol.Nodos.esDeMantenerPulsado(AccessibilityNodeInfo().apply {
			viewIdResourceName = "com.whatsapp:id/voice_note_btn"
			addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
		}))
		assertTrue(!com.xebolax.tiresias.arbol.Nodos.esDeMantenerPulsado(nodo("com.whatsapp:id/send", "Enviar")))
		assertTrue(!com.xebolax.tiresias.arbol.Nodos.esDeMantenerPulsado(nodo("com.whatsapp:id/voice_call_btn", "Llamada de voz")))
		val maneja = android.accessibilityservice.AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP
		val l = s.lectorPruebas!!
		try {
			l.alMantenerDeVerdad!!.invoke(true)
			assertTrue("En el botón de grabar, el doble toque sigue siendo de Tiresias", esperar(2000) { (s.serviceInfo.flags and maneja == 0).takeIf { it } } == true)
			// Y Android lo aplica de verdad (la exploración táctil hace el doble toque)
			val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
			fun volcado() = ua.executeShellCommand("dumpsys accessibility").let { p -> android.os.ParcelFileDescriptor.AutoCloseInputStream(p).bufferedReader().use { it.readText() } }
			assertNotNull("Android no ha dejado el doble toque a la exploración táctil", esperar(3000) { volcado().takeIf { it.contains("serviceHandlesDoubleTap=false") } })
		} finally {
			l.alMantenerDeVerdad!!.invoke(false)
		}
		assertTrue("Fuera del botón, el doble toque no ha vuelto a Tiresias", esperar(2000) { (s.serviceInfo.flags and maneja != 0).takeIf { it } } == true)
	}

	/** Con la cortina puesta, la captura para la IA y el OCR ve la pantalla de verdad (no negra). */
	@Test
	fun laIAVeLaPantallaConLaCortinaPuesta() {
		if (android.os.Build.VERSION.SDK_INT < 30) return
		abrirMuestrario()
		SystemClock.sleep(1000)
		hacer(Accion.CORTINA)
		try {
			SystemClock.sleep(800)
			val captura = s.lectorPruebas!!.vision.capturar()
			assertNotNull("No hubo captura", captura)
			// La parte de arriba del muestrario es clara: con la cortina en la captura, todo sería negro
			var claros = 0
			var total = 0
			for (x in 0 until captura!!.width step 40) for (y in captura.height / 8 until captura.height / 2 step 40) {
				val p = captura.getPixel(x, y)
				total++
				if (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p) > 120) claros++
			}
			assertTrue("La captura salió negra (la cortina tapaba): $claros claros de $total", claros > total / 10)
		} finally {
			hacer(Accion.CORTINA)
		}
	}

	/** Con un dedo en la pantalla, las teclas de volumen suben y bajan el de la voz (como TalkBack); sin dedo, son del sistema. */
	@Test
	fun conUnDedoEnLaPantallaLasTeclasCambianElVolumenDeLaVoz() {
		if (android.os.Build.VERSION.SDK_INT < 30) return
		val audio = app.getSystemService(android.media.AudioManager::class.java)
		val flujo = com.xebolax.tiresias.voz.PistaAudio.flujo
		val max = audio.getStreamMaxVolume(flujo)
		val antes = audio.getStreamVolume(flujo)
		val l = s.lectorPruebas!!
		fun toque(tipo: Int) = inst.runOnMainSync { l.alEvento(android.view.accessibility.AccessibilityEvent(tipo)) }
		fun tecla(accion: Int, codigo: Int): Boolean {
			var r = false
			inst.runOnMainSync { r = s.teclaPrueba(android.view.KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), accion, codigo, 0)) }
			return r
		}
		try {
			// A media altura, para que pueda subir y bajar
			audio.setStreamVolume(flujo, maxOf(1, max / 2), 0)
			val medio = audio.getStreamVolume(flujo)
			toque(android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START)
			val m = marca()
			assertTrue("Con el dedo, Tiresias no se quedó la tecla", tecla(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP))
			assertTrue(tecla(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_UP))
			assertTrue("No subió el volumen de la voz", esperar(2000) { (audio.getStreamVolume(flujo) > medio).takeIf { it } } == true)
			assertNotNull("No dijo el volumen", dichoQue(3000, m) { it.contains("por ciento") })
			assertTrue(tecla(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
			assertTrue(tecla(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
			assertTrue("No bajó el volumen de la voz", esperar(2000) { (audio.getStreamVolume(flujo) == medio).takeIf { it } } == true)
			toque(android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END)
			// Sin el dedo, las teclas vuelven a lo de siempre
			assertTrue("Sigue creyendo que hay un dedo en la pantalla", !l.dedoEnPantalla)
		} finally {
			toque(android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END)
			audio.setStreamVolume(flujo, antes, 0)
		}
	}

	/**
	 * El toque de verdad de Tiresias (clic directo, el respaldo cuando la app no atiende el clic de
	 * accesibilidad, la pulsación larga) llega a la app como una pulsación, no como exploración.
	 */
	@Test
	fun elToqueDeVerdadPulsaLaApp() {
		if (android.os.Build.VERSION.SDK_INT < 30) return
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		ajuste(Claves.APPS_CLIC_DIRECTO, setOf("com.xebolax.tiresias.muestrario"))
		abrirMuestrario()
		assertNotNull(irA("Guardar"))
		val pulsado = java.util.concurrent.CountDownLatch(1)
		ua.setOnAccessibilityEventListener { e ->
			if (e.eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED && e.text.any { it.toString() == "Guardar" }) pulsado.countDown()
		}
		try {
			s.ejecutarPrueba(Accion.ACTIVAR)
			assertTrue("El toque de verdad no pulsó «Guardar» (llegó como exploración)", pulsado.await(4, java.util.concurrent.TimeUnit.SECONDS))
		} finally {
			ua.setOnAccessibilityEventListener(null)
			ajuste(Claves.APPS_CLIC_DIRECTO, emptySet<String>())
		}
	}

	private fun notificar(titulo: String, texto: String): Int {
		if (android.os.Build.VERSION.SDK_INT >= 33) inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(android.app.NotificationManager::class.java)
		if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("pruebas", "Pruebas", android.app.NotificationManager.IMPORTANCE_HIGH))
		val n = (if (android.os.Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(app, "pruebas") else @Suppress("DEPRECATION") android.app.Notification.Builder(app))
			.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(titulo).setContentText(texto).build()
		val id = (SystemClock.uptimeMillis() % 10000).toInt()
		nm.notify(id, n)
		return id
	}

	/** Una palabra elegida calla la notificación, que queda en el historial con el motivo. */
	@Test
	fun unaPalabraCallaLaNotificacionYElHistorialDiceElMotivo() {
		ajuste(Claves.PALABRAS_CALLADAS, "anuncio secreto, otra")
		val nm = app.getSystemService(android.app.NotificationManager::class.java)
		val m = marca()
		val id = notificar("Oferta", "Un anuncio secreto de prueba")
		try {
			SystemClock.sleep(4000)
			assertTrue("Se leyó la notificación con la palabra callada", s.vozPruebas!!.dichoDesde(m).none { it.contains("Oferta") })
			val aviso = s.lectorPruebas!!.avisos.historial().firstOrNull { it.titulo == "Oferta" }
			assertNotNull("No está en el historial", aviso)
			assertEquals(R.string.aviso_motivo_palabra, aviso!!.motivo)
		} finally {
			nm.cancel(id)
			ajuste(Claves.PALABRAS_CALLADAS, "")
		}
	}

	/** El nombre del grupo que se repite («Familia: Ana», «Familia: Juan») se dice la primera vez. */
	@Test
	fun elPrincipioRepetidoDeUnGrupoSeDiceUnaVez() {
		val nm = app.getSystemService(android.app.NotificationManager::class.java)
		val m = marca()
		val id1 = notificar("Familia prueba: Ana", "Hola")
		assertNotNull("No se leyó la primera", dichoQue(8000, m) { it.contains("Familia prueba: Ana") })
		SystemClock.sleep(1500)
		val m2 = marca()
		val id2 = notificar("Familia prueba: Juan", "Qué tal")
		try {
			val dicha = dichoQue(8000, m2) { it.contains("Juan") }
			assertNotNull("No se leyó la segunda", dicha)
			assertTrue("Repitió el nombre del grupo: «$dicha»", !dicha!!.contains("Familia prueba"))
		} finally {
			nm.cancel(id1)
			nm.cancel(id2)
		}
	}

	/** Al cambiar un volumen (con las teclas, aquí desde la prueba), se dice cuál y cuánto queda. */
	@Test
	fun seDiceElVolumenNuevo() {
		val audio = app.getSystemService(android.media.AudioManager::class.java)
		val flujo = android.media.AudioManager.STREAM_MUSIC
		val antes = audio.getStreamVolume(flujo)
		val max = audio.getStreamMaxVolume(flujo)
		val m = marca()
		try {
			audio.setStreamVolume(flujo, if (antes < max) antes + 1 else antes - 1, 0)
			assertNotNull("No dijo el volumen nuevo", dichoQue(4000, m) { it.startsWith("Volumen multimedia") && it.contains("por ciento") })
		} finally {
			audio.setStreamVolume(flujo, antes, 0)
			SystemClock.sleep(800)
		}
	}

	/**
	 * El menú de Tiresias se usa varias veces seguidas (OnePlus 6, 29-10-2026: la segunda vez el doble toque no
	 * activaba nada, ni Ajustes ni Cancelar). La causa: con el selector en Edición, el doble toque hacía la opción
	 * de edición elegida (ninguna) en vez de activar; en un elemento que no es un campo, ahora activa.
	 */
	@Test
	fun elMenuDeTiresiasSeUsaVariasVecesSeguidas() {
		val maneja = android.accessibilityservice.AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP
		fun dobleToqueDeTiresias() = android.os.Build.VERSION.SDK_INT < 30 || esperar(2000) { (s.serviceInfo.flags and maneja != 0).takeIf { it } } == true
		fun abrirMenu(vuelta: Int) {
			hacer(Accion.MENU_TIRESIAS, 1500)
			assertTrue("Vuelta $vuelta: no se abrió el menú", s.ventanaAbiertaPruebas)
			assertTrue("Vuelta $vuelta: en el menú, el doble toque no es de Tiresias", dobleToqueDeTiresias())
		}
		val l = s.lectorPruebas!!
		abrirMuestrario()
		for (vuelta in 1..3) {
			// Con el selector en Edición (deslizar arriba o abajo sin querer)
			l.control = if (vuelta == 2) com.xebolax.tiresias.nucleo.ControlLectura.EDICION else com.xebolax.tiresias.nucleo.ControlLectura.PREDETERMINADO
			// 1. Una opción que se queda en la pantalla: Hora, fecha y batería
			abrirMenu(vuelta)
			assertNotNull("Vuelta $vuelta: no se llega a la hora", irA(nombreAccionDe(Accion.ESTADO_SISTEMA), 60))
			var m = marca()
			s.ejecutarPrueba(Accion.ACTIVAR)
			assertTrue("Vuelta $vuelta: el menú no se cerró al elegir", esperar(4000) { (!s.ventanaAbiertaPruebas).takeIf { it } } == true)
			assertNotNull("Vuelta $vuelta: no dijo la batería", dichoQue(6000, m) { it.contains("%") || it.contains("por ciento") || it.contains("batería") })
			SystemClock.sleep(1200)
			// 2. Cancelar
			abrirMenu(vuelta)
			assertNotNull("Vuelta $vuelta: no se llega a Cancelar", irA("Cancelar", 60))
			s.ejecutarPrueba(Accion.ACTIVAR)
			assertTrue("Vuelta $vuelta: Cancelar no cerró el menú", esperar(4000) { (!s.ventanaAbiertaPruebas).takeIf { it } } == true)
			SystemClock.sleep(1200)
		}
		// 3. Ajustes de Tiresias, tras haberlo usado varias veces (y con el selector en Edición)
		l.control = com.xebolax.tiresias.nucleo.ControlLectura.EDICION
		abrirMenu(4)
		assertNotNull("No se llega a Ajustes de Tiresias", irA(nombreAccionDe(Accion.AJUSTES_TIRESIAS), 60))
		val m = marca()
		s.ejecutarPrueba(Accion.ACTIVAR)
		val abierta = esperar(6000) { s.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName == app.packageName } }
		assertNotNull("Ajustes de Tiresias no se abrió desde el menú", abierta)
		assertTrue("Dijo que no se pudo abrir", s.vozPruebas!!.dichoDesde(m).none { it.startsWith("No se ha podido abrir") })
		l.control = com.xebolax.tiresias.nucleo.ControlLectura.PREDETERMINADO
		s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
		SystemClock.sleep(800)
	}

	/** Al recorrer el menú de Tiresias (largo: se desplaza), cada paso suena, adelante y con arriba y abajo. */
	@Test
	fun alRecorrerElMenuDeTiresiasSuenaCadaPaso() {
		val son = s.lectorPruebas!!.sonidos
		val mudos = ArrayList<String>()
		abrirMuestrario()
		for (modo in listOf(Ajustes.DESPLAZAR_NORMAL, Ajustes.DESPLAZAR_SIN_SONIDO, Ajustes.DESPLAZAR_ANTICIPADO)) {
			ajuste(Claves.DESPLAZAR_LISTAS, modo)
			hacer(Accion.MENU_TIRESIAS, 1500)
			assertTrue("No se abrió el menú", s.ventanaAbiertaPruebas)
			try {
				repeat(30) { i ->
					val antes = son.sonados
					val d = hacer(Accion.SIGUIENTE, 400)
					if (son.sonados == antes) mudos += "$modo, siguiente $i: «$d»"
				}
				repeat(12) { i ->
					val antes = son.sonados
					val d = hacer(Accion.SELECTOR_ANTERIOR, 400)
					if (son.sonados == antes) mudos += "$modo, arriba $i: «$d»"
				}
				repeat(6) { i ->
					val antes = son.sonados
					val d = hacer(Accion.SELECTOR_SIGUIENTE, 400)
					if (son.sonados == antes) mudos += "$modo, abajo $i: «$d»"
				}
			} finally {
				hacer(Accion.ATRAS, 1000)
			}
		}
		ajuste(Claves.DESPLAZAR_LISTAS, Ajustes.DESPLAZAR_NORMAL)
		assertTrue("Pasos sin sonido en el menú: $mudos", mudos.isEmpty())
		// Con «desplazar con su sonido», al moverse la lista del menú (es larga) suena el de desplazar
		hacer(Accion.MENU_TIRESIAS, 1500)
		val antes = son.vecesSonado(com.xebolax.tiresias.voz.Sonido.DESPLAZAR)
		try {
			repeat(35) { hacer(Accion.SIGUIENTE, 400) }
		} finally {
			hacer(Accion.ATRAS, 1000)
		}
		assertTrue("Al desplazarse la lista del menú no sonó el sonido de desplazar", son.vecesSonado(com.xebolax.tiresias.voz.Sonido.DESPLAZAR) > antes)
	}

	private fun nombreAccionDe(a: Accion): String {
		val id = app.resources.getIdentifier("accion_" + a.name.lowercase(), "string", app.packageName)
		return com.xebolax.tiresias.base.Idioma.envolver(app).getString(id)
	}

	/** Las vibraciones se notan: ningún pulso es tan corto que el motor no llegue a arrancar. */
	@Test
	fun lasVibracionesDuranLoBastanteParaNotarse() {
		val foco = longArrayOf(0, 12)
		assertEquals(12L, com.xebolax.tiresias.voz.Vibracion.duraciones(foco, true, 60)[1])
		// Sin control de intensidad: al menos 25 ms, y la intensidad alarga el pulso
		val al60 = com.xebolax.tiresias.voz.Vibracion.duraciones(foco, false, 60)[1]
		val al100 = com.xebolax.tiresias.voz.Vibracion.duraciones(foco, false, 100)[1]
		assertTrue("Pulso de $al60 ms", al60 >= 25)
		assertTrue("La intensidad no cambia nada: $al60 / $al100", al100 > al60)
		assertEquals(25L, com.xebolax.tiresias.voz.Vibracion.duraciones(foco, false, 0)[1])
		// Las pausas no cambian
		assertEquals(30L, com.xebolax.tiresias.voz.Vibracion.duraciones(longArrayOf(0, 8, 30, 8), false, 60)[2])
	}

	/** Ajustes y el tutorial se abren desde el menú de Tiresias, también con la pantalla de inicio delante. */
	@Test
	fun losAjustesYElTutorialSeAbrenDesdeElMenu() {
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		for (a in listOf(Accion.AJUSTES_TIRESIAS, Accion.TUTORIAL)) {
			ua.executeShellCommand("input keyevent KEYCODE_HOME").close()
			SystemClock.sleep(1500)
			val m = marca()
			s.ejecutarPrueba(a)
			val abierta = esperar(5000) {
				s.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName == app.packageName }
			}
			assertNotNull("$a no abrió ninguna pantalla de Tiresias", abierta)
			SystemClock.sleep(2000)
			assertTrue("Dijo que no se pudo abrir", s.vozPruebas!!.dichoDesde(m).none { it.startsWith("No se ha podido abrir") })
			s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
			SystemClock.sleep(800)
		}
		ua.executeShellCommand("input keyevent KEYCODE_HOME").close()
	}
}
