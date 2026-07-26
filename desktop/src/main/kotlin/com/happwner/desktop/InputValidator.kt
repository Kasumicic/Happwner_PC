package com.happwner.desktop

import com.happwner.SourceResolver
import java.net.URI

enum class SourceValidationIssue {
    EMPTY,
    INVALID,
}

object InputValidator {
    fun sourceIssue(source: String): SourceValidationIssue? {
        if (source.isBlank()) return SourceValidationIssue.EMPTY
        return when (val result = SourceResolver.resolve(source)) {
            is SourceResolver.Result.Error -> SourceValidationIssue.INVALID
            is SourceResolver.Result.Static -> null
            is SourceResolver.Result.Success -> {
                val valid = runCatching {
                    val uri = URI(result.url)
                    uri.scheme?.lowercase() in setOf("http", "https") &&
                        !uri.host.isNullOrBlank() &&
                        (uri.port == -1 || uri.port in 1..65535)
                }.getOrDefault(false)
                if (valid) null else SourceValidationIssue.INVALID
            }
        }
    }

    fun validPort(port: String): Int? =
        port.toIntOrNull()?.takeIf { it in 1024..65535 }
}
