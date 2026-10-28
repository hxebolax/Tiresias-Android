package com.xebolax.tiresias.ia

import android.content.Context
import android.util.Base64
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * La inteligencia artificial de Tiresias: describir imágenes y la pantalla,
 * proponer etiquetas, responder preguntas, resumir y traducir.
 *
 * Proveedores (portado de Lector Universal, del mismo autor, y ampliado):
 *
 * - **Descargado** ([ModeloLocal]): Gemma 4 con LiteRT-LM, sin Internet, en
 *   un proceso aparte para que un modelo de gigas nunca tire el lector.
 * - **Del móvil** ([Nano]): Gemini Nano de los Pixel y móviles compatibles, con
 *   el complemento «Tiresias IA del móvil» (abre su propia ventana porque AICore
 *   solo responde a la app que está delante).
 * - **En Internet con la clave del usuario**: Claude (Anthropic), OpenAI,
 *   Gemini y cualquier servidor compatible con OpenAI (Ollama, LM Studio...).
 *
 * Nada se envía sin que el usuario lo pida. Las claves van cifradas ([Secretos]).
 */
object AsistenteIA {

	enum class Proveedor { DESCARGADO, MOVIL, CLAUDE, OPENAI, GEMINI, COMPATIBLE }

	class Mensaje(val deUsuario: Boolean, val texto: String)

	/** Un fallo dicho claro; [codigo], el de HTTP si lo hubo (0 si no). */
	class ErrorIA(mensaje: String, val codigo: Int = 0) : Exception(mensaje)

	const val MODELO_CLAUDE = "claude-haiku-5-5"
	const val MODELO_OPENAI = "gpt-5-mini"
	/**
	 * Un modelo concreto, no un alias: «gemini-flash-latest» apuntó a un modelo que
	 * daba 503 casi siempre con las claves gratuitas, y quien lo sufría creía que su
	 * clave estaba mal (Backtalk 298). Si el elegido falla, se prueban los de [GEMINI_RESERVA].
	 */
	const val MODELO_GEMINI = "gemini-2.5-flash"
	private val GEMINI_RESERVA = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest")

	/** El proveedor que se usará, o null si no hay ninguno. «Automático» elige el primero disponible. */
	fun elegir(c: Context): Proveedor? {
		val p = Ajustes(c).p
		val s = Secretos(c)
		return when (p.getString(CLAVE_PROVEEDOR, "automatico")) {
			"descargado" -> Proveedor.DESCARGADO
			"movil" -> Proveedor.MOVIL
			"claude" -> Proveedor.CLAUDE
			"openai" -> Proveedor.OPENAI
			"gemini" -> Proveedor.GEMINI
			"compatible" -> Proveedor.COMPATIBLE
			"ninguno" -> null
			else -> when {
				ModeloLocal.estado(c) == ModeloLocal.Estado.DESCARGADO -> Proveedor.DESCARGADO
				Nano.instalado(c) -> Proveedor.MOVIL
				s.hay(Secretos.CLAUDE) -> Proveedor.CLAUDE
				s.hay(Secretos.OPENAI) -> Proveedor.OPENAI
				s.hay(Secretos.GEMINI) -> Proveedor.GEMINI
				p.getString(CLAVE_URL_COMPATIBLE, "").orEmpty().isNotBlank() -> Proveedor.COMPATIBLE
				else -> null
			}
		}
	}

	/** Si [p] manda los datos fuera del móvil (hay que avisar antes de la primera vez). */
	fun saleDelMovil(p: Proveedor) = p == Proveedor.CLAUDE || p == Proveedor.OPENAI || p == Proveedor.GEMINI ||
		(p == Proveedor.COMPATIBLE)

	fun nombre(c: Context, p: Proveedor): String = Idioma.envolver(c).getString(
		when (p) {
			Proveedor.DESCARGADO -> R.string.ia_descargado
			Proveedor.MOVIL -> R.string.ia_movil
			Proveedor.CLAUDE -> R.string.ia_claude
			Proveedor.OPENAI -> R.string.ia_openai
			Proveedor.GEMINI -> R.string.ia_gemini
			Proveedor.COMPATIBLE -> R.string.ia_compatible
		}
	)

	/** Instrucciones generales: idioma de Tiresias y respuestas pensadas para escuchar, sin formato. */
	fun instrucciones(c: Context): String {
		val idioma = Idioma.efectivo(c)
		return Idioma.envolver(c).getString(R.string.ia_instrucciones, idioma.getDisplayLanguage(idioma))
	}

