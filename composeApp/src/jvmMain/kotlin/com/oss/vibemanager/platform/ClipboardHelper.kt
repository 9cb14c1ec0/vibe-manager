package com.oss.vibemanager.platform

import com.oss.vibemanager.model.ContentBlock
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

fun getImagesFromClipboard(): List<ContentBlock.Image> {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage
            if (image != null) {
                val byteArrayStream = ByteArrayOutputStream()
                ImageIO.write(image, "png", byteArrayStream)
                val bytes = byteArrayStream.toByteArray()
                val base64 = Base64.getEncoder().encodeToString(bytes)
                listOf(ContentBlock.Image(base64Data = base64, mediaType = "image/png"))
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
