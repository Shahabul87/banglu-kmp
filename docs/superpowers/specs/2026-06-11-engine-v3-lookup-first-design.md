# Banglu Engine v3 — Lookup-First Conversion Core

**Date:** 2026-06-11
**Status:** Approved design, pending implementation plan
**Scope:** `shared/` engine module, `dictionary-compiler/`, `android-keyboard/` integration points

---

## 1. Problem statement

The Android/KMP engine converts lowercase roman input to Bengali through a
generate-then-validate pipeline: rule layers and a candidate lattice *invent*
Bengali strings, then a 484,996-word validator checks whether they are real,
with ten recovery layers patching failures. Observed consequences:

1. **Weird words committed to the editor.** The pattern engine always produces
   output (~0.85 confidence). When recovery layers miss, that raw output
   commits. Cleanliness guards (`isCleanSuggestion`,
   `hasSuspiciousGeneratedConjunct`, SmartEngine.kt:1205-1241) filter only the
   suggestion strip, never the editor primary.
2. **Non-dictionary suggestions.** The candidate lattice
   (`generateCandidateLattice`, SmartEngine.kt:2460) beam-searches up to ~128
   Bengali strings; filters are shape heuristics, not dictionary closure.
3. **Names and unknown English get mangled.** English coverage is a hand-curated
   set (~500 detector words, 967 transliteration entries). Everything outside
   falls into Bengali ambiguity machinery (shatva/natva, ন↔ণ swaps) that was
   designed for tatsama words and actively corrupts names.
4. **Complexity debt.** SmartEngine.kt is 4,052 lines, 23 stages, 200+ hardcoded
   word overrides — each added to patch the previous layer's failures.
5. **Resource cost.** The 484k word list loads into RAM as a HashSet plus a
   sorted list (~60MB) inside a keyboard process; dictionary.sqlite is 77MB
   with no phonetic index over the full word list.

Root cause: the 484k dictionary has **no phonetic keys** (only ~8,900 seed
entries do), so the engine cannot *find* words — it must guess them.

## 2. Design principle

Invert the architecture, following the proven web `smartengine-v2` philosophy
(banglu-web/src/engine/smart-v2):

> **Rules generate keys. The lexicon generates words. The ranker is the only
> gate. The engine never invents a Bengali string and asks if it is real.**

## 3. Target architecture

Three layers replace the current 23-stage pipeline.

### 3.1 Layer A — Rule layer (stateless, deterministic)

Two pure functions, no dictionary access, no learning, fully unit-testable:

- `canonicalKeys(roman: String): List<String>` — a **bounded** key lattice
  (hard cap, target ≤ 12 keys). Applies typing-habit normalization (the
  assa→accha class, existing `TypingHabitNormalizer` rules) and ambiguous-pair
  key variants (s/sh, c/ch/chh, j/z, ph/f, v/bh, vowel-length collapses).
  Output is *roman keys*, never Bengali.
- `transliterate(roman: String): String` — exactly **one** Bengali string via
  deterministic swap-free rules: conjunct table, vowel kar rules, default
  consonant forms (n→ন always, s per simple positional default, no
  ShatvaVidhan/NatvaVidhan guessing, no lattice, no character swaps). This is
  the OOV floor: output must always be *readable* Bengali, never optimal,
  never weird.

The same `transliterate` logic (in reverse, via the existing
`ReverseTransliterator`) defines canonical keys at compile time — runtime and
compile time share one notion of "how a word is typed."

### 3.2 Layer B — Lexicon layer (compiled offline → dictionary.sqlite)

New/changed tables in the compiled asset:

