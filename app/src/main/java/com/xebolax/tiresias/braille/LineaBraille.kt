package com.xebolax.tiresias.braille

import android.Manifest
import android.accessibilityservice.BrailleDisplayController
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.annotation.RequiresApi
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.braille.LineaHid
import com.xebolax.tiresias.nucleo.braille.OrdenLinea
import com.xebolax.tiresias.nucleo.braille.TablaBraille
import com.xebolax.tiresias.nucleo.braille.TecladoLinea
import com.xebolax.tiresias.nucleo.braille.VistaLinea
import com.xebolax.tiresias.voz.Sonido
import java.util.concurrent.Executor

/**
 * Una línea braille conectada con BrailleDisplayController (Android 15 o
 * posterior): Android abre el dispositivo HID de la línea, USB o Bluetooth, y
 * Tiresias le escribe las celdas y lee sus teclas. Sin controladores propios
 * ni BRLTTY: vale para cualquier línea que siga la norma HID Braille (las
 * actuales de Freedom Scientific, HumanWare, APH, Orbit, HIMS, Help Tech...).
 *
 * Todo pasa en el hilo del lector. Las teclas se convierten en órdenes
 * ([TecladoLinea]) que hace [Braille].
 */
@RequiresApi(35)
class LineaBraille(private val lector: Lector, private val alOrden: (OrdenLinea) -> Unit, private val alCambiarConexion: () -> Unit) {
	private val servicio = lector.servicio
	private val controlador: BrailleDisplayController? = runCatching { servicio.brailleDisplayController }.getOrNull()
	private val enHilo = Executor { lector.manejador.post(it) }
	/** Las combinaciones de la línea, con las que haya cambiado el usuario. */
	@Volatile private var acordes = TecladoLinea.leerAcordes(lector.ajustes.acordesBraille)
	private val teclado = TecladoLinea { acordes }

	/** Las combinaciones o el cursor han cambiado en Ajustes. */
	fun aplicarAjustes() {
		acordes = TecladoLinea.leerAcordes(lector.ajustes.acordesBraille)
		vista?.puntosCursor = lector.ajustes.puntosCursorBraille
	}

	private var linea: LineaHid? = null
	var vista: VistaLinea? = null
		private set

	/** Los dispositivos que quedan por probar en esta búsqueda y si se avisa de lo que pase. */
	private val candidatos = ArrayDeque<Any>()
	private var avisarBusqueda = false
	private var probando = false

	/** Un mensaje (una notificación, un anuncio) se ve un rato y luego vuelve lo que había. */
	private var mensajeHasta = 0L
	private val quitarMensaje = Runnable { escribir() }

	val conectada: Boolean get() = linea != null || virtual != null

	/** Una línea que es otra app (API): sus celdas y cómo mandarle lo que hay que ver. */
	private class Virtual(val nombre: String, val celdas: Int, val enviar: (ByteArray) -> Unit)
	private var virtual: Virtual? = null

	private val celdasLinea: Int get() = linea?.celdas ?: virtual?.celdas ?: 0

	/** Otra app hace de línea (un anotador, el programa del fabricante); solo si no hay una línea de verdad. */
	fun conectarVirtual(nombre: String, celdas: Int, enviar: (ByteArray) -> Unit): Boolean {
		if (linea != null || celdas !in 1..160) return false
		virtual = Virtual(nombre, celdas, enviar)
		vista = VistaLinea(celdas).apply { puntosCursor = lector.ajustes.puntosCursorBraille }
		lector.sonidos.tocar(Sonido.ENCENDIDO)
		lector.decir(lector.textos.getString(R.string.braille_linea_virtual, nombre, celdas))
		lector.manejador.postDelayed({ alCambiarConexion() }, 2000)
		lector.foco?.let { lector.braille.mostrarElemento(it) }
		return true
	}

	fun desconectarVirtual() {
		if (virtual == null) return
		virtual = null
		alDesconectar(true)
	}

