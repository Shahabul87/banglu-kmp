package com.banglu.engine

import com.banglu.engine.ai.AIDisambiguator
import com.banglu.engine.ai.BigramModel
import com.banglu.engine.ai.EnglishDetector
import com.banglu.engine.ai.ViterbiDecoder
import com.banglu.engine.ai.WordCandidate
import com.banglu.engine.dictionary.BengaliWordValidator
import com.banglu.engine.dictionary.PhoneticOverlapScorer
import com.banglu.engine.dictionary.ProgressiveNarrowingEngine
import com.banglu.engine.dictionary.SectionNarrowingEngine
import com.banglu.engine.dictionary.SeedData
import com.banglu.engine.dictionary.SmartDictionary
import com.banglu.engine.disambiguation.DisambiguationScorer
import com.banglu.engine.disambiguation.SwapType
import com.banglu.engine.platform.DictionaryLoader
import com.banglu.engine.platform.PhoneticIndexHit
import com.banglu.engine.platform.PhoneticIndexStore
import com.banglu.engine.platform.PlatformStorage
import com.banglu.engine.rules.CleanTransliterator
import com.banglu.engine.rules.ConjunctResolver
import com.banglu.engine.rules.ConjunctTable
import com.banglu.engine.rules.NasalResolver
import com.banglu.engine.rules.NatvaVidhan
import com.banglu.engine.rules.ShatvaVidhan
import com.banglu.engine.rules.StatisticalDefaults
import com.banglu.engine.types.Alternative
import com.banglu.engine.types.ConversionResult
import com.banglu.engine.types.LookupResult
import com.banglu.engine.types.PredictedWord
import com.banglu.engine.types.ResolutionSource
import com.banglu.engine.types.SmartSuggestion
import com.banglu.engine.types.WordCategory
import com.banglu.engine.util.ReverseTransliterator
import com.banglu.engine.util.TypoCorrector

private const val USER_CUSTOM_CONVERSION_FREQUENCY = 120

/**
 * SmartEngine - 7-layer Bengali phonetic conversion orchestrator.
 *
 * Receives English phonetic input and converts to Bengali through:
 *   Layer 1:   Dictionary lookup (PhoneticTrie, ~4K+ seed entries)
 *   Layer 0:   Section narrowing (480K Bengali dictionary via BengaliSectionIndex)
 *   Layer 1.5: Root decomposition (stem + suffix + 480K validation)
 *              English detection (passthrough)
 *   Layer 2-4: Pattern engine (ConjunctResolver, NasalResolver, ShatvaVidhan, NatvaVidhan)
 *   Layer 5:   AIDisambiguator (swap rules: ন↔ণ, শ↔ষ, ত↔ট, etc.)
 *   Layer 5.5: Dictionary validation (character swap fixes against 480K)
 *   Layer 5.7: Conjunct removal recovery (remove hasanta to find valid words)
 *   Layer 6:   Bengali dictionary recovery (search 480K by Bengali similarity)
 */
data class SmartEngineConfig(
    val enableExternalDictionaries: Boolean = true,
    val maxSuggestions: Int = 5,
    val autoAcceptThreshold: Double = 0.90,
    val neuralConfidenceThreshold: Double = 0.70
)

class SmartEngine(private val config: SmartEngineConfig = SmartEngineConfig()) {

    // ======================== Components ========================

    val dictionary = SmartDictionary()
    private val disambiguator = AIDisambiguator()
    private val validator = BengaliWordValidator()
    private val bigramModel = BigramModel()
    // User-typed (prev -> next -> count) pairs. Personal typing habits outrank
    // the corpus in next-word prediction: the corpus is formal wiki/news
    // register while the user types chat register.
    private val userBigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private val sectionEngine = SectionNarrowingEngine()
    val narrowingEngine: ProgressiveNarrowingEngine
    private var viterbiDecoder: ViterbiDecoder? = null
    private var disambiguationMap: Map<String, String>? = null
    private var corpusPhoneticIndex: MutableMap<String, List<String>> = mutableMapOf()
    private var phoneticIndex: PhoneticIndexStore? = null
    private var initialized = false

    /**
     * Engine v3: attach the precompiled phonetic index (replaces the runtime corpus index).
     *
     * Attaching a store frees the runtime-built corpus map. Note: attaching null
     * AFTER store-mode initialization leaves corpus lookups empty until the engine
     * is re-initialized (buildCorpusPhoneticIndex is skipped in store mode); this
     * is acceptable because detaching a store is only done in tests.
     */
    fun setPhoneticIndex(store: PhoneticIndexStore?) {
        phoneticIndex = store
        if (store != null) corpusPhoneticIndex = mutableMapOf()
        clearCache()
    }

    // ======================== LRU Word Cache ========================

