# Banglu Android Production-Readiness Audit

**Audit date:** 2026-08-26  
**Audited revision:** `0d3fc9ba4a1254a75d5267b0422e1b508a4399fb` (`v1.5.81`)  
**Android identity:** `com.banglu.keyboard`, version `1.5.81` (`2118`)  
**Scope:** Android IME, launcher/settings/tutorial/voice UI, dynamic account feature, shared engine integration, privacy/security, accessibility, performance/memory, testing, release engineering, supply chain, Play policy/store assets, signing, operations, and the generated release artifacts.  
**Method:** read-only source and artifact audit, three parallel specialist reviews, direct spot-verification of load-bearing findings, a full local release gate, release APK/AAB inspection, and verification of current Android/Google Play requirements. No application source was changed.

## Executive verdict

**The Android app is functionally mature and has a strong offline/privacy architecture, but the audited revision is not ready for a public production upload yet.** The build, unit tests, lint gate, signing, manifest, shrinking, package size, and 16 KiB binary checks pass. The release should nevertheless be held for two blockers and six high-priority issues:

1. “Clear learned data” can report success without deleting the persisted learning data.
2. The staged `releases/banglu-1.5.81-2118.aab` identifies an older source revision, not the `v1.5.81` tag.
3. Clipboard history can persist secrets from sensitive/private fields.
4. Email addresses are learned and surfaced under controls/disclosures that do not accurately describe that behavior.
5. Custom keyboard keys do not expose accessibility click actions.
6. There is no Android instrumentation or exact-AAB device certification gate.
7. The upload key and local signing properties are readable beyond the file owner.
8. The release privacy/store declarations do not match the current artifact and local data behavior.

The user’s long-running device testing is valuable functional evidence. This audit does not contradict that observation: most identified problems are privacy-control, accessibility, provenance, release-reproducibility, and edge-case defects that ordinary typing tests are unlikely to expose.

## Severity model

- **P0 — Release blocker:** must be corrected and re-verified before uploading this version.
- **P1 — High:** must be corrected before production rollout; material privacy, accessibility, security, or escaped-regression risk.
- **P2 — Medium:** should be completed before broad rollout or placed behind an explicit, time-bounded risk acceptance.
- **P3 — Low:** maintainability or polish improvement; not by itself a launch blocker.

## What passed

### Build and automated verification

`./scripts/validate_android_release.sh` completed successfully in 40 seconds. It ran:

- `:android-keyboard:verifyImePrivacyBoundary`
- `:shared:allTests`
- `:android-keyboard:lintRelease`
- `:android-keyboard:testDebugUnitTest`
- `:android-keyboard:testReleaseUnitTest`
- `:android-keyboard:assembleRelease`
- `:android-keyboard:bundleRelease`

Recorded test results:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Android keyboard debug | 122 | 0 | 0 | 0 |
| Android keyboard release | 122 | 0 | 0 | 0 |
| Shared JVM | 669 | 0 | 0 | 0 |
| Shared JavaScript/Node | 430 | 0 | 0 | 0 |
| Shared Android debug | 414 | 0 | 0 | 0 |
| Shared Android release | 414 | 0 | 0 | 0 |

Release lint reported **0 errors**, with 54 warnings and 5 informational findings. A separate specialist rerun of the Android test/lint tasks with `--rerun-tasks` also completed successfully.

### Release artifact

| Check | Observed result |
|---|---|
| Fresh release APK | 64,617,045 bytes; SHA-256 `ae57f2b1a8b9ee1132e5aa29201487fa95bb6fa9b42dd6433d072cf73a1dc26c` |
| Fresh release AAB | 67,792,107 bytes; SHA-256 `803a2b648d8ce0ba262562475e1369b75b083f97effc687001cd7a05986fa31d` |
| Release optimization | R8 minification and resource shrinking enabled |
| APK signing | One signer; APK Signature Scheme v2 verified |
| APK ZIP alignment | `zipalign -c -P 16 -v 4` passed |
| Native ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Native ELF alignment | Every native library PT_LOAD segment is 16 KiB-aligned |
| Target/compile SDK | 36 |
| Minimum SDK | 24 |
| Effective permissions | VIBRATE, RECORD_AUDIO, and the app’s signature-protected dynamic receiver permission; no INTERNET, ACCESS_NETWORK_STATE, or BILLING |
| Dynamic feature | `android_account` present as an install-time feature |
| Dictionary | Present, metadata version `3.9.6`; local asset SHA-256 `a8a4f65908f299dd3ec4a6a139a219cf19ac63896625dce7926be1ed040c727e` |

