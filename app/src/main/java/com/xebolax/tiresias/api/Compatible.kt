package com.xebolax.tiresias.api

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.servicio.ServicioTiresias
import fyi.quin.backtalk.directtouch.IDirectTouch
import fyi.quin.backtalk.speech.ISpeechControl

/**
 * Las interfaces que comparten otros lectores de Android para que otras apps hablen por ellos y pidan el toque directo
 * (`fyi.quin.backtalk.speech.ISpeechControl`, acción `fyi.quin.backtalk.action.SPEECH_CONTROL`, y
 * `fyi.quin.backtalk.directtouch.IDirectTouch`, acción `fyi.quin.backtalk.action.DIRECT_TOUCH`). Con ellas, un teclado
 * o un juego hecho para esos lectores funciona también con Tiresias cambiando solo el paquete al que se enlaza
 * (`com.xebolax.tiresias`). Guía: `docs/api.md`.
 *
 * Las reglas son las de Tiresias y las de esos lectores:
 * - El teclado que está puesto habla sin pedir permiso (ya ve todo lo que se escribe), sin límite, también con el móvil
 *   bloqueado, como el eco del teclado; y puede decir que él ya dice lo que se escribe ([VozDeApps.tecladoQueHabla]),
 *   para que Tiresias no lo repita. Al cambiar de teclado se olvida.
 * - Cualquier otra app necesita el permiso del usuario, el mismo de la API propia (Ajustes, «Apps que usan Tiresias»), y
 *   puede hablar unas pocas veces seguidas y después una vez por segundo.
 * - Nada de lo que mandan se guarda ni se registra.
 */
object VozDeApps {
	private const val ETIQUETA = "VozDeApps"
	private const val RAFAGA = 5
	private const val RECARGA_MS = 1000L
	private const val MAXIMO = 2000

	/** El paquete del teclado que dice él lo que se escribe (por la interfaz compatible), o null. */
	@Volatile var tecladoQueHabla: String? = null
		private set

	private class Cubo(var fichas: Double, var cuando: Long)
	private val cubos = HashMap<String, Cubo>()

	/** El paquete del teclado puesto, si es uno de [apps]; null si no. */
	fun tecladoActual(c: Context, apps: List<String>): String? =
		runCatching { Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
			?.let { ComponentName.unflattenFromString(it)?.packageName }?.takeIf { it in apps }

	/** Si [paquete] es el teclado que ha dicho que habla y sigue puesto. */
	fun tecladoHablaSolo(c: Context, paquete: String?): Boolean {
		val t = tecladoQueHabla ?: return false
		return t == paquete
	}

	/** Si [app] puede hablar ahora (unas pocas veces seguidas y después una por segundo). */
	@Synchronized
	private fun aTiempo(app: String): Boolean {
		val ahora = SystemClock.uptimeMillis()
		val c = cubos.getOrPut(app) { Cubo(RAFAGA.toDouble(), ahora) }
		c.fichas = (c.fichas + (ahora - c.cuando).coerceAtLeast(0).toDouble() / RECARGA_MS).coerceAtMost(RAFAGA.toDouble())
		c.cuando = ahora
		if (c.fichas < 1) return false
		c.fichas -= 1
		return true
	}

	/**
	 * La app de [apps] que puede usar Tiresias, o null (y se pregunta al usuario la primera vez). El teclado puesto
	 * siempre puede.
	 */
	private fun permitida(c: Context, apps: List<String>, s: ServicioTiresias): Pair<String, Boolean>? {
		tecladoActual(c, apps)?.let { return it to true }
		val ajustes = Ajustes(c)
		apps.firstOrNull { it == c.packageName || it in ajustes.apiPermitidas }?.let { return it to false }
		val app = apps.firstOrNull() ?: return null
		if (app !in ajustes.apiDenegadas) s.pedirPermisoApi(app)
		return null
	}

	/** Dice [texto] de una de [apps]: [ritmo] y [tono], múltiplos de los del usuario (0,25 a 4). */
	fun hablar(c: Context, apps: List<String>, texto: String?, interrumpir: Boolean, ritmo: Float, tono: Float, volumen: Float) {
		val s = ServicioTiresias.instancia ?: return
		val t = texto?.toString()?.trim()?.take(MAXIMO)?.ifEmpty { null } ?: return
		val (app, esTeclado) = permitida(c, apps, s) ?: return
		if (!esTeclado && !aTiempo(app)) return
		// (un volumen de 0 es que no se quiere oír)
		if (!volumen.isNaN() && volumen <= 0f) return
		fun escala(v: Float) = if (v.isNaN()) 100 else (v.coerceIn(0.25f, 4f) * 100).toInt()
		s.decirDesdeApp(t, interrumpir, velocidad = escala(ritmo), tono = escala(tono), comoTeclado = esTeclado)
	}

	fun callar(c: Context, apps: List<String>) {
		val s = ServicioTiresias.instancia ?: return
		permitida(c, apps, s) ?: return
		s.callarDesdeApp()
	}

	/** El teclado puesto dice (o deja de decir) lo que se escribe con él. Solo si [apps] es el teclado puesto. */
	fun decirEscritura(c: Context, apps: List<String>, dice: Boolean) {
		val teclado = tecladoActual(c, apps) ?: return
		if (dice) tecladoQueHabla = teclado else if (tecladoQueHabla == teclado) tecladoQueHabla = null
		Registro.i(ETIQUETA, "Un teclado ${if (dice) "dice" else "deja de decir"} lo que se escribe")
	}

	/** Las zonas de toque directo que pide [app] (en píxeles de la pantalla); null las quita. */
	fun toqueDirecto(c: Context, apps: List<String>, zona: Rect?) {
		val s = ServicioTiresias.instancia ?: return
		val (app, _) = permitida(c, apps, s) ?: return
		s.zonaDesdeApp(app, zona)
	}
}

/** El servicio de voz compatible (`fyi.quin.backtalk.action.SPEECH_CONTROL`). */
class ServicioVozCompatible : Service() {
	private val principal = Handler(Looper.getMainLooper())

