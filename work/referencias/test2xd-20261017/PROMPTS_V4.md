# Recortes corregidos V4

Modo utilizado: generador de imágenes integrado, edición con fondo transparente; no CLI. Originales conservados.

Archivos finales del proyecto:
- C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v4/brazo-elevado.png
- C:/Users/PC/.codex/worktrees/head-bop-test2xd/royer-method/output/headbop-test2xd/assets-v4/cabello-trasero.png

Conjunto de prompts finales (especificaciones de las dos ediciones):

## Brazo elevado
Use case: precise-object-edit.
Edit target: brazo-elevado.png; reference image: foto original del usuario.
Corrige únicamente la anatomía de la mano elevada: elimina el lóbulo/dedo extra que sobresale sin aparecer en la foto original y conserva los dedos legítimos al final de la mano. Limpia la unión inferior del hombro: elimina la segunda media luna de piel y el cuello duplicado, continuando la manga y su ropa blanca/oscura para que encaje bajo el torso. Conserva el estilo, colores, posición, tamaño del lienzo cuadrado y todos los contornos ajenos a estas dos correcciones. Sin texto ni nuevos objetos. Fondo realmente transparente, canal alfa.

## Cabello trasero
Use case: precise-object-edit.
Edit target: cabello-trasero.png.
Extiende solamente la parte inferior del cabello largo en un lienzo vertical 1024×1536: la composición cuadrada original ocupa los 1024 píxeles superiores, y los 512 inferiores continúan los mechones cobrizos de forma natural para que no se vea un corte al acercar la cámara. Conserva la coronilla, el volumen superior, el estilo de línea y sombreado, el color y las posiciones relativas del original. No añadas cara, cuerpo, ropa, texto ni otros objetos. Fondo realmente transparente con alfa.

Copias de entrega fuera de git: C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/assets-v4/