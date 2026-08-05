package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * S83: composing-preview vs commit parity audit (the 1,034 mismatches the
 * S82 study surfaced). Dumps every canonical key whose live preview
 * disagrees with what space would commit, classified by the commit layer
 * and key shape, so the fix lands on the dominant class.
 *
 * Skipped unless S83_STUDY=1:
 *   S83_STUDY=1 ./gradlew :shared:jvmTest --tests "com.banglu.engine.S83ComposingParityStudyJvm"
 * Output: build/reports/s83-study/{summary.txt,mismatches.tsv}
 */
class S83ComposingParityStudyJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun runStudy() {
        if (System.getenv("S83_STUDY") != "1") return
        val outDir = File("build/reports/s83-study").apply { mkdirs() }
        val dbFile = TestDictionaryLoader.findDictionarySqlite()

        val words = ArrayList<String>(120_000)
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali FROM words WHERE frequency >= 60").use { rs ->
                    while (rs.next()) words.add(ReverseTransliterator.foldNukta(rs.getString(1)))
                }
            }
        }
        println("S83 words=${words.size}")

        val rows = File(outDir, "mismatches.tsv").bufferedWriter()
        rows.write("key\tlen\tword\tcomposing\tcommit\tcommitSrc\tcommitConf\tcompSrc\n")
        var tested = 0
        var mismatches = 0
        val bySource = HashMap<String, Int>()
        val byLen = HashMap<Int, Int>()

        for (word in words) {
            val key = ReverseTransliterator.reverseWord(word).lowercase()
            if (key.isBlank() || !key.all { it in 'a'..'z' } || key.length !in 2..24) continue
            tested++
            val commit = engine.convertWord(key)
            val composing = engine.convertForComposing(key)
            if (ReverseTransliterator.foldNukta(commit.bengali) !=
                ReverseTransliterator.foldNukta(composing.bengali)
            ) {
                mismatches++
                bySource.merge(commit.source.name, 1, Int::plus)
                byLen.merge(key.length.coerceAtMost(8), 1, Int::plus)
                rows.write("$key\t${key.length}\t$word\t${composing.bengali}\t${commit.bengali}\t${commit.source}\t${commit.confidence}\t${composing.source}\n")
            }
            if (tested % 30000 == 0) println("S83 progress $tested")
        }
        rows.close()

        val summary = buildString {
            appendLine("S83 COMPOSING PARITY — tested=$tested mismatches=$mismatches")
            appendLine("by commit source:")
            bySource.entries.sortedByDescending { it.value }.forEach { appendLine("  ${it.key}: ${it.value}") }
            appendLine("by key length (8=8+):")
            byLen.entries.sortedBy { it.key }.forEach { appendLine("  len${it.key}: ${it.value}") }
        }
        File(outDir, "summary.txt").writeText(summary)
        println(summary)
    }
}
