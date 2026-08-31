package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S157 pin wall: every word the tutorial letter-cards advertise — every
 * listed spelling variant — must behave EXACTLY as displayed on the real
 * dictionary. Plain words: variant converts top-1. Twin words (pore-class):
 * the declared primary wins AND the card's word is reachable on the 6-slot
 * strip. Splits must concatenate to their roman (display honesty).
 */
class S157TutorialWordsJvmTest {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun everyTutorialVariantBehavesAsDisplayed() {
        val failures = buildList {
            for (w in TutorialWords.ALL_WORDS) {
                if (w.split.joinToString("") != w.roman)
                    add("split ${w.split} != roman ${w.roman}")
                for (v in listOf(w.roman) + w.alts) {
                    val primary = fold(engine.convertWord(v).bengali)
                    if (w.twinPrimary.isEmpty()) {
                        if (primary != fold(w.bengali))
                            add("$v -> $primary (wanted ${w.bengali})")
                    } else {
                        val strip = engine.getSuggestions(v, 6).map { fold(it.bengali) }
                        if (primary != fold(w.twinPrimary))
                            add("twin $v -> $primary (declared primary ${w.twinPrimary})")
                        if (fold(w.bengali) !in strip)
                            add("twin $v: ${w.bengali} not in strip $strip")
                    }
                }
            }
        }
        assertEquals(emptyList(), failures, "tutorial cards must never advertise a miss")
    }

    @Test
    fun everyCapHasWords() {
        for (f in TutorialWords.FAMILIES) for (c in f.caps) {
            check(c.words.isNotEmpty()) { "empty cap ${c.cap} in ${f.title}" }
        }
    }
}
