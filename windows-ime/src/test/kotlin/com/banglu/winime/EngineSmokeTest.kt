package com.banglu.winime

import com.banglu.engine.SmartEngineAdapter
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineSmokeTest {
    @Test
    fun fullEngineConvertsCoreWords() {
        TestEngine.boot()
        assertEquals("আমি", SmartEngineAdapter.convertWord("ami").bengali)
        assertEquals("কেমন", SmartEngineAdapter.convertWord("kmon").bengali)
        assertEquals("ইচ্ছা", SmartEngineAdapter.convertWord("issa").bengali)
    }
}
