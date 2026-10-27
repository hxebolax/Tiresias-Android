package com.xebolax.tiresias.nucleo

/**
 * Lo que propone la IA cuando se le pide hacer algo en la pantalla: una sola
 * acción, que Tiresias explica y solo hace si el usuario la confirma.
 *
 * La IA responde con un objeto JSON (a veces rodeado de texto o de ```):
 * `{"accion":"tocar","elemento":3,"explicacion":"Pulsar Enviar"}`. Las
 * coordenadas `x` e `y` van de 0 a 1000 sobre la captura, para los controles
 * que no están en la lista de elementos (juegos, apps sin accesibilidad).
 */
data class PropuestaIA(
	val accion: Tipo,
	val elemento: Int? = null,
	val x: Int? = null,
	val y: Int? = null,
	val texto: String? = null,
	val direccion: Direccion? = null,
	val explicacion: String = "",
) {
	enum class Tipo { TOCAR, MANTENER, ESCRIBIR, DESPLAZAR, NINGUNA }
	enum class Direccion { ARRIBA, ABAJO, IZQUIERDA, DERECHA }

	/** Si se puede hacer: cada acción necesita su dato (el elemento o el punto, el texto, la dirección). */
	val valida: Boolean
		get() = when (accion) {
			Tipo.TOCAR, Tipo.MANTENER -> elemento != null || (x in 0..1000 && y in 0..1000)
			Tipo.ESCRIBIR -> elemento != null && !texto.isNullOrEmpty()
			Tipo.DESPLAZAR -> direccion != null
			Tipo.NINGUNA -> true
		}

	companion object {
		private val CAMPO = Regex("\"([a-zA-Z_]+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d+(?:\\.\\d+)?|null|true|false)")

		/** La propuesta de la respuesta de la IA, o null si no trae un JSON que sirva. */
		fun leer(respuesta: String): PropuestaIA? {
			val json = objeto(respuesta) ?: return null
			val campos = HashMap<String, String?>()
			for (m in CAMPO.findAll(json)) {
				val v = m.groupValues[2]
				campos[m.groupValues[1].lowercase()] = when {
					v == "null" -> null
					v.startsWith("\"") -> cadena(v.substring(1, v.length - 1))
					else -> v
				}
			}
			val accion = when (campos["accion"]?.lowercase()?.trim()) {
				"tocar", "pulsar", "tap", "click" -> Tipo.TOCAR
				"mantener", "long_press", "pulsacion_larga" -> Tipo.MANTENER
				"escribir", "type", "write" -> Tipo.ESCRIBIR
				"desplazar", "scroll" -> Tipo.DESPLAZAR
				"ninguna", "nada", "none", "responder" -> Tipo.NINGUNA
				else -> return null
			}
			val direccion = when (campos["direccion"]?.lowercase()?.trim()) {
				"arriba", "up" -> Direccion.ARRIBA
				"abajo", "down" -> Direccion.ABAJO
				"izquierda", "left" -> Direccion.IZQUIERDA
				"derecha", "right" -> Direccion.DERECHA
				else -> null
			}
			fun numero(k: String) = campos[k]?.toDoubleOrNull()?.toInt()
			val explicacion = (campos["explicacion"] ?: campos["respuesta"] ?: campos["explanation"]).orEmpty().trim()
			return PropuestaIA(accion, numero("elemento"), numero("x"), numero("y"), campos["texto"], direccion, explicacion.take(400))
		}

		/** El primer objeto `{...}` completo de [s] (respetando las cadenas). */
		private fun objeto(s: String): String? {
			val inicio = s.indexOf('{').takeIf { it >= 0 } ?: return null
			var nivel = 0
			var enCadena = false
			var i = inicio
			while (i < s.length) {
				val c = s[i]
				when {
					enCadena && c == '\\' -> i++
					c == '"' -> enCadena = !enCadena
					!enCadena && c == '{' -> nivel++
					!enCadena && c == '}' -> if (--nivel == 0) return s.substring(inicio, i + 1)
				}
				i++
			}
			return null
		}

		private fun cadena(s: String): String {
			val r = StringBuilder()
			var i = 0
			while (i < s.length) {
				val c = s[i]
				if (c == '\\' && i + 1 < s.length) {
					when (val e = s[i + 1]) {
						'n' -> r.append('\n')
						't' -> r.append('\t')
						'u' -> if (i + 5 < s.length) {
							s.substring(i + 2, i + 6).toIntOrNull(16)?.let { r.append(it.toChar()) }
							i += 4
						}
						else -> r.append(e)
					}
					i += 2
					continue
				}
				r.append(c)
				i++
			}
			return r.toString()
		}
	}
}
