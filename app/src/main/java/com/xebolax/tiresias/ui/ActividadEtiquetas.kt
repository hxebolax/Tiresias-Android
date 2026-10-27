package com.xebolax.tiresias.ui

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.perfiles.Etiquetas
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Etiquetas personalizadas, agrupadas por app: cambiar, borrar una o todas las
 * de una app, exportar e importar (todas o las de una app). Las etiquetas se
 * ponen desde cualquier app con la acción «Etiquetar» del menú de Tiresias.
 */
class ActividadEtiquetas : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var etiquetas: Etiquetas
	private var exportarApp: String? = null

	private val importar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { leerDe(it) } }
	private val exportar = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { escribirEn(it) } }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		etiquetas = Etiquetas(this)
		p = pantalla(getString(R.string.ui_etiquetas))
		construir()
	}

	private fun nombreApp(paquete: String): String = runCatching {
		packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, PackageManager.GET_META_DATA)).toString()
	}.getOrDefault(paquete)

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.etq_explicacion))
		val todas = etiquetas.todas()
		if (todas.isEmpty()) p.parrafo(getString(R.string.etq_ninguna))
		for ((paquete, mapa) in todas.toSortedMap(compareBy { nombreApp(it) })) {
			val app = nombreApp(paquete)
			p.encabezado(resources.getQuantityString(R.plurals.etq_app, mapa.size, app, mapa.size))
			for ((id, texto) in mapa) {
				p.botonSecundario(getString(R.string.etq_fila, texto, id.substringAfterLast('/'))) { opciones(paquete, id, texto) }
			}
			p.botonSecundario(getString(R.string.etq_exportar_app, app)) {
				exportarApp = paquete
				exportar.launch("etiquetas_${app.replace(Regex("[^A-Za-z0-9]+"), "_")}.json")
			}
			p.botonSecundario(getString(R.string.etq_borrar_app, app)) {
				confirmar(getString(R.string.etq_borrar_app_pregunta, app), getString(R.string.etq_borrar_app, app)) {
					etiquetas.borrarApp(paquete)
					ServicioTiresias.instancia?.recargarAjustes()
					construir()
				}
			}
		}
		p.separador()
		p.boton(getString(R.string.etq_importar)) { importar.launch(arrayOf("application/json", "text/*")) }
		p.boton(getString(R.string.etq_exportar)) {
			exportarApp = null
			exportar.launch("etiquetas_tiresias.json")
		}
	}

	private fun opciones(paquete: String, id: String, texto: String) {
		elegir(texto, listOf(
			getString(R.string.etq_cambiar) to {
				pedirTexto(getString(R.string.etiqueta_titulo), getString(R.string.etiqueta_pista), texto) { nuevo ->
					etiquetas.poner(paquete, id, nuevo)
					ServicioTiresias.instancia?.recargarAjustes()
					construir()
				}
			},
			getString(R.string.etq_borrar, texto) to {
				etiquetas.poner(paquete, id, null)
				ServicioTiresias.instancia?.recargarAjustes()
				construir()
			},
		))
	}

	private fun leerDe(uri: Uri) {
		try {
			val texto = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
			val n = etiquetas.importarJson(texto)
			ServicioTiresias.instancia?.recargarAjustes()
			avisar(resources.getQuantityString(R.plurals.etq_importadas, n, n))
			construir()
		} catch (e: Exception) {
			Registro.e("Etiquetas", "No se pudieron importar las etiquetas", e)
			avisar(getString(R.string.error_importar))
		}
	}

	private fun escribirEn(uri: Uri) {
		try {
			contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(etiquetas.exportarJson(exportarApp)) }
			avisar(getString(R.string.exportado))
		} catch (e: Exception) {
			Registro.e("Etiquetas", "No se pudieron exportar las etiquetas", e)
			avisar(getString(R.string.error_exportar))
		}
	}
}
