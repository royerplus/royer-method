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

(Histórico: cuando se escribió esta lista no había commit. Hoy todo está en git, en la rama `claude/project-thread-skalim`; el estado vigente está en «Herramientas de movimiento», al final.)

1. Modelo de efectos temporales independientes `EfectoVideo`, persistencia, recorte/división/velocidad con fase conservada, panel Efectos, barras FX en timeline. Tipos incluyen iris, bordes espejo, blur direccional, RGB, luz, etc.
2. Controles de duración/keyframes/Bézier y formato 3:4. La antigua precisión mínima de 1 fotograma (33.333 ms) ya no rige: ver «Herramientas de movimiento» al final.
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

## Errores confirmados por la revisión de código, aún sin arreglar

**Actualización de Codex, 2026-10-16 06:25 UTC:** los ocho errores de esta lista histórica ya están corregidos y probados en la carpeta local. Ver el cierre y las mediciones al final de este documento.

Una revisión independiente de los cambios de la réplica confirmó 14 errores, cada uno con su escenario comprobado. Ya están arreglados: los 2 de Escena (máscaras mezcladas en un fotograma y fondo de más con mitades juntas, commit 2169ce4) y los 4 del reloj (7fab2d8, 9f42ea4, 5f1ebc9). Quedan estos 8, en orden de importancia. Ninguno afecta a la exportación de la réplica.

1. **Cierre al tocar un efecto que empieza en el último fotograma** (`editor/ui/PanelEfectos.kt`, línea ~178; gravedad alta). Ejemplo: un video de 3 s con un efecto en [2.966.667, 3.000.000] µs, puesto a velocidad 2x. En el borde Fin queda minimo = inicio + 33.333 > maximo = duración. Entonces `coerceIn(minimo, maximo)` lanza IllegalArgumentException al mover el deslizador, al pulsar ±1 f o "Al cabezal". Arreglo: pasar `(efecto.inicioUs + minimo).coerceAtMost(c.duracionUs)` y, dentro de BordeEfecto, usar `coerceIn(minimo, maxOf(minimo, maximo))`.
2. **Alargar un efecto no alarga `duracionBaseUs`** (PanelEfectos.kt, `rango()`, línea ~101). Ejemplo: un Flash de 0,5 s pasado a "Usar todo el bloque" (3 s) con "Salida suave" desaparece a los 0,5 s, y un "Cerrar en círculo" se cierra en 0,5 s. Arreglo: calcular primero el desfase nuevo y luego `duracionBaseUs = maxOf(baseActual, nuevoDesfase + (fin - ini))`.
3. **Mover el inicio hacia atrás y volver corre el reloj del efecto** (PanelEfectos.kt, línea ~100): `desfaseUs = (e.desfaseUs + ini - e.inicioUs).coerceAtLeast(0)`. Ejemplo: con -1 f y luego +1 f, el iris medido queda adelantado un fotograma. Arreglo: permitir `desfaseUs` negativo (quitar el `coerceAtLeast(0)` aquí y en `Persistencia.efectosDe`). `fuerzaEn` y `progresoEn` ya acotan el tiempo transcurrido.
4. **Archivos importados huérfanos** (`EditorScreen.kt`, línea ~133). Cada "Abrir proyecto" copia los medios en `filesDir/editor/importados/<uuid>` y nunca se borran; tampoco se borran si se cancela a medias. Arreglo: al arrancar, si `proyecto.json` se leyó bien, borrar en IO las carpetas que ninguna `Origen.uri` del proyecto usa. En la importación, borrar la carpeta en `catch (CancellationException)` con `NonCancellable` y relanzar.
5. **Un "Guardar proyecto" fallido deja un .royer a medias** (`PaqueteProyecto.kt`, línea ~36). Si falla la última fuente, el ZIP queda válido pero con un medio de 0 bytes, y se puede abrir. Arreglo: en el `onFailure` de guardar, llamar a `DocumentsContract.deleteDocument(contentResolver, uri)`, y en `leer` rechazar los medios de 0 bytes.
6. **COPIA_EXACTA en mediump** (`editor/motor/Compositor.kt`, línea ~499). En GPUs Mali, las mitades en movimiento se ven más blandas a 1080×1920 y la nitidez salta al juntarse. Arreglo: el mismo encabezado `#ifdef GL_FRAGMENT_PRECISION_HIGH precision highp float; ...` que CAPA.
7. **El chip de punto de progreso salta 1 µs antes cuando la rapidez ≠ 1** (PanelEfectos.kt, línea ~208). Por eso no se puede quitar el punto y se crean duplicados. Arreglo: calcular la ida y la vuelta en Double (`ceil`/`roundToLong`) y comparar con una tolerancia de `ceil(rapidez)`.
8. **Borde de corte de las piezas dentado** (`Sombreadores.kt`, línea ~314, `step()`). Una pieza girada y quieta tiene un borde en escalera. Arreglo: usar una rampa suave como en `uRecorte`, salvo en los bordes compartidos con otra pieza de matriz idéntica en esa muestra (un uniform vec4 por dibujo), para no crear costura. Gravedad baja.

Para probar en la nube sin Android se usó un arnés JVM (Compose de escritorio + android-all) que compila `app/src/main/java` y corre `app/src/test/java`. En el PC basta con `testDebugUnitTest`.

