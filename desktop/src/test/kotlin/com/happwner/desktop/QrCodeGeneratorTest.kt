package com.happwner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QrCodeGeneratorTest {
    @Test
    fun createsRequestedBitmap() {
        val bitmap = QrCodeGenerator.create(
            "http://192.168.1.10:8166/sub/example",
            size = 128,
        )

        assertEquals(128, bitmap.width)
        assertEquals(128, bitmap.height)
    }

    @Test
    fun rejectsEmptyContent() {
        assertFailsWith<IllegalArgumentException> {
            QrCodeGenerator.create("")
        }
    }
}
