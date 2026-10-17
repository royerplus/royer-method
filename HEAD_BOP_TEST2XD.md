# Head bop de test2xd

Estado actual: **V8**, diez posiciones oculares y transición continua del parpadeo lento. Conserva movimiento V6, borrador V4 y anteriores; sin nuevos cambios Android.

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

## Borrador V4 y revisión V5 · 2026-10-17

El usuario aprobó la dirección de V4 y pidió guardarla como borrador antes de naturalizar la cabeza. Su paquete y MP4 permanecen intactos en `borradores/V4-aprobada/` del material local. En el teléfono, el borrador editable está en `Download/RoyerMethod/Borradores/headbop-v4-aprobado.royer`; conserva SHA256 `4a62aaf97290cc4133e5e3c3dc61e035577fe939eb3ebc4eb482289941900836`.

Nuevo control **Animar → Head bop → Naturalidad**, de 0 a 100 %. Mezcla la trayectoria clásica con una frase suave de cuatro golpes, giro más contenido, anticipación, recuperación asimétrica y cabeceo con proyección vertical máxima de 1,2 % alrededor del cuello. El cabello hereda también esa proyección y su arrastre usa la misma trayectoria retardada. No añade claves. Guardar, recortar, cambiar velocidad y copiar ritmo preservan el nuevo parámetro; copiar ritmo sincroniza Naturalidad para mantener compatibles cabeza y cabello. Valor ausente o cero conserva el perfil V4.

V5 cambia únicamente tres parámetros en cada una de las seis instancias: Naturalidad 0,9, giro 4,8° y rebote 0,010. Nueve medios idénticos byte por byte; 72 claves, intro, música, geometría y pivotes intactos. El generador reproducible es `crear_headbop-v5.py`, fuera de git; paquete `proyecto-headbop-v5/headbop-test2xd.royer`, SHA256 `58985dc1b7ac6eb34d65a6a4791809d6b8659b1ccebacbef9624df44fea6dea1`.

Compilación de .prueba e instrumentación correctas; **161 pruebas JVM pasaron**, incluidas compatibilidad V4, continuidad del perfil, pivote del cabeceo, unión del cabello, recorte/velocidad, persistencia y sincronización. Exportación final nativa V5: 472 cuadros, 1080×1080, 30 fps, 15,733333 s, cero cuadros irregulares y cero avisos de fuentes. Se inspeccionaron varias poses nativas entre 7,05–8,80 s y capturas a 8 y 13,2 s. No sustituye a la valoración del usuario sobre la naturalidad.

Se conserva el primer intento fallido: 472 procesados pero sólo 467 muestras codificadas y dos intervalos irregulares. Se había abierto MainActivity avanzada la exportación; no está confirmada la causa. La repetición con la actividad abierta desde el principio dio 472/472 y cero irregulares. No se alteró el codec a partir de esa única muestra ni se hizo un benchmark de fluidez; rendimiento y comprobación táctil completa siguen pendientes.

Entrega V5 en galería: `Movies/RoyerMethod/headbop-test2xd-v5-natural.mp4`; proyecto editable en `Download/RoyerMethod/headbop-test2xd-v5-natural.royer`, también activo en .prueba. Video SHA256 `89a18eb6a066ff9c0e8a9f6848fc0dfe46963d455475a422cf6a0f1a2bef91f7`. Informes, capturas y logs locales en `validacion-v5/`. Sólo se actualizó .prueba; las fechas y versiones de la app normal coinciden antes/después. CELULAR.md liberado.
## Corrección V6 · 2026-10-17

El usuario rechazó V5 por pérdida de movimiento y cabeza aplastada. En V5 coincidían dos reducciones de giro (parámetro 7→4,8 y atenuación del perfil natural), además de una escala vertical animada. V6 elimina por completo la escala/proyección del motor HeadBop: sólo usa rotación y traslación rígidas, incluido el arrastre del cabello. Naturalidad modifica fase, curvas, arco y recuperación; conserva la amplitud, con acentos de ±2 %. El perfil clásico con valor cero sigue pasando sus matrices de referencia V4.

