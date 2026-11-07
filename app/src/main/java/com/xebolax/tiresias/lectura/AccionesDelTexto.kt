package com.xebolax.tiresias.lectura

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.accessibility.AccessibilityNodeInfo
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import com.xebolax.tiresias.base.Registro
import com.xebolax.tiresias.nucleo.EntidadTexto
import com.xebolax.tiresias.nucleo.EntidadesTexto
import com.xebolax.tiresias.nucleo.TrozoReconocido
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Busca en el texto de un elemento lo que se puede usar aunque no sea un enlace
 * ([EntidadesTexto]): las direcciones web y de correo con `Linkify`, y los
 * teléfonos con el clasificador de texto de Android (en el propio móvil), que
 * también dice qué cifras son fechas, horas o direcciones para no tomarlas por
 * códigos. El clasificador va en su hilo y se le espera un segundo como mucho.
 * Nada de esto se guarda.
 */
object AccionesDelTexto {
	private const val ETIQUETA = "AccionesDelTexto"
	private const val MAXIMO_NODOS = 40
	private const val MAXIMO_CARACTERES = 4_000
	private const val ESPERA_CLASIFICAR_MS = 1_000L

	private val hilo = Executors.newSingleThreadExecutor { r -> Thread(r, "tiresias-clasificar").apply { isDaemon = true } }

	/** Lo que hay en el texto de [n] y los de dentro, sin lo que [n] ya enlaza ([enlazados]: lo ofrece el menú como enlace). */
	fun buscar(c: Context, n: AccessibilityNodeInfo, enlazados: Set<String>): List<EntidadTexto> {
		val texto = textoDe(n)
		if (!EntidadesTexto.puedeTener(texto)) return emptyList()
		val sp = SpannableString(texto)
		Linkify.addLinks(sp, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
		val enlaces = sp.getSpans(0, sp.length, URLSpan::class.java).mapNotNull { s -> s.url?.let { Triple(sp.getSpanStart(s), sp.getSpanEnd(s), it) } }
		val clasificado = if (Build.VERSION.SDK_INT >= 28) clasificar(c.applicationContext, texto) else null
		return EntidadesTexto.encontrar(texto, enlaces, clasificado, enlazados)
	}

	/** El texto de [n] y de lo de dentro (40 nodos y 4000 caracteres como mucho). */
	private fun textoDe(n: AccessibilityNodeInfo): String {
		val sb = StringBuilder()
		val cola = ArrayDeque<AccessibilityNodeInfo>()
		cola.add(n)
		var vistos = 0
		while (cola.isNotEmpty() && vistos < MAXIMO_NODOS && sb.length < MAXIMO_CARACTERES) {
			val x = cola.removeFirst()
			vistos++
			x.text?.let { sb.append(it).append('\n') }
			x.contentDescription?.let { sb.append(it).append('\n') }
			for (i in 0 until x.childCount) runCatching { x.getChild(i) }.getOrNull()?.let { cola.add(it) }
		}
		return sb.take(MAXIMO_CARACTERES).toString()
	}

	/** Lo que reconoce el clasificador de Android, o null si no hay o no contesta a tiempo (entonces no se ofrecen códigos). */
	private fun clasificar(c: Context, texto: String): List<TrozoReconocido>? {
		if (Build.VERSION.SDK_INT < 28) return null
		val tarea = hilo.submit(Callable { generar(c, texto) })
		return try {
			tarea.get(ESPERA_CLASIFICAR_MS, TimeUnit.MILLISECONDS)
		} catch (e: TimeoutException) {
			tarea.cancel(true)
			Registro.w(ETIQUETA, "El clasificador de texto no contestó a tiempo")
			null
		} catch (e: ExecutionException) {
			Registro.w(ETIQUETA, "Fallo del clasificador de texto", e.cause ?: e)
			null
		}
	}

	private fun generar(c: Context, texto: String): List<TrozoReconocido>? {
		if (Build.VERSION.SDK_INT < 28) return null
		val clasificador = c.getSystemService(TextClassificationManager::class.java)?.textClassifier ?: return null
		if (clasificador === TextClassifier.NO_OP) return null
		val tipos = listOf(
			TextClassifier.TYPE_PHONE, TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME, TextClassifier.TYPE_ADDRESS,
			TextClassifier.TYPE_FLIGHT_NUMBER, TextClassifier.TYPE_URL, TextClassifier.TYPE_EMAIL,
		)
		val pedido = TextLinks.Request.Builder(texto)
			.setEntityConfig(TextClassifier.EntityConfig.createWithExplicitEntityList(tipos))
			.setDefaultLocales(LocaleList.getDefault())
			.build()
		return clasificador.generateLinks(pedido).links.mapNotNull { l ->
			if (l.entityCount == 0) return@mapNotNull null
			// El de Samsung pone «phone_e164» antes que «phone» para el mismo número
			val telefono = (0 until l.entityCount).any { l.getEntity(it) == TextClassifier.TYPE_PHONE }
			TrozoReconocido(l.start, l.end, telefono)
		}
	}
}
