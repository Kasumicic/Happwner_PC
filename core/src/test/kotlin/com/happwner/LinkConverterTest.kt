package com.happwner

import com.happwner.compat.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkConverterTest {
    @Test
    fun modernXrayVlessKeepsDirectSettingsGrpcAndReality() {
        val config = """
            {
              "outbounds": [{
                "protocol": "vless",
                "tag": "Modern VLESS",
                "settings": {
                  "address": "modern.example.com",
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
                    "shortId": "a1b2c3d4",
                    "spiderX": "/index"
                  },
                  "grpcSettings": {
                    "serviceName": "vpn-service",
                    "authority": "front.example.com",
                    "multiMode": true
                  }
                }
              }]
            }
        """.trimIndent()

        val link = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)
        val query = parseQuery(link)

        assertTrue(link.startsWith("vless://"))
        assertEquals("modern.example.com", URI(link).host)
        assertEquals("Modern VLESS", URLDecoder.decode(URI(link).rawFragment, Charsets.UTF_8))
        assertEquals("grpc", query["type"])
        assertEquals("reality", query["security"])
        assertEquals("reality-public-key", query["pbk"])
        assertEquals("a1b2c3d4", query["sid"])
        assertEquals("vpn-service", query["serviceName"])
        assertEquals("front.example.com", query["authority"])
        assertEquals("multi", query["mode"])
    }

    @Test
    fun vmessKeepsGrpcAndTlsFieldsInLegacyShareJson() {
        val config = """
            {
              "outbounds": [{
                "protocol": "vmess",
                "tag": "VMess gRPC",
                "settings": {
                  "vnext": [{
                    "address": "vmess.example.com",
                    "port": 443,
                    "users": [{
                      "id": "22222222-2222-4222-8222-222222222222",
                      "security": "chacha20-poly1305"
                    }]
                  }]
                },
                "streamSettings": {
                  "network": "grpc",
                  "security": "tls",
                  "tlsSettings": {
                    "serverName": "front.example.com",
                    "fingerprint": "firefox",
                    "alpn": ["h2", "http/1.1"]
                  },
                  "grpcSettings": {
                    "serviceName": "vpn-service",
                    "authority": "grpc.example.com",
                    "multiMode": true
                  }
                }
              }]
            }
        """.trimIndent()

        val share = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)
        val json = decodeVmess(share)

        assertEquals("grpc", json.getString("net"))
        assertEquals("vpn-service", json.getString("path"))
        assertEquals("grpc.example.com", json.getString("host"))
        assertEquals("multi", json.getString("type"))
        assertEquals("tls", json.getString("tls"))
        assertEquals("front.example.com", json.getString("sni"))
        assertEquals("firefox", json.getString("fp"))
        assertEquals("h2,http/1.1", json.getString("alpn"))
        assertEquals("chacha20-poly1305", json.getString("scy"))
    }

    @Test
    fun modernXrayVmessDirectSettingsConvert() {
        val config = """
            {
              "outbounds": [{
                "protocol": "vmess",
                "tag": "Modern VMess",
                "settings": {
                  "address": "modern-vmess.example.com",
                  "port": 443,
                  "id": "66666666-6666-4666-8666-666666666666",
                  "security": "aes-128-gcm"
                },
                "streamSettings": {
                  "method": "websocket",
                  "security": "tls",
                  "tlsSettings": {"serverName": "front.example.com", "fingerprint": "chrome"},
                  "wsSettings": {"path": "/ws", "headers": {"host": "cdn.example.com"}}
                }
              }]
            }
        """.trimIndent()

        val share = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)
        val json = decodeVmess(share)

        assertEquals("modern-vmess.example.com", json.getString("add"))
        assertEquals("66666666-6666-4666-8666-666666666666", json.getString("id"))
        assertEquals("aes-128-gcm", json.getString("scy"))
        assertEquals("ws", json.getString("net"))
        assertEquals("/ws", json.getString("path"))
        assertEquals("cdn.example.com", json.getString("host"))
        assertEquals("front.example.com", json.getString("sni"))
    }

    @Test
    fun trojanKeepsRealityAndGrpcFields() {
        val config = """
            {
              "outbounds": [{
                "protocol": "trojan",
                "tag": "Trojan Reality",
                "settings": {
                  "address": "trojan.example.com",
                  "port": 443,
                  "password": "p@ss word"
                },
                "streamSettings": {
                  "method": "grpc",
                  "security": "reality",
                  "realitySettings": {
                    "serverName": "front.example.com",
                    "fingerprint": "chrome",
                    "password": "reality-public-key",
                    "shortId": "abcd1234",
                    "spiderX": "/"
                  },
                  "grpcSettings": {
                    "serviceName": "trojan-service",
                    "authority": "front.example.com"
                  }
                }
              }]
            }
        """.trimIndent()

        val link = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)
        val query = parseQuery(link)

        assertTrue(link.startsWith("trojan://p%40ss%20word@"))
        assertEquals("trojan.example.com", URI(link).host)
        assertEquals("reality", query["security"])
        assertEquals("reality-public-key", query["pbk"])
        assertEquals("abcd1234", query["sid"])
        assertEquals("chrome", query["fp"])
        assertEquals("trojan-service", query["serviceName"])
        assertEquals("front.example.com", query["authority"])
    }

    @Test
    fun singBoxOutboundsConvertWithoutLosingTlsAndObfuscation() {
        val input = """
            [{
              "type": "vless",
              "tag": "Sing-box VLESS",
              "server": "2001:db8::20",
              "server_port": 443,
              "uuid": "33333333-3333-4333-8333-333333333333",
              "flow": "xtls-rprx-vision",
              "transport": {
                "type": "ws",
                "path": "/socket",
                "headers": {"host": "cdn.example.com"}
              },
              "tls": {
                "enabled": true,
                "server_name": "front.example.com",
                "alpn": ["h2"],
                "utls": {"enabled": true, "fingerprint": "chrome"}
              }
            }, {
              "type": "hysteria2",
              "tag": "Sing-box Hysteria2",
              "server": "hy.example.com",
              "server_port": 8443,
              "password": "hy password",
              "obfs": {"type": "salamander", "password": "obfs secret"},
              "tls": {"enabled": true, "server_name": "hy-front.example.com", "insecure": true}
            }]
        """.trimIndent()

        val links = LinkConverter.convert(input, jsonToUri = true, tryBase64 = false).lines()
        val vlessQuery = parseQuery(links[0])
        val hysteriaQuery = parseQuery(links[1])

        assertEquals(2, links.size)
        assertEquals("[2001:db8::20]", URI(links[0]).host)
        assertEquals("ws", vlessQuery["type"])
        assertEquals("/socket", vlessQuery["path"])
        assertEquals("cdn.example.com", vlessQuery["host"])
        assertEquals("front.example.com", vlessQuery["sni"])
        assertEquals("chrome", vlessQuery["fp"])
        assertEquals("salamander", hysteriaQuery["obfs"])
        assertEquals("obfs secret", hysteriaQuery["obfs-password"])
        assertEquals("hy-front.example.com", hysteriaQuery["sni"])
        assertEquals("1", hysteriaQuery["insecure"])
    }

    @Test
    fun currentXrayShadowsocksAndHysteriaSchemasConvert() {
        val input = """
            {
              "outbounds": [{
                "protocol": "shadowsocks",
                "tag": "Modern SS",
                "settings": {
                  "address": "ss.example.com",
                  "port": 8388,
                  "method": "2022-blake3-aes-128-gcm",
                  "password": "YWJjZGVmZ2hpamtsbW5vcA=="
                }
              }, {
                "protocol": "hysteria",
                "tag": "Modern Hysteria",
                "settings": {"version": 2, "address": "hy.example.com", "port": 443},
                "streamSettings": {
                  "method": "hysteria",
                  "security": "tls",
                  "hysteriaSettings": {"version": 2, "auth": "auth secret"},
                  "tlsSettings": {"serverName": "hy-front.example.com", "alpn": ["h3"]}
                }
              }]
            }
        """.trimIndent()

        val links = LinkConverter.convert(input, jsonToUri = true, tryBase64 = false).lines()

        assertEquals(2, links.size)
        assertTrue(links[0].startsWith("ss://"))
        assertTrue(links[1].startsWith("hysteria2://auth%20secret@"))
        assertEquals("hy-front.example.com", parseQuery(links[1])["sni"])
        assertEquals("h3", parseQuery(links[1])["alpn"])
    }

    @Test
    fun legacyArraysProduceEveryServerAndUser() {
        val firstUser = JSONObject().put("id", "44444444-4444-4444-8444-444444444444")
        val secondUser = JSONObject().put("id", "55555555-5555-4555-8555-555555555555")
        val firstServer = JSONObject()
            .put("address", "first.example.com")
            .put("port", 443)
            .put("users", org.json.JSONArray().put(firstUser).put(secondUser))
        val secondServer = JSONObject()
            .put("address", "second.example.com")
            .put("port", 8443)
            .put("users", org.json.JSONArray().put(JSONObject(firstUser.toString())))
        val outbound = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", org.json.JSONArray().put(firstServer).put(secondServer)))
        val config = JSONObject().put("outbounds", org.json.JSONArray().put(outbound))

        val links = LinkConverter.convert(config.toString(), jsonToUri = true, tryBase64 = false).lines()

        assertEquals(3, links.size)
        assertEquals(listOf("first.example.com", "first.example.com", "second.example.com"), links.map { URI(it).host })
    }

    @Test
    fun malformedSupportedOutboundDoesNotSilentlyDisappear() {
        val valid = JSONObject(xrayConfig("tls", securitySettings = "\"tlsSettings\": {}"))
            .getJSONArray("outbounds").getJSONObject(0)
        val malformed = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("address", "missing-credentials.example.com").put("port", 443))
        val input = JSONObject().put("outbounds", org.json.JSONArray().put(valid).put(malformed)).toString(2)

        val result = LinkConverter.convert(input, jsonToUri = true, tryBase64 = false)

        assertTrue(result.startsWith("{"))
        assertTrue(result.contains("missing-credentials.example.com"))
        assertTrue(result.contains("server.example.com"))
    }

    @Test
    fun unsupportedProxyOutboundPreventsPartialJsonToUriResult() {
        val valid = JSONObject(xrayConfig("tls", securitySettings = "\"tlsSettings\": {}"))
            .getJSONArray("outbounds").getJSONObject(0)
        val wireguard = JSONObject()
            .put("protocol", "wireguard")
            .put("settings", JSONObject().put("secretKey", "must-not-be-dropped"))
        val input = JSONObject().put("outbounds", org.json.JSONArray().put(valid).put(wireguard)).toString()

        val result = LinkConverter.convert(input, jsonToUri = true, tryBase64 = false)

        assertTrue(result.startsWith("{"))
        assertTrue(result.contains("must-not-be-dropped"))
    }

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

    @Test
    fun vlessNormalizesLegacyFlowAndIdnHostForSharing() {
        val config = """
            {"outbounds":[{
              "protocol":"vless",
              "settings":{"address":"пример.рф","port":443,"id":"77777777-7777-4777-8777-777777777777","flow":"xtls-rprx-vision-udp443"}
            }]}
        """.trimIndent()

        val link = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)

        assertTrue(link.contains("@xn--e1afmkfd.xn--p1ai:443"))
        assertEquals("xtls-rprx-vision", parseQuery(link)["flow"])
    }

    @Test
    fun singBoxHysteria2SharesPortHoppingAndFixedEch() {
        val config = """
            {
              "type":"hysteria2",
              "tag":"Port hopping",
              "server":"hy.example.com",
              "server_ports":[443,"5000:6000"],
              "password":"secret",
              "tls":{"enabled":true,"server_name":"front.example.com","ech":{"enabled":true,"config":["BASE64-ECH"]}}
            }
        """.trimIndent()

        val link = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)
        val query = parseRawQuery(link)

        assertTrue(link.startsWith("hysteria2://secret@hy.example.com:443,5000-6000/"))
        assertEquals("front.example.com", query["sni"])
        assertEquals("BASE64-ECH", query["ech"])
    }

    @Test
    fun xrayHysteria2SharesCertificatePin() {
        val config = """
            {"outbounds":[{
              "protocol":"hysteria",
              "settings":{"version":2,"address":"hy.example.com","port":443},
              "streamSettings":{
                "method":"hysteria",
                "security":"tls",
                "hysteriaSettings":{"version":2,"auth":"secret"},
                "tlsSettings":{"serverName":"front.example.com","pinnedPeerCertSha256":"deadbeef"}
              }
            }]}
        """.trimIndent()

        val link = LinkConverter.convert(config, jsonToUri = true, tryBase64 = false)

        assertEquals("deadbeef", parseQuery(link)["pinSHA256"])
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
        return decodeQuery(rawQuery)
    }

    private fun parseRawQuery(link: String): Map<String, String> {
        val rawQuery = link.substringAfter('?', "").substringBefore('#')
        return decodeQuery(rawQuery)
    }

    private fun decodeQuery(rawQuery: String): Map<String, String> {
        return rawQuery.split('&').associate { part ->
            val (key, value) = part.split('=', limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }

    private fun decodeVmess(link: String): JSONObject {
        assertTrue(link.startsWith("vmess://"))
        val decoded = Base64.decode(link.removePrefix("vmess://"), Base64.DEFAULT)
        return JSONObject(decoded.toString(Charsets.UTF_8))
    }
}
