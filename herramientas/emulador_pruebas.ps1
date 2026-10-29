# -*- coding: utf-8 -*-
# Tiresias - Pruebas en el emulador (NUNCA en un movil real).
#
#   emulador_pruebas.ps1 -Que lector         LectorTest: que dice Tiresias de cada control del muestrario,
#                                            casillas, regiones vivas, final de lista, web y latencia
#   emulador_pruebas.ps1 -Que accesibilidad  AccesibilidadTest: las pantallas de Tiresias con el
#                                            Accessibility Test Framework (el del agente android-accesible)
#   emulador_pruebas.ps1 -Que latencia       solo la prueba de latencia, con el resumen
#   emulador_pruebas.ps1 -Que todo           todas las pruebas del lector, una clase cada vez
#   emulador_pruebas.ps1 -Que funciones      API para otras apps, primero braille, IA sin configurar,
#                                            los complementos de la tienda y la copia de seguridad
#
# Activa Tiresias en el emulador (ANDROID_SERIAL=emulator-...) guardando antes
# los servicios de accesibilidad que hubiera y los deja como estaban al acabar:
# cambiar servicios de accesibilidad no se hace nunca en el movil de alguien.

param(
	[ValidateSet("lector", "accesibilidad", "latencia", "funciones", "todo")]
	[string]$Que = "lector"
)

$ErrorActionPreference = "Stop"
# adb escribe en UTF-8: sin esto los acentos del informe salen mal
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Raiz = Split-Path -Parent $PSScriptRoot
$Entorno = Join-Path $Raiz "entorno"
$env:JAVA_HOME = Join-Path $Entorno "jdk"
$env:ANDROID_HOME = Join-Path $Entorno "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $Entorno "gradle-home"
$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$Gradle = Join-Path $Entorno "gradle\bin\gradle.bat"
$Paquete = "com.xebolax.tiresias.pruebas"
$Servicio = "$Paquete/com.xebolax.tiresias.servicio.ServicioTiresias"

$emulador = & $Adb devices | Select-String "^emulator-\S+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1
if (-not $emulador) {
	Write-Host "[ERROR] No hay ningun emulador abierto. Abrelo con compilar.bat (opcion 5)." -ForegroundColor Red
	exit 1
}
$env:ANDROID_SERIAL = $emulador
Write-Host "[Emulador] Usando $emulador"

# Compilar e instalar Tiresias (variante de pruebas) y el muestrario
& $Gradle --no-daemon -p $Raiz :app:assembleDebug :muestrario:assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $Adb install -r (Join-Path $Raiz "app\build\outputs\apk\debug\app-debug.apk") | Out-Null
& $Adb install -r (Join-Path $Raiz "muestrario\build\outputs\apk\debug\muestrario-debug.apk") | Out-Null

# Guardar los servicios de accesibilidad y activar Tiresias (solo en el emulador)
$antes = (& $Adb shell settings get secure enabled_accessibility_services).Trim()
& $Adb shell settings put secure enabled_accessibility_services $Servicio
& $Adb shell settings put secure accessibility_enabled 1
# Tiresias en español para las pruebas (sus frases esperadas estan en español)
& $Adb shell cmd locale set-app-locales $Paquete --locales es-ES | Out-Null
Start-Sleep -Seconds 4

$clase = switch ($Que) {
	"lector" { "com.xebolax.tiresias.LectorTest" }
	"latencia" { "com.xebolax.tiresias.LectorTest#latenciaDelGestoALaVoz" }
	"accesibilidad" { "com.xebolax.tiresias.AccesibilidadTest" }
	"funciones" { "com.xebolax.tiresias.FuncionesTest,com.xebolax.tiresias.NovedadesTest,com.xebolax.tiresias.CoherenciaTest,com.xebolax.tiresias.PeticionesTest,com.xebolax.tiresias.EloquenceTest,com.xebolax.tiresias.SeniorTest,com.xebolax.tiresias.PeticionesSegundaTest,com.xebolax.tiresias.Peticiones105Test,com.xebolax.tiresias.Peticiones106Test,com.xebolax.tiresias.ComplementosTest,com.xebolax.tiresias.CopiaSeguridadTest" }
	"todo" { "com.xebolax.tiresias.LectorTest,com.xebolax.tiresias.NavegacionTest,com.xebolax.tiresias.LecturaTest,com.xebolax.tiresias.AjustesTest,com.xebolax.tiresias.AvisosTeclasTest,com.xebolax.tiresias.AccionesTest,com.xebolax.tiresias.FuncionesTest,com.xebolax.tiresias.NovedadesTest,com.xebolax.tiresias.CoherenciaTest,com.xebolax.tiresias.PeticionesTest,com.xebolax.tiresias.EloquenceTest,com.xebolax.tiresias.SeniorTest,com.xebolax.tiresias.PeticionesSegundaTest,com.xebolax.tiresias.Peticiones105Test,com.xebolax.tiresias.Peticiones106Test,com.xebolax.tiresias.ComplementosTest,com.xebolax.tiresias.CopiaSeguridadTest,com.xebolax.tiresias.AccesibilidadTest" }
}
& $Adb logcat -c
# Una clase cada vez: cmd.exe (gradle.bat) parte los argumentos por las comas
# (sin parar en el primer fallo: lo que Gradle escribe en stderr no corta el resto)
$ErrorActionPreference = "Continue"
$codigo = 0
foreach ($c in ($clase -split ",")) {
	& $Gradle --no-daemon -p $Raiz :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$c"
	if ($LASTEXITCODE -ne 0) { $codigo = $LASTEXITCODE; Write-Host "[FALLO] $c" -ForegroundColor Red } else { Write-Host "[BIEN] $c" -ForegroundColor Green }
	# El resultado de cada clase, con los fallos (el informe de Gradle se sobrescribe en la siguiente)
	$resultados = Join-Path $Raiz "docs\informes\resultados"
	New-Item -ItemType Directory -Force $resultados | Out-Null
	Get-ChildItem (Join-Path $Raiz "app\build\outputs\androidTest-results\connected\debug") -Recurse -Filter "TEST-*.xml" -ErrorAction SilentlyContinue |
		ForEach-Object { Copy-Item -Force $_.FullName (Join-Path $resultados (($c -split "\.")[-1] + ".xml")) }
}

$informe = Join-Path $Raiz "docs\informes"
New-Item -ItemType Directory -Force $informe | Out-Null
$registro = Join-Path $informe "pruebas_$Que.txt"
& $Adb logcat -d -s LectorTest TiresiasLatencia Recorrido ATF Foco PruebaTiresias | Out-File -Encoding utf8 $registro
Write-Host "[Informe] Registro de la prueba en $registro"
if ($Que -ne "accesibilidad") {
	Get-Content $registro | Select-String "Latencia del gesto" | ForEach-Object { Write-Host "[Latencia] $_" -ForegroundColor Cyan }
}

# Dejar los servicios como estaban
if ($antes -and $antes -ne "null") { & $Adb shell settings put secure enabled_accessibility_services $antes }
else { & $Adb shell settings delete secure enabled_accessibility_services | Out-Null }
exit $codigo
