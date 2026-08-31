package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S152 (tester report, 2026-08-30: "correct English keo thik banglae
 * convert korano — যেমন courier = কুরিয়ার"): the courier-class loans the
 * probe caught. Everything else in the tester's register already worked
 * (delivery, parcel, order, address, office, doctor, hospital...).
 */
class S152TesterEnglishJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun primary(k: String) = fold(engine.convertWord(k).bengali)
    private fun strip(k: String) = engine.getSuggestions(k, 6).map { fold(it.bengali) }

    @Test
    fun theTestersOwnExample() {
        assertEquals(fold("কুরিয়ার"), primary("courier"))
        assertTrue("courier" in engine.getSuggestions("courier", 6).map { it.bengali },
            "the English word keeps its S142 chip: ${strip("courier")}")
    }

    @Test
    fun cashAndBankReadTheLoanword() {
        assertEquals(fold("ক্যাশ"), primary("cash"))
        assertTrue(fold("চাষ") in strip("cash"), "চাষ stays a twin: ${strip("cash")}")
        assertEquals(fold("ব্যাংক"), primary("bank"))
        assertTrue(fold("বাঁক") in strip("bank"), "বাঁক stays a twin: ${strip("bank")}")
    }

    @Test
    fun theRegisterThatAlreadyWorkedStaysPinned() {
        assertEquals(fold("ডেলিভারি"), primary("delivery"))
        assertEquals(fold("পার্সেল"), primary("parcel"))
        assertEquals(fold("অর্ডার"), primary("order"))
        assertEquals(fold("অফিস"), primary("office"))
        assertEquals(fold("ডাক্তার"), primary("doctor"))
        assertEquals(fold("হাসপাতাল"), primary("hospital"))
        assertEquals(fold("মোবাইল"), primary("mobile"))
        assertEquals(fold("রিচার্জ"), primary("recharge"))
        assertEquals(fold("ব্যালেন্স"), primary("balance"))
    }
}
