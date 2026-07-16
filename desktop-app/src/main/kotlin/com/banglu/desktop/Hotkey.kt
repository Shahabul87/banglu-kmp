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
 *
 * S53 (Effortless On): the tray exposes a live on/off toggle — [setEnabled]
 * unregisters/re-registers the chord so users control the global hotkey from
 * the bottom-right corner without settings archaeology.
 */
object Hotkey {
    var registered by mutableStateOf(false); private set
    var lastError by mutableStateOf<String?>(null); private set

    private var provider: Provider? = null
    private var stroke: KeyStroke? = null
    private var trigger: (() -> Unit)? = null

    fun register(onTrigger: () -> Unit) {
        trigger = onTrigger
        Thread {
            try {
                val isMac = System.getProperty("os.name").lowercase().contains("mac")
                val mods = (if (isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK) or
                    InputEvent.SHIFT_DOWN_MASK
                val ks = KeyStroke.getKeyStroke(KeyEvent.VK_B, mods)
                val p = Provider.getCurrentProvider(true) // listeners on the Swing EDT
                p.register(ks) { trigger?.invoke() }
                provider = p
                stroke = ks
                registered = true
                lastError = null
            } catch (t: Throwable) {
                lastError = t.message ?: t::class.simpleName
                System.err.println("Banglu hotkey registration failed: $t")
            }
        }.apply { isDaemon = true; name = "banglu-hotkey-register" }.start()
    }

    /** Tray toggle: true re-arms the chord, false releases it OS-wide. */
    fun setEnabled(enabled: Boolean) {
        val p = provider ?: return
        val ks = stroke ?: return
        Thread {
            try {
                if (enabled && !registered) {
                    p.register(ks) { trigger?.invoke() }
                    registered = true
                } else if (!enabled && registered) {
                    p.reset()          // releases every chord this provider owns (ours: one)
                    registered = false
                }
                lastError = null
            } catch (t: Throwable) {
                lastError = t.message ?: t::class.simpleName
                System.err.println("Banglu hotkey toggle failed: $t")
            }
        }.apply { isDaemon = true; name = "banglu-hotkey-toggle" }.start()
    }
}
