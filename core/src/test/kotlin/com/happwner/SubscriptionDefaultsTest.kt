package com.happwner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionDefaultsTest {
    @Test
    fun newSubscriptionsDecodeBase64ByDefault() {
        val subscription = Subscription(
            name = "Default",
            source = "https://example.com/subscription",
        )

        assertTrue(subscription.decodeBase64)
        assertEquals("Happ/3.26.1", subscription.userAgent)
    }
}
