# Luces y efectos de la referencia Mem-Cho

Referencia aportada por Royer: 1080 × 1300, 30 fps, 14,933 segundos. Se revisaron sus 448 fotogramas. Los tiempos son aproximados: el video publicado contiene el resultado, pero no permite recuperar las capas, el programa ni los presets originales del autor.

## Qué ocurre y cómo hacerlo en la app

| Momento | Qué se ve | Herramientas |
| --- | --- | --- |
| 0–3,6 s | Imagen casi monocroma, contraste fuerte, borde fino cian/magenta; acercamientos y giros suaves. | Ajustes, Separar RGB y movimiento con curvas, ya disponibles. |
| 1,1–1,3 s | Ampliación y arrastre desenfocados con flash. | Zoom/latigazo, desenfoque direccional y Flash. Son efectos combinables. |
| 2,32–2,49 s | Paneles verticales con versiones claras y negativas de la cara. | Partes de la imagen, regiones y movimiento por pieza; Negativo. Sigue siendo una composición manual: esta entrega no añade un preset automático de paneles. |
| 3,43–3,57 s | Dos pulsos blancos localizados en la cara. | Foco local con opacidad animada. La referencia no demuestra aquí una alternancia inequívoca entre izquierda y derecha; se añadió también esa función solicitada por Royer. |
| 3,70–4,00; 6,13–6,27; 8,20–8,50 s | El plano entrante aparece sobre el anterior, con un personaje destacado, cambio de escala y, en ocasiones, una cuña blanca. | Capas, recorte y movimiento existentes. Para aislar la silueta en un fondo complejo hace falta una fuente con transparencia o preparar una máscara del personaje. El croma ayuda solo cuando el color permite separarlo. No se añadió segmentación automática. |
| 4,1–4,4; 5,8–6,1 s | Iris cian brillantes y destellos localizados. | **Luces > Foco**: colocar uno en cada iris, ajustar anchura/altura, núcleo, resplandor y color. La mezcla luminosa conserva el detalle del ojo. |
| 6,40–6,73 s | Luz azul/cian a un lado y rosa/violeta al otro, con semitono que desaparece. | Dos focos grandes con colores distintos y **Semitono** durante un tramo corto. |
| 6,97–7,07; 11,47–11,53 s | Neón violeta en zonas del brazo y lateral del personaje, que disminuye después. | **Luces > Trazo**: dibujar solo esas zonas. Animar opacidad o pulsar Revelar, Recorrer o Borrar y afinar sus claves. No requiere contornear todo el personaje. |
| 7–7,8 s | Baño amarillo, luz amplia y líneas de pantalla. | Resplandor de luces con tinte amarillo; Semitono en modo Líneas. |
| 8,53–8,67; 9,13–9,27; 9,67–9,80; 10,80–10,93; 11,37–11,50; 11,90–12,10; 12,43–12,57 s | Arrastre o zoom oblicuo, blur, flash y semitono al entrar el nuevo plano. | Combinar transiciones/movimiento, Desenfoque direccional, Flash y Semitono. **Línea de luz** ahora admite giro durante el barrido, halo y textura. |
| 9,30; 12,67 s | Pasada de colores por fondo y contornos. | Luces locales movidas con claves, o Línea de luz con Aurora al cambiar de plano. Son construcciones editables, no extracción de los presets originales. |
| 9,53–9,60; 10,23–10,60 s | Contornos del cabello y cara con colores arcoíris. | **Bordes arcoíris** detecta variaciones de la imagen y permite graduar umbral/grosor. Para elegir exactamente qué línea se ilumina, usar trazos manuales. |
| 13–14 s | Contracción horizontal, logo blanco pequeño y ráfagas de glitch/RGB. | Texto o logo, brillo, Glitch, RGB y cierre/revelado horizontal existentes. |
| 14,03–14,33 s | Retirada por franja oblicua, que se estrecha y se desvanece. | Máscara lineal, giro, opacidad y claves. |
| 14,4–14,93 s | Negro. | Fin deliberado del montaje. |

## La herramienta Luces

