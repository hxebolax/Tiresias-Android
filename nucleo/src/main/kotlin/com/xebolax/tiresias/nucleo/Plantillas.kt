package com.xebolax.tiresias.nucleo

/**
 * Las frases que dice Tiresias, por clave. El núcleo no sabe de idiomas: la app
 * le da los textos de sus recursos (res/values*), así cada traducción es solo
 * un archivo de cadenas. Las plantillas llevan marcadores de formato
 * (`%1$s`, `%1$d`) y nunca se construyen uniendo trozos de frase.
 */
enum class Frase {
	// Roles
	ROL_BOTON, ROL_BOTON_ALTERNAR, ROL_CASILLA, ROL_INTERRUPTOR, ROL_OPCION, ROL_CAMPO_TEXTO,
	ROL_CAMPO_CONTRASENA, ROL_DESLIZADOR, ROL_BARRA_PROGRESO, ROL_DESPLEGABLE, ROL_LISTA, ROL_CUADRICULA,
	ROL_TABLA, ROL_PESTANA, ROL_LISTA_PESTANAS, ROL_PAGINADOR, ROL_ENLACE, ROL_IMAGEN, ROL_MENU,
	ROL_ELEMENTO_MENU, ROL_BARRA_HERRAMIENTAS, ROL_DIALOGO, ROL_ALERTA, ROL_DESPLAZABLE, ROL_WEB,
	ROL_SELECTOR_FECHA, ROL_SELECTOR_HORA, ROL_SELECTOR_NUMERO, ROL_CHIP, ROL_VIDEO, ROL_MAPA, ROL_TECLA,
	ROL_CALENDARIO, ROL_EDITOR_VALOR, ROL_REGION, ROL_NAVEGACION, ROL_ESTADO,

	// Estados
	MARCADO, NO_MARCADO, PARCIAL, ACTIVADO, DESACTIVADO, SELECCIONADO, NO_DISPONIBLE,
	EXPANDIDO, CONTRAIDO, EXPANDIDO_PARCIAL, OBLIGATORIO, ENLACE_VISITADO, EDITANDO, PULSADO, NO_PULSADO,
	CARGANDO,

	// Con marcadores
	/** `%1$s`: el texto del error. */
	ERROR,
	ENCABEZADO,
	/** `%1$d`: nivel del encabezado. */
	ENCABEZADO_NIVEL,
	/** `%1$d` de `%2$d`. */
	POSICION,
	/** Fila `%1$d`, columna `%2$d`. */
	FILA_COLUMNA,
	/** `%1$d` por ciento. */
	POR_CIENTO,
	/** `%1$s`: el valor; `%2$s`: el mínimo; `%3$s`: el máximo. */
	VALOR_DE_RANGO,
	/** «sin nombre», para un control que no tiene ni nombre ni identificador. */
	SIN_NOMBRE,
	/** `%1$s`: el identificador legible de un elemento sin nombre. */
	SIN_NOMBRE_ID,
	TIENE_ACCIONES,
	/** `%1$s`: el título del panel. */
	PANEL,
	VACIO,
	/** `%1$s`: texto con espacios al principio o al final que importan. */
	ESPACIO,

	// Pistas de uso
	PISTA_PULSAR, PISTA_MANTENER, PISTA_ALTERNAR, PISTA_EDITAR, PISTA_DESLIZADOR, PISTA_EXPANDIR,
	PISTA_CONTRAER, PISTA_ACCIONES,
	/** «%1$s para ver sus acciones» (con el gesto que tiene el usuario para el menú de acciones). */
	PISTA_ACCIONES_GESTO,
	/** Con «activar al levantar el dedo» en toda la pantalla. */
	PISTA_LEVANTAR,
	/** Con «arriba y abajo eligen las acciones»: cómo se eligen y se hacen. */
	PISTA_ACCIONES_ARRIBA_ABAJO,
}

/** Frases con plural (la regla de cada idioma la pone la app con sus recursos de plurales). */
enum class FrasePlural {
	/** Lista con `%1$d` elementos. */
	LISTA_ELEMENTOS,
	/** Cuadrícula con `%1$d` filas. */
	CUADRICULA_FILAS,
	/** `%1$d` columnas. */
	COLUMNAS,
	/** `%1$s` repetido `%2$d` veces (para «=====»). */
	REPETIDO,
	/** `%1$d` caracteres. */
	CARACTERES,
	/** «con `%1$d` enlaces» (los que hay dentro del texto de un elemento). */
	ENLACES,
	/** «`%1$d` faltas de ortografía» (las que marca el corrector en un campo). */
	FALTAS,
}

/** De dónde saca el núcleo los textos: en la app, de los recursos; en las pruebas, de un mapa. */
interface Plantillas {
	/** La plantilla de [frase], sin rellenar. */
	fun de(frase: Frase): String

	/** La plantilla de [frase] para la cantidad [n], sin rellenar. */
	fun plural(frase: FrasePlural, n: Int): String

	/** [frase] rellenada con [argumentos]. */
	fun formato(frase: Frase, vararg argumentos: Any): String = de(frase).format(*argumentos)

	fun formatoPlural(frase: FrasePlural, n: Int, vararg argumentos: Any): String = plural(frase, n).format(*argumentos)
}
