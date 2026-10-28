package com.xebolax.tiresias.scripts

import com.xebolax.tiresias.arbol.Nodos
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.Elemento
import android.view.accessibility.AccessibilityNodeInfo
import com.xebolax.tiresias.voz.Sonido
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Los complementos de Tiresias: scripts en JavaScript (QuickJS) aislados, al
 * estilo de los de Backtalk.
 *
 * Un complemento es una carpeta en `files/complementos/<id>/` con
 * `complemento.js`, un módulo que exporta su `manifest`:
 *
 * ```js
 * export const manifest = { id: "mejor-whatsapp", nombre: "WhatsApp mejor", version: "1.0",
 *   apps: ["com.whatsapp"], permisos: ["voz"] }
 * tiresias.alEnfocar(e => e.nombre === "ic_send" ? "Enviar" : null)
 * tiresias.orden("Leer el último mensaje", () => tiresias.decir("..."))
 * ```
 *
 * - **Aislado**: cada complemento tiene su motor, con 16 MB de memoria y 30 ms
 *   por llamada como mucho; no ve Android ni Java, solo la API `tiresias`.
 * - **Permisos** que el usuario acepta al instalarlo: `voz` (decir y sonar),
 *   `portapapeles`. Cambiar lo que se dice de un elemento o de una notificación
 *   solo vale en las apps del manifiesto (`"*"`: todas).
 * - **Salida de emergencia**: [apagarTodos] (modo seguro, gesto que nada puede
 *   quitar) los apaga al momento; un complemento que falla o se pasa de tiempo
 *   tres veces seguidas se apaga solo.
 * - **Ajustes propios**: los que declara su manifiesto (`ajustes`), con su
 *   pantalla en Complementos; el complemento los lee con `tiresias.ajuste()`.
 * - **Traducibles**: cualquier texto del manifiesto puede llevar su versión en
 *   otro idioma con el sufijo del idioma (`nombre_en`, `descripcion_en`).
 */
class Scripts(private val lector: Lector?, contexto: Context = lector!!.servicio) {
	private val app = contexto.applicationContext
	private val carpeta = File(app.filesDir, "complementos")
	private val prefs = app.getSharedPreferences("complementos", Context.MODE_PRIVATE)
	private val cargados = LinkedHashMap<String, Cargado>()

	/** Un complemento instalado (cargado o no). */
	data class Complemento(
		val id: String, val nombre: String, val version: String, val apps: List<String>, val permisos: List<String>, val descripcion: String,
		val reglas: List<Regla> = emptyList(),
		/** Solo vale con estas pantallas delante (el final del nombre de su clase); vacía: todas (Backtalk 177 y 209). */
		val actividades: List<String> = emptyList(),
		/** Sus ajustes, en el orden del manifiesto (en el idioma de Tiresias). */
		val ajustes: List<AjusteComplemento> = emptyList(),
		/** La versión de la API que necesita como mínimo ([VERSION_API]) y la última con la que se ha probado. */
		val apiMinima: Int = 1,
		val apiProbada: Int = 1,
	)

	/**
	 * Un ajuste que declara un complemento. [tipo]: `si_no` (interruptor), `lista`
	 * (una de las [opciones]: valor y nombre), `numero` (de [min] a [max] de
	 * [paso] en [paso], con su [unidad]), `texto` o `apps` (varias apps elegidas
	 * de las instaladas; el valor es la lista de sus paquetes separados por comas).
	 */
	data class AjusteComplemento(
		val clave: String,
		val tipo: String,
		val nombre: String,
		val descripcion: String = "",
		val defecto: Any? = null,
		val opciones: List<Pair<String, String>> = emptyList(),
		val min: Int = 0,
		val max: Int = 100,
		val paso: Int = 1,
		val unidad: String = "",
	)

	/**
	 * Una regla sin código del manifiesto (Backtalk 205 y 223): para el elemento
	 * con el identificador [id] (completo o solo lo que va tras `:id/`), en las
	 * apps del complemento y, si se dice, solo con cierta [actividad] o [ventana]
	 * delante. Lo que se puede cambiar:
	 *
	 * - [etiqueta]: su nombre. [rol], [estado], [pista]: su tipo, su estado y su
	 *   pista, tal como se dicen. [encabezado]: si es un encabezado.
	 * - [ocultar]: no es una parada; con `"todo"`, tampoco nada de dentro ([ocultarDentro]).
	 * - [agrupar]: se lee entero en una sola parada, y sus botones de dentro son
	 *   acciones suyas (Backtalk 133). [activar]: el identificador del hijo que
	 *   se pulsa al activarlo.
	 * - [silenciar]: sus cambios no se anuncian; con `"todo"`, tampoco los de dentro.
	 * - [levantar]: se activa al levantar el dedo sobre él, sin doble toque.
	 *
	 * Ej.: `reglas: [{ id: "fondo", ocultar: true }, { id: "fila_app", agrupar: true }]`.
	 */
	data class Regla(
		val id: String,
		val etiqueta: String? = null,
		val ocultar: Boolean = false,
		val ocultarDentro: Boolean = false,
		val rol: String? = null,
		val estado: String? = null,
		val pista: String? = null,
		val encabezado: Boolean? = null,
		val agrupar: Boolean = false,
		val activar: String? = null,
		val silenciar: Boolean = false,
		val silenciarDentro: Boolean = false,
		val levantar: Boolean = false,
		val actividad: String? = null,
		val ventana: String? = null,
	)

	private inner class Cargado(val info: Complemento, val motor: QuickJs) {
		var fallos = 0
		val ordenes = mutableListOf<String>()
		/** Sus temporizadores pendientes (`tiresias.despues` y `tiresias.cada`), por su número. */
		val temporizadores = HashMap<Int, Runnable>()
		/** Si quiere saber de la batería (`tiresias.alBateria`). */
		var bateria = false
		/** Si quiere saber de las pantallas nuevas (`tiresias.alVentana`). */
		var ventana = false
		/** Si tiene una pregunta abierta (`tiresias.elegir`, `confirmar`, `pedirTexto`): una cada vez. */
		@Volatile var preguntando = false
	}

	/** Las ventanas de Tiresias, para las preguntas de los complementos (las pone el servicio). */
	var ventanas: com.xebolax.tiresias.ventanas.Ventanas? = null

	@Volatile var activos = prefs.getBoolean(CLAVE_ACTIVOS, true)
		private set

