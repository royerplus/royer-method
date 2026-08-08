# -*- coding: utf-8 -*-
"""
Ataca la inestabilidad del movimiento, no la falta de fluidez.

El problema
-----------
El clip es de 24 fps y lo sacabamos a 30. Esa relacion es 4:5, asi que uno
de cada cinco frames de salida cae justo encima de un frame original y los
otros cuatro son generados. Los originales se ven nitidos, los generados
ligeramente distintos, y el patron se repite en ciclo de cinco todo el rato.
Eso es lo que se percibe como "30 fps que no son estables".

Las tres salidas
----------------
  24fps   sale al ritmo nativo. Sin conversion no hay ciclo: todos los
          frames de salida coinciden con uno original. Es la unica que
          elimina la causa en vez de disimularla.

  30fps   misma conversion de antes pero con desenfoque calculado desde
          240 fps en vez de 120. El arrastre pasa de 4 posiciones a 8, o
          sea continuo en vez de escalonado. Disimula mejor el ciclo.

  30des   30 fps desplazados medio paso. Asi NINGUN frame de salida cae
          sobre un original: todos son generados, todos tienen la misma
          calidad, y el ciclo desaparece aunque siga habiendo conversion.
"""
import os, shutil, subprocess, time

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\clip_estable"
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

    print("[1] extrayendo...", flush=True)
    sh([FF, "-y", "-v", "error", "-i", ENTRADA, os.path.join(src, "%05d.png")])
    n = len(os.listdir(src))
    print("    %d frames a 24 fps" % n, flush=True)

    # 24 -> 240 son 10 frames de salida por cada uno de entrada
    print("[2] RIFE a 240 fps (el doble de fino que antes)...", flush=True)
    t0 = time.time()
    r = sh([RIFE, "-i", src, "-o", den, "-n", str(n * 10), "-m", "rife-v4.6"])
    if r.returncode != 0:
        print(r.stderr[-500:], flush=True)
        return
    print("    %d frames en %.0f s" % (len(os.listdir(den)), time.time() - t0), flush=True)

    patron = os.path.join(den, "%08d.png")
    print("[3] montando...", flush=True)

    # 24 fps nativo, obturador 360: promedia los 10 de cada intervalo
    sh([FF, "-y", "-v", "error", "-framerate", "240", "-i", patron,
        "-vf", "tmix=frames=10,fps=24"] + X264 +
       [os.path.join(BASE, "EST_24fps_nativo.mp4")])

    # 30 fps, obturador 360 desde 240: 8 posiciones por frame de salida
    sh([FF, "-y", "-v", "error", "-framerate", "240", "-i", patron,
        "-vf", "tmix=frames=8,fps=30"] + X264 +
       [os.path.join(BASE, "EST_30fps_fino.mp4")])

    # 30 fps desplazado medio paso: ningun frame coincide con un original.
    # El trim de 4 frames a 240 fps equivale a medio frame de salida a 30.
    sh([FF, "-y", "-v", "error", "-framerate", "240", "-i", patron,
        "-vf", "trim=start_frame=4,setpts=PTS-STARTPTS,tmix=frames=8,fps=30"] + X264 +
       [os.path.join(BASE, "EST_30fps_desplazado.mp4")])

    for f in sorted(os.listdir(BASE)):
        if f.startswith("EST_") and f.endswith(".mp4"):
            print("    %-30s %.2f MB" % (f, os.path.getsize(os.path.join(BASE, f)) / 1048576.0), flush=True)
    print("LISTO", flush=True)


if __name__ == "__main__":
    main()
