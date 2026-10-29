package com.xebolax.tiresias.voz

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.xebolax.tiresias.BuildConfig
import com.xebolax.tiresias.R
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Idioma
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.Diccionario
import com.xebolax.tiresias.nucleo.ModoMayusculas
import com.xebolax.tiresias.nucleo.Normalizador
import com.xebolax.tiresias.textos.DatosIdioma
import com.xebolax.tiresias.textos.PlantillasAndroid
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * La voz de Tiresias: recibe lo que hay que decir, lo prepara y lo hace sonar
 * con el motor elegido, en su propio hilo de prioridad de audio.
 *
 * - **Interrumpir es inmediato**: el enunciado nuevo corta el motor desde el
 *   hilo que llama (no espera a que el hilo de la voz se entere).
 * - **Nunca se queda muda**: si el motor del sistema falla o no empieza a
 *   hablar a tiempo, la frase se dice con eSpeak NG y, si falla, con Pico; tras
 *   tres fallos seguidos el motor se aparta un minuto y se avisa una vez.
 * - **Atenuación**: mientras habla, pide el foco de audio transitorio con
 *   atenuación (la música baja, no se para) si el usuario lo quiere.
 * - **Grabaciones**: si otra app graba con el micrófono, solo se dice lo que
 *   pide el usuario al moverse (nada de anuncios ni notificaciones encima).
 * - **Historial**: las últimas frases, solo en memoria (repetir, deletrear, copiar).
 *   En la variante de pruebas, además, cada frase va al registro con la
 *   etiqueta `TiresiasHabla`; en la de publicación nunca se registra.
 */
