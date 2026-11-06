> **EMPEZAR POR `GUIA_RAPIDA.md`** (y `REFERENCIAS.md` para las fotos: que recoger, que no, edificios): tabla situacion -> script, y la regla "si hay script, no tocar a mano".

---
name: dayr-exploracion
description: Jugar Day R Survival Premium en el celular de Royer (MCP Aaacelu): entrar a una ciudad, mapear sus edificios, planear la ruta edificio por edificio, lutear cada casa y resolver los combates que salgan. Se va completando con lo aprendido.
---

# Day R — exploración de ciudades (skill viva)

Esta skill ENLAZA lo que ya existía; no lo repite:
- `luteo_referencia.md` — traspaso: reglas de luteo, qué recoger, edificios prohibidos, scripts del celular (lutear.sh, recoger.sh, estado.sh…), coordenadas y colores.
- `mosaico.md` — cómo sacar una imagen de la ciudad entera (calidad fija, plan_mosaico, cobertura).
- `combate_notas.md` + `encuentro.sh` — combate por turnos (batallaD.sh / Bcv) y la ventana "Has visto a un enemigo".

## Reglas del usuario
- Abrir el juego si no está delante: `am start -n com.gm_shaber.dayrpremium/com.ansca.corona.CoronaActivity`. Royer usa el celular: si aparece Claude/Instagram/launcher, no pelear por la pantalla; avisar.
- Un toque en el mapa = el personaje CAMINA (gasta reloj/energía). Nunca tocar a ciegas.
- **Entrar a la ciudad (REGLA): ponerse en ZOOM 3 (`sh zoomnivel.sh 3`), apuntar a la ESQUINA del círculo de la ciudad y tocar ESA esquina, NO el centro.** Con un zoom más chico el toque puede caer fuera del borde (y el personaje se va al páramo).
  (Antes:) Se ve la ciudad y se evita caer sobre un edificio o cruzar edificios con enemigos hacia el centro.
- Antes de mapear: no estorbar/pisar los edificios a identificar (dejar al personaje en carretera/borde, ver mosaico.md).

## ZOOMS (anotado; verificar SIEMPRE con captura, no fiarse de `zoom_T`)
- El número guardado en `zoom_T` puede estar desfasado (el juego cambia solo el zoom al caminar lejos). Para fijar un zoom: DESHACER todo y luego subir:
  `sh zoomnivel.sh min` (tope de acercado) -> `zoomj.sh out 1218 540 450 1100 200` -> `zoomj.sh out 1218 540 450 1100 921` (alejar 1079 en total) -> `zoomj.sh in 1218 540 700 150 (150+X)` (acercar X px).
  X=341 -> T=738 (CALIDAD de mapeo, = `calidad.sh`). X=253 -> T=826 = ZOOM 3 (lutear / entrar a ciudad). Nivel 2 (ciudad completa) T=1652, nivel 1 = mundo.
- Comprobación visual del zoom 3: los caminos salen enormes y el círculo de una ciudad (aun sin descubrir) se ve muy grande (~1000+ px). Si el círculo mide ~510 px, el zoom NO está aplicado.
- Con un zoom más chico el toque a la esquina puede caer fuera del borde.

## ENTRAR A UNA CIUDAD (procedimiento exacto, REGLA)
1. Zoom 3 aplicado y verificado (arriba). Mover la cámara con `camara.sh` hasta ver a la vez al PERSONAJE y el círculo de la ciudad (sin paneles de la izquierda encima).
2. IDENTIFICAR primero: (a) posición del personaje (icono redondo con anillo verde), (b) centro y radio del círculo de la ciudad.
3. La esquina de entrada es el punto del borde del círculo sobre la RECTA personaje→centro de la ciudad (el borde más cercano en línea recta). Tocar ese punto justo DENTRO del borde (~90-95% del radio), nunca el centro ni otra esquina.
   MEJORA (usuario): reaplicar el zoom 3 (deshacer y subir) JUSTO antes de entrar, cada vez (el juego lo cambia solo), y centrar con la cámara al personaje y la ciudad lo mejor posible (ambos bien a la vista, lo más grandes) antes de calcular la recta. Una entrada algo floja es válida, pero se puede hacer mejor.
   Ejemplo hecho (Kolomna, funcionó): `zoomnivel.sh min` -> `zoomnivel.sh 2` (ver ciudad+personaje) -> `camara.sh` para centrar -> `zoomj.sh in 1218 540 700 150 976` (acercar 826 desde nivel 2 = zoom 3) -> `camara.sh` hasta ver personaje y arco de la ciudad -> medir personaje y radio -> tocar el punto del borde sobre la recta (x1.218 para pasar de captura reducida a píxeles reales).
4. Así el personaje camina en línea recta y llega bien a esa esquina. Luego captura para comprobar que quedó en el borde (idealmente sobre carretera).

## Flujo
0. MAPEAR = mosaico de tomas (mosaico.md: zoomnivel min/2 -> centro_ciudad.py -> camara.sh -> calidad.sh -> plan_mosaico.py -> tomas -> mosaico.py -> marcar ROJO no usable / VERDE usable). NO improvisar con capturas sueltas ni lutear antes de mostrar el mapeo al usuario.
   Antes de mapear dejar al personaje fuera de edificios (carretera/borde) y no moverlo durante las tomas.
