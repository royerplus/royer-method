# Skill: mapear una ciudad entera en mosaico (para distinguir edificios validos / no validos)

Objetivo: una sola imagen de la ciudad completa con resolucion suficiente para ver el TECHO de cada edificio.
Criterio de validez: techo con huecos o vigas a la vista = ruina (NO). Techo entero = usable, aunque tenga plantas.
Resolucion util: edificios de ~100 px de ancho (a la mitad se confunden ruinas con edificios gastados).

Archivos: `plan_mosaico.py` (calcula las tomas), `mosaico.py` (une), en el celular `zoomj.sh` y `camara.sh`.

Pasos
1. Nivel ciudad: `zoomnivel.sh min`, luego `2`, luego acercar a la vista de trabajo (en Shatki: `zoomj.sh in 1218 540 700 150 723`). Captura.
2. `python3 centro_ciudad.py captura.png` -> mide el circulo (aunque la barra inferior lo tape) e imprime
   `sh camara.sh DX DY` para CENTRARLO y el diametro D. NUNCA centrar a ojo: un error de 40 px a x1.6 son 64 px y deja el borde fuera.
3. Aplicar `camara.sh DX DY`. Captura de comprobacion: el circulo debe verse centrado.
4. FACTOR: 1.0 si los edificios ya miden >= 100 px; si no, 1.6 (2.4 solo si siguen chicos).
5. `python3 plan_mosaico.py D FACTOR` -> zoom + lista de `camara.sh` por toma (cubre el anillo con margen de 30 px).
6. Zoom, ir a la toma 1, captura; repetir camara + captura.
7. `python3 mosaico.py ciudad.png t1.png t2.png ...`
8. `python3 cobertura.py ciudad.png` -> debe decir COMPLETA. Si dice FALTA <lado>, repetir con una toma mas por ese lado.
9. Leer por cuadrantes ampliados y marcar rojo = no usable / verde = usable.

Reglas medidas
- No cambiar el zoom entre tomas: el unido es solo traslacion.
- Camara: el mapa sigue al dedo ~1:1 (ordenado 372 -> medido 367; 548 -> 546). Maximo ~800 px por movimiento.
- Zoom: ln(escala) = 0.001378 x px de recorrido de los dedos (`zoomj.sh in 1218 540 700 150 150+recorrido`).
- Zona util por captura 1440 x 820 px (fuera quedan HUD y barra inferior).
- Probado: Arzamas (ciudad grande) x1.6 en 2 tomas, desplazamiento 546 px, 1136 coincidencias.
         Shatki (D=745) x1.6 en 2 tomas, desplazamiento 367 px, 1628 coincidencias, mosaico 1440x1187. Ruinas visibles.
- Sin acercar (x1.0) una ciudad chica ya se lee, pero las ruinas dudosas (bloques amarillos con plantas) solo se separan a x1.6+.
- Lo que el color NO distingue: ruina vs edificio intacto con plantas, y luteado (sepia/blanco y negro = ya usado).
- Cada captura pesa ~3,5 MB en el contenedor: no pasar de ~8 por ciudad.
- Sin probar: x2.4 con 8 tomas (2 x 4); el plan lo calcula pero no lo he ejecutado.

Hallazgos de la verificacion (cobertura.py sobre mosaicos ya hechos)
- Arzamas en 2 tomas: anillo cortado por unos 3-7 px en 3 lados (practicamente completo, pero NO garantizado). Con el plan nuevo seria 3 tomas.
- Shatki en 2 tomas: faltaban 66 px del borde de abajo. Causa: centre la ciudad a ojo (error 40 px) y la zona util no esta centrada en pantalla.
- Medido: la zona util de una captura va de x -698..+742 e y -430..+390 respecto al centro de la pantalla (no es simetrica).
- Gotcha (corregido): el personaje que se vio caminar a la Comisaria lo movio Royer con su dedo, no un camara.sh. Si el personaje
  cambia de sitio o aparece una etiqueta de edificio, puede ser el usuario usando el celular: no sacar conclusiones, volver a medir.
- Gotcha: en `zoomj.sh in|out CX CY DUR D0 D1` el modo solo fija valores por defecto; manda la distancia D0->D1 de los dedos
  (D1>D0 acerca, D1<D0 aleja). Para ALEJAR 1079 px de recorrido desde el minimo: `zoomj.sh out 1218 540 450 1100 200` y `... 1100 921`.
- Verificado de principio a fin en Shatki (D=741, x1.6, 2 tomas): centro con error de 2 px, desplazamiento 426, mosaico 1440x1246,
  cobertura.py = COMPLETA (margenes 30 / 37 / 130 / 130).

Posicion del personaje y viaje (Royer)
- Antes de tomar el mosaico, dejar al personaje FUERA de los edificios, sobre una carretera (etiqueta "Carretera"), y si se puede en el borde
  de la ciudad. Su icono (~90 px) tapa un edificio a x1.6 y, dentro de uno, la etiqueta del nombre tambien tapa. Si esta dentro de un
  edificio, el mosaico no lo muestra bien: mover al personaje antes (tocar una carretera) y medir de nuevo.
- Para viajar de ciudad a ciudad NO hace falta el tope de alejado (nivel 1): basta ~la mitad (T ~ 1800 px desde el minimo). Se ve la
  ciudad destino y las vecinas. Sin probar el numero exacto; ajustar si no aparece el destino.
- Al llegar a una ciudad desde el mapa del mundo: bajar a la vista de trabajo (min -> 2 -> acercar 573 => T=1079) y medir con centro_ciudad.py.

