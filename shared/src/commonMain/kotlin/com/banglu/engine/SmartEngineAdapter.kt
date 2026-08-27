package com.banglu.engine

import com.banglu.engine.dictionary.EnglishPronunciationVariantData
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.platform.PhoneticIndexStore
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.PredictedWord
import com.banglu.engine.types.SmartSuggestion
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * SmartEngineAdapter - Public API singleton wrapping SmartEngine with learned-word persistence.
 *
 * This is the primary entry point for all phonetic conversion operations.
 * It manages the SmartEngine lifecycle, initialization, and user learning persistence.
 *
 * Usage:
 *   SmartEngineAdapter.initializeSync()          // Seed dictionary (instant)
 *   SmartEngineAdapter.initialize(storage, loader) // Full async init
 *   SmartEngineAdapter.convert("ami")             // Returns "আমি"
 */
object SmartEngineAdapter {
    @Volatile
    private var engine: SmartEngine? = null
    // S108: these fields are written on the init/settings thread (Android:
    // Main) and read on the engine lane — @Volatile makes the publication
    // safe; the preference maps additionally share [preferenceLock] because
    // clear+putAll and get can otherwise tear mid-rebuild.
    @Volatile
    private var storage: PlatformStorage? = null
    private val preferenceLock = Any()
    private val customPreferenceMap = mutableMapOf<String, String>()
    private val selectedPreferenceMap = mutableMapOf<String, String>()
    @Volatile
    private var learningEnabled = true
    @Volatile
    private var personalDictionaryEnabled = true
    /** S135: dedicated switch for saved-email identity assist (F-004). Email
     *  addresses are the most identifying thing the keyboard ever retains, so
     *  they get their own control instead of riding the broad personal
     *  dictionary default; both must be on for identities to be recorded OR
     *  surfaced. */
    @Volatile
    private var identityAssistEnabled = true
    @Volatile
    private var persistenceScope: CoroutineScope? = null
    @Volatile
    private var phoneticStore: com.banglu.engine.platform.PhoneticIndexStore? = null
    @Volatile
    private var engineFullyLoaded = false

    internal fun getEngine(): SmartEngine = com.banglu.engine.util.runSynchronized(this) {
        val existing = engine
        if (existing != null) return@runSynchronized existing
        val newEngine = SmartEngine()
        newEngine.initializeSync()
        engine = newEngine
        newEngine
    }

    /**
     * S72: shed the transient conversion caches (word LRU + store memos)
     * under OS memory pressure. Never touches the dictionary, learned words,
     * or the attached store — purely re-computable state. Safe no-op before
     * the engine exists.
     */
    fun clearTransientCaches() {
        engine?.clearCache()
    }

    /**
     * Synchronous initialization: loads seed dictionary (~4K words).
     * Safe to call multiple times. Call before any conversions.
     */
    fun initializeSync() {
        getEngine()
    }

    /**
     * Full async initialization: loads extended dictionaries, 480K word list,
     * frequency data, disambiguation map, bigram model, and learned words.
     *
     * @param storage Platform-specific storage for learned words and dictionary cache
     * @param loader Platform-specific dictionary loader for large files
     */
    suspend fun initialize(storage: PlatformStorage, loader: DictionaryLoader? = null) {
        this.storage = storage

        if (loader == null) {
            // Light path (no dictionary loader — tests, learned-words-only
            // refresh): cheap, runs in place on the current engine.
            val eng = getEngine()
            eng.initialize(storage, null)
            eng.setUserBigrams(storage.getUserBigrams())
            applyPreferenceMaps(buildPreferenceMaps(eng, storage))
            eng.clearCache()
            // No loader means nothing further will load — the engine is as
            // complete as it gets, so learning is sound (S34 gate).
            engineFullyLoaded = true
            return
        }

        // S14: the full-dictionary load takes seconds. It must NEVER run on the
        // engine that is serving live keystrokes (which happens on the IME main
        // thread): a fresh engine is built entirely on a background dispatcher
        // and swapped in atomically at the end. Typing keeps hitting the current
        // (seed) engine — degraded quality for a few seconds, but never frozen.
        if (engineFullyLoaded) {
            // Rebuild path (profile / lite-mode change): drop the old full
            // engine BEFORE building the new one so two full dictionaries never
            // coexist on a 256MB heap.
            com.banglu.engine.util.runSynchronized(this) { engine = null }
            engineFullyLoaded = false
        }
        // Ensure a serving engine exists and sees the sqlite store.
        val serving = getEngine()
        phoneticStore?.let { serving.setPhoneticIndex(it) }

        val store = phoneticStore
        val built = withContext(Dispatchers.Default) {
            val fresh = SmartEngine()
            fresh.initializeSync()
            // Attach the precompiled index BEFORE initialize so the engine
            // skips the runtime corpus-index build.
            store?.let { fresh.setPhoneticIndex(it) }
            fresh.initialize(storage, loader)
            // Personalized next-word prediction: user pairs outrank corpus bigrams.
            fresh.setUserBigrams(storage.getUserBigrams())
            Pair(fresh, buildPreferenceMaps(fresh, storage))
        }

        // Publish: swap the fully-built engine in under the same monitor that
        // getEngine() synchronizes on, then apply the preference maps. Learning
        // events recorded during the build window were persisted to storage and
        // reappear on the next initialize; the in-session map entries are
        // rebuilt here from the storage snapshot.
        applyPreferenceMaps(built.second)
        com.banglu.engine.util.runSynchronized(this) { engine = built.first }
        engineFullyLoaded = true
        built.first.clearCache()
    }

