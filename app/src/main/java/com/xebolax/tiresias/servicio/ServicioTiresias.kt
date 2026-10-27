package com.xebolax.tiresias.servicio

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.braille.Braille
import com.xebolax.tiresias.escucha.Escucha
import com.xebolax.tiresias.lectura.Ejecutor
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Diccionario
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.scripts.Scripts
import com.xebolax.tiresias.teclado.Teclado
import com.xebolax.tiresias.ventanas.Ventanas
import com.xebolax.tiresias.vision.Vision
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Sonidos
import com.xebolax.tiresias.voz.Tipo
import com.xebolax.tiresias.voz.Vibracion
import com.xebolax.tiresias.voz.Voz
import java.io.File

/**
 * El servicio de accesibilidad de Tiresias: el lector de pantalla.
 *
 * Aquí solo se conectan las piezas: la voz ([Voz]), el lector ([Lector], que
 * procesa los eventos en su propio hilo), los gestos, el teclado y las ventanas
 * propias. El hilo principal no hace nada lento: los eventos se copian y pasan
 * al hilo del lector; los gestos y las teclas se deciden al momento y lo que
 * haya que hacer va también al hilo del lector.
 *
 * Declara `isAccessibilityTool` (en Android 17 con la protección avanzada solo
 * funcionan los servicios que lo son) y es de arranque directo: habla en la
 * pantalla de bloqueo justo después de reiniciar.
 */
class ServicioTiresias : AccessibilityService() {
	private var voz: Voz? = null
	private var sonidos: Sonidos? = null
	private var lector: Lector? = null
	private var ejecutor: Ejecutor? = null
	private var teclado: Teclado? = null
	private var ventanas: Ventanas? = null
	private var paqueteActual: String? = null
	private var ordenesPrueba: OrdenesPrueba? = null

	private val reanudar = object : BroadcastReceiver() {
		override fun onReceive(c: Context?, i: Intent?) {
			lector?.manejador?.post { ejecutor?.suspender(false) }
		}
	}

