package com.xebolax.tiresias.ui

import android.app.Activity
import android.content.Intent
import com.xebolax.tiresias.R

/**
 * Las opciones del menú lateral, que solo está en Inicio. Las demás pantallas
 * tienen «Volver» arriba a la izquierda, que lleva a Inicio: desde ahí se va a
 * otra sección con el menú (lo pidió el usuario: el menú en cada pantalla
 * obligaba a volver con el gesto).
 */
fun abrirDestino(actividad: Activity, opcion: Int) {
	val destino = when (opcion) {
		R.id.lateral_inicio -> ActividadPrincipal::class.java
		R.id.lateral_recursos -> ActividadRecursos::class.java
		R.id.lateral_gestos -> ActividadGestos::class.java
		R.id.lateral_diccionario -> ActividadDiccionario::class.java
		R.id.lateral_etiquetas -> ActividadEtiquetas::class.java
		R.id.lateral_perfiles -> ActividadPerfiles::class.java
		R.id.lateral_complementos -> ActividadComplementos::class.java
		R.id.lateral_tutorial -> ActividadTutorial::class.java
		R.id.lateral_experimentos -> ActividadExperimentos::class.java
		R.id.lateral_ajustes -> ActividadAjustes::class.java
		R.id.lateral_manual -> ActividadManual::class.java
		R.id.lateral_acerca -> ActividadAcerca::class.java
		else -> return
	}
	if (destino == actividad.javaClass) return
	actividad.startActivity(Intent(actividad, destino))
}