    private suspend fun buildPreferenceMaps(
        eng: SmartEngine,
        storage: PlatformStorage
    ): Pair<Map<String, String>, Map<String, String>> {
        val custom = mutableMapOf<String, String>()
        val selected = mutableMapOf<String, String>()
        storage.getLearnedWords()
            .groupBy { it.phonetic.normalizedPhonetic() }
            .forEach { (phonetic, words) ->
                val customBest = words
                    .filter { it.frequency >= CUSTOM_CONVERSION_FREQUENCY }
                    .maxWithOrNull(
                        compareBy<com.banglu.engine.types.LearnedWord> { it.frequency }
                            .thenBy { it.lastUsed }
                    )
                // F5b sanitation: sub-custom (<120) learned entries are honored
                // only when the engine's gate oracle trusts them — real word,
                // clean floor of their own key, or approved composition. This
                // stops garbage learned by pre-gate builds (kkkkx -> ক্কক্কক্স)
                // from overriding the commit gate via selectedPreferenceMap.
                // Custom entries (>= CUSTOM_CONVERSION_FREQUENCY) are
                // user-authored — always exempt. Untrusted entries are SKIPPED
                // on load, never deleted from storage (reversible, no data
                // loss); without a validator/store oracle the check fails open
                // and keeps everything (the gate is dormant then anyway).
                val learnedBest = words
                    .filter {
                        it.frequency >= CUSTOM_CONVERSION_FREQUENCY ||
                            eng.isLearnedEntryTrusted(phonetic, it.bengali.trim())
                    }
                    .maxWithOrNull(
                        compareBy<com.banglu.engine.types.LearnedWord> { it.frequency }
                            .thenBy { it.lastUsed }
                    )
                if (customBest != null) {
                    custom[phonetic] = customBest.bengali
                } else if (learnedBest != null) {
                    selected[phonetic] = learnedBest.bengali
                }
            }
        return Pair(custom, selected)
    }

    private fun applyPreferenceMaps(maps: Pair<Map<String, String>, Map<String, String>>) {
        com.banglu.engine.util.runSynchronized(preferenceLock) {
            customPreferenceMap.clear()
            customPreferenceMap.putAll(maps.first)
            selectedPreferenceMap.clear()
            selectedPreferenceMap.putAll(maps.second)
        }
    }

    /**
     * Engine v3: attach the precompiled phonetic index store (Android: sqlite-backed).
     * Pre-attach (before [initialize]) lets the engine skip the runtime corpus-index build.
     * Post-attach (after [initialize]) is the first-install fallback when the db file was
     * not yet present at pre-attach time.
     * See [SmartEngine.setPhoneticIndex] for null-detach semantics.
     */
    fun setPhoneticIndex(store: PhoneticIndexStore?) {
        // Remembered so background-built engines (S14 swap) inherit the store.
        phoneticStore = store
        getEngine().setPhoneticIndex(store)
    }

    /**
     * Convert a single phonetic word to Bengali text.
     *
     * @param word English phonetic input (e.g., "ami")
     * @return Bengali text (e.g., "আমি")
     */
    fun convert(word: String): String = convertWord(word).bengali

