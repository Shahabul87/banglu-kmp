package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S168 (audit P2-2): key labels are sized like Gboard's — by the keyboard's
 * own font-size setting, NOT the system font scale (which clipped "EN"→"E"
 * and stacked the number-row hints over the digits at 1.3x). The helper
 * returns the sp value that, once the system multiplies it by fontScale,
 * renders at exactly the keyboard's intended size.
 */
class S168KeyLabelScaleTest {

    @Test
    fun unitySystemScaleIsIdentity() {
        assertEquals(18f, KeyLabelScale.systemIndependentSp(18f, systemFontScale = 1.0f), 0.0001f)
    }

    @Test
    fun largeSystemScaleIsCancelledOut() {
        assertEquals(18f / 1.3f, KeyLabelScale.systemIndependentSp(18f, systemFontScale = 1.3f), 0.0001f)
    }

    @Test
    fun degenerateScaleFallsBackToIdentity() {
        assertEquals(18f, KeyLabelScale.systemIndependentSp(18f, systemFontScale = 0f), 0.0001f)
    }
}
