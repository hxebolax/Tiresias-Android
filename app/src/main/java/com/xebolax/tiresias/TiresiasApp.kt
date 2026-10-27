package com.xebolax.tiresias

import android.app.Application
import com.xebolax.tiresias.accesibilidad.DeslizadorAjuste
import com.xebolax.tiresias.accesibilidad.MemoriaFoco
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Registro

/**
 * Arranque del proceso: registro técnico, foco que vuelve en todas las
 * pantallas de Tiresias y formato de los deslizadores de Ajustes.
 *
 * Puede arrancar antes de desbloquear el móvil (servicio de arranque directo):
 * aquí solo se usa el almacenamiento protegido del dispositivo.
 */
class TiresiasApp : Application() {
	override fun onCreate() {
		super.onCreate()
		Registro.iniciar(this)
		registerActivityLifecycleCallbacks(MemoriaFoco)
		DeslizadorAjuste.formatos[Claves.VELOCIDAD] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Claves.TONO] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Claves.VOLUMEN_VOZ] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Claves.VOLUMEN_SONIDOS] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Claves.INTENSIDAD_VIBRACION] = DeslizadorAjuste.porCiento
		DeslizadorAjuste.formatos[Claves.ESPERA_PISTAS] = DeslizadorAjuste.milisegundos
		DeslizadorAjuste.formatos[Claves.BRAILLE_MENSAJES] = DeslizadorAjuste.milisegundos
		DeslizadorAjuste.formatos[Claves.CONTINUA_INTERVALO] = DeslizadorAjuste.segundos
		DeslizadorAjuste.formatos[Claves.CONTINUA_MINUTOS] = DeslizadorAjuste.minutos
		DeslizadorAjuste.formatos[Claves.RETARDO_EXPLORACION] = DeslizadorAjuste.milisegundos
		Thread.setDefaultUncaughtExceptionHandler(manejadorFallos(Thread.getDefaultUncaughtExceptionHandler()))
	}

	/** Los cierres inesperados quedan en el registro técnico (sin datos privados) antes de que Android cierre la app. */
	private fun manejadorFallos(anterior: Thread.UncaughtExceptionHandler?) = Thread.UncaughtExceptionHandler { hilo, error ->
		Registro.e("Cierre", "Cierre inesperado en el hilo ${hilo.name}", error)
		anterior?.uncaughtException(hilo, error)
	}
}
