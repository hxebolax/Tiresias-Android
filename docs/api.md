# API de Tiresias para otras apps

Una app (un juego, una app sin accesibilidad, una herramienta) puede pedir a Tiresias que diga un texto, que se calle o que escriba un mensaje en la línea braille. Cada app necesita el permiso del usuario.

## Cómo funciona

- Es un servicio enlazado de Tiresias, con la acción `com.xebolax.tiresias.API` y un `Messenger`.
- Tiresias sabe qué app manda cada mensaje por `Message.sendingUid`, no por lo que diga la app.
- **La primera vez** que una app manda algo, Tiresias pregunta al usuario, en voz alta y en una ventana, si la deja. Mientras no conteste, la respuesta es `PREGUNTANDO` y no se dice nada. Lo que conteste se recuerda, y se cambia en Ajustes de Tiresias, «Apps que usan Tiresias».
- Como mucho, un mensaje cada 50 ms por app, y 2000 caracteres por texto. Lo que pasa de ahí se descarta.
- Tiresias no guarda nada de lo que mandan las apps.

## Mensajes

| `what` | Qué hace | Datos (`Bundle`) |
|---|---|---|
| `1` HABLAR | Dice el texto con la voz de Tiresias y lo pone unos segundos en la línea braille | `texto` (String), `interrumpir` (Boolean: cortar lo que esté diciendo; si no, va detrás) |
| `2` CALLAR | Calla la voz | — |
| `3` BRAILLE | Pone el texto unos segundos en la línea braille, sin voz | `texto` |
| `4` ESTADO | No hace nada: sirve para saber si Tiresias está activo y si la app tiene permiso | — |
| `5` ZONA_TOQUE_DIRECTO | Los toques en esa zona de la pantalla van directos a la app mientras está delante (un juego, un piano, una firma); todo a 0 la quita. Android 11 o posterior | `izquierda`, `arriba`, `derecha`, `abajo` (Int, píxeles de la pantalla) |
| `6` LINEA_CONECTAR | La app hace de línea braille (un anotador, el programa del fabricante). Hace falta `replyTo`: ahí llegan las celdas. Solo si no hay una línea de verdad conectada. Android 15 o posterior | `celdas` (Int, de 1 a 160) |
| `7` LINEA_TECLA | Una tecla de esa línea | `orden` (String): `desplazar:adelante`, `desplazar:atras`, `celda:N` (encaminamiento), `puntos:N` (los puntos de una celda, por ejemplo `puntos:125`), `accion:NOMBRE` (una acción de Tiresias, por ejemplo `accion:ATRAS`), `espacio`, `borrar` o `intro` |
| `8` LINEA_DESCONECTAR | La app deja de hacer de línea braille | — |

Mientras una app hace de línea braille, Tiresias le manda `9` LINEA_CELDAS a su `replyTo` cada vez que cambia lo que se ve, con `celdas` (ByteArray): un byte por celda, con el punto 1 en el bit 0 y el punto 8 en el bit 7.

Si el mensaje trae `replyTo`, Tiresias contesta con el mismo `what` y un `Bundle` con `resultado` (Int):

| `resultado` | Significa |
|---|---|
| `0` HECHO | Hecho |
| `1` SIN_PERMISO | El usuario no dejó a esta app usar Tiresias |
| `2` PREGUNTANDO | Tiresias está preguntando al usuario; prueba más tarde |
| `3` INACTIVO | Tiresias no está activo como lector de pantalla |

Con el modo «primero braille» activo, la voz solo dice los avisos de Tiresias y las notificaciones: lo que mande la app se ve en la línea braille, pero no suena.

## Ejemplo en Kotlin

En el `AndroidManifest.xml` de tu app (Android 11 o posterior solo deja ver las apps que se declaran):

```xml
<queries>
	<package android:name="com.xebolax.tiresias" />
</queries>
```

```kotlin
class Tiresias(private val contexto: Context) {
	private var mensajero: Messenger? = null

	private val conexion = object : ServiceConnection {
		override fun onServiceConnected(nombre: ComponentName?, binder: IBinder?) {
			mensajero = Messenger(binder)
		}
		override fun onServiceDisconnected(nombre: ComponentName?) {
			mensajero = null
		}
	}

	/** Se conecta si Tiresias está instalado; devuelve false si no. */
	fun conectar(): Boolean {
		val intent = Intent("com.xebolax.tiresias.API").setPackage("com.xebolax.tiresias")
		return contexto.bindService(intent, conexion, Context.BIND_AUTO_CREATE)
	}

	fun decir(texto: String, interrumpir: Boolean = true) {
		val m = Message.obtain(null, 1).apply {
			data = Bundle().apply {
				putString("texto", texto)
				putBoolean("interrumpir", interrumpir)
			}
		}
		runCatching { mensajero?.send(m) }
	}

	fun desconectar() = runCatching { contexto.unbindService(conexion) }
}
```

Lo normal es usar la API solo cuando Tiresias es el lector activo. Si no lo es, deja que hable el lector que haya: anuncia los cambios con `View.announceForAccessibility` o con regiones vivas (`accessibilityLiveRegion`).

## Para probarla

La variante de pruebas de Tiresias (`com.xebolax.tiresias.pruebas`) tiene la misma API. Con ella, usa ese paquete en `setPackage` y en `<queries>`. La prueba `FuncionesTest.laApiHablaYContesta` la usa en el emulador.
