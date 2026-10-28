package com.xebolax.tiresias.nucleo

/**
 * El papel de un elemento de la pantalla, como lo dice Tiresias.
 *
 * Se deduce del nombre de clase del nodo (como hace TalkBack en `Role.java`),
 * de su descripción de rol y de sus propiedades. [NINGUNO] es un elemento sin
 * papel que decir (texto suelto, contenedor).
 */
enum class Rol {
	NINGUNO,
	BOTON,
	BOTON_IMAGEN,
	BOTON_ALTERNAR,
	CASILLA,
	INTERRUPTOR,
	OPCION,
	CAMPO_TEXTO,
	DESLIZADOR,
	BARRA_PROGRESO,
	DESPLEGABLE,
	LISTA,
	CUADRICULA,
	TABLA,
	ELEMENTO_LISTA,
	CELDA,
	PESTANA,
	LISTA_PESTANAS,
	PAGINADOR,
	ENLACE,
	IMAGEN,
	MENU,
	ELEMENTO_MENU,
	BARRA_HERRAMIENTAS,
	DIALOGO,
	ALERTA,
	DESPLAZABLE,
	WEB,
	TEXTO,
	SELECTOR_FECHA,
	SELECTOR_HORA,
	SELECTOR_NUMERO,
	CHIP,
	VIDEO,
	MAPA,
	TECLA,
	CALENDARIO,
	EDITOR_VALOR,
	REGION,
	NAVEGACION,
	ESTADO,
}

/** Estado de una casilla, un interruptor o una opción (triestado en Android 16 o posterior). */
enum class Marcado { NO_MARCABLE, MARCADO, NO_MARCADO, PARCIAL }

/** Expandido o contraído (Android 16 añade el estado explícito; antes se deduce de las acciones). */
enum class Expansion { NINGUNA, EXPANDIDO, CONTRAIDO, EXPANDIDO_PARCIAL }

/** Un valor dentro de un rango: deslizadores y barras de progreso. */
data class Rango(
	val minimo: Float,
	val maximo: Float,
	val actual: Float,
	/** Tipo de rango de Android: 0 entero, 1 decimal, 2 por ciento. */
	val tipo: Int = 0,
	/** Texto del valor si la app lo da (stateDescription o el del nodo): manda sobre el número. */
	val texto: String? = null,
	val indeterminado: Boolean = false,
) {
	/** El valor en por ciento entre el mínimo y el máximo (0 si el rango no es válido). */
	val porCiento: Int
		get() = if (maximo > minimo) (((actual - minimo) * 100f) / (maximo - minimo)).let { Math.round(it) }.coerceIn(0, 100) else 0
}

/** Dónde está el elemento dentro de su colección (lista, cuadrícula, tabla). */
data class Posicion(
	/** Fila (o índice en una lista de una columna), empezando en 1; 0 si no se sabe. */
	val fila: Int = 0,
	val columna: Int = 0,
	val totalFilas: Int = 0,
	val totalColumnas: Int = 0,
	/** Encabezado de la fila y de la columna en una tabla, si lo hay. */
	val encabezadoFila: String? = null,
	val encabezadoColumna: String? = null,
	/** Si es una celda de encabezado de tabla. */
	val esEncabezado: Boolean = false,
)

/**
 * Lo que Tiresias sabe de un elemento de la pantalla para decirlo.
 *
 * Es una foto inmutable, sin referencias a Android: el servicio la construye a
 * partir del nodo de accesibilidad y el [Compositor] la convierte en frase.
 */
data class Elemento(
	/** El nombre: descripción del contenido o, si no hay, el texto visible (o el de sus hijos). */
	val nombre: String = "",
	val rol: Rol = Rol.NINGUNO,
	/** Lo escrito en un campo de texto (el nombre es su etiqueta). Vacío si no hay nada. */
	val texto: String = "",
	/** Descripción de rol que da la app (roleDescription): sustituye al nombre del [rol]. */
	val descripcionRol: String? = null,
	/** Descripción de estado que da la app (stateDescription): sustituye al estado calculado. */
	val estado: String? = null,
	val marcado: Marcado = Marcado.NO_MARCABLE,
	val seleccionado: Boolean = false,
	val activo: Boolean = true,
	val expansion: Expansion = Expansion.NINGUNA,
	val obligatorio: Boolean = false,
	/** Texto de error del campo (setError), con lo que hay que corregir. */
	val error: String? = null,
	val encabezado: Boolean = false,
	/** Nivel de encabezado (1 a 6) si se sabe (web); 0 si no. */
	val nivelEncabezado: Int = 0,
	/** Pista del campo (hintText) o texto de ayuda. */
	val pista: String? = null,
	/** Descripción complementaria (Android 16) o información sobre herramientas. */
	val complementaria: String? = null,
	val rango: Rango? = null,
	val posicion: Posicion? = null,
	val pulsable: Boolean = false,
	val mantenible: Boolean = false,
	val editable: Boolean = false,
	val contrasena: Boolean = false,
	/**
	 * Datos sensibles: una contraseña o lo que la app marca así (Android 14,
	 * `isAccessibilityDataSensitive`: banca, códigos). Tiresias lo lee, pero nunca
	 * lo da a un complemento, a la IA ni al reconocimiento de texto.
	 */
	val sensible: Boolean = false,
	/** Si tiene acciones propias de la app (además de pulsar y mantener). */
	val tieneAcciones: Boolean = false,
	/** Cuántos enlaces hay dentro de su texto («Más información» en medio de una frase). */
	val enlaces: Int = 0,
	/** Cuántas faltas de ortografía (o de gramática) marca el corrector en lo escrito en un campo. */
	val faltas: Int = 0,
	/** Si es un campo en el que se está escribiendo ahora (tiene el foco de entrada). */
	val escribiendo: Boolean = false,
	/** Identificador de la vista (com.app:id/boton_enviar), para los elementos sin nombre. */
	val identificador: String? = null,
	/** Etiqueta que el usuario puso a este elemento: manda sobre el nombre. */
	val etiquetaPersonal: String? = null,
	/** Enlace ya visitado (web). */
	val visitado: Boolean = false,
	/** Título del panel o de la ventana a la que pertenece, si es su primer elemento. */
	val tituloPanel: String? = null,
)
