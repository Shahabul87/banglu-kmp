package com.banglu.compiler

import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.util.ReverseTransliterator

data class PhoneticIndexRow(
    val key: String,
    val bengali: String,
    val frequency: Int,
    val tier: Int,    // 0 = Tier A (suggestible), 1 = Tier B (exact-match only)
    val priority: Int // 0 = canonical romanization, 1 = habit-alias key
)

/**
 * Nukta-folded, deduplicated word list. [words] preserves first-occurrence
 * order; [frequencies] is keyed by the folded form with the max frequency
 * across all raw spellings that fold to it.
 */
data class NuktaFoldResult(
    val words: List<String>,
    val frequencies: Map<String, Int>,
    val mergedCount: Int
)

data class IndexBuildReport(
    val totalWords: Int = 0,
    val roundTripOk: Int = 0,
    val totalRows: Int = 0,
    val droppedKeys: Int = 0,
    val wordsWithNoRows: Int = 0,
    val nuktaMerged: Int = 0,
    val canonicalRows: Int = 0,
    val habitAliasRows: Int = 0,
    val tierAWords: Int = 0,
    val tierBWords: Int = 0
) {
    val coveragePercent: Double
        get() = if (totalWords == 0) 0.0 else roundTripOk * 100.0 / totalWords
}

object PhoneticIndexBuilder {

    private const val TIER_A = 0
    private const val TIER_B = 1

    private const val PRIORITY_CANONICAL = 0
    private const val PRIORITY_HABIT = 1

    /**
     * Corpus frequency at or above which a word is suggestible (Tier A) even
     * when absent from the real-usage word list. Below this, absent words are
     * Tier B: reachable by exact key match only, never filling suggestion slots.
     */
    private const val SUGGESTIBLE_MIN_FREQUENCY = 60

    /** Hard bound on keys emitted per word (canonical + habit aliases). */
    private const val MAX_KEYS_PER_WORD = 32

    /**
     * The most recent report produced by the last [build] call.
     *
     * **Warning:** must be read immediately after the corresponding [build] call.
     * This is a single-threaded build tool — concurrent builds will overwrite
     * this field.
     */
    var lastReport: IndexBuildReport = IndexBuildReport()
        private set

    private val BENGALI_ONLY = Regex("^[\\u0980-\\u09FF]+$")
    private val ROMAN_ONLY = Regex("^[a-z]+$")

    /**
     * Fold decomposed nukta (base + U+09BC) in [words] using the engine's own
     * mapping ([ReverseTransliterator.foldNukta]) and merge duplicates: a word
     * whose folded form duplicates an earlier word is dropped, its frequency
     * merged (max) into the surviving folded entry. Engine-side comparisons
     * are nukta-folded post-F1, so the compiled `words` table must store the
     * folded form exactly once.
     */
    fun foldAndDedupe(words: List<String>, frequencies: Map<String, Int>): NuktaFoldResult {
        // Fold the frequency map keys first so a frequency recorded under either
        // encoding reaches the surviving folded word (max wins on collision).
        val foldedFrequencies = HashMap<String, Int>(frequencies.size * 2)
        for ((raw, freq) in frequencies) {
            val folded = ReverseTransliterator.foldNukta(raw.trim())
            foldedFrequencies[folded] = maxOf(foldedFrequencies[folded] ?: 0, freq)
        }

        val out = ArrayList<String>(words.size)
        val seen = HashSet<String>(words.size * 2)
        var merged = 0
        for (raw in words) {
            val folded = ReverseTransliterator.foldNukta(raw.trim())
            if (seen.add(folded)) out.add(folded) else merged++
        }
        return NuktaFoldResult(out, foldedFrequencies, merged)
    }

