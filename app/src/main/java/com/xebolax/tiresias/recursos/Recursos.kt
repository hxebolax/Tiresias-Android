package com.xebolax.tiresias.recursos

import android.content.Context
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Registro
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Recursos que no van en el APK y se descargan cuando el usuario los pide:
 * modelos de reconocimiento de texto (OCR), voces neuronales y el modelo de
 * reconocimiento de voz. Nunca se descarga nada solo.
 *
 * Cada archivo tiene su dirección, su tamaño y su SHA-256 publicados; si no
 * coinciden, se borra. Las descargas se reanudan si se cortan (petición Range,
 * como el actualizador). Se guardan en `files/recursos/<id>/`.
 */
object Recursos {

	data class Archivo(val url: String, val nombre: String, val tamano: Long, val sha256: String, val descomprimir: Boolean = false)

	data class Recurso(val id: String, val nombre: Int, val descripcion: Int, val archivos: List<Archivo>, val licencia: String) {
		val tamano: Long get() = archivos.sumOf { it.tamano }
	}

	private const val HF = "https://huggingface.co"

	/** Reconocimiento de texto: PP-OCRv5 de PaddlePaddle en ONNX (Apache 2.0), detección y alfabeto latino. */
	val OCR = Recurso(
		"ocr_latino", R.string.recurso_ocr, R.string.recurso_ocr_desc,
		listOf(
			Archivo("$HF/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx", "det.onnx", 4_826_518,
				"a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d"),
			Archivo("$HF/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx", "rec.onnx", 8_042_023,
				"7888113072263cb471b93f66dd5e2ad70548dc526fa1ace760d0d973dd121498"),
			// La configuración trae el diccionario de caracteres; no se comprueba su huella (no es LFS)
			Archivo("$HF/PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml", "rec.yml", 0, ""),
		),
		"Apache-2.0 (PaddlePaddle)",
	)

	/** Reconocimiento de voz sin conexión en español: Vosk, modelo pequeño (Apache 2.0). */
	val VOSK_ES = Recurso(
		"vosk_es", R.string.recurso_vosk, R.string.recurso_vosk_desc,
		listOf(Archivo("https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip", "modelo.zip", 0, "", descomprimir = true)),
		"Apache-2.0 (Alpha Cephei)",
	)

	val TODOS: List<Recurso> get() = listOf(OCR, VOSK_ES) + VocesNeuronales.CATALOGO.map { it.recurso }

	fun carpeta(c: Context, r: Recurso): File = File(c.filesDir, "recursos/${r.id}")

	/** Si el recurso está entero en el móvil. */
	fun instalado(c: Context, r: Recurso): Boolean {
		val d = carpeta(c, r)
		if (!File(d, ".completo").isFile) return false
		return r.archivos.all { a -> if (a.descomprimir) true else File(d, a.nombre).let { it.isFile && (a.tamano == 0L || it.length() == a.tamano) } }
	}

	fun borrar(c: Context, r: Recurso) {
		carpeta(c, r).deleteRecursively()
	}

	/**
	 * Descarga e instala [r]. Bloquea: llámese fuera del hilo principal.
	 * [progreso] recibe los bytes bajados y el total (0 si no se sabe). [sigue]
	 * se consulta a menudo: si devuelve false, se cancela.
	 */
	fun instalar(c: Context, r: Recurso, progreso: (Long, Long) -> Unit, sigue: () -> Boolean) {
		val d = carpeta(c, r).apply { mkdirs() }
		File(d, ".completo").delete()
		var base = 0L
		for (a in r.archivos) {
			val destino = File(d, a.nombre)
			descargar(a, destino, { n, t -> progreso(base + n, r.tamano.takeIf { it > 0 } ?: t) }, sigue)
			base += destino.length()
			if (a.descomprimir) {
				descomprimir(destino, d, sigue)
				destino.delete()
			}
		}
		File(d, ".completo").writeText(r.id)
	}

