package com.happwner.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object AppIcon {
    private val source: BufferedImage by lazy {
        requireNotNull(AppIcon::class.java.getResourceAsStream("/happwner-pc.png")) {
            "Application icon resource is missing"
        }.use { stream ->
            requireNotNull(ImageIO.read(stream)) { "Application icon resource is invalid" }
        }
    }

    fun image(size: Int): BufferedImage {
        require(size > 0) { "Icon size must be positive" }
        return BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().use { graphics ->
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                )
                graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY,
                )
                graphics.drawImage(AppIcon.source, 0, 0, size, size, null)
            }
        }
    }

    fun statusNotifierPixels(size: Int): ByteArray {
        val image = image(size)
        return ByteArray(size * size * 4).also { bytes ->
            var offset = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val argb = image.getRGB(x, y)
                    bytes[offset++] = (argb ushr 24).toByte()
                    bytes[offset++] = (argb ushr 16).toByte()
                    bytes[offset++] = (argb ushr 8).toByte()
                    bytes[offset++] = argb.toByte()
                }
            }
        }
    }
}

private fun <T : java.awt.Graphics2D> T.use(block: (T) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}
