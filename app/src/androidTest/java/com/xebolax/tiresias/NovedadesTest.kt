package com.xebolax.tiresias

import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.braille.Braille
import com.xebolax.tiresias.ia.Personas
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Elemento
import com.xebolax.tiresias.scripts.Tienda
import com.xebolax.tiresias.servicio.AvisoPausa
import com.xebolax.tiresias.textos.DatosIdioma
import com.xebolax.tiresias.ventanas.MenuCircular
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Lo nuevo de la 1.0.1 que no tiene prueba propia en otra clase: complementos
 * con reglas y su API nueva, la calculadora y las notas en braille, el braille
 * ASCII de los BRF, el menú circular con un gesto de verdad, las acciones
 * directas por tipo, el campo de texto propio de Tiresias, el estado elegible,
 * el aviso de pausa, los identificadores en español y las personas conocidas.
 */
@RunWith(AndroidJUnit4::class)
class NovedadesTest : PruebaTiresias() {
	private val pruebas get() = inst.context

	private fun codigo(id: String) = pruebas.assets.open("$id/complemento.js").bufferedReader().use { it.readText() }

	/** La celda con esos puntos, en el número que manda la línea (punto 1 = bit 0). */
	private fun p(vararg puntos: Int) = puntos.fold(0) { a, d -> a or (1 shl (d - 1)) }.toString()

	@Test
	fun lasReglasYLaApiNuevaDeLosComplementos() {
		val scripts = s.lectorPruebas!!.scripts
		val info = scripts.instalar(
			"""
			export const manifest = { id: "prueba-reglas", nombre: "Prueba", version: "1.0", descripcion: "d", apps: ["*"], permisos: ["voz", "braille"],
				reglas: [
					{ id: "fila", agrupar: true, rol: "tarjeta", activar: "boton_ok" },
					{ id: "com.app:id/reloj", silenciar: "todo" },
					{ id: "fondo", ocultar: "todo" },
					{ id: "solo_chat", etiqueta: "Chat", actividad: "ChatActivity", levantar: true },
				] };
			tiresias.alAcciones(e => e.id.endsWith("fila") ? [{ nombre: "Responder", hacer: () => tiresias.guardar("hecho", "si") }] : []);
			tiresias.alTeclaBraille(t => { if (t.tipo === "puntos" && t.valor === "1") { tiresias.guardar("tecla", t.valor); return true; } return false; });
			tiresias.orden("Leer dato", () => tiresias.guardar("leido", String(tiresias.leer("hecho"))));
			""".trimIndent()
		)
		val datos = app.getSharedPreferences("complemento_prueba-reglas", android.content.Context.MODE_PRIVATE)
		try {
			val fila = info.reglas.first { it.id == "fila" }
			assertTrue(fila.agrupar)
			assertEquals("tarjeta", fila.rol)
			assertEquals("boton_ok", fila.activar)
			assertTrue(info.reglas.first { it.id == "com.app:id/reloj" }.silenciarDentro)
			assertTrue(info.reglas.first { it.id == "fondo" }.ocultarDentro)
			info.reglas.first { it.id == "solo_chat" }.let { assertEquals("ChatActivity", it.actividad); assertTrue(it.levantar) }
			// Acciones añadidas a un elemento, y que se hacen
			val acciones = scripts.acciones("com.app", Elemento(identificador = "com.app:id/fila"))
			assertEquals(listOf("Responder"), acciones.map { it.third })
			scripts.hacerAccion(acciones[0].first, acciones[0].second)
			assertEquals("si", datos.getString("hecho", null))
			// Las teclas de la línea pasan antes por el complemento
			assertTrue(scripts.teclaBraille("puntos", "1"))
			assertFalse(scripts.teclaBraille("puntos", "2"))
			assertEquals("1", datos.getString("tecla", null))
			// Leer lo guardado
			scripts.ejecutarOrden("prueba-reglas", "Leer dato")
			assertEquals("si", datos.getString("leido", null))
		} finally {
			scripts.desinstalar(info.id)
		}
		assertTrue("Al desinstalar, sus datos se borran", datos.all.isEmpty())
	}

