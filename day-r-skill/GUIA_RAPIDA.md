# GUIA RAPIDA: que script usar y cuando (LEER ANTES DE TOCAR EL CELULAR)

## Regla de oro
**Si existe un script para la situacion, se usa el script. NO tocar la pantalla a mano** (tap/swipe sueltos) salvo que el script no cubra el caso. Los scripts viven en el celular `/data/local/tmp/` y se corren con `sh /data/local/tmp/<x>.sh` (shell del celular). Detalle de cada uno: `INDICE.md`. Trampas y reglas del usuario: `SKILL.md`.

## 0. Antes de cualquier script
1. Juego al frente y HORIZONTAL (ancho de captura 2436). Si el celular esta en vertical / otra app (p. ej. la app de Claude) los scripts salen con `JUEGO_FUERA` o `SIN_VENTANA`: NO insistir ni tocar; pedir al usuario que deje el juego en pantalla. Abrir: `am start -n com.gm_shaber.dayrpremium/com.ansca.corona.CoronaActivity`.
2. Servicio de combate vivo: `sh /data/local/tmp/bcv.sh ping` -> `PONG` (se arranca solo si no).
3. Que pantalla es: `sh bcv.sh ver 2` (PANT=COMBATE / PERGAMINO / ARMAS / VICTORIA / MISION / OTRA / VERTICAL) o `sh estado.sh` (BATALLA / PUERTA / LUPA / AGOTADO / OTRO / NO_HORIZONTAL).

## 1. Que pasa en pantalla -> que hacer
| Situacion | Script / accion | Resultado y siguiente paso |
|---|---|---|
| Ventana "Has visto a un enemigo" (mapa o durante el luteo) | `sh encuentro.sh [max_s]` | Hace TODO: Batalla automatica o, si "demasiado fuerte", OK->Ataque->`batallaD.sh`. Imprime `AUTO_HECHA` / `FIN_BATALLA`. Despues: re-ver items, `saquear.sh` x2, recoger (ver 4). |
| Pergamino / evento con opciones (p. ej. "Mujer, casa encantada") | NO tocar: `batallaD.sh`/`encuentro.sh` ya pulsan la 1a opcion (`pergamino()`) | Solo lanzar `encuentro.sh`. |
| Pantalla de armas / combate por turnos | `sh encuentro.sh` (o `sh batallaD.sh [max_s]`) | Un solo script a la vez. Log: `bc.log`, `enc.log`. Termina en `FIN_BATALLA` (victoria + mision cumplida cerradas). |
| Combate: errores | `SLOTS_DESCONOCIDOS` (exit 6) = no reconozco el orden de armas por icono: avisar, NO adivinar. `INVOCACION_FALLA` (exit 2). `PANTALLA_DESCONOCIDA` (exit 5, deja `pantalla_rara.raw`). `JUEGO_FUERA`/`SIN_VENTANA` = juego no al frente. | Ver `combate_notas.md` y `SKILL.md` (Combate). |
| En ciudad, ir a un edificio de la ruta | `nav.py captura.png DESTINO` (sandbox) -> da `camara.sh DX DY` + `input tap` | Ruta: `zaraysk_ruta.json` + `zaraysk_mosaico.png`. |
| Dentro de la casa (lupa) hay que lutear | `sh casa_auto.sh` (semi-auto: lupa, puerta, linterna, lutear, saquear x2) | Lee `casa_auto.res`: `LISTO_PARA_RECOGER` / `LISTO_TRAS_BATALLA` / `PARA_*`. |
| Recoger objetos | `sh rc.sh FLAGS` (cadena 1/0 por casilla, cierra la lupa) | Exclusiones: tele "?", abejas, cadaveres, armas "(CON DANOS)", mesas/madera, vehiculos. `recoger.sh` verifica casilla atascada por md5. |
| Linterna | `linterna.sh` identifica la puesta (FAROL/POTENTE/VACIA); `luz.sh N` elige. POTENTE (roja) solo si hay objetos dorados. | Identificar al inicio de cada fase. |
| Puerta bloqueada | `puerta.sh` (1 toque por comprobacion) | No tocar la cuadricula de objetos. |
| Zoom / camara | `zoomnivel.sh 1\|2\|3\|min\|estado`, `camara.sh DX DY` (>=80 px por swipe), `calidad.sh` | Zoom 3 para lutear, calidad 738 para mapear. |
| Mapear una ciudad nueva | `mosaico.md` + `centro_ciudad.py`, `plan_mosaico.py`, `mosaico.py` | Marcar rojo/verde; edificios en gris = no luteables. |

## 2. Cuando SI se puede tocar a mano
Solo si NO hay script para eso (p. ej. un dialogo nuevo desconocido): primero describir la pantalla al usuario, tocar UNA vez, comprobar con `bcv.sh ver 2`, y despues AGREGAR el caso al script y a esta guia. Nunca repetir toques a ciegas.

## 3. Orden normal de una sesion de luteo
`ver pantalla` -> (si ventana enemigo: `encuentro.sh`) -> `nav.py` al siguiente edificio -> entrar con lupa -> `casa_auto.sh` -> leer `casa_auto.res` -> `rc.sh FLAGS` -> siguiente. Parada actual de Zaraysk: 11 "Colegio - Biblioteca".

## 4. Lo que NO esta en el repositorio (solo en el celular)
`batallaD.sh`, `encuentro.sh` (copia en esta carpeta), `casa_auto.sh`, `rc.sh`, `recoger.sh`, `linterna.sh`, `luz.sh`, `puerta.sh`, `estado.sh`, `bcv.sh`, `bcv.jar` (v20) y sus respaldos `*.bak`. Pendiente: copiarlos aqui cuando el celular este conectado (`cat` por shell en bloques con md5, o `adb pull`), para que una sesion nueva pueda reinstalarlos.
