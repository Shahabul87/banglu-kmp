package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneticIndexBuilderTest {

    @Test
    fun buildsKeysForSimpleWords() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("আমি", "তুমি"),
            frequencies = mapOf("আমি" to 100, "তুমি" to 99)
        )
        val amiKeys = rows.filter { it.bengali == "আমি" }.map { it.key }
        assertTrue("ami" in amiKeys, "expected canonical key 'ami', got $amiKeys")
        assertEquals(0, rows.first { it.bengali == "আমি" }.tier) // freq>0 => Tier A
    }

    @Test
    fun tierBForWordsWithoutFrequency() {
        val rows = PhoneticIndexBuilder.build(words = listOf("আমি"), frequencies = emptyMap())
        assertEquals(1, rows.first().tier)
    }

    @Test
    fun chhWordsGetCAlias() {
        // ছবি reverses to chobi/chhobi family; lowercase scheme types it as cobi
        val rows = PhoneticIndexBuilder.build(words = listOf("ছবি"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue(keys.any { it.startsWith("c") && !it.startsWith("ch") },
            "expected a c-alias for chh word, got $keys")
    }

    @Test
    fun reportsRoundTripCoverage() {
        PhoneticIndexBuilder.build(words = listOf("আমি", "তুমি"), frequencies = emptyMap())
        assertTrue(PhoneticIndexBuilder.lastReport.totalWords == 2)
    }

    // -------------------------------------------------------------------------
    // New tests
    // -------------------------------------------------------------------------

    /**
     * ছত্রী reverses to "chhotrii" (probe-verified: chh + ii both present).
     * The fully-collapsed combined alias must be "cotri" (chh→c then ii→i).
     * This exercises the composed-collapse path in aliasesFor().
     *
     * Alias derivation:
     *   canonical  : "chhotrii"
     *   chh→c      : "cotrii"
     *   ii→i       : "chhotri"
     *   collapsed  : "cotri"   ← chh→c then ii→i applied in sequence
     */
    @Test
    fun combinedAliasForChhAndLongVowelWords() {
        // Probe finding: ReverseTransliterator.reverseWord("ছত্রী") = "chhotrii"
        // Contains both "chh" and "ii" → fully-collapsed alias = "cotri"
        val rows = PhoneticIndexBuilder.build(words = listOf("ছত্রী"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue(
            keys.any { it == "cotri" },
            "expected composed-collapse alias 'cotri' for ছত্রী (canonical 'chhotrii'), got $keys"
        )
    }


    /**
     * S16 chat-register chains (bujtecina study): বুঝতেছি reverses to
     * "bujhotechhi". The typed forms drop the medial inherent o
     * (verb_o_drop_te), the jh aspiration (h_lazy_jh), and write ছ as
     * ch/c/s — so bujtechi, bujteci, and bujtesi must all key the word.
     */
    @Test
    fun chatContinuousAliasesForBujhtechhi() {
        val rows = PhoneticIndexBuilder.build(words = listOf("বুঝতেছি"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        for (expected in listOf("bujhtechhi", "bujtechi", "bujteci", "bujtesi")) {
            assertTrue(expected in keys, "expected alias '$expected' for বুঝতেছি, got $keys")
        }
    }

    @Test
    fun filtersExcludeOutOfScopeWords() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("ও", "আমি্", "abc", "আমি আমি"),  // 1-char, hasanta-final, latin, contains space
            frequencies = emptyMap()
        )
        assertTrue(rows.isEmpty())
        assertEquals(0, PhoneticIndexBuilder.lastReport.totalWords)
    }

    /**
     * F1 corpus fix: দুঃখ used to reverse to "du:kh" (":" failed ROMAN_ONLY,
     * word got 0 rows). Visarga is now silent but geminates the following
     * consonant ("dukkh" + trailing-o alias "dukkho" — how users type it).
     *
     * S2 ADAPTATION (schema/rule-driven): the ungeminated "dukh" used to be
     * banned because it would shadow দুখ's exact key. The S2 double_reduce
     * habit rule now legitimately produces "dukh" — but only at PRIORITY_HABIT
     * (1), so দুখ (canonical owner of "dukh" at priority 0) still wins every
     * lookup; দুঃখ merely becomes reachable behind it. The shadowing concern
     * the old assertion guarded is now solved by the priority axis instead of
     * by omission.
     */
    @Test
    fun visargaWordsGetTypeableKeys() {
        val rows = PhoneticIndexBuilder.build(words = listOf("দুঃখ"), frequencies = emptyMap())
        val report = PhoneticIndexBuilder.lastReport
        assertEquals(1, report.totalWords)
        assertEquals(0, report.wordsWithNoRows)
        val keys = rows.map { it.key }
        assertTrue("dukkh" in keys, "expected canonical key 'dukkh', got $keys")
        assertTrue("dukkho" in keys, "expected geminated typing key 'dukkho', got $keys")
        // double_reduce alias exists but must never claim canonical priority.
        val dukhRow = rows.firstOrNull { it.key == "dukh" }
        assertEquals(1, dukhRow?.priority, "'dukh' must be a priority-1 habit alias, got $dukhRow")
    }

    /** F1 corpus fix: decomposed-nukta words (ড = ড + ়) must be indexed. */
    @Test
    fun decomposedNuktaWordsGetKeys() {
        val decomposedBoro = "বড়" // ব + ড + ় (decomposed বড়)
        val rows = PhoneticIndexBuilder.build(words = listOf(decomposedBoro), frequencies = emptyMap())
        assertEquals(0, PhoneticIndexBuilder.lastReport.wordsWithNoRows)
        assertTrue(rows.any { it.key == "bor" }, "expected key 'bor', got ${rows.map { it.key }}")
    }

    /** য emits canonical "z"; users overwhelmingly type "j" — alias required. */
    @Test
    fun zWordsGetJAlias() {
        val rows = PhoneticIndexBuilder.build(words = listOf("যদি"), frequencies = emptyMap())
        val keys = rows.map { it.key }
        assertTrue("zodi" in keys, "expected canonical 'zodi', got $keys")
        assertTrue("jodi" in keys, "expected j-alias 'jodi', got $keys")
    }

    /**
     * F1 corpus fix: 3-consonant clusters no longer leak hasanta, and the
     * natural typing for ya-phala / cluster-final words is among the keys.
     */
    @Test
    fun clusterWordsGetNaturalTypingKeys() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("যুক্তরাষ্ট্র", "লক্ষ্য", "সন্ধ্যা", "স্বাস্থ্য", "দারিদ্র্য", "স্ত্রী"),
            frequencies = emptyMap()
        )
        assertEquals(0, PhoneticIndexBuilder.lastReport.wordsWithNoRows)
        fun keysOf(word: String) = rows.filter { it.bengali == word }.map { it.key }
        assertTrue("juktorashtro" in keysOf("যুক্তরাষ্ট্র"), "got ${keysOf("যুক্তরাষ্ট্র")}")
        assertTrue("lokkho" in keysOf("লক্ষ্য"), "got ${keysOf("লক্ষ্য")}")
        assertTrue("sondha" in keysOf("সন্ধ্যা"), "got ${keysOf("সন্ধ্যা")}")
        assertTrue("swastho" in keysOf("স্বাস্থ্য"), "got ${keysOf("স্বাস্থ্য")}")
        assertTrue("daridro" in keysOf("দারিদ্র্য"), "got ${keysOf("দারিদ্র্য")}")
        assertTrue("stri" in keysOf("স্ত্রী"), "got ${keysOf("স্ত্রী")}")
    }

    /**
     * F7: general bo-phola (্ব) w-drop alias class. ReverseTransliterator
     * emits ্ব as "w" after a consonant, but users type the spoken form.
     *
     * Derivation for স্বাস্থ্যকর (probe-verified canonical "swasthyokor"):
     *   canonical    : "swasthyokor"
     *   bo-phola drop: "sasthyokor"   (s+w → s)
     *   ya-phala drop: "sasthokor"    (h+y+o → ho)
     * Both drops compose via expand(), so the chain emerges automatically.
     */
    @Test
    fun boPholaWordsGetWDropAliases() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("স্বাস্থ্যকর", "স্বপ্ন", "বিশ্বাস"),
            frequencies = emptyMap()
        )
        assertEquals(0, PhoneticIndexBuilder.lastReport.wordsWithNoRows)
        fun keysOf(word: String) = rows.filter { it.bengali == word }.map { it.key }
        // canonical keys still present
        assertTrue("swasthyokor" in keysOf("স্বাস্থ্যকর"), "got ${keysOf("স্বাস্থ্যকর")}")
        // composed collapse: w-drop + ya-phala drop
        assertTrue("sasthokor" in keysOf("স্বাস্থ্যকর"), "got ${keysOf("স্বাস্থ্যকর")}")
        // w-drop + trailing inherent o ("swopn" → "sopn" → "sopno")
        assertTrue("sopno" in keysOf("স্বপ্ন"), "got ${keysOf("স্বপ্ন")}")
        // w-drop mid-word ("bishwas" → "bishas")
        assertTrue("bishas" in keysOf("বিশ্বাস"), "got ${keysOf("বিশ্বাস")}")
    }

    /**
     * F7 guard: vowel+w glides must be untouched by the bo-phola drop.
     * Probe-verified: হাওয়া reverses to "haoya" — it contains no "w" at all
     * (ও/য় emit "o"/"y"), and no alias rule applies, so its key set is
     * exactly the canonical key. This pins that the w-drop rule introduces
     * no spurious variants for the হাওয়া class.
     */
    @Test
    fun glideWordsUnaffectedByBoPholaDrop() {
        val rows = PhoneticIndexBuilder.build(words = listOf("হাওয়া"), frequencies = emptyMap())
        // Canonical key intact (no bo-phola "w" mangling); the vowel-glide
        // y-drop habit (S8) adds "haoa" as a priority-1 alias.
        assertEquals("haoya", rows.first { it.priority == 0 }.key)
        assertEquals(listOf("haoa"), rows.filter { it.priority == 1 }.map { it.key })
    }

    @Test
    fun coveragePercentZeroWhenNoWords() {
        PhoneticIndexBuilder.build(words = emptyList(), frequencies = emptyMap())
        assertEquals(0.0, PhoneticIndexBuilder.lastReport.coveragePercent)
    }

    // -------------------------------------------------------------------------
    // S2: priority-tiered habit aliases, usage tier, nukta dedupe
    // -------------------------------------------------------------------------

    private fun keysOf(rows: List<PhoneticIndexRow>, word: String) =
        rows.filter { it.bengali == word }.map { it.key }

    private fun row(rows: List<PhoneticIndexRow>, word: String, key: String) =
        rows.firstOrNull { it.bengali == word && it.key == key }

    /** Canonical romanization is the unique priority-0 key; all aliases are priority 1. */
    @Test
    fun canonicalKeyIsUniquePriorityZero() {
        val rows = PhoneticIndexBuilder.build(words = listOf("যদি"), frequencies = emptyMap())
        val p0 = rows.filter { it.priority == 0 }
        assertEquals(listOf("zodi"), p0.map { it.key }, "exactly one priority-0 row (canonical)")
        assertTrue(rows.filter { it.key != "zodi" }.all { it.priority == 1 },
            "all habit aliases must be priority 1, got $rows")
    }

    /**
     * b_fola: স্বাস্থ্য canonical "swasthy" (probe-verified) — the AVRO-style
     * convention rewrites the bo-phola "w" as "b" (sw → sb). Chains:
     *   swasthy → b_fola → sbasthy → final_o → sbasthyo
     *   swasthy → b_fola → sbasthy → ya_drop → sbasth → final_o → sbastho
     * স্বাস্থ্যকর keeps the spoken-form chain (w_drop + ya_drop → sasthokor).
     */
    @Test
    fun bFolaProducesAvroStyleBKeys() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("স্বাস্থ্য", "স্বাস্থ্যকর"),
            frequencies = emptyMap()
        )
        val sasthoKeys = keysOf(rows, "স্বাস্থ্য")
        assertTrue("sbasthyo" in sasthoKeys, "expected b_fola key 'sbasthyo', got $sasthoKeys")
        assertTrue("sbastho" in sasthoKeys, "expected b_fola chain key 'sbastho', got $sasthoKeys")
        assertEquals(1, row(rows, "স্বাস্থ্য", "sbastho")?.priority)
        assertEquals(0, row(rows, "স্বাস্থ্য", "swasthy")?.priority, "canonical stays priority 0")
        val korKeys = keysOf(rows, "স্বাস্থ্যকর")
        assertTrue("sasthokor" in korKeys, "expected spoken-form chain 'sasthokor', got $korKeys")
        assertTrue("sbasthokor" in korKeys, "expected b_fola chain 'sbasthokor', got $korKeys")
    }

    /**
     * ya_fola_gemination: জন্য canonical "jony" (probe-verified) — the single
     * most famous Bangla typing habit doubles the consonant instead of the
     * ya-phala. Chain: jony → final_o → jonyo → gemination → jonno.
     * জন্যে (canonical "jonye") must be reachable as jonye AND jonne.
     */
    @Test
    fun yaFolaGeminationAliases() {
        val rows = PhoneticIndexBuilder.build(words = listOf("জন্য", "জন্যে"), frequencies = emptyMap())
        val jonyoKeys = keysOf(rows, "জন্য")
        assertTrue("jonno" in jonyoKeys, "expected gemination key 'jonno', got $jonyoKeys")
        assertEquals(1, row(rows, "জন্য", "jonno")?.priority)
        assertEquals(0, row(rows, "জন্য", "jony")?.priority, "canonical 'jony' stays priority 0")
        val jonyeKeys = keysOf(rows, "জন্যে")
        assertTrue("jonye" in jonyeKeys, "expected canonical 'jonye', got $jonyeKeys")
        assertTrue("jonne" in jonyeKeys, "expected gemination key 'jonne', got $jonyeKeys")
        assertEquals(0, row(rows, "জন্যে", "jonye")?.priority)
        assertEquals(1, row(rows, "জন্যে", "jonne")?.priority)
    }

    /** double_reduce: উত্তর canonical "uttor" stays priority 0; lazy "utor" is priority 1. */
    @Test
    fun doubleReduceAliases() {
        val rows = PhoneticIndexBuilder.build(words = listOf("উত্তর"), frequencies = emptyMap())
        assertEquals(0, row(rows, "উত্তর", "uttor")?.priority, "canonical 'uttor', got ${keysOf(rows, "উত্তর")}")
        assertEquals(1, row(rows, "উত্তর", "utor")?.priority, "expected habit key 'utor', got ${keysOf(rows, "উত্তর")}")
    }

    /** j_to_z: জীবন canonical "jiibon" → ii_collapse "jibon" → j_to_z "zibon" (all reachable). */
    @Test
    fun jToZSwapAliases() {
        val rows = PhoneticIndexBuilder.build(words = listOf("জীবন"), frequencies = emptyMap())
        val keys = keysOf(rows, "জীবন")
        assertTrue("jibon" in keys, "expected ii-collapse 'jibon', got $keys")
        assertTrue("zibon" in keys, "expected j→z swap 'zibon', got $keys")
        assertEquals(1, row(rows, "জীবন", "zibon")?.priority)
    }

    /** h_lazy: শুরু canonical "shuru" gains lazy "suru" at priority 1 (সুরু owns it at 0). */
    @Test
    fun hLazyShToS() {
        val rows = PhoneticIndexBuilder.build(words = listOf("শুরু", "সুরু"), frequencies = emptyMap())
        assertEquals(1, row(rows, "শুরু", "suru")?.priority, "got ${keysOf(rows, "শুরু")}")
        assertEquals(0, row(rows, "সুরু", "suru")?.priority, "got ${keysOf(rows, "সুরু")}")
    }

    /**
     * Real-usage tiering: tier 0 (suggestible) requires web-usage membership
     * OR corpus frequency >= 60; everything else is tier 1 (exact-match only).
     */
    @Test
    fun tierComesFromUsageEvidenceOrFrequencyFloor() {
        val rows = PhoneticIndexBuilder.build(
            words = listOf("আমি", "তুমি", "কলম"),
            frequencies = mapOf("আমি" to 5, "তুমি" to 5, "কলম" to 60),
            usageWords = setOf("আমি")
        )
        assertEquals(0, rows.first { it.bengali == "আমি" }.tier, "usage-listed word must be tier 0")
        assertEquals(1, rows.first { it.bengali == "তুমি" }.tier, "freq 5, no usage → tier 1")
        assertEquals(0, rows.first { it.bengali == "কলম" }.tier, "freq 60 floor → tier 0")
    }

    /** Usage-list matching is nukta-folded: decomposed usage entry marks the precomposed word. */
    @Test
    fun usageMatchingFoldsNukta() {
        val decomposedBoro = "\u09AC\u09A1\u09BC" // ব + ড + nukta (decomposed)
        val precomposedBoro = "বড়"      // ব + ড় (precomposed U+09DC)
        val rows = PhoneticIndexBuilder.build(
            words = listOf(precomposedBoro),
            frequencies = emptyMap(),
            usageWords = setOf(decomposedBoro)
        )
        assertEquals(0, rows.first().tier, "decomposed usage entry must mark the folded word tier 0")
    }

    /**
     * Nukta dedupe: a word whose folded form duplicates an earlier word is
     * skipped as a separate row set; frequencies merge (max); the surviving
     * rows carry the FOLDED Bengali form.
     */
    @Test
    fun nuktaDuplicatesMergeWithMaxFrequency() {
        val decomposedBoro = "\u09AC\u09A1\u09BC" // ব + ড + nukta (folds to precomposed)
        val precomposedBoro = "বড়"      // ব + ড় (precomposed U+09DC)
        val rows = PhoneticIndexBuilder.build(
            words = listOf(precomposedBoro, decomposedBoro),
            frequencies = mapOf(precomposedBoro to 10, decomposedBoro to 99)
        )
        assertEquals(1, PhoneticIndexBuilder.lastReport.totalWords, "duplicate folded word must merge")
        assertEquals(1, PhoneticIndexBuilder.lastReport.nuktaMerged)
        assertEquals(setOf(precomposedBoro), rows.map { it.bengali }.toSet(), "rows carry folded form")
        assertTrue(rows.all { it.frequency == 99 }, "merged frequency is the max, got $rows")
    }

    /** Per-word key emission is hard-capped at 32 (canonical + habit aliases). */
    @Test
    fun keysPerWordCappedAt32() {
        // Conjunct-heavy word with many applicable rules (sh, w, y, doubles).
        val rows = PhoneticIndexBuilder.build(
            words = listOf("বিশ্ববিদ্যালয়", "স্বাস্থ্যকর", "যুক্তরাষ্ট্র"),
            frequencies = emptyMap()
        )
        for ((word, wordRows) in rows.groupBy { it.bengali }) {
            assertTrue(wordRows.size <= 32, "$word emitted ${wordRows.size} keys (> 32)")
        }
    }
}
