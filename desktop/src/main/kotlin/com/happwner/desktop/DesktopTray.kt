package com.happwner.desktop

import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import java.awt.BasicStroke
import java.awt.Color
import java.awt.EventQueue
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Native cross-platform tray. On Linux, Dorkbox selects AppIndicator and
 * exports the DBusMenu required by KDE Plasma instead of relying on XEmbed.
 */
class DesktopTray(
    private val tooltip: String,
    private val openLabel: String,
    private val exitLabel: String,
    private val onOpen: () -> Unit,
    private val onExit: () -> Unit,
) : AutoCloseable {
    private var tray: SystemTray? = null

    fun install(): Boolean {
        if (tray != null) return true
        val installed = SystemTray.get("Happwner-PC") ?: return false
        installed.setImage(createIcon())
        installed.setTooltip(tooltip)
        installed.menu.add(MenuItem(openLabel) { onUiThread(onOpen) })
        installed.menu.add(Separator())
        installed.menu.add(MenuItem(exitLabel) { onUiThread(onExit) })
        tray = installed
        return true
    }

    override fun close() {
        tray?.shutdown()
        tray = null
    }

    private fun createIcon(): BufferedImage = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().use { graphics ->
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(42, 98, 214)
            graphics.fillRoundRect(2, 2, 28, 28, 8, 8)
            graphics.color = Color.WHITE
            graphics.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            graphics.drawLine(9, 10, 23, 10)
            graphics.drawLine(9, 16, 23, 16)
            graphics.drawLine(9, 22, 23, 22)
            graphics.fillOval(6, 8, 4, 4)
            graphics.fillOval(6, 14, 4, 4)
            graphics.fillOval(6, 20, 4, 4)
        }
    }

    private fun onUiThread(action: () -> Unit) {
        EventQueue.invokeLater(action)
    }

    private fun <T : Graphics2D> T.use(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
