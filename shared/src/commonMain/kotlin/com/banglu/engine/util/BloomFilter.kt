package com.banglu.engine.util

/**
 * S144: a plain Bloom filter over strings — the in-memory "this key does not
 * exist" answer the sqlite-backed stores use so the typo/lattice layers'
 * edit-variant probes (hundreds per keystroke) never reach the disk. No
 * false negatives by construction; a rare false positive only costs the one
 * sqlite query the store would have made anyway.
 *
 * Kirsch–Mitzenmacher double hashing over two independent 32-bit string
 * hashes (FNV-1a and a multiplicative mix of the JVM-style hashCode).
 */
class BloomFilter(bitCount: Int, private val hashes: Int) {
    private val bits = bitCount.coerceAtLeast(64)
    private val words = LongArray((bits + 63) ushr 6)
    var count: Int = 0
        private set

    fun add(key: String) {
        val h1 = fnv1a(key); val h2 = mix(key.hashCode()) or 1
        for (i in 0 until hashes) {
            val idx = index(h1, h2, i)
            words[idx ushr 6] = words[idx ushr 6] or (1L shl (idx and 63))
        }
        count++
    }

    fun mightContain(key: String): Boolean {
        val h1 = fnv1a(key); val h2 = mix(key.hashCode()) or 1
        for (i in 0 until hashes) {
            val idx = index(h1, h2, i)
            if (words[idx ushr 6] and (1L shl (idx and 63)) == 0L) return false
        }
        return true
    }

    private fun index(h1: Int, h2: Int, i: Int): Int {
        val h = (h1.toLong() + i.toLong() * h2.toLong()) and 0x7FFFFFFFL
        return (h % bits).toInt()
    }

    private fun fnv1a(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (ch in s) { h = h xor ch.code; h *= 0x01000193 }
        return h
    }

    private fun mix(x: Int): Int {
        var h = x * -0x61c88647   // 0x9E3779B9
        h = h xor (h ushr 16)
        return h
    }
}
