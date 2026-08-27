package com.banglu.keyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * S136 (production re-audit, F-005/F-006): assistive activation of the
 * keyboard, proven the way TalkBack and Switch Access do it — by performing
 * ACTION_CLICK on the keys' accessibility nodes (no touch events at all)
 * and reading the editor back. Also asserts every labelled key node is
 * activatable (itself or, for a chip whose label sits on a child node, its
 * clickable parent).
 */
@RunWith(AndroidJUnit4::class)
class S136KeyAccessibilityActivationInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val uiAutomation get() = instrumentation.uiAutomation
    private val packageName get() = context.packageName

    @Before
    fun setUp() {
        uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // shellRead waits for the command to finish; closing the stream early
        // (plain executeShellCommand) let `ime set` die before it applied.
        // S139: StrictMode violations recorded by the debug build (main-thread
        // disk I/O on the keystroke/attach path) FAIL this test — clear the
        // counters first so only this run counts.
        val diag = context.getSharedPreferences("banglu_prefs", android.content.Context.MODE_PRIVATE)
        diag.edit().also { e -> diag.all.keys.filter { it.startsWith("diag_failure_strict_") }.forEach { e.remove(it) } }.commit()
        shellRead("ime enable $packageName/.BangluIMEService")
        shellRead("ime set $packageName/.BangluIMEService")
        waitFor(8_000) {
            shellRead("settings get secure default_input_method").takeIf { it.contains(packageName) }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: fail("no launcher activity")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        dismissOnboarding()
    }

    @After
    fun tearDown() {
        shell("input keyevent BACK")
    }

    /** Every shell command WAITS for completion — closing the stream early
     *  lets the command die before it applies (ime set, input tap). */
    private fun shell(command: String) {
        shellRead(command)
        Thread.sleep(400)
    }

    private fun shellRead(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun roots(): List<AccessibilityNodeInfo> =
        uiAutomation.windows.mapNotNull { it.root } + listOfNotNull(uiAutomation.rootInActiveWindow)

    private fun imeRoots(): List<AccessibilityNodeInfo> =
        uiAutomation.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { it.root }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        node ?: return
        out += node
        for (i in 0 until node.childCount) walk(node.getChild(i), out)
    }

    private fun allNodes(roots: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> =
        mutableListOf<AccessibilityNodeInfo>().also { list -> roots.forEach { walk(it, list) } }

    private fun <T> waitFor(timeoutMs: Long = 8_000, probe: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            probe()?.let { return it }
            Thread.sleep(250)
        }
        fail("timed out after ${timeoutMs}ms")
    }

    /** Perform ACTION_CLICK on [node] or the nearest clickable ancestor —
     *  a label is often a plain text node inside the clickable row. */
    private fun clickViaAncestor(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(4) {
            val cur = n ?: return false
            if (cur.isClickable || cur.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            n = cur.parent
        }
        return false
    }

    private fun dismissOnboarding() {
        repeat(5) {
            Thread.sleep(1_200)
            val nodes = allNodes(roots())
            if (nodes.any { it.className?.contains("EditText") == true }) return
            val skip = nodes.firstOrNull { it.text?.toString() in setOf("Skip", "শুরু করুন", "শুরু করি") } ?: return@repeat
            clickViaAncestor(skip)
        }
    }

    private fun editor(): AccessibilityNodeInfo? =
        allNodes(roots()).firstOrNull { it.className?.contains("EditText") == true }

    private fun keyNodes(): List<AccessibilityNodeInfo> {
        val fromImeWindows = allNodes(imeRoots()).filter { !it.contentDescription.isNullOrBlank() }
        if (fromImeWindows.isNotEmpty()) return fromImeWindows
        // Some platform builds do not type the IME window; fall back to every
        // window's nodes owned by the keyboard package that look like keys.
        return allNodes(roots()).filter {
            it.packageName == packageName && !it.contentDescription.isNullOrBlank() &&
                it.contentDescription.toString() != "Banglu logo"
        }
    }

    private fun keyboardShown(): Boolean =
        shellRead("dumpsys input_method").contains("mInputShown=true")

    private fun showKeyboardWithARealTap(editorNode: AccessibilityNodeInfo) {
        repeat(3) {
            val rect = android.graphics.Rect()
            (editor() ?: editorNode).getBoundsInScreen(rect)
            shell("input tap ${rect.centerX()} ${rect.centerY()}")
            val deadline = System.currentTimeMillis() + 4_000
            while (System.currentTimeMillis() < deadline) {
                if (keyboardShown()) return
                Thread.sleep(250)
            }
        }
    }

    private fun activatable(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(3) {
            val cur = n ?: return false
            if (cur.isClickable || cur.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) return true
            n = cur.parent
        }
        return false
    }

    @Test
    fun assistiveClicksOnKeysTypeTheProbeWord() {
        val editorNode = waitFor(15_000) { editor() }
        editorNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        clickViaAncestor(editorNode)
        // Raising the soft keyboard is the host editor's job (a real tap);
        // what this test proves is that the KEYS activate assistively.
        showKeyboardWithARealTap(editorNode)
        // Start from an empty editor (ACTION_SET_TEXT is what an assistive
        // service would use too).
        editorNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
        )
        val keys = try {
            waitFor(12_000) { keyNodes().takeIf { list -> list.any { it.contentDescription.startsWith("Spacebar") } } }
        } catch (e: AssertionError) {
            val windows = uiAutomation.windows.joinToString { "type=${it.type} title=${it.title} pkg=${it.root?.packageName} nodes=${allNodes(listOfNotNull(it.root)).size}" }
            val ime = shellRead("dumpsys input_method").lineSequence().firstOrNull { "mInputShown" in it }?.trim()
            fail("keyboard nodes not visible. ime: $ime; windows: $windows; keyNodes=${keyNodes().map { it.contentDescription }}")
        }
        Thread.sleep(2_500) // seed engine + store attach on a cold process

        val labelled = keyNodes()
        val dead = labelled.filterNot { activatable(it) }.map { it.contentDescription.toString() }
        assertTrue(labelled.size >= 40, "labelled keyboard nodes: ${labelled.size}")
        assertEquals(emptyList(), dead, "every labelled key must be activatable by ACTION_CLICK")

        for (label in listOf("a", "m", "i")) {
            val key = keyNodes().firstOrNull {
                val cd = it.contentDescription.toString()
                cd == label || cd.startsWith("$label.")
            } ?: fail("key '$label' not in the IME accessibility tree")
            assertTrue(key.performAction(AccessibilityNodeInfo.ACTION_CLICK), "ACTION_CLICK on '$label' accepted")
            Thread.sleep(450)
        }
        Thread.sleep(1_500)
        val typed = waitFor { editor()?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
        assertEquals("আমি", typed, "assistive clicks a,m,i must compose আমি")

        // S138 (F-005): every key FAMILY executes, not just letters.
        clickKey("Spacebar")
        assertEquals("আমি ", editorTextRaw(), "Space via ACTION_CLICK")
        clickKey("Number ১")
        assertEquals("আমি ১", editorTextRaw(), "number row via ACTION_CLICK")
        clickKey("Backspace")
        clickKey("Backspace")
        assertEquals("আমি", editorTextRaw(), "Backspace via ACTION_CLICK (x2)")
        // Long-press alternative exposed as a custom accessibility action.
        val altKey = keyNodes().firstOrNull { n -> n.actionList.any { it.label?.startsWith("Insert") == true } }
            ?: fail("no key exposes an 'Insert …' custom action")
        val alt = altKey.actionList.first { it.label?.startsWith("Insert") == true }
        assertTrue(altKey.performAction(alt.id), "custom action '${alt.label}' accepted")
        Thread.sleep(600)
        assertTrue(editorTextRaw().length > "আমি".length, "custom action inserted the alternative")

        // S138 (F-013): real InputConnection deletion of non-Bengali clusters
        // through the platform grapheme rules (ICU on device).
        setEditorText("hi \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67") // hi 👨‍👩‍👧
        clickKey("Backspace")
        assertEquals("hi ", editorTextRaw(), "one Backspace removes the whole emoji family")
        setEditorText("\uD83C\uDDE7\uD83C\uDDE9") // 🇧🇩
        clickKey("Backspace")
        assertEquals("", editorTextRaw(), "one Backspace removes the whole flag")
        setEditorText("ক্ষমা")
        clickKey("Backspace")
        assertEquals("ক্ষ", editorTextRaw(), "Bengali rules unchanged: 'মা' is one cluster")

        // S139 (F-003): the enable → open panel → paste workflow on the real
        // keyboard, real SharedPreferences, real ClipboardManager.
        val prefs = context.getSharedPreferences("banglu_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, true).commit()
        // The Bengali backspace above left the keyboard RESUMING the word
        // (S88), so the suggestion strip hides the action bar. A hide/show
        // cycle is the real-world reset: onStartInputView clears the buffer
        // and the suggestions, and the action bar returns.
        setEditorText("")
        shell("input keyevent BACK")
        Thread.sleep(800)
        showKeyboardWithARealTap(waitFor { editor() })
        waitFor(6_000) { keyNodes().firstOrNull { it.contentDescription.toString() == "Clipboard" || it.contentDescription.toString() == "More tools" } }
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val copied = "ক্লিপবোর্ড টেস্ট ${System.currentTimeMillis() % 1000}"
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("banglu-test", copied))
        Thread.sleep(600)
        openClipboardPanel()
        val item = waitFor(6_000) { allNodes(imeRoots().ifEmpty { roots() }).firstOrNull { it.text?.toString() == copied } }
        assertTrue(clickViaAncestor(item), "clipboard item accepted the click")
        Thread.sleep(800)
        assertEquals(copied, editorTextRaw(), "the copied text was pasted from the panel")
        assertTrue(
            prefs.getString(PrefsMigrations.CLIPBOARD_ENTRIES_KEY, "").orEmpty().isNotEmpty(),
            "with history ON the entry persisted under the entries key"
        )
        // Switch off: the stored history must be gone (durable), the panel
        // must show only the current clip.
        prefs.edit().putBoolean(PrefsMigrations.CLIPBOARD_ENABLED_KEY, false).commit()
        Thread.sleep(800)
        assertNull(prefs.getString(PrefsMigrations.CLIPBOARD_ENTRIES_KEY, null), "history purged on switch-off")

        val strict = prefs.all.keys.filter { it.startsWith("diag_failure_strict_") }
        assertEquals(emptyList(), strict, "no StrictMode violation on the keystroke/attach path")
    }

    private fun openClipboardPanel() {
        // The strip alternates between the action bar (idle) and the
        // suggestion strip; nodes go stale between recompositions, so every
        // attempt re-queries the tree.
        repeat(5) {
            val fresh = keyNodes()
            val direct = fresh.firstOrNull { it.contentDescription.toString() == "Clipboard" }
            if (direct != null && clickViaAncestor(direct)) {
                Thread.sleep(800); return
            }
            val tools = fresh.firstOrNull { it.contentDescription.toString() == "More tools" }
            if (tools != null) clickViaAncestor(tools)
            Thread.sleep(700)
        }
        fail("could not open the clipboard panel: ${keyNodes().map { it.contentDescription }}")
    }

    private fun editorTextRaw(): String {
        Thread.sleep(500)
        val node = editor() ?: return ""
        // An empty field reports its HINT as text — that is "empty".
        if (node.isShowingHintText) return ""
        return node.text?.toString().orEmpty()
    }

    private fun clickKey(labelPrefix: String) {
        val key = keyNodes().firstOrNull { it.contentDescription.toString().startsWith(labelPrefix) }
            ?: fail("key '$labelPrefix' not in the IME accessibility tree")
        assertTrue(key.performAction(AccessibilityNodeInfo.ACTION_CLICK), "ACTION_CLICK on '$labelPrefix' accepted")
        Thread.sleep(450)
    }

    private fun setEditorText(text: String) {
        val node = editor() ?: fail("editor lost")
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        )
        Thread.sleep(300)
        (editor() ?: node).performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
            }
        )
        Thread.sleep(400)
    }
}
