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

    /**
     * Delete [count] characters THIS composer committed on the immediately
     * preceding keystroke — never anything older, and never anything the user
     * typed themselves.
     *
     * It exists because this host can edit text it has already committed (the
     * controller injects backspaces; that is how the live echo corrects a word
     * in place). Exactly two things need it: the second space that turns
     * `শব্দ ` into `শব্দ। `, and the tight punctuation that turns `শব্দ ` into
     * `শব্দ,`. Both fire on the very next key after the space was written, with
     * no intervening focus change — every path that ends composition without
     * that guarantee clears the arming flag first.
     */
    data class DeleteBack(val count: Int) : ComposerAction

    /** Re-inject the original key event (Enter/Tab/Backspace/Escape). */
    data class ForwardKey(val key: ComposerKey) : ComposerAction

    /**
     * What the strip should show. [predictions] distinguishes the two things
     * it can hold: ranked alternatives for the word being typed (pickable by
     * digit AND click), or next-word predictions after a commit (click-only —
     * digits after a space type digits, so the strip must not paint digit
     * hints that would lie).
     */
    data class Candidates(val list: List<String>, val predictions: Boolean = false) : ComposerAction
}

/**
 * The engine seam: full-pipeline convert + ranked suggestions, S130: with the
 * committed-word context Android and banglu-web have always passed. [prev1]
 * is the word committed immediately before the one in question, [prev2] the
 * one before that — the trigram/bigram rerank inputs. Null means "no trusted
 * context": start of a document, after a focus change, after the user edited
 * committed text.
 */
interface ComposerEngine {
    /**
     * Rule-only, zero-I/O transliteration — the same call the Android hot path
     * and the desktop editor use for their instant preview. It needs no
     * dictionary, no SQLite and no learned data, which is exactly why it is the
     * degraded path when [convert] cannot answer.
     */
    fun instant(raw: String): String

    /**
     * The full pipeline, context-reranked — the call shape of Android's
     * `convertWordWithContext`. Cheap enough (10-40 µs on the real dictionary,
     * context rerank included) to stay SYNCHRONOUS on the typing path: the
     * user sees the true Bangla the instant they type it rather than watching
     * a rule-only approximation change under them when a later pass lands.
     */
    fun convert(raw: String, prev1: String? = null, prev2: String? = null): String

    /**
     * Ranked alternatives, context-promoted — Android's
     * `getSuggestionsWithContext`, so the chip the context lane would commit
     * leads the strip. ~0.3-0.6 ms per call on the real dictionary — many
     * times the conversion beside it, and the reason typing felt slow when it
     * ran per letter. NEVER called from the typing path; only from
     * [Composer.refineCandidates], behind the controller's debounce.
     */
    fun suggest(raw: String, limit: Int = 6, prev1: String? = null, prev2: String? = null): List<String>

    /**
     * Teach the engine one explicit, user-chosen alternative — the call shape
     * of `SmartEngineAdapter.onWordSelected(raw, bangla, learnAsWord = false,
     * explicitChoice = true)`. Called ONLY for a non-primary pick: committing
     * the engine's own primary is never learned (S26 law). No-op by default so
     * a read-only host need not implement it.
     */
    fun selected(raw: String, bangla: String) {}

    /**
     * One observed (previous, next) commit pair for personalized next-word
     * prediction — `SmartEngineAdapter.recordNextWordUsage`. The composer only
     * calls this when the two words are PROVABLY adjacent: it wrote both, with
     * exactly one plain space between them and no focus change, punctuation,
     * দাঁড়ি, Enter or edit in the gap — the same adjacency Android proves by
     * reading the field, established here by construction instead.
     */
    fun recordCommitPair(prev: String, next: String) {}

    /**
     * Next-word predictions for the strip after a commit — Android's
     * `getNextWordPredictions(prev2, prev1, limit)`. ~5 µs on the real
     * dictionary, which is why the composer may call it inside the commit
     * path itself.
     */
    fun predictNext(prev2: String?, prev1: String, limit: Int): List<String> = emptyList()
}

