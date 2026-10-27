package com.xebolax.tiresias.ui

import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ia.ModeloLocal
import com.xebolax.tiresias.ia.Nano
import com.xebolax.tiresias.recursos.Recursos
import com.xebolax.tiresias.recursos.VocesNeuronales
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recursos que se descargan solo si el usuario quiere: reconocimiento de texto,
 * reconocimiento de voz, voces neuronales y los modelos de IA para el propio
 * móvil (también los que usan los Pixel). Cada uno dice su tamaño y su licencia
 * antes de bajarlo.
 */
class ActividadRecursos : ActividadBase() {
	private lateinit var p: Pantalla
	private var descarga: Job? = null
	private lateinit var actualizaciones: com.xebolax.tiresias.actualizaciones.GestorActualizaciones

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		actualizaciones = com.xebolax.tiresias.actualizaciones.GestorActualizaciones(this)
		p = pantalla(getString(R.string.ui_recursos))
	}

	override fun onResume() {
		super.onResume()
		construir()
	}

	private fun tamano(b: Long) = if (b > 0) Formatter.formatShortFileSize(this, b) else getString(R.string.rec_tamano_desconocido)

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.rec_explicacion))

		p.encabezado(getString(R.string.rec_cat_lectura))
		recurso(Recursos.OCR)
		recurso(Recursos.VOSK_ES)

		p.encabezado(getString(R.string.rec_cat_voces))
		p.parrafo(getString(R.string.rec_voces_explicacion))
		for (v in VocesNeuronales.CATALOGO) recurso(v.recurso, getString(R.string.rec_voz_nombre, v.nombre, getString(v.calidad)), v)

		p.encabezado(getString(R.string.rec_cat_ia))
		p.parrafo(getString(R.string.rec_ia_explicacion, String.format("%.1f", ModeloLocal.memoriaGb(this))))
		ModeloLocal.chipTensor()?.let { p.parrafo(getString(R.string.rec_ia_pixel, it)) }
		if (!ModeloLocal.compatible()) p.parrafo(getString(R.string.rec_ia_no_compatible))
		else {
			val elegido = ModeloLocal.elegido(this)
			for (m in ModeloLocal.disponibles()) {
				val bajado = ModeloLocal.archivo(this, m).let { it.isFile && it.length() == m.tamano }
				val marca = when {
					bajado && m == elegido -> getString(R.string.rec_en_uso)
					bajado -> getString(R.string.rec_descargado)
					else -> getString(R.string.rec_no_descargado)
				}
				p.botonSecundario(getString(R.string.rec_modelo_fila, getString(m.nombre), tamano(m.tamano), m.memoriaGb, marca)) { opcionesModelo(m, bajado) }
			}
			val estado = ModeloLocal.estado(this)
			if (estado == ModeloLocal.Estado.DESCARGANDO) p.estadoVivo(getString(R.string.rec_ia_bajando, ModeloLocal.porcentaje(this)))
		}

		p.encabezado(getString(R.string.rec_cat_nano))
		val aicore = Nano.tieneAicore(this)
		val instalado = Nano.instalado(this)
		p.estado(getString(when {
			instalado -> R.string.rec_nano_instalado
			aicore -> R.string.rec_nano_falta
			else -> R.string.rec_nano_sin_aicore
		}), if (instalado) true else if (aicore) false else null) {
			// Se descarga del servidor de Tiresias con las mismas comprobaciones que una actualización
			if (!instalado && aicore) actualizaciones.instalarComplemento("nano", getString(R.string.rec_nano_nombre), Nano.PAQUETE)
		}
		p.parrafo(getString(R.string.rec_nano_explicacion))
	}

	private fun recurso(r: Recursos.Recurso, titulo: String = getString(r.nombre), voz: VocesNeuronales.Voz? = null) {
		val instalado = Recursos.instalado(this, r)
		val marca = getString(if (instalado) R.string.rec_descargado else R.string.rec_no_descargado)
		p.botonSecundario(getString(R.string.rec_fila, titulo, tamano(r.tamano), marca)) {
			val descripcion = buildString {
				append(getString(r.descripcion))
				append("\n\n").append(getString(R.string.rec_licencia, r.licencia))
				if (voz?.derivadaLessac == true) append("\n").append(getString(R.string.rec_aviso_lessac))
			}
			val opciones = mutableListOf<Pair<String, () -> Unit>>()
			if (!instalado) opciones += getString(R.string.rec_descargar, titulo) to { bajar(r, titulo) }
			else {
				if (voz != null) opciones += getString(R.string.rec_usar_voz, titulo) to {
					Ajustes(this).motor = "tiresias:piper:${voz.id}"
					com.xebolax.tiresias.servicio.ServicioTiresias.instancia?.recargarAjustes()
					avisar(getString(R.string.rec_voz_en_uso, titulo))
				}
				opciones += getString(R.string.rec_borrar, titulo) to {
					confirmar(getString(R.string.rec_borrar_pregunta, titulo), getString(R.string.rec_borrar, titulo)) {
						Recursos.borrar(this, r)
						construir()
					}
				}
			}
			opciones += getString(R.string.rec_ver_detalle) to { mensaje(titulo, descripcion) }
			elegir(titulo, opciones)
		}
	}

	private fun bajar(r: Recursos.Recurso, titulo: String) {
		val margen = dp(this, 20)
		val estado = TextView(this).apply {
			text = getString(R.string.act_descargando_inicio)
			// El porcentaje cambia sin parar: no se anuncia solo (se lee al tocarlo)
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		}
		val barra = LinearProgressIndicator(this).apply {
			max = 100
			isIndeterminate = true
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		val vista = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(margen, margen / 2, margen, 0)
			addView(estado)
			addView(barra)
		}
		val dialogo: AlertDialog = MaterialAlertDialogBuilder(this)
			.setTitle(getString(R.string.rec_bajando, titulo))
			.setView(vista)
			.setCancelable(false)
			.setNegativeButton(R.string.cancelar) { _, _ -> descarga?.cancel() }
			.show()
		var ultimo = 0L
		descarga = lifecycleScope.launch {
			try {
				withContext(Dispatchers.IO) {
					Recursos.instalar(this@ActividadRecursos, r, { n, total ->
						val ahora = SystemClock.uptimeMillis()
						if (ahora - ultimo > 300) {
							ultimo = ahora
							runOnUiThread {
								if (total > 0) {
									barra.isIndeterminate = false
									barra.progress = (n * 100 / total).toInt()
									estado.text = getString(R.string.act_descargando, (n * 100 / total).toInt(), tamano(n), tamano(total))
								} else estado.text = tamano(n)
							}
						}
					}, { isActive })
				}
				dialogo.dismiss()
				avisar(getString(R.string.rec_listo, titulo))
			} catch (e: CancellationException) {
				dialogo.dismiss()
				avisar(getString(R.string.act_cancelada))
				throw e
			} catch (e: InterruptedException) {
				dialogo.dismiss()
				avisar(getString(R.string.act_cancelada))
			} catch (e: Exception) {
				Registro.w("Recursos", "Falló la descarga de ${r.id}", e)
				dialogo.dismiss()
				mensaje(titulo, getString(R.string.rec_error, e.message.orEmpty()))
			}
			construir()
		}
	}

	private fun opcionesModelo(m: ModeloLocal.Modelo, bajado: Boolean) {
		val nombre = getString(m.nombre)
		val opciones = mutableListOf<Pair<String, () -> Unit>>()
		if (!bajado) {
			opciones += getString(R.string.rec_descargar_wifi, nombre) to { empezarModelo(m, true) }
			opciones += getString(R.string.rec_descargar_datos, nombre) to { empezarModelo(m, false) }
		} else {
			opciones += getString(R.string.rec_usar_modelo, nombre) to {
				ModeloLocal.elegir(this, m)
				avisar(getString(R.string.rec_modelo_en_uso, nombre))
				construir()
			}
			opciones += getString(R.string.rec_borrar, nombre) to {
				confirmar(getString(R.string.rec_borrar_pregunta, nombre), getString(R.string.rec_borrar, nombre)) {
					ModeloLocal.pararServicio(this)
					ModeloLocal.archivo(this, m).delete()
					construir()
				}
			}
		}
		if (ModeloLocal.estado(this) == ModeloLocal.Estado.DESCARGANDO) opciones += getString(R.string.rec_cancelar_descarga) to {
			ModeloLocal.cancelar(this)
			construir()
		}
		elegir(nombre, opciones)
	}

	private fun empezarModelo(m: ModeloLocal.Modelo, soloWifi: Boolean) {
		if (ModeloLocal.memoriaGb(this) < m.memoriaGb - 1) {
			confirmar(getString(R.string.rec_poca_memoria, getString(m.nombre), m.memoriaGb), getString(R.string.rec_descargar_igual)) {
				ModeloLocal.elegir(this, m)
				ModeloLocal.descargar(this, soloWifi)
				construir()
			}
			return
		}
		ModeloLocal.elegir(this, m)
		ModeloLocal.descargar(this, soloWifi)
		avisar(getString(R.string.rec_ia_empezada, getString(m.nombre)))
		construir()
	}
}
