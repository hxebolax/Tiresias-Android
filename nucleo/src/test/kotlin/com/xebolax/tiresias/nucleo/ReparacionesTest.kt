package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La reparación automática: lo que propone en las pantallas mal hechas más comunes, y lo que no toca. */
class ReparacionesTest {
	private fun texto(id: String?, huella: Int = id.hashCode(), parada: Boolean = true) =
		NodoReparar("android.widget.TextView", id, parada = parada, accionable = false, conTexto = true, huella = huella)

	private fun fila(id: String, vararg hijos: NodoReparar, pulsable: Boolean = false, parada: Boolean = false) =
		NodoReparar("android.widget.LinearLayout", id, parada = parada, accionable = pulsable, conTexto = false, hijos = hijos.toList())

	private fun lista(vararg filas: NodoReparar) =
		NodoReparar("androidx.recyclerview.widget.RecyclerView", "lista", parada = false, accionable = false, conTexto = false, lista = true, hijos = filas.toList())

	@Test
	fun lasFilasLeidasATrozosSeAgrupan() {
		val l = lista(*Array(5) { i -> fila("fila", texto("titulo", i), texto("fecha", 100 + i)) })
		assertEquals(listOf(Reparacion(TipoReparacion.AGRUPAR_FILA, "fila", 1)), Reparaciones.analizar(l))
	}

	@Test
	fun dentroDeUnScrollViewConUnLinearLayout() {
		val dentro = NodoReparar("android.widget.LinearLayout", null, parada = false, accionable = false, conTexto = false,
			hijos = List(4) { i -> fila("fila", texto("titulo", i), texto("fecha", 100 + i)) })
		val scroll = NodoReparar("android.widget.ScrollView", null, parada = false, accionable = false, conTexto = false, lista = true, hijos = listOf(dentro))
		assertEquals(listOf(Reparacion(TipoReparacion.AGRUPAR_FILA, "fila", 1)), Reparaciones.analizar(scroll))
		// Y si cabe entera en la pantalla, el ScrollView no se desplaza: da igual
		val quieto = NodoReparar("android.widget.ScrollView", null, parada = false, accionable = false, conTexto = false, lista = false, hijos = listOf(dentro))
		assertEquals(listOf(Reparacion(TipoReparacion.AGRUPAR_FILA, "fila", 1)), Reparaciones.analizar(quieto))
	}

	@Test
	fun lasFilasQueAndroidQuitaDelArbolSeUnen() {
		// Sin la fila (no se pulsa ni dice nada propio: Android la quita), los textos quedan seguidos: título, fecha, título...
		val opciones = listOf(texto("seccion", 1), NodoReparar("TextView", "opcion", parada = true, accionable = true, conTexto = true, huella = 2),
			NodoReparar("TextView", "opcion", parada = true, accionable = true, conTexto = true, huella = 3))
		val trozos = (0 until 4).flatMap { i -> listOf(texto("titulo", 10 + i), texto("fecha", 20 + i)) }
		val raiz = NodoReparar("android.widget.ScrollView", null, parada = false, accionable = false, conTexto = false, hijos = opciones + texto("seccion", 4) + opciones.drop(1) + trozos)
		val r = Reparaciones.analizar(raiz)
		assertTrue(r.contains(Reparacion(TipoReparacion.UNIR_CON_ANTERIOR, "fecha", 1)))
		assertTrue(r.contains(Reparacion(TipoReparacion.ENCABEZADO, "seccion", 1)))
		// El título no es un encabezado ni se une a nada (va detrás de la fecha, pero también detrás de una opción)
		assertTrue(r.none { it.id == "titulo" })
	}

	@Test
	fun lasFilasBienHechasNoSeTocan() {
		// Cada fila es ya una parada que se pulsa (lee lo de dentro): nada que hacer
		val l = lista(*Array(5) { i -> fila("fila", texto("titulo", i, parada = false), pulsable = true, parada = true) })
		assertTrue(Reparaciones.analizar(l).isEmpty())
		// Con un botón dentro de cada fila, no se agrupa (el botón dejaría de ser suyo)
		val boton = NodoReparar("android.widget.ImageButton", "borrar", parada = true, accionable = true, conTexto = true, huella = 7)
		val conBoton = lista(*Array(5) { i -> fila("fila", texto("titulo", i), boton) })
		assertTrue(Reparaciones.analizar(conBoton).none { it.tipo == TipoReparacion.AGRUPAR_FILA })
		// Dos filas no bastan
		assertTrue(Reparaciones.analizar(lista(fila("fila", texto("a", 1), texto("b", 2)), fila("fila", texto("a", 3), texto("b", 4)))).isEmpty())
	}

	@Test
	fun losAdornosSinNadaQueDecirSeQuitan() {
		val adorno = NodoReparar("android.widget.ImageView", "separador", parada = true, accionable = false, conTexto = false)
		val raiz = NodoReparar("android.widget.FrameLayout", null, parada = false, accionable = false, conTexto = false, hijos = listOf(adorno, texto("titulo")))
		assertEquals(listOf(Reparacion(TipoReparacion.OCULTAR_VACIO, "separador", 1)), Reparaciones.analizar(raiz))
		// Un botón sin nombre no se quita: hay que oírlo (y etiquetarlo)
		val boton = NodoReparar("android.widget.ImageButton", "x", parada = true, accionable = true, conTexto = false)
		assertTrue(Reparaciones.analizar(NodoReparar("FrameLayout", null, false, false, false, hijos = listOf(boton))).isEmpty())
		// Ni una barra de progreso
		val progreso = NodoReparar("android.widget.ProgressBar", "cargando", parada = true, accionable = false, conTexto = false)
		assertTrue(Reparaciones.analizar(NodoReparar("FrameLayout", null, false, false, false, hijos = listOf(progreso))).isEmpty())
	}

	@Test
	fun loRepetidoSobra() {
		// La foto «Ana» (no se pulsa) y el nombre «Ana» seguidos: sobra la foto... o el texto, el que no se pulsa
		val foto = NodoReparar("android.widget.ImageView", "foto", parada = true, accionable = false, conTexto = true, huella = 42)
		val nombre = NodoReparar("android.widget.TextView", "nombre", parada = true, accionable = true, conTexto = true, huella = 42)
		val raiz = NodoReparar("LinearLayout", null, false, false, false, hijos = listOf(foto, nombre))
		assertEquals(listOf(Reparacion(TipoReparacion.OCULTAR_REPETIDO, "foto", 1)), Reparaciones.analizar(raiz))
	}

	@Test
	fun losTitulosDeSeccionSonEncabezados() {
		val l = lista(
			texto("seccion", 1), fila("opcion", texto(null, 2, parada = false), pulsable = true, parada = true), fila("opcion", texto(null, 3, parada = false), pulsable = true, parada = true),
			texto("seccion", 4), fila("opcion", texto(null, 5, parada = false), pulsable = true, parada = true), fila("opcion", texto(null, 6, parada = false), pulsable = true, parada = true),
		)
		assertEquals(listOf(Reparacion(TipoReparacion.ENCABEZADO, "seccion", 1)), Reparaciones.analizar(l))
	}
}
