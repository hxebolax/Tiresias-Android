package com.xebolax.tiresias.ia

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * IA descargada dentro de Tiresias (como en Lector Universal y en el Tiresias
 * de Windows): modelos Gemma 4 de Google con licencia Apache 2.0, en formato
 * LiteRT-LM, que funcionan sin Internet.
 *
 * - En cualquier móvil de 64 bits con memoria suficiente: Gemma 4 E2B (unos
 *   2,6 GB, 6 GB de memoria o más) o E4B (unos 3,7 GB, 12 GB o más).
 * - En un Pixel con chip Tensor G5 (Pixel 10) o G6 (Pixel 11): la versión de
 *   Gemma 4 E2B preparada para su procesador de IA, la que usan los Pixel.
 *
 * El modelo se baja con el gestor de descargas del sistema (sigue aunque se
 * cierre Tiresias, se reanuda si se corta, opción de solo wifi) y se comprueba
 * su tamaño. Se ejecuta en [ServicioIALocal], en un proceso aparte: si Android lo
 * cierra por falta de memoria, el lector sigue hablando.
 */
object ModeloLocal {

	/** Un modelo del catálogo: archivo en Hugging Face (litert-community), tamaño y SHA-256 publicados. */
	data class Modelo(val id: String, val nombre: Int, val repo: String, val archivo: String, val tamano: Long, val sha256: String, val memoriaGb: Int, val chip: String? = null) {
		val url: String get() = "https://huggingface.co/litert-community/$repo/resolve/main/$archivo"
	}

	val CATALOGO = listOf(
		Modelo("e2b", R.string.modelo_e2b, "gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2_588_147_712L,
			"181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", 6),
		Modelo("e4b", R.string.modelo_e4b, "gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm", 3_659_530_240L,
			"0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", 12),
		Modelo("e2b_tensor_g5", R.string.modelo_tensor_g5, "gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it_Google_Tensor_G5.litertlm", 3_113_545_589L,
			"af1082986639ecde7db95d91be6fe54f8b6b458104734c5bafc204e69d6852dc", 8, chip = "G5"),
		Modelo("e2b_tensor_g6", R.string.modelo_tensor_g6, "gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it_Google_Tensor_G6.litertlm", 3_313_938_293L,
			"f86c7c19c736e9307267946edef58b0a45494a9127036d54d90dd6b7617c95b7", 8, chip = "G6"),
	)

	enum class Estado { NO_DESCARGADO, DESCARGANDO, DESCARGADO, NO_COMPATIBLE }

	private const val CLAVE_MODELO = "ia_modelo_local"
	private const val CLAVE_DESCARGA = "ia_descarga_modelo"
	const val CLAVE_GPU = "ia_local_gpu"

	/** El chip Tensor de un Pixel («G5», «G6»), o null si no es un Pixel con Tensor. */
	fun chipTensor(): String? {
		val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
		val modelo = Build.MODEL.orEmpty()
		return when {
			soc.contains("Tensor G6", true) || Regex("Pixel 11", RegexOption.IGNORE_CASE).containsMatchIn(modelo) -> "G6"
			soc.contains("Tensor G5", true) || Regex("Pixel 10", RegexOption.IGNORE_CASE).containsMatchIn(modelo) -> "G5"
			else -> null
		}
	}

	/** Los modelos que se ofrecen en este móvil (los de Tensor, solo en su Pixel). */
	fun disponibles(): List<Modelo> {
		val chip = chipTensor()
		return CATALOGO.filter { it.chip == null || it.chip == chip }
	}

	/** El modelo recomendado: el del chip del Pixel si lo hay; si no, E2B (o E4B con mucha memoria). */
	fun recomendado(c: Context): Modelo {
		chipTensor()?.let { chip -> CATALOGO.firstOrNull { it.chip == chip }?.let { return it } }
		return if (memoriaGb(c) >= 12) CATALOGO[1] else CATALOGO[0]
	}

	fun elegido(c: Context): Modelo {
		val id = Ajustes(c).p.getString(CLAVE_MODELO, null)
		return CATALOGO.firstOrNull { it.id == id } ?: recomendado(c)
	}

	fun elegir(c: Context, m: Modelo) = Ajustes(c).p.edit().putString(CLAVE_MODELO, m.id).apply()

	fun archivo(c: Context, m: Modelo = elegido(c)): File =
		File(c.getExternalFilesDir("modelos") ?: File(c.filesDir, "modelos"), m.archivo)

	/** La biblioteca de LiteRT-LM solo existe para 64 bits. */
	fun compatible() = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

