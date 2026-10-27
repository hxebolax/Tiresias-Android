package com.xebolax.tiresias.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.xebolax.tiresias.accesibilidad.conEspera
import com.xebolax.tiresias.scripts.Tienda
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.scripts.Scripts
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Complementos: la tienda del servidor de Tiresias, instalar desde un archivo (.js), activar, desactivar,
 * desinstalar y volver a encenderlos tras el modo seguro. Antes de instalar se
 * dice en qué apps actúa y qué permisos pide.
 */
class ActividadComplementos : ActividadBase() {
	private lateinit var p: Pantalla
	private val scripts by lazy { Scripts(null, this) }

	private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { revisar(it) } }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.ui_complementos))
		construir()
	}

	private fun avisarServicio() {
		ServicioTiresias.instancia?.recargarComplementos()
	}

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.comp_explicacion))
		if (!scripts.activos) {
			p.estado(getString(R.string.comp_apagados), false) {
				scripts.encender()
				avisarServicio()
				construir()
			}
		}
		val lista = scripts.instalados()
		if (lista.isEmpty()) p.parrafo(getString(R.string.comp_ninguno))
		for (c in lista) {
			val activo = scripts.activado(c.id)
			p.botonSecundario(getString(R.string.comp_fila, c.nombre, c.version, getString(if (activo) R.string.comp_activado else R.string.comp_desactivado))) {
				val opciones = ArrayList<com.xebolax.tiresias.accesibilidad.Accion>()
				// Sus ajustes, primero: es lo que más se usa
				if (c.ajustes.isNotEmpty()) opciones += getString(R.string.comp_ajustes, c.nombre) to {
					startActivity(android.content.Intent(this, ActividadAjustesComplemento::class.java).putExtra(ActividadAjustesComplemento.EXTRA_ID, c.id))
				}
				opciones += listOf(
					getString(if (activo) R.string.comp_desactivar else R.string.comp_activar, c.nombre) to {
						scripts.activar(c.id, !activo)
						avisarServicio()
						construir()
					},
					getString(R.string.comp_desinstalar, c.nombre) to {
						confirmar(getString(R.string.comp_desinstalar_pregunta, c.nombre), getString(R.string.comp_desinstalar, c.nombre)) {
							scripts.desinstalar(c.id)
							avisarServicio()
							construir()
						}
					},
				)
				elegir(c.nombre, opciones)
			}
		}
		p.separador()
		p.boton(getString(R.string.tienda_abrir)) { abrirTienda() }
		p.botonSecundario(getString(R.string.tienda_fuentes, Tienda.fuentes(this).size)) { fuentes() }
		p.botonSecundario(getString(R.string.comp_instalar)) { elegirArchivo.launch(arrayOf("application/javascript", "text/javascript", "text/*", "application/octet-stream")) }
		if (scripts.activos && lista.isNotEmpty()) p.botonSecundario(getString(R.string.comp_apagar)) {
			scripts.apagarTodos()
			avisarServicio()
			construir()
		}
	}

	/** Las fuentes de complementos del usuario: añadir una o quitar las que hay. */
	private fun fuentes() {
		val a = Ajustes(this)
		val opciones = ArrayList<com.xebolax.tiresias.accesibilidad.Accion>()
		opciones += getString(R.string.tienda_fuente_anadir) to {
			pedirTexto(getString(R.string.tienda_fuente_anadir), getString(R.string.tienda_fuente_pista)) { texto ->
				val url = Tienda.normalizarFuente(texto)
				if (url == null) avisar(getString(R.string.tienda_fuente_mal))
				else {
					a.fuentesComplementos = a.fuentesComplementos + url
					avisar(getString(R.string.tienda_fuente_anadida))
					construir()
				}
			}
		}
		for (f in a.fuentesComplementos) opciones += getString(R.string.tienda_fuente_quitar, f) to {
			a.fuentesComplementos = a.fuentesComplementos - f
			avisar(getString(R.string.tienda_fuente_quitada))
			construir()
		}
		elegir(getString(R.string.tienda_fuentes_titulo), opciones)
	}

	/** La tienda: el catálogo del servidor, con lo que ya está instalado y lo que tiene versión nueva. */
	private fun abrirTienda() {
		lifecycleScope.launch {
			val catalogo = try {
				conEspera(getString(R.string.tienda_titulo), getString(R.string.tienda_cargando)) { Tienda.catalogo(this@ActividadComplementos) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w("Complementos", "No se pudo leer la tienda", e)
				avisar(getString(R.string.tienda_error))
				return@launch
			}
			if (catalogo.isEmpty()) {
				avisar(getString(R.string.tienda_vacia))
				return@launch
			}
			val instalados = scripts.instalados().associate { it.id to it.version }
			elegir(getString(R.string.tienda_titulo), catalogo.map { e ->
				val estado = when {
					Tienda.esNueva(e, instalados[e.id]) -> getString(R.string.tienda_actualizable, instalados[e.id])
					e.id in instalados -> getString(R.string.tienda_instalado)
					else -> ""
				}
				getString(R.string.tienda_fila, e.nombre, e.version, e.descripcion, estado).trim().trimEnd(',') to { instalarDeTienda(e) }
			})
		}
	}

	private fun instalarDeTienda(e: Tienda.Entrada) {
		lifecycleScope.launch {
			val codigo = try {
				conEspera(getString(R.string.tienda_titulo), getString(R.string.tienda_descargando, e.nombre)) { Tienda.descargar(this@ActividadComplementos, e) }
			} catch (ex: CancellationException) {
				throw ex
			} catch (ex: Exception) {
				Registro.w("Complementos", "No se pudo descargar ${e.id}", ex)
				avisar(getString(R.string.tienda_error_descarga, e.nombre))
				return@launch
			}
			revisarCodigo(codigo)
		}
	}

	/** Lee el complemento de un archivo, enseña sus apps y permisos y lo instala si el usuario lo confirma. */
	private fun revisar(uri: Uri) {
		val codigo = try {
			contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
		} catch (e: Exception) {
			Registro.e("Complementos", "No se pudo leer el archivo", e)
			avisar(getString(R.string.error_importar))
			return
		}
		revisarCodigo(codigo)
	}

	private fun revisarCodigo(codigo: String) {
		val info = try {
			scripts.leerManifiesto(codigo)
		} catch (e: Exception) {
			avisar(getString(R.string.comp_no_valido, e.message.orEmpty()))
			return
		}
		val apps = if ("*" in info.apps) getString(R.string.comp_todas_apps) else info.apps.joinToString(", ").ifEmpty { getString(R.string.comp_todas_apps) }
		val permisos = info.permisos.mapNotNull {
			when (it) {
				"voz" -> getString(R.string.comp_permiso_voz)
				"portapapeles" -> getString(R.string.comp_permiso_portapapeles)
				"braille" -> getString(R.string.comp_permiso_braille)
				"abrir" -> getString(R.string.comp_permiso_abrir)
				else -> null
			}
		}.joinToString(", ").ifEmpty { getString(R.string.comp_sin_permisos) }
		confirmar(getString(R.string.comp_confirmar, info.nombre, info.version, apps, permisos, info.descripcion), getString(R.string.comp_confirmar_si, info.nombre)) {
			try {
				scripts.instalar(codigo)
				avisarServicio()
				avisar(getString(R.string.comp_instalado, info.nombre))
			} catch (e: Exception) {
				avisar(getString(R.string.comp_no_valido, e.message.orEmpty()))
			}
			construir()
		}
	}
}
