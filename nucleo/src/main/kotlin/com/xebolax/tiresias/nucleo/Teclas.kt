package com.xebolax.tiresias.nucleo

/**
 * Una combinación de teclado físico: la tecla (código de Android, `KeyEvent.KEYCODE_*`)
 * y los modificadores. [Mod.TIRESIAS] es la tecla Tiresias que elija el usuario
 * (Alt, Meta o Bloq Mayús/Insert en el mapa parecido al de Windows).
 */
data class Combinacion(val tecla: Int, val modificadores: Int = 0) {
	fun con(m: Int) = copy(modificadores = modificadores or m)

	/** «TIRESIAS+CTRL+62»: para guardarla. */
	fun escribir(): String = buildString {
		if (modificadores and Mod.TIRESIAS != 0) append("TIRESIAS+")
		if (modificadores and Mod.CTRL != 0) append("CTRL+")
		if (modificadores and Mod.ALT != 0) append("ALT+")
		if (modificadores and Mod.MAYUS != 0) append("MAYUS+")
		if (modificadores and Mod.META != 0) append("META+")
		append(tecla)
	}

	companion object {
		fun leer(s: String): Combinacion? {
			var m = 0
			var tecla: Int? = null
			for (p in s.split('+')) {
				when (p.trim().uppercase()) {
					"TIRESIAS" -> m = m or Mod.TIRESIAS
					"CTRL" -> m = m or Mod.CTRL
					"ALT" -> m = m or Mod.ALT
					"MAYUS" -> m = m or Mod.MAYUS
					"META" -> m = m or Mod.META
					else -> tecla = p.trim().toIntOrNull()
				}
			}
			return tecla?.let { Combinacion(it, m) }
		}
	}
}

/** Modificadores de [Combinacion]. */
object Mod {
	const val TIRESIAS = 1
	const val CTRL = 2
	const val ALT = 4
	const val MAYUS = 8
	const val META = 16
}

/** Códigos de tecla de Android que usa el mapa por defecto (iguales a `KeyEvent.KEYCODE_*`). */
object Tecla {
	const val CERO = 7
	const val UNO = 8
	const val SEIS = 13
	const val ARRIBA = 19
	const val ABAJO = 20
	const val IZQUIERDA = 21
	const val DERECHA = 22
	const val A = 29
	const val B = 30
	const val C = 31
	const val D = 32
	const val E = 33
	const val F = 34
	const val G = 35
	const val H = 36
	const val I = 37
	const val K = 39
	const val L = 40
	const val M = 41
	const val N = 42
	const val O = 43
	const val P = 44
	const val R = 46
	const val S = 47
	const val T = 48
	const val U = 49
	const val V = 50
	const val X = 52
	const val Z = 54
	const val COMA = 55
	const val PUNTO = 56
	const val TAB = 61
	const val ESPACIO = 62
	const val INTRO = 66
	const val RETROCESO = 67
	const val BARRA = 76
	const val RE_PAG = 92
	const val AV_PAG = 93
	const val ESCAPE = 111
	const val SUPRIMIR = 112
	const val BLOQ_MAYUS = 115
	const val INICIO = 122
	const val FIN = 123
	const val INSERTAR = 124
	const val F1 = 131
	const val F2 = 132
	const val F7 = 137
	const val F12 = 142
}

/** Qué mapa de teclado se usa por defecto. */
enum class EstiloTeclado { TALKBACK, TIRESIAS_WINDOWS }

/**
 * Las asignaciones de teclas a acciones. Los dos mapas por defecto:
 *
 * - [EstiloTeclado.TALKBACK]: el mapa nuevo de TalkBack (modificador Meta, aquí la tecla Tiresias).
 * - [EstiloTeclado.TIRESIAS_WINDOWS]: parecido al del Tiresias de Windows y a NVDA
 *   (tecla Tiresias = Insert o Bloq Mayús), para quien usa los dos.
 *
 * La navegación por letras (H encabezado, K enlace...) se resuelve aparte,
 * en el modo exploración ([letraNavegacion]).
 */
