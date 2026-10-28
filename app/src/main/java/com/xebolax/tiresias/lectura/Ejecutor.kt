package com.xebolax.tiresias.lectura

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Describir
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.nucleo.Granularidad
import com.xebolax.tiresias.nucleo.NivelPuntuacion
import com.xebolax.tiresias.nucleo.NivelVerbosidad
import com.xebolax.tiresias.ui.ActividadPrincipal
import com.xebolax.tiresias.ui.ActividadTutorial
import com.xebolax.tiresias.ventanas.Ventanas
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Hace las [Accion]es de Tiresias. Todo corre en el hilo del lector; lo que
 * necesita el hilo principal (ventanas) lo pasa [Ventanas].
 */
class Ejecutor(private val lector: Lector, private val ventanas: Ventanas, private val alSuspender: (Boolean) -> Unit) {
	private val servicio = lector.servicio
	private val nav get() = lector.navegador
	private val t get() = lector.textos

	/** El control de lectura actual y la acción elegida dentro de los controles «Acciones» y «Edición». */
	private var accionElegida = -1
	private val modoSeleccion: Boolean get() = lector.edicion.modoSeleccion
	private var pausado: String? = null
	@Volatile var suspendido = false
		private set

	fun ejecutar(a: Accion): Boolean {
		if (suspendido && a != Accion.SUSPENDER && a != Accion.MODO_SEGURO) return false
		lector.momentoUsuario = SystemClock.uptimeMillis()
		if (a != Accion.LEER_DESDE_ARRIBA && a != Accion.LEER_DESDE_AQUI && a != Accion.PAUSAR_VOZ) lector.continua.parar()
		// En voz a demanda, lo que se pide con una acción sí se dice (moverse, no)
		if (a !in NAVEGAR_CALLADO) lector.voz.pedidoHasta = SystemClock.uptimeMillis() + 2500
		return try {
			lector.virtual?.let { v -> enVirtual(v, a)?.let { return it } }
			if (a != Accion.SOS && cuentaSos != null) {
				cancelarSos()
				return true
			}
			hacer(a)
		} catch (e: Exception) {
			com.xebolax.tiresias.base.Registro.e(ETIQUETA, "Fallo al hacer la acción $a", e)
			lector.sonidos.tocar(Sonido.ERROR)
			false
		}
	}