    private val wordCache = object : LinkedHashMap<String, ConversionResult>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ConversionResult>?): Boolean {
            return size > MAX_CACHE
        }
    }

    /**
     * LRU memo over [PhoneticIndexStore.lookupExact]. The composing preview and
     * the suggestion strip both query the same key on every keystroke; this
     * dedupes the double store read. Cleared in [clearCache] (and therefore on
     * [setPhoneticIndex]).
     */
    private val storeLookupMemo = object : LinkedHashMap<String, List<PhoneticIndexHit>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PhoneticIndexHit>>?): Boolean {
            return size > MAX_STORE_MEMO
        }
    }

    /**
     * LRU memo over [PhoneticIndexStore.containsWord] for the lite-mode commit
     * gate (validator not loaded, sqlite store attached). The gate can probe the
     * same Bengali string several times per commit (primary + alternatives +
     * composition roots); this bounds it to one indexed sqlite query per word.
     * Cleared in [clearCache] (and therefore on [setPhoneticIndex]).
     */
    private val containsWordMemo = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > MAX_STORE_MEMO
        }
    }

    private data class InflectionalSuffix(val phonetic: String, val bengali: String)

    private data class CandidatePath(
        val bengali: String,
        val score: Double,
        val literal: Boolean
    )

    private data class TokenExpansion(
        val out: String,
        val prior: Double,
        val literal: Boolean = true
    )

    private val inflectionalSuffixes: List<InflectionalSuffix> = listOf(
        InflectionalSuffix("echchhen", "েচ্ছেন"),
        InflectionalSuffix("echchhe", "েচ্ছে"),
        InflectionalSuffix("echchhi", "েচ্ছি"),
        InflectionalSuffix("chchhen", "চ্ছেন"),
        InflectionalSuffix("chchhe", "চ্ছে"),
        InflectionalSuffix("chchhi", "চ্ছি"),
        InflectionalSuffix("chen", "ছেন"),
        InflectionalSuffix("chi", "ছি"),
        InflectionalSuffix("che", "ছে"),
        InflectionalSuffix("gulo", "গুলো"),
        InflectionalSuffix("guli", "গুলি"),
        InflectionalSuffix("der", "দের"),
        InflectionalSuffix("ter", "তের"),
        InflectionalSuffix("era", "েরা"),
        InflectionalSuffix("er", "ের"),
        InflectionalSuffix("ke", "কে"),
        InflectionalSuffix("te", "তে"),
        InflectionalSuffix("ra", "রা"),
        InflectionalSuffix("e", "ে"),
        InflectionalSuffix("r", "র"),
        InflectionalSuffix("y", "য়"),
    )

    companion object {
        const val MAX_CACHE = 2000
        private const val MAX_STORE_MEMO = 128
        private const val MAX_SUGGESTION_CANDIDATES = 40

        /**
         * S1/D1, tightened S19 (WYSIWYG divergence audit, register study
         * 2026-07-06): the editor primary ALWAYS leads the strip. The study
         * found ~120 words where the strip's first chip differed from what
         * Space commits (chil: strip led চিল, commit ছিল) — the first chip
         * is the commit contract and may never disagree with it. Exact-key
         * competitors that outscore the primary sit at ranks 2+, still one
         * tap away (ghoro: ঘর first, ঘরোয়া/ঘোরা behind it).
         */
        private const val MAX_PRIMARY_STRIP_RANK = 1

        /**
         * S8: log-scale frequency gap (~25x real usage on the 60-100 corpus
         * scale) beyond which a tier-A habit-alias hit outranks the tier-A
         * canonical owner of the same key. See [applyEvidenceMargin].
         */
        private const val ALIAS_EVIDENCE_MARGIN = 12

        /**
         * S1/D3: minimum 484K-validator frequency for a stem to anchor an
         * inflection the validator itself does not attest (see
         * [SmartEngine.isCompositionStemTrusted]). Junk web-corpus aliases sit
         * below it (যেলা@1, যাতি@25); real roots sit above (ফার্স@37, তৃতীয়@73).
         */
        private const val MIN_COMPOSITION_STEM_FREQUENCY = 30

        /**
         * Seed/learned dictionary frequency at which an entry is a deliberate
         * user word (learnAsWord/user-dictionary convention, see
         * [SmartEngine.isLearnedEntryTrusted] docs) — always a trusted stem.
         */
        private const val USER_WORD_FREQUENCY_FLOOR = 120

        /** Bengali digits ০-৯ */
        private const val BENGALI_DIGITS = "০১২৩৪৫৬৭৮৯"

        /** Punctuation mapping (longest match first) */
        private val PUNCTUATION = linkedMapOf(
            "..." to "...",
            ".." to "।।",
            "." to "।",
            ":" to "ঃ",
            "^" to "ঁ",
            "$" to "৳",
            "," to ",",
            "!" to "!",
            "?" to "?",
            ";" to ";",
            "(" to "(",
            ")" to ")",
            "[" to "[",
            "]" to "]",
            "{" to "{",
            "}" to "}",
            "\"" to "\"",
            "'" to "'",
            "-" to "-",
            "/" to "/",
            "\\" to "\\",
            "=" to "=",
            "+" to "+",
        )

        private val STABLE_COMPOSITION_FRAGMENTS = setOf(
            "a", "aa", "i", "ii", "u", "uu", "e", "oi", "o", "ou",
            "k", "kh", "g", "gh", "ng",
            "c", "ch", "chh", "j", "jh",
            "t", "th", "d", "dh", "n",
            "p", "ph", "f", "b", "bh", "m",
            "r", "l", "sh", "s", "h", "y", "w", "v"
        )

        private val ENGLISH_VARIANT_PRIMARY_BY_KEY: Map<String, String> by lazy {
            val best = linkedMapOf<String, Pair<String, Int>>()
            for (entry in SeedData.SEED_DICTIONARY) {
                if (entry.category != WordCategory.FOREIGN && entry.category != WordCategory.PROPER) continue
                for (phonetic in entry.phonetics) {
                    val key = phonetic.lowercase().trim()
                    if (key.isBlank()) continue
                    val previous = best[key]
                    if (previous == null || entry.frequency > previous.second) {
                        best[key] = entry.bengali to entry.frequency
                    }
                }
            }
            best.mapValues { it.value.first }
        }

        /**
         * S24: English keys whose loanword intent overwhelmingly dominates the
         * evidenced Bengali inflection squatting on the key (time -> টাইম over
         * টিমে). Keep SMALL and unambiguous — name/নামে-class words must never
         * enter this list.
         */
        private val ENGLISH_PRIMARY_INTENT: Set<String> = setOf(
            "time", "common", "printer", "price", "single", "double",
            "simple", "share", "video", "table", "hotel", "note", "line",
            "online", "offline"
        )

        /**
         * S26: the adapter's preference layer needs to know these keys —
         * vetted intent flips are preference-immune (like curated loanwords),
         * or a stale learned entry from an older build keeps overriding the
         * shipped fix forever.
         */
        internal fun isEnglishPrimaryIntentKey(key: String): Boolean =
            key in ENGLISH_PRIMARY_INTENT

        private val MOBILE_SHORTHAND_OVERRIDES: Map<String, String> = mapOf(
            "amr" to "আমার",
            "tomr" to "তোমার",
            "tmi" to "তুমি",
            "tomi" to "তুমি",
            // S21: jac-/khac- continuous chat class (S17 leftover). The chat
            // stem is the -চ্ছ- form (যাচ্ছি "jacchi") but the continuous is
            // written with the dialect -তেছি/-তেসি tail: jactesi = যাইতেছি.
            // No habit chain can derive ite -> c, so the class is enumerated.
            // Attached negation (jactesina) resolves via tryNegationCompound,
            // whose prefix conversion re-enters this map.
            "jactesi" to "যাইতেছি",
            "jactechi" to "যাইতেছি",
            "jacteci" to "যাইতেছি",
            "khactesi" to "খাইতেছি",
            "khactechi" to "খাইতেছি",
            "khacteci" to "খাইতেছি",
        )

        /** Surfacing threshold for personal pairs — one occurrence may be a typo. */
        private const val USER_BIGRAM_MIN_COUNT = 2

        /** Retention cap per previous-word so a runaway context can't grow unbounded. */
        private const val USER_BIGRAM_MAX_FOLLOWERS = 24

        /**
         * Words that must never be *predicted* (still fully typeable). These are
         * wiki-meta and title-transliteration artifacts that rank high in the
         * corpus bigram table but are never a plausible chat continuation
         * (ভালো -> নিবন্ধ comes from "good article" wiki badges; তুমি -> অ্যান্ড
         * from song/movie titles).
         */
        private val PREDICTION_STOPLIST = setOf(
            "নিবন্ধ", "নিবন্ধটি", "নিবন্ধের",
            "উইকিপিডিয়া", "উইকিপিডিয়ার", "উইকি",
            "তথ্যসূত্র", "বিষয়শ্রেণী", "টেমপ্লেট", "প্রবেশদ্বার",
            "অ্যান্ড", "এন্ড", "দ্য", "দি", "অব", "অভ"
        )

        private val FALLBACK_NEXT_WORDS: Map<String, List<String>> = mapOf(
            "আমি" to listOf("ভালো", "এখন", "আজ", "যাবো", "চাই", "করতে"),
            "তুমি" to listOf("কেমন", "কি", "আজ", "যাবে", "পারবে", "করো"),
            "আপনি" to listOf("কেমন", "কি", "আজ", "করবেন", "পারবেন", "যাবেন"),
            "আমার" to listOf("মনে", "একটা", "কাছে", "বাড়ি", "কাজ", "সময়"),
            "তোমার" to listOf("কি", "কাছে", "মনে", "বাড়ি", "কাজ", "সময়"),
            "সে" to listOf("আজ", "এখন", "ভালো", "যাবে", "করবে", "আসে"),
            "আমরা" to listOf("আজ", "এখন", "একসাথে", "যাবো", "করবো", "পারবো"),
            "ভালো" to listOf("আছি", "লাগছে", "হবে", "কাজ", "মানুষ", "বাসা"),
            "কেমন" to listOf("আছো", "আছেন", "লাগছে", "হলো", "হবে", "করবে"),
            "আজ" to listOf("আমি", "তুমি", "আমরা", "রাতে", "সকালে", "একটা"),
            "এখন" to listOf("আমি", "তুমি", "কি", "কাজ", "যাচ্ছি", "করছি"),
            "কি" to listOf("করছো", "করছেন", "হবে", "লাগবে", "খবর", "সমস্যা"),
            "না" to listOf("আমি", "তুমি", "হবে", "করলে", "গেলে", "থাকলে"),
        )

        private val DIRECT_WORD_OVERRIDES: Map<String, List<String>> = mapOf(
            "keno" to listOf("কেনো", "কেন"),
            "koto" to listOf("কতো", "কত"),
            "biswsa" to listOf("বিশ্বসা", "বিশ্বাস"),
            "application" to listOf("এপ্লিকেশন", "অ্যাপ্লিকেশন"),
            "sobbdo" to listOf("শব্দ", "সব্বদো"),
            "honeymoon" to listOf("হানিমুন", "honeymoon"),
            "education" to listOf("এডুকেশন"),
            "system" to listOf("সিস্টেম"),
            "travel" to listOf("ট্রাভেল"),
            "plan" to listOf("প্ল্যান"),
            "nasa" to listOf("নাসা"),
            "unesco" to listOf("ইউনেস্কো"),
            "byatha" to listOf("ব্যথা", "ব্যাথা"),
            "bhiti" to listOf("ভিতি", "ভীতি"),
            "porikkha" to listOf("পরীক্ষা", "পরিক্ষা"),
            "accha" to listOf("আচ্ছা"),
            "acca" to listOf("আচ্ছা"),
            "assa" to listOf("আচ্ছা"),
            "cina" to listOf("ছিনা", "চিনা"),
            "gobeshona" to listOf("গবেষণা", "গবেষনা"),
            "database" to listOf("ডেটাবেস", "ডাটাবেস"),
            "onekkhon" to listOf("অনেকক্ষণ"),
            "priyojon" to listOf("প্রিয়জন"),
            "ghoro" to listOf("ঘর"),
            "mathematics" to listOf("ম্যাথমেটিক্স", "ম্যাথেমেটিক্স"),
            "montri" to listOf("মন্ত্রী", "মন্ত্রি"),
            "ghoshona" to listOf("ঘোষণা", "ঘোষনা"),
            "koti" to listOf("কোটি", "কটি"),
            "mangsho" to listOf("মাংস", "মাংসো"),
            "ojon" to listOf("ওজন", "ওজোন"),
            "shahid" to listOf("শহীদ", "শাহিদ"),
            "puroshkar" to listOf("পুরস্কার", "পুরষ্কার"),
            "shilpi" to listOf("শিল্পী", "শিল্পি"),
            "poribohon" to listOf("পরিবহন", "পরিবহণ"),
            "tyag" to listOf("ত্যাগ", "ট্যাগ"),
            "rahman" to listOf("রহমান", "রাহমান"),
            "rohman" to listOf("রহমান", "রোহমান"),
            "jodi" to listOf("যদি", "জদি"),
            "jemon" to listOf("যেমন", "জেমন"),
            "the" to listOf("থে", "ঠে", "দ্য"),
            "is" to listOf("ইস", "ইশ"),
            "for" to listOf("ফর", "ফড়"),
            "shikka" to listOf("শিক্ষা", "সিক্কা"),
            "bishse" to listOf("বিশ্বে", "বিশসে", "বিষয়ে"),
            "cheleraa" to listOf("ছেলেরা", "চেলেরা"),
            "chelera" to listOf("ছেলেরা", "চেলেরা"),
            "meyder" to listOf("মেয়েদের", "মেয়দের"),
            "meyeder" to listOf("মেয়েদের"),
            "puruskar" to listOf("পুরস্কার", "পুরষ্কার"),
            "santi" to listOf("শান্তি", "সান্তি"),
            "sohor" to listOf("শহর", "সহর"),
            "chasar" to listOf("চাষার", "চাষের"),
            "chashar" to listOf("চাষার", "চাষের"),
            "ashar" to listOf("আশার", "আষাঢ়"),
            "class" to listOf("ক্লাস"),
            "klas" to listOf("ক্লাস"),
            "block" to listOf("ব্লক"),
            "blok" to listOf("ব্লক"),
            "glass" to listOf("গ্লাস"),
            "glas" to listOf("গ্লাস"),
            "torko" to listOf("তর্ক"),
            "sworgo" to listOf("স্বর্গ"),
            "sorgo" to listOf("স্বর্গ"),
            "dhormo" to listOf("ধর্ম"),
            "ortho" to listOf("অর্থ"),
            "tbk" to listOf("ত্বক"),
            "sbpno" to listOf("স্বপ্ন"),
            "bagmii" to listOf("বাগ্মী"),
            "padma" to listOf("পদ্মা"),
            "shanto" to listOf("শান্ত"),
            "gondho" to listOf("গন্ধ"),
            "vondo" to listOf("ভণ্ড"),
            "biggan" to listOf("বিজ্ঞান"),
            "trivuj" to listOf("ত্রিভুজ"),
            "tribhuj" to listOf("ত্রিভুজ"),
            "shroddha" to listOf("শ্রদ্ধা"),
            "shokto" to listOf("শক্ত"),
            "mugdho" to listOf("মুগ্ধ"),
            "anondo" to listOf("আনন্দ"),
            "sompod" to listOf("সম্পদ"),
            "koshto" to listOf("কষ্ট"),
            "sthan" to listOf("স্থান"),
            "buddho" to listOf("বুদ্ধ"),
            "buddhi" to listOf("বুদ্ধি"),
            "bidya" to listOf("বিদ্যা"),
            "chihno" to listOf("চিহ্ন"),
            "brohmmo" to listOf("ব্রহ্ম"),
            "brohmana" to listOf("ব্রাহ্মণ"),
            "anko" to listOf("অঙ্ক"),
            "bangla" to listOf("বাংলা"),
            "ichcha" to listOf("ইচ্ছা"),
            "iccha" to listOf("ইচ্ছা"),
            "lojja" to listOf("লজ্জা"),
            "jonmo" to listOf("জন্ম"),
            "jonne" to listOf("জন্যে"),
            "jonnye" to listOf("জন্যে"),
            "pollii" to listOf("পল্লী"),
            "baxo" to listOf("বাক্স"),
            "koxobajar" to listOf("কক্সবাজার"),
            "okka" to listOf("অক্কা"),

            // Vowel-after-consonant exception verbs. Normally a/o/u after a
            // consonant becomes a dependent kar, but these spoken verb stems
            // keep an independent ও/ওয়া sound: খাওয়া, যাওয়া, পাওয়া, etc.
            // Keep the table here so dictionary duplicates like hoya→হয়া cannot
            // outrank the intended mobile phonetic spelling hoya/howa→হওয়া.
            "khawa" to listOf("খাওয়া"),
            "khaowa" to listOf("খাওয়া"),
            "khaoya" to listOf("খাওয়া"),
            "khaoa" to listOf("খাওয়া"),
            "khawar" to listOf("খাওয়ার"),
            "khaowar" to listOf("খাওয়ার"),
            "khaoyar" to listOf("খাওয়ার"),
            "jawa" to listOf("যাওয়া"),
            "jaowa" to listOf("যাওয়া"),
            "jaoya" to listOf("যাওয়া"),
            "jaoa" to listOf("যাওয়া"),
            "zawa" to listOf("যাওয়া"),
            "zaowa" to listOf("যাওয়া"),
            "jawar" to listOf("যাওয়ার"),
            "jaowar" to listOf("যাওয়ার"),
            "jaoyar" to listOf("যাওয়ার"),
            "pawa" to listOf("পাওয়া"),
            "paowa" to listOf("পাওয়া"),
            "paoya" to listOf("পাওয়া"),
            "paoa" to listOf("পাওয়া"),
            "pawar" to listOf("পাওয়ার"),
            "paowar" to listOf("পাওয়ার"),
            "paoyar" to listOf("পাওয়ার"),
            "chawa" to listOf("চাওয়া"),
            "chaowa" to listOf("চাওয়া"),
            "chaoya" to listOf("চাওয়া"),
            "chaoa" to listOf("চাওয়া"),
            "cawa" to listOf("চাওয়া"),
            "caowa" to listOf("চাওয়া"),
            "cawar" to listOf("চাওয়ার"),
            "chawar" to listOf("চাওয়ার"),
            "howa" to listOf("হওয়া"),
            "hoya" to listOf("হওয়া", "হয়া", "হোয়া"),
            "hoowa" to listOf("হওয়া"),
            "hoa" to listOf("হওয়া"),
            "howar" to listOf("হওয়ার"),
            "hoyar" to listOf("হওয়ার", "হয়ার"),
            "newa" to listOf("নেওয়া"),
            "neowa" to listOf("নেওয়া"),
            "neoya" to listOf("নেওয়া"),
            "neoa" to listOf("নেওয়া"),
            "newar" to listOf("নেওয়ার"),
            "neowar" to listOf("নেওয়ার"),
            "dewa" to listOf("দেওয়া"),
            "deowa" to listOf("দেওয়া"),
            "deoya" to listOf("দেওয়া"),
            "deoa" to listOf("দেওয়া"),
            "dewar" to listOf("দেওয়ার"),
            "deowar" to listOf("দেওয়ার"),
            "nawa" to listOf("নাওয়া", "নেওয়া"),
            "naowa" to listOf("নাওয়া", "নেওয়া"),
            "naoya" to listOf("নাওয়া", "নেওয়া"),
            "naoa" to listOf("নাওয়া", "নেওয়া"),
            "nowa" to listOf("নওয়া", "নেওয়া", "নাওয়া"),
            "nowar" to listOf("নওয়ার", "নেওয়ার", "নাওয়ার"),
        )
    }

    init {
        narrowingEngine = ProgressiveNarrowingEngine(dictionary)
    }

    // ======================== INITIALIZATION ========================

    /**
     * Synchronous initialization: loads seed dictionary (~4K words).
     * Call this before any conversions. Safe to call multiple times.
     */
    fun initializeSync() {
        if (initialized) return
        dictionary.initialize()
        // Initialize disambiguator with seed Bengali words
        disambiguator.initialize(SeedData.SEED_DICTIONARY.map { it.bengali })
        initialized = true
    }

    /** Test seam: load validator words directly (production uses initialize(loader)). */
    internal fun loadValidatorWords(words: List<String>) {
        validator.loadWords(words)
        clearCache()
    }

    /** Test seam: load validator frequency data directly (production uses initialize(loader)). */
    internal fun loadValidatorFrequencies(frequencies: Map<String, Int>) {
        validator.loadFrequencies(frequencies)
        clearCache()
    }

    /**
     * Async initialization: loads extended dictionaries, 480K word list,
     * frequency data, disambiguation map, bigram model, and learned words.
     *
     * @param storage Platform-specific storage for learned words (optional)
     * @param loader Platform-specific dictionary loader (optional)
     */
    suspend fun initialize(storage: PlatformStorage? = null, loader: DictionaryLoader? = null) {
        if (!initialized) initializeSync()

        // S4/C2 load order: the 480K word list + frequency map come FIRST —
        // their loaders materialize transient full-size copies (cursor strings,
        // the interface's Map) that need heap headroom, while their resident
        // cost is small (one sorted array + one IntArray). The extended
        // dictionary trie — by far the largest RESIDENT structure — is built
        // LAST. The previous trie-first order left a 256MB device heap with
        // <1MB free by the time the frequency cursor started reading, OOMing
        // full-mode load.

        // Load 480K word list
        loader?.loadFullDictionary()?.let { words ->
            validator.loadWords(words)
            sectionEngine.initialize(validator)
            // S4/C2: membership oracle, NOT a second 472K-entry HashSet copy —
            // the duplicate set OOMed 256MB-heap devices during full-mode load.
            disambiguator.setExtendedMembership(validator::isValid)
            if (phoneticIndex == null) buildCorpusPhoneticIndex(words)
        }

        // Load frequency data
        loader?.loadFrequencyMap()?.let { freqs ->
            validator.loadFrequencies(freqs)
            if (phoneticIndex == null) sortCorpusPhoneticIndex()
        }

        // Load extended dictionary if available
        loader?.loadExtendedDictionary()?.let { entries ->
            dictionary.addEntries(entries)
        }

        // Load disambiguation map
        loader?.loadDisambiguationMap()?.let { map ->
            disambiguationMap = map
        }

        // Load bigram model
        loader?.loadBigramModel()?.let { data ->
            bigramModel.loadFromData(data)
            viterbiDecoder = ViterbiDecoder(bigramModel)
        }

        // Load learned words
        storage?.getLearnedWords()?.let { learned ->
            for (word in learned) {
                if (word.frequency >= USER_CUSTOM_CONVERSION_FREQUENCY) {
                    addWord(word.phonetic, word.bengali, word.frequency)
                }
            }
        }

        clearCache()
    }

    private fun buildCorpusPhoneticIndex(words: List<String>) {
        val buckets = mutableMapOf<String, MutableList<String>>()
        val seenByKey = mutableMapOf<String, MutableSet<String>>()
        val bengaliOnly = Regex("^[\\u0980-\\u09FF]+$")
        val romanOnly = Regex("^[a-z]+$")

        for (rawWord in words) {
            val word = rawWord.trim()
            if (word.length !in 2..18) continue
            if (!bengaliOnly.matches(word)) continue
            if (word.endsWith("্")) continue

            val phonetic = ReverseTransliterator.reverseWord(word).lowercase()
            for (alias in corpusPhoneticAliases(phonetic)) {
                if (alias.length !in 2..24 || !romanOnly.matches(alias)) continue

                val seen = seenByKey.getOrPut(alias) { mutableSetOf() }
                if (!seen.add(word)) continue
                buckets.getOrPut(alias) { mutableListOf() }.add(word)
            }
        }

        corpusPhoneticIndex = buckets.mapValues { it.value.toList() }.toMutableMap()
        sortCorpusPhoneticIndex()
    }

    private fun corpusPhoneticAliases(phonetic: String): List<String> {
        val aliases = linkedSetOf(phonetic)

        // User-facing lowercase scheme: c is often intended for ছ, while ch
        // remains available for চ and legacy inputs. The reverse transliterator
        // emits ছ as chh, so add c-aliases for every real corpus word containing ছ.
        if (phonetic.contains("chh")) {
            aliases.add(phonetic.replace("chh", "c"))
        }

        return aliases.toList()
    }

    private fun sortCorpusPhoneticIndex() {
        if (corpusPhoneticIndex.isEmpty()) return

        corpusPhoneticIndex = corpusPhoneticIndex.mapValues { (_, words) ->
            words.sortedWith(
                compareByDescending<String> { validator.getFrequency(it) }
                    .thenBy { it.length }
                    .thenBy { it }
            ).take(16)
        }.toMutableMap()
    }

    /**
     * Single memoized entry point for all [PhoneticIndexStore.lookupExact] reads.
     * Returns an empty list when no store is attached.
     */
    private fun storeLookup(key: String): List<PhoneticIndexHit> {
        val store = phoneticIndex ?: return emptyList()
        storeLookupMemo[key]?.let { return it }
        val hits = applyEvidenceMargin(store.lookupExact(key))
        storeLookupMemo[key] = hits
        return hits
    }

    /**
     * S10 fragment sanity (residual ri-kar previews, study §5 follow-up): the
     * dictionary layer's fuzzy path can claim a mid-word fragment with a word
     * that does not own the typed key at all (poriko → পৃথক@0.85 while the
     * user is typing পরিকল্পনা). The discriminator between a fragment and a
     * completed typo: fragments have canonical continuations in the store.
     * When the match is unfaithful (< 0.90, word does not own the key) AND
     * canonical continuations exist, skip the layer — the pipeline floors to
     * the faithful pattern conversion and the strip fills with continuations.
     */
    private fun isUnfaithfulFragmentMatch(key: String, result: ConversionResult): Boolean {
        if (result.confidence >= 0.90) return false
        val store = phoneticIndex ?: return false
        if (storeLookup(key).any { it.bengali == result.bengali }) return false
        return store.lookupPrefix(key, 4).any { it.priority == PhoneticIndexHit.PRIORITY_CANONICAL }
    }

    /**
     * S9: append tier-A store hits for [key] as low-confidence alternatives
     * (deduped, capped) so downstream context reranking sees every real
     * homophone of the typed key, whichever layer produced the primary.
     */
    private fun withStoreAlternatives(key: String, result: ConversionResult): ConversionResult {
        val hits = storeLookup(key)
        if (hits.isEmpty()) return result
        val seen = mutableSetOf(result.bengali)
        result.alternatives.forEach { seen.add(it.bengali) }
        val extra = hits
            .filter { it.tier == PhoneticIndexHit.TIER_A && seen.add(it.bengali) }
            .take(4)
            .map { Alternative(it.bengali, 0.80) }
        if (extra.isEmpty()) return result
        return result.copy(alternatives = result.alternatives + extra)
    }

    /**
     * S8 within-tier evidence margin (y-drop study follow-up, docs/engine-
     * conjunct-study §4): the store orders tier-A hits canonical-owner-first,
     * which is right for ties and modest gaps (jon → জন before জন্য). But when
     * a habit-alias claimant out-uses the canonical owner by
     * [ALIAS_EVIDENCE_MARGIN] on the 60-100 log scale (~25x real usage), the
     * canonical owner is in practice a rare word squatting on a common typing
     * (songkha: সংখা vs সংখ্যা; suru: সুরু vs শুরু — the user's own learned
     * words agree) and the alias takes the key.
     */
    private fun applyEvidenceMargin(hits: List<PhoneticIndexHit>): List<PhoneticIndexHit> {
        if (hits.size < 2) return hits
        val top = hits.first()
        if (top.tier != PhoneticIndexHit.TIER_A ||
            top.priority != PhoneticIndexHit.PRIORITY_CANONICAL
        ) return hits
        // Sorted (tier, priority, freq desc): the first tier-A alias hit is
        // the strongest challenger.
        val challenger = hits.firstOrNull {
            it.tier == PhoneticIndexHit.TIER_A && it.priority > PhoneticIndexHit.PRIORITY_CANONICAL
        } ?: return hits
        if (challenger.frequency < top.frequency + ALIAS_EVIDENCE_MARGIN) return hits
        return listOf(challenger) + hits.filter { it !== challenger }
    }

    /**
     * Suggestible (Tier A) corpus words for [key], frequency-descending.
     * Store mode reads the precompiled index; legacy mode reads the
     * runtime-built corpus map (already sorted frequency-descending).
     */
    private fun corpusWordsFor(key: String): List<String> =
        if (phoneticIndex != null) {
            storeLookup(key).filter { it.tier == PhoneticIndexHit.TIER_A }.map { it.bengali }
        } else {
            corpusPhoneticIndex[key].orEmpty()
        }

    /**
     * Frequency of [bengali] in the attached store for [key] (0 when no store,
     * or no such entry). Mirrors tryCorpusPhoneticLookup's normalized-key
     * fallback so hits found via ee→i/oo→u collapse rank with their real
     * frequency. Cheap: backed by the [storeLookupMemo].
     */
    private fun storeFrequencyOf(key: String, bengali: String): Int {
        if (phoneticIndex == null) return 0
        storeLookup(key).firstOrNull { it.bengali == bengali }?.let { return it.frequency }
        val normalized = normalizeIndexQuery(key)
        if (normalized == key) return 0
        return storeLookup(normalized).firstOrNull { it.bengali == bengali }?.frequency ?: 0
    }

    /**
     * @param minKeyLength shortest key allowed to consult the index. Composing
     * call sites keep the default 3 so one/two-key live defaults stay stable
     * (t→ত, ta→তা); commit call sites ([convertWord]) pass 2 so short corpus
     * words (ob→অব) resolve through the compiled store. 1-char keys are
     * always excluded.
     */
    private fun tryCorpusPhoneticLookup(key: String, minKeyLength: Int = 3): ConversionResult? {
        if (key.length < maxOf(minKeyLength, 2)) return null

        // Engine v3: precompiled phonetic index (sqlite), replaces the runtime corpus index.
        phoneticIndex?.let { store ->
            val hits = storeLookup(key).ifEmpty {
                val normalized = normalizeIndexQuery(key)
                if (normalized != key) storeLookup(normalized) else emptyList()
            }
            if (hits.isEmpty()) return null
            // S7 continuation preference (study W3): when the best exact hit only
            // owns this key through a habit alias (priority > 0) and a canonical
            // (priority-0) word at least as common continues the typed key, the
            // alias must not become primary — mid-word it reads as the engine
            // "panicking" (brit flashing বৃত্ত while the user types british).
            // hits are (tier, priority)-sorted, so priority > 0 at the top means
            // no canonical word owns this exact key at all.
            val top = hits.first()
            if (top.priority > 0 && top.tier == PhoneticIndexHit.TIER_A &&
                store.lookupPrefix(key, 8).any { it.priority == 0 && it.frequency >= top.frequency }
            ) {
                return null
            }
            val alternatives = hits.drop(1).take(config.maxSuggestions - 1)
                .mapIndexed { index, hit -> Alternative(hit.bengali, maxOf(0.72, 0.92 - index * 0.04)) }
            return ConversionResult(top.bengali, 0.96, ResolutionSource.DICTIONARY, alternatives)
        }

        // Legacy path (no store attached): runtime-built corpus index keeps the
        // original 3-char floor — its 2-char buckets are noisy 480K fragments
        // without the compiled store's curation.
        if (key.length < 3) return null
        if (corpusPhoneticIndex.isEmpty()) return null

        val matches = corpusPhoneticIndex[key].orEmpty()
        if (matches.isEmpty()) return null

        val alternatives = matches
            .drop(1)
            .take(config.maxSuggestions - 1)
            .mapIndexed { index, bengali ->
                Alternative(bengali, maxOf(0.72, 0.92 - index * 0.04))
            }

        return ConversionResult(
            bengali = matches.first(),
            confidence = 0.96,
            source = ResolutionSource.DICTIONARY,
            alternatives = alternatives
        )
    }

    /**
     * Query-side typing-habit normalization for index lookups. The compiled
     * index stores canonical keys plus collapsed aliases (chh→c, ii→i, uu→u);
     * users may also type ee/oo for long vowels — collapse those here.
     */
    private fun normalizeIndexQuery(key: String): String =
        key.replace("ee", "i").replace("oo", "u")

    // ======================== SINGLE WORD CONVERSION (7-LAYER PIPELINE) ========================

    /**
     * Convert a single phonetic word to Bengali through the 7-layer pipeline.
     *
     * @param input English phonetic input (e.g., "ami", "bangladesh")
     * @return ConversionResult with Bengali text, confidence, source, and alternatives
     */
    /**
     * S22 typo correction wrapper. Every conversion path can end in one of
     * two garbage shapes: the commit-gate CLEAN_TRANSLITERATION floor
     * (kmon -> ক্মন) or a confident hit on an UNEVIDENCED junk corpus word
     * (amdaer -> আময়দাের@freq1). Both mean "nothing real owns this key" —
     * only then is an edit-distance-1 search worth trusting. Confident
     * evidenced words and seed/learned/user words are never re-guessed, and
     * recursive layer calls (compound halves, negation prefixes) resolve
     * exactly, not fuzzily.
     */
    fun convertWord(input: String): ConversionResult {
        val raw = convertWordRaw(input)
        if (inTypoCorrection || inCompoundSplit || inNegationCompound) return raw
        val key = input.trim().lowercase()
        if (key.length < 4 || !key.all { it in 'a'..'z' }) return raw
        if (!validator.isLoaded()) return raw
        // S24: English-intent arbitration. When an English key collides with
        // an EVIDENCED Bengali inflection (time -> টিমে, printer -> প্রিন্টের)
        // no frequency margin can decide safely (name -> নামে must stay
        // Bengali). Primary flips are limited to a vetted intent list; the
        // 4x-margin rule handles weakly-attested squatters generally, and
        // getSuggestions always offers the loanword as a chip.
        if (key in ENGLISH_PRIMARY_INTENT ||
            (EnglishDetector.isEnglish(key) && raw.confidence < 1.0)
        ) {
            phoneticIndex?.lookupEnglish(key)?.let { en ->
                if (en != raw.bengali &&
                    (key in ENGLISH_PRIMARY_INTENT ||
                        validator.getFrequency(en) > maxOf(validator.getFrequency(raw.bengali), 6) * 4)
                ) {
                    return ConversionResult(
                        bengali = en,
                        confidence = 0.93,
                        source = ResolutionSource.ENGLISH_LEXICON,
                        alternatives = listOf(Alternative(raw.bengali, minOf(raw.confidence, 0.8))) +
                            raw.alternatives.take(2)
                    )
                }
            }
        }

        // Legitimate results with no words-table row of their own: two-word
        // splits, attached-negation compounds, approved compositions, and
        // words the store assigns to this EXACT key (habit aliases). None of
        // these are junk — never re-guess them.
        if (' ' in raw.bengali) return raw
        val negationStemValid = (raw.bengali.endsWith("না") &&
            validator.isValid(raw.bengali.removeSuffix("না"))) ||
            (raw.bengali.endsWith("নাই") && validator.isValid(raw.bengali.removeSuffix("নাই")))
        // Composition protection needs an EVIDENCED stem — the words table's
        // junk tail makes isApprovedComposition alone too lenient (আময়দাের
        // parses as junk-root আময়দা + ের and would be shielded).
        val evidencedComposition = approvedCompositionSuffixes.any { sfx ->
            raw.bengali.length > sfx.length && raw.bengali.endsWith(sfx) &&
                raw.bengali.dropLast(sfx.length).removeSuffix("্").let { root ->
                    root.isNotEmpty() && validator.getFrequency(root) >= 25
                }
        }
        if (negationStemValid || evidencedComposition) return raw
        if (storeLookup(key).any { it.bengali == raw.bengali && it.tier == PhoneticIndexHit.TIER_A }) {
            return raw
        }
        // Rare-but-deliberate exact-key rows (tier-B ghumacco -> ঘুমাচ্ছ) are
        // protected only when the raw result actually CAME from that row —
        // i.e. the store owns the key AND the pipeline chose its word with
        // conviction. Junk tier-B ownership (amdaer -> আময়দাের) does not
        // shield a floor-frequency word from correction.
        if (raw.confidence >= 0.9 &&
            storeLookup(key).any { it.bengali == raw.bengali } &&
            validator.getFrequency(raw.bengali) >= 10
        ) return raw
        val junkFloor = raw.source == ResolutionSource.CLEAN_TRANSLITERATION ||
            raw.confidence < 0.82
        val unevidencedWord = raw.confidence < 0.97 &&
            validator.getFrequency(raw.bengali) < 25 &&
            !dictionary.containsBengali(raw.bengali)
        if (!junkFloor && !unevidencedWord) return raw
        // S24: an English word whose Bengali pipeline result is junk should
        // render as its loanword, not as whatever corpus-tail word squats on
        // the key (there -> থেরে, read -> রোড). The lexicon is consulted only
        // from this junk path — real Bengali words always win upstream.
        run {
            // Not gated on EnglishDetector: the detector is deliberately shy
            // (Banglish protection), but on THIS path the Bengali reading is
            // already junk — lexicon presence is evidence enough (price ->
            // প্রাইস, there -> দেয়ার instead of corpus-tail থেরে). The lexicon
            // must be MORE attested than the raw reading, or a correct-but-
            // rare loanword the pipeline already found (ডেভেলপ) gets replaced
            // by a worse generated one (ডিভেলপ).
            phoneticIndex?.lookupEnglish(key)?.let { en ->
                if (en != raw.bengali &&
                    validator.getFrequency(en) > validator.getFrequency(raw.bengali)) {
                    return ConversionResult(
                        bengali = en,
                        confidence = 0.9,
                        source = ResolutionSource.ENGLISH_LEXICON,
                        alternatives = listOf(Alternative(raw.bengali, minOf(raw.confidence, 0.7)))
                    )
                }
            }
        }
        val corrected = tryStoreTypoCorrection(key) ?: return raw
        if (corrected.bengali == raw.bengali) return raw
        return corrected.copy(
            alternatives = listOf(Alternative(raw.bengali, minOf(raw.confidence, 0.7))) +
                raw.alternatives.filter { it.bengali != corrected.bengali }.take(2)
        )
    }

    private fun convertWordRaw(input: String): ConversionResult {
        val trimmed = input.trim()
        val key = trimmed.lowercase()
        if (key.isEmpty()) return ConversionResult("", 0.0, ResolutionSource.RULE)
        tryLowercaseV2ControlRule(key)?.let { return it }

        // Lowercase-only Bangla mode: uppercase letters are not semantic.
        // Users should never need Shift to force ট/ড/ড়/ঁ; those alternatives
        // come from lowercase rules, suggestion ranking, or explicit controls.
        val cacheKey = key

        // Check cache — invalidate stale entries when 480K validator loads
        wordCache[cacheKey]?.let { cached ->
            val shouldInvalidate = validator.isLoaded()
                && cached.source != ResolutionSource.DICTIONARY
                && cached.source != ResolutionSource.ENGLISH_PASSTHROUGH
                && cached.source != ResolutionSource.ENGLISH_LEXICON
                // Gated OOV floor results are produced only AFTER the validator
                // loads and are intentionally not validator words — never stale.
                && cached.source != ResolutionSource.CLEAN_TRANSLITERATION
                && !validator.isValid(cached.bengali)
            if (!shouldInvalidate) return cached
            // Stale cache — re-run conversion with loaded validator
            wordCache.remove(cacheKey)
        }

        // Layer 1: Dictionary lookup
        tryDirectWordOverride(key)?.let { result ->
            cacheResult(cacheKey, result); return result
        }

        MOBILE_SHORTHAND_OVERRIDES[key]?.let { shorthand ->
            val literal = convertByPatterns(key)
            val alternatives = if (literal.bengali.isNotEmpty() && literal.bengali != shorthand) {
                listOf(Alternative(literal.bengali, minOf(literal.confidence, 0.82)))
            } else {
                emptyList()
            }
            val result = ConversionResult(
                bengali = shorthand,
                confidence = 0.999,
                source = ResolutionSource.DICTIONARY,
                alternatives = alternatives
            )
            cacheResult(cacheKey, result); return result
        }

        // S16: attached-negation compound (chat register writes না joined:
        // "bujtecina" = বুঝতেছি + না). Runs BEFORE the invented-composition
        // layers below, which otherwise assemble junk-stem garbage
        // (বুজয়তেছিনা@0.97) for exactly these keys. Internally defers to the
        // store when the FULL key is an attested word (মন্ত্রণা-class), and
        // requires the loaded validator, so seed-only engines fall through to
        // the productive-suffix table unchanged.
        tryNegationCompound(key)?.let { result ->
            cacheResult(cacheKey, result); return result
        }

        tryProductiveVerbSuffixConversion(key)?.let { result ->
            // Composed root+suffix string — may be an invented form; gate it.
            val gated = applyCompositionCommitGate(key, result)
            cacheResult(cacheKey, gated); return gated
        }

        val dictionaryLayer = convertByDictionary(key)?.takeUnless { isUnfaithfulFragmentMatch(key, it) }
        dictionaryLayer?.let { result ->
            tryCorpusPhoneticLookup(key, minKeyLength = 2)?.let { corpusResult ->
                val dictFreq = validator.getFrequency(result.bengali)
                // Store words live outside the 480K validator: without the store
                // frequency they would always score 0 here and could never win.
                val corpusFreq = maxOf(
                    validator.getFrequency(corpusResult.bengali),
                    storeFrequencyOf(key, corpusResult.bengali)
                )
                // F3 exact-key weighting: when the corpus hit sits under the
                // typed key EXACTLY (no ee/oo collapse) while the typed key
                // EXTENDS the dictionary word's canonical phonetic (the user
                // deliberately typed more letters than the dictionary word
                // needs — dutii = duti + i), the exact index hit wins unless
                // the dictionary word clearly dominates: +15 absolute AND 2x
                // relative. dutii: exact দুটিই@47 beats variant দুটি@76
                // (76 < 47*2). Abbreviated typing (ok vs canonical oke,
                // ca vs cha) keeps the standard rule — ওকে/চা must not lose
                // to rarer exact 2-char corpus keys.
                val corpusExactKey = storeLookup(key).any { it.bengali == corpusResult.bengali }
                val dictCanonical = dictionary.getPhoneticForBengali(result.bengali)
                val typedExtendsDictPhonetic = dictCanonical != null &&
                    dictCanonical != key && key.startsWith(dictCanonical)
                // S6 store-first arbitration (study W1): when the store's FIRST
                // hit for the exact typed key is a canonical tier-A owner, it
                // wins ties against the seed layer — seed frequencies are stale
                // and carry archaic spellings (toiri: seed তৈরী@82 must lose to
                // store-canonical তৈরি@82; modern usage 5,021 vs 531).
                // Requires the loaded validator: without it dictFreq is 0 for
                // every seed word and any tier-A store row would win the tie.
                val storeTop = storeLookup(key).firstOrNull()
                // priority is NOT required here: storeLookup's evidence margin
                // (S8) only lets an alias sit first on ~25x usage, so a tier-A
                // first hit is the store's verdict either way.
                val storeCanonicalFirst = validator.isLoaded() && storeTop != null &&
                    storeTop.bengali == corpusResult.bengali &&
                    storeTop.tier == PhoneticIndexHit.TIER_A
                // S12 exact-spelling fidelity: the user typed the store-first
                // word's own canonical romanization, and the seed layer's word
                // does not own this key at all — the seed mapping is a legacy
                // fuzzy shortcut (ghuma -> ঘুমায়, he -> হয়ে) and must not beat
                // the word the user literally spelled (ঘুমা, হে).
                val seedOwnsKey = storeLookup(key).any { it.bengali == result.bengali }
                val exactSpellingFidelity = storeCanonicalFirst &&
                    storeTop.priority == PhoneticIndexHit.PRIORITY_CANONICAL && !seedOwnsKey
                val corpusClearlyBetter = if (corpusExactKey && typedExtendsDictPhonetic) {
                    !(dictFreq > corpusFreq + 15 && dictFreq > corpusFreq * 2)
                } else {
                    corpusFreq > dictFreq + 5 || result.confidence < 0.90 ||
                        (storeCanonicalFirst && corpusFreq >= dictFreq) ||
                        exactSpellingFidelity
                }
                if (corpusClearlyBetter) {
                    cacheResult(cacheKey, corpusResult); return corpusResult
                }
            }

            val ranked = if (shouldApplyEarlyCandidateLattice(key)) applyCandidateLatticeRanking(key, result) else result
            // S9: when the seed layer wins, its alternatives only carry seed
            // variants — merge tier-A store hits so the bigram context rerank
            // can promote homophones (টেস্ট + mach needs ম্যাচ visible).
            val enriched = withStoreAlternatives(key, ranked)
            cacheResult(cacheKey, enriched); return enriched
        }

        if (EnglishDetector.isEnglish(key)) {
            tryCuratedEnglishVariant(key, trimmed)?.let { result ->
                cacheResult(cacheKey, result); return result
            }
            tryEnglishLexicon(key, trimmed)?.let { result ->
                cacheResult(cacheKey, result); return result
            }
            val result = ConversionResult(trimmed, 1.0, ResolutionSource.ENGLISH_PASSTHROUGH)
            cacheResult(cacheKey, result); return result
        }

        tryCorpusPhoneticLookup(key, minKeyLength = 2)?.let { result ->
            cacheResult(cacheKey, result); return result
        }

        // Lexicon words are English even when EnglishDetector doesn't know them.
        // Placed AFTER the corpus index so real Bengali words win ambiguous romanizations.
        tryEnglishLexicon(key, trimmed)?.let { result ->
            cacheResult(cacheKey, result); return result
        }

        tryProductiveSuffixConversion(key)?.let { result ->
            val ranked = applyCandidateLatticeRanking(key, result)
            // Composed stem+টা-form string — may be an invented form; gate it.
            val gated = applyCompositionCommitGate(key, ranked)
            cacheResult(cacheKey, gated); return gated
        }

        // Layer 1.2: Suffix-stripped dictionary lookup
        trySuffixStrippedDictionary(key)?.let { result ->
            val ranked = if (shouldApplyEarlyCandidateLattice(key)) applyCandidateLatticeRanking(key, result) else result
            // Composed stem+inflection string — may be an invented form; gate it.
            val gated = applyCompositionCommitGate(key, ranked)
            cacheResult(cacheKey, gated); return gated
        }

        // Layer 0: Section narrowing (if 480K loaded)
        if (sectionEngine.isReady()) {
            convertBySection(key)?.let { result ->
                if (result.confidence >= 0.95) {
                    val validated = applyDictionaryValidation(result)
                    val ranked = if (shouldApplyEarlyCandidateLattice(key)) applyCandidateLatticeRanking(key, validated) else validated
                    // Section words come from the 480K list (gate no-op), but
                    // validation/ranking may swap the primary — keep it closed.
                    val gated = applyCompositionCommitGate(key, ranked)
                    cacheResult(cacheKey, gated); return gated
                }
            }
        }

        // Layer 1.5: Root decomposition. S10 fragment sanity applies here too:
        // root matching stretches mid-word fragments to unrelated roots
        // (poriko → পৃথক while the user types পরিকল্পনা) — skip when the match
        // doesn't own the key and canonical continuations exist.
        convertByRootDecomposition(key)?.takeUnless { isUnfaithfulFragmentMatch(key, it) }?.let { result ->
            val ranked = if (shouldApplyEarlyCandidateLattice(key)) applyCandidateLatticeRanking(key, result) else result
            // Reassembled root+suffix string — may be an invented form; gate it.
            val gated = applyCompositionCommitGate(key, ranked)
            cacheResult(cacheKey, gated); return gated
        }

        // Layers 2-4: Pattern conversion
        var result = convertByPatterns(key)

        // Layer 5: AI Disambiguation (if confidence < 0.92)
        if (result.confidence < 0.92) {
            result = applyDisambiguation(result, key)
        }

        // Layer 5.5: Dictionary validation (if 480K loaded)
        if (validator.isLoaded()) {
            result = applyDictionaryValidation(result)
        }

        // Layer 5.7: Conjunct removal recovery (if 480K loaded and result not valid)
        if (validator.isLoaded() && !validator.isValid(result.bengali)) {
            result = applyConjunctRemovalRecovery(result)
        }

        // Layer 6: Bengali dictionary recovery (if 480K loaded and result not valid)
        // Gate: only fire on longer inputs (>= 6 chars) where pattern engine output is less trustworthy.
        // F3: approved compositions (real root + productive suffix, e.g. farser →
        // ফার্স+ের) are legitimate inflections absent from the 480K list — never
        // "recover" them to a different corpus word (farser must not become ফার্স্টের).
        if (key.length >= 6 && validator.isLoaded() && !validator.isValid(result.bengali) &&
            result.bengali.length >= 3 && !isApprovedComposition(result.bengali)
        ) {
            applyBengaliRecovery(result)?.let { recovered ->
                // S22 arbitration: recovery may land on a word that covers
                // only part of the typed key (valolagchena -> ভাললাগে drops
                // four keystrokes). A well-covering recovery wins; a poorly-
                // covering one yields to a glued two-word split when one
                // exists (ভালো লাগছেনা).
                val covered = com.banglu.engine.util.ReverseTransliterator
                    .reverseWord(recovered.bengali).length >= key.length
                if (!covered) {
                    tryCompoundSplit(key)?.let { split ->
                        cacheResult(cacheKey, split); return split
                    }
                }
                val gated = applyCommitGate(key, recovered)
                cacheResult(cacheKey, gated); return gated
            }
        }

        // S22: glued two-word compounds ("bujteparcina" = বুঝতে পারছিনা).
        // Every single-word layer including recovery has had its chance —
        // a strong split beats the pattern/typo floor below.
        tryCompoundSplit(key)?.let { split ->
            cacheResult(cacheKey, split); return split
        }

        // ======== Typo Correction + Fuzzy Fallback (post-Layer 6) ========
        // Only try typo correction when pattern engine produced low confidence.
        // This prevents "kotobar" from being wrongly corrected to "oktobar"→অক্টোবর.
        if (result.confidence < 0.5) {
            // Try typo correction: transposition, doubled-char reduction, vowel insertion
            val typoResult = TypoCorrector.correct(key, dictionary)
            if (typoResult != null) {
                val typoDictResult = convertByDictionary(typoResult.corrected)
                if (typoDictResult != null && typoDictResult.confidence > result.confidence) {
                    val correctedResult = typoDictResult.copy(
                        confidence = typoDictResult.confidence - 0.05,
                        alternatives = listOf(
                            Alternative(result.bengali, result.confidence)
                        ) + result.alternatives
                    )
                    val gated = applyCommitGate(key, correctedResult)
                    cacheResult(cacheKey, gated)
                    return gated
                }
            }

            // Fuzzy dictionary fallback
            val fuzzyResults = dictionary.fuzzyLookup(key, maxDistance = 1, limit = 1, anchorFirst = true)
            if (fuzzyResults.isNotEmpty() && fuzzyResults[0].confidence > result.confidence) {
                val fuzzyResult = ConversionResult(
                    bengali = fuzzyResults[0].bengali,
                    confidence = fuzzyResults[0].confidence * 0.9,
                    source = ResolutionSource.DICTIONARY,
                    alternatives = listOf(
                        Alternative(result.bengali, result.confidence)
                    ) + result.alternatives
                )
                val gated = applyCommitGate(key, fuzzyResult)
                cacheResult(cacheKey, gated)
                return gated
            }
        }

        result = applyCandidateLatticeRanking(key, result)
        result = applyCommitGate(key, result)

        cacheResult(cacheKey, result)
        return result
    }

    /**
     * Conservative conversion for live IME composing text.
     *
     * This intentionally avoids recovery/fuzzy/section/root layers because a
     * user's current buffer is usually incomplete while typing. Those aggressive
     * layers are still used by convertWord() when the word is committed.
     */
    /**
     * S9b: context-aware composing preview. The commit path and the strip
     * apply bigram context (টেস্ট + mach → ম্যাচ); without this overload the
     * inline preview showed the context-free reading (মাছ) and disagreed with
     * what space would commit. Store homophones are merged in as candidates,
     * and [rerankWithPreviousContext]'s observed-pair guard keeps previews
     * stable when there is no real evidence.
     */
    fun convertForComposing(input: String, previousBengali: String?): ConversionResult =
        convertForComposing(input, previousBengali, null)

    /** S20 overload: two-word context so the preview mirrors the trigram
     *  rerank the commit path applies — preview and Space must agree. */
    fun convertForComposing(
        input: String,
        previousBengali: String?,
        secondPreviousBengali: String?
    ): ConversionResult {
        val base = convertForComposing(input)
        val prev = previousBengali?.trim().orEmpty()
        if (prev.isEmpty() || base.bengali.isEmpty()) return base
        val enriched = withStoreAlternatives(input.trim().lowercase(), base)
        if (enriched.alternatives.isEmpty()) return base
        return rerankWithContext(secondPreviousBengali, prev, enriched)
            .copy(alternatives = emptyList())
    }

    fun convertForComposing(input: String): ConversionResult {
        val trimmed = input.trim()
        val key = trimmed.lowercase()
        if (key.isEmpty()) return ConversionResult("", 0.0, ResolutionSource.RULE)
        tryLowercaseV2ControlRule(key)?.let { return it }

        // S26 mirror: vetted English-intent keys always commit the loanword
        // (convertWord wrapper, S24/S25); the live preview must agree or the
        // editor shows the Bengali inflection (line previewed লিনে) that
        // space then "fixes" to লাইন. Store-less engines keep the raw
        // preview, matching the wrapper which also cannot flip without the
        // lexicon.
        if (key in ENGLISH_PRIMARY_INTENT) {
            phoneticIndex?.lookupEnglish(key)?.let { en ->
                return ConversionResult(en, 0.93, ResolutionSource.ENGLISH_LEXICON, emptyList())
            }
        }

        tryDirectWordOverride(key)?.let { override ->
            return override.copy(alternatives = emptyList())
        }

        MOBILE_SHORTHAND_OVERRIDES[key]?.let { bengali ->
            return ConversionResult(bengali, 0.99, ResolutionSource.DICTIONARY, emptyList())
        }

        // S16 mirror: the committed path resolves attached-negation keys via
        // tryNegationCompound BEFORE the verb-suffix table; the live preview
        // must agree or the editor shows garbage that space then "fixes"
        // (bujtecina previewed বুজয়তেছিনা, committed বুঝতেছিনা).
        tryNegationCompound(key)?.let { result ->
            return result.copy(alternatives = emptyList())
        }

        tryProductiveVerbSuffixConversion(key)?.let { result ->
            // Composed root+suffix string — gate like the committed path. Short
            // fragments stay live (same key.length >= 4 guard as the fallback).
            if (key.length < 4) return result.copy(alternatives = emptyList())
            return applyCompositionCommitGate(key, result).copy(alternatives = emptyList())
        }

        val pattern = convertByPatterns(key)
        if (STABLE_COMPOSITION_FRAGMENTS.contains(key)) {
            return pattern.copy(alternatives = emptyList())
        }

        // For completed-looking exact dictionary words, show the smart primary
        // in the editor. For incomplete prefixes, keep the literal pattern text
        // stable and let the suggestion row show smart candidates.
        val dictionaryResult = if (key.length >= 4) convertByDictionary(key) else null
        if (dictionaryResult != null && dictionaryResult.confidence >= 0.88) {
            // S6, composing side: the editor preview must not show a stale seed
            // spelling (toiri -> তৈরী) when the store's canonical tier-A owner
            // of the exact typed key is at least as common. Same arbitration
            // rule as convertWord; without this the preview and the committed
            // word diverge (editor তৈরী, space commits তৈরি).
            tryCorpusPhoneticLookup(key)?.let { corpusResult ->
                val storeTop = storeLookup(key).firstOrNull()
                // Like convertWord's S6 check: margin-promoted alias tops
                // (songkha → সংখ্যা) count — the preview must match the commit.
                val canonicalFirst = validator.isLoaded() && storeTop != null &&
                    storeTop.bengali == corpusResult.bengali &&
                    storeTop.tier == PhoneticIndexHit.TIER_A
                val corpusFreq = maxOf(
                    validator.getFrequency(corpusResult.bengali),
                    storeFrequencyOf(key, corpusResult.bengali)
                )
                val seedOwnsKey = storeLookup(key).any { it.bengali == dictionaryResult.bengali }
                val exactSpellingFidelity = canonicalFirst &&
                    storeTop.priority == PhoneticIndexHit.PRIORITY_CANONICAL && !seedOwnsKey
                if (canonicalFirst && corpusResult.bengali != dictionaryResult.bengali &&
                    (exactSpellingFidelity ||
                        corpusFreq >= validator.getFrequency(dictionaryResult.bengali))
                ) {
                    return corpusResult.copy(alternatives = emptyList())
                }
            }
            return applyCandidateLatticeRanking(key, dictionaryResult)
        }

        // Live composing should follow the V2 rule layer for short syllables.
        // Corpus phonetic hits can contain rare/noisy exact forms (for example
        // kuu -> কুউ) and must not override basic consonant + vowel-kar output.
        val corpusResult = if (key.length >= 4) tryCorpusPhoneticLookup(key) else null
        if (corpusResult != null && corpusResult.confidence >= 0.94) {
            return corpusResult.copy(alternatives = emptyList())
        }

        val sectionResult = if (sectionEngine.isReady() && key.length >= 4) convertBySection(key) else null
        if (sectionResult != null && sectionResult.confidence >= 0.95) {
            return sectionResult.copy(alternatives = emptyList())
        }

        // V2 parity: the live editor should still show the rule-composed Bangla
        // token. Suggestions/dictionary layers can promote smarter completed words,
        // but the fallback must not be raw Latin while the user is typing.
        val fallback = pattern.copy(
            confidence = minOf(pattern.confidence, 0.84),
            alternatives = emptyList()
        )
        // Short fragments are usually incomplete words mid-typing — keep them live.
        if (key.length < 4) return fallback
        return applyCommitGate(key, fallback).copy(alternatives = emptyList())
    }

    fun getCompositionPreview(input: String): String = convertForComposing(input).bengali

    // ======================== MULTI-WORD CONVERSION ========================

    /**
     * Parse multi-word input, converting each word and preserving whitespace.
     * Optionally applies Viterbi optimization if bigram model is loaded.
     *
     * @param input Full phonetic input (may contain spaces)
     * @return Converted Bengali text with whitespace preserved
     */
    fun parse(input: String): String {
        if (input.isEmpty()) return ""

        // Tokenize preserving whitespace
        val allTokens = mutableListOf<String>()
        val wordPattern = Regex("\\S+")
        var lastEnd = 0
        for (match in wordPattern.findAll(input)) {
            if (match.range.first > lastEnd) {
                allTokens.add(input.substring(lastEnd, match.range.first)) // whitespace
            }
            allTokens.add(match.value) // word
            lastEnd = match.range.last + 1
        }
        if (lastEnd < input.length) allTokens.add(input.substring(lastEnd))

        // Track which tokens are words (not whitespace) and their original phonetic
        val wordIndices = mutableListOf<Int>()
        val originalPhonetics = mutableListOf<String>()
        for ((i, token) in allTokens.withIndex()) {
            if (token.isNotBlank()) {
                originalPhonetics.add(token)
                val result = convertWord(token)
                allTokens[i] = result.bengali
                wordIndices.add(i)
            }
        }

        // Per-word bigram re-ranking for ambiguous pairs
        // When both primary and alternative are valid 480K words, use lower threshold (1.2x vs 1.5x)
        if (bigramModel.isLoaded() && wordIndices.size >= 2) {
            for (i in 1 until wordIndices.size) {
                val prevIdx = wordIndices[i - 1]
                val currIdx = wordIndices[i]
                val prevBengali = allTokens[prevIdx]
                val currPhonetic = originalPhonetics[i]
                val currResult = convertWord(currPhonetic)

                var bestScore = bigramModel.bigramProb(prevBengali, currResult.bengali)
                var bestBengali = currResult.bengali

                for (alt in currResult.alternatives) {
                    // S4: promotion requires real observed bigram evidence —
                    // see rerankWithPreviousContext (junk unigram counts must
                    // not flip a correct primary).
                    if (bigramModel.bigramCount(prevBengali, alt.bengali) == 0) continue

                    val altScore = bigramModel.bigramProb(prevBengali, alt.bengali)

                    val bothValid = validator.isLoaded() &&
                            validator.isValid(currResult.bengali) &&
                            validator.isValid(alt.bengali)

                    val threshold = if (bothValid) 1.2 else 1.5

                    if (altScore > bestScore * threshold) {
                        bestScore = altScore
                        bestBengali = alt.bengali
                    }
                }

                if (bestBengali != allTokens[currIdx]) {
                    allTokens[currIdx] = bestBengali
                }
            }
        }

        // Viterbi optimization (if bigram model loaded and 2+ words)
        val decoder = viterbiDecoder
        if (decoder != null && wordIndices.size >= 2) {
            val candidateSets = wordIndices.mapIndexed { idx, tokenIdx ->
                val phonetic = originalPhonetics[idx]
                val result = convertWord(phonetic)
                val candidates = mutableListOf(WordCandidate(result.bengali, result.confidence))
                for (alt in result.alternatives.take(4)) {
                    candidates.add(WordCandidate(alt.bengali, alt.confidence))
                }
                candidates
            }
            val viterbiResult = decoder.decode(candidateSets)
            for ((i, wordIdx) in wordIndices.withIndex()) {
                if (i < viterbiResult.words.size) {
                    allTokens[wordIdx] = viterbiResult.words[i]
                }
            }
        }

        return allTokens.joinToString("")
    }

    // ======================== SUGGESTIONS ========================

    /**
     * Get ranked suggestions for the current phonetic input.
     *
     * Tiers:
     *   0: Primary conversion result
     *   1: Exact dictionary matches
     *   2: Prefix matches
     *   3: Fuzzy matches
     *   3.6: Progressive narrowing
     *   3.7: Section narrowing (if 480K loaded)
     *   4: Pattern conversion alternatives
     *
     * @param input Phonetic input
     * @param limit Maximum suggestions to return
     * @return Sorted list of SmartSuggestion
     */
    fun getSuggestions(input: String, limit: Int = 5): List<SmartSuggestion> {
        val key = input.lowercase().trim()
        if (key.isEmpty()) return emptyList()
        if (limit <= 0) return emptyList()

        val maxResults = maxOf(limit, minOf(MAX_SUGGESTION_CANDIDATES, limit * 4))
        val suggestions = mutableListOf<SmartSuggestion>()
        val seen = mutableSetOf<String>()
        val exactDictionaryWords = dictionary.lookup(key).map { it.bengali }.toSet()

        // ── Tier 0: Primary conversion (matching web engine exactly) ──
        val primary = convertWord(key)
        if (primary.bengali.isNotEmpty() && seen.add(primary.bengali)) {
            val primaryIsExactDictionary = primary.bengali in exactDictionaryWords
            suggestions.add(
                SmartSuggestion(
                    primary.bengali,
                    1.0,
                    if (primaryIsExactDictionary) "dictionary_exact" else "primary",
                    key,
                    "tier0"
                )
            )
        }

        if (ENGLISH_VARIANT_PRIMARY_BY_KEY[key] == primary.bengali && input.trim() != primary.bengali && seen.add(input.trim())) {
            suggestions.add(
                SmartSuggestion(
                    bengali = input.trim(),
                    confidence = 0.98,
                    source = "english_passthrough",
                    phonetic = key,
                    tier = "tier0_english"
                )
            )
        }

        for (alt in generateAmbiguousCharAlternatives(primary.bengali)) {
            if (seen.add(alt.bengali)) {
                suggestions.add(SmartSuggestion(alt.bengali, alt.confidence, "ambiguous_variant", key, "tier0_ambiguous"))
            }
        }

        // Include alternatives from convertWord (user's literal + swap variants)
        for (alt in primary.alternatives) {
            if (seen.add(alt.bengali)) {
                suggestions.add(SmartSuggestion(alt.bengali, alt.confidence, "alternative", key, "tier0"))
            }
            for (variant in getYaOrthographicVariants(alt.bengali)) {
                if (seen.add(variant)) {
                    suggestions.add(
                        SmartSuggestion(
                            bengali = variant,
                            confidence = (alt.confidence * 0.98).coerceAtLeast(0.60),
                            source = "orthographic_variant",
                            phonetic = key,
                            tier = "tier0_orthographic"
                        )
                    )
                }
            }
        }

        for (variant in getYaOrthographicVariants(primary.bengali)) {
            if (seen.add(variant)) {
                suggestions.add(
                    SmartSuggestion(
                        bengali = variant,
                        confidence = 0.93,
                        source = "orthographic_variant",
                        phonetic = key,
                        tier = "tier0_orthographic"
                    )
                )
            }
        }

        // S18: confidence ranks by the store's OWN order (frequency within the
        // exact key), not by how crowded the strip already is — the old
        // suggestions.size discount floored exact tier-A hits at 0.72, below
        // prefix continuations (0.90), so পারোস vanished behind পারস্পরিক.
        // An exact-key match always outranks a continuation.
        for ((index, bengali) in corpusWordsFor(key).take(maxResults).withIndex()) {
            if (seen.add(bengali)) {
                suggestions.add(
                    SmartSuggestion(
                        bengali = bengali,
                        confidence = maxOf(0.80, 0.94 - index * 0.02),
                        source = "corpus_phonetic",
                        phonetic = key,
                        tier = "tier0_corpus"
                    )
                )
            }
        }

        // S7 usage-ranked continuations: real tier-A words whose key extends the
        // typed prefix, ordered canonical-first then by frequency (the store's
        // lookupPrefix contract). This is what puts ব্রিটিশ on the strip at
        // "brit" instead of alphabetical ব্রিং junk from the Bengali-prefix scan.
        if (key.length >= 3) {
            phoneticIndex?.let { store ->
                for ((index, hit) in store.lookupPrefix(key, 8).withIndex()) {
                    if (seen.add(hit.bengali)) {
                        suggestions.add(
                            SmartSuggestion(
                                bengali = hit.bengali,
                                confidence = maxOf(0.68, 0.90 - index * 0.03),
                                source = "corpus_prefix",
                                phonetic = key,
                                tier = "tier1_continuation"
                            )
                        )
                    }
                }
            }
        }

        // Same-sound candidate lattice: keep dental/retroflex, sibilant, nasal,
        // j/y, and vowel ambiguity visible even when the primary came from dictionary.
        for (candidate in generateCandidateLattice(key, 80)) {
            if (seen.add(candidate.bengali)) {
                suggestions.add(
                    SmartSuggestion(
                        bengali = candidate.bengali,
                        confidence = candidate.score.coerceIn(0.30, 0.96),
                        source = "candidate_lattice",
                        phonetic = key,
                        tier = "tier0_lattice"
                    )
                )
            }
        }

        // ো variant: when input ends with 'o', ensure both forms available
        if (key.endsWith("o") && primary.bengali.isNotEmpty()) {
            val bengali = primary.bengali
            if (bengali.endsWith("ো")) {
                val withoutOkar = bengali.dropLast(1)
                if (withoutOkar.isNotEmpty() && seen.add(withoutOkar)) {
                    suggestions.add(SmartSuggestion(withoutOkar, 0.90, "okar_variant", key, "tier0_okar"))
                }
            } else {
                val withOkar = bengali + "ো"
                if (seen.add(withOkar)) {
                    suggestions.add(SmartSuggestion(withOkar, 0.88, "okar_variant", key, "tier0_okar"))
                }
            }
        }

        // S24: the loanword rendering is always one tap away for English keys
        // (time -> টাইম chip even while টিমে holds the primary).
        if (EnglishDetector.isEnglish(key)) {
            phoneticIndex?.lookupEnglish(key)?.let { en ->
                if (seen.add(en)) {
                    suggestions.add(
                        SmartSuggestion(en, 0.9, "english_lexicon", key, "tier0_english")
                    )
                }
            }
        }

        if (EnglishDetector.isEnglish(key) && suggestions.none { it.bengali.lowercase() == key }) {
            suggestions.add(
                SmartSuggestion(
                    bengali = input.trim(),
                    confidence = if (primary.source == ResolutionSource.ENGLISH_PASSTHROUGH) 1.0 else 0.98,
                    source = "english_passthrough",
                    phonetic = key,
                    tier = "tier0_english"
                )
            )
        }

        // ── Bengali variant search (matching web: find related words from 480K by Bengali prefix) ──
        // This is what gives পেপারও, পেপারকে for pepar → পেপার
        if (validator.isLoaded() && primary.bengali.isNotEmpty() && primary.bengali.length >= 2) {
            val prefixLen = maxOf(2, primary.bengali.length - 1)
            val bengaliPrefix = primary.bengali.substring(0, minOf(prefixLen, primary.bengali.length))
            val bengaliVariants = validator.findByPrefix(bengaliPrefix, 10)
            for (variant in bengaliVariants) {
                if (suggestions.size >= maxResults) break
                if (seen.add(variant)) {
                    val lengthDiff = kotlin.math.abs(variant.length - primary.bengali.length)
                    val conf = maxOf(0.70 - lengthDiff * 0.08, 0.45)
                    suggestions.add(SmartSuggestion(variant, conf, "bengali_variant", "", "tier1"))
                }
            }
        }

        // ── Dictionary exact matches ──
        for (result in dictionary.lookup(key).take(maxResults)) {
            if (seen.add(result.bengali)) {
                suggestions.add(SmartSuggestion(result.bengali, result.confidence, "dictionary", result.matchedPhonetic, "tier2"))
            }
        }

        // ── Dictionary prefix matches (phonetic prefix) ──
        if (suggestions.size < maxResults) {
            for (result in dictionary.searchByPrefix(key, maxResults - suggestions.size)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, 0.60, "dictionary_prefix", result.phonetic, "tier3"))
                }
            }
        }

        // ── Fuzzy matches (edit distance 1, first-char anchored) ──
        if (suggestions.size < maxResults) {
            val maxFuzzy = minOf(3, maxResults - suggestions.size)
            for (result in dictionary.fuzzyLookup(key, 1, maxFuzzy, anchorFirst = true)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, result.confidence * 0.65, "dictionary_fuzzy", result.matchedPhonetic, "tier4"))
                }
            }
        }

        // ── Root decomposition suggestions (web parity: find dictionary root, suggest related 480K words) ──
        if (suggestions.size < maxResults && validator.isLoaded()) {
            val rootSuggestions = getRootBasedSuggestions(key, maxResults - suggestions.size)
            for (rs in rootSuggestions) {
                if (seen.add(rs.bengali)) {
                    suggestions.add(rs)
                }
            }
        }

        // ── Progressive narrowing ──
        if (suggestions.size < maxResults && key.length >= 2) {
            for (result in narrowingEngine.getSuggestions(key, maxResults - suggestions.size)) {
                if (seen.add(result.bengali)) {
                    suggestions.add(SmartSuggestion(result.bengali, result.confidence, "narrowing", result.phonetic, "tier5"))
                }
            }
        }

        // Section narrowing with overlap filter (Web Tier 3.7)
        if (suggestions.size < maxResults && sectionEngine.isReady()) {
            val sectionResults = sectionEngine.getSectionSuggestions(key, (maxResults - suggestions.size) * 3)
            val scored = sectionResults
                .filter { !it.bengali.contains("-") && seen.add(it.bengali) }
                .mapNotNull { sr ->
                    val phonetic = dictionary.getPhoneticForBengali(sr.bengali)
                        ?: ReverseTransliterator.reverseWord(sr.bengali)
                    if (phonetic.isNotEmpty()) {
                        val overlap = PhoneticOverlapScorer.score(key, phonetic)
                        if (overlap.score > 0.30) {
                            SmartSuggestion(
                                bengali = sr.bengali,
                                confidence = maxOf(sr.confidence * overlap.score, overlap.score * 0.8),
                                source = "section_filtered",
                                phonetic = phonetic,
                                tier = "tier6"
                            )
                        } else null
                    } else {
                        SmartSuggestion(sr.bengali, sr.confidence * 0.5, "section", "", "tier6")
                    }
                }
                .sortedByDescending { it.confidence }
                .take(maxResults - suggestions.size)

            suggestions.addAll(scored)
        }

        // ── Pattern conversion as fallback suggestion (web parity) ──
        // If primary came from dictionary, the raw pattern-engine output may differ
        // and should be offered as an alternative.
        if (suggestions.size < maxResults) {
            val patternResult = convertByPatterns(key)
            if (patternResult.bengali.isNotEmpty() && seen.add(patternResult.bengali)) {
                suggestions.add(
                    SmartSuggestion(
                        bengali = patternResult.bengali,
                        confidence = patternResult.confidence,
                        source = "pattern",
                        phonetic = key,
                        tier = "tier7_pattern"
                    )
                )
            }

            // ── Pattern alternatives (diphthong splits, অ/ও variants) ──
            for (alt in patternResult.alternatives) {
                if (suggestions.size >= maxResults) break
                if (seen.add(alt.bengali)) {
                    suggestions.add(
                        SmartSuggestion(
                            bengali = alt.bengali,
                            confidence = alt.confidence,
                            source = "pattern_alternative",
                            phonetic = key,
                            tier = "tier7_pattern_alt"
                        )
                    )
                }
            }
        }

        // ── Validator boost: boost confidence of real 480K words (web parity) ──
        val boosted = if (validator.isLoaded()) {
            suggestions.map { s ->
                if (validator.isValid(s.bengali)) {
                    s.copy(confidence = minOf(s.confidence + 0.05, 1.0))
                } else s
            }
        } else suggestions

        // Global filter: remove hyphenated garbage from 480K dictionary.
        // Final ordering (S1/D2, refined S4/C3): the LEADING sort key is a
        // three-band usage rank — (0) primary, Tier-A dictionary words, and
        // English entries; (1) Tier-B dictionary words (exact-typed rare words
        // stay reachable, below every real-usage word); (2) generated/variant
        // non-dictionary candidates. Within a band the existing score orders.
        // Sort keys are computed once per candidate, not per comparison.
        val banded = boosted
            .filter {
                it.bengali == primary.bengali ||
                    (!it.bengali.contains("-") && isCleanSuggestion(key, it, primary.bengali))
            }
            .map { s ->
                Triple(
                    s,
                    suggestionStripBand(key, s, primary.bengali, exactDictionaryWords),
                    suggestionRankScore(key, s, primary.bengali, exactDictionaryWords)
                )
            }

        // S4/C3 hard-drop: when a real Tier-A word is available beyond the
        // primary, generated non-dictionary candidates without phonetic fit are
        // dropped entirely — the strip should fill with real words, not
        // inventions (hoy must never pad the strip with হয).
        val hasBandZeroBeyondPrimary = banded.any {
            it.second == 0 && it.first.bengali != primary.bengali
        }
        val ordered = banded
            .filterNot { (s, band, _) ->
                band == 2 && hasBandZeroBeyondPrimary &&
                    !hasStripBandTwoPhoneticFit(key, s.bengali, primary.bengali)
            }
            .sortedWith(
                compareBy<Triple<SmartSuggestion, Int, Double>> { it.second }
                    .thenByDescending { it.third }
                    .thenByDescending { it.first.confidence }
            )
            .mapTo(mutableListOf()) { it.first }

        // D1 invariant, structural half: the primary normally wins on score
        // (rank 1), but when several exact-key dictionary hits legitimately
        // outscore it (ghoro -> ঘর behind ঘরোয়/ঘোরা/ঘড়া) it must still hold a
        // rank no worse than 3 — the strip may never hide the editor primary.
        if (primary.bengali.isNotEmpty()) {
            val maxPrimaryIndex = minOf(MAX_PRIMARY_STRIP_RANK, limit) - 1
            val primaryIndex = ordered.indexOfFirst { it.bengali == primary.bengali }
            if (primaryIndex > maxPrimaryIndex) {
                val promoted = ordered.removeAt(primaryIndex)
                ordered.add(maxPrimaryIndex, promoted)
            }
        }
        return ordered.take(limit)
    }

    /**
     * Leading strip-order key (S1/D2, refined S4/C3) — three bands, lower wins:
     *
     *   0 — primary (gate-approved by construction), English passthrough/lexicon
     *       entries, Tier-A (real-usage) dictionary words, and seed/learned/
     *       extended phonetic-dictionary entries absent from the words table.
     *   1 — Tier-B words-table words: exact-typed rare words stay reachable but
     *       never above a real-usage word (kore must not surface করউকা@1 above
     *       Tier-A completions).
     *   2 — generated/variant strings that are not dictionary words at all.
     *
     * Words-table membership is checked FIRST: many junk-tier corpus rows
     * (করউকা, কঠন, ভালক) also appear in the extended phonetic dictionary, and
     * a containsBengali shortcut would wrongly lift them to band 0.
     */
    private fun suggestionStripBand(
        key: String,
        suggestion: SmartSuggestion,
        primary: String,
        exactDictionaryWords: Set<String>
    ): Int {
        if (suggestion.bengali == primary) return 0
        if (suggestion.source == "english_passthrough") return 0
        if (isKnownWord(suggestion.bengali)) {
            return if (wordUsageTier(key, suggestion.bengali) == PhoneticIndexHit.TIER_A) 0 else 1
        }
        // Not a words-table word: seed/learned/extended dictionary entries are
        // still real curated entries — band 0 — but ONLY when this key actually
        // reaches them (exact lookup hit, or their registered phonetic aligns
        // with what was typed). A generated variant of the primary that merely
        // collides with an unrelated seed entry (hoy's হয, registered under
        // "hoz") earns no membership credit from the collision — band 2.
        if (suggestion.bengali in exactDictionaryWords) return 0
        if (dictionary.containsBengali(suggestion.bengali)) {
            val registered = dictionary.getPhoneticForBengali(suggestion.bengali)
            if (registered != null && (registered.startsWith(key) || key.startsWith(registered))) return 0
        }
        return 2
    }

    /**
     * S4/C3 usage tier of a dictionary word for strip banding. Resolution order:
     * hits under the typed key (memoized store read the strip already performed),
     * its normalized form, then the word's own canonical reverse-transliterated
     * key. A dictionary word with no resolvable index row defaults to Tier B —
     * it has no real-usage evidence. Without an attached store there is no tier
     * signal at all: every dictionary word ranks Tier A (legacy single-band
     * behavior for seed-only/JVM-validator setups).
     */
    private fun wordUsageTier(key: String, bengali: String): Int {
        if (phoneticIndex == null) return PhoneticIndexHit.TIER_A
        storeLookup(key).firstOrNull { it.bengali == bengali }?.let { return it.tier }
        val normalized = normalizeIndexQuery(key)
        if (normalized != key) {
            storeLookup(normalized).firstOrNull { it.bengali == bengali }?.let { return it.tier }
        }
        val canonical = ReverseTransliterator.reverseWord(bengali).lowercase()
        if (canonical.isNotEmpty() && canonical != key && canonical != normalized) {
            storeLookup(canonical).firstOrNull { it.bengali == bengali }?.let { return it.tier }
        }
        return PhoneticIndexHit.TIER_B
    }

    /**
     * Phonetic-fit oracle for the S4/C3 band-2 hard-drop. Reuses the strict
     * generated-candidate fit thresholds WITHOUT the short-key leniency
     * delegation: a band-2 candidate only earns a slot next to real Tier-A
     * words when its reverse transliteration genuinely matches the typed key.
     */
    private fun hasStripBandTwoPhoneticFit(key: String, bengali: String, primary: String): Boolean {
        if (key.isEmpty() || bengali.isEmpty() || bengali == primary) return true
        val reversePhonetic = ReverseTransliterator.reverseWord(bengali)
        if (reversePhonetic.isEmpty()) return false
        if (hasGeneratedVowelDrift(key, reversePhonetic)) return false
        val reverseScore = PhoneticOverlapScorer.score(key, reversePhonetic).score
        val threshold = if (key.length >= 6) 0.68 else 0.64
        return reverseScore >= threshold && hasCompatibleVowelPath(key, reversePhonetic)
    }

    private fun suggestionRankScore(
        key: String,
        suggestion: SmartSuggestion,
        primary: String,
        exactDictionaryWords: Set<String>
    ): Double {
        val exactDictionaryMatch = suggestion.bengali in exactDictionaryWords ||
            suggestion.source == "dictionary_exact" ||
            suggestion.source == "dictionary" ||
            // S18: the compiled index is the spelling authority — a word that
            // owns the EXACT typed key is an exact match wherever it entered
            // the strip from. Without this, exact-key store words that are
            // absent from the in-memory dictionaries (chat lexicon: পারোস)
            // rank below high-usage prefix continuations (পারস্পরিক).
            storeLookup(key).any { it.bengali == suggestion.bengali }
        val seedDictionaryWord = dictionary.getPhoneticForBengali(suggestion.bengali) != null
        val validatorWord = if (validator.isLoaded()) {
            validator.isValid(suggestion.bengali)
        } else {
            disambiguator.isKnownWord(suggestion.bengali)
        }
        val generatedSource = suggestion.source in setOf(
            "ambiguous_variant",
            "candidate_lattice",
            "pattern",
            "pattern_alternative",
            "orthographic_variant"
        )
        val completionSource = suggestion.source in setOf(
            "dictionary_prefix",
            "dictionary_fuzzy",
            "narrowing",
            "section_filtered",
            "root_dictionary",
            "bengali_variant"
        )

        val candidatePhonetic = suggestion.phonetic.ifBlank {
            dictionary.getPhoneticForBengali(suggestion.bengali)
                ?: ReverseTransliterator.reverseWord(suggestion.bengali)
        }
        val overlapScore = if (candidatePhonetic.isNotBlank()) {
            PhoneticOverlapScorer.score(key, candidatePhonetic).score
        } else {
            0.0
        }

        var score = suggestion.confidence

        if (suggestion.bengali == primary) score += 0.40
        if (exactDictionaryMatch) score += 0.48
        if (seedDictionaryWord) score += 0.28
        if (validatorWord) score += 0.18
        if (completionSource) score += 0.08
        score += overlapScore * 0.24

        if (generatedSource && !exactDictionaryMatch && !seedDictionaryWord && !validatorWord) {
            score -= 0.34
        }
        if (suggestion.source == "pattern_alternative") score -= 0.12
        if (suggestion.source == "pattern" && suggestion.bengali != primary) score -= 0.10
        if (suggestion.source == "english_passthrough" && suggestion.bengali.lowercase() == key && suggestion.bengali != primary) {
            score -= 0.18
        }
        if (hasSuspiciousGeneratedConjunct(suggestion.bengali)) score -= 0.30

        return score
    }

    private fun isCleanSuggestion(key: String, suggestion: SmartSuggestion, primary: String): Boolean {
        if (suggestion.bengali.isEmpty()) return false
        // Strip invariant (S1/D1): the editor primary — convertWord's output — is
        // gate-approved by definition (real word, approved composition, or clean
        // floor) and must always survive to the strip. Without this exemption the
        // hasSuggestionPhoneticFit length rule (reverse phonetic shorter than the
        // typed key) evicted the correct primary on append-o keys: reverse of
        // বাংলাদেশ is "bangladesh" (10 chars) < key "bangladesho" (11 chars).
        if (suggestion.bengali == primary) return true
        if (DIRECT_WORD_OVERRIDES[key]?.contains(suggestion.bengali) == true) return true
        if (suggestion.source == "english_passthrough") return suggestion.bengali.lowercase() == key
        if (Regex("[A-Za-z]").containsMatchIn(suggestion.bengali)) return false
        if (!respectsSibilantKeyBoundary(key, suggestion.bengali, primary, suggestion.source)) return false
        if (suggestion.source == "orthographic_variant") return true
        if (
            key.length < 3 &&
            suggestion.source in setOf("dictionary_prefix", "dictionary_fuzzy", "progressive_narrowing", "root_dictionary")
        ) {
            return false
        }

        // The primary returned above — every suggestion here is non-primary.
        val isRealWord = if (validator.isLoaded()) {
            validator.isValid(suggestion.bengali)
        } else {
            disambiguator.isKnownWord(suggestion.bengali)
        }

        if (suggestion.source == "dictionary_exact") return true
        if (key.contains("ss") && !suggestion.bengali.contains("্")) return false

        if (suggestion.source == "candidate_lattice") {
            val reversePhonetic = ReverseTransliterator.reverseWord(suggestion.bengali)
            if (!isRealWord && hasSuspiciousGeneratedConjunct(suggestion.bengali)) return false
            return suggestion.confidence >= 0.30 && !hasGeneratedVowelDrift(key, reversePhonetic)
        }

        if (!isRealWord) {
            if (hasSuspiciousGeneratedConjunct(suggestion.bengali)) return false
            return hasGeneratedSuggestionPhoneticFit(key, suggestion.bengali, primary)
        }

        return hasSuggestionPhoneticFit(key, suggestion.bengali, primary)
    }

    private fun respectsSibilantKeyBoundary(
        key: String,
        bengali: String,
        primary: String,
        source: String
    ): Boolean {
        if (bengali == primary || source == "dictionary_exact") return true

        val generatedSource = source in setOf(
            "ambiguous_variant",
            "candidate_lattice",
            "dictionary_prefix",
            "dictionary_fuzzy",
            "progressive_narrowing",
            "narrowing",
            "root_dictionary",
            "pattern_alternative"
        )
        if (!generatedSource) return true

        val hasTypedSh = key.contains("sh")
        val hasTypedPlainS = key.contains('s')

        if (!hasTypedSh && hasTypedPlainS && primary.none { it == 'শ' || it == 'ষ' }) {
            if (bengali.any { it == 'শ' || it == 'ষ' }) return false
        }

        if (hasTypedSh && primary.none { it == 'স' }) {
            if (bengali.contains('স')) return false
        }

        return true
    }

    private fun getYaOrthographicVariants(bengali: String): List<String> {
        if (bengali.isEmpty()) return emptyList()

        val variants = linkedSetOf<String>()
        if (bengali.contains("য")) variants.add(bengali.replace("য", "জ"))
        if (bengali.contains("জ")) variants.add(bengali.replace("জ", "য"))
        if (bengali.contains("য়")) variants.add(bengali.replace("য়", "য়"))
        if (bengali.contains("য়")) variants.add(bengali.replace("য়", "য়"))
        variants.remove(bengali)
        return variants.toList()
    }

    private fun hasSuggestionPhoneticFit(key: String, bengali: String, primary: String): Boolean {
        if (key.isEmpty() || bengali.isEmpty()) return false
        if (bengali == primary && key.length < 5) return true

        val dictionaryPhonetic = dictionary.getPhoneticForBengali(bengali)
        val reversePhonetic = ReverseTransliterator.reverseWord(bengali)
        val threshold = if (key.length >= 5) 0.55 else 0.34
        val reverseThreshold = if (key.length >= 5) 0.55 else 0.30

        val dictionaryScore = dictionaryPhonetic?.let { PhoneticOverlapScorer.score(key, it).score } ?: 0.0
        val reverseScore = if (reversePhonetic.isNotEmpty()) {
            PhoneticOverlapScorer.score(key, reversePhonetic).score
        } else 0.0

        if (dictionaryScore >= 0.90) return true
        if (key.length >= 5 && reversePhonetic.length < key.length) return false

        return if (dictionaryScore >= threshold) {
            reverseScore >= reverseThreshold && hasCompatibleVowelPath(key, reversePhonetic)
        } else {
            reverseScore >= threshold && hasCompatibleVowelPath(key, reversePhonetic)
        }
    }

    private fun hasGeneratedSuggestionPhoneticFit(key: String, bengali: String, primary: String): Boolean {
        if (key.isEmpty() || bengali.isEmpty() || bengali == primary) return true
        if (key.length <= 3) return hasSuggestionPhoneticFit(key, bengali, primary)

        val reversePhonetic = ReverseTransliterator.reverseWord(bengali)
        if (reversePhonetic.isEmpty()) return false

        val reverseScore = PhoneticOverlapScorer.score(key, reversePhonetic).score
        val threshold = if (key.length >= 6) 0.68 else 0.64

        if (hasGeneratedVowelDrift(key, reversePhonetic)) return false

        return reverseScore >= threshold && hasCompatibleVowelPath(key, reversePhonetic)
    }

    private fun hasGeneratedVowelDrift(key: String, phonetic: String): Boolean {
        val keyVowels = vowelPath(key)
        val candidateVowels = vowelPath(phonetic)

        if (candidateVowels.length > keyVowels.length) return true

        return key.length >= 5 && key.endsWith("oi") && phonetic.endsWith("oy")
    }

    private fun hasSuspiciousGeneratedConjunct(bengali: String): Boolean =
        Regex("([কখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহড়ঢ়য়])্\\1").containsMatchIn(bengali) ||
            Regex("[সশষ]্[সশষ]").containsMatchIn(bengali)

    private fun hasCompatibleVowelPath(key: String, phonetic: String): Boolean {
        if (key.length < 5) return true

        val keyVowels = vowelPath(key)
        if (keyVowels.length < 3) return true

        val candidateVowels = vowelPath(phonetic)
        if (candidateVowels.isEmpty()) return false

        val lcs = vowelPathLcs(keyVowels, candidateVowels)
        return lcs.toDouble() / keyVowels.length >= 0.66
    }

    private fun vowelPath(value: String): String = value
        .lowercase()
        .replace(Regex("ee|ii"), "i")
        .replace(Regex("oo|uu"), "u")
        .replace("ou", "o")
        .replace(Regex("oi|oy"), "i")
        .replace(Regex("[^aeiou]"), "")

    private fun vowelPathLcs(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    // ======================== PRIVATE PIPELINE METHODS ========================

    /**
     * Root-based suggestions: split input into possible root+suffix,
     * look up each root in the dictionary, then search the 480K validator
     * for words starting with that Bengali root.
     *
     * Web parity: SmartEngine.ts getRootBasedSuggestions()
     */
    private fun getRootBasedSuggestions(key: String, limit: Int): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        if (!validator.isLoaded() || key.length < 3) return suggestions

        // Find the longest dictionary root
        for (splitPos in (key.length - 1) downTo 2) {
            val rootPhonetic = key.substring(0, splitPos)
            val rootResults = dictionary.lookup(rootPhonetic)
            if (rootResults.isEmpty()) continue

            val rootBengali = rootResults[0].bengali

            // Search the 480K dictionary for words starting with this root
            val relatedWords = validator.findByPrefix(rootBengali, limit + 5)
            for (word in relatedWords) {
                if (word == rootBengali) continue // Skip the root itself (already in exact matches)
                if (suggestions.size >= limit) break
                suggestions.add(
                    SmartSuggestion(
                        bengali = word,
                        confidence = 0.80,
                        source = "root_dictionary",
                        phonetic = rootPhonetic,
                        tier = "tier4_root"
                    )
                )
            }

            // Only use the longest root match
            if (suggestions.isNotEmpty()) break
        }

        return suggestions
    }

    /**
     * Layer 1: Dictionary lookup via PhoneticTrie.
     */
    private fun tryDirectWordOverride(key: String): ConversionResult? {
        val words = DIRECT_WORD_OVERRIDES[key] ?: return null
        if (words.isEmpty()) return null
        val primary = words.first()
        val alternatives = words.drop(1).mapIndexed { index, bengali ->
            Alternative(bengali, maxOf(0.90, 0.97 - index * 0.03))
        }
        return ConversionResult(
            bengali = primary,
            confidence = 0.995,
            source = ResolutionSource.DICTIONARY,
            alternatives = alternatives
        )
    }

    private fun convertByDictionary(key: String): ConversionResult? {
        var results = dictionary.lookup(key)
        if (results.isEmpty()) return null

        // Step 1: HARD FILTER for start-of-word consonant violations
        // 's' (not 'sh') → filter out শ-starting results (শ needs 'sh')
        if (key.startsWith("s") && !key.startsWith("sh")) {
            val filtered = results.filter { !it.bengali.startsWith("শ") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }
        // For very short "sh" prefixes prefer শ/ষ visually, but let completed
        // words such as shokal/shobar rank by dictionary frequency.
        if (key.startsWith("sh") && results.size > 1 && key.length <= 2) {
            results = results.sortedWith(Comparator { a, b ->
                val aIsDental = if (a.bengali.startsWith("স")) 1 else 0
                val bIsDental = if (b.bengali.startsWith("স")) 1 else 0
                if (aIsDental != bIsDental) aIsDental - bIsDental
                else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
            })
        }
        // 'z' → filter out জ-starting results (জ needs 'j')
        if (key.startsWith("z")) {
            val filtered = results.filter { !it.bengali.startsWith("জ") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }
        // 'j' (not 'jh') → PREFER জ over য, but don't filter out য entirely
        // জ and য are both valid for 'j' in Bengali (phonetically similar)
        if (key.startsWith("j") && !key.startsWith("jh")) {
            val jResults = results.filter { it.bengali.startsWith("জ") }
            if (jResults.isNotEmpty()) results = jResults  // Prefer জ if available
            // If only য results, keep them (don't filter to null)
        }
        // t/d: NO hard filter — soft sort below (web engine parity)
        // Web SmartEngine.ts uses soft sort to keep ট/ড but rank ত/দ first
        // 'v' → filter out ব-starting results (ব needs 'b', 'v' maps to ভ)
        if (key.startsWith("v")) {
            val filtered = results.filter { !it.bengali.startsWith("ব") }
            if (filtered.isNotEmpty()) results = filtered else return null
        }

        // Re-rank by real wordfreq frequency — but ONLY when:
        // 1. Top result has low dict frequency (< 85) — not a curated seed entry
        // 2. Wordfreq strongly disagrees (gap > 5)
        var ranked = if (results.size > 1 && validator.isLoaded() && validator.hasFrequencyData()) {
            val topDictFreq = results[0].frequency
            if (topDictFreq < 85) {
                val topWf = validator.getFrequency(results[0].bengali)
                val secondWf = validator.getFrequency(results[1].bengali)
                if (secondWf > topWf + 5) {
                    results.sortedByDescending { validator.getFrequency(it.bengali) }
                } else results
            } else results
        } else results

        // Step 2a: SOFT SORT for very short ambiguous prefixes only.
        // t/ta and d/da keep the default dental forms visible, but completed
        // words like taka/dan must be ranked by dictionary evidence.
        if (key.startsWith("t") && !key.startsWith("th") && key.length <= 2) {
            ranked = ranked.sortedWith(Comparator { a, b ->
                val aIsRetro = if (a.bengali.startsWith("ট")) 1 else 0
                val bIsRetro = if (b.bengali.startsWith("ট")) 1 else 0
                if (aIsRetro != bIsRetro) aIsRetro - bIsRetro
                else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
            })
        }
        if (key.startsWith("d") && !key.startsWith("dh") && key.length <= 2) {
            ranked = ranked.sortedWith(Comparator { a, b ->
                val aIsRetro = if (a.bengali.startsWith("ড")) 1 else 0
                val bIsRetro = if (b.bengali.startsWith("ড")) 1 else 0
                if (aIsRetro != bIsRetro) aIsRetro - bIsRetro
                else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
            })
        }

        // Step 2b: SOFT SORT for middle/end position consonant violations
        // Prefer results with fewer শ/ষ when phonetic has standalone 's' (not 'sh')
        // Prefer results with fewer ড when phonetic has standalone 'd' (not 'dh')
        // Prefer results with fewer জ when phonetic has 'z'
        if (ranked.size > 1) {
            val hasStandaloneS = Regex("s(?!h)").containsMatchIn(key)
            val hasStandaloneD = false
            val hasZ = key.contains("z")
            if (hasStandaloneS || hasStandaloneD || hasZ) {
                ranked = ranked.sortedWith(Comparator { a, b ->
                    var vA = 0; var vB = 0
                    if (hasStandaloneS) {
                        vA += a.bengali.count { it == 'শ' || it == 'ষ' }
                        vB += b.bengali.count { it == 'শ' || it == 'ষ' }
                    }
                    if (hasStandaloneD) {
                        vA += a.bengali.count { it == 'ড' }
                        vB += b.bengali.count { it == 'ড' }
                    }
                    if (hasZ) {
                        vA += a.bengali.count { it == 'জ' }
                        vB += b.bengali.count { it == 'জ' }
                    }
                    if (vA != vB) vA - vB
                    else (b.confidence * 100).toInt() - (a.confidence * 100).toInt()
                })
            }
        }

        if (!validator.isLoaded()) {
            ranked = preferBareInherentFinalO(key, ranked)
        }

        // Do not blindly promote the ো-version for every roman word ending in
        // "o". In Bangla phonetic typing, final roman o often represents the
        // inherent vowel pronunciation of a consonant-ending word (boro → বড়),
        // while some words genuinely need visible o-kar. Keep both variants,
        // but only promote the o-kar form when dictionary/frequency evidence is
        // clearly stronger than the bare form.
        if (key.endsWith("o") && ranked.size > 1 && validator.isLoaded()) {
            val topBengali = ranked[0].bengali
            val expectedOkar = topBengali + "ো"
            val okarIdx = ranked.indexOfFirst { it.bengali == expectedOkar }
            if (okarIdx > 0) {
                val okarResult = ranked[okarIdx]
                val topFreq = validator.getFrequency(topBengali)
                val okarFreq = validator.getFrequency(okarResult.bengali)
                val okarIsExactSeed = okarResult.confidence > ranked[0].confidence + 0.08
                val okarHasStrongFrequency = okarFreq > topFreq + 25
                if (okarIsExactSeed || okarHasStrongFrequency) {
                    ranked = ranked.toMutableList().apply {
                        removeAt(okarIdx)
                        add(0, okarResult)
                    }
                }
            }
        }

        val best = ranked[0]
        val alternatives = ranked.drop(1).map { Alternative(it.bengali, it.confidence) }
        if (best.bengali.endsWith("্")) {
            val literal = convertByPatterns(key)
            if (literal.bengali.isNotEmpty() && !literal.bengali.endsWith("্")) {
                return ConversionResult(
                    bengali = literal.bengali,
                    confidence = minOf(best.confidence, 0.93),
                    source = ResolutionSource.RULE,
                    alternatives = mergeAlternatives(
                        listOf(Alternative(best.bengali, minOf(best.confidence, 0.82))) + alternatives,
                        literal.alternatives,
                        literal.bengali
                    )
                )
            }
        }
        return ConversionResult(best.bengali, best.confidence, ResolutionSource.DICTIONARY, alternatives)
    }

    private fun tryCuratedEnglishVariant(key: String, rawInput: String): ConversionResult? {
        val primary = ENGLISH_VARIANT_PRIMARY_BY_KEY[key] ?: return null
        val alternatives = if (primary != rawInput) {
            listOf(Alternative(rawInput, 0.98))
        } else {
            emptyList()
        }
        return ConversionResult(
            bengali = primary,
            confidence = 0.995,
            source = ResolutionSource.DICTIONARY,
            alternatives = alternatives
        )
    }

    /**
     * Engine v3: generated English lexicon (CMUdict-derived, 27,745 loanwords)
     * from the precompiled index. Curated seed entries must keep beating this:
     * tryCuratedEnglishVariant is always consulted first in convertWord.
     */
    private fun tryEnglishLexicon(key: String, rawInput: String): ConversionResult? {
        val bengali = phoneticIndex?.lookupEnglish(key) ?: return null
        return ConversionResult(
            bengali = bengali,
            confidence = 0.97,
            source = ResolutionSource.ENGLISH_LEXICON,
            alternatives = listOf(Alternative(rawInput, 0.95))
        )
    }

    /**
     * Final roman "o" can mean either a visible o-kar (ভালো) or only the
     * pronounced inherent vowel of a consonant-ending word (বড়). When the
     * dictionary contains the exact pair bare + "ো", keep the o-kar spelling
     * only if it has clearly stronger evidence. This is deliberately scoped to
     * consonant endings that commonly suffer from the বড়/বড়ো class of errors,
     * so high-frequency words like ভালো remain untouched.
     */
    private fun preferBareInherentFinalO(key: String, ranked: List<LookupResult>): List<LookupResult> {
        if (!key.endsWith("o") || ranked.size < 2) return ranked

        val top = ranked.first()
        if (!top.bengali.endsWith("ো")) return ranked

        val bare = top.bengali.dropLast(1)
        if (!bareHasInherentFinalOPreference(bare)) return ranked

        val bareIdx = ranked.indexOfFirst { it.bengali == bare }
        if (bareIdx <= 0) return ranked

        val bareResult = ranked[bareIdx]
        val topFreq = if (validator.isLoaded()) validator.getFrequency(top.bengali) else top.frequency
        val bareFreq = if (validator.isLoaded()) validator.getFrequency(bareResult.bengali) else bareResult.frequency
        val topEvidence = if (topFreq > 0) topFreq else top.frequency
        val bareEvidence = if (bareFreq > 0) bareFreq else bareResult.frequency

        if (bareEvidence + 15 < topEvidence) return ranked

        return ranked.toMutableList().apply {
            removeAt(bareIdx)
            add(0, bareResult)
        }
    }

    private fun bareHasInherentFinalOPreference(bengali: String): Boolean {
        return listOf("ড়", "ঢ়", "ট", "ঠ", "ড", "ঢ", "ণ", "ন", "র", "ল")
            .any { bengali.endsWith(it) }
    }

    /**
     * Layer 0: Section narrowing using 480K Bengali dictionary sections.
     */
    private fun convertBySection(key: String): ConversionResult? {
        val suggestions = sectionEngine.getSectionSuggestions(key, 5)
        if (suggestions.isEmpty()) return null
        // Score by phonetic overlap, reject hyphenated garbage
        var scored = suggestions
            .filter { !it.bengali.contains("-") }  // Reject hyphenated entries (garbage from 480K)
            .map { s ->
                val overlap = PhoneticOverlapScorer.score(key, ReverseTransliterator.reverseWord(s.bengali))
                s to overlap.score
            }.filter { it.second > 0.85 }
        if (scored.isEmpty()) return null

        // Apply consonant filters (same logic as dictionary lookup) to prevent
        // section narrowing from bypassing phonetic→Bengali consonant rules.
        // e.g., "sh" input should NOT return স-starting words (needs শ/ষ)
        if (key.startsWith("sh")) {
            scored = scored.filter { !it.first.bengali.startsWith("স") }
            if (scored.isEmpty()) return null
        }
        if (key.startsWith("s") && !key.startsWith("sh")) {
            scored = scored.filter { !it.first.bengali.startsWith("শ") }
            if (scored.isEmpty()) return null
        }

        val best = scored.maxByOrNull { it.second }!!
        return ConversionResult(best.first.bengali, best.first.confidence, ResolutionSource.SECTION)
    }

    // ======================== ROOT DECOMPOSITION (7 CASES) ========================

    /**
     * Common Bengali productive suffixes (postpositions/case markers/determiners).
     * These can attach to ANY noun root without being listed in the dictionary.
     * Map from phonetic suffix to Bengali suffix.
     */
    private val productiveVerbSuffixes: Map<String, String> = linkedMapOf(
        // Present/progressive colloquial verb endings. These are productive:
        // dictionary builds should not need variants like korci/korteco/partecilam.
        "ci" to "ছি",          // করছি
        "ce" to "ছে",          // করছে
        "co" to "ছো",          // করছো
        "cen" to "ছেন",        // করছেন
        "cilam" to "ছিলাম",    // করছিলাম
        "cile" to "ছিলে",      // করছিলে
        "cilo" to "ছিলো",      // করছিলো
        "cilen" to "ছিলেন",    // করছিলেন
        // S18: informal 2nd person (চট্টগ্রাম/ঢাকা chat register):
        // আছোস "achhos", পারোস "paros", করোস "koros".
        "os" to "োস",
        "teci" to "তেছি",      // করতেছি
        "techi" to "তেছি",     // legacy/common: kortechi -> করতেছি
        // S16 note: on store-backed engines, tryNegationCompound runs BEFORE
        // this layer and resolves *na keys against real store words
        // (bujtecina -> বুঝতেছি + না). These two entries remain for
        // seed-only mode (no validator/store), where the table is the only
        // path for partecina/kortecina-class forms.
        "tecina" to "তেছিনা",  // করতেছিনা
        "techina" to "তেছিনা", // legacy/common: kortechina -> করতেছিনা
        "tece" to "তেছে",      // করতেছে
        "teche" to "তেছে",     // legacy/common: korteche -> করতেছে
        "teco" to "তেছো",      // করতেছো
        "techo" to "তেছো",     // legacy/common: kortecho -> করতেছো
        "tecen" to "তেছেন",    // করতেছেন
        "techen" to "তেছেন",   // legacy/common: kortechen -> করতেছেন
        "tecilam" to "তেছিলাম", // করতেছিলাম
        "techilam" to "তেছিলাম", // legacy/common: kortechilam -> করতেছিলাম
        "tecile" to "তেছিলে",   // করতেছিলে
        "techile" to "তেছিলে",  // legacy/common: kortechile -> করতেছিলে
        "tecilo" to "তেছিলো",   // করতেছিলো
        "techilo" to "তেছিলো",  // legacy/common: kortechilo -> করতেছিলো
        "tecilen" to "তেছিলেন", // করতেছিলেন
        "techilen" to "তেছিলেন", // legacy/common: kortechilen -> করতেছিলেন
    )

    private val productiveSuffixes: Map<String, String> = mapOf(
        // Case markers / postpositions (attach to any noun)
        "ta" to "টা",    // definite article: বইটা (the book)
        "ti" to "টি",    // definite article (formal): বইটি
        "te" to "তে",    // locative/instrumental: ঘরতে (in the house)
        "ke" to "কে",    // accusative/dative: মানুষকে (to the person)
        "er" to "ের",    // possessive/genitive: মানুষের (of the person)
        "rta" to "রটা",  // possessive + definite: বাড়ীরটা/বাড়িরটা
        "ra" to "রা",    // plural (animate): মানুষরা (people)
        "der" to "দের",  // plural genitive: মানুষদের (of the people)
        "gulo" to "গুলো", // plural (inanimate): বইগুলো (the books)
        "guli" to "গুলি", // plural (formal): বইগুলি
        // Verb inflection suffixes (attach to verb roots without conjunct)
        "lam" to "লাম",  // past 1st person: করলাম, থাকলাম
        "le" to "লে",    // past/conditional 2nd: করলে, দেখলে
        "lo" to "লো",    // past 3rd person: করলো, রাখলো
        "bo" to "বো",    // future 1st person: করবো, রাখবো
        "be" to "বে",    // future 3rd person: করবে, দেখবে
        "ben" to "বেন",  // future formal: করবেন, রাখবেন
    ) + productiveVerbSuffixes

    private val verbProductiveSuffixPhonetics: Set<String> =
        setOf("lam", "le", "lo", "bo", "be", "ben") + productiveVerbSuffixes.keys

    private val verbRootPreferences: Map<String, List<String>> = mapOf(
        "par" to listOf("পার"),
    )

    /**
     * Check if a Unicode character is a Bengali consonant (ক-হ range, plus ড়, ঢ়, য়, ৎ).
     */
    private fun isBengaliConsonantChar(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x0995..0x09A8) ||  // ক-ন
               (code in 0x09AA..0x09B9) ||  // প-হ
               code == 0x09DC ||             // ড়
               code == 0x09DD ||             // ঢ়
               code == 0x09DF ||             // য়
               code == 0x09CE               // ৎ
    }

    /**
     * Check if a string ends with a Bengali consonant.
     * Handles compound consonants with nukta (য়, ড়, ঢ়) which are two Unicode chars:
     * base consonant + nukta (়, U+09BC). Web parity: matches SmartEngine.endsWithBengaliConsonant.
     */
    private fun endsWithBengaliConsonant(text: CharSequence): Boolean {
        if (text.isEmpty()) return false
        // Check two-char nukta consonants: য়, ড়, ঢ়
        if (text.length >= 2) {
            val lastTwo = text.substring(text.length - 2)
            if (lastTwo == "য়" || lastTwo == "ড়" || lastTwo == "ঢ়") return true
        }
        return isBengaliConsonantChar(text.last())
    }

    /**
     * Get the productive Bengali suffix for a phonetic suffix, if it matches.
     * Returns null if the suffix is not a known productive form.
     */
    private fun getProductiveSuffix(suffixPhonetic: String): String? {
        return productiveSuffixes[suffixPhonetic.lowercase()]
    }

    private fun tryProductiveVerbSuffixConversion(key: String): ConversionResult? {
        if (key.length < 5) return null

        // S16: an invented root+suffix composition must not preempt exact
        // store evidence for the typed key. bujteci matched suffix "techi"
        // with junk root বুজয় and returned বুজয়তেছি@0.97 while the store
        // held the real বুঝতেছি under the exact key. Defer when the store's
        // top hit is a suggestible word or validator-attested.
        if (phoneticIndex != null) {
            storeLookup(key).firstOrNull()?.let { top ->
                if (top.tier == PhoneticIndexHit.TIER_A || validator.isValid(top.bengali)) {
                    return null
                }
            }
        }

        for (suffixPhonetic in productiveVerbSuffixes.keys.sortedByDescending { it.length }) {
            if (!key.endsWith(suffixPhonetic) || key.length <= suffixPhonetic.length + 1) continue

            val rootPhonetic = key.dropLast(suffixPhonetic.length)
            if (rootPhonetic.length < 2) continue

            var rootResults = dictionary.lookup(rootPhonetic)
            val rootBengali = if (rootResults.isNotEmpty()) {
                if (key.startsWith("z")) {
                    rootResults = rootResults.filter { !it.bengali.startsWith("জ") }
                }
                if (key.startsWith("j") && !key.startsWith("jh")) {
                    rootResults = rootResults.filter { !it.bengali.startsWith("য") }
                }
                if (key.startsWith("s") && !key.startsWith("sh")) {
                    rootResults = rootResults.filter { !it.bengali.startsWith("শ") }
                }
                if (key.startsWith("sh")) {
                    rootResults = rootResults.filter { !it.bengali.startsWith("স") }
                }
                if (rootResults.isEmpty()) continue

                // S16 stem trust (same fix as convertByRootDecomposition):
                // junk corpus roots (বুজয়) must not outrank attested roots.
                // Both junk and real roots can pass isValid (the 476K list
                // carries corpus tail noise), so validator FREQUENCY breaks
                // the tie: বুজ@67 over বুজয়@floor.
                if (validator.isLoaded()) {
                    rootResults = rootResults.sortedWith(
                        compareByDescending<com.banglu.engine.types.LookupResult> { validator.isValid(it.bengali) }
                            .thenByDescending { validator.getFrequency(it.bengali) }
                    )
                }

                val root = verbRootPreferences[rootPhonetic]
                    ?.firstNotNullOfOrNull { preferred ->
                        rootResults.firstOrNull { it.bengali == preferred }
                    }
                    ?: rootResults.first()

                if (root.matchedPhonetic.isNotEmpty() && root.matchedPhonetic != rootPhonetic) continue
                root.bengali
            } else if (suffixPhonetic.startsWith("te")) {
                convertByPatterns(rootPhonetic).bengali.takeIf { it.isNotEmpty() } ?: continue
            } else {
                continue
            }

            val suffix = getProductiveSuffix(suffixPhonetic) ?: continue
            val bengali = rootBengali + suffix
            val literal = convertByPatterns(key)
            val alternatives = (listOf(Alternative(literal.bengali, literal.confidence.coerceAtMost(0.82))) + literal.alternatives)
                .filter { it.bengali != bengali }

            return ConversionResult(
                bengali = bengali,
                confidence = 0.97,
                source = ResolutionSource.RULE,
                alternatives = alternatives
            )
        }

        return null
    }

    /**
     * Map phonetic vowel suffixes to Bengali vowel-kar (dependent) forms.
     * Used when a vowel suffix follows a consonant-ending dictionary root.
     * e.g., root সুন্দর (sundor) + "i" -> সুন্দরী (with ী-kar, not ই standalone)
     *
     * Returns Pair(kar, altKar?) or null.
     */
    private fun getVowelKar(suffixPhonetic: String): Pair<String, String?>? {
        return when (suffixPhonetic.lowercase()) {
            "i"  -> Pair("ি", "ী")     // default short ি; explicitly long via 'ii'/'ee'
            "ii" -> Pair("ী", null)     // explicitly long ী
            "ee" -> Pair("ী", null)     // explicitly long ী
            "e"  -> Pair("ে", null)     // jibone -> জীবনে
            "u"  -> Pair("ু", "ূ")     // short u-kar, alt long
            "uu" -> Pair("ূ", null)     // explicitly long ূ
            "oo" -> Pair("ূ", null)     // explicitly long ূ
            "o"  -> Pair("ো", null)     // o-kar
            "a"  -> Pair("া", null)     // a-kar
            "oi" -> Pair("ৈ", null)     // oi-kar
            "ou" -> Pair("ৌ", null)     // ou-kar
            else -> null
        }
    }

    /**
     * Generate all possible Bengali forms for a root+suffix combination.
     * Tries both direct attachment and hasanta junction, with ambiguous char swaps.
     *
     * Priority scoring:
     * - suffix ends with 'a' -> prefer direct attachment (suffix is a postposition)
     * - suffix ends with 'o' -> prefer hasanta junction (conjunct formation)
     */
    private fun generateAllRootCandidates(
        rootBengali: String,
        suffixPhonetic: String
    ): List<Pair<String, Int>> {
        val candidates = mutableListOf<Pair<String, Int>>()
        val seen = mutableSetOf<String>()

        val suffixEndsWithO = suffixPhonetic.endsWith("o")

        // Convert suffix using pattern engine, then generate ambiguous char variants
        val suffixResult = convertByPatterns(suffixPhonetic)
        val suffixVariants = generateSuffixVariants(suffixResult.bengali)

        for (suffix in suffixVariants) {
            val lastChar = rootBengali.last()
            val firstChar = suffix[0]
            val bothConsonants =
                isBengaliConsonantChar(lastChar) && isBengaliConsonantChar(firstChar)

            // === Direct attachment (no hasanta) ===
            val direct = rootBengali + suffix
            if (direct !in seen) {
                seen.add(direct)
                candidates.add(Pair(direct, if (suffixEndsWithO) 4 else 10))
            }

            // === Hasanta junction (conjunct formation) ===
            if (bothConsonants) {
                val hasantaForm = rootBengali + "\u09CD" + suffix  // ্ (hasanta)
                if (hasantaForm !in seen) {
                    seen.add(hasantaForm)
                    candidates.add(Pair(hasantaForm, if (suffixEndsWithO) 7 else 4))
                }
            }

            // === Strip trailing vowel marks (inherent vowel handling) ===
            val trailingMarks = listOf("া", "ো", "ে", "ি", "ী", "ু", "ূ", "ৈ", "ৌ")
            val forms = mutableListOf(direct)
            if (bothConsonants) forms.add(rootBengali + "\u09CD" + suffix)

            for (form in forms) {
                for (mark in trailingMarks) {
                    if (form.endsWith(mark)) {
                        val stripped = form.substring(0, form.length - 1)
                        if (stripped !in seen) {
                            seen.add(stripped)
                            val isHasantaForm = form.contains("\u09CD")
                            // Only give high priority to hasanta+stripped when suffix ends with 'o'
                            val priority = if (suffixEndsWithO && isHasantaForm) 12 else 3
                            candidates.add(Pair(stripped, priority))
                        }
                    }
                }
            }
        }

        // Sort by priority for consistent validation order
        return candidates.sortedByDescending { it.second }
    }

    /**
     * Generate Bengali suffix variants by swapping ambiguous consonants.
     * Main swaps: ত↔ট, দ↔ড, ন↔ণ, স↔শ↔ষ
     */
    private fun generateSuffixVariants(suffixBengali: String): List<String> {
        val variants = mutableListOf(suffixBengali)
        val seen = mutableSetOf(suffixBengali)

        // Swap rules for the first consonant of the suffix
        val swaps = listOf(
            "ত" to "ট", "ট" to "ত",
            "দ" to "ড", "ড" to "দ",
            "ন" to "ণ", "ণ" to "ন",
            "স" to "শ", "শ" to "স",
            "স" to "ষ", "ষ" to "স",
        )

        for ((from, to) in swaps) {
            if (suffixBengali.startsWith(from)) {
                val swapped = to + suffixBengali.substring(from.length)
                if (swapped !in seen) {
                    seen.add(swapped)
                    variants.add(swapped)
                }
            }
        }

        return variants
    }

    /**
     * Join a consonant-ending root with a suffix, converting independent vowels
     * at the junction to their dependent (kar) forms.
     * e.g., কর + এন -> করেন (not করএন), জাম + আই -> জামাই (not জামআই)
     */
    private fun joinRootSuffix(rootBengali: String, suffixBengali: String): String {
        if (suffixBengali.isEmpty()) return rootBengali
        val lastRoot = rootBengali.last()
        if (!isBengaliConsonantChar(lastRoot)) {
            return rootBengali + suffixBengali
        }

        val independentToDependent = mapOf(
            'আ' to "া", 'ই' to "ি", 'ঈ' to "ী", 'উ' to "ু", 'ঊ' to "ূ",
            'এ' to "ে", 'ঐ' to "ৈ", 'ও' to "ো", 'ঔ' to "ৌ", 'ঋ' to "ৃ",
            'অ' to "",  // inherent vowel — no visible kar
        )

        val firstSuffix = suffixBengali[0]
        val depForm = independentToDependent[firstSuffix]
        if (depForm != null) {
            return rootBengali + depForm + suffixBengali.substring(1)
        }
        return rootBengali + suffixBengali
    }

    /**
     * Layer 1.5: Root decomposition - try splitting word into dictionary root + pattern suffix.
     *
     * 7-case system ported from web engine (SmartEngine.ts tryRootDecomposition):
     *   Case 1: Productive suffixes (টা, টি, তে, কে, ের, রা, দের, গুলো, etc.)
     *   Case 2: Single-char 'o' suffix -> inherent vowel OR explicit ো-kar
     *   Case 3: Single-char 'a' suffix -> explicit া-kar
     *   Case 4: Single vowel suffix (i/ii/ee/e/u/uu/oo/oi/ou) -> vowel কার
     *   Case 5: 2-char suffix ending in 'o' + root consonant -> hasanta junction
     *   Case 6: 2-char suffix NOT ending in 'o' -> direct attachment
     *   Case 7: Arbitrary suffix with 480K validation
     */
    /** Reentrancy guard for [tryNegationCompound] (it re-enters [convertWord]). */
    private var inNegationCompound = false

    /**
     * S16: resolve keys with an attached negation ("bujtecina", "partesina") as
     * prefix-word + না. The prefix runs through the FULL pipeline (store-first,
     * so habit aliases like bujteci -> বুঝতেছি apply) and must resolve to a
     * validator-attested word with high confidence; otherwise this layer stays
     * silent and the normal layers handle the key.
     */
    /**
     * S22: resolve a glued two-word chat compound ("bujteparcina") by trying
     * every split point and requiring BOTH halves to resolve to high-
     * confidence validator-attested words. Output is the proper two-word
     * spelling ("বুঝতে পারছিনা"). Splits are scored by bigram evidence first
     * (observed pair wins), then by the weaker half's frequency.
     */
    private var inCompoundSplit = false
    private fun tryCompoundSplit(key: String): ConversionResult? {
        if (inCompoundSplit || inNegationCompound) return null
        if (key.length < 9 || !validator.isLoaded()) return null
        if (!key.all { it in 'a'..'z' }) return null

        // Priority: if the glued key is ONE EDIT from a real single word
        // (duniajora -> duniyajora -> দুনিয়াজোড়া), that word wins — users
        // glue phrases, but they also just typo long words.
        tryStoreTypoCorrection(key)?.let { single ->
            if (validator.getFrequency(single.bengali) >= 40) return single
        }

        var best: Triple<String, String, Double>? = null
        inCompoundSplit = true
        try {
            for (i in 4..(key.length - 4)) {
                val leftKey = key.substring(0, i)
                val rightKey = key.substring(i)
                val left = convertWord(leftKey)
                if (left.confidence < 0.9 || !validator.isValid(left.bengali)) continue
                val right = convertWord(rightKey)
                if (right.confidence < 0.9) continue
                // The right half may itself be an attached-negation form
                // (parcina -> পারছিনা) — accept validator words or negation
                // compounds whose stem the validator attests.
                val rightValid = validator.isValid(right.bengali) ||
                    (right.bengali.endsWith("না") &&
                        validator.isValid(right.bengali.removeSuffix("না")))
                if (!rightValid) continue

                val pairEvidence = bigramModel.bigramCount(left.bengali, right.bengali)
                val minFreq = minOf(
                    validator.getFrequency(left.bengali),
                    if (validator.isValid(right.bengali)) validator.getFrequency(right.bengali)
                    else validator.getFrequency(right.bengali.removeSuffix("না"))
                )
                val score = pairEvidence * 1000.0 + minFreq
                if (best == null || score > best!!.third) {
                    best = Triple(left.bengali, right.bengali, score)
                }
            }
        } finally {
            inCompoundSplit = false
        }
        val found = best ?: return null
        // Both words must carry real evidence — junk-floor halves stay out.
        if (found.third < 25) return null
        return ConversionResult(
            bengali = "${found.first} ${found.second}",
            confidence = 0.88,
            source = ResolutionSource.DICTIONARY,
            alternatives = listOf(Alternative(found.first + found.second, 0.7))
        )
    }

    /**
     * S22: edit-distance-1 typo correction against the compiled index and the
     * full pipeline. Fires ONLY from the low-confidence fallthrough — a
     * confident exact word is never second-guessed. Two passes:
     *  1. all edit-1 variants (delete/transpose/substitute/insert) looked up
     *     in the store as O(1) exact keys, tier-A hits only;
     *  2. delete/transpose variants (the cheap, most common typo shapes) run
     *     through the full pipeline so alias- and negation-resolved words
     *     ("bujjtecina" -> bujtecina -> বুঝতেছিনা) are reachable too.
     */
    private var inTypoCorrection = false
    private fun tryStoreTypoCorrection(key: String): ConversionResult? {
        if (inTypoCorrection || inCompoundSplit || inNegationCompound) return null
        if (key.length < 4 || !validator.isLoaded()) return null
        if (!key.all { it in 'a'..'z' }) return null

        data class Cand(val word: String, val weight: Double, val freq: Int)
        var best: Cand? = null
        fun consider(variantKey: String, editWeight: Double) {
            val top = storeLookup(variantKey).firstOrNull() ?: return
            if (top.tier != PhoneticIndexHit.TIER_A) return
            if (!validator.isValid(top.bengali)) return
            val freq = maxOf(top.frequency, validator.getFrequency(top.bengali))
            // Corrections replace what the user typed — only corpus-evidenced
            // words (55+ band) qualify; junk-tail rows (বাড়িআলা) never do.
            if (freq < 55) return
            val c = Cand(top.bengali, editWeight, freq)
            fun score(x: Cand) = x.weight * (1.0 + kotlin.math.ln(x.freq.toDouble()))
            if (best == null || score(c) > score(best!!)) best = c
        }

        val n = key.length
        for (i in 0 until n) {
            // deletion (doubled-letter deletions are the most trusted edit)
            val del = key.removeRange(i, i + 1)
            consider(del, if (i > 0 && key[i] == key[i - 1]) 1.0 else 0.9)
            // transposition
            if (i < n - 1 && key[i] != key[i + 1]) {
                val t = key.toCharArray().also { val c = it[i]; it[i] = it[i + 1]; it[i + 1] = c }
                consider(String(t), 1.0)
            }
            // substitution
            for (ch in 'a'..'z') if (ch != key[i]) {
                consider(key.substring(0, i) + ch + key.substring(i + 1), 0.7)
            }
        }
        for (i in 0..n) for (ch in 'a'..'z') {
            val w = if (ch in "aeiou") 0.95 else 0.75
            consider(key.substring(0, i) + ch + key.substring(i), w)
        }

        best?.let {
            return ConversionResult(
                bengali = it.word,
                confidence = 0.85,
                source = ResolutionSource.DICTIONARY,
                alternatives = emptyList()
            )
        }

        // Pass 2: delete/transpose through the full pipeline (bounded: ~2n calls).
        inTypoCorrection = true
        try {
            var bestFull: ConversionResult? = null
            for (i in 0 until n) {
                val variants = buildList {
                    add(key.removeRange(i, i + 1))
                    if (i < n - 1 && key[i] != key[i + 1]) {
                        add(String(key.toCharArray().also {
                            val c = it[i]; it[i] = it[i + 1]; it[i + 1] = c
                        }))
                    }
                }
                for (v in variants) {
                    if (v.length < 3) continue
                    val r = convertWord(v)
                    if (r.confidence < 0.92) continue
                    // Plain words need corpus evidence (bariwala must not
                    // become junk-tail বাড়িআলা); negation compounds are
                    // already evidence-gated by their own layer.
                    val negationForm = r.bengali.endsWith("না") &&
                        validator.isValid(r.bengali.removeSuffix("না"))
                    val evidenced = validator.isValid(r.bengali) &&
                        validator.getFrequency(r.bengali) >= 55
                    if (!negationForm && !evidenced) continue
                    if (bestFull == null || r.confidence > bestFull!!.confidence) bestFull = r
                }
            }
            bestFull?.let {
                return ConversionResult(
                    bengali = it.bengali,
                    confidence = 0.84,
                    source = ResolutionSource.DICTIONARY,
                    alternatives = emptyList()
                )
            }
        } finally {
            inTypoCorrection = false
        }
        return null
    }

    private fun tryNegationCompound(key: String): ConversionResult? {
        if (inNegationCompound) return null
        // S18: "nai" (খাইনাই class) joined the "na" class from the S17
        // register study; S25 adds the attached তো particle (dekhisto =
        // দেখিস তো, koristo = করিস তো) — same machinery, but তো composes
        // SPACED (দেখিসতো is unattested; দেখিস@65 + তো@85 both are).
        val suffix = when {
            key.endsWith("nai") -> "nai" to "নাই"
            key.endsWith("na") -> "na" to "না"
            key.endsWith("to") -> "to" to "তো"
            else -> return null
        }
        // Minimum lengths: "na" keeps the proven S16 floor (>= 7 — sona/kena
        // class must never reach here); "nai" allows >= 6 (hoinai -> হইনাই);
        // "to" needs a 5+ char prefix (moto/gosto-class words stay clear and
        // the whole-word store guard below protects the rest).
        val minLen = when (suffix.first) {
            "nai" -> 6
            "to" -> 6   // hobeto -> হবে তো; store guard keeps dekhto/gosto safe
            else -> 7
        }
        if (key.length < minLen) return null
        if (!validator.isLoaded()) return null

        // Whole-word precedence: if the store attests the FULL key (মন্ত্রণা,
        // করছিনা as a real corpus word, ...), the store layer must resolve it —
        // this layer only serves keys nothing else owns.
        storeLookup(key).firstOrNull()?.let { top ->
            if (top.tier == PhoneticIndexHit.TIER_A || validator.isValid(top.bengali)) {
                return null
            }
        }

        val prefixKey = key.dropLast(suffix.first.length)
        inNegationCompound = true
        val prefix = try {
            convertWord(prefixKey)
        } finally {
            inNegationCompound = false
        }
        if (prefix.confidence < 0.9) return null
        if (!validator.isValid(prefix.bengali)) return null

        // Stem tie-break: the in-memory layers can hand back a floor-frequency
        // corpus squatter (parteci -> পার্তেছি@1) while the store's top hit for
        // the same key is the real word (পারতেছি@33). Validator frequency is
        // the shared evidence scale — take the stronger attested stem.
        var stem = prefix.bengali
        storeLookup(prefixKey).firstOrNull { validator.isValid(it.bengali) }?.let { top ->
            if (validator.getFrequency(top.bengali) > validator.getFrequency(stem)) {
                stem = top.bengali
            }
        }

        // "to" only: the -ত past-habitual reading owns the key when attested
        // (dekhto -> দেখত, not দেখ তো; hobeto composes — হবেত is not a word).
        if (suffix.first == "to" && validator.getFrequency(stem + "ত") >= 25) return null

        // না/নাই attach glued by convention (করিনা attested); তো is written
        // as a separate word — primary and alternative swap for it.
        return if (suffix.first == "to") {
            ConversionResult(
                bengali = "$stem ${suffix.second}",
                confidence = 0.93,
                source = ResolutionSource.DICTIONARY,
                alternatives = listOf(Alternative(stem + suffix.second, 0.85))
            )
        } else {
            ConversionResult(
                bengali = stem + suffix.second,
                confidence = 0.93,
                source = ResolutionSource.DICTIONARY,
                alternatives = listOf(Alternative("$stem ${suffix.second}", 0.9))
            )
        }
    }

    private fun convertByRootDecomposition(key: String): ConversionResult? {
        if (key.length < 4) return null // Too short for meaningful decomposition
        val validatorLoaded = validator.isLoaded()

        // Try progressively shorter prefixes to find the longest dictionary root.
        for (splitPos in key.length - 1 downTo 2) {
            val rootPhonetic = key.substring(0, splitPos)
            val suffixPhonetic = key.substring(splitPos)

            // Root must be an EXACT dictionary match (not suffix-stripped).
            var rootResults = dictionary.lookup(rootPhonetic)
            if (rootResults.isEmpty()) continue

            // Enforce consonant rules on root
            if (key.startsWith("z")) {
                rootResults = rootResults.filter { !it.bengali.startsWith("জ") }
            }
            if (key.startsWith("j") && !key.startsWith("jh")) {
                rootResults = rootResults.filter { !it.bengali.startsWith("য") }
            }
            if (key.startsWith("s") && !key.startsWith("sh")) {
                rootResults = rootResults.filter { !it.bengali.startsWith("শ") }
            }
            if (key.startsWith("sh")) {
                rootResults = rootResults.filter { !it.bengali.startsWith("স") }
            }
            if (rootResults.isEmpty()) continue

            // S16 stem trust: prefer roots the 484K validator attests, then
            // validator frequency. Junk corpus words otherwise outrank real
            // roots on raw in-memory frequency (বুজয়@68-unevidenced beat
            // বুজ@60 — and both can pass isValid, since the 476K list carries
            // corpus tail noise) and every suffix composition inherits the
            // garbage: bujte -> বুজয়তে, bujtechi -> বুজয়তেছি.
            if (validatorLoaded) {
                rootResults = rootResults.sortedWith(
                    compareByDescending<com.banglu.engine.types.LookupResult> { validator.isValid(it.bengali) }
                        .thenByDescending { validator.getFrequency(it.bengali) }
                )
            }

            // Skip suffix-stripped matches — only use exact phonetic matches
            val topResult = if (suffixPhonetic in verbProductiveSuffixPhonetics) {
                verbRootPreferences[rootPhonetic]
                    ?.firstNotNullOfOrNull { preferred ->
                        rootResults.firstOrNull { it.bengali == preferred }
                    }
                    ?: rootResults[0]
            } else {
                rootResults[0]
            }
            if (topResult.matchedPhonetic.isNotEmpty() && topResult.matchedPhonetic != rootPhonetic) continue

            val rootBengali = topResult.bengali
            val rootEndsWithConsonant = isBengaliConsonantChar(rootBengali.last())

            // Helper: validate a root decomposition result before returning it.
            // Root decomposition often produces garbage by combining accidental dictionary
            // roots with suffixes. Only accept if: (a) the combined result is a valid 480K word, OR
            // (b) the root was an exact phonetic match (not found through variant generation).
            fun validateResult(result: ConversionResult): ConversionResult? {
                if (validatorLoaded && validator.isValid(result.bengali)) {
                    return result // Valid word — accept
                }
                // Not a valid word — only accept if root was an EXACT match with high confidence
                val isExactRoot = topResult.matchedPhonetic.isEmpty() || topResult.matchedPhonetic == rootPhonetic
                if (isExactRoot && topResult.confidence >= 0.85) {
                    return result // Exact root match — accept even if combined form isn't in 480K
                }
                return null // Reject — likely garbage from variant-generated root
            }

            // === 1. Productive suffixes (টা, টি, তে, কে, etc.) ===
            val productiveSuffix = getProductiveSuffix(suffixPhonetic)
            if (productiveSuffix != null) {
                val primary = if (suffixPhonetic == "rta" && rootBengali.endsWith("ি")) {
                    rootBengali.dropLast(1) + "ীরটা"
                } else {
                    rootBengali + productiveSuffix
                }
                val alternatives = if (primary != rootBengali + productiveSuffix) {
                    listOf(Alternative(rootBengali + productiveSuffix, 0.88))
                } else {
                    emptyList()
                }
                val result = ConversionResult(
                    bengali = primary,
                    confidence = 0.95,
                    source = ResolutionSource.DICTIONARY,
                    alternatives = alternatives
                )
                val validated = validateResult(result)
                if (validated != null) return validated
                continue // Root wasn't good enough — try shorter root
            }

            // === 2. Single-char 'o' -> inherent vowel OR explicit ো-kar ===
            if (suffixPhonetic == "o" && rootEndsWithConsonant) {
                val withOkar = rootBengali + "ো"
                val withoutOkar = rootBengali
                val withOkarValid = validatorLoaded && validator.isValid(withOkar)
                val withoutOkarValid = validatorLoaded && validator.isValid(withoutOkar)

                if (withOkarValid && !withoutOkarValid) {
                    // Only ো version is valid — use it
                    return ConversionResult(
                        bengali = withOkar,
                        confidence = 0.95,
                        source = ResolutionSource.DICTIONARY,
                        alternatives = listOf(Alternative(withoutOkar, 0.80))
                    )
                } else if (withOkarValid && withoutOkarValid) {
                    // Both valid — strongly prefer ো since user explicitly typed 'o'
                    // Only keep without-ো if it has MUCH higher frequency (>30 gap)
                    val okarFreq = validator.getFrequency(withOkar)
                    val noOkarFreq = validator.getFrequency(withoutOkar)
                    if (noOkarFreq > okarFreq + 30) {
                        return ConversionResult(
                            bengali = withoutOkar,
                            confidence = 0.95,
                            source = ResolutionSource.DICTIONARY,
                            alternatives = listOf(Alternative(withOkar, 0.90))
                        )
                    }
                    // ো version preferred — user typed 'o' explicitly
                    return ConversionResult(
                        bengali = withOkar,
                        confidence = 0.95,
                        source = ResolutionSource.DICTIONARY,
                        alternatives = listOf(Alternative(withoutOkar, 0.90))
                    )
                }
                // Only without-ো is valid
                return ConversionResult(
                    bengali = withoutOkar,
                    confidence = 0.93,
                    source = ResolutionSource.DICTIONARY,
                    alternatives = listOf(Alternative(withOkar, 0.88))
                )
            }

            // === 3. Single-char 'a' -> explicit া-kar ===
            if (suffixPhonetic == "a" && rootEndsWithConsonant) {
                return ConversionResult(
                    bengali = rootBengali + "া",
                    confidence = 0.93,
                    source = ResolutionSource.DICTIONARY,
                    alternatives = listOf(Alternative(rootBengali, 0.85))
                )
            }

            // === 4. Single vowel suffix -> vowel-kar (dependent form) ===
            if (rootEndsWithConsonant) {
                val vowelKar = getVowelKar(suffixPhonetic)
                if (vowelKar != null) {
                    val alts = if (vowelKar.second != null) {
                        listOf(Alternative(rootBengali + vowelKar.second!!, 0.85))
                    } else {
                        emptyList()
                    }
                    val result = ConversionResult(
                        bengali = rootBengali + vowelKar.first,
                        confidence = 0.93,
                        source = ResolutionSource.DICTIONARY,
                        alternatives = alts
                    )
                    val validated = validateResult(result)
                    if (validated != null) return validated
                    continue
                }
            }

            // === 5. Short consonant+'o' suffix -> hasanta junction (conjunct) ===
            if (suffixPhonetic.length == 2 && suffixPhonetic.endsWith("o") && rootEndsWithConsonant) {
                val candidates = generateAllRootCandidates(rootBengali, suffixPhonetic)
                if (candidates.isNotEmpty()) {
                    val best = candidates[0]
                    val alts = candidates
                        .drop(1)
                        .take(3)
                        .filter { it.first != best.first }
                        .map { Alternative(it.first, 0.88) }
                    val result = ConversionResult(
                        bengali = best.first,
                        confidence = 0.95,
                        source = ResolutionSource.DICTIONARY,
                        alternatives = alts
                    )
                    val validated = validateResult(result)
                    if (validated != null) return validated
                    continue
                }
            }

            // === 6. Short consonant+vowel suffix -> direct attachment ===
            if (suffixPhonetic.length == 2 && rootEndsWithConsonant && !suffixPhonetic.endsWith("o")) {
                val suffixResult = convertByPatterns(suffixPhonetic)
                val directForm = joinRootSuffix(rootBengali, suffixResult.bengali)
                val result = ConversionResult(
                    bengali = directForm,
                    confidence = 0.92,
                    source = ResolutionSource.DICTIONARY,
                    alternatives = emptyList()
                )
                val validated = validateResult(result)
                if (validated != null) return validated
                continue // Don't return unvalidated — fall through to pattern engine
            }

            // === 7. 480K validator — validate arbitrary root+suffix combinations ===
            if (validatorLoaded) {
                val candidates = generateAllRootCandidates(rootBengali, suffixPhonetic)
                val validated = mutableListOf<Pair<String, Int>>()
                for (candidate in candidates) {
                    if (validator.isValid(candidate.first)) {
                        validated.add(Pair(candidate.first, candidate.second + 5))
                    }
                }
                if (validated.isNotEmpty()) {
                    val sorted = validated.sortedByDescending { it.second }
                    val best = sorted[0]
                    val alts = sorted
                        .drop(1)
                        .filter { it.first != best.first }
                        .map { Alternative(it.first, 0.90) }
                    return ConversionResult(
                        bengali = best.first,
                        confidence = 0.95,
                        source = ResolutionSource.DICTIONARY,
                        alternatives = alts
                    )
                }
            }

            // For suffixes that don't match any of the above rules (e.g., "moi", "basha"),
            // do NOT return a result — fall through to pattern conversion.
        }

        return null
    }

    /**
     * Layers 2-4: Pattern-based conversion using ConjunctResolver, ConjunctTable,
     * NasalResolver, NatvaVidhan, ShatvaVidhan, and basic consonant/vowel mapping.
     *
     * Uses greedy longest-match strategy:
     * 1. ConjunctResolver (locked patterns like sht→ষ্ট, str→স্ত্র)
     * 2. ConjunctTable entries (kh→খ, gh→ঘ, ch→চ, chh→ছ, etc.)
     * 3. Single consonants with context-aware rules
     * 4. Vowels (independent at start, dependent after consonants)
     */
    private fun convertByPatterns(rawKey: String): ConversionResult {
        val key = rawKey.lowercase()
        tryLowercaseV2ControlRule(key)?.let { return it }

        val result = StringBuilder()
        var i = 0
        var confidence = 0.85
        val alternatives = mutableListOf<Alternative>()

        while (i < key.length) {
            // --- Hasanta/ZWNJ escape: ",," between consonants prevents conjunct formation ---
            // e.g., "k,,t" → ক্‌ত instead of ক্ত (explicit halant + ZWNJ)
            if (i + 1 < key.length && key[i] == ',' && key[i + 1] == ',') {
                if (endsWithBengaliConsonant(result.toString())) {
                    result.append("্\u200C") // hasanta + ZWNJ
                }
                i += 2
                continue
            }

            // --- Punctuation (longest match first) ---
            var punctMatched = false
            for ((phonetic, bengali) in PUNCTUATION) {
                if (key.startsWith(phonetic, i)) {
                    result.append(bengali)
                    i += phonetic.length
                    punctMatched = true
                    break
                }
            }
            if (punctMatched) continue

            // --- Digits → Bengali numerals ---
            val ch = key[i]
            if (ch in '0'..'9') {
                result.append(BENGALI_DIGITS[ch - '0'])
                i++
                continue
            }

            // Try ConjunctResolver first (highest priority, locked patterns)
            val conjunctMatch = ConjunctResolver.matchAt(key, i)
            if (conjunctMatch != null) {
                result.append(conjunctMatch.bengali)
                i += conjunctMatch.consumed
                // Check for dependent vowel after conjunct
                if (i < key.length && key[i] in "aeiou") {
                    if (shouldSuppressO(key, i)) {
                        i++ // Smart "o" suppression
                    } else {
                        val vowelResult = resolveVowel(key, i, false)
                        result.append(vowelResult.first)
                        i += vowelResult.second
                        confidence = minOf(confidence, vowelResult.third)
                    }
                }
                continue
            }

            // Try ConjunctTable entries (aspirated consonants, common conjuncts)
            var tableMatched = false
            for (entry in ConjunctTable.TABLE) {
                if (key.startsWith(entry.phonetic, i, ignoreCase = true)) {
                    // Special handling for context-sensitive entries (web parity)
                    if (entry.phonetic == "sh") {
                        // Apply ShatvaVidhan for "sh" → ষ/শ disambiguation
                        val shatva = ShatvaVidhan.resolve(result.toString(), key, i)
                        result.append(shatva.bengali)
                        confidence = minOf(confidence, shatva.confidence)
                    } else {
                        result.append(entry.bengali)
                    }
                    i += entry.phonetic.length
                    // Handle dependent vowel after conjunct table entry
                    if (i < key.length && key[i] in "aeiou") {
                        if (shouldSuppressO(key, i)) {
                            i++ // Smart "o" suppression
                        } else {
                            val vowelResult = resolveVowel(key, i, false)
                            result.append(vowelResult.first)
                            i += vowelResult.second
                            confidence = minOf(confidence, vowelResult.third)
                        }
                    }
                    tableMatched = true
                    break
                }
            }
            if (tableMatched) continue

            val char = key[i]

            when {
                // Vowels
                char in "aeiou" -> {
                    // Web parity: use endsWithBengaliConsonant to decide dependent/independent.
                    // After digits or punctuation, vowels should be independent (আ not া).
                    val afterConsonant = endsWithBengaliConsonant(result)
                    if (afterConsonant) {
                        // After a Bengali consonant — use dependent (kar) form
                        // Also apply smart-o suppression
                        if (shouldSuppressO(key, i)) {
                            i++ // consume 'o' without adding ো
                        } else {
                            val vowelResult = resolveVowel(key, i, false)
                            result.append(vowelResult.first)
                            i += vowelResult.second
                            confidence = minOf(confidence, vowelResult.third)
                        }
                    } else {
                        // Not after consonant (word-initial, after digit, after vowel) — use independent form
                        val vowelResult = resolveVowel(key, i, true, isWordInitial = result.isEmpty())
                        result.append(vowelResult.first)
                        i += vowelResult.second
                        confidence = minOf(confidence, vowelResult.third)
                    }
                }

                // Consonants
                char in "bcdfghjklmnpqrstvwxyz" -> {
                    val consonantResult = resolveConsonant(key, i, result.toString())
                    result.append(consonantResult.first)
                    i += consonantResult.second
                    confidence = minOf(confidence, consonantResult.third)

                    // Check for dependent vowel after consonant
                    if (i < key.length && key[i] in "aeiou") {
                        // Web parity: check if output actually ends with Bengali consonant.
                        // Some "consonant" letters like 'w' map to vowels (ও),
                        // so the following vowel should be independent, not dependent.
                        val outputEndsWithConsonant = endsWithBengaliConsonant(result)
                        if (outputEndsWithConsonant && shouldSuppressO(key, i)) {
                            // Smart "o" suppression: skip 'o' (inherent vowel)
                            i++ // consume 'o' without adding ো
                        } else {
                            val vowelResult = resolveVowel(key, i, !outputEndsWithConsonant)
                            result.append(vowelResult.first)
                            i += vowelResult.second
                            confidence = minOf(confidence, vowelResult.third)
                        }
                    }
                }

                else -> {
                    result.append(char)
                    i++
                }
            }
        }

        return ConversionResult(result.toString(), confidence, ResolutionSource.RULE, getAlternatives(key, result.toString()))
    }

    private fun tryLowercaseV2ControlRule(key: String): ConversionResult? {
        fun result(value: String, confidence: Double = 0.96): ConversionResult =
            ConversionResult(value, confidence, ResolutionSource.RULE, emptyList())

        when (key) {
            "x", "hc" -> return result("্")
            "w" -> return result("ঃ")
            "nq" -> return result("ঁ")
            "ng", "ong" -> return result("ং")
        }

        if (key.length > 1 && key.endsWith("c")) {
            val baseKey = key.dropLast(1)
            val base = lowercaseV2ControlBaseConsonant(baseKey)
            if (base != null) return result(base + "্")
        }

        return null
    }

    private fun lowercaseV2ControlBaseConsonant(key: String): String? {
        return when (key) {
            "kkh", "ksh" -> "ক্ষ"
            "kh" -> "খ"
            "gh" -> "ঘ"
            "ch" -> "চ"
            "jh", "zh" -> "ঝ"
            "th" -> "থ"
            "dh" -> "ধ"
            "ph", "f" -> "ফ"
            "bh", "v" -> "ভ"
            "sh" -> "শ"
            "ng" -> "ঙ"
            "k" -> "ক"
            "g" -> "গ"
            "c" -> "ছ"
            "j" -> "জ"
            "z" -> "য"
            "t" -> "ত"
            "d" -> "দ"
            "n" -> "ন"
            "p" -> "প"
            "b" -> "ব"
            "m" -> "ম"
            "r" -> "র"
            "l" -> "ল"
            "s" -> "স"
            "h" -> "হ"
            else -> null
        }
    }

    // ======================== PATTERN ALTERNATIVES GENERATOR ========================

    internal fun getAlternatives(input: String, primary: String): List<Alternative> {
        val alternatives = mutableListOf<Alternative>()
        val seen = mutableSetOf(primary)

        fun addAlt(bengali: String, confidence: Double) {
            if (seen.add(bengali)) {
                alternatives.add(Alternative(bengali, confidence))
            }
        }

        for (alt in generateDiphthongAlternatives(primary)) addAlt(alt.bengali, alt.confidence)
        for (alt in generateInitialVowelAlternatives(primary)) addAlt(alt.bengali, alt.confidence)
        for (alt in generateAmbiguousCharAlternatives(primary)) addAlt(alt.bengali, alt.confidence)
        for (candidate in generateCandidateLattice(input.lowercase().trim(), 80)) {
            if (candidate.bengali != primary) {
                addAlt(candidate.bengali, candidate.score.coerceIn(0.30, 0.96))
            }
        }

        return alternatives.take(config.maxSuggestions - 1)
    }

    private fun generateCandidateLattice(key: String, limit: Int = 20): List<CandidatePath> {
        if (key.isEmpty() || key.length > 18 || key.any { it !in 'a'..'z' }) return emptyList()

        var beam = listOf(CandidatePath("", 0.0, true))
        var i = 0

        while (i < key.length) {
            val token = nextLatticeToken(key, i) ?: return emptyList()
            val next = mutableListOf<CandidatePath>()

            for (path in beam) {
                val expansions = expandLatticeToken(token, key, i, path.bengali)
                for (expansion in expansions) {
                    next.add(
                        CandidatePath(
                            bengali = path.bengali + expansion.out,
                            score = path.score + expansion.prior,
                            literal = path.literal && expansion.literal
                        )
                    )
                }
            }

            beam = rankLatticeBeam(next, key).take(if (key.length < 4) 24 else 128)
            i += token.length
        }

        return rankLatticeBeam(beam, key)
            .distinctBy { it.bengali }
            .take(limit)
    }

    private fun nextLatticeToken(key: String, index: Int): String? {
        val rest = key.substring(index)
        val locked = listOf(
            "shtr", "shth", "shph", "shchh", "ngkh", "nggh", "chch", "jhjh",
            "sht", "shn", "shk", "shp", "shm", "sth", "sph", "str", "kkh", "ksh",
            "ngk", "ngg", "ngm", "cch", "shr", "dhr", "bhr", "ghr", "khr", "ttr",
            "ddh", "dbh", "dgh", "mbh", "lbh", "nth", "tth", "ntr", "ndr", "bdh",
            "gdh", "ndh", "gy", "jn", "pt", "kt", "gn", "mn", "nt", "nd", "nj",
            "nc", "mb", "mp", "lp", "lb", "ld", "lt", "lk", "lm", "lg", "lf",
            "tn", "tm", "tb", "db", "dg", "dm", "jb", "hm", "hn", "hl", "hb",
            "nm", "ns", "gm", "gl", "kl", "km", "kb", "pl", "pn", "ps", "bl",
            "bd", "ml", "fl", "xo", "kk", "cc", "jj", "dd", "tt", "nn", "mm", "ll",
            "ss", "pp", "bb", "rr"
        )

        for (token in locked) {
            if (rest.startsWith(token)) return token
        }

        for (token in listOf("th", "dh", "sh", "chh", "ch", "kh", "gh", "jh", "ph", "bh", "rh", "ng", "oi", "oy", "ai", "ou", "ow", "ee", "ii", "uu", "oo", "aa")) {
            if (rest.startsWith(token)) return token
        }

        return rest.firstOrNull()?.toString()
    }

    private fun expandLatticeToken(token: String, key: String, index: Int, current: String): List<TokenExpansion> {
        val afterConsonant = endsWithBengaliConsonant(current)
        val nextIndex = index + token.length
        val next = if (nextIndex < key.length) key[nextIndex] else null
        val nextIsConsonant = next != null && next in 'a'..'z' && next !in "aeiou"

        fun dependent(short: String, long: String? = null): List<TokenExpansion> {
            val primary = if (afterConsonant) short else independentVowelFor(short)
            val expansions = mutableListOf(TokenExpansion(primary, 0.90, true))
            if (long != null) {
                expansions.add(TokenExpansion(if (afterConsonant) long else independentVowelFor(long), 0.55, false))
            }
            return expansions
        }

        val mapped = when (token) {
            "shtr" -> listOf(TokenExpansion("ষ্ট্র", 1.0))
            "shth" -> listOf(TokenExpansion("ষ্ঠ", 1.0))
            "shph" -> listOf(TokenExpansion("ষ্ফ", 1.0))
            "shchh" -> listOf(TokenExpansion("শ্ছ", 1.0))
            "shch" -> listOf(TokenExpansion("শ্চ", 1.0))
            "sht" -> listOf(TokenExpansion("ষ্ট", 1.0))
            "shn" -> listOf(TokenExpansion("ষ্ণ", 1.0))
            "shk" -> listOf(TokenExpansion("ষ্ক", 1.0))
            "shp" -> listOf(TokenExpansion("ষ্প", 1.0))
            "shm" -> listOf(TokenExpansion("ষ্ম", 1.0))
            "sth" -> listOf(TokenExpansion("স্থ", 1.0))
            "sph" -> listOf(TokenExpansion("স্ফ", 1.0))
            "str" -> listOf(TokenExpansion("স্ত্র", 1.0))
            "kkh", "ksh" -> listOf(TokenExpansion("ক্ষ", 1.0))
            "ngkh" -> listOf(TokenExpansion("ঙ্খ", 1.0))
            "nggh" -> listOf(TokenExpansion("ঙ্ঘ", 1.0))
            "ngk" -> listOf(TokenExpansion("ঙ্ক", 1.0))
            "ngg" -> listOf(TokenExpansion("ঙ্গ", 1.0))
            "ngm" -> listOf(TokenExpansion("ঙ্ম", 1.0))
            "xo" -> listOf(TokenExpansion("ক্স", 1.0))
            "chch", "cch" -> listOf(TokenExpansion("চ্ছ", 1.0))
            "kh" -> listOf(TokenExpansion("খ", 1.0))
            "gh" -> listOf(TokenExpansion("ঘ", 1.0))
            "jh" -> listOf(TokenExpansion("ঝ", 1.0))
            "ph" -> listOf(TokenExpansion("ফ", 1.0))
            "bh" -> listOf(TokenExpansion("ভ", 1.0))
            "rh" -> listOf(TokenExpansion("ড়", 1.0))
            "gy", "jn" -> listOf(TokenExpansion("জ্ঞ", 1.0))
            "pt" -> listOf(TokenExpansion("প্ত", 1.0))
            "kt" -> listOf(TokenExpansion("ক্ত", 1.0))
            "nt" -> listOf(TokenExpansion("ন্ত", 1.0))
            "nd" -> listOf(TokenExpansion("ন্দ", 1.0))
            "nj" -> listOf(TokenExpansion("ঞ্জ", 1.0))
            "nc" -> listOf(TokenExpansion("ঞ্চ", 1.0))
            "mb" -> listOf(TokenExpansion("ম্ব", 1.0))
            "mp" -> listOf(TokenExpansion("ম্প", 1.0))
            "rr" -> listOf(TokenExpansion(if (afterConsonant) "্র" else "রর", 0.95))
            "th" -> listOf(TokenExpansion("থ", 0.70), TokenExpansion("ঠ", 0.42, false))
            "dh" -> listOf(TokenExpansion("ধ", 0.80), TokenExpansion("ঢ", 0.38, false))
            "sh" -> listOf(TokenExpansion("শ", 0.55), TokenExpansion("ষ", 0.42, false))
            "chh" -> listOf(TokenExpansion("ছ", 0.90))
            "ch" -> listOf(TokenExpansion("চ", 0.84), TokenExpansion("ছ", 0.58, false))
            "ng" -> if (next != null && next in "aeiou") {
                listOf(TokenExpansion("ঙ", 0.75), TokenExpansion("ং", 0.55, false))
            } else {
                listOf(TokenExpansion("ং", 0.75), TokenExpansion("ঙ", 0.55, false))
            }
            else -> null
        }
        if (mapped != null) return mapped

        if (token == "a" || token == "aa") return dependent("া")
        if (token == "i") return dependent("ি", "ী")
        if (token == "ee" || token == "ii") return dependent("ী", "ি")
        if (token == "u") return dependent("ু", "ূ")
        if (token == "oo" || token == "uu") return dependent("ূ", "ু")
        if (token == "e") return dependent("ে")
        if (token == "oi") {
            return if (afterConsonant) {
                listOf(
                    TokenExpansion("ৈ", 0.78),
                    TokenExpansion("ই", 0.56, false),
                    TokenExpansion("য়", 0.46, false)
                )
            } else {
                listOf(
                    TokenExpansion("ঐ", 0.78),
                    TokenExpansion("ওই", 0.52, false),
                    TokenExpansion("ওয়", 0.36, false)
                )
            }
        }
        if (token == "oy") {
            return if (afterConsonant) {
                listOf(
                    TokenExpansion("য়", 0.78),
                    TokenExpansion("ই", 0.54, false),
                    TokenExpansion("ৈ", 0.42, false)
                )
            } else {
                listOf(
                    TokenExpansion("ওয়", 0.78),
                    TokenExpansion("ওই", 0.50, false),
                    TokenExpansion("ঐ", 0.35, false)
                )
            }
        }
        if (token == "ai") {
            return if (afterConsonant) {
                listOf(
                    TokenExpansion("াই", 0.82),
                    TokenExpansion("ই", 0.44, false)
                )
            } else {
                listOf(
                    TokenExpansion("আই", 0.82),
                    TokenExpansion("ই", 0.38, false)
                )
            }
        }
        if (token == "ou" || token == "ow") return dependent("ৌ")
        if (token == "o") {
            if (afterConsonant && nextIsConsonant) {
                return listOf(TokenExpansion("", 0.86), TokenExpansion("ো", 0.48, false))
            }
            return if (afterConsonant) {
                listOf(TokenExpansion("ো", 0.78), TokenExpansion("", 0.40, false))
            } else {
                listOf(
                    TokenExpansion(if (nextIsConsonant) "অ" else "ও", 0.70),
                    TokenExpansion(if (nextIsConsonant) "ও" else "অ", 0.42, false)
                )
            }
        }

        return when (token) {
            "k" -> listOf(TokenExpansion("ক", 1.0))
            "g" -> listOf(TokenExpansion("গ", 1.0))
            "c" -> listOf(TokenExpansion("ছ", 0.84), TokenExpansion("চ", 0.58, false))
            "j" -> listOf(TokenExpansion("জ", 0.76), TokenExpansion("য", 0.50, false))
            "z" -> listOf(TokenExpansion("য", 0.85), TokenExpansion("জ", 0.35, false))
            "t" -> listOf(TokenExpansion("ত", 0.80), TokenExpansion("ট", 0.52, false))
            "d" -> listOf(TokenExpansion("দ", 0.75), TokenExpansion("ড", 0.48, false))
            "n" -> listOf(TokenExpansion("ন", 0.85), TokenExpansion("ণ", 0.42, false))
            "s" -> listOf(TokenExpansion("স", 0.60))
            "r" -> if (index == 0) listOf(TokenExpansion("র", 0.90)) else listOf(TokenExpansion("র", 0.85), TokenExpansion("ড়", 0.45, false))
            "y" -> if (afterConsonant) {
                listOf(TokenExpansion("্য", 0.78), TokenExpansion("য়", 0.36, false))
            } else {
                listOf(TokenExpansion(if (index == 0) "য" else "য়", 0.76), TokenExpansion(if (index == 0) "য়" else "য", 0.32, false))
            }
            "p" -> listOf(TokenExpansion("প", 1.0))
            "f" -> listOf(TokenExpansion("ফ", 1.0))
            "b" -> listOf(TokenExpansion("ব", 1.0))
            "v" -> listOf(TokenExpansion("ভ", 1.0))
            "m" -> listOf(TokenExpansion("ম", 1.0))
            "l" -> listOf(TokenExpansion("ল", 1.0))
            "h" -> listOf(TokenExpansion("হ", 1.0))
            "q" -> listOf(TokenExpansion("ক", 0.70))
            "w" -> listOf(TokenExpansion("ও", 0.55), TokenExpansion("উ", 0.35, false), TokenExpansion("ব", 0.25, false))
            "x" -> listOf(TokenExpansion("ক্স", 0.70))
            else -> listOf(TokenExpansion(token, 0.10))
        }
    }

    private fun independentVowelFor(mark: String): String {
        return when (mark) {
            "া" -> "আ"
            "ি" -> "ই"
            "ী" -> "ঈ"
            "ু" -> "উ"
            "ূ" -> "ঊ"
            "ে" -> "এ"
            "ৈ" -> "ঐ"
            "ো" -> "ও"
            "ৌ" -> "ঔ"
            else -> mark
        }
    }

    private fun rankLatticeBeam(paths: List<CandidatePath>, key: String): List<CandidatePath> {
        val unique = linkedMapOf<String, CandidatePath>()
        for (path in paths) {
            val scored = path.copy(score = scoreLatticeCandidate(path, key))
            val previous = unique[path.bengali]
            if (previous == null || scored.score > previous.score) {
                unique[path.bengali] = scored
            }
        }

        return unique.values.sortedWith(
            compareByDescending<CandidatePath> { it.score }
                .thenByDescending { it.literal }
                .thenBy { it.bengali }
        )
    }

    private fun scoreLatticeCandidate(path: CandidatePath, key: String): Double {
        var score = path.score

        if (path.literal) score += if (key.length <= 2) 1.2 else 0.20

        if (dictionary.getPhoneticForBengali(path.bengali) != null) {
            score += if (key.length <= 2) 0.40 else 2.4
        }

        if (validator.isLoaded() && validator.isValid(path.bengali)) {
            score += minOf(1.2, validator.getFrequency(path.bengali) / 50.0)
        } else if (disambiguator.isKnownWord(path.bengali)) {
            score += 0.75
        }

        if (hasInvalidInitial(path.bengali)) score -= 2.5
        score += lowercaseV2AlignmentScore(key, path.bengali)
        return score
    }

    private fun lowercaseV2AlignmentScore(key: String, bengali: String): Double {
        var score = 0.0

        if (key == "a") {
            if (bengali == "আ") score += 8.0
            if (bengali == "অ") score += 2.0
            if (bengali == "ও") score -= 2.0
        }

        if (key == "o") {
            if (bengali == "ও") score += 8.0
            if (bengali == "অ") score += 1.0
        }

        if (key.contains("sh")) {
            score += bengali.count { it == 'শ' } * 1.4
            score += bengali.count { it == 'ষ' } * 0.8
            score -= bengali.count { it == 'স' } * 1.4
        } else if (Regex("s(?!h)").containsMatchIn(key)) {
            score += bengali.count { it == 'স' } * 1.0
            score -= bengali.count { it == 'শ' || it == 'ষ' } * 1.4
        }

        if (key.contains("sto") && bengali.contains("ষ্ট")) score += 1.6

        if (key.endsWith("be")) {
            if (bengali.endsWith("বে")) score += 1.0
            if (bengali.endsWith("োবে")) score -= 2.0
        }

        if (key.endsWith("ye")) {
            if (bengali.endsWith("য়ে") || bengali.endsWith("য়ে")) score += 1.0
            if (bengali.endsWith("োয়ে") || bengali.endsWith("োয়ে")) score -= 1.5
        }

        if (key.endsWith("oy")) {
            if (bengali.endsWith("য়") || bengali.endsWith("য়")) score += 1.0
            if (bengali.endsWith("োয়") || bengali.endsWith("োয়")) score -= 1.0
        }

        if (key.endsWith("r")) {
            if (bengali.endsWith("র")) score += 0.8
            if (bengali.endsWith("ড়") || bengali.endsWith("ঢ়")) score -= 0.8
        }

        if (!key.contains('r') && (bengali.contains("ড়") || bengali.contains("ঢ়") || bengali.contains("ঢ়"))) {
            score -= 8.0
        }

        if (Regex("[bcdfghjklmnpqrstvwxyz]o$").containsMatchIn(key)) {
            if (bengali.endsWith("ো")) score += 0.5
            if (bengali.endsWith("ও")) score -= 0.8
        }

        if (Regex("[bcdfghjklmnpqrstvwxyz][iu]$").containsMatchIn(key)) {
            if (bengali.endsWith("ি") || bengali.endsWith("ী") || bengali.endsWith("ু") || bengali.endsWith("ূ")) score += 0.7
            if (bengali.endsWith("ই") || bengali.endsWith("ঈ") || bengali.endsWith("উ") || bengali.endsWith("ঊ")) score -= 0.8
        }

        if (key.startsWith("e")) {
            if (bengali.startsWith("এ")) score += 0.7
            if (bengali.startsWith("ই")) score -= 0.7
        }

        return score
    }

    private fun lowercaseV2AlignmentPriority(key: String, bengali: String): Int =
        (lowercaseV2AlignmentScore(key, bengali) * 100).toInt()

    private fun hasInvalidInitial(bengali: String): Boolean {
        return bengali.startsWith("ড়") ||
            bengali.startsWith("ঢ়") ||
            bengali.startsWith("ণ") ||
            bengali.startsWith("ঙ") ||
            bengali.startsWith("ঞ") ||
            bengali.startsWith("ৎ")
    }

    private fun applyCandidateLatticeRanking(key: String, result: ConversionResult): ConversionResult {
        if (result.source == ResolutionSource.ENGLISH_PASSTHROUGH) return result

        val exactMatches = dictionary.lookup(key)
        val exactMatchRank = mutableMapOf<String, Pair<Int, Int>>()
        exactMatches.forEachIndexed { rank, match ->
            val previous = exactMatchRank[match.bengali]
            if (previous == null || match.frequency > previous.first) {
                exactMatchRank[match.bengali] = match.frequency to rank
            }
        }

        val lattice = generateCandidateLattice(key, 80)
            .filter { it.bengali.isNotEmpty() && it.bengali != result.bengali }
        if (lattice.isEmpty()) return result

        fun isKnown(word: String): Boolean =
            dictionary.getPhoneticForBengali(word) != null ||
                disambiguator.isKnownWord(word) ||
                (validator.isLoaded() && validator.isValid(word))

        fun candidatePriority(word: String): Int {
            val exact = exactMatchRank[word]
            if (exact != null) {
                val corpusFrequency = if (validator.isLoaded()) validator.getFrequency(word) else 0
                val alignment = lowercaseV2AlignmentPriority(key, word)
                return if (key.length < 4) {
                    1000 + corpusFrequency * 4 + exact.first - exact.second + alignment
                } else {
                    // S6: seed/extended entry frequencies are stale; the words-table
                    // frequency is corpus-refreshed (S5). Score exact matches by the
                    // stronger of the two so archaic twins (তৈরী) can't outrank the
                    // modern spelling (তৈরি@90) on legacy entry weight alone.
                    1000 + maxOf(exact.first, corpusFrequency) * 3 - exact.second + alignment
                }
            }

            if (dictionary.getPhoneticForBengali(word) != null) return 500 + lowercaseV2AlignmentPriority(key, word)

            if (validator.isLoaded() && validator.isValid(word)) {
                return validator.getFrequency(word) + lowercaseV2AlignmentPriority(key, word)
            }

            if (disambiguator.isKnownWord(word)) return 40 + lowercaseV2AlignmentPriority(key, word)

            return lowercaseV2AlignmentPriority(key, word)
        }

        val exactCandidates = exactMatches
            .map { CandidatePath(it.bengali, candidatePriority(it.bengali).toDouble(), true) }
            .filter { it.bengali != result.bengali }
        val rankedPool = (exactCandidates + lattice)
            .distinctBy { it.bengali }
        val currentIsExact = exactMatchRank.containsKey(result.bengali)
        val currentKnown = isKnown(result.bengali)
        val currentPriority = candidatePriority(result.bengali)
        val currentIsValidated = validator.isLoaded() && validator.isValid(result.bengali)
        val currentPhoneticFit = phoneticFitScore(key, result.bengali)
        val currentCorpusFrequency = if (validator.isLoaded()) validator.getFrequency(result.bengali) else 0
        val known = rankedPool.filter {
            if (currentIsExact) {
                exactMatchRank.containsKey(it.bengali)
            } else if (key.length < 4) {
                val candidateFrequency = if (validator.isLoaded()) validator.getFrequency(it.bengali) else 0
                val gap = if (key.length <= 2) 12 else 3
                isKnown(it.bengali) &&
                    validator.hasFrequencyData() &&
                    candidateFrequency > currentCorpusFrequency + gap &&
                    phoneticFitScore(key, it.bengali) >= maxOf(0.30, currentPhoneticFit - 0.05)
            } else {
                isKnown(it.bengali) &&
                    (exactMatchRank.containsKey(it.bengali) || hasConsonantAmbiguityDifference(result.bengali, it.bengali))
            }
        }
        if (known.isEmpty()) {
            val extras = lattice.take(config.maxSuggestions).map {
                Alternative(it.bengali, it.score.coerceIn(0.30, 0.86))
            }
            return result.copy(alternatives = mergeAlternatives(result.alternatives, extras, result.bengali))
        }

        val best = known.maxWithOrNull(
            compareBy<CandidatePath> { candidatePriority(it.bengali) }
                .thenBy { phoneticFitBucket(phoneticFitScore(key, it.bengali)) }
                .thenBy { it.score }
        ) ?: return result

        val bestPhoneticFit = phoneticFitScore(key, best.bengali)
        val bestCorpusFrequency = if (validator.isLoaded()) validator.getFrequency(best.bengali) else 0
        val shortCorpusPromotion = key.length < 4 &&
            validator.hasFrequencyData() &&
            bestCorpusFrequency > currentCorpusFrequency + (if (key.length <= 2) 12 else 3) &&
            bestPhoneticFit >= maxOf(0.30, currentPhoneticFit - 0.05)
        if (
            !shortCorpusPromotion &&
            !currentIsExact &&
            currentIsValidated &&
            !exactMatchRank.containsKey(best.bengali) &&
            bestPhoneticFit < currentPhoneticFit + 0.12
        ) {
            val extras = lattice.take(config.maxSuggestions + 2).map {
                Alternative(it.bengali, it.score.coerceIn(0.30, 0.90))
            }
            return result.copy(alternatives = mergeAlternatives(result.alternatives, extras, result.bengali))
        }

        val shouldPromote = !currentKnown ||
            shortCorpusPromotion ||
            candidatePriority(best.bengali) > currentPriority + 1 ||
            bestPhoneticFit >= currentPhoneticFit + 0.12

        val extras = lattice.take(config.maxSuggestions + 2).map {
            Alternative(it.bengali, it.score.coerceIn(0.30, 0.90))
        }

        if (!shouldPromote) {
            return result.copy(alternatives = mergeAlternatives(result.alternatives, extras, result.bengali))
        }

        val oldPrimary = Alternative(result.bengali, result.confidence.coerceAtMost(0.86))
        return result.copy(
            bengali = best.bengali,
            confidence = maxOf(result.confidence, best.score.coerceIn(0.60, 0.96)),
            source = ResolutionSource.DICTIONARY,
            alternatives = mergeAlternatives(listOf(oldPrimary) + result.alternatives, extras, best.bengali)
        )
    }

    private fun shouldApplyEarlyCandidateLattice(key: String): Boolean {
        return key.contains("oi") || key.contains("oy")
    }

    private data class ProductiveSuffix(val phonetic: String, val bengali: String)

    private fun tryProductiveSuffixConversion(key: String): ConversionResult? {
        val suffixes = listOf(
            ProductiveSuffix("take", "টাকে"),
            ProductiveSuffix("tate", "টাতে"),
            ProductiveSuffix("tao", "টাও"),
            ProductiveSuffix("tar", "টার"),
            ProductiveSuffix("tai", "টাই"),
            ProductiveSuffix("tei", "তেই"),
            ProductiveSuffix("tuku", "টুকু")
        )

        for (suffix in suffixes) {
            if (!key.endsWith(suffix.phonetic) || key.length <= suffix.phonetic.length) continue

            val stemKey = key.dropLast(suffix.phonetic.length)
            val stemResult = convertByDictionary(stemKey) ?: convertByPatterns(stemKey)
            var stem = stemResult.bengali
            if (stem.isEmpty()) continue

            if (stem.endsWith("ো") && suffix.bengali.startsWith("ট")) {
                stem = stem.dropLast(1)
            }

            val bengali = stem + suffix.bengali
            val literal = convertByPatterns(key)
            val alternatives = (listOf(Alternative(literal.bengali, literal.confidence.coerceAtMost(0.82))) + literal.alternatives)
                .filter { it.bengali != bengali }

            return ConversionResult(
                bengali = bengali,
                confidence = 0.91,
                source = ResolutionSource.RULE,
                alternatives = alternatives
            )
        }

        return null
    }

    private fun phoneticFitBucket(score: Double): Int = (score * 100).toInt()

    private fun phoneticFitScore(key: String, bengali: String): Double {
        if (key.isEmpty() || bengali.isEmpty()) return 0.0

        val dictionaryPhonetic = dictionary.getPhoneticForBengali(bengali)
        val reversePhonetic = ReverseTransliterator.reverseWord(bengali)
        val dictionaryScore = dictionaryPhonetic?.let { PhoneticOverlapScorer.score(key, it).score } ?: 0.0
        val reverseScore = if (reversePhonetic.isNotEmpty()) PhoneticOverlapScorer.score(key, reversePhonetic).score else 0.0
        val best = maxOf(dictionaryScore, reverseScore)
        return if (reversePhonetic.isNotEmpty() && !hasCompatibleVowelPath(key, reversePhonetic)) {
            best * 0.55
        } else {
            best
        }
    }

    private fun mergeAlternatives(
        existing: List<Alternative>,
        extras: List<Alternative>,
        primary: String
    ): List<Alternative> {
        val seen = mutableSetOf(primary)
        val merged = mutableListOf<Alternative>()
        for (alt in existing + extras) {
            if (alt.bengali.isNotEmpty() && seen.add(alt.bengali)) {
                merged.add(alt)
            }
        }
        return merged.take(config.maxSuggestions - 1)
    }

    private fun hasConsonantAmbiguityDifference(left: String, right: String): Boolean {
        if (left == right) return false
        val pairs = listOf(
            "ত" to "ট", "থ" to "ঠ",
            "দ" to "ড", "ধ" to "ঢ",
            "ন" to "ণ", "ং" to "ঙ",
            "স" to "শ", "স" to "ষ", "শ" to "ষ",
            "র" to "ড়", "র" to "ঢ়",
            "জ" to "য", "চ" to "ছ"
        )
        return pairs.any { (a, b) ->
            left.contains(a) && right.contains(b) || left.contains(b) && right.contains(a)
        }
    }

    internal fun generateDiphthongAlternatives(bengali: String): List<Alternative> {
        val alts = mutableListOf<Alternative>()

        if (bengali.contains('ৈ')) {
            val split = bengali.replace('ৈ', 'ই')
            if (split != bengali) {
                val isKnown = disambiguator.isKnownWord(split)
                alts.add(Alternative(split, if (isKnown) 0.92 else 0.60))
            }
        }

        if (bengali.contains('ৌ')) {
            val split = bengali.replace('ৌ', 'উ')
            if (split != bengali) {
                val isKnown = disambiguator.isKnownWord(split)
                alts.add(Alternative(split, if (isKnown) 0.92 else 0.55))
            }
        }

        if (bengali.contains('ই') && bengali.length >= 2) {
            val idx = bengali.indexOf('ই')
            if (idx > 0) {
                val prev = bengali[idx - 1].code
                val isBengaliConsonant = (prev in 0x0995..0x09A8) || (prev in 0x09AA..0x09B9)
                if (isBengaliConsonant) {
                    val diphthong = bengali.substring(0, idx) + "ৈ" + bengali.substring(idx + 1)
                    if (disambiguator.isKnownWord(diphthong)) {
                        alts.add(Alternative(diphthong, 0.85))
                    }
                }
            }
        }

        return alts
    }

    internal fun generateInitialVowelAlternatives(bengali: String): List<Alternative> {
        val alts = mutableListOf<Alternative>()

        when {
            bengali.startsWith("অ") -> {
                val oVersion = "ও" + bengali.substring(1)
                val isKnown = disambiguator.isKnownWord(oVersion)
                alts.add(Alternative(oVersion, if (isKnown) 0.90 else 0.55))
            }
            bengali.startsWith("ও") -> {
                val aVersion = "অ" + bengali.substring(1)
                val isKnown = disambiguator.isKnownWord(aVersion)
                alts.add(Alternative(aVersion, if (isKnown) 0.90 else 0.55))
            }
            bengali.startsWith("আ") -> {
                val oVersion = "অ" + bengali.substring(1)
                if (disambiguator.isKnownWord(oVersion)) {
                    alts.add(Alternative(oVersion, 0.88))
                }
            }
        }

        return alts
    }

    internal fun generateAmbiguousCharAlternatives(primary: String): List<Alternative> {
        if (primary.length < 2) return emptyList()

        val seen = mutableSetOf(primary)
        val alts = mutableListOf<Alternative>()

        val directSwaps = listOf(
            "ত" to listOf("ট"), "ট" to listOf("ত"),
            "থ" to listOf("ঠ"), "ঠ" to listOf("থ"),
            "দ" to listOf("ড"), "ড" to listOf("দ"),
            "ধ" to listOf("ঢ"), "ঢ" to listOf("ধ"),
            "ন" to listOf("ণ"), "ণ" to listOf("ন"),
            "শ" to listOf("ষ"), "ষ" to listOf("শ"),
            "জ" to listOf("য"), "য" to listOf("জ"),
            "চ" to listOf("ছ"), "ছ" to listOf("চ"),
            "ই" to listOf("য়", "ৈ"), "য়" to listOf("ই", "ৈ"), "ৈ" to listOf("ই", "য়")
        )
        val directCandidates = mutableListOf<String>()
        fun addDirectCandidate(candidate: String, from: String) {
            if (seen.add(candidate)) {
                val isKnown = disambiguator.isKnownWord(candidate)
                val confidence = when {
                    isKnown -> 0.98
                    from == "র" || from == "ড়" || from == "ঢ়" -> 0.89
                    else -> 0.97
                }
                alts.add(Alternative(candidate, confidence))
                directCandidates.add(candidate)
            }
        }
        for ((from, replacements) in directSwaps) {
            var searchFrom = 0
            while (searchFrom <= primary.length - from.length) {
                val idx = primary.indexOf(from, searchFrom)
                if (idx == -1) break
                for (replacement in replacements) {
                    val candidate = primary.substring(0, idx) + replacement + primary.substring(idx + from.length)
                    addDirectCandidate(candidate, from)
                }
                searchFrom = idx + from.length
            }
        }
        for (base in directCandidates.toList()) {
            for ((from, replacements) in directSwaps) {
                if (from == "র" || from == "ড়" || from == "ঢ়") continue
                var searchFrom = 0
                while (searchFrom <= base.length - from.length) {
                    val idx = base.indexOf(from, searchFrom)
                    if (idx == -1) break
                    for (replacement in replacements) {
                        addDirectCandidate(base.substring(0, idx) + replacement + base.substring(idx + from.length), from)
                    }
                    searchFrom = idx + from.length
                }
            }
        }

        val candidates = disambiguator.generateCandidates(primary)
        for (candidate in candidates) {
            if (!seen.add(candidate)) continue
            val isKnown = disambiguator.isKnownWord(candidate)
            alts.add(Alternative(candidate, if (isKnown) 0.95 else 0.91))
        }

        return alts.sortedByDescending { it.confidence }.take(32)
    }

    /**
     * Smart "o" suppression: between consonants, 'o' is the inherent vowel.
     * Bengali consonants already carry an inherent অ/ও sound, so typing
     * "bol" means ব+ল (= বল), NOT ব+ো+ল (= বোল).
     * Only add ো-কার when 'o' is at word end or before another vowel.
     *
     * Web parity: SmartEngine.ts lines 2927-2953
     *
     * @param key Full phonetic input
     * @param i Current position (should be pointing at 'o')
     * @return true if the 'o' should be suppressed (inherent vowel)
     */
    private fun shouldSuppressO(key: String, i: Int): Boolean {
        if (key[i] != 'o') return false
        val nextIdx = i + 1
        if (nextIdx >= key.length) return false  // 'o' at word end → don't suppress, add ো
        val nextChar = key[nextIdx]
        // Check if next char is a consonant letter (not a vowel)
        val isConsonantLetter = nextChar in 'a'..'z' && nextChar !in "aeiou"
        if (!isConsonantLetter) return false  // 'o' before vowel → don't suppress
        // Special case: 'o' before 'y'
        // moy → ময় (skip o), but moye → মোয়ে (keep o, because y+vowel needs ো)
        if (nextChar == 'y') {
            val afterY = if (nextIdx + 1 < key.length) key[nextIdx + 1] else ' '
            // o before trailing y (end or y+consonant) → suppress (inherent vowel)
            // o before y+vowel (moye) → DON'T suppress, let it add ো
            return afterY == ' ' || afterY !in "aeiou"
        }
        // 'o' before any other consonant → suppress (inherent vowel)
        return true
    }

    /**
     * Resolve a vowel at position i in the phonetic input.
     *
     * Web parity: independent 'o' has three modes:
     * 1. Word-initial + before consonant → অ (অনেক, অবশ্য)
     * 2. Word-initial + standalone → ও (conjunction)
     * 3. Mid-word independent (after non-consonant) → ও (ওকার independent)
     *
     * @param key Full phonetic input
     * @param i Current position
     * @param isIndependent True if vowel is NOT after a Bengali consonant
     * @param isWordInitial True if output is empty (word-start position)
     * @return Triple of (Bengali vowel string, chars consumed, confidence)
     */
    private fun resolveVowel(
        key: String,
        i: Int,
        isIndependent: Boolean,
        isWordInitial: Boolean = false
    ): Triple<String, Int, Double> {
        // Check for compound vowels first (longest match)
        if (i + 1 < key.length) {
            val twoChar = key.substring(i, minOf(i + 2, key.length))
            when (twoChar) {
                "ou" -> return if (isIndependent) Triple("ঔ", 2, 0.90) else Triple("ৌ", 2, 0.90)
                "oi" -> return if (isIndependent) Triple("ঐ", 2, 0.90) else Triple("ৈ", 2, 0.90)
                "oo" -> return if (isIndependent) Triple("ঊ", 2, 0.85) else Triple("ূ", 2, 0.85)
                "uu" -> return if (isIndependent) Triple("ঊ", 2, 0.85) else Triple("ূ", 2, 0.85)
                "ee" -> return if (isIndependent) Triple("ঈ", 2, 0.85) else Triple("ী", 2, 0.85)
                "ii" -> return if (isIndependent) Triple("ঈ", 2, 0.85) else Triple("ী", 2, 0.85)
                "aa" -> return if (isIndependent) Triple("আ", 2, 0.90) else Triple("া", 2, 0.90)
            }
        }
        // Single vowels
        return when (key[i]) {
            'a' -> if (isIndependent) Triple("আ", 1, 0.85) else Triple("া", 1, 0.85)
            'i' -> if (isIndependent) Triple("ই", 1, 0.85) else Triple("ি", 1, 0.85)
            'u' -> if (isIndependent) Triple("উ", 1, 0.90) else Triple("ু", 1, 0.90)
            'e' -> if (isIndependent) Triple("এ", 1, 0.90) else Triple("ে", 1, 0.90)
            'o' -> if (!isIndependent) {
                Triple("ো", 1, 0.85) // Dependent form (after consonant)
            } else if (isWordInitial) {
                // Word-initial 'o': অ before consonant, ও standalone/before vowel
                val nextIdx = i + 1
                val nextChar = if (nextIdx < key.length) key[nextIdx] else ' '
                val isFollowedByConsonant = nextChar in 'a'..'z' && nextChar !in "aeiou"
                if (isFollowedByConsonant) {
                    Triple("অ", 1, 0.75) // অনেক, অবশ্য
                } else {
                    Triple("ও", 1, 0.85) // standalone ও
                }
            } else {
                Triple("ও", 1, 0.85) // Mid-word independent: ও
            }
            else -> Triple(key[i].toString(), 1, 0.50)
        }
    }

    /**
     * Resolve a consonant at position i in the phonetic input.
     * Uses context-aware rules for n (NatvaVidhan), sh (ShatvaVidhan), ng (NasalResolver).
     *
     * @param key Full phonetic input
     * @param i Current position
     * @param bengaliContext Bengali text generated so far (for context-aware rules)
     * @return Triple of (Bengali consonant string, chars consumed, confidence)
     */
    // Consonants that can take ya-phala (্য)
    private val yPhalaConsonants = mapOf(
        'b' to "ব", 'k' to "ক", 'g' to "গ", 't' to "ত", 'd' to "দ",
        'n' to "ন", 'm' to "ম", 'j' to "জ", 'p' to "প", 'l' to "ল",
        'h' to "হ", 'v' to "ভ", 's' to "স", 'r' to "র"
    )

    // Consonants that can take ra-phala (্র)
    private val rPhalaConsonants = mapOf(
        'k' to "ক", 'g' to "গ", 't' to "ত", 'd' to "দ",
        'n' to "ন", 'm' to "ম", 'p' to "প", 'b' to "ব",
        'v' to "ভ", 's' to "স", 'j' to "জ", 'l' to "ল",
        'h' to "হ"
    )

    private val consonantMap = mapOf(
        'k' to "ক", 'g' to "গ", 'c' to "ছ", 'j' to "জ",
        't' to "ত", 'd' to "দ", 'p' to "প", 'b' to "ব",
        'f' to "ফ", 'm' to "ম", 'r' to "র", 'l' to "ল",
        's' to "স", 'h' to "হ", 'v' to "ভ", 'w' to "ও",
        'y' to "য়", 'z' to "য", 'q' to "ক", 'x' to "ক্স"
    )

    private fun resolveConsonant(key: String, i: Int, bengaliContext: String): Triple<String, Int, Double> {
        val ch = key[i]

        // 'ng' handling
        if (ch == 'n' && i + 1 < key.length && key[i + 1] == 'g') {
            val nextAfterNg = if (i + 2 < key.length) key[i + 2].toString() else null
            val nasal = NasalResolver.resolve(nextAfterNg)
            return Triple(nasal.toString(), 2, 0.90)
        }

        // 'sh' handling
        if (ch == 's' && i + 1 < key.length && key[i + 1] == 'h') {
            val resolution = ShatvaVidhan.resolve(bengaliContext, key, i)
            return Triple(resolution.bengali.toString(), 2, resolution.confidence)
        }

        // ৃ-কার: consonant + "ri" → consonant + ৃ (when followed by consonant or end)
        if (rPhalaConsonants.containsKey(ch) && i + 2 < key.length && key[i + 1] == 'r' && key[i + 2] == 'i') {
            val afterRI = if (i + 3 < key.length) key[i + 3] else ' '
            if (afterRI !in "aeiou") {
                // consonant + ri + consonant/end → ৃ (কৃষক, সৃষ্টি, বৃক্ষ)
                return Triple(rPhalaConsonants[ch]!! + "ৃ", 3, 0.90)
            }
        }

        // র-ফলা: consonant + "r" → consonant + ্র (প্র, ত্র, ক্র, etc.)
        if (rPhalaConsonants.containsKey(ch) && i + 1 < key.length && key[i + 1] == 'r') {
            val afterR = if (i + 2 < key.length) key[i + 2] else ' '
            if (afterR != 'r') { // Avoid 'rr'
                return Triple(rPhalaConsonants[ch]!! + "্র", 2, 0.85)
            }
        }

        // য-ফলা: consonant + "y" → consonant + ্য (ত্য, ব্য, জ্ঞ, etc.)
        if (yPhalaConsonants.containsKey(ch) && i + 1 < key.length && key[i + 1] == 'y') {
            val afterY = if (i + 2 < key.length) key[i + 2] else ' '
            if (afterY != 'y') { // Avoid 'yy'
                val conf = if (ch in "td") 0.80 else 0.85
                return Triple(yPhalaConsonants[ch]!! + "্য", 2, conf)
            }
        }

        // ৃ-কার / ঋ: standalone "ri" handling (web parity: SmartEngine.ts lines 2844-2866)
        // After consonant output: "ri" + consonant/end → ৃ
        // After non-consonant (word-initial or after vowel): "ri" + consonant/end → ঋ
        if (ch == 'r' && i + 1 < key.length && key[i + 1] == 'i') {
            val afterRI = if (i + 2 < key.length) key[i + 2] else ' '
            val bengaliEndsWithConsonant = endsWithBengaliConsonant(bengaliContext)
            if (bengaliEndsWithConsonant) {
                if (afterRI !in "aeiou" && afterRI != 'r') {
                    return Triple("ৃ", 2, 0.90) // After consonant: ri → ৃ (কৃষক, সৃষ্টি)
                } else {
                    return Triple("্রি", 2, 0.85) // After consonant + ri + vowel → ্রি (ক্রিকেট)
                }
            } else if (bengaliContext.isEmpty() || !bengaliEndsWithConsonant) {
                if (afterRI !in "aeiou" && afterRI != 'r') {
                    return Triple("ঋ", 2, 0.85) // Word-initial or after vowel: ri → ঋ (ঋতু, ঋণ)
                }
            }
        }

        // Reph: "r" + consonant → র্ (রেফ) when 'r' is followed by a non-vowel non-r non-h
        if (ch == 'r' && i + 1 < key.length) {
            val nextCh = key[i + 1]
            if (nextCh !in "aeiour" && nextCh != 'h') {
                return Triple("র্", 1, 0.85) // Only consume 'r', next consonant processed next iteration
            }
        }

        // 'n' (not ng) handling — NatvaVidhan
        if (ch == 'n' && (i + 1 >= key.length || key[i + 1] != 'g')) {
            val resolution = NatvaVidhan.resolve(bengaliContext)
            return Triple(resolution.bengali.toString(), 1, resolution.confidence)
        }

        val bengali = consonantMap[ch] ?: ch.toString()
        val defaultConf = StatisticalDefaults.getDefault(ch.toString())?.confidence ?: 0.80
        return Triple(bengali, 1, defaultConf)
    }

    /**
     * Layer 5: Apply AI disambiguation using character swap rules.
     *
     * Web parity: Enforce consonant rules — reject AI disambiguation results
     * that violate phonetic→Bengali consonant mapping rules:
     * - 'sh' input → result must NOT start with স (needs শ/ষ)
     * - 's' (not 'sh') → result must NOT start with শ (needs স)
     * - 'z' input → result must NOT start with জ (needs য)
     * - 'j' (not 'jh') → result must NOT start with য (needs জ)
     */
    private fun applyDisambiguation(result: ConversionResult, key: String = ""): ConversionResult {
        val disambiguated = disambiguator.disambiguate(result.bengali, result.confidence) ?: return result

        // Enforce consonant rules: reject AI disambiguation that violates mapping rules
        val aiViolatesRules = disambiguated.improved && (
            (key.startsWith("z") && disambiguated.bengali.startsWith("জ")) ||
            (key.startsWith("s") && !key.startsWith("sh") && disambiguated.bengali.startsWith("শ")) ||
            (key.startsWith("sh") && disambiguated.bengali.startsWith("স")) ||
            (key.startsWith("j") && !key.startsWith("jh") && disambiguated.bengali.startsWith("য"))
        )
        if (aiViolatesRules) return result

        return result.copy(
            bengali = disambiguated.bengali,
            confidence = disambiguated.confidence,
            alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
        )
    }

    /**
     * Layer 5.5: Apply dictionary validation with systematic character swaps.
     */
    private fun applyDictionaryValidation(result: ConversionResult): ConversionResult {
        // Try disambiguation map first (O(1))
        disambiguationMap?.get(result.bengali)?.let { correct ->
            if (validator.isValid(correct)) {
                return result.copy(
                    bengali = correct, confidence = 0.95,
                    alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                )
            }
        }

        // V2-style candidate arbitration: some Bangla alternatives are both valid
        // dictionary words (ন/ণ, শ/ষ). Do not wait until the current output is
        // invalid; score the competing form and let frequency/rules pick the better
        // candidate for the editor while preserving the old form as an alternative.
        val scoredSwap = applyScoredAmbiguousSwaps(result)
        if (scoredSwap.bengali != result.bengali) {
            return scoredSwap
        }

        // Try systematic swaps
        // ন↔ণ and শ↔ষ use per-position scoring via DisambiguationScorer when both forms are valid.
        // All other swap pairs use the original simple "only swap if current is invalid" logic.
        if (!validator.isValid(result.bengali)) {
            var improved = result.bengali

            // ── Fix 1: Strip trailing ো if dictionary validates without it ──────────
            if (improved.endsWith("ো")) {
                val withoutOkar = improved.dropLast(1)
                if (validator.isValid(withoutOkar) && !validator.isValid(improved)) {
                    improved = withoutOkar
                }
            }

            // ── ন→ণ (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'ন') {
                    val candidate = improved.substring(0, idx) + "ণ" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.N_NN,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── ণ→ন (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'ণ') {
                    val candidate = improved.substring(0, idx) + "ন" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.N_NN,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── শ→ষ (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'শ') {
                    val candidate = improved.substring(0, idx) + "ষ" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.SH_SS,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── ষ→শ (per-position with scorer) ──────────────────────────────────────
            for (idx in improved.indices) {
                if (improved[idx] == 'ষ') {
                    val candidate = improved.substring(0, idx) + "শ" + improved.substring(idx + 1)
                    if (candidate.contains("-")) continue
                    val candidateValid = validator.isValid(candidate)
                    val currentValid = validator.isValid(improved)
                    if (candidateValid && !currentValid) {
                        improved = candidate
                    } else if (candidateValid && currentValid) {
                        val scorerResult = DisambiguationScorer.score(
                            current = improved,
                            candidate = candidate,
                            swapIndex = idx,
                            swapType = SwapType.SH_SS,
                            frequency = DisambiguationScorer.FrequencyPair(
                                current = validator.getFrequency(improved),
                                candidate = validator.getFrequency(candidate)
                            )
                        )
                        if (scorerResult.recommendation == "candidate") {
                            improved = candidate
                        }
                    }
                }
            }

            // ── Fix 6: ং→ঙ before velar consonants ক/খ/গ/ঘ ─────────────────────
            if (improved.contains('ং')) {
                for (idx in improved.indices) {
                    if (improved[idx] == 'ং' && idx + 1 < improved.length) {
                        val next = improved[idx + 1]
                        // ঙ is expected before ক/খ/গ/ঘ (velar consonants)
                        val isBeforeVelar = next in "কখগঘ" ||
                            (next == '্' && idx + 2 < improved.length && improved[idx + 2] in "কখগঘ")
                        if (isBeforeVelar) {
                            val candidate = improved.substring(0, idx) + "ঙ" + improved.substring(idx + 1)
                            if (validator.isValid(candidate)) {
                                improved = candidate
                                break
                            }
                        }
                    }
                }
            }

            // ── Fix 7: র→ড় swap (last occurrence) ─────────────────────────────────
            if (improved.contains('র') && !validator.isValid(improved)) {
                val lastR = improved.lastIndexOf('র')
                if (lastR >= 0) {
                    val candidate = improved.substring(0, lastR) + "ড়" + improved.substring(lastR + 1)
                    if (validator.isValid(candidate)) {
                        improved = candidate
                    }
                }
            }

            // ── Fix 8: ট্র→ত্র conjunct swap ──────────────────────────────────────
            if (improved.contains("ট্র") && !validator.isValid(improved)) {
                val candidate = improved.replace("ট্র", "ত্র")
                if (validator.isValid(candidate)) {
                    improved = candidate
                }
            }

            // ── Fix 9: আ↔অ at word start ──────────────────────────────────────────
            if (improved.startsWith("আ") && !validator.isValid(improved)) {
                val candidate = "অ" + improved.substring(1)
                if (validator.isValid(candidate)) {
                    improved = candidate
                }
            }
            if (improved.startsWith("অ") && !validator.isValid(improved)) {
                val candidate = "আ" + improved.substring(1)
                if (validator.isValid(candidate)) {
                    improved = candidate
                }
            }

            // Early return if any of the scored swaps fixed the word
            if (validator.isValid(improved) && improved != result.bengali) {
                return result.copy(
                    bengali = improved, confidence = 0.90,
                    alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                )
            }

            // ── Remaining swaps (simple: only apply when current is invalid) ─────────
            val simpleSwaps = listOf(
                "স" to "ষ", "ষ" to "স",
                "ি" to "ী", "ী" to "ি",
                "ু" to "ূ", "ূ" to "ু",
                "চ" to "ছ", "ছ" to "চ",
                "ত" to "ট", "ট" to "ত",
                "দ" to "ড", "ড" to "দ"
            )

            for ((from, to) in simpleSwaps) {
                if (result.bengali.contains(from)) {
                    val candidate = result.bengali.replace(from, to)
                    // Reject hyphenated candidates (garbage from 480K dictionary)
                    if (validator.isValid(candidate) && !candidate.contains("-")) {
                        return result.copy(
                            bengali = candidate, confidence = 0.90,
                            alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                        )
                    }
                }
            }
        }

        return result
    }

    private fun applyScoredAmbiguousSwaps(result: ConversionResult): ConversionResult {
        if (!validator.isLoaded()) return result

        var improved = result.bengali
        var changed = false

        fun trySwap(idx: Int, replacement: Char, swapType: SwapType) {
            if (idx !in improved.indices) return
            val candidate = improved.substring(0, idx) + replacement + improved.substring(idx + 1)
            if (candidate == improved || candidate.contains("-")) return
            if (!validator.isValid(candidate)) return

            val currentValid = validator.isValid(improved)
            if (!currentValid) {
                improved = candidate
                changed = true
                return
            }

            val scorerResult = DisambiguationScorer.score(
                current = improved,
                candidate = candidate,
                swapIndex = idx,
                swapType = swapType,
                frequency = DisambiguationScorer.FrequencyPair(
                    current = validator.getFrequency(improved),
                    candidate = validator.getFrequency(candidate)
                )
            )
            if (scorerResult.recommendation == "candidate") {
                improved = candidate
                changed = true
            }
        }

        for (idx in improved.indices) {
            when (improved[idx]) {
                'ন' -> trySwap(idx, 'ণ', SwapType.N_NN)
                'ণ' -> trySwap(idx, 'ন', SwapType.N_NN)
            }
        }

        for (idx in improved.indices) {
            when (improved[idx]) {
                'শ' -> trySwap(idx, 'ষ', SwapType.SH_SS)
                'ষ' -> trySwap(idx, 'শ', SwapType.SH_SS)
            }
        }

        return if (changed && improved != result.bengali && validator.isValid(improved)) {
            result.copy(
                bengali = improved,
                confidence = maxOf(result.confidence, 0.90),
                alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
            )
        } else {
            result
        }
    }

    /**
     * Layer 5.7: Conjunct removal recovery.
     * When the pattern engine produces an invalid word with hasanta (্),
     * try removing hasantas one at a time or all at once to find a valid word.
     */
    private fun applyConjunctRemovalRecovery(result: ConversionResult): ConversionResult {
        if (!validator.isLoaded()) return result
        if (validator.isValid(result.bengali)) return result  // Already valid, keep it
        if (!result.bengali.contains("্")) return result       // No hasanta, nothing to remove

        // Try removing each hasanta one at a time
        for (i in result.bengali.indices) {
            if (result.bengali[i] == '্') {
                val without = result.bengali.removeRange(i, i + 1)
                if (validator.isValid(without)) {
                    return result.copy(
                        bengali = without,
                        confidence = 0.92,
                        alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
                    )
                }
            }
        }

        // Try removing ALL hasantas at once
        val allRemoved = result.bengali.replace("্", "")
        if (validator.isValid(allRemoved)) {
            return result.copy(
                bengali = allRemoved,
                confidence = 0.90,
                alternatives = result.alternatives + Alternative(result.bengali, result.confidence)
            )
        }

        return result
    }

    /**
     * Layer 6: Bengali dictionary recovery.
     * Searches 480K dictionary by progressively shorter Bengali prefixes,
     * scores candidates by Bengali character similarity (LCS with normalization).
     *
     * For longer inputs, similarity matters MORE and frequency matters LESS
     * (user typed enough characters to disambiguate — trust the input).
     */
    /**
     * Word-membership check backing the commit gate. Full mode (484K validator
     * loaded): RAM-speed validator lookup ONLY — the sqlite store is never
     * consulted, so full-mode latency is unchanged. Lite mode (validator never
     * loaded, sqlite store attached): one indexed words-table query per word,
     * memoized in [containsWordMemo].
     */
    private fun isKnownWord(bengali: String): Boolean {
        if (validator.isValid(bengali)) return true
        if (validator.isLoaded()) return false
        val store = phoneticIndex ?: return false
        // The compiled words table stores the nukta-FOLDED form only (S2);
        // rule-layer candidates may arrive decomposed — fold before querying.
        val folded = ReverseTransliterator.foldNukta(bengali)
        containsWordMemo[folded]?.let { return it }
        val known = store.containsWord(folded)
        containsWordMemo[folded] = known
        return known
    }

    /** Engine v3 commit gate (spec 3.3): is this result allowed as editor primary? */
    private fun isGateApproved(result: ConversionResult): Boolean = when {
        result.bengali.isEmpty() -> true
        result.source == ResolutionSource.ENGLISH_PASSTHROUGH -> true
        result.source == ResolutionSource.ENGLISH_LEXICON -> true
        result.source == ResolutionSource.CLEAN_TRANSLITERATION -> true
        isKnownWord(result.bengali) -> true
        dictionary.containsBengali(result.bengali) -> true   // seed + learned/user words
        else -> false
    }

    /**
     * Engine v3 commit gate: the editor primary must ALWAYS be a real dictionary
     * word, an English-lexicon entry, a user/seed word, or the clean deterministic
     * transliteration — never an ambiguity-swapped or invented Bengali string.
     * Armed when the 484K validator is loaded (full mode) OR the sqlite phonetic
     * index store is attached (lite mode — word membership comes from the on-disk
     * words table via [isKnownWord]). Seed-only engines (JVM unit tests, pre-init)
     * keep legacy pattern-tail behavior.
     */
    private fun applyCommitGate(key: String, result: ConversionResult): ConversionResult {
        val canValidate = validator.isLoaded() || phoneticIndex != null
        if (!canValidate) return result                   // gate armed only with real dictionary
        // CleanTransliterator contract: digits/punctuation must be handled by the
        // caller. Tokens with non-letter characters (e.g. "123", "k,,") are never
        // ambiguity-swapped Bengali inventions — keep the pattern-engine output.
        if (key.any { it !in 'a'..'z' }) return result
        // S10 fragment sanity: validation/disambiguation layers swap the pattern
        // output to a nearby REAL word that does not own the typed key at all
        // (poriko → পৃথক while the user types পরিকল্পনা). Being a dictionary
        // word is not enough to pass the gate when canonical continuations
        // prove the input is a mid-word fragment — floor to clean output.
        if (isGateApproved(result) && !isUnfaithfulFragmentMatch(key, result)) return result
        val clean = CleanTransliterator.transliterate(key)
        val dictionaryClosedAlternatives = result.alternatives
            .filter { isKnownWord(it.bengali) || dictionary.containsBengali(it.bengali) }
            .take(3)
        return ConversionResult(
            bengali = clean,
            confidence = 0.60,
            source = ResolutionSource.CLEAN_TRANSLITERATION,
            alternatives = dictionaryClosedAlternatives
        )
    }

    /**
     * Bengali surface forms a composition layer is allowed to attach to a real
     * dictionary root. Union of [productiveSuffixes] (incl. verb endings),
     * [tryProductiveSuffixConversion]'s টা-compound table, [inflectionalSuffixes],
     * and the dependent vowel signs used by root-decomposition cases 2-4.
     */
    private val approvedCompositionSuffixes: List<String> by lazy {
        (productiveSuffixes.values +
            listOf("টাকে", "টাতে", "টাও", "টার", "টাই", "তেই", "টুকু") +
            inflectionalSuffixes.map { it.bengali } +
            listOf("া", "ি", "ী", "ু", "ূ", "ে", "ৈ", "ো", "ৌ"))
            .distinct()
            .sortedByDescending { it.length }
    }

    /**
     * Composition allowance: true when [bengali] parses as a real/seed dictionary
     * root plus a whitelisted productive suffix (ট্রাম্প + ের -> ট্রাম্পের).
     * Productive inflections of real words are legitimate Bengali even when the
     * inflected surface form is missing from the 480K list — a blanket gate would
     * wrongly floor them (ট্রাম্পের, ফারসের, সোশ্যালে are corpus-correct but absent
     * from the validator). Compositions whose root is NOT a real word
     * (স্রমদক্ষ + টার) stay unapproved and get floored.
     */
    private fun isApprovedComposition(bengali: String): Boolean {
        // S1/D3: a composed surface containing an invalid trailing-য় junction is
        // never an approved composition, even when it parses as real-root +
        // whitelisted-suffix. Aliased stem keys drop the root's trailing য়
        // (tritiyo → তৃতীয়), so suffix layers re-append it: তৃতীয়+য় = তৃতীয়য়,
        // আওতায়+্+য় = আওতায়্য়. Neither য়য় nor ্য় occurs in real Bengali
        // (0 attested ্য় forms in the 484K words table; য়-conjuncts use য-ফলা).
        if (hasInventedYaJunction(bengali)) return false
        for (suffix in approvedCompositionSuffixes) {
            if (bengali.length <= suffix.length || !bengali.endsWith(suffix)) continue
            // Hasanta-junction compositions (root + ্ + suffix) reduce to the root.
            val root = bengali.dropLast(suffix.length).removeSuffix("্")
            if (root.isEmpty()) continue
            if (isKnownWord(root) || dictionary.containsBengali(root)) return true
        }
        return false
    }

    /**
     * S1/D3 junction validity: true when [bengali] contains a trailing-য়
     * surface that real Bengali never produces — a duplicated য়য় or an
     * explicit hasanta+য় conjunct (্য়; য় never conjuncts, য-ফলা is ্য).
     * Handles both encodings of য়: precomposed U+09DF and য (U+09AF) + nukta
     * (U+09BC). Only invented compositions/recoveries build these strings.
     */
    private fun hasInventedYaJunction(bengali: String): Boolean {
        val normalized = bengali.replace("\u09DF", "\u09AF\u09BC") // precomposed য় -> য + nukta
        return normalized.contains("\u09AF\u09BC\u09AF\u09BC") || // য়য়
            normalized.contains("\u09CD\u09AF\u09BC")              // ্য়
    }

    /**
     * Commit gate for composition-layer outputs (productive-suffix gluing,
     * suffix-stripped lookup, root decomposition, section narrowing). Same
     * contract as [applyCommitGate] with one extra approval: a dictionary-root +
     * whitelisted-productive-suffix composition may stand as editor primary even
     * when the full inflection is not itself a 480K/seed word. Everything else
     * composed (invented root, non-productive junction) floors to the clean
     * deterministic transliteration.
     */
    private fun applyCompositionCommitGate(key: String, result: ConversionResult): ConversionResult {
        val canValidate = validator.isLoaded() || phoneticIndex != null
        if (!canValidate) return result                   // gate armed only with real dictionary
        if (key.any { it !in 'a'..'z' }) return result    // CleanTransliterator contract (see applyCommitGate)
        // S10 fragment sanity: composition/section/root layers stretch mid-word
        // fragments to unrelated REAL words (poriko → পৃথক while typing
        // পরিকল্পনা) — being a dictionary word is not enough; the word must own
        // the typed key when canonical continuations prove this is a fragment.
        if (isUnfaithfulFragmentMatch(key, result)) return applyCommitGate(key, result)
        if (isGateApproved(result)) return result
        if (isApprovedComposition(result.bengali)) return result
        return applyCommitGate(key, result)
    }

    /**
     * Test seam: true when [bengali] is gate-approved — i.e., present in the 480K
     * validator (full mode), the sqlite store's words table (lite mode), OR the
     * seed/user dictionary. Pins the real contract that every editor primary and
     * every surfaced alternative must be a real or seed word.
     */
    internal fun isGateApprovedForTest(bengali: String): Boolean =
        isKnownWord(bengali) || dictionary.containsBengali(bengali)

    /**
     * Test seam for the F2 composition allowance: gate-approved OR a
     * dictionary-root + whitelisted-productive-suffix composition.
     */
    internal fun isCompositionGateApprovedForTest(bengali: String): Boolean =
        isGateApprovedForTest(bengali) || isApprovedComposition(bengali)

    /**
     * F5b learned-word sanitation oracle: should a persisted learned entry
     * (normalized [phonetic] key -> [bengali]) be honored at load time?
     *
     * Pre-gate builds learned garbage commits (kkkkx -> ক্কক্কক্স,
     * smartwatch -> স্মার্তওআতচ) that would override the commit gate through the
     * adapter preference maps. An entry is trusted iff it is something the gate
     * itself could approve today:
     * 1. [isKnownWord] — validator/store words-table membership ONLY. The
     *    seed/user [dictionary] is deliberately NOT consulted here: the load
     *    paths themselves add learned words to the dictionary, so
     *    containsBengali would self-approve the very garbage being filtered.
     * 2. Clean-floor equivalence — the entry equals the deterministic
     *    [CleanTransliterator] output of its own key. Legit learnAsWord names
     *    (rafsan -> রাফসান) ARE the floor; pre-gate inventions are not.
     * 3. [isApprovedComposition] — real-root + productive-suffix inflection.
     *
     * Keys containing non a-z characters are never gate-relevant (the gate
     * skips them — see [applyCommitGate]) so they are always trusted.
     * Fail-open: with neither the 484K validator loaded nor the sqlite store
     * attached there is no word-membership oracle — trust everything; the next
     * initialize/attach re-evaluates. Pure read: callers SKIP untrusted entries
     * on load but never delete them from storage (reversible, no data loss).
     * Custom user-dictionary entries (frequency >= 120) are exempted by the
     * caller and never reach this check.
     */
    internal fun isLearnedEntryTrusted(phonetic: String, bengali: String): Boolean {
        if (phonetic.any { it !in 'a'..'z' }) return true
        if (!validator.isLoaded() && phoneticIndex == null) return true
        if (isKnownWord(bengali)) return true
        if (bengali == CleanTransliterator.transliterate(phonetic)) return true
        return isApprovedComposition(bengali)
    }

    private fun applyBengaliRecovery(result: ConversionResult): ConversionResult? {
        val bengali = result.bengali

        // Collect candidates from multiple prefix lengths for broad coverage.
        // Also try vowel-swapped prefixes (ি↔ী, ু↔ূ) to find words like
        // নীলপরি when pattern gives নিলপৃ (short ি vs long ী)
        val allCandidates = mutableSetOf<String>()
        for (prefixLen in bengali.length downTo 2) {
            val prefix = bengali.substring(0, prefixLen)

            // Search with original prefix — reject hyphenated garbage
            val candidates = validator.findByPrefix(prefix, 30)
            for (c in candidates) {
                if (c != bengali && !c.contains("-")) allCandidates.add(c)
            }

            // Also search with vowel-swapped prefix (ি↔ী, ু↔ূ)
            val swapped = prefix
                .replace('ি', '\u0001').replace('ী', 'ি').replace('\u0001', 'ী')
                .replace('ু', '\u0002').replace('ূ', 'ু').replace('\u0002', 'ূ')
            if (swapped != prefix) {
                val swapCandidates = validator.findByPrefix(swapped, 20)
                for (c in swapCandidates) {
                    if (c != bengali) allCandidates.add(c)
                }
            }

            if (allCandidates.size >= 50) break
        }

        if (allCandidates.isEmpty()) return null

        val originalLen = bengali.length
        // Adaptive weighting: longer input = trust similarity more
        val freqWeight = when {
            originalLen >= 6 -> 0.05
            originalLen >= 4 -> 0.15
            else -> 0.25
        }
        val simWeight = 1.0 - freqWeight

        data class ScoredCandidate(
            val word: String,
            val similarity: Double,
            val frequency: Int,
            val combinedScore: Double
        )

        val scored = allCandidates.map { candidate ->
            val sim = bengaliSimilarity(bengali, candidate)
            val freq = validator.getFrequency(candidate)
            val combinedScore = sim * simWeight + (freq / 100.0) * freqWeight
            ScoredCandidate(candidate, sim, freq, combinedScore)
        }.sortedByDescending { it.combinedScore }

        // Only accept if the top candidate has reasonable similarity (>0.70) and length is close
        if (scored.isNotEmpty() && scored[0].similarity > 0.70 && kotlin.math.abs(scored[0].word.length - bengali.length) <= 3) {
            val best = scored[0]
            return ConversionResult(
                best.word, 0.85, ResolutionSource.DICTIONARY,
                listOf(Alternative(result.bengali, result.confidence)) +
                        scored.drop(1).take(4)
                            .filter { it.similarity > 0.40 }
                            .map { Alternative(it.word, it.combinedScore) }
            )
        }
        return null
    }

    /**
     * Bengali similarity using normalized comparison.
     *
     * Bengali characters like য়া vs আ, ড়া vs রা are different code points
     * but sound very similar. We normalize before LCS comparison:
     *   - Strip nukta (়): য় → য, ড় → ড
     *   - Replace standalone আ with া (same 'a' sound)
     *   - ী → ি, ূ → ু (similar sounds)
     *   - ঙ → ং, ণ → ন, ষ → শ (similar sounds)
     *
     * Returns combined score: primarily input coverage, with small candidate coverage factor.
     */
    private fun bengaliSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val normA = normalizeBengali(a)
        val normB = normalizeBengali(b)

        if (normA == normB) return 1.0

        val m = normA.length
        val n = normB.length
        val prev = IntArray(n + 1)
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (normA[i - 1] == normB[j - 1]) prev[j - 1] + 1
                else maxOf(prev[j], curr[j - 1])
            }
            prev.indices.forEach { prev[it] = curr[it] }
            curr.fill(0)
        }
        val lcsLen = prev[n]

        // Input coverage (how much of user's input is explained by candidate)
        val inputCoverage = lcsLen.toDouble() / m
        // Candidate coverage (to prevent matching very long words)
        val candidateCoverage = lcsLen.toDouble() / n
        // Combined: primarily input coverage, with small candidate coverage factor
        return inputCoverage * 0.8 + candidateCoverage * 0.2
    }

    /**
     * Normalize Bengali text for sound-level comparison.
     * Strips modifiers that don't change the sound significantly.
     */
    private fun normalizeBengali(text: String): String {
        val sb = StringBuilder(text.length + 4) // +4 for ৃ→রি expansion
        for (ch in text) {
            when (ch) {
                '\u09BC' -> {} // Strip nukta (়) — য় ≈ য, ড় ≈ ড
                'আ' -> sb.append('া')    // আ ≈ া (same 'a' sound)
                'ৃ' -> sb.append("রি")   // ৃ ≈ রি (ri-kar sounds same as র+ি)
                'ী' -> sb.append('ি')    // ী ≈ ি (similar sounds)
                'ূ' -> sb.append('ু')    // ূ ≈ ু (similar sounds)
                'ঙ' -> sb.append('ং')    // ঙ ≈ ং (similar sounds)
                'ণ' -> sb.append('ন')    // ণ ≈ ন (similar sounds)
                'ষ' -> sb.append('শ')    // ষ ≈ শ (similar sounds)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // English detection is now handled by EnglishDetector.isEnglish() in the ai package.

    // ======================== PUBLIC UTILITY METHODS ========================

    /**
     * Add a custom word to the dictionary.
     *
     * Only the single-key [phonetic] entry is evicted from [wordCache]. Callers
     * adding words after initialization must call [clearCache] so any
     * commit-gated entries re-evaluate with the updated dictionary.
     * (SmartEngineAdapter paths that bulk-load words at startup already do this.)
     */
    fun addWord(phonetic: String, bengali: String, frequency: Int) {
        val key = phonetic.lowercase().trim()
        if (!isPlausibleDynamicMapping(key, bengali)) return
        dictionary.addMapping(key, bengali, frequency)
        wordCache.remove(key)
    }

    fun isPlausibleDynamicMapping(phonetic: String, bengali: String): Boolean {
        val key = phonetic.lowercase().trim()
        if (key.isEmpty() || bengali.isEmpty()) return false
        if (convertWord(key).bengali == bengali) return true

        val reversePhonetic = ReverseTransliterator.reverseWord(bengali)
        if (reversePhonetic.isEmpty()) return false

        val overlap = PhoneticOverlapScorer.score(key, reversePhonetic).score
        return overlap >= (if (key.length >= 5) 0.65 else 0.30) && hasCompatibleVowelPath(key, reversePhonetic)
    }

    /**
     * Set learned words for section narrowing boosting.
     */
    fun setLearnedWords(words: Map<String, Int>) {
        sectionEngine.setLearnedWords(words)
    }

    /**
     * Get next-word predictions based on bigram model.
     *
     * @param prevBengali The previously committed Bengali word
     * @param limit Maximum predictions to return
     * @return List of predicted words with confidence scores
     */
    /** S20: followers observed after the exact two-word context. */
    fun getTrigramNextWordPredictions(prev2: String, prev1: String, limit: Int = 5): List<PredictedWord> =
        bigramModel.getTopTrigramPredictions(prev2.trim(), prev1.trim(), limit)
            .filter { it.bengali !in PREDICTION_STOPLIST }

    fun getNextWordPredictions(prevBengali: String, limit: Int = 5): List<PredictedWord> {
        val previous = prevBengali.trim()
        if (previous.isEmpty() || limit <= 0) return emptyList()

        val predictions = mutableListOf<PredictedWord>()
        val seen = mutableSetOf<String>()

        // 1. Personal pairs first: the user's own repeated (prev, next) commits
        // are the strongest signal and correct the corpus's formal register.
        userBigrams[previous]?.entries
            ?.filter { it.value >= USER_BIGRAM_MIN_COUNT }
            ?.sortedByDescending { it.value }
            ?.take(limit)
            ?.forEachIndexed { index, entry ->
                if (seen.add(entry.key)) {
                    predictions.add(
                        PredictedWord(
                            bengali = entry.key,
                            confidence = (0.97 - index * 0.03).coerceAtLeast(0.6)
                        )
                    )
                }
            }

        // 2. Corpus bigrams, with wiki-meta junk filtered out. Ask for extra
        // headroom so filtering and dedup don't starve the strip.
        if (predictions.size < limit && bigramModel.isLoaded()) {
            bigramModel.getTopPredictions(previous, limit * 2 + 4)
                .asSequence()
                .filter { it.bengali !in PREDICTION_STOPLIST }
                .filter { seen.add(it.bengali) }
                .take(limit - predictions.size)
                .forEach { predictions.add(it) }
        }

        // 3. Static fallback for common conversational openers.
        if (predictions.size < limit) {
            FALLBACK_NEXT_WORDS[previous].orEmpty()
                .filter { seen.add(it) }
                .take(limit - predictions.size)
                .forEachIndexed { index, word ->
                    predictions.add(
                        PredictedWord(
                            bengali = word,
                            confidence = (0.64 - index * 0.04).coerceAtLeast(0.42)
                        )
                    )
                }
        }

        return predictions.take(limit)
    }

    /**
     * Bulk-load persisted user bigram pairs (called once at initialization).
     */
    fun setUserBigrams(pairs: Map<String, Map<String, Int>>) {
        userBigrams.clear()
        for ((previous, followers) in pairs) {
            val prev = previous.trim()
            if (prev.isEmpty() || followers.isEmpty()) continue
            userBigrams[prev] = followers
                .entries
                .sortedByDescending { it.value }
                .take(USER_BIGRAM_MAX_FOLLOWERS)
                .associate { it.key.trim() to it.value }
                .filterKeys { it.isNotEmpty() }
                .toMutableMap()
        }
    }

    /**
     * Record one observed (previous, next) commit pair.
     *
     * @return the updated count for the pair (callers persist this).
     */
    fun recordUserBigram(prevBengali: String, nextBengali: String): Int {
        val prev = prevBengali.trim()
        val next = nextBengali.trim()
        if (prev.isEmpty() || next.isEmpty() || prev == next) return 0

        val followers = userBigrams.getOrPut(prev) { mutableMapOf() }
        val updated = (followers[next] ?: 0) + 1
        followers[next] = updated
        if (followers.size > USER_BIGRAM_MAX_FOLLOWERS) {
            followers.entries
                .minByOrNull { it.value }
                ?.let { followers.remove(it.key) }
        }
        return updated
    }

    /**
     * Re-rank a single word using the previously committed Bengali word.
     *
     * This is intentionally conservative: it only chooses between the primary
     * result and the alternatives already generated by the normal engine. That
     * keeps live typing fast and prevents context from inventing unrelated
     * dictionary words.
     */
    /**
     * S20: two-word context rerank. Trigram evidence (observed w1,w2,cand
     * triple) is the strongest homophone signal we have — mot after "onek
     * beshi" is মত, after "shob theke" is মোট. Falls back to the bigram
     * rerank when there is no second word or no trigram table. Promotion
     * stays evidence-gated (S4): an alternative needs an OBSERVED triple,
     * never interpolated probability alone.
     */
    fun rerankWithContext(
        prev2Bengali: String?,
        prev1Bengali: String?,
        result: ConversionResult
    ): ConversionResult {
        val prev1 = prev1Bengali?.trim().orEmpty()
        val prev2 = prev2Bengali?.trim().orEmpty()
        if (prev1.isEmpty() || !bigramModel.isLoaded() || result.alternatives.isEmpty()) {
            return rerankWithPreviousContext(prev1Bengali, result)
        }
        if (prev2.isEmpty() || !bigramModel.hasTrigrams()) {
            return rerankWithPreviousContext(prev1Bengali, result)
        }

        var bestWord = result.bengali
        var bestConfidence = result.confidence
        var bestScore = bigramModel.contextProb(prev2, prev1, result.bengali)
        val primaryTriple = bigramModel.trigramCount(prev2, prev1, result.bengali)

        for (alt in result.alternatives) {
            if (alt.bengali == result.bengali || alt.confidence < 0.35) continue
            val altTriple = bigramModel.trigramCount(prev2, prev1, alt.bengali)
            if (altTriple == 0) continue
            val bothValid = validator.isLoaded() &&
                validator.isValid(result.bengali) &&
                validator.isValid(alt.bengali)
            if (!bothValid) continue
            val altScore = bigramModel.contextProb(prev2, prev1, alt.bengali)
            // Observed triple against an unobserved primary is decisive on a
            // small margin; against an observed primary it must clearly win.
            val threshold = if (primaryTriple == 0) 1.05 else 1.30
            if (altScore > bestScore * threshold) {
                bestWord = alt.bengali
                bestConfidence = maxOf(result.confidence, alt.confidence, 0.91)
                bestScore = altScore
            }
        }

        if (bestWord == result.bengali) {
            // No trigram promotion — the bigram layer may still act.
            return rerankWithPreviousContext(prev1Bengali, result)
        }
        val alternatives = buildList {
            add(Alternative(result.bengali, minOf(result.confidence, 0.88)))
            result.alternatives
                .filter { it.bengali != bestWord && it.bengali != result.bengali }
                .forEach { add(it) }
        }
        return result.copy(
            bengali = bestWord,
            confidence = bestConfidence,
            source = ResolutionSource.STATISTICAL,
            alternatives = alternatives
        )
    }

    fun rerankWithPreviousContext(prevBengali: String?, result: ConversionResult): ConversionResult {
        val prev = prevBengali?.trim().orEmpty()
        if (prev.isEmpty() || !bigramModel.isLoaded() || result.alternatives.isEmpty()) return result

        var bestWord = result.bengali
        var bestConfidence = result.confidence
        var bestScore = bigramModel.bigramProb(prev, result.bengali)

        for (alt in result.alternatives) {
            if (alt.bengali == result.bengali || alt.confidence < 0.35) continue
            // S4: context may only promote an alternative on REAL bigram
            // evidence. bigramProb interpolates unigram frequency, and the
            // unigram table carries the same junk-corpus counts as the words
            // table (উতর@80 vs উত্তর@40) — without this guard, "modhe utor"
            // flipped the correct উত্তর primary back to উতর on commit.
            if (bigramModel.bigramCount(prev, alt.bengali) == 0) continue

            val altScore = bigramModel.bigramProb(prev, alt.bengali)
            val bothValid = validator.isLoaded() &&
                validator.isValid(result.bengali) &&
                validator.isValid(alt.bengali)
            val threshold = if (bothValid) 1.18 else 1.45

            if (altScore > bestScore * threshold) {
                bestWord = alt.bengali
                bestConfidence = maxOf(result.confidence, alt.confidence, 0.91)
                bestScore = altScore
            }
        }

        if (bestWord == result.bengali) return result

        val alternatives = buildList {
            add(Alternative(result.bengali, minOf(result.confidence, 0.88)))
            result.alternatives
                .filter { it.bengali != bestWord && it.bengali != result.bengali }
                .forEach { add(it) }
        }

        return result.copy(
            bengali = bestWord,
            confidence = bestConfidence,
            source = ResolutionSource.STATISTICAL,
            alternatives = alternatives
        )
    }

    /**
     * Clear the word conversion cache.
     */
    fun clearCache() {
        wordCache.clear()
        storeLookupMemo.clear()
        containsWordMemo.clear()
    }

    /**
     * Layer 1.2: Try suffix-stripped dictionary lookup.
     *
     * Strips inflectional suffixes (দের, ের, রা, গুলো, etc.) from the phonetic key,
     * looks up the stem in the dictionary, and reconstructs the full inflected form
     * by appending the corresponding Bengali suffix.
     *
     * Prefers longer stems and higher-frequency matches when multiple candidates match.
     */
    /**
     * S1/D3 stem-quality oracle for UNVALIDATED suffix compositions: when the
     * composed inflection is absent from the 484K validator, only well-attested
     * stems may anchor it. A stem is trusted when its corpus frequency clears
     * [MIN_COMPOSITION_STEM_FREQUENCY] (তৃতীয়@73, জাতি@71 pass; junk aliases
     * যাতি@25, যেলা@1 fail) or when it is a user/learned dictionary entry
     * ([seedFrequency] >= [USER_WORD_FREQUENCY_FLOOR], the learnAsWord
     * convention — রাফসান@120 composes রাফসানের). Inert without frequency data
     * (tiny test validators, seed-only mode): behavior is unchanged there.
     */
    private fun isCompositionStemTrusted(stemBengali: String, seedFrequency: Int): Boolean {
        if (!validator.isLoaded() || !validator.hasFrequencyData()) return true
        if (validator.getFrequency(stemBengali) >= MIN_COMPOSITION_STEM_FREQUENCY) return true
        return seedFrequency >= USER_WORD_FREQUENCY_FLOOR
    }

    internal fun trySuffixStrippedDictionary(key: String): ConversionResult? {
        var bestResult: ConversionResult? = null
        var bestStemLength = 0
        var bestFrequency = 0

        for (suffix in inflectionalSuffixes) {
            if (key.length > suffix.phonetic.length && key.endsWith(suffix.phonetic)) {
                val stem = key.substring(0, key.length - suffix.phonetic.length)
                if (stem.length < 2) continue

                val candidates = mutableListOf(stem)
                if (!stem.endsWith("a") && !stem.endsWith("o")) {
                    candidates.add(stem + "a")
                    candidates.add(stem + "o")
                }

                for (candidate in candidates) {
                    var stemResults = dictionary.lookup(candidate)
                    if (stemResults.isNotEmpty()) {
                        // Enforce consonant rules on stem results
                        if (key.startsWith("z")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("জ") }
                        }
                        if (key.startsWith("j") && !key.startsWith("jh")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("য") }
                        }
                        if (key.startsWith("s") && !key.startsWith("sh")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("শ") }
                        }
                        if (key.startsWith("sh")) {
                            stemResults = stemResults.filter { !it.bengali.startsWith("স") }
                        }
                        if (stemResults.isEmpty()) continue

                        val best = stemResults[0]
                        if (stem.length > bestStemLength ||
                            (stem.length == bestStemLength && best.frequency > bestFrequency)) {
                            var bengaliStem = best.bengali
                            if (candidate != stem) {
                                bengaliStem = bengaliStem.trimEnd('া', 'ো')
                            }
                            val combined = bengaliStem + suffix.bengali
                            val isExact = best.matchedPhonetic.isEmpty() || best.matchedPhonetic == candidate
                            val isValid = validator.isLoaded() && validator.isValid(combined)
                            // S1/D3: aliased stem keys can resolve to a word that
                            // already carries the trailing য় the suffix re-appends
                            // (tritiyo → তৃতীয়, then +য় = তৃতীয়য়). Never compose an
                            // invalid য় junction the validator does not attest.
                            if (!isValid && hasInventedYaJunction(combined)) continue
                            // S1/D3: an UNVALIDATED inflection may only be composed
                            // from a well-attested stem — junk corpus entries reached
                            // through ambiguity-aliased keys (zati → যাতি@25,
                            // zela → যেলা@1) otherwise mint invented DICTIONARY-source
                            // words (যাতির, যেলায়).
                            val stemTrusted = isValid || isCompositionStemTrusted(best.bengali, best.frequency)
                            if (isValid || (isExact && best.confidence >= 0.85 && stemTrusted)) {
                                bestResult = ConversionResult(
                                    bengali = combined,
                                    confidence = best.confidence * if (candidate == stem) 0.95 else 0.90,
                                    source = ResolutionSource.DICTIONARY,
                                    alternatives = emptyList()
                                )
                                bestStemLength = stem.length
                                bestFrequency = best.frequency
                            }
                        }
                        break
                    }
                }
            }
        }
        return bestResult
    }

    private fun cacheResult(key: String, result: ConversionResult) {
        wordCache[key] = result
    }
}
