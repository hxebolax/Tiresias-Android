package com.xebolax.tiresias.nucleo

/**
 * Los gestos que Tiresias reconoce. Los de un dedo existen en todas las
 * versiones; los de varios dedos, desde Android 11 (o con el reconocedor propio
 * desde Android 13). La app traduce los identificadores de Android a estos.
 */
enum class Gesto(val dedos: Int) {
	// Un dedo: deslizar
	ARRIBA(1), ABAJO(1), IZQUIERDA(1), DERECHA(1),
	// Un dedo: ida y vuelta
	ARRIBA_ABAJO(1), ABAJO_ARRIBA(1), IZQUIERDA_DERECHA(1), DERECHA_IZQUIERDA(1),
	// Un dedo: en ángulo
	ARRIBA_IZQUIERDA(1), ARRIBA_DERECHA(1), ABAJO_IZQUIERDA(1), ABAJO_DERECHA(1),
	IZQUIERDA_ARRIBA(1), IZQUIERDA_ABAJO(1), DERECHA_ARRIBA(1), DERECHA_ABAJO(1),
	// Un dedo: toques (los gestiona el sistema salvo con el reconocedor propio)
	DOBLE_TOQUE(1), DOBLE_TOQUE_MANTENIDO(1),
	// Dos dedos
	TOQUE_2(2), DOBLE_TOQUE_2(2), TRIPLE_TOQUE_2(2), DOBLE_TOQUE_MANTENIDO_2(2), TRIPLE_TOQUE_MANTENIDO_2(2), TOQUE_MANTENIDO_2(2),
	ARRIBA_2(2), ABAJO_2(2), IZQUIERDA_2(2), DERECHA_2(2),
	// Tres dedos
	TOQUE_3(3), DOBLE_TOQUE_3(3), TRIPLE_TOQUE_3(3), DOBLE_TOQUE_MANTENIDO_3(3), TRIPLE_TOQUE_MANTENIDO_3(3), TOQUE_MANTENIDO_3(3),
	ARRIBA_3(3), ABAJO_3(3), IZQUIERDA_3(3), DERECHA_3(3),
	// Cuatro dedos
	TOQUE_4(4), DOBLE_TOQUE_4(4), TRIPLE_TOQUE_4(4), DOBLE_TOQUE_MANTENIDO_4(4), TRIPLE_TOQUE_MANTENIDO_4(4),
	ARRIBA_4(4), ABAJO_4(4), IZQUIERDA_4(4), DERECHA_4(4),
	// Sensor de huella (donde exista)
	HUELLA_ARRIBA(0), HUELLA_ABAJO(0), HUELLA_IZQUIERDA(0), HUELLA_DERECHA(0),
	// Teclas de volumen (las reconoce Tiresias)
	VOLUMEN_ARRIBA_MANTENIDO(0), VOLUMEN_ABAJO_MANTENIDO(0), VOLUMEN_AMBAS(0), VOLUMEN_ARRIBA_TRIPLE(0), VOLUMEN_ABAJO_TRIPLE(0),
}

/**
 * Todo lo que Tiresias puede hacer y se puede asignar a un gesto o a una tecla.
 * El nombre de cada acción está en los recursos (`accion_<nombre en minúsculas>`).
 */
