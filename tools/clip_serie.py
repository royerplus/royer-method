# -*- coding: utf-8 -*-
"""
Genera una version de la serie de desenfoque, con el multiplicador que se le pase.

    python clip_serie.py 40

La serie duplica cada vez la resolucion temporal desde la que se construye
el arrastre de cada frame de salida:

    mult   interpolado   promedia    tiempo aprox (RX 580, clip de 8s)
     5      120 fps       3 de  4       50 s
    10      240 fps       8 de  8      111 s
    20      480 fps      16 de 16      236 s
    40      960 fps      32 de 32      475 s

El multiplicador tiene que dar un numero de frames divisible por la tasa de
salida, si no el promediado queda descuadrado. Con 24 fps de origen y 30 de
salida, los multiplos de 5 cumplen.

El peso del archivo final no cambia entre versiones; lo unico que cuesta es
tiempo de proceso y espacio temporal en disco.
"""
import os, shutil, subprocess, sys, time

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\clip_serie"
RIFE = r"C:\Users\Admin\AppData\Local\rife\rife-ncnn-vulkan-20221029-windows\rife-ncnn-vulkan.exe"
FF = (r"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages"
      r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
      r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

ENTRADA = os.path.join(BASE, "Generate_live_wallpaper_animation_202608021702.mp4")
FPS_ORIGEN = 24
FPS_SALIDA = 30
X264 = ["-c:v", "libx264", "-preset", "slow", "-tune", "animation",
        "-crf", "20", "-pix_fmt", "yuv420p", "-an"]


def sh(c):
    return subprocess.run(c, capture_output=True, text=True)


def main():
    mult = int(sys.argv[1]) if len(sys.argv) > 1 else 40
    fps_alto = FPS_ORIGEN * mult
    promedia = fps_alto // FPS_SALIDA
    if fps_alto % FPS_SALIDA:
        print("multiplicador invalido: %d fps no divide entre %d" % (fps_alto, FPS_SALIDA))
        return
    salida = os.path.join(BASE, "EST_30fps_%dfps.mp4" % fps_alto)

    src = os.path.join(TRABAJO, "src")
    den = os.path.join(TRABAJO, "den")
    for d in (src, den):
        if os.path.isdir(d):
            shutil.rmtree(d)
        os.makedirs(d)

    t0 = time.time()
    print("[1] extrayendo...", flush=True)
    sh([FF, "-y", "-v", "error", "-i", ENTRADA, os.path.join(src, "%05d.png")])
    n = len(os.listdir(src))
    print("    %d frames" % n, flush=True)

    total = n * mult
    print("[2] RIFE a %d fps  (%d frames)..." % (fps_alto, total), flush=True)
    r = sh([RIFE, "-i", src, "-o", den, "-n", str(total), "-m", "rife-v4.6"])
    if r.returncode != 0:
        print(r.stderr[-500:], flush=True)
        return
    print("    %d frames en %.0f s" % (len(os.listdir(den)), time.time() - t0), flush=True)

    print("[3] promediando %d en 1..." % promedia, flush=True)
    sh([FF, "-y", "-v", "error", "-framerate", str(fps_alto),
        "-i", os.path.join(den, "%08d.png"),
        "-vf", "tmix=frames=%d,fps=%d" % (promedia, FPS_SALIDA)] + X264 + [salida])

    print("LISTO en %.0f s  ·  %.2f MB  ->  %s" % (
        time.time() - t0, os.path.getsize(salida) / 1048576.0, os.path.basename(salida)), flush=True)
    shutil.rmtree(TRABAJO, ignore_errors=True)


if __name__ == "__main__":
    main()
