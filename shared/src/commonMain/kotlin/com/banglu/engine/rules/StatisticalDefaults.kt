package com.banglu.engine.rules

/**
 * StatisticalDefaults - Fallback probabilities for ambiguous phonetics
 *
 * When neither dictionary nor rule-based resolution is conclusive,
 * these statistical defaults provide the most probable Bengali character.
 * Each entry includes the primary (most likely) and secondary (alternative)
 * Bengali characters with a confidence score for the primary.
 */
object StatisticalDefaults {

    data class DefaultEntry(
        val primary: String,
        val secondary: String,
        val confidence: Double
    )

    /**
     * Map of phonetic patterns to their statistical default resolutions.
     */
    val DEFAULTS: Map<String, DefaultEntry> = mapOf(
        "t" to DefaultEntry(primary = "ত", secondary = "ট", confidence = 0.80),
        "d" to DefaultEntry(primary = "দ", secondary = "ড", confidence = 0.75),
        "n" to DefaultEntry(primary = "ন", secondary = "ণ", confidence = 0.85),
        "sh" to DefaultEntry(primary = "শ", secondary = "ষ", confidence = 0.55),
        "s" to DefaultEntry(primary = "স", secondary = "শ", confidence = 0.60),
        "r" to DefaultEntry(primary = "র", secondary = "ড়", confidence = 0.85),
        "ng" to DefaultEntry(primary = "ং", secondary = "ঙ", confidence = 0.95),
        "i" to DefaultEntry(primary = "ই", secondary = "ঈ", confidence = 0.85),
        "u" to DefaultEntry(primary = "উ", secondary = "ঊ", confidence = 0.90)
    )

    /**
     * Get the default resolution for a phonetic pattern.
     *
     * @param phonetic The phonetic pattern to look up
     * @return The default entry with primary, secondary, and confidence, or null if not found
     */
    fun getDefault(phonetic: String): DefaultEntry? {
        return DEFAULTS[phonetic.lowercase()]
    }

    /**
     * Get the primary (most likely) Bengali character for a phonetic pattern.
     *
     * @param phonetic The phonetic pattern to look up
     * @return The primary Bengali character, or null if not found
     */
    fun getPrimary(phonetic: String): String? {
        return DEFAULTS[phonetic.lowercase()]?.primary
    }

    /**
     * Get the confidence score for the primary resolution.
     *
     * @param phonetic The phonetic pattern to look up
     * @return The confidence score (0.0-1.0), or null if not found
     */
    fun getConfidence(phonetic: String): Double? {
        return DEFAULTS[phonetic.lowercase()]?.confidence
    }
}
