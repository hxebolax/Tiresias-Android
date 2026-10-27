package com.xebolax.tiresias.ia

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xebolax.tiresias.base.Registro
import java.io.File

/**
 * Gemini Nano del propio móvil (Pixel 9 o posterior, Galaxy S25 o posterior y
 * otros: ver `docs/05_ia_local.md`), a través del complemento «Tiresias IA del
 * móvil» (`com.xebolax.tiresias.nano`).
 *
 * Por qué un complemento aparte: ML Kit GenAI es una biblioteca propietaria de
 * Google que no puede ir en el mismo APK que eSpeak NG (GPL) y que rompería el
 * «sin Play Services» del lector; y AICore solo responde a la app que está en
 * primer plano, así que la pregunta la hace la ventana del complemento, que
 * muestra la respuesta como texto que Tiresias lee. Al cerrarla, el foco
 * vuelve donde estaba.
 */
object Nano {
	const val PAQUETE = "com.xebolax.tiresias.nano"
	const val ACCION = "com.xebolax.tiresias.nano.PREGUNTAR"
	const val EXTRA_PREGUNTA = "pregunta"
	const val EXTRA_SISTEMA = "sistema"
	const val EXTRA_TITULO = "titulo"

	fun instalado(c: Context): Boolean = runCatching {
		c.packageManager.getPackageInfo(PAQUETE, 0)
		true
	}.getOrDefault(false)

	/** Si el móvil tiene AICore (el servicio del sistema de Gemini Nano). */
	fun tieneAicore(c: Context): Boolean = runCatching {
		c.packageManager.getPackageInfo("com.google.android.aicore", 0)
		true
	}.getOrDefault(false)

	/** Abre la ventana del complemento con la [pregunta] y, si la hay, la [imagen]. */
	fun preguntar(c: Context, titulo: String, pregunta: String, imagen: File?): Boolean {
		val intent = Intent(ACCION).setPackage(PAQUETE)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			.putExtra(EXTRA_TITULO, titulo)
			.putExtra(EXTRA_PREGUNTA, pregunta)
			.putExtra(EXTRA_SISTEMA, AsistenteIA.instrucciones(c))
		if (imagen != null) {
			val uri = FileProvider.getUriForFile(c, c.packageName + ".archivos", imagen)
			intent.setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		return try {
			c.startActivity(intent)
			true
		} catch (e: Exception) {
			Registro.e("Nano", "No se pudo abrir el complemento de Gemini Nano", e)
			false
		}
	}
}
