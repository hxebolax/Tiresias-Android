package com.xebolax.tiresias.voz

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Almacen
import com.xebolax.tiresias.base.Registro
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Los sonidos de Tiresias: cada uno dice algo sin palabras. */
enum class Sonido {
	/** Un elemento recibe el foco al deslizar. */
	FOCO,
	/** El dedo pasa por un elemento al explorar. */
	TOQUE,
	ACTIVAR,
	MANTENER,
	/** La lista se ha desplazado. */
	DESPLAZAR,
	/** No hay más elementos en esa dirección. */
	BORDE,
	/** Se ha dado la vuelta (del último al primero). */
	VUELTA,
	/** Nueva ventana o pantalla. */
	VENTANA,
	ERROR,
	/** El elemento tiene acciones propias (petición de Backtalk 267). */
	ACCIONES,
	/** Cambio de control de lectura en el selector. */
	SELECTOR,
	NOTIFICACION,
	/** Tiresias se ha encendido. */
	ENCENDIDO,
	APAGADO,
	/** Gesto reconocido (al empezar la acción). */
	GESTO,
	/** Tecla del teclado en pantalla. */
	TECLA,
	/** Un campo de texto o el cursor al principio o al final. */
	EDICION,
	CARGANDO,
	/** Al deslizar se pasa del final de una fila al principio de la siguiente (iconos, celdas), SandFly. */
	FILA,
}

/**
 * Reproduce los [Sonido]s con `SoundPool` (latencia mínima y mezcla con la voz).
 *
 * Los sonidos se generan por código la primera vez (tonos cortos con
 * envolvente), así no hay archivos de sonido con licencias ajenas. Un tema de
 * sonidos (importado o de la tienda) y los archivos propios de cada sonido
 * mandan sobre ellos: ver [TemasSonido].
 *
 * [posicion] (0 izquierda, 1 derecha) mueve el sonido en estéreo si el usuario
 * activa el sonido posicional; [tono] lo sube o baja (posición en una lista).
 */
class Sonidos(contexto: Context) {
	private val app = contexto.applicationContext
	private val ajustes = Ajustes(app)
	@Volatile private var pool: SoundPool = nuevoPool()

