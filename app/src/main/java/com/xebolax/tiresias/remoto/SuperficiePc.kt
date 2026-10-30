package com.xebolax.tiresias.remoto

import com.xebolax.tiresias.R
import com.xebolax.tiresias.braille.DireccionBraille
import com.xebolax.tiresias.braille.GestoBraille
import com.xebolax.tiresias.braille.ModoSuperficie
import com.xebolax.tiresias.braille.TecladoBraille
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.remoto.TeclaVk

/**
 * La superficie braille para manejar un PC con NVDA (Can Talk), a pantalla
 * completa encima de todo. Dos modos, que se cambian con tres dedos hacia
 * arriba:
 *
 * - **Control**: las letras braille son teclas (a es la A); con el signo de
 *   número, las cifras. Los puntos sueltos preparan un modificador para la
 *   tecla siguiente: el 6, Control; el 5, Alt; el 3, Mayúsculas; el 2, la tecla
 *   NVDA. Un dedo, las flechas; dos dedos hacia abajo, Intro; hacia arriba,
 *   Escape; a la izquierda, Retroceso; a la derecha, Tabulador; tres dedos a la
 *   izquierda, Alt+Tab; a la derecha, Espacio.
 * - **Teclado braille**: los puntos van a NVDA, que los escribe con su tabla
 *   braille de entrada; un dedo a la izquierda borra, a la derecha es un
 *   espacio; dos dedos hacia abajo, Intro.
 *
 * Tres dedos hacia abajo abren el menú (más teclas, Control+Alt+Supr, pegar
 * texto, callar la voz del PC, desconectar o quitar la superficie). Las dos
 * teclas de volumen la quitan siempre. No se quita al cambiar de app: el PC
 * sigue conectado.
 */
class SuperficiePc(lector: Lector, private val cliente: ClienteNvda, private val alQuitar: () -> Unit) : ModoSuperficie(lector) {
	private var teclado = false
	private val modificadores = LinkedHashSet<TeclaVk>()
	private var numero = false
	/** El menú abierto: la opción en la que se está, o -1 sin menú. */
	private var enMenu = -1

	override val cerrarAlCambiarDeApp = false
	override val rotulo = "⠿ NVDA"

	override fun aviso(): String = t.getString(R.string.nvda_superficie_aviso)

	override fun alCerrar() = alQuitar()

	override fun alPuntos(puntos: Int) {
		if (enMenu >= 0) return
		if (teclado) {
			cliente.puntos(puntos)
			return
		}
		when (puntos) {
			P6 -> return modificador(TeclaVk.CONTROL, R.string.nvda_control)
			P5 -> return modificador(TeclaVk.ALT, R.string.nvda_alt)
			P3 -> return modificador(TeclaVk.MAYUSCULAS, R.string.nvda_mayusculas)
			P2 -> return modificador(TeclaVk.NVDA, R.string.nvda_tecla_nvda)
			TecladoBraille.SIGNO_DE_NUMERO -> {
				numero = true
				decir(t.getString(R.string.braille_numero))
				return
			}
		}
		val c = (if (numero) TecladoBraille.numero(puntos) else null) ?: TecladoBraille.letra(puntos)
		numero = false
		val tecla = c?.firstOrNull()?.let { TeclaVk.deLetra(it) }
		if (tecla == null) {
			decir(t.getString(R.string.nvda_sin_tecla))
			return
		}
		pulsar(tecla)
	}

	private fun modificador(t: TeclaVk, nombre: Int) {
		if (!modificadores.add(t)) modificadores.remove(t)
		decir(this.t.getString(nombre))
	}

	/** Pulsa [tecla] con los modificadores preparados y los gasta. */
	private fun pulsar(tecla: TeclaVk) {
		cliente.teclas(modificadores.toList() + tecla)
		modificadores.clear()
	}

	override fun alGesto(g: GestoBraille) {
		if (enMenu >= 0) return gestoMenu(g)
		when (g.dedos to g.direccion) {
			3 to DireccionBraille.ARRIBA -> {
				teclado = !teclado
				modificadores.clear()
				decir(t.getString(if (teclado) R.string.nvda_modo_teclado else R.string.nvda_modo_control))
			}
			3 to DireccionBraille.ABAJO -> {
				enMenu = 0
				decir(t.getString(R.string.nvda_menu) + ". " + opciones()[0].first)
			}
			else -> if (teclado) gestoTeclado(g) else gestoControl(g)
		}
	}

