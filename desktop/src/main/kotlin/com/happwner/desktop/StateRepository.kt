package com.happwner.desktop

import com.happwner.BindMode
import com.happwner.ServerSettings
import com.happwner.StoredState
import com.happwner.Subscription
import com.happwner.ThemeMode
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class StateRepository {
    @Synchronized
    fun load(): StoredState {
        val file = AppPaths.stateFile
        if (!file.exists()) return StoredState()
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        val settings = ServerSettings(
            bindMode = runCatching { BindMode.valueOf(properties.getProperty("server.bindMode", "LOCAL")) }.getOrDefault(BindMode.LOCAL),
            lanAddress = properties.getProperty("server.lanAddress", ""),
            port = properties.getProperty("server.port")?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8166,
            serverEnabled = properties.getProperty("server.enabled", "true").toBoolean(),
            launchAtLogin = properties.getProperty("app.launchAtLogin", "false").toBoolean(),
            language = properties.getProperty("app.language", "ru"),
            themeMode = parseThemeMode(properties.getProperty("app.theme")),
        )
        val count = properties.getProperty("subscriptions.count")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val subscriptions = (0 until count).mapNotNull { index ->
            val prefix = "subscriptions.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            val source = properties.getProperty(prefix + "source") ?: return@mapNotNull null
            Subscription(
                id = id,
                name = properties.getProperty(prefix + "name", "Subscription ${index + 1}"),
                source = source,
                hwid = properties.getProperty(prefix + "hwid", ""),
                userAgent = properties.getProperty(prefix + "userAgent", "Happ/1.0"),
                enabled = properties.getProperty(prefix + "enabled", "true").toBoolean(),
                decodeBase64 = properties.getProperty(prefix + "decodeBase64", "false").toBoolean(),
                jsonToUri = properties.getProperty(prefix + "jsonToUri", "false").toBoolean(),
                xrayToSingBox = properties.getProperty(prefix + "xrayToSingBox", "false").toBoolean(),
            )
        }
        return StoredState(settings, subscriptions)
    }

    @Synchronized
    fun save(state: StoredState) {
        Files.createDirectories(AppPaths.configDirectory)
        val properties = Properties().apply {
            setProperty("server.bindMode", state.settings.bindMode.name)
            setProperty("server.lanAddress", state.settings.lanAddress)
            setProperty("server.port", state.settings.port.toString())
            setProperty("server.enabled", state.settings.serverEnabled.toString())
            setProperty("app.launchAtLogin", state.settings.launchAtLogin.toString())
            setProperty("app.language", state.settings.language)
            setProperty("app.theme", state.settings.themeMode.name)
            setProperty("subscriptions.count", state.subscriptions.size.toString())
            state.subscriptions.forEachIndexed { index, subscription ->
                val prefix = "subscriptions.$index."
                setProperty(prefix + "id", subscription.id)
                setProperty(prefix + "name", subscription.name)
                setProperty(prefix + "source", subscription.source)
                setProperty(prefix + "hwid", subscription.hwid)
                setProperty(prefix + "userAgent", subscription.userAgent)
                setProperty(prefix + "enabled", subscription.enabled.toString())
                setProperty(prefix + "decodeBase64", subscription.decodeBase64.toString())
                setProperty(prefix + "jsonToUri", subscription.jsonToUri.toString())
                setProperty(prefix + "xrayToSingBox", subscription.xrayToSingBox.toString())
            }
        }
        val temporary = AppPaths.stateFile.resolveSibling("settings.properties.tmp")
        temporary.outputStream().use { properties.store(it, "Happwner PC") }
        runCatching {
            Files.move(temporary, AppPaths.stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, AppPaths.stateFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

internal fun parseThemeMode(value: String?): ThemeMode =
    runCatching { ThemeMode.valueOf(value ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK)
