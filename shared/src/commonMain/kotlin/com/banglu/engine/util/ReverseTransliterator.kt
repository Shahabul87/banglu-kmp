package com.banglu.engine.util

/**
 * ReverseTransliterator - Bengali to English phonetic reverse transliteration.
 *
 * Converts Bengali text back to the English phonetic spelling that a user
 * would type in the SmartEngine to produce that word.
 *
 * Strategy:
 * 1. Parse Bengali text character by character
 * 2. Handle known conjuncts (greedy longest match)
 * 3. Handle ra-phala (C + hasanta + র) and ya-phala (C + hasanta + য)
 * 4. Handle dependent vowels (kars)
 * 5. Handle independent vowels (word-initial)
 * 6. Handle special marks (chandrabindu, anusvara, visarga)
 * 7. Emit inherent vowel 'o' between bare consonants
 *
 * Ported from TypeScript: src/engine/smart/ReverseTransliterator.ts
 */
object ReverseTransliterator {

    private const val HASANTA = '\u09CD' // ্
    private const val VISARGA = 'ঃ' // ঃ
    private const val NUKTA = '়'   // ়

    /**
     * Fold decomposed nukta sequences (base + U+09BC) into the precomposed
     * code points: ড+়→ড়, ঢ+়→ঢ়, য+়→য়. NFC does NOT perform this fold
     * (ড়/ঢ়/য় are Unicode composition exclusions), but the 484K corpus
     * contains ~13.5K words encoded in the decomposed form, so every public
     * entry point must normalize before parsing.
     */
    private fun foldNukta(text: String): String {
        if (!text.contains(NUKTA)) return text
        return text
            .replace("ড়", "ড়") // ড + ় -> ড়
            .replace("ঢ়", "ঢ়") // ঢ + ় -> ঢ়
            .replace("য়", "য়") // য + ় -> য়
    }

    // ========================================================================
    // Reverse conjunct map: Bengali conjunct -> phonetic string.
    // Sorted longest-Bengali-first for greedy matching.
    // ========================================================================

    private data class ConjunctEntry(val bengali: String, val phonetic: String)

