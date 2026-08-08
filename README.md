# Royer Method

App de Android para preparar vídeos e imágenes antes de subirlos a redes
sociales. Está pensada para edits de anime, que es el caso difícil: mucho
movimiento, líneas finas y compresión agresiva por parte de la red.

## La idea

Las redes reparten un presupuesto de bits parecido sin importar la resolución
que subas. Cuantos más píxeles le des, menos le toca a cada uno. Así que subir
en 4K no mejora el resultado: lo empeora.

Lo que sí funciona es al revés — bajar la resolución con un buen filtro,
generar fotogramas intermedios y mezclarlos de vuelta para que cada fotograma
lleve el rastro del movimiento dentro. Sale más fluido y pesa mucho menos.

## Qué hace

**Vídeo**
- Genera fotogramas intermedios con RIFE, hasta 960 por segundo, en la GPU del
  teléfono por Vulkan.
- Los mezcla de vuelta a 24, 30 o 60 fps. Ese promedio es el desenfoque de
  movimiento, y es de donde viene la fluidez.
- Cámara lenta por tramos: en vez de mezclar los generados, los reparte. Se ve
  lento y fluido porque los fotogramas son reales, no repetidos.
- Reduce resolución con Lanczos en dos pasadas sobre la GPU.
- Reconstruye detalle con waifu2x, al doble y de vuelta.
- Comprime con MediaCodec.

**Imagen**
- Reconstruye con waifu2x y vuelve al tamaño de partida, así que sube calidad
  sin subir resolución.
- Reduce bajando por mitades, que promedia todos los píxeles en vez de
  descartarlos.
- Comprime a JPG, WEBP o PNG.

**Comparación**
- Antes y después en paralelo, enganchados fotograma a fotograma.
- Lupa a pantalla completa con línea divisoria y zoom.

**Cola**: prepara varios archivos y los procesa uno detrás de otro, guardando
cada resultado en la galería. Para dejarlo trabajando de noche.

**Edición** (beta): línea de tiempo, cortes, deshacer. En construcción.

## Compilar

Hace falta el NDK y CMake instalados desde el SDK Manager.

```
powershell -ExecutionPolicy Bypass -File tools\traer_dependencias.ps1
gradlew assembleRelease
```

El primer paso descarga ncnn y los pesos de los modelos, que no están en el
repositorio por ser binarios de terceros. Ver [NOTICE.md](NOTICE.md).

Solo se compila para `arm64-v8a`. Es lo que lleva cualquier teléfono capaz de
mover Vulkan con computación, que es lo que exigen los modelos.

## Estructura

```
app/src/main/java/com/royermethod/app/
  media/        leer archivos y sus datos
  procesado/    el motor: GL, transcodificado, modelos, cola
  ui/           pantallas y componentes
app/src/main/cpp/
  rife/         RIFE, con parches para ncnn moderno
  waifu2x/      waifu2x
  *_jni.cpp     puentes a Kotlin
tools/          scripts de apoyo y las pruebas de escritorio
```

## Notas

Los parches sobre `rife.cpp` y `warp.cpp` no son opcionales: el código
original es de 2022 y con ncnn actual daba imágenes corruptas y un fallo de
segmentación al cargar. Están comentados en el sitio donde se aplican.
