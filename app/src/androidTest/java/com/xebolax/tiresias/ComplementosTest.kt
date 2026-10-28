package com.xebolax.tiresias

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.tiresias.nucleo.Elemento
import com.xebolax.tiresias.nucleo.Rol
import com.xebolax.tiresias.scripts.Scripts
import com.xebolax.tiresias.scripts.Tienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los complementos de la tienda (carpeta `complementos/` del proyecto), uno a
 * uno, con el motor QuickJS de Tiresias: que se cargan y que hacen lo que dice
 * su descripción. Cada prueba instala solo el suyo y lo quita al acabar.
 */
@RunWith(AndroidJUnit4::class)
class ComplementosTest {
	private val inst = InstrumentationRegistry.getInstrumentation()
	private val scripts = Scripts(null, inst.targetContext)

	private fun codigo(id: String) = inst.context.assets.open("$id/complemento.js").bufferedReader().use { it.readText() }

	private fun <T> con(id: String, prueba: () -> T): T {
		val antes = scripts.instalados().map { it.id }.toSet()
		val info = scripts.instalar(codigo(id))
		assertEquals(id, info.id)
		// Solo este, para que no se mezclen
		val otros = scripts.instalados().map { it.id }.filter { it != id }
		otros.forEach { scripts.activar(it, false) }
		try {
			return prueba()
		} finally {
			otros.forEach { scripts.activar(it, true) }
			if (id !in antes) scripts.desinstalar(id)
		}
	}

	/** Lo que han pedido los complementos (método y valor), desde que empieza cada prueba. */
	private val pedidos = java.util.Collections.synchronizedList(ArrayList<Pair<String, String>>())

	init {
		scripts.espia = { _, metodo, valor -> pedidos += metodo to valor }
	}

	/** Lo que se espera está en español (el emulador está en inglés). */
	@org.junit.Before
	fun enEspanol() {
		if (com.xebolax.tiresias.base.Idioma.elegido(inst.targetContext) != "es") com.xebolax.tiresias.base.Idioma.cambiar(inst.targetContext, "es")
		pedidos.clear()
	}

	private fun dicho(): List<String> = pedidos.filter { it.first == "decir" || it.first == "decirEnCola" }.map { it.second }

	@Test
	fun todosSeCarganConSuIdYSusAjustes() {
		val ids = inst.context.assets.list("").orEmpty().filter { runCatching { inst.context.assets.open("$it/complemento.js").close() }.isSuccess }
		assertTrue("Debe haber complementos: $ids", ids.size >= 6)
		for (id in ids) {
			val info = scripts.leerManifiesto(codigo(id))
			assertEquals(id, info.id)
			assertTrue("$id sin descripción", info.descripcion.isNotBlank())
			assertTrue("$id sin ajustes", info.ajustes.isNotEmpty())
			// Cada ajuste con nombre y, las listas, con opciones
			info.ajustes.forEach { a -> assertTrue("$id: ${a.clave} sin nombre", a.nombre.isNotBlank() && (a.tipo != "lista" || a.opciones.isNotEmpty())) }
		}
	}

	@Test
	fun codigosDeSeguridad() = con("codigos-de-verificacion") {
		val r = scripts.alNotificar("com.google.android.apps.messaging", "Google", "G-123456 es tu código de verificación de Google.",
			mapOf("nombreApp" to "Mensajes", "sms" to true, "seLee" to true, "bloqueado" to false))
		assertTrue(r, r!!.startsWith("Código 1 2 3 4 5 6, copiado. Mensajes."))
		// Se copia sin la «G-» y como dato sensible
		assertTrue(pedidos.toString(), pedidos.contains("portapapelesSensible" to "123456"))
		val compra = scripts.alNotificar("com.banco", "Banco", "Compra de 45,90 € con la tarjeta terminada en 1234. Código: 7788")
		assertTrue(compra, compra!!.startsWith("Código 7 7 8 8"))
		assertNull(scripts.alNotificar("com.whatsapp", "Ana", "Nos vemos a las 1830"))
		assertNull(scripts.alNotificar("com.tienda", "Pedido", "Tu pedido 12345678 se ha enviado"))
		// La app callada: no se lee, pero el complemento avisa del código
		pedidos.clear()
		assertNull(scripts.alNotificar("com.banco", "Banco", "Tu clave es 4455", mapOf("seLee" to false, "nombreApp" to "Banco")))
		assertTrue(dicho().toString(), dicho().any { it.startsWith("Código 4 4 5 5, copiado, de Banco") })
		// Las órdenes con el último código
		pedidos.clear()
		scripts.ejecutarOrden("codigos-de-verificacion", "Deletrear el último código")
		assertEquals(listOf("4, 4, 5, 5"), dicho())
	}

