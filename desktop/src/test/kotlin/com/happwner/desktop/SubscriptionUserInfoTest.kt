package com.happwner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionUserInfoTest {
    @Test
    fun parsesStandardHeaderCaseInsensitively() {
        val result = SubscriptionUserInfoParser.parse(
            mapOf(
                "subscription-userINFO" to listOf(
                    "upload=1024; download=2048; total=10240; expire=1800000000",
                ),
            ),
        )

        assertEquals(1024L, result?.uploadBytes)
        assertEquals(2048L, result?.downloadBytes)
        assertEquals(3072L, result?.usedBytes)
        assertEquals(10240L, result?.totalBytes)
        assertEquals(7168L, result?.remainingBytes)
        assertEquals(1_800_000_000L, result?.expireEpochSeconds)
    }

    @Test
    fun ignoresUnknownAndMalformedValues() {
        val result = SubscriptionUserInfoParser.parse(
            mapOf(
                "Subscription-Userinfo" to listOf(
                    "upload=-1; download=broken; total=4096; expire=0; plan=home",
                ),
            ),
        )

        assertNull(result?.uploadBytes)
        assertNull(result?.downloadBytes)
        assertNull(result?.usedBytes)
        assertEquals(4096L, result?.totalBytes)
        assertEquals(0L, result?.expireEpochSeconds)
    }

    @Test
    fun returnsNullWhenHeaderIsMissingOrUnsupported() {
        assertNull(SubscriptionUserInfoParser.parse(emptyMap()))
        assertNull(SubscriptionUserInfoParser.parse(mapOf("Subscription-Userinfo" to listOf("plan=premium"))))
    }

    @Test
    fun clampsRemainingTrafficAtZero() {
        val info = SubscriptionUserInfo(uploadBytes = 80, downloadBytes = 40, totalBytes = 100)

        assertEquals(120L, info.usedBytes)
        assertEquals(0L, info.remainingBytes)
    }
}