## Estado final al 2026-10-16 06:25 UTC (Codex; 2026-10-15 23:25 en Tijuana)

Se leyó el documento completo y se continuó desde `e6200a2`, en la rama `claude/project-thread-skalim`. Primero se trabajó sólo en la carpeta, por indicación del usuario; después autorizó explícitamente subir estos cambios a GitHub. Se consultó el remoto antes de publicar: seguía en `e6200a2`, sin divergencia. No se añadieron MP4, .royer, APK ni archivos de `royer-method-work` a git. En EditorPro-main únicamente se modificó `CELULAR.md` para reservar y liberar el teléfono.

### Vista previa, primero medida sobre e6200a2 y después con los arreglos

Se compiló e instaló exclusivamente `com.royermethod.app.prueba` y su APK de instrumentación. La app normal `com.royermethod.app` conserva exactamente versión, primera instalación y última actualización; las fichas antes/después están en el directorio de validación indicado abajo. No se desinstaló ninguna app ni se borró logcat.

- El cambio ya existente que libera decodificadores fuera del hilo de dibujo sí arregla el cuadro 194. En la medición final su CPU es **5,523 ms** (versión 6: **53,776 ms**); el intervalo de publicación 193→194 es **32,301 ms** (antes **84,587 ms**). La presentación Texture de ese tramo es **33,196 ms**.
- El arranque de e6200a2 todavía aceptaba una marca `AudioTimestamp` de cero muestras y luego detenía la imagen al recibir la primera marca real. `testDiagnosticoArranqueAudio` permitió medirlo. Se corrigió `RelojAudio`: sólo una marca con posición positiva puede establecer esa referencia; se conserva el tiempo negativo de una presentación comprometida en el futuro. Dos regresiones JVM cubren ambos casos.
- Medición final con `testPrevisualizarProyectoReal`: **411 cuadros publicados, cero saltos de índice, 29,674 fps** incluyendo la preparación; **410 presentaciones Texture, cero saltos, 30,008 fps**, con una presentación sin marca asociada. Desde el primer movimiento, los intervalos de presentación iniciales son 33,3–33,7 ms. Ya no aparece la congelación de ~202 ms después de haber empezado a avanzar.
- **Límite pendiente:** la primera presentación tarda **217,638 ms**. El cuadro inicial se publica a 18,340 ms y se espera a preparar el audio. No se afirma respuesta instantánea ni ausencia total de variaciones: el máximo intervalo Texture del resto de la prueba fue 68,475 ms. Reducir esa preparación requiere preparar decodificador/AudioTrack antes de play y gestionar su invalidación por edición, seek y cancelación; no se adelantó artificialmente el reloj.

Antes de tocar los ocho errores se reexportó para validar el reloj: diferencia media de los 411 cuadros RGB contra export 6 = **0,438442/255**. Cumple el límite solicitado.

### Ocho errores corregidos, empezando por el cierre del panel

1. **Panel de efectos y último fotograma acelerado.** Se limita el mínimo de Fin a la duración real y se defienden los controles contra límites invertidos. Cambiar Fin conserva el inicio aunque el tramo restante dure menos de un fotograma. Dos casos en `ControlesEfectoTest` prueban el video de 3 s a 2x y todos los caminos de acotación. Este arreglo y sus dos pruebas se ejecutaron antes de los demás.
2. **Extensión de duración base.** `EfectoVideo.conRango` amplía la base hasta el nuevo final, incluyendo el desfase. Regresión con Flash, fundido de salida, iris y efecto previamente recortado en `EfectoVideoTest`.
3. **Continuidad al mover Inicio.** Se permiten desfases negativos en el modelo y en `Persistencia`. Pruebas de retroceder/avanzar un fotograma, intensidad/progreso/reloj y guardar/abrir en `EfectoVideoTest` y `PersistenciaTest`.
4. **Importaciones huérfanas o canceladas.** `ImportadosProyecto` captura candidatas al abrir el editor y limpia en IO sólo si se pudo leer el guardado. Conserva referencias de principal, capas y audio, así como proyectos en deshacer e importaciones posteriores al snapshot. La limpieza por fallo/cancelación envuelve también la vuelta de `withContext`, se ejecuta con `NonCancellable + IO` y relanza la cancelación. Regresiones en `ImportadosProyectoTest`.
5. **Guardado parcial.** Fallar o cancelar elimina el documento SAF mediante `DocumentsContract.deleteDocument`. `PaqueteProyecto` rechaza medios de cero bytes tanto al escribir como al leer. Pruebas de última fuente ilegible, borrado del parcial, cancelación al retornar de IO, preservación del error original y ZIP con medio vacío.
6. **Copia de textura a alta resolución.** `COPIA_EXACTA` usa `highp` con fallback a `mediump`. Prueba GPU real a 1080×1920: **error medio 0/255**, precisión de fragmento 23 bits, con patrón de detalle de un píxel.
7. **Selección de puntos con rapidez distinta de uno.** Ida/vuelta en Double con ceil/round y selección del punto más próximo dentro de `ceil(rapidez)`; editar y quitar actúan sobre su tiempo original. Dos regresiones con seis rapideces, tiempos superiores a la precisión entera de Float, desfase negativo y el último microsegundo.
8. **Bordes dentados de piezas.** Rampa de un píxel en cortes expuestos; juntas complementarias sólo si las matrices finales coinciden en esa muestra. `CortesPiezas` subdivide únicamente los dibujos cuando el vecino cubre parte del borde, sin cambiar el proyecto ni la textura. Seis regresiones JVM y tres GPU de giro, junta completa y vecino parcial. GPU: 548 píxeles de cobertura parcial; diferencia máxima entero/mitades **1/255**; junta parcial **255/255** en 44 muestras y 68 píxeles suaves en la parte expuesta.

