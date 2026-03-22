package com.banglu.engine.dictionary

/**
 * Expands non-standard phonetic input to canonical forms that may exist in the dictionary.
 *
 * Bengali typists use varied transliterations. This normalizer generates
 * alternative spellings so the dictionary can find matches.
 *
 * Example: "biggan" -> ["biggan", "bigyan", "bignon"]
 *          "dunia"  -> ["dunia", "duniya"]
 *          "shokal" -> ["shokal", "sokal"]
 */
object TypingHabitNormalizer {

    private data class HabitRule(
        val pattern: String,
        val replacements: List<String>,
        val context: String,
        val priority: Int
    )

    private val rules: List<HabitRule> = listOf(
        HabitRule("gg", listOf("gy", "gn"), "medial", 90),
        HabitRule("dd", listOf("dy", "ddh", "dv"), "medial", 90),
        HabitRule("ia", listOf("iya"), "medial", 85),
        HabitRule("nn", listOf("ny", "nno"), "medial", 80),
        HabitRule("ua", listOf("uya", "owa"), "medial", 80),
        HabitRule("ie", listOf("iye"), "medial", 80),
        HabitRule("ue", listOf("uye"), "medial", 75),
        HabitRule("io", listOf("iyo"), "medial", 75),
        HabitRule("cc", listOf("cch", "chy"), "medial", 70),
        HabitRule("bb", listOf("by", "bv"), "medial", 70),
        HabitRule("sh", listOf("s"), "initial", 60),
        HabitRule("pp", listOf("py", "pn"), "medial", 60),
        HabitRule("mm", listOf("my", "mn"), "medial", 60),
        HabitRule("ng", listOf("nk", "nkh"), "medial", 40)
    ).sortedByDescending { it.priority }

    /**
     * Expand user input into canonical phonetic variants.
     *
     * @param input The user's phonetic input
     * @param maxVariants Maximum number of variants to return (including original)
     * @return List of phonetic variants, always including the original input
     */
    fun expand(input: String, maxVariants: Int = 8): List<String> {
        val key = input.lowercase().trim()

        // Short inputs: no expansion
        if (key.isEmpty() || key.length < 3) {
            return listOf(key.ifEmpty { input })
        }

        val results = mutableSetOf(key)

        for (rule in rules) {
            if (results.size >= maxVariants) break

            // Find all occurrences of the pattern
            var idx = key.indexOf(rule.pattern)
            while (idx >= 0 && results.size < maxVariants) {
                // Check context
                val contextMatch = when (rule.context) {
                    "initial" -> idx == 0
                    "medial" -> idx > 0
                    else -> true
                }

                if (contextMatch) {
                    for (replacement in rule.replacements) {
                        if (results.size >= maxVariants) break

                        val variant = key.substring(0, idx) + replacement + key.substring(idx + rule.pattern.length)

                        if (variant.isNotEmpty() && variant != key && variant.length >= 2) {
                            results.add(variant)
                        }
                    }
                }

                // Find next occurrence after this one
                idx = key.indexOf(rule.pattern, idx + 1)
            }
        }

        return results.toList()
    }
}
