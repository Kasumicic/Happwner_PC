package com.happwner.desktop

import com.happwner.BindMode
import com.happwner.ServerSettings
import java.net.BindException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory

data class PortOwner(
    val pid: Long,
    val command: String?,
)

object PortDiagnostics {
    fun describe(error: Throwable, settings: ServerSettings): String {
        val language = settings.language
        val message = error.causes()
            .mapNotNull { it.message }
            .firstOrNull()
            .orEmpty()
        val address = when (settings.bindMode) {
            BindMode.LOCAL -> "127.0.0.1"
            BindMode.LAN -> settings.lanAddress.ifBlank { "0.0.0.0" }
        }

        if (message.contains("Cannot assign requested address", ignoreCase = true)) {
            return if (language == "en") {
                "Network address $address is no longer available. Select another LAN interface or automatic mode."
            } else {
                "Сетевой адрес $address больше недоступен. Выберите другой LAN-интерфейс или автоматический режим."
            }
        }

        val addressInUse = error.causes().any { it is BindException } ||
            message.contains("Address already in use", ignoreCase = true) ||
            message.contains("Only one usage of each socket address", ignoreCase = true)
        if (addressInUse) {
            val owner = findOwner(settings.port)
            return occupiedPortMessage(settings.port, owner, language)
        }

        return if (language == "en") {
            "Could not start the server on $address:${settings.port}: ${message.ifBlank { error.javaClass.simpleName }}"
        } else {
            "Не удалось запустить сервер на $address:${settings.port}: ${message.ifBlank { error.javaClass.simpleName }}"
        }
    }

    internal fun occupiedPortMessage(port: Int, owner: PortOwner?, language: String): String {
        val process = owner?.let {
            val name = it.command?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf(String::isNotBlank)
            if (name == null) "PID ${it.pid}" else "$name (PID ${it.pid})"
        }
        return if (language == "en") {
            if (process == null) {
                "Port $port is already in use. The owning process could not be identified; choose another port."
            } else {
                "Port $port is already in use by $process. Close it or choose another port."
            }
        } else {
            if (process == null) {
                "Порт $port уже занят. Определить процесс не удалось — выберите другой порт."
            } else {
                "Порт $port уже занят процессом $process. Закройте его или выберите другой порт."
            }
        }
    }

    private fun findOwner(port: Int): PortOwner? = when {
        System.getProperty("os.name").startsWith("Linux", ignoreCase = true) -> findLinuxOwner(port)
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> findWindowsOwner(port)
        else -> null
    }

    private fun findLinuxOwner(port: Int): PortOwner? = runCatching {
        val inodes = sequenceOf("/proc/net/tcp", "/proc/net/tcp6")
            .flatMap { file ->
                runCatching { Files.readAllLines(Path.of(file)).asSequence() }.getOrDefault(emptySequence())
            }
            .mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                val localPort = fields.getOrNull(1)
                    ?.substringAfterLast(':')
                    ?.toIntOrNull(16)
                val listening = fields.getOrNull(3) == "0A"
                fields.getOrNull(9)?.takeIf { listening && localPort == port }
            }
            .toSet()
        if (inodes.isEmpty()) return@runCatching null

        var result: PortOwner? = null
        Files.list(Path.of("/proc")).use { processes ->
            processes
                .filter { it.isDirectory() && it.fileName.toString().all(Char::isDigit) }
                .forEach { process ->
                    if (result == null && processOwnsSocket(process, inodes)) {
                        val pid = process.fileName.toString().toLong()
                        val command = ProcessHandle.of(pid)
                            .flatMap { it.info().command() }
                            .orElseGet {
                                runCatching {
                                    Files.readString(process.resolve("comm")).trim()
                                }.getOrNull()
                            }
                        result = PortOwner(pid, command)
                    }
                }
        }
        result
    }.getOrNull()

    private fun processOwnsSocket(process: Path, inodes: Set<String>): Boolean = runCatching {
        Files.list(process.resolve("fd")).use { descriptors ->
            descriptors.anyMatch { descriptor ->
                val target = runCatching { Files.readSymbolicLink(descriptor).toString() }.getOrNull()
                target?.startsWith("socket:[") == true &&
                    target.removePrefix("socket:[").removeSuffix("]") in inodes
            }
        }
    }.getOrDefault(false)

    private fun findWindowsOwner(port: Int): PortOwner? = runCatching {
        val process = ProcessBuilder("netstat", "-ano", "-p", "tcp")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        val pid = process.inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { parseWindowsNetstatLine(it, port) }.firstOrNull()
        } ?: return@runCatching null
        val command = ProcessHandle.of(pid).flatMap { it.info().command() }.orElse(null)
        PortOwner(pid, command)
    }.getOrNull()

    internal fun parseWindowsNetstatLine(line: String, port: Int): Long? {
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.size < 5 || !fields[0].equals("TCP", ignoreCase = true)) return null
        if (!fields[3].equals("LISTENING", ignoreCase = true)) return null
        val localPort = fields[1].substringAfterLast(':').toIntOrNull() ?: return null
        return fields[4].toLongOrNull()?.takeIf { localPort == port }
    }

    private fun Throwable.causes(): Sequence<Throwable> =
        generateSequence(this) { current -> current.cause?.takeUnless { it === current } }
}
