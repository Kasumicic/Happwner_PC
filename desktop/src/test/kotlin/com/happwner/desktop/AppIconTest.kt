package com.happwner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppIconTest {
    @Test
    fun createsTrayAndStatusNotifierSizes() {
        val image = AppIcon.image(32)
        val pixels = AppIcon.statusNotifierPixels(22)

        assertEquals(32, image.width)
        assertEquals(32, image.height)
        assertEquals(22 * 22 * 4, pixels.size)
        assertTrue(pixels.any { it.toInt() != 0 })
    }

    @Test
    fun statusNotifierPublishesProjectIcon() {
        val item = StatusNotifierItemObject("Happwner PC") {}

        assertEquals("happwner-pc", item.getIconName())
        assertEquals(listOf(22, 32, 64), item.getIconPixmap().map { it.width })
    }
}
