package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * S141 (field report 2026-08-29, generalised by the user: "the engine must
 * not ignore what I typed — at least show it in the suggestions"): an
 * out-of-vocabulary word whose deterministic reading is clean Bengali keeps
 * the commit; dictionary neighbours become chips. Open-syllable keys offer
 * their vowel twin in the slot after the primary (kri -> ক্রি / কৃ,
 * ku -> কু / কূ). Real store on ./dictionary.sqlite.
 */
class S141TypedFaithfulJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun strip(key: String, n: Int = 6) = engine.getSuggestions(key, n).map { fold(it.bengali) }

    @Test
    fun aTypedVowelIsNeverSwappedForACorpusNeighbour() {
        // The field case: one substitution away from বাংলা (and from বাংরু@62).
        val r = engine.convertWord("banglu")
        assertEquals(fold("বাংলু"), fold(r.bengali))
        assertNotEquals(ResolutionSource.DICTIONARY, r.source)
        // ...but the neighbour stays one tap away.
        assertTrue(fold("বাংলা") in strip("banglu"), "বাংলা must ride the strip: ${strip("banglu")}")
    }

    @Test
    fun recoveryIsASpellingNormaliserNotAWordChooser() {
        // Layer 6 recovery used to hand the inflected forms to বাংলাকে/বাংটুর.
        assertEquals(fold("বাংলুকে"), fold(engine.convertWord("bangluke").bengali))
        assertEquals(fold("বাংলুতে"), fold(engine.convertWord("banglute").bengali))
        assertEquals(fold("বাংলুর"), fold(engine.convertWord("banglur").bengali))
        assertTrue(fold("বাংলাকে") in strip("bangluke"), "the recovered word must ride the strip")
    }

    @Test
    fun fingerSlipRepairsAreUntouched() {
        // Every S22 shape still corrects: transposition, insertion, deletion.
        assertEquals("বাংলা", engine.convertWord("banlag").bengali)
        assertEquals("কেমন", engine.convertWord("kmon").bengali)
        assertEquals("আমাদের", engine.convertWord("amdaer").bengali)
        assertEquals("বুঝতেছিনা", engine.convertWord("bujjtecina").bengali)
        // A substitution still repairs a reading that is NOT clean Bengali
        // (থগিএছ: independent এ after a vowel sign) — the S87/S100 pin.
        assertEquals(fold("ঠকিয়েছ"), fold(engine.convertWord("thogieco").bengali))
    }

    @Test
    fun theTypedReadingHoldsTheLastSlotWhenTheEngineReplacedIt() {
        // Spelling-normalising recovery (র -> ড়) keeps the commit; the reading
        // the user watched forming stays one tap away in the last slot.
        val s = strip("bariwalader")
        assertEquals(fold("বাড়িওয়ালাদের"), s.first(), "commit contract unchanged")
        assertEquals(fold("বারিওয়ালাদের"), s.last(), "typed reading in the last slot: $s")
        // Confident owners are not "ignoring" the key: no literal chip.
        assertTrue(fold("ভালবাশি") !in strip("valobashi"), strip("valobashi").toString())
        assertTrue(fold("ক্মন") !in strip("kmon"), strip("kmon").toString())
    }

    @Test
    fun aHabitAliasCannotSmuggleInAVowelSubstitution() {
        // banglte -> বাংলোতে is a chat alias; deleting the typed u to reach it
        // is o-for-u in disguise.
        assertEquals(fold("বাংলুতে"), fold(engine.convertWord("banglute").bengali))
        assertEquals(fold("বাংলোতে"), fold(engine.convertWord("banglote").bengali), "the alias itself still owns its key")
    }

    @Test
    fun openSyllableKeysOfferTheirVowelTwinRightAfterThePrimary() {
        assertEquals(listOf(fold("ক্রি"), fold("কৃ")), strip("kri").take(2))
        assertEquals(listOf(fold("কু"), fold("কূ")), strip("ku").take(2))
        assertEquals(listOf(fold("প্রি"), fold("পৃ")), strip("pri").take(2))
        assertEquals(listOf(fold("মৃ"), fold("ম্রি")), strip("mri").take(2))
        assertEquals(listOf(fold("কি"), fold("কী")), strip("ki").take(2))
        // Closed syllables keep the ordinary gates — the real twin still shows.
        assertTrue(fold("কূল") in strip("kul"))
    }

    @Test
    fun anUnownedKeyOffersTheWordsOwningItsRomanPrefix() {
        // User: "type banglish — bangladesh must at least be in the suggestions".
        val s = strip("banglish")
        assertTrue(fold("বাংলাদেশ") in s, "prefix completion missing: $s")
        assertEquals(fold(engine.convertWord("banglish").bengali), s.first(), "commit contract unchanged")
    }

    @Test
    fun letterKeysOfferTheirWholeLetterClassAfterThePrimary() {
        // User: "sh should give তালব্য শ and মূর্ধন্য ষ, it only gives শ".
        assertEquals(listOf("শ", "ষ", "স"), strip("sh").take(3))
        assertEquals(listOf("ত", "ট"), strip("t").take(2))
        assertEquals(listOf("জ", "য"), strip("j").take(2))
        assertEquals(listOf("চ", "ছ"), strip("ch").take(2))
        assertEquals(listOf("ন", "ণ"), strip("n").take(2))
        assertEquals("থ", strip("th")[1])
        // A letter key still commits its primary.
        assertEquals("শ", engine.convertWord("sh").bengali)
    }

    @Test
    fun storeOwnedWordsAreUnaffected() {
        assertEquals("কলু", engine.convertWord("kolu").bengali)
        assertEquals(fold("বাংলা"), fold(engine.convertWord("bangla").bengali))
        assertEquals(fold("বাংলো"), fold(engine.convertWord("banglo").bengali))
    }
}
