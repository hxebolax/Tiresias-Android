package com.xebolax.tiresias.lectura

import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.voz.Enunciado
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Tipo

/**
 * Lectura continua: desde arriba o desde el elemento enfocado, moviendo el foco
 * con lo que se lee y desplazando la lista al llegar al final de lo visible.
 * Se para al tocar la pantalla, con cualquier gesto o tecla, o al cambiar de ventana.
 */
class LecturaContinua(private val lector: Lector) {
	@Volatile var activa = false
		private set
	private var vuelta = 0

	fun desdeArriba() {
		val raiz = lector.raiz() ?: return
		val lista = lector.navegador.orden(raiz)
		if (lista.isEmpty()) return
		empezar(lista.first())
	}

	fun desdeAqui() {
		val f = lector.foco ?: return desdeArriba()
		empezar(f)
	}

	private fun empezar(n: AccessibilityNodeInfo) {
		activa = true
		vuelta++
		leer(n, vuelta)
	}

	private fun leer(n: AccessibilityNodeInfo, v: Int) {
		if (!activa || v != vuelta) return
		lector.enfocarSinHablar(n)
		val texto = lector.compositor.describir(lector.elemento(n), lector.ajustes.verbosidad.copy(decirPistas = false))
		lector.voz.decir(Enunciado(texto, Modo.INTERRUMPIR, Tipo.LECTURA_CONTINUA, alTerminar = { entero ->
			if (entero) lector.manejador.post { siguiente(n, v) } else if (v == vuelta) activa = false
		}))
	}

	private fun siguiente(actual: AccessibilityNodeInfo, v: Int) {
		if (!activa || v != vuelta) return
		val raiz = lector.raiz() ?: return parar()
		val lista = lector.navegador.orden(raiz)
		val i = lista.indexOf(actual)
		val siguiente = lista.getOrNull(i + 1)
		if (siguiente != null) {
			leer(siguiente, v)
			return
		}
		// Final de lo visible: se desplaza la lista y se sigue
		val desplazable = Nodos.desplazable(actual) ?: Nodos.primerDesplazable(raiz)
		if (desplazable != null && runCatching { desplazable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }.getOrDefault(false)) {
			val desde = Nodos.limites(actual)
			lector.manejador.postDelayed({
				if (!activa || v != vuelta) return@postDelayed
				lector.navegador.invalidarTodo()
				val nueva = lector.navegador.orden(lector.raiz() ?: return@postDelayed parar())
				val j = nueva.indexOf(actual)
				val proximo = if (j >= 0) nueva.getOrNull(j + 1) else nueva.firstOrNull { Nodos.limites(it).top > desde.top }
				if (proximo != null) leer(proximo, v) else parar()
			}, 300)
		} else parar()
	}

	fun parar() {
		activa = false
		vuelta++
	}
}
