package com.xebolax.tiresias.nucleo.remoto

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections

class PuertosTest {
	// ── Respuestas de ejemplo, con la forma que tienen las de los routers reales ──

	private val ssdpMiniupnpd = "HTTP/1.1 200 OK\r\n" +
		"CACHE-CONTROL: max-age=120\r\n" +
		"ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
		"USN: uuid:6a1c1c6e-1d2a-4bb5-9a6e-0c2f1d1e1a11::urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
		"EXT:\r\n" +
		"SERVER: OpenWRT/OpenWrt UPnP/1.1 MiniUPnPd/2.2.1\r\n" +
		"LOCATION: http://192.168.1.1:5000/rootDesc.xml\r\n" +
		"OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n" +
		"01-NLS: 1\r\n" +
		"BOOTID.UPNP.ORG: 1\r\n" +
		"CONFIGID.UPNP.ORG: 1337\r\n\r\n"

	private val ssdpFritz = "HTTP/1.1 200 OK\r\n" +
		"Location: http://192.168.178.1:49000/igddesc.xml\r\n" +
		"Server: FRITZ!Box 7590 UPnP/1.0 AVM FRITZ!Box 7590 154.07.57\r\n" +
		"Cache-Control: max-age=1800\r\n" +
		"Ext:\r\n" +
		"ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
		"USN: uuid:75802409-bccb-40e7-8e6c-3431C4E2A1F0::urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"

	private val descripcionFritz = """
		<?xml version="1.0"?>
		<root xmlns="urn:schemas-upnp-org:device-1-0">
		<specVersion><major>1</major><minor>0</minor></specVersion>
		<device>
		<deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
		<friendlyName>FRITZ!Box 7590</friendlyName>
		<manufacturer>AVM Berlin</manufacturer>
		<modelName>FRITZ!Box 7590</modelName>
		<UDN>uuid:75802409-bccb-40e7-8e6c-3431C4E2A1F0</UDN>
		<serviceList>
		<service>
		<serviceType>urn:schemas-any-com:service:Any:1</serviceType>
		<serviceId>urn:any-com:serviceId:any1</serviceId>
		<controlURL>/igdupnp/control/any</controlURL>
		<eventSubURL>/igdupnp/control/any</eventSubURL>
		<SCPDURL>/any.xml</SCPDURL>
		</service>
		</serviceList>
		<deviceList>
		<device>
		<deviceType>urn:schemas-upnp-org:device:WANDevice:1</deviceType>
		<serviceList>
		<service>
		<serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType>
		<serviceId>urn:upnp-org:serviceId:WANCommonIFC1</serviceId>
		<controlURL>/igdupnp/control/WANCommonIFC1</controlURL>
		<eventSubURL>/igdupnp/control/WANCommonIFC1</eventSubURL>
		<SCPDURL>/igdicfgSCPD.xml</SCPDURL>
		</service>
		</serviceList>
		<deviceList>
		<device>
		<deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:1</deviceType>
		<serviceList>
		<service>
		<serviceType>urn:schemas-upnp-org:service:WANDSLLinkConfig:1</serviceType>
		<serviceId>urn:upnp-org:serviceId:WANDSLLinkC1</serviceId>
		<controlURL>/igdupnp/control/WANDSLLinkC1</controlURL>
		<eventSubURL>/igdupnp/control/WANDSLLinkC1</eventSubURL>
		<SCPDURL>/igddslSCPD.xml</SCPDURL>
		</service>
		<service>
		<serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
		<serviceId>urn:upnp-org:serviceId:WANIPConn1</serviceId>
		<controlURL>/igdupnp/control/WANIPConn1</controlURL>
		<eventSubURL>/igdupnp/control/WANIPConn1</eventSubURL>
		<SCPDURL>/igdconnSCPD.xml</SCPDURL>
		</service>
		</serviceList>
		</device>
		</deviceList>
		</device>
		</deviceList>
		<presentationURL>http://fritz.box</presentationURL>
		</device>
		</root>
	""".trimIndent()