| Table | Contents | Notes |
|---|---|---|
| `phonetic_index(key, word_id, tier)` | Canonical roman key(s) for **all 484,996 words**, computed offline by ReverseTransliterator + offline shatva/natva/conjunct disambiguation | Tier A = suggestible; Tier B = exact-match only |
| `irregular_variants(key, word_id, source)` | Keys where user typing diverges from canonical (accha/assa/asa → আচ্ছা) | Seeded from existing curated data (DIRECT_WORD_OVERRIDES, seed phonetic variants, disambiguation-map); grows from variant mining and usage, never from engine code |
| `english_lexicon(key, bengali, priority)` | ~30k entries generated offline: top English frequency list × CMU pronunciation dictionary × phoneme→Bengali mapper | The 967 curated entries (EnglishDirectData, EnglishPronunciationVariantData) override generated rows |
| `user_words(key, bengali, count, last_used)` | Learned OOV commits (names, new loanwords) — device-local, not in the asset | Replaces/extends current selectedPreferenceMap persistence |
| `words`, `bigram_*`, frequency tables | Unchanged | |

**Tiering policy (approved):** all 484k words remain in the index — nothing is
deleted. Tier A (~top 100k frequency-backed words) drives suggestions and
ranking. Tier B (the long tail, including scraped noise) is reachable **only**
by exact key match and is never proactively suggested, so junk cannot pollute
the strip while rare-but-real words still convert when typed precisely.

### 3.3 Layer C — Ranker + commit gate

Candidates come exclusively from: phonetic_index hits (Tier A by
prefix/variant; Tier B exact only), irregular_variants, english_lexicon, and
user_words. Every candidate is a real word by construction.

Scoring (web-V2 ranker model, SmartEngineV2Ranker.ts as reference):

```
score = frequency
      + key-alignment bonus   (s vs sh, o-kar vs inherent-o, etc.)
      + bigram context score  (existing BigramModel)
      + user-preference boost (user_words / learned selections)
      + tier penalty          (Tier B exact hits rank below Tier A)
```

**Commit-gate invariant (the core contract of v3):**

> The editor primary is always one of:
> (a) a dictionary word from phonetic_index/irregular_variants,
> (b) an english_lexicon entry,
> (c) a user_words entry,
> (d) the clean deterministic transliteration.
> It is **never** an ambiguity-swapped, conjunct-recovered, or
> lattice-composed string.

Suggestion strip: ranked candidates (dictionary-closed by construction), plus
two fixed fallback slots — the clean transliteration and the raw roman input —
so the user always has an escape hatch.

### 3.4 OOV behavior (approved)

For input with no lexicon hit (names, unknown English): commit the clean
deterministic transliteration as primary (rafsan → রাফসান); raw roman remains
in the strip. On commit, the (key, bengali) pair is learned into `user_words`
so the word is suggestible from then on.

### 3.5 What moves offline

ShatvaVidhan, NatvaVidhan, DisambiguationScorer, and conjunct disambiguation
move into `dictionary-compiler`, where they help produce correct canonical
keys per word at build time. At runtime the correct ন/ণ or শ/ষ form is found
by lookup, not generated by swaps. The runtime swap/recovery machinery
(AIDisambiguator swaps, Layers 5.5/5.7/6, conjunct-removal recovery, Bengali
edit-distance recovery, section narrowing) is deleted in Phase 2.

## 4. How current pain points resolve

| Pain point | Resolution |
|---|---|
| Weird words in editor | Impossible by commit-gate invariant |
| Weird suggestions | Strip is dictionary-closed by construction; heuristic filters deleted |
| Names mangled | Swap-free transliteration path + user_words learning |
| Mixed English (scooter, practice korsi) | Generated 30k english_lexicon; per-word pipeline chains words as today |
| assa/acca/accha → আচ্ছা | irregular_variants table, data not code |
| Oversized dictionary | Word list kept as validation backbone; runtime machinery removed; RAM ~60MB → <10MB; Tier B junk cannot surface uninvited |

## 5. Phasing

### Phase 1 — Index + gate (additive; keyboard shippable throughout)

1. **Compiler: canonical key generation.** For all 484k words, compute
   canonical key(s). Measure **round-trip coverage**: key →
   `transliterate(key)` → original word? Words that fail round-trip
   automatically receive variant keys. The coverage number quantifies exactly
   how much irregular dictionary is truly needed.
