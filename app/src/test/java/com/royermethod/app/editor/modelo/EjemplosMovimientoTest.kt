package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.PaqueteProyecto
import com.royermethod.app.editor.Persistencia
import com.royermethod.app.editor.modelo.TipoEfectoVideo.BARRIDO
import com.royermethod.app.editor.modelo.TipoEfectoVideo.BORDES_ESPEJO
import com.royermethod.app.editor.modelo.TipoEfectoVideo.CIRCULO
import com.royermethod.app.editor.modelo.TipoEfectoVideo.DESENFOQUE
import com.royermethod.app.editor.modelo.TipoEfectoVideo.DESENFOQUE_DIRECCIONAL
import com.royermethod.app.editor.modelo.TipoEfectoVideo.LATIGAZO
import com.royermethod.app.editor.modelo.TipoEfectoVideo.MITADES
import com.royermethod.app.editor.modelo.TipoEfectoVideo.SACUDIDA
import com.royermethod.app.editor.ui.EstadoEditor
import com.royermethod.app.ui.screens.cargarEjemplos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los ejemplos de movimiento: pocos efectos con nombre, independientes y editables despues. */
class EjemplosMovimientoTest {
    private val a = Origen("file:/ejemplos/ejemplo-a-1.png", "Ejemplo A.png", TipoOrigen.IMAGEN, 1080, 1350)
    private val b = Origen("file:/ejemplos/ejemplo-b-1.png", "Ejemplo B.png", TipoOrigen.IMAGEN, 1080, 1350)
    private val video = Origen("v", "v.mp4", TipoOrigen.VIDEO, 1920, 1080, 4_000_000, 30f, true)
    private val medidor = Medidor { c, _ -> c.origen!!.anchoVisible.toFloat() to c.origen!!.altoVisible.toFloat() }
    private val W = 576
    private val H = 1024

    private fun ejemplos(p: Proyecto = Proyecto(), conB: Boolean = true) = EjemplosMovimiento.agregar(p, a, if (conB) b else null)

    /** Lo del usuario: dos videos (el primero con transicion), una capa y un sonido. */
    private fun delUsuario(): Proyecto {
        var p = Proyecto(formato = Formato.CUADRADO, fondo = Fondo.NEGRO)
        p = Edicion.agregarPrincipal(p, listOf(video, video)).first
        p = p.copy(principal = p.principal.mapIndexed { i, c -> if (i == 0) c.copy(transicion = Transicion.FUNDIDO) else c })
        p = Edicion.agregarSuperpuesto(p, Origen("o", "o.png", TipoOrigen.IMAGEN, 500, 500), 7_000_000).first
        return Edicion.agregarAudio(p, Origen("s", "s.mp3", TipoOrigen.AUDIO, duracionUs = 9_000_000), 0).first
    }

    private fun bloque(r: EjemplosMovimiento.Resultado, i: Int): Clip = Edicion.clip(r.proyecto, r.ids[i])!!
    private fun capa(r: EjemplosMovimiento.Resultado): Clip = Edicion.clip(r.proyecto, r.capa!!)!!
    private fun inicio(r: EjemplosMovimiento.Resultado, i: Int): Long = Edicion.inicio(r.proyecto, r.ids[i])!!
    private fun efecto(c: Clip, grupo: String, tipo: TipoEfectoVideo) = c.efectos.single { it.grupo == grupo && it.tipo == tipo }

