package com.xebolax.tiresias.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.ModoSenior
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * El modo senior ([ModoSenior]): ponerlo o quitarlo, cómo se maneja y sus
 * opciones (leer solo llamadas y mensajes, un toque abre, el contacto de ayuda
 * y el código que protege los ajustes). Pensada para que la configure un
 * familiar: todo con una sola parada y los botones dicen lo que tienen puesto.
 */
class ActividadModoSenior : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes
	private lateinit var botonContacto: MaterialButton
	private lateinit var botonCodigo: MaterialButton

	private val pedirLlamar = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
		avisar(getString(if (concedido) R.string.senior_llamar_si else R.string.senior_llamar_no))
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.senior_titulo))
		construir()
		protegerConCodigo()
	}

	private fun construir() {
		p.parrafo(getString(R.string.senior_explicacion))
		interruptor(R.string.senior_poner, R.string.senior_poner_res, ajustes.modoSenior) { si ->
			if (si) ModoSenior.poner(this) else ModoSenior.quitar(this)
			ServicioTiresias.instancia?.recargarAjustes()
			avisar(getString(if (si) R.string.senior_puesto else R.string.senior_quitado))
		}
		p.encabezado(getString(R.string.senior_como))
		for (id in listOf(R.string.senior_como_1, R.string.senior_como_2, R.string.senior_como_3, R.string.senior_como_4, R.string.senior_como_5, R.string.senior_como_6)) {
			p.parrafo(getString(id))
		}
		p.encabezado(getString(R.string.senior_opciones))
		interruptor(R.string.senior_solo_mensajes, R.string.senior_solo_mensajes_res, ajustes.seniorSoloMensajes) { si ->
			ajustes.p.edit().putBoolean(Claves.SENIOR_SOLO_MENSAJES, si).apply()
			ServicioTiresias.instancia?.recargarAjustes()
		}
		interruptor(R.string.senior_toque_activa, R.string.senior_toque_activa_res, ajustes.seniorToqueActiva) { si ->
			ajustes.p.edit().putBoolean(Claves.SENIOR_TOQUE_ACTIVA, si).apply()
			ServicioTiresias.instancia?.recargarAjustes()
		}
		interruptor(R.string.senior_teclas_llamada, R.string.senior_teclas_llamada_res, ajustes.seniorTeclasLlamada) { si ->
			ajustes.p.edit().putBoolean(Claves.SENIOR_TECLAS_LLAMADA, si).apply()
			if (si && checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) pedirLlamar.launch(Manifest.permission.ANSWER_PHONE_CALLS)
		}
		interruptor(R.string.senior_sos_sms_ajuste, R.string.senior_sos_sms_ajuste_res, ajustes.seniorSosSms) { si ->
			ajustes.p.edit().putBoolean(Claves.SENIOR_SOS_SMS, si).apply()
			if (si) pedirSos.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_COARSE_LOCATION))
		}
		botonContacto = p.botonSecundario(textoContacto()) { cambiarContacto() }
		botonCodigo = p.botonSecundario(textoCodigo()) { cambiarCodigo() }
	}

	private val pedirSos = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
		if (r[Manifest.permission.SEND_SMS] != true) avisar(getString(R.string.senior_sos_sin_sms))
	}

	private fun textoContacto(): String {
		val numero = ajustes.seniorContactoAyuda
		val quien = when {
			numero.isEmpty() -> getString(R.string.senior_contacto_ninguno)
			ajustes.seniorNombreAyuda.isEmpty() -> numero
			else -> getString(R.string.senior_contacto_nombre_numero, ajustes.seniorNombreAyuda, numero)
		}
		return getString(R.string.senior_contacto, quien)
	}

	private fun textoCodigo(): String =
		getString(R.string.senior_codigo, getString(if (ajustes.seniorCodigo.isEmpty()) R.string.senior_codigo_no else R.string.senior_codigo_si))

	/** El nombre y luego el teléfono de quien ayuda; el teléfono vacío quita el contacto. */
	private fun cambiarContacto() {
		pedirTexto(getString(R.string.senior_contacto_nombre), getString(R.string.senior_contacto_nombre_pista), ajustes.seniorNombreAyuda) { nombre ->
			pedirTexto(getString(R.string.senior_contacto_numero), getString(R.string.senior_contacto_numero_pista), ajustes.seniorContactoAyuda, telefono = true) { numero ->
				ajustes.seniorNombreAyuda = nombre
				ajustes.seniorContactoAyuda = numero
				botonContacto.text = textoContacto()
				when {
					numero.isEmpty() -> avisar(getString(R.string.senior_contacto_quitado))
					checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED -> pedirLlamar.launch(Manifest.permission.CALL_PHONE)
					else -> avisar(getString(R.string.senior_contacto_puesto))
				}
			}
		}
	}

	/** El código de los ajustes: al menos cuatro cifras, o vacío para quitarlo. */
	private fun cambiarCodigo() {
		pedirTexto(getString(R.string.senior_codigo_poner), getString(R.string.senior_codigo_pista), numerico = true) { codigo ->
			if (codigo.isNotEmpty() && (codigo.length < 4 || codigo.any { !it.isDigit() })) {
				avisar(getString(R.string.senior_codigo_corto))
				return@pedirTexto
			}
			ajustes.seniorCodigo = codigo
			// Quien lo acaba de poner no tiene que decirlo otra vez enseguida
			ModoSenior.comprobar(this, codigo)
			botonCodigo.text = textoCodigo()
			avisar(getString(if (codigo.isEmpty()) R.string.senior_codigo_quitado else R.string.senior_codigo_puesto))
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
			minHeight = dp(this@ActividadModoSenior, 56)
			setOnCheckedChangeListener { _, si -> alCambiar(si) }
		}
		p.contenedor.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
			topMargin = dp(this@ActividadModoSenior, 8)
		})
	}
}
