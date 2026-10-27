package com.xebolax.tiresias.nucleo

/** Las partes de lo que se dice de un elemento, en el orden que elige el usuario. */
enum class Parte { NOMBRE, ROL, ESTADO, VALOR, POSICION, ERROR, COMPLEMENTARIA, PISTA }

/** Nivel general de verbosidad: cada uno es un punto de partida que el usuario ajusta. */
enum class NivelVerbosidad { ALTA, MEDIA, BAJA, PERSONALIZADA }

/**
 * Qué dice Tiresias de cada elemento y en qué orden.
 *
 * TalkBack solo deja elegir entre dos órdenes; Tiresias deja ordenar todas las
 * partes y quitar cada una. Se guarda por perfil (general o por app).
 */
data class Verbosidad(
	val orden: List<Parte> = ORDEN_NORMAL,
	val decirRol: Boolean = true,
	val decirEstado: Boolean = true,
	val decirPosicion: Boolean = true,
	/** En las tablas, los encabezados de fila y columna de cada celda. */
	val decirEncabezadosTabla: Boolean = true,
	/** Los encabezados de la tabla después de la fila y la columna (si no, antes). */
	val encabezadosDespues: Boolean = false,
	/** Decir «fila 2, columna 3» en las tablas. */
	val decirFilaColumna: Boolean = true,
	val decirEncabezado: Boolean = true,
	val decirComplementaria: Boolean = true,
	/** Pistas de uso («Toca dos veces para activar»): tras una pausa o nunca. */
	val decirPistas: Boolean = true,
	/** Aviso de que el elemento tiene acciones propias (palabra; el sonido lo decide la app). */
	val decirAcciones: Boolean = true,
	/** Decir el identificador de los elementos sin nombre (botón «enviar» en vez de «botón sin nombre»). */
	val decirIdentificador: Boolean = true,
	/** El título del panel al entrar en él. */
	val decirPanel: Boolean = true,
	/** Que los deslizadores digan el por ciento si la app no da el valor. */
	val porCientoRango: Boolean = true,
	val separador: String = ", ",
) {
	companion object {
		val ORDEN_NORMAL = listOf(Parte.NOMBRE, Parte.VALOR, Parte.ROL, Parte.ESTADO, Parte.POSICION, Parte.ERROR, Parte.COMPLEMENTARIA, Parte.PISTA)
		/** Estado primero, como piden muchas personas para las filas de lista. */
		val ORDEN_ESTADO_PRIMERO = listOf(Parte.ESTADO, Parte.NOMBRE, Parte.VALOR, Parte.ROL, Parte.POSICION, Parte.ERROR, Parte.COMPLEMENTARIA, Parte.PISTA)
		/** Rol primero (como el otro orden de TalkBack). */
		val ORDEN_ROL_PRIMERO = listOf(Parte.ROL, Parte.NOMBRE, Parte.VALOR, Parte.ESTADO, Parte.POSICION, Parte.ERROR, Parte.COMPLEMENTARIA, Parte.PISTA)

		fun de(nivel: NivelVerbosidad): Verbosidad = when (nivel) {
			NivelVerbosidad.ALTA, NivelVerbosidad.PERSONALIZADA -> Verbosidad()
			NivelVerbosidad.MEDIA -> Verbosidad(decirPistas = false, decirComplementaria = false)
			NivelVerbosidad.BAJA -> Verbosidad(decirPistas = false, decirComplementaria = false, decirPosicion = false, decirRol = false, decirPanel = false)
		}
	}
}