    /** Todo lo que dibuja un fotograma, con los numeros bit a bit: para comparar dos. */
    private fun firma(c: Cuadro): String {
        val sb = StringBuilder()
        fun b(f: Float) = Integer.toHexString(java.lang.Float.floatToRawIntBits(f))
        fun fs(a: FloatArray) = a.joinToString(",") { b(it) }
        fun paso(p: Paso) {
            when (p) {
                is PasoSolido -> sb.append("S ${p.argb} ${fs(p.opacidades)}\n")
                is PasoLinea -> sb.append("L ${fs(p.bordes)}\n")
                is PasoClip -> sb.append("C ${p.clip.id} ${p.esFondo} ${p.matrices.joinToString("|") { fs(it) }} ${fs(p.opacidades)} " +
                    "${fs(p.bordes)} ${p.mascara} ${b(p.desenfoque)} ${b(p.desenfoqueX)} ${b(p.desenfoqueY)} ${b(p.espejoBordes)} " +
                    "${b(p.luz)} ${b(p.rgb)} ${b(p.negativo)} ${b(p.anchoEnLienzo)} ${b(p.altoEnLienzo)} ${p.recorteFuente} ${p.mezcla}\n")
                is PasoPartes -> { sb.append("P ${p.mezcla}\n"); p.partes.forEach { paso(it) } }
                is PasoGrupo -> { sb.append("G ${p.matrices.joinToString("|") { fs(it) }} ${fs(p.bordes)} ${p.mascara}\n"); p.pasos.forEach { paso(it) } }
            }
        }
        c.pasos.forEach { paso(it) }
        return sb.toString()
    }

    /** El fotograma de salida que cae en [t] de la linea. */
    private fun cuadro(p: Proyecto, t: Long): Cuadro = Escena(p, W, H, medidor).cuadro(Linea.fotogramaEn(t))

    /** El mismo proyecto con un bloque (o capa) sin efectos ni claves. */
    private fun sinNada(p: Proyecto, id: Long) = Edicion.cambiar(p, id) { it.copy(efectos = emptyList(), claves = emptyList()) }

    // ---- Lo que se agrega ----

    @Test
    fun conUnProyectoVacioLosEjemplosSonElProyecto() {
        val vacio = Proyecto(formato = Formato.CUADRADO, fondo = Fondo.BLANCO, salida = AjustesSalida(ladoCorto = 720))
        val r = ejemplos(vacio)
        val p = r.proyecto
        assertEquals(0L, r.inicioUs)
        assertEquals(4, p.principal.size)
        assertEquals(r.ids, p.principal.map { it.id })
        // Los ajustes del proyecto se quedan como estaban
        assertEquals(vacio.formato, p.formato)
        assertEquals(vacio.fondo, p.fondo)
        assertEquals(vacio.salida, p.salida)
        assertEquals(EjemplosMovimiento.MITADES_US + EjemplosMovimiento.CIRCULO_US + EjemplosMovimiento.LATIGAZO_US +
            EjemplosMovimiento.BARRIDO_US, Linea.duracion(p))
        // El circulo y el anillo van en una capa encima del segundo bloque, que es la otra foto
        val capa = p.capas.single()
        assertEquals(r.capa, capa.id)
        assertEquals(inicio(r, 1), capa.inicioUs)
        assertEquals(bloque(r, 1).duracionUs, capa.clip.duracionUs)
        assertEquals(a, capa.clip.origen)
        assertEquals(b, bloque(r, 1).origen)
        assertTrue(bloque(r, 1).efectos.isEmpty())
        // Ids unicos, y el contador sigue despues del ultimo
        val ids = p.principal.map { it.id } + p.capas.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(p.siguienteId > ids.max())
    }

    @Test
    fun conAlgoYaHechoSeAgreganAlFinalSinTocarNada() {
        val antes = delUsuario()
        val r = ejemplos(antes)
        val p = r.proyecto
        // Lo de antes, intacto y en su sitio
        assertEquals(antes.principal, p.principal.take(antes.principal.size))
        assertEquals(antes.capas, p.capas.take(antes.capas.size))
        assertEquals(antes.audios, p.audios)
        assertEquals(listOf(antes.formato, antes.fondo, antes.salida), listOf(p.formato, p.fondo, p.salida))
        assertEquals(r.ids, p.principal.drop(antes.principal.size).map { it.id })
        // Empiezan donde acababa la linea principal (el fundido del primer video no les toca)
        assertEquals(Linea.duracionPrincipal(antes.principal), r.inicioUs)
        assertEquals(Linea.inicios(p.principal)[antes.principal.size], r.inicioUs)
        // Ningun id repetido con lo del usuario
        val viejos = antes.principal.map { it.id } + antes.capas.map { it.id } + antes.audios.map { it.id }
        assertTrue((r.ids + r.capa!!).none { it in viejos })
        assertTrue(r.ids.all { it >= antes.siguienteId })
        // La capa del usuario sigue ahi cuando empieza la del circulo: esta va en la fila de arriba
        val c = p.capas.last()
        assertEquals(r.capa, c.id)
        assertEquals(inicio(r, 1), c.inicioUs)
        assertTrue(antes.capas.single().finUs > c.inicioUs)
        assertEquals(1, c.fila)
        assertTrue(antes.capas.none { it.fila == c.fila && it.inicioUs < c.finUs && it.finUs > c.inicioUs })
    }

