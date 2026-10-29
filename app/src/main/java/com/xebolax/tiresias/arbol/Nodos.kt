package com.xebolax.tiresias.arbol

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Rol

/**
 * Ayudas sobre los nodos de accesibilidad: rol, si reciben el foco, su texto.
 *
 * Cada llamada a un nodo puede ser una consulta a la app que se está leyendo
 * (entre procesos), así que se leen las propiedades una vez y se guarda lo que
 * se necesita. Todo se usa desde el hilo del lector.
 */
object Nodos {
	private const val ETIQUETA = "Nodos"
	private const val PROFUNDIDAD_MAXIMA = 60

	/** El rol de [n] por su clase (como `Role.java` de TalkBack), su rol web y sus propiedades. */
	fun rol(n: AccessibilityNodeInfo): Rol {
		val clase = n.className?.toString().orEmpty()
		val web = rolWeb(n)
		if (web != null) return web
		return when {
			n.isEditable || clase.endsWith("EditText") || clase.endsWith("AutoCompleteTextView") -> Rol.CAMPO_TEXTO
			clase.endsWith("Switch") || clase.endsWith("SwitchCompat") || clase.endsWith("SwitchMaterial") || clase.endsWith("MaterialSwitch") -> Rol.INTERRUPTOR
			clase.endsWith("ToggleButton") -> Rol.BOTON_ALTERNAR
			clase.endsWith("CheckBox") || clase.endsWith("MaterialCheckBox") -> Rol.CASILLA
			clase.endsWith("CheckedTextView") -> if (n.isCheckable) Rol.CASILLA else Rol.TEXTO
			clase.endsWith("RadioButton") || clase.endsWith("MaterialRadioButton") -> Rol.OPCION
			clase.endsWith("SeekBar") || clase.endsWith("Slider") || clase.endsWith("RangeSlider") || clase.endsWith("RatingBar") -> Rol.DESLIZADOR
			clase.endsWith("ProgressBar") || clase.endsWith("LinearProgressIndicator") || clase.endsWith("CircularProgressIndicator") ->
				if (n.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id }) Rol.DESLIZADOR else Rol.BARRA_PROGRESO
			clase.endsWith("Spinner") -> Rol.DESPLEGABLE
			clase.endsWith("ImageButton") || clase.endsWith("FloatingActionButton") -> Rol.BOTON_IMAGEN
			clase.endsWith("Chip") -> Rol.CHIP
			clase.endsWith("Button") || clase.endsWith("MaterialButton") -> Rol.BOTON
			clase.endsWith("ImageView") || clase.endsWith("Image") -> if (n.isClickable) Rol.BOTON_IMAGEN else Rol.IMAGEN
			clase.endsWith("WebView") -> Rol.WEB
			clase.endsWith("ViewPager") || clase.endsWith("ViewPager2") -> Rol.PAGINADOR
			clase.endsWith("TabWidget") || clase.endsWith("TabLayout") -> Rol.LISTA_PESTANAS
			clase.endsWith("\$TabView") || clase.endsWith("TabView") -> Rol.PESTANA
			clase.endsWith("GridView") -> Rol.CUADRICULA
			clase.endsWith("ListView") || clase.endsWith("RecyclerView") -> coleccion(n)
			clase.endsWith("ScrollView") || clase.endsWith("HorizontalScrollView") || clase.endsWith("NestedScrollView") -> Rol.DESPLAZABLE
			clase.endsWith("Toolbar") || clase.endsWith("ActionBar") -> Rol.BARRA_HERRAMIENTAS
			clase.endsWith("NumberPicker") -> Rol.SELECTOR_NUMERO
			clase.endsWith("DatePicker") -> Rol.SELECTOR_FECHA
			clase.endsWith("TimePicker") -> Rol.SELECTOR_HORA
			clase.endsWith("CalendarView") -> Rol.CALENDARIO
			clase.endsWith("VideoView") || clase.endsWith("PlayerView") -> Rol.VIDEO
			clase.endsWith("MapView") -> Rol.MAPA
			clase.contains("Dialog") -> Rol.DIALOGO
			clase.endsWith("TextView") -> Rol.TEXTO
			n.isCheckable && n.isClickable -> Rol.CASILLA
			n.isClickable && clase.endsWith("View") && n.childCount == 0 -> Rol.BOTON
			else -> Rol.NINGUNO
		}
	}

	private fun coleccion(n: AccessibilityNodeInfo): Rol {
		val c = n.collectionInfo ?: return Rol.LISTA
		return if (c.columnCount > 1 && c.rowCount > 1) Rol.CUADRICULA else Rol.LISTA
	}

	/**
	 * Chrome y WebView dicen el rol HTML en los extras (`AccessibilityNodeInfo.chromeRole`).
	 * Así se distinguen los enlaces, los encabezados y las regiones.
	 */
	fun rolWeb(n: AccessibilityNodeInfo): Rol? {
		val extras = runCatching { n.extras }.getOrNull() ?: return null
		val rol = extras.getCharSequence("AccessibilityNodeInfo.chromeRole")?.toString() ?: return null
		return when (rol) {
			"link" -> Rol.ENLACE
			"button", "popUpButton" -> Rol.BOTON
			"checkBox" -> Rol.CASILLA
			"switch" -> Rol.INTERRUPTOR
			"radioButton" -> Rol.OPCION
			"textField", "searchBox", "textFieldWithComboBox" -> Rol.CAMPO_TEXTO
			"slider" -> Rol.DESLIZADOR
			"progressIndicator", "meter" -> Rol.BARRA_PROGRESO
			"comboBoxSelect", "comboBoxMenuButton", "listBox" -> Rol.DESPLEGABLE
			"image", "img", "svgRoot", "graphicsDocument" -> Rol.IMAGEN
			"list", "descriptionList" -> Rol.LISTA
			"listItem", "listBoxOption" -> Rol.ELEMENTO_LISTA
			"table", "grid", "treeGrid" -> Rol.TABLA
			"cell", "gridCell", "columnHeader", "rowHeader" -> Rol.CELDA
			"tab" -> Rol.PESTANA
			"tabList" -> Rol.LISTA_PESTANAS
			"menu", "menuBar" -> Rol.MENU
			"menuItem", "menuItemCheckBox", "menuItemRadio" -> Rol.ELEMENTO_MENU
			"dialog", "alertDialog" -> Rol.DIALOGO
			"alert" -> Rol.ALERTA
			"navigation" -> Rol.NAVEGACION
			"banner", "contentInfo", "main", "complementary", "form", "region", "search" -> Rol.REGION
			"toolbar" -> Rol.BARRA_HERRAMIENTAS
			"status", "log", "timer" -> Rol.ESTADO
			"date", "dateTime" -> Rol.SELECTOR_FECHA
			"inputTime" -> Rol.SELECTOR_HORA
			"video", "audio" -> Rol.VIDEO
			"heading" -> Rol.TEXTO
			else -> null
		}
	}

	/** Si [n] es un encabezado (Android 9+, o el rol web, o un elemento de colección marcado). */
	fun esEncabezado(n: AccessibilityNodeInfo): Boolean {
		if (Build.VERSION.SDK_INT >= 28 && n.isHeading) return true
		@Suppress("DEPRECATION")
		if (n.collectionItemInfo?.isHeading == true) return true
		return runCatching { n.extras?.getCharSequence("AccessibilityNodeInfo.chromeRole")?.toString() == "heading" }.getOrDefault(false)
	}

	/** El nivel de un encabezado web (1 a 6) por su descripción de rol («encabezado 2», «heading 2»), o 0. */
	fun nivelEncabezado(n: AccessibilityNodeInfo): Int {
		val descripcion = runCatching { n.extras?.getCharSequence("AccessibilityNodeInfo.roleDescription")?.toString() }.getOrNull()
		return descripcion?.let { Regex("([1-6])").find(it)?.value?.toIntOrNull() } ?: 0
	}

	/** Si [n] se puede pulsar, mantener, marcar o editar: entonces sus hijos se leen dentro de él. */
	fun accionable(n: AccessibilityNodeInfo): Boolean =
		n.isClickable || n.isLongClickable || n.isEditable ||
			n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK || it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }

	/**
	 * Una casilla o un interruptor que no se pulsa por sí mismo y está dentro de
	 * algo que sí (el interruptor de una fila de ajustes): no es una parada aparte,
	 * lo dice la fila con su estado. Como en TalkBack, marcable no es pulsable.
	 */
	fun marcableDeFila(n: AccessibilityNodeInfo): Boolean = n.isCheckable && !accionable(n) && antepasadoQueLee(n)

	/** Si la lista acepta desplazarse por accesibilidad (en alguna dirección): si no, hay que arrastrarla. */
	fun seDesplazaPorAccesibilidad(lista: AccessibilityNodeInfo): Boolean {
		val ids = setOf(
			AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
			android.R.id.accessibilityActionScrollUp, android.R.id.accessibilityActionScrollDown,
			android.R.id.accessibilityActionScrollLeft, android.R.id.accessibilityActionScrollRight,
		)
		return lista.actionList.any { it.id in ids }
	}

	/** Si [n] está dentro de [contenedor] (es él o un descendiente). */
	fun dentroDe(n: AccessibilityNodeInfo, contenedor: AccessibilityNodeInfo): Boolean {
		var p: AccessibilityNodeInfo? = n
		var nivel = 0
		while (p != null && nivel < 25) {
			if (p == contenedor) return true
			p = runCatching { p!!.parent }.getOrNull()
			nivel++
		}
		return false
	}

	/** El único interruptor o casilla «de fila» dentro de [n] (hasta tres niveles), o null. */
	fun marcableInterior(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		var encontrado: AccessibilityNodeInfo? = null
		fun buscar(x: AccessibilityNodeInfo, nivel: Int): Boolean {
			if (nivel > 3) return true
			for (i in 0 until x.childCount) {
				val h = runCatching { x.getChild(i) }.getOrNull() ?: continue
				if (h.isCheckable && !accionable(h)) {
					if (encontrado != null) return false
					encontrado = h
				} else if (!accionable(h) && !buscar(h, nivel + 1)) return false
			}
			return true
		}
		return if (buscar(n, 1)) encontrado else null
	}

	/** Si [n] tiene algo que decir por sí mismo (texto, descripción, estado o es un control). */
	fun tieneTexto(n: AccessibilityNodeInfo): Boolean =
		!n.contentDescription.isNullOrBlank() || !n.text.isNullOrBlank() ||
			(Build.VERSION.SDK_INT >= 30 && !n.stateDescription.isNullOrBlank())

	/**
	 * Si [n] recibe el foco al navegar (la regla de `shouldFocusNode` de TalkBack,
	 * simplificada): visible y, o bien es un control, o tiene texto y ningún
	 * antepasado que lo vaya a leer por él.
	 */
	fun recibeFoco(n: AccessibilityNodeInfo): Boolean {
		if (!n.isVisibleToUser) return false
		if (Build.VERSION.SDK_INT >= 28 && n.isScreenReaderFocusable) return true
		val rol = rol(n)
		if (rol == Rol.WEB && n.childCount > 0) return false
		if (accionable(n)) return true
		// Una casilla suelta (sin fila pulsable que la lea) sí es una parada
		if (n.isCheckable) return !antepasadoQueLee(n)
		if (n.isFocusable && tieneTexto(n)) return true
		if (rol == Rol.DESLIZADOR || rol == Rol.BARRA_PROGRESO || rol == Rol.IMAGEN && tieneTexto(n)) return true
		if (!tieneTexto(n) && !esEncabezado(n)) return false
		// Texto suelto: no si un antepasado pulsable lo va a leer como parte de él
		return !antepasadoQueLee(n)
	}

	/**
	 * La parte de [n] que se ve dentro de su ventana, después de pedir que se
	 * muestre (si estaba a medias fuera de la lista). Vacía si no se ve nada.
	 */
	fun visibleEnVentana(n: AccessibilityNodeInfo): android.graphics.Rect {
		if (!n.isVisibleToUser || limites(n).let { r -> r.width() <= 0 || r.height() <= 0 }) {
			runCatching { n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id) }
			runCatching { n.refresh() }
		}
		val r = limites(n)
		val ventana = runCatching { n.window }.getOrNull() ?: return r
		val v = android.graphics.Rect()
		ventana.getBoundsInScreen(v)
		return if (r.intersect(v)) r else android.graphics.Rect()
	}

	/** Si algún antepasado (hasta la colección o la ventana) es un control que leerá a [n] con él. */
	fun antepasadoQueLee(n: AccessibilityNodeInfo): Boolean {
		var p = runCatching { n.parent }.getOrNull()
		var nivel = 0
		while (p != null && nivel < 12) {
			if (Build.VERSION.SDK_INT >= 28 && p.isScreenReaderFocusable) return true
			if (accionable(p) && p.isVisibleToUser) return true
			// Un encabezado sin texto propio (la fila de una categoría de ajustes) se lee con su texto dentro:
			// una sola parada, no el encabezado y luego su texto otra vez
			if (esEncabezado(p) && !tieneTexto(p) && p.isVisibleToUser) return true
			if (p.collectionInfo != null) return false
			p = runCatching { p!!.parent }.getOrNull()
			nivel++
		}
		return false
	}

	/** El primer antepasado de [n] (o él mismo) que recibe el foco: el que se lee al tocar. */
	fun enfocable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		var actual: AccessibilityNodeInfo? = n
		var nivel = 0
		var candidato: AccessibilityNodeInfo? = null
		while (actual != null && nivel < 15) {
			if (accionable(actual) && actual.isVisibleToUser) return actual
			if (candidato == null && recibeFoco(actual)) candidato = actual
			actual = runCatching { actual!!.parent }.getOrNull()
			nivel++
		}
		return candidato
	}

	/**
	 * El texto de los hijos de un contenedor que se lee junto a él (filas de
	 * lista, botones con texto dentro), sin los hijos que reciben el foco por su
	 * cuenta. Como hace TalkBack con la descripción del árbol.
	 */
	fun textoHijos(n: AccessibilityNodeInfo, maximo: Int = 600): String {
		val sb = StringBuilder()
		val dichos = HashSet<String>()
		textoHijos(n, sb, 0, maximo, false, dichos)
		// Una fila medio fuera de la pantalla (al final de una lista que aún se está desplazando) tiene
		// sus textos todavía sin ver: antes que «sin nombre», se leen aunque no se vean
		if (sb.isBlank() && n.isVisibleToUser) textoHijos(n, sb, 0, maximo, true, dichos)
		// (sin comas seguidas, que dejan los textos que acaban en coma)
		return sb.toString().replace(Regex("\\s+"), " ").replace(Regex(",(\\s*,)+"), ",").trim().trimEnd(',')
	}

	private fun textoHijos(n: AccessibilityNodeInfo, sb: StringBuilder, nivel: Int, maximo: Int, ocultos: Boolean, dichos: MutableSet<String>) {
		if (nivel > 8 || sb.length > maximo) return
		for (i in 0 until n.childCount) {
			val hijo = runCatching { n.getChild(i) }.getOrNull() ?: continue
			if (!ocultos && !hijo.isVisibleToUser) continue
			// Un hijo que es un control con nombre propio (un botón «Borrar» en una fila) se lee aparte
			if (accionable(hijo) && esControlIndependiente(hijo)) continue
			val propio = when {
				hijo.isPassword -> hijo.contentDescription
				!hijo.contentDescription.isNullOrBlank() -> hijo.contentDescription
				else -> hijo.text
			}
			if (!propio.isNullOrBlank()) {
				// El mismo texto otra vez (el título y el deslizador que lo repite como descripción) no se dice dos veces
				// (solo frases con letras: un marcador «2» y «2» sí se dice entero)
				val clave = propio.toString().trim().trimEnd(',').lowercase()
				if (clave.length >= 4 && clave.any { it.isLetter() } && !dichos.add(clave)) continue
				if (sb.isNotEmpty()) sb.append(", ")
				sb.append(propio)
				if (Build.VERSION.SDK_INT >= 30) hijo.stateDescription?.takeIf { it.isNotBlank() }?.let { sb.append(", ").append(it) }
			} else textoHijos(hijo, sb, nivel + 1, maximo, ocultos, dichos)
		}
	}

	/** Un control dentro de una fila que tiene su propio nombre (un botón «Borrar» en una fila de lista). */
	private fun esControlIndependiente(n: AccessibilityNodeInfo): Boolean {
		val clase = n.className?.toString().orEmpty()
		return n.isCheckable || n.isEditable || clase.endsWith("Button") || clase.endsWith("ImageButton") || clase.endsWith("Switch") || clase.endsWith("CheckBox")
	}

	/**
	 * Los nodos que reciben el foco en la ventana de [raiz], en orden de lectura
	 * (el del árbol, que es el de las apps bien hechas; respeta
	 * `accessibilityTraversalBefore/After` en la medida en que Android lo aplica al árbol).
	 */
	fun ordenLectura(raiz: AccessibilityNodeInfo, maximo: Int = 2000, agrupa: ((AccessibilityNodeInfo) -> Boolean)? = null): List<AccessibilityNodeInfo> {
		val lista = ArrayList<AccessibilityNodeInfo>()
		try {
			recorrer(raiz, lista, 0, maximo, agrupa, repartir = true)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Fallo al recorrer la ventana", e)
		}
		return lista
	}

	/**
	 * Hilos para leer en paralelo las ramas de un contenedor con muchos hijos (la
	 * persiana de One UI tardaba de 300 a 600 ms por deslizamiento: Backtalk 229).
	 * Cada pregunta a la app es una llamada entre procesos; varias a la vez van antes.
	 */
	private val repartidor = java.util.concurrent.Executors.newFixedThreadPool(4) { r ->
		Thread(r, "tiresias-arbol").apply { isDaemon = true }
	}
	private const val HIJOS_PARA_REPARTIR = 6
	private const val NIVEL_MAXIMO_REPARTO = 8

	private fun recorrer(
		n: AccessibilityNodeInfo, lista: MutableList<AccessibilityNodeInfo>, nivel: Int, maximo: Int,
		agrupa: ((AccessibilityNodeInfo) -> Boolean)?, repartir: Boolean = false,
	) {
		if (nivel > PROFUNDIDAD_MAXIMA || lista.size >= maximo) return
		if (!n.isVisibleToUser && n.childCount == 0) return
		// Un elemento que una regla agrupa: una sola parada con todo lo de dentro
		if (agrupa != null && n.isVisibleToUser && agrupa(n)) {
			lista += n
			return
		}
		val foco = recibeFoco(n)
		if (foco) lista += n
		// Un control con texto dentro: sus hijos se leen con él, no aparte (salvo controles propios dentro)
		val leeHijos = foco && accionable(n)
		val hijos = (0 until n.childCount).mapNotNull { i -> runCatching { n.getChild(i) }.getOrNull() }
			.filter { hijo -> !(leeHijos && !esControlIndependiente(hijo) && !tieneControlDentro(hijo)) }
		// Muchos hijos (una lista larga, la persiana): cada rama en un hilo, y se juntan en su orden
		if (repartir && hijos.size >= HIJOS_PARA_REPARTIR && nivel <= NIVEL_MAXIMO_REPARTO) {
			val ramas = runCatching {
				repartidor.invokeAll(hijos.map { hijo ->
					java.util.concurrent.Callable {
						val rama = ArrayList<AccessibilityNodeInfo>()
						runCatching { recorrer(hijo, rama, nivel + 1, maximo, agrupa) }
							.onFailure { Registro.w(ETIQUETA, "Fallo al leer una rama de la ventana", it) }
						rama
					}
				})
			}.getOrNull()
			if (ramas != null) {
				for (r in ramas) {
					lista += runCatching { r.get() }.getOrDefault(emptyList())
					if (lista.size >= maximo) break
				}
				return
			}
		}
		for (hijo in hijos) recorrer(hijo, lista, nivel + 1, maximo, agrupa, repartir)
	}

	private fun tieneControlDentro(n: AccessibilityNodeInfo, nivel: Int = 0): Boolean {
		if (nivel > 4) return false
		for (i in 0 until n.childCount) {
			val h = runCatching { n.getChild(i) }.getOrNull() ?: continue
			if (esControlIndependiente(h) && h.isVisibleToUser) return true
			if (tieneControlDentro(h, nivel + 1)) return true
		}
		return false
	}

	/** El antepasado desplazable más cercano de [n] (para desplazar la lista en la que está). */
	fun desplazable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
		var p: AccessibilityNodeInfo? = n
		var nivel = 0
		while (p != null && nivel < 20) {
			// Un deslizador «se desplaza» cambiando su valor: no es una lista
			if (p.isScrollable && p.rangeInfo == null) return p
			p = runCatching { p!!.parent }.getOrNull()
			nivel++
		}
		return null
	}

	/** El primer nodo desplazable visible de la ventana (si el foco no está en ninguna lista). */
	fun primerDesplazable(raiz: AccessibilityNodeInfo, nivel: Int = 0): AccessibilityNodeInfo? {
		if (nivel > 30) return null
		if (raiz.isScrollable && raiz.isVisibleToUser && raiz.rangeInfo == null) return raiz
		for (i in 0 until raiz.childCount) {
			val h = runCatching { raiz.getChild(i) }.getOrNull() ?: continue
			primerDesplazable(h, nivel + 1)?.let { return it }
		}
		return null
	}

	/**
	 * Los enlaces que hay dentro del texto de [n], con su texto, en orden: los
	 * «Más información» o «Instalar de todas formas» en medio de una frase, que
	 * no son elementos propios (Android los da como ClickableSpan del texto).
	 */
	fun enlacesEnTexto(n: AccessibilityNodeInfo): List<Pair<String, android.text.style.ClickableSpan>> {
		val lista = ArrayList<Pair<String, android.text.style.ClickableSpan>>()
		enlacesDe(n, lista)
		// También los de dentro que no son paradas propias (el texto de un aviso dentro de su fila), como TalkBack
		// (sin recorrer una lista entera: 40 nodos como mucho)
		var vistos = 0
		fun dentro(p: AccessibilityNodeInfo, nivel: Int) {
			if (nivel > 6 || lista.size >= 20) return
			for (i in 0 until p.childCount) {
				if (++vistos > 40) return
				val h = runCatching { p.getChild(i) }.getOrNull() ?: continue
				if (!h.isVisibleToUser || h.isFocusable || h.isClickable || h.isScreenReaderFocusableCompat()) continue
				enlacesDe(h, lista)
				dentro(h, nivel + 1)
			}
		}
		dentro(n, 0)
		return lista
	}

	private fun AccessibilityNodeInfo.isScreenReaderFocusableCompat(): Boolean = Build.VERSION.SDK_INT >= 28 && isScreenReaderFocusable

	private fun enlacesDe(n: AccessibilityNodeInfo, lista: MutableList<Pair<String, android.text.style.ClickableSpan>>) {
		val texto = (runCatching { n.text }.getOrNull() as? android.text.Spanned)
			?: (runCatching { n.contentDescription }.getOrNull() as? android.text.Spanned) ?: return
		texto.getSpans(0, texto.length, android.text.style.ClickableSpan::class.java)
			.map { s -> Triple(texto.getSpanStart(s), texto.getSpanEnd(s), s) }
			.filter { (a, b, _) -> a >= 0 && b > a }
			.sortedBy { it.first }
			.mapNotNullTo(lista) { (a, b, s) -> texto.subSequence(a, b).toString().trim().takeIf { it.isNotEmpty() }?.let { it to s } }
	}

	/** Si [n] tiene enlaces dentro de su texto. */
	fun tieneEnlacesEnTexto(n: AccessibilityNodeInfo): Boolean {
		val texto = (runCatching { n.text }.getOrNull() as? android.text.Spanned)
			?: (runCatching { n.contentDescription }.getOrNull() as? android.text.Spanned) ?: return false
		return texto.getSpans(0, texto.length, android.text.style.ClickableSpan::class.java).isNotEmpty()
	}

	/** Una falta que marca el corrector en lo escrito: dónde está, la palabra y lo que propone. */
	data class Falta(val inicio: Int, val fin: Int, val palabra: String, val sugerencias: List<String>)

	/**
	 * Las faltas de ortografía (y de gramática, Android 12 o posterior) que el
	 * corrector del teclado ha marcado en lo escrito en [n], en orden: Android las
	 * da como SuggestionSpan del texto, como las ve TalkBack.
	 */
	fun faltas(n: AccessibilityNodeInfo): List<Falta> {
		if (!n.isEditable) return emptyList()
		val texto = runCatching { n.text }.getOrNull() as? android.text.Spanned ?: return emptyList()
		val marcas = android.text.style.SuggestionSpan.FLAG_MISSPELLED or (if (Build.VERSION.SDK_INT >= 31) android.text.style.SuggestionSpan.FLAG_GRAMMAR_ERROR else 0)
		return texto.getSpans(0, texto.length, android.text.style.SuggestionSpan::class.java)
			.filter { it.flags and marcas != 0 }
			.map { s -> Triple(texto.getSpanStart(s), texto.getSpanEnd(s), s) }
			.filter { (a, b, _) -> a >= 0 && b > a && b <= texto.length }
			.sortedBy { it.first }
			.distinctBy { it.first }
			.map { (a, b, s) -> Falta(a, b, texto.subSequence(a, b).toString(), s.suggestions.orEmpty().filter { it.isNotBlank() }.distinct().take(3)) }
	}

	/** Las listas desplazables que se ven en la ventana, de fuera a dentro (los deslizadores no). */
	fun desplazables(raiz: AccessibilityNodeInfo, nivel: Int = 0, lista: MutableList<AccessibilityNodeInfo> = ArrayList()): List<AccessibilityNodeInfo> {
		if (nivel > 30 || lista.size >= 8) return lista
		if (raiz.isScrollable && raiz.isVisibleToUser && raiz.rangeInfo == null) lista += raiz
		for (i in 0 until raiz.childCount) {
			val h = runCatching { raiz.getChild(i) }.getOrNull() ?: continue
			desplazables(h, nivel + 1, lista)
		}
		return lista
	}

	/** El rectángulo de [n] en la pantalla. */
	fun limites(n: AccessibilityNodeInfo): Rect = Rect().also { n.getBoundsInScreen(it) }

	/** Si [n] todavía existe en su ventana (refresh devuelve false si la app lo ha quitado). */
	fun vivo(n: AccessibilityNodeInfo): Boolean = runCatching { n.refresh() }.getOrDefault(false)

	/**
	 * Un botón que se usa manteniéndolo pulsado: grabar una nota de voz en WhatsApp
	 * o Telegram, hablar en un walkie. Ahí el doble toque mantenido tiene que dejarlo
	 * pulsado de verdad hasta que se levanta el dedo (una pulsación larga no basta:
	 * la app graba medio segundo y lo descarta). Se reconoce por su identificador o
	 * por su nombre.
	 */
	fun esDeMantenerPulsado(n: AccessibilityNodeInfo): Boolean {
		// (el de WhatsApp no se dice pulsable ni de pulsación larga: solo trae la acción de pulsación larga)
		if (!accionable(n)) return false
		val id = n.viewIdResourceName?.substringAfter(":id/", "")?.lowercase().orEmpty()
		if (id.isNotEmpty() && ID_MANTENER.containsMatchIn(id)) return true
		val nombre = (n.contentDescription ?: n.text)?.toString().orEmpty()
		return nombre.length <= 60 && NOMBRE_MANTENER.containsMatchIn(nombre)
	}

	private val ID_MANTENER = Regex("(^|_)(voice_?note|ptt|record\\w*|mic|microphone)(_|$)")
	private val NOMBRE_MANTENER = Regex(
		"(?i)\\b(mensajes? de voz|notas? de voz|grabar audio|graba(r|ción) de voz|mantén pulsado para grabar|" +
			"voice (message|note)s?|hold to record|record (audio|voice)|push to talk|pulsa para hablar)\\b",
	)

	/** Una contraseña o lo que la app marca como datos sensibles (Android 14). */
	fun sensible(n: AccessibilityNodeInfo): Boolean =
		n.isPassword || (android.os.Build.VERSION.SDK_INT >= 34 && runCatching { n.isAccessibilityDataSensitive }.getOrDefault(false))

	/**
	 * Si en [raiz] (hasta [maximo] nodos) hay algo que la app marca como datos
	 * sensibles (Android 14): entonces la pantalla no se captura para la IA ni el
	 * OCR. Un campo de contraseña visible no basta (sale tapado con puntos): si no,
	 * no se podría describir ninguna pantalla de inicio de sesión.
	 */
	fun haySensibles(raiz: AccessibilityNodeInfo?, maximo: Int = 600): Boolean {
		if (android.os.Build.VERSION.SDK_INT < 34) return false
		raiz ?: return false
		val cola = ArrayDeque<AccessibilityNodeInfo>()
		cola.add(raiz)
		var vistos = 0
		while (cola.isNotEmpty() && vistos++ < maximo) {
			val n = cola.removeFirst()
			if (runCatching { n.isAccessibilityDataSensitive }.getOrDefault(false) && n.isVisibleToUser) return true
			for (i in 0 until n.childCount) runCatching { n.getChild(i) }.getOrNull()?.let { cola.add(it) }
		}
		return false
	}
}
