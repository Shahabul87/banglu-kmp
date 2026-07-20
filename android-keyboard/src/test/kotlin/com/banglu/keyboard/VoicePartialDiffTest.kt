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
