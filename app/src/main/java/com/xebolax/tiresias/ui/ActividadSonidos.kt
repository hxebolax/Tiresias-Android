package com.xebolax.tiresias.ui

import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.accionesAccesibles
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.conEspera
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.scripts.Tienda
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.Sonido
import com.xebolax.tiresias.voz.Sonidos
import com.xebolax.tiresias.voz.TemasSonido
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Los sonidos y las vibraciones, uno a uno, y los temas de sonidos.
 *
 * - Cada fila es una casilla (marcada, ese sonido suena) y en su menú de
 *   acciones están «Probar», «Vibrar» o «No vibrar», y elegir o quitar su
 *   archivo propio. Sin lector, mantener pulsada la fila da las mismas opciones.
 * - Arriba: el tema (los de Tiresias, uno importado o uno de la tienda),
 *   importar un tema en ZIP y exportar los sonidos de ahora en ZIP.
 */
class ActividadSonidos : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes
	/** El sonido al que se le está eligiendo un archivo propio. */
	private var eligiendo: Sonido? = null

	private val elegirAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { ponerPropio(it) } }
	private val elegirZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importar(it) } }
	private val guardarZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { exportar(it) } }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.sonidos_titulo))
		construir()
	}

	private fun nombre(s: Sonido): String {
		val id = resources.getIdentifier("sonido_" + s.name.lowercase(), "string", packageName)
		return if (id != 0) getString(id) else s.name.lowercase()
	}

	private fun nombreTema(t: String): String = if (t.isEmpty()) getString(R.string.temas_tiresias) else t

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.sonidos_explicacion))
		// Los temas
		p.encabezado(getString(R.string.temas_titulo))
		p.boton(getString(R.string.temas_elegir, nombreTema(ajustes.temaSonidos))) { elegirTema() }
		p.botonSecundario(getString(R.string.temas_tienda)) { temasDeLaTienda() }
		p.botonSecundario(getString(R.string.temas_importar)) { elegirZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
		p.botonSecundario(getString(R.string.temas_exportar)) { guardarZip.launch("tiresias-sonidos.zip") }
		// Cada sonido
		p.encabezado(getString(R.string.sonidos_uno_a_uno))
		for (s in Sonido.entries) {
			val suena = s.name !in ajustes.sonidosApagados
			val vibra = s.name !in ajustes.vibracionesApagadas
			val propio = TemasSonido.propio(this, s) != null
			val caja = MaterialCheckBox(this).apply {
				text = getString(
					if (vibra) R.string.sonidos_fila_vibra else R.string.sonidos_fila_no_vibra,
					if (propio) getString(R.string.sonidos_con_propio, nombre(s)) else nombre(s),
				)
				isChecked = suena
				minHeight = dp(this@ActividadSonidos, 48)
				setOnCheckedChangeListener { _, marcada ->
					ajustes.apagarSonido(s, !marcada)
					ServicioTiresias.instancia?.recargarAjustes()
				}
			}
			val acciones = mutableListOf(
				getString(R.string.sonidos_probar) to { probar(s) },
				getString(if (vibra) R.string.sonidos_no_vibrar else R.string.sonidos_vibrar) to {
					ajustes.apagarVibracion(s, vibra)
					avisar(getString(if (vibra) R.string.sonidos_ya_no_vibra else R.string.sonidos_ya_vibra, nombre(s)))
					construir()
				},
				getString(R.string.sonidos_elegir_archivo) to {
					eligiendo = s
					elegirAudio.launch(arrayOf("audio/*"))
				},
			)
			if (propio) acciones += getString(R.string.sonidos_quitar_archivo) to {
				TemasSonido.quitarPropio(this, s)
				recargar()
				avisar(getString(R.string.sonidos_archivo_quitado, nombre(s)))
				construir()
			}
			caja.accionesAccesibles(acciones)
			caja.setOnLongClickListener {
				elegir(nombre(s), acciones)
				true
			}
			p.contenedor.addView(caja, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
		}
	}

	private fun recargar() = ServicioTiresias.instancia?.recargarSonidos()

	private fun elegirTema() {
		val temas = listOf("") + TemasSonido.instalados(this)
		elegirUno(getString(R.string.temas_titulo), temas.map { nombreTema(it) }, temas.indexOf(ajustes.temaSonidos).coerceAtLeast(0)) { i ->
			val t = temas[i]
			if (t.isEmpty() || t == ajustes.temaSonidos) {
				ajustes.temaSonidos = t
				recargar()
				construir()
				return@elegirUno
			}
			// Un tema instalado: ponerlo o borrarlo
			elegir(t, listOf(
				getString(R.string.temas_poner, t) to {
					ajustes.temaSonidos = t
					recargar()
					avisar(getString(R.string.temas_puesto, t))
					construir()
				},
				getString(R.string.temas_borrar, t) to {
					confirmar(getString(R.string.temas_borrar_pregunta, t), getString(R.string.temas_borrar, t)) {
						TemasSonido.borrar(this, t)
						if (ajustes.temaSonidos == t) ajustes.temaSonidos = ""
						recargar()
						construir()
					}
				},
			))
		}
	}

	private fun ponerPropio(uri: Uri) {
		val s = eligiendo ?: return
		eligiendo = null
		val ext = (contentResolver.getType(uri)?.substringAfter('/') ?: uri.lastPathSegment?.substringAfterLast('.')).orEmpty()
			.let { if (it == "mpeg") "mp3" else if (it == "x-wav" || it == "wave") "wav" else it }
		lifecycleScope.launch {
			try {
				withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { TemasSonido.ponerPropio(this@ActividadSonidos, s, it, ext) } }
				recargar()
				avisar(getString(R.string.sonidos_archivo_puesto, nombre(s)))
				construir()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo poner el archivo propio", e)
				avisar(getString(R.string.sonidos_archivo_mal))
			}
		}
	}

	private fun importar(uri: Uri) {
		val nombre = uri.lastPathSegment?.substringAfterLast('/') ?: "tema"
		lifecycleScope.launch {
			try {
				val tema = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { TemasSonido.importarZip(this@ActividadSonidos, it, nombre) } } ?: return@launch
				ajustes.temaSonidos = tema
				recargar()
				avisar(getString(R.string.temas_importado, tema))
				construir()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo importar el tema", e)
				avisar(getString(R.string.temas_importar_mal))
			}
		}
	}

	private fun exportar(uri: Uri) {
		lifecycleScope.launch {
			try {
				withContext(Dispatchers.IO) {
					val sonidos = Sonidos(this@ActividadSonidos)
					try {
						contentResolver.openOutputStream(uri)?.use { o -> TemasSonido.exportarZip(o) { s -> sonidos.efectivo(s) } }
					} finally {
						sonidos.cerrar()
					}
				}
				avisar(getString(R.string.temas_exportado))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo exportar el tema", e)
				avisar(getString(R.string.temas_exportar_mal))
			}
		}
	}

	private fun temasDeLaTienda() {
		lifecycleScope.launch {
			val temas = try {
				conEspera(getString(R.string.temas_tienda), getString(R.string.tienda_cargando)) { Tienda.temas(this@ActividadSonidos) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "La tienda de temas no respondió", e)
				avisar(getString(R.string.tienda_error))
				return@launch
			}
			if (temas.isEmpty()) {
				avisar(getString(R.string.temas_tienda_vacia))
				return@launch
			}
			val instalados = TemasSonido.instalados(this@ActividadSonidos)
			elegir(getString(R.string.temas_tienda), temas.map { t ->
				getString(if (t.id in instalados) R.string.temas_tienda_fila_instalado else R.string.temas_tienda_fila, t.nombre, t.descripcion) to { instalarDeLaTienda(t) }
			})
		}
	}

	private fun instalarDeLaTienda(t: Tienda.Tema) {
		lifecycleScope.launch {
			try {
				val datos = conEspera(getString(R.string.temas_tienda), getString(R.string.tienda_descargando, t.nombre)) { Tienda.descargarTema(this@ActividadSonidos, t) }
				val tema = withContext(Dispatchers.IO) { TemasSonido.importarZip(this@ActividadSonidos, datos.inputStream(), t.id) }
				ajustes.temaSonidos = tema
				recargar()
				avisar(getString(R.string.temas_puesto, t.nombre))
				construir()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo instalar el tema de la tienda", e)
				avisar(getString(R.string.temas_importar_mal))
			}
		}
	}

	private fun probar(s: Sonido) {
		val servicio = ServicioTiresias.instancia
		if (servicio == null) {
			avisar(getString(R.string.aj_probar_sin_servicio))
			return
		}
		servicio.probarSonido(s)
	}

	companion object {
		private const val ETIQUETA = "Sonidos"
	}
}
