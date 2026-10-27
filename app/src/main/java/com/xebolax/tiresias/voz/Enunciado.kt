package com.xebolax.tiresias.voz

import java.util.Locale

/** Qué pasa con lo que se estaba diciendo cuando llega un enunciado nuevo. */
enum class Modo {
	/** Corta lo que suena y lo que espera (lo normal al moverse por la pantalla). */
	INTERRUMPIR,
	/** Se pone a la cola, detrás de lo que suena (anuncios, pistas). */
	ENCOLAR,
	/** Solo si no se está diciendo nada (avisos sin importancia). */
	SI_CALLADO,
}

/** Para qué es lo que se dice: decide si una tecla o un toque lo cortan y si se guarda en el historial. */
enum class Tipo { NAVEGACION, ESCRITURA, ANUNCIO, NOTIFICACION, PISTA, LECTURA_CONTINUA, SISTEMA, IA }

/**
 * Una frase que va a la voz.
 *
 * [texto] ya va preparado (puntuación, diccionarios) salvo que [preparar] sea
 * true. [tono] y [velocidad] son relativos (100 = el del usuario): las
 * mayúsculas suben el tono, los avisos pueden ir algo más rápidos.
 */
data class Enunciado(
	val texto: String,
	val modo: Modo = Modo.INTERRUMPIR,
	val tipo: Tipo = Tipo.NAVEGACION,
	val idioma: Locale? = null,
	val tono: Int = 100,
	val velocidad: Int = 100,
	/** Pasar el texto por el [com.xebolax.tiresias.nucleo.Normalizador] antes de hablar. */
	val preparar: Boolean = true,
	/** Deletrear: cada carácter por su nombre. */
	val deletrear: Boolean = false,
	/** Se llama al empezar a sonar (desde el hilo de la voz). */
	val alEmpezar: (() -> Unit)? = null,
	/** Se llama al terminar, con true si sonó entero (false si se cortó o falló). */
	val alTerminar: ((Boolean) -> Unit)? = null,
)
