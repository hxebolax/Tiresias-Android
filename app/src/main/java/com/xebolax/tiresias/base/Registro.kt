package com.xebolax.tiresias.base

import android.content.Context
import android.os.Build
import android.util.Log
import com.xebolax.tiresias.nucleo.LimpiezaRegistro
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Registro técnico local de fallos, para diagnosticar sin datos privados.
 *
 * Nunca se escribe aquí texto leído de otras aplicaciones ni lo que dice la voz:
 * solo qué componente falló, cómo y con qué excepción. Y como la gente lo
 * comparte en público, todo pasa por [LimpiezaRegistro] al escribir (en el
 * archivo y en logcat) y otra vez al compartir: claves, texto entre comillas,
 * correos, direcciones y las líneas de más de un mensaje de excepción nunca
 * llegan (un usuario compartió así su clave de Gemini y un trozo de su chat). Se guarda en el
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
		val dir = Almacen.protegido(contexto).filesDir
		carpeta = dir
		runCatching { hilo.execute { limpiarGuardado(dir) } }.onFailure { Log.w("Registro", "No se pudo pasar al hilo del registro", it) }
	}

	/**
	 * Limpia lo que ya está guardado de versiones anteriores, que no limpiaban al escribir: las claves y las
	 * líneas de más de los mensajes de excepción (el trozo de chat de la 1.0.10), y las líneas de un diagnóstico
	 * de pruebas de la 1.0.6 (apuntaban los identificadores de lo que se exploraba). Así no quedan ni en el
	 * archivo. Solo se reescribe si cambia algo.
	 */
	@Synchronized
	private fun limpiarGuardado(dir: File) {
		for (nombre in listOf(ARCHIVO, ANTERIOR)) {
			val f = File(dir, nombre)
			try {
				if (!f.exists()) continue
				val antes = f.readText()
				val sinDiagnostico = antes.lineSequence().filterNot { it.contains(" Diagnostico: ") }.joinToString("\n")
				val limpio = LimpiezaRegistro.compartible(sinDiagnostico).trimEnd('\n') + "\n"
				if (limpio != antes) f.writeText(limpio)
			} catch (e: Exception) {
				Log.w("Registro", "No se pudo limpiar el registro", e)
			}
		}
	}

	fun i(etiqueta: String, mensaje: String) {
		val limpio = LimpiezaRegistro.mensaje(mensaje)
		Log.i(etiqueta, limpio)
		escribir("I", etiqueta, limpio, null)
	}

	fun w(etiqueta: String, mensaje: String, error: Throwable? = null) {
		val limpio = LimpiezaRegistro.mensaje(mensaje)
		val traza = error?.let { trazaLimpia(it) }
		Log.w(etiqueta, if (traza == null) limpio else "$limpio\n$traza")
		escribir("W", etiqueta, limpio, traza)
	}

	fun e(etiqueta: String, mensaje: String, error: Throwable? = null) {
		val limpio = LimpiezaRegistro.mensaje(mensaje)
		val traza = error?.let { trazaLimpia(it) }
		Log.e(etiqueta, if (traza == null) limpio else "$limpio\n$traza")
		escribir("E", etiqueta, limpio, traza)
	}

	/** La pila de [error] sin datos: tipos, primeras líneas limpias de los mensajes y líneas de código. */
	private fun trazaLimpia(error: Throwable): String = try {
		LimpiezaRegistro.traza(error)
	} catch (fallo: Exception) {
		// (una excepción rara que no se deja recorrer: al menos su tipo)
		Log.w("Registro", "No se pudo limpiar una traza", fallo)
		error.javaClass.name
	}

	/** Un trozo de registro limpio para compartirlo (lo mismo que hace [texto] con el archivo entero). */
	fun limpiar(t: String): String = LimpiezaRegistro.compartible(t)

	/** El archivo del registro (para compartirlo), o null si aún no hay nada. */
	fun archivo(): File? = carpeta?.let { File(it, ARCHIVO) }?.takeIf { it.exists() }

	/**
	 * Todo el registro como texto, listo para compartirlo: la parte anterior y la actual, limpias otra vez línea a
	 * línea y sin lo que no tiene forma de registro. Así también se limpia lo que escribieron las versiones de antes
	 * (que guardaban enteros los mensajes de las excepciones, con sus líneas de más).
	 */
	fun texto(): String {
		val dir = carpeta ?: return ""
		return LimpiezaRegistro.compartible(listOf(ANTERIOR, ARCHIVO).map { File(dir, it) }.filter { it.exists() }
			.joinToString("") { runCatching { it.readText() }.getOrDefault("") })
	}

	fun borrar() {
		val dir = carpeta ?: return
		File(dir, ARCHIVO).delete()
		File(dir, ANTERIOR).delete()
	}

	/** Espera a que quede escrito lo pendiente, [ms] como mucho (antes de reiniciar el proceso). */
	fun vaciar(ms: Long) {
		runCatching { hilo.submit { }.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS) }
			.onFailure { Log.w("Registro", "No se pudo esperar al hilo del registro", it) }
	}

	private fun escribir(nivel: String, etiqueta: String, mensaje: String, traza: String?) {
		val dir = carpeta ?: return
		val momento = Date()
		runCatching { hilo.execute { guardar(dir, momento, nivel, etiqueta, mensaje, traza) } }
			.onFailure { Log.w("Registro", "No se pudo pasar al hilo del registro", it) }
	}

	@Synchronized
	private fun guardar(dir: File, momento: Date, nivel: String, etiqueta: String, mensaje: String, traza: String?) {
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
			if (traza != null) linea.append(traza).append('\n')
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
