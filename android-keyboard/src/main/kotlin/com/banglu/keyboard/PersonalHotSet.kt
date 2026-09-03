package com.banglu.keyboard

import kotlin.math.pow

/**
 * S172: the user's own everyday roman keys — a usage count and last-used day
 * per key, capped, decayed by recency. Pure Kotlin, no Android.
 *
 * It never decides a conversion: at startup the top keys are replayed through
 * the engine's normal `convertWord` so the engine's own memos are warm for
 * the words this person actually types. Spec:
 * docs/superpowers/specs/2026-09-02-personal-hot-set.md
 */
class PersonalHotSet(private val cap: Int) {

    private class Entry(var count: Int, var lastDay: Int)

    private val entries = HashMap<String, Entry>()

    val size: Int get() = entries.size
    fun contains(key: String) = entries.containsKey(key)
    fun count(key: String) = entries[key]?.count ?: 0

    /** Records one committed use of [key] on [day] (days since epoch). */
    fun record(key: String, day: Int) {
        if (!eligible(key)) return
        val e = entries[key]
        if (e != null) {
            e.count = (e.count + 1).coerceAtMost(1_000_000)
            if (day > e.lastDay) e.lastDay = day
        } else {
            entries[key] = Entry(1, day)
            if (entries.size > cap) evictLowest(day)
        }
    }

    /** Highest-scoring keys first. */
    fun topKeys(n: Int, today: Int): List<String> =
        entries.entries
            .sortedWith(compareByDescending<Map.Entry<String, Entry>> { score(it.value, today) }.thenBy { it.key })
            .take(n)
            .map { it.key }

    private fun score(e: Entry, today: Int): Double {
        val age = (today - e.lastDay).coerceAtLeast(0)
        return e.count * 0.5.pow(age / HALF_LIFE_DAYS)
    }

    private fun evictLowest(today: Int) {
        val victim = entries.entries.minWithOrNull(
            compareBy<Map.Entry<String, Entry>> { score(it.value, today) }.thenByDescending { it.key }
        ) ?: return
        entries.remove(victim.key)
    }

    /** `key\tcount\tlastDay` per line. */
    fun serialize(): String =
        entries.entries.joinToString("\n") { "${it.key}\t${it.value.count}\t${it.value.lastDay}" }

    companion object {
        const val HALF_LIFE_DAYS = 30.0

        fun eligible(key: String): Boolean =
            key.length in 1..24 && key.all { it in 'a'..'z' }

        fun parse(text: String, cap: Int): PersonalHotSet {
            val set = PersonalHotSet(cap)
            for (line in text.lineSequence()) {
                val p = line.split('\t')
                if (p.size != 3) continue
                val count = p[1].toIntOrNull() ?: continue
                val day = p[2].toIntOrNull() ?: continue
                if (!eligible(p[0]) || count <= 0) continue
                set.entries[p[0]] = Entry(count, day)
            }
            // Honour the cap after a parse of a larger/older file.
            while (set.entries.size > cap) set.evictLowest(set.entries.values.maxOf { it.lastDay })
            return set
        }
    }
}
