# Release notes — 1.5.86 (2123)

## Play Console "What's new" (≤500 chars)

**বাংলা**
- ভয়েস টাইপিং নতুন করে তৈরি: বিরতির পর কথা হারায় না, বাক্য বারবার বসে না, কমা-দাঁড়ি নিজে বসে
- ক্লিপবোর্ড ইতিহাস আপনার ইচ্ছায় (ডিফল্টে বন্ধ); ব্যক্তিগত ঘরে শুধু বর্তমান কপি এক ট্যাপে বসে
- ইমেইল ঠিকানা মনে রাখা ডিফল্টে বন্ধ; "শেখা শব্দ মুছুন" এখন সত্যিই সব মুছে দেয়
- TalkBack ও Switch Access দিয়ে প্রতিটি কী চাপা যায়

**English**
- Voice typing rebuilt: no dropped speech after pauses, no repeated sentences, automatic commas/দাঁড়ি
- Clipboard history is opt-in (off by default); private fields get a one-tap paste of the current clip only
- Saved email addresses off by default; "Clear learned data" now really deletes everything
- Every key works with TalkBack and Switch Access

## Internal

- Round: S139 — fixes the 1.5.85 release blocker found by the re-audit: the clipboard opt-in Boolean shared the key of the String history payload (ClassCastException on upgrade or on first panel open after enabling). Keys split (`clipboard_history_enabled` / `clipboard_history_entries`), type-safe legacy migration (`PrefsMigrations`) at IME start and in the prefs provider, legacy payload purged (opt-in feature). Also: identity switch-off erases before the preference flips and an "off ⇒ no saved addresses" invariant re-establishes at start and on reset-all; preference maps / identity / English loads publish under `learningLock` only if no erase straddled the read; `SqlitePhoneticIndexStore.hasExtendedData()` warmed on IO (was a main-thread DiskReadViolation); unavailable-store path publishes the dictionary notice; teardown join timeout recorded; validator verifies the AAB signer and forwards `-PbangluAccount=true`; smoke treats missing heap rows as inconclusive; instrumented tests: prefs migration (String/Boolean/idempotent), enable → open panel → paste → switch-off purge on the real keyboard, StrictMode-violation assertion.
- 1.5.85 (2122) must NOT be promoted; this build supersedes it.

## Recorded at release (2026-08-26)
- Built from commit `2f2cd998bd1bf85691e2f29221e7d7b3e516326d` (tag `v1.5.86`); embedded revision verified; APK and AAB signer certificate SHA-256 `8fa2de6dd2216414954f581cbe1247255e7f74c8da4742c2f7d3d8df43b11a17` proven equal to the configured keystore
- `banglu-1.5.86-2123.aab` SHA-256 `83e800ed551bc26078322469a35e93b28500378c7c99ad998758b4320f6e73bc` (66,364,547 bytes); copies in `releases/` and `~/Downloads/`
- Gates: shared jvmTest 674, shared Android 417, android debug/release 144 each, lint 0 errors; connectedDebugAndroidTest 7/7 (erase provider ×3; clipboard prefs migration ×3 — legacy String purge, 1.5.85 Boolean migration, idempotence; assistive keys incl. Space/number/Backspace/long-press action, emoji family/flag deletion, enable → open panel → paste → switch-off purge, zero StrictMode violations); exact-AAB device smoke certified (`device-smoke-1.5.86.json`: activation 59 ms, heap 278.6 MB, frame p50 5.6 / p95 33.5 ms over 41 frames, no ANR)
- Supersedes 1.5.85 (2122), which must not be promoted (clipboard preference-type crash on upgrade / on enabling history)
