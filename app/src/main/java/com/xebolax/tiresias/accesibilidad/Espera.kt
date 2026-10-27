package com.xebolax.tiresias.accesibilidad

import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xebolax.tiresias.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Ventana de espera para todo lo que tarda (red, importar, sincronizar,
 * reconocer texto, consultar un servicio...): así se sabe que la app está
 * trabajando y la respuesta no llega más tarde en otra pantalla. TalkBack lee
 * su título y su mensaje al aparecer; Cancelar (o Atrás) la cierra en el acto.
 * Si el trabajo termina en menos de medio segundo no llega a mostrarse. Al
 * cerrarse, el foco vuelve a donde estaba (MemoriaFoco).
 *
 * Devuelve lo que devuelva el trabajo (y sus errores se lanzan aquí); si se
 * cancela, lanza CancellationException y la corrutina que la llama se para.
 * Quien la llame debe relanzar CancellationException ANTES de cualquier
 * catch genérico:
 *
 *     lifecycleScope.launch {
 *         try {
 *             val datos = conEspera(getString(R.string.buscando)) { repositorio.buscar(texto) }
 *             mostrar(datos)                       // en esta misma pantalla
 *         } catch (e: CancellationException) {
 *             throw e
 *         } catch (e: Exception) {
 *             Log.e("Buscar", "Falló la búsqueda", e)
 *             mensaje(getString(R.string.error_titulo), explicar(e))  // lenguaje claro, nunca la excepción
 *         }
 *     }
 *
 * Cadenas: espera_un_momento, cancelar, cancelado. Usa dp() y avisar() (Utilidades.kt).
 */
suspend fun <T> AppCompatActivity.conEspera(titulo: String, mensaje: String = getString(R.string.espera_un_momento), trabajo: suspend () -> T): T {
	val tarea = lifecycleScope.async { trabajo() }
	val cancelar = CompletableDeferred<Unit>()
	var dialogo: AlertDialog? = null
	val mostrar = lifecycleScope.launch {
		delay(500)
		val m = dp(this@conEspera, 24)
		val vista = LinearLayout(this@conEspera).apply {
			orientation = LinearLayout.HORIZONTAL
			setPadding(m, m / 2, m, 0)
			// La rueda es solo visual: el mensaje ya dice qué pasa
			addView(ProgressBar(this@conEspera).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
			addView(TextView(this@conEspera).apply {
				text = mensaje
				setPadding(m / 2, 0, 0, 0)
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
			})
		}
		dialogo = MaterialAlertDialogBuilder(this@conEspera)
			.setTitle(titulo)
			.setView(vista)
			.setNegativeButton(R.string.cancelar) { _, _ -> cancelar.complete(Unit) }
			.setOnCancelListener { cancelar.complete(Unit) }
			.show()
	}
	try {
		return select {
			tarea.onAwait { it }
			cancelar.onAwait { throw CancellationException("cancelado") }
		}
	} catch (e: CancellationException) {
		tarea.cancel()
		if (cancelar.isCompleted) avisar(getString(R.string.cancelado), mostrar = false)
		throw e
	} finally {
		mostrar.cancel()
		dialogo?.takeIf { it.isShowing }?.dismiss()
	}
}
