package com.banglu.keyboard

/**
 * S168 (audit P2-7): what a field's flags mean for the strip and for learning.
 *
 * - sensitive (password, OTP, email, raw-commit classes): no chips, no glide,
 *   no voice, no learning — unchanged from S56/S98.
 * - IME_FLAG_NO_PERSONALIZED_LEARNING (incognito) and URI fields (browser
 *   address bar): chips, glide and voice stay ON; only learning is off.
 *   The flag's contract is about learning, and the address bar is where
 *   Bengali users search the most.
 */
object InputPrivacyPolicy {
    data class Mode(val showSuggestions: Boolean, val learn: Boolean)

    fun resolve(sensitive: Boolean, noPersonalizedLearning: Boolean, uri: Boolean): Mode =
        if (sensitive) Mode(showSuggestions = false, learn = false)
        else Mode(showSuggestions = true, learn = !(noPersonalizedLearning || uri))
}
