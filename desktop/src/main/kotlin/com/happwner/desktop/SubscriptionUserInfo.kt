package com.happwner.desktop

data class SubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireEpochSeconds: Long? = null,
) {
    val usedBytes: Long?
        get() {
            if (uploadBytes == null && downloadBytes == null) return null
            return saturatingAdd(uploadBytes ?: 0, downloadBytes ?: 0)
        }

    val remainingBytes: Long?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.let { total -> usedBytes?.let { used -> (total - used).coerceAtLeast(0) } }

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}

object SubscriptionUserInfoParser {
    fun parse(headers: Map<String, List<String>>): SubscriptionUserInfo? {
        val fields = headers.entries
            .filter { it.key.equals(HEADER_NAME, ignoreCase = true) }
            .flatMap { it.value }
            .flatMap { value -> value.split(';', ',') }
            .mapNotNull(::parseField)
            .toMap()

        if (fields.isEmpty()) return null
        return SubscriptionUserInfo(
            uploadBytes = fields["upload"],
            downloadBytes = fields["download"],
            totalBytes = fields["total"],
            expireEpochSeconds = fields["expire"],
        )
    }

    private fun parseField(raw: String): Pair<String, Long>? {
        val separator = raw.indexOf('=')
        if (separator < 0) return null
        val key = raw.substring(0, separator).trim().lowercase()
        if (key !in SUPPORTED_FIELDS) return null
        val value = raw.substring(separator + 1).trim().toLongOrNull()
            ?.takeIf { it >= 0 }
            ?: return null
        return key to value
    }

    private const val HEADER_NAME = "Subscription-Userinfo"
    private val SUPPORTED_FIELDS = setOf("upload", "download", "total", "expire")
}
