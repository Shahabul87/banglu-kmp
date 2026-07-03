package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.util.ReverseTransliterator
import kotlinx.coroutines.runBlocking
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * Corpus-scale engine behavior study focused on complex conjuncts and
 * similar-sounding confusable typings (ri vs ri-kar, conjunct clusters).
 *
 * Not a pass/fail test: it measures the production-equivalent engine against
 * a real-web-text corpus and writes TSV results for offline analysis.
 *
 * Skipped unless STUDY_INPUT is set:
 *   STUDY_INPUT=/path/merged_counts.tsv  (word<TAB>count, desc by count)
 *   STUDY_OUT=/path/out_dir
 *   STUDY_MIN_COUNT=5        minimum corpus count to include a word
 *   STUDY_MAX_WORDS=200000   cap on words tested
 *
 * Run: ./gradlew :shared:jvmTest --tests "com.banglu.engine.ConjunctCorpusStudyJvm"
 */
class ConjunctCorpusStudyJvm {

    private data class CorpusWord(val word: String, val count: Int)

    @Test
    fun runStudy() {
        val inputPath = System.getenv("STUDY_INPUT") ?: return // not a CI test
        val outDir = File(System.getenv("STUDY_OUT") ?: "build/reports/conjunct-study")
        val minCount = System.getenv("STUDY_MIN_COUNT")?.toIntOrNull() ?: 5
        val maxWords = System.getenv("STUDY_MAX_WORDS")?.toIntOrNull() ?: 200_000
        outDir.mkdirs()

        val dbFile = TestDictionaryLoader.findDictionarySqlite()
        val engine = buildEngine(dbFile)
        val store = loadStore(dbFile)
        engine.setPhoneticIndex(store)

        val corpus = loadCorpus(File(inputPath), minCount, maxWords, store)
        log("corpus loaded: ${corpus.size} in-dictionary words (minCount=$minCount)")

        val results = File(outDir, "results.tsv").bufferedWriter()
        results.write("word\tcount\tvariant\tinput\tprimary\tprimaryOk\trank\tkeyState\ttop5\n")

        var done = 0
        for (cw in corpus) {
            val canonical = ReverseTransliterator.reverseWord(cw.word)
            if (canonical.isBlank() || !canonical.all { it.isLetter() || it == '`' }) continue
            val variants = buildList {
                add("canonical" to canonical)
                if (isConjunctWord(cw.word) && cw.count >= 10) {
                    lazyVariants(canonical).forEach { (name, typed) ->
                        if (typed != canonical) add(name to typed)
                    }
                }
            }
            for ((variantName, input) in variants) {
                // Compare nukta-folded on both sides: the engine can emit
                // decomposed য়/ড়/ঢ় forms that render identically to the target.
                val primary = ReverseTransliterator.foldNukta(engine.convertWord(input).bengali)
                val primaryOk = primary == cw.word
                // Suggestions are the expensive call: only where analysis needs rank.
                val needSugg = !(primaryOk && variantName == "canonical" && !isConjunctWord(cw.word))
                val sugg = if (needSugg) {
                    engine.getSuggestions(input, 10).map { ReverseTransliterator.foldNukta(it.bengali) }
                } else emptyList()
                val r = sugg.indexOf(cw.word)
                val rank = if (!needSugg || (primaryOk && r == -1)) 0 else if (r == -1) -1 else r + 1
                val keyState = keyState(store.lookupExact(input), cw.word)
                val top5 = sugg.take(5).joinToString("|")
                results.write(
                    "${cw.word}\t${cw.count}\t$variantName\t$input\t$primary\t$primaryOk\t$rank\t$keyState\t$top5\n"
                )
            }
            done++
            if (done % 5000 == 0) {
                log("progress: $done/${corpus.size}")
                results.flush()
            }
        }
        results.close()

        // ri-focus composing trace: what the preview shows at every keystroke.
        val riWords = corpus.filter { it.word.contains("রি") || it.word.contains('ৃ') }
            .sortedByDescending { it.count }
            .take(800)
        File(outDir, "ri_composing_trace.tsv").bufferedWriter().use { w ->
            w.write("word\tcount\tinput\tprefixOutputs\triKarLeak\n")
            for (cw in riWords) {
                val input = ReverseTransliterator.reverseWord(cw.word)
                if (input.isBlank()) continue
                val steps = (1..input.length).map { n ->
                    engine.convertWord(input.take(n)).bengali
                }
                // leak = a prefix preview shows ri-kar while the target uses রি
                val leak = cw.word.contains("রি") && !cw.word.contains('ৃ') &&
                    steps.any { it.contains('ৃ') }
                w.write("${cw.word}\t${cw.count}\t$input\t${steps.joinToString("|")}\t$leak\n")
            }
        }
        log("study complete → ${outDir.absolutePath}")
    }

