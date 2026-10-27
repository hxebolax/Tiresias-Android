package com.xebolax.tiresias

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.servicio.ServicioTiresias
import org.junit.After
import org.junit.Before

/**
 * Base de las pruebas que manejan Tiresias de verdad en el emulador: conecta el
 * servicio (la instrumentación reinicia el proceso), hace acciones como si
 * fueran gestos y lee lo que se ha dicho del historial de la voz (es lo que se
 * mandó a sonar). Los ajustes que cambia una prueba se dejan como estaban.
 */
abstract class PruebaTiresias {
	protected val inst = InstrumentationRegistry.getInstrumentation()
	protected val app: Context get() = inst.targetContext
	protected lateinit var s: ServicioTiresias
	protected val prefs by lazy { Almacen.protegido(app).getSharedPreferences(Ajustes.ARCHIVO, Context.MODE_PRIVATE) }
	private var antes: Map<String, *> = emptyMap<String, Any>()

	@Before
	fun conectarTiresias() {
		Ajustes(app).asistenteHecho = true
		antes = prefs.all.toMap()
		s = ServicioTiresias.instancia ?: conectar()
		// Las frases esperadas están en español (el emulador está en inglés)
		if (com.xebolax.tiresias.base.Idioma.elegido(app) != "es") {
			com.xebolax.tiresias.base.Idioma.cambiar(app, "es")
			s.recargarAjustes()
			SystemClock.sleep(500)
		}
		s.lectorPruebas!!.control = ControlLectura.PREDETERMINADO
		Log.i(ETIQUETA, "Idioma: ajuste «${Ajustes(app).idioma}», elegido «${com.xebolax.tiresias.base.Idioma.elegido(app)}», efectivo ${com.xebolax.tiresias.base.Idioma.efectivo(app)}, textos ${s.lectorPruebas!!.textos.resources.configuration.locales}")
		s.cerrarVentanaPruebas()
		// Si una prueba anterior dejó abierta la cortinilla o los ajustes rápidos, se cierran
		if (android.os.Build.VERSION.SDK_INT >= 31) {
			s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
			SystemClock.sleep(300)
		}
	}

	@After
	fun dejarComoEstaba() {
		s.cerrarVentanaPruebas()
		restaurarAjustes()
	}

	/** Deja los ajustes como estaban al empezar la prueba (en español) y los aplica al lector. */
	protected fun restaurarAjustes() {
		val e = prefs.edit().clear()
		for ((k, v) in antes) when (v) {
			is Boolean -> e.putBoolean(k, v)
			is Int -> e.putInt(k, v)
			is Long -> e.putLong(k, v)
			is Float -> e.putFloat(k, v)
			is String -> e.putString(k, v)
			is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
		}
		// El idioma se queda en español entre pruebas
		e.putString("idioma", "es")
		e.commit()
		s.recargarAjustes()
		s.lectorPruebas?.control = ControlLectura.PREDETERMINADO
		SystemClock.sleep(300)
	}

	private fun conectar(): ServicioTiresias {
		val ua = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		val servicio = app.packageName + "/com.xebolax.tiresias.servicio.ServicioTiresias"
		fun shell(orden: String) = ua.executeShellCommand(orden).close()
		shell("settings put secure enabled_accessibility_services null")
		SystemClock.sleep(800)
		shell("settings put secure enabled_accessibility_services $servicio")
		shell("settings put secure accessibility_enabled 1")
		return esperar(20_000) { ServicioTiresias.instancia } ?: error("Tiresias no está activo en el emulador")
	}

	protected fun <T> esperar(ms: Long, condicion: () -> T?): T? {
		val fin = SystemClock.uptimeMillis() + ms
		while (SystemClock.uptimeMillis() < fin) {
			condicion()?.let { return it }
			SystemClock.sleep(40)
		}
		return null
	}

	/** Cambia un ajuste (se deshace al acabar la prueba) y lo aplica al lector. */
	protected fun ajuste(clave: String, valor: Any) {
		val e = prefs.edit()
		when (valor) {
			is Boolean -> e.putBoolean(clave, valor)
			is Int -> e.putInt(clave, valor)
			is String -> e.putString(clave, valor)
			is Set<*> -> e.putStringSet(clave, valor.map { it.toString() }.toSet())
			else -> error("Tipo no previsto: $valor")
		}
		e.commit()
		s.recargarAjustes()
		SystemClock.sleep(300)
	}

	/** Lo último que ha dicho Tiresias. */
	protected fun ultimo(): String = s.vozPruebas!!.historial().firstOrNull().orEmpty()

	/** Hace [a] y devuelve lo nuevo que ha dicho (espera a que cambie, como mucho [espera] ms más). */
	protected fun hacer(a: Accion, espera: Long = 600): String {
		val voz = s.vozPruebas!!
		val antes = voz.historial().firstOrNull()
		marcaInicial = voz.contadorPruebas
		s.ejecutarPrueba(a)
		esperar(3000) { voz.historial().firstOrNull()?.takeIf { it != antes } }
		SystemClock.sleep(espera)
		return ultimo().also { Log.i(ETIQUETA, "$a -> «$it»") }
	}