	private val descripcionMiniupnpd = """
		<?xml version="1.0"?>
		<root xmlns="urn:schemas-upnp-org:device-1-0" configId="1337"><specVersion><major>1</major><minor>1</minor></specVersion><device><deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:2</deviceType><friendlyName>OpenWrt router</friendlyName><manufacturer>OpenWrt</manufacturer><serviceList><service><serviceType>urn:schemas-upnp-org:service:Layer3Forwarding:1</serviceType><serviceId>urn:upnp-org:serviceId:L3Forwarding1</serviceId><SCPDURL>/L3F.xml</SCPDURL><controlURL>/ctl/L3F</controlURL><eventSubURL>/evt/L3F</eventSubURL></service></serviceList><deviceList><device><deviceType>urn:schemas-upnp-org:device:WANDevice:2</deviceType><serviceList><service><serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType><serviceId>urn:upnp-org:serviceId:WANCommonIFC1</serviceId><SCPDURL>/WANCfg.xml</SCPDURL><controlURL>/ctl/CmnIfCfg</controlURL><eventSubURL>/evt/CmnIfCfg</eventSubURL></service></serviceList><deviceList><device><deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:2</deviceType><serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:2</serviceType><serviceId>urn:upnp-org:serviceId:WANIPConn1</serviceId><SCPDURL>/WANIPCn.xml</SCPDURL><controlURL>/ctl/IPConn</controlURL><eventSubURL>/evt/IPConn</eventSubURL></service><service><serviceType>urn:schemas-upnp-org:service:WANIPv6FirewallControl:1</serviceType><serviceId>urn:upnp-org:serviceId:WANIPv6Firewall1</serviceId><SCPDURL>/WANIP6FC.xml</SCPDURL><controlURL>/ctl/IP6FCtl</controlURL><eventSubURL>/evt/IP6FCtl</eventSubURL></service></serviceList></device></deviceList></device></deviceList><presentationURL>http://192.168.1.1/</presentationURL></device></root>
	""".trimIndent()

	private val descripcionPppConBase = """
		<?xml version="1.0" encoding="UTF-8"?>
		<root xmlns="urn:schemas-upnp-org:device-1-0">
		  <URLBase>http://10.0.0.138:80/</URLBase>
		  <device>
		    <serviceList>
		      <service>
		        <serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType>
		        <controlURL>upnp/control/WANPPPConn1</controlURL>
		      </service>
		    </serviceList>
		  </device>
		</root>
	""".trimIndent()

	private val respuestaIp = "<?xml version=\"1.0\"?>\r\n" +
		"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
		"<s:Body>\n<u:GetExternalIPAddressResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\">\n" +
		"<NewExternalIPAddress>83.45.120.7</NewExternalIPAddress>\n" +
		"</u:GetExternalIPAddressResponse>\n</s:Body>\n</s:Envelope>\n"

	private val error725 = "<?xml version=\"1.0\"?>\r\n" +
		"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
		"<s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>" +
		"<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>725</errorCode>" +
		"<errorDescription>OnlyPermanentLeasesSupported</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"

	@Test
	fun cabeceraLocation() {
		assertEquals("http://192.168.1.1:5000/rootDesc.xml", Puertos.respuestaSsdp(ssdpMiniupnpd))
		assertEquals("http://192.168.178.1:49000/igddesc.xml", Puertos.respuestaSsdp(ssdpFritz))
		assertNull(Puertos.respuestaSsdp("HTTP/1.1 200 OK\r\nST: x\r\n\r\n"))
		assertNull(Puertos.respuestaSsdp("HTTP/1.1 200 OK\r\nLOCATION: file:///etc/passwd\r\n\r\n"))
		assertNull(Puertos.respuestaSsdp(""))
	}

	@Test
	fun servicioDeLaFritzBox() {
		val (tipo, control) = Puertos.servicioDeControl(descripcionFritz, "http://192.168.178.1:49000/igddesc.xml")!!
		assertEquals("urn:schemas-upnp-org:service:WANIPConnection:1", tipo)
		assertEquals("http://192.168.178.1:49000/igdupnp/control/WANIPConn1", control)
	}

	@Test
	fun servicioDeMiniupnpdPrefiereLaVersion2() {
		val (tipo, control) = Puertos.servicioDeControl(descripcionMiniupnpd, "http://192.168.1.1:5000/rootDesc.xml")!!
		assertEquals("urn:schemas-upnp-org:service:WANIPConnection:2", tipo)
		assertEquals("http://192.168.1.1:5000/ctl/IPConn", control)
	}

	@Test
	fun servicioPppConUrlBase() {
		val (tipo, control) = Puertos.servicioDeControl(descripcionPppConBase, "http://10.0.0.138:1900/desc.xml")!!
		assertEquals("urn:schemas-upnp-org:service:WANPPPConnection:1", tipo)
		assertEquals("http://10.0.0.138:80/upnp/control/WANPPPConn1", control)
	}

