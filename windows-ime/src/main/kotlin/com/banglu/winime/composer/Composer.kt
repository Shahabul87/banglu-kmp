package com.banglu.winime.composer

/** One key event as the Windows keyboard hook sees it. */
sealed interface ComposerKey {
    data class Letter(val c: Char) : ComposerKey
    data object Space : ComposerKey
    data object Backspace : ComposerKey
    data class Digit(val c: Char) : ComposerKey
    data object Escape : ComposerKey
    data object Enter : ComposerKey
    data object Tab : ComposerKey
    data class Punctuation(val p: String) : ComposerKey
}

/**
 * What the hook must do in response to a key. Unlike IMK on macOS, the hook
 * swallows every key itself, so there is no marked text: the forming word is
 * echoed straight into the focused application and corrected in place
 * (Preview), the way Avro does it. "Let this key through" means a later task
 * must re-inject it synthetically (ForwardKey replaces passThrough).
 */
sealed interface ComposerAction {
    /**
     * The word being typed, as it should now READ IN THE HOST APPLICATION.
     * The controller echoes it there immediately (in-place, by diffing against
     * what it echoed last), so this is not a hint to a popup — it is the text
     * the user is looking at. `""` means the word is gone: un-type it.
     *
     * Emitted by the live path only. A word that ENDS emits [Commit] instead,
     * which seals the same text into the document.
     */
    data class Preview(val bangla: String, val raw: String) : ComposerAction
    /** Inject as unicode text. */
    data class Commit(val text: String) : ComposerAction
    /** Re-inject the original key event (Enter/Tab/Backspace/Escape). */
    data class ForwardKey(val key: ComposerKey) : ComposerAction
    data class Candidates(val list: List<String>) : ComposerAction
}

/** The engine seam: full-pipeline convert + ranked suggestions. */
interface ComposerEngine {
    /**
     * Rule-only, zero-I/O transliteration — the same call the Android hot path
     * and the desktop editor use for their instant preview. It needs no
     * dictionary, no SQLite and no learned data, which is exactly why it is the
     * degraded path when [convert] cannot answer.
     */
    fun instant(raw: String): String

    /**
     * The full pipeline. Measured at ~17 us per keystroke on the real
     * dictionary (its caches are warm by the second letter), so it stays
     * SYNCHRONOUS on the typing path: the user sees the true Bangla the
     * instant they type it rather than watching a rule-only approximation
     * change under them when a later pass lands.
     */
    fun convert(raw: String): String

    /**
     * Ranked alternatives. ~7.5 ms per keystroke on the real dictionary — 370x
     * everything else, and the entire reason typing felt slow. NEVER called
     * from the typing path; only from [Composer.refineCandidates], behind the
     * controller's debounce.
     */
    fun suggest(raw: String, limit: Int = 6): List<String>

    /**
     * Teach the engine one explicit, user-chosen alternative — the call shape
     * of `SmartEngineAdapter.onWordSelected(raw, bangla, learnAsWord = false,
     * explicitChoice = true)`. Called ONLY for a non-primary pick: committing
     * the engine's own primary is never learned (S26 law). No-op by default so
     * a read-only host need not implement it.
     */
    fun selected(raw: String, bangla: String) {}
}

/**
 * The IME's typing state machine — a line-faithful Kotlin port of
 * macos-ime/Sources/BangluCore/Composer.swift (macOS S51), re-expressed for
 * a Windows keyboard hook. Pure logic, no JNA/Win32 imports.
 *
 * দাঁড়ি uses the pending-space model: the hook cannot edit text already
 * committed to the host app, so a space after a word commits the word and
 * HOLDS the space; what arrives next decides whether it becomes " ", "। ",
 * or is swallowed (tight punctuation).
 *
 * The engine work is split by cost, not by convenience: conversion is
 * synchronous on the typing path (~17 us) and the suggestion query is not
 * (~7.5 ms). [refineCandidates] is the second half, driven by the controller's
 * debounce and guarded by [generation].
 */
class Composer(private val engine: ComposerEngine, banglaDigits: Boolean = true) {
    // Read on the worker thread (Controller.handle), written from the UI
    // thread by the tray's বাংলা সংখ্যা toggle (Task 8) — @Volatile is the
    // whole cross-thread contract, same as Controller.engineReady.
    @Volatile
    var banglaDigits: Boolean = banglaDigits

