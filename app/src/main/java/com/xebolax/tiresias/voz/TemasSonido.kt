package com.xebolax.tiresias.voz

import android.content.Context
import com.xebolax.tiresias.base.Almacen
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
	private const val MAXIMO_ARCHIVO = 2L * 1024 * 1024
	private const val MAXIMO_TEMA = 24L * 1024 * 1024

	fun carpeta(c: Context): File = File(Almacen.protegido(c).filesDir, "temas").apply { mkdirs() }

	private fun propios(c: Context): File = File(carpeta(c), PROPIOS)

	/** Los temas instalados (el nombre de su carpeta), por orden alfabético. */
	fun instalados(c: Context): List<String> =
		carpeta(c).listFiles().orEmpty().filter { it.isDirectory && it.name != PROPIOS }.map { it.name }.sorted()

	/** El archivo que suena para [s]: el propio, el del [tema] o null (el generado de Tiresias). */
	fun archivoDe(c: Context, tema: String?, s: Sonido): File? {
		propio(c, s)?.let { return it }
		if (tema.isNullOrEmpty()) return null
		return buscar(File(carpeta(c), tema), s)
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
		if (tema.isNotEmpty() && tema != PROPIOS) File(carpeta(c), tema).deleteRecursively()
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
