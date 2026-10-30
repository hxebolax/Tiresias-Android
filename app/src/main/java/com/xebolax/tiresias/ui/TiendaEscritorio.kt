package com.xebolax.tiresias.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.lifecycleScope
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.actualizaciones.Actualizador
import com.xebolax.tiresias.actualizaciones.ComplementoPublicado
import com.xebolax.tiresias.actualizaciones.GestorActualizaciones
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.escritorio.Contrato
import com.xebolax.tiresias.servicio.PuenteEscritorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * El Escritorio de Tiresias en la tienda: es un APK aparte, publicado junto a la versión de
 * Tiresias con las mismas comprobaciones (tamaño, huella y la misma firma). Aquí se
 * instala, se actualiza, se abre y se quita.
 */
object TiendaEscritorio {
	private const val ETIQUETA = "TiendaEscritorio"
	const val ID = "escritorio"

	/** El nombre de la fila, con su estado. */
	fun fila(a: ActividadBase): String {
		val nombre = a.getString(R.string.tienda_escritorio)
		val v = versionInstalada(a) ?: return a.getString(R.string.tienda_escritorio_no, nombre)
		return a.getString(R.string.tienda_escritorio_si, nombre, v.first)
	}

	private fun versionInstalada(a: ActividadBase): Pair<String, Long>? = try {
		val i = a.packageManager.getPackageInfo(Contrato.PAQUETE_ESCRITORIO, 0)
		i.versionName.orEmpty() to (if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong())
	} catch (e: PackageManager.NameNotFoundException) {
		null
	}

	/** Las opciones del escritorio, según lo que haya instalado y lo publicado. */
	fun abrir(a: ActividadBase, gestor: GestorActualizaciones) {
		a.lifecycleScope.launch {
			val publicado = try {
				Actualizador.complemento(a, ID)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo saber la versión publicada del escritorio", e)
				null
			}
			mostrar(a, gestor, publicado)
		}
	}

	private fun mostrar(a: ActividadBase, gestor: GestorActualizaciones, publicado: ComplementoPublicado?) {
		val nombre = a.getString(R.string.tienda_escritorio)
		val instalada = versionInstalada(a)
		val opciones = ArrayList<Pair<String, () -> Unit>>()
		if (instalada == null) {
			if (publicado == null) {
				a.avisar(a.getString(R.string.tienda_escritorio_no_publicado))
				return
			}
			a.confirmar(a.getString(R.string.tienda_escritorio_explicacion), a.getString(R.string.tienda_escritorio_instalar)) {
				gestor.instalarComplemento(ID, nombre, Contrato.PAQUETE_ESCRITORIO)
			}
			return
		}
		opciones += a.getString(R.string.tienda_escritorio_abrir) to { abrirEscritorio(a) }
		if (publicado != null && publicado.versionCode > instalada.second) {
			opciones += a.getString(R.string.tienda_escritorio_actualizar) to { gestor.instalarComplemento(ID, nombre, Contrato.PAQUETE_ESCRITORIO) }
		}
		if (PuenteEscritorio.esInicio(a)) opciones += a.getString(R.string.accion_escritorio_sistema) to { PuenteEscritorio.salirAlSistema(a) }
		opciones += a.getString(R.string.tienda_escritorio_quitar) to {
			runCatching { a.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + Contrato.PAQUETE_ESCRITORIO))) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo desinstalar el escritorio", it) }
		}
		a.elegir(nombre, opciones)
	}

	private fun abrirEscritorio(a: ActividadBase) {
		runCatching { a.startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(Contrato.PAQUETE_ESCRITORIO, Contrato.ACTIVIDAD_INICIO))) }
			.onFailure {
				Registro.w(ETIQUETA, "No se pudo abrir el escritorio", it)
				a.avisar(a.getString(R.string.tienda_escritorio_no_abre))
			}
	}
}
