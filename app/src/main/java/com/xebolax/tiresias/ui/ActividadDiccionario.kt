package com.xebolax.tiresias.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Diccionario
import com.xebolax.tiresias.nucleo.EntradaDiccionario
import com.xebolax.tiresias.nucleo.TipoEntrada
import com.xebolax.tiresias.servicio.ServicioTiresias
import java.io.File

/**
 * Diccionario de pronunciación: cambiar cómo se dice una palabra, un texto o
 * una expresión regular. Se importa y exporta en el formato de Tiresias, y se
 * importan diccionarios de NVDA (`.dic`).
 */
class ActividadDiccionario : ActividadBase() {
	private lateinit var p: Pantalla
	private val archivo by lazy { File(Almacen.protegido(this).filesDir, "diccionario.tsv") }
	private var entradas = mutableListOf<EntradaDiccionario>()

	private val importar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { leerDe(it) } }
	private val exportar = registerForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri -> uri?.let { escribirEn(it) } }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.ui_diccionario))
		entradas = runCatching { Diccionario.leer(archivo.readText()).entradas.toMutableList() }.getOrDefault(mutableListOf())
		construir()
	}

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.dic_explicacion))
		p.boton(getString(R.string.dic_nueva)) { editar(null) }
		p.encabezado(resources.getQuantityString(R.plurals.dic_entradas, entradas.size, entradas.size))
		entradas.forEachIndexed { i, e ->
			p.botonSecundario(getString(R.string.dic_fila, e.patron, e.reemplazo)) { opciones(i) }
		}
		p.separador()
		p.botonSecundario(getString(R.string.dic_importar)) { importar.launch(arrayOf("text/*", "application/octet-stream")) }
		p.botonSecundario(getString(R.string.dic_exportar)) { exportar.launch("diccionario_tiresias.tsv") }
	}

	private fun opciones(i: Int) {
		val e = entradas[i]
		elegir(getString(R.string.dic_fila, e.patron, e.reemplazo), listOf(
			getString(R.string.dic_editar) to { editar(i) },
			getString(R.string.dic_borrar, e.patron) to {
				confirmar(getString(R.string.dic_borrar_pregunta, e.patron), getString(R.string.dic_borrar, e.patron)) {
					entradas.removeAt(i)
					guardar()
				}
			},
		))
	}

	private fun editar(i: Int?) {
		val actual = i?.let { entradas[it] }
		pedirTexto(getString(R.string.dic_patron), getString(R.string.dic_patron_pista), actual?.patron.orEmpty()) { patron ->
			if (patron.isBlank()) return@pedirTexto
			pedirTexto(getString(R.string.dic_reemplazo), getString(R.string.dic_reemplazo_pista), actual?.reemplazo.orEmpty()) { reemplazo ->
				val tipos = TipoEntrada.entries
				val nombres = listOf(getString(R.string.dic_tipo_texto), getString(R.string.dic_tipo_palabra), getString(R.string.dic_tipo_expresion))
				elegirUno(getString(R.string.dic_tipo), nombres, tipos.indexOf(actual?.tipo ?: TipoEntrada.PALABRA)) { t ->
					val nueva = EntradaDiccionario(patron, reemplazo, tipos[t])
					if (nueva.regex == null) {
						avisar(getString(R.string.dic_expresion_mal))
						return@elegirUno
					}
					if (i == null) entradas += nueva else entradas[i] = nueva
					guardar()
				}
			}
		}
	}

	private fun guardar() {
		try {
			archivo.writeText(Diccionario(entradas).escribir())
			ServicioTiresias.instancia?.recargarAjustes()
		} catch (e: Exception) {
			Registro.e("Diccionario", "No se pudo guardar el diccionario", e)
			avisar(getString(R.string.error_guardar))
		}
		construir()
	}

	private fun leerDe(uri: Uri) {
		try {
			val texto = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
			val nuevas = Diccionario.leer(texto).entradas
			entradas.addAll(nuevas)
			guardar()
			avisar(resources.getQuantityString(R.plurals.dic_importadas, nuevas.size, nuevas.size))
		} catch (e: Exception) {
			Registro.e("Diccionario", "No se pudo importar", e)
			avisar(getString(R.string.error_importar))
		}
	}

	private fun escribirEn(uri: Uri) {
		try {
			contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(Diccionario(entradas).escribir()) }
			avisar(getString(R.string.exportado))
		} catch (e: Exception) {
			Registro.e("Diccionario", "No se pudo exportar", e)
			avisar(getString(R.string.error_exportar))
		}
	}
}
