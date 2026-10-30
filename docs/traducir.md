# Cómo traducir Tiresias a otro idioma

Tiresias está en español de España (el idioma base) y en inglés. Esta guía explica todo lo que hay que traducir para añadir otro idioma, dónde está y cómo se prueba y se envía. Está sacada del código de la 1.0.5.

En toda la guía, `XX` es el código de dos letras del idioma (ISO 639-1), por ejemplo `fr`, `pt` o `ca`. Es el mismo en todos los sitios: la carpeta de las cadenas, la de las tablas, el manual y la lista de idiomas.

## Índice

1. [Qué hay que traducir](#qué-hay-que-traducir)
2. [Las cadenas de la app](#las-cadenas-de-la-app)
3. [Dar de alta el idioma](#dar-de-alta-el-idioma)
4. [Los textos del núcleo](#los-textos-del-núcleo)
5. [Las tablas de lectura](#las-tablas-de-lectura)
6. [El manual](#el-manual)
7. [El braille](#el-braille)
8. [Las voces](#las-voces)
9. [Palabras que Tiresias busca en otras apps y en la voz](#palabras-que-tiresias-busca-en-otras-apps-y-en-la-voz)
10. [Los complementos](#los-complementos)
11. [La web y las novedades](#la-web-y-las-novedades)
12. [Cómo probar la traducción](#cómo-probar-la-traducción)
13. [Cómo enviarla](#cómo-enviarla)
14. [Lista de comprobación](#lista-de-comprobación)

## Qué hay que traducir

| Qué | Dónde | Hace falta tocar código |
|---|---|---|
| La interfaz y las frases del lector | `app/src/main/res/values-XX/strings*.xml` | No |
| El complemento de Gemini Nano | `nano/src/main/res/values-XX/strings.xml` | No |
| Que el idioma se pueda elegir | `base/Idioma.kt`, `strings_ajustes.xml` y `lectura/Ejecutor.kt` | Sí, una línea en cada sitio |
| Símbolos, emojis, letras, alfabeto fonético e identificadores | `app/src/main/assets/idiomas/XX/` | No |
| El manual | `app/src/main/assets/manual-XX.html` | No |
| La tabla braille de fábrica del idioma | `braille/Braille.kt` | Sí, si no quieres la inglesa |
| La voz eSpeak del idioma | `assets-espeak` y `voz/EspeakNativo.kt` | Sí, si eSpeak no la trae ya |
| Abreviaturas, órdenes por voz y palabras de botones | `nucleo/Segmentos.kt`, `escucha/Escucha.kt` y otros | Sí, para que todo funcione igual de bien |
| Los complementos de la tienda | `complementos/*/complemento.js` | Sí, son JavaScript |

Lo mínimo para que Tiresias hable y se use en tu idioma son las cadenas, el alta del idioma y las tablas de lectura. Lo demás mejora la experiencia y se puede enviar después.

## Las cadenas de la app

### Los archivos

Copia los archivos `strings*.xml` de `app/src/main/res/values/` a una carpeta nueva `app/src/main/res/values-XX/` y tradúcelos. Son ocho; la carpeta `values-en/` es un buen modelo, porque está completa.

| Archivo | Qué tiene | Cadenas | Plurales | Listas |
|---|---|---|---|---|
| `strings.xml` | Los textos generales de la app y del servicio | 150 | 3 | 0 |
| `strings_accesibilidad.xml` | Los textos comunes de los diálogos y las pantallas («Aceptar», «Cancelar», «Copiado», «Espera un momento») | 14 | 4 | 0 |
| `strings_acciones.xml` | El nombre de cada acción (`accion_*`), gesto (`gesto_*`) y control de lectura (`control_*`) | 265 | 2 | 0 |
| `strings_actualizaciones.xml` | El actualizador | 58 | 0 | 0 |
| `strings_ajustes.xml` | Los ajustes (`aj_*`) y las listas de opciones (`*_nombres`) | 324 | 0 | 25 |
| `strings_ia.xml` | La inteligencia artificial, incluidas las instrucciones que se le mandan | 139 | 1 | 0 |
| `strings_lectura.xml` | Las frases del lector (`f_*`), los plurales del lector (`p_*`) y lo que dice la voz | 143 | 10 | 0 |
| `strings_pantallas.xml` | Las pantallas, el manual (`man_*`), los sonidos, los complementos y la tienda | 339 | 6 | 0 |

No copies `colors.xml`, `ids.xml`, `ids_accesibilidad.xml` ni `themes.xml`: no tienen texto.

El complemento de Gemini Nano es un APK aparte con sus propias 14 cadenas: copia `nano/src/main/res/values/strings.xml` a `nano/src/main/res/values-XX/strings.xml`.

### Las reglas

1. **No cambies ningún `name=`.** Tiresias busca muchas cadenas por su nombre, formado con un prefijo y el nombre de una clave del código: `f_` y `p_` (las frases y plurales del núcleo), `accion_`, `gesto_`, `control_`, `aj_`, `sonido_`, `parte_` y `estado_parte_`. Si cambias un nombre, Tiresias no la encuentra.
2. **Respeta los marcadores** tal cual: `%1$s` (un texto), `%1$d` (un número), `%2$s`… Puedes cambiarlos de sitio en la frase según tu idioma, pero no quitarlos, ni cambiar su número o su letra. `%%` es un signo de por ciento.
3. **Escapa** el apóstrofo como `\'` y las comillas rectas como `\"`, y escribe `\n` para un salto de línea. Las comillas tipográficas (« », “ ”, ‘ ’) no se escapan. En inglés, «Don\'t» se escribe así.
4. **No traduzcas ni copies lo que lleva `translatable="false"`.** Son las 24 listas `*_valores` de `strings_ajustes.xml`, con los valores internos de cada ajuste. Sus listas hermanas `*_nombres` sí se traducen, y tienen que tener **el mismo número de elementos y en el mismo orden** que su `*_valores`: el primer nombre corresponde al primer valor. `verbosidad_todas` también se traduce.
5. **Los plurales** usan las categorías de tu idioma según el CLDR de Unicode (`zero`, `one`, `two`, `few`, `many`, `other`). El español usa `one`, `many` y `other` (el `many` es para los millones); el inglés, `one` y `other`. Pon todas las de tu idioma y siempre `other`.
6. **Las instrucciones de la IA** (`ia_instrucciones`, `iconos_instrucciones`, `actuar_instrucciones` y `actuar_peticion`, en `strings_ia.xml`) se traducen, pero:
   - en `actuar_instrucciones`, deja sin traducir las claves y los valores del JSON (`accion`, `tocar`, `mantener`, `escribir`, `desplazar`, `ninguna`, `elemento`, `x`, `y`, `texto`, `direccion`, `arriba`, `abajo`, `izquierda`, `derecha`, `explicacion`): el código los lee así;
   - en `iconos_instrucciones`, la respuesta para «no lo sé» tiene que ser una de las que Tiresias reconoce («unknown», «no sé», «desconocido», «ninguno», «nada», «none»…, en `nucleo/NombreIcono.kt`); lo más seguro es pedir «unknown»;
   - `ia_instrucciones` recibe en `%1$s` el nombre del idioma de la respuesta.
7. **Que no queden largas.** Los nombres de las acciones, gestos y controles se dicen muy a menudo: cortos y claros.

Si falta una cadena en tu idioma, Android usa la española sin cerrarse. La revisión de Gradle no avisa de las que faltan (`MissingTranslation` está desactivado en `app/build.gradle.kts`): usa el comando de [Cómo probar la traducción](#cómo-probar-la-traducción) para encontrarlas.

## Dar de alta el idioma

Estos cambios son de código, pero de una línea cada uno:

1. En `app/src/main/java/com/xebolax/tiresias/base/Idioma.kt`, añade el código a `DISPONIBLES`: `listOf("es", "en", "XX")`. Es la lista de los idiomas que Tiresias sabe hablar: si el del móvil no está, usa el español.
2. En `app/src/main/res/values/strings_ajustes.xml`:
   - añade `<item>XX</item>` al final de `idiomas_valores`;
   - añade al final de `idiomas_nombres` el nombre del idioma escrito en ese idioma («Français», «Português»).
3. En `idiomas_nombres` de **cada** traducción (`values-en/strings_ajustes.xml`, `values-XX/strings_ajustes.xml`…), añade también el nombre al final. Se escribe igual en todas, en su propio idioma, como «Español» y «English».
4. En `app/src/main/java/com/xebolax/tiresias/lectura/Ejecutor.kt`, función `cambiarIdioma`, añade el código a `listOf("", "es", "en")`. Es la lista por la que pasan el control de lectura Idioma y la acción de cambiar de idioma; si no lo añades, solo se puede elegir en Ajustes.

En Android 13 o posterior, el idioma aparece además en los ajustes del sistema, Idioma de la app, sin hacer nada más: la lista se genera al compilar a partir de las carpetas `values-XX` (`generateLocaleConfig = true` en `app/build.gradle.kts`) y Tiresias la sigue si el idioma está en `DISPONIBLES`.

Tiresias usa el idioma con el país del móvil (`fr` con Canadá da `fr-CA`), salvo el español, que va siempre con España.

## Los textos del núcleo

El núcleo (`nucleo/src/main/kotlin`) es el corazón del lector y no sabe de idiomas: todo lo que dice lo saca de las cadenas de la app.

- **Las plantillas**: cada `Frase` de `nucleo/Plantillas.kt` es la cadena `f_<nombre en minúsculas>` de `strings_lectura.xml`, y cada `FrasePlural` el plural `p_<nombre>`. Ahí están los roles («botón», «casilla»), los estados («marcado», «expandido»), las posiciones («%1$d de %2$d»), los encabezados, las pistas de uso («Toca dos veces para activar.») y los plurales («lista con %1$d elementos»). El comentario de cada una en `Plantillas.kt` dice qué va en cada marcador. Si falta una, Tiresias dice su nombre interno.
- **La voz**: «mayúscula %1$s» y «espacio» son `voz_mayuscula` y `voz_espacio`, también en `strings_lectura.xml`.
- **La verbosidad, la puntuación, los números, las mayúsculas y los emojis**: sus nombres son las listas `verbosidad_nombres`, `puntuacion_nombres`, `numeros_nombres`, `mayusculas_nombres` y `emojis_nombres` de `strings_ajustes.xml`. Qué símbolos se dicen en cada nivel de puntuación lo pone la tabla de símbolos (ver más abajo).
- **Los decimales** se escriben con el separador del idioma sin hacer nada.
- **Las abreviaturas** que no cortan una frase («Sr.», «etc.», «Mr.») están en la lista `ABREVIATURAS` de `nucleo/Segmentos.kt`, en español e inglés. Añade las de tu idioma para que leer por frases no se pare en ellas.

## Las tablas de lectura

Cada idioma tiene su carpeta `app/src/main/assets/idiomas/XX/` con cinco archivos de texto en UTF-8. Son tablas con un tabulador entre columnas; las líneas vacías y las que empiezan por `#` se saltan. Crea los cinco a partir de los de `es/` o `en/`.

| Archivo | Qué es | Formato | Si falta |
|---|---|---|---|
| `simbolos.tsv` | Cómo se dice cada símbolo y en qué nivel de puntuación | `símbolo<TAB>nombre<TAB>nivel[<TAB>mantener]` | Se usa el español |
| `emojis.tsv` | Los nombres de los emojis y de otros símbolos de Unicode | Igual que `simbolos.tsv` | Se usa el español |
| `letras.tsv` | Las letras que necesitan nombre al leerlas sueltas («y» se dice «i griega») | `letra<TAB>nombre` | Se usa el español |
| `teclas.tsv` | Las etiquetas en inglés de las teclas del teclado en pantalla (Gboard las da así en algunos móviles: «period», «comma») con su nombre en este idioma | `etiqueta en inglés<TAB>nombre` | Sin tabla propia, ninguna (la del español traduciría al español) |
| `fonetico.tsv` | El alfabeto para deletrear («a de Antonio») | `letra<TAB>palabra` | Se usa el español |
| `identificadores.tsv` | Las palabras de los identificadores de vista (casi siempre en inglés) en tu idioma | Ver abajo | Se usa el inglés: solo quita el relleno |

Como lo que falta cae en español, crea siempre los cinco: si no, con Tiresias en tu idioma se oirían los símbolos y las letras en español.

### `simbolos.tsv` y `emojis.tsv`

- El nivel es una de estas palabras, que **no se traducen**: `ninguna` (se dice siempre), `algunas`, `mayoria`, `todas` y `caracter` (solo al leer carácter a carácter). Con la puntuación del usuario en un nivel, se dicen los símbolos de ese nivel y de los anteriores.
- `mantener` (tampoco se traduce), en la cuarta columna: además del nombre, se deja el signo para que la voz haga la entonación (los puntos, las interrogaciones).
- Para un espacio, escribe `\s`; para un tabulador, `\t`; para una barra invertida, `\\`.
- Una línea que empieza por `#` es un comentario, salvo la de la propia almohadilla: `#`, un tabulador y su nombre (`#	almohadilla	algunas`).
- En `emojis.tsv` los emojis van con nivel `ninguna`; lo de `simbolos.tsv` manda sobre `emojis.tsv`.

### `identificadores.tsv`

Cuando un control no tiene nombre, Tiresias dice su identificador de vista (`contact_photo`) traducido («foto de contacto»). La tabla tiene:

- `palabra o frase en inglés<TAB>traducción<TAB>tipo`, con tipo `n` (nombre), `a` (adjetivo o adverbio) o `v` (verbo, o una palabra que no cambia de sitio). Varias palabras en la primera columna son una frase, que manda sobre las palabras sueltas («video call», «videollamada»).
- `palabra<TAB>-`: una palabra de relleno, que se quita («view», «btn», «iv»).
- La línea `@invertir`: el orden del español, con los nombres y adjetivos seguidos al revés («dark mode», «modo oscuro»).
- La línea `@union <palabra>`: la palabra que se pone entre dos nombres con `@invertir` (en español, `@union de`: «contact photo», «foto de contacto»).

Las palabras que no están en la tabla se dicen tal cual. La tabla española tiene más de 400 líneas: es la referencia.

### Regenerar los símbolos y los emojis de NVDA

Una parte de las tablas sale de NVDA, que tiene símbolos y emojis en muchos idiomas:

- `herramientas/tablas_nvda_simbolos.py` añade al final de `simbolos.tsv` los símbolos del `symbols.dic` de NVDA que la tabla no tiene, entre dos marcas (`# --- Desde aquí, generado del symbols.dic de NVDA` y `# --- Fin de lo generado de NVDA ---`). Lo que escribas tú, antes de la primera marca, manda y no se toca.
- `herramientas/tablas_nvda_emojis.py` escribe `emojis.tsv` entero a partir del `cldr.dic` de NVDA (datos del Unicode CLDR).

Los dos necesitan NVDA instalado, leen de `C:/Program Files/NVDA/locale/<idioma>/`, escriben en `E:/Antigravity/Tiresias_Android/app/src/main/assets/idiomas/` (rutas escritas en el script) y solo generan `es` y `en`. Para tu idioma:

1. Cambia las rutas si tu proyecto o NVDA están en otro sitio.
2. Añade tu código a la línea final `for idioma in ('es', 'en'):` de los dos scripts y, en el de emojis, una cabecera para tu idioma en `CABECERA`. El nombre de la carpeta de NVDA tiene que coincidir con tu código.
3. Crea antes tu `simbolos.tsv` con lo propio (el script de símbolos lo necesita).
4. Pásalos con `python herramientas\tablas_nvda_simbolos.py` y `python herramientas\tablas_nvda_emojis.py`.

Los datos de NVDA tienen licencia GPL-2 y los del CLDR, Unicode-3.0: se citan en `app/src/main/assets/licencias/TERCEROS.txt`, que hoy habla del español y el inglés. Añade ahí tu idioma.

## El manual

1. Copia `app/src/main/assets/manual-es.html` a `manual-XX.html` y tradúcelo.
2. Pon `lang="XX"` en la etiqueta `<html>`.
3. Deja tal cual, cada uno en su sitio, los comentarios `<!-- TIRESIAS:GESTOS -->`, `<!-- TIRESIAS:TECLAS -->` y `<!-- TIRESIAS:BRAILLE -->`. Al abrir el manual, Tiresias pone ahí las tablas de gestos, teclas y braille con las asignaciones que tiene el usuario y los textos de las cadenas `man_*` de `strings_pantallas.xml` (que ya habrás traducido).

Tiresias abre `manual-<idioma de Tiresias>.html` y, si no existe, `manual-es.html`. El manual se ve sin JavaScript.

## El braille

### La tabla informática de 8 puntos

Tiresias trae 17 tablas de liblouis en `app/src/main/assets/braille/`, con su idioma en la lista `TABLAS_INFORMATICAS` de `braille/Braille.kt`:

| Idioma | Tabla |
|---|---|
| Español | `Es-Es-G0.utb` |
| Inglés | `en-us-comp8-ext.utb`, `en-us-comp8.ctb` |
| Árabe, checo, alemán, finés, francés, hebreo, croata, húngaro, italiano, neerlandés, noruego, polaco, portugués y ruso | Una cada uno: `ar-ar-comp8.utb`, `cs-comp8.utb`, `de-de-comp8.ctb`, `fi-fi-8dot.ctb`, `fr-bfu-comp8.utb`, `he-IL-comp8.utb`, `hr-comp8.utb`, `hu-hu-comp8.ctb`, `it-it-comp8.utb`, `nl-comp8.utb`, `no-no-comp8.ctb`, `pl-pl-comp8.ctb`, `pt-pt-comp8.ctb`, `ru-comp8.utb` |

El usuario puede elegir cualquiera en los ajustes de braille («Tabla informática de 8 puntos»). La de fábrica, «La del idioma de Tiresias», la elige `cargarTabla()` en `Braille.kt`, y hoy solo distingue el español: cualquier otro idioma usa `en-us-comp8-ext.utb`. Para que tu idioma tenga la suya de fábrica, cambia esa línea para que elija de `TABLAS_INFORMATICAS` la de tu código.

Si tu idioma no tiene tabla:

1. Copia en `app/src/main/assets/braille/` su tabla de liblouis y las que incluya con `include` (están en liblouis y en la carpeta `braille/translate/src/phone/tables` de TalkBack).
2. Añádela a `TABLAS_INFORMATICAS` con tu código.
3. Ten en cuenta que Tiresias solo usa las órdenes de un carácter por celda (`letter`, `lowercase`, `uppercase`, `digit`, `punctuation`, `sign`, `math`, `space`…), no las contracciones.
4. Añade su licencia a `licencias/TERCEROS.txt` si es de otro origen.

### La tabla literaria de 6 puntos

La tabla literaria (`TablaLiteraria`, en `nucleo/src/main/kotlin/com/xebolax/tiresias/nucleo/braille/TablaBraille.kt`) es el braille español de grado 1 de la ONCE, escrito en el código. No hay otra: para otro idioma, recomienda la informática de 8 puntos.

## Las voces

La voz del idioma la pone el motor que elija el usuario, siguiendo el idioma de Tiresias:

| Motor | Idiomas | Dónde se elige la voz |
|---|---|---|
| eSpeak NG (incluido) | Español de España y de América, inglés de EE. UU. y británico, francés, alemán, italiano, portugués de Portugal y de Brasil | `vozPara()` en `voz/EspeakNativo.kt` |
| Pico (incluido) | Español de España, inglés de EE. UU. y británico | `VozPico.para()` en `voz/PicoNativo.kt` |
| Eloquence | Inglés, español, francés, alemán, italiano, polaco y japonés, con sus variantes | `voz/MotorEloquence.kt` |
| Piper (voces neuronales que se descargan) | Las del catálogo: hoy español e inglés | `recursos/VocesNeuronales.kt` |
| Las voces del sistema | Las que tenga el móvil | Ajustes de la voz |

**eSpeak.** Si tu idioma es uno de los que trae Tiresias, ya funciona. Si no, eSpeak lo tiene casi seguro, pero hay que añadirlo:

1. Copia de los datos de eSpeak NG 1.52 su diccionario (`XX_dict`) a `app/src/main/assets-espeak/espeak-ng-data/` y su archivo de idioma a la carpeta de su familia en `espeak-ng-data/lang/` (por ejemplo, el catalán en `lang/roa/ca`).
2. Añade tu código a `vozPara()` en `EspeakNativo.kt`: hoy un idioma que no está ahí habla con la voz española.
3. Sube `VERSION_DATOS` en `EspeakNativo.kt`: Tiresias copia los datos de eSpeak al móvil solo cuando cambia esa versión, así que sin subirla los móviles que ya tienen Tiresias no verían el idioma nuevo.
4. Para que Tiresias ofrezca tu idioma también como motor de voz del sistema, añade su voz a `VOCES` y su código de tres letras a `corto()` en `tts/ServicioTtsTiresias.kt`.

**Pico.** Un idioma que Pico no tiene usa la voz española. Pico existe también en francés, alemán e italiano, pero Tiresias no trae esas voces: añadirlas sería poner sus archivos en `assets/pico/` y su línea en `VozPico`.

## Palabras que Tiresias busca en otras apps y en la voz

Algunas funciones buscan palabras escritas en el código. No están en las cadenas porque no dependen del idioma de Tiresias, sino del de las apps o del de quien habla. Si tu idioma no está, esas funciones no lo reconocen:

| Qué | Dónde | Qué añadir |
|---|---|---|
| Contestar, colgar y rechazar las llamadas de internet con el gesto | `CONTESTAR`, `COLGAR` y `RECHAZAR` en `lectura/Avisos.kt` | Los nombres de esos botones en las notificaciones de llamada |
| Abrir «Leer más» y parecidos | `LEER_MAS` en `lectura/Lector.kt` | Cómo dicen las apps «leer más», «ver más» |
| Los nombres de los iconos que propone la IA | `VACIAS` y `PREFIJO` en `nucleo/NombreIcono.kt` | Cómo se dice «no lo sé» y «nombre:», «botón:» |
| Las órdenes por voz | `ORDENES` y las expresiones de «abre» y «busca» en `escucha/Escucha.kt` | Las frases de cada orden, sin tildes y en minúsculas |
| El modelo de reconocimiento de las órdenes por voz | `VOSK_ES` en `recursos/Recursos.kt` | Hoy solo se descarga el modelo español de Vosk |

## Los complementos

Los complementos de la tienda (`complementos/*/complemento.js`) tienen sus propios textos, en el manifiesto y en el código. Hoy están en español e inglés:

- **El manifiesto**: cada texto puede llevar la traducción con el sufijo del idioma. Añade `nombre_XX` y `descripcion_XX`, y en cada ajuste `nombre_XX`, `descripcion_XX`, `unidad_XX` y el `nombre_XX` de cada opción. Tiresias usa el de su idioma y, si no lo hay, el español.
- **El código**: cada complemento tiene un objeto `TEXTOS` con un apartado por idioma (`es`, `en`) y elige con `tiresias.idioma()`. Añade el apartado `XX` con las mismas claves. Muchas entradas son funciones, para los plurales y el orden de las palabras: tradúcelas como funciones.
- **La tienda**: el catálogo admite `nombre_XX` y `descripcion_XX`, pero `herramientas/publicar_actualizacion.ps1` solo copia `nombre_en` y `descripcion_en`. Para que la tienda los enseñe en tu idioma, hay que añadir tus dos campos a ese script.
- **Las pruebas**: las del banco de Node (`herramientas/banco-complementos`) y `ComplementosTest` esperan las frases en español, así que no cambian.

Los detalles de las traducciones de los complementos están en [docs/complementos.md](complementos.md#las-traducciones). Ten en cuenta el aviso de [Cómo se carga](complementos.md#cómo-se-carga): en la 1.0.4, los nombres de las órdenes que se calculan con `tiresias.idioma()` al cargar salen siempre en español.

## La web y las novedades

- **La web** (`herramientas/web/index.html`) está solo en español. `publicar_actualizacion.ps1` la rellena con la versión, la fecha, las descargas, las novedades y la huella del certificado (`{{VERSION}}`, `{{FECHA}}`, `{{DESCARGAS}}`, `{{NOVEDADES}}`, `{{HUELLA}}`), y los nombres de los procesadores de las descargas están en español dentro del script. No hay todavía una forma de publicar otra página por idioma: si quieres traducirla, propón en el pull request una copia traducida de la plantilla y lo que haría falta en el script.
- **Las novedades** (`NOVEDADES.txt`) se escriben solo en español, y así las ven el actualizador y la web.

## Cómo probar la traducción

### Encontrar lo que falta

Este comando de PowerShell, desde la carpeta del proyecto, compara los nombres de las cadenas, plurales y listas de `values/` con los de tu `values-XX/` (sin las que no se traducen) y enseña los que faltan o sobran. Con `values-en`, hoy da 1483 en las dos y ninguna diferencia:

```powershell
$XX = "fr"
$res = "app\src\main\res"
function Nombres($carpeta) {
	Get-ChildItem "$res\$carpeta\strings*.xml" | ForEach-Object {
		([xml](Get-Content $_.FullName -Raw -Encoding UTF8)).resources.ChildNodes |
			Where-Object { $_.NodeType -eq "Element" -and $_.translatable -ne "false" } | ForEach-Object { $_.name }
	}
}
$base = Nombres "values"
$trad = Nombres "values-$XX"
"base: $($base.Count) traducción: $($trad.Count)"
Compare-Object $base $trad | Format-Table -AutoSize
```

Si da un error de XML, el archivo tiene un fallo de formato (un apóstrofo sin escapar, una etiqueta sin cerrar): Android tampoco compilaría.

### Compilar y elegir el idioma

1. Compila con `compilar.bat` (opción 1). Para el emulador, la opción 8 compila, abre el emulador e instala.
2. Elige el idioma en Tiresias: Ajustes, «Idioma de Tiresias». En Android 13 o posterior, también en los ajustes del sistema, Idioma de la app. En el emulador se puede poner con `adb shell cmd locale set-app-locales com.xebolax.tiresias.pruebas --locales XX` (la variante de depuración se llama `com.xebolax.tiresias.pruebas`).
3. Recorre la app con Tiresias: el menú lateral, los ajustes, el menú de Tiresias, las acciones, los gestos y el manual. Comprueba que se entiende todo, que nada sale en español y que nada es demasiado largo.
4. Lee textos con símbolos, emojis, números y letras sueltas, y deletrea, para probar las tablas.
5. Con una línea braille, comprueba la tabla.

### Las pruebas automáticas

- `compilar.bat` opción 9 (o `compilar.bat pruebas`) pasa las pruebas del núcleo en el PC y, con el emulador abierto, las del lector y las de accesibilidad.
- Las pruebas del emulador (`herramientas\emulador_pruebas.ps1`) ponen Tiresias en español antes de empezar, porque esperan frases en español: comprueban que no has roto nada, pero no revisan tu traducción. Ninguna prueba automática mira hoy las cadenas de otro idioma.
- No hace falta traducir las pruebas.

## Cómo enviarla

1. Haz un fork de `https://github.com/hxebolax/Tiresias-Android` y crea una rama, por ejemplo `traduccion-XX`.
2. Añade tus archivos y cambios:
   - `app/src/main/res/values-XX/` con los ocho `strings*.xml`;
   - `nano/src/main/res/values-XX/strings.xml`;
   - `app/src/main/assets/idiomas/XX/` con los cinco archivos;
   - `app/src/main/assets/manual-XX.html`;
   - el alta del idioma en `Idioma.kt`, `strings_ajustes.xml` (en `values/` y en cada traducción) y `Ejecutor.kt`;
   - lo demás que hayas hecho: la tabla braille, la voz eSpeak, las palabras del código, los complementos.
3. Abre un pull request contra la rama principal. En la descripción, di qué idioma es, qué has traducido, qué falta, cómo lo has probado (en qué móvil o emulador y con qué voz) y si los datos que has copiado (tablas de liblouis, diccionarios de eSpeak, NVDA) tienen otra licencia.
4. Para corregir o completar una traducción que ya existe, el camino es el mismo.

Si no usas Git, abre un issue en `https://github.com/hxebolax/Tiresias-Android/issues` con los archivos adjuntos.

## Lista de comprobación

- [ ] Los ocho `strings*.xml` de `app/src/main/res/values-XX/`, traducidos y sin `name=` cambiados.
- [ ] Ninguna lista `*_valores` ni nada con `translatable="false"` copiado.
- [ ] Cada `*_nombres` con el mismo número de elementos y en el mismo orden que su `*_valores`.
- [ ] Los marcadores (`%1$s`, `%1$d`) intactos, los apóstrofos como `\'` y las comillas rectas como `\"`.
- [ ] Los plurales con todas las categorías de tu idioma y `other`.
- [ ] Las claves del JSON de `actuar_instrucciones` sin traducir, y «unknown» como respuesta de «no lo sé» en `iconos_instrucciones`.
- [ ] El comando de PowerShell no enseña ninguna diferencia.
- [ ] `nano/src/main/res/values-XX/strings.xml`.
- [ ] `XX` en `Idioma.DISPONIBLES`, en `idiomas_valores` (solo en `values/`), en `idiomas_nombres` (en todas las traducciones) y en `cambiarIdioma` de `Ejecutor.kt`.
- [ ] Los cinco archivos de `app/src/main/assets/idiomas/XX/`, con los niveles y `mantener` sin traducir.
- [ ] `manual-XX.html` con `lang="XX"` y los tres comentarios `TIRESIAS:` en su sitio.
- [ ] La tabla braille de fábrica de tu idioma en `cargarTabla()`, o dicho en el pull request que queda la inglesa.
- [ ] La voz eSpeak de tu idioma en `vozPara()` y, si has añadido datos, `VERSION_DATOS` subida.
- [ ] Las abreviaturas de `Segmentos.kt` y las palabras de `Avisos.kt`, `Lector.kt`, `NombreIcono.kt` y `Escucha.kt`, si quieres que esas funciones reconozcan tu idioma.
- [ ] Los complementos con sus `_XX` y su apartado en `TEXTOS`, si los traduces.
- [ ] Compilado, con el idioma elegido en Ajustes, recorrido con Tiresias y con las pruebas pasadas.
- [ ] El pull request con lo traducido, lo que falta y cómo se ha probado.