2. **Compiler: english_lexicon generation** from CMUdict + English frequency
   list via a phoneme→Bengali mapper; curated entries override.
3. **Engine: `PhoneticIndexLookup`** as new Layer 0 in SmartEngine.
4. **Engine: clean transliteration mode** (deterministic path, swaps disabled).
5. **Engine: commit gate** in `convertWord` and `convertForComposing`.
6. **Tests:** existing parity suite stays green; new suites for OOV/names,
   loanwords, mixed sentences, and irregular variants.

### Phase 2 — Tower teardown (one deletion at a time, parity-gated)

Delete in order, each gated on the regression corpus showing no accuracy drop:
recovery Layers 5.5/5.7/6 → section narrowing → typo-swap paths → runtime
shatva/natva application → candidate-lattice Bengali generation (lattice
survives only as key generation). Migrate the 200+ `DIRECT_WORD_OVERRIDES`
into irregular_variants. Target: SmartEngine.kt ≤ ~1,200 lines. Stop loading
the 484k HashSet/sorted list into RAM; validator becomes sqlite queries + LRU.

## 6. Regression harness

A corpus regression runner in `shared/src/commonTest`: a list of
(roman → expected Bengali) pairs assembled from (a) existing test expectations,
(b) round-trip pairs sampled from the compiled index, (c) the loanword and
OOV suites. Reports conversion accuracy % per build; Phase 2 deletions must
not lower it.

## 7. Risks and numbers to verify in Phase 1 step 1

| Risk | Mitigation / measurement |
|---|---|
| Asset growth (est. +15–25MB over current 77MB) | Real number from the index build; if oversized, hash Tier B keys and/or store key→id only |
| Round-trip coverage unknown (<80% would inflate variant mining) | Step 1 produces the number before any engine code changes |
| Keystroke latency (sqlite per keystroke must stay <16ms) | In-memory prefix index over Tier A only (~3–5MB); LRU cache; measure on device |
| CMUdict phoneme→Bengali mapper quality | Spot-check against the 967 curated entries (they double as a test set); curated always overrides generated |
| Tier A cutoff (~100k) too aggressive/loose | Cutoff is a compiler parameter; tune with the regression harness |

## 8. Out of scope

- iOS keyboard integration (ios-keyboard-engine consumes the same shared
  module; no iOS-specific work in this effort).
- Web engine changes (banglu-web is the reference, not a target).
- New UI/keyboard layout work in android-keyboard beyond wiring the engine.
- Sentence-level rewriting or transformer/ML models — the bigram model is the
  only context signal in v3.

## 9. Phase 1 measurements (recorded 2026-06-11, compiler run over real corpus)

| Metric | Value | Gate | Verdict |
|---|---|---|---|
| Corpus words | 484,996 (480,058 index-eligible after length/script filters) | — | — |
| Phonetic index rows | 563,998 (~1.17 keys/word) | — | — |
| Round-trip coverage | **38.7%** (185,746/480,058) | informational | Deterministic floor reproduces ~39% of words exactly; the index (not round-trip) drives conversion, but this number sizes the future variant-mining backlog |
| Words with zero index keys | 15,811 (3.3%, visarga-class & >24-char keys) | no silent caps | Counted + recorded in metadata (`phonetic_words_no_rows`); follow-up: extend ReverseTransliterator visarga mapping |
| Dropped keys | 18,579 | — | recorded (`phonetic_dropped_keys`) |
| English lexicon entries | 27,745 (2 unconvertible) | — | scooter→স্কুটার, internet→ইন্টারনেট verified |
| dictionary.sqlite size | **104 MB** (was 77 MB; 115 MB before word_id normalization) | ≤105 MB | PASS — `phonetic_index` stores `word_id` (join to `words`), not Bengali text |
| Unmapped index rows after normalization | 0 | — | every row joins to a word |

### Regression baseline (Phase 2 deletion gate)

Recorded 2026-06-11 from `./gradlew :shared:jvmTest --rerun` (aggregated from `shared/build/test-results/jvmTest/*.xml`):

