# Inteligencia artificial en el propio móvil

Petición del usuario (26 de octubre de 2026): que quien quiera pueda **descargarse un modelo** para usarlo sin Internet o, si tiene un **Pixel** (u otro móvil compatible), **usar los modelos que trae el propio móvil**. Base: lo que ya hace Lector Universal (`E:\Antigravity\ePubViewer-Android`, paquete `com.xebolax.lector.ia`).

## 1. Lo que hace Lector Universal

`ia/AsistenteIA.kt`, `ia/ModeloLocal.kt` y `ia/ConversacionIA.kt`:

- Cinco proveedores: **local** (modelo descargado), **dispositivo** (Gemini Nano del móvil), **Gemini**, **OpenAI** y **Claude** con la clave del usuario. «Automático» elige el local si está descargado, si no Gemini Nano si el móvil lo tiene, si no la primera clave que haya.
- **Modelo descargable**: Gemma 4 E2B de Google en formato `.litertlm` (2 588 147 712 bytes), desde `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`, con la biblioteca **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android:0.18.1`). Se descarga con el `DownloadManager` del sistema (sigue aunque se cierre la app, se reanuda, opción de solo wifi); solo en móviles de 64 bits; avisa si hay poca memoria (unos 6 GB recomendados); procesador por defecto y tarjeta gráfica opcional (con vuelta al procesador si falla); entiende imágenes y texto.
- **Gemini Nano**: ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt:1.0.0-beta4`), Android 8 o posterior; estados no disponible, descargable, descargando y disponible; el sistema baja el modelo la primera vez; admite imagen y texto.
- Instrucciones del sistema en el idioma de la app y respuestas pensadas para escuchar.

## 2. Lo que dice Google de los modelos del móvil (consultado el 26 de octubre de 2026)

