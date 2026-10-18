# Head bop: investigación y propuesta para test2xd

Fecha de consulta: 17 de octubre de 2026. Investigación de fuentes originales para animar una imagen por piezas en Royer Method. Esta nota no modifica el repositorio.

## Alcance de la evidencia

Se buscaron varios tutoriales de creadores y se intentó abrir cada enlace de YouTube. YouTube devolvió errores de recuperación/429; sólo se pudieron leer descripciones originales indexadas. Por tanto, **no se han visto ni medido sus fotogramas**, no se atribuyen tiempos ni curvas concretas a esos videos. Los procedimientos técnicos de abajo se apoyan en documentación oficial que sí se abrió y leyó. Las cifras de movimiento propuestas son decisiones de trabajo, no mediciones de los tutoriales.

El video musical del usuario debe analizarse por separado antes de fijar duración de intro, tempo, cortes o amplitud. Esta investigación no demuestra que la propuesta coincida con él.

## Tutoriales originales encontrados

1. **Kazi Aidah Haque — [FREE] Front Facing Head Bobbing Tutorial in Alight Motion | Gacha** (11 marzo 2022).
   https://www.youtube.com/watch?v=_CS0uAhu60Y
   La descripción original identifica Gacha Club como fuente del personaje, IbisPaint X para separación/ensamblado, Alight Motion para animación y KineMaster para edición. Evidencia útil: la preparación por piezas es una etapa diferenciada de la animación. No se recuperó el contenido audiovisual.

2. **Kazi Aidah Haque — How to make a head bobbing animation in Alight Motion | Gacha Stu-Club Tutorial** (24 diciembre 2020).
   https://www.youtube.com/watch?v=YbYMGg59fVY
   Enlace original encontrado, pero no accesible directamente. No uso una transcripción alojada por terceros como autoridad técnica. Su contenido queda pendiente de visualización.

3. **čybërspace — How to head bob and some simple transitions! • Gacha Life • Tutorial 3** (9 noviembre 2019).
   https://www.youtube.com/watch?v=Rm7d8ErZf8o
   Descripción original: tutorial de head bob y transiciones sencillas en Cute Cut; lista Cute Cut Pro, IbisPaint y Gacha Life. Otra fuente independiente del flujo de preparación de imagen más animación. No se midieron sus movimientos.

4. **BLACKROSE_BG — HOW TO MAKE HEAD BOP ANIMATIONS IN CAPCUT!** (15 agosto 2026; título mostrado vacío por el índice, nombre tomado de la descripción).
   https://www.youtube.com/watch?v=ry7oMZmazrU
   La descripción original enumera separar cabeza/cuerpo, volver a colocar la cabeza y animarla con keyframes. Es evidencia del método de recorte/ensamblado, no de un pivote o una curva concretos.

5. **Kazi Aidah Haque — How to make a head-turn in Alight Motion 2 [hair] | Gacha Stu-Club Tutorial**.
   https://www.youtube.com/watch?v=kDLPeQD4O5k
   Tutorial original específico de cabello encontrado. Sólo se recuperó título/descripción breve; no extraigo su técnica.

6. **Sanvo — Hair Physics**, enlazado y acreditado por Live2D.
   Video: https://www.youtube.com/watch?v=QtvzIUHGEOo
   Anuncio oficial abierto: https://www.live2d.com/en/information/community-discord-event-learn-live2d-together-vol-2-hair-physics-june-2-30/
   Live2D confirma que el estudio ofrece tutorial y archivos PSD/CMO3 para estudiar física de cabello. No se descargaron ni reutilizaron sus assets y no se pudo ver el video.

## Fundamentos técnicos comprobados en documentación oficial

### Separar y reconstruir lo oculto

