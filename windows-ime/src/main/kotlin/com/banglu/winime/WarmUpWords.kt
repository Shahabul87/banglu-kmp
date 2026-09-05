package com.banglu.winime

import com.banglu.engine.TutorialWords
import com.banglu.engine.dictionary.CulturalPhrases

/**
 * S187: the words the boot warm-up types through the engine lane — the
 * tutorial curriculum's real romans (conjunct-heavy, every family) plus the
 * cultural phrases, capped so a slow disk finishes in seconds. Read-only
 * shared data; nothing here is user text.
 */
internal object WarmUpWords {
    const val CAP = 220
    val list: List<String> by lazy {
        (TutorialWords.ALL_WORDS.map { it.roman } + CulturalPhrases.EXACT.keys)
            .filter { it.isNotBlank() && it.all { c -> c in 'a'..'z' } }
            .distinct()
            .let { all -> if (all.size <= CAP) all else all.filterIndexed { i, _ -> i % (all.size / CAP + 1) == 0 }.take(CAP) }
    }
}
