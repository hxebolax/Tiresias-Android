package com.xebolax.tiresias.base

import android.content.Context
import android.content.SharedPreferences
import com.xebolax.tiresias.nucleo.AjustesTexto
import com.xebolax.tiresias.nucleo.EstiloTeclado
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.nucleo.MapaTeclas
import com.xebolax.tiresias.nucleo.ModoEmojis
import com.xebolax.tiresias.nucleo.ModoMayusculas
import com.xebolax.tiresias.nucleo.ModoNumeros
import com.xebolax.tiresias.nucleo.NivelPuntuacion
import com.xebolax.tiresias.nucleo.NivelVerbosidad
import com.xebolax.tiresias.nucleo.Parte
import com.xebolax.tiresias.nucleo.Verbosidad

/**
 * Los ajustes de Tiresias, en el almacenamiento protegido del dispositivo (ver
 * [Almacen]) para que el lector los tenga también antes de desbloquear.
 *
 * Es el mismo archivo que usa la pantalla de Ajustes (`ajustes.xml`, con
 * `PreferenceManager.setStorageDeviceProtected()`), así que las claves son las
 * de [Claves]. Solo guarda configuración: nunca nada leído de la pantalla.
 */
class Ajustes(contexto: Context) {
	val p: SharedPreferences = Almacen.protegido(contexto).getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

	private fun cadena(clave: String, porDefecto: String) = p.getString(clave, porDefecto) ?: porDefecto
	private fun entero(clave: String, porDefecto: Int) = runCatching { p.getInt(clave, porDefecto) }.getOrElse { cadena(clave, porDefecto.toString()).toIntOrNull() ?: porDefecto }
	private fun logico(clave: String, porDefecto: Boolean) = p.getBoolean(clave, porDefecto)
	private fun poner(clave: String, valor: Any) = p.edit().apply {
		when (valor) {
			is String -> putString(clave, valor)
			is Int -> putInt(clave, valor)
			is Long -> putLong(clave, valor)
			is Boolean -> putBoolean(clave, valor)
		}
	}.apply()

	// --- General ----------------------------------------------------------------------

	/** Idioma de la interfaz y de las frases: "" sigue al sistema, "es" o "en". */
	var idioma: String
		get() = cadena(Claves.IDIOMA, "")
		set(v) = poner(Claves.IDIOMA, v)

	var asistenteHecho: Boolean
		get() = logico(Claves.ASISTENTE_HECHO, false)
		set(v) = poner(Claves.ASISTENTE_HECHO, v)

	/** versionCode con el que se abrió la última vez (para decir «actualizado a la versión X»). */
	var versionAnterior: Int
		get() = entero(Claves.VERSION_ANTERIOR, 0)
		set(v) = poner(Claves.VERSION_ANTERIOR, v)

	// --- Voz --------------------------------------------------------------------------

	/** Motor de voz: [MOTOR_ESPEAK], [MOTOR_PICO] (incluidos) o el paquete de un motor del sistema. */
	var motor: String
		get() = cadena(Claves.MOTOR, MOTOR_ESPEAK)
		set(v) = poner(Claves.MOTOR, v)

	/** Los teclados (paquetes) que hablan solos: con ellos, Tiresias no repite lo que se escribe ni sus teclas. */
	var tecladosQueHablan: Set<String>
		get() = p.getStringSet(Claves.TECLADOS_QUE_HABLAN, null) ?: TECLADOS_QUE_HABLAN_DE_FABRICA
		set(v) = p.edit().putStringSet(Claves.TECLADOS_QUE_HABLAN, v).apply()

	/** Cómo se desplazan las listas al llegar a lo último que se ve: normal, sin sonido o anticipado (como VoiceOver). */
	val desplazarListas: String get() = cadena(Claves.DESPLAZAR_LISTAS, DESPLAZAR_NORMAL)

	/** Si se detecta el idioma de los textos largos que la app no marca (español o inglés). */
	val detectarIdioma: Boolean get() = logico(Claves.DETECTAR_IDIOMA, true)

	/** La voz de las notificaciones (una de las incluidas), o "" para la de siempre. */
	val motorAvisos: String get() = cadena(Claves.MOTOR_AVISOS, "")

	/** Las voces del control «Voz» ([com.xebolax.tiresias.voz.VocesDisponibles.delControl]); vacío, todas. */
	var vocesControl: Set<String>
		get() = p.getStringSet(Claves.VOCES_CONTROL, emptySet()) ?: emptySet()
		set(v) = p.edit().putStringSet(Claves.VOCES_CONTROL, v).apply()

	/** Nombre de la voz del motor del sistema elegida ("" la predeterminada del motor), la del motor elegido. */
	var vozSistema: String
		get() = cadena(Claves.VOZ_SISTEMA, "")
		set(v) = poner(Claves.VOZ_SISTEMA, v)

