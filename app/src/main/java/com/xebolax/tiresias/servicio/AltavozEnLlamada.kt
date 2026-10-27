package com.xebolax.tiresias.servicio

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.os.OutcomeReceiver
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.PoliticaAltavoz
import com.xebolax.tiresias.nucleo.PoliticaAltavoz.Salida

/**
 * Pasa la llamada al altavoz al apartar el móvil de la oreja y al auricular al
 * volver a acercarlo, como VoiceOver en el iPhone (Backtalk 11, 18 y 196).
 *
 * Android solo enlaza este servicio durante las llamadas si Tiresias tiene el
 * permiso `MANAGE_ONGOING_CALLS`, que el usuario da una vez con adb (no hay otra
 * forma: Android no se lo da a los lectores de pantalla). No tiene pantalla de
 * llamada propia. Mira el sensor de proximidad solo mientras se oye una llamada
 * y el ajuste está puesto, y solo cambia la salida con Tiresias activo.
 *
 * Basado en SpeakerphoneInCallService de Backtalk (Copyright 2026 Backtalk
 * contributors, Apache License 2.0), con cambios.
 */
@RequiresApi(Build.VERSION_CODES.S)
class AltavozEnLlamada : InCallService() {
	private val principal = Handler(Looper.getMainLooper())
	private val politica = PoliticaAltavoz()
	private lateinit var sensores: SensorManager
	private var sensor: Sensor? = null
	private var lejos = 0f
	private var escuchando = false
	private var pendiente: Boolean? = null
	private val prefs: SharedPreferences by lazy { Almacen.protegido(this).getSharedPreferences(Ajustes.ARCHIVO, Context.MODE_PRIVATE) }