enum class Accion {
	NINGUNA,
	// Navegación
	SIGUIENTE, ANTERIOR, PRIMERO, ULTIMO,
	CONTROL_SIGUIENTE, CONTROL_ANTERIOR, SELECTOR_SIGUIENTE, SELECTOR_ANTERIOR,
	VENTANA_SIGUIENTE, VENTANA_ANTERIOR, CONTENEDOR_SIGUIENTE, CONTENEDOR_ANTERIOR,
	CUADRICULA_ARRIBA, CUADRICULA_ABAJO, CUADRICULA_IZQUIERDA, CUADRICULA_DERECHA,
	DONDE_ESTOY,
	// Activar y desplazar
	ACTIVAR, MANTENER, DESPLAZAR_ADELANTE, DESPLAZAR_ATRAS,
	DESPLAZAR_ARRIBA, DESPLAZAR_ABAJO, DESPLAZAR_IZQUIERDA, DESPLAZAR_DERECHA,
	// Sistema
	INICIO, ATRAS, RECIENTES, NOTIFICACIONES, AJUSTES_RAPIDOS, PANTALLA_DIVIDIDA, BLOQUEAR, CAPTURA,
	ENCENDIDO, TODAS_APPS,
	// Menús y búsqueda
	MENU_TIRESIAS, MENU_ACCIONES, MENU_EDICION, BUSCAR, LISTA_ELEMENTOS, HISTORIAL_HABLA, HISTORIAL_NOTIFICACIONES,
	// Lectura
	LEER_DESDE_ARRIBA, LEER_DESDE_AQUI, REPETIR, DELETREAR, DELETREO_FONETICO, LEER_TITULO, ESTADO_SISTEMA,
	LEER_PORTAPAPELES, COPIAR_ULTIMO,
	// Voz
	PAUSAR_VOZ, ALTERNAR_VOZ, VELOCIDAD_MAS, VELOCIDAD_MENOS, VOLUMEN_MAS, VOLUMEN_MENOS,
	TONO_MAS, TONO_MENOS, PUNTUACION_SIGUIENTE, VERBOSIDAD_SIGUIENTE, VOZ_SIGUIENTE, IDIOMA_SIGUIENTE,
	// Edición
	COPIAR, CORTAR, PEGAR, SELECCIONAR_TODO, MODO_SELECCION, DESHACER, REHACER,
	CURSOR_INICIO, CURSOR_FINAL,
	// Visión e IA
	DESCRIBIR_IA, PANTALLA_IA, PREGUNTAR_IA, OCR_ELEMENTO, OCR_PANTALLA, ETIQUETAR, CORTINA,
	// Multimedia y otros
	MULTIMEDIA, DICTADO, ORDENES_VOZ, PASAR_GESTO, TOQUE_DIRECTO,
	// Tiresias
	AJUSTES_TIRESIAS, AYUDA_ENTRADA, TUTORIAL, SUSPENDER, PERFIL_SIGUIENTE, BRAILLE_TECLADO, PRIMERO_BRAILLE,
	// IA que mira la pantalla: describir lo que pasa en un vídeo o un juego, y hacer algo con confirmación
	DESCRIPCION_CONTINUA, ACTUAR_IA,
	// Ir al principio o al final de la lista entera (desplazándola hasta el tope)
	LISTA_PRINCIPIO, LISTA_FINAL,
	// Subir y bajar el valor del deslizador enfocado
	VALOR_MAS, VALOR_MENOS,
	// Traducir el elemento al idioma de Tiresias con la IA
	TRADUCIR_IA,
	// Por dónde suenan las voces incluidas: sistema, altavoz o auriculares
	SALIDA_AUDIO,
	// Ir directamente al siguiente o al anterior de un tipo, sin cambiar el control de lectura (Backtalk 3)
	ENCABEZADO_SIGUIENTE, ENCABEZADO_ANTERIOR, ENLACE_SIGUIENTE, ENLACE_ANTERIOR,
	ACTIVABLE_SIGUIENTE, ACTIVABLE_ANTERIOR, BOTON_SIGUIENTE, BOTON_ANTERIOR,
	CAMPO_SIGUIENTE, CAMPO_ANTERIOR, CASILLA_SIGUIENTE, CASILLA_ANTERIOR,
	LISTA_SIGUIENTE, LISTA_ANTERIOR, TABLA_SIGUIENTE, TABLA_ANTERIOR,
	REGION_SIGUIENTE, REGION_ANTERIOR, IMAGEN_SIGUIENTE, IMAGEN_ANTERIOR,
	// En esta app, activar siempre con un toque de verdad (apps que no atienden el clic de accesibilidad: Backtalk 159)
	CLIC_DIRECTO,
	// Menú circular: deslizar hacia la opción y levantar el dedo (Backtalk 36)
	MENU_CIRCULAR,
	// Volver a poner la exploración táctil, si Android la deja «atascada» (Backtalk 194)
	REINICIAR_TACTIL,
	// Lo copiado por Tiresias: elegir uno de los últimos, y añadir lo último dicho a lo copiado (Backtalk 287)
	HISTORIAL_PORTAPAPELES, COPIAR_ANADIENDO,
	// Copiar todos los elementos de la lista enfocada, uno por línea (Jieshuo)
	COPIAR_LISTA,
	// Los toques van a las apps, como sin lector, y Tiresias sigue diciendo lo que pasa (Backtalk 293)
	AYUDA_OTRA_PERSONA,
	// El texto reconocido de la pantalla, como elementos que se recorren y se pulsan en su sitio (Prudence, Jieshuo)
	PANTALLA_VIRTUAL,
	// Modo senior: a Inicio siempre, diciendo dónde y la hora (BlindShell: mantener Atrás); qué se puede hacer aquí;
	// y pedir ayuda (SOS) a un contacto, con una cuenta atrás que se puede cancelar
	INICIO_SEGURO, QUE_PUEDO_HACER, SOS,
	// Vigilar un elemento y avisar cuando cambia (Talk Forward); ir al elemento número N de la lista;
	// leer los códigos QR y de barras de la pantalla; y el resumen de los avisos de las últimas horas
	VIGILAR, IR_A_POSICION, LEER_QR, RESUMEN_AVISOS,
	APAGAR_SCRIPTS, ORDENES_COMPLEMENTOS, MODO_SEGURO,
}

