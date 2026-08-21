package com.banglu.winime.hook

import com.banglu.winime.HookSink
import com.banglu.winime.KeySource
import com.banglu.winime.RawKey
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tag on every keyboard event this process synthesises. Windows hands it back
 * to the hook in `KBDLLHOOKSTRUCT.dwExtraInfo`, which is the only way we can
 * tell our own injected Bangla apart from what the user typed. Without it the
 * first committed word would be fed straight back into the engine forever.
 */
internal const val BANGLU_MAGIC = 0xBA6C1L

internal const val VK_BACK = 0x08
internal const val VK_TAB = 0x09
internal const val VK_RETURN = 0x0D
internal const val VK_SHIFT = 0x10
internal const val VK_CONTROL = 0x11
internal const val VK_MENU = 0x12 // Alt
internal const val VK_CAPITAL = 0x14
internal const val VK_ESCAPE = 0x1B
internal const val VK_SPACE = 0x20
internal const val VK_LWIN = 0x5B
internal const val VK_RWIN = 0x5C
internal const val VK_LSHIFT = 0xA0
internal const val VK_RSHIFT = 0xA1
internal const val VK_LCONTROL = 0xA2
internal const val VK_RCONTROL = 0xA3
internal const val VK_LMENU = 0xA4
internal const val VK_RMENU = 0xA5
internal const val VK_OEM_COMMA = 0xBC
internal const val VK_OEM_PERIOD = 0xBE
internal const val VK_OEM_2 = 0xBF // '/' unshifted, '?' shifted (US layout)

private const val EVENT_SYSTEM_FOREGROUND = 0x0003
private const val WINEVENT_OUTOFCONTEXT = 0x0000
private const val WINEVENT_SKIPOWNPROCESS = 0x0002

private const val WM_QUIT = 0x0012
private const val PM_NOREMOVE = 0x0000

/** WM_APP + 1: the watchdog's "try installing again" request to the pump. */
private const val WM_BANGLU_REARM = 0x8001

private const val KEY_STATE_DOWN = 0x8000

private const val WATCHDOG_PERIOD_MS = 5_000L
private const val START_TIMEOUT_MS = 5_000L
private const val STOP_JOIN_MS = 2_000L

/**
 * The system-wide keyboard hook: the single point where a physical keystroke
 * becomes a [RawKey] and where we decide whether the focused application ever
 * sees it.
 *
 * Three hard Win32 constraints shape every line below.
 *
 * 1. **`WH_KEYBOARD_LL` is bound to a thread with a message loop.** The
 *    callback is delivered on the thread that called `SetWindowsHookEx`,
 *    while that thread sits in `GetMessage`. So this class owns a dedicated
 *    pump thread, installs and removes both hooks on it, and reaches it from
 *    the outside only by posting messages.
 * 2. **A slow callback is silently unregistered.** Windows gives no error and
 *    no event — the keyboard simply goes dead. The callback therefore does
 *    nothing but read pre-built tables, ask the sink (which is itself
 *    non-blocking) and return. No allocation we control, no I/O, no locking,
 *    no logging.
 * 3. **A collected JNA callback is a native crash.** [keyboardProc] and
 *    [focusProc] are strong fields, and the running pump thread is a GC root
 *    that keeps `this` — and therefore them — reachable for the hook's whole
 *    lifetime.
 */
class LowLevelHook : KeySource {

    /**
     * Invoked on the pump thread when the KEYBOARD hook comes back after being
     * absent. The tray uses it to tell the user their keyboard is working
     * again. It deliberately ignores the foreground hook: re-arming that one
     * alone would announce a recovery for a keyboard that never stopped.
     */
    @Volatile
    var onRearm: (() -> Unit)? = null

