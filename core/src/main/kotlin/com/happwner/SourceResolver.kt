package com.happwner

object SourceResolver {
    sealed interface Result {
        data class Success(val url: String) : Result
        data class Static(val content: String) : Result
        data class Error(val message: String) : Result
    }

    fun resolve(input: String): Result {
        var current = input.trim()
        var wasWrapped = false
        repeat(8) {
            if (current.startsWith("http://", true) || current.startsWith("https://", true)) {
                val embedded = HappCrypto.extractEmbeddedHappLink(current)
                    ?: V2RayTunCrypto.extractEmbeddedV2RayLink(current)
                    ?: IncyLinks.extractEmbeddedIncyLink(current)
                if (embedded == null) return Result.Success(current)
                current = embedded
                wasWrapped = true
                return@repeat
            }

            HappCrypto.stripAddPrefix(current)?.let {
                current = it
                wasWrapped = true
                return@repeat
            }
            when (val decrypted = HappCrypto.decryptHappLink(current)) {
                is HappCrypto.HappLinkResult.Decrypted -> {
                    current = decrypted.plaintext.trim()
                    wasWrapped = true
                    return@repeat
                }
                is HappCrypto.HappLinkResult.Error -> return Result.Error("${decrypted.mode}: ${decrypted.reason}")
                HappCrypto.HappLinkResult.NotHappLink -> Unit
            }

            V2RayTunCrypto.stripImportPrefix(current)?.let {
                current = it
                wasWrapped = true
                return@repeat
            }
            when (val decrypted = V2RayTunCrypto.decryptCryptLink(current)) {
                is V2RayTunCrypto.Result.Decrypted -> {
                    current = decrypted.plaintext.trim()
                    wasWrapped = true
                    return@repeat
                }
                is V2RayTunCrypto.Result.Error -> return Result.Error(decrypted.reason)
                V2RayTunCrypto.Result.NotCryptLink -> Unit
            }

            IncyLinks.stripIncyPrefix(current)?.let {
                current = it
                wasWrapped = true
                return@repeat
            }
            if (wasWrapped && current.isNotBlank()) return Result.Static(current)
            return Result.Error("Ссылка не содержит HTTP(S)-подписку")
        }
        return Result.Error("Слишком много вложенных ссылок")
    }
}
