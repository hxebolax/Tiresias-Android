package com.xebolax.tiresias

import android.accessibilityservice.AccessibilityService
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Prueba del agente android-accesible adaptada a Tiresias (las pantallas de la
 * app, con el lector que esté activo en el emulador).
 *
 * Recorre las pantallas de la app con TalkBack activo, como quien desliza el
 * dedo de elemento en elemento: pone el foco de accesibilidad en cada uno
 * (TalkBack lo lee de verdad; con su registro detallado se ve qué dice), prueba
 * los deslizadores y pasa las comprobaciones del Accessibility Test Framework
 * de Google (las de Accessibility Scanner: etiquetas, tamaño de los objetivos,
 * contraste, duplicados...). Todo queda en el registro con las etiquetas
 * «Recorrido», «ATF», «Foco», «Eventos» y «Menu».
 *
 * Por defecto no falla: es un informe para revisar. Con el argumento
 * estricto=true falla si hay elementos sin nombre o errores del ATF.
 *
 * Se pasa con TalkBack encendido en el emulador (ver referencias/pruebas.md):
 *
 *     gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xebolax.tiresias.AccesibilidadTest
 *     adb logcat -d -s Recorrido ATF Foco Eventos Menu
 *
 * Dependencias (androidTestImplementation): androidx.test:runner, androidx.test:core,
 * androidx.test.ext:junit y com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework.
 *
 * QUÉ ADAPTAR: el paquete; pantallas (cada pantalla con el Intent que la abre);
 * preparar() para crear datos de muestra (sin listas vacías, que no prueban nada);
 * y los textos de focoVuelveTrasVentanas().
 */
@RunWith(AndroidJUnit4::class)
class AccesibilidadTest {

	private val instrumentacion = InstrumentationRegistry.getInstrumentation()
	private lateinit var app: Context
	private lateinit var ui: UiAutomation
	private val estricto = InstrumentationRegistry.getArguments().getString("estricto") == "true"
	private val problemas = mutableListOf<String>()

