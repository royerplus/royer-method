@file:JvmName("PuentesAndroid")
package androidx.compose.ui.graphics

import androidx.compose.ui.graphics.toComposeImageBitmap

/** Como en Android: un android.graphics.Bitmap se ensena en Compose. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = imagen.toComposeImageBitmap()

/** Una imagen de Skia en Compose (el nombre de Compose cambia entre versiones). */
fun toComposeImageBitmapDe(img: org.jetbrains.skia.Image): ImageBitmap = img.toComposeImageBitmap()
