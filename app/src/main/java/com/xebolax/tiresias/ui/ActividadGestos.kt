package com.xebolax.tiresias.ui

import android.content.Context
import android.os.Bundle
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.MapaGestos
import com.xebolax.tiresias.perfiles.Perfiles
import com.xebolax.tiresias.servicio.PuenteEscritorio
import com.xebolax.tiresias.servicio.ServicioTiresias

/**
 * Gestos de Tiresias: cada gesto con lo que hace, y elegir otra acción para
 * cualquiera. Los gestos de emergencia no se pueden cambiar (se dice en su fila).
 * Cada fila es una sola parada: «Deslizar a la derecha: siguiente elemento».
 *
 * Con [EXTRA_PAQUETE], los gestos de esa app (su perfil por app): lo que se
 * cambia solo vale en ella y lo demás sigue al mapa general. Así, en TikTok,
 * deslizar hacia arriba puede pasar al vídeo siguiente.
 */
class ActividadGestos : ActividadBase() {
	private lateinit var p: Pantalla
	private val perfiles by lazy { Perfiles(this) }
	/** La app cuyos gestos se cambian, o null para los de todas. */
	private var paquete: String? = null

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		protegerConCodigo()
		paquete = intent.getStringExtra(EXTRA_PAQUETE)?.takeIf { it.isNotBlank() }
		p = pantalla(paquete?.let { getString(R.string.gestos_de_app_titulo, nombreApp(this, it)) } ?: getString(R.string.ui_gestos))
		construir()
	}

	private fun construir() {
		paquete?.let {
			construirDeApp(it)
			return
		}
		p.vaciar()
		val ajustes = Ajustes(this)
		val mapa = ajustes.mapaGestos
		p.parrafo(getString(R.string.gestos_explicacion))
		var dedos = -1
		for (g in Gesto.entries) {
			if (g.dedos != dedos) {
				dedos = g.dedos
				p.encabezado(getString(when (dedos) {
					1 -> R.string.gestos_un_dedo
					2 -> R.string.gestos_dos_dedos
					3 -> R.string.gestos_tres_dedos
					4 -> R.string.gestos_cuatro_dedos
					else -> R.string.gestos_otros
				}))
			}
			val texto = getString(R.string.gestos_fila, nombreGesto(this, g), nombreAccion(this, mapa[g]))
			if (g in MapaGestos.EMERGENCIA) {
				p.parrafo(texto + ". " + getString(R.string.gestos_emergencia))
				continue
			}
			p.botonSecundario(texto) { elegir(g) }
		}
		p.separador()
		p.botonSecundario(getString(R.string.gestos_de_una_app)) { elegirApp() }
		p.botonSecundario(getString(R.string.gestos_sencillos)) {
			confirmar(getString(R.string.gestos_sencillos_pregunta), getString(R.string.gestos_sencillos)) {
				ajustes.mapaGestos = MapaGestos.SENCILLO
				ServicioTiresias.instancia?.recargarAjustes()
				avisar(getString(R.string.gestos_sencillos_puestos))
				construir()
			}
		}
		p.boton(getString(R.string.gestos_restablecer)) {
			confirmar(getString(R.string.gestos_restablecer_pregunta), getString(R.string.gestos_restablecer)) {
				ajustes.mapaGestos = MapaGestos.POR_DEFECTO
				ServicioTiresias.instancia?.recargarAjustes()
				avisar(getString(R.string.gestos_restablecidos))
				construir()
			}
		}
	}

	private fun elegir(g: Gesto) {
		val ajustes = Ajustes(this)
		val acciones = Accion.entries.filter { it != Accion.MODO_SEGURO && it !in PuenteEscritorio.ACCIONES_OCULTAS }
		val actual = acciones.indexOf(ajustes.mapaGestos[g]).coerceAtLeast(0)
		elegirUno(getString(R.string.gestos_elegir, nombreGesto(this, g)), acciones.map { nombreAccion(this, it) }, actual) { i ->
			ajustes.mapaGestos = ajustes.mapaGestos.mas(mapOf(g to acciones[i]))
			ServicioTiresias.instancia?.recargarAjustes()
			construir()
		}
	}

	/** Los gestos de [app]: cada fila dice lo que hace allí y si es propio de esa app. */
	private fun construirDeApp(app: String) {
		p.vaciar()
		val nombre = nombreApp(this, app)
		val general = Ajustes(this).mapaGestos
		val propios = perfiles.deApp(app)?.gestos.orEmpty()
		p.parrafo(getString(R.string.gestos_de_app_explicacion, nombre))
		var dedos = -1
		for (g in Gesto.entries) {
			if (g.dedos != dedos) {
				dedos = g.dedos
				p.encabezado(getString(when (dedos) {
					1 -> R.string.gestos_un_dedo
					2 -> R.string.gestos_dos_dedos
					3 -> R.string.gestos_tres_dedos
					4 -> R.string.gestos_cuatro_dedos
					else -> R.string.gestos_otros
				}))
			}
			if (g in MapaGestos.EMERGENCIA) {
				p.parrafo(getString(R.string.gestos_fila, nombreGesto(this, g), nombreAccion(this, general[g])) + ". " + getString(R.string.gestos_emergencia))
				continue
			}
			val propia = propios[g]
			val texto = if (propia != null) getString(R.string.gestos_fila_de_app, nombreGesto(this, g), nombreAccion(this, propia))
				else getString(R.string.gestos_fila, nombreGesto(this, g), nombreAccion(this, general[g]))
			p.botonSecundario(texto) { elegirDeApp(app, g) }
		}
		// Solo si hay algo que quitar
		if (propios.isNotEmpty()) {
			p.separador()
			p.boton(getString(R.string.gestos_de_app_quitar, nombre)) {
				confirmar(getString(R.string.gestos_de_app_quitar_pregunta, nombre), getString(R.string.gestos_de_app_quitar, nombre)) {
					guardarDeApp(app, emptyMap())
					avisar(getString(R.string.gestos_de_app_quitados, nombre))
					construir()
				}
			}
		}
	}

	/** Elegir qué hace [g] en [app]: la primera opción, si ya tiene uno propio, lo deja como en el resto. */
	private fun elegirDeApp(app: String, g: Gesto) {
		val general = Ajustes(this).mapaGestos[g]
		val propios = perfiles.deApp(app)?.gestos.orEmpty()
		val acciones = Accion.entries.filter { it != Accion.MODO_SEGURO && it !in PuenteEscritorio.ACCIONES_OCULTAS }
		val comoElResto = propios.containsKey(g)
		val nombres = (if (comoElResto) listOf(getString(R.string.gestos_como_el_resto, nombreAccion(this, general))) else emptyList()) +
			acciones.map { nombreAccion(this, it) }
		val desplazamiento = if (comoElResto) 1 else 0
		val actual = acciones.indexOf(propios[g] ?: general).coerceAtLeast(0) + desplazamiento
		elegirUno(getString(R.string.gestos_elegir, nombreGesto(this, g)), nombres, actual) { i ->
			val nuevos = propios.toMutableMap()
			if (comoElResto && i == 0) nuevos.remove(g)
			else {
				val accion = acciones[i - desplazamiento]
				// Lo mismo que en el resto no es un cambio de esta app
				if (accion == general) nuevos.remove(g) else nuevos[g] = accion
			}
			guardarDeApp(app, nuevos)
			construir()
		}
	}

	private fun guardarDeApp(app: String, gestos: Map<Gesto, Accion>) {
		val nuevo = (perfiles.deApp(app) ?: Perfiles.PerfilApp()).copy(gestos = gestos)
		perfiles.ponerApp(app, nuevo.takeIf { it != Perfiles.PerfilApp() })
		ServicioTiresias.instancia?.recargarAjustes()
	}

	private fun elegirApp() {
		val apps = com.xebolax.tiresias.base.Lanzables.todas(this).map { it.paquete to it.nombre }
		elegirUno(getString(R.string.perf_app_elegir), apps.map { it.second }, -1) { i -> abrirDeApp(this, apps[i].first) }
	}

	companion object {
		/** El paquete de la app cuyos gestos se cambian (sin él, los de todas). */
		const val EXTRA_PAQUETE = "paquete"

		/** Abre los gestos de [paquete]. */
		fun abrirDeApp(c: Context, paquete: String) {
			c.startActivity(android.content.Intent(c, ActividadGestos::class.java).putExtra(EXTRA_PAQUETE, paquete))
		}

		fun nombreApp(c: Context, paquete: String): String = runCatching {
			c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(paquete, 0)).toString()
		}.getOrDefault(paquete)

		fun nombreGesto(c: Context, g: Gesto): String {
			val t = Idioma.envolver(c)
			val id = t.resources.getIdentifier("gesto_" + g.name.lowercase(), "string", c.packageName)
			return if (id != 0) t.getString(id) else g.name.lowercase()
		}

		fun nombreAccion(c: Context, a: Accion): String {
			val t = Idioma.envolver(c)
			val id = t.resources.getIdentifier("accion_" + a.name.lowercase(), "string", c.packageName)
			return if (id != 0) t.getString(id) else a.name.lowercase()
		}
	}
}
