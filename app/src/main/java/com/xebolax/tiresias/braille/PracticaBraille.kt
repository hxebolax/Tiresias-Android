package com.xebolax.tiresias.braille

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import com.xebolax.tiresias.R
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.braille.BuscadorEmojis
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Tipo

/**
 * Lo que se pide escribir en una práctica: [texto] (una letra, un signo o una
 * palabra) y sus celdas braille en orden, con el signo de mayúscula o de número
 * delante cuando hace falta. Con [pista], la instrucción ya dice los puntos.
 */
class Objetivo(val texto: String, val celdas: List<Int>, val pista: Boolean)

/** Las celdas braille de un texto (signografía española, la misma del teclado), para el tutorial. */
object CeldasBraille {
	/** Las celdas de [texto], o null si tiene algo que el teclado braille no escribe. */
	fun de(texto: String): List<Int>? {
		val celdas = ArrayList<Int>()
		var enNumero = false
		for (c in texto) {
			val s = c.toString()
			if (c.isDigit()) {
				if (!enNumero) celdas += TecladoBraille.SIGNO_DE_NUMERO
				enNumero = true
				celdas += TecladoBraille.celdaDeCifra(s) ?: return null
				continue
			}
			enNumero = false
			if (c.isUpperCase()) celdas += TecladoBraille.SIGNO_DE_MAYUSCULA
			celdas += TecladoBraille.celdaDe(s.lowercase()) ?: return null
		}
		return celdas
	}

	/** «punto 1», «puntos 1 y 2», «puntos 1, 2 y 4». */
	fun describir(c: Context, puntos: Int): String {
		val lista = (1..6).filter { puntos and (1 shl (it - 1)) != 0 }.map { it.toString() }
		return when (lista.size) {
			0 -> ""
			1 -> c.getString(R.string.tb_punto, lista[0])
			else -> c.getString(R.string.tb_puntos, lista.dropLast(1).joinToString(", "), lista.last())
		}
	}

	/** Lo que es una celda sola: «signo de mayúscula», la letra o el signo, o null si no es nada. */
	fun nombre(c: Context, puntos: Int): String? = when (puntos) {
		TecladoBraille.SIGNO_DE_MAYUSCULA -> c.getString(R.string.braille_mayuscula)
		TecladoBraille.SIGNO_DE_NUMERO -> c.getString(R.string.braille_numero)
		else -> TecladoBraille.letra(puntos)
	}
}

/** Qué se practica en una lección del tutorial del teclado braille. */
sealed class Practica {
	/** Tocar con un dedo y oír qué punto es, hasta encontrar los seis. */
	object Puntos : Practica()
	/** La calibración punto a punto del teclado. */
	object Calibrar : Practica()
	/** Escribir cada objetivo, uno detrás de otro. */
	class Escribir(val objetivos: List<Objetivo>) : Practica()
	/** Hacer el gesto de cada acción del teclado (con los gestos que tenga puestos el usuario). */
	class Gestos(val acciones: List<AccionTeclado>) : Practica()
	/** Escribir lo que se quiera: cada letra se dice y se puede oír todo lo escrito. */
	object Libre : Practica()
	/** Buscar un emoji por su nombre, recorrer lo encontrado, ponerlo en favoritos y elegirlo (como en el teclado). */
	object Emojis : Practica()
}

/**
 * La práctica del tutorial del teclado braille: una superficie braille encima de
 * todo, igual que el teclado (los dedos llegan tal cual), que pide cada cosa,
 * dice si está bien y, si no, qué se ha escrito y qué tocaba. Se sale siempre
 * con dos dedos hacia arriba (y con las dos teclas de volumen, como cualquier
 * superficie). Al terminar todo, se quita sola y se vuelve al tutorial.
 */
class ModoPracticaBraille(lector: Lector, private val practica: Practica) : ModoSuperficie(lector) {
	private val principal = Handler(Looper.getMainLooper())
	private var vista: SuperficieBraille? = null
	private val gestos = GestosTeclado.leer(lector.ajustes.gestosTecladoBraille)

	// El estado de la práctica: solo se toca en el hilo del lector
	private val puntosVistos = HashSet<Int>()
	private var actual = 0
	private var posicion = 0
	private var fallos = 0
	private val escrito = StringBuilder()
	private var mayuscula = false
	private var numero = false
	private var terminada = false
	/** El primer anuncio de la calibración va detrás del aviso de la práctica, sin cortarlo. */
	@Volatile private var primerAnuncio = true

