package com.banglu.engine.dictionary

/**
 * Result of mapping a phonetic input to Bengali dictionary sections.
 *
 * @param bengaliPrefixes List of possible Bengali prefixes for this input
 * @param isAmbiguous True if the input maps to multiple possible sections
 * @param confidence How confident the mapping is (0.0 to 1.0)
 */
data class SectionMapping(
    val bengaliPrefixes: List<String>,
    val isAmbiguous: Boolean,
    val confidence: Double
)

/**
 * Mapping of an initial consonant to its Bengali equivalents.
 *
 * @param bengali List of possible Bengali consonants
 * @param confidence How unambiguous this mapping is (1.0 = single option)
 */
data class ConsonantMapping(
    val bengali: List<String>,
    val confidence: Double
)

/**
 * Maps English phonetic input to Bengali section prefixes for dictionary narrowing.
 *
 * Given a phonetic string like "kh" or "kha", determines which Bengali prefixes
 * (e.g., "খ", "খা") to search in the 480K dictionary. Uses greedy longest-first
 * matching for consonants and dependent/independent vowel mappings.
 */
object PhoneticSectionMapper {

    // Ordered longest-first for greedy matching
    private val INITIAL_CONSONANTS: List<Pair<String, ConsonantMapping>> = listOf(
        "chh" to ConsonantMapping(listOf("ছ"), 0.95),
        "kkh" to ConsonantMapping(listOf("ক্ষ"), 0.95),
        "ksh" to ConsonantMapping(listOf("ক্ষ"), 0.95),
        "ch" to ConsonantMapping(listOf("চ", "ছ"), 0.70),
        "kh" to ConsonantMapping(listOf("খ"), 1.0),
        "gh" to ConsonantMapping(listOf("ঘ"), 1.0),
        "jh" to ConsonantMapping(listOf("ঝ"), 1.0),
        "th" to ConsonantMapping(listOf("থ", "ঠ"), 0.80),
        "dh" to ConsonantMapping(listOf("ধ", "ঢ"), 0.80),
        "ph" to ConsonantMapping(listOf("ফ"), 1.0),
        "bh" to ConsonantMapping(listOf("ভ"), 1.0),
        "sh" to ConsonantMapping(listOf("শ", "ষ"), 0.60),
        "ng" to ConsonantMapping(listOf("ং", "ঙ"), 0.70),
        "k" to ConsonantMapping(listOf("ক"), 1.0),
        "g" to ConsonantMapping(listOf("গ"), 1.0),
        "c" to ConsonantMapping(listOf("ছ", "চ"), 0.62),
        "j" to ConsonantMapping(listOf("জ", "য"), 0.70),
        "t" to ConsonantMapping(listOf("ত", "ট"), 0.80),
        "d" to ConsonantMapping(listOf("দ", "ড"), 0.75),
        "n" to ConsonantMapping(listOf("ন", "ণ"), 0.85),
        "p" to ConsonantMapping(listOf("প"), 1.0),
        "b" to ConsonantMapping(listOf("ব"), 1.0),
        "m" to ConsonantMapping(listOf("ম"), 1.0),
        "r" to ConsonantMapping(listOf("র"), 1.0),
        "l" to ConsonantMapping(listOf("ল"), 1.0),
        "s" to ConsonantMapping(listOf("স", "শ", "ষ"), 0.60),
        "h" to ConsonantMapping(listOf("হ"), 1.0),
        "y" to ConsonantMapping(listOf("য"), 1.0),
    )

    // Dependent vowel signs (added after consonant) — ordered longest-first
    private val VOWEL_DEPENDENT: List<Pair<String, String>> = listOf(
        "ou" to "ৌ",
        "oi" to "ৈ",
        "oo" to "ূ",
        "ee" to "ী",
        "ii" to "ী",
        "uu" to "ূ",
        "a" to "া",
        "i" to "ি",
        "u" to "ু",
        "e" to "ে",
        "o" to "ো",
    )

    // Independent vowels (word-initial) — ordered longest-first
    private val INITIAL_VOWELS: List<Pair<String, Pair<List<String>, Double>>> = listOf(
        "ou" to Pair(listOf("ঔ"), 0.90),
        "oi" to Pair(listOf("ঐ"), 0.90),
        "oo" to Pair(listOf("ঊ"), 0.85),
        "ee" to Pair(listOf("ঈ"), 0.85),
        "a" to Pair(listOf("অ", "আ"), 0.60),
        "i" to Pair(listOf("ই", "ঈ"), 0.70),
        "u" to Pair(listOf("উ", "ঊ"), 0.70),
        "e" to Pair(listOf("এ", "ঐ"), 0.70),
        "o" to Pair(listOf("ও", "অ"), 0.60),
    )

    /**
     * Map a phonetic input string to Bengali section prefixes.
     *
     * Analyzes the beginning of the input to determine which Bengali prefix
     * sections are relevant. Handles both vowel-initial and consonant-initial words.
     *
     * @param phoneticInput The English phonetic input
     * @return SectionMapping with Bengali prefixes, ambiguity flag, and confidence
     */
    fun mapToSections(phoneticInput: String): SectionMapping {
        val input = phoneticInput.lowercase().trim()
        if (input.isEmpty()) return SectionMapping(emptyList(), false, 0.0)

        // Check if starts with vowel
        if (input[0] in "aeiou") return mapVowelInitial(input)

        // Find longest consonant match
        for ((pattern, mapping) in INITIAL_CONSONANTS) {
            if (input.startsWith(pattern)) {
                val remaining = input.substring(pattern.length)
                // Check for following vowel to form consonant+vowel section
                if (remaining.isNotEmpty()) {
                    // Try longest vowel match first (already sorted longest-first)
                    for ((vPattern, vSign) in VOWEL_DEPENDENT) {
                        if (remaining.startsWith(vPattern)) {
                            val sections = mapping.bengali.map { it + vSign }
                            return SectionMapping(sections, sections.size > 1, mapping.confidence)
                        }
                    }
                }
                // No vowel - just consonant sections
                return SectionMapping(mapping.bengali, mapping.bengali.size > 1, mapping.confidence)
            }
        }

        return SectionMapping(emptyList(), false, 0.0)
    }

    private fun mapVowelInitial(input: String): SectionMapping {
        for ((pattern, pair) in INITIAL_VOWELS) {
            if (input.startsWith(pattern)) {
                return SectionMapping(pair.first, pair.first.size > 1, pair.second)
            }
        }
        return SectionMapping(emptyList(), false, 0.0)
    }
}
