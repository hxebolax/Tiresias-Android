# Cómo hacer complementos para Tiresias

Esta guía es para quien quiere hacer su primer complemento de Tiresias o necesita la referencia completa de la API. Todo lo que dice está sacado del código de Tiresias 1.0.4:

- El motor y la API: `app/src/main/java/com/xebolax/tiresias/scripts/Scripts.kt` (el preludio JavaScript `PRELUDIO` y la función `atender`).
- La tienda: `app/src/main/java/com/xebolax/tiresias/scripts/Tienda.kt`.
- Los temas de sonido: `app/src/main/java/com/xebolax/tiresias/voz/TemasSonido.kt`.
- Los ejemplos: la carpeta `complementos/` del proyecto, con 13 complementos reales.

## Índice

1. [Qué es un complemento](#qué-es-un-complemento)
2. [Tutorial: tu primer complemento](#tutorial-tu-primer-complemento)
3. [El manifiesto](#el-manifiesto)
4. [Las traducciones](#las-traducciones)
5. [Los ajustes propios](#los-ajustes-propios)
6. [Las reglas sin código](#las-reglas-sin-código)
7. [Referencia de la API](#referencia-de-la-api)
8. [Buenas prácticas](#buenas-prácticas)
9. [Cómo probar un complemento](#cómo-probar-un-complemento)
10. [La tienda](#la-tienda)
11. [Los temas de sonido](#los-temas-de-sonido)
12. [Ejemplos reales](#ejemplos-reales)

## Qué es un complemento

Un complemento es un único archivo `complemento.js`: un módulo de JavaScript que exporta su `manifest` y usa la API `tiresias`. Con él puedes:

- cambiar lo que Tiresias dice de un elemento o de una notificación (arreglar un botón sin nombre, quitar ruido, avisar de un anuncio);
- añadir órdenes al menú «Órdenes de los complementos» de Tiresias;
- añadir acciones propias al menú de acciones de un elemento;
- hablar, hacer sonidos, escribir en la línea braille y quedarse las teclas de la línea;
- copiar al portapapeles, abrir enlaces, el marcador, el correo, un mapa o una app;
- hacer preguntas sencillas al usuario (una lista, aceptar o cancelar, un texto);
- saber la hora, la batería, si suena una llamada, la luz que hay, los datos de una app y la pantalla que se abre;
- tener sus propios ajustes, con su pantalla, y guardar sus datos;
- arreglar apps sin escribir código, con reglas en el manifiesto.

### Cómo corre

Cada complemento tiene su propio motor QuickJS, aislado de los demás y de Android: no ve Java, ni archivos, ni red, ni otras apps. Solo habla con Tiresias por la API `tiresias`, y Tiresias solo le hace caso en lo que permite su manifiesto.

| Límite | Valor | Qué pasa si se pasa |
|---|---|---|
| Memoria del motor | 16 MB | Error en la llamada |
| Pila | 512 KB | Error en la llamada |
| Tiempo para cargar el módulo | 500 ms | No se carga |
| Tiempo de cada llamada (cada gancho, orden, temporizador o respuesta) | 30 ms | Se corta y cuenta como fallo |
| Fallos seguidos antes de apagarse | 3 | Se desactiva y Tiresias dice «El complemento … se ha apagado porque fallaba» |
| Temporizadores pendientes | 30 | Los demás no se programan |
| Datos guardados (`guardar`) | 64 KB | No se guarda |
| Tamaño del archivo en la tienda | 512 KB | No se descarga |

Un fallo es una excepción sin atender o pasarse de tiempo. Cada llamada que sale bien pone la cuenta a cero. Un complemento apagado por fallar se vuelve a activar a mano en Complementos.

### La salida de emergencia

- El modo seguro (de fábrica, triple toque mantenido con cuatro dedos, un gesto que nada puede quitar) apaga todos los complementos al momento, además de quitar la cortina, volver a eSpeak y otras cosas.
- La acción «Apagar los complementos» hace solo eso, y se puede asignar a un gesto o una tecla.
- En la pantalla Complementos está «Apagar todos los complementos».

Los complementos quedan apagados hasta que el usuario los enciende otra vez en Complementos, aunque se reinicie el móvil.

### Lo que no puede hacer

- **No pulsa, no desliza, no escribe ni mueve nada en otras apps.** La API no tiene ninguna función para eso. Las dos cosas que se acercan las pide siempre el usuario: la regla `activar` (al activar él un elemento, Tiresias pulsa el hijo indicado) y la regla `agrupar` (los botones de dentro pasan a su menú de acciones). `abrir` y `abrirApp` abren algo, pero no tocan nada dentro.
- **No lee la pantalla entera.** Solo recibe el elemento que se va a decir, el enfocado (`tiresias.elemento()`), las notificaciones y el título de las pantallas nuevas.
- **No sale a internet** ni lee o escribe archivos.
- **No llama por teléfono**: `tel:` solo abre el marcador con el número.
- **No ve la voz ni los ajustes de Tiresias**, salvo el idioma.

### Los campos de contraseña

Lo de un campo de contraseña nunca llega a un complemento: con el foco en uno, `alEnfocar` no se llama (se dice lo de siempre), `alAcciones` no se pregunta y `tiresias.elemento()` devuelve `null`. Aun así, nunca guardes, copies ni registres el `texto` de un campo sin necesidad.

### Cómo se carga

Al encenderse Tiresias y al instalar, activar o desactivar un complemento, Tiresias vuelve a cargar los complementos activados. Para cada uno:

1. lee el `manifest` exportado (en un motor aparte, en el que la API no hace nada);
2. ejecuta el módulo entero una vez (el código que está fuera de las funciones), ya con la API atendida: lo que pide en el nivel superior (sus temporizadores, el idioma para nombrar sus órdenes, sus ajustes) tiene respuesta;
3. apunta las órdenes que ha registrado (`tiresias.orden`) y si quiere saber de la batería (`tiresias.alBateria`) o de las pantallas nuevas (`tiresias.alVentana`).

Además, cada vez que solo necesita el manifiesto (al pedir permiso para instalarlo o al hacer la lista de Complementos), ejecuta el módulo en un motor aparte en el que la API no hace nada. Por eso el código del nivel superior no debe tener efectos que importen.

Las órdenes, `alBateria` y `alVentana` se miran solo en ese momento: regístralas en el nivel superior del módulo, nunca dentro de una función que se llame más tarde. Los demás ganchos (`alEnfocar`, `alNotificar`, `alAcciones`, `alTeclaBraille`) se pueden registrar cuando quieras, pero lo normal es hacerlo también al cargar.

Si la carga falla (una excepción en el nivel superior o se pasa del tiempo), el complemento no se da de alta y los temporizadores que hubiera pedido se quitan. Antes de la 1.0.4, la API no contestaba durante la carga: un complemento que tenga que funcionar también en versiones anteriores debe poner sus temporizadores dentro de una orden o de un gancho.

## Tutorial: tu primer complemento

Vas a hacer «Minutos para la hora»: una orden que dice cuántos minutos faltan para la hora en punto, con un ajuste para el sonido. Este ejemplo y su prueba se han pasado con el banco de Node.

### Paso 1: crea la carpeta y el archivo

En el proyecto, crea `complementos/minutos-para-la-hora/complemento.js`. El nombre de la carpeta tiene que ser el `id` del complemento: lo comprueban las pruebas del emulador, y el banco busca el complemento por ese nombre. El archivo va en UTF-8. Si no trabajas en el proyecto, basta con un archivo `.js` suelto.

### Paso 2: el manifiesto

Lo primero del archivo es el manifiesto, un objeto exportado con el nombre `manifest`:

```js
export const manifest = {
	id: "minutos-para-la-hora",
	nombre: "Minutos para la hora",
	nombre_en: "Minutes to the hour",
	version: "1.0",
	autor: "Tu nombre",
	descripcion: "Una orden que dice cuántos minutos faltan para la hora en punto.",
	descripcion_en: "A command that says how many minutes are left until the hour.",
	apps: ["*"],
	permisos: ["voz"],
	ajustes: [
		{ clave: "sonido", tipo: "si_no", defecto: true,
			nombre: "Un sonido antes", nombre_en: "A sound first" },
	],
};
```

- `apps: ["*"]`: actúa en todas las apps. Pon siempre `apps`: sin él, el complemento no actúa en ninguna.
- `permisos: ["voz"]`: para hablar y hacer sonidos. Sin él, `tiresias.decir` no hace nada.
- Escribe el manifiesto con comillas dobles, los campos principales primero y cerrado con `};` en su propia línea: el script que publica la tienda lo lee así.

### Paso 3: la primera orden

Debajo del manifiesto, los textos en los dos idiomas y la orden:

```js
const TEXTOS = {
	es: {
		orden: "Minutos para la hora",
		faltan: (n, h) => (n === 1 ? "Falta 1 minuto" : "Faltan " + n + " minutos") + " para las " + h,
	},
	en: {
		orden: "Minutes to the hour",
		faltan: (n, h) => n + (n === 1 ? " minute" : " minutes") + " to " + h + " o'clock",
	},
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

function ajuste(clave, defecto) {
	const v = tiresias.ajuste(clave);
	return v === null || v === undefined ? defecto : v;
}

/** La frase para la hora «a» (la de tiresias.ahora()). Se exporta para probarla. */
export function frase(a) {
	const n = 60 - a.minuto;
	return T().faltan(n, (a.hora + 1) % 24);
}

tiresias.orden(T().orden, () => {
	const a = tiresias.ahora();
	if (!a) return;
	if (ajuste("sonido", true)) tiresias.sonido("notificacion");
	tiresias.decir(frase(a));
});
```

Exportar `frase` no le hace falta a Tiresias, pero deja probarla sola en el banco.

### Paso 4: pruébalo en el PC

El banco de Node prueba el complemento sin móvil ni emulador (necesitas [Node.js](https://nodejs.org), que no instala `preparar_entorno.ps1`). Crea `herramientas/banco-complementos/minutos-para-la-hora.mjs` (el mismo nombre que la carpeta del complemento):

```js
function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);
const a = (hora, minuto) => ({ anio: 2026, mes: 10, dia: 27, diaSemana: 2, hora, minuto, segundo: 0, ms: 0, formato24: true });

export function laFrase(c) {
	igual(c.modulo.frase(a(10, 45)), "Faltan 15 minutos para las 11");
	igual(c.modulo.frase(a(9, 59)), "Falta 1 minuto para las 10");
}

export function laOrdenSuenaYHabla(c) {
	c.ponerAhora(a(10, 45));
	c.hooks.ordenes["Minutos para la hora"]();
	igual(c.registro[0][0], "sonido");
	igual(dichos(c)[0], "Faltan 15 minutos para las 11");
}

export function sinSonido(c) {
	c.ajustes.sonido = false;
	c.ponerAhora(a(10, 45));
	c.hooks.ordenes["Minutos para la hora"]();
	igual(c.registro.some((x) => x[0] === "sonido"), false);
}
```

Y pásalo:

```
node herramientas\banco-complementos\banco.mjs complementos\minutos-para-la-hora\complemento.js herramientas\banco-complementos\minutos-para-la-hora.mjs
```

Sale una línea `bien` o `FALLO` por prueba y el total. `powershell -File herramientas\banco-complementos\probar.ps1` pasa las pruebas de todos los complementos de la carpeta.

### Paso 5: instálalo en el móvil

1. Copia `complemento.js` al móvil (por cable, por la nube o, en el emulador, con `adb push complemento.js /sdcard/Download/`).
2. En la app de Tiresias, abre el menú lateral y entra en Complementos.
3. Pulsa «Instalar un complemento desde un archivo» y elige el archivo.
4. Tiresias lee el manifiesto y pregunta: el nombre, la versión, en qué apps actúa, los permisos y la descripción. Acepta con «Instalar …».
5. Abre el menú de Tiresias, entra en «Órdenes de los complementos» y elige «Minutos para la hora». Ese menú también se puede asignar a un gesto o una tecla (la acción «Órdenes de los complementos»).
6. Su ajuste está en Complementos, en el complemento, «Ajustes de Minutos para la hora».

Para cambiarlo, vuelve a instalar el archivo: sustituye al anterior con el mismo `id`, y sus ajustes y datos se conservan. Al desinstalarlo se borran.

Si algo falla, el registro técnico (se comparte desde Ajustes) dice qué complemento falló, en qué gancho y con qué mensaje, sin datos de la pantalla. Tus propias líneas van ahí con `tiresias.registro()`.

### Paso 6: publícalo

Tienes dos caminos, explicados en [La tienda](#la-tienda):

- **En la tienda de Tiresias**: envía un pull request a `https://github.com/hxebolax/Tiresias-Android` con la carpeta `complementos/<id>/` y su prueba en `herramientas/banco-complementos/<id>.mjs`. El autor lo publica con `herramientas/publicar_actualizacion.ps1`.
- **En tu propia fuente**: sube a una carpeta con https (un repositorio de GitHub, GitLab o Codeberg, o tu web) el `.js` y un `catalogo.json`. Quien quiera tus complementos añade esa carpeta en Complementos, «Fuentes de complementos».

## El manifiesto

```js
export const manifest = {
	id: "mejor-whatsapp",
	nombre: "WhatsApp mejor",
	nombre_en: "Better WhatsApp",
	version: "1.0",
	autor: "Quien lo hace",
	descripcion: "Qué hace, en una frase.",
	descripcion_en: "What it does, in one sentence.",
	apps: ["com.whatsapp"],
	actividades: ["HomeActivity"],
	permisos: ["voz"],
	reglas: [{ id: "fondo", ocultar: true }],
	ajustes: [{ clave: "corto", tipo: "si_no", defecto: false, nombre: "Decirlo corto" }],
};
```

| Campo | Obligatorio | Qué es |
|---|---|---|
| `id` | Sí | De 2 a 64 caracteres: empieza por letra minúscula o cifra y sigue con minúsculas, cifras, punto, guion o guion bajo. Si no vale, el complemento no se instala. Es lo que identifica al complemento: otro con el mismo `id` lo sustituye |
| `nombre` | No | Lo que ve el usuario. Sin él, el `id` |
| `version` | No | De fábrica `"1.0"`. La tienda la compara número a número para avisar de versiones nuevas (`1.10` es más nueva que `1.9`) |
| `autor` | No | Solo lo usan la tienda y el catálogo |
| `descripcion` | No | Una frase; Tiresias la lee al pedir permiso para instalarlo |
| `apps` | En la práctica, sí | Lista de paquetes en los que actúa, o `["*"]` para todas. Vacía o sin poner, también todas (como dice la pantalla de instalar) |
| `actividades` | No | Solo actúa con estas pantallas delante: el final del nombre de su clase (`"ChatActivity"`). Vacía: en todas |
| `permisos` | No | Lo que puede hacer, ver la tabla siguiente |
| `reglas` | No | Arreglos sin código, ver [Las reglas sin código](#las-reglas-sin-código) |
| `ajustes` | No | Su pantalla de ajustes, ver [Los ajustes propios](#los-ajustes-propios) |

`nombre`, `descripcion`, y los textos de los ajustes y de sus opciones admiten traducción con el sufijo del idioma (`nombre_en`).

### Los permisos

| Permiso | Qué le deja hacer | Lo que pide sin él |
|---|---|---|
| `voz` | `decir` y `sonido` | No hacen nada |
| `portapapeles` | `portapapeles` y `vaciarPortapapeles` | No hacen nada; `vaciarPortapapeles` devuelve `false` |
| `braille` | `braille` y recibir las teclas en `alTeclaBraille` | Nada en la línea; su `alTeclaBraille` no se llama |
| `abrir` | `abrir`, `apps` y `abrirApp` | Devuelven `false` o `[]` |

Antes de instalar, Tiresias enseña al usuario los permisos con palabras: «hablar y hacer sonidos», «copiar al portapapeles», «usar la línea braille», «abrir enlaces, el marcador, el correo o los mapas». Un permiso que no está en la tabla se ignora.

Lo demás no necesita permiso: cambiar lo que se dice (`alEnfocar`, `alNotificar`), las órdenes y acciones, las preguntas, los temporizadores, los datos y toda la información (hora, batería, pantalla, llamada, luz, apps).

### Las apps y las actividades

Todos los ganchos, las órdenes y las reglas valen solo en las apps de `apps` y, si hay `actividades`, solo con una de esas pantallas delante. Se mira la app de la ventana de delante:

- `alEnfocar`, `alAcciones` y las reglas: la app del elemento.
- `alNotificar`: la app que manda la notificación.
- `alTeclaBraille` y las órdenes: la app de delante.
- `alVentana`: la app de la pantalla nueva (nunca las pantallas de Tiresias).

Con `actividades`, una pantalla recién abierta cuya clase Tiresias aún no sabe cuenta como que vale. Para saber el paquete y la clase de una pantalla, haz una orden que diga `tiresias.app()` y `tiresias.actividad()`.

## Las traducciones

Hay dos clases de textos: los del manifiesto y los del código.

**En el manifiesto**, cada texto puede llevar su traducción con el sufijo del idioma: `nombre_en`, `descripcion_en`, y en los ajustes `nombre_en`, `descripcion_en`, `unidad_en` y el `nombre_en` de cada opción. Tiresias usa el texto del idioma en el que está y, si no lo hay o está vacío, el de siempre, que se entiende en español. Vale para cualquier idioma que tenga Tiresias (`nombre_fr`, si un día hay francés). La tienda también lee `nombre_<idioma>` y `descripcion_<idioma>` del catálogo, pero el script que la publica solo copia `nombre_en` y `descripcion_en`.

**En el código**, `tiresias.idioma()` devuelve el idioma de Tiresias (`"es"`, `"en"`), que puede no ser el del móvil. El modelo de todos los ejemplos:

```js
const TEXTOS = {
	es: { listo: "Hecho", veces: (n) => n === 1 ? "1 vez" : n + " veces" },
	en: { listo: "Done", veces: (n) => n === 1 ? "once" : n + " times" },
};
const T = () => TEXTOS[tiresias.idioma()] || TEXTOS.es;

tiresias.decir(T().listo);
```

- Llama a `T()` cada vez que vayas a decir algo, no una sola vez al cargar: así se usa el idioma de ahora.
- Haz funciones para lo que lleva números (los plurales y el orden de las palabras cambian con el idioma) y no construyas frases pegando trozos.
- Un idioma que tu complemento no tiene cae en español.

## Los ajustes propios

Si el manifiesto trae `ajustes`, en Complementos aparece «Ajustes de …» con una pantalla hecha con ellos: los `si_no` son interruptores de una sola parada, y los demás, botones que dicen el valor de ahora y lo cambian. Tiene un botón para volver a los de fábrica. El complemento lee el valor en el momento con `tiresias.ajuste(clave)`, sin recargarse. Se borran al desinstalarlo.

```js
ajustes: [
	{ clave: "copiar", tipo: "si_no", defecto: true, nombre: "Copiar el código", descripcion: "…" },
	{ clave: "vaciar", tipo: "lista", defecto: "120", nombre: "Borrar el código del portapapeles",
		opciones: [{ valor: "0", nombre: "Nunca" }, { valor: "120", nombre: "A los 2 minutos" }] },
	{ clave: "minutos", tipo: "numero", defecto: 5, min: 1, max: 180, paso: 1, unidad: "minutos", nombre: "Duración" },
	{ clave: "palabras", tipo: "texto", defecto: "", nombre: "Palabras importantes" },
	{ clave: "excluidas", tipo: "apps", defecto: "", nombre: "Apps en las que no actuar" },
],
```

| Campo | Qué es |
|---|---|
| `clave` | Obligatoria: de 1 a 40 letras sin tilde, cifras o guiones bajos |
| `tipo` | `si_no` (de fábrica), `lista`, `numero`, `texto` o `apps` |
| `nombre` | Lo que se ve; sin él, la clave |
| `descripcion` | Una explicación debajo del nombre; en `texto`, la pista del campo |
| `defecto` | El valor de fábrica |
| `opciones` | Solo `lista`: `[{ valor, nombre }]`. Una lista sin opciones se descarta |
| `min`, `max`, `paso`, `unidad` | Solo `numero`: de fábrica 0, 100 y 1, sin unidad |

Un ajuste con una clave o un tipo que no valen se descarta sin avisar.

| Tipo | Cómo se elige | Qué devuelve `tiresias.ajuste` | Sin elegir |
|---|---|---|---|
| `si_no` | Un interruptor | `true` o `false` | `defecto`, o `false` |
| `lista` | Una de las `opciones` | El `valor` de la elegida, como texto | `defecto`, o el valor de la primera opción |
| `numero` | Una lista de `min` a `max` de `paso` en `paso` (con más de 60 valores, se escribe) | Un número entero entre `min` y `max` | `defecto`, o `min` |
| `texto` | Un campo de texto (hasta 2000 caracteres) | El texto | `defecto`, o `""` |
| `apps` | Varias de las apps con icono en el lanzador | Una lista de paquetes: `["com.whatsapp"]` | `defecto` (paquetes separados por comas), o `[]` |

`tiresias.ajuste` de una clave que no está en el manifiesto devuelve `null`. Solo hay números enteros. Para `apps`, pon `defecto: ""` o los paquetes separados por comas, no una lista.

## Las reglas sin código

Una regla cambia cómo se lee un elemento de una app sin escribir JavaScript. Toca al elemento con ese identificador de vista (`id`), en las apps del complemento:

```js
reglas: [
	{ id: "com.app:id/fondo", ocultar: true },
	{ id: "fila_mensaje", agrupar: true, activar: "abrir_chat" },
	{ id: "ic_send", etiqueta: "Enviar" },
	{ id: "contador", silenciar: true, actividad: "ChatActivity" },
],
```

| Campo | Qué hace |
|---|---|
| `id` | Obligatorio. El identificador completo (`com.app:id/fondo`) o solo lo que va tras `:id/` (`fondo`) |
| `etiqueta` | Su nombre. Una etiqueta que el usuario puso a mano manda sobre esta |
| `rol` | Su tipo, tal como se dice («pestaña», «botón de reproducir») |
| `estado` | Su estado, tal como se dice |
| `pista` | Su pista de uso |
| `encabezado` | `true` o `false`: si es un encabezado |
| `ocultar` | `true`: no es una parada. `"todo"`: tampoco lo es nada de lo que tiene dentro |
| `agrupar` | `true`: se lee entero en una sola parada, y sus controles de dentro pasan a su menú de acciones |
| `activar` | El identificador del hijo que se pulsa cuando el usuario lo activa (dentro de una fila agrupada, el botón de verdad) |
| `silenciar` | `true`: sus cambios no se anuncian. `"todo"`: tampoco los de lo que tiene dentro |
| `levantar` | `true`: se activa al levantar el dedo sobre él, sin doble toque |
| `actividad` | La regla solo vale con esa pantalla delante (el final del nombre de su clase) |
| `ventana` | La regla solo vale si el título de la ventana lo contiene (sin distinguir mayúsculas) |

`ocultar` y `silenciar` también aceptan los textos `"si"` y `"true"`. Si varias reglas, del mismo complemento o de varios, tocan al mismo elemento, para cada campo manda la primera que lo pone; `ocultar`, `agrupar`, `silenciar` y `levantar` valen si cualquiera los pone. El identificador de un elemento lo dice Tiresias cuando no tiene nombre, o una orden con `tiresias.elemento().id`.

## Referencia de la API

### Lo que hay que saber antes

- La API es el objeto global `tiresias`. No hay `import`: un complemento es un solo archivo.
- Casi todas las funciones contestan al momento. Las que esperan al usuario o a un sensor (`elegir`, `confirmar`, `pedirTexto` y `luz`) reciben una función a la que Tiresias llama después con la respuesta.
- Los textos que pasas se convierten con `String()`. Lo que devuelven es JSON ya leído: objetos, listas, números, textos, `true`, `false` o `null`.
- Una función cuyo permiso no tiene el complemento no hace nada y no da error.
- Tus funciones (los ganchos, las órdenes, los temporizadores, las respuestas) corren con el límite de 30 ms cada vez. Una excepción sin atender cuenta como fallo.
- Al cargar, la API ya contesta desde la 1.0.4 (ver [Cómo se carga](#cómo-se-carga)).

### Ganchos: lo que Tiresias le pasa al complemento

#### `tiresias.alEnfocar(f)`

- **Firma**: `tiresias.alEnfocar((e) => texto | null)`.
- **Permiso**: ninguno.
- **Cuándo se llama**: cada vez que Tiresias va a decir un elemento de las apps del complemento (al moverse por la pantalla, al tocarlo o al enfocarlo).
- **Recibe** `e`:

| Campo | Qué es |
|---|---|
| `app` | El paquete de la app del elemento |
| `nombre` | Su nombre (descripción o texto visible) |
| `rol` | Su tipo, en minúsculas: `boton`, `boton_imagen`, `casilla`, `interruptor`, `campo_texto`, `enlace`, `imagen`, `elemento_lista`, `texto`, `ninguno`… (los de `Rol` en `nucleo/Elemento.kt`) |
| `texto` | Lo escrito, solo en los campos de texto; `""` en los demás |
| `id` | Su identificador de vista (`com.app:id/enviar`) o `""` |
| `estado` | La descripción de estado que da la app, o `""` |
| `dicho` | Lo que Tiresias va a decir, entero (nombre, tipo, estado…), o lo que dejó el complemento anterior |

- **Devuelve**: un texto, que se dice en lugar de `dicho`; cualquier otra cosa (`null`, `undefined`) lo deja como está. No devuelvas `""`: el elemento se quedaría mudo.
- **Varios ganchos y varios complementos**: dentro de un complemento manda el primer gancho que devuelve texto. Entre complementos, el texto pasa de uno a otro: cada uno recibe en `dicho` lo que dejó el anterior, así se suman (uno pone nombre a un botón y otro acorta los enlaces).
- **Límites**: 30 ms. Se llama muy a menudo: haz poco trabajo y nada de temporizadores aquí.

```js
tiresias.alEnfocar((e) => e.id.endsWith(":id/ic_send") ? "Enviar, botón" : null);
```

#### `tiresias.alNotificar(f)`

- **Firma**: `tiresias.alNotificar((n) => texto | "" | null)`.
- **Permiso**: ninguno.
- **Cuándo se llama**: con cada notificación de las apps del complemento que Tiresias recibe, también las que no va a leer. No llegan las que no tienen título ni texto, ni las que solo se actualizan (de progreso) con el mismo título.
- **Recibe** `n`:

| Campo | Qué es |
|---|---|
| `app` | El paquete de la app |
| `nombreApp` | El nombre de la app |
| `titulo` | El título |
| `texto` | El texto largo o, si no hay, el normal |
| `categoria` | La categoría de Android (`msg`, `email`, `call`, `promo`, `alarm`…) o `""` |
| `sms` | `true` si es de la app de SMS del móvil |
| `continua` | `true` si es fija o de progreso |
| `bloqueado` | `true` si el móvil está bloqueado |
| `momento` | Cuándo llegó, en milisegundos desde 1970 |
| `mensajes` | En un chat, los mensajes que trae (los 20 últimos): `[{ remitente, texto }]`; si no, `[]` |
| `seLee` | `false` si Tiresias no la va a leer: lectura de notificaciones apagada, app callada, «No molestar», en una llamada, móvil bloqueado con «callar en la pantalla de bloqueo», o el modo senior con solo los mensajes |

- **Devuelve**: un texto, que se dice en lugar de la frase normal («WhatsApp: Ana. Hola»); `""` la calla (ni sonido ni voz); `null` la deja como está. Con `seLee` a `false` lo que devuelvas no se dice; si quieres avisar igual, usa `tiresias.decir` (así lo hace el de códigos de seguridad).
- **Varios complementos**: la reciben todos; manda lo que devuelva el primero que la cambie.
- **Límites**: 30 ms.

```js
tiresias.alNotificar((n) => n.categoria === "promo" ? "" : null);
```

#### `tiresias.alAcciones(f)`

- **Firma**: `tiresias.alAcciones((e) => [{ nombre, hacer }])`.
- **Permiso**: ninguno.
- **Cuándo se llama**: cuando Tiresias hace la lista de acciones de un elemento (al abrir su menú de acciones).
- **Recibe** `e`: lo mismo que `alEnfocar`, sin `dicho`.
- **Devuelve**: una lista de acciones: `nombre` (texto) y `hacer` (función que se llama si el usuario la elige). Lo que no tenga las dos cosas se salta. Devuelve `[]` o `null` para no añadir nada. Las acciones de todos los complementos se suman al final del menú.
- **Límites**: 30 ms para hacer la lista y otros 30 ms al hacer la acción.

```js
tiresias.alAcciones((e) => {
	const m = e.nombre.match(/\b\d{6}\b/);
	return m ? [{ nombre: "Copiar el código " + m[0], hacer: () => tiresias.portapapeles(m[0], true) }] : [];
});
```

#### `tiresias.alTeclaBraille(f)`

- **Firma**: `tiresias.alTeclaBraille((t) => true | false)`.
- **Permiso**: `braille`. Sin él, no se llama.
- **Cuándo se llama**: con cada orden que llega de la línea braille, antes de que Tiresias la haga, si la app de delante es de las del complemento. Con un documento BRF abierto en la línea, las de moverse por él (desplazar, siguiente, anterior, primero, último y atrás) se las queda el documento y no llegan.
- **Recibe** `t`: `tipo` y `valor` (siempre texto) y `app`:

| `tipo` | `valor` |
|---|---|
| `puntos` | Los puntos de una celda escrita, como número en texto, con el punto 1 en el bit 0 (`"1"` es el punto 1, `"3"` los puntos 1 y 2) |
| `tecla` | `espacio`, `borrar` o `intro` |
| `accion` | El nombre de la acción de Tiresias en minúsculas: `siguiente`, `anterior`, `atras`, `inicio`… |
| `desplazar` | `adelante` o `atras` |
| `celda` | El número de la tecla de encaminamiento pulsada |
| `otra` | `desconocida` |

- **Devuelve**: `true` si se queda la orden (Tiresias no hace nada más); cualquier otra cosa la deja pasar. Manda el primer complemento que devuelve `true`.
- **Límites**: 30 ms.

```js
let activo = false;
tiresias.alTeclaBraille((t) => activo && t.tipo === "tecla" && t.valor === "intro" ? (tiresias.braille("Intro"), true) : false);
```

#### `tiresias.alBateria(f)`

- **Firma**: `tiresias.alBateria((b) => { … })`.
- **Permiso**: ninguno.
- **Cuándo se llama**: cuando cambia el nivel, si está cargando o el tipo de enchufe (no con cada cambio de temperatura, que Android avisa muy a menudo). Tiresias solo escucha la batería si algún complemento cargado tiene este gancho.
- **Recibe** `b`: lo mismo que `tiresias.bateria()`.
- **Devuelve**: nada.
- **Límites**: 30 ms. Regístralo al cargar el módulo: se mira solo entonces.

```js
tiresias.alBateria((b) => { if (b.llena) tiresias.decir("Batería llena", true); });
```

#### `tiresias.alVentana(f)`

- **Firma**: `tiresias.alVentana((v) => { … })`.
- **Permiso**: ninguno.
- **Cuándo se llama**: cada vez que se abre una pantalla nueva en una de las apps del complemento (no con los paneles que se cierran ni con las pantallas de Tiresias).
- **Recibe** `v`: `app` (el paquete), `actividad` (la clase de la pantalla de delante, o `""`) y `titulo` (el título de la ventana, o `""`).
- **Devuelve**: nada. Se llama a todos los ganchos `alVentana` del complemento.
- **Límites**: 30 ms. Regístralo al cargar el módulo: se mira solo entonces.

```js
tiresias.alVentana((v) => { if (v.titulo === "Pago") tiresias.sonido("ventana"); });
```

#### `tiresias.orden(nombre, f)`

- **Firma**: `tiresias.orden(nombre, () => { … })`.
- **Permiso**: ninguno.
- **Cuándo se llama**: cuando el usuario la elige en «Órdenes de los complementos» (en el menú de Tiresias o con el gesto o la tecla de esa acción). Solo aparecen las órdenes de los complementos que valen en la app de delante. Tiresias espera un momento y devuelve el foco al elemento de antes, y después llama a la orden.
- **Devuelve**: nada.
- **Límites**: 30 ms. Regístralas al cargar el módulo: la lista se lee solo entonces. Dos órdenes con el mismo nombre en un complemento: queda la última.

```js
tiresias.orden("Decir la app", () => tiresias.decir(tiresias.app()));
```

### Hablar y sonar

#### `tiresias.decir(texto, cola)`

- **Permiso**: `voz`.
- **Qué hace**: lo dice Tiresias, como sus propios mensajes; con una línea braille conectada, también aparece en ella. Con `cola` a `true`, espera a que acabe lo que está diciendo; si no, lo corta.
- **Devuelve**: nada.

```js
tiresias.decir("Copiado");
tiresias.decir("Llega un código", true);
```

#### `tiresias.sonido(nombre)`

- **Permiso**: `voz`.
- **Qué hace**: suena uno de los sonidos de Tiresias (el del tema puesto), respetando los ajustes del usuario (sonidos apagados, volumen). Nombres: `foco`, `toque`, `activar`, `mantener`, `desplazar`, `borde`, `vuelta`, `ventana`, `error`, `acciones`, `selector`, `notificacion`, `encendido`, `apagado`, `gesto`, `tecla`, `edicion` y `cargando`. Un nombre que no existe no suena.
- **Devuelve**: nada.

```js
tiresias.sonido("notificacion");
```

### Portapapeles

#### `tiresias.portapapeles(texto, sensible)`

- **Permiso**: `portapapeles`.
- **Qué hace**: copia el texto. Con `sensible` a `true` (un código, una clave), lo marca como dato sensible: Android y el teclado no lo enseñan en la vista previa, y no pasa al historial del portapapeles de Tiresias.
- **Devuelve**: nada.

```js
tiresias.portapapeles("482913", true);
```

#### `tiresias.vaciarPortapapeles(texto)`

- **Permiso**: `portapapeles`.
- **Qué hace**: vacía el portapapeles solo si lo último que copió Tiresias es ese texto y el portapapeles sigue teniéndolo. Si el usuario ha copiado otra cosa después, no lo toca.
- **Devuelve**: `true` si lo vació.

```js
tiresias.despues(120000, () => tiresias.vaciarPortapapeles(codigo));
```

### Braille

#### `tiresias.braille(texto)`

- **Permiso**: `braille`.
- **Qué hace**: pone el texto (hasta 500 caracteres) en la línea braille durante el tiempo de los mensajes de los ajustes del usuario. Sin línea conectada no hace nada.
- **Devuelve**: nada.

```js
tiresias.braille("Modo notas");
```

### Abrir

#### `tiresias.abrir(uri)`

- **Permiso**: `abrir`.
- **Qué hace**: abre un enlace `http:` o `https:`, un correo `mailto:`, un mapa `geo:`, un SMS `sms:` o `smsto:`, o el marcador con un número `tel:` (nunca llama solo). Nada más: ni archivos ni otros esquemas.
- **Devuelve**: `true` si se abrió; `false` si el esquema no vale o no hay app para abrirlo.

```js
tiresias.abrir("tel:600111222");
```

#### `tiresias.apps()`

- **Permiso**: `abrir`.
- **Devuelve**: las apps que se pueden abrir (las del lanzador), ordenadas por su nombre: `[{ nombre, paquete }]`. Sin el permiso, `[]`.

```js
const whatsapp = tiresias.apps().find((a) => a.nombre === "WhatsApp");
```

#### `tiresias.abrirApp(paquete)`

- **Permiso**: `abrir`.
- **Qué hace**: abre esa app por su pantalla de inicio.
- **Devuelve**: `true` si se abrió.

```js
tiresias.abrirApp("com.whatsapp");
```

### Preguntar al usuario

Las tres preguntas usan las ventanas de Tiresias. Hay una pregunta cada vez por complemento, y nunca se abre encima de otra ventana de Tiresias: en esos casos, o sin opciones, la función recibe `null` enseguida. La respuesta llega después, en otra llamada (con su propio límite de 30 ms).

#### `tiresias.elegir(titulo, opciones, f)`

- **Firma**: `tiresias.elegir(titulo, ["Una", "Otra"], (i) => { … })`.
- **Permiso**: ninguno.
- **Qué hace**: una lista de Tiresias con ese título.
- **La función recibe**: la posición de la elegida (desde 0), o `null` si se cancela o no se pudo preguntar.
- **Límites**: 50 opciones como mucho, y 200 caracteres por opción y en el título.

```js
tiresias.elegir("¿Qué recordatorio?", ["Pastilla", "Gotas"], (i) => { if (i !== null) tiresias.decir("Elegido " + (i + 1)); });
```

#### `tiresias.confirmar(texto, f)`

- **Firma**: `tiresias.confirmar(texto, (si) => { … })`.
- **Permiso**: ninguno.
- **Qué hace**: una pregunta con Aceptar y Cancelar (en el idioma de Tiresias).
- **La función recibe**: `true` con Aceptar, `false` con Cancelar, o `null` si se cerró sin elegir o no se pudo preguntar.
- **Límites**: 300 caracteres.

```js
tiresias.confirmar("¿Borrar la nota?", (si) => { if (si) tiresias.borrarDato("nota"); });
```

#### `tiresias.pedirTexto(titulo, pista, f)`

- **Firma**: `tiresias.pedirTexto(titulo, pista, (texto) => { … })`.
- **Permiso**: ninguno.
- **Qué hace**: un campo de texto de una línea, con su título y su pista.
- **La función recibe**: lo escrito (hasta 2000 caracteres), o `null` si se cancela o no se pudo preguntar.
- **Límites**: 200 caracteres en el título y en la pista.

```js
tiresias.pedirTexto("Nuevo recordatorio", "08:00 Pastilla azul", (t) => { if (t) tiresias.guardar("ultimo", t); });
```

### Saber cosas

#### `tiresias.idioma()`

- **Permiso**: ninguno.
- **Devuelve**: el código del idioma de Tiresias: `"es"`, `"en"`. Es el elegido en Ajustes o, si sigue al sistema, el del móvil si Tiresias lo tiene; si no, `"es"`.

```js
const ingles = tiresias.idioma() === "en";
```

#### `tiresias.ahora()`

- **Permiso**: ninguno.
- **Devuelve**: la hora del móvil, con su zona horaria: `anio`, `mes` (1 a 12), `dia`, `diaSemana` (1 lunes … 7 domingo), `hora` (0 a 23), `minuto`, `segundo`, `ms` (milisegundos desde 1970) y `formato24` (si el móvil usa 24 horas). Puede ser `null` (al cargar, y en el banco si la prueba no la pone): ten un plan B.

```js
const a = tiresias.ahora();
if (a && a.diaSemana >= 6) tiresias.decir("Es fin de semana");
```

#### `tiresias.bateria()`

- **Permiso**: ninguno.
- **Devuelve**: `nivel` (por ciento), `cargando`, `llena`, `enchufe` (`""`, `"corriente"`, `"usb"`, `"inalambrico"` u `"otro"`) y `temperatura` (en grados), o `null` si no se sabe.

```js
const b = tiresias.bateria();
if (b) tiresias.decir("Batería al " + b.nivel + " %");
```

#### `tiresias.pantalla()`

- **Permiso**: ninguno.
- **Devuelve**: `{ encendida, bloqueada }`.

```js
const conPantalla = tiresias.pantalla().encendida;
```

#### `tiresias.llamada()`

- **Permiso**: ninguno.
- **Devuelve**: `"sonando"`, `"en_llamada"` o `""`. Vale para el teléfono y para las llamadas de internet (WhatsApp, Telegram, Signal…): una de estas cuenta como sonando durante un minuto desde su notificación, si no se ha contestado y tiene botón de contestar.

```js
if (tiresias.llamada() === "sonando") tiresias.decir("Sigue sonando", true);
```

#### `tiresias.luz(f)`

- **Firma**: `tiresias.luz((luxes) => { … })`.
- **Permiso**: ninguno.
- **Qué hace**: pide una lectura al sensor de luz.
- **La función recibe**: los luxes (un número con decimales), o `null` si el móvil no tiene sensor o no contesta en 2 segundos.

```js
tiresias.luz((l) => tiresias.decir(l === null ? "Sin sensor" : l < 10 ? "Oscuro" : "Hay luz"));
```

#### `tiresias.infoApp(paquete)`

- **Permiso**: ninguno.
- **Devuelve**: los datos de una app: `paquete`, `nombre`, `version` (la que ve el usuario), `codigo` (el número de versión), `instalada` y `actualizada` (milisegundos desde 1970). Con `""` o sin argumento, la app de delante. `null` si no se ve la app (desde Android 11, Tiresias solo ve las apps que declara en su manifiesto, como las que tienen icono en el lanzador).

```js
const i = tiresias.infoApp("");
if (i) tiresias.decir(i.nombre + ", versión " + i.version);
```

#### `tiresias.elemento()`

- **Permiso**: ninguno.
- **Devuelve**: el elemento enfocado, como el `e` de `alEnfocar` sin `dicho` (con `app`, `nombre`, `rol`, `texto`, `id` y `estado`), o `null`.

```js
const e = tiresias.elemento();
if (e) tiresias.decir(e.nombre.split(/\s+/).filter(Boolean).length + " palabras");
```

#### `tiresias.app()`

- **Permiso**: ninguno.
- **Devuelve**: el paquete de la app de la ventana de delante, o `""`.

#### `tiresias.actividad()`

- **Permiso**: ninguno.
- **Devuelve**: el nombre completo de la clase de la pantalla de delante (`com.whatsapp.HomeActivity`), o `""` si no se sabe.

```js
tiresias.orden("Dónde estoy", () => tiresias.decir(tiresias.app() + ", " + tiresias.actividad()));
```

### Ajustes y datos propios

#### `tiresias.ajuste(clave)`

- **Permiso**: ninguno.
- **Devuelve**: el valor de un ajuste del manifiesto, con el tipo de la tabla de [Los ajustes propios](#los-ajustes-propios), o `null` si la clave no está declarada. Se lee en el momento: si el usuario lo cambia, la siguiente llamada ya da el nuevo.

```js
const minutos = tiresias.ajuste("minutos") ?? 5;
```

#### `tiresias.guardar(clave, valor)`, `tiresias.leer(clave)`, `tiresias.borrarDato(clave)`

- **Permiso**: ninguno.
- **Qué hacen**: guardan, leen y borran datos del complemento, solo en el móvil y solo para él. Se conservan al reiniciar y al reinstalarlo, y se borran al desinstalarlo.
- **Devuelven**: `leer`, el texto guardado o `null`; las otras, nada.
- **Límites**: el valor se guarda como texto (`String(valor)`): para objetos, usa `JSON.stringify` y `JSON.parse`. La clave se corta a 100 caracteres. Todas las claves y valores juntos, 64 KB como mucho; lo que se pase no se guarda (sin error, con un aviso en el registro técnico).

```js
const notas = JSON.parse(tiresias.leer("notas") || "[]");
notas.push("Comprar pan");
tiresias.guardar("notas", JSON.stringify(notas));
```

### Temporizadores

#### `tiresias.despues(ms, f)`, `tiresias.cada(ms, f)`, `tiresias.cancelar(n)`

- **Permiso**: ninguno.
- **Qué hacen**: `despues` llama a `f` una vez pasados `ms` milisegundos (100 ms como mínimo); `cada`, cada `ms` milisegundos (1 segundo como mínimo). Los dos, 24 horas como mucho. `cancelar(n)` quita el temporizador número `n`.
- **Devuelven**: `despues` y `cada`, el número del temporizador.
- **Límites**: 30 temporizadores pendientes por complemento. Se quitan todos al apagar o descargar el complemento. Con el móvil dormido mucho rato, Android puede retrasarlos: para una hora exacta, comprueba `tiresias.ahora()` en cada vuelta en vez de contar con el temporizador.

```js
let t = 0;
tiresias.orden("Avisar en 5 minutos", () => {
	tiresias.cancelar(t);
	t = tiresias.despues(5 * 60000, () => tiresias.decir("Han pasado 5 minutos"));
});
```

### Registro

#### `tiresias.registro(texto)`

- **Permiso**: ninguno.
- **Qué hace**: escribe una línea en el registro técnico de Tiresias, con el `id` del complemento delante y cortada a 200 caracteres. Es para diagnosticar: nunca pongas ahí texto de la pantalla, de notificaciones ni nada del usuario.

```js
tiresias.registro("Cargados " + notas.length + " recordatorios");
```

## Buenas prácticas

- **Los textos, en los dos idiomas.** El manifiesto con sus `_en` y el código con un `TEXTOS` por idioma y `tiresias.idioma()`. Los 13 complementos de la tienda lo hacen así.
- **La hora, con `tiresias.ahora()`.** Da la hora del móvil con su zona horaria y su formato de 12 o 24 horas. En los avisos a una hora, revisa la hora en cada vuelta de un `cada` corto (el reloj parlante mira cada 15 segundos, los recordatorios cada 30) y apunta qué has avisado ya, en vez de un `despues` de horas: Android retrasa los temporizadores con el móvil dormido. Ten un plan B por si devuelve `null`, como el `ahora()` de `recordatorios` y `reloj-parlante`.
- **No hables de más.** Cambia lo que se dice solo cuando haga falta y devuelve `null` en lo demás. Di lo justo, sin repetir lo que Tiresias ya dice. Pon un ajuste para lo que no todo el mundo quiere.
- **`cola` cuando no seas la respuesta a algo.** Lo que llega solo (una hora, una batería, un código) va con `tiresias.decir(texto, true)`, para no cortar lo que el usuario está oyendo. Sin `cola`, solo la respuesta a una orden suya.
- **Los datos sensibles, como sensibles.** Copia los códigos y las claves con `portapapeles(texto, true)` y bórralos pasado un rato con `vaciarPortapapeles` (el de códigos de seguridad lo hace a los dos minutos, si nadie ha copiado otra cosa). No guardes con `guardar` lo que no haga falta guardar, y nunca lo pongas en `registro`.
- **Rápido.** 30 ms por llamada: nada de bucles largos en `alEnfocar`. Prepara las expresiones regulares una vez, fuera de las funciones.
- **Sin excepciones sueltas.** Comprueba los `null` (`elemento()`, `ahora()`, `bateria()`, `infoApp()`, `leer()`), y envuelve en `try` el `JSON.parse` de tus datos: tres fallos seguidos apagan el complemento.
- **Solo las apps que necesitas.** Pon sus paquetes en `apps` en vez de `"*"` si el complemento es para una app, y pide solo los permisos que usas: el usuario los ve antes de instalar.
- **Prueba lo que se pueda probar solo.** Exporta las funciones puras (buscar un código, formar una frase) para probarlas en el banco.

## Cómo probar un complemento

### El banco de Node

`herramientas/banco-complementos/` prueba los complementos en el PC, sin emulador:

- `banco.mjs` pone una API `tiresias` falsa que apunta lo que le piden, carga el complemento y pasa cada función exportada del archivo de pruebas.
- `probar.ps1` pasa las pruebas de todos: para cada `<id>.mjs` de la carpeta, prueba `complementos/<id>/complemento.js`. Termina con error si alguna falla.

```
node herramientas\banco-complementos\banco.mjs <ruta del complemento.js> <ruta de las pruebas.mjs>
powershell -File herramientas\banco-complementos\probar.ps1
```

Cada función exportada del archivo de pruebas es una prueba. Recibe `c`, y falla si lanza una excepción. Antes de cada prueba, el complemento se vuelve a cargar de cero y se vacían los ajustes y lo apuntado (los datos de `guardar` se conservan entre pruebas del mismo archivo).

| En `c` | Qué es |
|---|---|
| `c.modulo` | Lo que exporta el complemento (sus funciones puras) |
| `c.registro` | Lo que ha pedido: `["decir", texto, cola]`, `["sonido", nombre]`, `["portapapeles", texto, sensible]`, `["vaciar", texto]`, `["braille", texto]`, `["abrir", uri]`, `["abrirApp", paquete]` |
| `c.ajustes` | Los valores que devuelve `ajuste()`; ponlos antes de llamar (sin poner, `null`) |
| `c.datos` | Lo guardado con `guardar` |
| `c.hooks` | Los ganchos registrados: `enfocar`, `notificar`, `acciones`, `bateria`, `teclas`, `ventana` y `ordenes` (por nombre) |
| `c.temporizadores` | Los pendientes, por número: `{ ms, f, repetir }` |
| `c.preguntas` | Las preguntas abiertas: `{ tipo, titulo, opciones, pista, f }` |
| `c.enfocar(e)`, `c.notificar(n)`, `c.acciones(e)`, `c.bateria(b)`, `c.ventana(v)`, `c.tecla(tipo, valor)` | Hacen lo que Tiresias: pasan el dato por los ganchos y devuelven el resultado |
| `c.disparar()` | Llama a todos los temporizadores pendientes (y quita los de una vez) |
| `c.contestar(v)` | Contesta la primera pregunta abierta |
| `c.ponerAhora(a)`, `c.ponerLlamada(e)`, `c.ponerLuz(l)`, `c.ponerInfoApp(p, i)`, `c.ponerPantalla(p)` | Lo que devolverán `ahora()`, `llamada()`, `luz()`, `infoApp(p)` y `pantalla()` |
| `c.limpiar()` | Vacía `c.registro` |

Lo que el banco no simula, y hay que probar en el emulador o en el móvil:

- `idioma()` siempre da `"es"`; `app()`, `"com.prueba"`; `actividad()`, `""`; `bateria()` y `elemento()`, `null`; `apps()`, tres apps de ejemplo.
- No mira los permisos, las `apps` ni las `actividades` del manifiesto, ni las reglas.
- No pone límites de tiempo ni de memoria, y es el JavaScript de Node, no QuickJS.
- `luz()` contesta al momento, y las preguntas esperan a `c.contestar`.
- Contesta mientras se carga el módulo, cosa que Tiresias no hace (ver [Cómo se carga](#cómo-se-carga)).

Un ejemplo real, de `herramientas/banco-complementos/luz.mjs` (una de sus cinco pruebas): el detector de luz solo avisa cuando cambia la luz, y se para con la misma orden.

```js
function igual(a, b) { if (a !== b) throw new Error(`esperaba «${b}», salió «${a}»`); }
const dichos = (c) => c.registro.filter((x) => x[0] === "decir").map((x) => x[1]);

export function elDetectorAvisaSoloAlCambiar(c) {
	c.ponerLuz(2);
	c.hooks.ordenes["Detector de luz: empezar o parar"]();
	igual(dichos(c)[0], "Detector de luz en marcha. Apunta la cámara de delante hacia donde busques la luz.");
	const mirar = Object.values(c.temporizadores).find((t) => t.repetir);
	igual(mirar.ms, 2000);
	mirar.f();
	mirar.f();
	c.ponerLuz(800);
	mirar.f();
	igual(dichos(c).slice(1).join(" | "), "Oscuro | Mucha luz");
	c.hooks.ordenes["Detector de luz: empezar o parar"]();
	igual(dichos(c)[3], "Detector de luz parado");
	igual(Object.keys(c.temporizadores).length, 0);
}
```

### Las pruebas en el emulador

`app/src/androidTest/java/com/xebolax/tiresias/ComplementosTest.kt` carga los complementos de `complementos/` con el motor QuickJS de verdad (la carpeta se empaqueta como recursos de las pruebas), sin el lector: lo que necesita el lector (hablar, el portapapeles, las preguntas) no contesta, y lo que pide se ve con un espía. Comprueba:

- que todos se cargan, que su `id` es el nombre de su carpeta y que tienen descripción y ajustes, cada ajuste con nombre y las listas con opciones (un complemento de la carpeta sin `ajustes` hace fallar la prueba);
- que hacen lo que dicen, uno a uno (códigos, notificaciones a medida, reloj, batería, datos del texto, lectura limpia y los de la 1.0.4);
- que la tienda publicada en el servidor se descarga, coincide con su huella y el motor acepta cada complemento (necesita internet);
- que el catálogo se lee bien: salta los `id` y archivos que no valen y elige el nombre por idioma.

Las frases que espera están en español: la prueba pone Tiresias en español. Se pasa con las demás pruebas de funciones, en el emulador:

```
powershell -File herramientas\emulador_pruebas.ps1 -Que funciones
```

## La tienda

La tienda de Complementos junta varios catálogos:

1. El de Tiresias: `<servidor>/complementos/catalogo.json` (de fábrica `https://xebolax.synology.me/Tiresias-Android/complementos`). Cuando se active el respaldo de GitHub, también la carpeta `tienda/` del repositorio.
2. Las fuentes que añade el usuario.

Si un `id` está en varios, manda el primero: el de Tiresias sobre las fuentes. La tienda enseña cada complemento con su nombre, versión, descripción y si ya está instalado o tiene versión nueva. Al elegir uno, lo descarga, comprueba su tamaño y su huella, y pide permiso como al instalarlo desde un archivo.

### El catálogo

```json
{
	"formato": 1,
	"complementos": [
		{
			"id": "hora-y-fecha",
			"nombre": "Hora y fecha",
			"nombre_en": "Time and date",
			"version": "1.0",
			"autor": "Quien lo hace",
			"descripcion": "Qué hace, en una frase.",
			"descripcion_en": "What it does, in one sentence.",
			"apps": ["*"],
			"permisos": ["voz"],
			"archivo": "hora-y-fecha.js",
			"sha256": "9c570c1777a350aca5a05ddd449306e069a94a86d00f8e261939531fdd2ff785",
			"tamano": 1234
		}
	],
	"temas": [
		{ "id": "suave", "nombre": "Suave", "descripcion": "…", "autor": "…", "archivo": "suave.zip", "sha256": "…", "tamano": 56789 }
	]
}
```

| Campo | Qué es |
|---|---|
| `id` | Como el del manifiesto. Una entrada con un `id` que no vale se salta |
| `nombre`, `descripcion` | Lo que enseña la tienda; con `nombre_<idioma>` y `descripcion_<idioma>`, en el idioma de Tiresias |
| `version` | Para avisar de las versiones nuevas |
| `autor`, `apps`, `permisos` | Informativos: al instalar manda el manifiesto del propio archivo |
| `archivo` | Solo un nombre de archivo `.js` (letras, cifras, punto, guion y guion bajo), en la misma carpeta que el catálogo. Nunca una ruta ni otra web: la entrada se salta |
| `sha256` | La huella SHA-256 del archivo, en hexadecimal. Si no coincide, no se instala |
| `tamano` | Su tamaño en bytes. Si no coincide, no se instala |

Con `sha256` vacío o `tamano` a 0, esa comprobación no se hace: ponlos siempre. El catálogo y cada complemento tienen 512 KB como mucho, y cada descarga 15 segundos para contestar.

### Publicar en la tienda de Tiresias

`herramientas/publicar_actualizacion.ps1` (lo llama `compilar.bat publicar`) genera la tienda a partir de la carpeta `complementos/`:

1. Para cada `complementos/<carpeta>/complemento.js`, lee el bloque `export const manifest = { … };` con expresiones regulares (por eso los textos van entre comillas dobles y el bloque se cierra con `};`) y saca `id`, `nombre`, `version`, `autor`, `descripcion`, `nombre_en`, `descripcion_en`, `apps` y `permisos`. Con la primera aparición de cada campo: pon los del complemento antes que los `ajustes`, que también tienen `nombre` y `descripcion`.
2. Copia el archivo a `dist/complementos/<id>.js` y calcula su `sha256` y su `tamano`.
3. Hace lo mismo con los temas de `temas/` (ver [Los temas de sonido](#los-temas-de-sonido)).
4. Escribe `dist/complementos/catalogo.json`, deja una copia de todo en `tienda/` (el respaldo de GitHub) y lo sube a la carpeta del servidor: primero los archivos y al final el catálogo.

Un complemento sin manifiesto o con un `id` que no vale se avisa y no se publica.

### Tu propia fuente

Para publicar tus complementos sin pasar por la tienda de Tiresias:

1. Pon en una carpeta tus `.js` y un `catalogo.json` como el de arriba.
2. Calcula la huella y el tamaño de cada `.js`. En PowerShell: `(Get-FileHash -Algorithm SHA256 mi-complemento.js).Hash.ToLower()` y `(Get-Item mi-complemento.js).Length`. En Linux o macOS: `sha256sum mi-complemento.js` y `wc -c mi-complemento.js`. Vuelve a calcularlos cada vez que cambies el archivo.
3. Súbela a un sitio con https: un repositorio de GitHub, GitLab o Codeberg, o cualquier web.
4. Quien quiera tus complementos entra en Complementos, «Fuentes de complementos», «Añadir una fuente», y pega la dirección de la carpeta.

Tiresias acepta solo direcciones `https://`. Quita el `/` final y el `/catalogo.json` si se pega, y convierte la página de una carpeta de un repositorio en su dirección de archivos en bruto:

| Se pega | Se lee de |
|---|---|
| `https://github.com/usuario/repo/tree/main/carpeta` | `https://raw.githubusercontent.com/usuario/repo/main/carpeta` |
| `https://gitlab.com/usuario/repo/-/tree/main/carpeta` | `https://gitlab.com/usuario/repo/-/raw/main/carpeta` |
| `https://codeberg.org/usuario/repo/src/branch/main/carpeta` | `https://codeberg.org/usuario/repo/raw/branch/main/carpeta` |
| Cualquier otra `https://…` | Tal cual |

Una fuente que no contesta se salta sin estropear las demás.

### La huella SHA-256

La huella es la garantía de que lo que se instala es lo que se publicó: Tiresias descarga el archivo, calcula su SHA-256 y lo compara con el del catálogo (sin distinguir mayúsculas), y comprueba también el tamaño. Si algo no coincide, dice «El complemento descargado no es el publicado (tamaño o huella distintos)» y no instala nada. La huella no dice quién lo hizo: instala solo complementos de quien te fíes, como avisa la pantalla de Complementos.

## Los temas de sonido

Un tema de sonido cambia los sonidos de Tiresias. Es un ZIP con un archivo por sonido, con el nombre del sonido en minúsculas y extensión `ogg`, `wav` o `mp3`:

`foco`, `toque`, `activar`, `mantener`, `desplazar`, `borde`, `vuelta`, `ventana`, `error`, `acciones`, `selector`, `notificacion`, `encendido`, `apagado`, `gesto`, `tecla`, `edicion` y `cargando`.

- No hace falta que estén todos: los que faltan suenan como los de Tiresias.
- Los archivos con otro nombre o extensión se ignoran, y las carpetas de dentro del ZIP no cuentan (se mira solo el nombre del archivo).
- Cada sonido, 2 MB como mucho; el tema entero, 24 MB.
- Un sonido propio que el usuario pone a mano manda sobre el del tema.

El usuario los instala en la pantalla de Sonidos de Tiresias: «Temas de la tienda», «Importar un tema (ZIP)» o «Exportar los sonidos de ahora (ZIP)», que sirve de punto de partida para hacer uno.

En la tienda, los temas van en el apartado `temas` del catálogo, con `id`, `nombre`, `descripcion`, `autor`, `archivo` (un nombre `.zip`), `sha256` y `tamano`. Se leen del catálogo de Tiresias y de las fuentes del usuario, con la misma comprobación de huella. Su nombre y descripción no tienen traducción.

Para publicar uno en la tienda de Tiresias, crea `temas/<id>/` con un `tema.json` (`id`, `nombre`, `descripcion`, `autor`) y los archivos de sonido; `publicar_actualizacion.ps1` los comprime en `<id>.zip` y los añade al catálogo. Los dos temas de Tiresias, «Suave» y «Madera», se generan con `python herramientas/generar_temas_sonido.py`.

## Ejemplos reales

Los 13 complementos de `complementos/*/complemento.js` están en la tienda, tienen ajustes y textos en español e inglés, y cada uno su prueba en el banco (`herramientas/banco-complementos/<id>.mjs`). Son el mejor modelo:

| Complemento | Qué hace | Qué enseña |
|---|---|---|
| `anuncios` (Anuncios y patrocinados) | Dice «Anuncio» delante de lo patrocinado, o solo esa palabra, y calla las notificaciones de promociones | `alEnfocar` y `alNotificar` con `""`; ajustes `lista`, `texto` y `apps` |
| `codigos-de-verificacion` (Códigos de seguridad) | Encuentra los códigos de las notificaciones, los copia como sensibles, los dice cifra a cifra y los borra del portapapeles; órdenes para repetirlos | `seLee`, `portapapeles` sensible, `vaciarPortapapeles`, `despues`, `alAcciones`, `braille` |
| `datos-del-texto` (Datos del texto) | Ofrece en las acciones copiar o abrir los teléfonos, correos, enlaces, IBAN y números de pedido de un texto | `alAcciones`, `abrir`, `elemento` |
| `lectura-limpia` (Lectura limpia) | Lee los enlaces por su web, los números largos en grupos, sin caracteres invisibles ni repeticiones | `alEnfocar` sin permisos, encadenado con `dicho` |
| `letras-decorativas` (Letras decorativas) | Lee en letras normales los textos con letras de adorno de Unicode y los copia así | `alEnfocar`, `alNotificar`, una orden con `elemento` |
| `llamadas` (Quién llama) | Mientras suena una llamada, repite quién llama y por qué app | `llamada`, `cada`, `cancelar`, `pantalla` |
| `luz` (Cuánta luz hay) | Dice si hay luz con el sensor y tiene un detector que avisa al cambiar | `luz`, `cada`, `despues` |
| `modo-braille` (Modo braille) | Un menú en la línea braille: notas, abrir apps por su nombre, calculadora y hora | `alTeclaBraille`, `braille`, `apps`, `abrirApp`, `guardar` y `leer` |
| `notificaciones-a-medida` (Notificaciones a tu medida) | Quita emojis y repeticiones, agrupa ráfagas, resume las descargas, horario de silencio y palabras importantes | `alNotificar` con `mensajes`, `ahora`, `guardar` |
| `recordatorios` (Recordatorios) | Recordatorios del día a su hora, repetidos hasta marcarlos como hechos | `elegir`, `confirmar`, `pedirTexto`, `cada` y `ahora` |
| `reloj-parlante` (Reloj parlante) | Dice la hora cada hora, media o cuarto, con cronómetro y temporizador | `ahora` con plan B, `cada`, `pantalla`, `guardar` y `borrarDato` |
| `version-de-la-app` (Versión de la app) | Dice y copia el nombre, la versión y la fecha de actualización de la app de delante | `infoApp`; el más corto, buen punto de partida |
| `vigilante-de-bateria` (Vigilante de la batería) | Avisa al enchufar, al llegar a un nivel, al bajar de otro y si se calienta | `alBateria`, `bateria` |

Ninguno usa todavía `alVentana`, `reglas` ni `actividades`: sus ejemplos están en este documento.