### Validación y archivos finales

Build final: `testDebugUnitTest assemblePrueba assembleDebugAndroidTest` **BUILD SUCCESSFUL**. **132 pruebas JVM, cero fallos**; las cuatro pruebas de `SombreadoresIntegrationTest` pasaron en el Infinix X6871, Android 35. También pasaron `testPrevisualizarProyectoReal` y `testImportarYExportarProyectoReal`. No hay builds ni pruebas del teléfono en ejecución.

El export final de la app tiene **411 cuadros, 30 fps, 576×768, 13,7 s, cero avisos de fuentes y cero cuadros irregulares**. Se decodificaron y compararon los 411 cuadros RGB completos contra `export-medido-6/replica-medida-6.mp4`: **diferencia media 0,867356/255 = 0,003401394 normalizado**, menor que **1/255 = 0,003921569**. No es identidad binaria: el peor cuadro tiene diferencia media 3,236122/255; el límite solicitado era la media del video completo. Esta medición compara con export 6, no vuelve a demostrar coincidencia con el video de referencia original.

Todos los resultados, fuera de git:

`C:/Users/PC/Downloads/royer-method-work/resultados/validacion-codex-e6200a2/`

- `preview-final-informe.json`, `preview-final.log`: última medición de vista previa.
- `replica-final.mp4`, `replica-final-informe.json`, `export-final.log`: exportación final, sin reemplazar export 6 ni la entrega anterior.
- `comparacion-final-export6.json`: diferencia completa, con media por fotograma.
- `shaders-final.log`: medidas de las cuatro regresiones GPU.
- `arranque-audio.json`, `preview-1-informe.json`, `preview-2-informe.json`: diagnóstico y mediciones previas.
- `comparacion-reloj-export6.json`: control de exportación antes de los ocho arreglos.
- `original-package-antes.txt`, `original-package-despues.txt`: ficha de la app normal sin cambios.

APK instalada: `app/build/outputs/apk/prueba/app-prueba.apk`, SHA256 `f59d257434716f81d4ec21999cf4378aafab2be539267772fb5dc3d6ba8ad2f0`.
MP4 final: SHA256 `7471ca3dab994943c60160f96e2e268fd798850ca9c8c29a1225164494282ca1`.
El paquete utilizado sigue siendo el de export 6, SHA256 `d284575fa5422e764a0d8cb48601b29ba60d1422b3c25131816fb8f3325f34f4`.

El proyecto quedó guardado en .prueba y se relanzó su MainActivity. Teléfono liberado en `EditorPro-main/CELULAR.md` con línea de historial. Se mantienen los límites de material anotados por Claude (intro congelado bajo mitades, brillo de B y falta de medida fiable de G351).

### Entrega al celular y publicación autorizada, 2026-10-15 23:31 Tijuana

El usuario pidió subir el código a la misma rama y enviar el MP4 a su galería. Se copió el export final a `Movies/RoyerMethod/replica-codex-8-arreglos.mp4`, sin reemplazar entregas anteriores. El SHA256 en el celular coincide con el archivo local y MediaStore confirma el video indexado (id `1000296182`, 3.649.960 bytes). La galería puede mostrarlo en la carpeta RoyerMethod.

Se comprobó también el APK instalado de `com.royermethod.app.prueba`: SHA256 `f59d257434716f81d4ec21999cf4378aafab2be539267772fb5dc3d6ba8ad2f0`, idéntico a la compilación validada. Última actualización de .prueba: 2026-10-15 23:21:37. La app normal sigue intacta.

Esta tanda corrige las herramientas existentes y su renderizado; no incorpora tipos nuevos de animaciones, transiciones ni efectos al catálogo.

## Herramientas de movimiento (pedido del 2026-10-16 07:25; fases A y B integradas y revisadas el 2026-10-18)

Rama `claude/project-thread-skalim`, publicada en origin. Las fases A (efectos, animaciones, claves y común) y B (vista previa, panel de efectos, panel de animación, línea de tiempo y ejemplos) están en 085d411. Los arreglos de la revisión van en los commits siguientes (ver «Revisión del 2026-10-18»). Primero el modelo; después, dónde está cada cosa en la app.

