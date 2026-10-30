package com.xebolax.tiresias.nucleo.remoto

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Mensajes del protocolo de NVDA Remote, versión 2 (el de NVDA 2025 y el complemento
 * NVDA Remote 2.x): un objeto JSON por línea, en UTF-8 y terminado en `\n`, con el tipo en
 * `"type"`. Aquí solo se hacen y se leen las líneas; el socket TLS (puerto [PUERTO]) lo
 * abre la app.
 *
 * Al conectar se manda [versionProtocolo] y luego [unirse]. El servidor contesta
 * `channel_joined` y después reenvía cada mensaje de un cliente a los demás del canal,
 * añadiendo `origin` (el identificador del que lo manda).
 *
 * Las funciones que hacen mensajes devuelven la línea **sin** el `\n` final.
 */
object Nvda {
	const val VERSION = 2
	const val PUERTO = 6837

	/** Como mucho, cuántas partes y caracteres de un `speak` se leen (lo demás se descarta). */
	const val MAXIMO_PARTES = 4096
	const val MAXIMO_CARACTERES = 32768

	/** Como mucho, cuántas celdas de un `display` se leen. */
	const val MAXIMO_CELDAS = 1024

	private fun linea(tipo: String, vararg campos: Pair<String, Any?>): String {
		val m = LinkedHashMap<String, Any?>()
		m["type"] = tipo
		for ((k, v) in campos) m[k] = v
		return Json.escribir(m)
	}

	/** `{"type":"protocol_version","version":2}` */
	fun versionProtocolo(): String = linea("protocol_version", "version" to VERSION)

	/** Unirse al canal [clave]: como «master» si [controla] (este móvil maneja el PC) o como «slave». */
	fun unirse(clave: String, controla: Boolean): String =
		linea("join", "channel" to clave, "connection_type" to if (controla) "master" else "slave")

	/** Pulsar o soltar una tecla virtual de Windows. NVDA usa `vk_code`; `scan_code` puede ir a 0. */
	fun tecla(vk: Int, scan: Int, extendida: Boolean, pulsada: Boolean): String =
		linea("key", "vk_code" to vk, "scan_code" to scan, "extended" to extendida, "pressed" to pulsada)

	/**
	 * Una celda escrita en el teclado braille, como la manda NVDA: [puntos] con los bits 0-7
	 * para los puntos 1-8 y [espacio] si va con la barra espaciadora. NVDA la traduce con su
	 * tabla de entrada, igual que si se escribiera en una línea braille.
	 */
	fun puntosBraille(puntos: Int, espacio: Boolean): String {
		val teclas = (0 until 8).filter { puntos and (1 shl it) != 0 }.map { "dot${it + 1}" }.toMutableList()
		if (espacio) teclas += "space"
		return linea(
			"braille_input",
			"source" to FUENTE_BRAILLE,
			"id" to teclas.joinToString("+"),
			"dots" to (puntos and 0xFF),
			"space" to espacio,
		)
	}

	/** Una orden de NVDA (un `script_` de `globalCommands.GlobalCommands`, sin el prefijo) desde el braille. */
	fun ordenBraille(guion: String): String = linea(
		"braille_input",
		"source" to FUENTE_BRAILLE,
		"id" to guion,
		"dots" to 0,
		"space" to false,
		"scriptPath" to listOf("globalCommands", "GlobalCommands", guion),
	)

	/** Mandar [texto] al portapapeles del otro lado. */
	fun portapapeles(texto: String): String = linea("set_clipboard_text", "text" to texto)

	/** Ctrl+Alt+Supr (Secure Attention Sequence) en el PC controlado. */
	fun ctrlAltSupr(): String = linea("send_SAS")

	/** El nombre de «línea braille» con el que van los gestos; NVDA lo convierte en «remoteTiresias». */
	private const val FUENTE_BRAILLE = "tiresias"

	/** Las teclas que cuentan como modificadores en [combinacion]. */
	private val MODIFICADORES = setOf(TeclaVk.CONTROL, TeclaVk.MAYUSCULAS, TeclaVk.ALT, TeclaVk.WINDOWS, TeclaVk.NVDA)

	/**
	 * Las líneas para pulsar una combinación (los modificadores primero, en el orden dado, y
	 * luego las demás) y soltarla en orden inverso. Las repetidas se mandan una vez.
	 */
	fun combinacion(teclas: List<TeclaVk>): List<String> {
		val unicas = teclas.distinct()
		val orden = unicas.filter { it in MODIFICADORES } + unicas.filter { it !in MODIFICADORES }
		return orden.map { tecla(it.vk, 0, it.extendida, true) } + orden.asReversed().map { tecla(it.vk, 0, it.extendida, false) }
	}

