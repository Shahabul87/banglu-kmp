# Banglu KMP Engine — Production-Readiness Audit
Date: 2026-08-17
Scope: shared engine core + all five delivery surfaces (Android IME v1.5.66/2103, desktop editor, browser extension, macOS IME, web)
Method: 4 parallel read-only audit agents (engine core, Android hot path, security/privacy, multi-surface delivery) + all five test gates force-rerun fresh + load-bearing claims independently re-verified by hand.

## Verdict

**The Android IME is production-ready.** No critical findings on the Android surface: the keystroke contract is honored in code, the privacy promise is enforced by the merged release manifest (zero network permissions), and all test gates pass fresh.

**The multi-surface delivery layer is NOT fully production-ready.** Two criticals live outside Android: dictionary version drift with no version gate on the JS/desktop surfaces (observed live on disk today), and a silent permanent failure mode in the macOS IME. The shipped desktop editor also has a crash path and a learning-brain corruption path.

## Test evidence (all force-rerun 2026-08-17, zero failures)

| Gate | Result |
|---|---|
| `:shared:jvmTest` (full store, ./dictionary.sqlite) | 626 tests, 0 failures |
| `:shared:testDebugUnitTest` | 414 tests, 0 failures |
| `:shared:jsNodeTest` | 427 tests, 0 failures |
| `:desktop-app:test` (real sqlite) | 37 tests, 0 failures |
| `swift run BangluCoreTestRunner` (real JSC engine) | 83 checks, all passed (ready-latency measured 15.3s, doc says ~11s) |

Caveat: the JVM wall tested db **3.9.2** while the JS wall tested slim **3.9.1** — "both walls green" does not currently certify cross-surface parity (see C1).

---

## CRITICAL

### C1 — Dictionary version drift across surfaces; only Android gates on db version (VERIFIED on disk)
- Compiler writes `"3.9.2"` (`dictionary-compiler/.../DictionaryCompiler.kt:461`); `AndroidDictionaryLoader.kt:34` requires `3.9.2` and re-copies on mismatch; `SqlitePhoneticIndexStore.kt:53-61` rejects too. **Android: CLEAN.**
- **Observed drift right now**: `dictionary.sqlite` + Android asset = **3.9.2**; `shared/banglu-slim.json`, `macos-ime/Resources/built/banglu-slim.json`, `browser-extension/vendor/banglu-slim.json` = **3.9.1**; shipped store zips `banglu-chrome.zip`/`banglu-firefox.zip` (2026-08-05) = **3.8.10** — three rounds behind.
- `BangluWebEngine.attachSlimDictionary` (`shared/src/jsMain/.../BangluWebEngine.kt:30-44`) deserializes the slim's `version` field and never reads it; no JS host checks it; `browser-extension/build.sh:9` and `macos-ime/scripts/build-engine.sh:13` blind-copy whatever slim is on disk. Desktop has no version check either (`JvmSqliteDictionaryLoader`/`JvmSqlitePhoneticIndexStore`: nothing) and `EditorScreen.kt:216-222` unconditionally shows "পূর্ণ অভিধান ✓".
- Fix: version-equality assert in both build scripts + reject mismatched slim in `attachSlimDictionary` (or per host) + a desktop version probe. Regenerate slim from 3.9.2 and rebuild the extension zips before store upload.

### C2 — macOS IME: engine init failure is silent and permanent (VERIFIED)
- `BackgroundEngine.swift:15` — `let e = try? EngineJS(...)`; on any failure (malformed slim, missing bundle — `main.swift:24` falls back to `URL(fileURLWithPath: "/nonexistent")`) `impl` stays nil but `ready` is still flipped true (line 18). `convert` then echoes raw input forever: no log (the `os` import is unused), no retry, no user-visible signal. The user experiences "keyboard types English back" with no explanation.
- Fix: distinct `failed` state, `os.Logger` output, candidate-panel notice.

---

## HIGH-PRIORITY WARNINGS (desktop editor — shipped publicly as v1.1.0)

### W1 — Desktop runs concurrent conversions on a non-thread-safe engine (VERIFIED)
`EditorScreen.kt:241-249` refines via `LaunchedEffect(generation)` + `withContext(Dispatchers.Default)`. Cancellation is cooperative and `convertWord` never suspends, so a superseded refine keeps running while the next starts — two threads inside one `SmartEngine`. commonMain's reentrancy flags (`SmartEngine.kt:3312/3328/3451/3764`) are plain vars; `PhoneticTrie.addChild` (`PhoneticTrie.kt:37-41`) replaces parallel arrays non-atomically → possible wrong output, `ArrayIndexOutOfBoundsException`, or `ConcurrentModificationException` when learning inserts race reads. Android is immune via `engineLane = Dispatchers.Default.limitedParallelism(1)` (`BangluIMEService.kt:132`). Fix: mirror the single-parallelism lane on desktop.

