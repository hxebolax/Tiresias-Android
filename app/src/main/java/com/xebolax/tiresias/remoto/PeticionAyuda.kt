package com.xebolax.tiresias.remoto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.braille.BuscarEnviar
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.remoto.Invitacion
import java.util.Locale

/**
 * Manda la invitación de ayuda al contacto de ayuda, sola, con el botón de
 * pedir ayuda: por WhatsApp (Tiresias abre el chat con el texto puesto y pulsa
 * Enviar), por SMS (con el permiso de mandar SMS; si no, se abre Mensajes con
 * el texto) o con la hoja de compartir de Android. Lo que se manda es solo el
 * texto de la invitación: el nombre, los minutos que vale y su código.
 */
object PeticionAyuda {
	private const val ETIQUETA = "PeticionAyuda"
	const val WHATSAPP = "whatsapp"
	const val SMS = "sms"
	const val COMPARTIR = "compartir"
	private val APPS_WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
	private const val INTENTOS_ENVIAR = 16
	private const val ESPERA_ENVIAR_MS = 600L

	/** El mensaje: «Ana necesita ayuda con el móvil... Código: TIR1...». */
	fun texto(c: Context, inv: Invitacion): String =
		Idioma.envolver(c).getString(R.string.ayuda_mensaje, inv.nombre, SesionAyudado.MINUTOS_INVITACION, inv.codigo())

	/** Manda [inv] por [metodo] al contacto de ayuda (en el hilo del lector). */
	fun enviar(c: Context, lector: Lector, inv: Invitacion, metodo: String) {
		val a = lector.ajustes
		enviarA(c, lector, inv, metodo, a.seniorContactoAyuda, a.seniorNombreAyuda)
	}

	/** Manda [inv] por [metodo] a [telefono]. Si WhatsApp no está o no se deja, prueba por SMS antes de nada más. */
	fun enviarA(c: Context, lector: Lector, inv: Invitacion, metodo: String, telefono: String, nombre: String) {
		val numero = telefono.filter { it.isDigit() || it == '+' }
		val quien = nombre.ifBlank { numero }
		val mensaje = texto(c, inv)
		val hecho = when {
			numero.isEmpty() -> false
			metodo == WHATSAPP -> whatsapp(c, lector, numero, quien, mensaje)
			metodo == SMS -> sms(c, lector, numero, quien, mensaje)
			else -> false
		}
		if (!hecho) compartir(c, lector, mensaje)
	}

	/** La hoja de compartir con el mensaje: para mandarlo a quien se quiera, como se quiera. */
	fun compartir(c: Context, lector: Lector?, mensaje: String) {
		try {
			val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, mensaje)
			val t = Idioma.envolver(c)
			c.startActivity(Intent.createChooser(i, t.getString(R.string.ayuda_compartir_titulo)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			lector?.decir(R.string.ayuda_compartir_elige)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo compartir la invitación", e)
			lector?.decir(R.string.ayuda_no_enviada)
		}
	}

	/** El número con el prefijo del país, sin «+», como lo quiere WhatsApp. */
	fun numeroInternacional(c: Context, numero: String): String {
		val tm = c.getSystemService(TelephonyManager::class.java)
		val pais = listOf(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country).firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT) ?: "ES"
		val e164 = runCatching { PhoneNumberUtils.formatNumberToE164(numero, pais) }.getOrNull()
		return (e164 ?: numero).filter { it.isDigit() }.removePrefix("00")
	}

	private fun whatsapp(c: Context, lector: Lector, numero: String, quien: String, mensaje: String): Boolean {
		val paquete = APPS_WHATSAPP.firstOrNull { p -> runCatching { c.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false) } ?: return false
		val uri = Uri.parse("https://api.whatsapp.com/send?phone=" + numeroInternacional(c, numero) + "&text=" + Uri.encode(mensaje))
		return try {
			c.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(paquete).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			lector.decir(R.string.ayuda_enviando_whatsapp)
			pulsarEnviar(c, lector, paquete, numero, quien, mensaje, 0)
			true
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo abrir WhatsApp", e)
			false
		}
	}

	/** Espera a que WhatsApp abra el chat con el texto y pulsa Enviar (como haría la persona). */
	private fun pulsarEnviar(c: Context, lector: Lector, paquete: String, numero: String, quien: String, mensaje: String, intento: Int) {
		lector.manejador.postDelayed({
			val raiz = runCatching { lector.servicio.rootInActiveWindow }.getOrNull()
			val boton = if (raiz?.packageName?.toString() == paquete) buscarEnviar(raiz, paquete) else null
			when {
				boton != null && runCatching { boton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false) ->
					lector.decir(lector.textos.getString(R.string.ayuda_enviada, quien))
				intento + 1 < INTENTOS_ENVIAR -> pulsarEnviar(c, lector, paquete, numero, quien, mensaje, intento + 1)
				// Si el botón no aparece, que no se quede la petición sin salir
				c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED -> sms(c, lector, numero, quien, mensaje)
				else -> lector.decir(R.string.ayuda_pulsa_enviar)
			}
		}, ESPERA_ENVIAR_MS)
	}

	private fun buscarEnviar(raiz: AccessibilityNodeInfo, paquete: String): AccessibilityNodeInfo? {
		runCatching { raiz.findAccessibilityNodeInfosByViewId("$paquete:id/send") }.getOrNull()
			?.firstOrNull { it.isVisibleToUser && it.isEnabled }?.let { return it }
		val campo = runCatching { raiz.findAccessibilityNodeInfosByViewId("$paquete:id/entry").firstOrNull() }.getOrNull()
			?: runCatching { raiz.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
			?: return null
		// Sin texto en el campo, el botón es el del micrófono: no se pulsa
		if (campo.text.isNullOrBlank()) return null
		return BuscarEnviar.buscar(raiz, campo)
	}

	private fun sms(c: Context, lector: Lector, numero: String, quien: String, mensaje: String): Boolean {
		if (c.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
			try {
				val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
				val partes = sm.divideMessage(mensaje)
				sm.sendMultipartTextMessage(numero, null, partes, null, null)
				lector.decir(lector.textos.getString(R.string.ayuda_enviada_sms, quien))
				return true
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo mandar el SMS", e)
			}
		}
		// Sin el permiso: Mensajes con el texto puesto, para que la persona pulse Enviar
		return try {
			c.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numero")).putExtra("sms_body", mensaje).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			lector.decir(R.string.ayuda_pulsa_enviar)
			true
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No hay app de mensajes", e)
			false
		}
	}
}
