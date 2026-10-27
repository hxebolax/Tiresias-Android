cmake_minimum_required(VERSION 3.22.1)
project(tiresiasvoz C CXX)

# --- SVOX Pico (Apache 2.0) ---------------------------------------------------------
# De AOSP, y el puente JNI que usa PicoNativo para hablar con él.
# Basado en el CMakeLists.txt de Shortcut Menu (aaron-gh, Apache License 2.0).
file(GLOB PICO_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/pico/*.c)
add_library(tiresiaspico SHARED pico_jni.c ${PICO_SOURCES})
target_include_directories(tiresiaspico PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/pico)
# Pico es código de terceros y se compila tal cual, sin avisos.
set_source_files_properties(${PICO_SOURCES} PROPERTIES COMPILE_OPTIONS -w)
# Páginas de 16 KB (Android 15+): alinea los segmentos de la biblioteca.
target_link_options(tiresiaspico PRIVATE "-Wl,-z,max-page-size=16384")

# --- eSpeak NG 1.52.0 (GPL-3.0-or-later) --------------------------------------------
# Solo la biblioteca, con un config.h propio y sin descargar nada al compilar
# (el CMake oficial clona libsonic con git). Segunda voz incluida, independiente de Pico.
if(INCLUIR_ESPEAK)
	set(ESPEAK ${CMAKE_CURRENT_SOURCE_DIR}/espeak-ng/src)
	set(ESPEAK_SOURCES
		${ESPEAK}/libespeak-ng/common.c
		${ESPEAK}/libespeak-ng/mnemonics.c
		${ESPEAK}/libespeak-ng/error.c
		${ESPEAK}/libespeak-ng/ieee80.c
		${ESPEAK}/libespeak-ng/compiledata.c
		${ESPEAK}/libespeak-ng/compiledict.c
		${ESPEAK}/libespeak-ng/dictionary.c
		${ESPEAK}/libespeak-ng/encoding.c
		${ESPEAK}/libespeak-ng/intonation.c
		${ESPEAK}/libespeak-ng/langopts.c
		${ESPEAK}/libespeak-ng/numbers.c
		${ESPEAK}/libespeak-ng/phoneme.c
		${ESPEAK}/libespeak-ng/phonemelist.c
		${ESPEAK}/libespeak-ng/readclause.c
		${ESPEAK}/libespeak-ng/setlengths.c
		${ESPEAK}/libespeak-ng/soundicon.c
		${ESPEAK}/libespeak-ng/spect.c
		${ESPEAK}/libespeak-ng/ssml.c
		${ESPEAK}/libespeak-ng/synthdata.c
		${ESPEAK}/libespeak-ng/synthesize.c
		${ESPEAK}/libespeak-ng/tr_languages.c
		${ESPEAK}/libespeak-ng/translate.c
		${ESPEAK}/libespeak-ng/translateword.c
		${ESPEAK}/libespeak-ng/voices.c
		${ESPEAK}/libespeak-ng/wavegen.c
		${ESPEAK}/libespeak-ng/speech.c
		${ESPEAK}/libespeak-ng/espeak_api.c
		${ESPEAK}/libespeak-ng/klatt.c
		${ESPEAK}/libespeak-ng/sPlayer.c
		${ESPEAK}/ucd-tools/src/case.c
		${ESPEAK}/ucd-tools/src/categories.c
		${ESPEAK}/ucd-tools/src/ctype.c
		${ESPEAK}/ucd-tools/src/proplist.c
		${ESPEAK}/ucd-tools/src/scripts.c
		${ESPEAK}/ucd-tools/src/tostring.c
	)
	# speech-player (C++) aparte: las cabeceras de compatibilidad de eSpeak chocan con libc++
	add_library(speechplayer STATIC
		${ESPEAK}/speechPlayer/src/frame.cpp
		${ESPEAK}/speechPlayer/src/speechPlayer.cpp
		${ESPEAK}/speechPlayer/src/speechWaveGenerator.cpp
	)
	target_include_directories(speechplayer PRIVATE ${ESPEAK}/speechPlayer/include ${ESPEAK}/speechPlayer/src)
	target_compile_options(speechplayer PRIVATE -w -fPIC)
	add_library(tiresiasespeak SHARED espeak_jni.c ${ESPEAK_SOURCES})
	target_include_directories(tiresiasespeak PRIVATE
		${CMAKE_CURRENT_SOURCE_DIR}/espeak-ng/config
		${ESPEAK}/include
		${ESPEAK}/include/compat
		${ESPEAK}/ucd-tools/src/include
		${ESPEAK}/speechPlayer/include
		${ESPEAK}/libespeak-ng
	)
	target_compile_definitions(tiresiasespeak PRIVATE
		LIBESPEAK_NG_EXPORT=1
		PATH_ESPEAK_DATA="/data/local/tmp/espeak-ng-data"
	)
	set_source_files_properties(${ESPEAK_SOURCES} PROPERTIES COMPILE_OPTIONS "-w;-fwrapv;-fno-exceptions")
	target_link_libraries(tiresiasespeak PRIVATE speechplayer m)
	target_link_options(tiresiasespeak PRIVATE "-Wl,-z,max-page-size=16384")
endif()

# --- QuickJS (quickjs-ng 0.17.0, MIT) para los complementos ---------------------------
# Código del motor en quickjs/ (sin descargas al compilar) y el puente JNI, adaptado de
# Backtalk (Apache 2.0). Los scripts corren aislados: límite de memoria, de pila y de tiempo.
set(QJS ${CMAKE_CURRENT_SOURCE_DIR}/quickjs)
add_library(qjs STATIC ${QJS}/dtoa.c ${QJS}/libregexp.c ${QJS}/libunicode.c ${QJS}/quickjs.c)
target_include_directories(qjs PUBLIC ${QJS})
target_compile_definitions(qjs PRIVATE _GNU_SOURCE NDEBUG QUICKJS_NG_BUILD)
target_compile_options(qjs PRIVATE -O2 -w -fPIC)
add_library(tiresiasquickjs SHARED quickjs_jni.c)
target_compile_definitions(tiresiasquickjs PRIVATE NDEBUG QUICKJS_NG_BUILD)
target_compile_options(tiresiasquickjs PRIVATE -O2)
target_link_libraries(tiresiasquickjs PRIVATE qjs log m)
target_link_options(tiresiasquickjs PRIVATE "-Wl,-z,max-page-size=16384")
