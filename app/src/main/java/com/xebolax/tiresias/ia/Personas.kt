package com.xebolax.tiresias.ia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Las personas que conoce el usuario (Backtalk 173): un nombre y una foto de
 * su cara. Al describir una imagen o la pantalla, la IA recibe además una hoja
 * con esas fotos y sus nombres, y nombra a quien reconozca.
 *
 * - Las fotos se guardan solo en el móvil (en su almacén privado), recortadas
 *   y pequeñas (256 × 256).
 * - Con la IA del propio móvil, la hoja se une a la imagen y no sale de él. Con
 *   una IA de internet, solo se manda si el usuario lo ha permitido
 *   ([Ajustes.personasNube]): son fotos de otras personas.
 */
object Personas {
	data class Persona(val id: String, val nombre: String)

	private const val LADO = 256
	private const val ALTO_NOMBRE = 40
	private const val POR_FILA = 3
	private const val MAXIMO = 12
	private const val CLAVE = "personas"

	private fun carpeta(c: Context) = File(c.filesDir, "personas").apply { mkdirs() }

	private fun prefs(c: Context) = c.getSharedPreferences("personas", Context.MODE_PRIVATE)

	fun lista(c: Context): List<Persona> = runCatching {
		val a = JSONArray(prefs(c).getString(CLAVE, "[]"))
		(0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Persona(it.getString("id"), it.getString("nombre")) } }
			.filter { File(carpeta(c), "${it.id}.jpg").isFile }
	}.getOrElse {
		Registro.w(ETIQUETA, "No se pudo leer la lista de personas", it)
		emptyList()
	}

	private fun guardar(c: Context, l: List<Persona>) {
		val a = JSONArray()
		l.forEach { a.put(JSONObject().put("id", it.id).put("nombre", it.nombre)) }
		prefs(c).edit().putString(CLAVE, a.toString()).apply()
		hoja(c).delete()
	}

	/** Añade a [nombre] con la foto de [entrada]: se recorta al centro, en cuadrado. Bloquea: fuera del hilo principal. */
	fun anadir(c: Context, nombre: String, entrada: InputStream): Persona {
		val actuales = lista(c)
		if (actuales.size >= MAXIMO) throw IOException("Como mucho $MAXIMO personas")
		val foto = BitmapFactory.decodeStream(entrada) ?: throw IOException("No es una imagen")
		val lado = minOf(foto.width, foto.height)
		val recorte = Bitmap.createBitmap(foto, (foto.width - lado) / 2, (foto.height - lado) / 2, lado, lado)
		val pequena = Bitmap.createScaledBitmap(recorte, LADO, LADO, true)
		val p = Persona(UUID.randomUUID().toString(), nombre.trim().take(60))
		File(carpeta(c), "${p.id}.jpg").outputStream().use { pequena.compress(Bitmap.CompressFormat.JPEG, 85, it) }
		guardar(c, actuales + p)
		return p
	}

	fun borrar(c: Context, id: String) {
		File(carpeta(c), "$id.jpg").delete()
		guardar(c, lista(c).filter { it.id != id })
	}

	/** Si hay que mandar la hoja de personas a [proveedor] con esta imagen. */
	fun activas(c: Context, proveedor: AsistenteIA.Proveedor): Boolean {
		val a = Ajustes(c)
		if (!a.reconocerPersonas || lista(c).isEmpty()) return false
		return !AsistenteIA.saleDelMovil(proveedor) || a.personasNube
	}

	private fun hoja(c: Context) = File(c.cacheDir, "personas_hoja.jpg")

	/** La hoja con las fotos y los nombres (se rehace al cambiar las personas). */
	fun hojaDePersonas(c: Context): File? {
		val f = hoja(c)
		if (f.isFile) return f
		val l = lista(c).take(MAXIMO)
		if (l.isEmpty()) return null
		val filas = (l.size + POR_FILA - 1) / POR_FILA
		val columnas = minOf(POR_FILA, l.size)
		val imagen = Bitmap.createBitmap(columnas * LADO, filas * (LADO + ALTO_NOMBRE), Bitmap.Config.RGB_565)
		val lienzo = Canvas(imagen)
		lienzo.drawColor(Color.WHITE)
		val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 28f; textAlign = Paint.Align.CENTER }
		for ((i, p) in l.withIndex()) {
			val x = (i % POR_FILA) * LADO
			val y = (i / POR_FILA) * (LADO + ALTO_NOMBRE)
			BitmapFactory.decodeFile(File(carpeta(c), "${p.id}.jpg").path)?.let { lienzo.drawBitmap(it, null, Rect(x, y, x + LADO, y + LADO), null) }
			lienzo.drawText(p.nombre, x + LADO / 2f, y + LADO + 30f, texto)
		}
		f.outputStream().use { imagen.compress(Bitmap.CompressFormat.JPEG, 85, it) }
		return f
	}

	/** Lo que se añade a la pregunta: la hoja es la segunda imagen (o la parte derecha, con la IA del móvil). */
	fun instruccion(c: Context, unida: Boolean): String {
		val t = Idioma.envolver(c)
		val nombres = lista(c).joinToString(", ") { it.nombre }
		return t.getString(if (unida) R.string.ia_personas_unida else R.string.ia_personas_aparte, nombres)
	}

	/** [principal] y la hoja, una al lado de la otra, en un solo JPEG (para la IA del móvil, que lee una imagen). */
	fun unir(c: Context, principal: File, hojaPersonas: File): File {
		val a = BitmapFactory.decodeFile(principal.path) ?: return principal
		val b = BitmapFactory.decodeFile(hojaPersonas.path) ?: return principal
		val escala = a.height.toFloat() / b.height
		val ancho = (b.width * escala).toInt()
		val junta = Bitmap.createBitmap(a.width + ancho, a.height, Bitmap.Config.RGB_565)
		val lienzo = Canvas(junta)
		lienzo.drawColor(Color.WHITE)
		lienzo.drawBitmap(a, 0f, 0f, null)
		lienzo.drawBitmap(b, null, Rect(a.width, 0, a.width + ancho, a.height), null)
		val destino = File(c.cacheDir, "ia_con_personas.jpg")
		destino.outputStream().use { junta.compress(Bitmap.CompressFormat.JPEG, 85, it) }
		return destino
	}

	private const val ETIQUETA = "Personas"
}