### W2 — JVM SQLite store has zero error handling; SmartEngine has zero catch blocks (VERIFIED: 0 `catch` in `JvmSqlitePhoneticIndexStore.kt`)
A `SQLException` mid-conversion propagates uncaught out of the desktop refine coroutine. Android's store catches everything and degrades to rule fallback (`android SqlitePhoneticIndexStore.kt:101-118,183-191`); the JVM store shares one JDBC `Connection` across threads with no catch. Also: missing sqlite at launch throws inside `LaunchedEffect` (uncaught); `JvmSqlitePhoneticIndexStore.kt:18-21` `isAvailable` is dead code (JDBC creates an empty file first). Fix: same catch-and-return-empty pattern as Android, plus an `EngineFacade` error boundary.

### W3 — Desktop `learned.json` write is non-atomic, violating invariant #10 (VERIFIED: `Storage.kt:40,50` plain `writeText`)
Crash mid-write corrupts the file; `readAll()` swallows the parse error into an empty list; the next save persists the empty list — silent permanent loss of the shared desktop+macOS-IME learning brain. DraftStore already does tmp + `Files.move(REPLACE_EXISTING)`; LearnedStore.swift does `replaceItemAt`. Fix: route through the same pattern. Related: desktop and macOS IME both write `~/.banglu/learned.json` from different processes with no file lock — read-fresh-then-write-whole-array is last-writer-wins; small window, real on the dev Mac.

---

## WARNINGS (Android — none block release)

- **A1 — Leaked ~163MB `dictionary.sqlite.tmp` on failed asset copy** (`AndroidDictionaryLoader.kt:57-80`; found independently by two agents). The catch block never deletes the tmp; a disk-full device permanently loses that space and re-truncates every cold start. No free-space precheck (`StatFs`). Keyboard correctly stays working on seeds (S70 hardening confirmed). One-line fix + optional precheck.
- **A2 — Fast-commit reconcile converts with self-referential context** (`BangluIMEService.kt:3824-3831`): `updatePredictions(committedNow)` mutates `lastCommittedBengali` before the reconcile's `safeConvertWithContext` reads it, so the authoritative conversion reranks against its own preview instead of the two true previous words; cached-commit and fast-commit paths can disagree. Fix: snapshot the context before `updatePredictions` (the `previousWord` capture at :3824 already models this).
- **A3 — Adapter preference maps are a cross-thread data race** (`SmartEngineAdapter.kt:31-37,173-178,499-505,671-699`): plain HashMaps written on Main during init/learning-config, read/written on engineLane; also plain vars `storage`, `engineFullyLoaded`, `learningEnabled`, etc. Get-only reads make a crash unlikely (torn/stale reads during init window), but it's a genuine JMM race in the singleton. Cheap fix: `runSynchronized` in the three touch points. Related latent: `setUserBigrams` (`SmartEngine.kt:5973-5985`) mutates without the lock every other accessor takes — only safe because production paths call it pre-publication.
- **A4 — Telemetry keys storm the settings listener** (`BangluIMEService.kt:316-321` + `diag_*` writes in the same prefs file): a session-end flush triggers up to ~22 `reloadSettings()` runs on main. Waste, not a stall; move diagnostics to a separate prefs file or prefix-filter the listener.
- **A5 — Instant-preview "zero I/O" is enforced only by a soft timing test** (`S27ChatConjunctSpellingJvmTest.kt:51-58`, <1000µs/call). Code verified genuinely zero-I/O today (immutable tables only). Add a structural pin: a store stub that throws on access.

## WARNINGS (security/privacy/compliance — Play upload items)

