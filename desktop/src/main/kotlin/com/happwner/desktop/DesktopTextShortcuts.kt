package com.happwner.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference

internal enum class DesktopTextShortcut { COPY, PASTE, CUT, SELECT_ALL }

internal data class DesktopTextShortcutResult(
    val value: TextFieldValue,
    val clipboardWrite: String? = null,
)

internal fun applyDesktopTextShortcut(
    shortcut: DesktopTextShortcut,
    value: TextFieldValue,
    clipboardRead: String? = null,
): DesktopTextShortcutResult? {
    val start = value.selection.min
    val end = value.selection.max
    return when (shortcut) {
        DesktopTextShortcut.COPY -> DesktopTextShortcutResult(
            value = value,
            clipboardWrite = value.text.substring(start, end).takeIf { start != end },
        )
        DesktopTextShortcut.CUT -> {
            if (start == end) {
                DesktopTextShortcutResult(value)
            } else {
                DesktopTextShortcutResult(
                    value = TextFieldValue(value.text.removeRange(start, end), TextRange(start)),
                    clipboardWrite = value.text.substring(start, end),
                )
            }
        }
        DesktopTextShortcut.PASTE -> clipboardRead?.let {
            val updated = value.text.replaceRange(start, end, it)
            DesktopTextShortcutResult(
                value = TextFieldValue(updated, TextRange(start + it.length)),
            )
        }
        DesktopTextShortcut.SELECT_ALL -> DesktopTextShortcutResult(
            value = value.copy(selection = TextRange(0, value.text.length)),
        )
    }
}

internal class DesktopTextEditor {
    @Volatile
    var execute: (DesktopTextShortcut) -> Boolean = { false }
}

internal object DesktopTextShortcutDispatcher {
    private val focusedEditor = AtomicReference<DesktopTextEditor?>()
    private val keyDispatcher = KeyEventDispatcher { event ->
        if (
            event.id != KeyEvent.KEY_PRESSED ||
            !event.isControlDown ||
            event.isAltDown ||
            event.isMetaDown
        ) {
            return@KeyEventDispatcher false
        }
        val shortcut = when {
            event.keyCode == KeyEvent.VK_C || event.keyChar.code == 3 -> DesktopTextShortcut.COPY
            event.keyCode == KeyEvent.VK_V || event.keyChar.code == 22 -> DesktopTextShortcut.PASTE
            event.keyCode == KeyEvent.VK_X || event.keyChar.code == 24 -> DesktopTextShortcut.CUT
            event.keyCode == KeyEvent.VK_A || event.keyChar.code == 1 -> DesktopTextShortcut.SELECT_ALL
            else -> return@KeyEventDispatcher false
        }
        focusedEditor.get()?.execute?.invoke(shortcut) ?: false
    }

    fun install(): AutoCloseable {
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        manager.addKeyEventDispatcher(keyDispatcher)
        return AutoCloseable {
            focusedEditor.set(null)
            manager.removeKeyEventDispatcher(keyDispatcher)
        }
    }

    fun focus(editor: DesktopTextEditor) {
        focusedEditor.set(editor)
    }

    fun blur(editor: DesktopTextEditor) {
        focusedEditor.compareAndSet(editor, null)
    }
}

internal fun Modifier.desktopTextShortcuts(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
): Modifier = composed {
    val editor = remember { DesktopTextEditor() }
    SideEffect {
        editor.execute = handler@ { shortcut ->
            val clipboardRead = if (shortcut == DesktopTextShortcut.PASTE) readSystemClipboard() else null
            val result = applyDesktopTextShortcut(shortcut, value, clipboardRead)
                ?: return@handler false
            if (result.clipboardWrite != null && !writeSystemClipboard(result.clipboardWrite)) {
                return@handler false
            }
            if (result.value != value) onValueChange(result.value)
            true
        }
    }
    DisposableEffect(editor) {
        onDispose { DesktopTextShortcutDispatcher.blur(editor) }
    }
    onFocusChanged {
        if (it.hasFocus) {
            DesktopTextShortcutDispatcher.focus(editor)
        } else {
            DesktopTextShortcutDispatcher.blur(editor)
        }
    }
}

internal fun writeSystemClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}.isSuccess

private fun readSystemClipboard(): String? = runCatching {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
        clipboard.getData(DataFlavor.stringFlavor) as? String
    } else {
        null
    }
}.getOrNull()
