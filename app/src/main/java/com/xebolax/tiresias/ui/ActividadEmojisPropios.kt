package com.xebolax.tiresias.ui

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.Accion
import com.xebolax.tiresias.accesibilidad.accionesAccesibles
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.lectorActivo
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.braille.BaseEmojis
import com.xebolax.tiresias.braille.EmojisPropios

/**
 * Mis emojis: los favoritos (sin categoría) y las categorías propias del
 * teclado braille. Se crean, se cambian de nombre y se borran categorías; se
 * añaden emojis buscándolos por su nombre (la base de emojis de Tiresias); y
 * cada emoji tiene en su menú quitarlo, moverlo a otra categoría, ponerlo o
 * quitarlo de favoritos, y subirlo o bajarlo. Desde el teclado braille se
 * guardan con tres dedos a la derecha al explorar o buscar.
 */
class ActividadEmojisPropios : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.ep_titulo))
		construir(null)
	}

	private fun nombre(emoji: String) = BaseEmojis.nombre(this, emoji)

	/** Rehace la pantalla; el foco vuelve a lo que tenga la clave [enfocar] (lo que se acaba de tocar). */
	private fun construir(enfocar: String?) {
		p.vaciar()
		p.parrafo(getString(R.string.ep_explicacion))
		var destino: View? = null
		fun marcar(clave: String, v: View) {
			if (clave == enfocar) destino = v
		}

		// Favoritos
		val favoritos = ajustes.emojisFavoritos
		p.encabezado(getString(R.string.ep_favoritos_cabecera, favoritos.size))
		if (favoritos.isEmpty()) p.parrafo(getString(R.string.ep_sin_favoritos))
		for ((i, e) in favoritos.withIndex()) {
			val acciones = accionesFavorito(e, i, favoritos.size)
			marcar("fav:$e", p.botonSecundario(getString(R.string.ep_fila, nombre(e), getString(R.string.braille_favoritos))) { elegir(nombre(e), acciones) }.apply { accionesAccesibles(acciones) })
		}
		marcar("fav+", p.boton(getString(R.string.ep_anadir_favorito)) { buscarYAnadir(null) })

		// Categorías propias
		val categorias = EmojisPropios.categorias(ajustes)
		p.encabezado(getString(R.string.ep_categorias_cabecera))
		if (categorias.isEmpty()) p.parrafo(getString(R.string.ep_sin_categorias))
		for (c in categorias) {
			marcar("cat:${c.nombre}", p.encabezado(resources.getQuantityString(R.plurals.ep_categoria, c.emojis.size, c.nombre, c.emojis.size)))
			for ((i, e) in c.emojis.withIndex()) {
				val acciones = accionesEnCategoria(c.nombre, e, i, c.emojis.size)
				marcar("e:${c.nombre}:$e", p.botonSecundario(getString(R.string.ep_fila, nombre(e), c.nombre)) { elegir(nombre(e), acciones) }.apply { accionesAccesibles(acciones) })
			}
			marcar("add:${c.nombre}", p.boton(getString(R.string.ep_anadir_a, c.nombre)) { buscarYAnadir(c.nombre) })
			p.botonSecundario(getString(R.string.ep_renombrar, c.nombre)) { renombrar(c.nombre) }
			p.botonSecundario(getString(R.string.ep_borrar, c.nombre)) {
				confirmar(resources.getQuantityString(R.plurals.ep_borrar_pregunta, c.emojis.size, c.nombre, c.emojis.size), getString(R.string.ep_borrar, c.nombre)) {
					EmojisPropios.borrar(ajustes, c.nombre)
					avisar(getString(R.string.ep_borrada, c.nombre))
					construir("nueva")
				}
			}
		}
		p.separador()
		marcar("nueva", p.boton(getString(R.string.ep_nueva)) { crear() })
		destino?.let { v -> if (lectorActivo()) v.post { v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) } }
	}

	private fun accionesFavorito(e: String, i: Int, total: Int): List<Accion> = buildList {
		add(getString(R.string.ep_quitar_favorito) to {
			EmojisPropios.alternarFavorito(ajustes, e)
			avisar(getString(R.string.ep_quitado, nombre(e), getString(R.string.braille_favoritos)))
			construir("fav+")
		})
		add(getString(R.string.ep_guardar_en) to { elegirCategoria(nombre(e), null) { destino -> anadir(destino, e) } })
		if (i > 0) add(getString(R.string.ordenar_subir) to { EmojisPropios.desplazarFavorito(ajustes, e, -1); construir("fav:$e") })
		if (i < total - 1) add(getString(R.string.ordenar_bajar) to { EmojisPropios.desplazarFavorito(ajustes, e, 1); construir("fav:$e") })
	}

	private fun accionesEnCategoria(categoria: String, e: String, i: Int, total: Int): List<Accion> = buildList {
		add(getString(R.string.ep_quitar_de, categoria) to {
			EmojisPropios.quitar(ajustes, categoria, e)
			avisar(getString(R.string.ep_quitado, nombre(e), categoria))
			construir("add:$categoria")
		})
		add(getString(R.string.ep_mover) to {
			elegirCategoria(nombre(e), categoria) { destino ->
				EmojisPropios.mover(ajustes, categoria, destino, e)
				avisar(getString(R.string.ep_movido, nombre(e), destino))
				construir("e:$destino:$e")
			}
		})
		val esFavorito = e in ajustes.emojisFavoritos
		add(getString(if (esFavorito) R.string.ep_quitar_favorito else R.string.ep_poner_favorito) to {
			val puesto = EmojisPropios.alternarFavorito(ajustes, e)
			avisar(getString(if (puesto) R.string.ep_anadido else R.string.ep_quitado, nombre(e), getString(R.string.braille_favoritos)))
			construir("e:$categoria:$e")
		})
		if (i > 0) add(getString(R.string.ordenar_subir) to { EmojisPropios.desplazar(ajustes, categoria, e, -1); construir("e:$categoria:$e") })
		if (i < total - 1) add(getString(R.string.ordenar_bajar) to { EmojisPropios.desplazar(ajustes, categoria, e, 1); construir("e:$categoria:$e") })
	}

	/** Elige una categoría propia (sin [salvo]); sin ninguna, se dice cómo crearlas. */
	private fun elegirCategoria(titulo: String, salvo: String?, alElegir: (String) -> Unit) {
		val otras = EmojisPropios.categorias(ajustes).map { it.nombre }.filter { it != salvo }
		if (otras.isEmpty()) {
			avisar(getString(R.string.ep_sin_otras))
			return
		}
		elegir(getString(R.string.ep_elegir_categoria, titulo), otras.map { n -> n to { alElegir(n) } })
	}

	private fun anadir(categoria: String?, e: String) {
		if (categoria == null) {
			if (e !in ajustes.emojisFavoritos) EmojisPropios.alternarFavorito(ajustes, e)
			avisar(getString(R.string.ep_anadido, nombre(e), getString(R.string.braille_favoritos)))
			construir("fav:$e")
			return
		}
		if (e !in (EmojisPropios.categorias(ajustes).firstOrNull { it.nombre == categoria }?.emojis ?: emptyList())) EmojisPropios.alternar(ajustes, categoria, e)
		avisar(getString(R.string.ep_anadido, nombre(e), categoria))
		construir("e:$categoria:$e")
	}

	/** Busca un emoji por palabras de su nombre y lo añade a [categoria] (null: a favoritos). */
	private fun buscarYAnadir(categoria: String?) {
		pedirTexto(getString(R.string.ep_buscar_titulo), getString(R.string.ep_buscar_pista)) { consulta ->
			val hallados = BaseEmojis.buscador(this).buscar(consulta)
			if (hallados.isEmpty()) {
				avisar(getString(R.string.ep_sin_resultados, consulta))
				return@pedirTexto
			}
			elegir(resources.getQuantityString(R.plurals.ep_resultados, hallados.size, hallados.size), hallados.take(MAXIMO_RESULTADOS).map { h ->
				getString(R.string.ep_resultado, h.nombre, h.emoji) to { anadir(categoria, h.emoji) }
			})
		}
	}

	private fun crear() {
		pedirTexto(getString(R.string.ep_nueva), getString(R.string.ep_nombre_pista)) { nombre ->
			if (!EmojisPropios.crear(ajustes, nombre)) {
				avisar(getString(if (nombre.isBlank()) R.string.ep_nombre_vacio else R.string.ep_ya_existe))
				return@pedirTexto
			}
			avisar(getString(R.string.ep_creada, nombre.trim()))
			construir("add:${nombre.trim()}")
		}
	}

	private fun renombrar(antes: String) {
		pedirTexto(getString(R.string.ep_renombrar, antes), getString(R.string.ep_nombre_pista), inicial = antes) { nuevo ->
			if (nuevo.trim() == antes) return@pedirTexto
			if (!EmojisPropios.renombrar(ajustes, antes, nuevo)) {
				avisar(getString(if (nuevo.isBlank()) R.string.ep_nombre_vacio else R.string.ep_ya_existe))
				return@pedirTexto
			}
			avisar(getString(R.string.ep_renombrada, antes, nuevo.trim()))
			construir("cat:${nuevo.trim()}")
		}
	}

	private companion object {
		/** Los que se enseñan de una búsqueda (con «cara» salen cientos: mejor afinar con más palabras). */
		const val MAXIMO_RESULTADOS = 100
	}
}
