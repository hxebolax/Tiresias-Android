package com.xebolax.tiresias.nucleo

/**
 * Lo que nunca puede acabar en el registro técnico, que la gente comparte en
 * público para pedir ayuda: claves y contraseñas, texto de las apps, correos,
 * direcciones de red... Se aplica al escribir cada línea y otra vez al
 * compartir el archivo entero (así se limpian también los registros antiguos).
 *
 * Lección (07-11-2026): un usuario pegó su clave de Gemini con un trozo de su
 * chat detrás; Android rechazó la cabecera y el mensaje de la excepción, con la
 * clave y las líneas del chat, fue al registro que compartió en un foro. Por eso
 * no se confía en que cada sitio del código lo evite: de un mensaje de excepción
 * solo se guarda su primera línea, limpia, y las líneas de código.
 */
object LimpiezaRegistro {
	private const val OCULTO = "[oculto]"
	private const val TEXTO = "[texto]"
	private const val DIRECCION = "[dirección]"
	private const val CORREO = "[correo]"
	/** Lo más largo que se guarda de un mensaje (los nuestros caben de sobra). */
	const val MAXIMO_MENSAJE = 400

	/** Claves con formato conocido: Google (AIza..., AQ....), OpenAI y Anthropic (sk-...), GitHub, Slack, tokens JWT. */
	private val CLAVES = listOf(
		Regex("AIza[0-9A-Za-z_\\-]{20,}"),
		Regex("\\bAQ\\.[0-9A-Za-z_\\-.]{8,}"),
		Regex("\\bsk-[0-9A-Za-z_\\-]{8,}"),
		Regex("\\bgh[pousr]_[0-9A-Za-z]{16,}"),
		Regex("\\bxox[abprs]-[0-9A-Za-z\\-]{8,}"),
		Regex("\\beyJ[0-9A-Za-z_\\-]+\\.[0-9A-Za-z_\\-]+\\.[0-9A-Za-z_\\-]*"),
	)

	/** Lo que va detrás de una etiqueta de secreto («key=x», «x-goog-api-key: x», «header value: x»); lo ya tapado no se toca. */
	private val ETIQUETADOS = Regex(
		"(?i)\\b(apikey|api_key|api-key|x-goog-api-key|x-api-key|password|passwd|pwd|secret|token|clave|contraseña|" +
			"key|header value|value)(\\s*[:=]\\s*)(?!\\[oculto])([^\\s,;)\\]]+)",
	)

	/** Una cabecera de autorización: todo lo que va detrás («Authorization: Bearer x»). */
	private val AUTORIZACION = Regex("(?i)\\b((?:proxy-)?authorization)(\\s*[:=]\\s*)(.+)")

	/** «Bearer x», «Basic x», «Token x»: el secreto va detrás de un espacio. */
	private val CON_ESPACIO = Regex("(?i)\\b(bearer|basic|token)(\\s+)([0-9A-Za-z_\\-+/=.~]{8,})")

	/** Un token largo de letras y cifras mezcladas (una clave sin formato conocido). */
	private val TOKEN = Regex("(?<![\\w.$])[0-9A-Za-z_\\-+/=]{24,}(?![\\w.$(])")

	private val COMILLAS = listOf(
		Regex("\"[^\"\\n]*\""),
		Regex("'[^'\\n]{2,}'"),
		Regex("«[^»\\n]*»"),
		Regex("“[^”\\n]*”"),
		Regex("‘[^’\\n]{2,}’"),
	)

	private val CORREOS = Regex("[0-9A-Za-z._%+\\-]+@[0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)+")
	private val CONSULTA_URL = Regex("((?:https?|wss?)://[^\\s?#\"']+)[?#][^\\s\"']*")
	private val TELEFONOS = Regex("\\+\\d[\\d ]{7,16}\\d")

	/** Las direcciones de red que traen los errores de conexión (el PC con NVDA, el otro móvil, Tor). */
	private val DIRECCIONES = listOf(
		Regex("(?i)(connect to )\\S+"),
		Regex("(?i)(resolve host )\"[^\"]*\""),
		Regex("(?i)(hostname )\\S+"),
		Regex("(UnknownHostException: )\\S+"),
		Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b"),
		Regex("(?i)\\b[0-9a-f]{1,4}(?::[0-9a-f]{0,4}){3,7}\\b"),
		Regex("(?i)\\b[a-z2-7]{16,56}\\.onion\\b"),
	)

