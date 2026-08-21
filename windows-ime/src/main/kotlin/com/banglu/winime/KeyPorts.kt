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

    /**
     * Everything the hook has no [RawKey] for — arrows, `(`, `'`, Shift+2 and
     * the rest. The shift state travels with it because re-injecting the key
     * is the only way the application can ever produce the character, and
     * `(` is `9` with Shift held.
     */
    data class Unmanaged(val vk: Int, val shift: Boolean = false) : RawKey
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

    /**
     * [count] backspaces, to un-type text WE injected for the word currently
     * forming — never anything else. The controller is the only caller and it
     * counts them from its own echo ledger; see `Controller.reconcileEcho`.
     *
     * Sent as ONE `SendInput` batch rather than [count] calls: the host
     * application processes a single batch atomically, so an intervening real
     * keystroke cannot interleave halfway through a correction.
     */
    fun injectBackspaces(count: Int)

    /** Synthetic key event for a `ForwardKey` action. */
    fun injectKey(key: RawKey)

    /**
     * Re-injects a key the hook swallowed but the engine has no meaning for,
     * with its shift state, so the application produces exactly the character
     * the user pressed. Used to keep an unmanaged key behind the Bangla word
     * it would otherwise have overtaken.
     */
    fun injectVirtualKey(vk: Int, shift: Boolean)
}

/** The tray + preview window (Task 7). Called from the worker thread. */
interface ControllerListener {
    /**
     * The suggestion list for the word being typed, or empty to hide the
     * popup.
     *
     * The forming word itself is NOT reported here any more: it is echoed
     * live into the focused application as the user types, so the popup shows
     * suggestions and nothing else. An empty list is the hide signal.
     */
    fun onCandidates(candidates: List<String>)
    fun onModeChanged(mode: Mode)
}

/**
 * Deferred, cancellable execution of the debounced candidate refresh.
 *
 * A port rather than a bare timer so tests can fire the refresh
 * deterministically instead of sleeping, and so the production timer thread
 * stays out of the pure-logic layer. [schedule] REPLACES whatever was
 * pending: only the newest buffer is ever worth asking the engine about.
 */
interface RefineScheduler {
    fun schedule(task: () -> Unit)
    fun cancel()
    fun close()
}
