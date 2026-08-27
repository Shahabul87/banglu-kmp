# Banglu Engine — Architecture Reference

**Status:** reference document, written 2026-08-26 against `main` at v1.5.87 (Android 2124).
**Scope:** the shared conversion engine (`shared/`), how each surface hosts it, the
Android keystroke path, the learning system, the voice pipeline and the dictionary
build. Every box below names the real file or function so the diagram can be checked
against the code.

**One-sentence description you can use anywhere:** Banglu is an on-device Bangla
phonetic engine — Avro-compatible phonetic rules, a curated dictionary of ~485,000
words, and a statistical (n-gram) context model that learns your spelling preferences
on the phone; nothing you type leaves the device. It is a rule-based and statistical
system, not a neural network and not "AI".

---

## 1. System context — one engine, six surfaces

The law that keeps six products manageable: **conversion behaviour lives in `shared/`
(Kotlin Multiplatform) and nowhere else.** Platforms differ only in how they host the
engine and which dictionary tier they carry.

```mermaid
flowchart LR
    subgraph ENGINE["shared/ — Kotlin Multiplatform engine (commonMain)"]
        SE["SmartEngine.kt<br/>7-layer conversion orchestrator"]
        SA["SmartEngineAdapter.kt<br/>singleton facade: init, learning, erase, context calls"]
        SA --> SE
    end

    subgraph ANDROID["Android IME (JVM/ART)"]
        A1["BangluIMEService<br/>default process — offline"]
        A2["SqlitePhoneticIndexStore<br/>dictionary.sqlite (176 MB)"]
    end
    subgraph DESKTOP["বাংলু এডিটর (Compose Desktop)"]
        D1["EditorState / EngineFacade"]
        D2["JvmSqlitePhoneticIndexStore<br/>full sqlite via JDBC"]
    end
    subgraph WIN["বাংলু টাইপার (Windows IME)"]
        W1["Controller / Composer<br/>AdapterComposerEngine"]
        W2["full sqlite (JDBC)"]
    end
    subgraph JS["Kotlin/JS hosts"]
        J1["Browser extension<br/>(MV3, page/worker)"]
        J2["macOS input method<br/>(JavaScriptCore)"]
        J3["bangluweb<br/>(page + Node routes)"]
        JS1["BangluWebEngine.kt (@JsExport)<br/>slim JSON dictionary (22 MB in memory)"]
        J1 --> JS1
        J2 --> JS1
        J3 --> JS1
    end

    A1 --> SA
    A2 --> SE
    D1 --> SA
    D2 --> SE
    W1 --> SA
    W2 --> SE
    JS1 --> SA
```

**Contract for every host (S130 law):** hosting the engine is not parity — the
context calls are part of the contract: `convertWordWithContext`,
`getSuggestionsWithContext`, `recordNextWordUsage`, `getNextWordPredictions`.

**Parity walls:** JVM `:shared:jvmTest` (real `./dictionary.sqlite`), JS
`:shared:jsNodeTest`, macOS `swift run BangluCoreTestRunner`, desktop
`:desktop-app:test`, Android unit + `connectedDebugAndroidTest`.

---

## 2. Android process and privacy architecture (non-negotiable)

```mermaid
flowchart TB
    subgraph DEF["default process — OFFLINE (no INTERNET permission in the launch manifest)"]
        IME["BangluIMEService"]
        CKV["ComposeKeyboardView<br/>keys, strip, panels (Compose)"]
        ADP["SmartEngineAdapter (singleton)"]
        ENG["SmartEngine<br/>seed engine → swapped full engine"]
        STORE["SqlitePhoneticIndexStore<br/>dictionary.sqlite"]
        AS["AndroidStorage<br/>banglu_learning (learned words, bigrams,<br/>English learning, identities)"]
        PREFS["banglu_prefs<br/>settings, clipboard entries, diagnostics"]
        PROV["BangluPrefsProvider<br/>(ContentProvider, hosted HERE)"]
        IME --> CKV
        IME --> ADP --> ENG --> STORE
        ADP --> AS
        IME --> PREFS
        PROV --> PREFS
        PROV --> AS
        PROV --> ADP
    end
    subgraph UI[":ui process — may use network in future builds"]
        MA["MainActivity / Settings / Tutorial / VoicePermission"]
        RP["remoteBangluPrefs()<br/>proxy over the provider"]
        MA --> RP
    end
    RP -- "get_all / put_batch /<br/>erase_learning (all | identity)" --> PROV
    GV["verifyImePrivacyBoundary<br/>(gradle task on preBuild)"] -. greps IME hot path for<br/>URL / Billing / auth tokens .-> DEF
```

