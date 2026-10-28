package com.xebolax.tiresias.vision

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.Display
import com.xebolax.tiresias.R
import com.xebolax.tiresias.arbol.Nodos
import com.xebolax.tiresias.base.Ajustes
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.ia.AsistenteIA
import com.xebolax.tiresias.ia.Nano
import com.xebolax.tiresias.lectura.Lector
import com.xebolax.tiresias.nucleo.PropuestaIA
import com.xebolax.tiresias.ventanas.Ventanas
import com.xebolax.tiresias.voz.Modo
import com.xebolax.tiresias.voz.Sonido
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lo que Tiresias «ve»: capturas de pantalla, reconocimiento de texto e IA.
 *
 * - **OCR** del elemento o de la pantalla, sin Internet ([Ocr]). El resultado
 *   es una lista de líneas: al elegir una, Tiresias la toca en la pantalla
 *   (para apps y juegos que no se pueden leer de otra forma).
 * - **IA**: describir el elemento (una imagen, un botón sin nombre), la
 *   pantalla entera, o preguntar sobre lo que hay en ella.
 *
 * Las capturas usan `takeScreenshot` (Android 11 o posterior), sin pedir
 * permiso de grabación. Los campos de contraseña nunca se mandan: si el foco
 * está en uno, no se hace nada.
 */
class Vision(private val lector: Lector, private val ventanas: Ventanas) {
	private val servicio = lector.servicio
	private val t get() = lector.textos
	private val trabajo = Executors.newSingleThreadExecutor { Thread(it, "tiresias-vision").apply { priority = Thread.NORM_PRIORITY } }
	/** El OCR se crea al usarlo: cargar ONNX Runtime al arrancar el lector gastaría memoria y tiempo. */
	private val ocrPerezoso = lazy { Ocr(servicio) }
	val ocr by ocrPerezoso

	/** El sonido de «trabajando» mientras la IA piensa o se reconoce el texto (como TalkBack con Gemini). */
	val espera = EsperaSonora(lector)

	/** Nombres para los botones sin etiqueta (OCR o IA del móvil). */
	val iconos = Iconos(lector, this)

	// --- Captura -------------------------------------------------------------------------

	/** El motivo de la última captura que falló (el código de Android), o 0. */
	@Volatile private var errorCaptura = 0

	/**
	 * Una captura de la pantalla (o null si no se puede). Bloquea hasta 4 s. Si
	 * Android dice que se ha pedido otra hace muy poco (hay un mínimo entre
	 * capturas), se espera un segundo y se prueba otra vez.
	 */
	fun capturar(): Bitmap? {
		if (Build.VERSION.SDK_INT < 30) return null
		val primera = capturarUnaVez()
		if (primera != null || errorCaptura != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) return primera
		Thread.sleep(1000)
		return capturarUnaVez()
	}

	/** Lo que se dice cuando no se pudo capturar la pantalla, según lo que dijo Android. */
	private fun motivoSinCaptura(): Int = when (errorCaptura) {
		AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> R.string.vision_captura_protegida
		AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> R.string.vision_captura_seguida
		AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> R.string.vision_captura_sin_permiso
		else -> R.string.vision_sin_captura
	}

	@androidx.annotation.RequiresApi(30)
	private fun capturarUnaVez(): Bitmap? {
		errorCaptura = 0
		val listo = CountDownLatch(1)
		var resultado: Bitmap? = null
		servicio.takeScreenshot(Display.DEFAULT_DISPLAY, trabajoCaptura, object : AccessibilityService.TakeScreenshotCallback {
			override fun onSuccess(captura: AccessibilityService.ScreenshotResult) {
				try {
					val buffer = captura.hardwareBuffer
					val hw = Bitmap.wrapHardwareBuffer(buffer, captura.colorSpace)
					resultado = hw?.copy(Bitmap.Config.ARGB_8888, false)
					hw?.recycle()
					buffer.close()
				} catch (e: Exception) {
					Registro.e(ETIQUETA, "No se pudo leer la captura", e)
				}
				listo.countDown()
			}

			override fun onFailure(error: Int) {
				errorCaptura = error
				Registro.w(ETIQUETA, "La captura falló (código $error)")
				listo.countDown()
			}
		})
		listo.await(3, TimeUnit.SECONDS)
		return resultado
	}