    /**
     * Convert a single phonetic word with full result metadata.
     *
     * @param word English phonetic input
     * @return ConversionResult with Bengali text, confidence, source, and alternatives
     */
    fun convertWord(word: String): ConversionResult =
        applyUserPreference(word, getEngine().convertWord(word))

    /**
     * Convert with committed context available.
     *
     * The base conversion still comes from the normal engine, then the previous
     * Bengali word can promote one of the existing alternatives via the bigram
     * model. Explicit user dictionary conversions and learned choices remain the
     * final ranking layer.
     */
    fun convertWordWithContext(word: String, context: List<String>): ConversionResult {
        // S20: last two non-blank context words — trigram evidence first,
        // bigram fallback inside rerankWithContext.
        val recent = context.asReversed().filter { it.isNotBlank() }
        val eng = getEngine()
        val result = eng.convertWord(word)
        val contextRanked = eng.rerankWithContext(
            prev2Bengali = recent.getOrNull(1),
            prev1Bengali = recent.getOrNull(0),
            result = result
        )
        return applyUserPreference(word, contextRanked)
    }

    /**
     * Convert for live IME composing text. This is deliberately more
     * conservative than convertWord() so incomplete words do not jump through
     * fuzzy/recovery dictionary candidates while the user is still typing.
     *
     * S55 (F-ANDROID-003): must apply the SAME preference layer convertWord()
     * does, or a learned/explicit pick (ki -> কী) previews the engine's
     * unpreferred primary (কি) while Space commits the learned choice —
     * the preference map is adapter-owned state the engine never sees.
     */
    fun convertForComposing(
        word: String,
        previousBengali: String? = null,
        secondPreviousBengali: String? = null
    ): ConversionResult =
        applyUserPreference(word, getEngine().convertForComposing(word, previousBengali, secondPreviousBengali))

    fun getCompositionPreview(word: String): String = convertForComposing(word).bengali

    /** S28: rule-only instant echo — safe on the UI thread (see SmartEngine). */
    /**
     * S29: the instant echo must NEVER build the engine on the caller's (UI)
     * thread. During the cold-start seed load (engine == null) it falls back
     * to the raw buffer text; the async composing refine delivers the real
     * preview as soon as the seed build finishes.
     */
    fun convertForInstantPreview(word: String): String {
        val existing = engine ?: return word
        return existing.convertForInstantPreview(word)
    }

    /**
     * Parse multi-word input, converting each word and preserving whitespace.
     *
     * @param input Full phonetic input (may contain spaces)
     * @return Converted Bengali text with whitespace preserved
     */
    fun parse(input: String): String = getEngine().parse(input)

    /**
     * Get ranked suggestions for the current phonetic input.
     *
     * @param input Phonetic input
     * @param limit Maximum suggestions to return (default 5)
     * @return Sorted list of SmartSuggestion
     */
    fun getSuggestions(input: String, limit: Int = 5): List<SmartSuggestion> =
        enforceCuratedLoanwordPrimary(
            input,
            rerankSuggestionsByPreference(input, getEngine().getSuggestions(input, limit))
        )

    /**
     * Get suggestions with committed context available.
     *
     * The normal suggestion generator remains the source of candidates. Context
     * can only promote the same result that convertWordWithContext() would
     * commit, so the suggestion strip and Space commit do not disagree.
     */
    fun getSuggestionsWithContext(
        input: String,
        context: List<String>,
        limit: Int = 5
    ): List<SmartSuggestion> {
        val suggestions = getSuggestions(input, limit)
        val contextResult = convertWordWithContext(input, context)
        if (contextResult.bengali.isBlank()) return suggestions

        val index = suggestions.indexOfFirst { it.bengali == contextResult.bengali }
        val contextSuggestion = if (index >= 0) {
            suggestions[index].copy(
                confidence = maxOf(suggestions[index].confidence, contextResult.confidence),
                source = if (suggestions[index].source == "primary") "context_primary" else suggestions[index].source
            )
        } else {
            SmartSuggestion(
                bengali = contextResult.bengali,
                confidence = contextResult.confidence,
                source = "context_primary",
                phonetic = input.normalizedPhonetic(),
                tier = "tier0_context"
            )
        }

        return listOf(contextSuggestion) + suggestions.filterIndexed { i, suggestion ->
            i != index && suggestion.bengali != contextSuggestion.bengali
        }.take((limit - 1).coerceAtLeast(0))
    }