	private fun nuevoPool(): SoundPool = SoundPool.Builder()
		.setMaxStreams(4)
		.setAudioAttributes(
			AudioAttributes.Builder()
				.setUsage(if (Build.VERSION.SDK_INT >= 26 && !PistaAudio.multimedia) AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY else AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.build()
		)
		.build()

	/** Los sonidos por el volumen de ahora ([PistaAudio.multimedia]): un SoundPool nuevo con los mismos sonidos. En un hilo de fondo. */
	fun cambiarCanal() {
		val viejo = pool
		cargados = false
		pool = nuevoPool()
		ids.clear()
		cargar()
		runCatching { viejo.release() }
	}
	private val ids = HashMap<Sonido, Int>()
	@Volatile private var cargados = false

	/** Prepara los sonidos (en un hilo de fondo: genera los archivos la primera vez); se puede volver a llamar al cambiar de tema. */
	@Synchronized
	fun cargar() {
		try {
			val dir = File(Almacen.protegido(app).noBackupFilesDir, "sonidos/$VERSION").apply { mkdirs() }
			val tema = ajustes.temaSonidos
			val anteriores = ids.values.toList()
			for (s in Sonido.entries) {
				val archivo = efectivo(s, tema, dir)
				ids[s] = pool.load(archivo.path, 1)
			}
			anteriores.forEach { runCatching { pool.unload(it) } }
			cargados = true
		} catch (e: Exception) {
			Registro.e(ETIQUETA, "No se pudieron preparar los sonidos", e)
		}
	}

	/** El archivo que suena para [s]: el propio, el del tema o el generado. */
	fun efectivo(s: Sonido, tema: String = ajustes.temaSonidos, dir: File = File(Almacen.protegido(app).noBackupFilesDir, "sonidos/$VERSION").apply { mkdirs() }): File =
		TemasSonido.archivoDe(app, tema, s) ?: File(dir, s.name.lowercase() + ".wav").also { if (!it.isFile) generar(s, it) }

	/** Lo que hace cada sonido además de sonar: vibrar con su patrón (lo pone el servicio). */
	@Volatile var alSonar: ((Sonido) -> Unit)? = null

	fun tocar(s: Sonido, posicion: Float = 0.5f, tono: Float = 1f) {
		alSonar?.invoke(s)
		if (!cargados || !ajustes.sonidos || s.name in ajustes.sonidosApagados) return
		val id = ids[s] ?: return
		val v = ajustes.volumenSonidos / 100f
		val (izq, der) = if (ajustes.sonidoPosicional) {
			val p = posicion.coerceIn(0f, 1f)
			v * minOf(1f, 2f * (1f - p)) to v * minOf(1f, 2f * p)
		} else v to v
		runCatching { pool.play(id, izq, der, 1, 0, tono.coerceIn(0.5f, 2f)) }
		sonados++
		porSonido[s.ordinal]++
	}

	/** Los sonidos que han sonado de verdad, en total y de cada uno (para las pruebas: que nada deje de sonar). */
	@Volatile var sonados = 0
		private set
	private val porSonido = IntArray(Sonido.entries.size)

	fun vecesSonado(s: Sonido): Int = porSonido[s.ordinal]

	fun cerrar() = runCatching { pool.release() }

	private fun generar(s: Sonido, destino: File) {
		// (frecuencias en Hz, duración de cada nota en ms, decaimiento)
		val notas: List<Triple<Double, Int, Double>> = when (s) {
			Sonido.FOCO -> listOf(Triple(1320.0, 28, 9.0))
			Sonido.TOQUE -> listOf(Triple(2200.0, 12, 14.0))
			Sonido.ACTIVAR -> listOf(Triple(880.0, 40, 7.0), Triple(1320.0, 50, 7.0))
			Sonido.MANTENER -> listOf(Triple(660.0, 60, 4.0), Triple(990.0, 70, 5.0))
			Sonido.DESPLAZAR -> listOf(Triple(700.0, 30, 3.0), Triple(900.0, 30, 3.0), Triple(1100.0, 40, 5.0))
			Sonido.BORDE -> listOf(Triple(330.0, 60, 5.0), Triple(262.0, 80, 5.0))
			Sonido.VUELTA -> listOf(Triple(1320.0, 40, 6.0), Triple(660.0, 60, 6.0))
			Sonido.VENTANA -> listOf(Triple(784.0, 50, 5.0), Triple(1047.0, 80, 4.0))
			Sonido.ERROR -> listOf(Triple(196.0, 140, 3.0))
			Sonido.ACCIONES -> listOf(Triple(1568.0, 25, 9.0), Triple(2093.0, 30, 9.0))
			Sonido.SELECTOR -> listOf(Triple(1047.0, 35, 7.0))
			Sonido.NOTIFICACION -> listOf(Triple(988.0, 70, 4.0), Triple(1319.0, 110, 4.0))
			Sonido.ENCENDIDO -> listOf(Triple(523.0, 80, 4.0), Triple(659.0, 80, 4.0), Triple(784.0, 140, 3.0))
			Sonido.APAGADO -> listOf(Triple(784.0, 80, 4.0), Triple(659.0, 80, 4.0), Triple(523.0, 140, 3.0))
			Sonido.GESTO -> listOf(Triple(1760.0, 18, 12.0))
			Sonido.TECLA -> listOf(Triple(2637.0, 10, 16.0))
			Sonido.EDICION -> listOf(Triple(440.0, 40, 8.0))
			Sonido.CARGANDO -> listOf(Triple(600.0, 60, 2.0), Triple(600.0, 60, 2.0))
			Sonido.FILA -> listOf(Triple(1175.0, 22, 8.0), Triple(880.0, 30, 8.0))
		}
		val hz = 22050
		val muestras = ArrayList<Short>()
		for ((f, ms, decaimiento) in notas) {
			val n = hz * ms / 1000
			for (i in 0 until n) {
				val t = i.toDouble() / hz
				// Ataque de 2 ms y caída exponencial: sin chasquidos
				val ataque = minOf(1.0, i / (hz * 0.002))
				val envolvente = ataque * exp(-decaimiento * i / n)
				val v = sin(2 * PI * f * t) * 0.55 + sin(4 * PI * f * t) * 0.12
				muestras += (v * envolvente * 32767 * 0.8).toInt().coerceIn(-32768, 32767).toShort()
			}
		}
		escribirWav(destino, muestras, hz)
	}

	private fun escribirWav(destino: File, muestras: List<Short>, hz: Int) {
		val parcial = File(destino.path + ".parcial")
		DataOutputStream(FileOutputStream(parcial).buffered()).use { o ->
			val datos = muestras.size * 2
			fun i32(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
			fun i16(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
			o.writeBytes("RIFF"); i32(36 + datos); o.writeBytes("WAVE")
			o.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(hz); i32(hz * 2); i16(2); i16(16)
			o.writeBytes("data"); i32(datos)
			for (m in muestras) i16(m.toInt())
		}
		parcial.renameTo(destino)
	}

	companion object {
		private const val ETIQUETA = "Sonidos"
		/** Sube si cambian los sonidos generados (se vuelven a generar). */
		private const val VERSION = 1
	}
}

/** Las vibraciones de Tiresias: cortas, distintas por evento, con la intensidad que elija el usuario. */
class Vibracion(contexto: Context) {
	private val app = contexto.applicationContext
	private val ajustes = Ajustes(app)
	private val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
		app.getSystemService(VibratorManager::class.java)?.defaultVibrator
	} else @Suppress("DEPRECATION") app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

	/** Empezar a vibrar espera al sistema: en un hilo propio, para no retrasar la voz (SandFly). */
	private val hilo = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "tiresias-vibracion").apply { isDaemon = true } }