	/** Pantallas que se recorren además de la de inicio: nombre para el registro y cómo abrirla. */
	private val pantallas: List<Pair<String, (Context) -> Intent>> = listOf(
		"Ajustes" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadAjustes::class.java) },
		"Descargas" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadRecursos::class.java) },
		"Gestos" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadGestos::class.java) },
		"Diccionario" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadDiccionario::class.java) },
		"Etiquetas" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadEtiquetas::class.java) },
		"Perfiles" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadPerfiles::class.java) },
		"Complementos" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadComplementos::class.java) },
		"Tutorial" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadTutorial::class.java) },
		"Asistente" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadAsistente::class.java) },
		"Acerca de" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadAcerca::class.java) },
		"Sonidos" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadSonidos::class.java) },
		"Personas" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadPersonas::class.java) },
		"Menú circular" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadOrdenar::class.java).putExtra(com.xebolax.tiresias.ui.ActividadOrdenar.EXTRA_LISTA, com.xebolax.tiresias.ui.ActividadOrdenar.LISTA_CIRCULAR) },
		"Estado" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadOrdenar::class.java).putExtra(com.xebolax.tiresias.ui.ActividadOrdenar.EXTRA_LISTA, com.xebolax.tiresias.ui.ActividadOrdenar.LISTA_ESTADO) },
		"Combinaciones braille" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadTeclasBraille::class.java) },
		"Eloquence" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadEloquence::class.java) },
		"Modo senior" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadModoSenior::class.java) },
		// Instalado en preparar(): el de los códigos tiene ajustes de todos los tipos
		"Ajustes de un complemento" to { c -> Intent(c, com.xebolax.tiresias.ui.ActividadAjustesComplemento::class.java).putExtra(com.xebolax.tiresias.ui.ActividadAjustesComplemento.EXTRA_ID, "codigos-de-verificacion") },
	)

	@Before
	fun preparar() {
		app = instrumentacion.targetContext
		// Sin suprimir TalkBack: tiene que seguir hablando mientras se recorre
		ui = instrumentacion.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
		// La app en español, como la usará el usuario (quita esta línea si la app no tiene idioma por app)
		if (Build.VERSION.SDK_INT >= 33) ui.executeShellCommand("cmd locale set-app-locales ${app.packageName} --locales es-ES").close()
		// Sin el aviso del permiso de notificaciones tapando la pantalla
		if (Build.VERSION.SDK_INT >= 33) ui.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
		// El asistente no se abre solo encima de la pantalla principal
		com.xebolax.tiresias.base.Ajustes(app).asistenteHecho = true
		// Un complemento con ajustes, para recorrer su pantalla de ajustes
		val codigo = instrumentacion.context.assets.open("codigos-de-verificacion/complemento.js").bufferedReader().use { it.readText() }
		com.xebolax.tiresias.scripts.Scripts(null, app).instalar(codigo)
		// La instrumentación reinicia el proceso de Tiresias y Android no vuelve a conectar el
		// servicio solo: se reactiva (solo en el emulador) para que sea él el lector de la prueba
		if (com.xebolax.tiresias.servicio.ServicioTiresias.instancia == null) {
			val servicio = app.packageName + "/com.xebolax.tiresias.servicio.ServicioTiresias"
			ui.executeShellCommand("settings put secure enabled_accessibility_services null").close()
			esperar(800)
			ui.executeShellCommand("settings put secure enabled_accessibility_services $servicio").close()
			ui.executeShellCommand("settings put secure accessibility_enabled 1").close()
			repeat(200) { if (com.xebolax.tiresias.servicio.ServicioTiresias.instancia == null) esperar(100) }
		}
		esperar(1500)
	}

	// --- Utilidades ----------------------------------------------------------------------------

	private fun esperar(ms: Long) = Thread.sleep(ms)

	private fun inicio(): Intent = requireNotNull(app.packageManager.getLaunchIntentForPackage(app.packageName)) { "La app no tiene pantalla de inicio" }

	private fun abrir(intent: Intent, espera: Long = 4000) {
		app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
		esperar(espera)
	}

	private fun raiz(): AccessibilityNodeInfo? {
		repeat(10) {
			ui.rootInActiveWindow?.let { return it }
			esperar(300)
		}
		return null
	}

	/** Lo que diría TalkBack de un nodo: su nombre y su estado. */
	private fun texto(n: AccessibilityNodeInfo): String {
		val partes = listOfNotNull(
			n.contentDescription?.toString()?.takeIf { it.isNotBlank() } ?: n.text?.toString()?.takeIf { it.isNotBlank() },
			if (Build.VERSION.SDK_INT >= 30) n.stateDescription?.toString()?.takeIf { it.isNotBlank() } else null,
		)
		return partes.joinToString(", ")
	}

	/** Su texto o, en una fila, el de lo que tiene dentro. */
	private fun nombre(n: AccessibilityNodeInfo): String = texto(n).ifBlank {
		(0 until n.childCount).mapNotNull { n.getChild(it)?.let { h -> nombre(h) } }.filter { it.isNotBlank() }.joinToString(", ")
	}

	/** Toca el primer elemento con ese texto o descripción (como un doble toque de TalkBack). */
	private fun tocar(texto: String): Boolean {
		val nodo = elemento(texto) ?: return false
		val ok = nodo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
		esperar(2500)
		return ok
	}

	/** El elemento pulsable visible que se llama exactamente así (o, si no hay, el que lo contiene). */
	private fun elemento(texto: String): AccessibilityNodeInfo? {
		val todos = raiz()?.findAccessibilityNodeInfosByText(texto)?.filter { it.isVisibleToUser }.orEmpty()
		val n = todos.firstOrNull { (it.contentDescription ?: it.text)?.toString()?.trim() == texto } ?: todos.firstOrNull() ?: return null
		var p: AccessibilityNodeInfo? = n
		while (p != null && !p.isClickable && !p.isLongClickable) p = p.parent
		return p ?: n
	}

	/** ¿TalkBack se pararía en este elemento al deslizar? (tiene acción, o texto sin un padre con acción) */
	private fun enfocable(n: AccessibilityNodeInfo): Boolean {
		if (!n.isVisibleToUser) return false
		// TalkBack no se para en los contenedores con desplazamiento que tienen elementos dentro
		val clase = n.className?.toString().orEmpty()
		if ((n.isScrollable || clase.endsWith("ScrollView") || clase.endsWith("RecyclerView")) && n.childCount > 0) return false
		if (n.isClickable || n.isLongClickable || n.isFocusable || (Build.VERSION.SDK_INT >= 28 && n.isScreenReaderFocusable)) return true
		if (n.text.isNullOrBlank() && n.contentDescription.isNullOrBlank()) return false
		var p = n.parent
		while (p != null) {
			if (p.isClickable || p.isFocusable || (Build.VERSION.SDK_INT >= 28 && p.isScreenReaderFocusable)) return false
			p = p.parent
		}
		return true
	}

	private fun recoger(n: AccessibilityNodeInfo, lista: MutableList<AccessibilityNodeInfo>) {
		// Lo que TalkBack no visita (una vista marcada como no importante dentro de un control propio) no cuenta
		if (enfocable(n) && n.isImportantForAccessibility) lista += n
		for (i in 0 until n.childCount) n.getChild(i)?.let { recoger(it, lista) }
	}

	/** Recorre la pantalla con el foco de TalkBack y pasa las comprobaciones de accesibilidad. */
	private fun revisar(pantalla: String, maximo: Int = 45) {
		val r = raiz() ?: run { Log.w("Recorrido", "$pantalla | SIN VENTANA"); return }
		Log.i("Recorrido", "=== $pantalla ===")
		val nodos = mutableListOf<AccessibilityNodeInfo>()
		recoger(r, nodos)
		nodos.take(maximo).forEachIndexed { i, n ->
			val clase = n.className?.toString()?.substringAfterLast('.').orEmpty()
			val sinNombre = nombre(n).isBlank()
			if (sinNombre) problemas += "$pantalla: elemento ${i + 1} ($clase) sin nombre"
			Log.i("Recorrido", "$pantalla | ${i + 1} | $clase | ${if (sinNombre) "¡SIN NOMBRE!" else nombre(n)}" +
				(if (n.isClickable) " | pulsable" else "") + (if (n.actionList.size > 6) " | ${n.actionList.size} acciones" else ""))
			n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
			esperar(700)
			// Deslizadores: deslizar arriba como con TalkBack, ver qué valor dice y volver
			if (clase == "SeekBar" && n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) {
				val antes = texto(n)
				n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
				esperar(900)
				n.refresh()
				Log.i("Recorrido", "$pantalla | DESLIZADOR | $antes  →  ${texto(n)}")
				n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
				esperar(600)
			}
		}
		if (nodos.size > maximo) Log.i("Recorrido", "$pantalla | ... ${nodos.size - maximo} más")
		comprobar(pantalla, r)
	}

	/**
	 * Accessibility Test Framework sobre la jerarquía y una captura (para el
	 * contraste). Falsos avisos de contraste conocidos: el recuadro de foco de
	 * TalkBack y el oscurecido de detrás de un cajón abierto.
	 */
	private fun comprobar(pantalla: String, r: AccessibilityNodeInfo) {
		runCatching {
			val jerarquia = AccessibilityHierarchyAndroid.newBuilder(r, app).build()
			val parametros = Parameters()
			ui.takeScreenshot()?.let { parametros.putScreenCapture(BitmapImage(it)) }
			val comprobaciones = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)
			for (c in comprobaciones) {
				for (res in c.runCheckOnHierarchy(jerarquia, null, parametros)) {
					if (res.type != AccessibilityCheckResultType.ERROR && res.type != AccessibilityCheckResultType.WARNING) continue
					val e = res.element
					val quien = listOfNotNull(e?.contentDescription?.toString(), e?.text?.toString(), e?.className?.toString()?.substringAfterLast('.'))
						.firstOrNull { it.isNotBlank() }.orEmpty()
					val linea = "$pantalla | ${res.type} | ${c.javaClass.simpleName} | $quien | ${res.getMessage(Locale.getDefault())}"
					Log.w("ATF", linea)
					if (res.type == AccessibilityCheckResultType.ERROR) problemas += linea
				}
			}
		}.onFailure { Log.e("ATF", "$pantalla | no se pudo comprobar", it) }
	}

	private fun comprobarEstricto() {
		problemas.forEach { Log.w("Recorrido", "PROBLEMA | $it") }
		if (estricto) assertTrue("Problemas de accesibilidad:\n" + problemas.joinToString("\n"), problemas.isEmpty())
	}

	// --- El recorrido ----------------------------------------------------------------------------

	@Test
	fun recorrerTodo() {
		abrir(inicio())
		revisar("Inicio")
		if (tocar(app.getString(R.string.abrir_menu))) {
			revisar("Menú lateral")
			atras()
		}
		for ((titulo, abrirla) in pantallas) {
			abrir(abrirla(app))
			revisar(titulo)
		}
		comprobarEstricto()
	}

	/** Qué expone a TalkBack cada elemento del menú lateral: solo el destino actual «seleccionado», nada «marcable». */
	@Test
	fun menuLateral() {
		abrir(inicio())
		if (!tocar(app.getString(R.string.abrir_menu))) {
			Log.i("Menu", "Esta pantalla no tiene menú lateral")
			return
		}
		esperar(1000)
		Log.i("Menu", "Foco al abrir: ${conFoco()?.let { "${nombre(it)} seleccionado=${it.isSelected}" }}")
		val r = raiz() ?: return
		val nodos = mutableListOf<AccessibilityNodeInfo>()
		recoger(r, nodos)
		val seleccionados = nodos.count { it.isSelected }
		for (n in nodos) {
			Log.i("Menu", "${n.className?.toString()?.substringAfterLast('.')} | ${nombre(n)} | pulsable=${n.isClickable} marcable=${n.isCheckable} " +
				"marcado=${n.isChecked} seleccionado=${n.isSelected} enfocable=${n.isFocusable} hijos=${n.childCount}")
			if (n.isCheckable) problemas += "Menú lateral: «${nombre(n)}» es marcable (TalkBack dirá marcado o no marcado)"
		}
		if (seleccionados != 1) problemas += "Menú lateral: $seleccionados elementos seleccionados (debe ser 1, el destino actual)"
		atras()
		comprobarEstricto()
	}

	// --- El foco vuelve a su sitio al cerrar ventanas ------------------------------------------

	private fun conFoco(): AccessibilityNodeInfo? = raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

	private fun atras() {
		ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
		esperar(1500)
	}

	/** Pone el foco en el elemento, abre algo encima, lo cierra y mira si el foco ha vuelto al mismo sitio. */
	private fun vuelta(prueba: String, n: AccessibilityNodeInfo?, abrirlo: (AccessibilityNodeInfo) -> Unit, cerrarlo: () -> Unit) {
		if (n == null) { Log.w("Foco", "$prueba | NO ENCONTRADO"); return }
		n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
		esperar(1000)
		val antes = nombre(n)
		abrirlo(n)
		esperar(2500)
		val encima = raiz()?.let { r -> mutableListOf<AccessibilityNodeInfo>().also { recoger(r, it) }.take(4).joinToString(" / ") { nombre(it) } }
		// Qué eventos llegan a TalkBack al cerrar (un TYPE_WINDOW_STATE_CHANGED de la pantalla de debajo repetiría su título)
		ui.setOnAccessibilityEventListener { e ->
			if (e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
				Log.i("Eventos", "$prueba | ${AccessibilityEvent.eventTypeToString(e.eventType)} | ${e.className} | ${e.text}")
		}
		cerrarlo()
		esperar(2500)
		ui.setOnAccessibilityEventListener(null)
		val despues = conFoco()
		val bien = despues != null && (despues == n || nombre(despues) == antes)
		if (!bien) problemas += "$prueba: el foco no volvió (antes: $antes, después: ${despues?.let { nombre(it) } ?: "nada"})"
		Log.i("Foco", "$prueba | ${if (bien) "BIEN" else "MAL"} | antes: $antes | encima: $encima | después: ${despues?.let { nombre(it) } ?: "nada"}")
	}

	@Test
	fun focoVuelveTrasVentanas() {
		abrir(inicio())
		// El menú lateral y volver: el foco tiene que quedar en «Abrir menú»
		vuelta("Menú lateral", elemento(app.getString(R.string.abrir_menu)), { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }, ::atras)
		// Las acciones de una fila (pulsación larga) y volver. Adapta el texto a un elemento de los datos de muestra:
		// vuelta("Fila: acciones", elemento("Elemento de prueba"), { it.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }, ::atras)
		comprobarEstricto()
	}
}