    // ── engine + store ──────────────────────────────────────────────────

    private fun buildEngine(dbFile: File): SmartEngine =
        SmartEngine().also { eng ->
            eng.initializeSync()
            runBlocking { eng.initialize(storage = null, loader = TestDictionaryLoader(dbFile)) }
        }

    private fun loadStore(dbFile: File): InMemoryPhoneticIndexStore {
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val entries = ArrayList<Pair<PhoneticIndexHit, String>>(1_100_000)
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT p.key, w.bengali, p.frequency, p.tier, p.priority
                       FROM phonetic_index p JOIN words w ON w.id = p.word_id"""
                ).use { rs ->
                    while (rs.next()) {
                        entries.add(
                            PhoneticIndexHit(
                                rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)
                            ) to rs.getString(1)
                        )
                    }
                }
            }
            val english = HashMap<String, String>()
            conn.createStatement().use { st ->
                st.executeQuery("SELECT key, bengali FROM english_lexicon").use { rs ->
                    while (rs.next()) english[rs.getString(1)] = rs.getString(2)
                }
            }
            val words = HashSet<String>(600_000)
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali FROM words").use { rs ->
                    while (rs.next()) words.add(rs.getString(1))
                }
            }
            log("store: ${entries.size} index rows, ${english.size} english, ${words.size} words")
            return InMemoryPhoneticIndexStore(entries, english, words)
        }
    }

    private fun loadCorpus(
        file: File,
        minCount: Int,
        maxWords: Int,
        store: InMemoryPhoneticIndexStore
    ): List<CorpusWord> {
        val out = ArrayList<CorpusWord>(maxWords)
        file.forEachLine { line ->
            if (out.size >= maxWords) return@forEachLine
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEachLine
            val raw = line.substring(0, tab)
            val count = line.substring(tab + 1).trim().toIntOrNull() ?: return@forEachLine
            if (count < minCount) return@forEachLine
            val word = ReverseTransliterator.foldNukta(raw)
            // Corpus contains OCR noise and archaic forms; the dictionary is the
            // spelling authority per the study design — only test in-dict words.
            if (word.length in 2..20 && store.containsWord(word)) {
                out.add(CorpusWord(word, count))
            }
        }
        return out
    }

    // ── typing variants ─────────────────────────────────────────────────

    private fun isConjunctWord(w: String): Boolean =
        w.contains('্') || w.contains('ৃ') || w.contains('ঋ')

    /** Common lazy typings applied to the canonical romanization. */
    private fun lazyVariants(c: String): List<Pair<String, String>> = buildList {
        if (c.contains("sh")) add("h_lazy" to c.replace("sh", "s"))
        val degem = c.replace(Regex("([bcdghjklmnprst])\\1"), "$1")
        if (degem != c) add("degeminate" to degem)
        if (c.contains("w")) add("w_drop" to c.replace("w", ""))
        if (c.contains("y")) add("y_drop" to c.replace("y", ""))
        if (c.contains("rri")) add("rri_as_ri" to c.replace("rri", "ri"))
    }

    private fun keyState(hits: List<PhoneticIndexHit>, target: String): String = when {
        hits.isEmpty() -> "key_absent"
        hits.none { it.bengali == target } -> "key_other_owner"
        hits.firstOrNull()?.bengali == target -> "key_first"
        else -> "key_outranked"
    }

    private fun log(msg: String) = println("[conjunct-study] $msg")
}
