package com.banglu.engine.dictionary

/**
 * Generates phonetic spelling variations for dictionary lookups.
 *
 * Bengali has many valid transliterations for the same word.
 * This generator creates common alternations so lookups can find
 * words regardless of which spelling convention the user follows.
 *
 * Example: "shanti" -> ["santi", ...] (sh<->s alternation)
 *          "oonish" -> ["unish", ...] (oo<->u alternation)
 */
object PhoneticVariantGenerator {

    private data class AlternationPair(
        val a: String,
        val b: String,
        val category: String
    )

    private val alternations: List<AlternationPair> = listOf(
        // Vowel alternations
        AlternationPair("ee", "i", "VOWEL"),
        AlternationPair("oo", "u", "VOWEL"),
        AlternationPair("aa", "a", "VOWEL"),
        AlternationPair("ou", "o", "VOWEL"),
        AlternationPair("oi", "oy", "VOWEL"),
        AlternationPair("ii", "i", "VOWEL"),
        // Consonant alternations
        AlternationPair("sh", "s", "CONSONANT"),
        AlternationPair("chh", "ch", "CONSONANT"),
        AlternationPair("rh", "r", "CONSONANT"),
        // z↔j removed: z→য and j→জ are now separate consonant rules
        AlternationPair("w", "v", "CONSONANT"),
        AlternationPair("ph", "f", "CONSONANT"),
        // Trailing alternations
        AlternationPair("o", "a", "TRAILING"),
        AlternationPair("i", "ee", "TRAILING"),
        AlternationPair("u", "oo", "TRAILING")
    )

    /**
     * Generate phonetic spelling variations for a canonical form.
     *
     * @param canonical The canonical phonetic spelling
     * @param maxVariants Maximum number of variants to return (excluding original)
     * @return List of variant spellings (does not include the original)
     */
    fun generate(canonical: String, maxVariants: Int = 8): List<String> {
        val key = canonical.lowercase().trim()
        if (key.isEmpty()) return emptyList()

        val variants = mutableSetOf<String>()

        for (alt in alternations) {
            // If canonical contains side A, create variant with side B
            if (key.contains(alt.a)) {
                val variant = key.replace(alt.a, alt.b)
                if (variant != key && variant.isNotEmpty()) {
                    variants.add(variant)
                }
            }

            // If canonical contains side B, create variant with side A
            if (key.contains(alt.b)) {
                val variant = key.replace(alt.b, alt.a)
                if (variant != key && variant.isNotEmpty()) {
                    variants.add(variant)
                }
            }
        }

        // Remove original if it snuck in, deduplicate, limit
        variants.remove(key)
        return variants.toList().take(maxVariants)
    }
}
