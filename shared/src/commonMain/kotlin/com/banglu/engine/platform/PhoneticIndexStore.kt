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

    /** S143: every roman key the english_lexicon knows — the in-memory index
     *  the English spelling rescue searches (one slip = ~26·n variants; the
     *  store is never asked per variant). Empty when unsupported. */
    fun englishKeys(): Set<String> = emptySet()

    /** True if [bengali] is a word in the compiled dictionary (words table). */
    fun containsWord(bengali: String): Boolean

    // ── S102: extended-dictionary access (trie retirement) ───────────────
    // The 130K-entry extended dictionary used to be materialized into the
    // in-RAM PhoneticTrie (~70-90MB, the largest full-mode heap structure)
    // even though the same tables sit in the sqlite asset. Stores that can
    // serve them (full sqlite on Android/desktop, the jvmTest in-memory
    // fixture) answer these directly; SmartDictionary then skips the trie
    // load and merges these hits into its lookups on the async engine lane.
    // Slim/JS stores keep the defaults — those surfaces never had the trie.

    /** True when the extended-dictionary tables are available to query. */
    fun hasExtendedData(): Boolean = false

    /** Extended entries whose phonetic equals [key], frequency-descending. */
    fun lookupExtendedExact(key: String): List<ExtendedDictionaryHit> = emptyList()

    /** Extended entries whose phonetic starts with [prefix], frequency-descending. */
    fun lookupExtendedPrefix(prefix: String, limit: Int): List<ExtendedDictionaryHit> = emptyList()

    /** Canonical phonetic for an extended Bengali word, or null. */
    fun extendedPhoneticForBengali(bengali: String): String? = null
}

/** One extended-dictionary row: [phonetic] key -> [bengali] at [frequency]. */
data class ExtendedDictionaryHit(
    val phonetic: String,
    val bengali: String,
    val frequency: Int
)
