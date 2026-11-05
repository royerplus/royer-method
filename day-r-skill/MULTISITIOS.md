# Edificios con VARIOS sitios de luteo (registro, dado por el usuario con capturas)
Cada sitio es una parada distinta: al tocarlo el personaje se para en un punto (rombo verde distinto) y el nombre sale sobre el personaje con formato "Edificio - Sitio".
Se comprueba tocando cada parte del edificio y leyendo el nombre que aparece. Capturas en `evidencia_multisitios/` (1-8).

| Edificio | Sitios vistos (nombre exacto) | Captura | Notas |
|---|---|---|---|
| Colegio (bloque grande en forma de L, Zaraysk, parada 8 "Colegio") | Colegio - Biblioteca / Colegio - Aulas / Colegio - Cantina / Colegio - Gimnasio | 1,2,3,4 | 4 sitios. Cada uno en una parte distinta del edificio (biblioteca = ala de atrás, aulas = ala derecha, cantina = frente/esquina, gimnasio = ala izquierda). |
| Clínica (hospital rojo/blanco, parada 20 "Hospital") | Clínica - Edificio principal / Clínica - Salas | 5,6 | 2 sitios (el nombre "Hospital" de mi mapa es esta clínica). |
| Ruinas (bloque derrumbado junto al campamento del camión amarillo) | Ruinas - Entrada 1 / Ruinas - Entrada 2 | 7,8 | Son RUINAS: regla del usuario = ruinas nunca se lutean (no entrar a estos sitios). Aun asi tienen 2 entradas. |

## Reglas derivadas
- La ruta de cada ciudad debe tener UNA PARADA POR SITIO, no por edificio. En Zaraysk faltan: Colegio (x4), Clínica (x2). Los demás edificios grandes (bloques largos de varios pisos) hay que revisar tocando cada parte.
- Cómo detectarlo rápido: tocar una parte del edificio -> captura -> leer nombre; tocar otra parte lejana -> si el nombre cambia ("X - Otro sitio") hay más sitios. Anotar aquí.
- La Casa intacta, Casa maldita y Gasolinera tienen un solo sitio (el nombre no lleva " - ").
- Edificios de ladrillo/madera pequeños suelen ser 1 sitio. Los bloques largos y complejos (colegio, clínica, fábrica?) son multi-sitio.
- Pendiente: confirmar cuántos sitios tienen Fábrica, Mansión blanca, Bloque azul, Hospital (más allá de 2), edificios con contorno rojo.
