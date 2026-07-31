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
            FetchedSubscription("provider maintenance".toByteArray(), emptyMap(), 200),
        ) {
            writes++
            true
        }

        assertIs<ProfileCopyState.NoProfiles>(result)
        assertEquals(0, writes)
    }

    @Test
    fun reportsClipboardFailure() {
        val result = SubscriptionClipboard.copy(
            FetchedSubscription("vless://profile".toByteArray(), emptyMap(), 200),
        ) { false }

        assertIs<ProfileCopyState.ClipboardError>(result)
    }
}
