# -*- coding: utf-8 -*-
"""
Genera el icono de la app a partir del PNG de diseno, sin redibujarlo.

El truco esta en el encuadre. Un icono adaptativo de Android mide 108x108,
pero el lanzador solo ensena los 72x72 del centro: el resto se lo come la
mascara, sea circulo, squircle o lo que use cada telefono.

Si se pone el diseno ocupando los 108 completos, la mascara le corta las
puntas de las orejas. Asi que el diseno entero se mete dentro de esos 72 y
el borde que sobra se rellena con el color de las esquinas de la propia
imagen, que queda fuera de la mascara y no se ve.
"""
from PIL import Image
import os

ORIGEN = r"C:\Users\Admin\Documents\iihjfasihu\file_000000002ab881fb89ec92217c9e5f71.png"
RES = r"C:\Users\Admin\Documents\iihjfasihu\app\src\main\res"

# lienzo de 108dp en cada densidad
DENSIDADES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

VISIBLE = 72.0 / 108.0     # porcion que el lanzador deja ver


def color_borde(im, muestra=24):
    """Color medio de las cuatro esquinas, para rellenar lo que no se ve."""
    w, h = im.size
    trozos = [
        im.crop((0, 0, muestra, muestra)),
        im.crop((w - muestra, 0, w, muestra)),
        im.crop((0, h - muestra, muestra, h)),
        im.crop((w - muestra, h - muestra, w, h)),
    ]
    px = [p for t in trozos for p in t.convert("RGB").getdata()]
    n = len(px)
    return (
        sum(c[0] for c in px) // n,
        sum(c[1] for c in px) // n,
        sum(c[2] for c in px) // n,
    )


def main():
    src = Image.open(ORIGEN).convert("RGB")
    fondo = color_borde(src)
    print("color de relleno del borde: #%02X%02X%02X" % fondo)

    for dens, lado in DENSIDADES.items():
        lienzo = Image.new("RGB", (lado, lado), fondo)
        d = int(round(lado * VISIBLE))
        # LANCZOS: al reducir de 1254 a 72px, un remuestreo peor deja la
        # malla del gato con dientes de sierra
        peq = src.resize((d, d), Image.LANCZOS)
        lienzo.paste(peq, ((lado - d) // 2, (lado - d) // 2))

        carpeta = os.path.join(RES, "mipmap-" + dens)
        os.makedirs(carpeta, exist_ok=True)
        lienzo.save(os.path.join(carpeta, "ic_launcher_fondo.png"), optimize=True)

        # icono clasico, por si algun lanzador no usa el adaptativo: ahi el
        # diseno va entero y sin margen, porque nadie lo va a recortar
        entero = src.resize((lado, lado), Image.LANCZOS)
        entero.save(os.path.join(carpeta, "ic_launcher.png"), optimize=True)
        entero.save(os.path.join(carpeta, "ic_launcher_round.png"), optimize=True)

        print("%-8s  lienzo %d  diseno %d" % (dens, lado, d))


if __name__ == "__main__":
    main()
