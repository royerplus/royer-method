# -*- coding: utf-8 -*-
"""
Version ultrafina: 480 fps interpolados, promediados a 30.

La serie va doblando la resolucion temporal cada vez, para ver donde deja
de notarse:

  obturador_270    120 fps -> promedia  3 de  4     (el primero que elegiste)
  30fps_fino       240 fps -> promedia  8 de  8     (el segundo)
  ultrafino        480 fps -> promedia 16 de 16     (este)

Todas salen a 30 fps. Lo unico que cambia es de cuantas posiciones
intermedias se construye el arrastre de cada frame. Mas posiciones = el
rastro es continuo en vez de escalonado.

Son 20 frames generados por cada uno del original, asi que tarda el doble
que la anterior y ocupa unos 3.5 GB de temporales mientras trabaja.
"""
import os, shutil, subprocess, time

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\clip_ultra"
RIFE = r"C:\Users\Admin\AppData\Local\rife\rife-ncnn-vulkan-20221029-windows\rife-ncnn-vulkan.exe"
FF = (r"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages"
      r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
      r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

ENTRADA = os.path.join(BASE, "Generate_live_wallpaper_animation_202608021702.mp4")
SALIDA = os.path.join(BASE, "EST_30fps_ultrafino.mp4")
X264 = ["-c:v", "libx264", "-preset", "slow", "-tune", "animation",
        "-crf", "20", "-pix_fmt", "yuv420p", "-an"]


def sh(c):
    return subprocess.run(c, capture_output=True, text=True)


def main():
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

    print("[2] RIFE a 480 fps (%d frames)..." % (n * 20), flush=True)
    r = sh([RIFE, "-i", src, "-o", den, "-n", str(n * 20), "-m", "rife-v4.6"])
    if r.returncode != 0:
        print(r.stderr[-500:], flush=True)
        return
    print("    %d frames en %.0f s" % (len(os.listdir(den)), time.time() - t0), flush=True)

    print("[3] promediando 16 en 1...", flush=True)
    sh([FF, "-y", "-v", "error", "-framerate", "480",
        "-i", os.path.join(den, "%08d.png"),
        "-vf", "tmix=frames=16,fps=30"] + X264 + [SALIDA])

    print("LISTO en %.0f s  ·  %.2f MB" % (time.time() - t0,
          os.path.getsize(SALIDA) / 1048576.0), flush=True)
    shutil.rmtree(TRABAJO, ignore_errors=True)


if __name__ == "__main__":
    main()
