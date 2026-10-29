package com.xebolax.tiresias.lectura

import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.xebolax.tiresias.base.Ajustes
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.nucleo.ControlLectura
import com.xebolax.tiresias.nucleo.Granularidad
import com.xebolax.tiresias.nucleo.Rol
import com.xebolax.tiresias.nucleo.Segmentos
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido

/**
 * El movimiento por la pantalla: siguiente y anterior, primero y último, por
 * tipo de elemento, por cuadrícula, por ventanas y por el texto.
 *
 * - **Orden de lectura en caché** por ventana; se rehace en segundo plano cuando
 *   la ventana cambia, para que el siguiente deslizamiento no tenga que esperar
 *   (Backtalk: de unos 450 a 150 ms entre deslizamientos).
 * - **Final de la lista**: se desplaza sola y sigue por el primer elemento
 *   nuevo; si no se puede, suena el borde (o da la vuelta, si se elige).
 * - **Tras un desplazamiento grande** el foco va al elemento más cercano por
 *   posición, nunca a un salto en silencio (TalkBack, pull request 39).
 * - **Vigilante de 150 ms** para los desplazamientos que no avanzan.
 */
class Navegador(private val lector: Lector) {
	private val ordenes = HashMap<Int, List<AccessibilityNodeInfo>>()
	private var pendienteRehacer: Runnable? = null

	/** Un desplazamiento pedido por la navegación que espera su evento. */
	private class Avance(
		val adelante: Boolean, val desde: Rect, val momento: Long,
		/** La lista que se desplazó, la posición del elemento de partida en ella (si la app la da) y su firma. */
		val contenedor: AccessibilityNodeInfo? = null, val filaDesde: Int? = null, val firmaFoco: String? = null,
		/** Las firmas de lo que se veía en la lista antes de desplazarla: lo nuevo es lo que no está. */
		val firmasAntes: Set<String> = emptySet(),
	) {
		/** Las veces que se ha vuelto a mirar porque la lista aún no se había colocado. */
		var reintentos = 0
	}
	private var avance: Avance? = null

	/** Cursor propio en el texto de un nodo que no admite moverse por granularidad. */
	private var cursorNodo: AccessibilityNodeInfo? = null
	private var cursor = 0

	/** Mientras se recorre una lista hasta el tope, no se rehace el orden en cada página: solo al final. */
	@Volatile var sinRehacer = false

	fun invalidar(ventana: Int) {
		ordenes.remove(ventana)
		pendienteRehacer?.let { lector.manejador.removeCallbacks(it) }
		if (sinRehacer) return
		// Se rehace en cuanto la ventana se calme, antes de que el usuario deslice
		val r = Runnable { lector.raiz()?.let { orden(it) } }
		pendienteRehacer = r
		lector.manejador.postDelayed(r, 80)
	}

	fun invalidarTodo() = ordenes.clear()

	/** El orden de lectura de la ventana de [raiz] (de la caché si está). */
	fun orden(raiz: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
		val id = raiz.windowId
		ordenes[id]?.let { return it }
		val reglas = lector.scriptsListos && lector.scripts.hayReglas
		val inicio = android.os.SystemClock.uptimeMillis()
		val todos = Nodos.ordenLectura(raiz, agrupa = if (reglas) { n -> lector.scripts.regla(n)?.agrupar == true } else null)
		if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasOrden", "${todos.size} paradas en ${android.os.SystemClock.uptimeMillis() - inicio} ms")
		// Lo que una regla de un complemento oculta no es una parada (ni, con «todo», lo de dentro)
		val lista = if (reglas) todos.filter { n ->
			val r = lector.scripts.regla(n)
			r?.ocultar != true && r?.ocultarDentro != true && !lector.scripts.ocultoDentro(n)
		} else todos
		ordenes[id] = lista
		return lista
	}

	/** Prepara la frase del elemento anterior y del siguiente a [n]. */
	fun prepararVecinos(n: AccessibilityNodeInfo) {
		val raiz = lector.raiz() ?: return
		val lista = orden(raiz)
		val i = lista.indexOf(n)
		if (i < 0) return
		lista.getOrNull(i + 1)?.let { lector.preparar(it) }
		lista.getOrNull(i - 1)?.let { lector.preparar(it) }
	}

	// --- Elemento a elemento --------------------------------------------------------------

	fun siguiente() = mover(true)
	fun anterior() = mover(false)

