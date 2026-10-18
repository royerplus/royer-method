Estado de entrega: exportación nativa completada y revisada el 2026-10-17. Video en validacion-v8/headbop-v8.mp4 y en galería del teléfono. Informe nativo: 472 cuadros, 1080×1080, 30 fps, cero cuadros irregulares y cero avisos. No se midió fluidez de vista previa.

# Head bop V8 · parpadeo con más poses

Conserva V6 completa: medios, música, intro, duración, encuadre, movimiento y geometría. V4 y las versiones previas siguen intactas. Añade nueve estados oculares reales sobre la cabeza: 90, 80, 65, 55, 45, 30, 20, 10 y 0% abiertos, además del abierto original. Los seis nuevos dibujos se combinan con los tres de V7.

Los nombres de los PNG son etiquetas de generación. La revisión del área visible de ambos iris encontró que ojos-45.png estaba más abierto que ojos-55.png. Por eso el nodo nominal 55% usa ojos-45.png y el nodo nominal 45% usa ojos-55.png, sin editar ningún raster. El orden temporal sigue la apertura verificada: original, 90, 80, 65, archivo45, archivo55, 30, 20, 10 y cerrado. Los porcentajes son nombres de nodos de animación, no una medición física de apertura.

Dos parpadeos a 8.150 y 12.050 s, de 0.660 s: cierre de 0.240 s, cerrado de 0.100 s y apertura de 0.320 s. Los tiempos de cada pose se obtienen invirtiendo smoothstep 3u²−2u³ según su apertura real. El gesto acelera y frena globalmente. Entre poses se usa opacidad lineal para avanzar sin las pausas intermedias de V7. A 30 fps cambia la mezcla en cada cuadro de cierre/apertura.

Sólo dos poses contribuyen a la imagen a la vez. Los estados inferiores ya tapados se desactivan mediante claves de 1 µs mientras una capa opaca los cubre; se reactivan ocultos antes de reaparecer. Como máximo dos overlays están activos en instantes enteros de µs y tres durante el cruce oculto de 1 µs. Esto limita el apilado; no es una medición de rendimiento.

Cada capa ocular copia los tiempos, el encuadre, las doce claves y HeadBop de Cabeza. Sólo las dos piezas UV de los ojos cambian opacidad; no se escala ni deforma la cabeza. Las 72 claves V6 y los nueve medios originales se verifican intactos.

Regenerar con `python crear_headbop-v8.py`; `--draft` valida tiempos y escribe ajustes sin exigir los PNG. `ajustes-parpadeo-v8.json` reúne inicios, duración y poses. La validación matemática se guarda en `validacion-tiempos-v8.json`. Pendiente en este generador: revisión de los dibujos y exportación nativa. No se ha medido fluidez.
