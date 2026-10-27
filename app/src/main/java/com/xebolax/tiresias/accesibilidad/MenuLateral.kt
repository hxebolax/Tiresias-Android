package com.xebolax.tiresias.accesibilidad

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.xebolax.tiresias.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Cajón de navegación modal de Material 3, accesible con TalkBack.
 *
 * Por qué no NavigationView: con elementos marcables TalkBack decía «marcado»
 * o «no seleccionado» en TODAS las filas y el foco no entraba en el menú al
 * abrirlo. Aquí:
 *  - Arriba, los destinos (las secciones de la app). Solo el actual lleva el
 *    indicador (píldora), la negrita y el estado «seleccionado»; los demás no
 *    tienen estado, porque no son opciones marcables.
 *  - Tras un divisor, las acciones generales (Ajustes, Ayuda, Acerca de). Nunca
 *    quedan seleccionadas. Ajustes se abre SOLO desde aquí: una sola puerta a
 *    la configuración, sin atajos a Ajustes en las secciones.
 *  - Cada fila es una sola parada de 56 dp que se activa con un doble toque.
 *  - Al abrirlo con TalkBack, el foco va a la sección actual; Atrás lo cierra.
 *  - Un toque navega y cierra el cajón.
 *
 * El menú (res/menu) pone los destinos en un grupo y las acciones en otro:
 *
 *     <menu>
 *         <group android:id="@+id/grupo_destinos">
 *             <item android:id="@+id/destino_inicio" android:title="@string/inicio" android:icon="@drawable/ic_inicio" />
 *             ...
 *         </group>
 *         <group android:id="@+id/grupo_general">
 *             <item android:id="@+id/accion_ajustes" android:title="@string/ajustes" android:icon="@drawable/ic_ajustes" />
 *             ...
 *         </group>
 *     </menu>
 *
 * Uso en onCreate, en lugar de setContentView(contenido). Llama ANTES a
 * configurarBarra (o a setSupportActionBar): si se llama después, la barra de
 * acciones se queda con el botón de navegación y «Abrir menú» deja de abrir el
 * cajón (comprobado en el emulador):
 *
 *     configurarBarra(barra, getString(R.string.app_name), conArriba = false)
 *     setContentView(ponerMenuLateral(contenido, barra, R.menu.menu_lateral, R.id.destino_inicio, R.drawable.ic_menu) { id -> ... })
 *
 * Cadenas: app_name, abrir_menu, menu_lateral. Identificadores: menu_lateral_cajon,
 * menu_lateral_navegacion. Requiere Material Components 1.12 o posterior
 * (atributos colorSurfaceContainerLow y colorSecondaryContainer).
 */
fun AppCompatActivity.ponerMenuLateral(
	contenido: View,
	barra: MaterialToolbar,
	@MenuRes menu: Int,
	destinoActual: Int,
	@DrawableRes iconoMenu: Int,
	alElegir: (Int) -> Unit,
): DrawerLayout {
	val cajon = DrawerLayout(this).apply { id = R.id.menu_lateral_cajon }
	cajon.addView(contenido, DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
	val (navegacion, filaActual) = contenidoMenuLateral(menu, destinoActual) { id ->
		cajon.closeDrawer(GravityCompat.START)
		alElegir(id)
	}
	cajon.addView(navegacion, DrawerLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
		gravity = GravityCompat.START
	})
	barra.setNavigationIcon(iconoMenu)
	barra.navigationContentDescription = getString(R.string.abrir_menu)
	barra.setNavigationOnClickListener { cajon.openDrawer(GravityCompat.START) }
	// Atrás con el menú abierto lo cierra: se registra al abrirlo para ir antes que los demás callbacks
	var cerrar: OnBackPressedCallback? = null
	cajon.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
		override fun onDrawerOpened(vista: View) {
			// Con TalkBack, el foco entra directamente en el menú, en la sección en la que estás
			if (lectorActivo()) filaActual?.post { filaActual.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
			cerrar?.remove()
			cerrar = object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() = cajon.closeDrawer(GravityCompat.START)
			}.also { onBackPressedDispatcher.addCallback(this@ponerMenuLateral, it) }
		}

		override fun onDrawerClosed(vista: View) {
			cerrar?.remove()
			cerrar = null
		}
	})
	// Título del panel: TalkBack lo anuncia al abrir el cajón
	cajon.setDrawerTitle(GravityCompat.START, getString(R.string.menu_lateral))
	return cajon
}

