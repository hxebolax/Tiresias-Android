package com.xebolax.tiresias.gestos

import android.accessibilityservice.AccessibilityService
import android.graphics.Region
import android.os.Build
import android.view.Display
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.voz.Sonido

/**
 * Los gestos: traduce los de Android a los de Tiresias y gestiona el paso de
 * toques a la app (pasar el siguiente gesto y el toque directo para juegos).
 *
 * El paso de toques usa la región de paso de la exploración táctil (Android 11
 * o posterior). Nunca atrapa: el paso del siguiente gesto se acaba solo con el
 * primer toque o a los [ESPERA_PASO_MS], y el toque directo se quita con las
 * dos teclas de volumen y con el gesto de emergencia, que no pasa por aquí.
 */
class GestosTiresias(private val lector: Lector) {
	private val servicio = lector.servicio
	@Volatile var pasoActivo = false
		private set
	@Volatile private var toqueDirectoEn: String? = null
	private val preferencias = Almacen.protegido(servicio).getSharedPreferences("toque_directo", 0)

	/** El gesto de Tiresias de un identificador de Android (`AccessibilityService.GESTURE_*`). */
	fun deAndroid(id: Int): Gesto? = when (id) {
		AccessibilityService.GESTURE_SWIPE_UP -> Gesto.ARRIBA
		AccessibilityService.GESTURE_SWIPE_DOWN -> Gesto.ABAJO
		AccessibilityService.GESTURE_SWIPE_LEFT -> Gesto.IZQUIERDA
		AccessibilityService.GESTURE_SWIPE_RIGHT -> Gesto.DERECHA
		AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT -> Gesto.IZQUIERDA_DERECHA
		AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT -> Gesto.DERECHA_IZQUIERDA
		AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN -> Gesto.ARRIBA_ABAJO
		AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP -> Gesto.ABAJO_ARRIBA
		AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP -> Gesto.IZQUIERDA_ARRIBA
		AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN -> Gesto.IZQUIERDA_ABAJO
		AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP -> Gesto.DERECHA_ARRIBA
		AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN -> Gesto.DERECHA_ABAJO
		AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT -> Gesto.ARRIBA_IZQUIERDA
		AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT -> Gesto.ARRIBA_DERECHA
		AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT -> Gesto.ABAJO_IZQUIERDA
		AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT -> Gesto.ABAJO_DERECHA
		AccessibilityService.GESTURE_DOUBLE_TAP -> Gesto.DOBLE_TOQUE
		AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD -> Gesto.DOBLE_TOQUE_MANTENIDO
		AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP -> Gesto.TOQUE_2
		AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP -> Gesto.DOBLE_TOQUE_2
		AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP -> Gesto.TRIPLE_TOQUE_2
		AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD -> Gesto.DOBLE_TOQUE_MANTENIDO_2
		AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD -> Gesto.TRIPLE_TOQUE_MANTENIDO_2
		AccessibilityService.GESTURE_2_FINGER_SWIPE_UP -> Gesto.ARRIBA_2
		AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN -> Gesto.ABAJO_2
		AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT -> Gesto.IZQUIERDA_2
		AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT -> Gesto.DERECHA_2
		AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP -> Gesto.TOQUE_3
		AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP -> Gesto.DOBLE_TOQUE_3
		AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP -> Gesto.TRIPLE_TOQUE_3
		AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD -> Gesto.DOBLE_TOQUE_MANTENIDO_3
		AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD -> Gesto.TRIPLE_TOQUE_MANTENIDO_3
		AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD -> Gesto.TOQUE_MANTENIDO_3
		AccessibilityService.GESTURE_3_FINGER_SWIPE_UP -> Gesto.ARRIBA_3
		AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN -> Gesto.ABAJO_3
		AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT -> Gesto.IZQUIERDA_3
		AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> Gesto.DERECHA_3
		AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP -> Gesto.TOQUE_4
		AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP -> Gesto.DOBLE_TOQUE_4
		AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP -> Gesto.TRIPLE_TOQUE_4
		AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD -> Gesto.DOBLE_TOQUE_MANTENIDO_4
		AccessibilityService.GESTURE_4_FINGER_SWIPE_UP -> Gesto.ARRIBA_4
		AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN -> Gesto.ABAJO_4
		AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT -> Gesto.IZQUIERDA_4
		AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT -> Gesto.DERECHA_4
		else -> null
	}

