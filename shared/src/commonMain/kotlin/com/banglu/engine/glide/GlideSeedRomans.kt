package com.banglu.engine.glide

import com.banglu.engine.SmartEngine

/**
 * S163b: the chat-register spellings users ACTUALLY glide (kmon, valo,
 * tmi…) are engine seed shorthands — they have no phonetic_index rows, so
 * a store-built lexicon misses them entirely (found on device: gliding
 * kmon offered kloj/klok, valo offered gali). They join the glide lexicon
 * at everyday-band pseudo-frequency.
 */
object GlideSeedRomans {
    fun entries(): List<Pair<String, Int>> =
        SmartEngine.mobileShorthandEntries.keys
            .filter { it.length >= 2 && it.all { c -> c in 'a'..'z' } }
            .map { it to SEED_FREQ }

    /** High everyday band — these ARE the words people type most. */
    const val SEED_FREQ = 95

    /**
     * S163b: the v-for-bh chat spelling (valo, vai, vul…) is produced by the
     * TRANSLITERATION RULES when typed — it has no index rows at all, so no
     * key list can contain it. Expand bh-words into their v-twins for glide
     * reachability only; the engine converts the v-spelling itself.
     */
    fun expandVariants(entries: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val out = LinkedHashMap<String, Int>(entries.size * 2)
        for ((w, f) in entries) out[w] = maxOf(out[w] ?: 0, f)
        for ((w, f) in entries) {
            if ("bh" in w) {
                val v = w.replace("bh", "v")
                out[v] = maxOf(out[v] ?: 0, f)
            }
        }
        return out.map { it.key to it.value }
    }
}
