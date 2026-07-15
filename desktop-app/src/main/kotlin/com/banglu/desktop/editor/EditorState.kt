package com.banglu.desktop.editor

import java.util.ArrayDeque

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
    data class Snapshot(val committed: String, val commitPos: Int, val formingRaw: String)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

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
                pushUndo()
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
                } else if (commitPos >= 1 && committed[commitPos - 1] == ' ' &&
                    (commitPos < 2 || committed[commitPos - 2] !in " ।\n")
                ) {
                    // Double space after a word → দাঁড়ি (spec §4, Android rule)
                    pushUndo()
                    committed = committed.replaceRange(commitPos - 1, commitPos, "। ")
                    commitPos += 1
                } else {
                    insertCommitted(" ")
                }
            }
            c in '1'..'6' && popupVisible && (c - '1') < candidates.size ->
                pickCandidate(c - '1')
            c.isDigit() -> {
                commitFormingInternal()
                pushUndo()
                insertCommitted(if (banglaDigits) bengaliDigit(c) else c.toString())
            }
            else -> {
                commitFormingInternal()
                pushUndo()
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
            pushUndo()
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
        pushUndo()
        insertCommitted(formingBangla)
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }

    /**
     * Commits [candidates][index] + a space. Learning law (invariant #3):
     * ONLY a pick that differs from what would have been committed anyway is
     * an explicit choice; picking the engine's own primary teaches nothing.
     */
    fun pickCandidate(index: Int) {
        val choice = candidates.getOrNull(index) ?: return
        if (!forming) return
        pushUndo()
        if (choice != formingBangla) engine.selected(formingRaw, choice, explicit = true)
        formingBangla = choice
        commitFormingInternal()
        insertCommitted(" ")
    }

    fun dismissPopup() {
        popupDismissed = true
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

    private fun pushUndo() {
        undoStack.addLast(Snapshot(committed, commitPos, formingRaw))
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun restore(s: Snapshot) {
        committed = s.committed
        commitPos = s.commitPos
        formingRaw = s.formingRaw
        formingBangla = if (forming) engine.instant(formingRaw) else ""
        candidates = emptyList()
        popupDismissed = false
        generation++
    }

    fun undo() {
        val prev = undoStack.pollLast() ?: return
        redoStack.addLast(Snapshot(committed, commitPos, formingRaw))
        restore(prev)
    }

    fun redo() {
        val next = redoStack.pollLast() ?: return
        undoStack.addLast(Snapshot(committed, commitPos, formingRaw))
        restore(next)
    }
}

internal fun bengaliDigit(c: Char): String =
    if (c in '0'..'9') ('০' + (c - '0')).toString() else c.toString()
