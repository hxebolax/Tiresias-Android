package com.xebolax.tiresias.nucleo

import com.xebolax.tiresias.nucleo.PoliticaAltavoz.Salida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoliticaAltavozTest {

	@Test
	fun alApartarloVaAlAltavozYAlAcercarloVuelve() {
		val p = PoliticaAltavoz()
		assertNull("En la oreja, con el auricular: nada que cambiar", p.alLeer(true, Salida.AURICULAR))
		assertEquals(Salida.ALTAVOZ, p.alLeer(false, Salida.AURICULAR))
		p.alCambiarSalida(Salida.ALTAVOZ)
		assertEquals(Salida.AURICULAR, p.alLeer(true, Salida.ALTAVOZ))
	}

	@Test
	fun unaLlamadaQueEmpiezaLejosVaAlAltavoz() {
		assertEquals(Salida.ALTAVOZ, PoliticaAltavoz().alLeer(false, Salida.AURICULAR))
	}

	@Test
	fun noApagaElAltavozQuePusoElUsuario() {
		val p = PoliticaAltavoz()
		p.alLeer(false, Salida.ALTAVOZ)
		// El usuario lo puso en el altavoz: al acercarlo, se queda
		assertNull(p.alLeer(true, Salida.ALTAVOZ))
	}

	@Test
	fun noTocaElBluetoothNiLosAuriculares() {
		val p = PoliticaAltavoz()
		assertNull(p.alLeer(false, Salida.OTRA))
		assertNull(p.alLeer(true, Salida.OTRA))
	}

	@Test
	fun siElUsuarioCambiaLaSalidaSeRespeta() {
		val p = PoliticaAltavoz()
		assertEquals(Salida.ALTAVOZ, p.alLeer(false, Salida.AURICULAR))
		// El usuario lo pasa al auricular y vuelve a poner el altavoz él: ya no es de la política
		p.alCambiarSalida(Salida.AURICULAR)
		p.alCambiarSalida(Salida.ALTAVOZ)
		assertNull(p.alLeer(true, Salida.ALTAVOZ))
	}

	@Test
	fun sinMoverseNoHaceNada() {
		val p = PoliticaAltavoz()
		p.alLeer(false, Salida.AURICULAR)
		assertNull(p.alLeer(false, Salida.ALTAVOZ))
	}
}
