package com.xebolax.tiresias.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ia.Personas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Las personas que reconoce la IA al describir (Backtalk 173): cada una, un
 * nombre y una foto de su cara. Se añaden con «Añadir una persona» (primero el
 * nombre, luego la foto) y se borran desde su fila.
 */
class ActividadPersonas : ActividadBase() {
	private lateinit var p: Pantalla
	/** El nombre de la persona que se está añadiendo, hasta que se elige la foto. */
	private var nombrePendiente: String? = null

	private val elegirFoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { anadir(it) } }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.personas_titulo))
		construir()
	}

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.personas_explicacion))
		val lista = Personas.lista(this)
		if (lista.isEmpty()) p.parrafo(getString(R.string.personas_ninguna))
		for (persona in lista) {
			p.botonSecundario(persona.nombre) {
				elegir(persona.nombre, listOf(
					getString(R.string.personas_borrar, persona.nombre) to {
						confirmar(getString(R.string.personas_borrar_pregunta, persona.nombre), getString(R.string.personas_borrar, persona.nombre)) {
							Personas.borrar(this, persona.id)
							avisar(getString(R.string.personas_borrada, persona.nombre))
							construir()
						}
					},
				))
			}
		}
		p.separador()
		p.boton(getString(R.string.personas_anadir)) {
			pedirTexto(getString(R.string.personas_anadir), getString(R.string.personas_nombre)) { nombre ->
				if (nombre.isBlank()) return@pedirTexto
				nombrePendiente = nombre
				elegirFoto.launch(arrayOf("image/*"))
			}
		}
	}

	private fun anadir(uri: Uri) {
		val nombre = nombrePendiente ?: return
		nombrePendiente = null
		lifecycleScope.launch {
			try {
				withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { Personas.anadir(this@ActividadPersonas, nombre, it) } }
				avisar(getString(R.string.personas_anadida, nombre))
				construir()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo añadir la persona", e)
				avisar(getString(R.string.personas_mal))
			}
		}
	}

	companion object {
		private const val ETIQUETA = "Personas"
	}
}