	/** Los objetivos y gestos que de verdad se piden: el de salir (dos dedos hacia arriba) no se puede practicar aquí. */
	private val acciones: List<AccionTeclado> = (practica as? Practica.Gestos)?.acciones.orEmpty()
		.filter { a -> gestoDe(a)?.let { it.dedos == 2 && it.direccion == DireccionBraille.ARRIBA } == false }

	override val sinPasoDeToques: Boolean = true
	override val bloquearGiro: Boolean = true
	override val cerrarAlCambiarDeApp: Boolean = true
	override val rotulo: String get() = t.getString(R.string.braille_rotulo_practica)

	override fun avisoAlCerrar(): String = t.getString(if (terminada) R.string.tb_vuelta_terminada else R.string.tb_vuelta)

	override fun aviso(): String = when (practica) {
		Practica.Puntos -> t.getString(R.string.tb_aviso_puntos)
		Practica.Calibrar -> t.getString(R.string.tb_aviso_calibrar)
		is Practica.Escribir -> t.getString(R.string.tb_aviso_escribir)
		is Practica.Gestos -> t.getString(R.string.tb_aviso_gestos)
		Practica.Libre -> t.getString(R.string.tb_aviso_libre, ayudaLibre())
		Practica.Emojis -> t.getString(R.string.tb_aviso_emojis)
	}

	override fun crearVista(tocado: () -> Unit): View {
		val oyente = object : OyenteSuperficie {
			override fun alPuntos(puntos: Int) {
				tocado()
				lector.manejador.post { if (puesta) this@ModoPracticaBraille.alPuntos(puntos) }
			}
			override fun alGesto(g: GestoBraille) {
				tocado()
				lector.manejador.post { if (puesta) this@ModoPracticaBraille.alGesto(g) }
			}
			override fun alApoyar() {
				val a = lector.ajustes
				if (a.brailleVibrarPuntos) vista?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
				if (a.brailleSonarPuntos) lector.sonidos.tocar(Sonido.TECLA, volumen = a.brailleVolumenSonidos / 100f)
			}
			override fun anunciar(texto: String) {
				val encolar = primerAnuncio
				primerAnuncio = false
				lector.manejador.post { lector.decir(texto, if (encolar) Modo.ENCOLAR else Modo.INTERRUMPIR, Tipo.NAVEGACION) }
			}
			override fun espejo(): Boolean = lector.ajustes.brailleEspejo
		}
		val v = SuperficieBraille(lector.servicio, oyente, lector.textos, parteDelAlto = 0f, rotulo = rotulo)
		vista = v
		when (practica) {
			// Tras el aviso, la calibración pide el primer punto
			Practica.Calibrar -> principal.postDelayed({
				if (puesta) v.calibrar { hecha -> if (hecha) lector.manejador.post { terminar(null) } }
			}, ESPERA_PRIMERA_MS)
			// Lo primero que se pide, a la cola detrás del aviso (que se pone al añadir la vista)
			is Practica.Escribir, is Practica.Gestos -> lector.manejador.postDelayed({ if (puesta) pedir(encolar = true) }, ESPERA_PRIMERA_MS)
			else -> Unit
		}
		return v
	}

	// --- Lo que llega de los dedos (en el hilo del lector) ----------------------------------

	override fun alPuntos(puntos: Int) {
		when (practica) {
			Practica.Puntos -> puntoTocado(puntos)
			Practica.Calibrar -> Unit
			is Practica.Escribir -> celdaEscrita(practica.objetivos, puntos)
			is Practica.Gestos -> decir(t.getString(R.string.tb_gestos_no_puntos))
			Practica.Libre -> escribirLibre(puntos)
			Practica.Emojis -> letraEmoji(puntos)
		}
	}

	override fun alGesto(g: GestoBraille) {
		// Dos dedos hacia arriba: se sale, siempre
		if (g.dedos == 2 && g.direccion == DireccionBraille.ARRIBA) {
			cerrar()
			return
		}
		when (practica) {
			Practica.Puntos -> decir(t.getString(R.string.tb_puntos_un_dedo))
			Practica.Calibrar -> Unit
			is Practica.Escribir -> pedir()
			is Practica.Gestos -> gestoHecho(g)
			Practica.Libre -> gestoLibre(g)
			Practica.Emojis -> gestoEmoji(g)
		}
	}

	// --- Dónde están los puntos ---------------------------------------------------------------

