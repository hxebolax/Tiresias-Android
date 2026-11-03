# Cómo hacer un tema de sonidos para Tiresias

Un tema de sonidos es un archivo ZIP con un archivo de audio por cada sonido de Tiresias. Se importa en el móvil y se elige como cualquier otro tema. No hace falta programar nada.

## Lo que lleva el ZIP

- Un archivo por sonido, con el **nombre exacto** de la tabla de abajo, en minúsculas. Por ejemplo, `foco.wav` u `ogg`.
- Formatos: **WAV, OGG o MP3**.
- Puedes poner solo algunos sonidos. Los que falten suenan como los de Tiresias de siempre.
- Los archivos pueden ir sueltos en el ZIP o dentro de una carpeta: Tiresias solo mira el nombre del archivo.
- Opcional: un `tema.json` con el nombre y la descripción que se enseñan al elegirlo (ver más abajo). Sin él, el tema se llama como el ZIP.
- Límites: cada sonido, 2 MB como mucho; el tema entero, 24 MB.

## Los sonidos

| Archivo | Cuándo suena |
|---|---|
| `foco` | Un elemento recibe el foco al deslizar |
| `toque` | El dedo pasa por un elemento al explorar la pantalla |
| `activar` | Se activa algo (doble toque) |
| `mantener` | Se mantiene pulsado algo |
| `desplazar` | La lista se ha desplazado |
| `borde` | No hay más elementos en esa dirección |
| `vuelta` | Se ha dado la vuelta (del último al primero) |
| `ventana` | Pantalla o ventana nueva |
| `error` | Algo no se ha podido hacer |
| `acciones` | El elemento tiene acciones propias |
| `selector` | Cambia el control de lectura |
| `notificacion` | Llega una notificación |
| `encendido` | Tiresias se enciende |
| `apagado` | Tiresias se apaga o se apaga la pantalla |
| `gesto` | Se ha reconocido un gesto |
| `tecla` | Tecla del teclado en pantalla |
| `edicion` | Un campo de texto, o el cursor al principio o al final |
| `cargando` | Algo está cargando |
| `fila` | Al deslizar se pasa del final de una fila al principio de la siguiente (iconos, celdas) |
| `tecla_espacio` | Espacio en el teclado braille en pantalla |
| `tecla_borrar` | Borrar en el teclado braille en pantalla |
| `tecla_intro` | Intro en el teclado braille en pantalla |

## Consejos

- **Cortos.** `foco` y `toque` suenan con cada deslizamiento y cada elemento que se toca: lo ideal es que duren menos de 50 milisegundos, y no más de 100. Si duran más, se pisan al moverse deprisa. Los demás, mejor por debajo de medio segundo.
- **Sin silencio al principio.** Recorta el silencio del arranque; si no, el sonido llega tarde respecto a la voz.
- **El mismo volumen en todos.** Normaliza los archivos para que ninguno destaque. Que no sean más fuertes que la voz: suenan por el mismo volumen que ella.
- **Mono, 22 050 o 44 100 Hz y 16 bits** es lo más seguro. Si activas «Sonido según la posición» en Ajustes, Tiresias los mueve a izquierda o derecha él solo.
- **Distintos entre sí.** `borde`, `error` y `vuelta` deben distinguirse a la primera, sin mirar.

## El tema.json (opcional)

Un archivo de texto en UTF-8 llamado `tema.json`, dentro del ZIP:

```json
{
	"nombre": "Mi tema",
	"descripcion": "Sonidos de madera, cortos y suaves.",
	"nombre_en": "My theme",
	"descripcion_en": "Short, soft wooden sounds.",
	"autor": "Tu nombre"
}
```

`nombre_en` y `descripcion_en` se enseñan si Tiresias está en inglés.

## Importarlo, probarlo y compartirlo

1. Copia el ZIP al móvil.
2. En Tiresias: Ajustes, Voz, «Sonidos y vibración», «Sonidos y vibraciones uno a uno», «Importar un tema (ZIP)». Elige el ZIP.
3. En esa misma pantalla, elige el tema en «Tema». En la lista «Cada sonido», el menú de acciones de cada uno (o mantenerlo pulsado) tiene «Probar».
4. Para compartirlo: «Exportar los sonidos de ahora (ZIP)» guarda en un ZIP los sonidos que suenan ahora (los del tema y los archivos propios que hayas puesto a cada sonido).

También puedes empezar de otro: exporta un tema que te guste, cambia los archivos que quieras y vuelve a importarlo con otro nombre de ZIP.

## Generar sonidos por código

Los ocho temas que vienen dentro de Tiresias se generan con `herramientas/generar_temas_sonido.py`, que sintetiza cada sonido sin archivos de terceros. Es un buen punto de partida para hacer un tema sintetizado: copia uno de los temas del archivo, cambia sus notas y su timbre, y ejecútalo con Python 3 (`python herramientas/generar_temas_sonido.py`). Deja los temas en `temas/<id>/`; para usarlo en el móvil, comprime la carpeta en un ZIP e impórtalo.