Fuente: [Overview of the ML Kit GenAI APIs](https://developers.google.com/ml-kit/genai), [Image description](https://developers.google.com/ml-kit/genai/image-description/android), [Prompt API, primeros pasos](https://developers.google.com/ml-kit/genai/prompt/android/get-started).

- Funcionan sobre **AICore**, un servicio del sistema, con **Gemini Nano**. Todo se procesa en el móvil.
- API: resumen, corrección, reescritura, **descripción de imágenes** (beta, `com.google.mlkit:genai-image-description:1.0.0-beta1`, **solo en inglés** por ahora), **Prompt** (beta, `genai-prompt:1.0.0-beta4`, texto e imágenes, hasta 10 imágenes, entrada de menos de 4000 tokens) y **reconocimiento de voz** (alfa; modo básico en la mayoría de móviles con Android 12 o posterior, modo avanzado en Pixel 10 y 11).
- Móviles: Pixel 9, 10 y 11; Galaxy S25, S26 y los plegables recientes; y modelos recientes de Honor, iQOO, Lenovo, Motorola, OnePlus, OPPO, POCO, realme, Sharp, Sony, vivo y Xiaomi. Versiones del modelo: nano-v2, nano-v3 (Pixel 9 y 10, Galaxy S26) y nano-v4 (Pixel 11, Galaxy Z Fold8 y Flip8).
- Requisitos: Android 8 o posterior (API 26), **la app de AICore**, **sin el gestor de arranque desbloqueado**. Justo después de configurar el móvil o de reiniciar AICore puede fallar: hay que consultar el estado antes.
- **Restricción importante**: la inferencia solo se permite cuando la app es **la aplicación en primer plano**. Desde un servicio en primer plano devuelve `BACKGROUND_USE_BLOCKED`. Además hay cuota por app (`BUSY`, y `PER_APP_BATTERY_USE_QUOTA_EXCEEDED` para la diaria).
- Las páginas de Google no dicen qué idiomas admite el modelo de Prompt; la descripción de imágenes, solo inglés.

## 3. Consecuencias para Tiresias

### Un servicio de accesibilidad no está en primer plano

Tiresias es un servicio, y cuando el usuario está en otra app no es la app en primer plano: AICore le negaría la inferencia. Por eso **Gemini Nano siempre se usa desde una ventana propia** («Resultado de la IA»): Tiresias toma la captura o el texto, abre su ventana encima, la ventana pide la respuesta y la muestra como texto navegable (y Tiresias la lee). Al cerrarla, el foco vuelve donde estaba.

### Licencia y Play Services: Gemini Nano en un complemento aparte

ML Kit GenAI es una biblioteca propietaria de Google que depende de AICore y de su ecosistema. Tiresias es GPL-3.0-or-later e incluye eSpeak NG (GPL-3.0-or-later): meter ML Kit en el mismo APK rompería la licencia de eSpeak NG y la promesa de «sin Play Services».

Solución: **un complemento separado, «Tiresias IA del móvil»** (`com.xebolax.tiresias.nano`), un APK pequeño con licencia Apache 2.0, firmado con la misma clave, que:

- Contiene ML Kit GenAI (Prompt y descripción de imágenes).
- Tiene la ventana de resultado (así es su app la que está en primer plano y AICore no la bloquea).
- Recibe de Tiresias la imagen o el texto por un `content://` con permiso temporal, y devuelve el resultado con una difusión protegida por un permiso de firma (solo Tiresias la recibe).
- Se ofrece solo si el móvil tiene AICore; se instala y se actualiza con el mismo actualizador (en `version.json`, apartado `complementos`).

Tiresias sin el complemento funciona entero; con él, en un Pixel o compatible, usa Gemini Nano.

### Modelo descargable dentro de Tiresias

- **LiteRT-LM** (Apache 2.0, compatible con GPLv3) va dentro de Tiresias.
- Modelos ofrecidos: **Gemma 4 E2B** (unos 2,6 GB, móviles con unos 6 GB de memoria o más) y **Gemma 4 E4B** (más capaz, móviles con unos 12 GB). Ambos con licencia Apache 2.0 según las fichas consultadas; se comprueba la licencia en la ficha oficial de `litert-community` antes de publicarlo.
- **En un proceso aparte** (`android:process=":ia"`): un modelo de varios gigas no puede compartir proceso con el lector. Si Android lo cierra por falta de memoria, Tiresias sigue hablando y lo dice.
- Descarga con el `DownloadManager` del sistema (como Lector Universal), opción de solo wifi, comprobación de tamaño y SHA-256, cancelar y borrar. También se podrá descargar del servidor propio (`Tiresias-Android/recursos/`) si Hugging Face falla.
- Procesador por defecto y tarjeta gráfica opcional, con vuelta al procesador si falla. Solo 64 bits; se avisa de la memoria.
- Siempre a petición del usuario, nunca automáticamente.

### Proveedores en Tiresias

- **Del móvil** (Gemini Nano, con el complemento).
- **Descargado** (Gemma 4 con LiteRT-LM).
- **En Internet con clave propia**: Claude (Anthropic), OpenAI, Gemini y un servidor compatible con OpenAI (local o propio, por ejemplo Ollama o LM Studio en el PC de casa).
- **Automático**: descargado si está; si no, el del móvil si existe; si no, la primera clave configurada. Se puede elegir uno por función.

### Para qué la usa Tiresias

- Describir la imagen enfocada, la pantalla entera o una zona.
- Proponer etiquetas para botones sin nombre (el usuario las confirma).
- Preguntar sobre lo que hay en la pantalla (conversación con la captura).
- Resumir, traducir y corregir el texto enfocado o seleccionado.
- Leer CAPTCHAs visuales a petición.
- Asistente que actúa sobre controles inaccesibles, siempre con confirmación de cada acción.
- Descripción continua de juegos y vídeo (con límite de frecuencia y de cuota).

Nunca se envía ni se procesa nada sin que el usuario lo pida; nunca en campos de contraseña; con los proveedores de Internet se dice antes qué se envía y a quién.