    /**
     * Get next-word predictions based on the bigram model.
     * Returns predicted Bengali words that commonly follow the given word.
     *
     * @param prevBengali The previously committed Bengali word
     * @param limit Maximum predictions to return (default 5)
     * @return List of PredictedWord with bengali text and confidence
     */
    fun getNextWordPredictions(prevBengali: String, limit: Int = 5): List<PredictedWord> =
        getEngine().getNextWordPredictions(prevBengali, limit)

    /** S20: two-word next-word prediction — exact (prev2, prev1) trigram
     *  followers lead, bigram/user predictions fill the remainder. */
    fun getNextWordPredictions(
        prev2Bengali: String?,
        prevBengali: String,
        limit: Int = 5
    ): List<PredictedWord> {
        val eng = getEngine()
        val tri = if (!prev2Bengali.isNullOrBlank()) {
            eng.getTrigramNextWordPredictions(prev2Bengali, prevBengali, limit)
        } else emptyList()
        if (tri.size >= limit) return tri.take(limit)
        val seen = tri.mapTo(mutableSetOf()) { it.bengali }
        val rest = eng.getNextWordPredictions(prevBengali, limit)
            .filter { it.bengali !in seen }
        return (tri + rest).take(limit)
    }

    /**
     * Record one observed (previous, next) Bengali commit pair for personalized
     * next-word prediction. The caller (IME) is responsible for verifying the
     * two words were actually committed adjacently in the same field.
     */
    fun recordNextWordUsage(previousBengali: String, nextBengali: String) {
        if (!learningEnabled || !personalDictionaryEnabled) return
        val prev = previousBengali.trim()
        val next = nextBengali.trim()
        if (prev.isEmpty() || next.isEmpty() || prev == next) return

        mutateLearning {
            val count = getEngine().recordUserBigram(prev, next)
            // S66: single-lane persistence dispatcher — off the UI thread AND
            // off Dispatchers.Default, so blob re-serialization never competes
            // with the latency-critical conversion pool.
            if (count > 0) persist { it.saveUserBigram(prev, next, count) }
        }
    }

    /**
     * Record a user's word selection for learning.
     * Stores ranking preference and persists asynchronously.
     *
     * @param phonetic The phonetic input the user typed
     * @param bengali The Bengali word the user selected
     * @param learnAsWord When true (commit of a clean-transliterated OOV word such
     *        as a name: rafsan -> রাফসান), also add the word to the engine
     *        dictionary so the next conversion is dictionary-backed instead of
     *        gate-floored.
     */
    fun onWordSelected(
        phonetic: String,
        bengali: String,
        learnAsWord: Boolean = false,
        explicitChoice: Boolean = false
    ) {
        // S44 (audit finding): ranking preferences may ONLY come from an
        // explicit user tap. A passive space commit carries no choice signal —
        // when trigram context promotes ম্যাচ over মাছ, recording that commit
        // as a preference poisoned the key globally on the device (the S26
        // equal-to-primary skip compared against the CONTEXT-FREE primary and
        // misread contextual promotions as deliberate divergence).
        mutateLearning {
            if (explicitChoice) {
                rememberPreferredConversion(phonetic, bengali, baseFrequency = 94)
            }
            if (learnAsWord) {
                val key = phonetic.normalizedPhonetic()
                val cleanBengali = bengali.trim()
                if (key.isEmpty() || cleanBengali.isBlank()) return@mutateLearning
                // OOV learning is implicit typing learning that mutates the
                // personal dictionary, so it respects BOTH runtime settings —
                // matching rememberPreferredConversion (learningEnabled) and
                // addCustomConversion (personalDictionaryEnabled).
                if (!learningEnabled || !personalDictionaryEnabled) return@mutateLearning
                getEngine().addWord(key, cleanBengali, 94)
                // REQUIRED after addWord: commit-gated cache entries must re-evaluate.
                getEngine().clearCache()
                persistLearnedWord(key, cleanBengali, 94)
            }
        }
    }