class MapaTeclas(private val asignaciones: Map<Combinacion, Accion>) {
	operator fun get(c: Combinacion): Accion? = asignaciones[c]

	val todas: Map<Combinacion, Accion> get() = asignaciones

	fun mas(cambios: Map<Combinacion, Accion>) = MapaTeclas(asignaciones + cambios)

	fun escribir(): String = asignaciones.entries.joinToString(";") { "${it.key.escribir()}=${it.value.name}" }

	companion object {
		private const val T = Mod.TIRESIAS

		private val COMUNES = mapOf(
			Combinacion(Tecla.DERECHA, T) to Accion.SIGUIENTE,
			Combinacion(Tecla.IZQUIERDA, T) to Accion.ANTERIOR,
			Combinacion(Tecla.ESPACIO, T) to Accion.ACTIVAR,
			Combinacion(Tecla.ESPACIO, T or Mod.MAYUS) to Accion.MANTENER,
			Combinacion(Tecla.ARRIBA, T or Mod.MAYUS) to Accion.CONTROL_ANTERIOR,
			Combinacion(Tecla.ABAJO, T or Mod.MAYUS) to Accion.CONTROL_SIGUIENTE,
			Combinacion(Tecla.ARRIBA, T) to Accion.SELECTOR_ANTERIOR,
			Combinacion(Tecla.ABAJO, T) to Accion.SELECTOR_SIGUIENTE,
			Combinacion(Tecla.IZQUIERDA, T or Mod.CTRL) to Accion.PRIMERO,
			Combinacion(Tecla.DERECHA, T or Mod.CTRL) to Accion.ULTIMO,
			Combinacion(Tecla.ABAJO, T or Mod.CTRL or Mod.ALT) to Accion.VENTANA_SIGUIENTE,
			Combinacion(Tecla.ARRIBA, T or Mod.CTRL or Mod.ALT) to Accion.VENTANA_ANTERIOR,
			Combinacion(Tecla.DERECHA, T or Mod.CTRL or Mod.ALT) to Accion.CONTENEDOR_SIGUIENTE,
			Combinacion(Tecla.IZQUIERDA, T or Mod.CTRL or Mod.ALT) to Accion.CONTENEDOR_ANTERIOR,
			Combinacion(Tecla.RETROCESO, T) to Accion.ATRAS,
			Combinacion(Tecla.H, T or Mod.CTRL) to Accion.INICIO,
			Combinacion(Tecla.N, T) to Accion.NOTIFICACIONES,
			Combinacion(Tecla.R, T or Mod.CTRL) to Accion.RECIENTES,
			Combinacion(Tecla.M, T or Mod.CTRL) to Accion.MENU_TIRESIAS,
			Combinacion(Tecla.A, T or Mod.CTRL) to Accion.MENU_ACCIONES,
			Combinacion(Tecla.BARRA, T or Mod.CTRL) to Accion.BUSCAR,
			Combinacion(Tecla.F, T or Mod.CTRL) to Accion.BUSCAR,
			Combinacion(Tecla.F7, T) to Accion.LISTA_ELEMENTOS,
			Combinacion(Tecla.INTRO, T or Mod.CTRL) to Accion.MULTIMEDIA,
			Combinacion(Tecla.ESCAPE, Mod.CTRL) to Accion.PAUSAR_VOZ,
			Combinacion(Tecla.S, T) to Accion.ALTERNAR_VOZ,
			Combinacion(Tecla.T, T) to Accion.LEER_TITULO,
			Combinacion(Tecla.F12, T) to Accion.ESTADO_SISTEMA,
			Combinacion(Tecla.C, T or Mod.CTRL) to Accion.COPIAR_ULTIMO,
			Combinacion(Tecla.D, T or Mod.CTRL) to Accion.DESCRIBIR_IA,
			Combinacion(Tecla.O, T or Mod.CTRL) to Accion.OCR_PANTALLA,
			Combinacion(Tecla.E, T or Mod.CTRL) to Accion.ETIQUETAR,
			Combinacion(Tecla.UNO, T) to Accion.AYUDA_ENTRADA,
			Combinacion(Tecla.Z, T or Mod.CTRL) to Accion.SUSPENDER,
			Combinacion(Tecla.N, T or Mod.CTRL) to Accion.AJUSTES_TIRESIAS,
			Combinacion(Tecla.PUNTO, T) to Accion.DONDE_ESTOY,
		)

		/** El mapa nuevo de TalkBack (Meta + ...), adaptado a la tecla Tiresias. */
		val TALKBACK = MapaTeclas(
			COMUNES + mapOf(
				Combinacion(Tecla.R, T or Mod.CTRL or Mod.MAYUS) to Accion.LEER_DESDE_ARRIBA,
				Combinacion(Tecla.R, T) to Accion.LEER_DESDE_AQUI,
				Combinacion(Tecla.L, T or Mod.CTRL) to Accion.IDIOMA_SIGUIENTE,
			)
		)

		/** Parecido a NVDA y al Tiresias de Windows (tecla Tiresias: Insert o Bloq Mayús). */
		val TIRESIAS_WINDOWS = MapaTeclas(
			COMUNES + mapOf(
				Combinacion(Tecla.B, T) to Accion.LEER_DESDE_ARRIBA,
				Combinacion(Tecla.ABAJO, T or Mod.MAYUS or Mod.CTRL) to Accion.LEER_DESDE_AQUI,
				Combinacion(Tecla.A, T) to Accion.LEER_DESDE_AQUI,
				Combinacion(Tecla.T, T) to Accion.LEER_TITULO,
				Combinacion(Tecla.F, T) to Accion.ESTADO_SISTEMA,
				Combinacion(Tecla.N, T) to Accion.MENU_TIRESIAS,
				Combinacion(Tecla.P, T) to Accion.PUNTUACION_SIGUIENTE,
				Combinacion(Tecla.F7, T) to Accion.LISTA_ELEMENTOS,
				Combinacion(Tecla.F, T or Mod.CTRL) to Accion.BUSCAR,
				Combinacion(Tecla.C, T) to Accion.LEER_PORTAPAPELES,
				Combinacion(Tecla.ESPACIO, T or Mod.CTRL) to Accion.MODO_SELECCION,
				Combinacion(Tecla.Z, T) to Accion.SUSPENDER,
			)
		)

		fun de(estilo: EstiloTeclado) = if (estilo == EstiloTeclado.TALKBACK) TALKBACK else TIRESIAS_WINDOWS

		fun leer(texto: String, base: MapaTeclas): MapaTeclas {
			val cambios = mutableMapOf<Combinacion, Accion>()
			for (par in texto.split(';')) {
				val (c, a) = par.split('=').takeIf { it.size == 2 } ?: continue
				val combinacion = Combinacion.leer(c) ?: continue
				val accion = runCatching { Accion.valueOf(a.trim()) }.getOrNull() ?: continue
				cambios[combinacion] = accion
			}
			return base.mas(cambios)
		}

		/**
		 * Navegación por letras en el modo exploración (web y documentos), como
		 * TalkBack y NVDA: la letra lleva al siguiente elemento de ese tipo; con
		 * Mayúsculas, al anterior. Devuelve el control o null si la letra no hace nada.
		 */
		fun letraNavegacion(tecla: Int): ControlLectura? = when (tecla) {
			Tecla.H -> ControlLectura.ENCABEZADOS
			Tecla.K -> ControlLectura.ENLACES
			Tecla.F -> ControlLectura.CONTROLES
			Tecla.B -> ControlLectura.BOTONES
			Tecla.E -> ControlLectura.CAMPOS
			Tecla.X -> ControlLectura.CASILLAS
			Tecla.L -> ControlLectura.LISTAS
			Tecla.T -> ControlLectura.TABLAS
			Tecla.D -> ControlLectura.REGIONES
			Tecla.G -> ControlLectura.IMAGENES
			else -> null
		}

		/** El nivel de encabezado de las teclas 1 a 6 en el modo exploración, o 0. */
		fun nivelEncabezado(tecla: Int): Int = if (tecla in Tecla.UNO..Tecla.SEIS) tecla - Tecla.UNO + 1 else 0
	}
}