/** Lo que puede decir el gesto de estado («Hora, fecha y batería»), en el orden que se elija. */
enum class ParteEstado { HORA, FECHA, BATERIA, CONEXION, NOTIFICACIONES, NO_MOLESTAR, TIMBRE, ESPACIO }

/** Las listas que el usuario puede elegir y ordenar (Ajustes, Navegación). */
object Listas {
	/** El menú de Tiresias de fábrica. */
	val MENU_TIRESIAS = listOf(
		Accion.LEER_DESDE_ARRIBA, Accion.LEER_DESDE_AQUI, Accion.REPETIR, Accion.DELETREAR, Accion.COPIAR_ULTIMO,
		Accion.LISTA_ELEMENTOS, Accion.BUSCAR, Accion.MENU_ACCIONES, Accion.MENU_EDICION, Accion.ETIQUETAR,
		Accion.DESCRIBIR_IA, Accion.PANTALLA_IA, Accion.PREGUNTAR_IA, Accion.ACTUAR_IA, Accion.DESCRIPCION_CONTINUA, Accion.TRADUCIR_IA,
		Accion.OCR_ELEMENTO, Accion.OCR_PANTALLA, Accion.PANTALLA_VIRTUAL,
		Accion.HISTORIAL_HABLA, Accion.HISTORIAL_NOTIFICACIONES, Accion.LEER_PORTAPAPELES, Accion.HISTORIAL_PORTAPAPELES,
		Accion.COPIAR_LISTA, Accion.ESTADO_SISTEMA,
		Accion.CORTINA, Accion.ALTERNAR_VOZ, Accion.PERFIL_SIGUIENTE, Accion.AYUDA_ENTRADA, Accion.AYUDA_OTRA_PERSONA, Accion.TUTORIAL,
		Accion.PRIMERO_BRAILLE, Accion.ORDENES_COMPLEMENTOS, Accion.AJUSTES_TIRESIAS, Accion.SUSPENDER,
	)

	/** El menú circular de fábrica: ocho opciones, de arriba en el sentido de las agujas del reloj. */
	val MENU_CIRCULAR = listOf(
		Accion.LEER_DESDE_ARRIBA, Accion.REPETIR, Accion.LISTA_ELEMENTOS, Accion.BUSCAR,
		Accion.MENU_ACCIONES, Accion.COPIAR_ULTIMO, Accion.ESTADO_SISTEMA, Accion.AJUSTES_TIRESIAS,
	)

	/** El menú de Tiresias del modo senior: pocas cosas y las de cada día. */
	val MENU_SENIOR = listOf(
		Accion.ESTADO_SISTEMA, Accion.REPETIR, Accion.LEER_DESDE_ARRIBA, Accion.QUE_PUEDO_HACER,
		Accion.RESUMEN_AVISOS, Accion.HISTORIAL_NOTIFICACIONES, Accion.ORDENES_VOZ, Accion.INICIO_SEGURO, Accion.SOS,
	)

	/** Lo que dice «Hora, fecha y batería» de fábrica. */
	val ESTADO = listOf(ParteEstado.HORA, ParteEstado.FECHA, ParteEstado.BATERIA)