	/** Para las pruebas: ve cada cosa que pide un complemento (su id, el método y el valor). */
	@androidx.annotation.VisibleForTesting
	var espia: ((id: String, metodo: String, valor: String) -> Unit)? = null

	// --- Instalar y listar -------------------------------------------------------------

	/** Lee el manifiesto de un complemento sin instalarlo (para enseñar sus permisos). */
	fun leerManifiesto(codigo: String): Complemento {
		val motor = QuickJs(HostVacio, LIMITE_MEMORIA, LIMITE_PILA)
		try {
			val json = motor.evaluarModulo(PRELUDIO + "\n" + codigo, "complemento.js", LIMITE_CARGA_MS)
				?: throw QuickJsException("El complemento no exporta su manifest")
			return manifiesto(JSONObject(json))
		} finally {
			motor.close()
		}
	}

	fun instalar(codigo: String): Complemento {
		val info = leerManifiesto(codigo)
		val d = File(carpeta, info.id).apply { mkdirs() }
		File(d, ARCHIVO).writeText(codigo)
		prefs.edit().putBoolean(info.id, true).apply()
		recargar()
		return info
	}

	fun desinstalar(id: String) {
		descargar(id)
		datosDe(id).edit().clear().apply()
		ajustesDe(id).edit().clear().apply()
		File(carpeta, id).deleteRecursively()
		prefs.edit().remove(id).apply()
	}

	fun instalados(): List<Complemento> = carpeta.listFiles().orEmpty().filter { File(it, ARCHIVO).isFile }.mapNotNull { d ->
		runCatching { leerManifiesto(File(d, ARCHIVO).readText()) }.getOrElse {
			Registro.w(ETIQUETA, "Complemento ${d.name} no válido", it)
			null
		}
	}

	fun activado(id: String) = prefs.getBoolean(id, true)

	fun activar(id: String, si: Boolean) {
		prefs.edit().putBoolean(id, si).apply()
		recargar()
	}

	// --- Carga --------------------------------------------------------------------------

	/** Carga los complementos activados (en el hilo del lector). */
	@Synchronized
	fun recargar() {
		cargados.keys.toList().forEach { descargar(it) }
		if (activos) for (d in carpeta.listFiles().orEmpty()) {
			val f = File(d, ARCHIVO)
			if (!f.isFile || !activado(d.name)) continue
			cargar(f.readText())
		}
		vigilarBateria()
	}