	fun memoriaGb(c: Context): Double {
		val info = ActivityManager.MemoryInfo()
		(c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
		return info.totalMem / 1_073_741_824.0
	}

	private fun descargas(c: Context) = c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

	fun estado(c: Context): Estado {
		if (!compatible()) return Estado.NO_COMPATIBLE
		val p = Ajustes(c).p
		val id = p.getLong(CLAVE_DESCARGA, -1)
		if (id >= 0) {
			when (estadoDescarga(c, id)?.first) {
				DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> return Estado.DESCARGANDO
				else -> p.edit().remove(CLAVE_DESCARGA).apply()
			}
		}
		val m = elegido(c)
		val f = archivo(c, m)
		return if (f.exists() && f.length() == m.tamano) Estado.DESCARGADO else Estado.NO_DESCARGADO
	}

	private fun estadoDescarga(c: Context, id: Long): Pair<Int, Int>? =
		runCatching {
			descargas(c).query(DownloadManager.Query().setFilterById(id))?.use { cur ->
				if (!cur.moveToFirst()) return@use null
				val estado = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
				val bajado = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
				estado to (bajado * 100 / elegido(c).tamano).toInt()
			}
		}.getOrNull()

	fun porcentaje(c: Context): Int {
		val id = Ajustes(c).p.getLong(CLAVE_DESCARGA, -1)
		return if (id >= 0) estadoDescarga(c, id)?.second ?: 0 else 0
	}

	/** Empieza a bajar el modelo elegido. [soloWifi]: no gastar datos móviles. */
	fun descargar(c: Context, soloWifi: Boolean) {
		val m = elegido(c)
		val destino = archivo(c, m)
		destino.parentFile?.mkdirs()
		destino.delete()
		val t = Idioma.envolver(c)
		val peticion = DownloadManager.Request(Uri.parse(m.url))
			.setTitle(t.getString(R.string.ia_local_descarga_titulo, t.getString(m.nombre)))
			.setDescription(t.getString(R.string.app_name))
			.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
			.setDestinationUri(Uri.fromFile(destino))
			.setAllowedOverMetered(!soloWifi)
			.setAllowedOverRoaming(false)
		val id = descargas(c).enqueue(peticion)
		Ajustes(c).p.edit().putLong(CLAVE_DESCARGA, id).apply()
	}

	fun cancelar(c: Context) {
		val p = Ajustes(c).p
		val id = p.getLong(CLAVE_DESCARGA, -1)
		if (id >= 0) runCatching { descargas(c).remove(id) }
		p.edit().remove(CLAVE_DESCARGA).apply()
		archivo(c).delete()
	}

	fun borrar(c: Context) {
		pararServicio(c)
		archivo(c).delete()
	}

	/** Cierra el proceso de la IA (libera la memoria del modelo). */
	fun pararServicio(c: Context) = c.stopService(Intent(c, ServicioIALocal::class.java))

	// --- Preguntas al proceso de la IA ------------------------------------------------------

	private val hiloRespuestas = HandlerThread("tiresias-ia-respuestas").apply { start() }

	/**
	 * Pregunta al modelo descargado (bloquea hasta la respuesta o [ESPERA_MS]).
	 * Se conecta al servicio del proceso `:ia`, le manda la petición y espera.
	 */
	fun preguntar(c: Context, sistema: String, conversacion: List<AsistenteIA.Mensaje>, imagen: File?): String {
		val t = Idioma.envolver(c)
		if (estado(c) != Estado.DESCARGADO) throw AsistenteIA.ErrorIA(t.getString(R.string.ia_local_falta))
		val conectado = CountDownLatch(1)
		var mensajero: Messenger? = null
		val conexion = object : ServiceConnection {
			override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
				mensajero = Messenger(binder)
				conectado.countDown()
			}

			override fun onServiceDisconnected(name: ComponentName?) {
				mensajero = null
			}
		}
		val app = c.applicationContext
		if (!app.bindService(Intent(app, ServicioIALocal::class.java), conexion, Context.BIND_AUTO_CREATE)) {
			throw AsistenteIA.ErrorIA(t.getString(R.string.ia_local_error, "bind"))
		}
		try {
			if (!conectado.await(10, TimeUnit.SECONDS)) throw AsistenteIA.ErrorIA(t.getString(R.string.ia_local_error, "timeout"))
			val listo = CountDownLatch(1)
			var respuesta: String? = null
			var error: String? = null
			val receptor = Messenger(Handler(hiloRespuestas.looper) { m ->
				respuesta = m.data.getString(ServicioIALocal.RESPUESTA)
				error = m.data.getString(ServicioIALocal.ERROR)
				listo.countDown()
				true
			})
			val datos = Bundle().apply {
				putString(ServicioIALocal.SISTEMA, sistema)
				putStringArray(ServicioIALocal.TEXTOS, conversacion.map { it.texto }.toTypedArray())
				putBooleanArray(ServicioIALocal.DE_USUARIO, conversacion.map { it.deUsuario }.toBooleanArray())
				imagen?.let { putString(ServicioIALocal.IMAGEN, it.absolutePath) }
				putString(ServicioIALocal.MODELO, archivo(c).absolutePath)
				putBoolean(ServicioIALocal.GPU, Ajustes(c).p.getBoolean(CLAVE_GPU, false))
				putString(ServicioIALocal.TURNO_USUARIO, t.getString(R.string.ia_turno_usuario))
				putString(ServicioIALocal.TURNO_ASISTENTE, t.getString(R.string.ia_turno_asistente))
			}
			val m = Message.obtain(null, ServicioIALocal.PREGUNTAR).apply {
				data = datos
				replyTo = receptor
			}
			mensajero?.send(m) ?: throw AsistenteIA.ErrorIA(t.getString(R.string.ia_local_error, "send"))
			if (!listo.await(ESPERA_MS, TimeUnit.MILLISECONDS)) throw AsistenteIA.ErrorIA(t.getString(R.string.ia_local_lenta))
			error?.let { throw AsistenteIA.ErrorIA(if (it == ServicioIALocal.SIN_MEMORIA) t.getString(R.string.ia_local_memoria) else t.getString(R.string.ia_local_error, it)) }
			return respuesta.orEmpty()
		} catch (e: android.os.DeadObjectException) {
			// El proceso de la IA se cerró (casi siempre, por memoria): el lector sigue bien
			Registro.w(ETIQUETA, "El proceso de la IA se cerró", e)
			throw AsistenteIA.ErrorIA(t.getString(R.string.ia_local_memoria))
		} finally {
			runCatching { app.unbindService(conexion) }
		}
	}

	private const val ETIQUETA = "ModeloLocal"
	private const val ESPERA_MS = 180_000L
}
