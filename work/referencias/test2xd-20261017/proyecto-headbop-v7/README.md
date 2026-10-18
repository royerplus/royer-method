# Head bop V7 · parpadeo lento

Parte del proyecto V6 y conserva todos sus medios, música, intro, duración, salida y movimiento. V4 y su borrador aprobado siguen intactos.

Añade tres capas de estados oculares por encima de Cabeza y debajo de Flequillo. Cada una copia íntegramente los tiempos, el encuadre, las doce claves de movimiento y la configuración HeadBop de Cabeza. Las seis piezas UV oculares animan solamente su opacidad: no hay escala, desplazamiento ni giro relativo. Esto mantiene los ojos pegados a la misma cabeza sin aplastarla.

Hay dos parpadeos: 8.150–8.810 s y 12.050–12.710 s. Cada uno dura 0.660 s: cierre en 0.240 s, pausa de 0.100 s con ojos cerrados y recuperación en 0.320 s. El original abierto se conserva debajo; dos estados intermedios evitan el salto directo de abierto a cerrado. No hay parpadeos en la intro.

Las siete capas previas sólo cambian de fila para dejar espacio a las tres nuevas. Las 72 claves previas quedan intactas; las capas de ojos heredan 36 claves de movimiento y sus seis piezas añaden 60 claves exclusivamente de opacidad. Los nueve medios V6 son idénticos byte por byte. La configuración y el manifiesto incluyen tiempos y hashes.

Regenerar: `python crear_headbop-v7.py`; `--draft` prepara la configuración sin exigir los PNG. Los nuevos PNG deben ser RGBA de 1254 × 1254, alineados con Cabeza.png. El script no modifica ni recorta las imágenes: los recortes son piezas nativas del proyecto.

Verificado: CRC, identidad de medios previos, preservación de movimiento y estructura nativa. Exportación nativa completada en Royer Method · Prueba: 472 cuadros, 1080×1080 y 30 fps, sin cuadros irregulares ni avisos de fuentes. Se inspeccionaron las poses de cierre/apertura y sus bordes en una secuencia del video final; el resultado queda para valoración del usuario. No se midió fluidez. Video y proyecto ya están copiados en la galería y Download/RoyerMethod del teléfono.