    private val REVERSE_CONJUNCTS: List<ConjunctEntry> = listOf(
        // === 3-consonant conjuncts (must match before 2-consonant) ===
        ConjunctEntry("\u09A8\u09CD\u09A6\u09CD\u09B0", "ndr"),   // ন্দ্র
        ConjunctEntry("\u09A8\u09CD\u09A4\u09CD\u09B0", "ntr"),   // ন্ত্র
        ConjunctEntry("\u099C\u09CD\u099C\u09CD\u09AC", "jjb"),   // জ্জ্ব
        ConjunctEntry("\u09B7\u09CD\u099F\u09CD\u09B0", "shtr"),  // ষ্ট্র (যুক্তরাষ্ট্র)
        ConjunctEntry("\u09B8\u09CD\u09A4\u09CD\u09B0", "str"),   // স্ত্র (স্ত্রী)
        ConjunctEntry("\u09AE\u09CD\u09AA\u09CD\u09B0", "mpr"),   // ম্প্র (সম্প্রতি)
        ConjunctEntry("\u0995\u09CD\u09B7\u09CD\u09AF", "kkhy"),  // ক্ষ্য (লক্ষ্য)
        ConjunctEntry("\u09B8\u09CD\u09A5\u09CD\u09AF", "sthy"),  // স্থ্য (স্বাস্থ্য)
        ConjunctEntry("\u09A8\u09CD\u09A7\u09CD\u09AF", "ndhy"),  // ন্ধ্য (সন্ধ্যা)
        ConjunctEntry("\u09A6\u09CD\u09B0\u09CD\u09AF", "dry"),   // দ্র্য (দারিদ্র্য)
        ConjunctEntry("\u09AC\u09CD\u09B0\u09CD\u09AF", "bry"),   // ব্র্য (ব্র্যাক)

        // === Sibilant-based conjuncts ===
        ConjunctEntry("\u09B7\u09CD\u09A0", "shth"),  // ষ্ঠ
        ConjunctEntry("\u09B7\u09CD\u09AB", "shph"),  // ষ্ফ
        ConjunctEntry("\u09B7\u09CD\u099F", "sht"),   // ষ্ট
        ConjunctEntry("\u09B7\u09CD\u09A3", "shn"),   // ষ্ণ
        ConjunctEntry("\u09B7\u09CD\u0995", "shk"),   // ষ্ক
        ConjunctEntry("\u09B7\u09CD\u09AA", "shp"),   // ষ্প
        ConjunctEntry("\u09B7\u09CD\u09AE", "shm"),   // ষ্ম
        ConjunctEntry("\u09B8\u09CD\u09A5", "sth"),   // স্থ
        ConjunctEntry("\u09B8\u09CD\u09AB", "sph"),   // স্ফ
        ConjunctEntry("\u09B8\u09CD\u09A4", "st"),    // স্ত
        ConjunctEntry("\u09B8\u09CD\u09A8", "sn"),    // স্ন
        ConjunctEntry("\u09B8\u09CD\u0995", "sk"),    // স্ক
        ConjunctEntry("\u09B8\u09CD\u09AA", "sp"),    // স্প
        ConjunctEntry("\u09B8\u09CD\u09AE", "sm"),    // স্ম
        ConjunctEntry("\u09B8\u09CD\u09B2", "sl"),    // স্ল
        ConjunctEntry("\u09B8\u09CD\u09B0", "sr"),    // স্র
        ConjunctEntry("\u09B8\u09CD\u09AC", "sw"),    // স্ব
        ConjunctEntry("\u09B6\u09CD\u099B", "shchh"), // শ্ছ
        ConjunctEntry("\u09B6\u09CD\u099A", "shch"),  // শ্চ
        ConjunctEntry("\u09B6\u09CD\u09B0", "shr"),   // শ্র
        ConjunctEntry("\u09B6\u09CD\u09B2", "shl"),   // শ্ল
        ConjunctEntry("\u09B6\u09CD\u09AC", "shw"),   // শ্ব
        ConjunctEntry("\u0995\u09CD\u09B7", "kkh"),   // ক্ষ

        // === Multi-consonant conjuncts ===
        ConjunctEntry("\u099A\u09CD\u099B", "chch"),  // চ্ছ
        ConjunctEntry("\u099D\u09CD\u099D", "jhjh"),  // ঝ্ঝ

        // Ya-phala after aspirated consonants
        ConjunctEntry("\u0996\u09CD\u09AF", "khy"),   // খ্য
        ConjunctEntry("\u0998\u09CD\u09AF", "ghy"),   // ঘ্য
        ConjunctEntry("\u09A5\u09CD\u09AF", "thy"),   // থ্য
        ConjunctEntry("\u09A7\u09CD\u09AF", "dhy"),   // ধ্য
        ConjunctEntry("\u09AB\u09CD\u09AF", "phy"),   // ফ্য
        ConjunctEntry("\u09AD\u09CD\u09AF", "bhy"),   // ভ্য

        // R-phala after aspirated consonants
        ConjunctEntry("\u09A7\u09CD\u09B0", "dhr"),   // ধ্র
        ConjunctEntry("\u09AD\u09CD\u09B0", "bhr"),   // ভ্র
        ConjunctEntry("\u0998\u09CD\u09B0", "ghr"),   // ঘ্র
        ConjunctEntry("\u0996\u09CD\u09B0", "khr"),   // খ্র

        // Multi-consonant conjuncts
        ConjunctEntry("\u0999\u09CD\u0997", "ngo"),   // ঙ্গ
        ConjunctEntry("\u09AC\u09CD\u09A7", "bdh"),   // ব্ধ
        ConjunctEntry("\u0997\u09CD\u09A7", "gdh"),   // গ্ধ
        ConjunctEntry("\u09A8\u09CD\u09A7", "ndh"),   // ন্ধ
        ConjunctEntry("\u09A6\u09CD\u09A7", "ddh"),   // দ্ধ
        ConjunctEntry("\u09A6\u09CD\u09AD", "dbh"),   // দ্ভ
        ConjunctEntry("\u09A6\u09CD\u0998", "dgh"),   // দ্ঘ
        ConjunctEntry("\u09AE\u09CD\u09AD", "mbh"),   // ম্ভ
        ConjunctEntry("\u09B2\u09CD\u09AD", "lbh"),   // ল্ভ
        ConjunctEntry("\u09A8\u09CD\u09A5", "nth"),   // ন্থ
        ConjunctEntry("\u09A4\u09CD\u09A5", "tth"),   // ত্থ
        ConjunctEntry("\u09B8\u09CD\u09B8", "ss"),    // স্স

        // 2-char aspirated consonants (these are in the conjuncts table
        // because they are multi-codepoint: base + hasanta sequences or
        // dedicated characters matched as single entries in the table)
        ConjunctEntry("\u0996", "kh"),    // খ
        ConjunctEntry("\u0998", "gh"),    // ঘ
        ConjunctEntry("\u099D", "jh"),    // ঝ
        ConjunctEntry("\u09A5", "th"),    // থ
        ConjunctEntry("\u09A7", "dh"),    // ধ
        ConjunctEntry("\u09AB", "ph"),    // ফ
        ConjunctEntry("\u09AD", "bh"),    // ভ

        // 2-char conjunct pairs
        ConjunctEntry("\u099C\u09CD\u099E", "gy"),    // জ্ঞ
        ConjunctEntry("\u09AA\u09CD\u09A4", "pt"),    // প্ত
        ConjunctEntry("\u0995\u09CD\u09A4", "kt"),    // ক্ত
        ConjunctEntry("\u0997\u09CD\u09A8", "gn"),    // গ্ন
        ConjunctEntry("\u09AE\u09CD\u09A8", "mn"),    // ম্ন
        ConjunctEntry("\u09A8\u09CD\u09A4", "nt"),    // ন্ত
        ConjunctEntry("\u09A8\u09CD\u09A6", "nd"),    // ন্দ
        ConjunctEntry("\u099E\u09CD\u099C", "nj"),    // ঞ্জ
        ConjunctEntry("\u099E\u09CD\u099A", "nc"),    // ঞ্চ
        ConjunctEntry("\u09AE\u09CD\u09AC", "mb"),    // ম্ব
        ConjunctEntry("\u09AE\u09CD\u09AA", "mp"),    // ম্প
        ConjunctEntry("\u09B2\u09CD\u09AA", "lp"),    // ল্প
        ConjunctEntry("\u09B2\u09CD\u09AC", "lb"),    // ল্ব
        ConjunctEntry("\u09B2\u09CD\u09A1", "ld"),    // ল্ড
        ConjunctEntry("\u09B2\u09CD\u099F", "lt"),    // ল্ট
        ConjunctEntry("\u09B2\u09CD\u0995", "lk"),    // ল্ক
        ConjunctEntry("\u09B2\u09CD\u09AE", "lm"),    // ল্ম
        ConjunctEntry("\u09B2\u09CD\u0997", "lg"),    // ল্গ
        ConjunctEntry("\u09B2\u09CD\u09AB", "lf"),    // ল্ফ
        ConjunctEntry("\u09A4\u09CD\u09A8", "tn"),    // ত্ন
        ConjunctEntry("\u09A4\u09CD\u09AE", "tm"),    // ত্ম
        ConjunctEntry("\u09A4\u09CD\u09AC", "tb"),    // ত্ব
        ConjunctEntry("\u09A6\u09CD\u09AC", "db"),    // দ্ব
        ConjunctEntry("\u09A6\u09CD\u0997", "dg"),    // দ্গ
        ConjunctEntry("\u09A6\u09CD\u09AE", "dm"),    // দ্ম
        ConjunctEntry("\u099C\u09CD\u09AC", "jb"),    // জ্ব
        ConjunctEntry("\u09B9\u09CD\u09AE", "hm"),    // হ্ম
        ConjunctEntry("\u09B9\u09CD\u09A8", "hn"),    // হ্ন
        ConjunctEntry("\u09B9\u09CD\u09B2", "hl"),    // হ্ল
        ConjunctEntry("\u09B9\u09CD\u09AC", "hb"),    // হ্ব
        ConjunctEntry("\u09A8\u09CD\u09AE", "nm"),    // ন্ম
        ConjunctEntry("\u09A8\u09CD\u09B8", "ns"),    // ন্স
        ConjunctEntry("\u0997\u09CD\u09AE", "gm"),    // গ্ম
        ConjunctEntry("\u0997\u09CD\u09B2", "gl"),    // গ্ল
        ConjunctEntry("\u0995\u09CD\u09B2", "kl"),    // ক্ল
        ConjunctEntry("\u09AA\u09CD\u09B2", "pl"),    // প্ল
        ConjunctEntry("\u09AA\u09CD\u09A8", "pn"),    // প্ন
        ConjunctEntry("\u09AA\u09CD\u09B8", "ps"),    // প্স
        ConjunctEntry("\u09AC\u09CD\u09B2", "bl"),    // ব্ল
        ConjunctEntry("\u09AC\u09CD\u09A6", "bd"),    // ব্দ
        ConjunctEntry("\u09AE\u09CD\u09B2", "ml"),    // ম্ল
        ConjunctEntry("\u09AB\u09CD\u09B2", "fl"),    // ফ্ল
        ConjunctEntry("\u09AB\u09CD\u09B0", "fr"),    // ফ্র (আফ্রিকা — users type f, not ph)

        // Double consonants
        ConjunctEntry("\u0995\u09CD\u0995", "kk"),    // ক্ক
        ConjunctEntry("\u099A\u09CD\u099A", "cc"),    // চ্চ
        ConjunctEntry("\u099C\u09CD\u099C", "jj"),    // জ্জ
        ConjunctEntry("\u09A6\u09CD\u09A6", "dd"),    // দ্দ
        ConjunctEntry("\u09A4\u09CD\u09A4", "tt"),    // ত্ত
        ConjunctEntry("\u09A8\u09CD\u09A8", "nn"),    // ন্ন
        ConjunctEntry("\u09AE\u09CD\u09AE", "mm"),    // ম্ম
        ConjunctEntry("\u09B2\u09CD\u09B2", "ll"),    // ল্ল
        ConjunctEntry("\u09AA\u09CD\u09AA", "pp"),    // প্প
        ConjunctEntry("\u09AC\u09CD\u09AC", "bb"),    // ব্ব
    )

