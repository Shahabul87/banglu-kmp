package com.banglu.engine
import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
/** S177 study: extended-dictionary keys that are the exact roman of an attested index word but map to a LONGER completion (hrid → হৃদয় over হৃদ). Opt-in: S177_STUDY=1. */
class S177CompletionStudyJvm {
    @Test fun run() {
        if (System.getenv("S177_STUDY") != "1") return
        val tag = System.getenv("S177_TAG") ?: "run"
        val e = ConjunctSolutionRoundJvmTest.engine
        val db = TestDictionaryLoader.findDictionarySqlite()
        data class Row(val key: String, val completion: String, val literal: String, val litFreq: Int)
        val rows = ArrayList<Row>()
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            val sql = """
                SELECT p.phonetic, d.bengali, d.frequency FROM extended_phonetics p JOIN extended_dictionary d ON d.id = p.entry_id
                WHERE length(p.phonetic) BETWEEN 3 AND 12
            """.trimIndent()
            val cand = ArrayList<Triple<String,String,Int>>()
            c.createStatement().executeQuery(sql).use { r -> while (r.next()) cand.add(Triple(r.getString(1), r.getString(2), r.getInt(3))) }
            val st = c.prepareStatement("SELECT w.bengali, p.frequency, p.tier FROM phonetic_index p JOIN words w ON w.id = p.word_id WHERE p.key = ? ORDER BY p.tier, p.priority, p.frequency DESC LIMIT 3")
            for ((key, w, _) in cand) {
                if (!key.all { it in 'a'..'z' }) continue
                val own = ReverseTransliterator.reverseWord(w).lowercase().replace("rri", "ri")
                if (own.length <= key.length || !own.startsWith(key)) continue      // completion shape only
                st.setString(1, key)
                val hits = st.executeQuery().use { r -> generateSequence { if (r.next()) Triple(r.getString(1), r.getInt(2), r.getInt(3)) else null }.toList() }
                val top = hits.firstOrNull() ?: continue
                if (top.third != 0 || top.second < 30 || top.first == w) continue
                if (hits.any { it.first == w }) continue
                rows.add(Row(key, w, top.first, top.second))
            }
        }
        val out = File("build/reports/s177-study").apply { mkdirs() }
        val tsv = File(out, "commits-$tag.tsv").bufferedWriter()
        var literal = 0; var completion = 0; var other = 0
        for (r in rows) {
            val got = ReverseTransliterator.foldNukta(e.convertWord(r.key).bengali)
            val cls = when (got) { ReverseTransliterator.foldNukta(r.literal) -> { literal++; "LITERAL" }; ReverseTransliterator.foldNukta(r.completion) -> { completion++; "COMPLETION" }; else -> { other++; "OTHER" } }
            tsv.write("${r.key}\t${r.literal}@${r.litFreq}\t${r.completion}\t$got\t$cls\n")
        }
        tsv.close()
        val s = "S177 COMPLETION CLASS [$tag] keys=${rows.size} commit=literal:$literal completion:$completion other:$other\n"
        File(out, "summary-$tag.txt").writeText(s); println(s)
    }
}
