package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import kotlin.test.Test

/**
 * S157 scratch probe (tutorial redesign research): runs the curated vowel
 * tutorial candidates through the REAL engine and reports top1/top6 per
 * variant. Opt-in: BANGLU_S157_PROBE=1 + BANGLU_S157_TSV=<tsv>.
 * Rows: vowel<TAB>bengali<TAB>variant<TAB>syllable+split. Also asserts the
 * split concatenation equals the variant (display honesty).
 */
class S157VowelProbeJvm {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun probe() {
        if (System.getenv("BANGLU_S157_PROBE") != "1") { println("S157 probe skipped"); return }
        val tsv = File(System.getenv("BANGLU_S157_TSV")!!)
        var top1 = 0; var top6 = 0; var miss = 0
        tsv.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val p = line.split("\t")
            val (vowel, bengali, variant, split) = listOf(p[0], p[1], p[2], p[3])
            if (split.replace("+", "") != variant)
                println("SPLIT-MISMATCH\t$vowel\t$bengali\t$variant\t$split")
            val res = engine.convertWord(variant)
            val primary = fold(res.bengali)
            val want = fold(bengali)
            val sugg = engine.getSuggestions(variant, 6).map { fold(it.bengali) }
            when {
                primary == want -> { top1++; println("TOP1\t$vowel\t$bengali\t$variant") }
                want in sugg -> { top6++; println("TOP6\t$vowel\t$bengali\t$variant\tprimary=$primary") }
                else -> { miss++; println("MISS\t$vowel\t$bengali\t$variant\tprimary=$primary\tstrip=${sugg.joinToString(",")}") }
            }
        }
        println("S157 SUMMARY top1=$top1 top6=$top6 miss=$miss")
    }
}