Why the provider matters: Android processes share no singleton memory. Anything
that must *persist* — a settings write, an erase — is only real when it runs in the
keyboard process, which owns the one true `SharedPreferences` instance and the live
engine. `SettingsActivity` therefore never touches the engine directly; it calls the
provider (`eraseLearningInKeyboardProcess`) and shows success only on a confirmed
result (S135/S136).

---

## 3. The conversion pipeline — `SmartEngine.convertWord`

The engine converts **one word at a time**. `convertWord` is a wrapper that applies
typo correction and English arbitration around `convertWordRaw`, which runs the
layered pipeline and stops at the first layer that produces a trusted result.

```mermaid
flowchart TD
    IN["Roman input, e.g. 'bujteparcina'"] --> NORM["normalize key<br/>(lowercase, TypingHabitNormalizer)"]
    NORM --> RAW

    subgraph RAW["convertWordRaw — layered, first trusted result wins"]
        L1["Layer 1 — direct hits<br/>DIRECT_WORD_OVERRIDES, MOBILE_SHORTHAND_OVERRIDES<br/>(kmon→কেমন, hm, ok, vdo …) conf 0.999"]
        NEG["tryNegationCompound<br/>attached না/নাই/তো/নে (bolbone→বলবোনে)<br/>guards: store precedence, stem attestation"]
        ST["storeLookup — phonetic_index (sqlite/slim)<br/>order: tier ↑, priority ↑, freq ↓<br/>'canonical owner wins' (+S33 চ্চ/চ্ছ exception)"]
        L12["Layer 1.2 — suffix-stripped dictionary<br/>(trySuffixStrippedDictionary)"]
        L0["Layer 0 — section narrowing<br/>BengaliSectionIndex / SectionNarrowingEngine<br/>(needs the 480K validator list)"]
        L15["Layer 1.5 — root decomposition<br/>stem + suffix, validated against 480K"]
        L24["Layers 2–4 — rule engine<br/>CleanTransliterator → ConjunctResolver,<br/>VowelResolver, NasalResolver, ShatvaVidhan, NatvaVidhan"]
        L5["Layer 5 — AIDisambiguator (swap rules)<br/>ন↔ণ, শ↔ষ, ত↔ট … scored by DisambiguationScorer<br/>(only if confidence < 0.92)"]
        L55["Layer 5.5 — dictionary validation<br/>character-swap fixes against 480K"]
        L57["Layer 5.7 — conjunct removal recovery"]
        L6["Layer 6 — Bengali dictionary recovery<br/>(applyBengaliRecovery, coverage-arbitrated S22)"]
        CS["tryCompoundSplit<br/>bujteparcina → বুঝতে পারছিনা"]
        GATE["applyCommitGate<br/>trust floor for what space may commit"]
        L1 --> NEG --> ST --> L12 --> L0 --> L15 --> L24 --> L5 --> L55 --> L57 --> L6 --> CS --> GATE
    end

    RAW --> WRAP

    subgraph WRAP["convertWord wrapper — arbitration on top of the raw result"]
        EPI["ENGLISH_PRIMARY_INTENT flips<br/>(vetted list: an English key that collides with Bangla)"]
        HON["tryEnglishHonestyFlip (S131)<br/>real→রিয়েল not রোল: reverse-transliteration<br/>ownership floor 0.75, lexicon memo"]
        ED["EnglishDetector + junk-lexicon rescue"]
        ONSET["S113 onset-integrity floor<br/>(OOV honesty)"]
        TYPO["tryStoreTypoCorrection<br/>(TypoCorrector, skeleton/fuzzy)"]
        EPI --> HON --> ED --> ONSET --> TYPO
    end

    WRAP --> CTX["context rerank<br/>rerankWithContext(prev2, prev1, result)"]
    CTX --> OUT["ConversionResult<br/>bengali, confidence, source, alternatives"]
```