1. Entrar por la esquinita. 2. Mapear (mosaico.md) y marcar edificios válidos/prohibidos (luteo_referencia.md).
3. Ruta: empezar por el edificio MÁS CERCANO al personaje al entrar y seguir edificio por edificio (vecino más cercano).
4. En cada edificio: tocarlo → leer nombre → lupa → `lutear.sh` → recoger (2 fases primero, luego "Saquear a todos").
5. Si la ruta cruza un edificio con enemigos sale "Has visto a un enemigo" → `sh /data/local/tmp/encuentro.sh`:
   Batalla automática; si dice "El enemigo es demasiado fuerte" (Mortal…) → OK → Ataque → batalla normal con batallaD.sh (pergamino, armas, turnos).
   Es normal que pase de la ventana a la automatizada de enemigos; algunos son muy difíciles y se pelean a mano.

## Trampas aprendidas
- Al cerrar la lupa la cámara se mueve: tomar captura ANTES de tocar el siguiente edificio (si no, el toque cae fuera y el personaje sale al páramo).
- recoger.sh a veces recoge solo el primer objeto (Casa intacta): comprobar con captura y reintentar.
- La lámpara puede quedar vacía ("+0%" con linterna "+"): recargar antes de seguir.

## LINTERNAS (regla del usuario, corregida)
- Antes de lutear, la lupa debe mostrar una linterna en el cuadro de abajo a la izquierda. Si el cuadro sale VACÍO (icono verde con "+", `+0%`), NO hay linterna puesta aunque `luz_actual.txt` diga otra cosa: hay que equipar.
  `preparar.sh` ahora lo comprueba primero con `sinlampara.sh` y, si está vacío, llama a `luz.sh` (antes se saltaba y buscaba sin linterna).
- `luz.sh N`: abre el panel "Selecciona una fuente de luz", mira la 1ª fila (6 celdas) y, si la que toca no está, CLICKEA las filas de abajo (2ª y 3ª, y=560 y 725): una celda con icono = hay objeto. Elige por dorados: >=3 investigador, 1-2 potente, 0 farol.
- Si no aparece en NINGUNA fila => le faltan pilas: `luz.sh` imprime `NO_ENCONTRADA_RECARGAR`. Entonces recargar: Inventario -> Equipo -> tocar la lámpara -> Cargar, y repetir.
- Nunca buscar sin linterna equipada (el +% de éxito baja a +0%).

## Orden por casa (probado en Zaraysk)
1. Tocar edificio -> esperar a que llegue -> leer nombre -> lupa (1857,1011) -> `sh preparar.sh` (linterna) -> `sh lutear.sh`.
2. Al terminar ("No queda nada"): `sh saquear.sh` DOS veces + `sh fin.sh 40`, y hacer captura: el saqueo añade objetos (medicinas...) y cambia el número de casillas.
3. Armar FLAGS por casilla con las exclusiones (tele "?", abejas, cadáveres, armas dañadas, vehículos) y `sh recoger.sh FLAGS`. Comprobar con captura que solo quedaron las excluidas.
4. Cerrar lupa (2257,160) y capturar ANTES de tocar el siguiente edificio.
- `lutear.sh` tarda ~1-2 min si hay puerta: la herramienta shell corta a los 60 s; el script sigue, así que capturar para ver el estado.
- Casa maldita (brillo verde): abre directo en puerta bloqueada; el nuevo puerta.sh (1 toque por comprobación) funcionó sin pegar a los objetos.

## NAVEGACIÓN AUTOMÁTICA entre edificios (sin leer nombres ni mirar el mapa)
- Idea: el mosaico de la ciudad es el "mapa"; cada edificio se anota UNA vez con sus coordenadas del mosaico (`zaraysk_ruta.json`, x,y en la vista 2/3, x1.5 = px del mosaico).
- Por tramo: 1 captura (la del tool screenshot) -> `python3 -I nav.py CAPTURA DESTINO` (DESTINO = nº de parada o parte del nombre; `--lista` las enseña).
  nav.py ubica la captura dentro del mosaico con SIFT (vale CUALQUIER zoom y posición de cámara: mide el zoom solo), convierte el destino a píxeles de pantalla y, si cae fuera de la zona segura (x 500-1980, y 150-930), manda primero `sh camara.sh DX DY` y luego toca el centro.
  Imprime: `sh camara.sh ..` (si hace falta), `input tap X Y`, `ESPERA N` (segundos de caminata). Ejecutarlos tal cual; NO leer nombres ni verificar edificio.
- Validado: desde una captura real predijo (1148,767) para la Casa maldita; el toque real que llegó fue (1160,770).
- Tras cerrar la lupa el personaje queda en el centro de pantalla (1218,540), pero NO se usa: nav.py no necesita saber dónde está el personaje.
- El zoom cambia solo (usuario, o el juego): por eso cada tramo usa una captura nueva y no se fía de un zoom guardado.
- Límites: necesita >=15 coincidencias (ciudad visible); si dice POCAS_COINCIDENCIAS, volver a nivel 2 (`zoomnivel.sh 2`) y reintentar. La posición de cada edificio anotado es a ojo sobre el mosaico (error ~20 px): para el 1º tramo de cada edificio nuevo conviene confirmar con captura que llegó; luego se corrige el JSON.
- Los edificios que haga el usuario a mano se marcan `hecha` y se quitan de la ruta.

## Puerta al abrir la lupa (error de 50 s corregido)
- Si al abrir la lupa sale la PUERTA, atacarla YA (`puerta.sh`), no preparar linterna antes: con la puerta delante no existe el cuadro de linterna y `preparar.sh` lo leía como "sin linterna" y recorría el panel de lámparas (~50 s sin tocar la puerta). `preparar.sh` ahora solo actúa si `estado.sh` dice LUPA o AGOTADO.
- Nuevo flujo en una orden: `sh casa.sh` (puerta primero; luego preparar + lutear en segundo plano, log `casa.log`). OJO: ese nombre pisó un `casa.sh` viejo sin copia; considerar renombrar a casa_rapida.sh.

