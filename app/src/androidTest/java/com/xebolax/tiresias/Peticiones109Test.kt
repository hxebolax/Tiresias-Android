package com.xebolax.tiresias

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.arbol.Describir
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.braille.Braille
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.perfiles.Perfiles
import com.xebolax.tiresias.voz.VocesDisponibles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que contaron los usuarios de la 1.0.8: el teclado braille que no se veía
 * activado, la foto de los chats de WhatsApp leída con la fila, Chrome que se
 * iba a la barra de direcciones con los anuncios, los gestos de una sola app,
 * el menú circular en arriba y a la derecha y la voz que use el sistema.
 */
@RunWith(AndroidJUnit4::class)
class Peticiones109Test : PruebaTiresias() {
	private val muestrario = "com.xebolax.tiresias.muestrario"

	private fun abrir(clase: String) {
		val m = marca()
		app.startActivity(Intent().setClassName(muestrario, "$muestrario.$clase").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
		esperar(10_000) { s.lectorPruebas?.raiz()?.takeIf { it.packageName == muestrario && it.childCount > 0 } }
		esperarCalma(m)
	}

	/** El teclado braille activado se ve en las dos formas en que Android guarda su identificador (y con subtipos). */
	@Test
	fun elTecladoBrailleActivadoSeVeEnFormaCorta() {
		val publicado = ComponentName("com.xebolax.tiresias", "com.xebolax.tiresias.braille.TecladoBraille")
		assertTrue(Braille.estaEnLista("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME:com.xebolax.tiresias/.braille.TecladoBraille", publicado))
		assertTrue(Braille.estaEnLista("com.xebolax.tiresias/.braille.TecladoBraille;-921088104;529847764", publicado))
		assertTrue(Braille.estaEnLista("com.xebolax.tiresias/com.xebolax.tiresias.braille.TecladoBraille", publicado))
		assertFalse(Braille.estaEnLista("com.samsung.android.honeyboard/.service.HoneyBoardService", publicado))
		assertFalse(Braille.estaEnLista("", publicado))
		val pruebas = ComponentName("com.xebolax.tiresias.pruebas", "com.xebolax.tiresias.braille.TecladoBraille")
		assertTrue(Braille.estaEnLista("com.xebolax.tiresias.pruebas/com.xebolax.tiresias.braille.TecladoBraille", pruebas))
		assertFalse(Braille.estaEnLista("com.xebolax.tiresias/.braille.TecladoBraille", pruebas))
	}

	/** En una lista como la de WhatsApp, la foto es su propia parada y la fila no la lee. */
	@Test
	fun laFotoDeUnChatEsSuPropiaParada() {
		abrir("ActividadContactos")
		val dichos = arrayListOf(hacer(Accion.PRIMERO, 200))
		repeat(5) { dichos += hacer(Accion.SIGUIENTE, 200) }
		val todo = dichos.joinToString(" | ")
		assertTrue("La fila de Ana no es una parada: $todo", dichos.any { it.startsWith("Ana Pérez") })
		assertTrue("La foto de Ana no es una parada: $todo", dichos.any { it.startsWith("Foto de Ana Pérez") })
		assertFalse("La fila se lee con la foto: $todo", dichos.any { "Foto de" in it && "Hola" in it })
		// Como en WhatsApp: ni la lista (con nombre y mantenible) es una parada, ni el marco pulsable de la foto, que
		// sería una parada muda o repetida
		assertFalse("La lista es una parada: $todo", dichos.any { it.startsWith("Lista de chats") })
		assertEquals("La foto de Ana sale más de una vez: $todo", 1, dichos.count { it.startsWith("Foto de Ana Pérez") })
		assertFalse("Hay una parada sin nada: $todo", dichos.any { it.isBlank() || it.startsWith("Sin nombre") || it.startsWith("Botón") })
	}

	/** En «Reenviar a» de WhatsApp, cada contacto y cada encabezado es su propia parada, no la lista entera de una vez. */
	@Test
	fun reenviarLeeCadaContactoYLosEncabezados() {
		abrir("ActividadReenviar")
		val dichos = arrayListOf(hacer(Accion.PRIMERO, 200))
		repeat(9) { dichos += hacer(Accion.SIGUIENTE, 200) }
		val todo = dichos.joinToString(" | ")
		assertTrue("No dijo el encabezado: $todo", dichos.any { it.startsWith("Chats frecuentes") })
		assertTrue("No dijo el segundo encabezado: $todo", dichos.any { it.startsWith("Chats recientes") })
		for (nombre in listOf("Ana Pérez", "Benito Ruiz", "Daniel Gil")) assertTrue("«$nombre» no es una parada: $todo", dichos.any { it.startsWith(nombre) })
		assertFalse("Se leyó la lista de una vez: $todo", dichos.any { "Ana Pérez" in it && "Benito Ruiz" in it })
	}

	/** La lectura continua de un artículo con anuncios que se rehacen no se va a la barra de herramientas. */
	@Test
	fun laLecturaContinuaNoSeVaALaBarra() {
		abrir("ActividadPeriodico")
		SystemClock.sleep(1500)
		// Al primer párrafo
		var dicho = hacer(Accion.PRIMERO, 200)
		var pasos = 0
		while (!dicho.startsWith("Párrafo uno") && pasos++ < 8) dicho = hacer(Accion.SIGUIENTE, 200)
		assertTrue("No se llegó al primer párrafo: «$dicho»", dicho.startsWith("Párrafo uno"))
		val m = marca()
		s.ejecutarPrueba(Accion.LEER_DESDE_AQUI)
		val cinco = dichoQue(25_000, m) { it.startsWith("Párrafo cinco") }
		val leido = s.vozPruebas!!.dichoDesde(m).reversed()
		s.ejecutarPrueba(Accion.PAUSAR_VOZ)
		val todo = leido.joinToString(" | ")
		assertFalse("La lectura se fue a la barra: $todo", leido.any { it.startsWith("Inicio") || it.startsWith("Conexión segura") || it.startsWith("Titular") })
		assertNotNull("La lectura no llegó al párrafo cinco: $todo", cinco)
	}

	/** Si el anuncio en el que estaba el foco se rehace, el siguiente es lo que va detrás, no el principio. */
	@Test
	fun unAnuncioQueSeRehaceNoLlevaAlPrincipio() {
		abrir("ActividadPeriodico")
		SystemClock.sleep(1500)
		var dicho = hacer(Accion.PRIMERO, 200)
		var pasos = 0
		while (!dicho.startsWith("Anuncio") && pasos++ < 10) dicho = hacer(Accion.SIGUIENTE, 200)
		assertTrue("No se llegó al anuncio: «$dicho»", dicho.startsWith("Anuncio"))
		// El anuncio se rehace cada 700 ms
		SystemClock.sleep(1600)
		val siguiente = hacer(Accion.SIGUIENTE, 300)
		assertTrue("Tras rehacerse el anuncio se fue a «$siguiente»", siguiente.startsWith("Párrafo tres"))
		// Y el marco que se vuelve a cargar
		dicho = siguiente
		pasos = 0
		while (!dicho.startsWith("Publicidad") && pasos++ < 4) dicho = hacer(Accion.SIGUIENTE, 200)
		if (dicho.startsWith("Publicidad")) {
			SystemClock.sleep(1600)
			val trasMarco = hacer(Accion.SIGUIENTE, 300)
			assertFalse("Tras rehacerse el marco se fue a «$trasMarco»", trasMarco.startsWith("Inicio") || trasMarco.startsWith("Conexión segura") || trasMarco.startsWith("Titular"))
		}
	}

	/** Los gestos de una app valen en ella y solo en ella. */
	@Test
	fun losGestosDeUnaAppSoloValenEnElla() {
		val perfiles = Perfiles(app)
		try {
			perfiles.ponerApp(muestrario, Perfiles.PerfilApp(gestos = mapOf(Gesto.ARRIBA to Accion.ESTADO_SISTEMA)))
			s.recargarAjustes()
			abrir("ActividadContactos")
			val m = marca()
			s.gestoPrueba(Gesto.ARRIBA)
			assertNotNull("Arriba no hizo lo de esta app", dichoQue(3000, m) { it.contains("batería al") })
		} finally {
			perfiles.ponerApp(muestrario, null)
			s.recargarAjustes()
		}
	}

	/** Arriba y a la derecha abre el menú circular; abajo y a la derecha, el menú de Tiresias. */
	@Test
	fun arribaYDerechaEsElMenuCircular() {
		assertEquals(Accion.MENU_CIRCULAR, MapaGestos.POR_DEFECTO[Gesto.ARRIBA_DERECHA])
		assertEquals(Accion.MENU_TIRESIAS, MapaGestos.POR_DEFECTO[Gesto.ABAJO_DERECHA])
	}

	/** «La que use el sistema» es la primera voz de la lista, y con ella se sigue hablando. */
	@Test
	fun laVozDelSistemaSeOfreceYHabla() {
		assertEquals(Ajustes.MOTOR_DEL_SISTEMA, VocesDisponibles.disponibles(app).first().first)
		ajuste("motor", Ajustes.MOTOR_DEL_SISTEMA)
		abrir("ActividadContactos")
		val m = marca()
		s.ejecutarPrueba(Accion.PRIMERO)
		assertNotNull("Con la voz del sistema no se dijo nada", dichoQue(6000, m) { it.isNotBlank() })
	}

	/** Un botón de vídeo de Telegram también se mantiene pulsado; una descripción de relleno deja paso al texto. */
	@Test
	fun botonesDeGrabarYNombresDeRelleno() {
		for (nombre in listOf("Mensaje de vídeo", "Video message", "Grabar mensaje de video")) {
			assertTrue("«$nombre» es un botón de grabar", Nodos.esDeMantenerPulsado(AccessibilityNodeInfo().apply { isClickable = true; contentDescription = nombre }))
		}
		for (nombre in listOf("Task no name", "tarea no name", "null", "Unknown")) assertTrue(nombre, Nodos.esNombreDeRelleno(nombre))
		for (nombre in listOf("WhatsApp", "Notas", "Sin nombre de usuario aún")) assertFalse(nombre, Nodos.esNombreDeRelleno(nombre))
		val tarea = AccessibilityNodeInfo().apply {
			className = "android.widget.FrameLayout"
			contentDescription = "Task no name"
			text = "WhatsApp"
		}
		assertEquals("WhatsApp", Describir().elemento(tarea).nombre)
	}

	/** La estructura de la pantalla no lleva ningún texto de ella. */
	@Test
	fun laEstructuraNoLlevaTextos() {
		abrir("ActividadContactos")
		val l = s.lectorPruebas!!
		val raiz = l.raiz()!!
		val (texto, nodos) = Nodos.estructura(raiz, emptySet(), l.foco, 1500)
		assertTrue("Sin nodos", nodos > 5)
		assertTrue("Sin la lista: $texto", "RecyclerView" in texto)
		for (palabra in listOf("Ana", "Foto", "Hola", "13.10")) assertFalse("La estructura lleva «$palabra»", palabra in texto)
		val m = marca()
		s.ejecutarPrueba(Accion.ESTRUCTURA_PANTALLA)
		assertNotNull(dichoQue(3000, m) { it.startsWith("Copiada la estructura") })
	}

	/** Al apagar la pantalla, la pantalla de bloqueo no hace leer su primera notificación (solo se dice lo de Tiresias). */
	@Test
	fun alApagarNoSeLeeLaNotificacionDeLaPantallaDeBloqueo() {
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		if (android.os.Build.VERSION.SDK_INT >= 33) ua.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		val nm = app.getSystemService(android.app.NotificationManager::class.java)
		if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("pruebas", "Pruebas", android.app.NotificationManager.IMPORTANCE_HIGH))
		val n = (if (android.os.Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(app, "pruebas") else @Suppress("DEPRECATION") android.app.Notification.Builder(app))
			.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Correo de Luisa").setContentText("Mañana a las diez").build()
		nm.notify(4321, n)
		SystemClock.sleep(3000)
		try {
			val m = marca()
			ua.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
			SystemClock.sleep(5000)
			val dicho = s.vozPruebas!!.dichoDesde(m)
			assertFalse("Con la pantalla apagada se leyó la notificación: $dicho", dicho.any { "Luisa" in it || "diez" in it })
		} finally {
			ua.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
			SystemClock.sleep(800)
			ua.executeShellCommand("wm dismiss-keyguard").close()
			SystemClock.sleep(800)
			nm.cancel(4321)
		}
	}
}
