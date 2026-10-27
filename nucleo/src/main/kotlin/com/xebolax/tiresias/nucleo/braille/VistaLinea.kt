package com.xebolax.tiresias.nucleo.braille

import com.xebolax.tiresias.nucleo.Accion

/**
 * Lo que se ve en una línea de [ancho] celdas: un trozo del texto traducido.
 * Al desplazar no corta palabras si puede (como NVDA) y el cursor de un campo
 * de texto se marca con los puntos 7 y 8.
 */
class VistaLinea(val ancho: Int) {
	var traduccion: Traduccion = Traduccion(ByteArray(0), IntArray(1), IntArray(0))
		private set

	/** La posición del cursor en el texto (-1 sin cursor). */
	var cursor: Int = -1
		private set

	/** Los puntos con los que se marca el cursor (los 7 y 8 de fábrica; 0: no se marca). */
	var puntosCursor: Int = Puntos.CURSOR

	private var inicio = 0
	private val anteriores = ArrayDeque<Int>()

	/** Pone un texto nuevo; con cursor, la ventana se coloca donde está el cursor. */
	fun poner(t: Traduccion, cursorTexto: Int = -1) {
		traduccion = t
		cursor = cursorTexto
		anteriores.clear()
		inicio = 0
		if (cursorTexto >= 0) {
			val celda = t.celdaDe[cursorTexto.coerceIn(0, t.celdaDe.size - 1)]
			while (fin(inicio) <= celda && fin(inicio) < t.tamano) {
				anteriores.addLast(inicio)
				inicio = siguienteInicio(fin(inicio))
			}
		}
	}

	/** El cursor se ha movido en el mismo texto: la ventana lo sigue si se sale. */
	fun moverCursor(cursorTexto: Int) {
		val t = traduccion
		cursor = cursorTexto
		val celda = t.celdaDe[cursorTexto.coerceIn(0, t.celdaDe.size - 1)]
		if (celda < inicio || celda >= fin(inicio)) poner(t, cursorTexto)
	}

	/** Las celdas que se ven ahora, del tamaño de la línea. */
	fun celdas(): ByteArray {
		val t = traduccion
		val r = ByteArray(ancho)
		val hasta = fin(inicio)
		for (k in inicio until hasta) r[k - inicio] = t.celdas[k]
		if (cursor >= 0) {
			val celda = t.celdaDe[cursor.coerceIn(0, t.celdaDe.size - 1)]
			if (celda in inicio until inicio + ancho) r[celda - inicio] = (r[celda - inicio].toInt() or puntosCursor).toByte()
		}
		return r
	}

	/** Pasa al trozo siguiente; false si ya se ve el final. */
	fun avanzar(): Boolean {
		val f = fin(inicio)
		if (f >= traduccion.tamano) return false
		anteriores.addLast(inicio)
		inicio = siguienteInicio(f)
		return true
	}

	/** Vuelve al trozo anterior; false si ya se ve el principio. */
	fun retroceder(): Boolean {
		inicio = anteriores.removeLastOrNull() ?: return false
		return true
	}

	/** El carácter del texto que hay bajo la tecla de encaminamiento [celda] (-1 si no hay ninguno). */
	fun caracterEn(celda: Int): Int {
		val k = inicio + celda
		val t = traduccion
		return when {
			k < fin(inicio) && k < t.caracterDe.size -> t.caracterDe[k]
			// Tras el final del texto: el cursor va al final (para seguir escribiendo)
			k == t.tamano -> t.celdaDe.size - 1
			else -> -1
		}
	}

	private fun vacia(k: Int) = traduccion.celdas.getOrNull(k)?.toInt() == 0

	/** Dónde acaba la ventana que empieza en [desde]: sin partir una palabra si en la ventana hay un espacio. */
	private fun fin(desde: Int): Int {
		val n = traduccion.tamano
		val f = minOf(desde + ancho, n)
		if (f >= n || vacia(f) || vacia(f - 1)) return f
		for (k in f - 1 downTo desde + 1) if (vacia(k)) return k + 1
		return f
	}

	private fun siguienteInicio(f: Int): Int {
		var k = f
		while (k < traduccion.tamano && vacia(k)) k++
		return if (k >= traduccion.tamano) f else k
	}
}