    @Test
    fun sinSegundaFotoTodoEstaEnLaLineaPrincipal() {
        val r = ejemplos(conB = false)
        assertNull(r.capa)
        assertTrue(r.proyecto.capas.isEmpty())
        assertTrue(r.proyecto.principal.all { it.origen == a })
        // El circulo y el anillo van entonces en el segundo bloque
        assertEquals(listOf(CIRCULO, CIRCULO, CIRCULO), bloque(r, 1).efectos.map { it.tipo })
    }

    @Test
    fun cadaEfectoTieneIdGrupoLegibleYCabeEnSuBloque() {
        val r = ejemplos()
        val clips = r.ids.indices.map { bloque(r, it) } + capa(r)
        for (c in clips) {
            assertEquals(c.efectos.size, c.efectos.map { it.id }.toSet().size)
            for (e in c.efectos) {
                assertTrue(e.id > 0)
                assertTrue("sin grupo: ${e.tipo}", e.grupo.isNotBlank())
                assertTrue(e.inicioUs >= 0 && e.finUs > e.inicioUs && e.finUs <= c.duracionUs)
                assertTrue(e.fundidoEntradaUs >= 0 && e.fundidoSalidaUs >= 0 && e.desfaseUs == 0L)
                assertTrue(e.activo && e.intensidad > 0f)
            }
            for (k in c.claves) assertTrue(k.tiempoUs in 0..c.duracionUs)
            assertTrue(c.piezas.isEmpty())
        }
        // Que hay en cada uno, por grupos: las combinaciones del catalogo y lo suelto
        fun grupos(c: Clip) = c.efectos.groupBy({ it.grupo }, { it.tipo })
        assertEquals(mapOf("Mitades que se juntan 1" to listOf(MITADES, DESENFOQUE_DIRECCIONAL),
            EjemplosMovimiento.GRUPO_SEPARAR to listOf(MITADES, DESENFOQUE_DIRECCIONAL)), grupos(bloque(r, 0)))
        assertEquals(mapOf("Circulo que se abre 1" to listOf(CIRCULO), "Anillo que se abre 1" to listOf(CIRCULO),
            EjemplosMovimiento.GRUPO_CERRAR to listOf(CIRCULO)), grupos(capa(r)))
        assertEquals(mapOf("Latigazo con desenfoque 1" to listOf(LATIGAZO, DESENFOQUE_DIRECCIONAL, BORDES_ESPEJO),
            "Sacudida con desenfoque 1" to listOf(SACUDIDA, DESENFOQUE)), grupos(bloque(r, 2)))
        assertEquals(mapOf(EjemplosMovimiento.GRUPO_BARRIDO to listOf(BARRIDO)), grupos(bloque(r, 3)))
        // Pocos ajustes: ningun bloque pasa de cinco efectos y solo uno lleva claves (tres)
        assertTrue(clips.all { it.efectos.size <= 5 })
        assertEquals(listOf(0, 0, 0, 3, 0), clips.map { it.claves.size })
    }

