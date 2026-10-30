package com.xebolax.tiresias.nucleo.remoto

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Un puerto abierto en el router. [cerrar] lo quita (bloquea un momento: no se llama desde el
 * hilo principal). Si no se cierra, el router lo quita solo al caducar.
 */
data class Mapeo(val ipPublica: InetAddress, val puertoPublico: Int, val metodo: String, private val cerrarlo: () -> Unit) {
	private val cerrado = AtomicBoolean(false)

	/** Quita el reenvío del router. Solo hace algo la primera vez. */
	fun cerrar() {
		if (cerrado.compareAndSet(false, true)) cerrarlo()
	}

	companion object {
		const val UPNP = "upnp"
		const val NATPMP = "natpmp"
	}
}

/**
 * Abrir un puerto TCP en el router de casa, sin servidores externos, para que el ayudante
 * pueda conectarse desde fuera por IPv4: primero NAT-PMP (RFC 6886, routers de Apple y
 * muchos con miniupnpd) y luego UPnP IGD (la mayoría de los routers domésticos).
 *
 * El XML se lee con expresiones regulares: las respuestas de los routers son pequeñas y
 * siempre tienen la misma forma, y así no hace falta ningún analizador.
 */
object Puertos {
	private const val PUERTO_NATPMP = 5351
	private val SSDP = InetSocketAddress("239.255.255.250", 1900)
	private const val MAXIMO_XML = 256 * 1024

	/** Los servicios que saben abrir puertos, en orden de preferencia. */
	private val SERVICIOS = listOf(
		"urn:schemas-upnp-org:service:WANIPConnection:2",
		"urn:schemas-upnp-org:service:WANIPConnection:1",
		"urn:schemas-upnp-org:service:WANPPPConnection:1",
	)

	/**
	 * Pide al router que reenvíe [puertoPublico] TCP a [ipLocal]:[puertoLocal] durante
	 * [segundos]. Prueba NAT-PMP (UDP 5351 a [puertaEnlace], si se da) y UPnP (SSDP M-SEARCH a
	 * 239.255.255.250:1900 desde [ipLocal], descripción XML, servicio WANIPConnection:1/2 o
	 * WANPPPConnection:1, SOAP GetExternalIPAddress y AddPortMapping).
	 *
	 * Devuelve null si nada funciona o si la IP pública sale privada o compartida (CGNAT): en
	 * ese caso el puerto abierto no serviría de nada desde fuera. Bloquea, como mucho unos
	 * [esperaMs] en total. Nunca lanza salvo [InterruptedException].
	 */
	fun abrir(
		ipLocal: Inet4Address,
		puertoLocal: Int,
		puertaEnlace: Inet4Address?,
		puertoPublico: Int,
		segundos: Int,
		descripcion: String,
		esperaMs: Int = 4000,
	): Mapeo? {
		val inicio = System.nanoTime()
		val hasta = inicio + esperaMs.toLong() * 1_000_000L
		if (puertaEnlace != null) {
			// NAT-PMP contesta enseguida o no contesta: un tercio del tiempo, como mucho 1,5 s
			val limite = inicio + minOf(esperaMs / 3, 1500).toLong() * 1_000_000L
			natPmp(ipLocal, puertaEnlace, puertoLocal, puertoPublico, segundos, limite)?.let { return it }
			comprobarInterrupcion()
		}
		val mapeo = upnp(ipLocal, puertoLocal, puertoPublico, segundos, descripcion, hasta)
		comprobarInterrupcion()
		return mapeo
	}

	private fun comprobarInterrupcion() {
		if (Thread.currentThread().isInterrupted) throw InterruptedException()
	}