	/** Lee una línea (con o sin el `\n`); null si no es un objeto JSON o no tiene `"type"`. */
	fun leer(linea: String): MensajeNvda? {
		val m = Json.objeto(linea.trim()) ?: return null
		val tipo = m.texto("type") ?: return null
		val otro = MensajeNvda.Otro(tipo)
		return when (tipo) {
			"channel_joined" -> {
				val clientes = m.lista("clients")
				if (clientes != null) {
					MensajeNvda.CanalUnido(clientes.mapNotNull { cliente(Json.comoMapa(it)) })
				} else {
					// Protocolo 1: solo los identificadores, sin el tipo de conexión
					val ids = m.lista("user_ids") ?: emptyList()
					MensajeNvda.CanalUnido(ids.mapNotNull { idDe(it) }.map { ClienteNvda(it, "") })
				}
			}
			"client_joined" -> clienteDe(m)?.let { MensajeNvda.ClienteEntra(it) } ?: otro
			"client_left" -> clienteDe(m)?.let { MensajeNvda.ClienteSale(it) } ?: otro
			"speak" -> {
				val secuencia = m.lista("sequence") ?: return otro
				MensajeNvda.Hablar(partes(secuencia), m.entero("priority")?.toInt() ?: 0, m.entero("origin"))
			}
			"cancel" -> MensajeNvda.Callar(m.entero("origin"))
			"pause_speech" -> MensajeNvda.PausarVoz(m.logico("switch") ?: return otro)
			"tone" -> {
				val hz = m.decimal("hz") ?: return otro
				val ms = m.decimal("length") ?: return otro
				if (!hz.isFinite() || !ms.isFinite()) return otro
				MensajeNvda.Tono(hz.coerceIn(0.0, 100_000.0).toInt(), ms.coerceIn(0.0, 60_000.0).toInt())
			}
			"set_clipboard_text" -> MensajeNvda.Portapapeles(m.texto("text") ?: return otro)
			"display" -> {
				val celdas = m.lista("cells") ?: return otro
				MensajeNvda.Braille(celdas.asSequence().take(MAXIMO_CELDAS).map { ((it as? Number)?.toInt() ?: 0) and 0xFF }.toList())
			}
			"motd" -> MensajeNvda.Aviso(m.texto("motd") ?: return otro, m.logico("force_display") ?: false)
			"error" -> MensajeNvda.Error(m.texto("message") ?: m.texto("error") ?: "")
			"ping" -> MensajeNvda.Ping
			"version_mismatch" -> MensajeNvda.VersionDistinta
			else -> otro
		}
	}

	private fun idDe(v: Any?): Long? = when (v) {
		is Long -> v
		is Double -> if (v.isFinite() && v == Math.floor(v)) v.toLong() else null
		else -> null
	}

	private fun cliente(m: Map<String, Any?>?): ClienteNvda? {
		if (m == null) return null
		val id = m.entero("id") ?: return null
		return ClienteNvda(id, m.texto("connection_type") ?: "")
	}

	/** El cliente de `client_joined`/`client_left`: el objeto `client`, o si no `user_id` (protocolo 1). */
	private fun clienteDe(m: Map<String, Any?>): ClienteNvda? =
		cliente(m.mapa("client")) ?: m.entero("user_id")?.let { ClienteNvda(it, "") }

	/** Las partes de una secuencia de voz, con los límites de [MAXIMO_PARTES] y [MAXIMO_CARACTERES]. */
	private fun partes(secuencia: List<Any?>): List<ParteVoz> {
		val partes = ArrayList<ParteVoz>()
		var caracteres = 0
		for (p in secuencia) {
			if (partes.size >= MAXIMO_PARTES || caracteres >= MAXIMO_CARACTERES) break
			when (p) {
				is String -> {
					val texto = if (caracteres + p.length > MAXIMO_CARACTERES) p.substring(0, MAXIMO_CARACTERES - caracteres) else p
					caracteres += texto.length
					partes += ParteVoz.Texto(texto)
				}
				is List<*> -> orden(p)?.let { partes += it }
				// Números u objetos sueltos: no son voz
				else -> continue
			}
		}
		return partes
	}

