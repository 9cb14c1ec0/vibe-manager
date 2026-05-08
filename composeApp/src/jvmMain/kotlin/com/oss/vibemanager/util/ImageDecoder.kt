package com.oss.vibemanager.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.util.Base64

actual fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
    return try {
        val bytes = Base64.getDecoder().decode(base64)
        val skiaImage = Image.makeFromEncoded(bytes)
        val bitmap = Bitmap.makeFromImage(skiaImage)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
