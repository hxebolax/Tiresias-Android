package com.xebolax.tiresias.arbol

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.NodoReparar
import com.xebolax.tiresias.nucleo.Reparacion
import com.xebolax.tiresias.nucleo.Reparaciones
import com.xebolax.tiresias.nucleo.TipoReparacion
import com.xebolax.tiresias.scripts.Scripts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * La reparación automática de las pantallas mal hechas. Tiresias mira la
 * estructura de cada pantalla nueva de una app (la misma que se copia con
 * «Copiar la estructura de la pantalla», sin ningún texto), busca los fallos
 * más comunes ([Reparaciones]) y los arregla con reglas como las de los
 * complementos: por app, por pantalla (la actividad) y por identificador de
 * vista. Lo arreglado se recuerda (en `reparaciones.json`, en el
 * almacenamiento protegido) y vale desde ese momento; se ve y se deshace en
 * Ajustes, Navegación, «Reparaciones automáticas». Lo deshecho no se vuelve a
 * proponer.
 *
 * Se guardan solo paquetes, nombres de pantalla e identificadores de vista:
 * nunca un texto de la pantalla.
 */
class Reparador(contexto: Context) {
	private val archivo = File(Almacen.protegido(contexto).filesDir, "reparaciones.json")

	/** Una reparación guardada: la de [Reparaciones] en la pantalla [actividad] de [paquete]. */
	data class Guardada(val paquete: String, val actividad: String, val tipo: TipoReparacion, val id: String)

	private val hechas = ArrayList<Guardada>()
	/** Las que el usuario ha deshecho: no se vuelven a proponer. */
	private val rechazadas = HashSet<Guardada>()
	@Volatile private var cargadas = false
	/** Las pantallas ya revisadas desde que arrancó Tiresias (cada una, una vez). */
	private val revisadas = HashSet<String>()
	@Volatile var hay = false
		private set

	init {
		cargar()
	}

	@Synchronized
	private fun cargar() {
		if (cargadas) return
		cargadas = true
		if (!archivo.isFile) return
		try {
			val raiz = JSONObject(archivo.readText())
			fun leer(lista: JSONArray?, en: MutableCollection<Guardada>) {
				for (i in 0 until (lista?.length() ?: 0)) {
					val o = lista?.optJSONObject(i) ?: continue
					val tipo = runCatching { TipoReparacion.valueOf(o.optString("tipo")) }.getOrNull() ?: continue
					val p = o.optString("paquete")
					val id = o.optString("id")
					if (p.isBlank() || id.isBlank()) continue
					en += Guardada(p, o.optString("actividad"), tipo, id)
				}
			}
			leer(raiz.optJSONArray("hechas"), hechas)
			leer(raiz.optJSONArray("rechazadas"), rechazadas)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron leer las reparaciones", e)
		}
		hay = hechas.isNotEmpty()
	}

	private fun guardar() {
		try {
			fun lista(de: Collection<Guardada>) = JSONArray().also { a ->
				for (g in de) a.put(JSONObject().put("paquete", g.paquete).put("actividad", g.actividad).put("tipo", g.tipo.name).put("id", g.id))
			}
			val texto = JSONObject().put("formato", "tiresias-reparaciones").put("version", 1)
				.put("hechas", lista(hechas)).put("rechazadas", lista(rechazadas)).toString(1)
			val parcial = File(archivo.path + ".parcial")
			parcial.writeText(texto)
			parcial.renameTo(archivo)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron guardar las reparaciones", e)
		}
		hay = hechas.isNotEmpty()
	}

	/** Las reglas de las reparaciones de [paquete], para el motor de reglas de los complementos ([Scripts]). */
	@Synchronized
	fun reglas(paquete: String): List<Scripts.Regla> {
		cargar()
		if (hechas.isEmpty()) return emptyList()
		return hechas.filter { it.paquete == paquete }.map { g ->
			val actividad = g.actividad.takeIf { it.isNotEmpty() }
			when (g.tipo) {
				TipoReparacion.AGRUPAR_FILA -> Scripts.Regla(id = g.id, agrupar = true, actividad = actividad)
				TipoReparacion.OCULTAR_VACIO, TipoReparacion.OCULTAR_REPETIDO -> Scripts.Regla(id = g.id, ocultar = true, actividad = actividad)
				TipoReparacion.ENCABEZADO -> Scripts.Regla(id = g.id, encabezado = true, actividad = actividad)
				TipoReparacion.UNIR_CON_ANTERIOR -> Scripts.Regla(id = g.id, unirConAnterior = true, actividad = actividad)
			}
		}
	}

	@Synchronized
	fun todas(): List<Guardada> {
		cargar()
		return hechas.toList()
	}

	/** Deshace una reparación (y no se vuelve a proponer). */
	@Synchronized
	fun deshacer(g: Guardada) {
		cargar()
		if (hechas.remove(g)) rechazadas += g
		guardar()
	}

