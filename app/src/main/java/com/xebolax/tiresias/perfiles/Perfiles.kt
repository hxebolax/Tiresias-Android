package com.xebolax.tiresias.perfiles

import android.content.Context
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.nucleo.Gesto
import com.xebolax.tiresias.nucleo.MapaGestos
import org.json.JSONObject
import java.io.File

/**
 * Perfiles de Tiresias.
 *
 * - **Por app**: cambios que solo valen en una app (gestos, velocidad,
 *   puntuación, verbosidad). Se aplican solos al pasar a esa app.
 * - **Con nombre**: fotos de los ajustes de voz y lectura (por ejemplo
 *   «Lectura de libros», «Silencioso») que se cambian con un gesto o en Ajustes.
 *
 * Se guardan en `perfiles.json` en el almacenamiento protegido y se exportan
 * con la copia de seguridad de la configuración.
 */
class Perfiles(contexto: Context, private val lector: Lector? = null) {
	private val contexto: Context = contexto.applicationContext

	/** Lo que cambia un perfil de app. Lo que es null sigue al general. */
	data class PerfilApp(
		val gestos: Map<Gesto, Accion> = emptyMap(),
		val velocidad: Int? = null,
		val puntuacion: String? = null,
		val verbosidad: String? = null,
		val ecoTeclado: String? = null,
	)

	private val porApp = HashMap<String, PerfilApp>()
	private val conNombre = LinkedHashMap<String, Map<String, Any?>>()
	private var activo: String? = null
	private var appActual: String? = null
	private var cargados = false

	private fun archivo(c: Context) = File(Almacen.protegido(c).filesDir, "perfiles.json")

	/** Se olvida lo leído: lo cambió la pantalla Perfiles o una copia de seguridad, y se vuelve a leer al usarlo. */
	@Synchronized
	fun olvidar() {
		porApp.clear()
		conNombre.clear()
		activo = null
		cargados = false
	}

	@Synchronized
	fun cargar(c: Context = contexto) {
		if (cargados) return
		cargados = true
		val f = archivo(c)
		if (!f.isFile) return
		try {
			val raiz = JSONObject(f.readText())
			raiz.optJSONObject("apps")?.let { apps ->
				for (paquete in apps.keys()) {
					val o = apps.getJSONObject(paquete)
					porApp[paquete] = PerfilApp(
						gestos = MapaGestos.leer(o.optString("gestos"), MapaGestos(emptyMap())).todas,
						velocidad = o.optInt("velocidad", 0).takeIf { it > 0 },
						puntuacion = o.optString("puntuacion").ifBlank { null },
						verbosidad = o.optString("verbosidad").ifBlank { null },
						ecoTeclado = o.optString("eco").ifBlank { null },
					)
				}
			}
			raiz.optJSONObject("nombres")?.let { n ->
				for (nombre in n.keys()) {
					val o = n.getJSONObject(nombre)
					conNombre[nombre] = o.keys().asSequence().associateWith { o.get(it) }
				}
			}
			activo = raiz.optString("activo").ifBlank { null }
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron leer los perfiles", e)
		}
	}

	@Synchronized
	fun guardar(c: Context = contexto) {
		try {
			val apps = JSONObject()
			for ((p, perfil) in porApp) {
				apps.put(p, JSONObject()
					.put("gestos", MapaGestos(perfil.gestos).escribir(MapaGestos(emptyMap())))
					.put("velocidad", perfil.velocidad ?: 0)
					.put("puntuacion", perfil.puntuacion.orEmpty())
					.put("verbosidad", perfil.verbosidad.orEmpty())
					.put("eco", perfil.ecoTeclado.orEmpty()))
			}
			val nombres = JSONObject()
			for ((n, mapa) in conNombre) nombres.put(n, JSONObject(mapa))
			val raiz = JSONObject().put("apps", apps).put("nombres", nombres).put("activo", activo.orEmpty())
			val f = archivo(c)
			val parcial = File(f.path + ".parcial")
			parcial.writeText(raiz.toString(1))
			parcial.renameTo(f)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron guardar los perfiles", e)
		}
	}

	@Synchronized
	fun deApp(paquete: String): PerfilApp? {
		cargar()
		return porApp[paquete]
	}

	@Synchronized
	fun ponerApp(paquete: String, perfil: PerfilApp?) {
		cargar()
		if (perfil == null) porApp.remove(paquete) else porApp[paquete] = perfil
		guardar()
	}

	@Synchronized
	fun apps(): Map<String, PerfilApp> {
		cargar()
		return porApp.toMap()
	}

