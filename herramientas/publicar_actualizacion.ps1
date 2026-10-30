# -*- coding: utf-8 -*-
# Tiresias - Prepara una actualizacion para el servidor.
#
# Basado en herramientas\publicar_actualizacion.ps1 de Rescate Accesible (mismo autor).
#
# Copia los APK de cada procesador (apk\Tiresias_<procesador>.apk, los deja
# compilar.bat) a dist\Tiresias_v<version>_<procesador>.apk y crea
# dist\version.json con la version, el tamano y el SHA-256 de cada uno
# (apartado "abis": el actualizador elige el del movil; "file" es el de arm64)
# y las novedades (seccion "== <version>" de NOVEDADES.txt). Si existe TiresiasNano.apk, lo publica tambien como
# complemento (TiresiasNano_v<version>.apk, apartado "complementos").
# Despues copia todo a la carpeta del servidor (Synology Drive lo sube a
# https://xebolax.synology.me/Tiresias-Android): primero los APK y al final
# version.json, para que nadie vea una version cuyo APK aun no esta.
#
# Tambien publica la tienda de complementos: complementos\<id>\complemento.js
# pasa a dist\complementos\<id>.js con su catalogo.json, y una copia queda en
# tienda\ (el respaldo en GitHub).
#
# Uso: publicar_actualizacion.ps1 [-Carpeta carpeta_de_los_apk] [-Dist otra_carpeta] [-Servidor carpeta]

param(
	[string]$Carpeta = "",
	[string]$Dist = "",
	[string]$Servidor = "$env:USERPROFILE\SynologyDrive\web\Tiresias-Android"
)

$ErrorActionPreference = "Stop"
$Raiz = Split-Path -Parent $PSScriptRoot
if (-not $Carpeta) { $Carpeta = Join-Path $Raiz "apk" }
$Procesadores = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
$Apk = Join-Path $Carpeta "Tiresias_arm64-v8a.apk"
if (-not $Dist) { $Dist = Join-Path $Raiz "dist" }
$ApkNano = Join-Path $Raiz "TiresiasNano.apk"
$Aapt2 = Join-Path $Raiz "entorno\android-sdk\build-tools\37.0.0\aapt2.exe"
$Utf8 = New-Object System.Text.UTF8Encoding($false)

function Error($texto) { Write-Host "[ERROR] $texto" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $Apk)) { Error "No existe $Apk. Compila primero." }

# Version del proyecto
$gradle = [IO.File]::ReadAllText((Join-Path $Raiz "app\build.gradle.kts"), $Utf8)
$nombre = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$codigo = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $nombre -or -not $codigo) { Error "No se encuentran versionName y versionCode en app\build.gradle.kts" }

# El APK tiene que ser de esa version (evita publicar uno antiguo)
if (Test-Path $Aapt2) {
	$datos = & $Aapt2 dump badging $Apk 2>$null | Select-String "versionCode='(\d+)' versionName='([^']*)'"
	if ($datos) {
		$c = $datos.Matches[0].Groups[1].Value
		$v = $datos.Matches[0].Groups[2].Value
		if ($c -ne $codigo -or $v -ne $nombre) { Error "El APK es la version $v ($c), no la $nombre ($codigo). Compila primero." }
	}
} else {
	Write-Host "[AVISO] Sin aapt2 no se puede comprobar la version del APK."
}

# Novedades de NOVEDADES.txt (seccion "== 1.2.0")
$notas = ""
$fichero = Join-Path $Raiz "NOVEDADES.txt"
if (Test-Path $fichero) {
	$texto = [IO.File]::ReadAllText($fichero, $Utf8)
	$partes = [regex]::Split($texto, '(?m)^==\s*(\S+)\s*$')
	for ($i = 1; $i -lt $partes.Length - 1; $i += 2) {
		if ($partes[$i] -eq $nombre) { $notas = $partes[$i + 1].Trim() }
	}
	if (-not $notas) { Write-Host "[AVISO] NOVEDADES.txt no tiene la seccion '== $nombre': se publica sin novedades." }
}

