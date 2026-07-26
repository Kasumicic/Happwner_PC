package com.happwner.desktop

import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.freedesktop.dbus.Struct
import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

class DesktopTray(
    private val tooltip: String,
    private val openLabel: String,
    private val exitLabel: String,
    private val onOpen: () -> Unit,
    private val onExit: () -> Unit,
) : AutoCloseable {
    private var backend: AutoCloseable? = null

    fun install(): Boolean {
        if (backend != null) return true
        val installed = if (System.getProperty("os.name").lowercase().contains("linux")) {
            runCatching { LinuxStatusNotifier(tooltip, openLabel, exitLabel, onOpen, onExit).apply { install() } }
                .onFailure { System.err.println("Happwner tray: ${it.message}") }
                .getOrNull()
        } else {
            runCatching { AwtTray(tooltip, openLabel, exitLabel, onOpen, onExit).apply { install() } }.getOrNull()
        }
        backend = installed
        return installed != null
    }

    override fun close() {
        backend?.close()
        backend = null
    }
}

private class LinuxStatusNotifier(
    private val title: String,
    openLabel: String,
    exitLabel: String,
    onOpen: () -> Unit,
    onExit: () -> Unit,
) : AutoCloseable {
    private val serviceName = "org.freedesktop.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
    private val item = StatusNotifierItemObject(title) { onUiThread(onOpen) }
    private val menu = DBusMenuObject(openLabel, exitLabel, onOpen, onExit)
    private var connection: DBusConnection? = null

    fun install() {
        val connected = DBusConnectionBuilder.forSessionBus().build()
        try {
            connected.requestBusName(serviceName)
            connected.exportObject(STATUS_NOTIFIER_PATH, item)
            connected.exportObject(MENU_PATH, menu)
            val watcher = connected.getRemoteObject(
                "org.kde.StatusNotifierWatcher",
                "/StatusNotifierWatcher",
                StatusNotifierWatcher::class.java,
            )
            watcher.RegisterStatusNotifierItem(serviceName)
            connection = connected
        } catch (error: Exception) {
            connected.close()
            throw error
        }
    }

    override fun close() {
        connection?.close()
        connection = null
    }

    private fun onUiThread(action: () -> Unit) = EventQueue.invokeLater(action)
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    @DBusBoundProperty(name = "Category")
    fun getCategory(): String

    @DBusBoundProperty(name = "Id")
    fun getId(): String

    @DBusBoundProperty(name = "Title")
    fun getTitle(): String

    @DBusBoundProperty(name = "Status")
    fun getStatus(): String

    @DBusBoundProperty(name = "IconName")
    fun getIconName(): String

    @DBusBoundProperty(name = "IconPixmap")
    fun getIconPixmap(): Array<StatusNotifierIconPixmap>

    @DBusBoundProperty(name = "Menu")
    fun getMenu(): DBusPath

    @DBusBoundProperty(name = "ItemIsMenu")
    fun getItemIsMenu(): Boolean

    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)
}

class StatusNotifierItemObject(
    private val title: String,
    private val onActivate: () -> Unit,
) : StatusNotifierItem {
    override fun getCategory() = "ApplicationStatus"
    override fun getId() = "Happwner-PC"
    override fun getTitle() = title
    override fun getStatus() = "Active"
    override fun getIconName() = "happwner-pc"
    override fun getIconPixmap() = intArrayOf(22, 32, 64).map { size ->
        StatusNotifierIconPixmap(size, size, AppIcon.statusNotifierPixels(size))
    }.toTypedArray()
    override fun getMenu() = DBusPath(MENU_PATH)
    override fun getItemIsMenu() = false
    override fun Activate(x: Int, y: Int) = onActivate()
    override fun SecondaryActivate(x: Int, y: Int) = onActivate()
    override fun ContextMenu(x: Int, y: Int) = Unit
    override fun Scroll(delta: Int, orientation: String) = Unit
    override fun isRemote() = false
    override fun getObjectPath() = STATUS_NOTIFIER_PATH
}

@DBusInterfaceName("com.canonical.dbusmenu")
interface DBusMenu : DBusInterface {
    @DBusBoundProperty(name = "Version")
    fun getVersion(): UInt32

    @DBusBoundProperty(name = "Status")
    fun getStatus(): String

    @DBusBoundProperty(name = "TextDirection")
    fun getTextDirection(): String

