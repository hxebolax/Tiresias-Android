package com.xebolax.tiresias.accesibilidad

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.xebolax.tiresias.R

/**
 * Deslizador de Ajustes accesible (androidx.preference): TalkBack dice el valor
 * con su unidad («10 segundos», «100 por ciento», «18 puntos») en vez de un
 * porcentaje del recorrido, y al deslizar arriba o abajo cambia exactamente un
 * paso (de 5 en 5, de 10 en 10...), siempre en múltiplos del paso, no un 5 %
 * del recorrido. Es una sola parada: «20 puntos, Tamaño de letra, control
 * deslizante», con la descripción del ajuste como pista.
 *
 * En el XML de preferencias:
 *
 *     <com.xebolax.tiresias.accesibilidad.DeslizadorAjuste
 *         app:key="velocidad"
 *         app:title="@string/velocidad"
 *         app:min="50"
 *         android:max="300"
 *         app:seekBarIncrement="5"
 *         app:showSeekBarValue="true"
 *         android:defaultValue="100" />
 *
 * Y una vez, al arrancar (Application.onCreate), cómo se dice cada valor:
 *
 *     DeslizadorAjuste.formatos["velocidad"] = DeslizadorAjuste.porCiento
 *     DeslizadorAjuste.formatos["salto"] = DeslizadorAjuste.segundos
 *
 * Cadenas: valor_por_ciento, valor_puntos, valor_milisegundos y el plural segundos.
 */
class DeslizadorAjuste @JvmOverloads constructor(contexto: Context, atributos: AttributeSet? = null) : SeekBarPreference(contexto, atributos) {

	companion object {
		/** Cómo se dice el valor de cada ajuste, por su clave. */
		val formatos = HashMap<String, (Context, Int) -> String>()

		val porCiento: (Context, Int) -> String = { c, v -> c.getString(R.string.valor_por_ciento, v) }
		val segundos: (Context, Int) -> String = { c, v -> c.resources.getQuantityString(R.plurals.segundos, v, v) }
		val minutos: (Context, Int) -> String = { c, v -> c.resources.getQuantityString(R.plurals.minutos, v, v) }
		val puntos: (Context, Int) -> String = { c, v -> c.resources.getQuantityString(R.plurals.valor_puntos, v, v) }
		val milisegundos: (Context, Int) -> String = { c, v -> c.getString(R.string.valor_milisegundos, v) }
	}

	/** Formato solo para este deslizador (si se crea en código); si no, el de formatos[key]; si no, el número. */
	var formato: ((Context, Int) -> String)? = null

	private val paso get() = seekBarIncrement.coerceAtLeast(1)

	/** El valor dicho con su unidad. */
	fun texto(v: Int): String = (formato ?: key?.let { formatos[it] })?.invoke(context, v) ?: v.toString()

	override fun onBindViewHolder(holder: PreferenceViewHolder) {
		super.onBindViewHolder(holder)
		val barra = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar ?: return
		// Una sola parada para TalkBack, la barra (la fila, el título y el número sin unidad no se leen aparte)
		holder.itemView.isFocusable = false
		holder.itemView.isClickable = false
		for (id in listOf(android.R.id.title, android.R.id.summary, androidx.preference.R.id.seekbar_value)) {
			holder.findViewById(id)?.let {
				it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
				(it as? TextView)?.labelFor = View.NO_ID
			}
		}
		barra.contentDescription = title
		ViewCompat.setAccessibilityDelegate(barra, object : AccessibilityDelegateCompat() {
			override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
				super.onInitializeAccessibilityNodeInfo(host, info)
				info.stateDescription = texto(value)
				summary?.takeIf { it.isNotBlank() }?.let { info.hintText = it }
			}

			override fun performAccessibilityAction(host: View, accion: Int, argumentos: Bundle?): Boolean {
				val nuevo = when (accion) {
					AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> (value / paso + 1) * paso
					AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> ((value - 1) / paso) * paso
					else -> return super.performAccessibilityAction(host, accion, argumentos)
				}.coerceIn(min, max)
				if (nuevo != value && callChangeListener(nuevo)) {
					value = nuevo
					// Obsoleto en API 36; probado y aún funciona. Ver referencias/deslizadores-y-valores.md
					@Suppress("DEPRECATION")
					host.announceForAccessibility(texto(nuevo))
				}
				return true
			}
		})
	}
}
