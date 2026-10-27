package com.xebolax.tiresias.api

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * La API para que otras apps (juegos, apps sin accesibilidad, herramientas)
 * hablen con Tiresias o escriban en la línea braille, con permiso del usuario.
 *
 * Es un servicio enlazado con un `Messenger` (acción `com.xebolax.tiresias.API`):
 * así se sabe de verdad qué app manda cada mensaje (`Message.sendingUid`), sin
 * fiarse de lo que diga. La primera vez que una app lo usa, Tiresias pregunta
 * al usuario en voz alta si la deja; lo que conteste se recuerda y se cambia en
 * Ajustes, Apps que usan Tiresias. Guía para desarrolladores: `docs/api.md`.
 *
 * Mensajes (`what`): [HABLAR] (texto, interrumpir), [CALLAR], [BRAILLE] (texto),
 * [ESTADO] y [ZONA_TOQUE_DIRECTO] (izquierda, arriba, derecha y abajo en
 * píxeles de la pantalla; todo a 0 la quita): los toques en esa zona van
 * directos a la app mientras está delante, como un juego o un piano.
 *
 * Una app puede hacer también de línea braille (un anotador, el programa del
 * fabricante): [LINEA_CONECTAR] con `celdas` y `replyTo` (ahí recibe
 * [LINEA_CELDAS] con los puntos de cada celda en `celdas`), [LINEA_TECLA] con
 * la `orden` («desplazar:adelante», «celda:3», «puntos:13», «accion:ATRAS»,
 * «espacio», «borrar», «intro») y [LINEA_DESCONECTAR]. Android 15 o posterior. Si el mensaje trae `replyTo`, se contesta con el mismo `what` y
 * [RESULTADO]: [HECHO], [SIN_PERMISO], [PREGUNTANDO] o [INACTIVO]. Nada de lo
 * que mandan las apps se guarda.
 */
class ServicioApi : Service() {
	private val ultimos = HashMap<Int, Long>()
	private val mensajero = Messenger(Handler(Looper.getMainLooper()) { m -> atender(m); true })

	override fun onBind(intent: Intent?): IBinder = mensajero.binder

	private fun atender(m: Message) {
		val uid = m.sendingUid
		val paquete = packageManager.getPackagesForUid(uid)?.firstOrNull()
		val resultado = when {
			paquete == null -> SIN_PERMISO
			// Sin pasar de 20 mensajes por segundo: una app no puede ahogar al lector
			!aTiempo(uid) -> HECHO
			else -> hacer(m, paquete)
		}
		m.replyTo?.let { r ->
			runCatching { r.send(Message.obtain(null, m.what).apply { data = Bundle().apply { putInt(RESULTADO, resultado) } }) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo contestar a $paquete", it) }
		}
	}

	private fun aTiempo(uid: Int): Boolean {
		val ahora = SystemClock.uptimeMillis()
		val antes = ultimos[uid] ?: 0L
		if (ahora - antes < 50) return false
		ultimos[uid] = ahora
		return true
	}

	private fun hacer(m: Message, paquete: String): Int {
		val s = ServicioTiresias.instancia ?: return INACTIVO
		val ajustes = Ajustes(this)
		val propia = paquete == packageName
		if (!propia && paquete !in ajustes.apiPermitidas) {
			if (paquete in ajustes.apiDenegadas) return SIN_PERMISO
			s.pedirPermisoApi(paquete)
			return PREGUNTANDO
		}
		val texto = m.data?.getString(TEXTO).orEmpty().take(MAXIMO)
		when (m.what) {
			HABLAR -> if (texto.isNotBlank()) s.decirDesdeApp(texto, m.data?.getBoolean(INTERRUMPIR, false) == true)
			CALLAR -> s.callarDesdeApp()
			BRAILLE -> if (texto.isNotBlank()) s.brailleDesdeApp(texto)
			ESTADO -> Unit
			LINEA_CONECTAR -> {
				val r = m.replyTo ?: return SIN_PERMISO
				val nombre = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString() }.getOrDefault(paquete)
				val ok = s.conectarLineaVirtual(nombre, m.data?.getInt(CELDAS) ?: 0) { puntos ->
					r.send(Message.obtain(null, LINEA_CELDAS).apply { data = Bundle().apply { putByteArray(CELDAS, puntos) } })
				}
				if (!ok) return INACTIVO
			}
			LINEA_TECLA -> s.teclaLineaVirtual(m.data?.getString(ORDEN).orEmpty())
			LINEA_DESCONECTAR -> s.desconectarLineaVirtual()
			ZONA_TOQUE_DIRECTO -> {
				val d = m.data
				val zona = android.graphics.Rect(d?.getInt(IZQUIERDA) ?: 0, d?.getInt(ARRIBA) ?: 0, d?.getInt(DERECHA) ?: 0, d?.getInt(ABAJO) ?: 0)
				s.zonaDesdeApp(paquete, zona.takeIf { !it.isEmpty })
			}
			else -> return SIN_PERMISO
		}
		return HECHO
	}

	companion object {
		private const val ETIQUETA = "Api"
		const val ACCION = "com.xebolax.tiresias.API"
		const val HABLAR = 1
		const val CALLAR = 2
		const val BRAILLE = 3
		const val ESTADO = 4
		const val ZONA_TOQUE_DIRECTO = 5
		const val LINEA_CONECTAR = 6
		const val LINEA_TECLA = 7
		const val LINEA_DESCONECTAR = 8
		const val LINEA_CELDAS = 9
		const val CELDAS = "celdas"
		const val ORDEN = "orden"
		const val IZQUIERDA = "izquierda"
		const val ARRIBA = "arriba"
		const val DERECHA = "derecha"
		const val ABAJO = "abajo"
		const val TEXTO = "texto"
		const val INTERRUMPIR = "interrumpir"
		const val RESULTADO = "resultado"
		const val HECHO = 0
		const val SIN_PERMISO = 1
		const val PREGUNTANDO = 2
		const val INACTIVO = 3
		private const val MAXIMO = 2000
	}
}
