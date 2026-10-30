package com.xebolax.tiresias.braille

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo
import java.text.Normalizer
import java.util.Locale

/**
 * Lo que hace una superficie braille puesta encima de todo: la navegación web
 * braille, la búsqueda braille o el control de un PC. Todo llega en el hilo
 * del lector.
 */
abstract class ModoSuperficie(protected val lector: Lector) {
	/** Lo que se dice al abrirla (qué es y cómo se sale). */
	abstract fun aviso(): String
	abstract fun alPuntos(puntos: Int)
	abstract fun alGesto(g: GestoBraille)
	/** Al quitarla (por el usuario o porque se cambia de app o se bloquea). */
	open fun alCerrar() = Unit
	/** Si se cierra sola al cambiar de app (el control de un PC, no). */
	open val cerrarAlCambiarDeApp: Boolean = true
	/** Lo dibujado arriba (para quien ve un poco). */
	open val rotulo: String = "⠿ Tiresias"

	/** Quién la tiene puesta (para cerrarla desde dentro). */
	internal var encima: SuperficieEncima? = null

	fun cerrar() {
		encima?.cerrar()
	}

	protected val t get() = lector.textos

	protected fun decir(texto: String) = lector.decir(texto, Modo.INTERRUMPIR, Tipo.NAVEGACION)
}

/**
 * Una superficie braille a pantalla completa en una ventana de accesibilidad,
 * encima de la app, con la zona de paso de los toques puesta para que los
 * dedos lleguen tal cual (sin la exploración táctil), como hace Can Talk. Se
 * quita con dos dedos hacia arriba (en cada modo), con las dos teclas de
 * volumen, al bloquear el móvil, al cambiar de app y tras unos minutos sin
 * tocarla, para que nadie se quede sin lector.
 */
class SuperficieEncima(private val lector: Lector) {
	private val principal = Handler(Looper.getMainLooper())
	@Volatile private var vista: SuperficieBraille? = null
	@Volatile var modo: ModoSuperficie? = null
		private set

	val abierta: Boolean get() = modo != null

	fun abrir(m: ModoSuperficie) {
		if (Build.VERSION.SDK_INT < 30) {
			lector.decir(R.string.superficie_no_disponible)
			return
		}
		cerrar()
		modo = m
		m.encima = this
		principal.post {
			val wm = lector.servicio.getSystemService(WindowManager::class.java) ?: return@post
			val oyente = object : OyenteSuperficie {
				override fun alPuntos(puntos: Int) {
					tocado()
					lector.manejador.post { if (modo === m) m.alPuntos(puntos) }
				}
				override fun alGesto(g: GestoBraille) {
					tocado()
					lector.manejador.post { if (modo === m) m.alGesto(g) }
				}
				override fun alApoyar() {
					val a = lector.ajustes
					if (a.brailleVibrarPuntos) vista?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
					if (a.brailleSonarPuntos) lector.sonidos.tocar(Sonido.TECLA, volumen = a.brailleVolumenSonidos / 100f)
				}
				override fun anunciar(texto: String) {
					lector.manejador.post { lector.decir(texto, Modo.INTERRUMPIR, Tipo.NAVEGACION) }
				}
				override fun espejo(): Boolean = lector.ajustes.brailleEspejo
			}
			val v = SuperficieBraille(lector.servicio, oyente, lector.textos, parteDelAlto = 0f, rotulo = m.rotulo)
			val params = WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				PixelFormat.OPAQUE,
			)
			try {
				// Primero el paso de los toques: si el dedo baja enseguida, ya llega a la superficie
				lector.gestos.pasoParaSuperficie(true)
				wm.addView(v, params)
				vista = v
				tocado()
				lector.manejador.post { lector.decir(m.aviso(), Modo.INTERRUMPIR, Tipo.SISTEMA) }
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudo poner la superficie braille", e)
				modo = null
				lector.manejador.post { lector.gestos.pasoParaSuperficie(false) }
			}
		}
	}

	fun cerrar() {
		val m = modo ?: return
		modo = null
		m.encima = null
		principal.post {
			principal.removeCallbacks(sinUso)
			val v = vista
			vista = null
			if (v != null) runCatching { lector.servicio.getSystemService(WindowManager::class.java)?.removeView(v) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo quitar la superficie braille", it) }
			lector.manejador.post {
				lector.gestos.pasoParaSuperficie(false)
				m.alCerrar()
				lector.decir(R.string.superficie_cerrada)
			}
		}
	}

	/** Se ha cambiado de app: las superficies que lo piden se quitan. */
	fun alCambiarApp() {
		if (modo?.cerrarAlCambiarDeApp == true) cerrar()
	}

	private val sinUso = Runnable { cerrar() }

	private fun tocado() {
		principal.removeCallbacks(sinUso)
		principal.postDelayed(sinUso, SIN_USO_MS)
	}

	private companion object {
		const val ETIQUETA = "SuperficieEncima"
		/** Sin tocarla tanto tiempo, se quita sola. */
		const val SIN_USO_MS = 5 * 60_000L
	}
}