CALIDAD FIJA (Royer: misma calidad para todas las ciudades, solo cambia el nº de tomas)
- Calidad estandar = T=738 (zoom absoluto desde el minimo). Se aplica con `calidad.sh` en el celular (min -> 1079 -> -341). Es lo que se uso en Shatki.
- `plan_mosaico.py D T_MEDIDO [T_OBJETIVO]` calcula factor = exp(0.001378*(T_medido-T_obj)) y las tomas. Shatki = 2 tomas, Arzamas = 8 (2x4).
- Arzamas a T=738 (probado, 8 tomas): uniones perfectas (567/398/560/395/566/394/563 px, 1100-2300 coincidencias c/u), mosaico 2013x2007.
  FALLO: el borde SUPERIOR de la ciudad queda cortado (el complejo amurallado sale cortado arriba) y los bordes laterales quedan a ~0 px.
  Causa: el plan uso un diametro ESTIMADO (686 x 2.84 = 1948) y el real a esa calidad es ~2000+. El factor 2.84 no estaba medido.
  Regla: NUNCA usar un diametro estimado. Medir D a la calidad final (centro_ciudad.py despues de aplicar calidad.sh y alejar es inutil:
  el circulo no cabe). Alternativa: anadir 1 fila/columna extra de margen en plan_mosaico (M = 120 px en vez de 30).
- BUG de cobertura.py: con el anillo enorme y cortado ajusta otro objeto dorado (dio diametro 534 y "COMPLETA" falso). No fiarse:
  mirar el mosaico y comprobar que se ve el anillo en los 4 lados.

Arzamas COMPLETA a T=738 (10 tomas = 2 x 5): se anadieron 2 tomas de la fila superior; mosaico 2013 x 2257, anillo visible arriba y abajo.
- Si el usuario mueve la camara a mitad de la secuencia: tomar captura y ubicarse con `mosaico.desplaza(tile_conocida, captura)`
  (ambas tiles vecinas dieron la misma posicion, 2000+ coincidencias). Luego `camara.sh (destino - actual)`.
- Posicion de la fila nueva: tile 1 + (0,-250) y + (560,-250). Colocar nuevas tiles con coincidencias contra la vecina (error < 1 px).
- Lados izquierdo/derecho: el anillo queda a ~0 px del borde del mosaico (el crop de la zona util X0=520 recorta ~20 px del edificio mas a la izquierda).
  Para el siguiente mapeo usar margen M=120 en plan_mosaico.py.
- Lo sepia/blanco y negro = ya luteado: no se marca. Todo lo que conserva color y no es ruina/crater sin enemigos = verde.

LIMITES DE LA CIUDAD (Royer): mapear tambien el borde izquierdo, derecho e inferior (y el superior), con holgura FUERA del anillo.
- plan_mosaico.py ahora usa M=150 px de margen por lado (antes 30). Comprobar a ojo que se ve el anillo completo en los 4 lados
  (cobertura.py falla con anillos grandes, no fiarse).

Arzamas con LIMITES COMPLETOS (probado): 10 tomas del primer mapeo + 8 nuevas (derecha x2, abajo x1, fila inferior x3, izquierda x2) = 18 tomas, mosaico 2639 x 2501.
- El anillo se ve entero en los 4 lados con terreno vacio fuera (la franja negra arriba a la izquierda es zona no capturada, sin edificios).
- Las tomas nuevas se colocan por coincidencias contra la mejor vecina ya colocada (1300-2500 coincidencias cada una). No hace falta rejilla exacta.
- Las marcas hechas sobre un mosaico anterior se pasan al nuevo sumando el desplazamiento de origen (aqui +291,+38).
- No aparecieron edificios nuevos fuera del anillo: lo de fuera son banderas de campamento, el edificio de fuego (base) y terreno.

SASOVO (probado, 15 tomas = 3 x 5, mosaico 2735 x 2720, anillo completo en los 4 lados)
- BUG DE camara.sh (arreglado): si DX/DY no eran multiplos del paso maximo quedaba un deslizamiento FINAL CORTO (28-36 px) y el juego lo toma como
  un TOQUE => el personaje camina (paso junto a una Tienda contaminada y salto una incursion). Ahora el movimiento se reparte en pasos IGUALES y largos;
  si el total es <80 px no se mueve. Royer: NO mover al personaje; si hay que moverlo, hacerlo LO PRIMERO (para ver el camino antes).
- Un aviso de incursion abierto bloquea las capturas siguientes: cerrar con "Salir" (nunca "Empezar incursion").
- Si el personaje camina, la camara se recentra en el y se pierde la referencia. Recuperar: nivel ciudad (zoomnivel min, 2), centro_ciudad.py,
  camara.sh para centrar, calidad.sh y camara.sh hasta la toma siguiente CALCULADA (aqui (624,-458) = toma 4). Comprobado: toma3->toma4 = (-1,483), 380 coincidencias.
- Ajustar un circulo a un arco corto NO sirve (radio 244 en vez de ~1200): no usarlo para ubicarse.
- El mosaico se une colocando cada toma contra la mejor vecina ya colocada (380-2000 coincidencias). Las costuras solo se notan en el terreno vacio fuera del anillo.

Sasovo marcado: 11 rojos (ruinas de techo/pared caida, edificio negro quemado, cráter seco) y 52 verdes (incluye sitios con enemigos: 4 cráteres de fuego, 3 tiendas contaminadas, 2 campamentos, cabaña con fuego).
Dudas que quedaron en verde: edificio pequeño desvaido junto al arco este (puede estar ya luteado), y el gris grande del colegio (color tenue).
