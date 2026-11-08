package com.xebolax.tiresias.braille

import android.os.Handler
import android.os.Looper
import android.view.View
import com.xebolax.tiresias.R
import com.xebolax.tiresias.lectura.Lector

/**
 * El teclado braille en pantalla puesto encima de todo, a pantalla completa.
 *
 * Con la exploración táctil puesta, los dedos que caen en la ventana del
 * teclado no le llegan: Android los toma por un dedo que explora y solo se
 * mueve el foco («Superficie del teclado braille» y nada más). Así lo vivían
 * los usuarios hasta la 1.0.11. Ahora, como el teclado braille de TalkBack, la
 * superficie va en una ventana de accesibilidad de Tiresias que tapa la
 * pantalla entera, y los toques le llegan tal cual: en Android 11 o posterior
 * con la zona de paso de toques, y antes quitando la exploración táctil
 * mientras está puesta ([ModoSuperficie.sinPasoDeToques]).
 *
 * La pone y la quita el propio teclado ([TecladoBraille]), que está en el mismo
 * proceso: al aparecer y al irse. Si la quita otra cosa (las dos teclas de
 * volumen, apagar la pantalla, cambiar de app o no tocarla en un buen rato), el
 * teclado se esconde, para que nadie se quede con un teclado al que no le
 * llegan los dedos; al volver al campo de texto, sale otra vez.
 */
class ModoTecladoBraille(
	lector: Lector,
	private val teclado: TecladoBraille,
	/** Sin aviso: la app lo vuelve a pedir justo después de esconderlo (Chrome al ir a una página) y lo esconde ella. */
	private val callado: Boolean = false,
) : ModoSuperficie(lector) {
	private val principal = Handler(Looper.getMainLooper())

	/** La superficie que recibe los dedos (la crea [crearVista]); null mientras no está puesta. */
	@Volatile var vista: SuperficieBraille? = null
		private set

	/** Si la ha quitado el teclado (al esconderse): entonces no hay que esconderlo otra vez. */
	@Volatile var quitadaPorElTeclado = false

	override fun aviso(): String = if (callado) "" else teclado.avisoListo()

	override fun alPuntos(puntos: Int) = Unit

	override fun alGesto(g: GestoBraille) = Unit

	override val sinPasoDeToques: Boolean = true

	override val bloquearGiro: Boolean = true

	/** Se escribe despacio a veces (pensar una frase): un cuarto de hora sin tocarla, en vez de cinco minutos. */
	override val sinUsoMs: Long = 15 * 60_000L

	/**
	 * Lo dibujado arriba, para quien ve un poco. Con la cortina de pantalla puesta, nada: la superficie tapa la
	 * app (lo escrito no se ve) y, en negro del todo, tampoco se sabe que se está escribiendo. La cortina deja
	 * pasar los dedos, así que el teclado funciona igual con ella.
	 */
	override val rotulo: String get() =
		if (com.xebolax.tiresias.servicio.ServicioTiresias.instancia?.ventanasActuales?.cortinaPuesta == true) ""
		else lector.textos.getString(R.string.braille_rotulo_teclado)

	override fun avisoAlCerrar(): String? = null

	override fun crearVista(tocado: () -> Unit): View {
		// Los dedos van al teclado tal cual, en el hilo principal (el suyo); cada toque cuenta como uso
		val oyente = object : OyenteSuperficie {
			override fun alPuntos(puntos: Int) {
				tocado()
				teclado.alPuntos(puntos)
			}
			override fun alGesto(g: GestoBraille) {
				tocado()
				teclado.alGesto(g)
			}
			override fun alApoyar() = teclado.alApoyar()
			override fun anunciar(texto: String) = teclado.anunciar(texto)
			override fun espejo(): Boolean = teclado.espejo()
		}
		return SuperficieBraille(lector.servicio, oyente, lector.textos, parteDelAlto = 0f, rotulo = rotulo).also {
			it.contentDescription = lector.textos.getString(R.string.braille_superficie)
			vista = it
		}
	}

	override fun alCerrar() {
		val creada = vista != null
		vista = null
		principal.post { teclado.alQuitarSuperficie(this, creada) }
	}
}