	/**
	 * Envía la conversación (con una imagen JPEG opcional en la primera pregunta)
	 * y devuelve la respuesta. Bloquea: llámese fuera del hilo principal.
	 * [Proveedor.MOVIL] no pasa por aquí: lo atiende el complemento con su ventana.
	 */
	fun preguntar(c: Context, proveedor: Proveedor, conversacionPedida: List<Mensaje>, imagenPedida: File? = null): String {
		val t = Idioma.envolver(c)
		val sistema = instrucciones(c)
		// Las personas que conoce el usuario: su hoja de fotos va con la imagen (con la IA del móvil, unida a ella)
		var conversacion = conversacionPedida
		var imagen = imagenPedida
		var referencia: File? = null
		if (imagenPedida != null && Personas.activas(c, proveedor)) {
			Personas.hojaDePersonas(c)?.let { hoja ->
				val unida = proveedor == Proveedor.DESCARGADO
				if (unida) imagen = Personas.unir(c, imagenPedida, hoja) else referencia = hoja
				conversacion = conversacionPedida.mapIndexed { i, m -> if (i == 0 && m.deUsuario) Mensaje(true, m.texto + "\n\n" + Personas.instruccion(c, unida)) else m }
			}
		}
		val p = Ajustes(c).p
		val s = Secretos(c)
		val respuesta = when (proveedor) {
			Proveedor.DESCARGADO -> ModeloLocal.preguntar(c, sistema, conversacion, imagen)
			Proveedor.MOVIL -> throw ErrorIA(t.getString(R.string.ia_movil_ventana))
			Proveedor.CLAUDE -> claude(c, s.leer(Secretos.CLAUDE), p.getString(CLAVE_MODELO_CLAUDE, "").orEmpty(), sistema, conversacion, imagen, referencia)
			Proveedor.OPENAI -> compatibleOpenAi(c, "https://api.openai.com/v1/chat/completions", s.leer(Secretos.OPENAI),
				p.getString(CLAVE_MODELO_OPENAI, "").orEmpty().ifBlank { MODELO_OPENAI }, sistema, conversacion, imagen, R.string.ia_openai, referencia = referencia)
			Proveedor.GEMINI -> gemini(c, s.leer(Secretos.GEMINI), p.getString(CLAVE_MODELO_GEMINI, "").orEmpty(), sistema, conversacion, imagen, referencia)
			Proveedor.COMPATIBLE -> {
				val base = p.getString(CLAVE_URL_COMPATIBLE, "").orEmpty().trimEnd('/')
				if (base.isBlank()) throw ErrorIA(t.getString(R.string.ia_falta_url))
				compatibleOpenAi(c, "$base/chat/completions", s.leer(Secretos.COMPATIBLE), p.getString(CLAVE_MODELO_COMPATIBLE, "").orEmpty(),
					sistema, conversacion, imagen, R.string.ia_compatible, claveObligatoria = false, referencia = referencia)
			}
		}
		return respuesta.trim().ifBlank { throw ErrorIA(t.getString(R.string.ia_sin_respuesta)) }
	}

	private fun base64(imagen: File) = Base64.encodeToString(imagen.readBytes(), Base64.NO_WRAP)

	private fun claude(c: Context, clave: String, modelo: String, sistema: String, conversacion: List<Mensaje>, imagen: File?, referencia: File? = null): String {
		val t = Idioma.envolver(c)
		if (clave.isBlank()) throw ErrorIA(t.getString(R.string.ia_falta_clave, t.getString(R.string.ia_claude)))
		val mensajes = JSONArray()
		conversacion.forEachIndexed { i, m ->
			val partes = JSONArray()
			if (i == 0 && imagen != null) {
				partes.put(JSONObject().put("type", "image").put("source", JSONObject()
					.put("type", "base64").put("media_type", "image/jpeg").put("data", base64(imagen))))
				referencia?.let { r ->
					partes.put(JSONObject().put("type", "image").put("source", JSONObject()
						.put("type", "base64").put("media_type", "image/jpeg").put("data", base64(r))))
				}
			}
			partes.put(JSONObject().put("type", "text").put("text", m.texto))
			mensajes.put(JSONObject().put("role", if (m.deUsuario) "user" else "assistant").put("content", partes))
		}
		val cuerpo = JSONObject()
			.put("model", modelo.ifBlank { MODELO_CLAUDE })
			.put("max_tokens", 2000)
			.put("system", sistema)
			.put("messages", mensajes)
		val r = enviar(c, "https://api.anthropic.com/v1/messages", mapOf("x-api-key" to clave, "anthropic-version" to "2023-06-01"), cuerpo, t.getString(R.string.ia_claude))
		val partes = r.optJSONArray("content") ?: return ""
		return (0 until partes.length()).mapNotNull { partes.optJSONObject(it) }.filter { it.optString("type") == "text" }
			.joinToString("") { it.optString("text") }
	}

