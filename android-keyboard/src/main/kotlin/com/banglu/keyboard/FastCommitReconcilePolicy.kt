package com.banglu.keyboard

/**
 * S170: where a fast-committed preview (S32: the space landed before the
 * authoritative conversion) may be replaced once that conversion arrives.
 *
 * - [Plan.ReplaceTail]: the editor still ends with the committed text (plus at
 *   most one tight punctuation, S70) and no new word is being composed — the
 *   original S32 case.
 * - [Plan.ReplaceBeforeComposing]: the user is ALREADY composing the next word
 *   and the editor ends with `committed + composingNow`. The top-1,000 device
 *   study (S169c) showed long words at machine-speed typing kept their
 *   rule-only preview forever because this case was refused; the correction
 *   goes in front of the live composing text, which is then re-established.
 *
 * Pure decision — the service owns the InputConnection and the buffer.
 */
object FastCommitReconcilePolicy {

    sealed class Plan {
        data class ReplaceTail(val deleteLength: Int, val trailing: String) : Plan()
        data class ReplaceBeforeComposing(val deleteLength: Int, val composingNow: String) : Plan()
    }

    fun plan(
        before: String?,
        expected: String,
        composingNow: String,
        bufferActive: Boolean,
        isTightPunctuation: (Char) -> Boolean,
    ): Plan? {
        if (before == null || expected.isEmpty()) return null
        if (bufferActive) {
            if (composingNow.isEmpty()) return null
            val tail = expected + composingNow
            return if (before.endsWith(tail)) Plan.ReplaceBeforeComposing(tail.length, composingNow) else null
        }
        return when {
            before.endsWith(expected) -> Plan.ReplaceTail(expected.length, "")
            before.length > expected.length &&
                isTightPunctuation(before.last()) &&
                before.dropLast(1).endsWith(expected) -> Plan.ReplaceTail(expected.length + 1, before.last().toString())
            else -> null
        }
    }
}
