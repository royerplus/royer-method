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

**Editor** (por bloques, como CapCut)
- Pista principal y, debajo, una fila por cada capa (videos, fotos, textos)
  y por cada sonido. Se toca un bloque para editarlo; mantener pulsado lo
  mueve; los bordes lo recortan; pellizcar acerca la línea de tiempo.
- Animaciones de entrada, salida y continuas. Cada una es una lista de
  piezas que se combinan y se ajustan (fuerza, curva, cuándo empieza y
  acaba), y se pueden guardar como propias. Duraciones desde un fotograma
  (0,033 s a 30 fps), con ajuste de cuadro en cuadro.
- Partes de la imagen con regiones UV: separar mitades superior e inferior o
  izquierda y derecha, y ajustar los límites de cada región sin volver a llenar
  el lienzo con ella. Cada parte conserva el encuadre del bloque y tiene posición,
  escala, giro, opacidad y fotogramas clave independientes, con curvas y Bézier.
  Se pueden duplicar, quitar y ajustar sus tiempos fotograma a fotograma.
- Efectos de video independientes de la entrada y salida: 19 tipos, incluidos
  iris, cierres, revelado horizontal (`REVELAR_HORIZONTAL`), bordes en espejo y
  desenfoque horizontal y vertical. Cada
  efecto tiene inicio, fin, intensidad, rapidez, fundidos y activación; se
  pueden combinar, duplicar y recortar sin reiniciar su movimiento.
- Puntos de progreso editables para los iris, cierres y revelado horizontal:
  permiten reproducir el recorrido de una máscara medido en una referencia,
  controlando su avance en cada instante del efecto.
- Capas que se solapan: la imagen entrante puede animarse debajo mientras
  la anterior cierra su máscara. Los efectos aparecen como barras en los bloques.
- Transiciones con duración, ritmo, dirección, color y fuerza.
- Fotogramas clave con curvas editables a mano.
- Vista previa con reloj de audio continuo (`RelojAudio`): interpola las marcas
  de `AudioTimestamp`, limita el avance a las muestras escritas y al cabezal
  observado, y se detiene si falta audio. `Choreographer` programa el dibujo
  desde el hilo GL al ritmo de la pantalla. El objetivo es mantener 30 fps;
  el rendimiento final debe verificarse en el teléfono con el proyecto real.
- Formato 3:4, además de los formatos vertical, horizontal, cuadrado y 4:5.
- Abrir y guardar proyectos `.royer` con todos sus medios incluidos. La
  importación conserva el montaje anterior en deshacer y copia los archivos
  a la app para que el proyecto siga funcionando al mover el paquete.
- Exporta a 30 fps exactos (comprobados en el archivo), H.265 si el
  teléfono lo tiene y si no H.264, con el bitrate repartido según lo que se
  mueve en cada tramo. Los videos de menos de 30 fps generan los fotogramas
  que faltan.

**Personalizar**: color del neón, fondo, halo, latido, velocidad de las
animaciones, tamaño de letra, esquinas y los valores con los que empieza el
editor.

## Compilar

Hace falta el NDK y CMake instalados desde el SDK Manager.

```
powershell -ExecutionPolicy Bypass -File tools\traer_dependencias.ps1
gradlew assembleRelease
```

El primer paso descarga ncnn y los pesos de los modelos, que no están en el
repositorio por ser binarios de terceros. Ver [NOTICE.md](NOTICE.md). En Linux
es `bash tools/traer_dependencias.sh`.

GitHub también lo compila solo en cada push a `main` o a la rama del editor, y
deja el APK siempre en el mismo enlace:
https://github.com/royerplus/royer-method/releases/download/prueba/RoyerMethod.apk

Solo se compila para `arm64-v8a`. Es lo que lleva cualquier teléfono capaz de
mover Vulkan con computación, que es lo que exigen los modelos.

Para probar sin sustituir una instalación con otra firma:

```
gradlew assemblePrueba
```

Genera una variante optimizada llamada **Royer Method · Prueba**, con el
identificador `com.royermethod.app.prueba`. `testDebugUnitTest` comprueba el
modelo, las partes de imagen, los efectos, el reloj de audio, la persistencia
y los paquetes. Las pruebas optativas de
`ProyectoRealIntegrationTest` exportan y miden la vista previa en un teléfono
con un proyecto real, dejando sus informes junto al MP4. El informe de vista
previa separa cuadros publicados por EGL, tiempos de CPU y swap, y actualizaciones
de `TextureView` asociadas a la marca real de cada buffer. Esas mediciones son las
que permiten comprobar la fluidez de una compilación concreta.

## Estructura

```
app/src/main/java/com/royermethod/app/
  media/        leer archivos y sus datos
  procesado/    el motor de mejora: GL, transcodificado, modelos, cola
  editor/
    modelo/     el proyecto y sus cuentas (sin Android: se prueba en el PC)
    motor/      composición con OpenGL, compartida por vista previa y exportación
    exportar/   codificadores, MP4 y comprobación del resultado
    audio/      mezcla, cambio de velocidad y tono
    ui/         la pantalla del editor (sin Android: se dibuja también en el PC)
    vista/      lo que une la pantalla con el teléfono
  ui/           pantallas, componentes y tema
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
