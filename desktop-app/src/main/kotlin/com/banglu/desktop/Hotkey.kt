package com.banglu.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/**
 * S49: system-wide hotkey — Cmd+Shift+B (macOS) / Ctrl+Shift+B (Win/Linux)
 * summons the mini converter over ANY app (Word, editors, chat clients).
 *
 * macOS needs BOTH Privacy & Security grants: Accessibility AND Input
 * Monitoring (the CGEventTap that listens for the chord requires the
 * latter since Catalina). Registration is retried on a daemon thread, so
 * granting a permission arms the hotkey within seconds — no app restart.
 * `registered`/`lastError` are Compose snapshot state so the hint updates.
 */
object Hotkey {
    var registered by mutableStateOf(false); private set
    // macOS quirk: the event tap can register WITHOUT permission and then
    // silently receive nothing — only a real key event proves the grant.
    var eventsSeen by mutableStateOf(false); private set
    var lastError by mutableStateOf<String?>(null); private set

    fun register(onTrigger: () -> Unit) {
        Logger.getLogger(GlobalScreen::class.java.packageName).apply {
            level = Level.OFF; useParentHandlers = false
        }
        Thread {
            while (!registered) {
                try {
                    GlobalScreen.registerNativeHook()
                    GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
                        override fun nativeKeyPressed(e: NativeKeyEvent) {
                            eventsSeen = true
                            val mods = e.modifiers
                            val meta = mods and (NativeKeyEvent.META_MASK or NativeKeyEvent.META_L_MASK or NativeKeyEvent.META_R_MASK) != 0
                            val ctrl = mods and (NativeKeyEvent.CTRL_MASK or NativeKeyEvent.CTRL_L_MASK or NativeKeyEvent.CTRL_R_MASK) != 0
                            val shift = mods and (NativeKeyEvent.SHIFT_MASK or NativeKeyEvent.SHIFT_L_MASK or NativeKeyEvent.SHIFT_R_MASK) != 0
                            val isMac = System.getProperty("os.name").lowercase().contains("mac")
                            val chord = if (isMac) meta && shift else ctrl && shift
                            if (chord && e.keyCode == NativeKeyEvent.VC_B) onTrigger()
                        }
                    })
                    registered = true
                    lastError = null
                } catch (t: Throwable) {
                    lastError = t.message ?: t::class.simpleName
                    System.err.println("Banglu hotkey registration failed: $t")
                    Thread.sleep(3000)
                }
            }
        }.apply { isDaemon = true; name = "banglu-hotkey-register" }.start()
    }
}
