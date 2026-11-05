# Traspaso: Claude juega Day R Survival (Day R Premium) en el celular de Royer

Royer (habla español, desarrollador móvil en México) le enseña a Claude a jugar Day R en su celular Android por un servidor MCP llamado **Micelu4** (herramientas: screenshot, tap, swipe, key, open_app, type_text, shell). `shell` corre como uid shell (como ADB). Estado al cierre: Micelu4 se desconectó (error 502), así que el celular no es alcanzable hasta que Royer lo reconecte. Los scripts están guardados en el celular, en `/data/local/tmp/`.

## Objetivo
Que Claude lutee edificios de una ciudad de forma autónoma y muy rápida: decide qué "habilidad" (script de shell, un solo comando) usar mirando la pantalla (~1 captura por segundo). Las habilidades son secuencias de toques y lecturas de pixeles con coordenadas siempre iguales. Royer quiere que Claude haga los pasos por sí mismo, no que le dé instrucciones.

## Reglas de trato
- No entrar al juego del celular sin su permiso cuando lo diga. Él también usa el celular (la app de Claude), y eso a veces saca al juego de la pantalla: si las capturas muestran la app de Claude o el launcher, reabrir el juego con `am start -n com.gm_shaber.dayrpremium/com.ansca.corona.CoronaActivity`.
- Ante una duda, tomar otra captura para confirmar antes de dejar la pregunta pendiente.
- Para lutear, nunca usar el anuncio/video, ni dinamita de pago.

## Pantalla y coordenadas
- Real: 1080x2436 vertical (launcher/Claude) o **2436x1080 horizontal (el juego)**. Las capturas llegan reducidas (~1.22x): multiplicar por 1.22 para obtener pixeles reales. Los toques usan pixeles reales.
- `screencap /data/local/tmp/s.raw` da un volcado crudo: cabecera de 16 bytes (u32 W, u32 H, formato, espacio de color) + RGBA. Pixel (x,y) en offset `16+(y*W+x)*4`; se lee con `dd bs=1 skip=OFF count=3 | od -An -tu1`. Tarda ~0.33 s. Todos los scripts verifican W=2436 (horizontal).
- Cámara del juego: deslizar con un dedo la mueve (`input swipe`, deslizar en sentido contrario al que se quiere ir). El zoom/ampliar es con dos dedos; no se logró desde shell (ver "Pendientes").

## Reglas del juego (Royer las dijo)
**Luteo**
- Se toca el edificio, el personaje camina y se queda encima; **el nombre aparece sobre el personaje**. Leer el nombre y decidir. Lupa (abajo, ~x1857,y1011 real) abre el menú de búsqueda.
- Cada edificio tiene hasta 2 fases de búsqueda; la 2ª suele estar bloqueada (puerta). Abrir **las dos fases primero**, y solo después recoger.
- Al terminar (Buscar en gris, "No queda nada"): pulsar **"Saquear a todos"** antes de recoger. Saquear cadáveres corre una acción "Buscar 0/4" de ~30 s; la pantalla debe quedarse en el juego, si no se corta.
- Puerta bloqueada: 3 botones (arriba, dos en medio) se tocan en secuencia con ≥0.3 s entre cada uno, en bucle ~4 s justo tras pulsar Buscar. **Nunca el botón de abajo** (siempre video/anuncio u otra cosa inútil). Dinamita de pago: ignorar (cerrar con X).
- Linternas: por defecto farol poseído (+35%). Elegir por número de **objetos con contorno dorado/naranja** (rareza más alta) en la vista previa: 3 o más → linterna de investigador; 1-2 → linterna potente (roja, +70%); 0 → farol poseído. Equipar justo antes de lutear, no al terminar de recargar pilas. Recarga: Inventario → Equipo → tocar la lámpara → Cargar. No abrir el menú de lámparas si ya está la correcta.
- Peso: tolerancia hasta ~1300 kg aunque pase de 1050 (barra en rojo); a partir de ahí hay que soltar algo.
- Ruinas y cráteres: nunca. Ver diseños prohibidos abajo.

**Qué recoger** (doble toque sobre el objeto lo recoge entero)
- Recoger todo menos: animales, cadáveres enteros, armas dañadas (tienen barrita verde a la izquierda), vehículos (dañados o no; hay muchos, Claude juzga), madera y mesas de madera, tubo, horno de ladrillos destrozado, piedra, gasolina roja, **televisores con el logo verde "?"** (los demás televisores sí), abejas. Objetos de evento (icono de calabaza, sin contorno; palos/estrellas de madera) **sí** se recogen. Las ramas no tienen contorno.
- Mecánica de cuadrícula: al recoger un objeto, el siguiente a su derecha ocupa su lugar (el último de una fila → el primero de la fila de abajo). Por eso, con una cadena de 1 (recoger) y 0 (saltar), el doble toque va siempre en la casilla = número de objetos saltados hasta ese punto. El cambio es casi instantáneo.
- Un objeto no recogible se queda en su casilla (se cuenta como saltado).

