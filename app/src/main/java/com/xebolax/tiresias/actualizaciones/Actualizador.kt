/*
 * Portado del actualizador de Tiresias y Lector Universal (mismo autor).
 */
package com.xebolax.tiresias.actualizaciones

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** De dónde viene una versión publicada. */
enum class OrigenVersion { SERVIDOR, GITHUB }

/** Un complemento publicado junto a la versión (apartado «complementos» de version.json). */
data class ComplementoPublicado(val id: String, val paquete: String, val archivo: String, val sha256: String, val tamano: Long)

/** Versión publicada (version.json del servidor propio o de la release de GitHub). */
data class VersionPublicada(
	val version: String,
	val versionCode: Int,
	val archivo: String,
	val sha256: String,
	val tamano: Long,
	val fecha: String,
	val notas: String,
	val base: String,
	val origen: OrigenVersion = OrigenVersion.SERVIDOR,
	/** Otras direcciones del mismo APK, por si la principal falla al descargar. */
	val alternativas: List<String> = emptyList(),
	/** APK aparte que se publican con esta versión (el complemento de Gemini Nano). */
	val complementos: List<ComplementoPublicado> = emptyList(),
) {
	val url: String get() = "$base/$archivo"
}

/**
 * Actualizaciones sin tiendas ni Play Services, del origen elegido en Ajustes:
 *
 * - Solo el servidor propio (lo normal mientras el repositorio de GitHub no
 *   esté publicado): `version.json` y `Tiresias_v<versión>_<procesador>.apk` (uno por procesador) en
 *   `https://xebolax.synology.me/Tiresias-Android`.
 * - Automático: el servidor propio y, si no responde o falla, las releases
 *   de GitHub (el mismo `version.json` subido a la última release y, si no
 *   estuviera, su API). Solo con `BuildConfig.GITHUB_ACTIVO`.
 * - Solo GitHub o un servidor personalizado con la misma estructura que el propio.
 *
 * Después se descarga el APK (si se corta, se continúa; si la fuente falla del
 * todo, se prueba la otra) y se comprueba que es el publicado (tamaño y
 * SHA-256) y que es Tiresias, más nueva y firmada con la MISMA clave
 * que la instalada. Android además rechaza instalar encima una app con otra
 * firma, así que una fuente manipulada no puede colar otra aplicación. El
 * instalador del sistema pide la confirmación al usuario.
 */
object Actualizador {
	private const val AGENTE = "Tiresias-Actualizador"
	private const val ESPERA_MS = 20_000
	/** Cortes seguidos SIN avanzar antes de rendirse (si avanza, el contador vuelve a cero). */
	private const val REINTENTOS = 8
	private const val ETIQUETA = "Actualizador"

	/** Las releases del repositorio de GitHub (respaldo del servidor propio). */
	private val GITHUB_WEB = "https://github.com/${BuildConfig.REPO_GITHUB}/releases"
	private val GITHUB_API = "https://api.github.com/repos/${BuildConfig.REPO_GITHUB}/releases/latest"

	/** Dónde está el APK de [version] en las releases de GitHub (etiqueta v<versión>). */
	fun baseGitHub(version: String): String = "$GITHUB_WEB/download/v$version"

	/** Dirección del servidor que toca según el origen elegido (null: solo GitHub). */
	fun urlBase(contexto: Context): String? {
		val p = Ajustes(contexto)
		return when (p.origenActualizaciones) {
			Ajustes.ORIGEN_GITHUB -> if (BuildConfig.GITHUB_ACTIVO) null else BuildConfig.URL_ACTUALIZACIONES
			Ajustes.ORIGEN_PERSONALIZADO -> p.urlActualizaciones
			else -> BuildConfig.URL_ACTUALIZACIONES
		}
	}

	/** Carpeta de la caché donde se descarga el APK. */
	fun carpeta(contexto: Context) = File(contexto.cacheDir, "actualizaciones").apply { mkdirs() }

	private fun textos(contexto: Context) = Idioma.envolver(contexto)

	private fun conexion(url: String, conectar: Int = ESPERA_MS): HttpURLConnection =
		(URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = conectar
			readTimeout = ESPERA_MS
			useCaches = false
			setRequestProperty("User-Agent", AGENTE)
			setRequestProperty("Cache-Control", "no-cache")
			// Conexión nueva cada vez: una reutilizada que el servidor ya cerró corta la descarga
			setRequestProperty("Connection", "close")
		}

	/** "1.2.10" > "1.2.9": compara número a número. */
	fun compararVersiones(a: String, b: String): Int {
		val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
		val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
		for (i in 0 until maxOf(x.size, y.size)) {
			val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
			if (c != 0) return c
		}
		return 0
	}