    // Sort by Bengali string length descending for greedy matching
    private val CONJUNCTS_BY_LENGTH: List<ConjunctEntry> =
        REVERSE_CONJUNCTS.sortedByDescending { it.bengali.length }

    // ========================================================================
    // Single Bengali consonant -> canonical phonetic
    // ========================================================================

    private val REVERSE_CONSONANTS: Map<String, String> = mapOf(
        "\u0995" to "k",    // ক
        "\u0997" to "g",    // গ
        "\u099A" to "ch",   // চ
        "\u099B" to "chh",  // ছ
        "\u099C" to "j",    // জ
        "\u099F" to "t",    // ট
        "\u09A1" to "d",    // ড
        "\u09A4" to "t",    // ত
        "\u09A6" to "d",    // দ
        "\u09A8" to "n",    // ন
        "\u09A3" to "n",    // ণ
        "\u09AA" to "p",    // প
        "\u09AC" to "b",    // ব
        "\u09AE" to "m",    // ম
        "\u09AF" to "z",    // য
        "\u09AF\u09BC" to "y",  // য়
        "\u09B0" to "r",    // র
        "\u09B2" to "l",    // ল
        "\u09B9" to "h",    // হ
        "\u09B6" to "sh",   // শ
        "\u09B7" to "sh",   // ষ
        "\u09B8" to "s",    // স
        "\u09A0" to "th",   // ঠ
        "\u09A2" to "dh",   // ঢ
        "\u09DC" to "r",    // ড়
        "\u09DD" to "rh",   // ঢ়
        "\u09DF" to "y",    // য়
        "\u099E" to "n",    // ঞ
        "\u0999" to "ng",   // ঙ
        "\u09CE" to "t",    // ৎ
    )

