# -*- coding: utf-8 -*-
"""
El pipeline completo sobre el archivo crudo, que es el caso de uso real.

Todo lo que probamos antes partia de videos ya destrozados por TikTok. Este
parte del export de CapCut, que es lo que la app va a recibir de verdad.

Orden de los pasos, y por que ese orden:

  1. Bajar resolucion PRIMERO. Es lo que multiplica los bits por pixel, y
     ademas hace que RIFE trabaje sobre una cuarta parte de los pixeles.
  2. Realzar justo despues. Al reducir siempre se pierde definicion en el
     filo; el realce la devuelve. Va aqui y no al final porque despues viene
     el desenfoque de movimiento, y afilar un borron no tiene sentido.
     Se usa cas (realce adaptativo al contraste) en vez de unsharp porque
     no genera halos alrededor de la linea negra.
  3. Rellenar duplicados en su posicion temporal exacta, si los hay.
  4. Interpolar a 120 y mezclar de vuelta a 30: de ahi sale el desenfoque.
  5. Codificar con x264 y frames B, que es lo que le faltaba al original
     (venia del codificador por hardware del movil, con cero frames B).
"""
import os, shutil, subprocess, sys, time
from PIL import Image, ImageChops

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\rife_crudo"
RIFE = r"C:\Users\Admin\AppData\Local\rife\rife-ncnn-vulkan-20221029-windows\rife-ncnn-vulkan.exe"
FF = (r"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages"
      r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
      r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

ENTRADA = os.path.join(BASE, "lv_0_20260317212625.mp4")
SALIDA = os.path.join(BASE, "SALIDA_crudo_procesado.mp4")

ANCHO = 576          # 1080 -> 576, el mismo ancho que sus referencias god
FPS = 30
MULT = 4
MODELO = "rife-v4.6"
UMBRAL_DUP = 1.2


def sh(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def limpiar(d):
    if os.path.isdir(d):
        shutil.rmtree(d)
    os.makedirs(d, exist_ok=True)


def diferencia(a, b):
    ga = a.convert("L").resize((96, 96))
    gb = b.convert("L").resize((96, 96))
    h = ImageChops.difference(ga, gb).histogram()
    return sum(i * n for i, n in enumerate(h)) / float(96 * 96)


def main():
    t0 = time.time()
    chicos = os.path.join(TRABAJO, "chicos")
    limpios = os.path.join(TRABAJO, "limpios")
    densos = os.path.join(TRABAJO, "densos")
    for d in (chicos, limpios, densos):
        limpiar(d)

    # ---- 1 y 2: reducir y realzar en la misma pasada ----
    print("[1/5] reduciendo a %dpx y realzando..." % ANCHO, flush=True)
    r = sh([FF, "-y", "-v", "error", "-i", ENTRADA,
            "-vf", "scale=%d:-2:flags=lanczos,cas=strength=0.5" % ANCHO,
            os.path.join(chicos, "%06d.png")])
    if r.returncode != 0:
        print(r.stderr[-600:]); sys.exit(1)
    nombres = sorted(os.listdir(chicos))
    n = len(nombres)
    rutas = [os.path.join(chicos, x) for x in nombres]
    print("      %d frames" % n, flush=True)

    # ---- 3: duplicados ----
    print("[2/5] buscando duplicados...", flush=True)
    es_dup = [False] * n
    previa = Image.open(rutas[0])
    for i in range(1, n):
        act = Image.open(rutas[i])
        if diferencia(previa, act) < UMBRAL_DUP:
            es_dup[i] = True
        else:
            previa = act
    total = sum(es_dup)
    print("      %d de %d  (%.1f%%)" % (total, n, 100.0 * total / n), flush=True)

    for i, nom in enumerate(nombres):
        shutil.copy(rutas[i], os.path.join(limpios, nom))

    if total:
        print("[3/5] regenerando duplicados...", flush=True)
        reales = [i for i in range(n) if not es_dup[i]]
        hechos = 0
        for p in range(len(reales) - 1):
            a, b = reales[p], reales[p + 1]
            if b - a <= 1:
                continue
            for k in range(a + 1, b):
                sh([RIFE, "-0", rutas[a], "-1", rutas[b],
                    "-o", os.path.join(limpios, nombres[k]),
                    "-m", MODELO, "-s", "%.6f" % ((k - a) / float(b - a))])
                hechos += 1
        print("      %d regenerados" % hechos, flush=True)
    else:
        print("[3/5] sin duplicados que arreglar", flush=True)

    # ---- 4: a 120 fps ----
    print("[4/5] interpolando a %d fps... (lo lento)" % (FPS * MULT), flush=True)
    r = sh([RIFE, "-i", limpios, "-o", densos, "-n", str(n * MULT), "-m", MODELO])
    if r.returncode != 0:
        print(r.stderr[-600:]); sys.exit(1)
    print("      %d frames  (%.0f s)" % (len(os.listdir(densos)), time.time() - t0), flush=True)

    # ---- 5: mezclar, codificar, pegar el audio ----
    print("[5/5] mezclando y codificando...", flush=True)
    sh([FF, "-y", "-v", "error",
        "-framerate", str(FPS * MULT), "-i", os.path.join(densos, "%08d.png"),
        "-i", ENTRADA,
        "-filter_complex", "[0:v]tmix=frames=2,fps=%d[v]" % FPS,
        "-map", "[v]", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "slow", "-tune", "animation",
        "-crf", "21", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart", "-shortest", SALIDA])

    mb = os.path.getsize(SALIDA) / 1048576.0
    orig = os.path.getsize(ENTRADA) / 1048576.0
    print("LISTO en %.0f s" % (time.time() - t0), flush=True)
    print("  %.1f MB  ->  %.1f MB   (%.1fx mas ligero)" % (orig, mb, orig / mb), flush=True)


if __name__ == "__main__":
    main()
