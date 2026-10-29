package com.xebolax.tiresias.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.configurarBarra
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.copiar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirVarios
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.ia.AsistenteIA
import com.xebolax.tiresias.ia.Secretos
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.MotorSistema

/**
 * Ajustes de Tiresias, por secciones (voz, lectura, navegación, teclado,
 * notificaciones, IA, actualizaciones...). Cada sección es su propio archivo
 * `res/xml/ajustes_*.xml` sobre las mismas preferencias que lee el lector, en
 * el almacenamiento protegido. Al cambiar algo, el lector lo aplica al momento.
 */
class ActividadAjustes : ActividadBase(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
	private lateinit var barra: MaterialToolbar

	/** Un archivo BRF elegido: a la línea braille (se lee como braille ASCII, un byte por celda). */
	val elegirBrf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		uri ?: return@registerForActivityResult
		lifecycleScope.launch { abrirBrf(uri) }
	}

	private suspend fun abrirBrf(uri: android.net.Uri) {
		val texto = try {
			kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
				contentResolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size <= 4 * 1024 * 1024 }?.toString(Charsets.ISO_8859_1)
			}
		} catch (e: kotlinx.coroutines.CancellationException) {
			throw e
		} catch (e: Exception) {
			com.xebolax.tiresias.base.Registro.w("Ajustes", "No se pudo leer el archivo BRF", e)
			null
		}
		if (texto == null) {
			avisar(getString(R.string.aj_braille_brf_mal))
			return
		}
		val abierto = ServicioTiresias.instancia?.abrirBrf(texto) == true
		avisar(getString(if (abierto) R.string.aj_braille_brf_abierto else R.string.aj_braille_brf_sin_linea))
	}

	val pedirMicrofono = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
		avisar(getString(if (concedido) R.string.aj_microfono_si else R.string.aj_microfono_no))
	}

	/** Copia de seguridad de toda la configuración (CopiaSeguridad), a un archivo que elige el usuario. */
	val guardarCopia = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		uri ?: return@registerForActivityResult
		lifecycleScope.launch {
			try {
				kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
					contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(com.xebolax.tiresias.base.CopiaSeguridad.exportar(this@ActividadAjustes)) }
				}
				avisar(getString(R.string.aj_copia_guardada))
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Exception) {
				com.xebolax.tiresias.base.Registro.e("Ajustes", "No se pudo guardar la copia de seguridad", e)
				avisar(getString(R.string.error_exportar))
			}
		}
	}

	val recuperarCopia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		uri ?: return@registerForActivityResult
		confirmar(getString(R.string.aj_copia_confirmar), getString(R.string.aj_copia_recuperar)) { lifecycleScope.launch {
			try {
				val bien = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
					val texto = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
					com.xebolax.tiresias.base.CopiaSeguridad.importar(this@ActividadAjustes, texto)
				}
				if (bien) {
					ServicioTiresias.instancia?.recargarAjustes()
					ServicioTiresias.instancia?.recargarComplementos()
					avisar(getString(R.string.aj_copia_recuperada))
					recreate()
				} else {
					avisar(getString(R.string.aj_copia_no_valida))
				}
			} catch (e: kotlinx.coroutines.CancellationException) {
				throw e
			} catch (e: Exception) {
				com.xebolax.tiresias.base.Registro.e("Ajustes", "No se pudo recuperar la copia de seguridad", e)
				avisar(getString(R.string.error_importar))
			}
		} }
	}

	val pedirBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
		avisar(getString(if (concedido) R.string.aj_braille_bluetooth_si else R.string.aj_braille_bluetooth_no))
		if (concedido) ServicioTiresias.instancia?.buscarLineaBraille()
	}

	val pedirLlamadas = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
		avisar(getString(if (concedido) R.string.aj_llamadas_si else R.string.aj_llamadas_no))
	}

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		enableEdgeToEdge()
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		barra = MaterialToolbar(this)
		raiz.addView(barra)
		val marco = FrameLayout(this).apply { id = R.id.ajustes_contenido }
		raiz.addView(marco, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
		// Volver (arriba a la izquierda): de una sección a Ajustes y de Ajustes a Inicio; el menú lateral solo está en Inicio
		setContentView(raiz)
		configurarBarra(barra, getString(R.string.ui_ajustes), conArriba = true)
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
			v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
			WindowInsetsCompat.CONSUMED
		}
		if (estado == null) {
			supportFragmentManager.beginTransaction().replace(R.id.ajustes_contenido, Seccion.nueva(R.xml.ajustes)).commit()
			// Abrir una sección directamente (desde otra pantalla o en las pruebas): «seccion» = la clave
			intent.getStringExtra(EXTRA_SECCION)?.let { clave -> xmlDe(clave)?.let { abrirSeccion(clave, it, tituloDe(clave)) } }
		}
		protegerConCodigo()
		supportFragmentManager.addOnBackStackChangedListener {
			title = (supportFragmentManager.findFragmentById(R.id.ajustes_contenido) as? Seccion)?.preferenceScreen?.title ?: getString(R.string.ui_ajustes)
			barra.title = title
		}
	}

	override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
		val xml = xmlDe(pref.key) ?: return false
		abrirSeccion(pref.key, xml, pref.title)
		return true
	}

	private fun tituloDe(clave: String): CharSequence? {
		val id = resources.getIdentifier("aj_$clave", "string", packageName)
		return if (id != 0) getString(id) else null
	}

	private fun abrirSeccion(clave: String, xml: Int, titulo: CharSequence?) {
		supportFragmentManager.beginTransaction()
			.replace(R.id.ajustes_contenido, Seccion.nueva(xml))
			.addToBackStack(clave)
			.commit()
		// El título de la sección es lo que el lector dice al entrar
		barra.title = titulo
		title = titulo
	}

	private fun xmlDe(clave: String?): Int? {
		return when (clave) {
			"seccion_voz" -> R.xml.ajustes_voz
			"seccion_lectura" -> R.xml.ajustes_lectura
			"seccion_navegacion" -> R.xml.ajustes_navegacion
			"seccion_teclado" -> R.xml.ajustes_teclado
			"seccion_braille" -> R.xml.ajustes_braille
			"seccion_notificaciones" -> R.xml.ajustes_notificaciones
			"seccion_ia" -> R.xml.ajustes_ia
			"seccion_actualizaciones" -> R.xml.ajustes_actualizaciones
			else -> null
		}
	}

	companion object {
		const val EXTRA_SECCION = "seccion"
	}

	class Seccion : PreferenceFragmentCompat() {
		override fun onCreatePreferences(estado: Bundle?, clave: String?) {
			preferenceManager.setStorageDeviceProtected()
			preferenceManager.sharedPreferencesName = Ajustes.ARCHIVO
			setPreferencesFromResource(requireArguments().getInt(XML), clave)
			preparar()
		}

		override fun onResume() {
			super.onResume()
			refrescarVoz()
			refrescarTemas()
			findPreference<Preference>("oyente_avisos")?.summary = getString(
				if (com.xebolax.tiresias.servicio.OyenteAvisos.tieneAcceso(requireContext())) R.string.aj_oyente_avisos_si else R.string.aj_oyente_avisos_no,
			)
			preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(cambio)
		}

		override fun onPause() {
			preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(cambio)
			super.onPause()
		}

		/** Cualquier cambio se aplica al lector al momento. */
		private val cambio = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
			// Otro motor: con su velocidad, su tono y su volumen (o los normales la primera vez)
			if (clave == Claves.MOTOR) Ajustes(requireContext()).ajustarVozAlMotor()
			// Lo que cambian los gestos con la pantalla abierta, o el motor, se ve en los deslizadores
			if (clave in DE_LA_VOZ) refrescarVoz()
			ServicioTiresias.instancia?.recargarAjustes()
			if (clave == Claves.TEMA_SONIDOS) ServicioTiresias.instancia?.recargarSonidos()
		}

		/** Los deslizadores de la voz con lo que hay guardado (cambia con los gestos y con el motor). */
		private val DE_LA_VOZ = listOf(Claves.VELOCIDAD, Claves.TONO, Claves.VOLUMEN_VOZ)

		private fun refrescarVoz() {
			val prefs = preferenceManager.sharedPreferences ?: return
			for (clave in DE_LA_VOZ) {
				val d = findPreference<com.xebolax.tiresias.accesibilidad.DeslizadorAjuste>(clave) ?: continue
				val v = runCatching { prefs.getInt(clave, d.value) }.getOrElse { d.value }
				if (v != d.value) d.value = v
			}
		}

		/** El tema de sonidos: los de Tiresias, los incluidos y los instalados (cambian en «Sonidos y vibraciones uno a uno»). */
		private fun refrescarTemas() {
			val lista = findPreference<ListPreference>(Claves.TEMA_SONIDOS) ?: return
			val c = requireContext()
			val temas = listOf("") + com.xebolax.tiresias.voz.TemasSonido.instalados(c)
			lista.entries = temas.map { if (it.isEmpty()) getString(R.string.temas_tiresias) else com.xebolax.tiresias.voz.TemasSonido.nombreDe(c, it) }.toTypedArray()
			lista.entryValues = temas.toTypedArray()
			val actual = Ajustes(c).temaSonidos
			lista.value = if (actual in temas) actual else ""
		}

		private fun preparar() {
			val c = requireContext()
			findPreference<ListPreference>(Claves.IDIOMA)?.setOnPreferenceChangeListener { _, valor ->
				Idioma.cambiar(c, valor as String)
				ServicioTiresias.instancia?.recargarAjustes()
				activity?.recreate()
				true
			}
			// Las voces (archivos y apps instaladas) solo se buscan en la sección que las usa
			val motores = if (findPreference<Preference>(Claves.MOTOR) != null || findPreference<Preference>("voces_control") != null)
				com.xebolax.tiresias.voz.VocesDisponibles.disponibles(c) else emptyList()
			findPreference<ListPreference>(Claves.MOTOR)?.let { lista ->
				lista.entries = motores.map { it.second }.toTypedArray()
				lista.entryValues = motores.map { it.first }.toTypedArray()
				// Sin valor o con una voz que ya no está (desinstalada): la de fábrica, para que se vea cuál suena
				if (lista.value == null || lista.findIndexOfValue(lista.value) < 0) lista.value = Ajustes.MOTOR_ESPEAK
			}
			// La voz de las notificaciones: la de siempre o una de las incluidas
			findPreference<ListPreference>(Claves.MOTOR_AVISOS)?.let { lista ->
				val incluidas = motores.filter { it.first == Ajustes.MOTOR_ESPEAK || it.first == Ajustes.MOTOR_PICO || it.first == Ajustes.MOTOR_ELOQUENCE }
				lista.entries = (listOf(getString(R.string.aj_motor_avisos_misma)) + incluidas.map { it.second }).toTypedArray()
				lista.entryValues = (listOf("") + incluidas.map { it.first }).toTypedArray()
				if (lista.value == null || lista.findIndexOfValue(lista.value) < 0) lista.value = ""
			}
			// El oyente de notificaciones: dice si tiene el acceso y lleva a darlo
			findPreference<Preference>("oyente_avisos")?.setOnPreferenceClickListener {
				val intent = if (android.os.Build.VERSION.SDK_INT >= 30) android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
					.putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, android.content.ComponentName(c, com.xebolax.tiresias.servicio.OyenteAvisos::class.java).flattenToString())
				else android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
				runCatching { startActivity(intent) }.recoverCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
					.onFailure { com.xebolax.tiresias.base.Registro.w("ActividadAjustes", "No se pudo abrir el acceso a las notificaciones", it) }
				true
			}
			// Los teclados que hablan solos: se eligen de los activados en el móvil
			findPreference<Preference>("teclados_que_hablan")?.setOnPreferenceClickListener {
				val imm = c.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
				val teclados = imm?.enabledInputMethodList.orEmpty().map { it.packageName to it.loadLabel(c.packageManager).toString() }.distinctBy { it.first }
				val a = Ajustes(c)
				val marcados = a.tecladosQueHablan
				requireContext().elegirVarios(getString(R.string.aj_teclados_que_hablan), teclados.map { it.second }, BooleanArray(teclados.size) { teclados[it].first in marcados }) { nuevos ->
					a.tecladosQueHablan = teclados.filterIndexed { i, _ -> nuevos[i] }.map { it.first }.toSet()
				}
				true
			}
			findPreference<Preference>("voces_control")?.apply {
				val a = Ajustes(c)
				fun resumen() {
					val elegidas = a.vocesControl
					summary = if (elegidas.isEmpty()) getString(R.string.aj_voces_control_todas)
					else motores.filter { it.first in elegidas }.joinToString(", ") { it.second }
				}
				resumen()
				setOnPreferenceClickListener {
					val elegidas = a.vocesControl
					val marcadas = BooleanArray(motores.size) { elegidas.isEmpty() || motores[it].first in elegidas }
					requireContext().elegirVarios(getString(R.string.aj_voces_control), motores.map { it.second }, marcadas) { nuevas ->
						// Todas o ninguna marcada: todas (también las que se instalen después)
						val ids = motores.filterIndexed { i, _ -> nuevas[i] }.map { it.first }.toSet()
						a.vocesControl = if (ids.size == motores.size) emptySet() else ids
						resumen()
					}
					true
				}
			}
			// Los emojis: sin elegir, lo que da el interruptor antiguo (no el valor de fábrica, que lo pisaría)
			findPreference<ListPreference>(Claves.MODO_EMOJIS)?.let { if (it.value == null) it.value = Ajustes(c).modoEmojis.name }
			// La salida del audio: lo elegido y, detrás, la explicación
			findPreference<ListPreference>(Claves.SALIDA_AUDIO)?.summaryProvider = Preference.SummaryProvider<ListPreference> { p ->
				listOfNotNull(p.entry, getString(R.string.aj_salida_audio_res)).joinToString(". ")
			}
			// Sin sensor de proximidad (algunas tabletas), el ajuste no haría nada
			findPreference<Preference>(Claves.CALLAR_PROXIMIDAD)?.isVisible =
				c.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) != null
			findPreference<Preference>("modo_senior")?.setOnPreferenceClickListener {
				startActivity(android.content.Intent(c, ActividadModoSenior::class.java))
				true
			}
			findPreference<Preference>("eloquence_ajustes")?.apply {
				isVisible = com.xebolax.tiresias.voz.BibliotecaEloquence.disponible(c)
				setOnPreferenceClickListener {
					startActivity(android.content.Intent(c, ActividadEloquence::class.java))
					true
				}
			}
			findPreference<Preference>("probar_voz")?.setOnPreferenceClickListener {
				val s = ServicioTiresias.instancia
				if (s != null) s.probarVoz(getString(R.string.ui_frase_prueba)) else activity?.avisar(getString(R.string.aj_probar_sin_servicio))
				true
			}
			findPreference<Preference>("microfono")?.setOnPreferenceClickListener {
				val a = activity as? ActividadAjustes ?: return@setOnPreferenceClickListener true
				if (c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) a.avisar(getString(R.string.aj_microfono_si))
				else a.pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
				true
			}
			findPreference<Preference>("llamadas")?.apply {
				isVisible = android.os.Build.VERSION.SDK_INT >= 26
				setOnPreferenceClickListener {
					val a = activity as? ActividadAjustes ?: return@setOnPreferenceClickListener true
					if (android.os.Build.VERSION.SDK_INT >= 26) {
						if (c.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) a.avisar(getString(R.string.aj_llamadas_si))
						else a.pedirLlamadas.launch(Manifest.permission.ANSWER_PHONE_CALLS)
					}
					true
				}
			}
			// Las tablas informáticas de 8 puntos, con el nombre de su idioma (en el idioma de Tiresias)
			findPreference<androidx.preference.ListPreference>("braille_tabla_informatica")?.apply {
				val tablas = com.xebolax.tiresias.braille.Braille.TABLAS_INFORMATICAS
				val local = resources.configuration.locales[0]
				entries = (listOf(getString(R.string.aj_braille_tabla_auto)) + tablas.map { (archivo, idioma) ->
					getString(R.string.aj_braille_tabla_fila, java.util.Locale(idioma).getDisplayLanguage(local).replaceFirstChar { it.titlecase(local) }, archivo)
				}).toTypedArray()
				entryValues = (listOf("") + tablas.map { it.first }).toTypedArray()
			}
			findPreference<Preference>("personas")?.setOnPreferenceClickListener {
				startActivity(android.content.Intent(c, ActividadPersonas::class.java))
				true
			}
			// Un archivo BRF, a la línea braille
			findPreference<Preference>("braille_brf")?.setOnPreferenceClickListener {
				(activity as? ActividadAjustes)?.elegirBrf?.launch(arrayOf("*/*"))
				true
			}
			// Altavoz al apartar el móvil de la oreja: sin el permiso (adb), se explica y, aparte, se copia la orden que lo da
			val sinPermisoAltavoz = android.os.Build.VERSION.SDK_INT >= 31 && !com.xebolax.tiresias.servicio.PermisoAltavoz.puedeControlar(c)
			findPreference<Preference>("altavoz_al_apartar")?.apply {
				isVisible = android.os.Build.VERSION.SDK_INT >= 31
				if (sinPermisoAltavoz) summary = getString(R.string.aj_altavoz_sin_permiso)
			}
			findPreference<Preference>("altavoz_permiso")?.apply {
				isVisible = sinPermisoAltavoz
				val orden = com.xebolax.tiresias.servicio.PermisoAltavoz.ordenPermiso(c)
				summary = getString(R.string.aj_altavoz_permiso, orden)
				setOnPreferenceClickListener {
					c.copiar(orden)
					(activity as? ActividadAjustes)?.avisar(getString(R.string.aj_altavoz_copiado))
					true
				}
			}
			findPreference<Preference>("braille_acordes")?.setOnPreferenceClickListener {
				startActivity(android.content.Intent(c, ActividadTeclasBraille::class.java))
				true
			}
			findPreference<Preference>("sonidos_uno_a_uno")?.setOnPreferenceClickListener {
				startActivity(android.content.Intent(c, ActividadSonidos::class.java))
				true
			}
			for ((clave, lista) in listOf(
				"ordenar_menu" to ActividadOrdenar.LISTA_MENU, "ordenar_controles" to ActividadOrdenar.LISTA_CONTROLES,
				"ordenar_partes" to ActividadOrdenar.LISTA_PARTES, "ordenar_circular" to ActividadOrdenar.LISTA_CIRCULAR,
				"ordenar_estado" to ActividadOrdenar.LISTA_ESTADO,
			)) {
				findPreference<Preference>(clave)?.setOnPreferenceClickListener {
					startActivity(android.content.Intent(c, ActividadOrdenar::class.java).putExtra(ActividadOrdenar.EXTRA_LISTA, lista))
					true
				}
			}
			findPreference<Preference>("api_apps")?.setOnPreferenceClickListener {
				val a = Ajustes(c)
				val todas = (a.apiPermitidas + a.apiDenegadas).sorted()
				if (todas.isEmpty()) {
					activity?.avisar(getString(R.string.aj_api_ninguna))
					return@setOnPreferenceClickListener true
				}
				fun nombre(p: String) = runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
				c.elegir(getString(R.string.aj_api), todas.map { p ->
					val si = p in a.apiPermitidas
					getString(if (si) R.string.aj_api_si else R.string.aj_api_no, nombre(p)) to {
						a.permitirApi(p, !si)
						activity?.avisar(getString(if (si) R.string.api_denegada else R.string.api_permitida, nombre(p)))
					}
				})
				true
			}
			findPreference<Preference>("restablecer")?.setOnPreferenceClickListener {
				requireContext().confirmar(getString(R.string.aj_restablecer_pregunta), getString(R.string.aj_restablecer)) {
					com.xebolax.tiresias.base.Restablecer.todo(c)
					ServicioTiresias.instancia?.alRestablecer()
					startActivity(android.content.Intent(c, ActividadAsistente::class.java))
					activity?.finish()
				}
				true
			}
			findPreference<Preference>("copia_guardar")?.setOnPreferenceClickListener {
				(activity as? ActividadAjustes)?.guardarCopia?.launch("tiresias-configuracion.json")
				true
			}
			findPreference<Preference>("copia_recuperar")?.setOnPreferenceClickListener {
				(activity as? ActividadAjustes)?.recuperarCopia?.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
				true
			}
			findPreference<Preference>("braille_buscar")?.setOnPreferenceClickListener {
				val s = ServicioTiresias.instancia
				if (s != null) s.buscarLineaBraille() else activity?.avisar(getString(R.string.aj_probar_sin_servicio))
				true
			}
			findPreference<Preference>("braille_bluetooth")?.apply {
				isVisible = android.os.Build.VERSION.SDK_INT >= 35
				setOnPreferenceClickListener {
					val a = activity as? ActividadAjustes ?: return@setOnPreferenceClickListener true
					if (android.os.Build.VERSION.SDK_INT >= 31) {
						if (c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) a.avisar(getString(R.string.aj_braille_bluetooth_si))
						else a.pedirBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
					}
					true
				}
			}
			findPreference<Preference>("braille_teclado")?.setOnPreferenceClickListener {
				runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) }
				true
			}
			if (android.os.Build.VERSION.SDK_INT < 35) {
				findPreference<Preference>("braille_linea")?.apply { isEnabled = false; summary = getString(R.string.braille_linea_no_disponible) }
				findPreference<Preference>("braille_buscar")?.isVisible = false
			}
			// Claves de IA: se guardan cifradas (Secretos), nunca en las preferencias en claro
			val secretos = Secretos(c)
			for ((clave, secreto) in listOf("ia_clave_claude" to Secretos.CLAUDE, "ia_clave_openai" to Secretos.OPENAI, "ia_clave_gemini" to Secretos.GEMINI, "ia_clave_compatible" to Secretos.COMPATIBLE)) {
				findPreference<EditTextPreference>(clave)?.apply {
					isPersistent = false
					summary = getString(if (secretos.hay(secreto)) R.string.aj_clave_puesta else R.string.aj_clave_no)
					setOnBindEditTextListener { it.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
					setOnPreferenceChangeListener { p, valor ->
						secretos.guardar(secreto, valor as String)
						p.summary = getString(if (secretos.hay(secreto)) R.string.aj_clave_puesta else R.string.aj_clave_no)
						false
					}
				}
			}
			findPreference<ListPreference>(AsistenteIA.CLAVE_PROVEEDOR)?.let { if (it.value == null) it.value = "automatico" }
			// Sin valor guardado se ve el que vale de verdad, no «Sin establecer»
			findPreference<ListPreference>(Claves.TECLA_TIRESIAS)?.summaryProvider = Preference.SummaryProvider<ListPreference> { p ->
				p.entry ?: p.entries.getOrNull(p.findIndexOfValue(Ajustes(c).teclaTiresias))
			}
			for (clave in listOf("ia_modelo_claude", "ia_modelo_openai", "ia_modelo_gemini", "ia_modelo_compatible")) {
				findPreference<EditTextPreference>(clave)?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
					p.text?.takeIf { it.isNotBlank() } ?: getString(R.string.aj_modelo_recomendado)
				}
			}
			// Actualizaciones: la dirección solo con un servidor personalizado; GitHub solo si el repositorio está publicado
			val ajustes = Ajustes(c)
			val url = findPreference<EditTextPreference>(Claves.URL_ACTUALIZACIONES)?.also {
				if (it.text.isNullOrBlank()) it.text = BuildConfig.URL_ACTUALIZACIONES
				it.isVisible = ajustes.origenActualizaciones == Ajustes.ORIGEN_PERSONALIZADO
				it.setOnPreferenceChangeListener { _, valor ->
					val texto = (valor as String).trim()
					val valida = texto.startsWith("https://") || texto.startsWith("http://")
					if (!valida) activity?.avisar(getString(R.string.aj_servidor_no_valido))
					valida
				}
			}
			findPreference<ListPreference>(Claves.ORIGEN_ACTUALIZACIONES)?.let { lista ->
				if (!BuildConfig.GITHUB_ACTIVO) {
					lista.entries = arrayOf(getString(R.string.aj_origen_servidor), getString(R.string.aj_origen_personalizado))
					lista.entryValues = arrayOf(Ajustes.ORIGEN_SERVIDOR, Ajustes.ORIGEN_PERSONALIZADO)
				}
				// Un origen que no está en la lista (el automático, sin GitHub): el servidor de Tiresias
				lista.value = ajustes.origenActualizaciones.takeIf { lista.findIndexOfValue(it) >= 0 } ?: Ajustes.ORIGEN_SERVIDOR
				lista.setOnPreferenceChangeListener { _, valor ->
					url?.isVisible = valor == Ajustes.ORIGEN_PERSONALIZADO
					true
				}
			}
		}

		companion object {
			private const val XML = "xml"
			fun nueva(xml: Int) = Seccion().apply { arguments = Bundle().apply { putInt(XML, xml) } }
		}
	}
}
