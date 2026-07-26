package com.happwner

import java.util.UUID

const val DEFAULT_USER_AGENT = "Happ/3.26.1"

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val source: String,
    val hwid: String = "",
    val userAgent: String = DEFAULT_USER_AGENT,
    val enabled: Boolean = true,
    val decodeBase64: Boolean = true,
    val jsonToUri: Boolean = false,
    val xrayToSingBox: Boolean = false,
)

enum class BindMode { LOCAL, LAN }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class ServerSettings(
    val bindMode: BindMode = BindMode.LOCAL,
    val lanAddress: String = "",
    val port: Int = 8166,
    val serverEnabled: Boolean = true,
    val launchAtLogin: Boolean = false,
    val language: String = "ru",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val lastHwid: String = "",
)

data class StoredState(
    val settings: ServerSettings = ServerSettings(),
    val subscriptions: List<Subscription> = emptyList(),
)
