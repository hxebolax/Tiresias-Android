package com.xebolax.tiresias.vision

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader

/**
 * Los códigos QR y de barras de una imagen, con ZXing (Apache 2.0, sin
 * Internet ni Google Play Services): los de la pantalla (una tarjeta, una
 * entrada, una wifi) o los de una foto compartida con Tiresias.
 */
object Codigos {
	data class Codigo(val texto: String, val qr: Boolean)

	private val FORMATOS = listOf(
		BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
		BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
	)

	/** Todos los códigos de [imagen], sin repetir. Lento (cientos de milisegundos): nunca en el hilo principal. */
	fun leer(imagen: Bitmap): List<Codigo> {
		val ancho = imagen.width
		val alto = imagen.height
		val pixeles = IntArray(ancho * alto)
		imagen.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
		val pistas = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to FORMATOS)
		val lector = GenericMultipleBarcodeReader(MultiFormatReader().apply { setHints(pistas) })
		val resultados = try {
			lector.decodeMultiple(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(ancho, alto, pixeles))), pistas)
		} catch (e: NotFoundException) {
			// No es un error: no hay ningún código
			return emptyList()
		}
		return resultados.filter { !it.text.isNullOrBlank() }
			.map { Codigo(it.text, it.barcodeFormat == BarcodeFormat.QR_CODE) }
			.distinctBy { it.texto }
	}

	/** Si [texto] es algo que se puede abrir (una web, un teléfono, un correo, un mapa, un SMS). */
	fun abrible(texto: String): Boolean =
		Regex("(?i)^(https?://|tel:|mailto:|geo:|smsto?:)").containsMatchIn(texto.trim())
}
