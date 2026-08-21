package com.banglu.winime.composer

import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.TestEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pin tests for the Windows port of macos-ime/Sources/BangluCore/Composer.swift.
 * Runs against the real engine/dictionary via TestEngine.boot() — same gate
 * the macOS BangluCoreTestRunner uses, minus the Swift/JSC hosting.
 */
class ComposerTest {

    private object RealComposerEngine : ComposerEngine {
        override fun convert(raw: String): String = SmartEngineAdapter.convertWord(raw).bengali
        override fun suggest(raw: String, limit: Int): List<String> =
            SmartEngineAdapter.getSuggestions(raw, limit).map { it.bengali }
    }

    private fun composer(): Composer {
        TestEngine.boot()
        return Composer(RealComposerEngine)
    }

    private fun commits(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Commit>().joinToString("") { it.text }

    private fun previews(actions: List<ComposerAction>) =
        actions.filterIsInstance<ComposerAction.Preview>()

    private fun type(c: Composer, s: String): List<ComposerAction> =
        s.flatMap { c.handle(ComposerKey.Letter(it)) }

    @Test
    fun spaceCommitsExactlyThePreview() {
        val c = composer()
        val typed = type(c, "ami")
        val lastPreview = previews(typed).last()
        assertEquals("আমি", lastPreview.bangla)
        assertEquals("ami", lastPreview.raw)
        // WYSIWYG law: the space commit must equal the last previewed
        // Bangla exactly — never a re-conversion of the raw buffer.
        assertEquals(lastPreview.bangla, commits(c.handle(ComposerKey.Space)))
    }

    @Test
    fun doubleSpaceMakesDari() {
        val c = composer()
        type(c, "ami")
        assertEquals("আমি", commits(c.handle(ComposerKey.Space)))
        assertEquals("। ", commits(c.handle(ComposerKey.Space)))
    }

    @Test
    fun tripleSpaceAlternates() {
        val c = composer()
        type(c, "ami")
        assertEquals("আমি", commits(c.handle(ComposerKey.Space)))
        assertEquals("। ", commits(c.handle(ComposerKey.Space)))
        // dariJustCommitted flips back off: third space is a plain space,
        // not another দাঁড়ি.
        assertEquals(" ", commits(c.handle(ComposerKey.Space)))
    }

    @Test
    fun letterAfterPendingSpaceReleasesPlainSpace() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space) // commits আমি, holds the space
        assertTrue(c.pendingSpace)
        val actions = c.handle(ComposerKey.Letter('k'))
        assertEquals(" ", commits(actions))
        assertFalse(c.pendingSpace)
        assertTrue(c.forming)
        val preview = previews(actions).last()
        assertEquals("k", preview.raw)
    }

    @Test
    fun tightPunctuationSwallowsPendingSpace() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        assertEquals(",", commits(c.handle(ComposerKey.Punctuation(","))))
    }

    @Test
    fun periodMapsToDariAndIsTight() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        // "." maps to দাঁড়ি first, THEN the tight-punctuation check runs on
        // the mapped character — no space is inserted before it.
        assertEquals("।", commits(c.handle(ComposerKey.Punctuation("."))))
    }

    @Test
    fun digitsCommitBengali() {
        val c = composer()
        assertFalse(c.forming)
        assertEquals("৫", commits(c.handle(ComposerKey.Digit('5'))))
    }

    @Test
    fun digitPicksCandidateWhileForming() {
        val c = composer()
        var picked: Triple<String, String, Boolean>? = null
        c.onPick = { raw, bangla, wasPrimary -> picked = Triple(raw, bangla, wasPrimary) }
        type(c, "kmn") // primary কেমন, candidate[1] কেম (real-engine pin)
        val actions = c.handle(ComposerKey.Digit('2'))
        assertEquals("কেম", commits(actions))
        assertEquals(Triple("kmn", "কেম", false), picked)
        assertTrue(c.pendingSpace)
        assertFalse(c.forming)
    }

    @Test
    fun backspaceEditsFormingBuffer() {
        val c = composer()
        type(c, "amii")
        val actions = c.handle(ComposerKey.Backspace)
        val preview = previews(actions).last()
        assertEquals("ami", preview.raw)
        assertEquals("আমি", preview.bangla)
        assertTrue(c.forming)
    }

    @Test
    fun backspaceWithPendingSpaceMaterializesIt() {
        val c = composer()
        type(c, "ami")
        c.handle(ComposerKey.Space)
        assertTrue(c.pendingSpace)
        val actions = c.handle(ComposerKey.Backspace)
        assertEquals(
            listOf(ComposerAction.Commit(" "), ComposerAction.ForwardKey(ComposerKey.Backspace)),
            actions,
        )
        assertFalse(c.pendingSpace)
    }

    @Test
    fun enterCommitsFormingThenForwards() {
        val c = composer()
        type(c, "ami")
        val actions = c.handle(ComposerKey.Enter)
        assertEquals("আমি", commits(actions))
        assertEquals(ComposerAction.ForwardKey(ComposerKey.Enter), actions.last())
        assertFalse(c.forming)
        assertFalse(c.pendingSpace)
    }

    @Test
    fun escapeCancelsToRaw() {
        val c = composer()
        type(c, "ami")
        val actions = c.handle(ComposerKey.Escape)
        // The raw roman is committed verbatim — not the converted বাংলা.
        assertEquals("ami", commits(actions))
        assertFalse(c.forming)
    }

    @Test
    fun focusLostFlushesForming() {
        val c = composer()
        type(c, "ami")
        assertEquals("আমি", commits(c.focusLost()))
        assertFalse(c.forming)

        // pendingSpace path: the held space must be dropped silently — no
        // trailing space is injected into the host app on focus loss.
        val c2 = composer()
        type(c2, "ami")
        c2.handle(ComposerKey.Space)
        assertTrue(c2.pendingSpace)
        assertTrue(c2.focusLost().isEmpty())
        assertFalse(c2.pendingSpace)
    }

    @Test
    fun pickTeachesOnlyNonPrimary() {
        val c = composer()
        var picked: Triple<String, String, Boolean>? = null
        c.onPick = { raw, bangla, wasPrimary -> picked = Triple(raw, bangla, wasPrimary) }

        type(c, "kmn")
        val primaryActions = c.pick(0)
        assertEquals(Triple("kmn", "কেমন", true), picked)
        assertEquals("কেমন", commits(primaryActions))

        type(c, "kmn")
        val altActions = c.pick(1)
        assertEquals(Triple("kmn", "কেম", false), picked)
        assertEquals("কেম", commits(altActions))
    }
}
