package com.happwner.desktop

import com.happwner.Subscription

enum class SubscriptionRequestOrigin {
    CLIENT,
    MANUAL_CHECK,
    PROFILE_COPY,
}

data class SubscriptionRequestRecord(
    val completedAtMillis: Long,
    val statusCode: Int? = null,
    val servedStatusCode: Int? = null,
    val sizeBytes: Int? = null,
    val profileCount: Int? = null,
    val protocols: Map<String, Int> = emptyMap(),
    val durationMillis: Long? = null,
    val clientAddress: String? = null,
    val origin: SubscriptionRequestOrigin = SubscriptionRequestOrigin.CLIENT,
    val transformations: List<String> = emptyList(),
    val userInfo: SubscriptionUserInfo? = null,
    val error: String? = null,
) {
    companion object {
        fun success(
            response: FetchedSubscription,
            subscription: Subscription? = null,
            servedStatusCode: Int = 200,
            durationMillis: Long? = null,
            clientAddress: String? = null,
            origin: SubscriptionRequestOrigin = SubscriptionRequestOrigin.CLIENT,
            completedAtMillis: Long = System.currentTimeMillis(),
        ): SubscriptionRequestRecord {
            val inspection = SubscriptionInspector.inspect(response.body)
            return SubscriptionRequestRecord(
                completedAtMillis = completedAtMillis,
                statusCode = response.statusCode,
                servedStatusCode = servedStatusCode,
                sizeBytes = response.body.size,
                profileCount = inspection.profileCount,
                protocols = inspection.protocols,
                durationMillis = durationMillis,
                clientAddress = clientAddress,
                origin = origin,
                transformations = subscription?.enabledTransformations().orEmpty(),
                userInfo = response.userInfo,
            )
        }

        fun failure(
            message: String,
            subscription: Subscription? = null,
            servedStatusCode: Int? = null,
            durationMillis: Long? = null,
            clientAddress: String? = null,
            origin: SubscriptionRequestOrigin = SubscriptionRequestOrigin.CLIENT,
            completedAtMillis: Long = System.currentTimeMillis(),
        ) = SubscriptionRequestRecord(
            completedAtMillis = completedAtMillis,
            servedStatusCode = servedStatusCode,
            durationMillis = durationMillis,
            clientAddress = clientAddress,
            origin = origin,
            transformations = subscription?.enabledTransformations().orEmpty(),
            error = DiagnosticSanitizer.errorMessage(message, subscription),
        )
    }
}

data class SubscriptionActivity(
    val subscriptionId: String,
    val subscriptionName: String,
    val request: SubscriptionRequestRecord,
)

internal fun Subscription.enabledTransformations(): List<String> = buildList {
    if (decodeBase64) add("Base64")
    if (jsonToUri) add("JSON→URI")
    if (xrayToSingBox) add("Xray→sing-box")
}
