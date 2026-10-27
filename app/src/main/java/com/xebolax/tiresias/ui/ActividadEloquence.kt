package com.xebolax.tiresias.ui

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.confirmar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.accesibilidad.elegir
import com.xebolax.tiresias.accesibilidad.elegirUno
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.servicio.ServicioTiresias
import com.xebolax.tiresias.voz.MotorEloquence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.evvdroid.Eci
import java.io.File

/**
 * Los ajustes de Eloquence: la voz y cada uno de sus parámetros (lo que cambies
 * se queda en esa voz), la variante de cada idioma, las pausas, la predicción de
 * frases, las abreviaturas, la frecuencia de muestreo y los diccionarios. Cada
 * botón dice el ajuste y su valor de ahora («Tamaño de la cabeza: 50 por
 * ciento») y cada interruptor es una sola parada. La velocidad y el tono son los
 * de la voz de Tiresias (Ajustes, Voz), como con las demás voces.
 */
class ActividadEloquence : ActividadBase() {
	private lateinit var p: Pantalla
	private lateinit var ajustes: Ajustes
	/** Lo que trae de fábrica la voz elegida (se lee del motor una vez por voz). */
	private var fabrica: Map<Int, Int> = emptyMap()
	private var fabricaDe = -1
	/** El volumen del diccionario al que se le está eligiendo un archivo. */
	private var eligiendoDiccionario = -1