	private fun cargar(codigo: String) {
		// El manifiesto primero (con una API que no hace nada): así el complemento ya está dado de alta cuando
		// se ejecuta de verdad, y lo que pide al cargarse (sus temporizadores, el idioma de sus órdenes) se atiende
		val info = try {
			leerManifiesto(codigo)
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "Complemento que no se pudo cargar: ${e.message}", e)
			return
		}
		var c: Cargado? = null
		val host = object : QuickJs.Host {
			override fun call(method: String, json: String?): String? = c?.let { atender(it, method, json) }
			override fun loadModule(name: String): ByteArray? = null
			override fun onUnhandledError(error: QuickJsException) {
				Registro.w(ETIQUETA, "Error sin atender en un complemento: ${error.message}")
			}
		}
		val motor = try {
			QuickJs(host, LIMITE_MEMORIA, LIMITE_PILA)
		} catch (e: QuickJsException) {
			Registro.e(ETIQUETA, "No arranca el motor de complementos", e)
			return
		}
		val cargado = Cargado(info, motor)
		c = cargado
		try {
			motor.evaluarModulo(PRELUDIO + "\n" + codigo, "complemento.js", LIMITE_CARGA_MS) ?: throw QuickJsException("Sin manifest")
			cargados[info.id] = cargado
			motor.llamar("__ordenes", null, LIMITE_LLAMADA_MS)?.let { r ->
				val a = JSONArray(r)
				for (i in 0 until a.length()) cargado.ordenes += a.getString(i)
			}
			cargado.bateria = motor.llamar("__tieneBateria", null, LIMITE_LLAMADA_MS) == "si"
			cargado.ventana = motor.llamar("__tieneVentana", null, LIMITE_LLAMADA_MS) == "si"
		} catch (e: QuickJsException) {
			Registro.w(ETIQUETA, "Complemento que no se pudo cargar: ${e.message}")
			// Lo que pidió antes de fallar no se queda programado
			cargado.temporizadores.values.forEach { lector?.manejador?.removeCallbacks(it) }
			cargado.temporizadores.clear()
			motor.close()
		}
	}

	private fun descargar(id: String) {
		val c = cargados.remove(id) ?: return
		c.temporizadores.values.forEach { lector?.manejador?.removeCallbacks(it) }
		c.temporizadores.clear()
		c.motor.close()
		vigilarBateria()
	}

	/** Al cerrar Tiresias: fuera los motores, los temporizadores y el aviso de la batería. */
	@Synchronized
	fun cerrar() {
		cargados.keys.toList().forEach { descargar(it) }
		vigilarBateria()
	}

	/** La salida de emergencia: apaga todos los complementos al momento. */
	@Synchronized
	fun apagarTodos() {
		activos = false
		prefs.edit().putBoolean(CLAVE_ACTIVOS, false).apply()
		cargados.keys.toList().forEach { descargar(it) }
	}

	@Synchronized
	fun encender() {
		activos = true
		prefs.edit().putBoolean(CLAVE_ACTIVOS, true).apply()
		recargar()
	}

	// --- Ganchos ------------------------------------------------------------------------

	/**
	 * Lo que dicen los complementos de [e] en [paquete] en vez del texto normal,
	 * o null. Pasan uno tras otro: cada uno recibe en `dicho` lo que dejó el
	 * anterior (así se suman, por ejemplo, poner nombre a un botón y acortar enlaces).
	 */
	@Synchronized
	fun alEnfocar(paquete: String, e: Elemento, texto: String): String? {
		// Lo de un campo de contraseña nunca llega a un complemento
		if (!activos || cargados.isEmpty() || e.contrasena || e.sensible) return null
		var actual = texto
		var cambiado = false
		for (c in cargados.values) {
			if (!vale(c, paquete)) continue
			val json = JSONObject().put("app", paquete).put("nombre", e.nombre).put("rol", e.rol.name.lowercase()).put("texto", e.texto)
				.put("id", e.identificador ?: "").put("estado", e.estado ?: "").put("dicho", actual).toString()
			// El motor devuelve los textos tal cual (sin comillas JSON) y null si el complemento no cambia nada
			val r = llamar(c, "__enfocar", json) ?: continue
			if (com.xebolax.tiresias.BuildConfig.PRUEBAS) android.util.Log.d("TiresiasComplementos", "${c.info.id} en $paquete cambia el texto")
			actual = r
			cambiado = true
		}
		return if (cambiado) actual else null
	}

	/**
	 * Lo que dicen los complementos de una notificación: el texto nuevo, "" para
	 * callarla o null para la normal. La reciben todos (los que solo cuentan o
	 * copian un código también), y manda lo del primero que la cambie.
	 */
	@Synchronized
	fun alNotificar(paquete: String, titulo: String, texto: String, extra: Map<String, Any> = emptyMap()): String? {
		if (!activos || cargados.isEmpty()) return null
		val j = JSONObject().put("app", paquete).put("titulo", titulo).put("texto", texto)
		for ((k, v) in extra) j.put(k, v)
		val json = j.toString()
		var resultado: String? = null
		for (c in cargados.values) {
			if (!vale(c, paquete)) continue
			val r = llamar(c, "__notificar", json)
			if (resultado == null && r != null) resultado = r
		}
		return resultado
	}

	/** Una pantalla nueva de [paquete]: se la dice a los complementos que la quieren (`tiresias.alVentana`). */
	@Synchronized
	fun alVentana(paquete: String, actividad: String, titulo: String) {
		if (!activos || cargados.isEmpty()) return
		val json = JSONObject().put("app", paquete).put("actividad", actividad).put("titulo", titulo).toString()
		for (c in cargados.values) if (c.ventana && vale(c, paquete)) llamar(c, "__ventana", json)
	}

	/** La etiqueta que pone una regla de un complemento al elemento [id] de [paquete], o null. */
	fun etiquetaRegla(paquete: String, id: String): String? = reglasDe(paquete, id).firstNotNullOfOrNull { it.etiqueta }

	/** Si una regla de un complemento oculta el elemento [id] de [paquete] (no es una parada). */
	fun oculto(paquete: String, id: String): Boolean = reglasDe(paquete, id).any { it.ocultar || it.ocultarDentro }

	/** Si hay alguna regla cargada (para no preguntar por cada elemento si no hay ninguna). */
	val hayReglas: Boolean get() = activos && cargados.values.toList().any { it.info.reglas.isNotEmpty() }

	/** Si alguna regla mira dentro de un elemento (ocultar o silenciar todo lo de dentro). */
	private val hayReglasDentro: Boolean get() = activos && cargados.values.toList().any { c -> c.info.reglas.any { it.ocultarDentro || it.silenciarDentro } }

	/** Las reglas que tocan al elemento [id] de [paquete], de la primera a la última (manda la primera que ponga algo). */
	private fun reglasDe(paquete: String, id: String?): List<Regla> {
		if (!activos || id.isNullOrEmpty()) return emptyList()
		val corto = id.substringAfter(":id/", id)
		val delante = lector?.actividadDelante
		val ventana = lector?.tituloVentanaActual().orEmpty()
		return cargados.values.toList().filter { vale(it, paquete) }.flatMap { c ->
			c.info.reglas.filter { r ->
				(r.id == id || r.id == corto) &&
					(r.actividad == null || delante == null || delante.endsWith(r.actividad)) &&
					(r.ventana == null || ventana.contains(r.ventana, ignoreCase = true))
			}
		}
	}

	/** Todo lo que las reglas cambian del elemento [n], junto (para cada cosa, la primera regla que la pone), o null. */
	fun regla(n: AccessibilityNodeInfo): Regla? {
		if (!hayReglas) return null
		val lista = reglasDe(n.packageName?.toString().orEmpty(), n.viewIdResourceName)
		if (lista.isEmpty()) return null
		return Regla(
			id = lista.first().id,
			etiqueta = lista.firstNotNullOfOrNull { it.etiqueta },
			ocultar = lista.any { it.ocultar },
			ocultarDentro = lista.any { it.ocultarDentro },
			rol = lista.firstNotNullOfOrNull { it.rol },
			estado = lista.firstNotNullOfOrNull { it.estado },
			pista = lista.firstNotNullOfOrNull { it.pista },
			encabezado = lista.firstNotNullOfOrNull { it.encabezado },
			agrupar = lista.any { it.agrupar },
			activar = lista.firstNotNullOfOrNull { it.activar },
			silenciar = lista.any { it.silenciar },
			silenciarDentro = lista.any { it.silenciarDentro },
			levantar = lista.any { it.levantar },
		)
	}

	/** Si [n] está dentro de un elemento que una regla oculta con todo lo de dentro. */
	fun ocultoDentro(n: AccessibilityNodeInfo): Boolean = hayReglasDentro && antepasados(n).any { regla(it)?.ocultarDentro == true }

	/** Si los cambios de [n] no se anuncian (por él o por un elemento de fuera que lo silencia todo). */
	fun silenciado(n: AccessibilityNodeInfo): Boolean {
		if (!hayReglas) return false
		if (regla(n)?.let { it.silenciar || it.silenciarDentro } == true) return true
		return hayReglasDentro && antepasados(n).any { regla(it)?.silenciarDentro == true }
	}

	private fun antepasados(n: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
		var p = runCatching { n.parent }.getOrNull()
		var nivel = 0
		while (p != null && nivel < 20) {
			yield(p)
			p = runCatching { p!!.parent }.getOrNull()
			nivel++
		}
	}

	/**
	 * Las acciones que los complementos añaden a [e] en [paquete] (el gancho
	 * `alAcciones`): (id del complemento, posición, nombre). Se hacen con [hacerAccion].
	 */
	@Synchronized
	fun acciones(paquete: String, e: Elemento): List<Triple<String, Int, String>> {
		if (!activos || cargados.isEmpty() || e.contrasena || e.sensible) return emptyList()
		val json = JSONObject().put("app", paquete).put("nombre", e.nombre).put("rol", e.rol.name.lowercase()).put("texto", e.texto)
			.put("id", e.identificador ?: "").put("estado", e.estado ?: "").toString()
		val lista = ArrayList<Triple<String, Int, String>>()
		for (c in cargados.values) {
			if (!vale(c, paquete)) continue
			val r = llamar(c, "__acciones", json) ?: continue
			val a = runCatching { JSONArray(r) }.getOrNull() ?: continue
			for (i in 0 until a.length()) lista += Triple(c.info.id, i, a.optString(i))
		}
		return lista
	}

	@Synchronized
	fun hacerAccion(id: String, posicion: Int) {
		val c = cargados[id] ?: return
		llamar(c, "__accion", posicion.toString())
	}

	/**
	 * Una orden de la línea braille ([tipo] y [valor]) pasa antes por los
	 * complementos con permiso `braille`: si uno devuelve true, se la queda y
	 * Tiresias no hace nada más (Backtalk 153).
	 */
	@Synchronized
	fun teclaBraille(tipo: String, valor: String): Boolean {
		if (!activos || cargados.isEmpty()) return false
		val paquete = lector?.raiz()?.packageName?.toString().orEmpty()
		val json = JSONObject().put("tipo", tipo).put("valor", valor).put("app", paquete).toString()
		for (c in cargados.values) {
			if ("braille" !in c.info.permisos || !vale(c, paquete)) continue
			if (llamar(c, "__teclaBraille", json) == "si") return true
		}
		return false
	}

	/** Las órdenes de los complementos para el menú de Tiresias: (id del complemento, nombre de la orden). */
	@Synchronized
	fun ordenes(paquete: String?): List<Pair<String, String>> =
		cargados.values.filter { paquete == null || vale(it, paquete) }.flatMap { c -> c.ordenes.map { c.info.id to it } }

	@Synchronized
	fun ejecutarOrden(id: String, orden: String) {
		val c = cargados[id] ?: return
		llamar(c, "__orden", JSONObject.quote(orden))
	}

	private fun vale(c: Cargado, paquete: String): Boolean {
		if (c.info.apps.isNotEmpty() && "*" !in c.info.apps && paquete !in c.info.apps) return false
		if (c.info.actividades.isEmpty()) return true
		// Una pantalla aún sin saber (recién abierta) cuenta como que vale
		val delante = lector?.actividadDelante ?: return true
		return c.info.actividades.any { delante.endsWith(it) }
	}

	private fun llamar(c: Cargado, funcion: String, json: String?): String? = try {
		c.motor.llamar(funcion, json, LIMITE_LLAMADA_MS).also { c.fallos = 0 }
	} catch (e: QuickJsException) {
		Registro.w(ETIQUETA, "El complemento ${c.info.id} falló en $funcion${if (e.interrumpido) " (se pasó de tiempo)" else ""}: ${e.message}")
		if (++c.fallos >= FALLOS_PARA_APAGAR) {
			Registro.w(ETIQUETA, "El complemento ${c.info.id} se apaga por fallar seguido")
			prefs.edit().putBoolean(c.info.id, false).apply()
			descargar(c.info.id)
			lector?.decir(lector.textos.getString(com.xebolax.tiresias.R.string.scripts_apagado_uno, c.info.nombre))
		}
		null
	}

	/** Lo que un complemento pide a Tiresias. Solo lo que permite su manifiesto. */
	private fun atender(c: Cargado, metodo: String, json: String?): String? {
		val arg = json?.let { runCatching { JSONObject("{\"v\":$it}").opt("v") }.getOrNull() }
		espia?.invoke(c.info.id, metodo, (arg as? String) ?: arg?.toString().orEmpty())
		// Lo que no necesita al lector (también vale en las pantallas de Tiresias y en las pruebas)
		when (metodo) {
			"ajuste" -> return c.info.ajustes.firstOrNull { it.clave == (arg as? String) }?.let { valorJson(c.info.id, it) } ?: "null"
			"idioma" -> return JSONObject.quote(Idioma.efectivo(app).language)
			"ahora" -> return ahora().toString()
			"bateria" -> return estadoBateria(app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))?.toString() ?: "null"
			"pantalla" -> return JSONObject()
				.put("encendida", app.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false)
				.put("bloqueada", app.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true)
				.toString()
		}
		val l = lector ?: return null
		when (metodo) {
			"decir" -> if ("voz" in c.info.permisos) l.decir((arg as? String).orEmpty())
			"sonido" -> if ("voz" in c.info.permisos) runCatching { Sonido.valueOf((arg as? String).orEmpty().uppercase()) }.getOrNull()?.let { l.sonidos.tocar(it) }
			"portapapeles" -> if ("portapapeles" in c.info.permisos) l.edicion.portapapeles((arg as? String).orEmpty())
			"app" -> return JSONObject.quote(l.raiz()?.packageName?.toString().orEmpty())
			"registro" -> Registro.i(ETIQUETA, "[${c.info.id}] ${(arg as? String).orEmpty().take(200)}")
			"braille" -> if ("braille" in c.info.permisos && l.brailleListo) l.braille.mensaje((arg as? String).orEmpty().take(500))
			// Lo que guarda un complemento (sus notas, sus ajustes): solo él lo ve, en el móvil, sin pasar de 64 KB
			"guardar" -> (arg as? JSONArray)?.let { a ->
				val datos = datosDe(c.info.id)
				val clave = a.optString(0).take(100)
				val valor = a.optString(1)
				val total = datos.all.entries.sumOf { (k, v) -> if (k == clave) 0 else k.length + (v?.toString()?.length ?: 0) }
				if (clave.isNotEmpty() && total + clave.length + valor.length <= LIMITE_DATOS) datos.edit().putString(clave, valor).apply()
				else Registro.w(ETIQUETA, "El complemento ${c.info.id} se pasa del espacio para sus datos")
			}
			"leer" -> return datosDe(c.info.id).getString((arg as? String).orEmpty(), null)?.let { JSONObject.quote(it) } ?: "null"
			"borrarDato" -> datosDe(c.info.id).edit().remove((arg as? String).orEmpty()).apply()
			"actividad" -> return JSONObject.quote(l.actividadDelante.orEmpty())
			// El elemento enfocado (para las órdenes que trabajan con él: copiar su texto, contar sus palabras...)
			"elemento" -> {
				val f = l.foco ?: return "null"
				if (Nodos.sensible(f)) return "null"
				val e = l.elemento(f)
				return JSONObject().put("app", f.packageName?.toString().orEmpty()).put("nombre", e.nombre).put("rol", e.rol.name.lowercase())
					.put("texto", e.texto).put("id", e.identificador ?: "").put("estado", e.estado ?: "").toString()
			}
			// «decir» con cola: [texto, true] no corta lo que se está diciendo
			"decirEnCola" -> if ("voz" in c.info.permisos) l.decir((arg as? String).orEmpty(), com.xebolax.tiresias.voz.Modo.ENCOLAR)
			"portapapelesSensible" -> if ("portapapeles" in c.info.permisos) l.edicion.portapapeles((arg as? String).orEmpty(), sensible = true)
			"vaciarPortapapeles" -> if ("portapapeles" in c.info.permisos) return if (l.edicion.vaciarPortapapeles((arg as? String).orEmpty())) "true" else "false"
			"abrir" -> if ("abrir" in c.info.permisos) return if (abrir((arg as? String).orEmpty())) "true" else "false"
			"apps" -> if ("abrir" in c.info.permisos) return appsDelLanzador().toString()
			"abrirApp" -> if ("abrir" in c.info.permisos) return if (abrirApp((arg as? String).orEmpty())) "true" else "false"
			"temporizador" -> (arg as? JSONArray)?.let { a -> programar(c, a.optInt(0), a.optLong(1), a.optBoolean(2)) }
			// Si suena una llamada o hay una en curso (también las de WhatsApp, Telegram, Signal...)
			"llamada" -> return JSONObject.quote(l.avisos.estadoLlamada())
			"infoApp" -> return infoApp((arg as? String).orEmpty().ifEmpty { l.raiz()?.packageName?.toString().orEmpty() })?.toString() ?: "null"
			"luz" -> (arg as? Number)?.toInt()?.let { n -> leerLuz(c, n) }
			"elegir" -> (arg as? JSONArray)?.let { a ->
				val opciones = a.optJSONArray(2)?.let { o -> (0 until o.length()).map { o.optString(it).take(200) } }.orEmpty().take(50)
				preguntar(c, a.optInt(0), a.optString(1).take(200), opciones) { i -> i }
			}
			"confirmar" -> (arg as? JSONArray)?.let { a ->
				val t = l.textos
				preguntar(c, a.optInt(0), a.optString(1).take(300), listOf(t.getString(com.xebolax.tiresias.R.string.aceptar), t.getString(com.xebolax.tiresias.R.string.cancelar))) { i -> i == 0 }
			}
			"pedirTexto" -> (arg as? JSONArray)?.let { a -> pedirTexto(c, a.optInt(0), a.optString(1).take(200), a.optString(2).take(200)) }
			"cancelarTemporizador" -> (arg as? Number)?.toInt()?.let { n -> c.temporizadores.remove(n)?.let { l.manejador.removeCallbacks(it) } }
		}
		return null
	}

	// --- Preguntas, la luz y los datos de una app -------------------------------------------

	/** Devuelve [valor] a la función número [n] del complemento (la de una pregunta o de la luz), en el hilo del lector. */
	private fun responder(c: Cargado, n: Int, valor: Any?) {
		val l = lector ?: return
		l.manejador.post {
			synchronized(this@Scripts) {
				if (cargados[c.info.id] !== c) return@post
				llamar(c, "__respuesta", JSONArray().put(n).put(valor ?: JSONObject.NULL).toString())
			}
		}
	}

	/**
	 * Una lista de Tiresias con [opciones]; la función recibe lo que diga
	 * [respuesta] del elegido (o null si se cancela). Una pregunta cada vez por
	 * complemento, y nunca encima de otra ventana de Tiresias.
	 */
	private fun preguntar(c: Cargado, n: Int, titulo: String, opciones: List<String>, respuesta: (Int) -> Any) {
		val v = ventanas
		if (v == null || opciones.isEmpty() || c.preguntando || v.hayAbierta) {
			responder(c, n, null)
			return
		}
		c.preguntando = true
		v.lista(titulo, opciones, alCancelar = {
			c.preguntando = false
			responder(c, n, null)
		}) { i ->
			c.preguntando = false
			responder(c, n, respuesta(i))
		}
	}

	private fun pedirTexto(c: Cargado, n: Int, titulo: String, pista: String) {
		val v = ventanas
		if (v == null || c.preguntando || v.hayAbierta) {
			responder(c, n, null)
			return
		}
		c.preguntando = true
		v.pedirTexto(titulo, pista, alCancelar = {
			c.preguntando = false
			responder(c, n, null)
		}) { texto ->
			c.preguntando = false
			responder(c, n, texto.take(2000))
		}
	}

	/** Una lectura del sensor de luz (en luxes), o null si el móvil no tiene o no contesta en 2 segundos. */
	private fun leerLuz(c: Cargado, n: Int) {
		val l = lector ?: return
		val sensores = app.getSystemService(android.hardware.SensorManager::class.java)
		val sensor = sensores?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
		if (sensores == null || sensor == null) {
			responder(c, n, null)
			return
		}
		var contestado = false
		lateinit var oyente: android.hardware.SensorEventListener
		val fin = Runnable {
			if (contestado) return@Runnable
			contestado = true
			sensores.unregisterListener(oyente)
			responder(c, n, null)
		}
		oyente = object : android.hardware.SensorEventListener {
			override fun onSensorChanged(e: android.hardware.SensorEvent) {
				if (contestado) return
				contestado = true
				l.manejador.removeCallbacks(fin)
				sensores.unregisterListener(this)
				responder(c, n, e.values.firstOrNull()?.toDouble())
			}

			override fun onAccuracyChanged(s: android.hardware.Sensor?, precision: Int) = Unit
		}
		sensores.registerListener(oyente, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL, l.manejador)
		l.manejador.postDelayed(fin, 2000)
	}

	/** El nombre, la versión y las fechas de instalación y de la última actualización de [paquete], o null. */
	private fun infoApp(paquete: String): JSONObject? {
		if (paquete.isEmpty()) return null
		return try {
			val pm = app.packageManager
			val i = pm.getPackageInfo(paquete, 0)
			val codigo = if (android.os.Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong()
			JSONObject().put("paquete", paquete).put("nombre", i.applicationInfo?.loadLabel(pm)?.toString() ?: paquete)
				.put("version", i.versionName.orEmpty()).put("codigo", codigo)
				.put("instalada", i.firstInstallTime).put("actualizada", i.lastUpdateTime)
		} catch (e: android.content.pm.PackageManager.NameNotFoundException) {
			Registro.w(ETIQUETA, "No se ve la app $paquete", e)
			null
		}
	}

	// --- Temporizadores ------------------------------------------------------------------

	/** Un temporizador del complemento: [ms] después (y cada [ms], si [repetir]) llama a su función número [n]. */
	private fun programar(c: Cargado, n: Int, ms: Long, repetir: Boolean) {
		val l = lector ?: return
		if (c.temporizadores.size >= MAXIMO_TEMPORIZADORES && n !in c.temporizadores) {
			Registro.w(ETIQUETA, "El complemento ${c.info.id} tiene demasiados temporizadores")
			return
		}
		val espera = ms.coerceIn(if (repetir) 1000L else 100L, 24L * 3600 * 1000)
		c.temporizadores.remove(n)?.let { l.manejador.removeCallbacks(it) }
		val r = object : Runnable {
			override fun run() {
				synchronized(this@Scripts) {
					if (cargados[c.info.id] !== c || c.temporizadores[n] !== this) return
					if (repetir) l.manejador.postDelayed(this, espera) else c.temporizadores.remove(n)
					llamar(c, "__temporizador", n.toString())
				}
			}
		}
		c.temporizadores[n] = r
		l.manejador.postDelayed(r, espera)
	}

	// --- Batería -------------------------------------------------------------------------

	private var receptorBateria: BroadcastReceiver? = null
	private var ultimaBateria: String? = null

	/** Escucha la batería solo si algún complemento cargado quiere saber de ella. */
	private fun vigilarBateria() {
		val l = lector ?: return
		val quiere = activos && cargados.values.any { it.bateria }
		if (quiere && receptorBateria == null) {
			val r = object : BroadcastReceiver() {
				override fun onReceive(contexto: Context, intent: Intent) {
					val estado = estadoBateria(intent) ?: return
					l.manejador.post { avisarBateria(estado) }
				}
			}
			receptorBateria = r
			runCatching { app.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
				.onFailure { Registro.w(ETIQUETA, "No se pudo escuchar la batería", it) }
		} else if (!quiere && receptorBateria != null) {
			runCatching { app.unregisterReceiver(receptorBateria) }.onFailure { Registro.w(ETIQUETA, "No se pudo dejar de escuchar la batería", it) }
			receptorBateria = null
			ultimaBateria = null
		}
	}

	/** Pasa la batería a los complementos solo si cambia el nivel o la carga (Android avisa muy a menudo por la temperatura). */
	@Synchronized
	private fun avisarBateria(estado: JSONObject) {
		val clave = "${estado.optInt("nivel")}/${estado.optBoolean("cargando")}/${estado.optString("enchufe")}"
		if (clave == ultimaBateria) return
		ultimaBateria = clave
		val json = estado.toString()
		for (c in cargados.values.toList()) if (c.bateria) llamar(c, "__bateria", json)
	}

	private fun estadoBateria(i: Intent?): JSONObject? {
		i ?: return null
		val nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
		val escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
		if (nivel < 0) return null
		val estado = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
		val enchufe = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
			BatteryManager.BATTERY_PLUGGED_AC -> "corriente"
			BatteryManager.BATTERY_PLUGGED_USB -> "usb"
			BatteryManager.BATTERY_PLUGGED_WIRELESS -> "inalambrico"
			0 -> ""
			else -> "otro"
		}
		return JSONObject()
			.put("nivel", nivel * 100 / escala)
			.put("cargando", estado == BatteryManager.BATTERY_STATUS_CHARGING || estado == BatteryManager.BATTERY_STATUS_FULL && enchufe.isNotEmpty())
			.put("llena", estado == BatteryManager.BATTERY_STATUS_FULL)
			.put("enchufe", enchufe)
			.put("temperatura", i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
	}

	// --- Hora, abrir ---------------------------------------------------------------------

	private fun ahora(): JSONObject {
		val c = java.util.Calendar.getInstance()
		return JSONObject()
			.put("anio", c.get(java.util.Calendar.YEAR))
			.put("mes", c.get(java.util.Calendar.MONTH) + 1)
			.put("dia", c.get(java.util.Calendar.DAY_OF_MONTH))
			// 1 = lunes ... 7 = domingo
			.put("diaSemana", (c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 + 1)
			.put("hora", c.get(java.util.Calendar.HOUR_OF_DAY))
			.put("minuto", c.get(java.util.Calendar.MINUTE))
			.put("segundo", c.get(java.util.Calendar.SECOND))
			.put("ms", System.currentTimeMillis())
			.put("formato24", android.text.format.DateFormat.is24HourFormat(app))
	}

	/**
	 * Abre un enlace, un teléfono (solo el marcador: nunca llama sola), un
	 * correo, un mapa o un SMS. Nada más: ni archivos, ni apps por su nombre.
	 */
	private fun abrir(uri: String): Boolean {
		val u = runCatching { android.net.Uri.parse(uri.trim()) }.getOrNull() ?: return false
		val accion = when (u.scheme?.lowercase()) {
			"http", "https", "mailto", "geo", "sms", "smsto" -> Intent.ACTION_VIEW
			"tel" -> Intent.ACTION_DIAL
			else -> return false
		}
		return try {
			app.startActivity(Intent(accion, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			true
		} catch (e: android.content.ActivityNotFoundException) {
			Registro.w(ETIQUETA, "No hay app para abrir un ${u.scheme}", e)
			false
		}
	}

	/** Las apps que se pueden abrir (las del lanzador), por su nombre: [{nombre, paquete}]. */
	private fun appsDelLanzador(): JSONArray {
		val pm = app.packageManager
		val lista = runCatching {
			pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
				.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
				.distinctBy { it.first }
				.sortedBy { it.second.lowercase() }
		}.getOrElse {
			Registro.w(ETIQUETA, "No se pudo leer la lista de apps", it)
			emptyList()
		}
		return JSONArray().also { a -> lista.forEach { (p, n) -> a.put(JSONObject().put("nombre", n).put("paquete", p)) } }
	}

	/** Abre la app [paquete] por su pantalla de inicio. */
	private fun abrirApp(paquete: String): Boolean {
		val intent = app.packageManager.getLaunchIntentForPackage(paquete) ?: return false
		return try {
			app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
			true
		} catch (e: android.content.ActivityNotFoundException) {
			Registro.w(ETIQUETA, "No se pudo abrir la app", e)
			false
		}
	}

	// --- Ajustes del complemento -----------------------------------------------------------

	private fun ajustesDe(id: String) = app.getSharedPreferences("complemento_ajustes_$id", Context.MODE_PRIVATE)

	/** El valor de [a] para el complemento [id] (el elegido o el de fábrica), con su tipo. */
	fun valorAjuste(id: String, a: AjusteComplemento): Any {
		val p = ajustesDe(id)
		return when (a.tipo) {
			"si_no" -> p.getBoolean(a.clave, a.defecto as? Boolean ?: false)
			"numero" -> p.getInt(a.clave, (a.defecto as? Number)?.toInt() ?: a.min).coerceIn(a.min, a.max)
			else -> p.getString(a.clave, a.defecto?.toString() ?: (if (a.tipo == "lista") a.opciones.firstOrNull()?.first else null) ?: "") ?: ""
		}
	}

	fun ponerAjuste(id: String, a: AjusteComplemento, valor: Any) {
		val e = ajustesDe(id).edit()
		when (valor) {
			is Boolean -> e.putBoolean(a.clave, valor)
			is Int -> e.putInt(a.clave, valor.coerceIn(a.min, a.max))
			else -> e.putString(a.clave, valor.toString().take(2000))
		}
		e.apply()
	}

	/** Deja todos los ajustes del complemento [id] como vienen de fábrica. */
	fun restablecerAjustes(id: String) = ajustesDe(id).edit().clear().apply()

	private fun valorJson(id: String, a: AjusteComplemento): String = when (val v = valorAjuste(id, a)) {
		is Boolean, is Int -> v.toString()
		// «apps»: una lista de paquetes
		else -> if (a.tipo == "apps") JSONArray(v.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }).toString() else JSONObject.quote(v.toString())
	}

	private fun datosDe(id: String) = app.getSharedPreferences("complemento_$id", Context.MODE_PRIVATE)

	/** El idioma de Tiresias, para los textos traducidos de los manifiestos. */
	private val idioma: String get() = runCatching { Idioma.efectivo(app).language }.getOrDefault("es")

	/** El texto [k] de [o] en el idioma de Tiresias (`k_en`...), o el de siempre (en español). */
	private fun local(o: JSONObject, k: String, defecto: String = ""): String =
		o.optString("${k}_$idioma").trim().takeIf { idioma != "es" && it.isNotEmpty() } ?: o.optString(k, defecto).trim()

	private fun ajustes(m: JSONObject): List<AjusteComplemento> {
		val a = m.optJSONArray("ajustes") ?: return emptyList()
		return (0 until a.length()).mapNotNull { i ->
			val o = a.optJSONObject(i) ?: return@mapNotNull null
			val clave = o.optString("clave").trim()
			val tipo = o.optString("tipo", "si_no").trim()
			if (!Regex("[A-Za-z0-9_]{1,40}").matches(clave) || tipo !in setOf("si_no", "lista", "numero", "texto", "apps")) return@mapNotNull null
			val opciones = o.optJSONArray("opciones")?.let { l ->
				(0 until l.length()).mapNotNull { k -> l.optJSONObject(k)?.let { op -> op.optString("valor") to local(op, "nombre", op.optString("valor")) } }
			}.orEmpty()
			if (tipo == "lista" && opciones.isEmpty()) return@mapNotNull null
			AjusteComplemento(
				clave = clave, tipo = tipo, nombre = local(o, "nombre", clave), descripcion = local(o, "descripcion"),
				defecto = o.opt("defecto")?.takeIf { it != JSONObject.NULL }, opciones = opciones,
				min = o.optInt("min", 0), max = o.optInt("max", 100), paso = o.optInt("paso", 1).coerceAtLeast(1), unidad = local(o, "unidad"),
			)
		}
	}

	private fun manifiesto(m: JSONObject): Complemento {
		val id = m.optString("id").trim()
		if (!Regex("[a-z0-9][a-z0-9._-]{1,63}").matches(id)) throw QuickJsException("El id del complemento no es válido: $id")
		fun lista(clave: String) = m.optJSONArray(clave)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
		val reglas = m.optJSONArray("reglas")?.let { a ->
			(0 until a.length()).mapNotNull { i ->
				val r = a.optJSONObject(i) ?: return@mapNotNull null
				val rid = r.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
				fun texto(k: String) = r.optString(k).trim().takeIf { r.has(k) && it.isNotEmpty() }
				// «ocultar» y «silenciar»: true (el elemento) o "todo" (también lo de dentro)
				fun nivel(k: String): Pair<Boolean, Boolean> = when (val v = r.opt(k)) {
					is Boolean -> v to false
					is String -> (v == "si" || v == "true") to (v == "todo")
					else -> false to false
				}
				val (ocultar, ocultarDentro) = nivel("ocultar")
				val (silenciar, silenciarDentro) = nivel("silenciar")
				Regla(
					id = rid, etiqueta = texto("etiqueta"), ocultar = ocultar, ocultarDentro = ocultarDentro,
					rol = texto("rol"), estado = texto("estado"), pista = texto("pista"),
					encabezado = if (r.has("encabezado")) r.optBoolean("encabezado") else null,
					agrupar = r.optBoolean("agrupar", false), activar = texto("activar"),
					silenciar = silenciar, silenciarDentro = silenciarDentro, levantar = r.optBoolean("levantar", false),
					actividad = texto("actividad"), ventana = texto("ventana"),
				)
			}
		}.orEmpty()
		val minima = m.optInt("apiMinima", 1).coerceAtLeast(1)
		// Uno que pide una API más nueva que la de esta versión de Tiresias no se instala ni se carga (fallaría a medias)
		if (minima > VERSION_API) throw QuickJsException(Idioma.envolver(app).getString(com.xebolax.tiresias.R.string.scripts_api_nueva, local(m, "nombre", id)))
		return Complemento(id, local(m, "nombre", id), m.optString("version", "1.0"), lista("apps"), lista("permisos"), local(m, "descripcion"), reglas, lista("actividades"), ajustes(m),
			minima, m.optInt("apiProbada", minima).coerceAtLeast(minima))
	}

	private object HostVacio : QuickJs.Host {
		override fun call(method: String, json: String?): String? = null
		override fun loadModule(name: String): ByteArray? = null
		override fun onUnhandledError(error: QuickJsException) = Unit
	}

	companion object {
		private const val ETIQUETA = "Complementos"

		/**
		 * La versión de la API de los complementos: 1 hasta la 1.0.3; 2 desde la
		 * 1.0.4 (`llamada`, `alVentana`, `elegir`, `confirmar`, `pedirTexto`, `luz`
		 * e `infoApp`). Un complemento declara en su manifiesto `apiMinima` y
		 * `apiProbada`; lo ve en `tiresias.versionApi`. Se sube solo al añadir o
		 * cambiar algo de la API, nunca se quita nada sin subirla.
		 */
		const val VERSION_API = 2
		private const val ARCHIVO = "complemento.js"
		private const val CLAVE_ACTIVOS = "__activos"
		private const val LIMITE_MEMORIA = 16L * 1024 * 1024
		private const val LIMITE_PILA = 512L * 1024
		private const val LIMITE_CARGA_MS = 500L
		private const val LIMITE_LLAMADA_MS = 30L
		private const val FALLOS_PARA_APAGAR = 3
		private const val LIMITE_DATOS = 64 * 1024
		private const val MAXIMO_TEMPORIZADORES = 30

		/** La API `tiresias` que ve cada complemento (y los puntos de entrada que llama Tiresias). */
		private val PRELUDIO = """
			const __m = { enfocar: [], notificar: [], ordenes: {}, acciones: [], pendientes: [], teclas: [], bateria: [], ventana: [], respuestas: {}, temporizadores: {}, siguiente: 1 };
			const __esperar = (f) => { const n = __m.siguiente++; __m.respuestas[n] = typeof f === "function" ? f : () => {}; return n; };
			const __temporizar = (ms, f, repetir) => { const n = __m.siguiente++; __m.temporizadores[n] = { f, repetir }; __host("temporizador", JSON.stringify([n, Number(ms) || 0, repetir])); return n; };
			globalThis.tiresias = {
				versionApi: $VERSION_API,
				decir: (t, cola) => { __host(cola === true ? "decirEnCola" : "decir", JSON.stringify(String(t))); },
				sonido: (n) => { __host("sonido", JSON.stringify(String(n))); },
				portapapeles: (t, sensible) => { __host(sensible === true ? "portapapelesSensible" : "portapapeles", JSON.stringify(String(t))); },
				vaciarPortapapeles: (t) => __host("vaciarPortapapeles", JSON.stringify(String(t))) === "true",
				ajuste: (k) => JSON.parse(__host("ajuste", JSON.stringify(String(k))) || "null"),
				idioma: () => JSON.parse(__host("idioma", null) || '"es"'),
				ahora: () => JSON.parse(__host("ahora", null) || "null"),
				bateria: () => JSON.parse(__host("bateria", null) || "null"),
				pantalla: () => JSON.parse(__host("pantalla", null) || '{"encendida":true,"bloqueada":false}'),
				elemento: () => JSON.parse(__host("elemento", null) || "null"),
				alBateria: (f) => { __m.bateria.push(f); },
				abrir: (u) => __host("abrir", JSON.stringify(String(u))) === "true",
				apps: () => JSON.parse(__host("apps", null) || "[]"),
				abrirApp: (p) => __host("abrirApp", JSON.stringify(String(p))) === "true",
				despues: (ms, f) => __temporizar(ms, f, false),
				cada: (ms, f) => __temporizar(ms, f, true),
				cancelar: (n) => { if (__m.temporizadores[n]) { delete __m.temporizadores[n]; __host("cancelarTemporizador", JSON.stringify(Number(n))); } },
				app: () => JSON.parse(__host("app", null) || '""'),
				registro: (t) => { __host("registro", JSON.stringify(String(t))); },
				alEnfocar: (f) => { __m.enfocar.push(f); },
				alNotificar: (f) => { __m.notificar.push(f); },
				orden: (nombre, f) => { __m.ordenes[String(nombre)] = f; },
				alAcciones: (f) => { __m.acciones.push(f); },
				braille: (t) => { __host("braille", JSON.stringify(String(t))); },
				alTeclaBraille: (f) => { __m.teclas.push(f); },
				actividad: () => JSON.parse(__host("actividad", null) || '""'),
				guardar: (k, v) => { __host("guardar", JSON.stringify([String(k), String(v)])); },
				leer: (k) => JSON.parse(__host("leer", JSON.stringify(String(k))) || "null"),
				borrarDato: (k) => { __host("borrarDato", JSON.stringify(String(k))); },
				llamada: () => JSON.parse(__host("llamada", null) || '""'),
				infoApp: (p) => JSON.parse(__host("infoApp", JSON.stringify(p == null ? "" : String(p))) || "null"),
				luz: (f) => { __host("luz", JSON.stringify(__esperar(f))); },
				elegir: (titulo, opciones, f) => { __host("elegir", JSON.stringify([__esperar(f), String(titulo), (Array.isArray(opciones) ? opciones : []).map(String)])); },
				confirmar: (texto, f) => { __host("confirmar", JSON.stringify([__esperar(f), String(texto)])); },
				pedirTexto: (titulo, pista, f) => { __host("pedirTexto", JSON.stringify([__esperar(f), String(titulo), pista == null ? "" : String(pista)])); },
				alVentana: (f) => { __m.ventana.push(f); },
			};
			globalThis.__enfocar = (j) => { const e = JSON.parse(j); for (const f of __m.enfocar) { const r = f(e); if (typeof r === "string") return r; } return null; };
			globalThis.__notificar = (j) => { const n = JSON.parse(j); for (const f of __m.notificar) { const r = f(n); if (typeof r === "string") return r; } return null; };
			globalThis.__ordenes = () => Object.keys(__m.ordenes);
			globalThis.__orden = (j) => { const f = __m.ordenes[JSON.parse(j)]; if (f) f(); return null; };
			globalThis.__acciones = (j) => { const e = JSON.parse(j); __m.pendientes = []; const nombres = []; for (const f of __m.acciones) { const r = f(e); if (Array.isArray(r)) for (const a of r) { if (a && typeof a.nombre === "string" && typeof a.hacer === "function") { __m.pendientes.push(a.hacer); nombres.push(a.nombre); } } } return nombres; };
			globalThis.__accion = (j) => { const f = __m.pendientes[JSON.parse(j)]; if (f) f(); return null; };
			globalThis.__teclaBraille = (j) => { const t = JSON.parse(j); for (const f of __m.teclas) { if (f(t) === true) return "si"; } return null; };
			globalThis.__tieneBateria = () => __m.bateria.length > 0 ? "si" : null;
			globalThis.__tieneVentana = () => __m.ventana.length > 0 ? "si" : null;
			globalThis.__ventana = (j) => { const v = JSON.parse(j); for (const f of __m.ventana) f(v); return null; };
			globalThis.__respuesta = (j) => { const r = JSON.parse(j); const f = __m.respuestas[r[0]]; delete __m.respuestas[r[0]]; if (f) f(r[1]); return null; };
			globalThis.__bateria = (j) => { const b = JSON.parse(j); for (const f of __m.bateria) f(b); return null; };
			globalThis.__temporizador = (j) => { const n = JSON.parse(j); const t = __m.temporizadores[n]; if (!t) return null; if (!t.repetir) delete __m.temporizadores[n]; t.f(); return null; };
		""".trimIndent()
	}
}