	/** Al encender la pantalla, Tiresias vuelve si estaba suspendido: nunca deja a nadie atrapado. */
	private val pantalla = object : BroadcastReceiver() {
		override fun onReceive(c: Context?, i: Intent?) {
			when (i?.action) {
				Intent.ACTION_SCREEN_ON -> lector?.manejador?.post {
					if (ejecutor?.suspendido == true) ejecutor?.suspender(false)
					// Lo que el usuario quiere oír al encender la pantalla (Ajustes, Notificaciones y pantalla)
					when (lector?.ajustes?.alEncender) {
						"hora" -> lector?.decir(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date()), Modo.ENCOLAR)
						"estado" -> ejecutor?.ejecutar(Accion.ESTADO_SISTEMA)
					}
				}
				// Al desbloquear (Backtalk 31 y 149)
				Intent.ACTION_USER_PRESENT -> lector?.manejador?.post {
					when (lector?.ajustes?.alDesbloquear) {
						"desbloqueado" -> lector?.decir(R.string.desbloqueado, Modo.ENCOLAR)
						"hora" -> lector?.decir(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date()), Modo.ENCOLAR)
						"estado" -> ejecutor?.ejecutar(Accion.ESTADO_SISTEMA)
					}
				}
				Intent.ACTION_SCREEN_OFF -> {
					lector?.continua?.parar()
					// Lo copiado por Tiresias solo vive mientras se usa el móvil
					lector?.edicion?.olvidarCopiado()
					lector?.manejador?.post { lector?.salirVirtual(false) }
					lector?.let { l -> if (l.visionListo) l.vision.pararDescripcionContinua() }
					voz?.callar()
					lector?.let { l ->
						if (l.ajustes.alApagar) {
							l.sonidos.tocar(Sonido.APAGADO)
							l.decir(R.string.pantalla_apagada, modo = Modo.ENCOLAR)
						}
					}
				}
			}
		}
	}

	override fun onServiceConnected() {
		super.onServiceConnected()
		try {
			iniciar()
		} catch (e: Exception) {
			// Si algo falla al arrancar, al menos que hable para decirlo
			Registro.e(ETIQUETA, "Fallo al iniciar Tiresias", e)
		}
	}

	private fun iniciar() {
		Registro.iniciar(this)
		com.xebolax.tiresias.voz.PistaAudio.multimedia = Ajustes(this).vozMultimedia
		val v = Voz(this)
		voz = v
		val s = Sonidos(this)
		sonidos = s
		Thread({ s.cargar() }, "tiresias-sonidos").start()
		val vibracion = Vibracion(this)
		val vent = Ventanas(this)
		ventanas = vent
		val l = Lector(this, v, s, vibracion)
		l.hayVentanaPropia = { vent.hayAbierta }
		lector = l
		val ej = Ejecutor(l, vent) { suspendido -> configurar(suspendido) }
		ejecutor = ej
		val alAccion: (Accion) -> Unit = { a -> l.manejador.post { ej.ejecutar(a) } }
		l.vision = Vision(l, vent)
		l.escucha = Escucha(l, alAccion) { texto -> l.manejador.post { ej.buscarTexto(texto) } }
		l.braille = Braille(l, alAccion).also { b -> l.manejador.post { b.iniciar() } }
		l.scripts = Scripts(l).also { it.ventanas = vent }
		l.manejador.post { l.scripts.recargar() }
		teclado = Teclado(l, alAccion)
		configurar(false)
		cargarDiccionario(v)
		registerReceiver(pantalla, IntentFilter().apply {
			addAction(Intent.ACTION_SCREEN_ON)
			addAction(Intent.ACTION_SCREEN_OFF)
			addAction(Intent.ACTION_USER_PRESENT)
		})
		// «Reanudar» de la notificación de pausa (solo la manda Tiresias: no se exporta)
		androidx.core.content.ContextCompat.registerReceiver(this, reanudar, IntentFilter(AvisoPausa.REANUDAR), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
		instancia = this
		if (BuildConfig.PRUEBAS) {
			ordenesPrueba = OrdenesPrueba({ g -> alGestoTiresias(g) }, alAccion).also { it.registrar(this) }
		}

		// Aviso de arranque: con la voz de Tiresias, que no depende de nada más
		val ajustes = Ajustes(this)
		val t = Idioma.envolver(this)
		val anterior = ajustes.versionAnterior
		val frase = if (anterior in 1 until BuildConfig.VERSION_CODE) t.getString(R.string.servicio_actualizado, BuildConfig.VERSION_NAME) else t.getString(R.string.servicio_activo)
		ajustes.versionAnterior = BuildConfig.VERSION_CODE
		s.tocar(Sonido.ENCENDIDO)
		v.decir(Enunciado(frase, Modo.ENCOLAR, Tipo.SISTEMA))
	}

	/** Pone la configuración del servicio. Suspendido: sin exploración táctil ni gestos (solo las teclas de volumen). */
	private fun configurar(suspendido: Boolean) {
		val info = serviceInfo ?: return
		var banderas = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
			AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
			AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
			AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
		if (Build.VERSION.SDK_INT >= 26) banderas = banderas or AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME
		if (!suspendido) {
			banderas = banderas or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
			if (Build.VERSION.SDK_INT >= 30) {
				// El doble toque (y el doble toque mantenido) los hace Tiresias sobre lo que acaba de decir,
				// como TalkBack: si los hace Android, actúa sobre SU foco, que puede no ser el que se ha oído
				// (en los menús de Tiresias y en las listas había que repetir el doble toque)
				banderas = banderas or AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES or AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP
			}
			if (Build.VERSION.SDK_INT >= 31) banderas = banderas or AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
		}
		info.flags = banderas
		info.eventTypes = if (suspendido) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else AccessibilityEvent.TYPES_ALL_MASK
		info.notificationTimeout = 0
		serviceInfo = info
	}

	private fun cargarDiccionario(v: Voz) {
		val f = File(Almacen.protegido(this).filesDir, "diccionario.tsv")
		if (!f.isFile) return
		v.diccionario = runCatching { Diccionario.leer(f.readText()) }.getOrElse {
			Registro.e(ETIQUETA, "No se pudo leer el diccionario", it)
			Diccionario.VACIO
		}
	}

	override fun onAccessibilityEvent(e: AccessibilityEvent?) {
		e ?: return
		val l = lector ?: return
		if (ejecutor?.suspendido == true) return
		when (e.eventType) {
			AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
				val paquete = e.packageName?.toString()
				if (paquete != null && paquete != paqueteActual && paquete != packageName) {
					paqueteActual = paquete
					l.gestos.alCambiarApp(paquete)
					l.perfiles.alCambiarApp(paquete)
				}
			}
			AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> l.gestos.alTerminarToque()
		}
		l.alEvento(e)
	}

	@androidx.annotation.RequiresApi(30)
	override fun onGesture(gesto: AccessibilityGestureEvent): Boolean = alGesto(gesto.gestureId)

	@Deprecated("Deprecated in Java")
	override fun onGesture(gestureId: Int): Boolean = alGesto(gestureId)

	private fun alGesto(id: Int): Boolean {
		val gesto = lector?.gestos?.deAndroid(id) ?: return false
		return alGestoTiresias(gesto)
	}

	/** Un gesto ya traducido (de Android o de las órdenes de prueba). */
	private fun alGestoTiresias(gesto: Gesto): Boolean {
		val l = lector ?: return false
		val ej = ejecutor ?: return false
		val mapa = l.perfiles.mapaGestos(l.ajustes.mapaGestos, paqueteActual)
		val accion = mapa[gesto]
		// Un gesto de verdad (el doble toque) manda sobre «un toque activa» del modo senior
		l.manejador.post { l.cancelarToqueActiva() }
		// El gesto de emergencia funciona siempre, aunque Tiresias esté suspendido o en ayuda de entrada
		if (gesto == Gesto.TRIPLE_TOQUE_MANTENIDO_4) {
			l.manejador.post { ej.ejecutar(Accion.MODO_SEGURO) }
			return true
		}
		if (l.ayudaEntrada && accion != Accion.AYUDA_ENTRADA) {
			l.manejador.post { l.decir(l.textos.getString(R.string.ayuda_entrada_gesto, nombreGesto(gesto), ej.nombreAccion(accion))) }
			return true
		}
		l.manejador.post { ej.ejecutar(accion) }
		return true
	}

	private fun nombreGesto(g: Gesto): String {
		val t = Idioma.envolver(this)
		val id = t.resources.getIdentifier("gesto_" + g.name.lowercase(), "string", packageName)
		return if (id != 0) t.getString(id) else g.name.lowercase()
	}

	override fun onKeyEvent(e: KeyEvent?): Boolean {
		e ?: return false
		return try {
			teclado?.alPulsar(e) ?: false
		} catch (ex: Exception) {
			Registro.e(ETIQUETA, "Fallo con una tecla", ex)
			false
		}
	}

	override fun onInterrupt() {
		voz?.callar()
	}

	override fun onUnbind(intent: Intent?): Boolean {
		cerrar()
		return super.onUnbind(intent)
	}

	override fun onDestroy() {
		cerrar()
		super.onDestroy()
	}

	private fun cerrar() {
		if (instancia !== this) return
		instancia = null
		runCatching { unregisterReceiver(pantalla) }
		runCatching { unregisterReceiver(reanudar) }
		AvisoPausa.quitar(this)
		ordenesPrueba?.let { o -> runCatching { unregisterReceiver(o) } }
		runCatching { lector?.vision?.cerrar() }
		runCatching { lector?.escucha?.cerrar() }
		runCatching { lector?.takeIf { it.scriptsListos }?.scripts?.cerrar() }
		runCatching { lector?.braille?.parar() }
		runCatching { ventanas?.ponerCortina(false) }
		runCatching { lector?.cerrar() }
		runCatching {
			sonidos?.tocar(Sonido.APAGADO)
			voz?.cerrar()
		}
		runCatching { sonidos?.cerrar() }
	}

	/** Para las pantallas de Tiresias: recargar los ajustes al cambiarlos y probar la voz. */
	fun recargarAjustes() {
		lector?.recargarAjustes()
		voz?.let { cargarDiccionario(it) }
	}

	fun probarVoz(texto: String) = voz?.probar(texto)

	/** Otra app hace de línea braille (API); false si ya hay una línea o no se puede (antes de Android 15). */
	fun conectarLineaVirtual(nombre: String, celdas: Int, enviar: (ByteArray) -> Unit): Boolean {
		val l = lector ?: return false
		if (!l.brailleListo) return false
		return l.braille.conectarVirtual(nombre, celdas, enviar)
	}

	fun desconectarLineaVirtual() {
		val l = lector ?: return
		if (l.brailleListo) l.manejador.post { l.braille.desconectarVirtual() }
	}

	/** Una tecla de la línea de otra app («desplazar:adelante», «celda:3», «puntos:13», «accion:ATRAS»...). */
	fun teclaLineaVirtual(orden: String) {
		val l = lector ?: return
		if (!l.brailleListo) return
		val (tipo, valor) = orden.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
		val o: com.xebolax.tiresias.nucleo.braille.OrdenLinea = when (tipo) {
			"desplazar" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Desplazar(valor != "atras")
			"celda" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Encaminar(valor.toIntOrNull() ?: return)
			"puntos" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Escribir(com.xebolax.tiresias.nucleo.braille.Puntos.de(valor))
			"accion" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Hacer(runCatching { Accion.valueOf(valor.uppercase()) }.getOrNull() ?: return)
			"espacio" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Espacio
			"borrar" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Borrar
			"intro" -> com.xebolax.tiresias.nucleo.braille.OrdenLinea.Intro
			else -> return
		}
		l.braille.ordenVirtual(o)
	}

	/** Abre un archivo BRF en la línea braille; false si no hay línea. */
	fun abrirBrf(texto: String): Boolean {
		val l = lector ?: return false
		return l.brailleListo && l.braille.abrirBrf(texto)
	}

	/** Vuelve a cargar los sonidos (tras cambiar de tema o un archivo propio), en segundo plano. */
	fun recargarSonidos() {
		val s = sonidos ?: return
		Thread({ s.cargar() }, "tiresias-sonidos").start()
	}

	/** Vuelve a aplicar el toque directo a la app de delante (al pausarlo o reanudarlo desde los ajustes rápidos). */
	fun aplicarToqueDirecto() {
		val l = lector ?: return
		l.manejador.post { l.gestos.alCambiarApp(paqueteActual) }
	}

	/** Una app marca una zona de su pantalla para el toque directo (API, [com.xebolax.tiresias.api.ServicioApi.ZONA_TOQUE_DIRECTO]). */
	fun zonaDesdeApp(paquete: String, zona: android.graphics.Rect?) {
		val l = lector ?: return
		l.manejador.post { l.gestos.zonaDeApp(paquete, zona) }
	}

	/** Suena (y vibra) un sonido, para probarlo desde Ajustes, Sonidos uno a uno. */
	fun probarSonido(s: Sonido) {
		val l = lector ?: return
		l.sonidos.tocar(s)
		l.vibracion.vibrar(s)
	}

	// --- API para otras apps (api/ServicioApi) ---------------------------------------------

	private val preguntandoApi = HashSet<String>()

	/** Una app que no tiene permiso quiere usar la API: se pregunta al usuario una vez. */
	fun pedirPermisoApi(paquete: String) {
		val l = lector ?: return
		val v = ventanas ?: return
		if (!preguntandoApi.add(paquete)) return
		val t = l.textos
		val nombre = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrDefault(paquete)
		v.lista(t.getString(R.string.api_pregunta, nombre), listOf(t.getString(R.string.api_permitir), t.getString(R.string.api_no_permitir)), alCancelar = {
			preguntandoApi.remove(paquete)
		}) { i ->
			preguntandoApi.remove(paquete)
			Ajustes(this).permitirApi(paquete, i == 0)
			l.decir(t.getString(if (i == 0) R.string.api_permitida else R.string.api_denegada, nombre))
		}
	}

	fun decirDesdeApp(texto: String, interrumpir: Boolean) {
		val l = lector ?: return
		l.manejador.post {
			l.voz.decir(Enunciado(texto, if (interrumpir) Modo.INTERRUMPIR else Modo.ENCOLAR, Tipo.ANUNCIO))
			if (l.brailleListo) l.braille.mensaje(texto)
		}
	}

	fun callarDesdeApp() {
		voz?.callar()
	}

	fun brailleDesdeApp(texto: String) {
		val l = lector ?: return
		if (l.brailleListo) l.braille.mensaje(texto)
	}

	/** Busca y conecta una línea braille (desde los ajustes). */
	fun buscarLineaBraille() {
		val l = lector ?: return
		l.manejador.post { if (l.brailleListo) l.braille.buscarLinea() }
	}

	/** Vuelve a cargar los complementos (los cambió su pantalla). */
	fun recargarComplementos() {
		val l = lector ?: return
		l.manejador.post { if (l.scriptsListos) l.scripts.recargar() }
	}

	/** Para las pruebas instrumentadas: hace [a] como si llegara de un gesto. */
	fun ejecutarPrueba(a: Accion) {
		val l = lector ?: return
		l.manejador.post { ejecutor?.ejecutar(a) }
	}

	/** Para las pruebas instrumentadas: un gesto como si lo hubiera reconocido Android. */
	fun gestoPrueba(g: Gesto) {
		alGestoTiresias(g)
	}

	/** Para las pruebas instrumentadas: la voz (su historial y su latencia). */
	val vozPruebas: Voz? get() = voz

	/** Para las pruebas instrumentadas: el lector (control de lectura, foco). */
	val lectorPruebas: Lector? get() = lector

	/** Para las pruebas instrumentadas: una tecla como si llegara del teclado (devuelve si Tiresias se la queda). */
	fun teclaPrueba(e: android.view.KeyEvent): Boolean = teclado?.alPulsar(e) ?: false

	/** Para las pruebas instrumentadas: si hay un menú o diálogo de Tiresias abierto. */
	val ventanaAbiertaPruebas: Boolean get() = ventanas?.hayAbierta == true || ejecutor?.circular?.abierto == true

	/** Para las pruebas instrumentadas: cierra el menú o diálogo de Tiresias que haya. */
	/** «Ir al elemento número…» sin la ventana que pide el número. */
	fun irALaFilaPruebas(fila: Int) {
		lector?.manejador?.post { ejecutor?.irALaFila(fila) }
	}

	fun cerrarVentanaPruebas() {
		ventanas?.cerrar()
		ejecutor?.circular?.cerrar()
	}

	/** La zona de práctica del tutorial: los gestos se dicen en vez de hacerse. */
	fun alternarAyudaEntrada() {
		val l = lector ?: return
		l.manejador.post { ejecutor?.ejecutar(Accion.AYUDA_ENTRADA) }
	}

	companion object {
		private const val ETIQUETA = "ServicioTiresias"

		/** El servicio conectado ahora, o null si Tiresias no está activo. */
		@Volatile var instancia: ServicioTiresias? = null
			private set
	}
}
