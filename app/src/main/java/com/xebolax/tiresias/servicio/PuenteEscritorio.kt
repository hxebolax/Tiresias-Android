package com.xebolax.tiresias.servicio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.escritorio.Contrato
import com.xebolax.tiresias.remoto.Asistencia
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Tipo
import com.xebolax.tiresias.voz.VozEnLlamadas

/**
 * Lo que el lector hace por el Escritorio de Tiresias, un APK aparte con la misma firma.
 * Aquí está la salida: pase lo que pase en el escritorio, el lector sigue vivo y puede
 * llevar a la persona a otro Inicio. También la prueba de 60 segundos al hacerlo
 * predeterminado, que cuenta el lector y no el escritorio que se está probando, y el
 * vigía del modo senior, que recuerda cómo volver, pero nunca encierra a nadie.
 */
object PuenteEscritorio {
	private const val ETIQUETA = "PuenteEscritorio"
	private const val VIGIA_MS = 5 * 60_000L
	private const val INICIO_MS = 4000L

	/** El escritorio aún no sale: mientras sea false, el lector no lo ofrece en ningún sitio. */
	const val PUBLICADO = false

	/** Las acciones que solo tienen sentido con el escritorio: no salen en ninguna lista ni por voz. */
	val ACCIONES_OCULTAS: Set<com.xebolax.tiresias.nucleo.Accion> = if (PUBLICADO) emptySet() else setOf(com.xebolax.tiresias.nucleo.Accion.ESCRITORIO_SISTEMA)

	fun instalado(c: Context): Boolean = try {
		c.packageManager.getPackageInfo(Contrato.PAQUETE_ESCRITORIO, 0)
		true
	} catch (e: PackageManager.NameNotFoundException) {
		false
	}

	/** Si [paquete] es el escritorio y lleva la misma firma que el lector. */
	fun deConfianza(c: Context, paquete: String): Boolean =
		paquete == Contrato.PAQUETE_ESCRITORIO && c.packageManager.checkSignatures(c.packageName, paquete) == PackageManager.SIGNATURE_MATCH

	/** El Inicio de ahora, o null si Android preguntaría. */
	fun inicioActual(c: Context): String? = runCatching {
		c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
	}.onFailure { Registro.w(ETIQUETA, "No se pudo saber el Inicio", it) }.getOrNull()

	fun esInicio(c: Context): Boolean = inicioActual(c) == Contrato.PAQUETE_ESCRITORIO

