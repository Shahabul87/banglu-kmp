package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import kotlin.test.Test

/**
 * S157 bulk research probe: every mined candidate word is reverse-
 * transliterated to its canonical roman and run through the REAL engine.
 * Output TSV: letter, bengali, freq, roman, verdict(TOP1/TOP6/MISS), primary.
 * Opt-in: BANGLU_S157_BULK=1, BANGLU_S157_CANDIDATES, BANGLU_S157_OUT.
 */
class S157BulkProbeJvm {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun bulk() {
        if (System.getenv("BANGLU_S157_BULK") != "1") { println("S157 bulk skipped"); return }
        val out = File(System.getenv("BANGLU_S157_OUT")!!)
        var top1 = 0; var top6 = 0; var miss = 0; var skipped = 0
        out.bufferedWriter().use { w ->
            File(System.getenv("BANGLU_S157_CANDIDATES")!!).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val p = line.split("\t")
                if (p.size < 3) return@forEachLine
                val (letter, bengali, freq) = listOf(p[0], p[1], p[2])
                val roman = try { ReverseTransliterator.reverseWord(bengali) } catch (t: Throwable) { "" }
                if (roman.isBlank() || roman.any { it !in 'a'..'z' }) { skipped++; return@forEachLine }
                val want = fold(bengali)
                val primary = fold(engine.convertWord(roman).bengali)
                val verdict = when {
                    primary == want -> { top1++; "TOP1" }
                    want in engine.getSuggestions(roman, 6).map { fold(it.bengali) } -> { top6++; "TOP6" }
                    else -> { miss++; "MISS" }
                }
                w.write("$letter\t$bengali\t$freq\t$roman\t$verdict\t$primary\n")
            }
        }
        println("S157 BULK SUMMARY top1=$top1 top6=$top6 miss=$miss skipped=$skipped -> ${out.absolutePath}")
    }
}