    @Test
    fun lasCombinacionesSonLasDelCatalogoConSusAjustes() {
        val r = ejemplos()
        val mitades = bloque(r, 0)
        val juntar = efecto(mitades, "Mitades que se juntan 1", MITADES)
        assertEquals(CatalogoEfectos.ajustesDe(MITADES), juntar.ajustes)
        assertEquals(0L, juntar.inicioUs)
        val separar = efecto(mitades, EjemplosMovimiento.GRUPO_SEPARAR, MITADES)
        assertTrue(separar.ajustes.vertical)
        assertEquals(0f, separar.ajustes.desde)
        assertEquals(0.5f, separar.ajustes.hasta)
        assertEquals(mitades.duracionUs, separar.finUs)
        val anillo = efecto(capa(r), "Anillo que se abre 1", CIRCULO)
        assertTrue(anillo.ajustes.invertir && anillo.ajustes.grosor > 0f)
        assertEquals(0.25f, anillo.ajustes.centroX)
        assertEquals(-0.2f, anillo.ajustes.centroY)
        val cerrar = efecto(capa(r), EjemplosMovimiento.GRUPO_CERRAR, CIRCULO)
        assertEquals(1f, cerrar.ajustes.desde)
        assertEquals(0f, cerrar.ajustes.hasta)
        assertEquals(capa(r).duracionUs, cerrar.finUs)
        val latigazo = efecto(bloque(r, 2), "Latigazo con desenfoque 1", LATIGAZO)
        assertEquals(CatalogoEfectos.ajustesDe(LATIGAZO), latigazo.ajustes)
        assertEquals(EjemplosMovimiento.LATIGAZO_DESDE_US, latigazo.inicioUs)
    }

    // ---- Independientes ----

    @Test
    fun laSacudidaEsLaDeSiempre() {
        val c = bloque(ejemplos(), 2)
        val s = efecto(c, "Sacudida con desenfoque 1", SACUDIDA)
        // El tipo de siempre, sin ajustes ni reloj tocados
        assertEquals(EfectoVideo(SACUDIDA, s.inicioUs, s.finUs, id = s.id, grupo = s.grupo), s)
        assertEquals(EjemplosMovimiento.SACUDIDA_DESDE_US, s.inicioUs)
        val escena = Escena(Proyecto(principal = listOf(c)), W, H, medidor)
        val sola = c.copy(efectos = listOf(s))
        val aMano = c.copy(efectos = listOf(EfectoVideo(SACUDIDA, s.inicioUs, s.finUs)))
        var t = s.inicioUs
        while (t < s.finUs) {
            val e = escena.efectosVideo(sola, t)
            assertEquals(escena.efectosVideo(aMano, t), e)
            // La cuenta de la sacudida, tal cual
            val seg = (t - s.inicioUs) / 1_000_000f
            assertEquals(0.03f * Escena.ruido(seg * 24f, 1.3f), e.dx, 0f)
            assertEquals(0.02f * Escena.ruido(seg * 27f, 5.9f), e.dy, 0f)
            assertEquals(3f * Escena.ruido(seg * 20f, 8.1f), e.rotacion, 0f)
            t += 7_919
        }
        // En el fotograma el grupo y el id no cuentan: igual que la sacudida puesta a mano
        val p = Proyecto(principal = listOf(sola))
        val q = Proyecto(principal = listOf(aMano))
        for (n in Linea.fotogramaDe(s.inicioUs) until Linea.fotogramaDe(s.finUs)) {
            assertEquals(firma(Escena(q, W, H, medidor).cuadro(n)), firma(Escena(p, W, H, medidor).cuadro(n)))
        }
    }

