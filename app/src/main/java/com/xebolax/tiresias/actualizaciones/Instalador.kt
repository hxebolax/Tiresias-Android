package com.xebolax.tiresias.actualizaciones

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** En qué punto está una instalación. */
enum class FaseInstalacion { NADA, PREPARANDO, ESPERANDO_CONFIRMACION, BIEN, MAL }

/**
 * El estado de la última instalación pedida, para que la pantalla que la lanzó
 * diga el resultado en el mismo sitio. [confirmar] es el diálogo del sistema
 * que hay que abrir si Android no dejó abrirlo desde el receptor.
 */
data class EstadoInstalacion(
	val fase: FaseInstalacion = FaseInstalacion.NADA,
	val paquete: String? = null,
	val mensaje: String? = null,
	val confirmar: Intent? = null,
)

/** El estado compartido de las instalaciones (lo actualiza [ResultadoInstalacionReceiver]). */
object Instalaciones {
	private val estadoInterno = MutableStateFlow(EstadoInstalacion())
	val estado: StateFlow<EstadoInstalacion> = estadoInterno.asStateFlow()

	fun poner(estado: EstadoInstalacion) {
		estadoInterno.value = estado
	}

	/** Olvida el resultado ya mostrado. */
	fun limpiar() {
		estadoInterno.value = EstadoInstalacion()
	}
}

/** Un fallo antes de instalar, con un mensaje claro para el usuario. */
class ReinstalacionException(mensaje: String) : IOException(mensaje)

/**
 * Instala un APK descargado (las actualizaciones de Tiresias y del complemento
 * de Gemini Nano) con el instalador del sistema (PackageInstaller). Android
 * siempre pide confirmarlo y comprueba que la firma es la misma.
 *
 * Portado de Reinstalador de Rescate Accesible (mismo autor).
 */
class Instalador(contexto: Context) {
	private val app = contexto.applicationContext
	private val textos get() = Idioma.envolver(app)

	/** Android 8 o posterior pide permitir a la app instalar aplicaciones (una sola vez). */
	fun puedeInstalar(): Boolean = Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

	/** La pantalla del sistema para dar el permiso de instalar aplicaciones. */
	fun intentPermiso(): Intent =
		if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
		else Intent(Settings.ACTION_SECURITY_SETTINGS)

	suspend fun instalar(apks: List<File>, paquete: String) = withContext(Dispatchers.IO) {
		if (!puedeInstalar()) throw ReinstalacionException(textos.getString(R.string.instalar_sin_permiso))
		val instalador = app.packageManager.packageInstaller
		val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
			setAppPackageName(paquete)
			if (Build.VERSION.SDK_INT >= 26) setInstallReason(PackageManager.INSTALL_REASON_USER)
			// Android 14+: no se pide ser el dueño de las actualizaciones; la tienda que lo sea puede pedir otra confirmación
		}
		val id = instalador.createSession(parametros)
		try {
			instalador.openSession(id).use { sesion ->
				apks.forEachIndexed { i, apk ->
					ensureActive()
					val nombre = if (i == 0) "base.apk" else apk.name
					apk.inputStream().use { entrada ->
						sesion.openWrite(nombre, 0, apk.length()).use { salida ->
							entrada.copyTo(salida, 256 * 1024)
							sesion.fsync(salida)
						}
					}
				}
				val intent = Intent(app, ResultadoInstalacionReceiver::class.java).putExtra(EXTRA_PAQUETE, paquete)
				// Mutable: el instalador añade el estado al intent
				val banderas = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
				val pendiente = PendingIntent.getBroadcast(app, id, intent, banderas)
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.PREPARANDO, paquete))
				sesion.commit(pendiente.intentSender)
			}
		} catch (e: Exception) {
			runCatching { instalador.abandonSession(id) }
			throw e
		}
	}

	companion object {
		private const val ETIQUETA = "Instalador"
		const val EXTRA_PAQUETE = "com.xebolax.tiresias.PAQUETE"
	}
}

/**
 * Recibe el resultado del instalador: abre la confirmación que pide Android o
 * deja dicho en [Instalaciones] si salió bien o por qué falló, en lenguaje claro.
 */
class ResultadoInstalacionReceiver : BroadcastReceiver() {
	override fun onReceive(contexto: Context, intent: Intent) {
		val textos = Idioma.envolver(contexto)
		val paquete = intent.getStringExtra(Instalador.EXTRA_PAQUETE)
		val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
		val detalle = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
		when (estado) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				val confirmar = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
				else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.ESPERANDO_CONFIRMACION, paquete, textos.getString(R.string.instalar_confirma), confirmar))
				if (confirmar != null) {
					try {
						contexto.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
					} catch (e: Exception) {
						// En segundo plano Android puede no dejar abrirlo: la pantalla lo abrirá con «confirmar»
						Registro.w("Instalacion", "No se pudo abrir la confirmación del instalador", e)
					}
				}
			}
			PackageInstaller.STATUS_SUCCESS -> {
				Registro.i("Instalacion", "Instalado $paquete")
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.BIEN, paquete, textos.getString(R.string.instalar_bien)))
			}
			else -> {
				Registro.w("Instalacion", "Instalación de $paquete fallida: estado $estado, $detalle")
				val motivo = when (estado) {
					PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.instalar_error_cancelada
					PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.instalar_error_bloqueada
					PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.instalar_error_conflicto
					PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.instalar_error_incompatible
					PackageInstaller.STATUS_FAILURE_INVALID -> R.string.instalar_error_invalida
					PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.instalar_error_espacio
					else -> R.string.instalar_error_general
				}
				val texto = textos.getString(motivo) + if (detalle.isNotBlank()) " (" + detalle + ")" else ""
				Instalaciones.poner(EstadoInstalacion(FaseInstalacion.MAL, paquete, texto))
			}
		}
	}
}
