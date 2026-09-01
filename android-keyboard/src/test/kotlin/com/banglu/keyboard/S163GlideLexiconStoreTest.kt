package com.banglu.keyboard

import com.banglu.engine.glide.GlideEnglishWords
import com.banglu.engine.glide.GlideGrid
import com.banglu.engine.glide.GlideLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S163GlideLexiconStoreTest {

    @Test
    fun selectTopFiltersAndCaps() {
        val input = listOf("hello" to 9, "a" to 8, "don't" to 7, "world" to 6, "x2" to 5, "there" to 4)
        val out = GlideLexiconStore.selectTop(input, 2)
        assertEquals(listOf("hello" to 9, "world" to 6), out)
    }

    @Test
    fun englishWordlistBuildsARealLexicon() {
        // The full EN path minus file I/O: shared wordlist -> filter -> build.
        val words = GlideLexiconStore.selectTop(GlideEnglishWords.top(2000), 2000)
        assertTrue(words.size > 1500)
        val lex = GlideLexicon.build(words, GlideGrid())
        assertTrue(lex.size > 1500)
        // Rank order preserved as pseudo-frequency (prior for the decoder).
        assertTrue(lex.freq(0) > lex.freq(lex.size - 1))
    }
}
