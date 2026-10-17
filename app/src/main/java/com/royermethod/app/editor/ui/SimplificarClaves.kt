package com.royermethod.app.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Simplificacion
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.editor.modelo.Tolerancia
import com.royermethod.app.editor.modelo.Trayectoria
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonLleno
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Herramientas de claves para los paneles (Fotogramas clave y Partes):
 * recorrerlas una a una y simplificar una trayectoria con muchas.
 *
 * Simplificar ensena antes de aplicar cuantas claves quedan y cuanto se
 * aparta el resultado (Trayectoria: pixeles de exportacion y opacidad, en
 * todos los instantes que se dibujan). Aplicar es un paso de deshacer y
 * guarda las de antes: "Restaurar original" vuelve a ellas y "Olvidar
 * original" se queda con las nuevas. Las cuentas van fuera del hilo
 * principal: con cientos de claves tardan algo.
 */

/** De quien son las claves: el bloque entero o una de sus partes. */
internal sealed interface DuenoClaves {
    val id: Long

    data class Bloque(override val id: Long) : DuenoClaves
    data class Parte(override val id: Long, val indice: Int) : DuenoClaves
}

/** Lo puro del simplificador de los paneles (se prueba en el ordenador). */
internal object Simplificador {

    /** Las tolerancias que se ofrecen: medio pixel es la de [Tolerancia] por defecto. */
    val TOLERANCIAS = listOf(0.5f, 1f, 2f)

    /** [px] de exportacion y lo mismo en niveles de opacidad de 255 (0,5 px y 0,5/255 por defecto). */
    fun tolerancia(px: Float): Tolerancia = if (px == 0.5f) Tolerancia() else Tolerancia(px, px / 255f)

    /**
     * Lo que se ensena antes de aplicar: el proyecto del que se partio, el
     * que quedaria y el informe (cuantas claves, error real).
     */
    class Prueba(val base: Proyecto, val dueno: DuenoClaves, val tolerancia: Tolerancia, val resultado: Proyecto, val informe: Simplificacion)

    fun claves(p: Proyecto, d: DuenoClaves): List<Clave>? {
        val c = Edicion.clip(p, d.id) ?: return null
        return when (d) {
            is DuenoClaves.Bloque -> c.claves
            is DuenoClaves.Parte -> c.piezas.getOrNull(d.indice)?.claves
        }
    }

    fun originales(p: Proyecto, d: DuenoClaves): List<Clave>? {
        val c = Edicion.clip(p, d.id) ?: return null
        return when (d) {
            is DuenoClaves.Bloque -> c.clavesOriginales
            is DuenoClaves.Parte -> c.piezas.getOrNull(d.indice)?.clavesOriginales
        }
    }

    /** Simplifica sin tocar nada todavia; null si no hay claves. */
    fun probar(p: Proyecto, d: DuenoClaves, tol: Tolerancia = Tolerancia()): Prueba? {
        val r = when (d) {
            is DuenoClaves.Bloque -> Edicion.simplificarClaves(p, d.id, tol)
            is DuenoClaves.Parte -> Edicion.simplificarPieza(p, d.id, d.indice, tol)
        } ?: return null
        return Prueba(p, d, tol, r.first, r.second)
    }

    /**
     * Lo que hace "Aplicar" (dentro de un solo estado.hacer): si el proyecto
     * sigue siendo el de la prueba, su resultado tal cual; si cambio algo
     * entretanto, se vuelve a simplificar sobre el de ahora.
     */
    fun aplicar(actual: Proyecto, prueba: Prueba): Proyecto =
        if (actual === prueba.base) prueba.resultado
        else probar(actual, prueba.dueno, prueba.tolerancia)?.resultado ?: actual

    fun restaurar(p: Proyecto, d: DuenoClaves): Proyecto = when (d) {
        is DuenoClaves.Bloque -> Edicion.restaurarClaves(p, d.id)
        is DuenoClaves.Parte -> Edicion.restaurarPieza(p, d.id, d.indice)
    }

    fun olvidar(p: Proyecto, d: DuenoClaves): Proyecto = when (d) {
        is DuenoClaves.Bloque -> Edicion.descartarOriginales(p, d.id)
        is DuenoClaves.Parte -> Edicion.descartarOriginalesPieza(p, d.id, d.indice)
    }

