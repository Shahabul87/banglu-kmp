package com.banglu.engine

import com.banglu.engine.util.ReverseTransliterator
import java.sql.DriverManager
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * S102 study: what does the in-RAM extended-dictionary trie (~130K entries,
 * 379K phonetic keys, the largest full-mode heap structure) add over the
 * sqlite store + validator + generative lattice?
 *
 * Two engines share the SAME full store and word list; only the extended
 * dictionary differs. Two sweeps:
 *  1. canonical store keys — the store has precedence, so parity here means
 *     everyday typing never touches the trie;
 *  2. extended-ONLY keys (no store row) — the trie is currently the only
 *     resolver for these; measures what fuzzy/recovery keep without it.
 *
 * Run: S102_STUDY=1 ./gradlew :shared:jvmTest --tests "*S102*" --rerun
 * Optional: S102_MAX=<n> per-sweep sample cap (default 15000).
 */
class S102TrieValueStudyJvm {

    private class NoExtendedLoader : JvmSqliteDictionaryLoader(TestDictionaryLoader.findDictionarySqlite()) {
        override suspend fun loadExtendedDictionary(): List<com.banglu.engine.types.SmartDictionaryEntry>? = null
    }

    private fun fold(s: String) = ReverseTransliterator.foldNukta(s)

    @Test
    fun trieValueStudy() {
        if (System.getenv("S102_STUDY") != "1") return
        val maxKeys = System.getenv("S102_MAX")?.toIntOrNull() ?: 15_000

        val engineA = ConjunctSolutionRoundJvmTest.engine // full: trie loaded
        val engineB = SmartEngine().also { eng ->
            eng.initializeSync()
            runBlocking { eng.initialize(storage = null, loader = NoExtendedLoader()) }
            eng.setPhoneticIndex(ConjunctSolutionRoundJvmTest.store)
        }

        val dbFile = TestDictionaryLoader.findDictionarySqlite()
        val canonicalKeys = ArrayList<String>(maxKeys)
        val extOnlyKeys = ArrayList<String>(maxKeys)
        val realWords = HashSet<String>(1_200_000)
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT key FROM phonetic_index WHERE priority=0 AND tier=0 AND (rowid % 17)=0 LIMIT $maxKeys"
                ).use { rs -> while (rs.next()) canonicalKeys.add(rs.getString(1)) }
            }
            conn.createStatement().use { st ->
                st.executeQuery(
                    """SELECT DISTINCT p.phonetic FROM extended_phonetics p
                       WHERE NOT EXISTS (SELECT 1 FROM phonetic_index i WHERE i.key = p.phonetic)
                       LIMIT $maxKeys"""
                ).use { rs -> while (rs.next()) extOnlyKeys.add(rs.getString(1)) }
            }
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali FROM words").use { rs ->
                    while (rs.next()) realWords.add(fold(rs.getString(1)))
                }
            }
            conn.createStatement().use { st ->
                st.executeQuery("SELECT bengali FROM extended_dictionary").use { rs ->
                    while (rs.next()) realWords.add(fold(rs.getString(1)))
                }
            }
        }

        println("S102 STUDY — canonical=${canonicalKeys.size} extOnly=${extOnlyKeys.size}")

        // ── Sweep 1: canonical store keys ────────────────────────────────
        var primaryDiff = 0
        var composingDiff = 0
        var stripDiff = 0
        val primaryDiffSamples = ArrayList<String>()
        for (key in canonicalKeys) {
            val a = engineA.convertWord(key)
            val b = engineB.convertWord(key)
            if (fold(a.bengali) != fold(b.bengali)) {
                primaryDiff++
                if (primaryDiffSamples.size < 40) {
                    primaryDiffSamples.add("$key: A=${a.bengali}(${a.source}) B=${b.bengali}(${b.source})")
                }
            }
            val ca = engineA.convertForComposing(key).bengali
            val cb = engineB.convertForComposing(key).bengali
            if (fold(ca) != fold(cb)) composingDiff++
            val sa = engineA.getSuggestions(key, 6).map { fold(it.bengali) }
            val sb = engineB.getSuggestions(key, 6).map { fold(it.bengali) }
            if (sa != sb) stripDiff++
        }
        println("S102 CANONICAL: primaryDiff=$primaryDiff/${canonicalKeys.size} " +
            "composingDiff=$composingDiff stripDiff=$stripDiff")
        primaryDiffSamples.forEach { println("S102 CANON-DIFF $it") }

        // ── Sweep 2: extended-only keys (trie was sole resolver) ─────────
        var same = 0
        var differentReal = 0
        var degraded = 0
        val degradedSamples = ArrayList<String>()
        val differentSamples = ArrayList<String>()
        for (key in extOnlyKeys) {
            val a = engineA.convertWord(key)
            val b = engineB.convertWord(key)
            when {
                fold(a.bengali) == fold(b.bengali) -> same++
                b.confidence > 0.65 && fold(b.bengali).split(' ').all { it in realWords } -> {
                    differentReal++
                    if (differentSamples.size < 30) {
                        differentSamples.add("$key: A=${a.bengali} B=${b.bengali}@${b.confidence}(${b.source})")
                    }
                }
                else -> {
                    degraded++
                    if (degradedSamples.size < 30) {
                        degradedSamples.add("$key: A=${a.bengali} B=${b.bengali}@${b.confidence}(${b.source})")
                    }
                }
            }
        }
        println("S102 EXT-ONLY: same=$same differentReal=$differentReal degraded=$degraded " +
            "of ${extOnlyKeys.size}")
        differentSamples.forEach { println("S102 EXT-DIFF $it") }
        degradedSamples.forEach { println("S102 EXT-DEGRADED $it") }
    }
}
