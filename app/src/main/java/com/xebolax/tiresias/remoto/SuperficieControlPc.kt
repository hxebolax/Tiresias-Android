package com.xebolax.tiresias.remoto

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.braille.DireccionBraille
import com.xebolax.tiresias.braille.GestoBraille
import com.xebolax.tiresias.braille.ModoSuperficie
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.remoto.TeclaVk
import com.xebolax.tiresias.voz.Sonido

/**
 * Las órdenes que tienen un gesto propio en la pantalla de control de un PC,
 * con su nombre. La tabla de gestos ([GestosPc.TABLA]) es fija: así el tutorial
 * y la lista de gestos la enseñan sin necesitar un PC ni el lector.
 */
enum class OrdenPc(val nombre: Int) {
	IZQUIERDA(R.string.pc_o_izquierda), DERECHA(R.string.pc_o_derecha), ARRIBA(R.string.pc_o_arriba), ABAJO(R.string.pc_o_abajo),
	CALLAR(R.string.pc_o_callar), INTRO(R.string.pc_o_intro), ESPACIO(R.string.pc_o_espacio), ALT(R.string.pc_o_alt),
	TAB(R.string.pc_o_tab), TAB_ATRAS(R.string.pc_o_tab_atras), ESCAPE(R.string.pc_o_escape), MENU_CONTEXTUAL(R.string.pc_o_menu_contextual),
	FOCO(R.string.pc_o_foco), DICTAR(R.string.pc_o_dictar), LEER_LINEA(R.string.pc_o_leer_linea), INICIO_WINDOWS(R.string.pc_o_inicio_windows),
	VENTANA_SIGUIENTE(R.string.pc_o_ventana_siguiente), VENTANA_ANTERIOR(R.string.pc_o_ventana_anterior), LEER_TODO(R.string.pc_o_leer_todo), MENU_ORDENES(R.string.pc_o_menu_ordenes),
	TITULO(R.string.pc_o_titulo), ESCRIBIR(R.string.pc_o_escribir), PEGAR_COPIADO(R.string.pc_o_pegar_copiado), AYUDA_GESTOS(R.string.pc_o_ayuda_gestos),
	INICIO(R.string.pc_o_inicio), FIN(R.string.pc_o_fin), RETROCESO(R.string.pc_o_retroceso), SUPRIMIR(R.string.pc_o_suprimir),
	ESTADO(R.string.pc_o_estado), QUITAR(R.string.pc_o_quitar), VOZ_PC(R.string.pc_o_voz_pc), MODO_VOZ(R.string.pc_o_modo_voz),
}

/** La tabla de gestos de la pantalla de control, y los nombres de los gestos. */
object GestosPc {
	val TABLA: List<Pair<GestoPc, OrdenPc>> = listOf(
		GestoPc(1, TipoGestoPc.DESLIZAR, DireccionBraille.IZQUIERDA) to OrdenPc.IZQUIERDA,
		GestoPc(1, TipoGestoPc.DESLIZAR, DireccionBraille.DERECHA) to OrdenPc.DERECHA,
		GestoPc(1, TipoGestoPc.DESLIZAR, DireccionBraille.ARRIBA) to OrdenPc.ARRIBA,
		GestoPc(1, TipoGestoPc.DESLIZAR, DireccionBraille.ABAJO) to OrdenPc.ABAJO,
		GestoPc(1, TipoGestoPc.TOQUE) to OrdenPc.CALLAR,
		GestoPc(1, TipoGestoPc.DOBLE_TOQUE) to OrdenPc.INTRO,
		GestoPc(1, TipoGestoPc.TRIPLE_TOQUE) to OrdenPc.ESPACIO,
		GestoPc(1, TipoGestoPc.MANTENER) to OrdenPc.ALT,
		GestoPc(2, TipoGestoPc.DESLIZAR, DireccionBraille.DERECHA) to OrdenPc.TAB,
		GestoPc(2, TipoGestoPc.DESLIZAR, DireccionBraille.IZQUIERDA) to OrdenPc.TAB_ATRAS,
		GestoPc(2, TipoGestoPc.DESLIZAR, DireccionBraille.ARRIBA) to OrdenPc.ESCAPE,
		GestoPc(2, TipoGestoPc.DESLIZAR, DireccionBraille.ABAJO) to OrdenPc.MENU_CONTEXTUAL,
		GestoPc(2, TipoGestoPc.TOQUE) to OrdenPc.FOCO,
		GestoPc(2, TipoGestoPc.DOBLE_TOQUE) to OrdenPc.DICTAR,
		GestoPc(2, TipoGestoPc.TRIPLE_TOQUE) to OrdenPc.LEER_LINEA,
		GestoPc(2, TipoGestoPc.MANTENER) to OrdenPc.INICIO_WINDOWS,
		GestoPc(3, TipoGestoPc.DESLIZAR, DireccionBraille.DERECHA) to OrdenPc.VENTANA_SIGUIENTE,
		GestoPc(3, TipoGestoPc.DESLIZAR, DireccionBraille.IZQUIERDA) to OrdenPc.VENTANA_ANTERIOR,
		GestoPc(3, TipoGestoPc.DESLIZAR, DireccionBraille.ARRIBA) to OrdenPc.LEER_TODO,
		GestoPc(3, TipoGestoPc.DESLIZAR, DireccionBraille.ABAJO) to OrdenPc.MENU_ORDENES,
		GestoPc(3, TipoGestoPc.TOQUE) to OrdenPc.TITULO,
		GestoPc(3, TipoGestoPc.DOBLE_TOQUE) to OrdenPc.ESCRIBIR,
		GestoPc(3, TipoGestoPc.TRIPLE_TOQUE) to OrdenPc.PEGAR_COPIADO,
		GestoPc(3, TipoGestoPc.MANTENER) to OrdenPc.AYUDA_GESTOS,
		GestoPc(4, TipoGestoPc.DESLIZAR, DireccionBraille.ARRIBA) to OrdenPc.INICIO,
		GestoPc(4, TipoGestoPc.DESLIZAR, DireccionBraille.ABAJO) to OrdenPc.FIN,
		GestoPc(4, TipoGestoPc.DESLIZAR, DireccionBraille.IZQUIERDA) to OrdenPc.RETROCESO,
		GestoPc(4, TipoGestoPc.DESLIZAR, DireccionBraille.DERECHA) to OrdenPc.SUPRIMIR,
		GestoPc(4, TipoGestoPc.TOQUE) to OrdenPc.ESTADO,
		GestoPc(4, TipoGestoPc.DOBLE_TOQUE) to OrdenPc.QUITAR,
		GestoPc(4, TipoGestoPc.TRIPLE_TOQUE) to OrdenPc.VOZ_PC,
		GestoPc(4, TipoGestoPc.MANTENER) to OrdenPc.MODO_VOZ,
	)

	fun ordenDe(g: GestoPc): OrdenPc? = TABLA.firstOrNull { it.first == g }?.second

