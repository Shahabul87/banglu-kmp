package com.banglu.winime.hook

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.util.Locale

/**
 * The lowercase file name of the executable owning the focused window, e.g.
 * `"winword.exe"` — the key the per-app passthrough table is written in.
 *
 * [exeName] is called from the keyboard hook on every keystroke, so the
 * answer is cached against the window it was resolved for: the steady-state
 * cost is one cheap `GetForegroundWindow` plus a long comparison, and the
 * `OpenProcess`/`QueryFullProcessImageName` pair only runs when focus moves.
 *
 * Nothing here throws. A lookup that fails returns `""`, which the app-compat
 * table simply does not match — a failed query must never cost a keystroke.
 */
object ForegroundApp {

    private const val PATH_BUFFER_CHARS = 1024

    @Volatile
    private var cachedWindow: Long = 0L

    @Volatile
    private var cachedExe: String = ""

    fun exeName(): String {
        return try {
            val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return ""
            val handle = Pointer.nativeValue(hwnd.pointer)
            if (handle != 0L && handle == cachedWindow) return cachedExe
            val name = resolve(hwnd)
            // Order matters: publish the name before the key it is filed
            // under, so a concurrent reader can see a stale key but never a
            // fresh key paired with the previous app's name.
            cachedExe = name
            cachedWindow = handle
            name
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Forget the cached answer. Called when the foreground window changes,
     * because Windows recycles HWND values and a new process can be handed the
     * handle a dead one used to own.
     */
    fun invalidate() {
        cachedWindow = 0L
        cachedExe = ""
    }

    private fun resolve(hwnd: WinDef.HWND): String {
        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        val pid = pidRef.value
        if (pid == 0) return ""
        // QUERY_LIMITED_INFORMATION is the least privilege that still answers
        // QueryFullProcessImageName, and the only one that works against a
        // process running at a higher integrity level.
        val process: WinNT.HANDLE = Kernel32.INSTANCE
            .OpenProcess(WinNT.PROCESS_QUERY_LIMITED_INFORMATION, false, pid)
            ?: return ""
        try {
            val buffer = CharArray(PATH_BUFFER_CHARS)
            val length = IntByReference(buffer.size)
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, length)) return ""
            val written = length.value
            if (written <= 0 || written > buffer.size) return ""
            return basename(String(buffer, 0, written))
        } finally {
            Kernel32.INSTANCE.CloseHandle(process)
        }
    }

    private fun basename(path: String): String {
        val cut = maxOf(path.lastIndexOf('\\'), path.lastIndexOf('/'))
        return path.substring(cut + 1).lowercase(Locale.ROOT)
    }
}