- **Efectos nuevos** (`modelo/EfectoVideo.kt`, `Escena.efectosVideo`, `modelo/CatalogoEfectos.kt`): MOVER, ZOOM, GIRAR, LATIGAZO, DESENFOQUE_DIRECCIONAL, CIRCULO (círculo o anillo con centro y grosor), BARRIDO y MITADES, que parte el bloque en dos piezas sintéticas. Se ajustan con `AjustesEfecto`: desde/hasta, ángulo, curva o Bézier, centro (también en ZOOM y GIRAR, que se quedan quietos en él), grosor, suavidad, invertir y corte vertical. Con sus valores por defecto, los efectos viejos (la SACUDIDA incluida) dan exactamente lo mismo que antes.
- **Combinaciones rápidas** (también son los ejemplos editables): Mitades que se juntan, Círculo que se abre, Anillo que se abre, Latigazo con desenfoque y Sacudida con desenfoque. El anillo va invertido: su banda deja ver lo de debajo y, al salir por las esquinas, la imagen queda entera. Cada efecto queda independiente, con su id, y comparte grupo con los demás de la combinación (`moverGrupo`).
- **Animaciones acumulables** (`Clip.animaciones`, `modelo/Animaciones.kt`): las entradas, salidas y continuas heredadas se unifican sin cambiar ni un bit. La duración inicial depende del tipo y del largo del bloque. `convertirEnEfecto` lo hace en un solo paso: quita la animación y pone un efecto ANIMACION. `limitacionesConversion` explica lo que deja de seguir al bloque.
- **Tiempo preciso**: `TiempoPreciso` (texto, leer, ajustar; un tiempo de fotograma se lee «≈1.067 s», y lo que se escribe y se guarda sigue exacto con `textoExacto`) y `AjustesEditor.ajustarFotogramas`, que se cambia en Personalizar > Ajustar a fotogramas. También `CampoTiempo` y `EstadoEditor.pausarEn`, que deja el cabezal exacto aunque se esté reproduciendo. El zoom fino lo forman `zoomFino`, `ZOOM_FINO_MAX` = 60 000 dp/s (1 ms = 60 dp), `ponerZoom` y `zoomNormal`. El reloj sale con milésimas en pausa.
- **Claves**: `Edicion.TOLERANCIA_CLAVE` = 16 ms (con el ajuste a fotogramas) y `Edicion.TOLERANCIA_FINA` = 0,2 ms (sin él); `toleranciaClaves()` elige una u otra. Con la fina pueden convivir claves en 0, 0,001 y 0,005 s. Herramientas: `Edicion.claveCercana` y `Edicion.moverClave`, con sus equivalentes para partes en `PiezaVideo`.
- **Simplificador** (`modelo/Trayectoria.kt`; `Edicion.simplificarClaves`, `restaurarClaves`, `descartarOriginales` y las versiones `…Pieza`): guarda el original en `clavesOriginales`, que se persiste en el clip y en la parte.
  - Tolerancia por defecto: 0,5 px de exportación y 0,5/255 de opacidad.
  - Métrica: `hypot(Δx·W, Δy·H) + R·|Δescala| + R·|Δgiro|·π/180`, con W×H las medidas de exportación y R la media diagonal. Se mide en los centros de fotograma a 30 fps, en las muestras del obturador (como `Escena`) y en los tiempos de clave.
  - Es exacta para desplazamientos; para escala y giro es una aproximación.
  - Limitación: una sola curva por tramo para los cinco canales, así que las curvas en el plano conservan muchas claves.
  - Solo trata las claves de bloque y de parte (`DuenoClaves.Bloque/Parte`). Los puntos de progreso de un efecto no se simplifican ni tienen «Volver al original».

### Dónde está cada herramienta en la app (fase B)

Rutas: `U = app/src/main/java/com/royermethod/app/editor/ui`.

- **Efectos** (bloque elegido > Efectos; hoja «Efectos de video»; `U/PanelEfectos.kt`, `U/FranjaEfectos.kt`, lógica pura en `U/TramosEfecto.kt`):
  - Arriba, fija, la franja: una mini línea de tiempo del bloque con un carril por solape, la barra elegida en blanco y el cabezal. Tocar una barra la elige; tocar otra vez en el mismo sitio pasa a la de debajo, así se llega a todas aunque compartan carril. La elegida se agarra con holgura (28 dp de alto, asas de 24 dp), aunque el dedo caiga en el carril de al lado: para arrastrar otra que coincide con ella, tocarla antes. Arrastrar el centro mueve el tramo entero (conserva duración, reloj, puntos de progreso y fundidos); los bordes recortan sin mover el reloj. Con el imán se pega a fotogramas. Cada arrastre es un paso de deshacer. Tocar la regla o arrastrar en vacío mueve el cabezal.
  - «+ Agregar» (pasa a «< Volver») abre el catálogo por grupos y la pestaña «Combinaciones rápidas». Lo nuevo se pone en el cabezal (si está fuera del bloque, en su borde, y el cabezal va allí) y queda elegido.
  - Avisos en rojo bajo la cabecera (`TramosEfecto.mascarasDelante`, `seVeAlExportar`): una máscara tapada por otra puesta antes (el motor solo usa una a la vez) y un efecto tan corto que no cae en ningún fotograma exportado.
  - Del elegido se ven siempre Inicio (mueve el efecto entero), Duración e Intensidad. Debajo, plegables: Tiempo (Fin, Recortar inicio, Al cabezal, Fin al cabezal, Todo el bloque y, si es de una combinación, Mover el grupo), Forma (lo que da `CatalogoEfectos.parametros`), Entrada y salida (fundidos) y Avanzado (rapidez y puntos de progreso). La cabecera tiene encender, probar, duplicar, papelera y «Separar del grupo».
