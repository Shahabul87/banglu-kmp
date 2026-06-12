package com.banglu.engine.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPhoneticIndexStoreTest {

    private val store = InMemoryPhoneticIndexStore(
        entries = listOf(
            // "ami" entries: TIER_B hit inserted first (lower frequency) to confirm sort is by
            // frequency, not insertion order.
            PhoneticIndexHit("আমিও", 10, PhoneticIndexHit.TIER_B) to "ami",
            PhoneticIndexHit("আমি", 100, PhoneticIndexHit.TIER_A) to "ami",
            PhoneticIndexHit("আমিষ", 10, PhoneticIndexHit.TIER_B) to "amish",
            PhoneticIndexHit("কথা", 95, PhoneticIndexHit.TIER_A) to "kotha"
        ),
        english = mapOf("scooter" to "স্কুটার")
    )

    @Test
    fun exactLookupReturnsBothTiersSortedByFrequency() {
        val hits = store.lookupExact("ami")
        // Both TIER_A and TIER_B returned, ordered by frequency descending.
        assertEquals(listOf("আমি", "আমিও"), hits.map { it.bengali })
    }

    @Test
    fun prefixLookupIsTierAOnly() {
        val hits = store.lookupPrefix("am", limit = 10)
        // আমিও is TIER_B → excluded; only TIER_A আমি should appear.
        assertTrue(hits.all { it.tier == PhoneticIndexHit.TIER_A })
        assertEquals(listOf("আমি"), hits.map { it.bengali })
    }

    @Test
    fun tierBReachableByExactMatchOnly() {
        assertEquals(listOf("আমিষ"), store.lookupExact("amish").map { it.bengali })
    }

    @Test
    fun englishLexiconLookup() {
        assertEquals("স্কুটার", store.lookupEnglish("scooter"))
        assertNull(store.lookupEnglish("zzz"))
    }

    /**
     * S2/S4 key model: WITHIN a tier the canonical owner of a key (priority 0)
     * beats a habit-alias claimant (priority 1) even when the alias word is far
     * more frequent — suru → সুরু (canonical, freq 50) before শুরু (h_lazy
     * alias, freq 500) when both are Tier A. Frequency still breaks ties
     * within a priority band, and Tier B sorts after Tier A regardless.
     */
    @Test
    fun exactLookupOrdersByPriorityBeforeFrequencyWithinTier() {
        val priorityStore = InMemoryPhoneticIndexStore(
            entries = listOf(
                PhoneticIndexHit("শুরু", 500, PhoneticIndexHit.TIER_A, PhoneticIndexHit.PRIORITY_HABIT) to "suru",
                PhoneticIndexHit("সুরু", 50, PhoneticIndexHit.TIER_A, PhoneticIndexHit.PRIORITY_CANONICAL) to "suru",
                PhoneticIndexHit("সুরুয়া", 10, PhoneticIndexHit.TIER_B, PhoneticIndexHit.PRIORITY_HABIT) to "suru"
            )
        )
        val hits = priorityStore.lookupExact("suru")
        assertEquals(listOf("সুরু", "শুরু", "সুরুয়া"), hits.map { it.bengali })
        assertEquals(listOf(0, 1, 1), hits.map { it.priority })
    }

    /**
     * S4/C1 tier-first ranking: a Tier-B junk word that canonically owns a key
     * must NOT hijack it from a Tier-A real-usage word reached via a habit
     * alias — bishas → বিশ্বাস (Tier A, alias, freq 79) before বিষাস (Tier B,
     * canonical owner, freq 1). This is the S3 regression class that crashed
     * H2/H3/H4 under priority-first ordering.
     */
    @Test
    fun exactLookupOrdersByTierBeforePriority() {
        val tierStore = InMemoryPhoneticIndexStore(
            entries = listOf(
                PhoneticIndexHit("বিষাস", 1, PhoneticIndexHit.TIER_B, PhoneticIndexHit.PRIORITY_CANONICAL) to "bishas",
                PhoneticIndexHit("বিশ্বাস", 79, PhoneticIndexHit.TIER_A, PhoneticIndexHit.PRIORITY_HABIT) to "bishas",
                PhoneticIndexHit("বিশ্বাস্য", 24, PhoneticIndexHit.TIER_B, PhoneticIndexHit.PRIORITY_HABIT) to "bishas"
            )
        )
        val hits = tierStore.lookupExact("bishas")
        assertEquals(listOf("বিশ্বাস", "বিষাস", "বিশ্বাস্য"), hits.map { it.bengali })
        assertEquals(listOf(PhoneticIndexHit.TIER_A, PhoneticIndexHit.TIER_B, PhoneticIndexHit.TIER_B), hits.map { it.tier })
    }

    /** Habit-alias keys still SUGGEST their word — tier filters junk, priority only orders. */
    @Test
    fun habitAliasHitsRemainSuggestibleWhenTierA() {
        val store = InMemoryPhoneticIndexStore(
            entries = listOf(
                PhoneticIndexHit("স্বাস্থ্য", 80, PhoneticIndexHit.TIER_A, PhoneticIndexHit.PRIORITY_HABIT) to "sastho"
            )
        )
        assertEquals(listOf("স্বাস্থ্য"), store.lookupPrefix("sast", limit = 5).map { it.bengali })
        assertEquals(listOf("স্বাস্থ্য"), store.lookupExact("sastho").map { it.bengali })
    }

    @Test
    fun prefixLimitTruncatesAfterSorting() {
        val limitStore = InMemoryPhoneticIndexStore(
            entries = listOf(
                PhoneticIndexHit("কথা", 95, PhoneticIndexHit.TIER_A) to "kotha",
                PhoneticIndexHit("কাজ", 99, PhoneticIndexHit.TIER_A) to "kaj"
            )
        )
        val hits = limitStore.lookupPrefix("k", limit = 1)
        // After sort (99 first), limit 1 → only the frequency-99 word.
        assertEquals(listOf("কাজ"), hits.map { it.bengali })
    }
}