	@Test
	fun elModoBrailleConNotasYCalculadora() {
		val scripts = s.lectorPruebas!!.scripts
		val modo = scripts.instalar(codigo("modo-braille"))
		try {
			scripts.ejecutarOrden("modo-braille", "Modo braille")
			// Nueva nota: «hola»
			assertTrue(scripts.teclaBraille("accion", "siguiente"))
			scripts.teclaBraille("tecla", "intro")
			for (c in listOf(p(1, 2, 5), p(1, 3, 5), p(1, 2, 3), p(1))) assertTrue(scripts.teclaBraille("puntos", c))
			var m = marca()
			scripts.teclaBraille("tecla", "intro")
			assertNotNull("No se guardó la nota", dichoQue(3000, m) { it.contains("Nota guardada: hola") })
			// Notas: se lee la guardada
			m = marca()
			scripts.teclaBraille("tecla", "intro")
			assertNotNull("No se leyó la nota", dichoQue(3000, m) { it.contains("Nota 1 de 1: hola") })
			// Calculadora: 12 + 3
			scripts.teclaBraille("accion", "atras")
			repeat(3) { scripts.teclaBraille("accion", "siguiente") }
			scripts.teclaBraille("tecla", "intro")
			for (c in listOf(p(1), p(1, 2), p(2, 3, 5), p(1, 4))) scripts.teclaBraille("puntos", c)
			m = marca()
			scripts.teclaBraille("tecla", "intro")
			assertNotNull("La calculadora no dio el resultado", dichoQue(3000, m) { it.contains("12 más 3 igual a 15") })
			// Fuera del modo, las teclas vuelven a ser de Tiresias
			scripts.teclaBraille("accion", "atras")
			scripts.teclaBraille("accion", "atras")
			assertFalse(scripts.teclaBraille("puntos", p(1)))
		} finally {
			scripts.desinstalar(modo.id)
		}
	}

	@Test
	fun elBrailleAsciiDeLosBrf() {
		assertEquals("⠁⠃⠉", Braille.brfAUnicode("ABC"))
		assertEquals("⠁⠃⠉", Braille.brfAUnicode("abc"))
		// La coma es el punto 6; el espacio, la celda vacía
		assertEquals("⠠⠀⠿", Braille.brfAUnicode(", ="))
		// Sin línea braille (el emulador no tiene), no se abre
		assertFalse(s.abrirBrf("HELLO"))
	}

