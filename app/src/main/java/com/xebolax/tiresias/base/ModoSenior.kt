package com.xebolax.tiresias.base

import android.content.Context
import com.xebolax.tiresias.nucleo.Listas
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.nucleo.NivelVerbosidad
import org.json.JSONObject

/**
 * El modo senior: Tiresias para quien solo quiere lo básico (estudio de octubre
 * de 2026, en `docs/modo_senior.md`). Al ponerlo se guardan los ajustes que
 * cambia, para dejarlos como estaban al quitarlo:
 *
 * - Los gestos del modo senior ([MapaGestos.SENIOR]): deslizar en línea recta
 *   con un dedo, el doble toque y las teclas de volumen; nada en ángulo ni con
 *   varios dedos.
 * - El menú de Tiresias con pocas cosas ([Listas.MENU_SENIOR]).
 * - Lo que se dice, corto (verbosidad baja), y las listas que dan la vuelta (como
 *   un teléfono BlindShell).
 * - Al desbloquear y al encender la pantalla, cómo está todo (hora, batería,
 *   avisos).
 * - Un poco de espera al explorar, para que pasar el dedo de largo no lea todo.
 *
 * Lo que solo existe en el modo senior (leer solo las llamadas y los mensajes,
 * activar con un toque, el contacto de ayuda y el código) tiene sus ajustes en
 * su pantalla y no se toca al quitarlo.
 */
object ModoSenior {
	/** Los ajustes que el modo senior cambia, con su valor en el modo senior. */
	private fun valores(): Map<String, Any> = mapOf(
		Claves.GESTOS to MapaGestos.SENIOR.escribir(),
		Claves.MENU_TIRESIAS to Listas.escribir(Listas.MENU_SENIOR),
		Claves.NIVEL_VERBOSIDAD to NivelVerbosidad.BAJA.name,
		Claves.DAR_VUELTA to true,
		Claves.AL_DESBLOQUEAR to "estado",
		Claves.AL_ENCENDER to "hora",
		// Un número, como lo guarda su deslizador en Ajustes
		Claves.RETARDO_EXPLORACION to 150,
		Claves.ACTIVAR_AL_LEVANTAR to Ajustes.LEVANTAR_NO,
	)

	fun activo(c: Context): Boolean = Ajustes(c).modoSenior

	/** Pone el modo senior, guardando antes lo que había. */
	fun poner(c: Context) {
		val a = Ajustes(c)
		if (a.modoSenior) return
		val p = a.p
		val antes = JSONObject()
		for (clave in valores().keys) {
			val v = p.all[clave]
			antes.put(clave, if (v == null) JSONObject.NULL else JSONObject().put("tipo", tipoDe(v)).put("valor", v.toString()))
		}
		val e = p.edit().putString(Claves.SENIOR_ANTES, antes.toString())
		for ((clave, valor) in valores()) when (valor) {
			is Boolean -> e.putBoolean(clave, valor)
			is Int -> e.putInt(clave, valor)
			else -> e.putString(clave, valor.toString())
		}
		e.putBoolean(Claves.MODO_SENIOR, true).apply()
	}

	/** Quita el modo senior y deja los ajustes como estaban antes de ponerlo. */
	fun quitar(c: Context) {
		val a = Ajustes(c)
		if (!a.modoSenior) return
		val p = a.p
		val antes = runCatching { JSONObject(p.getString(Claves.SENIOR_ANTES, "{}") ?: "{}") }.getOrElse {
			Registro.w(ETIQUETA, "No se pudo leer lo que había antes del modo senior", it)
			JSONObject()
		}
		val e = p.edit()
		for (clave in valores().keys) {
			val o = antes.optJSONObject(clave)
			if (o == null) {
				e.remove(clave)
				continue
			}
			val v = o.optString("valor")
			when (o.optString("tipo")) {
				"logico" -> e.putBoolean(clave, v.toBoolean())
				"entero" -> e.putInt(clave, v.toIntOrNull() ?: 0)
				"largo" -> e.putLong(clave, v.toLongOrNull() ?: 0L)
				else -> e.putString(clave, v)
			}
		}
		e.remove(Claves.SENIOR_ANTES).putBoolean(Claves.MODO_SENIOR, false).apply()
	}

	private fun tipoDe(v: Any): String = when (v) {
		is Boolean -> "logico"
		is Int -> "entero"
		is Long -> "largo"
		else -> "texto"
	}

	/** El código que protege los ajustes, si hay modo senior y código. */
	fun protegido(c: Context): Boolean = Ajustes(c).let { it.modoSenior && it.seniorCodigo.isNotEmpty() } && System.currentTimeMillis() > desbloqueadoHasta

	/** Hasta cuándo no se vuelve a pedir el código (unos minutos tras decirlo bien). */
	@Volatile var desbloqueadoHasta = 0L

	/** Si [codigo] es el bueno; si lo es, no se vuelve a pedir en cinco minutos. */
	fun comprobar(c: Context, codigo: String): Boolean {
		val bueno = codigo.trim() == Ajustes(c).seniorCodigo
		if (bueno) desbloqueadoHasta = System.currentTimeMillis() + 5 * 60_000
		return bueno
	}

	private const val ETIQUETA = "ModoSenior"
}
