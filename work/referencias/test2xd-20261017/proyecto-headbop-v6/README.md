# Head bop V6: movimiento natural sin reducir amplitud

Parte directamente del paquete V4 aprobado, no del V5. V4, su borrador y V5 se conservan intactos. En el JSON del proyecto sólo se añade naturalidad=0.8 a cabeza y ambos cabellos: tres campos en total. Cuerpo y manos conservan el campo ausente (naturalidad efectiva 0) y su movimiento V4. Todas las instancias mantienen giro=7 grados y rebote=0.012, además de sus intensidades, BPM, fase, cuello, pivotes, retardo, arrastre y tiempos originales.

Los nueve medios son idénticos byte por byte a V4; se conservan siete capas, 72 claves de intro/fundido y cero claves de baile. La intro, la música, el encuadre y la ausencia de textos/+ se mantienen sin regenerar geometría. El manifiesto recoge hashes y deltas exactos.

Requiere compilar e instalar el motor V6 que implementa naturalidad usando solamente rotación/traslación, sin aplanar ni estirar la cabeza y sin atenuar la amplitud. Abrir este archivo en el motor V5 no comprueba esa corrección.

Regenerar desde la carpeta raíz: `python crear_headbop-v6.py`. El script usa el ZIP V4 como fuente y rechaza un hash distinto. No vuelve a generar PNG, intro ni claves.

Validado: CRC, medios, claves y los tres únicos cambios del JSON. Pendiente: exportación desde la app con motor V6 y revisión visual del movimiento. No se afirma que ya exista un render ni una comprobación de fluidez.
