package com.xebolax.tiresias.remoto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.remoto.Emparejar
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import com.xebolax.tiresias.nucleo.remoto.MensajeAyuda
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.ui.ActividadAyudar
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Tipo
import java.util.concurrent.CopyOnWriteArrayList

/**
 * La asistencia remota entre dos móviles con Tiresias, para personas de total
 * confianza (una hija que ayuda a su madre ciega desde su casa): la persona
 * que necesita ayuda (la «ayudada») la pide con un botón y su móvil manda solo
 * la invitación a su contacto de ayuda (WhatsApp o SMS) o la comparte; quien
 * ayuda (el «ayudante») la abre con su Tiresias y los dos móviles se enlazan, sin
 * ningún servidor propio: el de la persona ayudada escucha en un puerto y la
 * invitación lleva sus direcciones (la de la wifi, la IPv6, la pública si el
 * router abre un puerto con UPnP o NAT-PMP) y un servicio onion de [Tor], que
 * llega aunque los dos estén con datos móviles.
 *
 * Quien ayuda oye lo que dice el otro móvil (con su propia voz) y lo maneja: sus
 * gestos de Tiresias, los botones de su pantalla, escribir texto, abrir apps,
 * enlaces o la tienda. Lo ve todo menos las contraseñas, que no se mandan. La
 * persona ayudada oye que la están ayudando, cada pocos minutos, y lo corta
 * cuando quiera con las dos teclas de volumen, el gesto de emergencia, la
 * notificación o el menú. Todo va cifrado de extremo a extremo y nada se
 * guarda: solo existe mientras dura la ayuda.
 *
 * Solo entra quien la persona ha aceptado ([Confianza]), nunca durante una llamada
 * si no está en la lista, y nada de banca ni códigos pasa por la ayuda: es lo que
 * frena el guion de las estafas de falso soporte técnico.
 *
 * Este objeto lleva las dos sesiones posibles (como mucho una de cada) y lo
 * que necesitan las pantallas.
 */
object Asistencia {
	private const val ETIQUETA = "Asistencia"
	private const val CANAL = "ayuda_remota"
	private const val ID_AYUDADO = 7311
	private const val ID_PETICION = 7312
	const val TERMINAR = "com.xebolax.tiresias.AYUDA_TERMINAR"
	const val EXTRA_INVITACION = "invitacion"

	/** La sesión de este móvil cuando lo ayudan, o null. */
	@Volatile var ayudado: SesionAyudado? = null
		private set

	/** La sesión de este móvil cuando ayuda a otro, o null. */
	@Volatile var ayudante: SesionAyudante? = null
		private set

	/** Quien quiere saber que algo ha cambiado (las pantallas): se llama en cualquier hilo. */
	val oyentes = CopyOnWriteArrayList<() -> Unit>()

	internal fun avisarCambio() {
		for (o in oyentes) runCatching { o() }.onFailure { Registro.w(ETIQUETA, "Un oyente de la asistencia falló", it) }
	}

	// --- Pedir ayuda (este móvil es el ayudado) -----------------------------------------------------

	/**
	 * La acción «Pedir ayuda a distancia»: empieza a esperar al ayudante y manda
	 * la invitación al contacto de ayuda por el medio elegido. Sin contacto, la
	 * comparte. Necesita Tiresias activo (lo hace él).
	 */
	fun pedirAyuda(c: Context): Boolean {
		val servicio = ServicioTiresias.instancia
		val lector = servicio?.lectorActual
		if (servicio == null || lector == null) return false
		val a = Ajustes(c)
		val enviar = if (a.seniorContactoAyuda.isBlank()) PeticionAyuda.COMPARTIR else a.ayudaMetodo
		if (modoAvion(c)) lector.decir(R.string.ayuda_modo_avion)
		empezarAyudado(servicio) { inv ->
			if (inv == null) ofrecerLlamada(servicio, lector)
			else {
				PeticionAyuda.enviar(servicio, lector, inv, enviar)
				ayudado?.seguirPeticion()
			}
		}
		return true
	}