Notes that matter when reading the code:

- **Ranking law** in the phonetic index: rows order by `(tier ASC, priority ASC,
  freq DESC)`. `tier 0` = suggestible corpus words; `priority 0` = canonical
  romanization owner, `1` = habit alias. HABIT_RULES compose in table order in the
  compiler (`PhoneticIndexBuilder`) — a later rule never re-triggers an earlier one.
- **Lite mode** (`liteModeEnabled || isLowRamDevice || memoryClass < 256`): the
  loader skips the 480K validator, frequency scores, disambiguation map and bigram
  model. Layers 0/1.5/5.5/5.7/6 need the validator and simply do not run; the store
  and seeds still serve conversions. Any wrapper feature must either work without the
  validator or stay out of the composing preview (S26b law).
- **Invariants pinned by tests** (never "fix" them by editing a pin):
  kacci→কাচ্চি, jos→জোস, kassi→কাচছি, name→নামে, real→রিয়েল, roll→রোল.

---

## 4. Suggestions, alternatives and the context model

```mermaid
flowchart LR
    IN["input + previous two committed words"] --> PRIM["primary = convertWord(input)"]
    PRIM --> ALT["getAlternatives<br/>diphthong / initial-vowel / ambiguous-char variants,<br/>store hits (tier A), disambiguation swaps"]
    ALT --> CTX["rerankWithContext<br/>(trigram_triples: observed (w1,w2,cand) triples;<br/>bigrams: corpus + user pairs)"]
    CTX --> PREF["SmartEngineAdapter<br/>rerankSuggestionsByPreference + enforceCuratedLoanwordPrimary<br/>(user picks outrank corpus; loanword pins immune)"]
    PREF --> STRIP["suggestion strip — strip[0] IS the commit contract (S19)"]
    PREV["getNextWordPredictions / getTrigramNextWordPredictions"] --> STRIP
    BM["BigramModel + ViterbiDecoder<br/>(ai/) — best whole-sequence reading<br/>when several words are ambiguous"] --> CTX
```

**What the "statistical model" is, precisely.** An **n-gram model** is a table of
how often words appear next to each other in real Bangla text: after আমি, "ভালো" has
followed far more often than "ভাল্লুক", so it ranks first. Banglu stores bigram pairs
and trigram triples compiled from the corpus (`trigram_triples` in the sqlite) plus
the user's own pairs. **Viterbi** is the shortcut for choosing the best *whole
sentence* at once: it walks the candidates word by word, keeps only the most likely
path so far at each step, and ends with the single most probable sequence without
trying every combination. Promotion of an alternative over the primary stays
evidence-gated (S4/S20): it needs an *observed* triple, never interpolated
probability alone.

---

## 5. The Android keystroke hot path (S28/S29/S32 — keep it this way)

```mermaid
sequenceDiagram
    participant U as User
    participant IME as BangluIMEService (main thread)
    participant ENG as Engine (Dispatchers.Default / engineLane)
    participant IC as InputConnection (host app)

    U->>IME: key press (commit on pointer DOWN)
    IME->>IME: buffer += char
    IME->>IME: convertForInstantPreview(buffer)<br/>rule-only, zero I/O, sub-ms
    IME->>IC: setComposingText(instant preview)
    IME-)ENG: async refine (job-cancel coalescing,<br/>buffer == snapshot guard)
    ENG-->>IME: refined ConversionResult + suggestions
    IME->>IC: setComposingText(refined) + strip update
    U->>IME: space
    alt cached async result ready
        IME->>IC: commitText(cached primary)
    else not ready
        IME->>IC: commit the VISIBLE preview instantly
        IME-)ENG: reconcile off-thread (replace only while the editor<br/>still ends with what we committed; session token + buffer guards)
    end
    IME->>ENG: recordNextWordUsage(prev, committed) [learning gates apply]
```

