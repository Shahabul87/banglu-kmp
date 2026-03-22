package com.banglu.engine.rules

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class NatvaVidhanTest {

    @Test
    fun testAfterRi() {
        // কৃ — ৃ is a trigger
        assertTrue(NatvaVidhan.shouldBeRetroflex("\u0995\u09C3")) // কৃ
    }

    @Test
    fun testAfterRa() {
        // পর — র is a trigger
        assertTrue(NatvaVidhan.shouldBeRetroflex("\u09AA\u09B0")) // পর
    }

    @Test
    fun testAfterSha() {
        // বিষ — ষ is a trigger
        assertTrue(NatvaVidhan.shouldBeRetroflex("\u09AC\u09BF\u09B7")) // বিষ
    }

    @Test
    fun testBlockedByDental() {
        // ত — a blocker
        assertFalse(NatvaVidhan.shouldBeRetroflex("\u09A4")) // ত
    }

    @Test
    fun testBlockedBySa() {
        // স — a blocker
        assertFalse(NatvaVidhan.shouldBeRetroflex("\u09B8")) // স
    }

    @Test
    fun testEmptyContext() {
        assertFalse(NatvaVidhan.shouldBeRetroflex(""))
    }

    @Test
    fun testTransparentConsonant() {
        // কৃপ — ৃ is trigger, প is transparent, so ণ should apply
        assertTrue(NatvaVidhan.shouldBeRetroflex("\u0995\u09C3\u09AA")) // কৃপ
    }

    @Test
    fun testResolveRetroflex() {
        val result = NatvaVidhan.resolve("\u0995\u09C3") // কৃ
        assertEquals('\u09A3', result.bengali) // ণ
        assertEquals(0.80, result.confidence)
    }

    @Test
    fun testResolveDental() {
        val result = NatvaVidhan.resolve("\u09A4") // ত
        assertEquals('\u09A8', result.bengali) // ন
        assertEquals(0.85, result.confidence)
    }
}
