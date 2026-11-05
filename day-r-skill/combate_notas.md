# Combate manual (batallaC) - Day R Survival  [Royer]

## Reglas de Royer
- Ventanas de pergamino (eventos de pelea / misiones): SIEMPRE la 1a opcion (avanza). "Salir/Irse" solo para evitar. Nunca "Extra"/"Duplicar" (anuncios).
- Cartel PELIGRO: pasa solo. Ventana de armas: NO cambiar nada, boton verde "Empezar batalla" (1848,894).
- Turno 1 (mio): invocar al muneco (accion 2) cerca de los enemigos (basta una casilla iluminada) y PASAR.
- Turno del muneco: habilidad 3 (misma posicion que mi accion 4) en CUALQUIER enemigo; si esta en enfriamiento (reloj rojo), saltar turno.
  Si muere: su icono vuelve a color en mi columna -> re-invocar y volver a su 3a.
- Mis turnos desde el 2o: enemigo a tiro -> doble toque con el rifle, repetido (spam). NUNCA moverme si hay a tiro. Sin nadie a tiro: acercarme (Mover).
  Al acabar los puntos y usar el movimiento gratuito el turno pasa solo.
- Aliados = verde, enemigos = blanco/anillo oscuro. Tablero cuadrado de tamano fijo, N x N casillas con N variable (7, 8, 9 vistos).
- Turno enemigo = columna izquierda OSCURA; enfriamiento = icono del aliado GRIS. Los turnos enemigos duran mas cuantos mas enemigos (sin tiempos fijos: se espera mirando).
- Al terminar: "Victoria" -> Cerrar (923,843); "Mision cumplida" -> Cerrar (1011,812). La ventana de dialogo del inicio puede NO repetirse al final.

## Piezas en el celular (/data/local/tmp)
batallaC.sh (ciclo completo), accion.sh (mover|aliado|rifle|pistola [dir | X Y]), spam.sh, enemigo.sh, quien.sh, aliado.sh, pasar.sh, esperar.sh,
bc_det.sh (detectores), guarda.sh (aborta si no hay combate).  Registro de decisiones: bc.log.

## Verificado EN VIVO (prueba real: evento de la bruja, victoria, mision cumplida)
- Ciclo: evento -> PELIGRO -> armas -> invocar -> pasar -> muneco hab.3 -> rifle -> victoria -> Cerrar -> mision -> Cerrar.
- quien.sh (MIO/MUNECO), aliado.sh (VIVO tras invocar), esperar.sh, pasar.sh, accion.sh con X Y.
## Validado solo con capturas (sin correr en el juego)
- batallaC.sh completo, enemigo.sh, spam.sh, h3_enfriando, victoria/mision automaticas.
## Fallo encontrado en la prueba en seco y arreglado
- turno() tomaba el mapa/campamento por "turno mio" (icono crema por coincidencia) y pasar.sh pulso el boton del CAMPAMENTO (esquina inf. der.).
  Ahora todo exige en_combate: boton "Terminar turno" verde oliva (180,181,81) en (2113,1035) + cruz verde (305,100) + escudo (300,155). Medido: combate = 3/3, resto = 0/3.
## Pendiente
- Re-invocar al muneco cuando muere (aliado.sh=LIBRE) sin probar en vivo.
- Probar batallaC.sh completo en el siguiente combate real (hasta ahora solo en seco).

## Personaje distinto de Royer (ej. el Cazador, mision del Mago)  [Royer]
- Pasa muy raramente. No hay municion ni muneco. Reglas: 1) mirar el ALCANCE de cada arma (cuadro iluminado) y si el enemigo cae dentro;
  2) si un arma esta en ENFRIAMIENTO (reloj con numero) usar la valida; 3) si no se alcanza a nadie, ACERCARSE; luego atacar (doble toque en el enemigo).
- Cada arma tiene "xN" = usos por turno (la espada x1, el lanzallamas x1). El numero junto a Mover ("x2") = MOVIMIENTOS GRATIS por turno: un rasgo del personaje (el Cazador tiene 2).
  El movimiento es gratis y no gasta la accion del arma. Se pueden usar los 2 antes de atacar (error mio: use solo 1 cada turno).
- Todos los enemigos tienen 1 accion por turno y a veces hay mas de uno. Una vez gastadas las armas, terminar turno.
- Probado EN VIVO (a mano): mover, espada con doble toque, pasar, esperar mi turno, victoria + mision cumplida cerrados por batallaC.sh (FIN_BATALLA).
- Mato 4 de 4 (Zombi, Payaso -> deja un Globo, Armadura viviente, Fantasma). Me equivoque al decir que la Armadura sobrevivio.
## Wiki: Monster Masquerade (Halloween 2023)
- Evento "mascarada de monstruos": cerca de asentamientos, fuente de Halloween Mask y Spooky Bags. Mapas por facciones: Free Factions, Iron Front & Oil Syndicate, S.O.V.A.
- Imagenes en dayr.fandom.com (File:Monster Locations I/II/III.png). Pagina en version 823 (el juego va por la 1.917): verificar ubicaciones en vivo.

