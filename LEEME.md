# Tiresias para Android

Tiresias es un lector de pantalla libre para Android 7.0 o posterior, en español e inglés y sin Google Play Services. Dice en voz alta lo que hay en la pantalla y deja manejar el móvil con gestos, con un teclado, con una línea braille o con la voz.

El manual de uso completo está dentro de la app (menú lateral, Manual de uso) y en `app/src/main/assets/manual-es.html`. La presentación del proyecto está en `README.md`.

## Lo esencial

- **Se activa** en Ajustes del sistema, Accesibilidad, Tiresias. Si Android dice que el ajuste está restringido: información de la app, Más opciones, «Permitir ajustes restringidos».
- **Gestos** compatibles con TalkBack. Triple toque y mantener con cuatro dedos activa siempre el **modo seguro**. Las dos teclas de volumen a la vez suspenden o reanudan Tiresias.
- **Voces** sin Internet: eSpeak NG, Pico y Eloquence (menos en x86 de 32 bits), más las voces neuronales Piper que se descargan aparte y las del sistema. Cada voz guarda su velocidad, su tono y su volumen.
- **Braille**: teclado en pantalla, y líneas HID por USB o Bluetooth con Android 15 o posterior.
- **Descargas** opcionales: voces Piper, OCR (PaddleOCR), dictado (Vosk), IA en el móvil (Gemma 4) y el complemento Gemini Nano.

## Compilar

Todo lo necesario está dentro de esta carpeta, sin instalar nada en Windows:

- `entorno\jdk`: Java 17.
- `entorno\gradle`: Gradle 9.8.2.
- `entorno\android-sdk`: SDK de Android 37, NDK 29.0.14206865 y CMake 3.22.1, porque eSpeak NG, Pico y QuickJS son código nativo. También el emulador y tres imágenes de sistema.
- `entorno\avd`: los emuladores:
  - `tiresias`: Android 17 con Google;
  - `antiguo`: Android 7.0;
  - `sinplay`: Android 17 sin Google Play Services.
- `entorno\gradle-home`: la caché de dependencias.

Haz doble clic en **`compilar.bat`** y elige una opción:

1. Compilar los APK. `Tiresias.apk` es el de arm64, el de casi todos los móviles; en `apk\` hay uno por procesador. Al lado queda `TiresiasNano.apk`.
2. Compilar e instalar en el móvil conectado por USB. Instala el APK de su procesador.
3. Instalar el APK ya compilado.
4. Ver los móviles conectados.
5. Abrir el emulador de Android 17. Las opciones 6 y 7 abren el de Android 7.0 y el que no tiene Google Play Services.
8. Compilar, abrir el emulador, instalar y activar Tiresias en él.
9. Pasar las pruebas automáticas, en el PC y en el emulador.
10. Medir la latencia en el emulador.
11. Compilar y publicar la actualización: en `dist\` y en `SynologyDrive\web\Tiresias-Android`.
12. Preparar o reparar el entorno.
13. Cerrar todo lo abierto: emulador, Gradle y adb.

Instalar en un móvil solo instala la app. Nunca se activa ni se cambia ningún servicio ni ajuste de accesibilidad de un móvil; eso solo se hace en el emulador.

## Firma

La primera compilación crea la firma en `firma\`, que no se sube al repositorio. **Guárdala**: las actualizaciones solo se instalan encima si están firmadas con la misma clave.

## Publicar

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Escribe las novedades en `NOVEDADES.txt`, en una sección `== <versión>`.
3. Ejecuta la opción 11 (`compilar.bat publicar`).

Después, la misma versión se sube como release `v<versión>` de `https://github.com/hxebolax/Tiresias-Android`, con los APK y `version.json`. La app busca `version.json` en `https://xebolax.synology.me/Tiresias-Android`, en GitHub o en los dos (lo elige el usuario en Ajustes, Actualizaciones) y descarga el APK de su procesador. Antes de instalar comprueba el tamaño, el SHA-256, el paquete, la versión y la firma.
