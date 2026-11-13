# Revisión de orden y experiencia del editor

La revisión se hizo renderizando la interfaz Compose real con Robolectric y gráficos nativos de Android, con el elemento de referencia como recurso de prueba. Se capturaron las mismas pantallas antes y después a 360 × 800 y 320 × 640, también con texto ampliado al 130 %. La vista de video de estas pruebas es una fuente de prueba; las capturas validan la distribución y los controles, no el rendimiento del motor de video en un teléfono.

## Problemas observados y cambios

| Problema observado en las capturas | Cambio |
| --- | --- |
| La vista de colocación de Luces quedaba cortada; con claves y texto grande ya no aparecía. | Panel ampliado al abrir, con opción de reducirlo manteniendo visible el lienzo. Luces separa Colocar, Estilo y Animar. |
| La barra de claves ocupaba espacio en Texto, Borde y Ajustes aunque el usuario solo quisiera cambiar su apariencia. | Botón Animar explícito. Las opciones de canales y curvas aparecen al entrar en esa función. |
| Las tarjetas de tipos de luz parecían opciones de edición, pero cada toque añadía un objeto. | Añadir abre su propio catálogo; elegir una luz existente usa tarjetas con icono, color y estado seleccionado. |
| Borrar y Eliminar se confundían. | Desvanecer crea la animación de salida; Eliminar luz se mantiene como acción de gestión separada. |
| Grosor 0,006 y halo 0,025 aparecían como 0; otros valores fraccionarios parecían no cambiar. | Porcentajes y decimales legibles, manteniendo los valores reales del proyecto. |
| Tocar el número de un deslizador restablecía su valor sin una indicación clara; arrastrar para bajar la página podía tocar el ajuste. | Botón explícito de restablecer y gesto horizontal para cambiar el valor; el vertical permite desplazar la página. |
| Exportar se partía en dos líneas y el reloj invadía Reproducir con texto ampliado. | Ancho reservado para Exportar y reloj de dos líneas separado de los botones. |
| Orden y Marcos aparecían antes de las acciones habituales de edición; la flecha saltaba al final de una barra muy larga. | Dividir, Texto/Velocidad, Animar, Ajustes y Efectos cerca del inicio; Orden y Guías al final. La flecha avanza por páginas y la barra vuelve al inicio al cambiar selección. |
| RGB, curvas, limpieza y opciones avanzadas competían con los controles básicos. | Secciones plegables para opciones secundarias. Croma/Máscaras/Animar separados; selector de color con acción explícita. |
| Cambiar de pestaña conservaba el desplazamiento anterior y podía abrir una sección desde su final. | La hoja vuelve arriba al cambiar sección u objeto. |

## Uso

- **Luces:** Añadir → elegir un tipo o dibujar un trazo. Colocar muestra la imagen y los gestos. Estilo reúne color, intensidad, grosor/núcleo y halo. Animar reúne claves, curvas y presets. Tamaño/giro, RGB y gestión de objetos son secciones independientes.
- **Recorte:** Croma y Máscaras conservan sus controles específicos; Animar reúne los keyframes. Las figuras conservan su preview y los ajustes de tamaño/giro se despliegan cuando hacen falta.
- **Texto, Borde y Ajustes:** editar directamente; abrir Animar para cambiar canales y curvas. Un punto indica que ya hay claves.
- **Paneles:** el asa superior permite ampliar/reducir el panel. En pantallas bajas se limita su tamaño para conservar una parte de la vista previa.
- **Capas afectadas:** resumen compacto de alcance; tocarlo abre la selección por miniaturas. Crear un ajuste compartido o compartir un efecto sigue disponible desde ese apartado.

Los proyectos, efectos, curvas y valores guardados mantienen su formato. No se cambió el motor de renderizado ni se eliminaron herramientas. Las descripciones accesibles identifican reproducción, exportación, deshacer/rehacer, claves, curvas y cierre del panel.

## Verificación

- 14 capturas iniciales y 18 posteriores con las mismas dimensiones y configuraciones de texto. Se inspeccionaron Luces, sus claves y estilo, Recorte, Borde, Ajustes, Efectos, Texto y la barra principal.
- Tres pruebas de interfaz con Compose de producción: añadir un foco crea un objeto; elegir una luz existente no duplica; las pestañas muestran su sección y vuelven arriba; los valores pequeños se leen con precisión; los botones de claves caben y Exportar conserva una sola línea con texto ampliado.
- Un gesto vertical sobre el deslizador permite desplazar la página sin cambiar el valor. El gesto horizontal sí lo cambia; el botón explícito de restablecer funciona y los gestos se agrupan para deshacer.
- Tocar o arrastrar el asa amplía/reduce el panel sin modificar el proyecto.
- La copia de ajustes a la pista principal se etiqueta con ese alcance. Se ofrece en el ajuste local; una capa compartida utiliza su selector de destinos, evitando copiar además el mismo ajuste a cada clip.

El harness y sus dependencias de pruebas quedan fuera del proyecto; la configuración Gradle publicada no añade Robolectric ni un Activity de prueba. Las capturas usan un fotograma fijo en la vista de video. Algunas capturas offscreen de ese recurso mostraron una región sin pintar por caché del entorno de pruebas: no se atribuyó ese artefacto al motor de la app. Para la comparación se eligieron capturas completas. Falta la comprobación táctil y de rendimiento en hardware.
