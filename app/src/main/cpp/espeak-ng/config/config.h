/*
 * config.h de eSpeak NG 1.52.0 para Rescate Accesible (en lugar del generado por CMake o autoconf).
 * Síntesis síncrona y sin dependencias externas: sin hilo asíncrono, sin MBROLA, sin libsonic
 * ni libpcaudio (el sonido lo reproduce la app). Klatt y speech-player sí, como en la app oficial.
 */
#define HAVE_MKSTEMP 1

#define USE_ASYNC 0
#define USE_KLATT 1
#define USE_LIBPCAUDIO 0
#define USE_LIBSONIC 0
#define USE_MBROLA 0
#define USE_SPEECHPLAYER 1

#define PACKAGE_VERSION "1.52.0"
