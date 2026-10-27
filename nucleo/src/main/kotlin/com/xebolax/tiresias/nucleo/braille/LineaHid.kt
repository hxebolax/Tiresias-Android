package com.xebolax.tiresias.nucleo.braille

/**
 * Las líneas braille HID (la norma «HID Braille» de 2018, la de NVDA, JAWS y
 * BrailleDisplayController de Android 15): el descriptor de informes dice
 * cuántas celdas tiene la línea y qué teclas, y en qué bytes van.
 *
 * Usos de la página Braille (0x41) de las «HID Usage Tables».
 */
object UsosBraille {
	const val PAGINA = 0x41
	private fun u(id: Int) = (PAGINA shl 16) or id

	val CELDA_8 = u(0x03)
	val CELDA_6 = u(0x04)
	val GRUPO_ENCAMINAMIENTO_1 = u(0xFA)
	val ENCAMINAMIENTO = u(0x100)
	val PUNTO_1 = u(0x201) // ... PUNTO_8 = u(0x208)
	val ESPACIO = u(0x209)
	val ESPACIO_IZQUIERDO = u(0x20A)
	val ESPACIO_DERECHO = u(0x20B)
	val JOYSTICK_CENTRO = u(0x210)
	val JOYSTICK_ARRIBA = u(0x211)
	val JOYSTICK_ABAJO = u(0x212)
	val JOYSTICK_IZQUIERDA = u(0x213)
	val JOYSTICK_DERECHA = u(0x214)
	val CRUCETA_CENTRO = u(0x215)
	val CRUCETA_ARRIBA = u(0x216)
	val CRUCETA_ABAJO = u(0x217)
	val CRUCETA_IZQUIERDA = u(0x218)
	val CRUCETA_DERECHA = u(0x219)
	val DESPLAZAR_IZQUIERDA = u(0x21A)
	val DESPLAZAR_DERECHA = u(0x21B)
	val BASCULA_ARRIBA = u(0x21C)
	val BASCULA_ABAJO = u(0x21D)
	val BASCULA_PULSAR = u(0x21E)

	fun punto(uso: Int): Int = if (uso in PUNTO_1..u(0x208)) uso - PUNTO_1 + 1 else 0
}

/** Un campo de un informe HID: dónde está (bits), cuántos elementos tiene y qué usos. */
class CampoHid(
	val salida: Boolean,
	val idInforme: Int,
	val bit: Int,
	val tamano: Int,
	val cuenta: Int,
	val usos: List<Int>,
	val variable: Boolean,
	val minimoLogico: Int,
	val coleccion: Int,
) {
	/** El uso del elemento [i] (si hay menos usos que elementos, el último se repite). */
	fun uso(i: Int): Int = if (usos.isEmpty()) 0 else usos[minOf(i, usos.size - 1)]
}

/** Lo que dice un descriptor de informes HID, con lo que hace falta para una línea braille. */
class DescriptorHid(val campos: List<CampoHid>) {
	val conIds: Boolean get() = campos.any { it.idInforme != 0 }

	/** El tamaño en bytes de un informe (sin el byte del identificador). */
	fun bytes(salida: Boolean, id: Int): Int =
		(campos.filter { it.salida == salida && it.idInforme == id }.maxOfOrNull { it.bit + it.tamano * it.cuenta } ?: 0).let { (it + 7) / 8 }

	companion object {
		/** Lee un descriptor de informes (los elementos cortos de la norma HID 1.11; los largos se saltan). */
		fun leer(d: ByteArray): DescriptorHid {
			val campos = ArrayList<CampoHid>()
			// Globales (con su pila para Push y Pop)
			class Globales(var pagina: Int = 0, var minimo: Int = 0, var tamano: Int = 0, var cuenta: Int = 0, var id: Int = 0)
			var g = Globales()
			val pila = ArrayDeque<Globales>()
			// Locales: se vacían tras cada elemento principal
			val usos = ArrayList<Int>()
			var usoMinimo = -1
			var usoMaximo = -1
			val colecciones = ArrayDeque<Int>()
			val bits = HashMap<Pair<Boolean, Int>, Int>()
			fun completo(uso: Int, tam: Int) = if (tam == 4) uso else (g.pagina shl 16) or uso
			var i = 0
			while (i < d.size) {
				val prefijo = d[i].toInt() and 0xFF
				if (prefijo == 0xFE) {
					// Elemento largo: se salta
					val largo = d.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: break
					i += 3 + largo
					continue
				}
				val tam = when (prefijo and 0x03) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 4 }
				if (tam > 0 && i + tam >= d.size) break
				var sinSigno = 0L
				for (k in 0 until tam) sinSigno = sinSigno or ((d[i + 1 + k].toLong() and 0xFF) shl (8 * k))
				val conSigno = when (tam) {
					1 -> sinSigno.toByte().toInt()
					2 -> sinSigno.toShort().toInt()
					else -> sinSigno.toInt()
				}
				val valor = sinSigno.toInt()
				val etiqueta = prefijo and 0xFC
				when (etiqueta) {
					// Principales
					0x80, 0x90 -> {
						val salida = etiqueta == 0x90
						val lista = when {
							usoMinimo >= 0 && usoMaximo >= usoMinimo -> (usoMinimo..usoMaximo).toList()
							else -> usos.toList()
						}
						val clave = salida to g.id
						val inicio = bits[clave] ?: 0
						// Constante (relleno): ocupa sitio pero no es un campo
						if (valor and 0x01 == 0) {
							campos += CampoHid(salida, g.id, inicio, g.tamano, g.cuenta, lista, valor and 0x02 != 0, g.minimo, colecciones.lastOrNull() ?: 0)
						}
						bits[clave] = inicio + g.tamano * g.cuenta
						usos.clear(); usoMinimo = -1; usoMaximo = -1
					}
					0xB0 -> { usos.clear(); usoMinimo = -1; usoMaximo = -1 }
					0xA0 -> {
						colecciones.addLast(usos.firstOrNull() ?: (if (usoMinimo >= 0) usoMinimo else 0))
						usos.clear(); usoMinimo = -1; usoMaximo = -1
					}
					0xC0 -> { colecciones.removeLastOrNull() }
					// Globales
					0x04 -> g.pagina = valor
					0x14 -> g.minimo = conSigno
					0x74 -> g.tamano = valor
					0x84 -> g.id = valor
					0x94 -> g.cuenta = valor
					0xA4 -> pila.addLast(Globales(g.pagina, g.minimo, g.tamano, g.cuenta, g.id))
					0xB4 -> pila.removeLastOrNull()?.let { g = it }
					// Locales
					0x08 -> usos += completo(valor, tam)
					0x18 -> usoMinimo = completo(valor, tam)
					0x28 -> usoMaximo = completo(valor, tam)
				}
				i += 1 + tam
			}
			return DescriptorHid(campos)
		}
	}
}

