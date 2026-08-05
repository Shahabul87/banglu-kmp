package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * S82: corpus-scale suggestion-quality audit (user directive 2026-08-05:
 * "test the engine with at least 100k words and see why it produces
 * garbage — then find a general solution").
 *
 * For every high-usage corpus word, simulates the spellings real typists
 * use (canonical romanization + the known habit transforms), runs the full
 * conversion + suggestion pipeline, and audits every strip entry against
 * the corpus oracle. Garbage is attributed to the generator channel that
 * produced it so the fix lands at mechanism level.
 *
 * Not a pass/fail wall test — skipped unless S82_STUDY=1:
 *   S82_STUDY=1 S82_MAX=100000 ./gradlew :shared:jvmTest \
 *     --tests "com.banglu.engine.S82SuggestionQualityStudyJvm"
 * Output: build/reports/s82-study/{summary.txt,garbage.tsv,misses.tsv}
 */
class S82SuggestionQualityStudyJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun runStudy() {
        if (System.getenv("S82_STUDY") != "1") return
        val maxWords = System.getenv("S82_MAX")?.toIntOrNull() ?: 100_000
        val outDir = File("build/reports/s82-study").apply { mkdirs() }

        val dbFile = TestDictionaryLoader.findDictionarySqlite()
        val oracle = HashSet<String>(1 shl 20)
        val studyWords = ArrayList<Pair<String, Int>>(maxWords)
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali, frequency FROM words").use { rs ->
                    while (rs.next()) {
                        val w = ReverseTransliterator.foldNukta(rs.getString(1))
                        oracle.add(w)
                        val f = rs.getInt(2)
                        if (f >= 60) studyWords.add(w to f)
                    }
                }
            }
        }
        studyWords.sortByDescending { it.second }
        val words = studyWords.take(maxWords)
        println("S82 oracle=${oracle.size} studyWords=${words.size}")

        fun isRealText(s: String): Boolean {
            if (s.any { it in 'a'..'z' || it in 'A'..'Z' }) return true // english/latin: not our audit
            return s.split(' ').all { part -> part.isEmpty() || ReverseTransliterator.foldNukta(part) in oracle }
        }

        // The habit spellings typists actually produce, applied to the
        // canonical romanization (mirrors the compiler's rule families).
        fun typedVariants(canonical: String): List<Pair<String, String>> {
            val out = LinkedHashMap<String, String>()
            fun add(name: String, v: String) {
                if (v != canonical && v.length >= 2 && v !in out.values) out[name] = v
            }
            add("sh_s", canonical.replace("sh", "s"))
            add("chh_c", canonical.replace("chh", "c"))
            add("ph_f", canonical.replace("ph", "f"))
            add("o_drop", canonical.replace("ole", "le").replace("olo", "lo")
                .replace("ola", "la").replace("obo", "bo").replace("obe", "be").replace("ote", "te"))
            add("dbl_single", canonical.replace(Regex("([bcdfghjklmnpqrstvwxyz])\\1"), "$1"))
            return out.entries.take(3).map { it.key to it.value }
        }

        val garbageBySource = HashMap<String, Int>()
        val garbageSamples = File(outDir, "garbage.tsv").bufferedWriter()
        garbageSamples.write("key\tword\tgarbage\tsource\trank\n")
        val misses = File(outDir, "misses.tsv").bufferedWriter()
        misses.write("key\tvariant\tword\tprimary\ttop5\n")

        var keysTested = 0
        var primaryHit = 0
        var inTop5 = 0
        var totalSuggestions = 0
        var garbageCount = 0
        var garbageStrips = 0
        var composingMismatch = 0

        for ((word, _) in words) {
            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (canonical.isBlank() || !canonical.all { it in 'a'..'z' }) continue
            val variants = listOf("canonical" to canonical) + typedVariants(canonical)
            for ((variantName, key) in variants) {
                if (key.length !in 2..24) continue
                keysTested++
                val primary = engine.convertWord(key)
                val sugg = engine.getSuggestions(key, 6)
                val folded = sugg.map { ReverseTransliterator.foldNukta(it.bengali) }
                val pOk = ReverseTransliterator.foldNukta(primary.bengali) == word
                if (pOk) primaryHit++
                if (word in folded) inTop5++
                else if (variantName == "canonical") {
                    misses.write("$key\t$variantName\t$word\t${primary.bengali}\t${folded.take(5).joinToString("|")}\n")
                }
                var stripHadGarbage = false
                sugg.forEachIndexed { rank, s ->
                    totalSuggestions++
                    if (rank > 0 && !isRealText(s.bengali)) {
                        garbageCount++
                        stripHadGarbage = true
                        garbageBySource.merge(s.source, 1, Int::plus)
                        if (garbageCount <= 4000) {
                            garbageSamples.write("$key\t$word\t${s.bengali}\t${s.source}\t$rank\n")
                        }
                    }
                }
                if (stripHadGarbage) garbageStrips++
                if (variantName == "canonical") {
                    val comp = engine.convertForComposing(key)
                    if (ReverseTransliterator.foldNukta(comp.bengali) !=
                        ReverseTransliterator.foldNukta(primary.bengali)
                    ) composingMismatch++
                }
            }
            if (keysTested % 20000 < variants.size) println("S82 progress keys=$keysTested")
        }
        garbageSamples.close()
        misses.close()

        val summary = buildString {
            appendLine("S82 SUGGESTION QUALITY STUDY — db=${dbFile.name}")
            appendLine("words=${words.size} keysTested=$keysTested")
            appendLine("primaryHit=$primaryHit (${pct(primaryHit, keysTested)})")
            appendLine("intendedInTop6=$inTop5 (${pct(inTop5, keysTested)})")
            appendLine("composingMismatch(canonical)=$composingMismatch")
            appendLine("suggestions=$totalSuggestions garbage=$garbageCount (${pct(garbageCount, totalSuggestions)})")
            appendLine("stripsWithGarbage=$garbageStrips (${pct(garbageStrips, keysTested)})")
            appendLine("garbage by source:")
            garbageBySource.entries.sortedByDescending { it.value }.forEach {
                appendLine("  ${it.key}: ${it.value}")
            }
        }
        File(outDir, "summary.txt").writeText(summary)
        println(summary)
    }

    private fun pct(n: Int, d: Int) = if (d == 0) "0%" else "${(n * 1000 / d) / 10.0}%"
}