## Puerta nueva y linterna (hecho)
- `estado.sh` reconoce 2 diseños de puerta (con video abajo / sin video: dinamita + número verde). Respaldo: estado_v1.bak.
- `preparar.sh` lee el icono de la linterna puesta (no el archivo): si es la potente roja y no hay dorados, cambia a farol. Probado en vivo (POTENTE -> FAROL).
- Flujo que funcionó: tocar edificio (nav.py) -> lupa -> `estado.sh` -> si PUERTA: `puerta.sh` -> `preparar.sh` -> `lutear.sh` -> `saquear.sh` x2 + `fin.sh` -> `recoger.sh` -> cerrar lupa -> captura.
- Contador de radiación (icono 24→0 abajo): se agota; con 0 hay que parar o recuperar.

## FLUJO SEMI-AUTOMATICO (casa_auto.sh + rc.sh, en el celular)
1. Ir al edificio: captura -> `python3 -I nav.py captura N` -> `camara.sh` si hace falta -> `input tap X Y` (esperar ESPERA s).
2. `(sh /data/local/tmp/casa_auto.sh >/dev/null 2>&1 &)` : abre lupa, puerta, linterna, lutear, saquear x2, fin. Log `casa_auto.log`, resultado `casa_auto.res`.
   Resultados: LISTO_PARA_RECOGER | PARA_LINTERNA (no hay linterna adecuada/sin pilas: decide Claude, recargar) | PARA_PANTALLA | PARA_TIEMPO.
3. Claude mira UNA captura, arma FLAGS con exclusiones y corre `sh rc.sh FLAGS` (recoge y cierra la lupa).
4. Captura y siguiente edificio. Casas ya saqueadas: casa_auto salta directo a saquear.
- No probado en vivo todavia (escrito con el celular fuera de juego).

## recoger.sh verifica cada recogida (error en Casa intacta)
- Hay objetos que NO se recogen con doble toque (ej. el huerto, primera casilla). Antes el script suponia que todo desaparecia y se corria la fila: con uno atascado, los toques siguientes caian desplazados (se llevo hacha y mesa que debian quedar y no llego al final de la lista).
- Ahora tras cada doble toque compara la cuadricula (md5): si no cambio, cuenta el objeto como saltado y reintenta. Imprime `atascados:` con las casillas. Respaldo: recoger_v1.bak.
- Probado en vivo (Casa intacta): recogio los 6 recogibles y dejo el huerto; limite de 3 atascos seguidos para no tocar casillas vacias.
- Despues de recoger SIEMPRE comprobar con captura que lo excluido sigue ahi; si se llevo algo prohibido, avisar.

## Correcciones del usuario sobre recoger
- Barrita verde a la izquierda de un arma NO significa siempre "dañada" (un hacha con barra verde no estaba rota y se podía tomar). No saltar armas solo por la barra verde; saltar solo si el usuario lo indica o se ve claramente rota.
- Primer objeto no recogible (ej. huerto) rompía la cuenta de casillas: ya lo maneja recoger.sh (verifica con md5 y lo salta).

## Edificios con varios sitios de luteo (ver MULTISITIOS.md)
- Un edificio grande puede tener VARIOS sitios (nombre "Edificio - Sitio"). Colegio = 4 (Biblioteca, Aulas, Cantina, Gimnasio), Clínica = 2 (Edificio principal, Salas), Ruinas = 2 entradas (prohibido). Una parada por sitio. Capturas de referencia en evidencia_multisitios/.
- En la ruta, tocar cada parte del edificio y leer el nombre; si cambia, es otro sitio.

## Linterna: identificar SIEMPRE al empezar (error de la parada 6)
- Al empezar cada casa/fase, `linterna.sh` identifica la linterna puesta por su icono (FAROL / POTENTE / VACIA / DESCONOCIDA) y `preparar.sh` la imprime: `LINTERNA_PUESTA=X oro=N quiero=Y`. Cambia solo si no coincide. Queda en casa_auto.log.
- Falso dorado corregido: el RAYO amarillo de energia (cigarros, refrescos) lo contaba `rareza.sh` como moneda dorada y se usaba la roja sin necesidad. La moneda dorada tiene azul 45-115 y verde <=225; el rayo tiene azul ~3. Ahora se distingue.
- Probado fuera del juego con 6 capturas reales; falta verlo en vivo.

## Combate durante el luteo (probado en vivo, Casa de ladrillo del centro)
- Al luteo puede salir la ventana "Has visto a un enemigo" (abejas gigantes, nivel Seguro). `sh encuentro.sh 45` pulsa Batalla automatica -> Confirmar -> Cerrar y vuelve a la lista de la casa; funciono y gano.
- Ajuste: los botones en vivo son mas claros que en las capturas del usuario (Batalla automatica R~200): sin tope R<190. Si dice SIN_VENTANA con la ventana delante, repetir (a la 2a lo reconocio).
- Tras el combate: `saquear.sh` x2 + `fin.sh` otra vez (los cadaveres/abejas nuevos aparecen en la lista), luego recoger.
- Armas con icono ROJO de reparacion y texto "(CON DAÑOS)" = danadas: saltar. Cuerpos de abeja y cadaver: saltar. Mesa de madera: saltar.
- Paradas 6 y 7 hechas. Peso por encima de 1050 (icono rojo arriba a la izquierda), limite practico ~1300: vigilar.

