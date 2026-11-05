#!/usr/bin/env python3
"""png2raw.py *.png -> *.raw (formato de screencap crudo: 16 bytes cabecera w,h,1,0 + RGBA) para regresion.sh / T*.java (RAW=archivo.raw)."""
import sys, struct
from PIL import Image
for f in sys.argv[1:]:
    im = Image.open(f).convert('RGBA')
    open(f[:-4] + '.raw', 'wb').write(struct.pack('<IIII', im.size[0], im.size[1], 1, 0) + im.tobytes())
