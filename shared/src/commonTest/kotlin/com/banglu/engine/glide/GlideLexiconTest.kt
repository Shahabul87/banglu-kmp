package com.banglu.engine.glide

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlideLexiconTest {
    private val grid = GlideGrid()
    private val words = listOf("ami" to 90, "kmon" to 80, "kemon" to 70, "x1bad" to 60, "a" to 50)

    @Test
    fun buildSkipsIneligibleWords() {
        val lex = GlideLexicon.build(words, grid)
        assertEquals(3, lex.size) // x1bad (digit) and "a" (len<2) dropped
        assertEquals("ami", lex.word(0))
        assertEquals(80, lex.freq(1))
    }

    @Test
    fun templateEndpointsMatchLetterCenters() {
        val lex = GlideLexicon.build(words, grid)
        val s = lex.start(1) // kmon starts at k
        val k = grid.center('k')!!
        assertTrue(abs(s.x - k.x) < 0.06f && abs(s.y - k.y) < 0.06f)
        val e = lex.end(2) // kemon ends at n
        val n = grid.center('n')!!
        assertTrue(abs(e.x - n.x) < 0.06f && abs(e.y - n.y) < 0.06f)
    }

    @Test
    fun lengthIsTemplateArc() {
        // Templates are smoothed at build (corner rounding shortens the arc
        // a little) — length only needs to be in the raw polyline's ballpark
        // for the prune window to work.
        val lex = GlideLexicon.build(words, grid)
        val tpl = "ami".mapNotNull { grid.center(it) }
        assertTrue(abs(lex.length(0) - GlidePath.arcLength(tpl)) < 0.5f)
    }

    @Test
    fun serializeRoundTripsAndChecksVersion() {
        val lex = GlideLexicon.build(words, grid)
        val bytes = GlideLexicon.serialize(lex, "3.9.7")
        val back = GlideLexicon.deserialize(bytes, "3.9.7")!!
        assertEquals(lex.size, back.size)
        assertEquals("kemon", back.word(2))
        assertEquals(70, back.freq(2))
        val a = FloatArray(GlideLexicon.N_POINTS * 2)
        val b = FloatArray(GlideLexicon.N_POINTS * 2)
        lex.template(2, a)
        back.template(2, b)
        for (i in a.indices) assertTrue(abs(a[i] - b[i]) < 1e-4f)
        assertNull(GlideLexicon.deserialize(bytes, "3.9.8"))
        assertNull(GlideLexicon.deserialize(byteArrayOf(1, 2, 3), "3.9.7"))
    }
}
