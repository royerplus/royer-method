package com.royermethod.app.editor.motor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.opengl.GLES20
import com.royermethod.app.editor.modelo.Cuadro
import com.royermethod.app.editor.modelo.BordesPieza
import com.royermethod.app.editor.modelo.Mascara
import com.royermethod.app.editor.modelo.Mezcla
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Paso
import com.royermethod.app.editor.modelo.PasoClip
import com.royermethod.app.editor.modelo.PasoGrupo
import com.royermethod.app.editor.modelo.PasoLinea
import com.royermethod.app.editor.modelo.PasoPartes
import com.royermethod.app.editor.modelo.PasoSolido
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.editor.modelo.cortesEn
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Dibuja un [Cuadro] en la superficie activa.
 *
 * Abre y cierra las fuentes (videos, imagenes, textos) segun se van
 * necesitando, y es el mismo para la vista previa y para exportar.
 *
 * @param ancho,alto pixeles del lienzo
 * @param fluidez el modelo de interpolacion, o null si no se usa (la vista
 *   previa no lo usa: no da tiempo a generar fotogramas en directo)
 */
class Compositor(
    private val context: Context,
    val ancho: Int,
    val alto: Int,
    private val fluidez: Fluidez? = null
) {
    private val reduccion = Reduccion()
    private val copiaPlana = Programa(Sombreadores.VERTICE, Sombreadores.COPIA_PLANA)
    private val capa = Programa(Sombreadores.VERTICE, Sombreadores.CAPA)
    private val solido = Programa(Sombreadores.VERTICE, Sombreadores.SOLIDO)
    private val linea = Programa(Sombreadores.VERTICE, Sombreadores.LINEA)
    private val copiaExacta = Programa(Sombreadores.VERTICE, Sombreadores.COPIA_EXACTA)

    private val videos = ArrayList<FuenteVideo>()
    /** Archivos que no se pudieron abrir, con el cuadro en que fallaron. */
    private val fallos = HashMap<String, Long>()
    private val imagenes = HashMap<String, FuenteImagen>()
    /**
     * Fotos ya decodificadas en otro hilo, esperando a subirse a la GPU. Leer
     * un JPEG grande cuesta decenas de milisegundos: hecho en el hilo de
     * dibujo, el fotograma en que entra la foto se retrasaba.
     */
    private val precargadas = ConcurrentHashMap<String, Bitmap>()
    /** Lo pedido al hilo de precarga y todavia no subido a la GPU. */
    private val pedidas = ConcurrentHashMap.newKeySet<String>()
    private var precargador: ExecutorService? = null
    /**
     * Videos que se estan cerrando: el decodificador se suelta en otro hilo
     * (parar un MediaCodec costaba 25-50 ms en el fotograma en que tocaba
     * cerrarlo) y lo de OpenGL despues, aqui, cuando ya termino.
     */
    private val cerrando = ArrayList<FuenteVideo>()
    private var cerrador: ExecutorService? = null
    @Volatile
    private var soltado = false
    /** Hasta 1.5 veces el lienzo: deja hacer zoom sin que se pixele. */
    private val ladoImagen = (maxOf(ancho, alto) * 1.5f).toInt()
    private val textos = HashMap<Triple<Texto, Int, Float>, FuenteImagen>()
    private val fondos = HashMap<Any, Pair<Long, List<Lamina>>>()
    /** Laminas del tamano del lienzo para montar escenas aparte. */
    private val grupos = ArrayList<Lamina>()
    /** Dos destinos reutilizados para cualquier numero de muestras o partes. */
    private var muestraPartes: Lamina? = null
    private var promedioPartes: Lamina? = null

    /** Lo que se mide para la escena: videos e imagenes por sus metadatos, textos maquetados. */
    val medidor = MaquetaTexto.medidor

    private var numero = 0L

    /**
     * El fotograma de la linea de tiempo que se esta dibujando (0..63), para
     * el grano. Sale de Cuadro.numero y no de [numero], que cuenta tambien
     * los redibujos en pausa: asi un fotograma da el mismo grano en la vista
     * previa y en el video.
     */
    private var semilla = 0f

    /** Algo fallo al leer una fuente en el ultimo cuadro (se dibuja lo que hay). */
    var avisos = 0
        private set

    /**
     * Dibuja [cuadro] en lo que este activo.
     *
     * @param destino la lamina donde dibujar, o null para la superficie
     *   (pantalla o codificador)
     */
    fun dibujar(cuadro: Cuadro, destino: Lamina? = null) {
        numero++
        semilla = Math.floorMod(cuadro.numero, 64L).toFloat()
        // 1. que cada fuente tenga su fotograma, antes de tocar el destino
        val texturas = IdentityHashMap<PasoClip, Textura>()
        val preparadas = HashMap<ClaveTextura, Textura?>()
        for (paso in cuadro.clips) {
            if (!paso.visible) continue
            val clave = ClaveTextura(paso.clip.id, paso.tiempoArchivoUs, paso.exposicionUs, paso.esFondo,
                paso.anchoEnLienzo, paso.altoEnLienzo, paso.escalaTexto)
            if (!preparadas.containsKey(clave)) preparadas[clave] = preparar(paso)
            preparadas[clave]?.let { texturas[paso] = it }
        }

        // 2. las escenas que van aparte, cada una en su lamina. Antes que el
        // destino: en los moviles cambiar de destino a medio dibujar obliga a
        // guardar y recargar lo que ya habia.
        GLES20.glEnable(GLES20.GL_BLEND)
        val montadas = IdentityHashMap<PasoGrupo, Lamina>()
        var g = 0
        for (paso in cuadro.pasos) {
            if (paso !is PasoGrupo || !paso.visible) continue
            val l = grupos.getOrNull(g) ?: Lamina(ancho, alto).also { grupos.add(it) }
            g++
            l.activar()
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            for (q in paso.pasos) dibujarPaso(q, texturas, voltear = true)
            montadas[paso] = l
        }

        // 3. dibujar de abajo arriba
        if (destino != null) destino.activar() else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, ancho, alto)
        }
        val voltear = destino != null
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        for (paso in cuadro.pasos) {
            if (paso is PasoGrupo) montadas[paso]?.let { dibujarGrupo(paso, it, voltear) }
            else dibujarPaso(paso, texturas, voltear)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        limpiar()
    }

    /**
     * Empieza a leer en segundo plano las fotos de [origenes] que aun no estan
     * en la GPU, para que el fotograma en que aparecen no espere al archivo.
     * Se llama desde el hilo de dibujo; lo que no llegue a tiempo se lee
     * igual que siempre al necesitarlo.
     */
    fun anticipar(origenes: Collection<Origen>) {
        if (soltado) return
        for (o in origenes) {
            if (!o.esImagen || o.uri in imagenes || !pedidas.add(o.uri)) continue
            val hilo = precargador ?: Executors.newSingleThreadExecutor { r ->
                Thread(r, "precarga").apply { priority = Thread.MIN_PRIORITY }
            }.also { precargador = it }
            hilo.execute {
                val bmp = if (soltado) null else FuenteImagen.decodificar(context, Uri.parse(o.uri), ladoImagen)
                // Si mientras tanto ya se leyo en el hilo de dibujo, sobra.
                if (bmp != null && (soltado || o.uri !in pedidas)) bmp.recycle()
                else if (bmp != null) precargadas[o.uri] = bmp
            }
        }
    }

    private fun dibujarPaso(paso: Paso, texturas: Map<PasoClip, Textura>, voltear: Boolean) {
        when (paso) {
            is PasoSolido -> dibujarSolido(paso, voltear)
            is PasoLinea -> dibujarLinea(paso, voltear)
            is PasoClip -> texturas[paso]?.let { dibujarClip(paso, it, voltear) }
            is PasoPartes -> dibujarPartes(paso, texturas, voltear)
            // No hay grupos dentro de grupos
            is PasoGrupo -> Unit
        }
    }

    private class Textura(val id: Int, val ancho: Int, val alto: Int)
    private data class ClaveTextura(val clip: Long, val tiempo: Long, val exposicion: Long,
        val fondo: Boolean, val ancho: Float, val alto: Float, val escalaTexto: Float)

    /** Compone por instante antes de promediar, para conservar alfa y oclusion. */
    private fun dibujarPartes(p: PasoPartes, texturas: Map<PasoClip, Textura>, voltear: Boolean) {
        if (!p.visible) return
        val anterior = IntArray(1)
        val vista = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, anterior, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vista, 0)
        val muestra = muestraPartes ?: Lamina(ancho, alto).also { muestraPartes = it }
        val promedio = promedioPartes ?: Lamina(ancho, alto).also { promedioPartes = it }
        promedio.activar()
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        for (i in 0 until p.muestras) {
            muestra.activar()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            for (corte in p.cortesEn(i)) {
                val parte = p.partes[corte.parte]
                val textura = texturas[parte] ?: continue
                dibujarClip(parte.muestra(i).copy(recorteFuente = corte.region), textura,
                    voltear = true, compartidos = corte.compartidos)
            }
            promedio.activar()
            // Media incremental equivalente a sumar cada muestra con peso1/N.
            // El alfa constante1 queda exactamente1 tambien en destinos RGBA8.
            val peso = 1f / (i + 1)
            GLES20.glBlendColor(0f, 0f, 0f, peso)
            GLES20.glBlendFunc(GLES20.GL_CONSTANT_ALPHA, GLES20.GL_ONE_MINUS_CONSTANT_ALPHA)
            copiarExacto(muestra.textura, voltear = true)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, anterior[0])
        GLES20.glViewport(vista[0], vista[1], vista[2], vista[3])
        aplicarMezcla(p.mezcla)
        copiarExacto(promedio.textura, voltear)
    }

    private fun copiarExacto(textura: Int, voltear: Boolean) {
        copiaExacta.usar()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
        GLES20.glUniform1i(copiaExacta.u("sTex"), 0)
        copiaExacta.dibujar(voltear)
    }

    // ------------------------------------------------------------------
    // Fuentes
    // ------------------------------------------------------------------

    private fun preparar(paso: PasoClip): Textura? {
        val clip = paso.clip
        val texto = clip.texto
        if (texto != null) return textoDe(texto, paso.escalaTexto)
        val o = clip.origen ?: return null

        if (o.esImagen) {
            val img = imagenes.getOrPut(o.uri) {
                pedidas.remove(o.uri)
                precargadas.remove(o.uri)?.let { bmp -> FuenteImagen.subir(bmp).also { bmp.recycle() } }
                    ?: FuenteImagen.abrir(context, Uri.parse(o.uri), ladoImagen)
                    ?: return null
            }
            img.usadaEn = numero
            return if (paso.esFondo) fondo(img, img.textura, img.ancho, img.alto, 0L)
            else Textura(img.textura, img.ancho, img.alto)
        }

        val fuente = video(o.uri, o.giro, paso.tiempoArchivoUs) ?: return null
        fuente.usadaEn = numero
        val (w, h) = tamanoCopia(fuente, paso)

        // Modo fluido: los fotogramas que faltan los pone el modelo. Si no esta
        // disponible, sigue por el camino normal en vez de fallar.
        if (clip.fluido && fluidez != null) {
            val l = fluidez.fotograma(fuente, paso, w, h, reduccion)
            if (l != null) {
                return if (paso.esFondo) fondo(fuente, l.textura, l.ancho, l.alto, paso.tiempoArchivoUs)
                else Textura(l.textura, l.ancho, l.alto)
            }
        }

        if (!fuente.irA(paso.tiempoArchivoUs)) avisos++
        if (fuente.ptsActual < 0) return null
        val copia = fuente.copiar(w, h, reduccion)
        return if (paso.esFondo) fondo(fuente, copia.textura, copia.ancho, copia.alto, fuente.ptsActual)
        else Textura(copia.textura, copia.ancho, copia.alto)
    }

    /**
     * El decodificador que mejor sirve para [uri] en [tiempo].
     *
     * Dos bloques del mismo archivo (lo normal despues de dividir) pueden
     * compartir decodificador si el segundo empieza donde acaba el primero:
     * sigue decodificando sin saltar. Pero si los dos se ven a la vez, como
     * en una transicion entre dos trozos del mismo clip, cada uno necesita el
     * suyo.
     */
    private fun video(uri: String, giro: Int, tiempo: Long): FuenteVideo? {
        // Ya pedida en este mismo cuadro para el mismo instante (el fondo y el
        // clip de delante, por ejemplo): la misma.
        videos.firstOrNull { it.usadaEn == numero && it.uri.toString() == uri && it.ultimaPeticion == tiempo }
            ?.let { return it }

        val libres = videos.filter { it.uri.toString() == uri && it.usadaEn != numero }
        val cerca = libres
            .filter { it.ultimaPeticion in 0..tiempo && tiempo - it.ultimaPeticion < 1_500_000 }
            .minByOrNull { tiempo - it.ultimaPeticion }
        if (cerca != null) return cerca
        libres.firstOrNull()?.let { return it }

        // Los moviles tienen pocos decodificadores por hardware. Si ya hay
        // muchos abiertos, se cierra el que lleve mas tiempo sin usarse.
        if (videos.size >= MAX_VIDEOS) {
            videos.filter { it.usadaEn != numero }.minByOrNull { it.usadaEn }?.let {
                cerrar(it)
            }
        }
        // Un archivo que acaba de fallar no se reintenta en cada fotograma: abrir
        // un decodificador cuesta, y si falta el archivo fallara igual.
        fallos[uri]?.let { if (numero - it < REINTENTO_FOTOGRAMAS) { avisos++; return null } }
        return runCatching { FuenteVideo(context, Uri.parse(uri), giro) }
            .onFailure { avisos++; fallos[uri] = numero }
            .onSuccess { fallos.remove(uri) }
            .getOrNull()
            ?.also { videos.add(it) }
    }

    private fun tamanoCopia(f: FuenteVideo, paso: PasoClip): Pair<Int, Int> {
        if (paso.esFondo) {
            // El fondo se va a desenfocar: vale la copia que ya haya (la del
            // clip de delante, si es el mismo) y si no, medio lienzo. Pedir
            // un tamano propio obligaria a rehacer la copia dos veces por
            // fotograma.
            f.copia?.let { return it.ancho to it.alto }
            return minOf(f.anchoVisible, ancho / 2).coerceAtLeast(16) to
                minOf(f.altoVisible, alto / 2).coerceAtLeast(16)
        }
        fun redondear(v: Float, maximo: Int) = (ceil(v / 32f).toInt() * 32).coerceIn(16, maximo.coerceAtMost(2048))
        var w = redondear(paso.anchoEnLienzo, f.anchoVisible)
        var h = redondear(paso.altoEnLienzo, f.altoVisible)
        // No rehacer la copia por pequenos cambios de tamano (una animacion de
        // zoom pediria uno distinto en cada fotograma).
        f.copia?.let { c ->
            if (w <= c.ancho && h <= c.alto && w > c.ancho * 0.7f && h > c.alto * 0.7f) {
                w = c.ancho; h = c.alto
            }
        }
        return w to h
    }

    private fun textoDe(texto: Texto, escala: Float): Textura? {
        // Escalones de un cuarto: un zoom animado no repinta el texto en
        // cada fotograma, solo al cruzar cada escalon.
        val e = (ceil(escala.coerceIn(0.25f, 4f) * 4f) / 4f)
        val clave = Triple(texto, alto, e)
        val f = textos.getOrPut(clave) {
            val bmp = MaquetaTexto.pintar(texto, alto, e)
            FuenteImagen.subir(bmp).also { bmp.recycle() }
        }
        f.usadaEn = numero
        return Textura(f.textura, f.ancho, f.alto)
    }

    /**
     * El relleno desenfocado: la imagen reducida a la mitad varias veces
     * hasta quedarse en unos pocos pixeles. Reducir por mitades promedia todo;
     * ampliar luego esa miniatura con filtro ya da el desenfoque, y el
     * sombreador solo tiene que suavizar los escalones.
     */
    private fun fondo(clave: Any, textura: Int, w: Int, h: Int, pts: Long): Textura {
        val previo = fondos[clave]
        if (previo != null && previo.first == pts && pts >= 0) {
            val l = previo.second.last()
            return Textura(l.textura, l.ancho, l.alto)
        }
        val cadena = ArrayList<Lamina>()
        var sw = w; var sh = h
        val reutilizar = previo?.second
        while (minOf(sw, sh) > 48) {
            sw = (sw + 1) / 2; sh = (sh + 1) / 2
            val i = cadena.size
            val l = reutilizar?.getOrNull(i)?.takeIf { it.ancho == sw && it.alto == sh } ?: Lamina(sw, sh)
            cadena.add(l)
        }
        reutilizar?.filter { it !in cadena }?.forEach { it.soltar() }
        if (cadena.isEmpty()) cadena.add(Lamina(w, h))

        var fuente = textura
        GLES20.glDisable(GLES20.GL_BLEND)
        copiaPlana.usar()
        for (l in cadena) {
            l.activar()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fuente)
            GLES20.glUniform1i(copiaPlana.u("sTex"), 0)
            GLES20.glUniformMatrix3fv(copiaPlana.u("uMapa"), 1, false, IDENTIDAD, 0)
            GLES20.glUniform2f(copiaPlana.u("uPaso"), 0.25f / l.ancho, 0.25f / l.alto)
            copiaPlana.dibujar(voltear = true)
            fuente = l.textura
        }
        fondos[clave] = pts to cadena
        val l = cadena.last()
        return Textura(l.textura, l.ancho, l.alto)
    }

    private fun cerrar(v: FuenteVideo) {
        fluidez?.olvidar(v)
        fondos.remove(v)?.second?.forEach { it.soltar() }
        videos.remove(v)
        val hilo = cerrador ?: Executors.newSingleThreadExecutor { r -> Thread(r, "cerrar-video") }
            .also { cerrador = it }
        cerrando.add(v)
        hilo.execute { v.soltarDecodificador() }
    }

    /** Termina en este hilo los cierres cuyo decodificador ya se solto. */
    private fun terminarCierres() {
        val listos = cerrando.filter { it.decodificadorSuelto }
        for (v in listos) { v.soltarGl(); cerrando.remove(v) }
    }

    /** Cierra lo que ya no se usa, para no acumular decodificadores abiertos. */
    private fun limpiar() {
        terminarCierres()
        val viejos = videos.filter { numero - it.usadaEn > RETENER }
        for (v in viejos) cerrar(v)
        val imgViejas = imagenes.filterValues { numero - it.usadaEn > RETENER }
        for ((k, v) in imgViejas) {
            v.soltar(); imagenes.remove(k)
            fondos.remove(v)?.second?.forEach { it.soltar() }
        }
        val txtViejos = textos.filterValues { numero - it.usadaEn > RETENER }
        for ((k, v) in txtViejos) { v.soltar(); textos.remove(k) }
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    private fun dibujarSolido(p: PasoSolido, voltear: Boolean) {
        val a = p.opacidadMedia
        if (a <= 0.002f) return
        solido.usar()
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform4f(
            solido.u("uColor"),
            Color.red(p.argb) / 255f, Color.green(p.argb) / 255f, Color.blue(p.argb) / 255f, a
        )
        solido.dibujar(voltear)
    }

    private fun dibujarLinea(p: PasoLinea, voltear: Boolean) {
        linea.usar()
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform2f(linea.u("uLienzo"), ancho.toFloat(), alto.toFloat())
        GLES20.glUniform2f(linea.u("uDir"), p.dirX, p.dirY)
        GLES20.glUniform1fv(linea.u("uBorde"), p.bordes.size, p.bordes, 0)
        GLES20.glUniform1f(linea.u("uN"), p.bordes.size.toFloat())
        GLES20.glUniform1f(linea.u("uGrosor"), p.grosor)
        GLES20.glUniform3f(
            linea.u("uColor"),
            Color.red(p.color) / 255f, Color.green(p.color) / 255f, Color.blue(p.color) / 255f
        )
        linea.dibujar(voltear)
    }

    private fun dibujarClip(p: PasoClip, t: Textura, voltear: Boolean, compartidos: BordesPieza = BordesPieza.NINGUNO) {
        capa.usar()
        aplicarMezcla(p.mezcla)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.id)
        GLES20.glUniform1i(capa.u("sTex"), 0)
        GLES20.glUniform2f(capa.u("uLienzo"), ancho.toFloat(), alto.toFloat())
        GLES20.glUniform2f(capa.u("uTexel"), 1f / t.ancho, 1f / t.alto)

        muestras(p.matrices, p.opacidades, p.bordes)

        val r = if (p.esFondo) null else p.clip.encuadre.recorte
        if (r == null) GLES20.glUniform4f(capa.u("uRecorte"), 0f, 0f, 1f, 1f)
        else GLES20.glUniform4f(capa.u("uRecorte"), r.izquierda, r.arriba, 1f - r.derecha, 1f - r.abajo)

        mascara(p.mascara)
        // El fondo: la miniatura se suaviza con un radio de un par de sus pixeles
        val radio = if (p.esFondo) alto * 0.035f else p.desenfoque
        GLES20.glUniform1f(capa.u("uRadio"), radio)
        GLES20.glUniform2f(capa.u("uRadioDir"), if (p.esFondo) 0f else p.desenfoqueX, if (p.esFondo) 0f else p.desenfoqueY)
        GLES20.glUniform1f(capa.u("uEspejoBordes"), if (p.esFondo) 0f else p.espejoBordes)
        val region = if (p.esFondo) null else p.recorteFuente
        GLES20.glUniform1f(capa.u("uConRegionFuente"), if (region != null) 1f else 0f)
        if (region != null) GLES20.glUniform4f(capa.u("uRegionFuente"), region.izquierda, region.arriba, region.derecha, region.abajo)
        GLES20.glUniform4f(capa.u("uBordesCompartidos"),
            if (compartidos.izquierda) 1f else 0f, if (compartidos.arriba) 1f else 0f,
            if (compartidos.derecha) 1f else 0f, if (compartidos.abajo) 1f else 0f)

        val c = p.color
        GLES20.glUniform1f(capa.u("uNitidez"), if (p.esFondo) 0f else c.nitidez)
        GLES20.glUniform1f(capa.u("uBrillo"), c.brillo)
        GLES20.glUniform1f(capa.u("uContraste"), c.contraste)
        GLES20.glUniform1f(capa.u("uSaturacion"), c.saturacion)
        GLES20.glUniform1f(capa.u("uExposicion"), c.exposicion)
        GLES20.glUniform1f(capa.u("uCalidez"), c.calidez)
        GLES20.glUniform1f(capa.u("uTinte"), c.tinte)
        GLES20.glUniform1f(capa.u("uSombras"), c.sombras)
        GLES20.glUniform1f(capa.u("uLuces"), c.luces)
        GLES20.glUniform1f(capa.u("uVineta"), c.vineta)
        // El relleno es una miniatura de pocos pixeles: claridad y grano ahi
        // serian manchas, como la nitidez
        GLES20.glUniform1f(capa.u("uClaridad"), if (p.esFondo) 0f else c.claridad)
        GLES20.glUniform1f(capa.u("uVivacidad"), c.vivacidad)
        GLES20.glUniform1f(capa.u("uDesvanecer"), c.desvanecer)
        GLES20.glUniform1f(capa.u("uGrano"), if (p.esFondo) 0f else c.grano)
        GLES20.glUniform1f(capa.u("uSemilla"), semilla)
        GLES20.glUniform1f(capa.u("uNeutro"), if (c.copy(nitidez = 0f).neutro) 1f else 0f)
        GLES20.glUniform1f(capa.u("uLuz"), p.luz)
        GLES20.glUniform1f(capa.u("uRgb"), if (p.esFondo) 0f else p.rgb)
        GLES20.glUniform1f(capa.u("uNegativo"), p.negativo)
        capa.dibujar(voltear)
    }

    private fun aplicarMezcla(mezcla: Mezcla) {
        when (mezcla) {
            Mezcla.NORMAL -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            Mezcla.PANTALLA -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR)
            Mezcla.SUMAR -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
            Mezcla.MULTIPLICAR -> GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    /** Una escena montada aparte, puesta encima con su movimiento y su mascara. */
    private fun dibujarGrupo(p: PasoGrupo, l: Lamina, voltear: Boolean) {
        capa.usar()
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, l.textura)
        GLES20.glUniform1i(capa.u("sTex"), 0)
        GLES20.glUniform2f(capa.u("uLienzo"), ancho.toFloat(), alto.toFloat())
        GLES20.glUniform2f(capa.u("uTexel"), 1f / l.ancho, 1f / l.alto)
        muestras(p.matrices, p.opacidades, p.bordes)
        // Extender: el recorte se sale de la lamina y lo de fuera repite el borde
        if (p.extender) GLES20.glUniform4f(capa.u("uRecorte"), -1f, -1f, 2f, 2f)
        else GLES20.glUniform4f(capa.u("uRecorte"), 0f, 0f, 1f, 1f)
        mascara(p.mascara)
        GLES20.glUniform1f(capa.u("uRadio"), 0f)
        GLES20.glUniform2f(capa.u("uRadioDir"), 0f, 0f)
        GLES20.glUniform1f(capa.u("uEspejoBordes"), 0f)
        GLES20.glUniform1f(capa.u("uConRegionFuente"), 0f)
        GLES20.glUniform1f(capa.u("uNitidez"), 0f)
        GLES20.glUniform1f(capa.u("uNeutro"), 1f)
        GLES20.glUniform1f(capa.u("uLuz"), 0f)
        GLES20.glUniform1f(capa.u("uRgb"), 0f)
        GLES20.glUniform1f(capa.u("uNegativo"), 0f)
        capa.dibujar(voltear)
    }

    private fun muestras(matrices: List<FloatArray>, opacidades: FloatArray, bordes: FloatArray) {
        val n = matrices.size
        val todas = FloatArray(n * 9)
        for (i in 0 until n) System.arraycopy(matrices[i], 0, todas, i * 9, 9)
        GLES20.glUniformMatrix3fv(capa.u("uInv"), n, false, todas, 0)
        GLES20.glUniform1fv(capa.u("uOpac"), n, opacidades, 0)
        GLES20.glUniform1fv(capa.u("uBorde"), bordes.size.coerceAtMost(n), bordes, 0)
        GLES20.glUniform1f(capa.u("uN"), n.toFloat())
    }

    private fun mascara(m: Mascara?) {
        GLES20.glUniform1f(capa.u("uConMascara"), if (m != null) 1f else 0f)
        GLES20.glUniform1f(capa.u("uMascInvertida"), if (m?.invertida == true) 1f else 0f)
        if (m != null) {
            GLES20.glUniform2f(capa.u("uMascDir"), m.dirX, m.dirY)
            GLES20.glUniform1f(capa.u("uMascSuave"), m.suave.coerceAtLeast(0.5f))
            GLES20.glUniform1f(capa.u("uMascRadial"), if (m.radial) 1f else 0f)
            GLES20.glUniform2f(capa.u("uMascCentro"), m.centroX, m.centroY)
            GLES20.glUniform1f(capa.u("uMascGrosor"), m.grosor.coerceAtLeast(0f))
        }
    }

    fun soltar() {
        soltado = true
        precargador?.shutdownNow()
        precargador = null
        pedidas.clear()
        precargadas.values.forEach { it.recycle() }
        precargadas.clear()
        grupos.forEach { it.soltar() }
        grupos.clear()
        muestraPartes?.soltar(); muestraPartes = null
        promedioPartes?.soltar(); promedioPartes = null
        videos.forEach { it.soltar() }
        videos.clear()
        // Los que se estaban cerrando: esperar al decodificador antes de
        // borrar su textura, que todavia podria estar escribiendo en ella.
        cerrador?.let { it.shutdown(); runCatching { it.awaitTermination(2, TimeUnit.SECONDS) } }
        cerrador = null
        cerrando.forEach { if (!it.decodificadorSuelto) it.soltarDecodificador(); it.soltarGl() }
        cerrando.clear()
        imagenes.values.forEach { it.soltar() }
        imagenes.clear()
        textos.values.forEach { it.soltar() }
        textos.clear()
        fondos.values.forEach { par -> par.second.forEach { it.soltar() } }
        fondos.clear()
        fluidez?.soltar()
        reduccion.soltar(); copiaPlana.soltar(); copiaExacta.soltar(); capa.soltar(); solido.soltar(); linea.soltar()
    }

    private companion object {
        const val MAX_VIDEOS = 6

        /** Cuadros que se espera antes de volver a intentar abrir un archivo que fallo. */
        private const val REINTENTO_FOTOGRAMAS = 30L

        /** Cuadros que se mantiene abierta una fuente sin usarse. */
        const val RETENER = 45L

        val IDENTIDAD = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