	/** «Dos dedos hacia la derecha», «Un dedo, doble toque». */
	fun nombreGesto(c: Context, g: GestoPc): String {
		val dedos = c.resources.getQuantityString(R.plurals.pc_dedos, g.dedos, g.dedos)
		val que = when (g.tipo) {
			TipoGestoPc.TOQUE -> c.getString(R.string.pc_g_toque)
			TipoGestoPc.DOBLE_TOQUE -> c.getString(R.string.pc_g_doble_toque)
			TipoGestoPc.TRIPLE_TOQUE -> c.getString(R.string.pc_g_triple_toque)
			TipoGestoPc.MANTENER -> c.getString(R.string.pc_g_mantener)
			TipoGestoPc.DESLIZAR -> c.getString(when (g.direccion) {
				DireccionBraille.IZQUIERDA -> R.string.pc_g_izquierda
				DireccionBraille.DERECHA -> R.string.pc_g_derecha
				DireccionBraille.ARRIBA -> R.string.pc_g_arriba
				else -> R.string.pc_g_abajo
			})
		}
		return c.getString(R.string.pc_gesto_nombre, dedos, que)
	}

	/** El nombre de una tecla, para decir las órdenes y las combinaciones. */
	fun nombreTecla(c: Context, t: TeclaVk): String = when (t) {
		TeclaVk.IZQUIERDA -> c.getString(R.string.pc_t_izquierda)
		TeclaVk.DERECHA -> c.getString(R.string.pc_t_derecha)
		TeclaVk.ARRIBA -> c.getString(R.string.pc_t_arriba)
		TeclaVk.ABAJO -> c.getString(R.string.pc_t_abajo)
		TeclaVk.INICIO -> c.getString(R.string.nvda_menu_inicio)
		TeclaVk.FIN -> c.getString(R.string.nvda_menu_fin)
		TeclaVk.REPAG -> c.getString(R.string.nvda_menu_repag)
		TeclaVk.AVPAG -> c.getString(R.string.nvda_menu_avpag)
		TeclaVk.INSERTAR -> c.getString(R.string.pc_t_insertar)
		TeclaVk.SUPRIMIR -> c.getString(R.string.nvda_menu_suprimir)
		TeclaVk.INTRO -> c.getString(R.string.pc_t_intro)
		TeclaVk.ESCAPE -> c.getString(R.string.pc_t_escape)
		TeclaVk.TABULADOR -> c.getString(R.string.pc_t_tab)
		TeclaVk.RETROCESO -> c.getString(R.string.pc_t_retroceso)
		TeclaVk.ESPACIO -> c.getString(R.string.pc_t_espacio)
		TeclaVk.MAYUSCULAS -> c.getString(R.string.nvda_mayusculas)
		TeclaVk.CONTROL -> c.getString(R.string.nvda_control)
		TeclaVk.ALT -> c.getString(R.string.nvda_alt)
		TeclaVk.WINDOWS -> c.getString(R.string.pc_t_windows)
		TeclaVk.APLICACIONES -> c.getString(R.string.pc_t_aplicaciones)
		TeclaVk.BLOQ_MAYUS -> c.getString(R.string.pc_t_bloq_mayus)
		TeclaVk.NVDA -> c.getString(R.string.nvda_tecla_nvda)
		TeclaVk.NUM_MAS -> c.getString(R.string.pc_t_num_mas)
		TeclaVk.NUM_MENOS -> c.getString(R.string.pc_t_num_menos)
		TeclaVk.NUM_POR -> c.getString(R.string.pc_t_num_por)
		TeclaVk.NUM_DIVIDIR -> c.getString(R.string.pc_t_num_dividir)
		TeclaVk.NUM_INTRO -> c.getString(R.string.pc_t_num_intro)
		TeclaVk.NUM_PUNTO -> c.getString(R.string.pc_t_num_punto)
		TeclaVk.IMPRIMIR_PANTALLA -> c.getString(R.string.pc_t_impr_pant)
		TeclaVk.PAUSA -> c.getString(R.string.pc_t_pausa)
		TeclaVk.PUNTO -> c.getString(R.string.pc_t_punto)
		TeclaVk.COMA -> c.getString(R.string.pc_t_coma)
		else -> {
			val n = t.name
			when {
				n.startsWith("NUM") -> c.getString(R.string.pc_t_numerico, n.drop(3))
				n.length == 2 && n[0] == 'N' && n[1].isDigit() -> n.drop(1)
				else -> n
			}
		}
	}

	/** «Control más Alt más Supr». */
	fun nombreCombinacion(c: Context, teclas: List<TeclaVk>): String = teclas.joinToString(c.getString(R.string.pc_mas)) { nombreTecla(c, it) }
}

/**
 * La pantalla de control de un PC con NVDA o con Tiresias para Windows: una
 * pantalla a pantalla completa, encima de todo, con gestos propios de uno a
 * cuatro dedos para manejar Windows desde el móvil sin saber braille ([GestosPc.TABLA]):
 * un dedo son las flechas, dos dedos el Tabulador, Escape y el menú
 * contextual, tres dedos las ventanas y el menú de órdenes, cuatro dedos el
 * principio y el final. Un toque calla la voz del PC; dos toques, Intro.
 *
 * Tres dedos hacia abajo abren el **menú de órdenes**, por categorías: Windows,
 * edición, NVDA, navegación por objetos, cursor de revisión, navegación web,
 * teclas sueltas, modificadores (que se preparan para la tecla siguiente) y
 * esta pantalla. Se recorre con un dedo y se elige con un doble toque.
 *
 * En **práctica** (desde el tutorial, sin PC) cada gesto se dice y no se manda.
 * Las dos teclas de volumen quitan la pantalla; no se quita al cambiar de app.
 */