## PROTOCOLO FIJO: ventana de enemigo durante el luteo (el usuario lo aprobó como "perfecto", hacerlo SIEMPRE así)
Puede salir "Has visto a un enemigo" en cualquier momento del luteo (suele salir al terminar la busqueda / saquear). Secuencia obligatoria:
1. DETECTAR: si tras `casa_auto.sh` (o en cualquier captura) se ve la ventana (titulo rojo "Has visto a un enemigo", botones "Batalla automática" y "Ataque"), NO recoger todavia.
2. SKIPEAR: `sh /data/local/tmp/encuentro.sh 45` -> Batalla automática; si sale el cartel "demasiado fuerte" -> OK -> Ataque -> batallaD.sh (batalla manual); si no -> Confirmar -> Cerrar (AUTO_HECHA).
3. VOLVER A REVISAR LOS ITEMS: al regresar a la casa tomar captura nueva: la lista CAMBIA (aparecen cuerpos de enemigos, cadáver, botín nuevo).
4. VOLVER A SAQUEAR: `sh saquear.sh` x2 + `sh fin.sh 40` (otra vez, aunque ya se hubiera hecho antes del combate) y captura de nuevo, porque el saqueo añade objetos y mueve las casillas.
5. RECUENTO Y EXCLUSIONES sobre la lista NUEVA (cuerpos de abeja/enemigo, cadaveres, armas "CON DAÑOS" con icono rojo de reparacion, mesa/madera, tele "?", abejas) -> armar FLAGS por casilla -> `sh rc.sh FLAGS`.
6. Verificar con captura que quedo solo lo excluido, y recien entonces ir al siguiente edificio.
- Esto vale tambien para la ventana "demasiado fuerte": se resuelve igual y se repasa/ saquea de nuevo.
- Integrado en `casa_auto.sh` (pasos 1-4 automaticos); el paso 5 (flags) lo decide Claude mirando la captura.

## Progreso Zaraysk (guardado)
- Hechas: 1-15 (15 = Edificio blanco neoclasico = "Banco": su CAJA FUERTE con dinamita es DE PAGA: se omite y la parada 15 cuenta como COMPLETADA aunque el edificio no salga en gris). 16 Edificio marron SO (Supermercado) HECHA. Siguiente: 17 Mansion blanca... Peso >1165/1050 tras la 15 (limite practico ~1300): vigilar antes de seguir.
- Radiación llegó a 0 tras la parada 10 (empezó en 15, ~1 por búsqueda). Peso 1 043+/1 050 (icono rojo, limite practico ~1300). Ver que pasa con radiación 0 antes de seguir mucho.
- `casa_auto.sh` + `rc.sh` probados en vivo en 3 sitios seguidos (20-30 s cada uno). enlupa.sh funciono (desmintio un AGOTADO falso en el mapa).

## COMBATE: el ORDEN de las armas cambia en cada batalla (error del 'Hombre', arreglado v13)
- Ordenes vistos: (pistola, rifle, muneco), (muneco, rifle, pistola), (muneco, pistola, rifle). NUNCA suponer posicion ni 'pistola antes que rifle'.
- Causa del error: slots fijos en el codigo; "invocar" toco el slot del rifle (doble toque = disparo en turno 1) y "disparar" eligio la pistola.
- Arreglo: Bcv RECONOCE cada arma por su ICONO (plantillas 25x25 en gris, clara y oscurecida; correlacion; mejor permutacion con margen >=1.0 y puntaje >=0.35) en cada comando (cargaEstado). Si no esta seguro NO ACTUA: devuelve SLOTS_DESCONOCIDOS y batallaD.sh termina con ese texto (exit 6). Escribe `slots` ("m p r") solo como ultimo conocido.
- Probado fuera del juego: 391 capturas, 37 de combate con margen alto, todas con el orden correcto (incluidas batallas no usadas para las plantillas); en un mapa sin combate no decide. FALTA probarlo en vivo.
- Jar: bcv_v13 md5 46c1ad85...; respaldos bcv_v11_antes_iconos.bak, batallaD_v1.bak. Fuente: skill_dayr/Bcv.java.
- `invocar` comprueba que el marco este en el slot del muneco antes del doble toque. Cada 'blanco fijado' guarda captura blanco_N.raw para revisar si apunta a una mina.
- NUNCA borrar `slots` ni lanzar batallaD a mitad de turno sin comprobar con `sh bcv.sh ver 2` que SLOT/ALIADO tienen sentido.

## REGLA ANTI-REGRESION (queja del usuario: 'cosas ya corregidas se des-corrigen al cambiar de tema')
- Causa real (error h3): al arreglar el orden de armas deje `Y_H3` (habilidad 3 del MUNECO) igual a la fila del muneco entre las armas; en su turno el panel muestra SUS habilidades y la 3a esta SIEMPRE en y=871. Funcionaba antes solo por casualidad (muneco en fila 3).
- Corregido en bcv_v14 (md5 ff6ced30..., Y_H3 constante 871; respaldo bcv_v13_antes_h3.bak).
- ANTES de desplegar cualquier jar de combate correr `skill_dayr/regresion.sh` (prueba offline con capturas reales de los 3 ordenes de armas + habilidad 3 en 871 + mapa sin combate no decide). Si falla, no se despliega.
- Al tocar cualquier constante de posiciones (Y_*), preguntar: ¿depende del orden de armas? Panel del muneco, Mover (347) y Terminar turno NO dependen.
- Lo ya confirmado en vivo con v13: slots por icono bien, invocar bien (ficha verde en el tablero), disparo con rifle a enemigos, descarte de blancos sin linea de tiro.