	/** Una orden de voz `[Nombre, {campos}]`; null si es de las que se ignoran. */
	private fun orden(p: List<*>): ParteVoz? {
		val nombre = p.getOrNull(0) as? String ?: return null
		val campos = Json.comoMapa(p.getOrNull(1)) ?: emptyMap()
		return when (nombre) {
			"LangChangeCommand" -> ParteVoz.Idioma(campos.texto("lang"))
			"CharacterModeCommand" -> ParteVoz.Deletrear(campos.logico("state") ?: return null)
			"BreakCommand", "EndUtteranceCommand" -> ParteVoz.Pausa
			else -> null
		}
	}

	/**
	 * Un enlace `nvdaremote://host:puerto?key=…&mode=master|slave` (puede venir dentro de un
	 * texto más largo) → host, puerto ([PUERTO] si no lo lleva), clave y si este móvil
	 * controla. El modo del enlace es el que debe usar quien lo abre (NVDA lo invierte al
	 * copiarlo), así que `mode=master` es que controla. null si falta algo o no es válido.
	 */
	fun enlace(texto: String): EnlaceNvda? {
		val i = texto.indexOf("nvdaremote://", ignoreCase = true)
		if (i < 0) return null
		var fin = i
		while (fin < texto.length && !texto[fin].isWhitespace() && texto[fin] != '"' && texto[fin] != '<' && texto[fin] != '>') fin++
		val uri = try {
			URI(texto.substring(i, fin))
		} catch (e: URISyntaxException) {
			return null
		}
		val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.takeIf { it.isNotEmpty() } ?: return null
		val puerto = if (uri.port == -1) PUERTO else uri.port
		if (puerto !in 1..65535) return null
		val parametros = HashMap<String, String>()
		for (par in (uri.rawQuery ?: return null).split('&')) {
			val igual = par.indexOf('=')
			if (igual <= 0) continue
			val clave = decodificar(par.substring(0, igual)) ?: return null
			val valor = decodificar(par.substring(igual + 1)) ?: return null
			parametros.putIfAbsent(clave.lowercase(), valor)
		}
		val clave = parametros["key"]?.takeIf { it.isNotEmpty() } ?: return null
		val controla = when (parametros["mode"]?.lowercase()) {
			"master" -> true
			"slave" -> false
			else -> return null
		}
		return EnlaceNvda(host, puerto, clave, controla)
	}

	private fun decodificar(s: String): String? = try {
		URLDecoder.decode(s, "UTF-8")
	} catch (e: IllegalArgumentException) {
		null
	}
}

/** Lo que dice un enlace `nvdaremote://`. */
data class EnlaceNvda(val host: String, val puerto: Int, val clave: String, val controla: Boolean)

/** Un cliente del canal: su identificador y su tipo («master», «slave» o vacío si no se sabe). */
data class ClienteNvda(val id: Long, val tipo: String)

/** Un mensaje recibido de NVDA Remote. */
sealed class MensajeNvda {
	/** `channel_joined`: ya estamos en el canal; [clientes] son los que ya estaban. */
	data class CanalUnido(val clientes: List<ClienteNvda>) : MensajeNvda()

	/** `client_joined` */
	data class ClienteEntra(val cliente: ClienteNvda) : MensajeNvda()

	/** `client_left` */
	data class ClienteSale(val cliente: ClienteNvda) : MensajeNvda()

	/** `speak`: lo que dice el NVDA del otro lado. [origen] es el cliente que lo manda. */
	data class Hablar(val partes: List<ParteVoz>, val prioridad: Int, val origen: Long?) : MensajeNvda()

	/** `cancel`: callar. */
	data class Callar(val origen: Long?) : MensajeNvda()

	/** `pause_speech`: pausar (o seguir, si [pausar] es false). */
	data class PausarVoz(val pausar: Boolean) : MensajeNvda()

	/** `tone`: un pitido de [hz] durante [ms]. */
	data class Tono(val hz: Int, val ms: Int) : MensajeNvda()

	/** `set_clipboard_text` */
	data class Portapapeles(val texto: String) : MensajeNvda()

	/** `display`: las celdas de la línea braille del otro lado (bits 0-7 = puntos 1-8). */
	data class Braille(val celdas: List<Int>) : MensajeNvda()

	/** `motd`: el mensaje del servidor; [forzar] es que se muestre aunque ya se viera. */
	data class Aviso(val texto: String, val forzar: Boolean) : MensajeNvda()

	/** `error`: por ejemplo «incorrect_password». */
	data class Error(val texto: String) : MensajeNvda()

	/** `ping` */
	object Ping : MensajeNvda() {
		override fun toString() = "Ping"
	}

