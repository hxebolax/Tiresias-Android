package com.xebolax.tiresias.nucleo.remoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NvdaTest {
	@Test
	fun mensajesQueSeMandan() {
		assertEquals("{\"type\":\"protocol_version\",\"version\":2}", Nvda.versionProtocolo())
		assertEquals("{\"type\":\"join\",\"channel\":\"clave secreta\",\"connection_type\":\"master\"}", Nvda.unirse("clave secreta", controla = true))
		assertEquals("{\"type\":\"join\",\"channel\":\"k\",\"connection_type\":\"slave\"}", Nvda.unirse("k", controla = false))
		assertEquals("{\"type\":\"key\",\"vk_code\":65,\"scan_code\":0,\"extended\":false,\"pressed\":true}", Nvda.tecla(0x41, 0, extendida = false, pulsada = true))
		assertEquals("{\"type\":\"set_clipboard_text\",\"text\":\"línea\\notra\"}", Nvda.portapapeles("línea\notra"))
		assertEquals("{\"type\":\"send_SAS\"}", Nvda.ctrlAltSupr())
	}

	@Test
	fun braille() {
		// Puntos 1, 2 y 5 = «h»
		val h = Json.objeto(Nvda.puntosBraille(0b10011, espacio = false))!!
		assertEquals("braille_input", h.texto("type"))
		assertEquals(19L, h.entero("dots"))
		assertEquals(false, h.logico("space"))
		assertEquals("dot1+dot2+dot5", h.texto("id"))
		assertEquals("tiresias", h.texto("source"))
		val espacio = Json.objeto(Nvda.puntosBraille(0, espacio = true))!!
		assertEquals(0L, espacio.entero("dots"))
		assertEquals(true, espacio.logico("space"))
		assertEquals("space", espacio.texto("id"))
		val orden = Json.objeto(Nvda.ordenBraille("braille_scrollForward"))!!
		assertEquals(listOf("globalCommands", "GlobalCommands", "braille_scrollForward"), orden.lista("scriptPath"))
		assertEquals("braille_input", orden.texto("type"))
	}

	@Test
	fun combinacionModificadoresPrimeroYSoltarAlReves() {
		val lineas = Nvda.combinacion(listOf(TeclaVk.T, TeclaVk.CONTROL, TeclaVk.MAYUSCULAS)).map { Json.objeto(it)!! }
		val esperado = listOf(
			0x11L to true, 0x10L to true, 0x54L to true,
			0x54L to false, 0x10L to false, 0x11L to false,
		)
		assertEquals(esperado, lineas.map { it.entero("vk_code")!! to it.logico("pressed")!! })
		assertEquals(listOf(0L), lineas.map { it.entero("scan_code") }.distinct())
		// NVDA+T: Insertar del teclado numérico, no extendida
		val nvda = Nvda.combinacion(listOf(TeclaVk.NVDA, TeclaVk.T)).map { Json.objeto(it)!! }
		assertEquals(0x2DL, nvda[0].entero("vk_code"))
		assertEquals(false, nvda[0].logico("extended"))
		val flecha = Json.objeto(Nvda.combinacion(listOf(TeclaVk.ABAJO))[0])!!
		assertEquals(true, flecha.logico("extended"))
	}

	@Test
	fun teclasVirtuales() {
		assertEquals(TeclaVk.A, TeclaVk.deLetra('a'))
		assertEquals(TeclaVk.Z, TeclaVk.deLetra('Z'))
		assertEquals(TeclaVk.N0, TeclaVk.deLetra('0'))
		assertEquals(TeclaVk.N9, TeclaVk.deLetra('9'))
		assertNull(TeclaVk.deLetra('ñ'))
		assertNull(TeclaVk.deLetra(' '))
		for (c in 'a'..'z') assertEquals(c.uppercaseChar().code, TeclaVk.deLetra(c)!!.vk)
		for (c in '0'..'9') assertEquals(c.code, TeclaVk.deLetra(c)!!.vk)
		assertEquals(0x7B, TeclaVk.F12.vk)
	}

	@Test
	fun hablarConCambioDeIdioma() {
		// Como lo manda NVDA 2025.1 (con el origin que añade el servidor)
		val linea = "{\"sequence\": [\"Documento\", [\"LangChangeCommand\", {\"lang\": \"en_GB\", \"isDefault\": false}], \"Hello\", " +
			"[\"LangChangeCommand\", {\"lang\": null}], [\"CharacterModeCommand\", {\"state\": true}], \"ab\", " +
			"[\"CharacterModeCommand\", {\"state\": false}], [\"BreakCommand\", {\"time\": 100}], [\"PitchCommand\", {\"offset\": 30}], " +
			"[\"EndUtteranceCommand\", {}], \"fin\"], \"priority\": 0, \"type\": \"speak\", \"origin\": 3}\n"
		val m = Nvda.leer(linea) as MensajeNvda.Hablar
		assertEquals(
			listOf(
				ParteVoz.Texto("Documento"), ParteVoz.Idioma("en_GB"), ParteVoz.Texto("Hello"), ParteVoz.Idioma(null),
				ParteVoz.Deletrear(true), ParteVoz.Texto("ab"), ParteVoz.Deletrear(false), ParteVoz.Pausa,
				ParteVoz.Pausa, ParteVoz.Texto("fin"),
			),
			m.partes,
		)
		assertEquals(0, m.prioridad)
		assertEquals(3L, m.origen)
	}

	@Test
	fun hablarConLimites() {
		val muchas = (1..5000).joinToString(",") { "\"x\"" }
		assertEquals(Nvda.MAXIMO_PARTES, (Nvda.leer("{\"type\":\"speak\",\"sequence\":[$muchas]}") as MensajeNvda.Hablar).partes.size)
		val largo = "a".repeat(30000)
		val m = Nvda.leer("{\"type\":\"speak\",\"sequence\":[\"$largo\",\"$largo\",\"b\"]}") as MensajeNvda.Hablar
		assertEquals(Nvda.MAXIMO_CARACTERES, m.partes.sumOf { (it as ParteVoz.Texto).texto.length })
		assertEquals(2, m.partes.size)
	}

	@Test
	fun canalUnidoVersion1Y2() {
		// Protocolo 1 (servidor viejo): solo user_ids
		assertEquals(
			MensajeNvda.CanalUnido(listOf(ClienteNvda(4, ""), ClienteNvda(7, ""))),
			Nvda.leer("{\"type\": \"channel_joined\", \"channel\": \"abc\", \"user_ids\": [4, 7]}"),
		)
		// Protocolo 2: clients con su tipo (y user_ids por compatibilidad)
		assertEquals(
			MensajeNvda.CanalUnido(listOf(ClienteNvda(4, "slave"))),
			Nvda.leer("{\"type\": \"channel_joined\", \"channel\": \"abc\", \"user_ids\": [4], \"clients\": [{\"id\": 4, \"connection_type\": \"slave\"}]}"),
		)
		assertEquals(MensajeNvda.CanalUnido(emptyList()), Nvda.leer("{\"type\":\"channel_joined\",\"channel\":\"abc\",\"user_ids\":[],\"clients\":[]}"))
	}

	@Test
	fun clientesQueEntranYSalen() {
		assertEquals(
			MensajeNvda.ClienteEntra(ClienteNvda(9, "master")),
			Nvda.leer("{\"type\":\"client_joined\",\"origin\":9,\"user_id\":9,\"client\":{\"id\":9,\"connection_type\":\"master\"}}"),
		)
		assertEquals(MensajeNvda.ClienteSale(ClienteNvda(9, "")), Nvda.leer("{\"type\":\"client_left\",\"user_id\":9}"))
		assertEquals(MensajeNvda.Otro("client_left"), Nvda.leer("{\"type\":\"client_left\"}"))
	}

	@Test
	fun otrosMensajes() {
		assertEquals(MensajeNvda.Callar(2), Nvda.leer("{\"type\":\"cancel\",\"origin\":2}"))
		assertEquals(MensajeNvda.Callar(null), Nvda.leer("{\"type\":\"cancel\"}"))
		assertEquals(MensajeNvda.PausarVoz(true), Nvda.leer("{\"type\":\"pause_speech\",\"switch\":true}"))
		assertEquals(MensajeNvda.Tono(440, 100), Nvda.leer("{\"type\":\"tone\",\"hz\":440.0,\"length\":100,\"left\":50,\"right\":50}"))
		assertEquals(MensajeNvda.Portapapeles("hola"), Nvda.leer("{\"type\":\"set_clipboard_text\",\"text\":\"hola\"}"))
		assertEquals(MensajeNvda.Braille(listOf(0, 19, 255)), Nvda.leer("{\"type\":\"display\",\"cells\":[0,19,255]}"))
		assertEquals(MensajeNvda.Aviso("Bienvenido", true), Nvda.leer("{\"type\":\"motd\",\"motd\":\"Bienvenido\",\"force_display\":true}"))
		assertEquals(MensajeNvda.Aviso("Hola", false), Nvda.leer("{\"type\":\"motd\",\"motd\":\"Hola\"}"))
		assertEquals(MensajeNvda.Error("incorrect_password"), Nvda.leer("{\"type\":\"error\",\"message\":\"incorrect_password\"}"))
		assertEquals(MensajeNvda.Error("incorrect_password"), Nvda.leer("{\"type\":\"error\",\"error\":\"incorrect_password\"}"))
		assertSame(MensajeNvda.Ping, Nvda.leer("{\"type\":\"ping\"}"))
		assertSame(MensajeNvda.VersionDistinta, Nvda.leer("{\"type\":\"version_mismatch\"}"))
		assertEquals(MensajeNvda.Otro("set_braille_info"), Nvda.leer("{\"type\":\"set_braille_info\",\"name\":\"noBraille\",\"numCells\":0}"))
		assertEquals(MensajeNvda.Otro("tone"), Nvda.leer("{\"type\":\"tone\"}"))
		assertNull(Nvda.leer("no es json"))
		assertNull(Nvda.leer("{\"sin\":\"tipo\"}"))
		assertNull(Nvda.leer("[1]"))
		assertNull(Nvda.leer(""))
	}

	@Test
	fun enlaces() {
		assertEquals(EnlaceNvda("nvdaremote.com", 6837, "abc123", true), Nvda.enlace("nvdaremote://nvdaremote.com?key=abc123&mode=master"))
		assertEquals(EnlaceNvda("mi.servidor.es", 7000, "clave con espacios", false), Nvda.enlace("nvdaremote://mi.servidor.es:7000?key=clave+con%20espacios&mode=slave"))
		assertEquals(EnlaceNvda("::1", 6837, "k", true), Nvda.enlace("nvdaremote://[::1]?key=k&mode=master"))
		assertEquals(EnlaceNvda("1.2.3.4", 6837, "k", false), Nvda.enlace("Conéctate aquí: nvdaremote://1.2.3.4?mode=slave&key=k gracias"))
		assertEquals(EnlaceNvda("h", 6837, "k", true), Nvda.enlace("NVDAREMOTE://h?key=k&mode=MASTER&insecure=false"))
		assertNull(Nvda.enlace("nvdaremote://h?mode=master"))
		assertNull(Nvda.enlace("nvdaremote://h?key=k"))
		assertNull(Nvda.enlace("nvdaremote://h?key=k&mode=otro"))
		assertNull(Nvda.enlace("nvdaremote://?key=k&mode=master"))
		assertNull(Nvda.enlace("https://h?key=k&mode=master"))
		assertNull(Nvda.enlace("nvdaremote://h:99999?key=k&mode=master"))
	}
}
