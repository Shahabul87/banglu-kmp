package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S181 (Nazrul recording, 2026-09-04): the wrapper's typo correction must not
 * move AWAY from what was typed; the rule layer must write ঞ before চ ছ জ ঝ;
 * the তো particle must not split an attested word typed with a final
 * inherent o; "betha" is how people write ব্যথা.
 */
class S181LiteraryFaithfulJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)
    private fun commit(k: String) = fold(engine.convertWord(k).bengali)

    @Test
    fun typoCorrectionNeverReadsTheKeyWorseThanThePipelineResult() {
        // pipeline: সান্ত্রীরা@0.96 ("santrira", one letter from the key);
        // the old correction jumped to ছাত্রীরা ("chatrira", two letters away).
        assertEquals(fold("সান্ত্রীরা"), commit("shantrira"))
        // pipeline: ফেনাইয়া reads "phenaiya" exactly; the old correction shortened it to ফেনায়.
        assertEquals(fold("ফেনাইয়া"), commit("phenaiya"))
        assertEquals(fold("ঘষিয়াছে"), commit("ghoshiyachhe"))   // literal stays, ঘোষিয়াছে rides the strip
        assertTrue(fold("ঘোষিয়াছে") in engine.getSuggestions("ghoshiyachhe", 6).map { fold(it.bengali) })
    }

    @Test
    fun exactReadingProtectionIsBounded() {
        // the typed emphatic particle is kept (was dropped as a "typo")
        assertEquals("হচ্ছেও", commit("hochcheo"))
        assertEquals("ঘটেছেই", commit("ghotechhei"))
        // a single-slip correction to a real word still beats a junk exact reading
        assertEquals("মতামত", commit("motamoto"))
        assertEquals(fold("বিশ্ববিদ্যালয়"), fold(engine.convertWord("bishwabiddaloy").bengali))
    }

    @Test
    fun realTyposAreStillRepaired() {
        // The S22/S87 finger-slip shapes the correction exists for.
        assertEquals("আমাদের", commit("amdaer"))
        assertEquals("বাংলাদেশ", commit("bangaldesh"))
        assertEquals("কেমন", commit("kmon"))
    }

    @Test
    fun palatalNasalBeforeChJ() {
        assertEquals("সঞ্চিত", commit("shonchito"))
        assertEquals("সঞ্চিত", commit("sonchito"))
        assertEquals("অঞ্চল", commit("onchol"))
        assertEquals("ইঞ্জিন", commit("injin"))
        assertEquals("ঞ্চ", engine.convertForInstantPreview("nch").drop(0).let { if (it.contains("ঞ্চ")) "ঞ্চ" else it })
    }

    @Test
    fun attestedWordWithFinalInherentOBeatsTheToParticle() {
        assertEquals("পুঞ্জিত", commit("punjito"))
        assertEquals("গণিত", commit("gonito"))
        assertEquals("হবে তো", commit("hobeto"))      // the particle still composes where no word owns the key
        assertEquals("দেখিস তো", commit("dekhisto"))
    }

    @Test
    fun bethaIsByatha() {
        assertEquals("ব্যথা", commit("betha"))
        assertEquals("ব্যথা", commit("bytha"))
    }
}