    @Test
    fun sacudidaYDesenfoqueSeQuitanYSeMuevenPorSeparado() {
        val c = bloque(ejemplos(), 2)
        val grupo = "Sacudida con desenfoque 1"
        val s = efecto(c, grupo, SACUDIDA)
        val d = efecto(c, grupo, DESENFOQUE)
        val escena = Escena(Proyecto(principal = listOf(c)), W, H, medidor)
        val enMedio = (s.inicioUs + s.finUs) / 2
        // Quitar el desenfoque deja la sacudida igual, y al reves
        val sinD = c.sinEfecto(d.id)
        assertEquals(s, sinD.efectos.single { it.id == s.id })
        assertEquals(c.efectos.size - 1, sinD.efectos.size)
        val conD = escena.efectosVideo(c, enMedio)
        val soloS = escena.efectosVideo(sinD, enMedio)
        assertEquals(conD.dx, soloS.dx, 0f)
        assertEquals(conD.dy, soloS.dy, 0f)
        assertEquals(conD.rotacion, soloS.rotacion, 0f)
        assertTrue(conD.desenfoque > 0f)
        assertEquals(0f, soloS.desenfoque, 0f)
        val sinS = c.sinEfecto(s.id)
        assertEquals(d, sinS.efectos.single { it.id == d.id })
        val soloD = escena.efectosVideo(sinS, enMedio)
        assertEquals(conD.desenfoque, soloD.desenfoque, 0f)
        assertEquals(0f, soloD.dx, 0f)
        // Mover uno no mueve el otro
        val movido = c.cambiarEfecto(s.id) { it.desplazado(200_000, c.duracionUs) }
        assertEquals(s.inicioUs + 200_000, movido.efectos.single { it.id == s.id }.inicioUs)
        assertEquals(d, movido.efectos.single { it.id == d.id })
        // Cambiar la intensidad de uno tampoco
        val suave = c.cambiarEfecto(d.id) { it.copy(intensidad = 0.2f) }
        assertEquals(s, suave.efectos.single { it.id == s.id })
        // moverGrupo mueve los dos lo mismo y nada mas
        val grupoMovido = c.moverGrupo(grupo, -300_000)
        assertEquals(s.inicioUs - 300_000, grupoMovido.efectos.single { it.id == s.id }.inicioUs)
        assertEquals(d.inicioUs - 300_000, grupoMovido.efectos.single { it.id == d.id }.inicioUs)
        assertEquals(c.efectos.filter { it.grupo != grupo }, grupoMovido.efectos.filter { it.grupo != grupo })
    }

    @Test
    fun latigazoYRastroDireccionalSonIndependientes() {
        val c = bloque(ejemplos(), 2)
        val grupo = "Latigazo con desenfoque 1"
        val l = efecto(c, grupo, LATIGAZO)
        val d = efecto(c, grupo, DESENFOQUE_DIRECCIONAL)
        val escena = Escena(Proyecto(principal = listOf(c)), W, H, medidor)
        // Un cuarto del tramo: va lanzado hacia la izquierda con su rastro horizontal
        val t = l.inicioUs + l.duracionUs / 4
        val todo = escena.efectosVideo(c, t)
        assertTrue(todo.dx < 0f)
        assertTrue(todo.desenfoqueX != 0f)
        assertEquals(0f, todo.desenfoqueY, 1e-4f)
        val sinRastro = escena.efectosVideo(c.sinEfecto(d.id), t)
        assertEquals(todo.dx, sinRastro.dx, 0f)
        assertEquals(0f, sinRastro.desenfoqueX, 0f)
        val sinLatigazo = escena.efectosVideo(c.sinEfecto(l.id), t)
        assertEquals(0f, sinLatigazo.dx, 0f)
        assertEquals(todo.desenfoqueX, sinLatigazo.desenfoqueX, 0f)
        // Girar el rastro no cambia el latigazo
        val girado = c.cambiarEfecto(d.id) { it.copy(ajustes = it.ajustes.copy(angulo = 90f)) }
        assertEquals(l, girado.efectos.single { it.id == l.id })
        val e = escena.efectosVideo(girado, t)
        assertEquals(todo.dx, e.dx, 0f)
        assertEquals(0f, e.desenfoqueX, 1e-4f)
        assertTrue(e.desenfoqueY != 0f)
    }

    // ---- Lo que se ve ----

