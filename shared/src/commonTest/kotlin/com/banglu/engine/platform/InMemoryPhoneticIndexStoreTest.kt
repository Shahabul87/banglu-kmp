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
