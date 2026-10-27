package com.xebolax.tiresias.nano

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La ventana de «Tiresias IA del móvil»: pregunta a Gemini Nano (en el propio
 * móvil, con AICore) lo que manda Tiresias, con la captura si la hay, y enseña
 * la respuesta como texto que se lee y se puede copiar.
 *
 * Tiene que ser una ventana porque AICore solo responde a la app que está en
 * primer plano. Al cerrarla (Atrás o «Cerrar»), Tiresias devuelve el foco donde
 * estaba. Si se abre desde el icono, explica qué es y si el móvil lo admite.
 */
class ActividadNano : AppCompatActivity() {
	private lateinit var estado: TextView
	private lateinit var respuesta: TextView
	private lateinit var copiar: MaterialButton

	override fun onCreate(guardado: Bundle?) {
		super.onCreate(guardado)
		enableEdgeToEdge()
		val titulo = intent.getStringExtra(EXTRA_TITULO) ?: getString(R.string.nano_titulo)
		title = titulo
		val m = (16 * resources.displayMetrics.density).toInt()
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		val barra = MaterialToolbar(this).apply { this.title = titulo }
		raiz.addView(barra)
		val contenido = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(m, m / 2, m, m)
		}
		estado = TextView(this).apply {
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
			// Lo que va pasando se anuncia sin mover el foco
			ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
		}
		respuesta = TextView(this).apply {
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
			setTextIsSelectable(true)
			setPadding(0, m, 0, m)
		}
		copiar = MaterialButton(this).apply {
			text = getString(R.string.nano_copiar)
			minHeight = (56 * resources.displayMetrics.density).toInt()
			visibility = View.GONE
			setOnClickListener {
				getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.nano_nombre), respuesta.text))
				estado.text = getString(R.string.nano_copiado)
			}
		}
		val cerrar = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
			text = getString(R.string.nano_cerrar)
			minHeight = (56 * resources.displayMetrics.density).toInt()
			setOnClickListener { finish() }
		}
		contenido.addView(estado)
		contenido.addView(respuesta)
		contenido.addView(copiar)
		contenido.addView(cerrar)
		raiz.addView(ScrollView(this).apply { addView(contenido) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
		setContentView(raiz)
		ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, insets ->
			val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			v.updatePadding(left = b.left, top = b.top, right = b.right, bottom = b.bottom)
			WindowInsetsCompat.CONSUMED
		}
		val pregunta = intent.getStringExtra(EXTRA_PREGUNTA)
		if (pregunta == null) explicar() else responder(pregunta, intent.getStringExtra(EXTRA_SISTEMA).orEmpty(), intent.data)
	}

	/** Abierta desde el icono: qué es y si este móvil tiene Gemini Nano. */
	private fun explicar() {
		respuesta.text = getString(R.string.nano_explicacion)
		lifecycleScope.launch {
			val texto = try {
				when (Generation.getClient().checkStatus()) {
					FeatureStatus.AVAILABLE -> getString(R.string.nano_disponible)
					FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> getString(R.string.nano_descargable)
					else -> getString(R.string.nano_no_disponible)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				getString(R.string.nano_no_disponible)
			}
			estado.text = texto
		}
	}

	private fun responder(pregunta: String, sistema: String, imagen: Uri?) {
		estado.text = getString(R.string.nano_pensando)
		lifecycleScope.launch {
			try {
				val modelo = Generation.getClient()
				when (modelo.checkStatus()) {
					FeatureStatus.AVAILABLE -> Unit
					FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
						estado.text = getString(R.string.nano_bajando)
						var fallo: String? = null
						modelo.download().collect { s -> if (s is DownloadStatus.DownloadFailed) fallo = s.e.message ?: s.toString() }
						fallo?.let { error(getString(R.string.nano_error, it)) }
					}
					else -> error(getString(R.string.nano_no_disponible))
				}
				val mapa: Bitmap? = imagen?.let { uri ->
					withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }
				}
				val texto = "$sistema\n\n$pregunta"
				val peticion = if (mapa != null) generateContentRequest(ImagePart(mapa), TextPart(texto)) {}
				else generateContentRequest(TextPart(texto)) {}
				val resultado = modelo.generateContent(peticion).candidates.firstOrNull()?.text.orEmpty().trim()
				if (resultado.isEmpty()) error(getString(R.string.nano_sin_respuesta))
				respuesta.text = resultado
				estado.text = getString(R.string.nano_respuesta)
				copiar.visibility = View.VISIBLE
				// La respuesta recibe el foco para leerla enseguida
				respuesta.post { respuesta.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: GenAiException) {
				estado.text = getString(R.string.nano_error, e.message.orEmpty())
			} catch (e: IllegalStateException) {
				estado.text = e.message.orEmpty()
			} catch (e: Exception) {
				android.util.Log.e("TiresiasNano", "Gemini Nano falló", e)
				estado.text = getString(R.string.nano_error, e.message.orEmpty())
			}
		}
	}

	companion object {
		const val EXTRA_PREGUNTA = "pregunta"
		const val EXTRA_SISTEMA = "sistema"
		const val EXTRA_TITULO = "titulo"
	}
}
