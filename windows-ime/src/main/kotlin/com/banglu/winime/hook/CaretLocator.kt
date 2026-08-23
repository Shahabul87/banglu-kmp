package com.banglu.winime.hook

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary

/**
 * `ClientToScreen` is not among the functions `jna-platform`'s `User32`
 * declares, so it is bound here rather than reimplemented. Loaded without an
 * options map on purpose: the W32 unicode function mapper would look for
 * `ClientToScreenW`, which user32.dll does not export.
 */
private interface CaretUser32 : StdCallLibrary {
    @Suppress("FunctionName")
    fun ClientToScreen(hWnd: WinDef.HWND, lpPoint: WinDef.POINT): Boolean

    companion object {
        // `by lazy` keeps the native load off class initialisation, so merely
        // touching this file on a non-Windows machine does nothing.
        val INSTANCE: CaretUser32 by lazy {
            Native.load("user32", CaretUser32::class.java)
        }
    }
}

/**
 * Where the text caret is on screen, so the preview window can sit under the
 * word being typed instead of floating somewhere unrelated.
 *
 * Returns `null` for "unknown", which is the common case: only applications
 * that use the real Win32 caret report one at all — Chrome, Electron apps and
 * most custom text editors draw their own and are invisible to
 * `GetGUIThreadInfo`. The caller falls back to the mouse cursor; deciding that
 * is not this object's job.
 */
object CaretLocator {

    fun caretScreenPos(): Pair<Int, Int>? {
        return try {
            val user32 = User32.INSTANCE
            val foreground = user32.GetForegroundWindow() ?: return null
            // GUI thread info is per input thread, not per window: the caret
            // belongs to the thread that owns the focused window.
            val threadId = user32.GetWindowThreadProcessId(foreground, null)
            if (threadId == 0) return null
            val info = WinUser.GUITHREADINFO()
            // cbSize is a version tag; GetGUIThreadInfo fails outright if the
            // struct does not declare its own size.
            info.cbSize = info.size()
            if (!user32.GetGUIThreadInfo(threadId, info)) return null
            val caretWindow = info.hwndCaret ?: return null
            val rect = info.rcCaret
            // An all-zero rect is what a thread with no live caret reports;
            // treating it as a real position would pin the preview to the
            // window's top-left corner.
            if (rect.left == 0 && rect.top == 0 && rect.right == 0 && rect.bottom == 0) return null
            // rcCaret is in the caret window's client coordinates. Bottom, not
            // top: the preview hangs below the caret.
            val point = WinDef.POINT(rect.left, rect.bottom)
            if (!CaretUser32.INSTANCE.ClientToScreen(caretWindow, point)) return null
            point.x to point.y
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * The focused window's rectangle in physical screen pixels, or null —
     * S132's fallback anchor when no caret exists AND the mouse is parked
     * outside the window (the taskbar screenshot). Returned as
     * [left, top, right, bottom] so the ui layer never sees a JNA type.
     */
    fun foregroundWindowRect(): IntArray? {
        return try {
            val user32 = User32.INSTANCE
            val foreground = user32.GetForegroundWindow() ?: return null
            val rect = WinDef.RECT()
            if (!user32.GetWindowRect(foreground, rect)) return null
            intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
        } catch (_: Throwable) {
            null
        }
    }
}