	/** `version_mismatch`: el servidor no habla nuestra versión. */
	object VersionDistinta : MensajeNvda() {
		override fun toString() = "VersionDistinta"
	}

	/** Cualquier otro tipo, o uno conocido sin los campos que hacen falta. */
	data class Otro(val tipo: String) : MensajeNvda()
}

/** Un trozo de lo que dice NVDA. */
sealed class ParteVoz {
	data class Texto(val texto: String) : ParteVoz()

	/** Cambio de idioma; null es volver al de la voz. */
	data class Idioma(val idioma: String?) : ParteVoz()

	/** Empezar ([si]) o dejar de deletrear. */
	data class Deletrear(val si: Boolean) : ParteVoz()

	/** Una pausa o un final de frase. */
	object Pausa : ParteVoz() {
		override fun toString() = "Pausa"
	}
}

/** Teclas virtuales de Windows (`VK_*`) y si son extendidas. */
enum class TeclaVk(val vk: Int, val extendida: Boolean) {
	A(0x41, false), B(0x42, false), C(0x43, false), D(0x44, false), E(0x45, false), F(0x46, false),
	G(0x47, false), H(0x48, false), I(0x49, false), J(0x4A, false), K(0x4B, false), L(0x4C, false),
	M(0x4D, false), N(0x4E, false), O(0x4F, false), P(0x50, false), Q(0x51, false), R(0x52, false),
	S(0x53, false), T(0x54, false), U(0x55, false), V(0x56, false), W(0x57, false), X(0x58, false),
	Y(0x59, false), Z(0x5A, false),
	N0(0x30, false), N1(0x31, false), N2(0x32, false), N3(0x33, false), N4(0x34, false),
	N5(0x35, false), N6(0x36, false), N7(0x37, false), N8(0x38, false), N9(0x39, false),
	F1(0x70, false), F2(0x71, false), F3(0x72, false), F4(0x73, false), F5(0x74, false), F6(0x75, false),
	F7(0x76, false), F8(0x77, false), F9(0x78, false), F10(0x79, false), F11(0x7A, false), F12(0x7B, false),
	IZQUIERDA(0x25, true), ARRIBA(0x26, true), DERECHA(0x27, true), ABAJO(0x28, true),
	INICIO(0x24, true), FIN(0x23, true), REPAG(0x21, true), AVPAG(0x22, true),
	INSERTAR(0x2D, true), SUPRIMIR(0x2E, true),
	INTRO(0x0D, false), ESCAPE(0x1B, false), TABULADOR(0x09, false), RETROCESO(0x08, false), ESPACIO(0x20, false),
	MAYUSCULAS(0x10, false), CONTROL(0x11, false), ALT(0x12, false),
	WINDOWS(0x5B, true), APLICACIONES(0x5D, true), BLOQ_MAYUS(0x14, false),
	/** El teclado numérico (con Bloq Num apagado): la navegación por objetos y el cursor de revisión de NVDA en la disposición de escritorio. */
	NUM0(0x60, false), NUM1(0x61, false), NUM2(0x62, false), NUM3(0x63, false), NUM4(0x64, false),
	NUM5(0x65, false), NUM6(0x66, false), NUM7(0x67, false), NUM8(0x68, false), NUM9(0x69, false),
	NUM_MAS(0x6B, false), NUM_MENOS(0x6D, false), NUM_POR(0x6A, false), NUM_DIVIDIR(0x6F, true),
	/** Intro del teclado numérico (extendida): activa el navegador de objetos de NVDA. */
	NUM_INTRO(0x0D, true),
	/** El punto del teclado numérico (Supr con Bloq Num apagado): las dimensiones del objeto en NVDA. */
	NUM_PUNTO(0x6E, false),
	IMPRIMIR_PANTALLA(0x2C, true), PAUSA(0x13, false),
	/** El punto y la coma del teclado principal: el cursor de revisión en la disposición de portátil y salir del contenedor en la web. */
	PUNTO(0xBE, false), COMA(0xBC, false),

	/** Insertar del teclado numérico (no extendida): la tecla NVDA por defecto. */
	NVDA(0x2D, false);

	companion object {
		/** La tecla de una letra (a-z, sin distinguir mayúsculas) o de una cifra (0-9); null con lo demás. */
		fun deLetra(c: Char): TeclaVk? = when (c) {
			in 'a'..'z' -> entries[c - 'a']
			in 'A'..'Z' -> entries[c - 'A']
			in '0'..'9' -> entries[26 + (c - '0')]
			else -> null
		}
	}
}
