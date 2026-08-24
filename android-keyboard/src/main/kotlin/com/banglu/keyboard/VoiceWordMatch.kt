package com.banglu.keyboard

/**
 * S133 — the recognizer-respelling equivalence test, shared by
 * [VoicePartialDiff] (live-region revisions) and [VoiceCarryPolicy] (the
 * cross-restart carry).
 *
 * Google's recognizer constantly re-emits the SAME audio with revised
 * spellings ("অনেক" → "অ১নেক", the S120 field trace). Every duplication bug
 * in the voice path's history — S56, S120, S121, and the 2026-08-23 field
 * report this round answers — reduced to some layer comparing hypotheses
 * with EXACT text equality and treating a one-character respelling as brand
 * new speech. This object owns the fuzzy answer so no layer grows its own.
 *
 * Deliberately conservative: words shorter than 3 code units must match
 * exactly (কি/না-class words are whole morphemes — a one-char edit there IS
 * a different word), and the tolerated edit distance is 1 per 4 characters
 * (minimum 1). A false "similar" only makes the newest hypothesis replace
 * instead of append — the recognizer's own latest reading of the same audio
 * — never a loss of committed text.
 */
object VoiceWordMatch {

    fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        if (shorter < 3) return false
        if (kotlin.math.abs(a.length - b.length) > 2) return false
        val limit = maxOf(1, shorter / 4)
        return editDistanceAtMost(a, b, limit)
    }

    /** Banded Levenshtein: true iff distance(a, b) <= limit. */
    private fun editDistanceAtMost(a: String, b: String, limit: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > limit) return false
        var previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val substitute = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitute)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > limit) return false
            val swap = previous
            previous = current.copyInto(swap)
        }
        return previous[b.length] <= limit
    }
}
