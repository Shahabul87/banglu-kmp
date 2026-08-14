package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S104 (tester WhatsApp: kkorba -> করব): the dialect second-person -ba/-bi
 * verb forms (করবা "tumi korba?", করবি, পারবা, বলবা) had NO typed keys —
 * the romanizer's inherent o ("korobaa") was only dropped for obo/obe
 * (S79). verb_o_drop_b now covers the suffix-anchored oba/obi family;
 * অবিশ্বাস্য-class অবি- prefixes are untouched (the anchor exists because
 * a bare replace evicted their keys past the per-word alias cap).
 */
class S104DialectVerbJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun dialectBaBiFormsResolve() {
        assertEquals("করবা", engine.convertWord("korba").bengali)
        assertEquals("করবি", engine.convertWord("korbi").bengali)
        assertEquals("পারবা", engine.convertWord("parba").bengali)
        assertEquals("বলবা", engine.convertWord("bolba").bengali)
        assertEquals("করবার", engine.convertWord("korbar").bengali)
    }

    @Test
    fun obiPrefixNounsKeepTheirKeys() {
        assertEquals("অবিশ্বাস্য", engine.convertWord("obisasso").bengali)
        assertEquals("অবাক", engine.convertWord("obak").bengali)
    }
}