	/** Lo que no tiene sentido en el menú (se hace con gestos o es la salida de emergencia). */
	val FUERA_DEL_MENU = setOf(Accion.NINGUNA, Accion.MENU_TIRESIAS, Accion.MODO_SEGURO, Accion.SIGUIENTE, Accion.ANTERIOR, Accion.ACTIVAR, Accion.MANTENER)

	/** «LEER_DESDE_ARRIBA,REPETIR,...» → la lista (lo que no existe se salta; vacío: [porDefecto]). */
	fun <T : Enum<T>> leer(texto: String, valores: Array<T>, porDefecto: List<T>): List<T> {
		if (texto.isBlank()) return porDefecto
		val porNombre = valores.associateBy { it.name }
		return texto.split(',').mapNotNull { porNombre[it.trim()] }.distinct()
	}

	fun escribir(lista: List<Enum<*>>): String = lista.joinToString(",") { it.name }
}

/**
 * Qué acción hace cada gesto. El mapa por defecto es compatible con TalkBack
 * (`pref_shortcut_*_default` de TalkBack 16.2), para quien viene de él, con lo
 * que TalkBack no asigna aprovechado para funciones de Tiresias. El usuario
 * cambia lo que quiera, en general y por app ([MapaGestos.mas]).
 */
class MapaGestos(private val asignaciones: Map<Gesto, Accion>) {

	operator fun get(g: Gesto): Accion = asignaciones[g] ?: Accion.NINGUNA

	val todas: Map<Gesto, Accion> get() = asignaciones

	/** Este mapa con [cambios] por encima (el de una app sobre el general). */
	fun mas(cambios: Map<Gesto, Accion>): MapaGestos = MapaGestos(asignaciones + cambios)

	/** Los gestos que hacen [accion]. */
	fun gestosDe(accion: Accion): List<Gesto> = asignaciones.filterValues { it == accion }.keys.toList()

	/** «ARRIBA=SELECTOR_ANTERIOR;ABAJO=SELECTOR_SIGUIENTE» (solo lo que cambia respecto al mapa por defecto). */
	fun escribir(base: MapaGestos = POR_DEFECTO): String =
		asignaciones.filter { (g, a) -> base[g] != a }.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