    /**
     * Add a user-authored conversion such as ottadhunik -> অত্যাধুনিক.
     * This uses the same ranking path as suggestion learning, but with a
     * stronger frequency so explicit dictionary entries win immediately.
     */
    fun addCustomConversion(phonetic: String, bengali: String) {
        val key = phonetic.normalizedPhonetic()
        val cleanBengali = bengali.trim()
        if (key.isEmpty() || cleanBengali.isBlank()) return
        if (!personalDictionaryEnabled) return

        mutateLearning {
            com.banglu.engine.util.runSynchronized(preferenceLock) {
                customPreferenceMap[key] = cleanBengali
            }
            getEngine().addWord(key, cleanBengali, CUSTOM_CONVERSION_FREQUENCY)
            getEngine().clearCache()
            persistLearnedWord(key, cleanBengali, CUSTOM_CONVERSION_FREQUENCY)
        }
    }

    fun configurePersistenceScope(scope: CoroutineScope?) {
        persistenceScope = scope
    }

    private fun persistLearnedWord(phonetic: String, bengali: String, frequency: Int) {
        // Off the Main-dispatched persistenceScope — see recordNextWordUsage.
        persist { it.saveLearnedWord(phonetic, bengali, frequency) }
    }

    /** S78: durable half of the primary-tap preference clear. */
    private fun removeLearnedPreference(phonetic: String) {
        persist { it.removeLearnedWord(phonetic) }
    }

    /**
     * In-session preferences are deliberately NOT run through the F5b
     * sanitation oracle: a tapped suggestion is the user's explicit choice
     * right now, and post-F2/F5 every surfaced candidate is gate-approved,
     * floor, or approved-composition by construction. The one exception is a
     * raw English passthrough tap (bengali == latin text) — that too is the
     * user's explicit choice and must be honored this session. On the NEXT
     * initialize, the persisted entry is re-evaluated by
     * [SmartEngine.isLearnedEntryTrusted] like everything else.
     *
     * S26: a selection that MATCHES the engine's current primary is not a
     * preference — it is a space commit (or first-chip tap) accepting whatever
     * the engine said. Recording it would freeze this build's ranking as a
     * "user choice" and silently veto engine fixes shipped in later updates
     * (observed on-device: line->লিনে accepted on 1.5.10 shadowed the 1.5.14
     * line->লাইন fix). Only divergent selections carry signal.
     */
    private fun rememberPreferredConversion(phonetic: String, bengali: String, baseFrequency: Int) {
        val key = phonetic.normalizedPhonetic()
        val cleanBengali = bengali.trim()
        if (key.isEmpty() || cleanBengali.isBlank()) return
        if (!learningEnabled || !personalDictionaryEnabled) return

        val primaryResult = getEngine().convertWord(key)
        if (cleanBengali == primaryResult.bengali) {
            // S78: tapping the engine's own primary back is the user's
            // correction signal — clear a stored divergent preference (memory
            // AND storage) instead of silently keeping it. Pre-S78 this
            // branch just returned, which made a preference un-undoable: the
            // stale pick kept resurfacing first forever (tester: "লাস্টবার
            // যেটা সিলেক্ট করেছিলাম সেটাই বার বার আসছে"). Recording the
            // primary as a preference stays forbidden (S26 — it would freeze
            // this build's ranking against future engine fixes).
            val removed = com.banglu.engine.util.runSynchronized(preferenceLock) {
                selectedPreferenceMap.remove(key) != null
            }
            if (removed) {
                getEngine().evictCachedWord(key)
                removeLearnedPreference(key)
            }
            return
        }
        com.banglu.engine.util.runSynchronized(preferenceLock) {
            selectedPreferenceMap[key] = cleanBengali
        }
        // Suggestion taps are preferences, not dictionary mutations. Explicit
        // user dictionary formulas still go through addCustomConversion().
        // S66: a preference affects only this key — evict it alone instead of
        // wiping the whole cache on every explicit tap (cache-thrash fix).
        getEngine().evictCachedWord(key)
        persistLearnedWord(key, cleanBengali, baseFrequency)
    }

    /**
     * Reset all learned words and reinitialize the engine.
     */
    suspend fun resetLearning() {
        storage?.clearLearnedWords()
        storage?.clearUserBigrams()
        com.banglu.engine.util.runSynchronized(preferenceLock) {
            customPreferenceMap.clear()
            selectedPreferenceMap.clear()
        }
        engine = null
        engineFullyLoaded = false
    }