/**
 * El contenido del cajón. Devuelve la vista y la fila del destino actual
 * (para llevarle el foco al abrir).
 */
fun AppCompatActivity.contenidoMenuLateral(@MenuRes menuRes: Int, destinoActual: Int, alElegir: (Int) -> Unit): Pair<View, View?> {
	val dp = resources.displayMetrics.density
	// Se infla el menú con un PopupMenu porque MenuBuilder es API restringida
	val menu = PopupMenu(this, View(this)).menu.also { menuInflater.inflate(menuRes, it) }
	val lista = LinearLayout(this).apply {
		orientation = LinearLayout.VERTICAL
		minimumWidth = (300 * dp).toInt()
		setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
	}
	// Título del menú (encabezado), como el «headline» del cajón de Material
	lista.addView(TextView(this).apply {
		text = getString(R.string.app_name)
		setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
		setTextColor(MaterialColors.getColor(lista, com.google.android.material.R.attr.colorOnSurfaceVariant))
		setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
		ViewCompat.setAccessibilityHeading(this, true)
	})
	var actual: View? = null
	var grupoAnterior = -1
	for (i in 0 until menu.size()) {
		val item = menu.getItem(i)
		if (!item.isVisible) continue
		if (grupoAnterior != -1 && item.groupId != grupoAnterior) {
			// Divisor entre destinos y acciones: decorativo, no se lee
			lista.addView(View(this).apply {
				setBackgroundColor(MaterialColors.getColor(lista, com.google.android.material.R.attr.colorOutlineVariant))
				importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp.toInt().coerceAtLeast(1)).apply {
				setMargins((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
			})
		}
		grupoAnterior = item.groupId
		val esActual = item.itemId == destinoActual
		val fila = TextView(this).apply {
			text = item.title
			minHeight = (56 * dp).toInt()
			gravity = Gravity.CENTER_VERTICAL
			setPadding((16 * dp).toInt(), 0, (24 * dp).toInt(), 0)
			compoundDrawablePadding = (12 * dp).toInt()
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
			val color = MaterialColors.getColor(
				lista,
				if (esActual) com.google.android.material.R.attr.colorOnSecondaryContainer else com.google.android.material.R.attr.colorOnSurfaceVariant,
			)
			setTextColor(color)
			item.icon?.mutate()?.let { icono ->
				icono.setTint(color)
				icono.setBounds(0, 0, (24 * dp).toInt(), (24 * dp).toInt())
				setCompoundDrawablesRelative(icono, null, null, null)
			}
			// El indicador nunca es solo color: también negrita y «seleccionado»
			if (esActual) setTypeface(typeface, Typeface.BOLD)
			// Indicador del destino activo (píldora) y respuesta al toque
			val forma = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(28 * dp).build()).apply {
				fillColor = ColorStateList.valueOf(
					if (esActual) MaterialColors.getColor(lista, com.google.android.material.R.attr.colorSecondaryContainer) else Color.TRANSPARENT
				)
			}
			val mascara = MaterialShapeDrawable(forma.shapeAppearanceModel).apply {
				fillColor = ColorStateList.valueOf(Color.WHITE)
			}
			background = RippleDrawable(
				ColorStateList.valueOf(MaterialColors.getColor(lista, android.R.attr.colorControlHighlight)), forma, mascara,
			)
			isClickable = true
			isFocusable = true
			// Solo el destino actual dice «seleccionado»; los demás, nada
			isSelected = esActual
			setOnClickListener { alElegir(item.itemId) }
		}
		if (esActual) actual = fila
		lista.addView(fila, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}
	val contenido = ScrollView(this).apply {
		id = R.id.menu_lateral_navegacion
		// Borde a borde: el cajón no queda bajo la barra de estado ni la de navegación
		fitsSystemWindows = true
		isFillViewport = true
		layoutParams = ViewGroup.LayoutParams((300 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
		setBackgroundColor(MaterialColors.getColor(lista, com.google.android.material.R.attr.colorSurfaceContainerLow))
		addView(lista)
	}
	return contenido to actual
}
