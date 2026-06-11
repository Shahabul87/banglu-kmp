package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishLexiconBuilderTest {

    @Test
    fun buildsEntriesFromCmudictLines() {
        val entries = EnglishLexiconBuilder.build(
            cmudictLines = listOf(
                "bus B AH1 S",
                "bus(2) B AH0 S",          // alternate pronunciations are skipped
                "time T AY1 M",
                "rare'word R EH1 R"        // non a-z keys are skipped
            ),
            topWords = setOf("bus", "time")
        )
        assertEquals(setOf("bus", "time"), entries.map { it.key }.toSet())
        assertEquals("বাস", entries.first { it.key == "bus" }.bengali)
        assertEquals("টাইম", entries.first { it.key == "time" }.bengali)
    }

    @Test
    fun skipsWordsOutsideTopList() {
        val entries = EnglishLexiconBuilder.build(
            cmudictLines = listOf("zyzzyva Z IH0 Z IH1 V AH0"),
            topWords = setOf("bus")
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun countsUnconvertibleWords() {
        EnglishLexiconBuilder.build(
            cmudictLines = listOf("weird W IH1 R D", "broken B R OH9 K"), // OH9 = invalid token
            topWords = setOf("weird", "broken")
        )
        assertEquals(1, EnglishLexiconBuilder.lastSkippedUnconvertible)
    }

    @Test
    fun parsesTopWordsFromFrequencyLines() {
        // limit=2 over 3 eligible words discriminates limit-then-filter vs filter-then-limit:
        //   "the" → accepted (1st), "don't" → rejected (apostrophe), "of" → accepted (2nd = limit)
        // Under limit-then-filter (wrong): take 2 = {the, don't}, filter = {the} — fails.
        // Under filter-then-limit (correct): filter = {the, of}, take 2 = {the, of} — passes.
        val top = EnglishLexiconBuilder.parseTopWords(
            lines = listOf("the 100", "don't 90", "of 80"),
            limit = 2
        )
        assertEquals(setOf("the", "of"), top)
    }

    @Test
    fun deduplicatesBaseEntriesFirstPronunciationWins() {
        // Two CMUdict base lines for the same word — first pronunciation must win.
        val entries = EnglishLexiconBuilder.build(
            cmudictLines = listOf(
                "bus B AH1 S",   // primary → বাস
                "bus B AH0 S"    // duplicate base entry (not an alternate like "bus(2)") → must be skipped
            ),
            topWords = setOf("bus")
        )
        assertEquals(1, entries.size)
        assertEquals("বাস", entries[0].bengali)
    }
}
