package com.banglu.keyboard

/**
 * S71 (tester screenshot 2026-08-02: "এটাকেনহচ্ছেএটাকেনবারবাররেদাতিওনহচ্ছে"):
 * pure token normalization for voice segments, extracted from
 * BangluIMEService so the whitespace law is unit-pinned (VoiceSessionPolicy /
 * VoicePartialDiff pattern).
 *
 * The old in-service code did `text.split(Regex("(\\s+)")).joinToString("")`
 * — JavaScript split semantics, where the capturing group keeps delimiters.
 * Kotlin/Java split DISCARDS them, so the moment a segment contained one
 * Latin token (the gate below), every inter-word space in the WHOLE segment
 * was eaten. Downstream, the glued text shared no word prefix with the
 * properly-spaced live text, so VoicePartialDiff classified each revision as
 * a fresh segment and appended it — the duplicated-sentence half of the same
 * tester report.
 */
object VoiceTextNormalizer {

    fun containsLatinToken(text: String): Boolean = text.any { it in 'A'..'Z' || it in 'a'..'z' }

    /**
     * Convert each Latin token of [text] via [convert], PRESERVING all
     * whitespace exactly. Non-Latin tokens pass through untouched.
     */
    fun normalizeLatinTokens(text: String, convert: (String) -> String): String {
        if (!containsLatinToken(text)) return text
        return Regex("\\S+").replace(text) { match ->
            normalizeToken(match.value, convert)
        }
    }

    private fun normalizeToken(token: String, convert: (String) -> String): String {
        val core = token.trim { !it.isLetterOrDigit() }
        if (core.isEmpty() || core.any { it in 'ঀ'..'৿' } || core.any { it.isDigit() }) return token
        if (core.none { it in 'A'..'Z' || it in 'a'..'z' }) return token
        val converted = try {
            convert(core.lowercase())
        } catch (_: Exception) {
            core
        }
        if (converted == core || converted.any { it in 'A'..'Z' || it in 'a'..'z' }) return token
        return token.replace(core, converted)
    }
}