## Cuándo una ciudad está terminada
- Una ciudad NUNCA aparece como terminada. Los edificios se ven en GRIS cuando ya se luteó (las 2 fases, si las tiene).
- Cuando todos los edificios válidos estén grises, se considera la ciudad luteada. (Sepia/blanco y negro = usado.)

## Edificios por categoría (usuario)
- Edificios sueltos como un galpón de ladrillo con vehículos en el cruce central también son "edificio" y se lutean. El mosaico tiene que marcarlos aunque parezcan parte de la calle (en Zaraysk faltó el del centro: ruta 3).

## Pendiente de aprender
- Salir de la ciudad / viajar a la siguiente; si algo falta, anotarlo aquí.

## Movimiento en combate (v15, 2026-11-05)
- `mover` SOLO se usa cuando NINGÚN enemigo tiene casilla iluminada (en rango). Si hay alguno en rango el servicio loguea "hay enemigos EN RANGO ... NO me muevo" y dispara/pasa.
- Jar en celular: bcv.jar = v15 (md5 0330770c...); respaldo del anterior: bcv_v14_antes_mover2.bak. Fuente: skill_dayr/Bcv.java. Correr regresion.sh antes de desplegar.
- Pendiente: verificar en batalla real ese log.

## Detector de enemigos: aro azul (v16, 2026-11-05)
- Bug: enemigos tipo "General calabaza" tienen aro AZUL MARINO (~50,70,101, suma ~220) y `osc()` solo contaba "oscuro" suma<150 -> ficha descartada -> enemigos=0, el bot pasaba turnos. Arreglo: `osc` acepta tambien azul marino (b-r>=35, r<90, b<150).
- Prueba offline: skill_dayr/enemigo_azul.raw (esperado ene=7,6) con T4 (jug=4,3 ali=6,4). Comparado con raws viejos: sin cambios salvo ese enemigo. regresion.sh usa cls16.
- Si "enemigos=0" mientras la pantalla muestra enemigos: capturar (screencap + `sh bcv.sh ver 2`) y revisar el color del aro de esa ficha con px.sh.
- Jar celular: bcv.jar = v16 (md5 10cbc05f...), respaldo bcv_v15_antes_azul.bak.

## Movimiento: "en rango" = celda iluminada Y con tiro posible (v17, 2026-11-05)
- Error: un enemigo con celda iluminada pero SIN linea de tiro (descartado como "IMPOSIBLE atacar") contaba como "en rango" y el bot decidia NO moverse y pasaba turno. Ahora el gate solo bloquea `mover` si hay un enemigo en celda iluminada que NO este en `lastMalos` (descartados). Si todos los iluminados estan sin tiro -> se mueve (mover auto, una vez por turno).
- Jar celular: bcv.jar = v17 (md5 b9ae3540...), respaldo bcv_v16_antes_malos.bak. Fuente: skill_dayr/Bcv.java.
- Pendiente de ver en vivo: mover cuando el enemigo esta en celda iluminada sin linea de tiro (puede dar SIN_MEJORA porque la distancia no mejora; si pasa, elegir casilla por linea de tiro y no solo por distancia).

## Un solo script de batalla a la vez (2026-11-05)
- Sintoma: log lleno de "turno MUNECO -> NO_MUNECO" en bucle + "mv: bad bcv.cmd.tmp". Causa: DOS `batallaD.sh` corriendo a la vez (uno viejo que no murio) peleando por bcv.cmd/bcv.res.
- Arreglo: `encuentro.sh` ahora mata al inicio cualquier `sh batallaD.sh`/`sh encuentro.sh` previo (excepto el mismo). Respaldo: encuentro_v2.bak. Antes de lanzar a mano: `ps -A -o PID,ARGS | grep -E "batallaD|encuentro"`.

## Turno MIO con aro tambien en el muneco (v18, 2026-11-05)
- Bug: el perfil decia MIO y el muneco (pegado al jugador/enemigo) tenia aro naranja a la vez que el jugador -> turnoSeguro devolvia MUNECO y el script se quedaba en bucle `turno MUNECO -> NO_MUNECO` sin disparar.
- Regla nueva: MUNECO solo si hay aro en el aliado y NO hay aro en el jugador (`aroEnJug`). Capturas de turno del muneco (muneco.raw, h3_lista.raw) siguen dando MUNECO; muneco_pegado.raw da MIO. regresion.sh lo comprueba.
- Los dos "sh batallaD.sh" que se ven en ps son padre + subshell de `$(...)`, NO duplicados reales (la causa fue el aro); el guard de encuentro.sh que mata procesos previos sigue siendo util pero no era la causa.
- Jar celular: bcv.jar = v18 (md5 ca1793ac...), respaldo bcv_v17_antes_arojug.bak.

