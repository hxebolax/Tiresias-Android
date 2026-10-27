package com.xebolax.tiresias

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Claves
import com.xebolax.tiresias.nucleo.Accion
import com.xebolax.tiresias.voz.BibliotecaEloquence
import org.evvdroid.Eci
import org.evvdroid.EvvEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Eloquence dentro de Tiresias: la biblioteca se descomprime del APK, el motor
 * tiene sus diez idiomas, y Tiresias habla con ella (con la voz y los parámetros
 * elegidos), sin pasar a eSpeak.
 */
@RunWith(AndroidJUnit4::class)
class EloquenceTest : PruebaTiresias() {

	@Test
	fun laBibliotecaSeDescomprimeYTieneLosDiezIdiomas() {
		assertTrue("Este APK no trae Eloquence para el emulador", BibliotecaEloquence.disponible(app))
		assertTrue("No se pudo descomprimir Eloquence", BibliotecaEloquence.preparar(app))
		val idiomas = EvvEngine.available.map { Eci.baseLanguage(it) }
		assertTrue("Faltan idiomas: $idiomas", idiomas.size >= 10)
		assertTrue("No está el español de España", 0x00020000 in idiomas)
		assertTrue("No está el español de México", 0x00020001 in idiomas)
		val m = EvvEngine.open(0x00020000)
		assertNotNull("No se abre el motor en español", m)
		try {
			assertTrue("La voz no se aplica", m!!.applyVoice(0))
			assertTrue(m.speak("Hola, esto es Eloquence."))
			val bufer = ByteArray(8192)
			var total = 0
			while (true) {
				val n = m.read(bufer)
				if (n <= 0) break
				total += n
			}
			assertTrue("Eloquence no dio audio: $total bytes", total > 8000)
		} finally {
			m?.close()
		}
	}

	private fun hablaConEloquence(): Boolean {
		val voz = s.vozPruebas!!
		val antes = voz.ultimoInicioSonido
		abrirMuestrario()
		hacer(Accion.PRIMERO, 1500)
		esperar(4000) { voz.ultimoInicioSonido.takeIf { it != antes } }
		return voz.ultimoMotor == "Eloquence"
	}

	@Test
	fun tiresiasHablaConEloquenceYSusAjustes() {
		ajuste(Claves.MOTOR, Ajustes.MOTOR_ELOQUENCE)
		SystemClock.sleep(500)
		assertEquals("Eloquence", s.vozPruebas!!.nombreMotor)
		assertTrue("Tiresias no habló con Eloquence", hablaConEloquence())
		// Otra voz (Glen) con la cabeza más grande y voz de mujer: sigue hablando
		ajuste(Claves.ELOQUENCE_VOZ, 4)
		Ajustes(app).ponerEloquenceParametro(4, Eci.VOICE_HEAD_SIZE, 80)
		Ajustes(app).ponerEloquenceParametro(4, Eci.VOICE_GENDER, 1)
		ajuste(Claves.ELOQUENCE_PAUSAS, "0")
		assertTrue("Con otra voz y otros parámetros, no habló", hablaConEloquence())
		Ajustes(app).restablecerEloquence(4)
	}

	@Test
	fun deFabricaSuenaComoElNvdaDelAutor() {
		val a = Ajustes(app)
		assertEquals(0, a.eloquenceVoz)
		assertEquals(65, a.eloquenceParametro(0, Eci.VOICE_PITCH_BASELINE))
		assertEquals(35, a.eloquenceParametro(0, Eci.VOICE_PITCH_FLUCTUATION))
		assertEquals(95, a.eloquenceParametro(0, Eci.VOICE_VOLUME))
		assertEquals(0, a.eloquenceParametro(0, Eci.VOICE_ROUGHNESS))
		assertEquals(0, a.eloquenceParametro(0, Eci.VOICE_BREATHINESS))
		// La velocidad 55 de NVDA con el aumento: 166 en el motor, como el driver IBMTTS
		assertEquals(166, com.xebolax.tiresias.voz.MotorEloquence.velocidadMotor(a.eloquenceVelocidad, a.eloquenceAumento))
		assertEquals(104, com.xebolax.tiresias.voz.MotorEloquence.velocidadMotor(55, false))
		assertEquals(11025, a.eloquenceMuestreo)
		assertEquals(2, a.eloquencePausas)
	}
}