- **Animar** (bloque elegido > Animar; `U/PanelAnimacion.kt`, `U/LogicaAnimacion.kt`):
  - Fila del título: «Animar · Animaciones (n) ▾ · + · ✓». El desplegable lista las aplicadas: tocar elige, ✕ quita, y la elegida trae «Ajustar» y «Convertir en efecto».
  - «+» suma la siguiente que se toque y se apaga solo. Sin «+», tocar otra sustituye a la elegida: si su duración no se había tocado, toma la sugerida de la nueva; si se tocó, se queda (`LogicaAnimacion.duracionAlSustituir`).
  - Fijas arriba: las pestañas Entrada/Salida/Siempre y la duración de la elegida (CampoTiempo). Debajo se desliza el catálogo, con un filtro de categorías («Todas» es lo de siempre). Las puestas llevan PUESTA y la elegida ELEGIDA.
  - Al aplicar: pausa, cabezal al comienzo del bloque y duración sugerida (`duracionSugeridaUs`). Los ajustes posteriores no mueven el cabezal ni pisan la duración. En Personalizar ya no hay «Animación nueva».
  - Si la animación dura más que el bloque (se recortó después), se ve su duración real, una nota dice qué parte no se ve, y Empieza/Termina de las piezas cuentan sobre esa duración, como la escena (`LogicaAnimacion.baseTramos`).
- **Convertir en efecto**: en el desplegable o al final de «Ajustar». Un diálogo enseña `limitacionesConversion`. Es un paso de deshacer y abre Efectos con el efecto nuevo elegido (`estado.efectoAElegir`). Como efecto, sus piezas ya no se ajustan una a una; el diálogo y la descripción lo dicen.
- **Atrás del sistema** (`EstadoEditor.alAtras`/`atras`, `atrasEditor` en `ui/screens/EditorScreen.kt`): cierra primero lo de dentro del panel (en Animar, el desplegable, luego el «+», luego «Ajustar»; en Efectos, el catálogo), después la hoja, después la selección y por último sale del editor.
- **Línea de tiempo** (`U/ZoomLinea.kt`, `U/ClavesLinea.kt`, `U/LineaTiempo.kt`):
  - Zoom: «−» y «+» abajo a la derecha. Mantener «+» 2 s, con un anillo que se llena y la pista «Manten para zoom fino», desbloquea hasta 1 ms = 60 dp. La pastilla «Zoom normal», encima de −/+, vuelve. El pellizco respeta el límite que toque. «+» y «−» llevan semántica para TalkBack (Ampliar/Reducir zoom y la acción «Zoom fino»).
  - Regla de 1/2/5 ms hasta 5 min, con milésimas por debajo de 0,1 s y rayas de fotograma.
  - Rombos del elegido: solo se coge uno con 16 dp libres alrededor (`ClavesLinea.aislada`); con una clave por fotograma, arrastrar sobre el bloque sigue moviendo el cabezal. Tocar lleva el cabezal a la clave; arrastrar la mueve (`Edicion.moverClave`, un paso, sin saltar a las vecinas, nunca contra el dedo y con el imán si está puesto). Si ya está más cerca de su vecina que el imán (16 ms), no se mueve y avisa «Demasiado juntas para el iman»: hay que apagar Ajustar a fotogramas.
  - Los bloques con efectos dicen «FX n · nombre de su combinación» (`TramosEfecto.rotuloBloque`).
- **Claves** («Claves» = panel «Fotogramas clave», y «Partes»): `NavegadorClaves` («<», «>» y una pastilla por clave) y «Momento de esta clave» con CampoTiempo. Sin clave en el cabezal, la sección «Instante» (imán y campo «Cabezal», en tiempo del bloque) lleva el cabezal a un instante exacto antes de «Poner uno aquí». El simplificador (`U/SimplificarClaves.kt`) está al final de Claves y en la pestaña Curva de Partes: chips de 0,5/1/2 px, la prueba en segundo plano con antes → después y el error medido, Aplicar/Cancelar y, con el original guardado, Restaurar u Olvidar. «Aplicar» nunca recalcula: usa la prueba hecha y, si el proyecto cambió en lo que ella mira, no aplica. Las cuentas van de una en una (`CuentasClaves.enTurno`).
- **Ajustar a fotogramas**: el chip está en Efectos > Tiempo, Claves y Partes, y el interruptor en Personalizar. Apagado, −/+ va de 1 ms (o de 10 ms con el chip «Paso») y las claves pueden estar a 0,2 ms.
- **Ejemplos** (Herramientas sin nada elegido, entre «Formato» y «Abrir proyecto»; la fila sigue fuera de pantalla y un fundido con flecha a la derecha la lleva al final; `modelo/EjemplosMovimiento.kt`, `cargarEjemplos` en `ui/screens/EditorScreen.kt`):
  - Pone al final 4 bloques de foto sintética y deja el cabezal parado en el primero. Las fotos A y B son PNG en `filesDir/editor/ejemplos`.
  - Bloque 1: «Mitades que se juntan» y «Mitades que se separan».
  - Bloque 2: una capa A sobre B con «Círculo que se abre», un anillo descentrado y «Círculo que se cierra».
  - Bloque 3: «Latigazo con desenfoque» y «Sacudida con desenfoque» (la sacudida de siempre).
  - Bloque 4: un barrido de entrada y un golpe de zoom con claves a 1,000 / 1,005 / 1,400 s. Con Ajustar a fotogramas puesto (lo de fábrica), las de 1,000 y 1,005 no se mueven por separado (al cogerlas sale «Demasiado juntas para el iman» y no saltan): para editarlas, apagarlo. El aviso al cargar los ejemplos lo dice.
  - Todo se edita con los paneles de arriba. Un deshacer quita los cuatro bloques.

