package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S168 (audit P2-5): the cursor arrows step by USER-VISIBLE cluster, never
 * by code point (which parked the caret between ক and ি), and work from a
 * small window around the caret instead of the whole extracted document.
 */
class S168CursorStepPolicyTest {

    @Test
    fun leftStepsOverAWholeKarCluster() {
        assertEquals(2, CursorStepPolicy.leftStep("আমি কি"))
    }

    @Test
    fun rightStepsOverAWholeKarCluster() {
        assertEquals(2, CursorStepPolicy.rightStep("কিছু"))
    }

    @Test
    fun rightStepsOverAConjunctAsOneUnit() {
        assertEquals(3, CursorStepPolicy.rightStep("ক্ষমা"))
    }

    @Test
    fun asciiStepsOneChar() {
        assertEquals(1, CursorStepPolicy.leftStep("abc"))
        assertEquals(1, CursorStepPolicy.rightStep("abc"))
    }

    @Test
    fun edgesStepZero() {
        assertEquals(0, CursorStepPolicy.leftStep(""))
        assertEquals(0, CursorStepPolicy.rightStep(""))
    }
}
