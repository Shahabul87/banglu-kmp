package com.banglu.engine.platform

data class PhoneticIndexHit(
    val bengali: String,
    val frequency: Int,
    val tier: Int, // TIER_A (suggestible) or TIER_B (exact-match only)
    val priority: Int = PRIORITY_CANONICAL // key axis: canonical romanization vs habit alias
) {
    companion object {
        const val TIER_A = 0
        const val TIER_B = 1

        /** Key is the faithful romanization of the word (priority 0). */
        const val PRIORITY_CANONICAL = 0

        /** Key is a lazy-typing habit alias (priority 1) — loses to canonical owners on collision. */
        const val PRIORITY_HABIT = 1
    }
}

/**
 * Query interface over the precompiled phonetic index (Engine v3 spec 3.2).
 * Implementations must be safe to call on every keystroke (< 5ms typical).
 * Android: persistent read-only sqlite connection (joins words internally).
 * Tests/JVM: in-memory maps.
 */
interface PhoneticIndexStore {
    /**
     * Words whose canonical/variant key equals [key], ordered by
     * (priority ascending, frequency descending): a word that owns [key] as its
     * canonical romanization always precedes habit-alias claimants, regardless
     * of frequency. Implementations may cap the result size (at least 16
     * entries guaranteed when more exist).
     */
    fun lookupExact(key: String): List<PhoneticIndexHit>

    /**
     * Tier A words whose key starts with [prefix], frequency-descending.
     * [limit] must be >= 0; implementations return at most [limit] hits.
     */
    fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit>

    /** Bengali rendering for an English word key, or null. */
    fun lookupEnglish(key: String): String?

    /** True if [bengali] is a word in the compiled dictionary (words table). */
    fun containsWord(bengali: String): Boolean
}
