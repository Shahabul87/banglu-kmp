package com.banglu.engine.glide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlideGridTest {
    private val grid = GlideGrid()

    @Test
    fun qRowCenters() {
        assertEquals(GlidePoint(0.5f, 0.5f), grid.center('q'))
        assertEquals(GlidePoint(9.5f, 0.5f), grid.center('p'))
    }

    @Test
    fun homeAndBottomRowUseOffsets() {
        assertEquals(GlidePoint(1.0f, 1.5f), grid.center('a'))
        assertEquals(GlidePoint(9.0f, 1.5f), grid.center('l'))
        assertEquals(GlidePoint(2.0f, 2.5f), grid.center('z'))
        assertEquals(GlidePoint(8.0f, 2.5f), grid.center('m'))
    }

    @Test
    fun nonLettersHaveNoCenter() {
        assertNull(grid.center('1'))
        assertNull(grid.center('ঁ'))
    }

    @Test
    fun uppercaseFolds() {
        assertEquals(grid.center('k'), grid.center('K'))
    }
}
