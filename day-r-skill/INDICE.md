> Para decidir QUE script usar en cada momento: `GUIA_RAPIDA.md`.

# Índice: qué hace cada cosa y dónde buscarla (Day R)

Dos lugares: **SANDBOX** = `/tmp/claude-0/-home-user-royer-method/caac0904-0b46-56da-8dcb-c80004fee664/scratchpad/` (donde corro Python y guardo capturas) y **CELULAR** = `/data/local/tmp/` (donde viven los scripts de shell; se corren con `sh /data/local/tmp/<x>.sh` por `mcp__Aaacelu__shell`).
Capturas del juego: las que devuelve `screenshot` se guardan en el sandbox (`.../tool-results/mcp-Aaacelu-blob-*.png`, 2436x1080 reales; las veo reducidas a 2000 de ancho: real = visto x 1.218).

## Documentos (SANDBOX, carpeta `skill_dayr/` y `scratchpad/`)
| Archivo | Para qué |
|---|---|
| `skill_dayr/MULTISITIOS.md` | Registro de edificios con varios sitios de luteo (Colegio x4, Clínica x2, Ruinas x2) + capturas en `evidencia_multisitios/`. |
| `skill_dayr/SKILL.md` | Skill principal: reglas del usuario, flujo de ciudad, trampas aprendidas. EMPEZAR AQUÍ. |
| `skill_dayr/luteo_referencia.md` | Traspaso de luteo: reglas del juego, qué recoger, edificios prohibidos, tabla de scripts, coordenadas y colores. |
| `skill_dayr/mosaico.md` (= `SKILL_mosaico.md`) | CÓMO MAPEAR una ciudad: mosaico de tomas, calidad fija, marcar rojo/verde. |
| `skill_dayr/combate_notas.md` (= `NOTAS_COMBATE.md`) | Combate por turnos: coordenadas, detección, historial de arreglos. |
| `skill_dayr/encuentro.sh` | Copia de la ventana "Has visto a un enemigo" (la activa está en el celular). |

## Mapeo de ciudad (SANDBOX, Python)
| Archivo | Para qué |
|---|---|
| `centro_ciudad.py captura.png` | Mide el círculo de la ciudad e imprime `camara.sh DX DY` para centrarla y el diámetro D. |
| `plan_mosaico.py D T_MEDIDO [T_OBJ]` | Calcula el zoom y la lista de movimientos de cámara (tomas) para cubrir la ciudad entera (margen 150 px). |
| `mosaico.py salida.png t1.png t2.png ...` | Une las tomas (SIFT, solo traslación) en una imagen. |
| `cobertura.py mosaico.png` | Comprueba que el anillo de la ciudad quede completo (no fiarse con anillos grandes: mirar a ojo). |
| `flags.py`, `scale.py` | Auxiliares de marcado/escala de mapeos anteriores. |
| `*_mosaico.png`, `*_marcado*.png`, `ruta*.json`, `prohibidos/`, `ruinas/` | Resultados de mapeos anteriores (Shatki, Arzamas, Sasovo) con marcas y rutas: ejemplos de cómo quedó. |

## Navegación entre edificios (SANDBOX, skill_dayr/)
| Archivo | Para qué |
|---|---|
| `nav.py captura.png DESTINO` | Ubica una captura en el mosaico (SIFT, cualquier zoom) y da `camara.sh` + `input tap` + espera para ir a un edificio sin mirar el mapa. `--lista` imprime la ruta. |
| `zaraysk_ruta.json`, `zaraysk_mosaico.png` | Ruta y mosaico de Zaraysk que usa nav.py (coordenadas anotadas de cada edificio). |

## Cámara y zoom (CELULAR)
| Script | Para qué |
|---|---|
| `zoomnivel.sh 1\|2\|3\|min\|estado` | Niveles de zoom (1 mundo, 2 ciudad completa, 3 lutear). |
| `calidad.sh` | Zoom a la calidad estándar de mapeo (T=738). |
| `camara.sh DX DY` | Mueve la cámara (reparte en pasos largos; <80 px un swipe sería un toque que mueve al personaje). |
| `zoomj.sh`, `zoom.jar` | Zoom con dos dedos (inyección de toques). `zoom.sh`/`pinch.sh` son intentos viejos. |

## Luteo de una casa (CELULAR)
| Script | Para qué |
|---|---|
| `estado.sh` | Qué se ve: BATALLA / PUERTA / LUPA / AGOTADO / OTRO / NO_HORIZONTAL. |
| `casa.sh` | Casa completa: lupa, lámpara, lutear, saquear (versión larga). |
| `lutear.sh` | Lazo ≤60 s: preparar + Buscar + puerta + saquear; termina en `LISTO_PARA_RECOGER`. |
| `preparar.sh`, `rareza.sh`, `luz.sh N`, `sinlampara.sh` | Rareza de los objetos, elegir/equipar lámpara, detectar lámpara vacía. |
| `buscar.sh`, `puerta.sh`, `pago.sh`, `pagox.sh`, `fin.sh`, `saquear.sh` | Pulsar Buscar, puerta bloqueada, evitar el botón de pago, esperar fin de acción, "Saquear a todos". |
| `casa_auto.sh` | Semi-automatico: lupa, puerta, linterna, lutear, saquear x2; si sale la ventana de enemigo: encuentro.sh + saquear x2 otra vez (resultado en casa_auto.res: LISTO_PARA_RECOGER / LISTO_TRAS_BATALLA / PARA_*). |
| `linterna.sh` | Identifica la linterna puesta por su icono (FAROL/POTENTE/VACIA/DESCONOCIDA). |
| `rc.sh FLAGS` | Recoger con cadena 1/0 y cerrar la lupa. |
| `recoger.sh FLAGS`, `malos.sh` | Recoger objetos con cadena 1/0 (o recoger todo menos casillas malas). |
| `guarda.sh`, `hash.sh`, `px.sh x y` | Comprobar horizontal/combate, huella de pantalla, leer RGB de puntos. |

