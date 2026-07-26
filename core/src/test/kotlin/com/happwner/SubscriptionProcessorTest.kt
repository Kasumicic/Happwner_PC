package com.happwner

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
