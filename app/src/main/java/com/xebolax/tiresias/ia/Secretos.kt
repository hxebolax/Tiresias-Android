package com.xebolax.tiresias.ia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.xebolax.tiresias.base.Registro
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Las claves de los servicios de IA que pone el usuario, cifradas con una clave
 * del almacén de claves de Android (AES-GCM): nunca en claro en el disco, en el
 * registro ni en las copias de seguridad.
 */
class Secretos(contexto: Context) {
	private val p = contexto.applicationContext.getSharedPreferences("secretos", Context.MODE_PRIVATE)

	fun guardar(nombre: String, valor: String) {
		if (valor.isBlank()) {
			p.edit().remove(nombre).apply()
			return
		}
		try {
			val c = Cipher.getInstance(TRANSFORMACION)
			c.init(Cipher.ENCRYPT_MODE, clave())
			val cifrado = c.doFinal(valor.trim().toByteArray(Charsets.UTF_8))
			p.edit().putString(nombre, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cifrado, Base64.NO_WRAP)).apply()
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo guardar la clave $nombre", e)
		}
	}

	fun leer(nombre: String): String {
		val guardado = p.getString(nombre, null) ?: return ""
		return try {
			val (iv, datos) = guardado.split(':').let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
			val c = Cipher.getInstance(TRANSFORMACION)
			c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, iv))
			String(c.doFinal(datos), Charsets.UTF_8)
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudo leer la clave $nombre", e)
			""
		}
	}

	fun hay(nombre: String): Boolean = p.contains(nombre)

	private fun clave(): SecretKey {
		val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
		(ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
		val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
		g.init(
			KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build()
		)
		return g.generateKey()
	}

	companion object {
		private const val ETIQUETA = "Secretos"
		private const val ALIAS = "tiresias_secretos"
		private const val TRANSFORMACION = "AES/GCM/NoPadding"
		const val CLAUDE = "clave_claude"
		const val OPENAI = "clave_openai"
		const val GEMINI = "clave_gemini"
		const val COMPATIBLE = "clave_compatible"
	}
}