    /**
     * S128 (production audit): the ONE authoritative erasure. Clears every
     * category of learned personal data — persisted (learned words, custom
     * conversions, bigrams, English learning, saved identities) AND live
     * in-memory state (preference maps, the English-typing engine, the
     * identity store, and the loaded engine itself, which is dropped and
     * rebuilt clean on next use). Conversion behavior is untouched.
     */
    // S136 (F-001): EVERY persistence write goes through persist(). The
    // erase bumps eraseGeneration BEFORE clearing memory; a write launched
    // earlier carries the old generation and is DROPPED when it finally runs,
    // so a stale snapshot can never resurrect erased data — no matter how the
    // single lane interleaves suspending writes (the re-audit's exact case).
    // The mutex serializes in-flight writes against the erase itself.
    private val persistenceMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile
    private var eraseGeneration = 0

    /** S138 (F-001): ONE lock serializes every learning mutation (memory
     *  update + snapshot + persist launch) against the erase's generation
     *  bump + memory clear. A mutation therefore either fully precedes the
     *  erase (its write carries the old generation and is dropped) or fully
     *  follows it (it mutates already-empty memory and persists legitimately).
     *  Always the OUTER lock — never taken while holding the adapter monitor
     *  or preferenceLock. */
    private val learningLock = Any()

    private fun <T> mutateLearning(block: () -> T): T =
        com.banglu.engine.util.runSynchronized(learningLock, block)

    private fun persist(block: suspend (PlatformStorage) -> Unit) {
        val s = storage ?: return
        val scope = persistenceScope ?: return
        val generation = eraseGeneration
        scope.launch(com.banglu.engine.util.persistenceDispatcher) {
            persistenceMutex.withLock {
                if (generation != eraseGeneration) return@withLock
                try {
                    block(s)
                } catch (_: Exception) {
                    // Persistence failure is non-critical
                }
            }
        }
    }

    suspend fun eraseAllLearning(): Boolean {
        // S136 (F-001): ORDER IS THE CONTRACT. Live memory is cleared FIRST,
        // then the persisted delete is queued on the single persistence lane
        // — every snapshot write launched before this point (recordIdentity,
        // English learning, learned words) sits ahead of it on that lane and
        // is erased by it, and every write launched after it serializes the
        // already-empty memory. Return value = the platform's durable-delete
        // confirmation; the caller must not claim success on false.
        mutateLearning {
            eraseGeneration++
            com.banglu.engine.util.runSynchronized(preferenceLock) {
                customPreferenceMap.clear()
                selectedPreferenceMap.clear()
            }
            englishTyping.clearLearning()
            englishLearningLoaded = false
            identityAssist.clear()
            identityLoaded = false
            // S135 (F-001): this runs inside the keyboard process while
            // keystrokes may be in flight on another thread — drop the engine
            // under the same monitor getEngine() publishes through.
            com.banglu.engine.util.runSynchronized(this) { engine = null }
            engineFullyLoaded = false
        }
        val store = storage ?: return true
        return withContext(com.banglu.engine.util.persistenceDispatcher) {
            persistenceMutex.withLock { store.clearAllLearningDataDurably() }
        }
    }

    /** S136 (F-004): identity-only erase with the same ordering/durability
     *  contract as [eraseAllLearning]. */
    suspend fun eraseIdentity(): Boolean {
        mutateLearning {
            eraseGeneration++
            identityAssist.clear()
        }
        val store = storage ?: return true
        return withContext(com.banglu.engine.util.persistenceDispatcher) {
            persistenceMutex.withLock { store.clearIdentityUserDataDurably() }
        }
    }

    /**
     * Runtime settings from Android preferences. The IME calls this whenever
     * settings are reloaded so learning can be disabled without rebuilding.
     */
    fun configureLearning(enabled: Boolean, personalDictionary: Boolean, identityAssist: Boolean = true) {
        learningEnabled = enabled
        personalDictionaryEnabled = personalDictionary
        identityAssistEnabled = identityAssist
    }

    // ── S96: English typing suite ────────────────────────────────────────
    // The engine object is cheap (wordlist rank map + one sorted copy) and
    // platform-free; ALL calls must arrive on the platform's single engine
    // lane (S75 law) — same contract as SmartEngine itself.

    private val englishTyping = com.banglu.engine.english.EnglishTypingEngine()
    @Volatile
    private var englishLearningLoaded = false

