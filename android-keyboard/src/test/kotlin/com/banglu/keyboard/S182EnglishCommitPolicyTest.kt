package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class S182EnglishCommitPolicyTest {
    @Test
    fun theTypedWordCommitsAndTheCorrectionIsOnlyOffered() {
        assertEquals(
            EnglishCommitPolicy.Decision.KeepWithOffer("Lal", "all"),
            EnglishCommitPolicy.decide("Lal", "all", autoReplaceEnabled = false),
        )
    }

    @Test
    fun noCorrectionMeansPlainCommit() {
        assertEquals(EnglishCommitPolicy.Decision.Keep("hello"), EnglishCommitPolicy.decide("hello", null, false))
        assertEquals(EnglishCommitPolicy.Decision.Keep("hello"), EnglishCommitPolicy.decide("hello", "hello", true))
    }

    @Test
    fun theSwitchRestoresAutoReplace() {
        assertEquals(
            EnglishCommitPolicy.Decision.Replace("teh", "the"),
            EnglishCommitPolicy.decide("teh", "the", autoReplaceEnabled = true),
        )
    }
}
