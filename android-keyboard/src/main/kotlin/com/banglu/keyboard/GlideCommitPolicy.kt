package com.banglu.keyboard

import com.banglu.engine.types.SmartSuggestion

enum class GlideMode { BANGLA, ENGLISH }

/**
 * S163: what a finished glide erases and commits, and how an alternate chip
 * swaps the word afterwards. Pure decisions — the service owns the
 * InputConnection.
 *
 * BANGLA: the armed glide's first press went into the COMPOSING buffer, so
 * committing through the composition replaces it — nothing to erase.
 * ENGLISH: letters commit raw into the editor, so the chars typed since the
 * gesture's first press must be deleted before the word lands.
 */
object GlideCommitPolicy {

    const val GLIDE_ALT_SOURCE = "glide_alt"
    const val GLIDE_ALT_TIER = "glide_alt"

    data class GlideCommitPlan(val eraseEditorChars: Int, val commitText: String)

    fun planCommit(
        mode: GlideMode,
        editorCharsFromFirstKey: Int,
        word: String,
    ): GlideCommitPlan = GlideCommitPlan(
        eraseEditorChars = if (mode == GlideMode.ENGLISH) editorCharsFromFirstKey else 0,
        commitText = "$word "
    )

    /** Alternate chip: bengali = display/commit text, phonetic = the roman. */
    fun altChip(roman: String, bengali: String): SmartSuggestion =
        SmartSuggestion(bengali, 0.8, GLIDE_ALT_SOURCE, roman, GLIDE_ALT_TIER)

    /** Delete the committed word + its auto space, commit replacement + space. */
    fun swapLengths(justCommitted: String, replacement: String): Pair<Int, String> =
        justCommitted.length + 1 to "$replacement "
}
