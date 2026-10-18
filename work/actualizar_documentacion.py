from pathlib import Path
import csv, json
base=Path(r'C:\Users\PC\Downloads\royer-method-work')
out=base/'resultados'
rows=list(csv.DictReader((out/'replica-testprueba1-parametros.csv').open(encoding='utf-8-sig')))
lines=['# Réplica editable de testprueba1','',
'Esta es una adaptación con las doce fotos que aportaste. Reproduce el ritmo, las animaciones y las transiciones observadas en la referencia, usando imágenes de un personaje diferente. No pretende mantener la identidad del personaje de la referencia.','',
'El proyecto dura 13.700 segundos, en formato 3:4, con salida de 576 × 768 píxeles a 30 fps. El video `replica-testprueba1.mp4` se exportó mediante el motor de la app; el archivo `replica-testprueba1.royer` conserva el montaje editable.','',
'## Qué contiene','',
'- El intro original desde 0 hasta 4.967 s. Su sonido está silenciado para evitar duplicarlo con la pista musical.','- Un fotograma del intro a 4.933 s que se cierra verticalmente entre 4.967 y 5.300 s, revelando la primera foto debajo.','- Las doce fotos originales, repartidas entre dos ciclos de ocho planos. En el segundo ciclo se reutilizan cuatro fotos para seguir el ritmo de la referencia.','- Una única pista de audio tomada de la referencia hasta 13.700 s.','- Un cierre con blanco que se desvanece a negro. El outro de TikTok de 13.700 a 17.700 s queda fuera del montaje.','',
'Los originales se conservan completos dentro del paquete. Las primeras cinco fotos tienen recortes de encuadre no destructivos para excluir las barras del teléfono y los márgenes de captura.','',
'## Cómo abrir y ajustar el proyecto','',
'1. Abre el editor de la variante de prueba. Desliza horizontalmente la barra inferior de herramientas hasta encontrar **Abrir proyecto** y elige `replica-testprueba1.royer`.','2. Mueve el cabezal al instante que quieras revisar y toca la foto o su bloque de capa en la línea de tiempo. Pulsa **Efectos** para ver los tramos aplicados.','3. Selecciona un efecto para cambiar su inicio, fin, intensidad, rapidez o entrada y salida suaves. Los botones de un fotograma permiten ajustar los bordes con precisión. **Probar** reproduce su tramo y el interruptor permite compararlo activado o desactivado.','4. Usa **Animar**, **Encuadre** y **Claves** para modificar movimiento, recorte, zoom, posición o rotación. Deshacer y rehacer conservan los cambios como pasos editables.','5. Para guardar otra copia con sus medios, vuelve a la barra general y deslízala horizontalmente hasta **Guardar proyecto**. La exportación de video se realiza desde el control de exportación del editor.','',
'## Estructura y ajustes usados','',
'- 19 capas y 71 tramos de efectos. Se usan bordes en espejo, desenfoques direccionales, sacudidas pequeñas, cierres circulares, aperturas circulares y cierres verticales.','- Fotogramas clave de zoom, rebote, desplazamiento, rotación y opacidad.','- Dos revelados de izquierda a derecha durante el solape de imágenes.','- Las fotos base están en la fila 1; las superposiciones en la fila 2; el intro y el blanco final en la fila 3. La pista principal es negra.','- El plano C aparece detrás de B desde 6.033 s y 9.700 s, antes de terminar el cierre circular de B.','- El blanco final se desvanece sobre negro durante 0.900 s.','',
'## Tabla del montaje','',
'Los tiempos se almacenan en microsegundos y se alinean con la cuadrícula de 30 fps. A–H identifican los planos de cada ciclo.','',
'| Bloque | Foto | Inicio (s) | Fin (s) | Fila | Efectos |',
'|---|---|---:|---:|---:|---|']
pretty={'BORDES_ESPEJO':'Bordes en espejo','CERRAR_VERTICAL':'Cierre vertical','CIRCULO_ABRIR':'Apertura circular','CIRCULO_CERRAR':'Cierre circular','DESENFOQUE_HORIZONTAL':'Desenfoque horizontal','DESENFOQUE_VERTICAL':'Desenfoque vertical','SACUDIDA':'Sacudida'}
for r in rows:
 effect_names=[pretty.get(e['tipo'],e['tipo']) for e in json.loads(r['efectos'])]
 effect_names=list(dict.fromkeys(effect_names))
 effects=', '.join(effect_names) or '—'
 photo=r['foto'].split(':')[0] if r['foto'] else '—'
 lines.append(f"| {r['plano']} | {photo} | {int(r['inicio_us'])/1e6:.6f} | {int(r['fin_us'])/1e6:.6f} | {r['fila']} | {effects} |")
lines += ['', '## Archivos','',
'| Archivo | Uso |','|---|---|',
'| `replica-testprueba1.mp4` | Video exportado desde la app. |',
'| `replica-testprueba1.royer` | Proyecto portátil con `proyecto.json` y 17 medios. |',
'| `replica-testprueba1-proyecto.json` | Manifiesto legible del montaje. |',
'| `replica-testprueba1-parametros.csv` | Recortes y parámetros completos de cada capa, efecto y animación. |',
'| `replica-testprueba1-validacion.json` | Tamaño y huella SHA-256 del paquete. |',
'| `replica-informe.json` | Verificación de la exportación realizada en la app. |',
'| `../crear_replica.py` | Generador del proyecto; requiere Python con Pillow y FFmpeg. |','',
'## Compatibilidad del paquete','',
'Comprobado: ZIP íntegro, todos los medios presentes, doce fotos distintas utilizadas, identificadores únicos, claves ordenadas y los 71 intervalos de efectos dentro de sus clips. Los nombres de efectos y los campos del JSON coinciden con el lector actual del editor.','',
'API de persistencia: `version = 1`, `formato = RETRATO_3_4`, `principal[]`, `capas[{inicio, fila, mezcla, clip}]`, `audios[]` y `siguienteId`. Cada clip conserva `origen`, `encuadre`, `claves`, `entrada`, `salida` y `efectos`. El ZIP usa `proyecto.json` y rutas `medios/00000.ext`.']
(out/'replica-testprueba1-LEEME.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
report='''# Resultados de testprueba1

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

El proyecto contiene una pista principal negra, 19 capas, una pista de audio y 71 tramos de efectos. La segunda imagen ya se mueve detrás del cierre circular de la anterior, conservando el solape observado en la referencia. Los originales permanecen completos y los recortes del encuadre se pueden reajustar.

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
'''
(out/'RESULTADOS.md').write_text(report,encoding='utf-8')
print('Documentos actualizados: LEEME y RESULTADOS.md')