El paquete V6 parte directamente de V4 aprobada. Giro 7° y rebote 0,012 originales en todas las partes; sólo se añade Naturalidad 0,8 a cabeza, cabello trasero y flequillo. Cuerpo y manos mantienen el movimiento clásico V4. Exactamente tres cambios en JSON, nueve medios idénticos y 72 claves idénticas; intro, música, geometría, pivotes y borrador aprobados intactos. Archivo local: `proyecto-headbop-v6/headbop-test2xd.royer`; generador `crear_headbop-v6.py`, ambos fuera de git. SHA256 del paquete: `a48b38588220bbd57cb0ceb089609f9611f3bfa484ed3efbf7816d498aff43f1`.

Compilación .prueba e instrumentación correctas; **163 pruebas JVM pasaron**. Las nuevas verificaciones recorren el ciclo para comprobar distancias/ángulos constantes en todos los roles, intensidades distintas y fuente rectangular girada, amplitud comparable a V4, cuello y herencia del cabello. La medición geométrica del test usa Double al invertir las matrices Float para evitar cancelación numérica; no se relajaron sus tolerancias ni se añadió deformación para satisfacerlas.

Exportación nativa V6 correcta al primer intento: 472 cuadros, 1080×1080, 30 fps, 15,733333 s; cero cuadros irregulares y cero avisos de fuentes. Se inspeccionó una secuencia de poses de 7,05–8,80 s y capturas completas a 7,12 y 7,67 s. Falta la valoración del usuario sobre la naturalidad; no se afirma coincidencia con una referencia ni se ejecutó un benchmark de fluidez.

Video local `validacion-v6/headbop-v6.mp4`, SHA256 `40fb35923f42fd485e5cf2930444c2a455522aad529560d436aff0d6d80e118f`. Entrega en galería `Movies/RoyerMethod/headbop-test2xd-v6-sin-compresion.mp4`; proyecto `Download/RoyerMethod/headbop-test2xd-v6.royer`, también activo en .prueba. Se comprobaron hashes, registro en galería y borrador V4 intacto. Sólo se actualizó .prueba, la app normal conserva fechas/versiones y CELULAR.md quedó LIBRE. No se suben medios, APK ni paquetes a git.
## V7 · parpadeo facial un poco lento · 2026-10-17

Última petición del usuario: añadir parpadeo lento conservando V6. Se prepararon con el generador de imágenes integrado tres poses oculares de la cabeza: 65 % abierta, 30 % abierta y cerrada. Los PNG completos se usan sólo dentro de dos regiones UV de ojos; el resto de la cara y el cabello siguen viniendo de los medios V6. Fuentes y prompts exactos en `PROMPTS_V7.md`, fuera de git, con copias de assets en el output excluido del worktree.

El proyecto usa únicamente herramientas nativas existentes: tres overlays con piezas UV, movimiento y HeadBop idénticos a Cabeza, debajo del flequillo. Las piezas animan sólo opacidad, con estados intermedios anidados para cubrir el ojo abierto durante el cierre. Cero cambios Android, cero compresión o giro relativo de las piezas oculares. Las siete capas originales conservan contenido/movimiento, sólo se recolocan filas; nueve medios idénticos y las 72 claves V6 intactas. Overlays: 36 claves de movimiento copiadas y 60 claves sólo de opacidad; diez capas en total. El generador es `crear_headbop-v7.py`, fuera de git, y los ajustes están en `proyecto-headbop-v7/ajustes-parpadeo-v7.json`.

Dos eventos: 8,150–8,810 s y 12,050–12,710 s; cada uno dura 0,660 s (cierre 0,240, pausa 0,100, apertura 0,320). No hay parpadeos durante la intro. Se conservan V4/borrador, V6, música, duración y energía del movimiento.

Se reutilizó la compilación V6 de .prueba (163 pruebas JVM aprobadas en esa compilación), sin recompilar ni instalar APK. La prueba nativa de importación/exportación V7 pasó: 472 cuadros, 1080×1080, 30 fps, 15,733333 s; cero irregulares y cero avisos de fuentes. Se revisó una secuencia de poses nativas entre 8,100–8,833 s y capturas completas cerrada/intermedia; sin una unión de parche evidente en esa revisión. Esto no equivale a una medición de fluidez, que sigue pendiente.