	private fun puntoTocado(puntos: Int) {
		val descrito = CeldasBraille.describir(t, puntos)
		if (Integer.bitCount(puntos) != 1) {
			decir(descrito + ". " + t.getString(R.string.tb_puntos_un_dedo))
			return
		}
		val nuevo = puntosVistos.add(puntos)
		if (puntosVistos.size == 6) {
			decir(descrito + ". " + t.getString(R.string.tb_puntos_todos))
			terminar(null)
			return
		}
		decir(if (nuevo) t.resources.getQuantityString(R.plurals.tb_puntos_faltan, 6 - puntosVistos.size, descrito, 6 - puntosVistos.size) else descrito)
	}

	// --- Escribir lo que se pide --------------------------------------------------------------

	/** Dice lo que toca escribir ahora (con los puntos si es de las nuevas o si ya se ha fallado). */
	private fun pedir(encolar: Boolean = false) {
		val primero = if (encolar) Modo.ENCOLAR else Modo.INTERRUMPIR
		val p = practica as? Practica.Escribir ?: run {
			pedirGesto(primero)
			return
		}
		val o = p.objetivos.getOrNull(actual) ?: return
		val voz = lector.voz
		if (o.texto.length == 1) {
			voz.decir(Enunciado(t.getString(R.string.tb_escribe), primero, Tipo.NAVEGACION))
			voz.decir(Enunciado(o.texto, Modo.ENCOLAR, Tipo.NAVEGACION, deletrear = true))
			val puntos = o.celdas.joinToString(t.getString(R.string.tb_y_despues)) { CeldasBraille.describir(t, it) }
			if (o.pista || fallos > 0) voz.decir(Enunciado(puntos, Modo.ENCOLAR, Tipo.NAVEGACION))
		} else {
			val esNumero = o.texto.all { it.isDigit() }
			voz.decir(Enunciado(t.getString(if (esNumero) R.string.tb_escribe_numero else R.string.tb_escribe_palabra, o.texto), primero, Tipo.NAVEGACION))
			if (posicion > 0) voz.decir(Enunciado(t.getString(R.string.tb_vas_por, o.texto.take(letrasEscritas(o))), Modo.ENCOLAR, Tipo.NAVEGACION))
			if (o.pista || fallos > 0) pistaCelda(o)
		}
	}

	/** Cuántas letras de la palabra van escritas (los signos de mayúscula y número no son letras). */
	private fun letrasEscritas(o: Objetivo): Int {
		var letras = 0
		for (i in 0 until posicion) if (o.celdas[i] != TecladoBraille.SIGNO_DE_MAYUSCULA && o.celdas[i] != TecladoBraille.SIGNO_DE_NUMERO) letras++
		return letras
	}

	/** Lo que toca ahora en una palabra: «Ahora: s, puntos 2, 3 y 4». */
	private fun pistaCelda(o: Objetivo) {
		val celda = o.celdas.getOrNull(posicion) ?: return
		val voz = lector.voz
		voz.decir(Enunciado(t.getString(R.string.tb_ahora), Modo.ENCOLAR, Tipo.NAVEGACION))
		when (celda) {
			TecladoBraille.SIGNO_DE_MAYUSCULA, TecladoBraille.SIGNO_DE_NUMERO ->
				voz.decir(Enunciado(CeldasBraille.nombre(t, celda).orEmpty(), Modo.ENCOLAR, Tipo.NAVEGACION))
			else -> {
				val letra = o.texto.getOrNull(letrasEscritas(o))?.toString()?.lowercase().orEmpty()
				voz.decir(Enunciado(letra, Modo.ENCOLAR, Tipo.NAVEGACION, deletrear = true))
			}
		}
		voz.decir(Enunciado(CeldasBraille.describir(t, celda), Modo.ENCOLAR, Tipo.NAVEGACION))
	}