    /** A mitad de cada efecto el fotograma cambia; quieto, sale igual que el bloque sin nada. */
    @Test
    fun aMitadDelEfectoSeVeDistintoYEnReposoIgualQueSinEfectos() {
        val r = ejemplos(delUsuario().copy(fondo = Fondo.DESENFOCADO))
        val p = r.proyecto
        // (bloque, id que se limpia, instantes con algo en marcha, instantes quietos)
        val casos = listOf(
            Triple(0, r.ids[0], listOf(250_000L, 1_800_000L) to listOf(1_000_000L)),
            Triple(1, r.capa!!, listOf(250_000L, 1_150_000L, 2_150_000L) to listOf(700_000L, 1_650_000L)),
            Triple(2, r.ids[2], listOf(450_000L, 1_350_000L) to listOf(800_000L, 1_850_000L)),
            Triple(3, r.ids[3], listOf(250_000L, 1_000_000L, 1_200_000L) to listOf(700_000L, 1_650_000L))
        )
        for ((i, id, tiempos) in casos) {
            val ini = inicio(r, i)
            val limpio = sinNada(p, id)
            for (local in tiempos.first) {
                val c = cuadro(p, ini + local)
                assertTrue(c.pasos.isNotEmpty() && c.clips.isNotEmpty())
                assertNotEquals("bloque $i en $local", firma(cuadro(limpio, ini + local)), firma(c))
            }
            for (local in tiempos.second) {
                val c = cuadro(p, ini + local)
                assertTrue(c.clips.isNotEmpty())
                assertEquals("bloque $i en $local", firma(cuadro(limpio, ini + local)), firma(c))
            }
        }
    }

    @Test
    fun lasMitadesSePartenYSeJuntan() {
        val r = ejemplos()
        val ini = inicio(r, 0)
        // Entrando: dos partes, arriba y abajo, cada una desde su lado
        val entrando = cuadro(r.proyecto, ini + 150_000).pasos.filterIsInstance<PasoPartes>().single()
        assertEquals(listOf(0f, 0f), entrando.partes.map { it.recorteFuente!!.izquierda })
        assertEquals(0.5f, entrando.partes[0].recorteFuente!!.abajo, 1e-6f)
        assertEquals(0.5f, entrando.partes[1].recorteFuente!!.arriba, 1e-6f)
        // La de arriba viene de arriba: su matriz la desplaza hacia abajo en la textura
        val arriba = entrando.partes[0].matrices.first()
        val abajo = entrando.partes[1].matrices.first()
        assertTrue(arriba[7] > abajo[7])
        // Juntas a mitad del bloque: un solo dibujo, la foto entera
        assertTrue(cuadro(r.proyecto, ini + 1_000_000).pasos.none { it is PasoPartes })
        // Saliendo: izquierda y derecha
        val saliendo = cuadro(r.proyecto, ini + 1_850_000).pasos.filterIsInstance<PasoPartes>().single()
        assertEquals(0.5f, saliendo.partes[0].recorteFuente!!.derecha, 1e-6f)
        assertEquals(0.5f, saliendo.partes[1].recorteFuente!!.izquierda, 1e-6f)
    }

    @Test
    fun elCirculoYElAnilloEnmascaranLaCapaConSuCentro() {
        val r = ejemplos()
        val ini = inicio(r, 1)
        fun capaEn(local: Long) = cuadro(r.proyecto, ini + local).clips.single { it.clip.id == r.capa }
        val abriendo = capaEn(250_000).mascara!!
        assertTrue(abriendo.radial && !abriendo.invertida)
        assertEquals(0f, abriendo.grosor, 0f)
        val anillo = capaEn(1_150_000).mascara!!
        assertTrue(anillo.radial && anillo.invertida && anillo.grosor > 0f)
        assertEquals(0.25f * W, anillo.centroX, 1e-3f)
        assertEquals(-0.2f * H, anillo.centroY, 1e-3f)
        assertNull(capaEn(1_650_000).mascara)
        assertNotNull(capaEn(2_300_000).mascara)
        // Debajo esta la otra foto
        assertTrue(cuadro(r.proyecto, ini + 250_000).clips.any { it.clip.id == r.ids[1] && it.clip.origen == b })
    }

