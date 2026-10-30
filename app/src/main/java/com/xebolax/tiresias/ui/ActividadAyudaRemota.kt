package com.xebolax.tiresias.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.materialswitch.MaterialSwitch
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.nucleo.remoto.CodigoCorto
import com.xebolax.tiresias.nucleo.remoto.Emparejar
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import com.xebolax.tiresias.remoto.Asistencia
import com.xebolax.tiresias.remoto.Confianza
import com.xebolax.tiresias.remoto.PeticionAyuda
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.VozEnLlamadas

/**
 * La ayuda remota entre dos móviles con Tiresias, para personas de total
 * confianza. Arriba, el estado (región viva). Luego «Pedir ayuda» (mandar la
 * invitación al contacto de ayuda, compartirla con quien se quiera o dar un
 * código corto para la misma wifi), «Ayudar a alguien» (con la invitación
 * copiada o su código) y los ajustes: el contacto de ayuda (el mismo que el
 * SOS del modo senior), por dónde se manda, el nombre de este móvil, los
 * límites y los permisos. Pensada para que la prepare un familiar.
 */
class ActividadAyudaRemota : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes
	private lateinit var estado: TextView
	private val alCambiar: () -> Unit = { runOnUiThread { if (!isFinishing) refrescar() } }

	private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
		avisar(getString(if (si) R.string.ayuda_permiso_si else R.string.ayuda_permiso_no))
		construir()
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.accion_ayuda_remota))
		construir()
	}

	override fun onStart() {
		super.onStart()
		Asistencia.oyentes += alCambiar
		refrescar()
	}

	override fun onStop() {
		Asistencia.oyentes -= alCambiar
		super.onStop()
	}

	private fun construir() {
		ultimaForma = forma()
		p.vaciar()
		estado = p.estadoVivo(textoEstado())
		p.parrafo(getString(R.string.ayuda_explicacion))

		p.encabezado(getString(R.string.ayuda_pedir_titulo))
		val ayudado = Asistencia.ayudado
		if (ayudado != null && ayudado.activa) {
			p.boton(getString(R.string.ayuda_terminar)) {
				Asistencia.terminarPorUsuario()
				construir()
			}
			if (!ayudado.conectada) {
				p.botonSecundario(getString(R.string.ayuda_compartir_otra_vez)) { PeticionAyuda.compartir(this, null, PeticionAyuda.texto(this, ayudado.invitacion)) }
				p.botonSecundario(getString(R.string.ayuda_ver_codigo_corto)) { verCodigoCorto(ayudado.codigoCorto) }
			}
		} else {
			val quien = ajustes.seniorNombreAyuda.ifBlank { ajustes.seniorContactoAyuda }
			if (quien.isNotBlank()) p.boton(getString(R.string.ayuda_pedir_a, quien)) { pedir(enviar = true) }
			p.botonSecundario(getString(R.string.ayuda_invitar)) { pedir(enviar = false) }
			p.botonSecundario(getString(R.string.ayuda_codigo_wifi)) { pedir(enviar = false, corto = true) }
		}

		p.encabezado(getString(R.string.ayuda_ayudar_titulo))
		val ayudante = Asistencia.ayudante
		if (ayudante != null) {
			p.boton(getString(R.string.ayuda_volver_a_ayudar, ayudante.nombreOtro)) { startActivity(Intent(this, ActividadAyudar::class.java)) }
		} else {
			p.parrafo(getString(R.string.ayuda_ayudar_explicacion))
			p.boton(getString(R.string.ayuda_pegar)) { pegarInvitacion() }
			p.botonSecundario(getString(R.string.ayuda_escribir_codigo)) { escribirCodigo() }
		}

		p.encabezado(getString(R.string.ayuda_confianza_titulo))
		p.parrafo(getString(R.string.ayuda_confianza_explicacion))
		val personas = Confianza.personas(this)
		for (q in personas) p.botonSecundario(getString(R.string.ayuda_quitar_persona, q.nombre)) {
			confirmar(getString(R.string.ayuda_quitar_pregunta, q.nombre), getString(R.string.ayuda_quitar)) {
				Confianza.quitarPersona(this, q.id)
				construir()
			}
		}
		if (personas.isNotEmpty()) botonPersonaNueva()
		val ayudados = Confianza.ayudados(this)
		if (ayudados.isNotEmpty()) p.encabezado(getString(R.string.ayuda_a_quien_ayudo))
		for (q in ayudados) {
			p.botonSecundario(getString(R.string.ayuda_ofrecer, q.nombre)) {
				PeticionAyuda.compartir(this, null, getString(R.string.ayuda_oferta_mensaje, Emparejar.oferta(q, System.currentTimeMillis() / 1000)))
			}
			p.botonSecundario(getString(R.string.ayuda_dejar_de_ayudar, q.nombre)) {
				confirmar(getString(R.string.ayuda_dejar_pregunta, q.nombre), getString(R.string.ayuda_quitar)) {
					Confianza.quitarAyudado(this, q.id)
					construir()
				}
			}
		}
		p.botonSecundario(getString(R.string.ayuda_historial)) {
			val h = Confianza.historial(this)
			mensaje(getString(R.string.ayuda_historial), if (h.isEmpty()) getString(R.string.ayuda_historial_vacio) else h.joinToString("\n"))
		}

		p.encabezado(getString(R.string.ayuda_ajustes_titulo))
		p.botonSecundario(textoContacto()) { cambiarContacto() }
		p.botonSecundario(getString(R.string.ayuda_metodo, nombreMetodo(ajustes.ayudaMetodo))) { cambiarMetodo() }
		p.botonSecundario(getString(R.string.ayuda_nombre_boton, ajustes.ayudaNombre.ifBlank { getString(R.string.ayuda_nombre_ninguno) })) { cambiarNombre() }
		interruptor(R.string.ayuda_preguntar, R.string.ayuda_preguntar_res, ajustes.ayudaPreguntar) { ajustes.p.edit().putBoolean(Claves.AYUDA_PREGUNTAR, it).apply() }
		interruptor(R.string.ayuda_solo_confianza, R.string.ayuda_solo_confianza_res, ajustes.ayudaSoloConfianza) { ajustes.p.edit().putBoolean(Claves.AYUDA_SOLO_CONFIANZA, it).apply() }
		interruptor(R.string.ayuda_tor, R.string.ayuda_tor_res, ajustes.ayudaTor) { ajustes.p.edit().putBoolean(Claves.AYUDA_TOR, it).apply() }
		interruptor(R.string.ayuda_router, R.string.ayuda_router_res, ajustes.ayudaRouter) { ajustes.p.edit().putBoolean(Claves.AYUDA_ROUTER, it).apply() }
		p.botonSecundario(getString(R.string.ayuda_segundo_boton, ajustes.ayudaNombre2.ifBlank { ajustes.ayudaContacto2 }.ifBlank { getString(R.string.ayuda_segundo_ninguno) })) { cambiarSegundo() }
		p.botonSecundario(getString(R.string.ayuda_minutos_boton, ajustes.ayudaMinutos)) { elegirMinutos() }
		p.botonSecundario(textoAviso()) { elegirAviso() }
		permisos()
		p.encabezado(getString(R.string.ayuda_limites_titulo))
		p.parrafo(getString(R.string.ayuda_limites))
	}

	private fun permisos() {
		if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
			p.estado(getString(R.string.ayuda_permiso_red), false) { pedirPermiso.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }
		}
		if (ajustes.ayudaMetodo == PeticionAyuda.SMS && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
			p.estado(getString(R.string.ayuda_permiso_sms), false) { pedirPermiso.launch(Manifest.permission.SEND_SMS) }
		}
		if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
			p.estado(getString(R.string.ayuda_permiso_avisos), false) { pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS) }
		}
	}

	private fun textoEstado(): String {
		val ayudado = Asistencia.ayudado
		val ayudante = Asistencia.ayudante
		return when {
			ayudado != null && ayudado.conectada -> getString(R.string.ayuda_estado_ayudando_te, ayudado.nombreAyudante)
			ayudado != null && ayudado.activa -> getString(if (ayudado.desdeFuera) R.string.ayuda_estado_esperando_fuera else R.string.ayuda_estado_esperando)
			ayudante != null && ayudante.conectada -> getString(R.string.ayuda_estado_ayudas, ayudante.nombreOtro)
			ayudante != null -> getString(R.string.ayuda_estado_conectando)
			else -> getString(R.string.ayuda_estado_nada)
		}
	}

	/** Lo que decide qué botones hay: si cambia, se rehace la pantalla; si no, solo el estado (que se anuncia solo). */
	private fun forma(): List<Boolean> = listOf(Asistencia.ayudado?.activa == true, Asistencia.ayudado?.conectada == true, Asistencia.ayudante != null)
	private var ultimaForma: List<Boolean>? = null

	private fun refrescar() {
		if (forma() != ultimaForma) construir() else if (estado.text.toString() != textoEstado()) estado.text = textoEstado()
	}

	/** Una persona nueva: hay que pedirlo fuera de una llamada y esperar 24 horas, para poder hablarlo con la familia. */
	private fun botonPersonaNueva() {
		val desde = Confianza.nuevoDesde(this)
		if (desde == 0L) {
			p.botonSecundario(getString(R.string.ayuda_persona_nueva)) {
				if (VozEnLlamadas.enLlamada(this)) mensaje(getString(R.string.ayuda_persona_nueva), getString(R.string.ayuda_en_llamada))
				else confirmar(getString(R.string.ayuda_persona_nueva_pregunta), getString(R.string.ayuda_persona_nueva_si)) {
					Confianza.pedirNuevo(this)
					construir()
				}
			}
		} else {
			val cuando = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(desde * 1000))
			p.parrafo(getString(R.string.ayuda_persona_nueva_desde, cuando))
			p.botonSecundario(getString(R.string.ayuda_persona_nueva_cancelar)) {
				Confianza.cancelarNuevo(this)
				construir()
			}
		}
	}

	private fun cambiarSegundo() {
		pedirTexto(getString(R.string.ayuda_segundo_numero), getString(R.string.ayuda_segundo_numero_pista), ajustes.ayudaContacto2) { numero ->
			ajustes.ayudaContacto2 = numero.trim()
			if (numero.isBlank()) {
				ajustes.ayudaNombre2 = ""
				construir()
			} else pedirTexto(getString(R.string.ayuda_segundo_nombre), getString(R.string.ayuda_segundo_nombre_pista), ajustes.ayudaNombre2) { nombre ->
				ajustes.ayudaNombre2 = nombre.trim()
				construir()
			}
		}
	}

	private fun pedir(enviar: Boolean, corto: Boolean = false) {
		val servicio = ServicioTiresias.instancia
		if (servicio == null) {
			avisar(getString(R.string.ayuda_sin_tiresias))
			return
		}
		// Invitar a mano durante una llamada es el guion del falso soporte técnico
		if (!enviar && VozEnLlamadas.enLlamada(this)) {
			mensaje(getString(R.string.ayuda_invitar), getString(R.string.ayuda_en_llamada))
			return
		}
		avisar(getString(R.string.ayuda_preparando))
		if (enviar) {
			Asistencia.pedirAyuda(this)
			return
		}
		Asistencia.empezarAyudado(servicio) { inv ->
			runOnUiThread {
				val s = Asistencia.ayudado
				when {
					inv == null || s == null -> avisar(getString(R.string.ayuda_no_empieza))
					corto -> verCodigoCorto(s.codigoCorto)
					else -> PeticionAyuda.compartir(this, null, PeticionAyuda.texto(this, inv))
				}
				construir()
			}
		}
	}

	private fun verCodigoCorto(codigo: String) {
		mensaje(getString(R.string.ayuda_codigo_wifi), getString(R.string.ayuda_codigo_wifi_texto, CodigoCorto.agrupado(codigo).split(' ').joinToString(", ") { it.toCharArray().joinToString(" ") }))
	}

	private fun pegarInvitacion() {
		val texto = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull().orEmpty()
		val inv = Invitacion.leer(texto)
		when {
			inv == null -> avisar(getString(R.string.ayuda_sin_invitacion))
			inv.caducada(System.currentTimeMillis() / 1000) -> avisar(getString(R.string.ayuda_invitacion_caducada))
			else -> ayudar(inv.codigo(), null)
		}
	}

	private fun escribirCodigo() {
		pedirTexto(getString(R.string.ayuda_escribir_codigo), getString(R.string.ayuda_escribir_codigo_pista)) { texto ->
			val corto = CodigoCorto.normalizar(texto)
			val inv = Invitacion.leer(texto)
			when {
				inv != null && inv.caducada(System.currentTimeMillis() / 1000) -> avisar(getString(R.string.ayuda_invitacion_caducada))
				inv != null -> ayudar(inv.codigo(), null)
				corto != null -> ayudar(null, corto)
				else -> avisar(getString(R.string.ayuda_codigo_mal))
			}
		}
	}

	private fun ayudar(invitacion: String?, corto: String?) {
		startActivity(Intent(this, ActividadAyudar::class.java).apply {
			invitacion?.let { putExtra(Asistencia.EXTRA_INVITACION, it) }
			corto?.let { putExtra(ActividadAyudar.EXTRA_CODIGO, it) }
		})
	}

	// --- Ajustes ----------------------------------------------------------------------------------

	private fun textoContacto(): String {
		val numero = ajustes.seniorContactoAyuda
		val quien = when {
			numero.isEmpty() -> getString(R.string.senior_contacto_ninguno)
			ajustes.seniorNombreAyuda.isEmpty() -> numero
			else -> getString(R.string.senior_contacto_nombre_numero, ajustes.seniorNombreAyuda, numero)
		}
		return getString(R.string.ayuda_contacto, quien)
	}

	private fun cambiarContacto() {
		pedirTexto(getString(R.string.senior_contacto_nombre), getString(R.string.senior_contacto_nombre_pista), ajustes.seniorNombreAyuda) { nombre ->
			pedirTexto(getString(R.string.senior_contacto_numero), getString(R.string.senior_contacto_numero_pista), ajustes.seniorContactoAyuda, telefono = true) { numero ->
				ajustes.seniorNombreAyuda = nombre
				ajustes.seniorContactoAyuda = numero
				avisar(getString(if (numero.isEmpty()) R.string.senior_contacto_quitado else R.string.senior_contacto_puesto))
				construir()
			}
		}
	}

	private val metodos = listOf(PeticionAyuda.WHATSAPP, PeticionAyuda.SMS, PeticionAyuda.COMPARTIR)

	private fun nombreMetodo(m: String): String = getString(when (m) {
		PeticionAyuda.SMS -> R.string.ayuda_metodo_sms
		PeticionAyuda.COMPARTIR -> R.string.ayuda_metodo_compartir
		else -> R.string.ayuda_metodo_whatsapp
	})

	private fun cambiarMetodo() {
		elegirUno(getString(R.string.ayuda_metodo_titulo), metodos.map { nombreMetodo(it) }, metodos.indexOf(ajustes.ayudaMetodo).coerceAtLeast(0)) { i ->
			ajustes.ayudaMetodo = metodos[i]
			if (metodos[i] == PeticionAyuda.SMS && checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) pedirPermiso.launch(Manifest.permission.SEND_SMS)
			else avisar(getString(R.string.ayuda_metodo, nombreMetodo(metodos[i])))
			construir()
		}
	}

	private fun cambiarNombre() {
		pedirTexto(getString(R.string.ayuda_nombre_titulo), getString(R.string.ayuda_nombre_pista), ajustes.ayudaNombre) { nombre ->
			ajustes.ayudaNombre = nombre.take(40)
			construir()
		}
	}

	private fun elegirMinutos() {
		val opciones = listOf(15, 30, 60, 120)
		elegirUno(getString(R.string.ayuda_minutos_titulo), opciones.map { resources.getQuantityString(R.plurals.ayuda_minutos, it, it) }, opciones.indexOf(ajustes.ayudaMinutos)) { i ->
			ajustes.p.edit().putString(Claves.AYUDA_MINUTOS, opciones[i].toString()).apply()
			construir()
		}
	}

	private fun textoAviso(): String = if (ajustes.ayudaAviso == 0) getString(R.string.ayuda_aviso_nunca) else getString(R.string.ayuda_aviso_boton, ajustes.ayudaAviso)

	private fun elegirAviso() {
		val opciones = listOf(2, 5, 10, 0)
		elegirUno(getString(R.string.ayuda_aviso_titulo), opciones.map { if (it == 0) getString(R.string.ayuda_aviso_no) else resources.getQuantityString(R.plurals.ayuda_minutos, it, it) }, opciones.indexOf(ajustes.ayudaAviso)) { i ->
			ajustes.p.edit().putString(Claves.AYUDA_AVISO, opciones[i].toString()).apply()
			construir()
		}
	}

	/** Un interruptor de una sola parada, con su explicación dentro. */
	private fun interruptor(nombre: Int, explicacion: Int, marcado: Boolean, alCambiar: (Boolean) -> Unit) {
		val texto = SpannableStringBuilder(getString(nombre)).append("\n")
		val inicio = texto.length
		texto.append(getString(explicacion))
		texto.setSpan(RelativeSizeSpan(0.85f), inicio, texto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		val vista = MaterialSwitch(this).apply {
			text = texto
			isChecked = marcado
			minHeight = dp(this@ActividadAyudaRemota, 56)
			setOnCheckedChangeListener { _, si -> alCambiar(si) }
		}
		p.contenedor.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
			topMargin = dp(this@ActividadAyudaRemota, 8)
		})
	}
}
