package com.happwner.desktop

import java.nio.file.Path
import kotlin.io.path.Path

object AppPaths {
    val configDirectory: Path by lazy {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> Path(System.getenv("APPDATA") ?: System.getProperty("user.home"), "HappwnerPC")
            else -> Path(System.getenv("XDG_CONFIG_HOME") ?: Path(System.getProperty("user.home"), ".config").toString(), "happwner-pc")
        }
    }

    val stateFile: Path get() = configDirectory.resolve("settings.properties")
}