	/** Lo que se ha dicho en los últimos [ms] (de lo más nuevo a lo más viejo), sin las pistas de uso. */
	protected fun dichoDesde(antes: List<String>): List<String> {
		val ahora = s.vozPruebas!!.historial()
		val nuevos = ahora.size - antes.size
		return if (nuevos > 0) ahora.take(nuevos) else listOfNotNull(ahora.firstOrNull())
	}

	/** Una marca en el historial: lo que se diga después de ella es lo nuevo. */
	protected fun marca(): Int = s.vozPruebas!!.contadorPruebas

	/** Espera a que algo de lo dicho desde [desde] (por defecto, desde ahora) cumpla [condicion]. */
	protected fun dichoQue(ms: Long = 4000, desde: Int = marcaInicial, condicion: (String) -> Boolean): String? =
		esperar(ms) { s.vozPruebas!!.dichoDesde(desde).firstOrNull(condicion) }

	/** La marca de la última acción hecha con [hacer] (lo dicho después de ella). */
	protected var marcaInicial = 0
		private set

	protected fun abrirMuestrario(actividad: String = ".ActividadMuestrario") {
		val i = Intent().setClassName("com.xebolax.tiresias.muestrario", "com.xebolax.tiresias.muestrario$actividad")
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
		val m = marca()
		app.startActivity(i)
		esperar(10_000) { s.lectorPruebas?.raiz()?.takeIf { r -> r.packageName == "com.xebolax.tiresias.muestrario" && r.childCount > 0 && r.className?.contains("SplashScreen") != true } }
		// Que ya haya dicho el título y, si lo hay, el foco recordado: si no, llegan tarde y se confunden con lo que se prueba
		dichoQue(6000, m) { it.startsWith("Muestrario") || it.startsWith("Página web") }
		esperarCalma(m)
	}

	/** Espera a que Tiresias haya dicho algo desde [desde] y lleve un rato sin decir nada nuevo (como mucho 8 s). */
	protected fun esperarCalma(desde: Int) {
		val voz = s.vozPruebas!!
		val fin = SystemClock.uptimeMillis() + 8000
		esperar(6000) { voz.contadorPruebas.takeIf { it > desde } }
		var ultimo = voz.contadorPruebas
		var quieto = SystemClock.uptimeMillis()
		while (SystemClock.uptimeMillis() < fin) {
			SystemClock.sleep(100)
			if (voz.contadorPruebas != ultimo) {
				ultimo = voz.contadorPruebas
				quieto = SystemClock.uptimeMillis()
			} else if (SystemClock.uptimeMillis() - quieto > 1200) break
		}
	}

	protected fun abrir(clase: Class<*>, extra: (Intent) -> Unit = {}) {
		val m = marca()
		app.startActivity(Intent(app, clase).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).also(extra))
		esperar(10_000) { s.lectorPruebas?.raiz()?.takeIf { r -> r.packageName == app.packageName && r.childCount > 0 } }
		esperarCalma(m)
	}

	/** Va con «siguiente» hasta que lo dicho empiece por [inicio] (o null si no se llega). */
	protected fun irA(inicio: String, maximo: Int = 60): String? =
		buscarDesde(Accion.PRIMERO, inicio, maximo)
			// Puede estar más arriba en una lista ya desplazada: desde el principio de la lista entera
			?: buscarDesde(Accion.LISTA_PRINCIPIO, inicio, maximo)

	private fun buscarDesde(salida: Accion, inicio: String, maximo: Int): String? {
		hacer(salida, if (salida == Accion.PRIMERO) 600 else 1500)
		if (ultimo().startsWith(inicio)) return ultimo()
		var anterior = ""
		var iguales = 0
		for (i in 0 until maximo) {
			val d = hacer(Accion.SIGUIENTE, 350)
			if (d.startsWith(inicio)) return d
			if (d == anterior && ++iguales >= 3) return null
			if (d != anterior) iguales = 0
			anterior = d
		}
		return null
	}

	/** Recorre la pantalla con «siguiente» hasta el final y devuelve lo dicho de cada parada. */
	protected fun recorrer(nombre: String, maximo: Int = 90): List<String> {
		val dichos = ArrayList<String>()
		dichos += hacer(Accion.PRIMERO)
		var iguales = 0
		for (i in 0 until maximo) {
			val d = hacer(Accion.SIGUIENTE, 400)
			if (d == dichos.last()) {
				if (++iguales >= 2) break
				continue
			}
			iguales = 0
			dichos += d
		}
		dichos.forEachIndexed { i, d -> Log.i(ETIQUETA, "$nombre ${i + 1}: «$d»") }
		return dichos
	}

	companion object {
		const val ETIQUETA = "PruebaTiresias"
	}
}
