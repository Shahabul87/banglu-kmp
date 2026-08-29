package com.banglu.compiler

import java.io.File
import java.sql.DriverManager

/**
 * S45: emits the slim web dictionary from a compiled dictionary.sqlite.
 * Tier-A rows above the frequency floor + full english lexicon + the word
 * set for containsWord — consumed by BangluWebEngine.attachSlimDictionary.
 * Invoked via main(): DictionaryCompiler slim <db> <out.json> [floor]
 */
object SlimExporter {
    fun export(dbPath: String, outPath: String, floor: Int) {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val sb = StringBuilder(64 shl 20)
            sb.append("{\"version\":\"")
            conn.createStatement().executeQuery(
                "SELECT value FROM metadata WHERE key='version'"
            ).use { rs -> sb.append(if (rs.next()) rs.getString(1) else "unknown") }
            sb.append("\",\"index\":[")
            // S119: word -> corpus frequency, exported as a parallel freqs
            // array so the JS surfaces can load the validator WITH frequency
            // data — the glue layers (তো/না compounds) and the S118 stem-
            // evidence law are frequency-gated and were silently dead on slim.
            val words = LinkedHashMap<String, Int>(200_000)
            var rows = 0
            conn.createStatement().executeQuery(
                """SELECT p.key, w.bengali, p.frequency, p.tier, p.priority, w.frequency
                   FROM phonetic_index p JOIN words w ON w.id = p.word_id
                   WHERE p.tier = 0 AND p.frequency >= $floor"""
            ).use { rs ->
                while (rs.next()) {
                    if (rows > 0) sb.append(',')
                    sb.append("{\"k\":").append(q(rs.getString(1)))
                        .append(",\"b\":").append(q(rs.getString(2)))
                        .append(",\"f\":").append(rs.getInt(3))
                        .append(",\"t\":").append(rs.getInt(4))
                        .append(",\"p\":").append(rs.getInt(5)).append('}')
                    words[rs.getString(2)] = rs.getInt(6); rows++
                }
            }
            sb.append("],\"english\":[")
            var en = 0
            conn.createStatement().executeQuery(
                "SELECT key, bengali FROM english_lexicon"
            ).use { rs ->
                while (rs.next()) {
                    if (en > 0) sb.append(',')
                    sb.append("{\"k\":").append(q(rs.getString(1)))
                        .append(",\"b\":").append(q(rs.getString(2))).append('}')
                    en++
                }
            }
            sb.append("],\"words\":[")
            var wi = 0
            for (w in words.keys) { if (wi > 0) sb.append(','); sb.append(q(w)); wi++ }
            sb.append("],\"freqs\":[")
            wi = 0
            for (f in words.values) { if (wi > 0) sb.append(','); sb.append(f); wi++ }
            // S141: a pruned n-gram model so the JS surfaces (extension, macOS
            // IME, web editor) get the same next-word prediction bar as Android
            // and the Windows IME. Top followers only — a prediction bar shows
            // five chips, so the long tail buys nothing but megabytes.
            var bi = 0
            val prevWords = LinkedHashSet<String>()
            sb.append("],\"bi\":[")
            if (conn.hasTable("bigram_pairs")) {
                conn.createStatement().executeQuery(
                    """SELECT previous_word, next_word, count FROM (
                           SELECT previous_word, next_word, count,
                                  ROW_NUMBER() OVER (PARTITION BY previous_word ORDER BY count DESC, next_word) AS rn
                           FROM bigram_pairs)
                       WHERE rn <= $BIGRAM_FOLLOWERS AND count >= $NGRAM_MIN_COUNT"""
                ).use { rs ->
                    while (rs.next()) {
                        if (bi > 0) sb.append(',')
                        sb.append('[').append(q(rs.getString(1))).append(',').append(q(rs.getString(2)))
                            .append(',').append(rs.getInt(3)).append(']')
                        prevWords.add(rs.getString(1)); bi++
                    }
                }
            }
            var tri = 0
            sb.append("],\"tri\":[")
            if (conn.hasTable("trigram_triples")) {
                conn.createStatement().executeQuery(
                    """SELECT w1, w2, w3, count FROM (
                           SELECT w1, w2, w3, count,
                                  ROW_NUMBER() OVER (PARTITION BY w1, w2 ORDER BY count DESC, w3) AS rn
                           FROM trigram_triples)
                       WHERE rn <= $TRIGRAM_FOLLOWERS AND count >= $NGRAM_MIN_COUNT"""
                ).use { rs ->
                    while (rs.next()) {
                        if (tri > 0) sb.append(',')
                        sb.append('[').append(q(rs.getString(1))).append(',').append(q(rs.getString(2)))
                            .append(',').append(q(rs.getString(3))).append(',').append(rs.getInt(4)).append(']')
                        tri++
                    }
                }
            }
            // Unigram counts for the context words only (the model's word
            // pool is built from the pairs themselves).
            var uni = 0
            sb.append("],\"uni\":[")
            if (conn.hasTable("bigram_unigrams") && prevWords.isNotEmpty()) {
                conn.createStatement().executeQuery("SELECT word, count FROM bigram_unigrams").use { rs ->
                    while (rs.next()) {
                        if (rs.getString(1) !in prevWords) continue
                        if (uni > 0) sb.append(',')
                        sb.append('[').append(q(rs.getString(1))).append(',').append(rs.getInt(2)).append(']')
                        uni++
                    }
                }
            }
            sb.append("]}")
            File(outPath).writeText(sb.toString())
            println("slim: $rows index rows, $en english, ${words.size} words, $bi bigrams, $tri trigrams, $uni unigrams, floor=$floor -> $outPath (${File(outPath).length() / 1024 / 1024} MB)")
        }
    }

    private const val BIGRAM_FOLLOWERS = 5
    private const val TRIGRAM_FOLLOWERS = 3
    private const val NGRAM_MIN_COUNT = 3

    private fun java.sql.Connection.hasTable(name: String): Boolean =
        createStatement().executeQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='$name'"
        ).use { it.next() }

    private fun q(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
}
