package com.banglu.keyboard

/**
 * S170/S180: where a fast-committed preview (S32: the space landed before the
 * authoritative conversion) may be replaced once that conversion arrives.
 *
 * The committed word may by now be followed by a short TAIL — the space the
 * commit appended, a tight punctuation the user typed (S70), or the দাঁড়ি
 * model's "। " from a double space (S180: the Facebook demo recording showed
 * বুজতেপার্ছিনা। surviving because the tail was refused) — and, when the user
 * is already composing the next word, by that live composing text (S170).
 *
 * - [Plan.ReplaceTail]: editor ends with `committed + tail`.
 * - [Plan.ReplaceBeforeComposing]: editor ends with `committed + tail +
 *   composingNow`; the correction goes in front of the live composing text,
 *   which is then re-established.
 *
 * The tail is at most [MAX_TAIL] characters, each a space or a tight
 * punctuation; anything else (a letter, a longer gap) means the user acted
 * in between and the visible text stays untouched. Pure decision — the
 * service owns the InputConnection and the buffer.
 */
object FastCommitReconcilePolicy {

    const val MAX_TAIL = 3

    sealed class Plan {
        /** Delete [deleteLength] before the caret and commit `word + tail`. */
        data class ReplaceTail(val deleteLength: Int, val tail: String) : Plan()
        /** Finish composing, delete [deleteLength], commit `word + tail`, re-set [composingNow]. */
        data class ReplaceBeforeComposing(val deleteLength: Int, val tail: String, val composingNow: String) : Plan()
    }

    fun plan(
        before: String?,
        committed: String,
        appendText: String,
        composingNow: String,
        bufferActive: Boolean,
        isTightPunctuation: (Char) -> Boolean,
    ): Plan? {
        if (before == null || committed.isEmpty()) return null
        val body = if (bufferActive) {
            if (composingNow.isEmpty() || !before.endsWith(composingNow)) return null
            before.dropLast(composingNow.length)
        } else before
        var tailStart = body.length
        while (tailStart > 0 && body.length - tailStart < MAX_TAIL &&
            (body[tailStart - 1] == ' ' || isTightPunctuation(body[tailStart - 1]))
        ) tailStart--
        val tail = body.substring(tailStart)
        if (!body.substring(0, tailStart).endsWith(committed)) return null
        // The commit appended [appendText]; a tail without it means the user
        // deleted it — leave the text alone.
        if (appendText.isNotEmpty() && tail.isEmpty()) return null
        return if (bufferActive) {
            Plan.ReplaceBeforeComposing(committed.length + tail.length + composingNow.length, tail, composingNow)
        } else {
            Plan.ReplaceTail(committed.length + tail.length, tail)
        }
    }
}
