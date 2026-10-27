package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentificadoresTest {
	private val es = Identificadores.leer(
		"""
		@invertir
		view	-
		btn	-
		iv	-
		entry	-
		search view	buscador	n
		contact	contacto	n
		photo	foto	n
		profile	perfil	n
		user	usuario	n
		dark	oscuro	a
		mode	modo	n
		send	enviar	v
		message	mensaje	n
		voice note	nota de voz	n
		""".trimIndent()
	)

	@Test
	fun losNombresSeguidosVanAlRevesConDe() {
		assertEquals("foto de contacto", es.legible("com.whatsapp:id/contact_photo"))
		assertEquals("foto de contacto", es.legible("com.whatsapp:id/contact_photo_view"))
		assertEquals("foto de perfil de usuario", es.legible("com.x:id/userProfilePhoto"))
		assertEquals("modo oscuro", es.legible("com.x:id/dark_mode"))
	}

	@Test
	fun losVerbosSeQuedanDelante() {
		assertEquals("enviar mensaje", es.legible("com.x:id/btnSendMessage"))
		assertEquals("enviar nota de voz", es.legible("com.x:id/send_voice_note_btn"))
	}

	@Test
	fun lasFrasesMandanSobreElRelleno() {
		assertEquals("buscador", es.legible("com.x:id/search_view"))
	}

	@Test
	fun soloRellenoNoDiceNada() {
		assertNull(es.legible("com.whatsapp:id/entry"))
		assertNull(es.legible("com.x:id/iv"))
		assertNull(es.legible("com.x:id/view3"))
	}

	@Test
	fun loQueNoEstaEnLaTablaSeDiceTalCual() {
		assertEquals("boton enviar", es.legible("com.x:id/boton_enviar"))
		assertEquals("boton enviar", Identificadores.VACIO.legible("com.x:id/boton_enviar"))
	}
}