	/**
	 * La velocidad, el tono y el volumen de la voz son de cada motor, como en
	 * NVDA (que los guarda por sintetizador): una velocidad que va bien en un
	 * motor rápido es incomprensible en otro. Las claves de siempre
	 * ([Claves.VELOCIDAD], [Claves.TONO], [Claves.VOLUMEN_VOZ]) son las del motor
	 * elegido, las que cambian los gestos y Ajustes; las de los demás motores se
	 * guardan aparte. Si el motor ha cambiado desde la última vez, guarda las del
	 * anterior y pone las del nuevo: las suyas o, la primera vez que se usa, las
	 * normales (100 %). Devuelve null si el motor no ha cambiado, true si es la
	 * primera vez del motor nuevo y false si ya tenía las suyas.
	 */
	fun ajustarVozAlMotor(): Boolean? = synchronized(CERROJO_VOZ) {
		val actual = motor
		val anterior = p.getString(Claves.MOTOR_DE_LA_VOZ, null)
		if (anterior == actual) return null
		val e = p.edit().putString(Claves.MOTOR_DE_LA_VOZ, actual)
		// La primera vez (al actualizar desde una versión sin esto), lo que hay es del motor elegido
		if (anterior == null) {
			e.apply()
			return null
		}
		e.putString(Claves.VOZ_DE + anterior, "$velocidad;$tono;$volumenVoz")
		// La voz del sistema elegida es de su motor: otro motor no tiene esa voz
		e.putString(Claves.VOZ_SISTEMA_DE + anterior, vozSistema)
		e.putString(Claves.VOZ_SISTEMA, p.getString(Claves.VOZ_SISTEMA_DE + actual, "") ?: "")
		val suyos = p.getString(Claves.VOZ_DE + actual, null)?.split(';')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }
		val (v, t, vol) = suyos ?: listOf(100, 100, 100)
		e.putInt(Claves.VELOCIDAD, v.coerceIn(VELOCIDAD_MIN, VELOCIDAD_MAX))
			.putInt(Claves.TONO, t.coerceIn(TONO_MIN, TONO_MAX))
			.putInt(Claves.VOLUMEN_VOZ, vol.coerceIn(10, 100))
			.apply()
		return suyos == null
	}

	/**
	 * Guarda la velocidad, el tono, el volumen y la voz del sistema de ahora como
	 * los de su motor, y da los que se van a poner a mano (un perfil con nombre)
	 * como los de [nuevoMotor]: así el cambio de motor no los confunde.
	 */
	fun guardarVozAntesDeCambiar(nuevoMotor: String?): Unit = synchronized(CERROJO_VOZ) {
		val de = p.getString(Claves.MOTOR_DE_LA_VOZ, null) ?: motor
		val e = p.edit().putString(Claves.VOZ_DE + de, "$velocidad;$tono;$volumenVoz").putString(Claves.VOZ_SISTEMA_DE + de, vozSistema)
		if (nuevoMotor != null) e.putString(Claves.MOTOR_DE_LA_VOZ, nuevoMotor)
		e.commit()
	}

	/** La velocidad que tiene [motor] (la de ahora si es el elegido). */
	fun velocidadDe(motor: String): Int = synchronized(CERROJO_VOZ) {
		if (motor == this.motor) velocidad
		else p.getString(Claves.VOZ_DE + motor, null)?.substringBefore(';')?.toIntOrNull() ?: 100
	}

	/** Cambia la velocidad de [motor] (la de ahora si es el elegido). */
	fun ponerVelocidadDe(motor: String, v: Int): Unit = synchronized(CERROJO_VOZ) {
		if (motor == this.motor) {
			velocidad = v
			return
		}
		val resto = p.getString(Claves.VOZ_DE + motor, null)?.substringAfter(';', "100;100") ?: "100;100"
		p.edit().putString(Claves.VOZ_DE + motor, "${v.coerceIn(VELOCIDAD_MIN, VELOCIDAD_MAX)};$resto").apply()
	}

	/** Velocidad en por ciento (100 normal), la del motor elegido ([ajustarVozAlMotor]). Tiresias la aplica él, sea cual sea el motor. */
	var velocidad: Int
		get() = entero(Claves.VELOCIDAD, 100)
		set(v) = poner(Claves.VELOCIDAD, v.coerceIn(VELOCIDAD_MIN, VELOCIDAD_MAX))

	var tono: Int
		get() = entero(Claves.TONO, 100)
		set(v) = poner(Claves.TONO, v.coerceIn(TONO_MIN, TONO_MAX))

	/** Volumen de la voz respecto al de accesibilidad, en por ciento. */
	var volumenVoz: Int
		get() = entero(Claves.VOLUMEN_VOZ, 100)
		set(v) = poner(Claves.VOLUMEN_VOZ, v.coerceIn(10, 100))

	/** Bajar el volumen del resto del audio mientras habla Tiresias. */
	var atenuar: Boolean
		get() = logico(Claves.ATENUAR, true)
		set(v) = poner(Claves.ATENUAR, v)

	/** Cambiar de voz cuando la app indica el idioma de un texto (LocaleSpan), como TalkBack. */
	var cambioIdioma: Boolean
		get() = logico(Claves.CAMBIO_IDIOMA, true)
		set(v) = poner(Claves.CAMBIO_IDIOMA, v)

	/** Al cambiar de idioma, también de variante (español de México, inglés británico); si no, solo el idioma (Backtalk 58). */
	val cambioDialecto: Boolean get() = logico(Claves.CAMBIO_DIALECTO, true)

	/** Callarse mientras otra app graba con el micrófono (dictado, notas de voz, llamadas por internet). */
	var callarAlGrabar: Boolean
		get() = logico(Claves.CALLAR_AL_GRABAR, true)
		set(v) = poner(Claves.CALLAR_AL_GRABAR, v)

	/** Tapar el sensor de proximidad con la mano calla la voz (como TalkBack). */
	/** En un campo de texto, las teclas de volumen mueven el cursor un carácter (SandFly). */
	val volumenCursor: Boolean get() = logico(Claves.VOLUMEN_CURSOR, false)
	/** Decir el volumen nuevo al cambiarlo con las teclas (SandFly). */
	val decirVolumen: Boolean get() = logico(Claves.DECIR_VOLUMEN, true)

	var callarProximidad: Boolean
		get() = logico(Claves.CALLAR_PROXIMIDAD, true)
		set(v) = poner(Claves.CALLAR_PROXIMIDAD, v)

	val ajustesTexto: AjustesTexto
		get() = AjustesTexto(
			puntuacion = enumDe(cadena(Claves.PUNTUACION, NivelPuntuacion.ALGUNAS.name), NivelPuntuacion.ALGUNAS),
			numeros = enumDe(cadena(Claves.NUMEROS, ModoNumeros.CIFRAS.name), ModoNumeros.CIFRAS),
			mayusculas = enumDe(cadena(Claves.MAYUSCULAS, ModoMayusculas.TONO.name), ModoMayusculas.TONO),
			repetidos = entero(Claves.REPETIDOS, 4),
			emojis = modoEmojis,
		)

	var puntuacion: NivelPuntuacion
		get() = ajustesTexto.puntuacion
		set(v) = poner(Claves.PUNTUACION, v.name)

	/** Eco del teclado: "caracteres", "palabras", "ambos" o "nada". */
	var ecoTeclado: String
		get() = cadena(Claves.ECO_TECLADO, "caracteres")
		set(v) = poner(Claves.ECO_TECLADO, v)

	// --- Verbosidad -------------------------------------------------------------------

	val verbosidad: Verbosidad
		get() {
			val nivel = enumDe(cadena(Claves.NIVEL_VERBOSIDAD, NivelVerbosidad.ALTA.name), NivelVerbosidad.ALTA)
			val base = Verbosidad.de(nivel)
			val o = orden()
			val pistas = Parte.PISTA in o
			val encabezados = logico(Claves.ENCABEZADOS_TABLA, true)
			val despues = cadena(Claves.ENCABEZADOS_TABLA_LUGAR, "antes") == "despues"
			val filaColumna = logico(Claves.DECIR_FILA_COLUMNA, true)
			// «Avisar de los elementos con acciones» y «Nombrar los elementos sin nombre» valen con cualquier verbosidad
			// (apagados, quitan lo que daría el nivel; encendidos, lo deja como esté)
			if (nivel != NivelVerbosidad.PERSONALIZADA) return base.copy(orden = o, decirPistas = base.decirPistas && pistas, decirEncabezadosTabla = encabezados, encabezadosDespues = despues, decirFilaColumna = filaColumna,
				decirAcciones = base.decirAcciones && logico(Claves.DECIR_ACCIONES, true), decirIdentificador = base.decirIdentificador && logico(Claves.DECIR_IDENTIFICADOR, true))
			return base.copy(
				orden = o,
				decirEncabezadosTabla = encabezados,
				encabezadosDespues = despues,
				decirFilaColumna = filaColumna,
				decirRol = logico(Claves.DECIR_ROL, true),
				decirEstado = logico(Claves.DECIR_ESTADO, true),
				decirPosicion = logico(Claves.DECIR_POSICION, true),
				decirPistas = logico(Claves.DECIR_PISTAS, true) && pistas,
				decirAcciones = logico(Claves.DECIR_ACCIONES, true),
				decirIdentificador = logico(Claves.DECIR_IDENTIFICADOR, true),
				decirPanel = logico(Claves.DECIR_PANEL, true),
			)
		}

	var nivelVerbosidad: NivelVerbosidad
		get() = enumDe(cadena(Claves.NIVEL_VERBOSIDAD, NivelVerbosidad.ALTA.name), NivelVerbosidad.ALTA)
		set(v) = poner(Claves.NIVEL_VERBOSIDAD, v.name)

	private fun orden(): List<Parte> = when (cadena(Claves.ORDEN, "normal")) {
		"libre" -> ordenPartes.let { if (Parte.NOMBRE in it) it else listOf(Parte.NOMBRE) + it }
		"estado" -> Verbosidad.ORDEN_ESTADO_PRIMERO
		"rol" -> Verbosidad.ORDEN_ROL_PRIMERO
		else -> Verbosidad.ORDEN_NORMAL
	}

	/** Espera antes de decir la pista de uso, en milisegundos. */
	val esperaPistas: Long get() = entero(Claves.ESPERA_PISTAS, 1200).toLong()

	// --- Sonidos y vibración ------------------------------------------------------------

	var sonidos: Boolean
		get() = logico(Claves.SONIDOS, true)
		set(v) = poner(Claves.SONIDOS, v)

	val volumenSonidos: Int get() = entero(Claves.VOLUMEN_SONIDOS, 70)

	/** Sonido a la izquierda o a la derecha según la posición en la pantalla. */
	val sonidoPosicional: Boolean get() = logico(Claves.SONIDO_POSICIONAL, false)

	var vibracion: Boolean
		get() = logico(Claves.VIBRACION, true)
		set(v) = poner(Claves.VIBRACION, v)

	val intensidadVibracion: Int get() = entero(Claves.INTENSIDAD_VIBRACION, 60)

	// --- Navegación, gestos y teclado -----------------------------------------------------

	/** Al llegar al final, seguir por el principio (con un sonido que lo avisa). */
	val darVuelta: Boolean get() = logico(Claves.DAR_VUELTA, false)

	/** Recordar el elemento enfocado de cada ventana y volver a él (también al pulsar Atrás). */
	val recordarFoco: Boolean get() = logico(Claves.RECORDAR_FOCO, true)

	/** El menú de Tiresias: qué opciones salen y en qué orden. */
	var menuTiresias: List<com.xebolax.tiresias.nucleo.Accion>
		get() = com.xebolax.tiresias.nucleo.Listas.leer(cadena(Claves.MENU_TIRESIAS, ""), com.xebolax.tiresias.nucleo.Accion.entries.toTypedArray(), com.xebolax.tiresias.nucleo.Listas.MENU_TIRESIAS)
		set(v) = poner(Claves.MENU_TIRESIAS, com.xebolax.tiresias.nucleo.Listas.escribir(v))

	/** Los controles de lectura (deslizar arriba y luego abajo para cambiar): cuáles y en qué orden. */
	var controlesLectura: List<com.xebolax.tiresias.nucleo.ControlLectura>
		get() = com.xebolax.tiresias.nucleo.Listas.leer(cadena(Claves.CONTROLES_LECTURA, ""), com.xebolax.tiresias.nucleo.ControlLectura.entries.toTypedArray(), com.xebolax.tiresias.nucleo.ControlLectura.POR_DEFECTO)
		set(v) = poner(Claves.CONTROLES_LECTURA, com.xebolax.tiresias.nucleo.Listas.escribir(v))

	/** Tocar la pantalla corta lo que se está diciendo (si no, solo lo corta enfocar otra cosa). */
	val cortarAlTocar: Boolean get() = logico(Claves.CORTAR_AL_TOCAR, true)

	/** Decir los textos largos por frases (para reanudar la pausa donde iba). */
	val trocearLargos: Boolean get() = logico(Claves.TROCEAR_LARGOS, true)

	/** Si una lista no acepta desplazarse por accesibilidad, se arrastra con dos dedos. */
	val arrastreRespaldo: Boolean get() = logico(Claves.ARRASTRE_RESPALDO, true)

	/** Letras fonéticas al moverse por caracteres: "no", "despues" (b, Barcelona) o "en_vez" (solo Barcelona). */
	val foneticas: String get() = cadena(Claves.FONETICAS, "no")

	/** Los sonidos y las vibraciones que el usuario ha quitado, uno a uno (por el nombre de [Sonido]). */
	val sonidosApagados: Set<String> get() = p.getStringSet(Claves.SONIDOS_APAGADOS, emptySet()) ?: emptySet()
	val vibracionesApagadas: Set<String> get() = p.getStringSet(Claves.VIBRACIONES_APAGADAS, emptySet()) ?: emptySet()

	fun apagarSonido(s: com.xebolax.tiresias.voz.Sonido, apagar: Boolean) {
		p.edit().putStringSet(Claves.SONIDOS_APAGADOS, sonidosApagados.toMutableSet().apply { if (apagar) add(s.name) else remove(s.name) }).apply()
	}

	fun apagarVibracion(s: com.xebolax.tiresias.voz.Sonido, apagar: Boolean) {
		p.edit().putStringSet(Claves.VIBRACIONES_APAGADAS, vibracionesApagadas.toMutableSet().apply { if (apagar) add(s.name) else remove(s.name) }).apply()
	}

	/** Qué se dice al encender la pantalla: "nada", "hora" o "estado" (lo mismo que el gesto de estado). */
	val alEncender: String get() = cadena(Claves.AL_ENCENDER, "nada")

	/** Decir «pantalla apagada» al apagarla. */
	val alApagar: Boolean get() = logico(Claves.AL_APAGAR, false)

	/** El orden libre de las partes de lo que se dice (con «orden» = "libre"). */
	var ordenPartes: List<Parte>
		get() = com.xebolax.tiresias.nucleo.Listas.leer(cadena(Claves.ORDEN_PARTES, ""), Parte.entries.toTypedArray(), Verbosidad.ORDEN_NORMAL)
		set(v) = poner(Claves.ORDEN_PARTES, com.xebolax.tiresias.nucleo.Listas.escribir(v))

	/** En el teclado en pantalla, la tecla se escribe al levantar el dedo (sin doble toque). */
	val escribirAlLevantar: Boolean get() = logico(Claves.ESCRIBIR_AL_LEVANTAR, false)

	/** Un sonido cada pocos segundos mientras la IA piensa o se reconoce el texto. */
	val sonidoEspera: Boolean get() = logico(Claves.SONIDO_ESPERA, true)

	/** Decir «sigue pensando» si la IA tarda más de 20 segundos. */
	val avisoEspera: Boolean get() = logico(Claves.AVISO_ESPERA, true)

	/** En cada pantalla nueva, el foco va a su primer elemento (como en los diálogos). */
	val focoVentanasNuevas: Boolean get() = logico(Claves.FOCO_VENTANAS_NUEVAS, false)

	/** Los emojis: por su nombre, al motor o quitados (antes, el interruptor «emojis»: sí o quitarlos). */
	val modoEmojis: ModoEmojis
		get() {
			val elegido = cadena(Claves.MODO_EMOJIS, "")
			if (elegido.isEmpty()) return if (logico(Claves.EMOJIS, true)) ModoEmojis.NOMBRE else ModoEmojis.QUITAR
			return enumDe(elegido, ModoEmojis.NOMBRE)
		}

	/** Un tic por cada elemento que pasa al desplazar una lista (en vez del sonido de desplazar). */
	val ticDesplazar: Boolean get() = logico(Claves.TIC_DESPLAZAR, false)

	/** Nombrar en las descripciones a las personas que conoce el usuario (con sus fotos, Backtalk 173). */
	val reconocerPersonas: Boolean get() = logico(Claves.RECONOCER_PERSONAS, true)

	/** Mandar también las fotos de las personas a una IA de internet (si no, solo con la IA del móvil). */
	val personasNube: Boolean get() = logico(Claves.PERSONAS_NUBE, false)

	/** El tema de sonidos (el nombre de su carpeta en `temas/`); vacío: los de Tiresias. */
	var temaSonidos: String
		get() = cadena(Claves.TEMA_SONIDOS, "")
		set(v) = poner(Claves.TEMA_SONIDOS, v)

	/** El sonido del foco cambia de tono según el tipo de control (botón, enlace, campo...), como Unspoken de NVDA. */
	val sonidoTipo: Boolean get() = logico(Claves.SONIDO_TIPO, false)

	/** El sonido del foco más agudo arriba de la pantalla y más grave abajo. */
	val sonidoVertical: Boolean get() = logico(Claves.SONIDO_VERTICAL, false)

	/** Un clic al poner cada punto en el teclado braille en pantalla (Backtalk 12). */
	val brailleSonarPuntos: Boolean get() = logico(Claves.BRAILLE_SONAR_PUNTOS, false)

	/** Las fuentes de complementos que ha añadido el usuario, además del servidor propio. */
	var fuentesComplementos: List<String>
		get() = cadena(Claves.FUENTES_COMPLEMENTOS, "").split('\n').map { it.trim() }.filter { it.startsWith("https://") }
		set(v) = poner(Claves.FUENTES_COMPLEMENTOS, v.distinct().joinToString("\n"))

	/** Las ocho opciones del menú circular, en su orden. */
	var menuCircular: List<com.xebolax.tiresias.nucleo.Accion>
		get() = com.xebolax.tiresias.nucleo.Listas.leer(cadena(Claves.MENU_CIRCULAR, ""), com.xebolax.tiresias.nucleo.Accion.entries.toTypedArray(), com.xebolax.tiresias.nucleo.Listas.MENU_CIRCULAR)
		set(v) = poner(Claves.MENU_CIRCULAR, com.xebolax.tiresias.nucleo.Listas.escribir(v))

	/** Lo que dice el gesto de estado, en su orden. */
	var partesEstado: List<com.xebolax.tiresias.nucleo.ParteEstado>
		get() = com.xebolax.tiresias.nucleo.Listas.leer(cadena(Claves.PARTES_ESTADO, ""), com.xebolax.tiresias.nucleo.ParteEstado.entries.toTypedArray(), com.xebolax.tiresias.nucleo.Listas.ESTADO)
		set(v) = poner(Claves.PARTES_ESTADO, com.xebolax.tiresias.nucleo.Listas.escribir(v))

	/** Qué se dice al desbloquear: "nada", "desbloqueado", "hora" o "estado". */
	val alDesbloquear: String get() = cadena(Claves.AL_DESBLOQUEAR, "nada")

	/** Con el móvil bloqueado, callar las notificaciones y los avisos (solo se dice lo que tú pides). */
	val callarBloqueo: Boolean get() = logico(Claves.CALLAR_BLOQUEO, false)
	/** Al encender con el móvil bloqueado, unos segundos sin lo que dice solo la pantalla de bloqueo (Backtalk 312). */
	val soloLoElegidoAlEncender: Boolean get() = logico(Claves.SOLO_LO_ELEGIDO_AL_ENCENDER, false)

	/** Las apps en las que activar es siempre un toque de verdad (Backtalk 159). */
	var appsClicDirecto: Set<String>
		get() = p.getStringSet(Claves.APPS_CLIC_DIRECTO, emptySet()) ?: emptySet()
		set(v) = p.edit().putStringSet(Claves.APPS_CLIC_DIRECTO, v).apply()

	/** Los controles de lectura de la web (enlaces, regiones) solo dentro de una página web. */
	val controlesSoloWeb: Boolean get() = logico(Claves.CONTROLES_SOLO_WEB, true)

	/** En una pantalla nueva, el primer elemento corta el título en vez de esperar a que acabe. */
	val focoSinEsperarTitulo: Boolean get() = logico(Claves.FOCO_SIN_ESPERAR_TITULO, false)

	/** Activar al levantar el dedo tras explorar: [LEVANTAR_NO], [LEVANTAR_BARRA] o [LEVANTAR_PANTALLA]. */
	val activarAlLevantar: String get() = cadena(Claves.ACTIVAR_AL_LEVANTAR, LEVANTAR_NO)

	/** El toque directo de las apps está en pausa (mosaico de ajustes rápidos). */
	var toqueDirectoPausado: Boolean
		get() = logico(Claves.TOQUE_DIRECTO_PAUSADO, false)
		set(v) = poner(Claves.TOQUE_DIRECTO_PAUSADO, v)

	/** Qué teclas del teclado físico cortan la voz: [TECLAS_SIEMPRE], [TECLAS_CAMPOS] o [TECLAS_NUNCA]. */
	val teclasCortan: String get() = cadena(Claves.TECLAS_CORTAN, TECLAS_SIEMPRE)

	/** Si Intro corta la voz (para oír la respuesta de lo que se envía). */
	val introCorta: Boolean get() = logico(Claves.INTRO_CORTA, true)

	/** Por dónde suenan las voces incluidas: "sistema", "altavoz" o "auriculares" (`PistaAudio`). */
	var salidaAudio: String
		get() = cadena(Claves.SALIDA_AUDIO, "sistema")
		set(v) = poner(Claves.SALIDA_AUDIO, v)

	/** La voz y los sonidos por el volumen multimedia (en vez del de accesibilidad). Al volver a activar Tiresias. */
	val vozMultimedia: Boolean get() = logico(Claves.VOZ_MULTIMEDIA, false)

	/** En el teclado en pantalla, los toques pasan directos al teclado (se escribe tocando, sin explorar). */
	val toqueDirectoTeclado: Boolean get() = logico(Claves.TOQUE_DIRECTO_TECLADO, false)

	/** Teclado braille en pantalla: puntos en espejo, qué se dice al escribir y vibrar al poner cada punto. */
	val brailleEspejo: Boolean get() = logico(Claves.BRAILLE_ESPEJO, false)
	/** En el teclado braille, Intro pulsa el botón Enviar de las apps de mensajes (SandFly). */
	val brailleIntroEnvia: Boolean get() = logico(Claves.BRAILLE_INTRO_ENVIA, true)

	/** El teclado braille pone los puntos según cómo se sujeta el móvil (sensor de gravedad); manda sobre [brailleEspejo]. */
	val brailleSensor: Boolean get() = logico(Claves.BRAILLE_SENSOR, false)
	val brailleEco: String get() = cadena(Claves.BRAILLE_ECO, "letras")

	/** Las combinaciones de la línea braille que el usuario ha cambiado («12=ATRAS;13=NINGUNA»). */
	var acordesBraille: String
		get() = cadena(Claves.BRAILLE_ACORDES, "")
		set(v) = poner(Claves.BRAILLE_ACORDES, v)

	/** Los puntos que marcan el cursor en la línea: los 7 y 8, solo el 8, solo el 7 o ninguno. */
	val puntosCursorBraille: Int get() = when (cadena(Claves.BRAILLE_CURSOR, "78")) {
		"8" -> com.xebolax.tiresias.nucleo.braille.Puntos.P8
		"7" -> com.xebolax.tiresias.nucleo.braille.Puntos.P7
		"no" -> 0
		else -> com.xebolax.tiresias.nucleo.braille.Puntos.CURSOR
	}

	// --- Modo senior ----------------------------------------------------------------------------

	/** Si está puesto el modo senior ([ModoSenior]). */
	val modoSenior: Boolean get() = logico(Claves.MODO_SENIOR, false)

	/** En el modo senior, solo se leen las llamadas, los mensajes, las alarmas y los recordatorios. */
	val seniorSoloMensajes: Boolean get() = logico(Claves.SENIOR_SOLO_MENSAJES, true)

	/** En el modo senior, un toque sobre lo que se acaba de decir lo activa (para quien no domina el doble toque). */
	val seniorToqueActiva: Boolean get() = logico(Claves.SENIOR_TOQUE_ACTIVA, false)

	/** El teléfono al que se pide ayuda (SOS). */
	var seniorContactoAyuda: String
		get() = cadena(Claves.SENIOR_CONTACTO, "")
		set(v) = poner(Claves.SENIOR_CONTACTO, v.trim())

	/** El nombre de quien ayuda, para decirlo («Llamando a Juan»). */
	var seniorNombreAyuda: String
		get() = cadena(Claves.SENIOR_NOMBRE, "")
		set(v) = poner(Claves.SENIOR_NOMBRE, v.trim())

	/** El código (cifras) que protege los ajustes en el modo senior; vacío, sin código. */
	var seniorCodigo: String
		get() = cadena(Claves.SENIOR_CODIGO, "")
		set(v) = poner(Claves.SENIOR_CODIGO, v.trim())

	// --- Eloquence ----------------------------------------------------------------------------

	/** La voz de Eloquence: 0 a 7 (Reed, Shelley, Bobby, Rocko, Glen, Sandy, Grandma, Grandpa). */
	var eloquenceVoz: Int
		get() = entero(Claves.ELOQUENCE_VOZ, 0).coerceIn(0, 7)
		set(v) = poner(Claves.ELOQUENCE_VOZ, v.coerceIn(0, 7))

	/** El español de Eloquence: «auto» (según el texto), «ESP» (España) o «MEX» (México). */
	val eloquenceEspanol: String get() = cadena(Claves.ELOQUENCE_ESPANOL, "auto")
	/** El inglés: «auto», «USA» o «GBR». */
	val eloquenceIngles: String get() = cadena(Claves.ELOQUENCE_INGLES, "auto")
	/** El francés: «auto», «FRA» o «CAN». */
	val eloquenceFrances: String get() = cadena(Claves.ELOQUENCE_FRANCES, "auto")

	/** Las pausas: 0 las del motor, 1 acortar la del final, 2 acortar todas (como el driver de NVDA). */
	val eloquencePausas: Int get() = cadena(Claves.ELOQUENCE_PAUSAS, "2").toIntOrNull()?.coerceIn(0, 2) ?: 2
	/** La predicción de frases (entonación de prosa: va bien leyendo libros, no en la pantalla). */
	val eloquencePrediccion: Boolean get() = logico(Claves.ELOQUENCE_PREDICCION, false)
	/** Que el motor desarrolle las abreviaturas («Sr.» como «señor»). */
	val eloquenceAbreviaturas: Boolean get() = logico(Claves.ELOQUENCE_ABREVIATURAS, false)
	/** La frecuencia de muestreo, en hercios. */
	val eloquenceMuestreo: Int get() = cadena(Claves.ELOQUENCE_MUESTREO, "11025").toIntOrNull() ?: 11025

	/**
	 * El valor del parámetro [parametro] de la voz [voz]: el que haya puesto el
	 * usuario o, si no, el de Tiresias de fábrica (Reed como en el NVDA del autor:
	 * tono 65, entonación 35, volumen 95, sin aspereza ni aliento), o -1 para el
	 * de la propia voz.
	 */
	fun eloquenceParametro(voz: Int, parametro: Int): Int = entero("${Claves.ELOQUENCE_PARAMETRO}${voz}_$parametro", ELOQUENCE_DE_FABRICA[voz]?.get(parametro) ?: -1)

	/**
	 * Una sola vez (1.0.5): la velocidad guardada de Eloquence vuelve al 100 %.
	 * Hasta la 1.0.4, su 100 % era la velocidad 55 de NVDA con el aumento, unas
	 * diez veces la normal del motor, y un porcentaje guardado sobre esa base
	 * suena ahora muy distinto. Se quitan también aquella velocidad y su aumento.
	 */
	fun normalizarEloquence(): Unit = synchronized(CERROJO_VOZ) {
		if (p.getBoolean(Claves.ELOQUENCE_VELOCIDAD_NORMAL, false)) return
		val e = p.edit().putBoolean(Claves.ELOQUENCE_VELOCIDAD_NORMAL, true)
			.remove(Claves.ELOQUENCE_VELOCIDAD).remove(Claves.ELOQUENCE_AUMENTO)
		if (motor == MOTOR_ELOQUENCE && p.getString(Claves.MOTOR_DE_LA_VOZ, null) == MOTOR_ELOQUENCE) e.putInt(Claves.VELOCIDAD, 100)
		p.getString(Claves.VOZ_DE + MOTOR_ELOQUENCE, null)?.let { guardada ->
			e.putString(Claves.VOZ_DE + MOTOR_ELOQUENCE, "100;" + guardada.substringAfter(';', "100;100"))
		}
		e.apply()
	}

	fun ponerEloquenceParametro(voz: Int, parametro: Int, valor: Int) = poner("${Claves.ELOQUENCE_PARAMETRO}${voz}_$parametro", valor)

	/** Deja la voz [voz] como viene de fábrica. */
	fun restablecerEloquence(voz: Int) = p.edit().apply { for (k in 0 until 8) remove("${Claves.ELOQUENCE_PARAMETRO}${voz}_$k") }.apply()

	/** El diccionario de Eloquence del volumen [volumen] (0 principal, 1 raíces, 2 abreviaturas), copiado en Tiresias. */
	fun eloquenceDiccionario(volumen: Int): String? = cadena("${Claves.ELOQUENCE_DICCIONARIO}$volumen", "").ifEmpty { null }

	fun ponerEloquenceDiccionario(volumen: Int, ruta: String?) = if (ruta == null) p.edit().remove("${Claves.ELOQUENCE_DICCIONARIO}$volumen").apply() else poner("${Claves.ELOQUENCE_DICCIONARIO}$volumen", ruta)

	/** Un «Leer más» al final de un mensaje se pulsa solo al llegar a él, y se lee el resto (WhatsApp, Telegram...). */
	val leerMasSolo: Boolean get() = logico(Claves.LEER_MAS_SOLO, true)
	val brailleVibrarPuntos: Boolean get() = logico(Claves.BRAILLE_VIBRAR_PUNTOS, false)

	/** Con un deslizador enfocado, las teclas de volumen cambian su valor. */
	val volumenDeslizadores: Boolean get() = logico(Claves.VOLUMEN_DESLIZADORES, false)

	/** En un deslizador, deslizar arriba y abajo cambia su valor (si no, se navega como siempre). */
	val deslizadorArribaAbajo: Boolean get() = logico(Claves.DESLIZADOR_ARRIBA_ABAJO, true)

	/** Con el control predeterminado, arriba y abajo eligen las acciones propias del elemento (y el doble toque hace la elegida). */
	val accionesArribaAbajo: Boolean get() = logico(Claves.ACCIONES_ARRIBA_ABAJO, true)

	/** Leer lo que se toca de inmediato (sin esperar a que el dedo se pare). */
	val retardoExploracion: Long get() = entero(Claves.RETARDO_EXPLORACION, 0).toLong()

	var mapaGestos: MapaGestos
		get() = MapaGestos.leer(cadena(Claves.GESTOS, ""))
		set(v) = poner(Claves.GESTOS, v.escribir())

	val estiloTeclado: EstiloTeclado
		get() = enumDe(cadena(Claves.ESTILO_TECLADO, EstiloTeclado.TALKBACK.name), EstiloTeclado.TALKBACK)

	val mapaTeclas: MapaTeclas get() = MapaTeclas.leer(cadena(Claves.TECLAS, ""), MapaTeclas.de(estiloTeclado))

	/** Tecla Tiresias: "alt", "meta", "bloqmayus" o "insertar" (las dos últimas en el estilo Windows). */
	val teclaTiresias: String get() = cadena(Claves.TECLA_TIRESIAS, if (estiloTeclado == EstiloTeclado.TALKBACK) "alt" else "insertar")

	/** Reconocer los botones sin nombre: "no", "ocr" o "ia" (OCR y, si no basta, la IA del móvil). */
	val iconos: String get() = cadena(Claves.ICONOS, "ocr")

	/** Descripción continua: cada cuántos segundos se mira la pantalla y cuántos minutos como mucho. */
	val continuaIntervalo: Int get() = entero(Claves.CONTINUA_INTERVALO, 10).coerceIn(3, 120)
	val continuaMinutos: Int get() = entero(Claves.CONTINUA_MINUTOS, 10).coerceIn(1, 120)

	// --- Braille -------------------------------------------------------------------------

	/** Conectar las líneas braille HID (Android 15 o posterior). */
	val brailleLinea: Boolean get() = logico(Claves.BRAILLE_LINEA, true)

	/** "informatico" (8 puntos, de liblouis) o "literario" (6 puntos, grado 1). */
	val brailleTabla: String get() = cadena(Claves.BRAILLE_TABLA, "informatico")

	/** La tabla informática de 8 puntos elegida (su archivo); vacío: la del idioma de Tiresias. */
	val brailleTablaInformatica: String get() = cadena(Claves.BRAILLE_TABLA_INFORMATICA, "")

	/** Cuánto se ven los mensajes en la línea, en milisegundos. */
	val brailleMensajes: Long get() = entero(Claves.BRAILLE_MENSAJES, 4000).toLong()

	/** «Primero braille» con la línea conectada: "no", "minima" (solo avisos) o "sin_voz". */
	var braillePrimero: String
		get() = cadena(Claves.BRAILLE_PRIMERO, "no")
		set(v) = poner(Claves.BRAILLE_PRIMERO, v)

	// --- Notificaciones -------------------------------------------------------------------

	val leerNotificaciones: Boolean get() = logico(Claves.LEER_NOTIFICACIONES, true)
	/** Las palabras que callan una notificación (separadas por comas o líneas), en minúsculas. */
	val palabrasCalladas: List<String>
		get() = cadena(Claves.PALABRAS_CALLADAS, "").split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
	val notificacionesNoMolestar: Boolean get() = logico(Claves.NOTIFICACIONES_NO_MOLESTAR, false)
	val notificacionesEnLlamada: Boolean get() = logico(Claves.NOTIFICACIONES_EN_LLAMADA, false)
	/** Paquetes cuyas notificaciones no se leen solas. */
	val appsSilenciadas: Set<String> get() = p.getStringSet(Claves.APPS_SILENCIADAS, emptySet()) ?: emptySet()

	/** Apps que pueden hablar con Tiresias por su API, y las que el usuario no dejó. */
	val apiPermitidas: Set<String> get() = p.getStringSet(Claves.API_PERMITIDAS, emptySet()) ?: emptySet()
	val apiDenegadas: Set<String> get() = p.getStringSet(Claves.API_DENEGADAS, emptySet()) ?: emptySet()

	/** Deja (true), no deja (false) u olvida (null) que [paquete] use la API de Tiresias. */
	fun permitirApi(paquete: String, permitir: Boolean?) {
		val si = apiPermitidas.toMutableSet().apply { remove(paquete); if (permitir == true) add(paquete) }
		val no = apiDenegadas.toMutableSet().apply { remove(paquete); if (permitir == false) add(paquete) }
		p.edit().putStringSet(Claves.API_PERMITIDAS, si).putStringSet(Claves.API_DENEGADAS, no).apply()
	}

	fun silenciarApp(paquete: String, silenciar: Boolean) {
		val nuevas = appsSilenciadas.toMutableSet().apply { if (silenciar) add(paquete) else remove(paquete) }
		p.edit().putStringSet(Claves.APPS_SILENCIADAS, nuevas).apply()
	}

	// --- 1.0.7: teclado braille (Can Talk) ------------------------------------------------

	/** La calibración del teclado braille («x,y;x,y;...», seis puntos en proporción), normal o en espejo; vacía, sin calibrar. */
	fun calibracionBraille(espejo: Boolean): String = cadena(if (espejo) Claves.BRAILLE_CALIBRACION_ESPEJO else Claves.BRAILLE_CALIBRACION, "")

	fun ponerCalibracionBraille(espejo: Boolean, valor: String) = poner(if (espejo) Claves.BRAILLE_CALIBRACION_ESPEJO else Claves.BRAILLE_CALIBRACION, valor)

	/** Los gestos del teclado braille que el usuario ha cambiado ([com.xebolax.tiresias.braille.GestosTeclado]). */
	var gestosTecladoBraille: String
		get() = cadena(Claves.BRAILLE_GESTOS, "")
		set(v) = poner(Claves.BRAILLE_GESTOS, v)

	/** Mayúscula automática al empezar una frase en el teclado braille, si el campo la pide. */
	val brailleMayusculaAuto: Boolean get() = logico(Claves.BRAILLE_MAYUSCULA_AUTO, false)
	/** Un sonido al escribir un espacio, borrar o Intro en el teclado braille. */
	val brailleSonarTeclas: Boolean get() = logico(Claves.BRAILLE_SONAR_TECLAS, false)
	/** Los sonidos del teclado braille: "android" (los del sistema) o "tiresias" (los del tema, por el canal del lector). */
	val brailleEstiloSonidos: String get() = cadena(Claves.BRAILLE_ESTILO_SONIDOS, "android")
	val brailleVolumenSonidos: Int get() = entero(Claves.BRAILLE_VOLUMEN_SONIDOS, 60).coerceIn(0, 100)

	/** Los emojis favoritos del teclado braille (es configuración: los elige el usuario). */
	var emojisFavoritos: List<String>
		get() = cadena(Claves.EMOJIS_FAVORITOS, "").split('\n').filter { it.isNotEmpty() }
		set(v) = poner(Claves.EMOJIS_FAVORITOS, v.joinToString("\n"))

	/** El dictado pide al reconocedor del sistema que no tape las palabras malsonantes. */
	val dictadoSinCensura: Boolean get() = logico(Claves.DICTADO_SIN_CENSURA, false)

	// --- 1.0.7: voz en las llamadas, Bluetooth e IA ------------------------------------------

	/** En una llamada, la voz y los sonidos a un porcentaje propio del volumen ([com.xebolax.tiresias.voz.VozEnLlamadas]). */
	val vozLlamadas: Boolean get() = logico(Claves.VOZ_LLAMADAS, false)
	var volumenLlamadas: Int
		get() = entero(Claves.VOLUMEN_LLAMADAS, 50).coerceIn(5, 100)
		set(v) = poner(Claves.VOLUMEN_LLAMADAS, v.coerceIn(5, 100))
	/** Compensar los cambios del volumen de la llamada (experimental). */
	val compensarLlamadas: Boolean get() = logico(Claves.COMPENSAR_LLAMADAS, false)

	/** Qué hace cada aparato Bluetooth: identidad → "altavoz" o "auriculares" (lo que no está, lo del sistema). */
	var reglasBluetooth: Map<String, String>
		get() = cadena(Claves.REGLAS_BLUETOOTH, "").split('\n').mapNotNull { l ->
			val i = l.lastIndexOf('=')
			if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
		}.toMap()
		set(v) = poner(Claves.REGLAS_BLUETOOTH, v.entries.joinToString("\n") { "${it.key}=${it.value}" })

	/** Pedir a Gemini que no bloquee lo que describe (Can Talk); apagado de fábrica. */
	val iaSinFiltros: Boolean get() = logico(Claves.IA_SIN_FILTROS, false)

	// --- 1.0.7: control de un PC con NVDA (NVDA Remote) ------------------------------------

	/** Los equipos guardados (JSON); la clave va cifrada con el almacén de claves de Android. */
	var nvdaEquipos: String
		get() = cadena(Claves.NVDA_EQUIPOS, "")
		set(v) = poner(Claves.NVDA_EQUIPOS, v)
	/** Las huellas de los certificados de confianza: «servidor:puerto=sha256» por línea. */
	var nvdaHuellas: String
		get() = cadena(Claves.NVDA_HUELLAS, "")
		set(v) = poner(Claves.NVDA_HUELLAS, v)
	/** Al bloquear el móvil con un PC conectado: "desconectar", "silenciar" o "seguir". */
	val nvdaAlBloquear: String get() = cadena(Claves.NVDA_AL_BLOQUEAR, "desconectar")

	// --- 1.0.7: asistencia remota --------------------------------------------------------------

	/** El nombre con el que este móvil se presenta en la asistencia remota (vacío: el del contacto de ayuda o «Tiresias»). */
	var ayudaNombre: String
		get() = cadena(Claves.AYUDA_NOMBRE, "")
		set(v) = poner(Claves.AYUDA_NOMBRE, v)
	/** Cómo se manda la petición de ayuda al contacto: "whatsapp", "sms" o "compartir". */
	var ayudaMetodo: String
		get() = cadena(Claves.AYUDA_METODO, "whatsapp")
		set(v) = poner(Claves.AYUDA_METODO, v)
	/** El lector en primer plano, con un aviso discreto, para que limpiar Recientes no lo cierre ([com.xebolax.tiresias.servicio.Guarda]). */
	val guardaActiva: Boolean get() = logico(Claves.GUARDA_ACTIVA, true)

	/** Preguntar también a las personas de confianza antes de dejarlas entrar. A las demás se les pregunta siempre. */
	val ayudaPreguntar: Boolean get() = logico(Claves.AYUDA_PREGUNTAR, true)
	/** Solo pueden entrar las personas de confianza (la primera entra sin espera; las siguientes, tras 24 horas). */
	val ayudaSoloConfianza: Boolean get() = logico(Claves.AYUDA_SOLO_CONFIANZA, true)
	/** A quién más se le pide ayuda si el contacto de ayuda no entra en unos minutos. */
	var ayudaContacto2: String
		get() = cadena(Claves.AYUDA_CONTACTO2, "")
		set(v) = p.edit().putString(Claves.AYUDA_CONTACTO2, v).apply()
	var ayudaNombre2: String
		get() = cadena(Claves.AYUDA_NOMBRE2, "")
		set(v) = p.edit().putString(Claves.AYUDA_NOMBRE2, v).apply()
	/** Usar la red Tor si los móviles no se ven directamente (datos móviles en los dos lados). */
	val ayudaTor: Boolean get() = logico(Claves.AYUDA_TOR, true)
	/** Lo que dura como mucho una ayuda, en minutos. */
	val ayudaMinutos: Int get() = cadena(Claves.AYUDA_MINUTOS, "60").toIntOrNull()?.coerceIn(5, 240) ?: 60
	/** Cada cuántos minutos se recuerda que alguien está ayudando (0, nunca). */
	val ayudaAviso: Int get() = cadena(Claves.AYUDA_AVISO, "5").toIntOrNull()?.coerceIn(0, 60) ?: 5
	/** Pedir al router que abra un puerto (UPnP o NAT-PMP) para que entren desde fuera de casa. */
	val ayudaRouter: Boolean get() = logico(Claves.AYUDA_ROUTER, true)

	// --- Actualizaciones ----------------------------------------------------------------

	var buscarActualizaciones: Boolean
		get() = logico(Claves.BUSCAR_ACTUALIZACIONES, true)
		set(v) = poner(Claves.BUSCAR_ACTUALIZACIONES, v)

	/** "servidor", "automatico" (servidor y GitHub), "github" o "personalizado". */
	var origenActualizaciones: String
		get() = cadena(Claves.ORIGEN_ACTUALIZACIONES, ORIGEN_AUTOMATICO)
		set(v) = poner(Claves.ORIGEN_ACTUALIZACIONES, v)

	var urlActualizaciones: String
		get() = cadena(Claves.URL_ACTUALIZACIONES, "")
		set(v) = poner(Claves.URL_ACTUALIZACIONES, v)

	var ultimaBusquedaActualizacion: Long
		get() = p.getLong(Claves.ULTIMA_BUSQUEDA, 0L)
		set(v) = poner(Claves.ULTIMA_BUSQUEDA, v)

	var versionOmitida: String
		get() = cadena(Claves.VERSION_OMITIDA, "")
		set(v) = poner(Claves.VERSION_OMITIDA, v)

	companion object {
		const val ARCHIVO = "ajustes"
		const val MOTOR_ESPEAK = "tiresias:espeak"
		const val MOTOR_PICO = "tiresias:pico"
		const val MOTOR_ELOQUENCE = "tiresias:eloquence"

		/**
		 * Los parámetros de Eloquence de fábrica, por voz (0 es Reed): los del
		 * driver IBMTTS del NVDA del autor (voz Reed, tono 65, entonación 35,
		 * volumen 95, aspereza 0, aliento 0). Lo que no está, el de la voz.
		 */
		val ELOQUENCE_DE_FABRICA: Map<Int, Map<Int, Int>> = mapOf(
			0 to mapOf(
				org.evvdroid.Eci.VOICE_PITCH_BASELINE to 65,
				org.evvdroid.Eci.VOICE_PITCH_FLUCTUATION to 35,
				org.evvdroid.Eci.VOICE_VOLUME to 95,
				org.evvdroid.Eci.VOICE_ROUGHNESS to 0,
				org.evvdroid.Eci.VOICE_BREATHINESS to 0,
			),
		)
		const val LEVANTAR_NO = "no"
		const val LEVANTAR_BARRA = "barra"
		const val LEVANTAR_PANTALLA = "pantalla"
		const val TECLAS_SIEMPRE = "siempre"
		const val TECLAS_CAMPOS = "campos"
		const val TECLAS_NUNCA = "nunca"
		const val DESPLAZAR_NORMAL = "normal"
		const val DESPLAZAR_SIN_SONIDO = "sin_sonido"
		const val DESPLAZAR_ANTICIPADO = "anticipado"

		/** El teclado braille de TalkBack habla solo. */
		val TECLADOS_QUE_HABLAN_DE_FABRICA = setOf("com.google.android.marvin.talkback", "com.android.talkback")

		/** Para que el cambio de motor y lo que cambian los gestos o Ajustes no se pisen. */
		private val CERROJO_VOZ = Any()

		const val VELOCIDAD_MIN = 30
		const val VELOCIDAD_MAX = 600
		const val TONO_MIN = 50
		const val TONO_MAX = 200
		const val ORIGEN_SERVIDOR = "servidor"
		const val ORIGEN_AUTOMATICO = "automatico"
		const val ORIGEN_GITHUB = "github"
		const val ORIGEN_PERSONALIZADO = "personalizado"

		inline fun <reified T : Enum<T>> enumDe(nombre: String, porDefecto: T): T =
			runCatching { enumValueOf<T>(nombre.uppercase()) }.getOrDefault(porDefecto)
	}
}

