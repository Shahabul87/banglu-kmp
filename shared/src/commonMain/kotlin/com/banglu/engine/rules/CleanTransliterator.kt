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

    // Greedy longest-match consonant units (3-char before 2-char before 1-char).
    private val CONSONANTS: Map<String, String> = mapOf(
        "chh" to "ছ", "kkh" to "ক্ষ",
        "kh" to "খ", "gh" to "ঘ", "ch" to "চ", "jh" to "ঝ",
        "th" to "থ", "dh" to "ধ", "ph" to "ফ", "bh" to "ভ",
        "sh" to "শ", "ng" to "ং", "gg" to "জ্ঞ",
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

                CONSONANTS[unit]?.let { bengali ->
                    if (prevWasConsonant && bengali != "ং") out.append(HASANTA)
                    out.append(bengali)
                    prevWasConsonant = bengali != "ং"
                    pos += len
                    matched = true
                }
                if (matched) break

                VOWELS[unit]?.let { vowel ->
                    out.append(if (prevWasConsonant) vowel.kar else vowel.independent)
                    prevWasConsonant = false
                    pos += len
                    matched = true
                }
                if (matched) break
            }
            if (!matched) pos++ // silently drop unmappable chars (digits handled upstream)
        }
        return out.toString()
    }
}