class Voz(contexto: Context) {
	private val app = contexto.applicationContext
	val ajustes = Ajustes(app)
	private val audio = (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { PistaAudio.gestor = it }
	private var atributos = PistaAudio.atributosVoz()
	private val pista = PistaAudio(atributos)
	private val espeak = MotorEspeak(app, pista)
	private val pico = MotorPico(app, pista)
	private val eloquence = MotorEloquence(app, pista)
	private var sistema: MotorSistema? = null
	/** La voz neuronal Piper elegida (descargada), si la hay. */
	private var neuronal: MotorPiper? = null
	private val principal = Handler(Looper.getMainLooper())

	private val cerrojo = ReentrantLock()
	private val hayTrabajo = cerrojo.newCondition()
	private val cola = ArrayDeque<Pedido>()
	private val generacion = AtomicInteger()
	@Volatile private var enCurso: Pedido? = null
	@Volatile private var motorEnCurso: Motor? = null
	@Volatile private var terminar = false

	/** Fallos seguidos del motor del sistema y hasta cuándo está apartado. */
	private var fallosSeguidos = 0
	private var apartadoHasta = 0L
	private var avisadoFallo = false

	private var foco: AudioFocusRequest? = null
	@Volatile private var grabando = false

	/** Cuándo empezó a sonar la última frase (`SystemClock.uptimeMillis`), para medir la latencia en las pruebas. */
	@Volatile var ultimoInicioSonido = 0L

	/** El motor que dijo la última frase (para las pruebas y para saber si el elegido falla). */
	@Volatile var ultimoMotor: String? = null
		private set

	/** Voz apagada por el usuario (alternar voz): solo suenan los sonidos y el braille. */
	@Volatile var silenciada = false

	/** Con el modo «primero braille»: los únicos tipos de frase que suenan (null: todos). */
	@Volatile var tiposPermitidos: Set<Tipo>? = null

	/**
	 * Voz a demanda (NVDA 2024.1): al moverse, al llegar avisos o al escribir no
	 * habla; solo lo que se pide con una acción (repetir, leer, la hora...). La
	 * línea braille y los sonidos siguen igual.
	 */
	@Volatile var aDemanda = false
	/** Hasta cuándo vale lo que se dice por una acción pedida (en voz a demanda). */
	@Volatile var pedidoHasta = 0L

	private val historial = ArrayDeque<String>()

	private val hilo = Thread({ bucle() }, "tiresias-voz").apply {
		isDaemon = true
		start()
	}

	private class Pedido(val e: Enunciado, val texto: String, val idioma: Locale, val generacion: Int, val momento: Long = SystemClock.uptimeMillis())

	init {
		elegirMotor()
		vigilarGrabaciones()
	}

	/** Lee el motor elegido en Ajustes (y lo cambia si hace falta). */
	/**
	 * La voz por el volumen de ahora ([PistaAudio.multimedia]): la pista y la voz
	 * del sistema se rehacen con los atributos nuevos. Así se cambia en mitad de
	 * una llamada (One UI 8 calla el volumen de accesibilidad al subir el de la
	 * llamada por Bluetooth), sin volver a activar Tiresias.
	 */
	fun aplicarCanal() {
		atributos = PistaAudio.atributosVoz()
		pista.atributos = atributos
		sistema?.cerrar()
		sistema = null
		elegirMotor()
	}

	fun elegirMotor() {
		ajustes.normalizarEloquence()
		ajustes.ajustarVozAlMotor()
		PistaAudio.salida = ajustes.salidaAudio
		val id = ajustes.motor
		if (id.startsWith(MotorPiper.PREFIJO)) {
			sistema?.cerrar()
			sistema = null
			if (neuronal?.id != id) {
				neuronal?.cerrar()
				neuronal = MotorPiper.para(app, id, pista)
				if (neuronal == null) Registro.w(ETIQUETA, "La voz neuronal elegida no está descargada: se usa eSpeak")
				neuronal?.precargar()
			}
		} else if (id == Ajustes.MOTOR_ESPEAK || id == Ajustes.MOTOR_PICO || id == Ajustes.MOTOR_ELOQUENCE) {
			neuronal?.cerrar()
			neuronal = null
			sistema?.cerrar()
			sistema = null
		} else if (sistema?.id != id) {
			neuronal?.cerrar()
			neuronal = null
			sistema?.cerrar()
			sistema = MotorSistema(app, id, atributos).also { it.elegirVoz(ajustes.vozSistema) }
		} else sistema?.elegirVoz(ajustes.vozSistema)
		fallosSeguidos = 0
		apartadoHasta = 0
		avisadoFallo = false
		pista.ponerVolumen(ajustes.volumenVoz / 100f)
	}

	/** El nombre del motor que suena ahora. */
	/** El nombre del motor elegido (el que se usará en la siguiente frase). */
	val nombreMotor: String get() = motores().first().nombre

	val hablando: Boolean get() = enCurso != null || cerrojo.withLock { cola.isNotEmpty() }

	/** Si lo que suena ahora es una notificación (las dos teclas de volumen la callan). */
	val diciendoAviso: Boolean get() = enCurso?.e?.tipo == Tipo.NOTIFICACION

	/**
	 * Avisa (en el hilo de la voz) cuando la voz empieza a hablar y cuando lleva
	 * un rato callada: el sensor de proximidad solo se mira mientras habla.
	 */
	@Volatile var alCambiarHablando: ((Boolean) -> Unit)? = null

	/** Si el foco está en una contraseña o un dato sensible (lo pone el lector). */
	@Volatile var focoSensible = false
	private var activa = false

	private fun ponerActiva(si: Boolean) {
		if (activa == si) return
		activa = si
		runCatching { alCambiarHablando?.invoke(si) }.onFailure { Registro.w(ETIQUETA, "Fallo al avisar de que la voz habla o calla", it) }
	}

	/** Lo último que se dijo (para repetir o deletrear). */
	val ultimo: String? get() = synchronized(historial) { historial.lastOrNull() }

	/** Lo dicho recientemente, de lo más nuevo a lo más viejo. */
	fun historial(): List<String> = synchronized(historial) { historial.reversed() }

	/** Dice [e]. Se puede llamar desde cualquier hilo. */
	fun decir(e: Enunciado) {
		if (silenciada && e.tipo != Tipo.SISTEMA) {
			e.alTerminar?.invoke(false)
			return
		}
		tiposPermitidos?.let { permitidos ->
			if (e.tipo !in permitidos) {
				e.alTerminar?.invoke(false)
				return
			}
		}
		if (aDemanda && e.tipo in CALLADOS_A_DEMANDA && SystemClock.uptimeMillis() > pedidoHasta) {
			e.alTerminar?.invoke(false)
			return
		}
		if (grabando && ajustes.callarAlGrabar && e.tipo in setOf(Tipo.ANUNCIO, Tipo.NOTIFICACION, Tipo.PISTA)) {
			e.alTerminar?.invoke(false)
			return
		}
		val idioma = e.idioma ?: Idioma.efectivo(app)
		val texto = preparar(e, idioma)
		if (texto.isBlank()) {
			e.alTerminar?.invoke(true)
			return
		}
		if (e.tipo != Tipo.PISTA) recordar(texto)
		if (BuildConfig.PRUEBAS) {
			// Ni en esta variante se registra lo de una contraseña o un dato sensible (Android 14)
			val registrado = if (focoSensible && e.tipo in DEL_FOCO) "(dato sensible, ${texto.length} caracteres)" else texto
			Log.i(ETIQUETA_HABLA, "[${e.tipo}${e.idioma?.let { "|" + it.toLanguageTag() }.orEmpty()}] $registrado")
			idiomaPruebas = e.idioma
		}
		cerrojo.withLock {
			when (e.modo) {
				Modo.INTERRUMPIR -> {
					generacion.incrementAndGet()
					val descartados = cola.toList()
					cola.clear()
					motorEnCurso?.cortar()
					descartados.forEach { it.e.alTerminar?.let { f -> principal.post { f(false) } } }
				}
				Modo.SI_CALLADO -> if (enCurso != null || cola.isNotEmpty()) {
					e.alTerminar?.invoke(false)
					return
				}
				Modo.ENCOLAR -> Unit
			}
			// Lo largo, por frases: así «pausar» reanuda desde la frase en la que iba (y no desde el principio)
			val trozos = if (ajustes.trocearLargos && texto.length > LARGO && e.tipo != Tipo.ESCRITURA) trozos(texto) else listOf(texto)
			for ((i, t) in trozos.withIndex()) {
				val ultimo = i == trozos.lastIndex
				cola.addLast(Pedido(if (ultimo) e else e.copy(alTerminar = null), t, idioma, generacion.get()))
			}
			hayTrabajo.signal()
		}
	}

	/** Atajo: dice [texto] interrumpiendo. */
	fun decir(texto: String, tipo: Tipo = Tipo.NAVEGACION, modo: Modo = Modo.INTERRUMPIR) = decir(Enunciado(texto, modo = modo, tipo = tipo))

	/** Las frases de un texto largo (las muy largas, además, por comas). */
	private fun trozos(texto: String): List<String> {
		val frases = com.xebolax.tiresias.nucleo.Segmentos.frases(texto).map { it.texto }.filter { it.isNotBlank() }
		return frases.ifEmpty { listOf(texto) }
	}

	/** Lo que queda por decir (lo que suena ahora y lo que espera), para reanudarlo tras una pausa. */
	fun restante(): String? = cerrojo.withLock {
		val g = generacion.get()
		val partes = listOfNotNull(enCurso?.takeIf { it.generacion == g }?.texto) + cola.filter { it.generacion == g }.map { it.texto }
		partes.joinToString(" ").takeIf { it.isNotBlank() }
	}

	/** Las veces que se ha cortado la voz (para las pruebas: que nada la corte sin que nadie lo pida). */
	@Volatile var vecesCallada = 0
		private set

	/** Corta todo lo que suena y lo que espera. */
	fun callar() {
		vecesCallada++
		cerrojo.withLock {
			generacion.incrementAndGet()
			cola.clear()
			motorEnCurso?.cortar()
		}
	}

	/** Prueba que la voz habla de verdad con [motor] (Ajustes). */
	fun probar(texto: String) = decir(Enunciado(texto, tipo = Tipo.SISTEMA))

	fun cerrar() {
		terminar = true
		callar()
		cerrojo.withLock { hayTrabajo.signal() }
		runCatching { grabaciones?.let { audio.unregisterAudioRecordingCallback(it) } }
		sistema?.cerrar()
		neuronal?.cerrar()
		pico.cerrar()
		eloquence.cerrar()
		pista.cerrar()
		soltarFoco()
	}

	/** Un normalizador por idioma: crearlo carga tablas y recursos, y una frase no puede esperar a eso. */
	private val normalizadores = java.util.concurrent.ConcurrentHashMap<String, Normalizador>()

	/** Vuelve a cargar los textos (cambió el idioma de Tiresias o una tabla de símbolos). */
	fun invalidarTextos() = normalizadores.clear()

	private fun normalizador(idioma: Locale): Normalizador = normalizadores.getOrPut(idioma.language) {
		val datos = DatosIdioma.de(app, idioma)
		val textos = Idioma.envolver(app)
		Normalizador(datos.simbolos, PlantillasAndroid(app), datos.letras, textos.getString(R.string.voz_mayuscula), textos.getString(R.string.voz_espacio))
	}

	/** Cómo se dice un carácter suelto en el idioma de Tiresias («mayúscula a», «arroba»). */
	fun nombreCaracter(c: String): String =
		normalizador(Idioma.efectivo(app)).caracter(c, ajustes.ajustesTexto.copy(mayusculas = ModoMayusculas.PALABRA)).texto

	private fun preparar(e: Enunciado, idioma: Locale): String {
		val normalizador = normalizador(idioma)
		val ajustesTexto = ajustes.ajustesTexto.let { a -> puntuacionPerfil?.let { a.copy(puntuacion = it) } ?: a }
		if (e.deletrear) {
			// Un carácter suelto (navegación, eco) sigue el ajuste de mayúsculas; deletrear una frase siempre dice «mayúscula»
			val modo = if (e.texto.codePointCount(0, e.texto.length) == 1) ajustesTexto else ajustesTexto.copy(mayusculas = ModoMayusculas.PALABRA)
			return e.texto.codePoints().toArray().joinToString(", ") { cp ->
				normalizador.caracter(String(Character.toChars(cp)), modo).texto
			}
		}
		if (!e.preparar) return e.texto
		return normalizador.normalizar(e.texto, ajustesTexto, diccionario)
	}

	/** Cambios del perfil de la app de delante: velocidad y puntuación (los pone el lector). */
	@Volatile var velocidadPerfil: Int? = null
	@Volatile var puntuacionPerfil: com.xebolax.tiresias.nucleo.NivelPuntuacion? = null

	/** Diccionario general de pronunciación (lo carga el servicio; vacío hasta entonces). */
	@Volatile var diccionario: Diccionario = Diccionario.VACIO

	private fun recordar(texto: String) = synchronized(historial) {
		historial.addLast(texto)
		contadorPruebas++
		while (historial.size > MAXIMO_HISTORIAL) historial.removeFirst()
	}

	/** Cuántas frases se han dicho desde que arrancó (para que las pruebas vean solo lo nuevo). */
	@Volatile var contadorPruebas = 0

	/** El idioma pedido para la última frase (solo en la variante de pruebas; null: el de Tiresias). */
	@Volatile var idiomaPruebas: Locale? = null
		private set

	/** Lo dicho después de la marca [desde] (de [contadorPruebas]), de lo más nuevo a lo más viejo. */
	fun dichoDesde(desde: Int): List<String> = synchronized(historial) {
		val nuevos = (contadorPruebas - desde).coerceIn(0, historial.size)
		historial.toList().takeLast(nuevos).reversed()
	}

	/** Los motores a probar, en orden: el elegido (si no está apartado), eSpeak y Pico. */
	/** La voz de las notificaciones, si se ha elegido una incluida distinta de la de siempre; null, la de siempre. */
	private fun motorDeAvisos(): Motor? {
		val m = ajustes.motorAvisos
		if (m.isEmpty() || m == ajustes.motor) return null
		return when (m) {
			Ajustes.MOTOR_ESPEAK -> espeak
			Ajustes.MOTOR_PICO -> pico
			Ajustes.MOTOR_ELOQUENCE -> eloquence.takeIf { BibliotecaEloquence.disponible(app) }
			else -> null
		}
	}

	private fun motores(): List<Motor> {
		val lista = mutableListOf<Motor>()
		neuronal?.let { lista += it }
		val s = sistema
		if (s != null && SystemClock.uptimeMillis() >= apartadoHasta) lista += s
		// Eloquence, si es la elegida; si falla (o no hay para este procesador), eSpeak y Pico
		if (ajustes.motor == Ajustes.MOTOR_ELOQUENCE) lista += eloquence
		if (ajustes.motor == Ajustes.MOTOR_PICO) {
			lista += pico
			lista += espeak
		} else {
			lista += espeak
			lista += pico
		}
		return lista.distinct()
	}

	private fun bucle() {
		Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
		while (!terminar) {
			val pedido = cerrojo.withLock {
				while (cola.isEmpty() && !terminar) {
					// Callada: suelta el foco de audio y, al rato, la pista
					if (!hayTrabajo.await(ESPERA_CALLADA_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
						soltarFoco()
						ponerActiva(false)
						pista.cerrarSiCallada(CERRAR_PISTA_MS)
					}
				}
				if (terminar) return
				cola.removeFirst().also { enCurso = it }
			}
			// Lo de un foco que ya ha pasado no se dice
			if (runCatching { pedido.e.vigente?.invoke() }.getOrDefault(true) == false) {
				enCurso = null
				continue
			}
			ponerActiva(true)
			try {
				hablar(pedido)
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "Fallo inesperado al hablar", e)
			} finally {
				enCurso = null
				motorEnCurso = null
			}
		}
	}

	private fun hablar(p: Pedido) {
		val sigue = { p.generacion == generacion.get() && !terminar }
		if (!sigue()) {
			p.e.alTerminar?.invoke(false)
			return
		}
		pedirFoco()
		// Las notificaciones pueden ir con otra voz (Prudence), con la velocidad que tenga esa voz
		val deAvisos = if (p.e.tipo == Tipo.NOTIFICACION) motorDeAvisos() else null
		val velocidad = (if (deAvisos != null) ajustes.velocidadDe(ajustes.motorAvisos) else velocidadPerfil ?: ajustes.velocidad) * p.e.velocidad / 100
		val tono = ajustes.tono * p.e.tono / 100
		var resultado = Resultado.FALLO
		for (motor in deAvisos?.let { listOf(it, espeak) } ?: motores()) {
			if (!sigue()) {
				resultado = Resultado.CORTADO
				break
			}
			motorEnCurso = motor
			resultado = motor.hablar(p.texto, p.idioma, velocidad, tono, sigue) {
				// Latencia desde que se pidió decirlo hasta que empieza a sonar (solo en la variante de pruebas)
				ultimoInicioSonido = SystemClock.uptimeMillis()
				ultimoMotor = motor.nombre
				if (BuildConfig.PRUEBAS) Log.i(ETIQUETA_LATENCIA, "${ultimoInicioSonido - p.momento} ms ${motor.nombre}")
				p.e.alEmpezar?.invoke()
			}
			if (motor is MotorSistema) anotar(motor, resultado)
			if (motor is MotorEloquence && resultado == Resultado.FALLO && !avisadoEloquence) {
				avisadoEloquence = true
				val aviso = Idioma.envolver(app).getString(R.string.voz_motor_fallo, motor.nombre)
				cerrojo.withLock { cola.addFirst(Pedido(Enunciado(aviso, Modo.ENCOLAR, Tipo.SISTEMA), aviso, Idioma.efectivo(app), generacion.get())) }
			}
			if (resultado != Resultado.FALLO) break
			Registro.w(ETIQUETA, "${motor.nombre} no pudo hablar: se prueba la siguiente voz")
		}
		if (resultado == Resultado.FALLO) Registro.e(ETIQUETA, "Ninguna voz pudo hablar")
		p.e.alTerminar?.invoke(resultado == Resultado.HABLADO)
	}

	/** Si ya se ha avisado de que Eloquence falla (una vez por arranque). */
	@Volatile private var avisadoEloquence = false

	private fun anotar(motor: MotorSistema, r: Resultado) {
		if (r == Resultado.FALLO) {
			if (++fallosSeguidos >= FALLOS_PARA_APARTAR) {
				apartadoHasta = SystemClock.uptimeMillis() + APARTADO_MS
				fallosSeguidos = 0
				motor.reintentar()
				if (!avisadoFallo) {
					avisadoFallo = true
					val aviso = Idioma.envolver(app).getString(R.string.voz_motor_fallo, motor.nombre)
					cerrojo.withLock { cola.addFirst(Pedido(Enunciado(aviso, Modo.ENCOLAR, Tipo.SISTEMA), aviso, Idioma.efectivo(app), generacion.get())) }
				}
			}
		} else if (r == Resultado.HABLADO) {
			fallosSeguidos = 0
			avisadoFallo = false
		}
	}

	private fun pedirFoco() {
		if (!ajustes.atenuar || Build.VERSION.SDK_INT < 26 || foco != null) return
		// Con un asistente hablando (Gemini) o una llamada por internet, pedir el foco los corta: se habla encima
		val delicado = runCatching {
			audio.activePlaybackConfigurations.any {
				it.audioAttributes.usage == android.media.AudioAttributes.USAGE_ASSISTANT || it.audioAttributes.usage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
			}
		}.getOrDefault(false)
		if (delicado) return
		try {
			val peticion = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
				.setAudioAttributes(atributos)
				.setOnAudioFocusChangeListener { }
				.build()
			if (audio.requestAudioFocus(peticion) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) foco = peticion
			else focoDenegado()
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pudo pedir el foco de audio", e)
		}
	}

	/** La última vez que Android negó el foco de audio (para avisar como mucho una vez por minuto). */
	private var momentoFocoDenegado = 0L

	/**
	 * Android ha negado el foco de audio: en Android 17 el audio que no lo tiene
	 * puede no sonar, sin dar error. Se apunta y se avisa con una vibración corta,
	 * como mucho una vez por minuto, para que no parezca que Tiresias se ha callado sin más.
	 */
	private fun focoDenegado() {
		val ahora = SystemClock.uptimeMillis()
		if (ahora - momentoFocoDenegado < 60_000) return
		momentoFocoDenegado = ahora
		Registro.w(ETIQUETA, "Android no dio el foco de audio a la voz")
		runCatching {
			val v = app.getSystemService(android.os.Vibrator::class.java)
			if (Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
		}.onFailure { Registro.w(ETIQUETA, "No se pudo avisar con vibración", it) }
	}

	private fun soltarFoco() {
		val f = foco ?: return
		foco = null
		if (Build.VERSION.SDK_INT >= 26) runCatching { audio.abandonAudioFocusRequest(f) }
	}

	private var grabaciones: AudioManager.AudioRecordingCallback? = null

	private fun vigilarGrabaciones() {
		try {
			val cb = object : AudioManager.AudioRecordingCallback() {
				override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
					grabando = !configs.isNullOrEmpty()
				}
			}
			audio.registerAudioRecordingCallback(cb, principal)
			grabaciones = cb
			grabando = audio.activeRecordingConfigurations.isNotEmpty()
		} catch (e: Exception) {
			Registro.w(ETIQUETA, "No se pueden vigilar las grabaciones", e)
		}
	}

	companion object {
		/** Lo que no se dice en voz a demanda si no se ha pedido. */
		private val CALLADOS_A_DEMANDA = setOf(Tipo.NAVEGACION, Tipo.NOTIFICACION, Tipo.ANUNCIO, Tipo.PISTA, Tipo.ESCRITURA)
		/** A partir de cuántos caracteres un texto se dice por frases. */
		private const val LARGO = 200
		private const val ETIQUETA = "Voz"
		/** Etiqueta del registro de lo dicho (solo en la variante de pruebas). */
		const val ETIQUETA_HABLA = "TiresiasHabla"
		/** Etiqueta de la latencia medida (solo en la variante de pruebas). */
		const val ETIQUETA_LATENCIA = "TiresiasLatencia"
		private const val MAXIMO_HISTORIAL = 100
		private const val FALLOS_PARA_APARTAR = 3
		private const val APARTADO_MS = 60_000L
		private const val ESPERA_CALLADA_MS = 400L
		/** Lo que se dice del elemento enfocado y de lo que se escribe en él. */
		private val DEL_FOCO = setOf(Tipo.NAVEGACION, Tipo.ESCRITURA)
		private const val CERRAR_PISTA_MS = 5_000L
	}
}