- **S1 — Data Safety form internally inconsistent for the current build** (`design/play-store/DATA-SAFETY-FORM.md:12,14,19,25,28`): declares Email/Name/sync/purchase collection, but the shipping bundle has **no INTERNET permission at all** (verified in the merged release manifest + merger report: `:android_account`'s re-adds were REJECTED). Either declare only Audio (voice) for this release or keep forward-looking answers knowingly; reconcile the "collects=Yes" vs "deletion=N/A" contradiction. Do this before Play upload.
- **S2 — No `-assumenosideeffects` Log stripping in `proguard-rules.pro`.** All input-bearing logs are DEBUG-gated (verified) and die by constant folding; the residual channel is the unconditional `Log.w` at `BangluIMEService.kt:698` logging `e.message.take(120)` — safe only while exception messages never embed input. Add the standard rule.
- **S3 — `verifyImePrivacyBoundary` blind spots** (`android-keyboard/build.gradle.kts:103-207`): whitelist of 9 files (11 default-process files unscanned, incl. `AndroidStorage.kt`, `BangluPrefsProvider.kt`); silently skips missing files; token list misses Socket/ktor/okhttp/HttpClient; the whole `shared` module unscanned. Real backstop today is the absent INTERNET permission. Harden when convenient.
- **S4 (INFO cluster)**: `shared/build.gradle.kts:50` dead `ktor-client-okhttp` dep (jvmMain only, unused — remove); `AuthSessionStore.kt:28` writes `banglu_prefs` directly from :ui instead of via the provider (latent cross-process clobber when the account feature ships); Google datatransport components would run in the default process if INTERNET ever returns; clipboard history persisted Base64-in-private-prefs (12×1000 chars, `BangluIMEService.kt:4072-4078`) — contained by allowBackup=false, know it exists.

## INFO

- CLAUDE.md's version strings ("3.8.6"/db 3.8.7) are stale; actual is 3.9.2. macOS slim ready-latency measured 15.3s vs documented ~11s.
- `englishLearningLoaded`/`identityLoaded` flags set true before the load completes and never retried on failure (`SmartEngineAdapter.kt:544-548,575-579`).
- Engine rebuild on settings flip pays ~650ms synchronous seed build inside a conversion — documented deliberate memory trade-off.
- In-session unbounded growth (user-bigram outer keys, learned-word trie) is bounded only at the persistence layer (800/500 caps on reload) — fine in practice.
- `macos-ime/Tests/BangluCoreTests/EngineJSTests.swift` XCTest mirror can't run on this machine and drifts from the runner by hand-sync.
- 5 batch-edit pairs not in try/finally; voice final-refine patch lacks a batch edit (flicker only).

## Verified CLEAN (the strong parts)

- **Privacy promise is architecture, not policy**: merged RELEASE manifest contains only VIBRATE + RECORD_AUDIO; INTERNET/NETWORK_STATE/BILLING removals win over `:android_account` (merger report confirms REJECTED); the only two network call sites are `requireUiProcess`-guarded :ui classes; no Firebase/analytics/crash SDK anywhere; `allowBackup="false"` keeps learned words/clipboard/identity out of cloud backup.
- **Keystroke contract honored**: `convertForInstantPreview` verified line-by-line zero-I/O; every engine call on `engineLane`; no `runBlocking`/`GlobalScope`; zero `!!` in the keyboard package; all 15 commonMain `!!` sites guarded; every InputConnection use null-safe; all five async-apply sites token+snapshot guarded — no sequence found where stale results destroy user text.
- **Engine swap sound**: `@Volatile engine`, fresh instance built off-thread, published under monitor, captured once per call.
- **All caches bounded and locked** (LruCache 2000/128; candidates 40; english/identity hard caps).
- **Anti-poisoning guard unbypassed**: `isPlausibleDynamicMapping` guards the sole `dictionary.addMapping` call site; all surfaces funnel through `addWord` (note: public API makes future bypass possible — convention-protected).
- **Voice**: OS SpeechRecognizer only, no AudioRecord/MediaRecorder anywhere, runtime permission + Bengali prominent disclosure before the permission prompt, package-scoped broadcasts.
- **Coroutine/resource lifecycle**: serviceScope cancelled in onDestroy; per-session jobs cancelled in cleanupImeSession from all three hide paths; receiver/listener/recognizer/store all released.
- **ProGuard/R8**: minimal, sane, no suspicious keeps; R8 + resource shrink on for release.
- Asset copy is tmp+rename (partial file can't land in place); version probe catches truncation; keyboard degrades to seeds on copy failure with telemetry.

## Prioritized fix list

1. **C1**: version gate for slim consumers + build-script asserts; regenerate slim at 3.9.2; rebuild extension zips before store upload.
2. **C2**: BackgroundEngine failed-state + logging + user signal.
3. **W1+W2** (one small change): desktop `limitedParallelism(1)` lane + catch-and-empty in the JVM store.
4. **W3**: atomic write for desktop `learned.json`.
5. **S1**: reconcile Data Safety form before Play upload.
6. **A1**: delete tmp in catch + StatFs precheck.
7. **A2, S2, A3, A5, S3, A4** in that order, at leisure.
