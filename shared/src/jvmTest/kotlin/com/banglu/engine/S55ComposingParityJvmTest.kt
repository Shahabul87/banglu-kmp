package com.banglu.engine

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S55 (audit-fix round, docs/audits/audit-android-keyboard-2026-07-16.md
 * F-ANDROID-001/002/003, F-WEB-001/002): the live composing preview
 * (SmartEngine.convertForComposing / getCompositionPreview, and the
 * adapter's preference-aware overload the Android IME's async refine calls)
 * must show EXACTLY what Space commits.
 *
 * Root cause (traced against the real dictionary.sqlite, not assumed):
 * `convertWordRaw` resolves lexicon-known words with no Bengali dictionary/
 * corpus/section candidate via `tryEnglishLexicon` UNCONDITIONALLY (no
 * validator needed — SmartEngine.kt convertWordRaw, the "Lexicon words are
 * English even when EnglishDetector doesn't know them" step). The separate
 * `convertForComposing` pipeline had no mirror of that step at all, so it
 * fell through to the raw pattern/CLEAN_TRANSLITERATION floor instead
 * (callback -> ছাল্ল্বাছ্ক, motivation -> মতিভাতিওন) while Space, via
 * convertWord/convertWordRaw, produced the correct loanword. Separately,
 * convertWord's wrapper ALSO carries a validator-gated "junk-rescue" for
 * cases where the pipeline DID produce a real (but low-frequency,
 * unevidenced) Bengali guess and the lexicon is a strictly better-attested
 * competitor (there/read/price-class words) — that check is now a shared
 * private helper (`tryJunkLexiconRescue`) called from both convertWord and
 * convertForComposing so the two paths can never drift again. Finally, the
 * ADAPTER's preference layer (applyUserPreference, learned/explicit picks)
 * was applied in `SmartEngineAdapter.convertWord` but never in
 * `SmartEngineAdapter.convertForComposing`/`getCompositionPreview`.
 */
class S55ComposingParityJvmTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun callbackComposingPreviewMatchesCommit() {
        val engine = ConjunctSolutionRoundJvmTest.engine
        engine.clearCache()
        val commit = engine.convertWord("callback")
        assertEquals("কলব্যাক", commit.bengali)
        assertEquals(commit.bengali, engine.convertForComposing("callback").bengali,
            "F-ANDROID-001: composing preview must match Space commit for 'callback'")
        assertEquals(commit.bengali, engine.getCompositionPreview("callback"))
    }

    @Test
    fun motivationComposingPreviewMatchesCommit() {
        val engine = ConjunctSolutionRoundJvmTest.engine
        engine.clearCache()
        val commit = engine.convertWord("motivation")
        assertEquals("মোটিভেশন", commit.bengali)
        assertEquals(commit.bengali, engine.convertForComposing("motivation").bengali,
            "F-ANDROID-002: composing preview must match Space commit for 'motivation'")
        assertEquals(commit.bengali, engine.getCompositionPreview("motivation"))
    }

    /**
     * F-ANDROID-003: after an explicit learned pick, the adapter's composing
     * preview (what the IME's async refine shows) must agree with the
     * adapter's commit path — both go through applyUserPreference now.
     */
    @Test
    fun learnedPreferencePreviewMatchesAdapterCommit() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)
        SmartEngineAdapter.onWordSelected("ki", "কী", explicitChoice = true)

        val commit = SmartEngineAdapter.convertWord("ki").bengali
        assertEquals("কী", commit)
        assertEquals(commit, SmartEngineAdapter.convertForComposing("ki").bengali)
        assertEquals(commit, SmartEngineAdapter.getCompositionPreview("ki"))
    }

    /** Sacred pins: the fix must not disturb existing WYSIWYG-critical words. */
    @Test
    fun sacredPinsUnchangedThroughComposing() {
        val engine = ConjunctSolutionRoundJvmTest.engine
        engine.clearCache()
        assertEquals("কাচ্চি", engine.convertForComposing("kacci").bengali)
        assertEquals("কেমন", engine.convertForComposing("kemon").bengali)
        assertEquals("নামে", engine.convertForComposing("name").bengali)
        assertEquals("কাচছি", engine.convertForComposing("kassi").bengali)
    }

    /**
     * The equality law under lite/slim (no validator loaded — the web/
     * extension/macOS-IME tier and the low-RAM Android path): composing must
     * equal commit for whatever the validator-free pipeline actually
     * produces, not a hardcoded "pretty word." callback/motivation resolve
     * via the validator-free lexicon fallback even in this mode (closes
     * F-WEB-001/002); there/read/price were already validator-free-safe and
     * are pinned here as a no-regression guard.
     */
    @Test
    fun liteModeComposingMatchesCommitForLexiconWords() {
        val liteEngine = SmartEngine()
        liteEngine.initializeSync()
        liteEngine.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)

        for (word in listOf("callback", "motivation", "there", "read", "price")) {
            val commit = liteEngine.convertWord(word).bengali
            val preview = liteEngine.convertForComposing(word).bengali
            assertEquals(commit, preview, "lite-mode equality law violated for '$word'")
        }
        assertEquals("কলব্যাক", liteEngine.convertWord("callback").bengali)
        assertEquals("মোটিভেশন", liteEngine.convertWord("motivation").bengali)
    }

    /**
     * I3 (reviewer, post-approval hardening): in FULL mode (validator
     * loaded), these keys exercise the validator-GATED rescue mirror
     * (tryJunkLexiconRescue), not the validator-free unconditional lexicon
     * fallback that liteModeComposingMatchesCommitForLexiconWords pins.
     *
     * NOTE on word selection: the reviewer's original suggestion cited
     * "there"/"read"/"price". A full 20,000-key english_lexicon scan
     * (S55ScanProbeJvm, not committed) showed those three do NOT exercise
     * this mirror — convertForComposingCore's own dictionary/corpus layer
     * already lands on the identical answer convertWord finds independently,
     * so disabling the wrapper's rescue call left them passing. The scan
     * found the real set: astrid/batten/breeder/burma/cato/cool/denial (and
     * ~19 more) diverge from commit UNLESS the rescue mirror runs — pinned
     * below with the ones verified by disabling the mirror locally.
     *
     * RED evidence (recorded, not fabricated): with
     * `convertForComposing`'s wrapper temporarily forced to `return raw`
     * (bypassing `tryJunkLexiconRescue` entirely), this test failed:
     *   expected:<[অ্যাস্ট্রিড]> but was:<[আস্ট্রিদ]>  (astrid)
     * Restoring the `tryJunkLexiconRescue(key, raw) ?: raw` call makes it
     * pass again. Full RED/GREEN transcript in
     * .superpowers/sdd/s55-task-1-report.md.
     */
    @Test
    fun rescueMirrorFullModeEquality() {
        val engine = ConjunctSolutionRoundJvmTest.engine
        engine.clearCache()
        for (w in listOf("astrid", "batten", "breeder", "burma", "cato", "cool", "denial")) {
            assertEquals(
                engine.convertWord(w).bengali, engine.convertForComposing(w).bengali,
                "composing must equal commit for '$w' (validator-gated rescue mirror)"
            )
        }
    }

    /**
     * I1 (reviewer, post-approval hardening): the SAME 20,000-key scan found
     * the inverse failure mode — composing's rescue mirror, before the
     * guards were shared, OVERRODE store-protected commit words with a
     * different lexicon answer (antenna's commit correctly keeps the
     * store-owned আনতেননা reading; an ungated rescue swapped in অ্যান্টেনা).
     * Pinned here as the regression guard for [isProtectedFromJunkRescue]
     * being consulted from both convertWord AND tryJunkLexiconRescue.
     */
    @Test
    fun guardedResultsSurviveTheRescueMirrorUnchanged() {
        val engine = ConjunctSolutionRoundJvmTest.engine
        engine.clearCache()
        for (w in listOf("antenna", "archer", "astro", "balle", "blade", "brian", "charles")) {
            assertEquals(
                engine.convertWord(w).bengali, engine.convertForComposing(w).bengali,
                "guard-protected commit word for '$w' must not be overridden by the composing rescue mirror"
            )
        }
    }
}