	/** Abre el inicio hablado del modo senior, si el escritorio está. */
	fun abrirSenior(c: Context): Boolean {
		if (!instalado(c)) return false
		return try {
			c.startActivity(Intent().setComponent(ComponentName(Contrato.PAQUETE_ESCRITORIO, Contrato.ACTIVIDAD_SENIOR)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
			true
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo abrir el inicio hablado", e)
			false
		}
	}

	/** Otro Inicio que no sea el escritorio ni el de arranque; null si no hay. */
	private fun otroInicio(c: Context): ComponentName? = c.packageManager
		.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
		.map { it.activityInfo }
		.firstOrNull { it.packageName != Contrato.PAQUETE_ESCRITORIO && !it.name.endsWith(".FallbackHome") }
		?.let { ComponentName(it.packageName, it.name) }

	/**
	 * Saca a la persona del escritorio: le pide que deje de ser Inicio y abre el del
	 * sistema directamente. Sirve aunque el escritorio esté roto: el permiso del
	 * receptor es de firma y abrir el otro no depende de él.
	 */
	fun salirAlSistema(c: Context): Boolean {
		runCatching {
			c.sendBroadcast(Intent(Contrato.RECIBIR_SALIDA).setPackage(Contrato.PAQUETE_ESCRITORIO), Contrato.PERMISO_ESCRITORIO)
		}.onFailure { Registro.w(ETIQUETA, "No se pudo avisar al escritorio", it) }
		val otro = otroInicio(c) ?: return false
		return try {
			c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(otro).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			true
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo abrir el otro Inicio", e)
			false
		}
	}

	// --- La prueba de 60 segundos -------------------------------------------------------------------

	private var pruebaEnCurso: Runnable? = null

	fun empezarPrueba(lector: Lector, segundos: Int) {
		confirmarPrueba(lector)
		val fin = Runnable {
			pruebaEnCurso = null
			salirAlSistema(lector.servicio)
			lector.decir(lector.textos.getString(R.string.escritorio_prueba_fallida), Modo.INTERRUMPIR, Tipo.SISTEMA)
		}
		pruebaEnCurso = fin
		lector.manejador.postDelayed(fin, segundos.coerceIn(10, 300) * 1000L)
	}

	fun confirmarPrueba(lector: Lector) {
		pruebaEnCurso?.let { lector.manejador.removeCallbacks(it) }
		pruebaEnCurso = null
	}

	// --- Vigilar ------------------------------------------------------------------------------------

	/** Tras pulsar Inicio: si el escritorio es el Inicio y no aparece, se dice cómo salir. */
	fun vigilarInicio(lector: Lector) {
		val c = lector.servicio
		if (!esInicio(c)) return
		lector.manejador.postDelayed({
			val delante = runCatching { lector.servicio.rootInActiveWindow?.packageName?.toString() }.getOrNull()
			if (delante == null) lector.decir(lector.textos.getString(R.string.escritorio_no_responde), Modo.ENCOLAR, Tipo.SISTEMA)
		}, INICIO_MS)
	}

	/** Al encender la pantalla: si el escritorio era el Inicio y ya no lo es, se dice una vez. */
	fun mirarRol(lector: Lector) {
		val a = Ajustes(lector.servicio)
		val era = a.p.getBoolean(Claves.ESCRITORIO_ERA_INICIO, false)
		val es = esInicio(lector.servicio)
		if (era != es) a.p.edit().putBoolean(Claves.ESCRITORIO_ERA_INICIO, es).apply()
		if (era && !es && instalado(lector.servicio)) lector.decir(lector.textos.getString(R.string.escritorio_rol_perdido), Modo.ENCOLAR, Tipo.SISTEMA)
	}

	private var vigia: Runnable? = null

	/**
	 * Ha cambiado la app de delante: en el modo senior, si la persona se queda en otra
	 * app sin hacer nada, se le recuerda una vez cómo volver. No se hace durante una
	 * llamada ni con ayuda remota, y nunca se la saca de donde esté.
	 */
	fun alCambiarApp(lector: Lector, paquete: String) {
		vigia?.let { lector.manejador.removeCallbacks(it) }
		vigia = null
		val c = lector.servicio
		if (!Ajustes(c).modoSenior || paquete == Contrato.PAQUETE_ESCRITORIO || !instalado(c)) return
		val aviso = Runnable {
			vigia = null
			val ocupada = VozEnLlamadas.enLlamada(c) || Asistencia.ayudado?.conectada == true || lector.bloqueado()
			if (!ocupada) lector.decir(lector.textos.getString(R.string.escritorio_vigia), Modo.ENCOLAR, Tipo.SISTEMA)
		}
		vigia = aviso
		lector.manejador.postDelayed(aviso, VIGIA_MS)
	}

	/** Cuántas notificaciones hay por app, como JSON. Nunca su texto. */
	fun recuentos(): String {
		val activas = runCatching { OyenteAvisos.actual?.activeNotifications }
			.onFailure { Registro.w(ETIQUETA, "No se pudieron contar los avisos", it) }.getOrNull().orEmpty()
		val porApp = activas.filter { it.isClearable }.groupingBy { it.packageName }.eachCount()
		return org.json.JSONObject(porApp as Map<*, *>).toString()
	}

	/** El SDK que hace falta para ofrecerlo: en versiones anteriores no se puede probar hoy. */
	val puedeSerInicio get() = Build.VERSION.SDK_INT >= 29
}
