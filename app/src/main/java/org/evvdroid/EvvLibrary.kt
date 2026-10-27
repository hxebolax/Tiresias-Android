// Para Tiresias (no está en evvdroid): dónde está la biblioteca del motor.
package org.evvdroid

/**
 * Dónde ha descomprimido Tiresias `libevvjni.so`. Va aparte de [EvvNative]
 * para poder ponerlo antes de que [EvvNative] se cargue (al cargarse, carga la
 * biblioteca de esta ruta).
 */
object EvvLibrary {
	@Volatile
	var path: String? = null
}
