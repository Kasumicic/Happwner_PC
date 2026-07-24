package com.happwner.compat

import java.util.Base64 as JvmBase64

/** Small JVM compatibility layer used while the original crypto code is being ported. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    fun decode(value: String, flags: Int): ByteArray {
        val normalized = value.filterNot(Char::isWhitespace)
        return if (flags and URL_SAFE != 0) {
            JvmBase64.getUrlDecoder().decode(normalized)
        } else {
            JvmBase64.getDecoder().decode(normalized)
        }
    }

    fun encodeToString(value: ByteArray, flags: Int): String {
        var encoder = when {
            flags and URL_SAFE != 0 -> JvmBase64.getUrlEncoder()
            flags and NO_WRAP == 0 -> JvmBase64.getMimeEncoder(76, if (flags and CRLF != 0) "\r\n".toByteArray() else "\n".toByteArray())
            else -> JvmBase64.getEncoder()
        }
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        return encoder.encodeToString(value)
    }
}