Rules enforced by tests and StrictMode (debug builds flag any main-thread disk I/O):
no synchronous dictionary/SQLite/disk work on the main thread; WYSIWYG — the
composing preview and the space commit must agree (full and lite); cold start builds
the seed engine off-main and the instant preview returns raw input until seeds land
(~180 ms to first view).

---

## 6. Dictionary build and delivery

```mermaid
flowchart LR
    SRC1["../banglu-web/public wordlists"] --> COMP
    SRC2["dictionary-compiler/data<br/>corpus TSVs, chat_lexicon.tsv,<br/>book_lexicon.tsv, english_lexicon_overrides.tsv"] --> COMP
    SRC3["SeedData*.kt (≈6.5K curated words + phonetics)"] --> COMP
    COMP["dictionary-compiler<br/>DictionaryCompiler + PhoneticIndexBuilder<br/>(HABIT_RULES alias chains, tiering,<br/>promoteModernChhOverArchaicCc)"]
    COMP --> SQL["dictionary.sqlite<br/>words, phonetic_index (~1.35M rows),<br/>english_lexicon, trigram_triples, disambiguation<br/>metadata.version = DictionaryVersion.REQUIRED"]
    SQL --> AND["android-keyboard/src/main/assets/<br/>(pinned: android-keyboard/dictionary.sha256)"]
    SQL --> ROOT["./dictionary.sqlite — JVM tests read THIS"]
    SQL --> DESK["desktop-app + windows-ime resources"]
    SQL --> SLIM["--slim → shared/banglu-slim.json (22 MB)<br/>for every Kotlin/JS host"]
    SQL --> REL["GitHub release 'dictionary' (CI download)"]
```

The version gate (S108): `DictionaryVersion.REQUIRED` is the single bump point; the
compiler stamps it, every store refuses a mismatch, and the release validator refuses
an asset whose SHA-256/size/version differ from the pin.

---

## 7. Learning and erasure (poisoning-hardened, cross-process, race-free)

```mermaid
flowchart TD
    subgraph WRITES["learning mutations — all under learningLock (S138)"]
        W1["onWordSelected (explicit tap only — S44)"]
        W2["recordNextWordUsage (user bigrams)"]
        W3["addCustomConversion (freq 120)"]
        W4["recordIdentity (email fields only, switch default OFF)"]
        W5["recordEnglishCommit"]
    end
    WRITES --> G["isPlausibleDynamicMapping<br/>(anti-poisoning: key must phonetically overlap<br/>the reverse-transliteration of the Bengali) — never bypassed"]
    G --> P["persist { } — captures eraseGeneration,<br/>runs on the single persistence lane<br/>under persistenceMutex; a stale generation is DROPPED"]
    P --> AS["AndroidStorage / FileStorage / JS storage<br/>(rows {p,b,f,t}; ~/.banglu/learned.json on desktop)"]

    E["eraseAllLearning() — provider, keyboard process"] --> E1["under learningLock: generation++,<br/>clear maps, English, identity, drop engine"]
    E1 --> E2["persisted delete queued BEHIND pending writes<br/>on the same lane; commit() result propagated"]
    E2 --> E3["learning_erased_at stamp → IME rebuilds a clean engine<br/>(waits for the initial load job)"]
    INIT["initialize() build from a storage snapshot"] --> PUB["publish engine + maps + engineFullyLoaded<br/>ATOMICALLY under learningLock only if<br/>generation unchanged — else discard (S140)"]
```

Other laws: no learning at all until the full dictionary load completes (S34);
passive space-commits of the engine's own primary are never recorded (S26); a
learned entry equal to the raw transliteration of its key is skipped on load when it
is not a corpus word and the pipeline resolves elsewhere (S34 heal — skipped ≠
deleted).

---

## 8. Voice typing (S137 model of Google's speech service)

The keyboard never touches audio: `SpeechRecognizer` hands the microphone to the
phone's speech service (Google's on Play-certified phones). What Banglu owns is
session management and how partial hypotheses become committed text — and the
field traces showed the recognizer behaves like this:

