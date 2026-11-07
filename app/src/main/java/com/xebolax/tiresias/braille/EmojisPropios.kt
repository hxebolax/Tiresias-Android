package com.xebolax.tiresias.braille

import android.content.Context
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.braille.BuscadorEmojis
import org.json.JSONArray
import org.json.JSONObject

/** Una categoría de emojis hecha por el usuario («Familia», «Trabajo»), con sus emojis en orden. */
data class CategoriaEmojis(val nombre: String, val emojis: List<String>)

/**
 * Los emojis propios del usuario: los favoritos (sin categoría, los primeros al
 * explorar) y las categorías que crea, cambia de nombre y borra (en Ajustes,
 * Braille, «Mis emojis»), donde guarda emojis desde el teclado braille (tres
 * dedos a la derecha) o desde esa pantalla. Es configuración, no texto de la
 * pantalla.
 */
object EmojisPropios {
	private const val ETIQUETA = "EmojisPropios"
	private const val CLAVE = "braille_categorias_emoji"
	/** Como mucho, por categoría y en favoritos. */
	const val MAXIMO = 200

	fun categorias(a: Ajustes): List<CategoriaEmojis> {
		val texto = a.p.getString(CLAVE, "").orEmpty()
		if (texto.isBlank()) return emptyList()
		return try {
			val arr = JSONArray(texto)
			(0 until arr.length()).mapNotNull { i ->
				val o = arr.optJSONObject(i) ?: return@mapNotNull null
				val nombre = o.optString("nombre").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
				val e = o.optJSONArray("emojis") ?: JSONArray()
				CategoriaEmojis(nombre, (0 until e.length()).map { e.optString(it) }.filter { it.isNotEmpty() })
			}
		} catch (e: org.json.JSONException) {
			Registro.w(ETIQUETA, "Las categorías de emojis no se pudieron leer", e)
			emptyList()
		}
	}

	private fun guardar(a: Ajustes, lista: List<CategoriaEmojis>) {
		val arr = JSONArray()
		for (c in lista) arr.put(JSONObject().put("nombre", c.nombre).put("emojis", JSONArray(c.emojis.take(MAXIMO))))
		a.p.edit().putString(CLAVE, arr.toString()).apply()
	}

	private fun existe(lista: List<CategoriaEmojis>, nombre: String, salvo: String? = null) =
		lista.any { it.nombre.equals(nombre.trim(), ignoreCase = true) && !it.nombre.equals(salvo, ignoreCase = true) }

	/** Crea una categoría vacía; false si ya hay una con ese nombre o está en blanco. */
	fun crear(a: Ajustes, nombre: String): Boolean {
		val lista = categorias(a)
		if (nombre.isBlank() || existe(lista, nombre)) return false
		guardar(a, lista + CategoriaEmojis(nombre.trim(), emptyList()))
		return true
	}

	fun renombrar(a: Ajustes, antes: String, nuevo: String): Boolean {
		val lista = categorias(a)
		if (nuevo.isBlank() || existe(lista, nuevo, salvo = antes)) return false
		guardar(a, lista.map { if (it.nombre == antes) it.copy(nombre = nuevo.trim()) else it })
		return true
	}

	fun borrar(a: Ajustes, nombre: String) = guardar(a, categorias(a).filter { it.nombre != nombre })

	/** Pone [emoji] en [categoria] o lo quita si ya está; devuelve si queda puesto. */
	fun alternar(a: Ajustes, categoria: String, emoji: String): Boolean {
		var puesto = false
		guardar(a, categorias(a).map { c ->
			if (c.nombre != categoria) c
			else if (emoji in c.emojis) c.copy(emojis = c.emojis - emoji)
			else {
				puesto = true
				c.copy(emojis = c.emojis + emoji)
			}
		})
		return puesto
	}

	fun quitar(a: Ajustes, categoria: String, emoji: String) =
		guardar(a, categorias(a).map { if (it.nombre == categoria) it.copy(emojis = it.emojis - emoji) else it })

	/** Mueve [emoji] de una categoría a otra (sin repetirlo). */
	fun mover(a: Ajustes, desde: String, hacia: String, emoji: String) =
		guardar(a, categorias(a).map { c ->
			when (c.nombre) {
				desde -> c.copy(emojis = c.emojis - emoji)
				hacia -> if (emoji in c.emojis) c else c.copy(emojis = c.emojis + emoji)
				else -> c
			}
		})

	/** Sube o baja [emoji] un puesto dentro de [categoria]. */
	fun desplazar(a: Ajustes, categoria: String, emoji: String, paso: Int) =
		guardar(a, categorias(a).map { c ->
			if (c.nombre != categoria) return@map c
			val l = c.emojis.toMutableList()
			val i = l.indexOf(emoji)
			val j = (i + paso).coerceIn(0, l.lastIndex)
			if (i < 0 || i == j) return@map c
			l.removeAt(i)
			l.add(j, emoji)
			c.copy(emojis = l)
		})

	// --- Favoritos (los de siempre: sin categoría) --------------------------------------

	/** Pone o quita [emoji] de favoritos; devuelve si queda puesto. */
	fun alternarFavorito(a: Ajustes, emoji: String): Boolean {
		val ahora = a.emojisFavoritos.toMutableList()
		val quitar = emoji in ahora
		if (quitar) ahora.remove(emoji) else ahora.add(0, emoji)
		a.emojisFavoritos = ahora.take(MAXIMO)
		return !quitar
	}

	fun desplazarFavorito(a: Ajustes, emoji: String, paso: Int) {
		val l = a.emojisFavoritos.toMutableList()
		val i = l.indexOf(emoji)
		val j = (i + paso).coerceIn(0, l.lastIndex)
		if (i < 0 || i == j) return
		l.removeAt(i)
		l.add(j, emoji)
		a.emojisFavoritos = l
	}
}

/** La base de emojis de Tiresias (`emojis.tsv` del idioma de Tiresias, o del español), leída una vez por idioma. */
object BaseEmojis {
	private const val ETIQUETA = "BaseEmojis"
	@Volatile private var idioma = ""
	@Volatile private var buscador: BuscadorEmojis? = null
	@Volatile private var nombres: Map<String, String> = emptyMap()

	@Synchronized
	fun buscador(c: Context): BuscadorEmojis {
		val ahora = Idioma.efectivo(c).language
		buscador?.let { if (idioma == ahora) return it }
		fun leer(codigo: String) = try {
			c.assets.open("idiomas/$codigo/emojis.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }
		} catch (e: java.io.IOException) {
			Registro.w(ETIQUETA, "Sin base de emojis para $codigo", e)
			null
		}
		val tabla = BuscadorEmojis.leerTabla(leer(ahora) ?: leer("es").orEmpty())
		val b = BuscadorEmojis(tabla)
		nombres = tabla.toMap()
		buscador = b
		idioma = ahora
		return b
	}

	/** El nombre de un emoji («cara sonriendo»); si no está en la base, el propio emoji. */
	fun nombre(c: Context, emoji: String): String {
		buscador(c)
		return nombres[emoji] ?: nombres[emoji.replace("️", "")] ?: emoji
	}
}
