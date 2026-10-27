package com.xebolax.tiresias.nucleo

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Convierte un [Elemento] en lo que dice Tiresias al llegar a él.
 *
 * Reglas (de las pautas del agente android-accesible y de lo que se critica a
 * TalkBack):
 * - El orden de las partes lo elige el usuario ([Verbosidad.orden]).
 * - La descripción de rol y la de estado de la app mandan sobre las calculadas.
 * - Un interruptor dice «activado/desactivado»; una casilla, «marcado/no marcado».
 * - Nada se repite: si el nombre ya es el valor, el valor no se vuelve a decir.
 * - Un control sin nombre dice su identificador legible («botón enviar») en vez
 *   de un «sin nombre» que no ayuda.
 * - Las pistas van aparte ([pista]): se dicen tras una pausa, no pegadas.
 */
class Compositor(
	private val textos: Plantillas,
	locale: Locale = Locale("es", "ES"),
	/** Para decir los identificadores (casi siempre en inglés) en el idioma de Tiresias. */
	private val identificadores: Identificadores = Identificadores.VACIO,
	/** Lo que las pistas dicen de los gestos del usuario (Backtalk 123 y 130). */
	private val gestos: GestosDePistas = GestosDePistas(),
) {
	/**
	 * Los gestos que nombran las pistas: el del menú de acciones (null si no
	 * tiene, y entonces se dice el menú) y si se activa al levantar el dedo.
	 */
	data class GestosDePistas(val menuAcciones: String? = "", val alLevantar: Boolean = false, val accionesArribaAbajo: Boolean = false)


	private val decimal = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale))

	/** La frase completa del elemento (sin la pista de uso). */
	fun describir(e: Elemento, v: Verbosidad = Verbosidad()): String {
		val partes = mutableListOf<String>()
		val nombre = nombre(e, v)
		for (parte in v.orden) {
			when (parte) {
				Parte.NOMBRE -> partes.addIfNotBlank(nombre)
				Parte.VALOR -> valor(e, v)?.takeIf { !it.equals(nombre, ignoreCase = true) }?.let { partes.addIfNotBlank(it) }
				Parte.ROL -> if (v.decirRol) rol(e, v)?.let { partes.addIfNotBlank(it) }
				Parte.ESTADO -> if (v.decirEstado) estados(e).forEach { partes.addIfNotBlank(it) }
				Parte.POSICION -> if (v.decirPosicion) posicion(e, v)?.let { partes.addIfNotBlank(it) }
				Parte.ERROR -> e.error?.takeIf { it.isNotBlank() }?.let { partes += textos.formato(Frase.ERROR, it) }
				Parte.COMPLEMENTARIA -> if (v.decirComplementaria) e.complementaria?.takeIf { it.isNotBlank() && !it.equals(nombre, true) }?.let { partes += it }
				Parte.PISTA -> Unit
			}
		}
		if (v.decirAcciones && e.tieneAcciones) partes += textos.de(Frase.TIENE_ACCIONES)
		if (v.decirAcciones && e.enlaces > 0) partes += textos.formatoPlural(FrasePlural.ENLACES, e.enlaces, e.enlaces)
		if (e.faltas > 0) partes += textos.formatoPlural(FrasePlural.FALTAS, e.faltas, e.faltas)
		val frase = partes.distinctBy { it.lowercase() }.joinToString(v.separador)
		val panel = e.tituloPanel?.takeIf { v.decirPanel && it.isNotBlank() && !frase.startsWith(it, ignoreCase = true) }
		return if (panel != null) textos.formato(Frase.PANEL, panel) + v.separador + frase else frase
	}

	/** El nombre que se dice: la etiqueta del usuario, el nombre o, sin nombre, el identificador. */
	fun nombre(e: Elemento, v: Verbosidad = Verbosidad()): String {
		e.etiquetaPersonal?.takeIf { it.isNotBlank() }?.let { return it }
		if (e.nombre.isNotBlank()) return e.nombre
		if (!esControl(e)) return ""
		// Un campo con texto (o con su pista como texto, en Android 7) se reconoce por él: ni «sin nombre» ni identificador
		if (e.editable && e.texto.isNotBlank()) return ""
		val id = e.identificador?.let { identificadores.legible(it) }
		return if (v.decirIdentificador && !id.isNullOrBlank()) textos.formato(Frase.SIN_NOMBRE_ID, id) else textos.de(Frase.SIN_NOMBRE)
	}

	/** El rol que se dice, o null si no hay que decirlo. */
	fun rol(e: Elemento, v: Verbosidad = Verbosidad()): String? {
		// La descripción de rol de la app manda, salvo en los encabezados: la de Chrome es «heading 1»
		// (en el idioma de la app, no en el de Tiresias) y repetiría «encabezado de nivel 1»
		e.descripcionRol?.takeIf { it.isNotBlank() && !e.encabezado }?.let { return conEncabezado(it, e, v) }
		val base = when (e.rol) {
			Rol.NINGUNO, Rol.TEXTO, Rol.ELEMENTO_LISTA, Rol.CELDA -> null
			Rol.BOTON, Rol.BOTON_IMAGEN -> textos.de(Frase.ROL_BOTON)
			Rol.BOTON_ALTERNAR -> textos.de(Frase.ROL_BOTON_ALTERNAR)
			Rol.CASILLA -> textos.de(Frase.ROL_CASILLA)
			Rol.INTERRUPTOR -> textos.de(Frase.ROL_INTERRUPTOR)
			Rol.OPCION -> textos.de(Frase.ROL_OPCION)
			Rol.CAMPO_TEXTO -> textos.de(if (e.contrasena) Frase.ROL_CAMPO_CONTRASENA else Frase.ROL_CAMPO_TEXTO)
			Rol.DESLIZADOR -> textos.de(Frase.ROL_DESLIZADOR)
			Rol.BARRA_PROGRESO -> textos.de(Frase.ROL_BARRA_PROGRESO)
			Rol.DESPLEGABLE -> textos.de(Frase.ROL_DESPLEGABLE)
			Rol.LISTA -> textos.de(Frase.ROL_LISTA)
			Rol.CUADRICULA -> textos.de(Frase.ROL_CUADRICULA)
			Rol.TABLA -> textos.de(Frase.ROL_TABLA)
			Rol.PESTANA -> textos.de(Frase.ROL_PESTANA)
			Rol.LISTA_PESTANAS -> textos.de(Frase.ROL_LISTA_PESTANAS)
			Rol.PAGINADOR -> textos.de(Frase.ROL_PAGINADOR)
			Rol.ENLACE -> textos.de(Frase.ROL_ENLACE)
			Rol.IMAGEN -> textos.de(Frase.ROL_IMAGEN)
			Rol.MENU -> textos.de(Frase.ROL_MENU)
			Rol.ELEMENTO_MENU -> textos.de(Frase.ROL_ELEMENTO_MENU)
			Rol.BARRA_HERRAMIENTAS -> textos.de(Frase.ROL_BARRA_HERRAMIENTAS)
			Rol.DIALOGO -> textos.de(Frase.ROL_DIALOGO)
			Rol.ALERTA -> textos.de(Frase.ROL_ALERTA)
			Rol.DESPLAZABLE -> null
			Rol.WEB -> textos.de(Frase.ROL_WEB)
			Rol.SELECTOR_FECHA -> textos.de(Frase.ROL_SELECTOR_FECHA)
			Rol.SELECTOR_HORA -> textos.de(Frase.ROL_SELECTOR_HORA)
			Rol.SELECTOR_NUMERO -> textos.de(Frase.ROL_SELECTOR_NUMERO)
			Rol.CHIP -> textos.de(Frase.ROL_CHIP)
			Rol.VIDEO -> textos.de(Frase.ROL_VIDEO)
			Rol.MAPA -> textos.de(Frase.ROL_MAPA)
			Rol.TECLA -> null
			Rol.CALENDARIO -> textos.de(Frase.ROL_CALENDARIO)
			Rol.EDITOR_VALOR -> textos.de(Frase.ROL_EDITOR_VALOR)
			Rol.REGION -> textos.de(Frase.ROL_REGION)
			Rol.NAVEGACION -> textos.de(Frase.ROL_NAVEGACION)
			Rol.ESTADO -> null
		}
		// Un botón que es un enlace web: «enlace», no «botón»
		return conEncabezado(base, e, v)
	}

	private fun conEncabezado(rol: String?, e: Elemento, v: Verbosidad): String? {
		if (!e.encabezado || !v.decirEncabezado) return rol
		val encabezado = if (e.nivelEncabezado in 1..6) textos.formato(Frase.ENCABEZADO_NIVEL, e.nivelEncabezado) else textos.de(Frase.ENCABEZADO)
		return if (rol == null) encabezado else rol + v.separador + encabezado
	}

	/** Los estados que se dicen, en orden. La descripción de estado de la app manda. */
	fun estados(e: Elemento): List<String> {
		val r = mutableListOf<String>()
		val propio = e.estado?.takeIf { it.isNotBlank() }
		if (propio != null && e.rango == null) {
			r += propio
		} else {
			when (e.marcado) {
				Marcado.MARCADO -> r += textos.de(if (e.rol == Rol.INTERRUPTOR) Frase.ACTIVADO else if (e.rol == Rol.BOTON_ALTERNAR) Frase.PULSADO else Frase.MARCADO)
				Marcado.NO_MARCADO -> r += textos.de(if (e.rol == Rol.INTERRUPTOR) Frase.DESACTIVADO else if (e.rol == Rol.BOTON_ALTERNAR) Frase.NO_PULSADO else Frase.NO_MARCADO)
				Marcado.PARCIAL -> r += textos.de(Frase.PARCIAL)
				Marcado.NO_MARCABLE -> Unit
			}
		}
		// «Seleccionado» no se repite en las pestañas y opciones que ya dicen marcado
		if (e.seleccionado && e.marcado == Marcado.NO_MARCABLE) r += textos.de(Frase.SELECCIONADO)
		when (e.expansion) {
			Expansion.EXPANDIDO -> r += textos.de(Frase.EXPANDIDO)
			Expansion.CONTRAIDO -> r += textos.de(Frase.CONTRAIDO)
			Expansion.EXPANDIDO_PARCIAL -> r += textos.de(Frase.EXPANDIDO_PARCIAL)
			Expansion.NINGUNA -> Unit
		}
		if (e.obligatorio) r += textos.de(Frase.OBLIGATORIO)
		if (e.visitado && e.rol == Rol.ENLACE) r += textos.de(Frase.ENLACE_VISITADO)
		if (e.escribiendo) r += textos.de(Frase.EDITANDO)
		if (!e.activo) r += textos.de(Frase.NO_DISPONIBLE)
		return r
	}

	/** El valor: el texto de un campo o el de un rango. */
	fun valor(e: Elemento, v: Verbosidad = Verbosidad()): String? {
		e.rango?.let { return valorRango(it, e, v) }
		if (e.editable || e.rol == Rol.CAMPO_TEXTO) {
			if (e.contrasena) return e.texto.takeIf { it.isNotEmpty() }?.let { textos.formatoPlural(FrasePlural.CARACTERES, it.length, it.length) }
			return e.texto.takeIf { it.isNotBlank() } ?: e.pista?.takeIf { it.isNotBlank() && !it.equals(e.nombre, true) }
		}
		return null
	}

	private fun valorRango(r: Rango, e: Elemento, v: Verbosidad): String? {
		if (r.indeterminado) return textos.de(Frase.CARGANDO)
		e.estado?.takeIf { it.isNotBlank() }?.let { return it }
		r.texto?.takeIf { it.isNotBlank() }?.let { return it }
		return when {
			// Rango en por ciento, o barra de progreso (lo que importa es cuánto falta)
			r.tipo == 2 -> textos.formato(Frase.POR_CIENTO, Math.round(r.actual))
			e.rol == Rol.BARRA_PROGRESO -> textos.formato(Frase.POR_CIENTO, r.porCiento)
			r.minimo == 0f && r.maximo == 100f -> textos.formato(Frase.POR_CIENTO, Math.round(r.actual))
			v.porCientoRango && r.maximo > r.minimo -> textos.formato(Frase.VALOR_DE_RANGO, decimal.format(r.actual), decimal.format(r.minimo), decimal.format(r.maximo))
			else -> decimal.format(r.actual)
		}
	}

	/** «3 de 10» en listas; «fila 2, columna 3» en tablas y cuadrículas, con sus encabezados. */
	fun posicion(e: Elemento, v: Verbosidad = Verbosidad()): String? {
		val p = e.posicion ?: return null
		if (p.fila <= 0 && p.columna <= 0) return null
		val encabezados = mutableListOf<String>()
		if (v.decirEncabezadosTabla) {
			p.encabezadoFila?.takeIf { it.isNotBlank() && !it.equals(e.nombre, true) }?.let { encabezados += it }
			p.encabezadoColumna?.takeIf { it.isNotBlank() && !it.equals(e.nombre, true) }?.let { encabezados += it }
		}
		val tabla = p.totalColumnas > 1 || p.columna > 1
		val donde = when {
			tabla -> if (v.decirFilaColumna) textos.formato(Frase.FILA_COLUMNA, p.fila, p.columna) else null
			p.totalFilas > 0 -> textos.formato(Frase.POSICION, p.fila, p.totalFilas)
			else -> null
		}
		val partes = if (v.encabezadosDespues) listOfNotNull(donde) + encabezados else encabezados + listOfNotNull(donde)
		return partes.joinToString(", ").ifBlank { null }
	}

	/** La pista de uso, que se dice tras una pausa (o nunca, según la verbosidad). */
	fun pista(e: Elemento, v: Verbosidad = Verbosidad()): String? {
		if (!v.decirPistas || !e.activo) return null
		e.pista?.takeIf { it.isNotBlank() && e.rol != Rol.CAMPO_TEXTO && !it.equals(e.nombre, true) }?.let { return it }
		val uso = pistaDeUso(e)
		// Con acciones propias y «arriba y abajo eligen las acciones», también cómo se eligen
		if (gestos.accionesArribaAbajo && (e.tieneAcciones || e.enlaces > 0) && e.rol != Rol.DESLIZADOR && !e.editable) {
			val acciones = textos.de(Frase.PISTA_ACCIONES_ARRIBA_ABAJO)
			return if (uso == null || !e.pulsable && !e.mantenible) acciones else "$uso $acciones"
		}
		return uso
	}

	private fun pistaDeUso(e: Elemento): String? {
		return when {
			e.editable && !e.escribiendo -> textos.de(Frase.PISTA_EDITAR)
			e.rol == Rol.DESLIZADOR -> textos.de(Frase.PISTA_DESLIZADOR)
			e.expansion == Expansion.CONTRAIDO -> textos.de(Frase.PISTA_EXPANDIR)
			e.expansion == Expansion.EXPANDIDO -> textos.de(Frase.PISTA_CONTRAER)
			// Con «activar al levantar el dedo», lo que se pulsa no necesita doble toque
			gestos.alLevantar && e.pulsable && e.marcado == Marcado.NO_MARCABLE -> textos.de(Frase.PISTA_LEVANTAR)
			e.marcado != Marcado.NO_MARCABLE && e.pulsable -> textos.de(Frase.PISTA_ALTERNAR)
			e.pulsable && e.mantenible -> textos.de(Frase.PISTA_PULSAR) + " " + textos.de(Frase.PISTA_MANTENER)
			e.pulsable -> textos.de(Frase.PISTA_PULSAR)
			e.mantenible -> textos.de(Frase.PISTA_MANTENER)
			e.tieneAcciones || e.enlaces > 0 -> pistaAcciones()
			else -> null
		}
	}

	/** Cómo ver las acciones: con el gesto que tiene el usuario para ello, o el de fábrica si no se sabe. */
	private fun pistaAcciones(): String = when (val g = gestos.menuAcciones) {
		"" -> textos.de(Frase.PISTA_ACCIONES)
		null -> textos.de(Frase.PISTA_ACCIONES)
		else -> textos.formato(Frase.PISTA_ACCIONES_GESTO, g)
	}

	/** El nombre de la lista o la cuadrícula al entrar en ella («Lista con 12 elementos»). */
	fun entrarEnColeccion(rol: Rol, filas: Int, columnas: Int): String? = when {
		filas <= 0 -> null
		columnas > 1 -> textos.formatoPlural(FrasePlural.CUADRICULA_FILAS, filas, filas) + ", " + textos.formatoPlural(FrasePlural.COLUMNAS, columnas, columnas)
		rol == Rol.CUADRICULA || rol == Rol.TABLA -> textos.formatoPlural(FrasePlural.CUADRICULA_FILAS, filas, filas)
		else -> textos.formatoPlural(FrasePlural.LISTA_ELEMENTOS, filas, filas)
	}

	/** Un control (o una imagen) sin nombre ni etiqueta propia: lo que Tiresias intenta reconocer. */
	fun sinNombre(e: Elemento) = e.etiquetaPersonal.isNullOrBlank() && e.nombre.isBlank() && esControl(e) && !(e.editable && e.texto.isNotBlank())

	private fun esControl(e: Elemento) = e.pulsable || e.mantenible || e.editable || e.marcado != Marcado.NO_MARCABLE ||
		e.rol in setOf(Rol.BOTON, Rol.BOTON_IMAGEN, Rol.CASILLA, Rol.INTERRUPTOR, Rol.OPCION, Rol.CAMPO_TEXTO, Rol.DESLIZADOR, Rol.DESPLEGABLE, Rol.ENLACE, Rol.IMAGEN, Rol.PESTANA)

	private fun MutableList<String>.addIfNotBlank(s: String) {
		if (s.isNotBlank()) add(s.trim())
	}

	companion object {
		/**
		 * Un identificador de vista legible: `com.app:id/boton_enviar_mensaje` →
		 * «boton enviar mensaje»; `btnSendMessage` → «btn send message». Los
		 * identificadores generados (números, «view123») no sirven y dan null.
		 */
		fun legible(id: String): String? {
			val corto = id.substringAfterLast('/').substringAfterLast(':')
			if (corto.isBlank() || corto.all { it.isDigit() }) return null
			val separado = corto
				.replace(Regex("([a-z])([A-Z])"), "$1 $2")
				.replace('_', ' ').replace('-', ' ').replace('.', ' ')
				.replace(Regex("\\s+"), " ").trim().lowercase()
			if (Regex("^(view|layout|container|root|frame|linear|relative|constraint)\\s*\\d*$").matches(separado)) return null
			return separado.takeIf { it.any { c -> c.isLetter() } }
		}
	}
}
