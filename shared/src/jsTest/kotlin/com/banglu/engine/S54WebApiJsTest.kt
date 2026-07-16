package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S54: banglu-web migration surface — parse/context/preview/predictions/
 * custom-word wrappers added to BangluWebEngine so the web homepage and
 * dashboard editor can move off the legacy TS engine. Seed-only engine
 * (no slim dictionary attached) — same fixture convention as
 * S51LearningJsTest: plausible pairs, known seed words.
 */
class S54WebApiJsTest {

    @Test
    fun parseConvertsEveryWordAndKeepsWhitespace() {
        BangluWebEngine.initSeed()
        // "ami" -> আমি, "valo" -> ভালো are both seed words (SeedData.kt).
        assertEquals("আমি ভালো", BangluWebEngine.parse("ami valo"))
    }

    @Test
    fun convertWithContextIsNonCrashingAndMatchesPlainConvertWithoutContext() {
        BangluWebEngine.initSeed()
        // No context supplied -> identical to convert().
        assertEquals(
            BangluWebEngine.convert("ami"),
            BangluWebEngine.convertWithContext("ami", "", "")
        )
        // Seed-only engine has no bigram model attached (attachSlimDictionary
        // was never called), so rerankWithContext's bigramModel.isLoaded()
        // guard makes context a documented no-op passthrough here — this
        // still proves the wrapper doesn't crash and returns a sane result.
        assertEquals(
            "আমি",
            BangluWebEngine.convertWithContext("ami", "কেমন", "তুমি")
        )
    }

    @Test
    fun suggestionsWithContextLeadsWithTheTopRankedWord() {
        BangluWebEngine.initSeed()
        val plain = BangluWebEngine.suggestions("taka", 5)
        val withContext = BangluWebEngine.suggestionsWithContext("taka", "", "", 5)
        assertTrue(withContext.isNotEmpty())
        assertEquals(plain.first(), withContext.first())
        assertTrue(withContext.size <= 5)
    }

    @Test
    fun compositionPreviewResolvesAFullyTypedSeedWord() {
        BangluWebEngine.initSeed()
        assertEquals("আমি", BangluWebEngine.compositionPreview("ami"))
        // Must not crash on empty input.
        assertEquals("", BangluWebEngine.compositionPreview(""))
    }

    @Test
    fun nextWordPredictionsUsesTheStaticOpenerFallbackOnSeedOnlyEngine() {
        BangluWebEngine.initSeed()
        // "আমি" is a FALLBACK_NEXT_WORDS opener (SmartEngine.kt) — even
        // without a bigram model attached, the static fallback fires.
        val predictions = BangluWebEngine.nextWordPredictions("আমি", 5)
        assertTrue(predictions.isNotEmpty(), "expected static fallback predictions for আমি")
        assertTrue(predictions.size <= 5)

        // A word with no corpus/user/fallback data returns an empty array,
        // not a crash.
        val none = BangluWebEngine.nextWordPredictions("জিলিপিফুলঝুরি", 5)
        assertEquals(0, none.size)
    }

    @Test
    fun addCustomWordTeachesAPlausiblePairAndIsQueryableImmediately() {
        BangluWebEngine.initSeed()
        // plausible pair (passes isPlausibleDynamicMapping): reverse(দিবো) ≈ "dibo"
        BangluWebEngine.addCustomWord("dibo", "দিবো")
        assertEquals("দিবো", BangluWebEngine.convert("dibo"))
    }

    @Test
    fun addCustomWordRejectsAnImplausiblePairSilently() {
        BangluWebEngine.initSeed()
        val before = BangluWebEngine.convert("xyzzy123")
        // "xyzzy123" has no phonetic overlap with "কমলা" — isPlausibleDynamicMapping
        // must reject it; addWord no-ops rather than poisoning the dictionary.
        BangluWebEngine.addCustomWord("xyzzy123", "কমলা")
        assertEquals(before, BangluWebEngine.convert("xyzzy123"))
    }
}