	private fun hablar(text: String?, interrupt: Boolean, rate: Float, pitch: Float, volume: Float) {
		val apps = llamante()
		principal.post { VozDeApps.hablar(applicationContext, apps, text, interrupt, rate, pitch, volume) }
	}

	private fun callar() {
		val apps = llamante()
		principal.post { VozDeApps.callar(applicationContext, apps) }
	}

	private fun escritura(spoken: Boolean) {
		val apps = llamante()
		principal.post { VozDeApps.decirEscritura(applicationContext, apps, spoken) }
	}

	private val enlace = object : ISpeechControl.Stub() {
		override fun speak(text: String?, interrupt: Boolean, rate: Float, pitch: Float, volume: Float) = hablar(text, interrupt, rate, pitch, volume)
		override fun stop() = callar()
		override fun setTypingSpoken(spoken: Boolean) = escritura(spoken)
	}

	/** La misma interfaz con los nombres nuevos de SandFly (`io.github.aaron_gh.sandfly`, fba3fbd66). */
	private val enlaceSandFly = object : io.github.aaron_gh.sandfly.speech.ISpeechControl.Stub() {
		override fun speak(text: String?, interrupt: Boolean, rate: Float, pitch: Float, volume: Float) = hablar(text, interrupt, rate, pitch, volume)
		override fun stop() = callar()
		override fun setTypingSpoken(spoken: Boolean) = escritura(spoken)
	}

	override fun onBind(intent: Intent?): IBinder = if (intent?.action == ACCION_SANDFLY) enlaceSandFly else enlace

	companion object {
		const val ACCION_SANDFLY = "io.github.aaron_gh.sandfly.action.SPEECH_CONTROL"
	}

	/** Los paquetes de la app que llama, leídos en el hilo de la llamada (antes de pasar al principal). */
	private fun llamante(): List<String> = packageManager.getPackagesForUid(Binder.getCallingUid())?.toList().orEmpty()
}

/**
 * El toque directo compatible (`fyi.quin.backtalk.action.DIRECT_TOUCH`): toda la pantalla o unas zonas (en píxeles de
 * la pantalla, o por el identificador de una vista), mientras la app está delante. Los estados y las reglas por
 * actividad de la declaración del manifiesto no se usan: de la declaración solo vale `<screen>` sin condiciones.
 */
class ServicioToqueCompatible : Service() {
	private val principal = Handler(Looper.getMainLooper())

