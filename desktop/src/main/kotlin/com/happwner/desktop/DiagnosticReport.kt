package com.happwner.desktop

import com.happwner.BindMode
import com.happwner.ServerSettings
import com.happwner.Subscription
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object DiagnosticSanitizer {
    private val urlPattern = Regex("""(?i)\b(?:https?|happ|v2raytun|incy)://\S+""")
    private val uuidPattern = Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b""")

    fun errorMessage(message: String, subscription: Subscription?): String {
        var safe = message
        subscription?.source?.takeIf(String::isNotBlank)?.let { safe = safe.replace(it, "[source hidden]") }
        subscription?.hwid?.takeIf(String::isNotBlank)?.let { safe = safe.replace(it, "[HWID hidden]") }
        safe = safe.replace(urlPattern, "[URL hidden]")
        safe = safe.replace(uuidPattern, "[ID hidden]")
        return safe.take(MAX_ERROR_LENGTH)
    }

    fun clientAddress(address: String?): String = when {
        address.isNullOrBlank() -> "—"
        address == "127.0.0.1" || address == "::1" || address == "0:0:0:0:0:0:0:1" -> "loopback"
        IPV4.matches(address) -> address.substringBeforeLast('.') + ".x"
        ':' in address -> address.substringBeforeLast(':') + ":x"
        else -> "hidden"
    }

    private val IPV4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    private const val MAX_ERROR_LENGTH = 500
}

object DiagnosticReport {
    fun create(
        settings: ServerSettings,
        serverRunning: Boolean,
        activities: List<SubscriptionActivity>,
        language: String,
    ): String {
        val russian = language != "en"
        val mode = if (settings.bindMode == BindMode.LOCAL) "LOCAL" else "LAN"
        val lines = mutableListOf(
            "Happwner PC — ${if (russian) "диагностический отчёт" else "diagnostic report"}",
            "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            "${if (russian) "Сервер" else "Server"}: ${if (serverRunning) "ON" else "OFF"}",
            "${if (russian) "Режим" else "Mode"}: $mode",
            "${if (russian) "Порт" else "Port"}: ${settings.port}",
            "",
            "${if (russian) "Последние события" else "Recent activity"}: ${activities.size}",
        )
        activities.forEach { activity ->
            val request = activity.request
            val result = request.error?.let { "ERROR: $it" }
                ?: listOfNotNull(
                    request.servedStatusCode?.let { "HTTP $it" },
                    request.sizeBytes?.let { "$it B" },
                    request.profileCount?.let { "${if (russian) "профили" else "profiles"}: $it" },
                ).joinToString(", ")
            val details = buildList {
                request.durationMillis?.let { add("${it} ms") }
                if (request.protocols.isNotEmpty()) {
                    add(request.protocols.entries.joinToString { "${it.key}:${it.value}" })
                }
                if (request.transformations.isNotEmpty()) add(request.transformations.joinToString("+"))
            }.joinToString(", ")
            val safeName = DiagnosticSanitizer.errorMessage(activity.subscriptionName, null).take(MAX_NAME_LENGTH)
            lines += "${TIME_FORMATTER.format(Instant.ofEpochMilli(request.completedAtMillis))} | " +
                "$safeName | ${request.origin.name} | " +
                "${DiagnosticSanitizer.clientAddress(request.clientAddress)} | $result" +
                details.takeIf(String::isNotBlank)?.let { " | $it" }.orEmpty()
        }
        return lines.joinToString("\n")
    }

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private const val MAX_NAME_LENGTH = 80
}
