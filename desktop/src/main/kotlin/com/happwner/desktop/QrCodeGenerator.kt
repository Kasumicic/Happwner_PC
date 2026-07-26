package com.happwner.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

object QrCodeGenerator {
    fun create(content: String, size: Int = 320): ImageBitmap {
        require(content.isNotBlank()) { "QR content cannot be blank" }
        require(size > 0) { "QR size must be positive" }

        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
            ),
        )
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size) {
            for (x in 0 until size) {
                image.setRGB(x, y, if (matrix[x, y]) BLACK else WHITE)
            }
        }
        return image.toComposeImageBitmap()
    }

    private const val BLACK = 0x000000
    private const val WHITE = 0xFFFFFF
}
