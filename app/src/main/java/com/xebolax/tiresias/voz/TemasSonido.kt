package com.xebolax.tiresias.voz

import android.content.Context
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Los temas de sonidos (Backtalk 19, 22 y 212): carpetas con un archivo por
 * sonido, con su nombre en minúsculas (`foco.ogg`, `borde.wav`...), en WAV,
 * OGG o MP3.
 *
 * - Un tema se importa de un ZIP (del usuario o de la tienda) y se exporta en
 *   ZIP con los sonidos que suenan ahora, para compartirlo.
 * - Cada sonido puede tener además un archivo propio, que manda sobre el tema
 *   ([ponerPropio]); sin tema ni archivo propio suena el de Tiresias.
 *
 * Van en el almacén protegido del dispositivo, como los sonidos generados, para
 * que suenen también antes de desbloquear el móvil tras reiniciarlo.
 */
object TemasSonido {
	private val EXTENSIONES = listOf("ogg", "wav", "mp3")
	private const val PROPIOS = "_propios"
	private const val INCLUIDOS = "temas"
	private const val INCLUIDOS_SACADOS = "_incluidos"
	private const val MAXIMO_ARCHIVO = 2L * 1024 * 1024
	private const val MAXIMO_TEMA = 24L * 1024 * 1024

	fun carpeta(c: Context): File = File(Almacen.protegido(c).filesDir, "temas").apply { mkdirs() }

	private fun propios(c: Context): File = File(carpeta(c), PROPIOS)

	/** Los temas que vienen dentro de Tiresias (`assets/temas/<id>/`, los mismos de la tienda): no hace falta internet. */
	fun incluidos(c: Context): List<String> =
		runCatching { c.assets.list(INCLUIDOS).orEmpty().toList() }.getOrDefault(emptyList()).sorted()

