package com.banglu.keyboard

import com.banglu.engine.types.SmartSuggestion

/**
 * S168 (audit P0-1 residual): LazyRow keys for the suggestion strip.
 * Compose throws on a duplicate key — and in an IME that is a process death
 * (seen on device 2026-08-31: "বিয়ে|glide_alt|glide_alt" from two romans of
 * one word). Producers dedupe where they can; this is the last line.
 */
object StripKeyPolicy {
    fun key(s: SmartSuggestion): String = "${s.bengali}|${s.source}|${s.tier}"

    /** First occurrence wins; order otherwise preserved. */
    fun uniqueByKey(list: List<SmartSuggestion>): List<SmartSuggestion> {
        if (list.size < 2) return list
        val seen = HashSet<String>(list.size * 2)
        val out = ArrayList<SmartSuggestion>(list.size)
        for (s in list) if (seen.add(key(s))) out.add(s)
        return out
    }
}
