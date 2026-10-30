package com.xebolax.tiresias

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.base.CopiaSeguridad
import com.xebolax.tiresias.ia.Secretos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * La copia de seguridad de la configuración: lo que se guarda vuelve igual al
 * recuperarla, lo que no estaba vuelve a su valor por defecto, y las claves de
 * IA no salen nunca en el archivo. No necesita Tiresias activo.
 */
@RunWith(AndroidJUnit4::class)
class CopiaSeguridadTest {
	private val c: Context = InstrumentationRegistry.getInstrumentation().targetContext
	private val prefs = Almacen.protegido(c).getSharedPreferences(Ajustes.ARCHIVO, Context.MODE_PRIVATE)
	private val etiquetas = File(Almacen.protegido(c).filesDir, "etiquetas.json")

	@Test
	fun loGuardadoVuelveIgualYSinClaves() {
		val antes = prefs.all.toMap()
		val etiquetasAntes = etiquetas.takeIf { it.isFile }?.readText()
		val secretos = Secretos(c)
		// Solo se pone una clave de prueba si no hay ninguna (nunca se toca una de verdad)
		val habiaClave = secretos.hay(Secretos.CLAUDE)
		try {
			prefs.edit().putInt("velocidad", 173).putBoolean("sonidos", false).putString("braille_tabla", "literario")
				.putStringSet("apps_silenciadas", setOf("com.ejemplo.a", "com.ejemplo.b")).commit()
			etiquetas.writeText("""{"com.ejemplo.a":{"boton_1":"Enviar"}}""")
			if (!habiaClave) secretos.guardar(Secretos.CLAUDE, "clave-de-prueba-que-no-debe-salir")
			prefs.edit().putString(Claves.SENIOR_CODIGO, "480917").commit()

			val copia = CopiaSeguridad.exportar(c)
			assertFalse("La copia no puede llevar claves de IA", copia.contains("clave-de-prueba-que-no-debe-salir"))
			assertFalse("La copia no puede llevar el código del modo senior", copia.contains("480917") || copia.contains(Claves.SENIOR_CODIGO))

			// Se cambia todo y se recupera
			prefs.edit().putInt("velocidad", 90).putBoolean("sonidos", true).putString("nivel_verbosidad", "BAJA").commit()
			etiquetas.writeText("{}")
			assertTrue(CopiaSeguridad.importar(c, copia))

			assertEquals("Recuperar una copia no quita el código de este móvil", "480917", prefs.getString(Claves.SENIOR_CODIGO, null))
			// Una copia antigua que aún lo lleve tampoco lo cambia
			val antigua = copia.replaceFirst("\"ajustes\": {", "\"ajustes\": {\"${Claves.SENIOR_CODIGO}\": {\"s\": \"1111\"},")
			assertTrue(antigua.contains("1111"))
			assertTrue(CopiaSeguridad.importar(c, antigua))
			assertEquals("480917", prefs.getString(Claves.SENIOR_CODIGO, null))
			assertEquals(173, prefs.getInt("velocidad", 0))
			assertEquals(false, prefs.getBoolean("sonidos", true))
			assertEquals("literario", prefs.getString("braille_tabla", null))
			assertEquals(setOf("com.ejemplo.a", "com.ejemplo.b"), prefs.getStringSet("apps_silenciadas", null))
			// Lo que no estaba en la copia vuelve a su valor por defecto
			assertFalse(prefs.contains("nivel_verbosidad"))
			assertTrue(etiquetas.readText().contains("Enviar"))
			// Un archivo que no es una copia no cambia nada
			assertFalse(CopiaSeguridad.importar(c, """{"otra":"cosa"}"""))
			assertEquals(173, prefs.getInt("velocidad", 0))
		} finally {
			// Dejar todo como estaba
			val e = prefs.edit().clear()
			for ((k, v) in antes) when (v) {
				is Boolean -> e.putBoolean(k, v)
				is Int -> e.putInt(k, v)
				is Long -> e.putLong(k, v)
				is Float -> e.putFloat(k, v)
				is String -> e.putString(k, v)
				is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
			}
			e.commit()
			if (etiquetasAntes != null) etiquetas.writeText(etiquetasAntes) else etiquetas.delete()
			if (!habiaClave) secretos.guardar(Secretos.CLAUDE, "")
		}
	}
}