**Edificios prohibidos** (Royer mandó 5 imágenes): (1) cráter seco marrón con dos árboles pelados; (2) cráter con estanque verde/agua; (3) casa de madera marrón oscura con nieve y árboles pelados; (4) bloque gris-verdoso en ruinas, de varios pisos, con techo dañado y árboles creciendo alrededor; (5) edificio largo de madera oscura en dos secciones unidas, con nieve. Edificios con variante en ruinas: nunca. Evitar spots de evento como la farmacia ("Irse"). Válidos vistos: "Casa intacta", Biblioteca.
Dudas sin resolver: farmacia/evento, Imprenta, bloque amarillo cubierto de plantas, granero marrón.

**Combate**
- Combate obligatorio aleatorio (no el de edificios con enemigos, esos se pueden evitar): si ofrece **"Batalla automática"** → pulsarla → **Confirmar** → **Cerrar** (1 s entre toques; nunca "Extra", es anuncio). Luego se vuelve a la ventana anterior. La confirmación pide objetos (p. ej. un arma y 25 fantasmas); no se sabe si los gasta.
- Combate por turnos manual: 3 mejores armas preseleccionadas, 4 puntos de acción; primera acción = invocar aliado (objeto morado); la habilidad de abajo del aliado cancela un ataque enemigo 2 turnos (no jefes; cooldown 3-4); 6 casillas de turno (yo, aliados, enemigos); pasar turno da 3 puntos, no pasar con más de 3 guardados; primer movimiento gratis, extras 1 punto; rifle = arma del medio, más daño; habilidades 1 y 2 del personaje son automáticas y sin costo; pistola solo para activar la habilidad 1; no dejar que el escudo llegue a 0.

## Habilidades (scripts en `/data/local/tmp/`)
Todas se corren con `sh /data/local/tmp/<nombre>.sh`.

| Script | Qué hace |
|---|---|
| `estado.sh` | Imprime NO_HORIZONTAL, BATALLA, PUERTA, LUPA (Buscar verde), AGOTADO (Buscar gris) u OTRO. ~0.3 s. |
| `rareza.sh` | Lee rareza de las 8 celdas del menú de la lupa; imprime `1:COMUN … ORO=SI celdas: 7`. |
| `luz.sh N` | Elige y equipa la lámpara según N dorados; huellas en `luz_huellas.txt`; guarda la actual en `luz_actual.txt`. `luz.sh calibrar` imprime huellas de los 6 slots. |
| `preparar.sh` | rareza + (solo si hace falta) luz. |
| `puerta.sh` | 4 s de bucle con los 3 toques mientras detecte PUERTA (`DRY=1` imprime sin tocar). |
| `buscar.sh` | Pulsa Buscar si hay LUPA y lanza `puerta.sh`. |
| `saquear.sh` | Pulsa "Saquear a todos". |
| `lutear.sh` | Lazo de ≤60 s: LUPA→preparar+Buscar, PUERTA→puerta, BATALLA→batalla, AGOTADO→saquear y termina con `LISTO_PARA_RECOGER`. Empezar con la lupa abierta. |
| `recoger.sh FLAGS [pausa] [max]` | FLAGS = cadena de 1/0 por objeto en orden de cuadrícula. Hace los dobles toques predichos. `DRY=1` solo imprime. Freno de peso opcional con `GUARDA=1` (no usar: el icono ya sale en rojo y frena de más). |
| `batalla.sh` | Combate automático (ver arriba). |
| `px.sh x y …` | Imprime RGB de puntos. |
| `pinch.sh` | Intento de dos dedos con `uinput` (no funcionó). |

Flujo típico de una casa: tocar el edificio → leer el nombre en la captura → tocar la lupa (1857,1011) → `sh lutear.sh` → captura para ver los objetos → armar la cadena 1/0 → `sh recoger.sh <flags>` → cerrar la lupa con la X (2257,160). Tiempos medidos: puerta+búsqueda 7-12 s; recoger 7-13 objetos 2-3 s.

### Coordenadas reales clave (horizontal)
- Lupa: (1857,1011). Cerrar lupa/X: (2257,160); X del panel de lámparas: (2108,146).
- Menú de la lupa: Buscar (2071,825); "Saquear a todos" (814,135); icono de linterna (1754,800); botón Usar (1926,842); slots de lámpara en y=396, x = 375, 537, 700, 864, 1027, 1190.
- Cuadrícula de objetos, columnas x = 268, 436, 603, 770, 938, 1106, 1274, 1442; filas y = 270, 438, 605, 773.
- Puerta: botones (1976,345) arriba, (1742,536) izquierda, (2211,536) derecha; el de abajo (~y 664-684) NUNCA.
- Batalla: "Batalla automática" (890,739), "Confirmar" (1468,708), "Cerrar" (923,842).
- Un doble toque (2× `input tap` seguidos) tarda ~90 ms.

