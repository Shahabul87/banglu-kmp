package com.banglu.engine
import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
/**
 * S181 study: dump the commit result for every dictionary key (freq ≥ 60, as
 * S83), the two top-1,000 device lists, and the S176 inflection keys, so a
 * wrapper change can be diffed word by word. Opt-in: S181_DUMP=<out.tsv>.
 */
class S181CommitDumpJvm {
    @Test fun dump() {
        val out = System.getenv("S181_DUMP") ?: return
        val e = ConjunctSolutionRoundJvmTest.engine
        val db = TestDictionaryLoader.findDictionarySqlite()
        val keys = LinkedHashSet<String>()
        val stems = ArrayList<String>(); val loans = ArrayList<String>()
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().executeQuery("SELECT bengali FROM words WHERE frequency >= 60").use { r -> while (r.next()) {
                val k = ReverseTransliterator.reverseWord(ReverseTransliterator.foldNukta(r.getString(1))).lowercase()
                if (k.isNotBlank() && k.all { it in 'a'..'z' } && k.length in 2..24) keys.add(k)
            } }
            c.createStatement().executeQuery("SELECT bengali FROM words ORDER BY frequency DESC LIMIT 3000").use { r -> while (r.next()) stems.add(ReverseTransliterator.foldNukta(r.getString(1))) }
            c.createStatement().executeQuery("SELECT key FROM english_lexicon").use { r -> while (r.next()) { val k = r.getString(1); if (k.length in 4..12 && k.all { it in 'a'..'z' } && com.banglu.engine.ai.EnglishDetector.isCommonEnglishWord(k)) loans.add(k) } }
        }
        val dir = File("../docs/audits/audit-android-closed-testing-v1.5.105-2026-09-01/top1000").takeIf { it.isDirectory } ?: File("docs/audits/audit-android-closed-testing-v1.5.105-2026-09-01/top1000")
        for (f in listOf("top1000-all.tsv", "top1000-conjunct.tsv")) File(dir, f).readLines().forEach { l -> l.split("\t").getOrNull(1)?.let { if (it.isNotBlank()) keys.add(it) } }
        for (w in stems) { val r = ReverseTransliterator.reverseWord(w).lowercase(); if (r.isNotBlank() && r.all { it in 'a'..'z' } && r.length in 3..18) for (s in listOf("er","e","te","ke","ra","der","gulo","ta","o","i")) keys.add(r + s) }
        for (k in loans.take(2500)) for (s in listOf("er","e","te","ta","gulo")) keys.add(k + s)
        File(out).bufferedWriter().use { w ->
            for (k in keys) { val r = e.convertWord(k); w.write("$k\t${r.bengali}\t${r.source}\t${"%.2f".format(r.confidence)}\n") }
        }
        println("S181 dump keys=${keys.size} -> $out")
    }
    /** The two top-1,000 device lists against the current engine. */
    @Test fun top1000Oracle() {
        if (System.getenv("S181_DUMP") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        val dir = File("../docs/audits/audit-android-closed-testing-v1.5.105-2026-09-01/top1000").takeIf { it.isDirectory } ?: File("docs/audits/audit-android-closed-testing-v1.5.105-2026-09-01/top1000")
        for (f in listOf("top1000-all.tsv", "top1000-conjunct.tsv")) {
            var ok = 0; var n = 0
            File(dir, f).readLines().forEach { l -> val p = l.split("\t"); if (p.size >= 2 && p[1].isNotBlank()) { n++; if (ReverseTransliterator.foldNukta(e.convertWord(p[1]).bengali) == ReverseTransliterator.foldNukta(p[0])) ok++ } }
            println("S181 ORACLE $f exact=$ok/$n")
        }
    }
}
