package com.xebolax.tiresias.nucleo

/** Plantillas en español para las pruebas (las mismas que `res/values/strings_lectura.xml`). */
object TextosPrueba : Plantillas {
	private val frases = mapOf(
		Frase.ROL_BOTON to "botón", Frase.ROL_BOTON_ALTERNAR to "botón de alternar", Frase.ROL_CASILLA to "casilla",
		Frase.ROL_INTERRUPTOR to "interruptor", Frase.ROL_OPCION to "opción", Frase.ROL_CAMPO_TEXTO to "campo de texto",
		Frase.ROL_CAMPO_CONTRASENA to "campo de contraseña", Frase.ROL_DESLIZADOR to "deslizador",
		Frase.ROL_BARRA_PROGRESO to "barra de progreso", Frase.ROL_DESPLEGABLE to "desplegable", Frase.ROL_LISTA to "lista",
		Frase.ROL_CUADRICULA to "cuadrícula", Frase.ROL_TABLA to "tabla", Frase.ROL_PESTANA to "pestaña",
		Frase.ROL_ENLACE to "enlace", Frase.ROL_IMAGEN to "imagen",
		Frase.MARCADO to "marcado", Frase.NO_MARCADO to "no marcado", Frase.PARCIAL to "parcialmente marcado",
		Frase.ACTIVADO to "activado", Frase.DESACTIVADO to "desactivado", Frase.SELECCIONADO to "seleccionado",
		Frase.NO_DISPONIBLE to "no disponible", Frase.EXPANDIDO to "expandido", Frase.CONTRAIDO to "contraído",
		Frase.OBLIGATORIO to "obligatorio", Frase.EDITANDO to "editando", Frase.CARGANDO to "cargando",
		Frase.ERROR to "error: %1\$s", Frase.ENCABEZADO to "encabezado", Frase.ENCABEZADO_NIVEL to "encabezado de nivel %1\$d",
		Frase.POSICION to "%1\$d de %2\$d", Frase.FILA_COLUMNA to "fila %1\$d, columna %2\$d", Frase.POR_CIENTO to "%1\$d por ciento",
		Frase.VALOR_DE_RANGO to "%1\$s, entre %2\$s y %3\$s", Frase.SIN_NOMBRE to "sin nombre", Frase.SIN_NOMBRE_ID to "%1\$s, sin nombre",
		Frase.TIENE_ACCIONES to "con acciones", Frase.PANEL to "%1\$s", Frase.PISTA_PULSAR to "Toca dos veces para activar.",
		Frase.PISTA_EDITAR to "Toca dos veces para escribir.", Frase.PISTA_ALTERNAR to "Toca dos veces para cambiar.",
		Frase.PISTA_ACCIONES to "Toca con tres dedos para ver sus acciones.", Frase.PISTA_ACCIONES_GESTO to "%1\$s para ver sus acciones.",
		Frase.PISTA_LEVANTAR to "Levanta el dedo para activar.",
		Frase.PISTA_ACCIONES_ARRIBA_ABAJO to "Desliza arriba o abajo para elegir una acción y toca dos veces para hacerla.",
	)

	override fun de(frase: Frase): String = frases[frase] ?: frase.name.lowercase()

	override fun plural(frase: FrasePlural, n: Int): String = when (frase) {
		FrasePlural.LISTA_ELEMENTOS -> if (n == 1) "lista con %1\$d elemento" else "lista con %1\$d elementos"
		FrasePlural.CUADRICULA_FILAS -> if (n == 1) "cuadrícula con %1\$d fila" else "cuadrícula con %1\$d filas"
		FrasePlural.COLUMNAS -> if (n == 1) "%1\$d columna" else "%1\$d columnas"
		FrasePlural.REPETIDO -> "%1\$s repetido %2\$d veces"
		FrasePlural.CARACTERES -> if (n == 1) "%1\$d carácter" else "%1\$d caracteres"
		FrasePlural.ENLACES -> if (n == 1) "con %1\$d enlace" else "con %1\$d enlaces"
		FrasePlural.FALTAS -> if (n == 1) "%1\$d falta de ortografía" else "%1\$d faltas de ortografía"
	}
}
