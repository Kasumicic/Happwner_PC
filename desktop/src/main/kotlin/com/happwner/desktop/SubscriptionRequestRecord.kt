package com.happwner.desktop

data class SubscriptionRequestRecord(
    val completedAtMillis: Long,
    val statusCode: Int? = null,
    val sizeBytes: Int? = null,
    val profileCount: Int? = null,
    val error: String? = null,
) {
    companion object {
        fun success(
            response: FetchedSubscription,
            completedAtMillis: Long = System.currentTimeMillis(),
        ): SubscriptionRequestRecord {
            val inspection = SubscriptionInspector.inspect(response.body)
            return SubscriptionRequestRecord(
                completedAtMillis = completedAtMillis,
                statusCode = response.statusCode,
                sizeBytes = response.body.size,
                profileCount = inspection.profileCount,
            )
        }

        fun failure(
            message: String,
            completedAtMillis: Long = System.currentTimeMillis(),
        ) = SubscriptionRequestRecord(
            completedAtMillis = completedAtMillis,
            error = message,
        )
    }
}