	private val trabajoCaptura = Executors.newSingleThreadExecutor()

	private fun recortar(b: Bitmap, r: Rect): Bitmap? {
		val c = Rect(r)
		if (!c.intersect(0, 0, b.width, b.height) || c.width() < 4 || c.height() < 4) return null
		return Bitmap.createBitmap(b, c.left, c.top, c.width(), c.height())
	}

	private fun jpeg(b: Bitmap, lado: Int = 1600): File {
		val f = File(servicio.cacheDir, "vision/captura.jpg").apply { parentFile?.mkdirs() }
		// Como mucho 1600 píxeles de lado: suficiente para describir y mucho más ligero de enviar
		val escala = minOf(1f, lado.toFloat() / maxOf(b.width, b.height))
		val img = if (escala < 1f) Bitmap.createScaledBitmap(b, (b.width * escala).toInt(), (b.height * escala).toInt(), true) else b
		FileOutputStream(f).use { img.compress(Bitmap.CompressFormat.JPEG, 85, it) }
		if (img !== b) img.recycle()
		return f
	}

	private fun necesitaCaptura(): Boolean {
		if (Build.VERSION.SDK_INT < 30) {
			lector.decir(R.string.vision_android11)
			return false
		}
		if (lector.foco?.isPassword == true) {
			lector.decir(R.string.vision_contrasena)
			return false
		}
		// Lo que la app marca como datos sensibles (banca, códigos) no sale del móvil ni se reconoce
		if (lector.foco?.let { Nodos.sensible(it) } == true || Nodos.haySensibles(lector.raiz())) {
			lector.decir(R.string.vision_sensible)
			return false
		}
		return true
	}

	// --- OCR ----------------------------------------------------------------------------

	fun ocrPantalla(): Boolean = ocr(null)

	// --- Códigos QR y de barras ---------------------------------------------------------------

