package com.xebolax.tiresias.nucleo.remoto

/** Un elemento de la pantalla del ayudado, tal como lo ve el ayudante en su lista. */
data class ElementoRemoto(val indice: Int, val texto: String)

/**
 * Los mensajes de la asistencia remota entre dos Tiresias. Cada uno viaja como un objeto
 * JSON en una trama de [Enlace], con el tipo en el campo `"t"` y los demás campos con los
 * mismos nombres que las propiedades (`{"t":"voz","texto":"Hola","interrumpir":true,…}`).
 *
 * Los campos que pueden ser null se omiten cuando lo son. Al leer, un tipo desconocido, un
 * campo obligatorio que falta o un campo del tipo equivocado dan null: el que recibe lo
 * ignora (así un Tiresias más nuevo puede mandar mensajes que uno viejo no conoce).
 */
sealed class MensajeAyuda {
	/** El mensaje en JSON compacto. */
	abstract fun json(): String

	/** Ayuda para escribir: el tipo primero y luego los campos, quitando los null. */
	protected fun json(tipo: String, vararg campos: Pair<String, Any?>): String {
		val mapa = LinkedHashMap<String, Any?>()
		mapa["t"] = tipo
		for ((clave, valor) in campos) if (valor != null) mapa[clave] = valor
		return Json.escribir(mapa)
	}

	// ── Los dos sentidos ──

	/**
	 * Lo primero que manda cada lado. [ayudante] dice si es el que ayuda; [pruebas] son las
	 * de sus parejas ([Emparejar.prueba]), para que el ayudado sepa si lo conoce.
	 */
	data class Hola(val nombre: String, val version: String, val idioma: String, val ayudante: Boolean, val pruebas: List<String> = emptyList()) : MensajeAyuda() {
		override fun json() = json("hola", "nombre" to nombre, "version" to version, "idioma" to idioma, "ayudante" to ayudante, "pruebas" to pruebas.ifEmpty { null })
	}

	/** Se acaba la asistencia; [motivo] es una clave corta («usuario», «tiempo»…). */
	data class Fin(val motivo: String) : MensajeAyuda() {
		override fun json() = json("fin", "motivo" to motivo)
	}

	/** Para mantener viva la conexión. */
	object Ping : MensajeAyuda() {
		override fun json() = json("ping")
		override fun toString() = "Ping"
	}

	// ── Del ayudado al ayudante ──

	/** Lo que dice el Tiresias del ayudado. [tipo] es el tipo de anuncio y [idioma] el de la voz si cambia. */
	data class Voz(val texto: String, val interrumpir: Boolean, val deletrear: Boolean, val tipo: String, val idioma: String?) : MensajeAyuda() {
		override fun json() = json("voz", "texto" to texto, "interrumpir" to interrumpir, "deletrear" to deletrear, "tipo" to tipo, "idioma" to idioma)
	}

	/** El ayudado ha mandado callar a la voz. */
	object Callar : MensajeAyuda() {
		override fun json() = json("callar")
		override fun toString() = "Callar"
	}

	/** Un sonido de Tiresias, por su nombre. */
	data class Sonido(val nombre: String) : MensajeAyuda() {
		override fun json() = json("sonido", "nombre" to nombre)
	}

	/** Un dato del estado del móvil (batería, wifi…); [texto] null es que ya no hay. */
	data class Estado(val clave: String, val texto: String?) : MensajeAyuda() {
		override fun json() = json("estado", "clave" to clave, "texto" to texto)
	}

	/** Respuesta a [PedirElementos]. */
	data class Elementos(val peticion: Long, val lista: List<ElementoRemoto>) : MensajeAyuda() {
		override fun json() = json("elementos", "peticion" to peticion, "lista" to lista.map { linkedMapOf("indice" to it.indice, "texto" to it.texto) })
	}

	/** Respuesta a [PedirApps]: los nombres de las apps instaladas. */
	data class Apps(val peticion: Long, val nombres: List<String>) : MensajeAyuda() {
		override fun json() = json("apps", "peticion" to peticion, "nombres" to nombres)
	}

	/** Respuesta genérica a una orden con número de [peticion]. */
	data class Respuesta(val peticion: Long, val ok: Boolean, val texto: String?) : MensajeAyuda() {
		override fun json() = json("respuesta", "peticion" to peticion, "ok" to ok, "texto" to texto)
	}

	/** La ficha técnica del móvil del ayudado: frases cortas, sin nada de la pantalla. */
	data class Ficha(val lineas: List<String>) : MensajeAyuda() {
		override fun json() = json("ficha", "lineas" to lineas)
	}

	/** El ayudado acepta como persona de confianza al ayudante: los dos se guardan esta pareja. */
	data class Pareja(val id: String, val secreto: String, val nombre: String) : MensajeAyuda() {
		override fun json() = json("pareja", "id" to id, "secreto" to secreto, "nombre" to nombre)
	}

	// ── Del ayudante al ayudado ──

	/** Que la voz del ayudado diga [texto], para hablarle sin llamar. */
	data class Decir(val texto: String) : MensajeAyuda() {
		override fun json() = json("decir", "texto" to texto)
	}

	/** Que el móvil del ayudado llame por teléfono a su contacto de ayuda. */
	object Llamame : MensajeAyuda() {
		override fun json() = json("llamame")
		override fun toString() = "Llamame"
	}

	/** Hacer una acción de Tiresias por su nombre. */
	data class HacerAccion(val nombre: String) : MensajeAyuda() {
		override fun json() = json("accion", "nombre" to nombre)
	}

