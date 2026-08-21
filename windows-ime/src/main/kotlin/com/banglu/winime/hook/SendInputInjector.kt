package com.banglu.winime.hook

import com.banglu.winime.RawKey
import com.banglu.winime.TextInjector
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Puts text into whatever application has focus, using `SendInput` with
 * `KEYEVENTF_UNICODE` so no keyboard layout is involved: the code unit goes in
 * verbatim, which is the only way to type Bangla into an application whose
 * layout is US English.
 *
 * Every event carries [BANGLU_MAGIC] in `dwExtraInfo`. That tag is what
 * [LowLevelHook] tests to know it is looking at our own output — without it
 * the first injected word re-enters the engine and the two feed each other
 * forever.
 *
 * Called only from the controller's worker thread, never from the hook.
 */
class SendInputInjector : TextInjector {

    override fun injectText(text: String) {
        if (text.isEmpty()) return
        // UTF-16 code units, not code points: a surrogate pair is two events,
        // which is exactly what KEYEVENTF_UNICODE expects.
        val inputs = allocate(text.length * 2)
        for (i in text.indices) {
            val unit = text[i].code.toLong()
            unicodeEvent(inputs[i * 2], unit, up = false)
            unicodeEvent(inputs[i * 2 + 1], unit, up = true)
        }
        send(inputs)
    }

    override fun injectKey(key: RawKey) {
        when (key) {
            RawKey.Enter -> injectVk(VK_RETURN)
            RawKey.Tab -> injectVk(VK_TAB)
            RawKey.Backspace -> injectVk(VK_BACK)
            // Not in the original brief, but the composer forwards Escape
            // whenever no word is forming — which, thanks to the pending-space
            // window, is most of the time. Dropping it would silently break
            // the user's Escape key in every application.
            RawKey.Escape -> injectVk(VK_ESCAPE)
            // Unreachable from the composer, which only ever forwards the four
            // above; handled so a future action cannot vanish silently.
            is RawKey.Letter -> injectText(key.c.toString())
            is RawKey.Digit -> injectText(key.c.toString())
            is RawKey.Punct -> injectText(key.p)
            RawKey.Space -> injectText(" ")
            is RawKey.Unmanaged -> injectVirtualKey(key.vk, key.shift)
            // Not keystrokes: an internal signal and a window event.
            RawKey.ToggleHotkey, RawKey.FocusChanged -> Unit
        }
    }

    override fun injectVirtualKey(vk: Int, shift: Boolean) {
        if (vk < 0 || vk > 0xFFFF) return
        // Wrapping the key in a synthetic Shift press is only correct while
        // Shift is actually up. If the user is still holding it — which is the
        // common case, since they only just pressed the shifted key — our
        // shift-UP would tell Windows a key they are still pressing was
        // released, and the hook would then classify their very next keystroke
        // as unshifted. Holding Shift and typing "((" would produce "(৯".
        // With Shift already down the application reads the live modifier
        // state and the bare key is enough.
        if (!shift || isShiftDown()) {
            val inputs = allocate(2)
            vkEvent(inputs[0], vk.toLong(), up = false)
            vkEvent(inputs[1], vk.toLong(), up = true)
            send(inputs)
            return
        }
        val inputs = allocate(4)
        vkEvent(inputs[0], VK_SHIFT.toLong(), up = false)
        vkEvent(inputs[1], vk.toLong(), up = false)
        vkEvent(inputs[2], vk.toLong(), up = true)
        vkEvent(inputs[3], VK_SHIFT.toLong(), up = true)
        send(inputs)
    }

    private fun isShiftDown(): Boolean =
        (User32.INSTANCE.GetAsyncKeyState(VK_SHIFT).toInt() and 0x8000) != 0

    private fun injectVk(vk: Int) = injectVirtualKey(vk, shift = false)

    @Suppress("UNCHECKED_CAST")
    private fun allocate(count: Int): Array<WinUser.INPUT> =
        // toArray gives one contiguous native block, which is what SendInput
        // requires; separately constructed structures would not be adjacent.
        WinUser.INPUT().toArray(count) as Array<WinUser.INPUT>

    private fun unicodeEvent(input: WinUser.INPUT, codeUnit: Long, up: Boolean) {
        val flags = if (up) {
            WinUser.KEYBDINPUT.KEYEVENTF_UNICODE or WinUser.KEYBDINPUT.KEYEVENTF_KEYUP
        } else {
            WinUser.KEYBDINPUT.KEYEVENTF_UNICODE
        }
        // KEYEVENTF_UNICODE requires wVk = 0; the character travels in wScan.
        fill(input, wVk = 0L, wScan = codeUnit, flags = flags)
    }

    private fun vkEvent(input: WinUser.INPUT, vk: Long, up: Boolean) {
        fill(input, wVk = vk, wScan = 0L, flags = if (up) WinUser.KEYBDINPUT.KEYEVENTF_KEYUP else 0)
    }

    private fun fill(input: WinUser.INPUT, wVk: Long, wScan: Long, flags: Int) {
        input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        // The union is written according to the type selected here; without
        // this JNA would write the mouse member over our keyboard fields.
        input.input.setType("ki")
        input.input.ki.wVk = WinDef.WORD(wVk)
        input.input.ki.wScan = WinDef.WORD(wScan)
        input.input.ki.dwFlags = WinDef.DWORD(flags.toLong())
        input.input.ki.time = WinDef.DWORD(0)
        input.input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(BANGLU_MAGIC)
    }

    private fun send(inputs: Array<WinUser.INPUT>) {
        for (input in inputs) input.write()
        val sent = User32.INSTANCE
            .SendInput(WinDef.DWORD(inputs.size.toLong()), inputs, inputs[0].size())
            .toInt()
        if (sent != inputs.size) {
            // UIPI blocks a normal-integrity process from sending input to an
            // elevated window, and BlockInput does the same globally. Failing
            // loudly hands the controller its existing recovery path (reset
            // the composer, surface the error) instead of losing the user's
            // word with no trace.
            error("SendInput inserted $sent of ${inputs.size} events (blocked by UIPI or BlockInput)")
        }
    }
}
