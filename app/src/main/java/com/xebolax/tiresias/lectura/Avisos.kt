package com.xebolax.tiresias.lectura

import android.app.Notification
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.xebolax.tiresias.R
import com.xebolax.tiresias.nucleo.FiltroRepetidos
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo

/**
 * Las notificaciones: cuáles se leen solas y cuáles no, y su historial.
 *
 * Lo que se critica de TalkBack: la lectura automática no se puede quitar por
 * app. En Tiresias se puede quitar del todo, por app, en No molestar, durante
 * una llamada y mientras suena audio (vídeo, música). Las que no se leen se
 * guardan igual en el historial (solo en memoria) para leerlas cuando se quiera.
 */
class Avisos(private val lector: Lector) {
	/**
	 * Una notificación recibida: la app, su título y su texto. Solo en memoria, nunca en disco. [motivo] es por
	 * qué no se leyó sola (la cadena), o null si se leyó.
	 */
	data class Aviso(val paquete: String, val app: String, val titulo: String, val texto: String, val momento: Long, val notificacion: Notification? = null) {
		@Volatile var motivo: Int? = null
	}

	private val historial = ArrayDeque<Aviso>()
	private val filtro = FiltroRepetidos(ventanaMs = 3000, maximoPorSegundo = 3)

	/** Las notificaciones que han llegado como evento de accesibilidad hace poco (app, hora y título), para el oyente. */
	private val porEvento = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 50
	}

	private fun clave(paquete: String, n: Notification): String =
		"$paquete|${n.`when`}|${n.extras?.getCharSequence(Notification.EXTRA_TITLE)}"

	fun alNotificar(paquete: String, n: Notification, e: AccessibilityEvent) {
		synchronized(porEvento) { porEvento[clave(paquete, n)] = SystemClock.uptimeMillis() }
		// Con el oyente de notificaciones y el móvil bloqueado, la lee el oyente: trae la notificación entera
		// (el evento, solo la versión pública). Las llamadas, siempre por aquí
		if (com.xebolax.tiresias.servicio.OyenteAvisos.conectado && lector.bloqueado() && !esLlamada(n)) return
		procesar(paquete, n, e.text.joinToString(" ").trim())
	}

	/**
	 * Una notificación del oyente ([com.xebolax.tiresias.servicio.OyenteAvisos]), en el hilo del lector. Con el
	 * móvil bloqueado se lee ya; si no, solo si al poco no ha llegado su evento de accesibilidad (las que retiene
	 * No molestar o las que se actualizan sin avisar), para no decirla dos veces.
	 */
	fun alNotificarDesdeOyente(paquete: String, n: Notification) {
		if (lector.bloqueado()) {
			procesar(paquete, n, "")
			return
		}
		val c = clave(paquete, n)
		lector.manejador.postDelayed({
			val llego = synchronized(porEvento) { porEvento[c]?.let { SystemClock.uptimeMillis() - it < VIGENCIA_EVENTO_MS } == true }
			if (!llego) procesar(paquete, n, "")
		}, ESPERA_EVENTO_MS)
	}

	private fun procesar(paquete: String, n: Notification, textoEvento: String) {
		// El «N notificaciones más» que One UI pone en la persiana en lugar de las silenciosas: no es un aviso (SandFly)
		if (esResumenSamsung(paquete, n)) return
		val extras = n.extras
		val titulo = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
		val texto = textoDe(paquete, n, titulo, textoEvento)
		if (titulo.isEmpty() && texto.isEmpty()) return
		// Las llamadas de WhatsApp, Telegram, Signal...: se guardan para contestarlas o colgarlas con el gesto
		if (esLlamada(n) && paquete !in marcadores()) llamadaInternet = LlamadaInternet(paquete, titulo, n, System.currentTimeMillis())
		// Las de progreso (descargas) y las que solo se actualizan no se leen
		if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && historial.any { it.paquete == paquete && it.titulo == titulo }) return
		val app = nombreApp(paquete)
		// La notificación entera (en memoria) para abrirla o usar sus acciones desde el historial
		val aviso = Aviso(paquete, app, titulo, texto, System.currentTimeMillis(), n)
		synchronized(historial) {
			historial.addFirst(aviso)
			while (historial.size > MAXIMO) historial.removeLast()
		}
		// Una invitación de ayuda de Tiresias en un WhatsApp o un SMS: aviso propio para ayudar con un toque
		if (paquete != lector.servicio.packageName) com.xebolax.tiresias.remoto.Asistencia.mirarAviso(lector.servicio, titulo, texto)
		aviso.motivo = motivoParaCallar(paquete)
			?: if (lector.ajustes.modoSenior && lector.ajustes.seniorSoloMensajes && !esDeLasQueImportan(paquete, n)) R.string.aviso_motivo_senior else null
		val seLee = aviso.motivo == null
		// Los complementos la reciben aunque no se lea (uno que copia los códigos los quiere también con el móvil
		// bloqueado o la app callada); uno de esta app puede cambiarla o callarla ("" la calla)
		val propia = if (lector.scriptsListos) lector.scripts.alNotificar(paquete, titulo, texto, mapOf(
			"categoria" to n.category.orEmpty(),
			"nombreApp" to app,
			"sms" to (paquete == appSms()),
			"seLee" to seLee,
			"continua" to (n.flags and Notification.FLAG_ONGOING_EVENT != 0),
			"bloqueado" to lector.bloqueado(),
			"momento" to aviso.momento,
			"mensajes" to mensajesDe(n),
		)) else null
		if (!seLee) return
		if (propia != null && propia.isEmpty()) {
			aviso.motivo = R.string.aviso_motivo_complemento
			return
		}
		// El principio que se repite (el nombre del grupo de WhatsApp en «Familia: Ana»), solo la primera vez
		val tituloDicho = sinPrefijoRepetido(paquete, titulo)
		val frase = propia ?: lector.textos.getString(R.string.aviso_notificacion, app, listOf(tituloDicho, texto).filter { it.isNotEmpty() }.distinct().joinToString(". "))
		// Palabras que callan una notificación (Ajustes, Notificaciones)
		val palabras = lector.ajustes.palabrasCalladas
		if (palabras.isNotEmpty() && palabras.any { frase.contains(it, ignoreCase = true) }) {
			aviso.motivo = R.string.aviso_motivo_palabra
			return
		}
		if (!filtro.pasa(paquete, frase, SystemClock.uptimeMillis())) {
			aviso.motivo = R.string.aviso_motivo_repetida
			return
		}
		lector.sonidos.tocar(Sonido.NOTIFICACION)
		lector.voz.decir(Enunciado(frase, Modo.ENCOLAR, Tipo.NOTIFICACION))
		if (lector.brailleListo) lector.braille.mensaje(frase)
	}

	/**
	 * Los mensajes de una notificación de mensajería (un chat con varios sin
	 * leer), con quién los manda: [{remitente, texto}]. Vacío si no es de ese tipo.
	 */
	private fun mensajesDe(n: Notification): org.json.JSONArray {
		val a = org.json.JSONArray()
		val estilo = runCatching { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull() ?: return a
		for (m in estilo.messages.takeLast(20)) {
			a.put(org.json.JSONObject().put("remitente", m.person?.name?.toString().orEmpty()).put("texto", m.text?.toString().orEmpty()))
		}
		return a
	}

	/**
	 * El texto de una notificación: el largo, el normal, el último mensaje de un chat, las líneas del estilo bandeja de
	 * entrada, el del aviso (ticker) o el del evento. Si no tiene ninguno, el de su diseño propio (el Tiempo de Samsung
	 * solo pone ahí la temperatura) y, si tampoco, el subtexto, la información o el resumen, que suelen ser el nombre
	 * de la cuenta (SandFly 13da805a6, 4536e1ff1 y 6ff3efdae). Vacío si no tiene nada más que el título.
	 */
	private fun textoDe(paquete: String, n: Notification, titulo: String, textoEvento: String): String {
		val extras = n.extras
		return campo(extras, Notification.EXTRA_BIG_TEXT)
			?: campo(extras, Notification.EXTRA_TEXT)
			?: ultimoMensaje(n)
			?: lineas(extras)
			?: n.tickerText?.toString()?.trim()?.takeIf { it.isNotEmpty() }
			?: textoEvento.trim().takeIf { it.isNotEmpty() }
			?: textoDelDiseno(paquete, n, titulo)
			?: campo(extras, Notification.EXTRA_SUB_TEXT)
			?: campo(extras, Notification.EXTRA_INFO_TEXT)
			?: campo(extras, Notification.EXTRA_SUMMARY_TEXT)
			?: ""
	}

	/** El último mensaje de una notificación de un chat (`MessagingStyle`), o null. */
	private fun ultimoMensaje(n: Notification): String? = runCatching {
		androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.lastOrNull()?.text?.toString()?.trim()
	}.getOrNull()?.takeIf { it.isNotEmpty() }

	/** Lo que se ha leído del diseño propio de cada notificación (en memoria; se infla una sola vez). */
	private val disenos = object : LinkedHashMap<String, String>(32, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAXIMO_DISENOS
	}

	/**
	 * Los textos del diseño propio de [n] (`RemoteViews`: el grande, el normal o el emergente), uno tras otro, sin el
	 * título ni el nombre de la app, o null si no tiene. Se infla en el hilo del lector (no en el principal) y solo
	 * cuando la notificación no trae texto: como mucho [MAXIMO_TEXTOS_DISENO] textos y [MAXIMO_VISTAS_DISENO] vistas.
	 */
	private fun textoDelDiseno(paquete: String, n: Notification, titulo: String): String? {
		@Suppress("DEPRECATION")
		val vistas = n.bigContentView ?: n.contentView ?: n.headsUpContentView ?: return null
		val c = clave(paquete, n)
		val leido = synchronized(disenos) { disenos[c] } ?: inflarDiseno(vistas).also { synchronized(disenos) { disenos[c] = it } }
		val sobran = setOf(titulo.lowercase(), nombreApp(paquete).lowercase())
		val lineas = leido.split('\n').filter { it.isNotBlank() && it.lowercase() !in sobran }.distinct()
		return lineas.joinToString(". ").takeIf { it.isNotEmpty() }
	}

	private fun inflarDiseno(vistas: android.widget.RemoteViews): String = try {
		val contexto = lector.servicio
		val raiz = vistas.apply(contexto, android.widget.FrameLayout(contexto))
		val textos = ArrayList<String>()
		textosDe(raiz, textos, intArrayOf(0))
		textos.joinToString("\n")
	} catch (ex: Exception) {
		// El diseño de otra app: recursos que faltan o vistas que no se pueden crear aquí. Se lee como sin texto
		com.xebolax.tiresias.base.Registro.w(ETIQUETA, "No se pudo leer el diseño propio de una notificación", ex)
		""
	} catch (ex: OutOfMemoryError) {
		com.xebolax.tiresias.base.Registro.w(ETIQUETA, "Sin memoria para leer el diseño propio de una notificación", ex)
		""
	}

	private fun textosDe(v: android.view.View, textos: MutableList<String>, vistas: IntArray) {
		if (textos.size >= MAXIMO_TEXTOS_DISENO || ++vistas[0] > MAXIMO_VISTAS_DISENO || v.visibility != android.view.View.VISIBLE) return
		if (v is android.widget.TextView) {
			v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { textos += it.take(500).replace('\n', ' ') }
			return
		}
		if (v is android.view.ViewGroup) for (i in 0 until v.childCount) textosDe(v.getChildAt(i), textos, vistas)
	}

	/** En el modo senior, las que se leen: llamadas, mensajes, correo, alarmas, recordatorios y citas (y las de la app de SMS). */
	private fun esDeLasQueImportan(paquete: String, n: Notification): Boolean {
		if (paquete == appSms()) return true
		if (n.category in CATEGORIAS_SENIOR) return true
		return paquete in MENSAJERIA
	}

	/** Una llamada de una app de internet: su notificación (con los botones de contestar y colgar) y cuándo llegó. */
	class LlamadaInternet(val paquete: String, val quien: String, val notificacion: Notification, val momento: Long) {
		/** Si ya se ha pulsado su «Contestar» (la siguiente vez, se cuelga). */
		@Volatile var contestada = false
	}

	/** La última llamada de internet que ha llegado, o null. */
	@Volatile var llamadaInternet: LlamadaInternet? = null

	private fun esResumenSamsung(paquete: String, n: Notification): Boolean =
		paquete == "com.android.systemui" && ((android.os.Build.VERSION.SDK_INT >= 26 && n.channelId == "INSIGNIFICANT") || n.extras?.containsKey("insignificant_child_count") == true)

	private fun esLlamada(n: Notification): Boolean =
		n.category == Notification.CATEGORY_CALL || n.extras?.containsKey("android.callType") == true

	/** Las apps de teléfono del sistema: sus llamadas se contestan con Android (TelecomManager), no con la notificación. */
	private fun marcadores(): Set<String> {
		val propio = runCatching { lector.servicio.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
		return MARCADORES + listOfNotNull(propio)
	}

	/**
	 * Cómo está la llamada: `"sonando"`, `"en_llamada"` o `""`. Con el teléfono,
	 * lo dice Android; con las de internet, la notificación de llamada reciente
	 * y el modo del audio (el de comunicación es el de una llamada en curso).
	 */
	fun estadoLlamada(): String {
		val audio = lector.servicio.getSystemService(android.media.AudioManager::class.java) ?: return ""
		return when (audio.mode) {
			android.media.AudioManager.MODE_RINGTONE -> "sonando"
			android.media.AudioManager.MODE_IN_CALL, android.media.AudioManager.MODE_IN_COMMUNICATION -> "en_llamada"
			else -> llamadaInternet?.takeIf { !it.contestada && System.currentTimeMillis() - it.momento < SONANDO_MS && accionDe(it.notificacion, CONTESTAR) != null }?.let { "sonando" } ?: ""
		}
	}

	/** El botón de la notificación cuyo nombre es una de [palabras] (en cualquier idioma de los que conoce Tiresias). */
	fun accionDe(n: Notification, palabras: Regex): Notification.Action? =
		n.actions?.firstOrNull { it.title?.toString()?.let { t -> palabras.containsMatchIn(t) } == true }

	/** La app de SMS del móvil (la que reciben los códigos por mensaje). */
	private fun appSms(): String? = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(lector.servicio) }.getOrNull()

	/** Por qué no se lee sola una notificación de [paquete] (la cadena del motivo), o null si se lee. */
	private fun motivoParaCallar(paquete: String): Int? {
		val a = lector.ajustes
		if (!a.leerNotificaciones) return R.string.aviso_motivo_apagadas
		if (paquete in a.appsSilenciadas) return R.string.aviso_motivo_app
		if (lector.noMolestar() && !a.notificacionesNoMolestar) return R.string.aviso_motivo_no_molestar
		val audio = lector.servicio.getSystemService(AudioManager::class.java)
		val enLlamada = audio?.mode == AudioManager.MODE_IN_CALL || audio?.mode == AudioManager.MODE_IN_COMMUNICATION
		if (enLlamada && !a.notificacionesEnLlamada) return R.string.aviso_motivo_llamada
		// Con el móvil bloqueado (o en silencio y con la pantalla apagada), si así se ha elegido, nada que no se pida
		// (se guardan en Notificaciones recientes)
		return lector.motivoCallarSinPedir()
	}

	/** El último principio de título de cada app («Familia» en «Familia: Ana») y cuándo se dijo. */
	private val prefijos = HashMap<String, Pair<String, Long>>()

	/**
	 * [titulo] sin el principio que se repite (el nombre del grupo, «Familia: Ana» tras «Familia: Juan»), si la
	 * misma app lo ha dicho hace poco (SandFly). La primera vez se dice entero.
	 */
	private fun sinPrefijoRepetido(paquete: String, titulo: String): String {
		val i = titulo.indexOf(": ")
		if (i <= 0 || i > 60) return titulo
		val prefijo = titulo.substring(0, i)
		val ahora = SystemClock.uptimeMillis()
		val antes = prefijos[paquete]
		prefijos[paquete] = prefijo to ahora
		if (prefijos.size > 50) prefijos.clear()
		return if (antes != null && antes.first == prefijo && ahora - antes.second < PREFIJO_MS) titulo.substring(i + 2).trim().ifEmpty { titulo } else titulo
	}

	fun historial(): List<Aviso> = synchronized(historial) { historial.toList() }

	fun nombreApp(paquete: String): String = runCatching {
		val pm = lector.servicio.packageManager
		pm.getApplicationLabel(pm.getApplicationInfo(paquete, PackageManager.GET_META_DATA)).toString()
	}.getOrDefault(paquete)

	companion object {
		private const val ETIQUETA = "Avisos"
		/** Cuántos diseños propios leídos se guardan (en memoria), y cuánto se lee de cada uno. */
		private const val MAXIMO_DISENOS = 32
		private const val MAXIMO_TEXTOS_DISENO = 12
		private const val MAXIMO_VISTAS_DISENO = 200

		/** Un texto de los extras de una notificación, sin espacios de más, o null si no hay. */
		private fun campo(extras: android.os.Bundle?, clave: String): String? =
			runCatching { extras?.getCharSequence(clave) }.getOrNull()?.toString()?.trim()?.takeIf { it.isNotEmpty() }

		/** Las líneas del estilo bandeja de entrada (`InboxStyle`: un correo por línea), o null. */
		private fun lineas(extras: android.os.Bundle?): String? =
			runCatching { extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) }.getOrNull()
				?.mapNotNull { it?.toString()?.trim()?.takeIf { l -> l.isNotEmpty() } }?.joinToString(". ")?.takeIf { it.isNotEmpty() }

		/**
		 * Lo que identifica lo que dice una notificación (su título y su texto, o lo que lo sustituye), para no leer
		 * otra vez una que se actualiza sin cambiar. Una que solo tiene su diseño propio, sin texto, cuenta como tal.
		 */
		fun firma(n: Notification?): String {
			val e = n?.extras ?: return ""
			val titulo = campo(e, Notification.EXTRA_TITLE).orEmpty()
			val texto = campo(e, Notification.EXTRA_BIG_TEXT) ?: campo(e, Notification.EXTRA_TEXT) ?: lineas(e)
				?: n.tickerText?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: campo(e, Notification.EXTRA_SUB_TEXT)
				?: campo(e, Notification.EXTRA_INFO_TEXT) ?: campo(e, Notification.EXTRA_SUMMARY_TEXT)
			@Suppress("DEPRECATION")
			val diseno = if (texto == null && (n.bigContentView ?: n.contentView ?: n.headsUpContentView) != null) "diseño" else null
			return "$titulo|${texto ?: diseno.orEmpty()}".trim('|')
		}

		/** Las apps de teléfono más comunes (además de la elegida en el móvil). */
		private val MARCADORES = setOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui", "com.android.server.telecom", "com.android.incallui")

		/** Lo que dura como mucho una llamada sonando. */
		const val SONANDO_MS = 60_000L

		/** Los nombres de los botones de las notificaciones de llamada, en español, inglés y alguno más. */
		val CONTESTAR = Regex("(?i)\\b(contestar|responder|aceptar|atender|answer|accept|pick up)\\b")
		val COLGAR = Regex("(?i)\\b(colgar|finalizar|terminar|hang up|end call|end|leave)\\b")
		val RECHAZAR = Regex("(?i)\\b(rechazar|declinar|decline|reject|ignorar|ignore)\\b")
		/** `Notification.CATEGORY_MISSED_CALL` (Android 12): solo es su texto, y en los anteriores no llega nunca. */
		private const val CATEGORIA_LLAMADA_PERDIDA = "missed_call"

		/** Las categorías de notificación que se leen en el modo senior. */
		private val CATEGORIAS_SENIOR = setOf(
			Notification.CATEGORY_CALL, CATEGORIA_LLAMADA_PERDIDA, Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL,
			Notification.CATEGORY_ALARM, Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT,
		)

		/** Las apps de mensajes más comunes (por si no ponen categoría a sus notificaciones). */
		private val MENSAJERIA = setOf(
			"com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.google.android.apps.messaging",
			"com.samsung.android.messaging", "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
		) + com.xebolax.tiresias.braille.BuscarEnviar.APPS_MENSAJES
		private const val MAXIMO = 100
		/** Lo que se espera al evento de accesibilidad de una notificación del oyente, y cuánto vale el evento. */
		private const val ESPERA_EVENTO_MS = 1500L
		private const val VIGENCIA_EVENTO_MS = 10_000L
		/** Lo que dura un principio de título repetido (el grupo) para no volver a decirlo. */
		private const val PREFIJO_MS = 120_000L
	}
}