## Aliados = interior VERDE, no aro verde (v20, 2026-11-05)
- Error mio (v19): crei que aro verde = aliado. FALSO: el aro verde de zombis/murcielago/etc. es su BARRA DE VIDA; pueden ser enemigos. Aro azul tambien puede ser enemigo.
- Lo que distingue a un aliado es el INTERIOR de la ficha en verde (cara neon del muneco, granjero/mago del evento con sombrero verde). Enemigos: interior beige/blanco sobre negro.
- `interiorVerde(cx,cy)`: >=15 de 144 puntos del disco r<=33 con g>r+40 y g>b+40. Medido: muneco 76/144, granjero 75/144, enemigos 0/144. Esas fichas van a `otrosAliados` (no se les dispara ni se persigue); el muneco sigue marcando `ac,ar` por el test original.
- Prueba offline: skill_dayr/muneco_pegado.raw -> 7 enemigos + aliado 4,6 (granjero). skill_dayr/interiores.png muestra los interiores.
- Jar v20 (fuente skill_dayr/Bcv.java). Respaldos en celular: bcv_v18_antes_aliados.bak (v18).
- Jar celular: bcv.jar = v20 (md5 df36d3ed...), respaldo del anterior (v19, regla de aro verde mala): bcv_v19_ring.bak.

## Eventos (pergamino) que no llegan a batalla: RONDA de opciones (2026-11-05)
- Diseno pedido por el usuario: si el evento no avanza a la batalla, probar TODAS las opciones visibles, un click a cada una, comprobando ANTES de cada click que no haya empezado la ventana de enemigo.
- Falla que habia: el script solo probaba las demas opciones si el click "no avanzaba" (pantalla igual/ya vista). En eventos largos cada click abre una pagina nueva, asi que nunca lo hacia y se quedaba en la opcion 1 (ej. evento "Mujer / casa encantada", paginas con 3 opciones y flecha ▼).
- Ahora (`scripts_celular/batallaD.sh`): tras PMAX1=3 clicks de la 1a opcion sin batalla -> `ronda()` (filas y=747 y y=864; fila 1 y=630 ya probada), con `si_encuentro` antes y despues de cada click (usa `ENC_TEST=1 sh encuentro.sh`; si hay ventana de enemigo -> `exec sh encuentro.sh`). Limite 90 s en el evento (`PERGAMINO_ATASCADO`).
- Probado en vivo: llego a batalla y combatio hasta el turno 5 (luego el juego salio de pantalla).
- Pendiente: la flecha ▼ (mas opciones abajo) no se toca; el umbral PMAX1 (3) se puede cambiar (`PMAX1=5 sh encuentro.sh`).
- `batallaD.sh` ahora NO toca nada si el juego no tiene el foco (comprueba `dumpsys window | grep mCurrentFocus`); sale con JUEGO_FUERA a los 20 s.

## Enemigo = ficha CON ARO azul o verde; cajas/objetos NO (v21, 2026-11-05)
- Error: el bot disparaba a una caja/roca en casilla iluminada NARANJA. `analiza()` contaba como ficha cualquier cosa oscura en la casilla; la casilla naranja solo marca el rango, NO hace enemigo a nada.
- Regla (usuario): enemigo = ficha con aro AZUL (escudo) y/o VERDE oliva (vida, ~60,85,25). Sin ninguno -> objeto, no se dispara. Aliado = interior VERDE (v20) aunque tenga aro. Orden: interior verde -> aliado; sin aro azul/verde -> `sinAro` (objeto); resto -> enemigo.
- `tieneAroEnemigo`: corona r=38..64: azul (b>r+35,b>g+15) >=8 puntos o verde oliva (g>r+12,g>b+30,g>=40) >=6. El primer intento (verde vivo g>r+25) perdia enemigos con aro verde oscuro: SIEMPRE probar con las capturas.
- Pruebas: `regresion.sh` (chkE): bandidos_cajas 7 enemigos (incl. el bandido de aro verde 0,2), nuevo_dim 6 (la roca 3,7 ya NO es enemigo; antes si), clasico1 4. T9.java imprime enemigos/aliados/sinAro.
- Jar celular: bcv.jar = v21 (md5 896a251f...), respaldo bcv_v20_antes_aro_req.bak.
- Eventos: `PMAX1` por defecto 8 (el usuario lo pidio: 8 clics en la opcion 1 antes de la ronda; los clics se cortan solos al salir del pergamino).
- Corregido tras probarlo en vivo (v21): la ronda de opciones SOLO corre mientras no haya habido batalla (`FIN=0`). Despues de VICTORIA/MISION el evento que sigue se resuelve con la 1a opcion, sin ronda. Prueba en vivo v21: batalla de bandidos con cajas/manchas/barriles ganada en 126 s; los blancos (1,4 3,2 4,0 0,1...) eran bandidos con aro; sin disparos a cajas.
- PMAX1 final = 8 (decision del usuario, 2026-11-05): 8 clics en la 1a opcion (0,5 s de espera cada uno; tras cada clic se lee la pantalla y si ya no es PERGAMINO sale al instante) y despues la ronda con las demas opciones. Tope 90 s por evento.

## Ficha CLARA con aro no se identificaba: Chupacabras (v22, 2026-11-05)
- Error: el bot no identificaba a un enemigo (la Chupacabras, N=9, casilla 8,3, cara blanca y ancha). Causa: `analiza()` exigia >=5 de 8 puntos oscuros alrededor del centro; con un dibujo claro solo salian 4 y la rechazaba aunque tuviera aro.
- Arreglo: `if (dd < 5 && !aro && !tieneAroEnemigo(cx,cy)) continue;` -> una ficha con aro azul/verde vale aunque su dibujo sea claro.
- Pruebas: `chupacabras.png`/`.raw` (N=9) debe dar enemigos 2,3 / 8,3 (+ el jugador 7,4 offline); T9 acepta N como argumento (`java T9 9`). Las demas capturas de combate no cambian. `T10.java N col fila` explica por que una casilla se acepta/rechaza.
- Jar celular: bcv.jar = v22 (md5 ce2935d5...), respaldo bcv_v21_antes_ficha_clara.bak.
- Tableros: N puede ser 7, 8 o 9 (se lee de la captura); las pruebas deben usar el N correcto.