    /**
     * Cuanto se aparta lo de ahora del original guardado (px de exportacion,
     * opacidad 0..1), con la misma medida y los mismos instantes que
     * Edicion.simplificarClaves. Null si no hay original.
     */
    fun contraOriginal(p: Proyecto, d: DuenoClaves): Pair<Float, Float>? {
        val o = originales(p, d) ?: return null
        val ahora = claves(p, d) ?: return null
        val c = Edicion.clip(p, d.id) ?: return null
        val ini = Edicion.inicio(p, d.id) ?: return null
        val (w, h) = p.formato.medidas(p.salida.ladoCorto)
        // Las muestras del obturador al exportar, como Escena
        val k = if (p.salida.desenfoqueMovimiento) p.salida.muestras.coerceIn(1, Escena.MAX_MUESTRAS) else 1
        val ins = Trayectoria.instantes(c.duracionUs, k, o + ahora, ini)
        return Trayectoria.comparar(o, ahora, ins, w.toFloat(), h.toFloat(), quitarRepetidas = d is DuenoClaves.Parte)
    }

    private fun num(v: Float, decimales: Int): String {
        val s = "%.${decimales}f".format(Locale.ROOT, v)
        // "0.50" -> "0.5", "2.00" -> "2"
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }

    /** "1 px", "0.5 px" */
    fun textoPx(px: Float): String = num(px, 2) + " px"

    /** "61 -> 2 claves" */
    fun resumen(s: Simplificacion): String = "${s.antes} -> ${s.despues} claves"

    /** "error max 0.42 px (limite 0.5 px)" */
    fun textoError(px: Float, tol: Tolerancia): String = "error max ${textoPx(px)} (limite ${textoPx(tol.px)})"

    /** "opacidad 0.3/255 (limite 0.5/255)" */
    fun textoOpacidad(op: Float, tol: Tolerancia): String =
        "opacidad ${num(op * 255f, 1)}/255 (limite ${num(tol.opacidad * 255f, 1)}/255)"

    /** "Frente al original: error max 0.15 px, opacidad 0/255" */
    fun textoContra(px: Float, op: Float): String =
        "Frente al original: error max ${textoPx(px)}, opacidad ${num(op * 255f, 1)}/255"
}

/**
 * Para recorrer las claves una a una: "<", "Clave 2 de 3 · 0.001 s" y ">"
 * (botones de 44 dp) que llevan el cabezal a la anterior o a la siguiente; y,
 * si son pocas, una pastilla por clave con su tiempo exacto. [local] es el
 * cabezal dentro del bloque: se lee en un estado derivado, asi esto solo se
 * recompone al pasar de una clave a otra, no a cada cuadro.
 */
@Composable
internal fun NavegadorClaves(tiempos: List<Long>, tolerancia: Long, local: () -> Long?, onIr: (Long) -> Unit) {
    if (tiempos.isEmpty()) return
    // La lambda cambia en cada recomposicion del panel: se lee la ultima
    val leer by rememberUpdatedState(local)
    val pos by remember(tiempos, tolerancia) {
        derivedStateOf { ClavesLinea.posicion(tiempos, leer(), tolerancia) }
    }
    val p = pos
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonFlecha("‹", p.anterior != null) { p.anterior?.let(onIr) }
        Text(
            if (p.indice >= 0) "Clave ${p.indice + 1} de ${tiempos.size} · ${TiempoPreciso.texto(tiempos[p.indice])}"
            else "${tiempos.size} claves · ninguna en el cabezal",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            color = if (p.indice >= 0) Ink.White else Ink.White70,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        BotonFlecha("›", p.siguiente != null) { p.siguiente?.let(onIr) }
    }
    if (tiempos.size <= MAX_PASTILLAS) {
        Opciones(tiempos, if (p.indice >= 0) tiempos[p.indice] else null, { TiempoPreciso.texto(it) }, onIr)
    }
}

/** Con mas claves que esto, solo las flechas: cientos de pastillas no se pueden leer. */
private const val MAX_PASTILLAS = 12

@Composable
private fun BotonFlecha(texto: String, activo: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Ink.White08)
            .clickable(enabled = activo, onClick = onClick)
            .alpha(if (activo) 1f else 0.3f),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = MaterialTheme.typography.titleLarge, color = Ink.White90)
    }
}

