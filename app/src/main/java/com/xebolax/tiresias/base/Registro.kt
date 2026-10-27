package com.xebolax.tiresias.base

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Registro técnico local de fallos, para diagnosticar sin datos privados.
 *
 * Nunca se escribe aquí texto leído de otras aplicaciones ni lo que dice la voz:
 * solo qué componente falló, cómo y con qué excepción. Se guarda en el
 * almacenamiento protegido del dispositivo (funciona antes de desbloquear tras
 * reiniciar) y se recorta solo para no crecer sin límite. El usuario lo puede
 * compartir desde Ajustes si quiere enviar un informe.
 *
 * El archivo se escribe en un hilo propio de baja prioridad: registrar nunca
 * retrasa al hilo del lector ni al principal (Backtalk 248 y 250).
 */
object Registro {
	private const val ARCHIVO = "registro.txt"
	private const val ANTERIOR = "registro.1.txt"
	private const val MAXIMO = 256 * 1024L
	private var carpeta: File? = null
	private val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
	private val hilo = Executors.newSingleThreadExecutor { r ->
		Thread(r, "tiresias-registro").apply {
			isDaemon = true
			priority = Thread.MIN_PRIORITY
		}
	}

	/** Se llama una vez al arrancar el proceso (App.onCreate). */
	fun iniciar(contexto: Context) {
		carpeta = Almacen.protegido(contexto).filesDir
	}

	fun i(etiqueta: String, mensaje: String) {
		Log.i(etiqueta, mensaje)
		escribir("I", etiqueta, mensaje, null)
	}

	fun w(etiqueta: String, mensaje: String, error: Throwable? = null) {
		Log.w(etiqueta, mensaje, error)
		escribir("W", etiqueta, mensaje, error)
	}

	fun e(etiqueta: String, mensaje: String, error: Throwable? = null) {
		Log.e(etiqueta, mensaje, error)
		escribir("E", etiqueta, mensaje, error)
	}

	/** El archivo del registro (para compartirlo), o null si aún no hay nada. */
	fun archivo(): File? = carpeta?.let { File(it, ARCHIVO) }?.takeIf { it.exists() }

	/** Todo el registro como texto: la parte anterior y la actual. */
	fun texto(): String {
		val dir = carpeta ?: return ""
		return listOf(ANTERIOR, ARCHIVO).map { File(dir, it) }.filter { it.exists() }
			.joinToString("") { runCatching { it.readText() }.getOrDefault("") }
	}

	fun borrar() {
		val dir = carpeta ?: return
		File(dir, ARCHIVO).delete()
		File(dir, ANTERIOR).delete()
	}

	private fun escribir(nivel: String, etiqueta: String, mensaje: String, error: Throwable?) {
		val dir = carpeta ?: return
		val momento = Date()
		runCatching { hilo.execute { guardar(dir, momento, nivel, etiqueta, mensaje, error) } }
			.onFailure { Log.w("Registro", "No se pudo pasar al hilo del registro", it) }
	}

	@Synchronized
	private fun guardar(dir: File, momento: Date, nivel: String, etiqueta: String, mensaje: String, error: Throwable?) {
		try {
			val archivo = File(dir, ARCHIVO)
			if (archivo.length() > MAXIMO) {
				val anterior = File(dir, ANTERIOR)
				anterior.delete()
				archivo.renameTo(anterior)
			}
			val linea = StringBuilder()
				.append(formato.format(momento)).append(' ').append(nivel).append(' ')
				.append(etiqueta).append(": ").append(mensaje).append('\n')
			if (error != null) {
				val traza = StringWriter()
				error.printStackTrace(PrintWriter(traza))
				linea.append(traza).append('\n')
			}
			archivo.appendText(linea.toString())
		} catch (fallo: Exception) {
			// Sin registro en disco: queda al menos en logcat
			Log.w("Registro", "No se pudo escribir el registro", fallo)
		}
	}

	/** Cabecera del informe: modelo, versión de Android y de la app. Sin datos personales. */
	fun cabecera(version: String): String =
		"Tiresias $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n"
}