/** Claves de los ajustes (las mismas en `res/xml/ajustes_*.xml`). */
object Claves {
	const val IDIOMA = "idioma"
	const val ASISTENTE_HECHO = "asistente_hecho"
	const val VERSION_ANTERIOR = "version_anterior"
	const val MOTOR = "motor"
	const val VOZ_SISTEMA = "voz_sistema"
	const val VELOCIDAD = "velocidad"
	const val TONO = "tono"
	const val VOLUMEN_VOZ = "volumen_voz"
	/** El motor del que son la velocidad, el tono y el volumen de ahora. */
	const val MOTOR_DE_LA_VOZ = "motor_de_la_voz"
	/** «velocidad;tono;volumen» de cada motor que no es el elegido: voz_de_<motor>. */
	const val VOZ_DE = "voz_de_"
	/** La voz del sistema elegida de cada motor que no es el elegido: voz_sistema_de_<motor>. */
	const val VOZ_SISTEMA_DE = "voz_sistema_de_"
	const val VOCES_CONTROL = "voces_control"
	const val MOTOR_AVISOS = "motor_avisos"
	const val DETECTAR_IDIOMA = "detectar_idioma"
	const val DESPLAZAR_LISTAS = "desplazar_listas"
	const val TECLADOS_QUE_HABLAN = "teclados_que_hablan"
	/** El versionCode cuyas novedades ya se enseñaron al abrir la app. */
	const val NOVEDADES_VISTAS = "novedades_vistas"
	/** Las marcas de posición: JSON {app/pantalla: firma del elemento}. */
	const val MARCAS = "marcas"
	/** Los nombres propios de las pantallas: JSON {paquete/clase: nombre}. */
	const val NOMBRES_VENTANA = "nombres_ventana"
	/** Si el servicio estaba encendido la última vez (se apaga al quitarlo el usuario, no si lo cierra el sistema). */
	const val SERVICIO_ENCENDIDO = "servicio_encendido"
	/** El momento del último cierre de Tiresias por Android ya apuntado en el registro. */
	const val ULTIMA_SALIDA = "ultima_salida"
	const val ATENUAR = "atenuar"
	const val CAMBIO_IDIOMA = "cambio_idioma"
	const val CAMBIO_DIALECTO = "cambio_dialecto"
	const val CALLAR_AL_GRABAR = "callar_al_grabar"
	const val CALLAR_PROXIMIDAD = "callar_proximidad"
	const val DECIR_VOLUMEN = "decir_volumen"
	const val VOLUMEN_CURSOR = "volumen_cursor"
	const val PUNTUACION = "puntuacion"
	const val NUMEROS = "numeros"
	const val MAYUSCULAS = "mayusculas"
	const val REPETIDOS = "repetidos"
	const val EMOJIS = "emojis"
	const val MODO_EMOJIS = "modo_emojis"
	const val APPS_CLIC_DIRECTO = "apps_clic_directo"
	const val MENU_CIRCULAR = "menu_circular"
	const val FUENTES_COMPLEMENTOS = "fuentes_complementos"
	const val ALTAVOZ_AL_APARTAR = "altavoz_al_apartar"
	const val BRAILLE_TABLA_INFORMATICA = "braille_tabla_informatica"
	const val RECONOCER_PERSONAS = "reconocer_personas"
	const val TIC_DESPLAZAR = "tic_desplazar"
	const val ENCABEZADOS_TABLA_LUGAR = "encabezados_tabla_lugar"
	const val DECIR_FILA_COLUMNA = "decir_fila_columna"
	const val PERSONAS_NUBE = "personas_nube"
	const val TEMA_SONIDOS = "tema_sonidos"
	const val SONIDO_TIPO = "sonido_tipo"
	const val SONIDO_VERTICAL = "sonido_vertical"
	const val BRAILLE_SONAR_PUNTOS = "braille_sonar_puntos"
	const val PARTES_ESTADO = "partes_estado"
	const val AL_DESBLOQUEAR = "al_desbloquear"
	const val CALLAR_BLOQUEO = "callar_bloqueo"
	const val PALABRAS_CALLADAS = "palabras_calladas"
	const val BRAILLE_INTRO_ENVIA = "braille_intro_envia"
	const val SOLO_LO_ELEGIDO_AL_ENCENDER = "solo_lo_elegido_al_encender"
	const val CONTROLES_SOLO_WEB = "controles_solo_web"
	const val FOCO_SIN_ESPERAR_TITULO = "foco_sin_esperar_titulo"
	const val ACTIVAR_AL_LEVANTAR = "activar_al_levantar"
	const val TOQUE_DIRECTO_PAUSADO = "toque_directo_pausado"
	const val TECLAS_CORTAN = "teclas_cortan"
	const val INTRO_CORTA = "intro_corta"
	const val SALIDA_AUDIO = "salida_audio"
	const val ECO_TECLADO = "eco_teclado"
	const val NIVEL_VERBOSIDAD = "nivel_verbosidad"
	const val ORDEN = "orden"
	const val DECIR_ROL = "decir_rol"
	const val DECIR_ESTADO = "decir_estado"
	const val DECIR_POSICION = "decir_posicion"
	const val DECIR_PISTAS = "decir_pistas"
	const val ESPERA_PISTAS = "espera_pistas"
	const val DECIR_ACCIONES = "decir_acciones"
	const val DECIR_IDENTIFICADOR = "decir_identificador"
	const val DECIR_PANEL = "decir_panel"
	const val SONIDOS = "sonidos"
	const val VOLUMEN_SONIDOS = "volumen_sonidos"
	const val SONIDO_POSICIONAL = "sonido_posicional"
	const val VIBRACION = "vibracion"
	const val INTENSIDAD_VIBRACION = "intensidad_vibracion"
	const val DAR_VUELTA = "dar_vuelta"
	const val RECORDAR_FOCO = "recordar_foco"
	const val RETARDO_EXPLORACION = "retardo_exploracion"
	const val GESTOS = "gestos"
	const val ESTILO_TECLADO = "estilo_teclado"
	const val TECLAS = "teclas"
	const val TECLA_TIRESIAS = "tecla_tiresias"
	const val LEER_NOTIFICACIONES = "leer_notificaciones"
	const val NOTIFICACIONES_NO_MOLESTAR = "notificaciones_no_molestar"
	const val NOTIFICACIONES_EN_LLAMADA = "notificaciones_en_llamada"
	const val APPS_SILENCIADAS = "apps_silenciadas"
	const val ICONOS = "iconos"
	const val ENCABEZADOS_TABLA = "encabezados_tabla"
	const val FOCO_VENTANAS_NUEVAS = "foco_ventanas_nuevas"
	const val VOZ_MULTIMEDIA = "voz_multimedia"
	const val TOQUE_DIRECTO_TECLADO = "toque_directo_teclado"
	const val BRAILLE_ESPEJO = "braille_espejo"
	const val BRAILLE_SENSOR = "braille_sensor"
	const val BRAILLE_ECO = "braille_eco"
	const val BRAILLE_ACORDES = "braille_acordes"
	const val BRAILLE_CURSOR = "braille_cursor"
	const val LEER_MAS_SOLO = "leer_mas_solo"
	const val ELOQUENCE_VOZ = "eloquence_voz"
	const val ELOQUENCE_ESPANOL = "eloquence_espanol"
	const val ELOQUENCE_INGLES = "eloquence_ingles"
	const val ELOQUENCE_FRANCES = "eloquence_frances"
	const val ELOQUENCE_PAUSAS = "eloquence_pausas"
	const val ELOQUENCE_PREDICCION = "eloquence_prediccion"
	const val ELOQUENCE_ABREVIATURAS = "eloquence_abreviaturas"
	const val ELOQUENCE_MUESTREO = "eloquence_muestreo"
	const val ELOQUENCE_PARAMETRO = "eloquence_p"
	const val ELOQUENCE_DICCIONARIO = "eloquence_diccionario_"
	const val ELOQUENCE_VELOCIDAD = "eloquence_velocidad"
	const val MODO_SENIOR = "modo_senior"
	const val SENIOR_ANTES = "senior_antes"
	const val SENIOR_SOLO_MENSAJES = "senior_solo_mensajes"
	const val SENIOR_TOQUE_ACTIVA = "senior_toque_activa"
	const val SENIOR_CONTACTO = "senior_contacto"
	const val SENIOR_NOMBRE = "senior_nombre"
	const val SENIOR_CODIGO = "senior_codigo"
	const val ELOQUENCE_AUMENTO = "eloquence_aumento"
	/** Si ya se ha puesto la velocidad de Eloquence en su escala nueva ([Ajustes.normalizarEloquence]). */
	const val ELOQUENCE_VELOCIDAD_NORMAL = "eloquence_velocidad_normal"
	const val BRAILLE_VIBRAR_PUNTOS = "braille_vibrar_puntos"
	const val SONIDO_ESPERA = "sonido_espera"
	const val AVISO_ESPERA = "aviso_espera"
	const val ESCRIBIR_AL_LEVANTAR = "escribir_al_levantar"
	const val VOLUMEN_DESLIZADORES = "volumen_deslizadores"
	const val SONIDOS_APAGADOS = "sonidos_apagados"
	const val VIBRACIONES_APAGADAS = "vibraciones_apagadas"
	const val AL_ENCENDER = "al_encender"
	const val AL_APAGAR = "al_apagar"
	const val ORDEN_PARTES = "orden_partes"
	const val ARRASTRE_RESPALDO = "arrastre_respaldo"
	const val FONETICAS = "foneticas"
	const val CORTAR_AL_TOCAR = "cortar_al_tocar"
	const val TROCEAR_LARGOS = "trocear_largos"
	const val MENU_TIRESIAS = "menu_tiresias"
	const val CONTROLES_LECTURA = "controles_lectura"
	const val DESLIZADOR_ARRIBA_ABAJO = "deslizador_arriba_abajo"
	const val ACCIONES_ARRIBA_ABAJO = "acciones_arriba_abajo"
	const val API_PERMITIDAS = "api_permitidas"
	const val API_DENEGADAS = "api_denegadas"
	const val CONTINUA_INTERVALO = "continua_intervalo"
	const val CONTINUA_MINUTOS = "continua_minutos"
	const val BRAILLE_LINEA = "braille_linea"
	const val BRAILLE_TABLA = "braille_tabla"
	const val BRAILLE_MENSAJES = "braille_mensajes"
	const val BRAILLE_PRIMERO = "braille_primero"
	const val BUSCAR_ACTUALIZACIONES = "buscar_actualizaciones"
	const val ORIGEN_ACTUALIZACIONES = "origen_actualizaciones"
	const val URL_ACTUALIZACIONES = "url_actualizaciones"
	const val ULTIMA_BUSQUEDA = "ultima_busqueda_actualizacion"
	const val VERSION_OMITIDA = "version_omitida"
	const val BRAILLE_CALIBRACION = "braille_calibracion"
	const val BRAILLE_CALIBRACION_ESPEJO = "braille_calibracion_espejo"
	const val BRAILLE_GESTOS = "braille_gestos"
	const val BRAILLE_MAYUSCULA_AUTO = "braille_mayuscula_auto"
	const val BRAILLE_SONAR_TECLAS = "braille_sonar_teclas"
	const val BRAILLE_ESTILO_SONIDOS = "braille_estilo_sonidos"
	const val BRAILLE_VOLUMEN_SONIDOS = "braille_volumen_sonidos"
	const val EMOJIS_FAVORITOS = "emojis_favoritos"
	const val DICTADO_SIN_CENSURA = "dictado_sin_censura"
	const val VOZ_LLAMADAS = "voz_llamadas"
	const val VOLUMEN_LLAMADAS = "volumen_llamadas"
	const val COMPENSAR_LLAMADAS = "compensar_llamadas"
	const val REGLAS_BLUETOOTH = "reglas_bluetooth"
	const val IA_SIN_FILTROS = "ia_sin_filtros"
	const val NVDA_EQUIPOS = "nvda_equipos"
	const val NVDA_HUELLAS = "nvda_huellas"
	const val NVDA_AL_BLOQUEAR = "nvda_al_bloquear"
	const val GUARDA_ACTIVA = "guarda_activa"
	const val AYUDA_NOMBRE = "ayuda_nombre"
	const val AYUDA_METODO = "ayuda_metodo"
	const val AYUDA_PREGUNTAR = "ayuda_preguntar"
	const val AYUDA_MINUTOS = "ayuda_minutos"
	const val AYUDA_AVISO = "ayuda_aviso"
	const val AYUDA_ROUTER = "ayuda_router"
	const val AYUDA_TOR = "ayuda_tor"
	const val AYUDA_SOLO_CONFIANZA = "ayuda_solo_confianza"
	const val AYUDA_NUEVO_DESDE = "ayuda_nuevo_desde"
	const val AYUDA_CONTACTO2 = "ayuda_contacto2"
	const val AYUDA_NOMBRE2 = "ayuda_nombre2"
	const val AYUDA_HISTORIAL = "ayuda_historial"
}
