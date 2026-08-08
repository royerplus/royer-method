# -*- coding: utf-8 -*-
"""
Pipeline de fluidez con RIFE, conservando la sincronia con la musica.

El problema que resuelve
------------------------
El video dice 30 fps y entrega los frames puntuales, pero uno de cada cinco
es una copia del anterior, y NO estan repartidos: los primeros 10 segundos
casi no tienen y el ultimo tercio esta lleno. Si se quitan los duplicados y
se re-temporiza a un ritmo uniforme, cada frame se corre de sitio y el edit
se despega de la musica (llegue a medir 1.27 s de deriva haciendolo asi).

La solucion
-----------
No se toca la linea de tiempo en ningun momento. Los 737 frames siguen
siendo 737 frames a 30 fps; lo unico que cambia es QUE hay dentro de los que
eran copias. Cada duplicado se sustituye por un frame que RIFE calcula en su
posicion temporal exacta entre los dos frames reales que lo rodean.

Un duplicado a mitad de camino entre dos frames reales pide time-step 0.5;
si hay tres copias seguidas, piden 0.25, 0.5 y 0.75. Por eso hace falta
rife-v4.6: es el unico modelo que acepta posiciones arbitrarias.

Despues, ya con la secuencia limpia y uniforme, se interpola a 120 fps y se
mezcla de vuelta a 30 para generar el desenfoque de movimiento.
"""
import os, re, shutil, subprocess, sys, time
from PIL import Image, ImageChops

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\rife_trabajo"
RIFE = r"C:\Users\Admin\AppData\Local\rife\rife-ncnn-vulkan-20221029-windows\rife-ncnn-vulkan.exe"
FF = (r"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages"
      r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
      r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

ENTRADA = os.path.join(BASE, "1dd58cdc34bbe1072b211d3d3e8442a1 (1).mp4")
SALIDA = os.path.join(BASE, "SALIDA_rife.mp4")

FPS = 30
MULT = 4               # 30 -> 120 fps
MODELO = "rife-v4.6"   # el unico que acepta -s (time step)
UMBRAL_DUP = 1.2       # diferencia media por pixel por debajo de la cual
                       # dos frames se consideran el mismo


def sh(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def limpiar(d):
    if os.path.isdir(d):
        shutil.rmtree(d)
    os.makedirs(d, exist_ok=True)


def diferencia(a, b):
    """Diferencia media por pixel entre dos imagenes, en escala de grises
    y reducidas: nos da igual el ruido de compresion, solo si hay movimiento."""
    ga = a.convert("L").resize((96, 96))
    gb = b.convert("L").resize((96, 96))
    h = ImageChops.difference(ga, gb).histogram()
    total = sum(i * n for i, n in enumerate(h))
    return total / float(96 * 96)


def main():
    t_ini = time.time()
    crudos = os.path.join(TRABAJO, "crudos")
    limpios = os.path.join(TRABAJO, "limpios")
    densos = os.path.join(TRABAJO, "densos")
    for d in (crudos, limpios, densos):
        limpiar(d)

    # ---- 1. sacar todos los frames tal cual ----
    print("[1/5] extrayendo frames...", flush=True)
    sh([FF, "-y", "-v", "error", "-i", ENTRADA,
        os.path.join(crudos, "%06d.png")])
    nombres = sorted(os.listdir(crudos))
    n = len(nombres)
    print("      %d frames" % n, flush=True)

    # ---- 2. localizar los duplicados ----
    print("[2/5] buscando duplicados...", flush=True)
    rutas = [os.path.join(crudos, x) for x in nombres]
    es_dup = [False] * n
    previa = Image.open(rutas[0])
    for i in range(1, n):
        act = Image.open(rutas[i])
        if diferencia(previa, act) < UMBRAL_DUP:
            es_dup[i] = True
        else:
            previa = act
    total_dup = sum(es_dup)
    print("      %d duplicados de %d  (%.1f%%)" % (total_dup, n, 100.0 * total_dup / n), flush=True)

    # ---- 3. sustituir cada duplicado por un frame calculado en su sitio ----
    print("[3/5] regenerando duplicados con RIFE...", flush=True)
    reales = [i for i in range(n) if not es_dup[i]]
    for i, nom in enumerate(nombres):
        shutil.copy(rutas[i], os.path.join(limpios, nom))

    hechos = 0
    for pos in range(len(reales) - 1):
        a, b = reales[pos], reales[pos + 1]
        if b - a <= 1:
            continue                      # no hay hueco que rellenar
        for k in range(a + 1, b):
            paso = (k - a) / float(b - a)
            r = sh([RIFE, "-0", rutas[a], "-1", rutas[b],
                    "-o", os.path.join(limpios, nombres[k]),
                    "-m", MODELO, "-s", "%.6f" % paso])
            if r.returncode != 0:
                print("      fallo en el frame %d: %s" % (k, r.stderr.strip()[:120]), flush=True)
            hechos += 1
            if hechos % 25 == 0:
                print("      %d/%d  (%.0f s)" % (hechos, total_dup, time.time() - t_ini), flush=True)
    print("      %d frames regenerados" % hechos, flush=True)

    # ---- 4. de 30 a 120 fps, ya con la secuencia uniforme ----
    print("[4/5] interpolando a %d fps..." % (FPS * MULT), flush=True)
    r = sh([RIFE, "-i", limpios, "-o", densos,
            "-n", str(n * MULT), "-m", MODELO])
    if r.returncode != 0:
        print(r.stderr[-500:], flush=True)
        sys.exit(1)
    print("      %d frames" % len(os.listdir(densos)), flush=True)

    # ---- 5. mezclar a 30 fps y codificar ----
    # tmix promedia cada frame con el anterior: a 120 fps eso equivale a un
    # obturador de 180 grados, el estandar de cine. fps=30 se queda con uno
    # de cada cuatro.
    print("[5/5] mezclando y codificando...", flush=True)
    sh([FF, "-y", "-v", "error",
        "-framerate", str(FPS * MULT), "-i", os.path.join(densos, "%08d.png"),
        "-i", ENTRADA,
        "-filter_complex", "[0:v]tmix=frames=2,fps=%d[v]" % FPS,
        "-map", "[v]", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "slow", "-tune", "animation",
        "-crf", "20", "-pix_fmt", "yuv420p", "-c:a", "copy",
        "-shortest", SALIDA])

    print("LISTO en %.0f s  ->  %s" % (time.time() - t_ini, SALIDA), flush=True)


if __name__ == "__main__":
    main()
