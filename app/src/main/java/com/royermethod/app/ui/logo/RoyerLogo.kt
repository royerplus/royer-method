package com.royermethod.app.ui.logo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.royermethod.app.ui.theme.Ink

/**
 * Logo de Royer Method: gato low-poly de neon.
 *
 * Alambre puro, 50 polilineas. Lo unico relleno son los ojos, y justo por eso
 * son lo primero que mira uno. Los trazados son los del diseno original, en su
 * espacio de 1254x1254; no se han redibujado ni redondeado.
 */

private const val BOX = 1254f

private const val D_EYES = "M456 561 549 634 629 627 640 615 562 516 Z M983 562 928 533 860 606 857 629 935 645 985 579 Z"

private const val D_MESH = "M793.8 797 L802.3 742.7 906.9 761.4 895 790 886.3 842.3 934 808.8 931.7 782 939 775 937 754.4 1043 770 1038 694.8 1031 678 1021.3 674.7 915 712.2 845.7 662.7 832 671.2 813.2 670 777.7 663.7 758 585.6 752.1 401 905.1 444.7 844.2 284 830 266.6 753 393.2 738 368 692.7 243.3 670 251 560 326 521.2 221.2 358 77 346.3 73.7 345 90 444 296 454 292 521.2 221.2 541 215 745.7 223.7 792 247 796.3 254.7 830 266.6 942 132 955 124 955 136 876.8 305 844.2 284 M793.8 797 L778.3 796.7 779.8 742 647.6 748.9 543 749 585.3 707.3 386 656 447.3 571.3 490 568 548.8 573 579 600 652.8 624 673.3 612.7 598 443.2 752.1 401 M793.8 797 L870.2 852 886.3 842.3 M744 896.8 L721 898 643.8 854 626 850 573.5 816.2 413.7 916.7 513 938 518.2 1083.6 352.7 1006.7 346 1018.8 248 1136 171 1173.8 151 1191 135 1252 M744 896.8 L802 895 870.2 852 M744 896.8 L766.8 804 778.3 796.7 M223.3 449.3 L251 225 256.7 220.7 268 116 324 77 346.3 73.7 M223.3 449.3 L217 483 222 492 228 565.8 167 681 157.3 687.3 139.2 749 145 766 244 884 259.7 886.3 266 878 378 825 540 769.8 535 752 436 771 324 802 M223.3 449.3 L235 442 249.3 420.3 261 230 256.7 220.7 M923.3 971.7 L890 900.2 1022 891 1034 886 1055.3 835.3 1063 827 1087.8 775 1043 770 1049.5 822 1063 827 M923.3 971.7 L835.7 941.7 870 906 890 900.2 M923.3 971.7 L939 993 975.7 1183.3 983 1207 959 1252 M548.8 573 L563 526 M779.8 742 L778 727 739 691 729.7 674.3 747 665 769 667.2 777 677 805 683 803 724 802.3 742.7 M934 808.8 L987 851 1022 891 M652.8 624 L696.3 661.7 729.7 674.3 M803 724 L813 717 828.3 690.3 832 671.2 M972.8 411 L958 404 881 317 876.8 305 M972.8 411 L1006.3 470.7 1015 479 1011.7 579.7 985 571 936.2 578 916.8 606 884 616 865.3 615.7 850.3 624.3 758 585.6 673.3 612.7 M972.8 411 L993 116 991.5 78.9 976 88 955 124 M828.3 690.3 L805 683 813.2 670 M906 459.8 L905.1 444.7 995 472 1006.3 470.7 M906 459.8 L898 469 860 587 857 607 865.3 615.7 M906 459.8 L936.2 578 M742.7 1119.5 L754 1125 848 1253 M742.7 1119.5 L739 1251 730 1251 666.1 1176.9 525.7 1086.3 736.7 986.3 813 947 829.5 948 895 1091.8 910 1103 964 1177 975.7 1183.3 M742.7 1119.5 L743.2 1098.2 759 1086 829.5 948 M249.3 420.3 L262 417 438 307 444 296 M1038 694.8 L1065.8 721 1061 695 1011.7 579.7 M643.8 854 L766.8 804 M351 909.2 L259.7 886.3 M351 909.2 L347 970 353.3 998.3 403.3 918.3 351 909.2 M157.3 687.3 L156 675 211 487 217 483 M347 970 L178 1158 171 1173.8 M796.3 254.7 L692.7 243.3 M540 769.8 L556 797.8 573.5 816.2 643 762 647.6 748.9 593.8 707 696.3 661.7 M745.7 223.7 L976 66 989.3 66.3 M346 1018.8 L340 1252 M447.3 571.3 L388 570 362 494 362 485 372 480 378 470 559.7 333.7 598 443.2 382 482 372 480 M850.3 624.3 L845.7 662.7 M1065.8 721 L1087.8 775 M937 754.4 L915.3 723.7 906.9 761.4 931.7 782 1049.5 822 1055.3 835.3 M228 565.8 L377 656 372 663 139.2 749 M1021.3 674.7 L1013 667 941 643 916.8 606 M666.1 1176.9 L621 1252 M915 712.2 L915.3 723.7 M556 797.8 L376 882 M895 1091.8 L870 1252 M743.2 1098.2 L736.7 986.3 M530 1252 L517.7 1094.7 355 1253"

