package com.banglu.engine

import kotlin.test.Test

class MinimalDebugTest {
    @Test fun trace() {
        val e = SmartEngine(); e.initializeSync()
        // Test the SIMPLEST case that fails
        println("lu → " + e.convertWord("lu").bengali)    // Should be লু
        println("lun → " + e.convertWord("lun").bengali)  // Should be লুন  
        println("olun → " + e.convertWord("olun").bengali) // Test with o prefix
        println("un → " + e.convertWord("un").bengali)    // Should be উন
        println("ri → " + e.convertWord("ri").bengali)    // Should be রি
        println("ori → " + e.convertWord("ori").bengali)  // Test with o prefix
        println("bo → " + e.convertWord("bo").bengali)    // Should be বো
        println("abo → " + e.convertWord("abo").bengali)  // Test
        println("obo → " + e.convertWord("obo").bengali)  // Test
    }
}
