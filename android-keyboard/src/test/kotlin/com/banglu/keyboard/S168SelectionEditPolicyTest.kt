package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S168 (closed-testing audit P1-1): backspace with a RANGE selection must
 * delete the selected range, never the character before it — the host's
 * deleteSurroundingText contract excludes the selection.
 */
class S168SelectionEditPolicyTest {

    @Test
    fun caretSelectionFallsThroughToNormalBackspace() {
        assertEquals(SelectionEditPolicy.BackspacePlan.DELETE_BEFORE_CURSOR, SelectionEditPolicy.backspacePlan(7, 7))
    }

    @Test
    fun rangeSelectionDeletesTheRange() {
        assertEquals(SelectionEditPolicy.BackspacePlan.DELETE_SELECTION, SelectionEditPolicy.backspacePlan(3, 7))
    }

    @Test
    fun reversedRangeStillCountsAsRange() {
        assertEquals(SelectionEditPolicy.BackspacePlan.DELETE_SELECTION, SelectionEditPolicy.backspacePlan(7, 3))
    }

    @Test
    fun unknownSelectionFallsThrough() {
        assertEquals(SelectionEditPolicy.BackspacePlan.DELETE_BEFORE_CURSOR, SelectionEditPolicy.backspacePlan(-1, -1))
        assertEquals(SelectionEditPolicy.BackspacePlan.DELETE_BEFORE_CURSOR, SelectionEditPolicy.backspacePlan(-1, 4))
    }
}
