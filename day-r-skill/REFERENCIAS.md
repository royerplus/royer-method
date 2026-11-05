# REFERENCIAS VISUALES (fotos que Royer mando y capturas de evidencia)
Una sesion nueva debe MIRAR estas imagenes para reconocer cosas en pantalla (que recoger, que no, edificios prohibidos, ventanas, combate). Estan reducidas (max 1400 px, JPG) salvo los iconos PNG. Las categorias de `usuario/` las arme viendo miniaturas: abrir la imagen para confirmar antes de fiarse de la descripcion.

## 1. Objetos a NO recoger (regla en `luteo_referencia.md` y `SKILL.md`)
| Imagen | Que es / regla |
|---|---|
| `prohibidos/tv_interrogacion.png` | Tele con "?" -> NO recoger. |
| `prohibidos/mesa_madera.png` | Mesa/sillas de madera -> NO. |
| `prohibidos/horno_destrozado.png` | Horno de ladrillos destrozado -> NO. |
| `prohibidos/gasolina_roja.png` | Bidon rojo (gasolina): lo guarde en `prohibidos/`; confirmar la regla con Royer antes de dejarlo siempre. |
| `usuario/objetos_a_recoger_o_no/` | Reloj dorado (objeto dorado, rareza alta), horno destrozado junto a abeja (abeja = NO), mesa de madera "100%" (NO), bicicleta con icono ROJO de reparar (arma/vehiculo danado = NO). |
Resumen de exclusiones: tele "?", abejas, cadaveres, armas "(CON DANOS)" con icono rojo de reparacion (la barra verde sola NO basta), mesas/madera, vehiculos, horno destrozado, objeto no recogible en la 1a casilla (huerto) -> verificar con `recoger.sh`.

## 2. Edificios prohibidos y variantes (ruinas, crateres)
`ruinas/` = iconos del juego: `Ruin_brick2`, `Ruin_white2`, `Ruin_wooden`, `Ruin_yellow`, `burned_shop`, `contaminated_store`, `crater`, `crater_polluted`, `cursed_house` (`hoja*.png` = hojas de sprites, `shop_halloween.png` = icono de monedas). **Ruinas y crateres: nunca lutear.** Una "Casa maldita"/"Casa intacta" valida tiene un solo sitio. Los cinco disenos prohibidos que dio Royer estan descritos en `luteo_referencia.md`.

## 3. Edificios con varios sitios (una parada por sitio)
`multisitios/1..8.jpg` (ver `MULTISITIOS.md`): 1-4 Colegio (Biblioteca, Aulas, Cantina, Gimnasio), 5-6 Clinica (Edificio principal, Salas), 7-8 Ruinas (Entrada 1/2, prohibidas). El nombre "Edificio - Sitio" sale sobre el personaje al tocar.

## 4. Luteo dentro de la casa (lupa)
`usuario/lupa_luteo_y_listas/`: listas de objetos por tipo/cantidad ("Recoger todo", "Saquear a todos"), barra "Buscar 1/2", "No queda nada", probabilidades de encuentro, puerta bloqueada, panel "Selecciona una fuente de luz" (linternas). Coordenadas y scripts: `luteo_referencia.md`, `GUIA_RAPIDA.md`.

## 5. Combate por turnos
- `usuario/combate_tablero/`: tableros reales (fichas, aliados, enemigos, casillas iluminadas, panel de armas a la izquierda, habilidades del muneco). Sirven para entender aros (naranja = activo; azul/verde = barras de vida), aliados (interior VERDE) y rango.
- `usuario/combate_ventanas_y_armas/`: ventana "Has visto a un enemigo" (Batalla automatica / Ataque), "El enemigo es demasiado fuerte" y "Armamento disponible".

## 6. Mapa de ciudad y mapeo
- `usuario/mapa_ciudad_vistas/`: vistas de la ciudad (Zaraysk) a distintos zooms, con circulo de ciudad, personaje y nombres.
- `usuario/mapeo_marcado_rutas/`: mapeos ya marcados (cuadros VERDE = valido, ROJO = prohibido, AMARILLO = dudoso) y ruta numerada de Zaraysk.

## 7. Eventos (pergamino con opciones)
`usuario/eventos_pergamino/`: dialogos de evento (casa con presencia/fantasmas, bestias, etc.). El script pulsa la 1a opcion solo (`pergamino()` en `batallaD.sh`).

## Pendiente
Ver `GUIA_RAPIDA.md` (usar script antes que toques a mano) y `INDICE.md` (pendientes de la proxima sesion).
