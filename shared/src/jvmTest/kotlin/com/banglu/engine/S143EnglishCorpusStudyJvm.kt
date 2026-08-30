package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * S143 study (user, 2026-08-29: "test with thousands of common English
 * words"): the google-10000-english list against the real store — exact
 * spellings (does the English word resolve to an English rendering or to an
 * everyday Bengali word, and is the English spelling on the strip?) and one
 * deterministic slip per word (does the rescue land on the same rendering?).
 * Opt-in (BANGLU_ENGLISH_STUDY=1); writes docs/engine-english-study-<date>.md.
 */
class S143EnglishCorpusStudyJvm {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private val store get() = ConjunctSolutionRoundJvmTest.store
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    private fun slip(w: String): String? {
        if (w.length < 6) return null
        val i = w.length / 2
        return if (w[i] != w[i + 1]) w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)
        else w.removeRange(i, i + 1)
    }

    @Test
    fun commonEnglishWordsStudy() {
        // ~35K engine calls (commit + strip + slip per word): a 10+ minute
        // study, not a wall. Opt in like the S24 eval harness (EVAL_WORDS):
        //   BANGLU_ENGLISH_STUDY=1 ./gradlew :shared:jvmTest --tests '*S143EnglishCorpusStudy*'
        if (System.getenv("BANGLU_ENGLISH_STUDY") != "1") {
            println("S143 study skipped (set BANGLU_ENGLISH_STUDY=1 to run)")
            return
        }
        val words = javaClass.getResourceAsStream("/studies/google-10000-english.txt")!!.bufferedReader()
            .readLines().map { it.trim().lowercase() }.filter { it.length >= 4 && it.all { c -> c in 'a'..'z' } }.distinct()
        var english = 0; var everydayBengali = 0; var unknownToLexicon = 0; var dictionaryLoanword = 0
        val bengaliBelowBand = ArrayList<String>(); val chipMissing = ArrayList<String>()
        var slips = 0; var rescued = 0; val slipMiss = ArrayList<String>()
        for (w in words) {
            val lex = store.lookupEnglish(w)
            val r = engine.convertWord(w)
            val strip = engine.getSuggestions(w, 6)
            if (lex == null) { unknownToLexicon++; continue }
            val renderedEnglish = r.source == ResolutionSource.ENGLISH_LEXICON ||
                r.source == ResolutionSource.ENGLISH_PASSTHROUGH ||
                fold(r.bengali) == fold(lex) || engine.getSuggestions(w, 1).firstOrNull()?.source == "english_passthrough"
            if (renderedEnglish) english++ else {
                val ev = ConjunctSolutionRoundJvmTest.store.let { st -> st.lookupExact(w).maxOfOrNull { it.frequency } ?: 0 }
                val reverse = ReverseTransliterator.reverseWord(r.bengali)
                val loanwordSpelling = r.source == ResolutionSource.DICTIONARY && r.confidence >= 0.95 &&
                    com.banglu.engine.dictionary.PhoneticOverlapScorer.score(w, reverse).score >= 0.6
                if (ev >= 75) everydayBengali++
                else if (loanwordSpelling) { dictionaryLoanword++ }
                else bengaliBelowBand.add("$w -> ${r.bengali} [${r.source.name} ev=$ev lex=$lex]")
            }
            if (strip.none { it.bengali.lowercase() == w }) chipMissing.add("$w -> ${strip.map { it.bengali }}")
            slip(w)?.let { typo ->
                if (store.lookupEnglish(typo) != null) return@let   // the slip is itself a word
                slips++
                val rs = engine.convertWord(typo)
                if (fold(rs.bengali) == fold(r.bengali)) rescued++ else slipMiss.add("$typo (for $w) -> ${rs.bengali} [${rs.source.name}] vs ${r.bengali}")
            }
        }
        val known = words.size - unknownToLexicon
        val report = buildString {
            appendLine("# English-word study — 2026-08-29 (S142/S143)")
            appendLine()
            appendLine("Source: google-10000-english (no swears), ${words.size} words of 4+ letters; ${unknownToLexicon} unknown to the english_lexicon (skipped).")
            appendLine()
            appendLine("## Exact spellings (${known} lexicon words)")
            appendLine("- rendered as English (pronunciation or passthrough): $english")
            appendLine("- dictionary's own loanword spelling (reads the key; not the CMU row): $dictionaryLoanword")
            appendLine("- kept as an everyday Bengali word (evidence >= 75): $everydayBengali")
            appendLine("- Bengali reading below the band still won: ${bengaliBelowBand.size}")
            appendLine("- English spelling missing from the 6-chip strip: ${chipMissing.size}")
            appendLine()
            appendLine("## One-slip misspellings (${slips} slips)")
            appendLine("- rescued to the same rendering: $rescued (${if (slips > 0) rescued * 100 / slips else 0}%)")
            appendLine()
            appendLine("## Samples: Bengali below the band won"); bengaliBelowBand.take(80).forEach { appendLine("- $it") }
            appendLine(); appendLine("## Samples: English chip missing"); chipMissing.take(40).forEach { appendLine("- $it") }
            appendLine(); appendLine("## Samples: slip not rescued"); slipMiss.take(120).forEach { appendLine("- $it") }
        }
        // Gradle runs this module with cwd = shared/; the report belongs in the repo's docs/.
        val reportFile = if (File("../docs").isDirectory) File("../docs/engine-english-study-2026-08-29.md") else File("docs/engine-english-study-2026-08-29.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(report)
        File("/tmp/claude-501/english-study.md").writeText(report)
        assertTrue(known > 5000, "study needs the lexicon: $known")
    }
}