	private fun modoAvion(c: Context): Boolean =
		android.provider.Settings.Global.getInt(c.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1

	/** No se ha podido preparar la ayuda: que la persona no se quede esperando y pueda llamar. */
	private fun ofrecerLlamada(servicio: ServicioTiresias, lector: Lector) {
		val a = lector.ajustes
		val quien = a.seniorNombreAyuda.ifBlank { a.seniorContactoAyuda }
		val ventanas = servicio.ventanasActuales
		if (quien.isBlank() || ventanas == null) return lector.decir(R.string.ayuda_no_empieza)
		val titulo = lector.textos.getString(R.string.ayuda_no_empieza_llamar, quien)
		lector.decir(titulo, Modo.INTERRUMPIR, Tipo.SISTEMA)
		ventanas.lista(titulo, listOf(lector.textos.getString(R.string.ayuda_llamar_a, quien), lector.textos.getString(R.string.cancelar))) { i ->
			if (i == 0) servicio.llamarAyuda(a.seniorContactoAyuda)
		}
	}

	/** La acción de ayuda remota mientras alguien te ayuda: dice quién y sigue. Devuelve si lo ha dicho. */
	fun decirQuienAyuda(lector: Lector): Boolean {
		val s = ayudado?.takeIf { it.conectada } ?: return false
		lector.decir(lector.textos.getString(R.string.ayuda_quien, s.nombreAyudante), Modo.INTERRUMPIR, Tipo.SISTEMA)
		return true
	}

	/**
	 * Empieza la sesión de este móvil como ayudado (si no hay una ya) y da la
	 * invitación a [alListo] en el hilo del lector cuando está (abrir el puerto
	 * en el router tarda unos segundos), o null si no se ha podido.
	 */
	fun empezarAyudado(servicio: ServicioTiresias, alListo: (Invitacion?) -> Unit) {
		val lector = servicio.lectorActual ?: return alListo(null)
		ayudado?.let { s ->
			if (s.activa) {
				lector.manejador.post { alListo(s.invitacion) }
				return
			}
		}
		val s = SesionAyudado(servicio, lector)
		ayudado = s
		lector.decir(R.string.ayuda_preparando)
		Thread({
			val inv = try {
				s.empezar()
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudo empezar a esperar la ayuda", e)
				null
			}
			if (inv == null && ayudado === s) ayudado = null
			avisarCambio()
			lector.manejador.post { alListo(inv) }
		}, "tiresias-ayuda-empezar").start()
	}

	/** La sesión de ayudado ha terminado (la quita ella al terminar). */
	internal fun alTerminarAyudado(s: SesionAyudado) {
		if (ayudado === s) ayudado = null
		avisarCambio()
	}

	// --- Ayudar (este móvil es el ayudante) ---------------------------------------------------------

	/** Empieza a ayudar con [inv] (o con el código corto de la misma wifi). La pantalla de ayudar pone sus oyentes. */
	fun empezarAyudante(c: Context, inv: Invitacion?, codigoCorto: String?): SesionAyudante {
		ayudante?.terminar(SesionAyudante.FIN_OTRA)
		val s = SesionAyudante(c.applicationContext, inv, codigoCorto)
		ayudante = s
		s.conectar()
		avisarCambio()
		return s
	}

	internal fun alTerminarAyudante(s: SesionAyudante) {
		if (ayudante === s) {
			ayudante = null
			controlando = false
		}
		avisarCambio()
	}

	/** Manejando el otro móvil con los gestos propios de Tiresias (solo el ayudante). */
	@Volatile var controlando = false
		private set

	/** Lo que se queda en este móvil mientras se maneja el otro: salir, el volumen y la voz propia. */
	private val LOCALES = setOf(
		Accion.NINGUNA, Accion.AYUDA_CONTROLAR, Accion.AYUDA_TERMINAR, Accion.AYUDA_REMOTA, Accion.MODO_SEGURO, Accion.SUSPENDER,
		Accion.VOLUMEN_MAS, Accion.VOLUMEN_MENOS, Accion.VELOCIDAD_MAS, Accion.VELOCIDAD_MENOS, Accion.ALTERNAR_VOZ,
	)

	/** La acción «Manejar el otro móvil con mis gestos»: lo pone o lo quita. */
	fun alternarControl(): Boolean {
		val lector = ServicioTiresias.instancia?.lectorActual ?: return false
		val s = ayudante
		if (s == null || !s.conectada) {
			lector.decir(R.string.ayuda_no_ayudando)
			return false
		}
		controlando = !controlando
		lector.decir(if (controlando) lector.textos.getString(R.string.ayuda_controlando_si, s.nombreOtro) else lector.textos.getString(R.string.ayuda_controlando_no))
		avisarCambio()
		return true
	}

	/**
	 * Manejando el otro móvil: [a] va al otro (y se dice allí) en vez de hacerse
	 * aquí, salvo lo de [LOCALES]. Pausar la voz calla las dos. Devuelve si se ha mandado.
	 */
	fun redirigir(a: Accion): Boolean {
		if (!controlando || a in LOCALES) return false
		val s = ayudante ?: return false
		if (!s.conectada) return false
		if (a == Accion.PAUSAR_VOZ) ServicioTiresias.instancia?.lectorActual?.voz?.callar()
		s.enviar(MensajeAyuda.HacerAccion(a.name))
		return true
	}

	// --- Terminar ---------------------------------------------------------------------------------

	/**
	 * Termina lo que haya (las dos teclas de volumen, la salida de emergencia,
	 * la acción de terminar, la notificación). Devuelve si había algo.
	 */
	fun terminarPorUsuario(): Boolean {
		var habia = false
		ayudado?.let { if (it.activa) { it.terminar(SesionAyudado.FIN_USUARIO); habia = true } }
		ayudante?.let { it.terminar(SesionAyudante.FIN_USUARIO); habia = true }
		return habia
	}

	/** La pantalla se apaga o se enciende: la persona ayudada pone en pausa la ayuda o la reanuda. */
	fun alPantalla(encendida: Boolean) {
		ayudado?.alPantalla(encendida)
	}

	// --- Invitaciones que llegan (este móvil puede ayudar) ------------------------------------------

	/**
	 * Una notificación de otra app (un WhatsApp o un SMS) con una invitación de
	 * Tiresias dentro: Tiresias pone su propia notificación «Ana te pide ayuda:
	 * toca para ayudar». Solo se mira el texto en memoria; no se guarda.
	 */
	fun mirarAviso(c: Context, titulo: String, texto: String) {
		if (texto.contains(Emparejar.PREFIJO_OFERTA, ignoreCase = true)) return mirarOferta(c, texto)
		if (!texto.contains("TIR1", ignoreCase = true)) return
		val inv = Invitacion.leer(texto) ?: return
		if (inv.caducada(System.currentTimeMillis() / 1000)) return
		if (ayudado?.invitacion?.codigo() == inv.codigo()) return
		notificarPeticion(c, inv, titulo)
	}

	/** Una persona de confianza se ofrece a ayudar: se le pregunta a quien usa este móvil y, si dice que sí, se pide la ayuda. */
	private fun mirarOferta(c: Context, texto: String) {
		val p = Emparejar.leerOferta(texto, Confianza.personas(c), System.currentTimeMillis() / 1000) ?: return
		val servicio = ServicioTiresias.instancia ?: return
		val lector = servicio.lectorActual ?: return
		val ventanas = servicio.ventanasActuales ?: return
		if (ayudado != null || p.id == ofertaVista) return
		ofertaVista = p.id
		val titulo = lector.textos.getString(R.string.ayuda_oferta_pregunta, p.nombre)
		lector.decir(titulo, Modo.INTERRUMPIR, Tipo.SISTEMA)
		ventanas.lista(titulo, listOf(lector.textos.getString(R.string.ayuda_pregunta_si), lector.textos.getString(R.string.ayuda_pregunta_no)), alCancelar = { ofertaVista = null }) { i ->
			ofertaVista = null
			if (i == 0) pedirAyuda(servicio)
		}
	}

	/** La oferta que está preguntando ahora, para no preguntarla dos veces por cada notificación repetida. */
	@Volatile private var ofertaVista: String? = null

	private fun notificarPeticion(c: Context, inv: Invitacion, de: String) {
		if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
		val nm = c.getSystemService(NotificationManager::class.java) ?: return
		val t = Idioma.envolver(c)
		try {
			crearCanal(c, nm)
			val abrir = PendingIntent.getActivity(c, 1, Intent(c, ActividadAyudar::class.java).putExtra(EXTRA_INVITACION, inv.codigo()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val quien = inv.nombre.ifBlank { de }
			val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CANAL) else @Suppress("DEPRECATION") Notification.Builder(c)
			val n = b.setSmallIcon(R.drawable.ic_toque_directo)
				.setContentTitle(t.getString(R.string.ayuda_peticion_titulo, quien))
				.setContentText(t.getString(R.string.ayuda_peticion_texto))
				.setAutoCancel(true)
				.setCategory(Notification.CATEGORY_MESSAGE)
				.setContentIntent(abrir)
				.addAction(Notification.Action.Builder(null, t.getString(R.string.ayuda_peticion_ayudar), abrir).build())
				.build()
			nm.notify(ID_PETICION, n)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo avisar de la petición de ayuda", e)
		}
	}

	private fun crearCanal(c: Context, nm: NotificationManager) {
		if (Build.VERSION.SDK_INT >= 26) {
			nm.createNotificationChannel(NotificationChannel(CANAL, Idioma.envolver(c).getString(R.string.ayuda_canal), NotificationManager.IMPORTANCE_HIGH))
		}
	}

	/** La notificación fija de la persona ayudada: quién la ayuda y «Terminar». */
	internal fun notificarAyudado(c: Context, texto: String?) {
		val nm = c.getSystemService(NotificationManager::class.java) ?: return
		if (texto == null) {
			runCatching { nm.cancel(ID_AYUDADO) }
			return
		}
		if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
		val t = Idioma.envolver(c)
		try {
			crearCanal(c, nm)
			val terminar = PendingIntent.getBroadcast(c, 2, Intent(TERMINAR).setPackage(c.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val abrir = PendingIntent.getActivity(c, 3, Intent(c, com.xebolax.tiresias.ui.ActividadAyudaRemota::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CANAL) else @Suppress("DEPRECATION") Notification.Builder(c)
			val n = b.setSmallIcon(R.drawable.ic_toque_directo)
				.setContentTitle(t.getString(R.string.ayuda_notificacion_titulo))
				.setContentText(texto)
				.setOngoing(true)
				.setOnlyAlertOnce(true)
				.setCategory(Notification.CATEGORY_STATUS)
				.setContentIntent(abrir)
				.addAction(Notification.Action.Builder(null, t.getString(R.string.ayuda_terminar), terminar).build())
				.build()
			nm.notify(ID_AYUDADO, n)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo poner la notificación de la ayuda", e)
		}
	}
}
