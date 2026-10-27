package com.xebolax.tiresias.ia

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.xebolax.tiresias.base.Registro
import java.io.File

/**
 * El modelo descargado, en su propio proceso (`android:process=":ia"`).
 *
 * Un modelo de varios gigas no puede compartir proceso con el lector: si
 * Android se queda sin memoria, mata este proceso y no el de Tiresias, que
 * dice que no había memoria suficiente y sigue funcionando. Atiende las
 * preguntas de una en una en su hilo y mantiene el modelo cargado mientras haya
 * alguien conectado.
 */
class ServicioIALocal : Service() {
	private val hilo = HandlerThread("tiresias-ia", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
	private var motor: com.google.ai.edge.litertlm.Engine? = null
	private var rutaCargada = ""
	private var gpuCargada = false

	private val manejador = Handler(hilo.looper) { m ->
		if (m.what == PREGUNTAR) responder(m.data, m.replyTo)
		true
	}
	private val mensajero = Messenger(manejador)

	override fun onBind(intent: Intent?): IBinder = mensajero.binder

	override fun onDestroy() {
		super.onDestroy()
		cerrar()
		hilo.quitSafely()
	}

	private fun cerrar() {
		runCatching { motor?.close() }
		motor = null
		rutaCargada = ""
	}

	private fun cargar(ruta: String, gpu: Boolean): com.google.ai.edge.litertlm.Engine {
		motor?.let { if (ruta == rutaCargada && gpu == gpuCargada) return it else cerrar() }
		val cache = File(cacheDir, "litertlm").apply { mkdirs() }.absolutePath
		fun crear(conGpu: Boolean): com.google.ai.edge.litertlm.Engine {
			val backend = if (conGpu) com.google.ai.edge.litertlm.Backend.GPU() else com.google.ai.edge.litertlm.Backend.CPU()
			val config = com.google.ai.edge.litertlm.EngineConfig(
				modelPath = ruta,
				backend = backend,
				visionBackend = backend,
				maxNumTokens = 8192,
				maxNumImages = 1,
				cacheDir = cache,
			)
			return com.google.ai.edge.litertlm.Engine(config).also { it.initialize() }
		}
		// La tarjeta gráfica es más rápida pero su controlador falla en algunos móviles: si falla, procesador
		val m = if (!gpu) crear(false) else try {
			crear(true)
		} catch (e: Throwable) {
			if (e is OutOfMemoryError) throw e
			Registro.w(ETIQUETA, "La IA no pudo usar la tarjeta gráfica; se usa el procesador", e)
			crear(false)
		}
		motor = m
		rutaCargada = ruta
		gpuCargada = gpu
		return m
	}

	private fun responder(datos: Bundle, destino: Messenger?) {
		val r = Bundle()
		try {
			val m = cargar(datos.getString(MODELO).orEmpty(), datos.getBoolean(GPU))
			val textos = datos.getStringArray(TEXTOS).orEmpty()
			val deUsuario = datos.getBooleanArray(DE_USUARIO) ?: BooleanArray(textos.size) { true }
			val usuario = datos.getString(TURNO_USUARIO).orEmpty()
			val asistente = datos.getString(TURNO_ASISTENTE).orEmpty()
			val texto = buildString {
				appendLine(datos.getString(SISTEMA).orEmpty())
				appendLine()
				textos.forEachIndexed { i, t ->
					append(if (deUsuario.getOrElse(i) { true }) usuario else asistente).append(": ").appendLine(t)
				}
			}
			val partes = mutableListOf<com.google.ai.edge.litertlm.Content>()
			datos.getString(IMAGEN)?.let { partes += com.google.ai.edge.litertlm.Content.ImageFile(it) }
			partes += com.google.ai.edge.litertlm.Content.Text(texto)
			val respuesta = m.createConversation().use { conversacion ->
				val res = conversacion.sendMessage(com.google.ai.edge.litertlm.Contents.of(partes))
				res.contents.contents.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>().joinToString("") { it.text }
			}
			r.putString(RESPUESTA, respuesta)
		} catch (e: OutOfMemoryError) {
			cerrar()
			r.putString(ERROR, SIN_MEMORIA)
		} catch (e: Throwable) {
			Registro.e(ETIQUETA, "La IA descargada falló", e)
			cerrar()
			r.putString(ERROR, e.message ?: e.javaClass.simpleName)
		}
		runCatching { destino?.send(Message.obtain().apply { data = r }) }
	}

	companion object {
		private const val ETIQUETA = "ServicioIALocal"
		const val PREGUNTAR = 1
		const val SISTEMA = "sistema"
		const val TEXTOS = "textos"
		const val DE_USUARIO = "de_usuario"
		const val IMAGEN = "imagen"
		const val MODELO = "modelo"
		const val GPU = "gpu"
		const val TURNO_USUARIO = "turno_usuario"
		const val TURNO_ASISTENTE = "turno_asistente"
		const val RESPUESTA = "respuesta"
		const val ERROR = "error"
		const val SIN_MEMORIA = "sin_memoria"
	}
}
