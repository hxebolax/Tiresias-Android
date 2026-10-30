package com.xebolax.tiresias.braille

import android.content.Context
import com.xebolax.tiresias.R

/** Lo que puede hacer un gesto en el teclado braille en pantalla. El nombre de cada una está en `braille_accion_<nombre>`. */
enum class AccionTeclado {
	NINGUNA, BORRAR, BORRAR_PALABRA, ESPACIO, INTRO, CAMBIAR_TECLADO, BLOQUEAR_ORIENTACION,
	MENU, DICTADO, EMOJIS, PORTAPAPELES, SUGERENCIAS, LEER_CAMPO, CALIBRAR, QUITAR_CALIBRACION;

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
	})
}

/**
 * Qué hace cada gesto del teclado braille. Los de fábrica son los de siempre
 * (un dedo a la izquierda borra y a la derecha escribe un espacio; dos dedos
 * hacia abajo, Intro, y hacia arriba, cambiar de teclado; tres dedos hacia
 * arriba, bloquear la orientación) más el menú del teclado (un dedo hacia
 * arriba), las sugerencias de mención (un dedo hacia abajo) y borrar la palabra
 * (dos dedos a la izquierda). Los gestos de un punto (Can Talk) no hacen nada
 * de fábrica: si no se les pone una acción, hacen lo del gesto de un dedo.
 */
object GestosTeclado {
	val POR_DEFECTO: Map<String, AccionTeclado> = mapOf(
		"1_IZQUIERDA" to AccionTeclado.BORRAR,
		"1_DERECHA" to AccionTeclado.ESPACIO,
		"1_ARRIBA" to AccionTeclado.MENU,
		"1_ABAJO" to AccionTeclado.SUGERENCIAS,
		"2_ABAJO" to AccionTeclado.INTRO,
		"2_ARRIBA" to AccionTeclado.CAMBIAR_TECLADO,
		"2_IZQUIERDA" to AccionTeclado.BORRAR_PALABRA,
		"2_DERECHA" to AccionTeclado.NINGUNA,
		"3_ARRIBA" to AccionTeclado.BLOQUEAR_ORIENTACION,
		"3_ABAJO" to AccionTeclado.NINGUNA,
		"3_IZQUIERDA" to AccionTeclado.NINGUNA,
		"3_DERECHA" to AccionTeclado.NINGUNA,
	)

	/** Todos los gestos que se pueden cambiar: los de uno, dos y tres dedos y los de un punto (6 × 4). */
	val TODOS: List<GestoBraille> =
		(1..3).flatMap { d -> DireccionBraille.entries.map { GestoBraille(d, it) } } +
			(1..6).flatMap { p -> DireccionBraille.entries.map { GestoBraille(1, it, p) } }

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
	fun escribir(mapa: Map<String, AccionTeclado>): String =
		mapa.filter { (g, a) -> (POR_DEFECTO[g] ?: AccionTeclado.NINGUNA) != a }.entries.joinToString(";") { "${it.key}=${it.value.name}" }

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