/** Lo que pide el usuario con las teclas de la línea. */
sealed interface OrdenLinea {
	data class Hacer(val accion: Accion) : OrdenLinea
	/** Desplazar la línea por el texto (al acabar, al elemento siguiente o anterior). */
	data class Desplazar(val adelante: Boolean) : OrdenLinea
	/** Tecla de encaminamiento: en un campo de texto pone el cursor; si no, activa. */
	data class Encaminar(val celda: Int) : OrdenLinea
	/** Escribir una celda braille (el texto lo da la tabla). */
	data class Escribir(val celda: Int) : OrdenLinea
	data object Espacio : OrdenLinea
	data object Borrar : OrdenLinea
	data object Intro : OrdenLinea
	/** Una combinación que no hace nada (se avisa con un sonido). */
	data object Desconocida : OrdenLinea
}

/**
 * Las teclas de la línea: se van juntando mientras hay alguna pulsada y, al
 * soltarlas todas, la combinación entera es una orden (como en BrailleBack y
 * BRLTTY: los puntos pulsados a la vez forman una celda).
 */
class TecladoLinea(private val acordes: () -> Map<Int, Accion> = { ACORDES }) {
	private val juntas = HashSet<TeclaLinea>()

	/** Un informe de teclas nuevo; devuelve la orden si se han soltado todas. */
	fun alTeclas(ahora: Set<TeclaLinea>): OrdenLinea? {
		juntas += ahora
		if (ahora.isNotEmpty() || juntas.isEmpty()) return null
		val combinacion = juntas.toSet()
		juntas.clear()
		return orden(combinacion, acordes())
	}

