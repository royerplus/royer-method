# Head bop V4 editable en Royer Method

Este paquete contiene capas PNG transparentes, claves limitadas a la intro y seis instancias de la herramienta procedural Head bop. V4 omite por defecto todas las capas de texto añadidas, incluido el signo + y el crédito nativo. La fotografía original se conserva intacta y su autoría corresponde a **RAGI**. No contiene una animación renderizada ni un video externo ocultando el resultado.

## Abrir y ajustar

1. Abrir `headbop-test2xd.royer` en la variante de prueba con soporte Head bop.
2. Seleccionar una capa y entrar en **Animar → Head bop**.
3. Ajustar rol, BPM, intensidad, giro, rebote, inicio/final y fundido. Para cabeza, fijar el pivote/cuello. Para cabello, ajustar pivote, retardo y arrastre. Las manos conservan pivotes independientes.
4. Mantener BPM y fase compatibles entre las capas. La música y el inicio final global están en 6.840s.
5. Para cambiar el despiece de presentación, editar las pocas claves de encuadre anteriores al baile. No hay claves por cada golpe musical.
6. La fuente original de **RAGI** se conserva intacta, incluido su crédito en la fotografía. Los PNG reconstruidos son piezas derivadas para animación; se debe comprobar su encaje visual en el render nativo.

## Estructura

- Intro: original → despiece → cabeza → cabello/manos → reunión. Se conserva su dirección y música, sin rótulos superpuestos.
- Final: 6.840–15.733s, aproximadamente 107.665 BPM.
- 60 claves de intro, **0 claves del baile**, 12 claves sólo de fundido final: 72 claves totales. Seis herramientas Head bop, cero efectos de shake/desenfoque añadidos.
- Zoom final común1.160 y desplazamiento[0.0, 0.02]; rebote común0.012; fundido final0.200s. Todos modificables en controles-headbop.json: zoomFinal, desplazamientoFinalX/Y, rebote y fundidoFinalUs.
- Fondo y proyecto cuadrados; exportación1080×1080,30fps,muestras4. La app fija los fps del motor, no el manifiesto.
- La música original se recorta antes del outro TikTok. No se incluye el video tutorial como imagen ni su marca visual.

## Regenerar

`python crear_headbop-v4.py --assets assets-v4 --config proyecto-headbop-v4/ajustes-headbop-v4.json --out proyecto-headbop-v4`

Para revisar el proyecto sin tener aún todas las piezas:

`python crear_headbop-v4.py --draft`

El punto de partida es `proyecto-headbop-v3/controles-headbop.json`; `--config` aplica ajustes parciales encima. Cada pieza admite un objeto `encuadre` con `escala`, `x`, `y` y `rotacion`. También admite `--escala`, `--bpm`, `--inicio-final`, `--duracion`, `--original` y `--musica`. Para reutilizar el archivo completo de controles de esta V4, pasar `--base-config proyecto-headbop-v4/controles-headbop.json` sin volver a aplicar los ajustes parciales.

Los PNG pueden tener tamaños diferentes. Si conservan la relación de aspecto y framing, no necesitan cambios. Si se prolonga el lienzo, indicar `marcoReferenciaUV: [izquierda, arriba, derecha, abajo]`, el rectángulo normalizado que contiene el lienzo anterior en el PNG nuevo. Ejemplo: al extender un cuadrado hacia abajo a 1024×1536, usar `[0, 0, 1, 0.6666666667]`. El generador recalcula escala/centro, pivotes y cuello con ENCAJAR; rechaza transformaciones que deformen la imagen. El informe registra la geometría aplicada. Los PNG originales y V1/V2/V3 permanecen intactos.

El modo estricto rechaza PNG sin alpha real, piezas vacías y medios faltantes. La comprobación mínima revisa IDs, orden de claves, intervalos, rutas, CRC del ZIP, medios no vacíos y ausencia de los rótulos retirados. **Esto no sustituye a inspeccionar la vista previa y la exportación de la app.** La exportación final debe realizarla Royer Method.

No está confirmada coincidencia visual con la referencia. El BPM es una estimación por señal: subdivisión medida0,278643s y candidato107,665BPM, sin escucha semántica del audio.
