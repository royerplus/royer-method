package com.royermethod.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Iconos del editor, dibujados a trazo.
 *
 * La app no carga ninguna libreria de iconos: todos salen de aqui, con el
 * mismo grosor de linea y las mismas puntas redondas, para que se lean como
 * neon y tomen el color que el usuario haya elegido. Cada glifo dibuja en un
 * cuadrado de 24 unidades escalado al tamano que le den.
 */
typealias Glifo = DrawScope.(Color) -> Unit

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f
private fun DrawScope.p(x: Float, y: Float) = Offset(u(x) + (size.width - size.minDimension) / 2f, u(y) + (size.height - size.minDimension) / 2f)
private fun DrawScope.trazo() = Stroke(width = u(1.7f), cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.linea(c: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(c, p(x1, y1), p(x2, y2), u(1.7f), StrokeCap.Round)

private fun DrawScope.camino(c: Color, cerrar: Boolean = false, relleno: Boolean = false, vararg xy: Float) {
    val path = Path()
    path.moveTo(p(xy[0], xy[1]).x, p(xy[0], xy[1]).y)
    var i = 2
    while (i < xy.size) {
        val o = p(xy[i], xy[i + 1])
        path.lineTo(o.x, o.y)
        i += 2
    }
    if (cerrar) path.close()
    if (relleno) drawPath(path, c) else drawPath(path, c, style = trazo())
}

private fun DrawScope.caja(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
    drawRoundRect(c, p(x, y), Size(u(w), u(h)), CornerRadius(u(r)), style = trazo())

private fun DrawScope.circulo(c: Color, x: Float, y: Float, r: Float, relleno: Boolean = false) =
    if (relleno) drawCircle(c, u(r), p(x, y)) else drawCircle(c, u(r), p(x, y), style = trazo())

object Glifos {
    val atras: Glifo = { c -> camino(c, false, false, 15f, 5f, 8f, 12f, 15f, 19f) }
    val cerrar: Glifo = { c -> linea(c, 6f, 6f, 18f, 18f); linea(c, 18f, 6f, 6f, 18f) }
    val listo: Glifo = { c -> camino(c, false, false, 5f, 12.5f, 10f, 17.5f, 19f, 7f) }
    val mas: Glifo = { c -> linea(c, 12f, 5f, 12f, 19f); linea(c, 5f, 12f, 19f, 12f) }

    val deshacer: Glifo = { c ->
        camino(c, false, false, 8f, 5f, 4f, 9f, 8f, 13f)
        val path = Path()
        path.moveTo(p(4.5f, 9f).x, p(4.5f, 9f).y)
        path.cubicTo(p(13f, 9f).x, p(13f, 9f).y, p(20f, 9f).x, p(20f, 9f).y, p(20f, 14f).x, p(20f, 14f).y)
        path.cubicTo(p(20f, 18f).x, p(20f, 18f).y, p(17f, 19f).x, p(17f, 19f).y, p(13f, 19f).x, p(13f, 19f).y)
        drawPath(path, c, style = trazo())
    }
    val rehacer: Glifo = { c ->
        camino(c, false, false, 16f, 5f, 20f, 9f, 16f, 13f)
        val path = Path()
        path.moveTo(p(19.5f, 9f).x, p(19.5f, 9f).y)
        path.cubicTo(p(11f, 9f).x, p(11f, 9f).y, p(4f, 9f).x, p(4f, 9f).y, p(4f, 14f).x, p(4f, 14f).y)
        path.cubicTo(p(4f, 18f).x, p(4f, 18f).y, p(7f, 19f).x, p(7f, 19f).y, p(11f, 19f).x, p(11f, 19f).y)
        drawPath(path, c, style = trazo())
    }

    val play: Glifo = { c -> camino(c, true, true, 8f, 5f, 19f, 12f, 8f, 19f) }
    val pausa: Glifo = { c ->
        drawRoundRect(c, p(7f, 5f), Size(u(3.4f), u(14f)), CornerRadius(u(1f)))
        drawRoundRect(c, p(13.6f, 5f), Size(u(3.4f), u(14f)), CornerRadius(u(1f)))
    }

    /** Fotograma clave: rombo. Relleno cuando hay uno en el cabezal. */
    val rombo: Glifo = { c -> camino(c, true, false, 12f, 4f, 20f, 12f, 12f, 20f, 4f, 12f); linea(c, 12f, 9f, 12f, 15f); linea(c, 9f, 12f, 15f, 12f) }
    val romboLleno: Glifo = { c -> camino(c, true, true, 12f, 4f, 20f, 12f, 12f, 20f, 4f, 12f) }
    val romboQuitar: Glifo = { c -> camino(c, true, false, 12f, 4f, 20f, 12f, 12f, 20f, 4f, 12f); linea(c, 9f, 12f, 15f, 12f) }

    val dividir: Glifo = { c ->
        linea(c, 12f, 3f, 12f, 21f)
        caja(c, 3.5f, 7f, 5.5f, 10f, 1.5f)
        caja(c, 15f, 7f, 5.5f, 10f, 1.5f)
    }
    val velocidad: Glifo = { c ->
        val path = Path()
        path.addArc(androidx.compose.ui.geometry.Rect(p(4f, 6f), Size(u(16f), u(16f))), 180f, 180f)
        drawPath(path, c, style = trazo())
        linea(c, 12f, 14f, 16.5f, 9f)
        circulo(c, 12f, 14f, 1.4f, relleno = true)
    }
    val volumen: Glifo = { c ->
        camino(c, true, false, 4f, 9.5f, 8f, 9.5f, 12.5f, 5.5f, 12.5f, 18.5f, 8f, 14.5f, 4f, 14.5f)
        val a = Path()
        a.addArc(androidx.compose.ui.geometry.Rect(p(11f, 8f), Size(u(8f), u(8f))), -55f, 110f)
        drawPath(a, c, style = trazo())
        val b = Path()
        b.addArc(androidx.compose.ui.geometry.Rect(p(9f, 4.5f), Size(u(14f), u(15f))), -55f, 110f)
        drawPath(b, c, style = trazo())
    }
    val animacion: Glifo = { c ->
        circulo(c, 14.5f, 12f, 5.5f)
        linea(c, 3f, 9f, 7f, 9f)
        linea(c, 2f, 12f, 6.5f, 12f)
        linea(c, 3f, 15f, 7f, 15f)
    }
    val transicion: Glifo = { c -> camino(c, true, false, 4f, 6f, 12f, 12f, 4f, 18f); camino(c, true, false, 20f, 6f, 12f, 12f, 20f, 18f) }
    val corte: Glifo = { c -> linea(c, 12f, 5f, 12f, 19f) }
    val color: Glifo = { c ->
        circulo(c, 9f, 9.5f, 5f)
        circulo(c, 15f, 9.5f, 5f)
        circulo(c, 12f, 14.5f, 5f)
    }
    val encuadre: Glifo = { c ->
        camino(c, false, false, 7f, 3f, 7f, 17f, 21f, 17f)
        camino(c, false, false, 3f, 7f, 17f, 7f, 17f, 21f)
    }
    val texto: Glifo = { c ->
        linea(c, 5f, 6f, 19f, 6f)
        linea(c, 12f, 6f, 12f, 19f)
        linea(c, 9.5f, 19f, 14.5f, 19f)
    }
    val duplicar: Glifo = { c ->
        caja(c, 8f, 8f, 12f, 12f)
        camino(c, false, false, 16f, 5f, 16f, 4f, 4f, 4f, 4f, 16f, 5f, 16f)
    }
    val superponer: Glifo = { c ->
        caja(c, 3.5f, 8f, 12f, 12f)
        caja(c, 9f, 3.5f, 11.5f, 11.5f)
    }
    val aPrincipal: Glifo = { c ->
        linea(c, 12f, 3.5f, 12f, 13.5f)
        camino(c, false, false, 8f, 10f, 12f, 14f, 16f, 10f)
        caja(c, 3.5f, 16.5f, 17f, 4f, 1.5f)
    }
    val aCapa: Glifo = { c ->
        linea(c, 12f, 14f, 12f, 4f)
        camino(c, false, false, 8f, 8f, 12f, 4f, 16f, 8f)
        caja(c, 3.5f, 16.5f, 17f, 4f, 1.5f)
    }
    val papelera: Glifo = { c ->
        linea(c, 4f, 7f, 20f, 7f)
        camino(c, false, false, 9.5f, 7f, 10f, 4.5f, 14f, 4.5f, 14.5f, 7f)
        camino(c, false, false, 6.5f, 7f, 7.5f, 20f, 16.5f, 20f, 17.5f, 7f)
        linea(c, 10.5f, 11f, 10.5f, 16f)
        linea(c, 13.5f, 11f, 13.5f, 16f)
    }
    val audio: Glifo = { c ->
        camino(c, false, false, 9f, 17f, 9f, 5f, 19f, 3.5f, 19f, 15f)
        circulo(c, 6.5f, 17.5f, 2.6f)
        circulo(c, 16.5f, 15.5f, 2.6f)
    }
    val formato: Glifo = { c ->
        caja(c, 6.5f, 3.5f, 11f, 17f, 2f)
        linea(c, 3f, 9f, 3f, 15f)
        linea(c, 21f, 9f, 21f, 15f)
    }
    val editar: Glifo = { c ->
        camino(c, true, false, 5f, 19f, 5.5f, 15f, 15.5f, 5f, 19f, 8.5f, 9f, 18.5f)
        linea(c, 13f, 7.5f, 16.5f, 11f)
    }
    val curva: Glifo = { c ->
        val path = Path()
        path.moveTo(p(4f, 19f).x, p(4f, 19f).y)
        path.cubicTo(p(13f, 19f).x, p(13f, 19f).y, p(11f, 5f).x, p(11f, 5f).y, p(20f, 5f).x, p(20f, 5f).y)
        drawPath(path, c, style = trazo())
        circulo(c, 4f, 19f, 1.5f, relleno = true)
        circulo(c, 20f, 5f, 1.5f, relleno = true)
    }
    val exportar: Glifo = { c ->
        linea(c, 12f, 15f, 12f, 3.5f)
        camino(c, false, false, 7.5f, 8f, 12f, 3.5f, 16.5f, 8f)
        camino(c, false, false, 4.5f, 13f, 4.5f, 20f, 19.5f, 20f, 19.5f, 13f)
    }
    val ajustes: Glifo = { c ->
        linea(c, 4f, 7f, 20f, 7f); circulo(c, 9f, 7f, 2.2f, relleno = true)
        linea(c, 4f, 12f, 20f, 12f); circulo(c, 15f, 12f, 2.2f, relleno = true)
        linea(c, 4f, 17f, 20f, 17f); circulo(c, 7f, 17f, 2.2f, relleno = true)
    }
    val espejo: Glifo = { c ->
        linea(c, 12f, 3f, 12f, 21f)
        camino(c, true, false, 9f, 7f, 9f, 17f, 3.5f, 17f)
        camino(c, true, true, 15f, 7f, 15f, 17f, 20.5f, 17f)
    }
    val girar: Glifo = { c ->
        val a = Path()
        a.addArc(androidx.compose.ui.geometry.Rect(p(4.5f, 5f), Size(u(15f), u(15f))), -60f, 300f)
        drawPath(a, c, style = trazo())
        camino(c, false, false, 16f, 2.5f, 16.5f, 6.5f, 12.5f, 7.5f)
    }
    val reiniciar: Glifo = { c ->
        val a = Path()
        a.addArc(androidx.compose.ui.geometry.Rect(p(4.5f, 4.5f), Size(u(15f), u(15f))), 200f, 290f)
        drawPath(a, c, style = trazo())
        camino(c, false, false, 3.5f, 6f, 4.5f, 10.5f, 9f, 9.5f)
    }
    val guardar: Glifo = { c ->
        camino(c, true, false, 6f, 4f, 18f, 4f, 18f, 20f, 12f, 15.5f, 6f, 20f)
    }
    val flecha: Glifo = { c -> linea(c, 4f, 12f, 19f, 12f); camino(c, false, false, 13f, 6f, 19f, 12f, 13f, 18f) }
    /** Fotogramas que se encadenan: generar los que faltan. */
    val fluidez: Glifo = { c ->
        camino(c, true, false, 11f, 7f, 20f, 12f, 11f, 17f)
        linea(c, 3.5f, 9f, 8f, 9f)
        linea(c, 2.5f, 12f, 8f, 12f)
        linea(c, 3.5f, 15f, 8f, 15f)
    }
    /** Dos flechas que aprietan: menos peso. */
    val compresion: Glifo = { c ->
        linea(c, 12f, 3.5f, 12f, 9.5f); camino(c, false, false, 9f, 6.5f, 12f, 9.5f, 15f, 6.5f)
        linea(c, 12f, 20.5f, 12f, 14.5f); camino(c, false, false, 9f, 17.5f, 12f, 14.5f, 15f, 17.5f)
        linea(c, 5f, 12f, 19f, 12f)
    }
}