/**
 * "Simplificar claves" del bloque o de una parte, con antes/despues:
 * cuantas claves quedan y el error real frente a la tolerancia. Aplicar,
 * restaurar y olvidar son un paso de deshacer cada uno.
 */
@Composable
internal fun SimplificadorClaves(estado: EstadoEditor, dueno: DuenoClaves) {
    val p = estado.proyecto
    val claves = Simplificador.claves(p, dueno) ?: return
    val originales = Simplificador.originales(p, dueno)
    var tolPx by rememberSaveable { mutableFloatStateOf(0.5f) }
    var peticion by remember(dueno) { mutableIntStateOf(0) }
    var calculando by remember(dueno) { mutableStateOf(false) }
    var prueba by remember(dueno) { mutableStateOf<Simplificador.Prueba?>(null) }
    // La prueba vale mientras las claves sigan siendo las de cuando se hizo
    val vigente = prueba?.takeIf { Simplificador.claves(it.base, dueno) == claves && it.tolerancia == Simplificador.tolerancia(tolPx) }

    LaunchedEffect(peticion) {
        if (peticion == 0) return@LaunchedEffect
        calculando = true
        val base = estado.proyecto
        val tol = Simplificador.tolerancia(tolPx)
        prueba = withContext(Dispatchers.Default) { Simplificador.probar(base, dueno, tol) }
        calculando = false
    }
    // Lo de ahora frente al original guardado, tambien aparte
    var contra by remember(dueno) { mutableStateOf<Pair<Float, Float>?>(null) }
    LaunchedEffect(claves, originales) {
        contra = null
        if (originales != null) contra = withContext(Dispatchers.Default) { Simplificador.contraOriginal(p, dueno) }
    }

    Etiqueta("Simplificar claves")
    // La primera y la ultima se quedan siempre: con menos de tres no hay que probar
    val puede = claves.size >= 3
    if (puede) {
        Nota("Quita las claves que sobran sin apartarse mas de la tolerancia en ningun cuadro exportado.", color = Ink.White50)
        Opciones(Simplificador.TOLERANCIAS, tolPx, { Simplificador.textoPx(it) }, { tolPx = it })
    }

    val pr = vigente
    if (puede && pr != null) {
        val s = pr.informe
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(esquinas(14.dp))
                .border(1.dp, Ink.White30, esquinas(14.dp))
                .padding(14.dp)
        ) {
            if (s.despues < s.antes) {
                Text(Simplificador.resumen(s), style = MaterialTheme.typography.titleMedium, color = Ink.White)
                Spacer(Modifier.height(4.dp))
                Nota(Simplificador.textoError(s.errorMaxPx, s.tolerancia), color = Ink.White70)
                Nota(Simplificador.textoOpacidad(s.errorMaxOpacidad, s.tolerancia), color = Ink.White70)
                Nota("Medido en ${s.instantes} instantes de la exportacion", color = Ink.White50)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonLleno("Aplicar", {
                        estado.hacer { Simplificador.aplicar(it, pr) }
                        prueba = null
                    })
                    BotonBorde("Cancelar", { prueba = null })
                }
            } else {
                Text("No sobra ninguna clave", style = MaterialTheme.typography.titleMedium, color = Ink.White)
                Nota("Con ${Simplificador.textoPx(s.tolerancia.px)} cada una de las ${s.antes} hace falta. Prueba otra tolerancia.", color = Ink.White70)
                Spacer(Modifier.height(8.dp))
                BotonBorde("Cerrar", { prueba = null })
            }
        }
    } else if (puede) {
        BotonBorde(
            if (calculando) "Calculando..." else "Simplificar claves (${claves.size})",
            { if (!calculando) peticion++ }
        )
    }

    if (originales != null) {
        Spacer(Modifier.height(8.dp))
        Nota("Original guardado: ${originales.size} claves, ahora ${claves.size}.", color = Ink.White70)
        contra?.let { (px, op) ->
            Nota(Simplificador.textoContra(px, op), color = Ink.White50)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonBorde("Restaurar original", {
                estado.hacer { Simplificador.restaurar(it, dueno) }
                prueba = null
            })
            BotonBorde("Olvidar original", { estado.hacer { Simplificador.olvidar(it, dueno) } })
        }
        Nota("Las claves que quedan se cambian una a una arriba.", Modifier.padding(top = 4.dp), color = Ink.White50)
    }
}