	@Test
	fun losAjustesDeUnComplementoCambianLoQueHace() = con("codigos-de-verificacion") {
		val info = scripts.instalados().first { it.id == "codigos-de-verificacion" }
		val deletreo = info.ajustes.first { it.clave == "deletreo" }
		assertEquals("cifras", scripts.valorAjuste(info.id, deletreo))
		scripts.ponerAjuste(info.id, deletreo, "pares")
		try {
			val r = scripts.alNotificar("com.banco", "Banco", "Tu código es 482913")
			assertTrue(r, r!!.startsWith("Código 48, 29, 13"))
			val excluidas = info.ajustes.first { it.clave == "excluidas" }
			scripts.ponerAjuste(info.id, excluidas, "com.banco,com.otro")
			assertNull(scripts.alNotificar("com.banco", "Banco", "Tu código es 482913"))
		} finally {
			scripts.restablecerAjustes(info.id)
		}
		assertEquals("cifras", scripts.valorAjuste(info.id, deletreo))
	}

	@Test
	fun notificacionesAMedida() = con("notificaciones-a-medida") {
		val n = { app: String, titulo: String, texto: String -> scripts.alNotificar(app, titulo, texto, mapOf("nombreApp" to app.substringAfterLast('.'))) }
		assertEquals("whatsapp. Ana. Hola", n("com.whatsapp", "Ana", "Hola"))
		// La misma otra vez, callada
		assertEquals("", n("com.whatsapp", "Ana", "Hola"))
		// Las descargas: no cada porcentaje
		assertEquals("descargas. Bajando foto.jpg. 10 %", n("com.descargas", "Bajando foto.jpg", "10 %"))
		assertEquals("", n("com.descargas", "Bajando foto.jpg", "12 %"))
		// Una ráfaga: a partir de la quinta, calladas
		val rafaga = (1..7).map { n("com.grupo", "Grupo", "Mensaje $it") }
		assertEquals(rafaga.toString(), 3, rafaga.count { it == "" })
		// Importantes
		val info = scripts.instalados().first { it.id == "notificaciones-a-medida" }
		scripts.ponerAjuste(info.id, info.ajustes.first { it.clave == "importantes" }, "mamá, urgente")
		try {
			assertEquals("Importante. whatsapp. Mama. Llámame", n("com.whatsapp", "Mama", "Llámame"))
		} finally {
			scripts.restablecerAjustes(info.id)
		}
	}

	@Test
	fun relojBateriaYDatos() {
		con("reloj-parlante") {
			val ordenes = scripts.ordenes("com.app").map { it.second }
			assertTrue(ordenes.toString(), "Decir la hora" in ordenes && "Temporizador: empezar" in ordenes)
			pedidos.clear()
			scripts.ejecutarOrden("reloj-parlante", "Decir la hora")
			assertTrue(dicho().toString(), dicho().single().let { it.startsWith("Son las ") || it.startsWith("Es la ") })
			pedidos.clear()
			scripts.ejecutarOrden("reloj-parlante", "Decir la fecha completa")
			assertTrue(dicho().toString(), dicho().single().contains(" de ") && dicho().single().contains("Semana"))
		}
		con("vigilante-de-bateria") {
			pedidos.clear()
			scripts.ejecutarOrden("vigilante-de-bateria", "Estado de la batería")
			assertTrue(dicho().toString(), dicho().single().startsWith("Batería al "))
		}
		con("datos-del-texto") {
			val e = Elemento(nombre = "", texto = "Llámame al 600 111 222 o escribe a ana@correo.es")
			val nombres = scripts.acciones("com.app", e).map { it.third }
			assertEquals(listOf("Copiar el teléfono 600 111 222", "Llamar a 600 111 222", "Copiar el correo ana@correo.es", "Escribir a ana@correo.es"), nombres)
			assertTrue(scripts.acciones("com.app", Elemento(nombre = "Hola, ¿qué tal?")).isEmpty())
		}
		con("lectura-limpia") {
			val e = Elemento(nombre = "Llama al 600111222")
			assertEquals("Llama al 600 111 222", scripts.alEnfocar("com.app", e, "Llama al 600111222"))
			assertEquals("Mira enlace a amazon.es", scripts.alEnfocar("com.app", e, "Mira https://www.amazon.es/dp/B0CX12345/ref=sr_1_1?x=1"))
			assertNull(scripts.alEnfocar("com.app", e, "Nada que limpiar"))
		}
	}

