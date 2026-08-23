package com.banglu.winime

import com.banglu.engine.SmartEngineAdapter
import com.banglu.winime.composer.ComposerEngine

/**
 * The engine seam — `SmartEngineAdapter` behind [ComposerEngine], with the
 * SAME context calls the Android IME and banglu-web make (S130). It shipped
 * once as bare `convertWord`/`getSuggestions` and the user noticed within a
 * day that "the real engine is not used same-to-same like the Android or web
 * app": identical engine, identical dictionary, visibly different Bangla,
 * because the whole trigram/bigram context layer was switched off. The parity
 * wall (S130ContextParityTest) now pins this object against the Android call
 * shapes on the real dictionary.
 *
 * Called only from the controller's single worker thread, so it needs no lock
 * of its own — `SmartEngine` is not internally thread-safe, and that single
 * lane IS the guarantee. Do not add a second caller.
 */
internal object AdapterComposerEngine : ComposerEngine {
    // Rule-only, zero I/O — the same call the Android hot path and the desktop
    // editor's EngineFacade.instant use. Here it is the degraded path: it needs
    // no store, so it still answers when the full pipeline throws.
    override fun instant(raw: String): String =
        SmartEngineAdapter.convertForInstantPreview(raw)

    // convertWordWithContext reads the last two NON-BLANK entries from the end
    // of the list, so the order is oldest-first: [prev2, prev1].
    override fun convert(raw: String, prev1: String?, prev2: String?): String =
        SmartEngineAdapter.convertWordWithContext(raw, listOfNotNull(prev2, prev1)).bengali

    override fun suggest(raw: String, limit: Int, prev1: String?, prev2: String?): List<String> =
        SmartEngineAdapter.getSuggestionsWithContext(raw, listOfNotNull(prev2, prev1), limit)
            .map { it.bengali }

    override fun selected(raw: String, bangla: String) {
        // Reached only for a non-primary pick (S26 law, enforced in Controller).
        SmartEngineAdapter.onWordSelected(
            phonetic = raw,
            bengali = bangla,
            learnAsWord = false,
            explicitChoice = true,
        )
    }

    override fun recordCommitPair(prev: String, next: String) {
        SmartEngineAdapter.recordNextWordUsage(prev, next)
    }

    override fun predictNext(prev2: String?, prev1: String, limit: Int): List<String> =
        SmartEngineAdapter.getNextWordPredictions(prev2, prev1, limit).map { it.bengali }
}