	fun mover(adelante: Boolean, filtro: ((AccessibilityNodeInfo) -> Boolean)? = null, intento: Int = 0): Boolean {
		// La lista se está adelantando sola: se espera a que acabe (unos ms) en vez de desplazarla otra vez
		val espera = anticipandoHasta - SystemClock.uptimeMillis()
		if (espera > 0) {
			lector.manejador.postDelayed({ mover(adelante, filtro, intento) }, espera)
			return true
		}
		val raiz = lector.raiz() ?: return borde()
		val lista = orden(raiz)
		val candidatos = if (filtro != null) lista.filter(filtro) else lista
		if (candidatos.isEmpty()) return if (filtro != null) noHay() else borde()
		val foco = lector.foco
		val i = indiceFueraDeLaPantalla(lista, foco, adelante) ?: indice(lista, foco)
		val destino = if (filtro == null) {
			val j = if (i < 0) (if (adelante) 0 else lista.lastIndex) else if (adelante) i + 1 else i - 1
			lista.getOrNull(j)
		} else {
			if (i < 0) (if (adelante) candidatos.first() else candidatos.last())
			else if (adelante) lista.drop(i + 1).firstOrNull(filtro) else lista.take(i).lastOrNull(filtro)
		}
		// Dentro de una lista que aún puede avanzar, si lo siguiente ya está fuera de ella (el botón
		// Cancelar de un menú, lo que hay debajo de la lista), primero se desplaza: elemento a
		// elemento, sin saltarse los que no se ven (como TalkBack)
		if (destino != null && foco != null) {
			val contenedor = Nodos.desplazable(foco)
			val accion = if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
			if (contenedor != null && contenedor.actionList.any { it.id == accion } && !Nodos.dentroDe(destino, contenedor)) {
				if (desplazarParaSeguir(adelante, foco, raiz)) return true
			}
		}
		// Justo después de desplazarse la lista, un hueco en sus posiciones (del 17 al 22) es que aún no ha
		// colocado todas sus filas: se vuelve a mirar en un momento, en vez de saltárselas
		if (filtro == null && destino != null && foco != null && intento < REINTENTOS_COLOCAR &&
			SystemClock.uptimeMillis() - desplazadaEn < RECIEN_DESPLAZADA_MS && hayHueco(foco, destino, adelante)) {
			invalidarTodo()
			lector.manejador.postDelayed({ mover(adelante, null, intento + 1) }, ESPERA_COLOCAR_MS)
			return true
		}
		if (destino != null) {
			lector.enfocar(destino, if (filtro == null && foco != null && cambiaDeFila(foco, destino, adelante)) Sonido.FILA else Sonido.FOCO)
			anticipar(destino, adelante, lista)
			return true
		}
		// Al final: se desplaza la lista y se sigue por lo nuevo
		if (desplazarParaSeguir(adelante, foco, raiz)) return true
		if (lector.ajustes.darVuelta) return darLaVuelta(adelante, raiz, filtro)
		return if (filtro != null) noHay() else borde()
	}

	@Volatile private var vuelta: Runnable? = null

	/**
	 * Da la vuelta: al principio de verdad de la pantalla (o al final, hacia
	 * atrás). Las listas que no están en ese extremo se desplazan antes hasta él,
	 * página a página: si no, «lo primero» sería lo primero que se ve de una lista
	 * a medias y el siguiente gesto hacia atrás la desplazaría en vez de volver.
	 */
	private fun darLaVuelta(adelante: Boolean, raiz: AccessibilityNodeInfo, filtro: ((AccessibilityNodeInfo) -> Boolean)?): Boolean {
		lector.sonidos.tocar(Sonido.VUELTA)
		val accion = if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
		// Hacia el principio, primero las de dentro (si se sube antes la de fuera, la de dentro deja de verse); hacia el final, al revés
		// (y después se vuelve a mirar: la de dentro puede verse solo cuando la de fuera ha llegado abajo)
		fun pendientes(r: AccessibilityNodeInfo) = Nodos.desplazables(r).filter { l -> l.actionList.any { it.id == accion } }.let { if (adelante) it.reversed() else it }
		var listas = pendientes(raiz)
		val enfocarExtremo = {
			val r = lector.raiz() ?: raiz
			invalidarTodo()
			val lista = orden(r)
			val candidatos = if (filtro != null) lista.filter(filtro) else lista
			val destino = if (adelante) candidatos.firstOrNull() else candidatos.lastOrNull()
			if (destino != null) lector.enfocar(destino, null) else borde()
		}
		if (listas.isEmpty()) {
			enfocarExtremo()
			return true
		}
		val tocado = lector.toques
		var cual = 0
		var paginas = 0
		var rondas = 0
		val paso = object : Runnable {
			override fun run() {
				if (vuelta !== this) return
				// Si se toca la pantalla, se para (lo que se haga ahora manda)
				if (lector.toques != tocado) {
					vuelta = null
					sinRehacer = false
					return
				}
				while (paginas < MAXIMO_PAGINAS_VUELTA) {
					if (cual >= listas.size) {
						// Las que han aparecido al desplazar las otras
						if (++rondas > 2) break
						listas = (lector.raiz() ?: raiz).let { pendientes(it) }
						cual = 0
						if (listas.isEmpty()) break
					}
					val l = listas[cual]
					runCatching { l.refresh() }
					if (l.actionList.any { it.id == accion } && runCatching { l.performAction(accion) }.getOrDefault(false)) {
						paginas++
						lector.manejador.postDelayed(this, 90)
						return
					}
					cual++
				}
				vuelta = null
				sinRehacer = false
				lector.manejador.postDelayed({ enfocarExtremo() }, 200)
			}
		}
		vuelta = paso
		sinRehacer = true
		lector.manejador.post(paso)
		return true
	}

