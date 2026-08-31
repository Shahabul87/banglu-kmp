package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import kotlin.test.Test

/**
 * S149 (2026-08-30): Banglish-corpus study on the real dictionary, driven by
 * the datasets from the user's deep-research report (BanglaTLit, Vashantor,
 * Socian, Code-mixed Chaos, PolCSBD, Bengali SMS). Word-aligns the
 * (roman, gold-Bangla) sentence pairs, converts every roman token through
 * the engine, and buckets each miss into a failure pattern.
 *
 * Opt-in like the S143 study: BANGLU_BANGLISH_STUDY=1 and
 * BANGLU_BANGLISH_DIR=<dir with staged/pairs.tsv + staged/lines.tsv>.
 * Writes docs/engine-banglish-study-2026-08-30.md + failures TSV.
 */
class S149BanglishCorpusStudyJvm {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private fun fold(s: String): String = ReverseTransliterator.foldNukta(s)

    // Confusion-class folds, applied on top of foldNukta.
    private fun foldWith(s: String, map: Map<Char, Char>): String =
        buildString { for (c in fold(s)) append(map[c] ?: c) }

    private val SIBILANT = mapOf('শ' to 'স', 'ষ' to 'স')
    private val DENTAL = mapOf('ট' to 'ত', 'ঠ' to 'থ', 'ড' to 'দ', 'ঢ' to 'ধ', 'ণ' to 'ন')
    private val VOWEL_LEN = mapOf('ী' to 'ি', 'ূ' to 'ু', 'ঈ' to 'ই', 'ঊ' to 'উ')
    private val RA_CLASS = mapOf('ড' to 'র', 'ড়' to 'র', 'ঢ়' to 'র', 'ঢ' to 'র')
    private val NASAL = mapOf('ং' to 'ঙ', 'ঁ' to 'ঙ', 'ম' to 'ঙ', 'ন' to 'ঙ')
    private val JA_YA = mapOf('য়' to 'জ', 'য' to 'জ')

    private fun stripFinalVowel(s: String): String =
        s.trimEnd('া', 'ে', 'ো', 'ি', 'ু', '্')

    private fun edit(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val t = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return dp[b.length]
    }

    private fun classify(roman: String, out: String, gold: String): String {
        val fo = fold(out); val fg = fold(gold)
        if (fo.all { it.code < 128 }) return "english_passthrough_vs_bengali_gold"
        if (foldWith(fo, SIBILANT) == foldWith(fg, SIBILANT)) return "sibilant_s_sh_ss"
        if (foldWith(fo, DENTAL) == foldWith(fg, DENTAL)) return "dental_vs_retroflex"
        if (foldWith(fo, VOWEL_LEN) == foldWith(fg, VOWEL_LEN)) return "vowel_length_i_u"
        if (foldWith(fo, RA_CLASS) == foldWith(fg, RA_CLASS)) return "r_rr_d_class"
        if (foldWith(fo, JA_YA) == foldWith(fg, JA_YA)) return "j_y_class"
        if (foldWith(fo, NASAL) == foldWith(fg, NASAL)) return "nasal_ng_m_chandra"
        if (stripFinalVowel(fo) == stripFinalVowel(fg)) return "final_vowel_ending"
        val combined = { s: String -> foldWith(foldWith(foldWith(foldWith(s, SIBILANT), DENTAL), VOWEL_LEN), NASAL) }
        if (combined(fo) == combined(fg)) return "multi_confusion_mix"
        if (fg.startsWith(fo) || fo.startsWith(fg)) return "inflection_or_truncation"
        val d = edit(fo, fg)
        if (d <= 2) return "near_miss_le2_edits"
        return "different_word"
    }