	private fun gestoControl(g: GestoBraille) {
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.IZQUIERDA -> pulsar(TeclaVk.IZQUIERDA)
			1 to DireccionBraille.DERECHA -> pulsar(TeclaVk.DERECHA)
			1 to DireccionBraille.ARRIBA -> pulsar(TeclaVk.ARRIBA)
			1 to DireccionBraille.ABAJO -> pulsar(TeclaVk.ABAJO)
			2 to DireccionBraille.ABAJO -> pulsar(TeclaVk.INTRO)
			2 to DireccionBraille.ARRIBA -> pulsar(TeclaVk.ESCAPE)
			2 to DireccionBraille.IZQUIERDA -> pulsar(TeclaVk.RETROCESO)
			2 to DireccionBraille.DERECHA -> pulsar(TeclaVk.TABULADOR)
			3 to DireccionBraille.IZQUIERDA -> cliente.teclas(listOf(TeclaVk.ALT, TeclaVk.TABULADOR))
			3 to DireccionBraille.DERECHA -> pulsar(TeclaVk.ESPACIO)
			else -> Unit
		}
	}

	private fun gestoTeclado(g: GestoBraille) {
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.IZQUIERDA -> cliente.ordenBraille("braille_eraseLastCell")
			1 to DireccionBraille.DERECHA -> cliente.puntos(0, espacio = true)
			2 to DireccionBraille.ABAJO -> cliente.ordenBraille("braille_enter")
			2 to DireccionBraille.ARRIBA -> pulsar(TeclaVk.ESCAPE)
			2 to DireccionBraille.IZQUIERDA -> pulsar(TeclaVk.RETROCESO)
			2 to DireccionBraille.DERECHA -> pulsar(TeclaVk.TABULADOR)
			else -> Unit
		}
	}

	/** Las opciones del menú: lo que se dice y lo que hace. */
	private fun opciones(): List<Pair<String, () -> Unit>> = listOf(
		t.getString(R.string.nvda_menu_windows) to { pulsar(TeclaVk.WINDOWS) },
		t.getString(R.string.nvda_menu_aplicaciones) to { pulsar(TeclaVk.APLICACIONES) },
		t.getString(R.string.nvda_menu_inicio) to { pulsar(TeclaVk.INICIO) },
		t.getString(R.string.nvda_menu_fin) to { pulsar(TeclaVk.FIN) },
		t.getString(R.string.nvda_menu_repag) to { pulsar(TeclaVk.REPAG) },
		t.getString(R.string.nvda_menu_avpag) to { pulsar(TeclaVk.AVPAG) },
		t.getString(R.string.nvda_menu_suprimir) to { pulsar(TeclaVk.SUPRIMIR) },
		t.getString(R.string.nvda_menu_f4) to { cliente.teclas(listOf(TeclaVk.ALT, TeclaVk.F4)) },
		t.getString(R.string.nvda_menu_escritorio) to { cliente.teclas(listOf(TeclaVk.WINDOWS, TeclaVk.D)) },
		t.getString(R.string.nvda_menu_sas) to { cliente.ctrlAltSupr() },
		t.getString(R.string.nvda_menu_pegar) to { pegarPortapapeles() },
		t.getString(if (cliente.silenciado) R.string.nvda_menu_voz_si else R.string.nvda_menu_voz_no) to {
			cliente.silenciado = !cliente.silenciado
			decir(t.getString(if (cliente.silenciado) R.string.nvda_voz_callada else R.string.nvda_voz_oida))
		},
		t.getString(R.string.nvda_menu_desconectar) to {
			cliente.terminar()
			cerrar()
		},
		t.getString(R.string.nvda_menu_quitar) to { cerrar() },
	)

	private fun gestoMenu(g: GestoBraille) {
		val o = opciones()
		when (g.dedos to g.direccion) {
			1 to DireccionBraille.DERECHA, 1 to DireccionBraille.ABAJO -> {
				enMenu = (enMenu + 1) % o.size
				decir(o[enMenu].first)
			}
			1 to DireccionBraille.IZQUIERDA, 1 to DireccionBraille.ARRIBA -> {
				enMenu = (enMenu - 1 + o.size) % o.size
				decir(o[enMenu].first)
			}
			2 to DireccionBraille.ABAJO -> {
				val elegida = o[enMenu]
				enMenu = -1
				elegida.second()
			}
			2 to DireccionBraille.ARRIBA, 3 to DireccionBraille.ABAJO -> {
				enMenu = -1
				decir(t.getString(if (teclado) R.string.nvda_modo_teclado else R.string.nvda_modo_control))
			}
			else -> Unit
		}
	}

	/** Lo copiado en el móvil va al PC y se pega allí. */
	private fun pegarPortapapeles() {
		val cm = lector.servicio.getSystemService(android.content.ClipboardManager::class.java)
		val texto = runCatching { cm?.primaryClip?.getItemAt(0)?.coerceToText(lector.servicio)?.toString() }.getOrNull()
		if (texto.isNullOrEmpty()) decir(t.getString(R.string.nvda_portapapeles_vacio)) else cliente.pegarTexto(texto)
	}

	private companion object {
		const val P2 = 1 shl 1
		const val P3 = 1 shl 2
		const val P5 = 1 shl 4
		const val P6 = 1 shl 5
	}
}
