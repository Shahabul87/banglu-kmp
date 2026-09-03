package com.banglu.engine
import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
/** S176 study: preview-vs-commit parity on INFLECTED keys (the screenshot class). Opt-in: S176_STUDY=1. */
class S176InflectionParityStudyJvm {
    @Test fun run() {
        if (System.getenv("S176_STUDY") != "1") return
        val tag = System.getenv("S176_TAG") ?: "run"
        val e = ConjunctSolutionRoundJvmTest.engine
        val db = TestDictionaryLoader.findDictionarySqlite()
        val stems = ArrayList<String>(); val loans = ArrayList<String>()
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().executeQuery("SELECT bengali FROM words ORDER BY frequency DESC LIMIT 3000").use { r -> while (r.next()) stems.add(ReverseTransliterator.foldNukta(r.getString(1))) }
            c.createStatement().executeQuery("SELECT key FROM english_lexicon").use { r -> while (r.next()) { val k = r.getString(1); if (k.length in 4..12 && k.all { it in 'a'..'z' } && com.banglu.engine.ai.EnglishDetector.isCommonEnglishWord(k)) loans.add(k) } }
        }
        val bnSuffix = listOf("er","e","te","ke","ra","der","gulo","ta","o","i")
        val enSuffix = listOf("er","e","te","ta","gulo")
        val keys = LinkedHashSet<String>()
        for (w in stems) { val r = ReverseTransliterator.reverseWord(w).lowercase(); if (r.isNotBlank() && r.all { it in 'a'..'z' } && r.length in 3..18) for (s in bnSuffix) keys.add(r + s) }
        for (k in loans.take(2500)) for (s in enSuffix) keys.add(k + s)
        val out = File("build/reports/s176-study").apply { mkdirs() }
        val rows = File(out, "mismatches-$tag.tsv").bufferedWriter()
        var tested = 0; var mism = 0; var garbage = 0; val bySrc = HashMap<String, Int>()
        for (key in keys) {
            tested++
            val commit = e.convertWord(key); val comp = e.convertForComposing(key)
            if (ReverseTransliterator.foldNukta(commit.bengali) != ReverseTransliterator.foldNukta(comp.bengali)) {
                mism++; bySrc.merge("${comp.source}->${commit.source}", 1, Int::plus)
                if (comp.source == com.banglu.engine.types.ResolutionSource.CLEAN_TRANSLITERATION || comp.source == com.banglu.engine.types.ResolutionSource.RULE) garbage++
                rows.write("$key\t${comp.bengali}\t${comp.source}\t${commit.bengali}\t${commit.source}\t${commit.confidence}\n")
            }
        }
        rows.close()
        val s = buildString {
            appendLine("S176 INFLECTION PARITY [$tag] keys=$tested (bn stems=${stems.size}, en loans=${minOf(loans.size,2500)}) mismatches=$mism ruleFloorGarbage=$garbage")
            bySrc.entries.sortedByDescending { it.value }.take(12).forEach { appendLine("  ${it.key}: ${it.value}") }
        }
        File(out, "summary-$tag.txt").writeText(s); println(s)
    }
}