	@Test
	fun sinServicioQueAbraPuertos() {
		assertNull(Puertos.servicioDeControl("<root><device><serviceList><service><serviceType>urn:schemas-upnp-org:service:Layer3Forwarding:1</serviceType><controlURL>/x</controlURL></service></serviceList></device></root>", "http://1.2.3.4/"))
		assertNull(Puertos.servicioDeControl("no es xml", "http://1.2.3.4/"))
	}

	@Test
	fun peticionSoap() {
		val p = Puertos.peticionSoap(
			"urn:schemas-upnp-org:service:WANIPConnection:1",
			"AddPortMapping",
			listOf("NewRemoteHost" to "", "NewExternalPort" to "51000", "NewPortMappingDescription" to "Tiresias <ayuda> & más"),
		)
		assertTrue(p.startsWith("<?xml version=\"1.0\"?>"))
		assertTrue(p.contains("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"))
		assertTrue(p.contains("<u:AddPortMapping xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\"><NewRemoteHost></NewRemoteHost><NewExternalPort>51000</NewExternalPort>"))
		assertTrue(p.contains("<NewPortMappingDescription>Tiresias &lt;ayuda&gt; &amp; más</NewPortMappingDescription></u:AddPortMapping></s:Body></s:Envelope>"))
		// Sin argumentos
		assertTrue(Puertos.peticionSoap("urn:x:1", "GetExternalIPAddress", emptyList()).contains("<u:GetExternalIPAddress xmlns:u=\"urn:x:1\"></u:GetExternalIPAddress>"))
	}

	@Test
	fun ipDeLaRespuesta() {
		assertEquals("83.45.120.7", Puertos.ipDeRespuesta(respuestaIp))
		assertEquals("1.2.3.4", Puertos.ipDeRespuesta("<m:R xmlns:m=\"x\"><m:NewExternalIPAddress> 1.2.3.4 </m:NewExternalIPAddress></m:R>"))
		assertNull(Puertos.ipDeRespuesta("<NewExternalIPAddress></NewExternalIPAddress>"))
		assertNull(Puertos.ipDeRespuesta(error725))
	}

	private val local = InetAddress.getByName("127.0.0.1") as Inet4Address

	/** Un router UPnP de mentira en 127.0.0.1: sirve la descripción y contesta al SOAP. */
	private class RouterFalso(descripcion: String, private val ip: String, private val soloPermanentes: Boolean) : AutoCloseable {
		val servidor: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
		val acciones: MutableList<String> = Collections.synchronizedList(ArrayList())

		init {
			servidor.createContext("/rootDesc.xml") { h ->
				val b = descripcion.toByteArray()
				h.sendResponseHeaders(200, b.size.toLong())
				h.responseBody.use { it.write(b) }
			}
			servidor.createContext("/ctl/IPConn") { h ->
				val accion = h.requestHeaders.getFirst("SOAPAction")?.trim('"')?.substringAfter('#') ?: ""
				val cuerpo = h.requestBody.readBytes().toString(Charsets.UTF_8)
				acciones += accion + (Regex("<NewLeaseDuration>(\\d+)</NewLeaseDuration>").find(cuerpo)?.let { ":" + it.groupValues[1] } ?: "")
				val (codigo, respuesta) = when (accion) {
					"GetExternalIPAddress" -> 200 to "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:GetExternalIPAddressResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\"><NewExternalIPAddress>$ip</NewExternalIPAddress></u:GetExternalIPAddressResponse></s:Body></s:Envelope>"
					"AddPortMapping" -> if (soloPermanentes && !cuerpo.contains("<NewLeaseDuration>0</NewLeaseDuration>")) {
						500 to "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><detail><UPnPError><errorCode>725</errorCode></UPnPError></detail></s:Fault></s:Body></s:Envelope>"
					} else {
						200 to "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:AddPortMappingResponse xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:2\"/></s:Body></s:Envelope>"
					}
					"DeletePortMapping" -> 200 to "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:DeletePortMappingResponse/></s:Body></s:Envelope>"
					else -> 500 to "?"
				}
				val b = respuesta.toByteArray()
				h.sendResponseHeaders(codigo, b.size.toLong())
				h.responseBody.use { it.write(b) }
			}
			servidor.start()
		}

		val ubicacion get() = "http://127.0.0.1:${servidor.address.port}/rootDesc.xml"

		override fun close() = servidor.stop(0)
	}

	private fun dentroDe(ms: Long) = System.nanoTime() + ms * 1_000_000L