	/** Lee los códigos QR y de barras de la pantalla y ofrece abrirlos o copiarlos. */
	fun leerCodigos(): Boolean {
		if (!necesitaCaptura()) return false
		lector.decir(R.string.qr_buscando, modo = Modo.INTERRUMPIR)
		trabajo.execute {
			try {
				val captura = capturar() ?: run {
					val motivo = motivoSinCaptura()
					lector.manejador.post { lector.decir(motivo) }
					return@execute
				}
				val codigos = Codigos.leer(captura)
				captura.recycle()
				lector.manejador.post { ofrecerCodigos(codigos) }
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "No se pudieron leer los códigos", e)
				lector.manejador.post { lector.decir(R.string.qr_error) }
			}
		}
		return true
	}

	private fun ofrecerCodigos(codigos: List<Codigos.Codigo>) {
		when (codigos.size) {
			0 -> lector.decir(R.string.qr_ninguno)
			1 -> opcionesCodigo(codigos[0])
			else -> ventanas.lista(t.resources.getQuantityString(R.plurals.qr_varios, codigos.size, codigos.size), codigos.map { it.texto }) { i ->
				lector.manejador.post { opcionesCodigo(codigos[i]) }
			}
		}
	}

	/** Dice el código y ofrece abrirlo (si es una web, un teléfono...), copiarlo o decirlo otra vez. */
	private fun opcionesCodigo(c: Codigos.Codigo) {
		val tipo = t.getString(if (c.qr) R.string.qr_tipo_qr else R.string.qr_tipo_barras)
		val opciones = ArrayList<Pair<String, () -> Unit>>()
		if (Codigos.abrible(c.texto)) opciones += t.getString(R.string.qr_abrir) to { abrirCodigo(c.texto) }
		opciones += t.getString(R.string.qr_copiar) to { lector.edicion.portapapeles(c.texto); lector.decir(R.string.qr_copiado) }
		opciones += t.getString(R.string.qr_decir) to { lector.decir(c.texto) }
		lector.decir(t.getString(R.string.qr_leido, tipo, c.texto))
		ventanas.lista(t.getString(R.string.qr_titulo, tipo), opciones.map { it.first }) { i -> lector.manejador.post { opciones[i].second() } }
	}

	private fun abrirCodigo(texto: String) {
		try {
			servicio.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(texto.trim())).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
		} catch (e: android.content.ActivityNotFoundException) {
			Registro.w(ETIQUETA, "No hay app para abrir el código", e)
			lector.decir(R.string.qr_sin_app)
		}
	}

	/** Reconoce el texto de la pantalla y lo pone como pantalla virtual (o la quita si ya está). */
	fun pantallaVirtual(): Boolean {
		if (lector.virtual != null) {
			lector.salirVirtual(true)
			return true
		}
		return ocr(null, virtual = true)
	}

	fun ocrElemento(): Boolean = ocr(lector.foco?.let { Nodos.limites(it) })

	private fun ocr(zona: Rect?, virtual: Boolean = false): Boolean {
		if (!necesitaCaptura()) return false
		if (!ocr.disponible) {
			lector.decir(R.string.vision_ocr_falta)
			return false
		}
		espera.empezar()
		lector.decir(R.string.vision_reconociendo, modo = Modo.INTERRUMPIR)
		val previo = lector.foco
		trabajo.execute {
			try {
				val captura = capturar() ?: run {
					val motivo = motivoSinCaptura()
					lector.manejador.post { lector.decir(motivo) }
					return@execute
				}
				val imagen = zona?.let { recortar(captura, it) } ?: captura
				val lineas = ocr.reconocer(imagen, zona?.takeIf { imagen !== captura })
				if (imagen !== captura) imagen.recycle()
				captura.recycle()
				lector.manejador.post {
					if (lineas.isEmpty()) {
						lector.decir(R.string.vision_sin_texto)
						return@post
					}
					if (virtual) {
						lector.entrarVirtual(lineas.map { it.texto to it.rect })
						return@post
					}
					// Una lista de líneas: al elegir una, se toca en la pantalla
					ventanas.lista(t.resources.getQuantityString(R.plurals.vision_titulo_ocr, lineas.size, lineas.size), lineas.map { it.texto }, {
						previo?.let { p -> lector.manejador.postDelayed({ if (Nodos.vivo(p)) lector.enfocar(p, null) }, 250) }
					}) { i -> lector.manejador.postDelayed({ tocar(lineas[i].rect) }, 400) }
				}
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "El reconocimiento de texto falló", e)
				lector.manejador.post { lector.decir(R.string.vision_ocr_error) }
			} finally {
				espera.parar()
			}
		}
		return true
	}

	/** Toca el centro de [r] (doble toque sobre una línea reconocida). */
	private fun tocar(r: Rect) {
		val camino = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
		val gesto = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(camino, 0, 60)).build()
		lector.sonidos.tocar(Sonido.ACTIVAR)
		lector.mandarGesto(gesto)
	}

	// --- IA ---------------------------------------------------------------------------------

	fun describirElemento(): Boolean = describir(lector.foco?.let { Nodos.limites(it) }, t.getString(R.string.ia_pedir_elemento), R.string.ia_titulo_elemento)

	fun describirPantalla(): Boolean = describir(null, t.getString(R.string.ia_pedir_pantalla), R.string.ia_titulo_pantalla)

	fun preguntar(): Boolean {
		if (!necesitaCaptura()) return false
		val previo = lector.foco
		ventanas.pedirTexto(t.getString(R.string.ia_titulo_pregunta), t.getString(R.string.ia_pista_pregunta), alCancelar = {
			previo?.let { lector.manejador.postDelayed({ if (Nodos.vivo(it)) lector.enfocar(it, null) }, 250) }
		}) { pregunta ->
			if (pregunta.isNotBlank()) lector.manejador.postDelayed({ describir(null, pregunta, R.string.ia_titulo_pregunta) }, 400)
		}
		return true
	}

	private fun describir(zona: Rect?, pregunta: String, titulo: Int): Boolean {
		if (!necesitaCaptura()) return false
		val proveedor = AsistenteIA.elegir(servicio)
		if (proveedor == null) {
			lector.decir(R.string.ia_sin_configurar)
			return false
		}
		confirmarEnvio(proveedor) {
			espera.empezar()
			lector.decir(t.getString(R.string.ia_pensando, AsistenteIA.nombre(servicio, proveedor)), Modo.INTERRUMPIR)
			val previo = lector.foco
			trabajo.execute {
				try {
					val captura = capturar() ?: run {
					val motivo = motivoSinCaptura()
					lector.manejador.post { lector.decir(motivo) }
					return@execute
				}
					val imagen = zona?.let { recortar(captura, it) } ?: captura
					val archivo = jpeg(imagen)
					if (imagen !== captura) imagen.recycle()
					captura.recycle()
					if (proveedor == AsistenteIA.Proveedor.MOVIL) {
						// Gemini Nano: pregunta y respuesta en la ventana del complemento (AICore solo atiende a la app de delante)
						if (!Nano.preguntar(servicio, t.getString(titulo), pregunta, archivo)) lector.manejador.post { lector.decir(R.string.ia_movil_falta) }
						return@execute
					}
					val respuesta = AsistenteIA.preguntar(servicio, proveedor, listOf(AsistenteIA.Mensaje(true, pregunta)), archivo)
					archivo.delete()
					lector.manejador.post {
						ventanas.mensaje(t.getString(titulo), respuesta, alCerrar = {
							previo?.let { lector.manejador.postDelayed({ if (Nodos.vivo(it)) lector.enfocar(it, null) }, 250) }
						}, alCopiar = { lector.edicion.portapapeles(it) })
					}
				} catch (e: AsistenteIA.ErrorIA) {
					lector.manejador.post { lector.decir(e.message.orEmpty()) }
				} catch (e: Exception) {
					Registro.e(ETIQUETA, "La IA falló", e)
					lector.manejador.post { lector.decir(R.string.ia_error_general) }
				} finally {
					espera.parar()
				}
			}
		}
		return true
	}

	/** La primera vez que se usa un servicio de Internet, se dice qué se envía y a quién, y se pide permiso. */
	private fun confirmarEnvio(p: AsistenteIA.Proveedor, seguir: () -> Unit) {
		if (!AsistenteIA.saleDelMovil(p)) return seguir()
		val prefs = Ajustes(servicio).p
		val aceptados = prefs.getStringSet(AsistenteIA.CLAVE_ACEPTADOS, emptySet()).orEmpty()
		if (p.name in aceptados) return seguir()
		val nombre = AsistenteIA.nombre(servicio, p)
		ventanas.lista(t.getString(R.string.ia_confirmar_titulo, nombre), listOf(t.getString(R.string.ia_confirmar_si, nombre), t.getString(R.string.cancelar))) { i ->
			if (i == 0) {
				prefs.edit().putStringSet(AsistenteIA.CLAVE_ACEPTADOS, aceptados + p.name).apply()
				lector.manejador.postDelayed(seguir, 300)
			}
		}
	}

	// --- Descripción continua (vídeo y juegos) ---------------------------------------------

	private var continua: Runnable? = null
	private var continuaInicio = 0L
	private var continuaPeticiones = 0
	@Volatile private var continuaOcupada = false
	private var ultimaDescripcion: String? = null

	val describiendo: Boolean get() = continua != null

	/**
	 * Describir lo que pasa en la pantalla cada pocos segundos (un vídeo sin
	 * audiodescripción, un juego): la IA solo dice lo que ha cambiado. Siempre
	 * a petición, con límite de tiempo y, en la nube, de peticiones. Se para con
	 * la misma acción, al apagar la pantalla y con el modo seguro.
	 */
	fun alternarDescripcionContinua(): Boolean {
		if (describiendo) {
			pararDescripcionContinua()
			lector.decir(R.string.continua_parada)
			return true
		}
		if (!necesitaCaptura()) return false
		val proveedor = AsistenteIA.elegir(servicio) ?: run {
			lector.decir(R.string.ia_sin_configurar)
			return false
		}
		// Gemini Nano solo responde a la app de delante: no puede mirar un vídeo de otra
		if (proveedor == AsistenteIA.Proveedor.MOVIL) {
			lector.decir(R.string.continua_sin_nano)
			return false
		}
		confirmarEnvio(proveedor) {
			continuaInicio = android.os.SystemClock.uptimeMillis()
			continuaPeticiones = 0
			ultimaDescripcion = null
			val intervalo = lector.ajustes.continuaIntervalo * 1000L
			lector.decir(t.getString(R.string.continua_empieza, lector.ajustes.continuaIntervalo, lector.ajustes.continuaMinutos))
			val r = object : Runnable {
				override fun run() {
					if (continua !== this) return
					pasoContinua(proveedor)
					lector.manejador.postDelayed(this, intervalo)
				}
			}
			continua = r
			lector.manejador.postDelayed(r, 1500)
		}
		return true
	}

	fun pararDescripcionContinua() {
		continua?.let { lector.manejador.removeCallbacks(it) }
		continua = null
		rejillaAnterior = null
	}

	@Volatile private var rejillaAnterior: IntArray? = null

	/** La luminancia media de la captura en una rejilla de 32 × 24 (para saber si ha cambiado). */
	private fun rejilla(b: Bitmap): IntArray {
		val p = Bitmap.createScaledBitmap(b, 32, 24, true)
		val r = IntArray(32 * 24) { i ->
			val c = p.getPixel(i % 32, i / 32)
			(android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000
		}
		p.recycle()
		return r
	}

	private fun cambiadas(a: IntArray, b: IntArray): Int = a.indices.count { kotlin.math.abs(a[it] - b[it]) > 24 }

	private fun pasoContinua(proveedor: AsistenteIA.Proveedor) {
		val ahora = android.os.SystemClock.uptimeMillis()
		val limiteNube = AsistenteIA.saleDelMovil(proveedor) && continuaPeticiones >= MAXIMO_NUBE
		if (ahora - continuaInicio > lector.ajustes.continuaMinutos * 60_000L || limiteNube) {
			pararDescripcionContinua()
			lector.decir(R.string.continua_limite)
			return
		}
		// Sin hablar encima de lo que se está diciendo ni pedir otra si la anterior no ha vuelto
		if (continuaOcupada || lector.voz.hablando) return
		continuaOcupada = true
		continuaPeticiones++
		trabajo.execute {
			try {
				val captura = capturar() ?: return@execute
				// Si la pantalla apenas ha cambiado, no se pregunta (ni se gasta una consulta)
				val rejilla = rejilla(captura)
				val antes = rejillaAnterior
				if (antes != null && cambiadas(antes, rejilla) < CELDAS_CAMBIO) {
					captura.recycle()
					continuaPeticiones--
					return@execute
				}
				rejillaAnterior = rejilla
				val archivo = jpeg(captura, LADO_CONTINUA)
				captura.recycle()
				val anterior = ultimaDescripcion
				val conversacion = if (anterior == null) {
					listOf(AsistenteIA.Mensaje(true, t.getString(R.string.continua_primera)))
				} else {
					listOf(
						AsistenteIA.Mensaje(true, t.getString(R.string.continua_primera)),
						AsistenteIA.Mensaje(false, anterior),
						AsistenteIA.Mensaje(true, t.getString(R.string.continua_siguiente)),
					)
				}
				val respuesta = AsistenteIA.preguntar(servicio, proveedor, conversacion, archivo).trim()
				archivo.delete()
				// «-» o «sin cambios»: no hay nada nuevo que contar
				if (respuesta.isEmpty() || respuesta == "-" || respuesta.lowercase().startsWith(t.getString(R.string.continua_sin_cambios).lowercase())) return@execute
				ultimaDescripcion = respuesta
				lector.manejador.post {
					if (describiendo) lector.voz.decir(com.xebolax.tiresias.voz.Enunciado(respuesta, Modo.ENCOLAR, com.xebolax.tiresias.voz.Tipo.ANUNCIO))
				}
			} catch (e: AsistenteIA.ErrorIA) {
				lector.manejador.post {
					pararDescripcionContinua()
					lector.decir(e.message.orEmpty())
				}
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "La descripción continua falló", e)
				lector.manejador.post {
					pararDescripcionContinua()
					lector.decir(R.string.ia_error_general)
				}
			} finally {
				continuaOcupada = false
			}
		}
	}

	// --- Traducir ------------------------------------------------------------------------

	/** Traduce el texto del elemento enfocado al idioma de Tiresias con la IA elegida (solo el texto, sin captura). */
	fun traducir(): Boolean {
		val f = lector.foco ?: return false
		if (f.isPassword) {
			lector.decir(R.string.vision_contrasena)
			return false
		}
		val texto = lector.elemento(f).let { if (it.editable) it.texto.ifBlank { it.nombre } else it.nombre }.trim()
		if (texto.isBlank()) {
			lector.decir(R.string.traducir_nada)
			return false
		}
		val proveedor = AsistenteIA.elegir(servicio) ?: run {
			lector.decir(R.string.ia_sin_configurar)
			return false
		}
		if (proveedor == AsistenteIA.Proveedor.MOVIL) {
			lector.decir(R.string.actuar_sin_nano)
			return false
		}
		confirmarEnvio(proveedor) {
			espera.empezar()
			lector.decir(t.getString(R.string.ia_pensando, AsistenteIA.nombre(servicio, proveedor)), Modo.INTERRUMPIR)
			val idioma = java.util.Locale(com.xebolax.tiresias.base.Idioma.efectivo(servicio).language).getDisplayLanguage(com.xebolax.tiresias.base.Idioma.efectivo(servicio))
			trabajo.execute {
				try {
					val respuesta = AsistenteIA.preguntar(servicio, proveedor, listOf(AsistenteIA.Mensaje(true, t.getString(R.string.traducir_pedir, idioma, texto))), null)
					lector.manejador.post {
						ventanas.mensaje(t.getString(R.string.traducir_titulo), respuesta.trim(), alCerrar = {
							lector.manejador.postDelayed({ if (Nodos.vivo(f)) lector.enfocar(f, null) }, 250)
						}, alCopiar = { lector.edicion.portapapeles(it) })
					}
				} catch (e: AsistenteIA.ErrorIA) {
					lector.manejador.post { lector.decir(e.message.orEmpty()) }
				} catch (e: Exception) {
					Registro.e(ETIQUETA, "No se pudo traducir", e)
					lector.manejador.post { lector.decir(R.string.ia_error_general) }
				} finally {
					espera.parar()
				}
			}
		}
		return true
	}

	// --- Pedir a la IA que haga algo (con confirmación) -------------------------------------

	/**
	 * El usuario dice qué quiere hacer («pulsa jugar», «escribe hola en el
	 * mensaje»); la IA mira la captura y la lista de elementos y propone UNA
	 * acción. Tiresias la explica y solo la hace si el usuario la confirma. Sirve
	 * para los controles que no se pueden alcanzar de otra forma (juegos, apps
	 * sin accesibilidad). Nunca escribe en campos de contraseña.
	 */
	fun actuar(): Boolean {
		if (!necesitaCaptura()) return false
		val proveedor = AsistenteIA.elegir(servicio) ?: run {
			lector.decir(R.string.ia_sin_configurar)
			return false
		}
		if (proveedor == AsistenteIA.Proveedor.MOVIL) {
			lector.decir(R.string.actuar_sin_nano)
			return false
		}
		val previo = lector.foco
		ventanas.pedirTexto(t.getString(R.string.actuar_titulo), t.getString(R.string.actuar_pista), alCancelar = {
			previo?.let { lector.manejador.postDelayed({ if (Nodos.vivo(it)) lector.enfocar(it, null) }, 250) }
		}) { peticion ->
			if (peticion.isNotBlank()) lector.manejador.postDelayed({ confirmarEnvio(proveedor) { proponer(proveedor, peticion) } }, 400)
		}
		return true
	}

	private fun proponer(proveedor: AsistenteIA.Proveedor, peticion: String) {
		val raiz = lector.raiz() ?: return
		val ancho = servicio.resources.displayMetrics.widthPixels.coerceAtLeast(1)
		val alto = servicio.resources.displayMetrics.heightPixels.coerceAtLeast(1)
		// Los elementos con los que se puede hacer algo, numerados, con su sitio de 0 a 1000
		val elementos = lector.navegador.orden(raiz).filter { it.isClickable || it.isLongClickable || it.isEditable || it.isCheckable || it.isScrollable }.take(MAXIMO_ELEMENTOS)
		val lista = elementos.mapIndexed { i, n ->
			val r = Nodos.limites(n)
			"${i + 1}. ${lector.textoElemento(n)} [${r.left * 1000 / ancho},${r.top * 1000 / alto},${r.right * 1000 / ancho},${r.bottom * 1000 / alto}]"
		}.joinToString("\n")
		espera.empezar()
		lector.decir(t.getString(R.string.ia_pensando, AsistenteIA.nombre(servicio, proveedor)), Modo.INTERRUMPIR)
		trabajo.execute {
			try {
				val captura = capturar() ?: run {
					val motivo = motivoSinCaptura()
					lector.manejador.post { lector.decir(motivo) }
					return@execute
				}
				val archivo = jpeg(captura)
				captura.recycle()
				val pregunta = t.getString(R.string.actuar_peticion, peticion, lista.ifEmpty { t.getString(R.string.actuar_sin_elementos) })
				val respuesta = AsistenteIA.preguntar(servicio, proveedor, listOf(AsistenteIA.Mensaje(true, t.getString(R.string.actuar_instrucciones) + "\n\n" + pregunta)), archivo)
				archivo.delete()
				val p = PropuestaIA.leer(respuesta)
				lector.manejador.post { confirmarPropuesta(p, elementos, respuesta) }
			} catch (e: AsistenteIA.ErrorIA) {
				lector.manejador.post { lector.decir(e.message.orEmpty()) }
			} catch (e: Exception) {
				Registro.e(ETIQUETA, "La IA no pudo proponer una acción", e)
				lector.manejador.post { lector.decir(R.string.ia_error_general) }
			} finally {
				espera.parar()
			}
		}
	}

	private fun confirmarPropuesta(p: PropuestaIA?, elementos: List<android.view.accessibility.AccessibilityNodeInfo>, respuesta: String) {
		if (p == null || !p.valida) {
			ventanas.mensaje(t.getString(R.string.actuar_titulo), t.getString(R.string.actuar_no_entendida, respuesta.take(300)))
			return
		}
		if (p.accion == PropuestaIA.Tipo.NINGUNA) {
			ventanas.mensaje(t.getString(R.string.actuar_titulo), p.explicacion.ifEmpty { t.getString(R.string.actuar_nada) })
			return
		}
		val nodo = p.elemento?.let { elementos.getOrNull(it - 1) }
		if (p.elemento != null && nodo == null) {
			lector.decir(R.string.actuar_elemento_no)
			return
		}
		if (p.accion == PropuestaIA.Tipo.ESCRIBIR && nodo?.isPassword == true) {
			lector.decir(R.string.vision_contrasena)
			return
		}
		val que = when (p.accion) {
			PropuestaIA.Tipo.TOCAR -> nodo?.let { t.getString(R.string.actuar_tocar, lector.textoElemento(it)) } ?: t.getString(R.string.actuar_tocar_punto)
			PropuestaIA.Tipo.MANTENER -> nodo?.let { t.getString(R.string.actuar_mantener, lector.textoElemento(it)) } ?: t.getString(R.string.actuar_mantener_punto)
			PropuestaIA.Tipo.ESCRIBIR -> t.getString(R.string.actuar_escribir, p.texto.orEmpty(), nodo?.let { lector.textoElemento(it) }.orEmpty())
			PropuestaIA.Tipo.DESPLAZAR -> t.getString(R.string.actuar_desplazar)
			PropuestaIA.Tipo.NINGUNA -> ""
		}
		val titulo = listOf(p.explicacion, que).filter { it.isNotBlank() }.joinToString(". ")
		ventanas.lista(titulo, listOf(t.getString(R.string.actuar_hacerlo), t.getString(R.string.cancelar))) { i ->
			if (i == 0) lector.manejador.postDelayed({ hacerPropuesta(p, nodo) }, 400)
		}
	}

	private fun hacerPropuesta(p: PropuestaIA, nodo: android.view.accessibility.AccessibilityNodeInfo?) {
		val ancho = servicio.resources.displayMetrics.widthPixels
		val alto = servicio.resources.displayMetrics.heightPixels
		val punto = if (nodo != null) Nodos.limites(nodo).let { android.graphics.PointF(it.exactCenterX(), it.exactCenterY()) }
		else android.graphics.PointF((p.x ?: 500) * ancho / 1000f, (p.y ?: 500) * alto / 1000f)
		val hecho = when (p.accion) {
			PropuestaIA.Tipo.TOCAR -> (nodo != null && Nodos.vivo(nodo) && nodo.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) || gesto(punto, 60)
			PropuestaIA.Tipo.MANTENER -> (nodo != null && Nodos.vivo(nodo) && nodo.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)) || gesto(punto, 900)
			PropuestaIA.Tipo.ESCRIBIR -> nodo != null && Nodos.vivo(nodo) && !nodo.isPassword && nodo.performAction(
				android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
				android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, p.texto) },
			)
			PropuestaIA.Tipo.DESPLAZAR -> deslizar(p.direccion ?: PropuestaIA.Direccion.ABAJO, ancho, alto)
			PropuestaIA.Tipo.NINGUNA -> false
		}
		lector.sonidos.tocar(if (hecho) Sonido.ACTIVAR else Sonido.ERROR)
		if (!hecho) lector.decir(R.string.actuar_no_hecho)
	}

	/** Toca (o mantiene pulsado [ms]) el punto [p] de la pantalla. */
	private fun gesto(p: android.graphics.PointF, ms: Long): Boolean {
		val camino = Path().apply { moveTo(p.x, p.y) }
		return lector.mandarGesto(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(camino, 0, ms)).build())
	}

	/** Desliza un dedo por el centro de la pantalla para que el contenido se mueva en [d]. */
	private fun deslizar(d: PropuestaIA.Direccion, ancho: Int, alto: Int): Boolean {
		val cx = ancho / 2f
		val cy = alto / 2f
		val (desde, hasta) = when (d) {
			// Para ver lo de abajo, el dedo sube
			PropuestaIA.Direccion.ABAJO -> android.graphics.PointF(cx, alto * 0.75f) to android.graphics.PointF(cx, alto * 0.25f)
			PropuestaIA.Direccion.ARRIBA -> android.graphics.PointF(cx, alto * 0.25f) to android.graphics.PointF(cx, alto * 0.75f)
			PropuestaIA.Direccion.DERECHA -> android.graphics.PointF(ancho * 0.8f, cy) to android.graphics.PointF(ancho * 0.2f, cy)
			PropuestaIA.Direccion.IZQUIERDA -> android.graphics.PointF(ancho * 0.2f, cy) to android.graphics.PointF(ancho * 0.8f, cy)
		}
		val camino = Path().apply {
			moveTo(desde.x, desde.y)
			lineTo(hasta.x, hasta.y)
		}
		return lector.mandarGesto(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(camino, 0, 350)).build())
	}

	fun cerrar() {
		espera.parar()
		pararDescripcionContinua()
		iconos.cerrar()
		trabajo.shutdownNow()
		trabajoCaptura.shutdownNow()
		if (ocrPerezoso.isInitialized()) ocr.cerrar()
	}

	companion object {
		private const val ETIQUETA = "Vision"
		/** Peticiones a la nube como mucho en una descripción continua (cada una cuesta dinero al usuario). */
		private const val MAXIMO_NUBE = 120
		/** Lado de la captura en la descripción continua: más pequeña, más rápida. */
		private const val LADO_CONTINUA = 1024
		private const val MAXIMO_ELEMENTOS = 80
		/** Celdas de la rejilla que tienen que cambiar para volver a describir. */
		private const val CELDAS_CAMBIO = 6
	}
}
