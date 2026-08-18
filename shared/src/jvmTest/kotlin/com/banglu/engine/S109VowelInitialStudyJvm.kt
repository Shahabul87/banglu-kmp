package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * S109: vowel-initial words mega study (user report 2026-08-17: "engine finds
 * first vowel words confusing — typing aw for the অ sound doesn't produce it
 * first; typing a could be অ or আ and the engine should detect from what
 * follows").
 *
 * Three measured populations over the REAL store:
 *  1. canonical  — every tier-A canonical key whose owner starts with a
 *     Bengali vowel (অ আ ই ঈ উ ঊ এ ঐ ও ঔ): does typing the canonical
 *     romanization put the owner at primary / in the top-6?
 *  2. aw_variant — অ-initial owners whose key starts with "o", retyped with
 *     the chat "aw" onset (onek→awnek, oto→awto): recon showed the index has
 *     ZERO aw-keys, so this class free-falls today.
 *  3. a_variant  — the same অ-initial keys retyped with a bare "a" onset
 *     (onek→anek): the "a could be অ or আ" ambiguity class.
 *
 * Skipped unless S109_STUDY=1:
 *   S109_STUDY=1 S109_MAX=20000 ./gradlew :shared:jvmTest \
 *     --tests "com.banglu.engine.S109VowelInitialStudyJvm"
 * Output: build/reports/s109-study/{summary.txt,misses_*.tsv}
 *
 * Quick probe (a handful of words, prints current behavior):
 *   S109_PROBE=1 ./gradlew :shared:jvmTest \
 *     --tests "com.banglu.engine.S109VowelInitialStudyJvm"
 */
class S109VowelInitialStudyJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    private val vowels = setOf('অ', 'আ', 'ই', 'ঈ', 'উ', 'ঊ', 'এ', 'ঐ', 'ও', 'ঔ')

    @Test
    fun probe() {
        if (System.getenv("S109_PROBE") != "1") return
        val words = listOf(
            "a", "aw", "o", "e", "i", "u",
            "awto", "awnek", "awshadharon", "awsombhob", "awbosthaa", "awbostha",
            "oto", "onek", "oshadharon", "osombhob",
            "anek", "ato", "ashadharon", "asombhob",
            "abar", "amar", "aporadh", "aador",
            "auto", "ei", "oi", "eta", "ota"
        )
        for (w in words) {
            val r = engine.convertWord(w)
            val strip = engine.getSuggestions(w, 6).joinToString("|") { it.bengali }
            println("S109PROBE $w -> ${r.bengali} (conf=${r.confidence}, src=${r.source}) strip=[$strip]")
        }
    }

    @Test
    fun runStudy() {
        if (System.getenv("S109_STUDY") != "1") return
        val maxKeys = System.getenv("S109_MAX")?.toIntOrNull() ?: 20000
        val outDir = File("build/reports/s109-study").apply { mkdirs() }

        // key -> owner (first row in the store's (tier, priority, freq DESC)
        // law) restricted to tier-A canonical rows whose word is vowel-initial.
        data class Row(val key: String, val bengali: String, val freq: Int)
        val rows = ArrayList<Row>(40000)
        val dbFile = TestDictionaryLoader.findDictionarySqlite()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT p.key, w.bengali, p.frequency
                       FROM phonetic_index p JOIN words w ON w.id = p.word_id
                       WHERE p.tier = 0 AND p.priority = 0 AND p.frequency >= 35
                       ORDER BY p.key, p.frequency DESC"""
                ).use { rs ->
                    var lastKey = ""
                    while (rs.next()) {
                        val key = rs.getString(1)
                        if (key == lastKey) continue // keep only the key's top canonical owner
                        val bengali = rs.getString(2)
                        if (bengali.firstOrNull() !in vowels) continue
                        lastKey = key
                        rows.add(Row(key, ReverseTransliterator.foldNukta(bengali), rs.getInt(3)))
                    }
                }
            }
        }
        rows.sortByDescending { it.freq }
        val study = rows.take(maxKeys)
        println("S109 vowel-initial canonical keys=${rows.size} studied=${study.size}")

        data class Bucket(var n: Int = 0, var primary: Int = 0, var top6: Int = 0)

        fun runPopulation(
            name: String,
            population: List<Pair<String, Row>>, // typedKey to intended row
            bucketOf: (Row) -> String
        ): Map<String, Bucket> {
            val buckets = LinkedHashMap<String, Bucket>()
            val misses = File(outDir, "misses_$name.tsv").bufferedWriter()
            misses.write("typed\texpected\tgot\tfreq\tin_top6\n")
            for ((typed, row) in population) {
                val b = buckets.getOrPut(bucketOf(row)) { Bucket() }
                b.n++
                val got = ReverseTransliterator.foldNukta(engine.convertWord(typed).bengali)
                val inTop6 = if (got == row.bengali) true else {
                    engine.getSuggestions(typed, 6)
                        .any { ReverseTransliterator.foldNukta(it.bengali) == row.bengali }
                }
                if (got == row.bengali) b.primary++
                if (inTop6) b.top6++
                else misses.write("$typed\t${row.bengali}\t$got\t${row.freq}\t$inTop6\n")
            }
            misses.close()
            return buckets
        }

        val summary = StringBuilder()
        fun report(name: String, buckets: Map<String, Bucket>) {
            summary.append("== $name ==\n")
            var n = 0; var p = 0; var t = 0
            for ((k, b) in buckets.entries.sortedByDescending { it.value.n }) {
                n += b.n; p += b.primary; t += b.top6
                summary.append(
                    "%-4s n=%-6d primary=%5.1f%% top6=%5.1f%%\n"
                        .format(k, b.n, 100.0 * b.primary / b.n, 100.0 * b.top6 / b.n)
                )
            }
            summary.append(
                "TOTAL n=%d primary=%5.1f%% top6=%5.1f%%\n\n"
                    .format(n, 100.0 * p / n, 100.0 * t / n)
            )
        }

        // 1. canonical romanization of every vowel-initial word
        report("canonical", runPopulation(
            "canonical",
            study.map { it.key to it },
            bucketOf = { it.bengali.first().toString() }
        ))

        // 2+3. অ-initial words with o-onset keys, retyped as aw-/a-onset.
        val oOnset = study.filter { it.bengali.first() == 'অ' && it.key.startsWith("o") && it.key.length >= 2 }
        report("aw_variant", runPopulation(
            "aw_variant",
            oOnset.map { ("aw" + it.key.substring(1)) to it },
            bucketOf = { "aw" }
        ))
        report("a_variant", runPopulation(
            "a_variant",
            oOnset.map { ("a" + it.key.substring(1)) to it },
            bucketOf = { "a" }
        ))

        File(outDir, "summary.txt").writeText(summary.toString())
        println(summary)
    }
}
