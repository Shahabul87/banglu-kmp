package com.banglu.desktop.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S77: Shift+Space commits the forming word as raw ENGLISH (no conversion) —
 * the Avro-standard escape hatch for names, emails and technical terms.
 */
class S77ShiftSpaceEnglishTest {
    private val engine = TestEngine.facade
    private fun newState() = EditorState(engine)

    private fun EditorState.type(s: String) {
        for (c in s) applyEdit(
            display.substring(0, cursor) + c + display.substring(cursor),
            cursor + 1
        )
    }

    @Test
    fun shiftSpaceKeepsTheTypedEnglish() {
        val s = newState()
        s.type("email")
        assertTrue(s.formingBangla.isNotEmpty())          // it WAS converting live
        assertTrue(s.commitFormingAsEnglish())
        assertEquals("email ", s.display)                 // raw English + space
        assertEquals("", s.formingRaw)                    // nothing forming
    }

    @Test
    fun shiftSpaceMidSentenceLeavesEarlierBanglaAlone() {
        val s = newState()
        s.type("amar ")                                    // normal commit → Bangla
        s.type("password")
        assertTrue(s.commitFormingAsEnglish())
        assertEquals("আমার password ", s.display)
        // Next word converts normally again.
        s.type("kemon ")
        assertEquals("আমার password কেমন ", s.display)
    }

    @Test
    fun shiftSpaceWithNothingFormingReturnsFalse() {
        val s = newState()
        assertFalse(s.commitFormingAsEnglish())
        s.type("kemon ")
        assertFalse(s.commitFormingAsEnglish())            // already committed
        assertEquals("কেমন ", s.display)
    }

    @Test
    fun undoRestoresTheFormingState() {
        val s = newState()
        s.type("banglu")
        assertTrue(s.commitFormingAsEnglish())
        assertEquals("banglu ", s.display)
        s.undo()
        assertEquals("", s.committed)                      // commit rolled back
    }
}
