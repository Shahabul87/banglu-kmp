package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S171 (low-RAM validation follow-up): the BN lexicon cache must be keyed by
 * its cap, otherwise a lite-profile store loads a full 50K cache file built
 * earlier (seen on the 2 GB emulator: 4.0 MB glide_bn.bin in lite mode).
 */
class S171GlideLexiconCacheNameTest {
    @Test
    fun liteAndFullCachesAreDifferentFiles() {
        assertNotEquals(GlideLexiconStore.banglaCacheName(liteMode = true), GlideLexiconStore.banglaCacheName(liteMode = false))
    }

    @Test
    fun cacheNameCarriesTheCap() {
        assertEquals("glide_bn_${GlideLexiconStore.LITE_BN_CAP}.bin", GlideLexiconStore.banglaCacheName(liteMode = true))
        assertEquals("glide_bn_${GlideLexiconStore.BN_CAP}.bin", GlideLexiconStore.banglaCacheName(liteMode = false))
    }
}
