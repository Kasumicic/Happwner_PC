package com.happwner

import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val source: String,
    val hwid: String = "",
    val userAgent: String = "Happ/1.0",
    val enabled: Boolean = true,
    val decodeBase64: Boolean = false,
    val jsonToUri: Boolean = false,
    val xrayToSingBox: Boolean = false,
)

enum class BindMode { LOCAL, LAN }

data class ServerSettings(
    val bindMode: BindMode = BindMode.LOCAL,
    val port: Int = 8166,
    val serverEnabled: Boolean = true,
    val launchAtLogin: Boolean = false,
    val language: String = "ru",
)

data class StoredState(
    val settings: ServerSettings = ServerSettings(),
    val subscriptions: List<Subscription> = emptyList(),
)
