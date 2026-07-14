package com.banglu.desktop

import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent

/**
 * S49: "convert & paste" — copy the Bangla, give focus back to whatever app
 * the user was in (our window hides first), then synthesize the OS paste
 * chord. The text-expander pattern: works in Word, editors, browsers —
 * anywhere that accepts paste. macOS needs Accessibility for key synthesis.
 */
object Paste {
    private val isMac = System.getProperty("os.name").lowercase().contains("mac")

    fun copyThenPaste(text: String, afterHideDelayMs: Long = 220) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        Thread {
            runCatching {
                Thread.sleep(afterHideDelayMs) // focus returns to previous app
                val r = Robot()
                val mod = if (isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
                r.keyPress(mod); r.keyPress(KeyEvent.VK_V)
                r.keyRelease(KeyEvent.VK_V); r.keyRelease(mod)
            }
        }.start()
    }
}