	/** Los temas que se pueden elegir (los incluidos y los instalados, sin repetir), por orden alfabético. */
	fun instalados(c: Context): List<String> =
		(incluidos(c) + carpeta(c).listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith("_") && !it.name.endsWith(".parcial") }.map { it.name })
			.distinct().sorted()

	/** Si [tema] es de los que vienen dentro de Tiresias (no se borra: siempre se puede volver a elegir). */
	fun esIncluido(c: Context, tema: String): Boolean = tema in incluidos(c)

	/**
	 * El nombre que se enseña de [tema]: el de su `tema.json` en el idioma de
	 * Tiresias (`nombre_en`) o en español, o, si no tiene, el de su carpeta.
	 */
	fun nombreDe(c: Context, tema: String): String {
		val json = runCatching { c.assets.open("$INCLUIDOS/$tema/tema.json").bufferedReader().use { it.readText() } }.getOrNull()
			?: File(carpeta(c), "$tema/tema.json").takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
		val o = json?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: return tema
		val idioma = com.xebolax.tiresias.base.Idioma.efectivo(c).language
		return o.optString("nombre_$idioma").takeIf { idioma != "es" && it.isNotBlank() } ?: o.optString("nombre").takeIf { it.isNotBlank() } ?: tema
	}

	/** El archivo que suena para [s]: el propio, el del [tema] o null (el generado de Tiresias). En un hilo de fondo. */
	fun archivoDe(c: Context, tema: String?, s: Sonido): File? {
		propio(c, s)?.let { return it }
		if (tema.isNullOrEmpty()) return null
		// Uno instalado (de la tienda o importado) o, si no, el incluido en Tiresias
		buscar(File(carpeta(c), tema), s)?.let { return it }
		if (!esIncluido(c, tema)) return null
		return buscar(sacarIncluido(c, tema), s)
	}

	/**
	 * Copia un tema incluido de los assets a su carpeta (SoundPool necesita un
	 * archivo), una vez por instalación de Tiresias (versión y hora de instalación:
	 * reinstalar la misma versión también trae sus sonidos nuevos). Las copias
	 * anteriores se borran.
	 */
	@Synchronized
	private fun sacarIncluido(c: Context, tema: String): File {
		val base = File(carpeta(c), INCLUIDOS_SACADOS)
		val instalada = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).lastUpdateTime }.getOrDefault(0L)
		val version = File(base, "${com.xebolax.tiresias.BuildConfig.VERSION_CODE}-$instalada")
		val destino = File(version, tema)
		if (destino.isDirectory) return destino
		base.listFiles().orEmpty().filter { it != version }.forEach { it.deleteRecursively() }
		val parcial = File(version, "$tema.parcial").apply { deleteRecursively(); mkdirs() }
		try {
			for (archivo in c.assets.list("$INCLUIDOS/$tema").orEmpty()) {
				c.assets.open("$INCLUIDOS/$tema/$archivo").use { e -> File(parcial, archivo).outputStream().use { e.copyTo(it) } }
			}
			if (!parcial.renameTo(destino)) throw IOException("No se pudo guardar el tema $tema")
		} catch (e: IOException) {
			parcial.deleteRecursively()
			Registro.w("TemasSonido", "No se pudo sacar el tema incluido $tema", e)
		}
		return destino
	}

	fun propio(c: Context, s: Sonido): File? = buscar(propios(c), s)

	private fun buscar(d: File, s: Sonido): File? =
		EXTENSIONES.map { File(d, s.name.lowercase() + "." + it) }.firstOrNull { it.isFile && it.length() > 0 }

	/** Copia [entrada] (un audio de extensión [extension]) como el archivo propio de [s]. */
	fun ponerPropio(c: Context, s: Sonido, entrada: InputStream, extension: String) {
		val ext = extension.lowercase().takeIf { it in EXTENSIONES } ?: throw IOException("Formato no admitido: $extension")
		val d = propios(c).apply { mkdirs() }
		quitarPropio(c, s)
		copiarLimitado(entrada, File(d, s.name.lowercase() + "." + ext), MAXIMO_ARCHIVO)
	}

	fun quitarPropio(c: Context, s: Sonido) {
		for (ext in EXTENSIONES) File(propios(c), s.name.lowercase() + "." + ext).delete()
	}

	/**
	 * Instala el tema de un ZIP con el nombre [nombre] (se limpia: solo letras,
	 * números y guiones). Solo se sacan los archivos con el nombre de un sonido y
	 * una extensión de audio, sin carpetas ni rutas. Devuelve el nombre del tema.
	 */
	fun importarZip(c: Context, entrada: InputStream, nombre: String): String {
		val limpio = nombre.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9áéíóúñü_-]+"), "-").trim('-').take(40).ifEmpty { "tema" }
			// Un ZIP con el nombre de un tema incluido no lo tapa: se guarda aparte
			.let { if (it in incluidos(c)) "$it-propio" else it }
		val destino = File(carpeta(c), limpio)
		val parcial = File(carpeta(c), "$limpio.parcial").apply { deleteRecursively(); mkdirs() }
		val nombres = Sonido.entries.map { it.name.lowercase() }.toSet()
		var total = 0L
		var sacados = 0
		ZipInputStream(entrada.buffered()).use { zip ->
			while (true) {
				val e = zip.nextEntry ?: break
				if (e.isDirectory) continue
				val archivo = e.name.substringAfterLast('/').lowercase()
				val base = archivo.substringBeforeLast('.')
				val ext = archivo.substringAfterLast('.', "")
				if (base !in nombres || ext !in EXTENSIONES) continue
				total += copiarLimitado(zip, File(parcial, archivo), MAXIMO_ARCHIVO)
				if (total > MAXIMO_TEMA) throw IOException("El tema es demasiado grande")
				sacados++
			}
		}
		if (sacados == 0) {
			parcial.deleteRecursively()
			throw IOException("El ZIP no tiene ningún sonido de Tiresias")
		}
		destino.deleteRecursively()
		if (!parcial.renameTo(destino)) throw IOException("No se pudo guardar el tema")
		return limpio
	}

	/** Escribe en [salida] un ZIP con el archivo de cada sonido que suena ahora ([efectivo]). */
	fun exportarZip(salida: OutputStream, efectivo: (Sonido) -> File?) {
		ZipOutputStream(salida.buffered()).use { zip ->
			for (s in Sonido.entries) {
				val f = efectivo(s) ?: continue
				zip.putNextEntry(ZipEntry(s.name.lowercase() + "." + f.extension.lowercase()))
				f.inputStream().use { it.copyTo(zip) }
				zip.closeEntry()
			}
		}
	}

	fun borrar(c: Context, tema: String) {
		if (tema.isNotEmpty() && !tema.startsWith("_")) File(carpeta(c), tema).deleteRecursively()
	}

	private fun copiarLimitado(entrada: InputStream, destino: File, maximo: Long): Long {
		var total = 0L
		destino.outputStream().use { o ->
			val b = ByteArray(16 * 1024)
			while (true) {
				val n = entrada.read(b)
				if (n < 0) break
				total += n
				if (total > maximo) {
					o.close()
					destino.delete()
					throw IOException("Un sonido del tema es demasiado grande")
				}
				o.write(b, 0, n)
			}
		}
		return total
	}
}