	@Test
	fun elMenuCircularConUnGestoDeVerdad() {
		// Los sectores: arriba la primera opción y luego en el sentido de las agujas del reloj
		assertEquals(0, MenuCircular.indiceDeSector(0f, -100f, 8))
		assertEquals(1, MenuCircular.indiceDeSector(70f, -70f, 8))
		assertEquals(2, MenuCircular.indiceDeSector(100f, 0f, 8))
		assertEquals(4, MenuCircular.indiceDeSector(0f, 100f, 8))
		assertEquals(6, MenuCircular.indiceDeSector(-100f, 0f, 8))
		assertEquals(-1, MenuCircular.indiceDeSector(-100f, 0f, 3))
		if (Build.VERSION.SDK_INT < 30) return
		abrirMuestrario()
		ajuste("menu_circular", "ESTADO_SISTEMA,REPETIR")
		hacer(Accion.MENU_CIRCULAR, 800)
		assertTrue("No se abrió el menú circular", s.ventanaAbiertaPruebas)
		SystemClock.sleep(500)
		// Un dedo en el centro de la pantalla que sube y se levanta: la primera opción (arriba)
		val m = s.resources.displayMetrics
		val camino = Path().apply {
			moveTo(m.widthPixels / 2f, m.heightPixels / 2f)
			lineTo(m.widthPixels / 2f, m.heightPixels / 2f - 400f)
		}
		val marcaAntes = marca()
		s.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(camino, 0, 700)).build(), null, null)
		assertNotNull("Al pasar por la opción no se dijo su nombre", dichoQue(4000, marcaAntes) { it.startsWith("Hora, fecha y batería") })
		assertNotNull("Al levantar el dedo no se hizo la opción", dichoQue(5000, marcaAntes) { it.contains("batería al") })
		assertFalse("El menú circular no se cerró", s.ventanaAbiertaPruebas)
	}

	@Test
	fun lasAccionesDirectasPorTipo() {
		abrirMuestrario()
		assertEquals("Controles, encabezado", hacer(Accion.PRIMERO))
		assertTrue(hacer(Accion.ACTIVABLE_SIGUIENTE).startsWith("Guardar"))
		assertTrue(hacer(Accion.CASILLA_SIGUIENTE).startsWith("Recordarme"))
		assertTrue(hacer(Accion.BOTON_ANTERIOR).startsWith("boton enviar") || ultimo().startsWith("Guardar"))
	}

	@Test
	fun buscarEscribiendoEnElCampoDeTiresias() {
		abrirMuestrario()
		hacer(Accion.BUSCAR, 1200)
		assertTrue("No se abrió Buscar", s.ventanaAbiertaPruebas)
		// El campo del diálogo de Tiresias: se escribe en él como lo haría un teclado (Backtalk 225)
		val raices = s.windows.mapNotNull { it.root }
		val campo = raices.firstNotNullOfOrNull { r -> buscar(r) { it.isEditable } }
		assertNotNull("El diálogo de Buscar no tiene campo de texto", campo)
		campo!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Recordarme") })
		val aceptar = raices.firstNotNullOfOrNull { r -> r.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull() }
		assertNotNull("El diálogo de Buscar no tiene botón de aceptar", aceptar)
		val m = marca()
		aceptar!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
		assertNotNull("Buscar no llevó a lo escrito", dichoQue(5000, m) { it.startsWith("Recordarme") })
	}

	private fun buscar(n: AccessibilityNodeInfo, cond: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
		if (cond(n)) return n
		for (i in 0 until n.childCount) n.getChild(i)?.let { h -> buscar(h, cond)?.let { return it } }
		return null
	}

	@Test
	fun elEstadoDiceLoElegidoEnSuOrden() {
		ajuste("partes_estado", "TIMBRE,CONEXION,ESPACIO")
		val dicho = hacer(Accion.ESTADO_SISTEMA, 800)
		assertTrue("El estado no empieza por el timbre: «$dicho»", dicho.startsWith("timbre"))
		assertTrue("Falta la conexión: «$dicho»", dicho.contains("conectado") || dicho.contains("sin conexión"))
		assertTrue("Falta el espacio libre: «$dicho»", dicho.contains("libres"))
		assertFalse("Dice la batería sin haberla elegido: «$dicho»", dicho.contains("batería"))
	}

	@Test
	fun elAvisoDePausaYReanudar() {
		if (Build.VERSION.SDK_INT >= 33) inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(NotificationManager::class.java)
		s.ejecutarPrueba(Accion.SUSPENDER)
		SystemClock.sleep(1000)
		try {
			assertTrue("No salió el aviso de pausa", nm.activeNotifications.any { it.id == 7301 })
			// «Reanudar» de la notificación
			app.sendBroadcast(Intent(AvisoPausa.REANUDAR).setPackage(app.packageName))
			SystemClock.sleep(1000)
			assertTrue("Reanudar no quitó el aviso", nm.activeNotifications.none { it.id == 7301 })
		} finally {
			// Por si algo falló: que Tiresias no se quede en pausa
			if (nm.activeNotifications.any { it.id == 7301 }) s.ejecutarPrueba(Accion.SUSPENDER)
		}
	}

	@Test
	fun losIdentificadoresEnElIdiomaDeTiresias() {
		val es = DatosIdioma.de(app, Locale("es", "ES")).identificadores
		assertEquals("foto de contacto", es.legible("com.whatsapp:id/contact_photo"))
		assertEquals("foto de contacto", es.legible("com.whatsapp:id/contact_photo_view"))
		assertNull("«entry» no dice nada", es.legible("com.whatsapp:id/entry"))
		assertEquals("enviar mensaje", es.legible("com.app:id/btnSendMessage"))
		val en = DatosIdioma.de(app, Locale.ENGLISH).identificadores
		assertEquals("contact photo", en.legible("com.whatsapp:id/contact_photo_view"))
	}

	@Test
	fun laTiendaAceptaFuentesDeOtrosSitios() {
		assertEquals("https://raw.githubusercontent.com/a/b/main/tienda", Tienda.normalizarFuente("https://github.com/a/b/tree/main/tienda"))
		assertEquals("https://ejemplo.es/complementos", Tienda.normalizarFuente("https://ejemplo.es/complementos/catalogo.json"))
		assertNull("Sin https no vale", Tienda.normalizarFuente("http://ejemplo.es"))
		val temas = Tienda.leerTemas("""{"temas":[{"id":"suave","nombre":"Suave","archivo":"suave.zip","sha256":"AB","tamano":3},{"id":"malo","archivo":"../x.zip"}]}""")
		assertEquals(listOf("suave"), temas.map { it.id })
	}

	@Test
	fun lasPersonasConocidas() {
		val antes = Personas.lista(app).map { it.id }.toSet()
		val foto = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
		val bytes = ByteArrayOutputStream().also { foto.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
		val p = Personas.anadir(app, "Ana Prueba", ByteArrayInputStream(bytes))
		try {
			assertTrue(Personas.lista(app).any { it.nombre == "Ana Prueba" })
			val hoja = Personas.hojaDePersonas(app)
			assertNotNull("No se hizo la hoja de personas", hoja)
			assertTrue(Personas.instruccion(app, false).contains("Ana Prueba"))
			// Con la IA de internet, sus fotos no se mandan si no se ha permitido
			assertFalse(Personas.activas(app, com.xebolax.tiresias.ia.AsistenteIA.Proveedor.CLAUDE))
			assertTrue(Personas.activas(app, com.xebolax.tiresias.ia.AsistenteIA.Proveedor.DESCARGADO))
		} finally {
			if (p.id !in antes) Personas.borrar(app, p.id)
		}
		assertTrue(Personas.lista(app).none { it.nombre == "Ana Prueba" })
	}
}
