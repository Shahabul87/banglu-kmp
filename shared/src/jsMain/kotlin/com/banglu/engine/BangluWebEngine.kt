@file:OptIn(ExperimentalJsExport::class)

package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.platform.PhoneticIndexHit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * S45: the web/extension facade. Same SmartEngine that ships in the Android
 * keyboard, compiled to JS. Feed it the slim dictionary JSON produced by
 * `:dictionary-compiler` (--slim output) and you get v3 conversion parity for
 * the tier-A vocabulary, all shorthand/chat classes, and suggestions.
 */
@JsExport
object BangluWebEngine {
    private val engine = SmartEngine()
    private var ready = false

    /** Seed-only boot (rules + 6.5K seeds + shorthand). Instant. */
    fun initSeed() {
        if (!ready) {
            engine.initializeSync()
            ready = true
        }
    }

    /** Attach the slim dictionary (JSON string from banglu-slim.json). */
    fun attachSlimDictionary(json: String) {
        initSeed()
        val slim = Json.decodeFromString<SlimDictionary>(json)
        val entries = ArrayList<Pair<PhoneticIndexHit, String>>(slim.index.size)
        for (row in slim.index) {
            entries.add(
                PhoneticIndexHit(row.b, row.f, row.t, row.p) to row.k
            )
        }
        val english = HashMap<String, String>(slim.english.size * 2)
        for (e in slim.english) english[e.k] = e.b
        engine.setPhoneticIndex(
            InMemoryPhoneticIndexStore(entries, english, slim.words.toHashSet())
        )
    }

    fun convert(input: String): String = engine.convertWord(input.trim()).bengali

    fun suggestions(input: String, limit: Int): Array<String> =
        engine.getSuggestions(input.trim(), limit).map { it.bengali }.toTypedArray()

    fun instantPreview(input: String): String = engine.convertForInstantPreview(input)

    /**
     * S51: load the editor's ~/.banglu/learned.json (rows {p,b,f,t}; unknown
     * keys ignored, malformed input ignored — the IME must never crash on a
     * user-editable file).
     */
    fun applyLearnedWords(json: String) {
        initSeed()
        val rows = try {
            lenientJson.decodeFromString<List<LearnedRow>>(json)
        } catch (_: Throwable) {
            return
        }
        var applied = false
        for (r in rows) {
            val key = r.p.trim().lowercase()
            val bengali = r.b.trim()
            if (key.isEmpty() || bengali.isEmpty()) continue
            engine.addWord(key, bengali, r.f)
            applied = true
        }
        if (applied) engine.clearCache()
    }

    /** S51: one explicit candidate pick (same frequency the adapter uses). */
    fun recordPick(raw: String, bangla: String) {
        initSeed()
        val key = raw.trim().lowercase()
        val bengali = bangla.trim()
        if (key.isEmpty() || bengali.isEmpty()) return
        engine.addWord(key, bengali, 94)
        engine.clearCache()
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class LearnedRow(val p: String = "", val b: String = "", val f: Int = 94)

@Serializable
internal data class SlimRow(val k: String, val b: String, val f: Int, val t: Int, val p: Int)

@Serializable
internal data class SlimEnglish(val k: String, val b: String)

@Serializable
internal data class SlimDictionary(
    val version: String,
    val index: List<SlimRow>,
    val english: List<SlimEnglish>,
    val words: List<String>
)
