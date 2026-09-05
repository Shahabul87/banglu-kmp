package com.banglu.engine

import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.util.ReverseTransliterator
import java.io.File
import java.text.Normalizer
import java.sql.DriverManager
import kotlin.test.Test

/**
 * S188 (2026-09-05, user: "few newspapers … golpo, uponnas, literature,
 * science, names of people, objects, districts, villages — find every word
 * the engine cannot handle or produces garbage for, the failure pattern,
 * and how one word is typed in different ways"): real-corpus coverage study
 * on the real store. Opt-in:
 *
 *   S188_STUDY=1 S188_FILES="path:category,path:category" S188_MAX=30000 \
 *     ./gradlew :shared:jvmTest --tests "com.banglu.engine.S188RealWorldStudyJvm"
 *
 * Each file is "word<TAB>count". Every unique word (nukta-folded, Bengali
 * letters only) is typed two ways: the CANONICAL roman (ReverseTransliterator,
 * chandrabindu omitted — what people actually type) and the TYPIST fold
 * (sh→s, chh→ch, ph→f, bh→v, z→j, ii→i, uu→u). A word
 * passes on `primary` when the engine's commit is the word, on `top6` when
 * the strip carries it. Misses are tagged by the shape of the difference so
 * the patterns can be counted, occurrence-weighted.
 *
 * Output: build/reports/s188-study/<category>_{summary.txt,misses.tsv}
 * and patterns.tsv across all categories.
 */
class S188RealWorldStudyJvm {
    private val engine get() = ConjunctSolutionRoundJvmTest.engine
    // NFC first: news sites encode ো/ৌ as two code points (ে + া), the engine emits
    // the composed form — without this, identical words counted as misses.
    private fun fold(s: String) = ReverseTransliterator.foldNukta(Normalizer.normalize(s, Normalizer.Form.NFC))

    // The folds real typists make (S181 keyReadingDistance set). NOT y→j: the
    // reverse map already writes য় as "y" and nobody types "hoj" for হয় — the
    // first pass had that fold and it manufactured the largest "miss" class.
    // ii/uu are the reverse map's ী/ূ (mujabornii, puurnomoti) — typists write
    // i/u. NOT ee/oo: the map writes the vowel LETTER ও as "oo" (aroo, hooya),
    // and folding that gave "aru"/"huya", which nobody types.
    private fun typistFold(k: String): String = k
        .replace("chh", "ch").replace("sh", "s").replace("ph", "f").replace("bh", "v")
        .replace("z", "j").replace("ii", "i").replace("uu", "u")

    private val suffixes = listOf("দের", "গুলো", "গুলি", "ের", "েরা", "টা", "টি", "কে", "তে", "ছেন", "ছিল", "ছিলেন", "লাম", "বেন", "বে", "লে", "লো", "য়ে", "রা", "ে", "ও", "ই")

    private fun tags(expected: String, got: String, inDict: Boolean, key: String): List<String> = buildList {
        if (!inDict) add("oov")
        if (' ' in got) add("split")
        val e = expected; val g = got.replace(" ", "")
        fun swapOnly(a: String, b: String): Boolean {
            if (e.length != g.length) return false
            var diff = 0
            for (i in e.indices) if (e[i] != g[i]) { if (!((e[i] in a && g[i] in b) || (e[i] in b && g[i] in a))) return false; diff++ }
            return diff > 0
        }
        if (swapOnly("িু", "ীূ")) add("vowel_length")
        if (swapOnly("ন", "ণ")) add("na_nna")
        if (swapOnly("শষস", "শষস")) add("sibilant")
        if (swapOnly("য", "জ") || swapOnly("য়", "য") || swapOnly("য়", "জ")) add("ya_ja")
        if (swapOnly("র", "ড়") || swapOnly("ঢ়", "ঢ")) add("ra_rra")
        if (swapOnly("ব", "ভ")) add("ba_bha")
        if (swapOnly("ত", "ট") || swapOnly("দ", "ড") || swapOnly("থ", "ঠ") || swapOnly("ধ", "ঢ")) add("dental_retroflex")
        if ('ঁ' in e && 'ঁ' !in g) add("chandrabindu_dropped")
        if (e.endsWith("ো") && !g.endsWith("ো") && e.dropLast(1) == g) add("final_okar_dropped")
        if (!e.endsWith("ো") && g.endsWith("ো") && g.dropLast(1) == e) add("final_okar_added")
        if (e.count { it == '্' } != g.count { it == '্' }) add("conjunct_shape")
        if ('ৎ' in e || 'ৎ' in g) add("khanda_ta")
        if (e.length >= 12) add("long_compound")
        if (suffixes.any { e.endsWith(it) && e.length > it.length + 2 }) add("inflected")
        if (e.first() in "অআইঈউঊএঐওঔ") add("vowel_initial")
        if (isEmpty()) add("ranking_other")
    }

