# Pasa las pruebas de cada complemento de la tienda en Node (sin emulador): una API
# «tiresias» falsa que apunta lo que piden. Las de verdad, con QuickJS, son ComplementosTest.
# Uso: powershell -File herramientas\banco-complementos\probar.ps1
$aqui = $PSScriptRoot
$raiz = Split-Path -Parent (Split-Path -Parent $aqui)
$codigo = 0
foreach ($p in Get-ChildItem $aqui -Filter "*.mjs" | Where-Object { $_.Name -ne "banco.mjs" }) {
	$id = $p.BaseName
	Write-Host "== $id"
	node (Join-Path $aqui "banco.mjs") (Join-Path $raiz "complementos\$id\complemento.js") $p.FullName
	if ($LASTEXITCODE -ne 0) { $codigo = 1 }
}
exit $codigo
