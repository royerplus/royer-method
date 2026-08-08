# -*- coding: utf-8 -*-
"""
Convierte los shaders .comp de RIFE en cabeceras C.

rife-ncnn-vulkan no compila sus shaders: los incrusta como texto y ncnn los
compila en el movil al arrancar. El proyecto original hace esta conversion con
una macro de CMake de ncnn, que el paquete precompilado de Android no trae.
Son doce lineas, asi que se hace aqui y las cabeceras se quedan en el repo:
una dependencia menos en el build.

Replica exactamente lo que hace generate_shader_comp_header.cmake:
  1. tira todo lo anterior a #version (la licencia)
  2. quita la sangria de cada linea
  3. escribe los bytes como lista de enteros en hexadecimal
"""
import os
import re

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    "..", "app", "src", "main", "cpp")
CARPETAS = ["rife", "waifu2x"]


def convertir(BASE):
    hechos = 0
    for nombre in sorted(os.listdir(BASE)):
        if not nombre.endswith(".comp"):
            continue
        ruta = os.path.join(BASE, nombre)
        with open(ruta, "r", encoding="utf-8") as f:
            texto = f.read()

        corte = texto.find("#version")
        if corte < 0:
            print("sin #version: %s" % nombre)
            continue
        texto = texto[corte:]
        texto = re.sub(r"\n +", "\n", texto)

        crudo = texto.encode("utf-8")
        cuerpo = ",".join("0x%02x" % b for b in crudo)
        base = nombre[:-5]

        destino = os.path.join(BASE, base + ".comp.hex.h")
        with open(destino, "w", encoding="utf-8") as f:
            f.write("static const char %s_comp_data[] = {%s};\n" % (base, cuerpo))
        hechos += 1
        print("%-40s %6d bytes" % (base + ".comp.hex.h", len(crudo)))
    return hechos


def main():
    total = 0
    for c in CARPETAS:
        ruta = os.path.join(RAIZ, c)
        if os.path.isdir(ruta):
            total += convertir(ruta)
    print("LISTO: %d cabeceras" % total)


if __name__ == "__main__":
    main()
