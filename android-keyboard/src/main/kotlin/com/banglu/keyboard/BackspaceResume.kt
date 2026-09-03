package com.banglu.keyboard

/**
 * S88: resume-composition plan for a backspace that arrives with an EMPTY
 * roman buffer (the word under the cursor was already committed).
 *
 * Tester report (2026-08-13): type abaro -> আবারো committed; backspace the
 * trailing vowel; retype o -> the editor showed আবার + a fresh one-letter
 * composition ও = আবারও. The engine never saw the key "abaro" again, so the
 * same visible word came out different depending on edit history — a WYSIWYG
 * break for the whole vowel-ending class (ো/ও, সব vowel per the note).
 *
 * The plan: delete the committed trailing Bengali word, re-enter composing on
 * its remaining fragment with the fragment's ROMAN key as the buffer, so the
 * next letters re-convert the whole word exactly as if it were being typed
 * fresh. Gated on an exact rule-layer round-trip (reverse -> instant preview
 * must reproduce the fragment) so the backspace itself never changes what is
 * on screen; words that don't round-trip fall back to plain grapheme
 * deletion — the previous behavior, never worse.
 */
internal data class BackspaceResumePlan(
    /** UTF-16 length of the committed trailing word to delete. */
    val deleteLength: Int,
    /** The roman buffer to resume composing with. */
    val romanBuffer: String,
    /** The Bengali fragment the composing region shows (== instant preview). */
    val visibleFragment: String,
)

/**
 * S174: an edit with the caret INSIDE a Bengali word. Both sides of the word
 * are removed and the whole word is re-composed from
 * `romanPrefix + <edit> + romanSuffix`; the caret ends after the word.
 */
internal data class MidWordEditPlan(
    val deleteBefore: Int,
    val deleteAfter: Int,
    val romanPrefix: String,
    val romanSuffix: String,
)

internal object BackspaceResume {

    private const val MAX_WORD_LENGTH = 24

    private fun wordBefore(text: String): String {
        var start = text.length
        while (start > 0 && isBengaliWordChar(text[start - 1])) start--
        return text.substring(start)
    }

    private fun wordAfter(text: String): String {
        var end = 0
        while (end < text.length && isBengaliWordChar(text[end])) end++
        return text.substring(0, end)
    }

    /** The round-trip gate every resume shares: reverse → instant preview must echo the part. */
    private fun roundTrip(part: String, reverse: (String) -> String, instantPreview: (String) -> String): String? {
        if (part.isEmpty()) return ""
        val roman = runCatching { reverse(part).lowercase() }.getOrNull() ?: return null
        if (roman.isEmpty() || roman.length > MAX_WORD_LENGTH || !roman.all { it in 'a'..'z' }) return null
        val echo = runCatching { instantPreview(roman) }.getOrNull() ?: return null
        return if (echo == part) roman else null
    }

    /**
     * S174 (user: "type a letter wrong in the middle and try to fix that"):
     * a LETTER typed with Bengali word text on BOTH sides of the caret. The
     * one-sided S109 resume re-composed only the prefix and left the suffix
     * behind (স্বাধ|নতা + i → "স্বাধি" + "নতা", then a space inside the
     * word on commit). Null when the caret is not inside a word or either
     * side fails the round-trip gate — the caller keeps the old behaviour.
     */
    fun planForMidWordEdit(
        textBeforeCursor: String,
        textAfterCursor: String,
        reverse: (String) -> String,
        instantPreview: (String) -> String,
    ): MidWordEditPlan? {
        val suffix = wordAfter(textAfterCursor)
        if (suffix.isEmpty()) return null
        val prefix = wordBefore(textBeforeCursor)
        if (prefix.length + suffix.length > MAX_WORD_LENGTH) return null
        val romanPrefix = roundTrip(prefix, reverse, instantPreview) ?: return null
        val romanSuffix = roundTrip(suffix, reverse, instantPreview) ?: return null
        return MidWordEditPlan(prefix.length, suffix.length, romanPrefix, romanSuffix)
    }

    /**
     * S174: BACKSPACE with Bengali word text on both sides of the caret —
     * drop the last user-visible cluster before the caret and re-compose the
     * whole word.
     */
    fun planForMidWordBackspace(
        textBeforeCursor: String,
        textAfterCursor: String,
        reverse: (String) -> String,
        instantPreview: (String) -> String,
    ): MidWordEditPlan? {
        val suffix = wordAfter(textAfterCursor)
        if (suffix.isEmpty()) return null
        val prefix = wordBefore(textBeforeCursor)
        if (prefix.isEmpty() || prefix.length + suffix.length > MAX_WORD_LENGTH) return null
        val fragment = prefix.substring(0, previousUserVisibleClusterBoundary(prefix))
        val romanPrefix = roundTrip(fragment, reverse, instantPreview) ?: return null
        val romanSuffix = roundTrip(suffix, reverse, instantPreview) ?: return null
        return MidWordEditPlan(prefix.length, suffix.length, romanPrefix, romanSuffix)
    }

