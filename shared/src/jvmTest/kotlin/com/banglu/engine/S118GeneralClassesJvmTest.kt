package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/**
 * S118 — tester round (osojjo/otibo/ovhab report): three GENERAL class fixes.
 *
 * 1. hy-gemination compiler rules: হ্য after a vowel is pronounced "jjh" —
 *    every হ্য word now owns its jj/jjh/jy spellings (previously only
 *    ঐতিহ্য via a manual alias; অসহ্য was unreachable except letter-wise
 *    "osohy").
 * 2. Echo-vowel store retry: single-final-consonant tatsama words (অতীব)
 *    never get a compiler trailing-o twin (cluster-only size gate) — the
 *    engine now retries the o-less key when the typed key owns nothing.
 * 3. v/vh→bh query normalization: ভ typed as v (Avro) or vh now reaches
 *    the store's canonical bh keys (ovhab previously → ওহাব garbage).
 *    Chosen over compiler aliases: bh→v/vh branches measured +34MB.
 *
 * Plus the glue-stem evidence law: a particle (তো/না/ও…) may only attach to
 * a stem with REAL evidence (Tier-A store row or validator frequency above
 * the ≤1 junk band) — "shushto" glued "শুষ তো" from a freq-1 squatter.
 */
class S118GeneralClassesJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    // ── 1. hy-gemination class ──────────────────────────────────────────────

    @Test
    fun osojjoFamilyReachesOsohyo() {
        assertEquals("অসহ্য", engine.convertWord("osojjo").bengali, "osojjo")
        assertEquals("অসহ্য", engine.convertWord("osojjho").bengali, "osojjho")
        // the letter-wise canonical stays
        assertEquals("অসহ্য", engine.convertWord("osohjo").bengali, "osohjo")
    }

    @Test
    fun sohjoFamilyReachesSohy() {
        assertEquals("সহ্য", engine.convertWord("sojjo").bengali, "sojjo")
        assertEquals("সহ্য", engine.convertWord("sojjho").bengali, "sojjho")
    }

    @Test
    fun hyClassGeneralizesBeyondTheReportedWord() {
        // গ্রাহ্য "grajjho" — the class rule, not a per-word alias
        assertEquals("গ্রাহ্য", engine.convertWord("grajjho").bengali, "grajjho")
        // ঐতিহ্য keeps working (manual alias superseded by the rule)
        assertEquals("ঐতিহ্য", engine.convertWord("oitijjho").bengali, "oitijjho")
    }

    @Test
    fun osojjoSuggestionsCarryTheWord() {
        val strip = engine.getSuggestions("osojjo", 6).map { it.bengali }
        assertTrue("অসহ্য" in strip, "osojjo strip must contain অসহ্য, got $strip")
    }

    // ── 2. echo-vowel retry ─────────────────────────────────────────────────

    @Test
    fun otiboEchoVowelResolvesOtib() {
        assertEquals("অতীব", engine.convertWord("otibo").bengali, "otibo")
        assertEquals("অতীব", engine.convertWord("atibo").bengali, "atibo (a-onset)")
    }

    @Test
    fun echoRetryNeverShadowsRealOwners() {
        // koto owns কতো outright — the retry must not fire on owned keys
        assertEquals("কতো", engine.convertWord("koto").bengali, "koto")
        // bolo is a real word key, not a retry to বল
        assertNotEquals("", engine.convertWord("bolo").bengali)
    }

    // ── 3. ভ as v / vh ─────────────────────────────────────────────────────

    @Test
    fun bhClassReachableViaVAndVh() {
        assertEquals("অভাব", engine.convertWord("ovab").bengali, "ovab")
        assertEquals("অভাব", engine.convertWord("ovhab").bengali, "ovhab")
        assertEquals(engine.convertWord("bhalo").bengali, engine.convertWord("vhalo").bengali, "vhalo == bhalo")
        assertEquals(engine.convertWord("bhitore").bengali, engine.convertWord("vhitore").bengali, "vhitore == bhitore")
    }

    // ── 4. glue-stem evidence law ───────────────────────────────────────────

    @Test
    fun junkStemNeverHostsParticle() {
        // shushto: শুষ (freq-1 squatter) must not produce "শুষ তো"
        val r = engine.convertWord("shushto")
        assertTrue(' ' !in r.bengali, "shushto must not be a glued split, got ${r.bengali}")
    }

    @Test
    fun realGlueStemsKeepWorking() {
        assertEquals("বলবোনে", engine.convertWord("bolbone").bengali, "bolbone")
        assertEquals("হবে তো", engine.convertWord("hobeto").bengali, "hobeto")
        assertEquals("ধাপগুলি", engine.convertWord("dhapoguli").bengali, "dhapoguli")
    }

    // ── sacred pins re-asserted in-round (invariant #6) ────────────────────

    @Test
    fun sacredPinsUntouched() {
        assertEquals("কাচ্চি", engine.convertWord("kacci").bengali, "kacci")
        assertEquals("কাচছি", engine.convertWord("kassi").bengali, "kassi")
        assertEquals("জোস", engine.convertWord("jos").bengali, "jos")
    }
}
