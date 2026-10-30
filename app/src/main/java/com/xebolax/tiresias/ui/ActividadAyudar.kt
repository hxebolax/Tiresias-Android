package com.xebolax.tiresias.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.remoto.CodigoCorto
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import com.xebolax.tiresias.nucleo.remoto.MensajeAyuda
import com.xebolax.tiresias.remoto.Asistencia
import com.xebolax.tiresias.remoto.SesionAyudado
import com.xebolax.tiresias.remoto.SesionAyudante
import com.xebolax.tiresias.servicio.PuenteEscritorio
import com.xebolax.tiresias.servicio.ServicioTiresias
import java.util.Locale

/**
 * Ayudar a otra persona desde este móvil ([Asistencia]): se abre con su
 * invitación (de la notificación, compartida desde WhatsApp o Mensajes, o
 * pegada) o con su código corto. Arriba, el estado (región viva). Luego los
 * botones que mandan cosas al otro móvil, cada uno con lo que hace: moverse,
 * activar, ir atrás o a Inicio, la lista de sus elementos, escribir, abrir una
 * app o su información, abrir Ajustes o un enlace de la tienda, y todas las
 * acciones de Tiresias. Con Tiresias activo aquí, «Manejar con mis gestos»
 * manda los gestos propios al otro móvil. Lo que dice el otro móvil se oye con
 * la voz de Tiresias (algo más grave) o, sin él, con la voz del sistema.
 */
