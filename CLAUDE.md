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

**Current status (2026-07-15):** ONE ENGINE, FIVE SURFACES.
- **Android** v1.5.34 (2071), db 3.8.5 (S52: acronym layer + English tail) — pre-launch; Play upload is the
  pending user action (`releases/banglu-1.5.33-2070.aab`).
- **Desktop editor (বাংলু এডিটর)** v1.1.0 — SHIPPED PUBLICLY (S48–S50):
  installers for macOS/Windows/Linux on the GitHub release `desktop-v1.1.0`,
  download page live at https://www.craftsai.org/products/banglu.
- **Browser extension** (S47) — Chrome/Firefox zips built
  (`browser-extension/banglu-*.zip`), store uploads pending (user).
- **macOS input method (বাংলু ইনপুট মেথড)** (S51) — built + installed on the
  dev Mac, awaiting the user's manual acceptance gate (app matrix).
- **iOS** — future phase; the old `ios-keyboard-engine` Swift scaffold is
  NOT a base for anything (seed-only, never paritied).

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
│       ├── jvmTest/         FULL-STORE tests — the real regression wall:
│       │                    parity pins, S26/S27/S33/S34/S35/S43 round tests,
│       │                    ConjunctSolutionRoundJvmTest.engine = shared
│       │                    engine loaded from ./dictionary.sqlite
│       ├── jvmMain/         JvmSqlitePhoneticIndexStore + loader (desktop
│       │                    editor AND jvmTest share these)
│       ├── jsMain/          BangluWebEngine.kt — @JsExport facade (initSeed,
│       │                    attachSlimDictionary, convert, suggestions,
│       │                    instantPreview, applyLearnedWords, recordPick).
│       │                    Consumed by browser-extension AND macos-ime.
│       └── jsTest/          S45 web-parity wall + S51 learning tests
│                            (gate: ./gradlew :shared:jsNodeTest)
├── shared/banglu-slim.json      ← S45 slim in-memory dictionary (17MB;
│                                  1.8MB gz) for JS surfaces — regenerate via
│                                  dictionary-compiler --slim; untracked
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
├── desktop-app/                 ← বাংলু এডিটর (S48–S50): Compose Desktop app,
│   │                              FULL engine (JVM + 143MB sqlite via
│   │                              resources/common/, gitignored)
│   ├── src/main/kotlin/com/banglu/desktop/
│   │   ├── Main.kt              window/tray/hotkey wiring; --tutorial arg
│   │   ├── Hotkey.kt            ⌘⇧B/Ctrl+Shift+B via jkeymaster (OS hotkey
│   │   │                        API — NO permissions; never JNativeHook)
│   │   ├── Paste.kt, Storage.kt (FileStorage → ~/.banglu/learned.json)
│   │   └── editor/              EditorState (pure-Kotlin state machine),
│   │                            EditorScreen (Compose UI), EngineFacade,
│   │                            DraftStore (~/.banglu draft+prefs, atomic),
│   │                            DocxWriter (hand-written OOXML), Printer
│   │                            (Java2D — the ONLY Java path that shapes
│   │                            Bangla; never add a PDF library),
│   │                            TutorialView (Android curriculum ported),
│   │                            EditorTheme (brand palette + bundled Noto
│   │                            Sans Bengali v2.003, OFL)
│   └── src/test/                29+ JVM tests on the REAL dictionary incl.
│                                WYSIWYG pin tests
├── macos-ime/                   ← বাংলু ইনপুট মেথড (S51): real InputMethodKit
│   │                              input method — type Bangla in ANY app.
│   │                              Swift SPM, NO XCODE on this machine
│   │                              (CommandLineTools only — never add
│   │                              .xcodeproj; `swift test` FAILS here, the
│   │                              gate is `swift run BangluCoreTestRunner`)
│   ├── Sources/BangluCore/      EngineJS (shared JS engine hosted in
│   │                            JavaScriptCore), BackgroundEngine (seed-echo
│   │                            until the ~11s slim load finishes on a
│   │                            serial queue — JSC is NOT thread-safe),
│   │                            Composer (pending-space দাঁড়ি model — IMK
│   │                            can't edit committed text), LearnedStore
│   │                            (editor-shared ~/.banglu brain), AppCompat
│   │                            (per-app full/plain mode table)
│   ├── Sources/BangluIME/       IMKInputController glue + caret-anchored
│   │                            NSPanel candidate UI (custom panel is
│   │                            PRIMARY; IMKCandidates seam kept)
│   ├── Tests/BangluCoreTestRunner/  THE test gate (83 checks, real engine)
│   ├── scripts/build-engine.sh  gradle JS build + esbuild IIFE bundle
│   └── Makefile                 make install → ~/Library/Input Methods/
│                                (ad-hoc signed; Developer ID = v2/public)
├── browser-extension/           ← Chrome/Firefox extension (S47, MV3):
│   │                              inline typing in input/textarea + popup
│   │                              converter, SAME shared engine as JS
│   ├── build.sh                 pulls shared JS artifact + slim json into
│   │                            vendor/, esbuild bundle
│   └── banglu-chrome.zip, banglu-firefox.zip  (store uploads pending)
├── .github/workflows/desktop-release.yml  ← CI: DMG/MSI/DEB on 3 runners,
│                                  triggered by desktop-v* tags; downloads
│                                  dictionary.sqlite from the GitHub release
│                                  tagged `dictionary` (150MB asset)
├── ios-app/, ios-keyboard-engine/  ← iOS scaffold (future phase; market gap:
│                                     SwiftKey has NO iOS transliteration.
│                                     NOT a base — seed-only, unparitied)
├── banglu-web (SIBLING REPO ../banglu-web)  ← wordlist source for compiler +
│                                     web app — FULLY on the shared JS engine
│                                     since S54 (lib/banglu-engine vendor +
│                                     loader.ts; old TS engines decommissioned
│                                     — see its CLAUDE.md engine-law section)
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

### 3.7 Multi-platform engine delivery (ONE engine, many hosts)

The rule that makes five surfaces manageable: **conversion behavior lives in
`shared` (Kotlin) and NOWHERE else.** Platforms differ only in how they host
the engine and which dictionary tier they carry:

| Surface            | Engine host                     | Dictionary            |
|--------------------|---------------------------------|-----------------------|
| Android IME        | JVM/ART, adapter singleton      | full sqlite (143MB), lite fallback |
| Desktop editor     | JVM (Compose Desktop, jpackage) | full sqlite via JDBC  |
| Browser extension  | Kotlin/JS in the page/worker    | slim JSON (17MB mem)  |
| macOS input method | Kotlin/JS in JavaScriptCore     | slim JSON (17MB mem)  |
| Web (banglu-web)   | Kotlin/JS in page + Node routes | slim JSON (17MB mem)  |

- JS artifact: `./gradlew :shared:jsBrowserProductionLibraryDistribution` →
  `shared/build/dist/js/productionLibrary/banglu-engine.js` → esbuild bundle
  (extension: ESM; macOS IME: IIFE `--global-name=BangluNS`; banglu-web
  vendors the raw library + loader.ts). JS access path:
  `(ns.com ?? ns).banglu.engine.BangluWebEngine`. S54 exports the full web
  surface: parse, convertWithContext, suggestionsWithContext,
  compositionPreview, nextWordPredictions, addCustomWord.
- Parity walls: JVM = `:shared:jvmTest` (475) on ./dictionary.sqlite;
  JS = `:shared:jsNodeTest` (379, incl. S45 web-parity pins); macOS IME =
  `swift run BangluCoreTestRunner` (83 checks incl. WYSIWYG pins on the real
  JSC-hosted engine); desktop = `:desktop-app:test` (30) on the real sqlite.
- Learning writes go through `SmartEngine.addWord`, which is gated by
  `isPlausibleDynamicMapping` (anti-poisoning: key must phonetically overlap
  the reverse-transliteration of the Bengali). NEVER bypass it — learned.json
  is user-editable; real picks pass it by construction. Test fixtures must
  use plausible pairs (jbo→যাবো class), never junk keys.

### 3.8 Desktop editor (বাংলু এডিটর — S48–S50 architecture)

- `EditorState` = pure-Kotlin typing state machine (no Compose imports),
  JVM-tested keystroke-by-keystroke on the real dictionary. UI renders
  `display`, routes every field change through `applyEdit`, async engine
  refinement lands via generation-guarded `refine` (LaunchedEffect keyed on
  `generation` — the cancellation IS the stale-result guard).
- WYSIWYG: space commits EXACTLY the visible forming word; double-space dari;
  digits ০-৯; popup picks teach only non-primary choices; click any committed
  word to fix (the AI seam: wordRangeAt/candidatesForCommitted/
  replaceCommitted — future AI proposes the same "swap segment" ops).
- Never lose text: 2s-debounce autosave + DisposableEffect flush on window
  dispose + DraftFlush hook before tray-quit. All ~/.banglu writes are
  tmp + atomic replace (renameTo silently fails on Windows — always
  Files.move REPLACE_EXISTING).
- Packaging landmines (each cost a broken install): shared jvmTarget PINNED
  to 17 (Gradle daemon is JBR 21 → class-file 65 crashes the Temurin-17
  jpackage runtime); jpackage needs modules java.sql/instrument/management/
  jdk.unsupported; never reinstall from a stale mounted DMG volume.
- Exports: docx = hand-written OOXML with w:cs complex-script fonts (Word
  shapes Bangla itself); PDF = ⌘P → OS dialog via Java2D (direct-PDF Java
  libraries CANNOT shape Bengali conjuncts — never add one).

### 3.9 macOS input method (বাংলু ইনপুট মেথড — S51 architecture)

- IMK app in `~/Library/Input Methods/`; marked text shows the live-forming
  Bangla; commits via insertText. No click-to-fix (committed text belongs to
  the host app — platform contract, same as Avro/Pinyin).
- **Pending-space দাঁড়ি model** (IMK can't edit committed text): space after
  a word commits the word and HOLDS the space; next space → `। `; a letter →
  `" "`; tight punctuation (`,` `।` `?` `!` — tested on the MAPPED char, `.`
  maps to `।` first) swallows it; Enter/Tab/focus-loss drop it.
- **BackgroundEngine**: JSC context lives on a dedicated serial queue (JSC is
  not thread-safe); slim load takes ~11s, so until `ready` the IME echoes raw
  input (Android S29 cold-start pattern). Never call EngineJS off its queue.
- Per-app compat: `appcompat.json` maps bundle IDs to `plain` mode (no marked
  text; preview lives in the candidate panel only; commits via insertText) —
  the escape hatch for misbehaving Electron hosts.
- Distribution v1 = ad-hoc signing, dev Mac only. Public = Apple Developer
  ID ($99/yr) + notarization — a deliberate later decision.

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

### Per-platform build & test gates (run before ANY "done" claim)
- Android + engine: `./gradlew :shared:jvmTest :shared:testDebugUnitTest`
- JS surfaces: `./gradlew :shared:jsNodeTest` (then rebuild the library
  distribution if a JS consumer ships)
- Desktop: `./gradlew :desktop-app:test` then `:desktop-app:packageDmg`;
  install via ditto from the app image (never a stale mounted DMG);
  Windows/Linux installers come from CI (`git tag desktop-vX.Y.Z && git push
  origin desktop-vX.Y.Z` → artifacts on the Actions run; attach to a GitHub
  release for public URLs)
- macOS IME: `cd macos-ime && swift run BangluCoreTestRunner` (83 checks;
  **`swift test` does NOT work on this machine** — no XCTest in
  CommandLineTools, and the CLT Swift Testing helper silently runs zero
  tests; the runner is the only trusted gate) then `make install`
- Extension: `./browser-extension/build.sh` after any engine change

### Distribution surfaces (where users get Banglu)
- Desktop installers: GitHub release `desktop-v1.1.0` on Shahabul87/banglu-kmp
  (.msi/.dmg/.deb, permanent CDN URLs). The 150MB dictionary asset for CI
  lives on the release tagged `dictionary`.
- Public download page: https://www.craftsai.org/products/banglu — source is
  the SIBLING firm repo `~/myprojects/bdaiwebfirm/bd-ai-web-firm` (Next.js +
  velite; product schema has an optional `downloads:` list — new versions
  only edit `content/products/banglu.mdx`; that repo uses PR workflow, merge
  = production deploy).
- Android: Play Console (pending user upload). Extension: Chrome Web Store /
  Firefox AMO (pending user upload).

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
9. Conversion behavior lives ONLY in `shared` — no platform re-implements
   rules (the old ios-keyboard-engine and the two decommissioned banglu-web
   TS engines are cautionary tales, not patterns; banglu-web has been fully
   on the shared engine since S54).
10. `~/.banglu/learned.json` is the ONE learning brain for desktop + macOS
    IME — rows are `{p,b,f,t}` exactly (Storage.kt is the source of truth);
    every writer uses read-fresh → tmp → atomic replace.
11. `isPlausibleDynamicMapping` (the addWord anti-poisoning guard) is never
    bypassed — not in code, not in tests.
12. Desktop/IME privacy = same law as Android: no network entitlement, no
    sockets, typing never leaves the machine.

---

## 6. Where to look things up

- Round-by-round history + lessons: `git log --oneline` (S13…S51 messages are
  mini design docs) and the auto-memory file `banglu-ship-backlog.md`.
  Recent rounds: S45 slim dictionary + JS parity, S47 browser extension,
  S48–S50 desktop editor (spec + plan in docs/superpowers/), S51 macOS input
  method (spec + plan in docs/superpowers/).
- Design specs & implementation plans: docs/superpowers/specs/ and
  docs/superpowers/plans/ (the editor and macOS IME were built plan-driven
  with per-task adversarial review — the plans double as architecture docs).
- Engine research method & corpus harness: docs/engine-*-study-*.md.
- Store submission pack: design/play-store/ (listing, privacy, data safety,
  screenshots). Privacy policy live at
  https://shahabul87.github.io/banglu-privacy-policy/ (source: PRIVACY-POLICY.md).
- Pending strategic items: macOS IME manual gate + one-click-assistant
  verification + public signing decision, Play upload (user-side), extension
  store uploads (user-side), banglu-web src/engine/smart deletion after its
  uncommitted dictionary-override WIP is harvested into the compiler data,
  corpus archiving, iOS phase, trigram quality round, per-word "never learn
  this word" control (Release-A candidate).
```
