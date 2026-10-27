# Entorno, herramientas, pruebas en el emulador y actualizador

Este documento recoge cómo lo hace Rescate Accesible (`E:\Antigravity\RescateAccesible`) y cómo se trasladará a Tiresias. Todo lo que aquí se describe se ha leído en el código de Rescate Accesible el 26 de octubre de 2026; en Tiresias todavía no hay nada creado.

## 1. Lo que tiene Rescate Accesible

### compilar.bat

Un único archivo por lotes con menú y también con órdenes sin menú. Fija todas las variables al entorno portátil de la carpeta del proyecto (no instala nada en Windows):

- `JAVA_HOME` en `entorno\jdk` (Temurin 17).
- `ANDROID_HOME` y `ANDROID_SDK_ROOT` en `entorno\android-sdk`.
- `ANDROID_AVD_HOME` en `entorno\avd` (máquina virtual «pruebas»).
- `GRADLE_USER_HOME` en `entorno\gradle-home` (caché de dependencias).
- Gradle 8.11.1 en `entorno\gradle` (lo usa en lugar del wrapper, siempre con `--no-daemon`).

Opciones del menú: compilar el APK, compilar e instalar en el móvil (detecta móviles autorizados, avisa de los no autorizados, ofrece desinstalar si la firma es distinta), instalar el APK ya compilado, ver móviles, abrir el emulador (espera a `sys.boot_completed`), compilar más emulador más instalar y abrir, pruebas (unitarias en el PC y, si hay emulador, las instrumentadas), simulación de averías, regenerar grabaciones, publicar la actualización, preparar o reparar el entorno y cerrar todo (emulador, demonios de Gradle y adb). Sin menú: `compilar.bat compilar | instalar | todo | emulador | probar | pruebas | simulacion | grabaciones | publicar | cerrar`.

La primera compilación crea la firma propia (`firma\rescate.jks` y `firma\firma.properties` con clave aleatoria de 24 caracteres). `firma\`, `entorno\`, `dist\` y los APK están en `.gitignore`.

### herramientas\

- `preparar_entorno.ps1`: descarga lo que falte (JDK 17, Gradle, cmdline-tools, platform-tools, plataforma, build-tools, NDK 27.2, CMake 3.22.1, emulador e imagen `system-images;android-35;google_apis;x86_64`), acepta las licencias del SDK, crea el AVD «pruebas» (Pixel 6, teclado físico, 4 GB). Es idempotente. Con `-Origen <ruta>` copia lo que falte de otro `entorno` ya descargado (mucho más rápido) y con `-SinEmulador` solo prepara la compilación.
- `gradle.cmd`: lanza Gradle con el entorno portátil.
- `emulador_pruebas.ps1 -Que voz|accesibilidad|grabaciones|simulacion`: pasa una clase de pruebas instrumentadas concreta en el emulador (exige `emulator-*`, nunca un móvil), recoge resultados con `adb pull` y genera informes.
- `informe_simulacion.ps1`: convierte el JSON de la simulación en un informe Markdown y en HTML incluido en la app.
- `publicar_actualizacion.ps1`: comprueba con `aapt2 dump badging` que el APK es de la versión de `app\build.gradle.kts`, extrae la sección `== <versión>` de `NOVEDADES.txt`, crea `dist\<App>_v<versión>.apk` y `dist\version.json` (versión, versionCode, archivo, SHA-256, tamaño, fecha y novedades) y los copia a la carpeta del servidor, **primero el APK y al final `version.json`**, para que nadie vea una versión cuyo APK aún no está.
- `recorrido_talkback.py`: ayuda de recorrido con TalkBack.

### Pruebas

- Unitarias en el PC (`testDebugUnitTest`) para la lógica pura.
- Instrumentadas en el emulador: voces, recorrido de accesibilidad con el Accessibility Test Framework (`AccesibilidadTest`) y la simulación de averías.
- La variante de depuración se instala junto a la normal (`applicationIdSuffix = ".pruebas"`).

## 2. El actualizador de Rescate Accesible

Archivos: `app/src/main/java/com/xebolax/rescate/actualizaciones/Actualizador.kt` y `GestorActualizaciones.kt` (portados a su vez de Lector Universal).

### Cómo construye la dirección

- En `app/build.gradle.kts`: `urlActualizaciones = findProperty("urlActualizaciones") ?: "https://xebolax.synology.me/RescateAccesible"`, que pasa a `BuildConfig.URL_ACTUALIZACIONES`.
- La carpeta local `C:\Users\xebol\SynologyDrive\web\RescateAccesible` es la que Synology Drive sube al servidor: la parte de la dirección después de `https://xebolax.synology.me/` es el nombre de la carpeta dentro de `SynologyDrive\web`.
- En la carpeta están `version.json` y todos los `RescateAccesible_v<versión>.apk` publicados.
- GitHub como respaldo: `BuildConfig.REPO_GITHUB = "hxebolax/RescateAccesible"`; primero `releases/latest/download/version.json` y, si no está, la API `releases/latest` (busca el recurso `RescateAccesible_v<versión>.apk` y su `digest` SHA-256).

### Qué hace