    /**
     * Invoked on the pump thread when installing the keyboard hook fails — at
     * boot, or on a later attempt after it had been working. The argument is
     * the exception if one was thrown, and null when `SetWindowsHookEx` simply
     * returned NULL (which is the usual shape: group policy, a locked session,
     * an elevated foreground window at start-up).
     *
     * Without this a permanently failing install is a keyboard that does
     * nothing, forever, with nothing on screen to explain it.
     *
     * Edge-triggered, not level-triggered: it fires on entering the failed
     * state, not on every 5-second retry, because a tray warning repeating
     * forever is its own defect. [isInstalled] is the pollable truth for as
     * long as the condition lasts.
     */
    @Volatile
    var onHookLost: ((Throwable?) -> Unit)? = null

    /**
     * Whether we currently HOLD a keyboard-hook handle — which is not the same
     * question as "are keystrokes still reaching us".
     *
     * False is trustworthy: `SetWindowsHookEx` failed or was undone, and no key
     * reaches us. True is weaker than it looks: Windows silently unregisters a
     * hook whose callback it judged slow, tells nobody, and leaves the handle
     * in our hands — so a keyboard that has gone dead that way still reads
     * `true` here. No Win32 call reports that state, and guessing at it would
     * mean re-arming healthy hooks and eating keystrokes (see [runWatchdog]).
     * The compensating control is a user-driven one: the tray's
     * "কীবোর্ড কাজ করছে না? আবার চালু করুন" item, which is named after the
     * symptom precisely because no warning will have been shown.
     */
    val isInstalled: Boolean get() = hook != null

    @Volatile
    private var sink: HookSink? = null

    @Volatile
    private var pumpThreadId: Int = 0

    @Volatile
    private var stopping = false

    @Volatile
    private var hook: WinUser.HHOOK? = null

    @Volatile
    private var focusHook: WinNT.HANDLE? = null

    private val lifecycle = Any()
    private var pumpThread: Thread? = null
    private var watchdogThread: Thread? = null

    // Strong references for the lifetime of the hooks — see the class note.
    private val keyboardProc = KeyboardProc()
    private val focusProc = FocusProc()

    /**
     * Whether the down-stroke of this virtual key was swallowed. An app that
     * never saw the key-down must not see the key-up either, or it registers a
     * release for a key it believes was never pressed — which breaks
     * shortcut and game input handling in ways that look like our bug.
     */
    private val swallowedDown = BooleanArray(256)

    /** VK → key, pre-built so classification allocates nothing per keystroke. */
    private val plainKeys: Array<RawKey> = buildKeyTable(shift = false)
    private val shiftedKeys: Array<RawKey> = buildKeyTable(shift = true)

    /**
     * Modifier keys pressed on their own. They must never be claimed: the
     * controller now swallows an unmanaged key while a word is forming and
     * re-injects it, and re-injecting a modifier would be actively harmful —
     * the synthetic key-UP tells Windows a key the user is still holding was
     * released, and a lone Alt or Win press-and-release opens a menu.
     * The `GetAsyncKeyState` guard below cannot cover this, because a
     * modifier's own key-down may arrive before its state is published.
     */
    private val modifierKeys = BooleanArray(256).also {
        for (vk in intArrayOf(
            VK_SHIFT, VK_CONTROL, VK_MENU, VK_CAPITAL, VK_LWIN, VK_RWIN,
            VK_LSHIFT, VK_RSHIFT, VK_LCONTROL, VK_RCONTROL, VK_LMENU, VK_RMENU,
        )) {
            it[vk] = true
        }
    }

    /**
     * Reused across callbacks: the hook procedure runs only on the pump
     * thread, and JNA marshals both to native values before the call returns,
     * so neither instance is ever shared or retained.
     */
    private val swallowResult = WinDef.LRESULT(1)
    private val nextLParam = WinDef.LPARAM(0)

    // MARK: - lifecycle