/** Una tecla pulsada en la línea: su uso y, para las de encaminamiento, la celda. */
data class TeclaLinea(val uso: Int, val celda: Int = 0)

/**
 * Una línea braille HID concreta: escribe las celdas y lee las teclas. Si el
 * descriptor no tiene celdas braille, no es una línea ([crear] devuelve null).
 */
class LineaHid private constructor(private val d: DescriptorHid, private val celdasCampo: CampoHid) {
	val celdas: Int get() = celdasCampo.cuenta
	val ochoPuntos: Boolean get() = celdasCampo.uso(0) == UsosBraille.CELDA_8

	/** El informe de salida con [celdas] (de más se cortan, de menos se rellenan con celdas vacías). */
	fun informe(contenido: ByteArray): ByteArray {
		val id = celdasCampo.idInforme
		val conId = if (d.conIds) 1 else 0
		val informe = ByteArray(conId + d.bytes(true, id))
		if (conId == 1) informe[0] = id.toByte()
		val primero = conId + celdasCampo.bit / 8
		for (k in 0 until celdas) {
			var c = contenido.getOrElse(k) { 0 }.toInt() and 0xFF
			if (!ochoPuntos) c = c and 0x3F
			if (primero + k < informe.size) informe[primero + k] = c.toByte()
		}
		return informe
	}

	/** Las teclas que están pulsadas según este informe de entrada. */
	fun teclas(informe: ByteArray): Set<TeclaLinea> {
		if (informe.isEmpty()) return emptySet()
		val id = if (d.conIds) informe[0].toInt() and 0xFF else 0
		val desde = if (d.conIds) 1 else 0
		val pulsadas = HashSet<TeclaLinea>()
		for (c in d.campos) {
			if (c.salida || c.idInforme != id) continue
			for (k in 0 until c.cuenta) {
				val v = leerBits(informe, desde * 8 + c.bit + k * c.tamano, c.tamano)
				if (c.variable) {
					if (v == 0) continue
					val uso = c.uso(k)
					pulsadas += if (uso == UsosBraille.ENCAMINAMIENTO) TeclaLinea(uso, k) else TeclaLinea(uso)
				} else {
					// Matriz: el valor dice qué uso (0 = ninguna tecla); en las de encaminamiento, qué celda
					if (v == 0) continue
					val indice = v - c.minimoLogico
					if (c.usos.size == 1 && c.usos[0] == UsosBraille.ENCAMINAMIENTO) {
						pulsadas += TeclaLinea(UsosBraille.ENCAMINAMIENTO, indice)
					} else if (indice in c.usos.indices) {
						pulsadas += TeclaLinea(c.usos[indice])
					}
				}
			}
		}
		return pulsadas
	}

	private fun leerBits(b: ByteArray, bit: Int, n: Int): Int {
		var v = 0
		for (k in 0 until minOf(n, 31)) {
			val pos = bit + k
			val byte = b.getOrNull(pos / 8)?.toInt() ?: return v
			if (byte and (1 shl (pos % 8)) != 0) v = v or (1 shl k)
		}
		return v
	}

	companion object {
		fun crear(descriptor: ByteArray): LineaHid? {
			val d = DescriptorHid.leer(descriptor)
			val campo = d.campos.firstOrNull { it.salida && it.cuenta > 0 && (it.uso(0) == UsosBraille.CELDA_8 || it.uso(0) == UsosBraille.CELDA_6) } ?: return null
			return LineaHid(d, campo)
		}
	}
}