Elegir una foto, video o texto y tocar **Luces**. Las tarjetas muestran Trazo, Foco, Estrella y Anillo. El dibujo y la colocación se hacen sobre una vista del propio elemento; el resultado completo se ve en el lienzo principal.

- **Trazo:** arrastrar el dedo sobre el contorno que se quiera iluminar. Cada gesto crea una ruta independiente. La ruta queda ligada al elemento y acompaña su movimiento y perspectiva.
- **Foco:** luz ovalada, adecuada para un iris o una zona amplia. Arrastrar para colocar; anchura/altura cambian su proporción; Núcleo cambia la concentración del brillo.
- **Estrella y Anillo:** destello y contorno ovalado. El anillo también admite revelado y borrado progresivo.
- **Estilo:** muestras de color y RGB libre, intensidad, grosor o núcleo, resplandor, tamaño y giro. Resplandor cero elimina el halo. Cada luz puede duplicarse o eliminarse.
- **Claves:** cada luz tiene su minilínea de tiempo, rombos, búsqueda anterior/siguiente y curvas independientes. Añadir una clave, avanzar el cabezal y recolocar permite seguir manualmente ojos, pelo o brazos. Se interpolan posición, escala, giro, color, intensidad, grosor, halo y tramo visible.
- **Revelar, Recorrer y Borrar:** crean una animación inicial de hasta 0,6 segundos desde el cabezal. Los valores de sus claves y sus curvas se pueden afinar después. Foco/Estrella ofrecen Encender para animar opacidad.

Los controles de curvas usan el mismo editor de presets, Bézier y curvas de velocidad del resto de la app. Cada luz conserva su animación al cortar el clip y escala sus tiempos al cambiar la velocidad. No hay seguimiento automático ni reconstrucción automática del contorno del personaje.

## Nuevos efectos de pantalla

Se encuentran en el catálogo de **Efectos**, en los grupos Luz e Imagen, con el tramo y la selección de capas ya existentes:

- **Luz alterna por mitades:** un lado se ilumina y después el otro. Control de corte visual, dirección, centro, suavidad, color, rapidez y concentración del halo sobre los contornos. Es independiente del efecto Mitades que desplaza piezas de la imagen.
- **Resplandor de luces:** selecciona zonas luminosas según un umbral y extiende su brillo. Radio, tinte, intensidad y suavidad del umbral.
- **Semitono:** puntos o líneas, con tamaño, orientación, color e intensidad. Puede entrar fuerte y desaparecer mediante los fundidos del tramo.
- **Bordes arcoíris:** color luminoso sobre variaciones de la imagen, con umbral, radio, intensidad y velocidad del color. También detecta detalles del fondo; para selección artística exacta, elegir trazos.

Los nuevos efectos respetan el alcance de capas, los tiempos y la transparencia del elemento.

## Línea de luz ampliada

En la transición **Línea de luz**, las tarjetas Clásico, Neón, Giro violeta y Aurora configuran estilos editables. El recorrido opcional tiene dirección inicial, intermedia y final. La curva de la transición controla el desplazamiento y el giro. Halo, núcleo, irregularidad y color son independientes; Aurora añade variación de color.

La transición combina las escenas completas de ambos clips. La máscara de revelado y la luz comparten orientación y borde, para evitar que la decoración se despegue del cambio de plano. Un barrido luminoso y los paneles de 2,3 s son técnicas distintas.

## Cómo se encadena el estilo

A partir de unos 4,6 segundos se repite con variaciones esta secuencia:

1. Sube el brillo y la separación RGB.
2. Aparece un cuadro oscuro o con canales de color aislados.
3. Un arrastre/zoom con flash dura unos 2–5 cuadros.
4. Entra el plano con semitono durante unos 3–5 cuadros.
5. La trama desaparece y queda movimiento suave.

Muchas ráfagas están separadas aproximadamente 0,55–0,60 segundos. Es una observación del ritmo visual, no una medición del tempo del audio. Una sola transición no reproduce toda esa combinación: los tramos permiten componerla y adaptarla al material del usuario.
