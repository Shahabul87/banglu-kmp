package com.banglu.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S168 (audit P2-7): IME_FLAG_NO_PERSONALIZED_LEARNING and URI fields are
 * LEARNING flags, not display flags — Chrome's address bar and incognito
 * keep the chips, glide and voice; only learning is off. Passwords, OTP,
 * email and raw-commit fields stay fully private (no chips, no learning).
 */
class S168InputPrivacyPolicyTest {

    @Test
    fun normalFieldShowsChipsAndLearns() {
        val p = InputPrivacyPolicy.resolve(sensitive = false, noPersonalizedLearning = false, uri = false)
        assertEquals(InputPrivacyPolicy.Mode(showSuggestions = true, learn = true), p)
    }

    @Test
    fun uriFieldShowsChipsButNeverLearns() {
        val p = InputPrivacyPolicy.resolve(sensitive = false, noPersonalizedLearning = false, uri = true)
        assertEquals(InputPrivacyPolicy.Mode(showSuggestions = true, learn = false), p)
    }

    @Test
    fun incognitoShowsChipsButNeverLearns() {
        val p = InputPrivacyPolicy.resolve(sensitive = false, noPersonalizedLearning = true, uri = false)
        assertEquals(InputPrivacyPolicy.Mode(showSuggestions = true, learn = false), p)
    }

    @Test
    fun sensitiveFieldHidesChipsAndNeverLearns() {
        val p = InputPrivacyPolicy.resolve(sensitive = true, noPersonalizedLearning = false, uri = false)
        assertEquals(InputPrivacyPolicy.Mode(showSuggestions = false, learn = false), p)
    }
}
