package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConjunctResolverTest {

    @Test
    fun testKkh() {
        assertEquals("\u0995\u09CD\u09B7", ConjunctResolver.resolve("kkh")) // ক্ষ
    }

    @Test
    fun testSht() {
        assertEquals("\u09B7\u09CD\u099F", ConjunctResolver.resolve("sht")) // ষ্ট
    }

    @Test
    fun testStr() {
        assertEquals("\u09B8\u09CD\u09A4\u09CD\u09B0", ConjunctResolver.resolve("str")) // স্ত্র
    }

    @Test
    fun testShr() {
        assertEquals("\u09B6\u09CD\u09B0", ConjunctResolver.resolve("shr")) // শ্র
    }

    @Test
    fun testChhNotInConjunctMap() {
        // "chh" is NOT in the ConjunctResolver's CONJUNCT_MAP, it's in ConjunctTable
        assertNull(ConjunctResolver.resolve("chh"))
    }

    @Test
    fun testNoMatch() {
        assertNull(ConjunctResolver.resolve("xyz"))
    }

    @Test
    fun testMatchAt() {
        val match = ConjunctResolver.matchAt("shthane", 0)
        assertNotNull(match)
        assertEquals("\u09B7\u09CD\u09A0", match.bengali) // ষ্ঠ
        assertEquals(4, match.consumed)
    }

    @Test
    fun testResolveConjuncts() {
        val segments = ConjunctResolver.resolveConjuncts("kkhalok")
        assertTrue(segments.isNotEmpty())
        assertEquals("\u0995\u09CD\u09B7", segments[0].text) // ক্ষ
        assertTrue(segments[0].isConjunct)
    }

    @Test
    fun testFindAll() {
        val matches = ConjunctResolver.findAll("kkhsht")
        assertEquals(2, matches.size)
        assertEquals("\u0995\u09CD\u09B7", matches[0].bengali) // ক্ষ
        assertEquals("\u09B7\u09CD\u099F", matches[1].bengali) // ষ্ট
    }

    @Test
    fun testIsConjunct() {
        assertTrue(ConjunctResolver.isConjunct("kkh"))
        assertTrue(ConjunctResolver.isConjunct("sht"))
        assertTrue(!ConjunctResolver.isConjunct("xyz"))
    }

    @Test
    fun testResolveConjunctsCaseInsensitive() {
        // ConjunctResolver should handle case-insensitively
        val result = ConjunctResolver.resolve("KKH")
        assertEquals("\u0995\u09CD\u09B7", result) // ক্ষ
    }
}
