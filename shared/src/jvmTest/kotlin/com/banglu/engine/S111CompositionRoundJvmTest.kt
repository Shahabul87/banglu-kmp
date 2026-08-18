package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/**
 * S111 pins (full store): the three composition mechanisms from the book-
 * register OOV work (S110 report §recommended, rounds 1-2):
 *  1. plural/classifier suffix composition (-দের/-গুলি/-গুলো + case forms)
 *  2. o-less stem twin (dhapo -> ধাপ, not extended-dict junk ধাপা)
 *  3. compound-split honesty: the semantic-inversion class is dead
 *     (sposhtochihnit must NEVER become স্পষ্ট অচিহ্নিত), and habit-priority
 *     owners defer the তো particle (sposhto -> স্পষ্ট, not স্পস তো).
 */
class S111CompositionRoundJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun pluralSuffixComposition() {
        assertEquals("ভ্যারাইটিদের", engine.convertWord("bhyaraitider").bengali)
        assertEquals("উদ্ভিদদের", engine.convertWord("udbhidoder").bengali)
        assertEquals("বৈশিষ্ট্যগুলি", engine.convertWord("boishishtyoguli").bengali)
        assertEquals("অঙ্গগুলি", engine.convertWord("ongooguli").bengali)
        assertEquals("ছেলেগুলি", engine.convertWord("cheleguli").bengali)
        assertEquals("মানুষদের", engine.convertWord("manushder").bengali)
    }

    @Test
    fun oLessStemTwinBeatsJunk() {
        assertEquals("ধাপগুলি", engine.convertWord("dhapoguli").bengali)
        assertEquals("গণগুলির", engine.convertWord("gonogulir").bengali)
        assertEquals("দেহ গঠনের", engine.convertWord("dehogothoner").bengali)
    }

    @Test
    fun semanticInversionClassIsDead() {
        val got = engine.convertWord("sposhtochihnit").bengali
        assertEquals("স্পষ্ট চিহ্নিত", got)
        // The guard exists for exactly this: the negative-prefix capture must
        // never invert meaning, in the primary OR anywhere on the strip.
        val strip = engine.getSuggestions("sposhtochihnit", 6).map { it.bengali }
        assertTrue(strip.none { "অচিহ্নিত" in it }, "inverted form leaked into the strip: $strip")
    }

    @Test
    fun habitOwnersDeferTheToParticle() {
        // স্পষ্ট rides "sposhto" as a final-o habit alias at @82 — composing
        // স্পস+তো over it was garbage for a plain adjective.
        assertEquals("স্পষ্ট", engine.convertWord("sposhto").bengali)
    }

    @Test
    fun chatCompoundFlagshipsStillWin() {
        // The habit-deferral is evidence-competitive (S79 law): the glued
        // formal aliases (করবনা class) must never silence the chat compounds.
        assertEquals("করবোনা", engine.convertWord("korbona").bengali)
        assertEquals("বলবোনে", engine.convertWord("bolbone").bengali)
        assertEquals("দেখিস তো", engine.convertWord("dekhisto").bengali)
        assertEquals("যাওরা", engine.convertWord("jaora").bengali)
        // And attested whole words still own their keys outright.
        assertNotEquals("বই গুলি", engine.convertWord("boiguli").bengali)
        assertEquals("বইগুলি", engine.convertWord("boiguli").bengali)
    }
}
