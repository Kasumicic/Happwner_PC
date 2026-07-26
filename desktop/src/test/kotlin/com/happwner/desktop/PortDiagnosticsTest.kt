package com.happwner.desktop

import com.happwner.ServerSettings
import java.net.BindException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class PortDiagnosticsTest {
    @Test
    fun reportsOccupiedPortInSelectedLanguage() {
        val message = PortDiagnostics.describe(
            BindException("Address already in use"),
            ServerSettings(port = 18166, language = "ru"),
        )

        assertContains(message, "18166")
        assertContains(message, "занят")
    }

    @Test
    fun includesKnownOwner() {
        val message = PortDiagnostics.occupiedPortMessage(
            port = 8166,
            owner = PortOwner(1234, "/usr/bin/example-server"),
            language = "en",
        )

        assertContains(message, "example-server")
        assertContains(message, "PID 1234")
    }

    @Test
    fun parsesWindowsListeningSocket() {
        assertEquals(
            4242,
            PortDiagnostics.parseWindowsNetstatLine(
                "TCP    0.0.0.0:8166    0.0.0.0:0    LISTENING    4242",
                8166,
            ),
        )
        assertEquals(
            null,
            PortDiagnostics.parseWindowsNetstatLine(
                "TCP    0.0.0.0:9000    0.0.0.0:0    LISTENING    4242",
                8166,
            ),
        )
    }
}
