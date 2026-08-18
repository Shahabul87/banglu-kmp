package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * S110: book/paper register study (user-collected corpus, 2026-08-17:
 * test-bangla.md at the repo root — full literary prose, ~1.5MB). The
 * chat-register studies (S82/S89/S109) covered what people TYPE; this one
 * covers what people READ and then try to type: the literary long tail —
 * tatsama vocabulary, heavy conjuncts, inflected forms, translation prose.
 *
 * Method: tokenize the text into unique Bengali words (nukta-folded,
 * occurrence-weighted), derive each word's canonical romanization via
 * ReverseTransliterator (what a knowledgeable typist types), and ask the
 * REAL full-store engine for primary + top-6.
 *
 * Buckets:
 *   in-dictionary words  — engine CAN know them: primary/top6 miss = real
 *                          ranking failure, classified by structure.
 *   out-of-vocabulary    — not in the 476K words table (rare/archaic/OCR
 *                          noise): reported separately; a rule-layer exact
 *                          reproduction still counts as a pass there.
 *
 * Skipped unless S110_STUDY=1:
 *   S110_STUDY=1 S110_MAX=60000 ./gradlew :shared:jvmTest \
 *     --tests "com.banglu.engine.S110BookWordsStudyJvm"
 * Optional: S110_FILE=/path/to/corpus.md (default: repo-root test-bangla.md)
 * Output: build/reports/s110-study/{summary.txt,misses_indict.tsv,oov.tsv}
 */
class S110BookWordsStudyJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun runStudy() {
        if (System.getenv("S110_STUDY") != "1") return
        val maxWords = System.getenv("S110_MAX")?.toIntOrNull() ?: 60000
        val outDir = File("build/reports/s110-study").apply { mkdirs() }

        val corpus = System.getenv("S110_FILE")?.let { File(it) }
            ?: listOf(File("test-bangla.md"), File("../test-bangla.md"))
                .firstOrNull { it.exists() }
            ?: error("test-bangla.md not found (set S110_FILE)")

        // ── tokenize: Bengali-only runs, fold nukta, count occurrences ──
        val counts = HashMap<String, Int>(1 shl 17)
        val bengaliRun = Regex("[\\u0980-\\u09FF]+")
        corpus.forEachLine { line ->
            for (m in bengaliRun.findAll(line)) {
                val w = ReverseTransliterator.foldNukta(m.value)
                    .trim('ঃ', '্') // stray visarga/hasanta from OCR splits
                if (w.length in 2..18) counts.merge(w, 1) { a, b -> a + b }
            }
        }

        // dictionary oracle (words table, nukta-folded — same as S89)
        val oracle = HashSet<String>(1 shl 20)
        DriverManager.getConnection(
            "jdbc:sqlite:${TestDictionaryLoader.findDictionarySqlite().absolutePath}"
        ).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali FROM words").use { rs ->
                    while (rs.next()) oracle.add(ReverseTransliterator.foldNukta(rs.getString(1)))
                }
            }
        }

        val unique = counts.entries.sortedByDescending { it.value }.take(maxWords)
        println("S110 corpus=${corpus.name} tokens=${counts.values.sum()} unique=${counts.size} studied=${unique.size}")

        data class Bucket(var n: Int = 0, var wN: Long = 0, var primary: Int = 0, var top6: Int = 0)

        val inDict = Bucket(); val oov = Bucket()
        val classBuckets = LinkedHashMap<String, Bucket>()
        fun classify(w: String): List<String> = buildList {
            if ('্' in w) add("conjunct") else add("no_conjunct")
            if (w.first() in "অআইঈউঊএঐওঔ") add("vowel_initial")
            if ('ঁ' in w) add("chandrabindu")
            if ('ৎ' in w) add("khanda_ta")
            if (w.length >= 10) add("long10plus")
        }

        val missIndict = File(outDir, "misses_indict.tsv").bufferedWriter()
        missIndict.write("key\texpected\tgot\tcount\tin_top6\tclasses\n")
        val oovOut = File(outDir, "oov.tsv").bufferedWriter()
        oovOut.write("key\texpected\tgot\tcount\trule_pass\n")
        var unromanizable = 0

        for ((word, count) in unique) {
            val key = runCatching { ReverseTransliterator.reverseWord(word).lowercase() }
                .getOrNull() ?: ""
            if (key.isEmpty() || key.length > 24 || !key.all { it in 'a'..'z' }) {
                unromanizable++
                continue
            }
            val isDict = word in oracle
            val got = ReverseTransliterator.foldNukta(engine.convertWord(key).bengali)
            val primary = got == word
            val top6 = primary || engine.getSuggestions(key, 6)
                .any { ReverseTransliterator.foldNukta(it.bengali) == word }

            val b = if (isDict) inDict else oov
            b.n++; b.wN += count
            if (primary) b.primary++
            if (top6) b.top6++

            if (isDict) {
                val classes = classify(word)
                for (c in classes) {
                    val cb = classBuckets.getOrPut(c) { Bucket() }
                    cb.n++; if (primary) cb.primary++; if (top6) cb.top6++
                }
                if (!top6) missIndict.write("$key\t$word\t$got\t$count\t$top6\t${classes.joinToString(",")}\n")
            } else if (!primary) {
                oovOut.write("$key\t$word\t$got\t$count\tfalse\n")
            }
        }
        missIndict.close(); oovOut.close()

        val s = StringBuilder()
        fun line(name: String, b: Bucket) {
            if (b.n == 0) return
            s.append(
                "%-14s n=%-6d primary=%5.1f%% top6=%5.1f%%\n"
                    .format(name, b.n, 100.0 * b.primary / b.n, 100.0 * b.top6 / b.n)
            )
        }
        s.append("== populations ==\n")
        line("in_dictionary", inDict); line("oov", oov)
        s.append("unromanizable=$unromanizable\n\n== in-dictionary by class ==\n")
        for ((k, b) in classBuckets.entries.sortedByDescending { it.value.n }) line(k, b)
        File(outDir, "summary.txt").writeText(s.toString())
        println(s)
    }
}