	private val enlace = object : IDirectTouch.Stub() {
		override fun requestScreen(client: IBinder?) = pantalla()
		override fun requestAreas(client: IBinder?, areas: Array<out Rect>?, viewIds: Array<out String>?) = zonas(areas, viewIds)
		override fun stop(client: IBinder?) = parar()
		override fun reset(client: IBinder?) = parar()
		// (los estados de la declaración del manifiesto no se usan en Tiresias)
		override fun setState(client: IBinder?, state: String?) {}
	}

	/** La misma interfaz con los nombres nuevos de SandFly (`io.github.aaron_gh.sandfly`, fba3fbd66). */
	private val enlaceSandFly = object : io.github.aaron_gh.sandfly.directtouch.IDirectTouch.Stub() {
		override fun requestScreen(client: IBinder?) = pantalla()
		override fun requestAreas(client: IBinder?, areas: Array<out Rect>?, viewIds: Array<out String>?) = zonas(areas, viewIds)
		override fun stop(client: IBinder?) = parar()
		override fun reset(client: IBinder?) = parar()
		override fun setState(client: IBinder?, state: String?) {}
	}

	override fun onBind(intent: Intent?): IBinder = if (intent?.action == ACCION_SANDFLY) enlaceSandFly else enlace

	companion object {
		const val ACCION_SANDFLY = "io.github.aaron_gh.sandfly.action.DIRECT_TOUCH"
	}

	private fun pantalla() {
		val apps = llamante()
		principal.post {
			val m = resources.displayMetrics
			VozDeApps.toqueDirecto(applicationContext, apps, Rect(0, 0, m.widthPixels, m.heightPixels))
		}
	}

	private fun zonas(areas: Array<out Rect>?, viewIds: Array<out String>?) {
			val apps = llamante()
			val zonas = areas?.filterNotNull()?.map { Rect(it) }.orEmpty()
			val ids = viewIds?.filterNotNull().orEmpty()
			principal.post {
				val total = Rect()
				for (z in zonas) if (!z.isEmpty) total.union(z)
				// Las vistas por su identificador, donde estén ahora en la pantalla
				val raiz = runCatching { ServicioTiresias.instancia?.rootInActiveWindow }.getOrNull()
				for (id in ids) runCatching { raiz?.findAccessibilityNodeInfosByViewId(id) }.getOrNull()?.forEach { n ->
					val r = Rect()
					n.getBoundsInScreen(r)
					if (!r.isEmpty) total.union(r)
				}
				VozDeApps.toqueDirecto(applicationContext, apps, total.takeIf { !it.isEmpty })
			}
	}

	private fun parar() {
		val apps = llamante()
		principal.post { VozDeApps.toqueDirecto(applicationContext, apps, null) }
	}

	private fun llamante(): List<String> = packageManager.getPackagesForUid(Binder.getCallingUid())?.toList().orEmpty()
}

/**
 * Los avisos compatibles para hablar (`fyi.quin.backtalk.action.SPEAK` y `STOP_SPEECH`): solo Android 14 o posterior y
 * solo si quien lo manda comparte quién es (`BroadcastOptions.setShareIdentityEnabled`), porque hay que saber qué app es.
 */
class ReceptorVozCompatible : BroadcastReceiver() {
	override fun onReceive(c: Context, i: Intent) {
		if (Build.VERSION.SDK_INT < 34) return
		val app = sentFromPackage ?: return
		when (i.action) {
			ACCION_HABLAR, ACCION_HABLAR_SANDFLY -> VozDeApps.hablar(
				c, listOf(app), i.getStringExtra("text"), i.getBooleanExtra("interrupt", false),
				i.getFloatExtra("rate", 1f), i.getFloatExtra("pitch", 1f), i.getFloatExtra("volume", 1f),
			)
			ACCION_CALLAR, ACCION_CALLAR_SANDFLY -> VozDeApps.callar(c, listOf(app))
		}
	}

	companion object {
		const val ACCION_HABLAR = "fyi.quin.backtalk.action.SPEAK"
		const val ACCION_CALLAR = "fyi.quin.backtalk.action.STOP_SPEECH"
		const val ACCION_HABLAR_SANDFLY = "io.github.aaron_gh.sandfly.action.SPEAK"
		const val ACCION_CALLAR_SANDFLY = "io.github.aaron_gh.sandfly.action.STOP_SPEECH"
	}
}