	companion object {
		/**
		 * Gestos que nunca se pueden quitar: la salida de emergencia. Ningún
		 * perfil, script ni ajuste los cambia (lección del fallo de «Ocultar
		 * pantalla» de One UI 8 y de los servicios que dejan el móvil inservible).
		 */
		val EMERGENCIA: Map<Gesto, Accion> = mapOf(
			Gesto.TRIPLE_TOQUE_MANTENIDO_4 to Accion.MODO_SEGURO,
		)

		val POR_DEFECTO = MapaGestos(
			mapOf(
				Gesto.DERECHA to Accion.SIGUIENTE,
				Gesto.IZQUIERDA to Accion.ANTERIOR,
				Gesto.ARRIBA to Accion.SELECTOR_ANTERIOR,
				Gesto.ABAJO to Accion.SELECTOR_SIGUIENTE,
				Gesto.IZQUIERDA_DERECHA to Accion.DESPLAZAR_ATRAS,
				Gesto.DERECHA_IZQUIERDA to Accion.DESPLAZAR_ADELANTE,
				Gesto.ARRIBA_ABAJO to Accion.CONTROL_ANTERIOR,
				Gesto.ABAJO_ARRIBA to Accion.CONTROL_SIGUIENTE,
				Gesto.ARRIBA_IZQUIERDA to Accion.INICIO,
				Gesto.ABAJO_IZQUIERDA to Accion.ATRAS,
				Gesto.ARRIBA_DERECHA to Accion.MENU_TIRESIAS,
				Gesto.ABAJO_DERECHA to Accion.MENU_TIRESIAS,
				Gesto.DERECHA_ABAJO to Accion.NOTIFICACIONES,
				Gesto.DERECHA_ARRIBA to Accion.ORDENES_VOZ,
				Gesto.IZQUIERDA_ABAJO to Accion.BUSCAR,
				Gesto.IZQUIERDA_ARRIBA to Accion.RECIENTES,
				Gesto.DOBLE_TOQUE to Accion.ACTIVAR,
				Gesto.DOBLE_TOQUE_MANTENIDO to Accion.MANTENER,
				Gesto.TOQUE_2 to Accion.PAUSAR_VOZ,
				Gesto.DOBLE_TOQUE_2 to Accion.MULTIMEDIA,
				Gesto.TRIPLE_TOQUE_2 to Accion.LEER_DESDE_AQUI,
				Gesto.DOBLE_TOQUE_MANTENIDO_2 to Accion.MODO_SELECCION,
				Gesto.TRIPLE_TOQUE_MANTENIDO_2 to Accion.ALTERNAR_VOZ,
				Gesto.TOQUE_MANTENIDO_2 to Accion.REPETIR,
				Gesto.ARRIBA_2 to Accion.DESPLAZAR_ARRIBA,
				Gesto.ABAJO_2 to Accion.DESPLAZAR_ABAJO,
				Gesto.IZQUIERDA_2 to Accion.DESPLAZAR_IZQUIERDA,
				Gesto.DERECHA_2 to Accion.DESPLAZAR_DERECHA,
				Gesto.TOQUE_3 to Accion.MENU_ACCIONES,
				Gesto.DOBLE_TOQUE_3 to Accion.COPIAR,
				Gesto.TRIPLE_TOQUE_3 to Accion.PEGAR,
				Gesto.DOBLE_TOQUE_MANTENIDO_3 to Accion.CORTAR,
				Gesto.TOQUE_MANTENIDO_3 to Accion.BUSCAR,
				Gesto.TRIPLE_TOQUE_MANTENIDO_3 to Accion.LISTA_ELEMENTOS,
				Gesto.ARRIBA_3 to Accion.CONTROL_ANTERIOR,
				Gesto.ABAJO_3 to Accion.CONTROL_SIGUIENTE,
				Gesto.IZQUIERDA_3 to Accion.DESCRIBIR_IA,
				Gesto.DERECHA_3 to Accion.OCR_PANTALLA,
				Gesto.TOQUE_4 to Accion.ESTADO_SISTEMA,
				Gesto.DOBLE_TOQUE_4 to Accion.TUTORIAL,
				Gesto.TRIPLE_TOQUE_4 to Accion.CORTINA,
				Gesto.DOBLE_TOQUE_MANTENIDO_4 to Accion.PASAR_GESTO,
				Gesto.ARRIBA_4 to Accion.VENTANA_ANTERIOR,
				Gesto.ABAJO_4 to Accion.VENTANA_SIGUIENTE,
				Gesto.IZQUIERDA_4 to Accion.CONTENEDOR_ANTERIOR,
				Gesto.DERECHA_4 to Accion.CONTENEDOR_SIGUIENTE,
				Gesto.HUELLA_ARRIBA to Accion.CONTROL_ANTERIOR,
				Gesto.HUELLA_ABAJO to Accion.CONTROL_SIGUIENTE,
				Gesto.VOLUMEN_AMBAS to Accion.SUSPENDER,
			) + EMERGENCIA
		)

		/**
		 * Gestos sencillos, para empezar (CHI 2021: los gestos en ángulo y de ida y
		 * vuelta son los que más cuestan): lo de cada día con toques de dos y tres
		 * dedos y deslizar de tres. Los de ángulo siguen haciendo lo de siempre.
		 */
		val SENCILLO = POR_DEFECTO.mas(mapOf(
			Gesto.DOBLE_TOQUE_2 to Accion.ATRAS,
			Gesto.TRIPLE_TOQUE_2 to Accion.INICIO,
			Gesto.TOQUE_3 to Accion.MENU_TIRESIAS,
			Gesto.DOBLE_TOQUE_3 to Accion.MENU_ACCIONES,
			Gesto.TRIPLE_TOQUE_3 to Accion.LEER_DESDE_ARRIBA,
			Gesto.IZQUIERDA_3 to Accion.NOTIFICACIONES,
			Gesto.DERECHA_3 to Accion.RECIENTES,
			Gesto.ARRIBA_3 to Accion.CONTROL_ANTERIOR,
			Gesto.ABAJO_3 to Accion.CONTROL_SIGUIENTE,
		))

		/**
		 * Los gestos del modo senior (estudio de octubre de 2026: Rodrigues 2015,
		 * Leporini, Cáliz 2016, BlindShell): solo deslizar con un dedo en línea recta
		 * y el doble toque; lo demás, con las teclas de volumen, que no fallan con
		 * temblor ni con la piel seca. Los gestos en ángulo, de ida y vuelta y de
		 * varios dedos no hacen nada: así no hay sustos por un toque sin querer.
		 * Derecha e izquierda pasan de elemento (y la lista da la vuelta), arriba
		 * repite y abajo dice qué se puede hacer. Volumen: las dos a la vez, a
		 * Inicio; mantener arriba, la hora, la batería y los avisos; mantener abajo,
		 * el menú sencillo; tres veces arriba, pedir ayuda; tres veces abajo,
		 * hablarle. La salida de emergencia sigue como siempre.
		 */
		val SENIOR = MapaGestos(
			Gesto.entries.associateWith { Accion.NINGUNA } + mapOf(
				Gesto.DERECHA to Accion.SIGUIENTE,
				Gesto.IZQUIERDA to Accion.ANTERIOR,
				Gesto.ARRIBA to Accion.REPETIR,
				Gesto.ABAJO to Accion.QUE_PUEDO_HACER,
				Gesto.DOBLE_TOQUE to Accion.ACTIVAR,
				Gesto.DOBLE_TOQUE_MANTENIDO to Accion.MANTENER,
				Gesto.TOQUE_2 to Accion.PAUSAR_VOZ,
				Gesto.ARRIBA_2 to Accion.DESPLAZAR_ARRIBA,
				Gesto.ABAJO_2 to Accion.DESPLAZAR_ABAJO,
				Gesto.VOLUMEN_AMBAS to Accion.INICIO_SEGURO,
				Gesto.VOLUMEN_ARRIBA_MANTENIDO to Accion.ESTADO_SISTEMA,
				Gesto.VOLUMEN_ABAJO_MANTENIDO to Accion.MENU_TIRESIAS,
				Gesto.VOLUMEN_ARRIBA_TRIPLE to Accion.SOS,
				Gesto.VOLUMEN_ABAJO_TRIPLE to Accion.ORDENES_VOZ,
			) + EMERGENCIA
		)

		/** Lee lo que escribe [escribir]; lo que no se entiende se ignora. Los gestos de emergencia no se cambian. */
		fun leer(texto: String, base: MapaGestos = POR_DEFECTO): MapaGestos {
			val cambios = mutableMapOf<Gesto, Accion>()
			for (par in texto.split(';')) {
				val (g, a) = par.split('=').takeIf { it.size == 2 } ?: continue
				val gesto = runCatching { Gesto.valueOf(g.trim()) }.getOrNull() ?: continue
				val accion = runCatching { Accion.valueOf(a.trim()) }.getOrNull() ?: continue
				if (gesto !in EMERGENCIA) cambios[gesto] = accion
			}
			return base.mas(cambios)
		}
	}
}

