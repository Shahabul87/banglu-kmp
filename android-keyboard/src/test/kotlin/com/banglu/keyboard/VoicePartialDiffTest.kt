package com.banglu.keyboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** S56: pins the voice live-region revision law (see VoicePartialDiff). */
class VoicePartialDiffTest {

    @Test
    fun emptyPartialIgnored() {
        assertNull(VoicePartialDiff.diff("আমি ভালো", ""))
    }

    @Test
    fun firstPartialAppendsWhole() {
        val p = VoicePartialDiff.diff("", "আমি")!!
        assertEquals(0, p.deleteCount)
        assertEquals("আমি", p.insert)
        assertEquals("আমি", p.newLiveText)
    }

    @Test
    fun identicalAndShorterInterimIgnored() {
        assertNull(VoicePartialDiff.diff("আমি ভালো", "আমি ভালো"))
        assertNull(VoicePartialDiff.diff("আমি ভালো", "আমি"))
    }

    @Test
    fun pureExtensionAppendsSuffixOnly() {
        val p = VoicePartialDiff.diff("আমি ভালো", "আমি ভালো আছি")!!
        assertEquals(0, p.deleteCount)
        assertEquals(" আছি", p.insert)
        assertEquals("আমি ভালো আছি", p.newLiveText)
    }

    @Test
    fun tailRewriteDeletesOnlyDivergingWords() {
        // Recognizer improves the last word — earlier words untouched.
        val p = VoicePartialDiff.diff("আমি ভালো আসি", "আমি ভালো আছি")!!
        assertEquals(" আসি".length, p.deleteCount)
        assertEquals(" আছি", p.insert)
        assertEquals("আমি ভালো আছি", p.newLiveText)
    }

    @Test
    fun midSentenceRewriteKeepsStablePrefix() {
        val p = VoicePartialDiff.diff("আজ সকালে আমি বাজারে", "আজ সকালে আমরা স্কুলে যাবো")!!
        assertEquals(" আমি বাজারে".length, p.deleteCount)
        assertEquals(" আমরা স্কুলে যাবো", p.insert)
        assertEquals("আজ সকালে আমরা স্কুলে যাবো", p.newLiveText)
    }

    @Test
    fun freshSegmentAppendsAndNeverDeletes() {
        // The tester's data-loss case: after internal endpointing the
        // recognizer starts a new hypothesis sharing nothing with the long
        // sentence on screen — the old code deleted the whole sentence.
        val long = "আজ সকালে আমি বাজারে গিয়ে অনেক কিছু কিনেছি"
        val p = VoicePartialDiff.diff(long, "তারপর বাসায় ফিরলাম")!!
        assertEquals(0, p.deleteCount)
        assertEquals(" তারপর বাসায় ফিরলাম", p.insert)
        assertEquals("$long তারপর বাসায় ফিরলাম", p.newLiveText)
    }

    @Test
    fun freshSegmentAfterTrailingSpaceNeedsNoExtraBoundary() {
        val p = VoicePartialDiff.diff("আমি ভালো ", "তুমি কেমন")!!
        assertEquals(0, p.deleteCount)
        assertEquals("তুমি কেমন", p.insert)
    }

    @Test
    fun firstWordRewritePreferredAsAppendOverDeletion() {
        // Diverges at word 0: appending may duplicate, but never loses text.
        val p = VoicePartialDiff.diff("কাল আসবো", "আজ আসবো না")!!
        assertEquals(0, p.deleteCount)
        assertEquals(" আজ আসবো না", p.insert)
    }
}

// ── S133: the revision-vs-fresh-segment discriminator ────────────────────
// Field report (2026-08-23, third round of this bug): "one sentence or words
// is repeated several times". The S120 trace itself was a FIRST-WORD revision
// ("অনেক" → "অ১নেক") — exact word equality saw no common prefix, called it a
// fresh segment, and appended the whole hypothesis after itself. Fuzzy word
// matching (small edit distance = the recognizer respelling the same audio)
// must fold such hypotheses into the revision paths.

class S133FirstWordRevisionTest {

    @Test
    fun firstWordRespellingRewritesInsteadOfDuplicating() {
        // The exact S120 smoking-gun shape: live text on screen, next
        // hypothesis revises the FIRST word's spelling and adds one word.
        // The fuzzy overlap proves it is the SAME utterance — the hypothesis
        // replaces the live region (word-for-word rewrite plus the new word),
        // it must NEVER append after it (that was the duplication).
        val p = VoicePartialDiff.diff("অনেক রাত হয়ে", "অ১নেক রাত হয়ে গেছে")!!
        assertEquals("অনেক রাত হয়ে".length, p.deleteCount)
        assertEquals("অ১নেক রাত হয়ে গেছে", p.insert)
        assertEquals("অ১নেক রাত হয়ে গেছে", p.newLiveText)
    }

    @Test
    fun aShorterRespelledInterimIsIgnored() {
        // Same revision, but the hypothesis is a SHORTER interim — owing
        // nothing. Without the fuzzy-covered guard this deleted live words.
        assertNull(VoicePartialDiff.diff("অনেক রাত হয়ে", "অ১নেক রাত"))
    }

    @Test
    fun respelledFirstWordWithRewrittenTailRewritesTheRegion() {
        val p = VoicePartialDiff.diff("অনেক রাত হয়ে", "অ১নেক রাত হবে")!!
        assertEquals("অনেক রাত হয়ে".length, p.deleteCount)
        assertEquals("অ১নেক রাত হবে", p.insert)
        assertEquals("অ১নেক রাত হবে", p.newLiveText)
    }

    @Test
    fun genuinelyNewSpeechStillAppendsAsAFreshSegment() {
        // The S56 data-loss protection must survive: a hypothesis sharing no
        // similar words is a fresh segment — append, never delete.
        val p = VoicePartialDiff.diff("আজ সকালে বাজারে", "তারপর বাসায় ফিরলাম")!!
        assertEquals(0, p.deleteCount)
        assertEquals(" তারপর বাসায় ফিরলাম", p.insert)
    }
}
