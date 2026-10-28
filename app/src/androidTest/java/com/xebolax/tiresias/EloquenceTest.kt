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

	/** Los segundos que tarda Eloquence en decir [frase] a [porcentaje] de su velocidad (con la cuenta de Tiresias). */
	private fun segundosEloquence(frase: String, porcentaje: Int): Double {
		assertTrue(BibliotecaEloquence.preparar(app))
		val m = EvvEngine.open(0x00020000)!!
		try {
			assertTrue(m.applyVoice(0, mapOf(Eci.VOICE_SPEED to Eci.DEFAULT_SPEED)))
			m.setRatePercent(porcentaje)
			assertTrue(m.speak(frase))
			val bufer = ByteArray(8192)
			var bytes = 0L
			while (true) {
				val n = m.read(bufer)
				if (n <= 0) break
				bytes += n
			}
			return bytes / 2.0 / m.sampleRateHz
		} finally {
			m.close()
		}
	}

	/**
	 * Medido, no supuesto: al 100 % Eloquence tarda en una frase lo mismo, más o
	 * menos, que eSpeak a su velocidad normal (175 palabras por minuto), y al 30 %
	 * va bastante más despacio. Antes, al 100 % iba unas diez veces más deprisa.
	 */
	@Test
	fun alCienPorCienEloquenceHablaAUnaVelocidadNormal() {
		val frase = "Esta es una frase de prueba para medir la velocidad de la voz con calma, sin prisa ninguna."
		val espeak = com.xebolax.tiresias.voz.EspeakNativo.sintetizar(app, frase, java.util.Locale("es", "ES"), 100, 100)!!.size.toDouble() / com.xebolax.tiresias.voz.EspeakNativo.frecuenciaMuestras
		val normal = segundosEloquence(frase, 100)
		val lenta = segundosEloquence(frase, 30)
		val rapida = segundosEloquence(frase, 200)
		android.util.Log.i("EloquenceTest", "eSpeak 100 %: $espeak s; Eloquence 100 %: $normal s, 30 %: $lenta s, 200 %: $rapida s")
		val proporcion = normal / espeak
		assertTrue("Al 100 %, Eloquence dura $normal s y eSpeak $espeak s", proporcion in 0.6..1.8)
		assertTrue("Al 30 % no va más despacio: $lenta s frente a $normal s", lenta > normal * 1.5)
		assertTrue("Al 200 % no va más deprisa: $rapida s frente a $normal s", rapida < normal * 0.75)
	}

	/** Una velocidad guardada con la escala vieja (sobre la 166 de NVDA) vuelve una vez a la normal. */
	@Test
	fun laVelocidadGuardadaDeEloquenceVuelveALaNormal() {
		val a = Ajustes(app)
		prefs.edit().putBoolean(Claves.ELOQUENCE_VELOCIDAD_NORMAL, false).putString(Claves.MOTOR, Ajustes.MOTOR_ELOQUENCE)
			.putString(Claves.MOTOR_DE_LA_VOZ, Ajustes.MOTOR_ELOQUENCE).putInt(Claves.VELOCIDAD, 30)
			.putInt(Claves.ELOQUENCE_VELOCIDAD, 55).commit()
		a.normalizarEloquence()
		SystemClock.sleep(200)
		assertEquals(100, a.velocidad)
		assertTrue(!prefs.contains(Claves.ELOQUENCE_VELOCIDAD))
		// Solo una vez: lo que ponga el usuario después se queda
		ajuste(Claves.VELOCIDAD, 70)
		a.normalizarEloquence()
		assertEquals(70, a.velocidad)
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
		// La velocidad: con la de Tiresias al 100 %, la normal del motor (50), no la 166 del NVDA del autor
		// (unas diez veces más deprisa); al 30 %, lo más despacio que habla, y al 200 %, el doble de deprisa
		assertEquals(Eci.DEFAULT_SPEED, com.xebolax.tiresias.voz.MotorEloquence.velocidadMotor(100))
		assertTrue(com.xebolax.tiresias.voz.MotorEloquence.velocidadMotor(30) < Eci.DEFAULT_SPEED)
		val doble = org.evvdroid.SpeechRate.timesFor(com.xebolax.tiresias.voz.MotorEloquence.velocidadMotor(200))
		assertTrue("El 200 % no es el doble: $doble", doble in 1.9..2.1)
		assertEquals(11025, a.eloquenceMuestreo)
		assertEquals(2, a.eloquencePausas)
	}
}
