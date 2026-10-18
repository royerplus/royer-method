# Revisión independiente de encaje inicial

Inspección de V8 nativa en 0.600 s y 1.150 s. Los archivos derivados son sólo cuadros de análisis; ningún PNG de producción se modificó.

## Decisión mínima

Modificar exclusivamente el encuadre de la foto original (capa id 2): mismo ajuste geométrico en encuadre base y en sus tres claves. Dejar intactos los seis recortes, las capas de párpados, los pivotes de cuello y sus claves. Desplazar todo el rig en las poses iniciales corregiría el encaje, pero introduciría un retorno adicional y exigiría sincronizar dieciséis capas; tocar únicamente la cabeza volvería a separar cuello y pelo.

La revisión visual muestra un descenso facial de aproximadamente 26–31 px entre ambas imágenes, con posición X de los iris muy similar. Este rango es una estimación visual independiente, no sustituye el registro de puntos. La corrección V4 de +0.028 en Y equivale a 30.24 px a 1080 px y explica gran parte del desajuste. Una traslación uniforme de la foto hacia abajo es el punto de partida; una escala y rotación uniformes pequeñas pueden mejorar ojos, boca y mentón si los puntos medidos lo justifican. No deformar X/Y por separado ni alinear sólo el flequillo, porque su dibujo difiere.

## Estabilidad durante el fundido

La foto comienza en 0, conserva opacidad 1 hasta 650000 µs y llega a 0 en 1100000 µs. Su geometría actual es estática. El rig comienza en 700000 µs, y todas sus capas conservan su geometría inicial hasta 1300000 µs; HeadBop aún no actúa. Por tanto, un ajuste estático de la foto permanece estable durante todo el cruce, no sólo en un cuadro.

La curva SUAVE de las claves es ease-in-out cúbica (Escena.curva), no smoothstep. En el intervalo 650–700 ms anterior al comienzo del rig la foto sigue presente: alfa mínimo 0.994513. A 30 fps el único cuadro intermedio es 666.667 ms, alfa aproximado 0.999797. No existe hueco negro. Evitar cambiar los tiempos para resolver un problema geométrico; conservarlos permite acotar la diferencia a la foto. Las cuatro muestras temporales nativas tampoco generan movimiento geométrico en estas poses estáticas.

## Límites y comprobaciones propuestas

- Priorizar centros de ambos ojos, boca y mentón; usar puntos del cabello sólo como control secundario.
- Aceptar una similitud uniforme pequeña si mejora simultáneamente ambos ojos y boca. Si exige más de 3% de escala o 2° de giro, revisar correspondencias antes de aplicarla.
- Comprobar el marco transformado de la foto: con una traslación de unos 30 px, el rectángulo de foto queda dentro del lienzo de 1080; no hace falta recortar ni reescalar el arte. La foto ya tenía corte de su dibujo original, no debe inventarse arte para ocultarlo.
- Comparar puntos antes del fundido (0.600 s), a mitad (0.867 s) y después (1.150 s). Los puntos del rig deben permanecer inmóviles y los de la foto deben coincidir desde el primer cuadro, sin descenso progresivo.
- Revisar los cuadros alrededor de 0.700 y 1.100 s para descartar una doble imagen facial o un salto.
- Confirmar estructura: todos los campos de V8 salvo encuadre base/claves de id 2 permanecen idénticos y todos los medios del ZIP conservan SHA256. Después de 1.100 s las imágenes renderizadas deben coincidir con V8 (salvo variación del codificador) porque la capa modificada ya no está activa.

No se ha tocado Android, teléfono, fuentes de producción ni vídeo final desde esta revisión. No se afirma coincidencia exacta del redibujo con el original.