    override fun start(sink: HookSink) {
        synchronized(lifecycle) {
            if (pumpThread != null) return
            this.sink = sink
            stopping = false
            val ready = CountDownLatch(1)
            val pump = Thread({ runPump(ready) }, "banglu-winime-hook").apply { isDaemon = true }
            pumpThread = pump
            pump.start()
            val watchdog = Thread({ runWatchdog() }, "banglu-winime-hook-watchdog").apply { isDaemon = true }
            watchdogThread = watchdog
            watchdog.start()
            // Returning before the hook exists would let the caller report a
            // live keyboard that is not live yet. Bounded, so a wedged Win32
            // call cannot hang application start-up.
            ready.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** Unhooks and ends the pump. Idempotent. */
    override fun stop() {
        synchronized(lifecycle) {
            val pump = pumpThread ?: return
            pumpThread = null
            stopping = true
            val tid = pumpThreadId
            if (tid != 0) {
                User32.INSTANCE.PostThreadMessage(tid, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
            }
            pump.join(STOP_JOIN_MS)
            watchdogThread?.interrupt()
            watchdogThread = null
            pumpThreadId = 0
            sink = null
        }
    }

    /** Set while the keyboard hook is known to be down, so [onHookLost] fires once per outage. */
    private var hookLossReported = false

    // MARK: - pump thread

    private fun runPump(ready: CountDownLatch) {
        pumpThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
        val msg = WinUser.MSG()
        try {
            // Windows creates a thread's message queue lazily, and a message
            // posted before it exists is dropped. Forcing the queue into being
            // before start() returns is what makes an immediate stop() — or a
            // watchdog re-arm on a slow boot — reach this thread at all.
            User32.INSTANCE.PeekMessage(msg, null, WinUser.WM_USER, WinUser.WM_USER, PM_NOREMOVE)
            installKeyboardHook()
            installFocusHook()
        } finally {
            ready.countDown()
        }
        while (true) {
            // hWnd = null takes both window messages and the thread messages
            // that stop() and the watchdog post. 0 = WM_QUIT; -1 = a Win32
            // error, which our fixed arguments cannot provoke — treat it as
            // fatal for the pump rather than spinning the CPU on it.
            val r = User32.INSTANCE.GetMessage(msg, null, 0, 0)
            if (r <= 0) break
            if (msg.message == WM_BANGLU_REARM) {
                rearm()
                continue
            }
            User32.INSTANCE.TranslateMessage(msg)
            User32.INSTANCE.DispatchMessage(msg)
        }
        uninstall()
    }

    private fun installKeyboardHook(): Boolean {
        if (hook != null) return true
        var failure: Throwable? = null
        val h = try {
            // WH_KEYBOARD_LL ignores hMod, but passing the running module is
            // the documented call shape; the trailing 0 makes the hook global.
            val module = Kernel32.INSTANCE.GetModuleHandle(null)
            User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardProc, module, 0)
        } catch (t: Throwable) {
            failure = t
            null
        }
        hook = h
        if (h == null) {
            if (!hookLossReported) {
                hookLossReported = true
                runCatching { onHookLost?.invoke(failure) }
            }
            return false
        }
        hookLossReported = false
        return true
    }

    private fun installFocusHook(): Boolean {
        if (focusHook != null) return true
        // WINEVENT_OUTOFCONTEXT requires a NULL module and delivers the
        // callback on this thread's message loop — the same pump.
        // SKIPOWNPROCESS keeps our own preview window from being reported as
        // a focus change, which would flush the word the user is still typing.
        val h = User32.INSTANCE.SetWinEventHook(
            EVENT_SYSTEM_FOREGROUND,
            EVENT_SYSTEM_FOREGROUND,
            null,
            focusProc,
            0,
            0,
            WINEVENT_OUTOFCONTEXT or WINEVENT_SKIPOWNPROCESS,
        )
        focusHook = h
        return h != null
    }

    private fun rearm() {
        // The two hooks are tracked apart on purpose: only the keyboard one
        // going from absent to present is a recovery worth announcing.
        val keyboardWasMissing = hook == null
        val keyboardBack = installKeyboardHook() && keyboardWasMissing
        installFocusHook()
        if (keyboardBack) {
            runCatching { onRearm?.invoke() }
        }
    }

    private fun uninstall() {
        val h = hook
        hook = null
        if (h != null) runCatching { User32.INSTANCE.UnhookWindowsHookEx(h) }
        val f = focusHook
        focusHook = null
        if (f != null) runCatching { User32.INSTANCE.UnhookWinEvent(f) }
    }

    // MARK: - watchdog thread

    /**
     * Deliberately the dumbest watchdog that is still honest: it re-installs a
     * hook that is *known* to be absent, which happens when `SetWindowsHookEx`
     * fails transiently (desktop switch, session lock, an elevated foreground
     * window at boot). Windows can also drop a hook it judged slow without
     * telling anyone, and no Win32 call reports that — so this does NOT try to
     * guess at the health of a hook it still holds a handle for. Re-arming a
     * healthy hook would drop keystrokes for no reason.
     */
    private fun runWatchdog() {
        while (!stopping) {
            try {
                Thread.sleep(WATCHDOG_PERIOD_MS)
            } catch (_: InterruptedException) {
                return
            }
            if (stopping) return
            if (hook != null && focusHook != null) continue
            val tid = pumpThreadId
            if (tid != 0) {
                User32.INSTANCE.PostThreadMessage(tid, WM_BANGLU_REARM, WinDef.WPARAM(0), WinDef.LPARAM(0))
            }
        }
    }

    // MARK: - the hook procedure (pump thread, microseconds only)

    private inner class KeyboardProc : WinUser.LowLevelKeyboardProc {
        override fun callback(
            nCode: Int,
            wParam: WinDef.WPARAM,
            info: WinUser.KBDLLHOOKSTRUCT,
        ): WinDef.LRESULT {
            // Negative nCode: the documented contract is to pass it on
            // untouched without inspecting anything.
            if (nCode < 0) return next(nCode, wParam, info)

            // Our own SendInput must never be re-converted. The magic tag is
            // the whole test: every event this process emits goes through the
            // one builder that sets it. Testing LLKHF_INJECTED as well would
            // also exclude the on-screen/touch keyboard, AutoHotkey and
            // PowerToys remaps, OEM keyboard utilities and remote-desktop
            // input — for those users the app would be silently inert.
            val extra = info.dwExtraInfo
            if (extra != null && extra.toLong() == BANGLU_MAGIC) return next(nCode, wParam, info)

            val vk = info.vkCode
            if (vk < 0 || vk > 255) return next(nCode, wParam, info)

            val message = wParam.toInt()
            if (message == WinUser.WM_KEYUP || message == WinUser.WM_SYSKEYUP) {
                if (swallowedDown[vk]) {
                    swallowedDown[vk] = false
                    return swallowResult
                }
                return next(nCode, wParam, info)
            }
            if (message != WinUser.WM_KEYDOWN && message != WinUser.WM_SYSKEYDOWN) {
                return next(nCode, wParam, info)
            }

            if (modifierKeys[vk]) {
                swallowedDown[vk] = false
                return next(nCode, wParam, info)
            }

            val target = sink ?: return next(nCode, wParam, info)

            // GetAsyncKeyState, not GetKeyState: the latter answers for the
            // thread's own message queue, which for a global hook is the wrong
            // queue entirely.
            val ctrl = isDown(VK_CONTROL)
            val alt = isDown(VK_MENU)
            val win = isDown(VK_LWIN) || isDown(VK_RWIN)

            val key: RawKey
            if (ctrl && !alt && !win && vk == VK_SPACE) {
                key = RawKey.ToggleHotkey
            } else if (ctrl || alt || win) {
                // Ctrl+C, Alt+Tab and Win+E belong to the application and the
                // shell. Claiming any of them would break every app at once.
                swallowedDown[vk] = false
                return next(nCode, wParam, info)
            } else {
                // Shift only ever selects a different pre-built table; a
                // shifted letter is still the lowercase Letter the engine
                // contract requires. Keys whose shifted meaning we cannot
                // represent (Shift+2 = '@') fall through as Unmanaged carrying
                // that shift state, which is what lets the controller put '@'
                // — not '2' — back into the application after a flush.
                key = if (isDown(VK_SHIFT)) shiftedKeys[vk] else plainKeys[vk]
            }

            // A throwing sink must degrade to a working keyboard, never to a
            // dead one; the try costs nothing when nothing throws.
            val swallow = try {
                target.onKey(key, ForegroundApp.exeName())
            } catch (_: Throwable) {
                false
            }
            swallowedDown[vk] = swallow
            return if (swallow) swallowResult else next(nCode, wParam, info)
        }
    }

    private fun isDown(vk: Int): Boolean =
        (User32.INSTANCE.GetAsyncKeyState(vk).toInt() and KEY_STATE_DOWN) != 0

    /**
     * `CallNextHookEx` wants the raw LPARAM; JNA gave us the structure that
     * LPARAM pointed at, so the pointer value has to be rebuilt from it.
     */
    private fun next(
        nCode: Int,
        wParam: WinDef.WPARAM,
        info: WinUser.KBDLLHOOKSTRUCT,
    ): WinDef.LRESULT {
        nextLParam.setValue(Pointer.nativeValue(info.pointer))
        return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, nextLParam)
    }

    // MARK: - foreground watch (same pump thread)

    private inner class FocusProc : WinUser.WinEventProc {
        override fun callback(
            hWinEventHook: WinNT.HANDLE?,
            event: WinDef.DWORD?,
            hwnd: WinDef.HWND?,
            idObject: WinDef.LONG?,
            idChild: WinDef.LONG?,
            dwEventThread: WinDef.DWORD?,
            dwmsEventTime: WinDef.DWORD?,
        ) {
            // Drop the cached exe rather than re-resolving here: at this
            // instant GetForegroundWindow may still answer with the old
            // window, and caching that against the new one would mislabel the
            // app for as long as it stays focused.
            ForegroundApp.invalidate()
            val target = sink ?: return
            // A focus change is never swallowed — it exists to flush a word
            // stranded in the window we just left.
            try {
                target.onKey(RawKey.FocusChanged, "")
            } catch (_: Throwable) {
                // Same reason as the keyboard proc: never let the sink kill
                // the pump thread.
            }
        }
    }

    // MARK: - classification table

    private fun buildKeyTable(shift: Boolean): Array<RawKey> {
        // Every VK gets an instance up front, Unmanaged included, so the hook
        // procedure only ever indexes an array. The shift flag is baked into
        // the table rather than read per keystroke, which is what keeps the
        // controller able to re-inject `(` rather than `9`.
        val t = Array<RawKey>(256) { RawKey.Unmanaged(it, shift) }
        for (vk in 0x41..0x5A) t[vk] = RawKey.Letter('a' + (vk - 0x41))
        for (vk in 0x60..0x69) t[vk] = RawKey.Digit('0' + (vk - 0x60)) // numpad
        t[VK_SPACE] = RawKey.Space
        t[VK_BACK] = RawKey.Backspace
        t[VK_RETURN] = RawKey.Enter
        t[VK_TAB] = RawKey.Tab
        t[VK_ESCAPE] = RawKey.Escape
        if (shift) {
            t[0x31] = RawKey.Punct("!")
            t[VK_OEM_2] = RawKey.Punct("?")
        } else {
            for (vk in 0x30..0x39) t[vk] = RawKey.Digit('0' + (vk - 0x30))
            t[VK_OEM_PERIOD] = RawKey.Punct(".")
            t[VK_OEM_COMMA] = RawKey.Punct(",")
        }
        return t
    }
}