	/**
	 * Si [foco] se ha ido de la pantalla con su lista (se desplazó más de la cuenta) y la app da la posición
	 * de cada fila («17 de 28»): el índice que deja como siguiente (o anterior) a la fila de al lado, para no
	 * saltarse las que hay entre medias. null si no hace falta o no se sabe.
	 */
	private fun indiceFueraDeLaPantalla(lista: List<AccessibilityNodeInfo>, foco: AccessibilityNodeInfo?, adelante: Boolean): Int? {
		if (foco == null) return null
		val desde = fila(foco) ?: return null
		// Sigue en la pantalla y es la misma fila (una lista reutiliza la vista de una fila que se va para otra que llega)
		val k = lista.indexOf(foco)
		if (k >= 0 && fila(lista[k]) == desde) return null
		val conFila = lista.withIndex().filter { it.value.windowId == foco.windowId }.mapNotNull { (k, n) -> fila(n)?.let { k to it } }
		return if (adelante) conFila.filter { it.second > desde }.minByOrNull { it.second }?.let { it.first - 1 }
		else conFila.filter { it.second < desde }.maxByOrNull { it.second }?.let { it.first + 1 }
	}

	/** El índice de [foco] en [lista]; si ya no está (la lista cambió), el del más cercano por posición. */
	private fun indice(lista: List<AccessibilityNodeInfo>, foco: AccessibilityNodeInfo?): Int {
		if (foco == null) return -1
		val i = lista.indexOf(foco)
		if (i >= 0) return i
		// El foco está en otra ventana (la de debajo de un menú): se empieza por el principio
		if (lista.firstOrNull()?.windowId?.let { it != foco.windowId } == true) return -1
		val r = Nodos.limites(foco)
		if (r.isEmpty) return -1
		return lista.indices.minByOrNull { k ->
			val o = Nodos.limites(lista[k])
			Math.abs(o.top - r.top) * 4L + Math.abs(o.left - r.left)
		} ?: -1
	}

	/** Hasta cuándo la lista se está adelantando sola, y hasta cuándo no suena su desplazamiento. */
	@Volatile private var anticipandoHasta = 0L
	@Volatile var silencioDesplazarHasta = 0L
		private set

	/**
	 * Desplazamiento anticipado (como VoiceOver): si [n] es lo último que se ve de
	 * su lista (o lo primero, hacia atrás), la lista se adelanta ya un tercio, en
	 * silencio, mientras se dice el elemento. El foco sigue a la vista y lo
	 * siguiente ya está en la pantalla: el próximo deslizamiento no espera.
	 */
	private fun anticipar(n: AccessibilityNodeInfo, adelante: Boolean, lista: List<AccessibilityNodeInfo>) {
		if (lector.ajustes.desplazarListas != Ajustes.DESPLAZAR_ANTICIPADO) return
		val contenedor = Nodos.desplazable(n) ?: return
		val accion = if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
		// Solo si aún puede ir hacia ahí
		if (contenedor.actionList.none { it.id == accion }) return
		val dentro = lista.filter { Nodos.dentroDe(it, contenedor) }
		val i = dentro.indexOf(n)
		if (i < 0 || (adelante && i < dentro.lastIndex) || (!adelante && i > 0)) return
		lector.manejador.postDelayed({
			if (lector.foco != n || !Nodos.vivo(contenedor)) return@postDelayed
			val ahora = SystemClock.uptimeMillis()
			anticipandoHasta = ahora + DURACION_ANTICIPO_MS
			silencioDesplazarHasta = ahora + DURACION_ANTICIPO_MS + 400
			if (!arrastrar(contenedor, adelante, sonido = false)) {
				anticipandoHasta = 0L
				return@postDelayed
			}
			invalidarTodo()
		}, 60)
	}

