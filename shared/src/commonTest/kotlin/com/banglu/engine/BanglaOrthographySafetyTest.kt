package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class BanglaOrthographySafetyTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun criticalBanglaClustersStayDictionaryBacked() {
        val cases = mapOf(
            "mrityu" to "মৃত্যু",
            "mrittyu" to "মৃত্যু",
            "rashtra" to "রাষ্ট্র",
            "rashtro" to "রাষ্ট্র",
            "torko" to "তর্ক",
            "shorto" to "শর্ত",
            "orjon" to "অর্জন",
            "somporko" to "সম্পর্ক",
            "bortoman" to "বর্তমান",
            "udgom" to "উদ্গম",
            "udghaton" to "উদ্ঘাটন",
            "byatha" to "ব্যথা",
            "tyag" to "ত্যাগ",
            "ottyachar" to "অত্যাচার",
            "prottokkho" to "প্রত্যক্ষ"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }
}
