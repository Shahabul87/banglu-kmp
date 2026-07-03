package com.banglu.engine.rules

/**
 * Deterministic, swap-free roman -> Bengali transliteration.
 *
 * This is the OOV floor (Engine v3 spec section 3.1): one input, one output,
 * always readable Bengali, never optimal. No ShatvaVidhan/NatvaVidhan, no
 * character swaps, no candidate lattice. The same mapping (in reverse) defines
 * canonical phonetic keys at dictionary-compile time.
 */
object CleanTransliterator {

    private const val HASANTA = "্"
    private const val ANUSVARA = "ং"

    // Greedy longest-match consonant units (3-char before 2-char before 1-char).
    private val CONSONANTS: Map<String, String> = mapOf(
        "chh" to "ছ", "kkh" to "ক্ষ",
        // চ্ছ/চ্চ typed lazily as cch/cc (ghumacchi, ghumacco, bachcha):
        // without these units the floor renders cc as ছ্ছ, a stack that does
        // not exist in Bengali and reads as engine garbage mid-word.
        "cch" to "চ্ছ", "cc" to "চ্চ",
        "kh" to "খ", "gh" to "ঘ", "ch" to "চ", "jh" to "ঝ",
        "th" to "থ", "dh" to "ধ", "ph" to "ফ", "bh" to "ভ",
        "sh" to "শ", "ng" to ANUSVARA, "gg" to "জ্ঞ",
        "k" to "ক", "g" to "গ", "c" to "ছ", "j" to "জ", "z" to "জ",
        "t" to "ত", "d" to "দ", "n" to "ন", "p" to "প", "f" to "ফ",
        "b" to "ব", "v" to "ভ", "m" to "ম", "r" to "র", "l" to "ল",
        "s" to "স", "h" to "হ", "y" to "য়", "w" to "ও", "q" to "ক",
        "x" to "ক্স"
    )

    // Vowel units: independent form (word-initial / after vowel) and kar form.
    private data class Vowel(val independent: String, val kar: String)
    private val VOWELS: Map<String, Vowel> = mapOf(
        "oi" to Vowel("ঐ", "ৈ"), "ou" to Vowel("ঔ", "ৌ"),
        "ii" to Vowel("ঈ", "ী"), "ee" to Vowel("ঈ", "ী"),
        "uu" to Vowel("ঊ", "ূ"), "oo" to Vowel("উ", "ু"),
        "aa" to Vowel("আ", "া"),
        "a" to Vowel("আ", "া"), "i" to Vowel("ই", "ি"),
        "u" to Vowel("উ", "ু"), "e" to Vowel("এ", "ে"),
        "o" to Vowel("ও", "")   // after consonant: inherent vowel, emits nothing
    )

    private val UNIT_LENGTHS = intArrayOf(3, 2, 1)

    // Letters that count as a vowel for lookahead purposes (single-char vowel starters).
    private val VOWEL_START_CHARS = setOf('a', 'e', 'i', 'o', 'u')

    /**
     * Transliterate a Roman string to Bengali.
     *
     * Contract:
     * - Input is lowercased and trimmed before processing.
     * - Digits and punctuation MUST be handled by the caller before this call;
     *   unmappable characters are silently dropped here.
     * - Deterministic: same input always produces the same output.
     * - Output is always valid, readable Bengali graphemes — never a raw
     *   matra (kar) without a preceding consonant base, never ্+য়.
     */
    fun transliterate(roman: String): String {
        val key = roman.lowercase().trim()
        if (key.isEmpty()) return ""

        val out = StringBuilder()
        var pos = 0
        var prevWasConsonant = false

        while (pos < key.length) {
            var matched = false
            for (len in UNIT_LENGTHS) {
                if (pos + len > key.length) continue
                val unit = key.substring(pos, pos + len)

                // ── Special: ng before vowel or 'g' → velar nasal ঙ ──────────
                if (unit == "ng") {
                    val nextChar = key.getOrNull(pos + 2)
                    if (nextChar != null && (nextChar in VOWEL_START_CHARS || nextChar == 'g')) {
                        // Treat ঙ as a normal consonant (joins conjuncts, takes kars)
                        if (prevWasConsonant) out.append(HASANTA)
                        out.append("ঙ")
                        prevWasConsonant = true
                        pos += 2
                        matched = true
                        break
                    }
                    // Otherwise fall through to normal consonant handling (emits ং)
                }

                // ── Special: w before a vowel letter → ওয় া glide unit ────────
                if (unit == "w" && len == 1) {
                    val nextChar = key.getOrNull(pos + 1)
                    if (nextChar != null && nextChar in VOWEL_START_CHARS) {
                        // "ওয়" acts as a consonant unit: vowel following attaches as kar to য়.
                        // Never prepend hasanta before ওয় (it is itself vowel-consonant).
                        out.append("ওয়")
                        prevWasConsonant = true
                        pos += 1
                        matched = true
                        break
                    }
                    // No following vowel: fall through to normal mapping (ও)
                }

                val c = CONSONANTS[unit]
                if (c != null) {
                    if (unit == "y" && prevWasConsonant) {
                        // ya-phala: hasanta + য (U+09AF), not হসন্ত + য় (U+09DF)
                        out.append(HASANTA).append("য")
                        prevWasConsonant = true
                    } else if (unit == "w" && c == "ও") {
                        // bare w (not followed by vowel) emits the independent vowel ও.
                        // Independent vowels must never be hasanta-joined on either side.
                        // Do NOT prepend hasanta, and reset prevWasConsonant so a following
                        // vowel emits its independent form rather than a kar.
                        out.append(c)
                        prevWasConsonant = false
                    } else {
                        if (prevWasConsonant && c != ANUSVARA) out.append(HASANTA)
                        out.append(c)
                        prevWasConsonant = c != ANUSVARA
                    }
                    pos += len
                    matched = true
                    break
                }

                val v = VOWELS[unit]
                if (v != null) {
                    out.append(if (prevWasConsonant) v.kar else v.independent)
                    prevWasConsonant = false
                    pos += len
                    matched = true
                    break
                }
            }
            if (!matched) pos++ // silently drop unmappable chars (digits handled upstream)
        }
        return out.toString()
    }
}
