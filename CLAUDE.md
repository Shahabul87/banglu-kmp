# Banglu — Agent Knowledge Base

> Read this FIRST. It is the onboarding document for any AI agent or engineer
> working on this repository. It explains what we are building, why, how the
> code is organized, and the hard-won invariants you must not break.

---

## 1. Mission & Vision

**Mission:** let every Bengali speaker type Bangla the way they already think —
lowercase English letters in, correct Bangla out — with zero surprise, zero
lag, and zero data leaving their phone.

**Vision:** become the default Bangla keyboard for the chat generation by
winning on exactly four things (market research, `~/Downloads` deep-research
report 2026-07-12, validated against Ridmik/Gboard/SwiftKey/Borno reviews):

1. **Avro-compatible but smarter** — canonical phonetics PLUS the real chat
   register (issa→ইচ্ছা, kmon→কেমন, korsi→করছি, golp→গল্প, bolbone→বলবোনে).
2. **Predictable, stable, low-surprise typing** — what the preview shows is
   what space commits (WYSIWYG contract); no frozen keys, ever, on any phone
   down to 2GB RAM.
3. **Private by default** — the typing engine is 100% offline, enforced by
   architecture (process isolation + gradle verification task), not by policy.
4. **Bangla-first UX** — dari on double space, tight comma, ০-৯ digits,
   conjunct-perfect output, inline Bangla voice typing.

**Business model:** free core typing, no ads ever in the typing surface.
Future revenue = optional premium (AI rewrite via the :ui process, power-user
packs). NO cloud API on the keystroke path — decided 2026-07-03 (latency,
cost, and privacy-promise reasons; see memory + git history).

**Current status (2026-07-13):** v1.5.32 (2069), db 3.8.4. Pre-launch. Play
Console submission is the next user action (closed testing → production).
Testers actively feed spelling/UX reports; each becomes an "S-round" fix.

---

## 2. Repository Map

```
banglu-kmp/
├── CLAUDE.md                  ← this file
├── dictionary.sqlite          ← dev copy of compiled db — JVM TESTS LOAD THIS
│                                 (cp from compiler output after every rebuild!)
├── shared/                    ← Kotlin Multiplatform ENGINE (the brain)
│   └── src/
│       ├── commonMain/kotlin/com/banglu/engine/
│       │   ├── SmartEngine.kt          ← conversion pipeline (~5000 lines)
│       │   ├── SmartEngineAdapter.kt   ← singleton facade: init, learning,
│       │   │                              preferences, engine swap
│       │   ├── dictionary/  SeedData*.kt (≈6.5K curated words + phonetics),
│       │   │                EnglishDirectData, WordCategory
│       │   ├── rules/       CleanTransliterator, pattern tables
│       │   ├── platform/    PhoneticIndexStore (interface), InMemory impl,
│       │   │                PlatformStorage, DictionaryLoader
│       │   ├── disambiguation/  ত/ট দ/ড ন/ণ শ/ষ resolution
│       │   ├── ai/          bigram/trigram context rerank (on-device)
│       │   └── util/        ReverseTransliterator (Bengali→roman), nukta fold
│       ├── commonTest/      seed-only engine tests (no store)
│       └── jvmTest/         FULL-STORE tests — the real regression wall:
│                            parity pins, S26/S27/S33/S34/S35/S43 round tests,
│                            ConjunctSolutionRoundJvmTest.engine = shared
│                            engine loaded from ./dictionary.sqlite
├── android-keyboard/          ← the ANDROID APP (IME + UI activities)
│   └── src/main/kotlin/com/banglu/keyboard/
│       ├── BangluIMEService.kt     ← THE keyboard service (default process,
│       │                              offline; ~3000 lines; hot path)
│       ├── ComposeKeyboardView.kt  ← all key layouts/gestures (Compose)
│       ├── SmartEngine hookups: AndroidDictionaryLoader (asset copy + lite
│       │   gating), SqlitePhoneticIndexStore, AndroidStorage (learned words)
│       ├── MainActivity.kt         ← home: hero + live try-it editor (:ui)
│       ├── SettingsActivity.kt     ← settings incl. theme/height/font/learning
│       ├── TutorialActivity.kt     ← the guide (steps + full phonetic mapping)
│       ├── VoicePermissionActivity, BangluPrefsProvider, BangluProcessGuards
│       └── build.gradle.kts        ← versionCode/Name, perf buildType,
│                                      verifyImePrivacyBoundary task
├── android_account/            ← dynamic feature: auth/billing — :ui process
│                                  ONLY, never loaded in the IME process
├── dictionary-compiler/         ← JVM tool that builds dictionary.sqlite
│   ├── src/.../DictionaryCompiler.kt   (db schema, version string)
│   ├── src/.../PhoneticIndexBuilder.kt (HABIT_RULES alias chains, tiering,
│   │                                    chh-promote pass)
│   └── data/                    corpus TSVs, chat_lexicon.tsv,
│                                english_lexicon_overrides.tsv (CMU fixes)
├── ios-app/, ios-keyboard-engine/  ← iOS scaffold (future phase; market gap:
│                                     SwiftKey has NO iOS transliteration)
├── banglu-web (SIBLING REPO ../banglu-web)  ← wordlist source for compiler +
│                                     TS web engine (NOT yet paritied with v3)
├── design/play-store/           ← STORE-LISTING.md (paste-ready), PRIVACY-
│                                  POLICY.md (canonical), DATA-SAFETY-FORM.md,
│                                  screenshots-1.5.24/, icons
├── releases/                    ← versioned .apk (testers) + .aab (Play),
│                                  gitignored artifacts
├── docs/                        ← engine research studies (register, conjunct)
└── scripts/
```