    /** Load the persisted English learning blob once (idempotent). */
    suspend fun ensureEnglishLearningLoaded() {
        if (englishLearningLoaded) return
        // S108: flag flips only AFTER the load lands — setting it first let a
        // concurrent englishAutocorrect run against an empty word list (the
        // exact hazard the doc comment warns about), and a failed load was
        // never retried. Callers all arrive on the single engine lane, so the
        // check-then-load pair cannot double-run.
        storage?.loadEnglishUserData()?.let { englishTyping.load(it) }
        englishLearningLoaded = true
    }

    fun englishCompletions(prefix: String, limit: Int = 3): List<String> =
        englishTyping.completions(prefix, limit, personalDictionaryEnabled)

    fun englishPredictions(prev: String?, limit: Int = 3): List<String> =
        englishTyping.predictions(prev, limit)

    /**
     * S97: autocorrect-on-commit decision. Pure in-memory (safe on the
     * keystroke path once warm). Returns null until the learning blob is
     * loaded — correcting before the user's own words are known could
     * replace a word they taught us.
     */
    fun englishAutocorrect(word: String): String? {
        if (!englishLearningLoaded) return null
        return englishTyping.autocorrect(word)
    }

    // ── S98: identity assist (emails / usernames — NEVER passwords) ──────
    // The IME's sensitive-field gate keeps password and OTP fields out
    // before any of these are called; recording additionally requires the
    // personal-dictionary setting.

    private val identityAssist = com.banglu.engine.assist.IdentityAssist()
    @Volatile
    private var identityLoaded = false

    suspend fun ensureIdentityLoaded() {
        if (identityLoaded) return
        // S108: same flip-after-load ordering as ensureEnglishLearningLoaded.
        storage?.loadIdentityUserData()?.let { identityAssist.load(it) }
        identityLoaded = true
    }

    /** S135 (F-004): identity assist runs only when BOTH the personal
     *  dictionary and the dedicated identity switch are on. Surfacing is
     *  gated as strictly as recording — a switched-off user must never see
     *  an address the keyboard remembered earlier. */
    fun identityAssistActive(): Boolean = personalDictionaryEnabled && identityAssistEnabled

    /** Domain completion from the built-in list always works (stores
     *  nothing); saved domains join in only while identity memory is on. */
    fun identityDomainSuggestions(token: String, limit: Int = 3): List<String> =
        identityAssist.domainSuggestions(token, limit, includeSaved = identityAssistActive())

    fun identitySavedFills(limit: Int = 3): List<String> =
        if (identityAssistActive()) identityAssist.savedIdentities(limit) else emptyList()

    fun identityIsEmailLikeToken(token: String): Boolean =
        identityAssist.isEmailLikeToken(token)

    fun recordIdentity(token: String) {
        if (!identityAssistActive()) return
        mutateLearning {
            identityAssist.recordIdentity(token)
            val snapshot = identityAssist.serialize()
            persist { it.saveIdentityUserData(snapshot) }
        }
    }

    fun clearIdentity() {
        mutateLearning {
            identityAssist.clear()
            persist { it.saveIdentityUserData("") }
        }
    }

    /**
     * A word the user committed in EN mode. Gated on the learning setting;
     * persistence is debounced onto the persistence lane (never the
     * keystroke path).
     */
    fun recordEnglishCommit(word: String, prev: String?) {
        if (!learningEnabled) return
        mutateLearning {
            englishTyping.recordCommit(word, prev)
            val snapshot = englishTyping.serialize()
            persist { it.saveEnglishUserData(snapshot) }
        }
    }

    /**
     * Clear the word conversion cache.
     */
    fun clearCache() {
        engine?.clearCache()
    }

    /**
     * Reset the adapter to its initial state. For testing only.
     */
    fun reset() {
        engine = null
        storage = null
        com.banglu.engine.util.runSynchronized(preferenceLock) {
            customPreferenceMap.clear()
            selectedPreferenceMap.clear()
        }
        learningEnabled = true
        personalDictionaryEnabled = true
        identityAssistEnabled = true
        persistenceScope = null
        phoneticStore = null
        engineFullyLoaded = false
    }

