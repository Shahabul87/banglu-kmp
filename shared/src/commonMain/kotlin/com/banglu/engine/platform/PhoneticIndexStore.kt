package com.banglu.engine.platform

data class PhoneticIndexHit(
    val bengali: String,
    val frequency: Int,
    val tier: Int // 0 = Tier A (suggestible), 1 = Tier B (exact-match only)
)

/**
 * Query interface over the precompiled phonetic index (Engine v3 spec 3.2).
 * Implementations must be safe to call on every keystroke (< 5ms typical).
 * Android: persistent read-only sqlite connection (joins words internally).
 * Tests/JVM: in-memory maps.
 */
interface PhoneticIndexStore {
    /** All words whose canonical/variant key equals [key], frequency-descending. */
    fun lookupExact(key: String): List<PhoneticIndexHit>

    /** Tier A words whose key starts with [prefix], frequency-descending. */
    fun lookupPrefix(prefix: String, limit: Int): List<PhoneticIndexHit>

    /** Bengali rendering for an English word key, or null. */
    fun lookupEnglish(key: String): String?
}