    // ========================================================================
    // Independent vowels -> phonetic (word-initial position)
    // ========================================================================

    private val REVERSE_INDEPENDENT_VOWELS: Map<String, String> = mapOf(
        "\u0985" to "o",    // অ
        "\u0986" to "a",    // আ
        "\u0987" to "i",    // ই
        "\u0988" to "ii",   // ঈ
        "\u0989" to "u",    // উ
        "\u098A" to "uu",   // ঊ
        "\u098B" to "rri",  // ঋ
        "\u098F" to "e",    // এ
        "\u0990" to "oi",   // ঐ
        "\u0993" to "o",    // ও
        "\u0994" to "ou",   // ঔ
    )

    // ========================================================================
    // Dependent vowels (kars) -> phonetic
    // ========================================================================

    private val REVERSE_DEPENDENT_VOWELS: Map<String, String> = mapOf(
        "\u09BE" to "a",    // া
        "\u09BF" to "i",    // ি
        "\u09C0" to "ii",   // ী
        "\u09C1" to "u",    // ু
        "\u09C2" to "uu",   // ূ
        "\u09C3" to "rri",  // ৃ
        "\u09C7" to "e",    // ে
        "\u09C8" to "oi",   // ৈ
        "\u09CB" to "o",    // ো
        "\u09CC" to "ou",   // ৌ
    )

