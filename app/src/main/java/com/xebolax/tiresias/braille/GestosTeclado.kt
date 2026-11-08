package com.xebolax.tiresias.braille

import android.content.Context
import com.xebolax.tiresias.R
import com.xebolax.tiresias.nucleo.Accion

/** Lo que puede hacer un gesto en el teclado braille en pantalla. El nombre de cada una está en `braille_accion_<nombre>`. */
enum class AccionTeclado {
	NINGUNA, BORRAR, BORRAR_PALABRA, ESPACIO, INTRO, CAMBIAR_TECLADO, BLOQUEAR_ORIENTACION,
	MENU, DICTADO, EMOJIS, PORTAPAPELES, SUGERENCIAS, LEER_CAMPO, CALIBRAR, QUITAR_CALIBRACION,
	BUSCAR_EMOJI, EDICION, CURSOR_ATRAS, CURSOR_ADELANTE, CAMBIAR_UNIDAD, SELECCIONAR, SELECCIONAR_TODO, COPIAR, CORTAR, PEGAR, DESHACER, REHACER, IR_PRINCIPIO, IR_FINAL, LEER_SELECCION, MENU_LECTOR, ESCONDER_TECLADO;

	fun nombre(c: Context): String = c.getString(when (this) {
		NINGUNA -> R.string.braille_accion_ninguna
		BORRAR -> R.string.braille_accion_borrar
		BORRAR_PALABRA -> R.string.braille_accion_borrar_palabra
		ESPACIO -> R.string.braille_accion_espacio
		INTRO -> R.string.braille_accion_intro
		CAMBIAR_TECLADO -> R.string.braille_accion_cambiar_teclado
		BLOQUEAR_ORIENTACION -> R.string.braille_accion_bloquear_orientacion
		MENU -> R.string.braille_accion_menu
		DICTADO -> R.string.braille_accion_dictado
		EMOJIS -> R.string.braille_accion_emojis
		PORTAPAPELES -> R.string.braille_accion_portapapeles
		SUGERENCIAS -> R.string.braille_accion_sugerencias
		LEER_CAMPO -> R.string.braille_accion_leer_campo
		CALIBRAR -> R.string.braille_accion_calibrar
		QUITAR_CALIBRACION -> R.string.braille_accion_quitar_calibracion
		BUSCAR_EMOJI -> R.string.braille_accion_buscar_emoji
		EDICION -> R.string.braille_accion_edicion
		CURSOR_ATRAS -> R.string.braille_accion_cursor_atras
		CURSOR_ADELANTE -> R.string.braille_accion_cursor_adelante
		CAMBIAR_UNIDAD -> R.string.braille_accion_cambiar_unidad
		SELECCIONAR -> R.string.braille_accion_seleccionar
		SELECCIONAR_TODO -> R.string.braille_accion_seleccionar_todo
		COPIAR -> R.string.braille_accion_copiar
		CORTAR -> R.string.braille_accion_cortar
		PEGAR -> R.string.braille_accion_pegar
		DESHACER -> R.string.braille_accion_deshacer
		REHACER -> R.string.braille_accion_rehacer
		IR_PRINCIPIO -> R.string.braille_accion_ir_principio
		IR_FINAL -> R.string.braille_accion_ir_final
		LEER_SELECCION -> R.string.braille_accion_leer_seleccion
		MENU_LECTOR -> R.string.braille_accion_menu_lector
		ESCONDER_TECLADO -> R.string.braille_accion_esconder_teclado
	})
}

/**
 * Los dos menús del teclado braille, como el menú de Tiresias y el circular:
 * los dos se eligen y se ordenan en Ajustes, Braille.
 *
 * - El **menú del teclado** (un dedo hacia arriba): lo de escribir.
 * - El **menú del lector** (tres dedos hacia arriba): acciones de Tiresias que
 *   valen con el teclado puesto, porque solo dicen algo o cambian un ajuste
 *   (la cortina, la hora y la batería, la voz, la música...). Nada que abra una
 *   ventana o recorra la pantalla: la superficie del teclado lo tapa y se
 *   quedaría sin poder tocarse.
 */