	private fun celdaEscrita(objetivos: List<Objetivo>, puntos: Int) {
		lector.manejador.removeCallbacks(pedirPendiente)
		val o = objetivos.getOrNull(actual) ?: return
		val esperada = o.celdas.getOrNull(posicion) ?: return
		if (puntos == esperada) {
			posicion++
			if (posicion < o.celdas.size) {
				// Una parte bien (el signo de mayúscula, una letra o cifra de la palabra): se dice y se sigue
				if (puntos == TecladoBraille.SIGNO_DE_MAYUSCULA || puntos == TecladoBraille.SIGNO_DE_NUMERO) decir(CeldasBraille.nombre(t, puntos).orEmpty())
				else {
					// Lo escrito tal como es en la palabra: en un número, la cifra (no la letra de la misma celda)
					val escrito = o.texto.getOrNull(letrasEscritas(o) - 1)?.toString() ?: CeldasBraille.nombre(t, puntos).orEmpty()
					lector.voz.decir(Enunciado(escrito, Modo.INTERRUMPIR, Tipo.ESCRITURA, deletrear = true))
				}
				return
			}
			lector.sonidos.tocar(Sonido.ACTIVAR)
			actual++
			posicion = 0
			fallos = 0
			val final = actual >= objetivos.size
			// «¡Bien!», lo escrito (un signo, por su nombre) y, al acabar, que la lección ha terminado
			val voz = lector.voz
			voz.decir(Enunciado(t.getString(R.string.tb_bien), Modo.INTERRUMPIR, Tipo.NAVEGACION))
			voz.decir(Enunciado(o.texto, Modo.ENCOLAR, Tipo.NAVEGACION, deletrear = o.texto.length == 1))
			if (final) {
				voz.decir(Enunciado(t.getString(R.string.tb_leccion_terminada), Modo.ENCOLAR, Tipo.NAVEGACION))
				terminar(null)
				return
			}
			pedirLuego()
			return
		}
		// Mal: qué se ha escrito, y otra vez lo que toca (con los puntos)
		fallos++
		lector.sonidos.tocar(Sonido.ERROR)
		val voz = lector.voz
		val nombre = CeldasBraille.nombre(t, puntos)
		if (nombre == null) voz.decir(Enunciado(t.getString(R.string.tb_no_es_letra, CeldasBraille.describir(t, puntos)), Modo.INTERRUMPIR, Tipo.NAVEGACION))
		else {
			voz.decir(Enunciado(t.getString(R.string.tb_has_escrito), Modo.INTERRUMPIR, Tipo.NAVEGACION))
			voz.decir(Enunciado(nombre, Modo.ENCOLAR, Tipo.NAVEGACION, deletrear = nombre.length == 1))
			voz.decir(Enunciado(CeldasBraille.describir(t, puntos) + ".", Modo.ENCOLAR, Tipo.NAVEGACION))
		}
		if (o.texto.length == 1 && o.celdas.size == 1) {
			voz.decir(Enunciado(t.getString(R.string.tb_prueba_otra), Modo.ENCOLAR, Tipo.NAVEGACION))
			voz.decir(Enunciado(CeldasBraille.describir(t, esperada), Modo.ENCOLAR, Tipo.NAVEGACION))
		} else pistaCelda(o)
	}

	/** Lo siguiente que se pide, al poco; si se escribe antes (quien ya va rápido), no se pide dos veces. */
	private val pedirPendiente = Runnable { if (puesta) pedir() }

	private fun pedirLuego() {
		lector.manejador.removeCallbacks(pedirPendiente)
		lector.manejador.postDelayed(pedirPendiente, ESPERA_SIGUIENTE_MS)
	}

	// --- Los gestos -------------------------------------------------------------------------

	/** El primer gesto que hace [a] con los gestos del usuario (los de un punto no cuentan), o null si ninguno. */
	private fun gestoDe(a: AccionTeclado): GestoBraille? =
		gestos.entries.firstOrNull { it.value == a && !it.key.startsWith("p") }?.let { GestoBraille.deClave(it.key) }

	private fun pedirGesto(modo: Modo) {
		val a = acciones.getOrNull(actual) ?: return
		val g = gestoDe(a) ?: return
		lector.decir(t.getString(R.string.tb_haz_gesto, a.nombre(t), GestosTeclado.nombre(t, g)), modo, Tipo.NAVEGACION)
	}

	private fun gestoHecho(g: GestoBraille) {
		lector.manejador.removeCallbacks(pedirPendiente)
		val esperada = acciones.getOrNull(actual) ?: return
		val hecha = GestosTeclado.accion(gestos, g)
		if (hecha == esperada) {
			lector.sonidos.tocar(Sonido.ACTIVAR)
			actual++
			if (actual >= acciones.size) {
				decir(t.getString(R.string.tb_gestos_todos))
				terminar(null)
				return
			}
			decir(t.getString(R.string.tb_gesto_bien, esperada.nombre(t)))
			pedirLuego()
			return
		}
		lector.sonidos.tocar(Sonido.ERROR)
		val dicho = if (hecha == AccionTeclado.NINGUNA) t.getString(R.string.tb_gesto_nada, GestosTeclado.nombre(t, g))
		else t.getString(R.string.tb_gesto_otro, GestosTeclado.nombre(t, g), hecha.nombre(t))
		decir(dicho + " " + t.getString(R.string.tb_gesto_repite, GestosTeclado.nombre(t, gestoDe(esperada) ?: g)))
	}

