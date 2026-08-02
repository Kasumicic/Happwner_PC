package com.happwner

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubscriptionProcessorTest {
    @Test
    fun plainResponsePassesThroughByDefault() {
        val subscription = Subscription(name = "test", source = "https://example.com/sub")
        assertEquals(
            "vless://example",
            SubscriptionProcessor.transform(subscription.source, "vless://example", emptyMap(), subscription),
        )
    }

    @Test
    fun base64ResponseIsDecodedByDefault() {
        val subscription = Subscription(name = "test", source = "https://example.com/sub")
        val encoded = Base64.getEncoder().encodeToString("vless://example".toByteArray())

        assertEquals(
            "vless://example",
            SubscriptionProcessor.transform(subscription.source, encoded, emptyMap(), subscription),
        )
    }

    @Test
    fun reportsJsonProfilesThatCouldNotBeConvertedToUri() {
        val subscription = Subscription(
            name = "test",
            source = "https://example.com/sub",
            decodeBase64 = false,
            jsonToUri = true,
        )
        val json = """{"protocol":"wireguard","settings":{"secretKey":"keep-me"}}"""

        val result = SubscriptionProcessor.transformWithStats(
            subscription.source,
            json,
            emptyMap(),
            subscription,
        )

        assertEquals(json, result.text)
        assertEquals(1, result.uriPreserved)
        assertEquals(0, result.xraySkipped)
        assertTrue(result.hasWarnings)
    }

    @Test
    fun reportsPartiallySkippedXrayConversion() {
        val subscription = Subscription(
            name = "test",
            source = "https://example.com/sub",
            decodeBase64 = false,
            xrayToSingBox = true,
        )
        val result = SubscriptionProcessor.transformWithStats(
            subscription.source,
            "vless://existing\n${unsupportedXrayQuic().lineSequence().joinToString("") { it.trim() }}",
            emptyMap(),
            subscription,
        )

        assertEquals("vless://existing", result.text)
        assertEquals(1, result.xraySkipped)
    }

    @Test
    fun rejectsSubscriptionWhenEveryXrayProfileIsUnsupported() {
        val subscription = Subscription(
            name = "test",
            source = "https://example.com/sub",
            decodeBase64 = false,
            xrayToSingBox = true,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            SubscriptionProcessor.transformWithStats(
                subscription.source,
                unsupportedXrayQuic(),
                emptyMap(),
                subscription,
            )
        }

        assertTrue(error.message.orEmpty().contains("Ни один Xray-профиль"))
    }

    private fun unsupportedXrayQuic(): String = """
        {
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{
                "address": "server.example.com",
                "port": 443,
                "users": [{
                  "id": "11111111-1111-4111-8111-111111111111",
                  "encryption": "none"
                }]
              }]
            },
            "streamSettings": {
              "network": "quic",
              "security": "none",
              "quicSettings": {}
            }
          }]
        }
    """.trimIndent()
}
