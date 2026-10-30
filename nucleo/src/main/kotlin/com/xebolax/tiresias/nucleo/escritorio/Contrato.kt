package com.xebolax.tiresias.nucleo.escritorio

/**
 * Lo que comparten el lector y el escritorio, dos APK con la misma firma. El escritorio
 * va aparte para que un fallo suyo no se lleve la voz y al revés; hablan por la API de
 * Tiresias (`docs/api.md`), con un mensaje de acciones en lista blanca: nunca una acción
 * libre, porque cualquier fallo del escritorio no debe poder hacer lo que quiera con el lector.
 */
object Contrato {
	const val PAQUETE_ESCRITORIO = "com.xebolax.tiresias.escritorio"
	const val PAQUETE_LECTOR = "com.xebolax.tiresias"
	const val PAQUETE_LECTOR_PRUEBAS = "com.xebolax.tiresias.pruebas"
	const val ACCION_API = "com.xebolax.tiresias.API"

	/** El permiso de firma con el que el lector le pide cosas al escritorio. */
	const val PERMISO_ESCRITORIO = "com.xebolax.tiresias.permission.ESCRITORIO"

	// Las actividades del escritorio que abre el lector
	const val ACTIVIDAD_SENIOR = "$PAQUETE_ESCRITORIO.ui.ActividadSenior"
	const val ACTIVIDAD_INICIO = "$PAQUETE_ESCRITORIO.ui.ActividadInicio"
	const val ALIAS_INICIO = "$PAQUETE_ESCRITORIO.Inicio"

	/** El lector le dice al escritorio que deje de ser Inicio (desactiva su alias). */
	const val RECIBIR_SALIDA = "com.xebolax.tiresias.escritorio.SALIR_DE_INICIO"

	// Mensajes de la API (`what`), a continuación de los que ya tiene
	const val PEDIR_ACCION = 10
	const val PEDIR_RECUENTOS = 11
	const val PRUEBA_EMPEZAR = 12
	const val PRUEBA_CONFIRMAR = 13
	const val PEDIR_ANUNCIO = 14

	// Claves de los datos
	const val CLAVE_ACCION = "accion"
	const val CLAVE_SUSPENDIDO = "suspendido"
	const val CLAVE_AYUDA = "ayuda_remota"
	const val CLAVE_SENIOR = "senior"
	const val CLAVE_RECUENTOS = "recuentos"
	const val CLAVE_SEGUNDOS = "segundos"

	/** Los segundos que dura la prueba de hacer predeterminado el escritorio: si no se confirma, se vuelve al del sistema. */
	const val SEGUNDOS_PRUEBA = 60

	/** Lo único que el escritorio puede pedirle al lector. */
	val ACCIONES_PERMITIDAS = setOf(
		"MENU_TIRESIAS", "ORDENES_VOZ", "BUSQUEDA_BRAILLE", "ESTADO_SISTEMA", "RESUMEN_AVISOS",
		"HISTORIAL_NOTIFICACIONES", "SOS", "PEDIR_AYUDA", "ESCRITORIO_SISTEMA", "DICTADO", "ESTADO_Y_AVISOS",
	)
}