    @Test
    fun elGolpeDeZoomLlevaClavesA5Milesimas() {
        val r = ejemplos()
        val c = bloque(r, 3)
        assertEquals(listOf(1_000_000L, 1_005_000L, 1_400_000L), c.claves.map { it.tiempoUs })
        // Con la tolerancia fina son dos claves distintas; con la de fotogramas, la misma
        assertEquals(1_005_000L, Edicion.claveCercana(c.claves, 1_005_000, Edicion.TOLERANCIA_FINA)!!.tiempoUs)
        assertEquals(1_000_000L, Edicion.claveCercana(c.claves, 1_000_000, Edicion.TOLERANCIA_FINA)!!.tiempoUs)
        // A medio camino entre las dos (en linea recta) va a medio golpe
        assertEquals(1.09f, c.encuadreEn(1_002_500).escala, 1e-5f)
        assertEquals(-2f, c.encuadreEn(1_002_500).rotacion, 1e-5f)
        // El fotograma del segundo 1 junta muestras de antes y despues del golpe
        val n = Linea.fotogramaEn(inicio(r, 3) + 1_000_000)
        val m = Escena(r.proyecto, W, H, medidor).cuadro(n).clips.single { it.clip.id == c.id && !it.esFondo }.matrices
        assertTrue(m.size > 1)
        assertFalse(m.all { it.contentEquals(m.first()) })
    }

    // ---- Guardar, reabrir y deshacer ----

    @Test
    fun guardarYVolverAAbrirDejaLosEjemplosIdenticos() {
        val p = ejemplos(delUsuario()).proyecto
        val texto = Persistencia.aTexto(p)
        val leido = Persistencia.deTexto(texto)
        assertEquals(p, leido)
        assertEquals(texto, Persistencia.aTexto(leido!!))
    }

    @Test
    fun elPaqueteRoyerLlevaLasDosFotosYSeAbreIgual() {
        val p = ejemplos(delUsuario()).proyecto
        val out = java.io.ByteArrayOutputStream()
        val leidas = ArrayList<String>()
        // Cada foto "contiene" su uri: asi se sabe cual es cual al abrir
        PaqueteProyecto.escribir(p, out) { o -> leidas += o.uri; java.io.ByteArrayInputStream(o.uri.toByteArray()) }
        assertTrue(a.uri in leidas && b.uri in leidas)
        val dir = java.nio.file.Files.createTempDirectory("royer-ejemplos-").toFile()
        try {
            val q = PaqueteProyecto.leer(java.io.ByteArrayInputStream(out.toByteArray()), dir)
            fun original(o: Origen?) = o?.let { it.copy(uri = java.io.File(java.net.URI(it.uri)).readText()) }
            val vuelta = q.copy(
                principal = q.principal.map { it.copy(origen = original(it.origen)) },
                capas = q.capas.map { c -> c.copy(clip = c.clip.copy(origen = original(c.clip.origen))) },
                audios = q.audios.map { it.copy(origen = original(it.origen)!!) }
            )
            assertEquals(p, vuelta)
            assertTrue(q.principal.mapNotNull { it.origen }.all { it.uri.endsWith(".png") || it.uri.endsWith(".mp4") })
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun cargarlosEsUnSoloPasoDeDeshacerYDejaElCabezalAlPrincipio() {
        val antes = delUsuario()
        val e = EstadoEditor(antes)
        var pausas = 0
        e.alPausar = { pausas++ }
        e.reproduciendo = true
        e.irA(1_234_567)
        cargarEjemplos(e, a, b)
        val esperado = ejemplos(antes)
        assertEquals(esperado.proyecto, e.proyecto)
        assertEquals(esperado.inicioUs, e.tiempo)
        assertFalse(e.reproduciendo)
        assertEquals(1, pausas)
        assertNull(e.seleccion)
        assertEquals("Ejemplos agregados. Deshacer para quitarlos", e.aviso)
        e.deshacer()
        assertEquals(antes, e.proyecto)
        assertFalse(e.puedeDeshacer)
        e.rehacer()
        assertEquals(esperado.proyecto, e.proyecto)
        // Con el proyecto vacio, al principio de todo
        val vacio = EstadoEditor(Proyecto())
        vacio.irA(5_000_000)
        cargarEjemplos(vacio, a, null)
        assertEquals(0L, vacio.tiempo)
        assertEquals(4, vacio.proyecto.principal.size)
    }
}