object MenusTeclado {
	/** El menú del teclado de fábrica (lo que no viene a cuento, como las sugerencias sin una mención, no sale). */
	val TECLADO_POR_DEFECTO: List<AccionTeclado> = listOf(
		AccionTeclado.EMOJIS, AccionTeclado.BUSCAR_EMOJI, AccionTeclado.PORTAPAPELES, AccionTeclado.SUGERENCIAS, AccionTeclado.DICTADO,
		AccionTeclado.LEER_CAMPO, AccionTeclado.EDICION, AccionTeclado.BORRAR_PALABRA, AccionTeclado.CALIBRAR, AccionTeclado.QUITAR_CALIBRACION,
		AccionTeclado.BLOQUEAR_ORIENTACION, AccionTeclado.MENU_LECTOR, AccionTeclado.ESCONDER_TECLADO, AccionTeclado.CAMBIAR_TECLADO,
	)

	/** Lo que se puede poner en el menú del teclado: todo menos nada y el propio menú. */
	val TECLADO_POSIBLES: List<AccionTeclado> = AccionTeclado.entries.filter { it != AccionTeclado.NINGUNA && it != AccionTeclado.MENU }

	/** Las acciones del lector que valen con el teclado braille puesto. */
	val LECTOR_PERMITIDAS: List<com.xebolax.tiresias.nucleo.Accion> = listOf(
		Accion.CORTINA, Accion.ESTADO_SISTEMA, Accion.ESTADO_Y_AVISOS, Accion.RESUMEN_AVISOS, Accion.REPETIR, Accion.LEER_PORTAPAPELES,
		Accion.COPIAR_ULTIMO, Accion.TRADUCIR_ULTIMO, Accion.PAUSAR_VOZ, Accion.VELOCIDAD_MAS, Accion.VELOCIDAD_MENOS, Accion.VOLUMEN_MAS,
		Accion.VOLUMEN_MENOS, Accion.TONO_MAS, Accion.TONO_MENOS, Accion.PUNTUACION_SIGUIENTE, Accion.VOZ_SIGUIENTE, Accion.IDIOMA_SIGUIENTE,
		Accion.CANAL_VOZ, Accion.SALIDA_AUDIO, Accion.PERFIL_SIGUIENTE, Accion.MULTIMEDIA, Accion.PISTA_SIGUIENTE, Accion.PISTA_ANTERIOR,
		Accion.LLAMADA_CONTESTAR, Accion.LLAMADA_RECHAZAR, Accion.PRIMERO_BRAILLE,
	)

	/** El menú del lector de fábrica. */
	val LECTOR_POR_DEFECTO: List<com.xebolax.tiresias.nucleo.Accion> = listOf(
		Accion.CORTINA, Accion.ESTADO_SISTEMA, Accion.RESUMEN_AVISOS, Accion.REPETIR, Accion.LEER_PORTAPAPELES, Accion.VELOCIDAD_MAS,
		Accion.VELOCIDAD_MENOS, Accion.VOLUMEN_MAS, Accion.VOLUMEN_MENOS, Accion.MULTIMEDIA,
	)
}

/**
 * Qué hace cada gesto del teclado braille. Los de fábrica son los de siempre
 * (un dedo a la izquierda borra y a la derecha escribe un espacio; dos dedos
 * hacia abajo, Intro, y hacia arriba, cambiar de teclado; tres dedos hacia
 * arriba, bloquear la orientación) más el menú del teclado (un dedo hacia
 * arriba), esconder el teclado (un dedo hacia abajo; las sugerencias de mención si se acaba de avisar de
 * ellas), borrar la palabra
 * (dos dedos a la izquierda), dictar (dos dedos a la derecha) y recorrer lo
 * escrito (tres dedos a los lados mueven el cursor; tres hacia abajo cambian la
 * unidad: carácter, palabra o línea). Los gestos de un punto (Can Talk) no hacen nada
 * de fábrica: si no se les pone una acción, hacen lo del gesto de un dedo.
 */
object GestosTeclado {
	val POR_DEFECTO: Map<String, AccionTeclado> = mapOf(
		"1_IZQUIERDA" to AccionTeclado.BORRAR,
		"1_DERECHA" to AccionTeclado.ESPACIO,
		"1_ARRIBA" to AccionTeclado.MENU,
		"1_ABAJO" to AccionTeclado.ESCONDER_TECLADO,
		"2_ABAJO" to AccionTeclado.INTRO,
		"2_ARRIBA" to AccionTeclado.CAMBIAR_TECLADO,
		"2_IZQUIERDA" to AccionTeclado.BORRAR_PALABRA,
		"2_DERECHA" to AccionTeclado.DICTADO,
		"3_ARRIBA" to AccionTeclado.MENU_LECTOR,
		"3_ABAJO" to AccionTeclado.CAMBIAR_UNIDAD,
		"3_IZQUIERDA" to AccionTeclado.CURSOR_ATRAS,
		"3_DERECHA" to AccionTeclado.CURSOR_ADELANTE,
	)

