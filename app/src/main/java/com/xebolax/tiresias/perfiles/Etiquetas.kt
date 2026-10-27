package com.xebolax.tiresias.perfiles

import android.content.Context
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Etiquetas que el usuario pone a los elementos sin nombre (o con un nombre que
 * no sirve), por app y por identificador de vista. Se guardan en un JSON en el
 * almacenamiento protegido y se pueden exportar e importar (también en bloque,
 * por app: una de las carencias de TalkBack).
 */
class Etiquetas(contexto: Context) {
	private val archivo = File(Almacen.protegido(contexto).filesDir, "etiquetas.json")
	/** paquete → (identificador → etiqueta) */
	private val datos = HashMap<String, HashMap<String, String>>()
	@Volatile private var cargadas = false

	@Synchronized
	private fun cargar() {
		if (cargadas) return
		cargadas = true
		if (!archivo.isFile) return
		try {
			importarJson(archivo.readText(), reemplazar = true)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron leer las etiquetas", e)
		}
	}

	/** Se olvida lo leído: lo cambió la pantalla Etiquetas o una copia de seguridad, y se vuelve a leer al usarlo. */
	@Synchronized
	fun olvidar() {
		datos.clear()
		cargadas = false
	}

	@Synchronized
	fun de(paquete: String, id: String): String? {
		cargar()
		return datos[paquete]?.get(id)
	}

	@Synchronized
	fun poner(paquete: String, id: String, etiqueta: String?) {
		cargar()
		if (etiqueta.isNullOrBlank()) datos[paquete]?.remove(id) else datos.getOrPut(paquete) { HashMap() }[id] = etiqueta.trim()
		guardar()
	}

	@Synchronized
	fun todas(): Map<String, Map<String, String>> {
		cargar()
		return datos.mapValues { it.value.toMap() }
	}

	@Synchronized
	fun borrarApp(paquete: String) {
		cargar()
		datos.remove(paquete)
		guardar()
	}

	@Synchronized
	fun exportarJson(paquete: String? = null): String {
		cargar()
		val raiz = JSONObject().put("formato", "tiresias-etiquetas").put("version", 1)
		val lista = JSONArray()
		for ((p, mapa) in datos) {
			if (paquete != null && p != paquete) continue
			for ((id, etiqueta) in mapa) lista.put(JSONObject().put("paquete", p).put("id", id).put("etiqueta", etiqueta))
		}
		return raiz.put("etiquetas", lista).toString(1)
	}

	/** Importa un JSON exportado por Tiresias. Devuelve cuántas etiquetas entraron. */
	@Synchronized
	fun importarJson(texto: String, reemplazar: Boolean = false): Int {
		val raiz = JSONObject(texto)
		val lista = raiz.optJSONArray("etiquetas") ?: return 0
		if (reemplazar) datos.clear()
		var n = 0
		for (i in 0 until lista.length()) {
			val o = lista.optJSONObject(i) ?: continue
			val p = o.optString("paquete")
			val id = o.optString("id")
			val e = o.optString("etiqueta")
			if (p.isBlank() || id.isBlank() || e.isBlank()) continue
			datos.getOrPut(p) { HashMap() }[id] = e
			n++
		}
		if (!reemplazar) guardar()
		return n
	}

	private fun guardar() {
		try {
			val parcial = File(archivo.path + ".parcial")
			parcial.writeText(exportarSinCargar())
			parcial.renameTo(archivo)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron guardar las etiquetas", e)
		}
	}

	private fun exportarSinCargar(): String {
		val lista = JSONArray()
		for ((p, mapa) in datos) for ((id, etiqueta) in mapa) lista.put(JSONObject().put("paquete", p).put("id", id).put("etiqueta", etiqueta))
		return JSONObject().put("formato", "tiresias-etiquetas").put("version", 1).put("etiquetas", lista).toString(1)
	}

	companion object {
		private const val ETIQUETA = "Etiquetas"
	}
}