### Banco de la vista previa (JVM de la nube, NO tiempos del teléfono)

`./gradlew :app:testDebugUnitTest -Dbench=1 --tests '*EscenaRendimientoTest'`. Sin `-Dbench=1` sus 2 pruebas se saltan. Escribe líneas `BANCO` y `build/banco-escena.json`. Lo hice en 4 CPU compartidas con otros trabajos: sirve para comparar proyectos o commits, no dice nada de la fluidez del teléfono.

| Proyecto | CPU de `Escena.cuadro`, µs/cuadro, media (máx), k=4 · k=8 | Asignado por cuadro, k=4 · k=8 |
|---|---|---|
| a, un vídeo | 0,6 (0,8) · 0,8 (0,9) | 2,2 KB · 3,4 KB |
| c, complejo, parecido a la réplica | 7,3 (15,8) · 12,0 (26,6) | 14 KB · 23 KB |
| d, como c con 300 claves por bloque | 75 (190) · 147 (336) | 263 KB · 506 KB |
| e, herramientas nuevas | 9,4 (29) · 15 (51) | 14 KB · 23 KB |
| f, como e con 300 claves | 36 (122) · 68 (238) | 118 KB · 229 KB |

- El peor cuadro medido fue de 0,5 ms, frente a los 33 ms de un fotograma.
- Al pulsar play, el modelo (plan de audio, escena y primer cuadro) cuesta menos de 0,3 ms.
- Mezclar los primeros 150 ms de sonido cuesta 9–20 ms con fuentes de 44,1 kHz y 4–7 ms con fuentes de 48 kHz. Es lo que retrasa el primer movimiento, junto con abrir el decodificador. Los ~200 ms son por diseño: la imagen espera al audio.
- GPU, contada y no medida: con «Mitades que se juntan» hay hasta 21 dibujos de lienzo completo por cuadro (33 con k=8). Con dos desenfoques direccionales, hasta 272 lecturas por píxel a 576×1024 (457 a 1080×1920).
- No se optimizó nada, porque no hay una causa demostrada. Hipótesis por confirmar en el teléfono:
  - H1: la vista previa se dibuja al tamaño de la pantalla.
  - H2: paneles que leen `estado.tiempo` al componer; quedan Transporte, PanelPiezas, PanelEncuadre y los puntos de progreso de Efectos.
  - H3: en pausa, los dibujos no se agrupan.
  - H4: el decodificador de vídeo se abre en el primer cuadro del bloque.
  - H5: copias `sortedBy`/`distinctBy` en cada llamada con muchas claves (`Clip.kt`, `PiezaVideo.kt`, `EfectoVideo.kt`).
  - H6: cada búsqueda sonando vuelve a arrancar el AudioTrack.
- Arreglos listos, que solo hay que aplicar si el teléfono lo confirma:
  - H3: un contador de cambios `@Volatile` en `Previsualizador.proyecto()`; en pausa, solo dibuja el último.
  - H5: listas ordenadas `by lazy`, con la guardia de `Escena.cuadro` idéntico en `ProyectosBanco` (las fotos bit a bit no están en git: ver «Estado al 2026-10-18»).
  - H1: dibujar a la resolución de exportación y dejar que la TextureView escale.

**Diagnóstico RoyerVista** (`editor/vista/DiagnosticoVista.kt`, apagado por defecto y sin coste):
- Encender, ANTES de pulsar play: `adb shell setprop log.tag.RoyerVista DEBUG`. Con `VERBOSE` mide además la GPU con glFinish, lo que frena un poco.
- Leer: `adb logcat -s RoyerVista`. Apagar: `setprop log.tag.RoyerVista INFO`.
- Cada parada de play escribe estas líneas: tamaño y fps, arranque (sonido, primer cuadro, primer movimiento y primera presentación), cuadro ms (escena, cpu, gpu, swap), peores cuadros (con «al empezar un bloque»), publicación y presentación fuera de 25–42 ms, y gc.
- Al pulsar play escribe lo de la pausa anterior: cambios, dibujos y repetidos, y la espera en cola.
- La presentación real sale de `onSurfaceTextureUpdated` (EditorScreen).

### Pruebas pendientes en el teléfono (`com.royermethod.app.prueba`)

1. Vista previa con RoyerVista, en un proyecto sencillo y en la réplica:
   - Play unos 10 s y pausa. Anotar WxH, arranque, cuadro ms, peores cuadros, publicación y presentación, y gc.
   - Lo mismo con VERBOSE, y con altavoz y con Bluetooth.