	/**
	 * Arrastra con dos dedos el contenido de [lista] (para ver lo siguiente, los dedos suben), sin lanzarlo:
	 * al final los dedos se quedan quietos un momento antes de levantarse (Android 8+). Si se levantaran en
	 * marcha, la lista seguiría corriendo sola, se pasaría y el foco se quedaría fuera de la pantalla.
	 */
	fun arrastrar(lista: AccessibilityNodeInfo, adelante: Boolean, horizontal: Boolean = false, sonido: Boolean = true): Boolean {
		val r = Nodos.limites(lista)
		if (r.width() < 40 || r.height() < 40) return false
		val cx = r.exactCenterX()
		val cy = r.exactCenterY()
		val largo = if (horizontal) r.width() * 0.3f else r.height() * 0.3f
		val signo = if (adelante) -1f else 1f
		val seQueda = Build.VERSION.SDK_INT >= 26
		val gesto = GestureDescription.Builder()
		val trazos = ArrayList<Pair<GestureDescription.StrokeDescription, android.graphics.PointF>>()
		for (dedo in listOf(-40f, 40f)) {
			val camino = android.graphics.Path()
			val fin = if (horizontal) {
				camino.moveTo(cx - signo * largo, cy + dedo)
				android.graphics.PointF(cx + signo * largo, cy + dedo)
			} else {
				camino.moveTo(cx + dedo, cy - signo * largo)
				android.graphics.PointF(cx + dedo, cy + signo * largo)
			}
			camino.lineTo(fin.x, fin.y)
			val trazo = if (seQueda) GestureDescription.StrokeDescription(camino, 0, DURACION_ARRASTRE_MS, true)
			else GestureDescription.StrokeDescription(camino, 0, DURACION_ARRASTRE_MS)
			gesto.addStroke(trazo)
			trazos += trazo to fin
		}
		if (sonido && lector.ajustes.desplazarListas == Ajustes.DESPLAZAR_NORMAL) lector.sonidos.tocar(Sonido.DESPLAZAR)
		if (!seQueda) return lector.mandarGesto(gesto.build())
		return lector.mandarGesto(gesto.build()) {
			// Quietos donde acabaron, y entonces se levantan
			val quietos = GestureDescription.Builder()
			for ((trazo, fin) in trazos) {
				val punto = android.graphics.Path().apply { moveTo(fin.x, fin.y) }
				quietos.addStroke(trazo.continueStroke(punto, 0, QUIETOS_MS, false))
			}
			lector.mandarGesto(quietos.build())
		}
	}

	private fun desplazarParaSeguir(adelante: Boolean, foco: AccessibilityNodeInfo?, raiz: AccessibilityNodeInfo): Boolean {
		// Solo la lista en la que está el foco: con el foco fuera de toda lista (el título, al principio, o un
		// botón de abajo, al final), desplazar otra lista llevaría a su mitad, no a lo siguiente
		val lista = (if (foco != null && Nodos.vivo(foco)) Nodos.desplazable(foco) else Nodos.primerDesplazable(raiz)) ?: return false
		val accion = if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
		val desde = foco?.let { Nodos.limites(it) } ?: Rect()
		// Lo de antes de desplazar, para saber después cuál es el siguiente de verdad (una página entera de golpe
		// no debe saltarse los que aparecen arriba): su posición en la lista y lo que se veía
		val firmasAntes = orden(raiz).filter { Nodos.dentroDe(it, lista) }.map { firma(it) }.toSet()
		val nuevoAvance = { Avance(adelante, desde, SystemClock.uptimeMillis(), lista, foco?.let { fila(it) }, foco?.let { firma(it) }, firmasAntes) }
		val tieneAccion = lista.actionList.any { it.id == accion }
		val conAccion = tieneAccion && runCatching { lista.performAction(accion) }.getOrDefault(false)
		// La lista se desplaza por accesibilidad pero ya no puede ir hacia ahí: está en el tope (no hay que arrastrar)
		if (!conAccion && Nodos.seDesplazaPorAccesibilidad(lista)) return false
		// Sin la acción de accesibilidad (la app no la da): arrastre de dos dedos si se quiere
		if (!conAccion) {
			if (!lista.isScrollable || !lector.ajustes.arrastreRespaldo || !arrastrar(lista, adelante)) return false
			val a = nuevoAvance()
			avance = a
			lector.manejador.postDelayed({ if (avance === a) seguirTrasDesplazar(a) }, 800)
			return true
		}
		val a = nuevoAvance()
		avance = a
		// Si el evento de desplazamiento no llega (o la lista no avanza), no se espera más de 150 ms
		lector.manejador.postDelayed({ if (avance === a) seguirTrasDesplazar(a) }, ESPERA_DESPLAZAR_MS)
		return true
	}

	/** Cuándo se desplazó por última vez una lista (para saber si aún se está colocando). */
	@Volatile private var desplazadaEn = 0L

	fun alDesplazar(e: AccessibilityEvent) {
		desplazadaEn = SystemClock.uptimeMillis()
		val a = avance ?: return
		// Se deja que la lista termine de colocarse
		lector.manejador.postDelayed({ if (avance === a) seguirTrasDesplazar(a) }, 40)
	}