---

## 3. Architecture

### 3.1 Process & privacy architecture (NON-NEGOTIABLE)

```
┌────────────── default process (OFFLINE, no network) ──────────────┐
│ BangluIMEService → ComposeKeyboardView                            │
│        │ keystrokes                                               │
│        ▼                                                          │
│ SmartEngineAdapter (singleton) → SmartEngine (seed + swapped full)│
│        │                              │                           │
│ AndroidStorage (learned words)   SqlitePhoneticIndexStore         │
│ SharedPreferences                dictionary.sqlite (143MB asset)  │
└───────────────────────────────────────────────────────────────────┘
┌────────────── :ui process (may use network) ──────────────────────┐
│ MainActivity / Settings / Tutorial / VoicePermission / account    │
└───────────────────────────────────────────────────────────────────┘
```

- `verifyImePrivacyBoundary` (android-keyboard/build.gradle.kts) greps the IME
  hot-path files for forbidden tokens (URL, BillingClient, auth classes) and
  fails the build. It runs on preBuild. Never weaken it.
- Voice typing uses the OS SpeechRecognizer with explicit first-use disclosure.

### 3.2 Conversion pipeline (SmartEngine.convertWord)

Wrapper (typo correction + English arbitration + intent flips)
→ `convertWordRaw` layers, in order:
1. DIRECT_WORD_OVERRIDES, then MOBILE_SHORTHAND_OVERRIDES (kmon, hm, ok, vdo,
   rain, tmra… — conf 0.999, also mirrored in the instant preview)
2. tryNegationCompound — attached না/নাই/তো/নে (bolbone→বলবোনে); guards:
   whole-word store precedence, stem attestation (validator OR corpus
   containsWord), prefix conf ≥0.9
3. store (sqlite phonetic_index) exact → dictionary/seed → compound split
   (bujteparcina→বুঝতে পারছিনা) → skeleton/fuzzy → recovery → rule fallback
4. context rerank (user bigrams > corpus trigrams, observed-triple gated)

**Ranking law:** index rows order by (tier ASC, priority ASC, freq DESC).
tier 0 = suggestible corpus words; priority 0 = canonical romanization owner,
1 = habit alias. "Canonical owner wins" — with the S33 exception: an archaic
চ্চ owner is demoted when a strictly-more-frequent চ্ছ twin shares the key
(compiler `promoteModernChhOverArchaicCc`).

### 3.3 IME hot path (S28/S32/S29 architecture — keep it this way)

- EVERY keystroke: sync rule-only `convertForInstantPreview` (sub-ms, zero
  I/O, test-enforced) → async refine on Dispatchers.Default (buffer==snapshot
  guarded, job-cancel coalescing).
- Space commit: cached async conversion if ready; else commit the VISIBLE
  preview instantly and reconcile off-thread (replace only while editor still
  ends with what we committed; session token + buffer guards). NEVER convert
  synchronously on the UI thread.
- Cold start: seed build + store attach + AndroidStorage all off main
  (view shows in ~180ms). Instant preview returns raw input until seeds land.
- StrictMode (debug builds) flags any main-thread disk I/O — treat new
  violations as bugs.

### 3.4 Learning system (poisoning-hardened)

- Passive space-commits of the engine's own primary are NEVER recorded (S26).
- No learning at all until the full dictionary load completes (S34) —
  `dictionaryReadyForLearning` gate in the service.
- Load heal: a learned entry equal to the raw transliteration of its own key
  is skipped when it's not a corpus word AND the pipeline resolves elsewhere
  (S34, `isLearnedEntryTrusted`); skipped ≠ deleted (F5b reversibility).
- ENGLISH_PRIMARY_INTENT flips and curated loanwords are preference-immune.

