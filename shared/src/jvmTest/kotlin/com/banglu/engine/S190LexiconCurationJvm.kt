package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import java.text.Normalizer
import kotlin.test.Test

/**
 * S190 (dictionary round from the S188 study): curate the harvested proper
 * nouns and science terms into supplemental lexicon files for the compiler.
 * Opt-in:
 *
 *   S190_CURATE=1 S190_DIR=/path/to/harvest S190_OUT=dictionary-compiler/data \
 *     ./gradlew :shared:jvmTest --tests "com.banglu.engine.S190LexiconCurationJvm"
 *
 * Gates (a candidate must pass every one):
 *  - Bengali letters and signs only, 2–18 chars, no leading/trailing হসন্ত,
 *    no OCR-split vowel sign (া immediately followed by another vowel sign);
 *  - not already in the dictionary's words table (nukta-folded, NFC);
 *  - a Wikipedia TITLE token is accepted as a fact; a BODY word needs ≥ 5
 *    occurrences in its category corpus;
 *  - the real engine does not already turn its canonical roman into a
 *    dictionary word: when it does, the harvest spelling is a variant of a
 *    word the engine normalises (মাদারিপুর → মাদারীপুর) and adding it would
 *    let the variant win its own exact key — excluded, listed for review;
 *  - not a bare inflection the engine already builds from a known stem
 *    (commit == word).
 * Output: <out>/proper_nouns.tsv, <out>/science_glossary.tsv (word<TAB>count,
 * count capped at 40 so every row stays tier B), plus <out>/s190-review.tsv.
 */
class S190LexiconCurationJvm {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    private fun fold(s: String) = ReverseTransliterator.foldNukta(Normalizer.normalize(s, Normalizer.Form.NFC))
    private val bengaliOnly = Regex("^[\\u0985-\\u09B9\\u09BC-\\u09C4\\u09C7-\\u09C8\\u09CB-\\u09CD\\u09CE\\u09D7\\u09DC-\\u09DF\\u0981-\\u0983]+$")
    private val ocrSplit = Regex("\\u09BE[\\u09BE-\\u09C4\\u09C7\\u09C8\\u09CB\\u09CC]|[\\u09C7\\u09C8]\\u09BE|\\u09CD\\u09CD|\\u0981\\u0981")

    @Test
    fun curate() {
        if (System.getenv("S190_CURATE") != "1") return
        val dir = File(System.getenv("S190_DIR") ?: error("S190_DIR"))
        val out = File(System.getenv("S190_OUT") ?: "dictionary-compiler/data").apply { mkdirs() }
        val oracle = HashSet<String>(1 shl 20)
        DriverManager.getConnection("jdbc:sqlite:${TestDictionaryLoader.findDictionarySqlite().absolutePath}").use { c ->
            c.createStatement().use { st -> st.executeQuery("SELECT bengali FROM words").use { rs -> while (rs.next()) oracle.add(fold(rs.getString(1))) } }
        }
        data class Cand(val word: String, var count: Int, val sources: MutableSet<String>, var title: Boolean)
        val cands = LinkedHashMap<String, Cand>()
        fun load(name: String, category: String, isTitle: Boolean, minCount: Int) {
            val f = File(dir, name); if (!f.exists()) { println("S190 missing $name"); return }
            f.forEachLine { line ->
                val t = line.split('\t'); if (t.size < 2) return@forEachLine
                val w = fold(t[0]).trim(); val n = t[1].toIntOrNull() ?: return@forEachLine
                if (!isTitle && n < minCount) return@forEachLine
                if (w.length !in 2..18 || !bengaliOnly.matches(w) || w.startsWith("্") || w.endsWith("্") || ocrSplit.containsMatchIn(w)) return@forEachLine
                if (w in oracle) return@forEachLine
                val c = cands.getOrPut(w) { Cand(w, 0, mutableSetOf(), false) }
                c.count = maxOf(c.count, n); c.sources += category; if (isTitle) c.title = true
            }
        }
        for (cat in listOf("people", "places", "unions", "science", "objects")) load("${cat}_titles.tsv", cat, isTitle = true, minCount = 1)
        for (cat in listOf("people", "places", "unions", "science", "objects", "literature")) load("${cat}_counts.tsv", cat, isTitle = false, minCount = 5)
        load("news_counts.tsv", "news", isTitle = false, minCount = 5)
        println("S190 candidates after static gates: ${cands.size}")
        val proper = File(out, "proper_nouns.tsv").bufferedWriter()
        val science = File(out, "science_glossary.tsv").bufferedWriter()
        val review = File(out, "s190-review.tsv").bufferedWriter()
        review.write("word\tcount\tsources\treason\tengine\n")
        var kept = 0; var normalized = 0; var already = 0; var unromanizable = 0; var keptScience = 0
        for (c in cands.values) {
            val key = runCatching { ReverseTransliterator.reverseWord(c.word).replace("N", "").lowercase() }.getOrNull() ?: ""
            if (key.isEmpty() || key.length > 26 || !key.all { it in 'a'..'z' }) { unromanizable++; continue }
            val commit = fold(engine.convertWord(key).bengali)
            when {
                commit == c.word -> { already++; review.write("${c.word}\t${c.count}\t${c.sources.joinToString("+")}\talready_resolves\t$commit\n") }
                commit in oracle -> { normalized++; review.write("${c.word}\t${c.count}\t${c.sources.joinToString("+")}\tnormalized_variant\t$commit\n") }
                else -> {
                    val freq = minOf(c.count, 40)
                    if (c.sources.size == 1 && "science" in c.sources) { science.write("${c.word}\t$freq\n"); keptScience++ } else { proper.write("${c.word}\t$freq\n") }
                    kept++
                }
            }
        }
        proper.close(); science.close(); review.close()
        println("S190 kept=$kept (science=$keptScience) already_resolves=$already normalized_variant=$normalized unromanizable=$unromanizable")
    }
}