```mermaid
stateDiagram-v2
    [*] --> Listening: mic tap → fresh SpeechRecognizer (never reused, S92)
    Listening --> Rendering: partial hypotheses (cumulative within an utterance)
    Rendering --> Rendering: VoicePartialDiff — revise only the diverging tail,<br/>fuzzy word match (VoiceWordMatch), never delete earlier words
    Rendering --> Sealed: recognizer RESET detected<br/>(VoiceCarryPolicy.reconcile: no shared prefix / new beginning)<br/>→ seal live region with a MARK ONLY, never re-commit text
    Rendering --> IdleStop: 1.5 s after speech with no new speech<br/>→ stopListening() (before Google's own endpoint degrades later utterances)
    IdleStop --> Deferred: empty final is normal → screen text is the final;<br/>punctuation deferred (দাঁড়ি at 2.8 s, comma if speech resumes ≥ 1.4 s)
    Deferred --> Listening: restart after 250 ms settle (no-delay restarts → ERROR_SERVER_DISCONNECTED)
    Listening --> Listening: silence → NO_MATCH ladder, up to 6 sessions (~30 s)
    Listening --> [*]: user stop / cancel (no দাঁড়ি on user stop — S42)
```

Pure, unit-pinned policies: `VoiceCarryPolicy`, `VoicePartialDiff`, `VoiceWordMatch`,
`VoiceSessionPolicy`, `VoiceAnchorPolicy`, `VoiceTextNormalizer`.

---

## 9. Where the pieces live

| Concern | File(s) |
|---|---|
| Conversion orchestrator | `shared/src/commonMain/kotlin/com/banglu/engine/SmartEngine.kt` |
| Host facade, learning, erase, context calls | `…/engine/SmartEngineAdapter.kt` |
| Rules | `…/engine/rules/` (CleanTransliterator, ConjunctResolver, VowelResolver, NasalResolver, ShatvaVidhan, NatvaVidhan, StatisticalDefaults) |
| Dictionary structures | `…/engine/dictionary/` (SmartDictionary, PhoneticTrie, BengaliWordValidator, SectionNarrowingEngine, FrequencyRanker, SeedData*) |
| Statistical model | `…/engine/ai/` (BigramModel, ViterbiDecoder, AIDisambiguator, EnglishDetector) |
| Disambiguation scoring | `…/engine/disambiguation/DisambiguationScorer.kt` |
| English typing suite | `…/engine/english/` |
| Touch targeting | `…/engine/touch/` (TouchTargetModel, CharBigramData) |
| Identity assist | `…/engine/assist/IdentityAssist.kt` |
| Platform seams | `…/engine/platform/` (PhoneticIndexStore, PlatformStorage, DictionaryLoader) |
| Android IME | `android-keyboard/src/main/kotlin/com/banglu/keyboard/BangluIMEService.kt`, `ComposeKeyboardView.kt`, `SqlitePhoneticIndexStore.kt`, `AndroidStorage.kt`, `BangluPrefsProvider.kt` |
| Voice policies | `android-keyboard/.../Voice*.kt` |
| Compiler | `dictionary-compiler/` (DictionaryCompiler, PhoneticIndexBuilder) |
| Release gate | `scripts/validate_android_release.sh`, `scripts/android_device_smoke.py`, `scripts/verify_dictionary_pin.sh` |

## 10. Glossary

- **Phonetic (Avro-style) typing** — writing Bangla with lowercase English letters
  by sound; the engine maps them to Bangla script.
- **Tier / priority** — the index's ranking keys: tier 0 words are suggestible corpus
  words; priority 0 is the canonical romanization owner, 1 a habit alias.
- **n-gram model** — counts of word pairs/triples from real text, used to rank the
  likely next word and to pick between homophones by context.
- **Viterbi decoding** — the efficient search for the most probable whole sequence of
  words given those counts.
- **WYSIWYG contract** — what the composing preview shows is exactly what space
  commits.
- **Lite mode** — the reduced-memory configuration for low-RAM phones; conversions
  stay store-backed.
- **Learning generation / learningLock** — the mechanism that makes "Clear learned
  data" complete and race-free across threads and processes.
