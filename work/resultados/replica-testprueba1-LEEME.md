# Réplica editable de testprueba1

Esta es una adaptación con las doce fotos que aportaste. Reproduce el ritmo, las animaciones y las transiciones observadas en la referencia, usando imágenes de un personaje diferente. No pretende mantener la identidad del personaje de la referencia.

El proyecto dura 13.700 segundos, en formato 3:4, con salida de 576 × 768 píxeles a 30 fps. El video `replica-testprueba1.mp4` se exportó mediante el motor de la app; el archivo `replica-testprueba1.royer` conserva el montaje editable.

## Qué contiene

- El intro original desde 0 hasta 4.967 s. Su sonido está silenciado para evitar duplicarlo con la pista musical.
- Un fotograma del intro a 4.933 s que se cierra verticalmente entre 4.967 y 5.300 s, revelando la primera foto debajo.
- Las doce fotos originales, repartidas entre dos ciclos de ocho planos. En el segundo ciclo se reutilizan cuatro fotos para seguir el ritmo de la referencia.
- Una única pista de audio tomada de la referencia hasta 13.700 s.
- Un cierre con blanco que se desvanece a negro. El outro de TikTok de 13.700 a 17.700 s queda fuera del montaje.

Los originales se conservan completos dentro del paquete. Las primeras cinco fotos tienen recortes de encuadre no destructivos para excluir las barras del teléfono y los márgenes de captura.

## Cómo abrir y ajustar el proyecto

1. Abre el editor de la variante de prueba. Desliza horizontalmente la barra inferior de herramientas hasta encontrar **Abrir proyecto** y elige `replica-testprueba1.royer`.
2. Mueve el cabezal al instante que quieras revisar y toca la foto o su bloque de capa en la línea de tiempo. Pulsa **Efectos** para ver los tramos aplicados.
3. Selecciona un efecto para cambiar su inicio, fin, intensidad, rapidez o entrada y salida suaves. Los botones de un fotograma permiten ajustar los bordes con precisión. **Probar** reproduce su tramo y el interruptor permite compararlo activado o desactivado.
4. Usa **Animar**, **Encuadre** y **Claves** para modificar movimiento, recorte, zoom, posición o rotación. Deshacer y rehacer conservan los cambios como pasos editables.
5. Para guardar otra copia con sus medios, vuelve a la barra general y deslízala horizontalmente hasta **Guardar proyecto**. La exportación de video se realiza desde el control de exportación del editor.

## Estructura y ajustes usados

- 19 capas y 71 tramos de efectos. Se usan bordes en espejo, desenfoques direccionales, sacudidas pequeñas, cierres circulares, aperturas circulares y cierres verticales.
- Fotogramas clave de zoom, rebote, desplazamiento, rotación y opacidad.
- Dos revelados de izquierda a derecha durante el solape de imágenes.
- Las fotos base están en la fila 1; las superposiciones en la fila 2; el intro y el blanco final en la fila 3. La pista principal es negra.
- El plano C aparece detrás de B desde 6.033 s y 9.700 s, antes de terminar el cierre circular de B.
- El blanco final se desvanece sobre negro durante 0.900 s.

## Tabla del montaje

Los tiempos se almacenan en microsegundos y se alinean con la cuadrícula de 30 fps. A–H identifican los planos de cada ciclo.

| Bloque | Foto | Inicio (s) | Fin (s) | Fila | Efectos |
|---|---|---:|---:|---:|---|
| Intro original (audio silenciado) | — | 0.000000 | 4.966666 | 3 | — |
| Intro congelado: cierre vertical al centro | — | 4.966666 | 5.300000 | 3 | Cierre vertical |
| A1 | 01 | 4.966666 | 5.733333 | 1 | Bordes en espejo, Desenfoque horizontal, Sacudida |
| B1 | 02 | 5.733333 | 6.233333 | 2 | Bordes en espejo, Desenfoque horizontal, Sacudida, Cierre circular |
| C1 | 03 | 6.033333 | 6.666666 | 1 | Bordes en espejo, Desenfoque vertical, Sacudida, Apertura circular |
| D1 | 04 | 6.666666 | 7.133333 | 1 | Bordes en espejo, Desenfoque vertical, Sacudida |
| E1 | 05 | 7.133333 | 7.600000 | 1 | Bordes en espejo, Desenfoque horizontal, Sacudida |
| F1 | 06 | 7.600000 | 8.033333 | 1 | Bordes en espejo, Desenfoque horizontal, Sacudida |
| G1 | 07 | 7.733333 | 8.500000 | 2 | Bordes en espejo, Desenfoque vertical, Sacudida |
| H1 | 08 | 8.500000 | 8.966666 | 2 | Bordes en espejo, Desenfoque vertical, Sacudida, Cierre vertical |
| A2 | 09 | 8.700000 | 9.400000 | 1 | Bordes en espejo, Desenfoque horizontal, Sacudida |
| B2 | 10 | 9.400000 | 9.900000 | 2 | Bordes en espejo, Desenfoque horizontal, Sacudida, Cierre circular |
| C2 | 11 | 9.700000 | 10.333333 | 1 | Bordes en espejo, Desenfoque vertical, Sacudida, Apertura circular |
| D2 | 12 | 10.333333 | 10.800000 | 1 | Bordes en espejo, Desenfoque vertical, Sacudida |
| E2 | 01 | 10.800000 | 11.266666 | 1 | Bordes en espejo, Desenfoque horizontal, Sacudida |
| F2 | 03 | 11.266666 | 11.733333 | 1 | Bordes en espejo, Desenfoque horizontal, Sacudida |
| G2 | 05 | 11.400000 | 12.166666 | 2 | Bordes en espejo, Desenfoque vertical, Sacudida |
| H2 | 07 | 12.166666 | 12.633333 | 2 | Bordes en espejo, Desenfoque vertical, Sacudida, Desenfoque horizontal |
| Final: blanco a negro | — | 12.633333 | 13.700000 | 3 | — |

## Archivos

| Archivo | Uso |
|---|---|
| `replica-testprueba1.mp4` | Video exportado desde la app. |
| `replica-testprueba1.royer` | Proyecto portátil con `proyecto.json` y 17 medios. |
| `replica-testprueba1-proyecto.json` | Manifiesto legible del montaje. |
| `replica-testprueba1-parametros.csv` | Recortes y parámetros completos de cada capa, efecto y animación. |
| `replica-testprueba1-validacion.json` | Tamaño y huella SHA-256 del paquete. |
| `replica-informe.json` | Verificación de la exportación realizada en la app. |
| `../crear_replica.py` | Generador del proyecto; requiere Python con Pillow y FFmpeg. |

## Compatibilidad del paquete

Comprobado: ZIP íntegro, todos los medios presentes, doce fotos distintas utilizadas, identificadores únicos, claves ordenadas y los 71 intervalos de efectos dentro de sus clips. Los nombres de efectos y los campos del JSON coinciden con el lector actual del editor.

API de persistencia: `version = 1`, `formato = RETRATO_3_4`, `principal[]`, `capas[{inicio, fila, mezcla, clip}]`, `audios[]` y `siguienteId`. Cada clip conserva `origen`, `encuadre`, `claves`, `entrada`, `salida` y `efectos`. El ZIP usa `proyecto.json` y rutas `medios/00000.ext`.
