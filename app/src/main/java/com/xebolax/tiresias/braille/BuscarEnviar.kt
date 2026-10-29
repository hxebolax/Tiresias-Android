package com.xebolax.tiresias.braille

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.arbol.Nodos

/**
 * El botón Enviar que hay junto a un campo de texto de una app de mensajes
 * (WhatsApp, Mensajes, Telegram): sus campos no tienen acción del editor, o la
 * app no la atiende, así que Intro solo mete un salto de línea. Como SandFly
 * (SendButtonFinder): se busca por su nombre («Enviar», «Send»...) o por su
 * identificador (`send`), mejor en la misma fila que el campo y cuanto más
 * cerca, mejor.
 */
object BuscarEnviar {
	private const val MAXIMO_NODOS = 3000
	private const val MAXIMO_NOMBRE = 50

	/** Las palabras de «enviar» en los idiomas que conoce Tiresias. */
	private val PALABRAS = listOf("enviar", "mandar", "send", "envoyer", "senden", "invia", "inviare", "verzenden", "wyślij", "odeslat")

	/** El botón Enviar del campo [campo] dentro de [raiz], listo para pulsar (él o un antepasado pulsable), o null. */
	fun buscar(raiz: AccessibilityNodeInfo, campo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		val rc = Nodos.limites(campo)
		val candidatos = ArrayList<Pair<AccessibilityNodeInfo, Int>>()
		val cola = ArrayDeque<AccessibilityNodeInfo>().apply { add(raiz) }
		var vistos = 0
		while (cola.isNotEmpty() && vistos < MAXIMO_NODOS) {
			val n = cola.removeFirst()
			vistos++
			for (i in 0 until n.childCount) runCatching { n.getChild(i) }.getOrNull()?.let { cola.addLast(it) }
			if (n == campo || !n.isVisibleToUser || n.isEditable || !n.isEnabled) continue
			val fuerza = fuerza(n)
			if (fuerza == 0) continue
			// No el campo ni algo que lo contenga
			if (Nodos.dentroDe(campo, n)) continue
			candidatos += n to fuerza
		}
		if (candidatos.isEmpty()) return null
		val mejor = candidatos.minByOrNull { (n, fuerza) ->
			val r = Nodos.limites(n)
			val mismaFila = r.centerY() in rc.top..rc.bottom || rc.centerY() in r.top..r.bottom
			// Antes los de la misma fila, luego los de nombre exacto, luego el más cercano
			(if (mismaFila) 0L else 1_000_000L) + (if (fuerza == 2) 0L else 100_000L) + distancia(r, rc)
		}?.first ?: return null
		return pulsable(mejor)
	}

	/** 2: se llama «Enviar» o su identificador es de enviar; 1: empieza por «Enviar» («Enviar SMS»); 0: no es. */
	private fun fuerza(n: AccessibilityNodeInfo): Int {
		val id = n.viewIdResourceName?.substringAfter(":id/", "")?.lowercase().orEmpty()
		if (id.isNotEmpty() && "voice_note" !in id && "sender" !in id && (id == "send" || id.startsWith("send_") || id.endsWith("_send") || "_send_" in id || id.contains("toot"))) return 2
		val nombre = (n.contentDescription ?: n.text)?.toString()?.trim()?.lowercase().orEmpty()
		if (nombre.isEmpty() || nombre.length > MAXIMO_NOMBRE) return 0
		if (PALABRAS.any { nombre == it }) return 2
		if (PALABRAS.any { nombre.startsWith("$it ") }) return 1
		return 0
	}

	private fun distancia(a: Rect, b: Rect): Long =
		Math.hypot((a.centerX() - b.centerX()).toDouble(), (a.centerY() - b.centerY()).toDouble()).toLong()

	/** [n] si se pulsa, o el primer antepasado (hasta tres) que se pulsa. */
	private fun pulsable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		var p: AccessibilityNodeInfo? = n
		repeat(4) {
			val actual = p ?: return null
			if (Nodos.accionable(actual)) return actual
			p = runCatching { actual.parent }.getOrNull()
		}
		return null
	}
}
