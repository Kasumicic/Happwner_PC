package com.happwner.desktop

import com.happwner.BindMode
import com.happwner.ServerSettings
import com.happwner.Subscription
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticReportTest {
    @Test
    fun reportMasksClientAndDoesNotContainSubscriptionSecrets() {
        val source = "https://provider.example/sub/secret"
        val hwid = "very-secret-hwid"
        val subscription = Subscription(name = "Home", source = source, hwid = hwid)
        val request = SubscriptionRequestRecord.failure(
            message = "Failed to load $source for $hwid and 8c5a84c8-4551-4cbf-bbb6-26ded1170c4a",
            subscription = subscription,
            servedStatusCode = 502,
            clientAddress = "192.168.1.25",
            durationMillis = 42,
        )

        val report = DiagnosticReport.create(
            settings = ServerSettings(bindMode = BindMode.LAN, port = 8166),
            serverRunning = true,
            activities = listOf(SubscriptionActivity(subscription.id, subscription.name, request)),
            language = "en",
        )

        assertTrue("192.168.1.x" in report)
        assertTrue("[source hidden]" in report)
        assertTrue("[HWID hidden]" in report)
        assertTrue("[ID hidden]" in report)
        assertFalse(source in report)
        assertFalse(hwid in report)
        assertFalse(subscription.id in report)
    }

    @Test
    fun successfulReportContainsOnlyOperationalDetails() {
        val record = SubscriptionRequestRecord(
            completedAtMillis = 1_700_000_000_000,
            servedStatusCode = 200,
            sizeBytes = 2048,
            profileCount = 2,
            protocols = mapOf("vless" to 2),
            durationMillis = 15,
            clientAddress = "127.0.0.1",
            transformations = listOf("Base64"),
        )

        val report = DiagnosticReport.create(
            settings = ServerSettings(),
            serverRunning = true,
            activities = listOf(SubscriptionActivity("private-id", "Test", record)),
            language = "ru",
        )

        assertTrue("HTTP 200" in report)
        assertTrue("профили: 2" in report)
        assertTrue("vless:2" in report)
        assertTrue("loopback" in report)
        assertFalse("private-id" in report)
    }
}
