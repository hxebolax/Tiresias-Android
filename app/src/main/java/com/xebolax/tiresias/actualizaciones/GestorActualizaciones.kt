/*
 * Portado del gestor de actualizaciones de Rescate Accesible y Lector Universal (mismo autor),
 * con la instalación por PackageInstaller.
 */
package com.xebolax.tiresias.actualizaciones

import android.os.SystemClock
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.conEspera
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Interfaz de las actualizaciones: comprobación al abrir la app (como mucho cada
 * pocas horas) o a mano, aviso con las novedades, descarga con progreso y el
 * instalador del sistema.
 *
 * Se crea al construir la actividad (registra el regreso desde el permiso
 * «Instalar aplicaciones desconocidas»).
 */
class GestorActualizaciones(private val actividad: AppCompatActivity) {

	private companion object {
		const val CADA_MS = 6 * 60 * 60 * 1000L
		const val ETIQUETA = "Actualizaciones"
	}

	// Perezoso: el gestor se crea antes de que la actividad tenga contexto
	private val prefs by lazy { Ajustes(actividad) }
	private val reinstalador by lazy { Instalador(actividad) }
	private var comprobando = false
	private var descarga: Job? = null
	/** APK descargado que espera al permiso de instalar. */
	private var pendiente: File? = null
	private var paquetePendiente: String? = null

