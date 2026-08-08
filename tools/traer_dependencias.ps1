# Trae las dependencias nativas que no van en el repositorio.
#
# Son unos 40 MB de binarios de terceros: ncnn precompilado para Android con
# Vulkan, y los pesos de los dos modelos. Se descargan aqui en vez de guardarse
# en el repositorio para que este no engorde con cosas que no son nuestras y
# que ademas tienen su propia procedencia y licencia.
#
# Las versiones estan fijadas a proposito. ncnn cambia su interfaz cada pocas
# versiones, y el codigo de rife que llevamos ya viene parcheado para esta.
#
#   powershell -ExecutionPolicy Bypass -File tools\traer_dependencias.ps1

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$raiz = Split-Path -Parent $PSScriptRoot
$tmp = Join-Path $env:TEMP "royer-deps"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

$NCNN = "20260526"
$W2X = "20250915"

function Descargar($url, $destino) {
    if (Test-Path $destino) {
        Write-Host "ya esta: $(Split-Path -Leaf $destino)"
        return
    }
    Write-Host "descargando $(Split-Path -Leaf $destino)..."
    Invoke-WebRequest -Uri $url -OutFile $destino -TimeoutSec 900 -UseBasicParsing
}

# --- ncnn, solo arm64 ---
$zipNcnn = Join-Path $tmp "ncnn.zip"
Descargar "https://github.com/Tencent/ncnn/releases/download/$NCNN/ncnn-$NCNN-android-vulkan.zip" $zipNcnn

$dstNcnn = Join-Path $raiz "app\src\main\cpp\ncnn"
$z = [System.IO.Compression.ZipFile]::OpenRead($zipNcnn)
$n = 0
foreach ($e in $z.Entries) {
    if ($e.FullName -notlike "*arm64-v8a*" -or $e.Length -eq 0) { continue }
    $rel = $e.FullName -replace '^ncnn-[^/]+/arm64-v8a/', ''
    $out = Join-Path $dstNcnn $rel.Replace('/', '\')
    New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $out, $true)
    $n++
}
$z.Dispose()
Write-Host "ncnn: $n archivos"

# --- modelo de interpolacion (RIFE v4.6) ---
#
# Solo la version 4.6: es la unica que admite pedir un instante concreto entre
# dos fotogramas. Las anteriores solo saben dar el punto medio, y encadenar
# puntos medios para llegar a 960 fps acumula error.
$zipRife = Join-Path $tmp "rife.zip"
Descargar "https://github.com/nihui/rife-ncnn-vulkan/releases/download/20221029/rife-ncnn-vulkan-20221029-windows.zip" $zipRife

$dstRife = Join-Path $raiz "app\src\main\assets\rife-v4.6"
New-Item -ItemType Directory -Force -Path $dstRife | Out-Null
$z = [System.IO.Compression.ZipFile]::OpenRead($zipRife)
foreach ($e in $z.Entries) {
    if ($e.FullName -notlike "*rife-v4.6/*" -or $e.Length -eq 0) { continue }
    $out = Join-Path $dstRife ([System.IO.Path]::GetFileName($e.FullName))
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $out, $true)
}
$z.Dispose()
Write-Host "rife-v4.6 listo"

# --- modelos de reconstruccion (waifu2x cunet) ---
$zipW2x = Join-Path $tmp "w2x.zip"
Descargar "https://github.com/nihui/waifu2x-ncnn-vulkan/releases/download/$W2X/waifu2x-ncnn-vulkan-$W2X-windows.zip" $zipW2x

$dstW2x = Join-Path $raiz "app\src\main\assets\waifu2x"
New-Item -ItemType Directory -Force -Path $dstW2x | Out-Null
$z = [System.IO.Compression.ZipFile]::OpenRead($zipW2x)
foreach ($e in $z.Entries) {
    $nombre = [System.IO.Path]::GetFileName($e.FullName)
    if ($e.FullName -notlike "*models-cunet*") { continue }
    if ($nombre -notlike "*scale2.0x_model.*") { continue }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $dstW2x $nombre), $true)
}
$z.Dispose()
Write-Host "waifu2x cunet listo"

Write-Host ""
Write-Host "LISTO. Ahora: gradlew assembleRelease"
