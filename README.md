# Tiresias para Android

**Un lector de pantalla libre para Android: completo, rápido, en español y sin Google Play Services.**

Tiresias dice en voz alta lo que hay en la pantalla y deja manejar el móvil con gestos, con un teclado, con una línea braille o con la voz. Está hecho para personas ciegas o con baja visión, a partir de lo que piden los usuarios de TalkBack y de los demás lectores para Android y de los fallos que tienen. Es el hermano del [Tiresias para Windows](https://xebolax.synology.me/Tiresias/) del mismo autor.

- **Versión actual:** 1.0.5 ([novedades](NOVEDADES.txt)).
- **Descarga:** [última versión en GitHub](https://github.com/hxebolax/Tiresias-Android/releases/latest) o en la [web de Tiresias](https://xebolax.synology.me/Tiresias-Android).
- **Manual de uso:** dentro de la app (menú lateral, Manual de uso) y en [español](app/src/main/assets/manual-es.html) o [inglés](app/src/main/assets/manual-en.html).
- **Fallos y sugerencias:** [Issues de GitHub](https://github.com/hxebolax/Tiresias-Android/issues).

> **Estado del proyecto.** Tiresias es un proyecto conceptual, desarrollado al 100 % con inteligencia artificial bajo la supervisión de su autor. Se ha probado sobre todo en el emulador y en pocos móviles reales, así que puede tener fallos. Pruébalo con calma, ten a mano tu lector de siempre y, si algo falla, cuéntalo en GitHub: cada aviso ayuda.

---

## Índice

- [Por qué Tiresias](#por-qué-tiresias)
- [Lo que puedes hacer](#lo-que-puedes-hacer)
- [Instalarlo](#instalarlo)
- [Requisitos](#requisitos)
- [Para programadores](#para-programadores)
- [Licencias y créditos](#licencias-y-créditos)

---

## Por qué Tiresias

- **Sin Google Play Services.** Ni Play Services, ni Firebase, ni analíticas. Funciona igual en un móvil sin servicios de Google, y se prueba en un emulador sin ellos. Lo único que usa algo de Google es opcional: el complemento de Gemini Nano.
- **En español de verdad.** La interfaz, las frases del lector, los símbolos, los emojis y el manual están en español de España, y también en inglés. Traducirlo a otro idioma es cuestión de archivos de texto ([guía](docs/traducir.md)).
- **Rápido.** Del gesto a la voz, unos 9 ms de mediana con la voz integrada, medido en el emulador. Prepara la frase del elemento siguiente antes de que la pidas y no hace nada lento en el hilo principal.
- **Nunca te deja atrapado.** Triple toque y mantener con cuatro dedos activa siempre el modo seguro, y ningún ajuste, complemento ni modo lo puede quitar.
- **Libre y privado.** Software libre (GPL 3 o posterior). No guarda, no registra y no envía nada de lo que lee.

---

## Lo que puedes hacer

### Explorar y navegar

- Explorar tocando la pantalla y deslizar de elemento en elemento, con el doble toque y la pulsación larga en cualquier sitio.
- Moverse por caracteres, palabras, líneas, párrafos, encabezados, enlaces, controles, campos, botones, casillas, listas, tablas, regiones, imágenes y ventanas, con el control de lectura o con acciones directas.
- Listas que se desplazan solas sin saltarse ningún elemento, ir al principio o al final de la lista entera e **ir al elemento número…** de una lista.
- **Vigilar un elemento**: Tiresias avisa con un sonido cada vez que cambia (el coche que se acerca, un marcador, el turno de una cola), aunque estés en otra parte de la pantalla.
- Lectura continua, búsqueda, lista de elementos e historial de lo dicho.
- El foco vuelve a su sitio al volver a una pantalla y al cerrar diálogos, menús y paneles; va donde la app pide al abrir una pantalla y, si la app rehace el elemento enfocado, sigue en el más parecido.
- **Marcas de posición** en cada pantalla para volver a un elemento, y leer la fila o la columna entera de una tabla.
- **Poner un valor** exacto en un deslizador, en vez de ir paso a paso.
- Los enlaces dentro de un texto (como los del aviso de Play Protect) se eligen y se pulsan uno a uno.
- Pantalla virtual con el texto reconocido, para las apps que no se pueden leer, y «Ayuda de otra persona», para que alguien maneje tu móvil mientras Tiresias sigue hablando.
- Si la exploración táctil se atasca (el dedo «pegado» de los Samsung con One UI 8.5), Tiresias lo nota y la reinicia solo.

### Gestos y teclas

- Gestos por defecto compatibles con los de TalkBack, que se pueden cambiar para todas las apps o solo para una.
- Gestos sencillos de dos y tres dedos, menú circular y activar al levantar el dedo.
- Teclas de volumen: mantener o pulsar tres veces hace lo que asignes.
- Música sin salir de donde estás: canción siguiente y anterior con una acción o con el control de lectura «Música».
- Teclado físico con dos mapas, el de TalkBack y uno al estilo de NVDA y de Tiresias para Windows, con navegación por letras en las webs y ayuda de teclado.

### Voz y motores incluidos

- **eSpeak NG**, **SVOX Pico** y **Eloquence** van dentro de la app y funcionan sin Internet.
- **Eloquence**, la voz clásica de los lectores de pantalla: diez idiomas (inglés de EE. UU. y británico, español de España y de México, francés de Francia y de Canadá, alemán, italiano, polaco y japonés) y sus ocho voces, con todos sus ajustes como en NVDA. De fábrica suena como el driver IBMTTS de NVDA, y al 100 % habla a su velocidad normal (desde la 1.0.5; antes iba unas diez veces más deprisa). Se basa en [evvdroid](https://github.com/trypsynth/evvdroid) y en [openevv](https://github.com/Mudb0y/openevv).
- Voces neuronales **Piper**, que se descargan aparte, y cualquier **voz del sistema**. Si una falla, habla la integrada.
- **Cada voz con su velocidad, su tono y su volumen**, como en NVDA: al cambiar de voz se pone la suya y Tiresias dice con qué velocidad queda; un perfil con otra voz no estropea la velocidad de la de antes. Eliges por qué voces pasa el control Voz.
- **Voz de las notificaciones**: pueden ir con otra de las voces incluidas, con su propia velocidad.
- **Voz a demanda**, como en NVDA: al moverse no habla, solo cuando se le pide algo.
- Tiresias es también un **motor de voz del sistema** que pueden usar otras apps.
- Salida del audio por el altavoz o los auriculares, y voz por el volumen multimedia o el de accesibilidad, que se cambia al momento con una acción (en los Samsung con One UI 8, subir el volumen en una llamada por Bluetooth calla al lector).
- Cambio de voz según el idioma del texto y, si la app no lo marca, detección del idioma de los textos largos (español o inglés).

### Verbosidad y pronunciación

- Niveles de verbosidad y de puntuación, y qué se dice de cada elemento y en qué orden.
- Números, mayúsculas, letras fonéticas (también tras una pausa: «b»... «Barcelona»), encabezados de tabla, y los nombres de 3886 emojis y cientos de símbolos en español y en inglés.
- Menos ruido: los cronómetros y ecualizadores respetan el plazo que pide la app, lo que llega tarde de un foco que ya pasó no se dice y las regiones vivas de las ventanas de fondo no cortan la lectura.
- Los controles sin nombre se dicen por su identificador traducido («contact_photo», «foto de contacto»).
- Diccionario de pronunciación, que importa el de NVDA.
- Temas de sonidos, con un archivo propio para cada sonido y una vibración distinta para cada uno.

### Edición de texto

- Eco por caracteres, palabras o los dos; menú de edición y modo selección.
- Faltas de ortografía: cuántas marca el corrector y cambiarlas por lo que propone.
- **Lo copiado por Tiresias** (los últimos 20 textos, solo en la memoria del móvil), copiar la lista entera y copiar por trozos de un texto que no se puede editar.
- **Elegir y copiar texto** de un mensaje o una web, que no es un campo: el texto pasa a una ventana donde se selecciona el trozo que se quiera.
- Decir la posición del cursor («Carácter 5 de 11, línea 2 de 3») y el formato del texto (negrita, cursiva, subrayado, enlaces).
- Teclados que hablan solos: con ellos, Tiresias no repite lo que se escribe.

### Notificaciones y llamadas

- Notificaciones leídas con un sonido, apps calladas, historial y Notificaciones recientes con sus acciones (abrir, responder, sus botones, copiar).
- **Resumen de los avisos**: «Tienes 5 avisos de las últimas 12 horas. WhatsApp: 3, de Ana y Juan».
- Contestar y colgar con un gesto (doble toque con dos dedos) las llamadas del teléfono y las de **WhatsApp, Telegram, Signal y las demás de internet**.
- Altavoz al apartar el móvil de la oreja durante una llamada.
- Qué se dice al encender la pantalla y al desbloquear (también la hora y el resumen de los avisos), y un gesto de estado que dice lo que elijas: hora, fecha, batería, conexión, avisos y más.

### Braille

- Teclado braille en pantalla de seis puntos, con puntos en espejo, eco y vibración.
- Líneas braille HID por USB o Bluetooth, sin controladores, con Android 15 o posterior: se ve lo enfocado con el cursor, y desde la línea se escribe y se maneja el móvil.
- Tablas informáticas de 8 puntos en dieciséis idiomas y braille literario de 6 puntos; combinaciones de teclas y cursor configurables; archivos BRF.
- Modo «primero braille»: con la línea conectada, la voz se calla o solo dice los avisos; si la línea se desconecta, la voz vuelve sola.

### Reconocimiento de texto, códigos QR e inteligencia artificial

- **Texto en imágenes (OCR)** sin Internet, con PaddleOCR.
- **Códigos QR y de barras** de la pantalla, con opciones para abrirlos o copiarlos, sin Internet.
- **«Leer con Tiresias»**: comparte una foto o una imagen desde cualquier app y Tiresias lee sus códigos y su texto.
- **Descripción de imágenes y de la pantalla con IA**, como tú elijas:
  - en el móvil y sin Internet, con Gemma 4 (con versiones para el chip Tensor de los Pixel 10 y 11);
  - con Gemini Nano, mediante el complemento Tiresias Nano, en los móviles que lo traen;
  - o en la nube con tu propia clave: Claude, OpenAI, Gemini o un servidor compatible. Gemini usa de fábrica un modelo concreto y, si no contesta, prueba solo otros.
- Preguntar a la IA sobre la pantalla, traducir un elemento, reconocer quién sale en una foto entre las personas que conoces, y botones sin nombre reconocidos en el móvil («posiblemente: Enviar»).
- Descripción continua de vídeos y juegos (solo lo que cambia) y un asistente que hace lo que le pides en la pantalla, siempre con tu confirmación.
- Si no se puede capturar la pantalla, Tiresias dice por qué (una app protegida, capturas demasiado seguidas) y lo reintenta solo.

### Dictado y órdenes por voz

- Dictado y órdenes por voz sin Internet, con Vosk: «atrás», «inicio», «leer pantalla», «qué hora es», «busca…» y muchas más.

### Modo senior

Para personas mayores que empiezan o solo quieren lo básico, hecho a partir de un estudio sobre los mayores ciegos y el móvil:

- Pocos gestos y fáciles: deslizar a los lados para moverse (la lista vuelve a empezar al final), arriba repite, abajo dice qué se puede hacer ahí y el doble toque abre.
- Lo importante en las teclas de volumen: las dos a la vez llevan siempre a Inicio diciendo la hora; mantener arriba dice la hora, la batería y los avisos; mantener abajo abre un menú sencillo; tres veces arriba pide ayuda; tres veces abajo, órdenes por voz.
- Pedir ayuda llama al contacto que elijas tras una cuenta atrás de 5 segundos que se cancela tocando la pantalla.
- Leer solo las llamadas y los mensajes, abrir con un toque en vez del doble toque y proteger los ajustes con un código. Al quitarlo, todo vuelve a estar como antes.

### Complementos y tienda

- Complementos en JavaScript que mejoran Tiresias o apps concretas, cada uno aislado y con sus propios ajustes. Se instalan desde la tienda o desde otras fuentes (GitHub, GitLab, Codeberg).
- En la tienda: Códigos de seguridad, Notificaciones a tu medida, Reloj parlante, Vigilante de la batería, Datos del texto, Lectura limpia, Modo braille, Letras decorativas, Anuncios y patrocinados, Quién llama, Recordatorios, Versión de la app y Cuánta luz hay.
- Reglas sin código para poner nombre, tipo, estado o pista a un elemento, ocultarlo o agrupar una fila con sus botones. Guía para hacerlos: [`docs/complementos.md`](docs/complementos.md).
- Versión de la API en el manifiesto (`apiMinima`, `apiProbada`): un complemento que pide una API más nueva no se instala ni sale en la tienda.
- API para que otras apps (juegos, sobre todo) hablen con Tiresias o escriban en la línea braille, con tu permiso: [`docs/api.md`](docs/api.md).

### Perfiles y etiquetas

- Etiquetas para los botones sin nombre, que se exportan e importan, y nombre propio para una pantalla, que se dice al abrirla en vez de su título.
- Perfiles por app (verbosidad, velocidad, puntuación).
- Copia de seguridad de toda la configuración en un archivo, para otro móvil (sin las claves de IA).
- Cortina de pantalla, toque directo por app (en toda la pantalla, también la barra de navegación) y mosaico de ajustes rápidos.

### Privacidad

- Nada de lo que Tiresias lee se guarda, se registra ni se envía. No hay publicidad ni estadísticas.
- Solo se conecta a Internet para lo que tú pides: descargas, actualizaciones, la tienda y la IA en la nube con tu clave, que se guarda cifrada en el móvil.
- La telemetría que traía ONNX Runtime (la biblioteca del OCR y de las voces neuronales) está quitada del todo desde la 1.0.4.
- Lo que una app marca como datos sensibles (Android 14: banca, códigos) no se captura para la IA ni para el reconocimiento de texto, y no llega a los complementos.

### Actualizaciones

- Sin tienda: Tiresias busca versiones nuevas, descarga el APK de tu procesador y comprueba el tamaño, la huella SHA-256 y la firma antes de instalar. Nunca instala nada sin preguntarte.
- De dónde se actualiza (y de dónde se lee la tienda): el servidor de Tiresias, GitHub, los dos (de fábrica: si el servidor falla, GitHub) o un servidor personalizado.
- Al abrir la app tras actualizar, salen las novedades de la versión.

---

## Instalarlo

1. **Descarga el APK** de la [última versión en GitHub](https://github.com/hxebolax/Tiresias-Android/releases/latest) o de la [web de Tiresias](https://xebolax.synology.me/Tiresias-Android). Hay un APK por procesador:

   | Archivo | Para qué móviles |
   |---|---|
   | `Tiresias_v1.0.5_arm64-v8a.apk` | Casi todos los móviles de los últimos años. Si dudas, este. |
   | `Tiresias_v1.0.5_armeabi-v7a.apk` | Móviles antiguos de 32 bits. |
   | `Tiresias_v1.0.5_x86_64.apk` y `_x86.apk` | Emuladores y algunas tabletas con procesador Intel. El de x86 de 32 bits no lleva Eloquence. |

   `TiresiasNano_v1.0.5.apk` es el complemento de Gemini Nano: solo hace falta en los móviles que lo traen, y también se descarga desde la app.

2. **Permite instalar la app.** Abre el APK desde el navegador o el gestor de archivos. Si Android dice que no puede instalar apps de esa fuente, entra en los ajustes que te ofrece, activa «Permitir de esta fuente» y vuelve atrás.

3. **El aviso de Play Protect.** Como Tiresias no viene de Google Play, Play Protect puede avisar de que no reconoce al desarrollador o de que la app puede ser peligrosa. Es el aviso normal para cualquier app instalada a mano, no que se haya encontrado nada. Para seguir: elige **«Más información»** y después **«Instalar de todas formas»**. Son enlaces dentro del texto del aviso: con TalkBack, explora el texto o usa su menú de enlaces; con Tiresias, desliza arriba o abajo para elegir cada enlace y toca dos veces. Si pregunta si quieres enviar la app para que la analice, puedes aceptar o no: no cambia nada para instalarla. Puede volver a salir al actualizar.

4. **Activa Tiresias** en Ajustes del sistema, Accesibilidad, Tiresias. Si Android dice que el ajuste está **restringido** (pasa con las apps instaladas a mano desde Android 13): abre la información de la app Tiresias, el menú Más opciones (los tres puntos de arriba), elige «Permitir ajustes restringidos» y vuelve a Accesibilidad.

5. **Si vienes de TalkBack**, deja los dos activados solo un momento: activa Tiresias y después desactiva TalkBack, para que no se pisen los gestos y la voz.

6. **Abre la app**: el asistente te ayuda a quitar el ahorro de batería (para que el sistema no cierre Tiresias) y a dar los permisos necesarios, y el tutorial te enseña los gestos. En los Samsung, Xiaomi, OnePlus, Huawei, Motorola y vivo te dice además qué tocar en esa marca (en los Samsung, también el Auto Blocker, que bloquea las actualizaciones), e Inicio avisa si el sistema cierra Tiresias sin que lo apagues.

Después, Tiresias se actualiza solo desde la propia app. Los APK de GitHub y de la web son los mismos, con la misma firma, así que puedes usar uno u otro.

---

## Requisitos

- **Android 7.0 o posterior**, pensado sobre todo para el último Android (17). Sin Google Play Services.
- **Android 11 o posterior** para lo que lee la pantalla (OCR, códigos QR, descripción con IA) y para que Tiresias haga el doble toque sobre lo que acaba de decir.
- **Android 15 o posterior** para las líneas braille (norma HID Braille, por USB o Bluetooth).
- **Espacio:** el APK ocupa de 60 a 110 MB según el procesador. Lo que se descarga aparte ocupa más: la IA del móvil (Gemma 4), unos 2,5 GB (el modelo grande, 3,7 GB).
- **Gemini Nano** solo en los móviles que lo traen (Pixel 9 y posteriores, algunos Samsung y otros) y con los servicios de Google del móvil.
- Android TV: sale en su lanzador y está preparado para el mando, sin probar en un televisor.

---

## Para programadores

### El entorno portátil

Todo lo necesario para compilar y probar va dentro de la carpeta `entorno\` del proyecto, sin instalar nada en Windows. Esa carpeta no se sube al repositorio: se prepara la primera vez.

| Pieza | Versión |
|---|---|
| JDK | Eclipse Temurin 17 |
| Gradle | 9.8.2 (con el plugin de Android 9.4.1 y Kotlin 2.4.21) |
| Android SDK | plataforma 37, build-tools 37.0.0 |
| NDK y CMake | NDK 29.0.14206865, CMake 3.22.1 |
| Emuladores | `tiresias` (Android 17 con Google APIs), `antiguo` (Android 7.0) y `sinplay` (Android 17 sin Google Play Services) |

Hacen falta Windows con PowerShell, conexión a Internet la primera vez y espacio: unos 20 GB entre el SDK y las imágenes de los emuladores, más lo que crecen las máquinas virtuales al usarlas (decenas de GB). Para compilar sin los emuladores, bastan unos pocos GB.

### Compilar desde cero en Windows

```bat
git clone https://github.com/hxebolax/Tiresias-Android.git
cd Tiresias-Android
compilar.bat preparar
compilar.bat compilar
```

- `compilar.bat preparar` ejecuta `herramientas\preparar_entorno.ps1`: descarga el JDK, Gradle, el SDK con el NDK y CMake, el emulador y sus imágenes, acepta las licencias del Android SDK y crea las tres máquinas virtuales. Es idempotente: lo que ya está no se vuelve a descargar. Solo para compilar, sin emuladores: `powershell -ExecutionPolicy Bypass -File herramientas\preparar_entorno.ps1 -SinEmulador`. Con `-Origen <carpeta>` copia lo que falte de otro `entorno` ya descargado.
- `compilar.bat compilar` compila la versión de publicación de Tiresias y del complemento Nano. Deja un APK por procesador en `apk\Tiresias_<procesador>.apk`, el de arm64 también como `Tiresias.apk`, y `TiresiasNano.apk`. Si no existe el entorno, lo prepara antes.

Sin argumentos, `compilar.bat` muestra un menú. Las órdenes son: `compilar`, `instalar` (en el móvil conectado por USB, el APK de su procesador; solo instala, nunca toca los ajustes de accesibilidad del móvil), `todo` (compilar e instalar), `emulador`, `antiguo`, `sinplay`, `probar` (compila la variante de pruebas, abre el emulador, la instala y activa Tiresias en él), `pruebas`, `latencia`, `publicar`, `preparar` y `cerrar` (cierra el emulador, Gradle y adb).

Con un Android SDK propio y JDK 17 también vale el wrapper de Gradle:

```bat
gradlew :app:assembleDebug      :: variante de pruebas, todos los procesadores en un APK
gradlew :app:assembleRelease    :: un APK por procesador
gradlew :nucleo:test            :: pruebas del núcleo en el PC
```

La **variante de pruebas** (`com.xebolax.tiresias.pruebas`) se instala junto a la normal y es la única que registra lo que dice el lector (etiqueta `TiresiasHabla`) y atiende las órdenes de las pruebas. La versión de publicación nunca registra el texto de la pantalla.

### Instalar y activar en un aparato sin TalkBack

En los aparatos sin TalkBack (reproductores, relojes, radios de coche, ROM libres), una persona ciega no puede activar un lector sola. Para ellos está `herramientas\instalar_y_activar.ps1`, que ejecuta **el dueño del aparato, en su aparato**, con la depuración USB activada (Opciones de desarrollador) y el aparato conectado por USB:

```bat
powershell -ExecutionPolicy Bypass -File herramientas\instalar_y_activar.ps1
powershell -ExecutionPolicy Bypass -File herramientas\instalar_y_activar.ps1 -Activar
powershell -ExecutionPolicy Bypass -File herramientas\instalar_y_activar.ps1 -Apk ruta\al\Tiresias.apk
```

- Sin argumentos, busca el APK del procesador del aparato en `dist\` (o en `apk\`) y lo instala por adb (`adb install -r`). Usa el adb del entorno portátil o, si no está, el del sistema. Solo admite un aparato conectado.
- Con `-Activar`, después de instalar pregunta y, si se contesta «s», activa Tiresias como servicio de accesibilidad por adb, sumándolo a los que ya hubiera.
- Con `-Apk`, instala ese APK en vez de buscarlo.

`compilar.bat instalar` es otra cosa: solo instala, nunca toca los ajustes de accesibilidad.

### Módulos

| Módulo | Qué es |
|---|---|
| `app` | El lector: el `AccessibilityService`, las pantallas, las voces, el braille, la visión, los complementos y el actualizador. |
| `nucleo` | Lógica pura en Kotlin, sin Android: composición de frases, verbosidad, puntuación, gestos, teclas, segmentos y braille. Se prueba en el PC. |
| `muestrario` | App de muestras con todos los tipos de control, para las pruebas del lector. No se publica. |
| `nano` | El complemento Tiresias Nano (Gemini Nano con ML Kit GenAI), en un APK aparte para que el principal no dependa de Google. Licencia Apache 2.0. |

Dentro de `app/src/main/java/com/xebolax/tiresias/`: `servicio` (el servicio de accesibilidad), `lectura` (lector, navegación, ejecutor de acciones, edición, avisos y memoria del foco), `arbol`, `voz` (cola de voz, eSpeak NG, Pico, Eloquence, Piper, voces del sistema y sonidos), `tts` (el motor de voz del sistema), `braille`, `vision` e `ia` (OCR, códigos, IA local, Nano y en la nube), `escucha` (Vosk), `scripts` (complementos con QuickJS y la tienda), `api`, `actualizaciones`, y la interfaz en `ui` y `ventanas`. El código nativo (eSpeak NG, Pico, QuickJS y sus puentes JNI) está en `app/src/main/cpp`; el envoltorio de Eloquence, en `app/src/main/java/org/evvdroid`.

Otras carpetas: `complementos\` (el código de cada complemento de la tienda), `tienda\` (la copia publicada de la tienda, que es el respaldo en GitHub), `temas\` (los temas de sonidos) y `herramientas\` (entorno, publicación y pruebas).

### Pruebas

| Qué | Dónde | Cómo |
|---|---|---|
| Núcleo: frases, verbosidad, números, gestos, braille | PC | `gradlew :nucleo:test` o `compilar.bat pruebas` |
| Los complementos de la tienda, con una API `tiresias` falsa | PC, con Node.js | `powershell -File herramientas\banco-complementos\probar.ps1` |
| Lo que dice el lector de cada control del muestrario y la latencia | Emulador | `herramientas\emulador_pruebas.ps1 -Que lector` (o `-Que latencia`) |
| Las pantallas de Tiresias con el Accessibility Test Framework | Emulador | `-Que accesibilidad` |
| API, primero braille, Eloquence, modo senior, complementos con QuickJS, copia de seguridad y lo nuevo de cada versión | Emulador | `-Que funciones` |
| Todas las clases, una cada vez | Emulador | `-Que todo` |

Las pruebas en el emulador activan Tiresias **solo en el emulador** y dejan los servicios de accesibilidad como estaban. Nunca se cambian en un móvil de verdad. Node.js no va en el entorno portátil: instálalo aparte si quieres el banco de complementos. El emulador de Android 7.0 no arranca con el acelerador de Windows (WHPX), así que esa versión no está probada en él.

### Firmar

La primera vez que se compila la versión de publicación, `compilar.bat` crea una firma propia en `firma\tiresias.jks`, con una clave aleatoria en `firma\firma.properties`. Esa carpeta nunca se sube al repositorio. Guárdala bien: sin ella no se puede actualizar la app instalada sin desinstalarla. Un APK compilado con otra firma no se instala encima del oficial: hay que desinstalar antes el que haya (y se pierden sus ajustes).

### Publicar una versión

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Añade la sección `== <versión>` en `NOVEDADES.txt`.
3. Ejecuta `compilar.bat publicar`. Compila y llama a `herramientas\publicar_actualizacion.ps1`, que:
   - deja en `dist\` un APK por procesador (`Tiresias_v<versión>_<procesador>.apk`), el complemento Nano y `version.json`, con la versión, el tamaño y el SHA-256 de cada APK y las novedades de esa versión;
   - publica la tienda: cada `complementos\<id>\complemento.js` pasa a `complementos/<id>.js` con su `catalogo.json`, y una copia queda en `tienda\`;
   - copia todo a la carpeta del servidor, primero los APK y al final `version.json`, para que nadie vea una versión cuyo APK aún no está.
4. En GitHub, crea la release `v<versión>` con los APK de `dist\` y el mismo `version.json`, y sube los cambios de `tienda\` a la rama principal: es lo que lee el actualizador cuando el origen es GitHub.

La dirección del servidor y el repositorio de GitHub se cambian al compilar con `-PurlActualizaciones=...`, `-PrepoGithub=...` y `-PgithubActivo=true|false`.

### Documentación

- [`docs/04_entorno_herramientas_y_actualizador.md`](docs/04_entorno_herramientas_y_actualizador.md): el entorno, las pruebas en el emulador y el actualizador.
- [`docs/complementos.md`](docs/complementos.md): cómo hacer complementos.
- [`docs/api.md`](docs/api.md): la API para otras apps.
- [`docs/traducir.md`](docs/traducir.md): cómo traducir Tiresias.
- [`docs/05_ia_local.md`](docs/05_ia_local.md): la IA en el móvil.

---

## Licencias y créditos

Tiresias es software libre con licencia **GPL-3.0-or-later** ([`LICENSE`](LICENSE)), porque incluye eSpeak NG. El complemento `nano/` es Apache 2.0. El código de terceros conserva sus licencias; el detalle está en [`NOTICE`](NOTICE), en [`TERCEROS.txt`](app/src/main/assets/licencias/TERCEROS.txt) y, en la app, en Acerca de y actualizaciones.

Incluido en la app:

- **eSpeak NG** (Jonathan Duddington, Reece H. Dunn y colaboradores), GPL-3.0-or-later, con **speechPlayer** de NV Access.
- **SVOX Pico** (SVOX AG, del Android Open Source Project), Apache 2.0. El puente JNI sigue el de Shortcut Menu, de aaron-gh.
- **Eloquence**: el motor [openevv](https://github.com/Mudb0y/openevv) (Stanislaw Przedzinkowski, los colaboradores de OpenEVV SAPI5 y Felix Steindorff) y su envoltorio para Android [evvdroid](https://github.com/trypsynth/evvdroid) (Quin Gillespie), los dos con licencia MIT. Los datos de las voces están transcritos de Embedded ViaVoice de IBM y no tienen licencia libre; quién tiene hoy sus derechos está en un pleito. Están incluidos a petición del autor de Tiresias (más detalle en `NOTICE`).
- **ONNX Runtime** (Microsoft), MIT, sin su telemetría.
- **ZXing** (los autores de ZXing), Apache 2.0, para los códigos QR y de barras.
- **QuickJS-ng** (Fabrice Bellard, Charlie Gordon, Ben Noordhuis y Saúl Ibarra Corretgé), MIT, el motor de los complementos.
- **Backtalk**: el puente JNI de QuickJS y la política del altavoz en las llamadas, Apache 2.0, con cambios.
- **liblouis**: las tablas braille, LGPL-2.1-or-later (y LGPL-3.0-or-later la de español).
- **LiteRT-LM** (Google), **Vosk** (Alpha Cephei) con Kaldi y JNA, **Gson**, **AndroidX**, **Material Components**, **Kotlin**, **kotlinx.coroutines** y **JSpecify**, Apache 2.0; el icono «touch_app» de Material Symbols, Apache 2.0.
- Los nombres de los emojis y símbolos del **Unicode CLDR** (Unicode-3.0) y los símbolos del `symbols.dic` de **NVDA** (GPL-2).

Se descargan aparte, solo si tú quieres, cada uno con su licencia: los modelos de **PaddleOCR**, el modelo de voz de **Vosk**, las voces **Piper**, los modelos **Gemma 4** y el complemento Tiresias Nano, que usa Gemini Nano de Google.

El diseño de Tiresias aprende de TalkBack, talkback-foss, Backtalk, Appt, NVDA y Tiresias para Windows, y de las peticiones de sus usuarios. Gracias a todos ellos.

**Autor:** Héctor J. Benítez Corredera ([hxebolax](https://github.com/hxebolax)).