/** Los trazados se parsean una sola vez para toda la app, no por instancia. */
private object CatPaths {
    val mesh: Path by lazy { PathParser().parsePathString(D_MESH).toPath() }
    val eyes: Path by lazy { PathParser().parsePathString(D_EYES).toPath() }
}

private class LogoBitmaps(val mesh: ImageBitmap, val eyes: ImageBitmap)

/**
 * Halo por capas: el mismo trazo varias veces, de ancho y transparente a fino
 * y solido. Reproduce el bloom de tres capas del SVG original sin necesitar
 * RenderEffect, que solo existe a partir de Android 12.
 */
private fun DrawScope.neon(path: Path, width: Float) {
    val layers = floatArrayOf(
        9.0f, 0.040f,
        5.5f, 0.070f,
        3.0f, 0.120f,
        1.8f, 0.220f
    )
    var i = 0
    while (i < layers.size) {
        drawPath(
            path = path,
            color = Ink.White,
            alpha = layers[i + 1],
            style = Stroke(
                width = width * layers[i],
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        i += 2
    }
    drawPath(
        path = path,
        color = Ink.White,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * Rasteriza el logo a su tamano exacto en pixeles.
 *
 * Este es el arreglo de rendimiento. La malla tiene 234 segmentos y el halo
 * la traza 5 veces: son 1170 segmentos por frame y por logo. Como el latido
 * anima el brillo, eso se rehacia en cada frame, y a 144Hz no cabe en los
 * 6.9ms de presupuesto.
 *
 * La geometria nunca cambia: lo unico que varia es el brillo. Asi que se
 * dibuja una sola vez a un bitmap y despues cada frame es un unico volcado
 * de textura con distinto alfa. De 1170 operaciones de trazo a 2 volcados.
 */
private fun renderLogo(px: Int, density: Density): LogoBitmaps {
    fun bake(block: DrawScope.() -> Unit): ImageBitmap {
        val bmp = ImageBitmap(px, px)
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = GraphicsCanvas(bmp),
            size = Size(px.toFloat(), px.toFloat())
        ) { block() }
        return bmp
    }

    val k = px / BOX
    // El grosor no puede ser fijo. El trazo original de 5.5 es el 0.44% del
    // ancho: a 800px se ve bien, pero a 34px son 0.15px y la malla desaparece.
    // Se calcula al reves, partiendo del grosor que se quiere en pantalla.
    val sw = (1.15f * density.density) / k

    val mesh = bake { scale(k, k, pivot = Offset.Zero) { neon(CatPaths.mesh, sw) } }
    val eyes = bake {
        scale(k, k, pivot = Offset.Zero) {
            neon(CatPaths.eyes, sw * 0.9f)
            drawPath(path = CatPaths.eyes, color = Ink.White)
        }
    }
    return LogoBitmaps(mesh, eyes)
}

/**
 * Cache global por tamano en pixeles. La app usa cinco tamanos distintos y
 * varios se repiten entre pantallas; sin esto cada instancia rasterizaria su
 * propia copia. En total no llega a 1 MB.
 */
private val bitmapCache = HashMap<Int, LogoBitmaps>()

private fun logoFor(px: Int, density: Density): LogoBitmaps =
    bitmapCache.getOrPut(px) { renderLogo(px, density) }

/**
 * @param glow 0f apaga el halo, 1f es el brillo nominal. Animalo para el latido.
 * @param progress 0f..1f revela primero la malla y al final los ojos.
 */
@Composable
fun RoyerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    glow: Float = 1f,
    progress: Float = 1f
) {
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val bmp = remember(px) { logoFor(px, density) }

    Canvas(modifier = modifier.size(size)) {
        val sMesh = (progress / 0.60f).coerceIn(0f, 1f)
        val sEyes = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
        val brillo = 0.60f + 0.40f * glow

        if (sMesh > 0f) drawImage(bmp.mesh, alpha = (sMesh * brillo).coerceIn(0f, 1f))
        if (sEyes > 0f) drawImage(bmp.eyes, alpha = (sEyes * brillo).coerceIn(0f, 1f))
    }
}

