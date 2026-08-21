package com.banglu.winime

/**
 * The OS-free ports between the Win32 layer and everything else. Task 6
 * implements [KeySource]/[TextInjector] with JNA; Task 7 renders from
 * [ControllerListener]. Nothing in this file may know a VK code, an HWND, or
 * a JNA type — that isolation is what lets the whole typing pipeline be
 * tested on a Mac.
 */

/** Everything the hook layer reports — already classified, no VK codes here. */
sealed interface RawKey {
    data class Letter(val c: Char) : RawKey // a-z (lowercased by the hook)
    data class Digit(val c: Char) : RawKey // 0-9
    data class Punct(val p: String) : RawKey // . , ? !
    data object Space : RawKey
    data object Backspace : RawKey
    data object Enter : RawKey
    data object Tab : RawKey
    data object Escape : RawKey
    data object ToggleHotkey : RawKey // Ctrl+Space, pre-detected by the hook
    data object FocusChanged : RawKey // foreground window changed
    data class Unmanaged(val vk: Int) : RawKey // everything else
}

enum class Mode { OFF, BANGLA, ENGLISH }

/** The low-level keyboard hook (Task 6). */
interface KeySource {
    fun start(sink: HookSink)
    fun stop()
}

/** Called ON the hook thread. Must return the swallow decision immediately. */
interface HookSink {
    /** true = swallow (we own this key); false = pass to the app untouched. */
    fun onKey(key: RawKey, foregroundExe: String): Boolean
}

/** SendInput (Task 6). Only ever called from the controller's worker thread. */
interface TextInjector {
    /** KEYEVENTF_UNICODE, tagged so the hook ignores what we type ourselves. */
    fun injectText(text: String)

    /** Synthetic key event for a `ForwardKey` action. */
    fun injectKey(key: RawKey)
}

/** The tray + preview window (Task 7). Called from the worker thread. */
interface ControllerListener {
    /** `"" , ""` with no candidates = hide the preview window. */
    fun onPreview(bangla: String, raw: String, candidates: List<String>)
    fun onModeChanged(mode: Mode)
}
