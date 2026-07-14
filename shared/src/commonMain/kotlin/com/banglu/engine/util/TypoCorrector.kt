package com.banglu.engine.util

import com.banglu.engine.dictionary.SmartDictionary

/**
 * Result of a typo correction attempt.
 */
data class TypoCorrectionResult(
    val corrected: String,
    val original: String,
    val correctionType: String
)

/**
 * TypoCorrector - Tries to fix common typos against the dictionary.
 *
 * Strategies tried in order:
 * 1. Transposition: swap adjacent characters
 * 2. Double reduction: remove one of a doubled character
 * 3. Vowel insertion: insert a vowel between consecutive consonants
 *
 * Ported from TypeScript: src/engine/smart/TypoCorrector.ts
 */
object TypoCorrector {

    private const val MAX_INPUT_LENGTH = 15
    private const val MIN_INPUT_LENGTH = 2
    private val VOWELS = listOf('a', 'e', 'i', 'o', 'u')

    /**
     * Attempt to correct a typo by trying multiple strategies.
     *
     * @param input The user's raw phonetic input
     * @param dictionary The SmartDictionary to check corrections against
     * @return A correction result if found, or null if no correction applies
     */
    fun correct(input: String, dictionary: SmartDictionary): TypoCorrectionResult? {
        val key = input.lowercase().trim()
        if (key.length < MIN_INPUT_LENGTH || key.length > MAX_INPUT_LENGTH) return null

        // If the word is already in the dictionary, no correction needed
        if (dictionary.has(key)) return null

        return tryTransposition(key, dictionary)
            ?: tryDoubleReduction(key, dictionary)
            ?: tryVowelInsertion(key, dictionary)
    }

    /**
     * Try swapping adjacent characters to see if a valid word is produced.
     * Skips pairs where both characters are the same (no effect).
     */
    private fun tryTransposition(key: String, dictionary: SmartDictionary): TypoCorrectionResult? {
        val chars = key.toCharArray()
        for (i in 0 until chars.size - 1) {
            if (chars[i] == chars[i + 1]) continue

            // Swap
            val temp = chars[i]
            chars[i] = chars[i + 1]
            chars[i + 1] = temp

            val candidate = chars.concatToString()
            if (dictionary.has(candidate)) {
                return TypoCorrectionResult(candidate, key, "transposition")
            }

            // Swap back
            chars[i + 1] = chars[i]
            chars[i] = temp
        }
        return null
    }

    /**
     * Try removing one of a doubled character to see if a valid word is produced.
     */
    private fun tryDoubleReduction(key: String, dictionary: SmartDictionary): TypoCorrectionResult? {
        for (i in 0 until key.length - 1) {
            if (key[i] == key[i + 1]) {
                val candidate = key.substring(0, i) + key.substring(i + 1)
                if (candidate.length >= MIN_INPUT_LENGTH && dictionary.has(candidate)) {
                    return TypoCorrectionResult(candidate, key, "reduction")
                }
            }
        }
        return null
    }

    /**
     * Try inserting a vowel between consecutive consonant characters.
     */
    private fun tryVowelInsertion(key: String, dictionary: SmartDictionary): TypoCorrectionResult? {
        for (i in 0 until key.length - 1) {
            if (key[i] in VOWELS || key[i + 1] in VOWELS) continue

            for (vowel in VOWELS) {
                val candidate = key.substring(0, i + 1) + vowel + key.substring(i + 1)
                if (candidate.length <= MAX_INPUT_LENGTH && dictionary.has(candidate)) {
                    return TypoCorrectionResult(candidate, key, "insertion")
                }
            }
        }
        return null
    }
}
