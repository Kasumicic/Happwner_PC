package com.happwner.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

object AutostartManager {
    fun setEnabled(enabled: Boolean) {
        val target = targetFile()
        if (!enabled) {
            target.deleteIfExists()
            return
        }
        Files.createDirectories(target.parent)
        val command = ProcessHandle.current().info().command().orElseThrow {
            IllegalStateException("Не удалось определить путь к приложению")
        }
        if (isWindows()) {
            target.writeText("@echo off\r\nstart \"\" \"$command\" --minimized\r\n")
        } else {
            val escaped = command.replace("\\", "\\\\").replace("\"", "\\\"")
            target.writeText(
                """[Desktop Entry]
Type=Application
Name=Happwner PC
Exec="$escaped" --minimized
Terminal=false
X-GNOME-Autostart-enabled=true
""",
            )
        }
    }

    private fun targetFile(): Path = if (isWindows()) {
        Path(
            System.getenv("APPDATA") ?: System.getProperty("user.home"),
            "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "Happwner PC.cmd",
        )
    } else {
        Path(System.getenv("XDG_CONFIG_HOME") ?: Path(System.getProperty("user.home"), ".config").toString(), "autostart", "happwner-pc.desktop")
    }

    private fun isWindows() = System.getProperty("os.name").contains("win", ignoreCase = true)
}