	/** Todos los gestos que se pueden cambiar: los de uno, dos y tres dedos y los de un punto (6 × 4). */
	val TODOS: List<GestoBraille> =
		(1..3).flatMap { d -> DireccionBraille.entries.map { GestoBraille(d, it) } } +
			(1..6).flatMap { p -> DireccionBraille.entries.map { GestoBraille(1, it, p) } }

	/** Lo que se guarda delante de una acción del lector puesta en un gesto del teclado («3_ABAJO=L.CORTINA»). */
	const val DEL_LECTOR = "L."

	/**
	 * Las acciones del lector puestas en gestos del teclado (para tenerlas a mano sin abrir ningún menú): solo
	 * las que valen con el teclado puesto ([MenusTeclado.LECTOR_PERMITIDAS]).
	 */
	fun leerLector(texto: String): Map<String, com.xebolax.tiresias.nucleo.Accion> = texto.split(';').mapNotNull { par ->
		val (g, a) = par.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
		val gesto = GestoBraille.deClave(g.trim()) ?: return@mapNotNull null
		val nombre = a.trim().takeIf { it.startsWith(DEL_LECTOR) }?.removePrefix(DEL_LECTOR) ?: return@mapNotNull null
		val accion = MenusTeclado.LECTOR_PERMITIDAS.firstOrNull { it.name == nombre } ?: return@mapNotNull null
		gesto.clave to accion
	}.toMap()

	/** La acción del lector de [g] (la de su punto si tiene una; si no, la del gesto de un dedo), o null. */
	fun accionLector(mapa: Map<String, com.xebolax.tiresias.nucleo.Accion>, g: GestoBraille): com.xebolax.tiresias.nucleo.Accion? =
		mapa[g.clave] ?: if (g.punto > 0) mapa[g.generico.clave] else null

	/** «p3_IZQUIERDA=BORRAR_PALABRA;1_ARRIBA=DICTADO» sobre los de fábrica; lo que no se entiende se salta. */
	fun leer(texto: String): Map<String, AccionTeclado> {
		val cambios = texto.split(';').mapNotNull { par ->
			val (g, a) = par.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
			val gesto = GestoBraille.deClave(g.trim()) ?: return@mapNotNull null
			val accion = AccionTeclado.entries.firstOrNull { it.name == a.trim() } ?: return@mapNotNull null
			gesto.clave to accion
		}.toMap()
		return POR_DEFECTO + cambios
	}

	/** Solo lo que cambia respecto a los de fábrica. */
	fun escribir(mapa: Map<String, AccionTeclado>, lector: Map<String, com.xebolax.tiresias.nucleo.Accion> = emptyMap()): String =
		(mapa.filter { (g, a) -> g !in lector && (POR_DEFECTO[g] ?: AccionTeclado.NINGUNA) != a }.entries.map { "${it.key}=${it.value.name}" } +
			lector.entries.map { "${it.key}=$DEL_LECTOR${it.value.name}" }).joinToString(";")

	/** La acción de [g]: la del gesto de su punto si tiene una; si no, la del gesto de un dedo. */
	fun accion(mapa: Map<String, AccionTeclado>, g: GestoBraille): AccionTeclado {
		if (g.punto > 0) mapa[g.clave]?.takeIf { it != AccionTeclado.NINGUNA }?.let { return it }
		return mapa[g.generico.clave] ?: AccionTeclado.NINGUNA
	}

	/** «Un dedo hacia la izquierda», «Desde el punto 3 hacia arriba», «Dos dedos hacia abajo». */
	fun nombre(c: Context, g: GestoBraille): String {
		val direccion = c.getString(when (g.direccion) {
			DireccionBraille.IZQUIERDA -> R.string.braille_dir_izquierda
			DireccionBraille.DERECHA -> R.string.braille_dir_derecha
			DireccionBraille.ARRIBA -> R.string.braille_dir_arriba
			DireccionBraille.ABAJO -> R.string.braille_dir_abajo
		})
		return when {
			g.punto > 0 -> c.getString(R.string.braille_gesto_punto, g.punto, direccion)
			else -> c.resources.getQuantityString(R.plurals.braille_gesto_dedos, g.dedos, g.dedos, direccion)
		}
	}
}
