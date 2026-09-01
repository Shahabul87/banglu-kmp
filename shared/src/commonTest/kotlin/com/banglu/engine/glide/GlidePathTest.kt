package com.banglu.engine.glide

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlidePathTest {
    private fun p(x: Float, y: Float) = GlidePoint(x, y)

    @Test
    fun arcLengthOfUnitSquarePath() {
        val path = listOf(p(0f, 0f), p(1f, 0f), p(1f, 1f))
        assertEquals(2f, GlidePath.arcLength(path), 1e-4f)
    }

    @Test
    fun resampleIsEvenAndKeepsEndpoints() {
        val path = listOf(p(0f, 0f), p(4f, 0f))
        val r = GlidePath.resample(path, 5)
        assertEquals(5, r.size)
        assertEquals(0f, r.first().x, 1e-4f)
        assertEquals(4f, r.last().x, 1e-4f)
        assertEquals(1f, r[1].x, 1e-2f)
    }

    @Test
    fun singlePointResamples() {
        val r = GlidePath.resample(listOf(p(2f, 2f)), 4)
        assertEquals(4, r.size)
        assertTrue(r.all { abs(it.x - 2f) < 1e-4f })
    }

    @Test
    fun straightLineHasNoCorners() {
        val path = listOf(p(0f, 0f), p(5f, 0f))
        assertTrue(GlidePath.corners(path).isEmpty())
    }

    @Test
    fun rightAngleHasOneCornerNearItsFraction() {
        val path = listOf(p(0f, 0f), p(2f, 0f), p(2f, 2f))
        val c = GlidePath.corners(path)
        assertEquals(1, c.size)
        assertTrue(abs(c[0] - 0.5f) < 0.1f)
    }
}
