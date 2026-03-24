package com.banglu.engine

import kotlin.test.Test

class MoyTest {
    @Test fun testMoy() {
        val e = SmartEngine(); e.initializeSync()
        val words = listOf("moy", "mo", "oy", "joy", "boy", "hoy", "koy", "noy", "roy", "doy", "soy", "toy")
        for (w in words) println("$w → ${e.convertWord(w).bengali}")
    }
}
