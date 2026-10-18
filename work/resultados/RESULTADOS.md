# Resultados de testprueba1

Se creó una adaptación editable con las doce fotos aportadas. Conserva el comienzo de la referencia y reproduce su ritmo, sus movimientos y sus transiciones con imágenes de un personaje diferente.

## Entregables

- [Video exportado](replica-testprueba1.mp4).
- [Proyecto editable con sus medios](replica-testprueba1.royer).
- [Instrucciones y tabla del montaje](replica-testprueba1-LEEME.md).
- [Parámetros completos](replica-testprueba1-parametros.csv).
- [Manifiesto del proyecto](replica-testprueba1-proyecto.json).
- [Informe de exportación](replica-informe.json).

## Montaje realizado

El intro original ocupa de 0 a 4.967 s. Su salida se reconstruye con un cierre vertical hasta 5.300 s para mostrar la primera foto debajo. El montaje usa las doce fotos, una única pista de audio y un cierre de blanco a negro. Termina a los 13.700 s y excluye el outro de TikTok.

El proyecto contiene una pista principal negra, 19 capas, una pista de audio y 71 tramos de efectos. La imagen siguiente ya se mueve detrás del cierre circular de la anterior, conservando el solape observado en la referencia. Los originales permanecen completos y los recortes del encuadre se pueden reajustar.

## Funciones disponibles

El editor permite colocar varios efectos dentro de un mismo bloque, recortar sus tiempos y ajustar intensidad, rapidez y entrada o salida suaves. Cada tramo puede duplicarse, eliminarse, desactivarse y probarse. Los cambios se guardan con el proyecto y se conservan al exportar el video.

El catálogo ofrece 18 efectos:

| Grupo | Efectos |
|---|---|
| Movimiento | Sacudida, sacudida vertical, zoom al ritmo y twitch. |
| Luz | Flash, flash negro y estrobo. |
| Imagen | Separación RGB, negativo, desenfoque, desenfoque horizontal, desenfoque vertical, bordes en espejo y glitch. |
| Cierres y máscaras | Cierre circular, apertura circular, cierre vertical y cierre horizontal. |

Para ajustar la réplica, toca una foto o su capa y abre **Efectos**. Los controles de inicio y fin incluyen pasos de un fotograma. **Animar**, **Encuadre** y **Claves** permiten modificar zoom, recorte, posición, rotación y opacidad.

**Abrir proyecto** y **Guardar proyecto** están al final de la barra inferior de herramientas, que se desplaza horizontalmente. El formato `.royer` reúne el montaje y sus archivos en un paquete portátil.

## Verificación de la exportación

La exportación inicial se completó en el teléfono mediante el motor de la app: H.264, 576 × 768 píxeles, 411 fotogramas y una cadencia de 30 fps. El verificador registró cero fotogramas irregulares y cero avisos de fuentes. El informe completo está en `replica-informe.json`.

<!-- ROOT: incorporar aquí las mediciones finales y el resultado de la comprobación táctil. No convertir la validación de cadencia del archivo en una afirmación de fluidez de la vista previa. -->

## Aplicación original y variante de prueba

La aplicación instalada y los APK compilados localmente tienen firmas distintas. Se usó la variante de prueba `com.royermethod.app.prueba` para conservar la aplicación original y sus datos. La variante de prueba mantiene su proyecto y sus archivos por separado.
