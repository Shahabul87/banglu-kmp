package com.banglu.keyboard

import com.banglu.engine.types.SmartSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S168 (audit P0-1 residual): the suggestion strip keys LazyRow items by
 * bengali|source|tier; Compose throws (and the IME process dies) on a
 * duplicate. The strip must never hand LazyRow two equal keys, whatever a
 * producer sends.
 */
class S168StripKeyPolicyTest {

    private fun s(b: String, src: String = "engine", tier: String = "0") =
        SmartSuggestion(b, 0.9, src, "x", tier)

    @Test
    fun keyIsBengaliSourceTier() {
        assertEquals("কেমন|engine|0", StripKeyPolicy.key(s("কেমন")))
    }

    @Test
    fun duplicateKeysCollapseKeepingFirst() {
        val list = listOf(s("বিয়ে", "glide_alt", "glide_alt"), s("বিয়ে", "glide_alt", "glide_alt"), s("বিয়া", "glide_alt", "glide_alt"))
        val out = StripKeyPolicy.uniqueByKey(list)
        assertEquals(listOf("বিয়ে", "বিয়া"), out.map { it.bengali })
        assertEquals(out.size, out.map { StripKeyPolicy.key(it) }.toSet().size)
    }

    @Test
    fun differentTiersOfSameWordAreDistinct() {
        val list = listOf(s("কেমন", "engine", "0"), s("কেমন", "typed_roman", "typed_roman"))
        assertEquals(2, StripKeyPolicy.uniqueByKey(list).size)
    }
}
