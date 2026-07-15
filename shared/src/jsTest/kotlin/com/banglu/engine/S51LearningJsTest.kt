package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** S51: the macOS IME loads learned.json through these exports. */
class S51LearningJsTest {
    @Test
    fun appliedLearnedWordsWinTheirKey() {
        BangluWebEngine.initSeed()
        // plausible pair (passes isPlausibleDynamicMapping): reverse(যাবো) ≈ "jbo"
        // probed rule output before learning: জ্বো
        BangluWebEngine.applyLearnedWords(
            """[{"p":"jbo","b":"যাবো","f":94,"t":1752537600000}]"""
        )
        assertEquals("যাবো", BangluWebEngine.convert("jbo"))
    }

    @Test
    fun recordPickTeachesTheKey() {
        BangluWebEngine.initSeed()
        // plausible pair (passes isPlausibleDynamicMapping): reverse(খাবো) ≈ "khbo"
        // probed rule output before learning: খবো
        BangluWebEngine.recordPick("khbo", "খাবো")
        assertEquals("খাবো", BangluWebEngine.convert("khbo"))
    }

    @Test
    fun malformedLearnedJsonIsIgnored() {
        BangluWebEngine.initSeed()
        BangluWebEngine.applyLearnedWords("{not json")   // must not throw
        BangluWebEngine.applyLearnedWords("""[{"only":"junk"}]""")
    }
}