	@Test
	fun upnpContraUnRouterFalso() {
		RouterFalso(descripcionMiniupnpd.replace("http://192.168.1.1/", "/"), "83.45.120.7", soloPermanentes = false).use { r ->
			val mapeo = Puertos.mapearUpnp(r.ubicacion, local, 41234, 51000, 3600, "Tiresias", dentroDe(4000))
			assertNotNull(mapeo)
			assertEquals(InetAddress.getByName("83.45.120.7"), mapeo!!.ipPublica)
			assertEquals(51000, mapeo.puertoPublico)
			assertEquals(Mapeo.UPNP, mapeo.metodo)
			mapeo.cerrar()
			mapeo.cerrar()
			assertEquals(listOf("GetExternalIPAddress", "AddPortMapping:3600", "DeletePortMapping"), r.acciones.toList())
		}
	}

	@Test
	fun upnpQueSoloAdmitePermanentes() {
		RouterFalso(descripcionMiniupnpd, "83.45.120.7", soloPermanentes = true).use { r ->
			val mapeo = Puertos.mapearUpnp(r.ubicacion, local, 41234, 51000, 3600, "Tiresias", dentroDe(4000))
			assertNotNull(mapeo)
			assertEquals(listOf("GetExternalIPAddress", "AddPortMapping:3600", "AddPortMapping:0"), r.acciones.toList())
		}
	}

	@Test
	fun upnpConIpDeCgnatNoAbreNada() {
		RouterFalso(descripcionMiniupnpd, "100.72.13.5", soloPermanentes = false).use { r ->
			assertNull(Puertos.mapearUpnp(r.ubicacion, local, 41234, 51000, 3600, "Tiresias", dentroDe(4000)))
			assertEquals(listOf("GetExternalIPAddress"), r.acciones.toList())
		}
		RouterFalso(descripcionMiniupnpd, "192.168.0.2", soloPermanentes = false).use { r ->
			assertNull(Puertos.mapearUpnp(r.ubicacion, local, 41234, 51000, 3600, "Tiresias", dentroDe(4000)))
		}
	}

	@Test
	fun natPmpContraUnRouterFalso() {
		val socket = try {
			DatagramSocket(InetSocketAddress(local, 5351))
		} catch (e: BindException) {
			null
		}
		assumeTrue("El puerto UDP 5351 está ocupado en este PC", socket != null)
		val recibidas = Collections.synchronizedList(ArrayList<ByteArray>())
		val hilo = Thread {
			socket!!.use { s ->
				s.soTimeout = 5000
				val b = ByteArray(64)
				repeat(3) {
					val p = DatagramPacket(b, b.size)
					s.receive(p)
					val pet = b.copyOf(p.length)
					recibidas += pet
					val respuesta = if (pet[1].toInt() == 0) {
						ByteBuffer.allocate(12).put(0).put(128.toByte()).putShort(0).putInt(1234).put(byteArrayOf(83, 45, 120, 7)).array()
					} else {
						val entrada = ByteBuffer.wrap(pet)
						val interno = entrada.getShort(4)
						val externo = entrada.getShort(6)
						val vida = entrada.getInt(8)
						ByteBuffer.allocate(16).put(0).put(130.toByte()).putShort(0).putInt(1234).putShort(interno).putShort(externo).putInt(vida).array()
					}
					s.send(DatagramPacket(respuesta, respuesta.size, p.socketAddress))
				}
			}
		}
		hilo.start()
		// Sin UPnP en 127.0.0.1, solo puede salir por NAT-PMP
		val mapeo = Puertos.abrir(local, 41234, local, 51000, 3600, "Tiresias", esperaMs = 3000)
		assertNotNull(mapeo)
		assertEquals(Mapeo.NATPMP, mapeo!!.metodo)
		assertEquals(InetAddress.getByName("83.45.120.7"), mapeo.ipPublica)
		assertEquals(51000, mapeo.puertoPublico)
		mapeo.cerrar()
		hilo.join(6000)
		assertEquals(3, recibidas.size)
		val cierre = ByteBuffer.wrap(recibidas[2])
		assertEquals(2, recibidas[2][1].toInt())
		assertEquals(41234, cierre.getShort(4).toInt() and 0xFFFF)
		assertEquals(0, cierre.getShort(6).toInt())
		assertEquals(0, cierre.getInt(8))
	}

	@Test
	fun sinRouterDevuelveNullATiempo() {
		val inicio = System.nanoTime()
		val mapeo = Puertos.abrir(local, 41234, null, 51000, 3600, "Tiresias", esperaMs = 1200)
		val ms = (System.nanoTime() - inicio) / 1_000_000L
		assertNull(mapeo)
		assertTrue("Tardó $ms ms", ms < 4000)
	}
}
