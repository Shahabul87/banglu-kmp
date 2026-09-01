package com.banglu.engine.glide

import com.banglu.engine.english.EnglishWordData

/**
 * S163: the EN-mode glide vocabulary — the existing English wordlist,
 * rank-ordered, exposed to the platform lexicon builder (EnglishWordData is
 * internal to shared; this facade keeps it that way).
 */
object GlideEnglishWords {
    /** Top [n] words as (word, pseudo-frequency) — rank inverted so the
     *  decoder's frequency prior matches the list's own ordering. */
    fun top(n: Int): List<Pair<String, Int>> {
        val words = EnglishWordData.WORDS
        val take = n.coerceAtMost(words.size)
        return (0 until take).map { i -> words[i] to (take - i) }
    }
}
