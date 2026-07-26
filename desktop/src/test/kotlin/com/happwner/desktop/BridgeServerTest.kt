package com.happwner.desktop

import com.happwner.BindMode
import com.happwner.ServerSettings
import com.happwner.StoredState
import com.happwner.Subscription
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeServerTest {
    @Test
    fun servesSavedSubscriptionAndForwardsMetadata() {
        var receivedHwid: String? = null
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sub") { exchange ->
                receivedHwid = exchange.requestHeaders.getFirst("x-hwid")
                val body = "vless://example".toByteArray()
                exchange.responseHeaders.set("Subscription-Userinfo", "upload=1; download=2")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val bridgePort = freePort()
        val subscription = Subscription(
            id = "test-id",
            name = "Test",
            source = "http://127.0.0.1:${upstream.address.port}/sub",
            hwid = "device-123",
        )
        var lastRequest: SubscriptionRequestRecord? = null
        val bridge = BridgeServer(
            stateProvider = { StoredState(subscriptions = listOf(subscription)) },
            onSubscriptionResult = { id, result ->
                assertEquals(subscription.id, id)
                lastRequest = result
            },
        )
        try {
            bridge.start(ServerSettings(bindMode = BindMode.LOCAL, port = bridgePort))
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$bridgePort/sub/test-id")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())
            assertEquals("vless://example", response.body())
            assertEquals("device-123", receivedHwid)
            assertEquals("upload=1; download=2", response.headers().firstValue("Subscription-Userinfo").orElse(null))
            val recorded = assertNotNull(lastRequest)
            assertEquals(200, recorded.statusCode)
            assertEquals("vless://example".toByteArray().size, recorded.sizeBytes)
            assertEquals(1, recorded.profileCount)
            assertEquals(null, recorded.error)
        } finally {
            bridge.close()
            upstream.stop(0)
        }
    }

    @Test
    fun healthEndpointDoesNotExposeState() {
        val port = freePort()
        val bridge = BridgeServer(stateProvider = { StoredState() })
        try {
            bridge.start(ServerSettings(port = port))
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())
            assertEquals("{\"status\":\"ok\"}", response.body())
        } finally {
            bridge.close()
        }
    }

    @Test
    fun recordsFailedSubscriptionRequest() {
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sub") { exchange ->
                val body = "try later".toByteArray()
                exchange.sendResponseHeaders(503, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val subscription = Subscription(
            id = "failed-id",
            name = "Failed",
            source = "http://127.0.0.1:${upstream.address.port}/sub",
        )
        var lastRequest: SubscriptionRequestRecord? = null
        val bridgePort = freePort()
        val bridge = BridgeServer(
            stateProvider = { StoredState(subscriptions = listOf(subscription)) },
            onSubscriptionResult = { _, result -> lastRequest = result },
        )
        try {
            bridge.start(ServerSettings(port = bridgePort))
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$bridgePort/sub/${subscription.id}")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            assertEquals(502, response.statusCode())
            val recorded = assertNotNull(lastRequest)
            assertTrue(recorded.error.orEmpty().contains("HTTP 503"))
        } finally {
            bridge.close()
            upstream.stop(0)
        }
    }

    @Test
    fun closeStopsServerAndReleasesPort() {
        val port = freePort()
        val bridge = BridgeServer(stateProvider = { StoredState() })

        bridge.start(ServerSettings(port = port))
        assertEquals(true, bridge.running)
        bridge.close()

        assertFalse(bridge.running)
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", port))
        }
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }
}