/**
 * The IME's typing state machine — descended from
 * macos-ime/Sources/BangluCore/Composer.swift (macOS S51), re-expressed for
 * a Windows keyboard hook. Pure logic, no JNA/Win32 imports.
 *
 * **This host can edit text it has already committed**, and that single fact
 * is what separates it from the macOS original. IMK gave the Swift composer no
 * way back into committed text, so it used the *pending-space* model: a space
 * after a word committed the word and HELD the space, and the next keystroke
 * decided whether that space became " ", "। ", or vanished. Here the controller
 * injects backspaces — it is how the live echo rewrites `কিমি` into `কেমন`
 * mid-word — so holding the space bought nothing and cost everything: the user
 * pressed space, saw no space, pressed it again, and got a দাঁড়ি they never
 * asked for.
 *
 * The model is therefore *immediate, retroactively corrected*, which is what
 * Avro does and what the user expects:
 *
 * | state                     | Space                      | tight punctuation      |
 * |---------------------------|----------------------------|------------------------|
 * | a word is forming         | commit the word + `" "`    | commit the word + mark |
 * | a space we just wrote     | delete it, write `"। "`    | delete it, write mark  |
 * | anything else             | `" "`                      | mark                   |
 *
 * The middle row is the only place this composer ever deletes committed text,
 * it can only fire on the keystroke IMMEDIATELY after the space was written,
 * and [retroSpaceArmed] — the flag that permits it — is cleared by every other
 * key and by [focusLost]. See [ComposerAction.DeleteBack].
 *
 * The engine work is split by cost, not by convenience: conversion is
 * synchronous on the typing path (tens of microseconds) and the suggestion
 * query is not (~2.3 ms). [refineCandidates] is the second half, driven by the
 * controller's debounce and guarded by [generation].
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

    /**
     * S130 — the context ledger: the last two words this composer committed,
     * newest in [prev1]. They are what Android reads back from the field
     * before every conversion; a keyboard hook cannot read the field, so the
     * composer keeps its own record and DROPS it the moment it might be wrong
     * (focus change, a forwarded backspace that edits committed text, fault
     * recovery — every caller of [focusLost] and the not-forming Backspace).
     *
     * Punctuation, দাঁড়ি and Enter deliberately do NOT clear it: the Android
     * IME's ledger survives those too, and parity means converting `kmon`
     * after `আমি,` the same way the phone does.
     */
    private var prev1: String? = null
    private var prev2: String? = null

    /**
     * True while [prev1] is provably adjacent to whatever commits next — the
     * gap since [prev1]'s commit holds exactly one plain space (the trailing
     * space of a Space/pick commit) and nothing else has happened. This is
     * the licence to call [ComposerEngine.recordCommitPair]: Android proves
     * the same adjacency by reading the field before recording; here it is
     * established by construction and revoked by every event that breaks it —
     * punctuation, দাঁড়ি, digits, Enter/Tab/Escape, edits, focus loss.
     */
    private var chainIntact = false

    /**
     * Next-word predictions currently on the strip (click-only). Non-empty
     * only immediately after a Space/pick commit; any other key clears it,
     * so a click on a stale strip can never plant a word mid-typing.
     */
    private var predictions: List<String> = emptyList()

    /**
     * The raw buffer [candidates] was ranked for. The strip is deliberately NOT
     * blanked between keystrokes any more (that is what made it invisible while
     * typing), so it can lag the buffer by one debounce interval; this is how a
     * pick knows whether the list still describes what the user typed.
     */
    private var candidatesRaw = ""
    private var gen = 0L

    /**
     * True while the last thing committed is a plain space THIS composer wrote
     * immediately after finishing a word, with no other key since.
     *
     * It is the sole licence to delete committed text (the retro-দাঁড়ি and the
     * space a tight mark swallows), so every key that is not the space or the
     * punctuation that consumes it clears the flag, and so does [focusLost] —
     * which the controller calls on focus change, mode switch, an unmanaged
     * caret-moving key, and fault recovery.
     */
    var retroSpaceArmed: Boolean = false
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

        /** Android shows five next-word predictions; parity keeps it five. */
        private const val PREDICTION_LIMIT = 5

        /** Both halves of a recorded pair must be Bengali — Android's rule. */
        private fun isBengaliWord(word: String): Boolean =
            word.any { it in 'ঀ'..'৿' }
    }

    fun handle(key: ComposerKey): List<ComposerAction> = when (key) {
        is ComposerKey.Letter -> {
            // The space this letter follows is already in the document; all
            // that ends here is its licence to be rewritten as a দাঁড়ি.
            retroSpaceArmed = false
            // The strip may keep painting predictions for one debounce beat,
            // but a click on it must be inert from this keystroke on — the
            // refine for the new buffer is what replaces the pixels.
            predictions = emptyList()
            formingRaw += key.c
            val out = mutableListOf<ComposerAction>()
            refresh(out)
            out
        }

        ComposerKey.Space -> {
            if (forming) {
                // The word AND the space, in one commit: the space must be
                // visible the instant the user presses the key, not held back
                // waiting for the next one to disambiguate it.
                val out = commitForming(trailing = " ")
                retroSpaceArmed = true
                out
            } else if (retroSpaceArmed) {
                // The second space of a sentence break. The first one is one
                // keystroke old and still the last character in the document,
                // so it can simply be taken back.
                retroSpaceArmed = false
                // The sentence is over on both counts: no pair is recorded
                // across a দাঁড়ি, and the predictions were for a sentence
                // that just ended.
                chainIntact = false
                val out = mutableListOf<ComposerAction>(
                    ComposerAction.DeleteBack(1),
                    ComposerAction.Commit("। "),
                )
                clearPredictions(out)
                out
            } else {
                // Third and later spaces, and any space that does not follow a
                // word of ours: one দাঁড়ি per sentence break, never two.
                val out = mutableListOf<ComposerAction>(ComposerAction.Commit(" "))
                clearPredictions(out)
                out
            }
        }

        ComposerKey.Backspace -> {
            if (forming) {
                formingRaw = formingRaw.dropLast(1)
                val out = mutableListOf<ComposerAction>()
                refresh(out)
                out
            } else {
                // Nothing of ours is editable any more — including a space we
                // wrote, which is now ordinary document text. Forwarding lets
                // the host delete whatever is actually behind the caret — and
                // whatever it deletes may BE the word the ledger remembers, so
                // the context is gone with it (S130).
                retroSpaceArmed = false
                clearContext()
                val out = mutableListOf<ComposerAction>(
                    ComposerAction.ForwardKey(ComposerKey.Backspace),
                )
                clearPredictions(out)
                out
            }
        }

        is ComposerKey.Digit -> {
            val n = key.c.digitToIntOrNull()
            if (forming && candidates.isNotEmpty() && n != null && n in 1..MAX_CANDIDATES && n - 1 < candidates.size) {
                pick(n - 1)
            } else {
                val out = (if (forming) commitForming() else emptyList()).toMutableList()
                retroSpaceArmed = false
                // `আমি ১ কেমন` is not an adjacent pair (Android's field check
                // refuses it too) — and digits never pick predictions: after a
                // space the user typing 1 wants ১, not the first chip.
                chainIntact = false
                clearPredictions(out)
                out.add(ComposerAction.Commit(if (banglaDigits) bengaliDigit(key.c) else key.c.toString()))
                out
            }
        }

        ComposerKey.Escape -> {
            if (!forming) {
                // Escape can dismiss a dialog, an autocomplete list, anything —
                // after which the caret is not reliably ours. Disarm rather
                // than risk a retro-দাঁড়ি backspace landing on someone's text.
                retroSpaceArmed = false
                chainIntact = false
                val out = mutableListOf<ComposerAction>(
                    ComposerAction.ForwardKey(ComposerKey.Escape),
                )
                clearPredictions(out)
                out
            } else {
                val raw = formingRaw
                clearForming()
                // The roman stays as typed. It still becomes the context —
                // the word before the NEXT word really is `fb` — but a pair
                // with a non-Bengali half is never recorded, and the chain
                // does not survive an escape.
                prev2 = prev1
                prev1 = raw
                chainIntact = false
                // No Preview("") ahead of the Commit: the Bangla is already on
                // screen, and asking for an empty preview first would un-type
                // it only for the commit to type the roman over the gap. The
                // commit itself is the reconciliation target.
                listOf(ComposerAction.Commit(raw), ComposerAction.Candidates(emptyList()))
            }
        }

        ComposerKey.Enter, ComposerKey.Tab -> {
            val out = (if (forming) commitForming() else emptyList()).toMutableList()
            // A space already written stays a space: nothing about pressing
            // Enter says the user meant to end a sentence. But on Windows the
            // key may have sent a chat message or moved to another field, so
            // no pair is recorded across it (S130).
            retroSpaceArmed = false
            chainIntact = false
            clearPredictions(out)
            out.add(ComposerAction.ForwardKey(key))
            out
        }

        is ComposerKey.Punctuation -> {
            val out = (if (forming) commitForming() else emptyList()).toMutableList()
            val mapped = if (key.p == ".") "।" else key.p
            // Tight punctuation hugs the word: `আমি ,` is wrong in Bangla as it
            // is in English, so the space typed one keystroke ago comes back
            // out. (The check runs on the MAPPED character — "." is "।" first.)
            if (retroSpaceArmed && mapped in tightPunctuation) {
                out.add(ComposerAction.DeleteBack(1))
            }
            retroSpaceArmed = false
            // `আমি, কেমন` is not an adjacent pair — but the ledger survives:
            // the Android IME still converts কেমন knowing আমি (parity).
            chainIntact = false
            clearPredictions(out)
            out.add(ComposerAction.Commit(mapped))
            out
        }
    }

    fun focusLost(): List<ComposerAction> {
        retroSpaceArmed = false
        // Flush FIRST: the stranded word is real text in the old window, and
        // its pair with the word before it is real adjacency — then drop the
        // ledger, because nothing about the old document proves anything
        // about wherever the caret is now.
        val actions = if (forming) commitForming() else emptyList()
        clearContext()
        predictions = emptyList()
        return actions
    }

    fun pick(index: Int): List<ComposerAction> {
        if (!forming) return pickPrediction(index)
        if (index < 0 || index >= candidates.size) return emptyList()
        val choice = candidates[index]
        val wasPrimary = choice == formingBangla
        // WYSIWYG for the strip: the user gets the chip they clicked. But a
        // strip ranked for a SHORTER buffer must never TEACH — `kmn` → `কিমি`
        // is a mapping the user never made, and learned.json is forever.
        if (candidatesRaw == formingRaw) onPick?.invoke(formingRaw, choice, wasPrimary)
        formingBangla = choice
        // A pick ends the word exactly the way space does, space included: the
        // user's next letter starts the next word.
        val out = commitForming(trailing = " ")
        retroSpaceArmed = true
        return out
    }

    /**
     * A click on a prediction chip (S130): commits the predicted word plus the
     * space that ends it, exactly the way Android's prediction row does, and
     * keeps the chain going — the next strip predicts what follows THIS word.
     *
     * Only reachable while [predictions] is non-empty, which by construction
     * means the last thing committed was `word + " "` and nothing has happened
     * since — so the pair (prev1, prediction) is adjacent and recordable.
     */
    private fun pickPrediction(index: Int): List<ComposerAction> {
        if (index < 0 || index >= predictions.size) return emptyList()
        val word = predictions[index]
        gen++
        val out = mutableListOf<ComposerAction>(ComposerAction.Commit("$word "))
        finishWord(word, spaceFollows = true)
        retroSpaceArmed = true
        out.add(ComposerAction.Candidates(predictions, predictions = predictions.isNotEmpty()))
        return out
    }

    // MARK: - internals

    /**
     * The typing path. Conversion only — the suggestion list is NOT computed
     * here: `suggest` costs ~2.3 ms against the real dictionary against tens of
     * microseconds for the conversion, and running it per letter is what made
     * typing lag. [refineCandidates] fills it behind the controller's debounce.
     *
     * It does NOT blank the strip either, and that is the fix for the second
     * reported defect. Blanking it here meant the popup was hidden on every
     * keystroke and only reappeared after a pause LONGER than the gap between
     * two letters — so a user typing normally saw no suggestions at all. The
     * list now survives the keystroke and is replaced when the fresh one lands;
     * [candidatesRaw] records which buffer it belongs to so a pick from a
     * momentarily stale strip can be committed without being learned.
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
        out.add(ComposerAction.Preview(formingBangla, formingRaw))
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
        val list = engine.suggest(formingRaw, MAX_ENGINE_SUGGESTIONS, prev1, prev2).toMutableList()
        if (formingRaw !in list) list.add(formingRaw) // raw = inline English
        candidates = list
        candidatesRaw = formingRaw
        return listOf(ComposerAction.Candidates(candidates))
    }

    /**
     * A full-pipeline fault must cost the user accuracy, not their word: the
     * rule layer reads only immutable tables, so it still answers when the
     * store is mid-failure, and the letter they just typed still appears.
     */
    private fun liveConversion(raw: String): String = try {
        // S187 ("if the engine anyhow cannot generate words"): a blank answer
        // for a non-blank key would make the echo diff DELETE the word on
        // screen. The ladder is full pipeline → rule layer → the raw letters.
        engine.convert(raw, prev1, prev2).ifBlank { engine.instant(raw) }.ifBlank { raw }
    } catch (t: Throwable) {
        // If the rule layer is down too the engine is simply gone: let THAT
        // throwable out and leave the controller's reset path to handle it,
        // rather than reporting the same dead engine twice per keystroke.
        val fallback = engine.instant(raw).ifBlank { raw }
        onConversionFault?.invoke(t)
        fallback
    }

    /**
     * WYSIWYG: commits exactly formingBangla — never re-converts. No
     * Preview("") precedes it; the committed text IS the reconciliation
     * target, and with the conversion computed synchronously it already equals
     * what the host application shows, so the commit normally injects nothing
     * at all beyond [trailing].
     *
     * [trailing] rides along in the SAME commit rather than as a second action
     * so the controller's diff sees one target: committing `কেম ` over an
     * echoed `কেমন` is one backspace and one insert, not a rewrite followed by
     * an append.
     */
    private fun commitForming(trailing: String = ""): List<ComposerAction> {
        val word = formingBangla
        clearForming()
        finishWord(word, spaceFollows = trailing == " ")
        return listOf(
            ComposerAction.Commit(word + trailing),
            // Ends the word's candidate strip — and, after a space, replaces
            // it with the next-word predictions in the same listener call, so
            // the window never flickers through an empty state between them.
            ComposerAction.Candidates(predictions, predictions = predictions.isNotEmpty()),
        )
    }

    /**
     * The context bookkeeping every finished word runs (S130): record the
     * adjacent pair when the chain permits it, shift the ledger, and fetch
     * the next-word predictions when a space follows. Engine faults here may
     * cost personalization or a strip — never the word, which is already in
     * [ComposerAction.Commit] by the time this runs.
     */
    private fun finishWord(word: String, spaceFollows: Boolean) {
        if (word.isBlank()) {
            chainIntact = false
            predictions = emptyList()
            return
        }
        val previous = prev1
        if (chainIntact && previous != null && isBengaliWord(previous) && isBengaliWord(word)) {
            try {
                engine.recordCommitPair(previous, word)
            } catch (t: Throwable) {
                onConversionFault?.invoke(t)
            }
        }
        prev2 = prev1
        prev1 = word
        chainIntact = spaceFollows
        predictions = if (spaceFollows) {
            try {
                engine.predictNext(prev2, word, PREDICTION_LIMIT)
            } catch (t: Throwable) {
                onConversionFault?.invoke(t)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /** The ledger can no longer be trusted — see [prev1] for the reasons. */
    private fun clearContext() {
        prev1 = null
        prev2 = null
        chainIntact = false
    }

    /** Hides a prediction strip, emitting only when there is one to hide. */
    private fun clearPredictions(out: MutableList<ComposerAction>) {
        if (predictions.isEmpty()) return
        predictions = emptyList()
        out.add(ComposerAction.Candidates(emptyList()))
    }

    private fun clearForming() {
        gen++
        formingRaw = ""
        formingBangla = ""
        candidates = emptyList()
        candidatesRaw = ""
    }
}

private fun bengaliDigit(c: Char): String {
    val n = c.digitToIntOrNull()
    return if (n != null && n in 0..9) (0x09E6 + n).toChar().toString() else c.toString()
}
