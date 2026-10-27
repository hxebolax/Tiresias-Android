package com.xebolax.tiresias.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TablaSimbolosTest {
	@Test
	fun laAlmohadillaNoEsUnComentario() {
		val t = TablaSimbolos.leer("# Un comentario\n#\talmohadilla\talgunas\n@\tarroba\talgunas\n")
		assertEquals("almohadilla", t.nombreCaracter("#"))
		assertEquals("arroba", t.nombreCaracter("@"))
		// El comentario no se lee como símbolo
		assertNull(t["# Un comentario"])
	}
}
