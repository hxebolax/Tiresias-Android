package com.xebolax.tiresias.ui

import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.addCallback
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.copiar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.accesibilidad.mensaje
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.lectura.EdicionPartes
import com.xebolax.tiresias.nucleo.Partes
import com.xebolax.tiresias.nucleo.Segmento
import com.xebolax.tiresias.nucleo.UnidadPartes

/**
 * Edición por partes, como el modo de edición granular de Jieshuo: el texto de
 * un campo (o de un mensaje, solo para leerlo y copiarlo) partido en una lista
 * de caracteres, palabras, frases, líneas, párrafos o entero. Cada parte es una
 * fila de una sola parada; al tocarla se edita, se borra, se inserta otra antes
 * o después, se marca o se copia. Debajo: buscar, reemplazar en todo el texto
 * (diciendo cuántas veces), deshacer y rehacer, copiar y guardar en el campo.
 *
 * El texto solo está en la memoria mientras la pantalla está abierta
 * ([EdicionPartes]): no se guarda en ningún archivo.
 */
class ActividadEdicionPartes : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var t: EdicionPartes.Trabajo
	private var partes: List<Segmento> = emptyList()
	private var filas: List<View> = emptyList()
	/** La última parte encontrada al buscar (se sigue desde ella). */
	private var encontrada = -1

	private val unidad: UnidadPartes get() = t.unidad ?: UnidadPartes.FRASES

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val trabajo = EdicionPartes.trabajo
		if (trabajo == null) {
			finish()
			return
		}
		t = trabajo
		if (t.unidad == null) t.unidad = runCatching { UnidadPartes.valueOf(Ajustes(this).unidadPartes) }.getOrDefault(UnidadPartes.FRASES)
		p = pantalla(getString(if (t.editable) R.string.partes_titulo else R.string.partes_titulo_leer))
		onBackPressedDispatcher.addCallback(this) { salir() }
		construir()
	}

	override fun onDestroy() {
		// El texto se olvida al cerrar (no al girar la pantalla)
		if (isFinishing && EdicionPartes.trabajo === t) EdicionPartes.trabajo = null
		super.onDestroy()
	}

	private fun nombreUnidad(u: UnidadPartes): String = getString(when (u) {
		UnidadPartes.CARACTERES -> R.string.partes_unidad_caracteres
		UnidadPartes.PALABRAS -> R.string.partes_unidad_palabras
		UnidadPartes.FRASES -> R.string.partes_unidad_frases
		UnidadPartes.LINEAS -> R.string.partes_unidad_lineas
		UnidadPartes.PARRAFOS -> R.string.partes_unidad_parrafos
		UnidadPartes.TODO -> R.string.partes_unidad_todo
	})

	/** Rehace la pantalla y, si se da [enfocar], pone ahí el foco del lector (la fila que se acaba de cambiar). */
	private fun construir(enfocar: Int? = null) {
		p.vaciar()
		partes = Partes.partir(t.texto, unidad)
		val n = partes.size
		val c = t.texto.length
		p.encabezado(getString(R.string.partes_resumen, nombreUnidad(unidad), resources.getQuantityString(R.plurals.partes_partes, n, n), resources.getQuantityString(R.plurals.partes_caracteres, c, c)))
		p.botonSecundario(getString(R.string.partes_cambiar_unidad, nombreUnidad(unidad))) { elegirUnidad() }
		if (partes.isEmpty()) {
			p.parrafo(getString(R.string.partes_vacio))
			if (t.editable) p.boton(getString(R.string.partes_anadir)) {
				pedirTexto(getString(R.string.partes_anadir), getString(R.string.partes_texto_pista), multilinea = !t.unaLinea) { nuevo ->
					if (nuevo.isNotEmpty()) cambiar(nuevo, 0)
				}
			}
		}
		if (n > MAXIMO_FILAS) p.parrafo(getString(R.string.partes_demasiadas, MAXIMO_FILAS))
		filas = partes.take(MAXIMO_FILAS).mapIndexed { i, s -> p.botonSecundario(textoFila(i, s)) { opciones(i) } }
		p.separador()
		if (t.marcadas.isNotEmpty()) {
			val m = t.marcadas.size
			p.parrafo(resources.getQuantityString(R.plurals.partes_marcadas, m, m))
			p.botonSecundario(getString(R.string.partes_copiar_marcadas)) { copiarMarcadas(cortar = false) }
			if (t.editable) {
				p.botonSecundario(getString(R.string.partes_cortar_marcadas)) { copiarMarcadas(cortar = true) }
				p.botonSecundario(getString(R.string.partes_borrar_marcadas)) { borrarMarcadas() }
			}
			p.botonSecundario(getString(R.string.partes_desmarcar_todas)) {
				t.marcadas.clear()
				construir()
			}
		}
		if (partes.isNotEmpty()) p.botonSecundario(getString(R.string.partes_buscar)) { buscar() }
		if (t.editable && partes.isNotEmpty()) p.botonSecundario(getString(R.string.partes_reemplazar)) { reemplazar() }
		if (t.deshacer.isNotEmpty()) p.botonSecundario(getString(R.string.partes_deshacer)) { deshacer() }
		if (t.rehacer.isNotEmpty()) p.botonSecundario(getString(R.string.partes_rehacer)) { rehacer() }
		if (partes.isNotEmpty()) p.botonSecundario(getString(R.string.partes_copiar_todo)) { copiar(t.texto) }
		if (t.editable) p.boton(getString(R.string.partes_guardar)) { guardar() }
		p.botonSecundario(getString(R.string.partes_salir)) { salir() }
		enfocar?.let { k -> filas.getOrNull(k.coerceAtMost(filas.lastIndex))?.let { v -> v.post { v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) } } }
	}

	/** Lo que dice cada fila: si está marcada (lo primero), el número de línea en las líneas y la parte. */
	private fun textoFila(i: Int, s: Segmento): String {
		val parte = decible(s.texto)
		val base = if (unidad == UnidadPartes.LINEAS) getString(R.string.partes_fila_linea, i + 1, parte) else parte
		return if (i in t.marcadas) getString(R.string.partes_fila_marcada, base) else base
	}

	/** Una parte que no se oiría: un espacio o un salto por su nombre; una línea vacía, «vacía». */
	private fun decible(s: String): String = when {
		s.isEmpty() -> getString(R.string.partes_vacia)
		s == " " -> getString(R.string.partes_espacio)
		s == "\n" -> getString(R.string.partes_salto)
		s == "\t" -> getString(R.string.partes_tabulador)
		s.isBlank() -> getString(R.string.partes_vacia)
		else -> s
	}

	private fun elegirUnidad() {
		val todas = UnidadPartes.entries
		elegirUno(getString(R.string.partes_elegir_unidad), todas.map { nombreUnidad(it) }, todas.indexOf(unidad)) { i ->
			t.unidad = todas[i]
			t.marcadas.clear()
			encontrada = -1
			Ajustes(this).unidadPartes = todas[i].name
			construir(0)
		}
	}

	/** Lo que se puede hacer con la parte [i] (según se pueda editar y esté marcada o no). */
	private fun opciones(i: Int) {
		val s = partes.getOrNull(i) ?: return
		val lista = mutableListOf<Pair<String, () -> Unit>>()
		if (t.editable) {
			lista += getString(R.string.partes_editar) to { editar(i) }
			lista += getString(R.string.partes_insertar_antes) to { insertar(i, despues = false) }
			lista += getString(R.string.partes_insertar_despues) to { insertar(i, despues = true) }
			lista += getString(R.string.partes_borrar) to { cambiar(Partes.borrar(t.texto, s, unidad), i, getString(R.string.partes_borrada)) }
		}
		if (i in t.marcadas) lista += getString(R.string.partes_desmarcar) to { marcar(listOf(i), false) }
		else {
			lista += getString(R.string.partes_marcar) to { marcar(listOf(i), true) }
			// Un rango: de la marcada más cercana hasta esta (como mantener en Jieshuo)
			val desde = t.marcadas.filter { it != i }.minByOrNull { Math.abs(it - i) }
			if (desde != null) lista += getString(R.string.partes_marcar_hasta_aqui) to { marcar((minOf(desde, i)..maxOf(desde, i)).toList(), true) }
		}
		lista += getString(R.string.partes_copiar) to { copiar(s.texto) }
		elegir(decible(s.texto).take(80), lista)
	}

	private fun marcar(indices: List<Int>, si: Boolean) {
		if (si) t.marcadas.addAll(indices) else t.marcadas.removeAll(indices.toSet())
		construir(indices.last())
		val m = t.marcadas.size
		avisar(resources.getQuantityString(R.plurals.partes_marcadas, m, m))
	}

	private fun editar(i: Int) {
		val s = partes.getOrNull(i) ?: return
		val multilinea = !t.unaLinea && (unidad == UnidadPartes.TODO || unidad == UnidadPartes.PARRAFOS)
		pedirTexto(getString(R.string.partes_editar_titulo, i + 1), getString(R.string.partes_texto_pista), s.texto, multilinea = multilinea, alCancelar = { construir(i) }) { nuevo ->
			if (nuevo == s.texto) construir(i)
			else if (nuevo.isEmpty()) cambiar(Partes.borrar(t.texto, s, unidad), i, getString(R.string.partes_borrada))
			else cambiar(Partes.cambiar(t.texto, s, nuevo), i)
		}
	}

	private fun insertar(i: Int, despues: Boolean) {
		val s = partes.getOrNull(i) ?: return
		pedirTexto(getString(if (despues) R.string.partes_insertar_despues else R.string.partes_insertar_antes), getString(R.string.partes_texto_pista), multilinea = !t.unaLinea && unidad == UnidadPartes.PARRAFOS) { nuevo ->
			if (nuevo.isNotEmpty()) cambiar(Partes.insertar(t.texto, s, nuevo, despues, unidad), if (despues) i + 1 else i)
		}
	}

	private fun copiarMarcadas(cortar: Boolean) {
		val elegidas = t.marcadas.sorted().mapNotNull { partes.getOrNull(it) }
		if (elegidas.isEmpty()) return
		copiar(Partes.juntar(elegidas, unidad))
		if (cortar) cambiar(Partes.borrarVarias(t.texto, elegidas, unidad), t.marcadas.min(), getString(R.string.partes_cortadas))
	}

	private fun borrarMarcadas() {
		val elegidas = t.marcadas.sorted().mapNotNull { partes.getOrNull(it) }
		if (elegidas.isEmpty()) return
		val n = elegidas.size
		confirmar(resources.getQuantityString(R.plurals.partes_borrar_marcadas_pregunta, n, n), getString(R.string.partes_borrar_marcadas)) {
			cambiar(Partes.borrarVarias(t.texto, elegidas, unidad), t.marcadas.min(), getString(R.string.partes_borradas))
		}
	}

	/** Cambia el texto (se puede deshacer) y rehace la lista con el foco en la parte [enfocar]. */
	private fun cambiar(nuevo: String, enfocar: Int, aviso: String? = null) {
		if (nuevo == t.texto) return
		t.deshacer.addLast(t.texto)
		while (t.deshacer.size > MAXIMO_DESHACER) t.deshacer.removeFirst()
		t.rehacer.clear()
		t.texto = nuevo
		t.marcadas.clear()
		construir(enfocar)
		aviso?.let { avisar(it) }
	}

	private fun deshacer() {
		val anterior = t.deshacer.removeLastOrNull() ?: return
		t.rehacer.addLast(t.texto)
		t.texto = anterior
		t.marcadas.clear()
		construir(0)
		avisar(getString(R.string.partes_deshecho))
	}

	private fun rehacer() {
		val siguiente = t.rehacer.removeLastOrNull() ?: return
		t.deshacer.addLast(t.texto)
		t.texto = siguiente
		t.marcadas.clear()
		construir(0)
		avisar(getString(R.string.partes_rehecho))
	}

	/** Busca (sin distinguir mayúsculas) desde la última encontrada, y da la vuelta al llegar al final. */
	private fun buscar() {
		pedirTexto(getString(R.string.partes_buscar), getString(R.string.partes_buscar_pista)) { buscado ->
			if (buscado.isEmpty()) return@pedirTexto
			val orden = (encontrada + 1 until partes.size) + (0..encontrada.coerceAtMost(partes.lastIndex))
			val i = orden.firstOrNull { partes[it].texto.contains(buscado, ignoreCase = true) }
			if (i == null || i >= MAXIMO_FILAS) {
				avisar(getString(R.string.partes_no_esta, buscado))
				return@pedirTexto
			}
			encontrada = i
			construir(i)
		}
	}

	private fun reemplazar() {
		pedirTexto(getString(R.string.partes_reemplazar), getString(R.string.partes_reemplazar_buscar)) { buscado ->
			if (buscado.isEmpty()) return@pedirTexto
			pedirTexto(getString(R.string.partes_reemplazar_por_titulo, buscado), getString(R.string.partes_reemplazar_por)) { nuevo ->
				elegir(getString(R.string.partes_reemplazar), listOf(
					getString(R.string.partes_sin_distinguir) to { hacerReemplazo(buscado, nuevo, false) },
					getString(R.string.partes_distinguiendo) to { hacerReemplazo(buscado, nuevo, true) },
				))
			}
		}
	}

	private fun hacerReemplazo(buscado: String, nuevo: String, distinguir: Boolean) {
		val (texto, veces) = Partes.reemplazar(t.texto, buscado, nuevo, distinguir)
		if (veces == 0) {
			avisar(getString(R.string.partes_no_esta, buscado))
			return
		}
		cambiar(texto, 0, resources.getQuantityString(R.plurals.partes_reemplazadas, veces, veces))
	}

	/** Devuelve el texto al campo. Si la app lo ha cambiado mientras tanto, se pregunta antes de taparlo. */
	private fun guardar() {
		val final = if (t.unaLinea) t.texto.replace('\n', ' ') else t.texto
		if (t.maximo in 1 until final.length) {
			mensaje(getString(R.string.partes_titulo), getString(R.string.partes_demasiado_largo, final.length, t.maximo))
			return
		}
		val n = t.nodo
		val ahora = n?.let { nodo ->
			runCatching { nodo.refresh() }
			if (android.os.Build.VERSION.SDK_INT >= 26 && nodo.isShowingHintText) "" else nodo.text?.toString()
		}
		if (n == null || ahora == null) {
			copiar(final)
			mensaje(getString(R.string.partes_titulo), getString(R.string.partes_campo_perdido)) { terminar() }
			return
		}
		if (ahora != t.original) {
			confirmar(getString(R.string.partes_campo_cambiado), getString(R.string.partes_sustituir)) { t.guardar(final); terminar() }
			return
		}
		t.guardar(final)
		terminar()
	}

	/** Salir: con cambios sin guardar, se pregunta qué hacer con ellos (Atrás también). */
	private fun salir() {
		if (!t.editable || t.texto == t.original) {
			terminar()
			return
		}
		elegir(getString(R.string.partes_sin_guardar), listOf(
			getString(R.string.partes_guardar) to { guardar() },
			getString(R.string.partes_copiar_y_salir) to {
				copiar(t.texto)
				terminar()
			},
			getString(R.string.partes_salir_sin_guardar) to { terminar() },
			getString(R.string.partes_seguir) to { },
		))
	}

	private fun terminar() {
		EdicionPartes.trabajo = null
		finish()
	}

	private companion object {
		/** Las filas que se ponen como mucho (los caracteres de un texto largo serían miles). */
		const val MAXIMO_FILAS = 1500
		const val MAXIMO_DESHACER = 50
	}
}
