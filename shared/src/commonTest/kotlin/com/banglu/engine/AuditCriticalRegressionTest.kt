package com.banglu.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class AuditCriticalRegressionTest {
    private val engine = SmartEngine().also { it.initializeSync() }

    @Test
    fun samsungAuditCriticalWordsStayCorrect() {
        val cases = mapOf(
            "taka" to "টাকা",
            "boro" to "বড়",
            "ghora" to "ঘোড়া",
            "gari" to "গাড়ি",
            "kosto" to "কষ্ট",
            "rahman" to "রহমান",
            "hothat" to "হঠাৎ",
            "dukkho" to "দুঃখ",
            "uddog" to "উদ্যোগ",
            "biggan" to "বিজ্ঞান",
            "bhodro" to "ভদ্র",
            "protha" to "প্রথা",
            "jonmodin" to "জন্মদিন",
            "byatha" to "ব্যথা"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }

    @Test
    fun natvaDoesNotCorruptCommonNames() {
        val cases = mapOf(
            "rahman" to "রহমান",
            "rohman" to "রহমান",
            "karon" to "কারণ"
        )

        for ((input, expected) in cases) {
            assertEquals(expected, engine.convertWord(input).bengali, input)
        }
    }
}
