package com.banglu.engine.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPhoneticIndexStoreTest {

    private val store = InMemoryPhoneticIndexStore(
        entries = listOf(
            PhoneticIndexHit("আমি", 100, 0) to "ami",
            PhoneticIndexHit("আমিষ", 10, 1) to "amish",
            PhoneticIndexHit("কথা", 95, 0) to "kotha"
        ),
        english = mapOf("scooter" to "স্কুটার")
    )

    @Test
    fun exactLookupReturnsBothTiersSortedByFrequency() {
        val hits = store.lookupExact("ami")
        assertEquals(listOf("আমি"), hits.map { it.bengali })
    }

    @Test
    fun prefixLookupIsTierAOnly() {
        val hits = store.lookupPrefix("am", limit = 10)
        assertTrue(hits.all { it.tier == 0 })
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
}