class SuperficieControlPc(
	lector: Lector,
	private val cliente: ClienteNvda?,
	private val practica: Boolean = false,
	private val alQuitar: () -> Unit = {},
) : ModoSuperficie(lector) {
	private var vista: VistaControlPc? = null
	/** Los modificadores preparados para la tecla siguiente. */
	private val modificadores = LinkedHashSet<TeclaVk>()
	/** El menú de órdenes abierto (el nivel en el que se está), o null. */
	private var menu: Menu? = null

	override val cerrarAlCambiarDeApp = false
	override val rotulo = "⌨ PC"

	override fun aviso(): String = t.getString(if (practica) R.string.pc_aviso_practica else R.string.pc_aviso)

	override fun alPuntos(puntos: Int) = Unit
	override fun alGesto(g: GestoBraille) = Unit

	/** Lo que avisaba antes el cliente de sus cambios (la pantalla de Controlar un PC): se le sigue avisando. */
	private var avisabaAntes: (() -> Unit)? = null

	override fun crearVista(tocado: () -> Unit): View {
		// Si la conexión se corta (el PC se va, se cae la red), la pantalla no se queda puesta sin nada detrás
		cliente?.let { cl ->
			avisabaAntes = cl.alCambiar
			cl.alCambiar = {
				avisabaAntes?.invoke()
				lector.manejador.post { if (puesta) alCambiarConexion(cl) }
			}
		}
		val v = VistaControlPc(lector.servicio, { g ->
			tocado()
			lector.manejador.post { if (puesta) alGestoPc(g) }
		}, {
			val a = lector.ajustes
			if (a.brailleVibrarPuntos) vista?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
			if (a.brailleSonarPuntos) lector.sonidos.tocar(Sonido.TECLA, volumen = a.brailleVolumenSonidos / 100f)
		})
		v.rotulo = rotulo
		v.mensaje = t.getString(if (practica) R.string.pc_rotulo_practica else R.string.pc_rotulo)
		vista = v
		return v
	}

	override fun alCerrar() {
		modificadores.clear()
		// El cliente vuelve a avisar solo a quien avisaba antes
		cliente?.let { cl -> cl.alCambiar = avisabaAntes }
		alQuitar()
	}

	private fun alCambiarConexion(cl: ClienteNvda) {
		when (cl.estado) {
			ClienteNvda.Estado.TERMINADO -> {
				menu = null
				cerrar()
				lector.decir(cl.error ?: t.getString(R.string.nvda_cortada), com.xebolax.tiresias.voz.Modo.ENCOLAR, com.xebolax.tiresias.voz.Tipo.SISTEMA)
			}
			ClienteNvda.Estado.SIN_PC -> decir(t.getString(R.string.nvda_estado_sin_pc))
			ClienteNvda.Estado.CONECTADO -> decir(t.getString(R.string.nvda_estado_conectado, com.xebolax.tiresias.ui.ControlPc.host))
			else -> Unit
		}
	}

	/** Un gesto por su clave («2_DESLIZAR_DERECHA»), desde las pruebas. */
	fun gestoDePrueba(clave: String): Boolean {
		val g = GestoPc.deClave(clave) ?: return false
		alGestoPc(g)
		return true
	}

	fun alGestoPc(g: GestoPc) {
		menu?.let {
			gestoMenu(it, g)
			return
		}
		val orden = GestosPc.ordenDe(g)
		if (orden == null) {
			decir(t.getString(R.string.pc_gesto_sin_orden))
			return
		}
		hacer(orden)
	}

	// --- Las órdenes de los gestos ----------------------------------------------------------------

	private fun hacer(o: OrdenPc) {
		val nombre = t.getString(o.nombre)
		when (o) {
			OrdenPc.IZQUIERDA -> pulsar(nombre, TeclaVk.IZQUIERDA)
			OrdenPc.DERECHA -> pulsar(nombre, TeclaVk.DERECHA)
			OrdenPc.ARRIBA -> pulsar(nombre, TeclaVk.ARRIBA)
			OrdenPc.ABAJO -> pulsar(nombre, TeclaVk.ABAJO)
			OrdenPc.CALLAR -> {
				// Control suelto para NVDA (calla la voz), y la voz del móvil también
				lector.voz.callar()
				mandar(nombre, listOf(TeclaVk.CONTROL), conModificadores = false, decirlo = practica)
			}
			OrdenPc.INTRO -> pulsar(nombre, TeclaVk.INTRO)
			OrdenPc.ESPACIO -> pulsar(nombre, TeclaVk.ESPACIO)
			OrdenPc.ALT -> pulsar(nombre, TeclaVk.ALT)
			OrdenPc.TAB -> pulsar(nombre, TeclaVk.TABULADOR)
			OrdenPc.TAB_ATRAS -> mandar(nombre, listOf(TeclaVk.MAYUSCULAS, TeclaVk.TABULADOR))
			OrdenPc.ESCAPE -> pulsar(nombre, TeclaVk.ESCAPE)
			OrdenPc.MENU_CONTEXTUAL -> pulsar(nombre, TeclaVk.APLICACIONES)
			OrdenPc.FOCO -> mandar(nombre, listOf(TeclaVk.NVDA, TeclaVk.TABULADOR))
			OrdenPc.DICTAR -> dictar()
			OrdenPc.LEER_LINEA -> mandar(nombre, if (portatil) listOf(TeclaVk.NVDA, TeclaVk.L) else listOf(TeclaVk.NVDA, TeclaVk.ARRIBA))
			OrdenPc.INICIO_WINDOWS -> pulsar(nombre, TeclaVk.WINDOWS)
			OrdenPc.VENTANA_SIGUIENTE -> mandar(nombre, listOf(TeclaVk.ALT, TeclaVk.TABULADOR))
			OrdenPc.VENTANA_ANTERIOR -> mandar(nombre, listOf(TeclaVk.ALT, TeclaVk.MAYUSCULAS, TeclaVk.TABULADOR))
			OrdenPc.LEER_TODO -> mandar(nombre, if (portatil) listOf(TeclaVk.NVDA, TeclaVk.A) else listOf(TeclaVk.NVDA, TeclaVk.ABAJO))
			OrdenPc.MENU_ORDENES -> abrirMenu(raiz())
			OrdenPc.TITULO -> mandar(nombre, listOf(TeclaVk.NVDA, TeclaVk.T))
			OrdenPc.ESCRIBIR -> escribir()
			OrdenPc.PEGAR_COPIADO -> pegarPortapapeles()
			OrdenPc.AYUDA_GESTOS -> abrirMenu(menuGestos(null))
			OrdenPc.INICIO -> pulsar(nombre, TeclaVk.INICIO)
			OrdenPc.FIN -> pulsar(nombre, TeclaVk.FIN)
			OrdenPc.RETROCESO -> pulsar(nombre, TeclaVk.RETROCESO)
			OrdenPc.SUPRIMIR -> pulsar(nombre, TeclaVk.SUPRIMIR)
			OrdenPc.ESTADO -> estado()
			OrdenPc.QUITAR -> cerrar()
			OrdenPc.VOZ_PC -> alternarVozPc()
			OrdenPc.MODO_VOZ -> mandar(nombre, listOf(TeclaVk.NVDA, TeclaVk.S))
		}
	}

	private val portatil: Boolean get() = lector.ajustes.nvdaDisposicion == Ajustes.NVDA_PORTATIL

	/** Pulsa [tecla] con los modificadores preparados (y los gasta). */
	private fun pulsar(nombre: String, tecla: TeclaVk) = mandar(nombre, listOf(tecla))

	/**
	 * Manda una combinación al PC, con los modificadores preparados delante si
	 * [conModificadores] (y los gasta). En práctica, solo la dice. Si se dice al
	 * mandarla ([decirlo]), va con los modificadores que llevaba.
	 */
	private fun mandar(nombre: String, teclas: List<TeclaVk>, conModificadores: Boolean = true, decirlo: Boolean = true) {
		val mods = if (conModificadores) modificadores.toList() else emptyList()
		if (conModificadores) modificadores.clear()
		val texto = if (mods.isEmpty()) nombre else t.getString(R.string.pc_con_modificadores, GestosPc.nombreCombinacion(t, mods), nombre)
		mostrar(texto)
		if (practica) {
			decir(texto)
			return
		}
		val cl = cliente
		if (cl == null || cl.estado != ClienteNvda.Estado.CONECTADO) {
			decir(t.getString(R.string.pc_sin_conexion))
			return
		}
		if (decirlo && (mods.isNotEmpty() || lector.ajustes.nvdaDecirOrdenes)) decir(texto)
		cl.teclas(mods + teclas)
	}

	/** Lo mismo que [mandar], dos veces seguidas (NVDA+F12 dos veces: la fecha). */
	private fun mandarDosVeces(nombre: String, teclas: List<TeclaVk>) {
		mandar(nombre, teclas)
		if (!practica) lector.manejador.postDelayed({ if (puesta) cliente?.teclas(teclas) }, 120)
	}

	private fun mostrar(texto: String) {
		vista?.mensaje = texto
	}

	private fun preparar(m: TeclaVk) {
		val nombre = GestosPc.nombreTecla(t, m)
		if (modificadores.add(m)) decir(t.getString(R.string.pc_modificador_preparado, nombre))
		else {
			modificadores.remove(m)
			decir(t.getString(R.string.pc_modificador_quitado, nombre))
		}
		mostrar(GestosPc.nombreCombinacion(t, modificadores.toList()))
	}

	private fun dictar() {
		if (practica) {
			decir(t.getString(OrdenPc.DICTAR.nombre))
			return
		}
		val cl = cliente ?: return decir(t.getString(R.string.pc_sin_conexion))
		val empezado = lector.escucha.dictarEn { texto ->
			if (texto.isNotBlank()) {
				cl.pegarTexto(texto)
				decir(t.getString(R.string.pc_dictado_enviado, texto))
			}
		}
		if (!empezado) decir(t.getString(R.string.pc_dictado_no))
	}

	/** Pide un texto en el móvil (sin la pantalla, para poder escribir) y lo pega en el PC; después vuelve la pantalla. */
	private fun escribir() {
		if (practica) {
			decir(t.getString(OrdenPc.ESCRIBIR.nombre))
			return
		}
		val cl = cliente ?: return decir(t.getString(R.string.pc_sin_conexion))
		val ventanas = (if (lector.scriptsListos) lector.scripts.ventanas else null) ?: return decir(t.getString(R.string.pc_sin_conexion))
		val volver = {
			lector.manejador.postDelayed({ if (com.xebolax.tiresias.ui.ControlPc.cliente === cl && cl.estado == ClienteNvda.Estado.CONECTADO) lector.superficie.abrir(SuperficieControlPc(lector, cl)) }, 300)
		}
		cerrar()
		ventanas.pedirTexto(t.getString(R.string.nvda_enviar_texto), t.getString(R.string.nvda_enviar_texto_pista), alCancelar = { volver() }) { texto ->
			if (texto.isNotEmpty()) cl.pegarTexto(texto)
			volver()
		}
	}

	private fun pegarPortapapeles() {
		if (practica) {
			decir(t.getString(OrdenPc.PEGAR_COPIADO.nombre))
			return
		}
		val cl = cliente ?: return decir(t.getString(R.string.pc_sin_conexion))
		val cm = lector.servicio.getSystemService(android.content.ClipboardManager::class.java)
		val texto = runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(lector.servicio)?.toString() }.getOrNull()
		if (texto.isNullOrEmpty()) decir(t.getString(R.string.nvda_portapapeles_vacio)) else {
			cl.pegarTexto(texto)
			decir(t.getString(R.string.pc_pegado))
		}
	}

	private fun alternarVozPc() {
		val cl = cliente
		if (practica || cl == null) {
			decir(t.getString(OrdenPc.VOZ_PC.nombre))
			return
		}
		cl.silenciado = !cl.silenciado
		decir(t.getString(if (cl.silenciado) R.string.nvda_voz_callada else R.string.nvda_voz_oida))
	}

	private fun estado() {
		val partes = ArrayList<String>()
		partes += when {
			practica -> t.getString(R.string.pc_estado_practica)
			cliente == null || cliente.estado != ClienteNvda.Estado.CONECTADO -> t.getString(R.string.nvda_estado_nada)
			else -> t.getString(R.string.nvda_estado_conectado, com.xebolax.tiresias.ui.ControlPc.host)
		}
		if (modificadores.isNotEmpty()) partes += t.getString(R.string.pc_estado_modificadores, GestosPc.nombreCombinacion(t, modificadores.toList()))
		partes += t.getString(if (portatil) R.string.pc_disposicion_portatil else R.string.pc_disposicion_escritorio)
		if (cliente?.silenciado == true) partes += t.getString(R.string.nvda_voz_callada)
		decir(partes.joinToString(". "))
	}

	// --- El menú de órdenes ------------------------------------------------------------------------

	/** Una opción del menú: lo que se dice y lo que hace (o el menú que abre). */
	private class Opcion(val nombre: String, val submenu: (() -> Menu)? = null, val hacer: () -> Unit = {})

	private class Menu(
		val titulo: String, val opciones: List<Opcion>, val padre: Menu?, val ayuda: String? = null,
		/** En la navegación web: dos dedos a la derecha eligen «el anterior» (Mayúsculas y la letra). */
		val atrasConDosDedos: Boolean = false,
	) {
		var pos = 0
	}

	private fun abrirMenu(m: Menu) {
		menu = m
		mostrar(m.titulo)
		val primera = m.opciones.firstOrNull()?.nombre.orEmpty()
		decir(listOfNotNull(m.titulo, m.ayuda, t.getString(R.string.pc_menu_posicion, primera, 1, m.opciones.size)).joinToString(". "))
	}

	private fun cerrarMenu() {
		menu = null
		mostrar(t.getString(if (practica) R.string.pc_rotulo_practica else R.string.pc_rotulo))
		decir(t.getString(R.string.pc_menu_cerrado))
	}

	private fun gestoMenu(m: Menu, g: GestoPc) {
		val n = m.opciones.size
		when {
			g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 1 && (g.direccion == DireccionBraille.DERECHA || g.direccion == DireccionBraille.ABAJO) -> {
				m.pos = (m.pos + 1) % n
				decirOpcion(m)
			}
			g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 1 -> {
				m.pos = (m.pos - 1 + n) % n
				decirOpcion(m)
			}
			g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 3 && g.direccion == DireccionBraille.ARRIBA -> {
				m.pos = 0
				decirOpcion(m)
			}
			g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 3 && g.direccion == DireccionBraille.ABAJO -> cerrarMenu()
			g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 2 && g.direccion == DireccionBraille.ARRIBA -> cerrarMenu()
			g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 2 && g.direccion == DireccionBraille.IZQUIERDA -> {
				val padre = m.padre
				if (padre == null) cerrarMenu() else abrirMenu(padre)
			}
			g.tipo == TipoGestoPc.TOQUE && g.dedos == 1 -> decirOpcion(m)
			(g.tipo == TipoGestoPc.DOBLE_TOQUE && g.dedos == 1) || (g.tipo == TipoGestoPc.DESLIZAR && g.dedos == 2 && (g.direccion == DireccionBraille.ABAJO || g.direccion == DireccionBraille.DERECHA)) -> {
				val o = m.opciones[m.pos]
				val sub = o.submenu
				if (sub != null) abrirMenu(sub())
				else {
					// Hacia atrás en la navegación web: dos dedos a la derecha es «el anterior» (Mayúsculas y la letra)
					if (g.tipo == TipoGestoPc.DESLIZAR && g.direccion == DireccionBraille.DERECHA && m.atrasConDosDedos) modificadores += TeclaVk.MAYUSCULAS
					menu = null
					mostrar(o.nombre)
					o.hacer()
				}
			}
			g.tipo == TipoGestoPc.DOBLE_TOQUE && g.dedos == 4 -> cerrar()
			g.tipo == TipoGestoPc.TOQUE && g.dedos == 4 -> estado()
			else -> decir(t.getString(R.string.pc_menu_ayuda))
		}
	}

	private fun decirOpcion(m: Menu) {
		val o = m.opciones[m.pos]
		mostrar(o.nombre)
		decir(t.getString(R.string.pc_menu_posicion, o.nombre, m.pos + 1, m.opciones.size))
	}

	/** Una opción que manda una combinación (se dice con su nombre y, si se pide, con las teclas). */
	private fun tecla(nombre: Int, vararg teclas: TeclaVk): Opcion {
		val n = t.getString(nombre)
		val etiqueta = t.getString(R.string.pc_opcion_con_teclas, n, GestosPc.nombreCombinacion(t, teclas.toList()))
		return Opcion(etiqueta) { mandar(n, teclas.toList()) }
	}

	/** Una tecla suelta, nombrada por sí misma. */
	private fun teclaSuelta(k: TeclaVk): Opcion {
		val n = GestosPc.nombreTecla(t, k)
		return Opcion(n) { mandar(n, listOf(k)) }
	}

	/** Según la disposición del teclado de NVDA (escritorio o portátil). */
	private fun nvda(nombre: Int, escritorio: List<TeclaVk>, portatil: List<TeclaVk>): Opcion {
		val n = t.getString(nombre)
		val teclas = if (this.portatil) portatil else escritorio
		return Opcion(t.getString(R.string.pc_opcion_con_teclas, n, GestosPc.nombreCombinacion(t, teclas))) { mandar(n, teclas) }
	}

	private fun raiz(): Menu = Menu(t.getString(R.string.pc_o_menu_ordenes), listOf(
		Opcion(t.getString(R.string.pc_c_windows), { menuWindows(actual()) }),
		Opcion(t.getString(R.string.pc_c_edicion), { menuEdicion(actual()) }),
		Opcion(t.getString(R.string.pc_c_nvda), { menuNvda(actual()) }),
		Opcion(t.getString(R.string.pc_c_objetos), { menuObjetos(actual()) }),
		Opcion(t.getString(R.string.pc_c_revision), { menuRevision(actual()) }),
		Opcion(t.getString(R.string.pc_c_web), { menuWeb(actual()) }),
		Opcion(t.getString(R.string.pc_c_teclas), { menuTeclas(actual()) }),
		Opcion(t.getString(R.string.pc_c_modificadores), { menuModificadores(actual()) }),
		Opcion(t.getString(R.string.pc_c_gestos), { menuGestos(actual()) }),
		Opcion(t.getString(R.string.pc_c_pantalla), { menuPantalla(actual()) }),
	), null, t.getString(R.string.pc_menu_ayuda))

	/** El menú de ahora, para que un submenú sepa a dónde volver. */
	private fun actual(): Menu? = menu

	private fun menuWindows(padre: Menu?) = Menu(t.getString(R.string.pc_c_windows), listOf(
		tecla(R.string.pc_w_inicio, TeclaVk.WINDOWS),
		tecla(R.string.pc_w_escritorio, TeclaVk.WINDOWS, TeclaVk.D),
		tecla(R.string.pc_w_explorador, TeclaVk.WINDOWS, TeclaVk.E),
		tecla(R.string.pc_w_ejecutar, TeclaVk.WINDOWS, TeclaVk.R),
		tecla(R.string.pc_w_buscar, TeclaVk.WINDOWS, TeclaVk.S),
		tecla(R.string.pc_w_configuracion, TeclaVk.WINDOWS, TeclaVk.I),
		tecla(R.string.pc_w_ventana_siguiente, TeclaVk.ALT, TeclaVk.TABULADOR),
		tecla(R.string.pc_w_ventana_anterior, TeclaVk.ALT, TeclaVk.MAYUSCULAS, TeclaVk.TABULADOR),
		tecla(R.string.pc_w_cerrar_ventana, TeclaVk.ALT, TeclaVk.F4),
		tecla(R.string.pc_w_menu_ventana, TeclaVk.ALT, TeclaVk.ESPACIO),
		tecla(R.string.pc_w_maximizar, TeclaVk.WINDOWS, TeclaVk.ARRIBA),
		tecla(R.string.pc_w_minimizar, TeclaVk.WINDOWS, TeclaVk.ABAJO),
		tecla(R.string.pc_w_barra_tareas, TeclaVk.WINDOWS, TeclaVk.T),
		tecla(R.string.pc_w_area_notificacion, TeclaVk.WINDOWS, TeclaVk.B),
		tecla(R.string.pc_w_notificaciones, TeclaVk.WINDOWS, TeclaVk.N),
		tecla(R.string.pc_w_pestana_siguiente, TeclaVk.CONTROL, TeclaVk.TABULADOR),
		tecla(R.string.pc_w_pestana_anterior, TeclaVk.CONTROL, TeclaVk.MAYUSCULAS, TeclaVk.TABULADOR),
		tecla(R.string.pc_w_cerrar_pestana, TeclaVk.CONTROL, TeclaVk.W),
		tecla(R.string.pc_w_captura, TeclaVk.WINDOWS, TeclaVk.MAYUSCULAS, TeclaVk.S),
		tecla(R.string.pc_w_bloquear, TeclaVk.WINDOWS, TeclaVk.L),
		Opcion(t.getString(R.string.nvda_menu_sas)) { ctrlAltSupr() },
	), padre)

	private fun menuEdicion(padre: Menu?) = Menu(t.getString(R.string.pc_c_edicion), listOf(
		tecla(R.string.pc_e_copiar, TeclaVk.CONTROL, TeclaVk.C),
		tecla(R.string.pc_e_cortar, TeclaVk.CONTROL, TeclaVk.X),
		tecla(R.string.pc_e_pegar, TeclaVk.CONTROL, TeclaVk.V),
		tecla(R.string.pc_e_deshacer, TeclaVk.CONTROL, TeclaVk.Z),
		tecla(R.string.pc_e_rehacer, TeclaVk.CONTROL, TeclaVk.Y),
		tecla(R.string.pc_e_seleccionar_todo, TeclaVk.CONTROL, TeclaVk.A),
		tecla(R.string.pc_e_guardar, TeclaVk.CONTROL, TeclaVk.S),
		tecla(R.string.pc_e_buscar, TeclaVk.CONTROL, TeclaVk.F),
		tecla(R.string.pc_e_nuevo, TeclaVk.CONTROL, TeclaVk.N),
		tecla(R.string.pc_e_abrir, TeclaVk.CONTROL, TeclaVk.O),
		tecla(R.string.pc_e_imprimir, TeclaVk.CONTROL, TeclaVk.P),
		tecla(R.string.pc_e_palabra_anterior, TeclaVk.CONTROL, TeclaVk.IZQUIERDA),
		tecla(R.string.pc_e_palabra_siguiente, TeclaVk.CONTROL, TeclaVk.DERECHA),
		tecla(R.string.pc_e_inicio_linea, TeclaVk.INICIO),
		tecla(R.string.pc_e_fin_linea, TeclaVk.FIN),
		tecla(R.string.pc_e_inicio_documento, TeclaVk.CONTROL, TeclaVk.INICIO),
		tecla(R.string.pc_e_fin_documento, TeclaVk.CONTROL, TeclaVk.FIN),
		tecla(R.string.pc_e_pagina_anterior, TeclaVk.REPAG),
		tecla(R.string.pc_e_pagina_siguiente, TeclaVk.AVPAG),
		tecla(R.string.pc_e_sel_caracter_izq, TeclaVk.MAYUSCULAS, TeclaVk.IZQUIERDA),
		tecla(R.string.pc_e_sel_caracter_der, TeclaVk.MAYUSCULAS, TeclaVk.DERECHA),
		tecla(R.string.pc_e_sel_palabra_izq, TeclaVk.CONTROL, TeclaVk.MAYUSCULAS, TeclaVk.IZQUIERDA),
		tecla(R.string.pc_e_sel_palabra_der, TeclaVk.CONTROL, TeclaVk.MAYUSCULAS, TeclaVk.DERECHA),
		tecla(R.string.pc_e_sel_linea_arriba, TeclaVk.MAYUSCULAS, TeclaVk.ARRIBA),
		tecla(R.string.pc_e_sel_linea_abajo, TeclaVk.MAYUSCULAS, TeclaVk.ABAJO),
		tecla(R.string.pc_e_sel_inicio_linea, TeclaVk.MAYUSCULAS, TeclaVk.INICIO),
		tecla(R.string.pc_e_sel_fin_linea, TeclaVk.MAYUSCULAS, TeclaVk.FIN),
		tecla(R.string.pc_e_borrar_palabra, TeclaVk.CONTROL, TeclaVk.RETROCESO),
		teclaSuelta(TeclaVk.RETROCESO),
		teclaSuelta(TeclaVk.SUPRIMIR),
	), padre)

	private fun menuNvda(padre: Menu?) = Menu(t.getString(R.string.pc_c_nvda), listOf(
		nvda(R.string.pc_n_leer_todo, listOf(TeclaVk.NVDA, TeclaVk.ABAJO), listOf(TeclaVk.NVDA, TeclaVk.A)),
		nvda(R.string.pc_n_leer_linea, listOf(TeclaVk.NVDA, TeclaVk.ARRIBA), listOf(TeclaVk.NVDA, TeclaVk.L)),
		tecla(R.string.pc_n_titulo, TeclaVk.NVDA, TeclaVk.T),
		tecla(R.string.pc_n_foco, TeclaVk.NVDA, TeclaVk.TABULADOR),
		tecla(R.string.pc_n_ventana, TeclaVk.NVDA, TeclaVk.B),
		nvda(R.string.pc_n_barra_estado, listOf(TeclaVk.NVDA, TeclaVk.FIN), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.FIN)),
		nvda(R.string.pc_n_seleccion, listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.ARRIBA), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.S)),
		tecla(R.string.pc_n_hora, TeclaVk.NVDA, TeclaVk.F12),
		Opcion(t.getString(R.string.pc_opcion_con_teclas, t.getString(R.string.pc_n_fecha), GestosPc.nombreCombinacion(t, listOf(TeclaVk.NVDA, TeclaVk.F12)))) {
			mandarDosVeces(t.getString(R.string.pc_n_fecha), listOf(TeclaVk.NVDA, TeclaVk.F12))
		},
		tecla(R.string.pc_n_portapapeles, TeclaVk.NVDA, TeclaVk.C),
		tecla(R.string.pc_n_formato, TeclaVk.NVDA, TeclaVk.F),
		tecla(R.string.pc_n_enlace, TeclaVk.NVDA, TeclaVk.K),
		tecla(R.string.pc_n_modo_voz, TeclaVk.NVDA, TeclaVk.S),
		tecla(R.string.pc_n_modo_exploracion, TeclaVk.NVDA, TeclaVk.ESPACIO),
		tecla(R.string.pc_n_lista_elementos, TeclaVk.NVDA, TeclaVk.F7),
		tecla(R.string.pc_n_menu, TeclaVk.NVDA, TeclaVk.N),
		tecla(R.string.pc_n_configuracion, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.G),
		tecla(R.string.pc_n_ayuda_entrada, TeclaVk.NVDA, TeclaVk.N1),
		tecla(R.string.pc_n_dejar_pasar, TeclaVk.NVDA, TeclaVk.F2),
		tecla(R.string.pc_n_sintetizador, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.S),
		tecla(R.string.pc_n_voz_siguiente, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.DERECHA),
		tecla(R.string.pc_n_voz_anterior, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.IZQUIERDA),
		tecla(R.string.pc_n_voz_subir, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.ARRIBA),
		tecla(R.string.pc_n_voz_bajar, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.ABAJO),
		tecla(R.string.pc_n_salir, TeclaVk.NVDA, TeclaVk.Q),
	), padre)

	private fun menuObjetos(padre: Menu?) = Menu(t.getString(R.string.pc_c_objetos), listOf(
		nvda(R.string.pc_ob_actual, listOf(TeclaVk.NVDA, TeclaVk.NUM5), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.O)),
		nvda(R.string.pc_ob_contenedor, listOf(TeclaVk.NVDA, TeclaVk.NUM8), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.ARRIBA)),
		nvda(R.string.pc_ob_anterior, listOf(TeclaVk.NVDA, TeclaVk.NUM4), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.IZQUIERDA)),
		nvda(R.string.pc_ob_siguiente, listOf(TeclaVk.NVDA, TeclaVk.NUM6), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.DERECHA)),
		nvda(R.string.pc_ob_primero, listOf(TeclaVk.NVDA, TeclaVk.NUM2), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.ABAJO)),
		nvda(R.string.pc_ob_activar, listOf(TeclaVk.NVDA, TeclaVk.NUM_INTRO), listOf(TeclaVk.NVDA, TeclaVk.INTRO)),
		nvda(R.string.pc_ob_al_foco, listOf(TeclaVk.NVDA, TeclaVk.NUM_MENOS), listOf(TeclaVk.NVDA, TeclaVk.RETROCESO)),
		nvda(R.string.pc_ob_foco_al_navegador, listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.NUM_MENOS), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.RETROCESO)),
		nvda(R.string.pc_ob_dimensiones, listOf(TeclaVk.NVDA, TeclaVk.NUM_PUNTO), listOf(TeclaVk.NVDA, TeclaVk.SUPRIMIR)),
		nvda(R.string.pc_ob_leer_todo_revision, listOf(TeclaVk.NUM_MAS), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.A)),
	), padre)

	private fun menuRevision(padre: Menu?) = Menu(t.getString(R.string.pc_c_revision), listOf(
		nvda(R.string.pc_r_linea_anterior, listOf(TeclaVk.NUM7), listOf(TeclaVk.NVDA, TeclaVk.ARRIBA)),
		nvda(R.string.pc_r_linea_actual, listOf(TeclaVk.NUM8), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.PUNTO)),
		nvda(R.string.pc_r_linea_siguiente, listOf(TeclaVk.NUM9), listOf(TeclaVk.NVDA, TeclaVk.ABAJO)),
		nvda(R.string.pc_r_palabra_anterior, listOf(TeclaVk.NUM4), listOf(TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.IZQUIERDA)),
		nvda(R.string.pc_r_palabra_actual, listOf(TeclaVk.NUM5), listOf(TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.PUNTO)),
		nvda(R.string.pc_r_palabra_siguiente, listOf(TeclaVk.NUM6), listOf(TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.DERECHA)),
		nvda(R.string.pc_r_caracter_anterior, listOf(TeclaVk.NUM1), listOf(TeclaVk.NVDA, TeclaVk.IZQUIERDA)),
		nvda(R.string.pc_r_caracter_actual, listOf(TeclaVk.NUM2), listOf(TeclaVk.NVDA, TeclaVk.PUNTO)),
		nvda(R.string.pc_r_caracter_siguiente, listOf(TeclaVk.NUM3), listOf(TeclaVk.NVDA, TeclaVk.DERECHA)),
		nvda(R.string.pc_r_principio, listOf(TeclaVk.MAYUSCULAS, TeclaVk.NUM7), listOf(TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.INICIO)),
		nvda(R.string.pc_r_final, listOf(TeclaVk.MAYUSCULAS, TeclaVk.NUM9), listOf(TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.FIN)),
		nvda(R.string.pc_r_leer_todo, listOf(TeclaVk.NUM_MAS), listOf(TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.A)),
		nvda(R.string.pc_r_copiar_desde, listOf(TeclaVk.NVDA, TeclaVk.F9), listOf(TeclaVk.NVDA, TeclaVk.F9)),
		nvda(R.string.pc_r_copiar_hasta, listOf(TeclaVk.NVDA, TeclaVk.F10), listOf(TeclaVk.NVDA, TeclaVk.F10)),
		tecla(R.string.pc_r_modo_revision_siguiente, TeclaVk.NVDA, TeclaVk.AVPAG),
		tecla(R.string.pc_r_modo_revision_anterior, TeclaVk.NVDA, TeclaVk.REPAG),
	), padre)

	/** Las letras rápidas del modo exploración: un doble toque va al siguiente; dos dedos a la derecha, al anterior. */
	private fun menuWeb(padre: Menu?) = Menu(t.getString(R.string.pc_c_web), listOf(
		letra(R.string.pc_web_encabezado, TeclaVk.H),
		letra(R.string.pc_web_encabezado_1, TeclaVk.N1), letra(R.string.pc_web_encabezado_2, TeclaVk.N2), letra(R.string.pc_web_encabezado_3, TeclaVk.N3),
		letra(R.string.pc_web_encabezado_4, TeclaVk.N4), letra(R.string.pc_web_encabezado_5, TeclaVk.N5), letra(R.string.pc_web_encabezado_6, TeclaVk.N6),
		letra(R.string.pc_web_enlace, TeclaVk.K),
		letra(R.string.pc_web_enlace_no_visitado, TeclaVk.U),
		letra(R.string.pc_web_enlace_visitado, TeclaVk.V),
		letra(R.string.pc_web_boton, TeclaVk.B),
		letra(R.string.pc_web_campo_formulario, TeclaVk.F),
		letra(R.string.pc_web_campo_edicion, TeclaVk.E),
		letra(R.string.pc_web_casilla, TeclaVk.X),
		letra(R.string.pc_web_cuadro_combinado, TeclaVk.C),
		letra(R.string.pc_web_boton_opcion, TeclaVk.R),
		letra(R.string.pc_web_lista, TeclaVk.L),
		letra(R.string.pc_web_elemento_lista, TeclaVk.I),
		letra(R.string.pc_web_tabla, TeclaVk.T),
		letra(R.string.pc_web_region, TeclaVk.D),
		letra(R.string.pc_web_grafico, TeclaVk.G),
		letra(R.string.pc_web_objeto, TeclaVk.O),
		letra(R.string.pc_web_separador, TeclaVk.S),
		letra(R.string.pc_web_cita, TeclaVk.Q),
		letra(R.string.pc_web_marco, TeclaVk.M),
		letra(R.string.pc_web_parrafo, TeclaVk.P),
		letra(R.string.pc_web_texto, TeclaVk.N),
		letra(R.string.pc_web_anotacion, TeclaVk.A),
		letra(R.string.pc_web_error, TeclaVk.W),
		tecla(R.string.pc_web_fuera_contenedor, TeclaVk.COMA),
		tecla(R.string.pc_web_buscar, TeclaVk.NVDA, TeclaVk.CONTROL, TeclaVk.F),
		tecla(R.string.pc_web_buscar_siguiente, TeclaVk.NVDA, TeclaVk.F3),
		tecla(R.string.pc_web_buscar_anterior, TeclaVk.NVDA, TeclaVk.MAYUSCULAS, TeclaVk.F3),
		tecla(R.string.pc_web_celda_derecha, TeclaVk.CONTROL, TeclaVk.ALT, TeclaVk.DERECHA),
		tecla(R.string.pc_web_celda_izquierda, TeclaVk.CONTROL, TeclaVk.ALT, TeclaVk.IZQUIERDA),
		tecla(R.string.pc_web_celda_abajo, TeclaVk.CONTROL, TeclaVk.ALT, TeclaVk.ABAJO),
		tecla(R.string.pc_web_celda_arriba, TeclaVk.CONTROL, TeclaVk.ALT, TeclaVk.ARRIBA),
		tecla(R.string.pc_web_lista_elementos, TeclaVk.NVDA, TeclaVk.F7),
		tecla(R.string.pc_web_modo, TeclaVk.NVDA, TeclaVk.ESPACIO),
		tecla(R.string.pc_web_refrescar, TeclaVk.NVDA, TeclaVk.F5),
		tecla(R.string.pc_web_direccion, TeclaVk.CONTROL, TeclaVk.L),
		tecla(R.string.pc_web_atras, TeclaVk.ALT, TeclaVk.IZQUIERDA),
		tecla(R.string.pc_web_adelante, TeclaVk.ALT, TeclaVk.DERECHA),
	), padre, t.getString(R.string.pc_web_ayuda), atrasConDosDedos = true)

	private fun letra(nombre: Int, k: TeclaVk): Opcion {
		val n = t.getString(nombre)
		return Opcion(t.getString(R.string.pc_opcion_con_teclas, n, GestosPc.nombreTecla(t, k))) { mandar(n, listOf(k)) }
	}

	private fun menuTeclas(padre: Menu?) = Menu(t.getString(R.string.pc_c_teclas), listOf(
		Opcion(t.getString(R.string.pc_k_letras), { Menu(t.getString(R.string.pc_k_letras), TeclaVk.entries.filter { it.ordinal < 26 }.map { teclaSuelta(it) }, actual()) }),
		Opcion(t.getString(R.string.pc_k_numeros), { Menu(t.getString(R.string.pc_k_numeros), TeclaVk.entries.filter { it.ordinal in 26..35 }.map { teclaSuelta(it) }, actual()) }),
		Opcion(t.getString(R.string.pc_k_funcion), { Menu(t.getString(R.string.pc_k_funcion), listOf(TeclaVk.F1, TeclaVk.F2, TeclaVk.F3, TeclaVk.F4, TeclaVk.F5, TeclaVk.F6, TeclaVk.F7, TeclaVk.F8, TeclaVk.F9, TeclaVk.F10, TeclaVk.F11, TeclaVk.F12).map { teclaSuelta(it) }, actual()) }),
		Opcion(t.getString(R.string.pc_k_numerico), { Menu(t.getString(R.string.pc_k_numerico), listOf(TeclaVk.NUM0, TeclaVk.NUM1, TeclaVk.NUM2, TeclaVk.NUM3, TeclaVk.NUM4, TeclaVk.NUM5, TeclaVk.NUM6, TeclaVk.NUM7, TeclaVk.NUM8, TeclaVk.NUM9, TeclaVk.NUM_MAS, TeclaVk.NUM_MENOS, TeclaVk.NUM_POR, TeclaVk.NUM_DIVIDIR, TeclaVk.NUM_INTRO).map { teclaSuelta(it) }, actual()) }),
	) + listOf(
		TeclaVk.ESCAPE, TeclaVk.TABULADOR, TeclaVk.INTRO, TeclaVk.ESPACIO, TeclaVk.RETROCESO, TeclaVk.SUPRIMIR, TeclaVk.INSERTAR,
		TeclaVk.INICIO, TeclaVk.FIN, TeclaVk.REPAG, TeclaVk.AVPAG, TeclaVk.IZQUIERDA, TeclaVk.DERECHA, TeclaVk.ARRIBA, TeclaVk.ABAJO,
		TeclaVk.WINDOWS, TeclaVk.APLICACIONES, TeclaVk.BLOQ_MAYUS, TeclaVk.IMPRIMIR_PANTALLA, TeclaVk.PAUSA,
	).map { teclaSuelta(it) }, padre)

	private fun menuModificadores(padre: Menu?) = Menu(t.getString(R.string.pc_c_modificadores), listOf(
		TeclaVk.CONTROL, TeclaVk.ALT, TeclaVk.MAYUSCULAS, TeclaVk.WINDOWS, TeclaVk.NVDA,
	).map { m -> Opcion(GestosPc.nombreTecla(t, m)) { preparar(m) } } + listOf(
		Opcion(t.getString(R.string.pc_m_soltar)) {
			modificadores.clear()
			mostrar("")
			decir(t.getString(R.string.pc_m_soltados))
		},
		Opcion(t.getString(R.string.pc_m_mantener_alt)) { mantener(TeclaVk.ALT) },
		Opcion(t.getString(R.string.pc_m_soltar_alt)) { soltar(TeclaVk.ALT) },
	), padre, t.getString(R.string.pc_m_ayuda))

	/** Deja Alt pulsado (para recorrer las ventanas con Alt+Tab de una en una, como con la mano). */
	private fun mantener(k: TeclaVk) {
		val n = GestosPc.nombreTecla(t, k)
		mostrar(t.getString(R.string.pc_m_mantenida, n))
		decir(t.getString(R.string.pc_m_mantenida, n))
		if (!practica) cliente?.tecla(k, true)
	}

	private fun soltar(k: TeclaVk) {
		val n = GestosPc.nombreTecla(t, k)
		decir(t.getString(R.string.pc_m_soltada, n))
		if (!practica) cliente?.tecla(k, false)
	}

	private fun menuGestos(padre: Menu?) = Menu(t.getString(R.string.pc_c_gestos), GestosPc.TABLA.map { (g, o) ->
		Opcion(t.getString(R.string.pc_gesto_y_orden, GestosPc.nombreGesto(t, g), t.getString(o.nombre))) { hacer(o) }
	}, padre, t.getString(R.string.pc_gestos_ayuda))

	private fun menuPantalla(padre: Menu?) = Menu(t.getString(R.string.pc_c_pantalla), listOf(
		Opcion(t.getString(OrdenPc.DICTAR.nombre)) { dictar() },
		Opcion(t.getString(OrdenPc.ESCRIBIR.nombre)) { escribir() },
		Opcion(t.getString(OrdenPc.PEGAR_COPIADO.nombre)) { pegarPortapapeles() },
		Opcion(t.getString(if (cliente?.silenciado == true) R.string.nvda_menu_voz_si else R.string.nvda_menu_voz_no)) { alternarVozPc() },
		Opcion(t.getString(if (portatil) R.string.pc_p_disposicion_a_escritorio else R.string.pc_p_disposicion_a_portatil)) {
			val nueva = if (portatil) Ajustes.NVDA_ESCRITORIO else Ajustes.NVDA_PORTATIL
			lector.ajustes.nvdaDisposicion = nueva
			decir(t.getString(if (nueva == Ajustes.NVDA_PORTATIL) R.string.pc_disposicion_portatil else R.string.pc_disposicion_escritorio))
		},
		Opcion(t.getString(if (lector.ajustes.nvdaDecirOrdenes) R.string.pc_p_no_decir_ordenes else R.string.pc_p_decir_ordenes)) {
			val nuevo = !lector.ajustes.nvdaDecirOrdenes
			lector.ajustes.nvdaDecirOrdenes = nuevo
			decir(t.getString(if (nuevo) R.string.pc_p_decir_ordenes_si else R.string.pc_p_decir_ordenes_no))
		},
		Opcion(t.getString(R.string.pc_p_superficie_braille)) {
			val cl = cliente
			if (practica || cl == null) decir(t.getString(R.string.pc_sin_conexion))
			else {
				cerrar()
				lector.manejador.postDelayed({ if (cl.estado == ClienteNvda.Estado.CONECTADO) lector.superficie.abrir(SuperficiePc(lector, cl) {}) }, 300)
			}
		},
		Opcion(t.getString(OrdenPc.ESTADO.nombre)) { estado() },
		Opcion(t.getString(R.string.nvda_menu_desconectar)) {
			cliente?.terminar()
			cerrar()
		},
		Opcion(t.getString(OrdenPc.QUITAR.nombre)) { cerrar() },
	), padre)

	private fun ctrlAltSupr() {
		val n = t.getString(R.string.nvda_menu_sas)
		mostrar(n)
		if (practica) decir(n) else {
			decir(n)
			cliente?.ctrlAltSupr()
		}
	}
}
