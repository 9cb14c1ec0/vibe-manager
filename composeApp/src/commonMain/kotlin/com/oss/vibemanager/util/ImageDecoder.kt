package com.oss.vibemanager.util

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeBase64ToImageBitmap(base64: String): ImageBitmap?