	/** Los complementos de la 1.0.4, con QuickJS de verdad (sin el lector: lo que lo necesita no contesta). */
	@Test
	fun losComplementosNuevos() {
		con("letras-decorativas") {
			assertEquals("Hola Mundo, botón", scripts.alEnfocar("com.app", Elemento(nombre = "x"), "𝐇𝐨𝐥𝐚 𝐌𝐮𝐧𝐝𝐨, botón"))
			assertEquals("hola", scripts.alEnfocar("com.app", Elemento(nombre = "x"), "ⓗⓞⓛⓐ"))
			assertNull(scripts.alEnfocar("com.app", Elemento(nombre = "x"), "Superficie: 20 m²"))
		}
		con("anuncios") {
			assertEquals("Anuncio, Zapatillas, botón", scripts.alEnfocar("com.app", Elemento(nombre = "x"), "Patrocinado · Zapatillas, botón"))
			assertEquals("", scripts.alNotificar("com.tienda", "50 %", "Oferta", mapOf("categoria" to "promo")))
			assertNull(scripts.alEnfocar("com.app", Elemento(nombre = "x"), "Mensaje de Ana"))
		}
		con("llamadas") {
			assertNull(scripts.alNotificar("com.whatsapp", "Ana", "Llamada", mapOf("categoria" to "call", "nombreApp" to "WhatsApp", "seLee" to true)))
		}
		con("recordatorios") {
			val ordenes = scripts.ordenes("com.app").map { it.second }
			assertTrue(ordenes.toString(), ordenes.containsAll(listOf("Nuevo recordatorio", "Mis recordatorios", "Recordatorio hecho", "Recordatorios que quedan hoy")))
		}
		con("version-de-la-app") {
			pedidos.clear()
			scripts.ejecutarOrden("version-de-la-app", "Qué app es esta")
			assertEquals(listOf("No sé qué app es esta"), dicho())
		}
		// Lo de un campo de contraseña nunca llega a un complemento
		con("lectura-limpia") {
			assertNull(scripts.alEnfocar("com.app", Elemento(nombre = "x", contrasena = true), "Llama al 600111222"))
			assertTrue(scripts.acciones("com.app", Elemento(nombre = "", texto = "600 111 222", contrasena = true)).isEmpty())
		}
		con("luz") {
			assertTrue(scripts.ordenes("com.app").map { it.second }.containsAll(listOf("Cuánta luz hay", "Detector de luz: empezar o parar")))
		}
	}

	/** Un complemento que pide una API más nueva no se instala, la tienda no lo ofrece, y todos ven la versión. */
	@Test
	fun laVersionDeLaApi() {
		val futuro = """export const manifest = { id: "del-futuro", nombre: "Del futuro", version: "1.0", descripcion: "d", apps: ["*"], permisos: [], apiMinima: 99 };"""
		val error = runCatching { scripts.leerManifiesto(futuro) }.exceptionOrNull()
		assertTrue("Se aceptó un complemento que pide la API 99", error != null && error.message.orEmpty().contains("más nueva"))
		val catalogo = """{"formato":1,"complementos":[{"id":"del-futuro","nombre":"F","version":"1.0","archivo":"del-futuro.js","apiMinima":99},{"id":"de-hoy","nombre":"H","version":"1.0","archivo":"de-hoy.js","apiMinima":2}]}"""
		assertEquals(listOf("de-hoy"), Tienda.leer(catalogo).map { it.id })
		val actual = """export const manifest = { id: "version-api", nombre: "V", version: "1.0", descripcion: "d", apps: ["*"], permisos: ["voz"], ajustes: [{ clave: "x", tipo: "si_no", defecto: true, nombre: "X" }] };
tiresias.orden("Versión", () => tiresias.decir("API " + tiresias.versionApi));"""
		scripts.instalar(actual)
		try {
			pedidos.clear()
			scripts.ejecutarOrden("version-api", "Versión")
			assertEquals(listOf("API ${Scripts.VERSION_API}"), dicho())
		} finally {
			scripts.desinstalar("version-api")
		}
	}

	/** La tienda publicada de verdad en el servidor de Tiresias (necesita Internet en el emulador). */
	@Test
	fun laTiendaDelServidor() = kotlinx.coroutines.runBlocking {
		val c = inst.targetContext
		val catalogo = Tienda.catalogo(c)
		assertTrue("La tienda del servidor está vacía", catalogo.isNotEmpty())
		// Cada uno se descarga, se comprueba su huella y es un complemento que el motor acepta
		for (e in catalogo) assertEquals(e.id, scripts.leerManifiesto(Tienda.descargar(c, e)).id)
	}

	@Test
	fun catalogoDeLaTienda() {
		val json = """{"formato":1,"complementos":[
			{"id":"hora-y-fecha","nombre":"Hora y fecha","version":"1.2","descripcion":"d","autor":"a","apps":["*"],"permisos":["voz"],"archivo":"hora-y-fecha.js","sha256":"AB","tamano":10},
			{"id":"../malo","archivo":"x.js"},
			{"id":"otro","archivo":"../../fuera.js"}]}"""
		val lista = Tienda.leer(json)
		assertEquals(1, lista.size)
		// El nombre en el idioma de Tiresias, si el catálogo lo trae
		val conIngles = json.replace("\"nombre\":\"Hora y fecha\"", "\"nombre\":\"Hora y fecha\",\"nombre_en\":\"Time and date\"")
		assertEquals("Time and date", Tienda.leer(conIngles, "en")[0].nombre)
		assertEquals("Hora y fecha", Tienda.leer(conIngles, "es")[0].nombre)
		assertEquals("ab", lista[0].sha256)
		assertTrue(Tienda.esNueva(lista[0], "1.0"))
		assertTrue(!Tienda.esNueva(lista[0], "1.2") && !Tienda.esNueva(lista[0], null))
	}
}