	private fun seguirTrasDesplazar(a: Avance) {
		avance = null
		val raiz = lector.raiz() ?: return
		invalidarTodo()
		val lista = orden(raiz)
		if (lista.isEmpty()) {
			// La lista a medio rehacer: se vuelve a mirar en un momento (si no, el deslizamiento se quedaría callado)
			if (a.reintentos++ < REINTENTOS_COLOCAR) {
				avance = a
				lector.manejador.postDelayed({ if (avance === a) seguirTrasDesplazar(a) }, ESPERA_COLOCAR_MS)
			}
			return
		}
		val foco = lector.foco
		a.contenedor?.let { runCatching { it.refresh() } }
		val dentro = a.contenedor?.let { c -> lista.filter { Nodos.dentroDe(it, c) } }.orEmpty()
		// 1. Por su posición en la lista, si la app la da («7 de 19»): el 8, aunque la lista haya pasado una página entera
		val conPosicion = a.filaDesde?.let { desde ->
			val conFila = dentro.mapNotNull { n -> fila(n)?.let { n to it } }
			if (a.adelante) conFila.filter { it.second > desde }.minByOrNull { it.second }
			else conFila.filter { it.second < desde }.maxByOrNull { it.second }
		}
		// Con un hueco (del 14 al 19), la lista aún se está colocando y faltan filas: se vuelve a mirar
		// en un momento, en vez de saltárselas (un móvil lento, Android 15)
		if (conPosicion != null && a.reintentos < REINTENTOS_COLOCAR && !vecinas(a.filaDesde!!, conPosicion.second, a.adelante)) {
			a.reintentos++
			avance = a
			lector.manejador.postDelayed({ if (avance === a) seguirTrasDesplazar(a) }, ESPERA_COLOCAR_MS)
			return
		}
		val porFila = conPosicion?.first
		// 2. El de partida sigue ahí (y es el mismo: una lista reutiliza sus filas para otros elementos): su vecino
		val sigueAhi = foco != null && lista.contains(foco) && a.firmaFoco != null && runCatching { foco.refresh() }.getOrDefault(false) && firma(foco) == a.firmaFoco
		val destino = porFila ?: if (sigueAhi) {
			val i = lista.indexOf(foco)
			lista.getOrNull(if (a.adelante) i + 1 else i - 1)
		} else {
			// 3. Lo primero (o lo último) que no se veía antes; 4. si no, por la posición en la pantalla
			val nuevos = dentro.filter { firma(it) !in a.firmasAntes }
			(if (a.adelante) nuevos.firstOrNull() else nuevos.lastOrNull())
				?: if (a.adelante) lista.firstOrNull { Nodos.limites(it).top > a.desde.top } ?: lista.lastOrNull()
				else lista.lastOrNull { Nodos.limites(it).top < a.desde.top } ?: lista.firstOrNull()
		}
		if (destino != null && destino != foco) lector.enfocar(destino) else borde()
	}

	/** Si entre [desde] y [hasta], de la misma lista, faltan filas (las dos dicen su posición y no son vecinas). */
	private fun hayHueco(desde: AccessibilityNodeInfo, hasta: AccessibilityNodeInfo, adelante: Boolean): Boolean {
		if (desde.windowId != hasta.windowId) return false
		val f0 = fila(desde) ?: return false
		val f1 = fila(hasta) ?: return false
		// En la misma fila (un interruptor dentro de su fila) no hay hueco
		if (f0 == f1 || (adelante && f1 < f0) || (!adelante && f1 > f0)) return false
		return !vecinas(f0, f1, adelante)
	}

	/**
	 * Si al ir de [desde] a [hasta] se pasa del final de una fila al principio de la siguiente (adelante: debajo
	 * y a la izquierda; atrás: encima y a la derecha), en la misma ventana. En una lista de una columna no: cada
	 * elemento está debajo del anterior, pero no a su izquierda.
	 */
	private fun cambiaDeFila(desde: AccessibilityNodeInfo, hasta: AccessibilityNodeInfo, adelante: Boolean): Boolean {
		if (desde.windowId != hasta.windowId) return false
		val a = Nodos.limites(desde)
		val b = Nodos.limites(hasta)
		if (a.isEmpty || b.isEmpty) return false
		return if (adelante) b.centerY() >= a.bottom && b.centerX() < a.left
		else b.centerY() <= a.top && b.centerX() > a.right
	}

	/** Si la posición [hasta] va justo después (o antes) de [desde]: la siguiente columna o el principio (o el final) de la otra fila. */
	private fun vecinas(desde: Int, hasta: Int, adelante: Boolean): Boolean =
		if (adelante) hasta == desde + 1 || hasta == (desde / 1000 + 1) * 1000
		else hasta == desde - 1 || hasta / 1000 == desde / 1000 - 1

	/** Lo que identifica a un elemento por lo que es, no por la vista que lo enseña (las listas reutilizan sus filas). */
	private fun firma(n: AccessibilityNodeInfo): String =
		"${n.className}|${n.viewIdResourceName}|${n.text}|${n.contentDescription}"

