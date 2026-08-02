package com.happwner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubscriptionClipboardTest {
    @Test
    fun copiesTheCompleteProcessedSubscription() {
        val profiles = "vless://first\ntrojan://second"
        var clipboardText: String? = null

        val result = SubscriptionClipboard.copy(
            FetchedSubscription(profiles.toByteArray(), emptyMap(), 200),
        ) {
            clipboardText = it
            true
        }

        val success = assertIs<ProfileCopyState.Success>(result)
        assertEquals(2, success.profileCount)
        assertEquals(profiles, clipboardText)
    }

    @Test
    fun doesNotOverwriteClipboardWhenNoProfilesWereFound() {
        var writes = 0

        val result = SubscriptionClipboard.copy(
            FetchedSubscription(
                body = "provider maintenance".toByteArray(),
                headers = emptyMap(),
                statusCode = 200,
                uriPreserved = 1,
            ),
        ) {
            writes++
            true
        }

        val noProfiles = assertIs<ProfileCopyState.NoProfiles>(result)
        assertEquals(1, noProfiles.uriPreserved)
        assertEquals(0, writes)
    }

    @Test
    fun reportsClipboardFailure() {
        val result = SubscriptionClipboard.copy(
            FetchedSubscription("vless://profile".toByteArray(), emptyMap(), 200),
        ) { false }

        assertIs<ProfileCopyState.ClipboardError>(result)
    }

    @Test
    fun keepsConversionWarningsAfterCopyingProfiles() {
        val result = SubscriptionClipboard.copy(
            FetchedSubscription(
                body = "vless://profile".toByteArray(),
                headers = emptyMap(),
                statusCode = 200,
                xraySkipped = 2,
                uriPreserved = 1,
            ),
        ) { true }

        val success = assertIs<ProfileCopyState.Success>(result)
        assertEquals(2, success.xraySkipped)
        assertEquals(1, success.uriPreserved)
    }
}