- **Total: 359 tests, 0 failures, 0 skipped** (`:dictionary-compiler:test`: 27 tests, separate module). Any Phase 2 deletion PR must keep this suite green.
- Acceptance suite (`EngineV3AcceptanceTest`, 5 tests encoding spec §4 scenarios):
  - `irregularVariantsAllReachTheSameWord` — accha/assa/acca → আচ্ছা
  - `mixedEnglishBengaliSentence` — scooter → স্কুটার, play → প্লে, "ami scooter" parses mixed
  - `editorNeverShowsInventedStrings` — alien input ("kkkkx") commits CLEAN_TRANSLITERATION, never a pattern guess
  - `namesGetReadableTransliterationAndRawStaysReachable` — "rafsan" → readable Bengali floor, no roman residue
  - `tierBWordsResolveOnExactMatchOnly` — Tier B index entry resolves on its exact key
- Suggestion-quality benchmark (`SuggestionQualityBenchmarkTest`, 5 tests, all green): 74-case common-word set — 100% exact conversion, 100% top-3 suggestion presence, exact-dictionary-before-composer ranking invariant, latency budgets met (budgets: <10 ms/op conversion over 1,480 ops, <20 ms/op suggestions over 740 ops; full-test wall times 0.024 s and 0.297 s respectively, JUnit XML). It asserts pass/fail rather than printing a score; the baseline observation is "0 misses on all 74 cases".
- Tier note: all 563,998 compiled index rows are currently **Tier A** — the frequency file covers the full corpus, so the tier criterion `freq > 0` marks everything suggestible. A frequency-threshold parameter is needed when prefix-suggestions land (recorded for Phase 2).

### Corpus-fix round (F1-F5b, 2026-06-11)

Real-world corpus re-measurement (542 unique Bengali words from /tmp/banglu_corpus.txt,
keys derived via `ReverseTransliterator.reverseWord`, nukta-canonicalized comparison,
full setup: 484,996-word dictionary + phonetic index + english lexicon + words-set store).

| Metric | Before (db 3.3.0) | After (db 3.3.2) |
|---|---|---|
| Corpus primary accuracy | 96.8% (510/527 typeable) | **97.2% (527/542)** |
| Corpus top-3 accuracy | 99.1% (522/527) | **99.4% (539/542)** |
| Untypeable keys (cluster/visarga) | 15 of 542 | **0** |
| Index words with no rows | 15,811 | **35** |
| Dropped index keys | 18,579 | **62** |
| Index rows | 563,998 | 669,123 |
| English curated agreement | 28.5% | **55.1%** |
| dictionary.sqlite version | 3.3.0 | 3.3.2 |

Gate fixes shipped alongside the key/index recompile:

- **Composition gating (F2)** — multi-chunk compositions must be dictionary words or
  approved compositions; pattern guesses (শ্রমদক্ষটার-class) no longer escape as primaries;
  the deterministic floor takes over (shromodokkhotar → শ্রমদক্ষতার, CLEAN_TRANSLITERATION).
- **Lite-mode arming via store (F5)** — the commit gate arms whenever a phonetic index
  store is attached, even before the full word list loads, closing the first-install window.
- **Learned-word sanitation (F5b)** — sub-custom (<120 freq) learned entries are honored
  only when the gate oracle trusts them (real word / clean floor / approved composition);
  garbage learned by pre-gate builds is skipped on load (never deleted).

Fixed misses by task: অব, দুটিই (F3 exact-match arbitration); all 15 former untypeable
words — আফ্রিকা, দারিদ্র্য, দুঃখজনক, ব্র্যাকের, যুক্তরাষ্ট্র(ের), রাষ্ট্রকে/রাষ্ট্রীয়/রাষ্ট্রের, লক্ষ্যে,
সন্ধ্যায়, সম্প্রতি, সাম্প্রতিক, স্বতঃস্ফূর্ত, স্বাস্থ্য — now PRIMARY (F1 cluster/visarga key generation);
শ্রমদক্ষতার TOP3→PRIMARY (F2 composition gating let the correct floor through).