	companion object {
		/** Espacio y puntos a la vez: las órdenes de Tiresias (en el manual, «Línea braille»). */
		val ACORDES: Map<Int, Accion> = mapOf(
			Puntos.de("1") to Accion.ANTERIOR,
			Puntos.de("4") to Accion.SIGUIENTE,
			Puntos.de("123") to Accion.PRIMERO,
			Puntos.de("456") to Accion.ULTIMO,
			Puntos.de("2") to Accion.CONTROL_ANTERIOR,
			Puntos.de("5") to Accion.CONTROL_SIGUIENTE,
			Puntos.de("3") to Accion.SELECTOR_ANTERIOR,
			Puntos.de("6") to Accion.SELECTOR_SIGUIENTE,
			Puntos.de("12") to Accion.ATRAS, // b de «back»
			Puntos.de("125") to Accion.INICIO, // h
			Puntos.de("1345") to Accion.NOTIFICACIONES, // n
			Puntos.de("1235") to Accion.LEER_DESDE_AQUI, // r
			Puntos.de("2345") to Accion.LEER_DESDE_ARRIBA, // t
			Puntos.de("134") to Accion.MENU_TIRESIAS, // m
			Puntos.de("1245") to Accion.MENU_ACCIONES, // g
			Puntos.de("234") to Accion.PAUSAR_VOZ, // s de silencio
			Puntos.de("1236") to Accion.ALTERNAR_VOZ, // v
			Puntos.de("24") to Accion.DONDE_ESTOY, // i
			Puntos.de("124") to Accion.BUSCAR, // f de «find»
			Puntos.de("14") to Accion.COPIAR, // c
			Puntos.de("1234") to Accion.PEGAR, // p
			Puntos.de("1346") to Accion.CORTAR, // x
			Puntos.de("1356") to Accion.DESHACER, // z
			Puntos.de("12345") to Accion.ESTADO_SISTEMA, // q
			Puntos.de("1456") to Accion.RECIENTES,
			Puntos.de("145") to Accion.DELETREAR, // d
			Puntos.de("13") to Accion.LISTA_ELEMENTOS, // k
			Puntos.de("123456") to Accion.MANTENER, // pulsación larga
			Puntos.de("78") to Accion.PRIMERO_BRAILLE, // la voz se calla: todo en la línea
		)

		private val ESPACIOS = setOf(UsosBraille.ESPACIO, UsosBraille.ESPACIO_IZQUIERDO, UsosBraille.ESPACIO_DERECHO)

		private val TECLAS_SOLAS: Map<Int, OrdenLinea> = mapOf(
			UsosBraille.DESPLAZAR_IZQUIERDA to OrdenLinea.Desplazar(false),
			UsosBraille.DESPLAZAR_DERECHA to OrdenLinea.Desplazar(true),
			UsosBraille.JOYSTICK_IZQUIERDA to OrdenLinea.Desplazar(false),
			UsosBraille.JOYSTICK_DERECHA to OrdenLinea.Desplazar(true),
			UsosBraille.CRUCETA_IZQUIERDA to OrdenLinea.Desplazar(false),
			UsosBraille.CRUCETA_DERECHA to OrdenLinea.Desplazar(true),
			UsosBraille.JOYSTICK_ARRIBA to OrdenLinea.Hacer(Accion.ANTERIOR),
			UsosBraille.JOYSTICK_ABAJO to OrdenLinea.Hacer(Accion.SIGUIENTE),
			UsosBraille.CRUCETA_ARRIBA to OrdenLinea.Hacer(Accion.ANTERIOR),
			UsosBraille.CRUCETA_ABAJO to OrdenLinea.Hacer(Accion.SIGUIENTE),
			UsosBraille.BASCULA_ARRIBA to OrdenLinea.Hacer(Accion.ANTERIOR),
			UsosBraille.BASCULA_ABAJO to OrdenLinea.Hacer(Accion.SIGUIENTE),
			UsosBraille.JOYSTICK_CENTRO to OrdenLinea.Hacer(Accion.ACTIVAR),
			UsosBraille.CRUCETA_CENTRO to OrdenLinea.Hacer(Accion.ACTIVAR),
			UsosBraille.BASCULA_PULSAR to OrdenLinea.Hacer(Accion.ACTIVAR),
		)

		/** Las combinaciones del usuario: «12=ATRAS;13=NINGUNA» sobre [ACORDES] (solo lo que cambia). */
		fun leerAcordes(texto: String, base: Map<Int, Accion> = ACORDES): Map<Int, Accion> {
			val mapa = LinkedHashMap(base)
			for (parte in texto.split(';')) {
				val (puntos, accion) = parte.split('=').takeIf { it.size == 2 } ?: continue
				val celda = runCatching { Puntos.de(puntos.trim()) }.getOrNull()?.takeIf { it != 0 && puntos.trim().all { c -> c in '1'..'8' } } ?: continue
				val a = Accion.entries.firstOrNull { it.name == accion.trim() } ?: continue
				if (a == Accion.NINGUNA) mapa.remove(celda) else mapa[celda] = a
			}
			return mapa
		}

		/** Lo que cambia [mapa] respecto a [base], para guardarlo. */
		fun escribirAcordes(mapa: Map<Int, Accion>, base: Map<Int, Accion> = ACORDES): String {
			val partes = ArrayList<String>()
			for ((c, a) in mapa) if (base[c] != a) partes += "${Puntos.nombre(c)}=${a.name}"
			for (c in base.keys) if (c !in mapa) partes += "${Puntos.nombre(c)}=${Accion.NINGUNA.name}"
			return partes.joinToString(";")
		}

		fun orden(teclas: Set<TeclaLinea>, acordes: Map<Int, Accion> = ACORDES): OrdenLinea {
			val usos = teclas.map { it.uso }.toSet()
			// Una tecla de encaminamiento sola
			teclas.singleOrNull()?.takeIf { it.uso == UsosBraille.ENCAMINAMIENTO }?.let { return OrdenLinea.Encaminar(it.celda) }
			if (usos.size == 1) TECLAS_SOLAS[usos.first()]?.let { return it }
			val espacio = usos.any { it in ESPACIOS }
			val otras = usos.filter { it !in ESPACIOS }
			if (otras.any { UsosBraille.punto(it) == 0 }) return OrdenLinea.Desconocida
			val celda = otras.fold(0) { acc, u -> acc or (1 shl (UsosBraille.punto(u) - 1)) }
			return when {
				espacio && celda == 0 -> OrdenLinea.Espacio
				espacio && celda == Puntos.P7 -> OrdenLinea.Borrar
				espacio && celda == Puntos.P8 -> OrdenLinea.Intro
				espacio -> acordes[celda]?.let { OrdenLinea.Hacer(it) } ?: OrdenLinea.Desconocida
				// Los puntos 7 y 8 solos: borrar e intro (como en NVDA y BRLTTY)
				celda == Puntos.P7 -> OrdenLinea.Borrar
				celda == Puntos.P8 -> OrdenLinea.Intro
				celda != 0 -> OrdenLinea.Escribir(celda)
				else -> OrdenLinea.Desconocida
			}
		}
	}
}