    private var formingRaw = ""
    private var formingBangla = ""
    private var candidates: List<String> = emptyList()
    private var dariJustCommitted = false
    private var gen = 0L

    var pendingSpace: Boolean = false
        private set

    /** True while a word is being typed. */
    val forming: Boolean get() = formingRaw.isNotEmpty()

    /**
     * Bumped every time the forming buffer changes or ends. A debounced
     * candidate query carries the generation it was scheduled for and
     * [refineCandidates] discards it if this has moved on — the guard IS the
     * staleness check, so a slow query for `kmn` can never overwrite the strip
     * for `kmna`.
     */
    val generation: Long get() = gen

    /** Fires on every candidate pick; the caller decides whether to learn. */
    var onPick: ((raw: String, bangla: String, wasPrimary: Boolean) -> Unit)? = null

    /**
     * Reports a full-pipeline conversion that threw. The word is NOT lost when
     * this fires — the rule-only layer produced the text instead — but the
     * failure must still reach the user's tray rather than degrading silently.
     */
    var onConversionFault: ((Throwable) -> Unit)? = null

    companion object {
        private val tightPunctuation = setOf(",", "।", "?", "!")

        /**
         * How many candidates a digit can pick and the preview strip shows.
         * The three must agree: [MAX_CANDIDATES] = engine suggestions + the
         * raw-roman escape hatch, and PreviewWindow renders that many chips.
         */
        const val MAX_CANDIDATES = 6
        private const val MAX_ENGINE_SUGGESTIONS = MAX_CANDIDATES - 1
    }

    fun handle(key: ComposerKey): List<ComposerAction> = when (key) {
        is ComposerKey.Letter -> {
            val out = releasePendingSpace().toMutableList()
            formingRaw += key.c
            refresh(out)
            out
        }

        ComposerKey.Space -> {
            if (forming) {
                val out = commitForming()
                pendingSpace = true
                out
            } else if (pendingSpace) {
                pendingSpace = false
                if (dariJustCommitted) {
                    dariJustCommitted = false
                    listOf(ComposerAction.Commit(" "))
                } else {
                    dariJustCommitted = true
                    listOf(ComposerAction.Commit("। "))
                }
            } else {
                dariJustCommitted = false
                listOf(ComposerAction.Commit(" "))
            }
        }

        ComposerKey.Backspace -> {
            if (forming) {
                formingRaw = formingRaw.dropLast(1)
                val out = mutableListOf<ComposerAction>()
                refresh(out)
                out
            } else if (pendingSpace) {
                // The user saw themselves type a space; make it real, then
                // let the host's own backspace delete it.
                pendingSpace = false
                listOf(ComposerAction.Commit(" "), ComposerAction.ForwardKey(ComposerKey.Backspace))
            } else {
                listOf(ComposerAction.ForwardKey(ComposerKey.Backspace))
            }
        }

        is ComposerKey.Digit -> {
            val n = key.c.digitToIntOrNull()
            if (forming && candidates.isNotEmpty() && n != null && n in 1..MAX_CANDIDATES && n - 1 < candidates.size) {
                pick(n - 1)
            } else {
                val out = (if (forming) commitForming() else releasePendingSpace()).toMutableList()
                out.add(ComposerAction.Commit(if (banglaDigits) bengaliDigit(key.c) else key.c.toString()))
                out
            }
        }

        ComposerKey.Escape -> {
            if (!forming) {
                listOf(ComposerAction.ForwardKey(ComposerKey.Escape))
            } else {
                val raw = formingRaw
                clearForming()
                // No Preview("") ahead of the Commit: the Bangla is already on
                // screen, and asking for an empty preview first would un-type
                // it only for the commit to type the roman over the gap. The
                // commit itself is the reconciliation target.
                listOf(ComposerAction.Commit(raw), ComposerAction.Candidates(emptyList()))
            }
        }

        ComposerKey.Enter, ComposerKey.Tab -> {
            val out = (if (forming) commitForming() else emptyList()).toMutableList()
            pendingSpace = false
            out.add(ComposerAction.ForwardKey(key))
            out
        }

        is ComposerKey.Punctuation -> {
            val out = (if (forming) commitForming() else emptyList()).toMutableList()
            val mapped = if (key.p == ".") "।" else key.p
            if (pendingSpace) {
                pendingSpace = false
                if (mapped !in tightPunctuation) out.add(ComposerAction.Commit(" "))
            }
            out.add(ComposerAction.Commit(mapped))
            dariJustCommitted = (mapped == "।")
            out
        }
    }