2. H2: reproducir con Efectos, Animar, Claves y Partes abiertos (gfxinfo framestats o Perfetto) y comparar publicación con presentación.
3. H3: en pausa, sobre MITADES o un desenfoque direccional, arrastrar un deslizador 3 s y pulsar play enseguida. Si hay muchos «repetidos» y más de ~100 ms de espera, aplicar el arreglo H3.
4. H4/H5: reproducir la réplica entera. Si los peores cuadros caen «al empezar un bloque», son los decodificadores; si gc da bloqueantes o decenas de ms, es la basura de las claves.
5. Los 4 ejemplos reproduciendo y en pausa con VERBOSE, sobre todo «Mitades que se juntan» y los dos desenfoques direccionales. Mirar también el tiempo de crear los dos PNG, las uri `file:` en vista previa, miniaturas y exportación, guardar el .royer con esas fotos y volver a abrirlo, deshacer y rehacer, y dos toques seguidos en «Ejemplos».
6. Interfaz con el dedo:
   - CampoTiempo: teclado decimal, coma y punto.
   - pausarEn con sonido real.
   - Mantener «+» 2 s, y el pellizco más allá de ZOOM_MAX ya desbloqueado.
   - Coger y arrastrar rombos a zoom fino (radio de 12 dp, 16 dp libres alrededor) y, con una clave por fotograma, que arrastrar sobre el bloque mueva el cabezal.
   - Franja de efectos con 3 o 4 carriles de 13 dp: asas de 14 dp, la elegida con 28 dp de alto y asas de 24 dp, y «Toca otra vez» con 5 efectos solapados.
   - El desplegable de Animar y el diálogo de Convertir. El Atrás del sistema por capas: desplegable, «+», «Ajustar», hoja; en Efectos, catálogo y hoja.
   - La fila de categorías, que se desliza de lado dentro del desplazamiento vertical. El catálogo de Animar en un teléfono bajo: se ve en un hueco de unos 120 dp (U10, abajo).
   - El teclado sobre los CampoTiempo de la hoja: puede taparlos (U16, abajo).
   - La fila de herramientas con el fundido y la flecha, y los avisos largos (4,5 s).
   - «+» y «−» del zoom con TalkBack.
   - Que el retroceso no corte un arrastre a medias.
7. GPU: los uniformes `uMascCentro`/`uMascGrosor`, la precisión mediump del anillo y del centro, y una revisión visual del anillo invertido (en la nube solo se ha visto con SwiftShader). También barrido y revelar con el centro movido, y zoom y giro con centro.
8. Simplificar claves con miles de claves: cuánto tarda la prueba (en `Dispatchers.Default`, de una en una) y cuánto «Aplicar» (injerta las claves en el hilo principal), y si la vista previa sigue fluida mientras tanto.
9. Control: reexportar la réplica y compararla con la export 6. Debe dar 411 cuadros y una diferencia media menor que 1/255. En esta tarea sí cambiaron Escena (máscaras con centro y grosor, y en la revisión barrido/revelar con centro y zoom/giro con centro), Compositor (uniformes `uMascCentro` y `uMascGrosor`), Sombreadores (CAPA: `!= 0.0` y `abs()` en el desenfoque direccional, centro y grosor de la máscara) y Previsualizador (diagnóstico RoyerVista). No cambiaron Exportador ni la SACUDIDA. Con los valores por defecto los proyectos viejos dan lo mismo bit a bit en el modelo (ver «Estado al 2026-10-18»), pero en GPU solo se compiló con SwiftShader: si la export cambia, mirar primero esos cambios del sombreador y del compositor.

### Pendiente de código

- Las rayas FX de la línea de tiempo no responden al toque: para ir a un efecto concreto hay que abrir Efectos. El panel ya atiende `efectoAElegir` si alguien lo pone.
- Recortar y mover bloques en la línea arrancan con el retraso del touch slop; las claves ya no. Para igualarlos, empezar `acumulado` con `pasado.x`, como en las claves.
- El simplificador no se cancela de verdad: con `enTurno` no se juntan varias cuentas y la que se cancela esperando turno no corre, pero la que ya corre termina aunque nadie la espere. Arreglo: que `Trayectoria.simplificar` reciba `seguir: () -> Boolean = { true }`, lo mire en cada vuelta del bucle principal, y `CuentasClaves.enTurno` le pase `{ isActive }`.
- Un efecto ANIMACION (animación convertida) no deja ajustar sus piezas en Efectos; hoy solo lo avisan el diálogo y la descripción. Arreglo: en `SeccionForma` de `U/PanelEfectos.kt`, con `e.tipo == ANIMACION`, listar `e.animacion.piezas` con los controles de `Piezas` de Animar (cuánto, curva, empieza y termina) y escribir con `p.cambiar { ef.copy(animacion = ef.animacion.copy(piezas = …)) }`, en un paso de deshacer.
- Dos máscaras que coinciden: el motor solo usa la primera, y el panel avisa. Lo ideal sería combinarlas en el sombreador.
- Mover un grupo arrastrando una barra no existe, a propósito: cada efecto de una combinación se mueve por separado. El grupo se mueve con «Mover el grupo» (±1 f / 1 ms y «Grupo al cabezal»). Si se pide, un interruptor «Mover juntos» que haga que `ArrastreTramo.seguir` con MOVER aplique `conGrupoMovido`.
- U10, el catálogo de Animar en unos 120 dp: pasar `FiltroCategorias` al `fijo` de `HojaPanel` en `HojaAnimacion` y hacer crecer la hoja con Animar abierto (en `PantallaEditor`, `abajo = min(maxHeight * 0.55f, 420.dp)`). Redimensiona el TextureView de la vista previa: probarlo en el teléfono.
- U16, el teclado puede tapar los CampoTiempo (edge-to-edge sin `imePadding`): `Modifier.imePadding()` en la `Column` de `PantallaEditor` y `android:windowSoftInputMode="adjustResize"` en el manifiesto. No se hizo a ciegas porque encoge la vista previa en cada cuadro de la animación del teclado; probarlo en el teléfono.
- MITADES no trata bien un giro del bloque de más de 90°. Un APK viejo pierde `animaciones` y los efectos ANIMACION. `Edicion.agregarTexto` sigue creando la entrada heredada APARECER, que se unifica al primer cambio.