- Orígenes elegibles en Ajustes: automático (servidor y, si falla, GitHub), solo el servidor, solo GitHub o un servidor personalizado con la misma estructura.
- Comprobación automática al abrir la app como mucho cada 6 horas (sin molestar si no hay conexión) y comprobación a mano con ventana de espera y Cancelar.
- Diálogo con versión, tamaño, origen y novedades; botones Actualizar, Más tarde y, en la automática, Omitir esta versión.
- Descarga con progreso (sin región viva que cambie sin parar), reintentos y continuación con `Range` si se corta, hasta 8 cortes seguidos sin avanzar; si una fuente falla del todo, prueba la otra.
- Verificación antes de instalar: tamaño, SHA-256, que el paquete es el mismo, que el `versionCode` es mayor y que la firma coincide con la instalada.
- Permiso «Instalar aplicaciones desconocidas» pedido solo cuando hace falta e instalación con `PackageInstaller`.
- Al abrir tras actualizar: aviso «actualizada a la versión X» y limpieza de la caché de descargas.

## 3. Cómo será en Tiresias

### Identidad

- Paquete: `com.xebolax.tiresias` (depuración: `com.xebolax.tiresias.pruebas`, instalable junto a la normal).
- Dirección del servidor: `https://xebolax.synology.me/Tiresias-Android`.
- Carpeta local del servidor: `C:\Users\xebol\SynologyDrive\web\Tiresias-Android` (ya existe y está vacía).
- Archivos publicados: `version.json` y `Tiresias_v<versión>_<procesador>.apk` (uno por procesador; `version.json` los lista en `abis`).
- GitHub: el código del respaldo se incluye pero queda **desactivado** hasta que se suba el repositorio (`BuildConfig.GITHUB_ACTIVO = false` y `REPO_GITHUB` vacío). El origen por defecto es «solo el servidor». Cuando exista el repositorio, basta con poner su nombre, activar el indicador y publicar en cada release el mismo `version.json` y el APK; el origen por defecto pasará a «automático».

### Diferencias por ser un lector de pantalla

- **El usuario depende de Tiresias para manejar el instalador.** Tiresias sigue funcionando mientras se muestra el diálogo de instalación del sistema y lo lee como cualquier otra ventana. Antes de instalar, Tiresias dice que va a reiniciarse y que volverá a hablar en unos segundos.
- **Tras la actualización** Android vuelve a conectar el servicio de accesibilidad (sigue activado). Al conectar, Tiresias comprueba si viene de una actualización y dice «Tiresias actualizado a la versión X».
- **Nunca se actualiza solo**: siempre con la confirmación del usuario.
- **Paquetes de recursos** (voces neuronales, datos de OCR, tablas braille adicionales, paquetes de sonidos, diccionarios): mismo mecanismo y mismas comprobaciones (tamaño y SHA-256), con un `catalogo.json` en `Tiresias-Android/recursos/`, al estilo de la tienda de complementos del Tiresias de Windows (`catalogo.json` en `web\Tiresias\complementos`).

### compilar.bat de Tiresias

Las mismas opciones que Rescate Accesible, adaptadas:

1. Compilar el APK (`Tiresias.apk`).
2. Compilar e instalar en el móvil conectado (solo instala: nunca cambia ajustes de accesibilidad del móvil).
3. Instalar el APK ya compilado.
4. Ver móviles conectados.
5. Abrir la máquina virtual de pruebas (moderna).
6. Abrir la máquina virtual antigua (compatibilidad).
7. Abrir la máquina virtual sin Google Play Services.
8. Compilar, abrir el emulador, instalar y **activar Tiresias en el emulador**.
9. Pruebas automáticas: en el PC y en el emulador (lectura de cada control, navegación, gestos simulados, voz, latencia, accesibilidad de las pantallas de Tiresias).
10. Medir la latencia en el emulador e informe.
11. Compilar y publicar la actualización (`dist` y `SynologyDrive\web\Tiresias-Android`).
12. Preparar o reparar el entorno (con `-Origen E:\Antigravity\RescateAccesible\entorno` para copiar lo ya descargado).
13. Cerrar todo lo abierto.

Sin menú: `compilar.bat compilar | instalar | todo | emulador | antiguo | sinplay | probar | pruebas | latencia | publicar | preparar | cerrar`.

### Máquinas virtuales

- `tiresias`: Android 16 (API 36) con Google APIs, Pixel, teclado físico. La principal.
- `antiguo`: la versión mínima que se soporte (API 24 o 26), para comprobar la compatibilidad.
- `sinplay`: imagen AOSP sin Google Play Services (`system-images;android-36;default;x86_64`), para comprobar que todo funciona sin ellos.

### Pruebas propias de un lector

`adb shell input` no pasa por el filtro de accesibilidad, así que no sirve para probar gestos del lector. Tiresias tendrá, **solo en la variante de depuración**:

- Un receptor de órdenes de prueba (permiso firmado, no exportado a otras apps) que simula gestos y teclas llamando al mismo código que `onGesture` y el filtro de teclas.
- Un registro de lo que Tiresias dice (etiqueta `TiresiasHabla`), para que las pruebas comprueben las frases. En la versión de publicación no existe: nunca se registra el texto de la pantalla.
- Un módulo `muestrario`: una app de pruebas con todos los tipos de control (botones, casillas, interruptores, deslizadores, listas, cuadrículas, pestañas, campos de texto y contraseña, regiones vivas, diálogos, WebView con encabezados, enlaces, tablas, formularios y regiones ARIA, Compose y Views), para comprobar qué lee Tiresias en cada uno.
- La activación de Tiresias por `adb` **solo en el emulador**, guardando y restaurando el valor anterior de `enabled_accessibility_services`.
