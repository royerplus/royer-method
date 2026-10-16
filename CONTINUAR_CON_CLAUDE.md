# Continuar la réplica de testprueba1

El usuario pidió cerrar esta sesión pronto por la cuota y que Claude continúe.
**El trabajo sigue incompleto. No presentar la primera exportación como réplica final.**

## Lo que pide el usuario

Recrear los movimientos, animaciones, transiciones, efectos y tiempos del vídeo de referencia en esta app Android, usando sus doce fotos y reutilizando el intro original. Añadir las funciones que falten, con tiempos, recortes, intensidad y keyframes editables. La vista previa y la exportación deben ser fluidas.

El usuario rechazó la primera versión por ser una adaptación. Su corrección principal es exacta: después del intro, la FOTO siguiente se divide en superior/inferior, entra desde arriba/abajo y se reúne. El intro mantiene su tamaño y queda tapado. NO aplastar el intro. Esa entrada vuelve a aparecer sobre H en el segundo ciclo.

## Archivos y entorno

- Repositorio: `C:/Users/PC/Downloads/royer-method`.
- Materiales, análisis, scripts y resultados: `C:/Users/PC/Downloads/royer-method-work`.
- Material original copiado del teléfono: `.../royer-method-work/testprueba1`.
- Referencia: `675cab0eadd1705a9794a2f651390bed.mp4`, 576×768, 30 fps, 17.7 s. Montaje hasta13.7 s; outro de plataforma13.7–17.7 excluido.
- Las doce JPG son otras imágenes/personaje. Se copia la mecánica del movimiento, no sus píxeles. Algunas imágenes de la referencia tienen animación interna de ojos/boca, que no se deduce de fotos estáticas distintas.
- PowerShell; Java21: `C:/Users/PC/Downloads/EditorPro-main/EditorPro-main/.local-tools/jdk21/jdk-21.0.12.1+1`.
- SDK: `C:/Users/PC/AppData/Local/Packages/Claude_pzs8sxrjxfjjc/LocalCache/Local/Android/Sdk`.
- ADB: `<SDK>/platform-tools/adb.exe`.
- Python: `C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
- OpenCV/numpy/imageio_ffmpeg instalados en `.../royer-method-work/python`; scripts agregan esa ruta a sys.path.
- FFmpeg: `.../royer-method-work/python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe`.
- Gradle funciona offline, NDK27/CMake3.22 instalados y local.properties configurado.

## Teléfono e instalación

- Infinix X6871 / GT20 Pro, Android35, serial `1243225514100703`.
- El usuario lo reconectó por USB en esta sesión; ADB volvió a detectarlo y pantalla Awake.
- App original `com.royermethod.app` tiene otra firma. **No desinstalarla ni reemplazarla.**
- Variante de pruebas autorizada: `com.royermethod.app.prueba`, etiqueta `Royer Method · Prueba`.
- `prueba` hereda optimizaciones release y firma local debug. `debug` usa el mismo suffix. `app/proguard-prueba.pro` conserva clases editor/Kotlin para instrumentación.
- APK actualmente instalada corresponde al intento anterior, con corrección de MP4, pero NO contiene las nuevas piezas ni el nuevo reloj.
- APK objetivo: `app/build/outputs/apk/prueba/app-prueba.apk`.
- APK test: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- Directorio test en teléfono: `/sdcard/Android/data/com.royermethod.app.prueba/files/pruebas`.
- Ya hubo problema de permisos al crear esa carpeta desde adb shell. Se resolvió sólo allí con chmod2777 y archivo .royer644. La app necesita escribir informes/mp4 en esa carpeta.

## Cambios de código existentes

Todo está guardado en el checkout, sin commit ni push. El repo estaba limpio al comienzo. No hay PR solicitado.

1. Modelo de efectos temporales independientes `EfectoVideo`, persistencia, recorte/división/velocidad con fase conservada, panel Efectos, barras FX en timeline. Tipos incluyen iris, bordes espejo, blur direccional, RGB, luz, etc.
2. Precisión mínima de1fotograma (33.333 ms), controles de duración/keyframes/Bézier y formato3:4.
3. `PaqueteProyecto`: ZIP .royer portátil con proyecto.json y medios, import/export SAF y validación de rutas/límites.
4. `SincronizacionVideo.kt` + `Contenedor`: filtra flags sync falsos del encoder examinando NAL IDR/IRAP. Primer MP4 lineal estaba bien pero fastseek fallaba porque stss marcaba también B-frames como sync. Hay pruebas unitarias, **falta verificar un nuevo MP4 real con búsquedas**.
5. Nuevo `PiezaVideo`, `RegionUv`, `Clip.piezas`, keyframes propios y persistencia. Mantienen el mapa de textura completa: cortar una mitad NO vuelve a llenar el lienzo. Encuadre de pieza es delta en pantalla. Panel Partes permite UV, posición, escala, giro, opacidad, claves/curvas, recortar/dividir/velocidad conservando trayectoria.
6. Shader CAPA: `uConRegionFuente` / `uRegionFuente`, límites internos complementarios sin franja alfa doble. Bordes externos0/1 quedan abiertos para reflejo de textura.
7. Efectos `progresos: List<ProgresoEfecto(tiempoUs,valor)>`, interpolación lineal medida para iris y nuevo `REVELAR_HORIZONTAL`. Panel Efectos permite registrar/quitar/restablecer puntos. Su recorte conserva reloj en lugar de borrar fase.
8. Fusión de piezas cuando cubren toda UV sin solape y matrices/opacidades de TODAS muestras son idénticas: elimina costura y coste tras reunirse.
9. Motor general de piezas con blur: composición de piezas por instante y luego promedio, usando scratch+acumulador FBO. **Ver estado de validación al final de este documento; era el último cambio en curso al preparar el traspaso.**
10. Nuevo `RelojAudio`, `SalidaAudio` con AudioTimestamp/contador real de muestras escritas, estimación continua acotada y fallos/underrun. `Previsualizador` usa Choreographer en hilo GL, dibuja sólo al cambiar fotograma y protege carreras de play/pausa/búsqueda. Instrumentación registra CPU/swap y asocia SurfaceTexture.timestamp a marca EGL exacta.

## Fluidez: diagnóstico real y lo que falta

Benchmark anterior optimizado: 322 cuadros únicos en13.785s, ~23.39fps; 89 saltos. Los incrementos de reloj eran exactamente2048muestras/48kHz =23.4375Hz. El reloj crudo AudioTrack.playbackHeadPosition limitaba toda la vista previa, incluso planos estáticos.

También hubo un gap de presentación~968ms cerca de2.2–3.2s que necesita revisión con las nuevas métricas. No atribuir todo a GPU ni prometer30fps antes de medir.

La corrección está escrita con10pruebas nuevas puras, pero falta benchmark en teléfono. Se corrigieron carreras play→pause antes del arranque y seek→pause con reloj anterior: el reloj tiene dueño por orden y búsqueda.

## Datos forenses nuevos: usar éstos

Todos están en `.../royer-method-work/analisis/forense`.

- `f-%04d.png`: cuadros COMPLETOS576×768 desde original141=4.7s. **archivo = frameOriginal−140**. Ejemplo original159→f-0019.png.
- `ajustes-medidos.json`: registro SIFT con trainIdx únicos y ECC, poses por plano A–F, stableFrame, confianza y matrices. x/y son fracciones de576/768; escala relativa a stableFrame; giro horario. Los planos borrosos sin medida deben quedar explícitos, no ceros inventados.
- `gh-final-mediciones.json`: root midió G/H, ambos ciclos, y luminancia por cuadro del fade final. Incluye poses y final[].
- `entrada-mitad-mediciones.json`: A1, frames149–159, bordes y offsets de superior/inferior.
- `entrada-mitad-segunda.json`: A2, frames261–269; confirmar archivo final guardado por el analista.
- Otros JSON contienen mediciones crudas y candidatos. No confundirlos con datos finales.
- `mediciones.json/csv` del primer SIFT tiene matches duplicados y poses degeneradas: **NO usar valores de escala0 ni confiar ciegamente en él**.
- Contactos denso/ son miniaturas192×256 y causaron malas interpretaciones. Consultar siempre PNG completo.

Hallazgos comprobados:

- A1 entrada149..159: upperY=[−.4921875,−.48046875,−.46484375,−.4427083,−.4166667,−.375,−.3203125,−.2447917,−.1223958,−.0208333,0]; lowerY=[.4947917,.484375,.4674479,.4479167,.4192708,.3802083,.328125,.25,.125,.0234375,0]. Intro fijo debajo.
- A2 sobre H: el MISMO mecanismo, con su propia entrada más corta. H no se comprime.
- B acaba girando ~46° en185; C entra en187 con giro−56.79°, escala1.839 respecto194. El primer intento ±2° era incorrecto.
- C NO aparece en181: primer visible187 (6.233333s), y su agujero es negro uniforme. B186 es un ANILLO con centro negro, no círculo lleno.
- Radios exteriores B181..186: [386,349,310,268,222,174]px. Agujero C187..196: [138,117,97,77,60,44,30,18,9,0]px.
- Fórmulas del motor a576×768, intensidad1/fundidos0: cerrar r=484*(1-p); abrir agujero r=145.2*(1-p). Borde alpha0.5; suavidad2.804px.
- E prácticamente sin zoom/giro: movimiento XY curvo, no preset de impacto.
- G sobre F entra por barrido no lineal. Frontera x en232..240=[148,222,260,280,288,290,304,332,384]px;241todo576. G queda geométricamente quieta durante el barrido y se mueve después. Usar efecto REVELAR_HORIZONTAL con p=x/576, no Animacion.REVELAR basada en caja de foto.
- Segundo ciclo suele desplazarse+110frames; G tiene una pequeña diferencia en241/351 que conviene comprobar visualmente. GH tiene registro de ambos ciclos.
- Final379→405 se desvanece según luminancia medida;405..410 negro casi uniforme (~RGB8 por fuente/codec), no asumir recta900ms.

## Generadores y resultados

`.../royer-method-work/replicar_medida.py` es el NUEVO generador. Importa utilidades de crear_replica.py; consume los JSON forenses. Usa las12fotos, claves por cuadro, piezas independientes, iris medidos, barrido medido y fade medido. Sin sacudida aleatoria.

- Normal: `python replicar_medida.py` → `resultados/replica-medida-testprueba1.royer`, manifiesto, CSV, validación.
- `--diagnostico` → `diagnostico-medido.royer`: usa las imágenes stableFrame de la propia referencia para validar geometría contra la referencia, antes de exportar la entrega con fotos distintas. **Sólo diagnóstico, no entregar en lugar de fotos del usuario.**
- Sólo se extraen intro/audio con FFmpeg. El montaje final debe exportarse con la APP, no renderizarse externamente.
- El generador añade un disco negro nativo1frame sobre B186/296 para obtener anillo. Radio interior provisional165px, a confirmar con medición final del analista.
- Revisar que A normalice la escala/giro residuales de ECC: la animación interna de la cara falsea pequeñas deformaciones; tras unión s≈1/rot≈0 y XY medidos. Ver JSON final.
- Los frames sin medida directa quedan anotados en `*-validacion.json`; se interpolan o extrapolan al borde, no se ocultan como medición exacta.

Los archivos `resultados/replica-testprueba1.*`, `RESULTADOS.md`, `replica-testprueba1-LEEME.md`, `crear_replica.py` son del PRIMER INTENTO RECHAZADO. No son la entrega corregida. Primer export real pasó411frames30fps,0avisos, pero la mecánica visual estaba mal.

## Compilar y validar

PowerShell en repo:

```powershell
$env:JAVA_HOME='C:/Users/PC/Downloads/EditorPro-main/EditorPro-main/.local-tools/jdk21/jdk-21.0.12.1+1'
.\gradlew.bat --offline testDebugUnitTest assemblePrueba assembleDebugAndroidTest
```

La última batería completa ANTES de reloj/reveal/fusión/generalFBO:86pruebas pasaban. No confundirla con validación de los últimos cambios.

Instalar APK test y APK optimizada .prueba (la variante debug puede necesitar instalarse antes para el runner, pero prueba usa firma igual). No tocar com.royermethod.app.

Harness: `app/src/androidTest/java/com/royermethod/app/editor/ProyectoRealIntegrationTest.kt`.

```powershell
# Copiar paquete nuevo como replica.royer a la carpeta de pruebas.
# Runner: com.royermethod.app.prueba.test/android.test.InstrumentationTestRunner
# Clase: com.royermethod.app.editor.ProyectoRealIntegrationTest
# -e class '...#testImportarYExportarProyectoReal'
# Alternativa -e packagePath /ruta/absoluta/archivo.royer
# Luego ...#testPrevisualizarProyectoReal
```

El primer test importa a almacenamiento privado, guarda el proyecto por defecto (con backup previo), prueba undo/toggle y exporta por Exportador real.
Salidas fijas `pruebas/replica.mp4`, `replica-informe.json`. Copiarlas local con nombres NUEVOS para no confundir intentos.
Segundo test abre TextureView576×768, reproduce y guarda `preview-informe.json` con publicaciones/presentaciones precisas. La app queda en actividad de benchmark; relanzar editor al terminar.

Después:

1. Revisar vídeo real cuadro por cuadro: mitades149–159 y261–269, giros B/C, círculo/anillo186–187, reflejo D, barrido232–241 y342–352, final.
2. Exportar diagnóstico nativo si ayuda: comparar transforms/máscaras con referencia usando las mismas imágenes de registro, excluyendo marca de agua.
3. Verificar fastseek del MP4 nuevo y flags stss/NAL (el fix ya escrito aún no tuvo export nuevo).
4. Medir vista previa. Si quedan gaps, usar cpu/swap/SurfaceTexture del nuevo informe, no inferir frame presentado desde prev.tiempo.
5. Ajustar lo que no coincida. No afirmar exactitud sin comprobación.
6. Dejar nuevo proyecto cargado en Royer Method · Prueba, copiar entrega al teléfono si procede, entregar MP4, .royer, APK, informe de cambios/validación y límites restantes.

## Estado final del cierre

Consultar la nota añadida al final por Codex tras guardar los últimos agentes y ejecutar las comprobaciones que alcancen. No iniciar trabajo sobre una lectura parcial mientras todavía se estén escribiendo archivos.

### Cierre definitivo de agentes

Todos los agentes terminaron de escribir. Motor general2FBO está terminado, con media incremental GL_CONSTANT_ALPHA=1/(i+1) para evitar redondeo al sumar RGBA8; no validado aún en GPU. Nuevo reloj y carreras de pausa/búsqueda corregidos.

Forense final guardado:82poses, A159 normalizado s1/rot0; A2 empieza realmente259 y termina unión269. `entrada-mitad-segunda.json` y `ESTADO-FORENSE.md` existen. B186 anillo medido: media altura radial163..174.3px, pico168.5. Generador actualizado a A2desde259, disco interior163, y separación de ventanas GH para no interpolar entre ciclos distintos.

Se generó paquete nuevo de21capas/307claves/4piezas/24efectos; el script acaba de recibir esos últimos ajustes, por lo que conviene regenerarlo antes de usar. No está importado al teléfono ni exportado. Todavía NO existe vídeo corregido.

Compilación final iniciada: `testDebugUnitTest assemblePrueba assembleDebugAndroidTest`. Kotlin debug/prueba e instrumentación compilaron y testDebugUnitTest terminó sin fallos antes del cierre. Consultar nota de resultado final y los XML de app/build/test-results/testDebugUnitTest. No instalar ni asumir que la APK anterior contiene todo si build final no terminó.

**RESULTADO FINAL: BUILD SUCCESSFUL (1m14s). 99 pruebas,0fallos. APK prueba y APK instrumentación generadas. El paquete .royer se regeneró con los últimos ajustes (SHA256 a3f363188db62dfa2fde06117c67fc04630a11298276f2870e80a1ff88313de9). Nada nuevo instalado/exportado/medido en el celular. No hay compilaciones ni agentes trabajando. Próximo paso: instalar nueva .prueba e instrumentación, exportar y medir según instrucciones de arriba.**

## Estado al 2026-10-16 (Claude)

Hecho:

- Export 1 (resultados/export-medido-1): 411 fotogramas, 30 fps, 0 avisos, 0 irregulares. Revisado cuadro a cuadro: coinciden las mitades 149–159 y 259–269, el iris de B, el agujero de C, los giros, los bordes espejo, los barridos de G (232–241 y 342–352) y el fundido final. Fallaban los latigazos borrosos (172, 200, 228, 255–256 y +110).
- Persistencia: -0.0 se lee como 0 (commit 947577d); el test de exportar fallaba por eso.
- Fastseek: stss marca solo los IDR reales (1, 120, 240, 360); 8 búsquedas con ffmpeg dan el cuadro exacto.
- Fluidez de la vista previa: precarga de fotos en otro hilo (commit e0532d0). Pasó de 403 fotogramas / 29,06 fps / 6 saltos a 409 / 29,67 fps / 0 saltos. Solo quedan huecos de 100–150 ms al arrancar.
- Latigazos medidos en analisis/forense/latigazos-medidos.json y latigazos-diagonales.json (scripts en el scratchpad: latigazo.py y diagonal.py). El generador (royer-method-work/replicar_medida.py; copia previa en resultados/replicar_medida.antes-latigazos.py) mete un desplazamiento + DESENFOQUE_H/V apilado en esos cuadros, y deja los progresos de iris, agujero y barrido quietos mientras el obturador está abierto.
- Export 2 (resultados/export-medido-2, también en el teléfono en Movies/RoyerMethod/replica-medida-2.mp4): 411 fotogramas a 30 fps; los latigazos ya se ven, pero con rayas finas. El commit 916162e (corrimiento de muestras por obturador) debería quitarlas: NO probado aún en el teléfono.
- El teléfono se exporta en 4,9 s si la app está al frente. En segundo plano, "Hiber" de Infinix congela la app: lanzar MainActivity de .prueba justo después de am instrument.

Falta:

1. Instalar el APK nuevo, exportar (export 3) y revisar los latigazos (rayas) y el resto cuadro a cuadro.
2. Anillo de B en 186: posición y radio bien, pero más grueso y brillante que la referencia. Con el progreso quieto por obturador debería afinarse; comprobar. El brillo depende de la foto.
3. El intro queda congelado bajo las mitades (149–159); en la referencia se mueve. No se puede usar ese tramo del original porque ya trae las mitades.
4. B282 tuvo un ajuste flojo; se usa el valor de B172.

.royer actual: resultados/replica-medida-testprueba1.royer, SHA256 d3ee6fdab9440c1210e22684c3e99466c83b5cd61031f81dea579d92392da68d (no probado con el último APK).

### Actualización 2026-10-16, segunda sesión (Claude)

- Exports 3 a 6 en royer-method-work/resultados/export-medido-3..6. El mejor es el **6** (replica-medida-6.mp4, también en el teléfono en Movies/RoyerMethod): 411 fotogramas, 30 fps, 0 avisos, stss correcto.
- Rayas de los latigazos resueltas (commit 6b6acae): el desenfoque direccional toma una muestra cada ~3 px (16–64) y lleva ruido por píxel.
- Rastro del cuadro siguiente al latigazo: medido en analisis/forense/latigazos-residuales.json (173/283 60 px H, 201/311 100 px V, 229/339 100 px ~H, 241 100 px diagonal, 352 40 px V; 351 no ajusta). El generador lo añade como DESENFOQUE_H/V (copia previa: resultados/replicar_medida.antes-rastros.py). Medido sobre la salida de la app, da los mismos largos.
- No hay un obturador global en la referencia: hay cuadros que se mueven sin rastro (D202/204, E216), según analisis/forense/obturador-medido.json. No cambiar el obturador de 180°.
- El anillo de B 186 sale nítido, con radio 163–174. Es más brillante que el original por el contenido de la foto.
- Merge de 2169ce4 (Escena, máscaras): 103 pruebas OK. Diferencia media del video contra el export anterior fuera de los cuadros cambiados: menor que 1 de 255.
- Vista previa: 29,4–29,5 fps, 1–2 saltos. Tirones: ~200 ms al arrancar y 80 ms en el cuadro 194 (≈50 ms de CPU, antes 26). El cuadro 242 tarda 22 ms de swap por el desenfoque diagonal.
- .royer actual: SHA256 d284575fa5422e764a0d8cb48601b29ba60d1422b3c25131816fb8f3325f34f4.

Pendiente: el tirón del cuadro 194; el intro congelado bajo las mitades (límite del material); G del segundo ciclo (351) sin medida fiable.

## Estado final al 2026-10-16 05:05 (cierre de Claude; sirve para Claude, ChatGPT/Codex o quien siga)

**Entregable actual:** export 6, en `C:/Users/PC/Downloads/royer-method-work/entrega/` (replica-medida-6.mp4, replica-medida-6.royer, el APK .prueba que lo exportó, su informe y SHA256.txt). También está en el teléfono, en Movies/RoyerMethod/replica-medida-6.mp4. Revisado cuadro a cuadro contra la referencia: coincide todo lo listado arriba (mitades, iris y anillo, agujero, giros, espejos, barridos de G, latigazos con su rastro, fundido final). El generador vigente es `royer-method-work/replicar_medida.py` (no está en git).

**Commits nuevos SIN probar en el teléfono** (compilan y pasan 105 pruebas JVM):
- 88cc6d0: el Compositor suelta los decodificadores de video en otro hilo (debería quitar el tirón de 80 ms del cuadro 194).
- 7fab2d8, 9f42ea4, 5f1ebc9: arreglos del reloj de la vista previa (debería quitar el tirón de ~200 ms al dar play; detalle en cada commit).
- 9c1d031: prueba de diagnóstico `testDiagnosticoArranqueAudio`, que escribe `pruebas/arranque-audio.json`.

**Próximo paso exacto:**
1. Leer `C:/Users/PC/Downloads/EditorPro-main/CELULAR.md`; marcarlo OCUPADO.
2. Compilar (`$env:JAVA_HOME=...jdk21...; .\gradlew.bat --offline testDebugUnitTest assemblePrueba assembleDebugAndroidTest`). Instalar `app/build/outputs/apk/prueba/app-prueba.apk` y `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` con `adb install -r`. Nunca tocar com.royermethod.app.
3. La réplica ya está importada en la app .prueba. Correr `testPrevisualizarProyectoReal` (runner `com.royermethod.app.prueba.test/android.test.InstrumentationTestRunner`) y comparar contra `royer-method-work/resultados/export-medido-6/preview-1-informe.json` (29,46 fps, 1 salto, huecos en 3→4 de ~200 ms y en 193→194 de 85 ms).
4. Correr `testDiagnosticoArranqueAudio` si el tirón del arranque sigue.
5. Re-exportar (`testImportarYExportarProyectoReal`; justo después de lanzar `am instrument` hay que abrir MainActivity de .prueba con `am start`, porque "Hiber" de Infinix congela la app si queda en segundo plano). Confirmar que la diferencia media contra la export 6 es menor que 1/255 (los cuadros se comparan con ffmpeg + PIL; scripts de ejemplo en este documento y en el historial).
6. Dejar CELULAR.md en LIBRE con una línea de historial.

**Límites que no se pueden arreglar con el material:** el intro queda congelado bajo las mitades (149–159); el brillo del anillo de B depende de la foto; G del segundo ciclo (351) no tiene medida fiable (se usa 352).
