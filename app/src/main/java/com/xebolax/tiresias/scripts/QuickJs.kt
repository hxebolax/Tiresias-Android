/*
 * Adaptado de QuickJs.kt y QuickJsException.kt de Backtalk
 * (https://github.com/trypsynth/backtalk), Copyright 2026 Backtalk contributors,
 * con licencia Apache 2.0. Cambios para Tiresias: paquete, nombres en español y
 * biblioteca nativa propia (tiresiasquickjs, con quickjs-ng 0.17.0, MIT).
 */
package com.xebolax.tiresias.scripts

import java.io.Closeable

/** Un error de un script (o del motor): mensaje, traza de JavaScript y si fue por pasarse de tiempo. */
class QuickJsException @JvmOverloads constructor(
	message: String,
	val pila: String? = null,
	val interrumpido: Boolean = false,
) : Exception(message)

/**
 * Un motor QuickJS aislado: su propia memoria (con límite), su pila y un límite
 * de tiempo en cada llamada. El script solo habla con Tiresias por `__host(método, json)`,
 * que llega a [Host.call]: no ve nada de Android ni de Java.
 */
class QuickJs(host: Host, limiteMemoria: Long, limitePila: Long) : Closeable {

	interface Host {
		fun call(method: String, json: String?): String?
		fun loadModule(name: String): ByteArray?
		fun onUnhandledError(error: QuickJsException)
	}

	private var ptr: Long = nativeCreate(host, limiteMemoria, limitePila)

	init {
		if (ptr == 0L) throw QuickJsException("No se pudo arrancar el motor de scripts")
	}

	val cerrado: Boolean get() = ptr == 0L

	/** Carga un módulo; devuelve su `manifest` exportado, en JSON. */
	@Throws(QuickJsException::class)
	fun evaluarModulo(codigo: String, nombre: String, limiteMs: Long): String? = nativeEval(abierto(), codigo.toByteArray(), nombre, true, limiteMs)

	@Throws(QuickJsException::class)
	fun evaluar(codigo: String, nombre: String, limiteMs: Long) {
		nativeEval(abierto(), codigo.toByteArray(), nombre, false, limiteMs)
	}

	/** Llama a la función global [funcion] con [argumento] (JSON) y devuelve su resultado en JSON. */
	@Throws(QuickJsException::class)
	fun llamar(funcion: String, argumento: String?, limiteMs: Long): String? = nativeCall(abierto(), funcion, argumento, limiteMs)

	override fun close() {
		if (ptr != 0L) {
			nativeDestroy(ptr)
			ptr = 0L
		}
	}

	private fun abierto(): Long = ptr.takeIf { it != 0L } ?: throw QuickJsException("El script ya no está cargado")

	private companion object {
		init {
			System.loadLibrary("tiresiasquickjs")
		}

		@JvmStatic external fun nativeCreate(host: Host, memoryLimit: Long, stackSize: Long): Long
		@JvmStatic external fun nativeDestroy(ptr: Long)
		@JvmStatic external fun nativeEval(ptr: Long, source: ByteArray, fileName: String, isModule: Boolean, timeLimitMs: Long): String?
		@JvmStatic external fun nativeCall(ptr: Long, function: String, argument: String?, timeLimitMs: Long): String?
	}
}
