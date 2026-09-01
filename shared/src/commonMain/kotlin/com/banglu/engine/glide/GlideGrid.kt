package com.banglu.engine.glide

/** A position in key-grid units (one key width = 1.0, one row height = 1.0). */
data class GlidePoint(val x: Float, val y: Float)

/**
 * S163: the canonical letter grid glide geometry lives in. Templates are
 * built in this space and the Android layer maps finger pixels into it
 * (x / keyWidthPx, y / rowHeightPx from the letter-rows origin), so the
 * grid is orientation- and density-independent.
 *
 * Row x-offsets are the ONE place layout geometry is declared; if the real
 * keyboard rows ever change their indents, fix DEFAULT_ROW_OFFSETS here and
 * rebuild the cached lexicons (they are version-stamped).
 */
class GlideGrid(private val rowOffsets: FloatArray = DEFAULT_ROW_OFFSETS) {
    private val centers = HashMap<Char, GlidePoint>().apply {
        ROWS.forEachIndexed { r, row ->
            row.forEachIndexed { i, c ->
                put(c, GlidePoint(rowOffsets[r] + i + 0.5f, r + 0.5f))
            }
        }
    }

    fun center(c: Char): GlidePoint? = centers[c.lowercaseChar()]

    companion object {
        val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val DEFAULT_ROW_OFFSETS = floatArrayOf(0f, 0.5f, 1.5f)
    }
}