	private fun compatibleOpenAi(
		c: Context, url: String, clave: String, modelo: String, sistema: String, conversacion: List<Mensaje>, imagen: File?,
		nombre: Int, claveObligatoria: Boolean = true, referencia: File? = null,
	): String {
		val t = Idioma.envolver(c)
		if (claveObligatoria && clave.isBlank()) throw ErrorIA(t.getString(R.string.ia_falta_clave, t.getString(nombre)))
		val mensajes = JSONArray().put(JSONObject().put("role", "system").put("content", sistema))
		conversacion.forEachIndexed { i, m ->
			if (i == 0 && imagen != null) {
				val partes = JSONArray()
					.put(JSONObject().put("type", "text").put("text", m.texto))
					.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + base64(imagen))))
				referencia?.let { r -> partes.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + base64(r)))) }
				mensajes.put(JSONObject().put("role", "user").put("content", partes))
			} else mensajes.put(JSONObject().put("role", if (m.deUsuario) "user" else "assistant").put("content", m.texto))
		}
		val cuerpo = JSONObject().put("messages", mensajes)
		if (modelo.isNotBlank()) cuerpo.put("model", modelo)
		val cabeceras = if (clave.isNotBlank()) mapOf("Authorization" to "Bearer $clave") else emptyMap()
		val r = enviar(c, url, cabeceras, cuerpo, t.getString(nombre))
		return r.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
	}

	private fun gemini(c: Context, clave: String, modelo: String, sistema: String, conversacion: List<Mensaje>, imagen: File?, referencia: File? = null): String {
		val t = Idioma.envolver(c)
		if (clave.isBlank()) throw ErrorIA(t.getString(R.string.ia_falta_clave, t.getString(R.string.ia_gemini)))
		val contenidos = JSONArray()
		conversacion.forEachIndexed { i, m ->
			val partes = JSONArray()
			if (i == 0 && imagen != null) partes.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", base64(imagen))))
			if (i == 0 && referencia != null) partes.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", base64(referencia))))
			partes.put(JSONObject().put("text", m.texto))
			contenidos.put(JSONObject().put("role", if (m.deUsuario) "user" else "model").put("parts", partes))
		}
		val cuerpo = JSONObject()
			.put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sistema))))
			.put("contents", contenidos)
		// El elegido y, si el servicio falla (5xx) o no lo encuentra (404), los de reserva
		val modelos = (listOf(modelo.ifBlank { MODELO_GEMINI }) + GEMINI_RESERVA).distinct()
		var fallo: ErrorIA? = null
		var r: JSONObject? = null
		for (m in modelos) {
			val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent"
			try {
				r = enviar(c, url, mapOf("x-goog-api-key" to clave), cuerpo, t.getString(R.string.ia_gemini))
				break
			} catch (e: ErrorIA) {
				if (e.codigo != 404 && e.codigo !in 500..599) throw e
				Registro.w(ETIQUETA, "El modelo $m de Gemini no contesta (${e.codigo}): se prueba otro", e)
				fallo = e
			}
		}
		if (r == null) throw ErrorIA(t.getString(R.string.ia_error_modelo, t.getString(R.string.ia_gemini)), fallo?.codigo ?: 0)
		val partes = r.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
		return (0 until partes.length()).joinToString("") { partes.optJSONObject(it)?.optString("text").orEmpty() }
	}

	private fun enviar(c: Context, url: String, cabeceras: Map<String, String>, cuerpo: JSONObject, servicio: String): JSONObject {
		val t = Idioma.envolver(c)
		val conexion = URL(url).openConnection() as HttpURLConnection
		try {
			conexion.requestMethod = "POST"
			conexion.connectTimeout = 20_000
			conexion.readTimeout = 120_000
			conexion.doOutput = true
			conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
			cabeceras.forEach { (k, v) -> conexion.setRequestProperty(k, v) }
			conexion.outputStream.use { it.write(cuerpo.toString().toByteArray()) }
			val codigo = conexion.responseCode
			val texto = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
			if (codigo !in 200..299) {
				// El mensaje del servicio (clave incorrecta, sin saldo, modelo que no existe...), sin la clave
				val detalle = runCatching {
					val j = JSONObject(texto)
					j.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: j.optString("message")
				}.getOrNull().orEmpty().ifBlank { texto.take(300) }
				// Lo que el usuario puede arreglar, dicho claro (Backtalk 26 y 28); lo demás, con el mensaje del servicio
				throw ErrorIA(when (codigo) {
					401, 403 -> t.getString(R.string.ia_error_clave, servicio)
					402 -> t.getString(R.string.ia_error_saldo, servicio)
					429 -> t.getString(R.string.ia_error_limite, servicio)
					in 500..599 -> t.getString(R.string.ia_error_caido, servicio)
					else -> t.getString(R.string.ia_error_servicio, codigo, detalle)
				}, codigo)
			}
			return JSONObject(texto)
		} catch (e: ErrorIA) {
			throw e
		} catch (e: IOException) {
			Registro.w(ETIQUETA, "Sin conexión con el servicio de IA", e)
			throw ErrorIA(t.getString(R.string.ia_sin_conexion))
		} finally {
			conexion.disconnect()
		}
	}

	private const val ETIQUETA = "AsistenteIA"
	const val CLAVE_PROVEEDOR = "ia_proveedor"
	const val CLAVE_MODELO_CLAUDE = "ia_modelo_claude"
	const val CLAVE_MODELO_OPENAI = "ia_modelo_openai"
	const val CLAVE_MODELO_GEMINI = "ia_modelo_gemini"
	const val CLAVE_MODELO_COMPATIBLE = "ia_modelo_compatible"
	const val CLAVE_URL_COMPATIBLE = "ia_url_compatible"
	/** Proveedores de Internet ya aceptados por el usuario (se avisa la primera vez de qué se envía). */
	const val CLAVE_ACEPTADOS = "ia_aceptados"
}