    // ========================================================================
    // Special marks
    // ========================================================================

    private val REVERSE_MARKS: Map<String, String> = mapOf(
        "\u0981" to "N",    // ঁ (chandrabindu)
        "\u0982" to "ng",   // ং (anusvara)
        "\u0983" to "",     // ঃ (visarga) — silent in keys; gemination handled separately
    )

    // ========================================================================
    // Character classification helpers
    // ========================================================================

    private fun isBengaliConsonant(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x0995..0x09A8) ||    // ক-ন
                (code in 0x09AA..0x09B9) ||    // প-হ
                code == 0x09DC ||               // ড়
                code == 0x09DD ||               // ঢ়
                code == 0x09DF ||               // য়
                code == 0x09CE                  // ৎ
    }

    private fun isBengaliConsonantStr(str: String): Boolean {
        if (str.isEmpty()) return false
        if (str.length == 1) return isBengaliConsonant(str[0])
        // Two-codepoint: check if it's a nukta combination
        if (str.length == 2 && str[1].code == 0x09BC) return true
        return false
    }

    private fun isIndependentVowel(ch: Char): Boolean {
        val code = ch.code
        return code in 0x0985..0x0994  // অ-ঔ
    }

    private fun isDependentVowel(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x09BE..0x09C4) ||  // া ি ী ু ূ ৃ
                (code in 0x09C7..0x09C8) ||  // ে ৈ
                (code in 0x09CB..0x09CC)      // ো ৌ
    }

    private fun isBengaliDigit(ch: Char): Boolean {
        val code = ch.code
        return code in 0x09E6..0x09EF  // ০-৯
    }

    /**
     * Get the "effective" character at a position, handling two-codepoint
     * characters like য় (য + ়), ড় (ড + ়), ঢ় (ঢ + ়).
     */
    private fun getEffectiveChar(text: String, pos: Int): Pair<String, Int> {
        if (pos >= text.length) return "" to 0
        // Check for nukta-based characters (2 codepoints)
        if (pos + 1 < text.length && text[pos + 1].code == 0x09BC) {
            return text.substring(pos, pos + 2) to 2
        }
        return text[pos].toString() to 1
    }

    // ========================================================================
    // Post-consonant handling (inherent vowel logic)
    // ========================================================================

    /**
     * After emitting a consonant cluster's phonetic, determine what follows:
     * - Dependent vowel (kar) -> emit the vowel phonetic and advance
     * - Chandrabindu after a vowel-kar -> attach N
     * - End of word -> emit nothing
     * - Another consonant, independent vowel, or mark -> emit inherent 'o'
     */
    private fun consumePostConsonant(bengali: String, pos: Int): Pair<String, Int> {
        if (pos >= bengali.length) return "" to pos

        // Check for dependent vowel (kar) immediately after
        val depVowel = REVERSE_DEPENDENT_VOWELS[bengali[pos].toString()]
        if (depVowel != null) {
            var result = depVowel
            var newPos = pos + 1

            // Check for chandrabindu after vowel-kar
            if (newPos < bengali.length && bengali[newPos] == '\u0981') {
                result += "N"
                newPos++
            }

            return result to newPos
        }

        // No vowel-kar follows. Check if we need inherent vowel 'o'.
        // Hasanta means a conjunct follows - no inherent 'o'
        if (bengali[pos] == HASANTA) return "" to pos

        val ch = bengali[pos]
        val nextIsConsonant = isBengaliConsonant(ch) || getEffectiveChar(bengali, pos).first.let { isBengaliConsonantStr(it) }
        val nextIsIndependentVowel = isIndependentVowel(ch)
        val nextIsMark = ch == '\u0981' || ch == '\u0982' || ch == '\u0983'
        val nextIsKhondoTa = ch.code == 0x09CE

        if (nextIsConsonant || nextIsIndependentVowel || nextIsMark || nextIsKhondoTa) {
            return "o" to pos
        }

        return "" to pos
    }

    // ========================================================================
    // Greedy conjunct matcher
    // ========================================================================

    /**
     * Try to match the longest known Bengali conjunct at the current position.
     */
    private fun matchConjunctGreedy(text: String, pos: Int): Pair<String, Int>? {
        for (entry in CONJUNCTS_BY_LENGTH) {
            val bengaliLen = entry.bengali.length
            if (pos + bengaliLen <= text.length) {
                val slice = text.substring(pos, pos + bengaliLen)
                if (slice == entry.bengali) {
                    return entry.phonetic to bengaliLen
                }
            }
        }
        return null
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Reverse-transliterate a single Bengali word to phonetic English.
     * This is the core pattern-based algorithm. Input is nukta-folded first
     * so decomposed ড়/ঢ়/য় parse identically to the precomposed forms.
     */
    fun reverseWord(bengali: String): String {
        if (bengali.isEmpty()) return ""
        return reverseWordInternal(foldNukta(bengali))
    }

    /**
     * Visarga gemination: in Bengali pronunciation a visarga (ঃ) before a
     * consonant geminates it — দুঃখ is pronounced (and typed) "dukkho",
     * never "dukho". Append the first letter of the following consonant's
     * phonetic (ঃখ -> "k" + "kh"). The visarga itself is silent (its
     * REVERSE_MARKS emission is ""). Emitting the geminated form as THE
     * canonical key also prevents visarga words from shadowing exact
     * words (দুঃখ must not be indexed under "dukh", which belongs to দুখ).
     */
    private fun appendVisargaGemination(bengali: String, visargaPos: Int, output: StringBuilder) {
        val next = visargaPos + 1
        if (next >= bengali.length) return
        val phonetic = matchConjunctGreedy(bengali, next)?.first
            ?: REVERSE_CONSONANTS[getEffectiveChar(bengali, next).first]
        if (!phonetic.isNullOrEmpty()) output.append(phonetic[0])
    }

    private fun reverseWordInternal(bengali: String): String {
        if (bengali.isEmpty()) return ""

        val output = StringBuilder()
        var i = 0

        while (i < bengali.length) {
            val ch = bengali[i]

            // --- Bengali digits ---
            if (isBengaliDigit(ch)) {
                val digitVal = ch.code - 0x09E6
                output.append(digitVal)
                i++
                continue
            }

            // --- Punctuation ---
            if (ch == '\u0964') { // ।
                if (i + 1 < bengali.length && bengali[i + 1] == '\u0964') {
                    output.append("..")
                    i += 2
                } else {
                    output.append(".")
                    i++
                }
                continue
            }
            if (ch == '\u09F3') { // ৳
                output.append("$")
                i++
                continue
            }

            // --- Stray hasanta ---
            // Reached when the head of a 3+ consonant cluster was already
            // consumed (e.g. ষ্ট matched in ষ্ট্র) and the trailing
            // ্ + consonant remains. Never emit the hasanta literally.
            if (ch == HASANTA) {
                val (nextEff, nextEffLen) = getEffectiveChar(bengali, i + 1)
                if (nextEff == "\u09AF") {
                    // Ya-phala continuing the cluster: ...্য -> y
                    output.append("y")
                    i += 1 + nextEffLen
                    val (postPhonetic, newPos) = consumePostConsonant(bengali, i)
                    output.append(postPhonetic)
                    i = newPos
                } else {
                    // Skip; a following consonant (e.g. র in ষ্ট্র) is
                    // handled by the next iteration. Word-final hasanta is
                    // silently dropped.
                    i++
                }
                continue
            }

            // --- Special marks (standalone, not after consonant) ---
            val markPhonetic = REVERSE_MARKS[ch.toString()]
            if (markPhonetic != null) {
                if (ch == VISARGA) {
                    appendVisargaGemination(bengali, i, output)
                }
                output.append(markPhonetic)
                i++
                continue
            }

            // --- Try conjunct match (greedy, longest Bengali first) ---
            val conjMatch = matchConjunctGreedy(bengali, i)
            if (conjMatch != null) {
                output.append(conjMatch.first)
                i += conjMatch.second

                // Handle post-consonant
                val (postPhonetic, newPos) = consumePostConsonant(bengali, i)
                output.append(postPhonetic)
                i = newPos
                continue
            }

            // --- Independent vowels ---
            if (isIndependentVowel(ch)) {
                val vowelPhonetic = REVERSE_INDEPENDENT_VOWELS[ch.toString()]
                if (vowelPhonetic != null) {
                    output.append(vowelPhonetic)
                }
                i++
                continue
            }

            // --- Get effective character (handle nukta combinations) ---
            val (effChar, effLen) = getEffectiveChar(bengali, i)

            // --- Consonant processing ---
            if (isBengaliConsonantStr(effChar)) {
                val consonantPhonetic = REVERSE_CONSONANTS[effChar]
                if (consonantPhonetic == null) {
                    output.append(effChar)
                    i += effLen
                    continue
                }

                // Look ahead: is the next character a hasanta?
                val nextPos = i + effLen
                if (nextPos < bengali.length && bengali[nextPos] == HASANTA) {
                    val afterHasanta = nextPos + 1
                    if (afterHasanta < bengali.length) {
                        val (nextEff, nextEffLen) = getEffectiveChar(bengali, afterHasanta)

                        // Ra-phala: C + hasanta + র -> Cr
                        if (nextEff == "\u09B0") {
                            output.append(consonantPhonetic).append("r")
                            i = afterHasanta + nextEffLen
                            val (postPhonetic, newPos) = consumePostConsonant(bengali, i)
                            output.append(postPhonetic)
                            i = newPos
                            continue
                        }

                        // Ya-phala: C + hasanta + য -> Cy
                        if (nextEff == "\u09AF") {
                            output.append(consonantPhonetic).append("y")
                            i = afterHasanta + nextEffLen
                            val (postPhonetic, newPos) = consumePostConsonant(bengali, i)
                            output.append(postPhonetic)
                            i = newPos
                            continue
                        }

                        // Generic C + hasanta + C (unknown conjunct)
                        if (isBengaliConsonantStr(nextEff)) {
                            output.append(consonantPhonetic)
                            i = afterHasanta // skip past hasanta, next iteration handles second consonant
                            continue
                        }
                    }

                    // Hasanta at end of word (word-final halant)
                    output.append(consonantPhonetic)
                    i = nextPos + 1
                    continue
                }

                // No hasanta follows - bare consonant
                output.append(consonantPhonetic)
                i += effLen

                // Handle post-consonant
                val (postPhonetic, newPos) = consumePostConsonant(bengali, i)
                output.append(postPhonetic)
                i = newPos
                continue
            }

            // --- Dependent vowel without preceding consonant (edge case) ---
            if (isDependentVowel(ch)) {
                val depVowel = REVERSE_DEPENDENT_VOWELS[ch.toString()]
                if (depVowel != null) {
                    output.append(depVowel)
                }
                i++
                continue
            }

            // --- Unknown character: pass through ---
            output.append(ch)
            i++
        }

        return output.toString()
    }

    /**
     * Reverse-transliterate Bengali text (may contain multiple words).
     * Splits by whitespace, preserving whitespace tokens.
     */
    fun reverseTransliterate(bengali: String): String {
        if (bengali.isEmpty() || bengali.isBlank()) {
            return bengali // preserve whitespace-only strings
        }

        // Split preserving whitespace tokens
        val tokens = Regex("(\\s+)").split(bengali)
        val separators = Regex("(\\s+)").findAll(bengali).map { it.value }.toList()

        val result = StringBuilder()
        for ((index, token) in tokens.withIndex()) {
            if (token.isBlank()) {
                result.append(token)
            } else {
                result.append(reverseWord(token))
            }
            if (index < separators.size) {
                result.append(separators[index])
            }
        }
        return result.toString()
    }
}
