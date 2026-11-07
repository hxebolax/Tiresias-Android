package com.xebolax.tiresias.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.TextView
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.VocesPorIdioma
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.MotorSistema
import java.util.Locale

/**
 * Las voces de los motores del sistema, como en @Voice: se elige el motor
 * (Google, Samsung, RHVoice, Vocalizer...), el idioma y la voz concreta de
 * ese idioma, y se puede escuchar una muestra de cada una. Se guarda una voz
 * por idioma ([VocesPorIdioma]): la del español para el español, la del
 * inglés para el inglés, y Tiresias usa la de cada uno al cambiar de idioma.
 *
 * Elegir una voz de un motor que no es el que usa Tiresias pasa Tiresias a
 * ese motor (con la velocidad y el tono que tuviera en él).
 */
class ActividadVocesSistema : ActividadBase() {
	private lateinit var p: Pantalla
	private var instalados: List<Pair<String, String>> = emptyList()
	/** El paquete del motor que se está mirando. */
	private var motor = ""
	private var tts: TextToSpeech? = null
	private var voces: List<Voice> = emptyList()
	/** El idioma por el que se filtran las voces (dos o tres letras), o null para todos. */
	private var idioma: String? = null
	private var cargando = false
	/** Si el motor arrancó (puede arrancar y no dar la lista de sus voces: los antiguos). */
	private var arranco = false
	private lateinit var aviso: TextView

