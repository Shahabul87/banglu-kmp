package com.banglu.desktop.editor

/**
 * The editor's document + typing state machine. Pure Kotlin, no Compose —
 * the JVM regression wall drives it keystroke-by-keystroke against the real
 * dictionary (spec §8). The UI renders [display] and routes every text-field
 * change through [applyEdit]; async engine results land through [refine].
 *
 * Model: [committed] holds Bangla; at most ONE forming word exists at
 * [commitPos], kept as raw Banglish in [formingRaw] and shown as
 * [formingBangla]. WYSIWYG contract: a commit inserts formingBangla exactly
 * as displayed.
 */
class EditorState(
    private val engine: EngineFacade,
    var banglaDigits: Boolean = true,
) {
    var committed: String = ""
        private set
    var commitPos: Int = 0
        private set
    var formingRaw: String = ""
        private set
    var formingBangla: String = ""
        private set
    var candidates: List<String> = emptyList()
        private set
    var popupDismissed: Boolean = false
        private set

    /** Bumps whenever the forming word changes — the UI keys refine jobs on it. */
    var generation: Long = 0L
        private set

    val forming: Boolean get() = formingRaw.isNotEmpty()
    val display: String
        get() = if (!forming) committed
        else committed.substring(0, commitPos) + formingBangla + committed.substring(commitPos)
    val cursor: Int get() = commitPos + formingBangla.length
    val formingRange: IntRange?
        get() = if (forming) commitPos until (commitPos + formingBangla.length) else null
    val popupVisible: Boolean get() = forming && !popupDismissed && candidates.isNotEmpty()

    /** UI feeds every text-field change here; classifies the edit by diffing. */
    fun applyEdit(newText: String, newCursor: Int) {
        val old = display
        when {
            newText == old && newCursor == cursor -> return
            newText == old -> {                       // pure cursor move
                commitForming()
                commitPos = newCursor.coerceIn(0, committed.length)
            }
            newText.length == old.length + 1 && newCursor == cursor + 1 &&
                newText.startsWith(old.substring(0, cursor)) &&
                newText.endsWith(old.substring(cursor)) ->
                insertChar(newText[cursor])
            newText.length == old.length - 1 && newCursor == cursor - 1 && cursor > 0 &&
                newText == old.removeRange(cursor - 1, cursor) ->
                backspace()
            else -> {                                  // paste / selection replace
                commitFormingInternal()
                committed = newText
                commitPos = newCursor.coerceIn(0, committed.length)
            }
        }
    }

    private fun insertChar(c: Char) {
        when {
            c.isLetter() && c.code < 0x0980 -> {       // roman letters form Banglish
                formingRaw += c
                formingBangla = engine.instant(formingRaw)
                popupDismissed = false
                generation++
            }
            c == ' ' -> {
                if (forming) {
                    commitFormingInternal()
                    insertCommitted(" ")
                } else {
                    insertCommitted(" ")
                }
            }
            else -> {                                   // punctuation, newline, digits (Task 3)
                commitFormingInternal()
                insertCommitted(c.toString())
            }
        }
    }

    private fun backspace() {
        if (forming) {
            formingRaw = formingRaw.dropLast(1)
            formingBangla = if (forming) engine.instant(formingRaw) else ""
            if (!forming) { candidates = emptyList(); popupDismissed = false }
            generation++
        } else if (commitPos > 0) {
            committed = committed.removeRange(commitPos - 1, commitPos)
            commitPos -= 1
        }
    }

    private fun insertCommitted(s: String) {
        committed = committed.substring(0, commitPos) + s + committed.substring(commitPos)
        commitPos += s.length
    }

    /** Commits the VISIBLE Bangla exactly (WYSIWYG, invariant #2). */
    fun commitForming() {
        commitFormingInternal()
    }

    internal fun commitFormingInternal() {
        if (!forming) return
        insertCommitted(formingBangla)
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }

    /** Async engine result landing; stale results (raw moved on) are dropped. */
    fun refine(raw: String, bangla: String, suggested: List<String>) {
        if (raw != formingRaw) return
        formingBangla = bangla
        candidates = (suggested + raw).distinct().take(7)
    }

    /** Open-file / draft-restore entry point. */
    fun setAll(text: String, cursorAt: Int = text.length) {
        committed = text
        commitPos = cursorAt.coerceIn(0, text.length)
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }
}
