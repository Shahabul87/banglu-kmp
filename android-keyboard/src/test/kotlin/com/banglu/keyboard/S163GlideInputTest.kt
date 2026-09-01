package com.banglu.keyboard

import com.banglu.engine.glide.GlidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S163GlideInputTest {

    @Test
    fun tapNeverBecomesGlide() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        g.move(GlidePoint(1.2f, 1.5f))
        assertFalse(g.isGlide)
    }

    @Test
    fun longTravelOnLettersArms() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        var x = 1f
        repeat(8) { x += 0.3f; g.move(GlidePoint(x, 1.5f)) }
        assertTrue(g.isGlide)
        assertTrue(g.finish().size >= 4)
        assertFalse(g.isGlide) // finish resets
    }

    @Test
    fun nonLetterStartNeverArms() {
        val g = GlideInput()
        g.begin(false, GlidePoint(5f, 3.5f))
        var x = 5f
        repeat(12) { x += 0.4f; g.move(GlidePoint(x, 3.5f)) }
        assertFalse(g.isGlide)
    }

    @Test
    fun jitterIsDropped() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        repeat(50) { g.move(GlidePoint(1f + it % 2 * 0.01f, 1.5f)) }
        assertFalse(g.isGlide)
        assertTrue(g.finish().size <= 2)
    }

    @Test
    fun cancelResets() {
        val g = GlideInput()
        g.begin(true, GlidePoint(1f, 1.5f))
        var x = 1f
        repeat(8) { x += 0.3f; g.move(GlidePoint(x, 1.5f)) }
        g.cancel()
        assertFalse(g.isGlide)
        assertEquals(0, g.finish().size)
    }

    @Test
    fun pointCapKeepsBounded() {
        val g = GlideInput()
        g.begin(true, GlidePoint(0.5f, 0.5f))
        var x = 0.5f
        repeat(600) { x += 0.13f; g.move(GlidePoint(x % 10f, 0.5f)) }
        assertTrue(g.finish().size <= 256)
    }
}