## v23 (instalada): ficha con fondo negro = enemigo
- Problema: enemigo casi muerto (barra de vida vacia o tapada por debuffs) no tiene aro azul/verde suficiente -> v22 lo trataba como objeto (caso Tentaculo, t12/tentaculo.png; fotos bajavida1/2.jpg).
- Arreglo: `discoNegro()` (anillos r=46/50/54, >=78% pixeles oscuros) cuenta como enemigo; objetos quedan <=70%. Excluye la casilla `jug_pos` (el jugador sin aro naranja tambien tiene fondo negro).
- El aro naranja del jugador solo aparece mientras hay una accion (arma/invocacion) seleccionada; por eso el jugador se reconoce con `jug_pos`.
- Respaldo de v22 en el celular: `bcv_v22_antes_disconegro.bak`. Fuente: ver `Bcv.java`.

## v24 (instalada): no dar un blanco por muerto con una sola captura
- Problema: `atacar()` declaraba "ELIMINADO" si en UNA captura la ficha del blanco no estaba en `enemigos`. Con destello/numero de dano/animacion la deteccion falla un instante y el bot cambiaba de blanco sin haberlo matado.
- Arreglo: si no aparece, espera 500 ms, recaptura y reanaliza; solo si sigue ausente lo da por muerto (log: "reaparece tras recaptura" si no estaba muerto). Costo ~0,5 s por baja.
- Respaldo de v23 en el celular: `bcv_v23_antes_debounce.bak`. Fuente: `Bcv.java` (version vigente).

## Estado del combate (cierre) y pendientes leves
Combate completo y probado en vivo con v24 (varias victorias seguidas: batallas normales, eventos con pergamino, ronda de opciones). Scripts vigentes: `scripts_celular/batallaD.sh`, `scripts_celular/encuentro.sh`, servicio `Bcv.java` (v24). Para recompilar: javac --release 8 -> D8 --min-api 26 -> zip classes.dex (ver secciones de despliegue arriba); correr `regresion.sh` (usa `cls24`) antes de desplegar.
Pendientes leves (NO arreglar sin que Royer lo pida):
- A veces un turno pasa sin disparar con 1 punto de accion y enemigo en rango (el siguiente turno lo mata).
- "Probar al mas cercano" dispara a distancia 0 / sin linea de tiro (se descarta solo).
- La invocacion puede seguir disparando al jugador.
- Armas nuevas: si los iconos de slots no se reconocen (`slots por icono: NO seguro`) puede tocar el slot equivocado.
- Si la app de Claude pasa al frente (Royer escribiendo en el mismo celular) el script se detiene (JUEGO_FUERA/NO_HORIZONTAL): es a proposito.
Siguiente fase: ciudades (luteo/caminar): nav.py + casa_auto.sh + rc.sh; Zaraysk parada 11 "Colegio - Biblioteca".

## VIAJE POR CALCULO entre paradas (probado en vivo, Zaraysk) -- viaje.py + recentrar.sh
Idea de Royer: al abrir y cerrar la lupa la camara se centra en el personaje (1218,540). Hechos medidos (error <0.1 px del mosaico, 4 pruebas):
- Tocar un punto del suelo O un edificio: el personaje termina EXACTAMENTE en el punto tocado (no se desvia; el punto de parada de cada edificio es su x,y de `zaraysk_ruta.json`).
- A ZOOM 3 (`zoomnivel.sh estado` -> T=738) la escala es 1.9311 px de pantalla por px de mosaico (ruta*1.5 = mosaico). Constante.
- Por tanto: siguiente toque = (1218,540) + (destino - origen)*1.5*1.9311, SIN mirar el mapa. Requisitos: zoom 3, saber la parada actual, y `sh recentrar.sh` tras cada llegada (imprime CENTRADO; solo cierra la lupa si se abrio: el boton X esta junto al de pausa).
- Uso: `python3 -I viaje.py 12 11` (parada actual -> destino; acepta numero, nombre o x,y) -> imprime los toques; si la distancia excede la zona segura de toque (x560-1900,y200-880) lo parte en tramos iguales (tap, sleep, recentrar). Ejemplo real: 12 Bloque azul SE -> 11 Colegio-Biblioteca en 2 tramos, llego exacto.
- Verificacion opcional (sandbox): `python3 -I loc.py captura.png` ubica la captura en el mosaico (SIFT) y da el centro en coord de ruta; debe coincidir con la parada.
- Cuidado: los puntos intermedios de un tramo son suelo/edificios cualquiera (el 1er tramo de 12->11 cayo sobre "Colegio - Aulas", sin consecuencias). Si un intermedio cae en un sitio con enemigos (crater rojo, edificios rojos), poner waypoints: `viaje.py 12 x,y` y luego `viaje.py x,y 11`.
- Si el personaje queda en un edificio con enemigos, el sitio de parada de la ruta (x,y) ya es el punto seguro elegido; usar siempre esos puntos como "acomodo" tras cada edificio (terminar de lutear -> estamos en SU x,y).