### Revisión del 2026-10-18

Una revisión independiente del commit 085d411 dejó 34 hallazgos (interfaz, modelo, Android y cobertura). Se arreglaron en dos trabajos y se unieron; lo que se ve en la app ya está descrito arriba, en su sitio. Además, en el modelo:

- Barrido y revelar con el centro movido no tapaban ni descubrían todo el lienzo; ahora sí (`Escena.barrido`, largo con el centro). Con centro 0 no cambia ni un bit.
- Zoom y Girar aceptan centro (`Parametro.CENTRO`; `Escena.sobreCentro`). Con centro 0, el efecto de siempre.

Lo que no se cambió: tocar el número de un deslizador lo lleva a su neutro a propósito (convención de toda la app, un paso de deshacer). Lo que queda está en «Pendiente de código» y en las pruebas del teléfono.

### Estado al 2026-10-18 y próximo paso

- **Compilado solo en la nube.** Arnés JVM: Compose de escritorio + android-all, sin SDK de Android ni AGP; compila `app/src/main/java` y corre `app/src/test/java`. GitHub Actions compiló el APK (`assembleRelease` con AGP y el SDK) de 085d411 y de b29df83 sin errores, pero esa CI no corre las pruebas. **No se ha corrido aún `testDebugUnitTest assemblePrueba assembleDebugAndroidTest` con Gradle y el SDK del proyecto.**
- **Pruebas JVM:** 361 en `app/src/test` (eran 337 en 085d411; 24 nuevas en la revisión): 359 pasan, 0 fallos; las 2 de `EscenaRendimientoTest` se saltan sin `-Dbench=1`. Las nuevas o cambiadas están en `ClavesLineaTest`, `AtrasTest`, `TramosEfectoTest`, `PanelEfectosLogicaTest`, `EjemplosMovimientoTest`, `HerramientasEfectoTest`, `TiempoPrecisoTest`, `ControlesTiempoTest`, `PanelAnimacionLogicaTest` y `SimplificarClavesTest`.
- **Fotos bit a bit** (FotoReferencia, FotoAnimaciones y FotoEfectos: lo que dibujan los efectos viejos, la SACUDIDA con varias intensidades, transiciones y piezas): siguen idénticas a las de la base 20d9e35. **No están en git** (la referencia ocupa 7 MB); están en el espacio temporal de la sesión en la nube, que se pierde con ella, así que nadie puede repetirlas desde el repo. Las guardias que sí están en el repo: `AnimacionesTest.iguales`, `ConvertirAnimacionTest`, `ClavesFinasTest.conLaToleranciaDeSiempre…`, `HerramientasEfectoTest` (efectos nuevos con los ajustes de siempre) y `EjemplosMovimientoTest.laSacudidaEsLaDeSiempre`.
- **Interfaz:** capturas con Compose de escritorio, también fuera de git. No sustituyen al dedo ni a TalkBack.
- **APK de GitHub Actions:** `.github/workflows/apk.yml` publica en la release «prueba» dos APK, firmados con la clave de depuración que vive en la caché de CI:
  - `RoyerMethodGitHub.apk` (https://github.com/royerplus/royer-method/releases/download/prueba/RoyerMethodGitHub.apk): build type `github`, que es `prueba` con otro id, `com.royermethod.app.github` («Royer Method · GitHub»). Es el que se instala desde el teléfono con el enlace (lo pidió Royer el 2026-10-18): va al lado de la app normal y de la `.prueba` del PC sin reemplazar ni chocar con ninguna, y cada compilación de Actions se instala encima de la anterior. En el APK publicado de 9cf32fa se comprobaron el id y el nombre.
  - `RoyerMethod.apk`: `assembleRelease`, o sea `com.royermethod.app` sin sufijo. **No instalarlo en el teléfono:** es la app normal con la firma de CI y, según la firma que tenga la del teléfono, la instalación falla o la reemplaza.
  - La variante del PC para las pruebas con ADB sigue siendo `com.royermethod.app.prueba` (`assemblePrueba` → `app/build/outputs/apk/prueba/app-prueba.apk`); las pruebas instrumentadas exigen `.prueba`.

**Próximo paso exacto** (en el PC, con el teléfono reservado en `EditorPro-main/CELULAR.md`):

1. Traer `claude/project-thread-skalim` y compilar: `$env:JAVA_HOME=...jdk21...; .\gradlew.bat --offline testDebugUnitTest assemblePrueba assembleDebugAndroidTest`. Tienen que pasar las 359 pruebas JVM.
2. Instalar con `adb install -r` `app/build/outputs/apk/prueba/app-prueba.apk` y `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Nunca tocar `com.royermethod.app`.
3. Hacer primero la prueba 9 de arriba (reexportar la réplica y comparar con la export 6) y luego la 1 (RoyerVista). Después, las demás de esa lista.

Sin el PC, Royer puede instalar `RoyerMethodGitHub.apk` desde el teléfono y hacer las pruebas con el dedo (6) y los ejemplos (5). Las que usan ADB (RoyerVista, gfxinfo, reexportar la réplica) necesitan el PC.
