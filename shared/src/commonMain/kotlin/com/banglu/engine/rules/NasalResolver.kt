package com.banglu.engine.rules

/**
 * NasalResolver - Resolves ং vs ঙ ambiguity
 *
 * Rule: ঙ appears ONLY before গ/ঘ (as part of ঙ্গ/ঙ্ঘ conjuncts)
 * or before vowels (anusvara cannot take vowel kars).
 * Everywhere else, the nasal "ng" sound is ং (anusvara).
 *
 * This rule is ~95% reliable. Dictionary still overrides.
 */
object NasalResolver {

    /**
     * Determine whether "ng" at the current position should be ং or ঙ.
     *
     * @param nextPhoneticChar The character immediately following "ng" in phonetic input
     *                         (null if "ng" is at end of word)
     * @return 'ঙ' if before g or vowel, 'ং' otherwise
     */
    fun resolve(nextPhoneticChar: String?): Char {
        if (nextPhoneticChar == null) return 'ং'

        val next = nextPhoneticChar.lowercase()

        // ঙ before গ (g/gh) for ঙ্গ/ঙ্ঘ conjuncts
        if (next == "g") return 'ঙ'

        // ঙ before vowels — ং (anusvara) cannot take vowel kars,
        // so "ng" + vowel must use ঙ (the consonant).
        if (next in listOf("a", "e", "i", "o", "u")) return 'ঙ'

        return 'ং'
    }

    /**
     * Check if a position in phonetic input has an "ng" that should be ঙ.
     * Looks ahead in the input to determine context.
     *
     * @param input Full phonetic input
     * @param ngPosition Position where "ng" starts in the input
     * @return The resolved Bengali character with confidence
     */
    fun resolveInContext(input: String, ngPosition: Int): NasalResult {
        val afterNg = ngPosition + 2 // "ng" is 2 characters
        val nextChar = if (afterNg < input.length) input[afterNg].toString() else null

        val result = resolve(nextChar)

        return NasalResult(
            bengali = result,
            confidence = if (result == 'ং') 0.95 else 0.90
        )
    }
}

data class NasalResult(
    val bengali: Char,
    val confidence: Double
)