    /**
     * Build the phonetic index.
     *
     * @param words       Bengali word list (any nukta encoding; folded + deduped internally).
     * @param frequencies corpus frequency per word.
     * @param usageWords  words observed in real web usage (any nukta encoding) —
     *                    membership makes a word Tier A (suggestible) regardless
     *                    of corpus frequency.
     */
    fun build(
        words: List<String>,
        frequencies: Map<String, Int>,
        usageWords: Set<String> = emptySet()
    ): List<PhoneticIndexRow> {
        val folded = foldAndDedupe(words, frequencies)
        val usage = usageWords.mapTo(HashSet(usageWords.size * 2)) { ReverseTransliterator.foldNukta(it) }

        val rows = ArrayList<PhoneticIndexRow>(folded.words.size * 2)
        val seen = HashSet<String>(folded.words.size * 2)
        var roundTripOk = 0
        var total = 0
        var droppedKeys = 0
        var wordsWithNoRows = 0
        var canonicalRows = 0
        var habitAliasRows = 0
        var tierAWords = 0
        var tierBWords = 0

        for (word in folded.words) {
            if (word.length !in 2..18) continue
            if (!BENGALI_ONLY.matches(word)) continue
            if (word.endsWith("্")) continue
            total++

            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (CleanTransliterator.transliterate(canonical) == word) roundTripOk++

            val freq = folded.frequencies[word] ?: 0
            // Real-usage suggestion tiering: Tier A only for words people
            // actually use (seen in web usage, or corpus frequency >= 60).
            // Everything else stays typeable via exact keys but never fills
            // suggestion slots (the যেলা@1 junk class).
            val tier = if (word in usage || freq >= SUGGESTIBLE_MIN_FREQUENCY) TIER_A else TIER_B
            if (tier == TIER_A) tierAWords++ else tierBWords++

            // Visarga words are geminated by reverseWord itself (দুঃখ ->
            // "dukkh" -> alias "dukkho"); the ungeminated key ("dukh") is
            // intentionally NOT indexed — it belongs to the exact word দুখ.
            val aliases = aliasesFor(canonical)
            var rowsForWord = 0
            for (key in aliases) {
                if (key.length !in 2..24 || !ROMAN_ONLY.matches(key)) {
                    droppedKeys++
                    continue
                }
                if (!seen.add("$key $word")) continue
                val priority = if (key == canonical) PRIORITY_CANONICAL else PRIORITY_HABIT
                rows.add(PhoneticIndexRow(key, word, freq, tier, priority))
                if (priority == PRIORITY_CANONICAL) canonicalRows++ else habitAliasRows++
                rowsForWord++
            }
            if (rowsForWord == 0) wordsWithNoRows++
        }
        lastReport = IndexBuildReport(
            totalWords = total,
            roundTripOk = roundTripOk,
            totalRows = rows.size,
            droppedKeys = droppedKeys,
            wordsWithNoRows = wordsWithNoRows,
            nuktaMerged = folded.mergedCount,
            canonicalRows = canonicalRows,
            habitAliasRows = habitAliasRows,
            tierAWords = tierAWords,
            tierBWords = tierBWords
        )
        return rows
    }

    // =========================================================================
    // Habit-alias rule table
    // =========================================================================

    /** Ya-phala between a consonant and a vowel: users drop the y (সন্ধ্যা "sondhya" → "sondha"). */
    private val YA_PHALA_BEFORE_VOWEL = Regex("([bcdfghjklmnpqrstvwxz])y([aeiou])")

    /** Word-final ya-phala after a consonant: users drop the y (লক্ষ্য "lokkhy" → "lokkh"). */
    private val YA_PHALA_FINAL = Regex("([bcdfghjklmnpqrstvwxz])y$")

    /**
     * Ya-phala gemination class: the famous typing habit doubles the consonant
     * instead of writing the y (জন্য "jonyo" → "jonno", অন্য "onyo" → "onno").
     * 'h' is excluded — doubling the h of an aspirate digraph ("sondhya" →
     * "sondhha") is not a real habit; the ya-phala DROP covers those words.
     */
    private val YA_PHALA_GEMINATION = Regex("([bcdfgjklmnpqrstvwxz])y([aeiou])")

    /**
     * Bo-phola (্ব) after a consonant: ReverseTransliterator emits it as "w"
     * (স্বাস্থ্য → "swasthy", বিশ্বাস → "bishwas"). Only a w PRECEDED BY A
     * CONSONANT letter matches — vowel+w glides (হাওয়া-class keys) and
     * word-initial "w" are untouched. Two habits consume it:
     * - w_drop: ব-ফলা is barely pronounced; users type the spoken form
     *   (স্বাস্থ্য="sastho", বিশ্বাস="bishash").
     * - b_fola: AVRO-convention users spell it with "b" (স্বাস্থ্য="sbastho").
     */
    private val BO_PHOLA_W = Regex("([bcdfghjklmnpqrstvxz])w")