	/** Limpia una línea de texto: claves, lo etiquetado como secreto, tokens, correos, consultas de URL, direcciones y lo entrecomillado. */
	fun linea(texto: String): String {
		var t = texto
		for (r in DIRECCIONES) t = t.replace(r) { m -> m.groupValues.getOrElse(1) { "" } + DIRECCION }
		for (r in CLAVES) t = t.replace(r, OCULTO)
		for (r in listOf(AUTORIZACION, ETIQUETADOS, CON_ESPACIO)) t = t.replace(r) { m -> if (m.groupValues[3] == OCULTO) m.value else m.groupValues[1] + m.groupValues[2] + OCULTO }
		t = t.replace(CONSULTA_URL) { m -> m.groupValues[1] + "?" + OCULTO }
		t = t.replace(CORREOS, CORREO)
		t = t.replace(TELEFONOS, OCULTO)
		for (r in COMILLAS) t = t.replace(r, TEXTO)
		t = t.replace(TOKEN) { m -> if (m.value.any { it.isDigit() } && m.value.any { it.isLetter() }) OCULTO else m.value }
		return t
	}

	/** Si una línea es de una pila de llamadas (solo código: clases, métodos, archivos y números de línea). */
	fun esLineaDeCodigo(l: String): Boolean {
		val t = l.trim()
		return LINEA_CODIGO.matches(t) || MAS.matches(t)
	}

	private val LINEA_CODIGO = Regex("(at )?[\\w$.<>\\-/]+\\((?:[\\w$.\\-]+\\.(?:kt|java)(?::\\d+)?|Native Method|Unknown Source(?::\\d+)?|SourceFile(?::\\d+)?|D8[$][$]SyntheticClass(?::\\d+)?)\\)")
	private val MAS = Regex("\\.\\.\\. \\d+ more")
	/** La cabecera de un hilo en las pilas del vigilante («main: RUNNABLE»). */
	private val HILO = Regex("[\\w\\- ]{1,60}: [A-Z_]{3,20}")

	/**
	 * Un mensaje nuestro: la primera línea, limpia y recortada; de las siguientes, solo las que son pilas de
	 * llamadas (las del vigilante).
	 */
	fun mensaje(texto: String): String {
		val lineas = texto.split('\n')
		val primera = recortar(linea(lineas.first().trimEnd('\r')))
		val resto = lineas.drop(1).map { it.trimEnd('\r') }.filter { esLineaDeCodigo(it) || HILO.matches(it.trim()) }
		return (listOf(primera) + resto).joinToString("\n")
	}

	/** Lo que se guarda de una excepción: su tipo y la primera línea de su mensaje, limpia (nunca las demás). */
	fun excepcion(tipo: String, mensaje: String?): String {
		val primera = mensaje?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
		val otras = mensaje != null && mensaje.trim().contains('\n')
		return if (primera.isNullOrEmpty()) tipo else "$tipo: ${recortar(linea(primera))}${if (otras) " […]" else ""}"
	}

	/** La pila de una excepción, con sus causas, para el registro: tipos, mensajes limpios y líneas de código. */
	fun traza(error: Throwable, maximoLineas: Int = 40): String {
		val sb = StringBuilder()
		var e: Throwable? = error
		var primero = true
		val vistos = HashSet<Throwable>()
		while (e != null && vistos.add(e)) {
			sb.append(if (primero) "" else "Caused by: ").append(excepcion(e.javaClass.name, e.message)).append('\n')
			val pila = e.stackTrace
			for (f in pila.take(maximoLineas)) sb.append("\tat ").append(f.toString()).append('\n')
			if (pila.size > maximoLineas) sb.append("\t... ").append(pila.size - maximoLineas).append(" more\n")
			primero = false
			e = e.cause
		}
		return sb.toString()
	}

	/**
	 * El registro entero para compartirlo: cada línea limpia, y fuera las que no tienen forma de registro (las
	 * líneas sueltas de un mensaje de excepción de las versiones de antes, como el trozo del chat).
	 */
	fun compartible(texto: String): String = texto.split('\n').mapNotNull { bruta ->
		val l = bruta.trimEnd('\r')
		when {
			l.isBlank() -> l
			FECHA.containsMatchIn(l) -> recortar(linea(l), MAXIMO_MENSAJE + 80)
			esLineaDeCodigo(l) -> l
			HILO.matches(l.trim()) -> l
			CAUSA.containsMatchIn(l) -> recortar(linea(l))
			EXCEPCION.matches(l.trim()) -> recortar(linea(l))
			else -> null
		}
	}.joinToString("\n")

	private val FECHA = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} [IWE] ")
	private val CAUSA = Regex("^\\s*(Caused by|Suppressed): ")
	/** La primera línea de una excepción en una traza («java.io.IOException: algo»). */
	private val EXCEPCION = Regex("[a-z][\\w.]*\\.[A-Z][\\w$]*(?:Exception|Error|Throwable)[\\w$]*(: .*)?")

	private fun recortar(s: String, maximo: Int = MAXIMO_MENSAJE): String = if (s.length <= maximo) s else s.take(maximo) + "…"
}