### 3.5 Dictionary build

```
../banglu-web/public wordlists + corpus TSVs + SeedData
        │  ./gradlew :dictionary-compiler:run \
        │     --args="<abs>/banglu-web/public <abs>/dictionary.sqlite"
        ▼
dictionary.sqlite (words, phonetic_index ~1.35M rows, english_lexicon,
                   trigram_triples, disambiguation)  — version gate:
                   DictionaryCompiler "3.8.4" == AndroidDictionaryLoader
                   REQUIRED_DB_VERSION
        ▼  cp to android-keyboard/src/main/assets/dictionary.sqlite
        ▼  cp to ./dictionary.sqlite   ← REPO ROOT — JVM tests read THIS
```
HABIT_RULES compose in table order over aliases-produced-so-far; a later rule
never re-triggers an earlier one (order bugs are silent — S27 lesson).

### 3.6 Lite mode (low-RAM phones)

`liteModeEnabled || isLowRamDevice || memoryClass < 256` → loader skips the
476K validator list, extended dict, freq scores, disambiguation, bigrams.
Sqlite store + seeds remain → conversions stay store-backed. Any convertWord-
wrapper feature must either work without the validator or NOT be mirrored
into the composing preview, else lite preview/commit diverge (S26b law).

---

## 4. How we work (S-rounds)

Tester report → reproduce → JVM probe on the real store → root-cause →
targeted fix at the RIGHT layer (shorthand < seed < habit rule < engine
logic < compiler pass) → regression test named `S<NN>...JvmTest` → full
suites (`:shared:jvmTest :shared:testDebugUnitTest`, 470+ green) → perf build
on device → screenshot-verified → version bump → release artifacts → commit
with S-number → tag → push. One S-round = one commit = one story.

### Build variants
- `assembleDebug` — logging + StrictMode; slow; never judge feel on it.
- `assemblePerf` — R8 + DEBUG SIGNATURE: installs over debug/perf on the dev
  phone, learned data survives. THE variant for typing-feel testing.
- `assembleRelease`/`bundleRelease` — release keystore (`banglu-release.jks`,
  gitignored, exists ONLY on this laptop — must stay backed up off-machine).
  Artifacts → `releases/banglu-<ver>-<code>.apk|.aab`.

### Device/emulator gotchas (cost hours if forgotten)
- `adb install -r` over the LIVE IME leaves the system binding stale (no
  process, keyboard won't appear): fix `ime disable` + `enable` + `set`.
- `am force-stop` on the IME makes Android fall back to the OEM keyboard —
  re-run `ime set` before testing.
- Emulator low-end profile: `adb root; setprop dalvik.vm.heapgrowthlimit 128m`
  (→ lite mode; 512m → full). Resets on reboot.
- adb shell inside loops eats stdin → append `</dev/null`; zsh needs `${=var}`
  for word splitting.
- SettingsActivity/TutorialActivity are NOT exported — enter via MainActivity.

### Versioning & releases
versionCode/versionName in android-keyboard/build.gradle.kts; bump BOTH every
shippable change; tag `v<versionName>`; push main + tag. db changes bump the
compiler version string AND REQUIRED_DB_VERSION together.

---

## 5. Invariants (breaking any of these is a production incident)

1. Keystroke path: no sync dictionary/SQLite/disk work on the main thread.
2. WYSIWYG: composing preview and space-commit must agree (full AND lite).
3. No learning from seed-window commits; never learn a preview the engine
   didn't rank first.
4. IME process stays offline; account/billing stays in :ui.
5. Suggestion strip[0] IS the commit contract (S19).
6. Never break: kacci→কাচ্চি (dish), jos/hoise/dibi defaults (deliberate),
   kassi→কাচছি (standard orthography), name→নামে class stays Bengali.
7. Parity pin tests exist for a reason — a "fix" that flips a pin needs a
   documented decision, not a test edit.
8. New Prisma-style destructive ops don't exist here, but the same spirit:
   never delete learned-word storage; skip-on-load is the only sanitation.

---

## 6. Where to look things up

- Round-by-round history + lessons: `git log --oneline` (S13…S43 messages are
  mini design docs) and the auto-memory file `banglu-ship-backlog.md`.
- Engine research method & corpus harness: docs/engine-*-study-*.md.
- Store submission pack: design/play-store/ (listing, privacy, data safety,
  screenshots). Privacy policy live at
  https://shahabul87.github.io/banglu-privacy-policy/ (source: PRIVACY-POLICY.md).
- Pending strategic items: web-engine parity decision, corpus archiving,
  Play upload (user-side), iOS phase, trigram quality round, per-word
  "never learn this word" control (Release-A candidate).
```
