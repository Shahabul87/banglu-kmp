package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

/**
 * S89: confusing-words mega study (user directive 2026-08-13: "test engine
 * power using thousands of confusing words" — typing MISTAKES, not just habit
 * spellings). Complements S82 (habit variants) with the corruption classes
 * real typists produce:
 *
 *   voicing        g<->k, d<->t, b<->p swaps        (thogieco for thokieco)
 *   vowel_drop     one medial vowel omitted          (amke for amake)
 *   glide          iye -> ie / ie -> iye             (thokieco / thokiyeco)
 *   transpose      adjacent letters swapped          (tmui for tumi)
 *   double_miss    doubled letter pressed once       (chola for cholla)
 *   extra_h        aspirate h added/dropped          (tumhi / tik for thik)
 *
 * For each corruption of a high-usage word's typed key, asks: does the
 * intended word still reach the primary or the top-6 strip, and does the
 * strip stay garbage-free? Attributed per class so fixes land at mechanism
 * level.
 *
 * Skipped unless S89_STUDY=1:
 *   S89_STUDY=1 S89_MAX=2000 ./gradlew :shared:jvmTest \
 *     --tests "com.banglu.engine.S89ConfusingWordsStudyJvm"
 * Output: build/reports/s89-study/{summary.txt,misses.tsv}
 */
class S89ConfusingWordsStudyJvm {

    private val engine get() = ConjunctSolutionRoundJvmTest.engine

    @Test
    fun runStudy() {
        if (System.getenv("S89_STUDY") != "1") return
        val maxWords = System.getenv("S89_MAX")?.toIntOrNull() ?: 2000
        val outDir = File("build/reports/s89-study").apply { mkdirs() }

        val dbFile = TestDictionaryLoader.findDictionarySqlite()
        val oracle = HashSet<String>(1 shl 20)
        val studyWords = ArrayList<Pair<String, Int>>(maxWords * 2)
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali, frequency FROM words").use { rs ->
                    while (rs.next()) {
                        val w = ReverseTransliterator.foldNukta(rs.getString(1))
                        oracle.add(w)
                        val f = rs.getInt(2)
                        if (f >= 65) studyWords.add(w to f)
                    }
                }
            }
        }
        studyWords.sortByDescending { it.second }
        val words = studyWords.take(maxWords)
        println("S89 oracle=${oracle.size} words=${words.size}")

        fun isRealText(s: String): Boolean {
            if (s.any { it in 'a'..'z' || it in 'A'..'Z' }) return true
            return s.split(' ').all { it.isEmpty() || ReverseTransliterator.foldNukta(it) in oracle }
        }

        val vowels = "aeiou"
        fun corruptions(key: String): List<Pair<String, String>> {
            val out = LinkedHashMap<String, String>()
            fun add(cls: String, v: String) {
                if (v != key && v.length >= 3 && v !in out.values && !out.containsKey(cls)) out[cls] = v
            }
            // voicing swap on the first swappable consonant
            for ((a, b) in listOf('k' to 'g', 'g' to 'k', 't' to 'd', 'd' to 't', 'p' to 'b', 'b' to 'p')) {
                val i = key.indexOf(a)
                if (i >= 0) { add("voicing", key.substring(0, i) + b + key.substring(i + 1)); break }
            }
            // drop one MEDIAL vowel (never first/last char)
            for (i in 1 until key.length - 1) {
                if (key[i] in vowels && key[i - 1] !in vowels && key[i + 1] !in vowels) {
                    add("vowel_drop", key.removeRange(i, i + 1)); break
                }
            }
            // glide: iye <-> ie
            if ("iye" in key) add("glide", key.replace("iye", "ie"))
            else if ("ie" in key) add("glide", key.replace("ie", "iye"))
            // transpose the middle pair
            val m = key.length / 2
            if (key.length >= 4 && key[m] != key[m - 1]) {
                add("transpose", key.substring(0, m - 1) + key[m] + key[m - 1] + key.substring(m + 1))
            }
            // doubled letter pressed once
            val d = Regex("([a-z])\\1").find(key)
            if (d != null) add("double_miss", key.replaceFirst(d.value, d.groupValues[1]))
            // aspirate h dropped
            val h = Regex("([kgcjtdpb])h").find(key)
            if (h != null) add("extra_h", key.replaceFirst(h.value, h.groupValues[1]))
            return out.entries.map { it.key to it.value }
        }

        data class ClassStat(var tested: Int = 0, var primaryHit: Int = 0, var top6Hit: Int = 0, var garbage: Int = 0)
        val stats = LinkedHashMap<String, ClassStat>()
        val misses = File(outDir, "misses.tsv").bufferedWriter()
        misses.write("class\tkey\tword\tprimary\ttop6\n")
        var missRows = 0

        for ((word, _) in words) {
            val canonical = ReverseTransliterator.reverseWord(word).lowercase()
            if (canonical.isBlank() || !canonical.all { it in 'a'..'z' } || canonical.length !in 3..20) continue
            for ((cls, key) in corruptions(canonical)) {
                val st = stats.getOrPut(cls) { ClassStat() }
                st.tested++
                val primary = engine.convertWord(key)
                val sugg = engine.getSuggestions(key, 6)
                val folded = sugg.map { ReverseTransliterator.foldNukta(it.bengali) }
                val pHit = ReverseTransliterator.foldNukta(primary.bengali) == word
                if (pHit) st.primaryHit++
                if (word in folded) st.top6Hit++
                else if (missRows < 3000) {
                    misses.write("$cls\t$key\t$word\t${primary.bengali}\t${folded.joinToString("|")}\n")
                    missRows++
                }
                if (sugg.drop(1).any { !isRealText(it.bengali) }) st.garbage++
            }
        }
        misses.close()

        val summary = buildString {
            appendLine("S89 CONFUSING WORDS STUDY — db=${dbFile.name} words=${words.size}")
            appendLine("class          tested  primaryHit      top6Hit      stripsWithGarbage")
            for ((cls, s) in stats) {
                appendLine(
                    cls.padEnd(14) + s.tested.toString().padEnd(8) +
                        "${s.primaryHit} (${pct(s.primaryHit, s.tested)})".padEnd(16) +
                        "${s.top6Hit} (${pct(s.top6Hit, s.tested)})".padEnd(13) +
                        "${s.garbage} (${pct(s.garbage, s.tested)})"
                )
            }
        }
        File(outDir, "summary.txt").writeText(summary)
        println(summary)
    }

    private fun pct(n: Int, d: Int) = if (d == 0) "0%" else "${(n * 1000 / d) / 10.0}%"
}