The artifact is below Google Play’s 200 MB per-device compressed download limit. The project targets API 36, satisfying the Android 16 target requirement that takes effect for new apps and updates on August 31, 2026. The native artifact alignment also satisfies the current 16 KiB page-size requirement. See [Play app size limits](https://support.google.com/googleplay/android-developer/answer/9859152), [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878), and [16 KiB page-size support](https://developer.android.com/guide/practices/page-sizes).

### Security architecture

- The IME hot path has no effective Internet permission.
- The exported IME service is protected by `android.permission.BIND_INPUT_METHOD`.
- Settings, voice permission, tutorial, account activity, and preferences provider are not exported.
- Network/account classes are separated into the `:ui` process, and the privacy-boundary task scans the IME/shared source for forbidden network clients.
- Backups are disabled, and both cloud-backup and device-transfer rules exclude preferences, files, and databases.
- Voice disclosure occurs before requesting microphone permission and explains processing by the device speech provider.
- No analytics, advertising, crash-reporting SDK, WebView bridge, permissive TLS trust manager, external-storage persistence, or tracked private key was found.
- Release logging strips verbose/debug typed-text logs.

## Required findings

### F-001 — P0 — “Clear learned data” does not reliably delete persisted learning

**Status:** OBSERVED in the current release path.

**Evidence**

- `SettingsActivity` runs in `:ui` (`android-keyboard/src/main/AndroidManifest.xml:104`).
- `BangluSettingsScreen` creates an `AndroidStorage` instance but does not attach it to the shared engine (`SettingsActivity.kt:175-180`).
- The confirmation action calls `SmartEngineAdapter.eraseAllLearning()` and always displays a success toast (`SettingsActivity.kt:548-560`).
- `SmartEngineAdapter.storage` is nullable, process-local state (`SmartEngineAdapter.kt:27-35`), and erasure uses `storage?.clearAllLearningData()` (`SmartEngineAdapter.kt:556-568`).
- Storage is attached from `BangluIMEService` in the default IME process (`BangluIMEService.kt:994-1003`), not in the separate UI process.
- Persisted learned words, custom conversions, bigrams, English learning, and saved identities live in `banglu_learning` (`AndroidStorage.kt:27-40,126-137`).

**Root cause**

Android processes do not share Kotlin singleton memory. The `:ui` process gets a separate `SmartEngineAdapter`; its `storage` remains null. The null-safe call silently skips persistent deletion, after which the UI reports success.

**Impact**

The app’s deletion control and Play/privacy statements are false in this path. Personal learning, including saved email identities, can remain after the user is told it was removed.

**Fix**

1. Add a typed deletion operation to the non-exported `BangluPrefsProvider`, or use a private bound service hosted in the default IME process.
2. In that process, delete all scoped learning keys and purge the live `SmartEngineAdapter` state.
3. Return an explicit success/failure result. Show the success toast only after confirmed completion.
4. Add an Android instrumentation test that seeds every category, triggers deletion from `:ui`, checks the preference file through the authoritative process, and verifies both a running and cold IME engine no longer surface the data.

### F-002 — P0 — The staged 1.5.81 AAB has incorrect source provenance

**Status:** OBSERVED in the existing upload candidate.

**Evidence**

- `v1.5.81` and current `HEAD` are `0d3fc9ba4a1254a75d5267b0422e1b508a4399fb`.
- `releases/banglu-1.5.81-2118.aab` embeds revision `2be59babb606337c24e423ebcb7c1547a65debd6` in `base/root/META-INF/version-control-info.textproto`.
- A fresh AAB embeds the correct `0d3fc9b...` revision.
- The staged AAB is ignored/manual; CI tests the source but does not build or publish the Android release AAB (`.github/workflows/ci.yml:64-87`, `.gitignore:32`).

**Root cause**

The staged file appears to have been built while later source changes were still uncommitted, then retained after the release tag moved to the final commit. Even if functional bytecode happens to match, the artifact does not provide trustworthy source-to-binary traceability.

**Impact**

Incident response, rollback, and release auditability cannot prove that the Play upload came from the signed/tagged revision.

**Fix**

1. Do not upload the existing staged AAB.
2. After all blockers are fixed, build from a clean, immutable tagged revision.
3. Make the release gate assert: clean worktree, tag/version match, and embedded revision equals `HEAD`.
4. Publish the AAB SHA-256, revision, version, R8 mapping, and build metadata together.
5. Prefer a protected, tag-triggered CI release job with provenance/attestation and protected signing credentials.

### F-003 — P1 — Clipboard history persists sensitive content without a sensitive-field guard or expiry

**Status:** OBSERVED.

**Evidence**

- Opening the panel always loads history and captures the current clipboard (`BangluIMEService.kt:3712-3721`).
- Pasting re-adds the pasted value to history (`BangluIMEService.kt:3723-3733`).
- Clipboard text is read without checking `ClipDescription.EXTRA_IS_SENSITIVE` (`BangluIMEService.kt:4141-4150`).
- Up to 12 entries of 1,000 characters are stored indefinitely as reversible Base64 in ordinary SharedPreferences (`BangluIMEService.kt:420-421,4153-4198`).
- These paths do not check `privateInputMode` or `sensitiveInputMode`.

**Root cause**

The history feature treats opening the clipboard panel as permission to persist any current clip, independent of the editor’s sensitivity and the clip’s Android sensitivity metadata.

**Impact**

Passwords, one-time codes, recovery codes, payment data, tokens, or private messages can remain visible and at rest in keyboard history. App-private storage and disabled backup reduce exposure, but Base64 is not encryption and no retention limit exists.

**Fix**

1. Honor both modern and legacy sensitive clipboard flags and never persist flagged content.
2. In password, OTP, URI/email-private, or `IME_FLAG_NO_PERSONALIZED_LEARNING` fields, permit at most a one-shot paste without loading, displaying, or persisting history.
3. Make persistent history an explicit opt-in, timestamp entries, and apply a short documented expiry.
4. If persistent history remains, encrypt it with Android Keystore-backed authenticated encryption.
5. Clear sensitive in-memory state on field transition/screen lock and verify explicit deletion.
6. Add instrumentation tests for sensitive clips and every protected input class.

Android’s clipboard security guidance identifies sensitive clipboard metadata and clipboard retention as protections against disclosure: [Secure clipboard handling](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling).

### F-004 — P1 — Email identity learning is not controlled or disclosed accurately

**Status:** OBSERVED.

**Evidence**

- Email fields are classified as private/raw input (`BangluIMEService.kt:582-640`).
- Identity assist deliberately bypasses `privateInputMode`; `identityAssistAllowed()` checks only IME visibility, suggestions, and `!sensitiveInputMode` (`BangluIMEService.kt:1781-1785`).
- Completed `@` tokens are recorded on space/enter and saved fills are surfaced in email fields (`BangluIMEService.kt:1786-1849,2030-2150`).
- `recordIdentity` is gated by `personalDictionaryEnabled`, which defaults to true (`SmartEngineAdapter.kt:642-649`, `SettingsActivity.kt:186`).
- Turning the personal-dictionary switch off stops new recording but does not clear or prevent loading/surfacing existing saved identities.
- The setting describes names, places, and personal words, not stored email addresses (`SettingsActivity.kt:330-331`).
- The live privacy policy and repository policy disclose learned words and account email, but not automatic on-device keyboard-level email retention.

**Root cause**

Identity assist was added as an exception to the general private-input rule, but it reused a broad default-on personal-dictionary setting and never received a dedicated consent, display, purge, or disclosure model.

**Impact**

Users can have full email addresses retained and suggested without a clear expectation or effective dedicated opt-out. F-001 means the advertised deletion action currently cannot be trusted to remove them.

**Fix**

1. Add a dedicated, default-off “saved email/identity suggestions” control with a first-use disclosure.
2. Gate both recording and surfacing on that control and always block password, OTP, no-learning, and other sensitive contexts.
3. When disabled, immediately purge in-memory and persisted identity data, or present an explicit keep/delete choice.
4. Update privacy copy to state what is stored, why, for how long, where, and how it is deleted.
5. Add tests for email, password, OTP, URI, no-learning, toggle-off, process restart, and deletion.

### F-005 — P1 — Custom IME keys are announced as buttons but lack accessibility activation actions

**Status:** OBSERVED in source; impact is inferred from the semantics tree and must be confirmed manually.

**Evidence**

- General keys expose `role` and `contentDescription`, but input is handled only by raw `pointerInput` (`ComposeKeyboardView.kt:2241-2252`).
- The same pattern exists for Enter (`2100-2107`), Space (`2426-2433`), Backspace (`2552-2559`), and mode/number keys (`2650-2657`).
- No semantic `onClick` action is attached, and no accessibility instrumentation tests exist.

**Root cause**

Low-level gesture handling was optimized for commit-on-pointer-down and sliding behavior, but only descriptive semantics were added. A `Role.Button` does not itself provide `ACTION_CLICK`.

**Impact**

TalkBack, Switch Access, Voice Access, and accessibility-node clients may announce keys but be unable to activate them by double-tap/switch click. That can make the app unusable for affected users.

**Fix**

1. Add semantic click actions to every key while retaining the raw pointer path for touch users.
2. Expose long-press alternatives and spacebar cursor movement through appropriate custom accessibility actions.
3. Add Compose/instrumentation assertions that each key exposes and executes its click action.
4. Manually verify every key family with TalkBack and Switch Access on the exact release candidate.

Android recommends `clickable`/`toggleable` or explicit accessibility actions for interactive custom composables: [Make composables more accessible](https://developer.android.com/guide/topics/ui/accessibility/composables) and [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

### F-006 — P1 — The release process has no exact-AAB Android runtime certification gate

**Status:** OBSERVED release-control gap.

**Evidence**

- `android-keyboard` and `android_account` have no source `androidTest` directory.
- CI runs JVM/unit/lint checks but no emulator, managed-device, or release split tests (`.github/workflows/ci.yml:64-87`).
- The optional device step is skipped by default (`scripts/validate_android_release.sh:90-100`).
- `scripts/benchmark_android_keyboard.sh` builds debug APKs, waits for manual focus, and dumps memory/logs; it does not install Play-generated release splits, drive an input flow, or apply pass/fail thresholds.
- The audit’s full validator explicitly reported the device smoke as skipped.

**Root cause**

The test wall is strong at pure logic level, but the Android framework, process boundary, accessibility tree, permission flow, `InputConnection`, R8 behavior, and Play split delivery are outside it.

**Impact**

F-001 and F-005 are examples of regressions that all current unit tests allow. Lifecycle, process death, OEM editor behavior, permission denial, layout, and performance regressions can also reach production.

**Fix**

1. Add Android instrumentation coverage for IME input/selection/delete behavior, process-separated preferences/deletion, voice permission, and Compose semantics.
2. Run managed devices across representative API 24, 34, 35, and 36 profiles.
3. Generate splits from the exact signed release AAB with bundletool, install them, and run a deterministic smoke suite.
4. Add Macrobenchmark/Perfetto coverage for keyboard activation, first key, conversion, suggestion tap, mode switch, heap, frames, and ANR thresholds. Android recommends Macrobenchmark for large UI journeys and release-like performance measurement: [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
5. Record the device/build matrix and attach it to each release.

### F-007 — P1 — Upload-key and local signing credentials have overly broad file permissions

**Status:** OBSERVED on the audited release workstation.

**Evidence**

- `android-keyboard/banglu-release.jks` is mode `0644` (`-rw-r--r--`).
- `local.properties` is mode `0644` and is the release credential source (`android-keyboard/build.gradle.kts:10-14,29-35`).
- Both are correctly Git-ignored, but the store checklist only says to back up the keystore; no repository-enforced key-management/recovery procedure exists.

**Root cause**

Signing was configured for a local manual workflow without owner-only filesystem permissions or a protected release service.

**Impact**

Another local account/process with path access can read the upload key or plaintext signing properties. Loss or theft of this material can compromise uploads or future update continuity.

**Fix**

1. Set both files to owner-only access (`chmod 600`) and move the keystore outside the repository workspace.
2. Use a CI secret manager or protected signing service; never log credential values.
3. Confirm Play App Signing and upload-key reset/recovery configuration in Play Console.
4. Maintain and periodically test an encrypted offline backup with documented ownership and recovery steps.
5. Add a release preflight that detects missing/empty credentials and unsafe file mode without printing secrets.

### F-008 — P1 — Privacy policy, Data Safety draft, packaged policy, and artifact disagree

**Status:** OBSERVED; the live URL itself is healthy.

**Evidence**

- The exact in-app URL, `https://shahabul87.github.io/banglu-privacy-policy/`, returned HTTP 200 and current content dated August 23, 2026.
- The live/repository policy says the Android app requests INTERNET and offers account/sync/subscription features, while the merged launch manifest deliberately removes INTERNET, network-state, and BILLING and the account UI is hidden.
- The live policy omits keyboard-level saved email identities and does not give a concrete retention period for clipboard history.
- The packaged HTML copy is older (June 11, 2026) and differs from both the live and Markdown copies.
- `DATA-SAFETY-FORM.md` says all speech-provider audio is encrypted in transit, but the app delegates to whichever system `SpeechRecognizer` provider is installed and cannot prove every provider’s transport guarantee.
- The release validator checks only that the policy contains the words “internet,” voice/microphone, and offline; it does not compare claims with the merged artifact or local-data inventory.

**Root cause**

Multiple hand-maintained policy sources describe different future/current feature sets. The release gate checks keyword presence rather than semantic consistency.

**Impact**

Google Play requires a comprehensive, accurate privacy policy including data access, handling, retention, and deletion. Inaccurate or incomplete declarations can cause review questions or enforcement, and they weaken user trust. Google also makes the developer responsible for third-party SDK/provider behavior reflected in Data Safety. See [Google Play privacy-policy requirements](https://support.google.com/googleplay/android-developer/answer/17517561) and [Data Safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469).

**Fix**

1. Create one versioned data inventory from the exact merged artifact and reachable features.
2. Generate the hosted and packaged policies, Data Safety worksheet, and release checklist from that inventory.
3. Disclose local identity/email learning and clipboard retention/control precisely.
4. Remove account/sync/INTERNET claims for the network-free release, or ship a separately reviewed account-enabled flavor with matching permissions and controls.
5. Validate the speech-provider encryption answer with supported provider documentation; otherwise answer conservatively.
6. Add a release assertion that the public URL is reachable and that declared permissions/features match the artifact.

## Medium-priority findings

### F-009 — P2 — The local release validator does not authenticate or validate the untracked dictionary asset

**Evidence:** The 176,717,824-byte dictionary is Git-ignored. CI downloads and version-checks it, but the local release script directly builds whatever asset happens to be present and does not verify schema, version, size, or checksum. Runtime failure degrades to the seed engine. The current asset and artifacts are correct; the process is not reliable.

**Fix:** Implement one deterministic Gradle preparation/verification task, pin an approved SHA-256 or signed manifest, validate schema/version, make release assembly depend on it, and inspect the packaged copy in CI.

### F-010 — P2 — API 36 is built with an unsupported Android Gradle Plugin version

**Evidence:** compile/target SDK are 36, AGP is 8.7.3, and `android.suppressUnsupportedCompileSdk=36` suppresses the warning. Google lists AGP 8.9.1 as the minimum for API 36: [minimum AGP versions by API](https://developer.android.com/build/releases/about-agp).

**Fix:** Upgrade AGP/Gradle/Kotlin/Compose in controlled increments, remove the suppression, and rerun R8, lint, all tests, exact-AAB packaging, 16 KiB checks, and device regressions. Dependency update warnings should be triaged rather than blindly upgraded together.

### F-011 — P2 — Disabled account/billing/authentication code is still shipped install-time

**Evidence:** The base app always includes `:android_account`; its manifest declares install-time fused delivery; the split contains Billing, Credentials, Google Identity, and backend code. The launcher hides the feature, and the base manifest removes the permissions required for it to work. The validator currently requires the dormant split to be present.

**Fix:** Exclude it from the network-free launch flavor and assert its absence. Add it only to a separate account-enabled flavor after its permissions, account deletion, Data Safety, billing, backend, and process-boundary tests are production-ready.

### F-012 — P2 — Service lifecycle teardown can retain a large/stale engine graph

**Evidence:** The custom lifecycle is advanced to START/RESUME when creating the input view, but finish/hidden paths do not send PAUSE/STOP. `onDestroy` closes the SQLite store and cancels the scope but does not clear the `ViewModelStore` or detach/reset the process-wide engine singleton. The full loader materializes large dictionary collections.

**Risk:** After service destruction without process death, large state and a closed store can remain reachable. This requires heap/runtime reproduction before calling it a confirmed leak.

**Fix:** Implement a production teardown that cancels and joins jobs, detaches/clears storage and engine caches atomically, clears `ViewModelStore`, and drives lifecycle state from actual shown/hidden transitions. Verify destroy/recreate loops with heap dumps and functional input.

### F-013 — P2 — Backspace uses an incomplete custom grapheme algorithm

**Evidence:** `BackspaceResume.previousUserVisibleClusterBoundary` handles Bengali marks, virama/joiners, variation selector, and skin tones, but not full Unicode extended grapheme rules such as emoji ZWJ families, regional-indicator flags, and keycaps. Word delete searches only ASCII space. Existing tests focus on Bengali behavior.

**Fix:** Use API-24-compatible ICU `BreakIterator` or a tested UAX #29 segmenter. Add real `InputConnection` cases for emoji families/couples, skin tones, flags, keycaps, variation selectors, Bengali conjuncts, tabs/newlines, and non-ASCII whitespace.

### F-014 — P2 — Performance/memory evidence is not release-grade

**Evidence:** The benchmark script installs debug APKs even though the build file warns that debug is not representative. It captures a point-in-time memory dump without automated typing, latency/frame/heap/battery thresholds. No Banglu app paths occur in the packaged baseline profile. The post-load memory policy can explicitly invoke `Runtime.gc()`. Current on-device heap, low-memory recovery, cold-start, first-key latency, ANR, jank, and battery were not measured in this audit.

**Fix:** Benchmark the exact release/perf AAB splits with Macrobenchmark/Perfetto on low-, mid-, and high-tier devices; define pass/fail thresholds; generate an app-specific baseline/startup profile; and move or remove forced GC based on measured pause data.

### F-015 — P2 — Low-storage dictionary failure silently reduces core quality

**Evidence:** First use inflates and copies a 176.7 MB SQLite dictionary into internal storage. Because the packaged asset is compressed, the current fallback check conservatively requires about 244 MiB of available space (180 MiB assumed asset size plus a 64 MiB margin). On insufficient space or copy error, the loader logs and the IME continues seed-only. The user receives no direct warning or recovery action.

**Fix:** Surface a clear degraded-state notice in setup/settings, expose required/free-space information without sensitive paths, retry after storage recovery, and add first-install, interrupted-copy, version-refresh, and low-space tests.

### F-016 — P2 — Supply-chain and dependency compliance controls are incomplete

**Evidence:** No Gradle dependency locks or verification metadata were found; the wrapper lacks `distributionSha256Sum`; GitHub Actions use mutable major tags; the dictionary release asset is mutable and checked only by internal version; CI has no vulnerability/secret/license scan or SBOM. The in-app license file covers datasets, not the shipped AndroidX/Kotlin/Google libraries. No dependency was proven vulnerable by this audit.

**Fix:** Enable Gradle dependency verification and locking, add the wrapper checksum, pin Actions to commit SHAs, add OSV/OWASP scanning and secret scanning, generate an SBOM/license inventory, review required notices, and bind dictionary downloads to an approved checksum/signature.

### F-017 — P2 — Production diagnostics cannot reliably support crash/ANR response

**Evidence:** Uncaught-failure records are persisted asynchronously and keep only time/count/simple exception class; they may not finish before process death. No crash/ANR service is included. Settings reads a `diag_latency_last_*` field that the latency writer never writes, so that value remains misleadingly zero. Diagnostics are copied manually.

**Fix:** Keep privacy-first local diagnostics but persist a small bounded crash record synchronously, read `ApplicationExitInfo` on the next start, include build/API and a stack fingerprint without typed text, correct the latency schema, offer explicit user-consented export, and configure Play Vitals monitoring/alerts.

### F-018 — P2 — Voice disclosure UI is not demonstrably adaptive under API 36 behavior

**Evidence:** `VoicePermissionActivity` uses a fixed-padded, non-scrollable layout and does not explicitly consume safe-drawing insets like the primary screens. API 36 removes the edge-to-edge opt-out and strengthens adaptive large-screen expectations: [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16).

**Fix:** Apply edge-to-edge/safe-drawing insets, make the content scrollable or constrained, and test cutouts, landscape, split screen, gesture/three-button navigation, and 2.0 font scale on API 35/36.

### F-019 — P2 — Store operations and metadata are stale/manual

**Evidence:** `STORE-LISTING.md` is labeled v1.5.33, contains v1.5.33 notes, and tells the operator to upload `banglu-1.5.33-2070.aab`; the app is v1.5.81/2118. It also contains performance/battery claims that have no current release benchmark attached. Release files are manually ignored/staged.

**Fix:** Generate version/release checklist fields from Gradle metadata, attach the approved artifact checksum, write current release notes, reshoot/verify screenshots against the exact candidate, substantiate performance claims, and require a staged-rollout/rollback/Play-Vitals checklist.

### F-020 — P3 — User-visible and accessibility text is mostly hardcoded

**Evidence:** Android resources contain very few strings, while settings, onboarding, voice UI, and keyboard accessibility labels are embedded in Kotlin. No locale-specific Android resource directories were found.

**Fix:** Move user-visible and semantic text into resources, define the supported Bengali/English locale strategy, and test locale switching, text expansion, and screen-reader pronunciation.

## Conditional blockers before enabling accounts, sync, or billing

These paths are hidden and lack network/billing permissions today, so they are not active launch vulnerabilities. They must be fixed before enabling the feature:

1. **Cross-process storage:** `AccountActivity`, `AuthSessionStore`, `BackendSyncClient`, and `BillingEntitlementManager` directly open `banglu_prefs`, despite the project’s own documented requirement to use the authoritative cross-process bridge. This can produce stale/clobbered state and incorrect user scoping.
2. **Sensitive token handling:** auth tokens use AES/GCM, but purchase tokens are stored plaintext; raw backend response/error bodies are persisted without strict allowlisting/bounds.
3. **Billing identity:** the obfuscated account identifier uses Java’s 32-bit `hashCode()` with a constant anonymous fallback; use an opaque server identifier or a suitably keyed cryptographic derivation.
4. **Account deletion:** complete in-app and external deletion flows, backend erasure, retention policy, and Play Console URL are required before allowing account creation.
5. **Feature verification:** add end-to-end Credential Manager, backend TLS/input validation, Billing pending/purchase/restore/refund, process death, entitlement, and Play review tests.

## Recommended release gate

Do not upload until all P0/P1 items have been fixed and the following evidence exists for the replacement candidate:

1. A clean tagged source revision produces the exact AAB; embedded revision and version match; SHA-256 is recorded.
2. The full validator passes with a supported API-36 toolchain and a pinned, schema/version/hash-verified dictionary.
3. Cross-process deletion instrumentation proves every learning/identity category is deleted, both with the IME running and cold.
4. Sensitive clipboard and identity-assist instrumentation passes.
5. Every key exposes a working accessibility action; TalkBack and Switch Access manual passes are recorded.
6. Bundletool-generated splits from the exact AAB install and complete a deterministic IME/voice/settings smoke test.
7. Release/Macrobenchmark thresholds pass on the agreed low-, mid-, and high-tier device matrix, including API 36/16 KiB coverage.
8. Hosted privacy policy, packaged policy, Data Safety answers, permissions, screenshots, release notes, and reachable features match the same artifact.
9. Upload-key permissions, encrypted backup, Play App Signing/recovery, staged rollout, rollback, and Play Vitals alerts are confirmed.
10. Play pre-launch report and closed/internal testing show no new crash, ANR, accessibility, or compatibility blocker.

## Verification limitations

- This audit did not write to production, Play Console, a backend, or any user account.
- No live Play Console configuration, Play App Signing/recovery state, Data Safety submission, pre-launch report, Vitals data, staged rollout, or upload acceptance was available.
- No visible physical-device UI test was run during this audit. The repository’s optional device gate was skipped. The owner reports extensive device testing, but the exact fresh AAB was not exercised here.
- No current vulnerability database result was produced; the report identifies the absence of a repeatable scanner rather than claiming a known vulnerable dependency.
- Existing untracked/user files were preserved. This report is the only intentionally authored file; Gradle also generated/refreshed normal ignored build outputs while running the verification gate.

## Release decision

**NO-GO for public production upload at revision `0d3fc9b` until F-001 through F-008 are resolved and the replacement AAB passes the recommended gate.** Continued internal/closed testing is appropriate. After those corrections, the app’s passing build, strong unit coverage, offline IME boundary, restricted manifest, backup exclusions, signed/shrunk artifact, current target SDK, manageable package size, and 16 KiB alignment provide a solid base for production.