[Live2D — About Material Separation](https://docs.live2d.com/4.2/en/cubism-editor-manual/divide-the-material/)

El procedimiento oficial duplica la fuente, identifica partes y usa máscaras de capa para recortar de forma reversible. Indica dibujar las zonas que quedarán visibles cuando se muevan otras piezas y conservar el original separado de esas correcciones. Aplicación aquí: una cabeza recortada no basta si deja detrás una cabeza inmóvil; hay que reparar el cuello, torso y fondo que se descubrirán. También hacen falta solapes bajo cabello/manos para que no aparezcan huecos.

### Pivotes, jerarquía y movimiento local

[Alight Motion — Layer Parenting and Null Objects](https://support.alightmotion.com/hc/en-us/articles/10536997444369-Layer-Parenting-and-Null-Objects)

La jerarquía transmite posición, escala y rotación del padre al hijo, manteniendo animación propia del hijo. Los objetos nulos permiten movimiento común de un conjunto. La documentación también describe arcos combinando movimiento padre/hijo y curvas. Aplicación propuesta: torso → cabeza → cabello; torso → brazo → mano. Los pivotes anatómicos (cuello/hombro/codo/muñeca/raíz del mechón) son nuestra elección para este montaje, no valores prescritos por esa página.

[Live2D — Rotation deformer](https://docs.live2d.com/4.2/en/cubism-editor-manual/making-and-rotation-of-rotationdeformer/)

Confirma que un deformador de rotación padre permite girar los elementos contenidos. Es un respaldo al uso de piezas agrupadas alrededor de un punto de giro, sin tener que animar cada elemento de la cara por separado.

### Easing, anticipación y rebote

[Alight Motion — Animation Easing Curves](https://support.alightmotion.com/hc/en-us/articles/10536934703889-Animation-Easing-Curves)

Explica aceleración/desaceleración, curvas diferentes entre cada pareja de claves y curvas cíclicas, bounce y elastic. Describe overshoot para anticipación o rebote más allá de los valores extremos. Aplicación propuesta: bajar rápido hacia el golpe musical y regresar con desaceleración/rebote controlado; evitar que toda la secuencia sea lineal o un movimiento aleatorio de shake. Usar pocas poses y curvas editables.

### Cabello secundario

[Live2D — How to Set Up Physics](https://docs.live2d.com/en/cubism-editor-manual/physical-operation-setting/)

Separa parámetros de entrada, modelo de péndulo y salida. Permite ajustar velocidad, respuesta, amortiguación/convergencia y amplitud; sus péndulos de varios tramos modelan cabello. La rotación de cabeza/cuerpo puede impulsar la respuesta secundaria y un brazo puede impulsar accesorios. Advierte que superar el rango de salida produce movimiento entrecortado. Aplicación propuesta: raíz pegada a cabeza y puntas con retraso, menor amplitud en flequillo corto y más en mechones largos; evitar saturar el rango. Estos ajustes se han de calibrar sobre la imagen real.

## Propuesta de construcción para Royer Method

Estas son decisiones de diseño a validar visualmente, no una receta copiada ni una coincidencia demostrada con la referencia.

### Piezas mínimas útiles

- Fondo limpio.
- Torso/cuello reconstruidos donde estaban cabeza o manos.
- Cabeza/cara principal, manteniendo proporciones e identidad.
- Cabello trasero y 2–4 mechones delanteros/laterales si la imagen lo permite. No hace falta separar cada pelo.
- Brazo/antebrazo/mano visibles que realmente tengan margen para moverse. Dibujar zonas ocultas sólo donde el movimiento las revela.
- Conservar objetos/accesorios como hijos de su parte correspondiente.

Usar PNG transparentes alineados sobre un mismo lienzo. Incluir solape de unión suficiente; comprobar los bordes con contraste claro/oscuro. Un recorte rectangular de “mitad superior/inferior” no sustituye la silueta del personaje.

### Movimiento primario sincronizado

1. Detectar golpes reales de la música, verificándolos escuchando y con la envolvente de audio. No asumir BPM ni un tempo constante sin comprobarlo.
2. Tomar cada golpe como ancla del punto bajo del bob. El patrón puede tener pocas poses por pulso: anticipación → golpe → rebote → reposo. Mantener continuidad de posición/velocidad en el bucle.
3. Como arranque visual prudente: recorrido vertical de cabeza alrededor de 1–2 % del alto de la composición y giro de unos 2–5 grados, regulables. Depende del encuadre. El torso acompaña menos; así no parece que todo el recorte sea una tarjeta rígida.
4. Alternar un poco la inclinación entre golpes, usando un patrón determinista que pueda editarse. Evitar ruido aleatorio que esconda el ritmo.
5. Usar un control maestro de amplitud y otro de rebote/curva para recalibrar el movimiento sin editar cada pulso.

### Cabello y manos

- Cabello hereda el movimiento de cabeza. Sobre él se suma oscilación local con retraso: como punto de partida, 0,08–0,15 de un pulso para mechones cortos y 0,15–0,25 para largos; ajustar visualmente. Son propuestas nuestras, no cifras de Live2D.
- Las raíces no se despegan; las puntas continúan un momento cuando cambia la dirección de la cabeza. Un péndulo amortiguado o curva equivalente evita acumular claves por cuadro.
- Manos acompañan en arcos alrededor de hombro/codo/muñeca, con menos amplitud y un desfase distinto al del cabello. Si la pose es una mano cerca de la cara, priorizar coherencia del contacto y evitar separarla por moverla “porque sí”.
- No deformar los rasgos para simular gran giro 3D si sólo tenemos una vista. Un head bop 2D moderado puede mantener la ilustración fiel y limpia.

### Intro que muestra construcción

Fijar su longitud tras estudiar el video musical. Puede mostrar la fuente, separar visualmente las piezas en una vista de despiece, señalar brevemente pivotes y hacerlas regresar a su pose. Después, primer bob con cabeza sola, incorporación de cabello/manos y entrada de la animación completa en un golpe fuerte.

Debe utilizar las piezas y movimientos reales del proyecto, no fingir funciones o capturas de interfaz que la app no tiene. Al comenzar la parte final, retirar guías/rótulos para dejar protagonista al personaje y la música.

## Validación necesaria

- Reconstrucción estática primero: piezas reunidas conservan el aspecto y no duplican cabeza/cabello/manos.
- Verificar fotogramas extremos y contactos: sin grietas, cuello cortado, fondos duplicados ni halos de recorte.
- Verificar sincronía golpe/punto bajo en varios pulsos al principio y al final.
- Comparar vista previa/exportación con los mismos parámetros; no atribuir un movimiento diseñado a lag sin medir presentación de cuadros.
- Ajustes editables visibles: intervalo, amplitud vertical, giro, rebote, fase, pivote y respuesta secundaria por parte.