	private fun descargar(a: Archivo, destino: File, progreso: (Long, Long) -> Unit, sigue: () -> Boolean) {
		val parcial = File(destino.path + ".parte")
		if (destino.isFile && (a.tamano == 0L || destino.length() == a.tamano) && (a.sha256.isEmpty() || sha256(destino) == a.sha256)) return
		var leidos = if (parcial.isFile) parcial.length() else 0L
		var intentos = 0
		var url = a.url
		while (true) {
			if (!sigue()) throw InterruptedException()
			val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
				connectTimeout = 20_000
				readTimeout = 30_000
				instanceFollowRedirects = false
				setRequestProperty("User-Agent", "Tiresias-Recursos")
				if (leidos > 0) setRequestProperty("Range", "bytes=$leidos-")
			}
			try {
				val codigo = conexion.responseCode
				// Hugging Face redirige a su almacén: se sigue a mano (cambia de dominio)
				if (codigo in 300..399) {
					url = conexion.getHeaderField("Location")?.let { URL(URL(url), it).toString() } ?: throw IOException("Redirección sin destino")
					continue
				}
				if (codigo == 200) leidos = 0 else if (codigo != 206) throw IOException("El servidor respondió $codigo")
				val total = if (codigo == 206) leidos + conexion.contentLengthLong else conexion.contentLengthLong
				conexion.inputStream.use { entrada ->
					FileOutputStream(parcial, leidos > 0).use { salida ->
						val bufer = ByteArray(128 * 1024)
						while (true) {
							if (!sigue()) throw InterruptedException()
							val n = entrada.read(bufer)
							if (n < 0) break
							salida.write(bufer, 0, n)
							leidos += n
							progreso(leidos, total)
						}
					}
				}
				break
			} catch (e: IOException) {
				Registro.w(ETIQUETA, "Descarga cortada de ${a.nombre} en $leidos bytes", e)
				if (++intentos > 6) throw e
				Thread.sleep(2000L * intentos)
			} finally {
				conexion.disconnect()
			}
		}
		if (a.tamano > 0 && parcial.length() != a.tamano) {
			parcial.delete()
			throw IOException("Tamaño incorrecto de ${a.nombre}")
		}
		if (a.sha256.isNotEmpty() && sha256(parcial) != a.sha256) {
			parcial.delete()
			throw IOException("Huella SHA-256 incorrecta de ${a.nombre}")
		}
		destino.delete()
		if (!parcial.renameTo(destino)) throw IOException("No se pudo guardar ${a.nombre}")
	}

	private fun descomprimir(zip: File, destino: File, sigue: () -> Boolean) {
		val raiz = destino.canonicalPath
		ZipInputStream(zip.inputStream().buffered()).use { z ->
			while (true) {
				val e = z.nextEntry ?: break
				if (!sigue()) throw InterruptedException()
				// Los modelos de Vosk vienen en una carpeta: se quita ese primer nivel
				val ruta = e.name.substringAfter('/', e.name)
				if (ruta.isEmpty()) continue
				val f = File(destino, ruta)
				// Nada fuera de la carpeta del recurso (zip con «../»)
				if (!f.canonicalPath.startsWith(raiz)) throw IOException("Ruta no válida en el zip: ${e.name}")
				if (e.isDirectory) f.mkdirs() else {
					f.parentFile?.mkdirs()
					f.outputStream().use { z.copyTo(it) }
				}
			}
		}
	}

	fun sha256(f: File): String {
		val md = MessageDigest.getInstance("SHA-256")
		f.inputStream().use { entrada ->
			val b = ByteArray(1024 * 1024)
			while (true) {
				val n = entrada.read(b)
				if (n < 0) break
				md.update(b, 0, n)
			}
		}
		return md.digest().joinToString("") { "%02x".format(it) }
	}

	private const val ETIQUETA = "Recursos"
}