	private val alCambiarAjuste = SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
		if (clave == com.xebolax.tiresias.base.Claves.ALTAVOZ_AL_APARTAR) actualizar()
	}

	private val alCambiarLlamada = object : Call.Callback() {
		override fun onStateChanged(call: Call, state: Int) = actualizar()
	}

	private val alLeerSensor = object : SensorEventListener {
		override fun onSensorChanged(e: SensorEvent) {
			val enOreja = e.values[0] < lejos
			if (enOreja == pendiente) return
			// Se espera a que la lectura se mantenga: una mano que pasa por encima no cambia nada
			pendiente = enOreja
			principal.removeCallbacks(aplicar)
			principal.postDelayed(aplicar, ESPERA_MS)
		}

		override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
	}

	private val aplicar = Runnable {
		val enOreja = pendiente ?: return@Runnable
		if (ServicioTiresias.instancia == null) {
			// Sin Tiresias no se toca nada; la siguiente lectura vale cuando vuelva
			pendiente = null
			return@Runnable
		}
		val destino = politica.alLeer(enOreja, salidaActual()) ?: return@Runnable
		Registro.i(ETIQUETA, "Móvil ${if (enOreja) "en" else "lejos de"} la oreja: la llamada pasa a $destino")
		pasarA(destino)
	}

	/** El punto de audio de la llamada ahora y los que hay (Android 14 o posterior; aquí sin su tipo, que no existe antes). */
	private var puntoActual: Any? = null
	private var puntos: List<Any> = emptyList()

	/** Pasa la llamada a [destino]: con los puntos de audio en Android 14 o posterior, con la ruta antes. */
	private fun pasarA(destino: Salida) {
		if (Build.VERSION.SDK_INT >= 34) {
			val tipo = if (destino == Salida.ALTAVOZ) CallEndpoint.TYPE_SPEAKER else CallEndpoint.TYPE_EARPIECE
			val punto = puntos.filterIsInstance<CallEndpoint>().firstOrNull { it.endpointType == tipo } ?: return
			requestCallEndpointChange(punto, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
				override fun onResult(r: Void?) = Unit
				override fun onError(e: CallEndpointException) {
					Registro.w(ETIQUETA, "Android no dejó cambiar la salida de la llamada", e)
				}
			})
		} else {
			@Suppress("DEPRECATION")
			setAudioRoute(if (destino == Salida.ALTAVOZ) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
		}
	}

	@RequiresApi(34)
	override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
		puntoActual = callEndpoint
		politica.alCambiarSalida(salidaActual())
	}

	@RequiresApi(34)
	override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
		puntos = availableEndpoints.toList()
	}

	override fun onCreate() {
		super.onCreate()
		sensores = getSystemService(SensorManager::class.java)
		sensor = sensores.getDefaultSensor(Sensor.TYPE_PROXIMITY, true) ?: sensores.getDefaultSensor(Sensor.TYPE_PROXIMITY)
		lejos = sensor?.let { minOf(it.maximumRange, CERCA_CM) } ?: 0f
		prefs.registerOnSharedPreferenceChangeListener(alCambiarAjuste)
	}

	override fun onDestroy() {
		prefs.unregisterOnSharedPreferenceChangeListener(alCambiarAjuste)
		dejarDeEscuchar()
		super.onDestroy()
	}

	override fun onCallAdded(call: Call) {
		call.registerCallback(alCambiarLlamada, principal)
		actualizar()
	}

	override fun onCallRemoved(call: Call) {
		call.unregisterCallback(alCambiarLlamada)
		actualizar()
	}

	@Deprecated("Android 14 avisa con onCallEndpointChanged; esta vale para Android 12 y 13")
	override fun onCallAudioStateChanged(audioState: CallAudioState) {
		if (Build.VERSION.SDK_INT < 34) politica.alCambiarSalida(salidaActual())
	}

	/** Empieza o deja de mirar el sensor, según las llamadas y el ajuste. */
	private fun actualizar() {
		val oyendo = calls.any { it.details.state in ESTADOS_QUE_SE_OYEN }
		if (oyendo && prefs.getBoolean(com.xebolax.tiresias.base.Claves.ALTAVOZ_AL_APARTAR, false)) escuchar() else dejarDeEscuchar()
		if (calls.isEmpty()) politica.reiniciar()
	}

	private fun escuchar() {
		val s = sensor ?: return
		if (escuchando) return
		escuchando = true
		// El sensor dice su estado en cuanto se registra
		sensores.registerListener(alLeerSensor, s, SensorManager.SENSOR_DELAY_NORMAL, principal)
	}

	private fun dejarDeEscuchar() {
		if (!escuchando) return
		escuchando = false
		sensores.unregisterListener(alLeerSensor)
		principal.removeCallbacks(aplicar)
		pendiente = null
	}

	private fun salidaActual(): Salida {
		if (Build.VERSION.SDK_INT >= 34) {
			return when ((puntoActual as? CallEndpoint)?.endpointType) {
				CallEndpoint.TYPE_EARPIECE -> Salida.AURICULAR
				CallEndpoint.TYPE_SPEAKER -> Salida.ALTAVOZ
				else -> Salida.OTRA
			}
		}
		@Suppress("DEPRECATION")
		return when (callAudioState?.route) {
			CallAudioState.ROUTE_EARPIECE -> Salida.AURICULAR
			CallAudioState.ROUTE_SPEAKER -> Salida.ALTAVOZ
			else -> Salida.OTRA
		}
	}

	companion object {
		private const val ETIQUETA = "AltavozEnLlamada"
		/** Lo que tiene que durar una lectura para que la llamada cambie. */
		private const val ESPERA_MS = 300L
		/** Más cerca que esto, en centímetros, es «en la oreja». */
		private const val CERCA_CM = 5f
		private val ESTADOS_QUE_SE_OYEN = setOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE, Call.STATE_PULLING_CALL)
	}
}

/** El permiso para cambiar la salida de las llamadas (fuera del servicio, que solo existe en Android 12 o posterior). */
object PermisoAltavoz {
	private const val ETIQUETA = "AltavozEnLlamada"
	private const val OPERACION = "android:manage_ongoing_calls"

	/** La orden que da el permiso, para enseñarla al usuario. */
	fun ordenPermiso(c: Context): String = "adb shell appops set ${c.packageName} MANAGE_ONGOING_CALLS allow"

	/** Si Android deja a Tiresias cambiar la salida de las llamadas (Android 12 o posterior, con el permiso dado). */
	fun puedeControlar(c: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
		val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
		return try {
			ops.unsafeCheckOpNoThrow(OPERACION, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
		} catch (e: IllegalArgumentException) {
			Registro.w(ETIQUETA, "Android no conoce la operación de las llamadas", e)
			false
		}
	}
}
