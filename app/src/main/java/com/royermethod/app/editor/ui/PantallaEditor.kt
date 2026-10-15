package com.royermethod.app.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * El editor.
 *
 * Arriba la imagen, en medio la linea de tiempo con el cabezal fijo y abajo
 * las herramientas, como en CapCut: es la colocacion que todo el mundo ya
 * tiene aprendida. Lo propio de esta app es que hay muy poco a la vez:
 *
 *  - Sin nada elegido, abajo solo salen las cosas del proyecto (agregar,
 *    texto, sonido, formato).
 *  - Con un bloque elegido salen solo las suyas.
 *  - Cada herramienta abre un panel que ocupa el sitio de la linea de
 *    tiempo, nunca el de la imagen, para ver el cambio mientras se hace.
 *
 * Esto no sabe nada de Android: lo que dibuja la imagen, las miniaturas y
 * el elegir archivos llegan de fuera. Asi se puede dibujar y probar tambien
 * fuera del telefono.
 */
@Composable
fun PantallaEditor(
    estado: EstadoEditor,
    recursos: Recursos,
    medidor: Medidor,
    exportar: EstadoExportar,
    onVolver: () -> Unit,
    onAgregarPrincipal: () -> Unit,
    onAgregarCapa: () -> Unit,
    onAgregarSonido: () -> Unit,
    onExportar: () -> Unit,
    onCancelarExportar: () -> Unit,
    onCompartir: () -> Unit,
    onCerrarExportar: () -> Unit,
    modifier: Modifier = Modifier,
    vista: @Composable (Modifier) -> Unit
) {
    var exportando by remember { mutableStateOf(false) }
    // Si hay una exportacion en marcha o recien hecha, se ensena
    LaunchedEffect(exportar.corriendo, exportar.resultado, exportar.fallo) {
        if (exportar.corriendo || exportar.resultado != null || exportar.fallo != null) exportando = true
    }

    CompositionLocalProvider(LocalGestos provides estado) {
        BoxWithConstraints(modifier.fillMaxSize().background(Ink.Black)) {
            val abajo = min(maxHeight * 0.42f, 330.dp)
            Column(Modifier.fillMaxSize()) {
                BarraArriba(estado, onVolver, onExportar = { exportando = true })
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Lienzo(estado, medidor, Modifier.fillMaxSize(), vista)
                    Aviso(estado, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                }
                Transporte(estado)
                Box(Modifier.fillMaxWidth().height(abajo)) {
                    val panel = estado.panel
                    AnimatedContent(
                        targetState = when {
                            exportando -> "exportar"
                            panel != null -> panel.name
                            else -> ""
                        },
                        transitionSpec = {
                            (slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(200)))
                                .togetherWith(fadeOut(tween(140)))
                        },
                        label = "abajo"
                    ) { que ->
                        when {
                            que == "exportar" -> PanelExportar(
                                estado, exportar, onExportar, onCancelarExportar, onCompartir,
                                onCerrar = {
                                    exportando = false
                                    if (!exportar.corriendo) onCerrarExportar()
                                }
                            )
                            que.isNotEmpty() -> {
                                val p = Panel.valueOf(que)
                                HojaPanel(p.titulo, onCerrar = {
                                    estado.panel = null
                                    if (estado.seleccion is Seleccion.Paso) estado.elegir(null)
                                }) { ContenidoPanel(estado, p) }
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                LineaTiempo(estado, recursos, onAgregarPrincipal, onAgregarSonido, Modifier.fillMaxWidth().weight(1f))
                                Herramientas(estado, onAgregarPrincipal, onAgregarCapa, onAgregarSonido)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContenidoPanel(estado: EstadoEditor, p: Panel) {
    when (p) {
        Panel.VELOCIDAD -> PanelVelocidad(estado)
        Panel.VOLUMEN -> PanelVolumen(estado)
        Panel.ANIMACION -> PanelAnimacion(estado)
        Panel.TRANSICION -> PanelTransicion(estado)
        Panel.COLOR -> PanelColor(estado)
        Panel.ENCUADRE -> PanelEncuadre(estado)
        Panel.TEXTO -> PanelTexto(estado)
        Panel.FORMATO -> PanelFormato(estado)
        Panel.CURVA -> PanelCurva(estado)
    }
}

@Composable
private fun BarraArriba(estado: EstadoEditor, onVolver: () -> Unit, onExportar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotonIcono(Glifos.atras, onVolver)
        Spacer(Modifier.width(4.dp))
        // La proporcion a la vista, y tocandola se cambia
        Text(
            estado.proyecto.formato.nombre,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
            color = Ink.White70,
            modifier = Modifier
                .clip(esquinas(8.dp))
                .border(1.dp, Ink.White15, esquinas(8.dp))
                .clickable { estado.elegir(null); estado.panel = Panel.FORMATO }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(esquinas(20.dp))
                .background(Ink.White)
                .clickable(onClick = onExportar)
                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icono(Glifos.exportar, color = Ink.Black, tam = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Exportar", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Medium), color = Ink.Black)
        }
        Spacer(Modifier.width(6.dp))
    }
}

/** Reloj, play, fotograma clave, deshacer y rehacer. */
@Composable
private fun Transporte(estado: EstadoEditor) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(reloj(estado.tiempo), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp), color = Ink.White)
            Text(" / " + reloj(estado.duracion), style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.5.sp), color = Ink.White50)
        }
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Ink.White08)
                .clickable(enabled = estado.duracion > 0) { estado.reproduciendo = !estado.reproduciendo },
            contentAlignment = Alignment.Center
        ) {
            Icono(if (estado.reproduciendo) Glifos.pausa else Glifos.play, tam = 20.dp)
        }
        Row(Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            BotonClave(estado)
            BotonIcono(Glifos.deshacer, { estado.deshacer() }, activo = estado.puedeDeshacer)
            BotonIcono(Glifos.rehacer, { estado.rehacer() }, activo = estado.puedeRehacer)
        }
    }
}

