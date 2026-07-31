package com.happwner.desktop

object SubscriptionClipboard {
    fun copy(
        response: FetchedSubscription,
        clipboardWriter: (String) -> Boolean,
    ): ProfileCopyState {
        val inspection = SubscriptionInspector.inspect(response.body)
        if (inspection.profileCount == 0) return ProfileCopyState.NoProfiles
        return if (clipboardWriter(response.body.toString(Charsets.UTF_8))) {
            ProfileCopyState.Success(
                profileCount = inspection.profileCount,
                sizeBytes = response.body.size,
            )
        } else {
            ProfileCopyState.ClipboardError
        }
    }
}