	// --- Práctica libre -----------------------------------------------------------------------

	/** «Espacio: un dedo hacia la derecha. Borrar: ...», con los gestos que tenga puestos el usuario. */
	private fun ayudaLibre(): String = listOf(AccionTeclado.ESPACIO, AccionTeclado.BORRAR, AccionTeclado.INTRO).mapNotNull { a ->
		gestoDe(a)?.let { t.getString(R.string.tb_libre_gesto, if (a == AccionTeclado.INTRO) t.getString(R.string.tb_libre_leer) else a.nombre(t), GestosTeclado.nombre(t, it)) }
	}.joinToString(" ")

	private fun escribirLibre(puntos: Int) {
		when (puntos) {
			TecladoBraille.SIGNO_DE_MAYUSCULA -> {
				mayuscula = true
				decir(t.getString(R.string.braille_mayuscula))
				return
			}
			TecladoBraille.SIGNO_DE_NUMERO -> {
				numero = true
				decir(t.getString(R.string.braille_numero))
				return
			}
		}
		val c = ((if (numero) TecladoBraille.numero(puntos) else null) ?: TecladoBraille.letra(puntos).also { numero = false })
			?.let { TecladoBraille.signoQueAbre(it, escrito.toString()) }
		if (c == null) {
			lector.sonidos.tocar(Sonido.ERROR)
			decir(t.getString(R.string.tb_no_es_letra, CeldasBraille.describir(t, puntos)))
			return
		}
		val letra = if (mayuscula) c.uppercase() else c
		mayuscula = false
		escrito.append(letra)
		lector.voz.decir(Enunciado(letra, Modo.INTERRUMPIR, Tipo.ESCRITURA, deletrear = true))
	}

	private fun gestoLibre(g: GestoBraille) {
		when (GestosTeclado.accion(gestos, g)) {
			AccionTeclado.ESPACIO -> {
				numero = false
				val palabra = escrito.substring(escrito.lastIndexOf(' ') + 1)
				escrito.append(' ')
				decir(palabra.ifEmpty { t.getString(R.string.voz_espacio) })
			}
			AccionTeclado.BORRAR -> {
				if (escrito.isEmpty()) {
					decir(t.getString(R.string.braille_nada_que_borrar))
					return
				}
				val quitado = escrito.last().toString()
				escrito.setLength(escrito.length - 1)
				decir(t.getString(R.string.edicion_borrado, if (quitado == " ") t.getString(R.string.voz_espacio) else quitado))
			}
			AccionTeclado.BORRAR_PALABRA -> {
				val sin = escrito.toString().trimEnd()
				val inicio = sin.lastIndexOf(' ') + 1
				val borrada = sin.substring(inicio)
				escrito.setLength(inicio)
				decir(if (borrada.isEmpty()) t.getString(R.string.braille_nada_que_borrar) else t.getString(R.string.edicion_borrado, borrada))
			}
			AccionTeclado.INTRO, AccionTeclado.LEER_CAMPO ->
				decir(if (escrito.isBlank()) t.getString(R.string.tb_libre_vacio) else t.getString(R.string.tb_libre_todo, escrito.toString()))
			AccionTeclado.NINGUNA -> decir(t.getString(R.string.tb_gesto_nada, GestosTeclado.nombre(t, g)))
			else -> decir(t.getString(R.string.tb_libre_otro, GestosTeclado.accion(gestos, g).nombre(t)))
		}
	}

	// --- Buscar emojis ------------------------------------------------------------------------

	private val consulta = StringBuilder()
	private var encontrados: List<BuscadorEmojis.Emoji> = emptyList()
	private var elegido = 0
	private var favoritoProbado = false

