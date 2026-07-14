package com.banglu.engine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S43 tester round: parbi, vdo, rain, -নে softener class. */
class S43TesterRoundJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test fun parbiIsModernTuiForm() {
        assertEquals("পারবি", engine.convertWord("parbi").bengali)
        assertEquals("পারবো", engine.convertWord("parbo").bengali)
    }

    @Test fun vdoShorthand() {
        assertEquals("ভিডিও", engine.convertWord("vdo").bengali)
        assertEquals("ভিডিও", engine.convertWord("video").bengali)
    }

    @Test fun rainIsLoanword() {
        assertEquals("রেইন", engine.convertWord("rain").bengali)
        assertTrue(engine.getSuggestions("rain", 5).any { it.bengali == "রাইন" })
    }

    @Test fun neSoftenerClass() {
        assertEquals("বলবোনে", engine.convertWord("bolbone").bengali)
        assertEquals("আসবোনে", engine.convertWord("asbone").bengali)
        assertEquals("খাবোনে", engine.convertWord("khabone").bengali)
        // Attested -ne words must stay untouched.
        assertEquals("মনে", engine.convertWord("mone").bengali)
        assertEquals("এখানে", engine.convertWord("ekhane").bengali)
        assertEquals("দোকানে", engine.convertWord("dokane").bengali)
        assertEquals("কেমনে", engine.convertWord("kemne").bengali)
    }
}
