package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S172: per-user hot set of committed roman keys — counts, recency decay, cap, round-trip. */
class S172PersonalHotSetTest {

    @Test
    fun recordsCountsAndOrdersByUsage() {
        val h = PersonalHotSet(cap = 10)
        repeat(3) { h.record("ami", day = 100) }
        h.record("tumi", day = 100)
        assertEquals(listOf("ami", "tumi"), h.topKeys(10, today = 100))
        assertEquals(3, h.count("ami"))
    }

    @Test
    fun recencyBeatsStaleCounts() {
        val h = PersonalHotSet(cap = 10)
        repeat(3) { h.record("purono", day = 10) }      // 3 uses, 90 days ago
        h.record("notun", day = 100)                      // 1 use, today
        assertEquals("notun", h.topKeys(2, today = 100).first())
    }

    @Test
    fun capEvictsTheLowestScore() {
        val h = PersonalHotSet(cap = 3)
        h.record("a", day = 1); h.record("b", day = 50); h.record("c", day = 90)
        h.record("d", day = 100)
        assertEquals(3, h.size)
        assertFalse(h.contains("a"))
        assertTrue(h.contains("d"))
    }

    @Test
    fun ignoresNonRomanKeys() {
        val h = PersonalHotSet(cap = 10)
        h.record("কি", day = 1); h.record("a b", day = 1); h.record("", day = 1); h.record("ok", day = 1)
        assertEquals(listOf("ok"), h.topKeys(10, today = 1))
    }

    @Test
    fun serializesAndParsesLosslessly() {
        val h = PersonalHotSet(cap = 10)
        repeat(2) { h.record("kemon", day = 42) }; h.record("acho", day = 43)
        val text = h.serialize()
        val back = PersonalHotSet.parse(text, cap = 10)
        assertEquals(h.topKeys(10, today = 43), back.topKeys(10, today = 43))
        assertEquals(2, back.count("kemon"))
        assertEquals(PersonalHotSet(cap = 10).topKeys(5, today = 1), PersonalHotSet.parse("garbage\twithout\tnumbers", cap = 10).topKeys(5, today = 1))
    }
}