	/** Escribir [texto] en el campo que tenga el foco. */
	data class Escribir(val texto: String) : MensajeAyuda() {
		override fun json() = json("escribir", "texto" to texto)
	}

	/** Pedir la lista de elementos de la pantalla. */
	data class PedirElementos(val peticion: Long) : MensajeAyuda() {
		override fun json() = json("pedir_elementos", "peticion" to peticion)
	}

	/** Usar un elemento de la lista: [como] es [ACTIVAR], [MANTENER] o [ENFOCAR]. */
	data class UsarElemento(val peticion: Long, val indice: Int, val como: String) : MensajeAyuda() {
		override fun json() = json("usar_elemento", "peticion" to peticion, "indice" to indice, "como" to como)

		companion object {
			const val ACTIVAR = "activar"
			const val MANTENER = "mantener"
			const val ENFOCAR = "enfocar"
		}
	}

	/** Pedir la lista de apps. */
	data class PedirApps(val peticion: Long) : MensajeAyuda() {
		override fun json() = json("pedir_apps", "peticion" to peticion)
	}

	/** Abrir una app por su nombre; con [informacion], su pantalla de información en Ajustes. */
	data class AbrirApp(val peticion: Long, val nombre: String, val informacion: Boolean) : MensajeAyuda() {
		override fun json() = json("abrir_app", "peticion" to peticion, "nombre" to nombre, "informacion" to informacion)
	}

	/** Abrir un enlace en el móvil del ayudado. */
	data class AbrirEnlace(val peticion: Long, val enlace: String) : MensajeAyuda() {
		override fun json() = json("abrir_enlace", "peticion" to peticion, "enlace" to enlace)
	}

	companion object {
		/** El mensaje de [json], o null si el tipo es desconocido o está mal formado. No lanza. */
		fun leer(json: String): MensajeAyuda? {
			val m = Json.objeto(json) ?: return null
			return when (m.texto("t")) {
				"hola" -> Hola(
					m.texto("nombre") ?: return null,
					m.texto("version") ?: return null,
					m.texto("idioma") ?: return null,
					m.logico("ayudante") ?: return null,
					textos(m, "pruebas") ?: return null,
				)
				"fin" -> Fin(m.texto("motivo") ?: return null)
				"ping" -> Ping
				"voz" -> Voz(
					m.texto("texto") ?: return null,
					m.logico("interrumpir") ?: return null,
					m.logico("deletrear") ?: return null,
					m.texto("tipo") ?: return null,
					(opcional(m, "idioma") ?: return null).valor,
				)
				"callar" -> Callar
				"sonido" -> Sonido(m.texto("nombre") ?: return null)
				"estado" -> Estado(m.texto("clave") ?: return null, (opcional(m, "texto") ?: return null).valor)
				"elementos" -> {
					val lista = m.lista("lista") ?: return null
					val elementos = ArrayList<ElementoRemoto>(lista.size)
					for (e in lista) {
						val mapa = Json.comoMapa(e) ?: return null
						elementos += ElementoRemoto(indice(mapa, "indice") ?: return null, mapa.texto("texto") ?: return null)
					}
					Elementos(m.entero("peticion") ?: return null, elementos)
				}
				"apps" -> Apps(m.entero("peticion") ?: return null, textos(m, "nombres") ?: return null)
				"ficha" -> Ficha(textos(m, "lineas") ?: return null)
				"pareja" -> Pareja(m.texto("id") ?: return null, m.texto("secreto") ?: return null, m.texto("nombre") ?: return null)
				"decir" -> Decir(m.texto("texto") ?: return null)
				"llamame" -> Llamame
				"respuesta" -> Respuesta(m.entero("peticion") ?: return null, m.logico("ok") ?: return null, (opcional(m, "texto") ?: return null).valor)
				"accion" -> HacerAccion(m.texto("nombre") ?: return null)
				"escribir" -> Escribir(m.texto("texto") ?: return null)
				"pedir_elementos" -> PedirElementos(m.entero("peticion") ?: return null)
				"usar_elemento" -> UsarElemento(m.entero("peticion") ?: return null, indice(m, "indice") ?: return null, m.texto("como") ?: return null)
				"pedir_apps" -> PedirApps(m.entero("peticion") ?: return null)
				"abrir_app" -> AbrirApp(m.entero("peticion") ?: return null, m.texto("nombre") ?: return null, m.logico("informacion") ?: return null)
				"abrir_enlace" -> AbrirEnlace(m.entero("peticion") ?: return null, m.texto("enlace") ?: return null)
				else -> null
			}
		}

		/** Una lista de textos que puede faltar (vacía); null si tiene otra cosa. */
		private fun textos(m: Map<String, Any?>, clave: String): List<String>? {
			val lista = m.lista(clave) ?: return if (m[clave] == null) emptyList() else null
			if (lista.any { it !is String }) return null
			return lista.map { it as String }
		}

		/** Un entero de [clave] que cabe en un `Int`. */
		private fun indice(m: Map<String, Any?>, clave: String): Int? {
			val n = m.entero(clave) ?: return null
			return if (n in Int.MIN_VALUE..Int.MAX_VALUE) n.toInt() else null
		}

		/** Un texto que puede faltar: [Opcional.valor] null si falta o es null; null del todo si es de otro tipo. */
		private fun opcional(m: Map<String, Any?>, clave: String): Opcional? = when (val v = m[clave]) {
			null -> Opcional(null)
			is String -> Opcional(v)
			else -> null
		}

		private class Opcional(val valor: String?)
	}
}