	/** Deshace todas, o las de [paquete]; y lo que se deshizo antes se podrá volver a proponer. */
	@Synchronized
	fun borrar(paquete: String? = null) {
		cargar()
		hechas.removeAll { paquete == null || it.paquete == paquete }
		rechazadas.removeAll { paquete == null || it.paquete == paquete }
		revisadas.clear()
		guardar()
	}

	@Synchronized
	fun olvidar() {
		hechas.clear()
		rechazadas.clear()
		revisadas.clear()
		cargadas = false
		cargar()
	}

	/** Si la pantalla [actividad] de [paquete] ya se ha revisado desde que arrancó Tiresias. */
	@Synchronized
	fun revisada(paquete: String, actividad: String): Boolean = "$paquete/$actividad" in revisadas

	/**
	 * Revisa la ventana de [raiz] (de la pantalla [actividad] de [paquete]) y guarda las reparaciones nuevas.
	 * [paradas] son las paradas de Tiresias en esa ventana, y [huella] da la huella de lo que dice cada una.
	 * Devuelve las nuevas (vacía si no hay nada que reparar o ya estaba).
	 */
	fun revisar(
		raiz: AccessibilityNodeInfo, paquete: String, actividad: String,
		paradas: Set<AccessibilityNodeInfo>, huella: (AccessibilityNodeInfo) -> Int,
	): List<Reparacion> {
		synchronized(this) {
			cargar()
			revisadas += "$paquete/$actividad"
		}
		val arbol = try {
			convertir(raiz, paradas, huella, Contador())
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo leer la pantalla para revisarla", e)
			return emptyList()
		}
		val propuestas = Reparaciones.analizar(arbol)
		if (propuestas.isEmpty()) return emptyList()
		synchronized(this) {
			val nuevas = propuestas.filter { r ->
				val g = Guardada(paquete, actividad, r.tipo, r.id)
				g !in rechazadas && hechas.none { it.paquete == paquete && it.id == r.id && (it.actividad == actividad || it.actividad.isEmpty()) }
			}
			if (nuevas.isEmpty()) return emptyList()
			for (r in nuevas) hechas += Guardada(paquete, actividad, r.tipo, r.id)
			guardar()
			Registro.i(ETIQUETA, "Reparaciones nuevas en $paquete: ${nuevas.joinToString { "${it.tipo}" }}")
			return nuevas
		}
	}

	private class Contador {
		var nodos = 0
	}

	private fun convertir(n: AccessibilityNodeInfo, paradas: Set<AccessibilityNodeInfo>, huella: (AccessibilityNodeInfo) -> Int, c: Contador, nivel: Int = 0): NodoReparar {
		c.nodos++
		val hijos = if (nivel > PROFUNDIDAD || c.nodos > MAXIMO_NODOS) emptyList()
			else (0 until n.childCount).mapNotNull { i -> runCatching { n.getChild(i) }.getOrNull() }
				.filter { it.isVisibleToUser || it.childCount > 0 }
				.map { convertir(it, paradas, huella, c, nivel + 1) }
		val parada = n in paradas
		return NodoReparar(
			clase = n.className?.toString().orEmpty(),
			id = n.viewIdResourceName?.let { it.substringAfter(":id/", it) }?.takeIf { it.isNotBlank() },
			parada = parada,
			accionable = Nodos.accionable(n),
			conTexto = Nodos.tieneTexto(n),
			huella = if (parada) huella(n) else 0,
			encabezado = Nodos.esEncabezado(n),
			marcable = n.isCheckable,
			editable = n.isEditable,
			lista = n.collectionInfo != null || (n.isScrollable && n.rangeInfo == null),
			hijos = hijos,
		)
	}

	companion object {
		/** El nombre de cada tipo de reparación, para decirlo y para la lista. */
		fun nombre(t: TipoReparacion): Int = when (t) {
			TipoReparacion.AGRUPAR_FILA -> com.xebolax.tiresias.R.string.reparar_tipo_agrupar
			TipoReparacion.OCULTAR_VACIO -> com.xebolax.tiresias.R.string.reparar_tipo_vacio
			TipoReparacion.OCULTAR_REPETIDO -> com.xebolax.tiresias.R.string.reparar_tipo_repetido
			TipoReparacion.ENCABEZADO -> com.xebolax.tiresias.R.string.reparar_tipo_encabezado
			TipoReparacion.UNIR_CON_ANTERIOR -> com.xebolax.tiresias.R.string.reparar_tipo_agrupar
		}

		private const val ETIQUETA = "Reparador"
		/** Lo que se mira como mucho de una pantalla (más es una página web o una lista enorme: no merece la pena). */
		private const val MAXIMO_NODOS = 1500
		private const val PROFUNDIDAD = 40
	}
}
