package com.banglu.keyboard

import com.banglu.engine.glide.GlidePath
import com.banglu.engine.glide.GlidePoint

/**
 * S163: gesture classifier + point collector for glide typing. Pure state,
 * unit-tested; the Compose observer feeds it grid-space points and asks
 * [isGlide]. A sequence only ever ARMS when it began on a letter key and
 * travelled far enough — spacebar drags (S32), long-presses (S68) and plain
 * taps can never become glides.
 *
 * Grid note: rows 1 and 3 of the real layout map exactly onto
 * GlideGrid.DEFAULT_ROW_OFFSETS; row 2's fixed 24dp indent leaves edge keys
 * ≤0.15 key off the ideal grid (0 at center) — sub-noise for the decoder,
 * revisited in the on-device tuning round.
 */
class GlideInput(
    private val minTravelKeys: Float = 1.5f,
    private val minSampleDist: Float = 0.12f,
    private val maxPoints: Int = 256,
) {
    private val points = ArrayList<GlidePoint>(64)
    private var eligible = false
    private var armed = false
    private var travel = 0f

    val isGlide: Boolean get() = armed

    fun begin(startedOnLetter: Boolean, p: GlidePoint) {
        reset()
        eligible = startedOnLetter
        if (eligible) points.add(p)
    }

    fun move(p: GlidePoint) {
        if (!eligible) return
        val last = points.lastOrNull() ?: return
        val d = GlidePath.dist(last, p)
        if (d < minSampleDist) return
        points.add(p)
        travel += d
        if (!armed && travel >= minTravelKeys && points.size >= 4) armed = true
        if (points.size > maxPoints) {
            // Halve by dropping every other OLD point; endpoints kept.
            val kept = ArrayList<GlidePoint>(points.size / 2 + 2)
            for (i in points.indices) {
                if (i == 0 || i == points.size - 1 || i % 2 == 0) kept.add(points[i])
            }
            points.clear()
            points.addAll(kept)
        }
    }

    /** Returns the kept path and resets. Empty when never armed/cancelled. */
    fun finish(): List<GlidePoint> {
        val out = if (armed) ArrayList(points) else emptyList<GlidePoint>()
        reset()
        return out
    }

    fun cancel() = reset()

    private fun reset() {
        points.clear()
        eligible = false
        armed = false
        travel = 0f
    }
}
