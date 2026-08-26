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
    }
}
