/*
 * Puente JNI de EspeakNativo: eSpeak NG (GPL-3.0-or-later) en modo síncrono. Convierte texto en
 * muestras de 16 bits, mono, a la frecuencia que da espeak_Initialize (22 050 Hz).
 *
 * La API de eSpeak NG guarda su estado de forma global en el proceso: EspeakNativo (Kotlin)
 * serializa todas las llamadas. Inspirado en eSpeakService.c de la app Android oficial de
 * eSpeak NG (Reece H. Dunn, GPL-3.0-or-later).
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include <espeak-ng/speak_lib.h>

static short *muestras = NULL;
static size_t usadas = 0;
static size_t capacidad = 0;
static volatile int cancelar = 0;
static int fallo = 0;

/* Recibe cada bloque de sonido de espeak_Synth y lo añade al búfer. Devolver 1 corta la síntesis. */
static int alSintetizar(short *wav, int cuantas, espeak_EVENT *eventos) {
	(void) eventos;
	if (cancelar) {
		return 1;
	}
	if (wav == NULL || cuantas <= 0) {
		return 0;
	}
	if (usadas + (size_t) cuantas > capacidad) {
		size_t nueva = capacidad ? capacidad * 2 : 32768;
		while (nueva < usadas + (size_t) cuantas) {
			nueva *= 2;
		}
		short *crecido = realloc(muestras, nueva * sizeof(short));
		if (!crecido) {
			fallo = 1;
			return 1;
		}
		muestras = crecido;
		capacidad = nueva;
	}
	memcpy(muestras + usadas, wav, (size_t) cuantas * sizeof(short));
	usadas += (size_t) cuantas;
	return 0;
}

JNIEXPORT jint JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeIniciar(JNIEnv *env, jclass clazz, jstring ruta) {
	const char *c = (*env)->GetStringUTFChars(env, ruta, NULL);
	if (!c) {
		return -1;
	}
	/* 0x8000 = espeakINITIALIZE_DONT_EXIT: si faltan datos, devuelve error en vez de cerrar el proceso. */
	int frecuencia = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 500, c, 0x8000);
	(*env)->ReleaseStringUTFChars(env, ruta, c);
	if (frecuencia > 0) {
		espeak_SetSynthCallback(alSintetizar);
	}
	return frecuencia;
}

JNIEXPORT jboolean JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeVoz(JNIEnv *env, jclass clazz, jstring nombre) {
	const char *c = (*env)->GetStringUTFChars(env, nombre, NULL);
	if (!c) {
		return JNI_FALSE;
	}
	espeak_ERROR resultado = espeak_SetVoiceByName(c);
	(*env)->ReleaseStringUTFChars(env, nombre, c);
	return resultado == EE_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeParametro(JNIEnv *env, jclass clazz, jint parametro, jint valor) {
	return espeak_SetParameter((espeak_PARAMETER) parametro, (int) valor, 0) == EE_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jshortArray JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeSintetizar(JNIEnv *env, jclass clazz, jstring texto) {
	const char *c = (*env)->GetStringUTFChars(env, texto, NULL);
	if (!c) {
		return NULL;
	}
	usadas = 0;
	fallo = 0;
	cancelar = 0;
	size_t largo = strlen(c) + 1;
	espeak_ERROR resultado = espeak_Synth(c, largo, 0, POS_CHARACTER, 0, espeakCHARS_UTF8, NULL, NULL);
	(*env)->ReleaseStringUTFChars(env, texto, c);
	if (resultado != EE_OK || fallo) {
		return NULL;
	}
	jshortArray salida = (*env)->NewShortArray(env, (jsize) usadas);
	if (salida && usadas > 0) {
		(*env)->SetShortArrayRegion(env, salida, 0, (jsize) usadas, muestras);
	}
	return salida;
}

/*
 * Los fonemas de un texto en IPA (Alfabeto Fonético Internacional), con la voz
 * puesta, separados por espacios entre grupos. Los usan las voces neuronales
 * Piper, que se entrenaron con los fonemas de eSpeak NG. Devuelve NULL si falla.
 */
JNIEXPORT jstring JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeFonemas(JNIEnv *env, jclass clazz, jstring texto) {
	const char *c = (*env)->GetStringUTFChars(env, texto, NULL);
	if (!c) {
		return NULL;
	}
	size_t capacidadSalida = 1024;
	size_t largoSalida = 0;
	char *salida = malloc(capacidadSalida);
	if (!salida) {
		(*env)->ReleaseStringUTFChars(env, texto, c);
		return NULL;
	}
	salida[0] = 0;
	const void *puntero = c;
	while (puntero != NULL) {
		const char *fonemas = espeak_TextToPhonemes(&puntero, espeakCHARS_UTF8, espeakPHONEMES_IPA);
		if (!fonemas) {
			/* Sin más fonemas: se sale aunque el puntero no se haya puesto a NULL (nunca un bucle sin fin) */
			break;
		}
		size_t n = strlen(fonemas);
		if (largoSalida + n + 2 > capacidadSalida) {
			while (largoSalida + n + 2 > capacidadSalida) {
				capacidadSalida *= 2;
			}
			char *crecida = realloc(salida, capacidadSalida);
			if (!crecida) {
				break;
			}
			salida = crecida;
		}
		if (largoSalida > 0) {
			salida[largoSalida++] = ' ';
		}
		memcpy(salida + largoSalida, fonemas, n);
		largoSalida += n;
		salida[largoSalida] = 0;
	}
	(*env)->ReleaseStringUTFChars(env, texto, c);
	jstring resultado = (*env)->NewStringUTF(env, salida);
	free(salida);
	return resultado;
}

/* Corta la síntesis en curso (se puede llamar desde otro hilo). */
JNIEXPORT void JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeCancelar(JNIEnv *env, jclass clazz) {
	cancelar = 1;
}

JNIEXPORT void JNICALL
Java_com_xebolax_tiresias_voz_EspeakNativo_nativeTerminar(JNIEnv *env, jclass clazz) {
	espeak_Terminate();
	free(muestras);
	muestras = NULL;
	usadas = 0;
	capacidad = 0;
}
