package com.xebolax.tiresias.scripts

import android.content.Context
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.actualizaciones.Actualizador
import com.xebolax.tiresias.base.Idioma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * La tienda de complementos: un catálogo en el servidor de Tiresias, en la
 * misma dirección que las actualizaciones (`<servidor>/complementos/catalogo.json`),
 * con cada complemento en su archivo `.js`. Se descarga solo lo que el usuario
 * elige y se comprueba que es lo publicado (tamaño y SHA-256) antes de enseñarle
 * sus permisos e instalarlo. Lo publica `herramientas\publicar_actualizacion.ps1`
 * a partir de la carpeta `complementos\` del proyecto.
 *
 * Respaldo: la misma tienda en la carpeta `tienda/` del repositorio de GitHub
 * (solo con `BuildConfig.GITHUB_ACTIVO`), si el servidor propio no responde, o
 * la única si en Ajustes se eligió actualizar solo desde GitHub.
 */
object Tienda {
	/** Un complemento del catálogo. */
	data class Entrada(
		val id: String,
		val nombre: String,
		val version: String,
		val descripcion: String,
		val autor: String,
		val apps: List<String>,
		val permisos: List<String>,
		val archivo: String,
		val sha256: String,
		val tamano: Long,
		/** De dónde se leyó (el servidor propio o GitHub): de ahí se descarga. */
		val base: String = "",
	)

	private const val MAXIMO = 512 * 1024
	private const val MAXIMO_TEMA = 24 * 1024 * 1024
	private const val ESPERA_MS = 15_000

	/** La tienda en el repositorio de GitHub (rama principal, carpeta `tienda`). */
	val GITHUB = "https://raw.githubusercontent.com/${BuildConfig.REPO_GITHUB}/main/tienda"

	/** Dónde se busca la tienda, por orden. */
	fun bases(c: Context): List<String> {
		val propio = (Actualizador.urlBase(c) ?: BuildConfig.URL_ACTUALIZACIONES).trimEnd('/') + "/complementos"
		return when {
			!BuildConfig.GITHUB_ACTIVO -> listOf(propio)
			com.xebolax.tiresias.base.Ajustes(c).origenActualizaciones == com.xebolax.tiresias.base.Ajustes.ORIGEN_GITHUB -> listOf(GITHUB)
			else -> listOf(propio, GITHUB)
		}
	}

	/** Las fuentes que ha añadido el usuario (la carpeta de un catálogo en GitHub, GitLab, Codeberg o cualquier web con https). */
	fun fuentes(c: Context): List<String> = com.xebolax.tiresias.base.Ajustes(c).fuentesComplementos

	/**
	 * Convierte lo que pega el usuario en la carpeta del catálogo: una página de
	 * un repositorio de GitHub, GitLab o Codeberg pasa a su dirección «raw». Solo https.
	 */
	fun normalizarFuente(texto: String): String? {
		var u = texto.trim().removeSuffix("/").removeSuffix("/catalogo.json")
		if (!u.startsWith("https://")) return null
		Regex("^https://github\\.com/([^/]+)/([^/]+)/(?:tree|blob)/([^/]+)(/.*)?$").find(u)?.let { m ->
			u = "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}${m.groupValues[4]}"
		}
		Regex("^https://(gitlab\\.com|codeberg\\.org)/(.+?)/-?/?(?:tree|src/branch)/([^/]+)(/.*)?$").find(u)?.let { m ->
			u = if (m.groupValues[1] == "gitlab.com") "https://gitlab.com/${m.groupValues[2]}/-/raw/${m.groupValues[3]}${m.groupValues[4]}"
			else "https://codeberg.org/${m.groupValues[2]}/raw/branch/${m.groupValues[3]}${m.groupValues[4]}"
		}
		return u
	}

	/**
	 * El catálogo: el del primer sitio propio que responda y, además, el de cada
	 * fuente del usuario (lo del servidor propio manda si un id se repite).
	 */
	suspend fun catalogo(c: Context): List<Entrada> = withContext(Dispatchers.IO) {
		val propio = catalogoPropio(c)
		val todo = ArrayList(propio.getOrDefault(emptyList()))
		for (base in fuentes(c)) {
			try {
				val texto = String(bajar("$base/catalogo.json?t=${System.currentTimeMillis()}", MAXIMO), Charsets.UTF_8)
				leer(texto, Idioma.efectivo(c).language).forEach { e -> if (todo.none { it.id == e.id }) todo += e.copy(base = base) }
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Exception) {
				com.xebolax.tiresias.base.Registro.w("Tienda", "La fuente de complementos $base no respondió", e)
			}
		}
		if (todo.isEmpty()) propio.exceptionOrNull()?.let { throw it }
		todo
	}

	/** El catálogo publicado en el servidor propio (o en GitHub): del primer sitio que responda. */
	private fun catalogoPropio(c: Context): Result<List<Entrada>> {
		var fallo: Exception? = null
		for (base in bases(c)) {
			try {
				val texto = String(bajar("$base/catalogo.json?t=${System.currentTimeMillis()}", MAXIMO), Charsets.UTF_8)
				return Result.success(leer(texto, Idioma.efectivo(c).language).map { it.copy(base = base) })
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Exception) {
				com.xebolax.tiresias.base.Registro.w("Tienda", "La tienda de $base no respondió", e)
				fallo = e
			}
		}
		return Result.failure(fallo ?: IOException("Sin tienda"))
	}

	/** Un tema de sonidos del catálogo (un ZIP con un archivo por sonido). */
	data class Tema(val id: String, val nombre: String, val descripcion: String, val autor: String, val archivo: String, val sha256: String, val tamano: Long, val base: String = "")

	/** Los temas de sonidos del catálogo (`temas` en catalogo.json), del servidor propio y de las fuentes del usuario. */
	suspend fun temas(c: Context): List<Tema> = withContext(Dispatchers.IO) {
		val lista = ArrayList<Tema>()
		var fallo: Exception? = null
		for (base in (bases(c).take(1) + fuentes(c))) {
			try {
				val texto = String(bajar("$base/catalogo.json?t=${System.currentTimeMillis()}", MAXIMO), Charsets.UTF_8)
				leerTemas(texto).forEach { t -> if (lista.none { it.id == t.id }) lista += t.copy(base = base) }
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Exception) {
				com.xebolax.tiresias.base.Registro.w("Tienda", "Los temas de $base no respondieron", e)
				fallo = e
			}
		}
		if (lista.isEmpty()) fallo?.let { throw it }
		lista
	}

	fun leerTemas(texto: String): List<Tema> {
		val a = JSONObject(texto).optJSONArray("temas") ?: return emptyList()
		return (0 until a.length()).mapNotNull { i ->
			val o = a.optJSONObject(i) ?: return@mapNotNull null
			val id = o.optString("id")
			val archivo = o.optString("archivo")
			if (!Regex("[a-z0-9][a-z0-9._-]{1,63}").matches(id) || !Regex("[A-Za-z0-9._-]+\\.zip").matches(archivo)) return@mapNotNull null
			Tema(id, o.optString("nombre", id), o.optString("descripcion"), o.optString("autor"), archivo, o.optString("sha256").lowercase(), o.optLong("tamano", 0))
		}
	}

	/** El ZIP del tema, comprobado contra el catálogo. */
	suspend fun descargarTema(c: Context, t: Tema): ByteArray = withContext(Dispatchers.IO) {
		val textos = Idioma.envolver(c)
		val datos = bajar("${t.base.ifEmpty { bases(c).first() }}/${t.archivo}", MAXIMO_TEMA)
		if (t.tamano > 0 && datos.size.toLong() != t.tamano) throw IOException(textos.getString(R.string.tienda_no_coincide))
		val sha = MessageDigest.getInstance("SHA-256").digest(datos).joinToString("") { "%02x".format(it) }
		if (t.sha256.isNotEmpty() && sha != t.sha256) throw IOException(textos.getString(R.string.tienda_no_coincide))
		datos
	}

	/**
	 * Lee un catálogo; lo que no vale se salta (un nombre de archivo raro, un id
	 * que no es de complemento). El nombre y la descripción, en [idioma] si el
	 * catálogo los trae (`nombre_en`), o en español.
	 */
	fun leer(texto: String, idioma: String = "es"): List<Entrada> {
		val lista = JSONObject(texto).optJSONArray("complementos") ?: return emptyList()
		return (0 until lista.length()).mapNotNull { i ->
			val o = lista.optJSONObject(i) ?: return@mapNotNull null
			val id = o.optString("id")
			val archivo = o.optString("archivo")
			// Solo un nombre de archivo, nunca una ruta ni otra web
			if (!Regex("[a-z0-9][a-z0-9._-]{1,63}").matches(id) || !Regex("[A-Za-z0-9._-]+\\.js").matches(archivo)) return@mapNotNull null
			fun lista(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
			fun local(k: String, defecto: String = "") = o.optString("${k}_$idioma").takeIf { idioma != "es" && it.isNotBlank() } ?: o.optString(k, defecto)
			Entrada(id, local("nombre", id), o.optString("version", "1.0"), local("descripcion"), o.optString("autor"),
				lista("apps"), lista("permisos"), archivo, o.optString("sha256").lowercase(), o.optLong("tamano", 0))
		}
	}

	/** El código del complemento, comprobado contra el catálogo. */
	suspend fun descargar(c: Context, e: Entrada): String = withContext(Dispatchers.IO) {
		val t = Idioma.envolver(c)
		val datos = bajar("${e.base.ifEmpty { bases(c).first() }}/${e.archivo}", MAXIMO)
		if (e.tamano > 0 && datos.size.toLong() != e.tamano) throw IOException(t.getString(R.string.tienda_no_coincide))
		val sha = MessageDigest.getInstance("SHA-256").digest(datos).joinToString("") { "%02x".format(it) }
		if (e.sha256.isNotEmpty() && sha != e.sha256) throw IOException(t.getString(R.string.tienda_no_coincide))
		String(datos, Charsets.UTF_8)
	}

	/** ¿Hay una versión más nueva de [e] que la instalada [instalada]? */
	fun esNueva(e: Entrada, instalada: String?): Boolean = instalada != null && Actualizador.compararVersiones(e.version, instalada) > 0

	private fun bajar(url: String, maximo: Int): ByteArray {
		val c = (URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = ESPERA_MS
			readTimeout = ESPERA_MS
			useCaches = false
			setRequestProperty("User-Agent", "Tiresias/${BuildConfig.VERSION_NAME} (Android)")
			setRequestProperty("Cache-Control", "no-cache")
		}
		try {
			if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
			val salida = ByteArrayOutputStream()
			c.inputStream.use { entrada ->
				val buffer = ByteArray(16 * 1024)
				while (true) {
					val n = entrada.read(buffer)
					if (n < 0) break
					salida.write(buffer, 0, n)
					if (salida.size() > maximo) throw IOException("Demasiado grande")
				}
			}
			return salida.toByteArray()
		} finally {
			c.disconnect()
		}
	}
}
