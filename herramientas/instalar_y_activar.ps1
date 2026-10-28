# Instala Tiresias en un aparato conectado por USB y, si se pide, lo activa como
# lector de pantalla con adb: para aparatos sin TalkBack (reproductores, relojes,
# radios de coche, ROM libres) en los que una persona ciega no puede activarlo
# sola. Lo ejecuta el dueño del aparato, en su aparato.
#
# Uso: powershell -File herramientas\instalar_y_activar.ps1 [-Activar] [-Apk ruta]
# Necesita la depuración USB activada en el aparato (Opciones de desarrollador).
param(
	[switch]$Activar,
	[string]$Apk = ""
)
$ErrorActionPreference = "Stop"
$Raiz = Split-Path -Parent $PSScriptRoot
$Adb = Join-Path $Raiz "entorno\android-sdk\platform-tools\adb.exe"
if (-not (Test-Path $Adb)) { $Adb = "adb" }

$aparatos = @(& $Adb devices | Select-String "\sdevice$" | ForEach-Object { ($_ -split "\s+")[0] })
if ($aparatos.Count -eq 0) { Write-Host "[ERROR] No hay ningún aparato conectado con la depuración USB activada."; exit 1 }
if ($aparatos.Count -gt 1) { Write-Host "[ERROR] Hay más de un aparato conectado: deja solo el que quieras."; exit 1 }
$serie = $aparatos[0]
$modelo = (& $Adb -s $serie shell getprop ro.product.model).Trim()
$abi = (& $Adb -s $serie shell getprop ro.product.cpu.abi).Trim()
Write-Host "Aparato: $modelo ($serie), procesador $abi"

if (-not $Apk) {
	$candidato = Get-ChildItem (Join-Path $Raiz "dist") -Filter "Tiresias_v*_$abi.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
	if (-not $candidato) { $candidato = Get-ChildItem (Join-Path $Raiz "apk") -Filter "*$abi*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1 }
	if (-not $candidato) { Write-Host "[ERROR] No encuentro el APK para $abi en dist\ ni en apk\. Indícalo con -Apk."; exit 1 }
	$Apk = $candidato.FullName
}
Write-Host "Instalando $Apk ..."
& $Adb -s $serie install -r "$Apk"
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] No se pudo instalar."; exit 1 }

if ($Activar) {
	$respuesta = Read-Host "Se activará Tiresias como lector de pantalla en $modelo. ¿Seguir? (s/n)"
	if ($respuesta -ne "s") { Write-Host "Instalado, sin activar."; exit 0 }
	$servicio = "com.xebolax.tiresias/com.xebolax.tiresias.servicio.ServicioTiresias"
	$actuales = (& $Adb -s $serie shell settings get secure enabled_accessibility_services).Trim()
	if ($actuales -and $actuales -ne "null" -and $actuales -notmatch [regex]::Escape($servicio)) { $servicio = "$actuales`:$servicio" }
	& $Adb -s $serie shell settings put secure enabled_accessibility_services "$servicio"
	& $Adb -s $serie shell settings put secure accessibility_enabled 1
	Write-Host "[OK] Tiresias activado. Si no habla, desbloquea el aparato y espera unos segundos."
} else {
	Write-Host "[OK] Instalado. Para activarlo desde aquí, vuelve a ejecutar con -Activar."
}