    @DBusBoundProperty(name = "IconThemePath")
    fun getIconThemePath(): Array<String>

    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: Array<String>): DBusTuple2<UInt32, MenuLayout>
    fun GetGroupProperties(ids: IntArray, propertyNames: Array<String>): Array<MenuProperties>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: Array<MenuEvent>): IntArray
    fun AboutToShow(id: Int): Boolean
    fun AboutToShowGroup(ids: IntArray): DBusTuple2<IntArray, IntArray>
}

class DBusMenuObject(
    private val openLabel: String,
    private val exitLabel: String,
    private val onOpen: () -> Unit,
    private val onExit: () -> Unit,
) : DBusMenu {
    override fun getVersion() = UInt32(3)
    override fun getStatus() = "normal"
    override fun getTextDirection() = "ltr"
    override fun getIconThemePath() = emptyArray<String>()

    override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: Array<String>) =
        DBusTuple2(UInt32(1), layout(parentId))

    override fun GetGroupProperties(ids: IntArray, propertyNames: Array<String>) =
        ids.map { id -> properties(id)?.let { MenuProperties(id, it) } }.filterNotNull().toTypedArray()

    override fun GetProperty(id: Int, name: String): Variant<*> =
        properties(id)?.get(name) ?: Variant("")

    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        if (eventId != "clicked") return
        when (id) {
            OPEN_ID -> EventQueue.invokeLater(onOpen)
            EXIT_ID -> EventQueue.invokeLater(onExit)
        }
    }

    override fun EventGroup(events: Array<MenuEvent>): IntArray {
        events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
        return intArrayOf()
    }

    override fun AboutToShow(id: Int) = false
    override fun AboutToShowGroup(ids: IntArray) = DBusTuple2(intArrayOf(), intArrayOf())
    override fun isRemote() = false
    override fun getObjectPath() = MENU_PATH

    private fun layout(id: Int): MenuLayout = when (id) {
        OPEN_ID, SEPARATOR_ID, EXIT_ID -> MenuLayout(id, properties(id).orEmpty(), emptyArray())
        else -> MenuLayout(
            0,
            mapOf("children-display" to Variant("submenu")),
            arrayOf(OPEN_ID, SEPARATOR_ID, EXIT_ID).map { Variant(layout(it)) }.toTypedArray(),
        )
    }

    private fun properties(id: Int): Map<String, Variant<*>>? = when (id) {
        OPEN_ID -> mapOf("label" to Variant(openLabel), "enabled" to Variant(true))
        SEPARATOR_ID -> mapOf("type" to Variant("separator"), "enabled" to Variant(true))
        EXIT_ID -> mapOf("label" to Variant(exitLabel), "enabled" to Variant(true))
        else -> null
    }
}

class MenuLayout(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @Position(2) @JvmField val children: Array<Variant<*>>,
) : Struct()

class DBusTuple2<A, B>(
    @Position(0) @JvmField val first: A,
    @Position(1) @JvmField val second: B,
) : Tuple()

class MenuProperties(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

class MenuEvent(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val eventId: String,
    @Position(2) @JvmField val data: Variant<*>,
    @Position(3) @JvmField val timestamp: UInt32,
) : Struct()

class StatusNotifierIconPixmap(
    @Position(0) @JvmField val width: Int,
    @Position(1) @JvmField val height: Int,
    @Position(2) @JvmField val data: ByteArray,
) : Struct()

private class AwtTray(
    private val tooltip: String,
    private val openLabel: String,
    private val exitLabel: String,
    private val onOpen: () -> Unit,
    private val onExit: () -> Unit,
) : AutoCloseable {
    private var icon: TrayIcon? = null

    fun install() {
        check(SystemTray.isSupported()) { "System tray is unavailable" }
        val popup = PopupMenu().apply {
            add(MenuItem(openLabel).apply { addActionListener { onOpen() } })
            addSeparator()
            add(MenuItem(exitLabel).apply { addActionListener { onExit() } })
        }
        icon = TrayIcon(AppIcon.image(32), tooltip, popup).apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
            SystemTray.getSystemTray().add(this)
        }
    }

    override fun close() {
        icon?.let(SystemTray.getSystemTray()::remove)
        icon = null
    }
}

private const val STATUS_NOTIFIER_PATH = "/StatusNotifierItem"
private const val MENU_PATH = "/MenuBar"
private const val OPEN_ID = 1
private const val SEPARATOR_ID = 2
private const val EXIT_ID = 3
