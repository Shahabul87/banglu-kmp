package com.banglu.engine

import kotlin.test.Test

class CharDebugTest {
    @Test fun charByChar() {
        val e = SmartEngine(); e.initializeSync()
        val result = e.convertWord("cholun").bengali
        println("cholun → $result")
        println("chars: " + result.map { "${it}(U+${it.code.toString(16).uppercase()})" }.joinToString(" "))
        println("length: ${result.length}")
        
        // Also check web-expected output
        val expected = "চলুন"
        println("expected: $expected")
        println("expected chars: " + expected.map { "${it}(U+${it.code.toString(16).uppercase()})" }.joinToString(" "))
    }
}
