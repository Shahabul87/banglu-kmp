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
     * S54: multi-word conversion — converts each whitespace-separated token
     * and preserves the original whitespace, same contract as the Android
     * adapter's `parse()`. Use this (not per-word `convert`) when the caller
     * has a full sentence, e.g. server-side API routes.
     */
    fun parse(input: String): String = engine.parse(input)

    /**
     * S54: context-aware conversion. [prev1] is the immediately preceding
     * committed Bengali word, [prev2] the one before that (both optional —
     * pass "" for missing context). Mirrors
     * `SmartEngineAdapter.convertWordWithContext(word, listOf(prev2, prev1))`,
     * reimplemented directly on this instance's own [engine] because the
     * adapter is a separate singleton with its own engine/state that is
     * never wired to the slim-dictionary-backed web engine.
     */
    fun convertWithContext(word: String, prev1: String, prev2: String): String {
        val trimmed = word.trim()
        val base = engine.convertWord(trimmed)
        val ranked = engine.rerankWithContext(
            prev2Bengali = prev2.trim().ifEmpty { null },
            prev1Bengali = prev1.trim().ifEmpty { null },
            result = base
        )
        return ranked.bengali
    }

    /**
     * S54: suggestion strip re-ordered by the same context signal as
     * [convertWithContext] — the context-promoted word (if any) leads,
     * followed by the normal ranked suggestions, deduplicated, capped at
     * [limit]. Adapted from `SmartEngineAdapter.getSuggestionsWithContext`
     * (which also returns confidence/source/tier metadata this JS-facing
     * surface deliberately drops — callers only need the ranked strings).
     */
    fun suggestionsWithContext(word: String, prev1: String, prev2: String, limit: Int): Array<String> {
        val trimmed = word.trim()
        val suggestions = engine.getSuggestions(trimmed, limit).map { it.bengali }
        val contextBengali = convertWithContext(trimmed, prev1, prev2)
        val ordered = LinkedHashSet<String>()
        if (contextBengali.isNotBlank()) ordered.add(contextBengali)
        ordered.addAll(suggestions)
        return ordered.take(limit).toTypedArray()
    }

    /**
     * S54: composing-text preview — deliberately more conservative than
     * [convert] (no fuzzy/recovery dictionary jumps on incomplete words),
     * matching the Android IME's live-typing behavior.
     */
    fun compositionPreview(word: String): String = engine.getCompositionPreview(word)

    /**
     * S54: next-word predictions after [prevBengali] (corpus bigrams/
     * trigrams + user bigrams + a small static opener fallback — the SAME
     * on-device mechanism Android uses for its prediction bar; this
     * replaces the web app's neural-LM prediction bar, not a like-for-like
     * port of it). The seed-only engine (no slim dictionary attached) has
     * no bigram model loaded, so results come only from the static
     * FALLBACK_NEXT_WORDS table and any picks recorded this session via
     * [recordPick]/[applyLearnedWords]-driven usage — empty is a valid,
     * non-crashing result for words outside that table.
     */
    fun nextWordPredictions(prevBengali: String, limit: Int): Array<String> =
        engine.getNextWordPredictions(prevBengali.trim(), limit).map { it.bengali }.toTypedArray()

    /**
     * S54: add an explicit user dictionary entry (e.g. from a "teach a
     * word" UI), at the same frequency
     * (`SmartEngineAdapter.CUSTOM_CONVERSION_FREQUENCY` = 120) the adapter
     * uses for `addCustomConversion` so explicit entries outrank ordinary
     * learned picks (94) and dictionary defaults. Still routed through
     * `SmartEngine.addWord` -> `isPlausibleDynamicMapping`: a pair whose
     * phonetics don't plausibly reverse-transliterate to [bangla] is
     * silently rejected (anti-poisoning guard, never bypassed).
     */
    fun addCustomWord(raw: String, bangla: String) {
        initSeed()
        val key = raw.trim().lowercase()
        val bengali = bangla.trim()
        if (key.isEmpty() || bengali.isEmpty()) return
        engine.addWord(key, bengali, CUSTOM_CONVERSION_FREQUENCY)
        engine.clearCache()
    }

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

/** S54: mirrors `SmartEngineAdapter.CUSTOM_CONVERSION_FREQUENCY` (private there). */
private const val CUSTOM_CONVERSION_FREQUENCY = 120

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