	private val localeApp: Locale get() = Idioma.efectivo(this)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.voces_titulo))
		instalados = MotorSistema.instalados(this)
		val a = Ajustes(this)
		motor = estado?.getString(MOTOR) ?: motorDeTiresias(a) ?: instalados.firstOrNull()?.first.orEmpty()
		idioma = if (estado?.containsKey(IDIOMA) == true) estado.getString(IDIOMA) else localeApp.language
		if (motor.isEmpty()) {
			p.parrafo(getString(R.string.voces_sin_motores))
			return
		}
		cargar()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(MOTOR, motor)
		outState.putString(IDIOMA, idioma)
	}

	override fun onDestroy() {
		cerrarMotor()
		super.onDestroy()
	}

	private fun cerrarMotor() {
		runCatching { tts?.stop(); tts?.shutdown() }.onFailure { Registro.w(ETIQUETA, "No se pudo cerrar el motor", it) }
		tts = null
	}

	/** El motor del sistema que usa Tiresias ahora (el preferido del móvil con «La que use el sistema»), o null. */
	private fun motorDeTiresias(a: Ajustes): String? {
		val m = a.motor
		if (m == Ajustes.MOTOR_DEL_SISTEMA) return MotorSistema.predeterminado(this)
		return m.takeIf { id -> instalados.any { it.first == id } }
	}

	private fun nombreMotor(id: String): String = instalados.firstOrNull { it.first == id }?.second ?: id

	/** Arranca el motor para leer sus voces (cada motor tarda lo suyo: Google, un par de segundos la primera vez). */
	private fun cargar() {
		cerrarMotor()
		voces = emptyList()
		cargando = true
		arranco = false
		construir()
		val elegido = motor
		var nuevo: TextToSpeech? = null
		nuevo = TextToSpeech(applicationContext, { estado ->
			runOnUiThread {
				if (isDestroyed || motor != elegido || tts !== nuevo) return@runOnUiThread
				cargando = false
				arranco = estado == TextToSpeech.SUCCESS
				if (arranco) {
					voces = runCatching { nuevo?.voices?.toList().orEmpty() }
						.onFailure { Registro.w(ETIQUETA, "El motor no dio sus voces", it) }.getOrDefault(emptyList())
						.sortedWith(compareBy<Voice>({ sinDescargar(it) }, { it.locale.getDisplayName(localeApp) }, { it.name }))
				} else Registro.w(ETIQUETA, "El motor $elegido no arrancó ($estado)")
				construir()
				avisar(if (estado == TextToSpeech.SUCCESS) resources.getQuantityString(R.plurals.voces_cuantas, visibles().size, visibles().size)
					else getString(R.string.voces_no_arranca, nombreMotor(elegido)))
			}
		}, elegido)
		tts = nuevo
	}

	private fun visibles(): List<Voice> = idioma?.let { i -> voces.filter { it.locale.language == i } } ?: voces

	private fun nombreIdioma(codigo: String): String = Locale(codigo).getDisplayLanguage(localeApp)

	private fun construir() {
		p.vaciar()
		val a = Ajustes(this)
		p.botonSecundario(getString(R.string.voces_motor, nombreMotor(motor))) { elegirMotor() }
		p.botonSecundario(getString(R.string.voces_idioma, idioma?.let { nombreIdioma(it) } ?: getString(R.string.voces_todos_idiomas))) { elegirIdioma() }
		aviso = p.estadoVivo()
		if (cargando) {
			aviso.text = getString(R.string.voces_cargando, nombreMotor(motor))
			return
		}
		if (voces.isEmpty()) {
			aviso.text = getString(if (arranco) R.string.voces_sin_lista else R.string.voces_no_arranca, nombreMotor(motor))
			p.botonSecundario(getString(R.string.voces_instalar)) { instalarVoces() }
			return
		}
		val deTiresias = motorDeTiresias(a)
		if (deTiresias != motor) p.parrafo(getString(R.string.voces_otro_motor, nombreMotor(motor)))
		val guardado = if (deTiresias == motor) a.vozSistema else ""
		idioma?.let { i ->
			val enUso = VocesPorIdioma.para(guardado, i) { n -> voces.any { it.name == n && it.locale.language == i } }
			p.parrafo(getString(R.string.voces_en_uso, nombreIdioma(i), enUso?.let { legible(it) } ?: getString(R.string.voces_la_del_motor)))
		}
		val lista = visibles()
		aviso.text = resources.getQuantityString(R.plurals.voces_cuantas, lista.size, lista.size)
		for (v in lista) {
			val elegida = VocesPorIdioma.para(guardado, v.locale.language) { false } == v.name
			p.botonSecundario(textoVoz(v, elegida)) { opciones(v) }
		}
		idioma?.let { i -> p.botonSecundario(getString(R.string.voces_usar_del_motor, nombreIdioma(i))) { quitar(i) } }
		p.botonSecundario(getString(R.string.voces_instalar)) { instalarVoces() }
	}

	/** Si la voz aún no está en el móvil (se descarga al elegirla, si el motor lo hace): van al final de la lista. */
	private fun sinDescargar(v: Voice): Boolean = v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true

	/** El nombre de la voz sin el idioma delante, que ya se dice aparte («es-es-x-eea-local» → «eea, local»). */
	private fun legible(nombre: String): String {
		// La de Google que no es una voz concreta sino la predeterminada del idioma («es-ES-language»)
		if (nombre.endsWith("-language", ignoreCase = true)) return getString(R.string.voces_del_idioma)
		val sinIdioma = nombre.replace(Regex("^[a-zA-Z]{2,3}[-_][a-zA-Z0-9]{2,4}[-_](x[-_])?"), "")
		return sinIdioma.ifBlank { nombre }.replace('-', ' ').replace('_', ' ').trim()
	}

	/** Una fila: idioma y país, nombre, calidad, si necesita internet o está sin descargar, y si es la elegida. */
	private fun textoVoz(v: Voice, elegida: Boolean): String {
		val partes = mutableListOf(v.locale.getDisplayName(localeApp), legible(v.name))
		partes += getString(when {
			v.quality >= Voice.QUALITY_VERY_HIGH -> R.string.voces_calidad_muy_alta
			v.quality >= Voice.QUALITY_HIGH -> R.string.voces_calidad_alta
			v.quality >= Voice.QUALITY_NORMAL -> R.string.voces_calidad_normal
			v.quality >= Voice.QUALITY_LOW -> R.string.voces_calidad_baja
			else -> R.string.voces_calidad_muy_baja
		})
		partes += getString(if (v.isNetworkConnectionRequired) R.string.voces_internet else R.string.voces_sin_conexion)
		if (sinDescargar(v)) partes += getString(R.string.voces_sin_descargar)
		if (elegida) partes.add(0, getString(R.string.voces_elegida))
		return partes.joinToString(", ")
	}

	private fun elegirMotor() {
		if (instalados.isEmpty()) return
		elegirUno(getString(R.string.voces_elegir_motor), instalados.map { it.second }, instalados.indexOfFirst { it.first == motor }) { i ->
			if (instalados[i].first == motor) return@elegirUno
			motor = instalados[i].first
			cargar()
		}
	}

	private fun elegirIdioma() {
		val codigos = voces.map { it.locale.language }.distinct().sortedBy { nombreIdioma(it) }
		val nombres = listOf(getString(R.string.voces_todos_idiomas)) + codigos.map { nombreIdioma(it) }
		val actual = idioma?.let { codigos.indexOf(it) + 1 } ?: 0
		elegirUno(getString(R.string.voces_elegir_idioma), nombres, actual) { i ->
			idioma = if (i == 0) null else codigos[i - 1]
			construir()
			val n = visibles().size
			avisar(resources.getQuantityString(R.plurals.voces_cuantas, n, n))
		}
	}

	private fun opciones(v: Voice) {
		val i = nombreIdioma(v.locale.language)
		elegir(legible(v.name), listOf(
			getString(R.string.voces_usar, i) to { usar(v) },
			getString(R.string.voces_escuchar) to { muestra(v) },
		))
	}

	/** Se lee con la voz [v] una frase corta en su idioma (con el motor de esta pantalla, no con Tiresias). */
	private fun muestra(v: Voice) {
		val t = tts ?: return
		val frase = if (v.locale.language == localeApp.language) getString(R.string.voces_frase_muestra)
			else "${v.locale.getDisplayName(v.locale)}. 1, 2, 3."
		// Después de que Tiresias diga dónde vuelve el foco al cerrarse la lista de opciones
		aviso.postDelayed({
			if (isDestroyed) return@postDelayed
			ServicioTiresias.instancia?.lectorActual?.voz?.callar()
			runCatching {
				t.voice = v
				t.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "muestra")
			}.onFailure { Registro.w(ETIQUETA, "No se pudo leer la muestra", it) }
		}, ESPERA_MUESTRA_MS)
	}

	/** Usa [v] para su idioma. Si es de otro motor, Tiresias pasa a ese motor. */
	private fun usar(v: Voice) {
		val a = Ajustes(this)
		if (motorDeTiresias(a) != motor) {
			a.motor = motor
			// Guarda la velocidad, el tono y las voces del motor de antes y pone las de este
			a.ajustarVozAlMotor()
		}
		a.vozSistema = VocesPorIdioma.poner(a.vozSistema, v.locale.language, v.name)
		ServicioTiresias.instancia?.recargarAjustes()
		construir()
		avisar(getString(R.string.voces_elegida_aviso, nombreIdioma(v.locale.language), legible(v.name)))
	}

	/** Vuelve a la voz que el motor tenga para [codigo]. */
	private fun quitar(codigo: String) {
		val a = Ajustes(this)
		if (motorDeTiresias(a) == motor) {
			a.vozSistema = VocesPorIdioma.poner(a.vozSistema, codigo, null)
			ServicioTiresias.instancia?.recargarAjustes()
		}
		construir()
		avisar(getString(R.string.voces_quitada_aviso, nombreIdioma(codigo)))
	}

	/** La pantalla del motor para descargar voces (si la tiene); si no, sus ajustes en Android. */
	private fun instalarVoces() {
		val pedir = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(motor)
		val ajustes = Intent("com.android.settings.TTS_SETTINGS")
		runCatching { startActivity(pedir) }
			.recoverCatching { startActivity(ajustes) }
			.onFailure {
				Registro.w(ETIQUETA, "No se pudo abrir la instalación de voces", it)
				avisar(getString(R.string.voces_no_instalar))
			}
	}

	private companion object {
		const val ETIQUETA = "ActividadVocesSistema"
		const val MOTOR = "motor"
		const val IDIOMA = "idioma"
		const val ESPERA_MUESTRA_MS = 900L
	}
}