	/** Si el vibrador tiene las primitivas de composición que se usan (Android 11+): se mira una vez. */
	private val conPrimitivas: Boolean by lazy {
		Build.VERSION.SDK_INT >= 30 && runCatching {
			vibrador?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK) == true
		}.getOrDefault(false)
	}

	fun vibrar(s: Sonido) {
		if (!ajustes.vibracion || s.name in ajustes.vibracionesApagadas) return
		val v = vibrador ?: return
		runCatching { hilo.execute { vibrarYa(v, s) } }.onFailure { Registro.w("Vibracion", "No se pudo pasar al hilo de la vibración", it) }
	}

	private fun vibrarYa(v: Vibrator, s: Sonido) {
		if (!v.hasVibrator()) return
		val intensidad = ajustes.intensidadVibracion
		try {
			if (conPrimitivas && Build.VERSION.SDK_INT >= 30) {
				v.vibrate(composicion(patron(s), intensidad))
				return
			}
			val ms = duraciones(patron(s), Build.VERSION.SDK_INT >= 26 && v.hasAmplitudeControl(), intensidad)
			if (Build.VERSION.SDK_INT >= 26) {
				val amplitud = (intensidad * 255 / 100).coerceIn(1, 255)
				val amplitudes = IntArray(ms.size) { if (it % 2 == 1) amplitud else 0 }
				v.vibrate(VibrationEffect.createWaveform(ms, amplitudes, -1))
			} else @Suppress("DEPRECATION") v.vibrate(ms, -1)
		} catch (e: Exception) {
			Registro.w("Vibracion", "No se pudo vibrar", e)
		}
	}

	/**
	 * El patrón con las primitivas del vibrador (más nítidas que una onda): cada pulso corto, un TICK; cada
	 * pulso largo, un CLICK; con la misma pausa delante. La intensidad elegida es su fuerza.
	 */
	@androidx.annotation.RequiresApi(30)
	private fun composicion(patron: LongArray, intensidad: Int): VibrationEffect {
		val c = VibrationEffect.startComposition()
		val fuerza = (intensidad.coerceIn(0, 100) / 100f).coerceAtLeast(0.05f)
		var i = 1
		while (i < patron.size) {
			val pausa = patron[i - 1].toInt().coerceIn(0, 1000)
			val largo = patron[i] > PULSO_CORTO_MS
			val primitiva = if (largo) VibrationEffect.Composition.PRIMITIVE_CLICK else VibrationEffect.Composition.PRIMITIVE_TICK
			c.addPrimitive(primitiva, (fuerza * if (largo) 1f else 0.8f).coerceIn(0.05f, 1f), pausa)
			i += 2
		}
		return c.compose()
	}

	/**
	 * Una vibración distinta para cada sonido (Backtalk 21 y 29), para quien
	 * quita los sonidos: pausas y pulsos en milisegundos, empezando por la pausa.
	 */
	private fun patron(s: Sonido): LongArray = when (s) {
		Sonido.FOCO -> longArrayOf(0, 12)
		Sonido.TOQUE -> longArrayOf(0, 7)
		Sonido.TECLA -> longArrayOf(0, 9)
		Sonido.GESTO -> longArrayOf(0, 16)
		Sonido.ACTIVAR -> longArrayOf(0, 28)
		Sonido.SELECTOR -> longArrayOf(0, 14, 35, 14)
		Sonido.MANTENER -> longArrayOf(0, 55)
		Sonido.DESPLAZAR -> longArrayOf(0, 8, 30, 8, 30, 8)
		Sonido.BORDE -> longArrayOf(0, 22, 60, 22)
		Sonido.VUELTA -> longArrayOf(0, 15, 40, 40)
		Sonido.VENTANA -> longArrayOf(0, 34)
		Sonido.NOTIFICACION -> longArrayOf(0, 30, 50, 30, 50, 30)
		Sonido.ERROR -> longArrayOf(0, 80, 60, 80)
		Sonido.ACCIONES -> longArrayOf(0, 12, 30, 26)
		Sonido.ENCENDIDO -> longArrayOf(0, 40, 70, 40, 70, 60)
		Sonido.APAGADO -> longArrayOf(0, 60, 70, 40, 70, 20)
		Sonido.EDICION -> longArrayOf(0, 14, 25, 14)
		Sonido.CARGANDO -> longArrayOf(0, 10, 90, 10)
		Sonido.FILA -> longArrayOf(0, 10, 25, 18)
	}

	companion object {
		/**
		 * Los milisegundos de [patron] para este vibrador. Un pulso muy corto no se nota: el motor de los
		 * móviles sencillos (sin control de intensidad) ni llega a arrancar con menos de unos 25 ms, y ahí la
		 * intensidad elegida alarga o acorta los pulsos (no hay otra forma de cambiarla). Con control de
		 * intensidad, la intensidad va en la fuerza y el pulso dura al menos 12 ms.
		 */
		fun duraciones(patron: LongArray, conIntensidad: Boolean, intensidad: Int): LongArray = LongArray(patron.size) { i ->
			if (i % 2 == 0) patron[i]
			else if (conIntensidad) maxOf(MINIMO_CON_INTENSIDAD_MS, patron[i])
			else maxOf(MINIMO_SIN_INTENSIDAD_MS, maxOf(MINIMO_SIN_INTENSIDAD_MS, patron[i]) * (50 + intensidad.coerceIn(0, 100)) / 100)
		}

		private const val MINIMO_CON_INTENSIDAD_MS = 12L
		private const val MINIMO_SIN_INTENSIDAD_MS = 25L
		/** Hasta cuánto un pulso es corto (un TICK) al componer con primitivas. */
		private const val PULSO_CORTO_MS = 20L
	}

	/** Vibra [veces] pulsos (cuenta posiciones sin voz, último recurso si no hay sonido). */
	fun contar(veces: Int) {
		val v = vibrador ?: return
		val ms = LongArray(veces * 2) { if (it % 2 == 0) 120 else 60 }
		runCatching {
			if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(ms, -1)) else @Suppress("DEPRECATION") v.vibrate(ms, -1)
		}
	}
}
