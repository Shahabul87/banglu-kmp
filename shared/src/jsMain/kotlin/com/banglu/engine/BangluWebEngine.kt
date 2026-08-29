@file:OptIn(ExperimentalJsExport::class)

package com.banglu.engine

import com.banglu.engine.platform.InMemoryPhoneticIndexStore
import com.banglu.engine.types.BigramModelData
import com.banglu.engine.platform.PhoneticIndexHit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

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

    /**
     * Attach the slim dictionary (JSON string from banglu-slim.json).
     *
     * S108: throws on a version mismatch — a stale slim silently shipping an
     * old vocabulary is exactly the drift that put 3.8.10 in store zips while
     * Android shipped 3.9.2. Hosts catch and stay on the seed engine.
     */
    fun attachSlimDictionary(json: String) {
        initSeed()
        val slim = Json.decodeFromString<SlimDictionary>(json)
        require(slim.version == DictionaryVersion.REQUIRED) {
            "slim dictionary version ${slim.version} != engine required " +
                "${DictionaryVersion.REQUIRED} — regenerate banglu-slim.json " +
                "(dictionary-compiler: slim <db> <out.json>)"
        }
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
        // S119 slim-tier parity: the validator was never loaded on JS
        // surfaces, so every validator-gated layer (tryNegationCompound —
        // hobeto never glued হবে তো on web; the S118 stem-evidence law;
        // recovery guards) was silently dead. The slim word set + the S119
        // freqs array are the tier-A validator; sub-floor words simply stay
        // unattested — the documented slim scope.
        engine.loadValidatorWords(slim.words)
        if (slim.freqs.size == slim.words.size && slim.freqs.isNotEmpty()) {
            val freqMap = HashMap<String, Int>(slim.words.size * 2)
            for (i in slim.words.indices) freqMap[slim.words[i]] = slim.freqs[i]
            engine.loadValidatorFrequencies(freqMap)
        }
        // S141: the prediction bar's n-gram model (pruned in the exporter).
        if (slim.bi.isNotEmpty()) {
            val bigrams = HashMap<String, Int>(slim.bi.size * 2)
            for (row in slim.bi) if (row.size == 3) {
                bigrams[row[0].content + "\t" + row[1].content] = row[2].content.toIntOrNull() ?: continue
            }
            val trigrams = HashMap<String, Int>(slim.tri.size * 2)
            for (row in slim.tri) if (row.size == 4) {
                trigrams[row[0].content + "\t" + row[1].content + "\t" + row[2].content] =
                    row[3].content.toIntOrNull() ?: continue
            }
            val unigrams = HashMap<String, Int>(slim.uni.size * 2)
            for (row in slim.uni) if (row.size == 2) {
                unigrams[row[0].content] = row[1].content.toIntOrNull() ?: continue
            }
            engine.loadBigramModel(
                BigramModelData(
                    unigrams = unigrams,
                    bigrams = bigrams,
                    totalUnigrams = unigrams.values.sum(),
                    totalBigrams = bigrams.values.sum(),
                    trigrams = trigrams
                )
            )
        }
    }

    fun convert(input: String): String = engine.convertWord(input.trim()).bengali

    fun suggestions(input: String, limit: Int): Array<String> =
        engine.getSuggestions(input.trim(), limit).map { it.bengali }.toTypedArray()

    fun instantPreview(input: String): String = engine.convertForInstantPreview(input)

    /**
     * S114: Bengali -> roman key (the corpus romanization). Web hosts use it
     * for resume-editing: backspace into / typing after a committed word
     * re-enters roman composing on the word's own key, gated on an exact
     * reverse -> instantPreview round-trip (the Android S88/S109 mechanic;
     * the gate lives host-side, the linguistics live here).
     */
    fun reverseWord(bengali: String): String =
        com.banglu.engine.util.ReverseTransliterator.reverseWord(bengali)

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
     * S141: two-word next-word prediction — exact (prev2, prev1) trigram
     * followers lead, bigram/user predictions fill the remainder (the
     * SmartEngineAdapter overload Android and the Windows IME already use).
     * [prev2Bengali] may be empty when only one word precedes the caret.
     */
    fun nextWordPredictions2(prev2Bengali: String, prevBengali: String, limit: Int): Array<String> {
        val prev1 = prevBengali.trim()
        if (prev1.isEmpty() || limit <= 0) return emptyArray()
        val prev2 = prev2Bengali.trim()
        val tri = if (prev2.isNotEmpty()) engine.getTrigramNextWordPredictions(prev2, prev1, limit) else emptyList()
        if (tri.size >= limit) return tri.take(limit).map { it.bengali }.toTypedArray()
        val seen = tri.mapTo(mutableSetOf()) { it.bengali }
        val rest = engine.getNextWordPredictions(prev1, limit).filter { it.bengali !in seen }
        return (tri + rest).take(limit).map { it.bengali }.toTypedArray()
    }

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

    /**
     * S141: one committed (previous, next) Bengali pair — personal next-word
     * prediction for this session (the JS surfaces carry no bigram store;
     * the editor-shared learned.json holds words only).
     */
    fun recordNextWord(prevBengali: String, nextBengali: String) {
        initSeed()
        val prev = prevBengali.trim()
        val next = nextBengali.trim()
        if (prev.isEmpty() || next.isEmpty() || prev == next) return
        engine.recordUserBigram(prev, next)
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
    val words: List<String>,
    /** S119: corpus frequency per words[i] — powers the validator on slim
     *  surfaces (glue layers and evidence guards are frequency-gated). */
    val freqs: List<Int> = emptyList(),
    /** S141: pruned n-gram model — [prev, next, count] / [w1, w2, w3, count] /
     *  [word, count]. Absent in pre-S141 files (the version gate regenerates). */
    val bi: List<List<JsonPrimitive>> = emptyList(),
    val tri: List<List<JsonPrimitive>> = emptyList(),
    val uni: List<List<JsonPrimitive>> = emptyList()
)
