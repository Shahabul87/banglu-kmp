package com.banglu.engine

import com.banglu.engine.util.BloomFilter
import kotlin.test.Test
import kotlin.test.assertTrue

/** S144: the negative index must never lie about a present key, and must
 *  reject the overwhelming majority of absent ones. */
class S144BloomFilterTest {
    private fun word(i: Int): String = "k" + i.toString(36) + (i % 7).toString()

    @Test
    fun noFalseNegativesAndFewFalsePositives() {
        val bloom = BloomFilter(1 shl 22, 4)
        val present = (0 until 100_000).map { word(it) }
        present.forEach { bloom.add(it) }
        assertTrue(present.all { bloom.mightContain(it) }, "a present key was denied")
        val absent = (0 until 100_000).map { "z" + it.toString(36) + (it % 7).toString() }
        val fp = absent.count { bloom.mightContain(it) }
        assertTrue(fp < absent.size / 50, "false-positive rate too high: $fp / ${absent.size}")
    }
}