	/**
	 * La versión publicada, según el origen elegido en Ajustes. En automático,
	 * primero el servidor propio (con un reintento corto) y, si no responde o lo
	 * que da no vale, GitHub; lanza la excepción del servidor propio si tampoco
	 * se puede con GitHub. Con un origen fijo, solo ese.
	 */
	suspend fun versionPublicada(contexto: Context): VersionPublicada = withContext(Dispatchers.IO) {
		val origen = Ajustes(contexto).origenActualizaciones
		val base = urlBase(contexto) ?: return@withContext versionGitHub(contexto)
		// GitHub solo cuando el repositorio esté publicado (BuildConfig.GITHUB_ACTIVO)
		val conGitHub = origen == Ajustes.ORIGEN_AUTOMATICO && BuildConfig.GITHUB_ACTIVO
		var fallo: Exception? = null
		// Dos intentos cortos: es un archivo diminuto y quien busca a mano está esperando
		for (intento in 1..2) {
			try {
				val v = leerVersion(contexto, base, OrigenVersion.SERVIDOR)
				// El mismo APK en GitHub, por si la descarga del servidor falla después
				return@withContext if (conGitHub) v.copy(alternativas = listOf("${baseGitHub(v.version)}/${v.archivo}")) else v
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "El servidor propio no dio la versión (intento $intento)", e)
				fallo = e
				if (intento == 1 && e is IOException) delay(1500) else break
			}
		}
		if (!conGitHub) throw fallo ?: IOException("Sin respuesta de $base")
		try {
			val v = versionGitHub(contexto)
			Registro.i(ETIQUETA, "Versión ${v.version} encontrada en GitHub (el servidor propio falló)")
			v.copy(alternativas = listOf("$base/${v.archivo}"))
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Tampoco se pudo leer la versión de GitHub", e)
			throw fallo ?: e
		}
	}

	/**
	 * La última release de GitHub: su version.json (el mismo que el del
	 * servidor) y, si no lo tiene, la API de GitHub.
	 */
	private fun versionGitHub(contexto: Context): VersionPublicada {
		try {
			val v = leerVersion(contexto, "$GITHUB_WEB/latest/download", OrigenVersion.GITHUB)
			return v.copy(base = baseGitHub(v.version))
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "La release de GitHub no tiene version.json: se usa su API", e)
		}
		val c = conexion(GITHUB_API, conectar = 8_000)
		try {
			c.setRequestProperty("Accept", "application/vnd.github+json")
			if (c.responseCode != 200) throw IllegalStateException(textos(contexto).getString(R.string.act_servidor_respondio, c.responseCode))
			val json = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
			return desdeApiGitHub(json) ?: throw IllegalArgumentException(textos(contexto).getString(R.string.act_version_invalida))
		} finally {
			c.disconnect()
		}
	}

	/**
	 * Convierte la respuesta de la API de GitHub (`releases/latest`) en una
	 * versión publicada: la etiqueta v<versión>, el APK de la release (su nombre
	 * debe ser Tiresias_v<versión>.apk) con su tamaño y su SHA-256
	 * («digest» del recurso) y las novedades del cuerpo. Null si no vale.
	 */
	fun desdeApiGitHub(json: JSONObject): VersionPublicada? {
		if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
		val version = json.optString("tag_name").trim().removePrefix("v").takeIf { it.isNotEmpty() } ?: return null
		val recursos = json.optJSONArray("assets") ?: return null
		// Los APK de la release: uno por procesador (Tiresias_v1.0.0_arm64-v8a.apk) o uno para todos
		val apks = HashMap<String, JSONObject>()
		for (i in 0 until recursos.length()) {
			val r = recursos.optJSONObject(i) ?: continue
			val m = APK_GITHUB.matchEntire(r.optString("name")) ?: continue
			// Solo descargas del propio repositorio
			if (!r.optString("browser_download_url").startsWith("$GITHUB_WEB/download/")) continue
			apks[m.groupValues[1]] = r
		}
		val r = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { apks[it] } ?: apks[""] ?: return null
		val url = r.optString("browser_download_url")
		return VersionPublicada(
			version = version,
			versionCode = 0,
			archivo = r.optString("name"),
			sha256 = r.optString("digest").removePrefix("sha256:").trim().lowercase().takeIf { it.length == 64 }.orEmpty(),
			tamano = r.optLong("size", 0L),
			fecha = json.optString("published_at").take(10),
			notas = json.optString("body").trim(),
			base = url.substringBeforeLast('/'),
			origen = OrigenVersion.GITHUB,
		)
	}

	/** «Tiresias_v1.0.0.apk» o «Tiresias_v1.0.0_arm64-v8a.apk» (el grupo 1 es el procesador). */
	private val APK_GITHUB = Regex("Tiresias_v[0-9.]+(?:_([a-z0-9_-]+))?\\.apk")

	private fun leerVersion(contexto: Context, base: String, origen: OrigenVersion): VersionPublicada {
		val c = conexion("$base/version.json?t=${System.currentTimeMillis()}", conectar = 8_000)
		try {
			if (c.responseCode != 200) throw IllegalStateException(textos(contexto).getString(R.string.act_servidor_respondio, c.responseCode))
			val o = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
			return desdeVersionJson(o, base, origen) ?: throw IllegalArgumentException(textos(contexto).getString(R.string.act_version_invalida))
		} finally {
			c.disconnect()
		}
	}

	/** Lee un version.json; null si no es válido. */
	fun desdeVersionJson(o: JSONObject, base: String, origen: OrigenVersion = OrigenVersion.SERVIDOR): VersionPublicada? {
		val version = o.optString("version").trim()
		// Uno por procesador («abis»): el de este móvil; si no, el general («file»)
		val propio = o.optJSONObject("abis")?.let { abis -> Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abis.optJSONObject(it) } } ?: o
		val archivo = propio.optString("file").trim()
		if (version.isEmpty() || archivo.isEmpty()) return null
		// Solo un nombre de archivo, nunca una ruta ni otra web
		if (!Regex("[A-Za-z0-9._-]+\\.apk").matches(archivo)) return null
		return VersionPublicada(
			version = version,
			versionCode = o.optInt("versionCode", 0),
			archivo = archivo,
			sha256 = propio.optString("sha256").trim().lowercase(),
			tamano = propio.optLong("size", 0L),
			fecha = o.optString("date"),
			notas = o.optString("notas").trim(),
			base = base,
			origen = origen,
			complementos = complementos(o),
		)
	}

	private fun complementos(o: JSONObject): List<ComplementoPublicado> {
		val lista = o.optJSONArray("complementos") ?: return emptyList()
		return (0 until lista.length()).mapNotNull { i ->
			val c = lista.optJSONObject(i) ?: return@mapNotNull null
			val archivo = c.optString("file").trim()
			if (!Regex("[A-Za-z0-9._-]+\\.apk").matches(archivo)) return@mapNotNull null
			ComplementoPublicado(c.optString("id"), c.optString("package"), archivo, c.optString("sha256").trim().lowercase(), c.optLong("size", 0L))
		}
	}

	/**
	 * Descarga y verifica el complemento [id] publicado con la última versión
	 * (mismas comprobaciones que Tiresias: tamaño, SHA-256, paquete y la misma firma).
	 */
	suspend fun descargarComplemento(contexto: Context, id: String, progreso: (Long, Long) -> Unit): Pair<ComplementoPublicado, File> {
		val v = versionPublicada(contexto)
		val c = v.complementos.firstOrNull { it.id == id } ?: throw IllegalStateException(textos(contexto).getString(R.string.act_sin_complemento))
		val comoVersion = v.copy(archivo = c.archivo, sha256 = c.sha256, tamano = c.tamano, alternativas = emptyList())
		return c to descargarDesde(contexto, comoVersion, comoVersion.url, progreso, paquete = c.paquete, exigirMasNueva = false)
	}

	/** ¿La versión publicada es más nueva que la instalada? */
	fun esNueva(v: VersionPublicada): Boolean =
		if (v.versionCode > 0) v.versionCode > BuildConfig.VERSION_CODE
		else compararVersiones(v.version, BuildConfig.VERSION_NAME) > 0

	/**
	 * Descarga y verifica el APK. progreso(bytes, total) se llama desde el hilo
	 * de fondo. Devuelve el archivo listo para instalar.
	 */
	suspend fun descargar(contexto: Context, v: VersionPublicada, progreso: (Long, Long) -> Unit): File {
		var fallo: Exception? = null
		for (url in listOf(v.url) + v.alternativas) {
			try {
				return descargarDesde(contexto, v, url, progreso)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "La descarga desde ${if (url.startsWith(GITHUB_WEB)) "GitHub" else "el servidor propio"} falló; se prueba la otra fuente si la hay", e)
				fallo = fallo ?: e
			}
		}
		throw fallo ?: IllegalStateException(textos(contexto).getString(R.string.act_no_guardada))
	}

	/** Descarga el APK de [v] desde [url], con reintentos y continuación, y lo verifica. */
	private suspend fun descargarDesde(
		contexto: Context, v: VersionPublicada, url: String, progreso: (Long, Long) -> Unit,
		paquete: String = contexto.packageName, exigirMasNueva: Boolean = true,
	): File =
		withContext(Dispatchers.IO) {
			val t = textos(contexto)
			val carpeta = carpeta(contexto)
			carpeta.listFiles()?.forEach { it.delete() }
			val destino = File(carpeta, v.archivo)
			val parcial = File(carpeta, v.archivo + ".parte")
			try {
				var leidos = 0L
				var total = v.tamano
				var intentos = 0
				// Si la conexión se corta (datos móviles, cambio de red...), se reintenta
				// y se continúa donde se quedó (petición Range) en lugar de empezar de cero
				while (true) {
					ensureActive()
					val c = conexion(url)
					val alEmpezar = leidos
					if (leidos > 0) c.setRequestProperty("Range", "bytes=$leidos-")
					try {
						val codigo = c.responseCode
						if (codigo == 200) {
							// El servidor manda el archivo entero (sin Range): se empieza de nuevo
							leidos = 0
							total = c.contentLengthLong.takeIf { it > 0 } ?: v.tamano
						} else if (codigo != 206 || leidos == 0L) {
							throw IllegalStateException(t.getString(R.string.act_servidor_respondio, codigo))
						}
						c.inputStream.use { entrada ->
							FileOutputStream(parcial, leidos > 0).use { salida ->
								val bufer = ByteArray(64 * 1024)
								while (true) {
									ensureActive()
									val n = entrada.read(bufer)
									if (n < 0) break
									salida.write(bufer, 0, n)
									leidos += n
									progreso(leidos, total)
								}
							}
						}
						if (total > 0 && leidos < total) throw IOException("Faltan ${total - leidos} bytes")
						break
					} catch (e: IOException) {
						Registro.w(ETIQUETA, "Descarga cortada en $leidos de $total bytes", e)
						if (leidos > alEmpezar) intentos = 0
						if (++intentos > REINTENTOS) throw IllegalStateException(t.getString(R.string.act_cortes))
						delay(2000L * minOf(intentos, 5))
					} finally {
						c.disconnect()
					}
				}
				if (v.tamano > 0 && parcial.length() != v.tamano) throw IllegalStateException(t.getString(R.string.act_incompleta))
				if (v.sha256.isNotEmpty() && sha256(parcial) != v.sha256) throw IllegalStateException(t.getString(R.string.act_sha_distinto))
				if (!parcial.renameTo(destino)) throw IllegalStateException(t.getString(R.string.act_no_guardada))
				comprobarApk(contexto, destino, paquete, exigirMasNueva)
				destino
			} catch (e: Exception) {
				parcial.delete()
				destino.delete()
				throw e
			}
		}

	private fun sha256(archivo: File): String {
		val resumen = MessageDigest.getInstance("SHA-256")
		archivo.inputStream().use { entrada ->
			val bufer = ByteArray(1024 * 1024)
			while (true) {
				val n = entrada.read(bufer)
				if (n < 0) break
				resumen.update(bufer, 0, n)
			}
		}
		return resumen.digest().joinToString("") { "%02x".format(it) }
	}

	/** El APK debe ser [paquete] (Tiresias o su complemento), más nuevo si se pide y con la misma firma que Tiresias. */
	private fun comprobarApk(contexto: Context, apk: File, paquete: String, exigirMasNueva: Boolean) {
		val t = textos(contexto)
		val pm = contexto.packageManager
		val banderas = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
		else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
		val nueva = pm.getPackageArchiveInfo(apk.absolutePath, banderas)
			?: throw IllegalStateException(t.getString(R.string.act_no_valida))
		if (nueva.packageName != paquete) throw IllegalStateException(t.getString(R.string.act_otra_app))
		val codigoNuevo = if (Build.VERSION.SDK_INT >= 28) nueva.longVersionCode else @Suppress("DEPRECATION") nueva.versionCode.toLong()
		if (exigirMasNueva && codigoNuevo <= BuildConfig.VERSION_CODE) throw IllegalStateException(t.getString(R.string.act_no_mas_nueva))
		val instalada = pm.getPackageInfo(contexto.packageName, banderas)
		val firmasNuevas = firmas(nueva)
		// Si el sistema no da las firmas del archivo, el instalador las comprobará igualmente
		if (firmasNuevas.isNotEmpty() && firmasNuevas.intersect(firmas(instalada)).isEmpty()) {
			throw IllegalStateException(t.getString(R.string.act_otra_firma))
		}
	}

	private fun firmas(info: PackageInfo): Set<String> {
		val lista = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.let {
			if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
		} else @Suppress("DEPRECATION") info.signatures
		return lista.orEmpty().map { firma ->
			MessageDigest.getInstance("SHA-256").digest(firma.toByteArray()).joinToString("") { "%02x".format(it) }
		}.toSet()
	}
}
