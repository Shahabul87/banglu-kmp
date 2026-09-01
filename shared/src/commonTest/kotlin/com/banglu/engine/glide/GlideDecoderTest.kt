package com.banglu.engine.glide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlideDecoderTest {
    private val grid = GlideGrid()
    private val lex = GlideLexicon.build(
        listOf(
            "ami" to 95, "kmon" to 90, "kemon" to 85, "bangla" to 80,
            "kothay" to 70, "valo" to 60, "krishno" to 40
        ),
        grid
    )
    private val decoder = GlideDecoder(lex)

    private fun cleanPath(word: String): List<GlidePoint> =
        GlidePath.resample(word.mapNotNull { grid.center(it) }, 24)

    @Test
    fun cleanPathsDecodeTopOne() {
        for (w in listOf("ami", "kemon", "bangla", "kothay")) {
            val out = decoder.decode(cleanPath(w))
            assertEquals(w, out.first().word, "expected $w, got ${out.map { it.word }}")
        }
    }

    @Test
    fun noisyPathStillTopSix() {
        val path = cleanPath("kmon").mapIndexed { i, p ->
            GlidePoint(
                p.x + if (i % 2 == 0) 0.3f else -0.25f,
                p.y + if (i % 3 == 0) 0.3f else -0.2f
            )
        }
        val out = decoder.decode(path)
        assertTrue(out.take(6).any { it.word == "kmon" }, "got ${out.map { it.word }}")
    }

    @Test
    fun shortOrDegenerateGesturesReturnNothing() {
        assertTrue(decoder.decode(emptyList()).isEmpty())
        assertTrue(decoder.decode(listOf(GlidePoint(1f, 1f))).isEmpty())
        assertTrue(decoder.decode(List(10) { GlidePoint(1f, 1.5f) }).isEmpty())
    }

    @Test
    fun garbagePathCommitsNothing() {
        val path = listOf(GlidePoint(0.2f, 0.2f), GlidePoint(0.4f, 2.8f), GlidePoint(0.2f, 0.4f))
        assertTrue(decoder.decode(path).isEmpty())
    }
}
