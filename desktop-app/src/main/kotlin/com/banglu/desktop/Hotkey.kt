package com.banglu.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/**
 * S49: system-wide hotkey — Cmd+Shift+B (macOS) / Ctrl+Shift+B (Win/Linux)
 * summons the mini converter over ANY app (Word, editors, chat clients).
 * macOS requires Accessibility permission (System Settings → Privacy &
 * Security → Accessibility); without it the hotkey silently stays off and
 * the tray/main window still work.
 */
object Hotkey {
    @Volatile var registered = false; private set

    fun register(onTrigger: () -> Unit) {
        runCatching {
            Logger.getLogger(GlobalScreen::class.java.packageName).apply {
                level = Level.OFF; useParentHandlers = false
            }
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
                override fun nativeKeyPressed(e: NativeKeyEvent) {
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
        }.onFailure { registered = false }
    }
}