	private val permisoInstalar = actividad.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		val apk = pendiente ?: return@registerForActivityResult
		if (reinstalador.puedeInstalar()) instalar(apk, paquetePendiente ?: actividad.packageName)
		else actividad.mensaje(actividad.getString(R.string.act_titulo), actividad.getString(R.string.act_sin_permiso))
	}

	/** Lo que dice NOVEDADES.txt (dentro de la app) de [version], o null. */
	private fun novedadesDe(version: String): String? = try {
		val todo = actividad.assets.open("novedades.txt").bufferedReader().use { it.readText() }.replace("\r\n", "\n")
		val inicio = todo.indexOf("== $version\n").takeIf { it >= 0 } ?: return null
		val desde = inicio + "== $version\n".length
		val fin = todo.indexOf("\n== ", desde).takeIf { it >= 0 } ?: todo.length
		todo.substring(desde, fin).trim().takeIf { it.isNotEmpty() }
	} catch (e: java.io.IOException) {
		com.xebolax.tiresias.base.Registro.w("Actualizaciones", "No están las novedades dentro de la app", e)
		null
	}

	/** Al abrir la app: aviso si se acaba de actualizar y comprobación automática. */
	fun alIniciar() {
		val anterior = prefs.versionAnterior
		if (anterior in 1 until BuildConfig.VERSION_CODE) Actualizador.carpeta(actividad).listFiles()?.forEach { it.delete() }
		prefs.versionAnterior = BuildConfig.VERSION_CODE
		// Las novedades de esta versión, una vez, tras actualizar (el servicio ya apuntó la versión: va aparte; Backtalk 297)
		val vistas = prefs.p.getInt(com.xebolax.tiresias.base.Claves.NOVEDADES_VISTAS, 0)
		prefs.p.edit().putInt(com.xebolax.tiresias.base.Claves.NOVEDADES_VISTAS, BuildConfig.VERSION_CODE).apply()
		if (vistas in 1 until BuildConfig.VERSION_CODE || (vistas == 0 && anterior in 1 until BuildConfig.VERSION_CODE)) {
			val texto = novedadesDe(BuildConfig.VERSION_NAME)
			if (texto != null) actividad.mensaje(actividad.getString(R.string.act_novedades_titulo, BuildConfig.VERSION_NAME), texto)
			else actividad.avisar(actividad.getString(R.string.act_actualizada_a, BuildConfig.VERSION_NAME))
		}

		if (!prefs.buscarActualizaciones) return
		if (System.currentTimeMillis() - prefs.ultimaBusquedaActualizacion < CADA_MS) return
		actividad.lifecycleScope.launch {
			val v = try {
				Actualizador.versionPublicada(actividad)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Sin conexión al abrir: no se molesta al usuario
				Registro.w(ETIQUETA, "Comprobación automática sin respuesta", e)
				return@launch
			}
			prefs.ultimaBusquedaActualizacion = System.currentTimeMillis()
			if (Actualizador.esNueva(v) && v.version != prefs.versionOmitida) ofrecer(v, automatica = true)
		}
	}

	/** «Buscar actualizaciones ahora». */
	fun comprobarAMano() {
		if (comprobando || descarga?.isActive == true) return
		comprobando = true
		actividad.lifecycleScope.launch {
			try {
				// Ventana de espera: la respuesta del servidor puede tardar
				val v = actividad.conEspera(actividad.getString(R.string.act_buscar), actividad.getString(R.string.act_buscando)) {
					Actualizador.versionPublicada(actividad)
				}
				prefs.ultimaBusquedaActualizacion = System.currentTimeMillis()
				if (Actualizador.esNueva(v)) ofrecer(v, automatica = false)
				else actividad.mensaje(actividad.getString(R.string.act_titulo), actividad.getString(R.string.act_al_dia, BuildConfig.VERSION_NAME))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo comprobar si hay actualización", e)
				// Sin conexión: se dice claro, sin el texto técnico
				val motivo = if (e is IOException) actividad.getString(R.string.act_sin_conexion) else e.message.orEmpty()
				actividad.mensaje(actividad.getString(R.string.act_titulo), actividad.getString(R.string.act_error_comprobar, motivo))
			} finally {
				comprobando = false
			}
		}
	}

	private fun tamano(bytes: Long) = Formatter.formatShortFileSize(actividad, bytes)

	private fun ofrecer(v: VersionPublicada, automatica: Boolean) {
		if (actividad.isFinishing) return
		val texto = buildString {
			append(actividad.getString(R.string.act_texto, BuildConfig.VERSION_NAME, v.version))
			if (v.tamano > 0) append(" ").append(actividad.getString(R.string.act_tamano, tamano(v.tamano)))
			if (v.origen == OrigenVersion.GITHUB && prefs.origenActualizaciones == Ajustes.ORIGEN_AUTOMATICO) append(" ").append(actividad.getString(R.string.act_desde_github))
			if (v.notas.isNotBlank()) append("\n\n").append(actividad.getString(R.string.act_novedades)).append("\n").append(v.notas)
		}
		val dialogo = MaterialAlertDialogBuilder(actividad)
			.setTitle(actividad.getString(R.string.act_disponible, v.version))
			.setMessage(texto)
			.setPositiveButton(R.string.act_actualizar) { _, _ -> descargar(v) }
			.setNegativeButton(R.string.act_mas_tarde, null)
		if (automatica) dialogo.setNeutralButton(R.string.act_omitir) { _, _ -> prefs.versionOmitida = v.version }
		dialogo.show()
	}

	private fun descargar(v: VersionPublicada) =
		descargarConDialogo(actividad.getString(R.string.act_descargando_version, v.version), actividad.packageName) { progreso ->
			Actualizador.descargar(actividad, v, progreso)
		}

	/**
	 * Descarga e instala un complemento publicado con la versión (el de Gemini
	 * Nano). Las mismas comprobaciones que una actualización de Tiresias.
	 */
	fun instalarComplemento(id: String, nombre: String, paquete: String) =
		descargarConDialogo(actividad.getString(R.string.act_descargando_complemento, nombre), paquete) { progreso ->
			Actualizador.descargarComplemento(actividad, id, progreso).second
		}

	private fun descargarConDialogo(titulo: String, paquete: String, trabajo: suspend ((Long, Long) -> Unit) -> File) {
		val margen = dp(actividad, 20)
		val estado = TextView(actividad).apply {
			text = actividad.getString(R.string.act_descargando_inicio)
			// El porcentaje cambia sin parar: no se anuncia (TalkBack lo lee al tocarlo)
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		}
		val barra = LinearProgressIndicator(actividad).apply {
			max = 100
			isIndeterminate = true
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		val vista = LinearLayout(actividad).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(margen, margen / 2, margen, 0)
			addView(estado)
			addView(barra, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = margen / 2 })
		}
		val dialogo: AlertDialog = MaterialAlertDialogBuilder(actividad)
			.setTitle(titulo)
			.setView(vista)
			// Atrás cancela la descarga, como el botón (tocar fuera, no: se cancelaría sin querer)
			.setOnCancelListener { descarga?.cancel() }
			.setNegativeButton(R.string.cancelar) { _, _ -> descarga?.cancel() }
			.show()
			.also { it.setCanceledOnTouchOutside(false) }

		var ultimoRefresco = 0L
		descarga = actividad.lifecycleScope.launch {
			try {
				val apk = trabajo { leidos, total ->
					val ahora = SystemClock.uptimeMillis()
					if (ahora - ultimoRefresco >= 300 || leidos == total) {
						ultimoRefresco = ahora
						actividad.runOnUiThread {
							if (total > 0) {
								val pct = (leidos * 100 / total).toInt()
								barra.isIndeterminate = false
								barra.progress = pct
								estado.text = actividad.getString(R.string.act_descargando, pct, tamano(leidos), tamano(total))
							} else {
								estado.text = tamano(leidos)
							}
						}
					}
				}
				dialogo.dismiss()
				actividad.avisar(actividad.getString(R.string.act_descargada))
				prepararInstalacion(apk, paquete)
			} catch (e: CancellationException) {
				dialogo.dismiss()
				actividad.avisar(actividad.getString(R.string.act_cancelada))
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "Falló la descarga de la actualización", e)
				dialogo.dismiss()
				actividad.mensaje(actividad.getString(R.string.act_titulo), actividad.getString(R.string.act_error_descargar, e.message.orEmpty()))
			}
		}
	}

	/** Android 8+ pide permitir a la app instalar aplicaciones (una sola vez). */
	private fun prepararInstalacion(apk: File, paquete: String) {
		if (reinstalador.puedeInstalar()) return instalar(apk, paquete)
		pendiente = apk
		paquetePendiente = paquete
		MaterialAlertDialogBuilder(actividad)
			.setTitle(R.string.act_titulo)
			.setMessage(R.string.act_pedir_permiso)
			.setPositiveButton(R.string.act_abrir_ajustes) { _, _ -> permisoInstalar.launch(reinstalador.intentPermiso()) }
			.setNegativeButton(R.string.cancelar, null)
			.show()
	}

	private fun instalar(apk: File, paquete: String) {
		pendiente = null
		actividad.lifecycleScope.launch {
			try {
				Instalaciones.limpiar()
				reinstalador.instalar(listOf(apk), paquete)
				// Si sale bien, Android cierra la app para ponerle la versión nueva; aquí solo llega el fallo
				val fin = Instalaciones.estado.first { it.fase == FaseInstalacion.BIEN || it.fase == FaseInstalacion.MAL }
				if (fin.fase == FaseInstalacion.MAL) actividad.mensaje(actividad.getString(R.string.act_titulo), fin.mensaje.orEmpty())
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudo instalar la actualización", e)
				actividad.mensaje(actividad.getString(R.string.act_titulo), actividad.getString(R.string.act_error_descargar, e.message.orEmpty()))
			}
		}
	}
}