    /** A doubled consonant letter, for the lazy single-press habit (উত্তর "uttor" → "utor"). */
    private val DOUBLED_CONSONANT = Regex("([bcdfghjklmnpqrstvwxyz])\\1")

    /** য় glide between two vowels, dropped by lazy typists (phebruyari → phebruari). */
    private val VOWEL_Y_VOWEL = Regex("([aeiou])y([aeiou])")

    private const val VOWELS = "aeiou"

    private class HabitRule(val name: String, val transform: (String) -> String)

    /**
     * Declarative typing-habit rule table. Each rule maps an alias to a lazier
     * spelling of the same word; all rules produce PRIORITY-1 keys (the
     * canonical romanization alone is priority 0), so on key collision the
     * canonical owner always wins ("suru" → সুরু before শুরু) while habit keys
     * fill gaps ("sastho" → স্বাস্থ্য).
     *
     * Composition: rules are applied in table order, each to EVERY alias
     * produced so far (see [expand]), so combined collapses emerge as chains —
     * e.g. স্বাস্থ্যকর "swasthyokor" → w_drop → "sasthyokor" → ya_phala_drop →
     * "sasthokor"; জন্য "jony" → final_o → "jonyo" → gemination → "jonno";
     * জীবন "jiibon" → ii_collapse → "jibon" → j_to_z → "zibon". Order matters
     * only for chains: collapses first, conjunct habits next, then final_o and
     * gemination last (gemination needs the trailing inherent vowel in place).
     * Output is bounded by [MAX_KEYS_PER_WORD] and deduped.
     */
    private val HABIT_RULES: List<HabitRule> = listOf(
        // S27: inherent-o before the plain-ছ continuous (করছি -> "korochhi",
        // করছে -> "korochhe"); typists write "korchi"/"korsi". MUST run before
        // the chh-family rules below so their chains compose on the o-dropped
        // form (korchhi -> korchi/korci/korsi) — after them, "ochh" no longer
        // exists in the transformed aliases. Targeted at the -chh verb
        // morphology only, like verb_o_drop_te.
        HabitRule("verb_o_drop_chh") { it.replace("ochh", "chh") },
        // ছ is emitted "chh"; users type "c" (চ-style) or lazy "ch".
        HabitRule("chh_collapse") { it.replace("chh", "c") },
        HabitRule("h_lazy_chh") { it.replace("chh", "ch") },
        // S16 chat register: ছ written as স in continuous forms — বুঝতেছি
        // typed "bujtesi", পারতেছি "partesi". The whole "-tesi/-teci" dialect.
        HabitRule("s_for_chh") { it.replace("chh", "s") },
        // চ্ছ emits canonical "chch" (ঘুমাচ্ছ -> "ghumachch"); real typists
        // write "cch" (ghumacchi) or just "cc" (ghumacco). Chained so both
        // spellings key the word, and final_o composes ghumacco afterwards.
        HabitRule("chch_cch") { it.replace("chch", "cch") },
        HabitRule("cch_cc") { it.replace("cch", "cc") },
        // S27 chat register: চ্ছ written as ss — ইচ্ছা "issa", হচ্ছে "hosse",
        // খাচ্ছি "khassi". Same dialect family as s_for_chh below.
        HabitRule("chch_ss") { it.replace("chch", "ss") },
        // ী/ঈ → "ii", ূ/ঊ → "uu"; users omit the doubled vowel.
        HabitRule("ii_collapse") { it.replace("ii", "i") },
        HabitRule("uu_collapse") { it.replace("uu", "u") },
        // S16: reverse transliteration emits the inherent vowel before the
        // continuous suffix (বুঝতেছি -> "bujhotechhi", করতেস -> "korotes");
        // typists never write that o ("bujhtechi", "kortes"). Targeted at the
        // -te verb morphology only — a general medial-o drop was rejected for
        // collision noise (same reasoning as the final-o-drop rejection below).
        HabitRule("verb_o_drop_te") { it.replace("ote", "te") },
        // ঝ emits "jh"; users drop the h (বুঝি → "buji", ঝাল → "jal").
        // Must run BEFORE j_to_z: that rule doubles the alias set with
        // z-variants and would starve the 32-key budget for jh chains
        // (bujhotechhi -> ... -> bujtechi/bujteci/bujtesi).
        HabitRule("h_lazy_jh") { it.replace("jh", "j") },
        // য is emitted "z"; users overwhelmingly type "j" (যদি → "jodi") —
        // and the reverse: জ-words are reachable via "z" (জীবন → "zibon").
        HabitRule("z_to_j") { it.replace("z", "j") },
        HabitRule("j_to_z") { it.replace("j", "z") },
        // শ/ষ emit "sh"; lazy typists press a single "s" (শুরু → "suru").
        HabitRule("h_lazy_sh") { it.replace("sh", "s") },
        // ্ব: AVRO-style "b" spelling (sw→sb), or dropped entirely (spoken form).
        HabitRule("b_fola") { it.replace(BO_PHOLA_W, "$1b") },
        HabitRule("w_drop") { dropBoPhola(it) },
        // ্য: dropped (সন্ধ্যা → "sondha", লক্ষ্য → "lokkh").
        HabitRule("ya_phala_drop") { dropYaPhala(it) },
        // Doubled consonant pressed once (উত্তর "uttor" → "utor"). Heavy
        // collider — safe only because habit keys are priority 1 (canonical
        // owners win) and Tier B junk never reaches the suggestion strip.
        HabitRule("double_reduce") { it.replace(DOUBLED_CONSONANT, "$1") },
        // Trailing inherent vowel appended after a final consonant cluster
        // (শক্ত "shokt" → "shokto"). The inverse (dropping a final o) was
        // evaluated and rejected: it adds ~6% rows of pure collision noise
        // (verb-stem keys) and pushed the artifact over the 125 MB size gate.
        HabitRule("final_o") { withTrailingInherentO(it) },
        // ্য as gemination (জন্য "jonyo" → "jonno") — after final_o so the
        // jony → jonyo → jonno chain composes.
        HabitRule("ya_fola_gemination") { it.replace(YA_PHALA_GEMINATION, "$1$1$2") },
        // য় glide between vowels dropped (S8, y-drop study): ফেব্রুয়ারি
        // "phebruyari" → "phebruari", দুনিয়াতে "duniyate" → "duniate".
        HabitRule("vowel_glide_y_drop") { it.replace(VOWEL_Y_VOWEL, "$1$2") },
    )