/**
 * Los controles de lectura que se eligen con el selector (arriba y abajo los
 * recorren; con el rotor de dos dedos en Android 13+). Cada uno cambia lo que
 * hacen los deslizamientos arriba y abajo.
 */
enum class ControlLectura {
	CARACTERES, PALABRAS, LINEAS, PARRAFOS, ENCABEZADOS, ENLACES, CONTROLES, BOTONES, CAMPOS, CASILLAS,
	LISTAS, TABLAS, REGIONES, IMAGENES, VENTANAS, PREDETERMINADO, CUADRICULA, ACCIONES, EDICION,
	VELOCIDAD, VOLUMEN, TONO, PUNTUACION, IDIOMA, VERBOSIDAD, MOTOR, SALIDA,
	;

	/** Si es un control de movimiento por el texto o por la pantalla (los demás cambian un ajuste). */
	val esNavegacion: Boolean get() = this !in setOf(VELOCIDAD, VOLUMEN, TONO, PUNTUACION, IDIOMA, VERBOSIDAD, MOTOR, SALIDA, ACCIONES, EDICION)

	/** Los que solo tienen sentido dentro de una página web (Backtalk 222). */
	val soloWeb: Boolean get() = this == ENLACES || this == REGIONES

	companion object {
		val POR_DEFECTO = listOf(
			PREDETERMINADO, CARACTERES, PALABRAS, LINEAS, PARRAFOS, ENCABEZADOS, ENLACES, CONTROLES,
			CUADRICULA, VENTANAS, ACCIONES, EDICION, VELOCIDAD, VOLUMEN, PUNTUACION, IDIOMA,
		)
	}
}