	/** Pasa el siguiente gesto a la app sin que Tiresias lo interprete (para gestos propios de una app). */
	fun pasarSiguiente(): Boolean {
		if (Build.VERSION.SDK_INT < 30) {
			lector.decir(R.string.no_disponible_version)
			return false
		}
		pasoActivo = true
		region(true)
		lector.sonidos.tocar(Sonido.GESTO)
		lector.decir(R.string.gestos_paso)
		lector.manejador.postDelayed({ if (pasoActivo && toqueDirectoEn == null) terminarPaso() }, ESPERA_PASO_MS)
		return true
	}

	/** Se llama al terminar un toque: el paso del siguiente gesto se acaba. */
	fun alTerminarToque() {
		if (pasoActivo && toqueDirectoEn == null) terminarPaso()
	}

	private fun terminarPaso() {
		pasoActivo = false
		region(false)
	}

	/**
	 * Toque directo en la app de delante (juegos de audio, pianos, apps de
	 * firma): los toques van directos a la app. Se recuerda por app y se quita
	 * con las dos teclas de volumen.
	 */
	fun alternarToqueDirecto(): Boolean {
		if (Build.VERSION.SDK_INT < 30) {
			lector.decir(R.string.no_disponible_version)
			return false
		}
		val paquete = lector.raiz()?.packageName?.toString() ?: return false
		val activo = toqueDirectoEn == paquete
		preferencias.edit().putBoolean(paquete, !activo).apply()
		ponerToqueDirecto(if (activo) null else paquete)
		lector.decir(if (activo) R.string.gestos_toque_directo_no else R.string.gestos_toque_directo_si)
		return true
	}

	/**
	 * Al cambiar de app: si la nueva tiene el toque directo guardado (y no está
	 * en pausa desde los ajustes rápidos), se pone; si no, se quita. Si la app
	 * marcó con la API una zona para el toque directo, solo esa zona.
	 */
	fun alCambiarApp(paquete: String?) {
		if (Build.VERSION.SDK_INT < 30) return
		appDelante = paquete
		// Con la ayuda de otra persona o una superficie braille encima, toda la pantalla pasa siempre
		if (ayuda || superficie) return
		val pausado = com.xebolax.tiresias.base.Ajustes(servicio).toqueDirectoPausado
		val quiere = paquete != null && !pausado && preferencias.getBoolean(paquete, false)
		if (quiere && toqueDirectoEn != paquete) ponerToqueDirecto(paquete)
		else if (!quiere && toqueDirectoEn != null) ponerToqueDirecto(null)
		if (!quiere) {
			val zona = paquete?.let { zonasDeApps[it] }.takeIf { !pausado }
			zonaDeApp = zona != null
			ponerRegion(zona)
		}
	}

	/** La app de delante y las zonas que las apps han marcado para el toque directo (con la API). */
	@Volatile private var appDelante: String? = null
	@Volatile private var zonaDeApp = false
	private val zonasDeApps = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Rect>()

	/** [paquete] marca [zona] para el toque directo (vacía: la quita); vale mientras esa app está delante (Backtalk 185). */
	fun zonaDeApp(paquete: String, zona: android.graphics.Rect?) {
		if (zona == null || zona.isEmpty) zonasDeApps.remove(paquete) else zonasDeApps[paquete] = android.graphics.Rect(zona)
		if (paquete == appDelante) alCambiarApp(paquete)
	}

	val toqueDirecto: Boolean get() = toqueDirectoEn != null || ayuda

	/**
	 * Ayuda de otra persona (Backtalk 293): todos los toques van a las apps, como
	 * sin lector, para que otra persona maneje el móvil, y Tiresias sigue diciendo
	 * lo que pasa (lo que se pulsa, las ventanas, las notificaciones). Se quita
	 * con las dos teclas de volumen, como el toque directo.
	 */
	@Volatile var ayuda = false
		private set

	fun alternarAyuda(): Boolean {
		if (Build.VERSION.SDK_INT < 30) {
			lector.decir(R.string.no_disponible_version)
			return false
		}
		ayuda = !ayuda
		region(ayuda || toqueDirectoEn != null)
		if (!ayuda) alCambiarApp(appDelante)
		lector.decir(if (ayuda) R.string.ayuda_otra_si else R.string.ayuda_otra_no)
		return true
	}

