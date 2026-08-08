# -*- coding: utf-8 -*-
"""
Pipeline completo sobre un edit real, con cortes.

Diferencia con el clip suelto: aqui hay cambios de plano. RIFE no los
entiende y trata un corte como si fuera movimiento, asi que inventa frames
derretidos entre dos imagenes que no tienen nada que ver.

La solucion no necesita detectar escenas ni entender nada. Es la MISMA
medida de diferencia entre frames consecutivos, leida con dos umbrales:

    diferencia < 1.2   los frames son iguales -> es un duplicado, hay que
                       rellenarlo con un frame calculado en su posicion
    diferencia > 28    los frames no tienen nada que ver -> es un corte,
                       hay que respetarlo y no inventar nada en medio
    en medio           movimiento normal, se interpola

Una sola comparacion, dos umbrales, cero comprension de la escena. Es lo
unico que puede funcionar de forma general.

Genera dos salidas:
    _1_reducido   la imagen ya trabajada, con calidad alta y sin apretar
    _2_final      la misma, comprimida para subir
"""
import os, re, shutil, subprocess, sys, time
from PIL import Image, ImageChops

BASE = r"C:\Users\Admin\Documents\iihjfasihu"
TRABAJO = r"C:\Users\Admin\AppData\Local\Temp\edit_full"
RIFE = r"C:\Users\Admin\AppData\Local\rife\rife-ncnn-vulkan-20221029-windows\rife-ncnn-vulkan.exe"
FF = (r"C:\Users\Admin\AppData\Local\Microsoft\WinGet\Packages"
      r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
      r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

ENTRADA = os.path.join(BASE, "1dd58cdc34bbe1072b211d3d3e8442a1 (1).mp4")
FPS = 30
MULT = 32                 # 30 -> 960 fps
MODELO = "rife-v4.6"
UMBRAL_DUP = 1.2
UMBRAL_CORTE = 28.0
ANCHO = 432

TAG = "setparams=color_primaries=bt709:color_trc=bt709:colorspace=bt709"
PAR = "aq-mode=3:aq-strength=0.8:psy-rd=0.6,0.15:deblock=-1,-1:bframes=8:ref=6"


def sh(c):
    return subprocess.run(c, capture_output=True, text=True)


def dif(a, b):
    ga = a.convert("L").resize((96, 96))
    gb = b.convert("L").resize((96, 96))
    h = ImageChops.difference(ga, gb).histogram()
    return sum(i * n for i, n in enumerate(h)) / float(96 * 96)


def main():
    t0 = time.time()
    src = os.path.join(TRABAJO, "src")
    lim = os.path.join(TRABAJO, "lim")
    den = os.path.join(TRABAJO, "den")
    for d in (src, lim, den):
        if os.path.isdir(d):
            shutil.rmtree(d)
        os.makedirs(d)

    print("[1] extrayendo...", flush=True)
    sh([FF, "-y", "-v", "error", "-i", ENTRADA, os.path.join(src, "%06d.png")])
    nom = sorted(os.listdir(src))
    n = len(nom)
    rut = [os.path.join(src, x) for x in nom]
    print("    %d frames" % n, flush=True)

    print("[2] clasificando cada pareja...", flush=True)
    difs = [0.0] * n
    prev = Image.open(rut[0])
    for i in range(1, n):
        act = Image.open(rut[i])
        difs[i] = dif(prev, act)
        if difs[i] >= UMBRAL_DUP:
            prev = act
    dups = [i for i in range(1, n) if difs[i] < UMBRAL_DUP]

    # Los cortes NO salen del mismo umbral. Al medir la distribucion se ve
    # que entre movimiento rapido y cambio de plano no hay separacion: los
    # valores van de 4 a 135 en continuo, sin ningun hueco donde cortar.
    # Con umbral 40 salian 91 cortes y con 80 salian 14, y nada en los datos
    # dice cual es el bueno. Un umbral a mano falla siempre en la frontera.
    # El detector de ffmpeg lleva anos calibrado para esto; sigue siendo una
    # regla general aplicada igual a todo, no un tratamiento por escena.
    cortes = set()
    r = sh([FF, "-i", ENTRADA, "-vf",
            "select='gt(scene,0.35)',metadata=print:file=-", "-f", "null", "-"])
    for m in re.finditer(r"pts_time:([\d.]+)", (r.stdout or "") + (r.stderr or "")):
        cortes.add(int(round(float(m.group(1)) * FPS)))
    print("    %d duplicados  ·  %d cortes" % (len(dups), len(cortes)), flush=True)

    for i, x in enumerate(nom):
        shutil.copy(rut[i], os.path.join(lim, x))

    print("[3] rellenando duplicados...", flush=True)
    reales = [i for i in range(n) if i not in dups]
    hechos = 0
    for p in range(len(reales) - 1):
        a, b = reales[p], reales[p + 1]
        if b - a <= 1:
            continue
        if any(k in cortes for k in range(a + 1, b + 1)):
            continue          # hay un corte dentro: no se inventa nada
        for k in range(a + 1, b):
            sh([RIFE, "-0", rut[a], "-1", rut[b], "-o", os.path.join(lim, nom[k]),
                "-m", MODELO, "-s", "%.6f" % ((k - a) / float(b - a))])
            hechos += 1
    # los duplicados del final no tienen frame real detras: se quedan igual
    print("    %d rellenados de %d" % (hechos, len(dups)), flush=True)

    print("[4] interpolando a %d fps (%d frames)..." % (FPS * MULT, n * MULT), flush=True)
    r = sh([RIFE, "-i", lim, "-o", den, "-n", str(n * MULT), "-m", MODELO])
    if r.returncode != 0:
        print(r.stderr[-400:], flush=True)
        sys.exit(1)
    gen = sorted(os.listdir(den))
    print("    %d frames  (%.0f s)" % (len(gen), time.time() - t0), flush=True)

    # [5] arreglar los cortes: RIFE ha inventado transiciones donde habia
    # cambios de plano. Se sustituyen por un corte seco.
    print("[5] reparando %d cortes..." % len(cortes), flush=True)
    reparados = 0
    for c in cortes:
        ini = (c - 1) * MULT
        for j in range(1, MULT):
            idx = ini + j
            if idx >= len(gen):
                break
            # primera mitad del hueco se queda con el plano viejo, la
            # segunda con el nuevo. Nada de mezcla.
            origen = os.path.join(lim, nom[c - 1] if j < MULT // 2 else nom[c])
            shutil.copy(origen, os.path.join(den, gen[idx]))
            reparados += 1
    print("    %d frames sustituidos" % reparados, flush=True)

    print("[6] montando las dos salidas...", flush=True)
    alto = int(round(ANCHO * 1.0 / 2)) * 2      # el edit es cuadrado
    vf = (TAG + ",zscale=t=linear:npl=100,format=gbrpf32le"
          ",zscale=w=%d:h=%d:f=lanczos,zscale=t=bt709,format=yuv420p"
          ",cas=strength=0.4" % (ANCHO, alto))
    patron = os.path.join(den, "%08d.png")
    mezcla = "[0:v]tmix=frames=%d,fps=%d,%s[v]" % (MULT, FPS, vf)

    # 1) calidad alta, sin apretar
    sh([FF, "-y", "-v", "error", "-framerate", str(FPS * MULT), "-i", patron,
        "-i", ENTRADA, "-filter_complex", mezcla, "-map", "[v]", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "veryslow", "-tune", "animation",
        "-crf", "16", "-x264-params", PAR, "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "160k", "-shortest",
        os.path.join(BASE, "EDIT_1_reducido.mp4")])

    # 2) comprimido para subir
    sh([FF, "-y", "-v", "error", "-framerate", str(FPS * MULT), "-i", patron,
        "-i", ENTRADA, "-filter_complex", mezcla, "-map", "[v]", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "veryslow", "-tune", "animation",
        "-crf", "20", "-x264-params", PAR, "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-shortest",
        os.path.join(BASE, "EDIT_2_final.mp4")])

    for f in ("EDIT_1_reducido.mp4", "EDIT_2_final.mp4"):
        p = os.path.join(BASE, f)
        if os.path.exists(p):
            print("    %-24s %.2f MB" % (f, os.path.getsize(p) / 1048576.0), flush=True)
    print("LISTO en %.0f s" % (time.time() - t0), flush=True)
    shutil.rmtree(TRABAJO, ignore_errors=True)


if __name__ == "__main__":
    main()
