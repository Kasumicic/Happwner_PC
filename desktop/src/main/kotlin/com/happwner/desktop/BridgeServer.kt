package com.happwner.desktop

import com.happwner.BindMode
import com.happwner.ServerSettings
import com.happwner.StoredState
import com.happwner.Subscription
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class BridgeServer(
    private val stateProvider: () -> StoredState,
    private val fetcher: SubscriptionFetcher = SubscriptionFetcher(),
) : AutoCloseable {
    @Volatile private var server: HttpServer? = null
    @Volatile private var executor: ExecutorService? = null
    @Volatile var lastError: String? = null
        private set

    val running: Boolean get() = server != null

    @Synchronized
    fun start(settings: ServerSettings) {
        stop()
        val address = if (settings.bindMode == BindMode.LOCAL) "127.0.0.1" else "0.0.0.0"
        val created = try {
            HttpServer.create(InetSocketAddress(address, settings.port), 32)
        } catch (error: Exception) {
            lastError = error.message ?: "Не удалось открыть порт ${settings.port}"
            throw error
        }
        val createdExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "happwner-http").apply { isDaemon = true }
        }
        created.executor = createdExecutor
        created.createContext("/") { exchange -> handle(exchange) }
        created.start()
        lastError = null
        server = created
        executor = createdExecutor
    }

    @Synchronized
    fun stop() {
        val activeServer = server
        server = null
        val activeExecutor = executor
        executor = null
        activeServer?.stop(0)
        activeExecutor?.shutdownNow()
        activeExecutor?.awaitTermination(2, TimeUnit.SECONDS)
    }

    override fun close() = stop()

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") return sendText(exchange, 405, "Method Not Allowed")
            val path = exchange.requestURI.path
            when {
                path == "/health" -> sendText(exchange, 200, "{\"status\":\"ok\"}", "application/json; charset=utf-8")
                path.startsWith("/sub/") -> handleSaved(exchange, path.removePrefix("/sub/").substringBefore('/'))
                isLegacy(exchange) -> handleLegacy(exchange)
                else -> sendText(exchange, 404, "Not Found")
            }
        } catch (error: UpstreamException) {
            sendText(exchange, if (error.timeout) 504 else 502, error.message ?: "Upstream Error")
        } catch (error: IllegalArgumentException) {
            sendText(exchange, 400, error.message ?: "Bad Request")
        } catch (_: Exception) {
            runCatching { sendText(exchange, 500, "Internal Server Error") }
        } finally {
            exchange.close()
        }
    }

    private fun handleSaved(exchange: HttpExchange, id: String) {
        val subscription = stateProvider().subscriptions.firstOrNull { it.id == id && it.enabled }
            ?: return sendText(exchange, 404, "Subscription Not Found")
        sendSubscription(exchange, fetcher.fetch(subscription))
    }

    private fun handleLegacy(exchange: HttpExchange) {
        if (!exchange.remoteAddress.address.isLoopbackAddress) {
            return sendText(exchange, 403, "Legacy links are available only on this computer")
        }
        val query = when {
            exchange.requestURI.rawQuery != null -> exchange.requestURI.rawQuery
            exchange.requestURI.rawPath.startsWith("/url=") -> exchange.requestURI.rawPath.removePrefix("/")
            else -> ""
        }
        val params = parseQuery(query)
        val source = params["url"] ?: throw IllegalArgumentException("Missing URL")
        val subscription = Subscription(
            name = "Legacy",
            source = source,
            hwid = params["hwid"].orEmpty(),
            userAgent = params["ua"].orEmpty(),
        )
        sendSubscription(exchange, fetcher.fetch(subscription))
    }

    private fun isLegacy(exchange: HttpExchange): Boolean =
        exchange.requestURI.rawQuery?.split('&')?.any { it.startsWith("url=") } == true ||
            exchange.requestURI.rawPath.startsWith("/url=")

    private fun parseQuery(query: String): Map<String, String> = query.split('&').mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator < 0) null else {
            val key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8)
            key to value
        }
    }.toMap()

    private fun sendSubscription(exchange: HttpExchange, response: FetchedSubscription) {
        exchange.responseHeaders.apply {
            set("Content-Type", "text/plain; charset=utf-8")
            set("Access-Control-Allow-Origin", "*")
            for (name in FORWARDED_HEADERS) {
                response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.let { values ->
                    set(name, values.joinToString(", "))
                }
            }
        }
        exchange.sendResponseHeaders(200, response.body.size.toLong())
        exchange.responseBody.use { it.write(response.body) }
    }

    private fun sendText(exchange: HttpExchange, status: Int, message: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = message.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        private val FORWARDED_HEADERS = setOf(
            "Subscription-Userinfo",
            "Content-Disposition",
            "Profile-Update-Interval",
            "Profile-Title",
        )
    }
}
