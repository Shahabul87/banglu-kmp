package com.banglu.winime

import com.banglu.winime.composer.ComposerEngine
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S187: the boot warm-up runs on the engine lane one word per turn and never touches the composer; the latency ring fills from real keys. */
class S187WarmUpAndLatencyTest {
    private class CountingEngine : ComposerEngine {
        val converts = java.util.concurrent.atomic.AtomicInteger()
        val suggests = java.util.concurrent.atomic.AtomicInteger()
        override fun instant(raw: String) = raw
        override fun convert(raw: String, prev1: String?, prev2: String?): String { converts.incrementAndGet(); return raw.uppercase() }
        override fun suggest(raw: String, limit: Int, prev1: String?, prev2: String?): List<String> { suggests.incrementAndGet(); return listOf(raw.uppercase()) }
        override fun selected(raw: String, bangla: String) {}
        override fun recordCommitPair(prev: String, next: String) {}
        override fun predictNext(prev2: String?, prev1: String, limit: Int): List<String> = emptyList()
    }
    private class NullInjector : TextInjector {
        val text = StringBuilder()
        override fun injectText(text: String) { this.text.append(text) }
        override fun injectBackspaces(count: Int) { repeat(count) { if (text.isNotEmpty()) text.setLength(text.length - 1) } }
        override fun injectKey(key: RawKey) {}
        override fun injectVirtualKey(vk: Int, shift: Boolean) {}
    }
    private class NullListener : ControllerListener {
        override fun onCandidates(candidates: List<String>, predictions: Boolean) {}
        override fun onModeChanged(mode: Mode) {}
    }
    private class NoScheduler : RefineScheduler {
        override fun schedule(task: () -> Unit) {}
        override fun cancel() {}
        override fun close() {}
    }
    private val controllers = mutableListOf<Controller>()
    private fun controller(engine: CountingEngine): Controller =
        Controller(engine, NullInjector(), AppCompat(createTempDirectory("winime-s187").toFile()), NullListener(), NoScheduler())
            .apply { engineReady = true }.also { controllers += it }

    @AfterTest fun stop() { controllers.forEach { it.shutdown() } }

    @Test
    fun warmUpConvertsEveryPrefixAndSuggestsOncePerWord() {
        val e = CountingEngine(); val c = controller(e)
        c.warmUp(listOf("ami", "tumi", "kemon"))
        // Warm(n) enqueues Warm(n+1) after running, so drain once per word.
        repeat(5) { c.awaitIdle() }
        assertEquals(3, c.warmedWords)
        assertEquals(3 + 4 + 5, e.converts.get())
        assertEquals(3, e.suggests.get())
        assertEquals(0, c.latencySummary().samples, "warm-up words are not keystrokes")
    }

    @Test
    fun realKeysFillTheLatencyRingWhileTheWarmUpRuns() {
        val e = CountingEngine(); val c = controller(e)
        c.warmUp(List(40) { "word$it" })
        "ami".forEach { assertTrue(c.onKey(RawKey.Letter(it), "notepad.exe")) }
        c.onKey(RawKey.Space, "notepad.exe")
        repeat(45) { c.awaitIdle() }
        val l = c.latencySummary()
        assertEquals(4, l.samples)
        assertTrue(l.maxMs >= l.medianMs && l.medianMs >= 0.0)
        assertEquals(40, c.warmedWords)
    }

    @Test
    fun warmUpListIsBoundedAndLettersOnly() {
        assertTrue(WarmUpWords.list.size in 50..WarmUpWords.CAP, "size ${WarmUpWords.list.size}")
        assertTrue(WarmUpWords.list.all { w -> w.all { it in 'a'..'z' } })
    }
}
