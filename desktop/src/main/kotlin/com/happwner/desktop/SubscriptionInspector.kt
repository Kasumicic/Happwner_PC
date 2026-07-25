package com.happwner.desktop

data class SubscriptionInspection(
    val profileCount: Int,
    val protocols: Map<String, Int>,
    val preview: String,
)

object SubscriptionInspector {
    private val proxySchemes = setOf(
        "vless", "vmess", "trojan", "ss", "ssr", "hysteria", "hysteria2",
        "hy2", "tuic", "socks", "socks5", "http", "wireguard", "anytls",
    )
    private val linkPattern = Regex("""(?im)^\s*([a-z][a-z0-9+.-]*)://\S+""")
    private val jsonTypePattern = Regex(
        """"(?:type|protocol)"\s*:\s*"(vless|vmess|trojan|shadowsocks|ssr|hysteria2?|hy2|tuic|socks5?|http|wireguard|anytls)"""",
        RegexOption.IGNORE_CASE,
    )
    private val yamlProfilePattern = Regex("""(?m)^\s*-\s*name\s*:""", RegexOption.IGNORE_CASE)
    private val yamlTypePattern = Regex("""(?im)^\s*type\s*:\s*([a-z0-9_-]+)\s*$""")

    fun inspect(body: ByteArray): SubscriptionInspection {
        val text = body.toString(Charsets.UTF_8).trim()
        val linkProtocols = linkPattern.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .filter { it in proxySchemes }
            .toList()

        val protocols = when {
            linkProtocols.isNotEmpty() -> linkProtocols
            text.startsWith("{") || text.startsWith("[") -> jsonTypePattern.findAll(text)
                .map { normalizeProtocol(it.groupValues[1]) }
                .toList()
            Regex("""(?im)^\s*proxies\s*:""").containsMatchIn(text) -> {
                val types = yamlTypePattern.findAll(text).map { normalizeProtocol(it.groupValues[1]) }.toList()
                val count = yamlProfilePattern.findAll(text).count()
                if (types.isNotEmpty()) types else List(count) { "clash" }
            }
            else -> emptyList()
        }

        return SubscriptionInspection(
            profileCount = protocols.size,
            protocols = protocols.groupingBy { it }.eachCount().toSortedMap(),
            preview = text.take(PREVIEW_CHARS),
        )
    }

    private fun normalizeProtocol(protocol: String): String = when (protocol.lowercase()) {
        "shadowsocks" -> "ss"
        else -> protocol.lowercase()
    }

    private const val PREVIEW_CHARS = 800
}
