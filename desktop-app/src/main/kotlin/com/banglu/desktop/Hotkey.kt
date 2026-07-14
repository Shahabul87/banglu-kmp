package com.banglu.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tulskiy.keymaster.common.Provider
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * S49: system-wide hotkey — Cmd+Shift+B (macOS) / Ctrl+Shift+B (Win/Linux)
 * summons the mini converter over ANY app (Word, editors, chat clients).
 *
 * Registered through the OS-native single-hotkey APIs (Carbon
 * RegisterEventHotKey / Win32 RegisterHotKey / X11 XGrabKey) via jkeymaster —
 * these need NO privacy permissions on any platform, unlike a keyboard event
 * tap. (Auto-paste in Paste.kt still wants macOS Accessibility for the
 * synthesized ⌘V; without it the text stays on the clipboard for manual ⌘V.)
 */
object Hotkey {
    var registered by mutableStateOf(false); private set
    var lastError by mutableStateOf<String?>(null); private set

    fun register(onTrigger: () -> Unit) {
        Thread {
            try {
                val isMac = System.getProperty("os.name").lowercase().contains("mac")
                val mods = (if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK) or
                    InputEvent.SHIFT_DOWN_MASK
                val provider = Provider.getCurrentProvider(true) // listeners on the Swing EDT
                provider.register(KeyStroke.getKeyStroke(KeyEvent.VK_B, mods)) { onTrigger() }
                registered = true
                lastError = null
            } catch (t: Throwable) {
                lastError = t.message ?: t::class.simpleName
                System.err.println("Banglu hotkey registration failed: $t")
            }
        }.apply { isDaemon = true; name = "banglu-hotkey-register" }.start()
    }
}