    private fun applyUserPreference(phonetic: String, result: ConversionResult): ConversionResult {
        if (!personalDictionaryEnabled) return result

        val key = phonetic.normalizedPhonetic()
        val curatedPrimary = CURATED_LOANWORD_PRIMARY[key]
        if (curatedPrimary != null && result.bengali == curatedPrimary) return result
        // S26: vetted English-intent flips (line -> লাইন) are preference-
        // immune exactly like curated loanwords. Pre-S26 builds auto-learned
        // every space commit, so devices that ever committed the OLD primary
        // (লিনে on 1.5.10) carry a stale "preference" that would veto the
        // shipped fix forever. ENGLISH_LEXICON source means the wrapper flip
        // actually fired — keep it.
        if (result.source == com.banglu.engine.types.ResolutionSource.ENGLISH_LEXICON &&
            SmartEngine.isEnglishPrimaryIntentKey(key)
        ) {
            return result
        }
        // S78: USER_HISTORY means the personal-bigram rerank promoted this
        // word from the user's OWN repeated commits after the current previous
        // word ("boi pore" -> পড়ে). That per-context signal outranks the
        // context-blind last-tap preference — otherwise one tap on পরে
        // anywhere would undo the বই-specific correction forever.
        if (result.source == com.banglu.engine.types.ResolutionSource.USER_HISTORY) {
            return result
        }

        val preferred: String = com.banglu.engine.util.runSynchronized(preferenceLock) {
            customPreferenceMap[key]
                ?: if (learningEnabled) selectedPreferenceMap[key] else null
        } ?: return result
        if (preferred == result.bengali) return result

        val alternatives = buildList {
            add(com.banglu.engine.types.Alternative(result.bengali, minOf(result.confidence, 0.88)))
            result.alternatives
                .filter { it.bengali != preferred && it.bengali != result.bengali }
                .forEach { add(it) }
        }
        return result.copy(
            bengali = preferred,
            confidence = maxOf(result.confidence, 0.96),
            alternatives = alternatives
        )
    }

    private fun rerankSuggestionsByPreference(
        phonetic: String,
        suggestions: List<SmartSuggestion>
    ): List<SmartSuggestion> {
        if (!personalDictionaryEnabled) return suggestions

        val key = phonetic.normalizedPhonetic()
        val preferred: String = com.banglu.engine.util.runSynchronized(preferenceLock) {
            customPreferenceMap[key]
                ?: if (learningEnabled) selectedPreferenceMap[key] else null
        } ?: return suggestions
        val index = suggestions.indexOfFirst { it.bengali == preferred }
        if (index < 0) {
            return listOf(
                SmartSuggestion(
                    bengali = preferred,
                    confidence = 1.0,
                    source = "user_preference",
                    phonetic = phonetic.normalizedPhonetic(),
                    tier = "user"
                )
            ) + suggestions.take((suggestions.size - 1).coerceAtLeast(0))
        }

        val selected = suggestions[index].copy(confidence = maxOf(suggestions[index].confidence, 1.0))
        return listOf(selected) + suggestions.take(index) + suggestions.drop(index + 1)
    }

    private fun enforceCuratedLoanwordPrimary(
        phonetic: String,
        suggestions: List<SmartSuggestion>
    ): List<SmartSuggestion> {
        val primary = CURATED_LOANWORD_PRIMARY[phonetic.normalizedPhonetic()] ?: return suggestions
        val index = suggestions.indexOfFirst { it.bengali == primary }
        val primarySuggestion = if (index >= 0) {
            suggestions[index].copy(confidence = maxOf(suggestions[index].confidence, 1.0))
        } else {
            SmartSuggestion(
                bengali = primary,
                confidence = 1.0,
                source = "dictionary_exact",
                phonetic = phonetic.normalizedPhonetic(),
                tier = "tier0_curated"
            )
        }

        return listOf(primarySuggestion) + suggestions.filterIndexed { i, _ -> i != index }
    }

    private fun String.normalizedPhonetic(): String = lowercase().trim()

    private val CURATED_LOANWORD_PRIMARY = buildMap {
        for (entry in EnglishPronunciationVariantData.ENTRIES) {
            for (phonetic in entry.phonetics) { val k = phonetic.normalizedPhonetic(); if (k !in this) put(k, entry.bengali) }
        }
        put("application", "এপ্লিকেশন")
        put("database", "ডেটাবেস")
        put("facebook", "ফেসবুক")
        put("google", "গুগল")
        put("honeymoon", "হানিমুন")
        put("whatsapp", "হোয়াটসঅ্যাপ")
        put("youtube", "ইউটিউব")
    }

    private const val CUSTOM_CONVERSION_FREQUENCY = 120
}