	private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { ponerDiccionario(it) } }

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		ajustes = Ajustes(this)
		p = pantalla(getString(R.string.elo_titulo))
		construir()
	}

	private fun aplicar() {
		ServicioTiresias.instancia?.recargarAjustes()
		construir()
	}

	/** Lee lo de fábrica de la voz elegida (con el motor, fuera del hilo principal) y vuelve a construir. */
	private fun leerFabrica() {
		val voz = ajustes.eloquenceVoz
		if (fabricaDe == voz) return
		lifecycleScope.launch {
			val forma = try {
				withContext(Dispatchers.IO) { MotorEloquence.formaDeFabrica(this@ActividadEloquence, voz) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo leer la voz de fábrica de Eloquence", e)
				emptyMap()
			}
			fabrica = forma
			fabricaDe = voz
			construir()
		}
	}

	private fun nombreParametro(param: Int): String = getString(when (param) {
		Eci.VOICE_GENDER -> R.string.elo_genero
		Eci.VOICE_HEAD_SIZE -> R.string.elo_cabeza
		Eci.VOICE_PITCH_BASELINE -> R.string.elo_tono_base
		Eci.VOICE_PITCH_FLUCTUATION -> R.string.elo_entonacion
		Eci.VOICE_ROUGHNESS -> R.string.elo_aspereza
		Eci.VOICE_BREATHINESS -> R.string.elo_aliento
		else -> R.string.elo_volumen
	})

	private fun valorParametro(param: Int, valor: Int?): String = when {
		valor == null && fabrica.isEmpty() -> getString(R.string.elo_de_fabrica)
		valor == null -> getString(R.string.elo_de_fabrica)
		param == Eci.VOICE_GENDER -> getString(if (valor == 0) R.string.elo_hombre else R.string.elo_mujer)
		else -> getString(R.string.valor_por_ciento, valor)
	}

	private fun construir() {
		p.vaciar()
		p.parrafo(getString(R.string.elo_explicacion))
		if (ajustes.motor != Ajustes.MOTOR_ELOQUENCE) p.boton(getString(R.string.elo_usar)) {
			ajustes.motor = Ajustes.MOTOR_ELOQUENCE
			avisar(getString(R.string.elo_puesta))
			aplicar()
		}
		p.botonSecundario(getString(R.string.ui_probar_voz)) {
			val s = ServicioTiresias.instancia
			if (s != null) s.probarVoz(getString(R.string.ui_frase_prueba)) else avisar(getString(R.string.aj_probar_sin_servicio))
		}

		// La voz y sus parámetros
		p.encabezado(getString(R.string.elo_voz))
		val voz = ajustes.eloquenceVoz
		p.botonSecundario(getString(R.string.compaj_fila, getString(R.string.elo_voz), MotorEloquence.VOCES[voz])) {
			elegirUno(getString(R.string.elo_voz), MotorEloquence.VOCES, voz) { i ->
				ajustes.eloquenceVoz = i
				aplicar()
			}
		}
		leerFabrica()
		// La misma velocidad que en Ajustes, Voz, y la de los gestos: la de Eloquence (cada motor tiene la suya)
		val velocidades = (Ajustes.VELOCIDAD_MIN..Ajustes.VELOCIDAD_MAX step 10).toList()
		val actual = ajustes.velocidadDe(Ajustes.MOTOR_ELOQUENCE)
		p.botonSecundario(getString(R.string.compaj_fila, getString(R.string.elo_velocidad), getString(R.string.valor_por_ciento, actual))) {
			elegirUno(getString(R.string.elo_velocidad), velocidades.map { getString(R.string.valor_por_ciento, it) }, velocidades.indexOf(velocidades.minByOrNull { kotlin.math.abs(it - actual) })) { i ->
				ajustes.ponerVelocidadDe(Ajustes.MOTOR_ELOQUENCE, velocidades[i])
				aplicar()
			}
		}
		p.parrafo(getString(R.string.elo_velocidad_res))
		interruptor(Claves.ELOQUENCE_AUMENTO, R.string.elo_aumento, R.string.elo_aumento_res, ajustes.eloquenceAumento)
		for (param in MotorEloquence.PARAMETROS) {
			val puesto = ajustes.eloquenceParametro(voz, param).takeIf { it >= 0 }
			val valor = puesto ?: fabrica[param]
			p.botonSecundario(getString(R.string.compaj_fila, nombreParametro(param), valorParametro(param, valor))) { cambiarParametro(voz, param, valor) }
		}
		p.botonSecundario(getString(R.string.elo_restablecer_voz, MotorEloquence.VOCES[voz])) {
			confirmar(getString(R.string.elo_restablecer_pregunta, MotorEloquence.VOCES[voz]), getString(R.string.elo_restablecer_voz, MotorEloquence.VOCES[voz])) {
				ajustes.restablecerEloquence(voz)
				ajustes.p.edit().remove(Claves.ELOQUENCE_VELOCIDAD).remove(Claves.ELOQUENCE_AUMENTO).apply()
				ajustes.ponerVelocidadDe(Ajustes.MOTOR_ELOQUENCE, 100)
				aplicar()
			}
		}

		// Las variantes de los idiomas que tienen dos
		p.encabezado(getString(R.string.elo_idiomas))
		lista(Claves.ELOQUENCE_ESPANOL, R.string.elo_espanol, listOf("auto" to R.string.elo_auto, "ESP" to R.string.elo_espana, "MEX" to R.string.elo_mexico), ajustes.eloquenceEspanol)
		lista(Claves.ELOQUENCE_INGLES, R.string.elo_ingles, listOf("auto" to R.string.elo_auto, "USA" to R.string.elo_eeuu, "GBR" to R.string.elo_reino_unido), ajustes.eloquenceIngles)
		lista(Claves.ELOQUENCE_FRANCES, R.string.elo_frances, listOf("auto" to R.string.elo_auto, "FRA" to R.string.elo_francia, "CAN" to R.string.elo_canada), ajustes.eloquenceFrances)

		// Cómo lee
		p.encabezado(getString(R.string.elo_lectura))
		lista(Claves.ELOQUENCE_PAUSAS, R.string.elo_pausas, listOf("2" to R.string.elo_pausas_todas, "1" to R.string.elo_pausas_final, "0" to R.string.elo_pausas_motor), ajustes.eloquencePausas.toString())
		interruptor(Claves.ELOQUENCE_PREDICCION, R.string.elo_prediccion, R.string.elo_prediccion_res, ajustes.eloquencePrediccion)
		interruptor(Claves.ELOQUENCE_ABREVIATURAS, R.string.elo_abreviaturas, R.string.elo_abreviaturas_res, ajustes.eloquenceAbreviaturas)
		val frecuencias = MotorEloquence.FRECUENCIAS
		p.botonSecundario(getString(R.string.compaj_fila, getString(R.string.elo_muestreo), getString(R.string.elo_hercios, ajustes.eloquenceMuestreo))) {
			elegirUno(getString(R.string.elo_muestreo), frecuencias.map { getString(R.string.elo_hercios, it) }, frecuencias.indexOf(ajustes.eloquenceMuestreo)) { i ->
				ajustes.p.edit().putString(Claves.ELOQUENCE_MUESTREO, frecuencias[i].toString()).apply()
				aplicar()
			}
		}
		p.parrafo(getString(R.string.elo_muestreo_res))

		// Diccionarios
		p.encabezado(getString(R.string.elo_diccionarios))
		p.parrafo(getString(R.string.elo_diccionarios_res))
		for ((volumen, nombre) in listOf(Eci.DICT_MAIN to R.string.elo_dic_principal, Eci.DICT_ROOT to R.string.elo_dic_raices, Eci.DICT_ABBREVIATION to R.string.elo_dic_abreviaturas)) {
			val ruta = ajustes.eloquenceDiccionario(volumen)?.takeIf { File(it).isFile }
			val valor = ruta?.let { getString(R.string.elo_dic_entradas, org.evvdroid.Dictionaries.count(File(it))) } ?: getString(R.string.elo_dic_ninguno)
			p.botonSecundario(getString(R.string.compaj_fila, getString(nombre), valor)) { diccionario(volumen, getString(nombre), ruta != null) }
		}
	}

	/** Un ajuste de una lista de opciones, guardado como texto. */
	private fun lista(clave: String, nombre: Int, opciones: List<Pair<String, Int>>, actual: String) {
		val i = opciones.indexOfFirst { it.first == actual }.coerceAtLeast(0)
		p.botonSecundario(getString(R.string.compaj_fila, getString(nombre), getString(opciones[i].second))) {
			elegirUno(getString(nombre), opciones.map { getString(it.second) }, i) { k ->
				ajustes.p.edit().putString(clave, opciones[k].first).apply()
				aplicar()
			}
		}
	}

	/** Un interruptor de una sola parada, con su explicación dentro. */
	private fun interruptor(clave: String, nombre: Int, explicacion: Int, marcado: Boolean) {
		val texto = android.text.SpannableStringBuilder(getString(nombre)).append("\n")
		val inicio = texto.length
		texto.append(getString(explicacion))
		texto.setSpan(android.text.style.RelativeSizeSpan(0.85f), inicio, texto.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		val vista = MaterialSwitch(this).apply {
			text = texto
			isChecked = marcado
			minHeight = dp(this@ActividadEloquence, 56)
			setOnCheckedChangeListener { _, si ->
				ajustes.p.edit().putBoolean(clave, si).apply()
				ServicioTiresias.instancia?.recargarAjustes()
			}
		}
		p.contenedor.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
			topMargin = dp(this@ActividadEloquence, 8)
		})
	}

	private fun cambiarParametro(voz: Int, param: Int, actual: Int?) {
		if (param == Eci.VOICE_GENDER) {
			val opciones = listOf(getString(R.string.elo_hombre), getString(R.string.elo_mujer))
			elegirUno(nombreParametro(param), opciones, actual ?: -1) { i ->
				ajustes.ponerEloquenceParametro(voz, param, i)
				aplicar()
			}
			return
		}
		val valores = (0..100 step 5).toList()
		val nombres = valores.map { getString(R.string.valor_por_ciento, it) }
		val elegido = actual?.let { a -> valores.indexOf(valores.minByOrNull { kotlin.math.abs(it - a) }) } ?: -1
		elegirUno(nombreParametro(param), nombres, elegido) { i ->
			ajustes.ponerEloquenceParametro(voz, param, valores[i])
			aplicar()
		}
	}

	private fun diccionario(volumen: Int, nombre: String, hay: Boolean) {
		val opciones = ArrayList<com.xebolax.tiresias.accesibilidad.Accion>()
		opciones += getString(R.string.elo_dic_elegir) to {
			eligiendoDiccionario = volumen
			elegirArchivo.launch(arrayOf("text/*", "application/octet-stream"))
		}
		if (hay) opciones += getString(R.string.elo_dic_quitar) to {
			ajustes.eloquenceDiccionario(volumen)?.let { File(it).delete() }
			ajustes.ponerEloquenceDiccionario(volumen, null)
			avisar(getString(R.string.elo_dic_quitado))
			aplicar()
		}
		elegir(nombre, opciones)
	}

	/** Copia el diccionario elegido al almacenamiento de Tiresias (el original puede dejar de poder leerse). */
	private fun ponerDiccionario(uri: Uri) {
		val volumen = eligiendoDiccionario.takeIf { it >= 0 } ?: return
		eligiendoDiccionario = -1
		lifecycleScope.launch {
			try {
				val destino = withContext(Dispatchers.IO) {
					val carpeta = File(Almacen.protegido(this@ActividadEloquence).filesDir, "eloquence").apply { mkdirs() }
					val f = File(carpeta, "diccionario_$volumen.txt")
					contentResolver.openInputStream(uri)?.use { entrada -> f.outputStream().use { entrada.copyTo(it) } } ?: throw java.io.IOException("Sin datos")
					f
				}
				ajustes.ponerEloquenceDiccionario(volumen, destino.absolutePath)
				avisar(getString(R.string.elo_dic_puesto, org.evvdroid.Dictionaries.count(destino)))
				aplicar()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.w(ETIQUETA, "No se pudo copiar el diccionario de Eloquence", e)
				avisar(getString(R.string.elo_dic_mal))
			}
		}
	}

	companion object {
		private const val ETIQUETA = "AjustesEloquence"
	}
}
