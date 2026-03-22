package com.banglu.engine.rules

/**
 * ShatvaVidhan (ষত্ব বিধান) - When স/শ becomes ষ
 *
 * Core Rule: After ঋ/ৃ, র্, ই/ঈ-কার, উ/ঊ-কার, or ক in a tatsama word,
 * dental স may become retroflex ষ.
 *
 * Prefix Rule: Certain prefixes trigger ষ in the following root.
 *
 * Position Rule: ষ almost NEVER starts a word (only ~5 words).
 * Word-initial "sh" is শ with 80% confidence.
 *
 * NOTE: This has MANY exceptions. Dictionary ALWAYS takes priority.
 */
object ShatvaVidhan {

    // Prefixes that can trigger ষ in the root that follows
    private val TRIGGERING_PREFIXES: List<PrefixEntry> = listOf(
        PrefixEntry("ni", "নি"),
        PrefixEntry("bi", "বি"),
        PrefixEntry("pori", "পরি"),
        PrefixEntry("pari", "পরি"),
        PrefixEntry("proti", "প্রতি"),
        PrefixEntry("prati", "প্রতি"),
        PrefixEntry("obhi", "অভি"),
        PrefixEntry("abhi", "অভি"),
        PrefixEntry("su", "সু"),
        PrefixEntry("onu", "অনু"),
        PrefixEntry("anu", "অনু"),
        PrefixEntry("upo", "উপ"),
        PrefixEntry("upa", "উপ"),
        PrefixEntry("apo", "অপ"),
        PrefixEntry("opo", "অপ"),
        PrefixEntry("pri", "প্রি"),
        PrefixEntry("tri", "ত্রি"),
        PrefixEntry("duri", "দূরী")
    )

    // Vowels/consonants that trigger ষ when they appear before "sh/s"
    private val RI_VOWEL_CHARS = listOf('ঋ', 'ৃ')

    // Bengali vowel signs that can precede ষ
    private val SHATVA_VOWEL_SIGNS = listOf('ি', 'ী', 'ু', 'ূ')

    /**
     * Check if the Bengali context preceding an 's/sh' sound suggests
     * that it should be ষ (retroflex).
     *
     * @param bengaliContext Bengali text generated before the current 's' position
     * @return true if the sibilant should be ষ
     */
    fun shouldBeRetroflex(bengaliContext: String): Boolean {
        if (bengaliContext.isEmpty()) return false

        val lastChar = bengaliContext.last()

        // Check if preceded by ঋ or ৃ (ri vowel/sign)
        if (lastChar in RI_VOWEL_CHARS) return true

        // Check if preceded by র্ (ref form — র followed by hasanta)
        if (bengaliContext.length >= 2) {
            val secondLast = bengaliContext[bengaliContext.length - 2]
            if (secondLast == 'র' && lastChar == '্') return true
        }

        return false
    }

    /**
     * Check if preceding vowel sign suggests ষ (weaker signal).
     * ি, ী, ু, ূ before "sh" can indicate ষ in tatsama words.
     */
    fun hasVowelSignBefore(bengaliContext: String): Boolean {
        if (bengaliContext.isEmpty()) return false
        return bengaliContext.last() in SHATVA_VOWEL_SIGNS
    }

    /**
     * Check if a phonetic word starts with a prefix that triggers ষ.
     *
     * @param phoneticWord The full lowercase phonetic word
     * @return The prefix match info, or null if no prefix triggers ষ
     */
    fun matchPrefix(phoneticWord: String): PrefixMatch? {
        val lower = phoneticWord.lowercase()

        for (entry in TRIGGERING_PREFIXES) {
            if (lower.startsWith(entry.phonetic) && lower.length > entry.phonetic.length) {
                // Check if the character after prefix is 's' (potential ষ)
                val remaining = lower.substring(entry.phonetic.length)
                if (remaining.startsWith("s") || remaining.startsWith("sh")) {
                    return PrefixMatch(
                        prefix = entry.phonetic,
                        prefixBengali = entry.bengali,
                        remainingPhonetic = remaining
                    )
                }
            }
        }

        return null
    }

    /**
     * Determine sibilant position in the phonetic word.
     */
    fun getSibilantPosition(phoneticWord: String, sibilantIndex: Int): SibilantPosition {
        if (sibilantIndex == 0) return SibilantPosition.INITIAL
        // "sh" is 2 chars, "s" is 1 char
        val sibilantLen = if (sibilantIndex + 1 < phoneticWord.length && phoneticWord[sibilantIndex + 1] == 'h') 2 else 1
        if (sibilantIndex + sibilantLen >= phoneticWord.length) return SibilantPosition.FINAL
        return SibilantPosition.MEDIAL
    }

    /**
     * Resolve an 's/sh' sound considering context, prefix, and position rules.
     *
     * @param bengaliContext Bengali text built before this sibilant
     * @param phoneticWord The full phonetic word (for prefix detection)
     * @param sibilantIndex Position of "sh/s" in the phonetic word (default 0)
     * @return The resolved sibilant with confidence
     */
    fun resolve(
        bengaliContext: String,
        phoneticWord: String = "",
        sibilantIndex: Int = 0
    ): ShatvaResult {
        // 1. After ঋ/ৃ or র্ → strong signal for ষ
        if (shouldBeRetroflex(bengaliContext)) {
            return ShatvaResult(bengali = 'ষ', confidence = 0.85)
        }

        // 2. Prefix match → moderate signal for ষ
        if (phoneticWord.isNotEmpty() && matchPrefix(phoneticWord) != null) {
            return ShatvaResult(bengali = 'ষ', confidence = 0.70)
        }

        // 3. After ি/ী/ু/ূ vowel signs → weak signal for ষ (many exceptions)
        if (hasVowelSignBefore(bengaliContext)) {
            return ShatvaResult(bengali = 'ষ', confidence = 0.55)
        }

        // 4. Position-based heuristics
        if (phoneticWord.isNotEmpty()) {
            val position = getSibilantPosition(phoneticWord, sibilantIndex)

            // ষ almost NEVER starts a word — word-initial "sh" → শ
            if (position == SibilantPosition.INITIAL) {
                return ShatvaResult(bengali = 'শ', confidence = 0.80)
            }

            // Word-final "sh" → শ more common
            if (position == SibilantPosition.FINAL) {
                return ShatvaResult(bengali = 'শ', confidence = 0.65)
            }
        }

        // 5. Default to শ for "sh" sound (most common palatal sibilant in medial position)
        return ShatvaResult(bengali = 'শ', confidence = 0.55)
    }
}

enum class SibilantPosition {
    INITIAL, MEDIAL, FINAL
}

data class PrefixEntry(
    val phonetic: String,
    val bengali: String
)

data class PrefixMatch(
    val prefix: String,
    val prefixBengali: String,
    val remainingPhonetic: String
)

data class ShatvaResult(
    val bengali: Char,
    val confidence: Double
)