	/** La posición de [n] en su lista (fila y columna en un número), si la app la da; null si no. */
	fun filaDe(n: AccessibilityNodeInfo): Int? = fila(n)

	/** La posición de [n] (o de la fila en la que está) en su lista o cuadrícula, si la app la da: fila y columna en un número. */
	private fun fila(n: AccessibilityNodeInfo): Int? {
		var p: AccessibilityNodeInfo? = n
		repeat(4) {
			val info = p?.collectionItemInfo
			if (info != null) return info.rowIndex * 1000 + info.columnIndex
			p = runCatching { p?.parent }.getOrNull() ?: return null
		}
		return null
	}

	fun primero(): Boolean {
		val raiz = lector.raiz() ?: return borde()
		val n = orden(raiz).firstOrNull() ?: return borde()
		lector.enfocar(n)
		return true
	}

	fun ultimo(): Boolean {
		val raiz = lector.raiz() ?: return borde()
		val n = orden(raiz).lastOrNull() ?: return borde()
		lector.enfocar(n)
		return true
	}

	private fun borde(): Boolean {
		lector.sonidos.tocar(Sonido.BORDE)
		return false
	}

	private fun noHay(): Boolean {
		lector.sonidos.tocar(Sonido.BORDE)
		lector.decir(lector.textos.getString(R.string.nav_no_hay, nombreControl(lector.control)), Modo.INTERRUMPIR)
		return false
	}

	// --- Por tipo de elemento -------------------------------------------------------------

