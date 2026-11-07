package com.xebolax.tiresias.lectura

import android.graphics.Rect
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

	/**
	 * Dónde estaba lo último que se mandó a leer y lo que se desplaza con ello, apuntado al empezar a leerlo:
	 * una página que rehace un anuncio lo quita mientras se lee, y entonces ya no se puede preguntar por él.
	 */
	private var sitioLeido = android.graphics.Rect()
	private var listaLeida: AccessibilityNodeInfo? = null

	private fun leer(n: AccessibilityNodeInfo, v: Int) {
		if (!activa || v != vuelta) return
		lector.enfocarSinHablar(n)
		leidoEn = android.os.SystemClock.uptimeMillis()
		sitioLeido = Nodos.limites(n)
		listaLeida = Nodos.desplazableHacia(n, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: Nodos.desplazable(n)
		val texto = lector.compositor.describir(lector.elemento(n), lector.ajustes.verbosidad.copy(decirPistas = false))
		lector.voz.decir(Enunciado(texto, Modo.INTERRUMPIR, Tipo.LECTURA_CONTINUA, alTerminar = { entero ->
			traza("acabado entero=$entero")
			if (entero) lector.manejador.post { siguiente(n, v) } else if (v == vuelta) activa = false
		}))
	}

	private fun siguiente(actual: AccessibilityNodeInfo, v: Int, desplazamientos: Int = 0) {
		if (!activa || v != vuelta) return
		val raiz = lector.raiz() ?: return parar()
		val lista = lector.navegador.orden(raiz)
		val adelante = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
		val vivo = Nodos.vivo(actual)
		// La lista en la que está lo leído y que aún puede avanzar (si ya no existe, la de cuando se leyó)
		val queAvanza = if (vivo) Nodos.desplazableHacia(actual, adelante) else listaLeida?.takeIf { l -> Nodos.vivo(l) && l.actionList.any { it.id == adelante } }
		val siguiente = siguienteDe(lista, actual)
		// Lo siguiente del orden, si está dentro de esa lista. En Chrome la barra de herramientas va en el árbol
		// después de la página: al acabar lo que se ve, lo siguiente era la barra y la lectura se iba a ella, en vez
		// de desplazar la página (lo mismo que hace el deslizamiento, en Navegador.mover)
		traza("vivo=$vivo queAvanza=${queAvanza?.className} siguiente=${siguiente?.className} dentro=${siguiente != null && queAvanza != null && Nodos.dentroDe(siguiente, queAvanza)}")
		if (siguiente != null && (queAvanza == null || Nodos.dentroDe(siguiente, queAvanza))) {
			leer(siguiente, v)
			return
		}
		// Final de lo visible: se desplaza la lista y se sigue (si lo leído ya no existe, la lista en la que estaba)
		val desplazable = queAvanza ?: (if (vivo) Nodos.desplazable(actual) else listaLeida?.takeIf { Nodos.vivo(it) })
			?: Nodos.primerDesplazable(raiz, adelante) ?: Nodos.primerDesplazable(raiz)
		if (desplazamientos < MAXIMO_DESPLAZAMIENTOS && desplazable != null && runCatching { desplazable.performAction(adelante) }.getOrDefault(false)) {
			val desde = if (vivo) Nodos.limites(actual) else Rect(sitioLeido)
			lector.manejador.postDelayed({
				if (!activa || v != vuelta) return@postDelayed
				lector.navegador.invalidarTodo()
				val nueva = lector.navegador.orden(lector.raiz() ?: return@postDelayed parar())
				val j = nueva.indexOf(actual)
				val proximo = if (j >= 0) nueva.getOrNull(j + 1) else posterior(nueva, actual, desde)
				// Lo siguiente sigue fuera de la lista y la lista aún avanza (la página aún no ha cargado lo de abajo):
				// se vuelve a desplazar, como mucho unas pocas veces
				traza("tras desplazar j=$j proximo=${proximo?.className}")
				val fuera = proximo != null && Nodos.vivo(desplazable) && !Nodos.dentroDe(proximo, desplazable) && desplazable.actionList.any { it.id == adelante }
				when {
					proximo == null && siguiente != null -> leer(siguiente, v)
					proximo == null -> parar()
					fuera -> siguiente(actual, v, desplazamientos + 1)
					else -> leer(proximo, v)
				}
			}, 300)
		} else if (siguiente != null) leer(siguiente, v)
		else parar()
	}

	/**
	 * Lo que va después de [actual] en [lista]. Si [actual] ya no está (Chrome desplaza la página al leer y lo
	 * leído se va por arriba, o un anuncio rehace la página), lo que sigue por posición dentro de lo mismo que se
	 * desplaza, no lo primero de la ventana: eso era la barra de herramientas de Chrome, y la lectura se iba a ella.
	 */
	private fun siguienteDe(lista: List<AccessibilityNodeInfo>, actual: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		val i = lista.indexOf(actual)
		if (i >= 0) return lista.getOrNull(i + 1)
		// Si ya no existe (el anuncio que se acaba de leer se ha rehecho), sus límites salen vacíos y «lo primero por
		// debajo» era lo primero de la ventana: la barra de Chrome. Entonces vale dónde estaba al empezar a leerlo
		val desde = if (Nodos.vivo(actual)) Nodos.limites(actual).takeUnless { it.isEmpty } ?: Rect(sitioLeido) else Rect(sitioLeido)
		return posterior(lista, actual, desde)
	}

	/**
	 * Lo primero de [lista] por debajo de [desde], dentro de lo que se desplaza con [actual] (o, si ya no está, con lo
	 * que se leyó por última vez). Si no hay nada debajo dentro de eso, null: se desplaza en vez de salirse a la barra.
	 */
	private fun posterior(lista: List<AccessibilityNodeInfo>, actual: AccessibilityNodeInfo, desde: Rect): AccessibilityNodeInfo? {
		val contenedor = (if (Nodos.vivo(actual)) Nodos.desplazable(actual) else null) ?: listaLeida
		val zona = contenedor?.takeIf { Nodos.vivo(it) }?.let { Nodos.limites(it) }
		if (zona == null && desde.isEmpty) return null
		val dentro = if (zona == null) lista else lista.filter { n -> Nodos.limites(n).let { o -> zona.contains(o.centerX(), o.centerY()) } }
		return dentro.firstOrNull { Nodos.limites(it).top > desde.top }
	}

	/** Lo que decide la lectura continua, solo en la variante de pruebas (nunca el texto). */
	private fun traza(s: String, quien: Throwable? = null) {
		if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasContinua", s, quien)
	}

	fun parar() {
		if (activa && com.xebolax.tiresias.BuildConfig.PRUEBAS) traza("parar", Throwable())
		activa = false
		vuelta++
	}

	private companion object {
		/** Lo que puede haber entre que acaba un elemento y empieza el siguiente (desplazando la lista, unos 300 ms). */
		const val ENTRE_ELEMENTOS_MS = 2_000L
		/** Las veces seguidas que se desplaza buscando algo nuevo dentro de la lista antes de salirse de ella. */
		const val MAXIMO_DESPLAZAMIENTOS = 3
	}
}
