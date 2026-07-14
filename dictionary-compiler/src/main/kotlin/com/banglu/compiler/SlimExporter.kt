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
            val words = LinkedHashSet<String>(200_000)
            var rows = 0
            conn.createStatement().executeQuery(
                """SELECT p.key, w.bengali, p.frequency, p.tier, p.priority
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
                    words.add(rs.getString(2)); rows++
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
            words.forEachIndexed { i, w -> if (i > 0) sb.append(','); sb.append(q(w)) }
            sb.append("]}")
            File(outPath).writeText(sb.toString())
            println("slim: $rows index rows, $en english, ${words.size} words, floor=$floor -> $outPath (${File(outPath).length() / 1024 / 1024} MB)")
        }
    }

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
