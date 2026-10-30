package com.xebolax.tiresias.ui

import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ia.Secretos
import com.xebolax.tiresias.nucleo.remoto.Nvda
import com.xebolax.tiresias.remoto.ClienteNvda
import com.xebolax.tiresias.remoto.SuperficieControlPc
import com.xebolax.tiresias.remoto.SuperficiePc
import com.xebolax.tiresias.servicio.ServicioTiresias
import org.json.JSONArray
import org.json.JSONObject

/** El PC al que está conectado el móvil (como mucho uno), para la pantalla y la superficie braille. */
object ControlPc {
	@Volatile var cliente: ClienteNvda? = null
	@Volatile var host = ""
	@Volatile var puerto = Nvda.PUERTO
	@Volatile var clave = ""

	/** Al bloquear el móvil, lo elegido: desconectar (de fábrica), callar la voz del PC o seguir. */
	fun alPantalla(c: android.content.Context, encendida: Boolean) {
		val cl = cliente ?: return
		if (encendida) return
		when (Ajustes(c).nvdaAlBloquear) {
			"seguir" -> Unit
			"silenciar" -> cl.silenciado = true
			else -> cl.terminar()
		}
	}
}

/**
 * Manejar un PC con NVDA o con Tiresias para Windows desde el móvil (Can
 * Talk). Sin conectar: los equipos guardados, conectar a uno nuevo (servidor,
 * puerto y clave, o un enlace nvdaremote:// pegado). Conectado: poner la
 * superficie braille, mandar texto o lo copiado, callar la voz del PC,
 * guardar el equipo y desconectar. El estado va en una región viva.
 */
