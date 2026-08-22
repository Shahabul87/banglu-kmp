package com.banglu.winime.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import com.banglu.winime.EditionInfo
import com.banglu.winime.Mode

/**
 * Renders [ControlWindow] on THIS machine so its design can be looked at.
 *
 * The app itself cannot run here — the keyboard hook is Win32 — but the window
 * is pure Compose with no JNA anywhere in its tree, so it renders on macOS
 * exactly as it will on Windows apart from the native title bar. Without this,
 * every visual decision would be guesswork verified only by shipping an
 * installer to the user, which is how the last two rounds went.
 *
 * `./gradlew :windows-ime:previewControlWindow`
 * `-PpreviewMode=english|off|loading|hookdown|update` to see the other states.
 */
fun main() {
    val variant = System.getProperty("banglu.preview.mode") ?: "bangla"
    application {
        var mode by remember {
            mutableStateOf(
                when (variant) {
                    "english" -> Mode.ENGLISH
                    "off" -> Mode.OFF
                    else -> Mode.BANGLA
                }
            )
        }
        ControlWindow(
            visible = true,
            showRequests = 0,
            mode = mode,
            bootStatus = "লোড হচ্ছে…",
            hookHealthy = variant != "hookdown",
            engineReady = variant != "loading",
            updateLine = if (variant == "update") {
                "নতুন সংস্করণ ১.০.২ পাওয়া গেছে — উন্নতি ও ত্রুটি সংশোধন।"
            } else {
                ""
            },
            updateActionable = variant == "update",
            updateBusy = false,
            // The real thing, so the preview shows which edition it was built
            // with: run the task with -PbangluStore=true to review the Store
            // build's window (no update row, Store label on the version line).
            versionLine = EditionInfo.line,
            onUpdate = {},
            onMode = { mode = it },
            onHide = { exitApplication() },
            onQuit = { exitApplication() },
        )
    }
}
