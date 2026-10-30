package com.xebolax.tiresias.remoto

import android.content.Context
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ia.Secretos
import com.xebolax.tiresias.nucleo.remoto.Emparejar
import com.xebolax.tiresias.nucleo.remoto.Pareja

/**
 * Quién puede ayudar a este móvil y a quién ayuda este móvil. Las parejas se guardan
 * cifradas ([Secretos]) y solo salen de aquí sus pruebas, nunca el secreto.
 *
 * Es la defensa contra el guion del falso soporte técnico: con alguien en la lista, una
 * invitación no basta para entrar. Una persona nueva necesita que se pida fuera de una
 * llamada y esperar 24 horas ([ESPERA_NUEVO_S]), el tiempo de hablarlo con la familia. La
 * primera de todas entra sin espera: si no, la familia se quedaría sin ayuda el primer día.
 */
object Confianza {
	private const val ETIQUETA = "Confianza"
	private const val PERSONAS = "ayuda_personas"
	private const val AYUDADOS = "ayuda_ayudados"
	const val ESPERA_NUEVO_S = 24 * 3600L
	private const val VALE_NUEVO_S = 7 * 24 * 3600L
	private const val MAXIMO_HISTORIAL = 20

	/** Las personas que pueden ayudar a este móvil. */
	fun personas(c: Context): List<Pareja> = leer(c, PERSONAS)

	/** Las personas a las que ayuda este móvil. */
	fun ayudados(c: Context): List<Pareja> = leer(c, AYUDADOS)

	fun anadirPersona(c: Context, p: Pareja) = guardar(c, PERSONAS, personas(c).filter { it.id != p.id } + p)
	fun anadirAyudado(c: Context, p: Pareja) = guardar(c, AYUDADOS, ayudados(c).filter { it.id != p.id } + p)
	fun quitarPersona(c: Context, id: String) = guardar(c, PERSONAS, personas(c).filter { it.id != id })
	fun quitarAyudado(c: Context, id: String) = guardar(c, AYUDADOS, ayudados(c).filter { it.id != id })

	private fun leer(c: Context, clave: String): List<Pareja> = try {
		Emparejar.deJson(Secretos(c).leer(clave))
	} catch (e: Exception) {
		Registro.w(ETIQUETA, "No se pudo leer la lista de confianza", e)
		emptyList()
	}

	private fun guardar(c: Context, clave: String, lista: List<Pareja>) {
		try {
			Secretos(c).guardar(clave, Emparejar.aJson(lista))
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo guardar la lista de confianza", e)
		}
	}

	/** Empieza la espera para dejar entrar a una persona nueva. */
	fun pedirNuevo(c: Context) {
		Ajustes(c).p.edit().putLong(Claves.AYUDA_NUEVO_DESDE, System.currentTimeMillis() / 1000 + ESPERA_NUEVO_S).apply()
	}

	fun cancelarNuevo(c: Context) {
		Ajustes(c).p.edit().remove(Claves.AYUDA_NUEVO_DESDE).apply()
	}

	/** Desde cuándo podrá entrar una persona nueva (segundos Unix); 0, si no se ha pedido. */
	fun nuevoDesde(c: Context): Long {
		val desde = Ajustes(c).p.getLong(Claves.AYUDA_NUEVO_DESDE, 0L)
		return if (desde > 0 && System.currentTimeMillis() / 1000 > desde + VALE_NUEVO_S) 0L else desde
	}

	/** Si puede entrar alguien que no está en la lista (se le preguntará igualmente a la persona). */
	fun puedeEntrarNuevo(c: Context): Boolean {
		val a = Ajustes(c)
		if (!a.ayudaSoloConfianza || personas(c).isEmpty()) return true
		val desde = nuevoDesde(c)
		return desde > 0 && System.currentTimeMillis() / 1000 >= desde
	}

	/** Apunta una ayuda: quién, cuándo y cuánto. Nunca lo que había en la pantalla. */
	fun apuntar(c: Context, linea: String) {
		val a = Ajustes(c)
		val lineas = listOf(linea) + historial(c)
		a.p.edit().putString(Claves.AYUDA_HISTORIAL, lineas.take(MAXIMO_HISTORIAL).joinToString("\n")).apply()
	}

	fun historial(c: Context): List<String> =
		Ajustes(c).p.getString(Claves.AYUDA_HISTORIAL, "").orEmpty().split('\n').filter { it.isNotBlank() }
}