### Detección por color (RGB)
- Rareza (punto = extremo izquierdo de la barra de probabilidad), celdas 1-4 arriba, 5-8 abajo: y=410 y 634; x=1669,1833,1998,2162. Colores: COMÚN marrón (105,77,41), VERDE oliva (123,110,56), AZUL (97,104,120), MORADO (105,80,102)/(121,93,116), **ORO = naranja** (192,124,61)/(181,115,50). Clasificador: ORO si r≥165 y r-b≥100; MORADO si b-g≥15 y r-g≥15; AZUL si b≥r+8; VERDE si g*100≥r*83; si no COMÚN. Ojo: la celda 8 vacía puede leerse VERDE; no afecta a ORO.
- Estado: BATALLA = pixel (719,487) rojo (r≥70, g,b≤10) o verde (g≥40, r,b≤10). PUERTA = pixel (2010,645) con g≥190, b≤70, 140≤r≤215 y (1976,703) con g≥180, b≤70. LUPA = (2010,853) con r≥120, r-b≥60, |r-g|≤15 (Buscar verde-oliva). AGOTADO = (2010,853) y (1927,853) grises (90-150, canales iguales).
- Lámparas: huella = 9 pixeles RGB del icono grande del panel derecho, en (x,y) con x∈{1390,1434,1478}, y∈{160,195,230}; se compara por menor distancia. La lámpara equipada siempre pasa al slot 1, así que el orden de slots cambia; hay que leer los 6 cada vez. Si la correcta ya es la del slot 1, el menú muestra "En uso" en vez de "Usar": solo cerrar.
  Huellas calibradas (27 valores): FAROL `197 177 139 67 55 26 193 174 139 186 176 151 73 141 111 183 174 150 171 171 152 88 192 142 172 169 149`; INVESTIGADOR `209 168 124 17 10 5 215 140 39 43 26 12 44 30 19 227 139 35 216 153 102 89 53 21 215 150 98`; POTENTE `197 174 140 46 16 13 221 206 161 186 163 152 157 75 51 26 15 12 100 38 28 59 22 17 178 153 146`; CALAVERA `197 177 139 148 41 45 193 174 139 186 176 151 96 56 42 105 92 39 208 201 152 141 144 73 172 169 149`; ANTORCHA `209 186 145 213 192 163 254 231 161 207 185 145 212 191 160 205 183 144 204 181 137 208 185 152 201 176 135`; VELA `200 177 137 228 141 30 196 175 132 87 78 73 94 72 50 24 17 13 42 21 12 100 84 73 58 29 14`.
- Peso: icono en (1373,134): amarillo (241,172,0) = ok, rojo = sobre 1050. Hoy sale rojo siempre, así que el peso se lee de la captura ("997/1 050").

## Lo probado y lo que no
- Probado en vivo: rareza, luz (3 lámparas), puerta (2 casas), lutear.sh (3 casas completas), saquear, recoger (cientos de dobles toques), estado AGOTADO/LUPA.
- Solo probado con capturas de Royer (no en vivo): detección de BATALLA y batalla.sh, cambio de lámpara con dorados reales (hasta ahora 0 dorados en casas), reconocimiento de lámpara agotada (no se sabe cómo se ve).
- Lección: el saqueo de cadáveres de 30 s se corta si el juego sale de pantalla; avisar a Royer.

## Pendientes y problemas
1. **Micelu4 desconectado (502)**: reconectar antes de seguir.
2. **Linterna sin batería**: la barra del farol quedó casi vacía (amarilla). Recargar (Inventario → Equipo → lámpara → Cargar) antes de lutear.
3. **Peso 1 004/1 050 kg**; el límite práctico es ~1300 kg. Motor eléctrico (200 kg) quedó olvidado en la Imprenta.
4. **Zoom con dos dedos**: `input` solo manda un dedo. `sendevent` a /dev/input/event6 da permiso denegado; un dispositivo virtual con `uinput` (`pinch.sh`) corre sin error pero el juego no reaccionó. Opciones: servicio de accesibilidad/Shizuku del proyecto DroidBridge de Royer, o hacerlo todo con deslizamientos.
5. Siguiente reto sugerido por Royer: seguir luteando casas con la misma rapidez; cuidar no entrar en edificios prohibidos.
6. Idea de repositorio de GitHub: no se pudo crear (el token del entorno es inválido); Royer pidió subir "info y demás".

## Errores pasados (no repetir)
Usar linterna roja con objetos sin contorno dorado; recoger antes de abrir las 2 fases; no saquear a todos antes de recoger; tocar mientras el mapa se reacomoda (esperar/recapturar); confiar en coordenadas de la imagen reducida; dar por hecho que una acción funcionó sin captura; marcar edificios sin leer su nombre; frenar recogidas por el icono de peso en rojo.