	private val receptor = object : BroadcastReceiver() {
		override fun onReceive(c: Context, i: Intent) {
			when (i.action) {
				UsbManager.ACTION_USB_DEVICE_ATTACHED -> lector.manejador.postDelayed({ buscar(false) }, 800)
				UsbManager.ACTION_USB_DEVICE_DETACHED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> lector.manejador.post { comprobarConexion() }
				// La parte HID de Bluetooth tarda un poco más que el enlace en estar lista
				BluetoothDevice.ACTION_ACL_CONNECTED -> lector.manejador.postDelayed({ if (!conectada) buscar(false) }, 2500)
				PERMISO_USB -> if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) lector.manejador.post { buscar(true) }
			}
		}
	}

	fun iniciar() {
		if (controlador == null) return
		val filtro = IntentFilter().apply {
			addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
			addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
			addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
			addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
			addAction(PERMISO_USB)
		}
		runCatching { servicio.registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED) }
		lector.manejador.postDelayed({ buscar(false) }, 1500)
	}

	fun parar() {
		runCatching { servicio.unregisterReceiver(receptor) }
		desconectar()
	}

	fun desconectar() {
		runCatching { if (controlador?.isConnected == true) controlador.disconnect() }
		linea = null
		vista = null
	}

	/**
	 * Busca una línea entre los dispositivos USB HID y los Bluetooth emparejados
	 * y se conecta a la primera que lo sea. [avisar]: dice lo que pasa (cuando
	 * lo pide el usuario); si no, solo avisa al conectar.
	 */
	fun buscar(avisar: Boolean) {
		val c = controlador ?: run {
			if (avisar) lector.decir(R.string.braille_linea_no_disponible)
			return
		}
		if (conectada || probando) {
			if (avisar && conectada) lector.decir(R.string.braille_linea_conectada, linea?.celdas ?: 0)
			return
		}
		avisarBusqueda = avisar
		candidatos.clear()
		candidatos.addAll(dispositivosUsb(avisar))
		candidatos.addAll(dispositivosBluetooth(avisar))
		if (candidatos.isEmpty()) {
			if (avisar) lector.decir(R.string.braille_linea_ninguna)
			return
		}
		if (avisar) lector.decir(R.string.braille_linea_buscando)
		probarSiguiente(c)
	}

	private fun dispositivosUsb(pedirPermiso: Boolean): List<UsbDevice> {
		val usb = servicio.getSystemService(UsbManager::class.java) ?: return emptyList()
		val hid = usb.deviceList.values.filter { d -> (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_HID } }
		val conPermiso = hid.filter { usb.hasPermission(it) }
		// Sin permiso, Android pregunta al usuario (una vez por dispositivo); al aceptarlo se vuelve a buscar
		if (pedirPermiso || conPermiso.isEmpty()) {
			hid.firstOrNull { !usb.hasPermission(it) }?.let { d ->
				val pi = PendingIntent.getBroadcast(servicio, 0, Intent(PERMISO_USB).setPackage(servicio.packageName), PendingIntent.FLAG_MUTABLE)
				runCatching { usb.requestPermission(d, pi) }
			}
		}
		return conPermiso
	}

	private fun dispositivosBluetooth(avisar: Boolean): List<BluetoothDevice> {
		if (servicio.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			if (avisar) lector.decir(R.string.braille_linea_permiso_bt)
			return emptyList()
		}
		val adaptador = servicio.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
		val emparejados = runCatching { adaptador.bondedDevices.toList() }.getOrDefault(emptyList())
		// Primero los que por el nombre parecen líneas braille; los demás también se prueban (falla enseguida si no lo son)
		return emparejados.sortedByDescending { d -> runCatching { d.name }.getOrNull()?.let { NOMBRES.containsMatchIn(it) } == true }
	}

	private fun probarSiguiente(c: BrailleDisplayController) {
		val d = candidatos.removeFirstOrNull()
		if (d == null) {
			probando = false
			if (avisarBusqueda && !conectada) lector.decir(R.string.braille_linea_ninguna)
			return
		}
		probando = true
		val llamada = object : BrailleDisplayController.BrailleDisplayCallback {
			override fun onConnected(descriptor: ByteArray) = alConectar(c, descriptor)
			override fun onConnectionFailed(error: Int) {
				Registro.i(ETIQUETA, "No es una línea braille o no se pudo abrir (error $error)")
				probarSiguiente(c)
			}
			override fun onInput(informe: ByteArray) = alTeclas(informe)
			override fun onDisconnected() = alDesconectar()
		}
		val ok = runCatching {
			when (d) {
				is UsbDevice -> c.connect(d, enHilo, llamada)
				is BluetoothDevice -> {
					// Los Bluetooth solo se buscan con el permiso, pero puede haberse quitado mientras tanto
					if (servicio.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Sin permiso de dispositivos cercanos")
					c.connect(d, enHilo, llamada)
				}
			}
		}
		if (ok.isFailure) {
			Registro.w(ETIQUETA, "No se pudo probar un dispositivo", ok.exceptionOrNull())
			probarSiguiente(c)
		}
	}

	private fun alConectar(c: BrailleDisplayController, descriptor: ByteArray) {
		val l = LineaHid.crear(descriptor)
		if (l == null || l.celdas == 0) {
			runCatching { c.disconnect() }
			probarSiguiente(c)
			return
		}
		probando = false
		candidatos.clear()
		linea = l
		vista = VistaLinea(l.celdas).apply { puntosCursor = lector.ajustes.puntosCursorBraille }
		lector.sonidos.tocar(Sonido.ENCENDIDO)
		lector.decir(R.string.braille_linea_conectada, l.celdas)
		// «Primero braille» se aplica cuando ya se ha dicho que está conectada
		lector.manejador.postDelayed({ alCambiarConexion() }, 2000)
		// Lo que tiene el foco, en la línea desde el primer momento
		lector.foco?.let { lector.braille.mostrarElemento(it) }
	}

	private fun alDesconectar(estabaVirtual: Boolean = false) {
		val estaba = conectada || estabaVirtual
		linea = null
		vista = null
		probando = false
		// Lo primero, devolver la voz (si estaba callada por «primero braille»)
		alCambiarConexion()
		if (estaba) {
			lector.sonidos.tocar(Sonido.APAGADO)
			lector.decir(R.string.braille_linea_desconectada)
		}
	}

	private fun comprobarConexion() {
		if (conectada && controlador?.isConnected != true) alDesconectar()
	}

	private fun alTeclas(informe: ByteArray) {
		val l = linea ?: return
		teclado.alTeclas(l.teclas(informe))?.let { alOrden(it) }
	}

	/** Pone un texto en la línea (con cursor en los campos de texto). */
	fun mostrar(tabla: TablaBraille, texto: String, cursor: Int = -1) {
		val v = vista ?: return
		v.poner(tabla.traducir(texto), cursor)
		if (System.currentTimeMillis() < mensajeHasta) return
		escribir()
	}

	/** El cursor se movió en el texto que ya se ve. */
	fun moverCursor(cursor: Int) {
		val v = vista ?: return
		v.moverCursor(cursor)
		if (System.currentTimeMillis() >= mensajeHasta) escribir()
	}

	/** Un mensaje que se ve [ms] milisegundos y luego vuelve lo que había. */
	fun mensaje(tabla: TablaBraille, texto: String, ms: Long) {
		val n = celdasLinea
		if (n == 0) return
		val temporal = VistaLinea(n).apply { poner(tabla.traducir(texto)) }
		mensajeHasta = System.currentTimeMillis() + ms
		enviar(temporal.celdas())
		lector.manejador.removeCallbacks(quitarMensaje)
		lector.manejador.postDelayed(quitarMensaje, ms)
	}

	/** Desplaza la línea por el texto; false si ya se ve el final (o el principio). */
	fun desplazar(adelante: Boolean): Boolean {
		val v = vista ?: return false
		mensajeHasta = 0
		val movido = if (adelante) v.avanzar() else v.retroceder()
		if (movido) escribir()
		return movido
	}

	private fun escribir() {
		mensajeHasta = 0
		vista?.let { enviar(it.celdas()) }
	}

	private fun enviar(celdas: ByteArray) {
		virtual?.let { v ->
			runCatching { v.enviar(celdas) }.onFailure { Registro.w(ETIQUETA, "La línea de ${v.nombre} no contesta", it) }
			return
		}
		val l = linea ?: return
		val c = controlador ?: return
		runCatching { c.write(l.informe(celdas)) }.onFailure {
			Registro.w(ETIQUETA, "No se pudo escribir en la línea", it)
			comprobarConexion()
		}
	}

	companion object {
		private const val ETIQUETA = "LineaBraille"
		private const val PERMISO_USB = "com.xebolax.tiresias.PERMISO_USB_BRAILLE"
		/** Nombres Bluetooth de las líneas más comunes (solo para probarlas antes). */
		private val NOMBRES = Regex("(?i)braille|focus|brailliant|orbit|mantis|chameleon|activator|actilino|basic braille|esys|esytime|hims|qbraille|braille ?sense|brailleedge|bnote|nlS|apex|vario|seika|caiku|refreshabraille|pronto|alva|easy ?link|bbe|hid ?braille")
	}
}
