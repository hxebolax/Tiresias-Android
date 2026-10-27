package com.xebolax.tiresias.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.xebolax.tiresias.R
import com.xebolax.tiresias.accesibilidad.avisar
import com.xebolax.tiresias.accesibilidad.conEspera
import com.xebolax.tiresias.accesibilidad.copiar
import com.xebolax.tiresias.accesibilidad.dp
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.vision.Codigos
import com.xebolax.tiresias.vision.Ocr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Leer con Tiresias»: una imagen compartida desde otra app (una foto de la
 * cámara, una imagen de WhatsApp o del correo). Tiresias lee sus códigos QR y
 * de barras y su texto (con el OCR del móvil, sin Internet) y los deja en la
 * pantalla para leerlos, abrirlos o copiarlos. Así se lee el QR de una tarjeta
 * sin que Tiresias necesite cámara propia.
 */
class ActividadImagen : ActividadBase() {
	private lateinit var p: Pantalla

	/** Lo que se ha leído, para no repetir el trabajo al girar la pantalla. */
	private class Leido(val codigos: List<Codigos.Codigo>, val texto: String?, val sinOcr: Boolean)

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		p = pantalla(getString(R.string.imagen_titulo))
		val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.data
		if (uri == null) {
			p.parrafo(getString(R.string.imagen_sin_imagen))
			return
		}
		val estadoVivo = p.estadoVivo(getString(R.string.imagen_leyendo))
		lifecycleScope.launch {
			try {
				val imagen = withContext(Dispatchers.IO) { cargar(uri) }
				if (imagen == null) {
					estadoVivo.text = getString(R.string.imagen_no_se_abre)
					return@launch
				}
				mostrarImagen(imagen)
				// Los códigos enseguida; el texto (el OCR tarda más) después, sin hacer esperar a los códigos
				val codigos = conEspera(getString(R.string.imagen_leyendo)) { withContext(Dispatchers.Default) { Codigos.leer(imagen) } }
				mostrarCodigos(codigos)
				estadoVivo.text = getString(R.string.imagen_leyendo_texto)
				val leido = withContext(Dispatchers.Default) { leerTexto(imagen, codigos) }
				estadoVivo.text = resumen(leido)
				mostrarTexto(leido)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudo leer la imagen compartida", e)
				estadoVivo.text = getString(R.string.imagen_no_se_abre)
			}
		}
	}

	/** La imagen, como mucho de 2400 píxeles de lado (de sobra para el texto y los códigos). */
	private fun cargar(uri: Uri): Bitmap? {
		val tam = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		// Solo mide (con inJustDecodeBounds, decodeStream siempre devuelve null)
		val flujo = contentResolver.openInputStream(uri) ?: return null
		flujo.use { BitmapFactory.decodeStream(it, null, tam) }
		if (tam.outWidth <= 0 || tam.outHeight <= 0) return null
		var muestra = 1
		while (maxOf(tam.outWidth, tam.outHeight) / muestra > LADO_MAXIMO) muestra *= 2
		val opciones = BitmapFactory.Options().apply { inSampleSize = muestra; inPreferredConfig = Bitmap.Config.ARGB_8888 }
		return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
	}

	private fun leerTexto(imagen: Bitmap, codigos: List<Codigos.Codigo>): Leido {
		val ocr = Ocr(this)
		if (!ocr.disponible) return Leido(codigos, null, true)
		return try {
			Leido(codigos, ocr.texto(imagen).trim(), false)
		} finally {
			ocr.cerrar()
		}
	}

	private fun resumen(l: Leido): String = when {
		l.codigos.isEmpty() && l.texto.isNullOrEmpty() -> getString(if (l.sinOcr) R.string.imagen_nada_sin_ocr else R.string.imagen_nada)
		else -> listOfNotNull(
			l.codigos.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.qr_varios, it, it) },
			l.texto?.takeIf { it.isNotEmpty() }?.let { getString(R.string.imagen_con_texto) },
		).joinToString(", ")
	}

	private fun mostrarImagen(imagen: Bitmap) {
		val vista = ImageView(this).apply {
			setImageBitmap(imagen)
			adjustViewBounds = true
			contentDescription = getString(R.string.imagen_descripcion)
		}
		p.contenedor.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 240)))
	}

	private fun mostrarCodigos(codigos: List<Codigos.Codigo>) {
		if (codigos.isNotEmpty()) {
			p.encabezado(getString(R.string.imagen_codigos))
			for (c in codigos) {
				val tipo = getString(if (c.qr) R.string.qr_tipo_qr else R.string.qr_tipo_barras)
				p.parrafo(getString(R.string.qr_leido, tipo, c.texto))
				// Cada botón dice de qué código es: en la pantalla no hay dos con el mismo nombre
				val corto = c.texto.take(40)
				if (Codigos.abrible(c.texto)) p.boton(getString(R.string.imagen_abrir, corto)) { abrir(c.texto) }
				p.botonSecundario(getString(R.string.imagen_copiar, corto)) {
					copiar(c.texto)
					avisar(getString(R.string.qr_copiado))
				}
			}
		}
	}

	private fun mostrarTexto(l: Leido) {
		val texto = l.texto
		if (!texto.isNullOrEmpty()) {
			p.encabezado(getString(R.string.imagen_texto))
			p.parrafo(texto).setTextIsSelectable(true)
			p.botonSecundario(getString(R.string.imagen_copiar_texto)) {
				copiar(texto)
				avisar(getString(R.string.imagen_texto_copiado))
			}
		} else if (l.sinOcr) p.parrafo(getString(R.string.imagen_falta_ocr))
	}

	private fun abrir(texto: String) {
		try {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(texto.trim())))
		} catch (e: android.content.ActivityNotFoundException) {
			Registro.w(ETIQUETA, "No hay app para abrir el código", e)
			avisar(getString(R.string.qr_sin_app))
		}
	}

	private companion object {
		const val ETIQUETA = "ActividadImagen"
		const val LADO_MAXIMO = 2400
	}
}
