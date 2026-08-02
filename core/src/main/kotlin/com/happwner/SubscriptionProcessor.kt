package com.happwner

object SubscriptionProcessor {
    data class TransformResult(
        val text: String,
        val xraySkipped: Int,
        val uriPreserved: Int,
    ) {
        val hasWarnings: Boolean get() = xraySkipped > 0 || uriPreserved > 0
    }

    fun transform(subscriptionUrl: String, body: String, headers: Map<String, List<String>>, subscription: Subscription): String {
        return transformWithStats(subscriptionUrl, body, headers, subscription).text
    }

    fun transformWithStats(
        subscriptionUrl: String,
        body: String,
        headers: Map<String, List<String>>,
        subscription: Subscription,
    ): TransformResult {
        val plaintext = when (val result = HappCrypto.process(subscriptionUrl, body, headers)) {
            is HappCrypto.Result.Success -> result.plaintext
            is HappCrypto.Result.Failed -> throw IllegalArgumentException(
                "Не удалось расшифровать ${result.keyName}: ${result.reason}",
            )
            HappCrypto.Result.NotEncrypted -> body
        }
        val converted = LinkConverter.convertWithStats(
            plaintext,
            subscription.jsonToUri,
            subscription.decodeBase64,
            subscription.xrayToSingBox,
        )
        if (converted.text.isBlank() && converted.xraySkipped > 0) {
            throw IllegalArgumentException(
                "Ни один Xray-профиль не удалось безопасно преобразовать в sing-box",
            )
        }
        return TransformResult(
            text = converted.text,
            xraySkipped = converted.xraySkipped,
            uriPreserved = converted.uriPreserved,
        )
    }
}
