package com.xebolax.tiresias.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.PistaAudio

/**
 * Qué hace cada aparato Bluetooth con la voz (Can Talk): «Altavoz» (la música
 * en el altavoz de casa y las voces incluidas en el móvil), «Auriculares» o lo
 * del sistema. Salen los aparatos emparejados (con el permiso de dispositivos
 * cercanos) y los conectados ahora. Cada fila, una sola parada: «JBL Flip:
 * altavoz».
 */
class ActividadReglasBluetooth : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes

	private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { construir() }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		protegerConCodigo()
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.bt_titulo))
		construir()
	}

	private fun conPermiso(): Boolean = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

	/** Los aparatos: identidad → nombre. */
	@SuppressLint("MissingPermission")
	private fun aparatos(): Map<String, String> {
		val lista = LinkedHashMap<String, String>()
		if (conPermiso()) try {
			getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.forEach { d ->
				val clase = d.bluetoothClass?.majorDeviceClass
				if (clase == null || clase == BluetoothClass.Device.Major.AUDIO_VIDEO || clase == BluetoothClass.Device.Major.UNCATEGORIZED) {
					lista["address:" + d.address.uppercase()] = d.name ?: d.address
				}
			}
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudieron leer los aparatos emparejados", e)
		}
		runCatching { getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull()?.forEach { d ->
			if (d.type !in PistaAudio.BLUETOOTH) return@forEach
			val id = PistaAudio.identidad(d) ?: return@forEach
			if (id !in lista) lista[id] = d.productName?.toString()?.ifBlank { null } ?: id.substringAfter(':')
		}
		// Los que tienen regla y ya no se ven también salen, para poder quitarla
		for (id in ajustes.reglasBluetooth.keys) if (id !in lista) lista[id] = id.substringAfter(':')
		return lista
	}

	private fun nombreRegla(r: String?): String = getString(when (r) {
		PistaAudio.REGLA_ALTAVOZ -> R.string.bt_regla_altavoz
		PistaAudio.REGLA_AURICULARES -> R.string.bt_regla_auriculares
		else -> R.string.bt_regla_sistema
	})

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.bt_explicacion))
		if (!conPermiso()) p.boton(getString(R.string.bt_permiso)) { pedirPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT) }
		val todos = aparatos()
		if (todos.isEmpty()) {
			p.parrafo(getString(R.string.bt_ninguno))
			return
		}
		val reglas = ajustes.reglasBluetooth
		for ((id, nombre) in todos) {
			p.botonSecundario(getString(R.string.bt_fila, nombre, nombreRegla(reglas[id]))) { cambiar(id, nombre) }
		}
	}

	private fun cambiar(id: String, nombre: String) {
		val valores = listOf("", PistaAudio.REGLA_ALTAVOZ, PistaAudio.REGLA_AURICULARES)
		val actual = valores.indexOf(ajustes.reglasBluetooth[id] ?: "").coerceAtLeast(0)
		elegirUno(nombre, valores.map { nombreRegla(it.ifEmpty { null }) }, actual) { i ->
			val nuevas = ajustes.reglasBluetooth.toMutableMap()
			if (valores[i].isEmpty()) nuevas.remove(id) else nuevas[id] = valores[i]
			ajustes.reglasBluetooth = nuevas
			ServicioTiresias.instancia?.recargarAjustes()
			avisar(getString(R.string.bt_fila, nombre, nombreRegla(valores[i].ifEmpty { null })))
			construir()
		}
	}

	private companion object {
		const val ETIQUETA = "ReglasBluetooth"
	}
}