## Recorrido Zaraysk 11-15 con viaje.py + casa_auto.sh + rc.sh (hecho en vivo)
- Orden por parada: `python3 -I viaje.py ACTUAL SIGUIENTE` -> ejecutar tap/sleep + `sh recentrar.sh` (CENTRADO) -> `rm -f casa_auto.res; (sh casa_auto.sh >/dev/null 2>&1 &)` -> esperar ~40 s -> mirar `casa_auto.res`/captura -> `sh rc.sh FLAGS`.
- ZOOM: usar `zoomnivel.sh 3` (T=826): ahi ESC=0.9194 (viaje.py ya lo usa). Si el estado `zoom_T` esta desfasado (la vista real no coincide), `echo M > /data/local/tmp/zoom_T; sh zoomnivel.sh 3` re-normaliza. A otro zoom la escala cambia (medido 1.9311 mas cerca).
- Si no sabemos donde estamos (Royer movio al personaje): captura -> `python3 -I loc.py captura.png` da el centro en coord de ruta tras recentrar; `python3 -I viaje.py X,Y DESTINO`.
- casa_auto.sh puede cortarse a medias (sin casa_auto.res) tras un combate o por foco: mirar la captura; si "Saquear a todos" esta activo -> `saquear.sh` x2 + `fin.sh 40`, luego recoger.
- Tras un combate en el luteo (parada 15) casa_auto salio PARA_PANTALLA/TIEMPO_AGOTADO; la lista tenia abejas (NO), revolver/camion con icono de reparar (NO), mesa (NO). Recogidos: casquillos, monedas, alfombra, sillon.
- CAJA FUERTE (panel derecho: puerta de boveda con dinamita, boton de cerradura) = DE PAGA: no abrirla; marcar la parada como completada y seguir (`puerta.sh` dice SIN_PUERTA, `estado.sh` OTRO: normal).
- Exclusiones aplicadas: tele "?", pistolas/bici/camion con icono rojo de reparar, mesa de madera, abejas, cadaver (tras saquear desaparece de la lista).

## Si Royer mueve el personaje/camara entre paradas (viaje por calculo)
- El calculo supone que al recentrar el personaje esta en la x,y de la ultima parada. Si Royer lo mueve, NO se auto-corrige: el siguiente tramo sale desplazado EXACTAMENTE lo que el se movio (y el error se arrastra a las paradas siguientes).
- Antes de cada viaje, si hubo intervencion o hay duda: `sh recentrar.sh` + captura + `python3 -I loc.py captura.png` (da la posicion real en coord de ruta) y usar `viaje.py X,Y DESTINO` con esa posicion. Tambien sirve comprobar el nombre sobre el personaje al llegar.

## VIAJE CON VERIFICACION DE POSICION (pos.py) -- sustituye al calculo ciego cuando hay duda (hecho y probado: 15 -> 16)
Pedido de Royer: verificar posicion antes de la lupa; si no estoy bien, recolocar y volver a preguntar; marcar el destino y llegar sin importar nada (ataque skipeable, empujon, zona enemiga donde no queda en el centro); la parada solo cuenta cuando se llega al punto.
- `python3 -I pos.py CAPTURA.png DESTINO` (1 screenshot, ~2 s): ubica la captura en el mosaico (SIFT, mide la escala/zoom sola) + encuentra la ficha del personaje (plantilla `ficha_personaje.png`, visible a zoom nivel 3 o mas lejos) -> posicion en coord de ruta SIN recentrar (probado: mismo resultado con la ficha centrada o en (1124,367) a otro zoom).
- Imprime `LLEGADO a N (error e)` si e<2.5 unidades; si no, `RUTA SEGURA: aqui -> ... -> destino` + `input tap X Y` + `sleep N` (tramo parcial si el destino cae fuera de la zona segura de toque).
- RUTEO SEGURO: evita sitios enemigos (tipo E de `zaraysk_ruta.json`: campamentos, hogueras, crater) con margen 50 unidades y avanza por paradas V como waypoints (Dijkstra). Si no hay camino: `SIN_RUTA_SEGURA` (ir a mano).
- BUCLE (Claude): screenshot -> pos.py -> `input tap` -> `sleep` -> `sh recentrar.sh` (CENTRADO; si SIN_LUPA seguir igual) -> screenshot -> pos.py ... hasta LLEGADO; entonces `casa_auto.sh`. Tras un ataque skipeable (encuentro.sh) o si Royer movio algo: el mismo bucle.
- Medido: un personaje parado en una parada V queda a <1 unidad del punto (Colegio-Aulas 0.3, Banco 0.0, Supermercado 0.4). Tras mover Royer el personaje: error de decenas de unidades (detectado y corregido con el bucle).
- Limites: la ficha del personaje no se ve a zoom muy cercano (ahi se dibuja distinto); de noche SIFT podria fallar (SIN_UBICACION). Si SIN_FICHA: recentrar con la lupa y usar `viaje.py`.
- `viaje.py` (calculo puro, sin captura) sigue sirviendo cuando se sabe seguro de donde se parte.

## Leer el mapa: edificio gris/sepia = SIN lutear (dicho por Royer)
- En el mapa de una ciudad, un edificio desaturado (gris/sepia) esta SIN lutear; a color = ya saqueado. Sirve para saber que falta en una ciudad nueva (ej. captura de otra ciudad con un edificio gris junto a la carretera). Ojo: la parada 15 (Banco, caja de paga) puede seguir sin ponerse a color aunque este completada.

## Edificio de 2 partes luteables (dicho por Royer, otra ciudad)
- Fabrica de ladrillo con chimenea y tejado rojo (foto `referencias/multisitios/fabrica_dos_partes_otra_ciudad.jpg`): tiene DOS sitios luteables. Tratar como 2 paradas ("Fabrica - A" / "Fabrica - B"): tocar cada parte, leer el nombre sobre el personaje y lutear por separado. Mismo criterio que Colegio (4) y Clinica (2) en MULTISITIOS.md.