	/** Siguiente o anterior elemento del tipo de [c]: en la web lo hace el navegador; en las apps, Tiresias. */
	fun porTipo(c: ControlLectura, adelante: Boolean, nivel: Int = 0): Boolean {
		val foco = lector.foco
		val html = htmlDe(c, nivel)
		if (html != null && foco != null && enWeb(foco)) {
			val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING, html) }
			val accion = if (adelante) AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT else AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT
			if (runCatching { foco.performAction(accion, args) }.getOrDefault(false)) return true
		}
		val filtro: (AccessibilityNodeInfo) -> Boolean = when (c) {
			ControlLectura.ENCABEZADOS -> { n -> Nodos.esEncabezado(n) && (nivel == 0 || Nodos.nivelEncabezado(n) == nivel) }
			// También los textos con enlaces dentro (sus enlaces se eligen después con arriba y abajo)
			ControlLectura.ENLACES -> { n -> Nodos.rol(n) == Rol.ENLACE || Nodos.tieneEnlacesEnTexto(n) }
			ControlLectura.CONTROLES -> { n -> Nodos.accionable(n) }
			ControlLectura.BOTONES -> { n -> Nodos.rol(n).let { it == Rol.BOTON || it == Rol.BOTON_IMAGEN || it == Rol.BOTON_ALTERNAR } }
			ControlLectura.CAMPOS -> { n -> n.isEditable }
			ControlLectura.CASILLAS -> { n -> n.isCheckable }
			ControlLectura.LISTAS -> { n -> n.collectionInfo != null || lector.describir.coleccionDe(n)?.let { c2 -> esPrimeroDe(n, c2) } == true }
			ControlLectura.TABLAS -> { n -> Nodos.rol(n) == Rol.TABLA || (n.collectionItemInfo != null && lector.describir.coleccionDe(n)?.collectionInfo?.columnCount ?: 0 > 1) }
			ControlLectura.REGIONES -> { n -> Nodos.rolWeb(n) == Rol.REGION || Nodos.rolWeb(n) == Rol.NAVEGACION }
			ControlLectura.IMAGENES -> { n -> Nodos.rol(n) == Rol.IMAGEN }
			else -> { _ -> true }
		}
		return mover(adelante, filtro)
	}

	private fun esPrimeroDe(n: AccessibilityNodeInfo, coleccion: AccessibilityNodeInfo): Boolean = runCatching { coleccion.getChild(0) == n }.getOrDefault(false)

	private fun htmlDe(c: ControlLectura, nivel: Int): String? = when (c) {
		ControlLectura.ENCABEZADOS -> if (nivel in 1..6) "H$nivel" else "HEADING"
		ControlLectura.ENLACES -> "LINK"
		ControlLectura.CONTROLES -> "CONTROL"
		ControlLectura.BOTONES -> "BUTTON"
		ControlLectura.CAMPOS -> "TEXT_FIELD"
		ControlLectura.CASILLAS -> "CHECKBOX"
		ControlLectura.LISTAS -> "LIST"
		ControlLectura.TABLAS -> "TABLE"
		ControlLectura.REGIONES -> "LANDMARK"
		ControlLectura.IMAGENES -> "GRAPHIC"
		else -> null
	}

	/** Si [n] está dentro de una página web (Chrome, WebView): admite la navegación HTML. */
	fun enWeb(n: AccessibilityNodeInfo): Boolean {
		var p: AccessibilityNodeInfo? = n
		var nivel = 0
		while (p != null && nivel < 30) {
			if (p.actionList.any { it.id == AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT }) return true
			p = runCatching { p!!.parent }.getOrNull()
			nivel++
		}
		return false
	}

	// --- Por el texto ---------------------------------------------------------------------

	/**
	 * Mueve por el texto del elemento enfocado en [g]. Si la app lo admite
	 * (campos de texto, la web), lo hace ella y Tiresias dice lo recorrido al
	 * llegar el evento; si no, Tiresias recorre el texto él mismo.
	 */
	fun porTexto(g: Granularidad, adelante: Boolean, extender: Boolean = false): Boolean {
		val foco = lector.foco ?: return borde()
		val bit = when (g) {
			Granularidad.CARACTER -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
			Granularidad.PALABRA -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
			Granularidad.LINEA -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
			Granularidad.PARRAFO -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
			Granularidad.PAGINA -> AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE
			Granularidad.FRASE -> 0
		}
		if (bit != 0 && foco.movementGranularities and bit != 0) {
			val args = Bundle().apply {
				putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, bit)
				// Fuera de un campo no se selecciona: Tiresias apunta el trozo (EdicionTexto.apuntarTrozo)
				putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, extender && foco.isEditable)
			}
			val accion = if (adelante) AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY else AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
			if (runCatching { foco.performAction(accion, args) }.getOrDefault(false)) return true
			// Al principio o al final del texto: al elemento siguiente (o anterior), como TalkBack
			return alAcabarTexto(adelante, extender)
		}
		// Texto que la app no deja recorrer: Tiresias lo recorre él mismo
		val texto = lector.elemento(foco).let { if (it.editable) it.texto else it.nombre }
		if (texto.isEmpty()) return alAcabarTexto(adelante, extender)
		if (cursorNodo != foco) {
			cursorNodo = foco
			cursor = if (adelante) 0 else texto.length
		}
		val s = if (adelante) Segmentos.siguiente(texto, cursor, g) else Segmentos.anterior(texto, cursor, g)
		if (s == null) return alAcabarTexto(adelante, extender)
		cursor = if (adelante) s.fin else s.inicio
		lector.edicion.decirSegmento(s.texto, g)
		if (extender && !foco.isEditable) lector.edicion.apuntarTrozo(foco, texto, s.inicio, s.fin)
		return true
	}

	/**
	 * Se acabó el texto del elemento: se pasa al siguiente (o al anterior) y el
	 * cursor propio empieza por su principio (o su final). Al seleccionar no se
	 * sale del campo: suena el borde.
	 */
	private fun alAcabarTexto(adelante: Boolean, extender: Boolean): Boolean {
		if (extender || lector.foco?.isEditable == true) return borde()
		cursorNodo = null
		return mover(adelante)
	}

	// --- Cuadrícula -------------------------------------------------------------------------

	/**
	 * Navegación por cuadrícula en filas con varios botones (Backtalk 243):
	 * arriba y abajo van al elemento de la fila de encima o de debajo que más se
	 * solapa en horizontal; izquierda y derecha, al de la misma fila.
	 */
	fun cuadricula(dx: Int, dy: Int, desplazada: Boolean = false): Boolean {
		val raiz = lector.raiz() ?: return borde()
		val foco = lector.foco ?: return primero()
		val r = Nodos.limites(foco)
		val lista = orden(raiz).filter { it != foco }
		val candidato = lista.mapNotNull { n ->
			val o = Nodos.limites(n)
			if (o.isEmpty) return@mapNotNull null
			val solapeH = minOf(r.right, o.right) - maxOf(r.left, o.left)
			val solapeV = minOf(r.bottom, o.bottom) - maxOf(r.top, o.top)
			val distancia = when {
				dy > 0 -> if (o.top >= r.centerY() && solapeH > 0) o.top - r.top else null
				dy < 0 -> if (o.bottom <= r.centerY() && solapeH > 0) r.top - o.top else null
				dx > 0 -> if (o.left >= r.centerX() && solapeV > 0) o.left - r.left else null
				dx < 0 -> if (o.right <= r.centerX() && solapeV > 0) r.left - o.left else null
				else -> null
			}
			distancia?.let { n to it }
		}.minByOrNull { it.second }?.first
		if (candidato == null) {
			// Arriba o abajo dentro de una lista que aún puede ir hacia ahí: se desplaza una página y se vuelve
			// a buscar la fila de debajo (o de encima), como Backtalk 309. Una vez: si tampoco hay, el borde
			if (dy != 0 && !desplazada) {
				val accion = if (dy > 0) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
				val contenedor = Nodos.desplazable(foco)?.takeIf { c -> c.actionList.any { it.id == accion } && !esHorizontal(c) }
				val antes = firma(foco)
				if (contenedor != null && runCatching { contenedor.performAction(accion) }.getOrDefault(false)) {
					lector.manejador.postDelayed({
						invalidarTodo()
						val sigue = Nodos.vivo(foco) && foco.isVisibleToUser && firma(foco) == antes
						if (sigue) cuadricula(dx, dy, desplazada = true)
						else {
							// El de partida ya no se ve (la lista reutilizó su vista): la primera fila nueva (o la última, hacia arriba)
							val dentro = (lector.raiz()?.let { orden(it) }).orEmpty().filter { Nodos.dentroDe(it, contenedor) }
							val destino = if (dy > 0) dentro.firstOrNull() else dentro.lastOrNull()
							if (destino != null) lector.enfocar(destino) else borde()
						}
					}, ESPERA_VERTICAL_MS)
					return true
				}
			}
			return borde()
		}
		lector.enfocar(candidato)
		return true
	}

	/** Una lista que se desplaza de lado (un carrusel, unas pestañas): ahí arriba y abajo no la mueven. */
	private fun esHorizontal(n: AccessibilityNodeInfo): Boolean {
		val nombre = n.className?.toString()?.substringAfterLast('.').orEmpty()
		if (nombre == "HorizontalScrollView" || nombre == "ViewPager" || nombre == "ViewPager2") return true
		val info = n.collectionInfo ?: return false
		return info.rowCount <= 1 && info.columnCount > 1
	}

	// --- Ventanas -----------------------------------------------------------------------------

	/** Pasa a la ventana siguiente o anterior (barra de estado, app, teclado, diálogos...). */
	fun ventana(adelante: Boolean): Boolean {
		val ventanas = runCatching { lector.servicio.windows }.getOrNull().orEmpty()
			.filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || it.root?.packageName != lector.servicio.packageName }
		if (ventanas.isEmpty()) return borde()
		val actual = lector.foco?.windowId ?: ventanas.firstOrNull { it.isActive }?.id
		val i = ventanas.indexOfFirst { it.id == actual }
		val j = if (i < 0) 0 else (i + if (adelante) 1 else -1 + ventanas.size) % ventanas.size
		val destino = ventanas[j]
		val raiz = destino.root ?: return borde()
		val n = Nodos.ordenLectura(raiz).firstOrNull() ?: return borde()
		lector.sonidos.tocar(Sonido.VENTANA)
		destino.title?.toString()?.takeIf { it.isNotBlank() }?.let { lector.decir(it, Modo.INTERRUMPIR) }
		lector.enfocar(n, null, Modo.ENCOLAR)
		return true
	}

	/** Pasa al primer elemento del contenedor siguiente o anterior (la siguiente lista, la barra inferior...). */
	fun contenedor(adelante: Boolean): Boolean {
		val raiz = lector.raiz() ?: return borde()
		val lista = orden(raiz)
		val foco = lector.foco ?: return primero()
		val i = lista.indexOf(foco)
		if (i < 0) return primero()
		val padre = runCatching { foco.parent }.getOrNull()
		val indices = if (adelante) (i + 1 until lista.size) else (i - 1 downTo 0)
		for (k in indices) {
			val p = runCatching { lista[k].parent }.getOrNull()
			if (p != padre) {
				lector.enfocar(lista[k])
				return true
			}
		}
		return borde()
	}

	fun nombreControl(c: ControlLectura): String {
		val t = lector.textos
		val id = t.resources.getIdentifier("control_" + c.name.lowercase(), "string", t.packageName)
		return if (id != 0) t.getString(id) else c.name.lowercase()
	}

	companion object {
		const val ESPERA_DESPLAZAR_MS = 150L
		/** Lo que se espera, y cuántas veces, a que una lista desplazada termine de colocar sus filas. */
		private const val ESPERA_COLOCAR_MS = 150L
		private const val REINTENTOS_COLOCAR = 2
		/** Lo que se considera «recién desplazada» una lista. */
		private const val RECIEN_DESPLAZADA_MS = 1500L
		/** Lo que tarda el arrastre que adelanta la lista (450 ms de gesto, 120 quietos) y un poco para que se coloque. */
		private const val DURACION_ANTICIPO_MS = 700L
		/** Lo que dura el arrastre de una lista y lo que se quedan quietos los dedos al final (para que no se lance). */
		private const val DURACION_ARRASTRE_MS = 450L
		private const val QUIETOS_MS = 120L
		/** Lo que se espera tras desplazar una página en la navegación vertical antes de buscar la fila siguiente. */
		private const val ESPERA_VERTICAL_MS = 300L
		/** Páginas que se desplazan como mucho al dar la vuelta (entre todas las listas). */
		private const val MAXIMO_PAGINAS_VUELTA = 60
	}
}
