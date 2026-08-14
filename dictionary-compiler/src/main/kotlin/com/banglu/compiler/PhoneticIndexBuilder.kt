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
            val manualKeys = foldedManualAliases[word].orEmpty()
            // S78 (chandrabindu round): ঁ romanizes to "n" (বেঁচে -> "benche"),
            // but casual typists drop the nasal entirely ("beche", "pach",
            // "dat"). Seed the alias chains with the nasal-dropped
            // romanization so every habit rule composes on it too.
            val nasalSeed = if ('ঁ' in word) {
                ReverseTransliterator.reverseWord(word.replace("ঁ", "")).lowercase()
            } else null
            val aliases = aliasesFor(canonical, nasalSeed, bengali = word) + manualKeys
            val bofolaCanonical = if ("্ব" in word) {
                bofolaCanonicalKeys(word, listOfNotNull(canonical, nasalSeed))
            } else emptySet()
            var rowsForWord = 0
            for (key in aliases) {
                if (key.length !in 2..24 || !ROMAN_ONLY.matches(key)) {
                    droppedKeys++
                    continue
                }
                if (!seen.add("$key $word")) continue
                // Curated manual aliases are deliberate ownership decisions —
                // canonical priority, or an extended-dict junk twin on the
                // same key outranks them (মুস্কিল@65 vs মুশকিল@70 class).
                val priority = if (key == canonical || key in manualKeys || key in bofolaCanonical) {
                    PRIORITY_CANONICAL
                } else PRIORITY_HABIT
                rows.add(PhoneticIndexRow(key, word, freq, tier, priority))
                if (priority == PRIORITY_CANONICAL) canonicalRows++ else habitAliasRows++
                rowsForWord++
            }
            if (rowsForWord == 0) wordsWithNoRows++
        }
        promoteModernChhOverArchaicCc(rows)
        promoteNasalTwinOverPlain(rows)
        promoteHasantaFreeTwinOverConjunct(rows)
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

    /**
     * S33: archaic চ্চ continuous forms (হচ্চে, পাচ্চি, যাচ্চে — the Kolkata
     * literary register) romanize straight to "cc" keys, so they own those
     * keys at canonical priority and shadow the modern চ্ছ spelling the whole
     * userbase means (hocce → হচ্চে over হচ্ছে@89). When the same key also
     * maps to a strictly more frequent word that differs ONLY by চ্চ → চ্ছ,
     * promote that row to canonical priority — the engine's (tier, priority,
     * freq) order then surfaces the modern word first while the archaic form
     * stays reachable one slot down. কাচ্চি-style keys are untouched: the
     * dish outranks কাচছি on frequency, so no promotion happens there.
     */
    private fun promoteModernChhOverArchaicCc(rows: ArrayList<PhoneticIndexRow>) {
        val archaicOwners = HashMap<String, PhoneticIndexRow>()
        for (row in rows) {
            if (row.priority == PRIORITY_CANONICAL && "চ্চ" in row.bengali) {
                val existing = archaicOwners[row.key]
                if (existing == null || row.frequency > existing.frequency) {
                    archaicOwners[row.key] = row
                }
            }
        }
        if (archaicOwners.isEmpty()) return
        var promoted = 0
        for (i in rows.indices) {
            val row = rows[i]
            if (row.priority != PRIORITY_HABIT) continue
            val owner = archaicOwners[row.key] ?: continue
            if (row.tier != owner.tier) continue
            if (row.frequency <= owner.frequency) continue
            if (owner.bengali.replace("চ্চ", "চ্ছ") != row.bengali) continue
            rows[i] = row.copy(priority = PRIORITY_CANONICAL)
            promoted++
            println("  chh-promote: '${row.key}' ${owner.bengali}@${owner.frequency} -> ${row.bengali}@${row.frequency}")
        }
        if (promoted > 0) println("  chh-promote: $promoted key(s) now surface the modern চ্ছ form first")
    }

    /**
     * S78: the nasal-drop seed above gives chandrabindu words habit-alias rows
     * under the key casual typists actually press ("beche", "pach", "dat") —
     * but the plain twin owns that key at canonical priority (পাচ@64 shadows
     * পাঁচ@95 on "pach"). Mirror of the S33 chh-promote guard: when a habit
     * row differs from the same-key canonical owner ONLY by chandrabindu and
     * is strictly more frequent, promote it to canonical priority so the
     * engine's (tier, priority, freq) order surfaces the nasal word first
     * while the plain twin stays one slot down. বেচে/বেছে-style keys where
     * the plain owner is genuinely more frequent are untouched.
     */
    private fun promoteNasalTwinOverPlain(rows: ArrayList<PhoneticIndexRow>) {
        val plainOwners = HashMap<String, PhoneticIndexRow>()
        for (row in rows) {
            if (row.priority == PRIORITY_CANONICAL && 'ঁ' !in row.bengali) {
                val existing = plainOwners[row.key]
                if (existing == null || row.frequency > existing.frequency) {
                    plainOwners[row.key] = row
                }
            }
        }
        if (plainOwners.isEmpty()) return
        var promoted = 0
        for (i in rows.indices) {
            val row = rows[i]
            if (row.priority != PRIORITY_HABIT || 'ঁ' !in row.bengali) continue
            val owner = plainOwners[row.key] ?: continue
            if (row.tier != owner.tier) continue
            if (row.frequency <= owner.frequency) continue
            if (row.bengali.replace("ঁ", "") != owner.bengali) continue
            rows[i] = row.copy(priority = PRIORITY_CANONICAL)
            promoted++
        }
        if (promoted > 0) println("  nasal-promote: $promoted key(s) now surface the chandrabindu form first")
    }

    /**
     * S100 (marle class, unlocked by the chat-register frequencies): a
     * conjunct word can canonically own a key (মার্লে romanizes straight to
     * "marle") while the everyday hasanta-free verb (মারলে) only reaches it
     * through a habit alias — and priority outranks frequency, so the
     * loanword squatter wins forever. Mirror of the S78 nasal-twin pass:
     * when the habit row IS the owner minus its hasanta(s), same tier, and
     * STRICTLY more frequent (the chat register finally makes this true for
     * verbs), it takes canonical priority; the conjunct owner keeps its row
     * one slot down. কর্তা@75 vs করতা@60 stays untouched — frequency gates.
     */
    private fun promoteHasantaFreeTwinOverConjunct(rows: ArrayList<PhoneticIndexRow>) {
        val conjunctOwners = HashMap<String, PhoneticIndexRow>()
        for (row in rows) {
            if (row.priority == PRIORITY_CANONICAL && '্' in row.bengali) {
                val existing = conjunctOwners[row.key]
                if (existing == null || row.frequency > existing.frequency) {
                    conjunctOwners[row.key] = row
                }
            }
        }
        if (conjunctOwners.isEmpty()) return
        var promoted = 0
        for (i in rows.indices) {
            val row = rows[i]
            if (row.priority != PRIORITY_HABIT || '্' in row.bengali) continue
            val owner = conjunctOwners[row.key] ?: continue
            if (row.tier != owner.tier) continue
            if (row.frequency <= owner.frequency) continue
            if (row.bengali != owner.bengali.filterNot { it == '্' }) continue
            rows[i] = row.copy(priority = PRIORITY_CANONICAL)
            promoted++
        }
        if (promoted > 0) println("  hasanta-promote: $promoted key(s) now surface the everyday form first")
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

    // S103 bo-fola: medial-only (never at index 0 — the initial class drops
    // the b instead of doubling).
    private val MEDIAL_TB = Regex("(?<=.)tb")
    private val MEDIAL_DB = Regex("(?<=.)db")
    private val INITIAL_BOFOLA_PREFIXES = listOf("dhb", "tb", "db", "jb", "zb", "sb")

    /** য় glide between two vowels, dropped by lazy typists (phebruyari → phebruari). */
    private val VOWEL_Y_VOWEL = Regex("([aeiou])y([aeiou])")

    /** স্য before a vowel, for the S33 cc chat spelling (somosya → somocca). */
    private val SYA_BEFORE_VOWEL = Regex("sy([aeiou])")

    private const val VOWELS = "aeiou"

    /**
     * S59: curated per-word extra keys the habit-rule chains cannot derive —
     * casual spellings real typists use for specific words. Inserted at
     * habit-alias priority; the word must exist in the corpus or the row is
     * silently skipped. Keep this list SMALL and evidence-backed (each entry
     * came from a reproduced miss on the slim/full tier).
     */
    private val MANUAL_ALIASES: Map<String, List<String>> = mapOf(
        // মূর্ধন্য-ষ round (tester drawing, 2026-07-21): word-initial ষ.
        "ষাঁড়" to listOf("shar", "shnar"),
        "মুশকিল" to listOf("mushkil", "muskil"),
        "ঐতিহ্য" to listOf("oitijjho", "oitijho"),
        "ঔষুধ" to listOf("oushud"),
    )

    /** MANUAL_ALIASES with nukta-folded keys, matching the folded words the
     *  build loop iterates (ড়/ঢ়/য় normalization). */
    private val foldedManualAliases: Map<String, List<String>> by lazy {
        MANUAL_ALIASES.mapKeys { ReverseTransliterator.foldNukta(it.key) }
    }

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
        // S33 chat register: the স্য gemination class also written cc —
        // সমস্যা "somocca" (tester 2026-07-12: "সাজেশনেও আসছে না"). Same
        // ss≡cc equivalence users already expect from the চ্ছ family above.
        HabitRule("sya_cc") { it.replace(SYA_BEFORE_VOWEL, "cc$1") },
        // ী/ঈ → "ii", ূ/ঊ → "uu"; users omit the doubled vowel.
        HabitRule("ii_collapse") { it.replace("ii", "i") },
        HabitRule("uu_collapse") { it.replace("uu", "u") },
        // S16: reverse transliteration emits the inherent vowel before the
        // continuous suffix (বুঝতেছি -> "bujhotechhi", করতেস -> "korotes");
        // typists never write that o ("bujhtechi", "kortes"). Targeted at the
        // -te verb morphology only — a general medial-o drop was rejected for
        // collision noise (same reasoning as the final-o-drop rejection below).
        HabitRule("verb_o_drop_te") { it.replace("ote", "te") },
        // S79 (tester: parle -> পার্লে, verb missing from its own key): the
        // same inherent-o appears before the past/conditional -l suffixes
        // (পারলে -> "parole", পারলেন -> "parolen", করলাম -> "korolam") and the
        // future -b suffixes (পারবে -> "parobe", পারবোনা -> "parobona");
        // typists never write it ("parle", "korlam", "parbe"). Same targeted
        // scope as verb_o_drop_te — suffix-onset o only, never a general
        // medial-o drop.
        HabitRule("verb_o_drop_l") {
            it.replace("ole", "le").replace("olo", "lo").replace("ola", "la")
        },
        HabitRule("verb_o_drop_b") {
            it.replace("obo", "bo").replace("obe", "be")
        },
        // S84 (tester: kortam returned করিতাম/কর্তাম — করতাম only reachable via
        // "korotam"): the same inherent-o precedes the habitual-past -t
        // morphology (করতাম "korotam", করত "koroto", বলতা "bolota"); typists
        // write "kortam"/"korto". "ota" also covers -otam via one replace.
        // Same targeted suffix-onset scope as the -te/-l/-b siblings above.
        HabitRule("verb_o_drop_t") { key ->
            // করত emits "korot" (final inherent vowel unwritten); typists say
            // "korto". Restore the trailing o first so the medial drop lands.
            val restored = if (key.endsWith("ot")) key + "o" else key
            restored.replace("ota", "ta").replace("oto", "to")
        },
        // S81 (tester: dolfin composed দল্ফিন — ডলফিন only reachable via
        // "dolophin"): the inherent o before ফ is never typed (দলফিন,
        // আলফাজ-class loanwords). MUST run before ph_to_f so the chain
        // composes dolophin -> dolphin -> dolfin.
        HabitRule("o_drop_ph") { it.replace("oph", "ph") },
        // ফ emits "ph"; virtually everyone types "f" (ফোন "fon", ফল "fol",
        // ডলফিন "dolfin"). The fon/fol keys were EMPTY before this rule —
        // the whole ফ vocabulary depended on seed coverage for f-spellings.
        HabitRule("ph_to_f") { it.replace("ph", "f") },
        // ঝ emits "jh"; users drop the h (বুঝি → "buji", ঝাল → "jal").
        // Must run BEFORE j_to_z: that rule doubles the alias set with
        // z-variants and would starve the 32-key budget for jh chains
        // (bujhotechhi -> ... -> bujtechi/bujteci/bujtesi).
        HabitRule("h_lazy_jh") { it.replace("jh", "j") },
        // য is emitted "z"; users overwhelmingly type "j" (যদি → "jodi") —
        // and the reverse: জ-words are reachable via "z" (জীবন → "zibon").
        HabitRule("z_to_j") { it.replace("z", "j") },
        HabitRule("j_to_z") { it.replace("j", "z") },
        // S56 chat register: শ্ব is PRONOUNCED as a geminated শ-শ ("bishsho"),
        // so typists double the s — বিশ্ব "bissho"→"bisso", বিশ্বকাপ
        // "bissokap", বিশ্বাস "bissash". Canonical emits "shw" (bishwo);
        // rewrite to "ssh" here so the h_lazy_sh pass right below composes the
        // lazy double-s form ("bisshokap" → "bissokap"). MUST run before
        // h_lazy_sh (a later rule never re-triggers an earlier one — S27).
        HabitRule("shw_gemination") { it.replace("shw", "ssh") },
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
        // S86 (tester: thokiyecho had ZERO store rows — composing showed raw
        // থকিয়েচ): final_o just created new chh-final aliases the collapse
        // family (which already ran) never sees — thokiyechh -> thokiyechho
        // existed but thokiyecho/thokiyeco/thokiyeso didn't, for EVERY
        // ছ-final word whose -ো twin isn't independently in the corpus.
        // Re-run the collapses on the final_o output; dedup absorbs overlap.
        // Placed before vowel_glide_y_drop so the chat spellings compose too
        // (thokiyecho -> thokiecho, thokiyeco -> thokieco).
        HabitRule("chh_collapse_after_final_o") { it.replace("chh", "c") },
        HabitRule("h_lazy_chh_after_final_o") { it.replace("chh", "ch") },
        HabitRule("s_for_chh_after_final_o") { it.replace("chh", "s") },
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
     *
     * S78: [nasalSeed] (the nasal-dropped romanization of a chandrabindu
     * word) joins the set before the rules run, so every habit chain also
     * composes on it (কাঁদছে -> "kadchhe" -> kadche/kadce/kadse). It shares
     * the same key budget; the seed itself is always in.
     */
    internal fun aliasesFor(
        canonical: String,
        nasalSeed: String? = null,
        bengali: String? = null,
    ): List<String> {
        val aliases = LinkedHashSet<String>()
        aliases.add(canonical)
        if (nasalSeed != null && nasalSeed != canonical) aliases.add(nasalSeed)
        // S103: bo-fola pronunciation seeds join BEFORE the rule table so
        // final_o and the collapse chains compose on them (ostitt -> ostitto).
        if (bengali != null && "্ব" in bengali) {
            for (seed in bofolaPronunciationSeeds(bengali, aliases.toList())) aliases.add(seed)
        }
        for (rule in HABIT_RULES) expand(aliases, rule.transform)
        return aliases.toList()
    }

    /**
     * S103: the pronunciation keys are how users actually type bo-fola words
     * (nobody spells "ostitb") — they take CANONICAL priority like curated
     * manual aliases, so the whole-word ownership guards (S25 particle
     * split, S79 negation) defer to them. Seeds ending in a doubled
     * consonant also claim their inherent-o twin ("ostitt" -> "ostitto").
     * Canonical-priority collisions with true owners of the same key fall
     * to the tier/frequency law as usual (jor -> জোর stays primary, জ্বর
     * rides the strip).
     */
    internal fun bofolaCanonicalKeys(bengali: String, base: List<String>): Set<String> {
        if ("্ব" !in bengali) return emptySet()
        val seeds = bofolaPronunciationSeeds(bengali, base)
        val out = LinkedHashSet(seeds)
        for (seed in seeds) {
            if (seed.length >= 2 && seed.last() == seed[seed.length - 2]) out.add(seed + "o")
        }
        // The typed spelling often composes one more chain step — the য়
        // glide dropped between vowels (দায়িত্ব "dayitto" -> "daitto").
        // Those spellings are equally canonical for the class.
        for (key in out.toList()) {
            val glideDropped = key.replace(VOWEL_Y_VOWEL, "$1$2")
            if (glideDropped != key) out.add(glideDropped)
        }
        return out
    }

    /**
     * S103 (tester: ostitto -> অস্তিত তো, the অস্তিত্ব key gap): ব-ফলা (্ব)
     * is SILENT in speech — word-initially the consonant stands alone
     * (দ্বিতীয় "ditio", জ্বর "jor", ত্বক "tok", ধ্বনি "dhoni"), medially it
     * doubles the preceding consonant (অস্তিত্ব "ostitto", বিদ্বান "biddan",
     * অন্বেষণ "onneshon") — but the romanizer spells it letter-by-letter
     * ("ostitb", "jbor"), so no user-typed key ever reached these words.
     * The স্ব/শ্ব flavors romanize as "w" and were already covered by the
     * w-drop/shw chains; this pass covers the "b" flavors.
     *
     * Every transform is GATED on the Bengali actually containing the
     * cluster at the right position — a bare string rule would corrupt
     * compound words whose "tb"/"db" is a real pronounced ব (হাটবাজার
     * class). ম্ব / র্ব / ব্ব keep their real b-sound and are never touched.
     */
    private fun bofolaPronunciationSeeds(bengali: String, base: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        val initial = bengali.length >= 3 && bengali[1] == '্' && bengali[2] == 'ব'
        for (alias in base) {
            var k = alias
            // Medial doubling — the geminated conjuncts (ত্ত্ব "ttb",
            // জ্জ্ব "jjb") already carry the doubling in the romanization,
            // so only the b drops; the plain conjuncts double.
            if ("ত্ত্ব" in bengali) k = k.replace("ttb", "tt")
            if (bengali.indexOf("ত্ব") > 0) k = k.replace(MEDIAL_TB, "tt")
            if ("জ্জ্ব" in bengali) k = k.replace("jjb", "jj")
            if (bengali.indexOf("দ্ব") > 0) k = k.replace(MEDIAL_DB, "dd")
            if (bengali.indexOf("ন্ব") > 0) k = k.replace("nb", "nn")
            if ("র্ধ্ব" in bengali) k = k.replace("rdhb", "rdh")
            // Word-initial silent bo-fola: drop the b.
            if (initial) {
                for (prefix in INITIAL_BOFOLA_PREFIXES) {
                    if (k.startsWith(prefix)) {
                        k = prefix.dropLast(1) + k.substring(prefix.length)
                        break
                    }
                }
            }
            if (k != alias) {
                out.add(k)
                // ৃ romanizes "rri" (নেতৃত্ব "netrritt"); typed spelling is
                // "ri" ("netritto"). double_reduce can't produce this — it
                // collapses ALL doubles at once ("netrritt" -> "netrit"),
                // never the rr alone. Seed the collapsed twin explicitly.
                if ("rri" in k) out.add(k.replace("rri", "ri"))
            }
        }
        return out.toList()
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
