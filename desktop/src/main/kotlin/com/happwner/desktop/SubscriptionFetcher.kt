package com.happwner.desktop

import com.happwner.SourceResolver
import com.happwner.Subscription
import com.happwner.SubscriptionProcessor
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class UpstreamException(message: String, val timeout: Boolean = false) : Exception(message)

data class FetchedSubscription(
    val body: ByteArray,
    val headers: Map<String, List<String>>,
    val statusCode: Int?,
    val userInfo: SubscriptionUserInfo? = SubscriptionUserInfoParser.parse(headers),
    val xraySkipped: Int = 0,
    val uriPreserved: Int = 0,
)

class SubscriptionFetcher(
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
) {
    init {
        require(maxBodyBytes > 0) { "Лимит ответа должен быть положительным" }
    }

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetch(subscription: Subscription): FetchedSubscription {
        val resolution = SourceResolver.resolve(subscription.source)
        if (resolution is SourceResolver.Result.Static) {
            val transformed = SubscriptionProcessor.transformWithStats(
                "",
                resolution.content,
                emptyMap(),
                subscription,
            )
            return FetchedSubscription(
                body = transformed.text.toByteArray(Charsets.UTF_8),
                headers = emptyMap(),
                statusCode = null,
                xraySkipped = transformed.xraySkipped,
                uriPreserved = transformed.uriPreserved,
            )
        }
        val url = when (resolution) {
            is SourceResolver.Result.Success -> resolution.url
            is SourceResolver.Result.Error -> throw UpstreamException(resolution.message)
            is SourceResolver.Result.Static -> error("handled above")
        }
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(20))
            .header("x-hwid", subscription.hwid)
            .apply { if (subscription.userAgent.isNotBlank()) header("User-Agent", subscription.userAgent) }
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (error: java.net.http.HttpTimeoutException) {
            throw UpstreamException("Провайдер не ответил за 20 секунд", timeout = true)
        } catch (error: Exception) {
            throw UpstreamException(error.message ?: "Ошибка подключения к провайдеру")
        }
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw UpstreamException("Провайдер вернул HTTP ${response.statusCode()}")
        }

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        response.body().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBodyBytes) throw UpstreamException("Ответ превышает лимит ${formatLimit(maxBodyBytes)}")
                output.write(buffer, 0, read)
            }
        }
        val transformed = try {
            SubscriptionProcessor.transformWithStats(
                url,
                output.toString(Charsets.UTF_8),
                response.headers().map(),
                subscription,
            )
        } catch (error: Exception) {
            throw UpstreamException(error.message ?: "Ошибка обработки подписки")
        }
        return FetchedSubscription(
            body = transformed.text.toByteArray(Charsets.UTF_8),
            headers = response.headers().map(),
            statusCode = response.statusCode(),
            xraySkipped = transformed.xraySkipped,
            uriPreserved = transformed.uriPreserved,
        )
    }

    companion object {
        private const val MAX_BODY_BYTES = 32L * 1024 * 1024

        private fun formatLimit(bytes: Long): String =
            if (bytes % (1024 * 1024) == 0L) "${bytes / (1024 * 1024)} МБ" else "$bytes байт"
    }
}