	private fun hacer(a: Accion): Boolean = when (a) {
		Accion.NINGUNA -> false
		Accion.SIGUIENTE -> nav.siguiente()
		Accion.ANTERIOR -> nav.anterior()
		Accion.PRIMERO -> nav.primero()
		Accion.ULTIMO -> nav.ultimo()
		Accion.SELECTOR_SIGUIENTE -> selector(true)
		Accion.SELECTOR_ANTERIOR -> selector(false)
		Accion.CONTROL_SIGUIENTE -> cambiarControl(true)
		Accion.CONTROL_ANTERIOR -> cambiarControl(false)
		Accion.VENTANA_SIGUIENTE -> nav.ventana(true)
		Accion.VENTANA_ANTERIOR -> nav.ventana(false)
		Accion.CONTENEDOR_SIGUIENTE -> nav.contenedor(true)
		Accion.CONTENEDOR_ANTERIOR -> nav.contenedor(false)
		Accion.CUADRICULA_ARRIBA -> nav.cuadricula(0, -1)
		Accion.CUADRICULA_ABAJO -> nav.cuadricula(0, 1)
		Accion.CUADRICULA_IZQUIERDA -> nav.cuadricula(-1, 0)
		Accion.CUADRICULA_DERECHA -> nav.cuadricula(1, 0)
		Accion.DONDE_ESTOY -> dondeEstoy()
		Accion.ACTIVAR -> activar()
		Accion.MANTENER -> mantener()
		Accion.DESPLAZAR_ADELANTE -> desplazar(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
		Accion.DESPLAZAR_ATRAS -> desplazar(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
		Accion.DESPLAZAR_ARRIBA -> desplazar(android.R.id.accessibilityActionScrollUp, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
		Accion.DESPLAZAR_ABAJO -> desplazar(android.R.id.accessibilityActionScrollDown, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
		Accion.DESPLAZAR_IZQUIERDA -> desplazar(android.R.id.accessibilityActionScrollLeft, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
		Accion.DESPLAZAR_DERECHA -> desplazar(android.R.id.accessibilityActionScrollRight, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
		Accion.INICIO -> global(AccessibilityService.GLOBAL_ACTION_HOME)
		Accion.ATRAS -> when {
			circular.abierto -> true.also { circular.cerrar() }
			ventanas.hayAbierta -> true.also { ventanas.cerrar() }
			else -> global(AccessibilityService.GLOBAL_ACTION_BACK)
		}
		Accion.RECIENTES -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
		Accion.NOTIFICACIONES -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS).also { lector.momentoPersiana = SystemClock.uptimeMillis() }
		Accion.AJUSTES_RAPIDOS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS).also { lector.momentoPersiana = SystemClock.uptimeMillis() }
		Accion.PANTALLA_DIVIDIDA -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
		Accion.BLOQUEAR -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else noDisponible()
		Accion.CAPTURA -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) else noDisponible()
		Accion.ENCENDIDO -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
		Accion.TODAS_APPS -> if (Build.VERSION.SDK_INT >= 31) global(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS) else noDisponible()
		Accion.MENU_TIRESIAS -> menuTiresias()
		Accion.MENU_ACCIONES -> menuAcciones()
		Accion.MENU_EDICION -> menuEdicion()
		Accion.BUSCAR -> buscar()
		Accion.LISTA_ELEMENTOS -> listaElementos()
		Accion.HISTORIAL_HABLA -> historialHabla()
		Accion.HISTORIAL_NOTIFICACIONES -> historialAvisos()
		Accion.LEER_DESDE_ARRIBA -> true.also { lector.continua.desdeArriba() }
		Accion.LEER_DESDE_AQUI -> true.also { lector.continua.desdeAqui() }
		Accion.REPETIR -> ultimoDicho()?.let { lector.voz.decir(Enunciado(it, preparar = false)); true } ?: false
		Accion.DELETREAR -> ultimoDicho()?.let { lector.voz.decir(Enunciado(it, deletrear = true)); true } ?: false
		Accion.DELETREO_FONETICO -> deletreoFonetico()
		Accion.LEER_TITULO -> true.also { lector.decir(lector.tituloActivo().ifBlank { t.getString(R.string.lect_sin_titulo) }) }
		Accion.ESTADO_SISTEMA -> estadoSistema()
		Accion.LEER_PORTAPAPELES -> true.also { lector.edicion.leerPortapapeles() }
		Accion.COPIAR_ULTIMO -> ultimoDicho()?.let { lector.edicion.portapapeles(it); lector.decir(R.string.copiado); true } ?: false
		Accion.HISTORIAL_PORTAPAPELES -> historialCopiado()
		Accion.COPIAR_ANADIENDO -> ultimoDicho()?.let {
			lector.edicion.copiarAnadiendo(it)
			lector.decir(R.string.portapapeles_anadido)
			true
		} ?: false.also { lector.decir(R.string.portapapeles_nada_que_anadir) }
		Accion.COPIAR_LISTA -> copiarLista()
		Accion.AYUDA_OTRA_PERSONA -> lector.gestos.alternarAyuda()
		Accion.PANTALLA_VIRTUAL -> lector.vision.pantallaVirtual()
		Accion.INICIO_SEGURO -> inicioSeguro()
		Accion.QUE_PUEDO_HACER -> quePuedoHacer()
		Accion.SOS -> sos()
		Accion.VIGILAR -> lector.alternarVigilar(objetivo())
		Accion.IR_A_POSICION -> irAPosicion()
		Accion.LEER_QR -> lector.vision.leerCodigos()
		Accion.RESUMEN_AVISOS -> resumenAvisos()
		Accion.CANAL_VOZ -> canalVoz()
		Accion.VOZ_A_DEMANDA -> {
			val v = lector.voz
			v.aDemanda = !v.aDemanda
			lector.decir(if (v.aDemanda) R.string.voz_demanda_si else R.string.voz_demanda_no)
			true
		}
		Accion.PISTA_SIGUIENTE -> teclaMultimedia(KeyEvent.KEYCODE_MEDIA_NEXT)
		Accion.PISTA_ANTERIOR -> teclaMultimedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
		Accion.POSICION_CURSOR -> posicionCursor()
		Accion.PONER_VALOR -> ponerValor(objetivo())
		Accion.FORMATO_TEXTO -> formatoTexto(objetivo())
		Accion.PONER_MARCA -> ponerMarca()
		Accion.IR_A_MARCA -> irAMarca()
		Accion.LEER_FILA -> leerLinea(fila = true)
		Accion.LEER_COLUMNA -> leerLinea(fila = false)
		Accion.NOMBRAR_VENTANA -> nombrarVentana()
		Accion.SELECCIONAR_TEXTO -> seleccionarTexto()
		Accion.PAUSAR_VOZ -> pausar()
		Accion.ALTERNAR_VOZ -> alternarVoz()
		Accion.VELOCIDAD_MAS -> cambiarVelocidad(10)
		Accion.VELOCIDAD_MENOS -> cambiarVelocidad(-10)
		Accion.TONO_MAS -> cambiarTono(10)
		Accion.TONO_MENOS -> cambiarTono(-10)
		Accion.VOLUMEN_MAS -> volumen(true)
		Accion.VOLUMEN_MENOS -> volumen(false)
		Accion.PUNTUACION_SIGUIENTE -> cambiarPuntuacion(true)
		Accion.VERBOSIDAD_SIGUIENTE -> cambiarVerbosidad(true)
		Accion.VOZ_SIGUIENTE -> cambiarMotor(true)
		Accion.VALOR_MAS -> ajustarDeslizador(true, siempre = true) ?: false
		Accion.VALOR_MENOS -> ajustarDeslizador(false, siempre = true) ?: false
		Accion.LISTA_PRINCIPIO -> irAlExtremo(false)
		Accion.LISTA_FINAL -> irAlExtremo(true)
		Accion.IDIOMA_SIGUIENTE -> cambiarIdioma()
		Accion.COPIAR -> lector.edicion.copiar()
		Accion.CORTAR -> lector.edicion.cortar()
		Accion.PEGAR -> lector.edicion.pegar()
		Accion.SELECCIONAR_TODO -> lector.edicion.seleccionarTodo()
		Accion.MODO_SELECCION -> true.also { lector.edicion.alternarSeleccion() }
		Accion.DESHACER, Accion.REHACER -> deshacer(a == Accion.DESHACER)
		Accion.CURSOR_INICIO -> lector.edicion.cursorA(true)
		Accion.CURSOR_FINAL -> lector.edicion.cursorA(false)
		Accion.DESCRIBIR_IA -> lector.vision.describirElemento()
		Accion.PANTALLA_IA -> lector.vision.describirPantalla()
		Accion.PREGUNTAR_IA -> lector.vision.preguntar()
		Accion.OCR_ELEMENTO -> lector.vision.ocrElemento()
		Accion.OCR_PANTALLA -> lector.vision.ocrPantalla()
		Accion.ETIQUETAR -> etiquetar()
		Accion.CORTINA -> true.also {
			val poner = !ventanas.cortinaPuesta
			ventanas.ponerCortina(poner)
			lector.decir(if (poner) R.string.cortina_si else R.string.cortina_no)
		}
		Accion.MULTIMEDIA -> multimedia()
		Accion.DICTADO -> lector.escucha.dictar()
		Accion.ORDENES_VOZ -> lector.escucha.ordenes()
		Accion.PASAR_GESTO -> lector.gestos.pasarSiguiente()
		Accion.TOQUE_DIRECTO -> lector.gestos.alternarToqueDirecto()
		Accion.AJUSTES_TIRESIAS -> abrir(ActividadPrincipal::class.java)
		Accion.AYUDA_ENTRADA -> true.also {
			lector.ayudaEntrada = !lector.ayudaEntrada
			lector.decir(if (lector.ayudaEntrada) R.string.ayuda_entrada_si else R.string.ayuda_entrada_no)
		}
		Accion.TUTORIAL -> abrir(ActividadTutorial::class.java)
		Accion.SUSPENDER -> true.also { suspender(!suspendido) }
		Accion.PERFIL_SIGUIENTE -> lector.perfiles.siguiente()
		Accion.BRAILLE_TECLADO -> lector.braille.abrirTeclado()
		Accion.PRIMERO_BRAILLE -> lector.braille.alternarPrimero()
		Accion.DESCRIPCION_CONTINUA -> lector.vision.alternarDescripcionContinua()
		Accion.ACTUAR_IA -> lector.vision.actuar()
		Accion.TRADUCIR_IA -> lector.vision.traducir()
		Accion.SALIDA_AUDIO -> cambiarSalida()
		Accion.ENCABEZADO_SIGUIENTE, Accion.ENCABEZADO_ANTERIOR -> nav.porTipo(ControlLectura.ENCABEZADOS, a == Accion.ENCABEZADO_SIGUIENTE)
		Accion.ENLACE_SIGUIENTE, Accion.ENLACE_ANTERIOR -> nav.porTipo(ControlLectura.ENLACES, a == Accion.ENLACE_SIGUIENTE)
		Accion.ACTIVABLE_SIGUIENTE, Accion.ACTIVABLE_ANTERIOR -> nav.porTipo(ControlLectura.CONTROLES, a == Accion.ACTIVABLE_SIGUIENTE)
		Accion.BOTON_SIGUIENTE, Accion.BOTON_ANTERIOR -> nav.porTipo(ControlLectura.BOTONES, a == Accion.BOTON_SIGUIENTE)
		Accion.CAMPO_SIGUIENTE, Accion.CAMPO_ANTERIOR -> nav.porTipo(ControlLectura.CAMPOS, a == Accion.CAMPO_SIGUIENTE)
		Accion.CASILLA_SIGUIENTE, Accion.CASILLA_ANTERIOR -> nav.porTipo(ControlLectura.CASILLAS, a == Accion.CASILLA_SIGUIENTE)
		Accion.LISTA_SIGUIENTE, Accion.LISTA_ANTERIOR -> nav.porTipo(ControlLectura.LISTAS, a == Accion.LISTA_SIGUIENTE)
		Accion.TABLA_SIGUIENTE, Accion.TABLA_ANTERIOR -> nav.porTipo(ControlLectura.TABLAS, a == Accion.TABLA_SIGUIENTE)
		Accion.REGION_SIGUIENTE, Accion.REGION_ANTERIOR -> nav.porTipo(ControlLectura.REGIONES, a == Accion.REGION_SIGUIENTE)
		Accion.IMAGEN_SIGUIENTE, Accion.IMAGEN_ANTERIOR -> nav.porTipo(ControlLectura.IMAGENES, a == Accion.IMAGEN_SIGUIENTE)
		Accion.CLIC_DIRECTO -> alternarClicDirecto()
		Accion.MENU_CIRCULAR -> menuCircular()
		Accion.REINICIAR_TACTIL -> reiniciarTactil()
		Accion.APAGAR_SCRIPTS -> true.also { lector.scripts.apagarTodos(); lector.decir(R.string.scripts_apagados) }
		Accion.ORDENES_COMPLEMENTOS -> ordenesComplementos()
		Accion.MODO_SEGURO -> modoSeguro()
	}

	// --- Activar ------------------------------------------------------------------------------

	/** El elemento sobre el que se actúa: el que se ha dicho o, si ya no existe, el que tiene el foco de accesibilidad. */
	private fun objetivo(): AccessibilityNodeInfo? {
		lector.foco?.takeIf { Nodos.vivo(it) }?.let { return it }
		// La app lo rehízo: el más parecido, para no perder el sitio
		lector.foco?.let { viejo -> lector.recuperarFoco(viejo)?.let { return it } }
		return lector.raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: lector.foco
	}

	private fun activar(): Boolean {
		val f = objetivo() ?: return false
		if (lector.control == ControlLectura.EDICION) return hacerEdicionElegida()
		// La acción elegida con arriba y abajo para este elemento (con el control Acciones o con el predeterminado)
		accionElegidaDe(f)?.let { a ->
			lector.sonidos.tocar(Sonido.ACTIVAR)
			return a.second()
		}
		lector.sonidos.tocar(Sonido.ACTIVAR)
		// Una regla puede decir qué hijo se pulsa al activar (el botón de verdad dentro de una fila agrupada)
		if (lector.scriptsListos) lector.scripts.regla(f)?.activar?.let { id ->
			val hijo = runCatching { f.findAccessibilityNodeInfosByViewId(if (id.contains(":id/")) id else "${f.packageName}:id/$id") }.getOrNull()?.firstOrNull()
			if (hijo != null && runCatching { hijo.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)) return true
		}
		// Las apps con «clic directo»: siempre un toque de verdad (TikTok y otras que ignoran el clic de accesibilidad)
		if (f.packageName?.toString()?.let { it in lector.ajustes.appsClicDirecto } == true) return tocarEn(f, false)
		val antes = SystemClock.uptimeMillis()
		val ok = runCatching { f.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
		// Si la app no atiende el clic de accesibilidad (Telegram en HyperOS: Backtalk 266), un toque de verdad
		if (!ok) return tocarEn(f, false)
		com.xebolax.tiresias.base.Registro.i(ETIQUETA, "Clic en ${SystemClock.uptimeMillis() - antes} ms")
		return true
	}

	private fun mantener(): Boolean {
		val f = objetivo() ?: return false
		lector.sonidos.tocar(Sonido.MANTENER)
		val ok = runCatching { f.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }.getOrDefault(false)
		return ok || tocarEn(f, true)
	}

	/**
	 * Un toque (o una pulsación larga) sintético en [n]: antes se pide que se vea
	 * entero, y se toca en el centro de la parte que se ve dentro de su ventana,
	 * no en un borde tapado por otra cosa (Backtalk 92).
	 */
	private fun tocarEn(n: AccessibilityNodeInfo, largo: Boolean): Boolean {
		val r = Nodos.visibleEnVentana(n)
		if (r.isEmpty) return false
		val camino = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
		val gesto = GestureDescription.Builder()
			.addStroke(GestureDescription.StrokeDescription(camino, 0, if (largo) 800 else 60))
			.build()
		return lector.mandarGesto(gesto)
	}

	private fun desplazar(vararg acciones: Int): Boolean {
		val f = lector.foco
		val raiz = lector.raiz() ?: return false
		val lista = f?.let { Nodos.desplazable(it) } ?: Nodos.primerDesplazable(raiz) ?: run {
			lector.sonidos.tocar(Sonido.BORDE)
			return false
		}
		for (a in acciones) {
			if (lista.actionList.any { it.id == a } && runCatching { lista.performAction(a) }.getOrDefault(false)) return true
		}
		// La lista no acepta desplazarse por accesibilidad (Kindle y otras): un arrastre de dos dedos, como haría una persona
		val adelante = acciones.any { it == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || it == android.R.id.accessibilityActionScrollDown || it == android.R.id.accessibilityActionScrollRight }
		val horizontal = acciones.any { it == android.R.id.accessibilityActionScrollLeft || it == android.R.id.accessibilityActionScrollRight }
		if (lector.ajustes.arrastreRespaldo && !Nodos.seDesplazaPorAccesibilidad(lista) && nav.arrastrar(lista, adelante, horizontal)) return true
		lector.sonidos.tocar(Sonido.BORDE)
		return false
	}

	@Volatile private var extremo: Runnable? = null

	/**
	 * Al principio o al final de la lista entera: la desplaza página a página
	 * hasta el tope (100 como mucho; se para si se toca la pantalla) y enfoca el
	 * primer o el último elemento.
	 */
	private fun irAlExtremo(final: Boolean): Boolean {
		val raiz = lector.raiz() ?: return false
		val lista = lector.foco?.let { Nodos.desplazable(it) } ?: Nodos.primerDesplazable(raiz) ?: run {
			lector.sonidos.tocar(Sonido.BORDE)
			return false
		}
		val accion = if (final) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
		var paginas = 0
		val tocado = lector.toques
		if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasExtremo", "empieza en ${lista.className} ${lista.actionList.map { it.id }}")
		lector.sonidos.tocar(Sonido.CARGANDO)
		val paso = object : Runnable {
			override fun run() {
				if (extremo !== this || lector.toques != tocado) {
					if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasExtremo", "cortado (${extremo !== this}, ${lector.toques} != $tocado)")
					if (extremo == null || extremo === this) nav.sinRehacer = false
					return
				}
				runCatching { lista.refresh() }
				val sigue = paginas < 100 && lista.actionList.any { it.id == accion } && runCatching { lista.performAction(accion) }.getOrDefault(false)
				if (sigue) {
					paginas++
					lector.manejador.postDelayed(this, 120)
					return
				}
				extremo = null
				nav.sinRehacer = false
				lector.manejador.postDelayed({
					runCatching { lista.refresh() }
					val dentro = lector.raiz()?.let { r -> nav.orden(r).filter { Nodos.dentroDe(it, lista) } }.orEmpty()
					val destino = if (final) dentro.lastOrNull() else dentro.firstOrNull()
					if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasExtremo", "$paginas páginas, ${dentro.size} dentro, destino ${destino?.className}")
					if (destino != null) lector.enfocar(destino) else lector.sonidos.tocar(Sonido.BORDE)
				}, 250)
			}
		}
		extremo = paso
		nav.sinRehacer = true
		lector.manejador.post(paso)
		return true
	}

	private fun global(accion: Int): Boolean {
		lector.sonidos.tocar(Sonido.GESTO)
		return servicio.performGlobalAction(accion)
	}

	/**
	 * Si el foco está en algo con valor que se puede cambiar (un deslizador, una
	 * barra de volumen, un selector de número), lo sube o lo baja un paso.
	 * Null si no es un deslizador (o el usuario lo ha desactivado): se navega como siempre.
	 */
	private fun ajustarDeslizador(subir: Boolean, siempre: Boolean = false): Boolean? {
		if (!siempre && !lector.ajustes.deslizadorArribaAbajo) return null
		val f = objetivo() ?: return null
		val rango = f.rangeInfo ?: return null
		val acciones = f.actionList.map { it.id }
		val adelante = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
		val atras = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
		val fijar = android.R.id.accessibilityActionSetProgress
		if (adelante !in acciones && atras !in acciones && fijar !in acciones) return null
		lector.cancelarPista()
		val hecho = when {
			subir && adelante in acciones -> f.performAction(adelante)
			!subir && atras in acciones -> f.performAction(atras)
			fijar in acciones -> {
				// Un paso del 5 % (o de 1 en los de números enteros pequeños)
				val total = rango.max - rango.min
				val paso = if (rango.type == AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT && total <= 20) 1f else total / 20f
				val nuevo = (rango.current + if (subir) paso else -paso).coerceIn(rango.min, rango.max)
				f.performAction(fijar, android.os.Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, nuevo) })
			}
			else -> false
		}
		// En el tope: sonido de borde (el valor no cambia)
		if (!hecho) lector.sonidos.tocar(Sonido.BORDE)
		return true
	}

	private fun noDisponible(): Boolean {
		lector.decir(R.string.no_disponible_version)
		return false
	}

	// --- Selector (deslizar arriba y abajo) --------------------------------------------------

	private fun cambiarControl(adelante: Boolean): Boolean {
		// Los controles que ha elegido el usuario, en su orden (Ajustes, Navegación)
		// (los de la web, como enlaces y regiones, solo dentro de una página web, si así se ha elegido)
		val enWeb = lector.foco?.let { nav.enWeb(it) } == true
		val soloWeb = lector.ajustes.controlesSoloWeb
		val lista = lector.ajustes.controlesLectura.filter { enWeb || !soloWeb || !it.soloWeb }.ifEmpty { listOf(ControlLectura.PREDETERMINADO) }
		val i = lista.indexOf(lector.control).let { if (it < 0) (if (adelante) -1 else 0) else it }
		lector.control = lista[(i + if (adelante) 1 else lista.size - 1) % lista.size]
		accionElegida = -1
		edicionElegida = -1
		lector.sonidos.tocar(Sonido.SELECTOR)
		lector.decir(nav.nombreControl(lector.control))
		return true
	}

	private fun selector(abajo: Boolean): Boolean = when (val c = lector.control) {
		// En un deslizador, arriba sube el valor y abajo lo baja (como TalkBack); se puede quitar en Ajustes
		// En un elemento con acciones propias, arriba y abajo las eligen (como el control Acciones); también se puede quitar
		ControlLectura.PREDETERMINADO -> ajustarDeslizador(!abajo) ?: elegirAccionSiHay(abajo) ?: if (abajo) nav.siguiente() else nav.anterior()
		ControlLectura.CARACTERES -> nav.porTexto(Granularidad.CARACTER, abajo, modoSeleccion)
		ControlLectura.PALABRAS -> nav.porTexto(Granularidad.PALABRA, abajo, modoSeleccion)
		ControlLectura.LINEAS -> nav.porTexto(Granularidad.LINEA, abajo, modoSeleccion)
		ControlLectura.PARRAFOS -> nav.porTexto(Granularidad.PARRAFO, abajo, modoSeleccion)
		ControlLectura.CUADRICULA -> nav.cuadricula(0, if (abajo) 1 else -1)
		ControlLectura.VENTANAS -> nav.ventana(abajo)
		ControlLectura.ACCIONES -> elegirAccion(abajo)
		ControlLectura.EDICION -> elegirEdicion(abajo)
		ControlLectura.VELOCIDAD -> cambiarVelocidad(if (abajo) -10 else 10)
		ControlLectura.TONO -> cambiarTono(if (abajo) -10 else 10)
		ControlLectura.VOLUMEN -> volumen(!abajo)
		ControlLectura.PUNTUACION -> cambiarPuntuacion(abajo)
		ControlLectura.IDIOMA -> cambiarIdioma()
		ControlLectura.VERBOSIDAD -> cambiarVerbosidad(abajo)
		ControlLectura.MOTOR -> cambiarMotor(!abajo)
		ControlLectura.MULTIMEDIA -> teclaMultimedia(if (abajo) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS)
		ControlLectura.SALIDA -> cambiarSalida(!abajo)
		// En las apps, los enlaces de dentro del texto uno a uno (el doble toque pulsa el dicho) y luego el elemento siguiente
		ControlLectura.ENLACES -> enlaceDentro(abajo) ?: nav.porTipo(c, abajo)
		else -> nav.porTipo(c, abajo)
	}

	/**
	 * Con el control Enlaces fuera de la web: el enlace siguiente o anterior de
	 * dentro del texto enfocado (son las primeras opciones de [opcionesDe], así que
	 * el doble toque lo pulsa). Null si no hay o ya no quedan: se pasa al elemento.
	 */
	private fun enlaceDentro(abajo: Boolean): Boolean? {
		val f = objetivo() ?: return null
		if (nav.enWeb(f)) return null
		val enlaces = Nodos.enlacesEnTexto(f)
		if (enlaces.isEmpty()) return null
		val actual = if (accionEnFoco == lector.cambiosFoco && accionElegida in enlaces.indices) accionElegida else -1
		val siguiente = if (abajo) actual + 1 else if (actual < 0) enlaces.lastIndex else actual - 1
		if (siguiente !in enlaces.indices) return null
		lector.cancelarPista()
		accionElegida = siguiente
		accionEnFoco = lector.cambiosFoco
		lector.sonidos.tocar(Sonido.SELECTOR)
		lector.decir(t.getString(R.string.acc_enlace, enlaces[siguiente].first))
		return true
	}

	/** Las acciones del elemento enfocado (las propias de la app y las básicas que tenga). */
	private fun accionesDe(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo.AccessibilityAction> {
		val todas = n.actionList
		val propias = todas.filter { it.label != null && Describir.accionesPropias(listOf(it)) > 0 }
		val basicas = todas.filter { it.id == AccessibilityNodeInfo.ACTION_CLICK || it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK ||
			it.id == AccessibilityNodeInfo.ACTION_EXPAND || it.id == AccessibilityNodeInfo.ACTION_COLLAPSE || it.id == AccessibilityNodeInfo.ACTION_DISMISS }
		return propias + basicas
	}

	private fun nombreAccion(a: AccessibilityNodeInfo.AccessibilityAction): String = a.label?.toString() ?: when (a.id) {
		AccessibilityNodeInfo.ACTION_CLICK -> t.getString(R.string.acc_activar)
		AccessibilityNodeInfo.ACTION_LONG_CLICK -> t.getString(R.string.acc_mantener)
		AccessibilityNodeInfo.ACTION_EXPAND -> t.getString(R.string.acc_expandir)
		AccessibilityNodeInfo.ACTION_COLLAPSE -> t.getString(R.string.acc_contraer)
		AccessibilityNodeInfo.ACTION_DISMISS -> t.getString(R.string.acc_descartar)
		else -> t.getString(R.string.acc_desconocida)
	}

	/**
	 * Todo lo que se puede hacer con [n]: sus acciones, los botones de dentro si
	 * una regla lo agrupa, y las que le añaden los complementos.
	 */
	/** Las opciones de [n]; [delMenu]: las del menú de acciones, con alguna más que no entra en arriba y abajo. */
	private fun opcionesDe(n: AccessibilityNodeInfo, delMenu: Boolean = false): List<Pair<String, () -> Boolean>> {
		val lista = ArrayList<Pair<String, () -> Boolean>>()
		// Los enlaces de dentro del texto, primero (en un aviso, «Más información» o «Instalar de todas formas»)
		for ((texto, enlace) in Nodos.enlacesEnTexto(n)) lista += t.getString(R.string.acc_enlace, texto) to { pulsarEnlace(enlace) }
		// En un campo, las faltas que marca el corrector: cambiarlas por lo que propone, o ir a ellas
		for (f in Nodos.faltas(n).take(5)) {
			for (s in f.sugerencias) lista += t.getString(R.string.acc_corregir, f.palabra, s) to { corregir(n, f, s) }
			lista += t.getString(R.string.acc_ir_falta, f.palabra) to { irAFalta(n, f) }
		}
		for (a in accionesDe(n)) lista += nombreAccion(a) to { runCatching { n.performAction(a.id) }.getOrDefault(false) }
		// Un deslizador: poner un valor exacto, solo en el menú (arriba y abajo siguen cambiando su valor)
		if (delMenu && n.rangeInfo != null && n.actionList.any { it.id == android.R.id.accessibilityActionSetProgress }) lista += t.getString(R.string.accion_poner_valor) to { ponerValor(n) }
		if (lector.scriptsListos) {
			if (lector.scripts.regla(n)?.agrupar == true) {
				for (hijo in controlesDentro(n)) lista += lector.textoElemento(hijo) to { runCatching { hijo.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false) }
			}
			for ((id, i, nombre) in lector.scripts.acciones(n.packageName?.toString().orEmpty(), lector.elemento(n))) {
				lista += nombre to { lector.scripts.hacerAccion(id, i); true }
			}
		}
		return lista
	}

	/** Cambia la falta [f] de [campo] por [sugerencia] (si lo escrito no ha cambiado desde entonces) y deja el cursor tras ella. */
	private fun corregir(campo: AccessibilityNodeInfo, f: Nodos.Falta, sugerencia: String): Boolean {
		runCatching { campo.refresh() }
		val texto = campo.text?.toString() ?: return false
		if (f.fin > texto.length || texto.substring(f.inicio, f.fin) != f.palabra) {
			lector.sonidos.tocar(Sonido.ERROR)
			return false
		}
		val nuevo = texto.replaceRange(f.inicio, f.fin, sugerencia)
		val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, nuevo) }
		if (!runCatching { campo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)) {
			lector.sonidos.tocar(Sonido.ERROR)
			return false
		}
		val cursor = f.inicio + sugerencia.length
		campo.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, android.os.Bundle().apply {
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
			putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
		})
		lector.decir(t.getString(R.string.acc_corregido, sugerencia))
		return true
	}

