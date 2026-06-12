package com.banglu.compiler

import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.util.ReverseTransliterator

data class PhoneticIndexRow(
    val key: String,
    val bengali: String,
    val frequency: Int,
    val tier: Int // 0 = Tier A (suggestible), 1 = Tier B (exact-match only)
)

data class IndexBuildReport(
    val totalWords: Int = 0,
    val roundTripOk: Int = 0,
    val totalRows: Int = 0,
    val droppedKeys: Int = 0,
    val wordsWithNoRows: Int = 0
) {
    val coveragePercent: Double
        get() = if (totalWords == 0) 0.0 else roundTripOk * 100.0 / totalWords
}

object PhoneticIndexBuilder {

    private const val TIER_A = 0
    private const val TIER_B = 1

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

    fun build(words: List<String>, frequencies: Map<String, Int>): List<PhoneticIndexRow> {
        val rows = ArrayList<PhoneticIndexRow>(words.size * 2)
        val seen = HashSet<String>(words.size * 2)
        var roundTripOk = 0
        var total = 0
        var droppedKeys = 0
        var wordsWithNoRows = 0

        for (raw in words) {
            val word = raw.trim()
            if (word.length !in 2..18) continue
            if (!BENGALI_ONLY.matches(word)) continue
            if (word.endsWith("্")) continue
            total++

            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (CleanTransliterator.transliterate(canonical) == word) roundTripOk++

            val freq = frequencies[word] ?: 0
            val tier = if (freq > 0) TIER_A else TIER_B
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
                rows.add(PhoneticIndexRow(key, word, freq, tier))
                rowsForWord++
            }
            if (rowsForWord == 0) wordsWithNoRows++
        }
        lastReport = IndexBuildReport(
            totalWords = total,
            roundTripOk = roundTripOk,
            totalRows = rows.size,
            droppedKeys = droppedKeys,
            wordsWithNoRows = wordsWithNoRows
        )
        return rows
    }

    /** Ya-phala between a consonant and a vowel: users drop the y (সন্ধ্যা "sondhya" → "sondha"). */
    private val YA_PHALA_BEFORE_VOWEL = Regex("([bcdfghjklmnpqrstvwxz])y([aeiou])")

    /** Word-final ya-phala after a consonant: users drop the y (লক্ষ্য "lokkhy" → "lokkh"). */
    private val YA_PHALA_FINAL = Regex("([bcdfghjklmnpqrstvwxz])y$")

    /**
     * Bo-phola (্ব) after a consonant: ReverseTransliterator emits it as "w"
     * (স্বাস্থ্য → "swasthy", বিশ্বাস → "bishwas"), but the ব-ফলা is barely
     * pronounced and real users almost never type it (স্বাস্থ্য="shastho",
     * স্বপ্ন="shopno", বিশ্বাস="bishash"). Only a w PRECEDED BY A CONSONANT
     * letter is dropped — vowel+w glides (হাওয়া-class keys) and word-initial
     * "w" are untouched.
     */
    private val BO_PHOLA_W = Regex("([bcdfghjklmnpqrstvxz])w")

    private const val VOWELS = "aeiou"

    /**
     * Typing-habit aliases for a canonical phonetic key.
     *
     * Rules (each applied to every alias produced by the previous rules, so
     * all combinations are generated — e.g. "chhotrii" → "cotri",
     * "zuktorashtr" → "juktorashtro"):
     * - `chh → c`  : ছ (REVERSE_CONSONANTS emits "chh"; users type "c")
     * - `ii → i`   : ী / ঈ (emitted as "ii"; users omit the doubled vowel)
     * - `uu → u`   : ূ / ঊ (emitted as "uu"; users omit the doubled vowel)
     * - `z → j`    : য (emitted as "z"; users overwhelmingly type "j": যদি → "jodi")
     * - bo-phola drop : consonant+`w` — ্ব is barely pronounced; users type
     *   the spoken form (স্বাস্থ্যকর "swasthyokor" → "sasthyokor", বিশ্বাস
     *   "bishwas" → "bishas"). Vowel+`w` glides and word-initial `w` are kept.
     *   Composed with ya-phala drop this yields the natural typing
     *   ("swasthyokor" → "sasthokor").
     * - ya-phala drop : consonant+`y` word-finally or before a vowel — users
     *   type the pronounced form (লক্ষ্য "lokkhy" → "lokkh", সন্ধ্যা
     *   "sondhya" → "sondha"). Vowel+`y` (real য় sound, e.g. "deya") is kept.
     * - trailing inherent `o` : keys ending in a consonant CLUSTER are
     *   pronounced — and typed — with a final inherent vowel (শক্ত "shokt" →
     *   "shokto", যুক্তরাষ্ট্র → "...shtro", "dukkh" → "dukkho"). Single
     *   final consonants ("kom", "ghor") and final aspirate digraphs
     *   ("mukh") / "ng" are NOT clusters and get no `o`.
     *
     * Deleted rules (dead — transliterator never emits these patterns):
     * - ~~`ee → i`~~ : transliterator never emits "ee" as a vowel unit
     * - ~~`oo → u`~~ : transliterator never emits "oo" as a vowel unit;
     *                  accidental o+o adjacency would produce a wrong variant
     *
     * Note: runtime query-side normalizations (e.g. "ee → i") are handled
     * by the engine, not by index aliases here.
     */
    private fun aliasesFor(canonical: String): List<String> {
        val aliases = LinkedHashSet<String>()
        aliases.add(canonical)
        expand(aliases) { it.replace("chh", "c") }
        expand(aliases) { it.replace("ii", "i") }
        expand(aliases) { it.replace("uu", "u") }
        expand(aliases) { it.replace("z", "j") }
        expand(aliases) { dropBoPhola(it) }
        expand(aliases) { dropYaPhala(it) }
        expand(aliases) { withTrailingInherentO(it) }
        return aliases.toList()
    }

    /** Apply [rule] to every alias currently in [set], adding the results (dedup via set). */
    private inline fun expand(set: LinkedHashSet<String>, rule: (String) -> String) {
        for (key in set.toList()) set.add(rule(key))
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
