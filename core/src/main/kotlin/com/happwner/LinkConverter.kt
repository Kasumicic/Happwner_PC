package com.happwner

import com.happwner.compat.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.IDN
import java.net.URLEncoder

object LinkConverter {
    data class ConversionStats(val text: String, val xraySkipped: Int)

    fun convert(
        input: String,
        jsonToUri: Boolean = true,
        tryBase64: Boolean = true,
        xrayToSb: Boolean = false
    ): String {
        return convertWithStats(input, jsonToUri, tryBase64, xrayToSb).text
    }

    // Main converter. Pass order: base64 -> xray-to-sing-box -> JSON-to-URI
    fun convertWithStats(
        input: String,
        jsonToUri: Boolean = true,
        tryBase64: Boolean = true,
        xrayToSb: Boolean = false
    ): ConversionStats {
        if (!jsonToUri && !tryBase64 && !xrayToSb) return ConversionStats(input.trim(), 0)

        val trimmed = input.trim()
        val compact = isCompactJson(trimmed)

        // If the whole body is base64, decode it and recurse
        if (tryBase64 || xrayToSb) {
            val b64 = tryDecodeBase64WithFlag(input)
            if (b64 != null) {
                val inner = convertWithStats(b64.decoded, jsonToUri, tryBase64, xrayToSb)
                return if (tryBase64) {
                    inner
                } else {
                    ConversionStats(
                        encodeBase64Like(inner.text, b64),
                        inner.xraySkipped
                    )
                }
            }
        }

        // xray-to-sing-box only: merge every config into a single one (mergeUnified)
        if (xrayToSb && !jsonToUri) {
            val merged = convertXrayToSingbox(input, trimmed, compact)
            if (merged != null) return merged
        }

        // Both modes: first drop unsupported xray outbounds, then run JSON-to-URI
        if (xrayToSb && jsonToUri) {
            val filtered = preFilterUnsupportedXray(input)
            val inner = convertWithStats(filtered.text, jsonToUri = true, tryBase64 = tryBase64, xrayToSb = false)
            return ConversionStats(inner.text, inner.xraySkipped + filtered.skipped)
        }

        // Whole body is a single xray config -> sing-box
        if (xrayToSb && trimmed.startsWith("{") && isWholeJsonValue(trimmed)) {
            val r = SingBoxConverter.convert(trimmed, "")
            if (r is SingBoxConverter.Result.Ok) {
                return ConversionStats(formatJson(r.config, compact), 0)
            }
        }

        // Whole body is an xray array -> sing-box
        if (xrayToSb && trimmed.startsWith("[") && isWholeJsonValue(trimmed)) {
            val arr = tryConvertXrayArray(trimmed, compact)
            if (arr != null) return ConversionStats(arr.text, arr.skipped)
        }

        // JSON-to-URI must handle a complete pretty-printed JSON document as one value.
        // Walking it line by line would leave multiline provider responses unchanged.
        if (jsonToUri && (trimmed.startsWith("{") || trimmed.startsWith("[")) && isWholeJsonValue(trimmed)) {
            val converted = convertJsonValueToLinks(trimmed)
            if (converted != null) return ConversionStats(converted, 0)
        }

        val res = StringBuilder()
        var skipped = 0
        // Otherwise walk line by line
        input.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val lineCompact = isCompactJson(t)

            if (tryBase64 || xrayToSb) {
                val b64 = tryDecodeBase64WithFlag(t)
                if (b64 != null) {
                    val inner = convertWithStats(b64.decoded, jsonToUri, tryBase64, xrayToSb)
                    val output = if (tryBase64) {
                        inner.text
                    } else {
                        encodeBase64Like(inner.text, b64)
                    }
                    res.append(output).append("\n")
                    skipped += inner.xraySkipped
                    return@forEach
                }
            }

            if (xrayToSb && t.startsWith("{") && isWholeJsonValue(t)) {
                when (val r = SingBoxConverter.convert(t, "")) {
                    is SingBoxConverter.Result.Ok -> {
                        res.append(formatJson(r.config, lineCompact)).append("\n")
                        return@forEach
                    }
                    SingBoxConverter.Result.Unsupported -> {
                        skipped++
                        return@forEach
                    }
                    SingBoxConverter.Result.NotXray -> {}
                }
            }

            if (xrayToSb && t.startsWith("[") && isWholeJsonValue(t)) {
                val arr = tryConvertXrayArray(t, lineCompact)
                if (arr != null) {
                    res.append(arr.text).append("\n")
                    skipped += arr.skipped
                    return@forEach
                }
            }

            // A JSON outbound on this line -> proxy link
            if (jsonToUri && (t.startsWith("{") || t.startsWith("[")) && isWholeJsonValue(t)) {
                try {
                    if (t.startsWith("[")) {
                        val arr = JSONArray(t)
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i)
                            val piece: String? = if (obj != null) {
                                processJson(obj) ?: obj.toString()
                            } else {
                                val raw = arr.opt(i)
                                if (raw == null || raw === JSONObject.NULL) null
                                else raw.toString().trim().takeIf { it.isNotEmpty() }
                            }
                            if (piece != null) res.append(piece).append("\n")
                        }
                    } else {
                        val obj = JSONObject(t)
                        val converted = processJson(obj)
                        if (converted != null) res.append(converted).append("\n")
                        else res.append(t).append("\n")
                    }
                    return@forEach
                } catch (_: Throwable) {}
            }

            res.append(t).append("\n")
        }
        return ConversionStats(res.toString().trim(), skipped)
    }

    private fun convertJsonValueToLinks(text: String): String? = try {
        if (text.startsWith("[")) {
            val array = JSONArray(text)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                    if (item != null) {
                        add(processJson(item) ?: item.toString())
                    } else {
                        val raw = array.opt(index)
                        if (raw != null && raw !== JSONObject.NULL && raw.toString().isNotBlank()) add(raw.toString())
                    }
                }
            }.joinToString("\n").takeIf(String::isNotBlank)
        } else {
            val obj = JSONObject(text)
            processJson(obj) ?: text
        }
    } catch (_: Exception) {
        null
    }

    // Single-line JSON? (no newline within the first 1KB)
    private fun isCompactJson(s: String): Boolean {
        val limit = minOf(s.length, 1024)
        for (i in 0 until limit) {
            val c = s[i]
            if (c == '\n' || c == '\r') return false
        }
        return true
    }

    // true if the whole string is one valid JSON value (no trailing junk)
    private fun isWholeJsonValue(s: String): Boolean {
        return try {
            val t = JSONTokener(s)
            t.nextValue()
            t.nextClean().code == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun formatJson(obj: JSONObject, compact: Boolean): String =
        if (compact) obj.toString() else obj.toString(2)

    private fun formatJsonArray(arr: JSONArray, compact: Boolean): String =
        if (compact) arr.toString() else arr.toString(2)

    private data class ArrayConvResult(val text: String, val skipped: Int)

    private data class FilterResult(val text: String, val skipped: Int)

    // Normalize vless flow to its sing-box-valid form (xtls-rprx-vision-udp443 -> xtls-rprx-vision)
    private fun normalizeConfigFlowsInPlace(cfg: JSONObject): Boolean {
        val outs = cfg.optJSONArray("outbounds") ?: return false
        var changed = false
        for (i in 0 until outs.length()) {
            val ob = outs.optJSONObject(i) ?: continue
            if (ob.optString("protocol") != "vless") continue
            val vnext = ob.optJSONObject("settings")?.optJSONArray("vnext") ?: continue
            for (j in 0 until vnext.length()) {
                val users = vnext.optJSONObject(j)?.optJSONArray("users") ?: continue
                for (k in 0 until users.length()) {
                    val u = users.optJSONObject(k) ?: continue
                    val flow = u.optString("flow", "")
                    if (flow.isNotEmpty()) {
                        val norm = SingBoxConverter.normalizeFlow(flow)
                        if (norm != flow) { u.put("flow", norm); changed = true }
                    }
                }
            }
        }
        return changed
    }

    // Keep one xray config/array, dropping unsupported outbounds
    private fun preFilterUnsupportedXrayOne(t: String): FilterResult? {
        if (t.isEmpty()) return null
        if (!isWholeJsonValue(t)) return null
        if (t.startsWith("{")) {
            return when (SingBoxConverter.convertToOutbounds(t, "")) {
                is SingBoxConverter.OutboundsResult.Ok -> {
                    val cfg = try { JSONObject(t) } catch (_: Throwable) { null }
                    if (cfg != null && normalizeConfigFlowsInPlace(cfg)) FilterResult(cfg.toString(), 0)
                    else FilterResult(t, 0)
                }
                SingBoxConverter.OutboundsResult.Unsupported -> FilterResult("", 1)
                SingBoxConverter.OutboundsResult.NotXray -> null
            }
        }
        if (t.startsWith("[")) {
            val arr = try { JSONArray(t) } catch (_: Throwable) { return null }
            val out = JSONArray()
            var anyXray = false
            var skipped = 0
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj == null) {
                    val raw = arr.opt(i)
                    if (raw != null && raw !== JSONObject.NULL) out.put(raw)
                    continue
                }
                when (SingBoxConverter.convertToOutbounds(obj.toString(), "")) {
                    is SingBoxConverter.OutboundsResult.Ok -> {
                        normalizeConfigFlowsInPlace(obj)
                        out.put(obj)
                        anyXray = true
                    }
                    SingBoxConverter.OutboundsResult.Unsupported -> {
                        skipped++
                        anyXray = true
                    }
                    SingBoxConverter.OutboundsResult.NotXray -> out.put(obj)
                }
            }
            if (!anyXray) return null
            return FilterResult(formatJsonArray(out, isCompactJson(t)), skipped)
        }
        return null
    }

    // Drop unsupported xray configs/outbounds and count the skipped ones
    private fun preFilterUnsupportedXray(input: String): FilterResult {
        val trimmed = input.trim()
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && isWholeJsonValue(trimmed)) {
            val single = preFilterUnsupportedXrayOne(trimmed)
            if (single != null) return single
        }
        val res = StringBuilder()
        var totalSkipped = 0
        var anyFiltered = false
        for (line in input.lines()) {
            val tt = line.trim()
            if (tt.isEmpty()) continue
            val one = preFilterUnsupportedXrayOne(tt)
            if (one != null) {
                anyFiltered = true
                if (one.text.isNotEmpty()) res.append(one.text).append("\n")
                totalSkipped += one.skipped
            } else {
                res.append(tt).append("\n")
            }
        }
        if (!anyFiltered) return FilterResult(input, 0)
        return FilterResult(res.toString().trimEnd('\n'), totalSkipped)
    }

    // Convert each xray config inside an array to sing-box
    private fun tryConvertXrayArray(text: String, compact: Boolean): ArrayConvResult? {
        val arr = try { JSONArray(text) } catch (_: Throwable) { return null }
        if (arr.length() == 0) return null

        var anyXray = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val outs = obj.optJSONArray("outbounds") ?: continue
            for (j in 0 until outs.length()) {
                val o = outs.optJSONObject(j) ?: continue
                if (o.has("protocol")) { anyXray = true; break }
            }
            if (anyXray) break
        }
        if (!anyXray) return null

        val outArr = JSONArray()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj == null) {
                val raw = arr.opt(i)
                if (raw != null && raw !== JSONObject.NULL) outArr.put(raw)
                continue
            }
            when (val r = SingBoxConverter.convert(obj.toString(), "")) {
                is SingBoxConverter.Result.Ok -> outArr.put(r.config)
                SingBoxConverter.Result.Unsupported -> skipped++
                SingBoxConverter.Result.NotXray -> outArr.put(obj)
            }
        }
        return ArrayConvResult(formatJsonArray(outArr, compact), skipped)
    }

    // Merge xray configs into one sing-box; pass any other lines through unchanged
    private fun convertXrayToSingbox(input: String, trimmed: String, compact: Boolean): ConversionStats? {
        val configs = mutableListOf<JSONObject>()
        var skipped = 0
        var hadXray = false
        val passthroughLines = mutableListOf<String>()

        fun ingestObject(s: String): Boolean {
            return when (val r = SingBoxConverter.convert(s, "")) {
                is SingBoxConverter.Result.Ok -> {
                    configs.add(r.config); hadXray = true; true
                }
                SingBoxConverter.Result.Unsupported -> {
                    skipped++; hadXray = true; true
                }
                SingBoxConverter.Result.NotXray -> false
            }
        }

        fun ingestArray(s: String): Boolean {
            val arr = try { JSONArray(s) } catch (_: Throwable) { return false }
            var any = false
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                when (val r = SingBoxConverter.convert(obj.toString(), "")) {
                    is SingBoxConverter.Result.Ok -> {
                        configs.add(r.config); any = true
                    }
                    SingBoxConverter.Result.Unsupported -> {
                        skipped++; any = true
                    }
                    SingBoxConverter.Result.NotXray -> {}
                }
            }
            if (any) hadXray = true
            return any
        }

        val consumedWhole = when {
            trimmed.startsWith("{") && isWholeJsonValue(trimmed) -> ingestObject(trimmed)
            trimmed.startsWith("[") && isWholeJsonValue(trimmed) -> ingestArray(trimmed)
            else -> false
        }

        if (!consumedWhole) {
            for (line in input.lines()) {
                val t = line.trim()
                if (t.isEmpty()) continue
                val consumed = when {
                    t.startsWith("{") && isWholeJsonValue(t) -> ingestObject(t)
                    t.startsWith("[") && isWholeJsonValue(t) -> ingestArray(t)
                    else -> false
                }
                if (!consumed) passthroughLines.add(t)
            }
        }

        if (!hadXray) return null
        if (configs.isEmpty() && passthroughLines.isEmpty()) return null

        val builder = StringBuilder()
        if (configs.isNotEmpty()) {
            val merged = SingBoxConverter.mergeUnified(configs)
            if (merged != null) builder.append(formatJson(merged, compact))
        }
        for (l in passthroughLines) {
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append(l)
        }

        return ConversionStats(builder.toString(), skipped)
    }

    private val PROXY_SCHEMES = arrayOf(
        "vless://", "vmess://", "trojan://", "ss://", "ssr://",
        "hysteria://", "hysteria2://", "hy2://", "tuic://", "socks://",
        "http://", "https://", "happ://"
    )

    private data class Base64Result(
        val decoded: String,
        val flag: Int,
        val hadNewlines: Boolean,
        val hadCrlf: Boolean,
        val hadPadding: Boolean,
        val hadTrailingNewline: Boolean,
        val hadTrailingCrlf: Boolean
    )

    // Try to decode as Base64 (remember flag/newlines/padding so we can re-encode the same way)
    private fun tryDecodeBase64WithFlag(input: String): Base64Result? {
        if (input.length < 10) return null
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return null

        var hasStd = false
        var hasUrl = false
        var hadNewlines = false
        var hadPadding = false
        // Scan the alphabet: std vs url-safe, padding, newlines; bail on anything non-base64
        for (c in cleaned) {
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' || c == '\t' -> {}
                c == '=' -> hadPadding = true
                c == '\r' || c == '\n' -> { hadNewlines = true }
                c == '+' || c == '/' -> hasStd = true
                c == '-' || c == '_' -> hasUrl = true
                else -> return null
            }
        }
        if (hasStd && hasUrl) return null
        val rstripped = input.trimEnd(' ', '\t')
        val hadTrailingCrlf = rstripped.endsWith("\r\n")
        val hadTrailingNewline = hadTrailingCrlf || rstripped.endsWith("\n") || rstripped.endsWith("\r")
        val hadCrlf = (hadNewlines && cleaned.contains("\r\n")) || hadTrailingCrlf

        val flag = if (hasUrl) Base64.URL_SAFE else Base64.DEFAULT

        // Decode, then accept only if it's real text that looks like configs/links
        return try {
            val data = Base64.decode(cleaned, flag)
            if (data.isEmpty()) return null
            // Strict UTF-8 decode: binary/garbage is rejected (throws CharacterCodingException), valid text passes
            val decodedRaw = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data))
                    .toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                return null
            }
            // Control characters (except \t \n \r) and DEL indicate binary, so reject them
            for (ch in decodedRaw) {
                val cc = ch.code
                if (cc == 0x7f || (cc < 0x20 && cc != 0x09 && cc != 0x0a && cc != 0x0d)) return null
            }
            val decoded = decodedRaw.trimStart()
            val firstLine = decoded.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart() ?: return null
            val looksLikeJson = firstLine.startsWith("{") || firstLine.startsWith("[")
            val looksLikeProxyList = PROXY_SCHEMES.any { firstLine.startsWith(it, ignoreCase = true) }
            if (looksLikeJson || looksLikeProxyList) {
                Base64Result(decoded, flag, hadNewlines, hadCrlf, hadPadding, hadTrailingNewline, hadTrailingCrlf)
            } else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // Re-pack into base64 in exactly the same shape as the input
    private fun encodeBase64Like(text: String, b64: Base64Result): String {
        var flags = b64.flag
        if (!b64.hadNewlines) {
            flags = flags or Base64.NO_WRAP
        } else if (b64.hadCrlf) {
            flags = flags or Base64.CRLF
        }
        if (!b64.hadPadding) {
            flags = flags or Base64.NO_PADDING
        }
        val raw = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), flags)
        val body = raw.trimEnd('\n', '\r')
        return when {
            b64.hadTrailingCrlf -> "$body\r\n"
            b64.hadTrailingNewline -> "$body\n"
            else -> body
        }
    }

    // JSON outbound to a link (vless/vmess/ss/trojan/hysteria2/tuic)
    private fun processJson(root: JSONObject): String? {
        if (isShadowsocks(root)) {
            return buildOutboundLinks(root, root.optString("remarks", "")).joinToString("\n").takeIf(String::isNotEmpty)
        }

        val protocol = root.optString("protocol", root.optString("type")).lowercase()
        if (protocol in CONVERTIBLE_PROTOCOLS) {
            return buildOutboundLinks(root, root.optString("tag", root.optString("remarks", "")))
                .joinToString("\n")
                .takeIf(String::isNotEmpty)
        }

        val obs = root.optJSONArray("outbounds")
        if (obs != null) {
            val rem = root.optString("remarks", "")
            val converted = mutableListOf<String>()
            var failedSupportedOutbound = false
            for (i in 0 until obs.length()) {
                val ob = obs.optJSONObject(i) ?: continue
                val links = buildOutboundLinks(ob, rem)
                val protocolName = ob.optString("protocol", ob.optString("type")).lowercase()
                if (links.isEmpty() && (protocolName in PROXY_PROTOCOLS || isShadowsocks(ob))) {
                    failedSupportedOutbound = true
                }
                converted.addAll(links)
            }
            if (failedSupportedOutbound) return null
            return converted.joinToString("\n").takeIf(String::isNotEmpty)
        }
        return null
    }

    private fun buildOutboundLinks(outbound: JSONObject, remarks: String): List<String> {
        val protocol = outbound.optString("protocol", outbound.optString("type")).lowercase()
        val expanded = when (protocol) {
            "vless", "vmess" -> expandLegacyVnext(outbound)
            "shadowsocks", "trojan", "hysteria", "hysteria2" -> expandLegacyServers(outbound)
            else -> listOf(outbound)
        }
        return expanded.mapNotNull { item ->
            when (protocol) {
                "vless" -> buildVless(item, remarks)
                "vmess" -> buildVmess(item, remarks)
                "shadowsocks" -> buildShadowsocks(item, remarks)
                "trojan" -> buildTrojan(item, remarks)
                "hysteria", "hysteria2" -> buildHysteria2(item, remarks)
                "tuic" -> buildTuic(item, remarks)
                else -> if (isShadowsocks(item)) buildShadowsocks(item, remarks) else null
            }
        }
    }

    private fun expandLegacyVnext(outbound: JSONObject): List<JSONObject> {
        val vnext = outbound.optJSONObject("settings")?.optJSONArray("vnext") ?: return listOf(outbound)
        val expanded = mutableListOf<JSONObject>()
        for (serverIndex in 0 until vnext.length()) {
            val server = vnext.optJSONObject(serverIndex) ?: continue
            val users = server.optJSONArray("users")
            if (users == null || users.length() == 0) continue
            for (userIndex in 0 until users.length()) {
                val user = users.optJSONObject(userIndex) ?: continue
                val copy = JSONObject(outbound.toString())
                val serverCopy = JSONObject(server.toString()).put("users", JSONArray().put(JSONObject(user.toString())))
                copy.getJSONObject("settings").put("vnext", JSONArray().put(serverCopy))
                expanded.add(copy)
            }
        }
        return expanded.ifEmpty { listOf(outbound) }
    }

    private fun expandLegacyServers(outbound: JSONObject): List<JSONObject> {
        val servers = outbound.optJSONObject("settings")?.optJSONArray("servers") ?: return listOf(outbound)
        if (servers.length() <= 1) return listOf(outbound)
        return buildList {
            for (index in 0 until servers.length()) {
                val server = servers.optJSONObject(index) ?: continue
                val copy = JSONObject(outbound.toString())
                copy.getJSONObject("settings").put("servers", JSONArray().put(JSONObject(server.toString())))
                add(copy)
            }
        }.ifEmpty { listOf(outbound) }
    }

    private fun isShadowsocks(obj: JSONObject): Boolean {
        if (obj.has("server") && obj.has("server_port") && obj.has("password") && obj.has("method")) return true
        val settings = obj.optJSONObject("settings")
        if (settings != null) {
            if (settings.has("address") && settings.has("port") && settings.has("password") && settings.has("method")) {
                return true
            }
            val servers = settings.optJSONArray("servers")
            if (servers != null && servers.length() > 0) {
                val s = servers.getJSONObject(0)
                if (s.has("address") && s.has("port") && s.has("password") && s.has("method")) return true
            }
        }
        return false
    }

    private val CONVERTIBLE_PROTOCOLS = setOf(
        "vless", "vmess", "shadowsocks", "trojan", "hysteria", "hysteria2", "tuic",
    )
    private val PROXY_PROTOCOLS = CONVERTIBLE_PROTOCOLS + setOf(
        "socks", "http", "wireguard", "ssr", "anytls", "ssh", "naive", "shadowtls",
    )

    // VLESS: vnext/users plus reality/stream params
    private fun buildVless(ob: JSONObject, rem: String): String? {
        return try {
            val settings = ob.optJSONObject("settings")
            val legacyServer = settings?.optJSONArray("vnext")?.optJSONObject(0)
            val legacyUser = legacyServer?.optJSONArray("users")?.optJSONObject(0)
            val singBox = ob.optString("type").equals("vless", ignoreCase = true)
            val address = when {
                singBox -> ob.optString("server", "")
                legacyServer != null -> legacyServer.optString("address", "")
                else -> settings?.optString("address", "").orEmpty()
            }
            val port = when {
                singBox -> ob.optInt("server_port", 0)
                legacyServer != null -> legacyServer.optInt("port", 0)
                else -> settings?.optInt("port", 0) ?: 0
            }
            val id = when {
                singBox -> ob.optString("uuid", "")
                legacyUser != null -> legacyUser.optString("id", "")
                else -> settings?.optString("id", "").orEmpty()
            }
            if (address.isBlank() || port !in 1..65535 || id.isBlank()) return null

            val query = linkedMapOf(
                "encryption" to when {
                    singBox -> ob.optString("encryption", "none")
                    legacyUser != null -> legacyUser.optString("encryption", "none")
                    else -> settings?.optString("encryption", "none") ?: "none"
                },
            )
            putIfNotBlank(
                query,
                "flow",
                when {
                    singBox -> ob.optString("flow", "")
                    legacyUser != null -> legacyUser.optString("flow", "")
                    else -> settings?.optString("flow", "")
                }.let(SingBoxConverter::normalizeFlow),
            )
            if (singBox) {
                appendSingBoxQuery(query, ob)
            } else {
                val stream = ob.optJSONObject("streamSettings")
                appendXraySecurityQuery(query, stream)
                appendXrayTransportQuery(query, stream)
            }

            val enc = encodeUriComponent(ob.optString("tag", "").ifBlank { rem })
            "vless://${encodeUriComponent(id)}@${uriHost(address)}:$port?${encodedQuery(query)}#$enc"
        } catch (_: Exception) { null }
    }

    private fun putIfNotBlank(target: MutableMap<String, String>, key: String, value: String?) {
        if (!value.isNullOrBlank()) target[key] = value
    }

    private fun jsonStringList(value: Any?): String? = when (value) {
        is JSONArray -> (0 until value.length())
            .mapNotNull { value.optString(it, "").takeIf(String::isNotBlank) }
            .joinToString(",")
            .takeIf(String::isNotBlank)
        is String -> value.takeIf(String::isNotBlank)
        else -> null
    }

    private fun encodeUriComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun uriHost(address: String): String {
        val unwrapped = address.removePrefix("[").removeSuffix("]")
        if (':' in unwrapped) return "[$unwrapped]"
        return IDN.toASCII(unwrapped, IDN.USE_STD3_ASCII_RULES)
    }

    private fun normalizedNetwork(stream: JSONObject?): String {
        val raw = stream?.optString("method", "")
            ?.takeIf(String::isNotBlank)
            ?: stream?.optString("network", "tcp")
            ?: "tcp"
        return when (raw.lowercase()) {
            "raw" -> "tcp"
            "websocket" -> "ws"
            "mkcp" -> "kcp"
            else -> raw.lowercase()
        }
    }

    private fun appendXraySecurityQuery(query: MutableMap<String, String>, stream: JSONObject?) {
        val security = stream?.optString("security", "none")?.ifBlank { "none" } ?: "none"
        query["security"] = security
        when (security) {
            "reality" -> {
                val reality = stream?.optJSONObject("realitySettings")
                putIfNotBlank(query, "sni", reality?.optString("serverName", ""))
                putIfNotBlank(query, "fp", reality?.optString("fingerprint", ""))
                putIfNotBlank(
                    query,
                    "pbk",
                    reality?.optString("password", "")?.takeIf(String::isNotBlank)
                        ?: reality?.optString("publicKey", ""),
                )
                putIfNotBlank(query, "sid", reality?.optString("shortId", ""))
                putIfNotBlank(query, "pqv", reality?.optString("mldsa65Verify", ""))
                putIfNotBlank(query, "spx", reality?.optString("spiderX", ""))
            }
            "tls" -> {
                val tls = stream?.optJSONObject("tlsSettings")
                putIfNotBlank(query, "sni", tls?.optString("serverName", ""))
                putIfNotBlank(query, "fp", tls?.optString("fingerprint", ""))
                putIfNotBlank(query, "alpn", jsonStringList(tls?.opt("alpn")))
                putIfNotBlank(query, "ech", jsonStringList(tls?.opt("echConfigList")) ?: tls?.optString("ech", ""))
                putIfNotBlank(query, "pcs", jsonStringList(tls?.opt("pinnedPeerCertSha256")))
                putIfNotBlank(query, "vcn", tls?.optString("verifyPeerCertByName", ""))
                if (tls?.optBoolean("allowInsecure", false) == true) query["allowInsecure"] = "1"
            }
        }
    }

    private fun appendXrayTransportQuery(
        query: MutableMap<String, String>,
        stream: JSONObject?,
        network: String = normalizedNetwork(stream),
    ) {
        query["type"] = network
        when (network) {
            "ws" -> {
                val ws = stream?.optJSONObject("wsSettings")
                putIfNotBlank(query, "path", ws?.optString("path", ""))
                putIfNotBlank(query, "host", headerValue(ws?.optJSONObject("headers"), "Host"))
            }
            "grpc" -> {
                val grpc = stream?.optJSONObject("grpcSettings")
                putIfNotBlank(query, "serviceName", grpc?.optString("serviceName", ""))
                putIfNotBlank(query, "authority", grpc?.optString("authority", ""))
                if (grpc?.optBoolean("multiMode", false) == true) query["mode"] = "multi"
            }
            "xhttp" -> {
                val xhttp = stream?.optJSONObject("xhttpSettings")
                    ?: stream?.optJSONObject("splithttpSettings")
                putIfNotBlank(query, "path", xhttp?.optString("path", ""))
                putIfNotBlank(query, "host", jsonStringList(xhttp?.opt("host")))
                putIfNotBlank(query, "mode", xhttp?.optString("mode", ""))
                xhttp?.optJSONObject("extra")?.let { query["extra"] = it.toString() }
            }
            "httpupgrade" -> {
                val upgrade = stream?.optJSONObject("httpupgradeSettings")
                putIfNotBlank(query, "path", upgrade?.optString("path", ""))
                putIfNotBlank(
                    query,
                    "host",
                    upgrade?.optString("host", "")?.takeIf(String::isNotBlank)
                        ?: headerValue(upgrade?.optJSONObject("headers"), "Host"),
                )
            }
            "h2", "http" -> {
                val http = stream?.optJSONObject("httpSettings")
                putIfNotBlank(query, "path", http?.optString("path", ""))
                putIfNotBlank(query, "host", jsonStringList(http?.opt("host")))
            }
            "tcp" -> {
                val tcp = stream?.optJSONObject("tcpSettings") ?: stream?.optJSONObject("rawSettings")
                putIfNotBlank(query, "headerType", tcp?.optJSONObject("header")?.optString("type", ""))
            }
            "kcp" -> {
                val kcp = stream?.optJSONObject("kcpSettings")
                putIfNotBlank(query, "headerType", kcp?.optJSONObject("header")?.optString("type", ""))
                putIfNotBlank(query, "seed", kcp?.optString("seed", ""))
                putIfNotBlank(query, "mtu", kcp?.opt("mtu")?.toString())
                putIfNotBlank(query, "tti", kcp?.opt("tti")?.toString())
            }
        }
        stream?.opt("finalmask")?.takeUnless { it === JSONObject.NULL }?.let { value ->
            query["fm"] = if (value is JSONObject) value.toString() else value.toString()
        }
    }

    private fun appendSingBoxQuery(query: MutableMap<String, String>, outbound: JSONObject) {
        val transport = outbound.optJSONObject("transport")
        val network = when (transport?.optString("type", "")?.lowercase()) {
            "websocket" -> "ws"
            "http" -> "http"
            "grpc" -> "grpc"
            "httpupgrade" -> "httpupgrade"
            "quic" -> "quic"
            "" , null -> "tcp"
            else -> transport.optString("type").lowercase()
        }
        query["type"] = network
        when (network) {
            "ws" -> {
                putIfNotBlank(query, "path", transport?.optString("path", ""))
                putIfNotBlank(query, "host", headerValue(transport?.optJSONObject("headers"), "Host"))
            }
            "grpc" -> {
                putIfNotBlank(query, "serviceName", transport?.optString("service_name", ""))
                putIfNotBlank(query, "authority", transport?.optString("authority", ""))
            }
            "http", "httpupgrade" -> {
                putIfNotBlank(query, "path", transport?.optString("path", ""))
                putIfNotBlank(query, "host", jsonStringList(transport?.opt("host")))
            }
        }

        val tls = outbound.optJSONObject("tls")
        if (tls?.optBoolean("enabled", false) != true) {
            query["security"] = "none"
            return
        }
        val reality = tls.optJSONObject("reality")
        val isReality = reality?.optBoolean("enabled", false) == true
        query["security"] = if (isReality) "reality" else "tls"
        putIfNotBlank(query, "sni", tls.optString("server_name", ""))
        putIfNotBlank(query, "alpn", jsonStringList(tls.opt("alpn")))
        putIfNotBlank(query, "fp", tls.optJSONObject("utls")?.optString("fingerprint", ""))
        if (tls.optBoolean("insecure", false)) query["allowInsecure"] = "1"
        if (isReality) {
            putIfNotBlank(query, "pbk", reality.optString("public_key", ""))
            putIfNotBlank(query, "sid", reality.optString("short_id", ""))
        }
    }

    private fun headerValue(headers: JSONObject?, expectedName: String): String? {
        if (headers == null) return null
        val key = headers.keys().asSequence().firstOrNull { it.equals(expectedName, ignoreCase = true) }
            ?: return null
        return jsonStringList(headers.opt(key))
    }

    private fun encodedQuery(query: Map<String, String>): String = query.entries.joinToString("&") { (key, value) ->
        "$key=${encodeUriComponent(value)}"
    }

    // VMess: build the legacy JSON blob and base64 it
    private fun buildVmess(ob: JSONObject, rem: String): String? {
        return try {
            val linkJson = JSONObject()
            linkJson.put("v", "2")

            val settings = ob.optJSONObject("settings")
            val legacyServer = settings?.optJSONArray("vnext")?.optJSONObject(0)
            val legacyUser = legacyServer?.optJSONArray("users")?.optJSONObject(0)
            val singBox = ob.optString("type").equals("vmess", ignoreCase = true)

            val addr = when {
                singBox -> ob.optString("server", "")
                legacyServer != null -> legacyServer.optString("address", "")
                else -> settings?.optString("address", "").orEmpty()
            }
            val port = when {
                singBox -> ob.optInt("server_port", 0)
                legacyServer != null -> legacyServer.optInt("port", 0)
                else -> settings?.optInt("port", 0) ?: 0
            }
            val uuid = when {
                singBox -> ob.optString("uuid", "")
                legacyUser != null -> legacyUser.optString("id", "")
                else -> settings?.optString("id", "").orEmpty()
            }
            if (addr.isBlank() || port !in 1..65535 || uuid.isBlank()) return null

            linkJson.put("add", addr)
            linkJson.put("port", port.toString())
            linkJson.put("id", uuid)
            linkJson.put(
                "aid",
                when {
                    singBox -> ob.optInt("alter_id", 0)
                    legacyUser != null -> legacyUser.optInt("alterId", 0)
                    else -> settings?.optInt("alterId", 0) ?: 0
                }.toString(),
            )
            linkJson.put(
                "scy",
                when {
                    singBox -> ob.optString("security", "auto")
                    legacyUser != null -> legacyUser.optString("security", "auto")
                    else -> settings?.optString("security", "auto") ?: "auto"
                }.ifBlank { "auto" },
            )

            val transport = ob.optJSONObject("transport")
            val stream = ob.optJSONObject("streamSettings")
            val net = if (singBox) {
                when (transport?.optString("type", "")?.lowercase()) {
                    "websocket" -> "ws"
                    "" , null -> "tcp"
                    else -> transport.optString("type").lowercase()
                }
            } else {
                normalizedNetwork(stream)
            }
            linkJson.put("net", net)

            val tlsObj = ob.optJSONObject("tls")
            val streamSecurity = stream?.optString("security", "none") ?: "none"
            val isTls = tlsObj?.optBoolean("enabled") ?: (streamSecurity == "tls" || streamSecurity == "reality")
            linkJson.put("tls", if (isTls) streamSecurity.takeIf { it == "reality" } ?: "tls" else "")

            when (net) {
                "ws" -> {
                    val ws = transport ?: stream?.optJSONObject("wsSettings")
                    putJsonIfNotBlank(linkJson, "path", ws?.optString("path", ""))
                    putJsonIfNotBlank(linkJson, "host", headerValue(ws?.optJSONObject("headers"), "Host"))
                }
                "grpc" -> {
                    val grpc = transport ?: stream?.optJSONObject("grpcSettings")
                    putJsonIfNotBlank(
                        linkJson,
                        "path",
                        grpc?.optString("service_name", "")?.takeIf(String::isNotBlank)
                            ?: grpc?.optString("serviceName", ""),
                    )
                    putJsonIfNotBlank(linkJson, "host", grpc?.optString("authority", ""))
                    linkJson.put("type", if (grpc?.optBoolean("multiMode", false) == true) "multi" else "gun")
                }
                "h2", "http" -> {
                    val http = transport ?: stream?.optJSONObject("httpSettings")
                    putJsonIfNotBlank(linkJson, "path", http?.optString("path", ""))
                    putJsonIfNotBlank(linkJson, "host", jsonStringList(http?.opt("host")))
                }
                "httpupgrade" -> {
                    val upgrade = transport ?: stream?.optJSONObject("httpupgradeSettings")
                    putJsonIfNotBlank(linkJson, "path", upgrade?.optString("path", ""))
                    putJsonIfNotBlank(
                        linkJson,
                        "host",
                        upgrade?.optString("host", "")?.takeIf(String::isNotBlank)
                            ?: headerValue(upgrade?.optJSONObject("headers"), "Host"),
                    )
                }
                "kcp" -> {
                    val kcp = stream?.optJSONObject("kcpSettings")
                    putJsonIfNotBlank(linkJson, "type", kcp?.optJSONObject("header")?.optString("type", ""))
                    putJsonIfNotBlank(linkJson, "path", kcp?.optString("seed", ""))
                }
                "tcp" -> {
                    val tcp = stream?.optJSONObject("tcpSettings") ?: stream?.optJSONObject("rawSettings")
                    putJsonIfNotBlank(linkJson, "type", tcp?.optJSONObject("header")?.optString("type", ""))
                }
            }

            if (singBox) appendSingBoxTlsToVmess(linkJson, tlsObj)
            else appendXrayTlsToVmess(linkJson, stream)

            val finalRem = if (ob.has("tag")) ob.getString("tag") else (if (ob.has("remarks")) ob.getString("remarks") else rem)
            linkJson.put("ps", finalRem)

            val base64 = Base64.encodeToString(linkJson.toString().toByteArray(), Base64.NO_WRAP)
            "vmess://$base64"
        } catch (_: Exception) { null }
    }

    private fun appendXrayTlsToVmess(target: JSONObject, stream: JSONObject?) {
        when (stream?.optString("security", "none")) {
            "tls" -> {
                val tls = stream.optJSONObject("tlsSettings")
                putJsonIfNotBlank(target, "sni", tls?.optString("serverName", ""))
                putJsonIfNotBlank(target, "fp", tls?.optString("fingerprint", ""))
                putJsonIfNotBlank(target, "alpn", jsonStringList(tls?.opt("alpn")))
            }
            "reality" -> {
                val reality = stream.optJSONObject("realitySettings")
                putJsonIfNotBlank(target, "sni", reality?.optString("serverName", ""))
                putJsonIfNotBlank(target, "fp", reality?.optString("fingerprint", ""))
                putJsonIfNotBlank(
                    target,
                    "pbk",
                    reality?.optString("password", "")?.takeIf(String::isNotBlank)
                        ?: reality?.optString("publicKey", ""),
                )
                putJsonIfNotBlank(target, "sid", reality?.optString("shortId", ""))
                putJsonIfNotBlank(target, "spx", reality?.optString("spiderX", ""))
            }
        }
    }

    private fun appendSingBoxTlsToVmess(target: JSONObject, tls: JSONObject?) {
        if (tls?.optBoolean("enabled", false) != true) return
        putJsonIfNotBlank(target, "sni", tls.optString("server_name", ""))
        putJsonIfNotBlank(target, "alpn", jsonStringList(tls.opt("alpn")))
        putJsonIfNotBlank(target, "fp", tls.optJSONObject("utls")?.optString("fingerprint", ""))
        tls.optJSONObject("reality")?.takeIf { it.optBoolean("enabled", false) }?.let { reality ->
            putJsonIfNotBlank(target, "pbk", reality.optString("public_key", ""))
            putJsonIfNotBlank(target, "sid", reality.optString("short_id", ""))
        }
    }

    private fun putJsonIfNotBlank(target: JSONObject, key: String, value: String?) {
        if (!value.isNullOrBlank()) target.put(key, value)
    }

    // Shadowsocks: base64(method:password)@host:port
    private fun buildShadowsocks(ob: JSONObject, rem: String): String? {
        return try {
            val address: String
            val port: Int
            val method: String
            val password: String
            var plugin = ""
            var pluginOptions = ""
            if (ob.has("server")) {
                address = ob.getString("server")
                port = ob.getInt("server_port")
                method = ob.getString("method")
                password = ob.getString("password")
                plugin = ob.optString("plugin", "")
                pluginOptions = ob.optString("plugin_opts", "")
            } else {
                val settings = ob.optJSONObject("settings")
                val s = settings?.optJSONArray("servers")?.optJSONObject(0) ?: settings ?: return null
                address = s.getString("address")
                port = s.getInt("port")
                method = s.getString("method")
                password = s.getString("password")
                plugin = s.optString("plugin", "")
                pluginOptions = s.optString("plugin_opts", s.optString("pluginOpts", ""))
            }
            val credentials = "$method:$password"
            val ui = Base64.encodeToString(
                credentials.toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val finalRem = ob.optString("tag", "").ifBlank { ob.optString("remarks", "").ifBlank { rem } }
            val pluginValue = listOf(plugin, pluginOptions).filter(String::isNotBlank).joinToString(";")
            val query = pluginValue.takeIf(String::isNotBlank)?.let { "?plugin=${encodeUriComponent(it)}" }.orEmpty()
            "ss://$ui@${uriHost(address)}:$port$query#${encodeUriComponent(finalRem)}"
        } catch (_: Exception) { null }
    }

    // Trojan: password@host:port with a tls/ws query
    private fun buildTrojan(ob: JSONObject, rem: String): String? {
        return try {
            val settings = ob.optJSONObject("settings")
            val legacyServer = settings?.optJSONArray("servers")?.optJSONObject(0)
            val singBox = ob.optString("type").equals("trojan", ignoreCase = true)
            val address = when {
                singBox -> ob.optString("server", "")
                legacyServer != null -> legacyServer.optString("address", "")
                else -> settings?.optString("address", "").orEmpty()
            }
            val port = when {
                singBox -> ob.optInt("server_port", 0)
                legacyServer != null -> legacyServer.optInt("port", 0)
                else -> settings?.optInt("port", 0) ?: 0
            }
            val password = when {
                singBox -> ob.optString("password", "")
                legacyServer != null -> legacyServer.optString("password", "")
                else -> settings?.optString("password", "").orEmpty()
            }
            if (address.isBlank() || port !in 1..65535 || password.isBlank()) return null

            val query = linkedMapOf<String, String>()
            if (singBox) {
                appendSingBoxQuery(query, ob)
            } else {
                val stream = ob.optJSONObject("streamSettings")
                appendXraySecurityQuery(query, stream)
                appendXrayTransportQuery(query, stream)
            }
            val finalRem = ob.optString("tag", "").ifBlank { ob.optString("remarks", "").ifBlank { rem } }
            "trojan://${encodeUriComponent(password)}@${uriHost(address)}:$port?${encodedQuery(query)}#" +
                encodeUriComponent(finalRem)
        } catch (_: Exception) { null }
    }

    // Hysteria2: password@host:port with an obfs/sni query
    private fun buildHysteria2(ob: JSONObject, rem: String): String? {
        return try {
            val settings = ob.optJSONObject("settings")
            val legacyServer = settings?.optJSONArray("servers")?.optJSONObject(0)
            val singBox = ob.optString("type").equals("hysteria2", ignoreCase = true)
            val address = when {
                singBox -> ob.optString("server", "")
                legacyServer != null -> legacyServer.optString("address", "")
                else -> settings?.optString("address", "").orEmpty()
            }
            val port = when {
                singBox -> ob.optInt("server_port", 0)
                legacyServer != null -> legacyServer.optInt("port", 0)
                else -> settings?.optInt("port", 0) ?: 0
            }
            val portSpec = if (singBox && hasJsonValue(ob.opt("server_ports"))) {
                hysteria2UriPortSpec(ob.opt("server_ports")) ?: return null
            } else {
                if (port !in 1..65535) return null
                port.toString()
            }
            val stream = ob.optJSONObject("streamSettings")
            val legacyHy2 = stream?.optJSONObject("hy2Settings")
            val currentHy2 = stream?.optJSONObject("hysteriaSettings")
            val password = when {
                singBox -> ob.optString("password", "")
                legacyHy2 != null -> legacyHy2.optString("password", "")
                else -> currentHy2?.optString("auth", "").orEmpty()
            }
            if (address.isBlank() || password.isBlank()) return null

            val query = linkedMapOf<String, String>()
            val obfs = if (singBox) ob.optJSONObject("obfs") else legacyHy2?.optJSONObject("obfs")
            if (singBox && obfs != null &&
                (hasJsonValue(obfs.opt("min_packet_size")) || hasJsonValue(obfs.opt("max_packet_size")))) {
                return null
            }
            putIfNotBlank(query, "obfs", obfs?.optString("type", ""))
            putIfNotBlank(query, "obfs-password", obfs?.optString("password", ""))
            if (singBox) {
                val tls = ob.optJSONObject("tls")
                putIfNotBlank(query, "sni", tls?.optString("server_name", ""))
                putIfNotBlank(query, "alpn", jsonStringList(tls?.opt("alpn")))
                if (tls?.optBoolean("insecure", false) == true) query["insecure"] = "1"
                val ech = tls?.optJSONObject("ech")
                if (ech?.optBoolean("enabled", false) == true) {
                    if (ech.optString("config_path", "").isNotBlank()) return null
                    val configs = jsonStringValues(ech.opt("config"))
                    if (configs.size != 1) return null
                    query["ech"] = configs.single()
                }
            } else {
                val tls = stream?.optJSONObject("tlsSettings")
                putIfNotBlank(query, "sni", tls?.optString("serverName", ""))
                putIfNotBlank(query, "alpn", jsonStringList(tls?.opt("alpn")))
                if (tls?.optBoolean("allowInsecure", false) == true) query["insecure"] = "1"
                val pins = jsonStringValues(tls?.opt("pinnedPeerCertSha256"))
                if (pins.size > 1) return null
                putIfNotBlank(query, "pinSHA256", pins.singleOrNull())
                val echRaw = tls?.opt("echConfigList").takeIf(::hasJsonValue) ?: tls?.opt("ech")
                val echValues = jsonStringValues(echRaw)
                if (echValues.size > 1 || echValues.any { "://" in it }) return null
                putIfNotBlank(query, "ech", echValues.singleOrNull())
            }
            val finalRem = ob.optString("tag", "").ifBlank { ob.optString("remarks", "").ifBlank { rem } }
            val queryString = encodedQuery(query).takeIf(String::isNotEmpty)?.let { "?$it" }.orEmpty()
            "hysteria2://${encodeUriComponent(password)}@${uriHost(address)}:$portSpec/$queryString#" +
                encodeUriComponent(finalRem)
        } catch (_: Exception) { null }
    }

    private fun hasJsonValue(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is String -> value.isNotBlank()
        is JSONArray -> (0 until value.length()).any { hasJsonValue(value.opt(it)) }
        else -> true
    }

    private fun jsonStringValues(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull {
            value.optString(it, "").trim().takeIf(String::isNotEmpty)
        }
        is String -> listOfNotNull(value.trim().takeIf(String::isNotEmpty))
        else -> emptyList()
    }

    private fun hysteria2UriPortSpec(value: Any?): String? {
        val rawTokens = when (value) {
            is JSONArray -> (0 until value.length()).flatMap { index ->
                value.opt(index)?.toString()?.split(',') ?: emptyList()
            }
            null, JSONObject.NULL -> emptyList()
            else -> value.toString().split(',')
        }
        val normalized = rawTokens.map { raw ->
            val parts = raw.trim().split(':', '-', limit = 2)
            if (parts.isEmpty() || parts.size > 2) return null
            val first = parts[0].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            if (parts.size == 1) {
                first.toString()
            } else {
                val last = parts[1].toIntOrNull()?.takeIf { it in first..65535 } ?: return null
                "$first-$last"
            }
        }
        return normalized.takeIf(List<String>::isNotEmpty)?.joinToString(",")
    }

    // TUIC: uuid:password@host:port with a congestion/tls query
    private fun buildTuic(ob: JSONObject, rem: String): String? {
        return try {
            val address = ob.optString("server")
            val port = ob.optInt("server_port")
            val uuid = ob.optString("uuid")
            val password = ob.optString("password")
            if (address.isBlank() || port !in 1..65535 || uuid.isBlank() || password.isBlank()) return null

            val query = mutableMapOf<String, String>()
            val cc = ob.optString("congestion_control")
            if (cc.isNotEmpty()) query["congestion_control"] = cc

            val mode = ob.optString("udp_relay_mode")
            if (mode.isNotEmpty()) query["udp_relay_mode"] = mode

            val tls = ob.optJSONObject("tls")
            if (tls != null && tls.optBoolean("enabled", false)) {
                val sni = tls.optString("server_name")
                if (sni.isNotEmpty()) query["sni"] = sni

                val alpnArr = tls.optJSONArray("alpn")
                if (alpnArr != null && alpnArr.length() > 0) {
                    query["alpn"] = alpnArr.getString(0)
                }

                if (tls.optBoolean("insecure", false)) {
                    query["allow_insecure"] = "1"
                }
            }

            val queryStr = query.toList().sortedBy { it.first }.joinToString("&") {
                "${it.first}=${encodeUriComponent(it.second)}"
            }
            val queryString = if (queryStr.isNotEmpty()) "?$queryStr" else ""

            val finalRem = if (ob.has("tag")) ob.getString("tag") else (if (ob.has("remarks")) ob.getString("remarks") else rem)
            val encRem = encodeUriComponent(finalRem)

            "tuic://${encodeUriComponent(uuid)}:${encodeUriComponent(password)}@${uriHost(address)}:$port$queryString#$encRem"
        } catch (e: Exception) { null }
    }
}
