package com.happwner

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SingBoxConverterTest {
    @Test
    fun convertsCurrentXrayVlessGrpcRealitySchema() {
        val config = xrayConfig(
            """
            {
              "protocol": "vless",
              "tag": "modern-vless",
              "settings": {
                "address": "vless.example.com",
                "port": 443,
                "id": "11111111-1111-4111-8111-111111111111",
                "encryption": "none",
                "flow": "xtls-rprx-vision"
              },
              "streamSettings": {
                "method": "grpc",
                "security": "reality",
                "realitySettings": {
                  "serverName": "front.example.com",
                  "fingerprint": "chrome",
                  "password": "reality-public-key",
                  "shortId": "a1b2c3d4"
                },
                "grpcSettings": {"serviceName": "vpn-service", "authority": "front.example.com"}
              }
            }
            """.trimIndent(),
        )

        val result = assertIs<SingBoxConverter.Result.Ok>(SingBoxConverter.convert(config))
        val outbound = proxyOutbound(result.config, "vless")

        assertEquals("vless.example.com", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("11111111-1111-4111-8111-111111111111", outbound.getString("uuid"))
        assertEquals("xtls-rprx-vision", outbound.getString("flow"))
        assertEquals("grpc", outbound.getJSONObject("transport").getString("type"))
        assertEquals("vpn-service", outbound.getJSONObject("transport").getString("service_name"))
        assertEquals("reality-public-key", outbound.getJSONObject("tls").getJSONObject("reality").getString("public_key"))
    }

    @Test
    fun convertsCurrentXrayTrojanShadowsocksAndHysteriaSchemas() {
        val config = """
            {
              "outbounds": [{
                "protocol": "trojan",
                "tag": "trojan",
                "settings": {"address": "trojan.example.com", "port": 443, "password": "trojan-pass"},
                "streamSettings": {"security": "tls", "tlsSettings": {"serverName": "front.example.com"}}
              }, {
                "protocol": "shadowsocks",
                "tag": "ss",
                "settings": {"address": "ss.example.com", "port": 8388, "method": "aes-128-gcm", "password": "ss-pass"}
              }, {
                "protocol": "hysteria",
                "tag": "hy2",
                "settings": {"version": 2, "address": "hy.example.com", "port": 8443},
                "streamSettings": {
                  "method": "hysteria",
                  "security": "tls",
                  "hysteriaSettings": {"version": 2, "auth": "hy-pass"},
                  "tlsSettings": {"serverName": "hy-front.example.com", "alpn": ["h3"]}
                }
              }]
            }
        """.trimIndent()

        val result = assertIs<SingBoxConverter.Result.Ok>(SingBoxConverter.convert(config))
        val outbounds = result.config.getJSONArray("outbounds")
        val trojan = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
            .first { it.optString("type") == "trojan" }
        val shadowsocks = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
            .first { it.optString("type") == "shadowsocks" }
        val hysteria = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
            .first { it.optString("type") == "hysteria2" }

        assertEquals("trojan.example.com", trojan.getString("server"))
        assertEquals("trojan-pass", trojan.getString("password"))
        assertEquals("ss.example.com", shadowsocks.getString("server"))
        assertEquals("aes-128-gcm", shadowsocks.getString("method"))
        assertEquals("hy.example.com", hysteria.getString("server"))
        assertEquals("hy-pass", hysteria.getString("password"))
        assertTrue(hysteria.getJSONObject("tls").getJSONArray("alpn").toList().contains("h3"))
    }

    @Test
    fun extractsCurrentXrayHysteriaAsStandaloneOutbound() {
        val config = xrayConfig(
            """
            {
              "protocol": "hysteria",
              "tag": "hy2",
              "settings": {"version": 2, "address": "hy.example.com", "port": 443},
              "streamSettings": {
                "method": "hysteria",
                "security": "tls",
                "hysteriaSettings": {"version": 2, "auth": "hy-pass"},
                "tlsSettings": {"serverName": "hy-front.example.com"}
              }
            }
            """.trimIndent(),
        )

        val result = assertIs<SingBoxConverter.OutboundsResult.Ok>(
            SingBoxConverter.convertToOutbounds(config, "Hysteria profile"),
        )
        val outbound = result.outbounds.single()

        assertEquals("hysteria2", outbound.getString("type"))
        assertEquals("Hysteria profile", outbound.getString("tag"))
        assertEquals("hy.example.com", outbound.getString("server"))
        assertEquals("hy-pass", outbound.getString("password"))
    }

    @Test
    fun rejectsXhttpInsteadOfProducingInvalidSingBoxTransport() {
        val config = xrayConfig(
            """
            {
              "protocol": "vless",
              "settings": {
                "address": "xhttp.example.com",
                "port": 443,
                "id": "77777777-7777-4777-8777-777777777777"
              },
              "streamSettings": {"method": "xhttp", "security": "tls"}
            }
            """.trimIndent(),
        )

        assertIs<SingBoxConverter.Result.Unsupported>(SingBoxConverter.convert(config))
        assertIs<SingBoxConverter.OutboundsResult.Unsupported>(SingBoxConverter.convertToOutbounds(config))
    }

    private fun xrayConfig(outbound: String): String = JSONObject()
        .put("outbounds", org.json.JSONArray().put(JSONObject(outbound)))
        .toString()

    private fun proxyOutbound(config: JSONObject, type: String): JSONObject {
        val outbounds = config.getJSONArray("outbounds")
        return (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
            .first { it.optString("type") == type }
    }
}
