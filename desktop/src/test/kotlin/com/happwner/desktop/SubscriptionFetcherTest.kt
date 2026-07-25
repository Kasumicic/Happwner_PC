package com.happwner.desktop

import com.happwner.Subscription
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubscriptionFetcherTest {
    @Test
    fun returnsHttpStatusAndProcessedSize() {
        val server = testServer(206, "vless://example")
        try {
            val result = SubscriptionFetcher().fetch(
                Subscription(name = "Test", source = server.url("/subscription")),
            )

            assertEquals(206, result.statusCode)
            assertEquals("vless://example", result.body.toString(Charsets.UTF_8))
        } finally {
            server.stop()
        }
    }

    @Test
    fun reportsProviderHttpError() {
        val server = testServer(503, "try later")
        try {
            val error = assertFailsWith<UpstreamException> {
                SubscriptionFetcher().fetch(
                    Subscription(name = "Test", source = server.url("/subscription")),
                )
            }

            assertEquals("Провайдер вернул HTTP 503", error.message)
        } finally {
            server.stop()
        }
    }

    private fun testServer(status: Int, response: String): TestHttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/subscription") { exchange ->
                val body = response.toByteArray()
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        return TestHttpServer(server)
    }

    private class TestHttpServer(private val server: HttpServer) {
        fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"
        fun stop() = server.stop(0)
    }
}
