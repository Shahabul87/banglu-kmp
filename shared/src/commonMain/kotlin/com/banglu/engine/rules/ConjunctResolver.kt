package com.banglu.engine.rules

import com.banglu.engine.types.ConjunctMatch

/**
 * ConjunctResolver - 100% reliable conjunct auto-resolution rules
 *
 * These rules have NO exceptions in standard Bengali:
 * - Sibilant + Retroflex stop = Always ষ
 * - Sibilant + Dental stop = Always স
 * - Sibilant + Palatal = Always শ
 * - ক্ষ = Always ক + ষ
 */
object ConjunctResolver {

    /**
     * Longest-match table of phonetic -> Bengali conjuncts.
     * Ordered longest-first within each group for greedy matching.
     */
    private data class ConjunctMapEntry(
        val phonetic: String,
        val bengali: String
    )

    private val CONJUNCT_MAP: List<ConjunctMapEntry> = listOf(
        // ========== ষ conjuncts (sibilant + retroflex) ==========
        ConjunctMapEntry("shtr", "ষ্ট্র"),   // রাষ্ট্র (must be before sht)
        ConjunctMapEntry("shth", "ষ্ঠ"),     // ষষ্ঠ, নিষ্ঠা, প্রতিষ্ঠা
        ConjunctMapEntry("shph", "ষ্ফ"),     // নিষ্ফল
        ConjunctMapEntry("sht", "ষ্ট"),      // কষ্ট, নষ্ট, পুষ্ট, দৃষ্ট
        ConjunctMapEntry("shn", "ষ্ণ"),      // কৃষ্ণ, তৃষ্ণা, উষ্ণ
        ConjunctMapEntry("shk", "ষ্ক"),      // নিষ্কর, পরিষ্কার
        ConjunctMapEntry("shp", "ষ্প"),      // নিষ্পন্ন
        ConjunctMapEntry("shm", "ষ্ম"),      // গ্রীষ্ম, উষ্ম

        // ========== স conjuncts (sibilant + dental) ==========
        ConjunctMapEntry("str", "স্ত্র"),     // স্ত্রী, অস্ত্র (must be before st)
        ConjunctMapEntry("sth", "স্থ"),      // স্থান, স্থির, স্থাপন
        ConjunctMapEntry("sph", "স্ফ"),      // স্ফটিক, স্ফুলিঙ্গ
        ConjunctMapEntry("st", "স্ত"),       // বস্তু, অস্ত, রাস্তা
        ConjunctMapEntry("sn", "স্ন"),       // স্নান, স্নেহ
        ConjunctMapEntry("sk", "স্ক"),       // স্কুল, মস্ক
        ConjunctMapEntry("sp", "স্প"),       // স্পর্শ, স্পষ্ট
        ConjunctMapEntry("sm", "স্ম"),       // স্মরণ, স্মৃতি
        ConjunctMapEntry("sl", "স্ল"),       // স্লোগান
        ConjunctMapEntry("sr", "স্র"),       // স্রষ্টা, স্রোত
        ConjunctMapEntry("sw", "স্ব"),       // স্বাধীন, স্বপ্ন
        ConjunctMapEntry("sb", "স্ব"),       // Alternative spelling for স্ব

        // ========== শ conjuncts (sibilant + palatal / specific) ==========
        ConjunctMapEntry("shchh", "শ্ছ"),    // নিশ্ছিদ্র
        ConjunctMapEntry("shch", "শ্চ"),     // নিশ্চয়, পশ্চিম
        ConjunctMapEntry("shr", "শ্র"),      // শ্রম, শ্রদ্ধা, শ্রেণী
        ConjunctMapEntry("shl", "শ্ল"),      // শ্লোক, শ্লাঘা
        ConjunctMapEntry("shb", "শ্ব"),      // বিশ্ব, শ্বাস
        ConjunctMapEntry("shw", "শ্ব"),      // Alternative for শ্ব

        // ========== ক্ষ conjunct ==========
        ConjunctMapEntry("kkh", "ক্ষ"),      // শিক্ষা, ক্ষমা
        ConjunctMapEntry("ksh", "ক্ষ"),      // Alternative for ক্ষ

        // ========== V2 lowercase explicit clusters ==========
        ConjunctMapEntry("xo", "ক্স")        // বাক্স, কক্সবাজার
    )

    /**
     * Try to match a conjunct pattern at the current position in the phonetic input.
     * Uses longest-match (greedy) strategy.
     *
     * @param input The full lowercase phonetic input
     * @param position Current position in the input to match from
     * @return ConjunctMatch if a locked conjunct is found, null otherwise
     */
    fun matchAt(input: String, position: Int): ConjunctMatch? {
        val remaining = input.substring(position).lowercase()

        for (entry in CONJUNCT_MAP) {
            if (remaining.startsWith(entry.phonetic)) {
                return ConjunctMatch(
                    bengali = entry.bengali,
                    consumed = entry.phonetic.length,
                    confidence = 1.0
                )
            }
        }

        return null
    }

    /**
     * Check if a phonetic string contains any conjunct pattern.
     * Returns all conjunct matches found with their positions.
     */
    fun findAll(input: String): List<ConjunctMatchWithPosition> {
        val results = mutableListOf<ConjunctMatchWithPosition>()
        val lower = input.lowercase()
        var i = 0

        while (i < lower.length) {
            val match = matchAt(lower, i)
            if (match != null) {
                results.add(
                    ConjunctMatchWithPosition(
                        bengali = match.bengali,
                        consumed = match.consumed,
                        confidence = match.confidence,
                        position = i
                    )
                )
                i += match.consumed
            } else {
                i++
            }
        }

        return results
    }

    /**
     * Resolve a complete phonetic word by applying conjunct rules.
     * Returns the segments with all conjuncts resolved.
     * Characters that don't match any conjunct pattern are returned as-is (lowercase phonetic).
     */
    fun resolveConjuncts(input: String): List<ConjunctSegment> {
        val segments = mutableListOf<ConjunctSegment>()
        val lower = input.lowercase()
        var i = 0

        while (i < lower.length) {
            val match = matchAt(lower, i)
            if (match != null) {
                segments.add(
                    ConjunctSegment(
                        text = match.bengali,
                        isConjunct = true,
                        consumed = match.consumed
                    )
                )
                i += match.consumed
            } else {
                segments.add(
                    ConjunctSegment(
                        text = lower[i].toString(),
                        isConjunct = false,
                        consumed = 1
                    )
                )
                i++
            }
        }

        return segments
    }

    /**
     * Check if a specific phonetic pattern is a known conjunct.
     */
    fun isConjunct(pattern: String): Boolean {
        val lower = pattern.lowercase()
        return CONJUNCT_MAP.any { it.phonetic == lower }
    }

    /**
     * Get the Bengali conjunct for a given phonetic pattern.
     */
    fun resolve(pattern: String): String? {
        val lower = pattern.lowercase()
        return CONJUNCT_MAP.find { it.phonetic == lower }?.bengali
    }
}

data class ConjunctMatchWithPosition(
    val bengali: String,
    val consumed: Int,
    val confidence: Double,
    val position: Int
)

data class ConjunctSegment(
    val text: String,
    val isConjunct: Boolean,
    val consumed: Int
)