class ActividadControlPc : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes
	private lateinit var estado: TextView

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.accion_control_pc))
		construir()
	}

	override fun onResume() {
		super.onResume()
		construir()
	}

	private fun textoEstado(): String {
		val cl = ControlPc.cliente ?: return getString(R.string.nvda_estado_nada)
		return when (cl.estado) {
			ClienteNvda.Estado.CONECTANDO -> getString(R.string.nvda_estado_conectando, ControlPc.host)
			ClienteNvda.Estado.CONECTADO -> getString(R.string.nvda_estado_conectado, ControlPc.host)
			ClienteNvda.Estado.SIN_PC -> getString(R.string.nvda_estado_sin_pc)
			ClienteNvda.Estado.TERMINADO -> cl.error ?: getString(R.string.nvda_estado_nada)
		}
	}

	private var ultimo: ClienteNvda.Estado? = null

	private fun construir() {
		val cl = ControlPc.cliente
		ultimo = cl?.estado
		p.vaciar()
		estado = p.estadoVivo(textoEstado())
		p.parrafo(getString(R.string.nvda_explicacion))
		if (cl != null && cl.estado != ClienteNvda.Estado.TERMINADO) {
			p.boton(getString(R.string.pc_poner_pantalla)) { ponerPantalla() }
			p.botonSecundario(getString(R.string.nvda_poner_superficie)) { ponerSuperficie() }
			p.botonSecundario(getString(R.string.nvda_enviar_texto)) {
				pedirTexto(getString(R.string.nvda_enviar_texto), getString(R.string.nvda_enviar_texto_pista), multilinea = true) { texto ->
					if (texto.isNotEmpty()) cl.pegarTexto(texto)
				}
			}
			p.botonSecundario(getString(R.string.nvda_pegar_copiado)) {
				val texto = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull()
				if (texto.isNullOrEmpty()) avisar(getString(R.string.portapapeles_vacio_historial)) else cl.pegarTexto(texto)
			}
			p.botonSecundario(getString(if (cl.silenciado) R.string.nvda_menu_voz_si else R.string.nvda_menu_voz_no)) {
				cl.silenciado = !cl.silenciado
				avisar(getString(if (cl.silenciado) R.string.nvda_voz_callada else R.string.nvda_voz_oida))
				construir()
			}
			p.botonSecundario(getString(R.string.nvda_guardar)) { guardarEquipo() }
			p.boton(getString(R.string.nvda_menu_desconectar)) {
				cl.terminar()
				ServicioTiresias.instancia?.lectorActual?.superficie?.let { if (it.modo is SuperficiePc || it.modo is SuperficieControlPc) it.cerrar() }
				construir()
			}
			aprender()
			return
		}
		p.encabezado(getString(R.string.nvda_equipos))
		val equipos = equipos()
		if (equipos.isEmpty()) p.parrafo(getString(R.string.nvda_sin_equipos))
		for (e in equipos) {
			p.botonSecundario(getString(R.string.nvda_equipo_fila, e.optString("nombre"), e.optString("host"))) { elegirEquipo(e) }
		}
		p.encabezado(getString(R.string.nvda_nuevo))
		p.boton(getString(R.string.nvda_conectar_nuevo)) { pedirDatos() }
		p.botonSecundario(getString(R.string.nvda_pegar_enlace)) { pegarEnlace() }
		aprender()
	}

	/** El tutorial, la lista de gestos y la práctica sin PC: siempre a mano, con o sin conexión. */
	private fun aprender() {
		p.encabezado(getString(R.string.pc_aprender))
		p.parrafo(getString(R.string.pc_aprender_texto))
		p.boton(getString(R.string.pc_tutorial)) { startActivity(android.content.Intent(this, ActividadTutorialPc::class.java)) }
		p.botonSecundario(getString(R.string.pc_lista_gestos)) {
			startActivity(android.content.Intent(this, ActividadTutorialPc::class.java).putExtra("leccion", ActividadTutorialPc.LECCION_GESTOS))
		}
		p.botonSecundario(getString(R.string.pc_practicar)) {
			val lector = ServicioTiresias.instancia?.lectorActual
			if (lector == null) avisar(getString(R.string.nvda_sin_tiresias)) else lector.superficie.abrir(SuperficieControlPc(lector, null, practica = true))
		}
	}

	private fun equipos(): List<JSONObject> = runCatching {
		val a = JSONArray(ajustes.nvdaEquipos.ifBlank { "[]" })
		(0 until a.length()).map { a.getJSONObject(it) }
	}.getOrElse {
		Registro.w(ETIQUETA, "Los equipos guardados no se pueden leer", it)
		emptyList()
	}

	private fun guardarEquipos(lista: List<JSONObject>) {
		ajustes.nvdaEquipos = JSONArray().apply { lista.forEach { put(it) } }.toString()
	}

	private fun elegirEquipo(e: JSONObject) {
		val id = e.optString("id")
		elegir(e.optString("nombre"), listOf(
			getString(R.string.nvda_conectar) to {
				val clave = Secretos(this).leer("nvda_$id")
				if (clave.isEmpty()) pedirClave(e.optString("host"), e.optInt("puerto", Nvda.PUERTO))
				else conectar(e.optString("host"), e.optInt("puerto", Nvda.PUERTO), clave, null)
			},
			getString(R.string.nvda_quitar_equipo) to {
				confirmar(getString(R.string.nvda_quitar_pregunta, e.optString("nombre")), getString(R.string.nvda_quitar_equipo)) {
					guardarEquipos(equipos().filter { it.optString("id") != id })
					Secretos(this).guardar("nvda_$id", "")
					construir()
				}
			},
		))
	}

	private fun pedirDatos() {
		pedirTexto(getString(R.string.nvda_servidor), getString(R.string.nvda_servidor_pista), ControlPc.host.ifBlank { "nvdaremote.com" }) { servidor ->
			Nvda.enlace(servidor)?.let { return@pedirTexto conectar(it.host, it.puerto, it.clave, null) }
			val (host, puerto) = separar(servidor)
			// Sin servidor no hay a dónde conectar: se dice y se vuelve a pedir
			if (host.isBlank()) {
				avisar(getString(R.string.nvda_falta_servidor), mostrar = true)
				pedirDatos()
				return@pedirTexto
			}
			pedirClave(host, puerto)
		}
	}

	/** Pide la clave para [host]; vacía, avisa y la vuelve a pedir (cancelar sí deja salir). */
	private fun pedirClave(host: String, puerto: Int) {
		pedirTexto(getString(R.string.nvda_clave), getString(R.string.nvda_clave_pista)) { clave ->
			if (clave.isBlank()) {
				avisar(getString(R.string.nvda_falta_clave), mostrar = true)
				pedirClave(host, puerto)
			} else conectar(host, puerto, clave.trim(), null)
		}
	}

	/** «servidor» o «servidor:puerto». */
	private fun separar(texto: String): Pair<String, Int> {
		val t = texto.trim()
		val i = t.lastIndexOf(':')
		if (i > 0 && t.indexOf(':') == i) t.substring(i + 1).toIntOrNull()?.let { return t.substring(0, i) to it }
		return t to Nvda.PUERTO
	}

	private fun pegarEnlace() {
		val texto = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull().orEmpty()
		val e = Nvda.enlace(texto)
		if (e == null) avisar(getString(R.string.nvda_sin_enlace)) else conectar(e.host, e.puerto, e.clave, null)
	}

	private fun conectar(host: String, puerto: Int, clave: String, huella: String?) {
		ControlPc.cliente?.terminar()
		val cl = ClienteNvda(applicationContext)
		ControlPc.cliente = cl
		ControlPc.host = host
		ControlPc.puerto = puerto
		ControlPc.clave = clave
		cl.alCambiar = { runOnUiThread { if (!isFinishing) refrescar(cl) } }
		cl.alCertificado = { h -> runOnUiThread { if (!isFinishing) preguntarCertificado(host, puerto, clave, h) } }
		cl.conectar(host, puerto, clave, huella)
		construir()
	}

	private fun refrescar(cl: ClienteNvda) {
		if (cl !== ControlPc.cliente) return
		if (cl.estado != ultimo) {
			construir()
			// Al entrar el PC, la pantalla de control (o la superficie braille, si se ha elegido) se pone sola: es para lo que se conecta
			if (cl.estado == ClienteNvda.Estado.CONECTADO && ultimoPuesto !== cl) {
				ultimoPuesto = cl
				if (ajustes.nvdaPantalla == Ajustes.NVDA_PANTALLA_BRAILLE) ponerSuperficie() else ponerPantalla()
			}
		} else estado.text = textoEstado()
	}

	private var ultimoPuesto: ClienteNvda? = null

	private fun preguntarCertificado(host: String, puerto: Int, clave: String, huella: String) {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.nvda_certificado_titulo)
			.setMessage(getString(R.string.nvda_certificado_texto, host, huella))
			.setPositiveButton(R.string.nvda_fiarse_siempre) { _, _ ->
				ClienteNvda(applicationContext).guardarHuella(host, puerto, huella)
				conectar(host, puerto, clave, huella)
			}
			.setNeutralButton(R.string.nvda_fiarse_una_vez) { _, _ -> conectar(host, puerto, clave, huella) }
			.setNegativeButton(R.string.cancelar, null)
			.show()
	}

	private fun guardarEquipo() {
		pedirTexto(getString(R.string.nvda_nombre_equipo), getString(R.string.nvda_nombre_equipo_pista), ControlPc.host) { nombre ->
			val id = java.util.UUID.randomUUID().toString()
			val e = JSONObject().put("id", id).put("nombre", nombre.ifBlank { ControlPc.host }).put("host", ControlPc.host).put("puerto", ControlPc.puerto)
			confirmar(getString(R.string.nvda_guardar_clave_pregunta), getString(R.string.nvda_guardar_clave)) {
				Secretos(this).guardar("nvda_$id", ControlPc.clave)
				avisar(getString(R.string.nvda_guardado))
			}
			guardarEquipos(equipos().filter { it.optString("host") != ControlPc.host || it.optInt("puerto") != ControlPc.puerto } + e)
			avisar(getString(R.string.nvda_guardado))
		}
	}

	private fun ponerSuperficie() {
		val cl = ControlPc.cliente ?: return
		val lector = ServicioTiresias.instancia?.lectorActual
		if (lector == null) {
			avisar(getString(R.string.nvda_sin_tiresias))
			return
		}
		lector.superficie.abrir(SuperficiePc(lector, cl) {})
	}

	private fun ponerPantalla() {
		val cl = ControlPc.cliente ?: return
		val lector = ServicioTiresias.instancia?.lectorActual
		if (lector == null) {
			avisar(getString(R.string.nvda_sin_tiresias))
			return
		}
		lector.superficie.abrir(SuperficieControlPc(lector, cl))
	}

	private companion object {
		const val ETIQUETA = "ControlPc"
	}
}
