package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S105 (tester WhatsApp: jaora -> জায়রা junk): attached রা / রে join the
 * S16/S25 particle machinery — dialect plural imperatives (jaora = যাওরা,
 * khaora = খাওরা), regular plurals the corpus lacks glued (manushra =
 * মানুষরা), and the vocative/objective রে (jaore = যাওরে, bondhure =
 * বন্ধুরে). Guards: canonical whole-key owners always defer (তারা, ধরা,
 * করে, ঘুরে, পরে, আমরা); ra/re stems need ≥25 usage evidence (তম-class
 * squatter stems); tomra is the enumerated pronoun shorthand.
 */
class S105AttachedRaReJvmTest {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun attachedRaComposesDialectAndPluralForms() {
        assertEquals("যাওরা", engine.convertWord("jaora").bengali, "the tester's headline")
        assertEquals("খাওরা", engine.convertWord("khaora").bengali)
        assertEquals("মানুষরা", engine.convertWord("manushra").bengali)
    }

    @Test
    fun attachedReComposesVocatives() {
        assertEquals("যাওরে", engine.convertWord("jaore").bengali)
        assertEquals("বন্ধুরে", engine.convertWord("bondhure").bengali)
    }

    @Test
    fun canonicalOwnersOfRaReKeysNeverStolen() {
        assertEquals("তারা", engine.convertWord("tara").bengali)
        assertEquals("ধরা", engine.convertWord("dhora").bengali)
        assertEquals("আমরা", engine.convertWord("amra").bengali)
        assertEquals("করে", engine.convertWord("kore").bengali)
        assertEquals("ঘুরে", engine.convertWord("ghure").bengali)
        assertEquals("পরে", engine.convertWord("pore").bengali)
        assertEquals("ছেলেরা", engine.convertWord("chelera").bengali)
    }

    @Test
    fun tomraIsThePronounNotAComposition() {
        assertEquals("তোমরা", engine.convertWord("tomra").bengali)
    }
}
