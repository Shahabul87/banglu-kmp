package com.banglu.engine.rules

/**
 * NatvaVidhan (ণত্ব বিধান) - When ন becomes ণ
 *
 * Core Rule: After ঋ, র, or ষ in the same tatsama word,
 * dental ন becomes retroflex ণ, even with intervening
 * "transparent" consonants (ক-group, প-group, য, ব, হ, ং).
 *
 * Blocking consonants: ত/থ/দ/ধ/ন (dental group), চ/ছ/জ/ঝ/ঞ (palatal),
 * ট/ঠ/ড/ঢ (retroflex stops — but not ণ itself), শ, স, ল.
 *
 * NOTE: This is a morphological heuristic. The dictionary ALWAYS takes priority.
 */
object NatvaVidhan {

    // Trigger characters: after these, ন may become ণ
    private val TRIGGERS = setOf('ঋ', 'ৃ', 'র', 'ষ')

    // Transparent consonants — don't block the ণ transformation
    private val TRANSPARENT_CONSONANTS = setOf(
        // ক-group
        'ক', 'খ', 'গ', 'ঘ', 'ঙ',
        // প-group
        'প', 'ফ', 'ব', 'ভ', 'ম',
        // Others
        'য', 'হ', 'ং', 'ঁ'
    )

    // Bengali vowels and vowel signs — also transparent
    private val VOWELS_AND_SIGNS = setOf(
        'অ', 'আ', 'ই', 'ঈ', 'উ', 'ঊ', 'ঋ', 'এ', 'ঐ', 'ও', 'ঔ',
        'া', 'ি', 'ী', 'ু', 'ূ', 'ৃ', 'ে', 'ৈ', 'ো', 'ৌ',
        '্' // hasanta is transparent for scanning
    )

    // Blockers — these characters stop the ণ transformation
    private val BLOCKERS = setOf(
        // Dental group
        'ত', 'থ', 'দ', 'ধ', 'ন',
        // Palatal group
        'চ', 'ছ', 'জ', 'ঝ', 'ঞ',
        // Retroflex stops (not ণ)
        'ট', 'ঠ', 'ড', 'ঢ',
        // Others
        'শ', 'স', 'ল'
    )

    /**
     * Given a Bengali string built so far (the context preceding the current 'n'),
     * determine if the 'n' should be ণ instead of ন.
     *
     * @param bengaliContext The Bengali text generated before the current 'n' position
     * @return true if ন should become ণ based on ণত্ব বিধান
     */
    fun shouldBeRetroflex(bengaliContext: String): Boolean {
        if (bengaliContext.isEmpty()) return false

        // Scan backwards from end of context
        for (i in bengaliContext.length - 1 downTo 0) {
            val char = bengaliContext[i]

            // Found a trigger — ন should become ণ
            if (char in TRIGGERS) return true

            // Found a blocker — ন stays as ন
            if (char in BLOCKERS) return false

            // Transparent consonant or vowel — keep scanning
            if (char in TRANSPARENT_CONSONANTS || char in VOWELS_AND_SIGNS) {
                continue
            }

            // Unknown character — treat as blocker (safe default)
            return false
        }

        return false
    }

    /**
     * Resolve 'n' in a phonetic input based on the Bengali output built so far.
     *
     * @param bengaliContext Bengali text generated before this 'n'
     * @return The resolved character with confidence
     */
    fun resolve(bengaliContext: String): NatvaResult {
        return if (shouldBeRetroflex(bengaliContext)) {
            NatvaResult(bengali = 'ণ', confidence = 0.80)
        } else {
            NatvaResult(bengali = 'ন', confidence = 0.85)
        }
    }
}

data class NatvaResult(
    val bengali: Char,
    val confidence: Double
)