    @Test
    fun banglishCorpusStudy() {
        if (System.getenv("BANGLU_BANGLISH_STUDY") != "1") {
            println("S149 study skipped (set BANGLU_BANGLISH_STUDY=1)")
            return
        }
        val dir = File(System.getenv("BANGLU_BANGLISH_DIR") ?: error("BANGLU_BANGLISH_DIR not set"))
        val romanTok = Regex("[a-z]+")
        val banglaOk = Regex("^[\\u0980-\\u09FF\\u200C\\u200D]+$")

        data class Src(
            var sentences: Int = 0, var aligned: Int = 0, var sentenceExact: Int = 0,
            var words: Int = 0, var wordExact: Int = 0, var top6: Int = 0,
            val classes: MutableMap<String, Int> = mutableMapOf()
        )

        val perSource = linkedMapOf<String, Src>()
        val failCounts = HashMap<Triple<String, String, String>, Int>() // roman,out,gold -> n
        val capPerSource = mapOf(
            "banglatlit" to 9000, "vashantor-std" to 12500, "vashantor-dialect" to 2500
        )
        val seenPerSource = HashMap<String, Int>()
        var t0 = System.nanoTime(); var wordsTimed = 0L

        File(dir, "staged/pairs.tsv").forEachLine { line ->
            val p = line.split('\t')
            if (p.size != 3) return@forEachLine
            val (src, romanRaw, goldRaw) = p
            val cap = capPerSource[src] ?: 0
            val seen = seenPerSource.getOrDefault(src, 0)
            if (seen >= cap) return@forEachLine
            val s = perSource.getOrPut(src) { Src() }
            s.sentences++
            val roman = romanTok.findAll(romanRaw.lowercase()).map { it.value }
                .filter { it.length in 2..24 }.toList()
            val gold = goldRaw.split(' ').map { it.trim(',', '?', '!', '।', '.', '"', '\'', ';', ':', ')', '(') }
                .filter { it.isNotEmpty() }
            // word alignment only when clean 1:1
            val goldClean = gold.filter { banglaOk.matches(it) }
            if (roman.size != gold.size || goldClean.size != gold.size || roman.isEmpty()) return@forEachLine
            seenPerSource[src] = seen + 1
            s.aligned++
            var all = true
            for (i in roman.indices) {
                val r = roman[i]; val g = gold[i]
                val tw = System.nanoTime()
                val out = engine.convertWord(r).bengali
                wordsTimed += (System.nanoTime() - tw)
                s.words++
                if (fold(out) == fold(g)) { s.wordExact++; s.top6++ }
                else {
                    all = false
                    val hit = engine.getSuggestions(r, 6).any { fold(it.bengali) == fold(g) }
                    if (hit) s.top6++
                    val cls = classify(r, out, g)
                    s.classes.merge(cls, 1, Int::plus)
                    failCounts.merge(Triple(r, out, g), 1, Int::plus)
                }
            }
            if (all) s.sentenceExact++
        }

        // behavior-only lines: passthrough + throughput
        var behWords = 0; var behBengali = 0; var behAscii = 0
        val passthrough = HashMap<String, Int>()
        val behCap = 2500
        val behSeen = HashMap<String, Int>()
        File(dir, "staged/lines.tsv").forEachLine { line ->
            val p = line.split('\t')
            if (p.size != 2) return@forEachLine
            val (src, text) = p
            val seen = behSeen.getOrDefault(src, 0)
            if (seen >= behCap) return@forEachLine
            behSeen[src] = seen + 1
            for (m in romanTok.findAll(text.lowercase())) {
                val r = m.value
                if (r.length !in 2..24) continue
                val out = engine.convertWord(r).bengali
                behWords++
                if (out.all { it.code < 128 }) { behAscii++; passthrough.merge(r, 1, Int::plus) }
                else behBengali++
            }
        }

        val ms = { n: Long -> n / 1_000_000 }
        val sb = StringBuilder()
        sb.appendLine("# Engine × Banglish corpora — behaviour & failure-pattern study (S149, 2026-08-30)")
        sb.appendLine()
        sb.appendLine("Datasets from the deep-research report; real dictionary (./dictionary.sqlite);")
        sb.appendLine("word-aligned pairs only (roman token count == gold token count, gold fully Bengali).")
        sb.appendLine("Comparison after nukta folding. top6 = gold reachable in the 6-slot strip.")
        sb.appendLine()
        for ((name, s) in perSource) {
            sb.appendLine("## $name")
            sb.appendLine("- sentences read: ${s.sentences}, word-aligned: ${s.aligned}")
            sb.appendLine("- word pairs: ${s.words}; word-exact: ${s.wordExact} (${"%.1f".format(100.0 * s.wordExact / maxOf(1, s.words))}%); gold-in-top6: ${s.top6} (${"%.1f".format(100.0 * s.top6 / maxOf(1, s.words))}%)")
            sb.appendLine("- sentence-exact (all words): ${s.sentenceExact} / ${s.aligned} (${"%.1f".format(100.0 * s.sentenceExact / maxOf(1, s.aligned))}%)")
            sb.appendLine("- failure classes:")
            for ((c, n) in s.classes.entries.sortedByDescending { it.value }) {
                sb.appendLine("    - $c: $n (${"%.1f".format(100.0 * n / maxOf(1, s.words - s.wordExact))}% of misses)")
            }
            sb.appendLine()
        }
        sb.appendLine("## Most frequent misses (all pair sources)")
        sb.appendLine()
        sb.appendLine("| n | typed | engine | gold |")
        sb.appendLine("|---|---|---|---|")
        for ((k, n) in failCounts.entries.sortedByDescending { it.value }.take(60)) {
            sb.appendLine("| $n | ${k.first} | ${k.second} | ${k.third} |")
        }
        sb.appendLine()
        sb.appendLine("## Behaviour-only corpora (Socian / Chaos / SMS / PolCSBD, no gold)")
        sb.appendLine("- tokens converted: $behWords; Bengali output: $behBengali (${"%.1f".format(100.0 * behBengali / maxOf(1, behWords))}%); ASCII passthrough: $behAscii (${"%.1f".format(100.0 * behAscii / maxOf(1, behWords))}%)")
        sb.appendLine("- top passthrough tokens (English law / URLs etc.):")
        for ((w, n) in passthrough.entries.sortedByDescending { it.value }.take(30)) {
            sb.appendLine("    - $w: $n")
        }
        sb.appendLine()
        val totalWords = perSource.values.sumOf { it.words }
        sb.appendLine("- mean convertWord time over ${totalWords} pair-words: ${"%.2f".format(wordsTimed / 1e6 / maxOf(1, totalWords))} ms")
        File(dir, "engine-banglish-study-2026-08-30.md").writeText(sb.toString())
        File(dir, "staged/failures.tsv").bufferedWriter().use { w ->
            for ((k, n) in failCounts.entries.sortedByDescending { it.value }) {
                w.write("$n\t${k.first}\t${k.second}\t${k.third}\n")
            }
        }
        println("S149 study written: ${File(dir, "engine-banglish-study-2026-08-30.md").absolutePath}; total wall ${ms(System.nanoTime() - t0)} ms")
    }
}