    /**
     * Canonical key first, then habit aliases in rule-table order, deduped,
     * capped at [MAX_KEYS_PER_WORD].
     */
    internal fun aliasesFor(canonical: String): List<String> {
        val aliases = LinkedHashSet<String>()
        aliases.add(canonical)
        for (rule in HABIT_RULES) expand(aliases, rule.transform)
        return aliases.toList()
    }

    /**
     * Apply [rule] to every alias currently in [set], adding the results
     * (dedup via set) until the per-word key cap is reached.
     */
    private inline fun expand(set: LinkedHashSet<String>, rule: (String) -> String) {
        for (key in set.toList()) {
            if (set.size >= MAX_KEYS_PER_WORD) return
            set.add(rule(key))
        }
    }

    private fun dropYaPhala(key: String): String =
        key.replace(YA_PHALA_BEFORE_VOWEL, "$1$2").replace(YA_PHALA_FINAL, "$1")

    /**
     * Drop every bo-phola "w" (a w preceded by a consonant letter), iterating
     * to a fixpoint so stacked w's ("kww") collapse fully. Single-pass
     * Regex.replace skips a w that only becomes consonant-adjacent after the
     * previous drop.
     */
    private fun dropBoPhola(key: String): String {
        var current = key
        while (true) {
            val next = current.replace(BO_PHOLA_W, "$1")
            if (next == current) return current
            current = next
        }
    }

    /**
     * Append the final inherent vowel "o" when the key ends in a consonant
     * cluster (2+ consonant phonemes). Aspirate digraphs (kh/gh/ch/jh/th/
     * dh/ph/bh/sh) and "ng" count as a single phoneme.
     */
    private fun withTrailingInherentO(key: String): String {
        if (key.length < 3) return key
        val last = key.last()
        if (last in VOWELS) return key
        val prev = key[key.length - 2]
        val stem = when {
            last == 'h' && prev in "kgcjtdpbs" -> key.dropLast(2) // aspirate digraph
            last == 'g' && prev == 'n' -> key.dropLast(2)          // anusvara "ng"
            else -> key.dropLast(1)
        }
        if (stem.isEmpty() || stem.last() in VOWELS) return key
        return key + "o"
    }
}
