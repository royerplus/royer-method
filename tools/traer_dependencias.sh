#!/usr/bin/env bash
# Lo mismo que traer_dependencias.ps1, para Linux: es lo que usa la
# compilacion de GitHub. Descarga ncnn para arm64 y los pesos de RIFE v4.6 y
# waifu2x cunet, en las mismas versiones fijadas. Ver ese script para el
# porque de cada una. Los .param que ya estan en el repo no se pisan: son los
# mismos, solo cambian los saltos de linea.
#
#   bash tools/traer_dependencias.sh

set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
tmp="${TMPDIR:-/tmp}/royer-deps"
mkdir -p "$tmp"

NCNN=20260526
W2X=20250915

descargar() {
    if [ -f "$2" ]; then echo "ya esta: $(basename "$2")"; return; fi
    echo "descargando $(basename "$2")..."
    curl -fsSL --retry 3 --max-time 900 -o "$2.parte" "$1"
    mv "$2.parte" "$2"
}

# --- ncnn, solo arm64 ---
descargar "https://github.com/Tencent/ncnn/releases/download/$NCNN/ncnn-$NCNN-android-vulkan.zip" "$tmp/ncnn.zip"
rm -rf "$tmp/ncnn"
unzip -q "$tmp/ncnn.zip" "ncnn-*/arm64-v8a/*" -d "$tmp/ncnn"
dst="$raiz/app/src/main/cpp/ncnn"
mkdir -p "$dst"
cp -R "$tmp"/ncnn/ncnn-*/arm64-v8a/. "$dst/"
echo "ncnn: $(find "$dst" -type f | wc -l) archivos"

# --- modelo de interpolacion (RIFE v4.6) ---
descargar "https://github.com/nihui/rife-ncnn-vulkan/releases/download/20221029/rife-ncnn-vulkan-20221029-windows.zip" "$tmp/rife.zip"
mkdir -p "$raiz/app/src/main/assets/rife-v4.6"
unzip -q -n -j "$tmp/rife.zip" "*/rife-v4.6/*" -d "$raiz/app/src/main/assets/rife-v4.6"
echo "rife-v4.6 listo"

# --- modelos de reconstruccion (waifu2x cunet) ---
descargar "https://github.com/nihui/waifu2x-ncnn-vulkan/releases/download/$W2X/waifu2x-ncnn-vulkan-$W2X-windows.zip" "$tmp/w2x.zip"
mkdir -p "$raiz/app/src/main/assets/waifu2x"
unzip -q -n -j "$tmp/w2x.zip" "*models-cunet/*scale2.0x_model.*" -d "$raiz/app/src/main/assets/waifu2x"
echo "waifu2x cunet listo"
