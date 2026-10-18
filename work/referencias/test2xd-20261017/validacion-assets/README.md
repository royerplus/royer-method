# Head bop editable en Royer Method

Este paquete contiene capas PNG transparentes, textos nativos, claves limitadas a la intro y seis instancias de la herramienta procedural Head bop. No contiene una animación renderizada ni un video externo ocultando el resultado.

## Abrir y ajustar

1. Abrir `headbop-test2xd.royer` en la variante de prueba con soporte Head bop.
2. Seleccionar una capa y entrar en **Animar → Head bop**.
3. Ajustar rol, BPM, intensidad, giro, rebote, inicio/final y fundido. Para cabeza, fijar el pivote/cuello. Para cabello, ajustar pivote, retardo y arrastre. Las manos conservan pivotes independientes.
4. Mantener BPM y fase compatibles entre las capas. La música y el inicio final global están en 6.840s.
5. Para cambiar el despiece de presentación, editar las pocas claves de encuadre anteriores al baile. No hay claves por cada golpe musical.
6. La fuente y el crédito **RAGI** se conservan. Los PNG reconstruidos son piezas derivadas para animación; se debe comprobar su encaje visual en el render nativo.

## Estructura

- Intro: original → despiece → cuello/pivote → cabeza → cabello/manos → reunión.
- Final: 6.840–15.733s, aproximadamente 107.665 BPM.
- 51 claves de intro, **0 claves del baile**, seis herramientas Head bop, cero efectos de shake/desenfoque añadidos.
- Fondo y proyecto cuadrados; exportación1080×1080,30fps,muestras4. La app fija los fps del motor, no el manifiesto.
- La música original se recorta antes del outro TikTok. No se incluye el video tutorial como imagen ni su marca visual.

## Regenerar

`python crear_headbop.py --assets CARPETA_PNG --out CARPETA_SALIDA`

Para revisar el proyecto sin tener aún todas las piezas:

`python crear_headbop.py --draft`

Para modificar valores iniciales sin editar el script, copiar `controles-headbop.json`, modificar sus parámetros y pasar `--config ARCHIVO.json`. Cada pieza admite además un objeto `encuadre` con `escala`, `x`, `y` y `rotacion` para alinear los recortes sin editar los PNG. También admite `--escala`, `--bpm`, `--inicio-final`, `--duracion`, `--original` y `--musica`.

El modo estricto rechaza PNG sin alpha real, piezas vacías, lienzos distintos y medios faltantes. La prueba estructural revisa IDs, orden de claves, intervalos y rutas. **Esto no sustituye a inspeccionar la vista previa y la exportación de la app.**

No está confirmada coincidencia visual con la referencia. El BPM es una estimación por señal: subdivisión medida0,278643s y candidato107,665BPM, sin escucha semántica del audio.