	/** Lleva el cursor a la falta [f] (seleccionada, para que se pueda escribir encima). */
	private fun irAFalta(campo: AccessibilityNodeInfo, f: Nodos.Falta): Boolean {
		val ok = runCatching {
			campo.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, android.os.Bundle().apply {
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, f.inicio)
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, f.fin)
			})
		}.getOrDefault(false)
		if (ok) lector.decir(f.palabra) else lector.sonidos.tocar(Sonido.ERROR)
		return ok
	}

	/** Una vista cualquiera: el enlace de otra app la ignora, pero el método la pide. */
	private val vistaVacia by lazy { android.view.View(servicio) }

	/**
	 * Pulsa un enlace de dentro de un texto. Android lo da como un ClickableSpan
	 * que, al pulsarlo, pide a la app que haga el clic en ese trozo (Android 8 o
	 * posterior); si no responde, un toque de verdad en el texto no serviría, así
	 * que se avisa con el sonido de error.
	 */
	private fun pulsarEnlace(enlace: android.text.style.ClickableSpan): Boolean = try {
		enlace.onClick(vistaVacia)
		true
	} catch (e: RuntimeException) {
		com.xebolax.tiresias.base.Registro.w(ETIQUETA, "No se pudo pulsar el enlace del texto", e)
		lector.sonidos.tocar(Sonido.ERROR)
		false
	}

	/** Los controles pulsables de dentro de [n] (para un elemento agrupado). */
	private fun controlesDentro(n: AccessibilityNodeInfo, nivel: Int = 0, lista: MutableList<AccessibilityNodeInfo> = ArrayList()): List<AccessibilityNodeInfo> {
		if (nivel > 8 || lista.size >= 20) return lista
		for (i in 0 until n.childCount) {
			val h = runCatching { n.getChild(i) }.getOrNull() ?: continue
			if (h.isVisibleToUser && Nodos.accionable(h)) lista += h else controlesDentro(h, nivel + 1, lista)
		}
		return lista
	}

	/** El cambio de foco en el que se eligió [accionElegida]: con otro foco ya no vale. */
	private var accionEnFoco = -1

	private fun elegirAccion(abajo: Boolean): Boolean {
		val f = objetivo() ?: return false
		val acciones = opcionesDe(f)
		if (acciones.isEmpty()) {
			lector.sonidos.tocar(Sonido.BORDE)
			lector.decir(R.string.acc_ninguna)
			return false
		}
		elegirEntre(acciones, abajo)
		return true
	}

	/**
	 * Con el control predeterminado: si el elemento tiene acciones propias (las
	 * de la app, las de una regla que lo agrupa o las de un complemento), arriba y
	 * abajo las eligen. Null si no tiene o se ha quitado en Ajustes: se navega.
	 */
	private fun elegirAccionSiHay(abajo: Boolean): Boolean? {
		if (!lector.ajustes.accionesArribaAbajo) return null
		val f = objetivo() ?: return null
		val acciones = opcionesDe(f)
		val basicas = accionesDe(f).count { Describir.accionesPropias(listOf(it)) == 0 }
		if (acciones.size <= basicas) return null
		elegirEntre(acciones, abajo)
		return true
	}

	/** Pasa a la acción siguiente o anterior de [acciones] y la dice (empieza de cero al cambiar el foco). */
	private fun elegirEntre(acciones: List<Pair<String, () -> Boolean>>, abajo: Boolean) {
		lector.cancelarPista()
		if (accionEnFoco != lector.cambiosFoco || accionElegida >= acciones.size) accionElegida = -1
		accionEnFoco = lector.cambiosFoco
		val n = acciones.size
		accionElegida = if (accionElegida < 0) (if (abajo) 0 else n - 1) else (accionElegida + if (abajo) 1 else n - 1) % n
		lector.sonidos.tocar(Sonido.SELECTOR)
		lector.decir(acciones[accionElegida].first)
	}

	/** La acción elegida con arriba y abajo para [f], si se eligió en este mismo foco. */
	private fun accionElegidaDe(f: AccessibilityNodeInfo): Pair<String, () -> Boolean>? {
		if (accionElegida < 0 || accionEnFoco != lector.cambiosFoco) return null
		return opcionesDe(f).getOrNull(accionElegida)
	}

	private val edicionOpciones = listOf(
		R.string.acc_seleccionar_todo to Accion.SELECCIONAR_TODO,
		R.string.acc_copiar to Accion.COPIAR,
		R.string.acc_cortar to Accion.CORTAR,
		R.string.acc_pegar to Accion.PEGAR,
		R.string.acc_cursor_inicio to Accion.CURSOR_INICIO,
		R.string.acc_cursor_final to Accion.CURSOR_FINAL,
		R.string.acc_modo_seleccion to Accion.MODO_SELECCION,
	)

	/** La opción de edición elegida con arriba y abajo (con el control Edición). */
	private var edicionElegida = -1

	private fun elegirEdicion(abajo: Boolean): Boolean {
		val n = edicionOpciones.size
		edicionElegida = if (edicionElegida < 0) (if (abajo) 0 else n - 1) else (edicionElegida + if (abajo) 1 else n - 1) % n
		lector.sonidos.tocar(Sonido.SELECTOR)
		lector.decir(edicionOpciones[edicionElegida].first)
		return true
	}

	private fun hacerEdicionElegida(): Boolean = edicionOpciones.getOrNull(edicionElegida)?.let { ejecutar(it.second) } ?: false

	// --- Menús ----------------------------------------------------------------------------------

	/** Vuelve a poner el foco donde estaba al cerrar una ventana de Tiresias. */
	private fun devolverFoco(previo: AccessibilityNodeInfo?): () -> Unit = {
		lector.manejador.postDelayed({ previo?.takeIf { Nodos.vivo(it) }?.let { lector.enfocar(it, null) } }, 250)
	}

	/** El menú de Tiresias como lo ha dejado el usuario (Ajustes, Navegación, «Elegir el menú de Tiresias»). */
	private val accionesMenu: List<Accion> get() = lector.ajustes.menuTiresias

	private fun menuTiresias(): Boolean {
		val previo = lector.foco
		val acciones = accionesMenu
		val nombres = acciones.map { nombreAccion(it) }
		// Lo último dicho antes de abrir el menú (repetir, deletrear o copiar es eso, no la opción del menú)
		val dichoAntes = lector.voz.ultimo
		ventanas.lista(t.getString(R.string.menu_tiresias), nombres, devolverFoco(previo)) { i ->
			// Se vuelve en silencio al elemento de partida: la acción (leer desde aquí, etiquetar...) es sobre él
			lector.manejador.postDelayed({
				previo?.takeIf { Nodos.vivo(it) }?.let { lector.enfocarSinHablar(it) }
				dichoAntesDelMenu = dichoAntes
				try {
					ejecutar(acciones[i])
				} finally {
					dichoAntesDelMenu = null
				}
			}, 300)
		}
		return true
	}

	/** Lo dicho antes de abrir el menú de Tiresias, mientras se hace la opción elegida en él. */
	private var dichoAntesDelMenu: String? = null

	/** Lo último que se ha dicho (si la acción llega del menú, lo de antes de abrirlo). */
	private fun ultimoDicho(): String? = dichoAntesDelMenu ?: lector.voz.ultimo

	// --- Lo copiado y copiar una lista --------------------------------------------------------

	/** Lo último que copió Tiresias: elegir uno para copiarlo otra vez, pegarlo en el campo, oírlo entero o quitarlo. */
	private fun historialCopiado(): Boolean {
		val lista = lector.edicion.copiado()
		if (lista.isEmpty()) {
			lector.decir(R.string.portapapeles_vacio_historial)
			return false
		}
		val previo = lector.foco
		val nombres = lista.map { it.replace('\n', ' ').let { s -> if (s.length > 90) s.take(90) + "…" else s } }
		ventanas.lista(t.getString(R.string.accion_historial_portapapeles), nombres, devolverFoco(previo)) { i ->
			val texto = lista[i]
			val opciones = listOf(
				R.string.portapapeles_copiar_otra_vez, R.string.portapapeles_pegar, R.string.portapapeles_decir, R.string.portapapeles_quitar,
			)
			lector.manejador.postDelayed({
				ventanas.lista(nombres[i], opciones.map { t.getString(it) }, devolverFoco(previo)) { j ->
					lector.manejador.postDelayed({
						when (opciones[j]) {
							R.string.portapapeles_copiar_otra_vez -> {
								lector.edicion.portapapeles(texto)
								lector.decir(R.string.copiado)
							}
							R.string.portapapeles_pegar -> {
								previo?.takeIf { Nodos.vivo(it) }?.let { lector.enfocarSinHablar(it) }
								lector.edicion.portapapeles(texto)
								if (!lector.edicion.pegar()) lector.decir(R.string.copiado)
							}
							R.string.portapapeles_decir -> lector.decir(texto)
							else -> lector.edicion.olvidarCopiado(texto)
						}
					}, 300)
				}
			}, 300)
		}
		return true
	}

	@Volatile private var copiaLista: Runnable? = null

	/**
	 * Copia todos los elementos de la lista enfocada (o la primera de la
	 * pantalla), uno por línea (Jieshuo): la sube hasta arriba y la recorre
	 * página a página hasta abajo, apuntando cada elemento una vez (por su
	 * posición en la lista, si la app la da). Se para si se toca la pantalla.
	 */
	private fun copiarLista(): Boolean {
		val raiz = lector.raiz() ?: return false
		val lista = lector.foco?.let { Nodos.desplazable(it) } ?: Nodos.primerDesplazable(raiz) ?: run {
			lector.decir(R.string.lista_sin_lista)
			return false
		}
		val textos = LinkedHashMap<String, String>()
		val tocado = lector.toques
		var subiendo = true
		var paginas = 0
		lector.sonidos.tocar(Sonido.CARGANDO)
		lector.decir(R.string.lista_copiando)
		fun recoger() {
			nav.invalidarTodo()
			val r = lector.raiz() ?: return
			for (n in nav.orden(r)) {
				if (!Nodos.dentroDe(n, lista)) continue
				val e = lector.elemento(n)
				val texto = listOf(e.nombre, e.texto).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
				if (texto.isEmpty()) continue
				textos.putIfAbsent(nav.filaDe(n)?.toString() ?: texto, texto)
			}
		}
		val paso = object : Runnable {
			override fun run() {
				if (copiaLista !== this) return
				if (lector.toques != tocado) {
					copiaLista = null
					nav.sinRehacer = false
					return
				}
				runCatching { lista.refresh() }
				if (!subiendo) recoger()
				val accion = if (subiendo) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
				if (paginas < MAXIMO_PAGINAS_LISTA && lista.actionList.any { it.id == accion } && runCatching { lista.performAction(accion) }.getOrDefault(false)) {
					paginas++
					lector.manejador.postDelayed(this, 150)
					return
				}
				if (subiendo) {
					// Arriba del todo: ahora se recoge bajando
					subiendo = false
					lector.manejador.postDelayed(this, 200)
					return
				}
				copiaLista = null
				nav.sinRehacer = false
				if (textos.isEmpty()) {
					lector.sonidos.tocar(Sonido.BORDE)
					lector.decir(R.string.lista_sin_lista)
					return
				}
				lector.edicion.portapapeles(textos.values.joinToString("\n"))
				lector.sonidos.tocar(Sonido.EDICION)
				lector.decir(t.resources.getQuantityString(R.plurals.lista_copiada, textos.size, textos.size))
			}
		}
		copiaLista = paso
		nav.sinRehacer = true
		lector.manejador.post(paso)
		return true
	}

	// --- Modo senior -----------------------------------------------------------------------------

	/**
	 * Siempre a un sitio conocido (BlindShell: mantener Atrás): cierra lo que
	 * haya abierto de Tiresias, quita la pantalla virtual, la ayuda de otra
	 * persona y el toque directo, va a la pantalla de inicio y dice dónde está y
	 * la hora.
	 */
	private fun inicioSeguro(): Boolean {
		ventanas.cerrar()
		lector.salirVirtual(false)
		lector.gestos.restablecer()
		lector.continua.parar()
		cancelarSos()
		servicio.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
		lector.sonidos.tocar(Sonido.VENTANA)
		val hora = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
		lector.manejador.postDelayed({ lector.decir(t.getString(R.string.senior_inicio, hora)) }, 400)
		return true
	}

	/** Qué se puede hacer con lo que hay delante: cómo se activa, sus acciones y cómo volver, en pocas palabras. */
	private fun quePuedoHacer(): Boolean {
		val f = objetivo()
		if (f == null) {
			lector.decir(R.string.senior_nada_delante)
			return true
		}
		val e = lector.elemento(f)
		val partes = ArrayList<String>()
		partes += t.getString(R.string.senior_estas_en, lector.textoElemento(f))
		when {
			e.editable -> partes += t.getString(R.string.senior_escribir)
			e.marcado != com.xebolax.tiresias.nucleo.Marcado.NO_MARCABLE && e.pulsable -> partes += t.getString(R.string.senior_cambiar)
			e.pulsable -> partes += t.getString(R.string.senior_activar)
		}
		if (f.isLongClickable) partes += t.getString(R.string.senior_mantener)
		partes += t.getString(R.string.senior_moverse)
		lector.decir(partes.joinToString(". ") + ".")
		return true
	}

	/** La cuenta atrás de la llamada de ayuda, si está en marcha. */
	@Volatile private var cuentaSos: Runnable? = null

	private fun cancelarSos(): Boolean {
		val c = cuentaSos ?: return false
		lector.manejador.removeCallbacks(c)
		cuentaSos = null
		lector.decir(R.string.senior_sos_cancelado)
		return true
	}

	/**
	 * Pedir ayuda: cinco segundos de cuenta atrás, dicha, que se cancelan tocando
	 * la pantalla, con cualquier acción o pidiendo SOS otra vez; después, una
	 * llamada al contacto de ayuda (con el marcador si Android no deja llamar).
	 */
	private fun sos(): Boolean {
		if (cancelarSos()) return true
		val a = lector.ajustes
		val numero = a.seniorContactoAyuda.filter { it.isDigit() || it == '+' }
		if (numero.isEmpty()) {
			lector.decir(R.string.senior_sos_sin_contacto)
			return false
		}
		val quien = a.seniorNombreAyuda.ifEmpty { numero }
		val tocado = lector.toques
		var quedan = 5
		val paso = object : Runnable {
			override fun run() {
				if (cuentaSos !== this) return
				if (lector.toques != tocado) {
					cancelarSos()
					return
				}
				if (quedan > 0) {
					lector.sonidos.tocar(Sonido.BORDE)
					lector.decir(quedan.toString())
					quedan--
					lector.manejador.postDelayed(this, 1000)
					return
				}
				cuentaSos = null
				lector.decir(t.getString(R.string.senior_sos_llamando, quien))
				llamarAyuda(numero)
			}
		}
		cuentaSos = paso
		lector.sonidos.tocar(Sonido.ERROR)
		lector.decir(t.getString(R.string.senior_sos_aviso, quien))
		lector.manejador.postDelayed(paso, 3500)
		return true
	}

	@android.annotation.SuppressLint("MissingPermission")
	private fun llamarAyuda(numero: String) {
		val uri = android.net.Uri.parse("tel:$numero")
		val puede = servicio.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
		val intent = android.content.Intent(if (puede) android.content.Intent.ACTION_CALL else android.content.Intent.ACTION_DIAL, uri)
			.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
		try {
			servicio.startActivity(intent)
		} catch (e: android.content.ActivityNotFoundException) {
			com.xebolax.tiresias.base.Registro.w(ETIQUETA, "No hay app de teléfono para pedir ayuda", e)
			lector.decir(R.string.senior_sos_sin_telefono)
		}
	}

	// --- Ir a una posición de la lista ---------------------------------------------------------

	/** La lista (o lo que se desplaza) en la que está [n]. */
	private fun listaDe(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
		generateSequence(n) { runCatching { it.parent }.getOrNull() }.take(30).firstOrNull { it.collectionInfo != null || it.isScrollable }

	/** Va a la fila [fila] (desde 0) de la lista del foco, sin pedir el número (para las pruebas). */
	fun irALaFila(fila: Int) {
		listaDe(objetivo())?.let { irAFila(it, fila, 0) }
	}

	/** Pide un número y va a ese elemento de la lista, desplazándola si hace falta. */
	private fun irAPosicion(): Boolean {
		val lista = listaDe(objetivo())
		if (lista == null) {
			lector.decir(R.string.posicion_sin_lista)
			return false
		}
		val total = lista.collectionInfo?.rowCount?.takeIf { it > 0 } ?: 0
		val pista = if (total > 0) t.getString(R.string.posicion_pista_de, total) else t.getString(R.string.posicion_pista)
		ventanas.pedirTexto(t.getString(R.string.posicion_titulo), pista) { texto ->
			val n = texto.trim().toIntOrNull()
			if (n == null || n < 1 || (total > 0 && n > total)) {
				lector.decir(R.string.posicion_mal)
				return@pedirTexto
			}
			lector.manejador.post { irAFila(lista, n - 1, 0) }
		}
		return true
	}

	/** El elemento de la fila [fila] que se ve ahora en [lista] (por su posición en la colección o, si no la tiene, por su orden). */
	private fun filaVisible(lista: AccessibilityNodeInfo, fila: Int): AccessibilityNodeInfo? {
		if (!Nodos.vivo(lista)) return null
		val hijos = (0 until lista.childCount).mapNotNull { runCatching { lista.getChild(it) }.getOrNull() }
		if (hijos.any { it.collectionItemInfo != null }) return hijos.firstOrNull { it.collectionItemInfo?.rowIndex == fila }
		// Sin posiciones: solo vale si la lista está arriba del todo
		return if (!lista.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)) hijos.getOrNull(fila) else null
	}

	private fun filasVisibles(lista: AccessibilityNodeInfo): IntRange? {
		val filas = (0 until lista.childCount).mapNotNull { runCatching { lista.getChild(it)?.collectionItemInfo?.rowIndex }.getOrNull() }
		return if (filas.isEmpty()) null else filas.min()..filas.max()
	}

	/** Va a [fila]: si se ve, la enfoca; si no, pide a Android que desplace hasta ella o desplaza página a página. */
	private fun irAFila(lista: AccessibilityNodeInfo, fila: Int, intento: Int) {
		filaVisible(lista, fila)?.let {
			lector.enfocar(Nodos.enfocable(it) ?: it)
			return
		}
		if (intento >= MAXIMO_PAGINAS_POSICION) {
			lector.decir(R.string.posicion_no_llega)
			return
		}
		val hecho = if (intento == 0) {
			val args = android.os.Bundle().apply {
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, fila)
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
			}
			lista.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id, args)
		} else false
		if (!hecho) {
			val visibles = filasVisibles(lista)
			val adelante = visibles == null || fila > visibles.last
			if (!lista.performAction(if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
				lector.decir(R.string.posicion_no_llega)
				return
			}
		}
		lector.manejador.postDelayed({ irAFila(lista, fila, intento + 1) }, 400)
	}

	// --- Música, cursor y deslizadores ---------------------------------------------------------

	/** Una tecla multimedia (canción siguiente o anterior) para la app que suena. */
	private fun teclaMultimedia(codigo: Int): Boolean {
		val audio = servicio.getSystemService(android.media.AudioManager::class.java) ?: return false
		val ahora = SystemClock.uptimeMillis()
		audio.dispatchMediaKeyEvent(KeyEvent(ahora, ahora, KeyEvent.ACTION_DOWN, codigo, 0))
		audio.dispatchMediaKeyEvent(KeyEvent(ahora, ahora, KeyEvent.ACTION_UP, codigo, 0))
		lector.sonidos.tocar(Sonido.ACTIVAR)
		return true
	}

	/** Dónde está el cursor en el campo: el carácter (o lo seleccionado) y la línea, de cuántos. */
	private fun posicionCursor(): Boolean {
		val campo = lector.raiz()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: objetivo()?.takeIf { it.isEditable }
		if (campo == null) {
			lector.decir(R.string.cursor_sin_campo)
			return false
		}
		runCatching { campo.refresh() }
		val texto = campo.text?.toString().orEmpty()
		if ((Build.VERSION.SDK_INT >= 26 && campo.isShowingHintText) || texto.isEmpty()) {
			lector.decir(R.string.cursor_vacio)
			return true
		}
		val inicio = campo.textSelectionStart.coerceIn(0, texto.length)
		val fin = campo.textSelectionEnd.coerceIn(0, texto.length)
		val linea = texto.substring(0, minOf(inicio, fin)).count { it == '\n' } + 1
		val lineas = texto.count { it == '\n' } + 1
		val donde = if (inicio != fin) t.getString(R.string.cursor_seleccion, minOf(inicio, fin) + 1, maxOf(inicio, fin), texto.length)
		else t.getString(R.string.cursor_caracter, inicio + 1, texto.length + 1)
		lector.decir(if (lineas > 1) t.getString(R.string.cursor_con_linea, donde, linea, lineas) else donde)
		return true
	}

	/** Un valor exacto en un deslizador (entre su mínimo y su máximo), escrito, en vez de ir paso a paso. */
	private fun ponerValor(n: AccessibilityNodeInfo?): Boolean {
		val r = n?.rangeInfo
		if (n == null || r == null || !n.actionList.any { it.id == android.R.id.accessibilityActionSetProgress }) {
			lector.decir(R.string.valor_sin_deslizador)
			return false
		}
		val min = r.min
		val max = r.max
		fun numero(v: Float): String = if (v == Math.round(v).toFloat()) Math.round(v).toString() else "%.2f".format(v)
		ventanas.pedirTexto(t.getString(R.string.valor_titulo), t.getString(R.string.valor_pista, numero(min), numero(max)), numero(r.current)) { texto ->
			val v = texto.trim().replace(',', '.').toFloatOrNull()
			if (v == null || v < min || v > max) {
				lector.decir(t.getString(R.string.valor_fuera, numero(min), numero(max)))
				return@pedirTexto
			}
			lector.manejador.post {
				val args = android.os.Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, v) }
				if (!n.performAction(android.R.id.accessibilityActionSetProgress, args)) lector.sonidos.tocar(Sonido.ERROR)
				else lector.decir(t.getString(R.string.valor_puesto, numero(v)))
			}
		}
		return true
	}

	// --- Formato, marcas y tablas ----------------------------------------------------------------

	/** El formato del texto de [n] (negrita, cursiva, subrayado, tachado, enlaces), por trozos (Orca 51). */
	private fun formatoTexto(n: AccessibilityNodeInfo?): Boolean {
		val texto = n?.text as? android.text.Spanned
		if (texto == null || texto.isEmpty()) {
			lector.decir(R.string.formato_sin_texto)
			return false
		}
		val partes = describirFormato(texto)
		lector.decir(if (partes.isEmpty()) t.getString(R.string.formato_ninguno) else partes.joinToString(". ") + ".")
		return true
	}

	/** Los trozos con formato de [texto] dichos («Negrita: hola»), sin repetir, como mucho ocho. */
	fun describirFormato(texto: android.text.Spanned): List<String> {
		val partes = ArrayList<String>()
		for (sp in texto.getSpans(0, texto.length, Any::class.java)) {
			val inicio = texto.getSpanStart(sp).coerceAtLeast(0)
			val fin = texto.getSpanEnd(sp).coerceAtMost(texto.length)
			if (fin <= inicio) continue
			val trozo = texto.subSequence(inicio, fin).toString().trim().take(60)
			if (trozo.isEmpty()) continue
			val tipo = when (sp) {
				is android.text.style.StyleSpan -> when (sp.style) {
					android.graphics.Typeface.BOLD -> R.string.formato_negrita
					android.graphics.Typeface.ITALIC -> R.string.formato_cursiva
					android.graphics.Typeface.BOLD_ITALIC -> R.string.formato_negrita_cursiva
					else -> null
				}
				is android.text.style.UnderlineSpan -> R.string.formato_subrayado
				is android.text.style.StrikethroughSpan -> R.string.formato_tachado
				is android.text.style.URLSpan, is android.text.style.ClickableSpan -> R.string.formato_enlace
				is android.text.style.SuperscriptSpan -> R.string.formato_superindice
				is android.text.style.SubscriptSpan -> R.string.formato_subindice
				else -> null
			} ?: continue
			partes += t.getString(tipo, trozo)
		}
		return partes.distinct().take(8)
	}

	/**
	 * El texto de lo enfocado (y de lo que tiene dentro: un mensaje, un artículo)
	 * en una ventana para elegir un trozo y copiarlo, como en un campo. Lo más
	 * pedido a los lectores de Android: copiar texto que no es editable.
	 */
	private fun seleccionarTexto(): Boolean {
		val f = objetivo()
		if (f == null) {
			lector.decir(R.string.elegir_nada)
			return false
		}
		if (f.isEditable) {
			lector.decir(R.string.elegir_es_campo)
			return false
		}
		if (Nodos.sensible(f)) {
			lector.decir(R.string.vision_sensible)
			return false
		}
		val partes = ArrayList<String>()
		val cola = ArrayDeque<AccessibilityNodeInfo>()
		cola.add(f)
		var vistos = 0
		var largo = 0
		while (cola.isNotEmpty() && vistos++ < 600 && largo < 20_000) {
			val n = cola.removeFirst()
			if (!n.isVisibleToUser || Nodos.sensible(n)) continue
			(n.text ?: n.contentDescription)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it !in partes }?.let {
				partes += it
				largo += it.length
			}
			for (i in 0 until n.childCount) runCatching { n.getChild(i) }.getOrNull()?.let { cola.add(it) }
		}
		val texto = partes.joinToString("\n").take(20_000)
		if (texto.isBlank()) {
			lector.decir(R.string.elegir_nada)
			return false
		}
		ventanas.textoParaElegir(t.getString(R.string.elegir_titulo), texto) { elegido ->
			lector.manejador.post {
				lector.edicion.portapapeles(elegido)
				lector.decir(t.getString(R.string.elegir_copiado, elegido.length))
			}
		}
		return true
	}

	/** Un nombre propio para la pantalla de delante; vacío lo quita. */
	private fun nombrarVentana(): Boolean {
		val paquete = lector.raiz()?.packageName?.toString()
		val clase = lector.actividadDelante
		if (paquete == null || clase == null) {
			lector.decir(R.string.nombre_ventana_no)
			return false
		}
		val clave = "$paquete/$clase"
		val a = lector.ajustes
		val nombres = runCatching { org.json.JSONObject(a.p.getString(com.xebolax.tiresias.base.Claves.NOMBRES_VENTANA, "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
		ventanas.pedirTexto(t.getString(R.string.nombre_ventana_titulo), t.getString(R.string.nombre_ventana_pista), nombres.optString(clave, lector.tituloActivo())) { nombre ->
			if (nombre.isBlank()) nombres.remove(clave) else nombres.put(clave, nombre.trim())
			a.p.edit().putString(com.xebolax.tiresias.base.Claves.NOMBRES_VENTANA, nombres.toString()).apply()
			lector.decir(if (nombre.isBlank()) t.getString(R.string.nombre_ventana_quitado) else t.getString(R.string.nombre_ventana_puesto, nombre.trim()))
		}
		return true
	}

	/** Dónde se guardan las marcas: la app y la pantalla de delante. */
	private fun claveMarca(): String = (lector.raiz()?.packageName?.toString().orEmpty()) + "/" + lector.actividadDelante.orEmpty()

	/** Una marca de posición en esta pantalla (JAWS): se vuelve a ella con «Ir a la marca». */
	private fun ponerMarca(): Boolean {
		val f = objetivo() ?: run { lector.decir(R.string.marca_nada); return false }
		val a = lector.ajustes
		val marcas = runCatching { org.json.JSONObject(a.p.getString(com.xebolax.tiresias.base.Claves.MARCAS, "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
		marcas.put(claveMarca(), org.json.JSONObject().put("id", f.viewIdResourceName.orEmpty()).put("texto", lector.textoElemento(f)).put("nombre", lector.elemento(f).nombre))
		a.p.edit().putString(com.xebolax.tiresias.base.Claves.MARCAS, marcas.toString()).apply()
		lector.sonidos.tocar(Sonido.ACTIVAR)
		lector.decir(t.getString(R.string.marca_puesta, lector.elemento(f).nombre.ifBlank { lector.textoElemento(f) }))
		return true
	}

	private fun irAMarca(): Boolean {
		val marcas = runCatching { org.json.JSONObject(lector.ajustes.p.getString(com.xebolax.tiresias.base.Claves.MARCAS, "{}") ?: "{}") }.getOrNull()
		val m = marcas?.optJSONObject(claveMarca())
		if (m == null) {
			lector.decir(R.string.marca_no_hay)
			return false
		}
		val raiz = lector.raiz() ?: return false
		val id = m.optString("id")
		val nombre = m.optString("nombre")
		val candidatos = if (id.isNotEmpty()) raiz.findAccessibilityNodeInfosByViewId(id) else emptyList()
		val destino = candidatos.firstOrNull { nombre.isEmpty() || lector.elemento(it).nombre == nombre } ?: candidatos.firstOrNull()
			?: nombre.takeIf { it.isNotEmpty() }?.let { raiz.findAccessibilityNodeInfosByText(it).firstOrNull { n -> n.isVisibleToUser } }
		if (destino == null) {
			lector.decir(R.string.marca_no_esta)
			return false
		}
		lector.enfocar(Nodos.enfocable(destino) ?: destino)
		return true
	}

	/** La fila o la columna entera de la celda enfocada, en orden, de lo que se ve de la tabla. */
	private fun leerLinea(fila: Boolean): Boolean {
		val celda = objetivo()
		val info = celda?.collectionItemInfo
		val tabla = generateSequence(celda) { runCatching { it.parent }.getOrNull() }.take(8).firstOrNull { it.collectionInfo != null }
		if (celda == null || info == null || tabla == null) {
			lector.decir(R.string.tabla_sin_celda)
			return false
		}
		val celdas = ArrayList<Pair<Int, String>>()
		val cola = ArrayDeque<AccessibilityNodeInfo>()
		cola.add(tabla)
		var vistos = 0
		while (cola.isNotEmpty() && vistos++ < 800) {
			val n = cola.removeFirst()
			val i = n.collectionItemInfo
			if (i != null && n != tabla) {
				if (if (fila) i.rowIndex == info.rowIndex else i.columnIndex == info.columnIndex) {
					celdas += (if (fila) i.columnIndex else i.rowIndex) to lector.textoElemento(n)
					continue
				}
			}
			for (k in 0 until n.childCount) runCatching { n.getChild(k) }.getOrNull()?.let { cola.add(it) }
		}
		if (celdas.isEmpty()) {
			lector.decir(R.string.tabla_sin_celda)
			return false
		}
		lector.decir(celdas.sortedBy { it.first }.distinctBy { it.first }.joinToString(". ") { it.second })
		return true
	}

	// --- El volumen de la voz -------------------------------------------------------------------

	private fun canalVoz(): Boolean {
		val a = lector.ajustes
		val multimedia = !a.vozMultimedia
		a.p.edit().putBoolean(com.xebolax.tiresias.base.Claves.VOZ_MULTIMEDIA, multimedia).apply()
		lector.cambiarCanal(multimedia)
		lector.decir(if (multimedia) R.string.canal_multimedia else R.string.canal_accesibilidad)
		return true
	}

	// --- Resumen de los avisos -------------------------------------------------------------------

	/**
	 * Los avisos de las últimas horas en una frase, por app y por quién
	 * («Tienes 5 avisos. WhatsApp: 3, de Ana y Juan. Gmail: 2»), sin los fijos ni
	 * los de progreso. Para no tener que recorrer el historial entero.
	 */
	private fun resumenAvisos(): Boolean {
		val desde = System.currentTimeMillis() - HORAS_RESUMEN * 3_600_000L
		val avisos = lector.avisos.historial().filter { a ->
			a.momento >= desde && a.notificacion?.let { it.flags and android.app.Notification.FLAG_ONGOING_EVENT == 0 } != false
		}
		if (avisos.isEmpty()) {
			lector.decir(t.getString(R.string.resumen_ninguno, HORAS_RESUMEN))
			return true
		}
		val partes = ArrayList<String>()
		partes += t.resources.getQuantityString(R.plurals.resumen_avisos, avisos.size, avisos.size, HORAS_RESUMEN)
		for ((app, deApp) in avisos.groupBy { it.app }.entries.sortedByDescending { it.value.size }.take(6)) {
			val quienes = deApp.map { it.titulo }.filter { it.isNotBlank() && it != app }.distinct()
			partes += if (quienes.isEmpty() || quienes.size > 3) t.getString(R.string.resumen_app, app, deApp.size)
			else t.getString(R.string.resumen_app_de, app, deApp.size, enumerar(quienes))
		}
		lector.decir(partes.joinToString(". ") + ".")
		return true
	}

	/** «Ana», «Ana y Juan», «Ana, Juan y Luis». */
	private fun enumerar(nombres: List<String>): String =
		if (nombres.size < 2) nombres.joinToString() else nombres.dropLast(1).joinToString(", ") + t.getString(R.string.resumen_y) + nombres.last()

	// --- Pantalla virtual -----------------------------------------------------------------------

	/**
	 * Con la pantalla virtual puesta, moverse, activar y Atrás son sobre lo
	 * reconocido; null: la acción se hace como siempre (y la pantalla virtual se
	 * quita si lleva a otra pantalla).
	 */
	private fun enVirtual(v: Lector.PantallaVirtual, a: Accion): Boolean? = when (a) {
		Accion.SIGUIENTE -> lector.moverVirtual(1)
		Accion.ANTERIOR -> lector.moverVirtual(-1)
		Accion.PRIMERO -> lector.moverVirtual(Int.MIN_VALUE)
		Accion.ULTIMO -> lector.moverVirtual(Int.MAX_VALUE)
		Accion.REPETIR -> lector.moverVirtual(0)
		Accion.ACTIVAR, Accion.MANTENER -> v.elementos.getOrNull(v.actual)?.let { e ->
			lector.salirVirtual(false)
			lector.sonidos.tocar(Sonido.ACTIVAR)
			tocarPunto(e.second.exactCenterX(), e.second.exactCenterY(), a == Accion.MANTENER)
		} ?: false
		Accion.ATRAS, Accion.PANTALLA_VIRTUAL -> true.also { lector.salirVirtual(true) }
		Accion.SELECTOR_SIGUIENTE, Accion.SELECTOR_ANTERIOR, Accion.CONTROL_SIGUIENTE, Accion.CONTROL_ANTERIOR -> true.also { lector.sonidos.tocar(Sonido.BORDE) }
		else -> {
			lector.salirVirtual(false)
			null
		}
	}

	/** Un toque de verdad (o una pulsación larga) en un punto de la pantalla. */
	private fun tocarPunto(x: Float, y: Float, largo: Boolean): Boolean {
		val camino = Path().apply { moveTo(x, y) }
		val gesto = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(camino, 0, if (largo) 800 else 60)).build()
		return lector.mandarGesto(gesto)
	}

	private fun menuAcciones(): Boolean {
		val f = lector.foco ?: return false
		val acciones = opcionesDe(f, delMenu = true)
		if (acciones.isEmpty()) {
			lector.decir(R.string.acc_ninguna)
			return false
		}
		ventanas.lista(t.getString(R.string.menu_acciones), acciones.map { it.first }, devolverFoco(f)) { i ->
			lector.manejador.postDelayed({ acciones[i].second() }, 300)
		}
		return true
	}

	private fun menuEdicion(): Boolean {
		val previo = lector.foco
		ventanas.lista(t.getString(R.string.menu_edicion), edicionOpciones.map { t.getString(it.first) }, devolverFoco(previo)) { i ->
			lector.manejador.postDelayed({
				previo?.takeIf { Nodos.vivo(it) }?.let { lector.enfocarSinHablar(it) }
				ejecutar(edicionOpciones[i].second)
			}, 350)
		}
		return true
	}

	/** Lista de elementos de la pantalla, como en NVDA: primero se elige el tipo, luego el elemento. */
	private fun listaElementos(): Boolean {
		val raiz = lector.raiz() ?: return false
		val previo = lector.foco
		val tipos = listOf(
			ControlLectura.PREDETERMINADO, ControlLectura.ENCABEZADOS, ControlLectura.ENLACES, ControlLectura.CONTROLES,
			ControlLectura.BOTONES, ControlLectura.CAMPOS, ControlLectura.CASILLAS, ControlLectura.IMAGENES,
		)
		val nombres = tipos.map { if (it == ControlLectura.PREDETERMINADO) t.getString(R.string.lista_todos) else nav.nombreControl(it) }
		ventanas.lista(t.getString(R.string.lista_elementos), nombres, devolverFoco(previo)) { i ->
			lector.manejador.post {
				val tipo = tipos[i]
				val nodos = nav.orden(raiz).filter { n ->
					when (tipo) {
						ControlLectura.ENCABEZADOS -> Nodos.esEncabezado(n)
						ControlLectura.ENLACES -> Nodos.rol(n) == com.xebolax.tiresias.nucleo.Rol.ENLACE || Nodos.tieneEnlacesEnTexto(n)
						ControlLectura.CONTROLES -> Nodos.accionable(n)
						ControlLectura.BOTONES -> Nodos.rol(n).name.startsWith("BOTON")
						ControlLectura.CAMPOS -> n.isEditable
						ControlLectura.CASILLAS -> n.isCheckable
						ControlLectura.IMAGENES -> Nodos.rol(n) == com.xebolax.tiresias.nucleo.Rol.IMAGEN
						else -> true
					}
				}
				if (nodos.isEmpty()) {
					lector.decir(t.getString(R.string.nav_no_hay, nav.nombreControl(tipo)))
					return@post
				}
				val textos = nodos.map { lector.compositor.describir(lector.elemento(it), lector.ajustes.verbosidad.copy(decirPistas = false)) }
				// La segunda lista lleva por título lo que se eligió («Encabezados», «Todos los elementos»)
				ventanas.lista(nombres[i], textos, devolverFoco(previo)) { j ->
					lector.manejador.postDelayed({ lector.enfocar(nodos[j]) }, 300)
				}
			}
		}
		return true
	}

	/** Buscar un texto en la pantalla (sin distinguir mayúsculas ni acentos). */
	private fun buscar(): Boolean {
		val raiz = lector.raiz() ?: return false
		val previo = lector.foco
		ventanas.pedirTexto(t.getString(R.string.buscar_titulo), t.getString(R.string.buscar_pista), alCancelar = devolverFoco(previo)) { buscado ->
			lector.manejador.post {
				val clave = normalizar(buscado)
				if (clave.isBlank()) return@post
				val nodos = nav.orden(raiz).filter { n ->
					val e = lector.elemento(n)
					normalizar(e.nombre + " " + e.texto).contains(clave)
				}
				if (nodos.isEmpty()) {
					lector.decir(t.getString(R.string.buscar_nada, buscado))
					previo?.let { lector.enfocar(it, null, Modo.ENCOLAR) }
					return@post
				}
				if (nodos.size == 1) {
					lector.enfocar(nodos.first())
					return@post
				}
				val textos = nodos.map { lector.compositor.describir(lector.elemento(it), lector.ajustes.verbosidad.copy(decirPistas = false)) }
				ventanas.lista(t.resources.getQuantityString(R.plurals.buscar_resultados, nodos.size, nodos.size), textos, devolverFoco(previo)) { j ->
					lector.manejador.postDelayed({ lector.enfocar(nodos[j]) }, 300)
				}
			}
		}
		return true
	}

	private fun normalizar(s: String): String =
		java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

	private fun historialHabla(): Boolean {
		val lista = lector.voz.historial()
		if (lista.isEmpty()) return false
		val previo = lector.foco
		ventanas.lista(t.getString(R.string.historial_habla), lista.take(50), devolverFoco(previo)) { i ->
			lector.edicion.portapapeles(lista[i])
			lector.decir(R.string.copiado, modo = Modo.ENCOLAR)
			devolverFoco(previo)()
		}
		return true
	}

	private fun historialAvisos(): Boolean {
		val avisos = lector.avisos.historial()
		if (avisos.isEmpty()) {
			lector.decir(R.string.historial_avisos_vacio)
			return false
		}
		val formato = DateFormat.getTimeInstance(DateFormat.SHORT)
		val textos = avisos.map { t.getString(R.string.historial_aviso, it.app, formato.format(Date(it.momento)), listOf(it.titulo, it.texto).filter { s -> s.isNotEmpty() }.joinToString(". ")) }
		val previo = lector.foco
		ventanas.lista(t.getString(R.string.historial_avisos), textos, devolverFoco(previo)) { i ->
			lector.manejador.postDelayed({ accionesAviso(avisos[i], textos[i], previo) }, 350)
		}
		return true
	}

	/** Lo que se puede hacer con una notificación del historial: abrirla, sus acciones (responder también) o copiarla. */
	private fun accionesAviso(a: com.xebolax.tiresias.lectura.Avisos.Aviso, texto: String, previo: AccessibilityNodeInfo?) {
		val n = a.notificacion
		val opciones = ArrayList<Pair<String, () -> Unit>>()
		n?.contentIntent?.let { pi -> opciones += t.getString(R.string.historial_aviso_abrir) to { enviar(pi, null) } }
		for (accion in n?.actions.orEmpty()) {
			val titulo = accion.title?.toString()?.takeIf { it.isNotBlank() } ?: continue
			val entrada = accion.remoteInputs?.firstOrNull { it.allowFreeFormInput }
			opciones += if (entrada != null) {
				t.getString(R.string.historial_aviso_responder, titulo) to {
					ventanas.pedirTexto(titulo, entrada.label?.toString() ?: titulo, alCancelar = devolverFoco(previo)) { respuesta ->
						if (respuesta.isNotBlank()) {
							val datos = android.content.Intent()
							android.app.RemoteInput.addResultsToIntent(accion.remoteInputs, datos, android.os.Bundle().apply { putCharSequence(entrada.resultKey, respuesta) })
							enviar(accion.actionIntent, datos)
						}
					}
				}
			} else titulo to { enviar(accion.actionIntent, null) }
		}
		opciones += t.getString(R.string.historial_aviso_copiar) to {
			lector.edicion.portapapeles(texto)
			lector.decir(R.string.copiado, modo = Modo.ENCOLAR)
			devolverFoco(previo)()
		}
		ventanas.lista(listOf(a.app, a.titulo).filter { it.isNotBlank() }.joinToString(": "), opciones.map { it.first }, devolverFoco(previo)) { i ->
			lector.manejador.postDelayed({ opciones[i].second() }, 300)
		}
	}

	/** Manda la intención de una notificación (abrirla o una acción) desde el lector. */
	private fun enviar(pi: android.app.PendingIntent?, datos: android.content.Intent?) {
		if (pi == null) return
		val opciones = if (Build.VERSION.SDK_INT >= 34) android.app.ActivityOptions.makeBasic()
			.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
		val ok = runCatching { pi.send(servicio, 0, datos, null, null, null, opciones) }
		if (ok.isFailure) {
			com.xebolax.tiresias.base.Registro.w(ETIQUETA, "No se pudo usar la notificación", ok.exceptionOrNull())
			lector.decir(R.string.historial_aviso_no_se_pudo)
		} else lector.sonidos.tocar(Sonido.ACTIVAR)
	}

	private fun etiquetar(): Boolean {
		val f = lector.foco ?: return false
		val id = f.viewIdResourceName
		val paquete = f.packageName?.toString()
		if (id == null || paquete == null) {
			lector.decir(R.string.etiqueta_sin_id)
			return false
		}
		// Lo que reconoció Tiresias en el icono, si no tiene etiqueta todavía
		val actual = lector.etiquetas.de(paquete, id) ?: lector.vision.iconos.sugerencia(f).orEmpty()
		ventanas.pedirTexto(t.getString(R.string.etiqueta_titulo), t.getString(R.string.etiqueta_pista), actual, devolverFoco(f)) { texto ->
			lector.manejador.post {
				lector.etiquetas.poner(paquete, id, texto)
				lector.olvidarCaches()
				lector.decir(if (texto.isBlank()) t.getString(R.string.etiqueta_quitada) else t.getString(R.string.etiqueta_puesta, texto), Modo.ENCOLAR)
				devolverFoco(f)()
			}
		}
		return true
	}

	// --- Voz y ajustes rápidos --------------------------------------------------------------------

	private fun pausar(): Boolean {
		val p = pausado
		if (lector.voz.hablando || lector.continua.activa) {
			// Desde la frase que sonaba, no desde el principio
			pausado = lector.voz.restante() ?: lector.voz.ultimo
			lector.continua.parar()
			lector.voz.callar()
		} else if (p != null) {
			pausado = null
			lector.voz.decir(Enunciado(p, preparar = false))
		}
		return true
	}

	private fun alternarVoz(): Boolean {
		val callar = !lector.voz.silenciada
		if (callar) {
			lector.voz.decir(Enunciado(t.getString(R.string.voz_apagada), tipo = Tipo.SISTEMA))
			lector.voz.silenciada = true
		} else {
			lector.voz.silenciada = false
			lector.decir(R.string.voz_encendida)
		}
		return true
	}

	private fun cambiarVelocidad(delta: Int): Boolean {
		val a = lector.ajustes
		a.velocidad = a.velocidad + delta
		lector.decir(t.getString(R.string.valor_velocidad, a.velocidad))
		return true
	}

	private fun cambiarTono(delta: Int): Boolean {
		val a = lector.ajustes
		a.tono = a.tono + delta
		lector.decir(t.getString(R.string.valor_tono, a.tono))
		return true
	}

	private fun volumen(subir: Boolean): Boolean {
		val audio = servicio.getSystemService(android.media.AudioManager::class.java) ?: return false
		val flujo = com.xebolax.tiresias.voz.PistaAudio.flujo
		val max = audio.getStreamMaxVolume(flujo).coerceAtLeast(1)
		// El valor exacto (con auriculares Bluetooth, «subir» movía el volumen de la música: Backtalk 265),
		// y nunca a cero: Tiresias no se puede quedar mudo con un gesto
		val nuevo = (audio.getStreamVolume(flujo) + if (subir) 1 else -1).coerceIn(1, max)
		runCatching { audio.setStreamVolume(flujo, nuevo, 0) }
		lector.decir(t.getString(R.string.valor_volumen, audio.getStreamVolume(flujo) * 100 / max))
		return true
	}

	private fun cambiarPuntuacion(adelante: Boolean): Boolean {
		val niveles = listOf(NivelPuntuacion.NINGUNA, NivelPuntuacion.ALGUNAS, NivelPuntuacion.MAYORIA, NivelPuntuacion.TODAS)
		val a = lector.ajustes
		val i = niveles.indexOf(a.puntuacion).coerceAtLeast(0)
		a.puntuacion = niveles[(i + if (adelante) 1 else niveles.size - 1) % niveles.size]
		val nombres = t.resources.getStringArray(R.array.puntuacion_nombres)
		lector.decir(t.getString(R.string.valor_puntuacion, nombres[niveles.indexOf(a.puntuacion)]))
		return true
	}

	private fun cambiarVerbosidad(adelante: Boolean): Boolean {
		val niveles = listOf(NivelVerbosidad.ALTA, NivelVerbosidad.MEDIA, NivelVerbosidad.BAJA)
		val a = lector.ajustes
		val i = niveles.indexOf(a.nivelVerbosidad).coerceAtLeast(0)
		a.nivelVerbosidad = niveles[(i + if (adelante) 1 else niveles.size - 1) % niveles.size]
		lector.recargarAjustes()
		val nombres = t.resources.getStringArray(R.array.verbosidad_nombres)
		lector.decir(t.getString(R.string.valor_verbosidad, nombres[niveles.indexOf(a.nivelVerbosidad)]))
		return true
	}

	private fun cambiarMotor(adelante: Boolean): Boolean {
		val a = lector.ajustes
		// Solo las voces elegidas para el control (Ajustes, Voz), o todas
		val motores = com.xebolax.tiresias.voz.VocesDisponibles.delControl(servicio, a)
		val i = motores.indexOf(a.motor).coerceAtLeast(0)
		a.motor = motores[(i + if (adelante) 1 else motores.size - 1) % motores.size]
		// Cada motor con su velocidad: la suya o, la primera vez, la normal (y se dice)
		a.ajustarVozAlMotor()
		lector.voz.elegirMotor()
		lector.decir(t.getString(R.string.valor_motor_velocidad, lector.voz.nombreMotor, a.velocidad))
		return true
	}

	/** El menú circular; en Android 10 o anterior (sin paso directo de los toques), sus opciones en una lista. */
	val circular by lazy {
		com.xebolax.tiresias.ventanas.MenuCircular(
			servicio,
			decir = { texto -> lector.decir(texto, Modo.INTERRUMPIR, Tipo.NAVEGACION) },
			alPasar = { lector.sonidos.tocar(Sonido.FOCO) },
			// Poner el paso es inmediato (antes de enseñar el menú); quitarlo, en el hilo del lector
			alPaso = { poner -> if (poner) lector.gestos.pasoParaMenu(true) else lector.manejador.post { lector.gestos.pasoParaMenu(false) } },
		)
	}

	private fun menuCircular(): Boolean {
		val acciones = lector.ajustes.menuCircular.take(com.xebolax.tiresias.ventanas.MenuCircular.OPCIONES)
		if (acciones.isEmpty()) return false
		val nombres = acciones.map { nombreAccion(it) }
		val hacer: (Int) -> Unit = { i -> lector.manejador.post { ejecutar(acciones[i]) } }
		if (Build.VERSION.SDK_INT < 30) {
			ventanas.lista(t.getString(R.string.circular_titulo), nombres, alElegir = hacer)
			return true
		}
		circular.abrir(nombres, t.getString(R.string.circular_aviso), hacer)
		return true
	}

	/**
	 * Quita y vuelve a poner la exploración táctil: si Android deja un dedo
	 * «pegado» (pasa en algunos Samsung) y los toques dejan de explorar, esto
	 * lo arregla sin reiniciar el móvil.
	 */
	private fun reiniciarTactil(): Boolean {
		if (suspendido) return false
		alSuspender(true)
		lector.manejador.postDelayed({
			alSuspender(false)
			lector.gestos.pasoParaMenu(false)
			lector.decir(R.string.tactil_reiniciado)
		}, 600)
		return true
	}

	/** En la app de delante, activar siempre con un toque de verdad (o dejar de hacerlo). */
	private fun alternarClicDirecto(): Boolean {
		val paquete = lector.raiz()?.packageName?.toString() ?: return false
		val apps = lector.ajustes.appsClicDirecto.toMutableSet()
		val quitar = paquete in apps
		if (quitar) apps -= paquete else apps += paquete
		lector.ajustes.appsClicDirecto = apps
		lector.decir(if (quitar) R.string.clic_directo_no else R.string.clic_directo_si)
		return true
	}

	/** Sistema, altavoz y auriculares, por turno ([adelante]) o al revés; los auriculares solo si hay unos conectados. */
	private fun cambiarSalida(adelante: Boolean = true): Boolean {
		val a = lector.ajustes
		val p = com.xebolax.tiresias.voz.PistaAudio
		val orden = listOf(p.SALIDA_SISTEMA, p.SALIDA_ALTAVOZ, p.SALIDA_AURICULARES)
		var siguiente = orden[(orden.indexOf(a.salidaAudio).coerceAtLeast(0) + if (adelante) 1 else orden.size - 1) % orden.size]
		if (siguiente == p.SALIDA_AURICULARES) {
			p.salida = siguiente
			if (p.dispositivoElegido() == null) {
				lector.decir(t.getString(R.string.salida_sin_auriculares))
				siguiente = p.SALIDA_SISTEMA
			}
		}
		a.salidaAudio = siguiente
		p.salida = siguiente
		val nombre = when (siguiente) {
			p.SALIDA_ALTAVOZ -> R.string.salida_altavoz
			p.SALIDA_AURICULARES -> R.string.salida_auriculares
			else -> R.string.salida_sistema
		}
		lector.decir(t.getString(R.string.valor_salida, t.getString(nombre)), Modo.ENCOLAR)
		return true
	}

	private fun cambiarIdioma(): Boolean {
		val a = lector.ajustes
		val idiomas = listOf("", "es", "en")
		val i = idiomas.indexOf(a.idioma).coerceAtLeast(0)
		com.xebolax.tiresias.base.Idioma.cambiar(servicio, idiomas[(i + 1) % idiomas.size])
		lector.recargarAjustes()
		lector.manejador.post {
			val nombre = if (a.idioma.isEmpty()) t.getString(R.string.idioma_sistema) else Locale(a.idioma).getDisplayLanguage(Locale(a.idioma))
			lector.decir(t.getString(R.string.valor_idioma, nombre))
		}
		return true
	}

	private fun deshacer(deshacer: Boolean): Boolean {
		// No hay acción de accesibilidad para deshacer: se busca la que ofrezca la app con ese nombre
		val f = lector.foco ?: return false
		val nombre = t.getString(if (deshacer) R.string.acc_deshacer else R.string.acc_rehacer).lowercase()
		val a = f.actionList.firstOrNull { it.label?.toString()?.lowercase()?.contains(nombre) == true }
		if (a == null) {
			lector.decir(R.string.acc_no_deshacer)
			return false
		}
		return runCatching { f.performAction(a.id) }.getOrDefault(false)
	}

	/** La palabra fonética de un carácter («a» → «Antonio»), o null. */
	fun fonetica(c: String): String? {
		if (c.length != 1 || !c[0].isLetter()) return null
		val datos = com.xebolax.tiresias.textos.DatosIdioma.de(servicio, com.xebolax.tiresias.base.Idioma.efectivo(servicio))
		return datos.fonetico.de(c[0])
	}

	private fun deletreoFonetico(): Boolean {
		val texto = lector.voz.ultimo ?: return false
		val datos = com.xebolax.tiresias.textos.DatosIdioma.de(servicio, com.xebolax.tiresias.base.Idioma.efectivo(servicio))
		val palabra = texto.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
		val dicho = palabra.map { c -> datos.fonetico.de(c) ?: c.toString() }.joinToString(", ")
		lector.decir(dicho)
		return true
	}

	/** Lo que el usuario ha elegido de hora, fecha, batería, conexión... en su orden (Ajustes, Notificaciones y pantalla). */
	private fun estadoSistema(): Boolean {
		val partes = lector.ajustes.partesEstado.mapNotNull { parte -> runCatching { parteEstado(parte) }.getOrNull() }
		lector.decir(partes.filter { it.isNotBlank() }.joinToString(". ").ifBlank { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) })
		return true
	}

	private fun parteEstado(p: com.xebolax.tiresias.nucleo.ParteEstado): String? = when (p) {
		com.xebolax.tiresias.nucleo.ParteEstado.HORA -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
		com.xebolax.tiresias.nucleo.ParteEstado.FECHA -> DateFormat.getDateInstance(DateFormat.FULL).format(Date())
		com.xebolax.tiresias.nucleo.ParteEstado.BATERIA -> {
			val bateria = servicio.getSystemService(BatteryManager::class.java)
			val nivel = bateria?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
			if (nivel in 0..100) t.getString(if (bateria?.isCharging == true) R.string.estado_bateria_cargando else R.string.estado_bateria, nivel) else null
		}
		com.xebolax.tiresias.nucleo.ParteEstado.CONEXION -> {
			val cm = servicio.getSystemService(android.net.ConnectivityManager::class.java)
			val red = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
			t.getString(
				when {
					red == null -> R.string.estado_sin_conexion
					red.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> R.string.estado_wifi
					red.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> R.string.estado_datos
					red.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> R.string.estado_ethernet
					else -> R.string.estado_conectado
				}
			)
		}
		com.xebolax.tiresias.nucleo.ParteEstado.NOTIFICACIONES -> {
			// Las que han llegado en la última hora (las de Notificaciones recientes)
			val desde = System.currentTimeMillis() - 3_600_000L
			val n = lector.avisos.historial().count { it.momento >= desde }
			t.resources.getQuantityString(R.plurals.estado_notificaciones, n, n)
		}
		com.xebolax.tiresias.nucleo.ParteEstado.NO_MOLESTAR -> if (lector.noMolestar()) t.getString(R.string.estado_no_molestar) else null
		com.xebolax.tiresias.nucleo.ParteEstado.TIMBRE -> {
			val audio = servicio.getSystemService(android.media.AudioManager::class.java)
			when (audio?.ringerMode) {
				android.media.AudioManager.RINGER_MODE_SILENT -> t.getString(R.string.estado_timbre_silencio)
				android.media.AudioManager.RINGER_MODE_VIBRATE -> t.getString(R.string.estado_timbre_vibracion)
				android.media.AudioManager.RINGER_MODE_NORMAL -> t.getString(R.string.estado_timbre_sonido)
				else -> null
			}
		}
		com.xebolax.tiresias.nucleo.ParteEstado.ESPACIO -> {
			val libre = android.os.StatFs(android.os.Environment.getDataDirectory().path).availableBytes
			t.getString(R.string.estado_espacio, android.text.format.Formatter.formatShortFileSize(t, libre))
		}
	}

	private fun dondeEstoy(): Boolean {
		val partes = mutableListOf<String>()
		lector.foco?.packageName?.toString()?.let { partes += lector.avisos.nombreApp(it) }
		lector.tituloActivo().takeIf { it.isNotBlank() }?.let { partes += it }
		lector.foco?.let { partes += lector.compositor.describir(lector.elemento(it), lector.ajustes.verbosidad) }
		lector.decir(partes.distinct().joinToString(". "))
		return true
	}

	/**
	 * El gesto de multimedia: si suena una llamada, la responde; durante una
	 * llamada, la cuelga (con el permiso de responder llamadas, que se da en
	 * Ajustes); si no, reproduce o pausa la música o el vídeo.
	 */
	private fun multimedia(): Boolean {
		val audio = servicio.getSystemService(android.media.AudioManager::class.java) ?: return false
		if (llamadaInternet(audio)) return true
		if (llamada(audio)) return true
		val ahora = SystemClock.uptimeMillis()
		audio.dispatchMediaKeyEvent(KeyEvent(ahora, ahora, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
		audio.dispatchMediaKeyEvent(KeyEvent(ahora, ahora, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
		return true
	}

	/**
	 * Las llamadas de WhatsApp, Telegram, Signal y las demás de internet, que
	 * Android no deja contestar con TelecomManager: se pulsa el botón de su
	 * notificación. Mientras suena (o en el primer minuto), «Contestar»; con la
	 * llamada en curso (el audio en modo comunicación), «Colgar» o, si no lo
	 * tiene, «Rechazar». Sin llamada de verdad no hace nada: el gesto sigue
	 * siendo el de la música.
	 */
	private fun llamadaInternet(audio: android.media.AudioManager): Boolean {
		val l = lector.avisos.llamadaInternet ?: return false
		val modo = audio.mode
		val enCurso = modo == android.media.AudioManager.MODE_IN_COMMUNICATION || modo == android.media.AudioManager.MODE_IN_CALL
		val sonando = modo == android.media.AudioManager.MODE_RINGTONE || System.currentTimeMillis() - l.momento < Avisos.SONANDO_MS
		val n = l.notificacion
		val contestar = lector.avisos.accionDe(n, Avisos.CONTESTAR)
		val colgar = lector.avisos.accionDe(n, Avisos.COLGAR) ?: lector.avisos.accionDe(n, Avisos.RECHAZAR)
		val (accion, contesta) = when {
			contestar != null && !l.contestada && sonando -> contestar to true
			colgar != null && (enCurso || (l.contestada && sonando)) -> colgar to false
			else -> return false
		}
		try {
			accion.actionIntent.send()
		} catch (e: android.app.PendingIntent.CanceledException) {
			// La llamada ya no está (la notificación se quitó): el gesto vuelve a ser el de la música
			com.xebolax.tiresias.base.Registro.w(ETIQUETA, "El botón de la llamada ya no vale", e)
			lector.avisos.llamadaInternet = null
			return false
		}
		if (contesta) {
			l.contestada = true
			lector.sonidos.tocar(Sonido.ACTIVAR)
			lector.decir(R.string.llamada_contestando)
		} else {
			lector.avisos.llamadaInternet = null
			lector.sonidos.tocar(Sonido.APAGADO)
			lector.decir(R.string.llamada_colgando)
		}
		return true
	}

	@android.annotation.SuppressLint("MissingPermission")
	private fun llamada(audio: android.media.AudioManager): Boolean {
		if (Build.VERSION.SDK_INT < 26) return false
		val modo = audio.mode
		val sonando = modo == android.media.AudioManager.MODE_RINGTONE
		val enLlamada = modo == android.media.AudioManager.MODE_IN_CALL || modo == android.media.AudioManager.MODE_IN_COMMUNICATION
		if (!sonando && !enLlamada) return false
		if (servicio.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			lector.decir(R.string.llamada_sin_permiso)
			return true
		}
		val telecom = servicio.getSystemService(android.telecom.TelecomManager::class.java) ?: return false
		return runCatching {
			if (sonando) {
				@Suppress("DEPRECATION") telecom.acceptRingingCall()
				lector.sonidos.tocar(Sonido.ACTIVAR)
			} else if (Build.VERSION.SDK_INT >= 28) {
				@Suppress("DEPRECATION") telecom.endCall()
				lector.sonidos.tocar(Sonido.APAGADO)
			}
			true
		}.getOrElse {
			com.xebolax.tiresias.base.Registro.w(ETIQUETA, "No se pudo responder o colgar", it)
			false
		}
	}

	/** Las órdenes que añaden los complementos para la app de delante (y las de todas las apps). */
	private fun ordenesComplementos(): Boolean {
		val ordenes = lector.scripts.ordenes(lector.raiz()?.packageName?.toString())
		if (ordenes.isEmpty()) {
			lector.decir(R.string.scripts_sin_ordenes)
			return false
		}
		val previo = lector.foco
		ventanas.lista(t.getString(R.string.scripts_ordenes), ordenes.map { it.second }, devolverFoco(previo)) { i ->
			lector.manejador.postDelayed({
				previo?.takeIf { Nodos.vivo(it) }?.let { lector.enfocarSinHablar(it) }
				lector.scripts.ejecutarOrden(ordenes[i].first, ordenes[i].second)
			}, 300)
		}
		return true
	}

	/** Busca [texto] en la pantalla directamente (órdenes por voz: «buscar correo»). */
	fun buscarTexto(texto: String) {
		val raiz = lector.raiz() ?: return
		val clave = normalizar(texto)
		val nodos = nav.orden(raiz).filter { n -> lector.elemento(n).let { normalizar(it.nombre + " " + it.texto).contains(clave) } }
		when {
			nodos.isEmpty() -> lector.decir(t.getString(R.string.buscar_nada, texto))
			nodos.size == 1 -> lector.enfocar(nodos.first())
			else -> {
				val previo = lector.foco
				val textos = nodos.map { lector.compositor.describir(lector.elemento(it), lector.ajustes.verbosidad.copy(decirPistas = false)) }
				ventanas.lista(t.resources.getQuantityString(R.plurals.buscar_resultados, nodos.size, nodos.size), textos, devolverFoco(previo)) { j ->
					lector.manejador.postDelayed({ lector.enfocar(nodos[j]) }, 300)
				}
			}
		}
	}

	private fun abrir(clase: Class<*>): Boolean {
		val intent = Intent(servicio, clase).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
		return runCatching { servicio.startActivity(intent); true }.getOrElse {
			com.xebolax.tiresias.base.Registro.e(ETIQUETA, "No se pudo abrir ${clase.simpleName}", it)
			false
		}
	}

	/**
	 * Suspender: Tiresias deja de hablar y de tomar los toques (el móvil se usa
	 * como sin lector, por ejemplo para que otra persona lo maneje). Se vuelve
	 * con el mismo atajo (las dos teclas de volumen) o con el gesto de emergencia,
	 * y también solo al apagar y encender la pantalla: nunca deja a nadie atrapado.
	 */
	fun suspender(si: Boolean) {
		if (si == suspendido) return
		if (si) {
			lector.decir(R.string.suspendido)
			lector.sonidos.tocar(Sonido.APAGADO)
		}
		suspendido = si
		alSuspender(si)
		if (si) com.xebolax.tiresias.servicio.AvisoPausa.mostrar(servicio) else com.xebolax.tiresias.servicio.AvisoPausa.quitar(servicio)
		if (!si) {
			lector.sonidos.tocar(Sonido.ENCENDIDO)
			lector.decir(R.string.reanudado)
		}
	}

	/**
	 * Modo seguro (gesto de emergencia, que nada puede quitar): vuelve a un
	 * estado en el que se puede usar el móvil pase lo que pase. Quita la cortina,
	 * el paso de gestos y la suspensión, apaga los scripts, vuelve a eSpeak y a
	 * una velocidad normal si era extrema.
	 */
	private fun modoSeguro(): Boolean {
		suspender(false)
		ventanas.ponerCortina(false)
		ventanas.cerrar()
		circular.cerrar()
		lector.gestos.restablecer()
		lector.scripts.apagarTodos()
		lector.voz.silenciada = false
		if (lector.brailleListo) lector.braille.quitarPrimero()
		lector.vision.pararDescripcionContinua()
		val a = lector.ajustes
		if (a.motor != Ajustes.MOTOR_ESPEAK && a.motor != Ajustes.MOTOR_PICO) a.motor = Ajustes.MOTOR_ESPEAK
		// La velocidad, la del motor que queda (que puede ser otra que la de antes)
		a.ajustarVozAlMotor()
		if (a.velocidad > 300 || a.velocidad < 50) a.velocidad = 100
		lector.voz.elegirMotor()
		lector.control = ControlLectura.PREDETERMINADO
		lector.ayudaEntrada = false
		lector.decir(R.string.modo_seguro)
		return true
	}

	fun nombreAccion(a: Accion): String {
		val id = t.resources.getIdentifier("accion_" + a.name.lowercase(), "string", t.packageName)
		return if (id != 0) t.getString(id) else a.name.lowercase().replace('_', ' ')
	}

	companion object {
		/** Lo que en voz a demanda no habla: moverse (lo demás, que se pide, sí). */
		private val NAVEGAR_CALLADO = setOf(
			Accion.SIGUIENTE, Accion.ANTERIOR, Accion.PRIMERO, Accion.ULTIMO, Accion.DESPLAZAR_ARRIBA, Accion.DESPLAZAR_ABAJO,
			Accion.ACTIVAR, Accion.MANTENER, Accion.NINGUNA,
		)
		/** Las páginas que se desplaza como mucho una lista para llegar a una posición. */
		private const val MAXIMO_PAGINAS_POSICION = 60
		/** Las horas que abarca el resumen de los avisos. */
		const val HORAS_RESUMEN = 12
		/** Páginas que se recorren como mucho al copiar una lista entera. */
		private const val MAXIMO_PAGINAS_LISTA = 150
		private const val ETIQUETA = "Ejecutor"
	}
}