/**
 * Navegación web braille (Can Talk): con la superficie braille encima del
 * navegador, cada letra salta al siguiente elemento de su tipo, como las
 * teclas de NVDA: h encabezado, k enlace, f elemento de formulario, b botón, e
 * campo, x casilla, l lista, t tabla, d región, g imagen; con el signo de
 * mayúscula delante, al anterior; con el signo de número y una cifra del 1 al
 * 6, al encabezado de ese nivel. Un dedo a los lados pasa al elemento
 * siguiente o anterior; arriba y abajo, al anterior o siguiente del último
 * tipo; dos dedos hacia abajo activan (y en un campo de texto se quita para
 * escribir); dos hacia arriba la quitan; dos a la derecha leen desde aquí; dos
 * a la izquierda repiten; tres arriba y abajo, principio y final.
 */
class NavegacionWebBraille(lector: Lector, private val hacer: (Accion) -> Boolean) : ModoSuperficie(lector) {
	private var ultimo: ControlLectura? = null
	private var atras = false
	private var numero = false

	override fun aviso(): String = t.getString(R.string.navegacion_braille_aviso)

	override fun alPuntos(puntos: Int) {
		when (puntos) {
			TecladoBraille.SIGNO_DE_MAYUSCULA -> {
				atras = true
				decir(t.getString(R.string.navegacion_braille_anterior))
				return
			}
			TecladoBraille.SIGNO_DE_NUMERO -> {
				numero = true
				decir(t.getString(R.string.braille_numero))
				return
			}
		}
		val adelante = !atras
		atras = false
		if (numero) {
			numero = false
			val nivel = TecladoBraille.numero(puntos)?.toIntOrNull()?.takeIf { it in 1..6 }
			if (nivel != null) {
				ultimo = ControlLectura.ENCABEZADOS
				lector.navegador.porTipo(ControlLectura.ENCABEZADOS, adelante, nivel)
				return
			}
		}
		val letra = TecladoBraille.letra(puntos)
		val control = letra?.let { controlDe(it) }
		if (control == null) {
			decir(t.getString(R.string.navegacion_braille_sin_uso, letra ?: describir(puntos)))
			return
		}
		ultimo = control
		lector.navegador.porTipo(control, adelante)
	}

	override fun alGesto(g: GestoBraille) {
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.DERECHA -> hacer(Accion.SIGUIENTE)
			1 to DireccionBraille.IZQUIERDA -> hacer(Accion.ANTERIOR)
			1 to DireccionBraille.ABAJO -> ultimo?.let { lector.navegador.porTipo(it, true) } ?: hacer(Accion.SIGUIENTE)
			1 to DireccionBraille.ARRIBA -> ultimo?.let { lector.navegador.porTipo(it, false) } ?: hacer(Accion.ANTERIOR)
			2 to DireccionBraille.ABAJO -> {
				val campo = lector.foco?.isEditable == true
				hacer(Accion.ACTIVAR)
				// En un campo de texto hay que escribir: fuera la superficie, para usar el teclado
				if (campo) cerrar()
			}
			2 to DireccionBraille.ARRIBA -> cerrar()
			2 to DireccionBraille.DERECHA -> hacer(Accion.LEER_DESDE_AQUI)
			2 to DireccionBraille.IZQUIERDA -> hacer(Accion.REPETIR)
			3 to DireccionBraille.ARRIBA -> hacer(Accion.PRIMERO)
			3 to DireccionBraille.ABAJO -> hacer(Accion.ULTIMO)
			else -> decir(t.getString(R.string.navegacion_braille_ayuda))
		}
	}

	private fun describir(p: Int): String = (0 until 6).filter { p and (1 shl it) != 0 }.joinToString(" ") { (it + 1).toString() }

	companion object {
		fun controlDe(letra: String): ControlLectura? = when (letra) {
			"h" -> ControlLectura.ENCABEZADOS
			"k" -> ControlLectura.ENLACES
			"f" -> ControlLectura.CONTROLES
			"b" -> ControlLectura.BOTONES
			"e" -> ControlLectura.CAMPOS
			"x" -> ControlLectura.CASILLAS
			"l" -> ControlLectura.LISTAS
			"t" -> ControlLectura.TABLAS
			"d" -> ControlLectura.REGIONES
			"g" -> ControlLectura.IMAGENES
			else -> null
		}
	}
}

/**
 * Búsqueda braille (Can Talk): se escribe en braille parte de un nombre y se
 * recorren las coincidencias entre las apps instaladas o entre los elementos de
 * la pantalla. Un dedo a la izquierda borra y a la derecha escribe un espacio;
 * un dedo abajo y arriba, la coincidencia siguiente y la anterior; tres dedos a
 * los lados cambian entre apps y elementos; dos dedos hacia abajo abren la app
 * o activan el elemento (comprobando que sigue ahí); dos hacia arriba, salir.
 * Las contraseñas y los campos protegidos no se buscan.
 */
class BusquedaBraille(lector: Lector, private val hacer: (Accion) -> Boolean) : ModoSuperficie(lector) {
	private val consulta = StringBuilder()
	private var enApps = false
	private var numero = false
	private var actual = -1
	private var resultados: List<Pair<String, () -> Boolean>> = emptyList()