New-Item -ItemType Directory -Force $Dist | Out-Null
$abis = [ordered]@{}
foreach ($p in $Procesadores) {
	$origen = Join-Path $Carpeta "Tiresias_$p.apk"
	if (-not (Test-Path $origen)) { Error "Falta $origen. Compila primero (compilar.bat, opcion 1)." }
	$a = "Tiresias_v$($nombre)_$p.apk"
	$d = Join-Path $Dist $a
	Copy-Item -Force $origen $d
	$abis[$p] = [ordered]@{
		file = $a
		sha256 = (Get-FileHash -Algorithm SHA256 $d).Hash.ToLower()
		size = (Get-Item $d).Length
	}
}
$archivo = $abis["arm64-v8a"].file

$manifiesto = [ordered]@{
	version = $nombre
	versionCode = [int]$codigo
	file = $archivo
	sha256 = $abis["arm64-v8a"].sha256
	size = $abis["arm64-v8a"].size
	abis = $abis
	date = (Get-Date -Format "yyyy-MM-dd")
	notas = $notas
}

# El complemento de Gemini Nano (APK aparte): solo para los moviles con AICore
$archivoNano = $null
$complementos = @()
if (Test-Path $ApkNano) {
	$archivoNano = "TiresiasNano_v$nombre.apk"
	$destinoNano = Join-Path $Dist $archivoNano
	Copy-Item -Force $ApkNano $destinoNano
	$complementos += [ordered]@{
		id = "nano"
		package = "com.xebolax.tiresias.nano"
		file = $archivoNano
		sha256 = (Get-FileHash -Algorithm SHA256 $destinoNano).Hash.ToLower()
		size = (Get-Item $destinoNano).Length
	}
}
if ($complementos.Count -gt 0) { $manifiesto["complementos"] = @($complementos) }

# La pagina web (herramientas\web\index.html con la version, las descargas, las
# novedades y la huella del certificado), en dist\index.html
function Html($t) { return [System.Net.WebUtility]::HtmlEncode($t) }
$Apksigner = Join-Path $Raiz "entorno\android-sdk\build-tools\37.0.0\apksigner.bat"
$huella = ""
if (Test-Path $Apksigner) {
	$env:JAVA_HOME = Join-Path $Raiz "entorno\jdk"
	$cert = & $Apksigner verify --print-certs (Join-Path $Dist $archivo) 2>$null | Select-String "SHA-256 digest: ([0-9a-f]+)"
	if ($cert) { $huella = (($cert.Matches[0].Groups[1].Value.ToUpper()) -split '(..)' | Where-Object { $_ }) -join ":" }
}
if (-not $huella) { Write-Host "[AVISO] No se pudo leer la huella del certificado para la web." }
$nombresAbi = [ordered]@{ "arm64-v8a" = "ARM de 64 bits (casi todos los móviles)"; "armeabi-v7a" = "ARM de 32 bits (móviles antiguos)"; "x86_64" = "x86 de 64 bits (emuladores, algunas tabletas)"; "x86" = "x86 de 32 bits" }
$descargas = ($Procesadores | ForEach-Object {
	$mb = [math]::Round($abis[$_].size / 1MB, 1).ToString([Globalization.CultureInfo]::GetCultureInfo("es-ES"))
	"<li><a href=""$($abis[$_].file)"">Tiresias $nombre para $(Html $nombresAbi[$_])</a> ($mb MB)</li>"
}) -join "`n"
$novedadesHtml = "<ul>`n"
$abierta = $false
foreach ($linea in ($notas -split "`r?`n")) {
	if ($linea -match '^\s{2,}-\s+(.*)$') {
		if (-not $abierta) { $novedadesHtml += "<ul>`n"; $abierta = $true }
		$novedadesHtml += "<li>$(Html $Matches[1])</li>`n"
	} elseif ($linea -match '^-\s+(.*)$') {
		if ($abierta) { $novedadesHtml += "</ul>`n"; $abierta = $false }
		$novedadesHtml += "<li>$(Html $Matches[1])</li>`n"
	} elseif ($linea.Trim()) {
		if ($abierta) { $novedadesHtml += "</ul>`n"; $abierta = $false }
		$novedadesHtml += "<li>$(Html $linea.Trim())</li>`n"
	}
}
if ($abierta) { $novedadesHtml += "</ul>`n" }
$novedadesHtml += "</ul>"
$plantilla = [IO.File]::ReadAllText((Join-Path $PSScriptRoot "web\index.html"), $Utf8)
$pagina = $plantilla.Replace("{{VERSION}}", $nombre).Replace("{{FECHA}}", (Get-Date -Format "dd/MM/yyyy")).Replace("{{DESCARGAS}}", $descargas).Replace("{{NOVEDADES}}", $novedadesHtml).Replace("{{HUELLA}}", $huella)
[IO.File]::WriteAllText((Join-Path $Dist "index.html"), $pagina, $Utf8)

