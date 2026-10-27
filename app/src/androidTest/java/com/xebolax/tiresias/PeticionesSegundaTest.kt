package com.xebolax.tiresias

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.vision.Codigos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lo de la segunda investigación de octubre de 2026
 * (`docs/investigacion/05_peticiones_2026-10_segunda.md`): contestar y colgar
 * las llamadas de internet, vigilar un elemento, ir a una posición de la lista,
 * el resumen de los avisos y los códigos QR (de la pantalla y de una imagen
 * compartida).
 */
@RunWith(AndroidJUnit4::class)
class PeticionesSegundaTest : PruebaTiresias() {

	private fun permisoNotificaciones() {
		if (Build.VERSION.SDK_INT >= 33) inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("pruebas", "Pruebas", NotificationManager.IMPORTANCE_HIGH))
	}

	private fun constructor(): Notification.Builder =
		if (Build.VERSION.SDK_INT >= 26) Notification.Builder(app, "pruebas") else @Suppress("DEPRECATION") Notification.Builder(app)

	@Test
	fun unaLlamadaDeInternetSeContestaYSeCuelgaConElGesto() {
		permisoNotificaciones()
		val pulsados = CopyOnWriteArrayList<String>()
		val receptor = object : BroadcastReceiver() {
			override fun onReceive(c: Context, i: Intent) {
				pulsados += i.action.orEmpty()
			}
		}
		val filtro = IntentFilter().apply { addAction("prueba.contestar"); addAction("prueba.rechazar") }
		androidx.core.content.ContextCompat.registerReceiver(app, receptor, filtro, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
		val nm = app.getSystemService(NotificationManager::class.java)
		try {
			fun boton(nombre: String, accion: String) = Notification.Action.Builder(null, nombre,
				PendingIntent.getBroadcast(app, accion.hashCode(), Intent(accion).setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)).build()
			val n = constructor().setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Ana").setContentText("Llamada de voz entrante")
				.setCategory(Notification.CATEGORY_CALL)
				.addAction(boton("Rechazar", "prueba.rechazar")).addAction(boton("Contestar", "prueba.contestar")).build()
			nm.notify(4711, n)
			assertNotNull("No llegó la llamada", esperar(8000) { s.lectorPruebas?.avisos?.llamadaInternet })
			// Suena: el gesto la contesta
			var m = marca()
			s.ejecutarPrueba(Accion.MULTIMEDIA)
			assertNotNull("No se pulsó Contestar", esperar(4000) { pulsados.takeIf { "prueba.contestar" in it } })
			assertNotNull(dichoQue(3000, m) { it == "Contestando" })
			// Otra vez: la cuelga (con «Rechazar», que es lo que tiene)
			m = marca()
			s.ejecutarPrueba(Accion.MULTIMEDIA)
			assertNotNull("No se pulsó Rechazar", esperar(4000) { pulsados.takeIf { "prueba.rechazar" in it } })
			assertNotNull(dichoQue(3000, m) { it == "Colgando" })
			assertEquals(listOf("prueba.contestar", "prueba.rechazar"), pulsados.toList())
			assertEquals(null, s.lectorPruebas?.avisos?.llamadaInternet)
		} finally {
			nm.cancel(4711)
			app.unregisterReceiver(receptor)
		}
	}

	@Test
	fun vigilarUnElementoAvisaCuandoCambia() {
		abrirMuestrario()
		val casilla = s.lectorPruebas!!.raiz()!!.findAccessibilityNodeInfosByText("Recordarme").first()
		val l = s.lectorPruebas!!
		var m = marca()
		l.manejador.post { l.alternarVigilar(casilla) }
		assertNotNull(dichoQue(4000, m) { it.startsWith("Vigilando: Recordarme") })
		m = marca()
		assertTrue(casilla.performAction(AccessibilityNodeInfo.ACTION_CLICK))
		assertNotNull("No avisó del cambio", dichoQue(8000, m) { it.startsWith("Ha cambiado: Recordarme") && it.contains("marcad") })
		// Deja de vigilar con la misma acción
		m = marca()
		s.ejecutarPrueba(Accion.VIGILAR)
		assertNotNull(dichoQue(4000, m) { it == "Ya no se vigila" })
		casilla.performAction(AccessibilityNodeInfo.ACTION_CLICK)
		SystemClock.sleep(3500)
		assertTrue("Siguió vigilando", s.vozPruebas!!.dichoDesde(m).none { it.startsWith("Ha cambiado") })
	}

	@Test
	fun irAUnaPosicionDeLaLista() {
		abrirMuestrario()
		assertNotNull(irA("Mensaje 1,"))
		val m = marca()
		s.irALaFilaPruebas(29)
		assertNotNull("No llegó al 30", dichoQue(20_000, m) { it.startsWith("Mensaje 30") })
	}

	@Test
	fun elResumenDeLosAvisosDiceDeQuien() {
		permisoNotificaciones()
		val nm = app.getSystemService(NotificationManager::class.java)
		val ids = listOf(4801, 4802, 4803)
		for ((i, quien) in listOf("Ana", "Juan", "Ana").withIndex()) {
			nm.notify(ids[i], constructor().setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(quien).setContentText("Hola $i").build())
			SystemClock.sleep(700)
		}
		try {
			esperar(8000) { s.lectorPruebas?.avisos?.historial()?.takeIf { h -> h.count { it.titulo == "Juan" } > 0 } }
			val dicho = hacer(Accion.RESUMEN_AVISOS, 800)
			assertTrue("«$dicho»", dicho.startsWith("Tienes ") && dicho.contains("de Ana y Juan"))
		} finally {
			ids.forEach { nm.cancel(it) }
		}
	}

	/** Lo que pide un complemento al cargarse (su temporizador, el idioma) tiene respuesta: antes se perdía. */
	@Test
	fun unComplementoPideCosasAlCargarse() {
		val sc = s.lectorPruebas!!.scripts
		val pedidos = CopyOnWriteArrayList<String>()
		sc.espia = { id, metodo, _ -> if (id == "recordatorios") pedidos += metodo }
		val codigo = inst.context.assets.open("recordatorios/complemento.js").bufferedReader().use { it.readText() }
		val yaEstaba = sc.instalados().any { it.id == "recordatorios" }
		try {
			sc.instalar(codigo)
			assertTrue("No pidió su temporizador al cargarse: $pedidos", esperar(5000) { pedidos.takeIf { "temporizador" in it } } != null)
		} finally {
			sc.espia = null
			if (!yaEstaba) sc.desinstalar("recordatorios")
		}
	}

	/** La ventana de buscar (con el teclado abierto) se cierra con sus botones, con el doble toque. */
	@Test
	fun laVentanaDeBuscarSeManejaConSusBotones() {
		abrirMuestrario()
		for (boton in listOf("Cancelar", "Aceptar")) {
			hacer(Accion.BUSCAR, 1500)
			assertTrue("No se abrió la búsqueda", s.ventanaAbiertaPruebas)
			val m = marca()
			assertNotNull("No se llega a «$boton» deslizando", irA(boton, 10))
			s.ejecutarPrueba(Accion.ACTIVAR)
			val cerrada = esperar(5000) { true.takeIf { !s.ventanaAbiertaPruebas } }
			if (cerrada == null) s.cerrarVentanaPruebas()
			assertNotNull("«$boton» no hizo nada con el doble toque. Dicho: ${s.vozPruebas!!.dichoDesde(m)}", cerrada)
			SystemClock.sleep(800)
		}
		// Con el teclado abierto, los botones quedan por encima de él
		hacer(Accion.BUSCAR, 2500)
		val ventanas = s.windows
		val teclado = ventanas.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
		val cancelar = ventanas.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText("Cancelar") }.firstOrNull()
		assertNotNull(cancelar)
		if (teclado != null) {
			val t = android.graphics.Rect().also { teclado.getBoundsInScreen(it) }
			val b = android.graphics.Rect().also { cancelar!!.getBoundsInScreen(it) }
			assertTrue("Cancelar ($b) queda debajo del teclado ($t)", b.bottom <= t.top)
		}
		// Y la tecla Intro del teclado busca
		val campo = ventanas.mapNotNull { it.root }.mapNotNull { r -> r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.firstOrNull { it.isEditable }
		assertNotNull("No está el campo de buscar", campo)
		campo!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Guardar") })
		if (Build.VERSION.SDK_INT >= 30) {
			val m = marca()
			assertTrue(campo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
			assertNotNull("Intro no cerró la búsqueda", esperar(5000) { true.takeIf { !s.ventanaAbiertaPruebas } })
			assertNotNull("No buscó «Guardar»", dichoQue(6000, m) { it.startsWith("Guardar") })
		} else s.cerrarVentanaPruebas()
	}

	/** Todos los textos de una ventana (para saber qué hay cuando algo falla). */
	private fun textosDe(n: AccessibilityNodeInfo?, prof: Int = 0): List<String> {
		if (n == null || prof > 30) return emptyList()
		val propios = listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).filter { it.isNotBlank() }
		return propios + (0 until n.childCount).flatMap { textosDe(n.getChild(it), prof + 1) }
	}

	/** Un código QR en una imagen blanca, como el de una tarjeta. */
	private fun imagenQr(texto: String): Bitmap {
		val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 400, 400)
		val b = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
		b.eraseColor(Color.WHITE)
		for (x in 0 until 400) for (y in 0 until 400) if (m[x, y]) b.setPixel(x + 50, y + 50, Color.BLACK)
		return b
	}

	@Test
	fun losCodigosQrSeLeenDeUnaImagenCompartidaYDeLaPantalla() {
		val web = "https://xebolax.synology.me/Tiresias-Android"
		val qr = imagenQr(web)
		assertEquals(web, Codigos.leer(qr).single().texto)
		// Compartida con «Leer con Tiresias»
		val archivo = File(app.cacheDir, "compartir/qr_prueba.png").apply { parentFile?.mkdirs() }
		FileOutputStream(archivo).use { qr.compress(Bitmap.CompressFormat.PNG, 100, it) }
		val uri = FileProvider.getUriForFile(app, app.packageName + ".archivos", archivo)
		val m = marca()
		app.startActivity(Intent(Intent.ACTION_SEND).setClass(app, com.xebolax.tiresias.ui.ActividadImagen::class.java)
			.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
		val leido = esperar(30_000) { s.lectorPruebas?.raiz()?.findAccessibilityNodeInfosByText("Código QR: $web")?.firstOrNull() }
		assertNotNull("No se leyó el código de la imagen. En la pantalla: ${textosDe(s.lectorPruebas?.raiz())}", leido)
		esperarCalma(m)
		// Y de la pantalla, con la imagen a la vista
		if (Build.VERSION.SDK_INT >= 30) {
			val m2 = marca()
			s.ejecutarPrueba(Accion.LEER_QR)
			assertNotNull("No se leyó el código de la pantalla", dichoQue(15_000, m2) { it.startsWith("Código QR: https") && it.contains("xebolax.synology.me") })
			s.cerrarVentanaPruebas()
		}
	}
}
