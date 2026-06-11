package com.banglu.compiler

import kotlin.test.Test
import kotlin.test.assertEquals

class ArpabetToBengaliTest {

    private fun conv(pron: String): String =
        ArpabetToBengali.convert(pron.split(" "))

    @Test
    fun simpleWords() {
        // cat: K AE1 T
        assertEquals("ক্যাট", conv("K AE1 T"))
        // bus: B AH1 S
        assertEquals("বাস", conv("B AH1 S"))
        // computer: K AH0 M P Y UW1 T ER0
        assertEquals("কম্পিউটার", conv("K AH0 M P Y UW1 T ER0"))
    }

    @Test
    fun glidesAndDiphthongs() {
        // time: T AY1 M
        assertEquals("টাইম", conv("T AY1 M"))
        // go: G OW1
        assertEquals("গো", conv("G OW1"))
        // house: HH AW1 S
        assertEquals("হাউস", conv("HH AW1 S"))
    }

    @Test
    fun unstressedSchwaMidWordIsInherent() {
        // doctor: D AA1 K T ER0
        assertEquals("ডাক্টার", conv("D AA1 K T ER0"))
    }

    @Test
    fun positionalGlides() {
        // yes: Y EH1 S -> word-initial Y -> ইয়
        assertEquals("ইয়েস", conv("Y EH1 S"))
        // water: W AO1 T ER0 -> word-initial W -> ওয়
        assertEquals("ওয়াটার", conv("W AO1 T ER0"))
        // quick: K W IH1 K -> W after consonant -> ু
        assertEquals("কুইক", conv("K W IH1 K"))
    }
}
