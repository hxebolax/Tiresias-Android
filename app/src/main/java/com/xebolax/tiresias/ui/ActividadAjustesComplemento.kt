package com.xebolax.tiresias.ui

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.accesibilidad.elegirVarios
import com.xebolax.tiresias.accesibilidad.pedirTexto
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.scripts.Scripts
import com.xebolax.tiresias.scripts.Scripts.AjusteComplemento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Los ajustes de un complemento, tal como los declara su manifiesto: cada
 * interruptor es una fila de una sola parada («Copiar el código, explicación,
 * interruptor, activado»), y cada lista, número, texto o grupo de apps es un
 * botón que dice el ajuste y su valor de ahora («Vaciar el portapapeles: a los
 * 60 segundos») y abre su diálogo. El complemento lee el valor al momento: no
 * hay que reiniciar nada.
 */
class ActividadAjustesComplemento : ActividadBase() {
	private lateinit var p: Pantalla
	private val scripts by lazy { Scripts(null, this) }
	private lateinit var info: Scripts.Complemento

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val id = intent.getStringExtra(EXTRA_ID).orEmpty()
		val encontrado = scripts.instalados().firstOrNull { it.id == id }
		if (encontrado == null) {
			finish()
			return
		}
		info = encontrado
		p = pantalla(getString(R.string.compaj_titulo, info.nombre))
		construir()
	}

	private fun construir() {
		p.vaciar()
		if (info.descripcion.isNotBlank()) p.parrafo(info.descripcion)
		for (a in info.ajustes) {
			when (a.tipo) {
				"si_no" -> interruptor(a)
				else -> p.botonSecundario(getString(R.string.compaj_fila, a.nombre, textoValor(a))) { cambiar(a) }
			}
			// La explicación de los que no son interruptores, debajo de su botón (el interruptor la lleva dentro)
			if (a.tipo != "si_no" && a.descripcion.isNotBlank()) p.parrafo(a.descripcion)
		}
		p.separador()
		p.botonSecundario(getString(R.string.compaj_restablecer)) {
			confirmar(getString(R.string.compaj_restablecer_pregunta, info.nombre), getString(R.string.compaj_restablecer)) {
				scripts.restablecerAjustes(info.id)
				avisar(getString(R.string.compaj_restablecidos))
				construir()
			}
		}
	}

	/** Un interruptor de una sola parada, con la explicación en su texto (más pequeña). */
	private fun interruptor(a: AjusteComplemento) {
		val texto = SpannableStringBuilder(a.nombre)
		if (a.descripcion.isNotBlank()) {
			texto.append("\n")
			val inicio = texto.length
			texto.append(a.descripcion)
			texto.setSpan(RelativeSizeSpan(0.85f), inicio, texto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		val vista = MaterialSwitch(this).apply {
			text = texto
			isChecked = scripts.valorAjuste(info.id, a) as Boolean
			minHeight = dp(this@ActividadAjustesComplemento, 56)
			setOnCheckedChangeListener { _, marcado -> scripts.ponerAjuste(info.id, a, marcado) }
		}
		p.contenedor.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
			topMargin = dp(this@ActividadAjustesComplemento, 8)
		})
	}

	/** El valor de ahora, como se dice: el nombre de la opción, el número con su unidad, el texto o cuántas apps. */
	private fun textoValor(a: AjusteComplemento): String {
		val v = scripts.valorAjuste(info.id, a)
		return when (a.tipo) {
			"lista" -> a.opciones.firstOrNull { it.first == v.toString() }?.second ?: v.toString()
			"numero" -> listOf(v.toString(), a.unidad).filter { it.isNotBlank() }.joinToString(" ")
			"apps" -> {
				val n = v.toString().split(',').count { it.isNotBlank() }
				if (n == 0) getString(R.string.compaj_ninguna_app) else resources.getQuantityString(R.plurals.compaj_apps, n, n)
			}
			else -> v.toString().ifBlank { getString(R.string.compaj_vacio) }
		}
	}

	private fun cambiar(a: AjusteComplemento) {
		val actual = scripts.valorAjuste(info.id, a)
		when (a.tipo) {
			"lista" -> elegirUno(a.nombre, a.opciones.map { it.second }, a.opciones.indexOfFirst { it.first == actual.toString() }) { i ->
				scripts.ponerAjuste(info.id, a, a.opciones[i].first)
				construir()
			}
			"numero" -> {
				val valores = (a.min..a.max step a.paso).toList()
				// Con pocos valores, una lista para elegir; con muchos, escribir el número
				if (valores.size <= 60) {
					val nombres = valores.map { listOf(it.toString(), a.unidad).filter { t -> t.isNotBlank() }.joinToString(" ") }
					elegirUno(a.nombre, nombres, valores.indexOf(actual as Int)) { i ->
						scripts.ponerAjuste(info.id, a, valores[i])
						construir()
					}
				} else pedirTexto(a.nombre, getString(R.string.compaj_numero_pista, a.min, a.max), actual.toString(), numerico = true) { t ->
					val n = t.toIntOrNull()
					if (n == null || n < a.min || n > a.max) avisar(getString(R.string.compaj_numero_mal, a.min, a.max))
					else {
						scripts.ponerAjuste(info.id, a, n)
						construir()
					}
				}
			}
			"texto" -> pedirTexto(a.nombre, a.descripcion.ifBlank { a.nombre }, actual.toString(), multilinea = true) { t ->
				scripts.ponerAjuste(info.id, a, t)
				construir()
			}
			"apps" -> elegirApps(a, actual.toString())
		}
	}

	/** Varias apps de las instaladas (las que tienen icono en el lanzador), por su nombre. */
	private fun elegirApps(a: AjusteComplemento, actual: String) {
		lifecycleScope.launch {
			val apps = try {
				withContext(Dispatchers.IO) {
					val pm = packageManager
					pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
						.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
						.distinctBy { it.first }
						.sortedBy { it.second.lowercase() }
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo leer la lista de apps", e)
				avisar(getString(R.string.compaj_apps_error))
				return@launch
			}
			val elegidas = actual.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
			// Las elegidas que ya no están instaladas se conservan (por si vuelven)
			val ausentes = elegidas - apps.map { it.first }.toSet()
			elegirVarios(a.nombre, apps.map { it.second }, apps.map { it.first in elegidas }.toBooleanArray()) { marcadas ->
				val nuevas = apps.filterIndexed { i, _ -> marcadas[i] }.map { it.first } + ausentes
				scripts.ponerAjuste(info.id, a, nuevas.joinToString(","))
				construir()
			}
		}
	}

	companion object {
		const val EXTRA_ID = "id"
		private const val ETIQUETA = "AjustesComplemento"
	}
}