Paquete: `proyecto-headbop-v7/headbop-test2xd.royer`, SHA256 `1ff755a3ffbf5852cebddbf18c78ea6d1e1a7c7af19f8132307fe144585c4528`. Video: `validacion-v7/headbop-v7.mp4`, SHA256 `0f3c0b10f527be13339c4dca99c55225e02d210e9c77e3cb715e82e601982cbc`. Entrega: galería `Movies/RoyerMethod/headbop-test2xd-v7-parpadeo-lento.mp4` y editable `Download/RoyerMethod/headbop-test2xd-v7.royer`; hashes y registro en galería confirmados. Se reinició sólo .prueba al final para cargar el proyecto V7 guardado. La app normal conserva fechas/versiones, el borrador V4 mantiene su hash y CELULAR.md quedó LIBRE. Ningún PNG, MP4, .royer ni material de trabajo se sube a git.
## V8 · más posiciones reales de párpados · 2026-10-17

El usuario señaló que faltaban cuadros en el parpadeo V7. Se añadieron seis dibujos oculares intermedios con el generador integrado (no sólo más fotogramas del mismo dibujo). Diez estados contando el abierto original y cerrado; los seis nuevos PNG son 1254×1254 RGBA y se usan sólo en las dos regiones oculares existentes. Referencias y prompts exactos en `PROMPTS_V8.md` y sus registros A/B, fuera de git. El análisis de iris visible detectó una inversión entre archivos llamados55 y45; la secuencia final ordena sus posiciones por apertura real, conservando los archivos sin editarlos. Sus nombres son etiquetas aproximadas de generación, no mediciones de apertura anatómica.

Se mantienen 8,150–8,810 y 12,050–12,710 s: 0,240 s cierre, 0,100 s cerrado, 0,320 s apertura. Los nodos usan inverseSmoothstep global según apertura y rampas LINEAL entre dibujos, sin mesetas durante cierre/apertura. En vez de mantener nueve capas opacas apiladas, se apagan las cubiertas: sólo contribuyen dos poses por instante. Tres overlays pueden estar activos en un cruce oculto de 1 µs, sin cambiar la composición. Se validó avance en cada cuadro absoluto a 30 fps, mezcla normalizada y ausencia de mesetas intermedias; datos en `proyecto-headbop-v8/validacion-tiempos-v8.json`.

El generador `crear_headbop-v8.py` parte de V6, conservando siete capas originales, nueve medios y sus 72 claves. Nueve capas oculares heredan 108 claves de movimiento de Cabeza y 308 claves automáticas sólo de opacidad de piezas: 16 capas y 488 claves en total, sin claves nuevas del baile. Configuración de inicios, duración y poses en `ajustes-parpadeo-v8.json`; música, intro, movimiento, geometría y escala de cabeza V6 intactos. No se añadió un control Android nuevo: es un montaje con piezas y fuentes nativas existentes. Código y APK siguen siendo V6 (163 pruebas JVM aprobadas en esa compilación), sin recompilar ni instalar en esta revisión.

La exportación nativa V8 pasó: 472 cuadros, 1080×1080, 30 fps, 15,733333 s, cero irregulares y cero avisos de fuentes. Se pausó al quedar .prueba detrás de Instagram y terminó al volver a primer plano; su tiempo de proceso no sirve como benchmark. Se inspeccionaron 25 poses consecutivas a intervalos de un fotograma desde 8,133 s, una captura intermedia a 8,300 y el segundo cierre a 12,330. No se hizo un benchmark de fluidez ni se afirma naturalidad perfecta o coincidencia exacta con una referencia.

Paquete final `proyecto-headbop-v8/headbop-test2xd.royer`, SHA256 `3356ecf7112343bf8dd2a9990811044dd3339197fb766c78322914b9d5cb3a5b` (el primer hash V8 fue sustituido antes de exportar tras corregir el orden de dos poses). Video `validacion-v8/headbop-v8.mp4`, SHA256 `d9198e34c2575b0fd4f77c0600f6696a7c137bac3b4da968173a1bf25df4cd6a`. En galería: `Movies/RoyerMethod/headbop-test2xd-v8-parpadeo-fluido.mp4`; editable `Download/RoyerMethod/headbop-test2xd-v8.royer`, también cargado en .prueba después de reiniciar sólo esa variante. Hashes, MediaStore, borrador V4 y fechas/versiones de la app normal comprobados; CELULAR.md LIBRE. Medios, proyectos y archivos de trabajo no se suben a git.