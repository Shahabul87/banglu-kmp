package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S84 (tester 2026-08-13, রেফ note: "type করতাম, পারতাম — engine return
 * কর্তাম, পার্তাম"): the -ta/-to/-tam habitual-past morphology had NO
 * inherent-o-drop keys (করতাম only under "korotam"), so kortam free-fell to
 * sadhu/fuzzy garbage (করিতাম, কোর্ট, বলয়টা, মর্টার). Fixed by the compiler
 * verb_o_drop_t habit rule (db 3.8.11); composing parity follows by
 * construction (S83 shared arbitration).
 */
class S84VerbInflectionTJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun tamClassResolvesTheModernVerb() {
        assertEquals("করতাম", engine.convertWord("kortam").bengali)
        assertEquals("পারতাম", engine.convertWord("partam").bengali)
        assertEquals("বলতাম", engine.convertWord("boltam").bengali)
        assertEquals("মরতাম", engine.convertWord("mortam").bengali)
        assertEquals("ধরতাম", engine.convertWord("dhortam").bengali)
    }

    @Test
    fun toClassOutranksLoanwordSquatters() {
        // কোর্ট@79 owned "korto" outright before the rule; করত@83 must win.
        assertEquals("করত", engine.convertWord("korto").bengali)
    }

    @Test
    fun tamClassComposingMatchesCommit() {
        // The old composing previews were the ref-forms (কর্তাম, পার্তাম,
        // বল্তাম) — the tester's exact screenshots.
        for (key in listOf("kortam", "partam", "boltam", "mortam", "korto")) {
            assertEquals(
                fold(engine.convertWord(key).bengali),
                fold(engine.convertForComposing(key).bengali),
                "preview must equal commit for '$key'"
            )
        }
    }

    @Test
    fun kortaKeepsItsCanonicalOwner() {
        // কর্তা (the noun) canonically owns "korta" — the habit alias করতা
        // must not displace it (ownership law).
        assertEquals("কর্তা", engine.convertWord("korta").bengali)
    }
}
