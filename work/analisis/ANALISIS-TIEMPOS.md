# Análisis visual testprueba1

Referencia: `675cab0eadd1705a9794a2f651390bed.mp4`, 576×768, 30 fps, duración 17.70 s. Tiempos inspeccionados cuadro a cuadro en 4.700–13.867 s. Precisión temporal de los puntos: un frame (33.3 ms); los nombres describen apariencia, no prueban qué aplicación/preset originó cada efecto.

## Límites

- Intro en blanco y negro: 0.000–5.300 s (incluye salida).
- Primeras tiras del fondo del montaje aparecen ya en 4.967–5.000 s; la tarjeta negra se aplasta/cierra verticalmente al centro en 5.033–5.267 s. Primer cuadro del montaje sin intro: **5.300 s**.
- Si se quiere reutilizar un intro limpio sin incorporar la primera imagen ajena, cortar a **4.967 s** y hacer su salida sobre la primera foto propia durante 0.333 s.
- Montaje de imágenes: 5.300–12.633 s. Dos ciclos de la misma secuencia, separados por 3.667 s.
- Última imagen se barre a blanco en 12.567–12.633 s; blanco se desvanece hasta negro durante 12.633–13.533 s.
- Negro final: 13.533–13.700 s.
- **Outro TikTok: 13.700–17.700 s**, primer cuadro con logo grande exactamente 13.700 s. Se puede excluir conservando el edit completo hasta 13.700 s.

## Secuencia y efectos

A–H son ocho imágenes/planos. No corresponden a nombres de las fotos aportadas; solo indican orden de referencia.

| Inicio s | Fin s | Plano/evento | Animación/transición visible |
|---:|---:|---|---|
| 4.967 | 5.300 | Intro → A | Fondo A se revela desde bordes superior e inferior; tarjeta negra intro se cierra/aplasta verticalmente hacia la mitad. |
| 5.300 | 5.733 | A: retrato marrón con luces cálidas | Zoom corto con sobrepaso y oscilación/temblor horizontal; desenfoque de movimiento al acercarse a salida. |
| 5.733 | 6.233 | B: mano en la frente | Entrada con barrido lateral, desenfoque horizontal fuerte en 5.733–5.900; se estabiliza 5.933–6.000. Salida por máscara circular que encoge la imagen sobre negro en 6.033–6.200. |
| 6.233 | 6.667 | C: retrato claro/infantil | Se abre desde negro con círculo negro central que se encoge hasta desaparecer (6.233–6.533). Leve acercamiento y salida con estiramiento/desenfoque vertical en 6.567–6.667. |
| 6.667 | 7.133 | D: sentada, fondo naranja | Entrada vertical con zoom/rebote y bordes repetidos en espejo (cabeza invertida arriba); estabilización hacia 6.900, nuevo zoom al salir 7.033–7.100. |
| 7.133 | 7.600 | E: retrato sonriente lateral | Corte con movimiento/zoom y ligera rotación, temblor amortiguado; barrido horizontal de salida en 7.500–7.600. |
| 7.600 | 8.033 | F: retrato morado, fondo azul | Entrada barrido/desenfoque lateral 7.600–7.667. Wipe vertical izquierda→derecha revela G desde 7.733; aún queda una tira de F en 8.000; completo G en 8.033. |
| 8.033 | 8.500 | G: retrato verde/dorado | Acercamiento y pequeña inclinación con rebote; desenfoque/estiramiento vertical al salir 8.433–8.500. |
| 8.500 | 8.967 | H: retrato luminoso amarillo | Entrada vertical/zoom fuerte, borde superior duplicado en espejo; luz cálida/alto brillo. Desde 8.700 se aplasta/cierra en banda horizontal al centro para revelar A debajo; completo A8.967. |
| 8.967 | 9.400 | A, repetición | Igual ciclo anterior. |
| 9.400 | 9.900 | B, repetición | Barrido horizontal; máscara circular de salida en 9.700–9.867. |
| 9.900 | 10.333 | C, repetición | Círculo negro central se encoge en 9.900–10.200; salida vertical con blur. |
| 10.333 | 10.800 | D, repetición | Entrada vertical/rebote con imagen repetida en espejo en borde superior. |
| 10.800 | 11.267 | E, repetición | Zoom e inclinación; whip horizontal al salir11.167–11.267. |
| 11.267 | 11.733 | F, repetición | Entrada horizontal; wipe a G desde11.400; último borde azul visible11.700. |
| 11.733 | 12.167 | G, repetición | Zoom/rotación amortiguada; salida vertical 12.100–12.167. |
| 12.167 | 12.633 | H, repetición | Entrada vertical con reflejo de bordes; barrido horizontal a blanco 12.567–12.633. |
| 12.633 | 13.533 | Final blanco → negro | Blanco/gris cae en luminancia casi lineal hasta negro; texto/marca aún aparece durante fade. |
| 13.533 | 13.700 | Negro | Respiro antes de outro de la plataforma. |
| 13.700 | 17.700 | Outro TikTok | Ajeno al montaje: logo, barra de carga y búsqueda. |

## Parámetros recomendados para aproximar con fotos

- Unidad de ritmo aproximada 0.458 s (hay bloques alternados de 13/14 frames y B de15 frames); mantener los tiempos medidos aporta más fidelidad que espaciar todo uniforme.
- Transformación de zoom de entrada/salida, escala sobrepasando ~1.05–1.20 y regreso; amplitud real varía por plano y recorte. Rotación pequeña ~±2–4 grados y vibración lateral amortiguada. No hay evidencia clara de giro de360 grados.
- Motion blur direccional solo en ventanas de80–170 ms alrededor de cortes; horizontal en A/B y E/F, vertical en C/D y G/H.
- Repetición en espejo de bordes durante movimientos verticales evita negro y produce las cabezas invertidas visibles arriba. Es una característica clara de la referencia.
- Iris circular de salida (B) seguida de círculo negro central que encoge (C), con centro cerca de42% ancho/48% alto. Color negro; controla progreso/radio e intensidad de difuminado.
- Wipe vertical F→G de300 ms aproximadamente; sin necesidad de fundido global.
- Cierre de banda horizontal H→A ~267 ms y salida intro ~333 ms.
- El brillo amarillo/cálido de H y el azul/morado de F pueden ser del contenido original. Para fotos diferentes conviene controles de exposición/glow y temperatura, pero no afirmar que había un filtro independiente.
- No veo evidencia suficiente de glitch digital ni separación RGB fuerte. Añadirlos por defecto cambiaría el estilo.

## Firma APK

Consulta y copia solamente; sin instalar ni modificar el teléfono. Dispositivo1243225514100703. Paquete com.royermethod.app, versión1.0/código1. Instalado15-oct-2026 13:56:31.

- Certificado APK instalado SHA-256: `4fb84865dd66eb545577efdc63aa436272e4a22318c4b41b15f42d7b95059dab`.
- Certificado APK local debug y release previo SHA-256: `e1e7eb341eaff2c8e3af33ebb6f810c8e08c8e91df8bc1d73f596b34a90c1765`.
- Las firmas difieren, por lo que actualizar con `adb install -r` fallaría. Usar otra applicationId para probar sin tocar app existente, o recuperar la clave original. La copia leída es `analisis/apk-instalado.apk`.