	/** La base de emojis del idioma de Tiresias, leída al empezar a buscar. */
	private val buscador: BuscadorEmojis by lazy {
		fun leer(codigo: String) = try {
			lector.servicio.assets.open("idiomas/$codigo/emojis.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }
		} catch (e: java.io.IOException) {
			com.xebolax.tiresias.base.Registro.w("PracticaBraille", "Sin base de emojis para $codigo", e)
			null
		}
		BuscadorEmojis(BuscadorEmojis.leerTabla(leer(com.xebolax.tiresias.base.Idioma.efectivo(lector.servicio).language) ?: leer("es").orEmpty()))
	}

	private fun letraEmoji(puntos: Int) {
		val c = TecladoBraille.letra(puntos)?.takeIf { it[0].isLetter() }
		if (c == null) {
			decir(t.getString(R.string.tb_no_es_letra, CeldasBraille.describir(t, puntos)))
			return
		}
		val prueba = consulta.toString() + c
		val hay = buscador.buscar(prueba)
		if (hay.isEmpty()) {
			lector.sonidos.tocar(Sonido.ERROR)
			decir(t.getString(R.string.braille_busqueda_sin, prueba))
			return
		}
		consulta.append(c)
		encontrados = hay
		elegido = 0
		lector.voz.decir(Enunciado(c, Modo.INTERRUMPIR, Tipo.ESCRITURA, deletrear = true))
		lector.voz.decir(Enunciado(t.resources.getQuantityString(R.plurals.braille_busqueda_hay, hay.size, hay.size, hay[0].nombre), Modo.ENCOLAR, Tipo.NAVEGACION))
	}

	private fun gestoEmoji(g: GestoBraille) {
		val actual = encontrados.getOrNull(elegido)
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.DERECHA, 1 to DireccionBraille.IZQUIERDA -> {
				if (encontrados.isEmpty()) {
					decir(t.getString(R.string.braille_busqueda_escribe))
					return
				}
				elegido = (elegido + (if (g.direccion == DireccionBraille.DERECHA) 1 else -1) + encontrados.size) % encontrados.size
				decir(t.getString(R.string.braille_lista_elemento, encontrados[elegido].nombre, elegido + 1, encontrados.size))
			}
			1 to DireccionBraille.ABAJO -> {
				if (consulta.isNotEmpty() && consulta.last() != ' ') consulta.append(' ')
				decir(t.getString(R.string.voz_espacio))
			}
			1 to DireccionBraille.ARRIBA -> decir(if (consulta.isBlank()) t.getString(R.string.braille_busqueda_escribe)
				else t.resources.getQuantityString(R.plurals.braille_busqueda_estado, encontrados.size, consulta.toString().trim(), encontrados.size))
			2 to DireccionBraille.IZQUIERDA -> {
				if (consulta.isEmpty()) {
					decir(t.getString(R.string.braille_nada_que_borrar))
					return
				}
				val quitado = consulta.last()
				consulta.setLength(consulta.length - 1)
				encontrados = buscador.buscar(consulta.toString())
				elegido = 0
				decir(t.getString(R.string.edicion_borrado, if (quitado == ' ') t.getString(R.string.voz_espacio) else quitado.toString()))
			}
			// Favorito de verdad: la práctica también sirve para preparar los favoritos del teclado
			2 to DireccionBraille.DERECHA -> {
				if (actual == null) {
					decir(t.getString(R.string.braille_busqueda_escribe))
					return
				}
				val a = lector.ajustes
				val ahora = a.emojisFavoritos.toMutableList()
				val quitar = actual.emoji in ahora
				if (quitar) ahora.remove(actual.emoji) else ahora.add(0, actual.emoji)
				a.emojisFavoritos = ahora.take(60)
				favoritoProbado = true
				decir(t.getString(if (quitar) R.string.braille_favorito_quitado else R.string.braille_favorito_puesto))
			}
			2 to DireccionBraille.ABAJO -> {
				if (actual == null) {
					decir(t.getString(R.string.braille_busqueda_escribe))
					return
				}
				lector.sonidos.tocar(Sonido.ACTIVAR)
				decir(t.getString(R.string.tb_emoji_elegido, actual.nombre) + if (favoritoProbado) "" else " " + t.getString(R.string.tb_emoji_favorito_pendiente))
				terminar(null)
			}
			else -> decir(t.getString(R.string.braille_busqueda_ayuda))
		}
	}

	// --- Terminar ---------------------------------------------------------------------------

	/** Todo hecho: se dice [frase] (si hay) y la práctica se quita sola al poco, para oírlo entero. */
	private fun terminar(frase: String?) {
		terminada = true
		frase?.let { decir(it) }
		lector.manejador.postDelayed({ if (puesta) cerrar() }, ESPERA_FINAL_MS)
	}

	private companion object {
		/** Lo que se espera a lo primero: la vista se crea justo antes de añadirla y de poner su aviso a hablar. */
		const val ESPERA_PRIMERA_MS = 400L
		const val ESPERA_SIGUIENTE_MS = 900L
		const val ESPERA_FINAL_MS = 3500L
	}
}
