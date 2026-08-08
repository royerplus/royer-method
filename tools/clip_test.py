# -*- coding: utf-8 -*-
"""
Tres versiones del mismo clip para comparar fluidez a ojo.

Un clip suelto es la superficie correcta para esta prueba: no tiene cortes,
asi que la interpolacion no tiene ningun sitio donde equivocarse. Todo lo
que se vea es merito o culpa del metodo, no del material.

  A  original recodificado    24 fps        el punto de partida
  B  interpolado              60 fps        mas frames, sin desenfoque
  C  interpolado y mezclado   30 fps        el truco: subir y bajar

A se recodifica con los mismos ajustes que B y C aunque no haga falta. Si
se dejara el original tal cual, parte de la diferencia vendria del
codificador y no del metodo, y la comparacion no valdria.
"""
import os, shutil, subprocess, time

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\clip_test"
RIFE = r"C:\Users\Admin\AppData\Local\rife\rife-ncnn-vulkan-20221029-windows\rife-ncnn-vulkan.exe"
FF = (r"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages"
      r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
      r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

ENTRADA = os.path.join(BASE, "Generate_live_wallpaper_animation_202608021702.mp4")
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

    print("[1] extrayendo frames...", flush=True)
    sh([FF, "-y", "-v", "error", "-i", ENTRADA, os.path.join(src, "%05d.png")])
    n = len(os.listdir(src))
    print("    %d frames" % n, flush=True)

    # 24 -> 120 fps son 5 frames de salida por cada uno de entrada
    print("[2] RIFE a 120 fps...", flush=True)
    t0 = time.time()
    r = sh([RIFE, "-i", src, "-o", den, "-n", str(n * 5), "-m", "rife-v4.6"])
    if r.returncode != 0:
        print(r.stderr[-500:], flush=True)
        return
    print("    %d frames en %.0f s" % (len(os.listdir(den)), time.time() - t0), flush=True)

    print("[3] montando las tres versiones...", flush=True)
    patron = os.path.join(den, "%08d.png")

    sh([FF, "-y", "-v", "error", "-i", ENTRADA] + X264 +
       [os.path.join(BASE, "CLIP_A_original_24fps.mp4")])

    sh([FF, "-y", "-v", "error", "-framerate", "120", "-i", patron,
        "-vf", "fps=60"] + X264 + [os.path.join(BASE, "CLIP_B_60fps.mp4")])

    # tmix=2 sobre 120 fps promedia dos imagenes por cada salida: obturador
    # de 180 grados, el estandar de cine
    sh([FF, "-y", "-v", "error", "-framerate", "120", "-i", patron,
        "-vf", "tmix=frames=2,fps=30"] + X264 +
       [os.path.join(BASE, "CLIP_C_30fps_desenfoque.mp4")])

    for f in sorted(os.listdir(BASE)):
        if f.startswith("CLIP_") and f.endswith(".mp4"):
            print("    %-34s %.2f MB" % (f, os.path.getsize(os.path.join(BASE, f)) / 1048576.0), flush=True)
    print("LISTO", flush=True)


if __name__ == "__main__":
    main()