## PENDIENTE PARA MAÑANA (pedido de Royer, 03-nov)
Royer pausa aquí. Quejas a resolver:
1. TODO MUY LENTO: enemigo.sh (~10 s por lectura) e invocación (~20 s, autodetección de N con dd píxel a píxel) son los cuellos de botella. Solución: leer UNA captura y analizarla de una vez (no un dd por píxel), cachear N y la posición del jugador, bajar esperas fijas.
2. SIN ORDEN EN LOS TURNOS: falta una máquina de estados clara (turno1 -> invocar+pasar; turno mío -> disparar mientras haya puntos y usos; turno muñeco -> hab3; esperar). Hoy mezcla lecturas sueltas y adivina el turno 1.
3. POCA SENSACIÓN DE AUTOMATIZACIÓN: debe correr de una sola pasada, sin intervención y con un resumen claro al final (qué pasó en cada turno, en bc.log).
Estado actual (todo en /data/local/tmp del celular, respaldos *.bak):
- batallaC.sh: pergamino con modo fila (click no avanza -> opciones 2 y 3, 0.5 s, >60 s termina), puntos de acción (6 círculos, naranja = lleno, rifle cuesta 2), turno 1 si el muñeco está libre al primer turno visto, delays bajados.
- Orden de armas nuevo: slot1 pistola de rayos, slot2 rifle (y=688), slot3 muñeco (y=871; aliado.sh mira (152,810) y (262,935)).
- Sin resolver: "no pude acercarme" (jug_pos no se crea en la primera lectura), rutas con loot tras victoria, 4+ opciones en diálogos.
- Regla: el chat en el mismo celular saca el juego de pantalla (JUEGO_FUERA a los 20 s); escribir desde otro dispositivo.

## 03-nov (noche): LENTITUD -> reescrito en Java (batallaD)
- Causa: enemigo.sh (~10 s) y el resto de detectores hacian cientos de `dd|od` (un proceso por pixel) y cada accion pedia su propio screencap + `input tap` (JVM de ~0.4 s cada uno).
- Solucion: `bcv.jar` (fuente: scratchpad/bcv/Bcv.java). UNA captura -> todo el analisis (pantalla, turno MIO/MUNECO/OTRO, puntos de accion, rifle, aliado, enfriamiento h3, N del tablero, jugador, enemigos con rango). Servicio persistente (bcv.sh, archivos bcv.cmd/bcv.res/bcv.alive; mkfifo no esta permitido) -> ~1.1 s por lectura (antes ~10 s solo los enemigos).
- Comandos: ver [2] | tap X Y @ms | invocar [dir|auto] | mover [dir|auto] | atacar [max] | hab3 | pasar ANTES | turno_mio [1] | turno_muneco | ping | quit.
  * atacar: selecciona rifle, dispara al enemigo en rango y CAMBIA de blanco solo mientras AP>=2 y haya usos (sin releer enemigos); para en SIN_PUNTOS/AGOTADO/SIN_EFECTO/SIN_BLANCO/FIN_TURNO_AUTO. Guardas: solo actua si TURNO=MIO (nunca dispara en turno del muneco).
  * invocar auto: direccion hacia el promedio de los enemigos (no siempre derecha).
  * Jugador: ahora se detecta con aro naranja aunque el icono de radiacion le tape el anillo (>=3 puntos oscuros + >=2 naranjas).
- batallaD.sh = orquestador corto (ver -> despacha PERGAMINO/ARMAS/VICTORIA/MISION/COMBATE). batallaC.sh queda de respaldo.
- Orden de armas: slot1 pistola de rayos (y=499), slot2 rifle (688), slot3 muneco (871).
- PROBADO: deteccion offline con 14 capturas antiguas + 2 de hoy (N=9 autodetectado, AP, enemigos en rango = igual que el shell). Servicio y wrapper en el celular (ping/ver). batallaD en seco (sale JUEGO_FUERA a los 20 s).
- SIN PROBAR EN VIVO: los toques de Java (inyeccion igual que zoom.jar), turno completo, invocar con la casilla naranja. La pantalla del celular estaba apagada.
- Probar manana: juego en combate al frente -> `cd /data/local/tmp; sh batallaD.sh 900` y mirar bc.log (lineas "J" = decisiones de Java). Parar servicio: `sh bcv.sh quit`.
- Sigue pendiente: ALI (posicion del muneco) no se detecta (no afecta), loot tras victoria, dialogos de 4+ opciones.

## 03-nov: activo/inactivo e invocacion por el PERFIL (Bcv.java actualizado, jar nuevo SIN desplegar en el celular)
- Turno por perfil (arriba-izq): icono (207,116) brillante (219,199,164)=MIO, con capa oscura (143,131,107)=turno enemigo, verde=MUNECO. AP: circulos naranja (250,168,30) llenos; con capa gris no cuentan.
- Aro naranja del tablero = personaje ACTIVO (tambien en el enemigo cuando le toca). Cambios: jug_pos solo se guarda en MI turno (antes el aro del enemigo activo lo pisaba); si el perfil dice MIO pero el aro esta en el muneco -> MUNECO.
- Invocacion: icono slot 3 gris (ahora tambien bajo la capa oscura: antes leia LIBRE en turno enemigo) O ficha verde en el tablero.
- Jar: scratchpad/bcv/bcv_perfil.jar (md5 arriba). Pendiente: copiarlo al celular como /data/local/tmp/bcv.jar (el MCP del celular estaba desconectado).