$json = $manifiesto | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText((Join-Path $Dist "version.json"), $json + "`n", $Utf8)

# La tienda de complementos: cada complemento.js de la carpeta complementos\ del
# proyecto, en dist\complementos\<id>.js, y su catalogo.json (lo lee la app)
$DistComplementos = Join-Path $Dist "complementos"
New-Item -ItemType Directory -Force $DistComplementos | Out-Null
$catalogo = @()
foreach ($d in Get-ChildItem (Join-Path $Raiz "complementos") -Directory -ErrorAction SilentlyContinue) {
	$js = Join-Path $d.FullName "complemento.js"
	if (-not (Test-Path $js)) { continue }
	$codigo = [IO.File]::ReadAllText($js, $Utf8)
	$bloque = [regex]::Match($codigo, '(?s)export\s+const\s+manifest\s*=\s*\{(.*?)\};').Groups[1].Value
	if (-not $bloque) { Write-Host "[AVISO] $($d.Name) no tiene manifest: no se publica."; continue }
	function Campo($n) { [regex]::Match($bloque, "$n\s*:\s*""([^""]*)""").Groups[1].Value }
	function Lista($n) {
		$l = [regex]::Match($bloque, "$n\s*:\s*\[([^\]]*)\]").Groups[1].Value
		, @([regex]::Matches($l, '"([^"]*)"') | ForEach-Object { $_.Groups[1].Value })
	}
	$id = Campo "id"
	if ($id -notmatch '^[a-z0-9][a-z0-9._-]{1,63}$') { Write-Host "[AVISO] $($d.Name): id no valido, no se publica."; continue }
	$archivoJs = "$id.js"
	$destinoJs = Join-Path $DistComplementos $archivoJs
	Copy-Item -Force $js $destinoJs
	$catalogo += [ordered]@{
		id = $id
		nombre = Campo "nombre"
		version = Campo "version"
		autor = Campo "autor"
		descripcion = Campo "descripcion"
		nombre_en = Campo "nombre_en"
		descripcion_en = Campo "descripcion_en"
		apps = Lista "apps"
		permisos = Lista "permisos"
		apiMinima = $(if ($bloque -match 'apiMinima\s*:\s*(\d+)') { [int]$Matches[1] } else { 1 })
		archivo = $archivoJs
		sha256 = (Get-FileHash -Algorithm SHA256 $destinoJs).Hash.ToLower()
		tamano = (Get-Item $destinoJs).Length
	}
}
# Los temas de sonidos: cada carpeta temas\<id>\ (tema.json y un archivo por
# sonido) se publica comprimida en dist\complementos\<id>.zip, en el mismo catalogo
$temasCatalogo = @()
foreach ($d in Get-ChildItem (Join-Path $Raiz "temas") -Directory -ErrorAction SilentlyContinue) {
	$infoTema = Join-Path $d.FullName "tema.json"
	if (-not (Test-Path $infoTema)) { continue }
	$t = [IO.File]::ReadAllText($infoTema, $Utf8) | ConvertFrom-Json
	if ($t.id -notmatch '^[a-z0-9][a-z0-9._-]{1,63}$') { Write-Host "[AVISO] Tema $($d.Name): id no valido, no se publica."; continue }
	$zip = Join-Path $DistComplementos "$($t.id).zip"
	if (Test-Path $zip) { Remove-Item -Force $zip }
	$sonidosTema = @(Get-ChildItem $d.FullName -File | Where-Object { $_.Extension -in ".wav", ".ogg", ".mp3" })
	if ($sonidosTema.Count -gt 0) { Compress-Archive -Path $sonidosTema.FullName -DestinationPath $zip }
	if (-not (Test-Path $zip)) { Write-Host "[AVISO] Tema $($t.id): sin sonidos, no se publica."; continue }
	$temasCatalogo += [ordered]@{
		id = $t.id
		nombre = $t.nombre
		descripcion = $t.descripcion
		nombre_en = $t.nombre_en
		descripcion_en = $t.descripcion_en
		autor = $t.autor
		archivo = "$($t.id).zip"
		sha256 = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower()
		tamano = (Get-Item $zip).Length
	}
}
$jsonCatalogo = [ordered]@{ formato = 1; complementos = $catalogo; temas = $temasCatalogo } | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $DistComplementos "catalogo.json"), $jsonCatalogo + "`n", $Utf8)
# La misma tienda en la carpeta tienda\ del repositorio: es el respaldo en GitHub
# (raw.githubusercontent.com/<repo>/main/tienda) cuando se active
$Tienda = Join-Path $Raiz "tienda"
New-Item -ItemType Directory -Force $Tienda | Out-Null
Get-ChildItem $Tienda -File -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -Force (Join-Path $DistComplementos "*") $Tienda

