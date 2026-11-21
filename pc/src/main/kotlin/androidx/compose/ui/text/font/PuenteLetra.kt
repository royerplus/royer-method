@file:JvmName("PuenteLetra")
package androidx.compose.ui.text.font

/** Como en Android: la letra de un texto se usa en la interfaz. */
fun FontFamily(t: android.graphics.Typeface): FontFamily = t.familia