Remaining misses (3, all dictionary gaps — the word is absent from the 484K corpus):

- এডটেক (`edotek`) → এদিকটাতে (DICTIONARY) — fuzzy dictionary hit outranks the floor;
  loanword "edtech" not in corpus or english lexicon.
- ভ্যান্স (`bhyans`) → ভ্যানস (DICTIONARY) — near-form without the ন্স conjunct; proper
  noun (Vance) absent from corpus.
- যোগাযোগমাধ্যমে (`zogazogomadhyome`) → জগাজগমাধ্যমে (CLEAN_TRANSLITERATION) — compound
  absent from corpus; deterministic floor maps z→জ (the য-form needs dictionary knowledge).
  Floor output unchanged from the before-run; quality acceptable for an OOV floor.

Known regression (1): জন (`jon`) PRIMARY→TOP3 — F1's ya-phala variant keys give জন্য an
exact `jon` key, and জন্য (freq 94) outranks জন (freq 84) in exact-match arbitration;
জন remains in top-3. Candidate Phase 2 fix: exact-romanization hits should outrank
variant-key hits at equal tier.

Gate-escape re-check (the 5 former escapes): swasthyo → স্বাস্থ্য, lokkhye → লক্ষ্যে,
daridryo → দারিদ্র্য, sondhay → সন্ধ্যায় (all DICTIONARY); sromodokkhotar → স্রমদক্ষতার
(CLEAN_TRANSLITERATION, approved deterministic floor — the s-spelling cannot reach the
শ্র dictionary form). No non-dictionary primaries except approved floors.

### Phase 2 backlog (recorded at Phase 1 final review, 2026-06-11)

1. **`irregular_variants` table not yet built** — assa/acca/accha-class behavior
   currently rides on the legacy `DIRECT_WORD_OVERRIDES` code in SmartEngine,
   contradicting §4's "data not code" wording. Phase 2 must migrate those
   200+ overrides into the compiled table (already in the Phase 2 step list)
   and the acceptance test `irregularVariantsAllReachTheSameWord` then
   exercises the index path end-to-end.
2. **Tiering is vacuous in the shipped asset** — all 563,998 index rows are
   Tier A because `word-frequency.json` covers the full corpus and the tier
   criterion is `freq > 0`. Add a frequency-threshold compiler parameter
   before prefix-suggestions (`lookupPrefix`, currently dead API) land.
3. **Engine mutation thread-safety** — trie inserts from async OOV learning
   race with main-thread reads (pre-existing exposure, widened by learning);
   serialize engine mutation (Mutex or single-thread dispatcher).
4. **OOV word graduation** — learned names persist at freq 94 (< 120 reload
   threshold); output survives restarts via the preference layer, but consider
   commit-count-based promotion to durable dictionary entries.
5. **Suggestion-tap learning** — tapping a clean-transliteration suggestion
   does not trigger learning (SmartSuggestion lacks ResolutionSource).
6. **english_lexicon unconvertible count** — persist
   `EnglishLexiconBuilder.lastSkippedUnconvertible` into compiler metadata so
   the "2 unconvertible" figure is re-verifiable from the asset.
7. **Visarga keys** — 15,811 words (du:kh-class) have zero index keys because
   ReverseTransliterator emits `:` for ঃ; extend the romanization or add a
   visarga-aware alias rule.

## 10. Decisions log

| Decision | Choice | Date |
|---|---|---|
| Rebuild approach | Staged rebuild (index + gate first, then teardown) | 2026-06-11 |
| OOV editor behavior | Clean deterministic transliteration as primary; raw roman in strip; learn on commit | 2026-06-11 |
| English coverage | Offline-generated ~30k lexicon (CMUdict + frequency list); curated overrides | 2026-06-11 |
| 484k corpus policy | Tiered index — Tier A (~100k) suggestible, Tier B exact-match only; nothing deleted | 2026-06-11 |