Write-Host "[OK] Actualizacion $nombre lista en $Dist :" -ForegroundColor Green
foreach ($p in $Procesadores) { Write-Host ("     {0} ({1:N1} MB)" -f $abis[$p].file, ($abis[$p].size / 1MB)) }
if ($archivoNano) { Write-Host "     $archivoNano" }
Write-Host "     version.json"
Write-Host "     complementos\catalogo.json ($($catalogo.Count) complementos, $($temasCatalogo.Count) temas)"
if ($Servidor -and (Test-Path (Split-Path -Parent $Servidor))) {
	New-Item -ItemType Directory -Force $Servidor | Out-Null
	# La tienda: primero los .js y al final su catálogo
	$ServidorComplementos = Join-Path $Servidor "complementos"
	New-Item -ItemType Directory -Force $ServidorComplementos | Out-Null
	foreach ($c in $catalogo) { Copy-Item -Force (Join-Path $DistComplementos $c.archivo) (Join-Path $ServidorComplementos $c.archivo) }
	foreach ($t in $temasCatalogo) { Copy-Item -Force (Join-Path $DistComplementos $t.archivo) (Join-Path $ServidorComplementos $t.archivo) }
	Copy-Item -Force (Join-Path $DistComplementos "catalogo.json") (Join-Path $ServidorComplementos "catalogo.json")
	foreach ($p in $Procesadores) { Copy-Item -Force (Join-Path $Dist $abis[$p].file) (Join-Path $Servidor $abis[$p].file) }
	if ($archivoNano) { Copy-Item -Force (Join-Path $Dist $archivoNano) (Join-Path $Servidor $archivoNano) }
	Copy-Item -Force (Join-Path $Dist "index.html") (Join-Path $Servidor "index.html")
	# version.json al final: asi nadie ve una version cuyo APK aun no esta subido
	Copy-Item -Force (Join-Path $Dist "version.json") (Join-Path $Servidor "version.json")
	Write-Host "[OK] Copiados a $Servidor (Synology Drive los sube al servidor)." -ForegroundColor Green
} else {
	Write-Host "     Sube los archivos a la carpeta del servidor de actualizaciones."
}