	/** El mapa de gestos que vale en [paquete]: el general con los cambios de esa app. */
	fun mapaGestos(general: MapaGestos, paquete: String?): MapaGestos {
		val cambios = paquete?.let { deApp(it)?.gestos }.orEmpty().filterKeys { it !in MapaGestos.EMERGENCIA }
		return if (cambios.isEmpty()) general else general.mas(cambios)
	}

	/** Al cambiar de app: se aplican sus cambios de voz y lectura (y se quitan los de la anterior). */
	fun alCambiarApp(paquete: String?) {
		if (paquete == appActual) return
		appActual = paquete
		lector?.recargarAjustes()
	}

	/** La velocidad que vale ahora (la del perfil de la app, si la tiene). */
	fun velocidad(general: Int): Int = appActual?.let { deApp(it)?.velocidad } ?: general

	/** La verbosidad que vale ahora: la general o el nivel del perfil de la app. */
	fun verbosidad(general: com.xebolax.tiresias.nucleo.Verbosidad): com.xebolax.tiresias.nucleo.Verbosidad {
		val nivel = appActual?.let { deApp(it)?.verbosidad } ?: return general
		val n = Ajustes.enumDe(nivel, com.xebolax.tiresias.nucleo.NivelVerbosidad.ALTA)
		return if (n == com.xebolax.tiresias.nucleo.NivelVerbosidad.PERSONALIZADA) general else com.xebolax.tiresias.nucleo.Verbosidad.de(n).copy(orden = general.orden)
	}

	/** La puntuación del perfil de la app, o null para la general. */
	fun puntuacion(): com.xebolax.tiresias.nucleo.NivelPuntuacion? =
		appActual?.let { deApp(it)?.puntuacion }?.let { Ajustes.enumDe(it, com.xebolax.tiresias.nucleo.NivelPuntuacion.ALGUNAS) }

	/** El eco del teclado del perfil de la app, o null para el general. */
	fun ecoTeclado(): String? = appActual?.let { deApp(it)?.ecoTeclado }

	// --- Perfiles con nombre ----------------------------------------------------------------

	private val clavesGuardadas = listOf(
		Claves.MOTOR, Claves.VOZ_SISTEMA, Claves.VELOCIDAD, Claves.TONO, Claves.VOLUMEN_VOZ, Claves.PUNTUACION,
		Claves.NUMEROS, Claves.MAYUSCULAS, Claves.NIVEL_VERBOSIDAD, Claves.ORDEN, Claves.DECIR_PISTAS, Claves.SONIDOS,
		Claves.VIBRACION, Claves.ECO_TECLADO, Claves.LEER_NOTIFICACIONES,
	)

	@Synchronized
	fun guardarComo(nombre: String, c: Context = contexto) {
		cargar(c)
		val p = Ajustes(c).p
		conNombre[nombre] = clavesGuardadas.associateWith { p.all[it] }
		activo = nombre
		guardar(c)
	}

	@Synchronized
	fun nombres(c: Context = contexto): List<String> {
		cargar(c)
		return conNombre.keys.toList()
	}

	@Synchronized
	fun aplicar(nombre: String, c: Context = contexto): Boolean {
		cargar(c)
		val mapa = conNombre[nombre] ?: return false
		// La voz de ahora queda como la de su motor, y la del perfil, como la del motor del perfil
		val a = Ajustes(c)
		a.guardarVozAntesDeCambiar(mapa[Claves.MOTOR] as? String)
		val editor = a.p.edit()
		for ((k, v) in mapa) {
			when (v) {
				is String -> editor.putString(k, v)
				is Int -> editor.putInt(k, v)
				is Boolean -> editor.putBoolean(k, v)
				is Long -> editor.putLong(k, v)
				null, JSONObject.NULL -> editor.remove(k)
			}
		}
		editor.apply()
		activo = nombre
		guardar(c)
		lector?.recargarAjustes()
		return true
	}

	@Synchronized
	fun borrar(nombre: String, c: Context = contexto) {
		cargar(c)
		conNombre.remove(nombre)
		if (activo == nombre) activo = null
		guardar(c)
	}

	/** Pasa al siguiente perfil con nombre (gesto o tecla). */
	fun siguiente(): Boolean {
		val l = lector ?: return false
		val lista = nombres()
		if (lista.isEmpty()) {
			l.decir(R.string.perfil_ninguno)
			return false
		}
		val i = lista.indexOf(activo)
		val nuevo = lista[(i + 1) % lista.size]
		aplicar(nuevo)
		l.decir(l.textos.getString(R.string.perfil_activo, nuevo))
		return true
	}

	companion object {
		private const val ETIQUETA = "Perfiles"
	}
}