class ActividadAyudar : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var estado: TextView
	private var sesion: SesionAyudante? = null
	private var voz: TextToSpeech? = null
	private var ultimo: TextView? = null
	private val alCambiar: () -> Unit = { runOnUiThread { if (!isFinishing) refrescar() } }

	private val pedirRed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { empezar() }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.ayudar_titulo))
		if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
			pedirRed.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
		} else empezar()
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		empezar()
	}

	private fun empezar() {
		val existente = Asistencia.ayudante
		val texto = intent?.getStringExtra(Asistencia.EXTRA_INVITACION) ?: intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
		val inv = texto?.let { Invitacion.leer(it) }
		val corto = intent?.getStringExtra(EXTRA_CODIGO)?.let { CodigoCorto.normalizar(it) }
		val s = when {
			inv != null && inv.caducada(System.currentTimeMillis() / 1000) -> {
				avisar(getString(R.string.ayuda_invitacion_caducada))
				null
			}
			inv == null && corto == null && texto != null -> {
				avisar(getString(R.string.ayuda_sin_invitacion))
				null
			}
			Asistencia.ayudado?.activa == true -> {
				avisar(getString(R.string.ayudar_antes_termina))
				null
			}
			inv != null || corto != null -> Asistencia.empezarAyudante(this, inv, corto)
			else -> existente
		}
		if (s == null) {
			if (existente == null) {
				startActivity(Intent(this, ActividadAyudaRemota::class.java))
				finish()
				return
			}
		}
		sesion = s ?: existente
		prepararVoz()
		construir()
	}

	override fun onStart() {
		super.onStart()
		Asistencia.oyentes += alCambiar
	}

	override fun onStop() {
		Asistencia.oyentes -= alCambiar
		super.onStop()
	}

	override fun onDestroy() {
		sesion?.let { s ->
			if (s.alVoz != null) s.alVoz = null
			s.alElementos = null
			s.alApps = null
			s.alTexto = null
		}
		runCatching { voz?.shutdown() }
		voz = null
		super.onDestroy()
	}

	/** Sin Tiresias activo aquí (quien ayuda usa otro lector), lo que dice el otro móvil suena con la voz del sistema. */
	private fun prepararVoz() {
		val s = sesion ?: return
		if (ServicioTiresias.instancia != null) return
		if (voz == null) voz = TextToSpeech(applicationContext) { r -> if (r != TextToSpeech.SUCCESS) Registro.w(ETIQUETA, "Sin voz del sistema para la ayuda ($r)") }
		s.alVoz = { v ->
			val tts = voz
			if (tts != null) {
				v.idioma?.let { runCatching { tts.language = Locale.forLanguageTag(it) } }
				tts.setPitch(SesionAyudante.TONO_REMOTO / 100f)
				tts.speak(v.texto, if (v.interrumpir) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, null)
			}
		}
		s.alCallar = { voz?.stop() }
	}

	private fun textoEstado(): String {
		val s = sesion ?: return getString(R.string.ayuda_estado_nada)
		return when (s.estado) {
			SesionAyudante.Estado.CONECTANDO -> getString(if (s.porTor) R.string.ayudar_conectando_tor else R.string.ayudar_conectando, s.nombreOtro.ifBlank { getString(R.string.ayuda_alguien) })
			SesionAyudante.Estado.CONECTADA -> getString(R.string.ayudar_conectada, s.nombreOtro, s.comprobacion.toCharArray().joinToString(" "))
			SesionAyudante.Estado.CORTADA -> getString(R.string.ayudar_cortada)
			SesionAyudante.Estado.TERMINADA -> getString(when (s.motivoFin) {
				SesionAyudante.FIN_NO_LLEGA -> R.string.ayudar_no_llega
				SesionAyudado.FIN_RECHAZADA -> R.string.ayudar_rechazada
				SesionAyudante.FIN_OTRO, SesionAyudado.FIN_USUARIO -> R.string.ayudar_terminada_otro
				else -> R.string.ayudar_terminada
			})
		}
	}

	private var ultimaForma: Pair<SesionAyudante.Estado?, Boolean>? = null

	private fun refrescar() {
		val s = sesion
		val forma = s?.estado to Asistencia.controlando
		if (forma != ultimaForma) construir() else if (estado.text.toString() != textoEstado()) estado.text = textoEstado()
	}

	private fun construir() {
		val s = sesion
		ultimaForma = s?.estado to Asistencia.controlando
		p.vaciar()
		estado = p.estadoVivo(textoEstado())
		if (s == null || s.estado == SesionAyudante.Estado.TERMINADA) {
			p.boton(getString(R.string.cerrar)) { finish() }
			return
		}
		if (ServicioTiresias.instancia == null) p.parrafo(getString(R.string.ayudar_vidente))
		ultimo = p.parrafo(getString(R.string.ayudar_ultimo_vacio))
		s.alTexto = { lineas -> runOnUiThread { ultimo?.text = getString(R.string.ayudar_ultimo, lineas.joinToString("\n")) } }
		if (ServicioTiresias.instancia != null) {
			interruptor(R.string.ayudar_con_gestos, R.string.ayudar_con_gestos_res, Asistencia.controlando) { si ->
				if (si != Asistencia.controlando) ServicioTiresias.instancia?.hacer(Accion.AYUDA_CONTROLAR)
			}
		}
		p.encabezado(getString(R.string.ayudar_moverse))
		accion(R.string.ayudar_anterior, Accion.ANTERIOR)
		accion(R.string.ayudar_siguiente, Accion.SIGUIENTE)
		accion(R.string.ayudar_activar, Accion.ACTIVAR)
		accion(R.string.ayudar_mantener, Accion.MANTENER)
		accion(R.string.ayudar_repetir, Accion.REPETIR)
		accion(R.string.ayudar_donde, Accion.DONDE_ESTOY)
		accion(R.string.ayudar_leer, Accion.LEER_DESDE_ARRIBA)
		p.botonSecundario(getString(R.string.ayudar_elementos)) { pedirElementos() }
		p.encabezado(getString(R.string.ayudar_sistema))
		accion(R.string.ayudar_atras, Accion.ATRAS)
		accion(R.string.ayudar_inicio, Accion.INICIO)
		accion(R.string.ayudar_notificaciones, Accion.NOTIFICACIONES)
		accion(R.string.ayudar_recientes, Accion.RECIENTES)
		accion(R.string.ayudar_desplazar_abajo, Accion.DESPLAZAR_ADELANTE)
		accion(R.string.ayudar_desplazar_arriba, Accion.DESPLAZAR_ATRAS)
		p.encabezado(getString(R.string.ayudar_hacer))
		p.botonSecundario(getString(R.string.ayudar_escribir)) {
			pedirTexto(getString(R.string.ayudar_escribir), getString(R.string.ayudar_escribir_pista), multilinea = true) { texto ->
				if (texto.isNotEmpty()) mandar(MensajeAyuda.Escribir(texto))
			}
		}
		p.botonSecundario(getString(R.string.ayudar_abrir_app)) { pedirApps(informacion = false) }
		p.botonSecundario(getString(R.string.ayudar_info_app)) { pedirApps(informacion = true) }
		enlace(R.string.ayudar_ajustes_sistema, SesionAyudado.ENLACE_AJUSTES_SISTEMA)
		enlace(R.string.ayudar_ajustes_tiresias, SesionAyudado.ENLACE_AJUSTES_TIRESIAS)
		enlace(R.string.ayudar_accesibilidad, SesionAyudado.ENLACE_ACCESIBILIDAD)
		enlace(R.string.ayudar_wifi, SesionAyudado.ENLACE_WIFI)
		if (PuenteEscritorio.PUBLICADO) enlace(R.string.ayudar_escritorio_sistema, SesionAyudado.ENLACE_ESCRITORIO_SISTEMA)
		p.botonSecundario(getString(R.string.ayudar_tienda)) {
			pedirTexto(getString(R.string.ayudar_tienda), getString(R.string.ayudar_tienda_pista)) { texto ->
				if (texto.isNotEmpty()) mandar(MensajeAyuda.AbrirEnlace(s.nuevaPeticion(), "market://search?q=" + Uri.encode(texto)))
			}
		}
		p.botonSecundario(getString(R.string.ayudar_enlace)) {
			pedirTexto(getString(R.string.ayudar_enlace), getString(R.string.ayudar_enlace_pista)) { texto ->
				if (texto.isNotEmpty()) mandar(MensajeAyuda.AbrirEnlace(s.nuevaPeticion(), texto))
			}
		}
		p.botonSecundario(getString(R.string.ayudar_todas)) { todasLasAcciones() }
		p.encabezado(getString(R.string.ayudar_esta_ayuda))
		p.botonSecundario(getString(R.string.ayudar_ficha)) {
			if (s.ficha.isEmpty()) avisar(getString(R.string.ayudar_sin_ficha)) else mensaje(getString(R.string.ayudar_ficha), s.ficha.joinToString("\n"))
		}
		p.botonSecundario(getString(R.string.ayudar_decir)) {
			pedirTexto(getString(R.string.ayudar_decir), getString(R.string.ayudar_decir_pista), multilinea = true) { texto ->
				if (texto.isNotEmpty()) mandar(MensajeAyuda.Decir(texto))
			}
		}
		p.botonSecundario(getString(R.string.ayudar_que_llame)) { mandar(MensajeAyuda.Llamame) }
		p.botonSecundario(getString(if (s.silenciada) R.string.ayudar_voz_otra_vez else R.string.ayudar_silenciar)) {
			s.silenciada = !s.silenciada
			avisar(getString(if (s.silenciada) R.string.ayudar_silenciada else R.string.ayudar_con_voz))
			construir()
		}
		p.boton(getString(R.string.ayuda_terminar)) {
			confirmar(getString(R.string.ayudar_terminar_pregunta, s.nombreOtro), getString(R.string.ayuda_terminar)) {
				s.terminar(SesionAyudante.FIN_USUARIO)
				construir()
			}
		}
	}

	private fun mandar(m: MensajeAyuda) {
		if (sesion?.enviar(m) != true) avisar(getString(R.string.ayudar_sin_conexion))
	}

	private fun accion(texto: Int, a: Accion) {
		p.botonSecundario(getString(texto)) { mandar(MensajeAyuda.HacerAccion(a.name)) }
	}

	private fun enlace(texto: Int, enlace: String) {
		p.botonSecundario(getString(texto)) { sesion?.let { mandar(MensajeAyuda.AbrirEnlace(it.nuevaPeticion(), enlace)) } }
	}

	/** Pide la lista de elementos de la pantalla del otro móvil y deja elegir uno y qué hacer con él. */
	private fun pedirElementos() {
		val s = sesion ?: return
		val peticion = s.nuevaPeticion()
		s.alElementos = { m ->
			if (m.peticion == peticion) runOnUiThread {
				if (isFinishing) return@runOnUiThread
				if (m.lista.isEmpty()) avisar(getString(R.string.ayudar_sin_elementos))
				else MaterialAlertDialogBuilder(this)
					.setTitle(resources.getQuantityString(R.plurals.ayudar_elementos_titulo, m.lista.size, m.lista.size))
					.setItems(m.lista.map { it.texto }.toTypedArray()) { _, i -> usarElemento(m.lista[i].indice, m.lista[i].texto) }
					.setNegativeButton(R.string.cancelar, null)
					.show()
			}
		}
		mandar(MensajeAyuda.PedirElementos(peticion))
		avisar(getString(R.string.ayudar_pidiendo))
	}

	private fun usarElemento(indice: Int, texto: String) {
		val s = sesion ?: return
		elegir(texto, listOf(
			getString(R.string.ayudar_activar) to { mandar(MensajeAyuda.UsarElemento(s.nuevaPeticion(), indice, SesionAyudado.COMO_ACTIVAR)) },
			getString(R.string.ayudar_mantener) to { mandar(MensajeAyuda.UsarElemento(s.nuevaPeticion(), indice, SesionAyudado.COMO_MANTENER)) },
			getString(R.string.ayudar_ir_a) to { mandar(MensajeAyuda.UsarElemento(s.nuevaPeticion(), indice, SesionAyudado.COMO_ENFOCAR)) },
		))
	}

	private fun pedirApps(informacion: Boolean) {
		val s = sesion ?: return
		val peticion = s.nuevaPeticion()
		s.alApps = { m ->
			if (m.peticion == peticion) runOnUiThread {
				if (isFinishing) return@runOnUiThread
				if (m.nombres.isEmpty()) avisar(getString(R.string.ayudar_sin_apps))
				else MaterialAlertDialogBuilder(this)
					.setTitle(getString(if (informacion) R.string.ayudar_info_app else R.string.ayudar_abrir_app))
					.setItems(m.nombres.toTypedArray()) { _, i -> mandar(MensajeAyuda.AbrirApp(s.nuevaPeticion(), m.nombres[i], informacion)) }
					.setNegativeButton(R.string.cancelar, null)
					.show()
			}
		}
		mandar(MensajeAyuda.PedirApps(peticion))
		avisar(getString(R.string.ayudar_pidiendo))
	}

	private fun todasLasAcciones() {
		val acciones = Accion.entries.filter { it != Accion.NINGUNA && it !in SesionAyudado.PROHIBIDAS && it !in PuenteEscritorio.ACCIONES_OCULTAS }
		elegir(getString(R.string.ayudar_todas), acciones.map { a -> ActividadGestos.nombreAccion(this, a) to { mandar(MensajeAyuda.HacerAccion(a.name)) } })
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
			minHeight = dp(this@ActividadAyudar, 56)
			setOnCheckedChangeListener { _, si -> alCambiar(si) }
		}
		p.contenedor.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
			topMargin = dp(this@ActividadAyudar, 8)
		})
	}

	companion object {
		private const val ETIQUETA = "ActividadAyudar"
		const val EXTRA_CODIGO = "codigo_corto"
	}
}
