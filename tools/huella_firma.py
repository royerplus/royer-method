# -*- coding: utf-8 -*-
"""
Escribe la huella SHA-256 del certificado con el que va firmado un APK, en
hexadecimal y minusculas: lo mismo que keytool llama "SHA256" sin los dos
puntos. La compilacion de GitHub la compara con la de la clave de la cache
antes de publicar.

Lee el bloque de firma v2 (o el v3 si no hay v2) directamente del zip, sin
apksigner: el 2026-10-19 la salida de apksigner no traia la linea que se
buscaba y la comprobacion no pudo decir con que clave iba el APK.

  python3 tools/huella_firma.py app-release.apk
"""
import hashlib
import struct
import sys

V2 = 0x7109871A
V3 = 0xF05368C0


def con_largo(datos, p):
    """El trozo que empieza en p con su largo de 4 bytes delante, y donde acaba."""
    n = struct.unpack_from("<I", datos, p)[0]
    return datos[p + 4:p + 4 + n], p + 4 + n


def bloques(apk):
    fin_cd = apk.rfind(b"PK\x05\x06")
    if fin_cd < 0:
        raise ValueError("no es un zip")
    cd = struct.unpack_from("<I", apk, fin_cd + 16)[0]
    if apk[cd - 16:cd] != b"APK Sig Block 42":
        raise ValueError("no tiene bloque de firma v2/v3")
    largo = struct.unpack_from("<Q", apk, cd - 24)[0]
    p, fin = cd - largo, cd - 24  # los pares van tras el primer largo
    while p < fin:
        n = struct.unpack_from("<Q", apk, p)[0]
        yield struct.unpack_from("<I", apk, p + 8)[0], apk[p + 12:p + 8 + n]
        p += 8 + n


def certificado(bloque):
    """El primer certificado del primer firmante."""
    firmantes, _ = con_largo(bloque, 0)
    firmante, _ = con_largo(firmantes, 0)
    firmado, _ = con_largo(firmante, 0)
    _, p = con_largo(firmado, 0)  # resumenes
    certs, _ = con_largo(firmado, p)
    cert, _ = con_largo(certs, 0)
    return cert


def huella(ruta):
    with open(ruta, "rb") as f:
        apk = f.read()
    hay = dict(bloques(apk))
    for esquema in (V2, V3):
        if esquema in hay:
            return hashlib.sha256(certificado(hay[esquema])).hexdigest()
    raise ValueError("no tiene firma v2 ni v3")


if __name__ == "__main__":
    print(huella(sys.argv[1]))
