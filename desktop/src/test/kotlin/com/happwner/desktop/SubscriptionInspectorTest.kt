package com.happwner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionInspectorTest {
    @Test
    fun countsUriProfilesByProtocol() {
        val result = SubscriptionInspector.inspect(
            """
            vless://id@example.com:443#one
            vmess://encoded
            vless://id@example.net:443#two
            """.trimIndent().toByteArray(),
        )

        assertEquals(3, result.profileCount)
        assertEquals(mapOf("vless" to 2, "vmess" to 1), result.protocols)
    }

    @Test
    fun countsJsonOutbounds() {
        val result = SubscriptionInspector.inspect(
            """{"outbounds":[{"type":"vless"},{"type":"shadowsocks"},{"type":"direct"}]}""".toByteArray(),
        )

        assertEquals(2, result.profileCount)
        assertEquals(mapOf("ss" to 1, "vless" to 1), result.protocols)
    }

    @Test
    fun reportsTextWithoutProfilesAndKeepsPreview() {
        val result = SubscriptionInspector.inspect("provider maintenance".toByteArray())

        assertEquals(0, result.profileCount)
        assertTrue(result.preview.contains("maintenance"))
    }
}
