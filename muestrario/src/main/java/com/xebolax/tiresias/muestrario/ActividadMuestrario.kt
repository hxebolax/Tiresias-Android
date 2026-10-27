package com.xebolax.tiresias.muestrario

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Muestrario: todos los tipos de control en una pantalla, con nombres fijos,
 * para que la prueba LectorTest compruebe qué dice Tiresias de cada uno. El
 * orden de los elementos es el que espera la prueba: si se cambia algo aquí,
 * hay que cambiarlo allí.
 */
class ActividadMuestrario : AppCompatActivity() {
	private val principal = Handler(Looper.getMainLooper())

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		title = "Muestrario"
		val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

		raiz.addView(TextView(this).apply {
			text = "Controles"
			ViewCompat.setAccessibilityHeading(this, true)
		})
		raiz.addView(MaterialButton(this).apply { text = "Guardar"; setOnClickListener { } })
		raiz.addView(ImageButton(this).apply {
			id = R.id.boton_enviar
			setImageResource(android.R.drawable.ic_menu_send)
			// Sin nombre a propósito: Tiresias debe decir su identificador
			setOnClickListener { }
		})
		raiz.addView(CheckBox(this).apply { text = "Recordarme" })
		raiz.addView(SwitchCompat(this).apply { text = "Wifi"; isChecked = true })
		raiz.addView(RadioGroup(this).apply {
			addView(RadioButton(this@ActividadMuestrario).apply { text = "Rojo"; id = View.generateViewId() })
			addView(RadioButton(this@ActividadMuestrario).apply { text = "Verde"; id = View.generateViewId() })
		})
		raiz.addView(TextView(this).apply { text = "Volumen" })
		raiz.addView(SeekBar(this).apply {
			contentDescription = "Volumen"
			max = 100
			progress = 40
		})
		raiz.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
			contentDescription = "Descarga"
			max = 100
			progress = 75
		})
		raiz.addView(Spinner(this).apply {
			contentDescription = "País"
			adapter = ArrayAdapter(this@ActividadMuestrario, android.R.layout.simple_spinner_item, listOf("España", "México", "Argentina"))
		})
		raiz.addView(TextInputLayout(this).apply {
			hint = "Correo"
			addView(TextInputEditText(this@ActividadMuestrario))
		})
		raiz.addView(EditText(this).apply {
			hint = "Contraseña"
			inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
		})

		val vivo = TextView(this).apply {
			text = "Estado: listo"
			ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
		}
		raiz.addView(MaterialButton(this).apply {
			text = "Cambiar estado"
			setOnClickListener { principal.postDelayed({ vivo.text = "Estado: guardado" }, 200) }
		})
		raiz.addView(vivo)
		raiz.addView(MaterialButton(this).apply {
			text = "Abrir página web"
			setOnClickListener { startActivity(Intent(this@ActividadMuestrario, ActividadWeb::class.java)) }
		})

		raiz.addView(TextView(this).apply {
			text = "Mensajes"
			ViewCompat.setAccessibilityHeading(this, true)
		})
		val lista = RecyclerView(this).apply {
			layoutManager = LinearLayoutManager(this@ActividadMuestrario)
			adapter = Filas((1..40).map { "Mensaje $it" })
		}
		raiz.addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600))

		// Respeta las barras del sistema: lo que queda debajo no es visible para el lector
		setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(raiz) })
	}

	private class Filas(val textos: List<String>) : RecyclerView.Adapter<Filas.Fila>() {
		class Fila(val vista: TextView) : RecyclerView.ViewHolder(vista)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Fila(TextView(parent.context).apply {
			setPadding(24, 32, 24, 32)
			isClickable = true
			isFocusable = true
		})

		override fun onBindViewHolder(holder: Fila, position: Int) {
			holder.vista.text = textos[position]
			holder.vista.setOnClickListener { }
		}

		override fun getItemCount() = textos.size
	}
}
