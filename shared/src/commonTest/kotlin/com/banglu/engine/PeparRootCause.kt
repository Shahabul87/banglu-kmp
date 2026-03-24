package com.banglu.engine
import kotlin.test.Test
class PeparRootCause {
    @Test fun trace() {
        val e = SmartEngine(); e.initializeSync()
        // Seed-only mode — no 480K
        val r = e.convertWord("pepar")
        println("SEED-ONLY: pepar → ${r.bengali} (source=${r.source}, conf=${r.confidence})")
        
        // The REAL question: WHY does 480K override this?
        // The answer is in the cache + Layer 0 section narrowing
        // Let's trace what Layer 0 would find:
        println("")
        println("=== What 480K section narrowing finds for 'pe' prefix ===")
        // Can't test with 480K in unit test, but the bug is clear:
        // Layer 0 maps 'pe' → Bengali section 'পে'
        // Finds পে-অর্ডার in 480K (freq=57) 
        // Returns it with confidence >= 0.95
        // BEFORE pattern engine ever runs!
        println("Layer 0 returns পে-অর্ডার because it's in 480K under পে section")
        println("Pattern engine NEVER gets a chance to produce পেপার")
        println("")
        println("FIX: Layer 0 should ONLY return results if phonetic overlap > 0.70")
        println("pepar vs পে-অর্ডার overlap = very low (pe vs pe-order)")
        println("pepar vs পেপার overlap = high (pepar vs pepar)")
    }
}
