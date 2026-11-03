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
	@Volatile private var vuelta = 0
	/** Cuándo se mandó a decir el último elemento. */
	@Volatile private var leidoEn = 0L

	/**
	 * Si de verdad se está leyendo de seguido: marcada y con la voz hablando (o entre un elemento y el siguiente).
	 * Lo miran las teclas de volumen: una marca que se quedara puesta no les quita lo suyo.
	 */
	val leyendo: Boolean get() = activa && (lector.voz.hablando || android.os.SystemClock.uptimeMillis() - leidoEn < ENTRE_ELEMENTOS_MS)

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
		leidoEn = android.os.SystemClock.uptimeMillis()
		val texto = lector.compositor.describir(lector.elemento(n), lector.ajustes.verbosidad.copy(decirPistas = false))
		lector.voz.decir(Enunciado(texto, Modo.INTERRUMPIR, Tipo.LECTURA_CONTINUA, alTerminar = { entero ->
			if (entero) lector.manejador.post { siguiente(n, v) } else if (v == vuelta) activa = false
		}))
	}

	private fun siguiente(actual: AccessibilityNodeInfo, v: Int) {
		if (!activa || v != vuelta) return
		val raiz = lector.raiz() ?: return parar()
		val lista = lector.navegador.orden(raiz)
		val siguiente = siguienteDe(lista, actual)
		if (siguiente != null) {
			leer(siguiente, v)
			return
		}
		// Final de lo visible: se desplaza la lista y se sigue
		val adelante = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
		val desplazable = Nodos.desplazableHacia(actual, adelante) ?: Nodos.desplazable(actual) ?: Nodos.primerDesplazable(raiz, adelante) ?: Nodos.primerDesplazable(raiz)
		if (desplazable != null && runCatching { desplazable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }.getOrDefault(false)) {
			val desde = Nodos.limites(actual)
			lector.manejador.postDelayed({
				if (!activa || v != vuelta) return@postDelayed
				lector.navegador.invalidarTodo()
				val nueva = lector.navegador.orden(lector.raiz() ?: return@postDelayed parar())
				val j = nueva.indexOf(actual)
				val proximo = if (j >= 0) nueva.getOrNull(j + 1) else posterior(nueva, actual, desde)
				if (proximo != null) leer(proximo, v) else parar()
			}, 300)
		} else parar()
	}

	/**
	 * Lo que va después de [actual] en [lista]. Si [actual] ya no está (Chrome desplaza la página al leer y lo
	 * leído se va por arriba, o un anuncio rehace la página), lo que sigue por posición dentro de lo mismo que se
	 * desplaza, no lo primero de la ventana: eso era la barra de herramientas de Chrome, y la lectura se iba a ella.
	 */
	private fun siguienteDe(lista: List<AccessibilityNodeInfo>, actual: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		val i = lista.indexOf(actual)
		if (i >= 0) return lista.getOrNull(i + 1)
		runCatching { actual.refresh() }
		return posterior(lista, actual, Nodos.limites(actual))
	}

	/** Lo primero de [lista] por debajo de [desde], dentro de lo que se desplaza con [actual] si sigue ahí. */
	private fun posterior(lista: List<AccessibilityNodeInfo>, actual: AccessibilityNodeInfo, desde: android.graphics.Rect): AccessibilityNodeInfo? {
		val zona = Nodos.desplazable(actual)?.takeIf { Nodos.vivo(it) }?.let { Nodos.limites(it) }
		val dentro = lista.filter { n -> zona == null || Nodos.limites(n).let { o -> zona.contains(o.centerX(), o.centerY()) } }.ifEmpty { lista }
		return dentro.firstOrNull { Nodos.limites(it).top > desde.top }
	}

	fun parar() {
		activa = false
		vuelta++
	}

	private companion object {
		/** Lo que puede haber entre que acaba un elemento y empieza el siguiente (desplazando la lista, unos 300 ms). */
		const val ENTRE_ELEMENTOS_MS = 2_000L
	}
}
