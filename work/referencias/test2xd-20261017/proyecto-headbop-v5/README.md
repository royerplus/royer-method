# Head bop V5: prueba de movimiento natural

La V4 que gustó al usuario está conservada sin cambios en ../borradores/V4-aprobada. V5 parte de su paquete final, sin volver a construir la intro ni remapear los PNG.

En las seis instancias Head bop se cambia únicamente naturalidad de 0 (predeterminado anterior) a 0.9, giro de 7 a 4.8 grados y rebote de 0.012 a 0.010. Las intensidades de cabeza y ambos cabellos siguen en 1, sus movimientos comparten la misma base y conservan BPM, fase, tiempos, pivotes, cuello, retardo y arrastre. La nueva propiedad necesita una versión de Royer Method que implemente naturalidad; una versión anterior la ignorará.

Se conservan los nueve medios byte por byte, siete capas y las 72 claves originales (intro y fundido; cero claves de baile). No se añade texto ni +. El manifiesto documenta hashes y los 18 únicos cambios del JSON.

Regenerar desde la carpeta raíz con `python crear_headbop-v5.py`. El script rechaza una V4 de origen con hash distinto y nunca sustituye un borrador archivado diferente.

Validado aquí: estructura ZIP, CRC, integridad de medios, claves y alcance de cambios. Pendiente al preparar este paquete: exportación en la app, revisión visual del movimiento y cualquier comprobación de fluidez. No se afirma coincidencia con una referencia.
