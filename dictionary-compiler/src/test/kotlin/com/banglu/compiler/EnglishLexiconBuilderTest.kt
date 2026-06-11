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
        val top = EnglishLexiconBuilder.parseTopWords(
            lines = listOf("the 23135851162", "of 13151942776", "a 1041743", "x 999", "don't 88"),
            limit = 3
        )
        // "a" fails length>=2, "x" fails length>=2, "don't" fails a-z regex; limit applies to ACCEPTED words
        // Accepted: {the, of} (2 words < limit of 3)
        assertEquals(setOf("the", "of"), top)
    }
}
