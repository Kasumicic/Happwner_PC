package com.happwner.desktop

import com.happwner.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class StateRepositoryTest {
    @Test
    fun missingThemeDefaultsToDark() {
        assertEquals(ThemeMode.DARK, parseThemeMode(null))
    }

    @Test
    fun invalidThemeDefaultsToDark() {
        assertEquals(ThemeMode.DARK, parseThemeMode("UNKNOWN"))
    }

    @Test
    fun knownThemeIsPreserved() {
        assertEquals(ThemeMode.SYSTEM, parseThemeMode("SYSTEM"))
    }
}
