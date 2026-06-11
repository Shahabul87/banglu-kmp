package com.banglu.engine

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ImeCommitPipelineTest {

    @AfterTest
    fun tearDown() {
        SmartEngineAdapter.reset()
    }

    @Test
    fun commitsCommonSentenceThroughKeyboardBufferFlow() {
        SmartEngineAdapter.initializeSync()

        assertEquals("আমি ভালো আছি", commitLikeIme("ami bhalo achi"))
    }

    @Test
    fun commitsAmbiguousWordsWithDefaultRanking() {
        SmartEngineAdapter.initializeSync()

        assertEquals("টাকা দরজা", commitLikeIme("taka dorja"))
    }

    @Test
    fun commitsLowercaseOnlyAuditRegressionsThroughKeyboardBufferFlow() {
        SmartEngineAdapter.initializeSync()

        assertEquals("আগে ছিনা নাসা ইউনেস্কো", commitLikeIme("age cina nasa unesco"))
    }

    @Test
    fun explicitCustomConversionWinsInKeyboardCommitFlow() {
        SmartEngineAdapter.initializeSync()
        SmartEngineAdapter.addCustomConversion("ottadhunik", "অত্যাধুনিক")

        assertEquals("অত্যাধুনিক যুগ", commitLikeIme("ottadhunik jug"))
    }

    private fun commitLikeIme(input: String): String {
        val output = StringBuilder()
        val context = mutableListOf<String>()
        var buffer = StringBuilder()

        fun commitBuffer() {
            if (buffer.isEmpty()) return
            val result = SmartEngineAdapter.convertWordWithContext(buffer.toString(), context)
            output.append(result.bengali)
            context.add(result.bengali)
            buffer = StringBuilder()
        }

        for (ch in input) {
            if (ch.isWhitespace()) {
                commitBuffer()
                output.append(ch)
            } else {
                buffer.append(ch)
            }
        }
        commitBuffer()

        return output.toString()
    }
}