	private fun ponerToqueDirecto(paquete: String?) {
		toqueDirectoEn = paquete
		pasoActivo = paquete != null
		region(paquete != null)
	}

	/** Quita cualquier paso de toques (modo seguro y teclas de volumen). */
	fun restablecer() {
		ayuda = false
		toqueDirectoEn = null
		pasoActivo = false
		region(false)
	}

	/** Los toques en [zona] (el teclado en pantalla) pasan directos a la app; null: ninguna. */
	fun zonaDirecta(zona: android.graphics.Rect?) {
		if (Build.VERSION.SDK_INT < 30 || toqueDirectoEn != null || zonaDeApp || ayuda || superficie) return
		runCatching { fijarRegion(if (zona != null && !zona.isEmpty) Region(zona) else Region()) }
			.onFailure { Registro.w(ETIQUETA, "No se pudo poner el toque directo en el teclado", it) }
	}

	/** Mientras está abierto el menú circular, todos los toques van a él; al cerrarlo, lo de antes. */
	fun pasoParaMenu(poner: Boolean) {
		if (Build.VERSION.SDK_INT < 30) return
		if (poner) region(true)
		else if (toqueDirectoEn != null || ayuda) region(true)
		else {
			region(false)
			alCambiarApp(appDelante)
		}
	}

	/**
	 * Mientras hay una superficie braille encima de todo (navegación web braille,
	 * búsqueda braille, control de un PC), todos los toques van a ella y nada
	 * cambia la zona de paso: ni el cambio de app ni el teclado en pantalla.
	 */
	@Volatile var superficie = false
		private set

	fun pasoParaSuperficie(poner: Boolean) {
		if (Build.VERSION.SDK_INT < 30) return
		superficie = poner
		pasoParaMenu(poner)
	}

	private fun ponerRegion(zona: android.graphics.Rect?) {
		if (Build.VERSION.SDK_INT < 30) return
		runCatching { fijarRegion(if (zona != null && !zona.isEmpty) Region(zona) else Region()) }
			.onFailure { Registro.w(ETIQUETA, "No se pudo poner la zona de toque directo de la app", it) }
	}

	/** La zona de paso de toques puesta ahora (para devolverla tras un toque de Tiresias). */
	private var regionPuesta = Region()

	@androidx.annotation.RequiresApi(30)
	private fun fijarRegion(r: Region) {
		servicio.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY, r)
		regionPuesta = Region(r)
	}

	/**
	 * Un gesto de un dedo de Tiresias (un toque de verdad, una pulsación larga) que empieza en [zona]: si no
	 * pasa directo a la app, la exploración táctil lo toma por un dedo que explora y la app no recibe nada
	 * (Backtalk, DirectClick). Mientras dura, [zona] se suma a la de paso de toques; la función que devuelve
	 * pone la de antes (si nadie la ha cambiado entretanto).
	 */
	fun pasoParaToque(zona: android.graphics.Rect): () -> Unit {
		if (Build.VERSION.SDK_INT < 30) return {}
		val antes = Region(regionPuesta)
		val con = Region(antes).apply { op(zona, Region.Op.UNION) }
		try {
			fijarRegion(con)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo abrir el paso para un toque de Tiresias", e)
			return {}
		}
		return {
			if (regionPuesta == con) runCatching { fijarRegion(antes) }.onFailure { Registro.w(ETIQUETA, "No se pudo devolver la zona de paso de toques", it) }
		}
	}

	private fun region(toda: Boolean) {
		if (Build.VERSION.SDK_INT < 30) return
		try {
			// La pantalla entera de verdad, con la barra de navegación (displayMetrics la deja fuera en muchos móviles:
			// los toques sobre ella no pasaban a la app); TalkBack y Backtalk usan también las medidas reales
			val limites = servicio.getSystemService(android.view.WindowManager::class.java)?.maximumWindowMetrics?.bounds
			val ancho = limites?.width() ?: servicio.resources.displayMetrics.widthPixels
			val alto = limites?.height() ?: servicio.resources.displayMetrics.heightPixels
			val r = if (toda) Region(0, 0, ancho, alto) else Region()
			fijarRegion(r)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo cambiar la región de paso de toques", e)
		}
	}

	companion object {
		private const val ETIQUETA = "Gestos"
		private const val ESPERA_PASO_MS = 6000L
	}
}
