package com.xebolax.tiresias.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.perfiles.Perfiles
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Perfiles: con nombre (fotos de los ajustes de voz y lectura que se cambian
 * con un gesto) y por app (velocidad, puntuación, verbosidad, eco y silencio de
 * notificaciones solo en esa app).
 */
class ActividadPerfiles : ActividadBase() {
	private lateinit var p: Pantalla
	private val perfiles by lazy { Perfiles(this) }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.ui_perfiles))
		construir()
	}

	private fun nombreApp(paquete: String): String = runCatching {
		packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, PackageManager.GET_META_DATA)).toString()
	}.getOrDefault(paquete)

	private fun construir() {
		p.vaciar()
		p.encabezado(getString(R.string.perf_con_nombre))
		p.parrafo(getString(R.string.perf_con_nombre_explicacion))
		for (n in perfiles.nombres(this)) {
			p.botonSecundario(n) {
				elegir(n, listOf(
					getString(R.string.perf_aplicar) to {
						perfiles.aplicar(n, this)
						ServicioTiresias.instancia?.recargarAjustes()
						avisar(getString(R.string.perfil_activo, n))
					},
					getString(R.string.perf_actualizar) to {
						perfiles.guardarComo(n, this)
						avisar(getString(R.string.perf_guardado, n))
					},
					getString(R.string.perf_borrar, n) to {
						confirmar(getString(R.string.perf_borrar_pregunta, n), getString(R.string.perf_borrar, n)) {
							perfiles.borrar(n, this)
							construir()
						}
					},
				))
			}
		}
		p.boton(getString(R.string.perf_nuevo)) {
			pedirTexto(getString(R.string.perf_nuevo), getString(R.string.perf_nombre)) { nombre ->
				if (nombre.isBlank()) return@pedirTexto
				perfiles.guardarComo(nombre, this)
				avisar(getString(R.string.perf_guardado, nombre))
				construir()
			}
		}

		p.encabezado(getString(R.string.perf_por_app))
		p.parrafo(getString(R.string.perf_por_app_explicacion))
		for ((paquete, perfil) in perfiles.apps().toSortedMap(compareBy { nombreApp(it) })) {
			p.botonSecundario(getString(R.string.perf_app_fila, nombreApp(paquete), describir(perfil))) { editarApp(paquete) }
		}
		p.boton(getString(R.string.perf_app_nuevo)) { elegirApp() }
	}

	private fun describir(perfil: Perfiles.PerfilApp): String = listOfNotNull(
		perfil.velocidad?.let { getString(R.string.valor_velocidad, it) },
		perfil.puntuacion?.let { getString(R.string.aj_puntuacion) },
		perfil.verbosidad?.let { getString(R.string.aj_verbosidad) },
		perfil.ecoTeclado?.let { getString(R.string.aj_eco) },
		perfil.gestos.takeIf { it.isNotEmpty() }?.let { resources.getQuantityString(R.plurals.perf_gestos, it.size, it.size) },
	).joinToString(", ").ifEmpty { getString(R.string.perf_sin_cambios) }

	private fun elegirApp() {
		val pm = packageManager
		val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
			.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
			.distinctBy { it.first }.sortedBy { it.second.lowercase() }
		elegirUno(getString(R.string.perf_app_elegir), apps.map { it.second }, -1) { i -> editarApp(apps[i].first) }
	}

	private fun editarApp(paquete: String) {
		val actual = perfiles.deApp(paquete) ?: Perfiles.PerfilApp()
		val app = nombreApp(paquete)
		val silenciada = paquete in Ajustes(this).appsSilenciadas
		elegir(app, listOf(
			getString(R.string.perf_app_velocidad) to {
				pedirTexto(getString(R.string.perf_app_velocidad), getString(R.string.perf_app_velocidad_pista), actual.velocidad?.toString().orEmpty(), numerico = true) { v ->
					perfiles.ponerApp(paquete, actual.copy(velocidad = v.toIntOrNull()?.coerceIn(Ajustes.VELOCIDAD_MIN, Ajustes.VELOCIDAD_MAX)))
					construir()
				}
			},
			getString(R.string.perf_app_puntuacion) to {
				val valores = resources.getStringArray(R.array.puntuacion_valores)
				elegirUno(getString(R.string.aj_puntuacion), resources.getStringArray(R.array.puntuacion_nombres).toList(), valores.indexOf(actual.puntuacion)) { i ->
					perfiles.ponerApp(paquete, actual.copy(puntuacion = valores[i]))
					construir()
				}
			},
			getString(R.string.perf_app_verbosidad) to {
				val valores = resources.getStringArray(R.array.verbosidad_valores)
				elegirUno(getString(R.string.aj_verbosidad), resources.getStringArray(R.array.verbosidad_todas).toList(), valores.indexOf(actual.verbosidad)) { i ->
					perfiles.ponerApp(paquete, actual.copy(verbosidad = valores[i]))
					construir()
				}
			},
			getString(R.string.perf_app_eco) to {
				val valores = resources.getStringArray(R.array.eco_valores)
				elegirUno(getString(R.string.aj_eco), resources.getStringArray(R.array.eco_nombres).toList(), valores.indexOf(actual.ecoTeclado)) { i ->
					perfiles.ponerApp(paquete, actual.copy(ecoTeclado = valores[i]))
					construir()
				}
			},
			getString(if (silenciada) R.string.perf_app_notif_si else R.string.perf_app_notif_no) to {
				Ajustes(this).silenciarApp(paquete, !silenciada)
				avisar(getString(if (silenciada) R.string.perf_app_notif_vuelven else R.string.perf_app_notif_calladas, app))
			},
			getString(R.string.perf_app_quitar, app) to {
				perfiles.ponerApp(paquete, null)
				construir()
			},
		))
	}
}
