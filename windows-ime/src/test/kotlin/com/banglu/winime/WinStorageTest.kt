package com.banglu.winime

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class WinStorageTest {
    // createTempDir() is deprecated; kotlin.io.path.createTempDirectory keeps
    // test output free of deprecation warnings.
    private fun tempDir() = createTempDirectory(prefix = "ws").toFile()
    private fun fresh() = WinStorage(tempDir())

    @Test fun savesAndReloadsRows() = runBlocking {
        val s = fresh()
        s.saveLearnedWord("jbo", "যাবো", 1)
        val rows = s.getLearnedWords()
        assertEquals(1, rows.size)
        assertEquals("jbo", rows[0].phonetic)
        assertEquals("যাবো", rows[0].bengali)
    }

    @Test fun duplicateSaveBumpsFrequency() = runBlocking {
        val s = fresh()
        s.saveLearnedWord("jbo", "যাবো", 1)
        s.saveLearnedWord("jbo", "যাবো", 1)
        assertEquals(2, s.getLearnedWords().single().frequency)
    }

    @Test fun corruptFileDegradesToEmptyNotCrash() = runBlocking {
        val dir = tempDir()
        java.io.File(dir, "learned.json").writeText("{not json")
        assertTrue(WinStorage(dir).getLearnedWords().isEmpty())
    }
}