	private fun quedaMs(hasta: Long): Int = ((hasta - System.nanoTime()) / 1_000_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

	private fun ipVale(ip: InetAddress) = Direcciones.esPublicaIpv4(ip)

	// ── NAT-PMP ──

	private fun natPmp(ipLocal: Inet4Address, puertaEnlace: Inet4Address, puertoLocal: Int, puertoPublico: Int, segundos: Int, hasta: Long): Mapeo? {
		return try {
			DatagramSocket(InetSocketAddress(ipLocal, 0)).use { s ->
				s.connect(InetSocketAddress(puertaEnlace, PUERTO_NATPMP))
				val direccion = pedirNatPmp(s, byteArrayOf(0, 0), 128, 12, hasta) ?: return null
				val ip = InetAddress.getByAddress(direccion.copyOfRange(8, 12))
				if (!ipVale(ip)) return null
				val respuesta = pedirNatPmp(s, peticionNatPmp(puertoLocal, puertoPublico, segundos), 130, 16, hasta) ?: return null
				val externo = ((respuesta[10].toInt() and 0xFF) shl 8) or (respuesta[11].toInt() and 0xFF)
				if (externo == 0) return null
				Mapeo(ip, externo, Mapeo.NATPMP) { cerrarNatPmp(ipLocal, puertaEnlace, puertoLocal) }
			}
		} catch (e: IOException) {
			null
		} catch (e: RuntimeException) {
			null
		}
	}

	/** Petición de reenvío TCP (código 2). Con [segundos] 0 y puerto público 0, la quita. */
	private fun peticionNatPmp(puertoLocal: Int, puertoPublico: Int, segundos: Int): ByteArray =
		ByteBuffer.allocate(12).put(0).put(2).putShort(0).putShort(puertoLocal.toShort()).putShort(puertoPublico.toShort()).putInt(segundos).array()

	/**
	 * Manda [peticion] y espera la respuesta de código [codigo] con al menos [largo] bytes y
	 * resultado 0. Reintenta a los 250 ms, 500 ms… (RFC 6886) hasta [hasta]. null si no hay.
	 */
	private fun pedirNatPmp(s: DatagramSocket, peticion: ByteArray, codigo: Int, largo: Int, hasta: Long): ByteArray? {
		var espera = 250
		val buffer = ByteArray(64)
		while (true) {
			val queda = quedaMs(hasta)
			if (queda <= 0) return null
			s.send(DatagramPacket(peticion, peticion.size))
			val fin = System.nanoTime() + minOf(espera, queda).toLong() * 1_000_000L
			while (true) {
				val ms = quedaMs(fin)
				if (ms <= 0) break
				s.soTimeout = ms
				val paquete = DatagramPacket(buffer, buffer.size)
				try {
					s.receive(paquete)
				} catch (e: SocketTimeoutException) {
					break
				}
				val d = paquete.data.copyOf(paquete.length)
				if (d.size >= largo && d[0].toInt() == 0 && (d[1].toInt() and 0xFF) == codigo) {
					val resultado = ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
					return if (resultado == 0) d else null
				}
			}
			espera *= 2
		}
	}

	/** @return si el router confirmó que lo quitaba. */
	private fun cerrarNatPmp(ipLocal: Inet4Address, puertaEnlace: Inet4Address, puertoLocal: Int): Boolean {
		return try {
			DatagramSocket(InetSocketAddress(ipLocal, 0)).use { s ->
				s.connect(InetSocketAddress(puertaEnlace, PUERTO_NATPMP))
				pedirNatPmp(s, peticionNatPmp(puertoLocal, 0, 0), 130, 16, System.nanoTime() + 1000L * 1_000_000L) != null
			}
		} catch (e: IOException) {
			false
		} catch (e: RuntimeException) {
			false
		}
	}

	// ── UPnP IGD ──

	private fun upnp(ipLocal: Inet4Address, puertoLocal: Int, puertoPublico: Int, segundos: Int, descripcion: String, hasta: Long): Mapeo? {
		return try {
			val probados = HashSet<String>()
			DatagramSocket(InetSocketAddress(ipLocal, 0)).use { s ->
				for (st in listOf("urn:schemas-upnp-org:device:InternetGatewayDevice:1", "urn:schemas-upnp-org:device:InternetGatewayDevice:2")) {
					val m = busqueda(st).toByteArray(Charsets.US_ASCII)
					s.send(DatagramPacket(m, m.size, SSDP))
				}
				val buffer = ByteArray(2048)
				while (probados.size < 4) {
					val queda = quedaMs(hasta)
					if (queda <= 0) return null
					s.soTimeout = queda
					val paquete = DatagramPacket(buffer, buffer.size)
					try {
						s.receive(paquete)
					} catch (e: SocketTimeoutException) {
						return null
					}
					val ubicacion = respuestaSsdp(String(paquete.data, 0, paquete.length, Charsets.ISO_8859_1)) ?: continue
					if (!probados.add(ubicacion)) continue
					mapearUpnp(ubicacion, ipLocal, puertoLocal, puertoPublico, segundos, descripcion, hasta)?.let { return it }
					comprobarInterrupcion()
				}
				null
			}
		} catch (e: IOException) {
			null
		} catch (e: RuntimeException) {
			null
		}
	}

	private fun busqueda(st: String) = "M-SEARCH * HTTP/1.1\r\n" +
		"HOST: 239.255.255.250:1900\r\n" +
		"MAN: \"ssdp:discover\"\r\n" +
		"MX: 2\r\n" +
		"ST: $st\r\n\r\n"

	/** Con un router ya encontrado en [ubicacion]: su descripción, su IP pública y el reenvío. Interno para probarlo. */
	internal fun mapearUpnp(ubicacion: String, ipLocal: Inet4Address, puertoLocal: Int, puertoPublico: Int, segundos: Int, descripcion: String, hasta: Long): Mapeo? {
		val xml = http(ubicacion, null, null, hasta)?.takeIf { it.first == 200 }?.second ?: return null
		val (tipo, control) = servicioDeControl(xml, ubicacion) ?: return null
		val ipTexto = soap(control, tipo, "GetExternalIPAddress", emptyList(), hasta)?.takeIf { it.first == 200 }?.second?.let { ipDeRespuesta(it) } ?: return null
		if (!Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(ipTexto)) return null
		val ip = InetAddress.getByName(ipTexto)
		if (!ipVale(ip)) return null
		val cliente = ipLocal.hostAddress ?: return null
		fun anadir(duracion: Int) = soap(
			control, tipo, "AddPortMapping",
			listOf(
				"NewRemoteHost" to "",
				"NewExternalPort" to puertoPublico.toString(),
				"NewProtocol" to "TCP",
				"NewInternalPort" to puertoLocal.toString(),
				"NewInternalClient" to cliente,
				"NewEnabled" to "1",
				"NewPortMappingDescription" to descripcion,
				"NewLeaseDuration" to duracion.toString(),
			),
			hasta,
		)
		var respuesta = anadir(segundos) ?: return null
		// 725 OnlyPermanentLeasesSupported: algunos routers viejos solo aceptan duración 0
		if (respuesta.first != 200 && Regex("""<errorCode>\s*725\s*</errorCode>""").containsMatchIn(respuesta.second)) {
			respuesta = anadir(0) ?: return null
		}
		if (respuesta.first != 200) return null
		return Mapeo(ip, puertoPublico, Mapeo.UPNP) { cerrarUpnp(control, tipo, puertoPublico) }
	}

	/** @return si el router aceptó quitarlo. */
	private fun cerrarUpnp(control: String, tipo: String, puertoPublico: Int): Boolean {
		return try {
			val argumentos = listOf("NewRemoteHost" to "", "NewExternalPort" to puertoPublico.toString(), "NewProtocol" to "TCP")
			soap(control, tipo, "DeletePortMapping", argumentos, System.nanoTime() + 2000L * 1_000_000L)?.first == 200
		} catch (e: IOException) {
			false
		} catch (e: RuntimeException) {
			false
		}
	}

	private fun soap(control: String, tipo: String, accion: String, argumentos: List<Pair<String, String>>, hasta: Long): Pair<Int, String>? =
		http(control, peticionSoap(tipo, accion, argumentos), "\"$tipo#$accion\"", hasta)

	/** GET (sin [cuerpo]) o POST SOAP: (código, cuerpo), o null si no hay tiempo o el enlace no es http. */
	private fun http(direccion: String, cuerpo: String?, accionSoap: String?, hasta: Long): Pair<Int, String>? {
		val queda = quedaMs(hasta)
		if (queda <= 0) return null
		val url = URL(direccion)
		if (url.protocol != "http") return null
		val c = url.openConnection() as HttpURLConnection
		try {
			c.connectTimeout = minOf(queda, 2000)
			c.readTimeout = minOf(queda, 2000)
			c.useCaches = false
			c.instanceFollowRedirects = false
			if (cuerpo != null) {
				val bytes = cuerpo.toByteArray(Charsets.UTF_8)
				c.requestMethod = "POST"
				c.doOutput = true
				c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
				c.setRequestProperty("SOAPAction", accionSoap)
				c.setFixedLengthStreamingMode(bytes.size)
				c.outputStream.use { it.write(bytes) }
			}
			val codigo = c.responseCode
			val flujo = if (codigo >= 400) c.errorStream else c.inputStream
			val texto = flujo?.use { leerLimitado(it) } ?: ""
			return codigo to texto
		} finally {
			c.disconnect()
		}
	}

	private fun leerLimitado(entrada: InputStream): String {
		val b = ByteArrayOutputStream()
		val buffer = ByteArray(8192)
		while (b.size() < MAXIMO_XML) {
			val n = entrada.read(buffer)
			if (n < 0) break
			b.write(buffer, 0, minOf(n, MAXIMO_XML - b.size()))
		}
		return String(b.toByteArray(), Charsets.UTF_8)
	}

	// ── Partes puras, públicas para probarlas ──

	/** La cabecera LOCATION de una respuesta SSDP (o de un NOTIFY), si es un enlace http. */
	fun respuestaSsdp(texto: String): String? {
		for (linea in texto.split("\r\n", "\n")) {
			val dos = linea.indexOf(':')
			if (dos <= 0) continue
			if (!linea.substring(0, dos).trim().equals("LOCATION", ignoreCase = true)) continue
			val valor = linea.substring(dos + 1).trim()
			return valor.takeIf { it.startsWith("http://", ignoreCase = true) }
		}
		return null
	}

	/**
	 * El servicio que abre puertos en la descripción [xml] del router: (tipo de servicio, URL
	 * absoluta de control). La URL relativa se resuelve contra `<URLBase>` si lo hay, o si no
	 * contra [base] (la LOCATION de donde salió el XML). Se prefiere WANIPConnection:2, luego
	 * WANIPConnection:1 y por último WANPPPConnection:1.
	 */
	fun servicioDeControl(xml: String, base: String): Pair<String, String>? {
		val servicios = Regex("""<(?:\w+:)?service>(.*?)</(?:\w+:)?service>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
			.findAll(xml)
			.mapNotNull { m ->
				val dentro = m.groupValues[1]
				val tipo = etiqueta(dentro, "serviceType") ?: return@mapNotNull null
				val control = etiqueta(dentro, "controlURL") ?: return@mapNotNull null
				tipo to control
			}
			.toList()
		val elegido = SERVICIOS.firstNotNullOfOrNull { quiero -> servicios.firstOrNull { it.first.equals(quiero, ignoreCase = true) } } ?: return null
		val raiz = etiqueta(xml, "URLBase")?.takeIf { it.isNotBlank() } ?: base
		return try {
			elegido.first to URL(URL(raiz), elegido.second).toString()
		} catch (e: MalformedURLException) {
			null
		}
	}

	/** La petición SOAP de [accion] del servicio [tipo] con sus [argumentos] en orden. */
	fun peticionSoap(tipo: String, accion: String, argumentos: List<Pair<String, String>>): String = buildString {
		append("<?xml version=\"1.0\"?>\r\n")
		append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
		append("<s:Body>")
		append("<u:").append(accion).append(" xmlns:u=\"").append(escaparXml(tipo)).append("\">")
		for ((nombre, valor) in argumentos) {
			append('<').append(nombre).append('>').append(escaparXml(valor)).append("</").append(nombre).append('>')
		}
		append("</u:").append(accion).append('>')
		append("</s:Body></s:Envelope>\r\n")
	}

	/** El campo NewExternalIPAddress de la respuesta a GetExternalIPAddress, o null si falta o está vacío. */
	fun ipDeRespuesta(xml: String): String? = etiqueta(xml, "NewExternalIPAddress")?.takeIf { it.isNotEmpty() }

	/** El texto de la primera `<nombre>…</nombre>` (con o sin prefijo de espacio de nombres), sin espacios y desescapado. */
	private fun etiqueta(xml: String, nombre: String): String? {
		val m = Regex("""<(?:\w+:)?$nombre(?:\s[^>]*)?>(.*?)</(?:\w+:)?$nombre\s*>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xml)
			?: return null
		return desescaparXml(m.groupValues[1].trim())
	}

	private fun escaparXml(s: String): String = buildString(s.length) {
		for (c in s) when (c) {
			'&' -> append("&amp;")
			'<' -> append("&lt;")
			'>' -> append("&gt;")
			'"' -> append("&quot;")
			'\'' -> append("&apos;")
			else -> append(c)
		}
	}

	private fun desescaparXml(s: String): String =
		s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
