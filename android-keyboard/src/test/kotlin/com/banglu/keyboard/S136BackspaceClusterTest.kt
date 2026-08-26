package com.banglu.keyboard

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S136 (F-013): non-Bengali clusters are delegated to the platform grapheme
 * segmenter (ICU on device; a fake here); Bengali clusters keep the
 * product's own rules (S88 pins), breaker or not.
 */
class S136BackspaceClusterTest {

    @AfterTest
    fun tearDown() {
        BackspaceResume.nonBengaliClusterBreaker = null
    }

    @Test
    fun emojiFamilyIsDeletedAsOneClusterThroughTheBreaker() {
        val family = "👨‍👩‍👧" // 👨‍👩‍👧
        val text = "hi $family"
        var calls = 0
        BackspaceResume.nonBengaliClusterBreaker = { t, from ->
            calls++
            // ICU-like answer: the family starts right after "hi ".
            if (t.endsWith(family) && from == t.length) t.length - family.length else from - 1
        }
        assertEquals(3, BackspaceResume.previousUserVisibleClusterBoundary(text))
        assertEquals(1, calls)
    }

    @Test
    fun bengaliClusterNeverConsultsTheBreaker() {
        var calls = 0
        BackspaceResume.nonBengaliClusterBreaker = { _, from -> calls++; from - 1 }
        // ক + ্ + ষ : the conjunct is ONE cluster under the product rules.
        assertEquals(0, BackspaceResume.previousUserVisibleClusterBoundary("ক্ষ"))
        // kar stays attached (S88).
        assertEquals(1, BackspaceResume.previousUserVisibleClusterBoundary("আমি")) // "মি" is one cluster
        assertEquals(0, calls)
    }

    @Test
    fun withoutABreakerTheLegacyPathStillHandlesSkinTones() {
        val thumbs = "👍🏽" // 👍🏽
        assertEquals(0, BackspaceResume.previousUserVisibleClusterBoundary(thumbs))
    }
}
