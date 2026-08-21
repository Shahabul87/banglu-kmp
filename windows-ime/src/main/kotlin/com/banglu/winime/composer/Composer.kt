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
 * swallows every key itself, so there is no marked text (Preview replaces
 * setMarked as our OWN preview window) and "let this key through" means a
 * later task must re-inject it synthetically (ForwardKey replaces
 * passThrough).
 */
sealed interface ComposerAction {
    /** "" + "" = hide the preview window. */
    data class Preview(val bangla: String, val raw: String) : ComposerAction
    /** Inject as unicode text. */
    data class Commit(val text: String) : ComposerAction
    /** Re-inject the original key event (Enter/Tab/Backspace/Escape). */
    data class ForwardKey(val key: ComposerKey) : ComposerAction
    data class Candidates(val list: List<String>) : ComposerAction
}

/** The engine seam: full-pipeline convert + ranked suggestions. */
interface ComposerEngine {
    fun convert(raw: String): String
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
 * injected into the host app, so a space after a word commits the word and
 * HOLDS the space; what arrives next decides whether it becomes " ", "। ",
 * or is swallowed (tight punctuation).
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

    var pendingSpace: Boolean = false
        private set

    /** True while a word is being typed. */
    val forming: Boolean get() = formingRaw.isNotEmpty()

    /** Fires on every candidate pick; the caller decides whether to learn. */
    var onPick: ((raw: String, bangla: String, wasPrimary: Boolean) -> Unit)? = null

    private companion object {
        val tightPunctuation = setOf(",", "।", "?", "!")
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
            if (forming && candidates.isNotEmpty() && n != null && n in 1..6 && n - 1 < candidates.size) {
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
                listOf(ComposerAction.Preview("", ""), ComposerAction.Commit(raw), ComposerAction.Candidates(emptyList()))
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

    private fun refresh(out: MutableList<ComposerAction>) {
        if (formingRaw.isEmpty()) {
            clearForming()
            out.add(ComposerAction.Preview("", ""))
            out.add(ComposerAction.Candidates(emptyList()))
            return
        }
        formingBangla = engine.convert(formingRaw)
        val list = engine.suggest(formingRaw, 6).toMutableList()
        if (formingRaw !in list) list.add(formingRaw) // raw = inline English
        candidates = list
        out.add(ComposerAction.Preview(formingBangla, formingRaw))
        out.add(ComposerAction.Candidates(candidates))
    }

    /** WYSIWYG: commits exactly formingBangla — never re-converts. */
    private fun commitForming(): List<ComposerAction> {
        val text = formingBangla
        clearForming()
        dariJustCommitted = false
        return listOf(ComposerAction.Preview("", ""), ComposerAction.Commit(text), ComposerAction.Candidates(emptyList()))
    }

    private fun releasePendingSpace(): List<ComposerAction> {
        if (!pendingSpace) return emptyList()
        pendingSpace = false
        dariJustCommitted = false
        return listOf(ComposerAction.Commit(" "))
    }

    private fun clearForming() {
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
    }
}

private fun bengaliDigit(c: Char): String {
    val n = c.digitToIntOrNull()
    return if (n != null && n in 0..9) (0x09E6 + n).toChar().toString() else c.toString()
}