## Combate (CELULAR)
| Script | Para qué |
|---|---|
| `encuentro.sh [max_s]` | Ventana "Has visto a un enemigo": Batalla automática; si "demasiado fuerte" → OK → Ataque → batallaD.sh. |
| `batallaD.sh [max_s]` | Combate completo (pergamino, armas, turnos mío/muñeco, victoria). Usa el servicio Java. Log: `bc.log`. |
| `bcv.sh CMD`, `bcv.jar` | Cliente del servicio de visión/toques (`ver`, `turno_mio`, `turno_muneco`, `mover`, `tap`…). Fuente: SANDBOX `bcv/Bcv.java`. |
| `batallaC.sh`, `batalla.sh`, `accion.sh`, `aliado.sh`, `enemigo.sh`, `esperar.sh`, `pasar.sh`, `quien.sh`, `spam.sh`, `bc_det.sh` | Versión vieja en shell (respaldo) y sus detectores. |

## Otros
- Abrir el juego: `am start -n com.gm_shaber.dayrpremium/com.ansca.corona.CoronaActivity` (CELULAR).
- Respaldos del jar: `bcv_v*_*.bak` (CELULAR); fuentes y jars de cada versión en SANDBOX `bcv/`.
- Ignorar: `d_*.sh`, `d.sh`, `editorpro-*`, `start-miichan.sh` (otros proyectos).

## Combate: fuente, pruebas y despliegue (actualizado 2026-11-05, jar v20)
| Archivo | Para qué |
|---|---|
| `skill_dayr/Bcv.java` | Fuente ACTUAL del servicio de visión/toques (v20). Compilar: `javac --release 8 -d clsNN Bcv.java`; D8 (`zoomj/r8.jar --min-api 26`) -> classes.dex -> zip bcv.jar; instalar copiando en bloques base64 de 1800 chars con md5 por bloque y por línea (100 chars). |
| `skill_dayr/regresion.sh` | Corre ANTES de desplegar: slots por icono (5 raws), turno MIO/MUNECO (muneco.raw, h3_lista.raw, muneco_pegado.raw). Espera carpeta `bcv/t12` con los raws y `clsNN`. |
| `skill_dayr/t12/` | Raws de prueba (clasico1/2, nuevo_dim, enemigo_azul, muneco_pegado, muneco, h3_lista...) y T3..T8.java (T4 lista enemigos, T7 turno, T8 enemigos+aliados). |
| `skill_dayr/enemigo_azul.raw`, `muneco_pegado.raw`, `interiores.png` | Casos reales que causaron bugs (aro azul, muneco pegado, aliado con interior verde). |
| Respaldos en celular | bcv_v14..v19 *.bak; `encuentro_v2.bak`. Servicio: `sh bcv.sh ping`. |

## PENDIENTES (para la proxima sesion)
1. Celu (MCP) se desconecto y esta sesion no lo recupero: abrir sesion nueva con el celular conectado. Celular: bcv.jar = v20 ya instalado.
2. Combate: el usuario vio que el muneco/invocacion quiza todavia le dispare al jugador (no arreglado a proposito; pedir el momento exacto y mirar bc.log + captura). Riesgo anotado: `mover` puede dar SIN_MEJORA si el enemigo ya esta en celda iluminada sin linea de tiro.
3. Aliados "diferentes" (sin interior verde) podrian contarse como enemigos; medir su aro/interior con captura si pasa.
4. Falta probar en vivo el evento "Mujer / casa encantada" (el script pulsa la 1a opcion del pergamino solo; requiere el juego al frente y horizontal, sin cambiar de app).
5. Luteo Zaraysk: continuar en la parada 11 "Colegio - Biblioteca" (nav.py + casa_auto.sh + rc.sh); radiacion 0 (efecto desconocido), peso ~1043/1050.
6. Reglas operativas: no tocar el juego si el celular esta en vertical/otra app; el script termina con SIN_VENTANA o JUEGO_FUERA en ese caso.

## Imagenes de referencia
`REFERENCIAS.md` y carpeta `referencias/` (prohibidos, ruinas, multisitios, 68 capturas de Royer por categoria).

`scripts_celular/` = copias de `batallaD.sh` y `encuentro.sh` actuales (el resto de scripts sigue solo en el celular, ver GUIA_RAPIDA.md seccion 4). `encuentro.sh` ya no esta en la raiz.
