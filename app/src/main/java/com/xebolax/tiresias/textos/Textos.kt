package com.xebolax.tiresias.textos

import android.content.Context
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Fonetico
import com.xebolax.tiresias.nucleo.Frase
import com.xebolax.tiresias.nucleo.FrasePlural
import com.xebolax.tiresias.nucleo.Identificadores
import com.xebolax.tiresias.nucleo.Plantillas
import com.xebolax.tiresias.nucleo.TablaSimbolos
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Las [Plantillas] del núcleo sacadas de los recursos de Android, en el idioma
 * de Tiresias (que puede no ser el del móvil).
 *
 * Cada [Frase] es la cadena `f_<nombre en minúsculas>` de `res/values*\/strings_lectura.xml`
 * y cada [FrasePlural] el plural `p_<nombre>`. Para traducir Tiresias basta con
 * traducir esos archivos (que no falte ninguna cadena lo comprueba lint, con MissingTranslation).
 */
class PlantillasAndroid(contexto: Context) : Plantillas {
	private val recursos = Idioma.envolver(contexto.applicationContext).resources
	private val paquete = contexto.packageName
	private val cache = ConcurrentHashMap<Frase, String>()

	override fun de(frase: Frase): String = cache.getOrPut(frase) {
		val id = recursos.getIdentifier("f_" + frase.name.lowercase(Locale.ROOT), "string", paquete)
		if (id == 0) {
			Registro.w(ETIQUETA, "Falta la cadena f_${frase.name.lowercase(Locale.ROOT)}")
			frase.name.lowercase(Locale.ROOT).replace('_', ' ')
		} else recursos.getString(id)
	}

	override fun plural(frase: FrasePlural, n: Int): String {
		val id = recursos.getIdentifier("p_" + frase.name.lowercase(Locale.ROOT), "plurals", paquete)
		return if (id == 0) "%1\$s" else recursos.getQuantityString(id, n)
	}

	companion object {
		private const val ETIQUETA = "Plantillas"
	}
}

/**
 * Los datos de lectura de un idioma, en `assets/idiomas/<código>/`:
 *
 * - `simbolos.tsv`: puntuación y símbolos con su nivel (ver [TablaSimbolos]).
 * - `emojis.tsv`: emojis con su nombre (mismo formato, nivel «algunas»).
 * - `letras.tsv`: letras que necesitan nombre al leerlas sueltas («y» → «i griega»).
 * - `fonetico.tsv`: alfabeto para deletrear.
 * - `identificadores.tsv`: las palabras de los identificadores de vista (en
 *   inglés casi siempre) en este idioma, ver [Identificadores].
 *
 * - `teclas.tsv`: las etiquetas en inglés de las teclas del teclado en pantalla
 *   (Gboard las da así en algunos móviles: «period», «comma») en este idioma.
 *
 * Si un idioma no tiene alguno, se usa el del español (las teclas no). Para añadir un idioma
 * basta con crear su carpeta (ver `docs/traducir.md`).
 */
class DatosIdioma private constructor(
	val codigo: String,
	val simbolos: TablaSimbolos,
	val letras: Map<String, String>,
	val fonetico: Fonetico,
	val identificadores: Identificadores,
	/** Las etiquetas en inglés de las teclas del teclado en pantalla (`teclas.tsv`), en minúsculas, con su nombre en este idioma. */
	val teclas: Map<String, String>,
) {
	companion object {
		private val cache = ConcurrentHashMap<String, DatosIdioma>()

		fun de(contexto: Context, locale: Locale): DatosIdioma {
			val codigo = locale.language.ifEmpty { "es" }
			return cache.getOrPut(codigo) { cargar(contexto.applicationContext, codigo) }
		}

		private fun cargar(c: Context, codigo: String): DatosIdioma {
			fun leer(archivo: String): String? = runCatching {
				c.assets.open("idiomas/$codigo/$archivo").bufferedReader(Charsets.UTF_8).use { it.readText() }
			}.getOrNull() ?: runCatching {
				c.assets.open("idiomas/es/$archivo").bufferedReader(Charsets.UTF_8).use { it.readText() }
			}.getOrNull()

			val simbolos = TablaSimbolos.leer(leer("simbolos.tsv").orEmpty()).mas(TablaSimbolos.leer(leer("emojis.tsv").orEmpty()))
			val letras = leer("letras.tsv").orEmpty().lineSequence()
				.filter { it.isNotBlank() && !it.startsWith("#") }
				.mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1].trim() } }
				.toMap()
			val fonetico = leer("fonetico.tsv")?.let { Fonetico.leer(it) } ?: Fonetico.OTAN
			// Las teclas del teclado en pantalla («period» → «punto»): sin tabla propia, ninguna (la del español traduciría al español)
			val teclas = runCatching { c.assets.open("idiomas/$codigo/teclas.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull().orEmpty().lineSequence()
				.filter { it.isNotBlank() && !it.startsWith("#") }
				.mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0].trim().lowercase() to it[1].trim() } }
				.toMap()
			// Sin tabla propia, la del español no sirve (traduciría al español): solo se quita el relleno del inglés
			val identificadores = runCatching {
				c.assets.open("idiomas/$codigo/identificadores.tsv").bufferedReader(Charsets.UTF_8).use { Identificadores.leer(it.readText()) }
			}.getOrNull() ?: runCatching {
				c.assets.open("idiomas/en/identificadores.tsv").bufferedReader(Charsets.UTF_8).use { Identificadores.leer(it.readText()) }
			}.getOrNull() ?: Identificadores.VACIO
			return DatosIdioma(codigo, simbolos, letras, fonetico, identificadores, teclas)
		}
	}
}
