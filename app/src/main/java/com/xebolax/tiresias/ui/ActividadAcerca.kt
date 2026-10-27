package com.xebolax.tiresias.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.actualizaciones.GestorActualizaciones
import com.xebolax.tiresias.base.Registro
import java.io.File

/**
 * Acerca de y actualizaciones: versión, buscar actualizaciones, compartir el
 * registro de fallos (sin nada leído de la pantalla), créditos y licencias.
 */
class ActividadAcerca : ActividadBase() {
	private lateinit var actualizaciones: GestorActualizaciones

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		actualizaciones = GestorActualizaciones(this)
		val p = pantalla(getString(R.string.ui_acerca))
		p.encabezado(getString(R.string.app_name))
		p.parrafo(getString(R.string.acerca_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
		p.parrafo(getString(R.string.acerca_descripcion))
		p.boton(getString(R.string.act_buscar)) { actualizaciones.comprobarAMano() }
		p.boton(getString(R.string.acerca_compartir_registro)) { compartirRegistro() }
		p.encabezado(getString(R.string.acerca_creditos))
		p.parrafo(getString(R.string.acerca_creditos_texto))
		p.botonSecundario(getString(R.string.acerca_licencias)) { licencias() }
		p.encabezado(getString(R.string.acerca_privacidad))
		p.parrafo(getString(R.string.acerca_privacidad_texto))
	}

	private fun compartirRegistro() {
		val texto = Registro.cabecera(BuildConfig.VERSION_NAME) + Registro.texto()
		if (texto.isBlank()) {
			avisar(getString(R.string.acerca_registro_vacio))
			return
		}
		try {
			val f = File(cacheDir, "compartir/registro_tiresias.txt").apply { parentFile?.mkdirs(); writeText(texto) }
			val uri = FileProvider.getUriForFile(this, "$packageName.archivos", f)
			val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
				.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.acerca_asunto_registro, BuildConfig.VERSION_NAME, Build.MODEL))
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			startActivity(Intent.createChooser(intent, getString(R.string.acerca_compartir_registro)))
		} catch (e: Exception) {
			Registro.e("Acerca", "No se pudo compartir el registro", e)
			avisar(getString(R.string.error_exportar))
		}
	}

	private fun licencias() {
		val texto = runCatching { assets.open("licencias/TERCEROS.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
		mensaje(getString(R.string.acerca_licencias), texto)
	}
}
