package com.happwner

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkConverterTest {
    @Test
    fun tlsVlessKeepsTlsFieldsWithoutFakeRealityParameters() {
        val result = LinkConverter.convert(
            xrayConfig(
                security = "tls",
                securitySettings = """
                    "tlsSettings": {
                        "serverName": "spb.example.com",
                        "fingerprint": "firefox",
                        "alpn": ["http/1.1"]
                    }
                """.trimIndent(),
            ),
            jsonToUri = true,
            tryBase64 = false,
        )

        val query = parseQuery(result)
        assertEquals("tls", query["security"])
        assertEquals("spb.example.com", query["sni"])
        assertEquals("firefox", query["fp"])
        assertEquals("http/1.1", query["alpn"])
        assertFalse("pbk" in query)
        assertFalse("sid" in query)
    }

    @Test
    fun realityVlessKeepsPublicKeyAndShortId() {
        val publicKey = "VHF-dD-tisM7mq7wkcHNfohYjFGlz9n6ZeKLSm2XhGc"
        val result = LinkConverter.convert(
            xrayConfig(
                security = "reality",
                securitySettings = """
                    "realitySettings": {
                        "serverName": "reality.example.com",
                        "fingerprint": "firefox",
                        "publicKey": "$publicKey",
                        "shortId": "4fd3840415403f31"
                    }
                """.trimIndent(),
            ),
            jsonToUri = true,
            tryBase64 = false,
        )

        val query = parseQuery(result)
        assertEquals("reality", query["security"])
        assertEquals(publicKey, query["pbk"])
        assertEquals("4fd3840415403f31", query["sid"])
        assertEquals("reality.example.com", query["sni"])
    }

    @Test
    fun xhttpVlessKeepsTransportSettings() {
        val result = LinkConverter.convert(
            xrayConfig(
                security = "tls",
                network = "xhttp",
                securitySettings = """
                    "tlsSettings": {"serverName": "cdn.example.com"},
                    "xhttpSettings": {
                        "mode": "packet-up",
                        "host": "cdn.example.com",
                        "path": "/api-test",
                        "extra": {"scMaxBufferedPosts": 30}
                    }
                """.trimIndent(),
            ),
            jsonToUri = true,
            tryBase64 = false,
        )

        val query = parseQuery(result)
        assertEquals("xhttp", query["type"])
        assertEquals("packet-up", query["mode"])
        assertEquals("cdn.example.com", query["host"])
        assertEquals("/api-test", query["path"])
        assertTrue(query.getValue("extra").contains("\"scMaxBufferedPosts\":30"))
    }

    @Test
    fun jsonConfigConvertsEverySupportedOutbound() {
        val first = JSONObject(xrayConfig("tls", securitySettings = "\"tlsSettings\": {}"))
            .getJSONArray("outbounds").getJSONObject(0)
        val second = JSONObject(first.toString()).apply {
            getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).apply {
                put("address", "second.example.com")
                getJSONArray("users").getJSONObject(0).put("id", "1cb3a37a-51ee-40a4-a0fd-dd5a336f50ea")
            }
        }
        val config = JSONObject().put("outbounds", org.json.JSONArray().put(first).put(second))

        val links = LinkConverter.convert(config.toString(), jsonToUri = true, tryBase64 = false).lines()

        assertEquals(2, links.size)
        assertEquals("server.example.com", URI(links[0]).host)
        assertEquals("second.example.com", URI(links[1]).host)
    }

    @Test
    fun uriConversionsBracketIpv6Hosts() {
        val configs = listOf(
            """{"type":"shadowsocks","server":"2001:db8::10","server_port":443,"method":"aes-128-gcm","password":"secret"}""",
            """{"outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"2001:db8::11","port":443,"password":"secret"}]}}]}""",
            """{"outbounds":[{"protocol":"hysteria2","settings":{"servers":[{"address":"2001:db8::12","port":443}]},"streamSettings":{"hy2Settings":{"password":"secret"}}}]}""",
            """{"type":"tuic","server":"2001:db8::13","server_port":443,"uuid":"user","password":"secret"}""",
        )

        val hosts = configs.map { config ->
            URI(LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)).host
        }

        assertEquals(listOf("[2001:db8::10]", "[2001:db8::11]", "[2001:db8::12]", "[2001:db8::13]"), hosts)
    }

    private fun xrayConfig(
        security: String,
        network: String = "tcp",
        securitySettings: String,
    ): String = JSONObject("""
        {
          "remarks": "Test profile",
          "outbounds": [{
            "protocol": "vless",
            "settings": {
              "vnext": [{
                "address": "server.example.com",
                "port": 443,
                "users": [{
                  "id": "f716410d-9013-4378-b4fe-8a3bbc7f2c39",
                  "encryption": "none",
                  "flow": "xtls-rprx-vision"
                }]
              }]
            },
            "streamSettings": {
              "network": "$network",
              "security": "$security",
              $securitySettings
            }
          }]
        }
    """.trimIndent()).toString()

    private fun parseQuery(link: String): Map<String, String> {
        val rawQuery = URI(link).rawQuery
        return rawQuery.split('&').associate { part ->
            val (key, value) = part.split('=', limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }
}