/**
 * El rombo de fotograma clave. Con un bloque elegido y el cabezal dentro:
 * si ya hay uno ahi lo quita, si no lo pone con el encuadre que se ve.
 */
@Composable
private fun BotonClave(estado: EstadoEditor) {
    val c = (estado.seleccion as? Seleccion.Bloque)?.let { estado.clipElegido }
    val local = estado.localElegido
    val activo = c != null && local != null
    val hay = activo && c!!.claves.any { kotlin.math.abs(it.tiempoUs - local!!) <= Edicion.TOLERANCIA_CLAVE }
    BotonIcono(
        if (hay) Glifos.romboLleno else Glifos.rombo,
        {
            val clip = c ?: return@BotonIcono
            val l = local ?: return@BotonIcono
            if (hay) {
                estado.hacer { Edicion.quitarClave(it, clip.id, estado.tiempo) }
            } else {
                estado.hacer { Edicion.ponerClave(it, clip.id, estado.tiempo, clip.encuadreEn(l)) }
                if (clip.claves.isEmpty()) estado.avisar("Mueve el cabezal y cambia el bloque: se crea otro fotograma clave")
            }
        },
        activo = activo
    )
}

/** La fila de herramientas: cambia segun lo elegido. */
@Composable
private fun Herramientas(estado: EstadoEditor, onAgregarPrincipal: () -> Unit, onAgregarCapa: () -> Unit, onAgregarSonido: () -> Unit) {
    val s = estado.seleccion
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s != null) {
            Herramienta(Glifos.atras, "Volver", { estado.elegir(null) })
            Box(Modifier.width(1.dp).height(36.dp).background(Ink.White15))
        }
        when (s) {
            null -> {
                Herramienta(Glifos.editar, "Editar", {
                    // Elige lo que hay en la pista principal bajo el cabezal
                    val p = estado.proyecto
                    val en = com.royermethod.app.editor.modelo.Linea.principalEn(p.principal, com.royermethod.app.editor.modelo.Linea.inicios(p.principal), estado.tiempo)
                    val c = en?.segundo ?: en?.primero
                    if (c != null) estado.elegir(Seleccion.Bloque(c.id)) else estado.avisar("Agrega un video o una foto primero")
                })
                Herramienta(Glifos.mas, "Agregar", onAgregarPrincipal)
                Herramienta(Glifos.superponer, "Superponer", onAgregarCapa)
                Herramienta(Glifos.texto, "Texto", {
                    var nuevo = 0L
                    estado.hacer { p -> Edicion.agregarTexto(p, "Escribe aqui", estado.tiempo).also { nuevo = it.second }.first }
                    estado.elegir(Seleccion.Bloque(nuevo))
                    estado.panel = Panel.TEXTO
                })
                Herramienta(Glifos.audio, "Sonido", onAgregarSonido)
                Herramienta(Glifos.formato, "Formato", { estado.panel = Panel.FORMATO })
            }
            is Seleccion.Sonido -> {
                val id = s.id
                Herramienta(Glifos.dividir, "Dividir", {
                    val r = Edicion.dividirAudio(estado.proyecto, id, estado.tiempo)
                    if (r == null) estado.avisar("Pon el cabezal dentro del sonido para dividirlo") else estado.hacer { r.first }
                })
                Herramienta(Glifos.volumen, "Volumen", { estado.panel = Panel.VOLUMEN })
                Herramienta(Glifos.duplicar, "Duplicar", { Edicion.duplicar(estado.proyecto, id)?.let { r -> estado.hacer { r.first } } })
                Herramienta(Glifos.papelera, "Eliminar", { estado.hacer { Edicion.eliminar(it, id) }; estado.elegir(null) })
            }
            else -> {
                val c = estado.clipElegido ?: return@Row
                val id = c.id
                val principal = estado.proyecto.principal
                val i = principal.indexOfFirst { it.id == id }
                Herramienta(Glifos.dividir, "Dividir", {
                    val r = Edicion.dividir(estado.proyecto, id, estado.tiempo)
                    if (r == null) estado.avisar("Pon el cabezal dentro del bloque, lejos de sus bordes") else estado.hacer { r.first }
                })
                if (c.esTexto) Herramienta(Glifos.texto, "Texto", { estado.panel = Panel.TEXTO })
                if (c.esVideo) Herramienta(Glifos.velocidad, "Velocidad", { estado.panel = Panel.VELOCIDAD })
                Herramienta(Glifos.animacion, "Animar", { estado.panel = Panel.ANIMACION })
                if (i >= 0 && i < principal.lastIndex) Herramienta(Glifos.transicion, "Transicion", { estado.elegir(Seleccion.Paso(id)) })
                Herramienta(Glifos.encuadre, "Encuadre", { estado.panel = Panel.ENCUADRE })
                if (!c.esTexto) Herramienta(Glifos.color, "Color", { estado.panel = Panel.COLOR })
                Herramienta(Glifos.curva, "Curva", { estado.panel = Panel.CURVA }, resaltada = c.claves.isNotEmpty())
                if (c.esVideo) Herramienta(Glifos.volumen, "Volumen", { estado.panel = Panel.VOLUMEN })
                Herramienta(Glifos.duplicar, "Duplicar", { Edicion.duplicar(estado.proyecto, id)?.let { r -> estado.hacer { r.first } } })
                if (i >= 0) Herramienta(Glifos.aCapa, "A capa", { estado.hacer { Edicion.aCapa(it, id) } })
                else if (!c.esTexto) Herramienta(Glifos.aPrincipal, "A principal", { estado.hacer { Edicion.aPrincipal(it, id) } })
                Herramienta(Glifos.papelera, "Eliminar", { estado.hacer { Edicion.eliminar(it, id) }; estado.elegir(null) })
            }
        }
    }
}

/** Mensaje corto sobre la imagen que se va solo. */
@Composable
private fun Aviso(estado: EstadoEditor, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(estado.avisoN) {
        if (estado.avisoN == 0) return@LaunchedEffect
        visible = true
        delay(2600)
        visible = false
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(160)) + slideInVertically { -it / 2 }, exit = fadeOut(tween(300)) + slideOutVertically { -it / 3 }) {
        Text(
            estado.aviso ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Black,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(esquinas(18.dp))
                .background(Ink.White)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