	override fun aviso(): String = t.getString(R.string.busqueda_braille_aviso)

	override fun alPuntos(puntos: Int) {
		if (puntos == TecladoBraille.SIGNO_DE_NUMERO) {
			numero = true
			return
		}
		val c = (if (numero) TecladoBraille.numero(puntos) else null) ?: TecladoBraille.letra(puntos)
		if (c == null || puntos == TecladoBraille.SIGNO_DE_MAYUSCULA) return
		if (!c[0].isDigit()) numero = false
		consulta.append(c)
		buscar(c)
	}

	override fun alGesto(g: GestoBraille) {
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.IZQUIERDA -> {
				if (consulta.isEmpty()) {
					decir(t.getString(R.string.braille_nada_que_borrar))
					return
				}
				consulta.setLength(consulta.length - 1)
				buscar(null)
			}
			1 to DireccionBraille.DERECHA -> {
				consulta.append(' ')
				numero = false
				decir(t.getString(R.string.voz_espacio))
			}
			1 to DireccionBraille.ABAJO, 1 to DireccionBraille.ARRIBA -> {
				if (resultados.isEmpty()) {
					decir(t.getString(R.string.busqueda_braille_nada, consulta.toString().ifBlank { "…" }))
					return
				}
				val paso = if (g.direccion == DireccionBraille.ABAJO) 1 else -1
				actual = if (actual < 0) 0 else (actual + paso + resultados.size) % resultados.size
				decir(t.getString(R.string.braille_lista_elemento, resultados[actual].first, actual + 1, resultados.size))
			}
			3 to DireccionBraille.IZQUIERDA, 3 to DireccionBraille.DERECHA -> {
				enApps = !enApps
				decir(t.getString(if (enApps) R.string.busqueda_braille_apps else R.string.busqueda_braille_elementos))
				buscar(null)
			}
			2 to DireccionBraille.ABAJO -> {
				val elegido = resultados.getOrNull(actual)
				if (elegido == null) {
					decir(t.getString(R.string.braille_lista_elige_antes))
					return
				}
				cerrar()
				lector.manejador.postDelayed({ if (!elegido.second()) lector.decir(R.string.busqueda_braille_ya_no) }, ESPERA_MS)
			}
			2 to DireccionBraille.ARRIBA -> cerrar()
			else -> decir(t.getString(R.string.busqueda_braille_ayuda))
		}
	}

	/** Busca otra vez con la consulta de ahora; con [escrito], lo dice delante. */
	private fun buscar(escrito: String?) {
		val q = normalizar(consulta.toString())
		resultados = if (q.isBlank()) emptyList() else if (enApps) apps(q) else elementos(q)
		actual = -1
		val n = resultados.size
		val cuantos = t.resources.getQuantityString(R.plurals.busqueda_braille_resultados, n, n)
		decir(listOfNotNull(escrito ?: consulta.toString().ifBlank { null }, cuantos).joinToString(", "))
	}

	private fun apps(q: String): List<Pair<String, () -> Boolean>> {
		val pm = lector.servicio.packageManager
		return runCatching {
			pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
				.map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
				.filter { normalizar(it.first).contains(q) }
				.distinctBy { it.second }
				.sortedWith(compareBy({ !normalizar(it.first).startsWith(q) }, { it.first.lowercase() }))
				.take(MAXIMO)
				.map { (nombre, paquete) ->
					nombre to {
						val intent = pm.getLaunchIntentForPackage(paquete)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						intent != null && runCatching { lector.servicio.startActivity(intent); true }.getOrElse {
							Registro.w(ETIQUETA, "No se pudo abrir la app", it)
							false
						}
					}
				}
		}.getOrElse {
			Registro.w(ETIQUETA, "No se pudieron leer las apps", it)
			emptyList()
		}
	}

	private fun elementos(q: String): List<Pair<String, () -> Boolean>> {
		val raiz = lector.raiz() ?: return emptyList()
		return lector.navegador.orden(raiz).asSequence()
			.filter { !it.isPassword && it.isVisibleToUser && !(Build.VERSION.SDK_INT >= 34 && it.isAccessibilityDataSensitive) }
			.mapNotNull { n ->
				val e = lector.elemento(n)
				val nombre = listOf(e.nombre, e.texto).filter { it.isNotBlank() }.joinToString(" ")
				if (nombre.isBlank() || !normalizar(nombre).contains(q)) null else nombre to n
			}
			.take(MAXIMO)
			.map { (nombre, n) -> nombre to { usar(n) } }
			.toList()
	}

	/** Activa [n] si sigue en la pantalla; si no, no elige otro. */
	private fun usar(n: AccessibilityNodeInfo): Boolean {
		if (!runCatching { n.refresh() }.getOrDefault(false)) return false
		lector.enfocar(n)
		return hacer(Accion.ACTIVAR)
	}

	private fun normalizar(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

	private companion object {
		const val ETIQUETA = "BusquedaBraille"
		const val MAXIMO = 50
		/** Lo que tarda en quitarse la superficie antes de activar (para que el toque vaya a la app). */
		const val ESPERA_MS = 250L
	}
}
