# Head bop de test2xd

Rama separada: `codex/head-bop-test2xd`, basada en `6ca0760`. No se ha mezclado con el trabajo de Claude.

## Herramienta nativa

Seleccionar una capa y abrir **Animar → Head bop**. Los roles son cabeza, cabello, cuerpo y cada mano. Cada capa conserva sus controles: BPM, intensidad, giro, rebote, fase, pivotes, retardo/arrastre del cabello, intervalo móvil y fundido. El movimiento se evalúa por muestra en vista previa y exportación; no genera keyframes por cada golpe.

`Clip.headBop` es opcional y compatible con proyectos anteriores. Guarda sus parámetros; recortar, dividir o cambiar velocidad conserva el reloj musical. Los pivotes pueden quedar fuera del PNG (UV −1 a 2), necesario para que un flequillo recortado comparta el cuello de la cabeza.

El benchmark `testPrevisualizarProyectoReal` ahora respeta la proporción del proyecto, manteniendo lado corto 576. No se ejecutó un benchmark nuevo de fluidez: el usuario pidió dejar esa comprobación para después.

## Montaje y correcciones V4

Música extraída del video aportado por el usuario. Rejilla candidata 107,665 BPM; intro propia de despiece y ensamblado hasta 6,84 s, después head bop hasta 15,733333 s. El video musical enseña una construcción de ojos; el movimiento de cabeza/cabello/manos es una creación propia basada en técnicas de recortes y movimiento secundario.

Seis PNG separados: cuerpo, cabeza, flequillo, cabello trasero y ambos brazos. Se generaron mediante el editor de imágenes integrado y se reconstruyeron zonas ocultas; no son recortes idénticos píxel a píxel a la ilustración original. Autoría de la ilustración: RAGI.

V4, solicitada el 2026-10-17:

- Cabello trasero prolongado hacia abajo en PNG 1024×1536. Marco superior de referencia cuadrado; escala, centro y pivotes se recalculan para conservar el encaje sin deformación.
- Brazo elevado corregido con el generador: contorno de los dedos y unión inferior de ropa sin la segunda pieza de piel del hombro.
- Cabeza, flequillo y cabello bajan juntos 0,028 del lienzo antes del zoom, conservando el pivote de cuello. El brazo entra ligeramente bajo el torso.
- Cero capas de texto, incluido el signo +. La fotografía original se conserva intacta, incluido su crédito incorporado.
- Intro, música y dirección del montaje conservadas. Siete capas, seis herramientas HeadBop, cero efectos añadidos y cero claves de baile. Las claves corresponden a despiece, reunión, zoom y fundido.

## Estado de validación

Compilación `.prueba` e instrumentación realizadas. **152 pruebas JVM pasaron** (20 nuevas para HeadBop y su persistencia). Las cuatro versiones se exportaron en el Infinix X6871 con el motor nativo. V4 terminó con 472 cuadros, 1080×1080, 30 fps y 15,733333 s; cero avisos de fuentes y cero cuadros irregulares en el informe. Se inspeccionaron capturas nativas a 2,4 s y 8 s. La duración de proceso incluye una suspensión de Infinix al poner la app en segundo plano; no se usa como medición de rendimiento.

No afirmar coincidencia exacta con tutoriales ni ausencia de lag. La revisión de rendimiento y la comprobación táctil exhaustiva quedan pendientes por petición del usuario.

## Material local fuera de git

`C:/Users/PC/Downloads/royer-method-work/referencias/test2xd-20261017/`

- `entrada/`: foto y video del usuario, intactos.
- `assets/`: primeros PNG; `assets-v4/`: correcciones sin sobrescribir los originales.
- `crear_headbop-v4.py`, `proyecto-headbop-v4/`: generador, configuración, informe y proyecto editable.
- `validacion-v4/headbop-v4.mp4` y `headbop-v4-informe.json`: exportación nativa V4.
- `TUTORIALES.md`, `registro-assets*.json`: fuentes, registro y límites de medición.

Proyecto V4 SHA256: `4a62aaf97290cc4133e5e3c3dc61e035577fe939eb3ebc4eb482289941900836`.

No se incluyen MP4, .royer, APK, PNG generados ni material de trabajo en git. Las copias de assets dentro de `output/headbop-test2xd/` del worktree están excluidas localmente.

## Fuentes consultadas

- https://www.youtube.com/watch?v=_CS0uAhu60Y
- https://www.youtube.com/watch?v=Rm7d8ErZf8o
- https://www.youtube.com/watch?v=ry7oMZmazrU
- https://support.alightmotion.com/hc/en-us/articles/10536997444369-Layer-Parenting-and-Null-Objects
- https://support.alightmotion.com/hc/en-us/articles/10536934703889-Animation-Easing-Curves
- https://docs.live2d.com/4.2/en/cubism-editor-manual/divide-the-material/

Los tutoriales de YouTube sólo permitieron consultar descripciones indexadas; no se afirma haber visto sus secuencias completas. La referencia local sí se inspeccionó por fotogramas.

## Entrega V4

Video registrado en la galería del teléfono: Movies/RoyerMethod/headbop-test2xd-v4.mp4. Proyecto editable: Download/RoyerMethod/headbop-test2xd-v4.royer; también quedó como proyecto activo de Royer Method · Prueba. Hash SHA256 del video local y copiado: 84045cc34791642403b762e9924b92e030216df408b9ea67579878d56084a912. CELULAR.md liberado al terminar.