    fun focusLost(): List<ComposerAction> {
        pendingSpace = false
        return if (!forming) emptyList() else commitForming()
    }

    fun pick(index: Int): List<ComposerAction> {
        if (!forming || index < 0 || index >= candidates.size) return emptyList()
        val choice = candidates[index]
        val wasPrimary = choice == formingBangla
        onPick?.invoke(formingRaw, choice, wasPrimary)
        formingBangla = choice
        val out = commitForming()
        pendingSpace = true
        return out
    }

    // MARK: - internals

    /**
     * The typing path. Conversion only — the suggestion list is NOT computed
     * here: `suggest` costs ~7.5 ms against the real dictionary and running it
     * per letter is what made typing lag. It is dropped to empty (which hides
     * the popup) and re-filled by [refineCandidates] once the user pauses.
     *
     * Dropping rather than keeping the previous list is deliberate: those
     * entries were ranked for a SHORTER buffer, and picking one would commit
     * that older word's Bangla under the current raw key — teaching the engine
     * a mapping the user never made.
     */
    private fun refresh(out: MutableList<ComposerAction>) {
        gen++
        if (formingRaw.isEmpty()) {
            clearForming()
            out.add(ComposerAction.Preview("", ""))
            out.add(ComposerAction.Candidates(emptyList()))
            return
        }
        formingBangla = liveConversion(formingRaw)
        candidates = emptyList()
        out.add(ComposerAction.Preview(formingBangla, formingRaw))
        out.add(ComposerAction.Candidates(emptyList()))
    }

    /**
     * The debounced half: the ranked alternatives for the buffer as it stood at
     * [generation]. Returns nothing at all when the buffer has moved on, so a
     * result the user has already typed past is discarded rather than shown.
     *
     * It deliberately does NOT touch [formingBangla]: the conversion on screen
     * is already the full-pipeline answer, so there is nothing here to correct
     * and the text never moves under the user when the strip appears.
     */
    fun refineCandidates(generation: Long): List<ComposerAction> {
        if (generation != gen || formingRaw.isEmpty()) return emptyList()
        // Five, not six: the raw roman appended below is a real candidate and
        // has to fit inside the SAME window the two pick paths cover — digits
        // 1..6 here, six chips in PreviewWindow. Asking for six put the
        // escape hatch at index 6 whenever the engine filled the list, where
        // no digit and no chip could reach it.
        val list = engine.suggest(formingRaw, MAX_ENGINE_SUGGESTIONS).toMutableList()
        if (formingRaw !in list) list.add(formingRaw) // raw = inline English
        candidates = list
        return listOf(ComposerAction.Candidates(candidates))
    }

    /**
     * A full-pipeline fault must cost the user accuracy, not their word: the
     * rule layer reads only immutable tables, so it still answers when the
     * store is mid-failure, and the letter they just typed still appears.
     */
    private fun liveConversion(raw: String): String = try {
        engine.convert(raw)
    } catch (t: Throwable) {
        // If the rule layer is down too the engine is simply gone: let THAT
        // throwable out and leave the controller's reset path to handle it,
        // rather than reporting the same dead engine twice per keystroke.
        val fallback = engine.instant(raw)
        onConversionFault?.invoke(t)
        fallback
    }

    /**
     * WYSIWYG: commits exactly formingBangla — never re-converts. No
     * Preview("") precedes it; the committed text IS the reconciliation
     * target, and with the conversion computed synchronously it already equals
     * what the host application shows, so the commit normally injects nothing
     * at all.
     */
    private fun commitForming(): List<ComposerAction> {
        val text = formingBangla
        clearForming()
        dariJustCommitted = false
        return listOf(ComposerAction.Commit(text), ComposerAction.Candidates(emptyList()))
    }

    private fun releasePendingSpace(): List<ComposerAction> {
        if (!pendingSpace) return emptyList()
        pendingSpace = false
        dariJustCommitted = false
        return listOf(ComposerAction.Commit(" "))
    }

    private fun clearForming() {
        gen++
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
    }
}

private fun bengaliDigit(c: Char): String {
    val n = c.digitToIntOrNull()
    return if (n != null && n in 0..9) (0x09E6 + n).toChar().toString() else c.toString()
}