    @Test
    fun runStudy() {
        if (System.getenv("S188_STUDY") != "1") return
        val maxWords = System.getenv("S188_MAX")?.toIntOrNull() ?: 30000
        val files = System.getenv("S188_FILES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: error("S188_FILES=path:category,...")
        val outDir = File("build/reports/s188-study").apply { mkdirs() }
        val oracle = HashSet<String>(1 shl 20)
        DriverManager.getConnection("jdbc:sqlite:${TestDictionaryLoader.findDictionarySqlite().absolutePath}").use { conn ->
            conn.createStatement().use { st -> st.executeQuery("SELECT bengali FROM words").use { rs -> while (rs.next()) oracle.add(fold(rs.getString(1))) } }
        }
        val patternTotals = LinkedHashMap<String, LongArray>() // tag -> [missWords, missWeight]
        val allPatterns = File(outDir, "patterns.tsv").bufferedWriter()
        allPatterns.write("category\ttag\tmiss_words\tmiss_weight\n")
        for (spec in files) {
            val parts = spec.split(':', limit = 2)
            val path = parts[0]
            val category = parts.getOrNull(1) ?: File(path).nameWithoutExtension
            val counts = HashMap<String, Int>(1 shl 17)
            File(path).forEachLine { line ->
                val t = line.split('\t'); if (t.size < 2) return@forEachLine
                val w = fold(t[0]).trim('ঃ', '্'); val c = t[1].toIntOrNull() ?: return@forEachLine
                if (w.length in 2..20 && w.all { it in 'ঀ'..'৿' }) counts.merge(w, c) { a, b -> a + b }
            }
            val unique = counts.entries.sortedByDescending { it.value }.take(maxWords)
            val tokens = counts.values.sumOf { it.toLong() }
            data class B(var n: Int = 0, var w: Long = 0, var p: Int = 0, var pw: Long = 0, var t6: Int = 0, var t6w: Long = 0)
            val byVariant = linkedMapOf("canonical" to B(), "typist" to B())
            val byPop = linkedMapOf("in_dictionary" to B(), "oov" to B())
            val catPatterns = LinkedHashMap<String, LongArray>()
            val misses = File(outDir, "${category}_misses.tsv").bufferedWriter()
            misses.write("expected\tcount\tin_dict\tvariant\tkey\tgot\tsource\ttop6\ttags\n")
            var unromanizable = 0; var garbage = 0
            for ((word, count) in unique) {
                // the reverse map writes ঁ as "N" → lowercased it would read as ন; drop it (typists omit chandrabindu)
                val canonical = runCatching { ReverseTransliterator.reverseWord(word).replace("N", "").lowercase() }.getOrNull() ?: ""
                if (canonical.isEmpty() || canonical.length > 26 || !canonical.all { it in 'a'..'z' }) { unromanizable++; continue }
                val inDict = word in oracle
                val pop = byPop[if (inDict) "in_dictionary" else "oov"]!!
                pop.n++; pop.w += count
                var anyPrimary = false; var anyTop6 = false
                for ((variant, key) in listOf("canonical" to canonical, "typist" to typistFold(canonical)).distinctBy { it.second }) {
                    val r = engine.convertWord(key)
                    val got = fold(r.bengali)
                    val strip = engine.getSuggestions(key, 6).map { fold(it.bengali) }
                    val primary = got == word
                    val top6 = primary || word in strip
                    val b = byVariant[variant]!!
                    b.n++; b.w += count; if (primary) { b.p++; b.pw += count }; if (top6) { b.t6++; b.t6w += count }
                    anyPrimary = anyPrimary || primary; anyTop6 = anyTop6 || top6
                    if (!top6) {
                        val tg = tags(word, got, inDict, key)
                        if (r.source == ResolutionSource.CLEAN_TRANSLITERATION || r.source == ResolutionSource.RULE) garbage++
                        for (t in tg) { catPatterns.getOrPut(t) { LongArray(2) }.let { it[0]++; it[1] += count } ; patternTotals.getOrPut(t) { LongArray(2) }.let { it[0]++; it[1] += count } }
                        misses.write("$word\t$count\t$inDict\t$variant\t$key\t$got\t${r.source}\t${strip.joinToString("|")}\t${tg.joinToString(",")}\n")
                    }
                }
                if (anyPrimary) { pop.p++; pop.pw += count }; if (anyTop6) { pop.t6++; pop.t6w += count }
            }
            misses.close()
            val s = StringBuilder("S188 category=$category file=$path tokens=$tokens unique=${counts.size} studied=${unique.size} unromanizable=$unromanizable rule_floor_misses=$garbage\n")
            fun line(name: String, b: B) { if (b.n > 0) s.append("%-14s n=%-6d primary=%5.1f%% (weighted %5.1f%%)  top6=%5.1f%% (weighted %5.1f%%)\n".format(name, b.n, 100.0 * b.p / b.n, 100.0 * b.pw / b.w, 100.0 * b.t6 / b.n, 100.0 * b.t6w / b.w)) }
            s.append("== by variant ==\n"); byVariant.forEach { (k, b) -> line(k, b) }
            s.append("== by population (either variant) ==\n"); byPop.forEach { (k, b) -> line(k, b) }
            s.append("== miss patterns (words / weight) ==\n")
            for ((t, a) in catPatterns.entries.sortedByDescending { it.value[1] }) { s.append("%-24s %6d %9d\n".format(t, a[0], a[1])); allPatterns.write("$category\t$t\t${a[0]}\t${a[1]}\n") }
            File(outDir, "${category}_summary.txt").writeText(s.toString()); println(s)
        }
        for ((t, a) in patternTotals.entries.sortedByDescending { it.value[1] }) allPatterns.write("ALL\t$t\t${a[0]}\t${a[1]}\n")
        allPatterns.close()
    }
}
