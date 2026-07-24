package com.happwner

object SubscriptionProcessor {
    fun transform(subscriptionUrl: String, body: String, headers: Map<String, List<String>>, subscription: Subscription): String {
        val plaintext = when (val result = HappCrypto.process(subscriptionUrl, body, headers)) {
            is HappCrypto.Result.Success -> result.plaintext
            is HappCrypto.Result.Failed -> throw IllegalArgumentException(
                "Не удалось расшифровать ${result.keyName}: ${result.reason}",
            )
            HappCrypto.Result.NotEncrypted -> body
        }
        return LinkConverter.convert(
            plaintext,
            subscription.jsonToUri,
            subscription.decodeBase64,
            subscription.xrayToSingBox,
        )
    }
}
