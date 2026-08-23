package com.banglu.winime

import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.composer.Composer
import com.banglu.winime.composer.ComposerAction
import com.banglu.winime.composer.ComposerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S130 parity wall, on the real dictionary: the Windows typing lane must
 * produce EXACTLY what the Android lane produces for the same sentence —
 * same context-reranked conversions (convertWordWithContext), same
 * context-promoted suggestion strip (getSuggestionsWithContext), same
 * next-word predictions. This is the test that failed to exist when the
 * Windows app shipped calling the engine without context, and the user
 * noticed the same engine typing different Bangla than their phone.
 */
class S130ContextParityTest {

    private fun composer(): Composer {
        TestEngine.boot()
        return Composer(AdapterComposerEngine)
    }

    /** Applies commit/delete actions the way the host document would. */
    private fun apply(doc: StringBuilder, actions: List<ComposerAction>) {
        for (action in actions) when (action) {
            is ComposerAction.Commit -> doc.append(action.text)
            is ComposerAction.DeleteBack -> repeat(minOf(action.count, doc.length)) {
                doc.deleteCharAt(doc.length - 1)
            }
            else -> Unit
        }
    }

    @Test
    fun theDocumentMatchesTheAndroidContextLaneWordForWord() {
        val c = composer()
        val doc = StringBuilder()
        val expected = StringBuilder()
        var prev1: String? = null
        var prev2: String? = null
        for (raw in listOf("ami", "tomake", "onek", "valobashi")) {
            // The Android reference, computed BEFORE the Windows lane commits
            // (so both lanes convert against identical engine state).
            val reference =
                SmartEngineAdapter.convertWordWithContext(raw, listOfNotNull(prev2, prev1)).bengali
            raw.forEach { apply(doc, c.handle(ComposerKey.Letter(it))) }
            apply(doc, c.handle(ComposerKey.Space))
            expected.append(reference).append(' ')
            prev2 = prev1
            prev1 = reference
        }
        assertEquals(expected.toString(), doc.toString())
    }

    @Test
    fun theSuggestionStripMatchesTheAndroidContextStrip() {
        val c = composer()
        "kmon".forEach { c.handle(ComposerKey.Letter(it)) }
        c.handle(ComposerKey.Space)
        // Second word, typed with the first as its context.
        "acho".forEach { c.handle(ComposerKey.Letter(it)) }
        val strip = c.refineCandidates(c.generation)
            .filterIsInstance<ComposerAction.Candidates>().last().list
        val prev = SmartEngineAdapter.convertWordWithContext("kmon", emptyList()).bengali
        val reference = SmartEngineAdapter
            .getSuggestionsWithContext("acho", listOf(prev), 5)
            .map { it.bengali }
            .toMutableList()
        if ("acho" !in reference) reference.add("acho") // the raw escape hatch
        assertEquals(reference, strip)
    }

    @Test
    fun predictionsAfterACommitMatchTheAdapter() {
        val c = composer()
        "ami".forEach { c.handle(ComposerKey.Letter(it)) }
        val actions = c.handle(ComposerKey.Space)
        val strip = actions.filterIsInstance<ComposerAction.Candidates>().last()
        val prev = SmartEngineAdapter.convertWordWithContext("ami", emptyList()).bengali
        val reference = SmartEngineAdapter.getNextWordPredictions(null, prev, 5).map { it.bengali }
        assertEquals(reference, strip.list)
        assertTrue(reference.isEmpty() == !strip.predictions)
    }
}
