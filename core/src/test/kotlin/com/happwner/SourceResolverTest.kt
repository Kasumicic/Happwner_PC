package com.happwner

import java.net.URLEncoder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SourceResolverTest {
    @Test
    fun resolvesDirectHttpUrl() {
        val result = assertIs<SourceResolver.Result.Success>(SourceResolver.resolve(" https://example.com/sub "))
        assertEquals("https://example.com/sub", result.url)
    }

    @Test
    fun resolvesHappAddUrl() {
        val upstream = "https://example.com/sub?a=1&b=2"
        val encoded = URLEncoder.encode(upstream, Charsets.UTF_8)
        val result = assertIs<SourceResolver.Result.Success>(SourceResolver.resolve("happ://add/$encoded"))
        assertEquals(upstream, result.url)
    }

    @Test
    fun resolvesIncyImportBase64Url() {
        val upstream = "https://example.com/sub"
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(upstream.toByteArray())
        val result = assertIs<SourceResolver.Result.Success>(SourceResolver.resolve("incy://import/$encoded"))
        assertEquals(upstream, result.url)
    }

    @Test
    fun rejectsNonSubscriptionPayload() {
        assertIs<SourceResolver.Result.Error>(SourceResolver.resolve("vmess://profile"))
    }

    @Test
    fun wrappedStaticProfileCanBeServed() {
        val result = assertIs<SourceResolver.Result.Static>(SourceResolver.resolve("incy://add/vless://profile"))
        assertEquals("vless://profile", result.content)
    }
}