    /**
     * @param textBeforeCursor text directly before the cursor (no composing).
     * @param reverse Bengali -> roman key (ReverseTransliterator.reverseWord).
     * @param instantPreview the SAME sync rule-only function the IME uses to
     *   echo the buffer — the round-trip gate must match what would be shown.
     */
    fun plan(
        textBeforeCursor: String,
        reverse: (String) -> String,
        instantPreview: (String) -> String,
    ): BackspaceResumePlan? {
        if (textBeforeCursor.isEmpty()) return null
        var start = textBeforeCursor.length
        while (start > 0 && isBengaliWordChar(textBeforeCursor[start - 1])) start--
        val word = textBeforeCursor.substring(start)
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return null
        val boundary = previousUserVisibleClusterBoundary(word)
        // Single remaining cluster: deleting it empties the word — plain
        // deletion is already correct there.
        if (boundary <= 0) return null
        val fragment = word.substring(0, boundary)
        val roman = runCatching { reverse(fragment).lowercase() }.getOrNull() ?: return null
        if (roman.isEmpty() || roman.length > MAX_WORD_LENGTH) return null
        if (!roman.all { it in 'a'..'z' }) return null
        val echo = runCatching { instantPreview(roman) }.getOrNull() ?: return null
        if (echo != fragment) return null
        return BackspaceResumePlan(word.length, roman, fragment)
    }

    /**
     * S109: resume plan for a LETTER typed with an empty buffer while the
     * cursor sits directly after Bengali word text \u2014 the user is editing
     * that word (tap-in mid-word to add a kar, or typing right after a
     * committed word with no space). A fresh composition there turned the
     * vowel into a full letter (\u09A6|\u09AC\u09BE + 'a' showed \u09A6\u0986\u09AC\u09BE instead of \u09A6\u09BE\u09AC\u09BE \u2014
     * tester report 2026-08-17). Same round-trip gate as [plan]: the whole
     * prefix before the cursor must reproduce exactly through
     * reverse -> instant preview, else return null and the caller keeps the
     * old fresh-composition behavior \u2014 never worse.
     */
    fun planForTyping(
        textBeforeCursor: String,
        reverse: (String) -> String,
        instantPreview: (String) -> String,
    ): BackspaceResumePlan? {
        if (textBeforeCursor.isEmpty()) return null
        if (!isBengaliWordChar(textBeforeCursor.last())) return null
        var start = textBeforeCursor.length
        while (start > 0 && isBengaliWordChar(textBeforeCursor[start - 1])) start--
        val word = textBeforeCursor.substring(start)
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return null
        val roman = runCatching { reverse(word).lowercase() }.getOrNull() ?: return null
        if (roman.isEmpty() || roman.length > MAX_WORD_LENGTH) return null
        if (!roman.all { it in 'a'..'z' }) return null
        val echo = runCatching { instantPreview(roman) }.getOrNull() ?: return null
        if (echo != word) return null
        return BackspaceResumePlan(word.length, roman, word)
    }

    internal fun isBengaliWordChar(ch: Char): Boolean =
        ch in '\u0980'..'\u09FF' || ch == '\u200C' || ch == '\u200D'

    /**
     * Start index of the last user-visible grapheme cluster (keeps Bengali
     * kar/virama/joiner clusters and emoji modifiers intact). Moved verbatim
     * from BangluIMEService (S88) so the service and the resume plan share
     * ONE boundary definition.
     */
    /**
     * S136 (F-013): platform grapheme segmenter for NON-Bengali clusters
     * (emoji ZWJ families, flags, keycaps, combining Latin). The IME installs
     * ICU's character BreakIterator; tests inject a fake. Bengali clusters
     * never go through it — the rules below are the product's own (per-
     * conjunct deletion, S88 pins).
     */
    @Volatile
    var nonBengaliClusterBreaker: ((text: String, fromIndex: Int) -> Int)? = null

    private fun isBengaliCodePoint(cp: Int): Boolean = cp in 0x0980..0x09FF

    fun previousUserVisibleClusterBoundary(text: String, fromIndex: Int = text.length): Int {
        if (text.isEmpty() || fromIndex <= 0) return 0

        var index = fromIndex.coerceAtMost(text.length)
        index = Character.offsetByCodePoints(text, index, -1)

        val breaker = nonBengaliClusterBreaker
        if (breaker != null) {
            // Decide by the LAST base-ish code point: a Bengali cluster keeps
            // the Bengali rules; anything else (emoji, Latin) is ICU's call.
            var probe = index
            while (probe > 0 && (text.codePointAt(probe) == 0x200D || text.codePointAt(probe) == 0xFE0F ||
                    text.codePointAt(probe) in 0x1F3FB..0x1F3FF)) {
                probe = Character.offsetByCodePoints(text, probe, -1)
            }
            if (!isBengaliCodePoint(text.codePointAt(probe))) {
                val boundary = breaker(text, fromIndex.coerceAtMost(text.length))
                if (boundary in 0 until fromIndex) return boundary
            }
        }

        while (index > 0) {
            val cp = text.codePointAt(index)
            if (!isTrailingClusterCodePoint(cp)) break
            index = Character.offsetByCodePoints(text, index, -1)
        }

        var start = index
        while (start > 0) {
            val prev = Character.codePointBefore(text, start)
            val current = text.codePointAt(start)
            val prevIsVirama = prev == 0x09CD
            val currentIsJoiner = current == 0x200D || current == 0x200C
            if (!prevIsVirama && !currentIsJoiner) break
            start = Character.offsetByCodePoints(text, start, -1)
            while (start > 0 && isTrailingClusterCodePoint(text.codePointAt(start))) {
                start = Character.offsetByCodePoints(text, start, -1)
            }
        }

        return start.coerceAtLeast(0)
    }

    private fun isTrailingClusterCodePoint(cp: Int): Boolean {
        return cp == 0x09BC || // nukta
            cp == 0x09CD || // virama
            cp == 0x200D ||
            cp == 0x200C ||
            cp == 0xFE0F ||
            cp in 0x0981..0x0983 ||
            cp in 0x09BE..0x09C4 ||
            cp in 0x09C7..0x09C8 ||
            cp in 0x09CB..0x09CC ||
            cp in 0x1F3FB..0x1F3FF
    }
}
