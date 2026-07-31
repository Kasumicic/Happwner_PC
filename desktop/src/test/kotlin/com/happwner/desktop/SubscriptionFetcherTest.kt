package com.happwner.desktop

import com.happwner.Subscription
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubscriptionFetcherTest {
    @Test
    fun returnsHttpStatusAndProcessedSize() {
        val server = testServer(
            status = 206,
            response = "vless://example",
            userInfo = "upload=1024; download=2048; total=10240; expire=1800000000",
        )
        try {
            val result = SubscriptionFetcher().fetch(
                Subscription(name = "Test", source = server.url("/subscription")),
            )

            assertEquals(206, result.statusCode)
            assertEquals("vless://example", result.body.toString(Charsets.UTF_8))
            assertEquals(3072L, result.userInfo?.usedBytes)
            assertEquals(7168L, result.userInfo?.remainingBytes)
            assertEquals(1_800_000_000L, result.userInfo?.expireEpochSeconds)
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

    @Test
    fun rejectsResponseBeyondConfiguredLimit() {
        val server = testServer(200, "vless://profile-that-is-too-large")
        try {
            val error = assertFailsWith<UpstreamException> {
                SubscriptionFetcher(maxBodyBytes = 16).fetch(
                    Subscription(name = "Test", source = server.url("/subscription")),
                )
            }

            assertTrue(error.message.orEmpty().contains("16 байт"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsNonPositiveResponseLimit() {
        assertFailsWith<IllegalArgumentException> { SubscriptionFetcher(maxBodyBytes = 0) }
    }

    private fun testServer(status: Int, response: String, userInfo: String? = null): TestHttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/subscription") { exchange ->
                val body = response.toByteArray()
                userInfo?.let { exchange.responseHeaders.set("Subscription-Userinfo", it) }
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
